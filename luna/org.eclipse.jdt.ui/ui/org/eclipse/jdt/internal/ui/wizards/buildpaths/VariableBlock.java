/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.CoreUtility;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;


public class VariableBlock {

	private final ListDialogField<CPVariableElement> fVariablesList;
	private Control fControl;
	private CLabel fWarning;
	private boolean fHasChanges;

	private List<CPVariableElement> fSelectedElements;
	private boolean fAskToBuild;
	private final boolean fEditOnDoubleclick;


	public VariableBlock(boolean inPreferencePage, String initSelection) {

		fSelectedElements= new ArrayList<CPVariableElement>(0);
		fEditOnDoubleclick= inPreferencePage;
		fAskToBuild= true;

		String[] buttonLabels= new String[] {
			NewWizardMessages.VariableBlock_vars_add_button,
			NewWizardMessages.VariableBlock_vars_edit_button,
			NewWizardMessages.VariableBlock_vars_remove_button
		};

		VariablesAdapter adapter= new VariablesAdapter();

		CPVariableElementLabelProvider labelProvider= new CPVariableElementLabelProvider(inPreferencePage);

		fVariablesList= new ListDialogField<CPVariableElement>(adapter, buttonLabels, labelProvider);
		fVariablesList.setDialogFieldListener(adapter);
		fVariablesList.setLabelText(NewWizardMessages.VariableBlock_vars_label);
		fVariablesList.setRemoveButtonIndex(2);

		fVariablesList.enableButton(1, false);

		fVariablesList.setViewerComparator(new ViewerComparator() {
			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				if (e1 instanceof CPVariableElement && e2 instanceof CPVariableElement) {
					return getComparator().compare(((CPVariableElement)e1).getName(), ((CPVariableElement)e2).getName());
				}
				return super.compare(viewer, e1, e2);
			}
		});
		refresh(initSelection);
	}

	public boolean hasChanges() {
		return fHasChanges;
	}

	public void setChanges(boolean hasChanges) {
		fHasChanges= hasChanges;
	}

	public Control createContents(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
		composite.setFont(parent.getFont());

		LayoutUtil.doDefaultLayout(composite, new DialogField[] { fVariablesList }, true, 0, 0);
		LayoutUtil.setHorizontalGrabbing(fVariablesList.getListControl(null));

		fWarning= new CLabel(composite, SWT.NONE);
		fWarning.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, fVariablesList.getNumberOfControls() - 1, 1));

		fControl= composite;
		updateDeprecationWarning();

		return composite;
	}

	public void addDoubleClickListener(IDoubleClickListener listener) {
		fVariablesList.getTableViewer().addDoubleClickListener(listener);
	}

	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		fVariablesList.getTableViewer().addSelectionChangedListener(listener);
	}


	private Shell getShell() {
		if (fControl != null) {
			return fControl.getShell();
		}
		return JavaPlugin.getActiveWorkbenchShell();
	}

	private class VariablesAdapter implements IDialogFieldListener, IListAdapter<CPVariableElement> {

		// -------- IListAdapter --------

		public void customButtonPressed(ListDialogField<CPVariableElement> field, int index) {
			switch (index) {
			case 0: /* add */
				editEntries(null);
				break;
			case 1: /* edit */
				List<CPVariableElement> selected= field.getSelectedElements();
				editEntries(selected.get(0));
				break;
			}
		}

		public void selectionChanged(ListDialogField<CPVariableElement> field) {
			doSelectionChanged(field);
		}

		public void doubleClicked(ListDialogField<CPVariableElement> field) {
			if (fEditOnDoubleclick) {
				List<CPVariableElement> selected= field.getSelectedElements();
				if (canEdit(selected, containsReadOnly(selected))) {
					editEntries(selected.get(0));
				}
			}
		}

		// ---------- IDialogFieldListener --------

		public void dialogFieldChanged(DialogField field) {
		}

	}

	private boolean containsReadOnly(List<CPVariableElement> selected) {
		for (int i= selected.size()-1; i >= 0; i--) {
			if (selected.get(i).isReadOnly()) {
				return true;
			}
		}
		return false;
	}

	private boolean canEdit(List<CPVariableElement> selected, boolean containsReadOnly) {
		return selected.size() == 1 && !containsReadOnly;
	}

	/**
	 * @param field the dialog field
	 */
	private void doSelectionChanged(DialogField field) {
		List<CPVariableElement> selected= fVariablesList.getSelectedElements();
		boolean containsReadOnly= containsReadOnly(selected);

		// edit
		fVariablesList.enableButton(1, canEdit(selected, containsReadOnly));
		// remove button
		fVariablesList.enableButton(2, !containsReadOnly);

		fSelectedElements= selected;
		updateDeprecationWarning();
	}

	private void updateDeprecationWarning() {
		if (fWarning == null || fWarning.isDisposed())
			return;

		for (Iterator<CPVariableElement> iter= fSelectedElements.iterator(); iter.hasNext();) {
			CPVariableElement element= iter.next();
			String deprecationMessage= element.getDeprecationMessage();
			if (deprecationMessage != null) {
				fWarning.setText(deprecationMessage);
				fWarning.setImage(JFaceResources.getImage(Dialog.DLG_IMG_MESSAGE_WARNING));
				return;
			}
		}
		fWarning.setText(null);
		fWarning.setImage(null);
	}

	private void editEntries(CPVariableElement entry) {
		List<CPVariableElement> existingEntries= fVariablesList.getElements();

		VariableCreationDialog dialog= new VariableCreationDialog(getShell(), entry, existingEntries);
		if (dialog.open() != Window.OK) {
			return;
		}
		CPVariableElement newEntry= dialog.getClasspathElement();
		if (entry == null) {
			fVariablesList.addElement(newEntry);
			entry= newEntry;
			fHasChanges= true;
		} else {
			boolean hasChanges= !(entry.getName().equals(newEntry.getName()) && entry.getPath().equals(newEntry.getPath()));
			if (hasChanges) {
				fHasChanges= true;
				entry.setName(newEntry.getName());
				entry.setPath(newEntry.getPath());
				fVariablesList.refresh();
			}
		}
		fVariablesList.selectElements(new StructuredSelection(entry));
	}

	public List<CPVariableElement> getSelectedElements() {
		return fSelectedElements;
	}

	public boolean performOk() {
		ArrayList<String> removedVariables= new ArrayList<String>();
		ArrayList<String> changedVariables= new ArrayList<String>();
		removedVariables.addAll(Arrays.asList(JavaCore.getClasspathVariableNames()));

		// remove all unchanged
		List<CPVariableElement> changedElements= fVariablesList.getElements();
		for (int i= changedElements.size()-1; i >= 0; i--) {
			CPVariableElement curr= changedElements.get(i);
			if (curr.isReadOnly()) {
				changedElements.remove(curr);
			} else {
				IPath path= curr.getPath();
				IPath prevPath= JavaCore.getClasspathVariable(curr.getName());
				if (prevPath != null && prevPath.equals(path)) {
					changedElements.remove(curr);
				} else {
					changedVariables.add(curr.getName());
				}
			}
			removedVariables.remove(curr.getName());
		}
		int steps= changedElements.size() + removedVariables.size();
		if (steps > 0) {

			boolean needsBuild= false;
			if (fAskToBuild && doesChangeRequireFullBuild(removedVariables, changedVariables)) {
				String title= NewWizardMessages.VariableBlock_needsbuild_title;
				String message= NewWizardMessages.VariableBlock_needsbuild_message;

				MessageDialog buildDialog= new MessageDialog(getShell(), title, null, message, MessageDialog.QUESTION, new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, IDialogConstants.CANCEL_LABEL }, 2);
				int res= buildDialog.open();
				if (res != 0 && res != 1) {
					return false;
				}
				needsBuild= (res == 0);
			}

			final VariableBlockRunnable runnable= new VariableBlockRunnable(removedVariables, changedElements);
			final ProgressMonitorDialog dialog= new ProgressMonitorDialog(getShell());
			try {
				PlatformUI.getWorkbench().getProgressService().runInUI(dialog, runnable, ResourcesPlugin.getWorkspace().getRoot());
			} catch (InvocationTargetException e) {
				ExceptionHandler.handle(new InvocationTargetException(new NullPointerException()), getShell(), NewWizardMessages.VariableBlock_variableSettingError_titel, NewWizardMessages.VariableBlock_variableSettingError_message);
				return false;
			} catch (InterruptedException e) {
				return false;
			}

			if (needsBuild) {
				CoreUtility.getBuildJob(null).schedule();
			}
		}
		return true;
	}

	private boolean doesChangeRequireFullBuild(List<String> removed, List<String> changed) {
		try {
			IJavaModel model= JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
			IJavaProject[] projects= model.getJavaProjects();
			for (int i= 0; i < projects.length; i++) {
				IClasspathEntry[] entries= projects[i].getRawClasspath();
				for (int k= 0; k < entries.length; k++) {
					IClasspathEntry curr= entries[k];
					if (curr.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
						String var= curr.getPath().segment(0);
						if (removed.contains(var) || changed.contains(var)) {
							return true;
						}
					}
				}
			}
		} catch (JavaModelException e) {
			return true;
		}
		return false;
	}

	private class VariableBlockRunnable implements IRunnableWithProgress {
		private final List<String> fToRemove;
		private final List<CPVariableElement> fToChange;

		public VariableBlockRunnable(List<String> toRemove, List<CPVariableElement> toChange) {
			fToRemove= toRemove;
			fToChange= toChange;
		}

		/*
	 	 * @see IRunnableWithProgress#run(IProgressMonitor)
		 */
		public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
			monitor.beginTask(NewWizardMessages.VariableBlock_operation_desc, 1);
			try {
				setVariables(monitor);

			} catch (CoreException e) {
				throw new InvocationTargetException(e);
			} catch (OperationCanceledException e) {
				throw new InterruptedException();
			} finally {
				monitor.done();
			}
		}

		public void setVariables(IProgressMonitor monitor) throws JavaModelException, CoreException {
			int nVariables= fToChange.size() + fToRemove.size();

			String[] names= new String[nVariables];
			IPath[] paths= new IPath[nVariables];
			int k= 0;

			for (int i= 0; i < fToChange.size(); i++) {
				CPVariableElement curr= fToChange.get(i);
				names[k]= curr.getName();
				paths[k]= curr.getPath();
				k++;
			}
			for (int i= 0; i < fToRemove.size(); i++) {
				names[k]= fToRemove.get(i);
				paths[k]= null;
				k++;
			}
			JavaCore.setClasspathVariables(names, paths, new SubProgressMonitor(monitor, 1));
		}
	}

	/**
	 * If set to true, a dialog will ask the user to build on variable changed
	 * @param askToBuild The askToBuild to set
	 */
	public void setAskToBuild(boolean askToBuild) {
		fAskToBuild= askToBuild;
	}

	/**
	 * @param initSelection the initial selection
	 */
	public void refresh(String initSelection) {
		CPVariableElement initSelectedElement= null;

		String[] entries= JavaCore.getClasspathVariableNames();
		ArrayList<CPVariableElement> elements= new ArrayList<CPVariableElement>(entries.length);
		for (int i= 0; i < entries.length; i++) {
			String name= entries[i];
			CPVariableElement elem;
			IPath entryPath= JavaCore.getClasspathVariable(name);
			if (entryPath != null) {
				elem= new CPVariableElement(name, entryPath);
				elements.add(elem);
				if (name.equals(initSelection)) {
					initSelectedElement= elem;
				}
			}
		}

		fVariablesList.setElements(elements);

		if (initSelectedElement != null) {
			ISelection sel= new StructuredSelection(initSelectedElement);
			fVariablesList.selectElements(sel);
		} else {
			fVariablesList.selectFirstElement();
		}

		fHasChanges= false;
	}

	public void setSelection(String elementName) {
		for (int i= 0; i < fVariablesList.getSize(); i++) {
			CPVariableElement elem= fVariablesList.getElement(i);
			if (elem.getName().equals(elementName)) {
				fVariablesList.selectElements(new StructuredSelection(elem));
				return;
			}
		}
	}


}

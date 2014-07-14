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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;

import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.preferences.ClasspathVariablesPreferencePage;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;

public class NewVariableEntryDialog extends StatusDialog {

	private class VariablesAdapter implements IDialogFieldListener, IListAdapter<CPVariableElement> {

		// -------- IListAdapter --------

		public void customButtonPressed(ListDialogField<CPVariableElement> field, int index) {
			switch (index) {
			case IDX_EXTEND: /* extend */
				extendButtonPressed();
				break;
			}
		}

		public void selectionChanged(ListDialogField<CPVariableElement> field) {
			doSelectionChanged();
		}

		public void doubleClicked(ListDialogField<CPVariableElement> field) {
			doDoubleClick();
		}

		// ---------- IDialogFieldListener --------

		public void dialogFieldChanged(DialogField field) {
			if (field == fConfigButton) {
				configButtonPressed();
			}

		}

	}

	private final int IDX_EXTEND= 0;

	private ListDialogField<CPVariableElement> fVariablesList;
	private boolean fCanExtend;
	private boolean fIsValidSelection;

	private IPath[] fResultPaths;

	private SelectionButtonDialogField fConfigButton;

	private CLabel fWarning;

	public NewVariableEntryDialog(Shell parent) {
		super(parent);
		setTitle(NewWizardMessages.NewVariableEntryDialog_title);

		updateStatus(new StatusInfo(IStatus.ERROR, "")); //$NON-NLS-1$

		String[] buttonLabels= new String[] {
			NewWizardMessages.NewVariableEntryDialog_vars_extend,
		};

		VariablesAdapter adapter= new VariablesAdapter();

		CPVariableElementLabelProvider labelProvider= new CPVariableElementLabelProvider(false);

		fVariablesList= new ListDialogField<CPVariableElement>(adapter, buttonLabels, labelProvider);
		fVariablesList.setDialogFieldListener(adapter);
		fVariablesList.setLabelText(NewWizardMessages.NewVariableEntryDialog_vars_label);

		fVariablesList.enableButton(IDX_EXTEND, false);

		fVariablesList.setViewerComparator(new ViewerComparator() {
			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				if (e1 instanceof CPVariableElement && e2 instanceof CPVariableElement) {
					return getComparator().compare(((CPVariableElement)e1).getName(), ((CPVariableElement)e2).getName());
				}
				return super.compare(viewer, e1, e2);
			}
		});


		fConfigButton= new SelectionButtonDialogField(SWT.PUSH);
		fConfigButton.setLabelText(NewWizardMessages.NewVariableEntryDialog_configbutton_label);
		fConfigButton.setDialogFieldListener(adapter);

		initializeElements();

		fCanExtend= false;
		fIsValidSelection= false;
		fResultPaths= null;

		fVariablesList.selectFirstElement();
	}

	/*
	 * @see org.eclipse.jface.dialogs.Dialog#isResizable()
	 * @since 3.4
	 */
	@Override
	protected boolean isResizable() {
		return true;
	}

	private void initializeElements() {
		String[] entries= JavaCore.getClasspathVariableNames();
		ArrayList<CPVariableElement> elements= new ArrayList<CPVariableElement>(entries.length);
		for (int i= 0; i < entries.length; i++) {
			String name= entries[i];
			IPath entryPath= JavaCore.getClasspathVariable(name);
			if (entryPath != null) {
				elements.add(new CPVariableElement(name, entryPath));
			}
		}

		fVariablesList.setElements(elements);
	}


	/* (non-Javadoc)
	 * @see Window#configureShell(Shell)
	 */
	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(shell, IJavaHelpContextIds.NEW_VARIABLE_ENTRY_DIALOG);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#getDialogBoundsSettings()
	 */
	@Override
	protected IDialogSettings getDialogBoundsSettings() {
		return JavaPlugin.getDefault().getDialogSettingsSection(getClass().getName());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	protected Control createDialogArea(Composite parent) {
		initializeDialogUnits(parent);

		Composite composite= (Composite) super.createDialogArea(parent);
		GridLayout layout= (GridLayout) composite.getLayout();
		layout.numColumns= 2;

		fVariablesList.doFillIntoGrid(composite, 3);

		LayoutUtil.setHorizontalSpan(fVariablesList.getLabelControl(null), 2);

		GridData listData= (GridData) fVariablesList.getListControl(null).getLayoutData();
		listData.grabExcessHorizontalSpace= true;
		listData.heightHint= convertHeightInCharsToPixels(10);
		listData.widthHint= convertWidthInCharsToPixels(70);

		fWarning= new CLabel(composite, SWT.NONE);
		fWarning.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, fVariablesList.getNumberOfControls() - 1, 1));

		Composite lowerComposite= new Composite(composite, SWT.NONE);
		lowerComposite.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));

		layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		lowerComposite.setLayout(layout);

		fConfigButton.doFillIntoGrid(lowerComposite, 1);

		applyDialogFont(composite);
		return composite;
	}

	public IPath[] getResult() {
		return fResultPaths;
	}

	/*
 	 * @see IDoubleClickListener#doubleClick(DoubleClickEvent)
 	 */
	private void doDoubleClick() {
		if (fIsValidSelection) {
			okPressed();
		} else if (fCanExtend) {
			extendButtonPressed();
		}
	}

	private void doSelectionChanged() {
		boolean isValidSelection= true;
		boolean canExtend= false;
		StatusInfo status= new StatusInfo();

		List<CPVariableElement> selected= fVariablesList.getSelectedElements();
		int nSelected= selected.size();

		if (nSelected > 0) {
			fResultPaths= new Path[nSelected];
			for (int i= 0; i < nSelected; i++) {
				CPVariableElement curr= selected.get(i);
				fResultPaths[i]= new Path(curr.getName());
				File file= curr.getPath().toFile();
				if (!file.exists()) {
					status.setError(NewWizardMessages.NewVariableEntryDialog_info_notexists);
					isValidSelection= false;
					break;
				}
				if (file.isDirectory()) {
					canExtend= true;
				}
			}
		} else {
			isValidSelection= false;
			status.setInfo(NewWizardMessages.NewVariableEntryDialog_info_noselection);
		}
		if (isValidSelection && nSelected > 1) {
			String str= Messages.format(NewWizardMessages.NewVariableEntryDialog_info_selected, String.valueOf(nSelected));
			status.setInfo(str);
		}
		fCanExtend= nSelected == 1 && canExtend;
		fVariablesList.enableButton(0, fCanExtend);

		updateStatus(status);
		fIsValidSelection= isValidSelection;
		Button okButton= getButton(IDialogConstants.OK_ID);
		if (okButton != null  && !okButton.isDisposed()) {
			okButton.setEnabled(isValidSelection);
		}
		updateDeprecationWarning();
	}

	private void updateDeprecationWarning() {
		if (fWarning == null || fWarning.isDisposed())
			return;

		for (Iterator<CPVariableElement> iter= fVariablesList.getSelectedElements().iterator(); iter.hasNext();) {
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

	private IPath[] chooseExtensions(CPVariableElement elem) {
		File file= elem.getPath().toFile();

		JARFileSelectionDialog dialog= new JARFileSelectionDialog(getShell(), true, true, true);
		dialog.setTitle(NewWizardMessages.NewVariableEntryDialog_ExtensionDialog_title);
		dialog.setMessage(Messages.format(NewWizardMessages.NewVariableEntryDialog_ExtensionDialog_description, elem.getName()));
		dialog.setInput(file);
		if (dialog.open() == Window.OK) {
			Object[] selected= dialog.getResult();
			IPath[] paths= new IPath[selected.length];
			for (int i= 0; i < selected.length; i++) {
				IPath filePath= Path.fromOSString(((File) selected[i]).getPath());
				IPath resPath=  new Path(elem.getName());
				for (int k= elem.getPath().segmentCount(); k < filePath.segmentCount(); k++) {
					resPath= resPath.append(filePath.segment(k));
				}
				paths[i]= resPath;
			}
			return paths;
		}
		return null;
	}

	protected final void extendButtonPressed() {
		List<CPVariableElement> selected= fVariablesList.getSelectedElements();
		if (selected.size() == 1) {
			IPath[] extendedPaths= chooseExtensions(selected.get(0));
			if (extendedPaths != null) {
				fResultPaths= extendedPaths;
				super.buttonPressed(IDialogConstants.OK_ID);
			}
		}
	}

	protected final void configButtonPressed() {
		String id= ClasspathVariablesPreferencePage.ID;
		Map<String, String> options= new HashMap<String, String>();
		List<CPVariableElement> selected= fVariablesList.getSelectedElements();
		if (!selected.isEmpty()) {
			String varName= selected.get(0).getName();
			options.put(ClasspathVariablesPreferencePage.DATA_SELECT_VARIABLE, varName);
		}
		PreferencesUtil.createPreferenceDialogOn(getShell(), id, new String[] { id }, options).open();

		List<CPVariableElement> oldElements= fVariablesList.getElements();
		initializeElements();
		List<CPVariableElement> newElements= fVariablesList.getElements();
		newElements.removeAll(oldElements);
		if (!newElements.isEmpty()) {
			fVariablesList.selectElements(new StructuredSelection(newElements));
		} else if (fVariablesList.getSelectedElements().isEmpty()) {
			fVariablesList.selectFirstElement();
		}
	}

}

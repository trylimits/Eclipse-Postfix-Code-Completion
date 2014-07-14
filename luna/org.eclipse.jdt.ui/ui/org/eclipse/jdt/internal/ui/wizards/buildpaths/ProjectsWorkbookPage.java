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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.dialogs.ListSelectionDialog;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaElementComparator;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.viewsupport.JavaUILabelProvider;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ITreeListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.TreeListDialogField;


public class ProjectsWorkbookPage extends BuildPathBasePage {

	private final int IDX_ADDPROJECT= 0;

	private final int IDX_EDIT= 2;
	private final int IDX_REMOVE= 3;

	private final ListDialogField<CPListElement> fClassPathList;
	private IJavaProject fCurrJProject;

	private final TreeListDialogField<CPListElement> fProjectsList;

	private Control fSWTControl;

	private final IWorkbenchPreferenceContainer fPageContainer;

	public ProjectsWorkbookPage(ListDialogField<CPListElement> classPathList, IWorkbenchPreferenceContainer pageContainer) {
		fClassPathList= classPathList;
		fPageContainer= pageContainer;
		fSWTControl= null;

		String[] buttonLabels= new String[] {
			NewWizardMessages.ProjectsWorkbookPage_projects_add_button,
			null,
			NewWizardMessages.ProjectsWorkbookPage_projects_edit_button,
			NewWizardMessages.ProjectsWorkbookPage_projects_remove_button
		};

		ProjectsAdapter adapter= new ProjectsAdapter();

		fProjectsList= new TreeListDialogField<CPListElement>(adapter, buttonLabels, new CPListLabelProvider());
		fProjectsList.setDialogFieldListener(adapter);
		fProjectsList.setLabelText(NewWizardMessages.ProjectsWorkbookPage_projects_label);

		fProjectsList.enableButton(IDX_REMOVE, false);
		fProjectsList.enableButton(IDX_EDIT, false);

		fProjectsList.setViewerComparator(new CPListElementSorter());
	}

	@Override
	public void init(final IJavaProject jproject) {
		fCurrJProject= jproject;

		if (Display.getCurrent() != null) {
			updateProjectsList();
		} else {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					updateProjectsList();
				}
			});
		}
	}

	private void updateProjectsList() {
		// add the projects-cpentries that are already on the class path
		List<CPListElement> cpelements= fClassPathList.getElements();

		final List<CPListElement> checkedProjects= new ArrayList<CPListElement>(cpelements.size());

		for (int i= cpelements.size() - 1 ; i >= 0; i--) {
			CPListElement cpelem= cpelements.get(i);
			if (isEntryKind(cpelem.getEntryKind())) {
				checkedProjects.add(cpelem);
			}
		}
		fProjectsList.setElements(checkedProjects);
	}

	// -------- UI creation ---------

	@Override
	public Control getControl(Composite parent) {
		PixelConverter converter= new PixelConverter(parent);

		Composite composite= new Composite(parent, SWT.NONE);

		LayoutUtil.doDefaultLayout(composite, new DialogField[] { fProjectsList }, true, SWT.DEFAULT, SWT.DEFAULT);
		LayoutUtil.setHorizontalGrabbing(fProjectsList.getTreeControl(null));

		int buttonBarWidth= converter.convertWidthInCharsToPixels(24);
		fProjectsList.setButtonsMinWidth(buttonBarWidth);

		fSWTControl= composite;

		return composite;
	}

	private void updateClasspathList() {
		List<CPListElement> projelements= fProjectsList.getElements();

		boolean remove= false;
		List<CPListElement> cpelements= fClassPathList.getElements();
		// backwards, as entries will be deleted
		for (int i= cpelements.size() -1; i >= 0 ; i--) {
			CPListElement cpe= cpelements.get(i);
			if (isEntryKind(cpe.getEntryKind())) {
				if (!projelements.remove(cpe)) {
					cpelements.remove(i);
					remove= true;
				}
			}
		}
		for (int i= 0; i < projelements.size(); i++) {
			cpelements.add(projelements.get(i));
		}
		if (remove || (projelements.size() > 0)) {
			fClassPathList.setElements(cpelements);
		}
	}

	/*
	 * @see BuildPathBasePage#getSelection
	 */
	@Override
	public List<Object> getSelection() {
		return fProjectsList.getSelectedElements();
	}

	/*
	 * @see BuildPathBasePage#setSelection
	 */
	@Override
	public void setSelection(List<?> selElements, boolean expand) {
		fProjectsList.selectElements(new StructuredSelection(selElements));
		if (expand) {
			for (int i= 0; i < selElements.size(); i++) {
				fProjectsList.expandElement(selElements.get(i), 1);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathBasePage#isEntryKind(int)
	 */
	@Override
	public boolean isEntryKind(int kind) {
		return kind == IClasspathEntry.CPE_PROJECT;
	}


	private class ProjectsAdapter implements IDialogFieldListener, ITreeListAdapter<CPListElement> {

		private final Object[] EMPTY_ARR= new Object[0];

		// -------- IListAdapter --------
		public void customButtonPressed(TreeListDialogField<CPListElement> field, int index) {
			projectPageCustomButtonPressed(field, index);
		}

		public void selectionChanged(TreeListDialogField<CPListElement> field) {
			projectPageSelectionChanged(field);
		}

		public void doubleClicked(TreeListDialogField<CPListElement> field) {
			projectPageDoubleClicked(field);
		}

		public void keyPressed(TreeListDialogField<CPListElement> field, KeyEvent event) {
			projectPageKeyPressed(field, event);
		}

		public Object[] getChildren(TreeListDialogField<CPListElement> field, Object element) {
			if (element instanceof CPListElement) {
				return ((CPListElement) element).getChildren(false);
			}
			return EMPTY_ARR;
		}

		public Object getParent(TreeListDialogField<CPListElement> field, Object element) {
			if (element instanceof CPListElementAttribute) {
				return ((CPListElementAttribute) element).getParent();
			}
			return null;
		}

		public boolean hasChildren(TreeListDialogField<CPListElement> field, Object element) {
			return getChildren(field, element).length > 0;
		}

		// ---------- IDialogFieldListener --------

		public void dialogFieldChanged(DialogField field) {
			projectPageDialogFieldChanged(field);
		}
	}

	/**
	 * @param field the dialog field
	 * @param index the button index
	 */
	private void projectPageCustomButtonPressed(DialogField field, int index) {
		CPListElement[] entries= null;
		switch (index) {
		case IDX_ADDPROJECT: /* add project */
			entries= addProjectDialog();
			break;
		case IDX_EDIT: /* edit */
			editEntry();
			return;
		case IDX_REMOVE: /* remove */
			removeEntry();
			return;
		}
		if (entries != null) {
			int nElementsChosen= entries.length;
			// remove duplicates
			List<CPListElement> cplist= fProjectsList.getElements();
			List<CPListElement> elementsToAdd= new ArrayList<CPListElement>(nElementsChosen);
			for (int i= 0; i < nElementsChosen; i++) {
				CPListElement curr= entries[i];
				if (!cplist.contains(curr) && !elementsToAdd.contains(curr)) {
					elementsToAdd.add(curr);
				}
			}

			fProjectsList.addElements(elementsToAdd);
			if (index == IDX_ADDPROJECT) {
				fProjectsList.refresh();
			}
			fProjectsList.postSetSelection(new StructuredSelection(entries));
		}
	}

	private void removeEntry() {
		List<Object> selElements= fProjectsList.getSelectedElements();
		for (int i= selElements.size() - 1; i >= 0 ; i--) {
			Object elem= selElements.get(i);
			if (elem instanceof CPListElementAttribute) {
				CPListElementAttribute attrib= (CPListElementAttribute) elem;
				if (attrib.isBuiltIn()) {
					String key= attrib.getKey();
					Object value= null;
					if (key.equals(CPListElement.ACCESSRULES)) {
						value= new IAccessRule[0];
					}
					attrib.getParent().setAttribute(key, value);
				} else {
					removeCustomAttribute(attrib);
				}
				selElements.remove(i);
			}
		}
		if (selElements.isEmpty()) {
			fProjectsList.refresh();
			fClassPathList.dialogFieldChanged(); // validate
		} else {
			fProjectsList.removeElements(selElements);
		}
	}

	private boolean canRemove(List<?> selElements) {
		if (selElements.size() == 0) {
			return false;
		}

		for (int i= 0; i < selElements.size(); i++) {
			Object elem= selElements.get(i);
			if (elem instanceof CPListElementAttribute) {
				CPListElementAttribute attrib= (CPListElementAttribute) elem;
				if (attrib.isNonModifiable()) {
					return false;
				}
				if (attrib.isBuiltIn()) {
					if (CPListElement.ACCESSRULES.equals(attrib.getKey())) {
						if (((IAccessRule[]) attrib.getValue()).length == 0) {
							return false;
						}
					} else if (attrib.getValue() == null) {
						return false;
					}
				} else {
					if  (!canRemoveCustomAttribute(attrib)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private boolean canEdit(List<?> selElements) {
		if (selElements.size() != 1) {
			return false;
		}
		Object elem= selElements.get(0);
		if (elem instanceof CPListElement) {
			return false;
		}
		if (elem instanceof CPListElementAttribute) {
			CPListElementAttribute attrib= (CPListElementAttribute) elem;
			if (attrib.isNonModifiable()) {
				return false;
			}
			if (!attrib.isBuiltIn()) {
				return canEditCustomAttribute(attrib);
			}
			return true;
		}
		return false;
	}

	/**
	 * Method editEntry.
	 */
	private void editEntry() {
		List<Object> selElements= fProjectsList.getSelectedElements();
		if (selElements.size() != 1) {
			return;
		}
		Object elem= selElements.get(0);
		if (elem instanceof CPListElementAttribute) {
			editAttributeEntry((CPListElementAttribute) elem);
		}
	}

	private void editAttributeEntry(CPListElementAttribute elem) {
		String key= elem.getKey();
		if (key.equals(CPListElement.ACCESSRULES)) {
			showAccessRestrictionDialog(elem.getParent());
		} else {
			if (editCustomAttribute(getShell(), elem)) {
				fProjectsList.refresh();
				fClassPathList.dialogFieldChanged(); // validate
			}
		}
	}

	private void showAccessRestrictionDialog(CPListElement selElement) {
		AccessRulesDialog dialog= new AccessRulesDialog(getShell(), selElement, fCurrJProject, fPageContainer != null);
		int res= dialog.open();
		if (res == Window.OK || res == AccessRulesDialog.SWITCH_PAGE) {
			selElement.setAttribute(CPListElement.ACCESSRULES, dialog.getAccessRules());
			selElement.setAttribute(CPListElement.COMBINE_ACCESSRULES, new Boolean(dialog.doCombineAccessRules()));
			fProjectsList.refresh();
			fClassPathList.dialogFieldChanged(); // validate

			if (res == AccessRulesDialog.SWITCH_PAGE) {
				dialog.performPageSwitch(fPageContainer);
			}
		}
	}

	private Shell getShell() {
		if (fSWTControl != null) {
			return fSWTControl.getShell();
		}
		return JavaPlugin.getActiveWorkbenchShell();
	}


	private CPListElement[] addProjectDialog() {

		try {
			Object[] selectArr= getNotYetRequiredProjects();
			new JavaElementComparator().sort(null, selectArr);

			ListSelectionDialog dialog= new ListSelectionDialog(getShell(), Arrays.asList(selectArr), new ArrayContentProvider(), new JavaUILabelProvider(), NewWizardMessages.ProjectsWorkbookPage_chooseProjects_message);
			dialog.setTitle(NewWizardMessages.ProjectsWorkbookPage_chooseProjects_title);
			dialog.setHelpAvailable(false);
			if (dialog.open() == Window.OK) {
				Object[] result= dialog.getResult();
				CPListElement[] cpElements= new CPListElement[result.length];
				for (int i= 0; i < result.length; i++) {
					IJavaProject curr= (IJavaProject) result[i];
					cpElements[i]= new CPListElement(fCurrJProject, IClasspathEntry.CPE_PROJECT, curr.getPath(), curr.getResource());
				}
				return cpElements;
			}
		} catch (JavaModelException e) {
			return null;
		}
		return null;
	}

	private Object[] getNotYetRequiredProjects() throws JavaModelException {
		ArrayList<IJavaProject> selectable= new ArrayList<IJavaProject>();
		selectable.addAll(Arrays.asList(fCurrJProject.getJavaModel().getJavaProjects()));
		selectable.remove(fCurrJProject);

		List<CPListElement> elements= fProjectsList.getElements();
		for (int i= 0; i < elements.size(); i++) {
			CPListElement curr= elements.get(i);
			IJavaProject proj= (IJavaProject) JavaCore.create(curr.getResource());
			selectable.remove(proj);
		}
		Object[] selectArr= selectable.toArray();
		return selectArr;
	}

	/**
	 * @param field the dialog field
	 */
	protected void projectPageDoubleClicked(TreeListDialogField<CPListElement> field) {
		List<Object> selection= fProjectsList.getSelectedElements();
		if (canEdit(selection)) {
			editEntry();
		}
	}

	protected void projectPageKeyPressed(TreeListDialogField<CPListElement> field, KeyEvent event) {
		if (field == fProjectsList) {
			if (event.character == SWT.DEL && event.stateMask == 0) {
				List<Object> selection= field.getSelectedElements();
				if (canRemove(selection)) {
					removeEntry();
				}
			}
		}
	}

	/**
	 * @param field the dialog field
	 */
	private void projectPageDialogFieldChanged(DialogField field) {
		if (fCurrJProject != null) {
			// already initialized
			updateClasspathList();
		}
	}

	/**
	 * @param field the dialog field
	 */
	private void projectPageSelectionChanged(DialogField field) {
		List<Object> selElements= fProjectsList.getSelectedElements();
		fProjectsList.enableButton(IDX_EDIT, canEdit(selElements));
		fProjectsList.enableButton(IDX_REMOVE, canRemove(selElements));

		boolean enabled;
		try {
			enabled= getNotYetRequiredProjects().length > 0;
		} catch (JavaModelException ex) {
			enabled= false;
		}
		fProjectsList.enableButton(IDX_ADDPROJECT, enabled);
	}

	/**
     * {@inheritDoc}
     */
    @Override
	public void setFocus() {
    	fProjectsList.setFocus();
    }
}

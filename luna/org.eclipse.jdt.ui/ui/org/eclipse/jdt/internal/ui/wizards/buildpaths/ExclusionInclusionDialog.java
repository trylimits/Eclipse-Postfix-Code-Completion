/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman, mpchapman@gmail.com - 89977 Make JDT .java agnostic
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;

import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.ImageDescriptorRegistry;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;

public class ExclusionInclusionDialog extends StatusDialog {

	private static class ExclusionInclusionLabelProvider extends LabelProvider {

		private Image fElementImage;

		public ExclusionInclusionLabelProvider(ImageDescriptor descriptor) {
			ImageDescriptorRegistry registry= JavaPlugin.getImageDescriptorRegistry();
			fElementImage= registry.get(descriptor);
		}

		@Override
		public Image getImage(Object element) {
			return fElementImage;
		}

		@Override
		public String getText(Object element) {
			return BasicElementLabels.getFilePattern((String) element);
		}

	}

	private ListDialogField<String> fInclusionPatternList;
	private ListDialogField<String> fExclusionPatternList;
	private CPListElement fCurrElement;
	private IProject fCurrProject;

	private IContainer fCurrSourceFolder;

	private static final int IDX_ADD= 0;
	private static final int IDX_ADD_MULTIPLE= 1;
	private static final int IDX_EDIT= 2;
	private static final int IDX_REMOVE= 4;


	public ExclusionInclusionDialog(Shell parent, CPListElement entryToEdit, boolean focusOnExcluded) {
		super(parent);

		fCurrElement= entryToEdit;

		setTitle(NewWizardMessages.ExclusionInclusionDialog_title);

		fCurrProject= entryToEdit.getJavaProject().getProject();
		IWorkspaceRoot root= fCurrProject.getWorkspace().getRoot();
		IResource res= root.findMember(entryToEdit.getPath());
		if (res instanceof IContainer) {
			fCurrSourceFolder= (IContainer) res;
		}

		String excLabel= NewWizardMessages.ExclusionInclusionDialog_exclusion_pattern_label;
		ImageDescriptor excDescriptor= JavaPluginImages.DESC_OBJS_EXCLUSION_FILTER_ATTRIB;
		String[] excButtonLabels= new String[] {
				NewWizardMessages.ExclusionInclusionDialog_exclusion_pattern_add,
				NewWizardMessages.ExclusionInclusionDialog_exclusion_pattern_add_multiple,
				NewWizardMessages.ExclusionInclusionDialog_exclusion_pattern_edit,
				null,
				NewWizardMessages.ExclusionInclusionDialog_exclusion_pattern_remove
			};


		String incLabel= NewWizardMessages.ExclusionInclusionDialog_inclusion_pattern_label;
		ImageDescriptor incDescriptor= JavaPluginImages.DESC_OBJS_INCLUSION_FILTER_ATTRIB;
		String[] incButtonLabels= new String[] {
				NewWizardMessages.ExclusionInclusionDialog_inclusion_pattern_add,
				NewWizardMessages.ExclusionInclusionDialog_inclusion_pattern_add_multiple,
				NewWizardMessages.ExclusionInclusionDialog_inclusion_pattern_edit,
				null,
				NewWizardMessages.ExclusionInclusionDialog_inclusion_pattern_remove
			};

		fExclusionPatternList= createListContents(entryToEdit, CPListElement.EXCLUSION, excLabel, excDescriptor, excButtonLabels);
		fInclusionPatternList= createListContents(entryToEdit, CPListElement.INCLUSION, incLabel, incDescriptor, incButtonLabels);
		if (focusOnExcluded) {
			fExclusionPatternList.postSetFocusOnDialogField(parent.getDisplay());
		} else {
			fInclusionPatternList.postSetFocusOnDialogField(parent.getDisplay());
		}
	}

	/*
	 * @see org.eclipse.jface.dialogs.Dialog#isResizable()
	 * @since 3.4
	 */
	@Override
	protected boolean isResizable() {
		return true;
	}

	private ListDialogField<String> createListContents(CPListElement entryToEdit, String key, String label, ImageDescriptor descriptor, String[] buttonLabels) {
		ExclusionPatternAdapter adapter= new ExclusionPatternAdapter();

		ListDialogField<String> patternList= new ListDialogField<String>(adapter, buttonLabels, new ExclusionInclusionLabelProvider(descriptor));
		patternList.setDialogFieldListener(adapter);
		patternList.setLabelText(label);
		patternList.setRemoveButtonIndex(IDX_REMOVE);
		patternList.enableButton(IDX_EDIT, false);

		IPath[] pattern= (IPath[]) entryToEdit.getAttribute(key);

		ArrayList<String> elements= new ArrayList<String>(pattern.length);
		for (int i= 0; i < pattern.length; i++) {
			elements.add(pattern[i].toString());
		}
		patternList.setElements(elements);
		patternList.selectFirstElement();
		patternList.enableButton(IDX_ADD_MULTIPLE, fCurrSourceFolder != null);
		patternList.setViewerComparator(new ViewerComparator());
		return patternList;
	}


	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite= (Composite) super.createDialogArea(parent);

		Composite inner= new Composite(composite, SWT.NONE);
		inner.setFont(parent.getFont());

		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.numColumns= 2;
		inner.setLayout(layout);
		inner.setLayoutData(new GridData(GridData.FILL_BOTH));

		DialogField labelField= new DialogField();
		labelField.setLabelText(Messages.format(NewWizardMessages.ExclusionInclusionDialog_description, BasicElementLabels.getPathLabel(fCurrElement.getPath(), false)));
		labelField.doFillIntoGrid(inner, 2);

		fInclusionPatternList.doFillIntoGrid(inner, 3);
		LayoutUtil.setHorizontalSpan(fInclusionPatternList.getLabelControl(null), 2);
		LayoutUtil.setHorizontalGrabbing(fInclusionPatternList.getListControl(null));

		fExclusionPatternList.doFillIntoGrid(inner, 3);
		LayoutUtil.setHorizontalSpan(fExclusionPatternList.getLabelControl(null), 2);
		LayoutUtil.setHorizontalGrabbing(fExclusionPatternList.getListControl(null));

		applyDialogFont(composite);
		return composite;
	}

	protected void doCustomButtonPressed(ListDialogField<String> field, int index) {
		if (index == IDX_ADD) {
			addEntry(field);
		} else if (index == IDX_EDIT) {
			editEntry(field);
		} else if (index == IDX_ADD_MULTIPLE) {
			addMultipleEntries(field);
		}
	}

	protected void doDoubleClicked(ListDialogField<String> field) {
		editEntry(field);
	}

	protected void doSelectionChanged(ListDialogField<String> field) {
		List<String> selected= field.getSelectedElements();
		field.enableButton(IDX_EDIT, canEdit(selected));
	}

	private boolean canEdit(List<String> selected) {
		return selected.size() == 1;
	}

	private void editEntry(ListDialogField<String> field) {
		List<String> selElements= field.getSelectedElements();
		if (selElements.size() != 1) {
			return;
		}
		List<String> existing= field.getElements();
		String entry= selElements.get(0);
		ExclusionInclusionEntryDialog dialog= new ExclusionInclusionEntryDialog(getShell(), isExclusion(field), entry, existing, fCurrElement);
		if (dialog.open() == Window.OK) {
			field.replaceElement(entry, dialog.getExclusionPattern());
		}
	}

	private boolean isExclusion(ListDialogField<String> field) {
		return field == fExclusionPatternList;
	}


	private void addEntry(ListDialogField<String> field) {
		List<String> existing= field.getElements();
		ExclusionInclusionEntryDialog dialog= new ExclusionInclusionEntryDialog(getShell(), isExclusion(field), null, existing, fCurrElement);
		if (dialog.open() == Window.OK) {
			field.addElement(dialog.getExclusionPattern());
		}
	}



	// -------- ExclusionPatternAdapter --------

	private class ExclusionPatternAdapter implements IListAdapter<String>, IDialogFieldListener {
		/**
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter#customButtonPressed(org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField, int)
		 */
		public void customButtonPressed(ListDialogField<String> field, int index) {
			doCustomButtonPressed(field, index);
		}

		/**
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter#selectionChanged(org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField)
		 */
		public void selectionChanged(ListDialogField<String> field) {
			doSelectionChanged(field);
		}
		/**
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter#doubleClicked(org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField)
		 */
		public void doubleClicked(ListDialogField<String> field) {
			doDoubleClicked(field);
		}

		/**
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener#dialogFieldChanged(org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField)
		 */
		public void dialogFieldChanged(DialogField field) {
		}

	}

	protected void doStatusLineUpdate() {
	}

	protected void checkIfPatternValid() {
	}


	private IPath[] getPattern(ListDialogField<?> field) {
		Object[] arr= field.getElements().toArray();
		Arrays.sort(arr);
		IPath[] res= new IPath[arr.length];
		for (int i= 0; i < res.length; i++) {
			res[i]= new Path((String) arr[i]);
		}
		return res;
	}

	public IPath[] getExclusionPattern() {
		return getPattern(fExclusionPatternList);
	}

	public IPath[] getInclusionPattern() {
		return getPattern(fInclusionPatternList);
	}

	/*
	 * @see org.eclipse.jface.window.Window#configureShell(Shell)
	 */
	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell, IJavaHelpContextIds.EXCLUSION_PATTERN_DIALOG);
	}

	private void addMultipleEntries(ListDialogField<String> field) {
		String title, message;
		if (isExclusion(field)) {
			title= NewWizardMessages.ExclusionInclusionDialog_ChooseExclusionPattern_title;
			message= NewWizardMessages.ExclusionInclusionDialog_ChooseExclusionPattern_description;
		} else {
			title= NewWizardMessages.ExclusionInclusionDialog_ChooseInclusionPattern_title;
			message= NewWizardMessages.ExclusionInclusionDialog_ChooseInclusionPattern_description;
		}

		IPath[] res= ExclusionInclusionEntryDialog.chooseExclusionPattern(getShell(), fCurrSourceFolder, title, message, null, true);
		if (res != null) {
			for (int i= 0; i < res.length; i++) {
				field.addElement(res[i].toString());
			}
		}
	}
}

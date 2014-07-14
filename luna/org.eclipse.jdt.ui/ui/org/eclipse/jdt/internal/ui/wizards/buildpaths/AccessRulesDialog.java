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
import java.util.HashMap;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.viewers.ColumnLayoutData;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.FormText;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.preferences.ProblemSeveritiesPreferencePage;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;

public class AccessRulesDialog extends StatusDialog {

	public static final int SWITCH_PAGE= 10;

	private final ListDialogField<IAccessRule> fAccessRulesList;
	private final SelectionButtonDialogField fCombineRulesCheckbox;
	private final CPListElement fCurrElement;

	private final IJavaProject fProject;
	private final boolean fParentCanSwitchPage;

	private static final int IDX_ADD= 0;
	private static final int IDX_EDIT= 1;
	private static final int IDX_UP= 3;
	private static final int IDX_DOWN= 4;
	private static final int IDX_REMOVE= 6;


	public AccessRulesDialog(Shell parent, CPListElement entryToEdit, IJavaProject project, boolean parentCanSwitchPage) {
		super(parent);

		fCurrElement= entryToEdit;
		fProject= project; // can be null

		setTitle(NewWizardMessages.AccessRulesDialog_title);

		fAccessRulesList= createListContents(entryToEdit);

		fCombineRulesCheckbox= new SelectionButtonDialogField(SWT.CHECK);
		fCombineRulesCheckbox.setLabelText(NewWizardMessages.AccessRulesDialog_combine_label);
		fCombineRulesCheckbox.setSelection(Boolean.TRUE.equals(entryToEdit.getAttribute(CPListElement.COMBINE_ACCESSRULES)));

		fParentCanSwitchPage= parentCanSwitchPage;
	}

	/*
	 * @see org.eclipse.jface.dialogs.Dialog#isResizable()
	 * @since 3.4
	 */
	@Override
	protected boolean isResizable() {
		return true;
	}

	private ListDialogField<IAccessRule> createListContents(CPListElement entryToEdit) {
		String label= NewWizardMessages.AccessRulesDialog_rules_label;
		String[] buttonLabels= new String[] {
				NewWizardMessages.AccessRulesDialog_rules_add,
				NewWizardMessages.AccessRulesDialog_rules_edit,
				null,
				NewWizardMessages.AccessRulesDialog_rules_up,
				NewWizardMessages.AccessRulesDialog_rules_down,
				null,
				NewWizardMessages.AccessRulesDialog_rules_remove
		};

		TypeRestrictionAdapter adapter= new TypeRestrictionAdapter();
		AccessRulesLabelProvider labelProvider= new AccessRulesLabelProvider();

		ListDialogField<IAccessRule> patternList= new ListDialogField<IAccessRule>(adapter, buttonLabels, labelProvider);
		patternList.setDialogFieldListener(adapter);

		patternList.setLabelText(label);
		patternList.setRemoveButtonIndex(IDX_REMOVE);
		patternList.setUpButtonIndex(IDX_UP);
		patternList.setDownButtonIndex(IDX_DOWN);
		patternList.enableButton(IDX_EDIT, false);

		IAccessRule[] rules= (IAccessRule[]) entryToEdit.getAttribute(CPListElement.ACCESSRULES);
		ArrayList<IAccessRule> elements= new ArrayList<IAccessRule>(rules.length);
		for (int i= 0; i < rules.length; i++) {
			elements.add(rules[i]);
		}
		patternList.setElements(elements);
		patternList.selectFirstElement();
		return patternList;
	}


	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite= (Composite) super.createDialogArea(parent);

		int maxLabelSize= 0;
		GC gc= new GC(composite);
		try {
			maxLabelSize= gc.textExtent(AccessRulesLabelProvider.getResolutionLabel(IAccessRule.K_ACCESSIBLE)).x;
			int len2= gc.textExtent(AccessRulesLabelProvider.getResolutionLabel(IAccessRule.K_DISCOURAGED)).x;
			if (len2 > maxLabelSize) {
				maxLabelSize= len2;
			}
			int len3= gc.textExtent(AccessRulesLabelProvider.getResolutionLabel(IAccessRule.K_NON_ACCESSIBLE)).x;
			if (len3 > maxLabelSize) {
				maxLabelSize= len3;
			}
		} finally {
			gc.dispose();
		}

		ColumnLayoutData[] columnDta= new ColumnLayoutData[] {
				new ColumnPixelData(maxLabelSize + 40),
				new ColumnWeightData(1),
		};
		fAccessRulesList.setTableColumns(new ListDialogField.ColumnsDescription(columnDta, null, false));


		Composite inner= new Composite(composite, SWT.NONE);
		inner.setFont(composite.getFont());

		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.numColumns= 2;
		inner.setLayout(layout);
		inner.setLayoutData(new GridData(GridData.FILL_BOTH));

		Label description= new Label(inner, SWT.WRAP);

		description.setText(getDescriptionString());

		GridData data= new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1);
		data.widthHint= convertWidthInCharsToPixels(70);
		description.setLayoutData(data);

		fAccessRulesList.doFillIntoGrid(inner, 3);

		LayoutUtil.setHorizontalSpan(fAccessRulesList.getLabelControl(null), 2);

		data= (GridData) fAccessRulesList.getListControl(null).getLayoutData();
		data.grabExcessHorizontalSpace= true;
		data.heightHint= SWT.DEFAULT;

		if (fCurrElement.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
			fCombineRulesCheckbox.doFillIntoGrid(inner, 2);
		}

		if (fProject != null) {
			String forbiddenSeverity=  fProject.getOption(JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE, true);
			String discouragedSeverity= fProject.getOption(JavaCore.COMPILER_PB_DISCOURAGED_REFERENCE, true);
			String[] args= { getLocalizedString(discouragedSeverity), getLocalizedString(forbiddenSeverity) };

			FormToolkit toolkit= new FormToolkit(parent.getDisplay());
			toolkit.setBackground(null);
			try {
				FormText text = toolkit.createFormText(composite, true);
				text.setFont(inner.getFont());
				if (fParentCanSwitchPage) {
					// with link
					text.setText(Messages.format(NewWizardMessages.AccessRulesDialog_severity_info_with_link, args), true, false);
					text.addHyperlinkListener(new HyperlinkAdapter() {
						@Override
						public void linkActivated(HyperlinkEvent e) {
							doErrorWarningLinkPressed();
						}
					});
				} else {
					// no link
					text.setText(Messages.format(NewWizardMessages.AccessRulesDialog_severity_info_no_link, args), true, false);
				}
				data= new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1);
				data.widthHint= convertWidthInCharsToPixels(70);
				text.setLayoutData(data);
			} catch (IllegalArgumentException e) {
				JavaPlugin.log(e); // invalid string
			} finally {
				toolkit.dispose();
			}
		}
		applyDialogFont(composite);
		return composite;
	}

	final void doErrorWarningLinkPressed() {
		if (fParentCanSwitchPage && MessageDialog.openQuestion(getShell(), NewWizardMessages.AccessRulesDialog_switch_dialog_title, NewWizardMessages.AccessRulesDialog_switch_dialog_message)) {
	        setReturnCode(SWITCH_PAGE);
			close();
		}
	}

	private String getLocalizedString(String severity) {
		if (JavaCore.ERROR.equals(severity)) {
			return NewWizardMessages.AccessRulesDialog_severity_error;
		} else if (JavaCore.WARNING.equals(severity)) {
			return NewWizardMessages.AccessRulesDialog_severity_warning;
		} else {
			return NewWizardMessages.AccessRulesDialog_severity_ignore;
		}
	}

	private String getDescriptionString() {
		String desc;
		String name= BasicElementLabels.getResourceName(fCurrElement.getPath().lastSegment());
		switch (fCurrElement.getEntryKind()) {
			case IClasspathEntry.CPE_CONTAINER:
				try {
					name= JavaElementLabels.getContainerEntryLabel(fCurrElement.getPath(), fCurrElement.getJavaProject());
				} catch (JavaModelException e) {
				}
				desc= NewWizardMessages.AccessRulesDialog_container_description;
				break;
			case IClasspathEntry.CPE_PROJECT:
				desc=  NewWizardMessages.AccessRulesDialog_project_description;
				break;
			default:
				desc=  NewWizardMessages.AccessRulesDialog_description;
		}

		return Messages.format(desc, name);
	}


	protected void doCustomButtonPressed(ListDialogField<IAccessRule> field, int index) {
		if (index == IDX_ADD) {
			addEntry(field);
		} else if (index == IDX_EDIT) {
			editEntry(field);
		}
	}

	protected void doDoubleClicked(ListDialogField<IAccessRule> field) {
		editEntry(field);
	}

	protected void doSelectionChanged(ListDialogField<IAccessRule> field) {
		List<IAccessRule> selected= field.getSelectedElements();
		field.enableButton(IDX_EDIT, canEdit(selected));
	}

	private boolean canEdit(List<IAccessRule> selected) {
		return selected.size() == 1;
	}

	private void editEntry(ListDialogField<IAccessRule> field) {

		List<IAccessRule> selElements= field.getSelectedElements();
		if (selElements.size() != 1) {
			return;
		}
		IAccessRule rule= selElements.get(0);
		AccessRuleEntryDialog dialog= new AccessRuleEntryDialog(getShell(), rule, fCurrElement);
		if (dialog.open() == Window.OK) {
			field.replaceElement(rule, dialog.getRule());
		}
	}

	private void addEntry(ListDialogField<IAccessRule> field) {
		AccessRuleEntryDialog dialog= new AccessRuleEntryDialog(getShell(), null, fCurrElement);
		if (dialog.open() == Window.OK) {
			field.addElement(dialog.getRule());
		}
	}



	// -------- TypeRestrictionAdapter --------

	private class TypeRestrictionAdapter implements IListAdapter<IAccessRule>, IDialogFieldListener {
		/**
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter#customButtonPressed(org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField, int)
		 */
		public void customButtonPressed(ListDialogField<IAccessRule> field, int index) {
			doCustomButtonPressed(field, index);
		}

		/**
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter#selectionChanged(org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField)
		 */
		public void selectionChanged(ListDialogField<IAccessRule> field) {
			doSelectionChanged(field);
		}
		/**
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter#doubleClicked(org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField)
		 */
		public void doubleClicked(ListDialogField<IAccessRule> field) {
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

	public IAccessRule[] getAccessRules() {
		List<IAccessRule> elements= fAccessRulesList.getElements();
		return elements.toArray(new IAccessRule[elements.size()]);
	}

	public boolean doCombineAccessRules() {
		return fCombineRulesCheckbox.isSelected();
	}

	/*
	 * @see org.eclipse.jface.window.Window#configureShell(Shell)
	 */
	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		String helpContextId;
		if (fCurrElement.getEntryKind() == IClasspathEntry.CPE_PROJECT)
			helpContextId= IJavaHelpContextIds.ACCESS_RULES_DIALOG_COMBINE_RULES;
		else
			helpContextId= IJavaHelpContextIds.ACCESS_RULES_DIALOG;
		PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell, helpContextId);
	}

	public void performPageSwitch(IWorkbenchPreferenceContainer pageContainer) {
		HashMap<String, String> data= new HashMap<String, String>();
		data.put(ProblemSeveritiesPreferencePage.DATA_SELECT_OPTION_KEY, JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE);
		data.put(ProblemSeveritiesPreferencePage.DATA_SELECT_OPTION_QUALIFIER, JavaCore.PLUGIN_ID);
		pageContainer.openPage(ProblemSeveritiesPreferencePage.PROP_ID, data);
	}
}

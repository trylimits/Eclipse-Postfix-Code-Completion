/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.core.resources.IProject;

import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.viewers.ColumnLayoutData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.Strings;

import org.eclipse.jdt.ui.JavaElementImageDescriptor;
import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.viewsupport.ImageDescriptorRegistry;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringDialogField;

/*
 * The page to configure name conventions
 */
public class NameConventionConfigurationBlock extends OptionsConfigurationBlock {

	private final static int FIELD= 1;
	private final static int STATIC= 2;
	private final static int ARGUMENT= 3;
	private final static int LOCAL= 4;
	private final static int STATIC_FINAL= 5;

	// Preference store keys
	private static final Key PREF_FIELD_PREFIXES= getJDTCoreKey(JavaCore.CODEASSIST_FIELD_PREFIXES);
	private static final Key PREF_FIELD_SUFFIXES= getJDTCoreKey(JavaCore.CODEASSIST_FIELD_SUFFIXES);
	private static final Key PREF_STATIC_FIELD_PREFIXES= getJDTCoreKey(JavaCore.CODEASSIST_STATIC_FIELD_PREFIXES);
	private static final Key PREF_STATIC_FIELD_SUFFIXES= getJDTCoreKey(JavaCore.CODEASSIST_STATIC_FIELD_SUFFIXES);
	private static final Key PREF_STATIC_FINAL_FIELD_PREFIXES= getJDTCoreKey(JavaCore.CODEASSIST_STATIC_FINAL_FIELD_PREFIXES);
	private static final Key PREF_STATIC_FINAL_FIELD_SUFFIXES= getJDTCoreKey(JavaCore.CODEASSIST_STATIC_FINAL_FIELD_SUFFIXES);
	private static final Key PREF_ARGUMENT_PREFIXES= getJDTCoreKey(JavaCore.CODEASSIST_ARGUMENT_PREFIXES);
	private static final Key PREF_ARGUMENT_SUFFIXES= getJDTCoreKey(JavaCore.CODEASSIST_ARGUMENT_SUFFIXES);
	private static final Key PREF_LOCAL_PREFIXES= getJDTCoreKey(JavaCore.CODEASSIST_LOCAL_PREFIXES);
	private static final Key PREF_LOCAL_SUFFIXES= getJDTCoreKey(JavaCore.CODEASSIST_LOCAL_SUFFIXES);

	private static final Key PREF_KEYWORD_THIS= getJDTUIKey(PreferenceConstants.CODEGEN_KEYWORD_THIS);
	private static final Key PREF_IS_FOR_GETTERS= getJDTUIKey(PreferenceConstants.CODEGEN_IS_FOR_GETTERS);
	private static final Key PREF_EXCEPTION_NAME= getJDTUIKey(PreferenceConstants.CODEGEN_EXCEPTION_VAR_NAME);

	private static final Key PREF_USE_OVERRIDE_ANNOT= getJDTUIKey(PreferenceConstants.CODEGEN_USE_OVERRIDE_ANNOTATION);


	private static class NameConventionEntry {
		public int kind;
		public String prefix;
		public String suffix;
		public Key prefixkey;
		public Key suffixkey;
	}

	private class NameConventionInputDialog extends StatusDialog implements IDialogFieldListener {

		private StringDialogField fPrefixField;
		private StringDialogField fSuffixField;
		private NameConventionEntry fEntry;
		private DialogField fMessageField;

		public NameConventionInputDialog(Shell parent, String title, String message, NameConventionEntry entry) {
			super(parent);
			fEntry= entry;

			setTitle(title);

			fMessageField= new DialogField();
			fMessageField.setLabelText(message);

			fPrefixField= new StringDialogField();
			fPrefixField.setLabelText(PreferencesMessages.NameConventionConfigurationBlock_dialog_prefix);
			fPrefixField.setDialogFieldListener(this);

			fSuffixField= new StringDialogField();
			fSuffixField.setLabelText(PreferencesMessages.NameConventionConfigurationBlock_dialog_suffix);
			fSuffixField.setDialogFieldListener(this);

			fPrefixField.setText(entry.prefix);
			fSuffixField.setText(entry.suffix);
		}

		public NameConventionEntry getResult() {
			NameConventionEntry res= new NameConventionEntry();
			res.prefix= Strings.removeTrailingCharacters(fPrefixField.getText(), ',');
			res.suffix= Strings.removeTrailingCharacters(fSuffixField.getText(), ',');
			res.prefixkey= fEntry.prefixkey;
			res.suffixkey= fEntry.suffixkey;
			res.kind= 	fEntry.kind;
			return res;
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite composite= (Composite) super.createDialogArea(parent);
			Composite inner= new Composite(composite, SWT.NONE);
			inner.setFont(composite.getFont());
			GridLayout layout= new GridLayout();
			layout.marginHeight= 0;
			layout.marginWidth= 0;
			layout.numColumns= 2;
			inner.setLayout(layout);

			fMessageField.doFillIntoGrid(inner, 2);
			fPrefixField.doFillIntoGrid(inner, 2);
			fSuffixField.doFillIntoGrid(inner, 2);

			LayoutUtil.setHorizontalGrabbing(fPrefixField.getTextControl(null));
			LayoutUtil.setWidthHint(fPrefixField.getTextControl(null), convertWidthInCharsToPixels(45));
			LayoutUtil.setWidthHint(fSuffixField.getTextControl(null), convertWidthInCharsToPixels(45));

			fPrefixField.postSetFocusOnDialogField(parent.getDisplay());

			applyDialogFont(composite);

			PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IJavaHelpContextIds.CODE_STYLE_EDIT_PREFIX_SUFFIX);

			return composite;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener#dialogFieldChanged(org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField)
		 */
		public void dialogFieldChanged(DialogField field) {
			// validate
			IStatus prefixStatus= validateIdentifiers(getTokens(fPrefixField.getText(), ","), true); //$NON-NLS-1$
			IStatus suffixStatus= validateIdentifiers(getTokens(fSuffixField.getText(), ","), false); //$NON-NLS-1$

			updateStatus(StatusUtil.getMoreSevere(suffixStatus, prefixStatus));
		}

		private IStatus validateIdentifiers(String[] values, boolean prefix) {
			for (int i= 0; i < values.length; i++) {
				String val= values[i];
				if (val.length() == 0) {
					if (prefix) {
						return new StatusInfo(IStatus.ERROR, PreferencesMessages.NameConventionConfigurationBlock_error_emptyprefix);
					} else {
						return new StatusInfo(IStatus.ERROR, PreferencesMessages.NameConventionConfigurationBlock_error_emptysuffix);
					}
				}
				String name= prefix ? val + "x" : "x" + val; //$NON-NLS-2$ //$NON-NLS-1$
				IStatus status= JavaConventions.validateIdentifier(name, JavaCore.VERSION_1_3, JavaCore.VERSION_1_3);
				if (status.matches(IStatus.ERROR)) {
					if (prefix) {
						return new StatusInfo(IStatus.ERROR, Messages.format(PreferencesMessages.NameConventionConfigurationBlock_error_invalidprefix, val));
					} else {
						return new StatusInfo(IStatus.ERROR, Messages.format(PreferencesMessages.NameConventionConfigurationBlock_error_invalidsuffix, val));
					}
				}
			}
			return new StatusInfo();
		}

		/*
		 * @see org.eclipse.jface.window.Window#configureShell(Shell)
		 */
		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			//PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell, IJavaHelpContextIds.IMPORT_ORGANIZE_INPUT_DIALOG);
		}
	}

	private static class NameConventionLabelProvider extends LabelProvider implements ITableLabelProvider {

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.ILabelProvider#getImage(java.lang.Object)
		 */
		@Override
		public Image getImage(Object element) {
			return getColumnImage(element, 0);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.ILabelProvider#getText(java.lang.Object)
		 */
		@Override
		public String getText(Object element) {
			return getColumnText(element, 0);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnImage(java.lang.Object, int)
		 */
		public Image getColumnImage(Object element, int columnIndex) {
			if (columnIndex != 0) {
				return null;
			}

			NameConventionEntry entry= (NameConventionEntry) element;
			ImageDescriptorRegistry registry= JavaPlugin.getImageDescriptorRegistry();
			switch (entry.kind) {
				case FIELD:
					return registry.get(JavaPluginImages.DESC_FIELD_PUBLIC);
				case STATIC:
					return registry.get(new JavaElementImageDescriptor(JavaPluginImages.DESC_FIELD_PUBLIC, JavaElementImageDescriptor.STATIC, JavaElementImageProvider.SMALL_SIZE));
				case STATIC_FINAL:
					return registry.get(new JavaElementImageDescriptor(JavaPluginImages.DESC_FIELD_PUBLIC, JavaElementImageDescriptor.STATIC | JavaElementImageDescriptor.FINAL, JavaElementImageProvider.SMALL_SIZE));
				case ARGUMENT:
					return registry.get(JavaPluginImages.DESC_OBJS_LOCAL_VARIABLE);
				default:
					return registry.get(JavaPluginImages.DESC_OBJS_LOCAL_VARIABLE);
			}
		}
		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnText(java.lang.Object, int)
		 */
		public String getColumnText(Object element, int columnIndex) {
			NameConventionEntry entry= (NameConventionEntry) element;
			if (columnIndex == 0) {
				switch (entry.kind) {
					case FIELD:
						return PreferencesMessages.NameConventionConfigurationBlock_field_label;
					case STATIC:
						return PreferencesMessages.NameConventionConfigurationBlock_static_label;
					case STATIC_FINAL:
						return PreferencesMessages.NameConventionConfigurationBlock_static_final_label;
					case ARGUMENT:
						return PreferencesMessages.NameConventionConfigurationBlock_arg_label;
					default:
						return PreferencesMessages.NameConventionConfigurationBlock_local_label;
				}
			} else if (columnIndex == 1) {
				return entry.prefix;
			} else {
				return entry.suffix;
			}
		}
	}

	private class NameConventionAdapter implements IListAdapter<NameConventionEntry>, IDialogFieldListener {

		private boolean canEdit(ListDialogField<?> field) {
			return field.getSelectedElements().size() == 1;
		}

		public void customButtonPressed(ListDialogField<NameConventionEntry> field, int index) {
			doEditButtonPressed();
		}

		public void selectionChanged(ListDialogField<NameConventionEntry> field) {
			field.enableButton(0, canEdit(field));
		}

		public void doubleClicked(ListDialogField<NameConventionEntry> field) {
			if (canEdit(field)) {
				doEditButtonPressed();
			}
		}

		public void dialogFieldChanged(DialogField field) {
			updateModel(field);
		}
	}

	private ListDialogField<NameConventionEntry> fNameConventionList;
	private SelectionButtonDialogField fUseKeywordThisBox;
	private SelectionButtonDialogField fUseIsForBooleanGettersBox;

	private StringDialogField fExceptionName;
	private SelectionButtonDialogField fUseOverrideAnnotation;

	public NameConventionConfigurationBlock(IStatusChangeListener context, IProject project, IWorkbenchPreferenceContainer container) {
		super(context, project, getAllKeys(), container);

		NameConventionAdapter adapter=  new NameConventionAdapter();
		String[] buttons= new String[] {
			PreferencesMessages.NameConventionConfigurationBlock_list_edit_button
		};
		fNameConventionList= new ListDialogField<NameConventionEntry>(adapter, buttons, new NameConventionLabelProvider()) {
			@Override
			protected int getListStyle() {
				return super.getListStyle() & ~SWT.MULTI | SWT.SINGLE;
			}

		};
		fNameConventionList.setDialogFieldListener(adapter);
		fNameConventionList.setLabelText(PreferencesMessages.NameConventionConfigurationBlock_list_label);

		String[] columnsHeaders= new String[] {
			PreferencesMessages.NameConventionConfigurationBlock_list_name_column,
			PreferencesMessages.NameConventionConfigurationBlock_list_prefix_column,
			PreferencesMessages.NameConventionConfigurationBlock_list_suffix_column,
		};
		ColumnLayoutData[] data= new ColumnLayoutData[] {
			new ColumnWeightData(3),
			new ColumnWeightData(2),
			new ColumnWeightData(2)
		};

		fNameConventionList.setTableColumns(new ListDialogField.ColumnsDescription(data, columnsHeaders, true));

		if (fNameConventionList.getSize() > 0) {
			fNameConventionList.selectFirstElement();
		} else {
			fNameConventionList.enableButton(0, false);
		}

		fExceptionName= new StringDialogField();
		fExceptionName.setDialogFieldListener(adapter);
		fExceptionName.setLabelText(PreferencesMessages.NameConventionConfigurationBlock_exceptionname_label);

		fUseKeywordThisBox= new SelectionButtonDialogField(SWT.CHECK | SWT.WRAP);
		fUseKeywordThisBox.setDialogFieldListener(adapter);
		fUseKeywordThisBox.setLabelText(PreferencesMessages.NameConventionConfigurationBlock_keywordthis_label);

		fUseIsForBooleanGettersBox= new SelectionButtonDialogField(SWT.CHECK | SWT.WRAP);
		fUseIsForBooleanGettersBox.setDialogFieldListener(adapter);
		fUseIsForBooleanGettersBox.setLabelText(PreferencesMessages.NameConventionConfigurationBlock_isforbooleangetters_label);

		fUseOverrideAnnotation= new SelectionButtonDialogField(SWT.CHECK | SWT.WRAP);
		fUseOverrideAnnotation.setDialogFieldListener(adapter);
		fUseOverrideAnnotation.setLabelText(PreferencesMessages.NameConventionConfigurationBlock_use_override_annotation_label);

		updateControls();
	}

	private static Key[] getAllKeys() {
		return new Key[] {
			PREF_FIELD_PREFIXES, PREF_FIELD_SUFFIXES, PREF_STATIC_FIELD_PREFIXES, PREF_STATIC_FIELD_SUFFIXES,
			PREF_STATIC_FINAL_FIELD_PREFIXES, PREF_STATIC_FINAL_FIELD_SUFFIXES,
			PREF_ARGUMENT_PREFIXES, PREF_ARGUMENT_SUFFIXES, PREF_LOCAL_PREFIXES, PREF_LOCAL_SUFFIXES,
			PREF_EXCEPTION_NAME, PREF_KEYWORD_THIS, PREF_IS_FOR_GETTERS, PREF_USE_OVERRIDE_ANNOT
		};
	}

	@Override
	protected Control createContents(Composite parent) {
		setShell(parent.getShell());

		GridLayout layout= new GridLayout();
		layout.numColumns= 3;
		layout.marginHeight= 0;
		layout.marginWidth= 0;

		Composite composite= new Composite(parent, SWT.NONE);
		composite.setFont(parent.getFont());
		composite.setLayout(layout);

		fNameConventionList.doFillIntoGrid(composite, 4);
		LayoutUtil.setHorizontalSpan(fNameConventionList.getLabelControl(null), 2);
		Table table= fNameConventionList.getTableViewer().getTable();
		GridData data= (GridData)fNameConventionList.getListControl(null).getLayoutData();
		data.heightHint= SWTUtil.getTableHeightHint(table, 5);
		data.grabExcessHorizontalSpace= true;
		data.verticalAlignment= GridData.BEGINNING;
		data.grabExcessVerticalSpace= false;

		data= (GridData)fNameConventionList.getButtonBox(null).getLayoutData();
		data.grabExcessVerticalSpace= false;
		data.verticalAlignment= GridData.BEGINNING;

		fUseKeywordThisBox.doFillIntoGrid(composite, 3);
		fUseIsForBooleanGettersBox.doFillIntoGrid(composite, 3);

		fUseOverrideAnnotation.doFillIntoGrid(composite, 3);
		
		Link seeProblemSeverity= new Link(composite, SWT.WRAP);
		data= new GridData(SWT.BEGINNING, SWT.CENTER, true, false, 3, 1);
		seeProblemSeverity.setLayoutData(data);
		LayoutUtil.setHorizontalIndent(seeProblemSeverity);
		seeProblemSeverity.setText(PreferencesMessages.NameConventionConfigurationBlock_override_link_label);
		seeProblemSeverity.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				IWorkbenchPreferenceContainer preferenceContainer= getPreferenceContainer();
				if (preferenceContainer != null) {
					HashMap<String, String> prefsData= new HashMap<String, String>();
					prefsData.put(ProblemSeveritiesPreferencePage.DATA_SELECT_OPTION_KEY, JavaCore.COMPILER_PB_MISSING_OVERRIDE_ANNOTATION_FOR_INTERFACE_METHOD_IMPLEMENTATION);
					prefsData.put(ProblemSeveritiesPreferencePage.DATA_SELECT_OPTION_QUALIFIER, JavaCore.PLUGIN_ID);
					String id= fProject == null ? ProblemSeveritiesPreferencePage.PREF_ID : ProblemSeveritiesPreferencePage.PROP_ID;
					preferenceContainer.openPage(id, prefsData);
				}
			}
		});
		
		DialogField.createEmptySpace(composite, 3);

		fExceptionName.doFillIntoGrid(composite, 2);

		return composite;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#validateSettings(java.lang.String, java.lang.String)
	 */
	@Override
	protected void validateSettings(Key changedKey, String oldValue, String newValue) {
		// no validation here
	}

	protected final void updateModel(DialogField field) {
		if (field == fNameConventionList) {
			for (int i= 0; i < fNameConventionList.getSize(); i++) {
				NameConventionEntry entry= fNameConventionList.getElement(i);
				setValue(entry.suffixkey, entry.suffix);
				setValue(entry.prefixkey, entry.prefix);
			}
		} else if (field == fExceptionName) {
			String name= fExceptionName.getText();

			setValue(PREF_EXCEPTION_NAME, name);

			// validation
			IStatus status = JavaConventions.validateIdentifier(name, JavaCore.VERSION_1_3, JavaCore.VERSION_1_3);
			if (!status.isOK()) {
				fContext.statusChanged(status);
			} else {
				fContext.statusChanged(new StatusInfo());
			}
		} else if (field == fUseKeywordThisBox) {
			setValue(PREF_KEYWORD_THIS, fUseKeywordThisBox.isSelected());
		} else if (field == fUseIsForBooleanGettersBox) {
			setValue(PREF_IS_FOR_GETTERS, fUseIsForBooleanGettersBox.isSelected());
		} else if (field == fUseOverrideAnnotation) {
			setValue(PREF_USE_OVERRIDE_ANNOT, fUseOverrideAnnotation.isSelected());
		}
	}


	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#updateControls()
	 */
	@Override
	protected void updateControls() {
		ArrayList<NameConventionEntry> list= new ArrayList<NameConventionEntry>(4);
		createEntry(list, PREF_FIELD_PREFIXES, PREF_FIELD_SUFFIXES, FIELD);
		createEntry(list, PREF_STATIC_FIELD_PREFIXES, PREF_STATIC_FIELD_SUFFIXES, STATIC);
		createEntry(list, PREF_STATIC_FINAL_FIELD_PREFIXES, PREF_STATIC_FINAL_FIELD_SUFFIXES, STATIC_FINAL);
		createEntry(list, PREF_ARGUMENT_PREFIXES, PREF_ARGUMENT_SUFFIXES, ARGUMENT);
		createEntry(list, PREF_LOCAL_PREFIXES, PREF_LOCAL_SUFFIXES, LOCAL);
		fNameConventionList.setElements(list);

		fExceptionName.setText(getValue(PREF_EXCEPTION_NAME));
		fUseKeywordThisBox.setSelection(getBooleanValue(PREF_KEYWORD_THIS));
		fUseIsForBooleanGettersBox.setSelection(getBooleanValue(PREF_IS_FOR_GETTERS));
		fUseOverrideAnnotation.setSelection(getBooleanValue(PREF_USE_OVERRIDE_ANNOT));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#getFullBuildDialogStrings(boolean)
	 */
	@Override
	protected String[] getFullBuildDialogStrings(boolean workspaceSettings) {
		return null; // no build required
	}

	private void createEntry(List<NameConventionEntry> list, Key prefixKey, Key suffixKey, int kind) {
		NameConventionEntry entry= new NameConventionEntry();
		entry.kind= kind;
		entry.suffixkey= suffixKey;
		entry.prefixkey= prefixKey;
		entry.suffix= getPreferenceValue(suffixKey);
		entry.prefix= getPreferenceValue(prefixKey);
		list.add(entry);
	}

	private String getPreferenceValue(Key key) {
		String value= getValue(key);
		if (value == null) {
			value= ""; //$NON-NLS-1$
			JavaPlugin.logErrorMessage("JavaCore preference is null. Key:" + key); //$NON-NLS-1$
		}
		return value;
	}

	private void doEditButtonPressed() {
		NameConventionEntry entry= fNameConventionList.getSelectedElements().get(0);

		String title;
		String message;
		switch (entry.kind) {
			case FIELD:
				title= PreferencesMessages.NameConventionConfigurationBlock_field_dialog_title;
				message= PreferencesMessages.NameConventionConfigurationBlock_field_dialog_message;
				break;
			case STATIC:
				title= PreferencesMessages.NameConventionConfigurationBlock_static_dialog_title;
				message= PreferencesMessages.NameConventionConfigurationBlock_static_dialog_message;
				break;
			case STATIC_FINAL:
				title= PreferencesMessages.NameConventionConfigurationBlock_static_final_dialog_title;
				message= PreferencesMessages.NameConventionConfigurationBlock_static_final_dialog_message;
				break;
			case ARGUMENT:
				title= PreferencesMessages.NameConventionConfigurationBlock_arg_dialog_title;
				message= PreferencesMessages.NameConventionConfigurationBlock_arg_dialog_message;
				break;
			default:
				title= PreferencesMessages.NameConventionConfigurationBlock_local_dialog_title;
				message= PreferencesMessages.NameConventionConfigurationBlock_local_dialog_message;
		}

		NameConventionInputDialog dialog= new NameConventionInputDialog(getShell(), title, message, entry);
		if (dialog.open() == Window.OK) {
			fNameConventionList.replaceElement(entry, dialog.getResult());
			updateModel(fNameConventionList);
		}
	}
}

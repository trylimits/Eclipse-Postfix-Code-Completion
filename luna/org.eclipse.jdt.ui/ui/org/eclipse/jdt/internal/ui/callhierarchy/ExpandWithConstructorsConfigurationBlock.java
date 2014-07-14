/*******************************************************************************
 * Copyright (c) 2009, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.callhierarchy;

import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.SelectionDialog;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.ui.IJavaElementSearchConstants;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.TextFieldNavigationHandler;
import org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;

/**
 * Call hierarchy expand with constructors configuration block.
 *
 * @since 3.6
 */
public class ExpandWithConstructorsConfigurationBlock extends OptionsConfigurationBlock {

	/**
	 * Call hierarchy expand with constructors dialog for types and members.
	 */
	private static class CallHierarchyTypesOrMembersDialog extends StatusDialog {

		/**
		 * The change listener class for the dialog field and the string button dialog field.
		 *
		 */
		private class StringButtonAdapter implements IDialogFieldListener, IStringButtonAdapter {
			/*
			 * @see IDialogFieldListener#dialogFieldChanged(DialogField)
			 */
			public void dialogFieldChanged(DialogField field) {
				doValidation();
			}

			/*
			 * @see IStringButtonAdapter#changeControlPressed(DialogField)
			 */
			public void changeControlPressed(DialogField field) {
				doBrowseTypes();
			}
		}

		/**
		 * The name dialog field to hold the default expand with constructors list.
		 */
		private StringButtonDialogField fNameDialogField;

		/**
		 * The list of previously existing entries.
		 */
		private List<String> fExistingEntries;

		/**
		 * Tells whether it is an member or type.
		 */
		private final boolean fIsEditingMember;

		/**
		 * Creates a call hierarchy preference dialog for members or types.
		 *
		 * @param parent the parent shell
		 * @param existingEntries the existing list of types and members
		 * @param isEditingMember <code>true</code if its a member, <code>false</code> otherwise
		 */
		public CallHierarchyTypesOrMembersDialog(Shell parent, List<String> existingEntries, boolean isEditingMember) {
			super(parent);
			fIsEditingMember= isEditingMember;
			fExistingEntries= existingEntries;

			String label, title;
			if (isEditingMember) {
				title= CallHierarchyMessages.CallHierarchyTypesOrMembersDialog_member_title;
				label= CallHierarchyMessages.CallHierarchyTypesOrMembersDialog_member_labelText;
			} else {
				title= CallHierarchyMessages.CallHierarchyTypesOrMembersDialog_type_title;
				label= CallHierarchyMessages.CallHierarchyTypesOrMembersDialog_type_labelText;
			}
			setTitle(title);

			StringButtonAdapter adapter= new StringButtonAdapter();

			fNameDialogField= new StringButtonDialogField(adapter);
			fNameDialogField.setLabelText(label);
			fNameDialogField.setButtonLabel(CallHierarchyMessages.CallHierarchyTypesOrMembersDialog_browse_button);
			fNameDialogField.setDialogFieldListener(adapter);
			fNameDialogField.setText(""); //$NON-NLS-1$
		}

		/*
		 * @see org.eclipse.jface.dialogs.Dialog#isResizable()		 *
		 */
		@Override
		protected boolean isResizable() {
			return true;
		}

		/**
		 * Sets the initial selection in the name dialog field.
		 *
		 * @param editedEntry the edited entry
		 */
		public void setInitialSelection(String editedEntry) {
			Assert.isNotNull(editedEntry);
			if (editedEntry.length() == 0)
				fNameDialogField.setText(""); //$NON-NLS-1$
			else
				fNameDialogField.setText(editedEntry);
		}

		/**
		 * Returns the resulting text from the name dialog field.
		 *
		 * @return the resulting text from the name dialog field
		 */
		public String getResult() {
			String val= fNameDialogField.getText();
			if (!fIsEditingMember)
				val= val + WILDCARD;
			return val;
		}

		/*
		 * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(org.eclipse.swt.widgets.Composite)
		 */
		@Override
		protected Control createDialogArea(Composite parent) {
			Composite composite= (Composite)super.createDialogArea(parent);
			initializeDialogUnits(composite);

			GridLayout layout= (GridLayout)composite.getLayout();
			layout.numColumns= 2;

			fNameDialogField.doFillIntoGrid(composite, 3);

			fNameDialogField.getChangeControl(null).setVisible(!fIsEditingMember);

			LayoutUtil.setHorizontalSpan(fNameDialogField.getLabelControl(null), 2);

			int fieldWidthHint= convertWidthInCharsToPixels(60);
			Text text= fNameDialogField.getTextControl(null);
			LayoutUtil.setWidthHint(text, fieldWidthHint);
			LayoutUtil.setHorizontalGrabbing(text);
			LayoutUtil.setHorizontalSpan(text, fIsEditingMember ? 2 : 1);
			TextFieldNavigationHandler.install(text);

			DialogField.createEmptySpace(composite, 1);

			fNameDialogField.postSetFocusOnDialogField(parent.getDisplay());

			applyDialogFont(composite);
			return composite;
		}

		/**
		 * Creates the type hierarchy for type selection.
		 */
		private void doBrowseTypes() {
			IRunnableContext context= new BusyIndicatorRunnableContext();
			IJavaSearchScope scope= SearchEngine.createWorkspaceScope();
			int style= IJavaElementSearchConstants.CONSIDER_ALL_TYPES;
			try {
				SelectionDialog dialog= JavaUI.createTypeDialog(getShell(), context, scope, style, false, fNameDialogField.getText());
				dialog.setTitle(CallHierarchyMessages.CallHierarchyTypesOrMembersDialog_ChooseTypeDialog_title);
				dialog.setMessage(CallHierarchyMessages.CallHierarchyTypesOrMembersDialog_ChooseTypeDialog_description);
				if (dialog.open() == Window.OK) {
					IType res= (IType)dialog.getResult()[0];
					fNameDialogField.setText(res.getFullyQualifiedName('.'));
				}
			} catch (JavaModelException e) {
				ExceptionHandler.handle(e, getShell(), CallHierarchyMessages.CallHierarchyTypesOrMembersDialog_ChooseTypeDialog_title,
						CallHierarchyMessages.CallHierarchyTypesOrMembersDialog_ChooseTypeDialog_error_message);
			}
		}

		/**
		 * Validates the entered type or member and updates the status.
		 */
		private void doValidation() {
			StatusInfo status= new StatusInfo();
			String newText= fNameDialogField.getText();
			if (newText.length() == 0) {
				status.setError(""); //$NON-NLS-1$
			} else {
				IStatus val= JavaConventions.validateJavaTypeName(newText, JavaCore.VERSION_1_3, JavaCore.VERSION_1_3);
				if (val.matches(IStatus.ERROR)) {
					if (fIsEditingMember)
						status.setError(CallHierarchyMessages.CallHierarchyTypesOrMembersDialog_error_invalidMemberName);
					else
						status.setError(CallHierarchyMessages.CallHierarchyTypesOrMembersDialog_error_invalidTypeName);
				} else {
					if (doesExist(newText)) {
						status.setError(CallHierarchyMessages.CallHierarchyTypesOrMembersDialog_error_entryExists);
					}
				}
			}
			updateStatus(status);
		}

		/**
		 * Checks if the entry already exists.
		 *
		 * @param name the type or member name
		 * @return <code>true</code> if it already exists in the list of types and members,
		 *         <code>false</code> otherwise
		 */
		private boolean doesExist(String name) {
			for (int i= 0; i < fExistingEntries.size(); i++) {
				String entry= fExistingEntries.get(i);
				if (name.equals(entry)) {
					return true;
				}
			}
			return false;
		}


		/*
		 * @see org.eclipse.jface.window.Window#configureShell(Shell)
		 */
		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell, IJavaHelpContextIds.CALL_HIERARCHY_EXPAND_WITH_CONSTRUCTORS_DIALOG);
		}

	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#performOk()
	 */
	@Override
	public boolean performOk() {
		setValue(ANONYMOUS_EXPAND_WITH_CONSTRUCTORS, fIsAnonymous);
		return super.performOk();
	}

	/**
	 * The list label provider class.
	 */
	private static class ListLabelProvider extends LabelProvider {

		public final Image MEMBER_ICON;

		private final Image CLASS_ICON;

		/**
		 * Create the member and class icons.
		 */
		public ListLabelProvider() {
			MEMBER_ICON= JavaElementImageProvider.getDecoratedImage(JavaPluginImages.DESC_MISC_PUBLIC, 0, JavaElementImageProvider.SMALL_SIZE);
			CLASS_ICON= JavaElementImageProvider.getDecoratedImage(JavaPluginImages.DESC_OBJS_CLASS, 0, JavaElementImageProvider.SMALL_SIZE);
		}

		/*
		 * @see org.eclipse.jface.viewers.LabelProvider#getImage(java.lang.Object)
		 */
		@Override
		public Image getImage(Object element) {
			return ((String)element).endsWith(WILDCARD) ? CLASS_ICON : MEMBER_ICON;
		}

		/*
		 * @see org.eclipse.jface.viewers.LabelProvider#getText(java.lang.Object)
		 */
		@Override
		public String getText(Object element) {
			return BasicElementLabels.getJavaElementName((String)element);
		}
	}


	/**
	 * The change listener for <code>ListDialogField</code>.
	 */
	private class ListAdapter implements IListAdapter<String>, IDialogFieldListener {

		/**
		 * Checks if field can be edited.
		 *
		 * @param field the list dialog field
		 * @return <code>true</code> if it can be edited, <code>false</code> otherwise
		 */
		private boolean canEdit(ListDialogField<String> field) {
			List<String> selected= field.getSelectedElements();
			return selected.size() == 1;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter#customButtonPressed(org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField, int)
		 */
		public void customButtonPressed(ListDialogField<String> field, int index) {
			doButtonPressed(index);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter#selectionChanged(org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField)
		 */
		public void selectionChanged(ListDialogField<String> field) {
			fList.enableButton(IDX_EDIT, canEdit(field));
			fList.enableButton(IDX_REMOVE, canRemove(field));
		}

		/**
		 * Checks if the field can be removed.
		 *
		 * @param field the list dialog field
		 * @return <code>true</code> if it can be removed, <code>false</code> otherwise
		 */
		private boolean canRemove(ListDialogField<String> field) {
			List<String> selected= field.getSelectedElements();
			return selected.size() != 0;
		}

		/* )
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener#dialogFieldChanged(org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField)
		 */
		public void dialogFieldChanged(DialogField field) {
			doDialogFieldChanged(field);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter#doubleClicked(org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField)
		 */
		public void doubleClicked(ListDialogField<String> field) {
			if (canEdit(field)) {
				doButtonPressed(IDX_EDIT);
			}
		}
	}

	private static final String WILDCARD= ".*"; //$NON-NLS-1$

	private static final int IDX_NEW_TYPE= 0;
	private static final int IDX_NEW_MEMBER= 1;
	private static final int IDX_EDIT= 2;
	private static final int IDX_REMOVE= 3;
	private static final int IDX_RESTORE_DEFAULTS= 4;

	private ListDialogField<String> fList;

	private Button fAnonymousButton;

	protected boolean fIsAnonymous;

	/**
	 * A key that holds the list of methods or types whose methods are by default expanded with constructors in the Call Hierarchy.
	 */
	private static Key DEFAULT_EXPAND_WITH_CONSTRUCTORS_MEMBERS= getJDTUIKey(PreferenceConstants.PREF_DEFAULT_EXPAND_WITH_CONSTRUCTORS_MEMBERS);

	/**
	 * A key that controls whether the methods from anonymous types are by default expanded with constructors in the Call Hierarchy.
	 */
	private static Key ANONYMOUS_EXPAND_WITH_CONSTRUCTORS= getJDTUIKey(PreferenceConstants.PREF_ANONYMOUS_EXPAND_WITH_CONSTRUCTORS);

	/**
	 * Returns all the key values.
	 *
	 * @return array of keys
	 */
	public static Key[] getAllKeys() {
		return new Key[] { DEFAULT_EXPAND_WITH_CONSTRUCTORS_MEMBERS, ANONYMOUS_EXPAND_WITH_CONSTRUCTORS };
	}


	/**
	 * Creates the call hierarchy preferences configuration block.
	 *
	 * @param context the status
	 * @param container the preference container
	 */
	public ExpandWithConstructorsConfigurationBlock(IStatusChangeListener context, IWorkbenchPreferenceContainer container) {
		super(context, null, getAllKeys(), container);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#createContents(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	protected Control createContents(Composite parent) {
		Composite control= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		layout.marginWidth= 10;
		layout.marginHeight= 10;
		control.setLayout(layout);

		createPreferenceList(control);

		fAnonymousButton= new Button(control, SWT.CHECK);
		fAnonymousButton.setText(CallHierarchyMessages.CallHierarchyTypesOrMembersDialog_anonymousTypes_label);
		fIsAnonymous= getBooleanValue(ANONYMOUS_EXPAND_WITH_CONSTRUCTORS);
		fAnonymousButton.setSelection(fIsAnonymous);
		fAnonymousButton.setLayoutData(new GridData(SWT.LEAD, SWT.TOP, false, false));
		fAnonymousButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				fIsAnonymous= fAnonymousButton.getSelection();
			}

		});

		initialize();

		Dialog.applyDialogFont(control);

		return control;
	}

	/**
	 * Create a list dialog field.
	 *
	 * @param parent the composite
	 */
	private void createPreferenceList(Composite parent) {
		String[] buttonLabels= new String[] {
				CallHierarchyMessages.ExpandWithConstructorsConfigurationBlock_newType_button,
				CallHierarchyMessages.ExpandWithConstructorsConfigurationBlock_newMember_button,
				CallHierarchyMessages.ExpandWithConstructorsConfigurationBlock_edit_button,
				CallHierarchyMessages.ExpandWithConstructorsConfigurationBlock_remove_button,
				CallHierarchyMessages.ExpandWithConstructorsConfigurationBlock_restoreDefaults_button
		};

		ListAdapter adapter= new ListAdapter();

		fList= new ListDialogField<String>(adapter, buttonLabels, new ListLabelProvider());
		fList.setDialogFieldListener(adapter);
		fList.setLabelText(CallHierarchyMessages.ExpandWithConstructorsConfigurationBlock_description);
		fList.setRemoveButtonIndex(IDX_REMOVE);
		fList.enableButton(IDX_EDIT, false);
		fList.setViewerComparator(new ViewerComparator());

		PixelConverter pixelConverter= new PixelConverter(parent);

		fList.doFillIntoGrid(parent, 3);
		LayoutUtil.setHorizontalSpan(fList.getLabelControl(null), 2);
		LayoutUtil.setWidthHint(fList.getLabelControl(null), pixelConverter.convertWidthInCharsToPixels(60));
		LayoutUtil.setHorizontalGrabbing(fList.getListControl(null));

		Control listControl= fList.getListControl(null);
		GridData gd= (GridData)listControl.getLayoutData();
		gd.verticalAlignment= GridData.FILL;
		gd.grabExcessVerticalSpace= true;
		gd.heightHint= pixelConverter.convertHeightInCharsToPixels(10);
	}

	/**
	 * Initialize the elements of the list dialog field.
	 */
	public void initialize() {
		fList.setElements(Arrays.asList(getDefaultExpandWithConstructorsMembers()));
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#performDefaults()
	 */
	@Override
	public void performDefaults() {
		String str= PreferenceConstants.getPreferenceStore().getDefaultString(PreferenceConstants.PREF_DEFAULT_EXPAND_WITH_CONSTRUCTORS_MEMBERS);
		fList.setElements(Arrays.asList(deserializeMembers(str)));
		fIsAnonymous= PreferenceConstants.getPreferenceStore().getDefaultBoolean(PreferenceConstants.PREF_ANONYMOUS_EXPAND_WITH_CONSTRUCTORS);
		fAnonymousButton.setSelection(fIsAnonymous);
		super.performDefaults();
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#getFullBuildDialogStrings(boolean)
	 */
	@Override
	protected String[] getFullBuildDialogStrings(boolean workspaceSettings) {
		return null;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#validateSettings(org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock.Key, java.lang.String, java.lang.String)
	 */
	@Override
	protected void validateSettings(Key changedKey, String oldValue, String newValue) {

	}

	/**
	 * Perform the 'New' and 'Edit' button operations by opening the respective call hierarchy
	 * preferences dialog, and 'Restore Defaults' button by restoring to default values for the list dialog.
	 *
	 * @param index the index of the button
	 */
	private void doButtonPressed(int index) {
		if (index == IDX_NEW_TYPE || index == IDX_NEW_MEMBER) { // add new
			List<String> existing= fList.getElements();
			CallHierarchyTypesOrMembersDialog dialog= new CallHierarchyTypesOrMembersDialog(getShell(), existing, index == IDX_NEW_MEMBER);
			if (dialog.open() == Window.OK) {
				fList.addElement(dialog.getResult());
			}
		} else if (index == IDX_EDIT) { // edit
			List<String> selected= fList.getSelectedElements();
			if (selected.isEmpty())
				return;

			String editedEntry= selected.get(0);

			List<String> existing= fList.getElements();
			existing.remove(editedEntry);
			boolean isType= editedEntry.endsWith(WILDCARD);
			CallHierarchyTypesOrMembersDialog dialog= new CallHierarchyTypesOrMembersDialog(getShell(), existing, !isType);
			if (isType)
				dialog.setInitialSelection(editedEntry.substring(0, editedEntry.length() - 2));
			else
				dialog.setInitialSelection(editedEntry);

			if (dialog.open() == Window.OK) {
				fList.replaceElement(editedEntry, dialog.getResult());
			}
		} else if (index == IDX_RESTORE_DEFAULTS){
			performDefaults();
		}
	}

	/**
	 * Update the key on dialog field change.
	 *
	 * @param field the dialog field
	 */
	protected final void doDialogFieldChanged(DialogField field) {
		// set values in working copy
		if (field == fList) {
			setValue(DEFAULT_EXPAND_WITH_CONSTRUCTORS_MEMBERS, serializeMembers(fList.getElements()));
		}
	}

	/**
	 * Returns the array of strings containing the types or methods for default expand with constructors.
	 *
	 * @return the array of strings containing the types or methods for default expand with constructors
	 */
	private String[] getDefaultExpandWithConstructorsMembers() {
		String str= getValue(DEFAULT_EXPAND_WITH_CONSTRUCTORS_MEMBERS);
		if (str != null && str.length() > 0) {
			return deserializeMembers(str);
		}
		return new String[0];
	}

	/**
	 * Return the array of types and/or methods after splitting the stored preference string.
	 *
	 * @param str the input string
	 * @return the array of types and/or methods
	 */
	private static String[] deserializeMembers(String str) {
		return str.split(";"); //$NON-NLS-1$
	}

	/**
	 * Creates a single output string from the list of strings using a delimiter.
	 * 
	 * @param list the input list of types and/or methods
	 * @return the single output string from the list of strings using a delimiter
	 */
	public static String serializeMembers(List<String> list) {
		int size= list.size();
		StringBuffer buf= new StringBuffer();
		for (int i= 0; i < size; i++) {
			buf.append(list.get(i));
			if (i < size - 1)
				buf.append(';');
		}
		return buf.toString();
	}

}

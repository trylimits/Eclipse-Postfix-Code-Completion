/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;

import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.internal.corext.refactoring.code.ConvertAnonymousToNestedRefactoring;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.TextFieldNavigationHandler;

public class ConvertAnonymousToNestedWizard extends RefactoringWizard {

	/**
	 * The dialog setting section for <code>ConvertAnonymousToNestedWizard</code>.
	 * 
	 * @since 3.7
	 */
	static final String DIALOG_SETTING_SECTION= "ConvertAnonymousToNestedWizard"; //$NON-NLS-1$

	public ConvertAnonymousToNestedWizard(ConvertAnonymousToNestedRefactoring ref) {
		super(ref, PREVIEW_EXPAND_FIRST_NODE | DIALOG_BASED_USER_INTERFACE);
		setDefaultPageTitle(RefactoringMessages.ConvertAnonymousToNestedAction_wizard_title);
		setDialogSettings(JavaPlugin.getDefault().getDialogSettings());
	}

	/* non java-doc
	 * @see RefactoringWizard#addUserInputPages
	 */
	@Override
	protected void addUserInputPages(){
		addPage(new ConvertAnonymousToNestedInputPage());
	}

	private static class ConvertAnonymousToNestedInputPage extends UserInputWizardPage {

		private static final String DESCRIPTION = RefactoringMessages.ConvertAnonymousToNestedInputPage_description;
		public static final String PAGE_NAME= "ConvertAnonymousToNestedInputPage";//$NON-NLS-1$

		/**
		 * Stores the value of the declare as static option.
		 * 
		 * @since 3.7
		 */
		private static final String DECLARE_AS_STATIC= "DeclareAsStatic"; //$NON-NLS-1$

		/**
		 * Stores the value of the declare as final option.
		 * 
		 * @since 3.7
		 */
		private static final String DECLARE_AS_FINAL= "DeclareAsFinal"; //$NON-NLS-1$

		/**
		 * Stores the value of visibility control option.
		 * 
		 * @since 3.7
		 */
		private static final String VISIBILITY_CONTROL= "VisibilityControl"; //$NON-NLS-1$

		/**
		 * Stores the dialog settings.
		 * 
		 * @since 3.7
		 */
		private IDialogSettings fSettings;

		public ConvertAnonymousToNestedInputPage() {
			super(PAGE_NAME);
			setDescription(DESCRIPTION);
		}

		/**
		 * Initializes the default settings for the dialog options.
		 * 
		 * @since 3.7
		 */
		private void initializeDefaultSettings() {
			fSettings= getDialogSettings().getSection(DIALOG_SETTING_SECTION);
			if (fSettings == null) {
				fSettings= getDialogSettings().addNewSection(DIALOG_SETTING_SECTION);
				fSettings.put(DECLARE_AS_STATIC, getConvertRefactoring().getDeclareStatic());
				fSettings.put(DECLARE_AS_FINAL, getConvertRefactoring().getDeclareFinal());
				fSettings.put(VISIBILITY_CONTROL, getConvertRefactoring().getVisibility());
			}
		}

		public void createControl(Composite parent) {
			Composite result= new Composite(parent, SWT.NONE);
			setControl(result);
			GridLayout layout= new GridLayout();
			layout.numColumns= 2;
			layout.verticalSpacing= 8;
			result.setLayout(layout);

			initializeDefaultSettings();
			addVisibilityControl(result);
			Text textField= addFieldNameField(result);
			addDeclareFinalCheckbox(result);
			addDeclareAsStaticCheckbox(result);

			textField.setFocus();
			setPageComplete(false);
			Dialog.applyDialogFont(result);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.CONVERT_ANONYMOUS_TO_NESTED_WIZARD_PAGE);
		}

		private Text addFieldNameField(Composite result) {
			Label nameLabel= new Label(result, SWT.NONE);
			nameLabel.setText(RefactoringMessages.ConvertAnonymousToNestedInputPage_class_name);
			nameLabel.setLayoutData(new GridData());

			final Text classNameField= new Text(result, SWT.BORDER | SWT.SINGLE);
			classNameField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			classNameField.addModifyListener(new ModifyListener(){
				public void modifyText(ModifyEvent e) {
					ConvertAnonymousToNestedInputPage.this.getConvertRefactoring().setClassName(classNameField.getText());
					ConvertAnonymousToNestedInputPage.this.updateStatus();
				}
			});
			TextFieldNavigationHandler.install(classNameField);
			return classNameField;
		}

		private void updateStatus() {
			setPageComplete(getConvertRefactoring().validateInput());
		}

		private void addVisibilityControl(Composite result) {
			final ConvertAnonymousToNestedRefactoring r= getConvertRefactoring();
			int[] availableVisibilities= r.getAvailableVisibilities();
			int currectVisibility;
			if (r.isLocalInnerType()) {
				currectVisibility= r.getVisibility();
			} else {
				currectVisibility= fSettings.getInt(VISIBILITY_CONTROL);
				r.setVisibility(currectVisibility);
			}
			IVisibilityChangeListener visibilityChangeListener= new IVisibilityChangeListener(){
				public void visibilityChanged(int newVisibility) {
					r.setVisibility(newVisibility);
					fSettings.put(VISIBILITY_CONTROL, newVisibility);
				}

				public void modifierChanged(int modifier, boolean isChecked) {
				}
			};
			Composite visibilityComposite= InputPageUtil.createVisibilityControl(result, visibilityChangeListener, availableVisibilities, currectVisibility);
			if(visibilityComposite != null) {
			    GridData gd= new GridData(GridData.FILL_HORIZONTAL);
			    gd.horizontalSpan= 2;
			    visibilityComposite.setLayoutData(gd);
			}
		}

		public void addDeclareFinalCheckbox(Composite result) {
			GridData gd;
			final Button declareFinalCheckbox= new Button(result, SWT.CHECK);
			final ConvertAnonymousToNestedRefactoring r= getConvertRefactoring();
			declareFinalCheckbox.setEnabled(r.canEnableSettingFinal());
			boolean declareAsFinal= fSettings.getBoolean(DECLARE_AS_FINAL);
			r.setDeclareFinal(declareAsFinal);
			declareFinalCheckbox.setSelection(declareAsFinal);
			declareFinalCheckbox.setText(RefactoringMessages.ConvertAnonymousToNestedInputPage_declare_final);
			gd= new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalSpan= 2;
			declareFinalCheckbox.setLayoutData(gd);
			declareFinalCheckbox.addSelectionListener(new SelectionAdapter(){
				@Override
				public void widgetSelected(SelectionEvent e) {
					boolean declareFinal= declareFinalCheckbox.getSelection();
					r.setDeclareFinal(declareFinal);
					fSettings.put(DECLARE_AS_FINAL, declareFinal);
				}
			});
		}

		public void addDeclareAsStaticCheckbox(Composite result) {
			GridData gd;
			final Button declareAsStaticCheckbox= new Button(result, SWT.CHECK);
			final ConvertAnonymousToNestedRefactoring r= getConvertRefactoring();
			boolean isEnabled= !r.mustInnerClassBeStatic() && !r.isLocalInnerType();
			declareAsStaticCheckbox.setEnabled(isEnabled);
			boolean isSelected;
			if (isEnabled) {
				isSelected= fSettings.getBoolean(DECLARE_AS_STATIC);
				r.setDeclareStatic(isSelected);
			} else {
				isSelected= getConvertRefactoring().mustInnerClassBeStatic();
			}
			declareAsStaticCheckbox.setSelection(isSelected);
			declareAsStaticCheckbox.setText(RefactoringMessages.ConvertAnonymousToNestedInputPage_declare_static);
			gd= new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalSpan= 2;
			declareAsStaticCheckbox.setLayoutData(gd);
			declareAsStaticCheckbox.addSelectionListener(new SelectionAdapter() {
			    @Override
				public void widgetSelected(SelectionEvent e) {
					boolean declareAsStatic= declareAsStaticCheckbox.getSelection();
					r.setDeclareStatic(declareAsStatic);
					fSettings.put(DECLARE_AS_STATIC, declareAsStatic);
			    }
			});
		}

		private ConvertAnonymousToNestedRefactoring getConvertRefactoring(){
			return (ConvertAnonymousToNestedRefactoring)getRefactoring();
		}
	}
}

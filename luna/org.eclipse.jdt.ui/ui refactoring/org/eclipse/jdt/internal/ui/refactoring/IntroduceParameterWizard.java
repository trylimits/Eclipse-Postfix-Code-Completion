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
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.dialogs.Dialog;

import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo;
import org.eclipse.jdt.internal.corext.refactoring.code.IntroduceParameterRefactoring;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

public class IntroduceParameterWizard extends RefactoringWizard {

	public IntroduceParameterWizard(IntroduceParameterRefactoring ref) {
		super(ref, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
		setDefaultPageTitle(RefactoringMessages.IntroduceParameterWizard_defaultPageTitle);
	}

	/* non java-doc
	 * @see RefactoringWizard#addUserInputPages
	 */
	@Override
	protected void addUserInputPages(){
		addPage(new IntroduceParameterInputPage(getIntroduceParameterRefactoring().guessParameterNames()));
	}

	private IntroduceParameterRefactoring getIntroduceParameterRefactoring(){
		return (IntroduceParameterRefactoring)getRefactoring();
	}

	private static class IntroduceParameterInputPage extends UserInputWizardPage {

		private static final String DESCRIPTION = RefactoringMessages.IntroduceParameterInputPage_description;
		public static final String PAGE_NAME= "IntroduceParameterInputPage";//$NON-NLS-1$
		private String[] fParamNameProposals;

		private JavaSourceViewer fSignaturePreview;
		private Button fLeaveDelegateCheckBox;
		private Button fDeprecateDelegateCheckBox;

		public IntroduceParameterInputPage(String[] tempNameProposals) {
			super(PAGE_NAME);
			setDescription(DESCRIPTION);
			Assert.isNotNull(tempNameProposals);
			fParamNameProposals= tempNameProposals;
		}

		private IntroduceParameterRefactoring getIntroduceParameterRefactoring(){
			return (IntroduceParameterRefactoring)getRefactoring();
		}

		public void createControl(Composite parent) {
			Composite result= new Composite(parent, SWT.NONE);
			setControl(result);
			GridLayout layout= new GridLayout();
			result.setLayout(layout);

			createParameterTableControl(result);
			fLeaveDelegateCheckBox= DelegateUIHelper.generateLeaveDelegateCheckbox(result, getRefactoring(), false);
			if (fLeaveDelegateCheckBox != null) {
				fDeprecateDelegateCheckBox= new Button(result, SWT.CHECK);
				GridData data= new GridData();
				data.horizontalAlignment= GridData.FILL;
				data.horizontalIndent= (layout.marginWidth + fDeprecateDelegateCheckBox.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);
				fDeprecateDelegateCheckBox.setLayoutData(data);
				fDeprecateDelegateCheckBox.setText(DelegateUIHelper.getDeprecateDelegateCheckBoxTitle());
				final IntroduceParameterRefactoring refactoring= getIntroduceParameterRefactoring();
				fDeprecateDelegateCheckBox.setSelection(DelegateUIHelper.loadDeprecateDelegateSetting(refactoring));
				refactoring.setDeprecateDelegates(fDeprecateDelegateCheckBox.getSelection());
				fDeprecateDelegateCheckBox.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						refactoring.setDeprecateDelegates(fDeprecateDelegateCheckBox.getSelection());
					}
				});
				fDeprecateDelegateCheckBox.setEnabled(fLeaveDelegateCheckBox.getSelection());
				fLeaveDelegateCheckBox.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						fDeprecateDelegateCheckBox.setEnabled(fLeaveDelegateCheckBox.getSelection());
					}
				});
			}
			Label sep= new Label(result, SWT.SEPARATOR | SWT.HORIZONTAL);
			sep.setLayoutData((new GridData(GridData.FILL_HORIZONTAL)));
			createSignaturePreview(result);

			update(false);
			Dialog.applyDialogFont(result);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.INTRODUCE_PARAMETER_WIZARD_PAGE);
		}

		private ChangeParametersControl createParameterTableControl(Composite composite) {
			String labelText= RefactoringMessages.IntroduceParameterWizard_parameters;
			ChangeParametersControl cp= new ChangeParametersControl(composite, SWT.NONE, labelText, new IParameterListChangeListener() {
				public void parameterChanged(ParameterInfo parameter) {
					update(true);
				}
				public void parameterListChanged() {
					update(true);
				}
				public void parameterAdded(ParameterInfo parameter) {
					update(true);
				}
			}, ChangeParametersControl.Mode.INTRODUCE_PARAMETER, fParamNameProposals);
			cp.setLayoutData(new GridData(GridData.FILL_BOTH));
			cp.setInput(getIntroduceParameterRefactoring().getParameterInfos());
			cp.editParameter(getIntroduceParameterRefactoring().getAddedParameterInfo());
			return cp;
		}

		@Override
		public void dispose() {
			DelegateUIHelper.saveLeaveDelegateSetting(fLeaveDelegateCheckBox);
			DelegateUIHelper.saveDeprecateDelegateSetting(fDeprecateDelegateCheckBox);
			super.dispose();
		}

		private void createSignaturePreview(Composite composite) {
			Label previewLabel= new Label(composite, SWT.NONE);
			previewLabel.setText(RefactoringMessages.ChangeSignatureInputPage_method_Signature_Preview);

			fSignaturePreview= InputPageUtil.createSignaturePreview(composite);
		}

		private void update(boolean displayErrorMessage){
			updateStatus(displayErrorMessage);
			updateSignaturePreview();
		}

		private void updateStatus(boolean displayErrorMessage) {
			RefactoringStatus nameCheck= getIntroduceParameterRefactoring().validateInput();
			if (displayErrorMessage) {
				setPageComplete(nameCheck);
			} else {
				setErrorMessage(null);
				setPageComplete(true);
			}
		}

		private void updateSignaturePreview() {
			try{
				int top= fSignaturePreview.getTextWidget().getTopPixel();
				fSignaturePreview.getDocument().set(getIntroduceParameterRefactoring().getMethodSignaturePreview());
				fSignaturePreview.getTextWidget().setTopPixel(top);
			} catch (JavaModelException e){
				ExceptionHandler.handle(e, RefactoringMessages.IntroduceParameterWizard_defaultPageTitle, RefactoringMessages.ChangeSignatureInputPage_exception);
			}
		}

	}
}


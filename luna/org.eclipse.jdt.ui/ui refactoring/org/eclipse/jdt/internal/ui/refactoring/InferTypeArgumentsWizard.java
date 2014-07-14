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

package org.eclipse.jdt.internal.ui.refactoring;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;

import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.internal.corext.refactoring.generics.InferTypeArgumentsRefactoring;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;

public class InferTypeArgumentsWizard extends RefactoringWizard {

	public InferTypeArgumentsWizard(Refactoring refactoring) {
		super(refactoring, DIALOG_BASED_USER_INTERFACE);
		setDefaultPageTitle(RefactoringMessages.InferTypeArgumentsWizard_defaultPageTitle);
	}

	/*
	 * @see org.eclipse.ltk.ui.refactoring.RefactoringWizard#addUserInputPages()
	 */
	@Override
	protected void addUserInputPages() {
		addPage(new InferTypeArgumentsInputPage());
	}

	private static class InferTypeArgumentsInputPage extends UserInputWizardPage {

		public static final String PAGE_NAME= "InferTypeArgumentsInputPage"; //$NON-NLS-1$

		private static final String DESCRIPTION= RefactoringMessages.InferTypeArgumentsInputPage_description;

		private static final String DIALOG_SETTING_SECTION= "InferTypeArguments"; //$NON-NLS-1$
		private static final String ASSUME_CLONE_RETURNS_SAME_TYPE= "assumeCloneReturnsSameType"; //$NON-NLS-1$
		private static final String LEAVE_UNCONSTRAINED_RAW= "leaveUnconstrainedRaw"; //$NON-NLS-1$

		IDialogSettings fSettings;

		private InferTypeArgumentsRefactoring fRefactoring;


		public InferTypeArgumentsInputPage() {
			super(PAGE_NAME);
			setDescription(DESCRIPTION);
		}

		public void createControl(Composite parent) {
			fRefactoring= (InferTypeArgumentsRefactoring) getRefactoring();
			loadSettings();

			Composite result= new Composite(parent, SWT.NONE);
			setControl(result);
			GridLayout layout= new GridLayout();
			layout.numColumns= 1;
			result.setLayout(layout);

			Label doit= new Label(result, SWT.WRAP);
			doit.setText(RefactoringMessages.InferTypeArgumentsWizard_lengthyDescription);
			doit.setLayoutData(new GridData());

			Label separator= new Label(result, SWT.SEPARATOR | SWT.HORIZONTAL);
			separator.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));

			Button cloneCheckBox= new Button(result, SWT.CHECK);
			cloneCheckBox.setText(RefactoringMessages.InferTypeArgumentsWizard_assumeCloneSameType);
			boolean assumeCloneValue= fSettings.getBoolean(ASSUME_CLONE_RETURNS_SAME_TYPE);
			fRefactoring.setAssumeCloneReturnsSameType(assumeCloneValue);
			cloneCheckBox.setSelection(assumeCloneValue);
			cloneCheckBox.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					setAssumeCloseReturnsSameType(((Button)e.widget).getSelection());
				}
			});

			Button leaveRawCheckBox= new Button(result, SWT.CHECK);
			leaveRawCheckBox.setText(RefactoringMessages.InferTypeArgumentsWizard_leaveUnconstrainedRaw);
			boolean leaveRawValue= fSettings.getBoolean(LEAVE_UNCONSTRAINED_RAW);
			fRefactoring.setLeaveUnconstrainedRaw(leaveRawValue);
			leaveRawCheckBox.setSelection(leaveRawValue);
			leaveRawCheckBox.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					setLeaveUnconstrainedRaw(((Button)e.widget).getSelection());
				}
			});

			updateStatus();
			Dialog.applyDialogFont(result);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.INFER_TYPE_ARGUMENTS_WIZARD_PAGE);
		}

		private void setAssumeCloseReturnsSameType(boolean selection) {
			fSettings.put(ASSUME_CLONE_RETURNS_SAME_TYPE, selection);
			fRefactoring.setAssumeCloneReturnsSameType(selection);
		}

		private void setLeaveUnconstrainedRaw(boolean selection) {
			fSettings.put(LEAVE_UNCONSTRAINED_RAW, selection);
			fRefactoring.setLeaveUnconstrainedRaw(selection);
		}

		private void updateStatus() {
			setPageComplete(true);
		}

		private void loadSettings() {
			fSettings= getDialogSettings().getSection(DIALOG_SETTING_SECTION);
			if (fSettings == null) {
				fSettings= getDialogSettings().addNewSection(DIALOG_SETTING_SECTION);
				fSettings.put(ASSUME_CLONE_RETURNS_SAME_TYPE, true);
				fSettings.put(LEAVE_UNCONSTRAINED_RAW, true);
			}
			fRefactoring.setAssumeCloneReturnsSameType(fSettings.getBoolean(ASSUME_CLONE_RETURNS_SAME_TYPE));
			fRefactoring.setLeaveUnconstrainedRaw(fSettings.getBoolean(LEAVE_UNCONSTRAINED_RAW));
		}


	}
}

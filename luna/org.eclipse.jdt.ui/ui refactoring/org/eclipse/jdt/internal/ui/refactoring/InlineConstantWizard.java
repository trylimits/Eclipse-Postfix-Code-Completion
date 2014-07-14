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

import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.internal.corext.refactoring.code.InlineConstantRefactoring;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;

public class InlineConstantWizard extends RefactoringWizard {

	private static final String MESSAGE = RefactoringMessages.InlineConstantWizard_message;

	public InlineConstantWizard(InlineConstantRefactoring ref) {
		super(ref, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
		setDefaultPageTitle(RefactoringMessages.InlineConstantWizard_Inline_Constant);
	}

	/* non java-doc
	 * @see RefactoringWizard#addUserInputPages
	 */
	@Override
	protected void addUserInputPages() {
		String message= null;
		if(!getInlineConstantRefactoring().isInitializerAllStaticFinal()) {
			message= RefactoringMessages.InlineConstantWizard_initializer_refers_to_fields;
		} else {
			message= MESSAGE;
		}

		addPage(new InlineConstantInputPage(message));
	}

	private InlineConstantRefactoring getInlineConstantRefactoring(){
		return (InlineConstantRefactoring)getRefactoring();
	}

	private static class InlineConstantInputPage extends UserInputWizardPage {

		public static final String PAGE_NAME= "InlineConstantInputPage";//$NON-NLS-1$

		private InlineConstantRefactoring fRefactoring;
		private Button fRemove;

		public InlineConstantInputPage(String description) {
			super(PAGE_NAME);
			setDescription(description);
		}

		public void createControl(Composite parent) {
			initializeDialogUnits(parent);
			fRefactoring= (InlineConstantRefactoring)getRefactoring();
			fRefactoring.setReplaceAllReferences(fRefactoring.isDeclarationSelected());
			fRefactoring.setRemoveDeclaration(true);

			Composite result= new Composite(parent, SWT.NONE);
			setControl(result);
			GridLayout layout= new GridLayout();
			result.setLayout(layout);
			GridData gd= null;

			Label label= new Label(result, SWT.NONE);
			String constantLabel= JavaElementLabels.getElementLabel(fRefactoring.getField(), JavaElementLabels.ALL_DEFAULT | JavaElementLabels.ALL_FULLY_QUALIFIED);
			label.setText(Messages.format(RefactoringMessages.InlineConstantInputPage_Inline_constant, constantLabel));
			label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			Composite separator= new Composite(result, SWT.NONE);
			separator.setLayoutData(new GridData(0, 0));

			final Button all= new Button(result, SWT.RADIO);
			all.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			all.setText(RefactoringMessages.InlineConstantInputPage_All_references);
			all.setSelection(fRefactoring.getReplaceAllReferences());
			all.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					fRefactoring.setReplaceAllReferences(true);
					fRemove.setEnabled(true);
				}
			});

			fRemove= new Button(result, SWT.CHECK);
			gd= new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalIndent= LayoutUtil.getIndent();
			fRemove.setLayoutData(gd);
			fRemove.setText(RefactoringMessages.InlineConstantInputPage_Delete_constant);
			fRemove.setEnabled(all.getSelection());
			fRemove.setSelection(fRefactoring.getRemoveDeclaration());
			fRemove.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					fRefactoring.setRemoveDeclaration(fRemove.getSelection());
				}
			});


			final Button onlySelected= new Button(result, SWT.RADIO);
			onlySelected.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			onlySelected.setText(RefactoringMessages.InlineConstantInputPage_Only_selected);
			onlySelected.setSelection(!fRefactoring.getReplaceAllReferences());
			if (fRefactoring.isDeclarationSelected()) {
				onlySelected.setEnabled(false);
				all.setFocus();
			} else {
				onlySelected.setFocus();
			}
			onlySelected.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					fRefactoring.setReplaceAllReferences(false);
					fRemove.setEnabled(false);
				}
			});

			Dialog.applyDialogFont(result);

			PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.INLINE_CONSTANT_WIZARD_PAGE);
		}

	}
}

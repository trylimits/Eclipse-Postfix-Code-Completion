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
package org.eclipse.jdt.internal.ui.refactoring.code;

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

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.code.InlineMethodRefactoring;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;

public class InlineMethodInputPage extends UserInputWizardPage {

	public static final String PAGE_NAME= "InlineMethodInputPage";//$NON-NLS-1$
	private static final String DESCRIPTION = RefactoringMessages.InlineMethodInputPage_description;

	private InlineMethodRefactoring fRefactoring;
	private Button fRemove;

	public InlineMethodInputPage() {
		super(PAGE_NAME);
		setImageDescriptor(JavaPluginImages.DESC_WIZBAN_REFACTOR_CU);
		setDescription(DESCRIPTION);
	}

	public void createControl(Composite parent) {
		initializeDialogUnits(parent);
		fRefactoring= (InlineMethodRefactoring)getRefactoring();

		Composite result= new Composite(parent, SWT.NONE);
		setControl(result);
		GridLayout layout= new GridLayout();
		result.setLayout(layout);
		GridData gd= null;

		boolean all= fRefactoring.getInitialMode() == InlineMethodRefactoring.Mode.INLINE_ALL;
		Label label= new Label(result, SWT.NONE);
		String methodLabel= JavaElementLabels.getElementLabel(fRefactoring.getMethod(), JavaElementLabels.ALL_DEFAULT | JavaElementLabels.M_FULLY_QUALIFIED);
		label.setText(Messages.format(RefactoringMessages.InlineMethodInputPage_inline_method, methodLabel));
		label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Composite separator= new Composite(result, SWT.NONE);
		separator.setLayoutData(new GridData(0, 0));

		Button radioAll= new Button(result, SWT.RADIO);
		radioAll.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		radioAll.setText(RefactoringMessages.InlineMethodInputPage_all_invocations);
		radioAll.setSelection(all);
		radioAll.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				fRemove.setEnabled(fRefactoring.canEnableDeleteSource());
				if (((Button)event.widget).getSelection())
					changeRefactoring(InlineMethodRefactoring.Mode.INLINE_ALL);
			}
		});

		fRemove= new Button(result, SWT.CHECK);
		gd= new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalIndent= LayoutUtil.getIndent();
		fRemove.setLayoutData(gd);
		fRemove.setText(RefactoringMessages.InlineMethodInputPage_delete_declaration);
		fRemove.setEnabled(all && fRefactoring.canEnableDeleteSource());
		fRemove.setSelection(fRefactoring.canEnableDeleteSource());
		fRefactoring.setDeleteSource(fRefactoring.canEnableDeleteSource());
		fRemove.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				fRefactoring.setDeleteSource(((Button)e.widget).getSelection());
			}
		});


		Button radioSelected= new Button(result, SWT.RADIO);
		radioSelected.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		radioSelected.setText(RefactoringMessages.InlineMethodInputPage_only_selected);
		radioSelected.setSelection(!all);
		if (all) {
			radioSelected.setEnabled(false);
			radioAll.setFocus();
		} else {
			radioSelected.setFocus();
		}
		radioSelected.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				fRemove.setEnabled(false);
				if (((Button)event.widget).getSelection())
					changeRefactoring(InlineMethodRefactoring.Mode.INLINE_SINGLE);
			}
		});

		Dialog.applyDialogFont(result);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.INLINE_METHOD_WIZARD_PAGE);
	}

	private void changeRefactoring(InlineMethodRefactoring.Mode mode) {
		RefactoringStatus status;
		try {
			status= fRefactoring.setCurrentMode(mode);
		} catch (JavaModelException e) {
			status= RefactoringStatus.createFatalErrorStatus(e.getMessage());
		}
		setPageComplete(status);
	}
}

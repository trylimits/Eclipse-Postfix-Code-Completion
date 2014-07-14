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
package org.eclipse.jdt.ui.actions;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.code.ExtractTempRefactoring;

import org.eclipse.jdt.ui.refactoring.RefactoringSaveHelper;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;
import org.eclipse.jdt.internal.ui.refactoring.ExtractTempWizard;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.actions.RefactoringStarter;

/**
 * Extracts an expression into a new local variable and replaces all occurrences of
 * the expression with the local variable.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class ExtractTempAction extends SelectionDispatchAction {

	private final JavaEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the java editor
	 *
	 * @noreference This method is not intended to be referenced by clients.
	 */
	public ExtractTempAction(JavaEditor editor) {
		super(editor.getEditorSite());
		setText(RefactoringMessages.ExtractTempAction_label);
		fEditor= editor;
		setEnabled(SelectionConverter.getInputAsCompilationUnit(fEditor) != null);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.EXTRACT_TEMP_ACTION);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(fEditor != null && SelectionConverter.getInputAsCompilationUnit(fEditor) != null);
	}

	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 * @param selection the Java text selection (internal type)
	 *
	 * @noreference This method is not intended to be referenced by clients.
	 */
	@Override
	public void selectionChanged(JavaTextSelection selection) {
		setEnabled(RefactoringAvailabilityTester.isExtractTempAvailable(selection));
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(ITextSelection selection) {
		if (!ActionUtil.isEditable(fEditor))
			return;
		ExtractTempRefactoring refactoring= new ExtractTempRefactoring(SelectionConverter.getInputAsCompilationUnit(fEditor), selection.getOffset(), selection.getLength());
		new RefactoringStarter().activate(new ExtractTempWizard(refactoring), getShell(), RefactoringMessages.ExtractTempAction_extract_temp, RefactoringSaveHelper.SAVE_NOTHING);
	}
}

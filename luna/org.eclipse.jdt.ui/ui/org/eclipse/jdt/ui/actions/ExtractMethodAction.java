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
import org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring;

import org.eclipse.jdt.ui.refactoring.RefactoringSaveHelper;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.actions.RefactoringStarter;
import org.eclipse.jdt.internal.ui.refactoring.code.ExtractMethodWizard;

/**
 * Extracts the code selected inside a compilation unit editor into a new method.
 * Necessary arguments, exceptions and returns values are computed and an
 * appropriate method signature is generated.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class ExtractMethodAction extends SelectionDispatchAction {

	private final JavaEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 *
	 * @param editor the java editor
	 *
	 * @noreference This method is not intended to be referenced by clients.
	 */
	public ExtractMethodAction(JavaEditor editor) {
		super(editor.getEditorSite());
		setText(RefactoringMessages.ExtractMethodAction_label);
		fEditor= editor;
		setEnabled(SelectionConverter.getInputAsCompilationUnit(fEditor) != null);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.EXTRACT_METHOD_ACTION);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(selection.getLength() == 0 ? false : fEditor != null && SelectionConverter.getInputAsCompilationUnit(fEditor) != null);
	}

	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 * @param selection the Java text selection (internal type)
	 *
	 * @noreference This method is not intended to be referenced by clients.
	 */
	@Override
	public void selectionChanged(JavaTextSelection selection) {
		setEnabled(RefactoringAvailabilityTester.isExtractMethodAvailable(selection));
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(ITextSelection selection) {
		if (!ActionUtil.isEditable(fEditor))
			return;
		ExtractMethodRefactoring refactoring= new ExtractMethodRefactoring(SelectionConverter.getInputAsCompilationUnit(fEditor), selection.getOffset(), selection.getLength());
		new RefactoringStarter().activate(new ExtractMethodWizard(refactoring), getShell(), RefactoringMessages.ExtractMethodAction_dialog_title, RefactoringSaveHelper.SAVE_NOTHING);
	}
}

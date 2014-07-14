/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.actions;

import org.eclipse.jface.dialogs.MessageDialog;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.corext.refactoring.surround.SurroundWithTryCatchRefactoring;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

/**
 * Action to surround a set of statements with a try/multi-catch block.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 3.7.1
 * 
 * @noextend This class is not intended to be subclassed by clients.
 */
public class SurroundWithTryMultiCatchAction extends SurroundWithTryCatchAction {

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the compilation unit editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public SurroundWithTryMultiCatchAction(CompilationUnitEditor editor) {
		super(editor);
		setText(RefactoringMessages.SurroundWithTryMultiCatchAction_label);
	}

	@Override
	public void run(ITextSelection selection) {
		ICompilationUnit compilationUnit= SelectionConverter.getInputAsCompilationUnit(fEditor);
		IJavaProject javaProject= compilationUnit.getJavaProject();
		if (!JavaModelUtil.is17OrHigher(javaProject)) {
			String message= Messages.format(RefactoringMessages.SurroundWithTryMultiCatchAction_not17, BasicElementLabels.getJavaElementName(javaProject.getElementName()));
			MessageDialog.openInformation(JavaPlugin.getActiveWorkbenchShell(), getDialogTitle(), message);
			return;
		}
		super.run(selection);
	}

	@Override
	SurroundWithTryCatchRefactoring createRefactoring(ITextSelection selection, ICompilationUnit cu) {
		return SurroundWithTryCatchRefactoring.create(cu, selection, true);
	}

	@Override
	String getDialogTitle() {
		return RefactoringMessages.SurroundWithTryMultiCatchAction_dialog_title;
	}

	@Override
	boolean isApplicable() {
		if (!super.isApplicable())
			return false;
		
		ICompilationUnit compilationUnit= SelectionConverter.getInputAsCompilationUnit(fEditor);
		IJavaProject javaProject= compilationUnit.getJavaProject();
		return JavaModelUtil.is17OrHigher(javaProject);
	}

}

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
package org.eclipse.jdt.internal.ui.refactoring.actions;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringExecutionStarter;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * Inlines a constant.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 */
public class InlineConstantAction extends SelectionDispatchAction {

	private JavaEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 *
	 * @param editor the java editor
	 */
	public InlineConstantAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor));
	}

	public InlineConstantAction(IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.InlineConstantAction_inline_Constant);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.INLINE_ACTION);
	}

	//---- structured selection ---------------------------------------------

	/*
	 * @see SelectionDispatchAction#selectionChanged(IStructuredSelection)
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isInlineConstantAvailable(selection));
		} catch (JavaModelException e) {
			// http://bugs.eclipse.org/bugs/show_bug.cgi?id=19253
			if (JavaModelUtil.isExceptionToBeLogged(e))
				JavaPlugin.log(e);
			setEnabled(false);//no ui
		}
	}

	/*
	 * @see SelectionDispatchAction#run(IStructuredSelection)
	 */
	@Override
	public void run(IStructuredSelection selection) {
		try {
			Assert.isTrue(RefactoringAvailabilityTester.isInlineConstantAvailable(selection));

			Object first= selection.getFirstElement();
			Assert.isTrue(first instanceof IField);

			IField field= (IField) first;
			run(field.getNameRange().getOffset(), field.getNameRange().getLength(), field.getCompilationUnit());
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, getShell(), RefactoringMessages.InlineConstantAction_dialog_title, RefactoringMessages.InlineConstantAction_unexpected_exception);
		}
	}

	//---- text selection -----------------------------------------------

    /*
     * @see SelectionDispatchAction#selectionChanged(ITextSelection)
     */
	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(true);
    }

	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 * @param selection
	 */
	@Override
	public void selectionChanged(JavaTextSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isInlineConstantAvailable(selection));
		} catch (JavaModelException e) {
			setEnabled(false);
		}
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(ITextSelection selection) {
		run(selection.getOffset(), selection.getLength(), SelectionConverter.getInputAsCompilationUnit(fEditor));
	}

	private void run(int offset, int length, ICompilationUnit cu) {
		Assert.isNotNull(cu);
		Assert.isTrue(offset >= 0);
		Assert.isTrue(length >= 0);
		if (!ActionUtil.isEditable(fEditor, getShell(), cu))
			return;
		CompilationUnit node= RefactoringASTParser.parseWithASTProvider(cu, true, null);
		if (!RefactoringExecutionStarter.startInlineConstantRefactoring(cu, node, offset, length, getShell())) {
			MessageDialog.openInformation(getShell(), RefactoringMessages.InlineConstantAction_dialog_title, RefactoringMessages.InlineConstantAction_no_constant_reference_or_declaration);
		}
	}

	public boolean tryInlineConstant(ICompilationUnit unit, CompilationUnit node, ITextSelection selection, Shell shell) {
		return RefactoringExecutionStarter.startInlineConstantRefactoring(unit, node, selection.getOffset(), selection.getLength(), shell);
	}
}

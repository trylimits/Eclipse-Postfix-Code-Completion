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


import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringExecutionStarter;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * Action to convert an anonymous inner class to a nested class.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.1
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class ConvertAnonymousToNestedAction extends SelectionDispatchAction {

	private final JavaEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public ConvertAnonymousToNestedAction(JavaEditor editor) {
		super(editor.getEditorSite());
		setText(RefactoringMessages.ConvertAnonymousToNestedAction_Convert_Anonymous);
		fEditor= editor;
		setEnabled(SelectionConverter.getInputAsCompilationUnit(fEditor) != null);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.CONVERT_ANONYMOUS_TO_NESTED_ACTION);
	}

	/**
	 * Creates a new <code>ConvertAnonymousToNestedAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type
	 * <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public ConvertAnonymousToNestedAction(IWorkbenchSite site) {
		super(site);
		fEditor= null;
		setText(RefactoringMessages.ConvertAnonymousToNestedAction_Convert_Anonymous);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.CONVERT_ANONYMOUS_TO_NESTED_ACTION);
	}

	//---- Structured selection -----------------------------------------------------

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isConvertAnonymousAvailable(selection));
		} catch (JavaModelException e) {
			if (JavaModelUtil.isExceptionToBeLogged(e))
				JavaPlugin.log(e);
			setEnabled(false);
		}
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(IStructuredSelection selection) {
		IType type= getElement(selection);
		if (type == null)
			return;
		ISourceRange range;
		try {
			range= type.getNameRange();
			run(type.getCompilationUnit(), range.getOffset(), range.getLength());
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.ConvertAnonymousToNestedAction_dialog_title, RefactoringMessages.NewTextRefactoringAction_exception);
		}
	}

	private IType getElement(IStructuredSelection selection) {
		if (selection.size() != 1)
			return null;
		Object element= selection.getFirstElement();
		if (!(element instanceof IType))
			return null;
		IType type= (IType)element;
		try {
			if (type.isAnonymous())
				return type;
		} catch (JavaModelException e) {
			// fall through
		}
		return null;
	}

	//---- Text selection -----------------------------------------------------------

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(ITextSelection selection) {
		run(SelectionConverter.getInputAsCompilationUnit(fEditor), selection.getOffset(), selection.getLength());
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
		try {
			setEnabled(RefactoringAvailabilityTester.isConvertAnonymousAvailable(selection));
		} catch (JavaModelException e) {
			setEnabled(false);
		}
	}

	//---- helpers -------------------------------------------------------------------

	private void run(ICompilationUnit unit, int offset, int length) {
		if (!ActionUtil.isEditable(fEditor, getShell(), unit))
			return;
		RefactoringExecutionStarter.startConvertAnonymousRefactoring(unit, offset, length, getShell());
	}
}

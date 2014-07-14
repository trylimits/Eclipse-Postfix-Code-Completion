/*******************************************************************************
 * Copyright (c) 2007, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.actions;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringExecutionStarter;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

public class IntroduceParameterObjectAction extends SelectionDispatchAction {

	private JavaEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the compilation unit editor
	 */
	public IntroduceParameterObjectAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(true);
	}

	/**
	 * Creates a new <code>IntroduceIndirectionAction</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public IntroduceParameterObjectAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.IntroduceParameterObjectAction_action_text);
		setToolTipText(ActionMessages.IntroduceParameterObjectAction_action_tooltip);
		setDescription(ActionMessages.IntroduceParameterObjectAction_action_description);
	}

	//---- structured selection --------------------------------------------------

	/*
	 * @see SelectionDispatchAction#selectionChanged(IStructuredSelection)
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isIntroduceParameterObjectAvailable(selection));
		} catch (JavaModelException e) {
			if (JavaModelUtil.isExceptionToBeLogged(e))
				JavaPlugin.log(e);
		}
	}

	/*
	 * @see SelectionDispatchAction#selectionChanged(ITextSelection)
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(true);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#selectionChanged(org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection)
	 */
	@Override
	public void selectionChanged(JavaTextSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isIntroduceParameterObjectAvailable(selection));
		} catch (JavaModelException e) {
			if (JavaModelUtil.isExceptionToBeLogged(e))
				JavaPlugin.log(e);
			setEnabled(false);
		}
	}

	/*
	 * @see SelectionDispatchAction#run(IStructuredSelection)
	 */
	@Override
	public void run(IStructuredSelection selection) {
		try {
			IMethod singleSelectedMethod= getSingleSelectedMethod(selection);
			if (!ActionUtil.isEditable(getShell(), singleSelectedMethod))
				return;
			run(singleSelectedMethod);
		} catch (CoreException e) {
			ExceptionHandler.handle(e, getShell(), ActionMessages.IntroduceParameterObjectAction_exceptiondialog_title,	ActionMessages.IntroduceParameterObjectAction_unexpected_exception);
		}
	}

	/*
	 * (non-Javadoc) Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(ITextSelection selection) {
		try {
			if (!ActionUtil.isEditable(fEditor))
				return;
			run(getSingleSelectedMethod(selection));
		} catch (CoreException e) {
			ExceptionHandler.handle(e, getShell(), ActionMessages.IntroduceParameterObjectAction_exceptiondialog_title,	ActionMessages.IntroduceParameterObjectAction_unexpected_exception);
		}
	}

	@Override
	public void run(JavaTextSelection selection) {
		try {
			IJavaElement[] elements= selection.resolveElementAtOffset();
			if (elements.length != 1)
				return;

			if (!(elements[0] instanceof IMethod))
				return;

			run((IMethod) elements[0]);
		} catch (CoreException e) {
			ExceptionHandler.handle(e, getShell(), ActionMessages.IntroduceParameterObjectAction_exceptiondialog_title,	ActionMessages.IntroduceParameterObjectAction_unexpected_exception);
		}
	}

	private void run(IMethod method) throws CoreException {
		if (method == null) {
			MessageDialog.openError(getShell(), ActionMessages.IntroduceParameterObjectAction_exceptiondialog_title, ActionMessages.IntroduceParameterObjectAction_can_not_run_refactoring_message);
		} else if (fEditor == null || ActionUtil.isEditable(fEditor)) {
			RefactoringExecutionStarter.startIntroduceParameterObject(method, getShell());
		}
	}

	private static IMethod getSingleSelectedMethod(IStructuredSelection selection) {
		if (selection.size() != 1)
			return null;

		Object element= selection.getFirstElement();
		if (!(element instanceof IMethod))
			return null;

		return (IMethod)element;
	}

	private IMethod getSingleSelectedMethod(ITextSelection selection) throws JavaModelException {
		// - when caret/selection on method name (call or declaration) -> that method
		// - otherwise: caret position's enclosing method declaration
		// - when caret inside argument list of method declaration -> enclosing method declaration
		// - when caret inside argument list of method call -> enclosing method declaration (and NOT method call)
		IJavaElement[] elements= SelectionConverter.codeResolve(fEditor);
		if (elements.length > 1)
			return null;

		if (elements.length == 1 && elements[0] instanceof IMethod) {
			return (IMethod) elements[0];
		} else {
			IJavaElement elementAt= SelectionConverter.getInputAsCompilationUnit(fEditor).getElementAt(selection.getOffset());
			if (!(elementAt instanceof IMethod))
				return null;

			return (IMethod) elementAt;
		}
	}
}

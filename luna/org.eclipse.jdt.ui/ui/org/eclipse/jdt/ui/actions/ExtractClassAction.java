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
package org.eclipse.jdt.ui.actions;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringExecutionStarter;

import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.actions.RefactoringActions;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * Extracts selected fields into a new class and replaces the fields with a new field to the new class.
 *  *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 3.4
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class ExtractClassAction extends SelectionDispatchAction {
	private JavaEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public ExtractClassAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor));
	}

	/**
	 * Creates a new <code>ExtractClassAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public ExtractClassAction(IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.ExtractClassAction_action_text);
	}

	//---- structured selection -------------------------------------------

	/*
	 * @see SelectionDispatchAction#selectionChanged(IStructuredSelection)
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		try {
			IType singleSelectedType= RefactoringAvailabilityTester.getSingleSelectedType(selection);
			setEnabled(RefactoringAvailabilityTester.isExtractClassAvailable(singleSelectedType));
		} catch (JavaModelException e) {
			setEnabled(false);
		}
	}

	/*
	 * @see SelectionDispatchAction#run(IStructuredSelection)
	 */
	@Override
	public void run(IStructuredSelection selection) {
		try {
			IType singleSelectedType= RefactoringAvailabilityTester.getSingleSelectedType(selection);
			if (RefactoringAvailabilityTester.isExtractClassAvailable(singleSelectedType)) {
				if (!ActionUtil.isEditable(getShell(), singleSelectedType))
					return;
				RefactoringExecutionStarter.startExtractClassRefactoring(singleSelectedType, getShell());
			}
		} catch (CoreException e) {
			ExceptionHandler.handle(e, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
		}
	}

	/*
	 * @see SelectionDispatchAction#selectionChanged(ITextSelection)
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(true);
	}

	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 * @param selection the changed selection
	 *
	 * @noreference This method is not intended to be referenced by clients.
	 */
	@Override
	public void selectionChanged(JavaTextSelection selection) {
		try {
			IJavaElement element= selection.resolveEnclosingElement();
			if (element != null) {
				IType type= (IType) element.getAncestor(IJavaElement.TYPE);
				setEnabled(RefactoringAvailabilityTester.isExtractClassAvailable(type));
			} else {
				setEnabled(false);
			}
		} catch (JavaModelException e) {
			setEnabled(false);
		}
	}

	/*
	 * @see SelectionDispatchAction#run(ITextSelection)
	 */
	@Override
	public void run(ITextSelection selection) {
		try {
			if (!ActionUtil.isEditable(fEditor))
				return;
			IType type= RefactoringActions.getEnclosingOrPrimaryType(fEditor);
			if (RefactoringAvailabilityTester.isExtractClassAvailable(type)) {
				RefactoringExecutionStarter.startExtractClassRefactoring(type, getShell());
			}
		} catch (CoreException e) {
			ExceptionHandler.handle(e, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
		}
	}
}

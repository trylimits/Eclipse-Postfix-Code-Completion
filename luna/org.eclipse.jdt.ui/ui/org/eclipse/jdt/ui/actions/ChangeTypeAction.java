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

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.PrimitiveType;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringExecutionStarter;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * Action to generalize the type of a local or field declaration or the
 * return type of a method declaration.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 3.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class ChangeTypeAction extends SelectionDispatchAction {

	private JavaEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call
	 * this constructor.
	 * @param editor the java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public ChangeTypeAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.getInputAsCompilationUnit(fEditor) != null);
	}

	/**
	 * Creates a new <code>ChangeTypeAction</code>. The action requires that
	 * the selection provided by the site's selection provider is of type
	 * <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public ChangeTypeAction(IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.ChangeTypeAction_label);
		setToolTipText(RefactoringMessages.ChangeTypeAction_tooltipText);
		setDescription(RefactoringMessages.ChangeTypeAction_description);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.CHANGE_TYPE_ACTION);
	}

	//---- structured selection ---------------------------------------------

	@Override
	public void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isGeneralizeTypeAvailable(selection));
		} catch (JavaModelException e) {
			if (JavaModelUtil.isExceptionToBeLogged(e))
				JavaPlugin.log(e);
			setEnabled(false);
		}
	}

	@Override
	public void run(IStructuredSelection selection) {
		try {
			IMember member= getMember(selection);
			if (member == null || !ActionUtil.isEditable(getShell(), member))
				return;
			ISourceRange range= member.getNameRange();
			RefactoringExecutionStarter.startChangeTypeRefactoring(member.getCompilationUnit(), getShell(), range.getOffset(), range.getLength());
		} catch (CoreException e) {
			ExceptionHandler.handle(e, RefactoringMessages.ChangeTypeAction_dialog_title, RefactoringMessages.ChangeTypeAction_exception);
		}
	}

	private static IMember getMember(IStructuredSelection selection) throws JavaModelException {
		if (selection.size() != 1)
			return null;

		Object element= selection.getFirstElement();
		if (!(element instanceof IMember))
			return null;

		if (element instanceof IMethod) {
			IMethod method= (IMethod)element;
			String returnType= method.getReturnType();
			if (PrimitiveType.toCode(Signature.toString(returnType)) != null)
				return null;
			return method;
		} else if (element instanceof IField && !JdtFlags.isEnum((IMember) element)) {
			return (IField)element;
		}
		return null;
	}

	//---- text selection ------------------------------------------------------------

	/*
	 * (non-Javadoc) Method declared on SelectionDispatchAction
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(true);
	}

	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 * @param selection the java text selection
	 *
	 * @noreference This method is not intended to be referenced by clients.
	 */
	@Override
	public void selectionChanged(JavaTextSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isGeneralizeTypeAvailable(selection));
		} catch (JavaModelException e) {
			setEnabled(false);
		}
	}

	/*
	 * (non-Javadoc) Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(ITextSelection selection) {
		if (!ActionUtil.isEditable(fEditor))
			return;
		RefactoringExecutionStarter.startChangeTypeRefactoring(SelectionConverter.getInputAsCompilationUnit(fEditor), getShell(), selection.getOffset(), selection.getLength());
	}
}

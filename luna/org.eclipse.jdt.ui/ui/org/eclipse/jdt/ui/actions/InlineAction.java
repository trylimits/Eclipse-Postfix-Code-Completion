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

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.actions.InlineConstantAction;
import org.eclipse.jdt.internal.ui.refactoring.actions.InlineMethodAction;

/**
 * Inlines a method, local variable or a static final field.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.1
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class InlineAction extends SelectionDispatchAction {

	private JavaEditor fEditor;
	private final InlineTempAction fInlineTemp;
	private final InlineMethodAction fInlineMethod;
	private final InlineConstantAction fInlineConstant;

	/**
	 * Creates a new <code>InlineAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public InlineAction(IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.InlineAction_Inline);
		fInlineTemp		= new InlineTempAction(site);
		fInlineConstant	= new InlineConstantAction(site);
		fInlineMethod	= new InlineMethodAction(site);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.INLINE_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the compilation unit editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public InlineAction(JavaEditor editor) {
		//don't want to call 'this' here - it'd create useless action objects
		super(editor.getEditorSite());
		setText(RefactoringMessages.InlineAction_Inline);
		fEditor= editor;
		fInlineTemp		= new InlineTempAction(editor);
		fInlineConstant	= new InlineConstantAction(editor);
		fInlineMethod	= new InlineMethodAction(editor);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.INLINE_ACTION);
		setEnabled(SelectionConverter.getInputAsCompilationUnit(fEditor) != null);
	}

	/*
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#selectionChanged(org.eclipse.jface.viewers.ISelection)
	 */
	@Override
	public void selectionChanged(ISelection selection) {
		fInlineConstant.update(selection);
		fInlineMethod.update(selection);
		fInlineTemp.update(selection);
		setEnabled((fInlineTemp.isEnabled() || fInlineConstant.isEnabled() || fInlineMethod.isEnabled()));
	}

	/*
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#run(org.eclipse.jface.text.ITextSelection)
	 */
	@Override
	public void run(ITextSelection selection) {
		if (!ActionUtil.isEditable(fEditor))
			return;

		ITypeRoot typeRoot= SelectionConverter.getInput(fEditor);
		if (typeRoot == null)
			return;

		CompilationUnit node= RefactoringASTParser.parseWithASTProvider(typeRoot, true, null);

		if (typeRoot instanceof ICompilationUnit) {
			ICompilationUnit cu= (ICompilationUnit) typeRoot;
			if (fInlineTemp.isEnabled() && fInlineTemp.tryInlineTemp(cu, node, selection, getShell()))
				return;

			if (fInlineConstant.isEnabled() && fInlineConstant.tryInlineConstant(cu, node, selection, getShell()))
				return;
		}
		//InlineMethod is last (also tries enclosing element):
		if (fInlineMethod.isEnabled() && fInlineMethod.tryInlineMethod(typeRoot, node, selection, getShell()))
			return;

		MessageDialog.openInformation(getShell(), RefactoringMessages.InlineAction_dialog_title, RefactoringMessages.InlineAction_select);
	}

	/*
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#run(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	@Override
	public void run(IStructuredSelection selection) {
		if (fInlineConstant.isEnabled())
			fInlineConstant.run(selection);
		else if (fInlineMethod.isEnabled())
			fInlineMethod.run(selection);
		else
			//inline temp will never be enabled on IStructuredSelection
			//don't bother running it
			Assert.isTrue(! fInlineTemp.isEnabled());
	}
}

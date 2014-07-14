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
import org.eclipse.jface.text.TextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
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
 * Action that encapsulates the a constructor call with a factory
 * method.
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 3.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class IntroduceFactoryAction extends SelectionDispatchAction {

	private JavaEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public IntroduceFactoryAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.getInputAsCompilationUnit(fEditor) != null);
	}

	/**
	 * Creates a new <code>IntroduceFactoryAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public IntroduceFactoryAction(IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.IntroduceFactoryAction_label);
		setToolTipText(RefactoringMessages.IntroduceFactoryAction_tooltipText);
		setDescription(RefactoringMessages.IntroduceFactoryAction_description);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.INTRODUCE_FACTORY_ACTION);
	}

	//---- structured selection --------------------------------------------------

	/*
	 * @see SelectionDispatchAction#selectionChanged(IStructuredSelection)
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isIntroduceFactoryAvailable(selection));
		} catch (JavaModelException e) {
			if (JavaModelUtil.isExceptionToBeLogged(e))
				JavaPlugin.log(e);
			setEnabled(false);//no UI here - happens on selection changes
		}
	}

	/*
	 * @see SelectionDispatchAction#run(IStructuredSelection)
	 */
	@Override
	public void run(IStructuredSelection selection) {
		try {
			// we have to call this here - no selection changed event is sent after a refactoring but it may still invalidate enablement
			if (RefactoringAvailabilityTester.isIntroduceFactoryAvailable(selection)) {
				IMethod method= (IMethod) selection.getFirstElement();
				if (!ActionUtil.isEditable(getShell(), method))
					return;
				ISourceRange range= method.getNameRange();
				RefactoringExecutionStarter.startIntroduceFactoryRefactoring(method.getCompilationUnit(), new TextSelection(range.getOffset(), range.getLength()), getShell());
			}
		} catch (CoreException e) {
			ExceptionHandler.handle(e, RefactoringMessages.IntroduceFactoryAction_dialog_title, RefactoringMessages.IntroduceFactoryAction_exception);
		}
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
	 * 
	 * @param selection the Java text selection
	 * @noreference This method is not intended to be referenced by clients.
	 */
	@Override
	public void selectionChanged(JavaTextSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isIntroduceFactoryAvailable(selection));
		} catch (JavaModelException e) {
			setEnabled(false);
		}
	}

	@Override
	public void run(ITextSelection selection) {
		if (!ActionUtil.isEditable(fEditor))
			return;
		RefactoringExecutionStarter.startIntroduceFactoryRefactoring(SelectionConverter.getInputAsCompilationUnit(fEditor), selection, getShell());
	}
}

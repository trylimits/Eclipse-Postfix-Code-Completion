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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
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
 * Action to pull up method and fields into a superclass.
 * <p>
 * Action is applicable to selections containing elements of type
 * <code>IType</code> (top-level types only), <code>IField</code> and
 * <code>IMethod</code>.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class PullUpAction extends SelectionDispatchAction {

	private static IMember[] getSelectedMembers(IStructuredSelection selection) {
		if (selection.isEmpty())
			return null;
		if (selection.size() == 1) {
			try {
				final IType type= RefactoringAvailabilityTester.getSingleSelectedType(selection);
				if (type != null)
					return new IType[] { type};
			} catch (JavaModelException exception) {
				JavaPlugin.log(exception);
			}
		}
		for (Iterator<?> iter= selection.iterator(); iter.hasNext();) {
			if (!(iter.next() instanceof IMember))
				return null;
		}
		Set<Object> memberSet= new HashSet<Object>();
		memberSet.addAll(Arrays.asList(selection.toArray()));
		return memberSet.toArray(new IMember[memberSet.size()]);
	}

	private JavaEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call
	 * this constructor.
	 *
	 * @param editor
	 *            the java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public PullUpAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor));
	}

	/**
	 * Creates a new <code>PullUpAction</code>. The action requires that the
	 * selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site
	 *            the site providing context information for this action
	 */
	public PullUpAction(IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.RefactoringGroup_pull_Up_label);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.PULL_UP_ACTION);
	}

	private IMember getSelectedMemberFromEditor() throws JavaModelException {
		IJavaElement element= SelectionConverter.resolveEnclosingElement(fEditor, (ITextSelection) fEditor.getSelectionProvider().getSelection());
		if (element == null || !(element instanceof IMember))
			return null;
		return (IMember) element;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run(IStructuredSelection selection) {
		try {
			IMember[] members= getSelectedMembers(selection);
			if (RefactoringAvailabilityTester.isPullUpAvailable(members) && ActionUtil.isEditable(getShell(), members[0]))
				RefactoringExecutionStarter.startPullUpRefactoring(members, getShell());
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run(ITextSelection selection) {
		try {
			if (!ActionUtil.isEditable(fEditor))
				return;
			IMember member= getSelectedMemberFromEditor();
			IMember[] array= new IMember[] { member};
			if (member != null && RefactoringAvailabilityTester.isPullUpAvailable(array)) {
				RefactoringExecutionStarter.startPullUpRefactoring(array, getShell());
			} else {
				MessageDialog.openInformation(getShell(), RefactoringMessages.OpenRefactoringWizardAction_unavailable, RefactoringMessages.PullUpAction_unavailable);
			}
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isPullUpAvailable(selection));
		} catch (JavaModelException e) {
			// http://bugs.eclipse.org/bugs/show_bug.cgi?id=19253
			if (JavaModelUtil.isExceptionToBeLogged(e))
				JavaPlugin.log(e);
			setEnabled(false);// no UI
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(true);
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
			setEnabled(RefactoringAvailabilityTester.isPullUpAvailable(selection));
		} catch (JavaModelException e) {
			setEnabled(false);
		}
	}
}
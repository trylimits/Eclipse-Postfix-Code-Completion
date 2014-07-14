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
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.actions.MoveInstanceMethodAction;
import org.eclipse.jdt.internal.ui.refactoring.actions.MoveStaticMembersAction;
import org.eclipse.jdt.internal.ui.refactoring.reorg.ReorgMoveAction;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * This action moves Java elements to a new location. The action prompts
 * the user for the new location.
 * <p>
 * The action is applicable to a homogeneous selection containing either
 * projects, package fragment roots, package fragments, compilation units,
 * or static methods.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class MoveAction extends SelectionDispatchAction{
//TODO: remove duplicate availability checks. Look at
//- f...Action.selectionChanged
//- f...Action.isEnabled
//- ...Refactoring.isAvailable
//- try...
//... and remove duplicated code for text/structured selections.
//We have to clean this up, once we have a long term solution to
//bug 35748 (no JavaElements for local types).

	private JavaEditor fEditor;
	private MoveInstanceMethodAction fMoveInstanceMethodAction;
	private MoveStaticMembersAction fMoveStaticMembersAction;
	private ReorgMoveAction fReorgMoveAction;

	/**
	 * Creates a new <code>MoveAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public MoveAction(IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.MoveAction_text);
		fMoveStaticMembersAction= new MoveStaticMembersAction(site);
		fMoveInstanceMethodAction= new MoveInstanceMethodAction(site);
		fReorgMoveAction= new ReorgMoveAction(site);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.MOVE_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public MoveAction(JavaEditor editor) {
		super(editor.getEditorSite());
		fEditor= editor;
		setText(RefactoringMessages.MoveAction_text);
		fMoveStaticMembersAction= new MoveStaticMembersAction(editor);
		fMoveInstanceMethodAction= new MoveInstanceMethodAction(editor);
		fReorgMoveAction= new ReorgMoveAction(editor.getEditorSite());
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.MOVE_ACTION);
	}

	/*
	 * @see ISelectionChangedListener#selectionChanged(SelectionChangedEvent)
	 */
	@Override
	public void selectionChanged(SelectionChangedEvent event) {
		fMoveStaticMembersAction.selectionChanged(event);
		fMoveInstanceMethodAction.selectionChanged(event);
		fReorgMoveAction.selectionChanged(event);
		setEnabled(computeEnableState());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#setSpecialSelectionProvider(org.eclipse.jface.viewers.ISelectionProvider)
	 */
	@Override
	public void setSpecialSelectionProvider(ISelectionProvider provider) {
		super.setSpecialSelectionProvider(provider);

		fMoveInstanceMethodAction.setSpecialSelectionProvider(provider);
		fMoveStaticMembersAction.setSpecialSelectionProvider(provider);
		fReorgMoveAction.setSpecialSelectionProvider(provider);
	}

	/*
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#run(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	@Override
	public void run(IStructuredSelection selection) {
		try {
			if (fMoveInstanceMethodAction.isEnabled() && tryMoveInstanceMethod(selection))
				return;

			if (fMoveStaticMembersAction.isEnabled() && tryMoveStaticMembers(selection))
				return;

			if (fReorgMoveAction.isEnabled())
				fReorgMoveAction.run(selection);

		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
		}

	}

	/*
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#run(org.eclipse.jface.text.ITextSelection)
	 */
	@Override
	public void run(ITextSelection selection) {
		try {
			if (!ActionUtil.isEditable(fEditor))
				return;
			if (fMoveStaticMembersAction.isEnabled() && tryMoveStaticMembers(selection))
				return;

			if (fMoveInstanceMethodAction.isEnabled() && tryMoveInstanceMethod(selection))
				return;

			if (tryReorgMove())
				return;

			MessageDialog.openInformation(getShell(), RefactoringMessages.MoveAction_Move, RefactoringMessages.MoveAction_select);
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
		}
	}

	private boolean tryMoveStaticMembers(ITextSelection selection) throws JavaModelException {
		IJavaElement element= SelectionConverter.getElementAtOffset(fEditor);
		if (element == null || !(element instanceof IMember))
			return false;
		IMember[] array= new IMember[] { (IMember) element};
		if (!RefactoringAvailabilityTester.isMoveStaticMembersAvailable(array))
			return false;
		fMoveStaticMembersAction.run(selection);
		return true;
	}

	private static IMember[] getSelectedMembers(IStructuredSelection selection){
		if (selection.isEmpty())
			return null;

		for  (Iterator<?> iter= selection.iterator(); iter.hasNext(); ) {
			if (! (iter.next() instanceof IMember))
				return null;
		}
		return convertToMemberArray(selection.toArray());
	}

	private static IMember[] convertToMemberArray(Object[] obj) {
		if (obj == null)
			return null;
		Set<Object> memberSet= new HashSet<Object>();
		memberSet.addAll(Arrays.asList(obj));
		return memberSet.toArray(new IMember[memberSet.size()]);
	}

	private boolean tryMoveStaticMembers(IStructuredSelection selection) throws JavaModelException {
		IMember[] array= getSelectedMembers(selection);
		if (!RefactoringAvailabilityTester.isMoveStaticMembersAvailable(array))
			return false;
		fMoveStaticMembersAction.run(selection);
		return true;
	}

	private boolean tryMoveInstanceMethod(ITextSelection selection) throws JavaModelException {
		IJavaElement element= SelectionConverter.getElementAtOffset(fEditor);
		if (element == null || !(element instanceof IMethod))
			return false;

		IMethod method= (IMethod) element;
		if (!RefactoringAvailabilityTester.isMoveMethodAvailable(method))
			return false;
		fMoveInstanceMethodAction.run(selection);
		return true;
	}

	private boolean tryMoveInstanceMethod(IStructuredSelection selection) throws JavaModelException {
		IMethod method= getSingleSelectedMethod(selection);
		if (method == null)
			return false;
		if (!RefactoringAvailabilityTester.isMoveMethodAvailable(method))
			return false;
		fMoveInstanceMethodAction.run(selection);
		return true;
	}

	private static IMethod getSingleSelectedMethod(IStructuredSelection selection) {
		if (selection.isEmpty() || selection.size() != 1)
			return null;

		Object first= selection.getFirstElement();
		if (! (first instanceof IMethod))
			return null;
		return (IMethod) first;
	}


	private boolean tryReorgMove() throws JavaModelException {
		IJavaElement element= SelectionConverter.getElementAtOffset(fEditor);
		if (element == null)
			return false;
		StructuredSelection mockStructuredSelection= new StructuredSelection(element);
		fReorgMoveAction.selectionChanged(mockStructuredSelection);
		if (!fReorgMoveAction.isEnabled())
			return false;

		fReorgMoveAction.run(mockStructuredSelection);
		return true;
	}


	/*
	 * @see SelectionDispatchAction#update(ISelection)
	 */
	@Override
	public void update(ISelection selection) {
		fMoveStaticMembersAction.update(selection);
		fMoveInstanceMethodAction.update(selection);
		fReorgMoveAction.update(selection);
		setEnabled(computeEnableState());
	}

	private boolean computeEnableState(){
		return fMoveStaticMembersAction.isEnabled()
				|| fMoveInstanceMethodAction.isEnabled()
				|| fReorgMoveAction.isEnabled();
	}
}

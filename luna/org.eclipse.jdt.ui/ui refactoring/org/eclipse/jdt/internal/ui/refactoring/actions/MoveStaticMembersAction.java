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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringExecutionStarter;
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

public class MoveStaticMembersAction extends SelectionDispatchAction{

	private JavaEditor fEditor;

	public MoveStaticMembersAction(IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.RefactoringGroup_move_label);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.MOVE_ACTION);
	}

	public MoveStaticMembersAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor));
	}

	@Override
	public void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isMoveStaticMembersAvailable(getSelectedMembers(selection)));
		} catch (JavaModelException e) {
			// http://bugs.eclipse.org/bugs/show_bug.cgi?id=19253
			if (JavaModelUtil.isExceptionToBeLogged(e))
				JavaPlugin.log(e);
			setEnabled(false);//no ui
		}
	}

	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(true);
	}

	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 */
	@Override
	public void selectionChanged(JavaTextSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isMoveStaticAvailable(selection));
		} catch (JavaModelException e) {
			setEnabled(false);
		}
	}

	@Override
	public void run(IStructuredSelection selection) {
		try {
			IMember[] members= getSelectedMembers(selection);
			for (int index= 0; index < members.length; index++) {
				if (!ActionUtil.isEditable(getShell(), members[index]))
					return;
			}
			if (RefactoringAvailabilityTester.isMoveStaticMembersAvailable(members))
				RefactoringExecutionStarter.startMoveStaticMembersRefactoring(members, getShell());
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
		}
	}

	@Override
	public void run(ITextSelection selection) {
		try {
			IMember member= getSelectedMemberFromEditor();
			if (!ActionUtil.isEditable(fEditor, getShell(), member))
				return;
			IMember[] array= new IMember[]{member};
			if (member != null && RefactoringAvailabilityTester.isMoveStaticMembersAvailable(array)){
				RefactoringExecutionStarter.startMoveStaticMembersRefactoring(array, getShell());
			} else {
				MessageDialog.openInformation(getShell(), RefactoringMessages.OpenRefactoringWizardAction_unavailable, RefactoringMessages.MoveMembersAction_unavailable);
			}
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
		}
	}

	private static IMember[] getSelectedMembers(IStructuredSelection selection){
		if (selection.isEmpty())
			return null;

		for  (final Iterator<?> iterator= selection.iterator(); iterator.hasNext(); ) {
			if (! (iterator.next() instanceof IMember))
				return null;
		}
		Set<IMember> memberSet= new HashSet<IMember>();
		@SuppressWarnings("unchecked")
		List<IMember> selectionList= (List<IMember>) (List<?>) Arrays.asList(selection.toArray());
		memberSet.addAll(selectionList);
		return memberSet.toArray(new IMember[memberSet.size()]);
	}

	private IMember getSelectedMemberFromEditor() throws JavaModelException{
		IJavaElement element= SelectionConverter.getElementAtOffset(fEditor);
		if (element == null || ! (element instanceof IMember))
			return null;
		return (IMember)element;
	}
}

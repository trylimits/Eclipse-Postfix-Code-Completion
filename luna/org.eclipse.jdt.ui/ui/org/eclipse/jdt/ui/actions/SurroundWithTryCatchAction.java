/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sebastian Davids - Fixed bug 25898
 *******************************************************************************/
package org.eclipse.jdt.ui.actions;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jface.dialogs.MessageDialog;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ISourceRange;

import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.surround.SurroundWithTryCatchRefactoring;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.ui.util.ElementValidator;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.LinkedProposalModelPresenter;

/**
 * Action to surround a set of statements with a try/catch block.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class SurroundWithTryCatchAction extends SelectionDispatchAction {

	CompilationUnitEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the compilation unit editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public SurroundWithTryCatchAction(CompilationUnitEditor editor) {
		super(editor.getEditorSite());
		setText(RefactoringMessages.SurroundWithTryCatchAction_label);
		fEditor= editor;
		setEnabled(isApplicable());
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.SURROUND_WITH_TRY_CATCH_ACTION);
	}

	@Override
	public void run(ITextSelection selection) {
		if (!ActionUtil.isEditable(fEditor))
			return;
		ICompilationUnit cu= SelectionConverter.getInputAsCompilationUnit(fEditor);
		if (cu == null || !ElementValidator.checkValidateEdit(cu, getShell(), getDialogTitle()))
			return;
		SurroundWithTryCatchRefactoring refactoring= createRefactoring(selection, cu);

		if (refactoring == null)
			return;
		try {
			RefactoringStatus status= refactoring.checkInitialConditions(new NullProgressMonitor());
			if (status.hasFatalError()) {
				RefactoringStatusEntry entry= status.getEntryMatchingSeverity(RefactoringStatus.FATAL);
				MessageDialog.openInformation(getShell(), getDialogTitle(), entry.getMessage());
				if (entry.getContext() instanceof JavaStatusContext && fEditor != null) {
					JavaStatusContext context= (JavaStatusContext)entry.getContext();
					ISourceRange range= context.getSourceRange();
					fEditor.setHighlightRange(range.getOffset(), range.getLength(), true);
				}
				return;
			}
			if (refactoring.stopExecution())
				return;
			Change change= refactoring.createChange(new NullProgressMonitor());
			change.initializeValidationData(new NullProgressMonitor());
			PerformChangeOperation op= new PerformChangeOperation(change);
			// must be fork == false since file buffers can't be manipulated in a different thread.
			WorkbenchRunnableAdapter adapter= new WorkbenchRunnableAdapter(op);
			PlatformUI.getWorkbench().getProgressService().runInUI(
				new BusyIndicatorRunnableContext(), adapter, adapter.getSchedulingRule());

			new LinkedProposalModelPresenter().enterLinkedMode(fEditor.getViewer(), fEditor, false, refactoring.getLinkedProposalModel());

		} catch (CoreException e) {
			ExceptionHandler.handle(e, getDialogTitle(), RefactoringMessages.SurroundWithTryCatchAction_exception);
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getDialogTitle(), RefactoringMessages.SurroundWithTryCatchAction_exception);
		} catch (InterruptedException e) {
			// not cancelable
		} catch (BadLocationException e) {
			// ignore
		}
	}

	SurroundWithTryCatchRefactoring createRefactoring(ITextSelection selection, ICompilationUnit cu) {
		return SurroundWithTryCatchRefactoring.create(cu, selection);
	}

	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(selection.getLength() > 0 && isApplicable());
	}

	boolean isApplicable() {
		return fEditor != null && SelectionConverter.getInputAsCompilationUnit(fEditor) != null;
	}

	String getDialogTitle() {
		return RefactoringMessages.SurroundWithTryCatchAction_dialog_title;
	}
}

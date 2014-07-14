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

import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;

import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.codemanipulation.SortMembersOperation;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.dialogs.OptionalMessageDialog;
import org.eclipse.jdt.internal.ui.dialogs.SortMembersMessageDialog;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.ui.util.ElementValidator;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * Sorts the members of a compilation unit with the sort order as specified in
 * the Sort Order preference page.
 * <p>
 * The action will open the parent compilation unit in a Java editor. The result
 * is unsaved, so the user can decide if the changes are acceptable.
 * <p>
 * The action is applicable to structured selections containing a single
 * <code>ICompilationUnit</code> or top level <code>IType</code> in a
 * compilation unit.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.1
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class SortMembersAction extends SelectionDispatchAction {

	private CompilationUnitEditor fEditor;
	private final static String ID_OPTIONAL_DIALOG= "org.eclipse.jdt.ui.actions.SortMembersAction"; //$NON-NLS-1$

	/**
	 * Creates a new <code>SortMembersAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public SortMembersAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.SortMembersAction_label);
		setDescription(ActionMessages.SortMembersAction_description);
		setToolTipText(ActionMessages.SortMembersAction_tooltip);

		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.SORT_MEMBERS_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the compilation unit editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public SortMembersAction(CompilationUnitEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(checkEnabledEditor());
	}

	private boolean checkEnabledEditor() {
		return fEditor != null && SelectionConverter.canOperateOn(fEditor);
	}

	//---- Structured Viewer -----------------------------------------------------------

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		boolean enabled= false;
		enabled= getSelectedCompilationUnit(selection) != null;
		setEnabled(enabled);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(IStructuredSelection selection) {
		Shell shell= getShell();
		try {
			ICompilationUnit cu= getSelectedCompilationUnit(selection);
			if (cu == null) {
				return;
			}
			IType[] types= cu.getTypes();
			if (!hasMembersToSort(types)) {
				return;
			}
			if (!ActionUtil.isEditable(getShell(), cu)) {
				return;
			}

			SortMembersMessageDialog dialog= new SortMembersMessageDialog(getShell());
			if (dialog.open() != Window.OK) {
				return;
			}

			if (!ElementValidator.check(cu, getShell(), getDialogTitle(), false)) {
				return;
			}

			// open an editor and work on a working copy
			IEditorPart editor= JavaUI.openInEditor(cu);
			if (editor != null) {
				run(shell, cu, editor, dialog.isNotSortingFieldsEnabled());
			}
		} catch (CoreException e) {
			ExceptionHandler.handle(e, shell, getDialogTitle(), null);
		}
	}

	private boolean hasMembersToSort(IJavaElement[] members) throws JavaModelException {
		if (members.length > 1) {
			return true;
		}
		if (members.length == 1) {
			IJavaElement elem= members[0];
			if (elem instanceof IParent) {
				return hasMembersToSort(((IParent) elem).getChildren());
			}
		}
		return false;
	}

	//---- Java Editor --------------------------------------------------------------

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(ITextSelection selection) {
		Shell shell= getShell();
		IJavaElement input= SelectionConverter.getInput(fEditor);
		if (input instanceof ICompilationUnit) {
			if (!ActionUtil.isEditable(fEditor)) {
				return;
			}
			SortMembersMessageDialog dialog= new SortMembersMessageDialog(getShell());
			if (dialog.open() != Window.OK) {
				return;
			}
			if (!ElementValidator.check(input, getShell(), getDialogTitle(), true)) {
				return;
			}
			run(shell, (ICompilationUnit) input, fEditor, dialog.isNotSortingFieldsEnabled());
		} else {
			MessageDialog.openInformation(shell, getDialogTitle(), ActionMessages.SortMembersAction_not_applicable);
		}
	}

	//---- Helpers -------------------------------------------------------------------

	private boolean containsRelevantMarkers(IEditorPart editor) {
		IAnnotationModel model= JavaUI.getDocumentProvider().getAnnotationModel(editor.getEditorInput());
		Iterator<Annotation> iterator= model.getAnnotationIterator();
		while (iterator.hasNext()) {
			Annotation element= iterator.next();
			if (element instanceof IJavaAnnotation) {
				IJavaAnnotation annot= (IJavaAnnotation) element;
				if (!annot.isMarkedDeleted() && annot.isPersistent() && !annot.isProblem())
					return true;
			}
		}
		return false;
	}

	private void run(Shell shell, ICompilationUnit cu, IEditorPart editor, boolean isNotSortFields) {
		if (containsRelevantMarkers(editor)) {
			int returnCode= OptionalMessageDialog.open(ID_OPTIONAL_DIALOG,
					getShell(),
					getDialogTitle(),
					null,
					ActionMessages.SortMembersAction_containsmarkers,
					MessageDialog.WARNING,
					new String[] {IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL},
					0);
			if (returnCode != OptionalMessageDialog.NOT_SHOWN &&
					returnCode != Window.OK ) return;
		}

		SortMembersOperation op= new SortMembersOperation(cu, null, isNotSortFields);
		try {
			BusyIndicatorRunnableContext context= new BusyIndicatorRunnableContext();
			PlatformUI.getWorkbench().getProgressService().runInUI(context,
				new WorkbenchRunnableAdapter(op, op.getScheduleRule()),
				op.getScheduleRule());
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, shell, getDialogTitle(), null);
		} catch (InterruptedException e) {
			// Do nothing. Operation has been canceled by user.
		}
	}


	private ICompilationUnit getSelectedCompilationUnit(IStructuredSelection selection) {
		if (selection.size() == 1) {
			Object element= selection.getFirstElement();
			if (element instanceof ICompilationUnit) {
				return (ICompilationUnit) element;
			} else if (element instanceof IType) {
				IType type= (IType) element;
				if (type.getParent() instanceof ICompilationUnit) { // only top level types
					return type.getCompilationUnit();
				}
			}
		}
		return null;
	}

	private String getDialogTitle() {
		return ActionMessages.SortMembersAction_dialog_title;
	}
}

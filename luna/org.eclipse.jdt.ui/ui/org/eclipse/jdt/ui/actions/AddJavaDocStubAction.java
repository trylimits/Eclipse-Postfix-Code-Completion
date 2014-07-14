/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
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
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.codemanipulation.AddJavaDocStubOperation;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.util.ElementValidator;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * Create Javadoc comment stubs for the selected members.
 * <p>
 * Will open the parent compilation unit in a Java editor. The result is
 * unsaved, so the user can decide if the changes are acceptable.
 * <p>
 * The action is applicable to structured selections containing elements
 * of type <code>IMember</code>.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class AddJavaDocStubAction extends SelectionDispatchAction {

	private CompilationUnitEditor fEditor;

	/**
	 * Creates a new <code>AddJavaDocStubAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public AddJavaDocStubAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.AddJavaDocStubAction_label);
		setDescription(ActionMessages.AddJavaDocStubAction_description);
		setToolTipText(ActionMessages.AddJavaDocStubAction_tooltip);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.ADD_JAVADOC_STUB_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the compilation unit editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public AddJavaDocStubAction(CompilationUnitEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(checkEnabledEditor());
	}

	//---- Structured Viewer -----------------------------------------------------------

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		IMember[] members= getSelectedMembers(selection);
		setEnabled(members != null && members.length > 0);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(IStructuredSelection selection) {
		IMember[] members= getSelectedMembers(selection);
		if (members == null || members.length == 0) {
			return;
		}

		try {
			ICompilationUnit cu= members[0].getCompilationUnit();
			if (!ActionUtil.isEditable(getShell(), cu)) {
				return;
			}

			// open the editor, forces the creation of a working copy
			IEditorPart editor= JavaUI.openInEditor(cu);

			if (ElementValidator.check(members, getShell(), getDialogTitle(), false))
				run(members);
			JavaModelUtil.reconcile(cu);
			EditorUtility.revealInEditor(editor, members[0]);

		} catch (CoreException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.AddJavaDocStubsAction_error_actionFailed);
		}
	}

	//---- Java Editor --------------------------------------------------------------

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
	}

	private boolean checkEnabledEditor() {
		return fEditor != null && SelectionConverter.canOperateOn(fEditor);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(ITextSelection selection) {
		try {
			IJavaElement element= SelectionConverter.getElementAtOffset(fEditor);
			if (!ActionUtil.isEditable(fEditor, getShell(), element))
				return;
			int type= element != null ? element.getElementType() : -1;
			if (type != IJavaElement.METHOD && type != IJavaElement.TYPE && type != IJavaElement.FIELD) {
		 		element= SelectionConverter.getTypeAtOffset(fEditor);
		 		if (element == null) {
					MessageDialog.openInformation(getShell(), getDialogTitle(),
						ActionMessages.AddJavaDocStubsAction_not_applicable);
					return;
		 		}
			}
			IMember[] members= new IMember[] { (IMember)element };
			if (ElementValidator.checkValidateEdit(members, getShell(), getDialogTitle()))
				run(members);
		} catch (CoreException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.AddJavaDocStubsAction_error_actionFailed);
		}
	}

	//---- Helpers -------------------------------------------------------------------

	/**
	 * Note this method is for internal use only.
	 *
	 * @param members an array of members
	 */
	private void run(IMember[] members) {
		AddJavaDocStubOperation op= new AddJavaDocStubOperation(members);
		if (members.length < 11) {
			try {
				op.run(null);
			} catch (CoreException e) {
				ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.AddJavaDocStubsAction_error_actionFailed);
			} catch (OperationCanceledException e) {
				// operation canceled
			}
			return;
		}

		try {
			PlatformUI.getWorkbench().getProgressService().runInUI(
				PlatformUI.getWorkbench().getProgressService(),
				new WorkbenchRunnableAdapter(op, op.getScheduleRule()),
				op.getScheduleRule());
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.AddJavaDocStubsAction_error_actionFailed);
		} catch (InterruptedException e) {
			// operation canceled
		}
	}

	private IMember[] getSelectedMembers(IStructuredSelection selection) {
		List<?> elements= selection.toList();
		int nElements= elements.size();
		if (nElements > 0) {
			IMember[] res= new IMember[nElements];
			ICompilationUnit cu= null;
			for (int i= 0; i < nElements; i++) {
				Object curr= elements.get(i);
				if (curr instanceof IMethod || curr instanceof IType || curr instanceof IField) {
					IMember member= (IMember)curr; // limit to methods, types & fields
					if (! member.exists()) {
						return null;
					}
					if (i == 0) {
						cu= member.getCompilationUnit();
						if (cu == null) {
							return null;
						}
					} else if (!cu.equals(member.getCompilationUnit())) {
						return null;
					}
					if (member instanceof IMethod && ((IMethod) member).isLambdaMethod()) {
						return null;
					}
					try {
						if (member instanceof IType) {
							IType type= (IType) member;
							if (type.isAnonymous() || type.isLambda()) {
								return null;
							}
						}
					} catch (JavaModelException e) {
						return null;
					}
					res[i]= member;
				} else {
					return null;
				}
			}
			return res;
		}
		return null;
	}

	private String getDialogTitle() {
		return ActionMessages.AddJavaDocStubsAction_error_dialogTitle;
	}
}

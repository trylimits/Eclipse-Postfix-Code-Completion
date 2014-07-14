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
import java.util.ArrayList;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.resources.IWorkspaceRunnable;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;

import org.eclipse.jface.text.IRewriteTarget;
import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;

import org.eclipse.jdt.internal.corext.codemanipulation.AddUnimplementedMethodsOperation;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.dialogs.OverrideMethodDialog;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.ui.util.ElementValidator;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * Adds unimplemented methods of a type. The action opens a dialog from which the user can
 * choose the methods to be added.
 * <p>
 * Will open the parent compilation unit in a Java editor. The result is unsaved, so the
 * user can decide if the changes are acceptable.
 * <p>
 * The action is applicable to structured selections containing elements of type
 * {@link org.eclipse.jdt.core.IType}.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class OverrideMethodsAction extends SelectionDispatchAction {

	/** The dialog title */
	private static final String DIALOG_TITLE= ActionMessages.OverrideMethodsAction_error_title;

	/** The compilation unit editor */
	private CompilationUnitEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this
	 * constructor.
	 * @param editor the compilation unit editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public OverrideMethodsAction(final CompilationUnitEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(checkEnabledEditor());
	}

	/**
	 * Creates a new override method action.
	 * <p>
	 * The action requires that the selection provided by the site's selection provider is
	 * of type {@link org.eclipse.jface.viewers.IStructuredSelection}.
	 *
	 * @param site the workbench site providing context information for this action
	 */
	public OverrideMethodsAction(final IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.OverrideMethodsAction_label);
		setDescription(ActionMessages.OverrideMethodsAction_description);
		setToolTipText(ActionMessages.OverrideMethodsAction_tooltip);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.ADD_UNIMPLEMENTED_METHODS_ACTION);
	}

	private boolean canEnable(IStructuredSelection selection) throws JavaModelException {
		if ((selection.size() == 1) && (selection.getFirstElement() instanceof IType)) {
			final IType type= (IType) selection.getFirstElement();
			return type.getCompilationUnit() != null && !type.isInterface();
		}
		if ((selection.size() == 1) && (selection.getFirstElement() instanceof ICompilationUnit))
			return true;
		return false;
	}

	private boolean checkEnabledEditor() {
		return fEditor != null && SelectionConverter.canOperateOn(fEditor);
	}

	private String getDialogTitle() {
		return DIALOG_TITLE;
	}

	private IType getSelectedType(IStructuredSelection selection) throws JavaModelException {
		final Object[] elements= selection.toArray();
		if (elements.length == 1 && (elements[0] instanceof IType)) {
			final IType type= (IType) elements[0];
			if (type.getCompilationUnit() != null && !type.isInterface()) {
				return type;
			}
		} else if (elements[0] instanceof ICompilationUnit) {
			final IType type= ((ICompilationUnit) elements[0]).findPrimaryType();
			if (type != null && !type.isInterface())
				return type;
		}
		return null;
	}

	/*
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#run(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	@Override
	public void run(IStructuredSelection selection) {
		try {
			final IType type= getSelectedType(selection);
			if (type == null) {
				MessageDialog.openInformation(getShell(), getDialogTitle(), ActionMessages.OverrideMethodsAction_not_applicable);
				notifyResult(false);
				return;
			}
			if (!ElementValidator.check(type, getShell(), getDialogTitle(), false) || !ActionUtil.isEditable(getShell(), type)) {
				notifyResult(false);
				return;
			}
			run(getShell(), type);
		} catch (CoreException exception) {
			ExceptionHandler.handle(exception, getShell(), getDialogTitle(), ActionMessages.OverrideMethodsAction_error_actionfailed);
		}
	}

	/*
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#run(org.eclipse.jface.text.ITextSelection)
	 */
	@Override
	public void run(ITextSelection selection) {
		try {
			final IType type= SelectionConverter.getTypeAtOffset(fEditor);
			if (type != null) {
				if (!ElementValidator.check(type, getShell(), getDialogTitle(), false) || !ActionUtil.isEditable(fEditor, getShell(), type)) {
					notifyResult(false);
					return;
				}
				if (type.isAnnotation()) {
					MessageDialog.openInformation(getShell(), getDialogTitle(), ActionMessages.OverrideMethodsAction_annotation_not_applicable);
					notifyResult(false);
					return;
				}
				if (type.isInterface()) {
					MessageDialog.openInformation(getShell(), getDialogTitle(), ActionMessages.OverrideMethodsAction_interface_not_applicable);
					notifyResult(false);
					return;
				}
				run(getShell(), type);
			} else {
				MessageDialog.openInformation(getShell(), getDialogTitle(), ActionMessages.OverrideMethodsAction_not_applicable);
			}
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), null);
		} catch (CoreException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.OverrideMethodsAction_error_actionfailed);
		}
	}

	private void run(Shell shell, IType type) throws CoreException {
		final OverrideMethodDialog dialog= new OverrideMethodDialog(shell, fEditor, type, false);
		if (!dialog.hasMethodsToOverride()) {
			MessageDialog.openInformation(shell, getDialogTitle(), ActionMessages.OverrideMethodsAction_error_nothing_found);
			notifyResult(false);
			return;
		}
		if (dialog.open() != Window.OK) {
			notifyResult(false);
			return;
		}

		final Object[] selected= dialog.getResult();
		if (selected == null) {
			notifyResult(false);
			return;
		}

		ArrayList<IMethodBinding> methods= new ArrayList<IMethodBinding>();
		for (int i= 0; i < selected.length; i++) {
			Object elem= selected[i];
			if (elem instanceof IMethodBinding) {
				methods.add((IMethodBinding) elem);
			}
		}
		IMethodBinding[] methodToOverride= methods.toArray(new IMethodBinding[methods.size()]);


		final IEditorPart editor= JavaUI.openInEditor(type.getCompilationUnit());
		final IRewriteTarget target= editor != null ? (IRewriteTarget) editor.getAdapter(IRewriteTarget.class) : null;
		if (target != null)
			target.beginCompoundChange();
		try {
			CompilationUnit astRoot= dialog.getCompilationUnit();
			final ITypeBinding typeBinding= ASTNodes.getTypeBinding(astRoot, type);
			int insertPos= dialog.getInsertOffset();

			AddUnimplementedMethodsOperation operation= (AddUnimplementedMethodsOperation) createRunnable(astRoot, typeBinding, methodToOverride, insertPos, dialog.getGenerateComment());
			IRunnableContext context= JavaPlugin.getActiveWorkbenchWindow();
			if (context == null)
				context= new BusyIndicatorRunnableContext();
			PlatformUI.getWorkbench().getProgressService().runInUI(context, new WorkbenchRunnableAdapter(operation, operation.getSchedulingRule()), operation.getSchedulingRule());
			final String[] created= operation.getCreatedMethods();
			if (created == null || created.length == 0)
				MessageDialog.openInformation(shell, getDialogTitle(), ActionMessages.OverrideMethodsAction_error_nothing_found);
		} catch (InvocationTargetException exception) {
			ExceptionHandler.handle(exception, shell, getDialogTitle(), null);
		} catch (InterruptedException exception) {
			// Do nothing. Operation has been canceled by user.
		} finally {
			if (target != null)
				target.endCompoundChange();
		}
		notifyResult(true);
	}

	/**
	 * Returns a runnable that creates the method stubs for overridden methods.
	 *
	 * @param astRoot the AST of the compilation unit to work on. The AST must have been created from a {@link ICompilationUnit}, that
	 * means {@link ASTParser#setSource(ICompilationUnit)} was used.
	 * @param type the binding of the type to add the new methods to. The type binding must correspond to a type declaration in the AST.
	 * @param methodToOverride the bindings of methods to override or <code>null</code> to implement all unimplemented, abstract methods from super types.
	 * @param insertPos a hint for a location in the source where to insert the new methods or <code>-1</code> to use the default behavior.
	 * @param createComments if set, comments will be added to the new methods.
	 * @return returns a runnable that creates the methods stubs.
	 * @throws IllegalArgumentException a {@link IllegalArgumentException} is thrown if the AST passed has not been created from a {@link ICompilationUnit}.
	 *
	 * @since 3.2
	 */
	public static IWorkspaceRunnable createRunnable(CompilationUnit astRoot, ITypeBinding type, IMethodBinding[] methodToOverride, int insertPos, boolean createComments) {
		AddUnimplementedMethodsOperation operation= new AddUnimplementedMethodsOperation(astRoot, type, methodToOverride, insertPos, true, true, false);
		operation.setCreateComments(createComments);
		return operation;
	}

	/*
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#selectionChanged(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(canEnable(selection));
		} catch (JavaModelException exception) {
			if (JavaModelUtil.isExceptionToBeLogged(exception))
				JavaPlugin.log(exception);
			setEnabled(false);
		}
	}

	/*
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#run(org.eclipse.jface.text.ITextSelection)
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		// Do nothing
	}
}

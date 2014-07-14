/*******************************************************************************
 * Copyright (c) 2008, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation 
 *     Mateusz Matela <mateusz.matela@gmail.com> - [code manipulation] [dcr] toString() builder wizard - https://bugs.eclipse.org/bugs/show_bug.cgi?id=26070
 *     Mateusz Matela <mateusz.matela@gmail.com> - [toString] toString() generator: Fields in declaration order - https://bugs.eclipse.org/bugs/show_bug.cgi?id=279924
 *******************************************************************************/
package org.eclipse.jdt.ui.actions;


import java.lang.reflect.InvocationTargetException;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.jobs.ISchedulingRule;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;

import org.eclipse.jface.text.IRewriteTarget;
import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.eclipse.ltk.ui.refactoring.RefactoringUI;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.NodeFinder;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.dialogs.SourceActionDialog;
import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.ui.util.ElementValidator;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

/**
 * An abstract class containing elements common for <code>GenerateHashCodeEqualsAction</code> and 
 * <code>GenerateToStringAction</code>
 * 
 * since 3.5
 */
abstract class GenerateMethodAbstractAction extends SelectionDispatchAction {

	
	CompilationUnitEditor fEditor;
	CompilationUnit fUnit;
	ITypeBinding fTypeBinding;

	protected GenerateMethodAbstractAction(IWorkbenchSite site) {
		super(site);
	}

	static RefactoringStatusContext createRefactoringStatusContext(IJavaElement element) {
		if (element instanceof IMember) {
			return JavaStatusContext.create((IMember) element);
		}
		if (element instanceof ISourceReference) {
			IOpenable openable= element.getOpenable();
			try {
				if (openable instanceof ICompilationUnit) {
					return JavaStatusContext.create((ICompilationUnit) openable, ((ISourceReference) element).getSourceRange());
				} else if (openable instanceof IClassFile) {
					return JavaStatusContext.create((IClassFile) openable, ((ISourceReference) element).getSourceRange());
				}
			} catch (JavaModelException e) {
				// ignore
			}
		}
		return null;
	}

	/**
	 * Can this action be enabled on the specified selection?
	 * 
	 * @param selection the selection to test
	 * @return <code>true</code> if it can be enabled, <code>false</code>
	 *         otherwise
	 * @throws JavaModelException if the kind of the selection cannot be
	 *             determined
	 */
	boolean canEnable(final IStructuredSelection selection) throws JavaModelException {
		if (selection.size() == 1) {
			final Object element= selection.getFirstElement();
			if (element instanceof IType) {
				final IType type= (IType) element;
				return type.getCompilationUnit() != null && type.isClass();
			}
			if (element instanceof ICompilationUnit)
				return true;
		}
		return false;
	}

	/**
	 * Returns the single selected type from the specified selection.
	 * 
	 * @param selection the selection
	 * @return a single selected type, or <code>null</code>
	 * @throws JavaModelException if the kind of the selection cannot be
	 *             determined
	 */
	IType getSelectedType(final IStructuredSelection selection) throws JavaModelException {
		if (selection.size() == 1 && selection.getFirstElement() instanceof IType) {
			final IType type= (IType) selection.getFirstElement();
			if (type.getCompilationUnit() != null && type.isClass())
				return type;
		} else if (selection.getFirstElement() instanceof ICompilationUnit) {
			final ICompilationUnit unit= (ICompilationUnit) selection.getFirstElement();
			final IType type= unit.findPrimaryType();
			if (type != null && type.isClass())
				return type;
		}
		return null;
	}

	@Override
	public void run(IStructuredSelection selection) {
		try {
			checkAndRun(getSelectedType(selection));
		} catch (CoreException exception) {
			ExceptionHandler.handle(exception, getShell(), getErrorCaption(), ActionMessages.GenerateMethodAbstractAction_error_cannot_create);
		}
	}


	@Override
	public void run(ITextSelection selection) {
		try {
			checkAndRun(SelectionConverter.getTypeAtOffset(fEditor));
		} catch (CoreException e) {
			ExceptionHandler.handle(e, getShell(), getErrorCaption(), ActionMessages.GenerateMethodAbstractAction_error_cannot_create);
		}
	}

	void checkAndRun(IType type) throws CoreException {
		if (type == null) {
			MessageDialog.openInformation(getShell(), getErrorCaption(),
					ActionMessages.GenerateMethodAbstractAction_error_not_applicable);
			notifyResult(false);
		}
		if (!ElementValidator.check(type, getShell(), getErrorCaption(), false)
				|| ! ActionUtil.isEditable(fEditor, getShell(), type)) {
			notifyResult(false);
			return;
		}
		if (type == null) {
			MessageDialog.openError(getShell(), getErrorCaption(), ActionMessages.GenerateMethodAbstractAction_error_removed_type);
			notifyResult(false);
			return;
		}
		if (type.isAnnotation()) {
			MessageDialog.openInformation(getShell(), getErrorCaption(), ActionMessages.GenerateMethodAbstractAction_annotation_not_applicable);
			notifyResult(false);
			return;
		}
		if (type.isInterface()) {
			MessageDialog.openInformation(getShell(), getErrorCaption(), ActionMessages.GenerateMethodAbstractAction_interface_not_applicable);
			notifyResult(false);
			return;
		}
		if (type.isEnum()) {
			MessageDialog.openInformation(getShell(), getErrorCaption(), ActionMessages.GenerateMethodAbstractAction_enum_not_applicable);
			notifyResult(false);
			return;
		}
		if (type.isAnonymous()) {
			MessageDialog.openError(getShell(), getErrorCaption(), ActionMessages.GenerateMethodAbstractAction_anonymous_type_not_applicable);
			notifyResult(false);
			return;
		}
		run(getShell(), type);
	}

	/**
	 * Runs the action.
	 * 
	 * @param shell the shell to use
	 * @param type the type to generate stubs for
	 * @throws CoreException if an error occurs
	 */
	void run(Shell shell, IType type) throws CoreException {
	
		initialize(type);
	
		boolean regenerate= false;
		if (isMethodAlreadyImplemented(fTypeBinding)) {
  			regenerate= MessageDialog.openQuestion(getShell(), getErrorCaption(), Messages.format(ActionMessages.GenerateMethodAbstractAction_already_has_this_method_error, new String[] {
					BasicElementLabels.getJavaElementName(fTypeBinding.getQualifiedName()), getAlreadyImplementedErrorMethodName() }));
			if (!regenerate) {
				notifyResult(false);
				return;
			}
		}
	
		if (!generateCandidates()) {
			MessageDialog.openInformation(getShell(), getErrorCaption(),
					getNoMembersError());
			notifyResult(false);
			return;
		}
		
		final SourceActionDialog dialog= createDialog(shell, type);
		final int dialogResult= dialog.open();
		
		if (dialogResult == Window.OK) {
	
			final Object[] selected= dialog.getResult();
			if (selected == null) {
				notifyResult(false);
				return;
			}
			final CodeGenerationSettings settings= createSettings(type, dialog);
			final IWorkspaceRunnable operation= createOperation(selected, settings, regenerate, type, dialog.getElementPosition());
	
			ITypeBinding superclass= fTypeBinding.getSuperclass();
			RefactoringStatus status= new RefactoringStatus();
	
			status.merge(checkGeneralConditions(type, settings, selected));
			
			if (!"java.lang.Object".equals(superclass.getQualifiedName())) { //$NON-NLS-1$
				status.merge(checkSuperClass(superclass));
			}
			
			for (int i= 0; i < selected.length; i++) {
				status.merge(checkMember(selected[i]));
			}
	
			if (status.hasEntries()) {
				Dialog d= RefactoringUI.createLightWeightStatusDialog(status, getShell(), getErrorCaption());
				if (d.open() != Window.OK) {
					notifyResult(false);
					return;
				}
			}

			final IEditorPart editor= JavaUI.openInEditor(type.getCompilationUnit());
			final IRewriteTarget target= editor != null ? (IRewriteTarget) editor.getAdapter(IRewriteTarget.class) : null;
	
			if (target != null)
				target.beginCompoundChange();
			try {
				IRunnableContext context= JavaPlugin.getActiveWorkbenchWindow();
				if (context == null)
					context= new BusyIndicatorRunnableContext();
				ISchedulingRule schedulingRule= ResourcesPlugin.getWorkspace().getRoot();
				PlatformUI.getWorkbench().getProgressService().runInUI(context, new WorkbenchRunnableAdapter(operation, schedulingRule), schedulingRule);
			} catch (InvocationTargetException exception) {
				ExceptionHandler.handle(exception, shell, getErrorCaption(), null);
			} catch (InterruptedException exception) {
				// Do nothing. Operation has been canceled by user.
			} finally {
				if (target != null)
					target.endCompoundChange();
			}
		}
		notifyResult(dialogResult == Window.OK);
	}


	/**
	 * 
	 * @param type the type for which a method is created
	 * @param dialog the dialog box where the user has defined his preferences
	 * @return settings applicable for this action
	 */
	CodeGenerationSettings createSettings(IType type, SourceActionDialog dialog) {
		CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings(type.getJavaProject());
		settings.createComments= dialog.getGenerateComment();
		return settings;
	}
	
	/**
	 * @return Message to be shown when a method cannot be generated due to lack of appropriate members
	 */
	abstract String getNoMembersError();

	/**
	 * @return Caption for a dialog with error message
	 */
	abstract String getErrorCaption();
	
	abstract IWorkspaceRunnable createOperation(Object[] selectedBindings, CodeGenerationSettings settings, boolean regenerate, IJavaElement type, IJavaElement elementPosition);

	/**
	 * Checks whether general requirements are fulfilled
	 * 
	 * @param type the type for which a method is created
	 * @param settings preferences define by the user
	 * @param selected the type's members selected to be used in generated method
	 * @return RefactoringStatus containing information about eventual problems
	 */
	abstract RefactoringStatus checkGeneralConditions(IType type, CodeGenerationSettings settings, Object[] selected);

	/**
	 * Checks whether a member fulfills requirements expected by method generator
	 * 
	 * @param object member binding to be checked
	 * @return RefactoringStatus containing information about eventual problems
	 */
	abstract RefactoringStatus checkMember(Object object);
	
	/**
	 * Checks whether the superclass fulfills requirements expected by method generator
	 * @param superclass superclass type binding to be checked
	 * @return RefactoringStatus containing information about eventual problems
	 */
	abstract RefactoringStatus checkSuperClass(ITypeBinding superclass);

	/**
	 * Creates the action's main dialog.
	 * 
	 * @param shell the shell to use
	 * @param type the type to generate stubs for
	 * @return a dialog with generator-specific options
	 * @throws JavaModelException if creation of the dialog fails
	 */
	abstract SourceActionDialog createDialog(Shell shell, IType type) throws JavaModelException;

	/**
	 * Chooses type members that can be used in generated method.
	 * Returns false, if there are no such members and the method cannot
	 * be generated
	 * @return true, if the method can be generated (i.e. there are appropriate member fields)
	 * @throws JavaModelException if an error in java model occurs
	 */
	abstract boolean generateCandidates() throws JavaModelException;

	/**
	 * 
	 * @return The message displayed when the method is already implemented in the type.
	 */
	abstract String getAlreadyImplementedErrorMethodName();

	/**
	 * 
	 * @param typeBinding Type to be checked
	 * @return true if given type already contains the method to be generated 
	 */
	abstract boolean isMethodAlreadyImplemented(ITypeBinding typeBinding);

	boolean useBlocks(IJavaProject project) {
		if (CleanUpOptions.TRUE.equals(PreferenceConstants.getPreference(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS, project))) {
			return CleanUpOptions.TRUE.equals(PreferenceConstants.getPreference(CleanUpConstants.CONTROL_STATMENTS_USE_BLOCKS_ALWAYS, project))
					|| CleanUpOptions.TRUE.equals(PreferenceConstants.getPreference(CleanUpConstants.CONTROL_STATMENTS_USE_BLOCKS_NO_FOR_RETURN_AND_THROW, project));
		}
		return false;
	}

	void initialize(IType type) throws JavaModelException {
		RefactoringASTParser parser= new RefactoringASTParser(ASTProvider.SHARED_AST_LEVEL);
		fUnit= parser.parse(type.getCompilationUnit(), true);
		fTypeBinding= null;
		// type cannot be anonymous
		final AbstractTypeDeclaration declaration= (AbstractTypeDeclaration) ASTNodes.getParent(NodeFinder.perform(fUnit, type.getNameRange()),
				AbstractTypeDeclaration.class);
		if (declaration != null)
			fTypeBinding= declaration.resolveBinding();
	}

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

	@Override
	public void selectionChanged(ITextSelection selection) {
		// Do nothing
	}

}

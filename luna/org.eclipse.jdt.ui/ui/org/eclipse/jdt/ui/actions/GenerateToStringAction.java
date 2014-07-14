/*******************************************************************************
 * Copyright (c) 2008, 2011 Mateusz Matela and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Mateusz Matela <mateusz.matela@gmail.com> - [code manipulation] [dcr] toString() builder wizard - https://bugs.eclipse.org/bugs/show_bug.cgi?id=26070
 *     Mateusz Matela <mateusz.matela@gmail.com> - [toString] toString() generator: Fields in declaration order - https://bugs.eclipse.org/bugs/show_bug.cgi?id=279924
 *******************************************************************************/
package org.eclipse.jdt.ui.actions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.resources.IWorkspaceRunnable;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.GenerateToStringOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.ToStringGenerationSettings;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.dialogs.GenerateToStringDialog;
import org.eclipse.jdt.internal.ui.dialogs.SourceActionDialog;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

/**
 * Adds method implementations for <code>{@link java.lang.Object#toString()}</code> The action opens a
 * dialog from which the user can choose the fields and methods to be considered.
 * <p>
 * Will open the parent compilation unit in a Java editor. The result is
 * unsaved, so the user can decide if the changes are acceptable.
 * <p>
 * The action is applicable to structured selections containing elements of type
 * {@link org.eclipse.jdt.core.IType}.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 3.5
 * 
 * @noextend This class is not intended to be subclassed by clients.
 */
public class GenerateToStringAction extends GenerateMethodAbstractAction {
	
	private static final String METHODNAME_TO_STRING= "toString"; //$NON-NLS-1$
	
	private List<IVariableBinding> fFields;

	private List<IVariableBinding> fInheritedFields;

	private List<IVariableBinding> fSelectedFields;

	private List<IMethodBinding> fMethods;

	private List<IMethodBinding> fInheritedMethods;

	private GenerateToStringOperation operation;

	private class ToStringInfo {

		public boolean foundToString= false;

		public boolean foundFinalToString= false;

		public ToStringInfo(ITypeBinding typeBinding) {
			IMethodBinding[] declaredMethods= typeBinding.getDeclaredMethods();

			for (int i= 0; i < declaredMethods.length; i++) {
				if (declaredMethods[i].getName().equals(METHODNAME_TO_STRING) && declaredMethods[i].getParameterTypes().length == 0) {
					this.foundToString= true;
					if (Modifier.isFinal(declaredMethods[i].getModifiers()))
						this.foundFinalToString= true;
				}
			}
		}
	}

	/**
	 * Creates a new generate tostring action.
	 * <p>
	 * The action requires that the selection provided by the site's selection
	 * provider is of type
	 * {@link org.eclipse.jface.viewers.IStructuredSelection}.
	 * 
	 * @param site the workbench site providing context information for this
	 *            action
	 */
	public GenerateToStringAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.GenerateToStringAction_label);
		setDescription(ActionMessages.GenerateToStringAction_description);
		setToolTipText(ActionMessages.GenerateToStringAction_tooltip);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.GENERATE_TOSTRING_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call
	 * this constructor.
	 * 
	 * @param editor the compilation unit editor
	 * 
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public GenerateToStringAction(CompilationUnitEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled((fEditor != null && SelectionConverter.canOperateOn(fEditor)));
	}

	@Override
	RefactoringStatus checkMember(Object object) {
		// no conditions need to be checked 
		return new RefactoringStatus();
	}

	@Override
	RefactoringStatus checkGeneralConditions(IType type, CodeGenerationSettings settings, Object[] selected) {
		return operation.checkConditions();
	}

	 @Override
	RefactoringStatus checkSuperClass(ITypeBinding superclass) {
		RefactoringStatus status= new RefactoringStatus();
		if (new ToStringInfo(superclass).foundFinalToString) {
			status.addError(Messages.format(ActionMessages.GenerateMethodAbstractAction_final_method_in_superclass_error, new String[] {
					Messages.format(ActionMessages.GenerateMethodAbstractAction_super_class, BasicElementLabels.getJavaElementName(superclass.getQualifiedName())),
					ActionMessages.GenerateToStringAction_tostring }), createRefactoringStatusContext(superclass.getJavaElement()));
		}
		return status;
	}

	@Override
	SourceActionDialog createDialog(Shell shell, IType type) throws JavaModelException {
		IVariableBinding[] fieldBindings= fFields.toArray(new IVariableBinding[0]);
		IVariableBinding[] inheritedFieldBindings= fInheritedFields.toArray(new IVariableBinding[0]);
		IVariableBinding[] selectedFieldBindings= fSelectedFields.toArray(new IVariableBinding[0]);
		IMethodBinding[] methodBindings= fMethods.toArray(new IMethodBinding[0]);
		IMethodBinding[] inheritededMethodBindings= fInheritedMethods.toArray(new IMethodBinding[0]);
		return new GenerateToStringDialog(shell, fEditor, type, fieldBindings, inheritedFieldBindings, selectedFieldBindings, methodBindings, inheritededMethodBindings);
	}

	@Override
	IWorkspaceRunnable createOperation(Object[] selectedBindings, CodeGenerationSettings settings, boolean regenerate, IJavaElement type, IJavaElement elementPosition) {
		return operation= GenerateToStringOperation.createOperation(fTypeBinding, selectedBindings, fUnit, elementPosition, (ToStringGenerationSettings)settings);
	}
	
	@Override
	CodeGenerationSettings createSettings(IType type, SourceActionDialog dialog) {
		ToStringGenerationSettings settings= ((GenerateToStringDialog) dialog).getGenerationSettings();
		super.createSettings(type, dialog).setSettings(settings);
		settings.createComments= dialog.getGenerateComment();
		settings.useBlocks= useBlocks(type.getJavaProject());
		String version= fUnit.getJavaElement().getJavaProject().getOption(JavaCore.COMPILER_SOURCE, true);
		settings.is50orHigher= !JavaModelUtil.isVersionLessThan(version, JavaCore.VERSION_1_5);
		settings.is60orHigher= !JavaModelUtil.isVersionLessThan(version, JavaCore.VERSION_1_6);
		return settings;
	}

	@Override
	boolean generateCandidates() throws JavaModelException {
		IVariableBinding[] candidateFields= fTypeBinding.getDeclaredFields();
		HashMap<IJavaElement, IVariableBinding> fieldsToBindings= new HashMap<IJavaElement, IVariableBinding>();
		HashMap<IJavaElement, IVariableBinding> selectedFieldsToBindings= new HashMap<IJavaElement, IVariableBinding>();
		for (int i= 0; i < candidateFields.length; i++) {
			if (!Modifier.isStatic(candidateFields[i].getModifiers())) {
				fieldsToBindings.put(candidateFields[i].getJavaElement(), candidateFields[i]);
				if (!Modifier.isTransient(candidateFields[i].getModifiers()))
					selectedFieldsToBindings.put(candidateFields[i].getJavaElement(), candidateFields[i]);
			}
		}
		IType type= (IType)fTypeBinding.getJavaElement();
		IField[] allFields= type.getFields();
		fFields= new ArrayList<IVariableBinding>();
		populateMembers(fFields, allFields, fieldsToBindings);
		fSelectedFields= new ArrayList<IVariableBinding>();
		populateMembers(fSelectedFields, allFields, selectedFieldsToBindings);

		IMethodBinding[] candidateMethods= fTypeBinding.getDeclaredMethods();
		HashMap<IJavaElement, IMethodBinding> methodsToBindings= new HashMap<IJavaElement, IMethodBinding>();
		for (int i= 0; i < candidateMethods.length; i++) {
			if (!Modifier.isStatic(candidateMethods[i].getModifiers()) && candidateMethods[i].getParameterTypes().length == 0
					&& !candidateMethods[i].getReturnType().getName().equals("void") && !candidateMethods[i].getName().equals("toString") && !candidateMethods[i].getName().equals("clone")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				methodsToBindings.put(candidateMethods[i].getJavaElement(), candidateMethods[i]);
			}
		}
		fMethods= new ArrayList<IMethodBinding>();
		populateMembers(fMethods, type.getMethods(), methodsToBindings);

		fInheritedFields= new ArrayList<IVariableBinding>();
		fInheritedMethods= new ArrayList<IMethodBinding>();
		ITypeBinding typeBinding= fTypeBinding;
		while ((typeBinding= typeBinding.getSuperclass()) != null) {
			type = (IType)typeBinding.getJavaElement();
			candidateFields= typeBinding.getDeclaredFields();
			for (int i= 0; i < candidateFields.length; i++) {
				if (!Modifier.isPrivate(candidateFields[i].getModifiers()) && !Modifier.isStatic(candidateFields[i].getModifiers()) && !contains(fFields, candidateFields[i])
						&& !contains(fInheritedFields, candidateFields[i])) {
					fieldsToBindings.put(candidateFields[i].getJavaElement(), candidateFields[i]);
				}
			}
			populateMembers(fInheritedFields, type.getFields(), fieldsToBindings);
			
			candidateMethods= typeBinding.getDeclaredMethods();
			for (int i= 0; i < candidateMethods.length; i++) {
				if (!Modifier.isPrivate(candidateMethods[i].getModifiers())
						&& !Modifier.isStatic(candidateMethods[i].getModifiers())
						&& candidateMethods[i].getParameterTypes().length == 0
						&& !candidateMethods[i].getReturnType().getName().equals("void") && !contains(fMethods, candidateMethods[i]) && !contains(fInheritedMethods, candidateMethods[i]) && !candidateMethods[i].getName().equals("clone")) { //$NON-NLS-1$ //$NON-NLS-2$
					methodsToBindings.put(candidateMethods[i].getJavaElement(), candidateMethods[i]);
				}
			}
			populateMembers(fInheritedMethods, type.getMethods(), methodsToBindings);
		}
		
		return true;
	}

	/**
	 * Populates <code>result</code> with the bindings from <code>membersToBindings</code>, sorted
	 * in the order of <code>allMembers</code>.
	 * 
	 * @param result list of bindings from membersToBindings, sorted in source order
	 * @param allMembers all member elements in source order
	 * @param membersToBindings map from {@link IMember} to {@link IBinding}
	 * @since 3.6
	 */
	private static <T extends IBinding> void populateMembers(List<T> result, IMember[] allMembers, HashMap<IJavaElement, T> membersToBindings) {
		for (int i= 0; i < allMembers.length; i++) {
			T memberBinding= membersToBindings.remove(allMembers[i]);
			if (memberBinding != null) {
				result.add(memberBinding);
			}
		}
	}

	private static <T extends IBinding> boolean contains(List<T> inheritedFields, T member) {
		for (Iterator<T> iterator= inheritedFields.iterator(); iterator.hasNext();) {
			T object= iterator.next();
			if (object instanceof IVariableBinding && member instanceof IVariableBinding)
				if (((IVariableBinding) object).getName().equals(((IVariableBinding) member).getName()))
					return true;
			if (object instanceof IMethodBinding && member instanceof IMethodBinding)
				if (((IMethodBinding) object).getName().equals(((IMethodBinding) member).getName()))
					return true;
		}
		return false;
	}

	@Override
	String getAlreadyImplementedErrorMethodName() {
		return ActionMessages.GenerateToStringAction_tostring;
	}

	@Override
	boolean isMethodAlreadyImplemented(ITypeBinding typeBinding) {
		return new ToStringInfo(typeBinding).foundToString;
	}

	@Override
	String getErrorCaption() {
		return ActionMessages.GenerateToStringAction_error_caption;
	}

	@Override
	String getNoMembersError() {
		//no members error never occurs
		return null;
	}

}

/*******************************************************************************
 * Copyright (c) 2005, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Mateusz Matela <mateusz.matela@gmail.com> - [code manipulation] [dcr] toString() builder wizard - https://bugs.eclipse.org/bugs/show_bug.cgi?id=26070
 *******************************************************************************/
package org.eclipse.jdt.ui.actions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.resources.IWorkspaceRunnable;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.GenerateHashCodeEqualsOperation;
import org.eclipse.jdt.internal.corext.dom.TypeRules;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.dialogs.GenerateHashCodeEqualsDialog;
import org.eclipse.jdt.internal.ui.dialogs.SourceActionDialog;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.BindingLabelProvider;

/**
 * Adds method implementations for
 * <code>{@link java.lang.Object#equals(java.lang.Object)}</code> and
 * <code>{@link java.lang.Object#hashCode()}</code>. The action opens a
 * dialog from which the user can choose the fields to be considered.
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
 * @since 3.2
 */
public final class GenerateHashCodeEqualsAction extends GenerateMethodAbstractAction {
	
	private static final String METHODNAME_HASH_CODE= "hashCode"; //$NON-NLS-1$
	private static final String METHODNAME_EQUALS= "equals"; //$NON-NLS-1$

	private class HashCodeEqualsInfo {

		public boolean foundHashCode= false;

		public boolean foundEquals= false;

		public boolean foundFinalHashCode= false;

		public boolean foundFinalEquals= false;
	}

	private class HashCodeEqualsGenerationSettings extends CodeGenerationSettings {
		public boolean useInstanceOf= false;
		public boolean useBlocks= false;
	}

	private List<IVariableBinding> allFields;
	private List<IVariableBinding> selectedFields;

	private ArrayList<ITypeBinding> alreadyCheckedMemberTypes;

	/**
	 * Note: This constructor is for internal use only. Clients should not call
	 * this constructor.
	 *
	 * @param editor the compilation unit editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public GenerateHashCodeEqualsAction(final CompilationUnitEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled( (fEditor != null && SelectionConverter.canOperateOn(fEditor)));
	}

	/**
	 * Creates a new generate hashCode equals action.
	 * <p>
	 * The action requires that the selection provided by the site's selection
	 * provider is of type
	 * {@link org.eclipse.jface.viewers.IStructuredSelection}.
	 *
	 * @param site the workbench site providing context information for this
	 *            action
	 */
	public GenerateHashCodeEqualsAction(final IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.GenerateHashCodeEqualsAction_label);
		setDescription(ActionMessages.GenerateHashCodeEqualsAction_description);
		setToolTipText(ActionMessages.GenerateHashCodeEqualsAction_tooltip);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.GENERATE_HASHCODE_EQUALS_ACTION);
	}

	@Override
	boolean isMethodAlreadyImplemented(ITypeBinding typeBinding) {
		HashCodeEqualsInfo info= getTypeInfo(typeBinding, false);
		return (info.foundEquals || info.foundHashCode);
	}

	private HashCodeEqualsInfo getTypeInfo(ITypeBinding someType, boolean checkSuperclasses) {
		HashCodeEqualsInfo info= new HashCodeEqualsInfo();
		if (someType.isTypeVariable()) {
			someType= someType.getErasure();
		}
		
		while (true) {
			IMethodBinding[] declaredMethods= someType.getDeclaredMethods();
	
			for (int i= 0; i < declaredMethods.length; i++) {
				if (declaredMethods[i].getName().equals(METHODNAME_EQUALS)) {
					ITypeBinding[] b= declaredMethods[i].getParameterTypes();
					if ((b.length == 1) && (b[0].getQualifiedName().equals("java.lang.Object"))) { //$NON-NLS-1$
						info.foundEquals= true;
						if (Modifier.isFinal(declaredMethods[i].getModifiers()))
							info.foundFinalEquals= true;
					}
				}
				if (declaredMethods[i].getName().equals(METHODNAME_HASH_CODE) && declaredMethods[i].getParameterTypes().length == 0) {
					info.foundHashCode= true;
					if (Modifier.isFinal(declaredMethods[i].getModifiers()))
						info.foundFinalHashCode= true;
				}
				if (info.foundEquals && info.foundHashCode)
					break;
			}
			if (checkSuperclasses) {
				someType= someType.getSuperclass();
				if (someType == null || TypeRules.isJavaLangObject(someType)) {
					break;
				}
			} else {
				break;
			}
		}
		
		return info;
	}

	private RefactoringStatus checkHashCodeEqualsExists(ITypeBinding someType, boolean superClass) {

		RefactoringStatus status= new RefactoringStatus();
		HashCodeEqualsInfo info= getTypeInfo(someType, true);

		String concreteTypeWarning= superClass ? ActionMessages.GenerateMethodAbstractAction_super_class : ActionMessages.GenerateHashCodeEqualsAction_field_type;
		String concreteMethWarning= (someType.isInterface() || Modifier.isAbstract(someType.getModifiers()))
				? ActionMessages.GenerateHashCodeEqualsAction_interface_does_not_declare_hashCode_equals_error
				: ActionMessages.GenerateHashCodeEqualsAction_type_does_not_implement_hashCode_equals_error;
		String concreteHCEWarning= null;

		if (!info.foundEquals && (!info.foundHashCode))
			concreteHCEWarning= ActionMessages.GenerateHashCodeEqualsAction_equals_and_hashCode;
		else if (!info.foundEquals)
			concreteHCEWarning= ActionMessages.GenerateHashCodeEqualsAction_equals;
		else if (!info.foundHashCode)
			concreteHCEWarning= ActionMessages.GenerateHashCodeEqualsAction_hashCode;

		if (!info.foundEquals || !info.foundHashCode)
			status.addWarning(Messages.format(concreteMethWarning, new String[] {
					Messages.format(concreteTypeWarning, BindingLabelProvider.getBindingLabel(someType, JavaElementLabels.ALL_FULLY_QUALIFIED)), concreteHCEWarning }),
					createRefactoringStatusContext(someType.getJavaElement()));

		if (superClass && (info.foundFinalEquals || info.foundFinalHashCode)) {
			status.addError(Messages.format(ActionMessages.GenerateMethodAbstractAction_final_method_in_superclass_error, new String[] {
					Messages.format(concreteTypeWarning, BasicElementLabels.getJavaElementName(someType.getQualifiedName())), ActionMessages.GenerateHashCodeEqualsAction_hashcode_or_equals }),
					createRefactoringStatusContext(someType.getJavaElement()));
		}

		return status;
	}

	@Override
	CodeGenerationSettings createSettings(IType type, SourceActionDialog dialog) {
		HashCodeEqualsGenerationSettings settings= new HashCodeEqualsGenerationSettings();
		super.createSettings(type, dialog).setSettings(settings);
		settings.createComments= dialog.getGenerateComment();
		GenerateHashCodeEqualsDialog generateHashCodeEqualsDialog= (GenerateHashCodeEqualsDialog)dialog;
		settings.useInstanceOf= generateHashCodeEqualsDialog.isUseInstanceOf();
		settings.useBlocks= generateHashCodeEqualsDialog.isUseBlocks();
		return settings;
	}

	@Override
	void initialize(IType type) throws JavaModelException {
		super.initialize(type);
		alreadyCheckedMemberTypes= new ArrayList<ITypeBinding>();
	}

	@Override
	String getAlreadyImplementedErrorMethodName() {
		return ActionMessages.GenerateHashCodeEqualsAction_hashcode_or_equals;
	}

	@Override
	boolean generateCandidates() {
		IVariableBinding[] fCandidateFields= fTypeBinding.getDeclaredFields();

		allFields= new ArrayList<IVariableBinding>();
		selectedFields= new ArrayList<IVariableBinding>();
		for (int i= 0; i < fCandidateFields.length; i++) {
			if (!Modifier.isStatic(fCandidateFields[i].getModifiers())) {
				allFields.add(fCandidateFields[i]);
				if (!Modifier.isTransient(fCandidateFields[i].getModifiers()))
					selectedFields.add(fCandidateFields[i]);
			}
		}
		if (allFields.isEmpty()) {
			return false;
		}
		return true;
	}

	@Override
	SourceActionDialog createDialog(Shell shell, IType type) throws JavaModelException {
		IVariableBinding[] allFieldBindings= allFields.toArray(new IVariableBinding[0]);
		IVariableBinding[] selectedFieldBindings= selectedFields.toArray(new IVariableBinding[0]);
		return new GenerateHashCodeEqualsDialog(shell, fEditor, type, allFieldBindings, selectedFieldBindings);
	}

	@Override
	RefactoringStatus checkSuperClass(ITypeBinding superclass) {
		return checkHashCodeEqualsExists(superclass, true);
	}

	@Override
	RefactoringStatus checkGeneralConditions(IType type, CodeGenerationSettings settings, Object[] selected) {
		return new RefactoringStatus();
	}

	@Override
	RefactoringStatus checkMember(Object memberBinding) {
		RefactoringStatus status= new RefactoringStatus();
		IVariableBinding variableBinding= (IVariableBinding)memberBinding;
		ITypeBinding fieldsType= variableBinding.getType();
		if (fieldsType.isArray())
			fieldsType= fieldsType.getElementType();
		if (!fieldsType.isPrimitive() && !fieldsType.isEnum() && !alreadyCheckedMemberTypes.contains(fieldsType) && !fieldsType.equals(fTypeBinding)) {
			status.merge(checkHashCodeEqualsExists(fieldsType, false));
			alreadyCheckedMemberTypes.add(fieldsType);
		}
		if (Modifier.isTransient(variableBinding.getModifiers()))
			status.addWarning(Messages.format(ActionMessages.GenerateHashCodeEqualsAction_transient_field_included_error, BasicElementLabels.getJavaElementName(variableBinding.getName())),
					createRefactoringStatusContext(variableBinding.getJavaElement()));
		return status;
	}

	@Override
	IWorkspaceRunnable createOperation(Object[] selectedBindings, CodeGenerationSettings settings, boolean regenerate, IJavaElement type, IJavaElement elementPosition) {
		final IVariableBinding[] selectedVariableBindings= Arrays.asList(selectedBindings).toArray(new IVariableBinding[0]);
		HashCodeEqualsGenerationSettings hashCodeEqualsGenerationSettings= (HashCodeEqualsGenerationSettings)settings;
		GenerateHashCodeEqualsOperation operation= new GenerateHashCodeEqualsOperation(fTypeBinding, selectedVariableBindings, fUnit, elementPosition, settings,
				hashCodeEqualsGenerationSettings.useInstanceOf, regenerate, true, false);
		operation.setUseBlocksForThen(hashCodeEqualsGenerationSettings.useBlocks);
		return operation;
	}

	@Override
	String getErrorCaption() {
		return ActionMessages.GenerateHashCodeEqualsAction_error_caption;
	}

	@Override
	String getNoMembersError() {
		return ActionMessages.GenerateHashCodeEqualsAction_no_nonstatic_fields_error;
	}

}

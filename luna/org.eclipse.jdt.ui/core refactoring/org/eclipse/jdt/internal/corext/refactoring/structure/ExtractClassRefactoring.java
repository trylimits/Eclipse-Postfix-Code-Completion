/*******************************************************************************
 * Copyright (c) 2007, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Nikolay Metchev <nikolaymetchev@gmail.com> - [extract class] Extract class refactoring on a field in an inner non-static class yields compilation error - https://bugs.eclipse.org/394547
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.resource.ResourceChange;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.refactoring.descriptors.ExtractClassDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.ExtractClassDescriptor.Field;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.corext.codemanipulation.GetterSetterUtil;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.TypeBindingVisitor;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.structure.ParameterObjectFactory.CreationListener;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.correction.ASTResolving;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

public class ExtractClassRefactoring extends Refactoring {

	public static class ExtractClassDescriptorVerification {
		private ExtractClassDescriptor fDescriptor;

		public ExtractClassDescriptorVerification(ExtractClassDescriptor descriptor) {
			fDescriptor= descriptor;
		}

		public RefactoringStatus validateClassName() {
			RefactoringStatus status= new RefactoringStatus();
			status.merge(Checks.checkTypeName(fDescriptor.getClassName(), fDescriptor.getType()));
			status.merge(checkClass());
			return status;
		}

		private RefactoringStatus checkClass() {
			RefactoringStatus status= new RefactoringStatus();
			IType type= fDescriptor.getType();
			if (!fDescriptor.isCreateTopLevel()) {
				if (type.getType(fDescriptor.getClassName()).exists()) {
					status.addError(Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_errror_nested_name_clash, new Object[] { BasicElementLabels.getJavaElementName(fDescriptor.getClassName()), BasicElementLabels.getJavaElementName(type.getElementName()) }));
				}
			} else {
				status.merge(checkPackageClass());
			}
			return status;
		}

		private RefactoringStatus checkPackageClass() {
			RefactoringStatus status= new RefactoringStatus();
			IType type= fDescriptor.getType();
			IPackageFragmentRoot ancestor= (IPackageFragmentRoot) type.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			IPackageFragment packageFragment= ancestor.getPackageFragment(fDescriptor.getPackage());
			if (packageFragment.getCompilationUnit(fDescriptor.getClassName() + JavaModelUtil.DEFAULT_CU_SUFFIX).exists())
				status.addError(Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_error_toplevel_name_clash, new Object[] { BasicElementLabels.getJavaElementName(fDescriptor.getClassName()), BasicElementLabels.getJavaElementName(fDescriptor.getPackage()) }));
			return status;
		}

		public RefactoringStatus validateTopLevel() {
			return checkClass();
		}

		public RefactoringStatus validateParameterName() {
			RefactoringStatus status= new RefactoringStatus();
			String parameterName= fDescriptor.getFieldName();
			IType type= fDescriptor.getType();
			status.merge(Checks.checkFieldName(parameterName, type));
			validateFieldNames(status, parameterName, type);
			return status;
		}

		private void validateFieldNames(RefactoringStatus status, String parameterName, IType type) {
			if (type.getField(parameterName).exists()) {
				Field[] fields= fDescriptor.getFields();
				for (int i= 0; i < fields.length; i++) {
					Field field= fields[i];
					if (parameterName.equals(field.getFieldName())){
						if (!field.isCreateField())
							status.addError(Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_error_field_already_exists, BasicElementLabels.getJavaElementName(parameterName)));
					}
				}
			}
		}

		public RefactoringStatus validateFields() {
			RefactoringStatus status= new RefactoringStatus();
			Field[] fields= fDescriptor.getFields();
			Set<String> names= new HashSet<String>();
			for (int i= 0; i < fields.length; i++) {
				Field field= fields[i];
				if (field.isCreateField()) {
					if (names.contains(field.getNewFieldName())) {
						status.addError(Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_error_duplicate_field_name, BasicElementLabels.getJavaElementName(field.getNewFieldName())));
					}
					names.add(field.getNewFieldName());
					status.merge(Checks.checkFieldName(field.getNewFieldName(), fDescriptor.getType()));
				}
			}
			if (names.size() == 0) {
				status.addError(RefactoringCoreMessages.ExtractClassRefactoring_error_msg_one_field);
			}
			validateFieldNames(status, fDescriptor.getFieldName(), fDescriptor.getType());
			return status;
		}

		public RefactoringStatus validateAll() {
			RefactoringStatus status= new RefactoringStatus();
			status.merge(validateClassName()); //also validates toplevel
			status.merge(validateFields());
			status.merge(validateParameterName());
			return status;
		}
	}


	private final class FieldReferenceFinder extends ASTVisitor {
		public boolean fFieldRefFound= false;

		private FieldReferenceFinder() {
		}

		@Override
		public boolean visit(FieldAccess node) {
			IVariableBinding fieldBinding= node.resolveFieldBinding();
			return checkVariableBinding(fieldBinding);
		}

		@Override
		public boolean visit(SimpleName node) {
			IVariableBinding variableBinding= ASTNodes.getVariableBinding(node);
			return checkVariableBinding(variableBinding);
		}

		private boolean checkVariableBinding(IVariableBinding fieldBinding) {
			if (fieldBinding != null) {
				if (fieldBinding.isField()) {
					ITypeBinding declaringClass= fieldBinding.getDeclaringClass();
					if ((declaringClass != null) && declaringClass.getQualifiedName().equals(fDescriptor.getType().getFullyQualifiedName())) {
						FieldInfo fi= fVariables.get(fieldBinding.getName());
						if (fi != null && isCreateField(fi) && Bindings.equals(fieldBinding, fi.pi.getOldBinding())) {
							fFieldRefFound= true;
							return false;
						}
					}
				}
			}
			return true;
		}
	}

	private final class FieldInfo {
		ParameterInfo pi;
		VariableDeclarationFragment declaration;
		IField ifield;
		String name;
		Expression initializer;
		private Boolean hasFieldReferences= null;

		public boolean hasFieldReference() {
			if (hasFieldReferences == null) {
				if (initializer != null) {
					FieldReferenceFinder frf= new FieldReferenceFinder();
					initializer.accept(frf);
					hasFieldReferences= Boolean.valueOf(frf.fFieldRefFound);
				} else {
					hasFieldReferences= Boolean.FALSE;
				}
			}
			return hasFieldReferences.booleanValue();
		}

		private FieldInfo(ParameterInfo parameterInfo, IField ifield) {
			super();
			this.pi= parameterInfo;
			this.ifield= ifield;
			this.name= ifield.getElementName();
		}
	}

	private ExtractClassDescriptor fDescriptor;
	private Map<String, FieldInfo> fVariables;
	private CompilationUnitRewrite fBaseCURewrite;
	private TextChangeManager fChangeManager;
	private ParameterObjectFactory fParameterObjectFactory;
	private ExtractClassDescriptorVerification fVerification;

	public ExtractClassRefactoring(ExtractClassDescriptor descriptor) {
		fDescriptor= descriptor;
		IType type= fDescriptor.getType();
		if (fDescriptor.getPackage() == null) {
			fDescriptor.setPackage(type.getPackageFragment().getElementName());
		}
		if (fDescriptor.getClassName() == null) {
			fDescriptor.setClassName(type.getElementName() + "Data"); //$NON-NLS-1$
		}
		if (fDescriptor.getFieldName() == null) {
			fDescriptor.setFieldName(StubUtility.getVariableNameSuggestions(NamingConventions.VK_INSTANCE_FIELD, type.getJavaProject(), "data", 0, null, true)[0]); //$NON-NLS-1$
		}
		if (fDescriptor.getFields() == null) {
			try {
				fDescriptor.setFields(ExtractClassDescriptor.getFields(type));
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
			}
		}
		fVerification= new ExtractClassDescriptorVerification(descriptor);
	}


	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		RefactoringStatus result= new RefactoringStatus();
		pm.beginTask(RefactoringCoreMessages.ExtractClassRefactoring_progress_msg_check_initial_condition, 5);
		try {
			result.merge(fDescriptor.validateDescriptor());
			if (!result.isOK())
				return result;
			IType type= fDescriptor.getType();
			result.merge(Checks.checkAvailability(type));
			if (!result.isOK())
				return result;
			pm.worked(1);
			Field[] fields= ExtractClassDescriptor.getFields(fDescriptor.getType());
			pm.worked(1);
			if (pm.isCanceled())
				throw new OperationCanceledException();
			fVariables= new LinkedHashMap<String, FieldInfo>();
			if (fields.length == 0) {
				result.addFatalError(RefactoringCoreMessages.ExtractClassRefactoring_error_no_usable_fields, JavaStatusContext.create(type));
				return result;
			}
			for (int i= 0; i < fields.length; i++) {
				Field field= fields[i];
				String fieldName= field.getFieldName();
				IField declField= type.getField(fieldName);
				ParameterInfo info= new ParameterInfo(Signature.toString(declField.getTypeSignature()), fieldName, i);
				fVariables.put(fieldName, new FieldInfo(info, declField));
				if (pm.isCanceled())
					throw new OperationCanceledException();
			}
			pm.worked(3);
		} finally {
			pm.done();
		}
		return result;
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		RefactoringStatus result= new RefactoringStatus();
		result.merge(fVerification.validateAll());
		try {
			pm.beginTask(RefactoringCoreMessages.ExtractClassRefactoring_progress_final_conditions, 95);
			for (Iterator<FieldInfo> iter= fVariables.values().iterator(); iter.hasNext();) {
				FieldInfo fi= iter.next();
				boolean createField= isCreateField(fi);
				if (createField) {
					IField field= fi.ifield;
					int flags= field.getFlags();
					if (Flags.isStatic(flags)) {
						result.addFatalError(Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_error_field_is_static, BasicElementLabels.getJavaElementName(field.getElementName())), JavaStatusContext.create(field));
						return result;
					}
					if (Flags.isTransient(flags)) {
						result.addWarning(Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_warning_field_is_transient, BasicElementLabels.getJavaElementName(field.getElementName())), JavaStatusContext.create(field));
					}
					if (Flags.isVolatile(flags)) {
						result.addWarning(Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_warning_field_is_volatile, BasicElementLabels.getJavaElementName(field.getElementName())), JavaStatusContext.create(field));
					}
				}
			}
			pm.worked(5);
			fChangeManager= new TextChangeManager();
			fParameterObjectFactory= initializeFactory();
			IType type= fDescriptor.getType();
			pm.worked(5);

			FieldDeclaration field= performFieldRewrite(type, fParameterObjectFactory, result);
			int flags= RefactoringDescriptor.STRUCTURAL_CHANGE | JavaRefactoringDescriptor.JAR_MIGRATION | JavaRefactoringDescriptor.JAR_REFACTORING | JavaRefactoringDescriptor.JAR_SOURCE_ATTACHMENT;
			if (!Modifier.isPrivate(field.getModifiers()))
				flags|= RefactoringDescriptor.MULTI_CHANGE;
			fDescriptor.setFlags(flags);

			result.merge(updateReferences(type, fParameterObjectFactory, new SubProgressMonitor(pm, 65)));

		} finally {
			pm.done();
		}
		return result;
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		pm.beginTask(RefactoringCoreMessages.ExtractClassRefactoring_progress_create_change, 10);
		try {
			ICompilationUnit typeCU= fDescriptor.getType().getCompilationUnit();
			IPackageFragmentRoot packageRoot= (IPackageFragmentRoot) typeCU.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			ArrayList<Change> changes= new ArrayList<Change>();

			changes.addAll(createParameterObject(fParameterObjectFactory, packageRoot));
			fChangeManager.manage(typeCU, fBaseCURewrite.createChange(true, pm));
			changes.addAll(Arrays.asList(fChangeManager.getAllChanges()));
			String project= fDescriptor.getType().getJavaProject().getElementName();
			fDescriptor.setProject(project);
			fDescriptor.setDescription(getName());
			fDescriptor.setComment(createComment());
			DynamicValidationRefactoringChange change= new DynamicValidationRefactoringChange(fDescriptor, RefactoringCoreMessages.ExtractClassRefactoring_change_name, changes
					.toArray(new Change[changes.size()]));
			return change;
		} finally {
			pm.done();
		}
	}

	private String createComment() {
		Object[] keys= new Object[] { BasicElementLabels.getJavaElementName(fDescriptor.getClassName()), BasicElementLabels.getJavaElementName(fDescriptor.getType().getElementName()) };
		String header= Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_change_comment_header, keys);
		JDTRefactoringDescriptorComment comment= new JDTRefactoringDescriptorComment(fDescriptor.getType().getJavaProject().getElementName(), this, header);
		comment.addSetting(Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_comment_extracted_class, BasicElementLabels.getJavaElementName(fDescriptor.getClassName())));

		if (fDescriptor.isCreateTopLevel())
			comment.addSetting(Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_comment_package, BasicElementLabels.getJavaElementName(fDescriptor.getPackage())));

		Field[] fields= fDescriptor.getFields();
		ArrayList<String> strings= new ArrayList<String>();
		for (int i= 0; i < fields.length; i++) {
			Field field= fields[i];
			if (field.isCreateField()) {
				strings.add(Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_comment_field_renamed, new Object[] { BasicElementLabels.getJavaElementName(field.getFieldName()), BasicElementLabels.getJavaElementName(field.getNewFieldName()) }));
			}
		}
		String fieldString= JDTRefactoringDescriptorComment.createCompositeSetting(RefactoringCoreMessages.ExtractClassRefactoring_comment_move_field, strings.toArray(new String[strings
				.size()]));
		comment.addSetting(fieldString);

		if (fDescriptor.isCreateGetterSetter())
			comment.addSetting(RefactoringCoreMessages.ExtractClassRefactoring_comment_getters);

		comment.addSetting(Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_comment_fieldname, BasicElementLabels.getJavaElementName(fDescriptor.getFieldName())));
		return comment.asString();
	}

	private class FieldUpdate extends CreationListener {
		@Override
		public void fieldCreated(CompilationUnitRewrite cuRewrite, FieldDeclaration field, ParameterInfo pi) {
			FieldInfo fieldInfo= getFieldInfo(pi.getOldName());
			FieldDeclaration parent= (FieldDeclaration) fieldInfo.declaration.getParent();
			List<IExtendedModifier> modifiers= parent.modifiers();
			ListRewrite listRewrite= cuRewrite.getASTRewrite().getListRewrite(field, FieldDeclaration.MODIFIERS2_PROPERTY);
			for (Iterator<IExtendedModifier> iterator= modifiers.iterator(); iterator.hasNext();) {
				IExtendedModifier mod= iterator.next();
				//Temporarily disabled until initialization of final fields is handled correctly
//				if (mod.isModifier()) {
//					Modifier modifier= (Modifier) mod;
//					if (modifier.isFinal())
//						listRewrite.insertLast(moveNode(cuRewrite, modifier), null);
//				}
				if (mod.isAnnotation()) {
					listRewrite.insertFirst(moveNode(cuRewrite, (ASTNode) mod), null);
				}
			}
			if (fieldInfo.initializer != null && fieldInfo.hasFieldReference()) {
				List<VariableDeclarationFragment> fragments= field.fragments();
				for (Iterator<VariableDeclarationFragment> iterator= fragments.iterator(); iterator.hasNext();) {
					VariableDeclarationFragment vdf= iterator.next();
					vdf.setInitializer((Expression) moveNode(cuRewrite, fieldInfo.initializer));
				}
			}
			if (parent.getJavadoc() != null) {
				field.setJavadoc((Javadoc) moveNode(cuRewrite, parent.getJavadoc()));
			}
		}

		@Override
		public boolean isCreateSetter(ParameterInfo pi) {
			return true; //ignore that the original variable was final
		}

		@Override
		public boolean isUseInConstructor(ParameterInfo pi) {
			FieldInfo fi= getFieldInfo(pi.getOldName());
			return fi.initializer != null && !fi.hasFieldReference();
		}
	}

	private List<ResourceChange> createParameterObject(ParameterObjectFactory pof, IPackageFragmentRoot packageRoot) throws CoreException {
		FieldUpdate fieldUpdate= new FieldUpdate();
		if (fDescriptor.isCreateTopLevel())
			return pof.createTopLevelParameterObject(packageRoot, fieldUpdate);
		else {
			CompilationUnit root= fBaseCURewrite.getRoot();
			TypeDeclaration typeDecl= ASTNodeSearchUtil.getTypeDeclarationNode(fDescriptor.getType(), root);
			ASTRewrite rewrite= fBaseCURewrite.getASTRewrite();
			ListRewrite listRewrite= rewrite.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
			TypeDeclaration paramClass= pof.createClassDeclaration(typeDecl.getName().getFullyQualifiedName(), fBaseCURewrite, fieldUpdate);
			paramClass.modifiers().add(rewrite.getAST().newModifier(ModifierKeyword.PUBLIC_KEYWORD));
			if (shouldParamClassBeStatic(typeDecl)) {
				paramClass.modifiers().add(rewrite.getAST().newModifier(ModifierKeyword.STATIC_KEYWORD));
			}
			listRewrite.insertFirst(paramClass, fBaseCURewrite.createGroupDescription(RefactoringCoreMessages.ExtractClassRefactoring_group_insert_parameter));
			return new ArrayList<ResourceChange>(); //Change will be generated later for fBaseCURewrite
		}

	}

	private boolean shouldParamClassBeStatic(TypeDeclaration enclosingTypeDecl) {
		if (enclosingTypeDecl.isPackageMemberTypeDeclaration()) {
			return true;
		}
		ITypeBinding binding= enclosingTypeDecl.resolveBinding();
		int modifiers= binding != null ? binding.getModifiers() : enclosingTypeDecl.getModifiers();
		return Modifier.isStatic(modifiers);
	}

	private ParameterObjectFactory initializeFactory() {
		ParameterObjectFactory pof= new ParameterObjectFactory();
		pof.setClassName(fDescriptor.getClassName());
		pof.setPackage(fDescriptor.getPackage());
		pof.setEnclosingType(fDescriptor.getType().getFullyQualifiedName('.'));
		pof.setCreateGetter(fDescriptor.isCreateGetterSetter());
		pof.setCreateSetter(fDescriptor.isCreateGetterSetter());
		List<ParameterInfo> variables= new ArrayList<ParameterInfo>();
		for (Iterator<FieldInfo> iterator= fVariables.values().iterator(); iterator.hasNext();) {
			FieldInfo info= iterator.next();
			boolean createField= isCreateField(info);
			info.pi.setCreateField(createField);
			if (createField) {
				Field field= getField(info.name);
				info.pi.setNewName(field.getNewFieldName());
			}
			variables.add(info.pi);
		}
		pof.setVariables(variables);
		return pof;
	}

	private Field getField(String name) {
		Field[] fields= fDescriptor.getFields();
		for (int i= 0; i < fields.length; i++) {
			Field field= fields[i];
			if (field.getFieldName().equals(name))
				return field;
		}
		return null;
	}


	private RefactoringStatus updateReferences(IType type, ParameterObjectFactory pof, IProgressMonitor pm) throws CoreException {
		RefactoringStatus status= new RefactoringStatus();
		pm.beginTask(RefactoringCoreMessages.ExtractClassRefactoring_progress_updating_references, 100);
		try {
			pm.worked(10);
			if (pm.isCanceled())
				throw new OperationCanceledException();
			List<IField> validIFields= new ArrayList<IField>();
			for (Iterator<FieldInfo> iterator= fVariables.values().iterator(); iterator.hasNext();) {
				FieldInfo info= iterator.next();
				if (isCreateField(info))
					validIFields.add(info.ifield);
			}
			if (validIFields.size() == 0) {
				status.addWarning(RefactoringCoreMessages.ExtractClassRefactoring_warning_no_fields_moved, JavaStatusContext.create(type));
				return status;
			}
			SearchPattern pattern= RefactoringSearchEngine.createOrPattern(validIFields.toArray(new IField[validIFields.size()]), IJavaSearchConstants.ALL_OCCURRENCES);
			SearchResultGroup[] results= RefactoringSearchEngine.search(pattern, RefactoringScopeFactory.create(type), pm, status);
			SubProgressMonitor spm= new SubProgressMonitor(pm, 90);
			spm.beginTask(RefactoringCoreMessages.ExtractClassRefactoring_progress_updating_references, results.length * 10);
			try {
				for (int i= 0; i < results.length; i++) {
					SearchResultGroup group= results[i];
					ICompilationUnit unit= group.getCompilationUnit();

					CompilationUnitRewrite cuRewrite;
					if (unit.equals(fBaseCURewrite.getCu()))
						cuRewrite= fBaseCURewrite;
					else
						cuRewrite= new CompilationUnitRewrite(unit);
					spm.worked(1);

					status.merge(replaceReferences(pof, group, cuRewrite));
					if (cuRewrite != fBaseCURewrite) //Change for fBaseCURewrite will be generated later
						fChangeManager.manage(unit, cuRewrite.createChange(true, new SubProgressMonitor(spm, 9)));
					if (spm.isCanceled())
						throw new OperationCanceledException();
				}
			} finally {
				spm.done();
			}
		} finally {
			pm.done();
		}
		return status;
	}

	private RefactoringStatus replaceReferences(ParameterObjectFactory pof, SearchResultGroup group, CompilationUnitRewrite cuRewrite) {
		TextEditGroup writeGroup= cuRewrite.createGroupDescription(RefactoringCoreMessages.ExtractClassRefactoring_group_replace_write);
		TextEditGroup readGroup= cuRewrite.createGroupDescription(RefactoringCoreMessages.ExtractClassRefactoring_group_replace_read);
		ITypeRoot typeRoot= cuRewrite.getCu();
		IJavaProject javaProject= typeRoot.getJavaProject();
		AST ast= cuRewrite.getAST();

		RefactoringStatus status= new RefactoringStatus();
		String parameterName= fDescriptor.getFieldName();

		SearchMatch[] searchResults= group.getSearchResults();
		for (int j= 0; j < searchResults.length; j++) {
			SearchMatch searchMatch= searchResults[j];
			ASTNode node= NodeFinder.perform(cuRewrite.getRoot(), searchMatch.getOffset(), searchMatch.getLength());
			ASTNode parent= node.getParent();
			boolean isDeclaration= parent instanceof VariableDeclaration && ((VariableDeclaration)parent).getInitializer() != node;
			if (!isDeclaration && node instanceof SimpleName) {
				ASTRewrite rewrite= cuRewrite.getASTRewrite();
				if (parent.getNodeType() == ASTNode.SWITCH_CASE)
					status.addError(RefactoringCoreMessages.ExtractClassRefactoring_error_switch, JavaStatusContext.create(typeRoot, node));

				SimpleName name= (SimpleName) node;
				ParameterInfo pi= getFieldInfo(name.getIdentifier()).pi;
				boolean writeAccess= ASTResolving.isWriteAccess(name);
				if (writeAccess && fDescriptor.isCreateGetterSetter()) {
					boolean useSuper= parent.getNodeType() == ASTNode.SUPER_FIELD_ACCESS;
					Expression qualifier= getQualifier(parent);
					ASTNode replaceNode= getReplacementNode(parent, useSuper, qualifier);
					Expression assignedValue= getAssignedValue(pof, parameterName, javaProject, status, rewrite, pi, useSuper, name.resolveTypeBinding(), qualifier, replaceNode, typeRoot);
					if (assignedValue == null) {
						status.addError(RefactoringCoreMessages.ExtractClassRefactoring_error_unable_to_convert_node, JavaStatusContext.create(typeRoot, replaceNode));
					} else {
						NullLiteral marker= qualifier == null ? null : ast.newNullLiteral();
						Expression access= pof.createFieldWriteAccess(pi, parameterName, ast, javaProject, assignedValue, useSuper, marker);
						replaceMarker(rewrite, qualifier, access, marker);
						rewrite.replace(replaceNode, access, writeGroup);
					}
				} else {
					Expression fieldReadAccess= pof.createFieldReadAccess(pi, parameterName, ast, javaProject, false, null); //qualifier is already there
					rewrite.replace(name, fieldReadAccess, readGroup);
				}
			}
		}
		return status;
	}

	private Expression getAssignedValue(ParameterObjectFactory pof, String parameterName, IJavaProject javaProject, RefactoringStatus status, ASTRewrite rewrite, ParameterInfo pi, boolean useSuper, ITypeBinding typeBinding, Expression qualifier, ASTNode replaceNode, ITypeRoot typeRoot) {
		AST ast= rewrite.getAST();
		boolean is50OrHigher= JavaModelUtil.is50OrHigher(javaProject);
		Expression assignedValue= handleSimpleNameAssignment(replaceNode, pof, parameterName, ast, javaProject, useSuper);
		if (assignedValue == null) {
			NullLiteral marker= qualifier == null ? null : ast.newNullLiteral();
			Expression fieldReadAccess= pof.createFieldReadAccess(pi, parameterName, ast, javaProject, useSuper, marker);
			assignedValue= GetterSetterUtil.getAssignedValue(replaceNode, rewrite, fieldReadAccess, typeBinding, is50OrHigher);
			boolean markerReplaced= replaceMarker(rewrite, qualifier, assignedValue, marker);
			if (markerReplaced) {
				switch (qualifier.getNodeType()) {
					case ASTNode.METHOD_INVOCATION:
					case ASTNode.CLASS_INSTANCE_CREATION:
					case ASTNode.SUPER_METHOD_INVOCATION:
					case ASTNode.PARENTHESIZED_EXPRESSION:
						status.addWarning(RefactoringCoreMessages.ExtractClassRefactoring_warning_semantic_change, JavaStatusContext.create(typeRoot, replaceNode));
						break;
				}
			}
		}
		return assignedValue;
	}

	private ASTNode getReplacementNode(ASTNode parent, boolean useSuper, Expression qualifier) {
		if (qualifier != null || useSuper) {
			return parent.getParent();
		} else {
			return parent;
		}
	}

	private Expression getQualifier(ASTNode parent) {
		switch (parent.getNodeType()) {
			case ASTNode.FIELD_ACCESS:
				return ((FieldAccess) parent).getExpression();
			case ASTNode.QUALIFIED_NAME:
				return ((QualifiedName)parent).getQualifier();
			case ASTNode.SUPER_FIELD_ACCESS:
				return ((SuperFieldAccess)parent).getQualifier();
			default:
				return null;
		}
	}

	/*
	 * Replaces the NullLiteral dummy with the copied qualifier
	 */
	private boolean replaceMarker(final ASTRewrite rewrite, final Expression qualifier, Expression assignedValue, final NullLiteral marker) {
		class MarkerReplacer extends ASTVisitor {

			private boolean fReplaced= false;

			@Override
			public boolean visit(NullLiteral node) {
				if (node == marker) {
					rewrite.replace(node, rewrite.createCopyTarget(qualifier), null);
					fReplaced= true;
					return false;
				}
				return true;
			}
		}
		if (assignedValue != null && qualifier != null) {
			MarkerReplacer visitor= new MarkerReplacer();
			assignedValue.accept(visitor);
			return visitor.fReplaced;
		}
		return false;
	}

	private Expression handleSimpleNameAssignment(ASTNode replaceNode, ParameterObjectFactory pof, String parameterName, AST ast, IJavaProject javaProject, boolean useSuper) {
		if (replaceNode instanceof Assignment) {
			Assignment assignment= (Assignment) replaceNode;
			Expression rightHandSide= assignment.getRightHandSide();
			if (rightHandSide.getNodeType() == ASTNode.SIMPLE_NAME) {
				SimpleName sn= (SimpleName) rightHandSide;
				IVariableBinding binding= ASTNodes.getVariableBinding(sn);
				if (binding != null && binding.isField()) {
					if (fDescriptor.getType().getFullyQualifiedName().equals(binding.getDeclaringClass().getQualifiedName())) {
						FieldInfo fieldInfo= getFieldInfo(binding.getName());
						if (fieldInfo != null && binding == fieldInfo.pi.getOldBinding()) {
							return pof.createFieldReadAccess(fieldInfo.pi, parameterName, ast, javaProject, useSuper, null);
						}
					}
				}
			}
		}
		return null;
	}

	private FieldInfo getFieldInfo(String identifier) {
		return fVariables.get(identifier);
	}

	private FieldDeclaration performFieldRewrite(IType type, ParameterObjectFactory pof, RefactoringStatus status) throws CoreException {
		fBaseCURewrite= new CompilationUnitRewrite(type.getCompilationUnit());
		SimpleName name= (SimpleName) NodeFinder.perform(fBaseCURewrite.getRoot(), type.getNameRange());
		TypeDeclaration typeNode= (TypeDeclaration) ASTNodes.getParent(name, ASTNode.TYPE_DECLARATION);
		ASTRewrite rewrite= fBaseCURewrite.getASTRewrite();
		int modifier= Modifier.PRIVATE;
		TextEditGroup removeFieldGroup= fBaseCURewrite.createGroupDescription(RefactoringCoreMessages.ExtractClassRefactoring_group_remove_field);
		FieldDeclaration lastField= null;
		initializeDeclaration(typeNode);
		for (Iterator<FieldInfo> iter= fVariables.values().iterator(); iter.hasNext();) {
			FieldInfo pi= iter.next();
			if (isCreateField(pi)) {
				VariableDeclarationFragment vdf= pi.declaration;
				FieldDeclaration parent= (FieldDeclaration) vdf.getParent();
				if (lastField == null)
					lastField= parent;
				else if (lastField.getStartPosition() < parent.getStartPosition())
					lastField= parent;

				ListRewrite listRewrite= rewrite.getListRewrite(parent, FieldDeclaration.FRAGMENTS_PROPERTY);
				removeNode(vdf, removeFieldGroup, fBaseCURewrite);
				if (listRewrite.getRewrittenList().size() == 0) {
					removeNode(parent, removeFieldGroup, fBaseCURewrite);
				}

				if (fDescriptor.isCreateTopLevel()) {
					IVariableBinding binding= vdf.resolveBinding();
					ITypeRoot typeRoot= fBaseCURewrite.getCu();
					if (binding == null || binding.getType() == null){
						status.addFatalError(Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_fatal_error_cannot_resolve_binding, BasicElementLabels.getJavaElementName(pi.name)), JavaStatusContext.create(typeRoot, vdf));
					} else {
						ITypeBinding typeBinding= binding.getType();
						if (Modifier.isPrivate(typeBinding.getModifiers())){
							status.addError(Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_error_referencing_private_class, BasicElementLabels.getJavaElementName(typeBinding.getName())), JavaStatusContext.create(typeRoot, vdf));
						} else if (Modifier.isProtected(typeBinding.getModifiers())){
							ITypeBinding declaringClass= typeBinding.getDeclaringClass();
							if (declaringClass != null) {
								IPackageBinding package1= declaringClass.getPackage();
								if (package1 != null && !fDescriptor.getPackage().equals(package1.getName())){
									status.addError(Messages.format(RefactoringCoreMessages.ExtractClassRefactoring_error_referencing_protected_class, new String[] {BasicElementLabels.getJavaElementName(typeBinding.getName()), BasicElementLabels.getJavaElementName(fDescriptor.getPackage())}), JavaStatusContext.create(typeRoot, vdf));
								}
							}
						}
					}
				}
				Expression initializer= vdf.getInitializer();
				if (initializer != null)
					pi.initializer= initializer;
				int modifiers= parent.getModifiers();
				if (!MemberVisibilityAdjustor.hasLowerVisibility(modifiers, modifier)){
					modifier= modifiers;
				}
			}
		}
		FieldDeclaration fieldDeclaration= createParameterObjectField(pof, typeNode, modifier);
		ListRewrite bodyDeclList= rewrite.getListRewrite(typeNode, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		if (lastField != null)
			bodyDeclList.insertAfter(fieldDeclaration, lastField, null);
		else
			bodyDeclList.insertFirst(fieldDeclaration, null);
		return fieldDeclaration;
	}

	private void initializeDeclaration(TypeDeclaration node) {
		FieldDeclaration[] fields= node.getFields();
		for (int i= 0; i < fields.length; i++) {
			FieldDeclaration fieldDeclaration= fields[i];
			List<VariableDeclarationFragment> fragments= fieldDeclaration.fragments();
			for (Iterator<VariableDeclarationFragment> iterator= fragments.iterator(); iterator.hasNext();) {
				VariableDeclarationFragment vdf= iterator.next();
				FieldInfo fieldInfo= getFieldInfo(vdf.getName().getIdentifier());
				if (fieldInfo != null) {
					Assert.isNotNull(vdf);
					fieldInfo.declaration= vdf;
					fieldInfo.pi.setOldBinding(vdf.resolveBinding());
				}
			}
		}
	}

	private void removeNode(ASTNode parent, TextEditGroup removeFieldGroup, CompilationUnitRewrite baseCURewrite) {
		baseCURewrite.getASTRewrite().remove(parent, removeFieldGroup);
		baseCURewrite.getImportRemover().registerRemovedNode(parent);
	}

	private FieldDeclaration createParameterObjectField(ParameterObjectFactory pof, TypeDeclaration typeNode, int modifier) {
		AST ast= fBaseCURewrite.getAST();
		ClassInstanceCreation creation= ast.newClassInstanceCreation();
		creation.setType(pof.createType(fDescriptor.isCreateTopLevel(), fBaseCURewrite, typeNode.getStartPosition()));
		ListRewrite listRewrite= fBaseCURewrite.getASTRewrite().getListRewrite(creation, ClassInstanceCreation.ARGUMENTS_PROPERTY);
		for (Iterator<FieldInfo> iter= fVariables.values().iterator(); iter.hasNext();) {
			FieldInfo fi= iter.next();
			if (isCreateField(fi)) {
				Expression expression= fi.initializer;
				if (expression != null && !fi.hasFieldReference()) {
					importNodeTypes(expression, fBaseCURewrite);
					ASTNode createMoveTarget= fBaseCURewrite.getASTRewrite().createMoveTarget(expression);
					if (expression instanceof ArrayInitializer) {
						ArrayInitializer ai= (ArrayInitializer) expression;
						ITypeBinding type= ai.resolveTypeBinding();
						Type addImport= fBaseCURewrite.getImportRewrite().addImport(type, ast);
						fBaseCURewrite.getImportRemover().registerAddedImports(addImport);
						ArrayCreation arrayCreation= ast.newArrayCreation();
						arrayCreation.setType((ArrayType) addImport);
						arrayCreation.setInitializer((ArrayInitializer) createMoveTarget);
						listRewrite.insertLast(arrayCreation, null);
					} else {
						listRewrite.insertLast(createMoveTarget, null);
					}
				}
			}
		}

		VariableDeclarationFragment fragment= ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName(fDescriptor.getFieldName()));
		fragment.setInitializer(creation);

		ModifierKeyword acc= null;
		if (Modifier.isPublic(modifier)) {
			acc= ModifierKeyword.PUBLIC_KEYWORD;
		} else if (Modifier.isProtected(modifier)) {
			acc= ModifierKeyword.PROTECTED_KEYWORD;
		} else if (Modifier.isPrivate(modifier)) {
			acc= ModifierKeyword.PRIVATE_KEYWORD;
		}

		FieldDeclaration fieldDeclaration= ast.newFieldDeclaration(fragment);
		fieldDeclaration.setType(pof.createType(fDescriptor.isCreateTopLevel(), fBaseCURewrite, typeNode.getStartPosition()));
		if (acc != null)
			fieldDeclaration.modifiers().add(ast.newModifier(acc));
		return fieldDeclaration;
	}

	private void importNodeTypes(ASTNode node, final CompilationUnitRewrite cuRewrite) {
		ASTResolving.visitAllBindings(node, new TypeBindingVisitor() {
			public boolean visit(ITypeBinding nodeBinding) {
				ParameterObjectFactory.importBinding(nodeBinding, cuRewrite);
				return false;
			}
		});
	}

	private boolean isCreateField(FieldInfo fi) {
		return getField(fi.name).isCreateField();
	}


	@Override
	public String getName() {
		return RefactoringCoreMessages.ExtractClassRefactoring_refactoring_name;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ltk.core.refactoring.Refactoring#getAdapter(java.lang.Class)
	 */
	@Override
	public Object getAdapter(Class adapter) {
		if (adapter == ExtractClassDescriptorVerification.class) {
			return fVerification;
		}
		return super.getAdapter(adapter);
	}
}

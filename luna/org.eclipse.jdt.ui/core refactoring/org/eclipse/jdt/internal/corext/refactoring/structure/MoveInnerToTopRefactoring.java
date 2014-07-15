/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Samrat Dhillon samrat.dhillon@gmail.com - [move member type] Moving a member interface to its own file adds the host's type parameters to it - https://bugs.eclipse.org/bugs/show_bug.cgi?id=385237
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.IRefactoringStatusEntryComparator;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;
import org.eclipse.ltk.core.refactoring.TextChange;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.dom.rewrite.TargetSourceRangeComputer;
import org.eclipse.jdt.core.dom.rewrite.TargetSourceRangeComputer.SourceRange;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.ConvertMemberTypeDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringDescriptorUtil;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine2;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreateCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.structure.MemberVisibilityAdjustor.OutgoingMemberVisibilityAdjustment;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.JavadocUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.SearchUtils;
import org.eclipse.jdt.internal.corext.util.Strings;

import org.eclipse.jdt.ui.CodeGeneration;
import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.BindingLabelProvider;


public final class MoveInnerToTopRefactoring extends Refactoring {

	private static final String ATTRIBUTE_FIELD= "field"; //$NON-NLS-1$
	private static final String ATTRIBUTE_MANDATORY= "mandatory"; //$NON-NLS-1$
	private static final String ATTRIBUTE_POSSIBLE= "possible"; //$NON-NLS-1$
	private static final String ATTRIBUTE_FINAL= "final"; //$NON-NLS-1$
	private static final String ATTRIBUTE_FIELD_NAME= "fieldName"; //$NON-NLS-1$
	private static final String ATTRIBUTE_PARAMETER_NAME= "parameterName"; //$NON-NLS-1$

	private static class MemberAccessNodeCollector extends ASTVisitor {

		private final ITypeBinding fCurrentType;

		private final List<MethodInvocation> fMethodAccesses= new ArrayList<MethodInvocation>(0);

		private final List<Name> fSimpleNames= new ArrayList<Name>(0);

		MemberAccessNodeCollector(ITypeBinding currType) {
			Assert.isNotNull(currType);
			fCurrentType= currType;
		}

		MethodInvocation[] getMethodInvocations() {
			return fMethodAccesses.toArray(new MethodInvocation[fMethodAccesses.size()]);
		}

		SimpleName[] getSimpleFieldNames() {
			return fSimpleNames.toArray(new SimpleName[fSimpleNames.size()]);
		}

		@Override
		public boolean visit(FieldAccess node) {
			// field accesses always have an expression: look at the expression to find out if we do an outer instance access.
			node.getExpression().accept(this);
			return false;
		}

		@Override
		public boolean visit(MethodInvocation node) {
			Expression expression= node.getExpression();
			if (expression == null) {
				IMethodBinding binding= node.resolveMethodBinding();
				if (binding != null) {
					if (isAccessToOuter(binding.getDeclaringClass())) {
						fMethodAccesses.add(node);
					}
				}
			} else {
				expression.accept(this);
			}
			List<Expression> arguments= node.arguments();
			for (int i= 0; i < arguments.size(); i++) {
				arguments.get(i).accept(this);
			}
			return false;
		}
		@Override
		public boolean visit(QualifiedName node) {
			node.getQualifier().accept(this);
			return false;
		}

		@Override
		public boolean visit(SimpleName node) {
			IBinding binding= node.resolveBinding();
			if (binding instanceof IVariableBinding) {
				IVariableBinding variableBinding= (IVariableBinding) binding;
				if (variableBinding.isField()) {
					if (isAccessToOuter(variableBinding.getDeclaringClass())) {
						fSimpleNames.add(node);
					}
				}
			}
			return false;
		}

		@Override
		public boolean visit(ThisExpression node) {
			final Name qualifier= node.getQualifier();
			if (qualifier != null) {
				final ITypeBinding binding= qualifier.resolveTypeBinding();
				if (binding != null && binding != fCurrentType.getTypeDeclaration()) {
					fSimpleNames.add(qualifier);
				}
			}
			return super.visit(node);
		}

		private boolean isAccessToOuter(ITypeBinding binding) {
			binding= binding.getTypeDeclaration();
			if (Bindings.isSuperType(binding, fCurrentType, false)) {
				return false;
			}
			ITypeBinding outer= fCurrentType.getDeclaringClass();
			while (outer != null) {
				if (Bindings.isSuperType(binding, outer, false)) {
					return true;
				}
				outer= outer.getDeclaringClass();
			}
			return false;
		}


	}

	private class TypeReferenceQualifier extends ASTVisitor {

		private final TextEditGroup fGroup;

		private final ITypeBinding fTypeBinding;

		public TypeReferenceQualifier(final ITypeBinding type, final TextEditGroup group) {
			Assert.isNotNull(type);
			Assert.isNotNull(type.getDeclaringClass());
			fTypeBinding= type;
			fGroup= group;
		}

		@Override
		public boolean visit(final ClassInstanceCreation node) {
			Assert.isNotNull(node);
			if (fCreateInstanceField) {
				final AST ast= node.getAST();
				final Type type= node.getType();
				final ITypeBinding binding= type.resolveBinding();
				if (binding != null && binding.getDeclaringClass() != null && !Bindings.equals(binding, fTypeBinding) && fSourceRewrite.getRoot().findDeclaringNode(binding) != null) {
					if (!Modifier.isStatic(binding.getModifiers())) {
						Expression expression= null;
						if (fCodeGenerationSettings.useKeywordThis || fEnclosingInstanceFieldName.equals(fNameForEnclosingInstanceConstructorParameter)) {
							final FieldAccess access= ast.newFieldAccess();
							access.setExpression(ast.newThisExpression());
							access.setName(ast.newSimpleName(fEnclosingInstanceFieldName));
							expression= access;
						} else
							expression= ast.newSimpleName(fEnclosingInstanceFieldName);
						if (node.getExpression() != null)
							fSourceRewrite.getImportRemover().registerRemovedNode(node.getExpression());
						fSourceRewrite.getASTRewrite().set(node, ClassInstanceCreation.EXPRESSION_PROPERTY, expression, fGroup);
					} else
						addTypeQualification(type, fSourceRewrite, fGroup);
				}
			}
			return true;
		}

		@Override
		public boolean visit(final QualifiedType node) {
			Assert.isNotNull(node);
			return false;
		}

		@Override
		public boolean visit(final SimpleType node) {
			Assert.isNotNull(node);
			if (!(node.getParent() instanceof ClassInstanceCreation)) {
				final ITypeBinding binding= node.resolveBinding();
				if (binding != null) {
					final ITypeBinding declaring= binding.getDeclaringClass();
					if (declaring != null && !Bindings.equals(declaring, fTypeBinding.getDeclaringClass()) && !Bindings.equals(binding, fTypeBinding) && fSourceRewrite.getRoot().findDeclaringNode(binding) != null && Modifier.isStatic(binding.getModifiers()))
						addTypeQualification(node, fSourceRewrite, fGroup);
				}
			}
			return super.visit(node);
		}

		@Override
		public boolean visit(final ThisExpression node) {
			Assert.isNotNull(node);
			Name name= node.getQualifier();
			if (fCreateInstanceField && name != null) {
				ITypeBinding binding= node.resolveTypeBinding();
				if (binding != null && Bindings.equals(binding, fTypeBinding.getDeclaringClass())) {
					AST ast= node.getAST();
					Expression expression= null;
					if (fCodeGenerationSettings.useKeywordThis || fEnclosingInstanceFieldName.equals(fNameForEnclosingInstanceConstructorParameter)) {
						FieldAccess access= ast.newFieldAccess();
						access.setExpression(ast.newThisExpression());
						access.setName(ast.newSimpleName(fEnclosingInstanceFieldName));
						expression= access;
					} else {
						expression= ast.newSimpleName(fEnclosingInstanceFieldName);
					}
					fSourceRewrite.getASTRewrite().replace(node, expression, null);
				}
			}
			return super.visit(node);
		}
	}

	private static void addTypeParameters(final CompilationUnit unit, final IType type, final Map<String, ITypeBinding> map) throws JavaModelException {
		Assert.isNotNull(unit);
		Assert.isNotNull(type);
		Assert.isNotNull(map);
		final AbstractTypeDeclaration declaration= ASTNodeSearchUtil.getAbstractTypeDeclarationNode(type, unit);
		if (declaration instanceof TypeDeclaration) {
			ITypeBinding binding= null;
			TypeParameter parameter= null;
			for (final Iterator<TypeParameter> iterator= ((TypeDeclaration) declaration).typeParameters().iterator(); iterator.hasNext();) {
				parameter= iterator.next();
				binding= parameter.resolveBinding();
				if (binding != null && !map.containsKey(binding.getKey()))
					map.put(binding.getKey(), binding);
			}
			final IType declaring= type.getDeclaringType();
			if (declaring != null && !JdtFlags.isStatic(type))
				addTypeParameters(unit, declaring, map);
		}
	}

	private static boolean containsNonStatic(MethodInvocation[] invocations) {
		for (int i= 0; i < invocations.length; i++) {
			if (!isStatic(invocations[i]))
				return true;
		}
		return false;
	}

	private static boolean containsNonStatic(SimpleName[] fieldNames) {
		for (int i= 0; i < fieldNames.length; i++) {
			if (!isStaticFieldName(fieldNames[i]))
				return true;
		}
		return false;
	}

	private static boolean containsStatusEntry(final RefactoringStatus status, final RefactoringStatusEntry other) {
		return status.getEntries(new IRefactoringStatusEntryComparator() {

			public final int compare(final RefactoringStatusEntry entry1, final RefactoringStatusEntry entry2) {
				return entry1.getMessage().compareTo(entry2.getMessage());
			}
		}, other).length > 0;
	}

	private static AbstractTypeDeclaration findTypeDeclaration(IType enclosing, AbstractTypeDeclaration[] declarations) {
		String typeName= enclosing.getElementName();
		for (int i= 0; i < declarations.length; i++) {
			AbstractTypeDeclaration declaration= declarations[i];
			if (declaration.getName().getIdentifier().equals(typeName))
				return declaration;
		}
		return null;
	}

	private static AbstractTypeDeclaration findTypeDeclaration(IType type, CompilationUnit unit) {
		final List<IType> types= getDeclaringTypes(type);
		types.add(type);
		AbstractTypeDeclaration[] declarations= (AbstractTypeDeclaration[]) unit.types().toArray(new AbstractTypeDeclaration[unit.types().size()]);
		AbstractTypeDeclaration declaration= null;
		for (final Iterator<IType> iterator= types.iterator(); iterator.hasNext();) {
			IType enclosing= iterator.next();
			declaration= findTypeDeclaration(enclosing, declarations);
			Assert.isNotNull(declaration);
			declarations= getAbstractTypeDeclarations(declaration);
		}
		Assert.isNotNull(declaration);
		return declaration;
	}

	public static AbstractTypeDeclaration[] getAbstractTypeDeclarations(final AbstractTypeDeclaration declaration) {
		int typeCount= 0;
		for (Iterator<BodyDeclaration> iterator= declaration.bodyDeclarations().listIterator(); iterator.hasNext();) {
			if (iterator.next() instanceof AbstractTypeDeclaration) {
				typeCount++;
			}
		}
		AbstractTypeDeclaration[] declarations= new AbstractTypeDeclaration[typeCount];
		int next= 0;
		for (final Iterator<BodyDeclaration> iterator= declaration.bodyDeclarations().listIterator(); iterator.hasNext();) {
			BodyDeclaration object= iterator.next();
			if (object instanceof AbstractTypeDeclaration) {
				declarations[next++]= (AbstractTypeDeclaration) object;
			}
		}
		return declarations;
	}

	private static List<IType> getDeclaringTypes(IType type) {
		IType declaringType= type.getDeclaringType();
		if (declaringType == null)
			return new ArrayList<IType>(0);
		List<IType> result= getDeclaringTypes(declaringType);
		result.add(declaringType);
		return result;
	}

	private static String[] getFieldNames(IType type) {
		try {
			IField[] fields= type.getFields();
			List<String> result= new ArrayList<String>(fields.length);
			for (int i= 0; i < fields.length; i++) {
				result.add(fields[i].getElementName());
			}
			return result.toArray(new String[result.size()]);
		} catch (JavaModelException e) {
			return null;
		}
	}

	private static Set<ICompilationUnit> getMergedSet(Set<ICompilationUnit> s1, Set<ICompilationUnit> s2) {
		Set<ICompilationUnit> result= new HashSet<ICompilationUnit>();
		result.addAll(s1);
		result.addAll(s2);
		return result;
	}

	private static String[] getParameterNamesOfAllConstructors(IType type) throws JavaModelException {
		IMethod[] constructors= JavaElementUtil.getAllConstructors(type);
		Set<String> result= new HashSet<String>();
		for (int i= 0; i < constructors.length; i++) {
			result.addAll(Arrays.asList(constructors[i].getParameterNames()));
		}
		return result.toArray(new String[result.size()]);
	}

	private static ASTNode[] getReferenceNodesIn(CompilationUnit cuNode, Map<ICompilationUnit, SearchMatch[]> references, ICompilationUnit cu) {
		SearchMatch[] results= references.get(cu);
		if (results == null)
			return new ASTNode[0];
		return ASTNodeSearchUtil.getAstNodes(results, cuNode);
	}

	private static boolean isCorrespondingTypeBinding(ITypeBinding binding, IType type) {
		if (binding == null)
			return false;
		return Bindings.getFullyQualifiedName(binding).equals(JavaElementUtil.createSignature(type));
	}

	private static boolean isStatic(MethodInvocation invocation) {
		IMethodBinding methodBinding= invocation.resolveMethodBinding();
		if (methodBinding == null)
			return false;
		return JdtFlags.isStatic(methodBinding);
	}

	private static boolean isStaticFieldName(SimpleName name) {
		IBinding binding= name.resolveBinding();
		if (!(binding instanceof IVariableBinding))
			return false;
		IVariableBinding variableBinding= (IVariableBinding) binding;
		if (!variableBinding.isField())
			return false;
		return JdtFlags.isStatic(variableBinding);
	}

	private TextChangeManager fChangeManager;

	private CodeGenerationSettings fCodeGenerationSettings;

	private boolean fCreateInstanceField;

	private String fEnclosingInstanceFieldName;

	private boolean fIsInstanceFieldCreationMandatory;

	private boolean fIsInstanceFieldCreationPossible;

	private boolean fMarkInstanceFieldAsFinal;

	private String fNameForEnclosingInstanceConstructorParameter;

	private String fNewSourceOfInputType;

	private CompilationUnitRewrite fSourceRewrite;

	private Collection<IBinding> fStaticImports;

	private IType fType;

	private String fQualifiedTypeName;

	private Collection<ITypeBinding> fTypeImports;

	/**
	 * Creates a new move inner to top refactoring.
	 * @param type the type, or <code>null</code> if invoked by scripting
	 * @param settings the code generation settings, or <code>null</code> if invoked by scripting
	 * @throws JavaModelException if initialization failed
	 */
	public MoveInnerToTopRefactoring(IType type, CodeGenerationSettings settings) throws JavaModelException {
		fType= type;
		fCodeGenerationSettings= settings;
		fMarkInstanceFieldAsFinal= true; // default
		if (fType != null)
			initialize();
	}

    public MoveInnerToTopRefactoring(JavaRefactoringArguments arguments, RefactoringStatus status) {
		fType= null;
		fCodeGenerationSettings= null;
		fMarkInstanceFieldAsFinal= true; // default
   		RefactoringStatus initializeStatus= initialize(arguments);
   		status.merge(initializeStatus);
    }

	private void initialize() throws JavaModelException {
		fQualifiedTypeName= JavaModelUtil.concatenateName(fType.getPackageFragment().getElementName(), fType.getElementName());
		fEnclosingInstanceFieldName= getInitialNameForEnclosingInstanceField();
		fSourceRewrite= new CompilationUnitRewrite(fType.getCompilationUnit());
		fIsInstanceFieldCreationPossible= !(JdtFlags.isStatic(fType) || fType.isAnnotation() || fType.isEnum() || (fType.getDeclaringType() == null && !JavaElementUtil.isMainType(fType)));
		fIsInstanceFieldCreationMandatory= fIsInstanceFieldCreationPossible && isInstanceFieldCreationMandatory();
		fCreateInstanceField= fIsInstanceFieldCreationMandatory;
	}

	private void addEnclosingInstanceDeclaration(final AbstractTypeDeclaration declaration, final ASTRewrite rewrite) throws CoreException {
		Assert.isNotNull(declaration);
		Assert.isNotNull(rewrite);
		final AST ast= declaration.getAST();
		final VariableDeclarationFragment fragment= ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName(fEnclosingInstanceFieldName));
		final FieldDeclaration newField= ast.newFieldDeclaration(fragment);
		newField.modifiers().addAll(ASTNodeFactory.newModifiers(ast, getEnclosingInstanceAccessModifiers()));
		newField.setType(createEnclosingType(ast));
		final String comment= CodeGeneration.getFieldComment(fType.getCompilationUnit(), declaration.getName().getIdentifier(), fEnclosingInstanceFieldName, StubUtility.getLineDelimiterUsed(fType.getJavaProject()));
		if (comment != null && comment.length() > 0) {
			final Javadoc doc= (Javadoc) rewrite.createStringPlaceholder(comment, ASTNode.JAVADOC);
			newField.setJavadoc(doc);
		}
		rewrite.getListRewrite(declaration, declaration.getBodyDeclarationsProperty()).insertFirst(newField, null);
	}

	private void addEnclosingInstanceTypeParameters(final ITypeBinding[] parameters, final AbstractTypeDeclaration declaration, final ASTRewrite rewrite) {
		Assert.isNotNull(parameters);
		Assert.isNotNull(declaration);
		Assert.isNotNull(rewrite);
		if (declaration instanceof TypeDeclaration) {
			final TypeDeclaration type= (TypeDeclaration) declaration;
			final List<TypeParameter> existing= type.typeParameters();
			final Set<String> names= new HashSet<String>();
			TypeParameter parameter= null;
			for (final Iterator<TypeParameter> iterator= existing.iterator(); iterator.hasNext();) {
				parameter= iterator.next();
				names.add(parameter.getName().getIdentifier());
			}
			final ListRewrite rewriter= rewrite.getListRewrite(type, TypeDeclaration.TYPE_PARAMETERS_PROPERTY);
			String name= null;
			for (int index= 0; index < parameters.length; index++) {
				name= parameters[index].getName();
				if (!names.contains(name)) {
					parameter= type.getAST().newTypeParameter();
					parameter.setName(type.getAST().newSimpleName(name));
					rewriter.insertLast(parameter, null);
				}
			}
		}
	}

	private void addImportsToTargetUnit(final ICompilationUnit targetUnit, final IProgressMonitor monitor) throws CoreException, JavaModelException {
		monitor.beginTask("", 2); //$NON-NLS-1$
		try {
			ImportRewrite rewrite= StubUtility.createImportRewrite(targetUnit, true);
			if (fTypeImports != null) {
				ITypeBinding type= null;
				for (final Iterator<ITypeBinding> iterator= fTypeImports.iterator(); iterator.hasNext();) {
					type= iterator.next();
					rewrite.addImport(type);
				}
			}
			if (fStaticImports != null) {
				IBinding binding= null;
				for (final Iterator<IBinding> iterator= fStaticImports.iterator(); iterator.hasNext();) {
					binding= iterator.next();
					rewrite.addStaticImport(binding);
				}
			}
			fTypeImports= null;
			fStaticImports= null;
			TextEdit edits= rewrite.rewriteImports(new SubProgressMonitor(monitor, 1));
			JavaModelUtil.applyEdit(targetUnit, edits, false, new SubProgressMonitor(monitor, 1));
		} finally {
			monitor.done();
		}
	}

	private void addInheritedTypeQualifications(final AbstractTypeDeclaration declaration, final CompilationUnitRewrite targetRewrite, final TextEditGroup group) {
		Assert.isNotNull(declaration);
		Assert.isNotNull(targetRewrite);
		final CompilationUnit unit= (CompilationUnit) declaration.getRoot();
		final ITypeBinding binding= declaration.resolveBinding();
		if (binding != null) {
			Type type= null;
			if (declaration instanceof TypeDeclaration) {
				type= ((TypeDeclaration) declaration).getSuperclassType();
				if (type != null && unit.findDeclaringNode(binding) != null)
					addTypeQualification(type, targetRewrite, group);
			}
			List<Type> types= null;
			if (declaration instanceof TypeDeclaration)
				types= ((TypeDeclaration) declaration).superInterfaceTypes();
			else if (declaration instanceof EnumDeclaration)
				types= ((EnumDeclaration) declaration).superInterfaceTypes();
			if (types != null) {
				for (final Iterator<Type> iterator= types.iterator(); iterator.hasNext();) {
					type= iterator.next();
					if (unit.findDeclaringNode(type.resolveBinding()) != null)
						addTypeQualification(type, targetRewrite, group);
				}
			}
		}
	}

	private void addParameterToConstructor(final ASTRewrite rewrite, final MethodDeclaration declaration) throws JavaModelException {
		Assert.isNotNull(rewrite);
		Assert.isNotNull(declaration);
		final AST ast= declaration.getAST();
		final String name= getNameForEnclosingInstanceConstructorParameter();
		final SingleVariableDeclaration variable= ast.newSingleVariableDeclaration();
		variable.setType(createEnclosingType(ast));
		variable.setName(ast.newSimpleName(name));
		rewrite.getListRewrite(declaration, MethodDeclaration.PARAMETERS_PROPERTY).insertFirst(variable, null);
		JavadocUtil.addParamJavadoc(name, declaration, rewrite, fType.getJavaProject(), null);
	}

	private void addSimpleTypeQualification(final CompilationUnitRewrite targetRewrite, final ITypeBinding declaring, final SimpleType simpleType, final TextEditGroup group) {
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(declaring);
		Assert.isNotNull(simpleType);
		final AST ast= targetRewrite.getRoot().getAST();
		if (!(simpleType.getName() instanceof QualifiedName)) {
			targetRewrite.getASTRewrite().replace(simpleType, ast.newQualifiedType(targetRewrite.getImportRewrite().addImport(declaring, ast), ast.newSimpleName(simpleType.getName().getFullyQualifiedName())), group);
			targetRewrite.getImportRemover().registerRemovedNode(simpleType);
		}
	}

	private void addTypeQualification(final Type type, final CompilationUnitRewrite targetRewrite, final TextEditGroup group) {
		Assert.isNotNull(type);
		Assert.isNotNull(targetRewrite);
		final ITypeBinding binding= type.resolveBinding();
		if (binding != null) {
			final ITypeBinding declaring= binding.getDeclaringClass();
			if (declaring != null) {
				if (type instanceof SimpleType) {
					final SimpleType simpleType= (SimpleType) type;
					addSimpleTypeQualification(targetRewrite, declaring, simpleType, group);
				} else if (type instanceof ParameterizedType) {
					final ParameterizedType parameterizedType= (ParameterizedType) type;
					final Type rawType= parameterizedType.getType();
					if (rawType instanceof SimpleType)
						addSimpleTypeQualification(targetRewrite, declaring, (SimpleType) rawType, group);
				}
			}
		}
	}

	private RefactoringStatus checkConstructorParameterNames() {
		RefactoringStatus result= new RefactoringStatus();
		CompilationUnit cuNode= new RefactoringASTParser(ASTProvider.SHARED_AST_LEVEL).parse(fType.getCompilationUnit(), false);
		MethodDeclaration[] nodes= getConstructorDeclarationNodes(findTypeDeclaration(fType, cuNode));
		for (int i= 0; i < nodes.length; i++) {
			MethodDeclaration constructor= nodes[i];
			for (Iterator<SingleVariableDeclaration> iter= constructor.parameters().iterator(); iter.hasNext();) {
				SingleVariableDeclaration param= iter.next();
				if (fEnclosingInstanceFieldName.equals(param.getName().getIdentifier())) {
					String[] keys= new String[] { BasicElementLabels.getJavaElementName(param.getName().getIdentifier()), BasicElementLabels.getJavaElementName(fType.getElementName())};
					String msg= Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_name_used, keys);
					result.addError(msg, JavaStatusContext.create(fType.getCompilationUnit(), param));
				}
			}
		}
		return result;
	}

	public RefactoringStatus checkEnclosingInstanceName(String name) {
		if (!fCreateInstanceField)
			return new RefactoringStatus();
		RefactoringStatus result= Checks.checkFieldName(name, fType);
		if (!Checks.startsWithLowerCase(name))
			result.addWarning(RefactoringCoreMessages.MoveInnerToTopRefactoring_names_start_lowercase);

		if (fType.getField(name).exists()) {
			Object[] keys= new String[] { BasicElementLabels.getJavaElementName(name), BasicElementLabels.getJavaElementName(fType.getElementName())};
			String msg= Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_already_declared, keys);
			result.addError(msg, JavaStatusContext.create(fType.getField(name)));
		}
		return result;
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException {
		pm.beginTask("", 2);//$NON-NLS-1$
		try {
			RefactoringStatus result= new RefactoringStatus();

			if (JdtFlags.isStatic(fType))
				result.merge(checkEnclosingInstanceName(fEnclosingInstanceFieldName));

			String newCUName= JavaModelUtil.getRenamedCUName(fType.getCompilationUnit(), fType.getElementName());
			if (fType.getPackageFragment().getCompilationUnit(newCUName).exists()) {
				String message= Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_compilation_Unit_exists, new String[] { BasicElementLabels.getResourceName(newCUName), JavaElementLabels.getElementLabel(fType.getPackageFragment(), JavaElementLabels.ALL_DEFAULT)});
				result.addFatalError(message);
			}
			result.merge(checkEnclosingInstanceName(fEnclosingInstanceFieldName));
			result.merge(Checks.checkCompilationUnitName(newCUName, fType));
			result.merge(checkConstructorParameterNames());
			result.merge(checkTypeNameInPackage());
			fChangeManager= createChangeManager(new SubProgressMonitor(pm, 1), result);
			result.merge(Checks.validateModifiesFiles(ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits()), getValidationContext()));
			return result;
		} finally {
			pm.done();
		}
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor monitor) throws CoreException {
		return Checks.checkIfCuBroken(fType);
	}

	private RefactoringStatus checkTypeNameInPackage() throws JavaModelException {
		IType type= Checks.findTypeInPackage(fType.getPackageFragment(), fType.getElementName());
		if (type == null || !type.exists() || fType.equals(type)) {
			return null;
		}
		String message= Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_type_exists, new String[] { BasicElementLabels.getJavaElementName(fType.getElementName()), JavaElementLabels.getElementLabel(fType.getPackageFragment(), JavaElementLabels.ALL_DEFAULT)});
		return RefactoringStatus.createErrorStatus(message);
	}

	private Expression createAccessExpressionToEnclosingInstanceFieldText(ASTNode node, IBinding binding, AbstractTypeDeclaration declaration) {
		if (Modifier.isStatic(binding.getModifiers()))
			return node.getAST().newName(fType.getDeclaringType().getTypeQualifiedName('.'));
		else if ((isInAnonymousTypeInsideInputType(node, declaration) || isInLocalTypeInsideInputType(node, declaration) || isInNonStaticMemberTypeInsideInputType(node, declaration)))
			return createQualifiedReadAccessExpressionForEnclosingInstance(node.getAST());
		else
			return createReadAccessExpressionForEnclosingInstance(node.getAST());
	}

	@Override
	public Change createChange(final IProgressMonitor monitor) throws CoreException {
		monitor.beginTask(RefactoringCoreMessages.MoveInnerToTopRefactoring_creating_change, 1);
		final Map<String, String> arguments= new HashMap<String, String>();
		String project= null;
		IJavaProject javaProject= fType.getJavaProject();
		if (javaProject != null)
			project= javaProject.getElementName();
		final String description= Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_descriptor_description_short, BasicElementLabels.getJavaElementName(fType.getElementName()));
		final String header= Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_descriptor_description, new String[] { JavaElementLabels.getElementLabel(fType, JavaElementLabels.ALL_FULLY_QUALIFIED), JavaElementLabels.getElementLabel(fType.getParent(), JavaElementLabels.ALL_FULLY_QUALIFIED)});
		final JDTRefactoringDescriptorComment comment= new JDTRefactoringDescriptorComment(project, this, header);
		comment.addSetting(Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_original_pattern, JavaElementLabels.getElementLabel(fType, JavaElementLabels.ALL_FULLY_QUALIFIED)));
		final boolean enclosing= fEnclosingInstanceFieldName != null && !"".equals(fEnclosingInstanceFieldName); //$NON-NLS-1$
		if (enclosing)
			comment.addSetting(Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_field_pattern, BasicElementLabels.getJavaElementName(fEnclosingInstanceFieldName)));
		if (fNameForEnclosingInstanceConstructorParameter != null && !"".equals(fNameForEnclosingInstanceConstructorParameter)) //$NON-NLS-1$
			comment.addSetting(Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_parameter_pattern, BasicElementLabels.getJavaElementName(fNameForEnclosingInstanceConstructorParameter)));
		if (enclosing && fMarkInstanceFieldAsFinal)
			comment.addSetting(RefactoringCoreMessages.MoveInnerToTopRefactoring_declare_final);
		final ConvertMemberTypeDescriptor descriptor= RefactoringSignatureDescriptorFactory.createConvertMemberTypeDescriptor(project, description, comment.asString(), arguments, RefactoringDescriptor.MULTI_CHANGE | RefactoringDescriptor.STRUCTURAL_CHANGE | JavaRefactoringDescriptor.JAR_REFACTORING | JavaRefactoringDescriptor.JAR_SOURCE_ATTACHMENT);
		arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT, JavaRefactoringDescriptorUtil.elementToHandle(project, fType));
		if (enclosing)
			arguments.put(ATTRIBUTE_FIELD_NAME, fEnclosingInstanceFieldName);
		if (fNameForEnclosingInstanceConstructorParameter != null && !"".equals(fNameForEnclosingInstanceConstructorParameter)) //$NON-NLS-1$
			arguments.put(ATTRIBUTE_PARAMETER_NAME, fNameForEnclosingInstanceConstructorParameter);
		arguments.put(ATTRIBUTE_FIELD, Boolean.valueOf(fCreateInstanceField).toString());
		arguments.put(ATTRIBUTE_FINAL, Boolean.valueOf(fMarkInstanceFieldAsFinal).toString());
		arguments.put(ATTRIBUTE_POSSIBLE, Boolean.valueOf(fIsInstanceFieldCreationPossible).toString());
		arguments.put(ATTRIBUTE_MANDATORY, Boolean.valueOf(fIsInstanceFieldCreationMandatory).toString());
		final DynamicValidationRefactoringChange result= new DynamicValidationRefactoringChange(descriptor, RefactoringCoreMessages.MoveInnerToTopRefactoring_move_to_Top);
		result.addAll(fChangeManager.getAllChanges());
		result.add(createCompilationUnitForMovedType(new SubProgressMonitor(monitor, 1)));
		return result;
	}

	private TextChangeManager createChangeManager(final IProgressMonitor monitor, final RefactoringStatus status) throws CoreException {
		Assert.isNotNull(monitor);
		Assert.isNotNull(status);
		final TextChangeManager manager= new TextChangeManager();
		try {
			monitor.beginTask(RefactoringCoreMessages.MoveInnerToTopRefactoring_creating_preview, 4);
			final Map<ICompilationUnit, CompilationUnitRewrite> rewrites= new HashMap<ICompilationUnit, CompilationUnitRewrite>(2);
			fSourceRewrite.clearASTAndImportRewrites();
			rewrites.put(fSourceRewrite.getCu(), fSourceRewrite);
			final MemberVisibilityAdjustor adjustor= new MemberVisibilityAdjustor(fType.getPackageFragment(), fType);
			adjustor.setRewrites(rewrites);
			adjustor.setVisibilitySeverity(RefactoringStatus.WARNING);
			adjustor.setFailureSeverity(RefactoringStatus.WARNING);
			adjustor.setStatus(status);
			adjustor.adjustVisibility(new SubProgressMonitor(monitor, 1));
			final Map<String, ITypeBinding> parameters= new LinkedHashMap<String, ITypeBinding>();
			addTypeParameters(fSourceRewrite.getRoot(), fType, parameters);
			final ITypeBinding[] bindings= new ITypeBinding[parameters.values().size()];
			parameters.values().toArray(bindings);
			final Map<ICompilationUnit, SearchMatch[]> typeReferences= createTypeReferencesMapping(new SubProgressMonitor(monitor, 1), status);
			Map<ICompilationUnit, SearchMatch[]> constructorReferences= null;
			if (JdtFlags.isStatic(fType))
				constructorReferences= new HashMap<ICompilationUnit, SearchMatch[]>(0);
			else
				constructorReferences= createConstructorReferencesMapping(new SubProgressMonitor(monitor, 1), status);
			if (fCreateInstanceField) {
				// must increase visibility of all member types up
				// to the top level type to allow this
				IType type= fType;
				ModifierKeyword keyword= null;
				while ( (type= type.getDeclaringType()) != null) {
					if ((!adjustor.getAdjustments().containsKey(type)) && (Modifier.isPrivate(type.getFlags())))
						adjustor.getAdjustments().put(type, new OutgoingMemberVisibilityAdjustment(type, keyword, RefactoringStatus.createWarningStatus(Messages.format(RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_type_warning, new String[] { MemberVisibilityAdjustor.getLabel(type), MemberVisibilityAdjustor.getLabel(keyword) }), JavaStatusContext.create(type.getCompilationUnit(), type.getSourceRange()))));
				}
			}
			monitor.worked(1);
			ICompilationUnit inputCU= fType.getCompilationUnit();
			for (final Iterator<ICompilationUnit> iterator= getMergedSet(typeReferences.keySet(), constructorReferences.keySet()).iterator(); iterator.hasNext();) {
				final ICompilationUnit unit= iterator.next();
				final CompilationUnitRewrite targetRewrite= getCompilationUnitRewrite(unit);
				createCompilationUnitRewrite(bindings, targetRewrite, typeReferences, constructorReferences, adjustor.getAdjustments().containsKey(fType), inputCU, unit, false, status, monitor);
				if (unit.equals(inputCU)) {
					try {
						adjustor.setStatus(new RefactoringStatus());
						adjustor.rewriteVisibility(targetRewrite.getCu(), new SubProgressMonitor(monitor, 1));
					} finally {
						adjustor.setStatus(status);
					}
					fNewSourceOfInputType= createNewSource(targetRewrite, unit);
					targetRewrite.clearASTAndImportRewrites();
					createCompilationUnitRewrite(bindings, targetRewrite, typeReferences, constructorReferences, adjustor.getAdjustments().containsKey(fType), inputCU, unit, true, status, monitor);
				}
				adjustor.rewriteVisibility(targetRewrite.getCu(), new SubProgressMonitor(monitor, 1));
				manager.manage(unit, targetRewrite.createChange(true));
			}
			if (fNewSourceOfInputType == null) {
				fNewSourceOfInputType= createNewSource(fSourceRewrite, inputCU);
			}
		} finally {
			monitor.done();
		}
		return manager;
	}

	private Change createCompilationUnitForMovedType(IProgressMonitor pm) throws CoreException {
		ICompilationUnit newCuWC= null;
		try {
			newCuWC= fType.getPackageFragment().getCompilationUnit(JavaModelUtil.getRenamedCUName(fType.getCompilationUnit(), fType.getElementName())).getWorkingCopy(null);
			String source= createSourceForNewCu(newCuWC, pm);
			return new CreateCompilationUnitChange(fType.getPackageFragment().getCompilationUnit(JavaModelUtil.getRenamedCUName(fType.getCompilationUnit(), fType.getElementName())), source, null);
		} finally {
			if (newCuWC != null)
				newCuWC.discardWorkingCopy();
		}
	}

	private void createCompilationUnitRewrite(final ITypeBinding[] parameters, final CompilationUnitRewrite targetRewrite, final Map<ICompilationUnit, SearchMatch[]> typeReferences, final Map<ICompilationUnit, SearchMatch[]> constructorReferences, boolean visibilityWasAdjusted, final ICompilationUnit sourceUnit, final ICompilationUnit targetUnit, final boolean remove, final RefactoringStatus status, final IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(parameters);
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(typeReferences);
		Assert.isNotNull(constructorReferences);
		Assert.isNotNull(sourceUnit);
		Assert.isNotNull(targetUnit);
		final CompilationUnit root= targetRewrite.getRoot();
		final ASTRewrite rewrite= targetRewrite.getASTRewrite();
		if (targetUnit.equals(sourceUnit)) {
			final AbstractTypeDeclaration declaration= findTypeDeclaration(fType, root);
			final TextEditGroup qualifierGroup= fSourceRewrite.createGroupDescription(RefactoringCoreMessages.MoveInnerToTopRefactoring_change_qualifier);
			ITypeBinding binding= declaration.resolveBinding();
			if (!remove) {
				if (!JdtFlags.isStatic(fType) && fCreateInstanceField) {
					if (JavaElementUtil.getAllConstructors(fType).length == 0)
						createConstructor(declaration, rewrite);
					else
						modifyConstructors(declaration, rewrite);
					addInheritedTypeQualifications(declaration, targetRewrite, qualifierGroup);
					addEnclosingInstanceDeclaration(declaration, rewrite);
				}
				fTypeImports= new HashSet<ITypeBinding>();
				fStaticImports= new HashSet<IBinding>();
				ImportRewriteUtil.collectImports(fType.getJavaProject(), declaration, fTypeImports, fStaticImports, false);
				if (binding != null)
					fTypeImports.remove(binding);
			}
			addEnclosingInstanceTypeParameters(parameters, declaration, rewrite);
			modifyAccessToEnclosingInstance(targetRewrite, declaration, monitor);
			if (binding != null) {
				modifyInterfaceMemberModifiers(binding);
				final ITypeBinding declaring= binding.getDeclaringClass();
				if (declaring != null)
					declaration.accept(new TypeReferenceQualifier(binding, null));
			}
			final TextEditGroup groupMove= targetRewrite.createGroupDescription(RefactoringCoreMessages.MoveInnerToTopRefactoring_change_label);
			if (remove) {
				rewrite.remove(declaration, groupMove);
				targetRewrite.getImportRemover().registerRemovedNode(declaration);
			} else {
				// Bug 101017/96308: Rewrite the visibility of the element to be
				// moved and add a warning.

				// Note that this cannot be done in the MemberVisibilityAdjustor, as the private and
				// static flags must always be cleared when moving to new type.
				int newFlags= JdtFlags.clearFlag(Modifier.STATIC, declaration.getModifiers());

				if (!visibilityWasAdjusted) {
					if (Modifier.isPrivate(declaration.getModifiers()) || Modifier.isProtected(declaration.getModifiers())) {
						newFlags= JdtFlags.clearFlag(Modifier.PROTECTED | Modifier.PRIVATE, newFlags);
						final RefactoringStatusEntry entry= new RefactoringStatusEntry(RefactoringStatus.WARNING, Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_change_visibility_type_warning, new String[] { BindingLabelProvider.getBindingLabel(binding, JavaElementLabels.ALL_FULLY_QUALIFIED)}), JavaStatusContext.create(fSourceRewrite.getCu()));
						if (!containsStatusEntry(status, entry))
							status.addEntry(entry);
					}
				}

				ModifierRewrite.create(rewrite, declaration).setModifiers(newFlags, groupMove);
			}
		}
		ASTNode[] references= getReferenceNodesIn(root, typeReferences, targetUnit);
		for (int index= 0; index < references.length; index++)
			updateTypeReference(parameters, references[index], targetRewrite, targetUnit);
		references= getReferenceNodesIn(root, constructorReferences, targetUnit);
		for (int index= 0; index < references.length; index++)
			updateConstructorReference(parameters, references[index], targetRewrite, targetUnit);
	}

	private void createConstructor(final AbstractTypeDeclaration declaration, final ASTRewrite rewrite) throws CoreException {
		Assert.isNotNull(declaration);
		Assert.isNotNull(rewrite);
		final AST ast= declaration.getAST();
		final MethodDeclaration constructor= ast.newMethodDeclaration();
		constructor.setConstructor(true);
		constructor.setName(ast.newSimpleName(declaration.getName().getIdentifier()));
		final String comment= CodeGeneration.getMethodComment(fType.getCompilationUnit(), fType.getElementName(), fType.getElementName(), getNewConstructorParameterNames(), new String[0], null, null, StubUtility.getLineDelimiterUsed(fType.getJavaProject()));
		if (comment != null && comment.length() > 0) {
			final Javadoc doc= (Javadoc) rewrite.createStringPlaceholder(comment, ASTNode.JAVADOC);
			constructor.setJavadoc(doc);
		}
		if (fCreateInstanceField) {
			final SingleVariableDeclaration variable= ast.newSingleVariableDeclaration();
			final String name= getNameForEnclosingInstanceConstructorParameter();
			variable.setName(ast.newSimpleName(name));
			variable.setType(createEnclosingType(ast));
			constructor.parameters().add(variable);
			final Block body= ast.newBlock();
			final Assignment assignment= ast.newAssignment();
			if (fCodeGenerationSettings.useKeywordThis || fEnclosingInstanceFieldName.equals(fNameForEnclosingInstanceConstructorParameter)) {
				final FieldAccess access= ast.newFieldAccess();
				access.setExpression(ast.newThisExpression());
				access.setName(ast.newSimpleName(fEnclosingInstanceFieldName));
				assignment.setLeftHandSide(access);
			} else
				assignment.setLeftHandSide(ast.newSimpleName(fEnclosingInstanceFieldName));
			assignment.setRightHandSide(ast.newSimpleName(name));
			final Statement statement= ast.newExpressionStatement(assignment);
			body.statements().add(statement);
			constructor.setBody(body);
		} else
			constructor.setBody(ast.newBlock());
		rewrite.getListRewrite(declaration, declaration.getBodyDeclarationsProperty()).insertFirst(constructor, null);
	}

	// Map<ICompilationUnit, SearchMatch[]>
	private Map<ICompilationUnit, SearchMatch[]> createConstructorReferencesMapping(IProgressMonitor pm, RefactoringStatus status) throws JavaModelException {
		SearchResultGroup[] groups= ConstructorReferenceFinder.getConstructorReferences(fType, pm, status);
		Map<ICompilationUnit, SearchMatch[]> result= new HashMap<ICompilationUnit, SearchMatch[]>();
		for (int i= 0; i < groups.length; i++) {
			SearchResultGroup group= groups[i];
			ICompilationUnit cu= group.getCompilationUnit();
			if (cu == null)
				continue;
			result.put(cu, group.getSearchResults());
		}
		return result;
	}

	private Expression createEnclosingInstanceCreationString(final ASTNode node, final ICompilationUnit cu) throws JavaModelException {
		Assert.isTrue((node instanceof ClassInstanceCreation) || (node instanceof SuperConstructorInvocation));
		Assert.isNotNull(cu);
		Expression expression= null;
		if (node instanceof ClassInstanceCreation)
			expression= ((ClassInstanceCreation) node).getExpression();
		else
			expression= ((SuperConstructorInvocation) node).getExpression();
		final AST ast= node.getAST();
		if (expression != null)
			return expression;
		else if (JdtFlags.isStatic(fType))
			return null;
		else if (isInsideSubclassOfDeclaringType(node))
			return ast.newThisExpression();
		else if ((node.getStartPosition() >= fType.getSourceRange().getOffset() && ASTNodes.getExclusiveEnd(node) <= fType.getSourceRange().getOffset() + fType.getSourceRange().getLength())) {
			if (fCodeGenerationSettings.useKeywordThis || fEnclosingInstanceFieldName.equals(fNameForEnclosingInstanceConstructorParameter)) {
				final FieldAccess access= ast.newFieldAccess();
				access.setExpression(ast.newThisExpression());
				access.setName(ast.newSimpleName(fEnclosingInstanceFieldName));
				return access;
			} else
				return ast.newSimpleName(fEnclosingInstanceFieldName);
		} else if (isInsideTypeNestedInDeclaringType(node)) {
			final ThisExpression qualified= ast.newThisExpression();
			qualified.setQualifier(ast.newSimpleName(fType.getDeclaringType().getElementName()));
			return qualified;
		}
		return null;
	}

	private Type createEnclosingType(final AST ast) throws JavaModelException {
		Assert.isNotNull(ast);
		final ITypeParameter[] parameters= fType.getDeclaringType().getTypeParameters();
		final Type type= ASTNodeFactory.newType(ast, fType.getDeclaringType().getTypeQualifiedName('.'));
		if (parameters.length > 0) {
			final ParameterizedType parameterized= ast.newParameterizedType(type);
			for (int index= 0; index < parameters.length; index++)
				parameterized.typeArguments().add(ast.newSimpleType(ast.newSimpleName(parameters[index].getElementName())));
			return parameterized;
		}
		return type;
	}

	private String createNewSource(final CompilationUnitRewrite targetRewrite, final ICompilationUnit unit) throws CoreException, JavaModelException {
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(unit);
		TextChange change= targetRewrite.createChange(true);
		if (change == null)
			change= new CompilationUnitChange("", unit); //$NON-NLS-1$
		final String source= change.getPreviewContent(new NullProgressMonitor());
		final ASTParser parser= ASTParser.newParser(ASTProvider.SHARED_AST_LEVEL);
		parser.setProject(fType.getJavaProject());
		parser.setResolveBindings(false);
		parser.setSource(source.toCharArray());
		final AbstractTypeDeclaration declaration= findTypeDeclaration(fType, (CompilationUnit) parser.createAST(null));
		SourceRange sourceRange= new TargetSourceRangeComputer().computeSourceRange(declaration);
		return source.substring(sourceRange.getStartPosition(), sourceRange.getStartPosition() + sourceRange.getLength());
	}

	private Expression createQualifiedReadAccessExpressionForEnclosingInstance(AST ast) {
		ThisExpression expression= ast.newThisExpression();
		expression.setQualifier(ast.newName(new String[] { fType.getElementName()}));
		FieldAccess access= ast.newFieldAccess();
		access.setExpression(expression);
		access.setName(ast.newSimpleName(fEnclosingInstanceFieldName));
		return access;
	}

	private Expression createReadAccessExpressionForEnclosingInstance(AST ast) {
		if (fCodeGenerationSettings.useKeywordThis || fEnclosingInstanceFieldName.equals(fNameForEnclosingInstanceConstructorParameter)) {
			final FieldAccess access= ast.newFieldAccess();
			access.setExpression(ast.newThisExpression());
			access.setName(ast.newSimpleName(fEnclosingInstanceFieldName));
			return access;
		}
		return ast.newSimpleName(fEnclosingInstanceFieldName);
	}

	private String createSourceForNewCu(final ICompilationUnit unit, final IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(unit);
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask("", 2); //$NON-NLS-1$
			final String separator= StubUtility.getLineDelimiterUsed(fType.getJavaProject());
			final String block= getAlignedSourceBlock(unit, fNewSourceOfInputType);
			String fileComment= null;
			if (StubUtility.doAddComments(unit.getJavaProject()))
				fileComment= CodeGeneration.getFileComment(unit, separator);
			String content= CodeGeneration.getCompilationUnitContent(unit, fileComment, null, block, separator);
			if (content == null) {
				final StringBuffer buffer= new StringBuffer();
				if (!fType.getPackageFragment().isDefaultPackage()) {
					buffer.append("package ").append(fType.getPackageFragment().getElementName()).append(';'); //$NON-NLS-1$
				}
				buffer.append(separator).append(separator);
				buffer.append(block);
				content= buffer.toString();
			}
			unit.getBuffer().setContents(content);
			addImportsToTargetUnit(unit, new SubProgressMonitor(monitor, 1));
		} finally {
			monitor.done();
		}
		return unit.getSource();
	}

	private Map<ICompilationUnit, SearchMatch[]> createTypeReferencesMapping(IProgressMonitor pm, RefactoringStatus status) throws JavaModelException {
		final RefactoringSearchEngine2 engine= new RefactoringSearchEngine2(SearchPattern.createPattern(fType, IJavaSearchConstants.ALL_OCCURRENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE));
		engine.setFiltering(true, true);
		engine.setScope(RefactoringScopeFactory.create(fType));
		engine.setStatus(status);
		engine.searchPattern(new SubProgressMonitor(pm, 1));
		final SearchResultGroup[] groups= (SearchResultGroup[]) engine.getResults();
		Map<ICompilationUnit, SearchMatch[]> result= new HashMap<ICompilationUnit, SearchMatch[]>();
		for (int i= 0; i < groups.length; i++) {
			SearchResultGroup group= groups[i];
			ICompilationUnit cu= group.getCompilationUnit();
			if (cu == null)
				continue;
			result.put(cu, group.getSearchResults());
		}
		return result;
	}

	private String getAlignedSourceBlock(final ICompilationUnit unit, final String block) {
		Assert.isNotNull(block);
		final String[] lines= Strings.convertIntoLines(block);
		Strings.trimIndentation(lines, unit.getJavaProject(), false);
		return Strings.concatenate(lines, StubUtility.getLineDelimiterUsed(fType.getJavaProject()));
	}

	private CompilationUnitRewrite getCompilationUnitRewrite(final ICompilationUnit unit) {
		Assert.isNotNull(unit);
		if (unit.equals(fType.getCompilationUnit()))
			return fSourceRewrite;
		return new CompilationUnitRewrite(unit);
	}

	private MethodDeclaration[] getConstructorDeclarationNodes(final AbstractTypeDeclaration declaration) {
		if (declaration instanceof TypeDeclaration) {
			final MethodDeclaration[] declarations= ((TypeDeclaration) declaration).getMethods();
			final List<MethodDeclaration> result= new ArrayList<MethodDeclaration>(2);
			for (int index= 0; index < declarations.length; index++) {
				if (declarations[index].isConstructor())
					result.add(declarations[index]);
			}
			return result.toArray(new MethodDeclaration[result.size()]);
		}
		return new MethodDeclaration[] {};
	}

	public boolean getCreateInstanceField() {
		return fCreateInstanceField;
	}

	private int getEnclosingInstanceAccessModifiers() {
		if (fMarkInstanceFieldAsFinal)
			return Modifier.PRIVATE | Modifier.FINAL;
		else
			return Modifier.PRIVATE;
	}

	public String getEnclosingInstanceName() {
		return fEnclosingInstanceFieldName;
	}

	private String getInitialNameForEnclosingInstanceField() {
		IType enclosingType= fType.getDeclaringType();
		if (enclosingType == null)
			return ""; //$NON-NLS-1$
		String[] suggestedNames= StubUtility.getFieldNameSuggestions(fType.getDeclaringType(), getEnclosingInstanceAccessModifiers(), getFieldNames(fType));
		if (suggestedNames.length > 0)
			return suggestedNames[0];
		String name= enclosingType.getElementName();
		if (name.equals("")) //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		return Character.toLowerCase(name.charAt(0)) + name.substring(1);
	}

	public IType getInputType() {
		return fType;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.IRefactoring#getName()
	 */
	@Override
	public String getName() {
		return RefactoringCoreMessages.MoveInnerToTopRefactoring_name;
	}

	private String getNameForEnclosingInstanceConstructorParameter() throws JavaModelException {
		if (fNameForEnclosingInstanceConstructorParameter != null)
			return fNameForEnclosingInstanceConstructorParameter;

		String[] suggestedNames= StubUtility.getArgumentNameSuggestions(fType.getDeclaringType(), getParameterNamesOfAllConstructors(fType));
		if (suggestedNames.length > 0)
			fNameForEnclosingInstanceConstructorParameter= suggestedNames[0];
		else
			fNameForEnclosingInstanceConstructorParameter= fEnclosingInstanceFieldName;
		return fNameForEnclosingInstanceConstructorParameter;
	}

	private String[] getNewConstructorParameterNames() throws JavaModelException {
		if (!fCreateInstanceField)
			return new String[0];
		return new String[] { getNameForEnclosingInstanceConstructorParameter()};
	}

	private ASTNode getNewQualifiedNameNode(ITypeBinding[] parameters, Name name) {
		final AST ast= name.getAST();
		boolean raw= false;
		final ITypeBinding binding= name.resolveTypeBinding();
		if (binding != null && binding.isRawType())
			raw= true;
		if (parameters != null && parameters.length > 0 && !raw) {
			final ParameterizedType type= ast.newParameterizedType(ast.newSimpleType(ast.newName(fQualifiedTypeName)));
			for (int index= 0; index < parameters.length; index++)
				type.typeArguments().add(ast.newSimpleType(ast.newSimpleName(parameters[index].getName())));
			return type;
		}
		return ast.newName(fQualifiedTypeName);
	}

	private ASTNode getNewUnqualifiedTypeNode(ITypeBinding[] parameters, Name name) {
		final AST ast= name.getAST();
		boolean raw= false;
		final ITypeBinding binding= name.resolveTypeBinding();
		if (binding != null && binding.isRawType())
			raw= true;
		if (parameters != null && parameters.length > 0 && !raw) {
			final ParameterizedType type= ast.newParameterizedType(ast.newSimpleType(ast.newSimpleName(fType.getElementName())));
			for (int index= 0; index < parameters.length; index++)
				type.typeArguments().add(ast.newSimpleType(ast.newSimpleName(parameters[index].getName())));
			return type;
		}
		return ast.newSimpleType(ast.newSimpleName(fType.getElementName()));
	}

	private boolean insertExpressionAsParameter(ClassInstanceCreation cic, ASTRewrite rewrite, ICompilationUnit cu, TextEditGroup group) throws JavaModelException {
		final Expression expression= createEnclosingInstanceCreationString(cic, cu);
		if (expression == null)
			return false;
		rewrite.getListRewrite(cic, ClassInstanceCreation.ARGUMENTS_PROPERTY).insertFirst(expression, group);
		return true;
	}

	private boolean insertExpressionAsParameter(SuperConstructorInvocation sci, ASTRewrite rewrite, ICompilationUnit cu, TextEditGroup group) throws JavaModelException {
		final Expression expression= createEnclosingInstanceCreationString(sci, cu);
		if (expression == null)
			return false;
		rewrite.getListRewrite(sci, SuperConstructorInvocation.ARGUMENTS_PROPERTY).insertFirst(expression, group);
		return true;
	}

	public boolean isCreatingInstanceFieldMandatory() {
		return fIsInstanceFieldCreationMandatory;
	}

	public boolean isCreatingInstanceFieldPossible() {
		return fIsInstanceFieldCreationPossible;
	}

	private boolean isInAnonymousTypeInsideInputType(ASTNode node, AbstractTypeDeclaration declaration) {
		final AnonymousClassDeclaration anonymous= (AnonymousClassDeclaration) ASTNodes.getParent(node, AnonymousClassDeclaration.class);
		return anonymous != null && ASTNodes.isParent(anonymous, declaration);
	}

	private boolean isInLocalTypeInsideInputType(ASTNode node, AbstractTypeDeclaration declaration) {
		final TypeDeclarationStatement statement= (TypeDeclarationStatement) ASTNodes.getParent(node, TypeDeclarationStatement.class);
		return statement != null && ASTNodes.isParent(statement, declaration);
	}

	private boolean isInNonStaticMemberTypeInsideInputType(ASTNode node, AbstractTypeDeclaration declaration) {
		final AbstractTypeDeclaration nested= (AbstractTypeDeclaration) ASTNodes.getParent(node, AbstractTypeDeclaration.class);
		return nested != null && !declaration.equals(nested) && !Modifier.isStatic(nested.getFlags()) && ASTNodes.isParent(nested, declaration);
	}

	private boolean isInsideSubclassOfDeclaringType(ASTNode node) {
		Assert.isTrue((node instanceof ClassInstanceCreation) || (node instanceof SuperConstructorInvocation));
		final AbstractTypeDeclaration declaration= (AbstractTypeDeclaration) ASTNodes.getParent(node, AbstractTypeDeclaration.class);
		Assert.isNotNull(declaration);

		final AnonymousClassDeclaration anonymous= (AnonymousClassDeclaration) ASTNodes.getParent(node, AnonymousClassDeclaration.class);
		boolean isAnonymous= anonymous != null && ASTNodes.isParent(anonymous, declaration);
		if (isAnonymous)
			return anonymous != null && isSubclassBindingOfEnclosingType(anonymous.resolveBinding());
		return isSubclassBindingOfEnclosingType(declaration.resolveBinding());
	}

	private boolean isInsideTypeNestedInDeclaringType(ASTNode node) {
		Assert.isTrue((node instanceof ClassInstanceCreation) || (node instanceof SuperConstructorInvocation));
		final AbstractTypeDeclaration declaration= (AbstractTypeDeclaration) ASTNodes.getParent(node, AbstractTypeDeclaration.class);
		Assert.isNotNull(declaration);
		ITypeBinding enclosing= declaration.resolveBinding();
		while (enclosing != null) {
			if (isCorrespondingTypeBinding(enclosing, fType.getDeclaringType()))
				return true;
			enclosing= enclosing.getDeclaringClass();
		}
		return false;
	}

	private boolean isInstanceFieldCreationMandatory() {
		AbstractTypeDeclaration typeDeclaration= findTypeDeclaration(fType, fSourceRewrite.getRoot());
		ITypeBinding typeBinding= typeDeclaration.resolveBinding();
		if (typeBinding == null || Modifier.isStatic(typeBinding.getModifiers())) {
			return false;
		}
		final MemberAccessNodeCollector collector= new MemberAccessNodeCollector(typeBinding);
		typeDeclaration.accept(collector);
		return containsNonStatic(collector.getMethodInvocations()) || containsNonStatic(collector.getSimpleFieldNames());
	}

	public boolean isInstanceFieldMarkedFinal() {
		return fMarkInstanceFieldAsFinal;
	}

	private boolean isSubclassBindingOfEnclosingType(ITypeBinding binding) {
		while (binding != null) {
			if (isCorrespondingTypeBinding(binding, fType.getDeclaringType()))
				return true;
			binding= binding.getSuperclass();
		}
		return false;
	}

	/*
	 * This method qualifies accesses from within the moved type to the (now former) enclosed
	 * type of the moved type. Note that all visibility changes have already been scheduled
	 * in the visibility adjustor.
	 */
	private void modifyAccessToEnclosingInstance(final CompilationUnitRewrite targetRewrite, final AbstractTypeDeclaration declaration, final IProgressMonitor monitor) {
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(declaration);
		Assert.isNotNull(monitor);
		ITypeBinding typeBinding= declaration.resolveBinding();
		if (typeBinding == null) {
			return;
		}
		final MemberAccessNodeCollector collector= new MemberAccessNodeCollector(typeBinding);
		declaration.accept(collector);
		modifyAccessToMethodsFromEnclosingInstance(targetRewrite, collector.getMethodInvocations(), declaration);
		modifyAccessToFieldsFromEnclosingInstance(targetRewrite, collector.getSimpleFieldNames(), declaration);
	}

	private void modifyAccessToFieldsFromEnclosingInstance(CompilationUnitRewrite targetRewrite, SimpleName[] simpleNames, AbstractTypeDeclaration declaration) {
		IBinding binding= null;
		SimpleName simpleName= null;
		IVariableBinding variable= null;
		for (int index= 0; index < simpleNames.length; index++) {
			simpleName= simpleNames[index];
			binding= simpleName.resolveBinding();
			if (binding != null && binding instanceof IVariableBinding && !(simpleName.getParent() instanceof FieldAccess)) {
				variable= (IVariableBinding) binding;
				final FieldAccess access= simpleName.getAST().newFieldAccess();
				access.setExpression(createAccessExpressionToEnclosingInstanceFieldText(simpleName, variable, declaration));
				access.setName(simpleName.getAST().newSimpleName(simpleName.getIdentifier()));
				targetRewrite.getASTRewrite().replace(simpleName, access, null);
				targetRewrite.getImportRemover().registerRemovedNode(simpleName);
			}
		}
	}

	private void modifyAccessToMethodsFromEnclosingInstance(CompilationUnitRewrite targetRewrite, MethodInvocation[] methodInvocations, AbstractTypeDeclaration declaration) {
		IMethodBinding binding= null;
		MethodInvocation invocation= null;
		for (int index= 0; index < methodInvocations.length; index++) {
			invocation= methodInvocations[index];
			binding= invocation.resolveMethodBinding();
			if (binding != null) {
				final Expression target= invocation.getExpression();
				if (target == null) {
					final Expression expression= createAccessExpressionToEnclosingInstanceFieldText(invocation, binding, declaration);
					targetRewrite.getASTRewrite().set(invocation, MethodInvocation.EXPRESSION_PROPERTY, expression, null);
				} else {
					if (!(invocation.getExpression() instanceof ThisExpression) || !(((ThisExpression) invocation.getExpression()).getQualifier() != null))
						continue;
					targetRewrite.getASTRewrite().replace(target, createAccessExpressionToEnclosingInstanceFieldText(invocation, binding, declaration), null);
					targetRewrite.getImportRemover().registerRemovedNode(target);
				}
			}
		}
	}

	private void modifyConstructors(AbstractTypeDeclaration declaration, ASTRewrite rewrite) throws CoreException {
		final MethodDeclaration[] declarations= getConstructorDeclarationNodes(declaration);
		for (int index= 0; index < declarations.length; index++) {
			Assert.isTrue(declarations[index].isConstructor());
			addParameterToConstructor(rewrite, declarations[index]);
			setEnclosingInstanceFieldInConstructor(rewrite, declarations[index]);
		}
	}

	private void modifyInterfaceMemberModifiers(final ITypeBinding binding) {
		Assert.isNotNull(binding);
		ITypeBinding declaring= binding.getDeclaringClass();
		while (declaring != null && !declaring.isInterface()) {
			declaring= declaring.getDeclaringClass();
		}
		if (declaring != null) {
			final ASTNode node= ASTNodes.findDeclaration(binding, fSourceRewrite.getRoot());
			if (node instanceof AbstractTypeDeclaration) {
				ModifierRewrite.create(fSourceRewrite.getASTRewrite(), node).setVisibility(Modifier.PUBLIC, null);
			}
		}
	}

	public void setCreateInstanceField(boolean create) {
		Assert.isTrue(fIsInstanceFieldCreationPossible);
		Assert.isTrue(!fIsInstanceFieldCreationMandatory);
		fCreateInstanceField= create;
	}

	private void setEnclosingInstanceFieldInConstructor(ASTRewrite rewrite, MethodDeclaration decl) throws JavaModelException {
		final AST ast= decl.getAST();
		final Block body= decl.getBody();
		final List<Statement> statements= body.statements();
		if (statements.isEmpty()) {
			final Assignment assignment= ast.newAssignment();
			assignment.setLeftHandSide(createReadAccessExpressionForEnclosingInstance(ast));
			assignment.setRightHandSide(ast.newSimpleName(getNameForEnclosingInstanceConstructorParameter()));
			rewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY).insertFirst(ast.newExpressionStatement(assignment), null);
		} else {
			final Statement first= statements.get(0);
			if (first instanceof ConstructorInvocation) {
				rewrite.getListRewrite(first, ConstructorInvocation.ARGUMENTS_PROPERTY).insertFirst(ast.newSimpleName(fEnclosingInstanceFieldName), null);
			} else {
				int index= 0;
				if (first instanceof SuperConstructorInvocation)
					index++;
				final Assignment assignment= ast.newAssignment();
				assignment.setLeftHandSide(createReadAccessExpressionForEnclosingInstance(ast));
				assignment.setRightHandSide(ast.newSimpleName(getNameForEnclosingInstanceConstructorParameter()));
				rewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY).insertAt(ast.newExpressionStatement(assignment), index, null);
			}
		}
	}

	public void setEnclosingInstanceName(String name) {
		Assert.isNotNull(name);
		fEnclosingInstanceFieldName= name;
	}

	public void setMarkInstanceFieldAsFinal(boolean mark) {
		fMarkInstanceFieldAsFinal= mark;
	}

	private void updateConstructorReference(final ClassInstanceCreation creation, final CompilationUnitRewrite targetRewrite, final ICompilationUnit unit, TextEditGroup group) throws JavaModelException {
		Assert.isNotNull(creation);
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(unit);
		final ASTRewrite rewrite= targetRewrite.getASTRewrite();
		if (fCreateInstanceField)
			insertExpressionAsParameter(creation, rewrite, unit, group);
		final Expression expression= creation.getExpression();
		if (expression != null) {
			rewrite.remove(expression, null);
			targetRewrite.getImportRemover().registerRemovedNode(expression);
		}
	}

	private void updateConstructorReference(ITypeBinding[] parameters, ASTNode reference, CompilationUnitRewrite targetRewrite, ICompilationUnit cu) throws CoreException {
		final TextEditGroup group= targetRewrite.createGroupDescription(RefactoringCoreMessages.MoveInnerToTopRefactoring_update_constructor_reference);
		if (reference instanceof SuperConstructorInvocation)
			updateConstructorReference((SuperConstructorInvocation) reference, targetRewrite, cu, group);
		else if (reference instanceof ClassInstanceCreation)
			updateConstructorReference((ClassInstanceCreation) reference, targetRewrite, cu, group);
		else if (reference.getParent() instanceof ClassInstanceCreation)
			updateConstructorReference((ClassInstanceCreation) reference.getParent(), targetRewrite, cu, group);
		else if (reference.getParent() instanceof ParameterizedType && reference.getParent().getParent() instanceof ClassInstanceCreation)
			updateConstructorReference(parameters, (ParameterizedType) reference.getParent(), targetRewrite, cu, group);
	}

	private void updateConstructorReference(ITypeBinding[] parameters, ParameterizedType type, CompilationUnitRewrite targetRewrite, ICompilationUnit cu, TextEditGroup group) throws CoreException {
		final ListRewrite rewrite= targetRewrite.getASTRewrite().getListRewrite(type, ParameterizedType.TYPE_ARGUMENTS_PROPERTY);
		TypeParameter parameter= null;
		for (int index= type.typeArguments().size(); index < parameters.length; index++) {
			parameter= targetRewrite.getRoot().getAST().newTypeParameter();
			parameter.setName(targetRewrite.getRoot().getAST().newSimpleName(parameters[index].getName()));
			rewrite.insertLast(parameter, group);
		}
		if (type.getParent() instanceof ClassInstanceCreation)
			updateConstructorReference((ClassInstanceCreation) type.getParent(), targetRewrite, cu, group);
	}

	private void updateConstructorReference(final SuperConstructorInvocation invocation, final CompilationUnitRewrite targetRewrite, final ICompilationUnit unit, TextEditGroup group) throws CoreException {
		Assert.isNotNull(invocation);
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(unit);
		final ASTRewrite rewrite= targetRewrite.getASTRewrite();
		if (fCreateInstanceField)
			insertExpressionAsParameter(invocation, rewrite, unit, group);
		final Expression expression= invocation.getExpression();
		if (expression != null) {
			rewrite.remove(expression, null);
			targetRewrite.getImportRemover().registerRemovedNode(expression);
		}
	}

	private boolean updateNameReference(ITypeBinding[] parameters, Name name, CompilationUnitRewrite targetRewrite, TextEditGroup group) {
		if (ASTNodes.asString(name).equals(fType.getFullyQualifiedName('.'))) {
			targetRewrite.getASTRewrite().replace(name, getNewQualifiedNameNode(parameters, name), group);
			targetRewrite.getImportRemover().registerRemovedNode(name);
			return true;
		}
		targetRewrite.getASTRewrite().replace(name, getNewUnqualifiedTypeNode(parameters, name), group);
		targetRewrite.getImportRemover().registerRemovedNode(name);
		return true;
	}

	private boolean updateParameterizedTypeReference(ITypeBinding[] parameters, ParameterizedType type, CompilationUnitRewrite targetRewrite, TextEditGroup group) {
		if (!(type.getParent() instanceof ClassInstanceCreation)) {
			final ListRewrite rewrite= targetRewrite.getASTRewrite().getListRewrite(type, ParameterizedType.TYPE_ARGUMENTS_PROPERTY);
			final AST ast= targetRewrite.getRoot().getAST();
			Type simpleType= null;
			for (int index= type.typeArguments().size(); index < parameters.length; index++) {
				simpleType= ast.newSimpleType(ast.newSimpleName(parameters[index].getName()));
				rewrite.insertLast(simpleType, group);
			}
		}
		return true;
	}

	private boolean updateReference(ITypeBinding[] parameters, ASTNode node, CompilationUnitRewrite rewrite, TextEditGroup group) {
		if (node.getLocationInParent() == ParameterizedType.TYPE_PROPERTY) {
			updateParameterizedTypeReference(parameters, (ParameterizedType) node.getParent(), rewrite, group);
			return updateNameReference(new ITypeBinding[] {}, ((SimpleType) node).getName(), rewrite, group);
		} else if (node instanceof QualifiedName)
			return updateNameReference(parameters, (QualifiedName) node, rewrite, group);
		else if (node instanceof SimpleType)
			return updateNameReference(parameters, ((SimpleType) node).getName(), rewrite, group);
		else
			return false;
	}

	private void updateReferenceInImport(ImportDeclaration enclosingImport, ASTNode node, CompilationUnitRewrite rewrite) {
		final IBinding binding= enclosingImport.resolveBinding();
		if (binding instanceof ITypeBinding) {
			final ITypeBinding type= (ITypeBinding) binding;
			final ImportRewrite rewriter= rewrite.getImportRewrite();
			if (enclosingImport.isStatic()) {
				final String oldImport= ASTNodes.asString(node);
				final StringBuffer buffer= new StringBuffer(oldImport);
				final String typeName= fType.getDeclaringType().getElementName();
				final int index= buffer.indexOf(typeName);
				if (index >= 0) {
					buffer.delete(index, index + typeName.length() + 1);
					final String newImport= buffer.toString();
					if (enclosingImport.isOnDemand()) {
						rewriter.removeStaticImport(oldImport + ".*"); //$NON-NLS-1$
						rewriter.addStaticImport(newImport, "*", false); //$NON-NLS-1$
					} else {
						rewriter.removeStaticImport(oldImport);
						final int offset= newImport.lastIndexOf('.');
						if (offset >= 0 && offset < newImport.length() - 1) {
							rewriter.addStaticImport(newImport.substring(0, offset), newImport.substring(offset + 1), false);
						}
					}
				}
			} else
				rewriter.removeImport(type.getQualifiedName());
		}
	}

	private void updateTypeReference(ITypeBinding[] parameters, ASTNode node, CompilationUnitRewrite rewrite, ICompilationUnit cu) {
		ImportDeclaration enclosingImport= (ImportDeclaration) ASTNodes.getParent(node, ImportDeclaration.class);
		if (enclosingImport != null)
			updateReferenceInImport(enclosingImport, node, rewrite);
		else {
			final TextEditGroup group= rewrite.createGroupDescription(RefactoringCoreMessages.MoveInnerToTopRefactoring_update_type_reference);
			updateReference(parameters, node, rewrite, group);
			if (!fType.getPackageFragment().equals(cu.getParent())) {
				final String name= fType.getPackageFragment().getElementName() + '.' + fType.getElementName();
				rewrite.getImportRemover().registerAddedImport(name);
				rewrite.getImportRewrite().addImport(name);
			}
		}
	}

	private RefactoringStatus initialize(JavaRefactoringArguments arguments) {
		final String handle= arguments.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT);
		if (handle != null) {
			final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(arguments.getProject(), handle, false);
			if (element == null || !element.exists() || element.getElementType() != IJavaElement.TYPE)
				return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getName(), IJavaRefactorings.CONVERT_MEMBER_TYPE);
			else {
				fType= (IType) element;
				fCodeGenerationSettings= JavaPreferencesSettings.getCodeGenerationSettings(fType.getJavaProject());
				try {
					initialize();
				} catch (JavaModelException exception) {
					JavaPlugin.log(exception);
				}
			}
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT));
		final String fieldName= arguments.getAttribute(ATTRIBUTE_FIELD_NAME);
		if (fieldName != null && !"".equals(fieldName)) //$NON-NLS-1$
			fEnclosingInstanceFieldName= fieldName;
		final String parameterName= arguments.getAttribute(ATTRIBUTE_PARAMETER_NAME);
		if (parameterName != null && !"".equals(parameterName)) //$NON-NLS-1$
			fNameForEnclosingInstanceConstructorParameter= parameterName;
		final String createField= arguments.getAttribute(ATTRIBUTE_FIELD);
		if (createField != null) {
			fCreateInstanceField= Boolean.valueOf(createField).booleanValue();
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_FIELD));
		final String markFinal= arguments.getAttribute(ATTRIBUTE_FINAL);
		if (markFinal != null) {
			fMarkInstanceFieldAsFinal= Boolean.valueOf(markFinal).booleanValue();
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_FINAL));
		final String possible= arguments.getAttribute(ATTRIBUTE_POSSIBLE);
		if (possible != null) {
			fIsInstanceFieldCreationPossible= Boolean.valueOf(possible).booleanValue();
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_POSSIBLE));
		final String mandatory= arguments.getAttribute(ATTRIBUTE_MANDATORY);
		if (mandatory != null)
			fIsInstanceFieldCreationMandatory= Boolean.valueOf(mandatory).booleanValue();
		else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_MANDATORY));
		return new RefactoringStatus();
	}
}

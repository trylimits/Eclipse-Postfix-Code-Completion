/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Nikolay Metchev <nikolaymetchev@gmail.com> - [move method] super method invocation does not compile after refactoring - https://bugs.eclipse.org/356687
 *     Nikolay Metchev <nikolaymetchev@gmail.com> - [move method] Move method with static imported method calls introduces compiler error - https://bugs.eclipse.org/217753
 *     Nikolay Metchev <nikolaymetchev@gmail.com> - [move method] Annotation error in applying move-refactoring to inherited methods - https://bugs.eclipse.org/404471
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFile;

import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.RangeMarker;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.eclipse.text.edits.TextEditProcessor;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.MoveProcessor;
import org.eclipse.ltk.core.refactoring.participants.RefactoringParticipant;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.MethodRefParameter;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.MoveMethodDescriptor;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.GetterSetterUtil;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.CollectingSearchRequestor;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringDescriptorUtil;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.base.ReferencesInBinaryContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.delegates.DelegateMethodCreator;
import org.eclipse.jdt.internal.corext.refactoring.structure.MemberVisibilityAdjustor.IVisibilityAdjustment;
import org.eclipse.jdt.internal.corext.refactoring.structure.MemberVisibilityAdjustor.IncomingMemberVisibilityAdjustment;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IDelegateUpdating;
import org.eclipse.jdt.internal.corext.refactoring.util.JavadocUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.SearchUtils;
import org.eclipse.jdt.internal.corext.util.Strings;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.BindingLabelProvider;


/**
 * Refactoring processor to move instance methods.
 */
public final class MoveInstanceMethodProcessor extends MoveProcessor implements IDelegateUpdating {

	/**
	 * AST visitor to find references to parameters occurring in anonymous
	 * classes of a method body.
	 */
	public final class AnonymousClassReferenceFinder extends AstNodeFinder {

		/** The anonymous class nesting counter */
		protected int fAnonymousClass= 0;

		/** The declaring type of the method declaration */
		protected final ITypeBinding fDeclaringType;

		/**
		 * Creates a new anonymous class reference finder.
		 *
		 * @param declaration
		 *            the method declaration to search for references
		 */
		public AnonymousClassReferenceFinder(final MethodDeclaration declaration) {
			fDeclaringType= declaration.resolveBinding().getDeclaringClass();
		}

		@Override
		public final void endVisit(final AnonymousClassDeclaration node) {
			Assert.isNotNull(node);
			if (fAnonymousClass > 0)
				fAnonymousClass--;
			super.endVisit(node);
		}

		@Override
		public final boolean visit(final AnonymousClassDeclaration node) {
			Assert.isNotNull(node);
			fAnonymousClass++;
			return super.visit(node);
		}

		@Override
		public final boolean visit(final MethodInvocation node) {
			Assert.isNotNull(node);
			if (fAnonymousClass > 0) {
				final IMethodBinding binding= node.resolveMethodBinding();
				if (binding != null) {
					if (node.getExpression() == null && !Modifier.isStatic(binding.getModifiers()))
						fResult.add(node.getName());
				}
			}
			return true;
		}

		@Override
		public boolean visit(final SimpleName node) {
			Assert.isNotNull(node);
			if (fAnonymousClass > 0) {
				if (!(node.getParent() instanceof FieldAccess)) {
					final IBinding binding= node.resolveBinding();
					if (binding instanceof IVariableBinding) {
						final IVariableBinding variable= (IVariableBinding) binding;
						final ITypeBinding declaring= variable.getDeclaringClass();
						if (declaring != null && Bindings.equals(declaring, fDeclaringType))
							fResult.add(node);
					}
				}
			}
			return false;
		}
	}

	/**
	 * Partial implementation of an ast node finder.
	 */
	protected static class AstNodeFinder extends ASTVisitor {

		/** The found ast nodes */
		protected final Set<Expression> fResult= new HashSet<Expression>();

		/** The status of the find operation */
		protected final RefactoringStatus fStatus= new RefactoringStatus();

		/**
		 * Returns the result set.
		 *
		 * @return the result set
		 */
		public final Set<Expression> getResult() {
			return fResult;
		}

		/**
		 * Returns the status of the find operation.
		 *
		 * @return the status of the operation
		 */
		public final RefactoringStatus getStatus() {
			return fStatus;
		}
	}

	class DelegateInstanceMethodCreator extends DelegateMethodCreator {

		private Map<IMember, IncomingMemberVisibilityAdjustment> fAdjustments;

		private Map<ICompilationUnit, CompilationUnitRewrite> fRewrites;

		public DelegateInstanceMethodCreator(Map<IMember, IncomingMemberVisibilityAdjustment> adjustments, Map<ICompilationUnit, CompilationUnitRewrite> rewrites) {
			super();
			fAdjustments= adjustments;
			fRewrites= rewrites;
		}

		@Override
		protected ASTNode createBody(BodyDeclaration bd) throws JavaModelException {
			MethodDeclaration methodDeclaration= (MethodDeclaration) bd;
			final MethodInvocation invocation= getAst().newMethodInvocation();
			invocation.setName(getAst().newSimpleName(getNewElementName()));
			invocation.setExpression(createSimpleTargetAccessExpression(methodDeclaration));
			createArgumentList(methodDeclaration, invocation.arguments(), new VisibilityAdjustingArgumentFactory(getAst(), fRewrites, fAdjustments));
			final Block block= getAst().newBlock();
			block.statements().add(createMethodInvocation(methodDeclaration, invocation));
			if (!fSourceRewrite.getCu().equals(fTargetType.getCompilationUnit()))
				fSourceRewrite.getImportRemover().registerRemovedNode(methodDeclaration.getBody());
			return block;
		}

		@Override
		protected ASTNode createDocReference(final BodyDeclaration declaration) throws JavaModelException {
			return MoveInstanceMethodProcessor.this.createMethodReference((MethodDeclaration) declaration, getAst());
		}
	}

	/**
	 * AST visitor to find 'this' references to enclosing instances.
	 */
	public final class EnclosingInstanceReferenceFinder extends AstNodeFinder {

		/** The list of enclosing types */
		private final List<ITypeBinding> fEnclosingTypes= new ArrayList<ITypeBinding>(3);

		/**
		 * Creates a new enclosing instance reference finder.
		 *
		 * @param binding
		 *            the declaring type
		 */
		public EnclosingInstanceReferenceFinder(final ITypeBinding binding) {
			Assert.isNotNull(binding);
			ITypeBinding declaring= binding.getDeclaringClass();
			while (declaring != null) {
				fEnclosingTypes.add(declaring);
				declaring= declaring.getDeclaringClass();
			}
		}

		@Override
		public final boolean visit(final SimpleName node) {
			Assert.isNotNull(node);
			final IBinding binding= node.resolveBinding();
			ITypeBinding declaring= null;
			if (binding instanceof IVariableBinding) {
				final IVariableBinding variable= (IVariableBinding) binding;
				if (Flags.isStatic(variable.getModifiers()))
					return false;
				declaring= variable.getDeclaringClass();
			} else if (binding instanceof IMethodBinding) {
				final IMethodBinding method= (IMethodBinding) binding;
				if (Flags.isStatic(method.getModifiers()))
					return false;
				declaring= method.getDeclaringClass();
			}
			if (declaring != null) {
				ITypeBinding enclosing= null;
				for (final Iterator<ITypeBinding> iterator= fEnclosingTypes.iterator(); iterator.hasNext();) {
					enclosing= iterator.next();
					if (Bindings.equals(enclosing, declaring)) {
						fStatus.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_refers_enclosing_instances, JavaStatusContext.create(fMethod.getCompilationUnit(), node)));
						fResult.add(node);
						break;
					}
				}
			}
			return false;
		}

		@Override
		public final boolean visit(final ThisExpression node) {
			Assert.isNotNull(node);
			if (node.getQualifier() != null) {
				fStatus.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_refers_enclosing_instances, JavaStatusContext.create(fMethod.getCompilationUnit(), node)));
				fResult.add(node);
			}
			return false;
		}
	}

	/**
	 * AST visitor to find references to type variables or generic types.
	 */
	public final class GenericReferenceFinder extends AstNodeFinder {

		/** The type parameter binding keys */
		protected final Set<String> fBindings= new HashSet<String>();

		/**
		 * Creates a new generic reference finder.
		 *
		 * @param declaration
		 *            the method declaration
		 */
		public GenericReferenceFinder(final MethodDeclaration declaration) {
			Assert.isNotNull(declaration);
			ITypeBinding binding= null;
			TypeParameter parameter= null;
			for (final Iterator<TypeParameter> iterator= declaration.typeParameters().iterator(); iterator.hasNext();) {
				parameter= iterator.next();
				binding= parameter.resolveBinding();
				if (binding != null)
					fBindings.add(binding.getKey());
			}
		}

		/*
		 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.SimpleName)
		 */
		@Override
		public final boolean visit(final SimpleName node) {
			Assert.isNotNull(node);
			final IBinding binding= node.resolveBinding();
			if (binding instanceof ITypeBinding) {
				final ITypeBinding type= (ITypeBinding) binding;
				if (!fBindings.contains(type.getKey()) && type.isTypeVariable()) {
					fResult.add(node);
					fStatus.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_no_type_variables, JavaStatusContext.create(fMethod.getCompilationUnit(), node)));
					return false;
				}
			}
			return true;
		}
	}

	/**
	 * Factory for method argument declaration or expression nodes.
	 */
	protected static interface IArgumentFactory {

		/**
		 * Returns a argument node for the specified variable binding.
		 *
		 * @param binding
		 *            the binding to create a argument node for
		 * @param last
		 *            <code>true</code> if the argument represented by this
		 *            node is the last one in its declaring method
		 * @return the corresponding node
		 * @throws JavaModelException
		 *             if an error occurs
		 */
		public ASTNode getArgumentNode(IVariableBinding binding, boolean last) throws JavaModelException;

		/**
		 * Returns a target node for the current target.
		 *
		 * @return the corresponding node
		 * @throws JavaModelException
		 *             if an error occurs
		 */
		public ASTNode getTargetNode() throws JavaModelException;
	}

	/**
	 * AST visitor to rewrite the body of the moved method.
	 */
	public final class MethodBodyRewriter extends ASTVisitor {

		/** The anonymous class nesting counter */
		protected int fAnonymousClass= 0;

		/** The method declaration to rewrite */
		protected final MethodDeclaration fDeclaration;

		/** The source ast rewrite to use */
		protected final ASTRewrite fRewrite;

		/** The existing static imports */
		protected final Set<IBinding> fStaticImports= new HashSet<IBinding>();

		/** The refactoring status */
		protected final RefactoringStatus fStatus= new RefactoringStatus();

		/** The target compilation unit rewrite to use */
		protected final CompilationUnitRewrite fTargetRewrite;

		/**
		 * Creates a new method body rewriter.
		 *
		 * @param targetRewrite
		 *            the target compilation unit rewrite to use
		 * @param rewrite
		 *            the source ast rewrite to use
		 * @param sourceDeclaration
		 *            the source method declaration
		 */
		public MethodBodyRewriter(final CompilationUnitRewrite targetRewrite, final ASTRewrite rewrite, final MethodDeclaration sourceDeclaration) {
			Assert.isNotNull(targetRewrite);
			Assert.isNotNull(rewrite);
			Assert.isNotNull(sourceDeclaration);
			fTargetRewrite= targetRewrite;
			fRewrite= rewrite;
			fDeclaration= sourceDeclaration;
			fStaticImports.clear();
			ImportRewriteUtil.collectImports(fMethod.getJavaProject(), sourceDeclaration, new HashSet<ITypeBinding>(), fStaticImports, false);
		}

		private boolean isParameterName(String name) {
			List<SingleVariableDeclaration> parameters= fDeclaration.parameters();
			for (Iterator<SingleVariableDeclaration> iterator= parameters.iterator(); iterator.hasNext();) {
				SingleVariableDeclaration decl= iterator.next();
				if (name.equals(decl.getName().getIdentifier())) {
					return true;
				}
			}
			return false;

		}

		@Override
		public final void endVisit(final AnonymousClassDeclaration node) {
			Assert.isNotNull(node);
			if (fAnonymousClass > 0)
				fAnonymousClass--;
			super.endVisit(node);
		}

		@Override
		public final boolean visit(final AnonymousClassDeclaration node) {
			Assert.isNotNull(node);
			fAnonymousClass++;
			return super.visit(node);
		}

		@Override
		public final boolean visit(final ClassInstanceCreation node) {
			Assert.isNotNull(node);
			if (node.getParent() instanceof ClassInstanceCreation) {
				final AnonymousClassDeclaration declaration= node.getAnonymousClassDeclaration();
				if (declaration != null)
					visit(declaration);
				return false;
			}
			return super.visit(node);
		}

		private ASTNode getFieldReference(SimpleName oldNameNode, ASTRewrite rewrite) {
			String name= oldNameNode.getIdentifier();
			AST ast= rewrite.getAST();
			if (isParameterName(name) || StubUtility.useThisForFieldAccess(fTargetRewrite.getCu().getJavaProject())) {
				FieldAccess fieldAccess= ast.newFieldAccess();
				fieldAccess.setExpression(ast.newThisExpression());
				fieldAccess.setName((SimpleName) rewrite.createMoveTarget(oldNameNode));
				return fieldAccess;
			}
			return rewrite.createMoveTarget(oldNameNode);
		}

		@Override
		public final boolean visit(final FieldAccess node) {
			Assert.isNotNull(node);
			final Expression expression= node.getExpression();
			final IVariableBinding variable= node.resolveFieldBinding();
			final AST ast= fRewrite.getAST();
			if (expression instanceof ThisExpression) {
				if (Bindings.equals(fTarget, variable)) {
					if (fAnonymousClass > 0) {
						final ThisExpression target= ast.newThisExpression();
						target.setQualifier(ast.newSimpleName(fTargetType.getElementName()));
						fRewrite.replace(node, target, null);
					} else
						fRewrite.replace(node, ast.newThisExpression(), null);
					return false;
				} else {
					expression.accept(this);
					return false;
				}
			} else if (expression instanceof FieldAccess) {
				final FieldAccess access= (FieldAccess) expression;
				final IBinding binding= access.getName().resolveBinding();
				if (access.getExpression() instanceof ThisExpression && Bindings.equals(fTarget, binding)) {
					ASTNode newFieldAccess= getFieldReference(node.getName(), fRewrite);
					fRewrite.replace(node, newFieldAccess, null);
					return false;
				}
			} else if (expression != null) {
				expression.accept(this);
				return false;
			}
			return true;
		}

		public final void visit(final List<ASTNode> nodes) {
			Assert.isNotNull(nodes);
			ASTNode node= null;
			for (final Iterator<ASTNode> iterator= nodes.iterator(); iterator.hasNext();) {
				node= iterator.next();
				node.accept(this);
			}
		}

		@Override
		public final boolean visit(final MethodInvocation node) {
			Assert.isNotNull(node);
			final Expression expression= node.getExpression();
			final IMethodBinding method= node.resolveMethodBinding();
			if (method != null) {
				final ASTRewrite rewrite= fRewrite;
				if (expression == null) {
					final AST ast= node.getAST();
					if (!JdtFlags.isStatic(method))
						rewrite.set(node, MethodInvocation.EXPRESSION_PROPERTY, ast.newSimpleName(fTargetName), null);
					else if (!fStaticImports.contains(method)) {
						ITypeBinding declaring= method.getDeclaringClass();
						if (declaring != null) {
							IType type= (IType) declaring.getJavaElement();
							if (type != null) {
								rewrite.set(node, MethodInvocation.EXPRESSION_PROPERTY, ast.newName(type.getTypeQualifiedName('.')), null);
							}
						}
					}
					return true;
				} else {
					if (expression instanceof FieldAccess) {
						final FieldAccess access= (FieldAccess) expression;
						if (Bindings.equals(fTarget, access.resolveFieldBinding())) {
							rewrite.remove(expression, null);
							visit(node.arguments());
							return false;
						}
					} else if (expression instanceof Name) {
						final Name name= (Name) expression;
						if (Bindings.equals(fTarget, name.resolveBinding())) {
							rewrite.remove(expression, null);
							visit(node.arguments());
							return false;
						}
					}
				}
			}
			return true;
		}

		@Override
		public final boolean visit(final QualifiedName node) {
			Assert.isNotNull(node);
			IBinding binding= node.resolveBinding();
			if (binding instanceof ITypeBinding) {
				final ITypeBinding type= (ITypeBinding) binding;
				if (type.isClass() && type.getDeclaringClass() != null) {
					final Type newType= fTargetRewrite.getImportRewrite().addImport(type, node.getAST());
					fRewrite.replace(node, newType, null);
					return false;
				}
			}
			binding= node.getQualifier().resolveBinding();
			if (Bindings.equals(fTarget, binding)) {
				fRewrite.replace(node, getFieldReference(node.getName(), fRewrite), null);
				return false;
			}
			node.getQualifier().accept(this);
			return false;
		}

		@Override
		public final boolean visit(final SimpleName node) {
			Assert.isNotNull(node);
			final AST ast= node.getAST();
			final ASTRewrite rewrite= fRewrite;
			final IBinding binding= node.resolveBinding();
			if (binding instanceof ITypeBinding) {
				ITypeBinding type= (ITypeBinding) binding;
				String name= fTargetRewrite.getImportRewrite().addImport(type.getTypeDeclaration());
				if (name != null && name.indexOf('.') != -1) {
					fRewrite.replace(node, ASTNodeFactory.newName(ast, name), null);
					return false;
				}
			}
			if (Bindings.equals(fTarget, binding))
				if (fAnonymousClass > 0) {
					final ThisExpression target= ast.newThisExpression();
					target.setQualifier(ast.newSimpleName(fTargetType.getElementName()));
					fRewrite.replace(node, target, null);
				} else
					rewrite.replace(node, ast.newThisExpression(), null);
			else if (binding instanceof IVariableBinding) {
				final IVariableBinding variable= (IVariableBinding) binding;
				final IMethodBinding method= fDeclaration.resolveBinding();
				ITypeBinding declaring= variable.getDeclaringClass();
				if (method != null) {
					if (declaring != null && Bindings.isSuperType(declaring, method.getDeclaringClass(), false)) {
						declaring= declaring.getTypeDeclaration();
						if (JdtFlags.isStatic(variable))
							rewrite.replace(node, ast.newQualifiedName(ASTNodeFactory.newName(ast, fTargetRewrite.getImportRewrite().addImport(declaring)), ast.newSimpleName(node.getFullyQualifiedName())), null);
						else {
							final FieldAccess access= ast.newFieldAccess();
							access.setExpression(ast.newSimpleName(fTargetName));
							access.setName(ast.newSimpleName(node.getFullyQualifiedName()));
							rewrite.replace(node, access, null);
						}
					} else if (!(node.getParent() instanceof QualifiedName) && JdtFlags.isStatic(variable) && !fStaticImports.contains(variable) && !Checks.isEnumCase(node.getParent())) {
						rewrite.replace(node, ast.newQualifiedName(ASTNodeFactory.newName(ast, fTargetRewrite.getImportRewrite().addImport(declaring)), ast.newSimpleName(node.getFullyQualifiedName())), null);
					}
				}
			}
			return false;
		}

		@Override
		public final boolean visit(final ThisExpression node) {
			Assert.isNotNull(node);
			fRewrite.replace(node, node.getAST().newSimpleName(fTargetName), null);
			return false;
		}
	}

	/**
	 * AST visitor to find read-only fields of the declaring class of 'this'.
	 */
	public static class ReadyOnlyFieldFinder extends ASTVisitor {

		/**
		 * Returns the field binding associated with this expression.
		 *
		 * @param expression
		 *            the expression to get the field binding for
		 * @return the field binding, if the expression denotes a field access
		 *         or a field name, <code>null</code> otherwise
		 */
		protected static IVariableBinding getFieldBinding(final Expression expression) {
			Assert.isNotNull(expression);
			if (expression instanceof FieldAccess)
				return (IVariableBinding) ((FieldAccess) expression).getName().resolveBinding();
			if (expression instanceof Name) {
				final IBinding binding= ((Name) expression).resolveBinding();
				if (binding instanceof IVariableBinding) {
					final IVariableBinding variable= (IVariableBinding) binding;
					if (variable.isField())
						return variable;
				}
			}
			return null;
		}

		/**
		 * Is the specified name a qualified entity, e.g. preceded by 'this',
		 * 'super' or part of a method invocation?
		 *
		 * @param name
		 *            the name to check
		 * @return <code>true</code> if this entity is qualified,
		 *         <code>false</code> otherwise
		 */
		protected static boolean isQualifiedEntity(final Name name) {
			Assert.isNotNull(name);
			final ASTNode parent= name.getParent();
			if (parent instanceof QualifiedName && ((QualifiedName) parent).getName().equals(name) || parent instanceof FieldAccess && ((FieldAccess) parent).getName().equals(name) || parent instanceof SuperFieldAccess)
				return true;
			else if (parent instanceof MethodInvocation) {
				final MethodInvocation invocation= (MethodInvocation) parent;
				return invocation.getExpression() != null && invocation.getName().equals(name);
			}
			return false;
		}

		/** The list of found bindings */
		protected final List<IVariableBinding> fBindings= new LinkedList<IVariableBinding>();

		/** The keys of the found binding keys */
		protected final Set<String> fFound= new HashSet<String>();

		/** The keys of the written binding keys */
		protected final Set<String> fWritten= new HashSet<String>();

		/**
		 * Creates a new read only field finder.
		 *
		 * @param binding
		 *            The declaring class of the method declaring to find fields
		 *            for
		 */
		public ReadyOnlyFieldFinder(final ITypeBinding binding) {
			Assert.isNotNull(binding);
			final IVariableBinding[] bindings= binding.getDeclaredFields();
			IVariableBinding variable= null;
			for (int index= 0; index < bindings.length; index++) {
				variable= bindings[index];
				if (!variable.isSynthetic() && !fFound.contains(variable.getKey())) {
					fFound.add(variable.getKey());
					fBindings.add(variable);
				}
			}
		}

		/**
		 * Returns all fields of the declaring class plus the ones references in
		 * the visited method declaration.
		 *
		 * @return all fields of the declaring class plus the references ones
		 */
		public final IVariableBinding[] getDeclaredFields() {
			final IVariableBinding[] result= new IVariableBinding[fBindings.size()];
			fBindings.toArray(result);
			return result;
		}

		/**
		 * Returns all fields of the declaring class which are not written by
		 * the visited method declaration.
		 *
		 * @return all fields which are not written
		 */
		public final IVariableBinding[] getReadOnlyFields() {
			IVariableBinding binding= null;
			final List<IVariableBinding> list= new LinkedList<IVariableBinding>(fBindings);
			for (final Iterator<IVariableBinding> iterator= list.iterator(); iterator.hasNext();) {
				binding= iterator.next();
				if (fWritten.contains(binding.getKey()))
					iterator.remove();
			}
			final IVariableBinding[] result= new IVariableBinding[list.size()];
			list.toArray(result);
			return result;
		}

		@Override
		public final boolean visit(final Assignment node) {
			Assert.isNotNull(node);
			final IVariableBinding binding= getFieldBinding(node.getLeftHandSide());
			if (binding != null)
				fWritten.add(binding.getKey());
			return true;
		}

		@Override
		public final boolean visit(final FieldAccess node) {
			Assert.isNotNull(node);
			if (node.getExpression() instanceof ThisExpression) {
				final IVariableBinding binding= (IVariableBinding) node.getName().resolveBinding();
				if (binding != null) {
					final String key= binding.getKey();
					if (!fFound.contains(key)) {
						fFound.add(key);
						fBindings.add(binding);
					}
				}
			}
			return true;
		}

		@Override
		public final boolean visit(final PostfixExpression node) {
			final IVariableBinding binding= getFieldBinding(node.getOperand());
			if (binding != null)
				fWritten.add(binding.getKey());
			return true;
		}

		@Override
		public final boolean visit(final PrefixExpression node) {
			final IVariableBinding binding= getFieldBinding(node.getOperand());
			if (binding != null)
				fWritten.add(binding.getKey());
			return false;
		}

		@Override
		public final boolean visit(final SimpleName node) {
			Assert.isNotNull(node);
			final IBinding binding= node.resolveBinding();
			if (binding != null)
				if (isFieldAccess(node) && !isQualifiedEntity(node)) {
					final IVariableBinding variable= (IVariableBinding) binding;
					final String key= variable.getKey();
					if (!fFound.contains(key)) {
						fFound.add(key);
						fBindings.add(variable);
					}
				}
			return false;
		}
	}

	/**
	 * AST visitor to find recursive calls to the method.
	 */
	public final class RecursiveCallFinder extends AstNodeFinder {

		/** The method binding */
		protected final IMethodBinding fBinding;

		/**
		 * Creates a new recursive call finder.
		 *
		 * @param declaration
		 *            the method declaration
		 */
		public RecursiveCallFinder(final MethodDeclaration declaration) {
			Assert.isNotNull(declaration);
			fBinding= declaration.resolveBinding();
		}

		@Override
		public final boolean visit(final MethodInvocation node) {
			Assert.isNotNull(node);
			final Expression expression= node.getExpression();
			final IMethodBinding binding= node.resolveMethodBinding();
			if (binding == null || !Modifier.isStatic(binding.getModifiers()) && Bindings.equals(binding, fBinding) && (expression == null || expression instanceof ThisExpression)) {
				fStatus.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_potentially_recursive, JavaStatusContext.create(fMethod.getCompilationUnit(), node)));
				fResult.add(node);
				return false;
			}
			return true;
		}
	}

	/**
	 * AST visitor to find 'super' references.
	 */
	public final class SuperReferenceFinder extends AstNodeFinder {

		@Override
		public final boolean visit(final AnnotationTypeDeclaration node) {
			return false;
		}

		@Override
		public final boolean visit(final AnonymousClassDeclaration node) {
			return false;
		}

		@Override
		public final boolean visit(final EnumDeclaration node) {
			return false;
		}

		@Override
		public final boolean visit(final SuperFieldAccess node) {
			Assert.isNotNull(node);
			fStatus.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_uses_super, JavaStatusContext.create(fMethod.getCompilationUnit(), node)));
			fResult.add(node);
			return false;
		}

		@Override
		public final boolean visit(final SuperMethodInvocation node) {
			Assert.isNotNull(node);
			fStatus.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_uses_super, JavaStatusContext.create(fMethod.getCompilationUnit(), node)));
			fResult.add(node);
			return false;
		}

		@Override
		public final boolean visit(final TypeDeclaration node) {
			return false;
		}
	}

	/**
	 * AST visitor to find references to 'this'.
	 */
	public final class ThisReferenceFinder extends AstNodeFinder {

		@Override
		public final boolean visit(final MethodInvocation node) {
			Assert.isNotNull(node);
			final IMethodBinding binding= node.resolveMethodBinding();
			if (binding != null && !JdtFlags.isStatic(binding) && node.getExpression() == null) {
				fResult.add(node);
				fStatus.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_this_reference, JavaStatusContext.create(fMethod.getCompilationUnit(), node)));
			}
			return true;
		}

		@Override
		public final boolean visit(final SimpleName node) {
			Assert.isNotNull(node);
			if (isFieldAccess(node) && !isTargetAccess(node)) {
				fResult.add(node);
				fStatus.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_this_reference, JavaStatusContext.create(fMethod.getCompilationUnit(), node)));
			}
			return false;
		}

		@Override
		public final boolean visit(final ThisExpression node) {
			Assert.isNotNull(node);
			fResult.add(node);
			fStatus.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_this_reference, JavaStatusContext.create(fMethod.getCompilationUnit(), node)));
			return false;
		}
	}

	/**
	 * Argument factory which adjusts the visibilities of the argument types.
	 */
	public class VisibilityAdjustingArgumentFactory implements IArgumentFactory {

		/** The visibility adjustments */
		private final Map<IMember, IncomingMemberVisibilityAdjustment> fAdjustments;

		/** The ast to use for new nodes */
		private final AST fAst;

		/** The compilation unit rewrites */
		private final Map<ICompilationUnit, CompilationUnitRewrite> fRewrites;

		/**
		 * Creates a new visibility adjusting argument factory.
		 *
		 * @param ast
		 *            the ast to use for new nodes
		 * @param rewrites
		 *            the compilation unit rewrites
		 * @param adjustments
		 *            the map of elements to visibility adjustments
		 */
		public VisibilityAdjustingArgumentFactory(final AST ast, final Map<ICompilationUnit, CompilationUnitRewrite> rewrites, final Map<IMember, IncomingMemberVisibilityAdjustment> adjustments) {
			Assert.isNotNull(ast);
			Assert.isNotNull(rewrites);
			Assert.isNotNull(adjustments);
			fAst= ast;
			fRewrites= rewrites;
			fAdjustments= adjustments;
		}

		protected final void adjustTypeVisibility(final ITypeBinding binding) throws JavaModelException {
			Assert.isNotNull(binding);
			final IJavaElement element= binding.getJavaElement();
			if (element instanceof IType) {
				final IType type= (IType) element;
				if (!type.isBinary() && !type.isReadOnly() && !Flags.isPublic(type.getFlags())) {
					boolean same= false;
					final CompilationUnitRewrite rewrite= getCompilationUnitRewrite(fRewrites, type.getCompilationUnit());
					final AbstractTypeDeclaration declaration= ASTNodeSearchUtil.getAbstractTypeDeclarationNode(type, rewrite.getRoot());
					if (declaration != null) {
						final ITypeBinding declaring= declaration.resolveBinding();
						if (declaring != null && Bindings.equals(declaring.getPackage(), fTarget.getType().getPackage()))
							same= true;
						final Modifier.ModifierKeyword keyword= same ? null : Modifier.ModifierKeyword.PUBLIC_KEYWORD;
						final String modifier= same ? RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_default : RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_public;
						if (MemberVisibilityAdjustor.hasLowerVisibility(binding.getModifiers(), same ? Modifier.NONE : keyword == null ? Modifier.NONE : keyword.toFlagValue()) && MemberVisibilityAdjustor.needsVisibilityAdjustments(type, keyword, fAdjustments))
							fAdjustments.put(type, new MemberVisibilityAdjustor.OutgoingMemberVisibilityAdjustment(type, keyword, RefactoringStatus.createWarningStatus(Messages.format(RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_type_warning, new String[] { BindingLabelProvider.getBindingLabel(declaration.resolveBinding(), JavaElementLabels.ALL_FULLY_QUALIFIED), modifier }), JavaStatusContext.create(type.getCompilationUnit(), declaration))));
					}
				}
			}
		}

		public ASTNode getArgumentNode(final IVariableBinding binding, final boolean last) throws JavaModelException {
			Assert.isNotNull(binding);
			adjustTypeVisibility(binding.getType());
			return fAst.newSimpleName(binding.getName());
		}

		public ASTNode getTargetNode() throws JavaModelException {
			return fAst.newThisExpression();
		}
	}

	private static final String ATTRIBUTE_DEPRECATE= "deprecate"; //$NON-NLS-1$

	private static final String ATTRIBUTE_INLINE= "inline"; //$NON-NLS-1$

	private static final String ATTRIBUTE_REMOVE= "remove"; //$NON-NLS-1$

	private static final String ATTRIBUTE_TARGET_INDEX= "targetIndex"; //$NON-NLS-1$

	private static final String ATTRIBUTE_TARGET_NAME= "targetName"; //$NON-NLS-1$

	private static final String ATTRIBUTE_USE_GETTER= "getter"; //$NON-NLS-1$

	private static final String ATTRIBUTE_USE_SETTER= "setter"; //$NON-NLS-1$

	/** The identifier of this processor */
	public static final String IDENTIFIER= "org.eclipse.jdt.ui.moveInstanceMethodProcessor"; //$NON-NLS-1$

	/**
	 * Returns the bindings of the method arguments of the specified
	 * declaration.
	 *
	 * @param declaration
	 *            the method declaration
	 * @return the array of method argument variable bindings
	 */
	protected static IVariableBinding[] getArgumentBindings(final MethodDeclaration declaration) {
		Assert.isNotNull(declaration);
		final List<IVariableBinding> parameters= new ArrayList<IVariableBinding>(declaration.parameters().size());
		for (final Iterator<SingleVariableDeclaration> iterator= declaration.parameters().iterator(); iterator.hasNext();) {
			VariableDeclaration variable= iterator.next();
			IVariableBinding binding= variable.resolveBinding();
			if (binding == null)
				return new IVariableBinding[0];
			parameters.add(binding);
		}
		final IVariableBinding[] result= new IVariableBinding[parameters.size()];
		parameters.toArray(result);
		return result;
	}

	/**
	 * Returns the bindings of the method argument types of the specified
	 * declaration.
	 *
	 * @param declaration
	 *            the method declaration
	 * @return the array of method argument variable bindings
	 */
	protected static ITypeBinding[] getArgumentTypes(final MethodDeclaration declaration) {
		Assert.isNotNull(declaration);
		final IVariableBinding[] parameters= getArgumentBindings(declaration);
		final List<ITypeBinding> types= new ArrayList<ITypeBinding>(parameters.length);
		IVariableBinding binding= null;
		ITypeBinding type= null;
		for (int index= 0; index < parameters.length; index++) {
			binding= parameters[index];
			type= binding.getType();
			if (type != null)
				types.add(type);
		}
		final ITypeBinding[] result= new ITypeBinding[types.size()];
		types.toArray(result);
		return result;
	}

	/**
	 * Is the specified name a field access?
	 *
	 * @param name
	 *            the name to check
	 * @return <code>true</code> if this name is a field access,
	 *         <code>false</code> otherwise
	 */
	protected static boolean isFieldAccess(final SimpleName name) {
		Assert.isNotNull(name);
		final IBinding binding= name.resolveBinding();
		if (!(binding instanceof IVariableBinding))
			return false;
		final IVariableBinding variable= (IVariableBinding) binding;
		if (!variable.isField())
			return false;
		if ("length".equals(name.getIdentifier())) { //$NON-NLS-1$
			final ASTNode parent= name.getParent();
			if (parent instanceof QualifiedName) {
				final QualifiedName qualified= (QualifiedName) parent;
				final ITypeBinding type= qualified.getQualifier().resolveTypeBinding();
				if (type != null && type.isArray())
					return false;
			}
		}
		return !Modifier.isStatic(variable.getModifiers());
	}

	/** The candidate targets */
	private IVariableBinding[] fCandidateTargets= new IVariableBinding[0];

	/** The text change manager */
	private TextChangeManager fChangeManager= null;

	/** Should the delegator be deprecated? */
	private boolean fDelegateDeprecation= true;

	private boolean fDelegatingUpdating;

	/** Should the delegator be inlined? */
	private boolean fInline= false;

	/** The method to move */
	private IMethod fMethod;

	/** The name of the new method to generate */
	private String fMethodName;

	/** The possible targets */
	private IVariableBinding[] fPossibleTargets= new IVariableBinding[0];

	/** Should the delegator be removed after inlining? */
	private boolean fRemove= false;

	/** The code generation settings to apply */
	private CodeGenerationSettings fSettings;

	/** The source compilation unit rewrite */
	private CompilationUnitRewrite fSourceRewrite;

	/** The new target */
	private IVariableBinding fTarget= null;

	/** The name of the new target */
	private String fTargetName;

	/** Does the move method need a target node? */
	private boolean fTargetNode= true;

	/** The target type */
	private IType fTargetType= null;

	/** Should getter methods be used to resolve visibility issues? */
	private boolean fUseGetters= true;

	/** Should setter methods be used to resolve visibility issues? */
	private boolean fUseSetters= true;

	/**
	 * Creates a new move instance method processor.
	 *
	 * @param method
	 *            the method to move, or <code>null</code> if invoked by
	 *            scripting
	 * @param settings
	 *            the code generation settings to apply, or <code>null</code>
	 *            if invoked by scripting
	 */
	public MoveInstanceMethodProcessor(final IMethod method, final CodeGenerationSettings settings) {
		fSettings= settings;
		fMethod= method;
		if (method != null)
			initialize(method);
	}

	public MoveInstanceMethodProcessor(JavaRefactoringArguments arguments, RefactoringStatus status) {
		RefactoringStatus initializeStatus= initialize(arguments);
		status.merge(initializeStatus);
	}

	/**
	 * {@inheritDoc}
	 */
	public final boolean canEnableDelegateUpdating() {
		return true;
	}

	/**
	 * Checks whether a method with the proposed name already exists in the
	 * target type.
	 *
	 * @param monitor
	 *            the progress monitor to display progress
	 * @param status
	 *            the status of the condition checking
	 * @throws JavaModelException
	 *             if the declared methods of the target type could not be
	 *             retrieved
	 */
	protected void checkConflictingMethod(final IProgressMonitor monitor, final RefactoringStatus status) throws JavaModelException {
		Assert.isNotNull(monitor);
		Assert.isNotNull(status);
		final IMethod[] methods= fTargetType.getMethods();
		int newParamCount= fMethod.getParameterTypes().length;
		if (!fTarget.isField())
			newParamCount--; // moving to a parameter
		if (needsTargetNode())
			newParamCount++; // will add a parameter for the old 'this'
		try {
			monitor.beginTask("", methods.length); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_checking);
			IMethod method= null;
			for (int index= 0; index < methods.length; index++) {
				method= methods[index];
				if (method.getElementName().equals(fMethodName) && method.getParameterTypes().length == newParamCount)
					status.merge(RefactoringStatus.createErrorStatus(Messages.format(RefactoringCoreMessages.MoveInstanceMethodProcessor_method_already_exists, new String[] { BasicElementLabels.getJavaElementName(fMethodName), BasicElementLabels.getJavaElementName(fTargetType.getElementName()) }), JavaStatusContext.create(method)));
				monitor.worked(1);
			}
			if (fMethodName.equals(fTargetType.getElementName()))
				status.merge(RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.MoveInstanceMethodProcessor_method_type_clash, BasicElementLabels.getJavaElementName(fMethodName)), JavaStatusContext.create(fTargetType)));
		} finally {
			monitor.done();
		}
	}

	/**
	 * Checks whether the new target name conflicts with an already existing
	 * method parameter.
	 *
	 * @param monitor
	 *            the progress monitor to display progress
	 * @param status
	 *            the status of the condition checking
	 * @throws JavaModelException
	 *             if the method declaration of the method to move could not be
	 *             found
	 */
	protected void checkConflictingTarget(final IProgressMonitor monitor, final RefactoringStatus status) throws JavaModelException {
		Assert.isNotNull(monitor);
		Assert.isNotNull(status);
		final MethodDeclaration declaration= ASTNodeSearchUtil.getMethodDeclarationNode(fMethod, fSourceRewrite.getRoot());
		VariableDeclaration variable= null;
		final List<SingleVariableDeclaration> parameters= declaration.parameters();
		try {
			monitor.beginTask("", parameters.size()); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_checking);
			for (final Iterator<SingleVariableDeclaration> iterator= parameters.iterator(); iterator.hasNext();) {
				variable= iterator.next();
				if (fTargetName.equals(variable.getName().getIdentifier())) {
					status.merge(RefactoringStatus.createErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_target_name_already_used, JavaStatusContext.create(fMethod)));
					break;
				}
				monitor.worked(1);
			}
		} finally {
			monitor.done();
		}
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#checkFinalConditions(org.eclipse.core.runtime.IProgressMonitor,
	 *      org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext)
	 */
	@Override
	public final RefactoringStatus checkFinalConditions(final IProgressMonitor monitor, final CheckConditionsContext context) throws CoreException, OperationCanceledException {
		Assert.isNotNull(monitor);
		Assert.isNotNull(context);
		Assert.isNotNull(fTarget);
		final RefactoringStatus status= new RefactoringStatus();
		fChangeManager= new TextChangeManager();
		try {
			monitor.beginTask("", 4); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_checking);
			status.merge(Checks.checkIfCuBroken(fMethod));
			if (!status.hasError()) {
				checkGenericTarget(new SubProgressMonitor(monitor, 1), status);
				if (status.isOK()) {
					final IType type= getTargetType();
					if (type != null) {
						if (type.isBinary() || type.isReadOnly() || !fMethod.exists() || fMethod.isBinary() || fMethod.isReadOnly())
							status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_no_binary, JavaStatusContext.create(fMethod)));
						else {
							status.merge(Checks.checkIfCuBroken(type));
							if (!status.hasError()) {
								if (!type.exists() || type.isBinary() || type.isReadOnly())
									status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_no_binary, JavaStatusContext.create(fMethod)));
								checkConflictingTarget(new SubProgressMonitor(monitor, 1), status);
								checkConflictingMethod(new SubProgressMonitor(monitor, 1), status);

								Checks.addModifiedFilesToChecker(computeModifiedFiles(fMethod.getCompilationUnit(), type.getCompilationUnit()), context);

								monitor.worked(1);
								if (!status.hasFatalError())
									fChangeManager= createChangeManager(status, new SubProgressMonitor(monitor, 1));
							}
						}
					} else
						status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_no_resolved_target, JavaStatusContext.create(fMethod)));
				}
			}
		} finally {
			monitor.done();
		}
		return status;
	}

	/**
	 * Checks whether the target is a type variable or a generic type.
	 *
	 * @param monitor
	 *            the progress monitor to display progress
	 * @param status
	 *            the refactoring status
	 */
	protected void checkGenericTarget(final IProgressMonitor monitor, final RefactoringStatus status) {
		Assert.isNotNull(monitor);
		Assert.isNotNull(status);
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_checking);
			final ITypeBinding binding= fTarget.getType();
			if (binding == null || binding.isTypeVariable())
				status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_no_generic_targets, JavaStatusContext.create(fMethod)));
		} finally {
			monitor.done();
		}
	}

	/**
	 * Checks whether the method has references to type variables or generic
	 * types.
	 *
	 * @param monitor
	 *            the progress monitor to display progress
	 * @param declaration
	 *            the method declaration to check for generic types
	 * @param status
	 *            the status of the condition checking
	 */
	protected void checkGenericTypes(final IProgressMonitor monitor, final MethodDeclaration declaration, final RefactoringStatus status) {
		Assert.isNotNull(monitor);
		Assert.isNotNull(declaration);
		Assert.isNotNull(status);
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_checking);
			final AstNodeFinder finder= new GenericReferenceFinder(declaration);
			declaration.accept(finder);
			if (!finder.getStatus().isOK())
				status.merge(finder.getStatus());
		} finally {
			monitor.done();
		}
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#checkInitialConditions(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public final RefactoringStatus checkInitialConditions(final IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		Assert.isNotNull(monitor);
		final RefactoringStatus status= new RefactoringStatus();
		try {
			monitor.beginTask("", 4); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_checking);
			status.merge(Checks.checkIfCuBroken(fMethod));
			if (!status.hasError()) {
				checkMethodDeclaration(new SubProgressMonitor(monitor, 1), status);
				if (status.isOK()) {
					final MethodDeclaration declaration= ASTNodeSearchUtil.getMethodDeclarationNode(fMethod, fSourceRewrite.getRoot());
					checkGenericTypes(new SubProgressMonitor(monitor, 1), declaration, status);
					checkMethodBody(new SubProgressMonitor(monitor, 1), declaration, status);
					checkPossibleTargets(new SubProgressMonitor(monitor, 1), declaration, status);
				}
			}
		} finally {
			monitor.done();
		}
		return status;
	}

	/**
	 * Checks whether the instance method body is compatible with this
	 * refactoring.
	 *
	 * @param monitor
	 *            the progress monitor to display progress
	 * @param declaration
	 *            the method declaration whose body to check
	 * @param status
	 *            the status of the condition checking
	 */
	protected void checkMethodBody(final IProgressMonitor monitor, final MethodDeclaration declaration, final RefactoringStatus status) {
		Assert.isNotNull(monitor);
		Assert.isNotNull(declaration);
		Assert.isNotNull(status);
		try {
			monitor.beginTask("", 3); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_checking);
			AstNodeFinder finder= new SuperReferenceFinder();
			declaration.accept(finder);
			if (!finder.getStatus().isOK())
				status.merge(finder.getStatus());
			monitor.worked(1);
			finder= null;
			final IMethodBinding binding= declaration.resolveBinding();
			if (binding != null) {
				final ITypeBinding declaring= binding.getDeclaringClass();
				if (declaring != null)
					finder= new EnclosingInstanceReferenceFinder(declaring);
			}
			if (finder != null) {
				declaration.accept(finder);
				if (!finder.getStatus().isOK())
					status.merge(finder.getStatus());
				monitor.worked(1);
				finder= new RecursiveCallFinder(declaration);
				declaration.accept(finder);
				if (!finder.getStatus().isOK())
					status.merge(finder.getStatus());
				monitor.worked(1);
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Checks whether the instance method declaration is compatible with this
	 * refactoring.
	 *
	 * @param monitor
	 *            the progress monitor to display progress
	 * @param status
	 *            the status of the condition checking
	 * @throws JavaModelException
	 *             if the method does not exist
	 */
	protected void checkMethodDeclaration(final IProgressMonitor monitor, final RefactoringStatus status) throws JavaModelException {
		Assert.isNotNull(monitor);
		Assert.isNotNull(status);
		try {
			monitor.beginTask("", 5); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_checking);
			final int flags= fMethod.getFlags();
			if (Flags.isStatic(flags))
				status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_no_static_methods, JavaStatusContext.create(fMethod)));
			else if (Flags.isAbstract(flags))
				status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_single_implementation, JavaStatusContext.create(fMethod)));
			monitor.worked(1);
			if (Flags.isNative(flags))
				status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_no_native_methods, JavaStatusContext.create(fMethod)));
			monitor.worked(1);
			if (Flags.isSynchronized(flags))
				status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_no_synchronized_methods, JavaStatusContext.create(fMethod)));
			monitor.worked(1);
			if (fMethod.isConstructor())
				status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_no_constructors, JavaStatusContext.create(fMethod)));
			monitor.worked(1);
			if (fMethod.getDeclaringType().isAnnotation())
				status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_no_annotation, JavaStatusContext.create(fMethod)));
			else if (fMethod.getDeclaringType().isInterface() && !Flags.isDefaultMethod(flags))
				status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_no_interface, JavaStatusContext.create(fMethod)));
			monitor.worked(1);
		} finally {
			monitor.done();
		}
	}

	/**
	 * Checks whether the method has possible targets to be moved to
	 *
	 * @param monitor
	 *            the progress monitor to display progress
	 * @param declaration
	 *            the method declaration to check
	 * @param status
	 *            the status of the condition checking
	 */
	protected void checkPossibleTargets(final IProgressMonitor monitor, final MethodDeclaration declaration, final RefactoringStatus status) {
		Assert.isNotNull(monitor);
		Assert.isNotNull(declaration);
		Assert.isNotNull(status);
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_checking);
			if (computeTargetCategories(declaration).length < 1)
				status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.MoveInstanceMethodProcessor_cannot_be_moved, JavaStatusContext.create(fMethod)));
		} finally {
			monitor.done();
		}
	}

	/**
	 * Searches for references to the original method.
	 *
	 * @param monitor
	 *            the progress monitor to use
	 * @param status
	 *            the refactoring status to use
	 * @return the array of search result groups
	 * @throws CoreException
	 *             if an error occurred during search
	 */
	protected SearchResultGroup[] computeMethodReferences(final IProgressMonitor monitor, final RefactoringStatus status) throws CoreException {
		Assert.isNotNull(monitor);
		Assert.isNotNull(status);
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_checking);
			SearchPattern pattern= SearchPattern.createPattern(fMethod, IJavaSearchConstants.REFERENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE);
			IJavaSearchScope scope= RefactoringScopeFactory.create(fMethod, true, false);

			String binaryRefsDescription= Messages.format(RefactoringCoreMessages.ReferencesInBinaryContext_ref_in_binaries_description , BasicElementLabels.getJavaElementName(fMethod.getElementName()));
			ReferencesInBinaryContext binaryRefs= new ReferencesInBinaryContext(binaryRefsDescription);
			CollectingSearchRequestor requestor= new CollectingSearchRequestor(binaryRefs);
			SearchResultGroup[] result= RefactoringSearchEngine.search(pattern, scope, requestor, new SubProgressMonitor(monitor, 1), status);
			binaryRefs.addErrorIfNecessary(status);

			return result;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Computes the files that are being modified by this refactoring.
	 *
	 * @param source
	 *            the source compilation unit
	 * @param target
	 *            the target compilation unit
	 * @return the modified files
	 */
	protected IFile[] computeModifiedFiles(final ICompilationUnit source, final ICompilationUnit target) {
		Assert.isNotNull(source);
		Assert.isNotNull(target);
		if (source.equals(target))
			return ResourceUtil.getFiles(new ICompilationUnit[] { source });
		return ResourceUtil.getFiles(new ICompilationUnit[] { source, target });
	}

	/**
	 * Returns the reserved identifiers in the method to move.
	 *
	 * @return the reserved identifiers
	 * @throws JavaModelException
	 *             if the method declaration could not be found
	 */
	protected String[] computeReservedIdentifiers() throws JavaModelException {
		final List<String> names= new ArrayList<String>();
		final MethodDeclaration declaration= ASTNodeSearchUtil.getMethodDeclarationNode(fMethod, fSourceRewrite.getRoot());
		if (declaration != null) {
			final List<SingleVariableDeclaration> parameters= declaration.parameters();
			VariableDeclaration variable= null;
			for (int index= 0; index < parameters.size(); index++) {
				variable= parameters.get(index);
				names.add(variable.getName().getIdentifier());
			}
			final Block body= declaration.getBody();
			if (body != null) {
				final IBinding[] bindings= new ScopeAnalyzer(fSourceRewrite.getRoot()).getDeclarationsAfter(body.getStartPosition(), ScopeAnalyzer.VARIABLES);
				for (int index= 0; index < bindings.length; index++)
					names.add(bindings[index].getName());
			}
		}
		final String[] result= new String[names.size()];
		names.toArray(result);
		return result;
	}

	/**
	 * Computes the target categories for the method to move.
	 *
	 * @param declaration
	 *            the method declaration
	 * @return the possible targets as variable bindings of read-only fields and
	 *         parameters
	 */
	protected IVariableBinding[] computeTargetCategories(final MethodDeclaration declaration) {
		Assert.isNotNull(declaration);
		if (fPossibleTargets.length == 0 || fCandidateTargets.length == 0) {
			final List<IVariableBinding> possibleTargets= new ArrayList<IVariableBinding>(16);
			final List<IVariableBinding> candidateTargets= new ArrayList<IVariableBinding>(16);
			final IMethodBinding method= declaration.resolveBinding();
			if (method != null) {
				final ITypeBinding declaring= method.getDeclaringClass();
				IVariableBinding[] bindings= getArgumentBindings(declaration);
				ITypeBinding binding= null;
				for (int index= 0; index < bindings.length; index++) {
					binding= bindings[index].getType();
					if ((binding.isClass() || binding.isEnum() || is18OrHigherInterface(binding)) && binding.isFromSource()) {
						possibleTargets.add(bindings[index]);
						candidateTargets.add(bindings[index]);
					}
				}
				final ReadyOnlyFieldFinder visitor= new ReadyOnlyFieldFinder(declaring);
				declaration.accept(visitor);
				bindings= visitor.getReadOnlyFields();
				for (int index= 0; index < bindings.length; index++) {
					binding= bindings[index].getType();
					if ((binding.isClass() || is18OrHigherInterface(binding)) && binding.isFromSource())
						possibleTargets.add(bindings[index]);
				}
				bindings= visitor.getDeclaredFields();
				for (int index= 0; index < bindings.length; index++) {
					binding= bindings[index].getType();
					if ((binding.isClass() || is18OrHigherInterface(binding)) && binding.isFromSource())
						candidateTargets.add(bindings[index]);
				}
			}
			fPossibleTargets= new IVariableBinding[possibleTargets.size()];
			possibleTargets.toArray(fPossibleTargets);
			fCandidateTargets= new IVariableBinding[candidateTargets.size()];
			candidateTargets.toArray(fCandidateTargets);
		}
		return fPossibleTargets;
	}

	private static boolean is18OrHigherInterface(ITypeBinding binding) {
		if (!binding.isInterface() || binding.isAnnotation())
			return false;
		IJavaElement javaElement= binding.getJavaElement();
		return javaElement != null && JavaModelUtil.is18OrHigher(javaElement.getJavaProject());
	}

	/**
	 * Creates a visibility-adjusted target expression taking advantage of
	 * existing accessor methods.
	 *
	 * @param enclosingElement
	 *            the java element which encloses the current method access.
	 * @param expression
	 *            the expression to access the target, or <code>null</code>
	 * @param adjustments
	 *            the map of elements to visibility adjustments
	 * @param rewrite
	 *            the ast rewrite to use
	 * @return an adjusted target expression, or <code>null</code> if the
	 *         access did not have to be changed
	 * @throws JavaModelException
	 *             if an error occurs while accessing the target expression
	 */
	protected Expression createAdjustedTargetExpression(final IJavaElement enclosingElement, final Expression expression, final Map<IMember, IncomingMemberVisibilityAdjustment> adjustments, final ASTRewrite rewrite) throws JavaModelException {
		Assert.isNotNull(enclosingElement);
		Assert.isNotNull(adjustments);
		Assert.isNotNull(rewrite);
		final IJavaElement element= fTarget.getJavaElement();
		if (element != null && !Modifier.isPublic(fTarget.getModifiers())) {
			final IField field= (IField) fTarget.getJavaElement();
			if (field != null) {
				boolean same= field.getAncestor(IJavaElement.PACKAGE_FRAGMENT).equals(enclosingElement.getAncestor(IJavaElement.PACKAGE_FRAGMENT));
				final Modifier.ModifierKeyword keyword= same ? null : Modifier.ModifierKeyword.PUBLIC_KEYWORD;
				final String modifier= same ? RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_default : RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_public;
				if (fUseGetters) {
					final IMethod getter= GetterSetterUtil.getGetter(field);
					if (getter != null) {
						final MethodDeclaration method= ASTNodeSearchUtil.getMethodDeclarationNode(getter, fSourceRewrite.getRoot());
						if (method != null) {
							final IMethodBinding binding= method.resolveBinding();
							if (binding != null && MemberVisibilityAdjustor.hasLowerVisibility(getter.getFlags(), same ? Modifier.NONE : keyword == null ? Modifier.NONE : keyword.toFlagValue()) && MemberVisibilityAdjustor.needsVisibilityAdjustments(getter, keyword, adjustments))
								adjustments.put(getter, new MemberVisibilityAdjustor.OutgoingMemberVisibilityAdjustment(getter, keyword, RefactoringStatus.createWarningStatus(Messages.format(RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_method_warning, new String[] { BindingLabelProvider.getBindingLabel(binding, JavaElementLabels.ALL_FULLY_QUALIFIED), modifier }), JavaStatusContext.create(getter))));
							final MethodInvocation invocation= rewrite.getAST().newMethodInvocation();
							invocation.setExpression(expression);
							invocation.setName(rewrite.getAST().newSimpleName(getter.getElementName()));
							return invocation;
						}
					}
				}
				if (MemberVisibilityAdjustor.hasLowerVisibility(field.getFlags(), (keyword == null ? Modifier.NONE : keyword.toFlagValue())) && MemberVisibilityAdjustor.needsVisibilityAdjustments(field, keyword, adjustments))
					adjustments.put(field, new MemberVisibilityAdjustor.OutgoingMemberVisibilityAdjustment(field, keyword, RefactoringStatus.createWarningStatus(Messages.format(RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_field_warning, new String[] { BindingLabelProvider.getBindingLabel(fTarget, JavaElementLabels.ALL_FULLY_QUALIFIED), modifier }), JavaStatusContext.create(field))));
			}
		}
		return null;
	}

	/**
	 * Creates a generic argument list of the refactored moved method
	 *
	 * @param declaration
	 *            the method declaration of the method to move
	 * @param arguments
	 *            the argument list to create
	 * @param factory
	 *            the argument factory to use
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	protected void createArgumentList(MethodDeclaration declaration, List<ASTNode> arguments, IArgumentFactory factory) throws JavaModelException {
		Assert.isNotNull(declaration);
		Assert.isNotNull(arguments);
		Assert.isNotNull(factory);
		List<SingleVariableDeclaration> parameters= declaration.parameters();
		int size= parameters.size();
		for (int i= 0; i < size; i++) {
			IVariableBinding binding= parameters.get(i).resolveBinding();
			if (binding != null && Bindings.equals(binding, fTarget)) {
				if (needsTargetNode()) {
					// replace move target parameter with new target
					arguments.add(factory.getTargetNode());
				} else {
					// drop unused move target parameter 
				}
			} else {
				arguments.add(factory.getArgumentNode(binding, i == size - 1));
			}
		}
		if (needsTargetNode() && fTarget.isField()) {
			// prepend new target when moving to a field
			arguments.add(0, factory.getTargetNode());
		}
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#createChange(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public final Change createChange(final IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask("", 6); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_creating);
			final TextChange[] changes= fChangeManager.getAllChanges();
			if (changes.length == 1)
				return changes[0];
			final List<TextChange> list= new ArrayList<TextChange>(changes.length);
			list.addAll(Arrays.asList(changes));
			final Map<String, String> arguments= new HashMap<String, String>();
			String project= null;
			final IJavaProject javaProject= fMethod.getJavaProject();
			if (javaProject != null)
				project= javaProject.getElementName();
			int flags= JavaRefactoringDescriptor.JAR_REFACTORING | JavaRefactoringDescriptor.JAR_SOURCE_ATTACHMENT | RefactoringDescriptor.STRUCTURAL_CHANGE | RefactoringDescriptor.MULTI_CHANGE;
			final IType declaring= fMethod.getDeclaringType();
			try {
				if (declaring.isAnonymous() || declaring.isLocal())
					flags|= JavaRefactoringDescriptor.JAR_SOURCE_ATTACHMENT;
			} catch (JavaModelException exception) {
				JavaPlugin.log(exception);
			}
			final String description= Messages.format(RefactoringCoreMessages.MoveInstanceMethodProcessor_descriptor_description_short, BasicElementLabels.getJavaElementName(fMethod.getElementName()));
			final String header= Messages.format(RefactoringCoreMessages.MoveInstanceMethodProcessor_descriptor_description, new String[] { JavaElementLabels.getElementLabel(fMethod, JavaElementLabels.ALL_FULLY_QUALIFIED), BindingLabelProvider.getBindingLabel(fTarget, JavaElementLabels.ALL_FULLY_QUALIFIED) });
			final JDTRefactoringDescriptorComment comment= new JDTRefactoringDescriptorComment(project, this, header);
			comment.addSetting(Messages.format(RefactoringCoreMessages.MoveInstanceMethodProcessor_moved_element_pattern, RefactoringCoreMessages.JavaRefactoringDescriptor_not_available));
			comment.addSetting(Messages.format(RefactoringCoreMessages.MoveInstanceMethodProcessor_target_element_pattern, BindingLabelProvider.getBindingLabel(fTarget, JavaElementLabels.ALL_FULLY_QUALIFIED)));
			comment.addSetting(Messages.format(RefactoringCoreMessages.MoveInstanceMethodProcessor_method_name_pattern, BasicElementLabels.getJavaElementName(getMethodName())));
			if (needsTargetNode())
				comment.addSetting(Messages.format(RefactoringCoreMessages.MoveInstanceMethodProcessor_parameter_name_pattern, BasicElementLabels.getJavaElementName(getTargetName())));
			final MoveMethodDescriptor descriptor= RefactoringSignatureDescriptorFactory.createMoveMethodDescriptor(project, description, comment.asString(), arguments, flags);
			arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT, JavaRefactoringDescriptorUtil.elementToHandle(project, fMethod));
			arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME, fMethodName);
			arguments.put(ATTRIBUTE_TARGET_NAME, fTargetName);
			arguments.put(ATTRIBUTE_DEPRECATE, Boolean.valueOf(fDelegateDeprecation).toString());
			arguments.put(ATTRIBUTE_REMOVE, Boolean.valueOf(fRemove).toString());
			arguments.put(ATTRIBUTE_INLINE, Boolean.valueOf(fInline).toString());
			arguments.put(ATTRIBUTE_USE_GETTER, Boolean.valueOf(fUseGetters).toString());
			arguments.put(ATTRIBUTE_USE_SETTER, Boolean.valueOf(fUseSetters).toString());
			arguments.put(ATTRIBUTE_TARGET_INDEX, new Integer(getTargetIndex()).toString());
			return new DynamicValidationRefactoringChange(descriptor, RefactoringCoreMessages.MoveInstanceMethodRefactoring_name, list.toArray(new Change[list.size()]));
		} finally {
			monitor.done();
		}
	}

	/**
	 * Creates the text change manager for this processor.
	 *
	 * @param status
	 *            the refactoring status
	 * @param monitor
	 *            the progress monitor to display progress
	 * @return the created text change manager
	 * @throws JavaModelException
	 *             if the method declaration could not be found
	 * @throws CoreException
	 *             if the changes could not be generated
	 */
	protected TextChangeManager createChangeManager(final RefactoringStatus status, final IProgressMonitor monitor) throws JavaModelException, CoreException {
		Assert.isNotNull(status);
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask("", 7); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_creating);
			fSourceRewrite.clearASTAndImportRewrites();
			final TextChangeManager manager= new TextChangeManager();
			final CompilationUnitRewrite targetRewrite= fMethod.getCompilationUnit().equals(getTargetType().getCompilationUnit()) ? fSourceRewrite : new CompilationUnitRewrite(getTargetType().getCompilationUnit());
			final MethodDeclaration declaration= ASTNodeSearchUtil.getMethodDeclarationNode(fMethod, fSourceRewrite.getRoot());
			final SearchResultGroup[] references= computeMethodReferences(new SubProgressMonitor(monitor, 1), status);
			final Map<ICompilationUnit, CompilationUnitRewrite> rewrites= new HashMap<ICompilationUnit, CompilationUnitRewrite>(2);
			rewrites.put(fSourceRewrite.getCu(), fSourceRewrite);
			if (!fSourceRewrite.getCu().equals(targetRewrite.getCu()))
				rewrites.put(targetRewrite.getCu(), targetRewrite);
			final ASTRewrite sourceRewrite= ASTRewrite.create(fSourceRewrite.getRoot().getAST());
			final MemberVisibilityAdjustor adjustor= new MemberVisibilityAdjustor(fTargetType, fMethod);
			adjustor.setStatus(status);
			adjustor.setVisibilitySeverity(RefactoringStatus.WARNING);
			adjustor.setFailureSeverity(RefactoringStatus.WARNING);
			adjustor.setRewrites(rewrites);
			adjustor.setRewrite(sourceRewrite, fSourceRewrite.getRoot());
			adjustor.adjustVisibility(new SubProgressMonitor(monitor, 1));
			final IDocument document= new Document(fMethod.getCompilationUnit().getBuffer().getContents());
			createMethodCopy(document, declaration, sourceRewrite, rewrites, adjustor.getAdjustments(), status, new SubProgressMonitor(monitor, 1));
			createMethodJavadocReferences(rewrites, declaration, references, status, new SubProgressMonitor(monitor, 1));
			if (!fSourceRewrite.getCu().equals(targetRewrite.getCu()))
				createMethodImports(targetRewrite, declaration, new SubProgressMonitor(monitor, 1), status);
			boolean removable= false;
			if (fInline) {
				String binaryRefsDescription= Messages.format(RefactoringCoreMessages.ReferencesInBinaryContext_ref_in_binaries_description , BasicElementLabels.getJavaElementName(getMethod().getElementName()));
				ReferencesInBinaryContext binaryRefs= new ReferencesInBinaryContext(binaryRefsDescription);
				removable= createMethodDelegator(rewrites, declaration, references, adjustor.getAdjustments(), binaryRefs, status, new SubProgressMonitor(monitor, 1));
				binaryRefs.addErrorIfNecessary(status);
				if (fRemove && removable) {
					fSourceRewrite.getASTRewrite().remove(declaration, fSourceRewrite.createGroupDescription(RefactoringCoreMessages.MoveInstanceMethodProcessor_remove_original_method));
					if (!fSourceRewrite.getCu().equals(fTargetType.getCompilationUnit()))
						fSourceRewrite.getImportRemover().registerRemovedNode(declaration);
				}
			}
			if (!fRemove || !removable)
				createMethodDelegation(declaration, rewrites, adjustor.getAdjustments(), status, new SubProgressMonitor(monitor, 1));

			// Do not adjust visibility of a target field; references to the
			// field will be removed anyway.
			final IJavaElement targetElement= fTarget.getJavaElement();
			if (targetElement != null && targetElement instanceof IField && (Flags.isPrivate(fMethod.getFlags()) || !fInline)) {
				final IVisibilityAdjustment adjustmentForTarget= adjustor.getAdjustments().get(targetElement);
				if (adjustmentForTarget != null)
					adjustor.getAdjustments().remove(targetElement);
			}

			adjustor.rewriteVisibility(new SubProgressMonitor(monitor, 1));
			sourceRewrite.rewriteAST(document, fMethod.getJavaProject().getOptions(true));
			createMethodSignature(document, declaration, sourceRewrite, rewrites);
			ICompilationUnit unit= null;
			CompilationUnitRewrite rewrite= null;
			for (final Iterator<ICompilationUnit> iterator= rewrites.keySet().iterator(); iterator.hasNext();) {
				unit= iterator.next();
				rewrite= rewrites.get(unit);
				manager.manage(unit, rewrite.createChange(true));
			}
			return manager;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Creates the necessary change to inline a method invocation represented by
	 * a search match.
	 *
	 * @param rewriter
	 *            the current compilation unit rewrite
	 * @param declaration
	 *            the source method declaration
	 * @param match
	 *            the search match representing the method invocation
	 * @param adjustments
	 *            the map of elements to visibility adjustments
	 * @param status
	 *            the refactoring status
	 * @return <code>true</code> if the inline change could be performed,
	 *         <code>false</code> otherwise
	 * @throws JavaModelException
	 *             if a problem occurred while creating the inlined target
	 *             expression for field targets
	 */
	protected boolean createInlinedMethodInvocation(CompilationUnitRewrite rewriter, MethodDeclaration declaration, SearchMatch match,
			Map<IMember, IncomingMemberVisibilityAdjustment> adjustments, RefactoringStatus status) throws JavaModelException {
		Assert.isNotNull(rewriter);
		Assert.isNotNull(declaration);
		Assert.isNotNull(match);
		Assert.isNotNull(adjustments);
		Assert.isNotNull(status);
		boolean result= true;
		final ASTRewrite rewrite= rewriter.getASTRewrite();
		final ASTNode node= ASTNodeSearchUtil.findNode(match, rewriter.getRoot());
		final TextEditGroup group= rewriter.createGroupDescription(RefactoringCoreMessages.MoveInstanceMethodProcessor_inline_method_invocation);
		if (node instanceof SuperMethodInvocation) {
			SuperMethodInvocation invocation= (SuperMethodInvocation) node;
			MethodInvocation newMethodInvocation= rewrite.getAST().newMethodInvocation();
			newMethodInvocation.setName(rewrite.getAST().newSimpleName(fMethodName));
			if (fTarget.isField()) {
				newMethodInvocation.setStructuralProperty(MethodInvocation.EXPRESSION_PROPERTY, rewrite.getAST().newSimpleName(fTarget.getName()));
				if (needsTargetNode()) {
					newMethodInvocation.arguments().add(rewrite.getAST().newThisExpression());
				}
				for (ASTNode astNode : (List<ASTNode>) invocation.arguments()) {
					newMethodInvocation.arguments().add(rewrite.createCopyTarget(astNode));
				}
			} else {
				final IVariableBinding[] bindings= getArgumentBindings(declaration);
				List<ASTNode> arguments= invocation.arguments();
				for (int i= 0; i < arguments.size(); i++) {
					ASTNode arg= arguments.get(i);
					if (bindings.length > i && Bindings.equals(bindings[i], fTarget)) {
						if (arg.getNodeType() == ASTNode.NULL_LITERAL) {
							status.merge(RefactoringStatus.createErrorStatus(
									Messages.format(RefactoringCoreMessages.MoveInstanceMethodProcessor_no_null_argument,
											BindingLabelProvider.getBindingLabel(declaration.resolveBinding(), JavaElementLabels.ALL_FULLY_QUALIFIED)),
											JavaStatusContext.create(rewriter.getCu(), invocation)));
							result= false;
						} else {
							if (arg.getNodeType() != ASTNode.THIS_EXPRESSION) {
								newMethodInvocation.setStructuralProperty(MethodInvocation.EXPRESSION_PROPERTY, rewrite.createCopyTarget(arg));
							}
							if (needsTargetNode()) {
								newMethodInvocation.arguments().add(rewrite.getAST().newThisExpression());
							}
						}
					} else {
						newMethodInvocation.arguments().add(rewrite.createCopyTarget(arg));
					}
				}
			}
			if (result) {
				rewrite.replace(node, newMethodInvocation, group);
			}
		} else if (node instanceof MethodInvocation) {
			final MethodInvocation invocation= (MethodInvocation) node;
			final ListRewrite list= rewrite.getListRewrite(invocation, MethodInvocation.ARGUMENTS_PROPERTY);
			if (fTarget.isField()) {
				Expression access= null;
				if (invocation.getExpression() != null) {
					access= createInlinedTargetExpression(rewriter, (IJavaElement) match.getElement(), invocation.getExpression(), adjustments, status);
					rewrite.set(invocation, MethodInvocation.EXPRESSION_PROPERTY, access, group);
				} else
					rewrite.set(invocation, MethodInvocation.EXPRESSION_PROPERTY, rewrite.getAST().newSimpleName(fTarget.getName()), group);
				if (needsTargetNode()) {
					if (access == null || !(access instanceof FieldAccess))
						list.insertFirst(rewrite.getAST().newThisExpression(), null);
					else
						list.insertFirst(rewrite.createCopyTarget(invocation.getExpression()), null);
				}
			} else {
				final IVariableBinding[] bindings= getArgumentBindings(declaration);
				if (bindings.length > 0) {
					int index= 0;
					for (; index < bindings.length; index++)
						if (Bindings.equals(bindings[index], fTarget))
							break;
					if (index < bindings.length && invocation.arguments().size() > index) {
						final Expression argument= (Expression) invocation.arguments().get(index);
						if (argument instanceof NullLiteral) {
							status.merge(RefactoringStatus.createErrorStatus(Messages.format(RefactoringCoreMessages.MoveInstanceMethodProcessor_no_null_argument, BindingLabelProvider.getBindingLabel(declaration.resolveBinding(), JavaElementLabels.ALL_FULLY_QUALIFIED)), JavaStatusContext.create(rewriter.getCu(), invocation)));
							result= false;
						} else {
							if (argument instanceof ThisExpression)
								rewrite.remove(invocation.getExpression(), null);
							else
								rewrite.set(invocation, MethodInvocation.EXPRESSION_PROPERTY, rewrite.createCopyTarget(argument), group);
							if (needsTargetNode()) {
								if (invocation.getExpression() != null)
									list.replace(argument, rewrite.createCopyTarget(invocation.getExpression()), group);
								else {
									final ThisExpression expression= rewrite.getAST().newThisExpression();
									final AbstractTypeDeclaration member= (AbstractTypeDeclaration) ASTNodes.getParent(invocation, AbstractTypeDeclaration.class);
									if (member != null) {
										final ITypeBinding resolved= member.resolveBinding();
										if (ASTNodes.getParent(invocation, AnonymousClassDeclaration.class) != null || resolved != null && resolved.isMember()) {
											final IMethodBinding method= declaration.resolveBinding();
											if (method != null) {
												final ITypeBinding declaring= method.getDeclaringClass();
												if (declaring != null)
													expression.setQualifier(rewrite.getAST().newSimpleName(declaring.getName()));
											}
										}
									}
									list.replace(argument, expression, group);
								}
							} else
								list.remove(argument, group);
						}
					}
				}
			}
			if (result)
				rewrite.set(invocation, MethodInvocation.NAME_PROPERTY, rewrite.getAST().newSimpleName(fMethodName), group);
		}
		return result;
	}

	/**
	 * Creates the target field expression for the inline method invocation.
	 *
	 * @param rewriter
	 *            the current compilation unit rewrite
	 * @param enclosingElement
	 *            the enclosing java element of the method invocation.
	 * @param original
	 *            the original method invocation expression
	 * @param adjustments
	 *            the map of elements to visibility adjustments
	 * @param status
	 *            the refactoring status
	 * @return
	 * 			   returns the target expression
	 * @throws JavaModelException
	 *             if a problem occurred while retrieving potential getter
	 *             methods of the target
	 */
	protected Expression createInlinedTargetExpression(final CompilationUnitRewrite rewriter, final IJavaElement enclosingElement, final Expression original, final Map<IMember, IncomingMemberVisibilityAdjustment> adjustments, final RefactoringStatus status) throws JavaModelException {
		Assert.isNotNull(rewriter);
		Assert.isNotNull(enclosingElement);
		Assert.isNotNull(original);
		Assert.isNotNull(adjustments);
		Assert.isNotNull(status);
		Assert.isTrue(fTarget.isField());
		final Expression expression= (Expression) ASTNode.copySubtree(fSourceRewrite.getASTRewrite().getAST(), original);
		final Expression result= createAdjustedTargetExpression(enclosingElement, expression, adjustments, fSourceRewrite.getASTRewrite());
		if (result == null) {
			final FieldAccess access= fSourceRewrite.getASTRewrite().getAST().newFieldAccess();
			access.setExpression(expression);
			access.setName(fSourceRewrite.getASTRewrite().getAST().newSimpleName(fTarget.getName()));
			return access;
		}
		return result;
	}

	/**
	 * Creates the method arguments for the target method declaration.
	 *
	 * @param rewrites
	 *            the compilation unit rewrites
	 * @param rewrite
	 *            the source ast rewrite
	 * @param declaration
	 *            the source method declaration
	 * @param adjustments
	 *            the map of elements to visibility adjustments
	 * @param status
	 *            the refactoring status
	 * @throws JavaModelException
	 *             if an error occurs while accessing the types of the arguments
	 */
	protected void createMethodArguments(Map<ICompilationUnit, CompilationUnitRewrite> rewrites, ASTRewrite rewrite, final MethodDeclaration declaration, Map<IMember, IncomingMemberVisibilityAdjustment> adjustments, RefactoringStatus status) throws JavaModelException {
		Assert.isNotNull(rewrites);
		Assert.isNotNull(declaration);
		Assert.isNotNull(rewrite);
		Assert.isNotNull(adjustments);
		Assert.isNotNull(status);
		final CompilationUnitRewrite rewriter= getCompilationUnitRewrite(rewrites, getTargetType().getCompilationUnit());
		final AST ast= rewriter.getRoot().getAST();
		final AstNodeFinder finder= new AnonymousClassReferenceFinder(declaration);
		declaration.accept(finder);
		final List<ASTNode> arguments= new ArrayList<ASTNode>(declaration.parameters().size() + 1);
		createArgumentList(declaration, arguments, new VisibilityAdjustingArgumentFactory(ast, rewrites, adjustments) {

			@Override
			public final ASTNode getArgumentNode(final IVariableBinding binding, final boolean last) throws JavaModelException {
				Assert.isNotNull(binding);
				final SingleVariableDeclaration variable= ast.newSingleVariableDeclaration();
				final ITypeBinding type= binding.getType();
				adjustTypeVisibility(type);
				variable.setName(ast.newSimpleName(binding.getName()));
				variable.modifiers().addAll(ast.newModifiers(binding.getModifiers()));
				final IMethodBinding method= binding.getDeclaringMethod();
				if (last && method != null && method.isVarargs()) {
					variable.setVarargs(true);
					String name= null;
					if (type.isArray()) {
						name= type.getElementType().getName();
						if (PrimitiveType.toCode(name) != null)
							variable.setType(ast.newPrimitiveType(PrimitiveType.toCode(name)));
						else
							variable.setType(ast.newSimpleType(ast.newSimpleName(name)));
					} else {
						name= type.getName();
						if (PrimitiveType.toCode(name) != null)
							variable.setType(ast.newPrimitiveType(PrimitiveType.toCode(name)));
						else
							variable.setType(ast.newSimpleType(ast.newSimpleName(name)));
					}
				} else
					variable.setType(rewriter.getImportRewrite().addImport(type, ast));
				return variable;
			}

			@Override
			public final ASTNode getTargetNode() throws JavaModelException {
				final SingleVariableDeclaration variable= ast.newSingleVariableDeclaration();
				final IMethodBinding method= declaration.resolveBinding();
				if (method != null) {
					final ITypeBinding declaring= method.getDeclaringClass();
					if (declaring != null) {
						adjustTypeVisibility(declaring);
						variable.setType(rewriter.getImportRewrite().addImport(declaring, ast));
						variable.setName(ast.newSimpleName(fTargetName));
						if (finder.getResult().size() > 0)
							variable.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.FINAL_KEYWORD));
					}
				}
				return variable;
			}
		});
		final ListRewrite list= rewrite.getListRewrite(declaration, MethodDeclaration.PARAMETERS_PROPERTY);
		ASTNode node= null;
		for (final Iterator<SingleVariableDeclaration> iterator= declaration.parameters().iterator(); iterator.hasNext();) {
			node= iterator.next();
			list.remove(node, null);
		}
		for (final Iterator<ASTNode> iterator= arguments.iterator(); iterator.hasNext();) {
			node= iterator.next();
			list.insertLast(node, null);
		}
	}

	/**
	 * Creates the method body for the target method declaration.
	 *
	 * @param rewriter
	 *            the target compilation unit rewrite
	 * @param rewrite
	 *            the source ast rewrite
	 * @param declaration
	 *            the source method declaration
	 */
	protected void createMethodBody(final CompilationUnitRewrite rewriter, final ASTRewrite rewrite, final MethodDeclaration declaration) {
		Assert.isNotNull(declaration);
		declaration.getBody().accept(new MethodBodyRewriter(rewriter, rewrite, declaration));
	}

	/**
	 * Creates the method comment for the target method declaration.
	 *
	 * @param rewrite
	 *            the source ast rewrite
	 * @param declaration
	 *            the source method declaration
	 * @throws JavaModelException
	 *             if the argument references could not be generated
	 */
	protected void createMethodComment(final ASTRewrite rewrite, final MethodDeclaration declaration) throws JavaModelException {
		Assert.isNotNull(rewrite);
		Assert.isNotNull(declaration);
		final Javadoc comment= declaration.getJavadoc();
		if (comment != null) {
			final List<TagElement> tags= new LinkedList<TagElement>(comment.tags());
			final IVariableBinding[] bindings= getArgumentBindings(declaration);
			final Map<String, TagElement> elements= new HashMap<String, TagElement>(bindings.length);
			String name= null;
			List<? extends ASTNode> fragments= null;
			TagElement element= null;
			TagElement reference= null;
			IVariableBinding binding= null;
			for (int index= 0; index < bindings.length; index++) {
				binding= bindings[index];
				for (final Iterator<TagElement> iterator= comment.tags().iterator(); iterator.hasNext();) {
					element= iterator.next();
					name= element.getTagName();
					fragments= element.fragments();
					if (name != null) {
						if (name.equals(TagElement.TAG_PARAM) && !fragments.isEmpty() && fragments.get(0) instanceof SimpleName) {
							final SimpleName simple= (SimpleName) fragments.get(0);
							if (binding.getName().equals(simple.getIdentifier())) {
								elements.put(binding.getKey(), element);
								tags.remove(element);
							}
						} else if (reference == null)
							reference= element;
					}
				}
			}
			if (bindings.length == 0 && reference == null) {
				for (final Iterator<TagElement> iterator= comment.tags().iterator(); iterator.hasNext();) {
					element= iterator.next();
					name= element.getTagName();
					fragments= element.fragments();
					if (name != null && !name.equals(TagElement.TAG_PARAM))
						reference= element;
				}
			}
			final List<ASTNode> arguments= new ArrayList<ASTNode>(bindings.length + 1);
			createArgumentList(declaration, arguments, new IArgumentFactory() {

				public final ASTNode getArgumentNode(final IVariableBinding argument, final boolean last) throws JavaModelException {
					Assert.isNotNull(argument);
					if (elements.containsKey(argument.getKey()))
						return rewrite.createCopyTarget(elements.get(argument.getKey()));
					return JavadocUtil.createParamTag(argument.getName(), declaration.getAST(), fMethod.getJavaProject());
				}

				public final ASTNode getTargetNode() throws JavaModelException {
					return JavadocUtil.createParamTag(fTargetName, declaration.getAST(), fMethod.getJavaProject());
				}
			});
			final ListRewrite rewriter= rewrite.getListRewrite(comment, Javadoc.TAGS_PROPERTY);
			ASTNode tag= null;
			for (final Iterator<TagElement> iterator= comment.tags().iterator(); iterator.hasNext();) {
				tag= iterator.next();
				if (!tags.contains(tag))
					rewriter.remove(tag, null);
			}
			for (final Iterator<ASTNode> iterator= arguments.iterator(); iterator.hasNext();) {
				tag= iterator.next();
				if (reference != null)
					rewriter.insertBefore(tag, reference, null);
				else
					rewriter.insertLast(tag, null);
			}
		}
	}

	/**
	 * Creates the method content of the moved method.
	 *
	 * @param document
	 *            the document representing the source compilation unit
	 * @param declaration
	 *            the source method declaration
	 * @param rewrite
	 *            the ast rewrite to use
	 * @return the string representing the moved method body
	 * @throws BadLocationException
	 *             if an offset into the document is invalid
	 */
	protected String createMethodContent(final IDocument document, final MethodDeclaration declaration, final ASTRewrite rewrite) throws BadLocationException {
		Assert.isNotNull(document);
		Assert.isNotNull(declaration);
		Assert.isNotNull(rewrite);
		final IRegion range= new Region(declaration.getStartPosition(), declaration.getLength());
		final RangeMarker marker= new RangeMarker(range.getOffset(), range.getLength());
		final IJavaProject project= fMethod.getJavaProject();
		final TextEdit[] edits= rewrite.rewriteAST(document, project.getOptions(true)).removeChildren();
		for (int index= 0; index < edits.length; index++)
			marker.addChild(edits[index]);
		final MultiTextEdit result= new MultiTextEdit();
		result.addChild(marker);
		final TextEditProcessor processor= new TextEditProcessor(document, new MultiTextEdit(0, document.getLength()), TextEdit.UPDATE_REGIONS);
		processor.getRoot().addChild(result);
		processor.performEdits();
		final IRegion region= document.getLineInformation(document.getLineOfOffset(marker.getOffset()));
		return Strings.changeIndent(document.get(marker.getOffset(), marker.getLength()), Strings.computeIndentUnits(document.get(region.getOffset(), region.getLength()), project), project, "", TextUtilities.getDefaultLineDelimiter(document)); //$NON-NLS-1$
	}

	/**
	 * Creates the necessary changes to create the delegate method with the
	 * original method body.
	 *
	 * @param document
	 *            the buffer containing the source of the source compilation
	 *            unit
	 * @param declaration
	 *            the method declaration to use as source
	 * @param rewrite
	 *            the ast rewrite to use for the copy of the method body
	 * @param rewrites
	 *            the compilation unit rewrites
	 * @param adjustments
	 *            the map of elements to visibility adjustments
	 * @param status
	 *            the refactoring status
	 * @param monitor
	 *            the progress monitor to display progress
	 * @throws CoreException
	 *             if an error occurs
	 */
	protected void createMethodCopy(IDocument document, MethodDeclaration declaration, ASTRewrite rewrite, Map<ICompilationUnit, CompilationUnitRewrite> rewrites, Map<IMember, IncomingMemberVisibilityAdjustment> adjustments, RefactoringStatus status, IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(document);
		Assert.isNotNull(declaration);
		Assert.isNotNull(rewrite);
		Assert.isNotNull(rewrites);
		Assert.isNotNull(adjustments);
		Assert.isNotNull(status);
		Assert.isNotNull(monitor);
		final CompilationUnitRewrite rewriter= getCompilationUnitRewrite(rewrites, getTargetType().getCompilationUnit());
		try {
			rewrite.set(declaration, MethodDeclaration.NAME_PROPERTY, rewrite.getAST().newSimpleName(fMethodName), null);
			boolean same= false;
			final IMethodBinding binding= declaration.resolveBinding();
			if (binding != null) {
				final ITypeBinding declaring= binding.getDeclaringClass();
				if (declaring != null && Bindings.equals(declaring.getPackage(), fTarget.getType().getPackage()))
					same= true;
				final Modifier.ModifierKeyword keyword= same ? null : Modifier.ModifierKeyword.PUBLIC_KEYWORD;
				ModifierRewrite modifierRewrite= ModifierRewrite.create(rewrite, declaration);
				if (JdtFlags.isDefaultMethod(binding) && getTargetType().isClass()) {
					// Remove 'default' modifier and add 'public' visibility
					modifierRewrite.setVisibility(Modifier.PUBLIC, null);
					modifierRewrite.setModifiers(Modifier.NONE, Modifier.DEFAULT, null);
				} else if (!JdtFlags.isDefaultMethod(binding) && getTargetType().isInterface()) {
					// Remove visibility modifiers and add 'default'
					modifierRewrite.setModifiers(Modifier.DEFAULT, Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE, null);
				} else if (MemberVisibilityAdjustor.hasLowerVisibility(binding.getModifiers(), same ? Modifier.NONE : keyword == null ? Modifier.NONE : keyword.toFlagValue())
						&& MemberVisibilityAdjustor.needsVisibilityAdjustments(fMethod, keyword, adjustments)) {
					final MemberVisibilityAdjustor.IncomingMemberVisibilityAdjustment adjustment= new MemberVisibilityAdjustor.IncomingMemberVisibilityAdjustment(fMethod, keyword, RefactoringStatus.createStatus(RefactoringStatus.WARNING, Messages.format(RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_method_warning, new String[] { MemberVisibilityAdjustor.getLabel(fMethod), MemberVisibilityAdjustor.getLabel(keyword) }), JavaStatusContext.create(fMethod), null, RefactoringStatusEntry.NO_CODE, null));
					modifierRewrite.setVisibility(keyword == null ? Modifier.NONE : keyword.toFlagValue(), null);
					adjustment.setNeedsRewriting(false);
					adjustments.put(fMethod, adjustment);
				}
			}
			for (IExtendedModifier modifier : (List<IExtendedModifier>) declaration.modifiers()) {
				if (modifier.isAnnotation()) {
					Annotation annotation= (Annotation) modifier;
					ITypeBinding typeBinding= annotation.resolveTypeBinding();
					if (typeBinding != null && typeBinding.getQualifiedName().equals("java.lang.Override")) { //$NON-NLS-1$
						rewrite.remove(annotation, null);
					}
				}
			}
			createMethodArguments(rewrites, rewrite, declaration, adjustments, status);
			createMethodTypeParameters(rewrite, declaration, status);
			createMethodComment(rewrite, declaration);
			createMethodBody(rewriter, rewrite, declaration);
		} finally {
			if (fMethod.getCompilationUnit().equals(getTargetType().getCompilationUnit()))
				rewriter.clearImportRewrites();
		}
	}

	/**
	 * Creates the necessary changes to replace the body of the method
	 * declaration with an expression to invoke the delegate.
	 *
	 * @param declaration
	 *            the method declaration to replace its body
	 * @param rewrites
	 *            the compilation unit rewrites
	 * @param adjustments
	 *            the map of elements to visibility adjustments
	 * @param status
	 *            the refactoring status
	 * @param monitor
	 *            the progress monitor to display progress
	 * @throws CoreException
	 *             if the change could not be generated
	 */
	protected void createMethodDelegation(final MethodDeclaration declaration, final Map<ICompilationUnit, CompilationUnitRewrite> rewrites, final Map<IMember, IncomingMemberVisibilityAdjustment> adjustments, final RefactoringStatus status, final IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(declaration);
		Assert.isNotNull(monitor);

		final DelegateInstanceMethodCreator creator= new DelegateInstanceMethodCreator(adjustments, rewrites);
		creator.setSourceRewrite(fSourceRewrite);
		creator.setCopy(false);
		creator.setDeclareDeprecated(fDelegateDeprecation);
		creator.setDeclaration(declaration);
		creator.setNewElementName(fMethodName);
		creator.prepareDelegate();
		creator.createEdit();
	}

	/**
	 * Creates the necessary changes to inline the method invocations to the
	 * original method.
	 *
	 * @param rewrites
	 *            the map of compilation units to compilation unit rewrites
	 * @param declaration
	 *            the source method declaration
	 * @param groups
	 *            the search result groups representing all references to the
	 *            moved method, including references in comments
	 * @param adjustments
	 *            the map of elements to visibility adjustments
	 * @param binaryRefs
	 *            the binary references context
	 * @param status
	 *            the refactoring status
	 * @param monitor
	 *            the progress monitor to use
	 * @return <code>true</code> if all method invocations to the original
	 *         method declaration could be inlined, <code>false</code>
	 *         otherwise
	 */
	protected boolean createMethodDelegator(Map<ICompilationUnit, CompilationUnitRewrite> rewrites, MethodDeclaration declaration, SearchResultGroup[] groups, Map<IMember, IncomingMemberVisibilityAdjustment> adjustments, ReferencesInBinaryContext binaryRefs, RefactoringStatus status, IProgressMonitor monitor) {
		Assert.isNotNull(rewrites);
		Assert.isNotNull(declaration);
		Assert.isNotNull(groups);
		Assert.isNotNull(adjustments);
		Assert.isNotNull(status);
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask("", groups.length); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_creating);
			try {
				boolean result= true;
				boolean found= false;
				final ITypeHierarchy hierarchy= fMethod.getDeclaringType().newTypeHierarchy(new SubProgressMonitor(monitor, 1));
				IType type= null;
				IMethod method= null;
				IType[] types= hierarchy.getAllSubtypes(fMethod.getDeclaringType());
				for (int index= 0; index < types.length && !found; index++) {
					type= types[index];
					method= JavaModelUtil.findMethod(fMethod.getElementName(), fMethod.getParameterTypes(), false, type);
					if (method != null)
						found= true;
				}
				types= hierarchy.getAllSupertypes(fMethod.getDeclaringType());
				for (int index= 0; index < types.length && !found; index++) {
					type= types[index];
					method= JavaModelUtil.findMethod(fMethod.getElementName(), fMethod.getParameterTypes(), false, type);
					if (method != null)
						found= true;
				}
				if (found) {
					status.merge(RefactoringStatus.createWarningStatus(Messages.format(RefactoringCoreMessages.MoveInstanceMethodProcessor_inline_overridden, BindingLabelProvider.getBindingLabel(declaration.resolveBinding(), JavaElementLabels.ALL_FULLY_QUALIFIED)), JavaStatusContext.create(fMethod)));
					result= false;
				} else {
					monitor.worked(1);
					SearchMatch[] matches= null;
					IJavaElement element= null;
					ICompilationUnit unit= null;
					CompilationUnitRewrite rewrite= null;
					SearchResultGroup group= null;
					for (int index= 0; index < groups.length; index++) {
						group= groups[index];
						element= JavaCore.create(group.getResource());
						if (element instanceof ICompilationUnit) {
							matches= group.getSearchResults();
							unit= (ICompilationUnit) element;
							rewrite= getCompilationUnitRewrite(rewrites, unit);
							SearchMatch match= null;
							for (int offset= 0; offset < matches.length; offset++) {
								match= matches[offset];
								if (match.getAccuracy() == SearchMatch.A_INACCURATE) {
									status.merge(RefactoringStatus.createWarningStatus(Messages.format(RefactoringCoreMessages.MoveInstanceMethodProcessor_inline_inaccurate, BasicElementLabels.getFileName(unit)), JavaStatusContext.create(unit, new SourceRange(match.getOffset(), match.getLength()))));
									result= false;
								} else if (!createInlinedMethodInvocation(rewrite, declaration, match, adjustments, status))
									result= false;
							}
						} else {
							result= false;
						}
					}
					monitor.worked(1);
				}
				return result;
			} catch (CoreException exception) {
				status.merge(RefactoringStatus.create(exception.getStatus()));
				return false;
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Creates the necessary imports for the copied method in the target
	 * compilation unit.
	 *
	 * @param rewrite
	 *            the target compilation unit rewrite
	 * @param declaration
	 *            the source method declaration
	 * @param monitor
	 *            the progress monitor to use
	 * @param status
	 *            the refactoring status to use
	 * @throws CoreException
	 *             if an error occurs
	 */
	protected void createMethodImports(final CompilationUnitRewrite rewrite, final MethodDeclaration declaration, final IProgressMonitor monitor, final RefactoringStatus status) throws CoreException {
		Assert.isNotNull(rewrite);
		Assert.isNotNull(declaration);
		Assert.isNotNull(monitor);
		Assert.isNotNull(status);
		monitor.beginTask("", 1); //$NON-NLS-1$
		monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_creating);
		try {
			ImportRewriteUtil.addImports(rewrite, null, declaration, new HashMap<Name, String>(), new HashMap<Name, String>(), false);
		} finally {
			monitor.done();
		}
	}

	/**
	 * Creates the necessary change to updated a comment reference represented
	 * by a search match.
	 *
	 * @param rewrite
	 *            the current compilation unit rewrite
	 * @param declaration
	 *            the source method declaration
	 * @param match
	 *            the search match representing the method reference
	 * @param status
	 *            the refactoring status
	 */
	protected void createMethodJavadocReference(CompilationUnitRewrite rewrite, MethodDeclaration declaration, SearchMatch match, RefactoringStatus status) {
		Assert.isNotNull(rewrite);
		Assert.isNotNull(declaration);
		Assert.isNotNull(match);
		Assert.isNotNull(status);
		final ASTNode node= ASTNodeSearchUtil.findNode(match, rewrite.getRoot());
		if (node instanceof MethodRef) {
			final AST ast= node.getAST();
			final MethodRef successor= ast.newMethodRef();

			rewrite.getASTRewrite().replace(node, successor, null);
		}
	}

	/**
	 * Creates the necessary changes to update tag references to the original
	 * method.
	 *
	 * @param rewrites
	 *            the map of compilation units to compilation unit rewrites
	 * @param declaration
	 *            the source method declaration
	 * @param groups
	 *            the search result groups representing all references to the
	 *            moved method, including references in comments
	 * @param status
	 *            the refactoring status
	 * @param monitor
	 *            the progress monitor to use
	 */
	protected void createMethodJavadocReferences(Map<ICompilationUnit, CompilationUnitRewrite> rewrites, MethodDeclaration declaration, SearchResultGroup[] groups, RefactoringStatus status, IProgressMonitor monitor) {
		Assert.isNotNull(rewrites);
		Assert.isNotNull(declaration);
		Assert.isNotNull(status);
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask("", groups.length); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MoveInstanceMethodProcessor_creating);
			SearchMatch[] matches= null;
			IJavaElement element= null;
			ICompilationUnit unit= null;
			CompilationUnitRewrite rewrite= null;
			SearchResultGroup group= null;
			for (int index= 0; index < groups.length; index++) {
				group= groups[index];
				element= JavaCore.create(group.getResource());
				unit= group.getCompilationUnit();
				if (element instanceof ICompilationUnit) {
					matches= group.getSearchResults();
					unit= (ICompilationUnit) element;
					rewrite= getCompilationUnitRewrite(rewrites, unit);
					SearchMatch match= null;
					for (int offset= 0; offset < matches.length; offset++) {
						match= matches[offset];
						if (match.getAccuracy() == SearchMatch.A_INACCURATE) {
							status.merge(RefactoringStatus.createWarningStatus(Messages.format(RefactoringCoreMessages.MoveInstanceMethodProcessor_inline_inaccurate, BasicElementLabels.getFileName(unit)), JavaStatusContext.create(unit, new SourceRange(match.getOffset(), match.getLength()))));
						} else
							createMethodJavadocReference(rewrite, declaration, match, status);
					}
				}
				monitor.worked(1);
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Creates a comment method reference to the moved method
	 *
	 * @param declaration
	 *            the method declaration of the original method
	 * @param ast
	 *            the ast to create the method reference for
	 * @return the created link tag to reference the method
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	protected ASTNode createMethodReference(final MethodDeclaration declaration, final AST ast) throws JavaModelException {
		Assert.isNotNull(ast);
		Assert.isNotNull(declaration);
		final MethodRef reference= ast.newMethodRef();
		reference.setName(ast.newSimpleName(fMethodName));
		reference.setQualifier(ASTNodeFactory.newName(ast, fTargetType.getFullyQualifiedName('.')));
		createArgumentList(declaration, reference.parameters(), new IArgumentFactory() {

			public final ASTNode getArgumentNode(final IVariableBinding binding, final boolean last) {
				Assert.isNotNull(binding);
				final MethodRefParameter parameter= ast.newMethodRefParameter();
				parameter.setType(ASTNodeFactory.newType(ast, binding.getType().getName()));
				return parameter;
			}

			public final ASTNode getTargetNode() {
				final MethodRefParameter parameter= ast.newMethodRefParameter();
				final IMethodBinding method= declaration.resolveBinding();
				if (method != null) {
					final ITypeBinding declaring= method.getDeclaringClass();
					if (declaring != null)
						parameter.setType(ASTNodeFactory.newType(ast, Bindings.getFullyQualifiedName(declaring)));
				}
				return parameter;
			}
		});
		return reference;
	}

	/**
	 * @param document
	 *            the buffer containing the source of the source compilation
	 *            unit
	 * @param declaration
	 *            the method declaration to use as source
	 * @param rewrite
	 *            the ast rewrite to use for the copy of the method body
	 * @param rewrites
	 *            the compilation unit rewrites
	 * @throws JavaModelException
	 *             if the insertion point cannot be found
	 */
	protected void createMethodSignature(final IDocument document, final MethodDeclaration declaration, final ASTRewrite rewrite, final Map<ICompilationUnit, CompilationUnitRewrite> rewrites) throws JavaModelException {
		Assert.isNotNull(document);
		Assert.isNotNull(declaration);
		Assert.isNotNull(rewrite);
		Assert.isNotNull(rewrites);
		try {
			final CompilationUnitRewrite rewriter= getCompilationUnitRewrite(rewrites, getTargetType().getCompilationUnit());
			final MethodDeclaration stub= (MethodDeclaration) rewriter.getASTRewrite().createStringPlaceholder(createMethodContent(document, declaration, rewrite), ASTNode.METHOD_DECLARATION);
			final AbstractTypeDeclaration type= ASTNodeSearchUtil.getAbstractTypeDeclarationNode(getTargetType(), rewriter.getRoot());
			rewriter.getASTRewrite().getListRewrite(type, type.getBodyDeclarationsProperty()).insertAt(stub, ASTNodes.getInsertionIndex(stub, type.bodyDeclarations()), rewriter.createGroupDescription(RefactoringCoreMessages.MoveInstanceMethodProcessor_add_moved_method));
		} catch (BadLocationException exception) {
			JavaPlugin.log(exception);
		}
	}

	/**
	 * Creates the necessary changes to remove method type parameters if they
	 * match with enclosing type parameters.
	 *
	 * @param rewrite
	 *            the ast rewrite to use
	 * @param declaration
	 *            the method declaration to remove type parameters
	 * @param status
	 *            the refactoring status
	 */
	protected void createMethodTypeParameters(final ASTRewrite rewrite, final MethodDeclaration declaration, final RefactoringStatus status) {
		ITypeBinding binding= fTarget.getType();
		if (binding != null && binding.isParameterizedType()) {
			final IMethodBinding method= declaration.resolveBinding();
			if (method != null) {
				final ITypeBinding[] parameters= method.getTypeParameters();
				if (parameters.length > 0) {
					final ListRewrite rewriter= rewrite.getListRewrite(declaration, MethodDeclaration.TYPE_PARAMETERS_PROPERTY);
					boolean foundStatic= false;
					while (binding != null && !foundStatic) {
						if (Flags.isStatic(binding.getModifiers()))
							foundStatic= true;
						final ITypeBinding[] bindings= binding.getTypeArguments();
						for (int index= 0; index < bindings.length; index++) {
							for (int offset= 0; offset < parameters.length; offset++) {
								if (parameters[offset].getName().equals(bindings[index].getName())) {
									rewriter.remove((ASTNode) rewriter.getOriginalList().get(offset), null);
									status.addWarning(Messages.format(RefactoringCoreMessages.MoveInstanceMethodProcessor_present_type_parameter_warning, new Object[] { BasicElementLabels.getJavaElementName(parameters[offset].getName()), BindingLabelProvider.getBindingLabel(binding, JavaElementLabels.ALL_FULLY_QUALIFIED) }), JavaStatusContext.create(fMethod));
								}
							}
						}
						binding= binding.getDeclaringClass();
					}
				}
			}
		}
	}

	/**
	 * Creates the expression to access the new target.
	 *
	 * @param declaration
	 *            the method declaration where to access the target
	 * @return the corresponding expression
	 */
	protected Expression createSimpleTargetAccessExpression(final MethodDeclaration declaration) {
		Assert.isNotNull(declaration);
		Expression expression= null;
		final AST ast= declaration.getAST();
		final ITypeBinding type= fTarget.getDeclaringClass();
		if (type != null) {
			boolean shadows= false;
			final IVariableBinding[] bindings= getArgumentBindings(declaration);
			IVariableBinding variable= null;
			for (int index= 0; index < bindings.length; index++) {
				variable= bindings[index];
				if (fMethod.getDeclaringType().getField(variable.getName()).exists()) {
					shadows= true;
					break;
				}
			}
			if (fSettings.useKeywordThis || shadows) {
				final FieldAccess access= ast.newFieldAccess();
				access.setName(ast.newSimpleName(fTarget.getName()));
				access.setExpression(ast.newThisExpression());
				expression= access;
			} else
				expression= ast.newSimpleName(fTarget.getName());
		} else
			expression= ast.newSimpleName(fTarget.getName());
		return expression;
	}

	/**
	 * Returns the candidate targets for the method to move.
	 *
	 * @return the candidate targets as variable bindings of fields and
	 *         parameters
	 */
	public final IVariableBinding[] getCandidateTargets() {
		Assert.isNotNull(fCandidateTargets);
		return fCandidateTargets;
	}

	/**
	 * Returns a compilation unit rewrite for the specified compilation unit.
	 *
	 * @param rewrites
	 *            the compilation unit rewrite map
	 * @param unit
	 *            the compilation unit
	 * @return the corresponding compilation unit rewrite
	 */
	protected CompilationUnitRewrite getCompilationUnitRewrite(final Map<ICompilationUnit, CompilationUnitRewrite> rewrites, final ICompilationUnit unit) {
		Assert.isNotNull(rewrites);
		Assert.isNotNull(unit);
		CompilationUnitRewrite rewrite= rewrites.get(unit);
		if (rewrite == null) {
			rewrite= new CompilationUnitRewrite(unit);
			rewrites.put(unit, rewrite);
		}
		return rewrite;
	}

	/**
	 * {@inheritDoc}
	 */
	public final boolean getDelegateUpdating() {
		return fDelegatingUpdating;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getDelegateUpdatingTitle(boolean plural) {
		if (plural)
			return RefactoringCoreMessages.DelegateMethodCreator_keep_original_moved_plural;
		else
			return RefactoringCoreMessages.DelegateMethodCreator_keep_original_moved_singular;
	}

	/**
	 * {@inheritDoc}
	 */
	public final boolean getDeprecateDelegates() {
		return fDelegateDeprecation;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final Object[] getElements() {
		return new Object[] { fMethod };
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final String getIdentifier() {
		return IDENTIFIER;
	}

	/**
	 * Returns the method to be moved.
	 *
	 * @return the method to be moved
	 */
	public final IMethod getMethod() {
		return fMethod;
	}

	/**
	 * Returns the new method name.
	 *
	 * @return the name of the new method
	 */
	public final String getMethodName() {
		return fMethodName;
	}

	/**
	 * Returns the possible targets for the method to move.
	 *
	 * @return the possible targets as variable bindings of read-only fields and
	 *         parameters
	 */
	public final IVariableBinding[] getPossibleTargets() {
		Assert.isNotNull(fPossibleTargets);
		return fPossibleTargets;
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#getProcessorName()
	 */
	@Override
	public final String getProcessorName() {
		return RefactoringCoreMessages.MoveInstanceMethodProcessor_name;
	}

	/**
	 * Returns the index of the chosen target.
	 *
	 * @return the target index
	 */
	protected final int getTargetIndex() {
		final IVariableBinding[] targets= getPossibleTargets();
		int result= -1;
		for (int index= 0; index < targets.length; index++) {
			if (Bindings.equals(fTarget, targets[index])) {
				result= index;
				break;
			}
		}
		return result;
	}

	/**
	 * Returns the new target name.
	 *
	 * @return the name of the new target
	 */
	public final String getTargetName() {
		return fTargetName;
	}

	/**
	 * Returns the type of the new target.
	 *
	 * @return the type of the new target
	 * @throws JavaModelException
	 *             if the type does not exist
	 */
	protected IType getTargetType() throws JavaModelException {
		Assert.isNotNull(fTarget);
		if (fTargetType == null) {
			final ITypeBinding binding= fTarget.getType();
			if (binding != null)
				fTargetType= (IType) binding.getJavaElement();
			else
				throw new JavaModelException(new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), 0, RefactoringCoreMessages.MoveInstanceMethodProcessor_cannot_be_moved, null)));
		}
		return fTargetType;
	}

	/**
	 * Initializes the refactoring with the given input.
	 *
	 * @param method
	 *            the method to move
	 */
	protected void initialize(final IMethod method) {
		Assert.isNotNull(method);
		fSourceRewrite= new CompilationUnitRewrite(fMethod.getCompilationUnit());
		fMethodName= method.getElementName();
		fTargetName= suggestTargetName();
		if (fSettings == null)
			fSettings= JavaPreferencesSettings.getCodeGenerationSettings(fMethod.getJavaProject());
	}

	private RefactoringStatus initialize(JavaRefactoringArguments extended) {
		final String handle= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT);
		if (handle != null) {
			final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(extended.getProject(), handle, false);
			if (element == null || !element.exists() || element.getElementType() != IJavaElement.METHOD)
				return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getProcessorName(), IJavaRefactorings.MOVE_METHOD);
			else {
				fMethod= (IMethod) element;
				initialize(fMethod);
			}
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT));
		final String name= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME);
		if (name != null) {
			final RefactoringStatus status= setMethodName(name);
			if (status.hasError())
				return status;
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME));
		final String deprecate= extended.getAttribute(ATTRIBUTE_DEPRECATE);
		if (deprecate != null) {
			fDelegateDeprecation= Boolean.valueOf(deprecate).booleanValue();
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_DEPRECATE));
		final String remove= extended.getAttribute(ATTRIBUTE_REMOVE);
		if (remove != null) {
			fRemove= Boolean.valueOf(remove).booleanValue();
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_REMOVE));
		final String inline= extended.getAttribute(ATTRIBUTE_INLINE);
		if (inline != null) {
			fInline= Boolean.valueOf(inline).booleanValue();
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_INLINE));
		final String getter= extended.getAttribute(ATTRIBUTE_USE_GETTER);
		if (getter != null)
			fUseGetters= Boolean.valueOf(getter).booleanValue();
		else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_USE_GETTER));
		final String setter= extended.getAttribute(ATTRIBUTE_USE_SETTER);
		if (setter != null)
			fUseSetters= Boolean.valueOf(setter).booleanValue();
		else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_USE_SETTER));
		final String target= extended.getAttribute(ATTRIBUTE_TARGET_NAME);
		if (target != null) {
			final RefactoringStatus status= setTargetName(target);
			if (status.hasError())
				return status;
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_TARGET_NAME));
		final String value= extended.getAttribute(ATTRIBUTE_TARGET_INDEX);
		if (value != null) {
			try {
				final int index= Integer.valueOf(value).intValue();
				if (index >= 0) {
					final MethodDeclaration declaration= ASTNodeSearchUtil.getMethodDeclarationNode(fMethod, fSourceRewrite.getRoot());
					if (declaration != null) {
						final IVariableBinding[] bindings= computeTargetCategories(declaration);
						if (bindings != null && index < bindings.length)
							setTarget(bindings[index]);
					}
				}
			} catch (NumberFormatException exception) {
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new String[] { value, ATTRIBUTE_TARGET_INDEX }));
			} catch (JavaModelException exception) {
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new String[] { value, ATTRIBUTE_TARGET_INDEX }));
			}
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_TARGET_INDEX));
		return new RefactoringStatus();
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#isApplicable()
	 */
	@Override
	public final boolean isApplicable() throws CoreException {
		return fMethod.exists() && !fMethod.isConstructor() && !fMethod.isBinary() && !fMethod.isReadOnly() && fMethod.getCompilationUnit() != null && !JdtFlags.isStatic(fMethod);
	}

	/**
	 * Is the specified name a target access?
	 *
	 * @param name
	 *            the name to check
	 * @return <code>true</code> if this name is a target access,
	 *         <code>false</code> otherwise
	 */
	protected boolean isTargetAccess(final Name name) {
		Assert.isNotNull(name);
		final IBinding binding= name.resolveBinding();
		if (Bindings.equals(fTarget, binding))
			return true;
		if (name.getParent() instanceof FieldAccess) {
			final FieldAccess access= (FieldAccess) name.getParent();
			final Expression expression= access.getExpression();
			if (expression instanceof Name)
				return isTargetAccess((Name) expression);
		} else if (name instanceof QualifiedName) {
			final QualifiedName qualified= (QualifiedName) name;
			if (qualified.getQualifier() != null)
				return isTargetAccess(qualified.getQualifier());
		}
		return false;
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#loadParticipants(org.eclipse.ltk.core.refactoring.RefactoringStatus,
	 *      org.eclipse.ltk.core.refactoring.participants.SharableParticipants)
	 */
	@Override
	public final RefactoringParticipant[] loadParticipants(final RefactoringStatus status, final SharableParticipants participants) throws CoreException {
		return new RefactoringParticipant[0];
	}

	/**
	 * Does the moved method need a target node?
	 *
	 * @return <code>true</code> if it needs a target node, <code>false</code>
	 *         otherwise
	 */
	public final boolean needsTargetNode() {
		return fTargetNode;
	}

	/**
	 * {@inheritDoc}
	 */
	public final void setDelegateUpdating(final boolean updating) {
		fDelegatingUpdating= updating;
		setInlineDelegator(!updating);
		setRemoveDelegator(!updating);
	}

	/**
	 * {@inheritDoc}
	 */
	public final void setDeprecateDelegates(final boolean deprecate) {
		fDelegateDeprecation= deprecate;
	}

	/**
	 * Determines whether the delegator has to be inlined.
	 *
	 * @param inline
	 *            <code>true</code> to inline the delegator,
	 *            <code>false</code> otherwise
	 */
	public final void setInlineDelegator(final boolean inline) {
		fInline= inline;
	}

	/**
	 * Sets the new method name.
	 *
	 * @param name
	 *            the name to set
	 * @return the status of the operation
	 */
	public final RefactoringStatus setMethodName(final String name) {
		Assert.isNotNull(name);
		RefactoringStatus status= Checks.checkMethodName(name, fTargetType);
		if (status.hasFatalError())
			return status;
		fMethodName= name;
		return status;
	}

	/**
	 * Determines whether the delegator has to be removed after inlining. Note
	 * that the option to inline the delegator has to be enabled if this method
	 * is called with the argument <code>true</code>.
	 *
	 * @param remove
	 *            <code>true</code> if it should be removed,
	 *            <code>false</code> otherwise
	 */
	public final void setRemoveDelegator(final boolean remove) {
		Assert.isTrue(!remove || fInline);
		fRemove= remove;
	}

	/**
	 * Sets the new target.
	 *
	 * @param target
	 *            the target to set
	 */
	public final void setTarget(final IVariableBinding target) {
		Assert.isNotNull(target);
		fTarget= target;
		fTargetType= null;
		try {
			final MethodDeclaration declaration= ASTNodeSearchUtil.getMethodDeclarationNode(fMethod, fSourceRewrite.getRoot());
			if (declaration != null) {
				final AstNodeFinder finder= new ThisReferenceFinder();
				declaration.accept(finder);
				fTargetNode= !finder.getResult().isEmpty();
				return;
			}
		} catch (JavaModelException exception) {
			JavaPlugin.log(exception);
		}
		fTargetNode= true;
	}

	/**
	 * Sets the new target name.
	 *
	 * @param name
	 *            the name to set
	 * @return the status of the operation
	 */
	public final RefactoringStatus setTargetName(final String name) {
		Assert.isNotNull(name);
		final RefactoringStatus status= Checks.checkTempName(name, fMethod);
		if (status.hasFatalError())
			return status;
		fTargetName= name;
		return status;
	}

	/**
	 * Determines whether getter methods should be used to resolve visibility
	 * issues.
	 *
	 * @param use
	 *            <code>true</code> if getter methods should be used,
	 *            <code>false</code> otherwise
	 */
	public final void setUseGetters(final boolean use) {
		fUseGetters= use;
	}

	/**
	 * Determines whether setter methods should be used to resolve visibility
	 * issues.
	 *
	 * @param use
	 *            <code>true</code> if setter methods should be used,
	 *            <code>false</code> otherwise
	 */
	public final void setUseSetters(final boolean use) {
		fUseSetters= use;
	}

	/**
	 * Should getter methods be used to resolve visibility issues?
	 *
	 * @return <code>true</code> if getter methods should be used,
	 *         <code>false</code> otherwise
	 */
	public final boolean shouldUseGetters() {
		return fUseGetters;
	}

	/**
	 * Should setter methods be used to resolve visibility issues?
	 *
	 * @return <code>true</code> if setter methods should be used,
	 *         <code>false</code> otherwise
	 */
	public final boolean shouldUseSetters() {
		return fUseSetters;
	}

	/**
	 * Returns a best guess for the name of the new target.
	 *
	 * @return a best guess for the name
	 */
	protected String suggestTargetName() {
		try {
			final String[] candidates= StubUtility.getArgumentNameSuggestions(fMethod.getDeclaringType(), computeReservedIdentifiers());
			if (candidates.length > 0) {
				if (candidates[0].indexOf('$') < 0)
					return candidates[0];
			}
		} catch (JavaModelException exception) {
			JavaPlugin.log(exception);
		}
		return "arg"; //$NON-NLS-1$
	}
}

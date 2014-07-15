/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.structure.constraints;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.CompilationUnitRange;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ConstraintVariable2;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

/**
 * Type constraints creator to determine the necessary constraints to replace type occurrences by a given super type.
 *
 * @since 3.1
 */
public final class SuperTypeConstraintsCreator extends HierarchicalASTVisitor {

	/** The constraint variable property */
	private static final String PROPERTY_CONSTRAINT_VARIABLE= "cv"; //$NON-NLS-1$

	/**
	 * Returns the original methods of the method hierarchy of the specified method.
	 *
	 * @param binding the method binding
	 * @param type the current type
	 * @param originals the original methods which have already been found (element type: <code>IMethodBinding</code>)
	 * @param implementations <code>true</code> to favor implementation methods, <code>false</code> otherwise
	 */
	private static void getOriginalMethods(final IMethodBinding binding, final ITypeBinding type, final Collection<IMethodBinding> originals, final boolean implementations) {
		final ITypeBinding ancestor= type.getSuperclass();
		if (!implementations) {
			final ITypeBinding[] types= type.getInterfaces();
			for (int index= 0; index < types.length; index++)
				getOriginalMethods(binding, types[index], originals, implementations);
			if (ancestor != null)
				getOriginalMethods(binding, ancestor, originals, implementations);
		}
		if (implementations && ancestor != null)
			getOriginalMethods(binding, ancestor, originals, implementations);
		final IMethodBinding[] methods= type.getDeclaredMethods();
		IMethodBinding method= null;
		for (int index= 0; index < methods.length; index++) {
			method= methods[index];
			if (!binding.getKey().equals(method.getKey())) {
				boolean match= false;
				IMethodBinding current= null;
				for (final Iterator<IMethodBinding> iterator= originals.iterator(); iterator.hasNext();) {
					current= iterator.next();
					if (Bindings.isSubsignature(method, current))
						match= true;
				}
				if (!match && Bindings.isSubsignature(binding, method))
					originals.add(method);
			}
		}
	}

	/** The current method declarations being processed (element type: <code>MethodDeclaration</code>) */
	private final Stack<MethodDeclaration> fCurrentMethods= new Stack<MethodDeclaration>();

	/** Should instanceof expressions be rewritten? */
	private final boolean fInstanceOf;

	/** The type constraint model to solve */
	private final SuperTypeConstraintsModel fModel;

	/**
	 * Creates a new super type constraints creator.
	 *
	 * @param model the model to create the type constraints for
	 * @param instanceofs <code>true</code> to rewrite instanceof expressions, <code>false</code> otherwise
	 */
	public SuperTypeConstraintsCreator(final SuperTypeConstraintsModel model, final boolean instanceofs) {
		Assert.isNotNull(model);

		fModel= model;
		fInstanceOf= instanceofs;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.ArrayAccess)
	 */
	@Override
	public final void endVisit(final ArrayAccess node) {
		node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, node.getArray().getProperty(PROPERTY_CONSTRAINT_VARIABLE));
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.ArrayCreation)
	 */
	@Override
	public final void endVisit(final ArrayCreation node) {
		final ConstraintVariable2 ancestor= (ConstraintVariable2) node.getType().getProperty(PROPERTY_CONSTRAINT_VARIABLE);
		node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, ancestor);
		final ArrayInitializer initializer= node.getInitializer();
		if (initializer != null) {
			final ConstraintVariable2 descendant= (ConstraintVariable2) initializer.getProperty(PROPERTY_CONSTRAINT_VARIABLE);
			if (descendant != null)
				fModel.createSubtypeConstraint(descendant, ancestor);
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.ArrayInitializer)
	 */
	@Override
	public final void endVisit(final ArrayInitializer node) {
		final ITypeBinding binding= node.resolveTypeBinding();
		if (binding != null && binding.isArray()) {
			final ConstraintVariable2 ancestor= fModel.createIndependentTypeVariable(binding.getElementType());
			if (ancestor != null) {
				node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, ancestor);
				Expression expression= null;
				ConstraintVariable2 descendant= null;
				final List<Expression> expressions= node.expressions();
				for (int index= 0; index < expressions.size(); index++) {
					expression= expressions.get(index);
					descendant= (ConstraintVariable2) expression.getProperty(PROPERTY_CONSTRAINT_VARIABLE);
					if (descendant != null)
						fModel.createSubtypeConstraint(descendant, ancestor);
				}
			}
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.ArrayType)
	 */
	@Override
	public final void endVisit(final ArrayType node) {
		Type elementType= node.getElementType();
		final ConstraintVariable2 variable= fModel.createTypeVariable(elementType);
		if (variable != null) {
			elementType.setProperty(PROPERTY_CONSTRAINT_VARIABLE, variable);
			node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, variable);
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.Assignment)
	 */
	@Override
	public final void endVisit(final Assignment node) {
		final ConstraintVariable2 ancestor= (ConstraintVariable2) node.getLeftHandSide().getProperty(PROPERTY_CONSTRAINT_VARIABLE);
		final ConstraintVariable2 descendant= (ConstraintVariable2) node.getRightHandSide().getProperty(PROPERTY_CONSTRAINT_VARIABLE);
		node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, ancestor);
		if (ancestor != null && descendant != null)
			fModel.createSubtypeConstraint(descendant, ancestor);
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.CastExpression)
	 */
	@Override
	public final void endVisit(final CastExpression node) {
		final ConstraintVariable2 first= (ConstraintVariable2) node.getType().getProperty(PROPERTY_CONSTRAINT_VARIABLE);
		if (first != null) {
			node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, first);
			final ConstraintVariable2 second= (ConstraintVariable2) node.getExpression().getProperty(PROPERTY_CONSTRAINT_VARIABLE);
			if (second != null)
				fModel.createCastVariable(node, second);
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.CatchClause)
	 */
	@Override
	public final void endVisit(final CatchClause node) {
		final SingleVariableDeclaration declaration= node.getException();
		if (declaration != null) {
			final ConstraintVariable2 descendant= (ConstraintVariable2) declaration.getProperty(PROPERTY_CONSTRAINT_VARIABLE);
			if (descendant != null) {
				final ITypeBinding binding= node.getAST().resolveWellKnownType("java.lang.Throwable"); //$NON-NLS-1$
				if (binding != null) {
					final ConstraintVariable2 ancestor= fModel.createImmutableTypeVariable(binding);
					if (ancestor != null)
						fModel.createSubtypeConstraint(descendant, ancestor);
				}
			}
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.ClassInstanceCreation)
	 */
	@Override
	public final void endVisit(final ClassInstanceCreation node) {
		final IMethodBinding binding= node.resolveConstructorBinding();
		if (binding != null) {
			endVisit(node.arguments(), binding);
			ConstraintVariable2 variable= null;
			final AnonymousClassDeclaration declaration= node.getAnonymousClassDeclaration();
			if (declaration != null) {
				final ITypeBinding type= declaration.resolveBinding();
				if (type != null)
					variable= fModel.createImmutableTypeVariable(type);
			} else {
				final ITypeBinding type= node.resolveTypeBinding();
				if (type != null)
					variable= fModel.createImmutableTypeVariable(type);
			}
			if (variable != null)
				node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, variable);
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.ConditionalExpression)
	 */
	@Override
	public final void endVisit(final ConditionalExpression node) {
		ConstraintVariable2 thenVariable= null;
		ConstraintVariable2 elseVariable= null;
		final Expression thenExpression= node.getThenExpression();
		if (thenExpression != null)
			thenVariable= (ConstraintVariable2) thenExpression.getProperty(PROPERTY_CONSTRAINT_VARIABLE);
		final Expression elseExpression= node.getElseExpression();
		if (elseExpression != null)
			elseVariable= (ConstraintVariable2) elseExpression.getProperty(PROPERTY_CONSTRAINT_VARIABLE);
		ITypeBinding binding= node.resolveTypeBinding();
		if (binding != null) {
			if (binding.isArray())
				binding= binding.getElementType();
			final ConstraintVariable2 ancestor= fModel.createIndependentTypeVariable(binding);
			if (ancestor != null) {
				node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, ancestor);
				if (thenVariable != null)
					fModel.createSubtypeConstraint(thenVariable, ancestor);
				if (elseVariable != null)
					fModel.createSubtypeConstraint(elseVariable, ancestor);
				if (thenVariable != null && elseVariable != null)
					fModel.createConditionalTypeConstraint(ancestor, thenVariable, elseVariable);
			}
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.ConstructorInvocation)
	 */
	@Override
	public final void endVisit(final ConstructorInvocation node) {
		final IMethodBinding binding= node.resolveConstructorBinding();
		if (binding != null)
			endVisit(node.arguments(), binding);
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.FieldAccess)
	 */
	@Override
	public final void endVisit(final FieldAccess node) {
		final IVariableBinding binding= node.resolveFieldBinding();
		if (binding != null)
			endVisit(binding, node.getExpression(), node);
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.FieldDeclaration)
	 */
	@Override
	public final void endVisit(final FieldDeclaration node) {
		endVisit(node.fragments(), node.getType(), node);
	}

	/**
	 * End of visit the specified method declaration.
	 *
	 * @param binding the method binding
	 */
	private void endVisit(final IMethodBinding binding) {
		IMethodBinding method= null;
		ConstraintVariable2 ancestor= null;
		final ConstraintVariable2 descendant= fModel.createReturnTypeVariable(binding);
		if (descendant != null) {
			final Collection<IMethodBinding> originals= getOriginalMethods(binding);
			for (final Iterator<IMethodBinding> iterator= originals.iterator(); iterator.hasNext();) {
				method= iterator.next();
				if (!method.getKey().equals(binding.getKey())) {
					ancestor= fModel.createReturnTypeVariable(method);
					if (ancestor != null)
						fModel.createCovariantTypeConstraint(descendant, ancestor);
				}
			}
		}
	}

	/**
	 * End of visit the specified method invocation.
	 *
	 * @param binding the method binding
	 * @param descendant the constraint variable of the invocation expression
	 */
	private void endVisit(final IMethodBinding binding, final ConstraintVariable2 descendant) {
		ITypeBinding declaring= null;
		IMethodBinding method= null;
		final Collection<IMethodBinding> originals= getOriginalMethods(binding);
		for (final Iterator<IMethodBinding> iterator= originals.iterator(); iterator.hasNext();) {
			method= iterator.next();
			declaring= method.getDeclaringClass();
			if (declaring != null) {
				final ConstraintVariable2 ancestor= fModel.createDeclaringTypeVariable(declaring);
				if (ancestor != null)
					fModel.createSubtypeConstraint(descendant, ancestor);
			}
		}
	}

	/**
	 * End of visit the thrown exception
	 *
	 * @param binding the type binding of the thrown exception
	 * @param node the exception name node
	 */
	private void endVisit(final ITypeBinding binding, final Name node) {
		final ConstraintVariable2 variable= fModel.createExceptionVariable(node);
		if (variable != null)
			node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, variable);
	}

	/**
	 * End of visit the field access.
	 *
	 * @param binding the variable binding
	 * @param qualifier the qualifier expression, or <code>null</code>
	 * @param access the access expression
	 */
	private void endVisit(final IVariableBinding binding, final Expression qualifier, final Expression access) {
		access.setProperty(PROPERTY_CONSTRAINT_VARIABLE, fModel.createVariableVariable(binding));
		if (qualifier != null) {
			final ITypeBinding type= binding.getDeclaringClass();
			if (type != null) {
				// array.length does not have a declaring class
				final ConstraintVariable2 ancestor= fModel.createDeclaringTypeVariable(type);
				if (ancestor != null) {
					final ConstraintVariable2 descendant= (ConstraintVariable2) qualifier.getProperty(PROPERTY_CONSTRAINT_VARIABLE);
					if (descendant != null)
						fModel.createSubtypeConstraint(descendant, ancestor);
				}
			}
		}
	}

	/**
	 * End of visit the method argument list.
	 *
	 * @param arguments the arguments (element type: <code>Expression</code>)
	 * @param binding the method binding
	 */
	private void endVisit(final List<Expression> arguments, final IMethodBinding binding) {
		Expression expression= null;
		ConstraintVariable2 ancestor= null;
		ConstraintVariable2 descendant= null;
		for (int index= 0; index < arguments.size(); index++) {
			expression= arguments.get(index);
			descendant= (ConstraintVariable2) expression.getProperty(PROPERTY_CONSTRAINT_VARIABLE);
			ancestor= fModel.createMethodParameterVariable(binding, index);
			if (ancestor != null && descendant != null)
				fModel.createSubtypeConstraint(descendant, ancestor);
		}
	}

	/**
	 * End of visit the variable declaration fragment list.
	 *
	 * @param fragments the fragments (element type: <code>VariableDeclarationFragment</code>)
	 * @param type the type of the fragments
	 * @param parent the parent of the fragment list
	 */
	private void endVisit(final List<VariableDeclarationFragment> fragments, final Type type, final ASTNode parent) {
		final ConstraintVariable2 ancestor= (ConstraintVariable2) type.getProperty(PROPERTY_CONSTRAINT_VARIABLE);
		if (ancestor != null) {
			IVariableBinding binding= null;
			ConstraintVariable2 descendant= null;
			VariableDeclarationFragment fragment= null;
			for (int index= 0; index < fragments.size(); index++) {
				fragment= fragments.get(index);
				descendant= (ConstraintVariable2) fragment.getProperty(PROPERTY_CONSTRAINT_VARIABLE);
				if (descendant != null)
					fModel.createSubtypeConstraint(descendant, ancestor);
				binding= fragment.resolveBinding();
				if (binding != null) {
					descendant= fModel.createVariableVariable(binding);
					if (descendant != null)
						fModel.createEqualityConstraint(ancestor, descendant);
				}
			}
			parent.setProperty(PROPERTY_CONSTRAINT_VARIABLE, ancestor);
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.MethodDeclaration)
	 */
	@Override
	public final void endVisit(final MethodDeclaration node) {
		fCurrentMethods.pop();
		final IMethodBinding binding= node.resolveBinding();
		if (binding != null) {
			if (!binding.isConstructor()) {
				final Type type= node.getReturnType2();
				if (type != null) {
					final ConstraintVariable2 first= fModel.createReturnTypeVariable(binding);
					final ConstraintVariable2 second= (ConstraintVariable2) type.getProperty(PROPERTY_CONSTRAINT_VARIABLE);
					if (first != null) {
						if (second != null)
							fModel.createEqualityConstraint(first, second);
						endVisit(binding);
					}
				}
			}
			ConstraintVariable2 ancestor= null;
			ConstraintVariable2 descendant= null;
			IVariableBinding variable= null;
			final List<SingleVariableDeclaration> parameters= node.parameters();
			if (!parameters.isEmpty()) {
				final Collection<IMethodBinding> originals= getOriginalMethods(binding);
				SingleVariableDeclaration declaration= null;
				for (int index= 0; index < parameters.size(); index++) {
					declaration= parameters.get(index);
					ancestor= fModel.createMethodParameterVariable(binding, index);
					if (ancestor != null) {
						descendant= (ConstraintVariable2) declaration.getType().getProperty(PROPERTY_CONSTRAINT_VARIABLE);
						if (descendant != null)
							fModel.createEqualityConstraint(descendant, ancestor);
						variable= declaration.resolveBinding();
						if (variable != null) {
							descendant= fModel.createVariableVariable(variable);
							if (descendant != null)
								fModel.createEqualityConstraint(ancestor, descendant);
						}
						IMethodBinding method= null;
						for (final Iterator<IMethodBinding> iterator= originals.iterator(); iterator.hasNext();) {
							method= iterator.next();
							if (!method.getKey().equals(binding.getKey())) {
								descendant= fModel.createMethodParameterVariable(method, index);
								if (descendant != null)
									fModel.createEqualityConstraint(ancestor, descendant);
							}
						}
					}
				}
			}
			final List<Type> exceptions= node.thrownExceptionTypes();
			if (!exceptions.isEmpty()) {
				final ITypeBinding throwable= node.getAST().resolveWellKnownType("java.lang.Throwable"); //$NON-NLS-1$
				if (throwable != null) {
					ancestor= fModel.createImmutableTypeVariable(throwable);
					if (ancestor != null) {
						Type exception= null;
						for (int index= 0; index < exceptions.size(); index++) {
							exception= exceptions.get(index);
							descendant= (ConstraintVariable2) exception.getProperty(PROPERTY_CONSTRAINT_VARIABLE);
							if (descendant != null)
								fModel.createSubtypeConstraint(descendant, ancestor);
						}
					}
				}
			}
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.MethodInvocation)
	 */
	@Override
	public final void endVisit(final MethodInvocation node) {
		final IMethodBinding binding= node.resolveMethodBinding();
		if (binding != null) {
			endVisit(node, binding);
			endVisit(node.arguments(), binding);
			final Expression expression= node.getExpression();
			if (expression != null) {
				final ConstraintVariable2 descendant= (ConstraintVariable2) expression.getProperty(PROPERTY_CONSTRAINT_VARIABLE);
				if (descendant != null)
					endVisit(binding, descendant);
			}
		}
	}

	/**
	 * End of visit the return type of a method invocation.
	 *
	 * @param invocation the method invocation
	 * @param binding the method binding
	 */
	private void endVisit(final MethodInvocation invocation, final IMethodBinding binding) {
		if (!binding.isConstructor()) {
			final ConstraintVariable2 variable= fModel.createReturnTypeVariable(binding);
			if (variable != null)
				invocation.setProperty(PROPERTY_CONSTRAINT_VARIABLE, variable);
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.NullLiteral)
	 */
	@Override
	public final void endVisit(final NullLiteral node) {
		node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, fModel.createImmutableTypeVariable(node.resolveTypeBinding()));
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.ParenthesizedExpression)
	 */
	@Override
	public final void endVisit(final ParenthesizedExpression node) {
		node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, node.getExpression().getProperty(PROPERTY_CONSTRAINT_VARIABLE));
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.QualifiedName)
	 */
	@Override
	public final void endVisit(final QualifiedName node) {
		final ASTNode parent= node.getParent();
		final Name qualifier= node.getQualifier();
		IBinding binding= qualifier.resolveBinding();
		if (binding instanceof ITypeBinding) {
			final ConstraintVariable2 variable= fModel.createTypeVariable((ITypeBinding) binding, new CompilationUnitRange(RefactoringASTParser.getCompilationUnit(node), new SourceRange(qualifier.getStartPosition(), qualifier.getLength())));
			if (variable != null)
				qualifier.setProperty(PROPERTY_CONSTRAINT_VARIABLE, variable);
		}
		binding= node.getName().resolveBinding();
		if (binding instanceof IVariableBinding && !(parent instanceof ImportDeclaration))
			endVisit((IVariableBinding) binding, qualifier, node);
		else if (binding instanceof ITypeBinding && parent instanceof MethodDeclaration)
			endVisit((ITypeBinding) binding, node);
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.ReturnStatement)
	 */
	@Override
	public final void endVisit(final ReturnStatement node) {
		final Expression expression= node.getExpression();
		if (expression != null) {
			final ConstraintVariable2 descendant= (ConstraintVariable2) expression.getProperty(PROPERTY_CONSTRAINT_VARIABLE);
			if (descendant != null) {
				final MethodDeclaration declaration= fCurrentMethods.peek();
				if (declaration != null) {
					final IMethodBinding binding= declaration.resolveBinding();
					if (binding != null) {
						final ConstraintVariable2 ancestor= fModel.createReturnTypeVariable(binding);
						if (ancestor != null) {
							node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, ancestor);
							fModel.createSubtypeConstraint(descendant, ancestor);
						}
					}
				}
			}
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.SimpleName)
	 */
	@Override
	public final void endVisit(final SimpleName node) {
		final ASTNode parent= node.getParent();
		if (!(parent instanceof ImportDeclaration) && !(parent instanceof PackageDeclaration) && !(parent instanceof AbstractTypeDeclaration)) {
			final IBinding binding= node.resolveBinding();
			if (binding instanceof IVariableBinding && !(parent instanceof MethodDeclaration))
				endVisit((IVariableBinding) binding, null, node);
			else if (binding instanceof ITypeBinding && parent instanceof MethodDeclaration)
				endVisit((ITypeBinding) binding, node);
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.SingleVariableDeclaration)
	 */
	@Override
	public final void endVisit(final SingleVariableDeclaration node) {
		final ConstraintVariable2 ancestor= (ConstraintVariable2) node.getType().getProperty(PROPERTY_CONSTRAINT_VARIABLE);
		if (ancestor != null) {
			node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, ancestor);
			final Expression expression= node.getInitializer();
			if (expression != null) {
				final ConstraintVariable2 descendant= (ConstraintVariable2) expression.getProperty(PROPERTY_CONSTRAINT_VARIABLE);
				if (descendant != null)
					fModel.createSubtypeConstraint(descendant, ancestor);
			}
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.EnhancedForStatement)
	 */
	@Override
	public void endVisit(EnhancedForStatement node) {
		SingleVariableDeclaration parameter= node.getParameter();
		final ConstraintVariable2 ancestor= (ConstraintVariable2) parameter.getType().getProperty(PROPERTY_CONSTRAINT_VARIABLE);
		if (ancestor != null) {
			IVariableBinding binding= parameter.resolveBinding();
			if (binding != null) {
				ConstraintVariable2 descendant= fModel.createVariableVariable(binding);
				if (descendant != null) {
					fModel.createEqualityConstraint(ancestor, descendant);
				}
			}
			ITypeBinding collectionType= node.getExpression().resolveTypeBinding();
			if (collectionType != null && collectionType.isArray()) {
				ConstraintVariable2 descendant= (ConstraintVariable2) node.getExpression().getProperty(PROPERTY_CONSTRAINT_VARIABLE);
				if (descendant != null) {
					fModel.createSubtypeConstraint(descendant, ancestor);
				}
			}
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.SuperConstructorInvocation)
	 */
	@Override
	public final void endVisit(final SuperConstructorInvocation node) {
		final IMethodBinding binding= node.resolveConstructorBinding();
		if (binding != null)
			endVisit(node.arguments(), binding);
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.SuperFieldAccess)
	 */
	@Override
	public final void endVisit(final SuperFieldAccess node) {
		final Name name= node.getName();
		final IBinding binding= name.resolveBinding();
		if (binding instanceof IVariableBinding)
			endVisit((IVariableBinding) binding, null, node);
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.SuperMethodInvocation)
	 */
	@Override
	public final void endVisit(final SuperMethodInvocation node) {
		final IMethodBinding superBinding= node.resolveMethodBinding();
		if (superBinding != null) {
			endVisit(node.arguments(), superBinding);
			final MethodDeclaration declaration= fCurrentMethods.peek();
			if (declaration != null) {
				final IMethodBinding subBinding= declaration.resolveBinding();
				if (subBinding != null) {
					final ConstraintVariable2 ancestor= fModel.createReturnTypeVariable(superBinding);
					if (ancestor != null) {
						node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, ancestor);
						final ConstraintVariable2 descendant= fModel.createReturnTypeVariable(subBinding);
						if (descendant != null)
							fModel.createEqualityConstraint(descendant, ancestor);
					}
				}
			}
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.ThisExpression)
	 */
	@Override
	public final void endVisit(final ThisExpression node) {
		final ITypeBinding binding= node.resolveTypeBinding();
		if (binding != null)
			node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, fModel.createDeclaringTypeVariable(binding));
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.Type)
	 */
	@Override
	public final void endVisit(final Type node) {
		final ASTNode parent= node.getParent();
		if (!(parent instanceof AbstractTypeDeclaration) && !(parent instanceof ClassInstanceCreation) && !(parent instanceof TypeLiteral) && (!(parent instanceof InstanceofExpression) || fInstanceOf))
			node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, fModel.createTypeVariable(node));
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.VariableDeclarationExpression)
	 */
	@Override
	public final void endVisit(final VariableDeclarationExpression node) {
		endVisit(node.fragments(), node.getType(), node);
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.VariableDeclarationFragment)
	 */
	@Override
	public final void endVisit(final VariableDeclarationFragment node) {
		final Expression initializer= node.getInitializer();
		if (initializer != null)
			node.setProperty(PROPERTY_CONSTRAINT_VARIABLE, initializer.getProperty(PROPERTY_CONSTRAINT_VARIABLE));
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#endVisit(org.eclipse.jdt.core.dom.VariableDeclarationStatement)
	 */
	@Override
	public final void endVisit(final VariableDeclarationStatement node) {
		endVisit(node.fragments(), node.getType(), node);
	}

	/**
	 * Returns the original methods of the method hierarchy of the specified method.
	 *
	 * @param binding the method binding
	 * @return the original methods (element type: <code>IMethodBinding</code>)
	 */
	private Collection<IMethodBinding> getOriginalMethods(final IMethodBinding binding) {
		final Collection<IMethodBinding> originals= new ArrayList<IMethodBinding>();
		final ITypeBinding type= binding.getDeclaringClass();
		getOriginalMethods(binding, type, originals, false);
		getOriginalMethods(binding, type, originals, true);
		if (originals.isEmpty())
			originals.add(binding);
		return originals;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#visit(org.eclipse.jdt.core.dom.AnnotationTypeDeclaration)
	 */
	@Override
	public final boolean visit(final AnnotationTypeDeclaration node) {
		return false;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#visit(org.eclipse.jdt.core.dom.Comment)
	 */
	@Override
	public final boolean visit(final Comment node) {
		return false;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#visit(org.eclipse.jdt.core.dom.ImportDeclaration)
	 */
	@Override
	public final boolean visit(final ImportDeclaration node) {
		return false;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#visit(org.eclipse.jdt.core.dom.MethodDeclaration)
	 */
	@Override
	public final boolean visit(final MethodDeclaration node) {
		fCurrentMethods.push(node);
		return super.visit(node);
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#visit(org.eclipse.jdt.core.dom.PackageDeclaration)
	 */
	@Override
	public final boolean visit(final PackageDeclaration node) {
		return false;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#visit(org.eclipse.jdt.core.dom.ThisExpression)
	 */
	@Override
	public final boolean visit(final ThisExpression node) {
		return false;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor#visit(org.eclipse.jdt.core.dom.Type)
	 */
	@Override
	public final boolean visit(final Type node) {
		return false;
	}
}

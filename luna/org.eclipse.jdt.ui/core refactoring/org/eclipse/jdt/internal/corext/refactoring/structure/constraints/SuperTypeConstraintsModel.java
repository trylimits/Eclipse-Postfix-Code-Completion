/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.structure.constraints;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.Type;

import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.CompilationUnitRange;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TType;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TypeEnvironment;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.CastVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ConstraintVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ITypeConstraint2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ImmutableTypeVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.IndependentTypeVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ParameterTypeVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ReturnTypeVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.SubTypeConstraint2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.TypeEquivalenceSet;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.TypeVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.VariableVariable2;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;


/**
 * Type constraints model to hold all type constraints to replace type occurrences by a given supertype.
 *
 * @since 3.1
 */
public final class SuperTypeConstraintsModel {

	/** Customized implementation of a hash set 
	 * @param <E> the element type */
	private static class HashedSet<E> extends AbstractSet<E> {

		/** The backing hash map */
		private final Map<E, E> fImplementation= new HashMap<E, E>();

		/*
		 * @see java.util.AbstractCollection#add(java.lang.Object)
		 */
		@Override
		public final boolean add(final E object) {
			return fImplementation.put(object, object) == null;
		}

		/**
		 * Attempts to add the specified object to this set.
		 *
		 * @param object the object to add
		 * @return An already existing object considered equal to the specified one, or the newly added object
		 */
		public final E addExisting(final E object) {
			final E result= fImplementation.get(object);
			if (result != null)
				return result;
			fImplementation.put(object, object);
			return object;
		}

		/*
		 * @see java.util.AbstractCollection#clear()
		 */
		@Override
		public final void clear() {
			fImplementation.clear();
		}

		/*
		 * @see java.util.AbstractCollection#contains(java.lang.Object)
		 */
		@Override
		public final boolean contains(final Object object) {
			return fImplementation.containsKey(object);
		}

		/*
		 * @see java.util.AbstractCollection#isEmpty()
		 */
		@Override
		public final boolean isEmpty() {
			return fImplementation.isEmpty();
		}

		/*
		 * @see java.util.AbstractCollection#iterator()
		 */
		@Override
		public final Iterator<E> iterator() {
			return fImplementation.keySet().iterator();
		}

		/*
		 * @see java.util.AbstractCollection#remove(java.lang.Object)
		 */
		@Override
		public final boolean remove(final Object object) {
			return fImplementation.remove(object) == object;
		}

		/*
		 * @see java.util.AbstractCollection#size()
		 */
		@Override
		public final int size() {
			return fImplementation.size();
		}
	}

	/** The usage data */
	private static final String DATA_USAGE= "us"; //$NON-NLS-1$

	/** Maximal number of TTypes */
	private static final int MAX_CACHE= 64;

	/**
	 * Returns the usage of the specified constraint variable.
	 *
	 * @param variable the constraint variable
	 * @return the usage of the constraint variable (element type: <code>ITypeConstraint2</code>)
	 */
	public static Collection<ITypeConstraint2> getVariableUsage(final ConstraintVariable2 variable) {
		final Object data= variable.getData(DATA_USAGE);
		if (data == null) {
			return Collections.emptyList();
		} else if (data instanceof Collection) {
			@SuppressWarnings("unchecked")
			Collection<ITypeConstraint2> collection= (Collection<ITypeConstraint2>) data;
			return Collections.unmodifiableCollection(collection);
		} else {
			return Collections.singletonList((ITypeConstraint2) data);
		}
	}

	/**
	 * Is the type represented by the specified binding a constrained type?
	 *
	 * @param binding the binding to check, or <code>null</code>
	 * @return <code>true</code> if it is constrained, <code>false</code> otherwise
	 */
	public static boolean isConstrainedType(final ITypeBinding binding) {
		return binding != null && !binding.isSynthetic() && !binding.isPrimitive();
	}

	/**
	 * Sets the usage of the specified constraint variable.
	 *
	 * @param variable the constraint variable
	 * @param constraint the type constraint
	 */
	public static void setVariableUsage(final ConstraintVariable2 variable, ITypeConstraint2 constraint) {
		final Object data= variable.getData(DATA_USAGE);
		if (data == null) {
			variable.setData(DATA_USAGE, constraint);
		} else if (data instanceof Collection) {
			@SuppressWarnings("unchecked")
			Collection<ITypeConstraint2> collection= (Collection<ITypeConstraint2>) data;
			collection.add(constraint);
		} else {
			final Collection<Object> usage= new ArrayList<Object>(2);
			usage.add(data);
			usage.add(constraint);
			variable.setData(DATA_USAGE, usage);
		}
	}

	/** The cast variables (element type: <code>CastVariable2</code>) */
	private final Collection<CastVariable2> fCastVariables= new ArrayList<CastVariable2>();

	/** The compliance level */
	private int fCompliance= 3;

	/** The set of constraint variables (element type: <code>ConstraintVariable2</code>) */
	private final HashedSet<ConstraintVariable2> fConstraintVariables= new HashedSet<ConstraintVariable2>();

	/** The covariant type constraints (element type: <code>CovariantTypeConstraint</code>) */
	private final Collection<ITypeConstraint2> fCovariantTypeConstraints= new ArrayList<ITypeConstraint2>();

	/** The type environment to use */
	private TypeEnvironment fEnvironment;

	/** The subtype to replace */
	private final TType fSubType;

	/** The supertype as replacement */
	private final TType fSuperType;

	/** The TType cache */
	private Map<String, TType> fTTypeCache= new LinkedHashMap<String, TType>(MAX_CACHE, 0.75f, true) {

		private static final long serialVersionUID= 1L;

		@Override
		protected final boolean removeEldestEntry(Map.Entry<String, TType> entry) {
			return size() > MAX_CACHE;
		}
	};

	/** The set of type constraints (element type: <code>ITypeConstraint2</code>) */
	private final Set<ITypeConstraint2> fTypeConstraints= new HashSet<ITypeConstraint2>();

	/**
	 * Creates a new super type constraints model.
	 *
	 * @param environment the type environment
	 * @param subType the subtype to replace
	 * @param superType the supertype replacement
	 */
	public SuperTypeConstraintsModel(final TypeEnvironment environment, TType subType, TType superType) {
		fEnvironment= environment;
		fSubType= subType;
		fSuperType= superType;
	}

	/**
	 * Gets called when the creation of the model begins.
	 */
	public final void beginCreation() {
		// Do nothing right now
	}

	/**
	 * Creates a cast variable.
	 *
	 * @param expression the cast expression
	 * @param variable the associated constraint variable
	 * @return the created cast variable, or <code>null</code>
	 */
	public final ConstraintVariable2 createCastVariable(final CastExpression expression, final ConstraintVariable2 variable) {
		ITypeBinding binding= expression.resolveTypeBinding();
		if (binding.isArray())
			binding= binding.getElementType();
		if (isConstrainedType(binding)) {
			final CastVariable2 result= new CastVariable2(createTType(binding), new CompilationUnitRange(RefactoringASTParser.getCompilationUnit(expression), expression), variable);
			fCastVariables.add(result);
			return result;
		}
		return null;
	}

	/**
	 * Creates a conditional type constraint.
	 *
	 * @param expressionVariable the expression type constraint variable
	 * @param thenVariable the then type constraint variable
	 * @param elseVariable the else type constraint variable
	 */
	public final void createConditionalTypeConstraint(final ConstraintVariable2 expressionVariable, final ConstraintVariable2 thenVariable, final ConstraintVariable2 elseVariable) {
		final ITypeConstraint2 constraint= new ConditionalTypeConstraint(expressionVariable, thenVariable, elseVariable);
		if (!fTypeConstraints.contains(constraint)) {
			fTypeConstraints.add(constraint);
			setVariableUsage(expressionVariable, constraint);
			setVariableUsage(thenVariable, constraint);
			setVariableUsage(elseVariable, constraint);
		}
	}

	/**
	 * Creates a subtype constraint.
	 *
	 * @param descendant the descendant type constraint variable
	 * @param ancestor the ancestor type constraint variable
	 */
	public final void createCovariantTypeConstraint(final ConstraintVariable2 descendant, final ConstraintVariable2 ancestor) {
		final ITypeConstraint2 constraint= new CovariantTypeConstraint(descendant, ancestor);
		if (!fTypeConstraints.contains(constraint)) {
			fTypeConstraints.add(constraint);
			fCovariantTypeConstraints.add(constraint);
			setVariableUsage(descendant, constraint);
			setVariableUsage(ancestor, constraint);
		}
	}

	/**
	 * Creates a declaring type variable.
	 * <p>
	 * A declaring type variable stands for a type where something has been declared.
	 * </p>
	 *
	 * @param type the type binding
	 * @return the created declaring type variable
	 */
	public final ConstraintVariable2 createDeclaringTypeVariable(ITypeBinding type) {
		if (type.isArray())
			type= type.getElementType();
		type= type.getTypeDeclaration();
		return fConstraintVariables.addExisting(new ImmutableTypeVariable2(createTType(type)));
	}

	/**
	 * Creates an equality constraint.
	 *
	 * @param left the left typeconstraint variable
	 * @param right the right typeconstraint variable
	 */
	public final void createEqualityConstraint(final ConstraintVariable2 left, final ConstraintVariable2 right) {
		if (left != null && right != null) {
			final TypeEquivalenceSet first= left.getTypeEquivalenceSet();
			final TypeEquivalenceSet second= right.getTypeEquivalenceSet();
			if (first == null) {
				if (second == null) {
					final TypeEquivalenceSet set= new TypeEquivalenceSet(left, right);
					left.setTypeEquivalenceSet(set);
					right.setTypeEquivalenceSet(set);
				} else {
					second.add(left);
					left.setTypeEquivalenceSet(second);
				}
			} else {
				if (second == null) {
					first.add(right);
					right.setTypeEquivalenceSet(first);
				} else if (first == second)
					return;
				else {
					final ConstraintVariable2[] variables= second.getContributingVariables();
					first.addAll(variables);
					for (int index= 0; index < variables.length; index++)
						variables[index].setTypeEquivalenceSet(first);
				}
			}
		}
	}

	/**
	 * Creates an exception variable.
	 *
	 * @param name the name of the thrown exception
	 * @return the created exception variable, or <code>null</code>
	 */
	public final ConstraintVariable2 createExceptionVariable(final Name name) {
		final ITypeBinding binding= name.resolveTypeBinding();
		if (isConstrainedType(binding))
			return fConstraintVariables.addExisting(new TypeVariable2(createTType(binding), new CompilationUnitRange(RefactoringASTParser.getCompilationUnit(name), name)));
		return null;
	}

	/**
	 * Creates an immutable type variable.
	 *
	 * @param type the type binding
	 * @return the created plain type variable, or <code>null</code>
	 */
	public final ConstraintVariable2 createImmutableTypeVariable(ITypeBinding type) {
		if (type.isArray())
			type= type.getElementType();
		if (isConstrainedType(type))
			return fConstraintVariables.addExisting(new ImmutableTypeVariable2(createTType(type)));
		return null;
	}

	/**
	 * Creates an independent type variable.
	 * <p>
	 * An independant type variable stands for an arbitrary type.
	 * </p>
	 *
	 * @param type the type binding
	 * @return the created independant type variable, or <code>null</code>
	 */
	public final ConstraintVariable2 createIndependentTypeVariable(ITypeBinding type) {
		if (type.isArray())
			type= type.getElementType();
		if (isConstrainedType(type))
			return fConstraintVariables.addExisting(new IndependentTypeVariable2(createTType(type)));
		return null;
	}

	/**
	 * Creates a new method parameter variable.
	 *
	 * @param method the method binding
	 * @param index the index of the parameter
	 * @return the created method parameter variable, or <code>null</code>
	 */
	public final ConstraintVariable2 createMethodParameterVariable(final IMethodBinding method, final int index) {
		final ITypeBinding[] parameters= method.getParameterTypes();
		if (parameters.length < 1)
			return null;
		ITypeBinding binding= parameters[Math.min(index, parameters.length - 1)];
		if (binding.isArray())
			binding= binding.getElementType();
		if (isConstrainedType(binding)) {
			ConstraintVariable2 variable= null;
			final TType type= createTType(binding);
			if (method.getDeclaringClass().isFromSource())
				variable= new ParameterTypeVariable2(type, index, method.getMethodDeclaration());
			else
				variable= new ImmutableTypeVariable2(type);
			return fConstraintVariables.addExisting(variable);
		}
		return null;
	}

	/**
	 * Creates a new return type variable.
	 *
	 * @param method the method binding
	 * @return the created return type variable, or <code>null</code>
	 */
	public final ConstraintVariable2 createReturnTypeVariable(final IMethodBinding method) {
		if (!method.isConstructor()) {
			ITypeBinding binding= method.getReturnType();
			if (binding != null && binding.isArray())
				binding= binding.getElementType();
			if (binding != null && isConstrainedType(binding)) {
				ConstraintVariable2 variable= null;
				final TType type= createTType(binding);
				if (method.getDeclaringClass().isFromSource())
					variable= new ReturnTypeVariable2(type, method);
				else
					variable= new ImmutableTypeVariable2(type);
				return fConstraintVariables.addExisting(variable);
			}
		}
		return null;
	}

	/**
	 * Creates a subtype constraint.
	 *
	 * @param descendant the descendant type constraint variable
	 * @param ancestor the ancestor type constraint variable
	 */
	public final void createSubtypeConstraint(final ConstraintVariable2 descendant, final ConstraintVariable2 ancestor) {
		final ITypeConstraint2 constraint= new SubTypeConstraint2(descendant, ancestor);
		if (!fTypeConstraints.contains(constraint)) {
			fTypeConstraints.add(constraint);
			setVariableUsage(descendant, constraint);
			setVariableUsage(ancestor, constraint);
		}
	}

	/**
	 * Creates a new TType for the corresponding binding.
	 *
	 * @param binding The type binding
	 * @return The corresponding TType
	 */
	public final TType createTType(final ITypeBinding binding) {
		final String key= binding.getKey();
		final TType cached= fTTypeCache.get(key);
		if (cached != null)
			return cached;
		final TType type= fEnvironment.create(binding);
		fTTypeCache.put(key, type);
		return type;
	}

	/**
	 * Creates a type variable.
	 *
	 * @param type the type binding
	 * @param range the compilation unit range
	 * @return the created type variable, or <code>null</code>
	 */
	public final ConstraintVariable2 createTypeVariable(ITypeBinding type, final CompilationUnitRange range) {
		if (type.isArray())
			type= type.getElementType();
		if (isConstrainedType(type))
			return fConstraintVariables.addExisting(new TypeVariable2(createTType(type), range));
		return null;
	}

	/**
	 * Creates a type variable.
	 *
	 * @param type the type
	 * @return the created type variable, or <code>null</code>
	 */
	public final ConstraintVariable2 createTypeVariable(final Type type) {
		ITypeBinding binding= type.resolveBinding();
		if (binding != null) {
			if (binding.isArray())
				binding= binding.getElementType();
			if (isConstrainedType(binding))
				return fConstraintVariables.addExisting(new TypeVariable2(createTType(binding), new CompilationUnitRange(RefactoringASTParser.getCompilationUnit(type), type)));
		}
		return null;
	}

	/**
	 * Creates a variable type variable.
	 *
	 * @param binding the variable binding
	 * @return the created variable variable, or <code>null</code>
	 */
	public final ConstraintVariable2 createVariableVariable(final IVariableBinding binding) {
		ITypeBinding type= binding.getType();
		if (type.isArray())
			type= type.getElementType();
		if (isConstrainedType(type)) {
			ConstraintVariable2 variable= null;
			final IVariableBinding declaration= binding.getVariableDeclaration();
			if (declaration.isField()) {
				final ITypeBinding declaring= declaration.getDeclaringClass();
				if (!declaring.isFromSource())
					variable= new ImmutableTypeVariable2(createTType(type));
			} else {
				final IMethodBinding declaring= declaration.getDeclaringMethod();
				if (declaring != null && !declaring.getDeclaringClass().isFromSource())
					variable= new ImmutableTypeVariable2(createTType(type));
			}
			if (variable == null)
				variable= new VariableVariable2(createTType(type), declaration);
			return fConstraintVariables.addExisting(variable);
		}
		return null;
	}

	/**
	 * Gets called when the creation of the model ends.
	 */
	public final void endCreation() {
		fEnvironment= null;
		fTTypeCache= null;
	}

	/**
	 * Returns the cast variables of this model.
	 *
	 * @return the cast variables (element type: <code>CastVariable2</code>)
	 */
	public final Collection<CastVariable2> getCastVariables() {
		return Collections.unmodifiableCollection(fCastVariables);
	}

	/**
	 * Returns the compliance level to use.
	 *
	 * @return the compliance level
	 */
	public final int getCompliance() {
		return fCompliance;
	}

	/**
	 * Returns the constraint variables of this model.
	 *
	 * @return the constraint variables (element type: <code>ConstraintVariable2</code>)
	 */
	public final Collection<ConstraintVariable2> getConstraintVariables() {
		return Collections.unmodifiableCollection(fConstraintVariables);
	}

	/**
	 * Returns the subtype to be replaced.
	 *
	 * @return the subtype to be replaced
	 */
	public final TType getSubType() {
		return fSubType;
	}

	/**
	 * Returns the supertype as replacement.
	 *
	 * @return the supertype as replacement
	 */
	public final TType getSuperType() {
		return fSuperType;
	}

	/**
	 * Returns the type constraints of this model.
	 *
	 * @return the type constraints (element type: <code>ITypeConstraint2</code>)
	 */
	public final Collection<ITypeConstraint2> getTypeConstraints() {
		return Collections.unmodifiableCollection(fTypeConstraints);
	}

	/**
	 * Sets the compliance level to use.
	 *
	 * @param level the compliance level to use. The argument must be one of the <code>AST.JLSx</code> constants.
	 */
	public final void setCompliance(final int level) {
		fCompliance= level;
	}
}
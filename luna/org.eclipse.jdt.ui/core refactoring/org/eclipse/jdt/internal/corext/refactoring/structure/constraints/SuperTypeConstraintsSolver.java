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
package org.eclipse.jdt.internal.corext.refactoring.structure.constraints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jdt.core.ICompilationUnit;

import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TType;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.CastVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ConstraintVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ITypeConstraint2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ITypeConstraintVariable;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ITypeSet;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ImmutableTypeVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.TypeEquivalenceSet;

/**
 * Type constraint solver to solve supertype constraint models.
 *
 * @since 3.1
 */
public class SuperTypeConstraintsSolver {

	/** The type estimate data (type: <code>TType</code>) */
	public static final String DATA_TYPE_ESTIMATE= "te"; //$NON-NLS-1$

	/** The type constraint model to solve */
	protected final SuperTypeConstraintsModel fModel;

	/** The obsolete casts (element type: <code>&lt;ICompilationUnit, Collection&lt;CastVariable2&gt;&gt;</code>) */
	protected Map<ICompilationUnit, Collection<CastVariable2>> fObsoleteCasts= null;

	/** The list of constraint variables to be processed */
	protected LinkedList<ConstraintVariable2> fProcessable= null;

	/** The type occurrences (element type: <code>&lt;ICompilationUnit, Collection&lt;ITypeConstraintVariable&gt;</code>) */
	protected Map<ICompilationUnit, Collection<ITypeConstraintVariable>> fTypeOccurrences= null;

	/**
	 * Creates a new super type constraints solver.
	 *
	 * @param model the model to solve
	 */
	public SuperTypeConstraintsSolver(final SuperTypeConstraintsModel model) {
		Assert.isNotNull(model);

		fModel= model;
	}

	/**
	 * Computes the necessary equality constraints for conditional expressions.
	 *
	 * @param constraints the type constraints (element type: <code>ITypeConstraint2</code>)
	 * @param level the compliance level
	 */
	private void computeConditionalTypeConstraints(final Collection<ITypeConstraint2> constraints, final int level) {
		ITypeConstraint2 constraint= null;
		for (final Iterator<ITypeConstraint2> iterator= constraints.iterator(); iterator.hasNext();) {
			constraint= iterator.next();
			if (constraint instanceof ConditionalTypeConstraint) {
				final ConditionalTypeConstraint conditional= (ConditionalTypeConstraint) constraint;
				fModel.createEqualityConstraint(constraint.getLeft(), constraint.getRight());
				fModel.createEqualityConstraint(conditional.getExpression(), constraint.getLeft());
				fModel.createEqualityConstraint(conditional.getExpression(), constraint.getRight());
			}
		}
	}

	/**
	 * Computes the necessary equality constraints for non-covariant return types.
	 *
	 * @param constraints the type constraints (element type: <code>ITypeConstraint2</code>)
	 * @param level the compliance level
	 */
	private void computeNonCovariantConstraints(final Collection<ITypeConstraint2> constraints, final int level) {
		if (level != 3) {
			ITypeConstraint2 constraint= null;
			for (final Iterator<ITypeConstraint2> iterator= constraints.iterator(); iterator.hasNext();) {
				constraint= iterator.next();
				if (constraint instanceof CovariantTypeConstraint)
					fModel.createEqualityConstraint(constraint.getLeft(), constraint.getRight());
			}
		}
	}

	/**
	 * Computes the obsolete casts for the specified cast variables.
	 *
	 * @param variables the cast variables (element type: <code>CastVariable2</code>)
	 */
	private void computeObsoleteCasts(final Collection<CastVariable2> variables) {
		fObsoleteCasts= new HashMap<ICompilationUnit, Collection<CastVariable2>>();
		CastVariable2 variable= null;
		for (final Iterator<CastVariable2> iterator= variables.iterator(); iterator.hasNext();) {
			variable= iterator.next();
			final TType type= (TType) variable.getExpressionVariable().getData(DATA_TYPE_ESTIMATE);
			if (type != null && type.canAssignTo(variable.getType())) {
				final ICompilationUnit unit= variable.getCompilationUnit();
				Collection<CastVariable2> casts= fObsoleteCasts.get(unit);
				if (casts != null)
					casts.add(variable);
				else {
					casts= new ArrayList<CastVariable2>(1);
					casts.add(variable);
					fObsoleteCasts.put(unit, casts);
				}
			}
		}
	}

	/**
	 * Computes the initial type estimate for the specified constraint variable.
	 *
	 * @param variable the constraint variable
	 * @return the initial type estimate
	 */
	protected ITypeSet computeTypeEstimate(final ConstraintVariable2 variable) {
		final TType type= variable.getType();
		if (variable instanceof ImmutableTypeVariable2 || !type.getErasure().equals(fModel.getSubType().getErasure()))
			return SuperTypeSet.createTypeSet(type);
		return SuperTypeSet.createTypeSet(type, fModel.getSuperType());
	}

	/**
	 * Computes the initial type estimates for the specified variables.
	 *
	 * @param variables the constraint variables (element type: <code>ConstraintVariable2</code>)
	 */
	private void computeTypeEstimates(final Collection<ConstraintVariable2> variables) {
		ConstraintVariable2 variable= null;
		for (final Iterator<ConstraintVariable2> iterator= variables.iterator(); iterator.hasNext();) {
			variable= iterator.next();
			TypeEquivalenceSet set= variable.getTypeEquivalenceSet();
			if (set == null) {
				set= new TypeEquivalenceSet(variable);
				set.setTypeEstimate(computeTypeEstimate(variable));
				variable.setTypeEquivalenceSet(set);
			} else {
				ITypeSet estimate= variable.getTypeEstimate();
				if (estimate == null) {
					final ConstraintVariable2[] contributing= set.getContributingVariables();
					estimate= SuperTypeSet.getUniverse();
					for (int index= 0; index < contributing.length; index++)
						estimate= estimate.restrictedTo(computeTypeEstimate(contributing[index]));
					set.setTypeEstimate(estimate);
				}
			}
		}
	}

	/**
	 * Computes a single type for each of the specified constraint variables.
	 *
	 * @param variables the constraint variables (element type: <code>ConstraintVariable2</code>)
	 */
	private void computeTypeOccurrences(final Collection<ConstraintVariable2> variables) {
		fTypeOccurrences= new HashMap<ICompilationUnit, Collection<ITypeConstraintVariable>>();
		final TType superErasure= fModel.getSuperType().getErasure();
		TType estimatedType= null;
		ITypeSet set= null;
		ICompilationUnit unit= null;
		ConstraintVariable2 variable= null;
		ITypeConstraintVariable declaration= null;
		TType variableType= null;
		for (final Iterator<ConstraintVariable2> iterator= variables.iterator(); iterator.hasNext();) {
			variable= iterator.next();
			if (variable instanceof ITypeConstraintVariable) {
				declaration= (ITypeConstraintVariable) variable;
				variableType= variable.getType();
				set= declaration.getTypeEstimate();
				if (set != null) {
					estimatedType= set.chooseSingleType();
					if (estimatedType != null) {
						final TType typeErasure= estimatedType.getErasure();
						if (!typeErasure.equals(variableType.getErasure()) && typeErasure.equals(superErasure)) {
							declaration.setData(DATA_TYPE_ESTIMATE, estimatedType);
							unit= declaration.getCompilationUnit();
							if (unit != null) {
								Collection<ITypeConstraintVariable> matches= fTypeOccurrences.get(unit);
								if (matches != null)
									matches.add(declaration);
								else {
									matches= new ArrayList<ITypeConstraintVariable>(1);
									matches.add(declaration);
									fTypeOccurrences.put(unit, matches);
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Returns the computed obsolete casts.
	 *
	 * @return the obsolete casts (element type: <code>&lt;ICompilationUnit, Collection&lt;CastVariable2&gt;&gt;</code>)
	 */
	public final Map<ICompilationUnit, Collection<CastVariable2>> getObsoleteCasts() {
		return fObsoleteCasts;
	}

	/**
	 * Returns the computed type occurrences.
	 *
	 * @return the type occurrences (element type: <code>&lt;ICompilationUnit, Collection&lt;IDeclaredConstraintVariable&gt;</code>)
	 */
	public final Map<ICompilationUnit, Collection<ITypeConstraintVariable>> getTypeOccurrences() {
		return fTypeOccurrences;
	}

	/**
	 * Processes the given constraints on the constraint variable and propagates it.
	 *
	 * @param constraints the type constraints to process (element type: <code>ITypeConstraint2</code>)
	 */
	private void processConstraints(final Collection<ITypeConstraint2> constraints) {
		final int level= fModel.getCompliance();
		ITypeConstraint2 constraint= null;
		for (final Iterator<ITypeConstraint2> iterator= constraints.iterator(); iterator.hasNext();) {
			constraint= iterator.next();
			if ((level == 3 || !(constraint instanceof CovariantTypeConstraint)) && !(constraint instanceof ConditionalTypeConstraint)) {
				final ConstraintVariable2 leftVariable= constraint.getLeft();
				final ITypeSet leftEstimate= leftVariable.getTypeEstimate();
				final TypeEquivalenceSet set= leftVariable.getTypeEquivalenceSet();
				final ITypeSet newEstimate= leftEstimate.restrictedTo(constraint.getRight().getTypeEstimate());
				if (leftEstimate != newEstimate) {
					set.setTypeEstimate(newEstimate);
					fProcessable.addAll(Arrays.asList(set.getContributingVariables()));
				}
			}
		}
	}

	/**
	 * Solves the constraints of the associated model.
	 */
	public final void solveConstraints() {
		fProcessable= new LinkedList<ConstraintVariable2>();
		final Collection<ConstraintVariable2> variables= fModel.getConstraintVariables();
		final Collection<ITypeConstraint2> constraints= fModel.getTypeConstraints();
		final int level= fModel.getCompliance();
		computeNonCovariantConstraints(constraints, level);

		// TODO: use most specific common type for AST.JLS3
		computeConditionalTypeConstraints(constraints, level);

		computeTypeEstimates(variables);
		fProcessable.addAll(variables);
		Collection<ITypeConstraint2> usage= null;
		ConstraintVariable2 variable= null;
		while (!fProcessable.isEmpty()) {
			variable= fProcessable.removeFirst();
			usage= SuperTypeConstraintsModel.getVariableUsage(variable);
			if (!usage.isEmpty())
				processConstraints(usage);
			else
				variable.setData(DATA_TYPE_ESTIMATE, variable.getTypeEstimate().chooseSingleType());
		}
		computeTypeOccurrences(variables);
		computeObsoleteCasts(fModel.getCastVariables());
	}
}

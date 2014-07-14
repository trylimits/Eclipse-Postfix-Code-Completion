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
package org.eclipse.jdt.internal.corext.refactoring.structure;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextEditBasedChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RefactoringParticipant;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.UseSupertypeDescriptor;

import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringDescriptorUtil;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.structure.constraints.SuperTypeConstraintsModel;
import org.eclipse.jdt.internal.corext.refactoring.structure.constraints.SuperTypeConstraintsSolver;
import org.eclipse.jdt.internal.corext.refactoring.structure.constraints.SuperTypeRefactoringProcessor;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TType;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ISourceConstraintVariable;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ITypeConstraintVariable;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextEditBasedChangeManager;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;


/**
 * Refactoring processor to replace type occurrences by a super type.
 */
public final class UseSuperTypeProcessor extends SuperTypeRefactoringProcessor {

	private static final String IDENTIFIER= "org.eclipse.jdt.ui.useSuperTypeProcessor"; //$NON-NLS-1$

	/**
	 * Finds the type with the given fully qualified name (generic type
	 * parameters included) in the hierarchy.
	 *
	 * @param type
	 *            The hierarchy type to find the super type in
	 * @param name
	 *            The fully qualified name of the super type
	 * @return The found super type, or <code>null</code>
	 */
	protected static ITypeBinding findTypeInHierarchy(final ITypeBinding type, final String name) {
		if (type.isArray() || type.isPrimitive())
			return null;
		if (name.equals(type.getTypeDeclaration().getQualifiedName()))
			return type;
		final ITypeBinding binding= type.getSuperclass();
		if (binding != null) {
			final ITypeBinding result= findTypeInHierarchy(binding, name);
			if (result != null)
				return result;
		}
		final ITypeBinding[] bindings= type.getInterfaces();
		for (int index= 0; index < bindings.length; index++) {
			final ITypeBinding result= findTypeInHierarchy(bindings[index], name);
			if (result != null)
				return result;
		}
		return null;
	}

	/** The text change manager */
	private TextEditBasedChangeManager fChangeManager= null;

	/** The number of files affected by the last change generation */
	private int fChanges= 0;

	/** The subtype to replace */
	private IType fSubType;

	/** The supertype as replacement */
	private IType fSuperType= null;

	/**
	 * Creates a new super type processor.
	 *
	 * @param subType
	 *            the subtype to replace its occurrences
	 */
	public UseSuperTypeProcessor(final IType subType) {
		super(null);
		fReplace= true;
		fSubType= subType;
	}

	/**
	 * Creates a new super type processor.
	 *
	 * @param subType
	 *            the subtype to replace its occurrences
	 * @param superType
	 *            the supertype as replacement
	 */
	public UseSuperTypeProcessor(final IType subType, final IType superType) {
		super(null);
		fReplace= true;
		fSubType= subType;
		fSuperType= superType;
	}

	/**
	 * Creates a new super type processor from refactoring arguments.
	 *
	 * @param arguments
	 *            the refactoring arguments
	 * @param status
	 *            the resulting status
	 */
	public UseSuperTypeProcessor(JavaRefactoringArguments arguments, RefactoringStatus status) {
		super(null);
		fReplace= true;
		RefactoringStatus initializeStatus= initialize(arguments);
		status.merge(initializeStatus);
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#checkFinalConditions(org.eclipse.core.runtime.IProgressMonitor,org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext)
	 */
	@Override
	public final RefactoringStatus checkFinalConditions(final IProgressMonitor monitor, final CheckConditionsContext context) throws CoreException, OperationCanceledException {
		Assert.isNotNull(monitor);
		Assert.isNotNull(context);
		final RefactoringStatus status= new RefactoringStatus();
		fChangeManager= new TextEditBasedChangeManager();
		try {
			monitor.beginTask("", 200); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.UseSuperTypeProcessor_checking);
			fChangeManager= createChangeManager(new SubProgressMonitor(monitor, 200), status);
			if (!status.hasFatalError()) {
				Checks.addModifiedFilesToChecker(ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits()), context);
			}
		} finally {
			monitor.done();
		}
		return status;
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#checkInitialConditions(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public final RefactoringStatus checkInitialConditions(final IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		Assert.isNotNull(monitor);
		final RefactoringStatus status= new RefactoringStatus();
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.UseSuperTypeProcessor_checking);
			// No checks
			monitor.worked(1);
		} finally {
			monitor.done();
		}
		return status;
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#createChange(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public final Change createChange(final IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		Assert.isNotNull(monitor);
		try {
			fChanges= 0;
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_creating);
			final TextEditBasedChange[] changes= fChangeManager.getAllChanges();
			if (changes != null && changes.length != 0) {
				fChanges= changes.length;
				IJavaProject project= null;
				if (!fSubType.isBinary())
					project= fSubType.getJavaProject();
				int flags= JavaRefactoringDescriptor.JAR_MIGRATION | JavaRefactoringDescriptor.JAR_REFACTORING | RefactoringDescriptor.STRUCTURAL_CHANGE | RefactoringDescriptor.MULTI_CHANGE;
				try {
					if (fSubType.isLocal() || fSubType.isAnonymous())
						flags|= JavaRefactoringDescriptor.JAR_SOURCE_ATTACHMENT;
				} catch (JavaModelException exception) {
					JavaPlugin.log(exception);
				}
				final String name= project != null ? project.getElementName() : null;
				final String description= Messages.format(RefactoringCoreMessages.UseSuperTypeProcessor_descriptor_description_short, BasicElementLabels.getJavaElementName(fSuperType.getElementName()));
				final String header= Messages.format(RefactoringCoreMessages.UseSuperTypeProcessor_descriptor_description, new String[] { JavaElementLabels.getElementLabel(fSuperType, JavaElementLabels.ALL_FULLY_QUALIFIED), JavaElementLabels.getElementLabel(fSubType, JavaElementLabels.ALL_FULLY_QUALIFIED) });
				final JDTRefactoringDescriptorComment comment= new JDTRefactoringDescriptorComment(name, this, header);
				comment.addSetting(Messages.format(RefactoringCoreMessages.UseSuperTypeProcessor_refactored_element_pattern, JavaElementLabels.getElementLabel(fSuperType, JavaElementLabels.ALL_FULLY_QUALIFIED)));
				addSuperTypeSettings(comment, false);
				final UseSupertypeDescriptor descriptor= RefactoringSignatureDescriptorFactory.createUseSupertypeDescriptor();
				descriptor.setProject(name);
				descriptor.setDescription(description);
				descriptor.setComment(comment.asString());
				descriptor.setFlags(flags);
				descriptor.setSubtype(getSubType());
				descriptor.setSupertype(getSuperType());
				descriptor.setReplaceInstanceof(fInstanceOf);
				return new DynamicValidationRefactoringChange(descriptor, RefactoringCoreMessages.UseSupertypeWherePossibleRefactoring_name, fChangeManager.getAllChanges());
			}
			monitor.worked(1);
		} finally {
			monitor.done();
		}
		return null;
	}

	/**
	 * Creates the text change manager for this processor.
	 *
	 * @param monitor
	 *            the progress monitor to display progress
	 * @param status
	 *            the refactoring status
	 * @return the created text change manager
	 * @throws JavaModelException
	 *             if the method declaration could not be found
	 * @throws CoreException
	 *             if the changes could not be generated
	 */
	protected final TextEditBasedChangeManager createChangeManager(final IProgressMonitor monitor, final RefactoringStatus status) throws JavaModelException, CoreException {
		Assert.isNotNull(status);
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask("", 300); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.UseSuperTypeProcessor_creating);
			final TextEditBasedChangeManager manager= new TextEditBasedChangeManager();
			final IJavaProject project= fSubType.getJavaProject();
			final ASTParser parser= ASTParser.newParser(ASTProvider.SHARED_AST_LEVEL);
			parser.setWorkingCopyOwner(fOwner);
			parser.setResolveBindings(true);
			parser.setProject(project);
			parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
			if (fSubType.isBinary() || fSubType.isReadOnly()) {
				final IBinding[] bindings= parser.createBindings(new IJavaElement[] { fSubType, fSuperType }, new SubProgressMonitor(monitor, 50));
				if (bindings != null && bindings.length == 2 && bindings[0] instanceof ITypeBinding && bindings[1] instanceof ITypeBinding) {
					solveSuperTypeConstraints(null, null, fSubType, (ITypeBinding) bindings[0], (ITypeBinding) bindings[1], new SubProgressMonitor(monitor, 100), status);
					if (!status.hasFatalError())
						rewriteTypeOccurrences(manager, null, null, null, null, new HashSet<String>(), status, new SubProgressMonitor(monitor, 150));
				}
			} else {
				parser.createASTs(new ICompilationUnit[] { fSubType.getCompilationUnit() }, new String[0], new ASTRequestor() {

					@Override
					public final void acceptAST(final ICompilationUnit unit, final CompilationUnit node) {
						try {
							final CompilationUnitRewrite subRewrite= new CompilationUnitRewrite(fOwner, unit, node);
							final AbstractTypeDeclaration subDeclaration= ASTNodeSearchUtil.getAbstractTypeDeclarationNode(fSubType, subRewrite.getRoot());
							if (subDeclaration != null) {
								final ITypeBinding subBinding= subDeclaration.resolveBinding();
								if (subBinding != null) {
									final ITypeBinding superBinding= findTypeInHierarchy(subBinding, fSuperType.getFullyQualifiedName('.'));
									if (superBinding != null) {
										solveSuperTypeConstraints(subRewrite.getCu(), subRewrite.getRoot(), fSubType, subBinding, superBinding, new SubProgressMonitor(monitor, 100), status);
										if (!status.hasFatalError()) {
											rewriteTypeOccurrences(manager, this, subRewrite, subRewrite.getCu(), subRewrite.getRoot(), new HashSet<String>(), status, new SubProgressMonitor(monitor, 200));
											final TextChange change= subRewrite.createChange(true);
											if (change != null)
												manager.manage(subRewrite.getCu(), change);
										}
									}
								}
							}
						} catch (CoreException exception) {
							JavaPlugin.log(exception);
							status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.UseSuperTypeProcessor_internal_error));
						}
					}

					@Override
					public final void acceptBinding(final String key, final IBinding binding) {
						// Do nothing
					}
				}, new NullProgressMonitor());
			}
			return manager;
		} finally {
			monitor.done();
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.structure.constraints.SuperTypeRefactoringProcessor#createContraintSolver(org.eclipse.jdt.internal.corext.refactoring.structure.constraints.SuperTypeConstraintsModel)
	 */
	@Override
	protected final SuperTypeConstraintsSolver createContraintSolver(final SuperTypeConstraintsModel model) {
		return new SuperTypeConstraintsSolver(model);
	}

	/**
	 * Returns the number of files that are affected from the last change
	 * generation.
	 *
	 * @return The number of files which are affected
	 */
	public final int getChanges() {
		return fChanges;
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#getElements()
	 */
	@Override
	public final Object[] getElements() {
		return new Object[] { fSubType };
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#getIdentifier()
	 */
	@Override
	public final String getIdentifier() {
		return IDENTIFIER;
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#getProcessorName()
	 */
	@Override
	public final String getProcessorName() {
		return RefactoringCoreMessages.UseSuperTypeProcessor_name;
	}

	/**
	 * Returns the subtype to be replaced.
	 *
	 * @return The subtype to be replaced
	 */
	public final IType getSubType() {
		return fSubType;
	}

	/**
	 * Returns the supertype as replacement.
	 *
	 * @return The supertype as replacement
	 */
	public final IType getSuperType() {
		return fSuperType;
	}

	private final RefactoringStatus initialize(JavaRefactoringArguments extended) {
		String handle= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT);
		if (handle != null) {
			final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(extended.getProject(), handle, false);
			if (element == null || !element.exists() || element.getElementType() != IJavaElement.TYPE)
				return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getProcessorName(), IJavaRefactorings.USE_SUPER_TYPE);
			else
				fSubType= (IType) element;
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT));
		handle= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + 1);
		if (handle != null) {
			final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(extended.getProject(), handle, false);
			if (element == null || !element.exists() || element.getElementType() != IJavaElement.TYPE)
				return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getProcessorName(), IJavaRefactorings.USE_SUPER_TYPE);
			else
				fSuperType= (IType) element;
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + 1));
		final String instance= extended.getAttribute(ATTRIBUTE_INSTANCEOF);
		if (instance != null) {
			fInstanceOf= Boolean.valueOf(instance).booleanValue();
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_INSTANCEOF));
		return new RefactoringStatus();
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#isApplicable()
	 */
	@Override
	public final boolean isApplicable() throws CoreException {
		return Checks.isAvailable(fSubType) && Checks.isAvailable(fSuperType) && !fSubType.isAnonymous() && !fSubType.isAnnotation() && !fSuperType.isAnonymous() && !fSuperType.isAnnotation() && !fSuperType.isEnum();
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#loadParticipants(org.eclipse.ltk.core.refactoring.RefactoringStatus,org.eclipse.ltk.core.refactoring.participants.SharableParticipants)
	 */
	@Override
	public final RefactoringParticipant[] loadParticipants(final RefactoringStatus status, final SharableParticipants sharedParticipants) throws CoreException {
		return new RefactoringParticipant[0];
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void rewriteTypeOccurrences(final TextEditBasedChangeManager manager, final ASTRequestor requestor, final CompilationUnitRewrite rewrite, final ICompilationUnit unit, final CompilationUnit node, final Set<String> replacements, final IProgressMonitor monitor) throws CoreException {
		try {
			monitor.beginTask("", 100); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_creating);
			final Collection<ITypeConstraintVariable> collection= fTypeOccurrences.get(unit);
			if (collection != null && !collection.isEmpty()) {
				final IProgressMonitor subMonitor= new SubProgressMonitor(monitor, 100);
				try {
					subMonitor.beginTask("", collection.size() * 10); //$NON-NLS-1$
					subMonitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_creating);
					TType estimate= null;
					ISourceConstraintVariable variable= null;
					CompilationUnitRewrite currentRewrite= null;
					final ICompilationUnit sourceUnit= rewrite.getCu();
					if (sourceUnit.equals(unit))
						currentRewrite= rewrite;
					else
						currentRewrite= new CompilationUnitRewrite(fOwner, unit, node);
					for (final Iterator<ITypeConstraintVariable> iterator= collection.iterator(); iterator.hasNext();) {
						variable= iterator.next();
						estimate= (TType) variable.getData(SuperTypeConstraintsSolver.DATA_TYPE_ESTIMATE);
						if (estimate != null && variable instanceof ITypeConstraintVariable) {
							final ASTNode result= NodeFinder.perform(node, ((ITypeConstraintVariable) variable).getRange().getSourceRange());
							if (result != null)
								rewriteTypeOccurrence(estimate, currentRewrite, result, currentRewrite.createCategorizedGroupDescription(RefactoringCoreMessages.SuperTypeRefactoringProcessor_update_type_occurrence, SET_SUPER_TYPE));
						}
						subMonitor.worked(10);
					}
					if (!sourceUnit.equals(unit)) {
						final TextChange change= currentRewrite.createChange(true);
						if (change != null)
							manager.manage(unit, change);
					}
				} finally {
					subMonitor.done();
				}
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Sets the supertype as replacement.
	 *
	 * @param type
	 *            The supertype to set
	 */
	public final void setSuperType(final IType type) {
		Assert.isNotNull(type);

		fSuperType= type;
	}
}

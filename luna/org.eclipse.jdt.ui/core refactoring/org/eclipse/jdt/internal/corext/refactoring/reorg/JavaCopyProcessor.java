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
package org.eclipse.jdt.internal.corext.refactoring.reorg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IResource;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.ChangeDescriptor;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.CopyProcessor;
import org.eclipse.ltk.core.refactoring.participants.RefactoringParticipant;
import org.eclipse.ltk.core.refactoring.participants.ReorgExecutionLog;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationStateChange;
import org.eclipse.jdt.internal.corext.refactoring.participants.JavaProcessors;
import org.eclipse.jdt.internal.corext.refactoring.participants.ResourceProcessors;
import org.eclipse.jdt.internal.corext.refactoring.reorg.IReorgPolicy.ICopyPolicy;
import org.eclipse.jdt.internal.corext.util.Resources;

import org.eclipse.jdt.ui.refactoring.IRefactoringProcessorIds;

public final class JavaCopyProcessor extends CopyProcessor implements IReorgDestinationValidator {

	private ICopyPolicy fCopyPolicy;

	private ReorgExecutionLog fExecutionLog;

	private INewNameQueries fNewNameQueries;

	private IReorgQueries fReorgQueries;

	public JavaCopyProcessor(ICopyPolicy copyPolicy) {
		fCopyPolicy= copyPolicy;
	}

	public JavaCopyProcessor(JavaRefactoringArguments refactoringArguments, RefactoringStatus status) {
		RefactoringStatus initStatus= initialize(refactoringArguments);
		status.merge(initStatus);
	}


	public boolean canChildrenBeDestinations(IReorgDestination destination) {
		return fCopyPolicy.canChildrenBeDestinations(destination);
	}

	public boolean canElementBeDestination(IReorgDestination destination) {
		return fCopyPolicy.canElementBeDestination(destination);
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context) throws CoreException {
		Assert.isNotNull(fNewNameQueries, "Missing new name queries"); //$NON-NLS-1$
		Assert.isNotNull(fReorgQueries, "Missing reorg queries"); //$NON-NLS-1$
		pm.beginTask("", 2); //$NON-NLS-1$
		return fCopyPolicy.checkFinalConditions(new SubProgressMonitor(pm, 1), context, fReorgQueries);
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
		RefactoringStatus result= new RefactoringStatus();
		result.merge(RefactoringStatus.create(Resources.checkInSync(ReorgUtils.getNotNulls(fCopyPolicy.getResources()))));
		IResource[] javaResources= ReorgUtils.getResources(fCopyPolicy.getJavaElements());
		result.merge(RefactoringStatus.create(Resources.checkInSync(ReorgUtils.getNotNulls(javaResources))));
		return result;
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException {
		Assert.isNotNull(fNewNameQueries);
		Assert.isTrue(fCopyPolicy.getJavaElementDestination() == null || fCopyPolicy.getResourceDestination() == null);
		Assert.isTrue(fCopyPolicy.getJavaElementDestination() != null || fCopyPolicy.getResourceDestination() != null);
		try {
			final DynamicValidationStateChange result= new DynamicValidationStateChange(getChangeName()) {

				@Override
				public Object getAdapter(Class adapter) {
					if (ReorgExecutionLog.class.equals(adapter))
						return fExecutionLog;
					return super.getAdapter(adapter);
				}

				@Override
				public ChangeDescriptor getDescriptor() {
					return fCopyPolicy.getDescriptor();
				}

				@Override
				public Change perform(IProgressMonitor pm2) throws CoreException {
					try {
						super.perform(pm2);
					} catch (OperationCanceledException e) {
						fExecutionLog.markAsCanceled();
						throw e;
					}
					return null;
				}
			};
			Change change= fCopyPolicy.createChange(pm, new MonitoringNewNameQueries(fNewNameQueries, fExecutionLog));
			if (change instanceof CompositeChange) {
				CompositeChange subComposite= (CompositeChange) change;
				result.merge(subComposite);
			} else {
				result.add(change);
			}
			return result;
		} finally {
			pm.done();
		}
	}

	private String[] getAffectedProjectNatures() throws CoreException {
		String[] jNatures= JavaProcessors.computeAffectedNaturs(fCopyPolicy.getJavaElements());
		String[] rNatures= ResourceProcessors.computeAffectedNatures(fCopyPolicy.getResources());
		Set<String> result= new HashSet<String>();
		result.addAll(Arrays.asList(jNatures));
		result.addAll(Arrays.asList(rNatures));
		return result.toArray(new String[result.size()]);
	}

	private String getChangeName() {
		return RefactoringCoreMessages.JavaCopyProcessor_changeName;
	}

	public Object getCommonParentForInputElements() {
		return new ParentChecker(fCopyPolicy.getResources(), fCopyPolicy.getJavaElements()).getCommonParent();
	}

	@Override
	public Object[] getElements() {
		IJavaElement[] jElements= fCopyPolicy.getJavaElements();
		IResource[] resources= fCopyPolicy.getResources();
		List<IAdaptable> result= new ArrayList<IAdaptable>(jElements.length + resources.length);
		result.addAll(Arrays.asList(jElements));
		result.addAll(Arrays.asList(resources));
		return result.toArray();
	}

	@Override
	public String getIdentifier() {
		return IRefactoringProcessorIds.COPY_PROCESSOR;
	}

	public IJavaElement[] getJavaElements() {
		return fCopyPolicy.getJavaElements();
	}

	@Override
	public String getProcessorName() {
		return RefactoringCoreMessages.JavaCopyProcessor_processorName;
	}

	public IResource[] getResources() {
		return fCopyPolicy.getResources();
	}

	private RefactoringStatus initialize(JavaRefactoringArguments extended) {
		setReorgQueries(new NullReorgQueries());
		final RefactoringStatus status= new RefactoringStatus();
		fCopyPolicy= ReorgPolicyFactory.createCopyPolicy(status, extended);
		if (fCopyPolicy != null && !status.hasFatalError()) {
			status.merge(fCopyPolicy.initialize(extended));
			if (!status.hasFatalError()) {
				final ReorgExecutionLog log= ReorgPolicyFactory.loadReorgExecutionLog(extended);
				if (log != null && !status.hasFatalError())
					setNewNameQueries(new LoggedNewNameQueries(log));
			}
		}
		return status;
	}

	@Override
	public boolean isApplicable() throws CoreException {
		return fCopyPolicy.canEnable();
	}

	@Override
	public RefactoringParticipant[] loadParticipants(RefactoringStatus status, SharableParticipants sharedParticipants) throws CoreException {
		RefactoringParticipant[] result= fCopyPolicy.loadParticipants(status, this, getAffectedProjectNatures(), sharedParticipants);
		fExecutionLog= fCopyPolicy.getReorgExecutionLog();
		return result;
	}

	public RefactoringStatus setDestination(IReorgDestination destination) throws JavaModelException {
		fCopyPolicy.setDestination(destination);
		return fCopyPolicy.verifyDestination(destination);
	}

	public void setNewNameQueries(INewNameQueries newNameQueries) {
		Assert.isNotNull(newNameQueries);
		fNewNameQueries= newNameQueries;
	}

	public void setReorgQueries(IReorgQueries queries) {
		Assert.isNotNull(queries);
		fReorgQueries= queries;
	}

	public int getSaveMode() {
		return fCopyPolicy.getSaveMode();
	}
}

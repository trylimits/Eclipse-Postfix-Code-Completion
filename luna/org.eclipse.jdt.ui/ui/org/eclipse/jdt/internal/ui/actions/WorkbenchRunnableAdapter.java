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
package org.eclipse.jdt.internal.ui.actions;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.operation.IThreadListener;

import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.ui.JavaUIStatus;

/**
 * An {@link IRunnableWithProgress} that adapts an {@link IWorkspaceRunnable} so that is can be
 * executed inside an {@link IRunnableContext}. The runnable is run as an
 * {@linkplain JavaCore#run(IWorkspaceRunnable, ISchedulingRule, IProgressMonitor) atomic Java model operation}.
 * <p>
 * {@link OperationCanceledException}s thrown by the
 * adapted runnable are caught and re-thrown as {@link InterruptedException}s.
 */
public class WorkbenchRunnableAdapter implements IRunnableWithProgress, IThreadListener {

	private boolean fTransfer= false;
	private IWorkspaceRunnable fWorkspaceRunnable;
	private ISchedulingRule fRule;

	/**
	 * Runs a workspace runnable with the workspace lock.
	 * 
	 * @param runnable the runnable
	 */
	public WorkbenchRunnableAdapter(IWorkspaceRunnable runnable) {
		this(runnable, ResourcesPlugin.getWorkspace().getRoot());
	}

	/**
	 * Runs a workspace runnable with the given lock or <code>null</code> to run with no lock at
	 * all.
	 * 
	 * @param runnable the runnable
	 * @param rule the scheduling rule, or <code>null</code>
	 */
	public WorkbenchRunnableAdapter(IWorkspaceRunnable runnable, ISchedulingRule rule) {
		fWorkspaceRunnable= runnable;
		fRule= rule;
	}

	/**
	 * Runs a workspace runnable with the given lock or <code>null</code> to run with no lock at
	 * all.
	 * 
	 * @param runnable the runnable
	 * @param rule the scheduling rule, or <code>null</code>
	 * @param transfer <code>true</code> iff the rule is to be transfered to the modal context
	 *            thread
	 */
	public WorkbenchRunnableAdapter(IWorkspaceRunnable runnable, ISchedulingRule rule, boolean transfer) {
		fWorkspaceRunnable= runnable;
		fRule= rule;
		fTransfer= transfer;
	}

	/**
	 * Returns the scheduling rule, or <code>null</code> if none.
	 * 
	 * @return the scheduling rule, or <code>null</code> if none
	 */
	public ISchedulingRule getSchedulingRule() {
		return fRule;
	}

	/**
	 * {@inheritDoc}
	 */
	public void threadChange(Thread thread) {
		if (fTransfer)
			Job.getJobManager().transferRule(fRule, thread);
	}

	/*
	 * @see IRunnableWithProgress#run(IProgressMonitor)
	 */
	public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		try {
			JavaCore.run(fWorkspaceRunnable, fRule, monitor);
		} catch (OperationCanceledException e) {
			throw new InterruptedException(e.getMessage());
		} catch (CoreException e) {
			throw new InvocationTargetException(e);
		}
	}

	public void runAsUserJob(String name, final Object jobFamiliy) {
		Job job= new Job(name) {
			/* (non-Javadoc)
			 * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
			 */
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					WorkbenchRunnableAdapter.this.run(monitor);
				} catch (InvocationTargetException e) {
					Throwable cause= e.getCause();
					if (cause instanceof CoreException) {
						return ((CoreException) cause).getStatus();
					} else {
						return JavaUIStatus.createError(IStatus.ERROR, cause);
					}
				} catch (InterruptedException e) {
					return Status.CANCEL_STATUS;
				} finally {
					monitor.done();
				}
				return Status.OK_STATUS;
			}
			@Override
			public boolean belongsTo(Object family) {
				return jobFamiliy == family;
			}
		};
		job.setRule(fRule);
		job.setUser(true);
		job.schedule();

		// TODO: should block until user pressed 'to background'
	}
}

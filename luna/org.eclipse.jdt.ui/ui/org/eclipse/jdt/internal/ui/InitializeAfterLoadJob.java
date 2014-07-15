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
 package org.eclipse.jdt.internal.ui;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.ui.progress.UIJob;

import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.JavaUI;

public class InitializeAfterLoadJob extends UIJob {

	private final class RealJob extends Job {
		public RealJob(String name) {
			super(name);
		}
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			monitor.beginTask("", 10); //$NON-NLS-1$
			try {
				JavaCore.initializeAfterLoad(new SubProgressMonitor(monitor, 6));
				JavaPlugin.initializeAfterLoad(new SubProgressMonitor(monitor, 4));
			} catch (CoreException e) {
				JavaPlugin.log(e);
				return e.getStatus();
			}
			return Status.OK_STATUS;
		}
		@Override
		public boolean belongsTo(Object family) {
			return JavaUI.ID_PLUGIN.equals(family);
		}
	}

	public InitializeAfterLoadJob() {
		super(JavaUIMessages.InitializeAfterLoadJob_starter_job_name);
		setSystem(true);
	}
	@Override
	public IStatus runInUIThread(IProgressMonitor monitor) {
		Job job = new RealJob(JavaUIMessages.JavaPlugin_initializing_ui);
		job.setPriority(Job.SHORT);
		job.schedule();
		return Status.OK_STATUS;
	}
}
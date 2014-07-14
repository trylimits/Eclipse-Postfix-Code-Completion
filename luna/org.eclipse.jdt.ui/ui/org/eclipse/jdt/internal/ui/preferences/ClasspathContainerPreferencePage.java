/*******************************************************************************
 * Copyright (c) 2007, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.IWizard;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.preferences.WizardPropertyPage;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.packageview.ClassPathContainer;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathSupport;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.ClasspathContainerWizard;

/**
 * Wraps a PropertyPage around a ClasspathContainerWizard.
 * It is required, that the wizard consists of exactly one page.
 */
public class ClasspathContainerPreferencePage extends WizardPropertyPage {

	private IJavaProject fJavaProject;
	private IClasspathEntry fEntry;

	public ClasspathContainerPreferencePage() {
		noDefaultAndApplyButton();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setElement(IAdaptable element) {
		super.setElement(element);

		ClassPathContainer container;
		if (element instanceof ClassPathContainer) {
			container= (ClassPathContainer) element;
		} else {
			container= (ClassPathContainer) element.getAdapter(ClassPathContainer.class);
		}
		fJavaProject= container.getJavaProject();
		fEntry= container.getClasspathEntry();
	}

	@Override
	protected IWizard createWizard() {
		try {
			IJavaProject project= fJavaProject;
			IClasspathEntry[] entries= project.getRawClasspath();
			return new ClasspathContainerWizard(fEntry, project, entries);
		} catch (JavaModelException e) {
			String title= ActionMessages.ConfigureContainerAction_error_title;
			String message= ActionMessages.ConfigureContainerAction_error_creationfailed_message;
			ExceptionHandler.handle(e, getShell(), title, message);
		}

		return null;
	}

	/**
	 * Apply the changes to the classpath
	 */
	@Override
	protected void applyChanges() {
		IClasspathEntry[] created= ((ClasspathContainerWizard) getWizard()).getNewEntries();
		if (created == null || created.length != 1)
			return;

		final IClasspathEntry result= created[0];
		if (result == null || result.equals(fEntry))
			return;

		try {
			IClasspathEntry[] entries= fJavaProject.getRawClasspath();

			int idx= indexInClasspath(entries, fEntry);
			if (idx == -1)
				return;

			final IClasspathEntry[] newEntries= new IClasspathEntry[entries.length];
			System.arraycopy(entries, 0, newEntries, 0, entries.length);
			newEntries[idx]= result;

			IRunnableContext context= new ProgressMonitorDialog(getShell());
			context= PlatformUI.getWorkbench().getProgressService();
			context.run(true, true, new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					try {
						if (result.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
							IPath path= result.getPath();
							String eeID= JavaRuntime.getExecutionEnvironmentId(path);
							if (eeID != null) {
								BuildPathSupport.setEEComplianceOptions(fJavaProject, eeID, null);
							}
						}
						fJavaProject.setRawClasspath(newEntries, fJavaProject.getOutputLocation(), monitor);
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					}
				}
			});

			fEntry= result;

		} catch (JavaModelException e) {
			String title= ActionMessages.ConfigureContainerAction_error_title;
			String message= ActionMessages.ConfigureContainerAction_error_creationfailed_message;
			ExceptionHandler.handle(e, getShell(), title, message);
		} catch (InvocationTargetException e) {
			String title= ActionMessages.ConfigureContainerAction_error_title;
			String message= ActionMessages.ConfigureContainerAction_error_applyingfailed_message;
			ExceptionHandler.handle(e, getShell(), title, message);
		} catch (InterruptedException e) {
			// user cancelled
		}
	}

	private static int indexInClasspath(IClasspathEntry[] entries, IClasspathEntry entry) {
		for (int i= 0; i < entries.length; i++) {
			if (entries[i].equals(entry)) {
				return i;
			}
		}
		return -1;
	}

}

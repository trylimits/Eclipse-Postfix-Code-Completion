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
package org.eclipse.jdt.ui.wizards;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.core.filesystem.URIUtil;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IProject;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.operation.IRunnableWithProgress;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathsBlock;

/**
 * Basic wizard page for creating new Java projects. This page can be used in
 * project creation wizards. The page shows UI to configure the project with a Java
 * build path and output location. On finish the page will also configure the Java nature.
 * <p>
 * This is a replacement for {@link NewJavaProjectWizardPage} with a cleaner API.
 * </p>
 * <p>
 * The pages used by the New Java Project wizard are {@link NewJavaProjectWizardPageOne} and
 * {@link NewJavaProjectWizardPageTwo}.
 * </p>
 * <p>
 * Clients may instantiate or subclass.
 * </p>
 *
 * @since 2.0
 */
public class JavaCapabilityConfigurationPage extends NewElementWizardPage {

	private static final String PAGE_NAME= "JavaCapabilityConfigurationPage"; //$NON-NLS-1$

	private IJavaProject fJavaProject;
	private BuildPathsBlock fBuildPathsBlock;

	/**
	 * Creates a wizard page that can be used in a Java project creation wizard.
	 * It contains UI to configure a the classpath and the output folder.
	 *
	 * <p>
	 * After constructing, a call to {@link #init(IJavaProject, IPath, IClasspathEntry[], boolean)} is required.
	 * </p>
	 */
	public JavaCapabilityConfigurationPage() {
        super(PAGE_NAME);
        fJavaProject= null;

        setTitle(NewWizardMessages.JavaCapabilityConfigurationPage_title);
        setDescription(NewWizardMessages.JavaCapabilityConfigurationPage_description);
	}

    private BuildPathsBlock getBuildPathsBlock() {
        if (fBuildPathsBlock == null) {
            IStatusChangeListener listener= new IStatusChangeListener() {
                public void statusChanged(IStatus status) {
                    updateStatus(status);
                }
            };
            fBuildPathsBlock= new BuildPathsBlock(new BusyIndicatorRunnableContext(), listener, 0, useNewSourcePage(), null);
        }
        return fBuildPathsBlock;
    }

    /*
     * @see org.eclipse.jface.dialogs.DialogPage#dispose()
     * @since 3.3
     */
    @Override
	public void dispose() {
    	try {
        	super.dispose();
        } finally {
        	if (fBuildPathsBlock != null) {
        		fBuildPathsBlock.dispose();
        		fBuildPathsBlock= null;
        	}
        }
    }

	/**
	 * Clients can override this method to choose if the new source page is used. The new source page
	 * requires that the project is already created as Java project. The page will directly manipulate the classpath.
	 * By default <code>false</code> is returned.
	 * @return Returns <code>true</code> if the new source page should be used.
	 * @since 3.1
	 */
	protected boolean useNewSourcePage() {
		return false;
	}

	/**
	 * Initializes the page with the project and default classpath.
	 * <p>
	 * The default classpath entries must correspond the given project.
	 * </p>
	 * <p>
	 * The caller of this method is responsible for creating the underlying project. The page will create the output,
	 * source and library folders if required.
	 * </p>
	 * <p>
	 * The project does not have to exist at the time of initialization, but must exist when executing the runnable
	 * obtained by <code>getRunnable()</code>.
	 * </p>
	 * @param jproject The Java project.
	 * @param defaultOutputLocation The default classpath entries or <code>null</code> to let the page choose the default
	 * @param defaultEntries The folder to be taken as the default output path or <code>null</code> to let the page choose the default
	 * @param defaultsOverrideExistingClasspath If set to <code>true</code>, an existing '.classpath' file is ignored. If set to <code>false</code>
	 * the given default classpath and output location is only used if no '.classpath' exists.
	 */
	public void init(IJavaProject jproject, IPath defaultOutputLocation, IClasspathEntry[] defaultEntries, boolean defaultsOverrideExistingClasspath) {
		if (!defaultsOverrideExistingClasspath && jproject.exists() && jproject.getProject().getFile(".classpath").exists()) { //$NON-NLS-1$
			defaultOutputLocation= null;
			defaultEntries= null;
		}
		getBuildPathsBlock().init(jproject, defaultOutputLocation, defaultEntries);
		fJavaProject= jproject;
	}

	/* (non-Javadoc)
	 * @see WizardPage#createControl
	 */
	public void createControl(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
		composite.setFont(parent.getFont());
		composite.setLayout(new GridLayout(1, false));
		Control control= getBuildPathsBlock().createControl(composite);
		control.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		Dialog.applyDialogFont(composite);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IJavaHelpContextIds.NEW_JAVAPROJECT_WIZARD_PAGE);
		setControl(composite);
	}

	/**
	 * Returns the currently configured output location. Note that the returned path
	 * might not be a valid path.
	 *
	 * @return the currently configured output location
	 */
	public IPath getOutputLocation() {
		return getBuildPathsBlock().getOutputLocation();
	}

	/**
	 * Returns the currently configured classpath. Note that the classpath might
	 * not be valid.
	 *
	 * @return the currently configured classpath
	 */
	public IClasspathEntry[] getRawClassPath() {
		return getBuildPathsBlock().getRawClassPath();
	}

	/**
	 * Returns the Java project that was passed in {@link #init(IJavaProject, IPath, IClasspathEntry[], boolean)} or <code>null</code> if the
	 * page has not been initialized yet.
	 *
	 * @return the managed Java project or <code>null</code>
	 */
	public IJavaProject getJavaProject() {
		return fJavaProject;
	}


	/**
	 * Returns the runnable that will create the Java project or <code>null</code> if the page has
	 * not been initialized. The runnable sets the project's classpath and output location to the values
	 * configured in the page and adds the Java nature if not set yet. The method requires that the
	 * project is created and opened.
	 *
	 * @return the runnable that creates the new Java project
	 */
	public IRunnableWithProgress getRunnable() {
		if (getJavaProject() != null) {
			return new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					try {
						configureJavaProject(monitor);
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					}
				}
			};
		}
		return null;
	}

	/**
	 * Helper method to create and open a IProject. The project location
	 * is configured. No natures are added.
	 *
	 * @param project The handle of the project to create.
	 * @param locationPath The location of the project <code>null</code> to create the project in the workspace
	 * @param monitor a progress monitor to report progress or <code>null</code> if
	 *  progress reporting is not desired
	 * @throws CoreException if the project couldn't be created
	 * @since 2.1
	 * @deprecated use {@link #createProject(IProject, URI, IProgressMonitor)} instead.
	 */
	public static void createProject(IProject project, IPath locationPath, IProgressMonitor monitor) throws CoreException {
		createProject(project, locationPath != null ? URIUtil.toURI(locationPath) : null, monitor);
	}

	/**
	 * Helper method to create and open a IProject. The project location
	 * is configured. No natures are added.
	 *
	 * @param project The handle of the project to create.
	 * @param locationURI The location of the project or <code>null</code> to create the project in the workspace
	 * @param monitor a progress monitor to report progress or <code>null</code> if
	 *  progress reporting is not desired
	 * @throws CoreException if the project couldn't be created
	 * @see org.eclipse.core.resources.IProjectDescription#setLocationURI(java.net.URI)
	 * @since 3.2
	 */
	public static void createProject(IProject project, URI locationURI, IProgressMonitor monitor) throws CoreException {
		BuildPathsBlock.createProject(project, locationURI, monitor);
	}

	/**
	 * Adds the Java nature to the project (if not set yet) and configures the build classpath.
	 *
	 * @param monitor a progress monitor to report progress or <code>null</code> if
	 * progress reporting is not desired
	 * @throws CoreException Thrown when the configuring the Java project failed.
	 * @throws InterruptedException Thrown when the operation has been canceled.
	 */
	public void configureJavaProject(IProgressMonitor monitor) throws CoreException, InterruptedException {
		configureJavaProject(null, monitor);
	}
	
	/**
	 * Adds the Java nature to the project (if not set yet) and configures the build classpath.
	 *
	 * @param newProjectCompliance compliance to set for a new project, can be <code>null</code>
	 * @param monitor a progress monitor to report progress or <code>null</code> if
	 * progress reporting is not desired
	 * @throws CoreException Thrown when the configuring the Java project failed.
	 * @throws InterruptedException Thrown when the operation has been canceled.
	 * @since 3.5
	 */
	public void configureJavaProject(String newProjectCompliance, IProgressMonitor monitor) throws CoreException, InterruptedException {
		if (monitor == null) {
			monitor= new NullProgressMonitor();
		}

		int nSteps= 6;
		monitor.beginTask(NewWizardMessages.JavaCapabilityConfigurationPage_op_desc_java, nSteps);

		try {
			IProject project= getJavaProject().getProject();
			BuildPathsBlock.addJavaNature(project, new SubProgressMonitor(monitor, 1));
			getBuildPathsBlock().configureJavaProject(newProjectCompliance, new SubProgressMonitor(monitor, 5));
		} catch (OperationCanceledException e) {
			throw new InterruptedException();
		} finally {
			monitor.done();
		}
	}

	/**
	 * Transfers the focus into this page.
	 *
	 * @since 3.3
	 */
	protected void setFocus() {
		getBuildPathsBlock().setFocus();
	}
}

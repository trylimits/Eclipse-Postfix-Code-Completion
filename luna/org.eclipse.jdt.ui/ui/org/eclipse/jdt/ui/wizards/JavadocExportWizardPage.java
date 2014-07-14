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
package org.eclipse.jdt.ui.wizards;

import java.util.List;

import org.w3c.dom.Element;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.operation.IRunnableContext;

import org.eclipse.jdt.core.IJavaElement;

/**
 * A Javadoc export wizard page allows the user to add an additional page to the
 * Javadoc wizard.
 * <p>
 * Clients should extend this class and include the name of their
 * class in an extension contributed to the jdt.ui's Javadoc export wizard page
 * extension point (named <code>org.eclipse.jdt.ui.javadocExportWizardPage
 * </code>).
 * </p>
 *
 * @since 3.4
 */
public abstract class JavadocExportWizardPage {

	/**
	 * The page container.
	 * <p>
	 * This interface is not intended to be implemented by clients.
	 * </p>
	 */
	public static interface IJavadocExportWizardPageContainer {

		/**
		 * Returns a runnable context top be used inside this wizard for long running
		 * operations
		 *
		 * @return a runnable context
		 */
		public IRunnableContext getRunnableContext();

		/**
		 * Calling this method will update status lines and button enablement in the
		 * wizard page container
		 */
		public void statusUpdated();

		/**
		 * Returns the currently configured VM options.
		 * @return the VM Options
		 */
		public String[] getConfiguredVMOptions();

		/**
		 * Returns the currently configured Javadoc command options.
		 * @return the Javadoc command options
		 */
		public String[] getConfiguredJavadocOptions();

		/**
		 * Returns the Java elements selected for Javadoc generation
		 *
		 * @return the Java elements selected for Javadoc generation
		 */
		public IJavaElement[] getSelectedJavaElements();
	}


	private IStatus fPageStatus= Status.OK_STATUS;
	private IJavadocExportWizardPageContainer fContainer;

    /**
     * Creates the content of this page under the given parent composite.
     *
     * @param parent the parent composite
     * @return return the created content
     */
	public abstract Control createContents(Composite parent);


	/**
	 * Returns the status of the page. The page is considered valid ('Ok' and 'Next' enabled)
	 * when the severity of the status is {@link IStatus#OK} or {@link IStatus#WARNING}.
	 * The page is invalid when the severity is {@link IStatus#ERROR}.
	 *
	 * @return the page status
	 * @see #setStatus(IStatus) to set the page status
	 */
	public final IStatus getStatus() {
		return fPageStatus;
	}

	/**
	 * Sets the page status. The page status severity must be one of {@link IStatus#OK}
	 * {@link IStatus#WARNING} or {@link IStatus#ERROR}.
	 *
	 * @param status the new status
	 */
	protected final void setStatus(IStatus status) {
		fPageStatus= status;
		getContainer().statusUpdated();
	}

	/**
	 * Sets the parent wizard container. The Javadoc wizard will set the container before {@link #setVisible} or
	 * {@link #createContents(Composite)} is called.
	 *
	 * @param container the parent container
	 */
	public final void setContainer(IJavadocExportWizardPageContainer container) {
		fContainer= container;
	}

	/**
	 * Gets the parent wizard container
	 *
	 * @return the parent wizard container
	 */
	protected final IJavadocExportWizardPageContainer getContainer() {
		return fContainer;
	}

	/**
	 * Called when the command line arguments are collected. Clients can add or modify arguments.
	 * 
	 * @param vmOptions A {@link List} of {@link String} with the VM arguments.
	 * @param toolOptions A {@link List} of {@link String} with the Javadoc tool arguments. See the
	 *            <a href=
	 *            "http://download.oracle.com/javase/6/docs/technotes/guides/javadoc/index.html">
	 *            Javadoc command specification</a> for the format of the arguments.
	 */
	public void updateArguments(List<String> vmOptions, List<String> toolOptions) {
	}

	/**
	 * Called when the Javadoc ANT script is generated.
	 * 
	 * @param xmlDocument The XML element for the 'javadoc' node. Clients can add or modify
	 *            arguments. See the <a
	 *            href="http://ant.apache.org/manual/Tasks/javadoc.html">Javadoc ANT task</a>
	 *            specification for the format of the arguments.
	 */
	public void updateAntScript(Element xmlDocument) {
	}

    /**
     * Disposes any resources allocated by this
     * dialog page.
     */
    public void dispose() {
    }

    /**
     * Notifies that help has been requested for this dialog page.
     */
    public void performHelp() {
    }

    /**
     * Called when the page becomes visible or becomes hidden.
     *
     * @param visible <code>true</code> when the page becomes visible,
     *  and <code>false</code> when the page is hidden
     */
    public void setVisible(boolean visible) {
    }

}

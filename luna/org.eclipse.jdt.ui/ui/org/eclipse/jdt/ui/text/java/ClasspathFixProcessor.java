/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.text.java;

import org.eclipse.swt.graphics.Image;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.MultiStatus;

import org.eclipse.ltk.core.refactoring.Change;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.changes.ClasspathChange;

import org.eclipse.jdt.internal.ui.text.correction.ClasspathFixProcessorDescriptor;


/**
 * Class to be implemented by contributors to the extension point
 * <code>org.eclipse.jdt.ui.classpathFixProcessors</code>.
 *
 * @since 3.4
 */
public abstract class ClasspathFixProcessor {


	/**
	 * A proposal to fix a class path issue.
	 */
	public static abstract class ClasspathFixProposal {

		/**
		 * A helper method to create a {@link Change} that modifies a class path.
		 *
		 * @param project the project to change
		 * @param newClasspath the new class path
		 * @param outputLocation the new output location
		 *
		 * @return the {@link Change} to change the class path or <code>null</code> if the class path is
		 * not valid (See {@link JavaConventions#validateClasspath(IJavaProject, IClasspathEntry[], IPath)}).
		 */
		public static Change newClasspathChange(IJavaProject project, IClasspathEntry[] newClasspath, IPath outputLocation) {
			return ClasspathChange.newChange(project, newClasspath, outputLocation);
		}

		/**
		 * A helper method to create a {@link Change} that adds an entry to the class path.
		 *
		 * @param project the project to change
		 * @param entryToAdd the entry to add to the class path
		 *
		 * @return the {@link Change} to change the class path or <code>null</code> if the class path is
		 * not valid (See {@link JavaConventions#validateClasspath(IJavaProject, IClasspathEntry[], IPath)}).
		 * @throws JavaModelException thrown if accessing the project failed.
		 */
		public static Change newAddClasspathChange(IJavaProject project, IClasspathEntry entryToAdd) throws JavaModelException {
			return ClasspathChange.addEntryChange(project, entryToAdd);
		}

		/**
		 * Returns the change to invoke when the proposal is selected.
		 *
		 * @param monitor the progress monitor
		 * @return the change
		 * @throws CoreException thrown when the creation of the change failed
		 */
		public abstract Change createChange(IProgressMonitor monitor) throws CoreException;

		/**
		 * Returns the string to be displayed in a list of proposals.
		 *
		 * @return the string to be displayed
		 */
		public abstract String getDisplayString();

		/**
		 * Returns optional additional information about the proposal. The additional information will
		 * be presented to assist the user in deciding if the selected proposal is the desired choice.
		 *
		 * @return the additional information or <code>null</code>
		 */
		public abstract String getAdditionalProposalInfo();

		/**
		 * Returns the image to be displayed in the list of completion proposals.
		 * The image would typically be shown to the left of the display string.
		 *
		 * @return the image to be shown or <code>null</code> if no image is desired
		 */
		public abstract Image getImage();

		/**
		 * Returns the relevance of this completion proposal.
		 * <p>
		 * The relevance is used to determine if this proposal is more
		 * relevant than another proposal.</p>
		 *
		 * @return the relevance of this completion proposal in the range of [0, 100]
		 */
		public abstract int getRelevance();
	}

	/**
	 * Returns proposal that can fix non-resolvable imports. The proposal is expected to change the class path or a classpath container
	 * so that the missing type can be resolved. A proposal should also consider to fix related types. For example, when a reference to
	 * <code>junit.framework.TestCase</code> is requested, it makes sense to import the full JUnit library, not just fixing the missing type.
	 *
	 * @param project the current project
	 * @param name the missing type to be added to the class path. The entries can be either a
	 *  <ul><li>qualified type name, like 'junit.framework.Test'</li>
	 *  <li>simple type name, like 'TestCase'</li>
	 *  <li>on demand import name, like 'org.junit.*'</li></ul>
	 * @return returns proposals to fix the class path so that the missing types are found. If no proposals can be offered,
	 * either <code>null</code> or the empty array can be returned. If <code>null</code> is returned, also the processors
	 * overridden by this processor are asked. If a non null result is returned, all overridden processors are skipped.
	 *
	 * @throws CoreException thrown when the creation of the proposals fails
	 */
	public abstract ClasspathFixProposal[] getFixImportProposals(IJavaProject project, String name) throws CoreException;


	/**
	 * Evaluates all contributed proposals that can fix non-resolvable imports.
	 *
	 * @param project the current project
	 * @param name the missing type to be added to the class path. The entries can be either a
	 *  <ul><li>qualified type name, like 'junit.framework.Test'</li>
	 *  <li>simple type name, like 'TestCase'</li>
	 *  <li>on demand import name, like 'org.junit.*'</li></ul>
	 * @param status a {@link MultiStatus} to collect the resulting status or <code>null</code> to not collect status.
	 * @return returns proposals to fix the class path so that the missing types are found.
	 */
	public static ClasspathFixProposal[] getContributedFixImportProposals(IJavaProject project, String name, MultiStatus status) {
		return ClasspathFixProcessorDescriptor.getProposals(project, name, status);
	}


}

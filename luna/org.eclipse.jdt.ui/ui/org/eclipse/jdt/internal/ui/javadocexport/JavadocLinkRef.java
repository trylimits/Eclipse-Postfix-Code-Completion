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

package org.eclipse.jdt.internal.ui.javadocexport;

import java.net.URL;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathSupport;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;


public class JavadocLinkRef {
	private final IJavaProject fProject;
	private final IPath fContainerPath;
	private IClasspathEntry fClasspathEntry;

	public JavadocLinkRef(IPath containerPath, IClasspathEntry classpathEntry, IJavaProject project) {
		fContainerPath= containerPath;
		fProject= project;
		fClasspathEntry= classpathEntry;
	}

	public JavadocLinkRef(IJavaProject project) {
		this(null, null, project);
	}

	public boolean isProjectRef() {
		return fClasspathEntry == null;
	}

	public IPath getFullPath() {
		return isProjectRef() ? fProject.getPath() : fClasspathEntry.getPath();
	}

	public URL getURL() {
		if (isProjectRef()) {
			return JavaUI.getProjectJavadocLocation(fProject);
		} else {
			return JavaUI.getLibraryJavadocLocation(fClasspathEntry);
		}
	}

	public void setURL(URL url, IProgressMonitor monitor) throws CoreException {
		if (isProjectRef()) {
			JavaUI.setProjectJavadocLocation(fProject, url);
		} else {
			CPListElement element= CPListElement.createFromExisting(fClasspathEntry, fProject);
			String location= url != null ? url.toExternalForm() : null;
			element.setAttribute(CPListElement.JAVADOC, location);
			String[] changedAttributes= { CPListElement.JAVADOC };
			BuildPathSupport.modifyClasspathEntry(null, element.getClasspathEntry(), changedAttributes, fProject, fContainerPath, fClasspathEntry.getReferencingEntry() != null, monitor);
			fClasspathEntry= element.getClasspathEntry();
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj != null && obj.getClass().equals(getClass())) {
			JavadocLinkRef other= (JavadocLinkRef) obj;
			if (!fProject.equals(other.fProject) || isProjectRef() != other.isProjectRef()) {
				return false;
			}
			if (!isProjectRef()) {
				return fClasspathEntry.equals(other.fClasspathEntry);
			} else {
				return true;
			}
		}
		return false;
	}

	@Override
	public int hashCode() {
		if (isProjectRef()) {
			return fProject.hashCode();
		} else {
			return fProject.hashCode() + fClasspathEntry.hashCode();
		}

	}
}

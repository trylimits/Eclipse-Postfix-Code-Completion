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
package org.eclipse.jdt.internal.ui.filters;


import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;


/**
 * Filters out all output folders.
 * <p>
 * Note: Folder which are direct children of a Java element
 * are already filtered by the Java Model.
 * </p>
 *
 * @since 3.0
 */
public class OutputFolderFilter extends ViewerFilter {

	/**
	 * Returns the result of this filter, when applied to the
	 * given element.
	 *
	 * @param viewer the viewer
	 * @param parent the parent
	 * @param element the element to test
	 * @return <code>true</code> if element should be included
	 * @since 3.0
	 */
	@Override
	public boolean select(Viewer viewer, Object parent, Object element) {
		if (element instanceof IFolder) {
			IFolder folder= (IFolder)element;
			IProject proj= folder.getProject();
			try {
				if (!proj.hasNature(JavaCore.NATURE_ID))
					return true;

				IJavaProject jProject= JavaCore.create(folder.getProject());
				if (jProject == null || !jProject.exists())
					return true;

				// Check default output location
				IPath defaultOutputLocation= jProject.getOutputLocation();
				IPath folderPath= folder.getFullPath();
				if (defaultOutputLocation != null && defaultOutputLocation.equals(folderPath))
					return false;

				// Check output location for each class path entry
				IClasspathEntry[] cpEntries= jProject.getRawClasspath();
				for (int i= 0, length= cpEntries.length; i < length; i++) {
					IPath outputLocation= cpEntries[i].getOutputLocation();
					if (outputLocation != null && outputLocation.equals(folderPath))
						return false;
				}
			} catch (CoreException ex) {
				return true;
			}
		}
		return true;
	}
}

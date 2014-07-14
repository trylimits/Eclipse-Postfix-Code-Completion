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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.jdt.core.IJavaElement;


/**
 * Filters out all non-Java elements.
 */
public class NonJavaElementFilter extends ViewerFilter {

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ViewerFilter#select(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
	 */
	@Override
	public boolean select(Viewer viewer, Object parent, Object element) {
		if (element instanceof IJavaElement)
			return true;

		if (element instanceof IResource) {
			IProject project= ((IResource)element).getProject();
			return project == null || !project.isOpen();
		}

		// Exclude all IStorage elements which are neither Java elements nor resources
		if (element instanceof IStorage)
			return false;

		return true;
	}
}

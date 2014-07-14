/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui;

import org.eclipse.core.resources.IResource;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

/**
 * This class locates different resources
 * which are related to an object
 */
public class ResourceLocator implements IResourceLocator {

	public IResource getUnderlyingResource(Object element) throws JavaModelException {
		if (element instanceof IJavaElement)
			return ((IJavaElement) element).getUnderlyingResource();
		else
			return null;
	}

	public IResource getCorrespondingResource(Object element) throws JavaModelException {
		if (element instanceof IJavaElement)
			return ((IJavaElement) element).getCorrespondingResource();
		else
			return null;
	}

	public IResource getContainingResource(Object element) throws JavaModelException {
		IResource resource= null;
		if (element instanceof IResource)
			resource= (IResource) element;
		if (element instanceof IJavaElement) {
			resource= ((IJavaElement) element).getResource();
			if (resource == null)
				resource= ((IJavaElement) element).getJavaProject().getProject();
		}
		return resource;
	}
}

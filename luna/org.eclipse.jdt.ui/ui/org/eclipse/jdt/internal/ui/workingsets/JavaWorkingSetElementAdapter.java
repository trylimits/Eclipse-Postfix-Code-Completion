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

package org.eclipse.jdt.internal.ui.workingsets;

import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetElementAdapter;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;

public class JavaWorkingSetElementAdapter implements IWorkingSetElementAdapter {

	public IAdaptable[] adaptElements(IWorkingSet ws, IAdaptable[] elements) {
		ArrayList<Object> result= new ArrayList<Object>(elements.length);

		for (int i= 0; i < elements.length; i++) {
			IAdaptable curr= elements[i];
			if (curr instanceof IJavaElement) {
				result.add(curr);
			} else if (curr instanceof IResource) {
				result.add(adaptFromResource((IResource) curr));
			} else {
				Object elem= curr.getAdapter(IJavaElement.class);
				if (elem == null) {
					elem= curr.getAdapter(IResource.class);
					if (elem != null) {
						elem= adaptFromResource((IResource) elem);
					}
				}
				if (elem != null) {
					result.add(elem);
				} // ignore all others
			}
		}
		return result.toArray(new IAdaptable[result.size()]);
	}

	private Object adaptFromResource(IResource resource) {
		IProject project= resource.getProject();
		if (project != null && project.isAccessible()) {
			try {
				if (project.hasNature(JavaCore.NATURE_ID)) {
					IJavaElement elem= JavaCore.create(resource);
					if (elem != null) {
						return elem;
					}
				}
			} catch (CoreException e) {
				// ignore
			}
		}
		return resource;
	}


	public void dispose() {
	}

}

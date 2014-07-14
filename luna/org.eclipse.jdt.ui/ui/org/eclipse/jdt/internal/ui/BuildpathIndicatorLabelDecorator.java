/*******************************************************************************
 * Copyright (c) 2009, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IDecoration;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public class BuildpathIndicatorLabelDecorator extends AbstractJavaElementLabelDecorator {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void decorate(Object element, IDecoration decoration) {
		ImageDescriptor overlay= getOverlay(element);
		if (overlay != null) {
			decoration.addOverlay(overlay, IDecoration.BOTTOM_LEFT);
		}
	}

	private ImageDescriptor getOverlay(Object element) {
		if (element instanceof IResource) {
			IResource resource= (IResource) element;
			IProject project= resource.getProject();
			if (project != null) {
				IJavaProject javaProject= JavaCore.create(project);
				if (javaProject != null) {
					if (javaProject.isOnClasspath(resource)) {
						IJavaElement javaElement= JavaCore.create(resource, javaProject);
						try {
							if (javaElement instanceof IPackageFragmentRoot
									&& ((IPackageFragmentRoot)javaElement).getKind() != IPackageFragmentRoot.K_SOURCE) {
								return JavaPluginImages.DESC_OVR_LIBRARY;
							}
						} catch (JavaModelException e) {
							return null;
						}
					}
				}
			}
		}
		return null;
	}

	@Override
	protected void processDelta(IJavaElementDelta delta, List<IJavaElement> result) {
		IJavaElement elem= delta.getElement();

		boolean isChanged= delta.getKind() == IJavaElementDelta.CHANGED;
		boolean isRemoved= delta.getKind() == IJavaElementDelta.REMOVED;
		int flags= delta.getFlags();

		switch (elem.getElementType()) {
			case IJavaElement.JAVA_MODEL:
				processChildrenDelta(delta, result);
				return;
			case IJavaElement.JAVA_PROJECT:
				if (isRemoved || (isChanged &&
						(flags & IJavaElementDelta.F_CLOSED) != 0)) {
					return;
				}
				processChildrenDelta(delta, result);
				return;
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				if (isRemoved) {
					return;
				}
				try {
					if ((((flags & IJavaElementDelta.F_REMOVED_FROM_CLASSPATH) != 0) ||
							((flags & IJavaElementDelta.F_ADDED_TO_CLASSPATH) != 0))
							&& ((IPackageFragmentRoot)elem).getKind() != IPackageFragmentRoot.K_SOURCE) {
						result.add(elem);
					}
				} catch (JavaModelException e) {
					// don't update
				}
				return;
			default:
				return;
		}
	}

}

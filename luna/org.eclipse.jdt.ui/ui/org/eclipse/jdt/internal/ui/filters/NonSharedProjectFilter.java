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

import org.eclipse.team.core.RepositoryProvider;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;

import org.eclipse.core.resources.IProject;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.jdt.core.IJavaProject;


/**
 * Filters non-shared projects and Java projects. Non-shared projects are
 * projects that are not controlled by a team provider.
 *
 * @since 2.1
 */
public class NonSharedProjectFilter extends ViewerFilter {

	/*
	 * Layer breaker needed to identify imported PDE project's as non-shared,
	 * see https://bugs.eclipse.org/316269 for details.
	 */
	private static final String PDE_NATURE_ID= "org.eclipse.pde.PluginNature"; //$NON-NLS-1$
	private static final QualifiedName EXTERNAL_PDE_PROJECT_PROPERTY= new QualifiedName("org.eclipse.pde.core", "imported"); //$NON-NLS-1$ //$NON-NLS-2$


	/*
	 * @see ViewerFilter
	 */
	@Override
	public boolean select(Viewer viewer, Object parent, Object element) {
		if (element instanceof IProject)
			return isSharedProject((IProject)element);

		if (element instanceof IJavaProject)
			return isSharedProject(((IJavaProject)element).getProject());

		return true;
	}

	private static boolean isSharedProject(IProject project) {
		return !project.isAccessible() || RepositoryProvider.isShared(project) && !isBinaryPDEProject(project);
	}

	private static boolean isBinaryPDEProject(IProject project) {
		try {
			return project.hasNature(PDE_NATURE_ID) && project.getPersistentProperty(EXTERNAL_PDE_PROJECT_PROPERTY) != null;
		} catch (CoreException e) {
			return false;
		}
	}

}

/*******************************************************************************
 * Copyright (c) 2005, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.resources.IFolder;

import org.eclipse.ltk.core.refactoring.RefactoringDescriptorProxy;
import org.eclipse.ltk.core.refactoring.history.RefactoringHistory;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.ui.StandardJavaElementContentProvider;

/**
 * Content provider for Java models.
 *
 * @since 3.2
 */
public final class JavaModelContentProvider extends StandardJavaElementContentProvider {

	/** The name of the settings folder */
	private static final String NAME_SETTINGS_FOLDER= ".settings"; //$NON-NLS-1$

	/**
	 * Creates a new java model content provider.
	 */
	public JavaModelContentProvider() {
		super(true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object[] getChildren(final Object element) {
		if (element instanceof ICompilationUnit)
			return NO_CHILDREN;
		else if (element instanceof RefactoringHistory)
			return ((RefactoringHistory) element).getDescriptors();
		else if (element instanceof IJavaProject) {
			final List<Object> elements= new ArrayList<Object>();
			elements.add(((IJavaProject) element).getProject().getFolder(NAME_SETTINGS_FOLDER));
			final Object[] children= super.getChildren(element);
			for (int index= 0; index < children.length; index++) {
				if (!elements.contains(children[index]))
					elements.add(children[index]);
			}
			return elements.toArray();
		} else if (element instanceof IFolder) {
			final IFolder folder= (IFolder) element;
			try {
				return folder.members();
			} catch (CoreException exception) {
				// Do nothing
			}
		}
		return super.getChildren(element);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasChildren(final Object element) {
		if (element instanceof ICompilationUnit)
			return false;
		else if (element instanceof RefactoringHistory)
			return true;
		else if (element instanceof RefactoringDescriptorProxy)
			return false;
		else if (element instanceof IFolder)
			return true;
		return super.hasChildren(element);
	}
}

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

package org.eclipse.jdt.internal.ui;


import org.eclipse.core.runtime.IAdapterFactory;

import org.eclipse.core.resources.IProject;

import org.eclipse.jdt.core.IJavaProject;

/**
 * An adapter factory for IJavaProjects.
 */
public class JavaProjectAdapterFactory implements IAdapterFactory {

	private static Class<?>[] PROPERTIES= new Class[] {
		IProject.class,
	};

	public Class[] getAdapterList() {
		return PROPERTIES;
	}

	public Object getAdapter(Object element, Class key) {
		if (IProject.class.equals(key)) {
			IJavaProject javaProject= (IJavaProject)element;
			return javaProject.getProject();
		}
		return null;
	}
}

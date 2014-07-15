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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

import org.eclipse.ui.part.FileEditorInput;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;

public class ResourceAdapterFactory implements IAdapterFactory {

	private static Class<?>[] PROPERTIES= new Class[] {
		IJavaElement.class
	};

	public Class[] getAdapterList() {
		return PROPERTIES;
	}

	public Object getAdapter(Object element, Class key) {
		if (IJavaElement.class.equals(key)) {

			// Performance optimization, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=133141
			if (element instanceof IFile) {
				IJavaElement je= JavaPlugin.getDefault().getWorkingCopyManager().getWorkingCopy(new FileEditorInput((IFile)element));
				if (je != null)
					return je;
			}

			return JavaCore.create((IResource)element);
		}
		return null;
	}
}

/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.javaeditor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IBufferFactory;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IOpenable;


/**
 * Creates <code>IBuffer</code>s based on documents.
 * @deprecated since 3.0 no longer used
 */
public class CustomBufferFactory implements IBufferFactory {

	/*
	 * @see org.eclipse.jdt.core.IBufferFactory#createBuffer(org.eclipse.jdt.core.IOpenable)
	 */
	public IBuffer createBuffer(IOpenable owner) {
		if (owner instanceof ICompilationUnit) {
			ICompilationUnit unit= (ICompilationUnit) owner;
			ICompilationUnit original= unit.getPrimary();
			IResource resource= original.getResource();
			if (resource instanceof IFile) {
				return new DocumentAdapter(unit, (IFile) resource);
			}

		}
		return DocumentAdapter.NULL;
	}
}

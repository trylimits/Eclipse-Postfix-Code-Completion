/*******************************************************************************
 * Copyright (c) 2006, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.compare;

import org.eclipse.team.ui.history.ElementLocalHistoryPageSource;

import org.eclipse.core.resources.IFile;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;

public class JavaElementHistoryPageSource extends ElementLocalHistoryPageSource {

	private static JavaElementHistoryPageSource instance;

	public static JavaElementHistoryPageSource getInstance() {
		if (instance == null)
			instance = new JavaElementHistoryPageSource();
		return instance;
	}

	/**
	 * Returns true if the given IJavaElement maps to a JavaNode. The JavaHistoryAction uses this
	 * function to determine whether a selected Java element can be replaced by some piece of code
	 * from the local history.
	 * 
	 * @param je the Java element
	 * @return <code>true</code> if there are editions available for the given Java element,
	 *         <code>false</code> otherwise
	 * 
	 * 
	 */
	public static boolean hasEdition(IJavaElement je) {

		if (je instanceof IMember && ((IMember)je).isBinary())
			return false;

		switch (je.getElementType()) {
		case IJavaElement.COMPILATION_UNIT:
		case IJavaElement.TYPE:
		case IJavaElement.FIELD:
		case IJavaElement.METHOD:
		case IJavaElement.INITIALIZER:
		case IJavaElement.PACKAGE_DECLARATION:
		case IJavaElement.IMPORT_CONTAINER:
		case IJavaElement.IMPORT_DECLARATION:
			return true;
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.history.ElementLocalHistoryPageSource#getFile(java.lang.Object)
	 */
	@Override
	public IFile getFile(Object input) {
		// extract CU from input
		ICompilationUnit cu= null;
		if (input instanceof ICompilationUnit)
			cu= (ICompilationUnit) input;
		else if (input instanceof IMember)
			cu= ((IMember)input).getCompilationUnit();

		if (cu == null || !cu.exists())
			return null;

		// get to original CU
		cu= cu.getPrimary();

		// find underlying file
		IFile file= (IFile) cu.getResource();
		if (file != null && file.exists())
			return file;
		return null;
	}
}

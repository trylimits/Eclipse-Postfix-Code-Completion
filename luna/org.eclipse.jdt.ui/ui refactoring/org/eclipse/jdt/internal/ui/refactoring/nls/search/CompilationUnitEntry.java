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
package org.eclipse.jdt.internal.ui.refactoring.nls.search;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.jdt.core.ICompilationUnit;

public class CompilationUnitEntry implements IAdaptable {

	private final String fMessage;
	private final ICompilationUnit fCompilationUnit;

	public CompilationUnitEntry(String message, ICompilationUnit compilationUnit) {
		fMessage= message;
		fCompilationUnit= compilationUnit;
	}

	public String getMessage() {
		return fMessage;
	}

	public ICompilationUnit getCompilationUnit() {
		return fCompilationUnit;
	}

	public Object getAdapter(Class adapter) {
		if (ICompilationUnit.class.equals(adapter))
			return getCompilationUnit();
		return null;
	}

}

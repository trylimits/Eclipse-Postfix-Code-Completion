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
package org.eclipse.jdt.internal.ui.javaeditor;

import org.eclipse.jdt.core.ICompilationUnit;


public interface ISavePolicy {

	/**
	 * Notifies this save policy that the given compilation unit
	 * is about to be saved.
	 *
	 * @param unit the compilation unit
	 */
	void preSave(ICompilationUnit unit);

	/**
	 * Returns the compilation unit in which the argument
	 * has been changed. If the argument is not changed, the
	 * returned result is <code>null</code>.
	 *
	 * @param unit the compilation unit
	 * @return the changed compilation unit or <code>null</code>
	 */
	ICompilationUnit postSave(ICompilationUnit unit);
}

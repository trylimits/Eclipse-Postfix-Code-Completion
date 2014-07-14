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
package org.eclipse.jdt.ui;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.ui.IEditorInput;

import org.eclipse.jdt.core.ICompilationUnit;

/**
 * Interface for accessing working copies of <code>ICompilationUnit</code>
 * objects. The original compilation unit is only given indirectly by means
 * of an <code>IEditorInput</code>. The life cycle is as follows:
 * <ul>
 * <li> <code>connect</code> creates and remembers a working copy of the
 *   compilation unit which is encoded in the given editor input</li>
 * <li> <code>getWorkingCopy</code> returns the working copy remembered on
 *   <code>connect</code></li>
 * <li> <code>disconnect</code> destroys the working copy remembered on
 *   <code>connect</code></li>
 * </ul>
 * <p>
 * In order to provide backward compatibility for clients of <code>IWorkingCopyManager</code>, extension
 * interfaces are used to provide a means of evolution. The following extension interfaces
 * exist:
 * <ul>
 * <li> {@link org.eclipse.jdt.ui.IWorkingCopyManagerExtension} since version 2.1 introducing API
 *      to set and remove the working copy for a given editor input.</li>
 * </ul>
 * </p>
 * <p>
 * This interface is not intended to be implemented by clients.
 * </p>
 *
 * @see JavaUI#getWorkingCopyManager()
 * @see org.eclipse.jdt.ui.IWorkingCopyManagerExtension
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface IWorkingCopyManager {

	/**
	 * Connects the given editor input to this manager. After calling
	 * this method, a working copy will be available for the compilation unit encoded
	 * in the given editor input (does nothing if there is no encoded compilation unit).
	 *
	 * @param input the editor input
	 * @exception CoreException if the working copy cannot be created for the
	 *   compilation unit
	 */
	void connect(IEditorInput input) throws CoreException;

	/**
	 * Disconnects the given editor input from this manager. After calling
	 * this method, a working copy for the compilation unit encoded
	 * in the given editor input will no longer be available. Does nothing if there
	 * is no encoded compilation unit, or if there is no remembered working copy for
	 * the compilation unit.
	 *
	 * @param input the editor input
	 */
	void disconnect(IEditorInput input);

	/**
	 * Returns the working copy remembered for the compilation unit encoded in the
	 * given editor input.
	 *
	 * @param input the editor input
	 * @return the working copy of the compilation unit, or <code>null</code> if the
	 *   input does not encode an editor input, or if there is no remembered working
	 *   copy for this compilation unit
	 */
	ICompilationUnit getWorkingCopy(IEditorInput input);

	/**
	 * Shuts down this working copy manager. All working copies still remembered
	 * by this manager are destroyed.
	 */
	void shutdown();
}

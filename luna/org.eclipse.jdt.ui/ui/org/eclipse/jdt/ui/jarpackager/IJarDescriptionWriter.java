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
package org.eclipse.jdt.ui.jarpackager;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

/**
 * Writes the description file of a JAR package data object.
 * <p>
 * The format is defined by the client who implements the
 * reader/writer pair.
 * </p>
 *
 * @see org.eclipse.jdt.ui.jarpackager.JarPackageData
 * @see org.eclipse.jdt.ui.jarpackager.IJarDescriptionReader
 * @since 2.0
 */
public interface IJarDescriptionWriter {

	/**
	 * Writes the JAR package data to the description file
	 * to to the underlying stream.
	 * <p>
     * It is the client's responsibility to close this writer.
	 * </p>
	 *
	 * @param jarPackage the jar package data to write
	 *
	 * @throws CoreException	if writing fails, e.g. I/O error during write operation
	 */
	void write(JarPackageData jarPackage) throws CoreException;

	/**
     * Closes this writer.
	 * <p>
     * It is the client's responsibility to close this writer.
	 * </p>
     *
	 * @throws CoreException	if closing fails, e.g. I/O error during close operation
     */
    public void close() throws CoreException;

	/**
	 * Returns the status of this reader.
	 * If there were any errors, the result is a status object containing
	 * individual status objects for each error.
	 * If there were no errors, the result is a status object with error code <code>OK</code>.
	 *
	 * @return the status of this operation
	 */
	public IStatus getStatus();
}

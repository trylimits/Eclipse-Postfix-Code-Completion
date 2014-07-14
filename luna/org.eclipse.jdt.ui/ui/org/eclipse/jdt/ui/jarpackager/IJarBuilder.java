/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 83258 [jar exporter] Deploy java application as executable jar
 *******************************************************************************/
package org.eclipse.jdt.ui.jarpackager;

import java.util.zip.ZipFile;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.MultiStatus;

import org.eclipse.core.resources.IFile;

/**
 * A JAR builder can be used to add elements to a
 * JAR file which is about to be build.
 * <p>
 * The protocol defined by this interface is:
 * <ul>
 * <li>open is called</li>
 * <li>addFile and addJar is called multiple times</li>
 * <li>close is called</li>
 * </ul>
 * It is guaranteed that addFile and addJar is only called after
 * open is called and before close is called. Other methods may
 * be called any time.</p>
 * Implementors must be prepared that an instance if the implementation
 * is reused multiple times.<p>
 * <p>
 * In order to provide backward compatibility for clients of <code>IJarBuilder</code>, extension
 * interfaces are used to provide a means of evolution. The following extension interfaces exist:
 * <ul>
 * <li>{@link org.eclipse.jdt.ui.jarpackager.IJarBuilderExtension} since version 3.5 introducing
 *		the following functions:
 *			<ul>
 *				<li>Write a file to the JAR to build</li>
 *			</ul>
 * </li>
 *
 * @see org.eclipse.jdt.ui.jarpackager.JarPackageData
 * @since 3.4
 */
public interface IJarBuilder {

	/**
	 * Returns the unique id of this builder
	 *
	 * @return the unique id of this builder
	 */
	public String getId();

	/**
	 * Returns the manifest provider to build the manifest
	 *
	 * @return the manifest provider to build the manifest
	 */
	public IManifestProvider getManifestProvider();

	/**
	 * Called when building of the JAR starts
	 *
	 * @param jarPackage
	 *        the package to build
	 * @param shell
	 *        shell to show dialogs, <b>null</b> if no dialog must be shown
	 * @param status
	 *        a status to use to report status to the user
	 * @throws CoreException thrown when the JAR could not be opened
	 */
	public void open(JarPackageData jarPackage, Shell shell, MultiStatus status) throws CoreException;

	/**
	 * Add the given resource to the archive at the given path
	 *
	 * @param resource
	 *        the file to be written
	 * @param destinationPath
	 *        the path for the file inside the archive
	 * @throws CoreException
	 *        thrown when the file could not be written
	 */
	public void writeFile(IFile resource, IPath destinationPath) throws CoreException;

	/**
	 * Add the given archive to the archive which is about to be build
	 *
	 * @param archive
	 *        the archive to add
	 * @param monitor
	 *        a monitor to report progress to
	 */
	public void writeArchive(ZipFile archive, IProgressMonitor monitor);

	/**
	 * Called when building of the JAR finished.
	 *
	 * @throws CoreException
	 *        thrown when the JAR could not be closed
	 */
	public void close() throws CoreException;

}

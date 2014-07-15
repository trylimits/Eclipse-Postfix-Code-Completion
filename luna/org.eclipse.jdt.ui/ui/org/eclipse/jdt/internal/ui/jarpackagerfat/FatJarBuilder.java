/*******************************************************************************
 * Copyright (c) 2007, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 83258 [jar exporter] Deploy java application as executable jar
 *     Ferenc Hechler <ferenc_hechler@users.sourceforge.net> - [jar application] add Jar-in-Jar ClassLoader option - https://bugs.eclipse.org/bugs/show_bug.cgi?id=219530
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackagerfat;

import java.io.File;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.MultiStatus;

import org.eclipse.core.resources.IFile;

import org.eclipse.jdt.ui.jarpackager.IJarBuilderExtension;
import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.jarpackager.JarBuilder;

/**
 * A builder which is able to handle referenced libraries.
 * 
 * @since 3.4
 */
public abstract class FatJarBuilder extends JarBuilder implements IJarBuilderExtension {

	private JarPackageData fJarPackage;
	private JarWriter4 fJarWriter;

	protected JarWriter4 getJarWriter() {
		return fJarWriter;
	}

	public abstract boolean isRemoveSigners();

	public abstract boolean isMergeManifests();

	public abstract String getManifestClasspath();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void open(JarPackageData jarPackage, Shell displayShell, MultiStatus status) throws CoreException {
		super.open(jarPackage, displayShell, status);
		fJarPackage= jarPackage;
		fJarWriter= new JarWriter4(fJarPackage, displayShell);
	}

	/**
	 * {@inheritDoc}
	 */
	public void writeFile(IFile resource, IPath destinationPath) throws CoreException {
		fJarWriter.write(resource, destinationPath);
	}

	public void writeFile(File file, IPath destinationPath) throws CoreException {
		fJarWriter.write(file, destinationPath);
	}

	/**
	 * {@inheritDoc}
	 */
	public void close() throws CoreException {
		if (fJarWriter != null) {
			fJarWriter.close();
		}
	}

}

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

import java.util.jar.Manifest;

import org.eclipse.core.runtime.CoreException;

/**
 * A manifest provider creates manifest files.
 *
 * Clients may implement this interface.
 *
 * @see java.util.jar.Manifest
 * @since 2.0
 */
public interface IManifestProvider {

	/**
	 * Creates a manifest as defined by the <code>JarPackage</code>.
	 *
	 * @param	jarPackage		the JAR package specification
	 * @return  the created manifest
	 * @throws	CoreException	if access to any resource described by the JAR package has failed
	 */
	Manifest create(JarPackageData jarPackage) throws CoreException;

	/**
	 * Creates a default manifest.
	 *
	 * @param manifestVersion	a string denoting the manifest version
	 * @return the created manifest
	 */
	Manifest createDefault(String manifestVersion);
}

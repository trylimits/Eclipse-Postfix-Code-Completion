/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 262748 [jar exporter] extract constants for string literals in JarRsrcLoader et al.
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackagerfat;


/**
 * Constants used in the fat Jar packager.
 * 
 * Some of these are duplicated in JIJConstants in the source for the jar in jar loader:
 * {@link org.eclipse.jdt.internal.jarinjarloader}.
 * 
 * @since 3.6
 */
final class JIJConstants {
	
	static final String REDIRECTED_CLASS_PATH_MANIFEST_NAME  = "Rsrc-Class-Path";  //$NON-NLS-1$
	static final String REDIRECTED_MAIN_CLASS_MANIFEST_NAME  = "Rsrc-Main-Class";  //$NON-NLS-1$
	static final String CURRENT_DIR                          = "./";  //$NON-NLS-1$
	
	/**
	 * This is <code>{@link org.eclipse.jdt.internal.jarinjarloader.JarRsrcLoader}.class.getName()</code>,
	 * but that's not visible for the PDE builder when building the org.eclipse.jdt.ui plug-in.
	 */
	static final String LOADER_MAIN_CLASS                    = "org.eclipse.jdt.internal.jarinjarloader.JarRsrcLoader";  //$NON-NLS-1$
}

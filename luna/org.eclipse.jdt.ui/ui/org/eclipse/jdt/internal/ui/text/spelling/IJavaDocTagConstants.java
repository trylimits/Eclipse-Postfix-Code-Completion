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
package org.eclipse.jdt.internal.ui.text.spelling;


/**
 * Javadoc tag constants.
 *
 * @since 3.0
 */
public interface IJavaDocTagConstants {


	/** Javadoc link tags */
	public static final String[] JAVADOC_LINK_TAGS= new String[] { "@docRoot", "@inheritDoc", "@link", "@linkplain" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

	/** Javadoc parameter tags */
	public static final String[] JAVADOC_PARAM_TAGS= new String[] { "@exception", "@param", "@serialField", "@throws"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

	/** Javadoc reference tags */
	public static final String[] JAVADOC_REFERENCE_TAGS= new String[] { "@see" }; //$NON-NLS-1$

	/** Javadoc root tags */
	public static final String[] JAVADOC_ROOT_TAGS= new String[] { "@author", "@deprecated", "@return", "@see", "@serial", "@serialData", "@since", "@version", "@inheritDoc", "@category", "@value", "@literal", "@code", "@noinstantiate", "@noreference", "@noimplement", "@noextend", "@nooverride" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$ //$NON-NLS-15$ //$NON-NLS-16$ //$NON-NLS-17$ //$NON-NLS-18$

	/** Javadoc tag prefix */
	public static final char JAVADOC_TAG_PREFIX= '@';
}

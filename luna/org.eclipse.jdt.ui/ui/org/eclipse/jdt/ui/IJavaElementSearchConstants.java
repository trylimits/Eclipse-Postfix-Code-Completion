/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui;

/**
 * Search scope constants for Java selection dialogs.
 * <p>
 * This interface declares constants only; it is not intended to be implemented.
 * </p>
 *
 * @see JavaUI
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface IJavaElementSearchConstants {

	/**
	 * Search scope constant indicating that classes should be considered.
	 * Used when opening certain kinds of selection dialogs.
	 */
	public static final int CONSIDER_CLASSES= 1 << 1;

	/**
	 * Search scope constant indicating that interfaces should be considered.
	 * Used when opening certain kinds of selection dialogs.
	 */
	public static final int CONSIDER_INTERFACES= 1 << 2;

	/**
	 * Search scope constant indicating that both classes and interfaces
	 * should be considered. Equivalent to
	 * <code>CONSIDER_CLASSES | CONSIDER_INTERFACES</code>.
	 *
	 * @deprecated use CONSIDER_ALL_TYPES or CONSIDER_CLASSES_AND_INTERFACES instead
	 */
	public static final int CONSIDER_TYPES= CONSIDER_CLASSES | CONSIDER_INTERFACES;

	/**
	 * Search scope constant (bit mask) indicating that binaries should be considered.
	 * Used when opening certain kinds of selection dialogs.
	 */
	public static final int CONSIDER_BINARIES= 1 << 3;

	/**
	 * Search scope constant (bit mask) indicating that external JARs should be considered.
	 * Used when opening certain kinds of selection dialogs.
	 */
	public static final int CONSIDER_EXTERNAL_JARS= 1 << 4;

	/**
	 * Search scope constant (bit mask) indicating that required projects should be considered.
	 * Used when opening certain kinds of selection dialogs.
	 *
	 * @since 2.0
	 */
	public static final int CONSIDER_REQUIRED_PROJECTS= 1 << 5;

	/**
	 * Search scope constant indicating that annotation types should be considered.
	 * Used when opening certain kinds of selection dialogs.
	 *
	 * @since 3.1
	 */
	public static final int CONSIDER_ANNOTATION_TYPES= 1 << 6;

	/**
	 * Search scope constant indicating that enums should be considered.
	 * Used when opening certain kinds of selection dialogs.
	 *
	 * @since 3.1
	 */
	public static final int CONSIDER_ENUMS= 1 << 7;

	/**
	 * Search scope constant indicating that classes, interfaces, annotations
	 * and enums should be considered.
	 *
	 * @since 3.1
	 */
	public static final int CONSIDER_ALL_TYPES= 1 << 8;

	/**
	 * Search scope constant indicating that only classes and interfaces
	 * should be considered.
	 *
	 * @since 3.1
	 */
	public static final int CONSIDER_CLASSES_AND_INTERFACES= 1 << 9;

	/**
	 * Search scope constant indicating that only classes and enumeration types
	 * should be considered.
	 *
	 * @since 3.1
	 */
	public static final int CONSIDER_CLASSES_AND_ENUMS= 1 << 10;
	
	/**
	 * Search scope constant indicating that interfaces and annotation types
	 * should be considered.
	 * 
	 * @since 3.8
	 */
	public static final int CONSIDER_INTERFACES_AND_ANNOTATIONS= 1 << 11;
}

/*******************************************************************************
 * Copyright (c) 2005, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.text.folding;

import org.eclipse.jdt.core.IJavaElement;

/**
 * Extends {@link IJavaFoldingStructureProvider} with the following
 * functions:
 * <ul>
 * <li>collapsing of comments and members</li>
 * <li>expanding and collapsing of certain java elements</li>
 * </ul>
 *
 * @since 3.2
 */
public interface IJavaFoldingStructureProviderExtension {
	/**
	 * Collapses all members except for top level types.
	 */
	void collapseMembers();

	/**
	 * Collapses all comments.
	 */
	void collapseComments();

	/**
	 * Collapses the given elements.
	 *
	 * @param elements the java elements to collapse (the array and its elements must not be
	 *        modified)
	 */
	void collapseElements(IJavaElement[] elements);

	/**
	 * Expands the given elements.
	 *
	 * @param elements the java elements to expand (the array and its elements must not be modified)
	 */
	void expandElements(IJavaElement[] elements);
}

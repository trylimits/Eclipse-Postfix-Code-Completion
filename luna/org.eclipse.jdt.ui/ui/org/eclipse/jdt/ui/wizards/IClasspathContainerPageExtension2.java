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
package org.eclipse.jdt.ui.wizards;

import org.eclipse.jdt.core.IClasspathEntry;

/**
 * Classpath container pages that implement {@link IClasspathContainerPage} can
 * optionally implement {@link IClasspathContainerPageExtension2} to return more
 * than one element when creating new containers. If implemented, the method {@link #getNewContainers()}
 * is used instead of the method {@link IClasspathContainerPage#getSelection() } to get the
 * newly selected containers. {@link IClasspathContainerPage#getSelection() } is still used
 * for edited elements.
 *
 * @since 3.0
 */
public interface IClasspathContainerPageExtension2 {

	/**
	 * Method {@link #getNewContainers()} is called instead of {@link IClasspathContainerPage#getSelection() }
	 * to get the newly added containers. {@link IClasspathContainerPage#getSelection() } is still used
	 * to get the edited elements.
	 * @return the classpath entries created on the page. All returned entries must be on kind
	 * {@link IClasspathEntry#CPE_CONTAINER}
	 */
	public IClasspathEntry[] getNewContainers();

}

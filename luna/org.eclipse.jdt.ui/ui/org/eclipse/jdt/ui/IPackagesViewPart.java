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

import org.eclipse.jface.viewers.TreeViewer;

import org.eclipse.ui.IViewPart;

/**
 * The standard Packages view presents a Java-centric view of the workspace.
 * Within Java projects, the resource hierarchy is organized into Java packages
 * as described by the project's classpath. Note that this view shows both Java
 * elements and ordinary resources.
 * <p>
 * This interface is not intended to be implemented by clients.
 * </p>
 *
 * @see JavaUI#ID_PACKAGES
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface IPackagesViewPart extends IViewPart {
	/**
	 * Selects and reveals the given element in this packages view.
	 * The tree will be expanded as needed to show the element.
	 *
	 * @param element the element to be revealed
	 */
	void selectAndReveal(Object element);

	/**
	 * Returns the TreeViewer shown in the Packages view.
	 *
	 * @return the tree viewer used in the Packages view
	 *
	 * @since 2.0
	 */
	TreeViewer getTreeViewer();

    /**
     * Returns whether this Packages view's selection automatically tracks the active editor.
     *
     * @return <code>true</code> if linking is enabled, <code>false</code> if not
     * @since 3.2
     */
    boolean isLinkingEnabled();

    /**
     * Sets whether this Packages view's selection automatically tracks the active editor.
     *
     * @param enabled <code>true</code> to enable, <code>false</code> to disable
     * @since 3.2
     */
    void setLinkingEnabled(boolean enabled);
}

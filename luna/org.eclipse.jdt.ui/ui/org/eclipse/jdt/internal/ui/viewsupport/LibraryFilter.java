/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.viewsupport;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.jdt.core.IPackageFragmentRoot;

/**
 * Filters out all elements which libraries
 */
public class LibraryFilter extends ViewerFilter {

	/**
	 * Returns the result of this filter, when applied to the
	 * given inputs.
	 *
	 * @param viewer the viewer
	 * @param parent the parent element
	 * @param element the element to test
	 * @return Returns true if element should be included in filtered set
	 */
	@Override
	public boolean select(Viewer viewer, Object parent, Object element) {
		if (element instanceof IPackageFragmentRoot)
			return !((IPackageFragmentRoot)element).isArchive();
		return true;
	}
}

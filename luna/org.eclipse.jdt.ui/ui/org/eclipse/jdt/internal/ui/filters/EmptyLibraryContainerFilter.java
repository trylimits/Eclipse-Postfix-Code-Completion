/*******************************************************************************
 * Copyright (c) 2008, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.filters;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.jdt.internal.ui.packageview.PackageFragmentRootContainer;
import org.eclipse.jdt.internal.ui.viewsupport.ProblemTreeViewer;

/**
 * The library container filter is a filter used to determine whether
 * library containers are shown that are empty or have all children filtered out by other filters.
 * The filter is only applicable on a {@link ProblemTreeViewer}
 */
public class EmptyLibraryContainerFilter extends ViewerFilter {

	/* (non-Javadoc)
	 * Method declared on ViewerFilter.
	 */
	@Override
	public boolean select(Viewer viewer, Object parentElement, Object element) {
		if (element instanceof PackageFragmentRootContainer && viewer instanceof ProblemTreeViewer) {
			return ((ProblemTreeViewer) viewer).hasFilteredChildren(element);
		}
		return true;
	}
}

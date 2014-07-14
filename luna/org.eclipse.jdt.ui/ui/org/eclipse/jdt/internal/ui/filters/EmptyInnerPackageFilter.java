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
package org.eclipse.jdt.internal.ui.filters;


import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;


/**
 * Filters empty non-leaf package fragments
 */
public class EmptyInnerPackageFilter extends ViewerFilter {

	/*
	 * @see ViewerFilter
	 */
	@Override
	public boolean select(Viewer viewer, Object parent, Object element) {
		if (element instanceof IPackageFragment) {
			IPackageFragment pkg= (IPackageFragment)element;
			try {
				if (pkg.isDefaultPackage())
					return pkg.hasChildren();
				return !pkg.hasSubpackages() || pkg.hasChildren() || EmptyPackageFilter.hasUnfilteredResources(viewer, pkg);
			} catch (JavaModelException e) {
				return false;
			}
		}

		return true;
	}
}

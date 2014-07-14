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
package org.eclipse.jdt.internal.ui.jarpackager;

import java.util.List;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;

/**
 * Only selects packages (i.e. IPackageFragments) which are in
 * the initial packages list and parent types (i.e. package fragment
 * root, Java project and Java model)
 */
class SealPackagesFilter  extends ViewerFilter {

	private List<IPackageFragment> fAllowedPackages;

	public SealPackagesFilter(List<IPackageFragment> packages) {
		fAllowedPackages= packages;
	}


	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ViewerFilter#select(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
	 */
	@Override
	public boolean select(Viewer viewer, Object parent, Object element) {
		if (element instanceof IJavaElement) {
			int type= ((IJavaElement)element).getElementType();
			if (type == IJavaElement.JAVA_MODEL || type == IJavaElement.JAVA_PROJECT || type ==IJavaElement.PACKAGE_FRAGMENT_ROOT)
				return true;
			return (type == IJavaElement.PACKAGE_FRAGMENT && fAllowedPackages.contains(element));

		}
		else
			return false;
	}
}

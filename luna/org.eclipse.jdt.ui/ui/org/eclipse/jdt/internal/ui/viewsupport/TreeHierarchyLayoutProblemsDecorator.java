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

import org.eclipse.jdt.core.IPackageFragment;

import org.eclipse.jdt.ui.JavaElementImageDescriptor;
import org.eclipse.jdt.ui.ProblemsLabelDecorator;

import org.eclipse.jdt.internal.ui.browsing.LogicalPackage;

/**
 * Special problem decorator for hierarchical package layout.
 * <p>
 * It only decorates package fragments which are not covered by the
 * <code>ProblemsLabelDecorator</code>.
 * </p>
 *
 * @see org.eclipse.jdt.ui.ProblemsLabelDecorator
 * @since 2.1
 */
public class TreeHierarchyLayoutProblemsDecorator extends ProblemsLabelDecorator {

	private boolean fIsFlatLayout;

	public TreeHierarchyLayoutProblemsDecorator() {
		this(false);
	}

	public TreeHierarchyLayoutProblemsDecorator(boolean isFlatLayout) {
		super(null);
		fIsFlatLayout= isFlatLayout;
	}

	protected int computePackageAdornmentFlags(IPackageFragment fragment) {
		if (!fIsFlatLayout && !fragment.isDefaultPackage()) {
			return super.computeAdornmentFlags(fragment.getResource());
		}
		return super.computeAdornmentFlags(fragment);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.ProblemsLabelDecorator#computeAdornmentFlags(java.lang.Object)
	 */
	@Override
	protected int computeAdornmentFlags(Object element) {
		if (element instanceof IPackageFragment) {
			return computePackageAdornmentFlags((IPackageFragment) element);
		} else if (element instanceof LogicalPackage) {
			IPackageFragment[] fragments= ((LogicalPackage) element).getFragments();
			int res= 0;
			for (int i= 0; i < fragments.length; i++) {
				int flags= computePackageAdornmentFlags(fragments[i]);
				if (flags == JavaElementImageDescriptor.ERROR) {
					return flags;
				} else if (flags != 0) {
					res= flags;
				}
			}
			return res;
		}
		return super.computeAdornmentFlags(element);
	}

	public void setIsFlatLayout(boolean state) {
		fIsFlatLayout= state;
	}

}

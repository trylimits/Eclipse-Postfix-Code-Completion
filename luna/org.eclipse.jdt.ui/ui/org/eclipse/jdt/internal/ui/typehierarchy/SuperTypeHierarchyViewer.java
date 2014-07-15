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
package org.eclipse.jdt.internal.ui.typehierarchy;

import java.util.List;

import org.eclipse.swt.widgets.Composite;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;

/**
 * A viewer including the content provider for the supertype hierarchy.
 * Used by the TypeHierarchyViewPart which has to provide a TypeHierarchyLifeCycle
 * on construction (shared type hierarchy)
 */
public class SuperTypeHierarchyViewer extends TypeHierarchyViewer {

	public SuperTypeHierarchyViewer(Composite parent, TypeHierarchyLifeCycle lifeCycle) {
		super(parent, new SuperTypeHierarchyContentProvider(lifeCycle), lifeCycle);
	}

	/*
	 * @see TypeHierarchyViewer#updateContent
	 */
	@Override
	public void updateContent(boolean expand) {
		getTree().setRedraw(false);
		refresh();
		if (expand) {
			expandAll();
		}
		getTree().setRedraw(true);
	}

	/*
	 * Content provider for the supertype hierarchy
	 */
	public static class SuperTypeHierarchyContentProvider extends TypeHierarchyContentProvider {
		public SuperTypeHierarchyContentProvider(TypeHierarchyLifeCycle lifeCycle) {
			super(lifeCycle);
		}

		@Override
		protected final void getTypesInHierarchy(IType type, List<IType> res) {
			ITypeHierarchy hierarchy= getHierarchy();
			if (hierarchy != null) {
				IType[] types= hierarchy.getSupertypes(type);
				for (int i= 0; i < types.length; i++) {
					res.add(types[i]);
				}
			}
		}

		@Override
		protected IType getParentType(IType type) {
			// cant handle
			return null;
		}

	}

}

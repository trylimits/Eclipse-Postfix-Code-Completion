/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 *          (report 36180: Callers/Callees view)
 *   Michael Fraenkel (fraenkel@us.ibm.com) - patch
 *          (report 60714: Call Hierarchy: display search scope in view title)
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import org.eclipse.jface.action.Action;

import org.eclipse.jdt.core.search.IJavaSearchScope;


abstract class SearchScopeAction extends Action {
	private final SearchScopeActionGroup fGroup;

	public SearchScopeAction(SearchScopeActionGroup group, String text) {
		super(text, AS_RADIO_BUTTON);
		this.fGroup = group;
	}

	/**
	 * Fetches the search scope with the appropriate include mask.
	 * 
	 * @param includeMask the include mask
	 * @return the search scope with the appropriate include mask
	 * @since 3.7
	 */
	public abstract IJavaSearchScope getSearchScope(int includeMask);

	public abstract int getSearchScopeType();

	/* (non-Javadoc)
	 * @see org.eclipse.jface.action.Action#run()
	 */
	@Override
	public void run() {
		this.fGroup.setSelected(this, true);
		CallHierarchyViewPart part= this.fGroup.getView();
		part.setInputElements(part.getInputElements());
	}

	/**
	 * Fetches the description of the scope with the appropriate include mask.
	 * 
	 * @param includeMask the include mask
	 * @return the description of the scope with the appropriate include mask
	 * @since 3.7
	 */
	public abstract String getFullDescription(int includeMask);
}

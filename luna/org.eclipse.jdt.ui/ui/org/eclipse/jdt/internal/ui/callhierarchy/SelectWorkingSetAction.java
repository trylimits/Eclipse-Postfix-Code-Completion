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
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import org.eclipse.jface.action.Action;

import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.search.JavaSearchScopeFactory;

class SelectWorkingSetAction extends Action {
	private final SearchScopeActionGroup fGroup;

	public SelectWorkingSetAction(SearchScopeActionGroup group) {
		super(CallHierarchyMessages.SearchScopeActionGroup_workingset_select_text);
		this.fGroup = group;
		setToolTipText(CallHierarchyMessages.SearchScopeActionGroup_workingset_select_tooltip);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.CALL_HIERARCHY_SEARCH_SCOPE_ACTION);
	}
	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.jface.action.Action#run()
	 */
	@Override
	public void run() {
		try {
			IWorkingSet[] workingSets;
			workingSets = JavaSearchScopeFactory.getInstance().queryWorkingSets();
			if (workingSets != null) {
				this.fGroup.setActiveWorkingSets(workingSets);
				SearchUtil.updateLRUWorkingSets(workingSets);
			} else {
				this.fGroup.setActiveWorkingSets(null);
			}
		} catch (InterruptedException e) {
			// cancel pressed
		}
	}
}

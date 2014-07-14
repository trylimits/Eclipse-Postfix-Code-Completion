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
 *   Stephan Herrmann (stephan@cs.tu-berlin.de):
 *          - bug 75800: [call hierarchy] should allow searches for fields
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import java.util.HashSet;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.search.JavaSearchScopeFactory;


class SearchScopeProjectAction extends SearchScopeAction {
	private final SearchScopeActionGroup fGroup;

	public SearchScopeProjectAction(SearchScopeActionGroup group) {
		super(group, CallHierarchyMessages.SearchScopeActionGroup_project_text);
		this.fGroup= group;
		setToolTipText(CallHierarchyMessages.SearchScopeActionGroup_project_tooltip);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.CALL_HIERARCHY_SEARCH_SCOPE_ACTION);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.callhierarchy.SearchScopeAction#getSearchScope(int)
	 */
	@Override
	public IJavaSearchScope getSearchScope(int includeMask) {
		IMember[] members= fGroup.getView().getInputElements();
		if (members == null) {
			return null;
		}

		HashSet<IJavaProject> projects= new HashSet<IJavaProject>();
		for (int i= 0; i < members.length; i++) {
			projects.add(members[i].getJavaProject());
		}
		return SearchEngine.createJavaSearchScope(
				projects.toArray(new IJavaProject[projects.size()]),
				includeMask);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.callhierarchy.SearchScopeActionGroup.SearchScopeAction#getSearchScopeType()
	 */
	@Override
	public int getSearchScopeType() {
		return SearchScopeActionGroup.SEARCH_SCOPE_TYPE_PROJECT;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.callhierarchy.SearchScopeAction#getFullDescription()
	 */
	@Override
	public String getFullDescription(int includeMask) {
		IMember[] members= fGroup.getView().getInputElements();
		if (members != null) {
			HashSet<String> projectNames= new HashSet<String>();
			for (int i= 0; i < members.length; i++) {
				projectNames.add(members[i].getJavaProject().getElementName());
			}
			JavaSearchScopeFactory factory= JavaSearchScopeFactory.getInstance();
			return factory.getProjectScopeDescription(
					projectNames.toArray(new String[projectNames.size()]),
					includeMask);
		}
		return ""; //$NON-NLS-1$
	}
}

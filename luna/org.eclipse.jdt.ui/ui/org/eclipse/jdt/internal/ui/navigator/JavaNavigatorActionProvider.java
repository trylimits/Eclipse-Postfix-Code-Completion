/*******************************************************************************
 * Copyright (c) 2003, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.navigator;

import org.eclipse.jface.action.IMenuManager;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.navigator.CommonActionProvider;
import org.eclipse.ui.navigator.ICommonActionExtensionSite;
import org.eclipse.ui.navigator.ICommonViewerWorkbenchSite;

import org.eclipse.jdt.ui.actions.CCPActionGroup;
import org.eclipse.jdt.ui.actions.GenerateActionGroup;
import org.eclipse.jdt.ui.actions.JavaSearchActionGroup;
import org.eclipse.jdt.ui.actions.OpenViewActionGroup;

import org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage.GenerateBuildPathActionGroup;

/**
 * Contributes the following actions to the menu on behalf of the JDT content
 * extension.
 *
 * <ul>
 * <li>{@link OpenViewActionGroup}. Contributes the Open View actions (type
 * hierarchy, call hierarchy, etc).</li>
 * <li>{@link CCPActionGroup}. Contributes the Cut/Copy/Paste/Delete.</li>
 * <li>{@link JavaSearchActionGroup}. Contributes the search functionality (find in workspace, project, etc).</li>
 * <li>{@link GenerateBuildPathActionGroup}. Contributes the "Build Path>" convenience submenu option.</li>
 * <li>{@link GenerateActionGroup}. Contributes the "Source>" and "Generate>" submenu .</li>
 * </ul>
 */
public class JavaNavigatorActionProvider extends CommonActionProvider {

	private OpenViewActionGroup fOpenViewGroup;

	private CCPActionGroup fCCPGroup;

	private JavaSearchActionGroup fSearchGroup;

	private GenerateBuildPathActionGroup fBuildPathGroup;

	private GenerateActionGroup fGenerateGroup;

	private boolean fInViewPart= false;

	@Override
	public void fillActionBars(IActionBars actionBars) {
		if (fInViewPart) {
			fOpenViewGroup.fillActionBars(actionBars);
			fCCPGroup.fillActionBars(actionBars);
			fBuildPathGroup.fillActionBars(actionBars);
			fGenerateGroup.fillActionBars(actionBars);
			fSearchGroup.fillActionBars(actionBars);
		}
	}

	@Override
	public void fillContextMenu(IMenuManager menu) {
		if (fInViewPart) {
			fOpenViewGroup.fillContextMenu(menu);
			fCCPGroup.fillContextMenu(menu);
			fBuildPathGroup.fillContextMenu(menu);
			fGenerateGroup.fillContextMenu(menu);
			fSearchGroup.fillContextMenu(menu);
		}
	}

	@Override
	public void init(ICommonActionExtensionSite site) {

		ICommonViewerWorkbenchSite workbenchSite= null;
		if (site.getViewSite() instanceof ICommonViewerWorkbenchSite)
			workbenchSite= (ICommonViewerWorkbenchSite) site.getViewSite();

		if (workbenchSite != null) {
			if (workbenchSite.getPart() != null && workbenchSite.getPart() instanceof IViewPart) {
				IViewPart viewPart= (IViewPart) workbenchSite.getPart();

				fOpenViewGroup= new OpenViewActionGroup(viewPart, site.getStructuredViewer());
				fOpenViewGroup.containsOpenPropertiesAction(false);
				fCCPGroup= new CCPActionGroup(viewPart);
				fGenerateGroup= new GenerateActionGroup(viewPart);
				fSearchGroup= new JavaSearchActionGroup(viewPart);
				fBuildPathGroup= new GenerateBuildPathActionGroup(viewPart);

				fInViewPart= true;
			}
		}
	}

	@Override
	public void setContext(ActionContext context) {
		super.setContext(context);
		if (fInViewPart) {
			fOpenViewGroup.setContext(context);
			fCCPGroup.setContext(context);
			fGenerateGroup.setContext(context);
			fSearchGroup.setContext(context);
			fBuildPathGroup.setContext(context);
		}
	}

	/*
	 * @see org.eclipse.ui.actions.ActionGroup#dispose()
	 * @since 3.5
	 */
	@Override
	public void dispose() {
		if (fInViewPart) {
			fOpenViewGroup.dispose();
			fCCPGroup.dispose();
			fSearchGroup.dispose();
			fBuildPathGroup.dispose();
			fGenerateGroup.dispose();
		}
		super.dispose();
	}
}

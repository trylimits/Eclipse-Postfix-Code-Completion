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
package org.eclipse.jdt.ui.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.part.Page;

import org.eclipse.jdt.ui.IContextMenuConstants;

import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart;

/**
 * Action group that adds the show actions to a context menu and the action bar's navigate menu.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 * 
 * @noextend This class is not intended to be subclassed by clients.
 * @deprecated As of 3.5, got replaced by generic Navigate &gt; Show In &gt;
 */
public class ShowActionGroup extends ActionGroup {

	private boolean fIsPackageExplorer;

	private IWorkbenchSite fSite;
	private ShowInPackageViewAction fShowInPackagesViewAction;

	/**
	 * Creates a new <code>ShowActionGroup</code>. The action requires
	 * that the selection provided by the page's selection provider is of type
	 * <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param page the page that owns this action group
	 */
	public ShowActionGroup(Page page) {
		this(page.getSite());
	}

	/**
	 * Creates a new <code>ShowActionGroup</code>. The action requires
	 * that the selection provided by the part's selection provider is of type
	 * <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param part the view part that owns this action group
	 */
	public ShowActionGroup(IViewPart part) {
		this(part.getSite());
		fIsPackageExplorer= part instanceof PackageExplorerPart;
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param part the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public ShowActionGroup(JavaEditor part) {
		fShowInPackagesViewAction= new ShowInPackageViewAction(part);
		fShowInPackagesViewAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SHOW_IN_PACKAGE_VIEW);
		part.setAction("ShowInPackageView", fShowInPackagesViewAction); //$NON-NLS-1$

		initialize(part.getSite(), true);
	}

	private ShowActionGroup(IWorkbenchSite site) {
		fShowInPackagesViewAction= new ShowInPackageViewAction(site);
		fShowInPackagesViewAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SHOW_IN_PACKAGE_VIEW);

		initialize(site , false);
	}

	private void initialize(IWorkbenchSite site, boolean isJavaEditor) {
		fSite= site;
		ISelectionProvider provider= fSite.getSelectionProvider();
		ISelection selection= provider.getSelection();
		fShowInPackagesViewAction.update(selection);
		if (!isJavaEditor) {
			provider.addSelectionChangedListener(fShowInPackagesViewAction);
		}
	}

	/* (non-Javadoc)
	 * Method declared in ActionGroup
	 */
	@Override
	public void fillActionBars(IActionBars actionBar) {
		super.fillActionBars(actionBar);
		setGlobalActionHandlers(actionBar);
	}

	/* (non-Javadoc)
	 * Method declared in ActionGroup
	 */
	@Override
	public void fillContextMenu(IMenuManager menu) {
		super.fillContextMenu(menu);
		if (!fIsPackageExplorer) {
			appendToGroup(menu, fShowInPackagesViewAction);
		}
	}

	/*
	 * @see ActionGroup#dispose()
	 */
	@Override
	public void dispose() {
		ISelectionProvider provider= fSite.getSelectionProvider();
		provider.removeSelectionChangedListener(fShowInPackagesViewAction);
		super.dispose();
	}

	private void setGlobalActionHandlers(IActionBars actionBar) {
		if (!fIsPackageExplorer)
			actionBar.setGlobalActionHandler(JdtActionConstants.SHOW_IN_PACKAGE_VIEW, fShowInPackagesViewAction);
	}

	private void appendToGroup(IMenuManager menu, IAction action) {
		if (action.isEnabled())
			menu.appendToGroup(IContextMenuConstants.GROUP_SHOW, action);
	}
}

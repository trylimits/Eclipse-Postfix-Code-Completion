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

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.IShellProvider;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.actions.BuildAction;
import org.eclipse.ui.ide.IDEActionFactory;

import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.ui.IContextMenuConstants;

import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.viewsupport.IRefreshable;

/**
 * Contributes all build related actions to the context menu and installs handlers for the
 * corresponding global menu actions.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class BuildActionGroup extends ActionGroup {

	private static class RefreshableViewRefreshAction extends RefreshAction {
		private final IViewPart fPart;
		public RefreshableViewRefreshAction(IViewPart part) {
			super(part.getSite());
			fPart= part;
		}

		@Override
		public void run(IStructuredSelection selection) {
			super.run(selection);
			if (fPart instanceof IRefreshable) {
				((IRefreshable) fPart).refresh(selection);
			}
		}
	}

	/**
	 * Adapts a shell the a shell provider.
	 *
	 * @since 3.4
	 */
	private static class ShellProviderAdapter implements IShellProvider {

		private final Shell fShell;

		public ShellProviderAdapter(Shell shell) {
			fShell= shell;
		}

		/*
		 * @see org.eclipse.jface.window.IShellProvider#getShell()
		 */
		public Shell getShell() {
			return fShell;
		}
	}

	private final ISelectionProvider fSelectionProvider;
	private final BuildAction fBuildAction;
	private final RefreshAction fRefreshAction;

	/**
	 * Creates a new <code>BuildActionGroup</code>. The group requires that
	 * the selection provided by the view part's selection provider is of type
	 * <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param part the view part that owns this action group
	 */
	public BuildActionGroup(final IViewPart part) {
		this(part.getSite(), null, new RefreshableViewRefreshAction(part));
	}

	/**
	 * Creates a new <code>BuildActionGroup</code>. The group requires
	 * that the selection provided by the given selection provider is of type
	 * {@link IStructuredSelection}.
	 *
	 * @param site the site that will own the action group.
	 * @param specialSelectionProvider the selection provider used instead of the
	 *  sites selection provider.
	 *
	 * @since 3.4
	 */
	public BuildActionGroup(IWorkbenchSite site, ISelectionProvider specialSelectionProvider) {
		this(site, specialSelectionProvider, new RefreshAction(site));
	}

	private BuildActionGroup(IWorkbenchSite site, ISelectionProvider specialSelectionProvider, RefreshAction refreshAction) {
		fSelectionProvider= specialSelectionProvider != null ? specialSelectionProvider : site.getSelectionProvider();

		fBuildAction= new BuildAction(new ShellProviderAdapter(site.getShell()), IncrementalProjectBuilder.INCREMENTAL_BUILD);
		fBuildAction.setText(ActionMessages.BuildAction_label);
		fBuildAction.setActionDefinitionId(IWorkbenchCommandConstants.PROJECT_BUILD_PROJECT);

		fRefreshAction= refreshAction;
		fRefreshAction.setActionDefinitionId(IWorkbenchCommandConstants.FILE_REFRESH);

		if (specialSelectionProvider != null) {
			fRefreshAction.setSpecialSelectionProvider(specialSelectionProvider);
		}

		fSelectionProvider.addSelectionChangedListener(fBuildAction);
		fSelectionProvider.addSelectionChangedListener(fRefreshAction);
	}


	/**
	 * Returns the refresh action managed by this group.
	 *
	 * @return the refresh action. If this group doesn't manage a refresh action
	 * 	<code>null</code> is returned
	 */
	public IAction getRefreshAction() {
		return fRefreshAction;
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
		ISelection selection= getContext().getSelection();
		if (!ResourcesPlugin.getWorkspace().isAutoBuilding() && isBuildTarget(selection)) {
			appendToGroup(menu, fBuildAction);
		}
		appendToGroup(menu, fRefreshAction);
		super.fillContextMenu(menu);
	}

	/* (non-Javadoc)
	 * Method declared in ActionGroup
	 */
	@Override
	public void dispose() {
		fSelectionProvider.removeSelectionChangedListener(fBuildAction);
		fSelectionProvider.removeSelectionChangedListener(fRefreshAction);
		super.dispose();
	}

	private void setGlobalActionHandlers(IActionBars actionBar) {
		actionBar.setGlobalActionHandler(IDEActionFactory.BUILD_PROJECT.getId(), fBuildAction);
		actionBar.setGlobalActionHandler(ActionFactory.REFRESH.getId(), fRefreshAction);
	}

	private void appendToGroup(IMenuManager menu, IAction action) {
		if (action.isEnabled())
			menu.appendToGroup(IContextMenuConstants.GROUP_BUILD, action);
	}

	private boolean isBuildTarget(ISelection s) {
		if (!(s instanceof IStructuredSelection))
			return false;
		IStructuredSelection selection= (IStructuredSelection)s;
		if (selection.size() != 1)
			return false;
		return selection.getFirstElement() instanceof IJavaProject;
	}
}

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

import java.util.Iterator;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.actions.ActionGroup;

import org.eclipse.ui.texteditor.ITextEditorActionConstants;

import org.eclipse.jdt.ui.IContextMenuConstants;

import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.search.SearchMessages;
import org.eclipse.jdt.internal.ui.search.SearchUtil;

/**
 * Action group that adds the search for implementors actions to a
 * context menu and the global menu bar.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class ImplementorsSearchGroup extends ActionGroup  {

	private static final String MENU_TEXT= SearchMessages.group_implementors;

	private IWorkbenchSite fSite;
	private JavaEditor fEditor;
	private IActionBars fActionBars;

	private String fGroupId;

	private FindImplementorsAction fFindImplementorsAction;
	private FindImplementorsInProjectAction fFindImplementorsInProjectAction;
	private FindImplementorsInWorkingSetAction fFindImplementorsInWorkingSetAction;

	/**
	 * Creates a new <code>ImplementorsSearchGroup</code>. The group
	 * requires that the selection provided by the site's selection provider
	 * is of type <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the view part that owns this action group
	 */
	public ImplementorsSearchGroup(IWorkbenchSite site) {
		this(site, null);
	}

	/**
	 * Creates a new <code>ImplementorsSearchGroup</code>. The group requires
	 * that the selection provided by the given selection provider is of type
	 * {@link IStructuredSelection}.
	 *
	 * @param site the site that will own the action group.
	 * @param specialSelectionProvider the selection provider used instead of the
	 *  sites selection provider.
	 *
	 * @since 3.4
	 */
	public ImplementorsSearchGroup(IWorkbenchSite site, ISelectionProvider specialSelectionProvider) {
		fSite= site;
		fGroupId= IContextMenuConstants.GROUP_SEARCH;

		fFindImplementorsAction= new FindImplementorsAction(site);
		fFindImplementorsAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_IMPLEMENTORS_IN_WORKSPACE);

		fFindImplementorsInProjectAction= new FindImplementorsInProjectAction(site);
		fFindImplementorsInProjectAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_IMPLEMENTORS_IN_PROJECT);

		fFindImplementorsInWorkingSetAction= new FindImplementorsInWorkingSetAction(site);
		fFindImplementorsInWorkingSetAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_IMPLEMENTORS_IN_WORKING_SET);

		// register the actions as selection listeners
		ISelectionProvider provider= specialSelectionProvider == null ? fSite.getSelectionProvider() : specialSelectionProvider;
		ISelection selection= provider.getSelection();
		registerAction(fFindImplementorsAction, provider, selection, specialSelectionProvider);
		registerAction(fFindImplementorsInProjectAction, provider, selection, specialSelectionProvider);
		registerAction(fFindImplementorsInWorkingSetAction, provider, selection, specialSelectionProvider);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public ImplementorsSearchGroup(JavaEditor editor) {
		fEditor= editor;
		fSite= fEditor.getSite();
		fGroupId= ITextEditorActionConstants.GROUP_FIND;

		fFindImplementorsAction= new FindImplementorsAction(fEditor);
		fFindImplementorsAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_IMPLEMENTORS_IN_WORKSPACE);
		fEditor.setAction("SearchImplementorsInWorkspace", fFindImplementorsAction); //$NON-NLS-1$

		fFindImplementorsInProjectAction= new FindImplementorsInProjectAction(fEditor);
		fFindImplementorsInProjectAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_IMPLEMENTORS_IN_PROJECT);
		fEditor.setAction("SearchImplementorsInProject", fFindImplementorsInProjectAction); //$NON-NLS-1$

		fFindImplementorsInWorkingSetAction= new FindImplementorsInWorkingSetAction(fEditor);
		fFindImplementorsInWorkingSetAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_IMPLEMENTORS_IN_WORKING_SET);
		fEditor.setAction("SearchImplementorsInWorkingSet", fFindImplementorsInWorkingSetAction); //$NON-NLS-1$
	}

	private void registerAction(SelectionDispatchAction action, ISelectionProvider provider, ISelection selection, ISelectionProvider specialSelectionProvider) {
		action.update(selection);
		provider.addSelectionChangedListener(action);
		if (specialSelectionProvider != null)
			action.setSpecialSelectionProvider(specialSelectionProvider);
	}

	private void addAction(IAction action, IMenuManager manager) {
		if (action.isEnabled()) {
			manager.add(action);
		}
	}

	private void addWorkingSetAction(IWorkingSet[] workingSets, IMenuManager manager) {
		FindAction action;
		if (fEditor != null)
			action= new WorkingSetFindAction(fEditor, new FindImplementorsInWorkingSetAction(fEditor, workingSets), SearchUtil.toString(workingSets));
		else
			action= new WorkingSetFindAction(fSite, new FindImplementorsInWorkingSetAction(fSite, workingSets), SearchUtil.toString(workingSets));
		action.update(getContext().getSelection());
		addAction(action, manager);
	}


	/* (non-Javadoc)
	 * Method declared on ActionGroup.
	 */
	@Override
	public void fillContextMenu(IMenuManager manager) {
		MenuManager javaSearchMM= new MenuManager(MENU_TEXT, IContextMenuConstants.GROUP_SEARCH);
		addAction(fFindImplementorsAction, javaSearchMM);
		addAction(fFindImplementorsInProjectAction, javaSearchMM);

		javaSearchMM.add(new Separator());

		Iterator<IWorkingSet[]> iter= SearchUtil.getLRUWorkingSets().sortedIterator();
		while (iter.hasNext()) {
			addWorkingSetAction(iter.next(), javaSearchMM);
		}
		addAction(fFindImplementorsInWorkingSetAction, javaSearchMM);

		if (!javaSearchMM.isEmpty())
			manager.appendToGroup(fGroupId, javaSearchMM);
	}


	/*
	 * Method declared on ActionGroup.
	 */
	@Override
	public void fillActionBars(IActionBars actionBars) {
		Assert.isNotNull(actionBars);
		super.fillActionBars(actionBars);
		fActionBars= actionBars;
		updateGlobalActionHandlers();
	}

	/*
	 * Method declared on ActionGroup.
	 */
	@Override
	public void dispose() {
		ISelectionProvider provider= fSite.getSelectionProvider();
		if (provider != null) {
			disposeAction(fFindImplementorsAction, provider);
			disposeAction(fFindImplementorsInProjectAction, provider);
			disposeAction(fFindImplementorsInWorkingSetAction, provider);
		}
		super.dispose();
		fFindImplementorsAction= null;
		fFindImplementorsInProjectAction= null;
		fFindImplementorsInWorkingSetAction= null;
		updateGlobalActionHandlers();
	}

	private void updateGlobalActionHandlers() {
		if (fActionBars != null) {
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_IMPLEMENTORS_IN_WORKSPACE, fFindImplementorsAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_IMPLEMENTORS_IN_PROJECT, fFindImplementorsInProjectAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_IMPLEMENTORS_IN_WORKING_SET, fFindImplementorsInWorkingSetAction);
		}
	}

	private void disposeAction(ISelectionChangedListener action, ISelectionProvider provider) {
		if (action != null)
			provider.removeSelectionChangedListener(action);
	}
}



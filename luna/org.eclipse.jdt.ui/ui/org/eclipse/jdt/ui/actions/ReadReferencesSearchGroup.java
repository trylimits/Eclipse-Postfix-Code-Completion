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
 * Action group that adds the search for read references actions to a
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
public class ReadReferencesSearchGroup extends ActionGroup  {

	private static final String MENU_TEXT= SearchMessages.group_readReferences;

	private IWorkbenchSite fSite;
	private JavaEditor fEditor;
	private IActionBars fActionBars;

	private String fGroupId;

	private FindReadReferencesAction fFindReadReferencesAction;
	private FindReadReferencesInProjectAction fFindReadReferencesInProjectAction;
	private FindReadReferencesInHierarchyAction fFindReadReferencesInHierarchyAction;
	private FindReadReferencesInWorkingSetAction fFindReadReferencesInWorkingSetAction;

	/**
	 * Creates a new <code>ReadReferencesSearchGroup</code>. The group requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the view part that owns this action group
	 */
	public ReadReferencesSearchGroup(IWorkbenchSite site) {
		this(site, null);
	}

	/**
	 * Creates a new <code>ReadReferencesSearchGroup</code>. The group requires
	 * that the selection provided by the given selection provider is of type
	 * {@link IStructuredSelection}.
	 *
	 * @param site the site that will own the action group.
	 * @param specialSelectionProvider the selection provider used instead of the
	 *  sites selection provider.
	 *
	 * @since 3.4
	 */
	public ReadReferencesSearchGroup(IWorkbenchSite site, ISelectionProvider specialSelectionProvider) {
		fSite= site;
		fGroupId= IContextMenuConstants.GROUP_SEARCH;

		fFindReadReferencesAction= new FindReadReferencesAction(site);
		fFindReadReferencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_WORKSPACE);

		fFindReadReferencesInProjectAction= new FindReadReferencesInProjectAction(site);
		fFindReadReferencesInProjectAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_READ_ACCESS_IN_PROJECT);

		fFindReadReferencesInHierarchyAction= new FindReadReferencesInHierarchyAction(site);
		fFindReadReferencesInHierarchyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_HIERARCHY);

		fFindReadReferencesInWorkingSetAction= new FindReadReferencesInWorkingSetAction(site);
		fFindReadReferencesInWorkingSetAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_WORKING_SET);

		// register the actions as selection listeners
		ISelectionProvider provider= specialSelectionProvider == null ? fSite.getSelectionProvider() : specialSelectionProvider;
		ISelection selection= provider.getSelection();
		registerAction(fFindReadReferencesAction, provider, selection, specialSelectionProvider);
		registerAction(fFindReadReferencesInProjectAction, provider, selection, specialSelectionProvider);
		registerAction(fFindReadReferencesInHierarchyAction, provider, selection, specialSelectionProvider);
		registerAction(fFindReadReferencesInWorkingSetAction, provider, selection, specialSelectionProvider);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public ReadReferencesSearchGroup(JavaEditor editor) {
		fEditor= editor;
		fSite= fEditor.getSite();
		fGroupId= ITextEditorActionConstants.GROUP_FIND;

		fFindReadReferencesAction= new FindReadReferencesAction(fEditor);
		fFindReadReferencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_WORKSPACE);
		fEditor.setAction("SearchReadAccessInWorkspace", fFindReadReferencesAction); //$NON-NLS-1$

		fFindReadReferencesInProjectAction= new FindReadReferencesInProjectAction(fEditor);
		fFindReadReferencesInProjectAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_READ_ACCESS_IN_PROJECT);
		fEditor.setAction("SearchReadAccessInProject", fFindReadReferencesInProjectAction); //$NON-NLS-1$

		fFindReadReferencesInHierarchyAction= new FindReadReferencesInHierarchyAction(fEditor);
		fFindReadReferencesInHierarchyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_HIERARCHY);
		fEditor.setAction("SearchReadAccessInHierarchy", fFindReadReferencesInHierarchyAction); //$NON-NLS-1$

		fFindReadReferencesInWorkingSetAction= new FindReadReferencesInWorkingSetAction(fEditor);
		fFindReadReferencesInWorkingSetAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_WORKING_SET);
		fEditor.setAction("SearchReadAccessInWorkingSet", fFindReadReferencesInWorkingSetAction); //$NON-NLS-1$
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
			action= new WorkingSetFindAction(fEditor, new FindReadReferencesInWorkingSetAction(fEditor, workingSets), SearchUtil.toString(workingSets));
		else
			action= new WorkingSetFindAction(fSite, new FindReadReferencesInWorkingSetAction(fSite, workingSets), SearchUtil.toString(workingSets));
		action.update(getContext().getSelection());
		addAction(action, manager);
	}


	/* (non-Javadoc)
	 * Method declared on ActionGroup.
	 */
	@Override
	public void fillContextMenu(IMenuManager manager) {
		MenuManager javaSearchMM= new MenuManager(MENU_TEXT, IContextMenuConstants.GROUP_SEARCH);
		addAction(fFindReadReferencesAction, javaSearchMM);
		addAction(fFindReadReferencesInProjectAction, javaSearchMM);
		addAction(fFindReadReferencesInHierarchyAction, javaSearchMM);

		javaSearchMM.add(new Separator());

		Iterator<IWorkingSet[]> iter= SearchUtil.getLRUWorkingSets().sortedIterator();
		while (iter.hasNext()) {
			addWorkingSetAction(iter.next(), javaSearchMM);
		}
		addAction(fFindReadReferencesInWorkingSetAction, javaSearchMM);

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
			disposeAction(fFindReadReferencesAction, provider);
			disposeAction(fFindReadReferencesInProjectAction, provider);
			disposeAction(fFindReadReferencesInHierarchyAction, provider);
			disposeAction(fFindReadReferencesInWorkingSetAction, provider);
		}
		fFindReadReferencesAction= null;
		fFindReadReferencesInProjectAction= null;
		fFindReadReferencesInHierarchyAction= null;
		fFindReadReferencesInWorkingSetAction= null;
		updateGlobalActionHandlers();
		super.dispose();
	}

	private void updateGlobalActionHandlers() {
		if (fActionBars != null) {
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_READ_ACCESS_IN_WORKSPACE, fFindReadReferencesAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_READ_ACCESS_IN_PROJECT, fFindReadReferencesInProjectAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_READ_ACCESS_IN_HIERARCHY, fFindReadReferencesInHierarchyAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_READ_ACCESS_IN_WORKING_SET, fFindReadReferencesInWorkingSetAction);
		}
	}

	private void disposeAction(ISelectionChangedListener action, ISelectionProvider provider) {
		if (action != null)
			provider.removeSelectionChangedListener(action);
	}
}

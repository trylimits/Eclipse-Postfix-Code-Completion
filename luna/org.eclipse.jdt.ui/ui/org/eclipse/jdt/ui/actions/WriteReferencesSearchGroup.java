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
 * Action group that adds the search for write references actions to a
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
public class WriteReferencesSearchGroup extends ActionGroup  {

	private static final String MENU_TEXT= SearchMessages.group_writeReferences;

	private IWorkbenchSite fSite;
	private JavaEditor fEditor;
	private IActionBars fActionBars;

	private String fGroupId;

	private FindWriteReferencesAction fFindWriteReferencesAction;
	private FindWriteReferencesInProjectAction fFindWriteReferencesInProjectAction;
	private FindWriteReferencesInHierarchyAction fFindWriteReferencesInHierarchyAction;
	private FindWriteReferencesInWorkingSetAction fFindWriteReferencesInWorkingSetAction;

	/**
	 * Creates a new <code>WriteReferencesSearchGroup</code>. The action
	 * requires that the selection provided by the site's selection provider is of
	 * type <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the view part that owns this action group
	 */
	public WriteReferencesSearchGroup(IWorkbenchSite site) {
		this(site, null);
	}

	/**
	 * Creates a new <code>WriteReferencesSearchGroup</code>. The group requires
	 * that the selection provided by the given selection provider is of type
	 * {@link IStructuredSelection}.
	 *
	 * @param site the site that will own the action group.
	 * @param specialSelectionProvider the selection provider used instead of the
	 *  sites selection provider.
	 *
	 * @since 3.4
	 */
	public WriteReferencesSearchGroup(IWorkbenchSite site, ISelectionProvider specialSelectionProvider) {
		fSite= site;
		fGroupId= IContextMenuConstants.GROUP_SEARCH;

		fFindWriteReferencesAction= new FindWriteReferencesAction(site);
		fFindWriteReferencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_WORKSPACE);

		fFindWriteReferencesInProjectAction= new FindWriteReferencesInProjectAction(site);
		fFindWriteReferencesInProjectAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_PROJECT);

		fFindWriteReferencesInHierarchyAction= new FindWriteReferencesInHierarchyAction(site);
		fFindWriteReferencesInHierarchyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_HIERARCHY);

		fFindWriteReferencesInWorkingSetAction= new FindWriteReferencesInWorkingSetAction(site);
		fFindWriteReferencesInWorkingSetAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_WORKING_SET);

		// register the actions as selection listeners
		ISelectionProvider provider= specialSelectionProvider == null ? fSite.getSelectionProvider() : specialSelectionProvider;
		ISelection selection= provider.getSelection();
		registerAction(fFindWriteReferencesAction, provider, selection, specialSelectionProvider);
		registerAction(fFindWriteReferencesInProjectAction, provider, selection, specialSelectionProvider);
		registerAction(fFindWriteReferencesInHierarchyAction, provider, selection, specialSelectionProvider);
		registerAction(fFindWriteReferencesInWorkingSetAction, provider, selection, specialSelectionProvider);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public WriteReferencesSearchGroup(JavaEditor editor) {
		fEditor= editor;
		fSite= fEditor.getSite();
		fGroupId= ITextEditorActionConstants.GROUP_FIND;

		fFindWriteReferencesAction= new FindWriteReferencesAction(fEditor);
		fFindWriteReferencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_WORKSPACE);
		fEditor.setAction("SearchWriteAccessInWorkspace", fFindWriteReferencesAction); //$NON-NLS-1$

		fFindWriteReferencesInProjectAction= new FindWriteReferencesInProjectAction(fEditor);
		fFindWriteReferencesInProjectAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_PROJECT);
		fEditor.setAction("SearchWriteAccessInProject", fFindWriteReferencesInProjectAction); //$NON-NLS-1$

		fFindWriteReferencesInHierarchyAction= new FindWriteReferencesInHierarchyAction(fEditor);
		fFindWriteReferencesInHierarchyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_HIERARCHY);
		fEditor.setAction("SearchWriteAccessInHierarchy", fFindWriteReferencesInHierarchyAction); //$NON-NLS-1$

		fFindWriteReferencesInWorkingSetAction= new FindWriteReferencesInWorkingSetAction(fEditor);
		fFindWriteReferencesInWorkingSetAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_WRITE_ACCESS_IN_WORKING_SET);
		fEditor.setAction("SearchWriteAccessInWorkingSet", fFindWriteReferencesInWorkingSetAction); //$NON-NLS-1$
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
			action= new WorkingSetFindAction(fEditor, new FindWriteReferencesInWorkingSetAction(fEditor, workingSets), SearchUtil.toString(workingSets));
		else
			action= new WorkingSetFindAction(fSite, new FindWriteReferencesInWorkingSetAction(fSite, workingSets), SearchUtil.toString(workingSets));
		action.update(getContext().getSelection());
		addAction(action, manager);
	}


	/* (non-Javadoc)
	 * Method declared on ActionGroup.
	 */
	@Override
	public void fillContextMenu(IMenuManager manager) {
		MenuManager javaSearchMM= new MenuManager(MENU_TEXT, IContextMenuConstants.GROUP_SEARCH);
		addAction(fFindWriteReferencesAction, javaSearchMM);
		addAction(fFindWriteReferencesInProjectAction, javaSearchMM);
		addAction(fFindWriteReferencesInHierarchyAction, javaSearchMM);

		javaSearchMM.add(new Separator());

		Iterator<IWorkingSet[]> iter= SearchUtil.getLRUWorkingSets().sortedIterator();
		while (iter.hasNext()) {
			addWorkingSetAction(iter.next(), javaSearchMM);
		}
		addAction(fFindWriteReferencesInWorkingSetAction, javaSearchMM);

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
			disposeAction(fFindWriteReferencesAction, provider);
			disposeAction(fFindWriteReferencesInProjectAction, provider);
			disposeAction(fFindWriteReferencesInHierarchyAction, provider);
			disposeAction(fFindWriteReferencesInWorkingSetAction, provider);
		}
		fFindWriteReferencesAction= null;
		fFindWriteReferencesInProjectAction= null;
		fFindWriteReferencesInHierarchyAction= null;
		fFindWriteReferencesInWorkingSetAction= null;
		updateGlobalActionHandlers();
		super.dispose();
	}

	private void updateGlobalActionHandlers() {
		if (fActionBars != null) {
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_WRITE_ACCESS_IN_WORKSPACE, fFindWriteReferencesAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_WRITE_ACCESS_IN_PROJECT, fFindWriteReferencesInProjectAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_WRITE_ACCESS_IN_HIERARCHY, fFindWriteReferencesInHierarchyAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_WRITE_ACCESS_IN_WORKING_SET, fFindWriteReferencesInWorkingSetAction);
		}
	}

	private void disposeAction(ISelectionChangedListener action, ISelectionProvider provider) {
		if (action != null)
			provider.removeSelectionChangedListener(action);
	}
}

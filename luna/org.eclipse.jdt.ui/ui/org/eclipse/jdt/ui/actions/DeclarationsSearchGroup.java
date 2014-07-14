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
 * Action group that adds the search for declarations actions to a
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
public class DeclarationsSearchGroup extends ActionGroup  {

	private static final String MENU_TEXT= SearchMessages.group_declarations;

	private IWorkbenchSite fSite;
	private JavaEditor fEditor;
	private IActionBars fActionBars;

	private String fGroupId;

	private FindDeclarationsAction fFindDeclarationsAction;
	private FindDeclarationsInProjectAction fFindDeclarationsInProjectAction;
	private FindDeclarationsInWorkingSetAction fFindDeclarationsInWorkingSetAction;
	private FindDeclarationsInHierarchyAction fFindDeclarationsInHierarchyAction;

	/**
	 * Creates a new <code>DeclarationsSearchGroup</code>. The group requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * IStructuredSelection</code>.
	 *
	 * @param site the workbench site that owns this action group
	 */
	public DeclarationsSearchGroup(IWorkbenchSite site) {
		this(site, null);
	}

	/**
	 * Creates a new <code>DeclarationsSearchGroup</code>. The group requires
	 * that the selection provided by the given selection provider is of type
	 * {@link IStructuredSelection}.
	 *
	 * @param site the site that will own the action group.
	 * @param specialSelectionProvider the selection provider used instead of the
	 *  sites selection provider.
	 *
	 * @since 3.4
	 */
	public DeclarationsSearchGroup(IWorkbenchSite site, ISelectionProvider specialSelectionProvider) {
		fSite= site;
		fGroupId= IContextMenuConstants.GROUP_SEARCH;

		fFindDeclarationsAction= new FindDeclarationsAction(site);
		fFindDeclarationsAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_DECLARATIONS_IN_WORKSPACE);

		fFindDeclarationsInProjectAction= new FindDeclarationsInProjectAction(site);
		fFindDeclarationsInProjectAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_DECLARATIONS_IN_PROJECTS);

		fFindDeclarationsInHierarchyAction= new FindDeclarationsInHierarchyAction(site);
		fFindDeclarationsInHierarchyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_DECLARATIONS_IN_HIERARCHY);

		fFindDeclarationsInWorkingSetAction= new FindDeclarationsInWorkingSetAction(site);
		fFindDeclarationsInWorkingSetAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_DECLARATIONS_IN_WORKING_SET);

		// register the actions as selection listeners
		ISelectionProvider provider= specialSelectionProvider == null ? fSite.getSelectionProvider() : specialSelectionProvider;
		ISelection selection= provider.getSelection();
		registerAction(fFindDeclarationsAction, provider, selection, specialSelectionProvider);
		registerAction(fFindDeclarationsInProjectAction, provider, selection, specialSelectionProvider);
		registerAction(fFindDeclarationsInHierarchyAction, provider, selection, specialSelectionProvider);
		registerAction(fFindDeclarationsInWorkingSetAction, provider, selection, specialSelectionProvider);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 *
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public DeclarationsSearchGroup(JavaEditor editor) {
		Assert.isNotNull(editor);
		fEditor= editor;
		fSite= fEditor.getSite();
		fGroupId= ITextEditorActionConstants.GROUP_FIND;

		fFindDeclarationsAction= new FindDeclarationsAction(fEditor);
		fFindDeclarationsAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_DECLARATIONS_IN_WORKSPACE);
		fEditor.setAction("SearchDeclarationsInWorkspace", fFindDeclarationsAction); //$NON-NLS-1$

		fFindDeclarationsInProjectAction= new FindDeclarationsInProjectAction(fEditor);
		fFindDeclarationsInProjectAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_DECLARATIONS_IN_PROJECTS);
		fEditor.setAction("SearchDeclarationsInProjects", fFindDeclarationsInProjectAction); //$NON-NLS-1$

		fFindDeclarationsInHierarchyAction= new FindDeclarationsInHierarchyAction(fEditor);
		fFindDeclarationsInHierarchyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_DECLARATIONS_IN_HIERARCHY);
		fEditor.setAction("SearchDeclarationsInHierarchy", fFindDeclarationsInHierarchyAction); //$NON-NLS-1$

		fFindDeclarationsInWorkingSetAction= new FindDeclarationsInWorkingSetAction(fEditor);
		fFindDeclarationsInWorkingSetAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_DECLARATIONS_IN_WORKING_SET);
		fEditor.setAction("SearchDeclarationsInWorkingSet", fFindDeclarationsInWorkingSetAction); //$NON-NLS-1$
	}

	private void registerAction(SelectionDispatchAction action, ISelectionProvider provider, ISelection selection, ISelectionProvider specialSelectionProvider) {
		action.update(selection);
		provider.addSelectionChangedListener(action);
		if (specialSelectionProvider != null)
			action.setSpecialSelectionProvider(specialSelectionProvider);
	}

	/* (non-Javadoc)
	 * Method declared on ActionGroup.
	 */
	@Override
	public void fillActionBars(IActionBars actionBars) {
		Assert.isNotNull(actionBars);
		super.fillActionBars(actionBars);
		fActionBars= actionBars;
		updateGlobalActionHandlers();
	}

	private void addAction(IAction action, IMenuManager manager) {
		if (action.isEnabled()) {
			manager.add(action);
		}
	}

	private void addWorkingSetAction(IWorkingSet[] workingSets, IMenuManager manager) {
		FindAction action;
		if (fEditor != null)
			action= new WorkingSetFindAction(fEditor, new FindDeclarationsInWorkingSetAction(fEditor, workingSets), SearchUtil.toString(workingSets));
		else
			action= new WorkingSetFindAction(fSite, new FindDeclarationsInWorkingSetAction(fSite, workingSets), SearchUtil.toString(workingSets));
		action.update(getContext().getSelection());
		addAction(action, manager);
	}


	/* (non-Javadoc)
	 * Method declared on ActionGroup.
	 */
	@Override
	public void fillContextMenu(IMenuManager manager) {
		IMenuManager javaSearchMM= new MenuManager(MENU_TEXT, IContextMenuConstants.GROUP_SEARCH);
		addAction(fFindDeclarationsAction, javaSearchMM);
		addAction(fFindDeclarationsInProjectAction, javaSearchMM);
		addAction(fFindDeclarationsInHierarchyAction, javaSearchMM);

		javaSearchMM.add(new Separator());

		Iterator<IWorkingSet[]> iter= SearchUtil.getLRUWorkingSets().sortedIterator();
		while (iter.hasNext()) {
			addWorkingSetAction(iter.next(), javaSearchMM);
		}
		addAction(fFindDeclarationsInWorkingSetAction, javaSearchMM);

		if (!javaSearchMM.isEmpty())
			manager.appendToGroup(fGroupId, javaSearchMM);
	}

	/*
	 * Method declared on ActionGroup.
	 */
	@Override
	public void dispose() {
		ISelectionProvider provider= fSite.getSelectionProvider();
		if (provider != null) {
			disposeAction(fFindDeclarationsAction, provider);
			disposeAction(fFindDeclarationsInProjectAction, provider);
			disposeAction(fFindDeclarationsInHierarchyAction, provider);
			disposeAction(fFindDeclarationsInWorkingSetAction, provider);
		}
		fFindDeclarationsAction= null;
		fFindDeclarationsInProjectAction= null;
		fFindDeclarationsInHierarchyAction= null;
		fFindDeclarationsInWorkingSetAction= null;
		updateGlobalActionHandlers();
		super.dispose();
	}

	private void updateGlobalActionHandlers() {
		if (fActionBars != null) {
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_DECLARATIONS_IN_WORKSPACE, fFindDeclarationsAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_DECLARATIONS_IN_PROJECT, fFindDeclarationsInProjectAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_DECLARATIONS_IN_HIERARCHY, fFindDeclarationsInHierarchyAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_DECLARATIONS_IN_WORKING_SET, fFindDeclarationsInWorkingSetAction);
		}
	}

	private void disposeAction(ISelectionChangedListener action, ISelectionProvider provider) {
		if (action != null)
			provider.removeSelectionChangedListener(action);
	}
}

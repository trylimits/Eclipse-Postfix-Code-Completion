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
 * Action group that adds the search for references actions to a
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
public class ReferencesSearchGroup extends ActionGroup  {

	private static final String MENU_TEXT= SearchMessages.group_references;

	private IWorkbenchSite fSite;
	private JavaEditor fEditor;
	private IActionBars fActionBars;

	private String fGroupId;

	private FindReferencesAction fFindReferencesAction;
	private FindReferencesInProjectAction fFindReferencesInProjectAction;
	private FindReferencesInHierarchyAction fFindReferencesInHierarchyAction;
	private FindReferencesInWorkingSetAction fFindReferencesInWorkingSetAction;

	/**
	 * Creates a new <code>ReferencesSearchGroup</code>. The group requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the view part that owns this action group
	 */
	public ReferencesSearchGroup(IWorkbenchSite site) {
		this(site, null);
	}

	/**
	 * Creates a new <code>ReferencesSearchGroup</code>. The group requires
	 * that the selection provided by the given selection provider is of type
	 * {@link IStructuredSelection}.
	 *
	 * @param site the site that will own the action group.
	 * @param specialSelectionProvider the selection provider used instead of the
	 *  sites selection provider.
	 *
	 * @since 3.4
	 */
	public ReferencesSearchGroup(IWorkbenchSite site, ISelectionProvider specialSelectionProvider) {
		fSite= site;
		fGroupId= IContextMenuConstants.GROUP_SEARCH;

		fFindReferencesAction= new FindReferencesAction(site);
		fFindReferencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_REFERENCES_IN_WORKSPACE);

		fFindReferencesInProjectAction= new FindReferencesInProjectAction(site);
		fFindReferencesInProjectAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_REFERENCES_IN_PROJECT);

		fFindReferencesInHierarchyAction= new FindReferencesInHierarchyAction(site);
		fFindReferencesInHierarchyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_REFERENCES_IN_HIERARCHY);

		fFindReferencesInWorkingSetAction= new FindReferencesInWorkingSetAction(site);
		fFindReferencesInWorkingSetAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_REFERENCES_IN_WORKING_SET);

		// register the actions as selection listeners
		ISelectionProvider provider= specialSelectionProvider == null ? fSite.getSelectionProvider() : specialSelectionProvider;
		ISelection selection= provider.getSelection();
		registerAction(fFindReferencesAction, provider, selection, specialSelectionProvider);
		registerAction(fFindReferencesInProjectAction, provider, selection, specialSelectionProvider);
		registerAction(fFindReferencesInHierarchyAction, provider, selection, specialSelectionProvider);
		registerAction(fFindReferencesInWorkingSetAction, provider, selection, specialSelectionProvider);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public ReferencesSearchGroup(JavaEditor editor) {
		Assert.isNotNull(editor);
		fEditor= editor;
		fSite= fEditor.getSite();
		fGroupId= ITextEditorActionConstants.GROUP_FIND;

		fFindReferencesAction= new FindReferencesAction(editor);
		fFindReferencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_REFERENCES_IN_WORKSPACE);
		fEditor.setAction("SearchReferencesInWorkspace", fFindReferencesAction); //$NON-NLS-1$

		fFindReferencesInProjectAction= new FindReferencesInProjectAction(fEditor);
		fFindReferencesInProjectAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_REFERENCES_IN_PROJECT);
		fEditor.setAction("SearchReferencesInProject", fFindReferencesInProjectAction); //$NON-NLS-1$

		fFindReferencesInHierarchyAction= new FindReferencesInHierarchyAction(fEditor);
		fFindReferencesInHierarchyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_REFERENCES_IN_HIERARCHY);
		fEditor.setAction("SearchReferencesInHierarchy", fFindReferencesInHierarchyAction); //$NON-NLS-1$

		fFindReferencesInWorkingSetAction= new FindReferencesInWorkingSetAction(fEditor);
		fFindReferencesInWorkingSetAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_REFERENCES_IN_WORKING_SET);
		fEditor.setAction("SearchReferencesInWorkingSet", fFindReferencesInWorkingSetAction); //$NON-NLS-1$
	}

	private void registerAction(SelectionDispatchAction action, ISelectionProvider provider, ISelection selection, ISelectionProvider specialSelectionProvider) {
		action.update(selection);
		provider.addSelectionChangedListener(action);
		if (specialSelectionProvider != null)
			action.setSpecialSelectionProvider(specialSelectionProvider);
	}

	/**
	 * Note: this method is for internal use only. Clients should not call this method.
	 *
	 * @return the menu label
	 */
	protected String getName() {
		return MENU_TEXT;
	}

	/* (non-Javadoc)
	 * Method declared in ActionGroup
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
			action= new WorkingSetFindAction(fEditor, new FindReferencesInWorkingSetAction(fEditor, workingSets), SearchUtil.toString(workingSets));
		else
			action= new WorkingSetFindAction(fSite, new FindReferencesInWorkingSetAction(fSite, workingSets), SearchUtil.toString(workingSets));
		action.update(getContext().getSelection());
		addAction(action, manager);
	}


	/* (non-Javadoc)
	 * Method declared on ActionGroup.
	 */
	@Override
	public void fillContextMenu(IMenuManager manager) {
		MenuManager javaSearchMM= new MenuManager(getName(), IContextMenuConstants.GROUP_SEARCH);
		addAction(fFindReferencesAction, javaSearchMM);
		addAction(fFindReferencesInProjectAction, javaSearchMM);
		addAction(fFindReferencesInHierarchyAction, javaSearchMM);

		javaSearchMM.add(new Separator());

		Iterator<IWorkingSet[]> iter= SearchUtil.getLRUWorkingSets().sortedIterator();
		while (iter.hasNext()) {
			addWorkingSetAction(iter.next(), javaSearchMM);
		}
		addAction(fFindReferencesInWorkingSetAction, javaSearchMM);

		if (!javaSearchMM.isEmpty())
			manager.appendToGroup(fGroupId, javaSearchMM);
	}

	/*
	 * Overrides method declared in ActionGroup
	 */
	@Override
	public void dispose() {
		ISelectionProvider provider= fSite.getSelectionProvider();
		if (provider != null) {
			disposeAction(fFindReferencesAction, provider);
			disposeAction(fFindReferencesInProjectAction, provider);
			disposeAction(fFindReferencesInHierarchyAction, provider);
			disposeAction(fFindReferencesInWorkingSetAction, provider);
		}
		fFindReferencesAction= null;
		fFindReferencesInProjectAction= null;
		fFindReferencesInHierarchyAction= null;
		fFindReferencesInWorkingSetAction= null;
		updateGlobalActionHandlers();
		super.dispose();
	}

	private void updateGlobalActionHandlers() {
		if (fActionBars != null) {
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_REFERENCES_IN_WORKSPACE, fFindReferencesAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_REFERENCES_IN_PROJECT, fFindReferencesInProjectAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_REFERENCES_IN_HIERARCHY, fFindReferencesInHierarchyAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_REFERENCES_IN_WORKING_SET, fFindReferencesInWorkingSetAction);
		}
	}

	private void disposeAction(ISelectionChangedListener action, ISelectionProvider provider) {
		if (action != null)
			provider.removeSelectionChangedListener(action);
	}
}

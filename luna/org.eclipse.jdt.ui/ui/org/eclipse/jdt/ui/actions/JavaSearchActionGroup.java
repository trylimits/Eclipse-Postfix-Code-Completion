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

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.part.Page;

import org.eclipse.ui.texteditor.ITextEditorActionConstants;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.search.SearchMessages;

/**
 * Action group that adds the Java search actions to a context menu and
 * the global menu bar.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class JavaSearchActionGroup extends ActionGroup {

	private JavaEditor fEditor;

	private ReferencesSearchGroup fReferencesGroup;
	private ReadReferencesSearchGroup fReadAccessGroup;
	private WriteReferencesSearchGroup fWriteAccessGroup;
	private DeclarationsSearchGroup fDeclarationsGroup;
	private ImplementorsSearchGroup fImplementorsGroup;
	private OccurrencesSearchGroup fOccurrencesGroup;


	/**
	 * Creates a new <code>JavaSearchActionGroup</code>. The group
	 * requires that the selection provided by the part's selection provider
	 * is of type <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param part the view part that owns this action group
	 */
	public JavaSearchActionGroup(IViewPart part) {
		this(part.getViewSite());
	}

	/**
	 * Creates a new <code>JavaSearchActionGroup</code>. The group
	 * requires that the selection provided by the page's selection provider
	 * is of type <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param page the page that owns this action group
	 */
	public JavaSearchActionGroup(Page page) {
		this(page.getSite());
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public JavaSearchActionGroup(JavaEditor editor) {
		Assert.isNotNull(editor);
		fEditor= editor;

		fReferencesGroup= new ReferencesSearchGroup(fEditor);
		fReadAccessGroup= new ReadReferencesSearchGroup(fEditor);
		fWriteAccessGroup= new WriteReferencesSearchGroup(fEditor);
		fDeclarationsGroup= new DeclarationsSearchGroup(fEditor);
		fImplementorsGroup= new ImplementorsSearchGroup(fEditor);
		fOccurrencesGroup= new OccurrencesSearchGroup(fEditor);
	}

	private JavaSearchActionGroup(IWorkbenchSite site) {
		fReferencesGroup= new ReferencesSearchGroup(site);
		fReadAccessGroup= new ReadReferencesSearchGroup(site);
		fWriteAccessGroup= new WriteReferencesSearchGroup(site);
		fDeclarationsGroup= new DeclarationsSearchGroup(site);
		fImplementorsGroup= new ImplementorsSearchGroup(site);
		fOccurrencesGroup= new OccurrencesSearchGroup(site);
	}

	/**
	 * Creates a new <code>JavaSearchActionGroup</code>. The group requires
	 * that the selection provided by the given selection provider is of type
	 * {@link IStructuredSelection}.
	 *
	 * @param site the site that will own the action group.
	 * @param specialSelectionProvider the selection provider used instead of the
	 *  sites selection provider.
	 *
	 * @since 3.4
	 */
	public JavaSearchActionGroup(IWorkbenchSite site, ISelectionProvider specialSelectionProvider) {
		fReferencesGroup= new ReferencesSearchGroup(site, specialSelectionProvider);
		fReadAccessGroup= new ReadReferencesSearchGroup(site, specialSelectionProvider);
		fWriteAccessGroup= new WriteReferencesSearchGroup(site, specialSelectionProvider);
		fDeclarationsGroup= new DeclarationsSearchGroup(site, specialSelectionProvider);
		fImplementorsGroup= new ImplementorsSearchGroup(site, specialSelectionProvider);
		fOccurrencesGroup= new OccurrencesSearchGroup(site, specialSelectionProvider);
	}

	/*
	 * Method declared on ActionGroup.
	 */
	@Override
	public void setContext(ActionContext context) {
		fReferencesGroup.setContext(context);
		fDeclarationsGroup.setContext(context);
		fImplementorsGroup.setContext(context);
		fReadAccessGroup.setContext(context);
		fWriteAccessGroup.setContext(context);
		fOccurrencesGroup.setContext(context);
	}

	/*
	 * Method declared on ActionGroup.
	 */
	@Override
	public void fillActionBars(IActionBars actionBar) {
		super.fillActionBars(actionBar);
		fReferencesGroup.fillActionBars(actionBar);
		fDeclarationsGroup.fillActionBars(actionBar);
		fImplementorsGroup.fillActionBars(actionBar);
		fReadAccessGroup.fillActionBars(actionBar);
		fWriteAccessGroup.fillActionBars(actionBar);
		fOccurrencesGroup.fillActionBars(actionBar);
	}

	/*
	 * Method declared on ActionGroup.
	 */
	@Override
	public void fillContextMenu(IMenuManager menu) {
		super.fillContextMenu(menu);

		if(PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.SEARCH_USE_REDUCED_MENU)) {
			fReferencesGroup.fillContextMenu(menu);
			fDeclarationsGroup.fillContextMenu(menu);

			if (fEditor == null) {
				fImplementorsGroup.fillContextMenu(menu);
				fReadAccessGroup.fillContextMenu(menu);
				fWriteAccessGroup.fillContextMenu(menu);
			}
		} else {
			IMenuManager target= menu;
			IMenuManager searchSubMenu= null;
			if (fEditor != null) {
				String groupName= SearchMessages.group_search;
				searchSubMenu= new MenuManager(groupName, ITextEditorActionConstants.GROUP_FIND);
				searchSubMenu.add(new GroupMarker(ITextEditorActionConstants.GROUP_FIND));
				target= searchSubMenu;
			}

			fReferencesGroup.fillContextMenu(target);
			fDeclarationsGroup.fillContextMenu(target);
			fImplementorsGroup.fillContextMenu(target);
			fReadAccessGroup.fillContextMenu(target);
			fWriteAccessGroup.fillContextMenu(target);

			if (searchSubMenu != null) {
				fOccurrencesGroup.fillContextMenu(target);
				searchSubMenu.add(new Separator());
			}

			// no other way to find out if we have added items.
			if (searchSubMenu != null && searchSubMenu.getItems().length > 2) {
				menu.appendToGroup(ITextEditorActionConstants.GROUP_FIND, searchSubMenu);
			}
		}
	}

	/*
	 * Method declared on ActionGroup.
	 */
	@Override
	public void dispose() {
		fReferencesGroup.dispose();
		fDeclarationsGroup.dispose();
		fImplementorsGroup.dispose();
		fReadAccessGroup.dispose();
		fWriteAccessGroup.dispose();
		fOccurrencesGroup.dispose();

		super.dispose();
	}
}

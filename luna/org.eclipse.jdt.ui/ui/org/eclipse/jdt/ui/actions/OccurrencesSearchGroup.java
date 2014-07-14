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

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.actions.ActionGroup;

import org.eclipse.ui.texteditor.ITextEditorActionConstants;

import org.eclipse.jdt.core.ITypeRoot;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;
import org.eclipse.jdt.internal.ui.search.SearchMessages;

/**
 * Action group that adds the occurrences in file actions
 * to a context menu and the global menu bar.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 3.1
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class OccurrencesSearchGroup extends ActionGroup  {

	private IWorkbenchSite fSite;
	private JavaEditor fEditor;
	private IActionBars fActionBars;

	private String fGroupId;

	private FindOccurrencesInFileAction fOccurrencesInFileAction;
	private FindExceptionOccurrencesAction fExceptionOccurrencesAction;
	private FindImplementOccurrencesAction fFindImplementorOccurrencesAction;
	private FindBreakContinueTargetOccurrencesAction fBreakContinueTargetOccurrencesAction;
	private FindMethodExitOccurrencesAction fMethodExitOccurrencesAction;

	/**
	 * Creates a new <code>ImplementorsSearchGroup</code>. The group
	 * requires that the selection provided by the site's selection provider
	 * is of type <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the view part that owns this action group
	 */
	public OccurrencesSearchGroup(IWorkbenchSite site) {
		this(site, null);
	}

	/**
	 * Creates a new <code>OccurrencesSearchGroup</code>. The group requires
	 * that the selection provided by the given selection provider is of type
	 * {@link IStructuredSelection}.
	 *
	 * @param site the site that will own the action group.
	 * @param specialSelectionProvider the selection provider used instead of the
	 *  sites selection provider.
	 *
	 * @since 3.4
	 */
	public OccurrencesSearchGroup(IWorkbenchSite site, ISelectionProvider specialSelectionProvider) {
		fSite= site;
		fGroupId= IContextMenuConstants.GROUP_SEARCH;

		fOccurrencesInFileAction= new FindOccurrencesInFileAction(site);
		fOccurrencesInFileAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_OCCURRENCES_IN_FILE);
		// Need to reset the label
		fOccurrencesInFileAction.setText(SearchMessages.Search_FindOccurrencesInFile_shortLabel);

		fExceptionOccurrencesAction= new FindExceptionOccurrencesAction(site);
		fExceptionOccurrencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_EXCEPTION_OCCURRENCES_IN_FILE);

		fFindImplementorOccurrencesAction= new FindImplementOccurrencesAction(site);
		fFindImplementorOccurrencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_IMPLEMENT_OCCURRENCES_IN_FILE);

		fBreakContinueTargetOccurrencesAction= new FindBreakContinueTargetOccurrencesAction(site);
		fBreakContinueTargetOccurrencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_BREAK_CONTINUE_TARGET_OCCURRENCES);

		fMethodExitOccurrencesAction= new FindMethodExitOccurrencesAction(site);
		fMethodExitOccurrencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_METHOD_EXIT_OCCURRENCES);


		// register the actions as selection listeners
		ISelectionProvider provider= specialSelectionProvider == null ? fSite.getSelectionProvider() : specialSelectionProvider;
		ISelection selection= provider.getSelection();
		registerAction(fOccurrencesInFileAction, provider, selection, specialSelectionProvider);
		registerAction(fExceptionOccurrencesAction, provider, selection, specialSelectionProvider);
		registerAction(fFindImplementorOccurrencesAction, provider, selection, specialSelectionProvider);
		registerAction(fBreakContinueTargetOccurrencesAction, provider, selection, specialSelectionProvider);
		registerAction(fMethodExitOccurrencesAction, provider, selection, specialSelectionProvider);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 *
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public OccurrencesSearchGroup(JavaEditor editor) {
		fEditor= editor;
		fSite= fEditor.getSite();
		fGroupId= ITextEditorActionConstants.GROUP_FIND;

		fOccurrencesInFileAction= new FindOccurrencesInFileAction(fEditor);
		fOccurrencesInFileAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_OCCURRENCES_IN_FILE);
		// Need to reset the label
		fOccurrencesInFileAction.setText(SearchMessages.Search_FindOccurrencesInFile_shortLabel);
		fEditor.setAction("SearchOccurrencesInFile", fOccurrencesInFileAction); //$NON-NLS-1$

		fExceptionOccurrencesAction= new FindExceptionOccurrencesAction(fEditor);
		fExceptionOccurrencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_EXCEPTION_OCCURRENCES_IN_FILE);
		fEditor.setAction("SearchExceptionOccurrences", fExceptionOccurrencesAction); //$NON-NLS-1$

		fFindImplementorOccurrencesAction= new FindImplementOccurrencesAction(fEditor);
		fFindImplementorOccurrencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_IMPLEMENT_OCCURRENCES_IN_FILE);
		fEditor.setAction("SearchImplementOccurrences", fFindImplementorOccurrencesAction); //$NON-NLS-1$

		fBreakContinueTargetOccurrencesAction= new FindBreakContinueTargetOccurrencesAction(fEditor);
		fBreakContinueTargetOccurrencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_BREAK_CONTINUE_TARGET_OCCURRENCES);
		fEditor.setAction("BreakContinueTargetOccurrences", fBreakContinueTargetOccurrencesAction); //$NON-NLS-1$

		fMethodExitOccurrencesAction= new FindMethodExitOccurrencesAction(fEditor);
		fMethodExitOccurrencesAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_METHOD_EXIT_OCCURRENCES);
		fEditor.setAction("ExitOccurrencesAction", fMethodExitOccurrencesAction); //$NON-NLS-1$

//
//		ISelectionProvider provider= fSite.getSelectionProvider();
//		ISelection selection= provider.getSelection();
//
//		registerAction(fOccurrencesInFileAction, provider, selection);
//		registerAction(fExceptionOccurrencesAction, provider, selection);
//		registerAction(fFindImplementorOccurrencesAction, provider, selection);
	}

	private void registerAction(SelectionDispatchAction action, ISelectionProvider provider, ISelection selection, ISelectionProvider specialSelectionProvider) {
		action.update(selection);
		provider.addSelectionChangedListener(action);
		if (specialSelectionProvider != null)
			action.setSpecialSelectionProvider(specialSelectionProvider);
	}

	/*
	 * Method declared on ActionGroup.
	 */
	@Override
	public void fillContextMenu(IMenuManager manager) {
		String menuText= SearchMessages.group_occurrences;
		MenuManager javaSearchMM= new MenuManager(menuText, IContextMenuConstants.GROUP_SEARCH);
		javaSearchMM.setActionDefinitionId(IJavaEditorActionDefinitionIds.SEARCH_OCCURRENCES_IN_FILE_QUICK_MENU);
		javaSearchMM.add(new Action() {
		});
		javaSearchMM.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager mm) {
				mm.removeAll();
				updateActionsInJavaEditor();
				addAction(fOccurrencesInFileAction, mm);
				addAction(fFindImplementorOccurrencesAction, mm);
				addAction(fExceptionOccurrencesAction, mm);
				addAction(fMethodExitOccurrencesAction, mm);
				addAction(fBreakContinueTargetOccurrencesAction, mm);
				if (mm.isEmpty()) {
					mm.add(new Action(SearchMessages.group_occurrences_quickMenu_noEntriesAvailable) {
						@Override
						public boolean isEnabled() {
							return false;
						}
					});
				}
			}

			private void addAction(Action action, IMenuManager mm) {
				if (action.isEnabled())
					mm.add(action);
			}
		});
		manager.appendToGroup(fGroupId, javaSearchMM);
	}

	private void updateActionsInJavaEditor() {
		if (fEditor == null)
			return;

		ITypeRoot element= SelectionConverter.getInput(fEditor);
		if (element == null)
			return;

		ITextSelection textSelection= (ITextSelection) fEditor.getSelectionProvider().getSelection();
		IDocument document= JavaUI.getDocumentProvider().getDocument(fEditor.getEditorInput());
		JavaTextSelection javaSelection= new JavaTextSelection(element, document, textSelection.getOffset(), textSelection.getLength());

		fExceptionOccurrencesAction.update(javaSelection);
		fOccurrencesInFileAction.update(javaSelection);
		fFindImplementorOccurrencesAction.update(javaSelection);
		fBreakContinueTargetOccurrencesAction.update(javaSelection);
		fMethodExitOccurrencesAction.update(javaSelection);
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
			disposeAction(fFindImplementorOccurrencesAction, provider);
			disposeAction(fExceptionOccurrencesAction, provider);
			disposeAction(fOccurrencesInFileAction, provider);
			disposeAction(fMethodExitOccurrencesAction, provider);
			disposeAction(fBreakContinueTargetOccurrencesAction, provider);
		}
		super.dispose();
		fFindImplementorOccurrencesAction= null;
		fExceptionOccurrencesAction= null;
		fOccurrencesInFileAction= null;
		fMethodExitOccurrencesAction= null;
		fBreakContinueTargetOccurrencesAction= null;
		updateGlobalActionHandlers();
	}

	private void updateGlobalActionHandlers() {
		if (fActionBars != null) {
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_OCCURRENCES_IN_FILE, fOccurrencesInFileAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_EXCEPTION_OCCURRENCES, fExceptionOccurrencesAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_IMPLEMENT_OCCURRENCES, fFindImplementorOccurrencesAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_BREAK_CONTINUE_TARGET_OCCURRENCES, fBreakContinueTargetOccurrencesAction);
			fActionBars.setGlobalActionHandler(JdtActionConstants.FIND_METHOD_EXIT_OCCURRENCES, fMethodExitOccurrencesAction);

		}
	}

	private void disposeAction(ISelectionChangedListener action, ISelectionProvider provider) {
		if (action != null)
			provider.removeSelectionChangedListener(action);
	}
}

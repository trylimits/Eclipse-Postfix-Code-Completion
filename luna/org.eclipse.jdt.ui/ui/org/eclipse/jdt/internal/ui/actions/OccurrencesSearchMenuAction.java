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
package org.eclipse.jdt.internal.ui.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.viewers.ISelection;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IPartService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowPulldownDelegate2;
import org.eclipse.ui.actions.RetargetAction;

import org.eclipse.jdt.core.ITypeRoot;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds;
import org.eclipse.jdt.ui.actions.JdtActionConstants;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;

/**
 * <p>
 * This is required because of
 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=79162
 * and
 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=137679
 * </p>
 */
public class OccurrencesSearchMenuAction implements IWorkbenchWindowPulldownDelegate2 {

	private static Action NO_ACTION_AVAILABLE= new Action(ActionMessages.OccurrencesSearchMenuAction_no_entries_available) {
		@Override
		public boolean isEnabled() {
			return false;
		}
	};

	private Menu fMenu;

	private IPartService fPartService;
	private RetargetAction[] fRetargetActions;

	/**
	 * {@inheritDoc}
	 */
	public Menu getMenu(Menu parent) {
		setMenu(new Menu(parent));
		fillMenu(fMenu);
		initMenu(fMenu);
		return fMenu;
	}

	/**
	 * {@inheritDoc}
	 */
	public Menu getMenu(Control parent) {
		setMenu(new Menu(parent));
		fillMenu(fMenu);
		initMenu(fMenu);
		return fMenu;
	}

	protected void initMenu(Menu menu) {
		menu.addMenuListener(new MenuAdapter() {
			@Override
			public void menuShown(MenuEvent e) {
				Menu m= (Menu) e.widget;
				MenuItem[] items= m.getItems();
				for (int i= 0; i < items.length; i++) {
					items[i].dispose();
				}
				fillMenu(m);
			}
		});
	}

	/**
	 * {@inheritDoc}
	 */
	public void dispose() {
		setMenu(null);
		disposeSubmenuActions();
	}

	private RetargetAction createSubmenuAction(IPartService partService, String actionID, String text, String actionDefinitionId) {
		RetargetAction action= new RetargetAction(actionID, text);
		action.setActionDefinitionId(actionDefinitionId);

		partService.addPartListener(action);
		IWorkbenchPart activePart = partService.getActivePart();
		if (activePart != null) {
			action.partActivated(activePart);
		}
		return action;
	}

	private void disposeSubmenuActions() {
		if (fPartService != null && fRetargetActions != null) {
			for (int i= 0; i < fRetargetActions.length; i++) {
				fPartService.removePartListener(fRetargetActions[i]);
				fRetargetActions[i].dispose();
			}
		}
		fRetargetActions= null;
		fPartService= null;
	}

	/**
	 * {@inheritDoc}
	 */
	public void init(IWorkbenchWindow window) {
		disposeSubmenuActions(); // paranoia code: double initialization should not happen
		if (window != null) {
			fPartService= window.getPartService();
			if (fPartService != null) {
				fRetargetActions= new RetargetAction[] {
					createSubmenuAction(fPartService, JdtActionConstants.FIND_OCCURRENCES_IN_FILE, ActionMessages.OccurrencesSearchMenuAction_occurrences_in_file_label, IJavaEditorActionDefinitionIds.SEARCH_OCCURRENCES_IN_FILE),
					createSubmenuAction(fPartService, JdtActionConstants.FIND_IMPLEMENT_OCCURRENCES, ActionMessages.OccurrencesSearchMenuAction_implementing_methods_label, IJavaEditorActionDefinitionIds.SEARCH_IMPLEMENT_OCCURRENCES_IN_FILE),
					createSubmenuAction(fPartService, JdtActionConstants.FIND_EXCEPTION_OCCURRENCES, ActionMessages.OccurrencesSearchMenuAction_throwing_exception_label, IJavaEditorActionDefinitionIds.SEARCH_EXCEPTION_OCCURRENCES_IN_FILE),
					createSubmenuAction(fPartService, JdtActionConstants.FIND_METHOD_EXIT_OCCURRENCES, ActionMessages.OccurrencesSearchMenuAction_method_exits_label, IJavaEditorActionDefinitionIds.SEARCH_METHOD_EXIT_OCCURRENCES),
					createSubmenuAction(fPartService, JdtActionConstants.FIND_BREAK_CONTINUE_TARGET_OCCURRENCES, ActionMessages.OccurrencesSearchMenuAction_break_continue_target_label, IJavaEditorActionDefinitionIds.SEARCH_BREAK_CONTINUE_TARGET_OCCURRENCES)
				};
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void run(IAction a) {
		if (fRetargetActions == null)
			return;

		JavaEditor editor;
		ISelection selection;

		IWorkbenchPart activePart= JavaPlugin.getActivePage().getActivePart();
		if (activePart instanceof JavaEditor) {
			selection= getEditorSelection((JavaEditor) activePart);
			if (selection == null)
				return;

			if (selection instanceof ITextSelection) {
				editor= (JavaEditor) activePart;
			} else {
				editor= null;
			}
		} else {
			editor= null;
			selection= activePart.getSite().getSelectionProvider().getSelection();
		}

		final ArrayList<IAction> activeActions= new ArrayList<IAction>(fRetargetActions.length);
		for (int i= 0; i < fRetargetActions.length; i++) {
			RetargetAction action= fRetargetActions[i];
			IAction actionHandler= action.getActionHandler();
			if (actionHandler instanceof SelectionDispatchAction) {
				((SelectionDispatchAction) actionHandler).update(selection);
				if (actionHandler.isEnabled()) {
					activeActions.add(actionHandler);
				}
			}
		}
		if (activeActions.size() == 1) {
			activeActions.get(0).run();
		} else {
			new JDTQuickMenuCreator(editor) {
				@Override
				protected void fillMenu(IMenuManager menu) {
					fillQuickMenu(menu, activeActions);
				}
			}.createMenu();
		}
	}

	private void updateActions() {
		IWorkbenchPart activePart= JavaPlugin.getActivePage().getActivePart();
		if (!(activePart instanceof JavaEditor))
			return;

		ISelection javaSelection= getEditorSelection((JavaEditor) activePart);
		if (javaSelection == null)
			return;

		for (int i= 0; i < fRetargetActions.length; i++) {
			RetargetAction action= fRetargetActions[i];
			IAction actionHandler= action.getActionHandler();
			if (actionHandler instanceof SelectionDispatchAction) {
				((SelectionDispatchAction) actionHandler).update(javaSelection);
			}
		}
	}

	private ISelection getEditorSelection(JavaEditor editor) {
		ITypeRoot element= SelectionConverter.getInput(editor);
		if (element == null)
			return null;

		if (editor.isBreadcrumbActive())
			return editor.getBreadcrumb().getSelectionProvider().getSelection();
		else {
			ITextSelection textSelection= (ITextSelection) editor.getSelectionProvider().getSelection();
			IDocument document= JavaUI.getDocumentProvider().getDocument(editor.getEditorInput());
			return new JavaTextSelection(element, document, textSelection.getOffset(), textSelection.getLength());
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public void selectionChanged(IAction action, ISelection selection) {
	}

	private void fillQuickMenu(IMenuManager manager, List<IAction> activeActions) {
		if (activeActions.isEmpty()) {
			manager.add(NO_ACTION_AVAILABLE);
		} else {
			for (int i= 0; i < activeActions.size(); i++) {
				manager.add(activeActions.get(i));
			}
		}
	}

	/**
	 * The menu to show in the workbench menu
	 * @param menu the menu to fill
	 */
	public void fillMenu(Menu menu) {
		if (fRetargetActions != null) {
			updateActions();
			for (int i= 0; i < fRetargetActions.length; i++) {
				ActionContributionItem item= new ActionContributionItem(fRetargetActions[i]);
				item.fill(menu, -1);
			}
		} else {
			// can only happen if 'init' was not called: programming error
			ActionContributionItem item= new ActionContributionItem(NO_ACTION_AVAILABLE);
			item.fill(menu, -1);
		}
	}

	private void setMenu(Menu menu) {
		if (fMenu != null) {
			fMenu.dispose();
		}
		fMenu = menu;
	}
}

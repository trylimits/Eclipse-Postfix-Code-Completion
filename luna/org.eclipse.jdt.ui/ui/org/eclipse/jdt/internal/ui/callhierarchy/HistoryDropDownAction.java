/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 * 			(report 36180: Callers/Callees view)
 *   Stephan Herrmann (stephan@cs.tu-berlin.de):
 *          - bug 75800: [call hierarchy] should allow searches for fields
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IMenuCreator;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IMember;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;


class HistoryDropDownAction extends Action implements IMenuCreator {

	private static class ClearHistoryAction extends Action {

		/**
		 * Creates a clear history action.
		 * 
		 * @param view the Call Hierarchy view part
		 */
		public ClearHistoryAction(CallHierarchyViewPart view) {
			super(CallHierarchyMessages.HistoryDropDownAction_clearhistory_label);
		}

		@Override
		public void run() {
			CallHierarchyUI.getDefault().clearHistory();
		}
	}

    public static final int RESULTS_IN_DROP_DOWN = 10;
    private CallHierarchyViewPart fView;
    private Menu fMenu;

    public HistoryDropDownAction(CallHierarchyViewPart view) {
        fView = view;
        fMenu = null;
        setToolTipText(CallHierarchyMessages.HistoryDropDownAction_tooltip);
        JavaPluginImages.setLocalImageDescriptors(this, "history_list.gif"); //$NON-NLS-1$

        PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.CALL_HIERARCHY_HISTORY_DROP_DOWN_ACTION);

        setMenuCreator(this);
    }

    public Menu getMenu(Menu parent) {
        return null;
    }

    public Menu getMenu(Control parent) {
        if (fMenu != null) {
            fMenu.dispose();
        }
        fMenu= new Menu(parent);
        IMember[][] elements= fView.getHistoryEntries();
        addEntries(fMenu, elements);
		new MenuItem(fMenu, SWT.SEPARATOR);
		addActionToMenu(fMenu, new HistoryListAction(fView));
		addActionToMenu(fMenu, new ClearHistoryAction(fView));
        return fMenu;
    }

    public void dispose() {
        fView = null;

        if (fMenu != null) {
            fMenu.dispose();
            fMenu = null;
        }
    }

    protected void addActionToMenu(Menu parent, Action action) {
        ActionContributionItem item = new ActionContributionItem(action);
        item.fill(parent, -1);
    }

    private boolean addEntries(Menu menu, IMember[][] elements) {
        boolean checked = false;

        int min = Math.min(elements.length, RESULTS_IN_DROP_DOWN);

        for (int i = 0; i < min; i++) {
            HistoryAction action = new HistoryAction(fView, elements[i]);
            action.setChecked(Arrays.equals(elements[i], fView.getInputElements()));
            checked = checked || action.isChecked();
            addActionToMenu(menu, action);
        }

        return checked;
    }

    @Override
	public void run() {
        new HistoryListAction(fView).run();
    }
}

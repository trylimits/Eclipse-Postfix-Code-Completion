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
package org.eclipse.jdt.internal.ui.workingsets;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.actions.ActionGroup;

import org.eclipse.jdt.ui.IContextMenuConstants;

import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart;

/**
 * An action group to provide access to the working sets.
 */
public class ViewActionGroup extends ActionGroup {

	private static final int SHOW_PROJECTS= PackageExplorerPart.PROJECTS_AS_ROOTS;
	private static final int SHOW_WORKING_SETS= PackageExplorerPart.WORKING_SETS_AS_ROOTS;

	public static final String MODE_CHANGED= ViewActionGroup.class.getName() + ".mode_changed"; //$NON-NLS-1$

	private static final Integer INT_SHOW_PROJECTS= new Integer(SHOW_PROJECTS);
	private static final Integer INT_SHOW_WORKING_SETS= new Integer(SHOW_WORKING_SETS);

	private IPropertyChangeListener fChangeListener;

	private int fMode;
	private IMenuManager fMenuManager;
	private IWorkingSetActionGroup fActiveActionGroup;
	private final WorkingSetShowActionGroup fShowActionGroup;
	private final WorkingSetFilterActionGroup fFilterActionGroup;
	private final ConfigureWorkingSetAssignementAction fWorkingSetAssignementAction;
	private final IWorkbenchPartSite fSite;

	public ViewActionGroup(int mode, IPropertyChangeListener changeListener, IWorkbenchPartSite site) {
		fChangeListener= changeListener;
		fSite= site;
		if (fChangeListener == null) {
			fChangeListener = new IPropertyChangeListener() {
				public void propertyChange(PropertyChangeEvent event) {}
			};
		}
		fFilterActionGroup= new WorkingSetFilterActionGroup(site, fChangeListener);
		fShowActionGroup= new WorkingSetShowActionGroup(site);
		fWorkingSetAssignementAction= new ConfigureWorkingSetAssignementAction(site);

		ISelectionProvider selectionProvider= site.getSelectionProvider();
		selectionProvider.addSelectionChangedListener(fWorkingSetAssignementAction);

		fMode= mode;
		if (fMode == SHOW_WORKING_SETS)
			fActiveActionGroup= fShowActionGroup;
		else
			fActiveActionGroup= fFilterActionGroup;
	}

	@Override
	public void dispose() {
		fFilterActionGroup.dispose();
		fShowActionGroup.dispose();
		fChangeListener= null;
		ISelectionProvider selectionProvider= fSite.getSelectionProvider();
		selectionProvider.removeSelectionChangedListener(fWorkingSetAssignementAction);
		super.dispose();
	}

	public void setWorkingSetModel(WorkingSetModel model) {
		fShowActionGroup.setWorkingSetMode(model);
		fWorkingSetAssignementAction.setWorkingSetModel(model);
	}

	@Override
	public void fillContextMenu(IMenuManager menu) {
		if (fWorkingSetAssignementAction.isEnabled())
			menu.appendToGroup(IContextMenuConstants.GROUP_BUILD, fWorkingSetAssignementAction);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void fillActionBars(IActionBars actionBars) {
		super.fillActionBars(actionBars);
		fMenuManager= actionBars.getMenuManager();
		fillViewMenu(fMenuManager);

		if (fActiveActionGroup == null)
			fActiveActionGroup= fFilterActionGroup;
		((ActionGroup)fActiveActionGroup).fillActionBars(actionBars);
	}

	private void fillViewMenu(IMenuManager menu) {
		IMenuManager showMenu= new MenuManager(WorkingSetMessages.ViewActionGroup_show_label);
		fillShowMenu(showMenu);
		menu.add(showMenu);
		menu.add(new Separator(IWorkingSetActionGroup.ACTION_GROUP));
	}

	private void fillShowMenu(IMenuManager menu) {
		ViewAction projects= new ViewAction(this, SHOW_PROJECTS);
		projects.setText(WorkingSetMessages.ViewActionGroup_projects_label);
		menu.add(projects);
		ViewAction workingSets= new ViewAction(this, SHOW_WORKING_SETS);
		workingSets.setText(WorkingSetMessages.ViewActionGroup_workingSets_label);
		menu.add(workingSets);
		if (fMode == SHOW_PROJECTS) {
			projects.setChecked(true);
		} else {
			workingSets.setChecked(true);
		}
	}

	public void fillFilters(StructuredViewer viewer) {
		ViewerFilter workingSetFilter= fFilterActionGroup.getWorkingSetFilter();
		if (fMode == SHOW_PROJECTS) {
			viewer.addFilter(workingSetFilter);
		} else {
			viewer.removeFilter(workingSetFilter);
		}
	}

	public void setMode(int mode) {
		fMode= mode;
		fActiveActionGroup.cleanViewMenu(fMenuManager);
		PropertyChangeEvent event;
		if (mode == SHOW_PROJECTS) {
			fActiveActionGroup= fFilterActionGroup;
			event= new PropertyChangeEvent(this, MODE_CHANGED, INT_SHOW_WORKING_SETS, INT_SHOW_PROJECTS);
		} else {
			fActiveActionGroup= fShowActionGroup;
			event= new PropertyChangeEvent(this, MODE_CHANGED, INT_SHOW_PROJECTS, INT_SHOW_WORKING_SETS);
		}
		fActiveActionGroup.fillViewMenu(fMenuManager);
		fMenuManager.updateAll(true);
		if(fChangeListener != null)
			fChangeListener.propertyChange(event);
	}

	public WorkingSetFilterActionGroup getFilterGroup() {
		return fFilterActionGroup;
	}

	public void restoreState(IMemento memento) {
		fFilterActionGroup.restoreState(memento);
	}

	public void saveState(IMemento memento) {
		fFilterActionGroup.saveState(memento);
	}
}

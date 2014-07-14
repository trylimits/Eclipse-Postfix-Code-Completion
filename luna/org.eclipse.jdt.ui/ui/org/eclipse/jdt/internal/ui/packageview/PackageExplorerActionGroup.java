/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Eric Rizzo - replaced Collapse All action with generic equivalent
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.packageview;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.OpenStrategy;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeViewer;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.actions.OpenInNewWindowAction;
import org.eclipse.ui.handlers.CollapseAllHandler;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.views.framelist.BackAction;
import org.eclipse.ui.views.framelist.ForwardAction;
import org.eclipse.ui.views.framelist.Frame;
import org.eclipse.ui.views.framelist.FrameAction;
import org.eclipse.ui.views.framelist.FrameList;
import org.eclipse.ui.views.framelist.GoIntoAction;
import org.eclipse.ui.views.framelist.TreeFrame;
import org.eclipse.ui.views.framelist.UpAction;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.actions.BuildActionGroup;
import org.eclipse.jdt.ui.actions.CCPActionGroup;
import org.eclipse.jdt.ui.actions.CustomFiltersActionGroup;
import org.eclipse.jdt.ui.actions.GenerateActionGroup;
import org.eclipse.jdt.ui.actions.ImportActionGroup;
import org.eclipse.jdt.ui.actions.JavaSearchActionGroup;
import org.eclipse.jdt.ui.actions.JdtActionConstants;
import org.eclipse.jdt.ui.actions.NavigateActionGroup;
import org.eclipse.jdt.ui.actions.OpenProjectAction;
import org.eclipse.jdt.ui.actions.ProjectActionGroup;
import org.eclipse.jdt.ui.actions.RefactorActionGroup;

import org.eclipse.jdt.internal.ui.actions.CollapseAllAction;
import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.actions.NewWizardsActionGroup;
import org.eclipse.jdt.internal.ui.actions.SelectAllAction;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage.GenerateBuildPathActionGroup;
import org.eclipse.jdt.internal.ui.workingsets.ViewActionGroup;

class PackageExplorerActionGroup extends CompositeActionGroup {

	private static final String FRAME_ACTION_SEPARATOR_ID= "FRAME_ACTION_SEPARATOR_ID"; //$NON-NLS-1$
	private static final String FRAME_ACTION_GROUP_ID= "FRAME_ACTION_GROUP_ID"; //$NON-NLS-1$

	private PackageExplorerPart fPart;

	private FrameList fFrameList;
	private GoIntoAction fZoomInAction;
 	private BackAction fBackAction;
	private ForwardAction fForwardAction;
	private UpAction fUpAction;
	private boolean fFrameActionsShown;


	private GotoTypeAction fGotoTypeAction;
	private GotoPackageAction fGotoPackageAction;
	private GotoResourceAction fGotoResourceAction;
	private CollapseAllAction fCollapseAllAction;
	private SelectAllAction fSelectAllAction;


	private ToggleLinkingAction fToggleLinkingAction;

	private RefactorActionGroup fRefactorActionGroup;
	private NavigateActionGroup fNavigateActionGroup;
	private ViewActionGroup fViewActionGroup;

	private CustomFiltersActionGroup fCustomFiltersActionGroup;

	private IAction fGotoRequiredProjectAction;

	private ProjectActionGroup fProjectActionGroup;

	public PackageExplorerActionGroup(PackageExplorerPart part) {
		super();
		fPart= part;
		fFrameActionsShown= false;
		TreeViewer viewer= part.getTreeViewer();

		IPropertyChangeListener workingSetListener= new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				doWorkingSetChanged(event);
			}
		};

		IWorkbenchPartSite site = fPart.getSite();
		setGroups(new ActionGroup[] {
			new NewWizardsActionGroup(site),
			fNavigateActionGroup= new NavigateActionGroup(fPart),
			new CCPActionGroup(fPart),
            new GenerateBuildPathActionGroup(fPart),
			new GenerateActionGroup(fPart),
			fRefactorActionGroup= new RefactorActionGroup(fPart),
			new ImportActionGroup(fPart),
			new BuildActionGroup(fPart),
			new JavaSearchActionGroup(fPart),
			fProjectActionGroup= new ProjectActionGroup(fPart),
			fViewActionGroup= new ViewActionGroup(fPart.getRootMode(), workingSetListener, site),
			fCustomFiltersActionGroup= new CustomFiltersActionGroup(fPart, viewer),
			new LayoutActionGroup(fPart)
		});


		fViewActionGroup.fillFilters(viewer);

		PackagesFrameSource frameSource= new PackagesFrameSource(fPart);
		fFrameList= new FrameList(frameSource);
		frameSource.connectTo(fFrameList);
		fZoomInAction= new GoIntoAction(fFrameList);
		fPart.getSite().getSelectionProvider().addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				fZoomInAction.update();
				}
		});

		fBackAction= new BackAction(fFrameList);
		fForwardAction= new ForwardAction(fFrameList);
		fUpAction= new UpAction(fFrameList);
		fFrameList.addPropertyChangeListener(new IPropertyChangeListener() { // connect after the actions (order of property listener)
			public void propertyChange(PropertyChangeEvent event) {
				fPart.updateTitle();
				fPart.updateToolbar();
			}
		});

		fGotoTypeAction= new GotoTypeAction(fPart);
		fGotoPackageAction= new GotoPackageAction(fPart);
		fGotoResourceAction= new GotoResourceAction(fPart);

		fCollapseAllAction= new CollapseAllAction(fPart.getTreeViewer());
		fCollapseAllAction.setActionDefinitionId(CollapseAllHandler.COMMAND_ID);
		fToggleLinkingAction = new ToggleLinkingAction(fPart);
		fToggleLinkingAction.setActionDefinitionId(IWorkbenchCommandConstants.NAVIGATE_TOGGLE_LINK_WITH_EDITOR);

		fGotoRequiredProjectAction= new GotoRequiredProjectAction(fPart);
		fSelectAllAction= new SelectAllAction(fPart.getTreeViewer());
	}

	@Override
	public void dispose() {
		super.dispose();
	}


	//---- Persistent state -----------------------------------------------------------------------

	/* package */ void restoreFilterAndSorterState(IMemento memento) {
		fViewActionGroup.restoreState(memento);
		fCustomFiltersActionGroup.restoreState(memento);
	}

	/* package */ void saveFilterAndSorterState(IMemento memento) {
		fViewActionGroup.saveState(memento);
		fCustomFiltersActionGroup.saveState(memento);
	}

	//---- Action Bars ----------------------------------------------------------------------------

	@Override
	public void fillActionBars(IActionBars actionBars) {
		super.fillActionBars(actionBars);
		setGlobalActionHandlers(actionBars);
		fillToolBar(actionBars.getToolBarManager());
		fillViewMenu(actionBars.getMenuManager());
	}

	private void setGlobalActionHandlers(IActionBars actionBars) {
		// Navigate Go Into and Go To actions.
		actionBars.setGlobalActionHandler(IWorkbenchActionConstants.GO_INTO, fZoomInAction);
		actionBars.setGlobalActionHandler(ActionFactory.BACK.getId(), fBackAction);
		actionBars.setGlobalActionHandler(ActionFactory.FORWARD.getId(), fForwardAction);
		actionBars.setGlobalActionHandler(IWorkbenchActionConstants.UP, fUpAction);
		actionBars.setGlobalActionHandler(IWorkbenchActionConstants.GO_TO_RESOURCE, fGotoResourceAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.GOTO_TYPE, fGotoTypeAction);
		actionBars.setGlobalActionHandler(JdtActionConstants.GOTO_PACKAGE, fGotoPackageAction);
		actionBars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(), fSelectAllAction);

		fRefactorActionGroup.retargetFileMenuActions(actionBars);

		IHandlerService handlerService= (IHandlerService) fPart.getViewSite().getService(IHandlerService.class);
		handlerService.activateHandler(IWorkbenchCommandConstants.NAVIGATE_TOGGLE_LINK_WITH_EDITOR, new ActionHandler(fToggleLinkingAction));
		handlerService.activateHandler(CollapseAllHandler.COMMAND_ID, new ActionHandler(fCollapseAllAction));
	}

	/* package */ void fillToolBar(IToolBarManager toolBar) {
		if (fBackAction.isEnabled() || fUpAction.isEnabled() || fForwardAction.isEnabled()) {
			toolBar.add(fBackAction);
			toolBar.add(fForwardAction);
			toolBar.add(fUpAction);
			toolBar.add(new Separator(FRAME_ACTION_SEPARATOR_ID));
			fFrameActionsShown= true;
		}
		toolBar.add(new GroupMarker(FRAME_ACTION_GROUP_ID));
		toolBar.add(fCollapseAllAction);
		toolBar.add(fToggleLinkingAction);
		toolBar.update(true);
	}

	public void updateToolBar(IToolBarManager toolBar) {

		boolean hasBeenFrameActionsShown= fFrameActionsShown;
		fFrameActionsShown= fBackAction.isEnabled() || fUpAction.isEnabled() || fForwardAction.isEnabled();
		if (fFrameActionsShown != hasBeenFrameActionsShown) {
			if (hasBeenFrameActionsShown) {
				toolBar.remove(fBackAction.getId());
				toolBar.remove(fForwardAction.getId());
				toolBar.remove(fUpAction.getId());
				toolBar.remove(FRAME_ACTION_SEPARATOR_ID);
			} else {
				toolBar.prependToGroup(FRAME_ACTION_GROUP_ID, new Separator(FRAME_ACTION_SEPARATOR_ID));
				toolBar.prependToGroup(FRAME_ACTION_GROUP_ID, fUpAction);
				toolBar.prependToGroup(FRAME_ACTION_GROUP_ID, fForwardAction);
				toolBar.prependToGroup(FRAME_ACTION_GROUP_ID, fBackAction);
			}
			toolBar.update(true);
		}
	}

	/* package */ void fillViewMenu(IMenuManager menu) {
		menu.add(new Separator());
		menu.add(fToggleLinkingAction);
		menu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		menu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS+"-end"));//$NON-NLS-1$
	}

	//---- Context menu -------------------------------------------------------------------------

	@Override
	public void fillContextMenu(IMenuManager menu) {
		IStructuredSelection selection= (IStructuredSelection)getContext().getSelection();
		int size= selection.size();
		Object element= selection.getFirstElement();

		if (element instanceof ClassPathContainer.RequiredProjectWrapper)
			menu.appendToGroup(IContextMenuConstants.GROUP_SHOW, fGotoRequiredProjectAction);

		addGotoMenu(menu, element, size);

		addOpenNewWindowAction(menu, element);

		super.fillContextMenu(menu);
	}

	 private void addGotoMenu(IMenuManager menu, Object element, int size) {
		boolean enabled= size == 1 && fPart.getTreeViewer().isExpandable(element) && (isGoIntoTarget(element) || element instanceof IContainer);
		fZoomInAction.setEnabled(enabled);
		if (enabled)
			menu.appendToGroup(IContextMenuConstants.GROUP_GOTO, fZoomInAction);
	}

	private boolean isGoIntoTarget(Object element) {
		if (element == null)
			return false;
		if (element instanceof IJavaElement) {
			int type= ((IJavaElement)element).getElementType();
			return type == IJavaElement.JAVA_PROJECT ||
				type == IJavaElement.PACKAGE_FRAGMENT_ROOT ||
				type == IJavaElement.PACKAGE_FRAGMENT;
		}
		if (element instanceof IWorkingSet) {
			return true;
		}
		return false;
	}

	private void addOpenNewWindowAction(IMenuManager menu, Object element) {
		if (element instanceof IJavaElement) {
			element= ((IJavaElement)element).getResource();

		}
		// fix for 64890 Package explorer out of sync when open/closing projects [package explorer] 64890
		if (element instanceof IProject && !((IProject)element).isOpen())
			return;

		if (!(element instanceof IContainer))
			return;
		menu.appendToGroup(
			IContextMenuConstants.GROUP_OPEN,
			new OpenInNewWindowAction(fPart.getSite().getWorkbenchWindow(), (IContainer)element));
	}

	//---- Key board and mouse handling ------------------------------------------------------------

	/* package*/ void handleDoubleClick(DoubleClickEvent event) {
		TreeViewer viewer= fPart.getTreeViewer();
		IStructuredSelection selection= (IStructuredSelection)event.getSelection();
		Object element= selection.getFirstElement();
		if (viewer.isExpandable(element)) {
			if (doubleClickGoesInto()) {
				// don't zoom into compilation units and class files
				if (element instanceof ICompilationUnit || element instanceof IClassFile)
					return;
				if (element instanceof IOpenable || element instanceof IContainer || element instanceof IWorkingSet) {
					fZoomInAction.run();
				}
			} else {
				IAction openAction= fNavigateActionGroup.getOpenAction();
				if (openAction != null && openAction.isEnabled() && OpenStrategy.getOpenMethod() == OpenStrategy.DOUBLE_CLICK)
					return;
				if (selection instanceof ITreeSelection) {
					TreePath[] paths= ((ITreeSelection)selection).getPathsFor(element);
					for (int i= 0; i < paths.length; i++) {
						viewer.setExpandedState(paths[i], !viewer.getExpandedState(paths[i]));
					}
				} else {
					viewer.setExpandedState(element, !viewer.getExpandedState(element));
				}
			}
		} else if (element instanceof IProject && !((IProject) element).isOpen()) {
			OpenProjectAction openProjectAction= fProjectActionGroup.getOpenProjectAction();
			if (openProjectAction.isEnabled()) {
				openProjectAction.run();
			}
		}
	}

	/**
	 * Called by Package Explorer.
	 *
	 * @param event the open event
	 * @param activate <code>true</code> if the opened editor should be activated
	 */
	/* package */void handleOpen(ISelection event, boolean activate) {
		IAction openAction= fNavigateActionGroup.getOpenAction();
		if (openAction != null && openAction.isEnabled()) {
			// XXX: should use the given arguments instead of using org.eclipse.jface.util.OpenStrategy.activateOnOpen()
			openAction.run();
			return;
		}
	}

	/* package */ void handleKeyEvent(KeyEvent event) {
		if (event.stateMask != 0)
			return;

		if (event.keyCode == SWT.BS) {
			if (fUpAction != null && fUpAction.isEnabled()) {
				fUpAction.run();
				event.doit= false;
			}
		}
	}

	private void doWorkingSetChanged(PropertyChangeEvent event) {
		if (ViewActionGroup.MODE_CHANGED.equals(event.getProperty())) {
			fPart.rootModeChanged(((Integer)event.getNewValue()).intValue());
			Object oldInput= null;
			Object newInput= null;
			if (fPart.getRootMode() == PackageExplorerPart.PROJECTS_AS_ROOTS) {
				oldInput= fPart.getWorkingSetModel();
				newInput= JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
			} else {
				oldInput= JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
				newInput= fPart.getWorkingSetModel();
			}
			if (oldInput != null && newInput != null) {
				Frame frame;
				for (int i= 0; (frame= fFrameList.getFrame(i)) != null; i++) {
					if (frame instanceof TreeFrame) {
						TreeFrame treeFrame= (TreeFrame)frame;
						if (oldInput.equals(treeFrame.getInput()))
							treeFrame.setInput(newInput);
					}
				}
			}
		} else {
			IWorkingSet workingSet= (IWorkingSet) event.getNewValue();

			String workingSetLabel= null;
			if (workingSet != null)
				workingSetLabel= BasicElementLabels.getWorkingSetLabel(workingSet);
			fPart.setWorkingSetLabel(workingSetLabel);
			fPart.updateTitle();

			String property= event.getProperty();
			if (IWorkingSetManager.CHANGE_WORKING_SET_CONTENT_CHANGE.equals(property)) {
				TreeViewer viewer= fPart.getTreeViewer();
				viewer.getControl().setRedraw(false);
				viewer.refresh();
				viewer.getControl().setRedraw(true);
			}
		}
	}

	private boolean doubleClickGoesInto() {
		return PreferenceConstants.DOUBLE_CLICK_GOES_INTO.equals(PreferenceConstants.getPreferenceStore().getString(PreferenceConstants.DOUBLE_CLICK));
	}

	public FrameAction getUpAction() {
		return fUpAction;
	}

	public FrameAction getBackAction() {
		return fBackAction;
	}
	public FrameAction getForwardAction() {
		return fForwardAction;
	}

	public ViewActionGroup getWorkingSetActionGroup() {
	    return fViewActionGroup;
	}

	public CustomFiltersActionGroup getCustomFilterActionGroup() {
	    return fCustomFiltersActionGroup;
	}

	public FrameList getFrameList() {
		return fFrameList;
	}
}

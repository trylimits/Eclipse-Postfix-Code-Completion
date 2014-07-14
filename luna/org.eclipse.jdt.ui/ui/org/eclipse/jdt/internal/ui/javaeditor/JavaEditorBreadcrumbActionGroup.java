/*******************************************************************************
 * Copyright (c) 2008, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.javaeditor;

import org.eclipse.core.commands.operations.IUndoContext;

import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.viewers.ISelectionProvider;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.operations.UndoRedoActionGroup;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.actions.BuildActionGroup;
import org.eclipse.jdt.ui.actions.CCPActionGroup;
import org.eclipse.jdt.ui.actions.GenerateActionGroup;
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds;
import org.eclipse.jdt.ui.actions.JavaSearchActionGroup;
import org.eclipse.jdt.ui.actions.NavigateActionGroup;
import org.eclipse.jdt.ui.actions.ProjectActionGroup;
import org.eclipse.jdt.ui.actions.RefactorActionGroup;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.actions.NewWizardsActionGroup;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage.GenerateBuildPathActionGroup;
import org.eclipse.jdt.internal.ui.workingsets.ConfigureWorkingSetAssignementAction;


/**
 * Actions to show in the context menu for elements in the {@link JavaEditorBreadcrumb}.
 *
 * @since 3.4
 */
final class JavaEditorBreadcrumbActionGroup extends CompositeActionGroup	 {

	static final class WorkingSetActionGroup extends ActionGroup {

		private final ConfigureWorkingSetAssignementAction fAssignWorkingSetAction;
		private final ISelectionProvider fSelectionProvider;

		public WorkingSetActionGroup(IWorkbenchSite site, ISelectionProvider selectionProvider) {
			fSelectionProvider= selectionProvider;
			fAssignWorkingSetAction= new ConfigureWorkingSetAssignementAction(site);
			fAssignWorkingSetAction.setSpecialSelectionProvider(selectionProvider);
			selectionProvider.addSelectionChangedListener(fAssignWorkingSetAction);
		}

		@Override
		public void dispose() {
			super.dispose();
			fSelectionProvider.removeSelectionChangedListener(fAssignWorkingSetAction);
		}

		@Override
		public void fillContextMenu(IMenuManager menu) {
			if (fAssignWorkingSetAction.isEnabled())
				menu.appendToGroup(IContextMenuConstants.GROUP_BUILD, fAssignWorkingSetAction);
		}
	}

	private static final class BreadcrumbActionGroup extends ActionGroup {

		private static final class GoToEditorAction extends Action {

			private final JavaEditor fJavaEditor;

			public GoToEditorAction(JavaEditor javaEditor) {
				super(JavaEditorMessages.JavaEditorBreadcrumbActionGroup_go_to_editor_action_label);
				setEnabled(true);
				fJavaEditor= javaEditor;
			}

			/*
			 * @see org.eclipse.jface.action.Action#run()
			 */
			@Override
			public void run() {
				fJavaEditor.getViewer().getTextWidget().setFocus();
			}
		}

		private GoToEditorAction fGoToEditor;
		private ToggleBreadcrumbAction fHideBreadcrumb;

		public BreadcrumbActionGroup(JavaEditor javaEditor) {
			fGoToEditor= new GoToEditorAction(javaEditor);
			fGoToEditor.setActionDefinitionId(IJavaEditorActionDefinitionIds.SHOW_IN_BREADCRUMB);
			fHideBreadcrumb= new ToggleBreadcrumbAction(javaEditor.getSite().getPage(), true);
			fHideBreadcrumb.setActionDefinitionId(IJavaEditorActionDefinitionIds.TOGGLE_BREADCRUMB);
		}

		/*
		 * @see org.eclipse.ui.actions.ActionGroup#fillActionBars(org.eclipse.ui.IActionBars)
		 */
		@Override
		public void fillActionBars(IActionBars actionBars) {
			super.fillActionBars(actionBars);
			actionBars.setGlobalActionHandler(IJavaEditorActionDefinitionIds.SHOW_IN_BREADCRUMB, fGoToEditor);
		}

		/*
		 * @see org.eclipse.ui.actions.ActionGroup#fillContextMenu(org.eclipse.jface.action.IMenuManager)
		 */
		@Override
		public void fillContextMenu(IMenuManager menu) {
			super.fillContextMenu(menu);
			menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, fGoToEditor);
			menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, fHideBreadcrumb);
		}
	}

	public JavaEditorBreadcrumbActionGroup(JavaEditor javaEditor, ISelectionProvider selectionProvider) {
		super(new ActionGroup[] {
				new BreadcrumbActionGroup(javaEditor),
				new UndoRedoActionGroup(javaEditor.getEditorSite(), (IUndoContext) ResourcesPlugin.getWorkspace().getAdapter(IUndoContext.class), true),
				new NewWizardsActionGroup(javaEditor.getEditorSite()),
				new JavaSearchActionGroup(javaEditor.getEditorSite(), selectionProvider),
				new NavigateActionGroup(javaEditor.getEditorSite(), selectionProvider),
				new CCPActionGroup(javaEditor.getEditorSite(), selectionProvider),
				new GenerateBuildPathActionGroup(javaEditor.getEditorSite(), selectionProvider),
				new GenerateActionGroup(javaEditor.getEditorSite(), selectionProvider),
				new RefactorActionGroup(javaEditor.getEditorSite(), selectionProvider),
				new BuildActionGroup(javaEditor.getEditorSite(), selectionProvider),
				new ProjectActionGroup(javaEditor.getEditorSite(), selectionProvider),
				new WorkingSetActionGroup(javaEditor.getEditorSite(), selectionProvider)
		});
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.actions.CompositeActionGroup#fillContextMenu(org.eclipse.jface.action.IMenuManager)
	 */
	@Override
	public void fillContextMenu(IMenuManager menu) {
		JavaPlugin.createStandardGroups(menu);

		super.fillContextMenu(menu);
	}
}

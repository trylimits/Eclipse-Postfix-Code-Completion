/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman, mpchapman@gmail.com - 89977 Make JDT .java agnostic
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.ToolBar;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.corext.buildpath.IBuildpathModifierListener;

import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.util.ViewerPane;

/**
 * Action group for the dialog package explorer shown on the second page
 * of the new java project wizard.
 */
public class DialogPackageExplorerActionGroup extends CompositeActionGroup {

	private DialogPackageExplorer fDialogPackageExplorer;

	private final AddFolderToBuildpathAction fAddFolderToBuildpathAction;
	private final RemoveFromBuildpathAction fRemoveFromBuildpathAction;
	private final ExcludeFromBuildpathAction fExcludeFromBuildpathAction;
	private final IncludeToBuildpathAction fIncludeToBuildpathAction;
	private final EditFilterAction fEditFilterAction;
	private final EditOutputFolderAction fEditOutputFolderAction;
	private final ClasspathModifierDropDownAction fDropDownAction;
	private final CreateLinkedSourceFolderAction fCreateLinkedSourceFolderAction;
	private final CreateSourceFolderAction fCreateSourceFolderAction;
	private final ResetAllAction fResetAllAction;

    /**
     * Constructor which creates the operations and based on this
     * operations the actions.
     *
     * @param provider a information provider to pass necessary information
     * to the operations
     * @param context
     * @param dialogPackageExplorer the package explorer for which to contribute the actions to
     * @param page the page showing the package explorer
     */
    public DialogPackageExplorerActionGroup(HintTextGroup provider, IRunnableContext context, DialogPackageExplorer dialogPackageExplorer, final NewSourceContainerWorkbookPage page) {
        super();

        fDialogPackageExplorer= dialogPackageExplorer;

        if (context == null)
        	context= PlatformUI.getWorkbench().getProgressService();

        fAddFolderToBuildpathAction= new AddFolderToBuildpathAction(context, fDialogPackageExplorer) {
        	@Override
			public void run() {
        		page.commitDefaultOutputFolder();
        	    super.run();
        	}
        };
		fDialogPackageExplorer.addSelectionChangedListener(fAddFolderToBuildpathAction);

		fRemoveFromBuildpathAction= new RemoveFromBuildpathAction(context, fDialogPackageExplorer) {
        	@Override
			public void run() {
        		page.commitDefaultOutputFolder();
        	    super.run();
        	}
        };
		fDialogPackageExplorer.addSelectionChangedListener(fRemoveFromBuildpathAction);

		fExcludeFromBuildpathAction= new ExcludeFromBuildpathAction(context, fDialogPackageExplorer) {
        	@Override
			public void run() {
        		page.commitDefaultOutputFolder();
        	    super.run();
        	}
        };
		fDialogPackageExplorer.addSelectionChangedListener(fExcludeFromBuildpathAction);

		fIncludeToBuildpathAction= new IncludeToBuildpathAction(context, fDialogPackageExplorer) {
        	@Override
			public void run() {
        		page.commitDefaultOutputFolder();
        	    super.run();
        	}
        };
		fDialogPackageExplorer.addSelectionChangedListener(fIncludeToBuildpathAction);

			fEditFilterAction= new EditFilterAction(context, fDialogPackageExplorer) {
	        	@Override
				public void run() {
	        		page.commitDefaultOutputFolder();
	        	    super.run();
	        	}
	        };
			fDialogPackageExplorer.addSelectionChangedListener(fEditFilterAction);

	        fEditOutputFolderAction= new EditOutputFolderAction(context, fDialogPackageExplorer) {
	        	@Override
				public void run() {
	        		page.commitDefaultOutputFolder();
	        	    super.run();
	        	}
	        };
			fDialogPackageExplorer.addSelectionChangedListener(fEditOutputFolderAction);

        fDropDownAction= new ClasspathModifierDropDownAction();
		fDropDownAction.addAction(fEditFilterAction);
        fDropDownAction.addAction(fEditOutputFolderAction);
		fDialogPackageExplorer.addPostSelectionChangedListener(fDropDownAction);

        fCreateLinkedSourceFolderAction= new CreateLinkedSourceFolderAction2(provider, context, fDialogPackageExplorer) {
        	@Override
			public void run() {
        		page.commitDefaultOutputFolder();
        	    super.run();
        	}

        	/**
        	 * {@inheritDoc}
        	 */
        	@Override
			protected List<IJavaProject> getSelectedElements() {
        		ArrayList<IJavaProject> result= new ArrayList<IJavaProject>();
        		result.add(page.getJavaProject());
        		return result;
        	}

        	/**
        	 * {@inheritDoc}
        	 */
        	@Override
			protected boolean canHandle(IStructuredSelection selection) {
        		return true;
        	}
        };

        fCreateSourceFolderAction= new CreateSourceFolderAction2(provider, context, fDialogPackageExplorer) {
        	@Override
			public void run() {
        		page.commitDefaultOutputFolder();
        	    super.run();
        	}

        	/**
        	 * {@inheritDoc}
        	 */
        	@Override
			protected List<IJavaProject> getSelectedElements() {
        		ArrayList<IJavaProject> result= new ArrayList<IJavaProject>();
        		result.add(page.getJavaProject());
        		return result;
        	}

        	/**
        	 * {@inheritDoc}
        	 */
        	@Override
			protected boolean canHandle(IStructuredSelection selection) {
        		return true;
        	}
        };

		fResetAllAction= new ResetAllAction(provider, context, fDialogPackageExplorer);


        //options:
        //AddArchiveToBuildpathAction
        //AddLibraryToBuildpathAction
        //AddSelectedLibraryToBuildpathAction
        //ResetAction
        //ResetAllOutputFoldersAction
    }

    /* (non-Javadoc)
     * @see org.eclipse.jdt.internal.ui.actions.CompositeActionGroup#dispose()
     */
    @Override
	public void dispose() {
        super.dispose();

        fDialogPackageExplorer.removeSelectionChangedListener(fAddFolderToBuildpathAction);
        fDialogPackageExplorer.removeSelectionChangedListener(fRemoveFromBuildpathAction);
        fDialogPackageExplorer.removeSelectionChangedListener(fExcludeFromBuildpathAction);
		fDialogPackageExplorer.removeSelectionChangedListener(fIncludeToBuildpathAction);
		fDialogPackageExplorer.removeSelectionChangedListener(fEditFilterAction);
		fDialogPackageExplorer.removeSelectionChangedListener(fEditOutputFolderAction);
		fDialogPackageExplorer.removePostSelectionChangedListener(fDropDownAction);
        fDialogPackageExplorer= null;
    }

    public void addBuildpathModifierListener(IBuildpathModifierListener listener) {
    	fAddFolderToBuildpathAction.addBuildpathModifierListener(listener);
    	fRemoveFromBuildpathAction.addBuildpathModifierListener(listener);
    	fExcludeFromBuildpathAction.addBuildpathModifierListener(listener);
    	fIncludeToBuildpathAction.addBuildpathModifierListener(listener);
    	fEditFilterAction.addBuildpathModifierListener(listener);
    	fEditOutputFolderAction.addBuildpathModifierListener(listener);
    	fCreateLinkedSourceFolderAction.addBuildpathModifierListener(listener);
    	fCreateSourceFolderAction.addBuildpathModifierListener(listener);
    }

	public void removeBuildpathModifierListener(IBuildpathModifierListener listener) {
		fAddFolderToBuildpathAction.removeBuildpathModifierListener(listener);
    	fRemoveFromBuildpathAction.removeBuildpathModifierListener(listener);
    	fExcludeFromBuildpathAction.removeBuildpathModifierListener(listener);
    	fIncludeToBuildpathAction.removeBuildpathModifierListener(listener);
    	fEditFilterAction.removeBuildpathModifierListener(listener);
    	fEditOutputFolderAction.removeBuildpathModifierListener(listener);
    	fCreateLinkedSourceFolderAction.removeBuildpathModifierListener(listener);
    	fCreateSourceFolderAction.removeBuildpathModifierListener(listener);
    }

    /**
     * Create a toolbar manager for a given
     * <code>ViewerPane</code>
     *
     * @param pane the pane to create the <code>
     * ToolBarManager</code> for.
     * @return the created <code>ToolBarManager</code>
     */
    public ToolBarManager createLeftToolBarManager(ViewerPane pane) {
        ToolBarManager tbm= pane.getToolBarManager();

        tbm.add(fAddFolderToBuildpathAction);
        tbm.add(fRemoveFromBuildpathAction);
        tbm.add(new Separator());
        tbm.add(fExcludeFromBuildpathAction);
        tbm.add(fIncludeToBuildpathAction);
        tbm.add(new Separator());
        tbm.add(fDropDownAction);

        tbm.update(true);
        return tbm;
    }

    /**
     * Create a toolbar manager for a given
     * <code>ViewerPane</code>
     *
     * @param pane the pane to create the help toolbar for
     * @return the created <code>ToolBarManager</code>
     */
    public ToolBarManager createLeftToolBar(ViewerPane pane) {
        ToolBar tb= new ToolBar(pane, SWT.FLAT);
        pane.setTopRight(tb);
        ToolBarManager tbm= new ToolBarManager(tb);

        tbm.add(fCreateLinkedSourceFolderAction);
        tbm.add(fCreateSourceFolderAction);
        tbm.add(fResetAllAction);
        tbm.add(new HelpAction());

        tbm.update(true);
        return tbm;
    }

    /**
     * Fill the context menu with the available actions
     *
     * @param menu the menu to be filled up with actions
     */
    @Override
	public void fillContextMenu(IMenuManager menu) {

    	if (fAddFolderToBuildpathAction.isEnabled())
    		menu.add(fAddFolderToBuildpathAction);

    	if (fRemoveFromBuildpathAction.isEnabled())
    		menu.add(fRemoveFromBuildpathAction);

    	if (fExcludeFromBuildpathAction.isEnabled())
    		menu.add(fExcludeFromBuildpathAction);

    	if (fIncludeToBuildpathAction.isEnabled())
    		menu.add(fIncludeToBuildpathAction);

    	if (fEditFilterAction.isEnabled())
    		menu.add(fEditFilterAction);

    	if (fEditOutputFolderAction.isEnabled())
    		menu.add(fEditOutputFolderAction);

    	if (fCreateLinkedSourceFolderAction.isEnabled())
    		menu.add(fCreateLinkedSourceFolderAction);

    	if (fCreateSourceFolderAction.isEnabled())
    		menu.add(fCreateSourceFolderAction);

        super.fillContextMenu(menu);
    }

	public BuildpathModifierAction[] getHintTextGroupActions() {
		List<BuildpathModifierAction> result= new ArrayList<BuildpathModifierAction>();

    	if (fCreateSourceFolderAction.isEnabled())
    		result.add(fCreateSourceFolderAction);

    	if (fCreateLinkedSourceFolderAction.isEnabled())
    		result.add(fCreateLinkedSourceFolderAction);

    	if (fEditFilterAction.isEnabled())
    		result.add(fEditFilterAction);

    	if (fExcludeFromBuildpathAction.isEnabled())
    		result.add(fExcludeFromBuildpathAction);

    	if (fIncludeToBuildpathAction.isEnabled())
    		result.add(fIncludeToBuildpathAction);

    	if (fEditOutputFolderAction.isEnabled())
    		result.add(fEditOutputFolderAction);

		if (fAddFolderToBuildpathAction.isEnabled())
    		result.add(fAddFolderToBuildpathAction);

    	if (fRemoveFromBuildpathAction.isEnabled())
    		result.add(fRemoveFromBuildpathAction);

	    return result.toArray(new BuildpathModifierAction[result.size()]);
    }

	public EditOutputFolderAction getEditOutputFolderAction() {
	    return fEditOutputFolderAction;
    }

	public ResetAllAction getResetAllAction() {
		return fResetAllAction;
    }
}

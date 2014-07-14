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
package org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.part.Page;

import org.eclipse.ui.texteditor.IUpdate;

import org.eclipse.jdt.ui.IContextMenuConstants;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.JarImportWizardAction;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;

/**
 * Action group that adds the source and generate actions to a part's context
 * menu and installs handlers for the corresponding global menu actions.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 3.1
 */
public class GenerateBuildPathActionGroup extends ActionGroup {
    /**
     * Pop-up menu: id of the source sub menu (value <code>org.eclipse.jdt.ui.buildpath.menu</code>).
     *
     * @since 3.1
     */
    public static final String MENU_ID= "org.eclipse.jdt.ui.buildpath.menu"; //$NON-NLS-1$

    /**
     * Pop-up menu: id of the build path (add /remove) group of the build path sub menu (value
     * <code>buildpathGroup</code>).
     *
     * @since 3.1
     */
    public static final String GROUP_BUILDPATH= "buildpathGroup";  //$NON-NLS-1$

    /**
     * Pop-up menu: id of the filter (include / exclude) group of the build path sub menu (value
     * <code>filterGroup</code>).
     *
     * @since 3.1
     */
    public static final String GROUP_FILTER= "filterGroup";  //$NON-NLS-1$

    /**
     * Pop-up menu: id of the customize (filters / output folder) group of the build path sub menu (value
     * <code>customizeGroup</code>).
     *
     * @since 3.1
     */
    public static final String GROUP_CUSTOMIZE= "customizeGroup";  //$NON-NLS-1$

	private static class NoActionAvailable extends Action {
		public NoActionAvailable() {
			setEnabled(false);
			setText(NewWizardMessages.GenerateBuildPathActionGroup_no_action_available);
		}
	}
	private Action fNoActionAvailable= new NoActionAvailable();

    private class UpdateJarFileAction extends JarImportWizardAction implements IUpdate {

		public UpdateJarFileAction() {
			setText(ActionMessages.GenerateBuildPathActionGroup_update_jar_text);
			setDescription(ActionMessages.GenerateBuildPathActionGroup_update_jar_description);
			setToolTipText(ActionMessages.GenerateBuildPathActionGroup_update_jar_tooltip);
			setImageDescriptor(JavaPluginImages.DESC_OBJS_JAR);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.JARIMPORT_WIZARD_PAGE);
		}

		/**
		 * {@inheritDoc}
		 */
		public void update() {
			final IWorkbenchPart part= fSite.getPage().getActivePart();
			if (part != null)
				setActivePart(this, part);
			selectionChanged(this, fSelectionProvider.getSelection());
		}
	}

    private final IWorkbenchSite fSite;
	private final ISelectionProvider fSelectionProvider;
	private final List<Action> fActions;

	private String fGroupName= IContextMenuConstants.GROUP_REORGANIZE;


    /**
     * Creates a new <code>GenerateActionGroup</code>. The group
     * requires that the selection provided by the page's selection provider
     * is of type <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
     *
     * @param page the page that owns this action group
     */
    public GenerateBuildPathActionGroup(Page page) {
		this(page.getSite(), page.getSite().getSelectionProvider());
    }

    /**
     * Creates a new <code>GenerateActionGroup</code>. The group
     * requires that the selection provided by the part's selection provider
     * is of type <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
     *
     * @param part the view part that owns this action group
     */
    public GenerateBuildPathActionGroup(IViewPart part) {
		this(part.getSite(), part.getSite().getSelectionProvider());
    }
    /**
	 * Creates a new <code>GenerateActionGroup</code>. The group requires
	 * that the selection provided by the given selection provider is of type
	 * {@link IStructuredSelection}.
	 *
	 * @param site the site that will own the action group.
	 * @param selectionProvider the selection provider used instead of the
	 *  page selection provider.
	 *
	 * @since 3.4
	 */
	public GenerateBuildPathActionGroup(IWorkbenchSite site, ISelectionProvider selectionProvider) {
        fSite= site;
		fSelectionProvider= selectionProvider;
        fActions= new ArrayList<Action>();

		final CreateLinkedSourceFolderAction addLinkedSourceFolderAction= new CreateLinkedSourceFolderAction(site);
		fActions.add(addLinkedSourceFolderAction);

        final CreateSourceFolderAction addSourceFolderAction= new CreateSourceFolderAction(site);
        fActions.add(addSourceFolderAction);

		final AddFolderToBuildpathAction addFolder= new AddFolderToBuildpathAction(site);
		fActions.add(addFolder);

		final AddSelectedLibraryToBuildpathAction addSelectedLibrary= new AddSelectedLibraryToBuildpathAction(site);
		fActions.add(addSelectedLibrary);

		final RemoveFromBuildpathAction remove= new RemoveFromBuildpathAction(site);
		fActions.add(remove);

		final AddArchiveToBuildpathAction addArchive= new AddArchiveToBuildpathAction(site);
		fActions.add(addArchive);

		final AddLibraryToBuildpathAction addLibrary= new AddLibraryToBuildpathAction(site);
		fActions.add(addLibrary);

		final UpdateJarFileAction updateAction= new UpdateJarFileAction();
		fActions.add(updateAction);

		final ExcludeFromBuildpathAction exclude= new ExcludeFromBuildpathAction(site);
		fActions.add(exclude);

		final IncludeToBuildpathAction include= new IncludeToBuildpathAction(site);
		fActions.add(include);

		final EditFilterAction editFilterAction= new EditFilterAction(site);
		fActions.add(editFilterAction);

		final EditOutputFolderAction editOutput= new EditOutputFolderAction(site);
		fActions.add(editOutput);

		final ConfigureBuildPathAction configure= new ConfigureBuildPathAction(site);
		fActions.add(configure);

		for (Iterator<Action> iter= fActions.iterator(); iter.hasNext();) {
			Action action= iter.next();
			if (action instanceof ISelectionChangedListener) {
				ISelectionChangedListener listener= (ISelectionChangedListener)action;
				selectionProvider.addSelectionChangedListener(listener);
				listener.selectionChanged(new SelectionChangedEvent(selectionProvider, selectionProvider.getSelection()));
			}
		}

    }

    /* (non-Javadoc)
     * Method declared in ActionGroup
     */
    @Override
	public void fillActionBars(IActionBars actionBar) {
        super.fillActionBars(actionBar);
        setGlobalActionHandlers(actionBar);
    }

    /* (non-Javadoc)
     * Method declared in ActionGroup
     */
    @Override
	public void fillContextMenu(IMenuManager menu) {
        super.fillContextMenu(menu);
        if (!canOperateOnSelection())
        	return;
        String menuText= ActionMessages.BuildPath_label;
        IMenuManager subMenu= new MenuManager(menuText, MENU_ID);
        subMenu.addMenuListener(new IMenuListener() {
        	public void menuAboutToShow(IMenuManager manager) {
        		fillViewSubMenu(manager);
        	}
        });
        subMenu.setRemoveAllWhenShown(true);
        subMenu.add(new ConfigureBuildPathAction(fSite));
        menu.appendToGroup(fGroupName, subMenu);
    }

	private void fillViewSubMenu(IMenuManager source) {
        int added= 0;
        int i=0;
        for (Iterator<Action> iter= fActions.iterator(); iter.hasNext();) {
			Action action= iter.next();
			if (action instanceof IUpdate)
				((IUpdate) action).update();

            if (i == 2)
                source.add(new Separator(GROUP_BUILDPATH));
            else if (i == 8)
                source.add(new Separator(GROUP_FILTER));
            else if (i == 10)
                source.add(new Separator(GROUP_CUSTOMIZE));
            added+= addAction(source, action);
            i++;
		}

        if (added == 0) {
        	source.add(fNoActionAvailable);
        }
    }

	/**
	 * @param actionBar the action bars to set the handler for
	 */
	private void setGlobalActionHandlers(IActionBars actionBar) {
        // TODO implement
    }

    private int addAction(IMenuManager menu, IAction action) {
        if (action != null && action.isEnabled()) {
            menu.add(action);
            return 1;
        }
        return 0;
    }

    private boolean canOperateOnSelection() {
		ISelection sel= fSelectionProvider.getSelection();
    	if (!(sel instanceof IStructuredSelection))
    		return false;
    	IStructuredSelection selection= (IStructuredSelection)sel;
    	if (selection.isEmpty())
			return false;
    	for (Iterator<?> iter= selection.iterator(); iter.hasNext();) {
			Object element= iter.next();
			if (element instanceof IWorkingSet)
				return false;
		}
    	return true;
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dispose() {
		if (fActions != null) {
			for (Iterator<Action> iter= fActions.iterator(); iter.hasNext();) {
				Action action= iter.next();
				if (action instanceof ISelectionChangedListener)
					fSelectionProvider.removeSelectionChangedListener((ISelectionChangedListener) action);
			}
			fActions.clear();
		}
		super.dispose();
	}
}

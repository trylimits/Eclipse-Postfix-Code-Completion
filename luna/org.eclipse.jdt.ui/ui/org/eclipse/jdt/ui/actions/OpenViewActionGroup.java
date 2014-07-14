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

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.actions.ContributionItemFactory;
import org.eclipse.ui.dialogs.PropertyDialogAction;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.part.Page;

import org.eclipse.jdt.ui.IContextMenuConstants;

import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.callhierarchy.OpenCallHierarchyAction;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

/**
 * Action group that adds actions to open a new JDT view part or an external
 * viewer to a context menu and the global menu bar.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class OpenViewActionGroup extends ActionGroup {

    private boolean fEditorIsOwner;
	private boolean fIsTypeHiararchyViewerOwner;
    private boolean fIsCallHiararchyViewerOwner;

	private ISelectionProvider fSelectionProvider;

	private OpenSuperImplementationAction fOpenSuperImplementation;
	private OpenImplementationAction fOpenImplementation;

	private OpenAttachedJavadocAction fOpenAttachedJavadoc;
	private OpenTypeHierarchyAction fOpenTypeHierarchy;
    private OpenCallHierarchyAction fOpenCallHierarchy;
	private PropertyDialogAction fOpenPropertiesDialog;

	private boolean fShowOpenPropertiesAction= true;
	private boolean fShowShowInMenu= true;

	/**
	 * Creates a new <code>OpenActionGroup</code>. The group requires
	 * that the selection provided by the page's selection provider is
	 * of type {@link IStructuredSelection}.
	 *
	 * @param page the page that owns this action group
	 */
	public OpenViewActionGroup(Page page) {
		createSiteActions(page.getSite(), null);
	}

	/**
	 * Creates a new <code>OpenActionGroup</code>. The group requires
	 * that the selection provided by the given selection provider is
	 * of type {@link IStructuredSelection}.
	 *
	 * @param page the page that owns this action group
	 * @param selectionProvider the selection provider used instead of the
	 *  page selection provider.
	 *
	 * @since 3.2
	 */
	public OpenViewActionGroup(Page page, ISelectionProvider selectionProvider) {
		createSiteActions(page.getSite(), selectionProvider);
	}

	/**
	 * Creates a new <code>OpenActionGroup</code>. The group requires
	 * that the selection provided by the part's selection provider is
	 * of type {@link IStructuredSelection}.
	 *
	 * @param part the view part that owns this action group
	 */
	public OpenViewActionGroup(IViewPart part) {
		this(part, null);
	}

	/**
	 * Creates a new <code>OpenActionGroup</code>. The group requires
	 * that the selection provided by the given selection provider is of type
	 * {@link IStructuredSelection}.
	 *
	 * @param part the view part that owns this action group
	 * @param selectionProvider the selection provider used instead of the
	 *  page selection provider.
	 *
	 * @since 3.2
	 */
	public OpenViewActionGroup(IViewPart part, ISelectionProvider selectionProvider) {
		createSiteActions(part.getSite(), selectionProvider);
		// we do a name check here to avoid class loading.
		String partName= part.getClass().getName();
		fIsTypeHiararchyViewerOwner= "org.eclipse.jdt.internal.ui.typehierarchy.TypeHierarchyViewPart".equals(partName); //$NON-NLS-1$
		fIsCallHiararchyViewerOwner= "org.eclipse.jdt.internal.ui.callhierarchy.CallHierarchyViewPart".equals(partName); //$NON-NLS-1$
	}

	/**
	 * Creates a new <code>OpenActionGroup</code>. The group requires
	 * that the selection provided by the given selection provider is of type
	 * {@link IStructuredSelection}.
	 *
	 * @param site the site that will own the action group.
	 * @param selectionProvider the selection provider used instead of the
	 *  page selection provider.
	 *
	 * @since 3.2
	 */
	public OpenViewActionGroup(IWorkbenchSite site, ISelectionProvider selectionProvider) {
		createSiteActions(site, selectionProvider);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param part the editor part
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public OpenViewActionGroup(JavaEditor part) {
		fEditorIsOwner= true;
		fShowShowInMenu= false;
		
		fOpenImplementation= new OpenImplementationAction(part);
		fOpenImplementation.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_IMPLEMENTATION);
		part.setAction("OpenImplementation", fOpenImplementation); //$NON-NLS-1$

		fOpenSuperImplementation= new OpenSuperImplementationAction(part);
		fOpenSuperImplementation.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_SUPER_IMPLEMENTATION);
		part.setAction("OpenSuperImplementation", fOpenSuperImplementation); //$NON-NLS-1$

		fOpenAttachedJavadoc= new OpenAttachedJavadocAction(part);
		fOpenAttachedJavadoc.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_ATTACHED_JAVADOC);
		part.setAction("OpenAttachedJavadoc", fOpenAttachedJavadoc); //$NON-NLS-1$

		fOpenTypeHierarchy= new OpenTypeHierarchyAction(part);
		fOpenTypeHierarchy.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_TYPE_HIERARCHY);
		part.setAction("OpenTypeHierarchy", fOpenTypeHierarchy); //$NON-NLS-1$

		fOpenCallHierarchy= new OpenCallHierarchyAction(part);
		fOpenCallHierarchy.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_CALL_HIERARCHY);
		part.setAction("OpenCallHierarchy", fOpenCallHierarchy); //$NON-NLS-1$

		initialize(part.getEditorSite().getSelectionProvider());
	}

	/**
	 * Specifies if this action group also contains the 'Properties' action ({@link PropertyDialogAction}).
	 * By default, the action is contained in the group.
	 *
	 * @param enable If set, the 'Properties' action is part of this action group
	 * @since 3.3
	 */
	public void containsOpenPropertiesAction(boolean enable) {
		fShowOpenPropertiesAction= enable;
	}

	/**
	 * Specifies if this action group also contains the 'Show In' menu (See {@link ContributionItemFactory#VIEWS_SHOW_IN}).
	 * By default, the action is  contained in the group except for editors.
	 *
	 * @param enable If set, the 'Show In' menu is part of this action group
	 * @since 3.3
	 */
	public void containsShowInMenu(boolean enable) {
		fShowShowInMenu= enable;
	}

	private void createSiteActions(IWorkbenchSite site, ISelectionProvider specialProvider) {
		fOpenImplementation= new OpenImplementationAction(site);
		fOpenImplementation.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_IMPLEMENTATION);
		fOpenImplementation.setSpecialSelectionProvider(specialProvider);
		
		fOpenSuperImplementation= new OpenSuperImplementationAction(site);
		fOpenSuperImplementation.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_SUPER_IMPLEMENTATION);
		fOpenSuperImplementation.setSpecialSelectionProvider(specialProvider);

		fOpenAttachedJavadoc= new OpenAttachedJavadocAction(site);
		fOpenAttachedJavadoc.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_ATTACHED_JAVADOC);
		fOpenAttachedJavadoc.setSpecialSelectionProvider(specialProvider);

		fOpenTypeHierarchy= new OpenTypeHierarchyAction(site);
		fOpenTypeHierarchy.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_TYPE_HIERARCHY);
		fOpenTypeHierarchy.setSpecialSelectionProvider(specialProvider);

		fOpenCallHierarchy= new OpenCallHierarchyAction(site);
		fOpenCallHierarchy.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_CALL_HIERARCHY);
		fOpenCallHierarchy.setSpecialSelectionProvider(specialProvider);

		ISelectionProvider provider= specialProvider != null ? specialProvider : site.getSelectionProvider();

		fOpenPropertiesDialog= new PropertyDialogAction(site, provider);
		fOpenPropertiesDialog.setActionDefinitionId(IWorkbenchCommandConstants.FILE_PROPERTIES);

		initialize(provider);
	}

	private void initialize(ISelectionProvider provider) {
		fSelectionProvider= provider;
		ISelection selection= provider.getSelection();
		fOpenImplementation.update(selection);
		fOpenSuperImplementation.update(selection);
		fOpenAttachedJavadoc.update(selection);
		fOpenTypeHierarchy.update(selection);
		fOpenCallHierarchy.update(selection);
		if (!fEditorIsOwner) {
			if (fShowOpenPropertiesAction) {
				if (selection instanceof IStructuredSelection) {
					fOpenPropertiesDialog.selectionChanged((IStructuredSelection) selection);
				} else {
					fOpenPropertiesDialog.selectionChanged(selection);
				}
			}
			provider.addSelectionChangedListener(fOpenImplementation);
			provider.addSelectionChangedListener(fOpenSuperImplementation);
			provider.addSelectionChangedListener(fOpenAttachedJavadoc);
			provider.addSelectionChangedListener(fOpenTypeHierarchy);
			provider.addSelectionChangedListener(fOpenCallHierarchy);
			// no need to register the open properties dialog action since it registers itself
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
		if (!fIsTypeHiararchyViewerOwner)
			appendToGroup(menu, fOpenTypeHierarchy);
        if (!fIsCallHiararchyViewerOwner)
            appendToGroup(menu, fOpenCallHierarchy);

        if (fShowShowInMenu) {
			MenuManager showInSubMenu= new MenuManager(getShowInMenuLabel());
			IWorkbenchWindow workbenchWindow= fOpenSuperImplementation.getSite().getWorkbenchWindow();
			showInSubMenu.add(ContributionItemFactory.VIEWS_SHOW_IN.create(workbenchWindow));
			menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, showInSubMenu);
        }

		IStructuredSelection selection= getStructuredSelection();
		if (fShowOpenPropertiesAction && selection != null && fOpenPropertiesDialog.isApplicableForSelection())
			menu.appendToGroup(IContextMenuConstants.GROUP_PROPERTIES, fOpenPropertiesDialog);
	}

	private String getShowInMenuLabel() {
		String keyBinding= null;

		IBindingService bindingService= (IBindingService) PlatformUI.getWorkbench().getAdapter(IBindingService.class);
		if (bindingService != null)
			keyBinding= bindingService.getBestActiveBindingFormattedFor(IWorkbenchCommandConstants.NAVIGATE_SHOW_IN_QUICK_MENU);

		if (keyBinding == null)
			keyBinding= ""; //$NON-NLS-1$

		return ActionMessages.OpenViewActionGroup_showInAction_label + '\t' + keyBinding;
	}

	/*
	 * @see ActionGroup#dispose()
	 */
	@Override
	public void dispose() {
		fSelectionProvider.removeSelectionChangedListener(fOpenImplementation);
		fSelectionProvider.removeSelectionChangedListener(fOpenSuperImplementation);
		fSelectionProvider.removeSelectionChangedListener(fOpenAttachedJavadoc);
		fSelectionProvider.removeSelectionChangedListener(fOpenTypeHierarchy);
		fSelectionProvider.removeSelectionChangedListener(fOpenCallHierarchy);
		super.dispose();
	}

	private void setGlobalActionHandlers(IActionBars actionBars) {
		actionBars.setGlobalActionHandler(JdtActionConstants.OPEN_IMPLEMENTATION, fOpenImplementation);
		actionBars.setGlobalActionHandler(JdtActionConstants.OPEN_SUPER_IMPLEMENTATION, fOpenSuperImplementation);
		actionBars.setGlobalActionHandler(JdtActionConstants.OPEN_ATTACHED_JAVA_DOC, fOpenAttachedJavadoc);
		actionBars.setGlobalActionHandler(JdtActionConstants.OPEN_TYPE_HIERARCHY, fOpenTypeHierarchy);
		actionBars.setGlobalActionHandler(JdtActionConstants.OPEN_CALL_HIERARCHY, fOpenCallHierarchy);

		if (!fEditorIsOwner && fShowOpenPropertiesAction)
			actionBars.setGlobalActionHandler(ActionFactory.PROPERTIES.getId(), fOpenPropertiesDialog);
	}

	private void appendToGroup(IMenuManager menu, IAction action) {
		if (action.isEnabled())
			menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, action);
	}

	private IStructuredSelection getStructuredSelection() {
		ISelection selection= getContext().getSelection();
		if (selection instanceof IStructuredSelection)
			return (IStructuredSelection)selection;
		return null;
	}




}

/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Eric Rizzo - Added "Collapse All" toolbar action
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.browsing;

import java.util.Iterator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreeViewer;

import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.CollapseAllHandler;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.IShowInTargetList;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.actions.ProjectActionGroup;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.CollapseAllAction;
import org.eclipse.jdt.internal.ui.viewsupport.FilterUpdater;
import org.eclipse.jdt.internal.ui.viewsupport.ProblemTreeViewer;


public class ProjectsView extends JavaBrowsingPart {

	private FilterUpdater fFilterUpdater;
	private CollapseAllAction fCollapseAllAction;


	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.browsing.JavaBrowsingPart#createViewer(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	protected StructuredViewer createViewer(Composite parent) {
		ProblemTreeViewer result= new ProblemTreeViewer(parent, SWT.MULTI);
		fFilterUpdater= new FilterUpdater(result);
		ResourcesPlugin.getWorkspace().addResourceChangeListener(fFilterUpdater);
		return result;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.browsing.JavaBrowsingPart#dispose()
	 */
	@Override
	public void dispose() {
		if (fFilterUpdater != null)
			ResourcesPlugin.getWorkspace().removeResourceChangeListener(fFilterUpdater);
		super.dispose();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.browsing.JavaBrowsingPart#getAdapter(java.lang.Class)
	 */
	@Override
	public Object getAdapter(Class key) {
		if (key == IShowInTargetList.class) {
			return new IShowInTargetList() {
				public String[] getShowInTargetIds() {
					return new String[] { JavaUI.ID_PACKAGES, JavaPlugin.ID_RES_NAV  };
				}

			};
		}
		return super.getAdapter(key);
	}


	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.browsing.JavaBrowsingPart#createContentProvider()
	 */
	@Override
	protected IContentProvider createContentProvider() {
		return new ProjectAndSourceFolderContentProvider(this);
	}

	/**
	 * Returns the context ID for the Help system.
	 *
	 * @return	the string used as ID for the Help context
	 */
	@Override
	protected String getHelpContextId() {
		return IJavaHelpContextIds.PROJECTS_VIEW;
	}

	@Override
	protected String getLinkToEditorKey() {
		return PreferenceConstants.LINK_BROWSING_PROJECTS_TO_EDITOR;
	}


	/**
	 * Adds additional listeners to this view.
	 */
	@Override
	protected void hookViewerListeners() {
		super.hookViewerListeners();
		getViewer().addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				TreeViewer viewer= (TreeViewer)getViewer();
				Object element= ((IStructuredSelection)event.getSelection()).getFirstElement();
				if (viewer.isExpandable(element))
					viewer.setExpandedState(element, !viewer.getExpandedState(element));
			}
		});
	}

	@Override
	protected void setInitialInput() {
		IJavaElement root= JavaCore.create(JavaPlugin.getWorkspace().getRoot());
		getViewer().setInput(root);
		updateTitle();
	}

	/**
	 * Answers if the given <code>element</code> is a valid
	 * input for this part.
	 *
	 * @param 	element	the object to test
	 * @return	<true> if the given element is a valid input
	 */
	@Override
	protected boolean isValidInput(Object element) {
		return element instanceof IJavaModel;
	}

	/**
	 * Answers if the given <code>element</code> is a valid
	 * element for this part.
	 *
	 * @param 	element	the object to test
	 * @return	<true> if the given element is a valid element
	 */
	@Override
	protected boolean isValidElement(Object element) {
		return element instanceof IJavaProject || element instanceof IPackageFragmentRoot;
	}

	/**
	 * Finds the element which has to be selected in this part.
	 *
	 * @param je	the Java element which has the focus
	 * @return the element to select
	 */
	@Override
	protected IJavaElement findElementToSelect(IJavaElement je) {
		if (je == null)
			return null;

		switch (je.getElementType()) {
			case IJavaElement.JAVA_MODEL :
				return null;
			case IJavaElement.JAVA_PROJECT:
				return je;
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				if (je.getElementName().equals(IPackageFragmentRoot.DEFAULT_PACKAGEROOT_PATH))
					return je.getParent();
				else
					return je;
			default :
				return findElementToSelect(je.getParent());
		}
	}

	/*
	 * @see JavaBrowsingPart#setInput(Object)
	 */
	@Override
	protected void setInput(Object input) {
		// Don't allow to clear input for this view
		if (input != null)
			super.setInput(input);
		else
			getViewer().setSelection(null);
	}

	@Override
	protected void createActions() {
		super.createActions();
		fActionGroups.addGroup(new ProjectActionGroup(this));
		fCollapseAllAction= new CollapseAllAction((TreeViewer) getViewer());
		fCollapseAllAction.setActionDefinitionId(CollapseAllHandler.COMMAND_ID);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.browsing.JavaBrowsingPart#activateHandlers(org.eclipse.ui.handlers.IHandlerService)
	 * @since 3.4
	 */
	@Override
	protected void activateHandlers(IHandlerService handlerService) {
		super.activateHandlers(handlerService);
		handlerService.activateHandler(CollapseAllHandler.COMMAND_ID, new ActionHandler(fCollapseAllAction));
	}

	@Override
	protected void fillToolBar(IToolBarManager tbm) {
		super.fillToolBar(tbm);
		tbm.add(fCollapseAllAction);
	}

	/**
	 * Handles selection of LogicalPackage in Packages view.
	 *
	 * @see org.eclipse.ui.ISelectionListener#selectionChanged(org.eclipse.ui.IWorkbenchPart, org.eclipse.jface.viewers.ISelection)
	 * @since 2.1
	 */
	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		if (!needsToProcessSelectionChanged(part))
			return;

		if (selection instanceof IStructuredSelection) {
			IStructuredSelection sel= (IStructuredSelection)selection;
			Iterator<?> iter= sel.iterator();
			while (iter.hasNext()) {
				Object selectedElement= iter.next();
				if (selectedElement instanceof LogicalPackage) {
					selection= new StructuredSelection(((LogicalPackage)selectedElement).getJavaProject());
					break;
				}
			}
		}
		super.selectionChanged(part, selection);
	}
}

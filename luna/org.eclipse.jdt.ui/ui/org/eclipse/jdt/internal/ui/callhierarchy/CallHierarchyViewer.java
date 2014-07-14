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
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;

import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;

import org.eclipse.ui.IWorkbenchPartSite;

import org.eclipse.jdt.internal.corext.callhierarchy.CallerMethodWrapper;
import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;

import org.eclipse.jdt.internal.ui.viewsupport.ColoringLabelProvider;


class CallHierarchyViewer extends TreeViewer {

	private final CallHierarchyViewPart fPart;
	private final CallHierarchyContentProvider fContentProvider;

	private CallerMethodWrapper fConstructorToExpand;

	private TreeRoot fDummyRoot;

    /**
     * @param parent the parent composite
     * @param part the call hierarchy view part
     */
    CallHierarchyViewer(Composite parent, CallHierarchyViewPart part) {
        super(new Tree(parent, SWT.MULTI));

        fPart = part;

        getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
        setUseHashlookup(true);
        setAutoExpandLevel(2);
        fContentProvider = new CallHierarchyContentProvider(fPart);
        setContentProvider(fContentProvider);
        setLabelProvider(new ColoringLabelProvider(new CallHierarchyLabelProvider()));


        clearViewer();
    }

    void setMethodWrappers(MethodWrapper[] wrappers) {
        setInput(getTreeRoot(wrappers));

        setFocus();
        if (wrappers != null && wrappers.length > 0)
        	setSelection(new StructuredSelection(wrappers[0]), true);
		expandConstructorNode();
    }

    CallHierarchyViewPart getPart() {
        return fPart;
    }

    /**
     *
     */
    void setFocus() {
        getControl().setFocus();
    }

    boolean isInFocus() {
        return getControl().isFocusControl();
    }

    void addKeyListener(KeyListener keyListener) {
        getControl().addKeyListener(keyListener);
    }

    /**
     * Wraps the roots of a MethodWrapper tree in a dummy root in order to show
     * it in the tree.
     *
     * @param roots The visible roots of the MethodWrapper tree.
     * @return A new TreeRoot which is a dummy root above the specified root.
     */
    private TreeRoot getTreeRoot(MethodWrapper[] roots) {
		return getTreeRoot(roots, false);
	}


	/**
	 * Wraps the roots of a MethodWrapper tree in a dummy root in order to show it in the tree.
	 * 
	 * @param roots The visible roots of the MethodWrapper tree.
	 * @param addRoots <code>true</code> if the roots need to be added to the existing roots,
	 *            <code>false</code> otherwise
	 * @return a new TreeRoot which is a dummy root above the specified root
	 * @since 3.7
	 */
	TreeRoot getTreeRoot(MethodWrapper[] roots, boolean addRoots) {
		if (fDummyRoot == null || !addRoots)
			fDummyRoot= new TreeRoot(roots);
		else
			fDummyRoot.addRoots(roots);

		return fDummyRoot;
	}

    /**
     * Attaches a context menu listener to the tree
     * @param menuListener the menu listener
     * @param viewSite the view site
     * @param selectionProvider the selection provider
     */
    void initContextMenu(IMenuListener menuListener, IWorkbenchPartSite viewSite, ISelectionProvider selectionProvider) {
        MenuManager menuMgr= new MenuManager();
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(menuListener);
        Menu menu= menuMgr.createContextMenu(getTree());
        getTree().setMenu(menu);
        viewSite.registerContextMenu(menuMgr, selectionProvider);
    }

    void clearViewer() {
        setInput(TreeRoot.EMPTY_ROOT);
		fDummyRoot= null;
    }

    void cancelJobs() {
    	if (fPart == null)
    		return;
        fContentProvider.cancelJobs(fPart.getCurrentMethodWrappers());
    }

	/**
	 * {@inheritDoc}
	 * 
	 * @since 3.7
	 */
	@Override
	protected Object[] getSortedChildren(Object parentElementOrTreePath) {
		Object[] sortedChildren= super.getSortedChildren(parentElementOrTreePath);
		if (parentElementOrTreePath instanceof CallerMethodWrapper) {
			CallerMethodWrapper parentWrapper= (CallerMethodWrapper)parentElementOrTreePath;
			if (parentWrapper.getExpandWithConstructors() && sortedChildren.length == 2 && sortedChildren[0] instanceof CallerMethodWrapper) {
				setConstructorToExpand((CallerMethodWrapper)sortedChildren[0]);
			}
		}
		return sortedChildren;
	}


	/**
	 * {@inheritDoc}
	 * 
	 * @since 3.7
	 */
	@Override
	protected void handleTreeExpand(TreeEvent event) {
		super.handleTreeExpand(event);
		expandConstructorNode();
	}

	/**
	 * Sets the constructor node.
	 * 
	 * @param wrapper the constructor caller method wrapper
	 * @since 3.7
	 */
	private void setConstructorToExpand(CallerMethodWrapper wrapper) {
		fConstructorToExpand= wrapper;
		
	}

	/**
	 * Expands the constructor node when in expand with constructors mode.
	 * 
	 * @since 3.7
	 */
	void expandConstructorNode() {
		if (fConstructorToExpand != null) {
			setExpandedState(fConstructorToExpand, true);
			fConstructorToExpand= null;
		}
	}
}

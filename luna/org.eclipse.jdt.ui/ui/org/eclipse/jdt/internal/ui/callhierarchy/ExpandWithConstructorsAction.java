/*******************************************************************************
 * Copyright (c) 2009, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import java.util.Iterator;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.corext.callhierarchy.CallerMethodWrapper;
import org.eclipse.jdt.internal.corext.callhierarchy.RealCallers;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;

/**
 * The action to expand the selected member hierarchy with constructor calls.
 * 
 * @since 3.5
 */
class ExpandWithConstructorsAction extends Action {

	/**
	 * The call hierarchy view part.
	 */
	private CallHierarchyViewPart fPart;

	/**
	 * The call hierarchy viewer.
	 */
	private CallHierarchyViewer fCallHierarchyViewer;

	/**
	 * Creates the action for expanding the hierarchy with constructor calls.
	 * 
	 * @param callHierarchyViewPart the call hierarchy view part
	 * @param callHierarchyViewer the call hierarchy viewer
	 */
	public ExpandWithConstructorsAction(CallHierarchyViewPart callHierarchyViewPart, CallHierarchyViewer callHierarchyViewer) {
		super(CallHierarchyMessages.ExpandWithConstructorsAction_expandWithConstructors_text, AS_CHECK_BOX);
		fPart= callHierarchyViewPart;
		fCallHierarchyViewer= callHierarchyViewer;
		setDescription(CallHierarchyMessages.ExpandWithConstructorsAction_expandWithConstructors_description);
		setToolTipText(CallHierarchyMessages.ExpandWithConstructorsAction_expandWithConstructors_tooltip);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.CALL_HIERARCHY_EXPAND_WITH_CONSTRUCTORS_ACTION);

	}


	/*
	 * @see Action#run
	 */
	@Override
	public void run() {
		boolean isChecked= isChecked();
		fCallHierarchyViewer.cancelJobs();

		IStructuredSelection selection= (IStructuredSelection)getSelection();
		for (Iterator<?> iter= selection.iterator(); iter.hasNext();) {
			CallerMethodWrapper member= (CallerMethodWrapper)iter.next();
			member.setExpandWithConstructors(isChecked);
			if (!isChecked) { // must collapse before refresh
				fCallHierarchyViewer.setExpandedState(member, false);
			}
			fCallHierarchyViewer.refresh(member);
			if (isChecked) { // expand only after refresh
				fCallHierarchyViewer.setExpandedState(member, true);
				fCallHierarchyViewer.expandConstructorNode();
			}
		}
	}

	/**
	 * Gets the selection from the call hierarchy view part.
	 * 
	 * @return the current selection
	 */
	private ISelection getSelection() {
		return fPart.getSelection();
	}

	/**
	 * Checks whether this action can be added for the selected element in the call hierarchy.
	 * 
	 * @return <code> true</code> if the action can be added, <code>false</code> otherwise
	 */
	public boolean canActionBeAdded() {
		if (fPart.getCallMode() == CallHierarchyViewPart.CALL_MODE_CALLEES)
			return false;
		ISelection selection= getSelection();
		if (selection.isEmpty())
			return false;
		
		boolean allElementsChecked= true;
		IStructuredSelection structuredSelection= (IStructuredSelection)selection;
		CallerMethodWrapper[] wrappers= new CallerMethodWrapper[structuredSelection.size()];
		int i= 0;
		for (Iterator<?> iter= structuredSelection.iterator(); iter.hasNext(); i++) {
			Object element= iter.next();
			if (!(element instanceof CallerMethodWrapper) || element instanceof RealCallers)
				return false;
			
			wrappers[i]= (CallerMethodWrapper)element;
			if (!CallHierarchyContentProvider.canExpandWithConstructors(wrappers[i]))
				return false;

			for (int j= 0; j < i; j++) {
				CallerMethodWrapper parent= (CallerMethodWrapper)wrappers[j].getParent();
				while (parent != null) {
					if (wrappers[i] == parent) {
						return false;// disable if element is a parent of other selected elements
					}
					parent= (CallerMethodWrapper)parent.getParent();
				}
				CallerMethodWrapper parentElement= (CallerMethodWrapper)wrappers[i].getParent();
				while (parentElement != null) {
					if (parentElement == wrappers[j]) {
						return false;// disable if element is a child of other selected elements
					}
					parentElement= (CallerMethodWrapper)parentElement.getParent();
				}

			}
			CallHierarchyContentProvider.ensureDefaultExpandWithConstructors(wrappers[i]);
			if (!wrappers[i].getExpandWithConstructors()) {
				allElementsChecked= false;
			}
		}
		if (allElementsChecked) {
			setChecked(true);
		} else {
			setChecked(false);
		}
		return true;
	}
}

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

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IMember;

import org.eclipse.jdt.internal.corext.callhierarchy.CallHierarchy;
import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

class FocusOnSelectionAction extends Action {
	private CallHierarchyViewPart fPart;

	public FocusOnSelectionAction(CallHierarchyViewPart part) {
		super(CallHierarchyMessages.FocusOnSelectionAction_focusOnSelection_text);
		fPart= part;
		setDescription(CallHierarchyMessages.FocusOnSelectionAction_focusOnSelection_description);
		setToolTipText(CallHierarchyMessages.FocusOnSelectionAction_focusOnSelection_tooltip);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.CALL_HIERARCHY_FOCUS_ON_SELECTION_ACTION);
	}

	public boolean canActionBeAdded() {
		IMember[] members= getSelectedInputElements();

		if (members != null) {
			if (members.length == 1) {
				setText(Messages.format(CallHierarchyMessages.FocusOnSelectionAction_focusOn_text, BasicElementLabels.getJavaElementName(members[0].getElementName())));
			} else {
				setText(CallHierarchyMessages.FocusOnSelectionAction_focusOn_selected);
			}
			return true;

		} else {
			return false;
		}
	}

	private IMember[] getSelectedInputElements() {
		ISelection selection= getSelection();
		if (selection instanceof IStructuredSelection) {
			Object[] elements= ((IStructuredSelection) selection).toArray();
			IMember[] members= new IMember[elements.length];
			for (int i= 0; i < elements.length; i++) {
				Object element= elements[i];
				if (CallHierarchy.isPossibleInputElement(element)) {
					members[i]= (IMember) element;
				} else if (element instanceof MethodWrapper) {
					IMember wrapped= ((MethodWrapper) element).getMember();
					if (CallHierarchy.isPossibleInputElement(wrapped)) {
						members[i]= wrapped;
					} else {
						return null;
					}
				} else {
					return null;
				}
			}
			if (members.length > 0)
				return members;
		}
		return null;
	}

	/*
	 * @see Action#run
	 */
	@Override
	public void run() {
		IMember[] members= getSelectedInputElements();
		if (members != null) {
			fPart.setInputElements(members);
		}
	}

	private ISelection getSelection() {
		ISelectionProvider provider= fPart.getSite().getSelectionProvider();

		if (provider != null) {
			return provider.getSelection();
		}

		return null;
	}
}

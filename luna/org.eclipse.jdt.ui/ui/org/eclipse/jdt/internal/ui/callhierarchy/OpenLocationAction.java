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

import java.util.Iterator;

import org.eclipse.jface.util.OpenStrategy;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;

import org.eclipse.ui.IWorkbenchSite;

import org.eclipse.jdt.internal.corext.callhierarchy.CallLocation;
import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;

import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

class OpenLocationAction extends SelectionDispatchAction {
    private CallHierarchyViewPart fPart;

    public OpenLocationAction(CallHierarchyViewPart part, IWorkbenchSite site) {
        super(site);
        fPart= part;
		LocationViewer viewer= fPart.getLocationViewer();
        setText(CallHierarchyMessages.OpenLocationAction_label);
        setToolTipText(CallHierarchyMessages.OpenLocationAction_tooltip);
		setEnabled(!fPart.getSelection().isEmpty());

		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				setEnabled(!event.getSelection().isEmpty());
			}
		});
	}

    private boolean checkEnabled(IStructuredSelection selection) {
        if (selection.isEmpty()) {
            return false;
        }

        for (Iterator<?> iter = selection.iterator(); iter.hasNext();) {
            Object element = iter.next();

            if (element instanceof MethodWrapper) {
                continue;
            } else if (element instanceof CallLocation) {
                continue;
            }

            return false;
        }

        return true;
    }

    /* (non-Javadoc)
     * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#getSelection()
     */
    @Override
	public ISelection getSelection() {
        return fPart.getSelection();
    }

    /* (non-Javadoc)
     * Method declared on SelectionDispatchAction.
     */
    @Override
	public void run(IStructuredSelection selection) {
        if (!checkEnabled(selection))
            return;

        for (Iterator<?> iter= selection.iterator(); iter.hasNext();) {
	        boolean noError= CallHierarchyUI.openInEditor(iter.next(), getShell(), OpenStrategy.activateOnOpen());
	        if (! noError)
	        	return;
		}
    }
}

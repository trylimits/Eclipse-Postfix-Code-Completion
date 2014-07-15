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

import org.eclipse.jface.viewers.ISelection;

import org.eclipse.ui.IWorkbenchSite;

import org.eclipse.jdt.core.IMember;

import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;

import org.eclipse.jdt.ui.actions.OpenAction;

import org.eclipse.jdt.internal.ui.util.SelectionUtil;

/**
 * This class is used for opening the declaration of an element from the call hierarchy view.
 */
class OpenDeclarationAction extends OpenAction {
    public OpenDeclarationAction(IWorkbenchSite site) {
        super(site);
    }

    public boolean canActionBeAdded() {
        // It is safe to cast to IMember since the selection has already been converted
        IMember member = (IMember) SelectionUtil.getSingleElement(getSelection());

        if (member != null) {
            return true;
        }

        return false;
    }

    @Override
	public ISelection getSelection() {
        return CallHierarchyUI.convertSelection(getSelectionProvider().getSelection());
    }

    @Override
	public Object getElementToOpen(Object object) {
        if (object instanceof MethodWrapper) {
            return ((MethodWrapper) object).getMember();
        }
        return object;
    }
}

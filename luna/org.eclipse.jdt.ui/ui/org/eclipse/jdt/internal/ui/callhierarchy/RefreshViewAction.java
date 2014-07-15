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

import org.eclipse.jface.action.Action;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;

class RefreshViewAction extends Action {
    private CallHierarchyViewPart fPart;

	public RefreshViewAction(CallHierarchyViewPart part) {
		fPart= part;
		setText(CallHierarchyMessages.RefreshViewAction_text);
		setToolTipText(CallHierarchyMessages.RefreshViewAction_tooltip);
		JavaPluginImages.setLocalImageDescriptors(this, "refresh.gif");//$NON-NLS-1$
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.CALL_HIERARCHY_REFRESH_VIEW_ACTION);
		setEnabled(false);
	}

    /**
     * @see org.eclipse.jface.action.Action#run()
     */
    @Override
	public void run() {
        fPart.refresh();
    }
}

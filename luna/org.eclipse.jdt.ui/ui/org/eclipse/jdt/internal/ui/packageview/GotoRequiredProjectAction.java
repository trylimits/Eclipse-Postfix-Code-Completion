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
package org.eclipse.jdt.internal.ui.packageview;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;

/**
 * Goto to the referenced required project
 */
class GotoRequiredProjectAction extends Action {

	private PackageExplorerPart fPackageExplorer;

	GotoRequiredProjectAction(PackageExplorerPart part) {
		super(PackagesMessages.GotoRequiredProjectAction_label);
		setDescription(PackagesMessages.GotoRequiredProjectAction_description);
		setToolTipText(PackagesMessages.GotoRequiredProjectAction_tooltip);
		fPackageExplorer= part;
	}

	@Override
	public void run() {
		IStructuredSelection selection= (IStructuredSelection)fPackageExplorer.getSite().getSelectionProvider().getSelection();
		Object element= selection.getFirstElement();
		if (element instanceof ClassPathContainer.RequiredProjectWrapper) {
			ClassPathContainer.RequiredProjectWrapper wrapper= (ClassPathContainer.RequiredProjectWrapper) element;
			fPackageExplorer.tryToReveal(wrapper.getProject());
		}
	}
}

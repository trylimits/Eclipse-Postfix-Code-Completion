/*******************************************************************************
 * Copyright (c) 2008, 2011 Eric Rizzo, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Eric Rizzo - initial API and implementation (see Bug 210255)
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.actions;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.TreeViewer;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;


/**
 * An {@link Action} that will collapse all nodes in a given {@link TreeViewer}.
 *
 * @since 3.4
 */
public class CollapseAllAction extends Action {

	private final TreeViewer fViewer;

	public CollapseAllAction(TreeViewer viewer) {
		super(ActionMessages.CollapsAllAction_label, JavaPluginImages.DESC_ELCL_COLLAPSEALL);
		setToolTipText(ActionMessages.CollapsAllAction_tooltip);
		setDescription(ActionMessages.CollapsAllAction_description);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.COLLAPSE_ALL_ACTION);
		Assert.isNotNull(viewer);
		fViewer= viewer;
	}

	@Override
	public void run() {
		try {
			fViewer.getControl().setRedraw(false);
			fViewer.collapseAll();
		} finally {
			fViewer.getControl().setRedraw(true);
		}
	}

}

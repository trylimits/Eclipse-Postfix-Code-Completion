/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.window.Window;

/**
 * Action class to create and open the search in dialog.
 * 
 * @since 3.7
 */
public class ShowSearchInDialogAction extends Action {
	private CallHierarchyViewPart fPart;

	private SearchInDialog fSearchInDialog;

	/**
	 * Action to show the <code>SearchInDialog</code>.
	 * 
	 * @param part the call hierarchy view part
	 * @param viewer the call hierarchy viewer	
	 */
	public ShowSearchInDialogAction(CallHierarchyViewPart part, CallHierarchyViewer viewer) {
		Assert.isNotNull(part);
		Assert.isNotNull(viewer);
		fPart= part;
		fSearchInDialog= new SearchInDialog(fPart.getViewSite().getShell());
		setText(CallHierarchyMessages.ShowSearchInDialogAction_text);
	}

	/**
	 * Returns the <code>SearchInDialog</code>.
	 * 
	 * @return the <code>searchInDialog</code>
	 */
	public SearchInDialog getSearchInDialog() {
		return fSearchInDialog;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.action.Action#run()
	 */
	@Override
	public void run() {
		SearchInDialog dialog= getSearchInDialog();
		if (dialog.open() == Window.OK && dialog.isIncludeMaskChanged()) {
			fPart.setInputElements(fPart.getInputElements());
		}
	}
}

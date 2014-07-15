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

import org.eclipse.jdt.ui.IPackagesViewPart;

import org.eclipse.jdt.internal.ui.actions.AbstractToggleLinkingAction;


/**
 * This action toggles whether this package explorer links its selection to the active
 * editor.
 *
 * @since 2.1
 */
public class ToggleLinkingAction extends AbstractToggleLinkingAction {

	private IPackagesViewPart fPackageExplorerPart;

	/**
	 * Constructs a new action.
	 * @param explorer the package explorer
	 */
	public ToggleLinkingAction(IPackagesViewPart explorer) {
		setChecked(explorer.isLinkingEnabled());
		fPackageExplorerPart= explorer;
	}

	/**
	 * Runs the action.
	 */
	@Override
	public void run() {
		fPackageExplorerPart.setLinkingEnabled(isChecked());
	}

}

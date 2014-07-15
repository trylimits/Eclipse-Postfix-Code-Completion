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
package org.eclipse.jdt.internal.ui.typehierarchy;

import org.eclipse.swt.custom.BusyIndicator;

import org.eclipse.jface.action.Action;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;

/**
 * Action to show / hide inherited members in the method view
 * Depending in the action state a different label provider is installed in the viewer
 */
public class ShowInheritedMembersAction extends Action {

	private MethodsViewer fMethodsViewer;

	/**
	 * Creates the action.
	 * @param viewer the viewer
	 * @param initValue the initial state
	 */
	public ShowInheritedMembersAction(MethodsViewer viewer, boolean initValue) {
		super(TypeHierarchyMessages.ShowInheritedMembersAction_label);
		setDescription(TypeHierarchyMessages.ShowInheritedMembersAction_description);
		setToolTipText(TypeHierarchyMessages.ShowInheritedMembersAction_tooltip);

		JavaPluginImages.setLocalImageDescriptors(this, "inher_co.gif"); //$NON-NLS-1$

		fMethodsViewer= viewer;

		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.SHOW_INHERITED_ACTION);

		setChecked(initValue);
	}

	/*
	 * @see Action#actionPerformed
	 */
	@Override
	public void run() {
		BusyIndicator.showWhile(fMethodsViewer.getControl().getDisplay(), new Runnable() {
			public void run() {
				fMethodsViewer.showInheritedMethods(isChecked());
			}
		});
	}
}

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

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.Action;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.ui.ITypeHierarchyViewPart;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;

/**
 * Toggles the orientationof the layout of the type hierarchy
 */
public class ToggleOrientationAction extends Action {

	private ITypeHierarchyViewPart fView;
	private int fActionOrientation;

	public ToggleOrientationAction(ITypeHierarchyViewPart v, int orientation) {
		super("", AS_RADIO_BUTTON); //$NON-NLS-1$
		if (orientation == ITypeHierarchyViewPart.VIEW_LAYOUT_HORIZONTAL) {
			setText(TypeHierarchyMessages.ToggleOrientationAction_horizontal_label);
			setDescription(TypeHierarchyMessages.ToggleOrientationAction_horizontal_description);
			setToolTipText(TypeHierarchyMessages.ToggleOrientationAction_horizontal_tooltip);
			JavaPluginImages.setLocalImageDescriptors(this, "th_horizontal.gif"); //$NON-NLS-1$
		} else if (orientation == ITypeHierarchyViewPart.VIEW_LAYOUT_VERTICAL) {
			setText(TypeHierarchyMessages.ToggleOrientationAction_vertical_label);
			setDescription(TypeHierarchyMessages.ToggleOrientationAction_vertical_description);
			setToolTipText(TypeHierarchyMessages.ToggleOrientationAction_vertical_tooltip);
			JavaPluginImages.setLocalImageDescriptors(this, "th_vertical.gif"); //$NON-NLS-1$
		} else if (orientation == ITypeHierarchyViewPart.VIEW_LAYOUT_AUTOMATIC) {
			setText(TypeHierarchyMessages.ToggleOrientationAction_automatic_label);
			setDescription(TypeHierarchyMessages.ToggleOrientationAction_automatic_description);
			setToolTipText(TypeHierarchyMessages.ToggleOrientationAction_automatic_tooltip);
			JavaPluginImages.setLocalImageDescriptors(this, "th_automatic.gif"); //$NON-NLS-1$
		} else if (orientation == ITypeHierarchyViewPart.VIEW_LAYOUT_SINGLE) {
			setText(TypeHierarchyMessages.ToggleOrientationAction_single_label);
			setDescription(TypeHierarchyMessages.ToggleOrientationAction_single_description);
			setToolTipText(TypeHierarchyMessages.ToggleOrientationAction_single_tooltip);
			JavaPluginImages.setLocalImageDescriptors(this, "th_single.gif"); //$NON-NLS-1$
		} else {
			Assert.isTrue(false);
		}
		fView= v;
		fActionOrientation= orientation;
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.TOGGLE_ORIENTATION_ACTION);
	}

	public int getOrientation() {
		return fActionOrientation;
	}

	/*
	 * @see Action#actionPerformed
	 */
	@Override
	public void run() {
		if (isChecked()) {
			fView.setViewLayout(fActionOrientation);
		}
	}

}

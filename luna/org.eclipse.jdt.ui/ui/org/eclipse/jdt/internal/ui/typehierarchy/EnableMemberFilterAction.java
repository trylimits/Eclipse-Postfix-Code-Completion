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

import org.eclipse.jdt.ui.ITypeHierarchyViewPart;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;

/**
 * Action enable / disable member filtering
 */
public class EnableMemberFilterAction extends Action {

	private ITypeHierarchyViewPart fView;

	public EnableMemberFilterAction(ITypeHierarchyViewPart v, boolean initValue) {
		super(TypeHierarchyMessages.EnableMemberFilterAction_label);
		setDescription(TypeHierarchyMessages.EnableMemberFilterAction_description);
		setToolTipText(TypeHierarchyMessages.EnableMemberFilterAction_tooltip);

		JavaPluginImages.setLocalImageDescriptors(this, "impl_co.gif"); //$NON-NLS-1$

		fView= v;
		setChecked(initValue);

		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.ENABLE_METHODFILTER_ACTION);

	}

	/*
	 * @see Action#actionPerformed
	 */
	@Override
	public void run() {
		BusyIndicator.showWhile(fView.getSite().getShell().getDisplay(), new Runnable() {
			public void run() {
				fView.showMembersInHierarchy(isChecked());
			}
		});
	}
}

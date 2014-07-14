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

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.IDialogConstants;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.ui.ITypeHierarchyViewPart;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.dialogs.FilteredTypesSelectionDialog;

/**
 * Refocuses the type hierarchy on a type selection from a all types dialog.
 */
public class FocusOnTypeAction extends Action {

	private ITypeHierarchyViewPart fViewPart;

	public FocusOnTypeAction(ITypeHierarchyViewPart part) {
		super(TypeHierarchyMessages.FocusOnTypeAction_label);
		setDescription(TypeHierarchyMessages.FocusOnTypeAction_description);
		setToolTipText(TypeHierarchyMessages.FocusOnTypeAction_tooltip);

		fViewPart= part;
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this,	IJavaHelpContextIds.FOCUS_ON_TYPE_ACTION);
	}

	/*
	 * @see Action#run
	 */
	@Override
	public void run() {
		Shell parent= fViewPart.getSite().getShell();
		FilteredTypesSelectionDialog dialog= new FilteredTypesSelectionDialog(parent, false,
			PlatformUI.getWorkbench().getProgressService(),
			SearchEngine.createWorkspaceScope(), IJavaSearchConstants.TYPE);

		dialog.setTitle(TypeHierarchyMessages.FocusOnTypeAction_dialog_title);
		dialog.setMessage(TypeHierarchyMessages.FocusOnTypeAction_dialog_message);
		if (dialog.open() != IDialogConstants.OK_ID) {
			return;
		}

		Object[] types= dialog.getResult();
		if (types != null && types.length > 0) {
			IType type= (IType)types[0];
			fViewPart.setInputElement(type);
		}
	}
}

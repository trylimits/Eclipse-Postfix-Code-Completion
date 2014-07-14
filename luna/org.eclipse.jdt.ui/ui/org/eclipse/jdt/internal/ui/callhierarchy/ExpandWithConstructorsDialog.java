/*******************************************************************************
 * Copyright (c) 2009, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.TrayDialog;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;

/**
 * Configuration dialog for default "Expand with Constructors" behavior.
 * 
 * @since 3.5
 */
class ExpandWithConstructorsDialog extends TrayDialog {

	private Control fConfigurationBlockControl;
	private ExpandWithConstructorsConfigurationBlock fConfigurationBlock;

	protected ExpandWithConstructorsDialog(Shell parentShell) {
		super(parentShell);
	}
	
	/*
	 * @see org.eclipse.jface.dialogs.Dialog#isResizable()
	 */
	@Override
	protected boolean isResizable() {
		return true;
	}
	
	/*
	 * @see org.eclipse.jface.dialogs.StatusDialog#configureShell(org.eclipse.swt.widgets.Shell)
	 */
	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText(CallHierarchyMessages.ExpandWithConstructorsDialog_title);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell, IJavaHelpContextIds.CALL_HIERARCHY_EXPAND_WITH_CONSTRUCTORS_DIALOG);
	}

	/*
	 * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	protected Control createDialogArea(Composite composite) {
		fConfigurationBlock= new ExpandWithConstructorsConfigurationBlock(new IStatusChangeListener() {
			public void statusChanged(IStatus status) {
				//Do nothing

			}
		}, null);
		GridData data= new GridData(GridData.FILL, GridData.FILL, true, true);
		fConfigurationBlockControl= fConfigurationBlock.createContents(composite);
		fConfigurationBlockControl.setLayoutData(data);

		Dialog.applyDialogFont(composite);
		return composite;
	}

	/*
	 * @see org.eclipse.jface.dialogs.Dialog#okPressed()
	 */
	@Override
	protected void okPressed() {
		fConfigurationBlock.performOk();
		super.okPressed();
	}
}

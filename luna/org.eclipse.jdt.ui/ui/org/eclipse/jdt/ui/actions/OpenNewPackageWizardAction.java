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

package org.eclipse.jdt.ui.actions;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.INewWizard;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.ui.wizards.NewPackageWizardPage;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.wizards.NewPackageCreationWizard;

/**
 * <p>Action that opens the new package wizard. The action initializes the wizard with the
 * selection as configured by {@link #setSelection(IStructuredSelection)} or the selection of
 * the active workbench window.</p>
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 3.2
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class OpenNewPackageWizardAction extends AbstractOpenWizardAction {

	private NewPackageWizardPage fPage;

	/**
	 * Creates an instance of the <code>OpenNewPackageWizardAction</code>.
	 */
	public OpenNewPackageWizardAction() {
		setText(ActionMessages.OpenNewPackageWizardAction_text);
		setDescription(ActionMessages.OpenNewPackageWizardAction_description);
		setToolTipText(ActionMessages.OpenNewPackageWizardAction_tooltip);
		setImageDescriptor(JavaPluginImages.DESC_WIZBAN_NEWPACK);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.OPEN_PACKAGE_WIZARD_ACTION);
	}

	/**
	 * Sets a page to be used by the wizard or <code>null</code> to use a page initialized with values
	 * from the current selection (see {@link #getSelection()} and {@link #setSelection(IStructuredSelection)}).
	 * @param page the page to use or <code>null</code>
	 *
	 * @since 3.4
	 */
	public void setConfiguredWizardPage(NewPackageWizardPage page) {
		fPage= page;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.actions.AbstractOpenWizardAction#createWizard()
	 */
	@Override
	protected final INewWizard createWizard() throws CoreException {
		return new NewPackageCreationWizard(fPage);
	}
}

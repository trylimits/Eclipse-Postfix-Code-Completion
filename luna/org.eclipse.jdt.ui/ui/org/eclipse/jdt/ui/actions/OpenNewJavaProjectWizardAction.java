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

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.INewWizard;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.ui.wizards.NewJavaProjectWizardPageOne;
import org.eclipse.jdt.ui.wizards.NewJavaProjectWizardPageTwo;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.wizards.JavaProjectWizard;

/**
 * <p>Action that opens the new Java project wizard. The action initializes the wizard with the
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
public class OpenNewJavaProjectWizardAction extends AbstractOpenWizardAction {

	private NewJavaProjectWizardPageOne fPageOne;
	private NewJavaProjectWizardPageTwo fPageTwo;

	/**
	 * Creates an instance of the <code>OpenNewJavaProjectWizardAction</code>.
	 */
	public OpenNewJavaProjectWizardAction() {
		setText(ActionMessages.OpenNewJavaProjectWizardAction_text);
		setDescription(ActionMessages.OpenNewJavaProjectWizardAction_description);
		setToolTipText(ActionMessages.OpenNewJavaProjectWizardAction_tooltip);
		setImageDescriptor(JavaPluginImages.DESC_WIZBAN_NEWJPRJ);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.OPEN_PROJECT_WIZARD_ACTION);
		setShell(JavaPlugin.getActiveWorkbenchShell());

		fPageOne= null;
		fPageTwo= null;
	}

	/**
	 * Sets the configure wizard pages to be used by the wizard.
	 *
	 * @param pageOne the {@link NewJavaProjectWizardPageOne} page to use or <code>null</code> to use the default page.
	 * @param pageTwo the {@link NewJavaProjectWizardPageTwo} page to use or <code>null</code> to use the default page.
	 *
	 * @since 3.4
	 */
	public void setConfiguredWizardPages(NewJavaProjectWizardPageOne pageOne, NewJavaProjectWizardPageTwo pageTwo) {
		fPageOne= pageOne;
		fPageTwo= pageTwo;
	}


	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.actions.AbstractOpenWizardAction#createWizard()
	 */
	@Override
	protected final INewWizard createWizard() throws CoreException {
		return new JavaProjectWizard(fPageOne, fPageTwo);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.actions.AbstractOpenWizardAction#doCreateProjectFirstOnEmptyWorkspace(Shell)
	 */
	@Override
	protected boolean doCreateProjectFirstOnEmptyWorkspace(Shell shell) {
		return true; // can work on an empty workspace
	}
}

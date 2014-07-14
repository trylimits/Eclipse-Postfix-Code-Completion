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

import org.eclipse.jdt.ui.wizards.NewAnnotationWizardPage;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.wizards.NewAnnotationCreationWizard;

/**
* <p>Action that opens the new annotation wizard.The action initialized the wizard with either the selection
 * as configured by {@link #setSelection(IStructuredSelection)} or takes a preconfigured
 * new annotation wizard page, see {@link #setConfiguredWizardPage(NewAnnotationWizardPage)}.
 * </p>
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 3.2
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class OpenNewAnnotationWizardAction extends AbstractOpenWizardAction {

	private NewAnnotationWizardPage fPage;
	private boolean fOpenEditorOnFinish;

	/**
	 * Creates an instance of the <code>OpenNewAnnotationWizardAction</code>.
	 */
	public OpenNewAnnotationWizardAction() {
		setText(ActionMessages.OpenNewAnnotationWizardAction_text);
		setDescription(ActionMessages.OpenNewAnnotationWizardAction_description);
		setToolTipText(ActionMessages.OpenNewAnnotationWizardAction_tooltip);
		setImageDescriptor(JavaPluginImages.DESC_WIZBAN_NEWANNOT);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.OPEN_ANNOTATION_WIZARD_ACTION);
		setShell(JavaPlugin.getActiveWorkbenchShell());

		fPage= null;
		fOpenEditorOnFinish= true;
	}

	/**
	 * Sets a page to be used by the wizard or <code>null</code> to use a page initialized with values
	 * from the current selection (see {@link #getSelection()} and {@link #setSelection(IStructuredSelection)}).
	 * @param page the page to use or <code>null</code>
	 */
	public void setConfiguredWizardPage(NewAnnotationWizardPage page) {
		fPage= page;
	}

	/**
	 * Specifies if the wizard will open the created type with the default editor. The default behaviour is to open
	 * an editor.
	 *
	 * @param openEditorOnFinish if set, the wizard will open the created type with the default editor
	 *
	 * @since 3.3
	 */
	public void setOpenEditorOnFinish(boolean openEditorOnFinish) {
		fOpenEditorOnFinish= openEditorOnFinish;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.actions.AbstractOpenWizardAction#createWizard()
	 */
	@Override
	protected final INewWizard createWizard() throws CoreException {
		return new NewAnnotationCreationWizard(fPage, fOpenEditorOnFinish);
	}
}

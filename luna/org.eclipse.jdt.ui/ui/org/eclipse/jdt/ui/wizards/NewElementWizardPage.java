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
package org.eclipse.jdt.ui.wizards;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.wizard.WizardPage;

import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;

/**
 * Base class for wizard page responsible to create Java elements. The class
 * provides API to update the wizard's status line and OK button according to
 * the value of a <code>IStatus</code> object.
 *
 * <p>
 * Clients may subclass.
 * </p>
 *
 * @since 2.0
 */
public abstract class NewElementWizardPage extends WizardPage {

	private IStatus fCurrStatus;

	private boolean fPageVisible;

	/**
	 * Creates a <code>NewElementWizardPage</code>.
	 *
	 * @param name the wizard page's name
	 */
	public NewElementWizardPage(String name) {
		super(name);
		fPageVisible= false;
		fCurrStatus=  new StatusInfo();
	}

	// ---- WizardPage ----------------

	/*
	 * @see WizardPage#becomesVisible
	 */
	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		fPageVisible= visible;
		// policy: wizards are not allowed to come up with an error message
		if (visible && fCurrStatus.matches(IStatus.ERROR)) {
			StatusInfo status= new StatusInfo();
			status.setError("");  //$NON-NLS-1$
			fCurrStatus= status;
		}
		updateStatus(fCurrStatus);
	}

	/**
	 * Updates the status line and the OK button according to the given status
	 *
	 * @param status status to apply
	 */
	protected void updateStatus(IStatus status) {
		fCurrStatus= status;
		setPageComplete(!status.matches(IStatus.ERROR));
		if (fPageVisible) {
			StatusUtil.applyToStatusLine(this, status);
		}
	}

	/**
	 * Updates the status line and the OK button according to the status evaluate from
	 * an array of status. The most severe error is taken.  In case that two status with
	 * the same severity exists, the status with lower index is taken.
	 *
	 * @param status the array of status
	 */
	protected void updateStatus(IStatus[] status) {
		updateStatus(StatusUtil.getMostSevere(status));
	}

}

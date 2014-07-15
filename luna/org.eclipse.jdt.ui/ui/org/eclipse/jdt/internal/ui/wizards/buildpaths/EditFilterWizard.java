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
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import org.eclipse.core.runtime.IPath;

import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;

public class EditFilterWizard extends BuildPathWizard {

	private SetFilterWizardPage fFilterPage;
	private final IPath[] fOrginalInclusion, fOriginalExclusion;

	public EditFilterWizard(CPListElement[] existingEntries, CPListElement newEntry, IPath outputLocation) {
		super(existingEntries, newEntry, outputLocation, NewWizardMessages.ExclusionInclusionDialog_title, null);

		IPath[] inc= (IPath[])newEntry.getAttribute(CPListElement.INCLUSION);
		fOrginalInclusion= new IPath[inc.length];
		System.arraycopy(inc, 0, fOrginalInclusion, 0, inc.length);

		IPath[] excl= (IPath[])newEntry.getAttribute(CPListElement.EXCLUSION);
		fOriginalExclusion= new IPath[excl.length];
		System.arraycopy(excl, 0, fOriginalExclusion, 0, excl.length);
	}

	/*
	 * @see Wizard#addPages
	 */
	@Override
	public void addPages() {
		super.addPages();

		fFilterPage= new SetFilterWizardPage(getEntryToEdit(), getExistingEntries(), getOutputLocation());
		addPage(fFilterPage);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.IWizard#performFinish()
	 */
	@Override
	public boolean performFinish() {
		CPListElement entryToEdit= getEntryToEdit();
		entryToEdit.setAttribute(CPListElement.INCLUSION, fFilterPage.getInclusionPattern());
		entryToEdit.setAttribute(CPListElement.EXCLUSION, fFilterPage.getExclusionPattern());

		return super.performFinish();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void cancel() {
		CPListElement entryToEdit= getEntryToEdit();
		entryToEdit.setAttribute(CPListElement.INCLUSION, fOrginalInclusion);
		entryToEdit.setAttribute(CPListElement.EXCLUSION, fOriginalExclusion);
	}
}

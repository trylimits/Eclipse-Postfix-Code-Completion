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

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.ui.wizards.IClasspathContainerPage;
import org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension;
import org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension2;

import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;


/**
  */
public class ClasspathContainerWizard extends Wizard {

	private final ClasspathContainerDescriptor fPageDesc;
	private final IClasspathEntry fEntryToEdit;

	private IClasspathEntry[] fNewEntries;
	private IClasspathContainerPage fContainerPage;
	private final IJavaProject fCurrProject;
	private final IClasspathEntry[] fCurrClasspath;

	private ClasspathContainerSelectionPage fSelectionWizardPage;

	/**
	 * Constructor for ClasspathContainerWizard.
	 * @param entryToEdit entry to edit
	 * @param currProject current project
	 * @param currEntries entries currently in classpath
	 */
	public ClasspathContainerWizard(IClasspathEntry entryToEdit, IJavaProject currProject, IClasspathEntry[] currEntries) {
		this(entryToEdit, null, currProject, currEntries);
	}

	/**
	 * Constructor for ClasspathContainerWizard.
	 * @param pageDesc page description
	 * @param currProject current project
	 * @param currEntries entries currently in classpath
	 */
	public ClasspathContainerWizard(ClasspathContainerDescriptor pageDesc, IJavaProject currProject, IClasspathEntry[] currEntries) {
		this(null, pageDesc, currProject, currEntries);
	}

	private ClasspathContainerWizard(IClasspathEntry entryToEdit, ClasspathContainerDescriptor pageDesc, IJavaProject currProject, IClasspathEntry[] currEntries) {
		fEntryToEdit= entryToEdit;
		fPageDesc= pageDesc;
		fNewEntries= null;

		fCurrProject= currProject;
		fCurrClasspath= currEntries;

		String title;
		if (entryToEdit == null) {
			title= NewWizardMessages.ClasspathContainerWizard_new_title;
		} else {
			title= NewWizardMessages.ClasspathContainerWizard_edit_title;
		}
		setWindowTitle(title);
	}

	public IClasspathEntry[] getNewEntries() {
		return fNewEntries;
	}

	/* (non-Javadoc)
	 * @see IWizard#performFinish()
	 */
	@Override
	public boolean performFinish() {
		if (fContainerPage != null) {
			if (fContainerPage.finish()) {
				if (fEntryToEdit == null && fContainerPage instanceof IClasspathContainerPageExtension2) {
					fNewEntries= ((IClasspathContainerPageExtension2) fContainerPage).getNewContainers();
				} else {
					IClasspathEntry entry= fContainerPage.getSelection();
					fNewEntries= (entry != null) ? new IClasspathEntry[] { entry } : null;
				}
				return true;
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see IWizard#addPages()
	 */
	@Override
	public void addPages() {
		if (fPageDesc != null) {
			fContainerPage= getContainerPage(fPageDesc);
			addPage(fContainerPage);
		} else if (fEntryToEdit == null) { // new entry: show selection page as first page
			ClasspathContainerDescriptor[] containers= ClasspathContainerDescriptor.getDescriptors();

			fSelectionWizardPage= new ClasspathContainerSelectionPage(containers);
			addPage(fSelectionWizardPage);

			// add as dummy, will not be shown
			fContainerPage= new ClasspathContainerDefaultPage();
			addPage(fContainerPage);
		} else { // fPageDesc == null && fEntryToEdit != null
			ClasspathContainerDescriptor[] containers= ClasspathContainerDescriptor.getDescriptors();
			ClasspathContainerDescriptor descriptor= findDescriptorPage(containers, fEntryToEdit);
			fContainerPage= getContainerPage(descriptor);
			addPage(fContainerPage);
		}
		super.addPages();
	}

	private IClasspathContainerPage getContainerPage(ClasspathContainerDescriptor pageDesc) {
		IClasspathContainerPage containerPage= null;
		if (pageDesc != null) {
			IClasspathContainerPage page= pageDesc.getPage();
			if (page != null) {
				return page; // if page is already created, avoid double initialization
			}
			try {
				containerPage= pageDesc.createPage();
			} catch (CoreException e) {
				handlePageCreationFailed(e);
			}
		}

		if (containerPage == null)	{
			containerPage= new ClasspathContainerDefaultPage();
			if (pageDesc != null) {
				pageDesc.setPage(containerPage); // avoid creation next time
			}
		}

		if (containerPage instanceof IClasspathContainerPageExtension) {
			((IClasspathContainerPageExtension) containerPage).initialize(fCurrProject, fCurrClasspath);
		}

		containerPage.setSelection(fEntryToEdit);
		containerPage.setWizard(this);
		return containerPage;
	}

	/* (non-Javadoc)
	 * @see IWizard#getNextPage(IWizardPage)
	 */
	@Override
	public IWizardPage getNextPage(IWizardPage page) {
		if (page == fSelectionWizardPage) {

			ClasspathContainerDescriptor selected= fSelectionWizardPage.getSelected();
			fContainerPage= getContainerPage(selected);

			return fContainerPage;
		}
		return super.getNextPage(page);
	}

	private void handlePageCreationFailed(CoreException e) {
		String title= NewWizardMessages.ClasspathContainerWizard_pagecreationerror_title;
		String message= NewWizardMessages.ClasspathContainerWizard_pagecreationerror_message;
		ExceptionHandler.handle(e, getShell(), title, message);
	}


	private ClasspathContainerDescriptor findDescriptorPage(ClasspathContainerDescriptor[] containers, IClasspathEntry entry) {
		for (int i = 0; i < containers.length; i++) {
			if (containers[i].canEdit(entry)) {
				return containers[i];
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#dispose()
	 */
	@Override
	public void dispose() {
		if (fSelectionWizardPage != null) {
			ClasspathContainerDescriptor[] descriptors= fSelectionWizardPage.getContainers();
			for (int i= 0; i < descriptors.length; i++) {
				descriptors[i].dispose();
			}
		}
		super.dispose();
	}

	/* (non-Javadoc)
	 * @see IWizard#canFinish()
	 */
	@Override
	public boolean canFinish() {
		if (fSelectionWizardPage != null) {
			if (!fContainerPage.isPageComplete()) {
				return false;
			}
		}
		if (fContainerPage != null) {
			return fContainerPage.isPageComplete();
		}
		return false;
	}

	public static int openWizard(Shell shell, ClasspathContainerWizard wizard) {
		WizardDialog dialog= new WizardDialog(shell, wizard);
		PixelConverter converter= new PixelConverter(JFaceResources.getDialogFont());
		dialog.setMinimumPageSize(converter.convertWidthInCharsToPixels(70), converter.convertHeightInCharsToPixels(20));
		dialog.create();
		return dialog.open();
	}

}

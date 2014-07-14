/*******************************************************************************
 * Copyright (c) 2000, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring.reorg;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;

import org.eclipse.ui.IWorkbenchWizard;

import org.eclipse.jdt.internal.corext.refactoring.reorg.ICreateTargetQueries;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ICreateTargetQuery;

import org.eclipse.jdt.ui.wizards.NewPackageWizardPage;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.wizards.NewPackageCreationWizard;


public class CreateTargetQueries implements ICreateTargetQueries {

	private final Wizard fWizard;
	private final Shell fShell;

	public CreateTargetQueries(Wizard wizard) {
		fWizard= wizard;
		fShell= null;
	}

	public CreateTargetQueries(Shell shell) {
		fShell = shell;
		fWizard= null;
	}

	private Shell getShell() {
		Assert.isTrue(fWizard == null || fShell == null);
		if (fWizard != null)
			return fWizard.getContainer().getShell();
		else if (fShell != null)
			return fShell;
		else
			return JavaPlugin.getActiveWorkbenchShell();
	}

	public ICreateTargetQuery createNewPackageQuery() {
		return new ICreateTargetQuery() {
			public Object getCreatedTarget(Object selection) {
				IWorkbenchWizard packageCreationWizard= new NewPackageCreationWizard();

				IWizardPage[] pages= openNewElementWizard(packageCreationWizard, getShell(), selection);

				NewPackageWizardPage page= (NewPackageWizardPage) pages[0];
				return page.getNewPackageFragment();
			}

			public String getNewButtonLabel() {
				return ReorgMessages.ReorgMoveWizard_newPackage;
			}
		};
	}

	private IWizardPage[] openNewElementWizard(IWorkbenchWizard wizard, Shell shell, Object selection) {
		wizard.init(JavaPlugin.getDefault().getWorkbench(), new StructuredSelection(selection));

		WizardDialog dialog= new WizardDialog(shell, wizard);
		PixelConverter converter= new PixelConverter(JFaceResources.getDialogFont());
		dialog.setMinimumPageSize(converter.convertWidthInCharsToPixels(70), converter.convertHeightInCharsToPixels(20));
		dialog.create();
		dialog.open();
		IWizardPage[] pages= wizard.getPages();
		return pages;
	}
}

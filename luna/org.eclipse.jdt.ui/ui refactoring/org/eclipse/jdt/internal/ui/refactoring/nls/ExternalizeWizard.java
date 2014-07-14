/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring.nls;

import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

import org.eclipse.jdt.core.ICompilationUnit;

import org.eclipse.jdt.internal.corext.refactoring.nls.NLSRefactoring;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.refactoring.RefactoringSaveHelper;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.refactoring.actions.RefactoringStarter;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

/**
 * good citizen problems - wizard is only valid after constructor (when the pages toggle
 * some values and force an validate the validate can't get a wizard)
 */
public class ExternalizeWizard extends RefactoringWizard {

	public ExternalizeWizard(NLSRefactoring refactoring) {
		super(refactoring,CHECK_INITIAL_CONDITIONS_ON_OPEN | WIZARD_BASED_USER_INTERFACE);
		setDefaultPageTitle(Messages.format(NLSUIMessages.ExternalizeWizardPage_title, BasicElementLabels.getFileName(refactoring.getCu())));
		setWindowTitle(NLSUIMessages.ExternalizeWizard_name);
		setDefaultPageImageDescriptor(JavaPluginImages.DESC_WIZBAN_EXTERNALIZE_STRINGS);
	}

	/**
	 * @see RefactoringWizard#addUserInputPages()
	 */
	@Override
	protected void addUserInputPages() {

		NLSRefactoring nlsRefac= (NLSRefactoring) getRefactoring();
		ExternalizeWizardPage page= new ExternalizeWizardPage(nlsRefac);
		page.setMessage(NLSUIMessages.ExternalizeWizard_select);
		addPage(page);

		/*ExternalizeWizardPage2 page2= new ExternalizeWizardPage2(nlsRefac);
		 page2.setMessage(NLSUIMessages.getString("wizard.select_values")); //$NON-NLS-1$
		 addPage(page2);*/
	}

	@Override
	public boolean canFinish() {
		IWizardPage page= getContainer().getCurrentPage();
		return super.canFinish() && !(page instanceof ExternalizeWizardPage);
	}

	public static void open(final ICompilationUnit unit, final Shell shell) {
		if (unit == null || !unit.exists()) {
			return;
		}
		Display display= shell != null ? shell.getDisplay() : Display.getCurrent();
		BusyIndicator.showWhile(display, new Runnable() {
			public void run() {
				NLSRefactoring refactoring= null;
				try {
					refactoring= NLSRefactoring.create(unit);
				} catch (IllegalArgumentException e) {
					// Loading a properties file can throw an IAE due to malformed Unicode escape sequence, see Properties#load for details.
					IStatus status= new Status(IStatus.ERROR, JavaPlugin.getPluginId(), e.getLocalizedMessage());
					ExceptionHandler.handle(status,
							NLSUIMessages.ExternalizeWizard_name,
							NLSUIMessages.ExternalizeWizard_error_message);
				}
				if (refactoring != null)
					new RefactoringStarter().activate(new ExternalizeWizard(refactoring), shell, ActionMessages.ExternalizeStringsAction_dialog_title, RefactoringSaveHelper.SAVE_REFACTORING);
			}
		});
	}
}

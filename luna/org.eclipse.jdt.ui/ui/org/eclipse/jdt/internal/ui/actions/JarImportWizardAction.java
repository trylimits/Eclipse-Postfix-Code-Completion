/*******************************************************************************
 * Copyright (c) 2005, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.wizard.WizardDialog;

import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.jarimport.JarImportWizard;

/**
 * Combined action and action delegate for the jar import action.
 *
 * @since 3.2
 */
public class JarImportWizardAction extends Action implements IObjectActionDelegate, ISelectionChangedListener {

	/** The wizard height */
	public static final int SIZING_WIZARD_HEIGHT= 520;

	/** The wizard width */
	public static final int SIZING_WIZARD_WIDTH= 470;

	/** The structured selection, or <code>null</code> */
	private IStructuredSelection fSelection= null;

	/** The active workbench part, or <code>null</code> */
	private IWorkbenchPart fWorkbenchPart= null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run() {
		run(this);
	}

	/**
	 * {@inheritDoc}
	 */
	public void run(final IAction action) {
		if (fWorkbenchPart == null || fSelection == null)
			return;
		final IImportWizard wizard= new JarImportWizard(false);
		final IWorkbenchWindow window= fWorkbenchPart.getSite().getWorkbenchWindow();
		wizard.init(window.getWorkbench(), fSelection);
		final WizardDialog dialog= new WizardDialog(window.getShell(), wizard);
		dialog.create();
		dialog.getShell().setSize(Math.max(SIZING_WIZARD_WIDTH, dialog.getShell().getSize().x), SIZING_WIZARD_HEIGHT);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(dialog.getShell(), IJavaHelpContextIds.JARIMPORT_WIZARD_PAGE);
		dialog.open();
	}

	/**
	 * {@inheritDoc}
	 */
	public void selectionChanged(final IAction action, final ISelection selection) {
		fSelection= null;
		if (selection instanceof IStructuredSelection) {
			final IStructuredSelection structured= (IStructuredSelection) selection;
			if (structured.size() == 1) {
				final Object element= structured.getFirstElement();
				if (element instanceof IPackageFragmentRoot) {
					final IPackageFragmentRoot root= (IPackageFragmentRoot) element;
					try {
						final IClasspathEntry entry= root.getRawClasspathEntry();
						if (JarImportWizard.isValidClassPathEntry(entry) && JarImportWizard.isValidJavaProject(root.getJavaProject())
								&& root.getResolvedClasspathEntry().getReferencingEntry() == null) {
							fSelection= structured;
						}
					} catch (JavaModelException exception) {
						JavaPlugin.log(exception);
					}
				}
			}
		}
		action.setEnabled(fSelection != null);
	}

	/**
	 * {@inheritDoc}
	 */
	public void selectionChanged(final SelectionChangedEvent event) {
		selectionChanged(this, event.getSelection());
	}

	/**
	 * {@inheritDoc}
	 */
	public void setActivePart(final IAction action, final IWorkbenchPart part) {
		fWorkbenchPart= part;
	}
}

/*******************************************************************************
 * Copyright (c) 2006, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.operation.IRunnableWithProgress;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.corext.refactoring.structure.ExtractSupertypeProcessor;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Wizard page to select methods to be deleted after extract supertype.
 *
 * @since 3.2
 */
public class ExtractSupertypeMethodPage extends PullUpMethodPage {


	public ExtractSupertypeMethodPage(ExtractSupertypeProcessor processor) {
		super(processor);
	}

	/**
	 * Returns the refactoring processor.
	 *
	 * @return the refactoring processor
	 */
	private ExtractSupertypeProcessor getProcessor() {
		return (ExtractSupertypeProcessor) getPullUpRefactoringProcessor();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.EXTRACT_SUPERTYPE_WIZARD_PAGE);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setVisible(final boolean visible) {
		if (visible) {
			final ExtractSupertypeProcessor processor= getProcessor();
			processor.resetChanges();
			try {
				getWizard().getContainer().run(false, false, new IRunnableWithProgress() {

					public void run(final IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						processor.createWorkingCopyLayer(monitor);
					}
				});
			} catch (InvocationTargetException exception) {
				JavaPlugin.log(exception);
			} catch (InterruptedException exception) {
				// Does not happen
			}
		}
		super.setVisible(visible);
	}
}

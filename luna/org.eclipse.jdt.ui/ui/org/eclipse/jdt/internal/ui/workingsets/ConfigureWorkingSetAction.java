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
package org.eclipse.jdt.internal.ui.workingsets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.IDialogConstants;

import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;


public class ConfigureWorkingSetAction extends Action {

	private final IWorkbenchPartSite fSite;
	private WorkingSetModel fWorkingSetModel;

	public ConfigureWorkingSetAction(IWorkbenchPartSite site) {
		super(WorkingSetMessages.ConfigureWorkingSetAction_label);
		fSite= site;
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.CONFIGURE_WORKING_SETS_ACTION);
	}

	public void setWorkingSetModel(WorkingSetModel model) {
		fWorkingSetModel= model;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run() {
		List<IWorkingSet> workingSets= new ArrayList<IWorkingSet>(Arrays.asList(fWorkingSetModel.getAllWorkingSets()));
		IWorkingSet[] activeWorkingSets;
		if (fWorkingSetModel.needsConfiguration()) {
			activeWorkingSets= fWorkingSetModel.getAllWorkingSets();
		} else {
			activeWorkingSets= fWorkingSetModel.getActiveWorkingSets();
		}
		boolean isSortingEnabled= fWorkingSetModel.isSortingEnabled();
		WorkingSetConfigurationDialog dialog= new WorkingSetConfigurationDialog(fSite.getShell(), workingSets.toArray(new IWorkingSet[workingSets.size()]), isSortingEnabled);
		dialog.setSelection(activeWorkingSets);
		if (dialog.open() == IDialogConstants.OK_ID) {
			isSortingEnabled= dialog.isSortingEnabled();
			fWorkingSetModel.setWorkingSets(dialog.getAllWorkingSets(), isSortingEnabled, dialog.getSelection());
		}
	}
}

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
package org.eclipse.jdt.internal.ui.preferences;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.core.resources.IProject;

import org.eclipse.jface.preference.IPreferencePageContainer;

import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;
import org.eclipse.ui.preferences.IWorkingCopyManager;
import org.eclipse.ui.preferences.WorkingCopyManager;

import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileConfigurationBlock;

public abstract class ProfilePreferencePage extends PropertyAndPreferencePage {

	private ProfileConfigurationBlock fConfigurationBlock;

	public ProfilePreferencePage() {
		super();
	}

	protected abstract ProfileConfigurationBlock createConfigurationBlock(PreferencesAccess access);

	@Override
	public void createControl(Composite parent) {
    	IPreferencePageContainer container= getContainer();
    	IWorkingCopyManager workingCopyManager;
    	if (container instanceof IWorkbenchPreferenceContainer) {
    		workingCopyManager= ((IWorkbenchPreferenceContainer) container).getWorkingCopyManager();
    	} else {
    		workingCopyManager= new WorkingCopyManager(); // non shared
    	}
    	PreferencesAccess access= PreferencesAccess.getWorkingCopyPreferences(workingCopyManager);
    	fConfigurationBlock= createConfigurationBlock(access);

    	super.createControl(parent);
    }

	@Override
	protected Control createPreferenceContent(Composite composite) {
    	return fConfigurationBlock.createContents(composite);
    }

	@Override
	protected boolean hasProjectSpecificOptions(IProject project) {
    	return fConfigurationBlock.hasProjectSpecificOptions(project);
    }

	@Override
	protected void enableProjectSpecificSettings(boolean useProjectSpecificSettings) {
    	super.enableProjectSpecificSettings(useProjectSpecificSettings);
    	if (fConfigurationBlock != null) {
    		fConfigurationBlock.enableProjectSpecificSettings(useProjectSpecificSettings);
    	}
    }

	@Override
	public void dispose() {
    	if (fConfigurationBlock != null) {
    		fConfigurationBlock.dispose();
    	}
    	super.dispose();
    }

	@Override
	protected void performDefaults() {
    	if (fConfigurationBlock != null) {
    		fConfigurationBlock.performDefaults();
    	}
    	super.performDefaults();
    }

	@Override
	public boolean performOk() {
    	if (fConfigurationBlock != null && !fConfigurationBlock.performOk()) {
    		return false;
    	}
    	return super.performOk();
    }

	@Override
	public void performApply() {
    	if (fConfigurationBlock != null) {
    		fConfigurationBlock.performApply();
    	}
    	super.performApply();
    }

	@Override
	public void setElement(IAdaptable element) {
    	super.setElement(element);
    	setDescription(null); // no description for property page
    }

}

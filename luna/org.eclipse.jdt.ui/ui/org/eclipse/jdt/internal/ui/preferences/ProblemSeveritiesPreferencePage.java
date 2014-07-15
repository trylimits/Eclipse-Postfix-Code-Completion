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

import java.util.Map;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.core.resources.IProject;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Page used to configure both workspace and project specific compiler settings
 */
public class ProblemSeveritiesPreferencePage extends PropertyAndPreferencePage {

	public static final String PREF_ID= "org.eclipse.jdt.ui.preferences.ProblemSeveritiesPreferencePage"; //$NON-NLS-1$
	public static final String PROP_ID= "org.eclipse.jdt.ui.propertyPages.ProblemSeveritiesPreferencePage"; //$NON-NLS-1$

	public static final String DATA_SELECT_OPTION_KEY= "select_option_key"; //$NON-NLS-1$
	public static final String DATA_SELECT_OPTION_QUALIFIER= "select_option_qualifier"; //$NON-NLS-1$

	/**
	 * Key for a Boolean value defining if 'use project specific settings' should be enabled or not.
	 */
	public static final String USE_PROJECT_SPECIFIC_OPTIONS= "use_project_specific_key"; //$NON-NLS-1$

	private ProblemSeveritiesConfigurationBlock fConfigurationBlock;

	public ProblemSeveritiesPreferencePage() {
		setPreferenceStore(JavaPlugin.getDefault().getPreferenceStore());
		//setDescription(PreferencesMessages.getString("ProblemSeveritiesPreferencePage.description")); //$NON-NLS-1$

		// only used when page is shown programatically
		setTitle(PreferencesMessages.ProblemSeveritiesPreferencePage_title);
	}

	/*
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public void createControl(Composite parent) {
		IWorkbenchPreferenceContainer container= (IWorkbenchPreferenceContainer) getContainer();
		fConfigurationBlock= new ProblemSeveritiesConfigurationBlock(getNewStatusChangedListener(), getProject(), container);

		super.createControl(parent);
		if (isProjectPreferencePage()) {
			PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.PROBLEM_SEVERITIES_PROPERTY_PAGE);
		} else {
			PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.PROBLEM_SEVERITIES_PREFERENCE_PAGE);
		}
	}

	@Override
	protected Control createPreferenceContent(Composite composite) {
		return fConfigurationBlock.createContents(composite);
	}

	@Override
	public Point computeSize() {
		Point size= super.computeSize();
		size.y= 10; //see bug 294763
		return size;
	}

	@Override
	protected boolean hasProjectSpecificOptions(IProject project) {
		return fConfigurationBlock.hasProjectSpecificOptions(project);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.PropertyAndPreferencePage#getPreferencePageID()
	 */
	@Override
	protected String getPreferencePageID() {
		return PREF_ID;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.PropertyAndPreferencePage#getPropertyPageID()
	 */
	@Override
	protected String getPropertyPageID() {
		return PROP_ID;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.DialogPage#dispose()
	 */
	@Override
	public void dispose() {
		if (fConfigurationBlock != null) {
			fConfigurationBlock.dispose();
		}
		super.dispose();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.PropertyAndPreferencePage#enableProjectSpecificSettings(boolean)
	 */
	@Override
	protected void enableProjectSpecificSettings(boolean useProjectSpecificSettings) {
		super.enableProjectSpecificSettings(useProjectSpecificSettings);
		if (fConfigurationBlock != null) {
			fConfigurationBlock.useProjectSpecificSettings(useProjectSpecificSettings);
		}
	}

	/*
	 * @see org.eclipse.jface.preference.IPreferencePage#performDefaults()
	 */
	@Override
	protected void performDefaults() {
		super.performDefaults();
		if (fConfigurationBlock != null) {
			fConfigurationBlock.performDefaults();
		}
	}

	/*
	 * @see org.eclipse.jface.preference.IPreferencePage#performOk()
	 */
	@Override
	public boolean performOk() {
		if (fConfigurationBlock != null && !fConfigurationBlock.performOk()) {
			return false;
		}
		return super.performOk();
	}

	/*
	 * @see org.eclipse.jface.preference.IPreferencePage#performApply()
	 */
	@Override
	public void performApply() {
		if (fConfigurationBlock != null) {
			fConfigurationBlock.performApply();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.preference.PreferencePage#applyData(java.lang.Object)
	 */
	@Override
	public void applyData(Object data) {
		super.applyData(data);
		if (data instanceof Map && fConfigurationBlock != null) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map= (Map<String, Object>) data;
			if (isProjectPreferencePage()) {
				Boolean useProjectOptions= (Boolean) map.get(USE_PROJECT_SPECIFIC_OPTIONS);
				if (useProjectOptions != null) {
					enableProjectSpecificSettings(useProjectOptions.booleanValue());
				}
			}

			Object key= map.get(DATA_SELECT_OPTION_KEY);
			Object qualifier= map.get(DATA_SELECT_OPTION_QUALIFIER);
			if (key instanceof String && qualifier instanceof String) {
				fConfigurationBlock.selectOption((String) key, (String) qualifier);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.PropertyAndPreferencePage#setElement(org.eclipse.core.runtime.IAdaptable)
	 */
	@Override
	public void setElement(IAdaptable element) {
		super.setElement(element);
		setDescription(null); // no description for property page
	}

}

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

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;



/**
 * Occurrences preference page.
 * <p>
 * Note: Must be public since it is referenced from plugin.xml
 * </p>
 *
 * @since 3.0
 */
public class OccurrencesPreferencePage extends AbstractConfigurationBlockPreferencePage {

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.AbstractConfigureationBlockPreferencePage#getHelpId()
	 */
	@Override
	protected String getHelpId() {
		return IJavaHelpContextIds.JAVA_EDITOR_PREFERENCE_PAGE;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.AbstractConfigurationBlockPreferencePage#setDescription()
	 */
	@Override
	protected void setDescription() {
		// This page has no description
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.AbstractConfigurationBlockPreferencePage#setPreferenceStore()
	 */
	@Override
	protected void setPreferenceStore() {
		setPreferenceStore(JavaPlugin.getDefault().getPreferenceStore());
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.AbstractConfigureationBlockPreferencePage#createConfigurationBlock(org.eclipse.jdt.internal.ui.preferences.OverlayPreferenceStore)
	 */
	@Override
	protected IPreferenceConfigurationBlock createConfigurationBlock(OverlayPreferenceStore overlayPreferenceStore) {
		return new MarkOccurrencesConfigurationBlock(overlayPreferenceStore);
	}
}

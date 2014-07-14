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
package org.eclipse.jdt.internal.ui.preferences.formatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.preferences.PreferencesAccess;

public class FormatterProfileManager extends ProfileManager {

	public final static String ECLIPSE21_PROFILE= "org.eclipse.jdt.ui.default_profile"; //$NON-NLS-1$
	public final static String ECLIPSE_PROFILE= "org.eclipse.jdt.ui.default.eclipse_profile"; //$NON-NLS-1$
	public final static String JAVA_PROFILE= "org.eclipse.jdt.ui.default.sun_profile"; //$NON-NLS-1$

	public final static String DEFAULT_PROFILE= ECLIPSE_PROFILE;

	private final static KeySet[] KEY_SETS= new KeySet[] {
		new KeySet(JavaCore.PLUGIN_ID, new ArrayList<String>(DefaultCodeFormatterConstants.getJavaConventionsSettings().keySet())),
		new KeySet(JavaUI.ID_PLUGIN, Collections.<String>emptyList())
	};

	private final static String PROFILE_KEY= PreferenceConstants.FORMATTER_PROFILE;
	private final static String FORMATTER_SETTINGS_VERSION= "formatter_settings_version";  //$NON-NLS-1$

	public FormatterProfileManager(List<Profile> profiles, IScopeContext context, PreferencesAccess preferencesAccess, IProfileVersioner profileVersioner) {
	    super(addBuiltinProfiles(profiles, profileVersioner), context, preferencesAccess, profileVersioner, KEY_SETS, PROFILE_KEY, FORMATTER_SETTINGS_VERSION);
    }

	private static List<Profile> addBuiltinProfiles(List<Profile> profiles, IProfileVersioner profileVersioner) {
		final Profile javaProfile= new BuiltInProfile(JAVA_PROFILE, FormatterMessages.ProfileManager_java_conventions_profile_name, getJavaSettings(), 1, profileVersioner.getCurrentVersion(), profileVersioner.getProfileKind());
		profiles.add(javaProfile);

		final Profile eclipseProfile= new BuiltInProfile(ECLIPSE_PROFILE, FormatterMessages.ProfileManager_eclipse_profile_name, getEclipseSettings(), 2, profileVersioner.getCurrentVersion(), profileVersioner.getProfileKind());
		profiles.add(eclipseProfile);

		final Profile eclipse21Profile= new BuiltInProfile(ECLIPSE21_PROFILE, FormatterMessages.ProfileManager_default_profile_name, getEclipse21Settings(), 3, profileVersioner.getCurrentVersion(), profileVersioner.getProfileKind());
		profiles.add(eclipse21Profile);
		return profiles;
	}


	/**
	 * @return Returns the settings for the default profile.
	 */
	public static Map<String, String> getEclipse21Settings() {
		final Map<String, String> options= DefaultCodeFormatterConstants.getEclipse21Settings();

		ProfileVersioner.setLatestCompliance(options);
		return options;
	}

	/**
	 * @return Returns the settings for the new eclipse profile.
	 */
	public static Map<String, String> getEclipseSettings() {
		final Map<String, String> options= DefaultCodeFormatterConstants.getEclipseDefaultSettings();

		ProfileVersioner.setLatestCompliance(options);
		return options;
	}

	/**
	 * @return Returns the settings for the Java Conventions profile.
	 */
	public static Map<String, String> getJavaSettings() {
		final Map<String, String> options= DefaultCodeFormatterConstants.getJavaConventionsSettings();

		ProfileVersioner.setLatestCompliance(options);
		return options;
	}

	/**
	 * @return Returns the default settings.
	 */
	public static Map<String, String> getDefaultSettings() {
		return getEclipseSettings();
	}


	/* (non-Javadoc)
     * @see org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager#getSelectedProfileId(org.eclipse.core.runtime.preferences.IScopeContext)
     */
	@Override
	protected String getSelectedProfileId(IScopeContext instanceScope) {
		String profileId= instanceScope.getNode(JavaUI.ID_PLUGIN).get(PROFILE_KEY, null);
		if (profileId == null) {
			// request from bug 129427
			profileId= DefaultScope.INSTANCE.getNode(JavaUI.ID_PLUGIN).get(PROFILE_KEY, null);
			// fix for bug 89739
			if (DEFAULT_PROFILE.equals(profileId)) { // default default:
				IEclipsePreferences node= instanceScope.getNode(JavaCore.PLUGIN_ID);
				if (node != null) {
					String tabSetting= node.get(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, null);
					if (JavaCore.SPACE.equals(tabSetting)) {
						profileId= JAVA_PROFILE;
					}
				}
			}
		}
	    return profileId;
    }


	/* (non-Javadoc)
     * @see org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager#getDefaultProfile()
     */
    @Override
	public Profile getDefaultProfile() {
	    return getProfile(DEFAULT_PROFILE);
    }

}

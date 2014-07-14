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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.osgi.service.prefs.BackingStoreException;
import org.xml.sax.InputSource;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;

import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.preferences.PreferencesAccess;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager.CustomProfile;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager.Profile;



public class FormatterProfileStore extends ProfileStore {

	/**
	 * Preference key where all profiles are stored
	 */
	private static final String PREF_FORMATTER_PROFILES= "org.eclipse.jdt.ui.formatterprofiles"; //$NON-NLS-1$

	private final IProfileVersioner fProfileVersioner;

	public FormatterProfileStore(IProfileVersioner profileVersioner) {
		super(PREF_FORMATTER_PROFILES, profileVersioner);
		fProfileVersioner= profileVersioner;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Profile> readProfiles(IScopeContext scope) throws CoreException {
	    List<Profile> profiles= super.readProfiles(scope);
	    if (profiles == null) {
			profiles= readOldForCompatibility(scope);
		}
	    return profiles;
	}

	/**
	 * Read the available profiles from the internal XML file and return them
	 * as collection.
	 * @return returns a list of <code>CustomProfile</code> or <code>null</code>
	 */
	private List<Profile> readOldForCompatibility(IScopeContext instanceScope) {

		// in 3.0 M9 and less the profiles were stored in a file in the plugin's meta data
		final String STORE_FILE= "code_formatter_profiles.xml"; //$NON-NLS-1$

		File file= JavaPlugin.getDefault().getStateLocation().append(STORE_FILE).toFile();
		if (!file.exists())
			return null;

		try {
			// note that it's wrong to use a file reader when XML declares UTF-8: Kept for compatibility
			final FileReader reader= new FileReader(file);
			try {
				List<Profile> res= readProfilesFromStream(new InputSource(reader));
				if (res != null) {
					for (int i= 0; i < res.size(); i++) {
						fProfileVersioner.update((CustomProfile) res.get(i));
					}
					writeProfiles(res, instanceScope);
				}
				file.delete(); // remove after successful write
				return res;
			} finally {
				reader.close();
			}
		} catch (CoreException e) {
			JavaPlugin.log(e); // log but ignore
		} catch (IOException e) {
			JavaPlugin.log(e); // log but ignore
		}
		return null;
	}


	public static void checkCurrentOptionsVersion() {
		PreferencesAccess access= PreferencesAccess.getOriginalPreferences();
		ProfileVersioner profileVersioner= new ProfileVersioner();

		IScopeContext instanceScope= access.getInstanceScope();
		IEclipsePreferences uiPreferences= instanceScope.getNode(JavaUI.ID_PLUGIN);
		int version= uiPreferences.getInt(PREF_FORMATTER_PROFILES + VERSION_KEY_SUFFIX, 0);
		if (version >= profileVersioner.getCurrentVersion()) {
			return; // is up to date
		}
		try {
			List<Profile> profiles= (new FormatterProfileStore(profileVersioner)).readProfiles(instanceScope);
			if (profiles == null) {
				profiles= new ArrayList<Profile>();
			}
			ProfileManager manager= new FormatterProfileManager(profiles, instanceScope, access, profileVersioner);
			if (manager.getSelected() instanceof CustomProfile) {
				manager.commitChanges(instanceScope); // updates JavaCore options
			}
			uiPreferences.putInt(PREF_FORMATTER_PROFILES + VERSION_KEY_SUFFIX, profileVersioner.getCurrentVersion());
			savePreferences(instanceScope);
		} catch (CoreException e) {
			JavaPlugin.log(e);
		} catch (BackingStoreException e) {
			JavaPlugin.log(e);
		}
	}

	private static void savePreferences(final IScopeContext context) throws BackingStoreException {
		try {
			context.getNode(JavaUI.ID_PLUGIN).flush();
		} finally {
			context.getNode(JavaCore.PLUGIN_ID).flush();
		}
	}
}

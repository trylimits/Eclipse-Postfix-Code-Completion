/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Aaron Luchko, aluchko@redhat.com - 105926 [Formatter] Exporting Unnamed profile fails silently
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences.cleanup;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.IScopeContext;

import org.eclipse.core.resources.IProject;

import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CleanUpPreferenceUtil;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.fix.MapCleanUpOptions;
import org.eclipse.jdt.internal.ui.preferences.BulletListBlock;
import org.eclipse.jdt.internal.ui.preferences.PreferencesAccess;
import org.eclipse.jdt.internal.ui.preferences.formatter.IProfileVersioner;
import org.eclipse.jdt.internal.ui.preferences.formatter.ModifyDialog;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileConfigurationBlock;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager.CustomProfile;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager.Profile;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileStore;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;


/**
 * The clean up configuration block for the clean up preference page.
 */
public class CleanUpConfigurationBlock extends ProfileConfigurationBlock {

	private static final String CLEANUP_PAGE_SETTINGS_KEY= "cleanup_page"; //$NON-NLS-1$
	private static final String DIALOGSTORE_LASTSAVELOADPATH= JavaUI.ID_PLUGIN + ".cleanup"; //$NON-NLS-1$

	private final IScopeContext fCurrContext;
	private SelectionButtonDialogField fShowCleanUpWizardDialogField;
	private CleanUpProfileManager fProfileManager;
	private ProfileStore fProfileStore;

    public CleanUpConfigurationBlock(IProject project, PreferencesAccess access) {
	    super(project, access, DIALOGSTORE_LASTSAVELOADPATH);

		if (project != null) {
			fCurrContext= null;
		} else {
			fCurrContext= access.getInstanceScope();
		}
    }

	@Override
	protected IProfileVersioner createProfileVersioner() {
	    return new CleanUpProfileVersioner();
    }

	@Override
	protected ProfileStore createProfileStore(IProfileVersioner versioner) {
	    fProfileStore= new ProfileStore(CleanUpConstants.CLEANUP_PROFILES, versioner);
		return fProfileStore;
    }

	@Override
	protected ProfileManager createProfileManager(List<Profile> profiles, IScopeContext context, PreferencesAccess access, IProfileVersioner profileVersioner) {
		profiles.addAll(CleanUpPreferenceUtil.getBuiltInProfiles());
	    fProfileManager= new CleanUpProfileManager(profiles, context, access, profileVersioner);
		return fProfileManager;
    }

	/**
     * {@inheritDoc}
     */
    @Override
	protected void configurePreview(Composite composite, int numColumns, final ProfileManager profileManager) {
    	Map<String, String> settings= profileManager.getSelected().getSettings();
		final Map<String, String> sharedSettings= new Hashtable<String, String>();
		fill(settings, sharedSettings);

		final ICleanUp[] cleanUps= JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps();
		CleanUpOptions options= new MapCleanUpOptions(sharedSettings);
		for (int i= 0; i < cleanUps.length; i++) {
			cleanUps[i].setOptions(options);
		}

		createLabel(composite, CleanUpMessages.CleanUpConfigurationBlock_SelectedCleanUps_label, numColumns);

		final BulletListBlock cleanUpListBlock= new BulletListBlock(composite, SWT.NONE);
		GridData gridData= new GridData(SWT.FILL, SWT.FILL, true, true);
		gridData.horizontalSpan= numColumns;
		cleanUpListBlock.setLayoutData(gridData);
		cleanUpListBlock.setText(getSelectedCleanUpsInfo(cleanUps));

		profileManager.addObserver(new Observer() {

			public void update(Observable o, Object arg) {
				final int value= ((Integer)arg).intValue();
				switch (value) {
				case ProfileManager.PROFILE_CREATED_EVENT:
				case ProfileManager.PROFILE_DELETED_EVENT:
				case ProfileManager.SELECTION_CHANGED_EVENT:
				case ProfileManager.SETTINGS_CHANGED_EVENT:
					fill(profileManager.getSelected().getSettings(), sharedSettings);
					cleanUpListBlock.setText(getSelectedCleanUpsInfo(cleanUps));
				}
            }

		});
    }

    private String getSelectedCleanUpsInfo(ICleanUp[] cleanUps) {
    	if (cleanUps.length == 0)
    		return ""; //$NON-NLS-1$

    	StringBuffer buf= new StringBuffer();

    	boolean first= true;
    	for (int i= 0; i < cleanUps.length; i++) {
	        String[] descriptions= cleanUps[i].getStepDescriptions();
	        if (descriptions != null) {
    	        for (int j= 0; j < descriptions.length; j++) {
    	        	if (first) {
    	        		first= false;
    	        	} else {
    	        		buf.append('\n');
    	        	}
    	            buf.append(descriptions[j]);
                }
	        }
        }

    	return buf.toString();
    }

	private void fill(Map<String, String> settings, Map<String, String> sharedSettings) {
		sharedSettings.clear();
		for (Iterator<String> iterator= settings.keySet().iterator(); iterator.hasNext();) {
	        String key= iterator.next();
	        sharedSettings.put(key, settings.get(key));
        }
    }

	@Override
	protected ModifyDialog createModifyDialog(Shell shell, Profile profile, ProfileManager profileManager, ProfileStore profileStore, boolean newProfile) {
        return new CleanUpModifyDialog(shell, profile, profileManager, profileStore, newProfile, CLEANUP_PAGE_SETTINGS_KEY, DIALOGSTORE_LASTSAVELOADPATH);
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Composite createContents(Composite parent) {
	    Composite composite= super.createContents(parent);

	    if (fCurrContext == null)
	    	return composite;

	    fShowCleanUpWizardDialogField= new SelectionButtonDialogField(SWT.CHECK);
		fShowCleanUpWizardDialogField.setLabelText(CleanUpMessages.CleanUpConfigurationBlock_ShowCleanUpWizard_checkBoxLabel);
	    fShowCleanUpWizardDialogField.doFillIntoGrid(composite, 5);

	    IEclipsePreferences node= fCurrContext.getNode(JavaUI.ID_PLUGIN);
		boolean showWizard;
		if (node.get(CleanUpConstants.SHOW_CLEAN_UP_WIZARD, null) != null) {
			showWizard= node.getBoolean(CleanUpConstants.SHOW_CLEAN_UP_WIZARD, true);
		} else {
			showWizard= DefaultScope.INSTANCE.getNode(JavaUI.ID_PLUGIN).getBoolean(CleanUpConstants.SHOW_CLEAN_UP_WIZARD, true);
		}
		if (showWizard)
			fShowCleanUpWizardDialogField.setSelection(true);

	    fShowCleanUpWizardDialogField.setDialogFieldListener(new IDialogFieldListener() {
			public void dialogFieldChanged(DialogField field) {
				doShowCleanUpWizard(fShowCleanUpWizardDialogField.isSelected());
            }
	    });

		return composite;
	}

	private void doShowCleanUpWizard(boolean showWizard) {
		IEclipsePreferences preferences= fCurrContext.getNode(JavaUI.ID_PLUGIN);
		if (preferences.get(CleanUpConstants.SHOW_CLEAN_UP_WIZARD, null) != null &&
				preferences.getBoolean(CleanUpConstants.SHOW_CLEAN_UP_WIZARD, true) == showWizard)
			return;

		preferences.putBoolean(CleanUpConstants.SHOW_CLEAN_UP_WIZARD, showWizard);
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void performDefaults() {
		super.performDefaults();
		if (fCurrContext == null)
			return;

		fCurrContext.getNode(JavaUI.ID_PLUGIN).remove(CleanUpConstants.SHOW_CLEAN_UP_WIZARD);
		boolean showWizard= DefaultScope.INSTANCE.getNode(JavaUI.ID_PLUGIN).getBoolean(CleanUpConstants.SHOW_CLEAN_UP_WIZARD, true);
		fShowCleanUpWizardDialogField.setDialogFieldListener(null);
		fShowCleanUpWizardDialogField.setSelection(showWizard);
		fShowCleanUpWizardDialogField.setDialogFieldListener(new IDialogFieldListener() {
			public void dialogFieldChanged(DialogField field) {
				doShowCleanUpWizard(fShowCleanUpWizardDialogField.isSelected());
            }
	    });
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void preferenceChanged(PreferenceChangeEvent event) {
		if (CleanUpConstants.CLEANUP_PROFILES.equals(event.getKey())) {
			try {
				String id= fCurrContext.getNode(JavaUI.ID_PLUGIN).get(CleanUpConstants.CLEANUP_PROFILE, null);
				if (id == null)
					fProfileManager.getDefaultProfile().getID();

				List<Profile> oldProfiles= fProfileManager.getSortedProfiles();
				Profile[] oldProfilesArray= oldProfiles.toArray(new Profile[oldProfiles.size()]);
				for (int i= 0; i < oldProfilesArray.length; i++) {
					if (oldProfilesArray[i] instanceof CustomProfile) {
						fProfileManager.deleteProfile((CustomProfile)oldProfilesArray[i]);
					}
				}

				List<Profile> newProfiles= fProfileStore.readProfilesFromString((String)event.getNewValue());
				for (Iterator<Profile> iterator= newProfiles.iterator(); iterator.hasNext();) {
					CustomProfile profile= (CustomProfile)iterator.next();
					fProfileManager.addProfile(profile);
				}

				Profile profile= fProfileManager.getProfile(id);
				if (profile != null) {
					fProfileManager.setSelected(profile);
				} else {
					fProfileManager.setSelected(fProfileManager.getDefaultProfile());
				}
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		} else if (CleanUpConstants.CLEANUP_PROFILE.equals(event.getKey())) {
			if (event.getNewValue() == null) {
				fProfileManager.setSelected(fProfileManager.getDefaultProfile());
			} else {
				Profile profile= fProfileManager.getProfile((String)event.getNewValue());
				if (profile != null) {
					fProfileManager.setSelected(profile);
				}
			}
		}
	}

}
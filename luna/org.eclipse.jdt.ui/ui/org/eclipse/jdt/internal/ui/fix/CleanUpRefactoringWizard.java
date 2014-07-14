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
package org.eclipse.jdt.internal.ui.fix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xml.sax.InputSource;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

import org.eclipse.core.resources.ProjectScope;

import org.eclipse.jface.dialogs.ControlEnableState;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ColumnLayoutData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.ui.dialogs.PreferencesUtil;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CleanUpPreferenceUtil;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.corext.fix.CleanUpRegistry.CleanUpTabPageDescriptor;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.preferences.BulletListBlock;
import org.eclipse.jdt.internal.ui.preferences.CleanUpPreferencePage;
import org.eclipse.jdt.internal.ui.preferences.cleanup.CleanUpProfileVersioner;
import org.eclipse.jdt.internal.ui.preferences.cleanup.CleanUpTabPage;
import org.eclipse.jdt.internal.ui.preferences.formatter.IModifyDialogTabPage;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager.CustomProfile;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager.Profile;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileStore;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;

public class CleanUpRefactoringWizard extends RefactoringWizard {

	private static final String USE_CUSTOM_PROFILE_KEY= "org.eclipse.jdt.ui.cleanup.use_dialog_profile"; //$NON-NLS-1$
	private static final String CUSTOM_PROFILE_KEY= "org.eclipse.jdt.ui.cleanup.custom_profile"; //$NON-NLS-1$

	private static class ProjectProfileLableProvider extends LabelProvider implements ITableLabelProvider {

		private Hashtable<String, Profile> fProfileIdsTable;

		/**
		 * {@inheritDoc}
		 */
		public Image getColumnImage(Object element, int columnIndex) {
			return null;
		}

		/**
		 * {@inheritDoc}
		 */
		public String getColumnText(Object element, int columnIndex) {
			if (columnIndex == 0) {
				return ((IJavaProject)element).getProject().getName();
			} else if (columnIndex == 1) {

				if (fProfileIdsTable == null)
		    		fProfileIdsTable= loadProfiles();

				IEclipsePreferences instancePreferences= InstanceScope.INSTANCE.getNode(JavaUI.ID_PLUGIN);

	    		final String workbenchProfileId;
	    		if (instancePreferences.get(CleanUpConstants.CLEANUP_PROFILE, null) != null) {
	    			workbenchProfileId= instancePreferences.get(CleanUpConstants.CLEANUP_PROFILE, null);
	    		} else {
	    			workbenchProfileId= CleanUpConstants.DEFAULT_PROFILE;
	    		}

				return getProjectProfileName((IJavaProject)element, fProfileIdsTable, workbenchProfileId);
			}
			return null;
		}

		private Hashtable<String, Profile> loadProfiles() {
    		List<Profile> list= CleanUpPreferenceUtil.loadProfiles(InstanceScope.INSTANCE);
    		Hashtable<String, Profile> profileIdsTable= new Hashtable<String, Profile>();
    		for (Iterator<Profile> iterator= list.iterator(); iterator.hasNext();) {
	            Profile profile= iterator.next();
	            profileIdsTable.put(profile.getID(), profile);
            }

    		return profileIdsTable;
        }

		private String getProjectProfileName(final IJavaProject project, Hashtable<String, Profile> profileIdsTable, String workbenchProfileId) {
			ProjectScope projectScope= new ProjectScope(project.getProject());
	        IEclipsePreferences node= projectScope.getNode(JavaUI.ID_PLUGIN);
	        String id= node.get(CleanUpConstants.CLEANUP_PROFILE, null);
			if (id == null) {
	        	Profile profile= profileIdsTable.get(workbenchProfileId);
		        if (profile != null) {
		        	return profile.getName();
		        } else {
		        	return MultiFixMessages.CleanUpRefactoringWizard_unknownProfile_Name;
		        }
	        } else {
		        Profile profile= profileIdsTable.get(id);
		        if (profile != null) {
		        	return profile.getName();
		        } else {
		        	return Messages.format(MultiFixMessages.CleanUpRefactoringWizard_UnmanagedProfileWithName_Name, id.substring(ProfileManager.ID_PREFIX.length()));
		        }
	        }
        }

		public void reset() {
			fProfileIdsTable= null;
        }
	}

	private static class CleanUpConfigurationPage extends UserInputWizardPage implements IModifyDialogTabPage.IModificationListener {

		private static final class WizardCleanUpSelectionDialog extends CleanUpSelectionDialog {

			private static final String CLEAN_UP_SELECTION_PREFERENCE_KEY= "clean_up_selection_dialog"; //$NON-NLS-1$

			private WizardCleanUpSelectionDialog(Shell parent, Map<String, String> settings) {
				super(parent, settings, MultiFixMessages.CleanUpRefactoringWizard_CustomCleanUpsDialog_title);
			}

			@Override
			protected NamedCleanUpTabPage[] createTabPages(Map<String, String> workingValues) {
				CleanUpTabPageDescriptor[] descriptors= JavaPlugin.getDefault().getCleanUpRegistry().getCleanUpTabPageDescriptors(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS);

				NamedCleanUpTabPage[] result= new NamedCleanUpTabPage[descriptors.length];

				for (int i= 0; i < descriptors.length; i++) {
					String name= descriptors[i].getName();
					CleanUpTabPage page= descriptors[i].createTabPage();

					page.setOptionsKind(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS);
					page.setModifyListener(this);
					page.setWorkingValues(workingValues);

					result[i]= new NamedCleanUpTabPage(name, page);
				}

				return result;
			}

			@Override
			protected String getPreferenceKeyPrefix() {
				return CLEAN_UP_SELECTION_PREFERENCE_KEY;
			}

			@Override
			protected String getSelectionCountMessage(int selectionCount, int size) {
				return Messages.format(MultiFixMessages.CleanUpRefactoringWizard_XofYCleanUpsSelected_message, new Object[] {new Integer(selectionCount), new Integer(size)});
			}

			@Override
			protected String getEmptySelectionMessage() {
				return MultiFixMessages.CleanUpRefactoringWizard_EmptySelection_message;
			}
		}

		private static final class ProfileTableAdapter implements IListAdapter<IJavaProject> {
	        private final ProjectProfileLableProvider fProvider;
			private final Shell fShell;

	        private ProfileTableAdapter(ProjectProfileLableProvider provider, Shell shell) {
		        fProvider= provider;
				fShell= shell;
	        }

	        public void customButtonPressed(ListDialogField<IJavaProject> field, int index) {
	        	openPropertyDialog(field);
	        }

	        public void doubleClicked(ListDialogField<IJavaProject> field) {
				openPropertyDialog(field);
	        }

	        private void openPropertyDialog(ListDialogField<IJavaProject> field) {
	            IJavaProject project= field.getSelectedElements().get(0);
	        	PreferencesUtil.createPropertyDialogOn(fShell, project, CleanUpPreferencePage.PROP_ID, null, null).open();
	        	List<?> selectedElements= field.getSelectedElements();
	        	fProvider.reset();
	        	field.refresh();
	        	field.selectElements(new StructuredSelection(selectedElements));
            }

	        public void selectionChanged(ListDialogField<IJavaProject> field) {
	        	if (field.getSelectedElements().size() != 1) {
	        		field.enableButton(0, false);
	        	} else {
	        		field.enableButton(0, true);
	        	}
	        }
        }

		private static final String ENCODING= "UTF-8"; //$NON-NLS-1$

		private final CleanUpRefactoring fCleanUpRefactoring;
		private Map<String, String> fCustomSettings;
		private SelectionButtonDialogField fUseCustomField;

		private ControlEnableState fEnableState;

		public CleanUpConfigurationPage(CleanUpRefactoring refactoring) {
			super(MultiFixMessages.CleanUpRefactoringWizard_CleanUpConfigurationPage_title);
			fCleanUpRefactoring= refactoring;
			int cleanUpTargetsSize= fCleanUpRefactoring.getCleanUpTargetsSize();
			IJavaProject[] projects= fCleanUpRefactoring.getProjects();
			if (cleanUpTargetsSize == 1) {
				setMessage(MultiFixMessages.CleanUpRefactoringWizard_CleaningUp11_Title);
			} else if (projects.length == 1) {
				setMessage(Messages.format(MultiFixMessages.CleanUpRefactoringWizard_CleaningUpN1_Title, new Integer(cleanUpTargetsSize)));
			} else {
				setMessage(Messages.format(MultiFixMessages.CleanUpRefactoringWizard_CleaningUpNN_Title, new Object[] {new Integer(cleanUpTargetsSize), new Integer(projects.length)}));
			}
        }

		/**
         * {@inheritDoc}
         */
        public void createControl(Composite parent) {
			initializeDialogUnits(parent);

        	boolean isCustom= getDialogSettings().getBoolean(USE_CUSTOM_PROFILE_KEY);

        	final Composite composite= new Composite(parent, SWT.NONE);
			composite.setLayout(new GridLayout(2, false));
			composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			composite.setFont(parent.getFont());

			SelectionButtonDialogField useProfile= new SelectionButtonDialogField(SWT.RADIO);
			useProfile.setLabelText(MultiFixMessages.CleanUpRefactoringWizard_use_configured_radio);
			useProfile.setSelection(!isCustom);
			useProfile.doFillIntoGrid(composite, 2);

			ProjectProfileLableProvider tableLabelProvider= new ProjectProfileLableProvider();
			IListAdapter<IJavaProject> listAdapter= new ProfileTableAdapter(tableLabelProvider, getShell());
			String[] buttons= new String[] {
				MultiFixMessages.CleanUpRefactoringWizard_Configure_Button
			};
			final ListDialogField<IJavaProject> settingsField= new ListDialogField<IJavaProject>(listAdapter, buttons, tableLabelProvider) {
				/**
				 * {@inheritDoc}
				 */
				@Override
				protected int getListStyle() {
					return super.getListStyle() | SWT.SINGLE;
				}
			};

			String[] headerNames= new String[] {
					MultiFixMessages.CleanUpRefactoringWizard_Project_TableHeader,
					MultiFixMessages.CleanUpRefactoringWizard_Profile_TableHeader
			};
			ColumnLayoutData[] columns = new ColumnLayoutData[] {
					new ColumnWeightData(2, true),
					new ColumnWeightData(1, true)
			};
			settingsField.setTableColumns(new ListDialogField.ColumnsDescription(columns , headerNames, true));
			settingsField.setViewerComparator(new ViewerComparator());

			settingsField.doFillIntoGrid(composite, 3);

			Table table= settingsField.getTableViewer().getTable();
			GridData data= (GridData)settingsField.getListControl(null).getLayoutData();
			data.horizontalIndent= 15;
			data.grabExcessVerticalSpace= false;
			data.heightHint= SWTUtil.getTableHeightHint(table, Math.min(5, fCleanUpRefactoring.getProjects().length + 2));
			data.grabExcessHorizontalSpace= true;
			data.verticalAlignment= GridData.BEGINNING;

			data= (GridData)settingsField.getButtonBox(null).getLayoutData();
			data.grabExcessVerticalSpace= false;
			data.verticalAlignment= GridData.BEGINNING;

			data= (GridData)settingsField.getLabelControl(null).getLayoutData();
			data.exclude= true;

			settingsField.setElements(Arrays.asList(fCleanUpRefactoring.getProjects()));
			settingsField.selectFirstElement();

			fUseCustomField= new SelectionButtonDialogField(SWT.RADIO);
			fUseCustomField.setLabelText(MultiFixMessages.CleanUpRefactoringWizard_use_custom_radio);
			fUseCustomField.setSelection(isCustom);
			fUseCustomField.doFillIntoGrid(composite, 2);

			String settings= getDialogSettings().get(CUSTOM_PROFILE_KEY);
			if (settings == null) {
				fCustomSettings= JavaPlugin.getDefault().getCleanUpRegistry().getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS).getMap();
			} else {
				try {
	                fCustomSettings= decodeSettings(settings);
                } catch (CoreException e) {
	                JavaPlugin.log(e);
	                fCustomSettings= JavaPlugin.getDefault().getCleanUpRegistry().getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS).getMap();
                }
			}

			final BulletListBlock bulletListBlock= new BulletListBlock(composite, SWT.NONE);
			GridData layoutData= new GridData(SWT.FILL, SWT.FILL, true, true);
			layoutData.horizontalIndent= 15;
			layoutData.grabExcessVerticalSpace= true;
			bulletListBlock.setLayoutData(layoutData);

			final Button configure= new Button(composite, SWT.NONE);
			configure.setText(MultiFixMessages.CleanUpRefactoringWizard_ConfigureCustomProfile_button);

			data= new GridData(SWT.TOP, SWT.LEAD, false, false);
			data.widthHint= SWTUtil.getButtonWidthHint(configure);
			configure.setLayoutData(data);

			showCustomSettings(bulletListBlock);
			configure.addSelectionListener(new SelectionAdapter() {
				/**
				 * {@inheritDoc}
				 */
				@Override
				public void widgetSelected(SelectionEvent e) {
					Hashtable<String, String> workingValues= new Hashtable<String, String>(fCustomSettings);
					CleanUpSelectionDialog dialog= new WizardCleanUpSelectionDialog(getShell(), workingValues);
					if (dialog.open() == Window.OK) {
						fCustomSettings= workingValues;
						showCustomSettings(bulletListBlock);
					}
				}
			});

			updateEnableState(isCustom, settingsField, configure, bulletListBlock);

			fUseCustomField.setDialogFieldListener(new IDialogFieldListener() {
				public void dialogFieldChanged(DialogField field) {
					updateEnableState(fUseCustomField.isSelected(), settingsField, configure, bulletListBlock);
                }
			});

			Link preferencePageLink= new Link(composite, SWT.WRAP);
			preferencePageLink.setText(MultiFixMessages.CleanUpRefactoringWizard_HideWizard_Link);
			preferencePageLink.setFont(parent.getFont());
			GridData gridData= new GridData(SWT.FILL, SWT.FILL, true, false);
			gridData.widthHint= convertWidthInCharsToPixels(50);
			gridData.horizontalSpan= 2;
			preferencePageLink.setLayoutData(gridData);
			preferencePageLink.addSelectionListener(new SelectionAdapter() {
				/**
				 * {@inheritDoc}
				 */
				@Override
				public void widgetSelected(SelectionEvent e) {
					PreferencesUtil.createPreferenceDialogOn(composite.getShell(), CleanUpPreferencePage.PREF_ID, null, null).open();
				}
			});

			setControl(composite);

			Dialog.applyDialogFont(composite);
        }

		private void updateEnableState(boolean isCustom, final ListDialogField<IJavaProject> settingsField, Button configureCustom, BulletListBlock bulletListBlock) {
			settingsField.getListControl(null).setEnabled(!isCustom);
			if (isCustom) {
				fEnableState= ControlEnableState.disable(settingsField.getButtonBox(null));
			} else if (fEnableState != null) {
				fEnableState.restore();
				fEnableState= null;
			}
			bulletListBlock.setEnabled(isCustom);
			configureCustom.setEnabled(isCustom);
		}

        private void showCustomSettings(BulletListBlock bulletListBlock) {
			StringBuffer buf= new StringBuffer();

			final ICleanUp[] cleanUps= JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps();
			CleanUpOptions options= new MapCleanUpOptions(fCustomSettings);
	    	for (int i= 0; i < cleanUps.length; i++) {
	    		cleanUps[i].setOptions(options);
		        String[] descriptions= cleanUps[i].getStepDescriptions();
		        if (descriptions != null) {
	    	        for (int j= 0; j < descriptions.length; j++) {
	    	        	if (buf.length() > 0) {
	    	        		buf.append('\n');
	    	        	}
	    	            buf.append(descriptions[j]);
	                }
		        }
	        }
	    	bulletListBlock.setText(buf.toString());
        }

        @Override
		protected boolean performFinish() {
			initializeRefactoring();
			storeSettings();
			return super.performFinish();
		}

		@Override
		public IWizardPage getNextPage() {
			initializeRefactoring();
			storeSettings();
			return super.getNextPage();
		}

		private void storeSettings() {
			getDialogSettings().put(USE_CUSTOM_PROFILE_KEY, fUseCustomField.isSelected());
			try {
	            getDialogSettings().put(CUSTOM_PROFILE_KEY, encodeSettings(fCustomSettings));
            } catch (CoreException e) {
	            JavaPlugin.log(e);
            }
        }

		private void initializeRefactoring() {
			CleanUpRefactoring refactoring= (CleanUpRefactoring)getRefactoring();

			CleanUpOptions options= null;
			if (fUseCustomField.isSelected()) {
				refactoring.setUseOptionsFromProfile(false);
				options= new MapCleanUpOptions(fCustomSettings);
			} else {
				refactoring.setUseOptionsFromProfile(true);
			}

			refactoring.clearCleanUps();
			ICleanUp[] cleanups= JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps();
			for (int i= 0; i < cleanups.length; i++) {
				if (options != null)
					cleanups[i].setOptions(options);
	            refactoring.addCleanUp(cleanups[i]);
            }
        }

		public String encodeSettings(Map<String, String> settings) throws CoreException {
			ByteArrayOutputStream stream= new ByteArrayOutputStream(2000);
			try {
				CleanUpProfileVersioner versioner= new CleanUpProfileVersioner();
				CustomProfile profile= new ProfileManager.CustomProfile("custom", settings, versioner.getCurrentVersion(), versioner.getProfileKind()); //$NON-NLS-1$
				ArrayList<Profile> profiles= new ArrayList<Profile>();
				profiles.add(profile);
				ProfileStore.writeProfilesToStream(profiles, stream, ENCODING, versioner);
				try {
					return stream.toString(ENCODING);
				} catch (UnsupportedEncodingException e) {
					return stream.toString();
				}
			} finally {
				try { stream.close(); } catch (IOException e) { /* ignore */ }
			}
		}

		public Map<String, String> decodeSettings(String settings) throws CoreException {
			byte[] bytes;
			try {
				bytes= settings.getBytes(ENCODING);
			} catch (UnsupportedEncodingException e) {
				bytes= settings.getBytes();
			}
			InputStream is= new ByteArrayInputStream(bytes);
			try {
				List<Profile> res= ProfileStore.readProfilesFromStream(new InputSource(is));
				if (res == null || res.size() == 0)
					return JavaPlugin.getDefault().getCleanUpRegistry().getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS).getMap();

				CustomProfile profile= (CustomProfile)res.get(0);
				new CleanUpProfileVersioner().update(profile);
				return profile.getSettings();
			} finally {
				try { is.close(); } catch (IOException e) { /* ignore */ }
			}
		}

		/**
         * {@inheritDoc}
         */
        public void updateStatus(IStatus status) {}

		/**
         * {@inheritDoc}
         */
        public void valuesModified() {}
	}

	public CleanUpRefactoringWizard(CleanUpRefactoring refactoring, int flags) {
		super(refactoring, flags);
		setDefaultPageTitle(MultiFixMessages.CleanUpRefactoringWizard_PageTitle);
		setWindowTitle(MultiFixMessages.CleanUpRefactoringWizard_WindowTitle);
		setDefaultPageImageDescriptor(JavaPluginImages.DESC_WIZBAN_CLEAN_UP);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ltk.ui.refactoring.RefactoringWizard#addUserInputPages()
	 */
	@Override
	protected void addUserInputPages() {
		addPage(new CleanUpConfigurationPage((CleanUpRefactoring)getRefactoring()));
	}

}

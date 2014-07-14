/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.osgi.service.prefs.Preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.preferences.BundleDefaultsScope;

import org.eclipse.core.resources.IProject;

import org.eclipse.jface.dialogs.ControlEnableState;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.resource.JFaceResources;

import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstall2;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathSupport;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;


/**
 * Configuration block for the 'Java Compiler' page.
 */
public class ComplianceConfigurationBlock extends OptionsConfigurationBlock {

	/**
	 * Key for the "Compiler compliance follows EE" setting.
	 * <br>Only applicable if <code>fProject != null</code>.
	 * <p>Values are { {@link #DEFAULT_CONF}, {@link #USER_CONF}, or {@link #DISABLED} }.
	 */
	private static final Key INTR_COMPLIANCE_FOLLOWS_EE= getLocalKey("internal.compliance.follows.ee"); //$NON-NLS-1$
	
	/**
	 * Key for the "Use default compliance" setting.
	 * <p>Values are { {@link #DEFAULT_CONF}, {@link #USER_CONF} }.
	 */
	private static final Key INTR_DEFAULT_COMPLIANCE= getLocalKey("internal.default.compliance"); //$NON-NLS-1$
	
	// Preference store keys, see JavaCore.getOptions
	private static final Key PREF_PB_ASSERT_AS_IDENTIFIER= getJDTCoreKey(JavaCore.COMPILER_PB_ASSERT_IDENTIFIER);
	private static final Key PREF_PB_ENUM_AS_IDENTIFIER= getJDTCoreKey(JavaCore.COMPILER_PB_ENUM_IDENTIFIER);
	private static final Key PREF_SOURCE_COMPATIBILITY= getJDTCoreKey(JavaCore.COMPILER_SOURCE);
	private static final Key PREF_CODEGEN_TARGET_PLATFORM= getJDTCoreKey(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM);
	private static final Key PREF_COMPLIANCE= getJDTCoreKey(JavaCore.COMPILER_COMPLIANCE);
	
	/* see also BuildPathSupport#PREFS_COMPLIANCE */
	private static final Key[] PREFS_COMPLIANCE= new Key[] { PREF_COMPLIANCE,
		PREF_PB_ASSERT_AS_IDENTIFIER, PREF_PB_ENUM_AS_IDENTIFIER,
		PREF_SOURCE_COMPATIBILITY, PREF_CODEGEN_TARGET_PLATFORM };
	
	private static final Key PREF_CODEGEN_INLINE_JSR_BYTECODE= getJDTCoreKey(JavaCore.COMPILER_CODEGEN_INLINE_JSR_BYTECODE);
	
	private static final Key PREF_LOCAL_VARIABLE_ATTR=  getJDTCoreKey(JavaCore.COMPILER_LOCAL_VARIABLE_ATTR);
	private static final Key PREF_LINE_NUMBER_ATTR= getJDTCoreKey(JavaCore.COMPILER_LINE_NUMBER_ATTR);
	private static final Key PREF_SOURCE_FILE_ATTR= getJDTCoreKey(JavaCore.COMPILER_SOURCE_FILE_ATTR);
	private static final Key PREF_CODEGEN_UNUSED_LOCAL= getJDTCoreKey(JavaCore.COMPILER_CODEGEN_UNUSED_LOCAL);
	private static final Key PREF_CODEGEN_METHOD_PARAMETERS_ATTR= getJDTCoreKey(JavaCore.COMPILER_CODEGEN_METHOD_PARAMETERS_ATTR);
	
	// values
	private static final String GENERATE= JavaCore.GENERATE;
	private static final String DO_NOT_GENERATE= JavaCore.DO_NOT_GENERATE;

	private static final String PRESERVE= JavaCore.PRESERVE;
	private static final String OPTIMIZE_OUT= JavaCore.OPTIMIZE_OUT;

	private static final String VERSION_CLDC_1_1= JavaCore.VERSION_CLDC_1_1;
	private static final String VERSION_1_1= JavaCore.VERSION_1_1;
	private static final String VERSION_1_2= JavaCore.VERSION_1_2;
	private static final String VERSION_1_3= JavaCore.VERSION_1_3;

	private static final String VERSION_1_4= JavaCore.VERSION_1_4;
	private static final String VERSION_1_5= JavaCore.VERSION_1_5;
	private static final String VERSION_1_6= JavaCore.VERSION_1_6;
	private static final String VERSION_1_7= JavaCore.VERSION_1_7;
	private static final String VERSION_1_8= JavaCore.VERSION_1_8;
	private static final String VERSION_JSR14= "jsr14"; //$NON-NLS-1$

	private static final String ERROR= JavaCore.ERROR;
	private static final String WARNING= JavaCore.WARNING;
	private static final String IGNORE= JavaCore.IGNORE;

	private static final String ENABLED= JavaCore.ENABLED;
	private static final String DISABLED= JavaCore.DISABLED;

	private static final String DEFAULT_CONF= "default"; //$NON-NLS-1$
	private static final String USER_CONF= "user";	 //$NON-NLS-1$

	private ArrayList<Control> fComplianceFollowsEEControls;
	private ArrayList<Control> fComplianceControls;
	private ArrayList<Control> fComplianceChildControls;
	private PixelConverter fPixelConverter;

	/**
	 * Remembered user compliance (stored when {@link #INTR_DEFAULT_COMPLIANCE} is switched to
	 * {@link #DEFAULT_CONF}). Elements are identified by <code>IDX_*</code> constants.
	 *
	 * @see #IDX_ASSERT_AS_IDENTIFIER
	 * @see #IDX_ENUM_AS_IDENTIFIER
	 * @see #IDX_SOURCE_COMPATIBILITY
	 * @see #IDX_CODEGEN_TARGET_PLATFORM
	 * @see #IDX_COMPLIANCE
	 * @see #IDX_INLINE_JSR_BYTECODE
	 * @see #IDX_METHOD_PARAMETERS_ATTR
	 */
	private String[] fRememberedUserCompliance;
	
	/**
	 * Stored compliance settings that were active when the page was first shown. May be <code>null</code>.
	 * Elements are identified by <code>IDX_*</code> constants.
	 * @see #IDX_ASSERT_AS_IDENTIFIER
	 * @see #IDX_ENUM_AS_IDENTIFIER
	 * @see #IDX_SOURCE_COMPATIBILITY
	 * @see #IDX_CODEGEN_TARGET_PLATFORM
	 * @see #IDX_COMPLIANCE
	 * @see #IDX_INLINE_JSR_BYTECODE
	 * @see #IDX_METHOD_PARAMETERS_ATTR
	 */
	private String[] fOriginalStoredCompliance;

	private static final int IDX_ASSERT_AS_IDENTIFIER= 0;
	private static final int IDX_ENUM_AS_IDENTIFIER= 1;
	private static final int IDX_SOURCE_COMPATIBILITY= 2;
	private static final int IDX_CODEGEN_TARGET_PLATFORM= 3;
	private static final int IDX_COMPLIANCE= 4;
	private static final int IDX_INLINE_JSR_BYTECODE= 5;
	private static final int IDX_METHOD_PARAMETERS_ATTR= 6;

	private IStatus fComplianceStatus;

	private Link fJRE50InfoText;
	private Label fJRE50InfoImage;
	private Composite fControlsComposite;
	private ControlEnableState fBlockEnableState;

	public ComplianceConfigurationBlock(IStatusChangeListener context, IProject project, IWorkbenchPreferenceContainer container) {
		super(context, project, getKeys(project != null), container);
		setDefaultCompilerComplianceValues();

		fBlockEnableState= null;
		fComplianceFollowsEEControls= new ArrayList<Control>();
		fComplianceControls= new ArrayList<Control>();
		fComplianceChildControls= new ArrayList<Control>();

		fComplianceStatus= new StatusInfo();

		fRememberedUserCompliance= new String[] { // caution: order depends on IDX_* constants
			getValue(PREF_PB_ASSERT_AS_IDENTIFIER),
			getValue(PREF_PB_ENUM_AS_IDENTIFIER),
			getValue(PREF_SOURCE_COMPATIBILITY),
			getValue(PREF_CODEGEN_TARGET_PLATFORM),
			getValue(PREF_COMPLIANCE),
			getValue(PREF_CODEGEN_INLINE_JSR_BYTECODE),
			getValue(PREF_CODEGEN_METHOD_PARAMETERS_ATTR)
		};
	}

	private static Key[] getKeys(boolean projectSpecific) {
		Key[] keys= new Key[] {
				PREF_LOCAL_VARIABLE_ATTR, PREF_LINE_NUMBER_ATTR, PREF_SOURCE_FILE_ATTR, PREF_CODEGEN_UNUSED_LOCAL, PREF_CODEGEN_INLINE_JSR_BYTECODE, INTR_DEFAULT_COMPLIANCE,
				PREF_COMPLIANCE, PREF_SOURCE_COMPATIBILITY,
				PREF_CODEGEN_TARGET_PLATFORM, PREF_PB_ASSERT_AS_IDENTIFIER, PREF_PB_ENUM_AS_IDENTIFIER, PREF_CODEGEN_METHOD_PARAMETERS_ATTR
			};
		
		if (projectSpecific) {
			Key[] allKeys = new Key[keys.length + 1];
			System.arraycopy(keys, 0, allKeys, 0, keys.length);
			allKeys[keys.length]= INTR_COMPLIANCE_FOLLOWS_EE;
			return allKeys;
		}
		
		return keys;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#settingsUpdated()
	 */
	@Override
	protected void settingsUpdated() {
		setValue(INTR_DEFAULT_COMPLIANCE, getCurrentCompliance());
		updateComplianceFollowsEE();
		super.settingsUpdated();
	}

	/*
	 * @see org.eclipse.jface.preference.PreferencePage#createContents(Composite)
	 */
	@Override
	protected Control createContents(Composite parent) {
		fPixelConverter= new PixelConverter(parent);
		setShell(parent.getShell());

		Composite complianceComposite= createComplianceTabContent(parent);

		validateSettings(null, null, null);

		return complianceComposite;
	}

	public void enablePreferenceContent(boolean enable) {
		if (fControlsComposite != null && !fControlsComposite.isDisposed()) {
			if (enable) {
				if (fBlockEnableState != null) {
					fBlockEnableState.restore();
					fBlockEnableState= null;
				}
			} else {
				if (fBlockEnableState == null) {
					fBlockEnableState= ControlEnableState.disable(fControlsComposite);
				}
			}
		}
	}

	private Composite createComplianceTabContent(Composite folder) {

		String[] values3456= new String[] { VERSION_1_3, VERSION_1_4, VERSION_1_5, VERSION_1_6, VERSION_1_7, VERSION_1_8 };
		String[] values3456Labels= new String[] {
			PreferencesMessages.ComplianceConfigurationBlock_version13,
			PreferencesMessages.ComplianceConfigurationBlock_version14,
			PreferencesMessages.ComplianceConfigurationBlock_version15,
			PreferencesMessages.ComplianceConfigurationBlock_version16,
			PreferencesMessages.ComplianceConfigurationBlock_version17,
			PreferencesMessages.ComplianceConfigurationBlock_version18,
		};

		final ScrolledPageContent sc1 = new ScrolledPageContent(folder);
		Composite composite= sc1.getBody();
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		composite.setLayout(layout);

		fControlsComposite= new Composite(composite, SWT.NONE);
		fControlsComposite.setFont(composite.getFont());
		fControlsComposite.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));

		layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.numColumns= 1;
		fControlsComposite.setLayout(layout);

		int nColumns= 3;

		layout= new GridLayout();
		layout.numColumns= nColumns;

		Group group= new Group(fControlsComposite, SWT.NONE);
		group.setFont(fControlsComposite.getFont());
		group.setText(PreferencesMessages.ComplianceConfigurationBlock_compliance_group_label);
		group.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));
		group.setLayout(layout);

		String[] defaultUserValues= new String[] { DEFAULT_CONF, USER_CONF };
		
		Control[] otherChildren= group.getChildren();
		if (fProject != null) {
			String label= PreferencesMessages.ComplianceConfigurationBlock_compliance_follows_EE_label;
			int widthHint= fPixelConverter.convertWidthInCharsToPixels(40);
			addCheckBoxWithLink(group, label, INTR_COMPLIANCE_FOLLOWS_EE, defaultUserValues, 0, widthHint, new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					openBuildPathPropertyPage();
				}
			});
		}
		
		Control[] allChildren= group.getChildren();
		fComplianceFollowsEEControls.addAll(Arrays.asList(allChildren));
		fComplianceFollowsEEControls.removeAll(Arrays.asList(otherChildren));
		otherChildren= allChildren;
		
		
		String label= PreferencesMessages.ComplianceConfigurationBlock_compiler_compliance_label;
		addComboBox(group, label, PREF_COMPLIANCE, values3456, values3456Labels, 0);

		label= PreferencesMessages.ComplianceConfigurationBlock_default_settings_label;
		addCheckBox(group, label, INTR_DEFAULT_COMPLIANCE, defaultUserValues, 0);

		allChildren= group.getChildren();
		fComplianceControls.addAll(Arrays.asList(allChildren));
		fComplianceControls.removeAll(Arrays.asList(otherChildren));
		otherChildren= allChildren;
		
		
		int indent= LayoutUtil.getIndent();

		String[] versions= new String[] { VERSION_CLDC_1_1, VERSION_1_1, VERSION_1_2, VERSION_1_3, VERSION_1_4, VERSION_1_5, VERSION_1_6, VERSION_1_7, VERSION_1_8 };
		String[] versionsLabels= new String[] {
				PreferencesMessages.ComplianceConfigurationBlock_versionCLDC11,
				PreferencesMessages.ComplianceConfigurationBlock_version11,
				PreferencesMessages.ComplianceConfigurationBlock_version12,
				PreferencesMessages.ComplianceConfigurationBlock_version13,
				PreferencesMessages.ComplianceConfigurationBlock_version14,
				PreferencesMessages.ComplianceConfigurationBlock_version15,
				PreferencesMessages.ComplianceConfigurationBlock_version16,
				PreferencesMessages.ComplianceConfigurationBlock_version17,
				PreferencesMessages.ComplianceConfigurationBlock_version18
		};

		boolean showJsr14= ComplianceConfigurationBlock.VERSION_JSR14.equals(getValue(PREF_CODEGEN_TARGET_PLATFORM));
		if (showJsr14) {
			versions= append(versions, ComplianceConfigurationBlock.VERSION_JSR14);
			versionsLabels= append(versionsLabels, ComplianceConfigurationBlock.VERSION_JSR14);
		}

		label= PreferencesMessages.ComplianceConfigurationBlock_codegen_targetplatform_label;
		addComboBox(group, label, PREF_CODEGEN_TARGET_PLATFORM, versions, versionsLabels, indent);

		label= PreferencesMessages.ComplianceConfigurationBlock_source_compatibility_label;
		addComboBox(group, label, PREF_SOURCE_COMPATIBILITY, values3456, values3456Labels, indent);

		String[] errorWarningIgnore= new String[] { ERROR, WARNING, IGNORE };

		String[] errorWarningIgnoreLabels= new String[] {
			PreferencesMessages.ComplianceConfigurationBlock_error,
			PreferencesMessages.ComplianceConfigurationBlock_warning,
			PreferencesMessages.ComplianceConfigurationBlock_ignore
		};

		label= PreferencesMessages.ComplianceConfigurationBlock_pb_assert_as_identifier_label;
		addComboBox(group, label, PREF_PB_ASSERT_AS_IDENTIFIER, errorWarningIgnore, errorWarningIgnoreLabels, indent);

		label= PreferencesMessages.ComplianceConfigurationBlock_pb_enum_as_identifier_label;
		addComboBox(group, label, PREF_PB_ENUM_AS_IDENTIFIER, errorWarningIgnore, errorWarningIgnoreLabels, indent);

		allChildren= group.getChildren();
		fComplianceChildControls.addAll(Arrays.asList(allChildren));
		fComplianceChildControls.removeAll(Arrays.asList(otherChildren));
		

		layout= new GridLayout();
		layout.numColumns= nColumns;

		group= new Group(fControlsComposite, SWT.NONE);
		group.setFont(fControlsComposite.getFont());
		group.setText(PreferencesMessages.ComplianceConfigurationBlock_classfiles_group_label);
		group.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));
		group.setLayout(layout);

		String[] generateValues= new String[] { GENERATE, DO_NOT_GENERATE };
		String[] enableDisableValues= new String[] { ENABLED, DISABLED };

		label= PreferencesMessages.ComplianceConfigurationBlock_variable_attr_label;
		addCheckBox(group, label, PREF_LOCAL_VARIABLE_ATTR, generateValues, 0);

		label= PreferencesMessages.ComplianceConfigurationBlock_line_number_attr_label;
		addCheckBox(group, label, PREF_LINE_NUMBER_ATTR, generateValues, 0);

		label= PreferencesMessages.ComplianceConfigurationBlock_source_file_attr_label;
		addCheckBox(group, label, PREF_SOURCE_FILE_ATTR, generateValues, 0);

		label= PreferencesMessages.ComplianceConfigurationBlock_codegen_unused_local_label;
		addCheckBox(group, label, PREF_CODEGEN_UNUSED_LOCAL, new String[] { PRESERVE, OPTIMIZE_OUT }, 0);

		label= PreferencesMessages.ComplianceConfigurationBlock_codegen_inline_jsr_bytecode_label;
		addCheckBox(group, label, PREF_CODEGEN_INLINE_JSR_BYTECODE, enableDisableValues, 0);
		
		label= PreferencesMessages.ComplianceConfigurationBlock_codegen_method_parameters_attr;
		addCheckBox(group, label, PREF_CODEGEN_METHOD_PARAMETERS_ATTR, generateValues, 0);
		
		Composite infoComposite= new Composite(fControlsComposite, SWT.NONE);
		infoComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		infoComposite.setLayout(new GridLayout(2, false));
		
		fJRE50InfoImage= new Label(infoComposite, SWT.NONE);
		fJRE50InfoImage.setImage(JFaceResources.getImage(Dialog.DLG_IMG_MESSAGE_WARNING));
		GridData gd= new GridData(GridData.BEGINNING, GridData.BEGINNING, false, false);
		fJRE50InfoImage.setLayoutData(gd);
		
		fJRE50InfoText= new Link(infoComposite, SWT.WRAP);
		fJRE50InfoText.setFont(composite.getFont());
		// set a text: not the real one, just for layouting
		fJRE50InfoText.setText(Messages.format(PreferencesMessages.ComplianceConfigurationBlock_jrecompliance_info_project, new String[] { getVersionLabel(VERSION_1_3), getVersionLabel(VERSION_1_3) }));
		fJRE50InfoText.setVisible(false);
		fJRE50InfoText.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
				if ("1".equals(e.text)) { //$NON-NLS-1$
					openJREInstallPreferencePage(false);
				} else if ("2".equals(e.text)) { //$NON-NLS-1$
					openJREInstallPreferencePage(true);
				} else {
					openBuildPathPropertyPage();
				}
			}
			public void widgetSelected(SelectionEvent e) {
				widgetDefaultSelected(e);
			}
		});
		gd= new GridData(GridData.FILL, GridData.FILL, true, true);
		gd.widthHint= fPixelConverter.convertWidthInCharsToPixels(50);
		fJRE50InfoText.setLayoutData(gd);
		validateComplianceStatus();

		return sc1;
	}

	private static String[] append(String[] versions, String version) {
		String[] result= new String[versions.length + 1];
		System.arraycopy(versions, 0, result, 0, versions.length);
		result[versions.length]= version;
		return result;
	}

	protected final void openBuildPathPropertyPage() {
		if (getPreferenceContainer() != null) {
			Map<Object, IClasspathEntry> data= new HashMap<Object, IClasspathEntry>();
			data.put(BuildPathsPropertyPage.DATA_REVEAL_ENTRY, JavaRuntime.getDefaultJREContainerEntry());
			getPreferenceContainer().openPage(BuildPathsPropertyPage.PROP_ID, data);
		}
		validateComplianceStatus();
	}

	protected final void openJREInstallPreferencePage(boolean openEE) {
		String jreID= BuildPathSupport.JRE_PREF_PAGE_ID;
		String eeID= BuildPathSupport.EE_PREF_PAGE_ID;
		String pageId= openEE ? eeID : jreID;
		if (fProject == null && getPreferenceContainer() != null) {
			getPreferenceContainer().openPage(pageId, null);
		} else {
			PreferencesUtil.createPreferenceDialogOn(getShell(), pageId, new String[] { jreID, eeID }, null).open();
		}
		validateComplianceStatus();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(Key changedKey, String oldValue, String newValue) {
		if (!areSettingsEnabled()) {
			return;
		}
		if (changedKey != null) {
			if (INTR_DEFAULT_COMPLIANCE.equals(changedKey)) {
				updateComplianceEnableState();
				updateComplianceDefaultSettings(true, null);
				fComplianceStatus= validateCompliance();
			} else if (INTR_COMPLIANCE_FOLLOWS_EE.equals(changedKey)) {
				setValue(INTR_DEFAULT_COMPLIANCE, DEFAULT_CONF);
				updateComplianceEnableState();
				updateComplianceDefaultSettings(true, null);
				updateControls();
				fComplianceStatus= validateCompliance();
				validateComplianceStatus();
			} else if (PREF_COMPLIANCE.equals(changedKey)) {
			    // set compliance settings to default
			    Object oldDefault= getValue(INTR_DEFAULT_COMPLIANCE);
				boolean rememberOld= USER_CONF.equals(oldDefault);
				updateComplianceDefaultSettings(rememberOld, oldValue);
				fComplianceStatus= validateCompliance();
				validateComplianceStatus();
			} else if (PREF_SOURCE_COMPATIBILITY.equals(changedKey)) {
				updateAssertEnumAsIdentifierEnableState();
				fComplianceStatus= validateCompliance();
			} else if (PREF_CODEGEN_TARGET_PLATFORM.equals(changedKey)) {
				if (VERSION_CLDC_1_1.equals(newValue) && !oldValue.equals(newValue)) {
					String compliance= getValue(PREF_COMPLIANCE);
					String source= getValue(PREF_SOURCE_COMPATIBILITY);
					if (!JavaModelUtil.isVersionLessThan(compliance, VERSION_1_5)) {
						setValue(PREF_COMPLIANCE, VERSION_1_4);
					}
					if (!VERSION_1_3.equals(source)) {
						setValue(PREF_SOURCE_COMPATIBILITY, VERSION_1_3);
					}
				}
				updateControls();
				updateInlineJSREnableState();
				updateStoreMethodParamNamesEnableState();
				updateAssertEnumAsIdentifierEnableState();
				fComplianceStatus= validateCompliance();
			} else if (PREF_PB_ENUM_AS_IDENTIFIER.equals(changedKey) ||
					PREF_PB_ASSERT_AS_IDENTIFIER.equals(changedKey)) {
				fComplianceStatus= validateCompliance();
			} else {
				return;
			}
		} else {
			updateComplianceFollowsEE();
			updateControls();
			updateComplianceEnableState();
			updateAssertEnumAsIdentifierEnableState();
			updateInlineJSREnableState();
			updateStoreMethodParamNamesEnableState();
			fComplianceStatus= validateCompliance();
			validateComplianceStatus();
		}
		fContext.statusChanged(fComplianceStatus);
	}
	
	public void refreshComplianceSettings() {
		if (fProject != null) {
			if (fOriginalStoredCompliance == null) {
				fOriginalStoredCompliance= new String[] { // caution: order depends on IDX_* constants
						getOriginalStoredValue(PREF_PB_ASSERT_AS_IDENTIFIER),
						getOriginalStoredValue(PREF_PB_ENUM_AS_IDENTIFIER),
						getOriginalStoredValue(PREF_SOURCE_COMPATIBILITY),
						getOriginalStoredValue(PREF_CODEGEN_TARGET_PLATFORM),
						getOriginalStoredValue(PREF_COMPLIANCE),
						getOriginalStoredValue(PREF_CODEGEN_INLINE_JSR_BYTECODE),
						getOriginalStoredValue(PREF_CODEGEN_METHOD_PARAMETERS_ATTR)
					};
				
			} else {
				String[] storedCompliance= new String[] {
						getOriginalStoredValue(PREF_PB_ASSERT_AS_IDENTIFIER),
						getOriginalStoredValue(PREF_PB_ENUM_AS_IDENTIFIER),
						getOriginalStoredValue(PREF_SOURCE_COMPATIBILITY),
						getOriginalStoredValue(PREF_CODEGEN_TARGET_PLATFORM),
						getOriginalStoredValue(PREF_COMPLIANCE),
						getOriginalStoredValue(PREF_CODEGEN_INLINE_JSR_BYTECODE),
						getOriginalStoredValue(PREF_CODEGEN_METHOD_PARAMETERS_ATTR)
					};
				if (!Arrays.equals(fOriginalStoredCompliance, storedCompliance)) {
					// compliance changed on disk -> override user modifications
					
					fOriginalStoredCompliance= storedCompliance;
					
					setValue(PREF_PB_ASSERT_AS_IDENTIFIER, storedCompliance[IDX_ASSERT_AS_IDENTIFIER]);
					setValue(PREF_PB_ENUM_AS_IDENTIFIER, storedCompliance[IDX_ENUM_AS_IDENTIFIER]);
					setValue(PREF_SOURCE_COMPATIBILITY, storedCompliance[IDX_SOURCE_COMPATIBILITY]);
					setValue(PREF_CODEGEN_TARGET_PLATFORM, storedCompliance[IDX_CODEGEN_TARGET_PLATFORM]);
					setValue(PREF_COMPLIANCE, storedCompliance[IDX_COMPLIANCE]);
					setValue(PREF_CODEGEN_INLINE_JSR_BYTECODE, storedCompliance[IDX_INLINE_JSR_BYTECODE]);
					setValue(PREF_CODEGEN_METHOD_PARAMETERS_ATTR, storedCompliance[IDX_METHOD_PARAMETERS_ATTR]);
					
				}
				
				updateComplianceFollowsEE();
				updateControls();
				updateComplianceEnableState();
				validateComplianceStatus();
				updateInlineJSREnableState();
				updateStoreMethodParamNamesEnableState();
			}
		}
	}

	private void validateComplianceStatus() {
		if (fJRE50InfoText != null && !fJRE50InfoText.isDisposed()) {
			boolean isVisible= false;
			String compliance= getStoredValue(PREF_COMPLIANCE); // get actual value
			IVMInstall install= null;
			if (fProject != null) { // project specific settings: only test if a 50 JRE is installed
				try {
					install= JavaRuntime.getVMInstall(JavaCore.create(fProject));
				} catch (CoreException e) {
					JavaPlugin.log(e);
				}
			} else {
				install= JavaRuntime.getDefaultVMInstall();
			}
			if (install instanceof IVMInstall2) {
				String compilerCompliance= JavaModelUtil.getCompilerCompliance((IVMInstall2) install, compliance);
				if (!compilerCompliance.equals(compliance)) { // Discourage using compiler with version other than compliance
					String[] args= { getVersionLabel(compliance), getVersionLabel(compilerCompliance) };
					if (fProject == null) {
						fJRE50InfoText.setText(Messages.format(PreferencesMessages.ComplianceConfigurationBlock_jrecompliance_info, args));
					} else {
						fJRE50InfoText.setText(Messages.format(PreferencesMessages.ComplianceConfigurationBlock_jrecompliance_info_project, args));
					}
					isVisible= true;
				}
			}
			
//			String source= getValue(PREF_SOURCE_COMPATIBILITY);
//			if (VERSION_1_8.equals(source)) {
//				fJRE50InfoText.setText("This is an implementation of an early-draft specification developed under the Java Community Process (JCP) and is made available for testing and evaluation purposes only. The code is not compatible with any specification of the JCP."); //$NON-NLS-1$
//				isVisible= true;
//			}
			
			fJRE50InfoText.setVisible(isVisible);
			fJRE50InfoImage.setImage(isVisible ? JFaceResources.getImage(Dialog.DLG_IMG_MESSAGE_WARNING) : null);
			fJRE50InfoImage.getParent().layout();
		}
	}

	private String getVersionLabel(String version) {
		return BasicElementLabels.getVersionName(version);
	}


	private IStatus validateCompliance() {
		StatusInfo status= new StatusInfo();
		String compliance= getValue(PREF_COMPLIANCE);
		String source= getValue(PREF_SOURCE_COMPATIBILITY);
		String target= getValue(PREF_CODEGEN_TARGET_PLATFORM);
		
		if (ComplianceConfigurationBlock.VERSION_JSR14.equals(target)) {
			target= source;
		}

		// compliance must not be smaller than source or target
		if (JavaModelUtil.isVersionLessThan(compliance, source)) {
			status.setError(PreferencesMessages.ComplianceConfigurationBlock_src_greater_compliance);
			return status;
		}

		if (JavaModelUtil.isVersionLessThan(compliance, target)) {
			status.setError(PreferencesMessages.ComplianceConfigurationBlock_classfile_greater_compliance);
			return status;
		}

		if (VERSION_CLDC_1_1.equals(target)) {
			if (!VERSION_1_3.equals(source) || !JavaModelUtil.isVersionLessThan(compliance, VERSION_1_5)) {
				status.setError(PreferencesMessages.ComplianceConfigurationBlock_cldc11_requires_source13_compliance_se14);
				return status;
			}
		}

		// target must not be smaller than source
		if (!VERSION_1_3.equals(source) && JavaModelUtil.isVersionLessThan(target, source)) {
			status.setError(PreferencesMessages.ComplianceConfigurationBlock_classfile_greater_source);
			return status;
		}

		return status;
	}


	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#useProjectSpecificSettings(boolean)
	 */
	@Override
	public void useProjectSpecificSettings(boolean enable) {
		super.useProjectSpecificSettings(enable);
		validateComplianceStatus();
	}

	private void updateComplianceFollowsEE() {
		if (fProject != null) {
			String complianceFollowsEE= DISABLED;
			IExecutionEnvironment ee= getEE();
			String label;
			if (ee != null) {
				complianceFollowsEE= getComplianceFollowsEE(ee);
				label= Messages.format(PreferencesMessages.ComplianceConfigurationBlock_compliance_follows_EE_with_EE_label, ee.getId());
			} else {
				label= PreferencesMessages.ComplianceConfigurationBlock_compliance_follows_EE_label;
			}
			Link checkBoxLink= getCheckBoxLink(INTR_COMPLIANCE_FOLLOWS_EE);
			if (checkBoxLink != null) {
				checkBoxLink.setText(label);
			}
			setValue(INTR_COMPLIANCE_FOLLOWS_EE, complianceFollowsEE);
		}
	}

	private void updateComplianceEnableState() {
		boolean enableComplianceControls= true;
		if (fProject != null) {
			boolean hasProjectSpecificOptions= hasProjectSpecificOptions(fProject);
			String complianceFollowsEE= getValue(INTR_COMPLIANCE_FOLLOWS_EE);
			updateCheckBox(getCheckBox(INTR_COMPLIANCE_FOLLOWS_EE));
			boolean enableComplianceFollowsEE= hasProjectSpecificOptions && ! DISABLED.equals(complianceFollowsEE); // is default or user
			updateControlsEnableState(fComplianceFollowsEEControls, enableComplianceFollowsEE);
		
			enableComplianceControls= hasProjectSpecificOptions && ! DEFAULT_CONF.equals(complianceFollowsEE); // is disabled or user
			updateControlsEnableState(fComplianceControls, enableComplianceControls);
		}
		
		boolean enableComplianceChildren= enableComplianceControls && checkValue(INTR_DEFAULT_COMPLIANCE, USER_CONF);
		updateControlsEnableState(fComplianceChildControls, enableComplianceChildren);
	}

	private void updateControlsEnableState(List<Control> controls, boolean enable) {
		for (int i= controls.size() - 1; i >= 0; i--) {
			Control curr= controls.get(i);
			if (curr instanceof Composite) {
				updateControlsEnableState(Arrays.asList(((Composite)curr).getChildren()), enable);
			}
			curr.setEnabled(enable);
		}
	}

	private void updateAssertEnumAsIdentifierEnableState() {
		if (checkValue(INTR_DEFAULT_COMPLIANCE, USER_CONF)) {
			String compatibility= getValue(PREF_SOURCE_COMPATIBILITY);

			boolean isLessThan14= VERSION_1_3.equals(compatibility);
			updateRememberedComplianceOption(PREF_PB_ASSERT_AS_IDENTIFIER, IDX_ASSERT_AS_IDENTIFIER, isLessThan14);

			boolean isLessThan15= isLessThan14 || VERSION_1_4.equals(compatibility);
			updateRememberedComplianceOption(PREF_PB_ENUM_AS_IDENTIFIER, IDX_ENUM_AS_IDENTIFIER, isLessThan15);
		}
	}

	private void updateRememberedComplianceOption(Key prefKey, int idx, boolean enabled) {
		Combo combo= getComboBox(prefKey);
		combo.setEnabled(enabled);

		if (!enabled) {
			String val= getValue(prefKey);
			if (!ERROR.equals(val)) {
				setValue(prefKey, ERROR);
				updateCombo(combo);
				fRememberedUserCompliance[idx]= val;
			}
		} else {
			String val= fRememberedUserCompliance[idx];
			if (!ERROR.equals(val)) {
				setValue(prefKey, val);
				updateCombo(combo);
			}
		}
	}

	private void updateInlineJSREnableState() {
		String target= getValue(PREF_CODEGEN_TARGET_PLATFORM);

		boolean enabled= JavaModelUtil.isVersionLessThan(target, VERSION_1_5);
		Button checkBox= getCheckBox(PREF_CODEGEN_INLINE_JSR_BYTECODE);
		boolean wasCheckBoxEnabled= checkBox.isEnabled();
		checkBox.setEnabled(enabled);

		if (!enabled) {
			String val= getValue(PREF_CODEGEN_INLINE_JSR_BYTECODE);
			if (wasCheckBoxEnabled)
				fRememberedUserCompliance[IDX_INLINE_JSR_BYTECODE]= val;

			if (!ENABLED.equals(val)) {
				setValue(PREF_CODEGEN_INLINE_JSR_BYTECODE, ENABLED);
				updateCheckBox(checkBox);
			}
		} else {
			if (!wasCheckBoxEnabled) {
				String val= fRememberedUserCompliance[IDX_INLINE_JSR_BYTECODE];
				if (!ENABLED.equals(val)) {
					setValue(PREF_CODEGEN_INLINE_JSR_BYTECODE, val);
					updateCheckBox(checkBox);
				}
			}
		}
	}

	private void updateStoreMethodParamNamesEnableState() {
		String target= getValue(PREF_CODEGEN_TARGET_PLATFORM);
		boolean enabled= JavaModelUtil.is18OrHigher(target);
		Button checkBox= getCheckBox(PREF_CODEGEN_METHOD_PARAMETERS_ATTR);
		boolean wasCheckBoxEnabled= checkBox.isEnabled();
		checkBox.setEnabled(enabled);

		if (enabled) {
			if (!wasCheckBoxEnabled) {
				String val= fRememberedUserCompliance[IDX_METHOD_PARAMETERS_ATTR];
				if (GENERATE.equals(val)) {
					setValue(PREF_CODEGEN_METHOD_PARAMETERS_ATTR, val);
					updateCheckBox(checkBox);
				}
			}
		} else {
			String val= getValue(PREF_CODEGEN_METHOD_PARAMETERS_ATTR);
			if (wasCheckBoxEnabled)
				fRememberedUserCompliance[IDX_METHOD_PARAMETERS_ATTR]= val;

			if (GENERATE.equals(val)) {
				setValue(PREF_CODEGEN_METHOD_PARAMETERS_ATTR, DO_NOT_GENERATE);
				updateCheckBox(checkBox);
			}
		}
	}

	/**
	 * Sets the default compliance values derived from the chosen level or restores the user
	 * compliance settings.
	 * 
	 * @param rememberOld if <code>true</code>, the current compliance settings are remembered as
	 *            user settings. If <code>false</code>, overwrite the current settings.
	 * @param oldComplianceLevel the previous compliance level
	 */
	private void updateComplianceDefaultSettings(boolean rememberOld, String oldComplianceLevel) {
		String assertAsId, enumAsId, source, target;
		boolean isDefault= checkValue(INTR_DEFAULT_COMPLIANCE, DEFAULT_CONF);
		boolean isFollowEE= checkValue(INTR_COMPLIANCE_FOLLOWS_EE, DEFAULT_CONF);
		String complianceLevel= getValue(PREF_COMPLIANCE);

		if (isDefault || isFollowEE) {
			if (rememberOld) {
				if (oldComplianceLevel == null) {
					oldComplianceLevel= complianceLevel;
				}

				fRememberedUserCompliance[IDX_ASSERT_AS_IDENTIFIER]= getValue(PREF_PB_ASSERT_AS_IDENTIFIER);
				fRememberedUserCompliance[IDX_ENUM_AS_IDENTIFIER]= getValue(PREF_PB_ENUM_AS_IDENTIFIER);
				fRememberedUserCompliance[IDX_SOURCE_COMPATIBILITY]= getValue(PREF_SOURCE_COMPATIBILITY);
				fRememberedUserCompliance[IDX_CODEGEN_TARGET_PLATFORM]= getValue(PREF_CODEGEN_TARGET_PLATFORM);
				fRememberedUserCompliance[IDX_COMPLIANCE]= oldComplianceLevel;
			}
			
			if (isFollowEE) {
				IExecutionEnvironment ee= getEE();
				Map<String, String> eeOptions= BuildPathSupport.getEEOptions(ee);
				if (eeOptions == null)
					return;
				
				assertAsId= eeOptions.get(PREF_PB_ASSERT_AS_IDENTIFIER.getName());
				enumAsId= eeOptions.get(PREF_PB_ENUM_AS_IDENTIFIER.getName());
				source= eeOptions.get(PREF_SOURCE_COMPATIBILITY.getName());
				target= eeOptions.get(PREF_CODEGEN_TARGET_PLATFORM.getName());
				
				setValue(PREF_COMPLIANCE, eeOptions.get(PREF_COMPLIANCE.getName()));
				String inlineJSR= eeOptions.get(PREF_CODEGEN_INLINE_JSR_BYTECODE.getName());
				if (inlineJSR != null) {
					setValue(PREF_CODEGEN_INLINE_JSR_BYTECODE, inlineJSR);
				}
				
			} else {
				//TODO: use JavaModelUtil.setComplianceOptions(new HashMap(), complianceLevel);
				if (VERSION_1_4.equals(complianceLevel)) {
					assertAsId= WARNING;
					enumAsId= WARNING;
					source= VERSION_1_3;
					target= VERSION_1_2;
				} else if (VERSION_1_5.equals(complianceLevel)) {
					assertAsId= ERROR;
					enumAsId= ERROR;
					source= VERSION_1_5;
					target= VERSION_1_5;
				} else if (VERSION_1_6.equals(complianceLevel)) {
					assertAsId= ERROR;
					enumAsId= ERROR;
					source= VERSION_1_6;
					target= VERSION_1_6;
				} else if (VERSION_1_7.equals(complianceLevel)) {
					assertAsId= ERROR;
					enumAsId= ERROR;
					source= VERSION_1_7;
					target= VERSION_1_7;
				} else if (VERSION_1_8.equals(complianceLevel)) {
					assertAsId= ERROR;
					enumAsId= ERROR;
					source= VERSION_1_8;
					target= VERSION_1_8;
				} else {
					assertAsId= IGNORE;
					enumAsId= IGNORE;
					source= VERSION_1_3;
					target= VERSION_1_1;
				}
			}
		} else {
			if (rememberOld && complianceLevel.equals(fRememberedUserCompliance[IDX_COMPLIANCE])) {
				assertAsId= fRememberedUserCompliance[IDX_ASSERT_AS_IDENTIFIER];
				enumAsId= fRememberedUserCompliance[IDX_ENUM_AS_IDENTIFIER];
				source= fRememberedUserCompliance[IDX_SOURCE_COMPATIBILITY];
				target= fRememberedUserCompliance[IDX_CODEGEN_TARGET_PLATFORM];
			} else {
				updateInlineJSREnableState();
				updateAssertEnumAsIdentifierEnableState();
				updateStoreMethodParamNamesEnableState();
				return;
			}
		}
		setValue(PREF_PB_ASSERT_AS_IDENTIFIER, assertAsId);
		setValue(PREF_PB_ENUM_AS_IDENTIFIER, enumAsId);
		setValue(PREF_SOURCE_COMPATIBILITY, source);
		setValue(PREF_CODEGEN_TARGET_PLATFORM, target);
		updateControls();
		updateInlineJSREnableState();
		updateAssertEnumAsIdentifierEnableState();
		updateStoreMethodParamNamesEnableState();
	}

	/**
	 * Evaluate if the current compliance setting correspond to a default setting.
	 * 
	 * @return {@link #DEFAULT_CONF} or {@link #USER_CONF}
	 */
	private String getCurrentCompliance() {
		Object complianceLevel= getValue(PREF_COMPLIANCE);
		//TODO: use JavaModelUtil.setComplianceOptions(new HashMap(), complianceLevel);
		if ((VERSION_1_3.equals(complianceLevel)
				&& IGNORE.equals(getValue(PREF_PB_ASSERT_AS_IDENTIFIER))
				&& IGNORE.equals(getValue(PREF_PB_ENUM_AS_IDENTIFIER))
				&& VERSION_1_3.equals(getValue(PREF_SOURCE_COMPATIBILITY))
				&& VERSION_1_1.equals(getValue(PREF_CODEGEN_TARGET_PLATFORM)))
			|| (VERSION_1_4.equals(complianceLevel)
				&& WARNING.equals(getValue(PREF_PB_ASSERT_AS_IDENTIFIER))
				&& WARNING.equals(getValue(PREF_PB_ENUM_AS_IDENTIFIER))
				&& VERSION_1_3.equals(getValue(PREF_SOURCE_COMPATIBILITY))
				&& VERSION_1_2.equals(getValue(PREF_CODEGEN_TARGET_PLATFORM)))
			|| (VERSION_1_5.equals(complianceLevel)
				&& ERROR.equals(getValue(PREF_PB_ASSERT_AS_IDENTIFIER))
				&& ERROR.equals(getValue(PREF_PB_ENUM_AS_IDENTIFIER))
				&& VERSION_1_5.equals(getValue(PREF_SOURCE_COMPATIBILITY))
				&& VERSION_1_5.equals(getValue(PREF_CODEGEN_TARGET_PLATFORM)))
			|| (VERSION_1_6.equals(complianceLevel)
				&& ERROR.equals(getValue(PREF_PB_ASSERT_AS_IDENTIFIER))
				&& ERROR.equals(getValue(PREF_PB_ENUM_AS_IDENTIFIER))
				&& VERSION_1_6.equals(getValue(PREF_SOURCE_COMPATIBILITY))
				&& VERSION_1_6.equals(getValue(PREF_CODEGEN_TARGET_PLATFORM)))
			|| (VERSION_1_7.equals(complianceLevel)
				&& ERROR.equals(getValue(PREF_PB_ASSERT_AS_IDENTIFIER))
				&& ERROR.equals(getValue(PREF_PB_ENUM_AS_IDENTIFIER))
				&& VERSION_1_7.equals(getValue(PREF_SOURCE_COMPATIBILITY))
				&& VERSION_1_7.equals(getValue(PREF_CODEGEN_TARGET_PLATFORM)))
			|| (VERSION_1_8.equals(complianceLevel)
					&& ERROR.equals(getValue(PREF_PB_ASSERT_AS_IDENTIFIER))
					&& ERROR.equals(getValue(PREF_PB_ENUM_AS_IDENTIFIER))
					&& VERSION_1_8.equals(getValue(PREF_SOURCE_COMPATIBILITY))
					&& VERSION_1_8.equals(getValue(PREF_CODEGEN_TARGET_PLATFORM)))) {
			return DEFAULT_CONF;
		}
		return USER_CONF;
	}

	private IExecutionEnvironment getEE() {
		if (fProject == null)
			return null;
		
		try {
			IClasspathEntry[] entries= JavaCore.create(fProject).getRawClasspath();
			for (int i= 0; i < entries.length; i++) {
				IClasspathEntry entry= entries[i];
				if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
					String eeId= JavaRuntime.getExecutionEnvironmentId(entry.getPath());
					if (eeId != null) {
						return JavaRuntime.getExecutionEnvironmentsManager().getEnvironment(eeId);
					}
				}
			}
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
		return null;
	}

	/**
	 * Evaluate if the builds path contains an execution environment and the current compliance
	 * settings follow the EE options.
	 * 
	 * @param ee the EE, or <code>null</code> if none available
	 * @return {@link #DEFAULT_CONF} if the compliance follows the EE, or {@link #USER_CONF} if the
	 *         settings differ, or {@link #DISABLED} if there's no EE at all
	 */
	private String getComplianceFollowsEE(IExecutionEnvironment ee) {
		Map<String, String> options= BuildPathSupport.getEEOptions(ee);
		if (options == null)
			return DISABLED;
		
		return checkDefaults(PREFS_COMPLIANCE, options);
	}

	private String checkDefaults(Key[] keys, Map<String, String> options) {
		for (int i= 0; i < keys.length; i++) {
			Key key= keys[i];
			Object option= options.get(key.getName());
			if (!checkValue(key, (String)option))
				return USER_CONF;
		}
		return DEFAULT_CONF;
	}
	
	@Override
	protected String[] getFullBuildDialogStrings(boolean workspaceSettings) {
		String title= PreferencesMessages.ComplianceConfigurationBlock_needsbuild_title;
		String message;
		if (workspaceSettings) {
			message= PreferencesMessages.ComplianceConfigurationBlock_needsfullbuild_message;
		} else {
			message= PreferencesMessages.ComplianceConfigurationBlock_needsprojectbuild_message;
		}
		return new String[] { title, message };
	}

	/**
	 * Sets the default compiler compliance options based on the current default JRE in the
	 * workspace.
	 * 
	 * @since 3.5
	 */
	private void setDefaultCompilerComplianceValues() {
		IVMInstall defaultVMInstall= JavaRuntime.getDefaultVMInstall();
		if (defaultVMInstall instanceof IVMInstall2 && isOriginalDefaultCompliance()) {
			String complianceLevel= JavaModelUtil.getCompilerCompliance((IVMInstall2)defaultVMInstall, JavaCore.VERSION_1_4);
			Map<String, String> complianceOptions= new HashMap<String, String>();
			JavaModelUtil.setComplianceOptions(complianceOptions, complianceLevel);
			setDefaultValue(PREF_COMPLIANCE, complianceOptions.get(PREF_COMPLIANCE.getName()));
			setDefaultValue(PREF_PB_ASSERT_AS_IDENTIFIER, complianceOptions.get(PREF_PB_ASSERT_AS_IDENTIFIER.getName()));
			setDefaultValue(PREF_PB_ENUM_AS_IDENTIFIER, complianceOptions.get(PREF_PB_ENUM_AS_IDENTIFIER.getName()));
			setDefaultValue(PREF_SOURCE_COMPATIBILITY, complianceOptions.get(PREF_SOURCE_COMPATIBILITY.getName()));
			setDefaultValue(PREF_CODEGEN_TARGET_PLATFORM, complianceOptions.get(PREF_CODEGEN_TARGET_PLATFORM.getName()));
		}
	}

	/**
	 * Tells whether the compliance option is the same as the original default.
	 * 
	 * @return <code>true</code> if the compliance is the same as the original default
	 * @since 3.6
	 */
	private static final boolean isOriginalDefaultCompliance() {
		Hashtable<String, String> options= JavaCore.getDefaultOptions();
		Preferences bundleDefaults= BundleDefaultsScope.INSTANCE.getNode(JavaCore.PLUGIN_ID);

		return equals(JavaCore.COMPILER_COMPLIANCE, bundleDefaults, options)
				&& equals(JavaCore.COMPILER_SOURCE, bundleDefaults, options)
				&& equals(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, bundleDefaults, options)
				&& equals(JavaCore.COMPILER_PB_ASSERT_IDENTIFIER, bundleDefaults, options)
				&& equals(JavaCore.COMPILER_PB_ENUM_IDENTIFIER, bundleDefaults, options);
	}

	/**
	 * Returns whether the option for the given key is the same in the map and the preferences.
	 * 
	 * @param key the key of option to test
	 * @param preferences the preferences
	 * @param map the map
	 * @return <code>true</code> if the options are the same in both maps
	 * @since 3.6
	 */
	private static boolean equals(String key, Preferences preferences, Map<String, String> map) {
		String dummy= new String();
		String defaultValue= preferences.get(key, dummy);
		return defaultValue != null && defaultValue != dummy
				? map.containsKey(key) && equals(defaultValue, map.get(key))
				: !map.containsKey(key);
	}

	/**
	 * Returns whether the objects are equal.
	 * 
	 * @param o1 an object
	 * @param o2 an object
	 * @return <code>true</code> if the two objects are equal
	 * @since 3.6
	 */
	private static boolean equals(Object o1, Object o2) {
		return o1 == null ? o2 == null : o1.equals(o2);
	}

}

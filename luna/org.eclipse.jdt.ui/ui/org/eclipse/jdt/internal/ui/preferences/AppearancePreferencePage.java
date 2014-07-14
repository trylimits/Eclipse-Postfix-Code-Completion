/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Guven Demir <guven.internet+eclipse@gmail.com> - [package explorer] Alternative package name shortening: abbreviation - https://bugs.eclipse.org/bugs/show_bug.cgi?id=299514
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.JFaceResources;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLabelComposer;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.Separator;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.TextBoxDialogField;


public class AppearancePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	private static final String SHOW_CU_CHILDREN= PreferenceConstants.SHOW_CU_CHILDREN;
	private static final String PREF_METHOD_RETURNTYPE= PreferenceConstants.APPEARANCE_METHOD_RETURNTYPE;
	private static final String PREF_METHOD_TYPEPARAMETERS= PreferenceConstants.APPEARANCE_METHOD_TYPEPARAMETERS;
	private static final String PREF_COMPRESS_PACKAGE_NAMES= PreferenceConstants.APPEARANCE_COMPRESS_PACKAGE_NAMES;
	private static final String PREF_ABBREVIATE_PACKAGE_NAMES= PreferenceConstants.APPEARANCE_ABBREVIATE_PACKAGE_NAMES;
	private static final String PREF_PKG_NAME_PATTERN_FOR_PKG_VIEW= PreferenceConstants.APPEARANCE_PKG_NAME_PATTERN_FOR_PKG_VIEW;
	private static final String PREF_PKG_NAME_ABBREVIATION_PATTERN_FOR_PKG_VIEW= PreferenceConstants.APPEARANCE_PKG_NAME_ABBREVIATION_PATTERN_FOR_PKG_VIEW;
	private static final String STACK_BROWSING_VIEWS_VERTICALLY= PreferenceConstants.BROWSING_STACK_VERTICALLY;
	private static final String PREF_FOLD_PACKAGES_IN_PACKAGE_EXPLORER= PreferenceConstants.APPEARANCE_FOLD_PACKAGES_IN_PACKAGE_EXPLORER;
	private static final String PREF_CATEGORY= PreferenceConstants.APPEARANCE_CATEGORY;

	private SelectionButtonDialogField fShowMethodReturnType;
	private SelectionButtonDialogField fShowCategory;
	private SelectionButtonDialogField fCompressPackageNames;
	private SelectionButtonDialogField fAbbreviatePackageNames;
	private SelectionButtonDialogField fStackBrowsingViewsVertically;
	private SelectionButtonDialogField fShowMembersInPackageView;
	private StringDialogField fPackageNamePattern;
	private StringDialogField fAbbreviatePackageNamePattern;
	private SelectionButtonDialogField fFoldPackagesInPackageExplorer;
	private SelectionButtonDialogField fShowMethodTypeParameters;

	public AppearancePreferencePage() {
		setPreferenceStore(JavaPlugin.getDefault().getPreferenceStore());
		setDescription(PreferencesMessages.AppearancePreferencePage_description);

		IDialogFieldListener listener= new IDialogFieldListener() {
			public void dialogFieldChanged(DialogField field) {
				doDialogFieldChanged(field);
			}
		};

		fShowMethodReturnType= new SelectionButtonDialogField(SWT.CHECK);
		fShowMethodReturnType.setDialogFieldListener(listener);
		fShowMethodReturnType.setLabelText(PreferencesMessages.AppearancePreferencePage_methodreturntype_label);

		fShowMethodTypeParameters= new SelectionButtonDialogField(SWT.CHECK);
		fShowMethodTypeParameters.setDialogFieldListener(listener);
		fShowMethodTypeParameters.setLabelText(PreferencesMessages.AppearancePreferencePage_methodtypeparams_label);

		fShowCategory= new SelectionButtonDialogField(SWT.CHECK);
		fShowCategory.setDialogFieldListener(listener);
		fShowCategory.setLabelText(PreferencesMessages.AppearancePreferencePage_showCategory_label);

		fShowMembersInPackageView= new SelectionButtonDialogField(SWT.CHECK);
		fShowMembersInPackageView.setDialogFieldListener(listener);
		fShowMembersInPackageView.setLabelText(PreferencesMessages.AppearancePreferencePage_showMembersInPackagesView);

		fStackBrowsingViewsVertically= new SelectionButtonDialogField(SWT.CHECK);
		fStackBrowsingViewsVertically.setDialogFieldListener(listener);
		fStackBrowsingViewsVertically.setLabelText(PreferencesMessages.AppearancePreferencePage_stackViewsVerticallyInTheJavaBrowsingPerspective);

		fFoldPackagesInPackageExplorer= new SelectionButtonDialogField(SWT.CHECK);
		fFoldPackagesInPackageExplorer.setDialogFieldListener(listener);
		fFoldPackagesInPackageExplorer.setLabelText(PreferencesMessages.AppearancePreferencePage_foldEmptyPackages);

		fCompressPackageNames= new SelectionButtonDialogField(SWT.CHECK);
		fCompressPackageNames.setDialogFieldListener(listener);
		fCompressPackageNames.setLabelText(PreferencesMessages.AppearancePreferencePage_pkgNamePatternEnable_label);

		fPackageNamePattern= new StringDialogField();
		fPackageNamePattern.setDialogFieldListener(listener);
		fPackageNamePattern.setLabelText(PreferencesMessages.AppearancePreferencePage_pkgNamePattern_label);

		fAbbreviatePackageNames= new SelectionButtonDialogField(SWT.CHECK);
		fAbbreviatePackageNames.setDialogFieldListener(listener);
		fAbbreviatePackageNames.setLabelText(PreferencesMessages.AppearancePreferencePage_pkgNamePatternAbbreviateEnable_label);
		
		fAbbreviatePackageNamePattern= new TextBoxDialogField();
		fAbbreviatePackageNamePattern.setDialogFieldListener(listener);
		fAbbreviatePackageNamePattern.setLabelText(PreferencesMessages.AppearancePreferencePage_pkgNamePatternAbbreviate_label);
	}

	private void initFields() {
		IPreferenceStore prefs= getPreferenceStore();
		fShowMethodReturnType.setSelection(prefs.getBoolean(PREF_METHOD_RETURNTYPE));
		fShowMethodTypeParameters.setSelection(prefs.getBoolean(PREF_METHOD_TYPEPARAMETERS));
		fShowMembersInPackageView.setSelection(prefs.getBoolean(SHOW_CU_CHILDREN));
		fShowCategory.setSelection(prefs.getBoolean(PREF_CATEGORY));
		fStackBrowsingViewsVertically.setSelection(prefs.getBoolean(STACK_BROWSING_VIEWS_VERTICALLY));
		fPackageNamePattern.setText(prefs.getString(PREF_PKG_NAME_PATTERN_FOR_PKG_VIEW));
		fCompressPackageNames.setSelection(prefs.getBoolean(PREF_COMPRESS_PACKAGE_NAMES));
		fPackageNamePattern.setEnabled(fCompressPackageNames.isSelected());
		fAbbreviatePackageNamePattern.setText(prefs.getString(PREF_PKG_NAME_ABBREVIATION_PATTERN_FOR_PKG_VIEW));
		fAbbreviatePackageNames.setSelection(prefs.getBoolean(PREF_ABBREVIATE_PACKAGE_NAMES));
		doDialogFieldChanged(fAbbreviatePackageNames);
		fAbbreviatePackageNamePattern.setEnabled(fAbbreviatePackageNames.isSelected());
		fFoldPackagesInPackageExplorer.setSelection(prefs.getBoolean(PREF_FOLD_PACKAGES_IN_PACKAGE_EXPLORER));
	}

	/*
	 * @see PreferencePage#createControl(Composite)
	 */
	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.APPEARANCE_PREFERENCE_PAGE);
	}

	/*
	 * @see PreferencePage#createContents(Composite)
	 */
	@Override
	protected Control createContents(Composite parent) {
		initializeDialogUnits(parent);
		int nColumns= 1;

		Composite result= new Composite(parent, SWT.NONE);
		result.setFont(parent.getFont());

		GridLayout layout= new GridLayout();
		layout.marginHeight= convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		layout.marginWidth= 0;
		layout.numColumns= nColumns;
		result.setLayout(layout);

		fShowMethodReturnType.doFillIntoGrid(result, nColumns);
		fShowMethodTypeParameters.doFillIntoGrid(result, nColumns);
		fShowCategory.doFillIntoGrid(result, nColumns);
		fShowMembersInPackageView.doFillIntoGrid(result, nColumns);
		fFoldPackagesInPackageExplorer.doFillIntoGrid(result, nColumns);

		new Separator().doFillIntoGrid(result, nColumns);

		fCompressPackageNames.doFillIntoGrid(result, nColumns);
		fPackageNamePattern.doFillIntoGrid(result, 2);
		LayoutUtil.setHorizontalIndent(fPackageNamePattern.getLabelControl(null));
		Text packageNamePatternControl= fPackageNamePattern.getTextControl(null);
		LayoutUtil.setHorizontalIndent(packageNamePatternControl);
		LayoutUtil.setHorizontalGrabbing(packageNamePatternControl);
		LayoutUtil.setWidthHint(fPackageNamePattern.getLabelControl(null), convertWidthInCharsToPixels(65));

		new Separator().doFillIntoGrid(result, nColumns);
		fAbbreviatePackageNames.doFillIntoGrid(result, nColumns);
		fAbbreviatePackageNamePattern.doFillIntoGrid(result, 2);
		LayoutUtil.setHorizontalIndent(fAbbreviatePackageNamePattern.getLabelControl(null));
		Text abbreviatePackageNamePatternControl= fAbbreviatePackageNamePattern.getTextControl(null);
		LayoutUtil.setHorizontalIndent(abbreviatePackageNamePatternControl);
		LayoutUtil.setHorizontalGrabbing(abbreviatePackageNamePatternControl);
		LayoutUtil.setWidthHint(fAbbreviatePackageNamePattern.getLabelControl(null), convertWidthInCharsToPixels(65));
		LayoutUtil.setVerticalGrabbing(abbreviatePackageNamePatternControl);
		LayoutUtil.setHeightHint(abbreviatePackageNamePatternControl, convertHeightInCharsToPixels(3));

		new Separator().doFillIntoGrid(result, nColumns);
		fStackBrowsingViewsVertically.doFillIntoGrid(result, nColumns);

		String noteTitle= PreferencesMessages.AppearancePreferencePage_note;
		String noteMessage= PreferencesMessages.AppearancePreferencePage_preferenceOnlyEffectiveForNewPerspectives;
		Composite noteControl= createNoteComposite(JFaceResources.getDialogFont(), result, noteTitle, noteMessage);
		GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalSpan= 2;
		gd.horizontalIndent= LayoutUtil.getIndent();
		noteControl.setLayoutData(gd);

		initFields();

		Dialog.applyDialogFont(result);
		return result;
	}

	private void doDialogFieldChanged(DialogField field) {
		if (field == fCompressPackageNames)
			fPackageNamePattern.setEnabled(fCompressPackageNames.isSelected());

		if (field == fAbbreviatePackageNames)
			fAbbreviatePackageNamePattern.setEnabled(fAbbreviatePackageNames.isSelected());

		updateStatus(getValidationStatus());
	}

	private IStatus getValidationStatus() {
		if (fAbbreviatePackageNames.isSelected()
				&& JavaElementLabelComposer.parseAbbreviationPattern(fAbbreviatePackageNamePattern.getText()) == null) {
			return new StatusInfo(IStatus.ERROR, PreferencesMessages.AppearancePreferencePage_packageNameAbbreviationPattern_error_isInvalid);
		}

		if (fCompressPackageNames.isSelected() && fPackageNamePattern.getText().equals("")) //$NON-NLS-1$
			return new StatusInfo(IStatus.ERROR, PreferencesMessages.AppearancePreferencePage_packageNameCompressionPattern_error_isEmpty);

		return new StatusInfo();
	}

	private void updateStatus(IStatus status) {
		setValid(!status.matches(IStatus.ERROR));
		StatusUtil.applyToStatusLine(this, status);
	}

	/*
	 * @see IWorkbenchPreferencePage#init(IWorkbench)
	 */
	public void init(IWorkbench workbench) {
	}

	/*
	 * @see IPreferencePage#performOk()
	 */
	@Override
	public boolean performOk() {
		IPreferenceStore prefs= getPreferenceStore();
		prefs.setValue(PREF_METHOD_RETURNTYPE, fShowMethodReturnType.isSelected());
		prefs.setValue(PREF_METHOD_TYPEPARAMETERS, fShowMethodTypeParameters.isSelected());
		prefs.setValue(PREF_CATEGORY, fShowCategory.isSelected());
		prefs.setValue(SHOW_CU_CHILDREN, fShowMembersInPackageView.isSelected());
		prefs.setValue(STACK_BROWSING_VIEWS_VERTICALLY, fStackBrowsingViewsVertically.isSelected());
		prefs.setValue(PREF_PKG_NAME_PATTERN_FOR_PKG_VIEW, fPackageNamePattern.getText());
		prefs.setValue(PREF_COMPRESS_PACKAGE_NAMES, fCompressPackageNames.isSelected());
		prefs.setValue(PREF_PKG_NAME_ABBREVIATION_PATTERN_FOR_PKG_VIEW, fAbbreviatePackageNamePattern.getText());
		prefs.setValue(PREF_ABBREVIATE_PACKAGE_NAMES, fAbbreviatePackageNames.isSelected());
		prefs.setValue(PREF_FOLD_PACKAGES_IN_PACKAGE_EXPLORER, fFoldPackagesInPackageExplorer.isSelected());
		JavaPlugin.flushInstanceScope();
		return super.performOk();
	}

	/*
	 * @see PreferencePage#performDefaults()
	 */
	@Override
	protected void performDefaults() {
		IPreferenceStore prefs= getPreferenceStore();
		fShowMethodReturnType.setSelection(prefs.getDefaultBoolean(PREF_METHOD_RETURNTYPE));
		fShowMethodTypeParameters.setSelection(prefs.getDefaultBoolean(PREF_METHOD_TYPEPARAMETERS));
		fShowCategory.setSelection(prefs.getDefaultBoolean(PREF_CATEGORY));
		fShowMembersInPackageView.setSelection(prefs.getDefaultBoolean(SHOW_CU_CHILDREN));
		fStackBrowsingViewsVertically.setSelection(prefs.getDefaultBoolean(STACK_BROWSING_VIEWS_VERTICALLY));
		fPackageNamePattern.setText(prefs.getDefaultString(PREF_PKG_NAME_PATTERN_FOR_PKG_VIEW));
		fCompressPackageNames.setSelection(prefs.getDefaultBoolean(PREF_COMPRESS_PACKAGE_NAMES));
		fAbbreviatePackageNamePattern.setText(prefs.getDefaultString(PREF_PKG_NAME_ABBREVIATION_PATTERN_FOR_PKG_VIEW));
		fAbbreviatePackageNames.setSelection(prefs.getDefaultBoolean(PREF_ABBREVIATE_PACKAGE_NAMES));
		fFoldPackagesInPackageExplorer.setSelection(prefs.getDefaultBoolean(PREF_FOLD_PACKAGES_IN_PACKAGE_EXPLORER));
		super.performDefaults();
	}
}


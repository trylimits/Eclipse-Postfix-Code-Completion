/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;

import org.eclipse.core.resources.IProject;

import org.eclipse.jface.dialogs.ControlEnableState;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.PixelConverter;

import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;


public class JavadocProblemsConfigurationBlock extends OptionsConfigurationBlock {

	private static final Key PREF_JAVADOC_SUPPORT= getJDTCoreKey(JavaCore.COMPILER_DOC_COMMENT_SUPPORT);

	private static final Key PREF_PB_INVALID_JAVADOC= getJDTCoreKey(JavaCore.COMPILER_PB_INVALID_JAVADOC);
	private static final Key PREF_PB_INVALID_JAVADOC_TAGS= getJDTCoreKey(JavaCore.COMPILER_PB_INVALID_JAVADOC_TAGS);
	private static final Key PREF_PB_INVALID_JAVADOC_TAGS_NOT_VISIBLE_REF= getJDTCoreKey(JavaCore.COMPILER_PB_INVALID_JAVADOC_TAGS__NOT_VISIBLE_REF);
	private static final Key PREF_PB_INVALID_JAVADOC_TAGS_DEPRECATED_REF= getJDTCoreKey(JavaCore.COMPILER_PB_INVALID_JAVADOC_TAGS__DEPRECATED_REF);
	private static final Key PREF_PB_INVALID_JAVADOC_TAGS_VISIBILITY= getJDTCoreKey(JavaCore.COMPILER_PB_INVALID_JAVADOC_TAGS_VISIBILITY);

	private static final Key PREF_PB_MISSING_JAVADOC_TAGS= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_JAVADOC_TAGS);
	private static final Key PREF_PB_MISSING_JAVADOC_TAGS_VISIBILITY= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_JAVADOC_TAGS_VISIBILITY);
	private static final Key PREF_PB_MISSING_JAVADOC_TAGS_OVERRIDING= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_JAVADOC_TAGS_OVERRIDING);
	private static final Key PREF_PB_MISSING_JAVADOC_TAGS_METHOD_TYPE_PARAMETERS= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_JAVADOC_TAGS_METHOD_TYPE_PARAMETERS);

	private static final Key PREF_PB_MISSING_JAVADOC_TAG_DESCRIPTION= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_JAVADOC_TAG_DESCRIPTION);

	private static final Key PREF_PB_MISSING_JAVADOC_COMMENTS= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_JAVADOC_COMMENTS);
	private static final Key PREF_PB_MISSING_JAVADOC_COMMENTS_VISIBILITY= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_JAVADOC_COMMENTS_VISIBILITY);
	private static final Key PREF_PB_MISSING_JAVADOC_COMMENTS_OVERRIDING= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_JAVADOC_COMMENTS_OVERRIDING);


	// values
	private static final String ERROR= JavaCore.ERROR;
	private static final String WARNING= JavaCore.WARNING;
	private static final String IGNORE= JavaCore.IGNORE;

	private static final String ENABLED= JavaCore.ENABLED;
	private static final String DISABLED= JavaCore.DISABLED;

	private static final String PUBLIC= JavaCore.PUBLIC;
	private static final String PROTECTED= JavaCore.PROTECTED;
	private static final String DEFAULT= JavaCore.DEFAULT;
	private static final String PRIVATE= JavaCore.PRIVATE;

	private static final String NO_TAG= JavaCore.COMPILER_PB_MISSING_JAVADOC_TAG_DESCRIPTION_NO_TAG;
	private static final String ALL_STANDARD_TAGS= JavaCore.COMPILER_PB_MISSING_JAVADOC_TAG_DESCRIPTION_ALL_STANDARD_TAGS;
	private static final String RETURN_TAGS= JavaCore.COMPILER_PB_MISSING_JAVADOC_TAG_DESCRIPTION_RETURN_TAG;

	private PixelConverter fPixelConverter;
	private Composite fJavadocComposite;

	private ControlEnableState fBlockEnableState;


	public JavadocProblemsConfigurationBlock(IStatusChangeListener context, IProject project, IWorkbenchPreferenceContainer container) {
		super(context, project, getKeys(), container);
		fBlockEnableState= null;
	}

	public static Key[] getKeys() {
		Key[] keys= new Key[] {
				PREF_JAVADOC_SUPPORT,
				PREF_PB_INVALID_JAVADOC, PREF_PB_INVALID_JAVADOC_TAGS_VISIBILITY, PREF_PB_INVALID_JAVADOC_TAGS,
				PREF_PB_INVALID_JAVADOC_TAGS_VISIBILITY,
				PREF_PB_INVALID_JAVADOC_TAGS_NOT_VISIBLE_REF, PREF_PB_INVALID_JAVADOC_TAGS_DEPRECATED_REF,
				PREF_PB_MISSING_JAVADOC_TAGS, PREF_PB_MISSING_JAVADOC_TAGS_VISIBILITY, PREF_PB_MISSING_JAVADOC_TAGS_OVERRIDING,
				PREF_PB_MISSING_JAVADOC_TAGS_METHOD_TYPE_PARAMETERS,
				PREF_PB_MISSING_JAVADOC_COMMENTS, PREF_PB_MISSING_JAVADOC_COMMENTS_VISIBILITY, PREF_PB_MISSING_JAVADOC_COMMENTS_OVERRIDING,
				PREF_PB_MISSING_JAVADOC_TAG_DESCRIPTION,
			};
		return keys;
	}

	/*
	 * @see org.eclipse.jface.preference.PreferencePage#createContents(Composite)
	 */
	@Override
	protected Control createContents(Composite parent) {
		fPixelConverter= new PixelConverter(parent);
		setShell(parent.getShell());

		Composite javadocComposite= createJavadocTabContent(parent);

		validateSettings(null, null, null);

		return javadocComposite;
	}

	private Composite createJavadocTabContent(Composite folder) {
		String[] errorWarningIgnore= new String[] { ERROR, WARNING, IGNORE };

		String[] errorWarningIgnoreLabels= new String[] {
				PreferencesMessages.JavadocProblemsConfigurationBlock_error,
				PreferencesMessages.JavadocProblemsConfigurationBlock_warning,
				PreferencesMessages.JavadocProblemsConfigurationBlock_ignore
		};

		String[] enabledDisabled= new String[] { ENABLED, DISABLED };
		String[] disabledEnabled= new String[] { DISABLED, ENABLED };

		String[] visibilities= new String[] { PUBLIC, PROTECTED, DEFAULT, PRIVATE  };

		String[] visibilitiesLabels= new String[] {
				PreferencesMessages.JavadocProblemsConfigurationBlock_public,
				PreferencesMessages.JavadocProblemsConfigurationBlock_protected,
				PreferencesMessages.JavadocProblemsConfigurationBlock_default,
				PreferencesMessages.JavadocProblemsConfigurationBlock_private
		};

		String[] missingTagValues= { ALL_STANDARD_TAGS, RETURN_TAGS, NO_TAG };
		String[] missingTagLabels= new String[] {
				PreferencesMessages.JavadocProblemsConfigurationBlock_allStandardTags,
				PreferencesMessages.JavadocProblemsConfigurationBlock_returnTag,
				PreferencesMessages.JavadocProblemsConfigurationBlock_ignore
		};

		int nColumns= 3;


		final ScrolledPageContent sc1 = new ScrolledPageContent(folder);

		Composite outer= sc1.getBody();

		GridLayout layout = new GridLayout();
		layout.numColumns= nColumns;
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		outer.setLayout(layout);

		StyledText widget= new StyledText(outer, SWT.READ_ONLY | SWT.WRAP);
		widget.setText(Messages.format(PreferencesMessages.JavadocProblemsConfigurationBlock_note_message, PreferencesMessages.JavadocProblemsConfigurationBlock_note_title));
		widget.setBackground(outer.getBackground());
		widget.setLeftMargin(0);
		widget.setEnabled(false);
		StyleRange styleRange= new StyleRange();
		styleRange.start= 0;
		styleRange.length= PreferencesMessages.JavadocProblemsConfigurationBlock_note_title.length();
		styleRange.fontStyle= SWT.BOLD;
		widget.setStyleRange(styleRange);
		GridDataFactory.generate(widget, nColumns, 1);

		Composite spacer= new Composite(outer, SWT.NONE);
		GridDataFactory.fillDefaults().span(nColumns, 1).hint(1, 5).applyTo(spacer);

		Link ignoreOptionalProblemsLink= createIgnoreOptionalProblemsLink(outer);
		if (ignoreOptionalProblemsLink != null) {
			GridData gd= new GridData();
			gd.horizontalSpan= nColumns;
			ignoreOptionalProblemsLink.setLayoutData(gd);
			
			spacer= new Composite(outer, SWT.NONE);
			GridDataFactory.fillDefaults().span(nColumns, 1).hint(1, 5).applyTo(spacer);
		}
		
		String label= PreferencesMessages.JavadocProblemsConfigurationBlock_pb_javadoc_support_label;
		addCheckBox(outer, label, PREF_JAVADOC_SUPPORT, enabledDisabled, 0);

		int indent= fPixelConverter.convertWidthInCharsToPixels(4);
		
		layout = new GridLayout();
		layout.numColumns= nColumns;
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.marginLeft= indent;

		Composite composite= new Composite(outer, SWT.NONE);
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL, GridData.FILL, false, true));

		fJavadocComposite= composite;
		
		spacer= new Composite(composite, SWT.NONE);
		GridDataFactory.fillDefaults().span(nColumns, 1).hint(1, 5).applyTo(spacer);
		
		Label description= new Label(composite, SWT.WRAP);
		description.setText(PreferencesMessages.JavadocProblemsConfigurationBlock_javadoc_description);
		GridData gd= new GridData();
		gd.horizontalSpan= nColumns;
		description.setLayoutData(gd);

		spacer= new Composite(composite, SWT.NONE);
		GridDataFactory.fillDefaults().span(nColumns, 1).hint(1, 5).applyTo(spacer);

		label = PreferencesMessages.JavadocProblemsConfigurationBlock_pb_invalid_javadoc_label;
		addComboBox(composite, label, PREF_PB_INVALID_JAVADOC, errorWarningIgnore, errorWarningIgnoreLabels, 0);

		label = PreferencesMessages.JavadocProblemsConfigurationBlock_pb_invalid_javadoc_tags_visibility_label;
		addComboBox(composite, label, PREF_PB_INVALID_JAVADOC_TAGS_VISIBILITY, visibilities, visibilitiesLabels, indent);

		label= PreferencesMessages.JavadocProblemsConfigurationBlock_pb_invalid_javadoc_tags_label;
		addCheckBox(composite, label, PREF_PB_INVALID_JAVADOC_TAGS, enabledDisabled, indent);

		Composite validateTagComposite= new Composite(composite, SWT.NONE);
		GridData gridData= new GridData(SWT.FILL, SWT.TOP, true, false);
		gridData.horizontalIndent= indent;
		gridData.horizontalSpan= nColumns;
		validateTagComposite.setLayoutData(gridData);
		GridLayout gridLayout= new GridLayout(1, false);
		gridLayout.marginHeight= 0;
		gridLayout.marginWidth= 0;
		validateTagComposite.setLayout(gridLayout);

		label= PreferencesMessages.JavadocProblemsConfigurationBlock_pb_invalid_javadoc_tags_not_visible_ref_label;
		addCheckBox(validateTagComposite, label, PREF_PB_INVALID_JAVADOC_TAGS_NOT_VISIBLE_REF, enabledDisabled, indent);

		label= PreferencesMessages.JavadocProblemsConfigurationBlock_pb_invalid_javadoc_tags_deprecated_label;
		addCheckBox(validateTagComposite, label, PREF_PB_INVALID_JAVADOC_TAGS_DEPRECATED_REF, enabledDisabled, indent);

		label= PreferencesMessages.JavadocProblemsConfigurationBlock_pb_missing_tag_description;
		addComboBox(composite, label, PREF_PB_MISSING_JAVADOC_TAG_DESCRIPTION, missingTagValues, missingTagLabels, indent);


		spacer= new Composite(composite, SWT.NONE);
		GridDataFactory.fillDefaults().span(nColumns, 1).hint(1, 5).applyTo(spacer);

		label = PreferencesMessages.JavadocProblemsConfigurationBlock_pb_missing_javadoc_label;
		addComboBox(composite, label, PREF_PB_MISSING_JAVADOC_TAGS, errorWarningIgnore, errorWarningIgnoreLabels, 0);

		label = PreferencesMessages.JavadocProblemsConfigurationBlock_pb_missing_javadoc_tags_visibility_label;
		addComboBox(composite, label, PREF_PB_MISSING_JAVADOC_TAGS_VISIBILITY, visibilities, visibilitiesLabels, indent);

		label= PreferencesMessages.JavadocProblemsConfigurationBlock_pb_missing_javadoc_tags_overriding_label;
		addCheckBox(composite, label, PREF_PB_MISSING_JAVADOC_TAGS_OVERRIDING, disabledEnabled, indent);

		label= PreferencesMessages.JavadocProblemsConfigurationBlock_pb_missing_comments_method_type_parameter_label;
		addCheckBox(composite, label, PREF_PB_MISSING_JAVADOC_TAGS_METHOD_TYPE_PARAMETERS, disabledEnabled, indent);
		

		spacer= new Composite(composite, SWT.NONE);
		GridDataFactory.fillDefaults().span(nColumns, 1).hint(1, 5).applyTo(spacer);

		label = PreferencesMessages.JavadocProblemsConfigurationBlock_pb_missing_comments_label;
		addComboBox(composite, label, PREF_PB_MISSING_JAVADOC_COMMENTS, errorWarningIgnore, errorWarningIgnoreLabels, 0);

		label = PreferencesMessages.JavadocProblemsConfigurationBlock_pb_missing_comments_visibility_label;
		addComboBox(composite, label, PREF_PB_MISSING_JAVADOC_COMMENTS_VISIBILITY, visibilities, visibilitiesLabels, indent);

		label= PreferencesMessages.JavadocProblemsConfigurationBlock_pb_missing_comments_overriding_label;
		addCheckBox(composite, label, PREF_PB_MISSING_JAVADOC_COMMENTS_OVERRIDING, disabledEnabled, indent);

		return sc1;
	}

	/* (non-javadoc)
	 * Update fields and validate.
	 * @param changedKey Key that changed, or null, if all changed.
	 */
	@Override
	protected void validateSettings(Key changedKey, String oldValue, String newValue) {
		if (!areSettingsEnabled()) {
			return;
		}

		if (changedKey != null) {
			if (PREF_PB_INVALID_JAVADOC.equals(changedKey) ||
					PREF_PB_MISSING_JAVADOC_TAGS.equals(changedKey) ||
					PREF_PB_MISSING_JAVADOC_COMMENTS.equals(changedKey) ||
					PREF_JAVADOC_SUPPORT.equals(changedKey) ||
					PREF_PB_INVALID_JAVADOC_TAGS.equals(changedKey)) {
				updateEnableStates();
			} else {
				return;
			}
		} else {
			updateEnableStates();
		}
		fContext.statusChanged(new StatusInfo());
	}

	private void updateEnableStates() {
		boolean enableJavadoc= checkValue(PREF_JAVADOC_SUPPORT, ENABLED);
		enableConfigControls(enableJavadoc);

		if (enableJavadoc) {
			boolean enableInvalidTagsErrors= !checkValue(PREF_PB_INVALID_JAVADOC, IGNORE);
			getCheckBox(PREF_PB_INVALID_JAVADOC_TAGS).setEnabled(enableInvalidTagsErrors);

			boolean enableInvalidTagsDetailsErrors= enableInvalidTagsErrors && checkValue(PREF_PB_INVALID_JAVADOC_TAGS, ENABLED);
			getCheckBox(PREF_PB_INVALID_JAVADOC_TAGS_NOT_VISIBLE_REF).setEnabled(enableInvalidTagsDetailsErrors);
			getCheckBox(PREF_PB_INVALID_JAVADOC_TAGS_DEPRECATED_REF).setEnabled(enableInvalidTagsDetailsErrors);

			setComboEnabled(PREF_PB_INVALID_JAVADOC_TAGS_VISIBILITY, enableInvalidTagsErrors);
			setComboEnabled(PREF_PB_MISSING_JAVADOC_TAG_DESCRIPTION, enableInvalidTagsErrors);

			boolean enableMissingTagsErrors= !checkValue(PREF_PB_MISSING_JAVADOC_TAGS, IGNORE);
			getCheckBox(PREF_PB_MISSING_JAVADOC_TAGS_OVERRIDING).setEnabled(enableMissingTagsErrors);
			getCheckBox(PREF_PB_MISSING_JAVADOC_TAGS_METHOD_TYPE_PARAMETERS).setEnabled(enableMissingTagsErrors);
			setComboEnabled(PREF_PB_MISSING_JAVADOC_TAGS_VISIBILITY, enableMissingTagsErrors);

			boolean enableMissingCommentsErrors= !checkValue(PREF_PB_MISSING_JAVADOC_COMMENTS, IGNORE);
			getCheckBox(PREF_PB_MISSING_JAVADOC_COMMENTS_OVERRIDING).setEnabled(enableMissingCommentsErrors);
			setComboEnabled(PREF_PB_MISSING_JAVADOC_COMMENTS_VISIBILITY, enableMissingCommentsErrors);
		}
	}

	protected void enableConfigControls(boolean enable) {
		if (enable) {
			if (fBlockEnableState != null) {
				fBlockEnableState.restore();
				fBlockEnableState= null;
			}
		} else {
			if (fBlockEnableState == null) {
				fBlockEnableState= ControlEnableState.disable(fJavadocComposite);
			}
		}
	}


	@Override
	protected String[] getFullBuildDialogStrings(boolean workspaceSettings) {
		String title= PreferencesMessages.JavadocProblemsConfigurationBlock_needsbuild_title;
		String message;
		if (workspaceSettings) {
			message= PreferencesMessages.JavadocProblemsConfigurationBlock_needsfullbuild_message;
		} else {
			message= PreferencesMessages.JavadocProblemsConfigurationBlock_needsprojectbuild_message;
		}
		return new String[] { title, message };
	}

}

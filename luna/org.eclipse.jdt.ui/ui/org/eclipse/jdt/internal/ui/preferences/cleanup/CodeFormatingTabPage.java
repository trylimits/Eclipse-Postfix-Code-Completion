/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Alex Blewitt - https://bugs.eclipse.org/bugs/show_bug.cgi?id=168954
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences.cleanup;

import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

import org.eclipse.jface.dialogs.Dialog;

import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.fix.CodeFormatCleanUp;
import org.eclipse.jdt.internal.ui.fix.ImportsCleanUp;
import org.eclipse.jdt.internal.ui.fix.SortMembersCleanUp;
import org.eclipse.jdt.internal.ui.preferences.formatter.JavaPreview;

public final class CodeFormatingTabPage extends AbstractCleanUpTabPage {

	public static final String ID= "org.eclipse.jdt.ui.cleanup.tabpage.code_formatting"; //$NON-NLS-1$

	private Map<String, String> fValues;
	private CleanUpPreview fPreview;

	public CodeFormatingTabPage() {
		super();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setWorkingValues(Map<String, String> workingValues) {
		super.setWorkingValues(workingValues);
		fValues= workingValues;
	}

	@Override
	protected AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values) {
		return new AbstractCleanUp[] {
				new ImportsCleanUp(values),
				new CodeFormatCleanUp(values),
				new SortMembersCleanUp(values)
		};
	}

	@Override
	protected JavaPreview doCreateJavaPreview(Composite parent) {
		fPreview= (CleanUpPreview)super.doCreateJavaPreview(parent);
		fPreview.showInvisibleCharacters(true);
        fPreview.setFormat(CleanUpOptions.TRUE.equals(fValues.get(CleanUpConstants.FORMAT_SOURCE_CODE)));
        fPreview.setCorrectIndentation(CleanUpOptions.TRUE.equals(fValues.get(CleanUpConstants.FORMAT_CORRECT_INDENTATION)));
		return fPreview;
	}

	@Override
	protected void doCreatePreferences(Composite composite, int numColumns) {

		Group group= createGroup(numColumns, composite, CleanUpMessages.CodeFormatingTabPage_GroupName_Formatter);

		if (!isSaveAction()) {
			final CheckboxPreference format= createCheckboxPref(group, numColumns, CleanUpMessages.CodeFormatingTabPage_CheckboxName_FormatSourceCode, CleanUpConstants.FORMAT_SOURCE_CODE, CleanUpModifyDialog.FALSE_TRUE);
			registerPreference(format);
			format.addObserver(new Observer() {
				public void update(Observable o, Object arg) {
					fPreview.setFormat(format.getChecked());
					fPreview.update();
				}
			});
		}

		final CheckboxPreference whiteSpace= createCheckboxPref(group, numColumns, CleanUpMessages.CodeFormatingTabPage_RemoveTrailingWhitespace_checkbox_text, CleanUpConstants.FORMAT_REMOVE_TRAILING_WHITESPACES, CleanUpModifyDialog.FALSE_TRUE);
		intent(group);
		final RadioPreference allPref= createRadioPref(group, 1, CleanUpMessages.CodeFormatingTabPage_RemoveTrailingWhitespace_all_radio, CleanUpConstants.FORMAT_REMOVE_TRAILING_WHITESPACES_ALL, CleanUpModifyDialog.FALSE_TRUE);
		final RadioPreference ignoreEmptyPref= createRadioPref(group, 1, CleanUpMessages.CodeFormatingTabPage_RemoveTrailingWhitespace_ignoreEmpty_radio, CleanUpConstants.FORMAT_REMOVE_TRAILING_WHITESPACES_IGNORE_EMPTY, CleanUpModifyDialog.FALSE_TRUE);
		registerSlavePreference(whiteSpace, new RadioPreference[] {allPref, ignoreEmptyPref});

		final CheckboxPreference correctIndentation= createCheckboxPref(group, numColumns, CleanUpMessages.CodeFormatingTabPage_correctIndentation_checkbox_text, CleanUpConstants.FORMAT_CORRECT_INDENTATION, CleanUpModifyDialog.FALSE_TRUE);
		registerPreference(correctIndentation);
		correctIndentation.addObserver(new Observer() {
			public void update(Observable o, Object arg) {
				fPreview.setCorrectIndentation(correctIndentation.getChecked());
				fPreview.update();
			}
		});

		if (!isSaveAction()) {
			createLabel(numColumns, group, CleanUpMessages.CodeFormatingTabPage_FormatterSettings_Description).setFont(composite.getFont());

			Group importsGroup= createGroup(numColumns, composite, CleanUpMessages.CodeFormatingTabPage_Imports_GroupName);
			CheckboxPreference organizeImports= createCheckboxPref(importsGroup, numColumns, CleanUpMessages.CodeFormatingTabPage_OrganizeImports_CheckBoxLable, CleanUpConstants.ORGANIZE_IMPORTS, CleanUpModifyDialog.FALSE_TRUE);
			registerPreference(organizeImports);
			createLabel(numColumns, importsGroup, CleanUpMessages.CodeFormatingTabPage_OrganizeImportsSettings_Description).setFont(composite.getFont());
		}

		Group sortMembersGroup= createGroup(numColumns, composite, CleanUpMessages.CodeFormatingTabPage_SortMembers_GroupName);

		final CheckboxPreference sortMembersPref= createCheckboxPref(sortMembersGroup, numColumns, CleanUpMessages.CodeFormatingTabPage_SortMembers_CheckBoxLabel, CleanUpConstants.SORT_MEMBERS, CleanUpModifyDialog.FALSE_TRUE);
		intent(sortMembersGroup);
		final RadioPreference sortAllPref= createRadioPref(sortMembersGroup, numColumns - 1, CleanUpMessages.CodeFormatingTabPage_SortMembersFields_CheckBoxLabel, CleanUpConstants.SORT_MEMBERS_ALL, CleanUpModifyDialog.FALSE_TRUE);
		intent(sortMembersGroup);
		final Button nullRadio= new Button(sortMembersGroup, SWT.RADIO);
		nullRadio.setText(CleanUpMessages.CodeFormatingTabPage_SortMembersExclusive_radio0);
		nullRadio.setLayoutData(createGridData(numColumns - 1, GridData.FILL_HORIZONTAL, SWT.DEFAULT));
		nullRadio.setFont(composite.getFont());
		intent(sortMembersGroup);
		final Label warningImage= new Label(sortMembersGroup, SWT.LEFT | SWT.WRAP);
		warningImage.setImage(Dialog.getImage(Dialog.DLG_IMG_MESSAGE_WARNING));
		warningImage.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, false));
		final Label warningLabel= createLabel(numColumns - 2, sortMembersGroup, CleanUpMessages.CodeFormatingTabPage_SortMembersSemanticChange_warning);

		registerSlavePreference(sortMembersPref, new RadioPreference[] {sortAllPref});
		sortMembersPref.addObserver(new Observer() {
			public void update(Observable o, Object arg) {
				nullRadio.setEnabled(sortMembersPref.getChecked());

				boolean warningEnabled= sortMembersPref.getChecked() && sortAllPref.getChecked();
				warningImage.setEnabled(warningEnabled);
				warningLabel.setEnabled(warningEnabled);
			}
		});
		sortAllPref.addObserver(new Observer() {
			public void update(Observable o, Object arg) {
				boolean warningEnabled= sortMembersPref.getChecked() && sortAllPref.getChecked();
				warningImage.setEnabled(warningEnabled);
				warningLabel.setEnabled(warningEnabled);
			}
		});
		nullRadio.setEnabled(sortMembersPref.getChecked());
		nullRadio.setSelection(CleanUpOptions.FALSE.equals(fValues.get(CleanUpConstants.SORT_MEMBERS_ALL)));
		boolean warningEnabled= sortMembersPref.getChecked() && sortAllPref.getChecked();
		warningImage.setEnabled(warningEnabled);
		warningLabel.setEnabled(warningEnabled);

		createLabel(numColumns, sortMembersGroup, CleanUpMessages.CodeFormatingTabPage_SortMembers_Description);
	}

}

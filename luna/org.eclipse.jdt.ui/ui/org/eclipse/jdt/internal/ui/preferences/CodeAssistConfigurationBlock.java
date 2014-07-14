/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.text.java.ProposalSorterHandle;
import org.eclipse.jdt.internal.ui.text.java.ProposalSorterRegistry;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;


/**
 * Configures the content assist preferences.
 *
 * @since 3.0
 */
class CodeAssistConfigurationBlock extends OptionsConfigurationBlock {

	private static final Key PREF_CODEASSIST_AUTOACTIVATION= getJDTUIKey(PreferenceConstants.CODEASSIST_AUTOACTIVATION);
	private static final Key PREF_CODEASSIST_AUTOACTIVATION_DELAY= getJDTUIKey(PreferenceConstants.CODEASSIST_AUTOACTIVATION_DELAY);
	private static final Key PREF_CODEASSIST_AUTOINSERT= getJDTUIKey(PreferenceConstants.CODEASSIST_AUTOINSERT);
	private static final Key PREF_CODEASSIST_AUTOACTIVATION_TRIGGERS_JAVA= getJDTUIKey(PreferenceConstants.CODEASSIST_AUTOACTIVATION_TRIGGERS_JAVA);
	private static final Key PREF_CODEASSIST_AUTOACTIVATION_TRIGGERS_JAVADOC= getJDTUIKey(PreferenceConstants.CODEASSIST_AUTOACTIVATION_TRIGGERS_JAVADOC);
	private static final Key PREF_CODEASSIST_SHOW_VISIBLE_PROPOSALS= getJDTUIKey(PreferenceConstants.CODEASSIST_SHOW_VISIBLE_PROPOSALS);
	private static final Key PREF_CODEASSIST_SORTER= getJDTUIKey(PreferenceConstants.CODEASSIST_SORTER);
	private static final Key PREF_CODEASSIST_CASE_SENSITIVITY= getJDTUIKey(PreferenceConstants.CODEASSIST_CASE_SENSITIVITY);
	private static final Key PREF_CODEASSIST_ADDIMPORT= getJDTUIKey(PreferenceConstants.CODEASSIST_ADDIMPORT);
	private static final Key PREF_CODEASSIST_SUGGEST_STATIC_IMPORTS= getJDTCoreKey(JavaCore.CODEASSIST_SUGGEST_STATIC_IMPORTS);
	private static final Key PREF_CODEASSIST_INSERT_COMPLETION= getJDTUIKey(PreferenceConstants.CODEASSIST_INSERT_COMPLETION);
	private static final Key PREF_CODEASSIST_FILL_ARGUMENT_NAMES= getJDTUIKey(PreferenceConstants.CODEASSIST_FILL_ARGUMENT_NAMES);
	private static final Key PREF_CODEASSIST_GUESS_METHOD_ARGUMENTS= getJDTUIKey(PreferenceConstants.CODEASSIST_GUESS_METHOD_ARGUMENTS);
	private static final Key PREF_CODEASSIST_PREFIX_COMPLETION= getJDTUIKey(PreferenceConstants.CODEASSIST_PREFIX_COMPLETION);
	private static final Key PREF_CODEASSIST_DEPRECATION_CHECK= getJDTCoreKey(JavaCore.CODEASSIST_DEPRECATION_CHECK);
	private static final Key PREF_CODEASSIST_CAMEL_CASE_MATCH= getJDTCoreKey(JavaCore.CODEASSIST_CAMEL_CASE_MATCH);

	private static Key[] getAllKeys() {
		return new Key[] {
				PREF_CODEASSIST_AUTOACTIVATION,
				PREF_CODEASSIST_AUTOACTIVATION_DELAY,
				PREF_CODEASSIST_AUTOINSERT,
				PREF_CODEASSIST_AUTOACTIVATION_TRIGGERS_JAVA,
				PREF_CODEASSIST_AUTOACTIVATION_TRIGGERS_JAVADOC,
				PREF_CODEASSIST_SHOW_VISIBLE_PROPOSALS,
				PREF_CODEASSIST_SORTER,
				PREF_CODEASSIST_CASE_SENSITIVITY,
				PREF_CODEASSIST_ADDIMPORT,
				PREF_CODEASSIST_SUGGEST_STATIC_IMPORTS,
				PREF_CODEASSIST_INSERT_COMPLETION,
				PREF_CODEASSIST_FILL_ARGUMENT_NAMES,
				PREF_CODEASSIST_GUESS_METHOD_ARGUMENTS,
				PREF_CODEASSIST_PREFIX_COMPLETION,
				PREF_CODEASSIST_DEPRECATION_CHECK,
				PREF_CODEASSIST_CAMEL_CASE_MATCH,
		};
	}

	private static final String[] trueFalse= new String[] { IPreferenceStore.TRUE, IPreferenceStore.FALSE };
	private static final String[] enabledDisabled= new String[] { JavaCore.ENABLED, JavaCore.DISABLED };

	private Button fCompletionInsertsRadioButton;
	private Button fCompletionOverwritesRadioButton;
	private Button fInsertParameterNamesRadioButton;
	private Button fInsertBestGuessRadioButton;

	public CodeAssistConfigurationBlock(IStatusChangeListener statusListener, IWorkbenchPreferenceContainer workbenchcontainer) {
		super(statusListener, null, getAllKeys(), workbenchcontainer);
	}

	@Override
	protected Control createContents(Composite parent) {
		ScrolledPageContent scrolled= new ScrolledPageContent(parent, SWT.H_SCROLL | SWT.V_SCROLL);
		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);

		Composite control= new Composite(scrolled, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		control.setLayout(layout);

		Composite composite;

		composite= createSubsection(control, PreferencesMessages.CodeAssistConfigurationBlock_insertionSection_title);
		addInsertionSection(composite);

		composite= createSubsection(control, PreferencesMessages.CodeAssistConfigurationBlock_sortingSection_title);
		addSortingSection(composite);

		composite= createSubsection(control, PreferencesMessages.CodeAssistConfigurationBlock_autoactivationSection_title);
		addAutoActivationSection(composite);

		initialize();

		scrolled.setContent(control);
		final Point size= control.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		scrolled.setMinSize(size.x, size.y);
		return scrolled;
	}

	protected Composite createSubsection(Composite parent, String label) {
		Group group= new Group(parent, SWT.SHADOW_NONE);
		group.setText(label);
		GridData data= new GridData(SWT.FILL, SWT.CENTER, true, false);
		group.setLayoutData(data);
		GridLayout layout= new GridLayout();
		layout.numColumns= 3;
		group.setLayout(layout);

		return group;
	}

	private void addInsertionSection(Composite composite) {
		addCompletionRadioButtons(composite);

		String label;
		label= PreferencesMessages.JavaEditorPreferencePage_insertSingleProposalsAutomatically;
		addCheckBox(composite, label, PREF_CODEASSIST_AUTOINSERT, trueFalse, 0);

		label= PreferencesMessages.JavaEditorPreferencePage_completePrefixes;
		addCheckBox(composite, label, PREF_CODEASSIST_PREFIX_COMPLETION, trueFalse, 0);

		label= PreferencesMessages.JavaEditorPreferencePage_automaticallyAddImportInsteadOfQualifiedName;
		Button master= addCheckBox(composite, label, PREF_CODEASSIST_ADDIMPORT, trueFalse, 0);

		label= PreferencesMessages.JavaEditorPreferencePage_suggestStaticImports;
		Button slave= addCheckBoxWithLink(composite, label, PREF_CODEASSIST_SUGGEST_STATIC_IMPORTS, enabledDisabled, 20, SWT.DEFAULT, new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				openStaticImportFavoritesPage();
			}
		});
		createSelectionDependency(master, slave.getParent());


		label= PreferencesMessages.JavaEditorPreferencePage_fillArgumentsOnMethodCompletion;
		master= addCheckBox(composite, label, PREF_CODEASSIST_FILL_ARGUMENT_NAMES, trueFalse, 0);

		Composite fillComposite= new Composite(composite, SWT.NONE);
		GridData gd= new GridData();
		gd.horizontalSpan= 2;
		gd.horizontalIndent= LayoutUtil.getIndent();
		fillComposite.setLayoutData(gd);
		GridLayout layout= new GridLayout();
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		layout.numColumns= 2;
		fillComposite.setLayout(layout);


		SelectionListener completionSelectionListener= new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean state= fInsertBestGuessRadioButton.getSelection();
				setValue(PREF_CODEASSIST_GUESS_METHOD_ARGUMENTS, state);
			}
		};

		fInsertParameterNamesRadioButton= new Button(fillComposite, SWT.RADIO | SWT.LEFT);
		fInsertParameterNamesRadioButton.setText(PreferencesMessages.JavaEditorPreferencePage_fillParameterNamesOnMethodCompletion);
		fInsertParameterNamesRadioButton.setLayoutData(new GridData());
		fInsertParameterNamesRadioButton.addSelectionListener(completionSelectionListener);

		fInsertBestGuessRadioButton= new Button(fillComposite, SWT.RADIO | SWT.LEFT);
		fInsertBestGuessRadioButton.setText(PreferencesMessages.JavaEditorPreferencePage_fillBestGuessedArgumentsOnMethodCompletion);
		fInsertBestGuessRadioButton.setLayoutData(new GridData());
		fInsertBestGuessRadioButton.addSelectionListener(completionSelectionListener);

		createSelectionDependency(master, fInsertParameterNamesRadioButton);
		createSelectionDependency(master, fInsertBestGuessRadioButton);
	}

	protected final void openStaticImportFavoritesPage() {
		if (getPreferenceContainer() != null) {
			getPreferenceContainer().openPage(CodeAssistFavoritesPreferencePage.PAGE_ID, null);
		} else {
			PreferencesUtil.createPreferenceDialogOn(getShell(), CodeAssistFavoritesPreferencePage.PAGE_ID, null, null).open();
		}
	}

	/**
	 * Creates a selection dependency between a master and a slave control.
	 *
	 * @param master
	 *                   The master button that controls the state of the slave
	 * @param slave
	 *                   The slave control that is enabled only if the master is
	 *                   selected
	 */
	protected static void createSelectionDependency(final Button master, final Control slave) {
		master.addSelectionListener(new SelectionAdapter() {
			
			@Override
			public void widgetSelected(SelectionEvent event) {
				deepSetEnabled(slave, master.getSelection());
			}

			private void deepSetEnabled(final Control control, boolean enabled) {
				control.setEnabled(enabled);
				if (control instanceof Composite) {
					Control[] children= ((Composite)control).getChildren();
					for (int i= 0; i < children.length; i++) {
						deepSetEnabled(children[i], enabled);
					}
				}
			}
		});
		slave.setEnabled(master.getSelection());
	}

	private void addSortingSection(Composite composite) {
		String label;

		label= PreferencesMessages.CodeAssistConfigurationBlock_typeFilters_link;
		createPreferencePageLink(composite, label, null);
		new Label(composite, SWT.NONE);

		label= PreferencesMessages.JavaEditorPreferencePage_presentProposalsInAlphabeticalOrder;
		ProposalSorterHandle[] sorters= ProposalSorterRegistry.getDefault().getSorters();
		String[] labels= new String[sorters.length];
		String[] values= new String[sorters.length];
		for (int i= 0; i < sorters.length; i++) {
			ProposalSorterHandle handle= sorters[i];
			labels[i]= handle.getName();
			values[i]= handle.getId();
		}

		addComboBox(composite, label, PREF_CODEASSIST_SORTER, values, labels, 0);

		label= PreferencesMessages.CodeAssistConfigurationBlock_matchCamelCase_label;
		addCheckBox(composite, label, PREF_CODEASSIST_CAMEL_CASE_MATCH, enabledDisabled, 0);
		
		label= PreferencesMessages.JavaEditorPreferencePage_showOnlyProposalsVisibleInTheInvocationContext;
		addCheckBox(composite, label, PREF_CODEASSIST_SHOW_VISIBLE_PROPOSALS, trueFalse, 0);

		label= PreferencesMessages.CodeAssistConfigurationBlock_hideDeprecated_label;
		addCheckBox(composite, label, PREF_CODEASSIST_DEPRECATION_CHECK, enabledDisabled, 0);
	}

	private void createPreferencePageLink(Composite composite, String label, final Map<String, String> targetInfo) {
		final Link link= new Link(composite, SWT.NONE);
		link.setText(label);
		link.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				PreferencesUtil.createPreferenceDialogOn(link.getShell(), e.text, null, targetInfo);
			}
		});
	}

	private void addAutoActivationSection(Composite composite) {
		String label;
		label= PreferencesMessages.JavaEditorPreferencePage_enableAutoActivation;
		final Button autoactivation= addCheckBox(composite, label, PREF_CODEASSIST_AUTOACTIVATION, trueFalse, 0);
		autoactivation.addSelectionListener(new SelectionAdapter(){
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateAutoactivationControls();
			}
		});

		label= PreferencesMessages.JavaEditorPreferencePage_autoActivationDelay;
		addLabelledTextField(composite, label, PREF_CODEASSIST_AUTOACTIVATION_DELAY, 4, 20);

		label= PreferencesMessages.JavaEditorPreferencePage_autoActivationTriggersForJava;
		addLabelledTextField(composite, label, PREF_CODEASSIST_AUTOACTIVATION_TRIGGERS_JAVA, 100, 4, 20);

		label= PreferencesMessages.JavaEditorPreferencePage_autoActivationTriggersForJavaDoc;
		addLabelledTextField(composite, label, PREF_CODEASSIST_AUTOACTIVATION_TRIGGERS_JAVADOC, 100, 4, 20);
	}


	protected Text addLabelledTextField(Composite parent, String label, Key key, int textlimit, int indent) {
		return addLabelledTextField(parent, label, key, textlimit, textlimit, indent);
	}

	protected Text addLabelledTextField(Composite parent, String label, Key key, int modelTextLimit, int fieldTextLimit, int indent) {
		PixelConverter pixelConverter= new PixelConverter(parent);

		Label labelControl= new Label(parent, SWT.NONE);
		labelControl.setText(label);
		GridData data= new GridData();
		data.horizontalIndent= indent;
		labelControl.setLayoutData(data);

		Text textBox= new Text(parent, SWT.BORDER | SWT.SINGLE);
		textBox.setData(key);
		textBox.setLayoutData(new GridData());

		fLabels.put(textBox, labelControl);

		String currValue= getValue(key);
		if (currValue != null) {
			textBox.setText(currValue);
		}
		textBox.addModifyListener(getTextModifyListener());

		data= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		if (modelTextLimit != 0)
			textBox.setTextLimit(modelTextLimit);

		if (fieldTextLimit != 0)
			data.widthHint= pixelConverter.convertWidthInCharsToPixels(fieldTextLimit + 1);

		data.horizontalSpan= 2;
		textBox.setLayoutData(data);

		fTextBoxes.add(textBox);
		return textBox;
	}

	private void addCompletionRadioButtons(Composite contentAssistComposite) {
		Composite completionComposite= new Composite(contentAssistComposite, SWT.NONE);
		GridData ccgd= new GridData();
		ccgd.horizontalSpan= 2;
		completionComposite.setLayoutData(ccgd);
		GridLayout ccgl= new GridLayout();
		ccgl.marginWidth= 0;
		ccgl.numColumns= 2;
		completionComposite.setLayout(ccgl);

		SelectionListener completionSelectionListener= new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean insert= fCompletionInsertsRadioButton.getSelection();
				setValue(PREF_CODEASSIST_INSERT_COMPLETION, insert);
			}
		};

		fCompletionInsertsRadioButton= new Button(completionComposite, SWT.RADIO | SWT.LEFT);
		fCompletionInsertsRadioButton.setText(PreferencesMessages.JavaEditorPreferencePage_completionInserts);
		fCompletionInsertsRadioButton.setLayoutData(new GridData());
		fCompletionInsertsRadioButton.addSelectionListener(completionSelectionListener);

		fCompletionOverwritesRadioButton= new Button(completionComposite, SWT.RADIO | SWT.LEFT);
		fCompletionOverwritesRadioButton.setText(PreferencesMessages.JavaEditorPreferencePage_completionOverwrites);
		fCompletionOverwritesRadioButton.setLayoutData(new GridData());
		fCompletionOverwritesRadioButton.addSelectionListener(completionSelectionListener);

		Label label= new Label(completionComposite, SWT.NONE);
		label.setText(PreferencesMessages.JavaEditorPreferencePage_completionToggleHint);
		GridData gd= new GridData();
		gd.horizontalIndent= LayoutUtil.getIndent();
		gd.horizontalSpan= 2;
		label.setLayoutData(gd);
	}

	public void initialize() {
		initializeFields();
	}

	private void initializeFields() {
		boolean value= getBooleanValue(PREF_CODEASSIST_INSERT_COMPLETION);
		fCompletionInsertsRadioButton.setSelection(value);
		fCompletionOverwritesRadioButton.setSelection(!value);
		value= getBooleanValue(PREF_CODEASSIST_GUESS_METHOD_ARGUMENTS);
		fInsertBestGuessRadioButton.setSelection(value);
		fInsertParameterNamesRadioButton.setSelection(!value);

		value= getBooleanValue(PREF_CODEASSIST_FILL_ARGUMENT_NAMES);
		fInsertParameterNamesRadioButton.setEnabled(value);
		fInsertBestGuessRadioButton.setEnabled(value);

		updateAutoactivationControls();
 	}

    private void updateAutoactivationControls() {
        boolean autoactivation= getBooleanValue(PREF_CODEASSIST_AUTOACTIVATION);
        setControlEnabled(PREF_CODEASSIST_AUTOACTIVATION_DELAY, autoactivation);
        setControlEnabled(PREF_CODEASSIST_AUTOACTIVATION_TRIGGERS_JAVA, autoactivation);
        setControlEnabled(PREF_CODEASSIST_AUTOACTIVATION_TRIGGERS_JAVADOC, autoactivation);
    }


	@Override
	public void performDefaults() {
		super.performDefaults();
		initializeFields();
	}

	@Override
	protected String[] getFullBuildDialogStrings(boolean workspaceSettings) {
		return null;
	}

	/**
	 * Validates that the specified number is positive.
	 *
	 * @param number
	 *                   The number to validate
	 * @return The status of the validation
	 */
	protected static IStatus validatePositiveNumber(final String number) {

		final StatusInfo status= new StatusInfo();
		if (number.length() == 0) {
			status.setError(PreferencesMessages.SpellingPreferencePage_empty_threshold);
		} else {
			try {
				final int value= Integer.parseInt(number);
				if (value < 0) {
					status.setError(Messages.format(PreferencesMessages.SpellingPreferencePage_invalid_threshold, number));
				}
			} catch (NumberFormatException exception) {
				status.setError(Messages.format(PreferencesMessages.SpellingPreferencePage_invalid_threshold, number));
			}
		}
		return status;
	}

	@Override
	protected void validateSettings(Key key, String oldValue, String newValue) {
		if (key == null || PREF_CODEASSIST_AUTOACTIVATION_DELAY.equals(key))
			fContext.statusChanged(validatePositiveNumber(getValue(PREF_CODEASSIST_AUTOACTIVATION_DELAY)));
	}

	protected void setControlEnabled(Key key, boolean enabled) {
		Control control= getControl(key);
		control.setEnabled(enabled);
		Label label= fLabels.get(control);
		if (label != null)
			label.setEnabled(enabled);
	}

	private Control getControl(Key key) {
		for (int i= fComboBoxes.size() - 1; i >= 0; i--) {
			Control curr= fComboBoxes.get(i);
			ControlData data= (ControlData) curr.getData();
			if (key.equals(data.getKey())) {
				return curr;
			}
		}
		for (int i= fCheckBoxes.size() - 1; i >= 0; i--) {
			Control curr= fCheckBoxes.get(i);
			ControlData data= (ControlData) curr.getData();
			if (key.equals(data.getKey())) {
				return curr;
			}
		}
		for (int i= fTextBoxes.size() - 1; i >= 0; i--) {
			Control curr= fTextBoxes.get(i);
			Key currKey= (Key) curr.getData();
			if (key.equals(currKey)) {
				return curr;
			}
		}
		return null;
	}
}

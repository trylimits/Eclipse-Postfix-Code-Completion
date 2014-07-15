/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Benjamin Muskalla <b.muskalla@gmx.net> - [preferences] Add preference for new compiler warning: MissingSynchronizedModifierInInheritedMethod - https://bugs.eclipse.org/bugs/show_bug.cgi?id=245240
 *     Stephan Herrmann <stephan@cs.tu-berlin.de> - [compiler][null] inheritance of null annotations as an option - https://bugs.eclipse.org/388281
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences;

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.preferences.DefaultScope;

import org.eclipse.core.resources.IProject;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.SelectionDialog;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.ui.IJavaElementSearchConstants;
import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.dialogs.TextFieldNavigationHandler;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;


public class ProblemSeveritiesConfigurationBlock extends OptionsConfigurationBlock {

	private class NullAnnotationsConfigurationDialog extends StatusDialog {
		
		private class StringButtonAdapter implements IDialogFieldListener, IStringButtonAdapter {
			public void dialogFieldChanged(DialogField field) {
				doValidation((StringButtonDialogField) field);
			}

			public void changeControlPressed(DialogField field) {
				doBrowseTypes((StringButtonDialogField) field);
			}
		}

		private static final int RESTORE_DEFAULTS_BUTTON_ID= IDialogConstants.CLIENT_ID + 1;
		
		
		private StringButtonDialogField fNullableAnnotationDialogField;
		private StringButtonDialogField fNonNullAnnotationDialogField;
		private StringButtonDialogField fNonNullByDefaultAnnotationDialogField;
		private IStatus fNullableStatus, fNonNullStatus, fNonNullByDefaultStatus;

		private NullAnnotationsConfigurationDialog() {
			super(ProblemSeveritiesConfigurationBlock.this.getShell());
			
			setTitle(PreferencesMessages.NullAnnotationsConfigurationDialog_title);
			
			fNullableStatus= new StatusInfo();
			fNonNullStatus= new StatusInfo();
			fNonNullByDefaultStatus= new StatusInfo();
			
			StringButtonAdapter adapter= new StringButtonAdapter();

			fNullableAnnotationDialogField= new StringButtonDialogField(adapter);
			fNullableAnnotationDialogField.setLabelText(PreferencesMessages.NullAnnotationsConfigurationDialog_nullable_annotation_label);
			fNullableAnnotationDialogField.setButtonLabel(PreferencesMessages.NullAnnotationsConfigurationDialog_browse1);
			fNullableAnnotationDialogField.setDialogFieldListener(adapter);
			fNullableAnnotationDialogField.setText(getValue(PREF_NULLABLE_ANNOTATION_NAME));
			
			fNonNullAnnotationDialogField= new StringButtonDialogField(adapter);
			fNonNullAnnotationDialogField.setLabelText(PreferencesMessages.NullAnnotationsConfigurationDialog_nonnull_annotation_label);
			fNonNullAnnotationDialogField.setButtonLabel(PreferencesMessages.NullAnnotationsConfigurationDialog_browse2);
			fNonNullAnnotationDialogField.setDialogFieldListener(adapter);
			fNonNullAnnotationDialogField.setText(getValue(PREF_NONNULL_ANNOTATION_NAME));
			
			fNonNullByDefaultAnnotationDialogField= new StringButtonDialogField(adapter);
			fNonNullByDefaultAnnotationDialogField.setLabelText(PreferencesMessages.NullAnnotationsConfigurationDialog_nonnullbydefault_annotation_label);
			fNonNullByDefaultAnnotationDialogField.setButtonLabel(PreferencesMessages.NullAnnotationsConfigurationDialog_browse3);
			fNonNullByDefaultAnnotationDialogField.setDialogFieldListener(adapter);
			fNonNullByDefaultAnnotationDialogField.setText(getValue(PREF_NONNULL_BY_DEFAULT_ANNOTATION_NAME));
		}
		
		@Override
		protected Control createDialogArea(Composite parent) {
			Composite composite= (Composite) super.createDialogArea(parent);
			initializeDialogUnits(parent);

			GridLayout layout= (GridLayout) composite.getLayout();
			layout.numColumns= 3;
			
			int fieldWidthHint= convertWidthInCharsToPixels(60);
			
			Label intro= new Label(composite, SWT.WRAP);
			intro.setText(PreferencesMessages.NullAnnotationsConfigurationDialog_null_annotations_description);
	    	intro.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false, layout.numColumns, 1));
			LayoutUtil.setWidthHint(intro, fieldWidthHint);
	    	
			fNullableAnnotationDialogField.doFillIntoGrid(composite, 3);
			Text text= fNullableAnnotationDialogField.getTextControl(null);
			LayoutUtil.setWidthHint(text, fieldWidthHint);
			TextFieldNavigationHandler.install(text);

			fNonNullAnnotationDialogField.doFillIntoGrid(composite, 3);
			TextFieldNavigationHandler.install(fNonNullAnnotationDialogField.getTextControl(null));
			
			fNonNullByDefaultAnnotationDialogField.doFillIntoGrid(composite, 3);
			TextFieldNavigationHandler.install(fNonNullByDefaultAnnotationDialogField.getTextControl(null));
			
			fNullableAnnotationDialogField.postSetFocusOnDialogField(parent.getDisplay());

			applyDialogFont(composite);
			return composite;
		}
		
		private void doBrowseTypes(StringButtonDialogField dialogField) {
			IRunnableContext context= new BusyIndicatorRunnableContext();
			IJavaSearchScope scope= SearchEngine.createWorkspaceScope();
			int style= IJavaElementSearchConstants.CONSIDER_ANNOTATION_TYPES;
			try {
				SelectionDialog dialog= JavaUI.createTypeDialog(getShell(), context, scope, style, false, dialogField.getText());
				dialog.setTitle(PreferencesMessages.NullAnnotationsConfigurationDialog_browse_title);
				dialog.setMessage(PreferencesMessages.NullAnnotationsConfigurationDialog_choose_annotation);
				if (dialog.open() == Window.OK) {
					IType res= (IType) dialog.getResult()[0];
					dialogField.setText(res.getFullyQualifiedName('.'));
				}
			} catch (JavaModelException e) {
				ExceptionHandler.handle(e, getShell(), PreferencesMessages.NullAnnotationsConfigurationDialog_error_title, PreferencesMessages.NullAnnotationsConfigurationDialog_error_message);
			}
		}
		
		private void doValidation(StringButtonDialogField dialogField) {
			String newValue= dialogField.getText();
			if (fNullableAnnotationDialogField.equals(dialogField)) {
				fNullableStatus= validateNullnessAnnotation(newValue, PreferencesMessages.NullAnnotationsConfigurationDialog_nullable_annotation_error);
			} else if (fNonNullAnnotationDialogField.equals(dialogField)) {
				fNonNullStatus= validateNullnessAnnotation(newValue, PreferencesMessages.NullAnnotationsConfigurationDialog_nonull_annotation_error);
			} else if (fNonNullByDefaultAnnotationDialogField.equals(dialogField)) {
				fNonNullByDefaultStatus= validateNullnessAnnotation(newValue, PreferencesMessages.NullAnnotationsConfigurationDialog_nonnullbydefault_annotation_error);
			}
			IStatus status= StatusUtil.getMostSevere(new IStatus[] { fNullableStatus, fNonNullStatus, fNonNullByDefaultStatus });
			updateStatus(status);
		}

		@Override
		protected void createButtonsForButtonBar(Composite parent) {
			createButton(parent, RESTORE_DEFAULTS_BUTTON_ID, PreferencesMessages.NullAnnotationsConfigurationDialog_restore_defaults, false);
			super.createButtonsForButtonBar(parent);
		}
		
		@Override
		protected void buttonPressed(int buttonId) {
			if (buttonId == RESTORE_DEFAULTS_BUTTON_ID) {
				fNullableAnnotationDialogField.setText(NULL_ANNOTATIONS_DEFAULTS[0]);
				fNonNullAnnotationDialogField.setText(NULL_ANNOTATIONS_DEFAULTS[1]);
				fNonNullByDefaultAnnotationDialogField.setText(NULL_ANNOTATIONS_DEFAULTS[2]);
			} else {
				super.buttonPressed(buttonId);
			}
		}
		
		public String[] getResult() {
			return new String[] {
					fNullableAnnotationDialogField.getText(),
					fNonNullAnnotationDialogField.getText(),
					fNonNullByDefaultAnnotationDialogField.getText()
			};
		}
		@Override
		protected boolean isResizable() {
			return true;
		}
		
		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell, IJavaHelpContextIds.PROBLEM_SEVERITIES_PROPERTY_PAGE);
		}
	}

	private static final String SETTINGS_SECTION_NAME= "ProblemSeveritiesConfigurationBlock";  //$NON-NLS-1$

	// Preference store keys, see JavaCore.getOptions
	private static final Key PREF_PB_OVERRIDING_PACKAGE_DEFAULT_METHOD= getJDTCoreKey(JavaCore.COMPILER_PB_OVERRIDING_PACKAGE_DEFAULT_METHOD);
	private static final Key PREF_PB_METHOD_WITH_CONSTRUCTOR_NAME= getJDTCoreKey(JavaCore.COMPILER_PB_METHOD_WITH_CONSTRUCTOR_NAME);
	private static final Key PREF_PB_DEPRECATION= getJDTCoreKey(JavaCore.COMPILER_PB_DEPRECATION);
	private static final Key PREF_PB_DEPRECATION_IN_DEPRECATED_CODE=getJDTCoreKey(JavaCore.COMPILER_PB_DEPRECATION_IN_DEPRECATED_CODE);
	private static final Key PREF_PB_DEPRECATION_WHEN_OVERRIDING= getJDTCoreKey(JavaCore.COMPILER_PB_DEPRECATION_WHEN_OVERRIDING_DEPRECATED_METHOD);

	private static final Key PREF_PB_HIDDEN_CATCH_BLOCK= getJDTCoreKey(JavaCore.COMPILER_PB_HIDDEN_CATCH_BLOCK);
	private static final Key PREF_PB_UNUSED_LOCAL= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_LOCAL);
	private static final Key PREF_PB_UNUSED_PARAMETER= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_PARAMETER);
	private static final Key PREF_PB_SIGNAL_PARAMETER_IN_OVERRIDING= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_PARAMETER_WHEN_OVERRIDING_CONCRETE);
	private static final Key PREF_PB_UNUSED_PARAMETER_INCLUDE_DOC_COMMENT_REFERENCE= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_PARAMETER_INCLUDE_DOC_COMMENT_REFERENCE);
	private static final Key PREF_PB_SIGNAL_PARAMETER_IN_ABSTRACT= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_PARAMETER_WHEN_IMPLEMENTING_ABSTRACT);
	private static final Key PREF_PB_SYNTHETIC_ACCESS_EMULATION= getJDTCoreKey(JavaCore.COMPILER_PB_SYNTHETIC_ACCESS_EMULATION);
	private static final Key PREF_PB_NON_EXTERNALIZED_STRINGS= getJDTCoreKey(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL);
	private static final Key PREF_PB_UNUSED_IMPORT= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_IMPORT);
	private static final Key PREF_PB_UNUSED_PRIVATE= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER);
	private static final Key PREF_PB_UNUSED_TYPE_PARAMETER= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_TYPE_PARAMETER);
	private static final Key PREF_PB_STATIC_ACCESS_RECEIVER= getJDTCoreKey(JavaCore.COMPILER_PB_STATIC_ACCESS_RECEIVER);
	private static final Key PREF_PB_NO_EFFECT_ASSIGNMENT= getJDTCoreKey(JavaCore.COMPILER_PB_NO_EFFECT_ASSIGNMENT);
	private static final Key PREF_PB_CHAR_ARRAY_IN_CONCAT= getJDTCoreKey(JavaCore.COMPILER_PB_CHAR_ARRAY_IN_STRING_CONCATENATION);
	private static final Key PREF_PB_POSSIBLE_ACCIDENTAL_BOOLEAN_ASSIGNMENT= getJDTCoreKey(JavaCore.COMPILER_PB_POSSIBLE_ACCIDENTAL_BOOLEAN_ASSIGNMENT);
	private static final Key PREF_PB_LOCAL_VARIABLE_HIDING= getJDTCoreKey(JavaCore.COMPILER_PB_LOCAL_VARIABLE_HIDING);
	private static final Key PREF_PB_FIELD_HIDING= getJDTCoreKey(JavaCore.COMPILER_PB_FIELD_HIDING);
	private static final Key PREF_PB_SPECIAL_PARAMETER_HIDING_FIELD= getJDTCoreKey(JavaCore.COMPILER_PB_SPECIAL_PARAMETER_HIDING_FIELD);
	private static final Key PREF_PB_INDIRECT_STATIC_ACCESS= getJDTCoreKey(JavaCore.COMPILER_PB_INDIRECT_STATIC_ACCESS);
	private static final Key PREF_PB_EMPTY_STATEMENT= getJDTCoreKey(JavaCore.COMPILER_PB_EMPTY_STATEMENT);
	private static final Key PREF_PB_UNNECESSARY_ELSE= getJDTCoreKey(JavaCore.COMPILER_PB_UNNECESSARY_ELSE);
	private static final Key PREF_PB_UNNECESSARY_TYPE_CHECK= getJDTCoreKey(JavaCore.COMPILER_PB_UNNECESSARY_TYPE_CHECK);
	private static final Key PREF_PB_INCOMPATIBLE_INTERFACE_METHOD= getJDTCoreKey(JavaCore.COMPILER_PB_INCOMPATIBLE_NON_INHERITED_INTERFACE_METHOD);
	private static final Key PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_DECLARED_THROWN_EXCEPTION);
	private static final Key PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_WHEN_OVERRIDING= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_DECLARED_THROWN_EXCEPTION_WHEN_OVERRIDING);
	private static final Key PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_INCLUDE_DOC_COMMENT_REFERENCE= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_DECLARED_THROWN_EXCEPTION_INCLUDE_DOC_COMMENT_REFERENCE);
	private static final Key PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_EXEMPT_EXCEPTION_AND_THROWABLE= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_DECLARED_THROWN_EXCEPTION_EXEMPT_EXCEPTION_AND_THROWABLE);
	private static final Key PREF_PB_MISSING_SERIAL_VERSION= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_SERIAL_VERSION);
	private static final Key PREF_PB_UNDOCUMENTED_EMPTY_BLOCK= getJDTCoreKey(JavaCore.COMPILER_PB_UNDOCUMENTED_EMPTY_BLOCK);
	private static final Key PREF_PB_FINALLY_BLOCK_NOT_COMPLETING= getJDTCoreKey(JavaCore.COMPILER_PB_FINALLY_BLOCK_NOT_COMPLETING);
	private static final Key PREF_PB_UNQUALIFIED_FIELD_ACCESS= getJDTCoreKey(JavaCore.COMPILER_PB_UNQUALIFIED_FIELD_ACCESS);
	private static final Key PREF_PB_MISSING_DEPRECATED_ANNOTATION= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_DEPRECATED_ANNOTATION);
	private static final Key PREF_PB_FORBIDDEN_REFERENCE= getJDTCoreKey(JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE);
	private static final Key PREF_PB_DISCOURRAGED_REFERENCE= getJDTCoreKey(JavaCore.COMPILER_PB_DISCOURAGED_REFERENCE);
	private static final Key PREF_PB_UNUSED_LABEL= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_LABEL);
	private static final Key PREF_PB_PARAMETER_ASSIGNMENT= getJDTCoreKey(JavaCore.COMPILER_PB_PARAMETER_ASSIGNMENT);
	private static final Key PREF_PB_FALLTHROUGH_CASE= getJDTCoreKey(JavaCore.COMPILER_PB_FALLTHROUGH_CASE);
	private static final Key PREF_PB_COMPARING_IDENTICAL= getJDTCoreKey(JavaCore.COMPILER_PB_COMPARING_IDENTICAL);
	private static final Key PREF_PB_MISSING_SYNCHRONIZED_ON_INHERITED_METHOD= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_SYNCHRONIZED_ON_INHERITED_METHOD);

	private static final Key PREF_PB_NULL_REFERENCE= getJDTCoreKey(JavaCore.COMPILER_PB_NULL_REFERENCE);
	private static final Key PREF_PB_POTENTIAL_NULL_REFERENCE= getJDTCoreKey(JavaCore.COMPILER_PB_POTENTIAL_NULL_REFERENCE);
	
	private static final Key PREF_ANNOTATION_NULL_ANALYSIS= getJDTCoreKey(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS);
	
	private static final Key PREF_PB_SYNTACTIC_NULL_ANLYSIS_FOR_FIELDS= getJDTCoreKey(JavaCore.COMPILER_PB_SYNTACTIC_NULL_ANALYSIS_FOR_FIELDS);

	private static final Key PREF_INHERIT_NULL_ANNOTATIONS= getJDTCoreKey(JavaCore.COMPILER_INHERIT_NULL_ANNOTATIONS);

	/**
	 * Key for the "Use default annotations for null " setting.
	 * <p>Values are { {@link #ENABLED}, {@link #DISABLED} }.
	 */
	private static final Key INTR_DEFAULT_NULL_ANNOTATIONS= getLocalKey("internal.default.null.annotations"); //$NON-NLS-1$
	private static final Key PREF_NULLABLE_ANNOTATION_NAME= getJDTCoreKey(JavaCore.COMPILER_NULLABLE_ANNOTATION_NAME);
	private static final Key PREF_NONNULL_ANNOTATION_NAME= getJDTCoreKey(JavaCore.COMPILER_NONNULL_ANNOTATION_NAME);
	private static final Key PREF_NONNULL_BY_DEFAULT_ANNOTATION_NAME= getJDTCoreKey(JavaCore.COMPILER_NONNULL_BY_DEFAULT_ANNOTATION_NAME);
	private static final String[] NULL_ANNOTATIONS_DEFAULTS= {
		PREF_NULLABLE_ANNOTATION_NAME.getStoredValue(DefaultScope.INSTANCE, null),
		PREF_NONNULL_ANNOTATION_NAME.getStoredValue(DefaultScope.INSTANCE, null),
		PREF_NONNULL_BY_DEFAULT_ANNOTATION_NAME.getStoredValue(DefaultScope.INSTANCE, null),
	};
	
	private static final Key PREF_MISSING_NONNULL_BY_DEFAULT_ANNOTATION= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_NONNULL_BY_DEFAULT_ANNOTATION);
	
	private static final Key PREF_PB_NULL_SPECIFICATION_VIOLATION= getJDTCoreKey(JavaCore.COMPILER_PB_NULL_SPECIFICATION_VIOLATION);
	private static final Key PREF_PB_POTENTIAL_NULL_ANNOTATION_INFERENCE_CONFLICT= getJDTCoreKey(JavaCore.COMPILER_PB_NULL_ANNOTATION_INFERENCE_CONFLICT);
	private static final Key PREF_PB_NULL_UNCHECKED_CONVERSION= getJDTCoreKey(JavaCore.COMPILER_PB_NULL_UNCHECKED_CONVERSION);
	private static final Key PREF_PB_REDUNDANT_NULL_ANNOTATION= getJDTCoreKey(JavaCore.COMPILER_PB_REDUNDANT_NULL_ANNOTATION);
	private static final Key PREF_PB_NONNULL_PARAMETER_ANNOTATION_DROPPED= getJDTCoreKey(JavaCore.COMPILER_PB_NONNULL_PARAMETER_ANNOTATION_DROPPED);

	private static final Key PREF_PB_REDUNDANT_NULL_CHECK= getJDTCoreKey(JavaCore.COMPILER_PB_REDUNDANT_NULL_CHECK);

	private static final Key PREF_PB_UNCLOSED_CLOSEABLE= getJDTCoreKey(JavaCore.COMPILER_PB_UNCLOSED_CLOSEABLE);
	private static final Key PREF_PB_POTENTIALLY_UNCLOSED_CLOSEABLE= getJDTCoreKey(JavaCore.COMPILER_PB_POTENTIALLY_UNCLOSED_CLOSEABLE);
	private static final Key PREF_PB_EXPLICITLY_CLOSED_AUTOCLOSEABLE= getJDTCoreKey(JavaCore.COMPILER_PB_EXPLICITLY_CLOSED_AUTOCLOSEABLE);

	private static final Key PREF_PB_INCLUDE_ASSERTS_IN_NULL_ANALYSIS= getJDTCoreKey(JavaCore.COMPILER_PB_INCLUDE_ASSERTS_IN_NULL_ANALYSIS);
	private static final Key PREF_PB_REDUNDANT_SUPERINTERFACE= getJDTCoreKey(JavaCore.COMPILER_PB_REDUNDANT_SUPERINTERFACE);

	private static final Key PREF_PB_UNUSED_WARNING_TOKEN= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_WARNING_TOKEN);

	private static final Key PREF_15_PB_UNCHECKED_TYPE_OPERATION= getJDTCoreKey(JavaCore.COMPILER_PB_UNCHECKED_TYPE_OPERATION);
	private static final Key PREF_15_PB_FINAL_PARAM_BOUND= getJDTCoreKey(JavaCore.COMPILER_PB_FINAL_PARAMETER_BOUND);
	private static final Key PREF_15_PB_VARARGS_ARGUMENT_NEED_CAST= getJDTCoreKey(JavaCore.COMPILER_PB_VARARGS_ARGUMENT_NEED_CAST);
	private static final Key PREF_15_PB_AUTOBOXING_PROBLEM= getJDTCoreKey(JavaCore.COMPILER_PB_AUTOBOXING);

	private static final Key PREF_15_PB_MISSING_OVERRIDE_ANNOTATION= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_OVERRIDE_ANNOTATION);
	private static final Key PREF_16_PB_MISSING_OVERRIDE_ANNOTATION_FOR_INTERFACE_METHOD_IMPLEMENTATION= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_OVERRIDE_ANNOTATION_FOR_INTERFACE_METHOD_IMPLEMENTATION);
	private static final Key PREF_15_PB_ANNOTATION_SUPER_INTERFACE= getJDTCoreKey(JavaCore.COMPILER_PB_ANNOTATION_SUPER_INTERFACE);
	private static final Key PREF_15_PB_TYPE_PARAMETER_HIDING= getJDTCoreKey(JavaCore.COMPILER_PB_TYPE_PARAMETER_HIDING);
	private static final Key PREF_15_PB_INCOMPLETE_ENUM_SWITCH= getJDTCoreKey(JavaCore.COMPILER_PB_INCOMPLETE_ENUM_SWITCH);
	private static final Key PREF_15_PB_MISSING_ENUM_CASE_DESPITE_DEFAULT= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_ENUM_CASE_DESPITE_DEFAULT);
	private static final Key PREF_15_PB_SWITCH_MISSING_DEFAULT_CASE= getJDTCoreKey(JavaCore.COMPILER_PB_SWITCH_MISSING_DEFAULT_CASE);
	private static final Key PREF_15_PB_RAW_TYPE_REFERENCE= getJDTCoreKey(JavaCore.COMPILER_PB_RAW_TYPE_REFERENCE);
	private static final Key PREF_17_PB_REDUNDANT_TYPE_ARGUMENTS= getJDTCoreKey(JavaCore.COMPILER_PB_REDUNDANT_TYPE_ARGUMENTS);
	private static final Key PREF_15_PB_UNAVOIDABLE_GENERIC_TYPE_PROBLEMS= getJDTCoreKey(JavaCore.COMPILER_PB_UNAVOIDABLE_GENERIC_TYPE_PROBLEMS);

	private static final Key PREF_PB_SUPPRESS_WARNINGS= getJDTCoreKey(JavaCore.COMPILER_PB_SUPPRESS_WARNINGS);
	private static final Key PREF_PB_SUPPRESS_OPTIONAL_ERRORS= getJDTCoreKey(JavaCore.COMPILER_PB_SUPPRESS_OPTIONAL_ERRORS);
	private static final Key PREF_PB_UNHANDLED_WARNING_TOKEN= getJDTCoreKey(JavaCore.COMPILER_PB_UNHANDLED_WARNING_TOKEN);
	private static final Key PREF_PB_FATAL_OPTIONAL_ERROR= getJDTCoreKey(JavaCore.COMPILER_PB_FATAL_OPTIONAL_ERROR);

	private static final Key PREF_PB_MISSING_HASHCODE_METHOD= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_HASHCODE_METHOD);
	private static final Key PREF_PB_DEAD_CODE= getJDTCoreKey(JavaCore.COMPILER_PB_DEAD_CODE);
	private static final Key PREF_PB_UNUSED_OBJECT_ALLOCATION= getJDTCoreKey(JavaCore.COMPILER_PB_UNUSED_OBJECT_ALLOCATION);
	private static final Key PREF_PB_MISSING_STATIC_ON_METHOD= getJDTCoreKey(JavaCore.COMPILER_PB_MISSING_STATIC_ON_METHOD);
	private static final Key PREF_PB_POTENTIALLY_MISSING_STATIC_ON_METHOD= getJDTCoreKey(JavaCore.COMPILER_PB_POTENTIALLY_MISSING_STATIC_ON_METHOD);
	
	// values
	private static final String ERROR= JavaCore.ERROR;
	private static final String WARNING= JavaCore.WARNING;
	private static final String IGNORE= JavaCore.IGNORE;

	private static final String ENABLED= JavaCore.ENABLED;
	private static final String DISABLED= JavaCore.DISABLED;

	private PixelConverter fPixelConverter;

	private FilteredPreferenceTree fFilteredPrefTree;

	public ProblemSeveritiesConfigurationBlock(IStatusChangeListener context, IProject project, IWorkbenchPreferenceContainer container) {
		super(context, project, getKeys(), container);
		
		// Compatibility code for the merge of the two option PB_SIGNAL_PARAMETER:
		if (ENABLED.equals(getValue(PREF_PB_SIGNAL_PARAMETER_IN_ABSTRACT))) {
			setValue(PREF_PB_SIGNAL_PARAMETER_IN_OVERRIDING, ENABLED);
		}
	}

	public static Key[] getKeys() {
		return new Key[] {
				PREF_PB_OVERRIDING_PACKAGE_DEFAULT_METHOD,
				PREF_PB_METHOD_WITH_CONSTRUCTOR_NAME, PREF_PB_DEPRECATION, PREF_PB_HIDDEN_CATCH_BLOCK, PREF_PB_UNUSED_LOCAL,
				PREF_PB_UNUSED_PARAMETER, PREF_PB_UNUSED_PARAMETER_INCLUDE_DOC_COMMENT_REFERENCE,
				PREF_PB_SYNTHETIC_ACCESS_EMULATION, PREF_PB_NON_EXTERNALIZED_STRINGS,
				PREF_PB_UNUSED_IMPORT, PREF_PB_UNUSED_LABEL,
				PREF_PB_STATIC_ACCESS_RECEIVER, PREF_PB_DEPRECATION_IN_DEPRECATED_CODE,
				PREF_PB_NO_EFFECT_ASSIGNMENT, PREF_PB_INCOMPATIBLE_INTERFACE_METHOD,
				PREF_PB_UNUSED_PRIVATE, PREF_PB_UNUSED_TYPE_PARAMETER, PREF_PB_CHAR_ARRAY_IN_CONCAT, PREF_PB_UNNECESSARY_ELSE,
				PREF_PB_POSSIBLE_ACCIDENTAL_BOOLEAN_ASSIGNMENT, PREF_PB_LOCAL_VARIABLE_HIDING, PREF_PB_FIELD_HIDING,
				PREF_PB_SPECIAL_PARAMETER_HIDING_FIELD, PREF_PB_INDIRECT_STATIC_ACCESS,
				PREF_PB_EMPTY_STATEMENT, PREF_PB_SIGNAL_PARAMETER_IN_OVERRIDING, PREF_PB_SIGNAL_PARAMETER_IN_ABSTRACT,
				PREF_PB_UNNECESSARY_TYPE_CHECK, PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION, PREF_PB_UNQUALIFIED_FIELD_ACCESS,
				PREF_PB_UNDOCUMENTED_EMPTY_BLOCK, PREF_PB_FINALLY_BLOCK_NOT_COMPLETING, PREF_PB_DEPRECATION_WHEN_OVERRIDING,
				PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_WHEN_OVERRIDING, PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_INCLUDE_DOC_COMMENT_REFERENCE,
				PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_EXEMPT_EXCEPTION_AND_THROWABLE,
				PREF_PB_MISSING_SERIAL_VERSION, PREF_PB_PARAMETER_ASSIGNMENT,
				PREF_PB_NULL_REFERENCE, PREF_PB_POTENTIAL_NULL_REFERENCE,
				PREF_ANNOTATION_NULL_ANALYSIS,
				INTR_DEFAULT_NULL_ANNOTATIONS,
				PREF_NULLABLE_ANNOTATION_NAME,
				PREF_NONNULL_ANNOTATION_NAME,
				PREF_NONNULL_BY_DEFAULT_ANNOTATION_NAME,
				PREF_MISSING_NONNULL_BY_DEFAULT_ANNOTATION,
				PREF_PB_NULL_SPECIFICATION_VIOLATION,
				PREF_PB_POTENTIAL_NULL_ANNOTATION_INFERENCE_CONFLICT,
				PREF_PB_NULL_UNCHECKED_CONVERSION,
				PREF_PB_REDUNDANT_NULL_ANNOTATION,
				PREF_PB_NONNULL_PARAMETER_ANNOTATION_DROPPED,
				PREF_PB_REDUNDANT_NULL_CHECK, PREF_PB_INCLUDE_ASSERTS_IN_NULL_ANALYSIS,
				PREF_PB_SYNTACTIC_NULL_ANLYSIS_FOR_FIELDS,
				PREF_INHERIT_NULL_ANNOTATIONS,
				PREF_PB_UNCLOSED_CLOSEABLE, PREF_PB_POTENTIALLY_UNCLOSED_CLOSEABLE, PREF_PB_EXPLICITLY_CLOSED_AUTOCLOSEABLE,
				PREF_PB_FALLTHROUGH_CASE, PREF_PB_REDUNDANT_SUPERINTERFACE, PREF_PB_UNUSED_WARNING_TOKEN,
				PREF_15_PB_UNCHECKED_TYPE_OPERATION, PREF_15_PB_FINAL_PARAM_BOUND, PREF_15_PB_VARARGS_ARGUMENT_NEED_CAST,
				PREF_15_PB_AUTOBOXING_PROBLEM, PREF_15_PB_MISSING_OVERRIDE_ANNOTATION, PREF_16_PB_MISSING_OVERRIDE_ANNOTATION_FOR_INTERFACE_METHOD_IMPLEMENTATION,
				PREF_15_PB_ANNOTATION_SUPER_INTERFACE,
				PREF_15_PB_TYPE_PARAMETER_HIDING,
				PREF_15_PB_INCOMPLETE_ENUM_SWITCH, PREF_15_PB_MISSING_ENUM_CASE_DESPITE_DEFAULT, PREF_15_PB_SWITCH_MISSING_DEFAULT_CASE,
				PREF_PB_MISSING_DEPRECATED_ANNOTATION,
				PREF_15_PB_RAW_TYPE_REFERENCE, PREF_15_PB_UNAVOIDABLE_GENERIC_TYPE_PROBLEMS, PREF_17_PB_REDUNDANT_TYPE_ARGUMENTS,
				PREF_PB_FATAL_OPTIONAL_ERROR,
				PREF_PB_FORBIDDEN_REFERENCE, PREF_PB_DISCOURRAGED_REFERENCE,
				PREF_PB_SUPPRESS_WARNINGS, PREF_PB_SUPPRESS_OPTIONAL_ERRORS,
				PREF_PB_UNHANDLED_WARNING_TOKEN,
				PREF_PB_COMPARING_IDENTICAL, PREF_PB_MISSING_SYNCHRONIZED_ON_INHERITED_METHOD, PREF_PB_MISSING_HASHCODE_METHOD,
				PREF_PB_DEAD_CODE, PREF_PB_UNUSED_OBJECT_ALLOCATION,
				PREF_PB_MISSING_STATIC_ON_METHOD, PREF_PB_POTENTIALLY_MISSING_STATIC_ON_METHOD
			};
	}

	/*
	 * @see org.eclipse.jface.preference.PreferencePage#createContents(Composite)
	 */
	@Override
	protected Control createContents(Composite parent) {
		fPixelConverter= new PixelConverter(parent);
		setShell(parent.getShell());

		Composite mainComp= new Composite(parent, SWT.NONE);
		mainComp.setFont(parent.getFont());
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		mainComp.setLayout(layout);

		createIgnoreOptionalProblemsLink(mainComp);
		
		Composite spacer= new Composite(mainComp, SWT.NONE);
		spacer.setLayoutData(new GridData(0, 0));
		
		Composite commonComposite= createStyleTabContent(mainComp);
		GridData gridData= new GridData(SWT.FILL, SWT.FILL, true, true);
		gridData.heightHint= fPixelConverter.convertHeightInCharsToPixels(30);
		commonComposite.setLayoutData(gridData);

		validateSettings(null, null, null);

		return mainComp;
	}

	private Composite createStyleTabContent(Composite folder) {
		String[] errorWarningIgnore= new String[] { ERROR, WARNING, IGNORE };
		String[] errorWarningIgnoreLabels= new String[] {
			PreferencesMessages.ProblemSeveritiesConfigurationBlock_error,
			PreferencesMessages.ProblemSeveritiesConfigurationBlock_warning,
			PreferencesMessages.ProblemSeveritiesConfigurationBlock_ignore
		};

		String[] errorWarning= new String[] { ERROR, WARNING };
		String[] errorWarningLabels= new String[] {
				PreferencesMessages.ProblemSeveritiesConfigurationBlock_error,
				PreferencesMessages.ProblemSeveritiesConfigurationBlock_warning
		};
		
		String[] enabledDisabled= new String[] { ENABLED, DISABLED };
		String[] disabledEnabled= new String[] { DISABLED, ENABLED };

		fFilteredPrefTree= new FilteredPreferenceTree(this, folder, PreferencesMessages.ProblemSeveritiesConfigurationBlock_common_description);
		final ScrolledPageContent sc1= fFilteredPrefTree.getScrolledPageContent();
		
		int nColumns= 3;

		Composite composite= sc1.getBody();
		GridLayout layout= new GridLayout(nColumns, false);
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		composite.setLayout(layout);

		final int defaultIndent= 0;
		final int extraIndent= LayoutUtil.getIndent();

		String label;
		ExpandableComposite excomposite;
		Composite inner;
		PreferenceTreeNode section;
		PreferenceTreeNode node;
		Key twistieKey;

		// --- style

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_section_code_style;
		twistieKey= OptionsConfigurationBlock.getLocalKey("ProblemSeveritiesConfigurationBlock_section_code_style"); //$NON-NLS-1$
		section= fFilteredPrefTree.addExpandableComposite(composite, label, nColumns, twistieKey, null, false);
		excomposite= getExpandableComposite(twistieKey);

		inner= createInnerComposite(excomposite, nColumns, composite.getFont());

		// - expression level
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_static_access_receiver_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_STATIC_ACCESS_RECEIVER, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_indirect_access_to_static_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_INDIRECT_STATIC_ACCESS, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unqualified_field_access_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNQUALIFIED_FIELD_ACCESS, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_synth_access_emul_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_SYNTHETIC_ACCESS_EMULATION, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_parameter_assignment;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_PARAMETER_ASSIGNMENT, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		// - statements level
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_non_externalized_strings_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_NON_EXTERNALIZED_STRINGS, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_undocumented_empty_block_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNDOCUMENTED_EMPTY_BLOCK, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_resource_not_managed_via_twr_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_EXPLICITLY_CLOSED_AUTOCLOSEABLE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		// - member level
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_method_naming_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_METHOD_WITH_CONSTRUCTOR_NAME, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_missing_static_on_method_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_MISSING_STATIC_ON_METHOD, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_potentially_missing_static_on_method_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_POTENTIALLY_MISSING_STATIC_ON_METHOD, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		// --- potential_programming_problems

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_section_potential_programming_problems;
		twistieKey= OptionsConfigurationBlock.getLocalKey("ProblemSeveritiesConfigurationBlock_section_potential_programming_problems"); //$NON-NLS-1$
		section= fFilteredPrefTree.addExpandableComposite(composite, label, nColumns, twistieKey, null, false);
		excomposite= getExpandableComposite(twistieKey);

		inner= createInnerComposite(excomposite, nColumns, composite.getFont());

		// - affecting expressions
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_comparing_identical;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_COMPARING_IDENTICAL, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_no_effect_assignment_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_NO_EFFECT_ASSIGNMENT, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_accidential_assignement_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_POSSIBLE_ACCIDENTAL_BOOLEAN_ASSIGNMENT, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_autoboxing_problem_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_15_PB_AUTOBOXING_PROBLEM, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_char_array_in_concat_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_CHAR_ARRAY_IN_CONCAT, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		// - affecting method invocations
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_inexact_vararg_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_15_PB_VARARGS_ARGUMENT_NEED_CAST, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		// - affecting single statements
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_empty_statement_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_EMPTY_STATEMENT, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unused_object_allocation_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNUSED_OBJECT_ALLOCATION, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_incomplete_enum_switch_label;
		node= fFilteredPrefTree.addComboBox(inner, label, PREF_15_PB_INCOMPLETE_ENUM_SWITCH, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_missing_enum_case_despite_default;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_15_PB_MISSING_ENUM_CASE_DESPITE_DEFAULT, enabledDisabled, extraIndent, node);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_switch_missing_default_case_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_15_PB_SWITCH_MISSING_DEFAULT_CASE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_fall_through_case;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_FALLTHROUGH_CASE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_hidden_catchblock_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_HIDDEN_CATCH_BLOCK, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_finally_block_not_completing_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_FINALLY_BLOCK_NOT_COMPLETING, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		// - affecting code blocks
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_dead_code;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_DEAD_CODE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_resource_leak_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNCLOSED_CLOSEABLE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_potential_resource_leak_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_POTENTIALLY_UNCLOSED_CLOSEABLE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		// - affecting members
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_missing_serial_version_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_MISSING_SERIAL_VERSION, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_missing_synchronized_on_inherited_method;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_MISSING_SYNCHRONIZED_ON_INHERITED_METHOD, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_missing_hashcode_method;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_MISSING_HASHCODE_METHOD, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);


		// --- name_shadowing

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_section_name_shadowing;
		twistieKey= OptionsConfigurationBlock.getLocalKey("ProblemSeveritiesConfigurationBlock_section_name_shadowing"); //$NON-NLS-1$
		section= fFilteredPrefTree.addExpandableComposite(composite, label, nColumns, twistieKey, null, false);
		excomposite= getExpandableComposite(twistieKey);

		inner= createInnerComposite(excomposite, nColumns, composite.getFont());

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_field_hiding_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_FIELD_HIDING, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_local_variable_hiding_label;
		node= fFilteredPrefTree.addComboBox(inner, label, PREF_PB_LOCAL_VARIABLE_HIDING, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_special_param_hiding_label;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_PB_SPECIAL_PARAMETER_HIDING_FIELD, enabledDisabled, extraIndent, node);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_type_parameter_hiding_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_15_PB_TYPE_PARAMETER_HIDING, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_overriding_pkg_dflt_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_OVERRIDING_PACKAGE_DEFAULT_METHOD, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_incompatible_interface_method_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_INCOMPATIBLE_INTERFACE_METHOD, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		// --- API access rules

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_section_deprecations;
		twistieKey= OptionsConfigurationBlock.getLocalKey("ProblemSeveritiesConfigurationBlock_section_deprecations"); //$NON-NLS-1$
		section= fFilteredPrefTree.addExpandableComposite(composite, label, nColumns, twistieKey, null, false);
		excomposite= getExpandableComposite(twistieKey);

		inner= createInnerComposite(excomposite, nColumns, composite.getFont());

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_deprecation_label;
		node= fFilteredPrefTree.addComboBox(inner, label, PREF_PB_DEPRECATION, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_deprecation_in_deprecation_label;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_PB_DEPRECATION_IN_DEPRECATED_CODE, enabledDisabled, extraIndent, node);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_deprecation_when_overriding_label;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_PB_DEPRECATION_WHEN_OVERRIDING, enabledDisabled, extraIndent, node);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_forbidden_reference_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_FORBIDDEN_REFERENCE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_discourraged_reference_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_DISCOURRAGED_REFERENCE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);


		// --- unnecessary_code

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_section_unnecessary_code;
		twistieKey= OptionsConfigurationBlock.getLocalKey("ProblemSeveritiesConfigurationBlock_section_unnecessary_code"); //$NON-NLS-1$
		section= fFilteredPrefTree.addExpandableComposite(composite, label, nColumns, twistieKey, null, false);
		excomposite= getExpandableComposite(twistieKey);

		inner= createInnerComposite(excomposite, nColumns, composite.getFont());

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unused_local_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNUSED_LOCAL, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unused_parameter_label;
		node= fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNUSED_PARAMETER, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_signal_param_in_overriding_label;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_PB_SIGNAL_PARAMETER_IN_OVERRIDING, disabledEnabled, extraIndent, node);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unused_type_parameter;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNUSED_TYPE_PARAMETER, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_ignore_documented_unused_parameters;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_PB_UNUSED_PARAMETER_INCLUDE_DOC_COMMENT_REFERENCE, enabledDisabled, defaultIndent, node);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unused_imports_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNUSED_IMPORT, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unused_private_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNUSED_PRIVATE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unnecessary_else_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNNECESSARY_ELSE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unnecessary_type_check_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNNECESSARY_TYPE_CHECK, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unused_throwing_exception_label;
		node= fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unused_throwing_exception_when_overriding_label;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_WHEN_OVERRIDING, disabledEnabled, extraIndent, node);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_ignore_documented_unused_exceptions;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_INCLUDE_DOC_COMMENT_REFERENCE, enabledDisabled, extraIndent, node);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unused_throwing_exception_ignore_unchecked_label;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_EXEMPT_EXCEPTION_AND_THROWABLE, enabledDisabled, extraIndent, node);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unused_label_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNUSED_LABEL, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_redundant_super_interface_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_REDUNDANT_SUPERINTERFACE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		// --- generics

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_section_generics;
		twistieKey= OptionsConfigurationBlock.getLocalKey("ProblemSeveritiesConfigurationBlock_section_generics"); //$NON-NLS-1$
		section= fFilteredPrefTree.addExpandableComposite(composite, label, nColumns, twistieKey, null, false);
		excomposite= getExpandableComposite(twistieKey);

		inner= createInnerComposite(excomposite, nColumns, composite.getFont());

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unsafe_type_op_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_15_PB_UNCHECKED_TYPE_OPERATION, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_raw_type_reference;
		fFilteredPrefTree.addComboBox(inner, label, PREF_15_PB_RAW_TYPE_REFERENCE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_final_param_bound_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_15_PB_FINAL_PARAM_BOUND, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_redundant_type_arguments_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_17_PB_REDUNDANT_TYPE_ARGUMENTS, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unavoidable_generic_type_problems;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_15_PB_UNAVOIDABLE_GENERIC_TYPE_PROBLEMS, disabledEnabled, defaultIndent, section);
		
		// --- annotations

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_section_annotations;
		twistieKey= OptionsConfigurationBlock.getLocalKey("ProblemSeveritiesConfigurationBlock_section_annotations"); //$NON-NLS-1$
		section= fFilteredPrefTree.addExpandableComposite(composite, label, nColumns, twistieKey, null, false);
		excomposite= getExpandableComposite(twistieKey);

		inner= createInnerComposite(excomposite, nColumns, composite.getFont());

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_missing_override_annotation_label;
		node= fFilteredPrefTree.addComboBox(inner, label, PREF_15_PB_MISSING_OVERRIDE_ANNOTATION, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_missing_override_annotation_for_interface_method_implementations_label;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_16_PB_MISSING_OVERRIDE_ANNOTATION_FOR_INTERFACE_METHOD_IMPLEMENTATION, enabledDisabled, extraIndent, node);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_missing_deprecated_annotation_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_MISSING_DEPRECATED_ANNOTATION, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_annotation_super_interface_label;
		fFilteredPrefTree.addComboBox(inner, label, PREF_15_PB_ANNOTATION_SUPER_INTERFACE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_unhandled_surpresswarning_tokens;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNHANDLED_WARNING_TOKEN, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_enable_surpresswarning_annotation;
		node= fFilteredPrefTree.addCheckBox(inner, label, PREF_PB_SUPPRESS_WARNINGS, enabledDisabled, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_unused_suppresswarnings_token;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_UNUSED_WARNING_TOKEN, errorWarningIgnore, errorWarningIgnoreLabels, extraIndent, node);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_suppress_optional_errors_label;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_PB_SUPPRESS_OPTIONAL_ERRORS, enabledDisabled, extraIndent, node);
		
		// --- null analysis

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_section_null_analysis;
		twistieKey= OptionsConfigurationBlock.getLocalKey("ProblemSeveritiesConfigurationBlock_section_null_analysis"); //$NON-NLS-1$
		section= fFilteredPrefTree.addExpandableComposite(composite, label, nColumns, twistieKey, null, false);
		excomposite= getExpandableComposite(twistieKey);

		inner= createInnerComposite(excomposite, nColumns, composite.getFont());
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_null_reference;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_NULL_REFERENCE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_potential_null_reference;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_POTENTIAL_NULL_REFERENCE, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_redundant_null_check;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_REDUNDANT_NULL_CHECK, errorWarningIgnore, errorWarningIgnoreLabels, defaultIndent, section);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_include_assert_in_null_analysis;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_PB_INCLUDE_ASSERTS_IN_NULL_ANALYSIS, enabledDisabled, defaultIndent, section);
		
		// --- annotation-based nulll analysis
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_enable_annotation_null_analysis;
		node= fFilteredPrefTree.addCheckBox(inner, label, PREF_ANNOTATION_NULL_ANALYSIS, enabledDisabled, defaultIndent, section);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_null_spec_violation;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_NULL_SPECIFICATION_VIOLATION, errorWarning, errorWarningLabels, extraIndent, node);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_null_annotation_inference_conflict;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_POTENTIAL_NULL_ANNOTATION_INFERENCE_CONFLICT, errorWarningIgnore, errorWarningIgnoreLabels, extraIndent, node);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_null_unchecked_conversion;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_NULL_UNCHECKED_CONVERSION, errorWarningIgnore, errorWarningIgnoreLabels, extraIndent, node);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_redundant_null_annotation;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_REDUNDANT_NULL_ANNOTATION, errorWarningIgnore, errorWarningIgnoreLabels, extraIndent, node);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_pb_nonnull_parameter_annotation_dropped;
		fFilteredPrefTree.addComboBox(inner, label, PREF_PB_NONNULL_PARAMETER_ANNOTATION_DROPPED, errorWarningIgnore, errorWarningIgnoreLabels, extraIndent, node);

		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_missing_nonnull_by_default_annotation;
		fFilteredPrefTree.addComboBox(inner, label, PREF_MISSING_NONNULL_BY_DEFAULT_ANNOTATION, errorWarningIgnore, errorWarningIgnoreLabels, extraIndent, node);
		
		label= PreferencesMessages.NullAnnotationsConfigurationDialog_use_default_annotations_for_null;
		fFilteredPrefTree.addCheckBoxWithLink(inner, label, INTR_DEFAULT_NULL_ANNOTATIONS, enabledDisabled, extraIndent, node, true, SWT.DEFAULT,
				new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						openNullAnnotationsConfigurationDialog();
					}
				});
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_inherit_null_annotations;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_INHERIT_NULL_ANNOTATIONS, enabledDisabled, extraIndent, node);
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_enable_syntactic_null_analysis_for_fields;
		fFilteredPrefTree.addCheckBox(inner, label, PREF_PB_SYNTACTIC_NULL_ANLYSIS_FOR_FIELDS, enabledDisabled, extraIndent, node);
		
		// --- global
		
		// add some vertical space before:
		GridData gd= new GridData();
		gd.verticalIndent= fPixelConverter.convertHeightInCharsToPixels(1);
		gd.horizontalSpan= 2;
		
		label= PreferencesMessages.ProblemSeveritiesConfigurationBlock_treat_optional_as_fatal;
		addCheckBox(composite, label, PREF_PB_FATAL_OPTIONAL_ERROR, enabledDisabled, defaultIndent).setLayoutData(gd);

		IDialogSettings settingsSection= JavaPlugin.getDefault().getDialogSettings().getSection(SETTINGS_SECTION_NAME);
		restoreSectionExpansionStates(settingsSection);

		return sc1;
	}

	private void openNullAnnotationsConfigurationDialog() {
		NullAnnotationsConfigurationDialog dialog= new NullAnnotationsConfigurationDialog();
		int result= dialog.open();
		if (result == Window.OK) {
			String[] annotationNames= dialog.getResult();
			setValue(PREF_NULLABLE_ANNOTATION_NAME, annotationNames[0]);
			setValue(PREF_NONNULL_ANNOTATION_NAME, annotationNames[1]);
			setValue(PREF_NONNULL_BY_DEFAULT_ANNOTATION_NAME, annotationNames[2]);
		}
		updateNullAnnotationsSetting();
	}

	private Composite createInnerComposite(ExpandableComposite excomposite, int nColumns, Font font) {
		Composite inner= new Composite(excomposite, SWT.NONE);
		inner.setFont(font);
		inner.setLayout(new GridLayout(nColumns, false));
		excomposite.setClient(inner);
		return inner;
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
			if (PREF_PB_UNUSED_PARAMETER.equals(changedKey) ||
					PREF_PB_DEPRECATION.equals(changedKey) ||
					PREF_PB_LOCAL_VARIABLE_HIDING.equals(changedKey) ||
					PREF_15_PB_INCOMPLETE_ENUM_SWITCH.equals(changedKey) ||
					PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION.equals(changedKey) ||
					PREF_PB_SUPPRESS_WARNINGS.equals(changedKey) ||
					PREF_ANNOTATION_NULL_ANALYSIS.equals(changedKey)) {
				updateEnableStates();
			}
			
			if (checkValue(PREF_ANNOTATION_NULL_ANALYSIS, ENABLED)
					&& (PREF_ANNOTATION_NULL_ANALYSIS.equals(changedKey)
							|| PREF_PB_NULL_REFERENCE.equals(changedKey)
							|| PREF_PB_POTENTIAL_NULL_REFERENCE.equals(changedKey)
							|| PREF_PB_NULL_SPECIFICATION_VIOLATION.equals(changedKey)
							|| PREF_PB_POTENTIAL_NULL_ANNOTATION_INFERENCE_CONFLICT.equals(changedKey))) {
				boolean badNullRef= lessSevere(getValue(PREF_PB_NULL_REFERENCE), getValue(PREF_PB_NULL_SPECIFICATION_VIOLATION));
				boolean badPotNullRef= lessSevere(getValue(PREF_PB_POTENTIAL_NULL_REFERENCE), getValue(PREF_PB_POTENTIAL_NULL_ANNOTATION_INFERENCE_CONFLICT));
				boolean ask= false;
				ask |= badNullRef && (PREF_PB_NULL_REFERENCE.equals(changedKey) || PREF_PB_NULL_SPECIFICATION_VIOLATION.equals(changedKey));
				ask |= badPotNullRef && (PREF_PB_POTENTIAL_NULL_REFERENCE.equals(changedKey) || PREF_PB_POTENTIAL_NULL_ANNOTATION_INFERENCE_CONFLICT.equals(changedKey));
				ask |= (badNullRef || badPotNullRef) && PREF_ANNOTATION_NULL_ANALYSIS.equals(changedKey);
				if (ask) {
					final Combo comboBoxNullRef= getComboBox(PREF_PB_NULL_REFERENCE);
					final Label labelNullRef= fLabels.get(comboBoxNullRef);
					int highlightNullRef= getHighlight(labelNullRef);
					final Combo comboBoxPotNullRef= getComboBox(PREF_PB_POTENTIAL_NULL_REFERENCE);
					final Label labelPotNullRef= fLabels.get(comboBoxPotNullRef);
					int highlightPotNullRef= getHighlight(labelPotNullRef);
					
					getShell().getDisplay().asyncExec(new Runnable() {
						public void run() {
							highlight(comboBoxNullRef.getParent(), labelNullRef, comboBoxNullRef, HIGHLIGHT_FOCUS);
							highlight(comboBoxPotNullRef.getParent(), labelPotNullRef, comboBoxPotNullRef, HIGHLIGHT_FOCUS);
						}
					});
					
					MessageDialog messageDialog= new MessageDialog(
							getShell(),
							PreferencesMessages.ProblemSeveritiesConfigurationBlock_adapt_null_pointer_access_settings_dialog_title,
							null,
							PreferencesMessages.ProblemSeveritiesConfigurationBlock_adapt_null_pointer_access_settings_dialog_message,
							MessageDialog.QUESTION,
							new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL },
							0);
					messageDialog.create();
					Shell messageShell= messageDialog.getShell();
					messageShell.setLocation(messageShell.getLocation().x, getShell().getLocation().y + 40);
					if (messageDialog.open() == 0) {
						if (badNullRef) {
							setValue(PREF_PB_NULL_REFERENCE, getValue(PREF_PB_NULL_SPECIFICATION_VIOLATION));
							updateCombo(getComboBox(PREF_PB_NULL_REFERENCE));
						}
						if (badPotNullRef) {
							setValue(PREF_PB_POTENTIAL_NULL_REFERENCE, getValue(PREF_PB_POTENTIAL_NULL_ANNOTATION_INFERENCE_CONFLICT));
							updateCombo(getComboBox(PREF_PB_POTENTIAL_NULL_REFERENCE));
						}
					}
					
					highlight(comboBoxNullRef.getParent(), labelNullRef, comboBoxNullRef, highlightNullRef);
					highlight(comboBoxPotNullRef.getParent(), labelPotNullRef, comboBoxPotNullRef, highlightPotNullRef);
				}

			} else if (PREF_PB_SIGNAL_PARAMETER_IN_OVERRIDING.equals(changedKey)) {
				// merging the two options
				setValue(PREF_PB_SIGNAL_PARAMETER_IN_ABSTRACT, newValue);
				
			} else if (INTR_DEFAULT_NULL_ANNOTATIONS.equals(changedKey)) {
				if (ENABLED.equals(newValue)) {
					setValue(PREF_NULLABLE_ANNOTATION_NAME, NULL_ANNOTATIONS_DEFAULTS[0]);
					setValue(PREF_NONNULL_ANNOTATION_NAME, NULL_ANNOTATIONS_DEFAULTS[1]);
					setValue(PREF_NONNULL_BY_DEFAULT_ANNOTATION_NAME, NULL_ANNOTATIONS_DEFAULTS[2]);
				} else {
					openNullAnnotationsConfigurationDialog();
				}
				
			} else {
				return;
			}
		} else {
			updateEnableStates();
			updateNullAnnotationsSetting();
		}
	}

	private static boolean lessSevere(String errorWarningIgnore, String errorWarningIgnore2) {
		if (IGNORE.equals(errorWarningIgnore))
			return ! IGNORE.equals(errorWarningIgnore2);
		else if (WARNING.equals(errorWarningIgnore))
			return ERROR.equals(errorWarningIgnore2);
		else
			return false;
	}

	private void updateNullAnnotationsSetting() {
		String[] annotationNames= {
				getValue(PREF_NULLABLE_ANNOTATION_NAME),
				getValue(PREF_NONNULL_ANNOTATION_NAME),
				getValue(PREF_NONNULL_BY_DEFAULT_ANNOTATION_NAME)
		};
		String defaultNullAnnotationsValue= Arrays.equals(annotationNames, NULL_ANNOTATIONS_DEFAULTS) ? ENABLED : DISABLED;
		setValue(INTR_DEFAULT_NULL_ANNOTATIONS, defaultNullAnnotationsValue);
		updateCheckBox(getCheckBox(INTR_DEFAULT_NULL_ANNOTATIONS));
	}

	private void updateEnableStates() {
		boolean enableUnusedParams= !checkValue(PREF_PB_UNUSED_PARAMETER, IGNORE);
		getCheckBox(PREF_PB_SIGNAL_PARAMETER_IN_OVERRIDING).setEnabled(enableUnusedParams);

		boolean enableDeprecation= !checkValue(PREF_PB_DEPRECATION, IGNORE);
		getCheckBox(PREF_PB_DEPRECATION_IN_DEPRECATED_CODE).setEnabled(enableDeprecation);
		getCheckBox(PREF_PB_DEPRECATION_WHEN_OVERRIDING).setEnabled(enableDeprecation);

		boolean enableThrownExceptions= !checkValue(PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION, IGNORE);
		getCheckBox(PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_WHEN_OVERRIDING).setEnabled(enableThrownExceptions);
		getCheckBox(PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_INCLUDE_DOC_COMMENT_REFERENCE).setEnabled(enableThrownExceptions);
		getCheckBox(PREF_PB_UNUSED_DECLARED_THROWN_EXCEPTION_EXEMPT_EXCEPTION_AND_THROWABLE).setEnabled(enableThrownExceptions);

		boolean enableHiding= !checkValue(PREF_PB_LOCAL_VARIABLE_HIDING, IGNORE);
		getCheckBox(PREF_PB_SPECIAL_PARAMETER_HIDING_FIELD).setEnabled(enableHiding);
		
		boolean enableMissingEnumDespiteDefault= !checkValue(PREF_15_PB_INCOMPLETE_ENUM_SWITCH, IGNORE);
		getCheckBox(PREF_15_PB_MISSING_ENUM_CASE_DESPITE_DEFAULT).setEnabled(enableMissingEnumDespiteDefault);
		
		boolean enableSuppressWarnings= checkValue(PREF_PB_SUPPRESS_WARNINGS, ENABLED);
		getCheckBox(PREF_PB_SUPPRESS_OPTIONAL_ERRORS).setEnabled(enableSuppressWarnings);
		setComboEnabled(PREF_PB_UNUSED_WARNING_TOKEN, enableSuppressWarnings);
		
		boolean enableAnnotationNullAnalysis= checkValue(PREF_ANNOTATION_NULL_ANALYSIS, ENABLED);
		getCheckBox(INTR_DEFAULT_NULL_ANNOTATIONS).setEnabled(enableAnnotationNullAnalysis);
		getCheckBoxLink(INTR_DEFAULT_NULL_ANNOTATIONS).setEnabled(enableAnnotationNullAnalysis);
		setComboEnabled(PREF_PB_NULL_SPECIFICATION_VIOLATION, enableAnnotationNullAnalysis);
		setComboEnabled(PREF_PB_POTENTIAL_NULL_ANNOTATION_INFERENCE_CONFLICT, enableAnnotationNullAnalysis);
		setComboEnabled(PREF_PB_NULL_UNCHECKED_CONVERSION, enableAnnotationNullAnalysis);
		setComboEnabled(PREF_PB_REDUNDANT_NULL_ANNOTATION, enableAnnotationNullAnalysis);
		setComboEnabled(PREF_PB_NONNULL_PARAMETER_ANNOTATION_DROPPED, enableAnnotationNullAnalysis);
		setComboEnabled(PREF_MISSING_NONNULL_BY_DEFAULT_ANNOTATION, enableAnnotationNullAnalysis);
		getCheckBox(PREF_INHERIT_NULL_ANNOTATIONS).setEnabled(enableAnnotationNullAnalysis);
		getCheckBox(PREF_PB_SYNTACTIC_NULL_ANLYSIS_FOR_FIELDS).setEnabled(enableAnnotationNullAnalysis);
	}

	private IStatus validateNullnessAnnotation(String value, String errorMessage) {
		StatusInfo status= new StatusInfo();
		if (JavaConventions.validateJavaTypeName(value, JavaCore.VERSION_1_5, JavaCore.VERSION_1_5).matches(IStatus.ERROR)
				|| value.indexOf('.') == -1)
			status.setError(errorMessage);
		return status;
	}
	
	@Override
	protected String[] getFullBuildDialogStrings(boolean workspaceSettings) {
		String title= PreferencesMessages.ProblemSeveritiesConfigurationBlock_needsbuild_title;
		String message;
		if (workspaceSettings) {
			message= PreferencesMessages.ProblemSeveritiesConfigurationBlock_needsfullbuild_message;
		} else {
			message= PreferencesMessages.ProblemSeveritiesConfigurationBlock_needsprojectbuild_message;
		}
		return new String[] { title, message };
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#dispose()
	 */
	@Override
	public void dispose() {
		IDialogSettings section= JavaPlugin.getDefault().getDialogSettings().addNewSection(SETTINGS_SECTION_NAME);
		storeSectionExpansionStates(section);
		super.dispose();
	}

}

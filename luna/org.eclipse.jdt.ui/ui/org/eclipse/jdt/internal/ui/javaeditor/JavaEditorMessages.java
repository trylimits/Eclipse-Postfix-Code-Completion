/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Andre Soereng <andreis@fast.no> - [syntax highlighting] highlight numbers - https://bugs.eclipse.org/bugs/show_bug.cgi?id=63573
 *     Björn Michael <b.michael@gmx.de> - [syntax highlighting] Syntax coloring for abstract classes - https://bugs.eclipse.org/331311
 *     Björn Michael <b.michael@gmx.de> - [syntax highlighting] Add highlight for inherited fields - https://bugs.eclipse.org/348368
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.javaeditor;

import java.util.ResourceBundle;

import org.eclipse.osgi.util.NLS;

/**
 * Helper class to get NLSed messages.
 */
final class JavaEditorMessages extends NLS {

	private static final String BUNDLE_FOR_CONSTRUCTED_KEYS= "org.eclipse.jdt.internal.ui.javaeditor.ConstructedJavaEditorMessages";//$NON-NLS-1$
	private static ResourceBundle fgBundleForConstructedKeys= ResourceBundle.getBundle(BUNDLE_FOR_CONSTRUCTED_KEYS);

	/**
	 * Returns the message bundle which contains constructed keys.
	 *
	 * @since 3.1
	 * @return the message bundle
	 */
	public static ResourceBundle getBundleForConstructedKeys() {
		return fgBundleForConstructedKeys;
	}

	private static final String BUNDLE_NAME= JavaEditorMessages.class.getName();


	private JavaEditorMessages() {
		// Do not instantiate
	}

	public static String AddImportOnSelection_label;
	public static String AddImportOnSelection_tooltip;
	public static String AddImportOnSelection_description;
	public static String AddImportOnSelection_error_title;
	public static String AddImportOnSelection_dialog_title;
	public static String AddImportOnSelection_dialog_message;
	public static String ClassFileEditor_error_classfile_not_on_classpath;
	public static String ClassFileEditor_error_invalid_input_message;
	public static String ClassFileEditor_error_title;
	public static String ClassFileEditor_error_message;
	public static String ClassFileEditor_SourceAttachmentForm_cannotconfigure;
	public static String ClassFileEditor_SourceAttachmentForm_notsupported;
	public static String ClassFileEditor_SourceAttachmentForm_readonly;
	public static String ClassFileMarkerAnnotationModel_error_isAcceptable;
	public static String ClassFileMarkerAnnotationModel_error_isAffected;
	public static String ClassFileMarkerAnnotationModel_error_resourceChanged;
	public static String CompilationUnitDocumentProvider_progressNotifyingSaveParticipants;
	public static String CompilationUnitDocumentProvider_error_saveParticipantProblem;
	public static String CompilationUnitDocumentProvider_calculatingChangedRegions_message;
	public static String CompilationUnitDocumentProvider_error_calculatingChangedRegions;
	public static String CompilationUnitDocumentProvider_error_saveParticipantSavedFile;
	public static String CompilationUnitDocumentProvider_error_saveParticipantFailed;
	public static String CompilationUnitDocumentProvider_error_outOfSync;
	public static String CompilationUnitEditor_error_saving_message1;
	public static String CompilationUnitEditor_error_saving_title1;
	public static String CompilationUnitEditor_error_saving_editedLines_calculation_message;
	public static String CompilationUnitEditor_error_saving_editedLines_calculation_link;
	public static String CompilationUnitEditor_error_saving_editedLines_calculation_property_link;
	public static String CompilationUnitEditor_error_saving_participant_message;
	public static String CompilationUnitEditor_error_saving_participant_link;
	public static String CompilationUnitEditor_error_saving_participant_property_link;
	public static String EditorUtility_file_must_not_be_null;
	public static String EditorUtility_no_active_WorkbenchPage;
	public static String EditorUtility_no_editorInput;
	public static String JavaOutlinePage_Sort_label;
	public static String JavaOutlinePage_Sort_tooltip;
	public static String JavaOutlinePage_Sort_description;
	public static String JavaOutlinePage_GoIntoTopLevelType_label;
	public static String JavaOutlinePage_GoIntoTopLevelType_tooltip;
	public static String JavaOutlinePage_GoIntoTopLevelType_description;
	public static String JavaOutlinePage_error_NoTopLevelType;
	public static String ToggleComment_error_title;
	public static String ToggleComment_error_message;
	public static String ContentAssistProposal_label;
	public static String Editor_FoldingMenu_name;
	public static String CompilationUnitDocumentProvider_saveAsTargetOpenInEditor;
	public static String ClassFileDocumentProvider_error_createElementInfo;
	public static String ExpandSelectionMenu_label;
	public static String GotoMatchingBracket_label;
	public static String GotoMatchingBracket_error_noMatchingBracket;
	public static String GotoMatchingBracket_error_bracketOutsideSelectedElement;
	public static String ShowInBreadcrumbAction_label;
	public static String SourceAttachmentForm_title;
	public static String SourceAttachmentForm_heading;
	public static String SourceAttachmentForm_message_noSource;
	public static String SourceAttachmentForm_message_noSourceAttachment;
	public static String SourceAttachmentForm_message_pressButtonToAttach;
	public static String SourceAttachmentForm_message_noSourceInAttachment;
	public static String SourceAttachmentForm_message_pressButtonToChange;
	public static String SourceAttachmentForm_button_attachSource;
	public static String SourceAttachmentForm_button_changeAttachedSource;
	public static String SourceAttachmentForm_error_title;
	public static String SourceAttachmentForm_error_message;
	public static String SourceAttachmentForm_attach_error_title;
	public static String SourceAttachmentForm_attach_error_message;
	public static String EditorUtility_concatModifierStrings;
	public static String OverrideIndicatorManager_implements;
	public static String OverrideIndicatorManager_intallJob;
	public static String OverrideIndicatorManager_overrides;
	public static String OverrideIndicatorManager_open_error_title;
	public static String OverrideIndicatorManager_open_error_message;
	public static String OverrideIndicatorManager_open_error_messageHasLogEntry;
	public static String SemanticHighlighting_job;
	public static String SemanticHighlighting_field;
	public static String SemanticHighlighting_staticField;
	public static String SemanticHighlighting_staticFinalField;
	public static String SemanticHighlighting_inheritedField;
	public static String SemanticHighlighting_methodDeclaration;
	public static String SemanticHighlighting_staticMethodInvocation;
	public static String SemanticHighlighting_annotationElementReference;
	public static String SemanticHighlighting_abstractClasses;
	public static String SemanticHighlighting_abstractMethodInvocation;
	public static String SemanticHighlighting_inheritedMethodInvocation;
	public static String SemanticHighlighting_localVariableDeclaration;
	public static String SemanticHighlighting_localVariable;
	public static String SemanticHighlighting_parameterVariable;
	public static String SemanticHighlighting_deprecatedMember;
	public static String SemanticHighlighting_typeVariables;
	public static String SemanticHighlighting_method;
	public static String SemanticHighlighting_autoboxing;
	public static String SemanticHighlighting_numbers;
	public static String SemanticHighlighting_classes;
	public static String SemanticHighlighting_enums;
	public static String SemanticHighlighting_interfaces;
	public static String SemanticHighlighting_annotations;
	public static String SemanticHighlighting_typeArguments;
	public static String JavaEditor_FormatElementAction_description;
	public static String JavaEditor_FormatElementAction_label;
	public static String JavaEditor_FormatElementDialog_label;
	public static String JavaEditor_markOccurrences_job_name;
	public static String JavaEditorBreadcrumbActionGroup_go_to_editor_action_label;
	public static String JavaElementHyperlink_hyperlinkText;
	public static String JavaElementHyperlink_hyperlinkText_qualified;
	public static String Editor_OpenPropertiesFile_error_keyNotFound;
	public static String Editor_OpenPropertiesFile_error_fileNotFound_dialogMessage;
	public static String Editor_OpenPropertiesFile_error_openEditor_dialogMessage;
	public static String Editor_OpenPropertiesFile_hyperlinkText;
	public static String Editor_MoveLines_IllegalMove_status;
	public static String BasicEditorActionContributor_specific_content_assist_menu;
	public static String JavaElementImplementationHyperlink_error_no_implementations_found_message;
	public static String JavaElementImplementationHyperlink_error_status_message;
	public static String JavaElementImplementationHyperlink_hyperlinkText;
	public static String JavaElementImplementationHyperlink_hyperlinkText_qualified;
	public static String JavaElementImplementationHyperlink_search_method_implementors;
	public static String JavaElementDeclaredTypeHyperlink_hyperlinkText_qualified;
	public static String JavaElementDeclaredTypeHyperlink_hyperlinkText_qualified_signature;
	public static String JavaElementDeclaredTypeHyperlink_hyperlinkText;
	public static String JavaElementDeclaredTypeHyperlink_error_msg;
	public static String JavaElementReturnTypeHyperlink_hyperlinkText_qualified;
	public static String JavaElementReturnTypeHyperlink_hyperlinkText;
	public static String JavaElementReturnTypeHyperlink_error_msg;
	public static String JavaElementSuperImplementationHyperlink_hyperlinkText;
	public static String JavaElementSuperImplementationHyperlink_hyperlinkText_qualified;

	static {
		NLS.initializeMessages(BUNDLE_NAME, JavaEditorMessages.class);
	}

}

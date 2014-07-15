/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;

import org.eclipse.ui.IWorkbenchPreferenceConstants;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.IColorManager;
import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.java.JavaCompletionProcessor;
import org.eclipse.jdt.internal.ui.text.javadoc.JavadocCompletionProcessor;


public class ContentAssistPreference {

	/** Preference key for content assist auto activation */
	private final static String AUTOACTIVATION=  PreferenceConstants.CODEASSIST_AUTOACTIVATION;
	/** Preference key for content assist auto activation delay */
	private final static String AUTOACTIVATION_DELAY=  PreferenceConstants.CODEASSIST_AUTOACTIVATION_DELAY;
	/** Preference key for content assist parameters color */
	private final static String PARAMETERS_FOREGROUND=  PreferenceConstants.CODEASSIST_PARAMETERS_FOREGROUND;
	/** Preference key for content assist parameters color */
	private final static String PARAMETERS_BACKGROUND=  PreferenceConstants.CODEASSIST_PARAMETERS_BACKGROUND;
	/** Preference key for content assist auto insert */
	private final static String AUTOINSERT= PreferenceConstants.CODEASSIST_AUTOINSERT;

	/** Preference key for java content assist auto activation triggers */
	private final static String AUTOACTIVATION_TRIGGERS_JAVA= PreferenceConstants.CODEASSIST_AUTOACTIVATION_TRIGGERS_JAVA;
	/** Preference key for javadoc content assist auto activation triggers */
	private final static String AUTOACTIVATION_TRIGGERS_JAVADOC= PreferenceConstants.CODEASSIST_AUTOACTIVATION_TRIGGERS_JAVADOC;

	/** Preference key for visibility of proposals */
	private final static String SHOW_VISIBLE_PROPOSALS= PreferenceConstants.CODEASSIST_SHOW_VISIBLE_PROPOSALS;
	/** Preference key for case sensitivity of proposals */
	private final static String CASE_SENSITIVITY= PreferenceConstants.CODEASSIST_CASE_SENSITIVITY;
	/** Preference key for adding imports on code assist */
	/** Preference key for filling argument names on method completion */
	private static final String FILL_METHOD_ARGUMENTS= PreferenceConstants.CODEASSIST_FILL_ARGUMENT_NAMES;
	/** Preference key for prefix completion. */
	private static final String PREFIX_COMPLETION= PreferenceConstants.CODEASSIST_PREFIX_COMPLETION;
	/**
	 * Preference key for colored labels.
	 * @since 3.4
	 */
	private static final String USE_COLORED_LABELS= IWorkbenchPreferenceConstants.USE_COLORED_LABELS;


	private static Color getColor(IPreferenceStore store, String key, IColorManager manager) {
		RGB rgb= PreferenceConverter.getColor(store, key);
		return manager.getColor(rgb);
	}

	private static Color getColor(IPreferenceStore store, String key) {
		JavaTextTools textTools= JavaPlugin.getDefault().getJavaTextTools();
		return getColor(store, key, textTools.getColorManager());
	}

	private static JavaCompletionProcessor getJavaProcessor(ContentAssistant assistant) {
		IContentAssistProcessor p= assistant.getContentAssistProcessor(IDocument.DEFAULT_CONTENT_TYPE);
		if (p instanceof JavaCompletionProcessor)
			return  (JavaCompletionProcessor) p;
		return null;
	}

	private static JavadocCompletionProcessor getJavaDocProcessor(ContentAssistant assistant) {
		IContentAssistProcessor p= assistant.getContentAssistProcessor(IJavaPartitions.JAVA_DOC);
		if (p instanceof JavadocCompletionProcessor)
			return (JavadocCompletionProcessor) p;
		return null;
	}

	private static void configureJavaProcessor(ContentAssistant assistant, IPreferenceStore store) {
		JavaCompletionProcessor jcp= getJavaProcessor(assistant);
		if (jcp == null)
			return;

		String triggers= store.getString(AUTOACTIVATION_TRIGGERS_JAVA);
		if (triggers != null)
			jcp.setCompletionProposalAutoActivationCharacters(triggers.toCharArray());

		boolean enabled= store.getBoolean(SHOW_VISIBLE_PROPOSALS);
		jcp.restrictProposalsToVisibility(enabled);

		enabled= store.getBoolean(CASE_SENSITIVITY);
		jcp.restrictProposalsToMatchingCases(enabled);
	}

	private static void configureJavaDocProcessor(ContentAssistant assistant, IPreferenceStore store) {
		JavadocCompletionProcessor jdcp= getJavaDocProcessor(assistant);
		if (jdcp == null)
			return;

		String triggers= store.getString(AUTOACTIVATION_TRIGGERS_JAVADOC);
		if (triggers != null)
			jdcp.setCompletionProposalAutoActivationCharacters(triggers.toCharArray());

		boolean enabled= store.getBoolean(CASE_SENSITIVITY);
		jdcp.restrictProposalsToMatchingCases(enabled);
	}

	/**
	 * Configure the given content assistant from the given store.
	 *
	 * @param assistant the content assistant
	 * @param store the preference store
	 */
	public static void configure(ContentAssistant assistant, IPreferenceStore store) {

		JavaTextTools textTools= JavaPlugin.getDefault().getJavaTextTools();
		IColorManager manager= textTools.getColorManager();


		boolean enabled= store.getBoolean(AUTOACTIVATION);
		assistant.enableAutoActivation(enabled);

		int delay= store.getInt(AUTOACTIVATION_DELAY);
		assistant.setAutoActivationDelay(delay);

		Color c= getColor(store, PARAMETERS_FOREGROUND, manager);
		assistant.setContextInformationPopupForeground(c);
		assistant.setContextSelectorForeground(c);

		c= getColor(store, PARAMETERS_BACKGROUND, manager);
		assistant.setContextInformationPopupBackground(c);
		assistant.setContextSelectorBackground(c);

		enabled= store.getBoolean(AUTOINSERT);
		assistant.enableAutoInsert(enabled);

		enabled= store.getBoolean(PREFIX_COMPLETION);
		assistant.enablePrefixCompletion(enabled);

		enabled= store.getBoolean(USE_COLORED_LABELS);
		assistant.enableColoredLabels(enabled);


		configureJavaProcessor(assistant, store);
		configureJavaDocProcessor(assistant, store);
	}


	private static void changeJavaProcessor(ContentAssistant assistant, IPreferenceStore store, String key) {
		JavaCompletionProcessor jcp= getJavaProcessor(assistant);
		if (jcp == null)
			return;

		if (AUTOACTIVATION_TRIGGERS_JAVA.equals(key)) {
			String triggers= store.getString(AUTOACTIVATION_TRIGGERS_JAVA);
			if (triggers != null)
				jcp.setCompletionProposalAutoActivationCharacters(triggers.toCharArray());
		} else if (SHOW_VISIBLE_PROPOSALS.equals(key)) {
			boolean enabled= store.getBoolean(SHOW_VISIBLE_PROPOSALS);
			jcp.restrictProposalsToVisibility(enabled);
		} else if (CASE_SENSITIVITY.equals(key)) {
			boolean enabled= store.getBoolean(CASE_SENSITIVITY);
			jcp.restrictProposalsToMatchingCases(enabled);
		}
	}

	private static void changeJavaDocProcessor(ContentAssistant assistant, IPreferenceStore store, String key) {
		JavadocCompletionProcessor jdcp= getJavaDocProcessor(assistant);
		if (jdcp == null)
			return;

		if (AUTOACTIVATION_TRIGGERS_JAVADOC.equals(key)) {
			String triggers= store.getString(AUTOACTIVATION_TRIGGERS_JAVADOC);
			if (triggers != null)
				jdcp.setCompletionProposalAutoActivationCharacters(triggers.toCharArray());
		} else if (CASE_SENSITIVITY.equals(key)) {
			boolean enabled= store.getBoolean(CASE_SENSITIVITY);
			jdcp.restrictProposalsToMatchingCases(enabled);
		}
	}

	/**
	 * Changes the configuration of the given content assistant according to the given property
	 * change event and the given preference store.
	 *
	 * @param assistant the content assistant
	 * @param store the preference store
	 * @param event the property change event
	 */
	public static void changeConfiguration(ContentAssistant assistant, IPreferenceStore store, PropertyChangeEvent event) {

		String p= event.getProperty();

		if (AUTOACTIVATION.equals(p)) {
			boolean enabled= store.getBoolean(AUTOACTIVATION);
			assistant.enableAutoActivation(enabled);
		} else if (AUTOACTIVATION_DELAY.equals(p)) {
			int delay= store.getInt(AUTOACTIVATION_DELAY);
			assistant.setAutoActivationDelay(delay);
		} else if (PARAMETERS_FOREGROUND.equals(p)) {
			Color c= getColor(store, PARAMETERS_FOREGROUND);
			assistant.setContextInformationPopupForeground(c);
			assistant.setContextSelectorForeground(c);
		} else if (PARAMETERS_BACKGROUND.equals(p)) {
			Color c= getColor(store, PARAMETERS_BACKGROUND);
			assistant.setContextInformationPopupBackground(c);
			assistant.setContextSelectorBackground(c);
		} else if (AUTOINSERT.equals(p)) {
			boolean enabled= store.getBoolean(AUTOINSERT);
			assistant.enableAutoInsert(enabled);
		} else if (PREFIX_COMPLETION.equals(p)) {
			boolean enabled= store.getBoolean(PREFIX_COMPLETION);
			assistant.enablePrefixCompletion(enabled);
		} else if (USE_COLORED_LABELS.equals(p)) {
			boolean enabled= store.getBoolean(USE_COLORED_LABELS);
			assistant.enableColoredLabels(enabled);
		}

		changeJavaProcessor(assistant, store, p);
		changeJavaDocProcessor(assistant, store, p);
	}

	public static boolean fillArgumentsOnMethodCompletion(IPreferenceStore store) {
		return store.getBoolean(FILL_METHOD_ARGUMENTS);
	}
}


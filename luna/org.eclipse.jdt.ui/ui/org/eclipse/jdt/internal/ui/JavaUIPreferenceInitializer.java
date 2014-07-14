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
package org.eclipse.jdt.internal.ui;

import org.eclipse.swt.graphics.RGB;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.ColorRegistry;

import org.eclipse.ui.PlatformUI;

import org.eclipse.ui.editors.text.EditorsUI;

import org.eclipse.jdt.ui.PreferenceConstants;

public class JavaUIPreferenceInitializer extends AbstractPreferenceInitializer {

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
	 */
	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore store = PreferenceConstants.getPreferenceStore();

		EditorsUI.useAnnotationsPreferencePage(store);
		EditorsUI.useQuickDiffPreferencePage(store);
		PreferenceConstants.initializeDefaultValues(store);
	}

	public static void setThemeBasedPreferences(IPreferenceStore store, boolean fireEvent) {
		ColorRegistry registry= null;
		if (PlatformUI.isWorkbenchRunning())
			registry= PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getColorRegistry();

		setDefault(
				store,
				PreferenceConstants.EDITOR_STRING_COLOR,
				findRGB(registry, IJavaThemeConstants.EDITOR_STRING_COLOR, new RGB(42, 0, 255)), fireEvent);
		setDefault(
				store,
				PreferenceConstants.EDITOR_JAVA_DEFAULT_COLOR,
				findRGB(registry, IJavaThemeConstants.EDITOR_JAVA_DEFAULT_COLOR, new RGB(0, 0, 0)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.EDITOR_MULTI_LINE_COMMENT_COLOR,
				findRGB(registry, IJavaThemeConstants.EDITOR_MULTI_LINE_COMMENT_COLOR, new RGB(63, 127, 95)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.EDITOR_SINGLE_LINE_COMMENT_COLOR,
				findRGB(registry, IJavaThemeConstants.EDITOR_SINGLE_LINE_COMMENT_COLOR, new RGB(63, 127, 95)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.EDITOR_JAVA_KEYWORD_COLOR,
				findRGB(registry, IJavaThemeConstants.EDITOR_JAVA_KEYWORD_COLOR, new RGB(127, 0, 85)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.EDITOR_JAVA_KEYWORD_RETURN_COLOR,
				findRGB(registry, IJavaThemeConstants.EDITOR_JAVA_KEYWORD_RETURN_COLOR, new RGB(127, 0, 85)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.EDITOR_JAVA_OPERATOR_COLOR,
				findRGB(registry, IJavaThemeConstants.EDITOR_JAVA_OPERATOR_COLOR, new RGB(0, 0, 0)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.EDITOR_JAVA_BRACKET_COLOR,
				findRGB(registry, IJavaThemeConstants.EDITOR_JAVA_BRACKET_COLOR, new RGB(0, 0, 0)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.EDITOR_MATCHING_BRACKETS_COLOR,
				findRGB(registry, IJavaThemeConstants.EDITOR_MATCHING_BRACKETS_COLOR, new RGB(192, 192,192)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.EDITOR_TASK_TAG_COLOR,
				findRGB(registry, IJavaThemeConstants.EDITOR_TASK_TAG_COLOR, new RGB(127, 159, 191)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.EDITOR_JAVADOC_KEYWORD_COLOR,
				findRGB(registry, IJavaThemeConstants.EDITOR_JAVADOC_KEYWORD_COLOR, new RGB(127, 159, 191)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.EDITOR_JAVADOC_TAG_COLOR,
				findRGB(registry, IJavaThemeConstants.EDITOR_JAVADOC_TAG_COLOR, new RGB(127, 127, 159)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.EDITOR_JAVADOC_LINKS_COLOR,
				findRGB(registry, IJavaThemeConstants.EDITOR_JAVADOC_LINKS_COLOR, new RGB(63, 63, 191)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.EDITOR_JAVADOC_DEFAULT_COLOR,
				findRGB(registry, IJavaThemeConstants.EDITOR_JAVADOC_DEFAULT_COLOR, new RGB(63, 95, 191)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.CODEASSIST_PARAMETERS_BACKGROUND,
				findRGB(registry, IJavaThemeConstants.CODEASSIST_PARAMETERS_BACKGROUND, new RGB(255, 255, 255)), fireEvent);
		setDefault(
				store,
				PreferenceConstants.CODEASSIST_PARAMETERS_FOREGROUND,
				findRGB(registry, IJavaThemeConstants.CODEASSIST_PARAMETERS_FOREGROUND, new RGB(0, 0, 0)), fireEvent);
		setDefault(
				store,
				PreferenceConstants.CODEASSIST_REPLACEMENT_BACKGROUND,
				findRGB(registry, IJavaThemeConstants.CODEASSIST_REPLACEMENT_BACKGROUND, new RGB(255, 255, 0)), fireEvent);
		setDefault(
				store,
				PreferenceConstants.CODEASSIST_REPLACEMENT_FOREGROUND,
				findRGB(registry, IJavaThemeConstants.CODEASSIST_REPLACEMENT_FOREGROUND, new RGB(255, 0, 0)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.PROPERTIES_FILE_COLORING_KEY,
				findRGB(registry, IJavaThemeConstants.PROPERTIES_FILE_COLORING_KEY, new RGB(0, 0, 0)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.PROPERTIES_FILE_COLORING_VALUE,
				findRGB(registry, IJavaThemeConstants.PROPERTIES_FILE_COLORING_VALUE, new RGB(42, 0, 255)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.PROPERTIES_FILE_COLORING_ASSIGNMENT,
				findRGB(registry, IJavaThemeConstants.PROPERTIES_FILE_COLORING_ASSIGNMENT, new RGB(0, 0, 0)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.PROPERTIES_FILE_COLORING_ARGUMENT,
				findRGB(registry, IJavaThemeConstants.PROPERTIES_FILE_COLORING_ARGUMENT, new RGB(127, 0, 85)), fireEvent);

		setDefault(
				store,
				PreferenceConstants.PROPERTIES_FILE_COLORING_COMMENT,
				findRGB(registry, IJavaThemeConstants.PROPERTIES_FILE_COLORING_COMMENT, new RGB(63, 127, 95)), fireEvent);


	}

	/**
	 * Sets the default value and fires a property
	 * change event if necessary.
	 *
	 * @param store	the preference store
	 * @param key the preference key
	 * @param newValue the new value
	 * @param fireEvent <code>false</code> if no event should be fired
	 * @since 3.4
	 */
	private static void setDefault(IPreferenceStore store, String key, RGB newValue, boolean fireEvent) {
		if (!fireEvent) {
			PreferenceConverter.setDefault(store, key, newValue);
			return;
		}

		RGB oldValue= null;
		if (store.isDefault(key))
			oldValue= PreferenceConverter.getDefaultColor(store, key);

		PreferenceConverter.setDefault(store, key, newValue);

		if (oldValue != null && !oldValue.equals(newValue))
			store.firePropertyChangeEvent(key, oldValue, newValue);
	}

	/**
	 * Returns the RGB for the given key in the given color registry.
	 *
	 * @param registry the color registry
	 * @param key the key for the constant in the registry
	 * @param defaultRGB the default RGB if no entry is found
	 * @return RGB the RGB
	 * @since 3.4
	 */
	private static RGB findRGB(ColorRegistry registry, String key, RGB defaultRGB) {
		if (registry == null)
			return defaultRGB;

		RGB rgb= registry.getRGB(key);
		if (rgb != null)
			return rgb;

		return defaultRGB;
	}

}
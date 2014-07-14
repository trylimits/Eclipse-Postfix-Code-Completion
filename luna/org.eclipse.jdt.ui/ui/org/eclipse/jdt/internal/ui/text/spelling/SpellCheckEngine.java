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
package org.eclipse.jdt.internal.ui.text.spelling;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.ui.texteditor.spelling.SpellingService;

import org.eclipse.ui.editors.text.EditorsUI;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.spelling.engine.DefaultSpellChecker;
import org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellCheckEngine;
import org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellChecker;
import org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellDictionary;
import org.eclipse.jdt.internal.ui.text.spelling.engine.LocaleSensitiveSpellDictionary;
import org.eclipse.jdt.internal.ui.text.spelling.engine.PersistentSpellDictionary;


/**
 * Spell check engine for Java source spell checking.
 *
 * @since 3.0
 */
public class SpellCheckEngine implements ISpellCheckEngine, IPropertyChangeListener {

	/** The dictionary location */
	public static final String DICTIONARY_LOCATION= "dictionaries/"; //$NON-NLS-1$

	/** The singleton engine instance */
	private static ISpellCheckEngine fgEngine= null;

	/**
	 * Caches the locales of installed dictionaries.
	 *
	 * @since 3.3
	 */
	private static Set<Locale> fgLocalesWithInstalledDictionaries;

	/**
	 * Returns the locales for which this
	 * spell check engine has dictionaries in certain location.
	 *
	 * @param location dictionaries location
	 * @return The available locales for this engine
	 */
	private static Set<Locale> getLocalesWithInstalledDictionaries(URL location) {
		String[] fileNames;
		try {
			URL url= FileLocator.toFileURL(location);
			File file= new File(url.getFile());
			if (!file.isDirectory())
				return Collections.emptySet();
			fileNames= file.list();
			if (fileNames == null)
				return Collections.emptySet();
		} catch (IOException ex) {
			JavaPlugin.log(ex);
			return Collections.emptySet();
		}

		Set<Locale> localesWithInstalledDictionaries= new HashSet<Locale>();
		int fileNameCount= fileNames.length;
		for (int i= 0; i < fileNameCount; i++) {
			String fileName= fileNames[i];
			int localeEnd= fileName.indexOf(".dictionary"); //$NON-NLS-1$
			if (localeEnd > 1) {
				String localeName= fileName.substring(0, localeEnd);
				int languageEnd=localeName.indexOf('_');
				if (languageEnd == -1)
					localesWithInstalledDictionaries.add(new Locale(localeName));
				else if (languageEnd == 2 && localeName.length() == 5)
					localesWithInstalledDictionaries.add(new Locale(localeName.substring(0, 2), localeName.substring(3)));
				else if (localeName.length() > 6 && localeName.charAt(5) == '_')
					localesWithInstalledDictionaries.add(new Locale(localeName.substring(0, 2), localeName.substring(3, 5), localeName.substring(6)));
			}
		}

		return localesWithInstalledDictionaries;
	}


	/**
	 * Returns the locales for which this
	 * spell check engine has dictionaries.
	 *
	 * @return The available locales for this engine
	 */
	public static Set<Locale> getLocalesWithInstalledDictionaries() {
		if (fgLocalesWithInstalledDictionaries != null)
			return fgLocalesWithInstalledDictionaries;

		Enumeration<URL> locations;
		try {
			locations= getDictionaryLocations();
			if (locations == null)
				return fgLocalesWithInstalledDictionaries= Collections.emptySet();
		} catch (IOException ex) {
			JavaPlugin.log(ex);
			return fgLocalesWithInstalledDictionaries= Collections.emptySet();
		}

		fgLocalesWithInstalledDictionaries= new HashSet<Locale>();

		while (locations.hasMoreElements()) {
			URL location= locations.nextElement();
			Set<Locale> locales= getLocalesWithInstalledDictionaries(location);
			fgLocalesWithInstalledDictionaries.addAll(locales);
		}

		return fgLocalesWithInstalledDictionaries;
	}

	/**
	 * Returns the default locale for this engine.
	 *
	 * @return The default locale
	 */
	public static Locale getDefaultLocale() {
		return Locale.getDefault();
	}

	/**
	 * Returns the dictionary closest to the given locale.
	 *
	 * @param locale the locale
	 * @return the dictionary or <code>null</code> if none is suitable
	 * @since 3.3
	 */
	public ISpellDictionary findDictionary(Locale locale) {
		ISpellDictionary dictionary= fLocaleDictionaries.get(locale);
		if (dictionary != null)
			return dictionary;

		// Try same language
		String language= locale.getLanguage();
		Iterator<Entry<Locale, ISpellDictionary>> iter= fLocaleDictionaries.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<Locale, ISpellDictionary> entry= iter.next();
			Locale dictLocale= entry.getKey();
			if (dictLocale.getLanguage().equals(language))
				return entry.getValue();
		}

		return null;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellCheckEngine#findDictionary(java.util.Locale)
	 * @since 3.3
	 */
	public static Locale findClosestLocale(Locale locale) {
		if (locale == null || locale.toString().length() == 0)
			return locale;

		if (getLocalesWithInstalledDictionaries().contains(locale))
			return locale;

		// Try same language
		String language= locale.getLanguage();
		Iterator<Locale> iter= getLocalesWithInstalledDictionaries().iterator();
		while (iter.hasNext()) {
			Locale dictLocale= iter.next();
			if (dictLocale.getLanguage().equals(language))
				return dictLocale;
		}

		// Try whether American English is present
		Locale defaultLocale= Locale.US;
		if (getLocalesWithInstalledDictionaries().contains(defaultLocale))
			return defaultLocale;

		return null;
	}

	/**
	 * Returns the enumeration of URLs for the dictionary locations where
	 * the Platform dictionaries are located.
	 * <p>
	 * This is in <code>org.eclipse.jdt.ui/dictionaries/</code>
	 * which can also be populated via fragments.
	 * </p>
	 *
	 * @throws IOException if there is an I/O error
	 * @return The dictionary locations, or <code>null</code> iff the locations are not known
	 */
	public static Enumeration<URL> getDictionaryLocations() throws IOException {
		final JavaPlugin plugin= JavaPlugin.getDefault();
		if (plugin != null)
			return plugin.getBundle().getResources("/" + DICTIONARY_LOCATION); //$NON-NLS-1$
		return null;
	}

	/**
	 * Returns the singleton instance of the spell check engine.
	 *
	 * @return The singleton instance of the spell check engine
	 */
	public static synchronized final ISpellCheckEngine getInstance() {

		if (fgEngine == null)
			fgEngine= new SpellCheckEngine();

		return fgEngine;
	}

	/**
	 * Shuts down the singleton instance of the spell check engine.
	 */
	public static synchronized final void shutdownInstance() {
		if (fgEngine != null) {
			fgEngine.shutdown();
			fgEngine= null;
		}
	}

	/** The registered locale insensitive dictionaries */
	private Set<ISpellDictionary> fGlobalDictionaries= new HashSet<ISpellDictionary>();

	/** The spell checker for fLocale */
	private ISpellChecker fChecker= null;

	/** The registered locale sensitive dictionaries */
	private Map<Locale, ISpellDictionary> fLocaleDictionaries= new HashMap<Locale, ISpellDictionary>();

	/** The user dictionary */
	private ISpellDictionary fUserDictionary= null;

	/**
	 * Creates a new spell check manager.
	 */
	private SpellCheckEngine() {

		fGlobalDictionaries.add(new TaskTagDictionary());
		fGlobalDictionaries.add(new HtmlTagDictionary());
		fGlobalDictionaries.add(new JavaDocTagDictionary());

		try {

			Locale locale= null;
			final Enumeration<URL> locations= getDictionaryLocations();

			while (locations != null && locations.hasMoreElements()) {
				URL location= locations.nextElement();

				for (final Iterator<Locale> iterator= getLocalesWithInstalledDictionaries(location).iterator(); iterator.hasNext();) {

					locale= iterator.next();
					fLocaleDictionaries.put(locale, new LocaleSensitiveSpellDictionary(locale, location));
				}
			}

		} catch (IOException exception) {
			// Do nothing
		}

		JavaPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(this);
		EditorsUI.getPreferenceStore().addPropertyChangeListener(this);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellCheckEngine#getSpellChecker()
	 */
	public synchronized final ISpellChecker getSpellChecker() throws IllegalStateException {
		if (fGlobalDictionaries == null)
			throw new IllegalStateException("spell checker has been shut down"); //$NON-NLS-1$

		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		Locale locale= getCurrentLocale(store);
		if (fUserDictionary == null && "".equals(locale.toString())) //$NON-NLS-1$
			return null;

		if (fChecker != null && fChecker.getLocale().equals(locale))
			return fChecker;

		resetSpellChecker();

		fChecker= new DefaultSpellChecker(store, locale);
		resetUserDictionary();

		for (Iterator<ISpellDictionary> iterator= fGlobalDictionaries.iterator(); iterator.hasNext();) {
			ISpellDictionary dictionary= iterator.next();
			fChecker.addDictionary(dictionary);
		}

		ISpellDictionary dictionary= findDictionary(fChecker.getLocale());
		if (dictionary != null)
			fChecker.addDictionary(dictionary);

		return fChecker;
	}

	/**
	 * Returns the current locale of the spelling preferences.
	 *
	 * @param store the preference store
	 * @return The current locale of the spelling preferences
	 */
	private Locale getCurrentLocale(IPreferenceStore store) {
		return convertToLocale(store.getString(PreferenceConstants.SPELLING_LOCALE));
	}

	public static Locale convertToLocale(String locale) {
		Locale defaultLocale= SpellCheckEngine.getDefaultLocale();
		if (locale.equals(defaultLocale.toString()))
			return defaultLocale;

		int length= locale.length();
		if (length >= 5)
			return new Locale(locale.substring(0, 2), locale.substring(3, 5));

		if (length == 2 && locale.indexOf('_') == -1)
			return new Locale(locale);

		if (length == 3 && locale.charAt(0) == '_')
			return new Locale("", locale.substring(1)); //$NON-NLS-1$

		return new Locale(""); //$NON-NLS-1$
	}

	/*
	 * @see org.eclipse.jdt.ui.text.spelling.engine.ISpellCheckEngine#getLocale()
	 */
	public synchronized final Locale getLocale() {
		if (fChecker == null)
			return null;

		return fChecker.getLocale();
	}

	/*
	 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
	 */
	public final void propertyChange(final PropertyChangeEvent event) {
		if (event.getProperty().equals(PreferenceConstants.SPELLING_LOCALE)) {
			resetSpellChecker();
			return;
		}

		if (event.getProperty().equals(PreferenceConstants.SPELLING_USER_DICTIONARY)) {
			resetUserDictionary();
			return;
		}

		if (event.getProperty().equals(PreferenceConstants.SPELLING_USER_DICTIONARY_ENCODING)) {
			resetUserDictionary();
			return;
		}

		if (event.getProperty().equals(SpellingService.PREFERENCE_SPELLING_ENABLED) && !EditorsUI.getPreferenceStore().getBoolean(SpellingService.PREFERENCE_SPELLING_ENABLED)) {
			if (this == fgEngine)
				SpellCheckEngine.shutdownInstance();
			else
				shutdown();
		}
	}

	/**
	 * Resets the current checker's user dictionary.
	 */
	private synchronized void resetUserDictionary() {
		if (fChecker == null)
			return;

		// Update user dictionary
		if (fUserDictionary != null) {
			fChecker.removeDictionary(fUserDictionary);
			fUserDictionary.unload();
			fUserDictionary= null;
		}

		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		String filePath= store.getString(PreferenceConstants.SPELLING_USER_DICTIONARY);

		VariablesPlugin variablesPlugin= VariablesPlugin.getDefault();
		if (variablesPlugin == null)
			return;

		IStringVariableManager variableManager= variablesPlugin.getStringVariableManager();
		try {
			filePath= variableManager.performStringSubstitution(filePath);
		} catch (CoreException e) {
			JavaPlugin.log(e);
			return;
		}
		if (filePath.length() > 0) {
			try {
				File file= new File(filePath);
				if (!file.exists() && !file.createNewFile())
					return;

				final URL url= new URL("file", null, filePath); //$NON-NLS-1$
				InputStream stream= url.openStream();
				if (stream != null) {
					try {
						fUserDictionary= new PersistentSpellDictionary(url);
						fChecker.addDictionary(fUserDictionary);
					} finally {
						stream.close();
					}
				}
			} catch (MalformedURLException exception) {
				// Do nothing
			} catch (IOException exception) {
				// Do nothing
			}
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellCheckEngine#registerDictionary(org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellDictionary)
	 */
	public synchronized final void registerGlobalDictionary(final ISpellDictionary dictionary) {
		fGlobalDictionaries.add(dictionary);
		resetSpellChecker();
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellCheckEngine#registerDictionary(java.util.Locale, org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellDictionary)
	 */
	public synchronized final void registerDictionary(final Locale locale, final ISpellDictionary dictionary) {
		fLocaleDictionaries.put(locale, dictionary);
		resetSpellChecker();
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellCheckEngine#unload()
	 */
	public synchronized final void shutdown() {

		JavaPlugin.getDefault().getPreferenceStore().removePropertyChangeListener(this);
		EditorsUI.getPreferenceStore().removePropertyChangeListener(this);

		ISpellDictionary dictionary= null;
		for (final Iterator<ISpellDictionary> iterator= fGlobalDictionaries.iterator(); iterator.hasNext();) {
			dictionary= iterator.next();
			dictionary.unload();
		}
		fGlobalDictionaries= null;

		for (final Iterator<ISpellDictionary> iterator= fLocaleDictionaries.values().iterator(); iterator.hasNext();) {
			dictionary= iterator.next();
			dictionary.unload();
		}
		fLocaleDictionaries= null;

		fUserDictionary= null;
		fChecker= null;
	}

	private synchronized void resetSpellChecker() {
		if (fChecker != null) {
			ISpellDictionary dictionary= fLocaleDictionaries.get(fChecker.getLocale());
			if (dictionary != null)
				dictionary.unload();
		}
		fChecker= null;
	}

	/*
	 * @see org.eclipse.jdt.ui.text.spelling.engine.ISpellCheckEngine#unregisterDictionary(org.eclipse.jdt.ui.text.spelling.engine.ISpellDictionary)
	 */
	public synchronized final void unregisterDictionary(final ISpellDictionary dictionary) {
		fGlobalDictionaries.remove(dictionary);
		fLocaleDictionaries.values().remove(dictionary);
		dictionary.unload();
		resetSpellChecker();
	}
}

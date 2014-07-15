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
package org.eclipse.jdt.internal.ui.text.spelling.engine;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jdt.ui.PreferenceConstants;


/**
 * Default spell checker for standard text.
 *
 * @since 3.0
 */
public class DefaultSpellChecker implements ISpellChecker {

	/** Array of URL prefixes */
	public static final String[] URL_PREFIXES= new String[] { "http://", "https://", "www.", "ftp://", "ftps://", "news://", "mailto://" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$

	/**
	 * Does this word contain digits?
	 *
	 * @param word the word to check
	 * @return <code>true</code> iff this word contains digits, <code>false></code> otherwise
	 */
	protected static boolean isDigits(final String word) {

		for (int index= 0; index < word.length(); index++) {

			if (Character.isDigit(word.charAt(index)))
				return true;
		}
		return false;
	}

	/**
	 * Does this word contain mixed-case letters?
	 *
	 * @param word
	 *                   The word to check
	 * @param sentence
	 *                   <code>true</code> iff the specified word starts a new
	 *                   sentence, <code>false</code> otherwise
	 * @return <code>true</code> iff the contains mixed-case letters, <code>false</code>
	 *               otherwise
	 */
	protected static boolean isMixedCase(final String word, final boolean sentence) {

		final int length= word.length();
		boolean upper= Character.isUpperCase(word.charAt(0));

		if (sentence && upper && (length > 1))
			upper= Character.isUpperCase(word.charAt(1));

		if (upper) {

			for (int index= length - 1; index > 0; index--) {
				if (Character.isLowerCase(word.charAt(index)))
					return true;
			}
		} else {

			for (int index= length - 1; index > 0; index--) {
				if (Character.isUpperCase(word.charAt(index)))
					return true;
			}
		}
		return false;
	}

	/**
	 * Does this word contain upper-case letters only?
	 *
	 * @param word
	 *                   The word to check
	 * @return <code>true</code> iff this word only contains upper-case
	 *               letters, <code>false</code> otherwise
	 */
	protected static boolean isUpperCase(final String word) {

		for (int index= word.length() - 1; index >= 0; index--) {

			if (Character.isLowerCase(word.charAt(index)))
				return false;
		}
		return true;
	}

	/**
	 * Does this word look like an URL?
	 *
	 * @param word
	 *                   The word to check
	 * @return <code>true</code> iff this word looks like an URL, <code>false</code>
	 *               otherwise
	 */
	protected static boolean isUrl(final String word) {

		for (int index= 0; index < URL_PREFIXES.length; index++) {

			if (word.startsWith(URL_PREFIXES[index]))
				return true;
		}
		return false;
	}

	/**
	 * The dictionaries to use for spell checking. Synchronized to avoid
	 * concurrent modifications.
	 */
	private final Set<ISpellDictionary> fDictionaries= Collections.synchronizedSet(new HashSet<ISpellDictionary>());

	/**
	 * The words to be ignored. Synchronized to avoid concurrent modifications.
	 */
	private final Set<String> fIgnored= Collections.synchronizedSet(new HashSet<String>());

	/**
	 * The preference store. Assumes the <code>IPreferenceStore</code>
	 * implementation is thread safe.
	 */
	private final IPreferenceStore fPreferences;

	/**
	 * The locale of this checker.
	 * @since 3.3
	 */
	private Locale fLocale;

	/**
	 * Creates a new default spell checker.
	 *
	 * @param store the preference store for this spell checker
	 * @param locale the locale
	 */
	public DefaultSpellChecker(IPreferenceStore store, Locale locale) {
		Assert.isLegal(store != null);
		Assert.isLegal(locale != null);

		fPreferences= store;
		fLocale= locale;
	}

	/*
	 * @see org.eclipse.spelling.done.ISpellChecker#addDictionary(org.eclipse.spelling.done.ISpellDictionary)
	 */
	public final void addDictionary(final ISpellDictionary dictionary) {
		// synchronizing is necessary as this is a write access
		fDictionaries.add(dictionary);
	}

	/*
	 * @see org.eclipse.jdt.ui.text.spelling.engine.ISpellChecker#acceptsWords()
	 */
	public boolean acceptsWords() {
		// synchronizing might not be needed here since acceptWords is
		// a read-only access and only called in the same thread as
		// the modifying methods add/checkWord (?)
		Set<ISpellDictionary> copy;
		synchronized (fDictionaries) {
			copy= new HashSet<ISpellDictionary>(fDictionaries);
		}

		ISpellDictionary dictionary= null;
		for (final Iterator<ISpellDictionary> iterator= copy.iterator(); iterator.hasNext();) {

			dictionary= iterator.next();
			if (dictionary.acceptsWords())
				return true;
		}
		return false;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellChecker#addWord(java.lang.String)
	 */
	public void addWord(final String word) {
		// synchronizing is necessary as this is a write access
		Set<ISpellDictionary> copy;
		synchronized (fDictionaries) {
			copy= new HashSet<ISpellDictionary>(fDictionaries);
		}

		final String addable= word.toLowerCase();
		for (final Iterator<ISpellDictionary> iterator= copy.iterator(); iterator.hasNext();) {
			ISpellDictionary dictionary= iterator.next();
			if (dictionary.acceptsWords())
				dictionary.addWord(addable);
		}

	}

	/*
	 * @see org.eclipse.jdt.ui.text.spelling.engine.ISpellChecker#checkWord(java.lang.String)
	 */
	public final void checkWord(final String word) {
		// synchronizing is necessary as this is a write access
		fIgnored.remove(word.toLowerCase());
	}

	/*
	 * @see org.eclipse.spelling.done.ISpellChecker#execute(org.eclipse.spelling.ISpellCheckTokenizer)
	 */
	public void execute(final ISpellEventListener listener, final ISpellCheckIterator iterator) {

		final boolean ignoreDigits= fPreferences.getBoolean(PreferenceConstants.SPELLING_IGNORE_DIGITS);
		final boolean ignoreMixed= fPreferences.getBoolean(PreferenceConstants.SPELLING_IGNORE_MIXED);
		final boolean ignoreSentence= fPreferences.getBoolean(PreferenceConstants.SPELLING_IGNORE_SENTENCE);
		final boolean ignoreUpper= fPreferences.getBoolean(PreferenceConstants.SPELLING_IGNORE_UPPER);
		final boolean ignoreURLS= fPreferences.getBoolean(PreferenceConstants.SPELLING_IGNORE_URLS);
		final boolean ignoreNonLetters= fPreferences.getBoolean(PreferenceConstants.SPELLING_IGNORE_NON_LETTERS);
		final boolean ignoreSingleLetters= fPreferences.getBoolean(PreferenceConstants.SPELLING_IGNORE_SINGLE_LETTERS);
		final int problemsThreshold= PreferenceConstants.getPreferenceStore().getInt(PreferenceConstants.SPELLING_PROBLEMS_THRESHOLD);

		iterator.setIgnoreSingleLetters(ignoreSingleLetters);

		Iterator<ISpellDictionary> iter= fDictionaries.iterator();
		while (iter.hasNext())
			iter.next().setStripNonLetters(ignoreNonLetters);

		String word= null;
		boolean starts= false;
		int problemCount= 0;

		while (problemCount <= problemsThreshold && iterator.hasNext()) {

			word= iterator.next();
			if (word != null) {

				// synchronizing is necessary as this is called inside the reconciler
				if (!fIgnored.contains(word)) {

					starts= iterator.startsSentence();
					if (!isCorrect(word)) {

					    boolean isMixed=  isMixedCase(word, true);
					    boolean isUpper= isUpperCase(word);
					    boolean isDigits= isDigits(word);
					    boolean isURL= isUrl(word);

					    if ( !ignoreMixed && isMixed || !ignoreUpper && isUpper || !ignoreDigits && isDigits || !ignoreURLS && isURL || !(isMixed || isUpper || isDigits || isURL)) {
					        listener.handle(new SpellEvent(this, word, iterator.getBegin(), iterator.getEnd(), starts, false));
					        problemCount++;
					    }

					} else {

						if (!ignoreSentence && starts && Character.isLowerCase(word.charAt(0))) {
							listener.handle(new SpellEvent(this, word, iterator.getBegin(), iterator.getEnd(), true, true));
							problemCount++;
						}
					}
				}
			}
		}
	}

	/*
	 * @see org.eclipse.spelling.done.ISpellChecker#getProposals(java.lang.String,boolean)
	 */
	public Set<RankedWordProposal> getProposals(final String word, final boolean sentence) {

		// synchronizing might not be needed here since getProposals is
		// a read-only access and only called in the same thread as
		// the modifing methods add/removeDictionary (?)
		Set<ISpellDictionary> copy;
		synchronized (fDictionaries) {
			copy= new HashSet<ISpellDictionary>(fDictionaries);
		}

		ISpellDictionary dictionary= null;
		final HashSet<RankedWordProposal> proposals= new HashSet<RankedWordProposal>();

		for (final Iterator<ISpellDictionary> iterator= copy.iterator(); iterator.hasNext();) {

			dictionary= iterator.next();
			proposals.addAll(dictionary.getProposals(word, sentence));
		}
		return proposals;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellChecker#ignoreWord(java.lang.String)
	 */
	public final void ignoreWord(final String word) {
		// synchronizing is necessary as this is a write access
		fIgnored.add(word.toLowerCase());
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellChecker#isCorrect(java.lang.String)
	 */
	public final boolean isCorrect(final String word) {
		// synchronizing is necessary as this is called from execute
		Set<ISpellDictionary> copy;
		synchronized (fDictionaries) {
			copy= new HashSet<ISpellDictionary>(fDictionaries);
		}

		if (fIgnored.contains(word.toLowerCase()))
			return true;

		ISpellDictionary dictionary= null;
		for (final Iterator<ISpellDictionary> iterator= copy.iterator(); iterator.hasNext();) {

			dictionary= iterator.next();
			if (dictionary.isCorrect(word))
				return true;
		}
		return false;
	}

	/*
	 * @see org.eclipse.spelling.done.ISpellChecker#removeDictionary(org.eclipse.spelling.done.ISpellDictionary)
	 */
	public final void removeDictionary(final ISpellDictionary dictionary) {
		// synchronizing is necessary as this is a write access
		fDictionaries.remove(dictionary);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellChecker#getLocale()
	 * @since 3.3
	 */
	public Locale getLocale() {
		return fLocale;
	}
}

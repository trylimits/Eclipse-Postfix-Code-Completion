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

import java.util.Locale;
import java.util.Set;

/**
 * Interface for spell checkers.
 *
 * @since 3.0
 */
public interface ISpellChecker {

	/**
	 * Adds a dictionary to the list of active dictionaries.
	 *
	 * @param dictionary
	 *                   The dictionary to add
	 */
	void addDictionary(ISpellDictionary dictionary);

	/**
	 * Returns whether this spell checker accepts word additions.
	 *
	 * @return <code>true</code> if word additions are accepted, <code>false</code> otherwise
	 */
	boolean acceptsWords();

	/**
	 * Adds the specified word to the set of correct words.
	 *
	 * @param word
	 *                   The word to add to the set of correct words
	 */
	void addWord(String word);

	/**
	 * Checks the specified word until calling <code>ignoreWord(String)</code>.
	 *
	 * @param word
	 *                   The word to check
	 */
	void checkWord(String word);

	/**
	 * Checks the spelling with the spell check iterator. Implementations must
	 * be thread safe as this may be called inside a reconciler thread.
	 *
	 * @param listener the spell event listener
	 * @param iterator the iterator to use for spell checking
	 */
	void execute(ISpellEventListener listener, ISpellCheckIterator iterator);

	/**
	 * Returns the ranked proposals for a word.
	 *
	 * @param word
	 *                   The word to retrieve the proposals for
	 * @param sentence
	 *                   <code>true</code> iff the proposals should start a
	 *                   sentence, <code>false</code> otherwise
	 * @return Set of ranked proposals for the word
	 */
	Set<RankedWordProposal> getProposals(String word, boolean sentence);

	/**
	 * Ignores the specified word until calling <code>checkWord(String)</code>.
	 *
	 * @param word
	 *                   The word to ignore
	 */
	void ignoreWord(String word);

	/**
	 * Is the specified word correctly spelled? Implementations must be thread
	 * safe as this may be called from within a reconciler thread.
	 *
	 * @param word
	 *                   The word to check its spelling
	 * @return <code>true</code> iff the word is correctly spelled, <code>false</code>
	 *               otherwise
	 */
	boolean isCorrect(String word);

	/**
	 * Remove a dictionary from the list of active dictionaries.
	 *
	 * @param dictionary
	 *                   The dictionary to remove
	 */
	void removeDictionary(ISpellDictionary dictionary);

	/**
	 * Returns the current locale of the spell check engine.
	 *
	 * @return The current locale of the engine
	 * @since 3.3
	 */
	Locale getLocale();
}

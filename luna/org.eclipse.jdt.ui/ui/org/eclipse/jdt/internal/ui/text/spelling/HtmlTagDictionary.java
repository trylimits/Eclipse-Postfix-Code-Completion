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

import java.net.URL;

import org.eclipse.jdt.internal.ui.text.javadoc.IHtmlTagConstants;
import org.eclipse.jdt.internal.ui.text.spelling.engine.AbstractSpellDictionary;

/**
 * Dictionary for html tags.
 *
 * @since 3.0
 */
public class HtmlTagDictionary extends AbstractSpellDictionary {

	/*
	 * @see org.eclipse.jdt.internal.ui.text.spelling.engine.AbstractSpellDictionary#getName()
	 */
	@Override
	protected final URL getURL() {
		return null;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellDictionary#isCorrect(java.lang.String)
	 */
	@Override
	public boolean isCorrect(final String word) {

		if (word.charAt(0) == IHtmlTagConstants.HTML_TAG_PREFIX)
			return super.isCorrect(word);

		return false;
	}

	/*
	 * @see org.eclipse.jdt.ui.text.spelling.engine.AbstractSpellDictionary#load(java.net.URL)
	 */
	@Override
	protected synchronized boolean load(final URL url) {

		unload();

		for (int index= 0; index < IHtmlTagConstants.HTML_GENERAL_TAGS.length; index++) {

			hashWord(IHtmlTagConstants.HTML_TAG_PREFIX + IHtmlTagConstants.HTML_GENERAL_TAGS[index] + IHtmlTagConstants.HTML_TAG_POSTFIX);
			hashWord(IHtmlTagConstants.HTML_CLOSE_PREFIX + IHtmlTagConstants.HTML_GENERAL_TAGS[index] + IHtmlTagConstants.HTML_TAG_POSTFIX);
		}
		return true;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.spelling.engine.AbstractSpellDictionary#stripNonLetters(java.lang.String)
	 * @since 3.3
	 */
	@Override
	protected String stripNonLetters(String word) {
		return word;
	}

}

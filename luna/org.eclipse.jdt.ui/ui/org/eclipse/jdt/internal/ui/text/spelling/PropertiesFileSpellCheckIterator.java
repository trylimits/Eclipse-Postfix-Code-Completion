/*******************************************************************************
 * Copyright (c) 2007, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.spelling;

import java.util.Locale;

import com.ibm.icu.text.BreakIterator;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;


/**
 * Iterator to spell check Java properties files
 * where '&' is ignored.
 *
 * @since 3.3
 */
public class PropertiesFileSpellCheckIterator extends SpellCheckIterator {

	public PropertiesFileSpellCheckIterator(IDocument document, IRegion region, Locale locale) {
		super(document, region, locale);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.spelling.SpellCheckIterator#next()
	 */
	@Override
	public final String next() {
		int previous= -1;
		String token= nextToken();
		while (fSuccessor != BreakIterator.DONE && (token == null || fContent.charAt(fNext) == '&')) {
			if (token != null) {
				if (previous == -1)
					previous= fPrevious;
				String nextToken= nextToken();
				if (nextToken != null)
					token= token + nextToken.substring(1);
				else
					token= token + '&';
			} else
				token= nextToken();

		}

		if (previous != -1)
			fPrevious= previous;

		if (token != null && token.length() > 1 && token.startsWith("&")) { //$NON-NLS-1$
			token= token.substring(1);

			// Add characters in front of '&'
			while (fPrevious > 0 && !Character.isWhitespace(fContent.charAt(fPrevious - 1)) && fContent.charAt(fPrevious - 1) != '=') {
				token= fContent.charAt(fPrevious - 1) + token;
				fPrevious--;
			}

		}

		fLastToken= token;

		return token;
	}

}

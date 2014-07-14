/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.util.PatternMatcher;
import org.eclipse.jdt.internal.ui.util.StringMatcher;

/**
 * A pattern matcher for labels from {@link JavaElementLabels} where the label starts with the name
 * of the element (appended method return type or post qualifications are OK).
 * 
 * If the pattern starts with a valid Java identifier, the name from the label is matched
 * with a {@link PatternMatcher} and the rest is matched with a {@link StringMatcher}.
 * Otherwise, the whole label is matched with a {@link StringMatcher}.
 * 
 * @since 3.8
 */
public class JavaElementPrefixPatternMatcher {

	static final String STAR= "*"; //$NON-NLS-1$

	private PatternMatcher fElementNamePattern;

	private StringMatcher fRestPattern;

	public JavaElementPrefixPatternMatcher(String pattern) {
		int elementNameEnd= findNameEnd(pattern);

		String elementName= pattern.substring(0, elementNameEnd);
		String rest= pattern.substring(elementNameEnd);

		if (rest.startsWith(STAR)) {
			elementNameEnd= 0;
			rest= pattern;
		}
		fElementNamePattern= elementNameEnd == 0 ? null : new PatternMatcher(elementName);
		
		boolean ignoreCase= rest.toLowerCase().equals(rest);
		fRestPattern= new StringMatcher(rest + STAR, ignoreCase, false);
	}

	private int findNameEnd(String pattern) {
		int len= pattern.length();
		if (len != 0 && Character.isJavaIdentifierStart(pattern.charAt(0))) {
			for (int i= 1; i < len; i++) {
				if (! Character.isJavaIdentifierPart(pattern.charAt(i)))
					return i;
			}
			return len;
		}
		
		return 0;
	}

	public boolean matches(String label) {
		int elementNameEnd= findNameEnd(label);
		
		if (fElementNamePattern == null)
			return fRestPattern.match(label);
		
		if (! fElementNamePattern.matches(label.substring(0, elementNameEnd)))
			return false;
		
		return fRestPattern.match(label.substring(elementNameEnd));
	}
}

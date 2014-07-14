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
package org.eclipse.jdt.internal.ui.propertiesfileeditor;

import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.WordPatternRule;

/**
 * A specific single line rule for arguments in a property value.
 * <p>
 * An argument is defined as '{', digit, {digit}, '}'
 * </p>
 *
 * @since 3.1
 */
public final class ArgumentRule extends WordPatternRule {

	private static class ArgumentDetector implements IWordDetector {

		/*
		 * @see IWordDetector#isWordStart
		 */
		public boolean isWordStart(char c) {
			return '{' == c;
		}

		/*
		 * @see IWordDetector#isWordPart
		 */
		public boolean isWordPart(char c) {
			return c == '}' || Character.isDigit(c);
		}
	}


	private int fCount= 0;

	/**
	 * Creates an argument rule for the given <code>token</code>.
	 *
	 * @param token the token to be returned on success
	 */
	public ArgumentRule(IToken token) {
		super(new ArgumentDetector(), "{", "}", token); //$NON-NLS-1$//$NON-NLS-2$
	}

	/*
	 * @see org.eclipse.jface.text.rules.WordPatternRule#endSequenceDetected(org.eclipse.jface.text.rules.ICharacterScanner)
	 */
	@Override
	protected boolean endSequenceDetected(ICharacterScanner scanner) {
		fCount++;

		if (scanner.read() == '}')
			return fCount > 2;

		scanner.unread();
		return super.endSequenceDetected(scanner);
	}

	/*
	 * @see org.eclipse.jface.text.rules.PatternRule#sequenceDetected(org.eclipse.jface.text.rules.ICharacterScanner, char[], boolean)
	 */
	@Override
	protected boolean sequenceDetected(ICharacterScanner scanner, char[] sequence, boolean eofAllowed) {
		fCount= 0;
		return super.sequenceDetected(scanner, sequence, eofAllowed);
	}
}

/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Christian Plesner Hansen (plesner@quenta.org) - changed implementation to use DefaultCharacterPairMatcher
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.source.DefaultCharacterPairMatcher;

import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.text.IJavaPartitions;

/**
 * Helper class for match pairs of characters.
 */
public final class JavaPairMatcher extends DefaultCharacterPairMatcher implements ISourceVersionDependent {

	/**
	 * Stores the source version state.
	 * @since 3.1
	 */
	private boolean fHighlightAngularBrackets= false;


	public JavaPairMatcher(char[] pairs) {
		super(pairs, IJavaPartitions.JAVA_PARTITIONING, true);
	}

	/* @see ICharacterPairMatcher#match(IDocument, int) */
	@Override
	public IRegion match(IDocument document, int offset) {
		try {
			return performMatch(document, offset);
		} catch (BadLocationException ble) {
			return null;
		}
	}

	/*
	 * Performs the actual work of matching for #match(IDocument, int).
	 */
	private IRegion performMatch(IDocument document, int offset) throws BadLocationException {
		if (offset < 0 || document == null) return null;
		char prevChar= document.getChar(Math.max(offset - 1, 0));
		char currChar= (offset != document.getLength()) ? document.getChar(offset) : Character.MIN_VALUE;

		if (prevChar == '>' && currChar != '>') { //https://bugs.eclipse.org/bugs/show_bug.cgi?id=372516
			offset--;
			currChar= prevChar;
			prevChar= document.getChar(Math.max(offset - 1, 0));
		} else if (currChar == '<' && (prevChar != '>' && prevChar != '<')) {
			offset++;
			prevChar= currChar;
			currChar= document.getChar(offset);
		}

		if ((prevChar == '<' || currChar == '>') && !fHighlightAngularBrackets)
			return null;
		if (prevChar == '<' && isLessThanOperator(document, offset - 1))
			return null;
		final IRegion region= super.match(document, offset);
		if (region == null) return region;
		if (currChar == '>') {
			final int peer= region.getOffset();
			if (isLessThanOperator(document, peer)) return null;
		}
		return region;
	}

	/**
	 * Returns <code>true</code> if the character at the specified offset is a less-than sign, rather than
	 * the opening angle bracket of a type parameter list.
	 * 
	 * @param document a document
	 * @param offset an offset within the document
	 * @return <code>true</code> if the character at the specified offset is a less-than sign
	 * @throws BadLocationException if offset is invalid in the document
	 */
	private boolean isLessThanOperator(IDocument document, int offset) throws BadLocationException {
		if (offset < 0) return false;
		String contentType= TextUtilities.getContentType(document, IJavaPartitions.JAVA_PARTITIONING, offset, false);
		if (!IDocument.DEFAULT_CONTENT_TYPE.equals(contentType)) {
			return false;
		}
		JavaHeuristicScanner scanner= new JavaHeuristicScanner(document, IJavaPartitions.JAVA_PARTITIONING, contentType);
		return !isTypeParameterOpeningBracket(offset, document, scanner);
	}

	/**
	 * Checks if the angular bracket at <code>offset</code> is a type parameter opening bracket.
	 * 
	 * @param offset the offset of the opening bracket
	 * @param document the document
	 * @param scanner a java heuristic scanner on <code>document</code>
	 * @return <code>true</code> if the bracket is part of a type parameter, <code>false</code>
	 *         otherwise
	 * @since 3.1
	 */
	private boolean isTypeParameterOpeningBracket(int offset, IDocument document, JavaHeuristicScanner scanner) {
		/*
		 * type parameter come after braces (closing or opening), semicolons, or after
		 * a Type name (heuristic: starts with capital character), or after a modifier
		 * keyword in a method declaration (visibility, static, synchronized, final)
		 */

		try {
			IRegion line= document.getLineInformationOfOffset(offset);

			int prevToken= scanner.previousToken(offset - 1, line.getOffset());
			int prevTokenOffset= scanner.getPosition() + 1;
			String previous= prevToken == Symbols.TokenEOF ? null : document.get(prevTokenOffset, offset - prevTokenOffset).trim();

			if (	   prevToken == Symbols.TokenLBRACE
					|| prevToken == Symbols.TokenRBRACE
					|| prevToken == Symbols.TokenSEMICOLON
					|| prevToken == Symbols.TokenSYNCHRONIZED
					|| prevToken == Symbols.TokenSTATIC
					|| (prevToken == Symbols.TokenIDENT && isTypeParameterIntroducer(previous))
					|| prevToken == Symbols.TokenEOF)
				return true;
		} catch (BadLocationException e) {
			return false;
		}

		return false;
	}

	/**
	 * Returns true if the character at the specified offset is a greater-than sign, rather than an
	 * type parameter list close angle bracket.
	 * 
	 * @param document a document
	 * @param offset an offset within the document
	 * @return true if the character at the specified offset is a greater-than sign
	 * @throws BadLocationException if offset is invalid in the document
	 */
	private boolean isGreaterThanOperator(IDocument document, int offset) throws BadLocationException {
		if (offset < 0)
			return false;
		String contentType= TextUtilities.getContentType(document, IJavaPartitions.JAVA_PARTITIONING, offset, false);
		if (!IDocument.DEFAULT_CONTENT_TYPE.equals(contentType)) {
			return false;
		}
		JavaHeuristicScanner scanner= new JavaHeuristicScanner(document, IJavaPartitions.JAVA_PARTITIONING, contentType);
		return !isTypeParameterClosingBracket(offset, document, scanner);
	}

	/**
	 * Checks if the angular bracket at <code>offset</code> is a type parameter closing bracket.
	 * 
	 * @param offset the offset of the closing bracket
	 * @param document the document
	 * @param scanner a java heuristic scanner on <code>document</code>
	 * @return <code>true</code> if the bracket is part of a type parameter, <code>false</code>
	 *         otherwise
	 * @since 3.8
	 */
	private boolean isTypeParameterClosingBracket(int offset, IDocument document, JavaHeuristicScanner scanner) {
		/*
		 * type parameter closing brackets come after question marks, other type parameter
		 * closing brackets, or after a Type name (heuristic: starts with capital character)
		 */

		try {
			IRegion line= document.getLineInformationOfOffset(offset);

			int prevToken= scanner.previousToken(offset - 1, line.getOffset());
			int prevTokenOffset= scanner.getPosition() + 1;
			String previous= prevToken == Symbols.TokenEOF ? null : document.get(prevTokenOffset, offset - prevTokenOffset).trim();

			if ((prevToken == Symbols.TokenIDENT && (previous.length() > 0 && Character.isUpperCase(previous.charAt(0))))
					|| prevToken == Symbols.TokenEOF
					|| prevToken == Symbols.TokenGREATERTHAN
					|| prevToken == Symbols.TokenQUESTIONMARK)
				return true;
		} catch (BadLocationException e) {
			return false;
		}

		return false;
	}

	/**
	 * Returns <code>true</code> if <code>identifier</code> is an identifier
	 * that could come right before a type parameter list. It uses a heuristic:
	 * if the identifier starts with an upper case, it is assumed a type name.
	 * Also, if <code>identifier</code> is a method modifier, it is assumed
	 * that the angular bracket is part of the generic type parameter of a
	 * method.
	 *
	 * @param identifier the identifier to check
	 * @return <code>true</code> if the identifier could introduce a type
	 *         parameter list
	 * @since 3.1
	 */
	private boolean isTypeParameterIntroducer(String identifier) {
		return identifier.length() > 0
				&& (Character.isUpperCase(identifier.charAt(0))
						|| identifier.startsWith("final") //$NON-NLS-1$
						|| identifier.startsWith("public") //$NON-NLS-1$
						|| identifier.startsWith("public") //$NON-NLS-1$
						|| identifier.startsWith("protected") //$NON-NLS-1$
						|| identifier.startsWith("private")); //$NON-NLS-1$
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.ISourceVersionDependent#setSourceVersion(java.lang.String)
	 */
	public void setSourceVersion(String version) {
		if (JavaCore.VERSION_1_5.compareTo(version) <= 0)
			fHighlightAngularBrackets= true;
		else
			fHighlightAngularBrackets= false;
	}

	/*
	 * @see org.eclipse.jface.text.source.ICharacterPairMatcherExtension#isMatchedChar(char, org.eclipse.jface.text.IDocument, int)
	 */
	@Override
	public boolean isMatchedChar(char ch, IDocument document, int offset) {
		try {
			if (ch == '<') {
				if (isLessThanOperator(document, offset)) {
					return false;
				}
			} else if (ch == '>') {
				if (isGreaterThanOperator(document, offset)) {
					return false;
				}
			}
		} catch (BadLocationException e) {
			// do nothing
		}
		return super.isMatchedChar(ch, document, offset);
	}
}

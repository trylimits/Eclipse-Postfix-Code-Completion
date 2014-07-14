/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.propertiesfileeditor;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;

import org.eclipse.jdt.internal.ui.JavaPluginImages;

/**
 * Proposal to escape or unescape backslashes.
 * 
 * @since 3.7
 */
public class EscapeBackslashCompletionProposal implements ICompletionProposal {

	private final String fProposalText;

	private final int fOffset;

	private final int fLength;

	private final String fDisplayString;

	/**
	 * Constructor for EscapeBackslashCompletionProposal.
	 * 
	 * @param proposalText the proposal text
	 * @param offset offset of the proposal
	 * @param length length of the proposal
	 * @param displayString the display string
	 */
	public EscapeBackslashCompletionProposal(String proposalText, int offset, int length, String displayString) {
		fProposalText= proposalText;
		fOffset= offset;
		fLength= length;
		fDisplayString= displayString;
	}

	public void apply(IDocument document) {
		try {
			document.replace(fOffset, fLength, fProposalText);
		} catch (BadLocationException e) {
			//do nothing
		}
	}

	public Point getSelection(IDocument document) {
		return null;
	}

	public String getAdditionalProposalInfo() {
		//the proposal info is not HTML
		return "<pre>" + fProposalText + "</pre>"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	public String getDisplayString() {
		return fDisplayString;
	}

	public Image getImage() {
		return JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
	}

	public IContextInformation getContextInformation() {
		return null;
	}
}
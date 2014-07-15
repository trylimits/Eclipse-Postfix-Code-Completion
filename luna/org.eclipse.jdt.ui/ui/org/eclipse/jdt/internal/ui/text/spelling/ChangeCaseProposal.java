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

import java.util.Locale;

import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext;

import org.eclipse.jdt.internal.ui.JavaUIMessages;

/**
 * Proposal to change the letter case of a word.
 *
 * @since 3.0
 */
public class ChangeCaseProposal extends WordCorrectionProposal {

	/**
	 * Creates a new change case proposal.
	 * 
	 * @param arguments The problem arguments associated with the spelling problem
	 * @param offset The offset in the document where to apply the proposal
	 * @param length The length in the document to apply the proposal
	 * @param context The invocation context for this proposal
	 * @param locale The locale to use for the case change
	 */
	public ChangeCaseProposal(final String[] arguments, final int offset, final int length, final IQuickAssistInvocationContext context, final Locale locale) {
		super(Character.isLowerCase(arguments[0].charAt(0)) ? Character.toUpperCase(arguments[0].charAt(0)) + arguments[0].substring(1) : arguments[0], arguments, offset, length, context, Integer.MAX_VALUE);
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.ICompletionProposal#getDisplayString()
	 */
	@Override
	public String getDisplayString() {
		return JavaUIMessages.Spelling_case_label;
	}
}

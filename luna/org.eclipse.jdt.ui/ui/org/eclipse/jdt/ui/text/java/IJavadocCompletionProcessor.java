/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.text.java;

import org.eclipse.jface.text.contentassist.IContextInformation;

import org.eclipse.jdt.core.ICompilationUnit;


/**
 * A Javadoc processor proposes completions and computes
 * context information for a particular content type.
 * <p>
 * This interface must be implemented by clients who extend the
 * <code>org.eclipse.jdt.ui.javadocCompletionProcessor</code> extension-point</p>.
 *
 * @since 2.1
 * @deprecated As of 3.2, replaced by <code>org.eclipse.jdt.ui.javaCompletionProposalComputer</code> extension-point</p>.
 */
public interface IJavadocCompletionProcessor {

	/**
	 * Flag used by <code>computeCompletionProposals</code>.
	 * Specifies that only proposals should be returned that match
	 * the case of the prefix in the code (value: <code>1</code>).
	 */
	int RESTRICT_TO_MATCHING_CASE= 1;


	/**
	 * Returns information about possible contexts based on the
	 * specified location within the compilation unit.
	 *
	 * @param	cu the working copy of the compilation unit which
	 *				is used to compute the possible contexts
	 * @param	offset an offset within the compilation unit for
	 * 				which context information should be computed
	 * @return	an array of context information objects or <code>null</code>
	 * 				if no context could be found
	 */
	IContextInformation[] computeContextInformation(ICompilationUnit cu, int offset);


	/**
	 * Returns the completion proposals based on the specified location
	 * within the compilation unit.
	 *
	 * @param	cu the working copy of the compilation unit in which the
	 * 				completion request has been called.
	 * @param	offset an offset within the compilation unit for which
	 * 				completion proposals should be computed
	 * @param	length the length of the current selection.
	 * @param	flags settings for the code assist. Flags as defined in this interface,
	 *				e.g. <code>RESTRICT_TO_MATCHING_CASE</code>.
	 * @return an array of completion proposals or <code>null</code> if
	 *				no proposals could be found
     */
	IJavaCompletionProposal[] computeCompletionProposals(ICompilationUnit cu, int offset, int length, int flags);


	/**
	 * Returns the reason why this completion processor was unable
	 * to produce a completion proposals or context information.
	 *
	 * @return an error message or <code>null</code> if no error occurred
	 */
	String getErrorMessage();
}

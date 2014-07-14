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
package org.eclipse.jdt.internal.ui.text.javadoc;


import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

import org.eclipse.ui.IEditorPart;

import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavadocCompletionProcessor;

import org.eclipse.jdt.internal.ui.text.java.JavaCompletionProcessor;

/**
 * Javadoc completion processor.
 *
 * @since 3.2
 */
public class JavadocCompletionProcessor extends JavaCompletionProcessor {

	private int fSubProcessorFlags;

	public JavadocCompletionProcessor(IEditorPart editor, ContentAssistant assistant) {
		super(editor, assistant, IJavaPartitions.JAVA_DOC);
		fSubProcessorFlags= 0;
	}

	/**
	 * Tells this processor to restrict is proposals to those
	 * starting with matching cases.
	 *
	 * @param restrict <code>true</code> if proposals should be restricted
	 */
	@Override
	public void restrictProposalsToMatchingCases(boolean restrict) {
		fSubProcessorFlags= restrict ? IJavadocCompletionProcessor.RESTRICT_TO_MATCHING_CASE : 0;
	}

	/**
	 * @see IContentAssistProcessor#getContextInformationValidator()
	 */
	@Override
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.java.JavaCompletionProcessor#createContext(org.eclipse.jface.text.ITextViewer, int)
	 */
	@Override
	protected ContentAssistInvocationContext createContext(ITextViewer viewer, int offset) {
		return new JavadocContentAssistInvocationContext(viewer, offset, fEditor, fSubProcessorFlags);
	}

}

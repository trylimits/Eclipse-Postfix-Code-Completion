/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Paul Fullbright <paul.fullbright@oracle.com> - content assist category enablement - http://bugs.eclipse.org/345213
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java;

import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

import org.eclipse.ui.IEditorPart;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;

import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;


/**
 * Java completion processor.
 */
public class JavaCompletionProcessor extends ContentAssistProcessor {

	private final static String VISIBILITY= JavaCore.CODEASSIST_VISIBILITY_CHECK;
	private final static String ENABLED= "enabled"; //$NON-NLS-1$
	private final static String DISABLED= "disabled"; //$NON-NLS-1$

	private IContextInformationValidator fValidator;
	protected final IEditorPart fEditor;

	public JavaCompletionProcessor(IEditorPart editor, ContentAssistant assistant, String partition) {
		super(assistant, partition);
		fEditor= editor;
	}

	/**
	 * Tells this processor to restrict its proposal to those element
	 * visible in the actual invocation context.
	 *
	 * @param restrict <code>true</code> if proposals should be restricted
	 */
	public void restrictProposalsToVisibility(boolean restrict) {
		Hashtable<String, String> options= JavaCore.getOptions();
		Object value= options.get(VISIBILITY);
		if (value instanceof String) {
			String newValue= restrict ? ENABLED : DISABLED;
			if ( !newValue.equals(value)) {
				options.put(VISIBILITY, newValue);
				JavaCore.setOptions(options);
			}
		}
	}

	/**
	 * Tells this processor to restrict is proposals to those
	 * starting with matching cases.
	 *
	 * @param restrict <code>true</code> if proposals should be restricted
	 */
	public void restrictProposalsToMatchingCases(boolean restrict) {
		// not yet supported
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getContextInformationValidator()
	 */
	@Override
	public IContextInformationValidator getContextInformationValidator() {
		if (fValidator == null)
			fValidator= new JavaParameterListValidator();
		return fValidator;
	}

	/*
	 * @see ContentAssistProcessor#checkDefaultEnablement(CompletionProposalCategory)
	 * @since 3.8
	 */
	@Override
	protected boolean checkDefaultEnablement(CompletionProposalCategory category) {
		return super.checkDefaultEnablement(category) && category.matches(getJavaProject());
	}

	/*
	 * @see ContentAssistProcessor#checkSeparateEnablement(CompletionProposalCategory)
	 * @since 3.8
	 */
	@Override
	protected boolean checkSeparateEnablement(CompletionProposalCategory category) {
		return super.checkSeparateEnablement(category) && category.matches(getJavaProject());
	}

	private IJavaProject getJavaProject() {
		return EditorUtility.getJavaProject(fEditor.getEditorInput());
	}
	
	/*
	 * @see org.eclipse.jdt.internal.ui.text.java.ContentAssistProcessor#filterAndSort(java.util.List, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	protected List<ICompletionProposal> sortProposals(List<ICompletionProposal> proposals, IProgressMonitor monitor, ContentAssistInvocationContext context) {
		ProposalSorterRegistry.getDefault().getCurrentSorter().sortProposals(context, proposals);
		return proposals;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.java.ContentAssistProcessor#createContext(org.eclipse.jface.text.ITextViewer, int)
	 */
	@Override
	protected ContentAssistInvocationContext createContext(ITextViewer viewer, int offset) {
		return new JavaContentAssistInvocationContext(viewer, offset, fEditor);
	}
}

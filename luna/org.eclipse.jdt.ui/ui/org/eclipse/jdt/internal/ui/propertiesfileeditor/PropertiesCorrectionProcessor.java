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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFile;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPartitioningException;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.TextInvocationContext;

import org.eclipse.ui.IEditorPart;

import org.eclipse.ui.texteditor.spelling.SpellingCorrectionProcessor;

import org.eclipse.ltk.core.refactoring.NullChange;

import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.ui.text.java.CompletionProposalComparator;
import org.eclipse.jdt.ui.text.java.correction.ChangeCorrectionProposal;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * The properties file correction processor. Clients can set pre-computed proposals, and if set the
 * processor returns only these pre-computed proposals.
 * 
 * @since 3.7
 */
public class PropertiesCorrectionProcessor implements org.eclipse.jface.text.quickassist.IQuickAssistProcessor {

	private String fErrorMessage;

	private SpellingCorrectionProcessor fSpellingCorrectionProcessor;

	private ICompletionProposal[] fPreComputedProposals;

	private final PropertiesCorrectionAssistant fAssistant;

	public PropertiesCorrectionProcessor(PropertiesCorrectionAssistant assistant) {
		fAssistant= assistant;
		fSpellingCorrectionProcessor= new SpellingCorrectionProcessor();
	}

	/*
	 * @see IContentAssistProcessor#computeCompletionProposals(ITextViewer, int)
	 */
	public ICompletionProposal[] computeQuickAssistProposals(IQuickAssistInvocationContext quickAssistContext) {

		ISourceViewer viewer= quickAssistContext.getSourceViewer();
		int length= viewer != null ? viewer.getSelectedRange().y : 0;
		TextInvocationContext context= new TextInvocationContext(viewer, quickAssistContext.getOffset(), length);

		fErrorMessage= null;
		ICompletionProposal[] res= null;
		if (fPreComputedProposals != null) {
			res= fPreComputedProposals;
		} else {
			try {
				List<ICompletionProposal> proposals= new ArrayList<ICompletionProposal>();
				ICompletionProposal[] spellingProposals= fSpellingCorrectionProcessor.computeQuickAssistProposals(quickAssistContext);
				if (spellingProposals.length > 1) {
					for (int i= 0; i < spellingProposals.length; i++) {
						proposals.add(spellingProposals[i]);
					}
				}
				ICompletionProposal[] assists= PropertiesQuickAssistProcessor.collectAssists(createAssistContext(context));
				if (assists != null) {
					for (int i= 0; i < assists.length; i++) {
						proposals.add(assists[i]);
					}
				}
				res= proposals.toArray(new ICompletionProposal[proposals.size()]);
			} catch (BadLocationException e) {
				fErrorMessage= PropertiesFileEditorMessages.PropertiesCorrectionProcessor_error_quickassist_message;
				JavaPlugin.log(e);
			} catch (BadPartitioningException e) {
				fErrorMessage= PropertiesFileEditorMessages.PropertiesCorrectionProcessor_error_quickassist_message;
				JavaPlugin.log(e);
			}
		}

		if (res == null || res.length == 0) {
			return new ICompletionProposal[] { new ChangeCorrectionProposal(PropertiesFileEditorMessages.PropertiesCorrectionProcessor_NoCorrectionProposal_description, new NullChange(""), 0, null) }; //$NON-NLS-1$
		}
		if (res.length > 1) {
			Arrays.sort(res, new CompletionProposalComparator());
		}
		fPreComputedProposals= null;
		return res;
	}

	/*
	 * @see IContentAssistProcessor#getErrorMessage()
	 */
	public String getErrorMessage() {
		return fErrorMessage;
	}

	/*
	 * @see org.eclipse.jface.text.quickassist.IQuickAssistProcessor#canFix(org.eclipse.jface.text.source.Annotation)
	 */
	public boolean canFix(Annotation annotation) {
		return fSpellingCorrectionProcessor.canFix(annotation);
	}

	/*
	 * @see org.eclipse.jface.text.quickassist.IQuickAssistProcessor#canAssist(org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext)
	 */
	public boolean canAssist(IQuickAssistInvocationContext invocationContext) {
		return PropertiesQuickAssistProcessor.hasAssists(createAssistContext(invocationContext));
	}

	/**
	 * Sets the pre-computed proposals.
	 * 
	 * @param preComputedProposals the pre-computed proposals
	 */
	public void setProposals(ICompletionProposal[] preComputedProposals) {
		fPreComputedProposals= preComputedProposals;
	}

	private PropertiesAssistContext createAssistContext(IQuickAssistInvocationContext invocationContext) {
		IEditorPart editorPart= fAssistant.getEditor();
		IFile file= (IFile) editorPart.getEditorInput().getAdapter(IFile.class);
		ISourceViewer sourceViewer= invocationContext.getSourceViewer();
		IType accessorType= ((PropertiesFileEditor) editorPart).getAccessorType();
		return new PropertiesAssistContext(sourceViewer, invocationContext.getOffset(), invocationContext.getLength(), file, sourceViewer.getDocument(), accessorType);
	}
}

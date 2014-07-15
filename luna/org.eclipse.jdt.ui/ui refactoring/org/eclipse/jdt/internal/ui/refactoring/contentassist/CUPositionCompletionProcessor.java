/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring.contentassist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.graphics.Image;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jface.contentassist.IContentAssistSubjectControl;
import org.eclipse.jface.contentassist.ISubjectControlContentAssistProcessor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.StyledString;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.IProblem;

import org.eclipse.jdt.internal.corext.refactoring.StubTypeContext;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.java.CompletionProposalComparator;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.text.java.JavaCompletionProposal;
import org.eclipse.jdt.internal.ui.text.java.JavaTypeCompletionProposal;
import org.eclipse.jdt.internal.ui.viewsupport.ImageDescriptorRegistry;


public class CUPositionCompletionProcessor implements IContentAssistProcessor, ISubjectControlContentAssistProcessor {

	private static final ImageDescriptorRegistry IMAGE_DESC_REGISTRY= JavaPlugin.getImageDescriptorRegistry();

	private String fErrorMessage;
	private char[] fProposalAutoActivationSet;
	private CompletionProposalComparator fComparator;

	private CompletionContextRequestor fCompletionContextRequestor;

	private CUPositionCompletionRequestor fCompletionRequestor;

	/**
	 * Creates a <code>CUPositionCompletionProcessor</code>.
	 * The completion context must be set via {@link #setCompletionContext(ICompilationUnit,String,String)}.
	 * @param completionRequestor the completion requestor
	 */
	public CUPositionCompletionProcessor(CUPositionCompletionRequestor completionRequestor) {
		fCompletionRequestor= completionRequestor;

		fComparator= new CompletionProposalComparator();
		IPreferenceStore preferenceStore= JavaPlugin.getDefault().getPreferenceStore();
		String triggers= preferenceStore.getString(PreferenceConstants.CODEASSIST_AUTOACTIVATION_TRIGGERS_JAVA);
		fProposalAutoActivationSet = triggers.toCharArray();
	}

	/**
	 * @param cuHandle the {@link ICompilationUnit} in whose context codeComplete will be invoked.
	 * 		The given cu doesn't have to exist (and if it exists, it will not be modified).
	 * 		An independent working copy consisting of
	 * 		<code>beforeString</code> + ${current_input} + <code>afterString</code> will be used.
	 * @param beforeString the string before the input position
	 * @param afterString the string after the input position
	 */
	public void setCompletionContext(final ICompilationUnit cuHandle, final String beforeString, final String afterString) {
		fCompletionContextRequestor= new CompletionContextRequestor() {
			final StubTypeContext fStubTypeContext= new StubTypeContext(cuHandle, beforeString, afterString);
			@Override
			public StubTypeContext getStubTypeContext() {
				return fStubTypeContext;
			}
		};
	}

	public void setCompletionContextRequestor(CompletionContextRequestor completionContextRequestor) {
		fCompletionContextRequestor= completionContextRequestor;
	}

	/**
	 * Computing proposals on a <code>ITextViewer</code> is not supported.
	 * @see #computeCompletionProposals(IContentAssistSubjectControl, int)
	 * @see IContentAssistProcessor#computeCompletionProposals(ITextViewer, int)
	 */
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int documentOffset) {
		Assert.isTrue(false, "ITextViewer not supported"); //$NON-NLS-1$
		return null;
	}

	/**
	 * Computing context information on a <code>ITextViewer</code> is not supported.
	 * @see #computeContextInformation(IContentAssistSubjectControl, int)
	 * @see IContentAssistProcessor#computeContextInformation(ITextViewer, int)
	 */
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int documentOffset) {
		Assert.isTrue(false, "ITextViewer not supported"); //$NON-NLS-1$
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getCompletionProposalAutoActivationCharacters()
	 */
	public char[] getCompletionProposalAutoActivationCharacters() {
		return fProposalAutoActivationSet;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getContextInformationAutoActivationCharacters()
	 */
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getErrorMessage()
	 */
	public String getErrorMessage() {
		return fErrorMessage;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getContextInformationValidator()
	 */
	public IContextInformationValidator getContextInformationValidator() {
		return null; //no context
	}

	/*
	 * @see ISubjectControlContentAssistProcessor#computeContextInformation(IContentAssistSubjectControl, int)
	 */
	public IContextInformation[] computeContextInformation(IContentAssistSubjectControl contentAssistSubjectControl, int documentOffset) {
		return null;
	}

	/*
	 * @see ISubjectControlContentAssistProcessor#computeCompletionProposals(IContentAssistSubjectControl, int)
	 */
	public ICompletionProposal[] computeCompletionProposals(IContentAssistSubjectControl contentAssistSubjectControl, int documentOffset) {
		if (fCompletionContextRequestor == null || fCompletionContextRequestor.getOriginalCu() == null)
			return null;
		String input= contentAssistSubjectControl.getDocument().get();
		if (documentOffset == 0)
			return null;
		ICompletionProposal[] proposals= internalComputeCompletionProposals(documentOffset, input);
		Arrays.sort(proposals, fComparator);
		return proposals;
	}

	private ICompletionProposal[] internalComputeCompletionProposals(int documentOffset, String input) {
		String cuString= fCompletionContextRequestor.getBeforeString() + input + fCompletionContextRequestor.getAfterString();
		ICompilationUnit cu= null;
		try {
			/*
			 * Explicitly create a new non-shared working copy.
			 *
			 * The WorkingCopy cannot easily be shared between calls, since IContentAssistProcessor
			 * has no dispose() lifecycle method. A workaround could be to pass in a WorkingCopyOwner
			 * and dispose the owner's working copies from the caller's dispose().
			 */
			cu= fCompletionContextRequestor.getOriginalCu().getWorkingCopy(new WorkingCopyOwner() {/*subclass*/}, new NullProgressMonitor());
			cu.getBuffer().setContents(cuString);
			int cuPrefixLength= fCompletionContextRequestor.getBeforeString().length();
			fCompletionRequestor.setOffsetReduction(cuPrefixLength);

			cu.codeComplete(cuPrefixLength + documentOffset, fCompletionRequestor);

			JavaCompletionProposal[] proposals= fCompletionRequestor.getResults();
			if (proposals.length == 0) {
				String errorMsg= fCompletionRequestor.getErrorMessage();
				if (errorMsg == null || errorMsg.trim().length() == 0)
					errorMsg= RefactoringMessages.JavaTypeCompletionProcessor_no_completion;
				fErrorMessage= errorMsg;
			} else {
				fErrorMessage= fCompletionRequestor.getErrorMessage();
			}
			return proposals;
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
			return null;
		} finally {
			try {
				if (cu != null)
					cu.discardWorkingCopy();
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
			}
		}
	}

	protected abstract static class CUPositionCompletionRequestor extends CompletionRequestor {
		public static final char[] TRIGGER_CHARACTERS= new char[] { '.' };

		private int fOffsetReduction;

		private List<JavaCompletionProposal> fProposals;
		private String fErrorMessage2;

		private void setOffsetReduction(int offsetReduction) {
			fOffsetReduction= offsetReduction;
			fProposals= new ArrayList<JavaCompletionProposal>();
		}

		@Override
		public final void completionFailure(IProblem error) {
			fErrorMessage2= error.getMessage();
		}

		public final JavaCompletionProposal[] getResults() {
			return fProposals.toArray(new JavaCompletionProposal[fProposals.size()]);
		}

		public final String getErrorMessage() {
			return fErrorMessage2;
		}

		protected final void addAdjustedCompletion(String name, String completion,
				int start, int end, int relevance, ImageDescriptor descriptor) {
			JavaCompletionProposal javaCompletionProposal= new JavaCompletionProposal(completion, start - fOffsetReduction, end - start,
					getImage(descriptor), new StyledString(name), relevance);
			javaCompletionProposal.setTriggerCharacters(TRIGGER_CHARACTERS);
			fProposals.add(javaCompletionProposal);
		}

		protected final void addAdjustedTypeCompletion(String name, String completion,
				int start, int end, int relevance, ImageDescriptor descriptor, String fullyQualifiedName) {
			String replacementString= fullyQualifiedName == null || completion.length() == 0 ? completion : fullyQualifiedName;
			int replacementOffset= start - fOffsetReduction;
			int replacementLength= end - start;
			JavaTypeCompletionProposal javaCompletionProposal= new JavaTypeCompletionProposal(
					replacementString,
					null,
					replacementOffset,
					replacementLength,
					getImage(descriptor),
					new StyledString(name),
					relevance,
					fullyQualifiedName);
			javaCompletionProposal.setTriggerCharacters(TRIGGER_CHARACTERS);
			fProposals.add(javaCompletionProposal);
		}

		private static Image getImage(ImageDescriptor descriptor) {
			return (descriptor == null) ? null : CUPositionCompletionProcessor.IMAGE_DESC_REGISTRY.get(descriptor);
		}
	}
}

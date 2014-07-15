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

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.VerifyEvent;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.core.resources.IFile;

import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.quickassist.IQuickAssistAssistant;
import org.eclipse.jface.text.quickassist.IQuickAssistProcessor;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.ISourceViewerExtension3;

import org.eclipse.ui.progress.WorkbenchJob;

/**
 * Auto edit strategy that escapes a character if it cannot be encoded in the .properties file's
 * encoding.
 * 
 * <p>
 * A quick assist to escape backslashes is offered iff the pasted text is not perfectly correct for
 * the .properties file, i.e. if the text contains
 * <ul>
 * <li>an invalid escape sequence as defined by
 * {@link PropertiesFileEscapes#containsInvalidEscapeSequence(String)}</li>
 * <li>a character which requires Unicode escapes</li>
 * </ul>
 * </p>
 * 
 * @since 3.7
 */
public class PropertiesFileAutoEditStrategy implements IAutoEditStrategy {

	private final IFile fFile;

	private String fCharsetName;

	private CharsetEncoder fCharsetEncoder;

	private final ISourceViewer fSourceViewer;

	public PropertiesFileAutoEditStrategy(IFile file, ISourceViewer sourceViewer) {
		fFile= file;
		fSourceViewer= sourceViewer;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.text.IAutoEditStrategy#customizeDocumentCommand(org.eclipse.jface.text.IDocument, org.eclipse.jface.text.DocumentCommand)
	 */
	public void customizeDocumentCommand(IDocument document, DocumentCommand command) {
		showProposal(escape(command), document);
	}

	private ICompletionProposal escape(DocumentCommand command) {
		try {
			String charsetName= fFile.getCharset();
			if (!charsetName.equals(fCharsetName)) {
				fCharsetName= charsetName;
				fCharsetEncoder= Charset.forName(fCharsetName).newEncoder();
			}
		} catch (CoreException e) {
			return null;
		}

		String text= command.text;
		boolean escapeUnicodeChars= !fCharsetEncoder.canEncode(text);
		boolean escapeBackslash= (text.length() > 1) && ((escapeUnicodeChars && PropertiesFileEscapes.containsUnescapedBackslash(text)) || PropertiesFileEscapes.containsInvalidEscapeSequence(text));

		if (!escapeUnicodeChars && !escapeBackslash)
			return null;

		command.text= PropertiesFileEscapes.escape(text, false, false, escapeUnicodeChars);
		if (escapeBackslash) {
			String proposalText= PropertiesFileEscapes.escape(text, false, true, escapeUnicodeChars);
			return new EscapeBackslashCompletionProposal(proposalText, command.offset, command.text.length(),
					PropertiesFileEditorMessages.EscapeBackslashCompletionProposal_escapeBackslashesInOriginalString);
		}
		return null;
	}

	private void showProposal(final ICompletionProposal proposal, final IDocument document) {
		if (proposal != null && fSourceViewer instanceof ISourceViewerExtension3) {
			final WorkbenchJob job= new WorkbenchJob(PropertiesFileEditorMessages.PropertiesFileAutoEditStrategy_showQuickAssist) {
				@Override
				public IStatus runInUIThread(IProgressMonitor monitor) {
					IQuickAssistAssistant assistant= ((ISourceViewerExtension3)fSourceViewer).getQuickAssistAssistant();
					IQuickAssistProcessor processor= assistant.getQuickAssistProcessor();
					if (processor instanceof PropertiesCorrectionProcessor) {
						((PropertiesCorrectionProcessor)processor).setProposals(new ICompletionProposal[] { proposal });
						assistant.showPossibleQuickAssists();
					}
					return Status.OK_STATUS;
				}
			};
			job.setSystem(true);
			job.schedule(500);
			final StyledText textWidget= fSourceViewer.getTextWidget();
			textWidget.addVerifyKeyListener(new VerifyKeyListener() {
				public void verifyKey(VerifyEvent event) {
					job.cancel();
					textWidget.removeVerifyKeyListener(this);
				}
			});

			final IDocumentListener documentListener= new IDocumentListener() {
				private boolean pasteComplete= false;
				public void documentAboutToBeChanged(DocumentEvent event) {
				}
				public void documentChanged(DocumentEvent event) {
					if (pasteComplete) {
						job.cancel();
						document.removeDocumentListener(this);
					}
					pasteComplete= true;
				}
			};
			document.addDocumentListener(documentListener);
		}
	}
}

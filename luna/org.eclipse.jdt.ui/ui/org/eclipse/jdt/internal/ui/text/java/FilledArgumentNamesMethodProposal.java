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
package org.eclipse.jdt.internal.ui.text.java;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.MessageDialog;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.link.LinkedModeUI;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.jface.text.link.LinkedPositionGroup;

import org.eclipse.ui.IEditorPart;

import org.eclipse.ui.texteditor.link.EditorLinkedModeUI;

import org.eclipse.jdt.core.CompletionProposal;

import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.EditorHighlightingSynchronizer;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

/**
 * A method proposal with filled in argument names.
 */
public class FilledArgumentNamesMethodProposal extends JavaMethodCompletionProposal {

	private IRegion fSelectedRegion; // initialized by apply()
	private int[] fArgumentOffsets;
	private int[] fArgumentLengths;

	public FilledArgumentNamesMethodProposal(CompletionProposal proposal, JavaContentAssistInvocationContext context) {
		super(proposal, context);
	}

	/*
	 * @see ICompletionProposalExtension#apply(IDocument, char)
	 */
	@Override
	public void apply(IDocument document, char trigger, int offset) {
		super.apply(document, trigger, offset);
		int baseOffset= getReplacementOffset();
		String replacement= getReplacementString();

		if (fArgumentOffsets != null && getTextViewer() != null) {
			try {
				LinkedModeModel model= new LinkedModeModel();
				for (int i= 0; i != fArgumentOffsets.length; i++) {
					LinkedPositionGroup group= new LinkedPositionGroup();
					group.addPosition(new LinkedPosition(document, baseOffset + fArgumentOffsets[i], fArgumentLengths[i], LinkedPositionGroup.NO_STOP));
					model.addGroup(group);
				}

				model.forceInstall();
				JavaEditor editor= getJavaEditor();
				if (editor != null) {
					model.addLinkingListener(new EditorHighlightingSynchronizer(editor));
				}

				LinkedModeUI ui= new EditorLinkedModeUI(model, getTextViewer());
				ui.setExitPosition(getTextViewer(), baseOffset + replacement.length(), 0, Integer.MAX_VALUE);
				ui.setExitPolicy(new ExitPolicy(')', document));
				ui.setDoContextInfo(true);
				ui.setCyclingMode(LinkedModeUI.CYCLE_WHEN_NO_PARENT);
				ui.enter();

				fSelectedRegion= ui.getSelectedRegion();

			} catch (BadLocationException e) {
				JavaPlugin.log(e);
				openErrorDialog(e);
			}
		} else {
			fSelectedRegion= new Region(baseOffset + replacement.length(), 0);
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.java.JavaMethodCompletionProposal#needsLinkedMode()
	 */
	@Override
	protected boolean needsLinkedMode() {
		return false; // we handle it ourselves
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.java.LazyJavaCompletionProposal#computeReplacementString()
	 */
	@Override
	protected String computeReplacementString() {

		if (!hasParameters() || !hasArgumentList())
			return super.computeReplacementString();

		StringBuffer buffer= new StringBuffer();
		appendMethodNameReplacement(buffer);

		char[][] parameterNames= fProposal.findParameterNames(null);
		int count= parameterNames.length;
		fArgumentOffsets= new int[count];
		fArgumentLengths= new int[count];

		FormatterPrefs prefs= getFormatterPrefs();

		setCursorPosition(buffer.length());

		if (prefs.afterOpeningParen)
			buffer.append(SPACE);

		for (int i= 0; i != count; i++) {
			if (i != 0) {
				if (prefs.beforeComma)
					buffer.append(SPACE);
				buffer.append(COMMA);
				if (prefs.afterComma)
					buffer.append(SPACE);
			}

			fArgumentOffsets[i]= buffer.length();
			buffer.append(parameterNames[i]);
			fArgumentLengths[i]= parameterNames[i].length;
		}

		if (prefs.beforeClosingParen)
			buffer.append(SPACE);

		buffer.append(RPAREN);

		if (canAutomaticallyAppendSemicolon())
			buffer.append(SEMICOLON);

		return buffer.toString();
	}

	/**
	 * Returns the currently active java editor, or <code>null</code> if it
	 * cannot be determined.
	 *
	 * @return  the currently active java editor, or <code>null</code>
	 */
	private JavaEditor getJavaEditor() {
		IEditorPart part= JavaPlugin.getActivePage().getActiveEditor();
		if (part instanceof JavaEditor)
			return (JavaEditor) part;
		else
			return null;
	}

	/*
	 * @see ICompletionProposal#getSelection(IDocument)
	 */
	@Override
	public Point getSelection(IDocument document) {
		if (fSelectedRegion == null)
			return new Point(getReplacementOffset(), 0);

		return new Point(fSelectedRegion.getOffset(), fSelectedRegion.getLength());
	}

	private void openErrorDialog(BadLocationException e) {
		Shell shell= getTextViewer().getTextWidget().getShell();
		MessageDialog.openError(shell, JavaTextMessages.FilledArgumentNamesMethodProposal_error_msg, e.getMessage());
	}

}

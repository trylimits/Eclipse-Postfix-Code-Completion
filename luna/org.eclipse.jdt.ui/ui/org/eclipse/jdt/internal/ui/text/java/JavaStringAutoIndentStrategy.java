/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Kelly Campbell <kellyc@google.com> - [typing] String literal splitting should use formatter preferences - http://bugs.eclipse.org/48433
 *     Martin Hare Robertson <mchr3k@gmail.com> - [typing] String literal splitting should use formatter preferences - http://bugs.eclipse.org/48433
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java;


import java.util.StringTokenizer;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TextUtilities;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;

import org.eclipse.ui.texteditor.ITextEditorExtension3;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;


/**
 * Auto indent strategy for java strings
 */
public class JavaStringAutoIndentStrategy extends DefaultIndentLineAutoEditStrategy {

	private String fPartitioning;
	private IJavaProject fProject;

	/**
	 * The input string doesn't contain any line delimiter.
	 *
	 * @param inputString the given input string
	 * @param indentation the indentation
	 * @param delimiter the line delimiter
	 * @return the display string
	 */
	private String displayString(String inputString, String indentation, String delimiter) {

		int length = inputString.length();
		StringBuffer buffer = new StringBuffer(length);
		StringTokenizer tokenizer= new StringTokenizer(inputString, "\n\r", true); //$NON-NLS-1$
		while (tokenizer.hasMoreTokens()){

			String token = tokenizer.nextToken();
			if (token.equals("\r")) { //$NON-NLS-1$
				buffer.append("\\r"); //$NON-NLS-1$
				if (tokenizer.hasMoreTokens()) {
					token = tokenizer.nextToken();
					if (token.equals("\n")) { //$NON-NLS-1$
						buffer.append("\\n"); //$NON-NLS-1$
						buffer.append("\" + " + delimiter); //$NON-NLS-1$
						buffer.append(indentation);
						buffer.append("\""); //$NON-NLS-1$
						continue;
					} else {
						buffer.append("\" + " + delimiter); //$NON-NLS-1$
						buffer.append(indentation);
						buffer.append("\""); //$NON-NLS-1$
					}
				} else {
					continue;
				}
			} else if (token.equals("\n")) { //$NON-NLS-1$
				buffer.append("\\n"); //$NON-NLS-1$
				buffer.append("\" + " + delimiter); //$NON-NLS-1$
				buffer.append(indentation);
				buffer.append("\""); //$NON-NLS-1$
				continue;
			}

			StringBuffer tokenBuffer = new StringBuffer();
			for (int i = 0; i < token.length(); i++){
				char c = token.charAt(i);
				switch (c) {
					case '\r' :
						tokenBuffer.append("\\r"); //$NON-NLS-1$
						break;
					case '\n' :
						tokenBuffer.append("\\n"); //$NON-NLS-1$
						break;
					case '\b' :
						tokenBuffer.append("\\b"); //$NON-NLS-1$
						break;
					case '\t' :
						// keep tabs verbatim
						tokenBuffer.append("\t"); //$NON-NLS-1$
						break;
					case '\f' :
						tokenBuffer.append("\\f"); //$NON-NLS-1$
						break;
					case '\"' :
						tokenBuffer.append("\\\""); //$NON-NLS-1$
						break;
					case '\\' :
						tokenBuffer.append("\\\\"); //$NON-NLS-1$
						break;
					default :
						tokenBuffer.append(c);
				}
			}
			buffer.append(tokenBuffer);
		}
		return buffer.toString();
	}

	/**
	 * Creates a new Java string auto indent strategy for the given document partitioning.
	 *
	 * @param partitioning the document partitioning
	 * @param project the project for retrieving project specific preferences
	 */
	public JavaStringAutoIndentStrategy(String partitioning, IJavaProject project) {
		super();
		fPartitioning= partitioning;
		fProject= project;
	}

	private boolean isLineDelimiter(IDocument document, String text) {
		String[] delimiters= document.getLegalLineDelimiters();
		if (delimiters != null)
			return TextUtilities.equals(delimiters, text) > -1;
		return false;
	}

	private String getLineIndentation(IDocument document, int offset) throws BadLocationException {

		// find start of line
		int adjustedOffset= (offset == document.getLength() ? offset  - 1 : offset);
		IRegion line= document.getLineInformationOfOffset(adjustedOffset);
		int start= line.getOffset();

		// find white spaces
		int end= findEndOfWhiteSpace(document, start, offset);

		return document.get(start, end - start);
	}

	private String getModifiedText(String string, String indentation, String delimiter) {
		return displayString(string, indentation, delimiter);
	}

	private void javaStringIndentAfterNewLine(IDocument document, DocumentCommand command) throws BadLocationException {

		ITypedRegion partition= TextUtilities.getPartition(document, fPartitioning, command.offset, true);
		int offset= partition.getOffset();
		int length= partition.getLength();

		if (command.offset == offset + length && document.getChar(offset + length - 1) == '\"')
			return;

		String indentation= getLineIndentation(document, command.offset);
		String delimiter= TextUtilities.getDefaultLineDelimiter(document);

		IRegion line= document.getLineInformationOfOffset(offset);
		String string= document.get(line.getOffset(), offset - line.getOffset()).trim();
		if (string.length() != 0 && !string.equals("+")) //$NON-NLS-1$
			indentation += getExtraIndentAfterNewLine();
		
		boolean isLineDelimiter= isLineDelimiter(document, command.text);
		if (isEditorWrapStrings() && isLineDelimiter) {
			if (isWrappingBeforeBinaryOperator()) {
				command.text= "\"" + command.text + indentation + "+ \"";  //$NON-NLS-1$//$NON-NLS-2$
			} else {
				command.text= "\" +" + command.text + indentation + "\"";  //$NON-NLS-1$//$NON-NLS-2$
			}
		} else if (command.text.length() > 1 && !isLineDelimiter && isEditorEscapeStrings()) {
			command.text= getModifiedText(command.text, indentation, delimiter);
		}
	}

	private boolean isEditorWrapStrings() {
		IPreferenceStore preferenceStore= JavaPlugin.getDefault().getPreferenceStore();
		return preferenceStore.getBoolean(PreferenceConstants.EDITOR_WRAP_STRINGS);
	}

	private boolean isEditorEscapeStrings() {
		IPreferenceStore preferenceStore= JavaPlugin.getDefault().getPreferenceStore();
		return preferenceStore.getBoolean(PreferenceConstants.EDITOR_ESCAPE_STRINGS);
	}

	private String getCoreFormatterOption(String key) {
		if (fProject == null)
			return JavaCore.getOption(key);
		return fProject.getOption(key, true);
	}

	private boolean isWrappingBeforeBinaryOperator() {
		return DefaultCodeFormatterConstants.TRUE.equals(getCoreFormatterOption(
				DefaultCodeFormatterConstants.FORMATTER_WRAP_BEFORE_BINARY_OPERATOR));
	}

	private int getContinuationIndentationSize() {
		int formatterContinuationIndentationSize= 2;
		try {
			formatterContinuationIndentationSize= Integer.parseInt(getCoreFormatterOption(
					DefaultCodeFormatterConstants.FORMATTER_CONTINUATION_INDENTATION));
		} catch (NumberFormatException ex) {
			// Ignore, use default of 2
		}
		return formatterContinuationIndentationSize;
	}
	
	private int getBinaryOperatorAlignmentStyle() {
		String binaryAlignmentValue= getCoreFormatterOption(
				DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_BINARY_EXPRESSION);
		return DefaultCodeFormatterConstants.getIndentStyle(binaryAlignmentValue);
	}

	/**
	 * Returns extra indentation string for strings that are broken by a newline
	 * based on the value of the formatter preferences for tabs vs. spaces.
	 *
	 * @return two tabs or equivalent number of spaces
	 */
	private String getExtraIndentAfterNewLine() {
		// read settings
		int formatterContinuationIndentationSize= getContinuationIndentationSize();
		int binaryAlignmentValue= getBinaryOperatorAlignmentStyle();
		
		// work out indent
		int indentSize= formatterContinuationIndentationSize;
		if (binaryAlignmentValue == DefaultCodeFormatterConstants.INDENT_BY_ONE) {
			indentSize= 1;
		} else if (binaryAlignmentValue == DefaultCodeFormatterConstants.INDENT_ON_COLUMN) {
			// there is no obvious way to work out the current column indent
		}
		
		// generate indentation string with correct size
		return CodeFormatterUtil.createIndentString(indentSize, fProject);
	}

	private boolean isSmartMode() {
		IWorkbenchPage page= JavaPlugin.getActivePage();
		if (page != null)  {
			IEditorPart part= page.getActiveEditor();
			if (part instanceof ITextEditorExtension3) {
				ITextEditorExtension3 extension= (ITextEditorExtension3) part;
				return extension.getInsertMode() == ITextEditorExtension3.SMART_INSERT;
			}
		}
		return false;
	}

	/*
	 * @see org.eclipse.jface.text.IAutoIndentStrategy#customizeDocumentCommand(IDocument, DocumentCommand)
	 */
	@Override
	public void customizeDocumentCommand(IDocument document, DocumentCommand command) {
		try {
			if (command.text == null)
				return;
			if (isSmartMode())
				javaStringIndentAfterNewLine(document, command);
		} catch (BadLocationException e) {
		}
	}
}

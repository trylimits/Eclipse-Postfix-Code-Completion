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
package org.eclipse.jdt.internal.ui.text.java;

import java.util.LinkedList;
import java.util.Map;

import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.TypedPosition;
import org.eclipse.jface.text.formatter.ContextBasedFormattingStrategy;
import org.eclipse.jface.text.formatter.FormattingContextProperties;
import org.eclipse.jface.text.formatter.IFormattingContext;

import org.eclipse.jdt.core.formatter.CodeFormatter;

import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Formatting strategy for java source code.
 *
 * @since 3.0
 */
public class JavaFormattingStrategy extends ContextBasedFormattingStrategy {

	/** Documents to be formatted by this strategy */
	private final LinkedList<IDocument> fDocuments= new LinkedList<IDocument>();
	/** Partitions to be formatted by this strategy */
	private final LinkedList<TypedPosition> fPartitions= new LinkedList<TypedPosition>();

	/**
	 * Creates a new java formatting strategy.
 	 */
	public JavaFormattingStrategy() {
		super();
	}

	/*
	 * @see org.eclipse.jface.text.formatter.ContextBasedFormattingStrategy#format()
	 */
	@Override
	public void format() {
		super.format();

		final IDocument document= fDocuments.removeFirst();
		final TypedPosition partition= fPartitions.removeFirst();

		if (document != null && partition != null) {
			Map<String, IDocumentPartitioner> partitioners= null;
			try {

				final TextEdit edit= CodeFormatterUtil.reformat(CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.F_INCLUDE_COMMENTS, document.get(), partition.getOffset(), partition.getLength(), 0, TextUtilities.getDefaultLineDelimiter(document), getPreferences());
				if (edit != null) {
					if (edit.getChildrenSize() > 20)
						partitioners= TextUtilities.removeDocumentPartitioners(document);

					edit.apply(document);
				}

			} catch (MalformedTreeException exception) {
				JavaPlugin.log(exception);
			} catch (BadLocationException exception) {
				// Can only happen on concurrent document modification - log and bail out
				JavaPlugin.log(exception);
			} finally {
				if (partitioners != null)
					TextUtilities.addDocumentPartitioners(document, partitioners);
			}
		}
 	}

	/*
	 * @see org.eclipse.jface.text.formatter.ContextBasedFormattingStrategy#formatterStarts(org.eclipse.jface.text.formatter.IFormattingContext)
	 */
	@Override
	public void formatterStarts(final IFormattingContext context) {
		super.formatterStarts(context);

		fPartitions.addLast((TypedPosition) context.getProperty(FormattingContextProperties.CONTEXT_PARTITION));
		fDocuments.addLast((IDocument) context.getProperty(FormattingContextProperties.CONTEXT_MEDIUM));
	}

	/*
	 * @see org.eclipse.jface.text.formatter.ContextBasedFormattingStrategy#formatterStops()
	 */
	@Override
	public void formatterStops() {
		super.formatterStops();

		fPartitions.clear();
		fDocuments.clear();
	}
}

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.eclipse.core.runtime.AssertionFailedException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.TypedRegion;

import org.eclipse.ui.texteditor.spelling.ISpellingProblemCollector;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.IPropertiesFilePartitions;
import org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellChecker;

/**
 * Properties file spelling engine
 *
 * @since 3.1
 */
public class PropertiesFileSpellingEngine extends SpellingEngine {

	/*
	 * @see org.eclipse.jdt.internal.ui.text.spelling.SpellingEngine#check(org.eclipse.jface.text.IDocument, org.eclipse.jface.text.IRegion[], org.eclipse.jdt.internal.ui.text.spelling.engine.ISpellChecker, org.eclipse.ui.texteditor.spelling.ISpellingProblemCollector, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	protected void check(IDocument document, IRegion[] regions, ISpellChecker checker, ISpellingProblemCollector collector, IProgressMonitor monitor) {
		SpellEventListener listener= new SpellEventListener(collector, document);
		boolean isIgnoringAmpersand= PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.SPELLING_IGNORE_AMPERSAND_IN_PROPERTIES);
		try {
			List<ITypedRegion> partitionList= new ArrayList<ITypedRegion>();
			for (int i= 0; i < regions.length; i++)
				partitionList.addAll(Arrays.asList(TextUtilities.computePartitioning(document, IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING, regions[i].getOffset(), regions[i].getLength(), false)));
			ITypedRegion[] partitions= partitionList.toArray(new ITypedRegion[partitionList.size()]);

			for (int i= 0; i < partitions.length; i++) {
				if (monitor != null && monitor.isCanceled())
					return;
				if (listener.isProblemsThresholdReached())
					return;

				ITypedRegion partition= partitions[i];
				if (IPropertiesFilePartitions.COMMENT.equals(partition.getType())) {
					for (; i < partitions.length - 1; i++) {
						ITypedRegion next= partitions[i+1];
						int gapOffset= partition.getOffset() + partition.getLength();
						int gapLength= next.getOffset() - gapOffset;
						if ((IPropertiesFilePartitions.COMMENT.equals(next.getType()) || isWhitespace(document, next.getOffset(), next.getLength())) && isWhitespace(document, gapOffset, gapLength))
							partition= new TypedRegion(partition.getOffset(), next.getOffset() + next.getLength() - partition.getOffset(), partition.getType());
						else
							break;
					}
				}
				String partitionType= partition.getType();
				if (IPropertiesFilePartitions.COMMENT.equals(partitionType) || (!isIgnoringAmpersand && IPropertiesFilePartitions.PROPERTY_VALUE.equals(partitionType))) {
					Locale locale= checker.getLocale();
					checker.execute(listener, new SpellCheckIterator(document, partition, locale));
				} else if (isIgnoringAmpersand && IPropertiesFilePartitions.PROPERTY_VALUE.equals(partitionType)) {
					Locale locale= checker.getLocale();
					checker.execute(listener, new PropertiesFileSpellCheckIterator(document, partition, locale));
				}
			}
		} catch (BadLocationException x) {
			// ignore: the document has been changed in another thread and will be checked again
		} catch (AssertionFailedException x) {
			// ignore: the document has been changed in another thread and will be checked again
		}
	}

	/**
	 * Returns <code>true</code> iff the given region contains only
	 * whitespace.
	 *
	 * @param document the document
	 * @param offset the region's offset
	 * @param length the region's length
	 * @return <code>true</code> iff the given region contains only
	 *         whitespace
	 */
	private boolean isWhitespace(IDocument document, int offset, int length) {
		try {
			for (int i= 0; i < length; i++)
				if (!Character.isWhitespace(document.getChar(offset + i)))
					return false;
			return true;
		} catch (BadLocationException x) {
			JavaPlugin.log(x);
			return false;
		}
	}
}

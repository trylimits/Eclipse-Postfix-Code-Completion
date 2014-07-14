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

package org.eclipse.jdt.internal.ui.propertiesfileeditor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPartitioningException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.WhitespaceRule;
import org.eclipse.jface.text.rules.WordRule;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.IColorManager;

import org.eclipse.jdt.internal.ui.text.AbstractJavaScanner;
import org.eclipse.jdt.internal.ui.text.JavaWhitespaceDetector;


/**
 * A rule based property value scanner.
 *
 * @since 3.1
 */
public final class PropertyValueScanner extends AbstractJavaScanner {

	public class AssignmentDetector implements IWordDetector {

		/*
		 * @see IWordDetector#isWordStart
		 */
		public boolean isWordStart(char c) {
			if ('=' != c && ':' != c || fDocument == null)
				return false;

			try {
				// check whether it is the first '=' in the logical line

				int i=fOffset-2;
				while (Character.isWhitespace(fDocument.getChar(i))) {
					i--;
				}
				
				ITypedRegion partition= null;
				if (fDocument instanceof IDocumentExtension3)
					partition= ((IDocumentExtension3)fDocument).getPartition(IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING, i, false);
				return partition != null && IDocument.DEFAULT_CONTENT_TYPE.equals(partition.getType());
			} catch (BadLocationException ex) {
				return false;
			} catch (BadPartitioningException e) {
				return false;
			}
		}

		/*
		 * @see IWordDetector#isWordPart
		 */
		public boolean isWordPart(char c) {
			return false;
		}
	}


	private static String[] fgTokenProperties= {
		PreferenceConstants.PROPERTIES_FILE_COLORING_VALUE,
		PreferenceConstants.PROPERTIES_FILE_COLORING_ARGUMENT,
		PreferenceConstants.PROPERTIES_FILE_COLORING_ASSIGNMENT
	};


	/**
	 * Creates a property value code scanner
	 *
	 * @param manager	the color manager
	 * @param store		the preference store
	 */
	public PropertyValueScanner(IColorManager manager, IPreferenceStore store) {
		super(manager, store);
		initialize();
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.AbstractJavaScanner#getTokenProperties()
	 */
	@Override
	protected String[] getTokenProperties() {
		return fgTokenProperties;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.AbstractJavaScanner#createRules()
	 */
	@Override
	protected List<IRule> createRules() {
		setDefaultReturnToken(getToken(PreferenceConstants.PROPERTIES_FILE_COLORING_VALUE));
		List<IRule> rules= new ArrayList<IRule>();

		// Add rule for arguments.
		IToken token= getToken(PreferenceConstants.PROPERTIES_FILE_COLORING_ARGUMENT);
		rules.add(new ArgumentRule(token));

		// Add word rule for assignment operator.
		token= getToken(PreferenceConstants.PROPERTIES_FILE_COLORING_ASSIGNMENT);
		WordRule wordRule= new WordRule(new AssignmentDetector(), token);
		rules.add(wordRule);

		// Add generic whitespace rule.
		rules.add(new WhitespaceRule(new JavaWhitespaceDetector()));

		return rules;
	}
}

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
package org.eclipse.jdt.internal.corext.refactoring.nls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.ibm.icu.text.Collator;

import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.ReplaceEdit;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextUtilities;

import org.eclipse.ltk.core.refactoring.TextChange;

import org.eclipse.jdt.internal.corext.refactoring.changes.TextChangeCompatibility;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.Strings;

import org.eclipse.jdt.internal.ui.propertiesfileeditor.PropertiesFileEscapes;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

public class PropertyFileDocumentModel {

	private List<KeyValuePairModell> fKeyValuePairs;
    private String fLineDelimiter;

    public PropertyFileDocumentModel(IDocument document) {
        parsePropertyDocument(document);
        fLineDelimiter= TextUtilities.getDefaultLineDelimiter(document);
    }

    /**
	 * @return the line delimiter used by the document described by this model
	 */
    public String getLineDelimiter() {
		return fLineDelimiter;
	}

    /**
	 * Return the key value pair in this model with the key <code>key</code>
	 *
	 * @param key the key of the pair
	 * @return the pair with the key or <b>null</b> if no such pair.
	 */
    public KeyValuePair getKeyValuePair(String key) {
    	for (int i= 0; i < fKeyValuePairs.size(); i++) {
            KeyValuePairModell keyValuePair = fKeyValuePairs.get(i);
            if (keyValuePair.getKey().equals(key)) {
            	return keyValuePair;
            }
    	}
    	return null;
    }

    private InsertEdit insert(KeyValuePair keyValuePair) {
        KeyValuePairModell keyValuePairModell = new KeyValuePairModell(keyValuePair);
        int index = findInsertPosition(keyValuePairModell);
        KeyValuePairModell insertHere = fKeyValuePairs.get(index);
        int offset = insertHere.fOffset;

        String extra= ""; //$NON-NLS-1$
        if (insertHere instanceof LastKeyValuePair && ((LastKeyValuePair)insertHere).needsNewLine()) {
        	extra= fLineDelimiter;
        	((LastKeyValuePair)insertHere).resetNeedsNewLine();
        	offset-= insertHere.fLeadingWhiteSpaces;
        } else if (index > 0) {
        	String beforeKey= fKeyValuePairs.get(index - 1).fKey;
			String afterKey= insertHere.fKey;
			String key= keyValuePair.fKey;
			int distBefore= NLSUtil.invertDistance(key, beforeKey);
			int distAfter= NLSUtil.invertDistance(key, afterKey);
			if (distBefore > distAfter) {
				offset-= insertHere.fLeadingWhiteSpaces;
			} else if (distBefore == distAfter && Collator.getInstance().compare(beforeKey, afterKey) < 0) {
				offset-= insertHere.fLeadingWhiteSpaces;
			} else {
				//insert it before afterKey -> move the leading white spaces to the inserted pair
				keyValuePairModell.fLeadingWhiteSpaces= insertHere.fLeadingWhiteSpaces;
				insertHere.fLeadingWhiteSpaces= 0;
			}
        }

        String text= extra + keyValuePairModell.getKeyValueText();
        keyValuePairModell.fOffset= offset;
        keyValuePairModell.fLength= text.length();
        fKeyValuePairs.add(index, keyValuePairModell);
		return new InsertEdit(offset, text);
    }

    /**
     * Inserts the given key value pairs into this model at appropriate
     * positions. Records all required text changes in the given change
     *
     * @param keyValuePairs the key value pairs to insert
     * @param change the change to use to record text changes
     */
    public void insert(KeyValuePair[] keyValuePairs, TextChange change) {

        ArrayList<KeyValuePair> sorted= new ArrayList<KeyValuePair>(Arrays.asList(keyValuePairs));
        Collections.sort(sorted, new Comparator<KeyValuePair>() {
			public int compare(KeyValuePair p1, KeyValuePair p2) {
				return Collator.getInstance().compare(p1.fKey, p2.fKey);
			}
        });

        for (int i = 0; i < sorted.size(); i++) {
            KeyValuePair curr= sorted.get(i);
			InsertEdit insertEdit= insert(curr);

            String message= Messages.format(NLSMessages.NLSPropertyFileModifier_add_entry, BasicElementLabels.getJavaElementName(curr.getKey()));
			TextChangeCompatibility.addTextEdit(change, message, insertEdit);
        }
    }

    public DeleteEdit remove(String key) {
    	for (Iterator<KeyValuePairModell> iter = fKeyValuePairs.iterator(); iter.hasNext();) {
            KeyValuePairModell keyValuePair = iter.next();
            if (keyValuePair.fKey.equals(key)) {
            	return new DeleteEdit(keyValuePair.fOffset, keyValuePair.getLength());
            }
        }
        return null;
    }

    public ReplaceEdit replace(KeyValuePair toReplace, KeyValuePair replaceWith) {
        for (Iterator<KeyValuePairModell> iter = fKeyValuePairs.iterator(); iter.hasNext();) {
            KeyValuePairModell keyValuePair = iter.next();
            if (keyValuePair.fKey.equals(toReplace.getKey())) {
                String newText= new KeyValuePairModell(replaceWith).getKeyValueText();
                return new ReplaceEdit(keyValuePair.fOffset, keyValuePair.getLength(), newText);
            }
        }
        return null;
    }

    private int findInsertPosition(KeyValuePairModell keyValuePair) {
    	ArrayList<String> keys= new ArrayList<String>();
        for (int i= 0; i < fKeyValuePairs.size(); i++) {
            KeyValuePairModell element = fKeyValuePairs.get(i);
            if (! (element instanceof LastKeyValuePair))
            	keys.add(element.getKey());
        }
        int insertIndex= NLSUtil.getInsertionPosition(keyValuePair.getKey(), keys);

        if (insertIndex < fKeyValuePairs.size() - 1) {
            insertIndex++;
        }

        return insertIndex;
    }

    private void parsePropertyDocument(IDocument document) {
        fKeyValuePairs = new ArrayList<KeyValuePairModell>();

        SimpleLineReader reader = new SimpleLineReader(document);
        int offset = 0;
        String line = reader.readLine();
        int leadingWhiteSpaces = 0;
        while (line != null) {
            if (!SimpleLineReader.isCommentOrWhiteSpace(line)) {
                int idx = getIndexOfSeparationCharacter(line);
                if (idx != -1) {
					String key= line.substring(0, idx);
					String trimmedKey= key.trim();
					String value= line.substring(idx + 1);
					String trimmedValue= Strings.trimLeadingTabsAndSpaces(value);
					int length= key.length() + 1 + value.length();
                    fKeyValuePairs.add(new KeyValuePairModell(trimmedKey, trimmedValue, offset, length, leadingWhiteSpaces));
                    leadingWhiteSpaces = 0;
                }
            } else {
                leadingWhiteSpaces += line.length();
            }
            offset += line.length();
            line = reader.readLine();
        }
        int lastLine= document.getNumberOfLines() - 1;
        boolean needsNewLine= false;
        try {
        	needsNewLine= !(document.getLineLength(lastLine) == 0);
		} catch (BadLocationException ignore) {
			// treat last line having no new line
		}
        LastKeyValuePair lastKeyValuePair = new LastKeyValuePair(offset, needsNewLine);
		fKeyValuePairs.add(lastKeyValuePair);
    }

    private int getIndexOfSeparationCharacter(String line) {
        int minIndex = -1;
        int indexOfEven = line.indexOf('=');
        int indexOfColumn = line.indexOf(':');
        int indexOfBlank = line.indexOf(' ');

        if ((indexOfEven != -1) && (indexOfColumn != -1)) {
            minIndex = Math.min(indexOfEven, indexOfColumn);
        } else {
            minIndex = Math.max(indexOfEven, indexOfColumn);
        }

		if ((minIndex == -1) && (indexOfBlank != -1)) {
			minIndex= indexOfBlank;
        }

        return minIndex;
    }

	public static String escape(String s, boolean escapeCommentCharsAndLeadingWhitespaces) {
		StringBuffer sb= new StringBuffer(s.length());
		int length= s.length();
		for (int i= 0; i < length; i++){
			char c= s.charAt(i);
			sb.append(PropertiesFileEscapes.escape(c));
		}
		if(!escapeCommentCharsAndLeadingWhitespaces)
			return sb.toString();
		return escapeLeadingWhiteSpaces(escapeCommentChars(sb.toString()));
	}

	private static String escapeCommentChars(String string) {
	    StringBuffer sb = new StringBuffer(string.length() + 5);
	    for (int i = 0; i < string.length(); i++) {
	      char c = string.charAt(i);
	      switch (c) {
	      case '!':
	        sb.append("\\!"); //$NON-NLS-1$
	        break;
	      case '#':
	        sb.append("\\#"); //$NON-NLS-1$
	        break;
	      default:
	        sb.append(c);
	      }
	    }
	    return sb.toString();
	}

	private static String escapeLeadingWhiteSpaces(String str) {
		int firstNonWhiteSpace= findFirstNonWhiteSpace(str);
		StringBuffer buf= new StringBuffer(firstNonWhiteSpace);
		for (int i = 0; i < firstNonWhiteSpace; i++) {
			buf.append('\\');
		    buf.append(str.charAt(i));
		}
		buf.append(str.substring(firstNonWhiteSpace));
		return buf.toString();
	}

	/**
	 * @param s the string to inspect
	 * @return the first non whitespace character, the length if only whitespace characters
	 */
	private static int findFirstNonWhiteSpace(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isWhitespace(s.charAt(i)))
				return i;
		}
		return s.length();
	}

	private static class KeyValuePairModell extends KeyValuePair {

        int fOffset;
        int fLength;
        int fLeadingWhiteSpaces;

        public KeyValuePairModell(String key, String value, int offset, int length, int leadingWhiteSpaces) {
            super(key, value);
            fOffset = offset;
            fLength = length;
            fLeadingWhiteSpaces = leadingWhiteSpaces;
        }

		public KeyValuePairModell(KeyValuePair keyValuePair) {
            super(keyValuePair.fKey, keyValuePair.fValue);
        }

		public int getLength() {
			return fLength;
		}
		
        private String getKeyValueText() {
			return fKey + '=' + fValue;
        }
    }

    /**
     * anchor element for a list of KeyValuePairs. (it is greater than every
     * other KeyValuePair)
     */
    private static class LastKeyValuePair extends KeyValuePairModell {

    	private boolean fNeedsNewLine;

        public LastKeyValuePair(int offset, boolean needsNewLine) {
            super("zzzzzzz", "key", offset, 7 + 1 + 3, 0); //$NON-NLS-1$ //$NON-NLS-2$
            fNeedsNewLine= needsNewLine;
        }
        public boolean needsNewLine() {
        	return fNeedsNewLine;
        }
        public void resetNeedsNewLine() {
        	fNeedsNewLine= false;
        }
    }
}

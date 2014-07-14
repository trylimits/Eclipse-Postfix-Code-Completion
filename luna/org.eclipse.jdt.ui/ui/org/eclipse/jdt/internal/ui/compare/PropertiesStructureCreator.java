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
package org.eclipse.jdt.internal.ui.compare;

import org.eclipse.swt.graphics.Image;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.rules.FastPartitioner;

import org.eclipse.compare.CompareUI;
import org.eclipse.compare.IEditableContent;
import org.eclipse.compare.ISharedDocumentAdapter;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.DocumentRangeNode;
import org.eclipse.compare.structuremergeviewer.IStructureComparator;
import org.eclipse.compare.structuremergeviewer.StructureCreator;
import org.eclipse.compare.structuremergeviewer.StructureRootNode;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.IPropertiesFilePartitions;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.PropertiesFilePartitionScanner;


public class PropertiesStructureCreator extends StructureCreator {

	/**
	 * A PropertyNode represents a key/value pair of a Java property file.
	 * The text range of a leg/value pair starts with an optional
	 * comment and ends right after the value.
	 */
	static class PropertyNode extends DocumentRangeNode implements ITypedElement {

		public PropertyNode(DocumentRangeNode parent, int type, String id, IDocument doc, int start, int length) {
			super(parent, type, id, doc, start, length);
			if (parent != null) {
				parent.addChild(this);
			}
		}

		/* (non Java doc)
		 * see ITypedElement#getName
		 */
		public String getName() {
			return getId();
		}

		/* (non Java doc)
		 * see ITypedElement#getType
		 */
		public String getType() {
			return "properties2"; //$NON-NLS-1$
		}

		/* (non Java doc)
		 * see ITypedElement#getImage
		 */
		public Image getImage() {
			return CompareUI.getImage(getType());
		}
	}

	private static final String WHITESPACE= " \t\r\n\f"; //$NON-NLS-1$
	private static final String SEPARATORS= "=:"; //$NON-NLS-1$
	private static final String SEPARATORS2= SEPARATORS + WHITESPACE;


	public PropertiesStructureCreator() {
	}

	public String getName() {
		return CompareMessages.PropertyCompareViewer_title;
	}

	@Override
	protected IStructureComparator createStructureComparator(Object input,
			IDocument document, ISharedDocumentAdapter sharedDocumentAdapter,
			IProgressMonitor monitor) throws CoreException {

		final boolean isEditable;
		if (input instanceof IEditableContent)
			isEditable= ((IEditableContent) input).isEditable();
		else
			isEditable= false;

		DocumentRangeNode root= new StructureRootNode(document, input, this, sharedDocumentAdapter) {
			@Override
			public boolean isEditable() {
				return isEditable;
			}
		};

		try {
			monitor = beginWork(monitor);
			parsePropertyFile(root, document, monitor);
		} finally {
			monitor.done();
		}

		return root;
	}

	@Override
	public IStructureComparator locate(Object path, Object source) {
		return null;
	}

	public String getContents(Object node, boolean ignoreWhitespace) {
		if (node instanceof IStreamContentAccessor) {
			IStreamContentAccessor sca= (IStreamContentAccessor) node;
			try {
				return JavaCompareUtilities.readString(sca);
			} catch (CoreException ex) {
				JavaPlugin.log(ex);
			}
		}
		return null;
	}

	private String readLine(int[] args, IDocument doc) {
		int line= args[0]++;
		try {
			IRegion region= doc.getLineInformation(line);
			int start= region.getOffset();
			int length= region.getLength();

			try {
				region= doc.getLineInformation(line+1);
				args[1]= region.getOffset();
			} catch (BadLocationException ex) {
				args[1]= doc.getLength();
			}

			return doc.get(start, length);
		} catch (BadLocationException ex) {
			// silently ignored
		}
		return null;
	}

	private void parsePropertyFile(DocumentRangeNode root, IDocument doc, IProgressMonitor monitor) {

		int start= -1;
		int lineStart= 0;

		int[] args= new int[2];
		args[0]= 0;	// here we return the line number
		args[1]= 0;	// and here the offset of the first character of the line

		for (;;) {
			worked(monitor);
			lineStart= args[1];	// start of current line
            String line= readLine(args, doc);
			if (line == null)
				return;

			if (line.length() <= 0)
				continue;	// empty line

			char firstChar= line.charAt(0);
			if (firstChar == '#' || firstChar == '!') {
				if (start < 0)	// comment belongs to next key/value pair
					start= lineStart;
				continue;	// comment
			}

			// find continuation lines
			while (needNextLine(line)) {
				String nextLine= readLine(args, doc);
				if (nextLine == null)
					nextLine= ""; //$NON-NLS-1$
				String line2= line.substring(0, line.length()-1);
				int startPos= 0;
				for (; startPos < nextLine.length(); startPos++)
					if (WHITESPACE.indexOf(nextLine.charAt(startPos)) == -1)
						break;
				nextLine= nextLine.substring(startPos, nextLine.length());
				line= line2 + nextLine;
			}

    		// key start
    		int len= line.length();
    		int keyPos= 0;
    		for (; keyPos < len; keyPos++) {
       			if (WHITESPACE.indexOf(line.charAt(keyPos)) == -1)
            		break;
    		}

    		// key/value separator
    		int separatorPos;
    		for (separatorPos= keyPos; separatorPos < len; separatorPos++) {
        		char c= line.charAt(separatorPos);
        		if (c == '\\')
            		separatorPos++;
        		else if (SEPARATORS2.indexOf(c) != -1)
            		break;
    		}

     		int valuePos;
    		for (valuePos= separatorPos; valuePos < len; valuePos++)
        		if (WHITESPACE.indexOf(line.charAt(valuePos)) == -1)
            		break;

     		if (valuePos < len)
        		if (SEPARATORS.indexOf(line.charAt(valuePos)) != -1)
            		valuePos++;

     		while (valuePos < len) {
        		if (WHITESPACE.indexOf(line.charAt(valuePos)) == -1)
            		break;
        		valuePos++;
    		}

    		String key= convert(line.substring(keyPos, separatorPos));
    		if (key.length() > 0) {

 				if (start < 0)
					start= lineStart;

	    		int length= args[1] - start;

				try {
					String s= doc.get(start, length);
					for (int i= s.length()-1; i >= 0; i--) {
						char c= s.charAt(i);
						if (c !='\r' && c != '\n')
							break;
						length--;
					}
				} catch (BadLocationException e) {
					// silently ignored
				}

				new PropertyNode(root, 0, key, doc, start, length);
 				start= -1;
   			}
		}
	}

	private boolean needNextLine(String line) {
		int slashes= 0;
		int ix= line.length() - 1;
		while ((ix >= 0) && (line.charAt(ix--) == '\\'))
			slashes++;
		return slashes % 2 == 1;
	}

	/*
	 * Converts escaped characters to Unicode.
	 */
	private String convert(String s) {
		int l= s.length();
		StringBuffer buf= new StringBuffer(l);
		int i= 0;

		while (i < l) {
			char c= s.charAt(i++);
			if (c == '\\') {
				c= s.charAt(i++);
				if (c == 'u') {
					int v= 0;
					for (int j= 0; j < 4; j++) {
						c= s.charAt(i++);
				        switch (c) {
				        case '0': case '1': case '2': case '3': case '4':
				        case '5': case '6': case '7': case '8': case '9':
							v= (v << 4) + (c-'0');
					     	break;
						case 'a': case 'b': case 'c':
		     			case 'd': case 'e': case 'f':
							v= (v << 4) + 10+(c-'a');
							break;
						case 'A': case 'B': case 'C':
		                case 'D': case 'E': case 'F':
							v= (v << 4) + 10+(c - 'A');
							break;
						default:
		             		throw new IllegalArgumentException(CompareMessages.PropertyCompareViewer_malformedEncoding);
		                }
					}
					buf.append((char)v);
				} else {
					switch (c) {
					case 't':
		    			c= '\t';
						break;
					case 'r':
		    			c= '\r';
						break;
					case 'n':
		    			c= '\n';
						break;
					case 'f':
		    			c= '\f';
						break;
					}
		            buf.append(c);
				}
			} else
				buf.append(c);
		}
		return buf.toString();
	}

	@Override
	protected IDocumentPartitioner getDocumentPartitioner() {
		return new FastPartitioner(new PropertiesFilePartitionScanner(), IPropertiesFilePartitions.PARTITIONS);
	}

	@Override
	protected String getDocumentPartitioning() {
		return IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING;
	}

	private void worked(IProgressMonitor monitor) {
		if (monitor.isCanceled())
			throw new OperationCanceledException();
		monitor.worked(1);
	}

	private IProgressMonitor beginWork(IProgressMonitor monitor) {
		if (monitor == null)
			return new NullProgressMonitor();
		return new SubProgressMonitor(monitor, IProgressMonitor.UNKNOWN);
	}

}

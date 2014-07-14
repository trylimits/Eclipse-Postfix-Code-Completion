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
package org.eclipse.jdt.internal.ui.search;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.formatter.IndentManipulation;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;


public class JavaElementLine {


	private final ITypeRoot fElement;
	private final String fLineContents;
	private final int fLineNumber;
	private final int fLineStartOffset;

	private int fFlags;

	/**
	 * @param element either an ICompilationUnit or an IClassFile
	 * @param lineNumber the line number, starting at 0
	 * @param lineStartOffset the start offset of the line
	 * @throws CoreException thrown when accessing of the buffer failed
	 */
	public JavaElementLine(ITypeRoot element, int lineNumber, int lineStartOffset) throws CoreException {
		fElement= element;
		fFlags= 0;

		IBuffer buffer= element.getBuffer();
		if (buffer == null) {
			throw new CoreException(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, Messages.format( SearchMessages.JavaElementLine_error_nobuffer, BasicElementLabels.getFileName(element))));
		}

		int length= buffer.getLength();
		int i= lineStartOffset;

		char ch= buffer.getChar(i);
		while (lineStartOffset < length && IndentManipulation.isIndentChar(ch)) {
			ch= buffer.getChar(++i);
		}
		fLineStartOffset= i;

		StringBuffer buf= new StringBuffer();

		while (i < length && !IndentManipulation.isLineDelimiterChar(ch)) {
			if (Character.isISOControl(ch)) {
				buf.append(' ');
			} else {
				buf.append(ch);
			}
			i++;
			if (i < length)
				ch= buffer.getChar(i);
		}
		fLineContents= buf.toString();
		fLineNumber= lineNumber;
	}

	public void setFlags(int flags) {
		fFlags= flags;
	}

	public int getFlags() {
		return fFlags;
	}

	public ITypeRoot getJavaElement() {
		return fElement;
	}

	/**
	 * Returns the line number.
	 * 
	 * @return the line number, starting at 0
	 */
	public int getLineNumber() {
		return fLineNumber;
	}

	public String getLineContents() {
		return fLineContents;
	}

	public int getLineStartOffset() {
		return fLineStartOffset;
	}
}

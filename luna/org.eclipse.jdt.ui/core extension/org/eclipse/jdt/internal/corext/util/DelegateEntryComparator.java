/*******************************************************************************
 * Copyright (c) 2009, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Mateusz Wenus <mateusz.wenus@gmail.com> - [override method] generate in declaration order [code generation] - https://bugs.eclipse.org/bugs/show_bug.cgi?id=140971
 *     IBM Corporation - bug fixes
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.util;

import java.util.Comparator;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.dom.IVariableBinding;

import org.eclipse.jdt.internal.corext.codemanipulation.AddDelegateMethodsOperation.DelegateEntry;

/**
 * A comparator which sorts instances of DelegateEntry according to order in which delegate methods
 * should be generated in a type. More formally, if <code>T</code> is a class and <code>d1</code>
 * and <code>d2</code> are two DelegateEntries representing delegate methods which are about to be
 * added to <code>T</code> then according to this comparator <code>d1</code> is less than
 * <code>d2</code> iff one of following holds:
 * <ul>
 * <li><code>d1</code> and <code>d2</code> represent methods delegated to the same field of
 * <code>T</code>, that field's type has a source attachment and method of <code>d1</code> appears
 * in that source before method of <code>d2</code></li>
 * <li><code>d1</code> and <code>d2</code> represent methods delegated to the same field of
 * <code>T</code>, that field's type doesn't have a source attachment and name of method of
 * <code>d1</code> alphabetically precedes name of method of <code>d2</code></li>
 * <li><code>d1</code> and <code>d2</code> represent methods delegated to different fields
 * <code>f1</code> and <code>f2</code>; field <code>f1</code> is declared before <code>f2</code> in
 * type <code>T</code></li>
 * </ul>
 */
public class DelegateEntryComparator implements Comparator<DelegateEntry> {

	public int compare(DelegateEntry firstEntry, DelegateEntry secondEntry) {
		IVariableBinding firstVariable= firstEntry.field;
		IVariableBinding secondVariable= secondEntry.field;

		if (firstVariable.equals(secondVariable)) {
			try {
				IMethod firstMethod= (IMethod)firstEntry.delegateMethod.getJavaElement();
				IMethod secondMethod= (IMethod)secondEntry.delegateMethod.getJavaElement();
				ISourceRange firstSourceRange= firstMethod.getSourceRange();
				ISourceRange secondSourceRange= secondMethod.getSourceRange();
				if (!SourceRange.isAvailable(firstSourceRange) || !SourceRange.isAvailable(secondSourceRange)) {
					return firstMethod.getElementName().compareTo(secondMethod.getElementName());
				} else {
					return firstSourceRange.getOffset() - secondSourceRange.getOffset();
				}
			} catch (JavaModelException e) {
				return 0;
			}
		}

		IField firstField= (IField)firstVariable.getJavaElement();
		IField secondField= (IField)secondVariable.getJavaElement();
		try {
			return firstField.getSourceRange().getOffset() - secondField.getSourceRange().getOffset();
		} catch (JavaModelException e) {
			return 0;
		}
	}
}

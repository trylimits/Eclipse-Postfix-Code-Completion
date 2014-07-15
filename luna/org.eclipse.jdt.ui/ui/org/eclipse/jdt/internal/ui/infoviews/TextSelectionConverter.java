/*******************************************************************************
 * Copyright (c) 2000, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.infoviews;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IEditorInput;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICodeAssist;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.IWorkingCopyManager;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

/**
 * Helper class to convert text selections to Java elements.
 *
 * @since 3.0
 */
class TextSelectionConverter {

	/** Empty result. */
	private static final IJavaElement[] EMPTY_RESULT= new IJavaElement[0];

	/** Prevent instance creation. */
	private TextSelectionConverter() {
	}

	/**
	 * Finds and returns the Java elements for the given editor selection.
	 * 
	 * @param editor the Java editor
	 * @param selection the text selection
	 * @return the Java elements for the given editor selection
	 * @throws JavaModelException if accessing the type root fails
	 */
	public static IJavaElement[] codeResolve(JavaEditor editor, ITextSelection selection) throws JavaModelException {
		return codeResolve(getInput(editor), selection);
	}

	/**
	 * Finds and returns the Java element that contains the text selection in the given editor.
	 * 
	 * @param editor the Java editor
	 * @param selection the text selection
	 * @return the Java elements for the given editor selection
	 * @throws JavaModelException if accessing the type root fails
	 */
	public static IJavaElement getElementAtOffset(JavaEditor editor, ITextSelection selection) throws JavaModelException {
		return getElementAtOffset(getInput(editor), selection);
	}

	//-------------------- Helper methods --------------------

	private static IJavaElement getInput(JavaEditor editor) {
		if (editor == null)
			return null;
		IEditorInput input= editor.getEditorInput();
		if (input instanceof IClassFileEditorInput)
			return ((IClassFileEditorInput)input).getClassFile();
		IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();
		return manager.getWorkingCopy(input);
	}

	private static IJavaElement[] codeResolve(IJavaElement input, ITextSelection selection) throws JavaModelException {
			if (input instanceof ICodeAssist) {
				if (input instanceof ICompilationUnit) {
					ICompilationUnit cunit= (ICompilationUnit)input;
					if (cunit.isWorkingCopy())
						JavaModelUtil.reconcile(cunit);
				}
				IJavaElement[] elements= ((ICodeAssist)input).codeSelect(selection.getOffset(), selection.getLength());
				if (elements != null && elements.length > 0)
					return elements;
			}
			return EMPTY_RESULT;
	}

	private static IJavaElement getElementAtOffset(IJavaElement input, ITextSelection selection) throws JavaModelException {
		if (input instanceof ICompilationUnit) {
			ICompilationUnit cunit= (ICompilationUnit)input;
			if (cunit.isWorkingCopy())
				JavaModelUtil.reconcile(cunit);
			IJavaElement ref= cunit.getElementAt(selection.getOffset());
			if (ref == null)
				return input;
			else
				return ref;
		} else if (input instanceof IClassFile) {
			IJavaElement ref= ((IClassFile)input).getElementAt(selection.getOffset());
			if (ref == null)
				return input;
			else
				return ref;
		}
		return null;
	}
}

/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.javaeditor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.IAction;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import org.eclipse.ui.IEditorInput;

import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.jdt.core.ICodeAssist;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

import org.eclipse.jdt.internal.corext.util.CollectionsUtil;

import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.search.BreakContinueTargetFinder;
import org.eclipse.jdt.internal.ui.search.IOccurrencesFinder.OccurrenceLocation;
import org.eclipse.jdt.internal.ui.text.JavaWordFinder;


/**
 * Java element hyperlink detector.
 *
 * @since 3.1
 */
public class JavaElementHyperlinkDetector extends AbstractHyperlinkDetector {

	/* cache for the last result from codeSelect(..) */
	private static ITypeRoot fLastInput;
	private static long fLastModStamp;
	private static IRegion fLastWordRegion;
	private static IJavaElement[] fLastElements;

	/*
	 * @see org.eclipse.jface.text.hyperlink.IHyperlinkDetector#detectHyperlinks(org.eclipse.jface.text.ITextViewer, org.eclipse.jface.text.IRegion, boolean)
	 */
	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region, boolean canShowMultipleHyperlinks) {
		ITextEditor textEditor= (ITextEditor)getAdapter(ITextEditor.class);
		if (region == null || !(textEditor instanceof JavaEditor))
			return null;

		IAction openAction= textEditor.getAction("OpenEditor"); //$NON-NLS-1$
		if (!(openAction instanceof SelectionDispatchAction))
			return null;

		int offset= region.getOffset();

		ITypeRoot input= EditorUtility.getEditorInputJavaElement(textEditor, false);
		if (input == null)
			return null;

		try {
			IDocumentProvider documentProvider= textEditor.getDocumentProvider();
			IEditorInput editorInput= textEditor.getEditorInput();
			IDocument document= documentProvider.getDocument(editorInput);
			IRegion wordRegion= JavaWordFinder.findWord(document, offset);
			if (wordRegion == null || wordRegion.getLength() == 0)
				return null;

			if (isInheritDoc(document, wordRegion) && getClass() != JavaElementHyperlinkDetector.class)
				return null;

			if (JavaElementHyperlinkDetector.class == getClass() && findBreakOrContinueTarget(input, region) != null)
				return new IHyperlink[] { new JavaElementHyperlink(wordRegion, (SelectionDispatchAction)openAction, null, false) };
			
			IJavaElement[] elements;
			long modStamp= documentProvider.getModificationStamp(editorInput);
			if (input.equals(fLastInput) && modStamp == fLastModStamp && wordRegion.equals(fLastWordRegion)) {
				elements= fLastElements;
			} else {
				elements= ((ICodeAssist) input).codeSelect(wordRegion.getOffset(), wordRegion.getLength());
				elements= selectOpenableElements(elements);
				fLastInput= input;
				fLastModStamp= modStamp;
				fLastWordRegion= wordRegion;
				fLastElements= elements;
			}
			if (elements.length == 0)
				return null;
			
			ArrayList<IHyperlink> links= new ArrayList<IHyperlink>(elements.length);
			for (int i= 0; i < elements.length; i++) {
				addHyperlinks(links, wordRegion, (SelectionDispatchAction)openAction, elements[i], elements.length > 1, (JavaEditor)textEditor);
			}
			if (links.size() == 0)
				return null;
			
			return CollectionsUtil.toArray(links, IHyperlink.class);

		} catch (JavaModelException e) {
			return null;
		}
	}
	
	@Override
	public void dispose() {
		super.dispose();
		fLastElements= null;
		fLastInput= null;
		fLastWordRegion= null;
	}

	/**
	 * Returns whether the word is "inheritDoc".
	 * 
	 * @param document the document
	 * @param wordRegion the word region
	 * @return <code>true</code> iff the word is "inheritDoc"
	 * @since 3.7
	 */
	private static boolean isInheritDoc(IDocument document, IRegion wordRegion) {
		try {
			String word= document.get(wordRegion.getOffset(), wordRegion.getLength());
			return "inheritDoc".equals(word); //$NON-NLS-1$
		} catch (BadLocationException e) {
			return false;
		}
	}

	/**
	 * Creates and adds Java element hyperlinks.
	 * 
	 * @param hyperlinksCollector the list to which hyperlinks should be added
	 * @param wordRegion the region of the link
	 * @param openAction the action to use to open the Java elements
	 * @param element the Java element to open
	 * @param qualify <code>true</code> if the hyperlink text should show a qualified name for
	 *            element
	 * @param editor the active Java editor
	 * 
	 * @since 3.5
	 */
	protected void addHyperlinks(List<IHyperlink> hyperlinksCollector, IRegion wordRegion, SelectionDispatchAction openAction, IJavaElement element, boolean qualify, JavaEditor editor) {
		hyperlinksCollector.add(new JavaElementHyperlink(wordRegion, openAction, element, qualify));
	}


	/**
	 * Selects the openable elements out of the given ones.
	 *
	 * @param elements the elements to filter
	 * @return the openable elements
	 * @since 3.4
	 */
	private IJavaElement[] selectOpenableElements(IJavaElement[] elements) {
		List<IJavaElement> result= new ArrayList<IJavaElement>(elements.length);
		for (int i= 0; i < elements.length; i++) {
			IJavaElement element= elements[i];
			switch (element.getElementType()) {
				case IJavaElement.PACKAGE_DECLARATION:
				case IJavaElement.PACKAGE_FRAGMENT:
				case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				case IJavaElement.JAVA_PROJECT:
				case IJavaElement.JAVA_MODEL:
					break;
				default:
					result.add(element);
					break;
			}
		}
		return result.toArray(new IJavaElement[result.size()]);
	}

	/**
	 * Finds the target for break or continue node.
	 * 
	 * @param input the editor input
	 * @param region the region
	 * @return the break or continue target location or <code>null</code> if none
	 * @since 3.7
	 */
	public static OccurrenceLocation findBreakOrContinueTarget(ITypeRoot input, IRegion region) {
		CompilationUnit astRoot= SharedASTProvider.getAST(input, SharedASTProvider.WAIT_NO, null);
		if (astRoot == null)
			return null;

		ASTNode node= NodeFinder.perform(astRoot, region.getOffset(), region.getLength());
		ASTNode breakOrContinueNode= null;
		boolean labelSelected= false;
		if (node instanceof SimpleName) {
			SimpleName simpleName= (SimpleName) node;
			StructuralPropertyDescriptor location= simpleName.getLocationInParent();
			if (location == ContinueStatement.LABEL_PROPERTY || location == BreakStatement.LABEL_PROPERTY) {
				breakOrContinueNode= simpleName.getParent();
				labelSelected= true;
			}
		} else if (node instanceof ContinueStatement || node instanceof BreakStatement)
			breakOrContinueNode= node;

		if (breakOrContinueNode == null)
			return null;

		BreakContinueTargetFinder finder= new BreakContinueTargetFinder();
		if (finder.initialize(astRoot, breakOrContinueNode) == null) {
			OccurrenceLocation[] locations= finder.getOccurrences();
			if (locations != null) {
				if (breakOrContinueNode instanceof BreakStatement && !labelSelected)
					return locations[locations.length - 1]; // points to the end of target statement
				return locations[0]; // points to the beginning of target statement
			}
		}
		return null;
	}	
}

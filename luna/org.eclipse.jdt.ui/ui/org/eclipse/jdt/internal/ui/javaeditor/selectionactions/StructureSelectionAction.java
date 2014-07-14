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
package org.eclipse.jdt.internal.ui.javaeditor.selectionactions;

import java.util.List;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Selection;
import org.eclipse.jdt.internal.corext.dom.SelectionAnalyzer;

import org.eclipse.jdt.ui.SharedASTProvider;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

public abstract class StructureSelectionAction extends Action {

	public static final String NEXT= "SelectNextElement"; //$NON-NLS-1$
	public static final String PREVIOUS= "SelectPreviousElement"; //$NON-NLS-1$
	public static final String ENCLOSING= "SelectEnclosingElement"; //$NON-NLS-1$
	public static final String HISTORY= "RestoreLastSelection"; //$NON-NLS-1$

	private JavaEditor fEditor;
	private SelectionHistory fSelectionHistory;

	protected StructureSelectionAction(String text, JavaEditor editor, SelectionHistory history) {
		super(text);
		Assert.isNotNull(editor);
		Assert.isNotNull(history);
		fEditor= editor;
		fSelectionHistory= history;
	}

	/*
	 * This constructor is for testing purpose only.
	 */
	protected StructureSelectionAction() {
		super(""); //$NON-NLS-1$
	}

	/*
	 * Method declared in IAction.
	 */
	@Override
	public final  void run() {
		IJavaElement inputElement= EditorUtility.getEditorInputJavaElement(fEditor, false);
		if (!(inputElement instanceof ITypeRoot && inputElement.exists()))
			return;

		ITypeRoot typeRoot= (ITypeRoot) inputElement;
		ISourceRange sourceRange;
		try {
			sourceRange= typeRoot.getSourceRange();
			if (sourceRange == null || sourceRange.getLength() == 0) {
				MessageDialog.openInformation(fEditor.getEditorSite().getShell(),
					SelectionActionMessages.StructureSelect_error_title,
					SelectionActionMessages.StructureSelect_error_message);
				return;
			}
		} catch (JavaModelException e) {
		}
		ITextSelection selection= getTextSelection();
		ISourceRange newRange= getNewSelectionRange(createSourceRange(selection), typeRoot);
		// Check if new selection differs from current selection
		if (selection.getOffset() == newRange.getOffset() && selection.getLength() == newRange.getLength())
			return;
		fSelectionHistory.remember(new SourceRange(selection.getOffset(), selection.getLength()));
		try {
			fSelectionHistory.ignoreSelectionChanges();
			fEditor.selectAndReveal(newRange.getOffset(), newRange.getLength());
		} finally {
			fSelectionHistory.listenToSelectionChanges();
		}
	}

	public final ISourceRange getNewSelectionRange(ISourceRange oldSourceRange, ITypeRoot typeRoot) {
		try{
			CompilationUnit root= getAST(typeRoot);
			if (root == null)
				return oldSourceRange;
			Selection selection= Selection.createFromStartLength(oldSourceRange.getOffset(), oldSourceRange.getLength());
			SelectionAnalyzer selAnalyzer= new SelectionAnalyzer(selection, true);
			root.accept(selAnalyzer);
			return internalGetNewSelectionRange(oldSourceRange, typeRoot, selAnalyzer);
	 	}	catch (JavaModelException e){
	 		JavaPlugin.log(e); //dialog would be too heavy here
	 		return new SourceRange(oldSourceRange.getOffset(), oldSourceRange.getLength());
	 	}
	}

	/**
	 * Subclasses determine the actual new selection.
	 * 
	 * @param oldSourceRange the selected range
	 * @param sr the current type root
	 * @param selAnalyzer the selection analyzer
	 * @return return the new selection range
	 * @throws JavaModelException if getting the source range fails
	 */
	abstract ISourceRange internalGetNewSelectionRange(ISourceRange oldSourceRange, ISourceReference sr, SelectionAnalyzer selAnalyzer) throws JavaModelException;

	protected final ITextSelection getTextSelection() {
		return (ITextSelection)fEditor.getSelectionProvider().getSelection();
	}

	// -- helper methods for subclasses to fit a node range into the source range

	protected static ISourceRange getLastCoveringNodeRange(ISourceRange oldSourceRange, ISourceReference sr, SelectionAnalyzer selAnalyzer) throws JavaModelException {
		if (selAnalyzer.getLastCoveringNode() == null)
			return oldSourceRange;
		else
			return getSelectedNodeSourceRange(sr, selAnalyzer.getLastCoveringNode());
	}

	protected static ISourceRange getSelectedNodeSourceRange(ISourceReference sr, ASTNode nodeToSelect) throws JavaModelException {
		int offset= nodeToSelect.getStartPosition();
		int end= Math.min(sr.getSourceRange().getLength(), nodeToSelect.getStartPosition() + nodeToSelect.getLength() - 1);
		return createSourceRange(offset, end);
	}

	//-- private helper methods

	private static ISourceRange createSourceRange(ITextSelection ts){
		return new SourceRange(ts.getOffset(), ts.getLength());
	}

	private static CompilationUnit getAST(ITypeRoot sr) {
		return SharedASTProvider.getAST(sr, SharedASTProvider.WAIT_YES, null);
	}

	//-- helper methods for this class and subclasses

	static ISourceRange createSourceRange(int offset, int end){
		int length= end - offset + 1;
		if (length == 0) //to allow 0-length selection
			length= 1;
		return new SourceRange(Math.max(0, offset), length);
	}

	static ASTNode[] getSiblingNodes(ASTNode node) {
		ASTNode parent= node.getParent();
		StructuralPropertyDescriptor locationInParent= node.getLocationInParent();
		if (locationInParent.isChildListProperty()) {
			List<? extends ASTNode> siblings= ASTNodes.getChildListProperty(parent, (ChildListPropertyDescriptor) locationInParent);
			return siblings.toArray(new ASTNode[siblings.size()]);
		}
		return null;
	}

	static int findIndex(Object[] array, Object o){
		for (int i= 0; i < array.length; i++) {
			Object object= array[i];
			if (object == o)
				return i;
		}
		return -1;
	}

}

/*******************************************************************************
 * Copyright (c) 2005, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Paul Fullbright <paul.fullbright@oracle.com> - content assist category enablement - http://bugs.eclipse.org/345213
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.javaeditor;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.source.ISourceViewer;

import org.eclipse.ui.IEditorPart;

import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.IUpdate;

import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.ui.text.IJavaPartitions;

import org.eclipse.jdt.internal.ui.text.java.CompletionProposalCategory;
import org.eclipse.jdt.internal.ui.text.java.CompletionProposalComputerRegistry;


/**
 * Action to run content assist on a specific proposal category.
 *
 * @since 3.2
 */
final class SpecificContentAssistAction extends Action implements IUpdate {
	/**
	 * The category represented by this action.
	 */
	private final CompletionProposalCategory fCategory;
	/**
	 * The content assist executor.
	 */
	private final SpecificContentAssistExecutor fExecutor= new SpecificContentAssistExecutor(CompletionProposalComputerRegistry.getDefault());
	/**
	 * The editor.
	 */
	private JavaEditor fEditor;

	/**
	 * Creates a new action for a certain proposal category.
	 * 
	 * @param category the completion proposal category
	 */
	public SpecificContentAssistAction(CompletionProposalCategory category) {
		fCategory= category;
		setText(category.getName());
		setImageDescriptor(category.getImageDescriptor());
		setActionDefinitionId("org.eclipse.jdt.ui.specific_content_assist.command"); //$NON-NLS-1$
	}

	/*
	 * @see org.eclipse.jface.action.Action#run()
	 */
	@Override
	public void run() {
		ITextEditor editor= getActiveEditor();
		if (editor == null)
			return;

		fExecutor.invokeContentAssist(editor, fCategory.getId());

		return;
	}

	private ITextEditor getActiveEditor() {
		return fEditor;
	}

	/**
	 * Sets the active editor part.
	 *
	 * @param part the editor, possibly <code>null</code>
	 */
	public void setActiveEditor(IEditorPart part) {
		JavaEditor editor;
		if (part instanceof JavaEditor)
			editor= (JavaEditor) part;
		else
			editor= null;
		fEditor= editor;
		setEnabled(computeEnablement(fEditor));
	}

	private boolean computeEnablement(ITextEditor editor) {
		if (editor == null)
			return false;
		
		ITextOperationTarget target= (ITextOperationTarget) editor.getAdapter(ITextOperationTarget.class);
		if (target == null || ! target.canDoOperation(ISourceViewer.CONTENTASSIST_PROPOSALS))
			return false;
		
		IJavaProject javaProject = EditorUtility.getJavaProject(editor.getEditorInput());
		if (! fCategory.matches(javaProject))
			return false;
		
		ISelection selection= editor.getSelectionProvider().getSelection();
		return isValidSelection(selection);
	}

    /**
	 * Computes the partition type at the selection start and checks whether the proposal category
	 * has any computers for this partition.
	 *
	 * @param selection the selection
	 * @return <code>true</code> if there are any computers for the selection
	 */
    private boolean isValidSelection(ISelection selection) {
    	if (!(selection instanceof ITextSelection))
    		return false;
    	int offset= ((ITextSelection) selection).getOffset();

    	IDocument document= getDocument();
    	if (document == null)
    		return false;

    	String contentType;
    	try {
	        contentType= TextUtilities.getContentType(document, IJavaPartitions.JAVA_PARTITIONING, offset, true);
        } catch (BadLocationException x) {
        	return false;
        }

        return fCategory.hasComputers(contentType);
    }

	private IDocument getDocument() {
		Assert.isTrue(fEditor != null);
	    IDocumentProvider provider= fEditor.getDocumentProvider();
	    if (provider == null)
	    	return null;

		IDocument document= provider.getDocument(fEditor.getEditorInput());
	    return document;
    }

	/*
     * @see org.eclipse.ui.texteditor.IUpdate#update()
     */
    public void update() {
    	setEnabled(computeEnablement(fEditor));
    }
}

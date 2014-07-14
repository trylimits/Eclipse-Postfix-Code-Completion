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

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFileState;

import org.eclipse.core.filebuffers.ITextFileBuffer;

import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.RewriteSessionEditProcessor;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;

import org.eclipse.ui.texteditor.IDocumentProvider;

import org.eclipse.compare.HistoryItem;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.ResourceNode;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.eclipse.jdt.internal.corext.util.Strings;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

/**
 * Base class for the "Replace with local history"
 * and "Add from local history" actions.
 */
abstract class JavaHistoryActionImpl /* extends Action implements IActionDelegate*/ {

	private boolean fModifiesFile;
	private ISelection fSelection;

	JavaHistoryActionImpl(boolean modifiesFile) {
		fModifiesFile= modifiesFile;
	}

	ISelection getSelection() {
		return fSelection;
	}

	final IFile getFile(Object input) {
		return JavaElementHistoryPageSource.getInstance().getFile(input);
	}

	final ITypedElement[] buildEditions(ITypedElement target, IFile file) {

		// setup array of editions
		IFileState[] states= null;
		// add available editions
		try {
			states= file.getHistory(null);
		} catch (CoreException ex) {
			JavaPlugin.log(ex);
		}

		int count= 1;
		if (states != null)
			count+= states.length;

		ITypedElement[] editions= new ITypedElement[count];
		editions[0]= new ResourceNode(file);
		if (states != null)
			for (int i= 0; i < states.length; i++)
				editions[i+1]= new HistoryItem(target, states[i]);
		return editions;
	}

	final Shell getShell() {
		if (fEditor != null)
			return fEditor.getEditorSite().getShell();
		return JavaPlugin.getActiveWorkbenchShell();
	}

	/**
	 * Tries to find the given element in a working copy.
	 */
	final IJavaElement getWorkingCopy(IJavaElement input) {
		// TODO: With new working copy story: original == working copy.
		// Note that the previous code could result in a reconcile as side effect. Should check if that
		// is still required.
		return input;
	}

	final ASTNode getBodyContainer(CompilationUnit root, IMember parent) throws JavaModelException {
		ISourceRange sourceRange= parent.getNameRange();
		ASTNode parentNode= NodeFinder.perform(root, sourceRange);
		do {
			if (parentNode instanceof TypeDeclaration || parentNode instanceof EnumDeclaration || parentNode instanceof AnnotationTypeDeclaration)
				return parentNode;
			parentNode= parentNode.getParent();
		} while (parentNode != null);
		return null;
	}

	/**
	 * Returns true if the given file is open in an editor.
	 */
	final boolean beingEdited(IFile file) {
		IDocumentProvider dp= JavaPlugin.getDefault().getCompilationUnitDocumentProvider();
		FileEditorInput input= new FileEditorInput(file);
		return dp.getDocument(input) != null;
	}

	/**
	 * Returns an IMember or null.
	 */
	final IMember getEditionElement(ISelection selection) {

		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ss= (IStructuredSelection) selection;
			if (ss.size() == 1) {
				Object o= ss.getFirstElement();
				if (o instanceof IMember) {
					IMember m= (IMember) o;
					if (m.exists() && !m.isBinary() && JavaStructureCreator.hasEdition(m))
						return m;
				}
			}
		}
		return null;
	}

	final boolean isEnabled(IFile file) {
		if (file == null || ! file.exists())
			return false;
		if (fModifiesFile) {
			// without validate/edit we would do this:
			//    return !file.isReadOnly();
			// with validate/edit we have to return true
			return true;
		}
		return true;
	}

	boolean isEnabled(ISelection selection) {
		IMember m= getEditionElement(selection);
		if (m == null)
			return false;
		IFile file= getFile(m);
		if (!isEnabled(file))
			return false;
		return true;
	}

	void applyChanges(ASTRewrite rewriter, final IDocument document, final ITextFileBuffer textFileBuffer, Shell shell, boolean inEditor, Map<String, String> options)
							throws CoreException, InvocationTargetException, InterruptedException {


		MultiTextEdit edit= new MultiTextEdit();
		try {
			TextEdit res= rewriter.rewriteAST(document, options);
			edit.addChildren(res.removeChildren());
		} catch (IllegalArgumentException e) {
			JavaPlugin.log(e);
		}

		try {
			new RewriteSessionEditProcessor(document, edit, TextEdit.UPDATE_REGIONS).performEdits();
		} catch (BadLocationException e) {
			JavaPlugin.log(e);
		}

		IRunnableWithProgress r= new IRunnableWithProgress() {
			public void run(IProgressMonitor pm) throws InvocationTargetException {
				try {
					textFileBuffer.commit(pm, false);
				} catch (CoreException ex) {
					throw new InvocationTargetException(ex);
				}
			}
		};

		if (inEditor) {
			// we don't show progress
			r.run(new NullProgressMonitor());
		} else {
			PlatformUI.getWorkbench().getProgressService().run(true, false, r);
		}
	}

	static String trimTextBlock(String content, String delimiter, IJavaProject currentProject) {
		if (content != null) {
			String[] lines= Strings.convertIntoLines(content);
			if (lines != null) {
				Strings.trimIndentation(lines, currentProject);
				return Strings.concatenate(lines, delimiter);
			}
		}
		return null;
	}

	final JavaEditor getEditor(IFile file) {
		FileEditorInput fei= new FileEditorInput(file);
		IWorkbench workbench= JavaPlugin.getDefault().getWorkbench();
		IWorkbenchWindow[] windows= workbench.getWorkbenchWindows();
		for (int i= 0; i < windows.length; i++) {
			IWorkbenchPage[] pages= windows[i].getPages();
			for (int x= 0; x < pages.length; x++) {
				IEditorPart[] editors= pages[x].getDirtyEditors();
				for (int z= 0; z < editors.length; z++) {
					IEditorPart ep= editors[z];
					if (ep instanceof JavaEditor) {
						JavaEditor je= (JavaEditor) ep;
						if (fei.equals(je.getEditorInput()))
							return (JavaEditor) ep;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Executes this action with the given selection.
	 */
	public abstract void run(ISelection selection);

	//---- Action

	private JavaEditor fEditor;
	private String fTitle;
	private String fMessage;

	void init(JavaEditor editor, String title, String message) {
		fEditor= editor;
		fTitle= title;
		fMessage= message;
	}

	final JavaEditor getEditor() {
		return fEditor;
	}

	public final void runFromEditor(IAction uiProxy) {

		// this run is called from Editor
		IJavaElement element= null;
		try {
			element= SelectionConverter.getElementAtOffset(fEditor);
		} catch (JavaModelException e) {
			// ignored
		}

		fSelection= element != null
						? new StructuredSelection(element)
						: StructuredSelection.EMPTY;
		boolean isEnabled= isEnabled(fSelection);
		uiProxy.setEnabled(isEnabled);

		if (!isEnabled) {
			MessageDialog.openInformation(getShell(), fTitle, fMessage);
			return;
		}
		run(fSelection);
	}

	boolean checkEnabled() {
		ICompilationUnit unit= SelectionConverter.getInputAsCompilationUnit(fEditor);
		IFile file= getFile(unit);
		return isEnabled(file);
	}

	public final void update(IAction uiProxy) {
		uiProxy.setEnabled(checkEnabled());
	}

 	//---- IActionDelegate

	public final void selectionChanged(IAction uiProxy, ISelection selection) {
		fSelection= selection;
		uiProxy.setEnabled(isEnabled(selection));
	}

	public final void run(IAction action) {
		run(fSelection);
	}

	static CompilationUnit parsePartialCompilationUnit(ICompilationUnit unit) {

		if (unit == null) {
			throw new IllegalArgumentException();
		}
		try {
			ASTParser c= ASTParser.newParser(ASTProvider.SHARED_AST_LEVEL);
			c.setSource(unit);
			c.setFocalPosition(0);
			c.setResolveBindings(false);
			c.setWorkingCopyOwner(null);
			ASTNode result= c.createAST(null);
			return (CompilationUnit) result;
		} catch (IllegalStateException e) {
			// convert ASTParser's complaints into old form
			throw new IllegalArgumentException();
		}
	}
}

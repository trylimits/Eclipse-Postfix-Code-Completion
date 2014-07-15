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
package org.eclipse.jdt.ui.actions;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.ui.texteditor.IEditorStatusLine;

import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;
import org.eclipse.jdt.internal.ui.search.ExceptionOccurrencesFinder;
import org.eclipse.jdt.internal.ui.search.FindOccurrencesEngine;

/**
 * Action to find all originators of a exception (e.g. method invocations,
 * class casts, ...) for a given exception.
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 3.1
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class FindExceptionOccurrencesAction extends SelectionDispatchAction {

	private JavaEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 *
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public FindExceptionOccurrencesAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(getEditorInput(editor) != null);
	}

	/**
	 * Creates a new <code>FindExceptionOccurrencesAction</code>. The action
	 * requires that the selection provided by the site's selection provider is of type
	 * <code>IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public FindExceptionOccurrencesAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.FindExceptionOccurrences_text);
		setToolTipText(ActionMessages.FindExceptionOccurrences_toolTip);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.FIND_EXCEPTION_OCCURRENCES);
	}

	//---- Text Selection ----------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(true);
	}

	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 * @param selection the java text selection
	 *
	 * @noreference This method is not intended to be referenced by clients.
	 */
	@Override
	public void selectionChanged(JavaTextSelection selection) {
		CompilationUnit astRoot= selection.resolvePartialAstAtOffset();
		setEnabled(astRoot != null && new ExceptionOccurrencesFinder().initialize(astRoot, selection.getOffset(), selection.getLength()) == null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		setEnabled(false);
	}

	/* (non-JavaDoc)
	 * Method declared in SelectionDispatchAction.
	 */
	@Override
	public final void run(ITextSelection ts) {
		ITypeRoot input= getEditorInput(fEditor);
		if (!ActionUtil.isProcessable(getShell(), input))
			return;
		FindOccurrencesEngine engine= FindOccurrencesEngine.create(new ExceptionOccurrencesFinder());
		try {
			String result= engine.run(input, ts.getOffset(), ts.getLength());
			if (result != null)
				showMessage(getShell(), fEditor, result);
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		}
	}

	private static ITypeRoot getEditorInput(JavaEditor editor) {
		return JavaUI.getEditorInputTypeRoot(editor.getEditorInput());
	}

	private static void showMessage(Shell shell, JavaEditor editor, String msg) {
		IEditorStatusLine statusLine= (IEditorStatusLine) editor.getAdapter(IEditorStatusLine.class);
		if (statusLine != null)
			statusLine.setMessage(true, msg, null);
		shell.getDisplay().beep();
	}
}

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

import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.Page;

import org.eclipse.ui.texteditor.IEditorStatusLine;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;
import org.eclipse.jdt.internal.ui.search.FindOccurrencesEngine;
import org.eclipse.jdt.internal.ui.search.OccurrencesFinder;
import org.eclipse.jdt.internal.ui.search.SearchMessages;

/**
 * Action to find all occurrences of a compilation unit member (e.g.
 * fields, methods, types, and local variables) in a file.
 * <p>
 * Action is applicable to selections containing elements of type
 * <tt>IMember</tt>.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.1
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class FindOccurrencesInFileAction extends SelectionDispatchAction {

	private JavaEditor fEditor;
	private IActionBars fActionBars;

	/**
	 * Creates a new <code>FindOccurrencesInFileAction</code>. The action requires
	 * that the selection provided by the view part's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param part the part providing context information for this action
	 */
	public FindOccurrencesInFileAction(IViewPart part) {
		this(part.getSite());
	}

	/**
	 * Creates a new <code>FindOccurrencesInFileAction</code>. The action requires
	 * that the selection provided by the page's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param page the page providing context information for this action
	 */
	public FindOccurrencesInFileAction(Page page) {
		this(page.getSite());
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public FindOccurrencesInFileAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(getEditorInput(editor) != null);
	}

	/**
	 * Creates a new <code>FindOccurrencesInFileAction</code>. The action
	 * requires that the selection provided by the site's selection provider is of type
	 * <code>IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 * @since 3.1
	 */
	public FindOccurrencesInFileAction(IWorkbenchSite site) {
		super(site);

		if (site instanceof IViewSite)
			fActionBars= ((IViewSite)site).getActionBars();
		else if (site instanceof IEditorSite)
			fActionBars= ((IEditorSite)site).getActionBars();
		else if (site instanceof IPageSite)
			fActionBars= ((IPageSite)site).getActionBars();

		setText(SearchMessages.Search_FindOccurrencesInFile_label);
		setToolTipText(SearchMessages.Search_FindOccurrencesInFile_tooltip);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.FIND_OCCURRENCES_IN_FILE_ACTION);
	}

	//---- Structured Selection -------------------------------------------------------------

	/* (non-JavaDoc)
	 * Method declared in SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		setEnabled(getMember(selection) != null);
	}

	/* (non-JavaDoc)
	 * Method declared in SelectionDispatchAction.
	 */
	private IMember getMember(IStructuredSelection selection) {
		if (selection.size() != 1)
			return null;
		Object o= selection.getFirstElement();
		if (o instanceof IMember) {
			IMember member= (IMember)o;
			try {
				if (member.getNameRange() == null)
					return null;
			} catch (JavaModelException ex) {
				return null;
			}

			IClassFile file= member.getClassFile();
			if (file != null) {
				try {
					if (file.getSourceRange() != null)
						return member;
				} catch (JavaModelException e) {
					return null;
				}
			}
			return member;
		}
		return null;
	}

	@Override
	public void run(IStructuredSelection selection) {
		IMember member= getMember(selection);
		if (!ActionUtil.isProcessable(getShell(), member))
			return;
		FindOccurrencesEngine engine= FindOccurrencesEngine.create(new OccurrencesFinder());
		try {
			ISourceRange range= member.getNameRange();
			String result= engine.run(member.getTypeRoot(), range.getOffset(), range.getLength());
			if (result != null)
				showMessage(getShell(), fActionBars, result);
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		}
	}

	private static void showMessage(Shell shell, IActionBars actionBars, String msg) {
		if (actionBars != null) {
			IStatusLineManager statusLine= actionBars.getStatusLineManager();
			if (statusLine != null)
				statusLine.setMessage(msg);
		}
		shell.getDisplay().beep();
	}

	//---- Text Selection ----------------------------------------------------------------------

	/* (non-JavaDoc)
	 * Method declared in SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(true);
	}

	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 * 
	 * @param selection the Java text selection
	 * @noreference This method is not intended to be referenced by clients.
	 */
	@Override
	public void selectionChanged(JavaTextSelection selection) {
		CompilationUnit astRoot= selection.resolvePartialAstAtOffset();
		setEnabled(astRoot != null && new OccurrencesFinder().initialize(astRoot, selection.getOffset(), selection.getLength()) == null);
	}

	/* (non-JavaDoc)
	 * Method declared in SelectionDispatchAction.
	 */
	@Override
	public final void run(ITextSelection ts) {
		ITypeRoot input= getEditorInput(fEditor);
		if (!ActionUtil.isProcessable(getShell(), input))
			return;
		OccurrencesFinder finder= new OccurrencesFinder();
		FindOccurrencesEngine engine= FindOccurrencesEngine.create(finder);
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

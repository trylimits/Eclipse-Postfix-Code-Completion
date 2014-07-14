/*******************************************************************************
 * Copyright (c) 2009, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.actions;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Region;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaElementImplementationHyperlink;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;


/**
 * The action allows to open the implementation for a method in its hierarchy.
 * <p>
 * The action is applicable to selections containing elements of type <code>
 * IMethod</code>.
 * </p>
 * 
 * @since 3.6
 * @noextend This class is not intended to be subclassed by clients.
 */
public class OpenImplementationAction extends SelectionDispatchAction {

	/**
	 * The Java editor.
	 */
	private JavaEditor fEditor;


	/**
	 * Creates an <code>OpenImplementationAction</code>.
	 * 
	 * @param site the workbench site
	 */
	protected OpenImplementationAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.OpenImplementationAction_label);
		setDescription(ActionMessages.OpenImplementationAction_description);
		setToolTipText(ActionMessages.OpenImplementationAction_tooltip);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.OPEN_IMPLEMENTATION_ACTION);
	}

	/**
	 * Creates an <code>OpenImplementationAction</code>. Note: This constructor is for internal use
	 * only. Clients should not call this constructor.
	 * 
	 * @param editor the editor
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public OpenImplementationAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor) && fEditor.getSelectionProvider().getSelection() instanceof ITextSelection);
	}

	/*
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#selectionChanged(org.eclipse.jface.text.ITextSelection)
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
	}

	/*
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#selectionChanged(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		setEnabled(false);
	}

	/*
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#run(org.eclipse.jface.text.ITextSelection)
	 */
	@Override
	public void run(ITextSelection selection) {
		if (!ActionUtil.isProcessable(fEditor))
			return;
		IJavaElement element= null;
		try {
			IJavaElement[] elements= SelectionConverter.codeResolveForked(fEditor, true);
			if (elements.length == 1)
				element= elements[0];
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.OpenAction_error_message);
			return;
		} catch (InterruptedException e) {
			return;
		}

		if (element == null || !(element instanceof IMethod)) {
			MessageDialog.openInformation(getShell(), getDialogTitle(), ActionMessages.OpenImplementationAction_not_applicable);
			return;
		}
		if (!ActionUtil.isProcessable(getShell(), element))
			return;
		SelectionDispatchAction openAction= (SelectionDispatchAction)fEditor.getAction("OpenEditor"); //$NON-NLS-1$
		if (openAction == null)
			return;

		IRegion region= new Region(selection.getOffset(), 0);
		JavaElementImplementationHyperlink.openImplementations(fEditor, region, (IMethod)element, openAction);

	}

	/**
	 * Returns the dialog title.
	 * 
	 * @return the dialog title
	 */
	private String getDialogTitle() {
		return ActionMessages.OpenImplementationAction_error_title;
	}
}

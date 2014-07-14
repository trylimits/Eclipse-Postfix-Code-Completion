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

import java.lang.reflect.InvocationTargetException;
import java.net.URL;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.OpenBrowserUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * This action opens the selected element's Javadoc in a browser as defined by the preferences.
 * <p>
 * The action is applicable to selections containing elements of type <code>IJavaElement</code>.
 * 
 * @since 3.6
 * @noextend This class is not intended to be subclassed by clients.
 */
public class OpenAttachedJavadocAction extends SelectionDispatchAction {

	private JavaEditor fEditor;

	private Shell fShell;

	/**
	 * Creates a new <code>OpenAttachedJavadocAction</code>. The action requires that the selection
	 * provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>
	 * .
	 * 
	 * @param site the site providing additional context information for this action
	 */
	public OpenAttachedJavadocAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.OpenAttachedJavadocAction_label);
		setDescription(ActionMessages.OpenAttachedJavadocAction_description);
		setToolTipText(ActionMessages.OpenAttachedJavadocAction_tooltip);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.OPEN_ATTACHED_JAVADOC_ACTION);
	}


	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * 
	 * @param editor the Java editor
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public OpenAttachedJavadocAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor));
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		setEnabled(canEnableFor(selection));
	}

	/**
	 * Tells whether this action can be enabled for the given selection.
	 * 
	 * @param selection the structured selection.
	 * @return <code>true</code> if the action can be enabled, <code>false</code> otherwise
	 */
	protected boolean canEnableFor(IStructuredSelection selection) {
		if (selection.size() != 1)
			return false;
		return selection.getFirstElement() instanceof IJavaElement;
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(ITextSelection selection) {
		IJavaElement element= SelectionConverter.getInput(fEditor);
		if (!ActionUtil.isProcessable(getShell(), element))
			return;

		try {
			IJavaElement[] elements= SelectionConverter.codeResolveOrInputForked(fEditor);
			if (elements == null || elements.length == 0)
				return;
			IJavaElement candidate= elements[0];
			if (elements.length > 1) {
				candidate= SelectionConverter.selectJavaElement(elements, getShell(), getDialogTitle(), ActionMessages.OpenAttachedJavadocAction_select_element);
			}
			if (candidate != null) {
				run(candidate);
			}
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.OpenAttachedJavadocAction_code_resolve_failed);
		} catch (InterruptedException e) {
			// cancelled
		}
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(IStructuredSelection selection) {
		if (!canEnableFor(selection))
			return;
		IJavaElement element= (IJavaElement)selection.getFirstElement();
		if (!ActionUtil.isProcessable(getShell(), element))
			return;
		run(element);
	}

	/**
	 * Executes this actions with the given Java element.
	 * 
	 * @param element the Java element
	 */
	protected void run(IJavaElement element) {
		if (element == null)
			return;
		Shell shell= getShell();
		try {
			String labelName= JavaElementLabels.getElementLabel(element, JavaElementLabels.ALL_DEFAULT);

			URL baseURL= JavaUI.getJavadocBaseLocation(element);
			if (baseURL == null) {
				IPackageFragmentRoot root= JavaModelUtil.getPackageFragmentRoot(element);
				if (root != null && root.getKind() == IPackageFragmentRoot.K_BINARY) {
					String message= ActionMessages.OpenAttachedJavadocAction_libraries_no_location;
					showMessage(shell, Messages.format(message, new String[] { labelName, JavaElementLabels.getElementLabel(root, JavaElementLabels.ALL_DEFAULT) }), false);
				} else {
					IJavaElement annotatedElement= element.getJavaProject();
					String message= ActionMessages.OpenAttachedJavadocAction_source_no_location;
					showMessage(shell, Messages.format(message, new String[] { labelName, JavaElementLabels.getElementLabel(annotatedElement, JavaElementLabels.ALL_DEFAULT) }), false);
				}
				return;
			}
			URL url= JavaUI.getJavadocLocation(element, true);
			if (url != null)
				open(url);
		} catch (CoreException e) {
			JavaPlugin.log(e);
			showMessage(shell, ActionMessages.OpenAttachedJavadocAction_opening_failed, true);
		}
	}

	/**
	 * Opens the given URL in the browser.
	 * 
	 * @param url the URL
	 */
	protected void open(URL url) {
		if (forceExternalBrowser())
			OpenBrowserUtil.openExternal(url, getShell().getDisplay());
		else
			OpenBrowserUtil.open(url, getShell().getDisplay());
	}

	/**
	 * Tells whether to use an external browser or the one chosen by the preferences.
	 * 
	 * @return <code>true</code> if it should always use the external browser, <code>false</code> to
	 *         use the browser chosen in the preferences
	 * @since 3.6
	 */
	boolean forceExternalBrowser() {
		return false;
	}

	private static void showMessage(final Shell shell, final String message, final boolean isError) {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				if (isError) {
					MessageDialog.openError(shell, getTitle(), message);
				} else {
					MessageDialog.openInformation(shell, getTitle(), message);
				}
			}
		});
	}

	private static String getTitle() {
		return ActionMessages.OpenAttachedJavadocAction_dialog_title;
	}

	/**
	 * Note: this method is for internal use only. Clients should not call this method.
	 *
	 * @return the dialog default title
	 *
	 * @noreference This method is not intended to be referenced by clients.
	 */
	protected String getDialogTitle() {
		return getTitle();
	}

	/**
	 * Returns the shell provided by the site owning this action.
	 * 
	 * @return the site's shell
	 */
	@Override
	public Shell getShell() {
		if (fShell != null)
			return fShell;
		return super.getShell();
	}

}

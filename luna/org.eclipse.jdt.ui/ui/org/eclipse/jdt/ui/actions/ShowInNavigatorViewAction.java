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

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ISetSelectionTarget;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;


/**
 * Reveals the selected element in the resource navigator view.
 * <p>
 * Action is applicable to structured selections containing Java element or resources.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 * 
 * @noextend This class is not intended to be subclassed by clients.
 * @deprecated As of 3.5, got replaced by generic Navigate &gt; Show In &gt;
 */
public class ShowInNavigatorViewAction extends SelectionDispatchAction {

	private JavaEditor fEditor;

	/**
	 * Creates a new <code>ShowInNavigatorViewAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type
	 * <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public ShowInNavigatorViewAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.ShowInNavigatorView_label);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.SHOW_IN_NAVIGATOR_VIEW_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public ShowInNavigatorViewAction(JavaEditor editor) {
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
		setEnabled(getResource(selection) != null);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(ITextSelection selection) {
		IJavaElement input= SelectionConverter.getInput(fEditor);
		if (!ActionUtil.isProcessable(getShell(), input))
			return;


		try {
			IJavaElement[] elements= SelectionConverter.codeResolveOrInputForked(fEditor);
			if (elements == null || elements.length == 0)
				return;

			IJavaElement candidate= elements[0];
			if (elements.length > 1) {
				candidate= SelectionConverter.selectJavaElement(elements, getShell(), getDialogTitle(), ActionMessages.ShowInNavigatorView_dialog_message);
			}
			if (candidate != null) {
				run(getResource(candidate));
			}
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getDialogTitle(), ActionMessages.SelectionConverter_codeResolve_failed);
		} catch (InterruptedException e) {
			// cancelled
		}
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(IStructuredSelection selection) {
		run(getResource(selection));
	}

	/*
	 * No Javadoc. The method should be internal but can't be changed since
	 * we shipped it with a public visibility
	 */
	public void run(IResource resource) {
		if (resource == null)
			return;
		try {
			IWorkbenchPage page= getSite().getWorkbenchWindow().getActivePage();
			IViewPart view= page.showView(JavaPlugin.ID_RES_NAV);
			if (view instanceof ISetSelectionTarget) {
				ISelection selection= new StructuredSelection(resource);
				((ISetSelectionTarget)view).selectReveal(selection);
			}
		} catch(PartInitException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.ShowInNavigatorView_error_activation_failed);
		}
	}

	private IResource getResource(IStructuredSelection selection) {
		if (selection.size() != 1)
			return null;
		Object element= selection.getFirstElement();
		if (element instanceof IResource)
			return (IResource)element;
		if (element instanceof IJavaElement)
			return getResource((IJavaElement)element);
		return null;
	}

	private IResource getResource(IJavaElement element) {
		if (element == null)
			return null;

		element= (IJavaElement) element.getOpenable();
		if (element instanceof ICompilationUnit) {
			element= ((ICompilationUnit) element).getPrimary();
		}
		return element.getResource();
	}

	private static String getDialogTitle() {
		return ActionMessages.ShowInNavigatorView_dialog_title;
	}
}

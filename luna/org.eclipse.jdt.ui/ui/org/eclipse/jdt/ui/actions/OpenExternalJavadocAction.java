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

import org.eclipse.jface.viewers.ISelectionProvider;

import org.eclipse.ui.IWorkbenchSite;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;


/**
 * This action opens the selected element's Javadoc in an external browser.
 * <p>
 * The action is applicable to selections containing elements of type <code>IJavaElement</code>.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 * @noextend This class is not intended to be subclassed by clients.
 * @deprecated As of 3.6, replaced by {@link OpenAttachedJavadocAction}
 */
public class OpenExternalJavadocAction extends OpenAttachedJavadocAction {

	/**
	 * Creates a new <code>OpenExternalJavadocAction</code>. The action requires that the selection
	 * provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>
	 * 
	 * @param site the site providing additional context information for this action
	 */
	public OpenExternalJavadocAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.OpenExternalJavadocAction_label);
		setDescription(ActionMessages.OpenExternalJavadocAction_description);
		setToolTipText(ActionMessages.OpenExternalJavadocAction_tooltip);
	}

	/**
	 * Creates a new <code>OpenExternalJavadocAction</code>. The action requires that the selection
	 * provided by the given selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>
	 * .
	 * 
	 * @param site the site providing additional context information for this action
	 * @param provider a special selection provider which is used instead of the site's selection
	 *            provider or <code>null</code> to use the site's selection provider
	 * 
	 * @since 3.2
	 * @deprecated Use {@link #setSpecialSelectionProvider(ISelectionProvider)} instead.
	 */
    public OpenExternalJavadocAction(IWorkbenchSite site, ISelectionProvider provider) {
        this(site);
        setSpecialSelectionProvider(provider);
    }

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * 
	 * @param editor the Java editor
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public OpenExternalJavadocAction(JavaEditor editor) {
		super(editor);
		setText(ActionMessages.OpenExternalJavadocAction_label);
		setDescription(ActionMessages.OpenExternalJavadocAction_description);
		setToolTipText(ActionMessages.OpenExternalJavadocAction_tooltip);
	}

	/*
	 * No Javadoc since the method isn't meant to be public but is
	 * since the beginning
	 */
	@Override
	public void run(IJavaElement element) {
		super.run(element);
	}

	/*
	 * @see org.eclipse.jdt.ui.actions.OpenAttachedJavadocAction#forceExternalBrowser()
	 */
	@Override
	boolean forceExternalBrowser() {
		return true;
	}
}

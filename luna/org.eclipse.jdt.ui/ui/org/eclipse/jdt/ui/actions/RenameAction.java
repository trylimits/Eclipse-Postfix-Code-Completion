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

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.actions.RenameJavaElementAction;
import org.eclipse.jdt.internal.ui.refactoring.actions.RenameResourceAction;

/**
 * Renames a Java element or workbench resource.
 * <p>
 * Action is applicable to selections containing elements of type
 * <code>IJavaElement</code> or <code>IResource</code>.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class RenameAction extends SelectionDispatchAction {

	private RenameJavaElementAction fRenameJavaElement;
	private RenameResourceAction fRenameResource;

	/**
	 * Creates a new <code>RenameAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public RenameAction(IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.RenameAction_text);
		fRenameJavaElement= new RenameJavaElementAction(site);
		fRenameJavaElement.setText(getText());
		fRenameResource= new RenameResourceAction(site);
		fRenameResource.setText(getText());
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.RENAME_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public RenameAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fRenameJavaElement= new RenameJavaElementAction(editor);
	}

	/*
	 * @see ISelectionChangedListener#selectionChanged(SelectionChangedEvent)
	 */
	@Override
	public void selectionChanged(SelectionChangedEvent event) {
		fRenameJavaElement.selectionChanged(event);
		if (fRenameResource != null)
			fRenameResource.selectionChanged(event);
		setEnabled(computeEnabledState());
	}

	/*
	 * @see SelectionDispatchAction#update(ISelection)
	 */
	@Override
	public void update(ISelection selection) {
		fRenameJavaElement.update(selection);

		if (fRenameResource != null)
			fRenameResource.update(selection);

		setEnabled(computeEnabledState());
	}

	private boolean computeEnabledState(){
		if (fRenameResource != null) {
			return fRenameJavaElement.isEnabled() || fRenameResource.isEnabled();
		} else {
			return fRenameJavaElement.isEnabled();
		}
	}

	@Override
	public void run(IStructuredSelection selection) {
		if (fRenameJavaElement.isEnabled())
			fRenameJavaElement.run(selection);
		if (fRenameResource != null && fRenameResource.isEnabled())
			fRenameResource.run(selection);
	}

	@Override
	public void run(ITextSelection selection) {
		if (fRenameJavaElement.isEnabled())
			fRenameJavaElement.run(selection);
	}
}

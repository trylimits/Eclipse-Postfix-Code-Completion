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
package org.eclipse.jdt.internal.ui.browsing;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.core.resources.IWorkspace;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.actions.OpenInNewWindowAction;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;

/*
 * XXX: This is a workaround for: http://dev.eclipse.org/bugs/show_bug.cgi?id=13070
 * This class can be removed once the bug is fixed.
 *
 * @since 2.0
 */
public class PatchedOpenInNewWindowAction extends OpenInNewWindowAction {

	private IWorkbenchWindow fWorkbenchWindow;

	public PatchedOpenInNewWindowAction(IWorkbenchWindow window, IAdaptable input) {
		super(window, input);
		fWorkbenchWindow= window;
	}

	@Override
	public void run() {
		JavaBrowsingPerspectiveFactory.setInputFromAction(getSelectedJavaElement());
		try {
			super.run();
		} finally {
			JavaBrowsingPerspectiveFactory.setInputFromAction(null);
		}
	}

	private IJavaElement getSelectedJavaElement() {
		if (fWorkbenchWindow.getActivePage() != null) {
			ISelection selection= fWorkbenchWindow.getActivePage().getSelection();
			if (selection instanceof IStructuredSelection && !selection.isEmpty()) {
				Object selectedElement= ((IStructuredSelection)selection).getFirstElement();
				if (selectedElement instanceof IJavaElement)
					return (IJavaElement)selectedElement;
				if (!(selectedElement instanceof IJavaElement) && selectedElement instanceof IAdaptable)
					return (IJavaElement)((IAdaptable)selectedElement).getAdapter(IJavaElement.class);
				else if (selectedElement instanceof IWorkspace)
						return JavaCore.create(((IWorkspace)selectedElement).getRoot());
			}
		}
		return null;
	}
}

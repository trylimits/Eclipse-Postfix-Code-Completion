/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.navigator;

import org.eclipse.core.resources.IFile;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.ide.ResourceUtil;
import org.eclipse.ui.navigator.ILinkHelper;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;

public class JavaFileLinkHelper implements ILinkHelper {

	public void activateEditor(IWorkbenchPage page, IStructuredSelection selection) {
		if (selection == null || selection.isEmpty())
			return;
		Object element= selection.getFirstElement();
		IEditorPart part= EditorUtility.isOpenInEditor(element);
		if (part != null) {
			page.bringToTop(part);
			if (element instanceof IJavaElement)
				EditorUtility.revealInEditor(part, (IJavaElement) element);
		}

	}

	public IStructuredSelection findSelection(IEditorInput input) {
		IJavaElement element= JavaUI.getEditorInputJavaElement(input);
		if (element == null) {
			IFile file = ResourceUtil.getFile(input);
			if (file != null) {
				element= JavaCore.create(file);
			}
		}
		return (element != null) ? new StructuredSelection(element) : StructuredSelection.EMPTY;
	}

}

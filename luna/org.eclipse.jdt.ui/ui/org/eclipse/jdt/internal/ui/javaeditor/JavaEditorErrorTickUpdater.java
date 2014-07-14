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
package org.eclipse.jdt.internal.ui.javaeditor;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;

import org.eclipse.core.resources.IResource;

import org.eclipse.ui.IEditorInput;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.ui.ProblemsLabelDecorator;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.viewsupport.IProblemChangedListener;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaUILabelProvider;

/**
 * The <code>JavaEditorErrorTickUpdater</code> will register as a IProblemChangedListener
 * to listen on problem changes of the editor's input. It updates the title images when the annotation
 * model changed.
 */
public class JavaEditorErrorTickUpdater implements IProblemChangedListener {

	private JavaEditor fJavaEditor;
	private JavaUILabelProvider fLabelProvider;

	public JavaEditorErrorTickUpdater(JavaEditor editor) {
		Assert.isNotNull(editor);
		fJavaEditor= editor;
		fLabelProvider=  new JavaUILabelProvider(0, JavaElementImageProvider.SMALL_ICONS);
		fLabelProvider.addLabelDecorator(new ProblemsLabelDecorator(null));
		JavaPlugin.getDefault().getProblemMarkerManager().addListener(this);
	}

	/*
	 * @see IProblemChangedListener#problemsChanged(IResource[], boolean)
	 */
	public void problemsChanged(IResource[] changedResources, boolean isMarkerChange) {
		if (!isMarkerChange)
			return;

		IEditorInput input= fJavaEditor.getEditorInput();
		if (input != null) { // might run async, tests needed
			IJavaElement jelement= (IJavaElement) input.getAdapter(IJavaElement.class);
			if (jelement != null) {
				IResource resource= jelement.getResource();
				for (int i = 0; i < changedResources.length; i++) {
					if (changedResources[i].equals(resource)) {
						updateEditorImage(jelement);
					}
				}
			}
		}
	}

	public void updateEditorImage(IJavaElement jelement) {
		Image titleImage= fJavaEditor.getTitleImage();
		if (titleImage == null) {
			return;
		}
		Image newImage;
		if (jelement instanceof ICompilationUnit && !jelement.getJavaProject().isOnClasspath(jelement))
			newImage= fLabelProvider.getImage(jelement.getResource());
		else
			newImage= fLabelProvider.getImage(jelement);
		if (titleImage != newImage) {
			postImageChange(newImage);
		}
	}

	private void postImageChange(final Image newImage) {
		Shell shell= fJavaEditor.getEditorSite().getShell();
		if (shell != null && !shell.isDisposed()) {
			shell.getDisplay().syncExec(new Runnable() {
				public void run() {
					fJavaEditor.updatedTitleImage(newImage);
				}
			});
		}
	}

	public void dispose() {
		fLabelProvider.dispose();
		JavaPlugin.getDefault().getProblemMarkerManager().removeListener(this);
	}


}



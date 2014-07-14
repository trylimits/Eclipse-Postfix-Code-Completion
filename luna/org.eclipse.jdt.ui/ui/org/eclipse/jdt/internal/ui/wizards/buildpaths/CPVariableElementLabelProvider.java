/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.util.ArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import org.eclipse.core.runtime.IPath;

import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.LabelProvider;

import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;


public class CPVariableElementLabelProvider extends LabelProvider implements IColorProvider {

	// shared, do not dispose:
	private Image fJARImage;
	private Image fFolderImage;

	private Image fDeprecatedJARImage;
	private Image fDeprecatedFolderImage;

	private boolean fHighlightReadOnly;

	public CPVariableElementLabelProvider(boolean highlightReadOnly) {
		ImageRegistry reg= JavaPlugin.getDefault().getImageRegistry();
		fJARImage= reg.get(JavaPluginImages.IMG_OBJS_EXTJAR);
		fFolderImage= PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FOLDER);

		fDeprecatedJARImage= new DecorationOverlayIcon(fJARImage, JavaPluginImages.DESC_OVR_DEPRECATED, IDecoration.TOP_LEFT).createImage();
		fDeprecatedFolderImage= new DecorationOverlayIcon(fFolderImage, JavaPluginImages.DESC_OVR_DEPRECATED, IDecoration.TOP_LEFT).createImage();

		fHighlightReadOnly= highlightReadOnly;
	}

	/*
	 * @see LabelProvider#getImage(java.lang.Object)
	 */
	@Override
	public Image getImage(Object element) {
		if (element instanceof CPVariableElement) {
			CPVariableElement curr= (CPVariableElement) element;
			IPath path= curr.getPath();
			if (path.toFile().isFile()) {
				return curr.isDeprecated() ? fDeprecatedJARImage : fJARImage;
			}
			return curr.isDeprecated() ? fDeprecatedFolderImage : fFolderImage;
		}
		return super.getImage(element);
	}

	/*
	 * @see LabelProvider#getText(java.lang.Object)
	 */
	@Override
	public String getText(Object element) {
		if (element instanceof CPVariableElement) {
			CPVariableElement curr= (CPVariableElement)element;
			String name= curr.getName();
			IPath path= curr.getPath();

			String result= name;
			ArrayList<String> restrictions= new ArrayList<String>(2);

			if (curr.isReadOnly() && fHighlightReadOnly) {
				restrictions.add(NewWizardMessages.CPVariableElementLabelProvider_read_only);
			}
			if (curr.isDeprecated()) {
				restrictions.add(NewWizardMessages.CPVariableElementLabelProvider_deprecated);
			}
			if (restrictions.size() == 1) {
				result= Messages.format(NewWizardMessages.CPVariableElementLabelProvider_one_restriction, new Object[] {result, restrictions.get(0)});
			} else if (restrictions.size() == 2) {
				result= Messages.format(NewWizardMessages.CPVariableElementLabelProvider_two_restrictions, new Object[] {result, restrictions.get(0), restrictions.get(1)});
			}

			if (path != null) {
				String appendix;
				if (!path.isEmpty()) {
					appendix= BasicElementLabels.getPathLabel(path, true);
				} else {
					appendix= NewWizardMessages.CPVariableElementLabelProvider_empty;
				}
				result= Messages.format(NewWizardMessages.CPVariableElementLabelProvider_appendix, new Object[] {result, appendix});
			}

			return result;
		}


		return super.getText(element);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IColorProvider#getForeground(java.lang.Object)
	 */
	public Color getForeground(Object element) {
		if (isUnmodifiable(element)) {
			Display display= Display.getCurrent();
			return display.getSystemColor(SWT.COLOR_INFO_FOREGROUND);
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IColorProvider#getBackground(java.lang.Object)
	 */
	public Color getBackground(Object element) {
		if (isUnmodifiable(element)) {
			Display display= Display.getCurrent();
			return display.getSystemColor(SWT.COLOR_INFO_BACKGROUND);
		}
		return null;
	}

	private boolean isUnmodifiable(Object element) {
		if (element instanceof CPVariableElement) {
			CPVariableElement curr= (CPVariableElement) element;
			if (fHighlightReadOnly && curr.isReadOnly()) {
				return true;
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IBaseLabelProvider#dispose()
	 */
	@Override
	public void dispose() {
		super.dispose();
		fDeprecatedFolderImage.dispose();
		fDeprecatedJARImage.dispose();
	}

}

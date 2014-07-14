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
package org.eclipse.jdt.internal.ui.javadocexport;

import java.net.URL;

import org.eclipse.swt.graphics.Image;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.LabelProvider;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.ISharedImages;
import org.eclipse.jdt.ui.JavaElementImageDescriptor;
import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;


public class JavadocLinkDialogLabelProvider extends LabelProvider {

	@Override
	public String getText(Object element) {
		if (element instanceof JavadocLinkRef) {
			JavadocLinkRef ref= (JavadocLinkRef) element;
			URL url= ref.getURL();
			String text= ref.getFullPath().lastSegment();
			if (url != null) {
				Object[] args= new Object[] { BasicElementLabels.getResourceName(text), BasicElementLabels.getURLPart(url.toExternalForm()) };
				return Messages.format(JavadocExportMessages.JavadocLinkDialogLabelProvider_configuredentry, args);
			} else {
				return Messages.format(JavadocExportMessages.JavadocLinkDialogLabelProvider_notconfiguredentry, BasicElementLabels.getResourceName(text));
			}
		}
		return super.getText(element);
	}

	@Override
	public Image getImage(Object element) {
		if (element instanceof JavadocLinkRef) {
			JavadocLinkRef ref= (JavadocLinkRef) element;
			ImageDescriptor desc;
			if (ref.isProjectRef()) {
				desc= PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(IDE.SharedImages.IMG_OBJ_PROJECT);
			} else {
				desc= JavaUI.getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJS_JAR);
			}
			if (ref.getURL() == null) {
				return JavaPlugin.getImageDescriptorRegistry().get(new JavaElementImageDescriptor(desc, JavaElementImageDescriptor.WARNING, JavaElementImageProvider.SMALL_SIZE));
			}
			return JavaPlugin.getImageDescriptorRegistry().get(desc);
		}
		return null;
	}

}

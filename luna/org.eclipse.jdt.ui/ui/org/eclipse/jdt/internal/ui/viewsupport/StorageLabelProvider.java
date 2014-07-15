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
package org.eclipse.jdt.internal.ui.viewsupport;


import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.swt.graphics.Image;

import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IStorage;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.LabelProvider;

import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IFileEditorMapping;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJarEntryResource;

/**
 * Standard label provider for IStorage objects.
 * Use this class when you want to present IStorage objects in a viewer.
 */
public class StorageLabelProvider extends LabelProvider {

	private IEditorRegistry fEditorRegistry= null;
	private Map<String, Image> fJarImageMap= new HashMap<String, Image>(10);
	private Image fDefaultImage;

	private IEditorRegistry getEditorRegistry() {
		if (fEditorRegistry == null)
			fEditorRegistry= PlatformUI.getWorkbench().getEditorRegistry();
		return fEditorRegistry;
	}

	/* (non-Javadoc)
	 * @see ILabelProvider#getImage
	 */
	@Override
	public Image getImage(Object element) {
		if (element instanceof IStorage)
			return getImageForJarEntry((IStorage)element);

		return super.getImage(element);
	}

	/* (non-Javadoc)
	 * @see ILabelProvider#getText
	 */
	@Override
	public String getText(Object element) {
		if (element instanceof IStorage) {
			return BasicElementLabels.getResourceName(((IStorage)element).getName());
		}
		return super.getText(element);
	}

	/* (non-Javadoc)
	 *
	 * @see IBaseLabelProvider#dispose
	 */
	@Override
	public void dispose() {
		if (fJarImageMap != null) {
			Iterator<Image> each= fJarImageMap.values().iterator();
			while (each.hasNext()) {
				Image image= each.next();
				image.dispose();
			}
			fJarImageMap= null;
		}
		fDefaultImage= null;
	}

	/*
	 * Gets and caches an image for a JarEntryFile.
	 * The image for a JarEntryFile is retrieved from the EditorRegistry.
	 */
	private Image getImageForJarEntry(IStorage element) {
		if (element instanceof IJarEntryResource && !((IJarEntryResource) element).isFile()) {
			return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FOLDER);
		}

		if (fJarImageMap == null)
			return getDefaultImage();

		if (element == null || element.getName() == null)
			return getDefaultImage();

		// Try to find icon for full name
		String name= element.getName();
		Image image= fJarImageMap.get(name);
		if (image != null)
			return image;
		IFileEditorMapping[] mappings= getEditorRegistry().getFileEditorMappings();
		int i= 0;
		while (i < mappings.length) {
			if (mappings[i].getLabel().equals(name))
				break;
			i++;
		}
		String key= name;
		if (i == mappings.length) {
			// Try to find icon for extension
			IPath path= element.getFullPath();
			if (path == null)
				return getDefaultImage();
			key= path.getFileExtension();
			if (key == null)
				return getDefaultImage();
			image= fJarImageMap.get(key);
			if (image != null)
				return image;
		}

		// Get the image from the editor registry
		ImageDescriptor desc= getEditorRegistry().getImageDescriptor(name);
		image= desc.createImage();

		fJarImageMap.put(key, image);

		return image;
	}

	private Image getDefaultImage() {
		if (fDefaultImage == null)
			fDefaultImage= PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FILE);
		return fDefaultImage;
	}
}

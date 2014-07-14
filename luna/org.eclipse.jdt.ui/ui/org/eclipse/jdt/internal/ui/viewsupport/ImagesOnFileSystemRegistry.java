/*******************************************************************************
 * Copyright (c) 2008, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.viewsupport;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.internal.ui.JavaPlugin;


/**
 * Image registry that keeps its images on the local file system.
 *
 * @since 3.4
 */
public class ImagesOnFileSystemRegistry {

	private static final String IMAGE_DIR= "jdt-images"; //$NON-NLS-1$

	private HashMap<ImageDescriptor, URL> fURLMap;
	private final File fTempDir;
	private final JavaElementImageProvider fImageProvider;
	private int fImageCount;

	public ImagesOnFileSystemRegistry() {
		fURLMap= new HashMap<ImageDescriptor, URL>();
		fTempDir= getTempDir();
		fImageProvider= new JavaElementImageProvider();
		fImageCount= 0;
	}

	private File getTempDir() {
		try {
			File imageDir= JavaPlugin.getDefault().getStateLocation().append(IMAGE_DIR).toFile();
			if (imageDir.exists()) {
				// has not been deleted on previous shutdown
				delete(imageDir);
			}
			if (!imageDir.exists()) {
				imageDir.mkdir();
			}
			if (!imageDir.isDirectory()) {
				JavaPlugin.logErrorMessage("Failed to create image directory " + imageDir.toString()); //$NON-NLS-1$
				return null;
			}
			return imageDir;
		} catch (IllegalStateException e) {
			// no state location
			return null;
		}
	}

	private void delete(File file) {
		if (file.isDirectory()) {
			File[] listFiles= file.listFiles();
			for (int i= 0; i < listFiles.length; i++) {
				delete(listFiles[i]);
			}
		}
		file.delete();
	}

	public URL getImageURL(IJavaElement element) {
		ImageDescriptor descriptor= fImageProvider.getJavaImageDescriptor(element, JavaElementImageProvider.OVERLAY_ICONS | JavaElementImageProvider.SMALL_ICONS);
		if (descriptor == null)
			return null;
		return getImageURL(descriptor);
	}

	public URL getImageURL(ImageDescriptor descriptor) {
		if (fTempDir == null)
			return null;

		URL url= fURLMap.get(descriptor);
		if (url != null)
			return url;

		File imageFile= getNewFile();
		ImageData imageData= descriptor.getImageData();
		if (imageData == null) {
			return null;
		}

		ImageLoader loader= new ImageLoader();
		loader.data= new ImageData[] { imageData };
		loader.save(imageFile.getAbsolutePath(), SWT.IMAGE_PNG);

		try {
			url= imageFile.toURI().toURL();
			fURLMap.put(descriptor, url);
			return url;
		} catch (MalformedURLException e) {
			JavaPlugin.log(e);
		}
		return null;
	}

	private File getNewFile() {
		File file;
		do {
			file= new File(fTempDir, String.valueOf(getImageCount()) + ".png"); //$NON-NLS-1$
		} while (file.exists());
		return file;
	}

	private synchronized int getImageCount() {
		return fImageCount++;
	}

	public void dispose() {
		if (fTempDir != null) {
			delete(fTempDir);
		}
		fURLMap= null;
	}
}

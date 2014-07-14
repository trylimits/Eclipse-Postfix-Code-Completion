/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.viewsupport;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;

import org.eclipse.jface.resource.CompositeImageDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;

/**
 * An image descriptor that draws a focus adornment on top of a base image.
 * 
 * @since 3.7
 */
public class FocusDescriptor extends CompositeImageDescriptor {
	private ImageDescriptor fBase;
	public FocusDescriptor(ImageDescriptor base) {
		fBase= base;
	}
	@Override
	protected void drawCompositeImage(int width, int height) {
		drawImage(getImageData(fBase), 0, 0);
		drawImage(getImageData(JavaPluginImages.DESC_OVR_FOCUS), 0, 0);
	}

	private ImageData getImageData(ImageDescriptor descriptor) {
		ImageData data= descriptor.getImageData(); // see bug 51965: getImageData can return null
		if (data == null) {
			data= DEFAULT_IMAGE_DATA;
			JavaPlugin.logErrorMessage("Image data not available: " + descriptor.toString()); //$NON-NLS-1$
		}
		return data;
	}

	@Override
	protected Point getSize() {
		return JavaElementImageProvider.BIG_SIZE;
	}
	@Override
	public int hashCode() {
		return fBase.hashCode();
	}
	@Override
	public boolean equals(Object object) {
		return object != null && FocusDescriptor.class.equals(object.getClass()) && ((FocusDescriptor)object).fBase.equals(fBase);
	}
}

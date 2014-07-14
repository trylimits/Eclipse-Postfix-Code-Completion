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
package org.eclipse.jdt.internal.ui.javaeditor;

import org.eclipse.swt.graphics.Image;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.jface.text.source.Annotation;

import org.eclipse.ui.texteditor.IAnnotationImageProvider;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.text.correction.JavaCorrectionProcessor;

/**
 * Image provider for annotations based on Java problem markers.
 *
 * @since 3.0
 */
public class JavaAnnotationImageProvider implements IAnnotationImageProvider {

	private final static int NO_IMAGE= 0;
	private final static int GRAY_IMAGE= 1;
	private final static int OVERLAY_IMAGE= 2;
	private final static int QUICKFIX_IMAGE= 3;
	private final static int QUICKFIX_ERROR_IMAGE= 4;


	private static Image fgQuickFixImage;
	private static Image fgQuickFixErrorImage;
	private boolean fShowQuickFixIcon;
	private int fCachedImageType;
	private Image fCachedImage;


	public JavaAnnotationImageProvider() {
		fShowQuickFixIcon= PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_CORRECTION_INDICATION);
	}

	/*
	 * @see org.eclipse.jface.text.source.IAnnotationImageProvider#getManagedImage(org.eclipse.jface.text.source.Annotation)
	 */
	public Image getManagedImage(Annotation annotation) {
		if (annotation instanceof IJavaAnnotation) {
			IJavaAnnotation javaAnnotation= (IJavaAnnotation) annotation;
			int imageType= getImageType(javaAnnotation);
			return getImage(javaAnnotation, imageType);
		}
		return null;
	}

	/*
	 * @see org.eclipse.jface.text.source.IAnnotationImageProvider#getImageDescriptorId(org.eclipse.jface.text.source.Annotation)
	 */
	public String getImageDescriptorId(Annotation annotation) {
		// unmanaged images are not supported
		return null;
	}

	/*
	 * @see org.eclipse.jface.text.source.IAnnotationImageProvider#getImageDescriptor(java.lang.String)
	 */
	public ImageDescriptor getImageDescriptor(String symbolicName) {
		// unmanaged images are not supported
		return null;
	}


	private boolean showQuickFix(IJavaAnnotation annotation) {
		return fShowQuickFixIcon && annotation.isProblem() && JavaCorrectionProcessor.hasCorrections((Annotation) annotation);
	}

	private Image getQuickFixImage() {
		if (fgQuickFixImage == null)
			fgQuickFixImage= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_FIXABLE_PROBLEM);
		return fgQuickFixImage;
	}

	private Image getQuickFixErrorImage() {
		if (fgQuickFixErrorImage == null)
			fgQuickFixErrorImage= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_FIXABLE_ERROR);
		return fgQuickFixErrorImage;
	}

	private int getImageType(IJavaAnnotation annotation) {
		int imageType= NO_IMAGE;
		if (annotation.hasOverlay())
			imageType= OVERLAY_IMAGE;
		else if (!annotation.isMarkedDeleted()) {
			if (showQuickFix(annotation))
				imageType= JavaMarkerAnnotation.ERROR_ANNOTATION_TYPE.equals(annotation.getType()) ? QUICKFIX_ERROR_IMAGE : QUICKFIX_IMAGE;
		} else {
			imageType= GRAY_IMAGE;
		}
		return imageType;
	}

	private Image getImage(IJavaAnnotation annotation, int imageType) {
		if ((imageType == QUICKFIX_IMAGE || imageType == QUICKFIX_ERROR_IMAGE) && fCachedImageType == imageType)
			return fCachedImage;

		Image image= null;
		switch (imageType) {
			case OVERLAY_IMAGE:
				IJavaAnnotation overlay= annotation.getOverlay();
				image= getManagedImage((Annotation) overlay);
				fCachedImageType= -1;
				break;
			case QUICKFIX_IMAGE:
				image= getQuickFixImage();
				fCachedImageType= imageType;
				fCachedImage= image;
				break;
			case QUICKFIX_ERROR_IMAGE:
				image= getQuickFixErrorImage();
				fCachedImageType= imageType;
				fCachedImage= image;
				break;
			case GRAY_IMAGE: {
				String annotationType= annotation.getType();
				if (JavaMarkerAnnotation.ERROR_ANNOTATION_TYPE.equals(annotationType)) {
					image= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_ERROR_ALT);
				} else if (JavaMarkerAnnotation.WARNING_ANNOTATION_TYPE.equals(annotationType)) {
					image= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_WARNING_ALT);
				} else if (JavaMarkerAnnotation.INFO_ANNOTATION_TYPE.equals(annotationType)) {
					image= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_INFO_ALT);
				}
				fCachedImageType= -1;
				break;
			}
		}

		return image;
	}
}

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
package org.eclipse.jdt.internal.ui.typehierarchy;

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import org.eclipse.jface.preference.JFacePreferences;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaElementImageDescriptor;
import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.FocusDescriptor;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;

/**
 * Label provider for the hierarchy viewers. Types in the hierarchy that are not belonging to the
 * input scope are rendered differently.
  */
public class HierarchyLabelProvider extends AppearanceAwareLabelProvider {

	private Color fSpecialColor;

	private ViewerFilter fFilter;

	private TypeHierarchyLifeCycle fHierarchy;

	public HierarchyLabelProvider(TypeHierarchyLifeCycle lifeCycle) {
		super(DEFAULT_TEXTFLAGS | JavaElementLabels.USE_RESOLVED | JavaElementLabels.P_COMPRESSED, DEFAULT_IMAGEFLAGS);

		fHierarchy= lifeCycle;
		fFilter= null;
	}

	/**
	 * @return Returns the filter.
	 */
	public ViewerFilter getFilter() {
		return fFilter;
	}

	/**
	 * @param filter The filter to set.
	 */
	public void setFilter(ViewerFilter filter) {
		fFilter= filter;
	}

	protected boolean isInDifferentHierarchyScope(IType type) {
		if (fFilter != null && !fFilter.select(null, null, type)) {
			return true;
		}
		IJavaElement[] input= fHierarchy.getInputElements();
		if (input == null)
			return false;
		for (int i= 0; i < input.length; i++) {
			if (input[i] == null || input[i].getElementType() == IJavaElement.TYPE) {
				return false;
			}
			IJavaElement parent= type.getAncestor(input[i].getElementType());
			if (input[i].getElementType() == IJavaElement.PACKAGE_FRAGMENT) {
				if (parent == null || parent.getElementName().equals(input[i].getElementName())) {
					return false;
				}
			} else if (input[i].equals(parent)) {
				return false;
			}
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see ILabelProvider#getImage
	 */
	@Override
	public Image getImage(Object element) {
		Image result= null;
		if (element instanceof IType) {
			ImageDescriptor desc= getTypeImageDescriptor((IType) element);
			if (desc != null) {
				IJavaElement[] elements= fHierarchy.getInputElements();
				if (elements != null && Arrays.asList(elements).contains(element)) {
					desc= new FocusDescriptor(desc);
				}
				result= JavaPlugin.getImageDescriptorRegistry().get(desc);
			}
		} else {
			result= fImageLabelProvider.getImageLabel(element, evaluateImageFlags(element));
		}
		return decorateImage(result, element);
	}

	private ImageDescriptor getTypeImageDescriptor(IType type) {
		ITypeHierarchy hierarchy= fHierarchy.getHierarchy();
		if (hierarchy == null) {
			return new JavaElementImageDescriptor(JavaPluginImages.DESC_OBJS_CLASS, 0, JavaElementImageProvider.BIG_SIZE);
		}

		int flags= hierarchy.getCachedFlags(type);
		if (flags == -1) {
			return new JavaElementImageDescriptor(JavaPluginImages.DESC_OBJS_CLASS, 0, JavaElementImageProvider.BIG_SIZE);
		}

		boolean isInterface= Flags.isInterface(flags);
		IType declaringType= type.getDeclaringType();
		boolean isInner= declaringType != null;
		boolean isInInterfaceOrAnnotation= false;
		if (isInner) {
			int declaringTypeFlags= hierarchy.getCachedFlags(declaringType);
			if (declaringTypeFlags != -1) {
				isInInterfaceOrAnnotation= Flags.isInterface(declaringTypeFlags);
			} else {
				// declaring type is not in hierarchy, so we have to pay the price for resolving here
				try {
					isInInterfaceOrAnnotation= declaringType.isInterface();
				} catch (JavaModelException e) {
				}
			}
		}

		ImageDescriptor desc= JavaElementImageProvider.getTypeImageDescriptor(isInner, isInInterfaceOrAnnotation, flags, isInDifferentHierarchyScope(type));

		int adornmentFlags= 0;
		if (Flags.isFinal(flags)) {
			adornmentFlags |= JavaElementImageDescriptor.FINAL;
		}
		if (Flags.isAbstract(flags) && !isInterface) {
			adornmentFlags |= JavaElementImageDescriptor.ABSTRACT;
		}
		if (Flags.isStatic(flags)) {
			adornmentFlags |= JavaElementImageDescriptor.STATIC;
		}
		if (Flags.isDeprecated(flags)) {
			adornmentFlags |= JavaElementImageDescriptor.DEPRECATED;
		}

		return new JavaElementImageDescriptor(desc, adornmentFlags, JavaElementImageProvider.BIG_SIZE);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IColorProvider#getForeground(java.lang.Object)
	 */
	@Override
	public Color getForeground(Object element) {
		if (element instanceof IMethod) {
			if (fSpecialColor == null) {
				fSpecialColor= Display.getCurrent().getSystemColor(SWT.COLOR_DARK_BLUE);
			}
			return fSpecialColor;
		} else if (element instanceof IType && isInDifferentHierarchyScope((IType) element)) {
			return JFaceResources.getColorRegistry().get(JFacePreferences.QUALIFIER_COLOR);
		}
		return null;
	}



}

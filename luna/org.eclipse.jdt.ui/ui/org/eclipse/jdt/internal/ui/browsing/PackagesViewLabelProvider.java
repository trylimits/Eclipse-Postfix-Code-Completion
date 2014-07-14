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

import org.eclipse.swt.graphics.Image;

import org.eclipse.core.runtime.Assert;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.StyledString;

import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.ImageDescriptorRegistry;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.internal.ui.viewsupport.TreeHierarchyLayoutProblemsDecorator;

/**
 * Label provider for the Packages view.
 */
class PackagesViewLabelProvider extends AppearanceAwareLabelProvider {

	static final int HIERARCHICAL_VIEW_STATE= 0;
	static final int FLAT_VIEW_STATE= 1;

	private int fViewState;

	private ImageDescriptorRegistry fRegistry;
	private TreeHierarchyLayoutProblemsDecorator fDecorator;

	PackagesViewLabelProvider(int state) {
		this(state, AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS | JavaElementLabels.P_COMPRESSED, AppearanceAwareLabelProvider.DEFAULT_IMAGEFLAGS | JavaElementImageProvider.SMALL_ICONS);
	}

	PackagesViewLabelProvider(int state, long textFlags, int imageFlags) {
		super(textFlags, imageFlags);

		Assert.isTrue(isValidState(state));
		fViewState= state;
		fRegistry= JavaPlugin.getImageDescriptorRegistry();

		fDecorator= new TreeHierarchyLayoutProblemsDecorator(isFlatView());
		addLabelDecorator(fDecorator);
	}

	private boolean isValidState(int state) {
		return state == FLAT_VIEW_STATE || state == HIERARCHICAL_VIEW_STATE;
	}

	/*
	 * @see org.eclipse.jface.viewers.ILabelProvider#getImage(java.lang.Object)
	 */
	@Override
	public Image getImage(Object element) {
		if (element instanceof LogicalPackage) {
			LogicalPackage cp= (LogicalPackage) element;
			return getLogicalPackageImage(cp);
		}
		return super.getImage(element);
	}

	/*
	 * Decoration is only concerned with error ticks
	 */
	private Image getLogicalPackageImage(LogicalPackage cp) {
		IPackageFragment[] fragments= cp.getFragments();
		for (int i= 0; i < fragments.length; i++) {
			IPackageFragment fragment= fragments[i];
			if(!isEmpty(fragment)) {
				return decorateCompoundElement(JavaPluginImages.DESC_OBJS_LOGICAL_PACKAGE, cp);
			}
		}
		return decorateCompoundElement(JavaPluginImages.DESC_OBJS_EMPTY_LOGICAL_PACKAGE, cp);
	}


	private Image decorateCompoundElement(ImageDescriptor imageDescriptor, LogicalPackage cp) {
		Image image= fRegistry.get(imageDescriptor);
		return decorateImage(image, cp);
	}

	private boolean isEmpty(IPackageFragment fragment) {
		try {
			return (fragment.getCompilationUnits().length == 0) && (fragment.getClassFiles().length == 0);
		} catch (JavaModelException e) {
			// ignore
		}
		return false;
	}

	/*
	 * @see org.eclipse.jface.viewers.ILabelProvider#getText(java.lang.Object)
	 */
	@Override
	public String getText(Object element) {
		if (element instanceof IPackageFragment)
			return getText((IPackageFragment)element);
		else if (element instanceof LogicalPackage)
			return getText((LogicalPackage)element);
		else
			return super.getText(element);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.viewsupport.JavaUILabelProvider#getRichTextLabel(java.lang.Object)
	 */
	@Override
	public StyledString getStyledText(Object element) {
		if (element instanceof IPackageFragment || element instanceof LogicalPackage)
			return new StyledString(getText(element));
		return super.getStyledText(element);
	}

	private String getText(IPackageFragment fragment) {
		if (isFlatView())
			return getFlatText(fragment);
		else
			return getHierarchicalText(fragment);
	}

	private String getText(LogicalPackage logicalPackage) {
		IPackageFragment[] fragments= logicalPackage.getFragments();
		return getText(fragments[0]);
	}

	private String getFlatText(IPackageFragment fragment) {
		return super.getText(fragment);
	}

	private boolean isFlatView() {
		return fViewState==FLAT_VIEW_STATE;
	}

	private String getHierarchicalText(IPackageFragment fragment) {
		if (fragment.isDefaultPackage()) {
			return super.getText(fragment);
		}
		IResource res= fragment.getResource();
		if(res != null && !(res.getType() == IResource.FILE))
			return decorateText(res.getName(), fragment);
		else
			return decorateText(calculateName(fragment), fragment);
	}

	private String calculateName(IPackageFragment fragment) {

		String name= fragment.getElementName();
		if (name.indexOf(".") != -1) //$NON-NLS-1$
			name= name.substring(name.lastIndexOf(".") + 1); //$NON-NLS-1$
		return name;

	}
}

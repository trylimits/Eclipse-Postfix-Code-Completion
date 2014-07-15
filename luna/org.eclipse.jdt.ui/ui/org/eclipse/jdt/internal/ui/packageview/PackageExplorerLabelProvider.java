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
package org.eclipse.jdt.internal.ui.packageview;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.swt.graphics.Image;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IFolder;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.StyledString;

import org.eclipse.ui.IWorkingSet;

import org.eclipse.jdt.core.IPackageFragment;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;

/**
 * Provides the labels for the Package Explorer.
 * <p>
 * It provides labels for the packages in hierarchical layout and in all
 * other cases delegates it to its super class.
 * </p>
 * @since 2.1
 */
public class PackageExplorerLabelProvider extends AppearanceAwareLabelProvider {

	private PackageExplorerContentProvider fContentProvider;
	private Map<ImageDescriptor, Image> fWorkingSetImages;

	private boolean fIsFlatLayout;
	private PackageExplorerProblemsDecorator fProblemDecorator;

	public PackageExplorerLabelProvider(PackageExplorerContentProvider cp) {
		super(DEFAULT_TEXTFLAGS | JavaElementLabels.P_COMPRESSED | JavaElementLabels.ALL_CATEGORY, DEFAULT_IMAGEFLAGS | JavaElementImageProvider.SMALL_ICONS);

		fProblemDecorator= new PackageExplorerProblemsDecorator();
		addLabelDecorator(fProblemDecorator);
		Assert.isNotNull(cp);
		fContentProvider= cp;
		fWorkingSetImages= null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.viewsupport.JavaUILabelProvider#getStyledText(java.lang.Object)
	 */
	@Override
	public StyledString getStyledText(Object element) {
		String text= getSpecificText(element);
		if (text != null) {
			return new StyledString(decorateText(text, element));
		}
		return super.getStyledText(element);
	}

	private String getSpecificText(Object element) {
		if (!fIsFlatLayout && element instanceof IPackageFragment) {
			IPackageFragment fragment = (IPackageFragment) element;
			Object parent= fContentProvider.getHierarchicalPackageParent(fragment);
			if (parent instanceof IPackageFragment) {
				return getNameDelta((IPackageFragment) parent, fragment);
			} else if (parent instanceof IFolder) { // bug 152735
				return getNameDelta((IFolder) parent, fragment);
			}
		} else if (element instanceof IWorkingSet) {
			return ((IWorkingSet) element).getLabel();
		}
		return null;
	}

	@Override
	public String getText(Object element) {
		String text= getSpecificText(element);
		if (text != null) {
			return decorateText(text, element);
		}
		return super.getText(element);
	}

	private String getNameDelta(IPackageFragment parent, IPackageFragment fragment) {
		String prefix= parent.getElementName() + '.';
		String fullName= fragment.getElementName();
		if (fullName.startsWith(prefix)) {
			return fullName.substring(prefix.length());
		}
		return fullName;
	}

	private String getNameDelta(IFolder parent, IPackageFragment fragment) {
		IPath prefix= parent.getFullPath();
		IPath fullPath= fragment.getPath();
		if (prefix.isPrefixOf(fullPath)) {
			StringBuffer buf= new StringBuffer();
			for (int i= prefix.segmentCount(); i < fullPath.segmentCount(); i++) {
				if (buf.length() > 0)
					buf.append('.');
				buf.append(fullPath.segment(i));
			}
			return buf.toString();
		}
		return fragment.getElementName();
	}

	@Override
	public Image getImage(Object element) {
		if (element instanceof IWorkingSet) {
			ImageDescriptor image= ((IWorkingSet)element).getImageDescriptor();
			if (image == null) {
				return null;
			}
			if (fWorkingSetImages == null) {
				fWorkingSetImages= new HashMap<ImageDescriptor, Image>();
			}

			Image result= fWorkingSetImages.get(image);
			if (result == null) {
				result= image.createImage();
				fWorkingSetImages.put(image, result);
			}
			return decorateImage(result, element);
		}
		return super.getImage(element);
	}

	public void setIsFlatLayout(boolean state) {
		fIsFlatLayout= state;
		fProblemDecorator.setIsFlatLayout(state);
	}

	@Override
	public void dispose() {
		if (fWorkingSetImages != null) {
			for (Iterator<Image> iter= fWorkingSetImages.values().iterator(); iter.hasNext();) {
				iter.next().dispose();
			}
		}
		super.dispose();
	}
}

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

package org.eclipse.jdt.internal.ui.refactoring.nls.search;

import org.eclipse.swt.graphics.Image;

import org.eclipse.core.resources.IFile;

import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;

import org.eclipse.search.ui.text.AbstractTextSearchViewPage;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.search.TextSearchLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;


class NLSSearchResultLabelProvider2 extends TextSearchLabelProvider implements IStyledLabelProvider {

	private AppearanceAwareLabelProvider fLabelProvider;

	public NLSSearchResultLabelProvider2(AbstractTextSearchViewPage page) {
		super(page);
		fLabelProvider= new AppearanceAwareLabelProvider(JavaElementLabels.ALL_POST_QUALIFIED, 0);
	}

	public StyledString getStyledText(Object element) {
		return getColoredLabelWithCounts(element, internalGetText(element));
	}


	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.LabelProvider#getText(java.lang.Object)
	 */
	@Override
	public String getText(Object element) {
		return getLabelWithCounts(element, internalGetText(element).getString());
	}

	private StyledString internalGetText(Object element) {
		String description;
		StyledString elementLabel;

		if (element instanceof FileEntry) {
			FileEntry fileEntry= (FileEntry) element;
			description= fileEntry.getMessage();
			elementLabel= getPropertiesName(fileEntry.getPropertiesFile());
		} else if (element instanceof CompilationUnitEntry) {
			CompilationUnitEntry cuEntry= (CompilationUnitEntry) element;
			description= cuEntry.getMessage();
			elementLabel= JavaElementLabels.getStyledTextLabel(cuEntry.getCompilationUnit(), (JavaElementLabels.ALL_POST_QUALIFIED | JavaElementLabels.COLORIZE));
		} else {
			description= NLSSearchMessages.NLSSearchResultLabelProvider2_undefinedKeys;
			elementLabel= JavaElementLabels.getStyledTextLabel(element, (JavaElementLabels.ALL_POST_QUALIFIED | JavaElementLabels.COLORIZE));
		}
		return new StyledString(description).append(' ').append(elementLabel);
	}

	private StyledString getPropertiesName(IFile propertiesFile) {
		String path= BasicElementLabels.getPathLabel(propertiesFile.getFullPath().removeLastSegments(1), false);
		String propertiesName= BasicElementLabels.getResourceName(propertiesFile.getName());
		return new StyledString(propertiesName).append(JavaElementLabels.CONCAT_STRING + path, StyledString.QUALIFIER_STYLER);
	}

	/*
	 * @see org.eclipse.jface.viewers.LabelProvider#getImage(java.lang.Object)
	 */
	@Override
	public Image getImage(Object element) {
		if (element instanceof FileEntry)
			element= ((FileEntry) element).getPropertiesFile();
		if (element instanceof CompilationUnitEntry)
			element= ((CompilationUnitEntry)element).getCompilationUnit();

		return fLabelProvider.getImage(element);
	}

	/*
	 * @see org.eclipse.jface.viewers.LabelProvider#dispose()
	 */
	@Override
	public void dispose() {
		fLabelProvider.dispose();
		fLabelProvider= null;
		super.dispose();
	}
}

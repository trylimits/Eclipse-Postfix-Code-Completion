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
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;


public class ClasspathOrderingWorkbookPage extends BuildPathBasePage {

	private ListDialogField<CPListElement> fClassPathList;

	public ClasspathOrderingWorkbookPage(ListDialogField<CPListElement> classPathList) {
		fClassPathList= classPathList;
	}

	@Override
	public Control getControl(Composite parent) {
		PixelConverter converter= new PixelConverter(parent);

		Composite composite= new Composite(parent, SWT.NONE);
		composite.setFont(parent.getFont());

		LayoutUtil.doDefaultLayout(composite, new DialogField[] { fClassPathList }, true, SWT.DEFAULT, SWT.DEFAULT);
		LayoutUtil.setHorizontalGrabbing(fClassPathList.getListControl(null));

		int buttonBarWidth= converter.convertWidthInCharsToPixels(24);
		fClassPathList.setButtonsMinWidth(buttonBarWidth);

		return composite;
	}

	/*
	 * @see BuildPathBasePage#getSelection
	 */
	@Override
	public List<?> getSelection() {
		return fClassPathList.getSelectedElements();
	}

	/*
	 * @see BuildPathBasePage#setSelection
	 */
	@Override
	public void setSelection(List<?> selElements, boolean expand) {
		fClassPathList.selectElements(new StructuredSelection(selElements));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathBasePage#isEntryKind(int)
	 */
	@Override
	public boolean isEntryKind(int kind) {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathBasePage#init(org.eclipse.jdt.core.IJavaProject)
	 */
	@Override
	public void init(IJavaProject javaProject) {
	}

	/**
     * {@inheritDoc}
     */
    @Override
	public void setFocus() {
    	fClassPathList.setFocus();
    }

}

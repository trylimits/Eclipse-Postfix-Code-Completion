/*******************************************************************************
 * Copyright (c) 2005, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.model;

import org.eclipse.swt.graphics.Image;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.resources.IFolder;

import org.eclipse.ltk.core.refactoring.RefactoringDescriptorProxy;
import org.eclipse.ltk.core.refactoring.history.RefactoringHistory;
import org.eclipse.ltk.ui.refactoring.history.RefactoringHistoryControlConfiguration;
import org.eclipse.ltk.ui.refactoring.history.RefactoringHistoryLabelProvider;

import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;

/**
 * Label provider for Java models.
 *
 * @since 3.2
 */
public final class JavaModelLabelProvider extends AppearanceAwareLabelProvider {

	/** The name of the settings folder */
	private static final String NAME_SETTINGS_FOLDER= ".settings"; //$NON-NLS-1$

	/** The refactoring history label provider */
	private final RefactoringHistoryLabelProvider fHistoryLabelProvider= new RefactoringHistoryLabelProvider(new RefactoringHistoryControlConfiguration(null, false, false));

	/** The project preferences label */
	private final String fPreferencesLabel;

	/** The pending refactorings label */
	private final String fRefactoringsLabel;

	/** The project settings image, or <code>null</code> */
	private Image fSettingsImage= null;

	/**
	 * Creates a new java model label provider.
	 */
	public JavaModelLabelProvider() {
		this(ModelMessages.JavaModelLabelProvider_project_preferences_label, ModelMessages.JavaModelLabelProvider_refactorings_label);
	}

	/**
	 * Creates a new java model label provider.
	 *
	 * @param preferences
	 *            the preferences label
	 * @param refactorings
	 *            the refactorings label
	 */
	public JavaModelLabelProvider(final String preferences, final String refactorings) {
		super(AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS | JavaElementLabels.P_COMPRESSED, AppearanceAwareLabelProvider.DEFAULT_IMAGEFLAGS | JavaElementImageProvider.SMALL_ICONS);
		Assert.isNotNull(preferences);
		Assert.isNotNull(refactorings);
		fPreferencesLabel= preferences;
		fRefactoringsLabel= refactorings;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dispose() {
		super.dispose();
		fHistoryLabelProvider.dispose();
		if (fSettingsImage != null && !fSettingsImage.isDisposed()) {
			fSettingsImage.dispose();
			fSettingsImage= null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Image getImage(final Object element) {
		if (element instanceof IFolder) {
			final IFolder folder= (IFolder) element;
			if (!isInJavaProject(folder))
				return null;
			if (folder.getName().equals(NAME_SETTINGS_FOLDER)) {
				if (fSettingsImage == null || fSettingsImage.isDisposed())
					fSettingsImage= JavaPluginImages.DESC_OBJS_PROJECT_SETTINGS.createImage();
				return decorateImage(fSettingsImage, element);
			}
		}
		Image image= super.getImage(element);
		if (image == null) {
			if (element instanceof RefactoringHistory)
				image= fHistoryLabelProvider.getImage(element);
			else if (element instanceof RefactoringDescriptorProxy)
				image= fHistoryLabelProvider.getImage(element);
			return decorateImage(image, element);
		}
		return image;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getText(final Object element) {
		if (element instanceof IFolder) {
			final IFolder folder= (IFolder) element;
			if (!isInJavaProject(folder))
				return null;
			if (folder.getName().equals(NAME_SETTINGS_FOLDER))
				return decorateText(fPreferencesLabel, element);
		}
		String text= super.getText(element);
		if (text == null || "".equals(text)) { //$NON-NLS-1$
			if (element instanceof RefactoringHistory)
				text= fRefactoringsLabel;
			else if (element instanceof RefactoringDescriptorProxy)
				text= fHistoryLabelProvider.getText(element);
			return decorateText(text, element);
		}
		return text;
	}

	/**
	 * Tells whether the folder's project is a Java project.
	 * 
	 * @param folder the folder
	 * @return <code>true</code> if the folder is in a Java project
	 * @since 3.6
	 */
	private static boolean isInJavaProject(IFolder folder) {
		try {
			return folder.getProject().getNature(JavaCore.NATURE_ID) != null;
		} catch (CoreException e) {
			return false;
		}
	}
}

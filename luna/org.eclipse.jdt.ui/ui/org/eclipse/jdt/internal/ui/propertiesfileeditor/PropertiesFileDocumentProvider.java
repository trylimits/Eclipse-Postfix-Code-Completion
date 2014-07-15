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

package org.eclipse.jdt.internal.ui.propertiesfileeditor;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;

import org.eclipse.core.resources.IFile;

import org.eclipse.jface.text.IDocument;

import org.eclipse.ui.IFileEditorInput;

import org.eclipse.ui.texteditor.IDocumentProvider;

import org.eclipse.ui.editors.text.ForwardingDocumentProvider;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;


/**
 * Shared properties file document provider specialized for Java properties files.
 *
 * @since 3.1
 */
public class PropertiesFileDocumentProvider extends TextFileDocumentProvider {


	private static final IContentType JAVA_PROPERTIES_FILE_CONTENT_TYPE= Platform.getContentTypeManager().getContentType("org.eclipse.jdt.core.javaProperties"); //$NON-NLS-1$

	/**
	 * Checks whether the passed file editor input defines a Java properties file.
	 * 
	 * @param element the file editor input
	 * @return <code>true</code> if element defines a Java properties file, <code>false</code>
	 *         otherwise
	 * @throws CoreException
	 * 
	 * @since 3.7
	 */
	public static boolean isJavaPropertiesFile(Object element) throws CoreException {
		if (JAVA_PROPERTIES_FILE_CONTENT_TYPE == null || !(element instanceof IFileEditorInput))
			return false;

		IFileEditorInput input= (IFileEditorInput)element;

		IFile file= input.getFile();
		if (file == null || !file.isAccessible())
			return false;

		IContentDescription description= file.getContentDescription();
		if (description == null || description.getContentType() == null || !description.getContentType().isKindOf(JAVA_PROPERTIES_FILE_CONTENT_TYPE))
			return false;

		return true;
	}

	/**
	 * Creates a new properties file document provider and
	 * sets up the parent chain.
	 */
	public PropertiesFileDocumentProvider() {
		IDocumentProvider provider= new TextFileDocumentProvider();
		provider= new ForwardingDocumentProvider(IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING, new PropertiesFileDocumentSetupParticipant(), provider);
		setParentDocumentProvider(provider);
	}

	/*
	 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider#createFileInfo(java.lang.Object)
	 */
	@Override
	protected FileInfo createFileInfo(Object element) throws CoreException {
		if (!isJavaPropertiesFile(element))
			return null;

		return super.createFileInfo(element);
	}

	/*
	 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider#createSaveOperation(java.lang.Object, org.eclipse.jface.text.IDocument, boolean)
	 * @since 3.1
	 */
	@Override
	protected DocumentProviderOperation createSaveOperation(final Object element, final IDocument document, final boolean overwrite) throws CoreException {
		if (getFileInfo(element) == null)
			return null;

		return super.createSaveOperation(element, document, overwrite);
	}
}

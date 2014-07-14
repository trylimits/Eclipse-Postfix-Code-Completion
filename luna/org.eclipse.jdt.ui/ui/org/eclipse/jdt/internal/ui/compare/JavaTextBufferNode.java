/*******************************************************************************
 * Copyright (c) 2005, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.compare;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.eclipse.swt.graphics.Image;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFileState;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.text.IDocument;

import org.eclipse.compare.HistoryItem;
import org.eclipse.compare.IEncodedStreamContentAccessor;
import org.eclipse.compare.IResourceProvider;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.ResourceNode;
import org.eclipse.compare.structuremergeviewer.DocumentRangeNode;

import org.eclipse.jdt.core.dom.ASTNode;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Implements the IStreamContentAccessor and ITypedElement protocols
 * for a TextBuffer.
 */
class JavaTextBufferNode implements ITypedElement, IEncodedStreamContentAccessor, IResourceProvider {

	private IFile fFile;
	private IDocument fDocument;
	private boolean fInEditor;

	JavaTextBufferNode(IFile file, IDocument document, boolean inEditor) {
		fFile= file;
		fDocument= document;
		fInEditor= inEditor;
	}

	public String getName() {
		if (fInEditor)
			return CompareMessages.Editor_Buffer;
		return CompareMessages.Workspace_File;
	}

	public String getType() {
		return "java";	//$NON-NLS-1$
	}

	public Image getImage() {
		return null;
	}

	public InputStream getContents() {
		return new ByteArrayInputStream(JavaCompareUtilities.getBytes(fDocument.get(), "UTF-16")); //$NON-NLS-1$
	}

	public String getCharset() {
		return "UTF-16"; //$NON-NLS-1$
	}

	public IResource getResource() {
		return fFile;
	}

	static final ITypedElement[] buildEditions(ITypedElement target, IFile file) {

		// setup array of editions
		IFileState[] states= null;
		// add available editions
		try {
			states= file.getHistory(null);
		} catch (CoreException ex) {
			JavaPlugin.log(ex);
		}

		int count= 1;
		if (states != null)
			count+= states.length;

		ITypedElement[] editions= new ITypedElement[count];
		editions[0]= new ResourceNode(file);
		if (states != null)
			for (int i= 0; i < states.length; i++)
				editions[i+1]= new HistoryItem(target, states[i]);
		return editions;
	}

	/**
	 * Returns the corresponding place holder type for the given element.
	 * @return a place holder type (see ASTRewrite) or -1 if there is no corresponding placeholder
	 */
	static final int getPlaceHolderType(ITypedElement element) {

		if (element instanceof DocumentRangeNode) {
			JavaNode jn= (JavaNode) element;
			switch (jn.getTypeCode()) {

			case JavaNode.PACKAGE:
			    return ASTNode.PACKAGE_DECLARATION;

			case JavaNode.CLASS:
			case JavaNode.INTERFACE:
				return ASTNode.TYPE_DECLARATION;

			case JavaNode.ENUM:
				return ASTNode.ENUM_DECLARATION;

			case JavaNode.ANNOTATION:
				return ASTNode.ANNOTATION_TYPE_DECLARATION;

			case JavaNode.CONSTRUCTOR:
			case JavaNode.METHOD:
				return ASTNode.METHOD_DECLARATION;

			case JavaNode.FIELD:
				return ASTNode.FIELD_DECLARATION;

			case JavaNode.INIT:
				return ASTNode.INITIALIZER;

			case JavaNode.IMPORT:
			case JavaNode.IMPORT_CONTAINER:
				return ASTNode.IMPORT_DECLARATION;

			case JavaNode.CU:
			    return ASTNode.COMPILATION_UNIT;
			}
		}
		return -1;
	}
}

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


import java.util.Map;

import org.eclipse.core.resources.IResource;

import org.eclipse.ui.IEditorInput;

import org.eclipse.ui.texteditor.AddMarkerAction;
import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.IResourceLocator;



class AddClassFileMarkerAction extends AddMarkerAction {


	public AddClassFileMarkerAction(String prefix, ITextEditor textEditor, String markerType, boolean askForLabel) {
		super(JavaEditorMessages.getBundleForConstructedKeys(), prefix, textEditor, markerType, askForLabel);
	}

	/**
	 * @see AddMarkerAction#getResource()
	 */
	@Override
	protected IResource getResource() {

		IResource resource= null;

		IEditorInput input= getTextEditor().getEditorInput();
		if (input instanceof IClassFileEditorInput) {
			IClassFile c= ((IClassFileEditorInput) input).getClassFile();
			IResourceLocator locator= (IResourceLocator) c.getAdapter(IResourceLocator.class);
			if (locator != null) {
				try {
					resource= locator.getContainingResource(c);
				} catch (JavaModelException x) {
					// ignore but should inform
				}
			}
		}

		return resource;
	}

	/**
	 * @see AddMarkerAction#getInitialAttributes()
	 */
	@Override
	protected Map getInitialAttributes() {

		Map<String, Object> attributes= super.getInitialAttributes();

		IEditorInput input= getTextEditor().getEditorInput();
		if (input instanceof IClassFileEditorInput) {

			IClassFile classFile= ((IClassFileEditorInput) input).getClassFile();
			JavaCore.addJavaElementMarkerAttributes(attributes, classFile);
		}

		return attributes;
	}
}

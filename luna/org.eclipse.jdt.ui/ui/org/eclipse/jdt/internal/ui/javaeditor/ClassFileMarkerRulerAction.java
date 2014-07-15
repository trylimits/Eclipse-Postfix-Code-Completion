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

import org.eclipse.jface.text.source.IVerticalRuler;

import org.eclipse.ui.IEditorInput;

import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.MarkerRulerAction;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.IResourceLocator;



class ClassFileMarkerRulerAction extends MarkerRulerAction {


	public ClassFileMarkerRulerAction(String prefix, IVerticalRuler ruler, ITextEditor editor, String markerType, boolean askForLabel) {
		super(JavaEditorMessages.getBundleForConstructedKeys(), prefix, editor, ruler, markerType, askForLabel);
	}

	/**
	 * @see MarkerRulerAction#getResource()
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
	 * @see MarkerRulerAction#getInitialAttributes()
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

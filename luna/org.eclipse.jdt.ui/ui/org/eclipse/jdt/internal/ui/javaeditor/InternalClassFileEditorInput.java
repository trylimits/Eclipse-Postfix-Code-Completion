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


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.IPersistableElement;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;


/**
 * Class file considered as editor input.
 */
public class InternalClassFileEditorInput implements IClassFileEditorInput, IPersistableElement, IPathEditorInput {

	private IClassFile fClassFile;

	private IPath fPath;

	public InternalClassFileEditorInput(IClassFile classFile) {
		fClassFile= classFile;
	}

	/*
	 * @see Object#equals(Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof InternalClassFileEditorInput))
			return false;
		InternalClassFileEditorInput other= (InternalClassFileEditorInput) obj;
		return fClassFile.equals(other.fClassFile);
	}

	/*
	 * @see Object#hashCode
	 */
	@Override
	public int hashCode() {
		return fClassFile.hashCode();
	}

	/*
	 * @see IClassFileEditorInput#getClassFile()
	 */
	public IClassFile getClassFile() {
		return fClassFile;
	}

	/*
	 * @see IEditorInput#getPersistable()
	 */
	public IPersistableElement getPersistable() {
		return this;
	}

	/*
	 * @see IEditorInput#getName()
	 */
	public String getName() {
		return fClassFile.getElementName();
	}

	/*
	 * @see IEditorInput#getToolTipText()
	 */
	public String getToolTipText() {
		return fClassFile.getType().getFullyQualifiedName();
	}

	/*
	 * @see IEditorInput#getImageDescriptor()
	 */
	public ImageDescriptor getImageDescriptor() {
		try {
			if (fClassFile.isClass())
				return JavaPluginImages.DESC_OBJS_CFILECLASS;
			return JavaPluginImages.DESC_OBJS_CFILEINT;
		} catch (JavaModelException e) {
			// fall through
		}
		return JavaPluginImages.DESC_OBJS_CFILE;
	}

	/*
	 * @see IEditorInput#exists()
	 */
	public boolean exists() {
		return fClassFile.exists();
	}

	/*
	 * @see IAdaptable#getAdapter(Class)
	 */
	public Object getAdapter(Class adapter) {
		if (adapter == IClassFile.class)
			return fClassFile;
		else if (adapter == IJavaElement.class)
			return fClassFile;
		return null;
	}

	/*
	 * @see IPersistableElement#saveState(IMemento)
	 */
	public void saveState(IMemento memento) {
		ClassFileEditorInputFactory.saveState(memento, this);
	}

	/*
	 * @see IPersistableElement#getFactoryId()
	 */
	public String getFactoryId() {
		return ClassFileEditorInputFactory.ID;
	}

	/*
	 * @see org.eclipse.ui.IPathEditorInput#getPath()
	 * @since 3.7
	 */
	public IPath getPath() {
		if (fPath == null)
			fPath= writeToTempFile(fClassFile);
		return fPath;
	}

	private static IPath writeToTempFile(IClassFile classFile) {
		FileOutputStream writer= null;
		try {
			File file= File.createTempFile(classFile.getElementName(), ".class"); //$NON-NLS-1$
			byte[] bytes= classFile.getBytes();
			writer= new FileOutputStream(file);
			writer.write(bytes);
			return new Path(file.toString());
		} catch (IOException e) {
			JavaPlugin.log(e);
		} catch (CoreException e) {
			JavaPlugin.log(e.getStatus());
		} finally {
			if (writer != null)
				try {
					writer.close();
				} catch (IOException e) {
					JavaPlugin.log(e);
				}
		}
		throw new IllegalArgumentException("Could not create temporary file."); //$NON-NLS-1$
	}

}



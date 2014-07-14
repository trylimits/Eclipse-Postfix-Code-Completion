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


import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IStorage;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.IStorageEditorInput;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IPackageFragmentRoot;

import org.eclipse.jdt.internal.ui.JarEntryEditorInputFactory;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

/**
 * An EditorInput for a JarEntryFile.
 */
public class JarEntryEditorInput implements IStorageEditorInput {

	private final IStorage fJarEntryFile;

	public JarEntryEditorInput(IStorage jarEntryFile) {
		Assert.isNotNull(jarEntryFile);
		fJarEntryFile= jarEntryFile;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof JarEntryEditorInput))
			return false;
		JarEntryEditorInput other= (JarEntryEditorInput) obj;
		return fJarEntryFile.equals(other.fJarEntryFile);
	}

	@Override
	public int hashCode() {
		return fJarEntryFile.hashCode();
	}
	
	/*
	 * @see IEditorInput#getPersistable()
	 */
	public IPersistableElement getPersistable() {
		if (fJarEntryFile instanceof IJarEntryResource) {
			return new IPersistableElement() {
				public void saveState(IMemento memento) {
					JarEntryEditorInputFactory.saveState(memento, (IJarEntryResource) fJarEntryFile);
				}

				public String getFactoryId() {
					return JarEntryEditorInputFactory.FACTORY_ID;
				}
			};
		} else {
			return null;
		}
	}

	/*
	 * @see IEditorInput#getName()
	 */
	public String getName() {
		return fJarEntryFile.getName();
	}

	/*
	 * @see IEditorInput#getContentType()
	 */
	public String getContentType() {
		return fJarEntryFile.getFullPath().getFileExtension();
	}

	/*
	 * @see IEditorInput#getToolTipText()
	 */
	public String getToolTipText() {
		if (fJarEntryFile instanceof IJarEntryResource) {
			IJarEntryResource jarEntry= (IJarEntryResource)fJarEntryFile;
			IPackageFragmentRoot root= jarEntry.getPackageFragmentRoot();
			IPath fullPath= root.getPath().append(fJarEntryFile.getFullPath());
			return BasicElementLabels.getPathLabel(fullPath, root.isExternal());
		}

		IPath fullPath= fJarEntryFile.getFullPath();
		if (fullPath == null)
			return null;
		return BasicElementLabels.getPathLabel(fullPath, false);
	}

	/*
	 * @see IEditorInput#getImageDescriptor()
	 */
	public ImageDescriptor getImageDescriptor() {
		IEditorRegistry registry= PlatformUI.getWorkbench().getEditorRegistry();
		return registry.getImageDescriptor(getContentType());
	}

	/*
	 * @see IEditorInput#exists()
	 */
	public boolean exists() {
		// JAR entries can't be deleted
		return true;
	}

	/*
	 * @see IAdaptable#getAdapter(Class)
	 */
	public Object getAdapter(Class adapter) {
		return null;
	}

	/*
	 * see IStorageEditorInput#getStorage()
	 */
	 public IStorage getStorage() {
	 	return fJarEntryFile;
	 }
}



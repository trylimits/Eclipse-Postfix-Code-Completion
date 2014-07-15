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
package org.eclipse.jdt.internal.ui;

import java.util.ArrayList;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.ui.IElementFactory;
import org.eclipse.ui.IMemento;

import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.javaeditor.JarEntryEditorInput;

/**
 * The JarEntryEditorInputFactory is used to save and recreate {@link JarEntryEditorInput}s.
 *
 * @see IMemento
 * @see IElementFactory
 */
public class JarEntryEditorInputFactory implements IElementFactory {

	public static final String FACTORY_ID= "org.eclipse.jdt.ui.internal.JarEntryEditorInputFactory"; //$NON-NLS-1$
	private static final String KEY_ELEMENT= "element"; //$NON-NLS-1$
	private static final String KEY_PATH= "path"; //$NON-NLS-1$

	/**
	 * Public constructor for extension point.
	 */
	public JarEntryEditorInputFactory() {
	}

	/*
	 * @see org.eclipse.ui.IElementFactory#createElement(org.eclipse.ui.IMemento)
	 */
	public IAdaptable createElement(IMemento memento) {

		String rootIdentifier= memento.getString(KEY_ELEMENT);
		String pathIdentifier= memento.getString(KEY_PATH);
		if (rootIdentifier != null && pathIdentifier != null) {
			IJavaElement restoredParent= JavaCore.create(rootIdentifier);
			try {
				Object[] children;
				if (restoredParent instanceof IPackageFragmentRoot) {
					IPackageFragmentRoot restoredRoot= (IPackageFragmentRoot) restoredParent;
					if (!restoredParent.exists()) {
						restoredRoot= fuzzyResolveRoot(restoredRoot);
						if (restoredRoot == null) {
							return null;
						}
					}
					children= restoredRoot.getNonJavaResources();

				} else if (restoredParent instanceof IPackageFragment) {
					IPackageFragment restoredPackage= (IPackageFragment) restoredParent;
					if (!restoredPackage.exists()) {
						IPackageFragmentRoot restoredRoot= (IPackageFragmentRoot) restoredPackage.getParent();
						restoredRoot= fuzzyResolveRoot(restoredRoot);
						if (restoredRoot == null) {
							return null;
						}
						restoredPackage= restoredRoot.getPackageFragment(restoredPackage.getElementName());
						if (!restoredPackage.exists()) {
							return null;
						}
					}
					children= restoredPackage.getNonJavaResources();
				} else {
					return null; // should not happen
				}

				String[] pathSegments= new Path(pathIdentifier).segments();
				return createEditorInput(pathSegments, children);
			} catch (JavaModelException e) {
				return null;
			}
		}
		return null;
	}

	private IPackageFragmentRoot fuzzyResolveRoot(IPackageFragmentRoot restoredRoot) throws JavaModelException {
		IJavaProject project= restoredRoot.getJavaProject();
		String rootName= restoredRoot.getElementName();
		int versionSepIndex= rootName.indexOf('_');
		if (versionSepIndex > 0) {
			// try stripping plug-in version number
			String prefix= rootName.substring(0, versionSepIndex);
			IPackageFragmentRoot[] roots= project.getPackageFragmentRoots();
			for (int i= 0; i < roots.length; i++) {
				if (roots[i].getElementName().startsWith(prefix)) {
					return roots[i];
				}
			}
		}
		return null;
	}

	private JarEntryEditorInput createEditorInput(String[] pathSegments, Object[] children) {
		int depth= pathSegments.length;
		segments: for (int i= 0; i < depth; i++) {
			String name= pathSegments[i];
			for (int j= 0; j < children.length; j++) {
				Object child= children[j];
				if (child instanceof IJarEntryResource) {
					IJarEntryResource jarEntryResource= (IJarEntryResource) child;
					if (name.equals(jarEntryResource.getName())) {
						boolean isFile= jarEntryResource.isFile();
						if (isFile) {
							if (i == depth - 1) {
								return new JarEntryEditorInput(jarEntryResource);
							} else {
								return null; // got a file for a directory name
							}
						} else {
							children= jarEntryResource.getChildren();
							continue segments;
						}
					}
				}
			}
			return null; // no child found on this level
		}
		return null;
	}

	/*
	 * @see IPersistableElement#saveState(IMemento)
	 */
	public static void saveState(IMemento memento, IJarEntryResource jarEntryResource) {
		ArrayList<String> reversePath= new ArrayList<String>();
		reversePath.add(jarEntryResource.getName());

		Object parent= jarEntryResource.getParent();
		while (parent instanceof IJarEntryResource) {
			jarEntryResource= (IJarEntryResource) parent;
			reversePath.add(jarEntryResource.getName());
			parent= jarEntryResource.getParent();
		}
		if (parent instanceof IPackageFragmentRoot || parent instanceof IPackageFragment) {
			memento.putString(KEY_ELEMENT, ((IJavaElement) parent).getHandleIdentifier());
			IPath path= new Path(reversePath.get(reversePath.size() - 1));
			for (int i= reversePath.size() - 2; i >= 0; i--) {
				path= path.append(reversePath.get(i));
			}
			memento.putString(KEY_PATH, path.toString());
		}
	}
}

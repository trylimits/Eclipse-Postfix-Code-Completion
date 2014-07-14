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
package org.eclipse.jdt.internal.ui.packageview;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.model.IWorkbenchAdapter;

import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

/**
 * Representation of class path containers in Java UI.
 */
public class ClassPathContainer extends PackageFragmentRootContainer {

	private IClasspathEntry fClassPathEntry;
	private IClasspathContainer fContainer;

	public static class RequiredProjectWrapper implements IAdaptable, IWorkbenchAdapter {

		private final ClassPathContainer fParent;
		private final IJavaProject fProject;

		public RequiredProjectWrapper(ClassPathContainer parent, IJavaProject project) {
			fParent= parent;
			fProject= project;
		}

		public IJavaProject getProject() {
			return fProject;
		}

		public ClassPathContainer getParentClassPathContainer() {
			return fParent;
		}

		public Object getAdapter(Class adapter) {
			if (adapter == IWorkbenchAdapter.class)
				return this;
			return null;
		}

		public Object[] getChildren(Object o) {
			return new Object[0];
		}

		public ImageDescriptor getImageDescriptor(Object object) {
			return PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(IDE.SharedImages.IMG_OBJ_PROJECT);
		}

		public String getLabel(Object o) {
			return fProject.getElementName();
		}

		public Object getParent(Object o) {
			return fParent;
		}
	}

	public ClassPathContainer(IJavaProject parent, IClasspathEntry entry) {
		super(parent);
		fClassPathEntry= entry;
		try {
			fContainer= JavaCore.getClasspathContainer(entry.getPath(), parent);
		} catch (JavaModelException e) {
			fContainer= null;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ClassPathContainer) {
			ClassPathContainer other = (ClassPathContainer)obj;
			if (getJavaProject().equals(other.getJavaProject()) &&
				fClassPathEntry.equals(other.fClassPathEntry)) {
				return true;
			}

		}
		return false;
	}

	@Override
	public int hashCode() {
		return getJavaProject().hashCode()*17+fClassPathEntry.hashCode();
	}

	@Override
	public IPackageFragmentRoot[] getPackageFragmentRoots() {
		return getJavaProject().findPackageFragmentRoots(fClassPathEntry);
	}

	@Override
	public IAdaptable[] getChildren() {
		List<IAdaptable> list= new ArrayList<IAdaptable>();
		IPackageFragmentRoot[] roots= getPackageFragmentRoots();
		for (int i= 0; i < roots.length; i++) {
			list.add(roots[i]);
		}
		if (fContainer != null) {
			IClasspathEntry[] classpathEntries= fContainer.getClasspathEntries();
			if (classpathEntries == null) {
				// invalid implementation of a classpath container
				JavaPlugin.log(new IllegalArgumentException("Invalid classpath container implementation: getClasspathEntries() returns null. " + fContainer.getPath())); //$NON-NLS-1$
			} else {
				IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
				for (int i= 0; i < classpathEntries.length; i++) {
					IClasspathEntry entry= classpathEntries[i];
					if (entry.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
						IResource resource= root.findMember(entry.getPath());
						if (resource instanceof IProject)
							list.add(new RequiredProjectWrapper(this, JavaCore.create((IProject) resource)));
					}
				}
			}
		}
		return list.toArray(new IAdaptable[list.size()]);
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return JavaPluginImages.DESC_OBJS_LIBRARY;
	}

	@Override
	public String getLabel() {
		if (fContainer != null)
			return fContainer.getDescription();

		IPath path= fClassPathEntry.getPath();
		String containerId= path.segment(0);
		ClasspathContainerInitializer initializer= JavaCore.getClasspathContainerInitializer(containerId);
		if (initializer != null) {
			String description= initializer.getDescription(path, getJavaProject());
			return Messages.format(PackagesMessages.ClassPathContainer_unbound_label, description);
		}
		return Messages.format(PackagesMessages.ClassPathContainer_unknown_label, BasicElementLabels.getPathLabel(path, false));
	}

	public IClasspathEntry getClasspathEntry() {
		return fClassPathEntry;
	}

	static boolean contains(IJavaProject project, IClasspathEntry entry, IPackageFragmentRoot root) {
		IPackageFragmentRoot[] roots= project.findPackageFragmentRoots(entry);
		for (int i= 0; i < roots.length; i++) {
			if (roots[i].equals(root))
				return true;
		}
		return false;
	}

}

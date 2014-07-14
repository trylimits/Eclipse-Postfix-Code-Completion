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

import org.osgi.framework.Bundle;

import org.eclipse.team.ui.history.IHistoryPageSource;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.core.runtime.Platform;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.mapping.ResourceMapping;

import org.eclipse.ui.IContainmentAdapter;
import org.eclipse.ui.IContributorResourceAdapter;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.ide.IContributorResourceAdapter2;
import org.eclipse.ui.model.IWorkbenchAdapter;
import org.eclipse.ui.views.properties.FilePropertySource;
import org.eclipse.ui.views.properties.IPropertySource;
import org.eclipse.ui.views.properties.ResourcePropertySource;
import org.eclipse.ui.views.tasklist.ITaskListResourceAdapter;

import org.eclipse.search.ui.ISearchPageScoreComputer;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;

import org.eclipse.jdt.internal.corext.util.JavaElementResourceMapping;

import org.eclipse.jdt.internal.ui.compare.JavaElementHistoryPageSource;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.jdt.internal.ui.search.JavaSearchPageScoreComputer;
import org.eclipse.jdt.internal.ui.search.SearchUtil;


/**
 * Implements basic UI support for Java elements.
 * Implements handle to persistent support for Java elements.
 */
public class JavaElementAdapterFactory implements IAdapterFactory, IContributorResourceAdapter2 {

	private static Class<?>[] ADAPTER_LIST= new Class[] {
		IPropertySource.class,
		IResource.class,
		IWorkbenchAdapter.class,
		IResourceLocator.class,
		IPersistableElement.class,
		IContributorResourceAdapter.class,
		IContributorResourceAdapter2.class,
		ITaskListResourceAdapter.class,
		IContainmentAdapter.class
	};

	/*
	 * Do not use real type since this would cause
	 * the Search plug-in to be loaded.
	 */
	private Object fSearchPageScoreComputer;

	private boolean fIsTeamUILoaded;
	private static IResourceLocator fgResourceLocator;
	private static JavaWorkbenchAdapter fgJavaWorkbenchAdapter;
	private static ITaskListResourceAdapter fgTaskListAdapter;
	private static JavaElementContainmentAdapter fgJavaElementContainmentAdapter;

	public Class[] getAdapterList() {
		updateLazyLoadedAdapters();
		return ADAPTER_LIST;
	}

	public Object getAdapter(Object element, Class key) {
		updateLazyLoadedAdapters();
		IJavaElement java= getJavaElement(element);

		if (IPropertySource.class.equals(key)) {
			return getProperties(java);
		} if (IResource.class.equals(key)) {
			return getResource(java);
		} if (fSearchPageScoreComputer != null && ISearchPageScoreComputer.class.equals(key)) {
			return fSearchPageScoreComputer;
		} if (IWorkbenchAdapter.class.equals(key)) {
			return getJavaWorkbenchAdapter();
		} if (IResourceLocator.class.equals(key)) {
			return getResourceLocator();
		} if (IPersistableElement.class.equals(key)) {
			return new PersistableJavaElementFactory(java);
		} if (IContributorResourceAdapter.class.equals(key)) {
			return this;
		} if (IContributorResourceAdapter2.class.equals(key)) {
			return this;
		} if (ITaskListResourceAdapter.class.equals(key)) {
			return getTaskListAdapter();
		} if (IContainmentAdapter.class.equals(key)) {
			return getJavaElementContainmentAdapter();
		} if (fIsTeamUILoaded && IHistoryPageSource.class.equals(key) && JavaElementHistoryPageSource.hasEdition(java)) {
			return JavaElementHistoryPageSource.getInstance();
		}
		return null;
	}

	private IResource getResource(IJavaElement element) {
		// can't use IJavaElement.getResource directly as we are interested in the
		// corresponding resource
		switch (element.getElementType()) {
			case IJavaElement.TYPE:
				// top level types behave like the CU
				IJavaElement parent= element.getParent();
				if (parent instanceof ICompilationUnit) {
					return ((ICompilationUnit) parent).getPrimary().getResource();
				}
				return null;
			case IJavaElement.COMPILATION_UNIT:
				return ((ICompilationUnit) element).getPrimary().getResource();
			case IJavaElement.CLASS_FILE:
			case IJavaElement.PACKAGE_FRAGMENT:
				// test if in a archive
				IPackageFragmentRoot root= (IPackageFragmentRoot) element.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
				if (!root.isArchive() && !root.isExternal()) {
					return element.getResource();
				}
				return null;
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
			case IJavaElement.JAVA_PROJECT:
			case IJavaElement.JAVA_MODEL:
				return element.getResource();
			default:
				return null;
		}
    }

    public IResource getAdaptedResource(IAdaptable adaptable) {
    	IJavaElement je= getJavaElement(adaptable);
    	if (je != null)
    		return getResource(je);

    	return null;
    }

    public ResourceMapping getAdaptedResourceMapping(IAdaptable adaptable) {
    	IJavaElement je= getJavaElement(adaptable);
    	if (je != null)
    		return JavaElementResourceMapping.create(je);

    	return null;
    }

	private IJavaElement getJavaElement(Object element) {
		if (element instanceof IJavaElement)
			return (IJavaElement)element;
		if (element instanceof IClassFileEditorInput)
			return ((IClassFileEditorInput)element).getClassFile().getPrimaryElement();

		return null;
	}

	private IPropertySource getProperties(IJavaElement element) {
		IResource resource= getResource(element);
		if (resource == null)
			return new JavaElementProperties(element);
		if (resource.getType() == IResource.FILE)
			return new FilePropertySource((IFile) resource);
		return new ResourcePropertySource(resource);
	}

	private void updateLazyLoadedAdapters() {
		if (fSearchPageScoreComputer == null && SearchUtil.isSearchPlugInActivated())
			createSearchPageScoreComputer();
		if (!fIsTeamUILoaded && isTeamUIPlugInActivated()) {
			addClassToAdapterList(IHistoryPageSource.class);
			fIsTeamUILoaded= true;
		}
	}

	private void createSearchPageScoreComputer() {
		fSearchPageScoreComputer= new JavaSearchPageScoreComputer();
		addClassToAdapterList(ISearchPageScoreComputer.class);
	}

	private static void addClassToAdapterList(Class<?> clazz) {
		int oldSize= ADAPTER_LIST.length;
		Class<?>[] oldProperties= ADAPTER_LIST;
		ADAPTER_LIST= new Class[oldSize + 1];
		System.arraycopy(oldProperties, 0, ADAPTER_LIST, 0, oldSize);
		ADAPTER_LIST[oldSize]= clazz;
	}

	private static boolean isTeamUIPlugInActivated() {
		return Platform.getBundle("org.eclipse.team.ui").getState() == Bundle.ACTIVE; //$NON-NLS-1$
	}

	private static IResourceLocator getResourceLocator() {
		if (fgResourceLocator == null)
			fgResourceLocator= new ResourceLocator();
		return fgResourceLocator;
	}

	private static JavaWorkbenchAdapter getJavaWorkbenchAdapter() {
		if (fgJavaWorkbenchAdapter == null)
			fgJavaWorkbenchAdapter= new JavaWorkbenchAdapter();
		return fgJavaWorkbenchAdapter;
	}

	private static ITaskListResourceAdapter getTaskListAdapter() {
		if (fgTaskListAdapter == null)
			fgTaskListAdapter= new JavaTaskListAdapter();
		return fgTaskListAdapter;
	}

	private static JavaElementContainmentAdapter getJavaElementContainmentAdapter() {
		if (fgJavaElementContainmentAdapter == null)
			fgJavaElementContainmentAdapter= new JavaElementContainmentAdapter();
		return fgJavaElementContainmentAdapter;
	}
}

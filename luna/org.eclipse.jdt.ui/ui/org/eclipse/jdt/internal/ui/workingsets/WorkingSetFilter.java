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
package org.eclipse.jdt.internal.ui.workingsets;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.viewers.Viewer;

import org.eclipse.ui.IWorkingSet;

import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.ui.packageview.PackageFragmentRootContainer;
import org.eclipse.jdt.internal.ui.packageview.ClassPathContainer.RequiredProjectWrapper;
import org.eclipse.jdt.internal.ui.viewsupport.JavaViewerFilter;

/**
 * Working set filter for Java viewers.
 */
public class WorkingSetFilter extends JavaViewerFilter {


	private static class WorkingSetCompareEntry {

		/**
		 * Denotes an {@link IJarEntryResource} if it is
		 * <code>null &amp;&amp; fJavaElement != null.</code>
		 */
		private IPath fResourcePath;
		private IJavaElement fJavaElement;


		public WorkingSetCompareEntry(IAdaptable a) {
			if (a instanceof IJavaElement) {
				init((IJavaElement) a);
			} else if (a instanceof IResource) {
				init((IResource) a);
			} else if (a instanceof RequiredProjectWrapper) {
				RequiredProjectWrapper wrapper= (RequiredProjectWrapper) a;
				IJavaProject proj= wrapper.getParentClassPathContainer().getJavaProject();
				// the project reference is treated like an internal JAR.
				// that means it will only appear if the parent container project is in the working set
				IResource fakeInternal= proj.getProject().getFile(wrapper.getProject().getElementName() + "-fake-jar.jar"); //$NON-NLS-1$
				init(proj.getPackageFragmentRoot(fakeInternal));
			} else if (a instanceof IJarEntryResource) {
				init((IJarEntryResource)a);
			} else {
				IJavaElement je= (IJavaElement) a.getAdapter(IJavaElement.class);
				if (je != null) {
					init(je);
				} else {
					IResource resource= (IResource) a.getAdapter(IResource.class);
					if (resource != null) {
						init(resource);
					} else {
						fResourcePath= null;
						fJavaElement= null;
					}
				}
			}
		}

		private void init(IResource resource) {
			fJavaElement= JavaCore.create(resource);
			fResourcePath= resource.getFullPath();
		}

		private void init(IJavaElement curr) {
			fJavaElement= curr;
			fResourcePath= curr.getPath();
		}

		private void init(IJarEntryResource jarEntryResource) {
			Object parent= jarEntryResource.getParent();
			while (parent instanceof IJarEntryResource)
				parent= ((IJarEntryResource)parent).getParent();
			fJavaElement= (IJavaElement)parent;
			fResourcePath= null;
		}

		public boolean contains(WorkingSetCompareEntry element) {
			if (fJavaElement != null && element.fJavaElement != null) {
				IJavaElement other= element.fJavaElement;
				if (fJavaElement.getElementType() == IJavaElement.JAVA_PROJECT) {
					IPackageFragmentRoot pkgRoot= (IPackageFragmentRoot) other.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
					if (pkgRoot != null && pkgRoot.isExternal()) {
						if (((IJavaProject) fJavaElement).isOnClasspath(other)) {
							return true;
						}
					}
				}

				// Check relationship in both directions except for IJarFileEntryResource which cannot be part of a working set
				if (isAncestor(other, fJavaElement) || isAncestor(fJavaElement, other) && element.fResourcePath != null) {
					return true;
				}
				return false;
			}
			if (fResourcePath != null && element.fResourcePath != null) {
				IPath other= element.fResourcePath;
				if (other.isPrefixOf(fResourcePath) || fResourcePath.isPrefixOf(other))
					return true;
			}
			return false;
		}

		/**
		 * Check whether the given parent is an ancestor of the given element
		 * or the same as the element.
		 * 
		 * @param elem the element
		 * @param parent the anchestor
		 * @return <code>true</code> if it is an ancestor
		 */
		private boolean isAncestor(IJavaElement elem, IJavaElement parent) {
			IJavaElement anc= elem.getAncestor(parent.getElementType());
			if (parent.equals(anc)) {
				return true;
			}
			while (anc instanceof IMember) { // ITypes can be in ITypes
				anc= anc.getParent().getAncestor(parent.getElementType());
				if (parent.equals(anc)) {
					return true;
				}
			}
			return false;
		}
	}

	private IWorkingSet fWorkingSet;

	private WorkingSetCompareEntry[] fCachedCompareEntries;

	public WorkingSetFilter() {
		fWorkingSet= null;
		fCachedCompareEntries= null;
	}

	/**
	 * Returns the working set which is used by this filter.
	 *
	 * @return the working set
	 */
	public IWorkingSet getWorkingSet() {
		return fWorkingSet;
	}

	/**
	 * Sets this filter's working set.
	 *
	 * @param workingSet the working set
	 */
	public void setWorkingSet(IWorkingSet workingSet) {
		if (fWorkingSet != workingSet) {
			fWorkingSet= workingSet;
			notifyWorkingSetContentChange();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.filters.JavaViewerFilter#initFilter()
	 */
	@Override
	protected void initFilter() {
		notifyWorkingSetContentChange();
	}

	/**
	 * Invoke when the content of the current working set changed. Clients are responsible to listen to changes and call this method.
	 */
	public final void notifyWorkingSetContentChange() {
		if (fWorkingSet != null) {
			IAdaptable[] elements= fWorkingSet.getElements();
			fCachedCompareEntries= new WorkingSetCompareEntry[elements.length];
			for (int i= 0; i < elements.length; i++) {
				fCachedCompareEntries[i]= new WorkingSetCompareEntry(elements[i]);
			}
		} else {
			fCachedCompareEntries= null;
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.filters.JavaViewerFilter#freeFilter()
	 */
	@Override
	protected void freeFilter() {
		fCachedCompareEntries= null;
	}

	/*
	 * Overrides method from ViewerFilter.
	 */
	@Override
	public boolean select(Viewer viewer, Object parentElement, Object element) {
		if (fWorkingSet == null || (fWorkingSet.isAggregateWorkingSet() && fWorkingSet.isEmpty()))
			return true;

		if (element instanceof PackageFragmentRootContainer) {
			return isEnclosing((PackageFragmentRootContainer)element);
		}

		if (element instanceof IAdaptable)
			return isEnclosing((IAdaptable)element);

		return true;
	}

	public boolean isEnclosing(IAdaptable a) {
		WorkingSetCompareEntry curr= new WorkingSetCompareEntry(a);
		if (fCachedCompareEntries != null) {
			for (int i= 0; i < fCachedCompareEntries.length; i++) {
				if (fCachedCompareEntries[i].contains(curr)) {
					return  true;
				}
			}
			return false;
		}
		if (fWorkingSet != null) {
			IAdaptable[] elements= fWorkingSet.getElements();
			for (int i= 0; i < elements.length; i++) {
				if (new WorkingSetCompareEntry(elements[i]).contains(curr)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isEnclosing(PackageFragmentRootContainer container) {
		// check whether the containing package fragment roots are enclosed
		IAdaptable[] roots= container.getChildren();
		for (int i= 0; i < roots.length; i++) {
			if (isEnclosing(roots[i])) {
				return true;
			}
		}
		return false;
	}

}

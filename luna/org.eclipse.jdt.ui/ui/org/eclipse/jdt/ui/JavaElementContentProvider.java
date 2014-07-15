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
package org.eclipse.jdt.ui;

import org.eclipse.swt.widgets.Control;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;

import org.eclipse.jface.viewers.IBasicPropertyConstants;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * A tree content provider for Java elements. It extends the
 * StandardJavaElementContentProvider with support for listening to changes.
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * @deprecated use the StandardJavaElementContentProvider instead
 * @see StandardJavaElementContentProvider
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class JavaElementContentProvider extends StandardJavaElementContentProvider implements IElementChangedListener {

	/** The tree viewer */
	protected TreeViewer fViewer;
	/** The input object */
	protected Object fInput;

	/* (non-Javadoc)
	 * Method declared on IContentProvider.
	 */
	@Override
	public void dispose() {
		super.dispose();
		JavaCore.removeElementChangedListener(this);
	}

	/* (non-Javadoc)
	 * Method declared on IContentProvider.
	 */
	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		super.inputChanged(viewer, oldInput, newInput);
		fViewer= (TreeViewer)viewer;
		if (oldInput == null && newInput != null) {
			JavaCore.addElementChangedListener(this);
		} else if (oldInput != null && newInput == null) {
			JavaCore.removeElementChangedListener(this);
		}
		fInput= newInput;
	}
	/**
	 * Creates a new content provider for Java elements.
	 */
	public JavaElementContentProvider() {
	}

	/**
	 * Creates a new content provider for Java elements.
	 *
	 * @param provideMembers if <code>true</code> members below compilation units
	 * and class files are provided.
	 * @param provideWorkingCopy if <code>true</code> the element provider provides
	 * working copies for members of compilation units which have an associated working
	 * copy. Otherwise only original elements are provided.
	 *
	 * @since 2.0
	 */
	public JavaElementContentProvider(boolean provideMembers, boolean provideWorkingCopy) {
		super(provideMembers, provideWorkingCopy);
	}

	/* (non-Javadoc)
	 * Method declared on IElementChangedListener.
	 */
	public void elementChanged(final ElementChangedEvent event) {
		try {
			processDelta(event.getDelta());
		} catch(JavaModelException e) {
			JavaPlugin.log(e);
		}
	}

	/**
	 * Processes a delta recursively. When more than two children are affected the
	 * tree is fully refreshed starting at this node. The delta is processed in the
	 * current thread but the viewer updates are posted to the UI thread.
	 *
	 * @param delta the delta to be processed
	 *
	 * @throws JavaModelException if an error occurs while processing the delta
	 */
	protected void processDelta(IJavaElementDelta delta) throws JavaModelException {
		int kind= delta.getKind();
		int flags= delta.getFlags();
		IJavaElement element= delta.getElement();

		if (element instanceof ICompilationUnit) {
			if (!getProvideWorkingCopy())
				return;

			ICompilationUnit cu= (ICompilationUnit) element;
			if (!JavaModelUtil.isPrimary(cu) || !isOnClassPath((ICompilationUnit)element)) {
				return;
			}
		}

		// handle open and closing of a solution or project
		if (((flags & IJavaElementDelta.F_CLOSED) != 0) || ((flags & IJavaElementDelta.F_OPENED) != 0)) {
			postRefresh(element);
			return;
		}

		if (kind == IJavaElementDelta.REMOVED) {
			Object parent= internalGetParent(element);
			postRemove(element);
			if (parent instanceof IPackageFragment)
				updatePackageIcon((IPackageFragment)parent);
			// we are filtering out empty subpackages, so we
			// a package becomes empty we remove it from the viewer.
			if (isPackageFragmentEmpty(element.getParent())) {
				if (fViewer.testFindItem(parent) != null)
					postRefresh(internalGetParent(parent));
			}
			return;
		}

		if (kind == IJavaElementDelta.ADDED) {
			Object parent= internalGetParent(element);
			// we are filtering out empty subpackages, so we
			// have to handle additions to them specially.
			if (parent instanceof IPackageFragment) {
				Object grandparent= internalGetParent(parent);
				// 1GE8SI6: ITPJUI:WIN98 - Rename is not shown in Packages View
				// avoid posting a refresh to an invisible parent
				if (parent.equals(fInput)) {
					postRefresh(parent);
				} else {
					// refresh from grandparent if parent isn't visible yet
					if (fViewer.testFindItem(parent) == null)
						postRefresh(grandparent);
					else {
						postRefresh(parent);
					}
				}
			} else {
				postAdd(parent, element);
			}
		}

		if (element instanceof ICompilationUnit) {
			if (kind == IJavaElementDelta.CHANGED) {
				postRefresh(element);
				return;
			}
		}
		// we don't show the contents of a compilation or IClassFile, so don't go any deeper
		if ((element instanceof ICompilationUnit) || (element instanceof IClassFile))
			return;

		// the contents of an external JAR has changed
		if (element instanceof IPackageFragmentRoot && ((flags & IJavaElementDelta.F_ARCHIVE_CONTENT_CHANGED) != 0))
			postRefresh(element);

		if (isClassPathChange(delta)) {
			 // throw the towel and do a full refresh of the affected java project.
			postRefresh(element.getJavaProject());
		}

		if (delta.getResourceDeltas() != null) {
			IResourceDelta[] rd= delta.getResourceDeltas();
			for (int i= 0; i < rd.length; i++) {
				processResourceDelta(rd[i], element);
			}
		}

		IJavaElementDelta[] affectedChildren= delta.getAffectedChildren();
		if (affectedChildren.length > 1) {
			// a package fragment might become non empty refresh from the parent
			if (element instanceof IPackageFragment) {
				IJavaElement parent= (IJavaElement)internalGetParent(element);
				// 1GE8SI6: ITPJUI:WIN98 - Rename is not shown in Packages View
				// avoid posting a refresh to an invisible parent
				if (element.equals(fInput)) {
					postRefresh(element);
				} else {
					postRefresh(parent);
				}
				return;
			}
			// more than one child changed, refresh from here downwards
			if (element instanceof IPackageFragmentRoot)
				postRefresh(skipProjectPackageFragmentRoot((IPackageFragmentRoot)element));
			else
				postRefresh(element);
			return;
		}
		for (int i= 0; i < affectedChildren.length; i++) {
			processDelta(affectedChildren[i]);
		}
	}

	private boolean isOnClassPath(ICompilationUnit element) {
		IJavaProject project= element.getJavaProject();
		if (project == null || !project.exists())
			return false;
		return project.isOnClasspath(element);
	}


	/*
	 * Updates the package icon
	 */
	 private void updatePackageIcon(final IJavaElement element) {
	 	postRunnable(new Runnable() {
			public void run() {
				// 1GF87WR: ITPUI:ALL - SWTEx + NPE closing a workbench window.
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed())
					fViewer.update(element, new String[]{IBasicPropertyConstants.P_IMAGE});
			}
		});
	 }

	/*
	 * Process resource deltas
	 */
	private void processResourceDelta(IResourceDelta delta, Object parent) {
		int status= delta.getKind();
		IResource resource= delta.getResource();
		// filter out changes affecting the output folder
		if (resource == null)
			return;

		// this could be optimized by handling all the added children in the parent
		if ((status & IResourceDelta.REMOVED) != 0) {
			if (parent instanceof IPackageFragment)
				// refresh one level above to deal with empty package filtering properly
				postRefresh(internalGetParent(parent));
			else
				postRemove(resource);
		}
		if ((status & IResourceDelta.ADDED) != 0) {
			if (parent instanceof IPackageFragment)
				// refresh one level above to deal with empty package filtering properly
				postRefresh(internalGetParent(parent));
			else
				postAdd(parent, resource);
		}
		IResourceDelta[] affectedChildren= delta.getAffectedChildren();

		if (affectedChildren.length > 1) {
			// more than one child changed, refresh from here downwards
			postRefresh(resource);
			return;
		}

		for (int i= 0; i < affectedChildren.length; i++)
			processResourceDelta(affectedChildren[i], resource);
	}

	private void postRefresh(final Object root) {
		postRunnable(new Runnable() {
			public void run() {
				// 1GF87WR: ITPUI:ALL - SWTEx + NPE closing a workbench window.
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed())
					fViewer.refresh(root);
			}
		});
	}

	private void postAdd(final Object parent, final Object element) {
		postRunnable(new Runnable() {
			public void run() {
				// 1GF87WR: ITPUI:ALL - SWTEx + NPE closing a workbench window.
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed())
					fViewer.add(parent, element);
			}
		});
	}

	private void postRemove(final Object element) {
		postRunnable(new Runnable() {
			public void run() {
				// 1GF87WR: ITPUI:ALL - SWTEx + NPE closing a workbench window.
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed())
					fViewer.remove(element);
			}
		});
	}

	private void postRunnable(final Runnable r) {
		Control ctrl= fViewer.getControl();
		if (ctrl != null && !ctrl.isDisposed()) {
			ctrl.getDisplay().asyncExec(r);
		}
	}
}

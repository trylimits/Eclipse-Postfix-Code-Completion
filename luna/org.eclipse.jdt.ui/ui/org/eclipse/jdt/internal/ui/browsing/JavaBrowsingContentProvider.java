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
package org.eclipse.jdt.internal.ui.browsing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.IBasicPropertyConstants;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IImportContainer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.IWorkingCopy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.StandardJavaElementContentProvider;

import org.eclipse.jdt.internal.ui.JavaPlugin;

class JavaBrowsingContentProvider extends StandardJavaElementContentProvider implements IElementChangedListener {

	private StructuredViewer fViewer;
	private Object fInput;
	private JavaBrowsingPart fBrowsingPart;
	private int fReadsInDisplayThread;


	public JavaBrowsingContentProvider(boolean provideMembers, JavaBrowsingPart browsingPart) {
		super(provideMembers);
		fBrowsingPart= browsingPart;
		fViewer= fBrowsingPart.getViewer();
		JavaCore.addElementChangedListener(this);
	}

	@Override
	public boolean hasChildren(Object element) {
		startReadInDisplayThread();
		try{
			return super.hasChildren(element);
		} finally {
			finishedReadInDisplayThread();
		}
	}

	@Override
	public Object[] getChildren(Object element) {
		if (!exists(element))
			return NO_CHILDREN;

		startReadInDisplayThread();
		try {
			if (element instanceof Collection) {
				Collection<?> elements= (Collection<?>)element;
				if (elements.isEmpty())
					return NO_CHILDREN;
				Object[] result= new Object[0];
				Iterator<?> iter= ((Collection<?>)element).iterator();
				while (iter.hasNext()) {
					Object[] children= getChildren(iter.next());
					if (children != NO_CHILDREN)
						result= concatenate(result, children);
				}
				return result;
			}
			if (element instanceof IPackageFragment)
				return getPackageContents((IPackageFragment)element);
			if (fProvideMembers && element instanceof IType)
				return getChildren((IType)element);
			if (fProvideMembers && element instanceof ISourceReference && element instanceof IParent)
				return removeImportAndPackageDeclarations(super.getChildren(element));
			if (element instanceof IJavaProject)
				return getPackageFragmentRoots((IJavaProject)element);
			return super.getChildren(element);
		} catch (JavaModelException e) {
			return NO_CHILDREN;
		} finally {
			finishedReadInDisplayThread();
		}
	}

	private Object[] getPackageContents(IPackageFragment fragment) throws JavaModelException {
		ISourceReference[] sourceRefs;
		if (fragment.getKind() == IPackageFragmentRoot.K_SOURCE) {
			sourceRefs= fragment.getCompilationUnits();
		}
		else {
			IClassFile[] classFiles= fragment.getClassFiles();
			List<IClassFile> topLevelClassFile= new ArrayList<IClassFile>();
			for (int i= 0; i < classFiles.length; i++) {
				IType type= classFiles[i].getType();
				if (type != null && type.getDeclaringType() == null && !type.isAnonymous() && !type.isLocal())
					topLevelClassFile.add(classFiles[i]);
			}
			sourceRefs= topLevelClassFile.toArray(new ISourceReference[topLevelClassFile.size()]);
		}

		Object[] result= new Object[0];
		for (int i= 0; i < sourceRefs.length; i++)
			result= concatenate(result, removeImportAndPackageDeclarations(getChildren(sourceRefs[i])));
		return concatenate(result, fragment.getNonJavaResources());
	}

	private Object[] removeImportAndPackageDeclarations(Object[] members) {
		ArrayList<Object> tempResult= new ArrayList<Object>(members.length);
		for (int i= 0; i < members.length; i++)
			if (!(members[i] instanceof IImportContainer) && !(members[i] instanceof IPackageDeclaration))
				tempResult.add(members[i]);
		return tempResult.toArray();
	}

	private Object[] getChildren(IType type) throws JavaModelException{
		IParent parent;
		if (type.isBinary())
			parent= type.getClassFile();
		else {
			parent= type.getCompilationUnit();
		}
		if (type.getDeclaringType() != null)
			return type.getChildren();

		// Add import declarations
		IJavaElement[] members= parent.getChildren();
		ArrayList<IJavaElement> tempResult= new ArrayList<IJavaElement>(members.length);
		for (int i= 0; i < members.length; i++)
			if ((members[i] instanceof IImportContainer))
				tempResult.add(members[i]);
		tempResult.addAll(Arrays.asList(type.getChildren()));
		return tempResult.toArray();
	}

	@Override
	protected Object[] getPackageFragmentRoots(IJavaProject project) throws JavaModelException {
		if (!project.getProject().isOpen())
			return NO_CHILDREN;

		IPackageFragmentRoot[] roots= project.getPackageFragmentRoots();
		List<IJavaElement> list= new ArrayList<IJavaElement>(roots.length);
		// filter out package fragments that correspond to projects and
		// replace them with the package fragments directly
		for (int i= 0; i < roots.length; i++) {
			IPackageFragmentRoot root= roots[i];
			if (!root.isExternal()) {
				IJavaElement[] children= root.getChildren();
				for (int k= 0; k < children.length; k++)
					list.add(children[k]);
			}
			else if (hasChildren(root)) {
				list.add(root);
			}
		}
		return concatenate(list.toArray(), project.getNonJavaResources());
	}

	// ---------------- Element change handling

	/* (non-Javadoc)
	 * Method declared on IContentProvider.
	 */
	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		super.inputChanged(viewer, oldInput, newInput);

		if (newInput instanceof Collection) {
			// Get a template object from the collection
			Collection<?> col= (Collection<?>)newInput;
			if (!col.isEmpty())
				newInput= col.iterator().next();
			else
				newInput= null;
		}
		fInput= newInput;
	}

	/* (non-Javadoc)
	 * Method declared on IContentProvider.
	 */
	@Override
	public void dispose() {
		super.dispose();
		JavaCore.removeElementChangedListener(this);
	}

	/* (non-Javadoc)
	 * Method declared on IElementChangedListener.
	 */
	public void elementChanged(final ElementChangedEvent event) {
		try {
			processDelta(event.getDelta());
		} catch(JavaModelException e) {
			JavaPlugin.log(e.getStatus());
		}
	}


	/**
	 * Processes a delta recursively. When more than two children are affected the
	 * tree is fully refreshed starting at this node. The delta is processed in the
	 * current thread but the viewer updates are posted to the UI thread.
	 */
	protected void processDelta(IJavaElementDelta delta) throws JavaModelException {
		int kind= delta.getKind();
		int flags= delta.getFlags();
		final IJavaElement element= delta.getElement();
		final boolean isElementValidForView= fBrowsingPart.isValidElement(element);

		if (!getProvideWorkingCopy() && element instanceof IWorkingCopy && ((IWorkingCopy)element).isWorkingCopy())
			return;

		if (element != null && element.getElementType() == IJavaElement.COMPILATION_UNIT && !isOnClassPath((ICompilationUnit)element))
			return;

		// handle open and closing of a solution or project
		if (((flags & IJavaElementDelta.F_CLOSED) != 0) || ((flags & IJavaElementDelta.F_OPENED) != 0)) {
			postRefresh(null);
			return;
		}

		if (kind == IJavaElementDelta.REMOVED) {
			Object parent= internalGetParent(element);
			if (isElementValidForView) {
				if (element instanceof IClassFile) {
					postRemove(((IClassFile)element).getType());
				} else if (element instanceof ICompilationUnit && !((ICompilationUnit)element).isWorkingCopy()) {
						postRefresh(null);
				} else if (element instanceof ICompilationUnit && ((ICompilationUnit)element).isWorkingCopy()) {
					if (getProvideWorkingCopy())
						postRefresh(null);
				} else if (parent instanceof ICompilationUnit && getProvideWorkingCopy() && !((ICompilationUnit)parent).isWorkingCopy()) {
					if (element instanceof IWorkingCopy && ((IWorkingCopy)element).isWorkingCopy()) {
						// working copy removed from system - refresh
						postRefresh(null);
					}
				} else if (element instanceof IWorkingCopy && ((IWorkingCopy)element).isWorkingCopy() && parent != null && parent.equals(fInput))
					// closed editor - removing working copy
					postRefresh(null);
				else
					postRemove(element);
			}

			if (fBrowsingPart.isAncestorOf(element, fInput)) {
				if (element instanceof IWorkingCopy && ((IWorkingCopy)element).isWorkingCopy()) {
					postAdjustInputAndSetSelection(((IJavaElement) fInput).getPrimaryElement());
				} else
					postAdjustInputAndSetSelection(null);
			}

			if (fInput != null && fInput.equals(element))
				postRefresh(null);

			if (parent instanceof IPackageFragment && fBrowsingPart.isValidElement(parent))  {
				// refresh if package gets empty (might be filtered)
				if (isPackageFragmentEmpty((IPackageFragment)parent) && fViewer.testFindItem(parent) != null)
						postRefresh(null);
			}

			return;
		}
		if (kind == IJavaElementDelta.ADDED && delta.getMovedFromElement() != null && element instanceof ICompilationUnit)
			return;

		if (kind == IJavaElementDelta.ADDED) {
			if (isElementValidForView) {
				Object parent= internalGetParent(element);
				if (element instanceof IClassFile) {
					postAdd(parent, ((IClassFile)element).getType());
				} else if (element instanceof ICompilationUnit && !((ICompilationUnit)element).isWorkingCopy()) {
						postAdd(parent, ((ICompilationUnit)element).getTypes());
				} else if (parent instanceof ICompilationUnit && getProvideWorkingCopy() && !((ICompilationUnit)parent).isWorkingCopy()) {
					//	do nothing
				} else if (element instanceof IWorkingCopy && ((IWorkingCopy)element).isWorkingCopy()) {
					// new working copy comes to live
					postRefresh(null);
				} else
					postAdd(parent, element);
			} else	if (fInput == null) {
				IJavaElement newInput= fBrowsingPart.findInputForJavaElement(element);
				if (newInput != null)
					postAdjustInputAndSetSelection(element);
			} else if (element instanceof IType && fBrowsingPart.isValidInput(element)) {
				IJavaElement cu1= element.getAncestor(IJavaElement.COMPILATION_UNIT);
				IJavaElement cu2= ((IJavaElement)fInput).getAncestor(IJavaElement.COMPILATION_UNIT);
				if  (cu1 != null && cu2 != null && cu1.equals(cu2))
					postAdjustInputAndSetSelection(element);
			}
			return;
		}

		if (kind == IJavaElementDelta.CHANGED) {
			if (fInput != null && fInput.equals(element) && (flags & IJavaElementDelta.F_CHILDREN) != 0 && (flags & IJavaElementDelta.F_FINE_GRAINED) != 0) {
				postRefresh(null, true);
				return;
			}
			if (isElementValidForView && (flags & IJavaElementDelta.F_MODIFIERS) != 0) {
					postUpdateIcon(element);
			}
		}

		if (isClassPathChange(delta))
			 // throw the towel and do a full refresh
			postRefresh(null);

		if ((flags & IJavaElementDelta.F_ARCHIVE_CONTENT_CHANGED) != 0 && fInput instanceof IJavaElement) {
			IPackageFragmentRoot pkgRoot= (IPackageFragmentRoot)element;
			IJavaElement inputsParent= ((IJavaElement)fInput).getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			if (pkgRoot.equals(inputsParent))
				postRefresh(null);
		}

		// the source attachment of a JAR has changed
		if (element instanceof IPackageFragmentRoot && (((flags & IJavaElementDelta.F_SOURCEATTACHED) != 0 || ((flags & IJavaElementDelta.F_SOURCEDETACHED)) != 0)))
			postUpdateIcon(element);

		IJavaElementDelta[] affectedChildren= delta.getAffectedChildren();
		if (affectedChildren.length > 1) {
			// a package fragment might become non empty refresh from the parent
			if (element instanceof IPackageFragment) {
				IJavaElement parent= (IJavaElement)internalGetParent(element);
				// avoid posting a refresh to an invisible parent
				if (element.equals(fInput)) {
					postRefresh(element);
				} else {
					postRefresh(parent);
				}
			}
			// more than one child changed, refresh from here downwards
			if (element instanceof IPackageFragmentRoot && isElementValidForView) {
				postRefresh(skipProjectPackageFragmentRoot((IPackageFragmentRoot)element));
				return;
			}
		}
		for (int i= 0; i < affectedChildren.length; i++) {
			processDelta(affectedChildren[i]);
		}
	}

	private boolean isOnClassPath(ICompilationUnit element) throws JavaModelException {
		IJavaProject project= element.getJavaProject();
		if (project == null || !project.exists())
			return false;
		return project.isOnClasspath(element);
	}

	/**
	 * Updates the package icon
	 */
	 private void postUpdateIcon(final IJavaElement element) {
	 	postRunnable(new Runnable() {
			public void run() {
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed())
					fViewer.update(element, new String[]{IBasicPropertyConstants.P_IMAGE});
			}
		});
	 }

	private void postRefresh(final Object root, final boolean updateLabels) {
		postRunnable(new Runnable() {
			public void run() {
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed())
					fViewer.refresh(root, updateLabels);
			}
		});
	}

	private void postRefresh(final Object root) {
		postRefresh(root, false);
	}

	private void postAdd(final Object parent, final Object element) {
		postAdd(parent, new Object[] {element});
	}

	private void postAdd(final Object parent, final Object[] elements) {
		if (elements == null || elements.length <= 0)
			return;

		postRunnable(new Runnable() {
			public void run() {
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed()) {
					Object[] newElements= getNewElements(elements);
					if (fViewer instanceof AbstractTreeViewer) {
						if (fViewer.testFindItem(parent) == null) {
							Object root= ((AbstractTreeViewer)fViewer).getInput();
							if (root != null)
								((AbstractTreeViewer)fViewer).add(root, newElements);
						}
						else
							((AbstractTreeViewer)fViewer).add(parent, newElements);
					}
					else if (fViewer instanceof ListViewer)
						((ListViewer)fViewer).add(newElements);
					else if (fViewer instanceof TableViewer)
						((TableViewer)fViewer).add(newElements);
					if (fViewer.testFindItem(elements[0]) != null)
						fBrowsingPart.adjustInputAndSetSelection(elements[0]);
				}
			}
		});
	}

	private Object[] getNewElements(Object[] elements) {
		int elementsLength= elements.length;
		ArrayList<Object> result= new ArrayList<Object>(elementsLength);
		for (int i= 0; i < elementsLength; i++) {
			Object element= elements[i];
			if (fViewer.testFindItem(element) == null)
				result.add(element);
		}
		return result.toArray();
	}

	private void postRemove(final Object element) {
		postRemove(new Object[] {element});
	}

	private void postRemove(final Object[] elements) {
		if (elements.length <= 0)
			return;

		postRunnable(new Runnable() {
			public void run() {
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed()) {
					if (fViewer instanceof AbstractTreeViewer)
						((AbstractTreeViewer)fViewer).remove(elements);
					else if (fViewer instanceof ListViewer)
						((ListViewer)fViewer).remove(elements);
					else if (fViewer instanceof TableViewer)
						((TableViewer)fViewer).remove(elements);
				}
			}
		});
	}

	private void postAdjustInputAndSetSelection(final Object element) {
		postRunnable(new Runnable() {
			public void run() {
				Control ctrl= fViewer.getControl();
				if (ctrl != null && !ctrl.isDisposed()) {
					ctrl.setRedraw(false);
					fBrowsingPart.adjustInputAndSetSelection(element);
					ctrl.setRedraw(true);
				}
			}
		});
	}

	protected void startReadInDisplayThread() {
		if (isDisplayThread())
			fReadsInDisplayThread++;
	}

	protected void finishedReadInDisplayThread() {
		if (isDisplayThread())
			fReadsInDisplayThread--;
	}

	private boolean isDisplayThread() {
		Control ctrl= fViewer.getControl();
		if (ctrl == null)
			return false;

		Display currentDisplay= Display.getCurrent();
		return currentDisplay != null && currentDisplay.equals(ctrl.getDisplay());
	}

	private void postRunnable(final Runnable r) {
		Control ctrl= fViewer.getControl();
		if (ctrl != null && !ctrl.isDisposed()) {
			fBrowsingPart.setProcessSelectionEvents(false);
			try {
				if (isDisplayThread() && fReadsInDisplayThread == 0)
					ctrl.getDisplay().syncExec(r);
				else
					ctrl.getDisplay().asyncExec(r);
			} finally {
				fBrowsingPart.setProcessSelectionEvents(true);
			}
		}
	}

	/**
	 * Returns the parent for the element.
	 * <p>
	 * Note: This method will return a working copy if the
	 * parent is a working copy. The super class implementation
	 * returns the original element instead.
	 * </p>
	 */
	@Override
	protected Object internalGetParent(Object element) {
		if (element instanceof IJavaProject) {
			return ((IJavaProject)element).getJavaModel();
		}
		// try to map resources to the containing package fragment
		if (element instanceof IResource) {
			IResource parent= ((IResource)element).getParent();
			Object jParent= JavaCore.create(parent);
			if (jParent != null)
				return jParent;
			return parent;
		}

		// for package fragments that are contained in a project package fragment
		// we have to skip the package fragment root as the parent.
		if (element instanceof IPackageFragment) {
			IPackageFragmentRoot parent= (IPackageFragmentRoot)((IPackageFragment)element).getParent();
			return skipProjectPackageFragmentRoot(parent);
		}
		if (element instanceof IJavaElement)
			return ((IJavaElement)element).getParent();

		return null;
	}
}

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
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

public class CPUserLibraryElement {

	private  class UpdatedClasspathContainer implements IClasspathContainer {

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.core.IClasspathContainer#getClasspathEntries()
		 */
		public IClasspathEntry[] getClasspathEntries() {
			CPListElement[] children= getChildren();
			IClasspathEntry[] entries= new IClasspathEntry[children.length];
			for (int i= 0; i < entries.length; i++) {
				entries[i]= children[i].getClasspathEntry();
			}
			return entries;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.core.IClasspathContainer#getDescription()
		 */
		public String getDescription() {
			return getName();
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.core.IClasspathContainer#getKind()
		 */
		public int getKind() {
			return isSystemLibrary() ? IClasspathContainer.K_SYSTEM : K_APPLICATION;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.core.IClasspathContainer#getPath()
		 */
		public IPath getPath() {
			return CPUserLibraryElement.this.getPath();
		}
	}


	private String fName;
	private List<CPListElement> fChildren;
	private boolean fIsSystemLibrary;

	public CPUserLibraryElement(String name, IClasspathContainer container, IJavaProject project) {
		fName= name;
		fChildren= new ArrayList<CPListElement>();
		if (container != null) {
			IClasspathEntry[] entries= container.getClasspathEntries();
			CPListElement[] res= new CPListElement[entries.length];
			for (int i= 0; i < res.length; i++) {
				IClasspathEntry curr= entries[i];
				CPListElement elem= CPListElement.createFromExisting(this, curr, project);
				//elem.setAttribute(CPListElement.SOURCEATTACHMENT, curr.getSourceAttachmentPath());
				//elem.setAttribute(CPListElement.JAVADOC, JavaUI.getLibraryJavadocLocation(curr.getPath()));
				fChildren.add(elem);
			}
			fIsSystemLibrary= container.getKind() == IClasspathContainer.K_SYSTEM;
		} else {
			fIsSystemLibrary= false;
		}
	}

	/**
	 * Creates a new user library element with the given name and children and sets itself as the
	 * parent container to each given child element.
	 * 
	 * @param name the name of the library element
	 * @param isSystemLibrary <code>true</code> if the library is a system library,
	 *            <code>false</code> otherwise
	 * @param children the children elements of the library element or <code>null</code>
	 */
	public CPUserLibraryElement(String name, boolean isSystemLibrary, CPListElement[] children) {
		fName= name;
		fChildren= new ArrayList<CPListElement>();
		if (children != null) {
			for (int i= 0; i < children.length; i++) {
				children[i].setParentContainer(this);
				fChildren.add(children[i]);
			}
		}
		fIsSystemLibrary= isSystemLibrary;
	}

	public CPListElement[] getChildren() {
		return fChildren.toArray(new CPListElement[fChildren.size()]);
	}

	public String getName() {
		return fName;
	}

	public IPath getPath() {
		return new Path(JavaCore.USER_LIBRARY_CONTAINER_ID).append(fName);
	}

	public boolean isSystemLibrary() {
		return fIsSystemLibrary;
	}

	public void add(CPListElement element) {
		if (!fChildren.contains(element)) {
			fChildren.add(element);
		}
	}

	private List<CPListElement> moveUp(List<CPListElement> elements, List<CPListElement> move) {
		int nElements= elements.size();
		List<CPListElement> res= new ArrayList<CPListElement>(nElements);
		CPListElement floating= null;
		for (int i= 0; i < nElements; i++) {
			CPListElement curr= elements.get(i);
			if (move.contains(curr)) {
				res.add(curr);
			} else {
				if (floating != null) {
					res.add(floating);
				}
				floating= curr;
			}
		}
		if (floating != null) {
			res.add(floating);
		}
		return res;
	}

	public void moveUp(List<CPListElement> toMoveUp) {
		if (toMoveUp.size() > 0) {
			fChildren= moveUp(fChildren, toMoveUp);
		}
	}

	public void moveDown(List<CPListElement> toMoveDown) {
		if (toMoveDown.size() > 0) {
			Collections.reverse(fChildren);
			fChildren= moveUp(fChildren, toMoveDown);
			Collections.reverse(fChildren);
		}
	}


	public void remove(CPListElement element) {
		fChildren.remove(element);
	}

	public void replace(CPListElement existingElement, CPListElement element) {
		if (element.equals(existingElement)) {
			// same element selected again: do nothing
		} else if (fChildren.contains(element)) {
			fChildren.remove(existingElement);
		} else {
			int index= fChildren.indexOf(existingElement);
			if (index != -1) {
				fChildren.set(index, element);
			} else {
				fChildren.add(element);
			}
			element.setAttributesFromExisting(existingElement);
		}
	}

	public IClasspathContainer getUpdatedContainer() {
		return new UpdatedClasspathContainer();
	}

	public boolean hasChanges(IClasspathContainer oldContainer) {
		if (oldContainer == null || (oldContainer.getKind() == IClasspathContainer.K_SYSTEM) != fIsSystemLibrary) {
			return true;
		}
		IClasspathEntry[] oldEntries= oldContainer.getClasspathEntries();
		if (fChildren.size() != oldEntries.length) {
			return true;
		}
		for (int i= 0; i < oldEntries.length; i++) {
			CPListElement child= fChildren.get(i);
			if (!child.getClasspathEntry().equals(oldEntries[i])) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns if a entry has children that are missing
	 * @return Returns a boolean
	 */
	public boolean hasMissingChildren() {
		for (int i= 0; i < fChildren.size(); i++) {
			Object curr= fChildren.get(i);
			if (curr instanceof CPListElement && ((CPListElement) curr).isMissing()) {
				return true;
			}
		}
		return false;
	}

}

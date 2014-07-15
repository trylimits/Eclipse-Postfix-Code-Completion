/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Carsten Pfeiffer <carsten.pfeiffer@gebit.de> - [search] Custom search results not shown hierarchically in the java search results view - https://bugs.eclipse.org/bugs/show_bug.cgi?id=303705
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.search;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ITreeContentProvider;

import org.eclipse.search.ui.text.AbstractTextSearchResult;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.ui.StandardJavaElementContentProvider;

public class LevelTreeContentProvider extends JavaSearchContentProvider implements ITreeContentProvider {
	private Map<Object, Set<Object>> fChildrenMap;
	private StandardJavaElementContentProvider fContentProvider;

	public static final int LEVEL_TYPE= 1;
	public static final int LEVEL_FILE= 2;
	public static final int LEVEL_PACKAGE= 3;
	public static final int LEVEL_PROJECT= 4;

	private static final int[][] JAVA_ELEMENT_TYPES= {{IJavaElement.TYPE},
			{IJavaElement.CLASS_FILE, IJavaElement.COMPILATION_UNIT},
			{IJavaElement.PACKAGE_FRAGMENT},
			{IJavaElement.JAVA_PROJECT, IJavaElement.PACKAGE_FRAGMENT_ROOT},
			{IJavaElement.JAVA_MODEL}};
	private static final int[][] RESOURCE_TYPES= {
			{},
			{IResource.FILE},
			{IResource.FOLDER},
			{IResource.PROJECT},
			{IResource.ROOT}};

	private static final int MAX_LEVEL= JAVA_ELEMENT_TYPES.length - 1;
	private int fCurrentLevel;
	static class FastJavaElementProvider extends StandardJavaElementContentProvider {
		@Override
		public Object getParent(Object element) {
			Object parent= internalGetParent(element);
			if (parent == null && element instanceof IAdaptable) {
				IAdaptable adaptable = (IAdaptable)element;
				Object javaElement= adaptable.getAdapter(IJavaElement.class);
				if (javaElement != null) {
					parent= internalGetParent(javaElement);
				} else {
					Object resource= adaptable.getAdapter(IResource.class);
					if (resource != null) {
						parent= internalGetParent(resource);
					}
				}
			}
			return parent;
		}
	}

	public LevelTreeContentProvider(JavaSearchResultPage page, int level) {
		super(page);
		fCurrentLevel= level;
		fContentProvider= new FastJavaElementProvider();
	}

	public Object getParent(Object child) {
		Object possibleParent= internalGetParent(child);
		if (possibleParent instanceof IJavaElement) {
			IJavaElement javaElement= (IJavaElement) possibleParent;
			for (int j= fCurrentLevel; j < MAX_LEVEL + 1; j++) {
				for (int i= 0; i < JAVA_ELEMENT_TYPES[j].length; i++) {
					if (javaElement.getElementType() == JAVA_ELEMENT_TYPES[j][i]) {
						return null;
					}
				}
			}
		} else if (possibleParent instanceof IResource) {
			IResource resource= (IResource) possibleParent;
			for (int j= fCurrentLevel; j < MAX_LEVEL + 1; j++) {
				for (int i= 0; i < RESOURCE_TYPES[j].length; i++) {
					if (resource.getType() == RESOURCE_TYPES[j][i]) {
						return null;
					}
				}
			}
		}
		if (fCurrentLevel != LEVEL_FILE && child instanceof IType) {
			IType type= (IType) child;
			if (possibleParent instanceof ICompilationUnit
					|| possibleParent instanceof IClassFile)
				possibleParent= type.getPackageFragment();
		}
		return possibleParent;
	}

	private Object internalGetParent(Object child) {
		return fContentProvider.getParent(child);
	}

	public Object[] getElements(Object inputElement) {
		return getChildren(inputElement);
	}

	@Override
	protected synchronized void initialize(AbstractTextSearchResult result) {
		super.initialize(result);
		fChildrenMap= new HashMap<Object, Set<Object>>();
		if (result != null) {
			Object[] elements= result.getElements();
			for (int i= 0; i < elements.length; i++) {
				if (getPage().getDisplayedMatchCount(elements[i]) > 0) {
					insert(null, null, elements[i]);
				}
			}
		}
	}

	protected void insert(Map<Object, Set<Object>> toAdd, Set<Object> toUpdate, Object child) {
		Object parent= getParent(child);
		while (parent != null) {
			if (insertChild(parent, child)) {
				if (toAdd != null)
					insertInto(parent, child, toAdd);
			} else {
				if (toUpdate != null)
					toUpdate.add(parent);
				return;
			}
			child= parent;
			parent= getParent(child);
		}
		if (insertChild(getSearchResult(), child)) {
			if (toAdd != null)
				insertInto(getSearchResult(), child, toAdd);
		}
	}

	private boolean insertChild(Object parent, Object child) {
		return insertInto(parent, child, fChildrenMap);
	}

	private boolean insertInto(Object parent, Object child, Map<Object, Set<Object>> map) {
		Set<Object> children= map.get(parent);
		if (children == null) {
			children= new HashSet<Object>();
			map.put(parent, children);
		}
		return children.add(child);
	}

	protected void remove(Set<Object> toRemove, Set<Object> toUpdate, Object element) {
		// precondition here:  fResult.getMatchCount(child) <= 0

		if (hasChildren(element)) {
			if (toUpdate != null)
				toUpdate.add(element);
		} else {
			if (getPage().getDisplayedMatchCount(element) == 0) {
				fChildrenMap.remove(element);
				Object parent= getParent(element);
				if (parent != null) {
					if (removeFromSiblings(element, parent)) {
						remove(toRemove, toUpdate, parent);
					}
				} else {
					if (removeFromSiblings(element, getSearchResult())) {
						if (toRemove != null)
							toRemove.add(element);
					}
				}
			} else {
				if (toUpdate != null) {
					toUpdate.add(element);
				}
			}
		}
	}

	/**
	 * Tries to remove the given element from the list of stored siblings.
	 * 
	 * @param element potential child
	 * @param parent potential parent
	 * @return returns true if it really was a remove (i.e. element was a child of parent).
	 */
	private boolean removeFromSiblings(Object element, Object parent) {
		Set<Object> siblings= fChildrenMap.get(parent);
		if (siblings != null) {
			return siblings.remove(element);
		} else {
			return false;
		}
	}

	public Object[] getChildren(Object parentElement) {
		Set<Object> children= fChildrenMap.get(parentElement);
		if (children == null)
			return EMPTY_ARR;
		int limit= getPage().getElementLimit().intValue();
		if (limit != -1 && limit < children.size()) {
			Object[] limitedArray= new Object[limit];
			Iterator<Object> iterator= children.iterator();
			for (int i= 0; i < limit; i++) {
				limitedArray[i]= iterator.next();
			}
			return limitedArray;
		}

		return children.toArray();
	}

	public boolean hasChildren(Object element) {
		Set<Object> children= fChildrenMap.get(element);
		return children != null && !children.isEmpty();
	}

	@Override
	public synchronized void elementsChanged(Object[] updatedElements) {
		if (getSearchResult() == null)
			return;

		AbstractTreeViewer viewer= (AbstractTreeViewer) getPage().getViewer();

		Set<Object> toRemove= new HashSet<Object>();
		Set<Object> toUpdate= new HashSet<Object>();
		Map<Object, Set<Object>> toAdd= new HashMap<Object, Set<Object>>();
		for (int i= 0; i < updatedElements.length; i++) {
			if (getPage().getDisplayedMatchCount(updatedElements[i]) > 0)
				insert(toAdd, toUpdate, updatedElements[i]);
			else
				remove(toRemove, toUpdate, updatedElements[i]);
		}

		viewer.remove(toRemove.toArray());
		for (Iterator<Object> iter= toAdd.keySet().iterator(); iter.hasNext();) {
			Object parent= iter.next();
			HashSet<Object> children= (HashSet<Object>) toAdd.get(parent);
			viewer.add(parent, children.toArray());
		}
		for (Iterator<Object> elementsToUpdate= toUpdate.iterator(); elementsToUpdate.hasNext();) {
			viewer.refresh(elementsToUpdate.next());
		}

	}

	@Override
	public void clear() {
		initialize(getSearchResult());
		getPage().getViewer().refresh();
	}

	public void setLevel(int level) {
		fCurrentLevel= level;
		initialize(getSearchResult());
		getPage().getViewer().refresh();
	}

}

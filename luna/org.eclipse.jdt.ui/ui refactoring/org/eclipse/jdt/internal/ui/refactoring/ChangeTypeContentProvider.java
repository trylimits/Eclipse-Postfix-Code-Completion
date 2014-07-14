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
package org.eclipse.jdt.internal.ui.refactoring;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.jdt.core.dom.ITypeBinding;

import org.eclipse.jdt.internal.corext.refactoring.structure.ChangeTypeRefactoring;
import org.eclipse.jdt.internal.corext.util.CollectionsUtil;

class ChangeTypeContentProvider implements ITreeContentProvider {

	private ChangeTypeRefactoring fGeneralizeType;

	ChangeTypeContentProvider(ChangeTypeRefactoring gt){
		fGeneralizeType= gt;
	}

	public Object[] getChildren(Object element) {
		if (element instanceof RootType){
			return ((RootType)element).getChildren();
		}
		ITypeBinding[] superTypes = CollectionsUtil.toArray(getDirectSuperTypes((ITypeBinding)element), ITypeBinding.class);
		Arrays.sort(superTypes, new Comparator<ITypeBinding>(){
			public int compare(ITypeBinding o1, ITypeBinding o2) {
				String name1 = o1.getQualifiedName();
				String name2 = o2.getQualifiedName();
				return name1.compareTo(name2);
			}
		});
		return superTypes;
	}

	/**
	 * @param type a type
	 * @return the direct superclass and direct superinterfaces. Class Object is
	 * included in the result if the root of the hierarchy is a top-level
	 * interface.
	 */
	public Set<ITypeBinding> getDirectSuperTypes(ITypeBinding type){
		Set<ITypeBinding> result= new HashSet<ITypeBinding>();
		if (type.getSuperclass() != null){
			result.add(type.getSuperclass());
		}
		ITypeBinding[] interfaces= type.getInterfaces();
		for (int i=0; i < interfaces.length; i++){
			result.add(interfaces[i]);
		}
		if (fGeneralizeType.getOriginalType().isInterface() && type != fGeneralizeType.getObject()){
			result.add(fGeneralizeType.getObject());
		}
		return result;
	}

	public Object[] getElements(Object element) {
		Assert.isTrue(element instanceof RootType);
		return ((RootType)element).getChildren();
	}

	public boolean hasChildren(Object element) {
		return getChildren(element).length > 0;
	}

	public Object getParent(Object element) {
		return null;
	}

	public void dispose() {
	}

	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
	}

	/**
	 * Artificial "root node" of the tree view. This is needed to handle situations where the replacement
	 * types do not have a single common supertype. Also, the tree view does not show the root node by
	 * default.
	 */
	static class RootType {
		RootType(ITypeBinding root){
			fRoot = root;
		}
		public ITypeBinding[] getChildren(){
			return new ITypeBinding[]{ fRoot };
		}
		private ITypeBinding fRoot;
	}
}

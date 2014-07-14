/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.typehierarchy;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;

import org.eclipse.jdt.ui.IWorkingCopyProvider;

/**
 * Base class for content providers for type hierarchy viewers.
 * Implementors must override 'getTypesInHierarchy'.
 * Java delta processing is also performed by the content provider
 */
public abstract class TypeHierarchyContentProvider implements ITreeContentProvider, IWorkingCopyProvider {
	protected static final Object[] NO_ELEMENTS= new Object[0];

	protected TypeHierarchyLifeCycle fTypeHierarchy;
	protected IMember[] fMemberFilter;

	protected TreeViewer fViewer;

	private ViewerFilter fWorkingSetFilter;
	private MethodOverrideTester fMethodOverrideTester;
	private ITypeHierarchyLifeCycleListener fTypeHierarchyLifeCycleListener;


	public TypeHierarchyContentProvider(TypeHierarchyLifeCycle lifecycle) {
		fTypeHierarchy= lifecycle;
		fMemberFilter= null;
		fWorkingSetFilter= null;
		fMethodOverrideTester= null;
		fTypeHierarchyLifeCycleListener= new ITypeHierarchyLifeCycleListener() {
			public void typeHierarchyChanged(TypeHierarchyLifeCycle typeHierarchyProvider, IType[] changedTypes) {
				if (changedTypes == null) {
					synchronized (this) {
						fMethodOverrideTester= null;
					}
				}
			}
		};
		lifecycle.addChangedListener(fTypeHierarchyLifeCycleListener);
	}

	/**
	 * Sets members to filter the hierarchy for. Set to <code>null</code> to disable member filtering.
	 * When member filtering is enabled, the hierarchy contains only types that contain
	 * an implementation of one of the filter members and the members themself.
	 * The hierarchy can be empty as well.
	 * @param memberFilter the new member filter
	 */
	public final void setMemberFilter(IMember[] memberFilter) {
		fMemberFilter= memberFilter;
	}

	private boolean initializeMethodOverrideTester(IMethod filterMethod, IType typeToFindIn) {
		IType filterType= filterMethod.getDeclaringType();
		ITypeHierarchy hierarchy= fTypeHierarchy.getHierarchy();

		boolean filterOverrides= JavaModelUtil.isSuperType(hierarchy, typeToFindIn, filterType);
		IType focusType= filterOverrides ? filterType : typeToFindIn;

		if (fMethodOverrideTester == null || !fMethodOverrideTester.getFocusType().equals(focusType)) {
			fMethodOverrideTester= new MethodOverrideTester(focusType, hierarchy);
		}
		return filterOverrides;
	}

	private void addCompatibleMethods(IMethod filterMethod, IType typeToFindIn, List<IMember> children) throws JavaModelException {
		int flags= filterMethod.getFlags();
		if (Flags.isPrivate(flags) || Flags.isStatic(flags) || filterMethod.isConstructor())
			return;
		synchronized (fTypeHierarchyLifeCycleListener) {
			boolean filterMethodOverrides= initializeMethodOverrideTester(filterMethod, typeToFindIn);
			IMethod[] methods= typeToFindIn.getMethods();
			for (int i= 0; i < methods.length; i++) {
				IMethod curr= methods[i];
				flags= curr.getFlags();
				if (Flags.isPrivate(flags) || Flags.isStatic(flags) || curr.isConstructor())
					continue;
				if (isCompatibleMethod(filterMethod, curr, filterMethodOverrides) && !children.contains(curr)) {
					children.add(curr);
				}
			}
		}
	}

	private boolean hasCompatibleMethod(IMethod filterMethod, IType typeToFindIn) throws JavaModelException {
		int flags= filterMethod.getFlags();
		if (Flags.isPrivate(flags) || Flags.isStatic(flags) || filterMethod.isConstructor())
			return false;
		synchronized (fTypeHierarchyLifeCycleListener) {
			boolean filterMethodOverrides= initializeMethodOverrideTester(filterMethod, typeToFindIn);
			IMethod[] methods= typeToFindIn.getMethods();
			for (int i= 0; i < methods.length; i++) {
				IMethod curr= methods[i];
				flags= curr.getFlags();
				if (Flags.isPrivate(flags) || Flags.isStatic(flags) || curr.isConstructor())
					continue;
				if (isCompatibleMethod(filterMethod, curr, filterMethodOverrides)) {
					return true;
				}
			}
			return false;
		}
	}

	private boolean isCompatibleMethod(IMethod filterMethod, IMethod method, boolean filterOverrides) throws JavaModelException {
		if (filterOverrides) {
			return fMethodOverrideTester.isSubsignature(filterMethod, method);
		} else {
			return fMethodOverrideTester.isSubsignature(method, filterMethod);
		}
	}

	/**
	 * The members to filter or <code>null</code> if member filtering is disabled.
	 * @return the member filter
	 */
	public IMember[] getMemberFilter() {
		return fMemberFilter;
	}

	/**
	 * Sets a filter representing a working set or <code>null</code> if working sets are disabled.
	 * @param filter the filter
	 */
	public void setWorkingSetFilter(ViewerFilter filter) {
		fWorkingSetFilter= filter;
	}


	protected final ITypeHierarchy getHierarchy() {
		return fTypeHierarchy.getHierarchy();
	}


	/* (non-Javadoc)
	 * @see IReconciled#providesWorkingCopies()
	 */
	public boolean providesWorkingCopies() {
		return true;
	}


	/*
	 * Called for the root element
	 * @see IStructuredContentProvider#getElements
	 */
	public Object[] getElements(Object parent) {
		ArrayList<IType> types= new ArrayList<IType>();
		getRootTypes(types);
		for (int i= types.size() - 1; i >= 0; i--) {
			IType curr= types.get(i);
			try {
				if (!isInTree(curr)) {
					types.remove(i);
				}
			} catch (JavaModelException e) {
				// ignore
			}
		}
		return types.toArray();
	}

	protected void getRootTypes(List<IType> res) {
		ITypeHierarchy hierarchy= getHierarchy();
		if (hierarchy != null) {
			IType input= hierarchy.getType();
			if (input != null) {
				res.add(input);
			}
			// opened on a region: dont show
		}
	}

	/**
	 * Hook to overwrite. Filter will be applied on the returned types
	 * @param type the type
	 * @param res all types in the hierarchy of the given type
	 */
	protected abstract void getTypesInHierarchy(IType type, List<IType> res);

	/**
	 * Hook to overwrite. Return null if parent is ambiguous.
	 * @param type the type
	 * @return the parent type
	 */
	protected abstract IType getParentType(IType type);


	private boolean isInHierarchyOfInputElements(IType type) {
		if (fWorkingSetFilter != null && !fWorkingSetFilter.select(null, null, type)) {
			return false;
		}

		IJavaElement[] input= fTypeHierarchy.getInputElements();
		if (input == null)
			return false;
		for (int i= 0; i < input.length; i++) {
			int inputType= input[i].getElementType();
			if (inputType == IJavaElement.TYPE) {
				return true;
			}

			IJavaElement parent= type.getAncestor(inputType);
			if (inputType == IJavaElement.PACKAGE_FRAGMENT) {
				if (parent == null || parent.getElementName().equals(input[i].getElementName())) {
					return true;
				}
			} else if (input[i].equals(parent)) {
				return true;
			}
		}
		return false;
	}

	/*
	 * Called for the tree children.
	 * @see ITreeContentProvider#getChildren
	 */
	public Object[] getChildren(Object element) {
		if (element instanceof IType) {
			try {
				IType type= (IType)element;

				List<IMember> children= new ArrayList<IMember>();
				if (fMemberFilter != null) {
					addFilteredMemberChildren(type, children);
				}

				addTypeChildren(type, children);

				return children.toArray();
			} catch (JavaModelException e) {
				// ignore
			}
		}
		return NO_ELEMENTS;
	}

	/*
	 * @see ITreeContentProvider#hasChildren
	 */
	public boolean hasChildren(Object element) {
		if (element instanceof IType) {
			try {
				IType type= (IType) element;
				return hasTypeChildren(type) || (fMemberFilter != null && hasMemberFilterChildren(type));
			} catch (JavaModelException e) {
				return false;
			}
		}
		return false;
	}

	private void addFilteredMemberChildren(IType parent, List<IMember> children) throws JavaModelException {
		for (int i= 0; i < fMemberFilter.length; i++) {
			IMember member= fMemberFilter[i];
			if (parent.equals(member.getDeclaringType())) {
				if (!children.contains(member)) {
					children.add(member);
				}
			} else if (member instanceof IMethod) {
				addCompatibleMethods((IMethod) member, parent, children);
			}
		}
	}

	private void addTypeChildren(IType type, List<IMember> children) throws JavaModelException {
		ArrayList<IType> types= new ArrayList<IType>();
		getTypesInHierarchy(type, types);
		int len= types.size();
		for (int i= 0; i < len; i++) {
			IType curr= types.get(i);
			if (isInTree(curr)) {
				children.add(curr);
			}
		}
	}

	protected final boolean isInTree(IType type) throws JavaModelException {
		if (isInHierarchyOfInputElements(type)) {
			if (fMemberFilter != null) {
				return hasMemberFilterChildren(type) || hasTypeChildren(type);
			} else {
				return true;
			}
		}
		return hasTypeChildren(type);
	}

	private boolean hasMemberFilterChildren(IType type) throws JavaModelException {
		for (int i= 0; i < fMemberFilter.length; i++) {
			IMember member= fMemberFilter[i];
			if (type.equals(member.getDeclaringType())) {
				return true;
			} else if (member instanceof IMethod) {
				if (hasCompatibleMethod((IMethod) member, type)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean hasTypeChildren(IType type) throws JavaModelException {
		ArrayList<IType> types= new ArrayList<IType>();
		getTypesInHierarchy(type, types);
		int len= types.size();
		for (int i= 0; i < len; i++) {
			IType curr= types.get(i);
			if (isInTree(curr)) {
				return true;
			}
		}
		return false;
	}

	/*
	 * @see IContentProvider#inputChanged
	 */
	public void inputChanged(Viewer part, Object oldInput, Object newInput) {
		Assert.isTrue(part instanceof TreeViewer);
		fViewer= (TreeViewer)part;
	}

	/*
	 * @see IContentProvider#dispose
	 */
	public void dispose() {
		fTypeHierarchy.removeChangedListener(fTypeHierarchyLifeCycleListener);

	}

	/*
	 * @see ITreeContentProvider#getParent
	 */
	public Object getParent(Object element) {
		if (element instanceof IMember) {
			IMember member= (IMember) element;
			if (member.getElementType() == IJavaElement.TYPE) {
				return getParentType((IType)member);
			}
			return member.getDeclaringType();
		}
		return null;
	}

	protected final boolean isAnonymous(IType type) {
		try {
			return type.isAnonymous();
		} catch (JavaModelException e) {
			return false;
		}
	}

	protected final boolean isAnonymousFromInterface(IType type) {
		return isAnonymous(type) && fTypeHierarchy.getHierarchy().getSuperInterfaces(type).length != 0;
	}

	protected final boolean isObject(IType type) {
		return "Object".equals(type.getElementName()) && type.getDeclaringType() == null && "java.lang".equals(type.getPackageFragment().getElementName());  //$NON-NLS-1$//$NON-NLS-2$
	}

}

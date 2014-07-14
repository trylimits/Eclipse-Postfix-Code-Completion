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

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;

import org.eclipse.jdt.ui.JavaElementComparator;

import org.eclipse.jdt.internal.ui.viewsupport.SourcePositionComparator;

/**
  */
public abstract class AbstractHierarchyViewerSorter extends ViewerComparator {

	private static final int OTHER= 1;
	private static final int CLASS= 2;
	private static final int INTERFACE= 3;
	private static final int ANONYM= 4;

	private JavaElementComparator fNormalSorter;
	private SourcePositionComparator fSourcePositonSorter;

	public AbstractHierarchyViewerSorter() {
		fNormalSorter= new JavaElementComparator();
		fSourcePositonSorter= new SourcePositionComparator();
	}

	protected abstract ITypeHierarchy getHierarchy(IType type);
	public abstract boolean isSortByDefiningType();
	public abstract boolean isSortAlphabetically();


	protected int getTypeFlags(IType type) throws JavaModelException {
		return type.getFlags();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ViewerSorter#category(java.lang.Object)
	 */
	@Override
	public int category(Object element) {
		if (element instanceof IType) {
			IType type= (IType) element;
			try {
				if (type.isAnonymous() || type.isLambda()) {
					return ANONYM;
				}
			} catch (JavaModelException e1) {
				if (type.getElementName().length() == 0) {
					return ANONYM;
				}
			}
			try {
				int flags= getTypeFlags(type);
				if (Flags.isInterface(flags)) {
					return INTERFACE;
				} else {
					return CLASS;
				}
			} catch (JavaModelException e) {
				// ignore
			}
		}
		return OTHER;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ViewerSorter#compare(null, null, null)
	 */
	@Override
	public int compare(Viewer viewer, Object e1, Object e2) {
		if (!isSortAlphabetically() && !isSortByDefiningType()) {
			return fSourcePositonSorter.compare(viewer, e1, e2);
		}

		int cat1= category(e1);
		int cat2= category(e2);

		if (cat1 != cat2)
			return cat1 - cat2;

		if (cat1 == OTHER) { // method or field
			if (isSortByDefiningType()) {
				try {
					IType def1= (e1 instanceof IMethod) ? getDefiningType((IMethod) e1) : null;
					IType def2= (e2 instanceof IMethod) ? getDefiningType((IMethod) e2) : null;
					if (def1 != null) {
						if (def2 != null) {
							if (!def2.equals(def1)) {
								return compareInHierarchy(def1, def2);
							}
						} else {
							return -1;
						}
					} else {
						if (def2 != null) {
							return 1;
						}
					}
				} catch (JavaModelException e) {
					// ignore, default to normal comparison
				}
			}
			if (isSortAlphabetically()) {
				return fNormalSorter.compare(viewer, e1, e2); // use appearance pref page settings
			}
			return 0;
		} else if (cat1 == ANONYM) {
			return 0;
		} else if (isSortAlphabetically()) {
			String name1= ((IType) e1).getElementName();
			String name2= ((IType) e2).getElementName();
			return getComparator().compare(name1, name2);
		}
		return 0;
	}

	private IType getDefiningType(IMethod method) throws JavaModelException {
		int flags= method.getFlags();
		if (Flags.isPrivate(flags) || Flags.isStatic(flags) || method.isConstructor()) {
			return null;
		}

		IType declaringType= method.getDeclaringType();
		ITypeHierarchy hierarchy= getHierarchy(declaringType);
		if (hierarchy != null) {
			MethodOverrideTester tester= new MethodOverrideTester(declaringType, hierarchy);
			IMethod res= tester.findDeclaringMethod(method, true);
			if (res != null) {
				return res.getDeclaringType();
			}
		}
		return null;
	}


	private int compareInHierarchy(IType def1, IType def2) {
		if (JavaModelUtil.isSuperType(getHierarchy(def1), def2, def1)) {
			return 1;
		} else if (JavaModelUtil.isSuperType(getHierarchy(def2), def1, def2)) {
			return -1;
		}
		// interfaces after classes
		try {
			int flags1= getTypeFlags(def1);
			int flags2= getTypeFlags(def2);
			if (Flags.isInterface(flags1)) {
				if (!Flags.isInterface(flags2)) {
					return 1;
				}
			} else if (Flags.isInterface(flags2)) {
				return -1;
			}
		} catch (JavaModelException e) {
			// ignore
		}
		String name1= def1.getElementName();
		String name2= def2.getElementName();

		return getComparator().compare(name1, name2);
	}

}

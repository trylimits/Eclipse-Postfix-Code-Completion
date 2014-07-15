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
package org.eclipse.jdt.internal.ui.viewsupport;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

/**
 * Filter for the methods viewer.
 * Changing a filter property does not trigger a refiltering of the viewer
 */
public class MemberFilter extends ViewerFilter {

	public static final int FILTER_NONPUBLIC= 1;
	public static final int FILTER_STATIC= 2;
	public static final int FILTER_FIELDS= 4;
	public static final int FILTER_LOCALTYPES= 8;

	private int fFilterProperties;


	/**
	 * Modifies filter and add a property to filter for
	 * @param filter the filter to add
	 */
	public final void addFilter(int filter) {
		fFilterProperties |= filter;
	}
	/**
	 * Modifies filter and remove a property to filter for
	 * @param filter the filter to remove
	 */
	public final void removeFilter(int filter) {
		fFilterProperties &= (-1 ^ filter);
	}
	/**
	 * Tests if a property is filtered
	 * @param filter the filter to test
	 * @return returns the result of the test
	 */
	public final boolean hasFilter(int filter) {
		return (fFilterProperties & filter) != 0;
	}

	/*
	 * @see ViewerFilter#select(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
	 */
	@Override
	public boolean select(Viewer viewer, Object parentElement, Object element) {
		try {
			if (element instanceof IMember) {
				IMember member= (IMember) element;
				int memberType= member.getElementType();

				if (hasFilter(FILTER_FIELDS) && memberType == IJavaElement.FIELD) {
					return false;
				}

				if (hasFilter(FILTER_LOCALTYPES) && memberType == IJavaElement.TYPE && isLocalType((IType) member)) {
					return false;
				}

				if (member.getElementName().startsWith("<")) { // filter out <clinit> //$NON-NLS-1$
					return false;
				}
				int flags= member.getFlags();
				if (hasFilter(FILTER_STATIC) && (Flags.isStatic(flags) || isFieldInInterfaceOrAnnotation(member)) && memberType != IJavaElement.TYPE) {
					return false;
				}
				if (hasFilter(FILTER_NONPUBLIC) && !Flags.isPublic(flags) && !isMemberInInterfaceOrAnnotation(member) && !isTopLevelType(member) && !isEnumConstant(member)) {
					return false;
				}
			}
		} catch (JavaModelException e) {
			// ignore
		}
		return true;
	}

	private boolean isLocalType(IType type) {
		IJavaElement parent= type.getParent();
		return parent instanceof IMember && !(parent instanceof IType);
	}

	private boolean isMemberInInterfaceOrAnnotation(IMember member) throws JavaModelException {
		IType parent= member.getDeclaringType();
		return parent != null && JavaModelUtil.isInterfaceOrAnnotation(parent);
	}

	private boolean isFieldInInterfaceOrAnnotation(IMember member) throws JavaModelException {
		return (member.getElementType() == IJavaElement.FIELD) && JavaModelUtil.isInterfaceOrAnnotation(member.getDeclaringType());
	}

	private boolean isTopLevelType(IMember member) {
		IType parent= member.getDeclaringType();
		return parent == null;
	}

	private boolean isEnumConstant(IMember member) throws JavaModelException {
		return (member.getElementType() == IJavaElement.FIELD) && ((IField)member).isEnumConstant();
	}
}

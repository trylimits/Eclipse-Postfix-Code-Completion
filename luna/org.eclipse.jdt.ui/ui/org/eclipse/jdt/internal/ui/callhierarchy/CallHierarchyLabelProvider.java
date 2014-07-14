/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 * 			(report 36180: Callers/Callees view)
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import java.util.Collection;

import org.eclipse.swt.graphics.Image;

import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;

import org.eclipse.ui.model.IWorkbenchAdapter;

import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.callhierarchy.CallLocation;
import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;
import org.eclipse.jdt.internal.corext.callhierarchy.RealCallers;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.ColoringLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;


class CallHierarchyLabelProvider extends AppearanceAwareLabelProvider {
	private static final long TEXTFLAGS= DEFAULT_TEXTFLAGS | JavaElementLabels.ALL_POST_QUALIFIED | JavaElementLabels.P_COMPRESSED;

	private static final int IMAGEFLAGS= DEFAULT_IMAGEFLAGS | JavaElementImageProvider.SMALL_ICONS;

	private ILabelDecorator fDecorator;

	CallHierarchyLabelProvider() {
		super(TEXTFLAGS, IMAGEFLAGS);
		fDecorator= new CallHierarchyLabelDecorator();
	}

	/*
	 * @see org.eclipse.jface.viewers.LabelProvider#getImage(java.lang.Object)
	 */
	@Override
	public Image getImage(Object element) {
		if (element instanceof MethodWrapper) {
			MethodWrapper methodWrapper;
			if (element instanceof RealCallers) {
				methodWrapper= ((RealCallers)element).getParent();
			} else {
				methodWrapper= (MethodWrapper)element;
			}

			IMember member= methodWrapper.getMember();
			if (member != null) {
				return fDecorator.decorateImage(super.getImage(member), methodWrapper);
			} else {
				return null;
			}
		} else if (isPendingUpdate(element)) {
			return null;
		} else {
			return super.getImage(element);
		}
	}

	/*
	 * @see ILabelProvider#getText(Object)
	 */
	@Override
	public String getText(Object element) {
		if (isNormalMethodWrapper(element)) {
			MethodWrapper wrapper= (MethodWrapper)element;
			String decorated= getElementLabel(wrapper);
			
			if (isSpecialConstructorNode(wrapper)) {
				decorated= Messages.format(CallHierarchyMessages.CallHierarchyLabelProvider_constructor_label, decorated);
			}
			return decorated;
		}
		return getSpecialLabel(element);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.viewsupport.JavaUILabelProvider#getStyledText(java.lang.Object)
	 */
	@Override
	public StyledString getStyledText(Object element) {
		if (isNormalMethodWrapper(element)) {
			MethodWrapper wrapper= (MethodWrapper)element;
			String decorated= getElementLabel(wrapper);
			
			StyledString styledLabel= super.getStyledText(wrapper.getMember());
			StyledString styledDecorated= StyledCellLabelProvider.styleDecoratedString(decorated, StyledString.COUNTER_STYLER, styledLabel);
			if (isSpecialConstructorNode(wrapper)) {
				decorated= Messages.format(CallHierarchyMessages.CallHierarchyLabelProvider_constructor_label, decorated);
				styledDecorated= StyledCellLabelProvider.styleDecoratedString(decorated, ColoringLabelProvider.INHERITED_STYLER, styledDecorated);
			}
			return styledDecorated;
		}
		
		String specialLabel= getSpecialLabel(element);
		Styler styler= element instanceof RealCallers ? ColoringLabelProvider.INHERITED_STYLER : null;
		return new StyledString(specialLabel, styler);
	}

	
	private boolean isNormalMethodWrapper(Object element) {
		return element instanceof MethodWrapper && ((MethodWrapper)element).getMember() != null && !(element instanceof RealCallers);
	}
	
	private boolean isSpecialConstructorNode(MethodWrapper wrapper) {
		MethodWrapper parentWrapper= wrapper.getParent();
		if (!CallHierarchyContentProvider.isExpandWithConstructors(parentWrapper))
			return false;
		
		IMember member= wrapper.getMember();
		if (member instanceof IType)
			return true;
		
		try {
			return member instanceof IMethod && ((IMethod)member).isConstructor();
		} catch (JavaModelException e) {
			return false; // assume it's not a constructor
		}
	}

	private String getSpecialLabel(Object element) {
		if (element instanceof RealCallers) {
			return CallHierarchyMessages.CallHierarchyLabelProvider_expandWithConstructorsAction_realCallers;
		} else if (element instanceof MethodWrapper) {
			return CallHierarchyMessages.CallHierarchyLabelProvider_root;
		} else if (element == TreeTermination.SEARCH_CANCELED) {
			return CallHierarchyMessages.CallHierarchyLabelProvider_searchCanceled;
		} else if (isPendingUpdate(element)) {
			return CallHierarchyMessages.CallHierarchyLabelProvider_updatePending;
		}
		return CallHierarchyMessages.CallHierarchyLabelProvider_noMethodSelected;
	}

	private boolean isPendingUpdate(Object element) {
		return element instanceof IWorkbenchAdapter;
	}

	private String getElementLabel(MethodWrapper methodWrapper) {
		String label= super.getText(methodWrapper.getMember());

		Collection<CallLocation> callLocations= methodWrapper.getMethodCall().getCallLocations();

		if ((callLocations != null) && (callLocations.size() > 1)) {
			return Messages.format(CallHierarchyMessages.CallHierarchyLabelProvider_matches, new String[] { label, String.valueOf(callLocations.size()) });
		}

		return label;
	}
}

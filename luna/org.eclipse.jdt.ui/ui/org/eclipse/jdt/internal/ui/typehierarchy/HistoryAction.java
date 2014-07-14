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
package org.eclipse.jdt.internal.ui.typehierarchy;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.StyledString;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;

/**
 * Action used for the type hierarchy forward / backward buttons
 */
public class HistoryAction extends Action {

	private TypeHierarchyViewPart fViewPart;
	private IJavaElement[] fElements;

	public HistoryAction(TypeHierarchyViewPart viewPart, IJavaElement[] elements) {
        super("", AS_RADIO_BUTTON); //$NON-NLS-1$
		fViewPart= viewPart;
		fElements= elements;

		String elementName= getElementLabel(elements);
		setText(elementName);
		setImageDescriptor(getImageDescriptor(elements[0]));

		setDescription(Messages.format(TypeHierarchyMessages.HistoryAction_description, elementName));
		setToolTipText(Messages.format(TypeHierarchyMessages.HistoryAction_tooltip, elementName));
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.HISTORY_ACTION);
	}

	private ImageDescriptor getImageDescriptor(IJavaElement elem) {
		JavaElementImageProvider imageProvider= new JavaElementImageProvider();
		ImageDescriptor desc= imageProvider.getBaseImageDescriptor(elem, 0);
		imageProvider.dispose();
		return desc;
	}

	/*
	 * @see Action#run()
	 */
	@Override
	public void run() {
		fViewPart.gotoHistoryEntry(fElements);
	}

	/**
	 * Fetches the label for the java element.
	 * 
	 * @param element the java element
	 * @return the label for the java element
	 * @since 3.7
	 */
	static StyledString getSingleElementLabel(IJavaElement element) {
		return JavaElementLabels.getStyledElementLabel(element, JavaElementLabels.ALL_POST_QUALIFIED | JavaElementLabels.COLORIZE | JavaElementLabels.P_COMPRESSED);
	}

	/**
	 * Fetches the label for all the java elements.
	 * 
	 * @param elements the java elements
	 * @return the label for all the java elements
	 * @since 3.7
	 */
	static String getElementLabel(IJavaElement[] elements) {
		switch (elements.length) {
			case 0:
				Assert.isTrue(false);
				return null;

			case 1:
				return Messages.format(TypeHierarchyMessages.HistoryAction_inputElements_1,
						new String[] { getShortLabel(elements[0]) });
			case 2:
				return Messages.format(TypeHierarchyMessages.HistoryAction_inputElements_2,
						new String[] { getShortLabel(elements[0]), getShortLabel(elements[1]) });
			default:
				return Messages.format(TypeHierarchyMessages.HistoryAction_inputElements_more,
						new String[] { getShortLabel(elements[0]), getShortLabel(elements[1]), getShortLabel(elements[2]) });
		}
	}

	/**
	 * Fetches the short label for the java element.
	 * 
	 * @param element the java element
	 * @return the short label for the java element
	 * @since 3.7
	 */
	static String getShortLabel(IJavaElement element) {
		return JavaElementLabels.getElementLabel(element, JavaElementLabels.ALL_DEFAULT | JavaElementLabels.ALL_POST_QUALIFIED | JavaElementLabels.P_COMPRESSED);
	}

}

/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.javaeditor;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;
import org.eclipse.jdt.internal.corext.util.SuperTypeHierarchyCache;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Java element super implementation hyperlink.
 * 
 * @since 3.7
 */
public class JavaElementSuperImplementationHyperlink implements IHyperlink {

	private final SelectionDispatchAction fOpenAction;
	private final IMethod fMethod;
	private final boolean fQualify;
	private IRegion fRegion;

	/**
	 * Creates a new Java element super implementation hyperlink for methods.
	 * 
	 * @param region the region of the link
	 * @param openAction the action to use to open the java elements
	 * @param method the method to open
	 * @param qualify <code>true</code> if the hyperlink text should show a qualified name for
	 *            element
	 */
	public JavaElementSuperImplementationHyperlink(IRegion region, SelectionDispatchAction openAction, IMethod method, boolean qualify) {
		Assert.isNotNull(openAction);
		Assert.isNotNull(region);
		Assert.isNotNull(method);

		fRegion= region;
		fOpenAction= openAction;
		fMethod= method;
		fQualify= qualify;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.hyperlink.IHyperlink#getHyperlinkRegion()
	 */
	public IRegion getHyperlinkRegion() {
		return fRegion;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.hyperlink.IHyperlink#getTypeLabel()
	 */
	public String getTypeLabel() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.hyperlink.IHyperlink#getHyperlinkText()
	 */
	public String getHyperlinkText() {
		if (fQualify) {
			String methodLabel= JavaElementLabels.getElementLabel(fMethod, JavaElementLabels.ALL_FULLY_QUALIFIED);
			return Messages.format(JavaEditorMessages.JavaElementSuperImplementationHyperlink_hyperlinkText_qualified, new Object[] { methodLabel });
		} else {
			return JavaEditorMessages.JavaElementSuperImplementationHyperlink_hyperlinkText;
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.hyperlink.IHyperlink#open()
	 */
	public void open() {
		try {
			IMethod method= findSuperImplementation(fMethod);
			if (method != null)
				fOpenAction.run(new StructuredSelection(method));
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
			return;
		}
	}

	/**
	 * Finds the super implementation of the method.
	 * 
	 * @param method the method
	 * @return the super implementation of the method if any or <code>null</code>
	 * @throws JavaModelException if an exception occurs while creating the type hierarchy to find
	 *             the super implementation
	 */
	static IMethod findSuperImplementation(IMethod method) throws JavaModelException {
		MethodOverrideTester tester= SuperTypeHierarchyCache.getMethodOverrideTester(method.getDeclaringType());
		return tester.findOverriddenMethod(method, false);
	}

}

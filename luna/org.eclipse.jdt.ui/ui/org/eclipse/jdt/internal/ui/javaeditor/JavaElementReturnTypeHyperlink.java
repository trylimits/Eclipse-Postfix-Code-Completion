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
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Creates a Java element return type hyperlink for methods.
 * 
 * @since 3.7
 */
public class JavaElementReturnTypeHyperlink implements IHyperlink {

	private final IRegion fRegion;
	private final SelectionDispatchAction fOpenAction;
	private final IMethod fMethod;
	private final boolean fQualify;
	
	/**
	 * Creates a new Java element return type hyperlink for methods.
	 * 
	 * @param region the region of the link
	 * @param openAction the action to use to open the Java elements
	 * @param method the method to open
	 * @param qualify <code>true</code> if the hyperlink text should show a qualified name for
	 *            element.
	 */
	public JavaElementReturnTypeHyperlink(IRegion region, SelectionDispatchAction openAction, IMethod method, boolean qualify) {
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
			return Messages.format(JavaEditorMessages.JavaElementReturnTypeHyperlink_hyperlinkText_qualified, new Object[] { methodLabel });
		} else {
			return JavaEditorMessages.JavaElementReturnTypeHyperlink_hyperlinkText;
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.hyperlink.IHyperlink#open()
	 */
	public void open() {
		try {
			String returnTypeSignature= fMethod.getReturnType();
			int kind= Signature.getTypeSignatureKind(returnTypeSignature);
			if (kind == Signature.ARRAY_TYPE_SIGNATURE) {
				returnTypeSignature= Signature.getElementType(returnTypeSignature);
			} else if (kind == Signature.CLASS_TYPE_SIGNATURE) {
				returnTypeSignature= Signature.getTypeErasure(returnTypeSignature);
			}
			String returnType= Signature.toString(returnTypeSignature);

			String[][] resolvedType= fMethod.getDeclaringType().resolveType(returnType);
			if (resolvedType == null || resolvedType.length == 0) {
				openMethodAndShowErrorInStatusLine();
				return;
			}

			String typeName= JavaModelUtil.concatenateName(resolvedType[0][0], resolvedType[0][1]);
			IType type= fMethod.getJavaProject().findType(typeName, (IProgressMonitor)null);
			if (type != null) {
				fOpenAction.run(new StructuredSelection(type));
				return;
			}
			openMethodAndShowErrorInStatusLine();
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
			return;
		}
	}

	/**
	 * Opens and selects the method, and shows an error message in the status line.
	 */
	private void openMethodAndShowErrorInStatusLine() {
		try {
			IEditorPart editor= JavaUI.openInEditor(fMethod);
			
			editor.getSite().getShell().getDisplay().beep();
			if (editor instanceof JavaEditor)
				((JavaEditor)editor).setStatusLineErrorMessage(JavaEditorMessages.JavaElementReturnTypeHyperlink_error_msg);
			
		} catch (PartInitException e) {
			JavaPlugin.log(e);
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
	}
}

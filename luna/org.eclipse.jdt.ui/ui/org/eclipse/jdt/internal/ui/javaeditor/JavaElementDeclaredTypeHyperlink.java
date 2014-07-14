/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
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

import org.eclipse.jdt.core.IJavaElement;
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
 * Java element declared type hyperlink for variables.
 * 
 * @since 3.7
 */
public class JavaElementDeclaredTypeHyperlink implements IHyperlink {

	private final IRegion fRegion;
	private final SelectionDispatchAction fOpenAction;
	private final IJavaElement fElement;
	private final String fTypeSig;
	private final boolean fQualify;
	
	/**
	 * Creates a new Java element declared type hyperlink for variables.
	 * 
	 * @param region the region of the link
	 * @param openAction the action to use to open the Java elements
	 * @param element the Java element to open
	 * @param qualify <code>true</code> if the hyperlink text should show a qualified name for
	 *            element.
	 */
	public JavaElementDeclaredTypeHyperlink(IRegion region, SelectionDispatchAction openAction, IJavaElement element, boolean qualify) {
		this(region, openAction, element, null, qualify);
	}

	/**
	 * Creates a new Java element declared type hyperlink for variables.
	 * 
	 * @param region the region of the link
	 * @param openAction the action to use to open the Java elements
	 * @param element the Java element to open
	 * @param typeSig the signature of the type to open
	 * @param qualify <code>true</code> if the hyperlink text should show a qualified name for
	 *            element.
	 */
	public JavaElementDeclaredTypeHyperlink(IRegion region, SelectionDispatchAction openAction, IJavaElement element, String typeSig, boolean qualify) {
		Assert.isNotNull(openAction);
		Assert.isNotNull(region);
		Assert.isNotNull(element);

		fRegion= region;
		fOpenAction= openAction;
		fElement= element;
		fTypeSig= typeSig;
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
			if (fTypeSig == null) {
				String elementLabel= JavaElementLabels.getElementLabel(fElement, JavaElementLabels.ALL_FULLY_QUALIFIED);
				return Messages.format(JavaEditorMessages.JavaElementDeclaredTypeHyperlink_hyperlinkText_qualified, new Object[] { elementLabel });
			} else {
				String type= Signature.toString(fTypeSig);
				return Messages.format(JavaEditorMessages.JavaElementDeclaredTypeHyperlink_hyperlinkText_qualified_signature, new Object[] { type });
			}
		} else {
			return JavaEditorMessages.JavaElementDeclaredTypeHyperlink_hyperlinkText;
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.hyperlink.IHyperlink#open()
	 */
	public void open() {
		String typeSignature= fTypeSig;
		if (typeSignature == null) {
			try {
				typeSignature= JavaElementHyperlinkDeclaredTypeDetector.getTypeSignature(fElement);
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
				return;
			}
		}
		int kind= Signature.getTypeSignatureKind(typeSignature);
		if (kind == Signature.ARRAY_TYPE_SIGNATURE) {
			typeSignature= Signature.getElementType(typeSignature);
		} else if (kind == Signature.CLASS_TYPE_SIGNATURE) {
			typeSignature= Signature.getTypeErasure(typeSignature);
		}
		String typeName= Signature.toString(typeSignature);

		IJavaElement parent= fElement.getAncestor(IJavaElement.TYPE);
		if (parent == null) {
			openElementAndShowErrorInStatusLine();
			return;
		}
		try {
			String[][] resolvedType= ((IType)parent).resolveType(typeName);
			if (resolvedType == null || resolvedType.length == 0) {
				openElementAndShowErrorInStatusLine();
				return;
			}
			String qualTypeName= JavaModelUtil.concatenateName(resolvedType[0][0], resolvedType[0][1]);
			IType type= fElement.getJavaProject().findType(qualTypeName, (IProgressMonitor)null);
			if (type != null) {
				fOpenAction.run(new StructuredSelection(type));
				return;
			}
			openElementAndShowErrorInStatusLine();
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
			return;
		}
	}

	/**
	 * Opens and selects the Java element, and shows an error message in the status line.
	 */
	private void openElementAndShowErrorInStatusLine() {
		try {
			IEditorPart editor= JavaUI.openInEditor(fElement);
			
			editor.getSite().getShell().getDisplay().beep();
			if (editor instanceof JavaEditor)
				((JavaEditor)editor).setStatusLineErrorMessage(JavaEditorMessages.JavaElementDeclaredTypeHyperlink_error_msg);
			
		} catch (PartInitException e) {
			JavaPlugin.log(e);
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
	}
}

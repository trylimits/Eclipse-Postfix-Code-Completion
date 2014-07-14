/*******************************************************************************
 * Copyright (c) 2000, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.compare;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ResourceBundle;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IActionDelegate;

import org.eclipse.compare.IEncodedStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.DiffNode;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

import org.eclipse.jdt.internal.ui.JavaPlugin;

public class JavaCompareAction implements IActionDelegate {

	class TypedElement implements ITypedElement, IEncodedStreamContentAccessor {

		private ISourceReference fSource;
		private String fContents;

		TypedElement(ISourceReference s, String contents) {
			fSource= s;
			fContents= contents;
		}

		public String getName() {
			return fJavaElementLabelProvider.getText(fSource);
		}

		public String getType() {
			return "JAVA"; //$NON-NLS-1$
		}

		public Image getImage() {
			return fJavaElementLabelProvider.getImage(fSource);
		}

		public InputStream getContents() throws CoreException {
			byte[] bytes;
			try {
				bytes= fContents.getBytes("UTF-16"); //$NON-NLS-1$
			} catch (UnsupportedEncodingException e) {
				bytes= fContents.getBytes();
			}
			return new ByteArrayInputStream(bytes);
		}

		public String getCharset() {
			return "UTF-16"; //$NON-NLS-1$
		}
	}

	private static final String BUNDLE_NAME= "org.eclipse.jdt.internal.ui.compare.CompareAction"; //$NON-NLS-1$

	private ISourceReference fLeft;
	private ISourceReference fRight;

	private JavaElementLabelProvider fJavaElementLabelProvider;


	public void selectionChanged(IAction action, ISelection selection) {
		action.setEnabled(isEnabled(selection));
	}

	public void run(IAction action) {
		Shell shell= JavaPlugin.getActiveWorkbenchShell();
		ResourceBundle bundle= ResourceBundle.getBundle(BUNDLE_NAME);
		CompareDialog d= new CompareDialog(shell, bundle);


		String left= null;
		String right= null;

		try {
			left= getExtendedSource(fLeft);
		} catch (JavaModelException ex) {
			JavaPlugin.log(ex);
		}

		try {
			right= getExtendedSource(fRight);
		} catch (JavaModelException ex) {
			JavaPlugin.log(ex);
		}

		fJavaElementLabelProvider= new JavaElementLabelProvider(
					JavaElementLabelProvider.SHOW_PARAMETERS |
					JavaElementLabelProvider.SHOW_OVERLAY_ICONS |
					JavaElementLabelProvider.SHOW_POST_QUALIFIED |
					JavaElementLabelProvider.SHOW_ROOT);

		if (left == null || right == null) {
			String errorTitle= JavaCompareUtilities.getString(bundle, "errorTitle"); //$NON-NLS-1$
			String errorFormat= JavaCompareUtilities.getString(bundle, "errorFormat"); //$NON-NLS-1$

			Object element= null;
			if (left == null)
				element= fLeft;
			else
				element= fRight;

			String message= Messages.format(errorFormat, new String[] { fJavaElementLabelProvider.getText(element) } );

			MessageDialog.openError(shell, errorTitle, message);
			return;
		}

		d.compare(new DiffNode(new TypedElement(fLeft, left), new TypedElement(fRight, right)));

		fJavaElementLabelProvider.dispose();
		fJavaElementLabelProvider= null;
	}

	protected boolean isEnabled(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			Object[] sel= ((IStructuredSelection) selection).toArray();
			if (sel.length == 2) {
				for (int i= 0; i < 2; i++) {
					Object o= sel[i];
					if (!(o instanceof ISourceReference))
						return false;
				}
				fLeft= (ISourceReference) sel[0];
				fRight= (ISourceReference) sel[1];
				return true;
			}
		}
		return false;
	}

	private String getExtendedSource(ISourceReference ref) throws JavaModelException {

		// get parent
		if (ref instanceof IJavaElement) {
			IJavaElement parent= ((IJavaElement) ref).getParent();
			if (parent instanceof ISourceReference) {
				ISourceReference sr= (ISourceReference) parent;
				String parentContent= sr.getSource();
				if (parentContent != null) {
					ISourceRange parentRange= sr.getSourceRange();
					ISourceRange childRange= ref.getSourceRange();

					int start= childRange.getOffset() - parentRange.getOffset();
					int end= start + childRange.getLength();

					// search backwards for beginning of line
					while (start > 0) {
						char c= parentContent.charAt(start-1);
						if (c == '\n' || c == '\r')
							break;
						start--;
					}

					return parentContent.substring(start, end);
				}
			}
		}

		return ref.getSource();
	}
}

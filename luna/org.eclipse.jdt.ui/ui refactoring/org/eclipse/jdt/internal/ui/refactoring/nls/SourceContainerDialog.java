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
package org.eclipse.jdt.internal.ui.refactoring.nls;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IWorkspaceRoot;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.StandardJavaElementContentProvider;

import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter;

public class SourceContainerDialog extends ElementTreeSelectionDialog {

	private class PackageAndProjectSelectionValidator extends TypedElementSelectionValidator {

		public PackageAndProjectSelectionValidator() {
			super(new Class[]{IPackageFragmentRoot.class},false);
		}

		@Override
		public boolean isSelectedValid(Object element) {
			try {
				if (element instanceof IJavaProject) {
					IJavaProject jproject= (IJavaProject) element;
					IPath path= jproject.getProject().getFullPath();
					return (jproject.findPackageFragmentRoot(path) != null);
				} else
					if (element instanceof IPackageFragmentRoot) {
						return (((IPackageFragmentRoot) element).getKind() == IPackageFragmentRoot.K_SOURCE);
					}
				return true;
			} catch (JavaModelException e) {
				// fall through returning false
			}
			return false;
		}
	}

	/**
	 * A TypedViewerFilter that accepts only PackageFragments and JavaProjects.
	 * PackageFragments are only accepted if they are of the kind K_SOURCE.
	 */
	private class JavaTypedViewerFilter extends TypedViewerFilter {

		public JavaTypedViewerFilter() {
			super(new Class[]{IPackageFragmentRoot.class, IJavaProject.class});
		}

		@Override
		public boolean select(Viewer viewer, Object parent, Object element) {
			if (element instanceof IPackageFragmentRoot) {
				IPackageFragmentRoot fragmentRoot= (IPackageFragmentRoot)element;
				try {
					return (fragmentRoot.getKind() == IPackageFragmentRoot.K_SOURCE);
				} catch (JavaModelException e) {
					return false;
				}
			}
			return super.select(viewer, parent, element);
		}
	}

	private SourceContainerDialog(Shell shell) {
		super(shell,new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT),new StandardJavaElementContentProvider());
		setValidator(new PackageAndProjectSelectionValidator());
		setComparator(new JavaElementComparator());
		setTitle(NewWizardMessages.NewContainerWizardPage_ChooseSourceContainerDialog_title);
		setMessage(NewWizardMessages.NewContainerWizardPage_ChooseSourceContainerDialog_description);
		addFilter(new JavaTypedViewerFilter());
	}

	public static IPackageFragmentRoot getSourceContainer(Shell shell, IWorkspaceRoot workspaceRoot, IJavaElement initElement) {
		SourceContainerDialog dialog= new SourceContainerDialog(shell);
		dialog.setInput(JavaCore.create(workspaceRoot));
		dialog.setInitialSelection(initElement);

		if (dialog.open() == Window.OK) {
			Object element= dialog.getFirstResult();
			if (element instanceof IJavaProject) {
				IJavaProject jproject= (IJavaProject) element;
				return jproject.getPackageFragmentRoot(jproject.getProject());
			} else
				if (element instanceof IPackageFragmentRoot) {
					return (IPackageFragmentRoot) element;
				}
			return null;
		}
		return null;
	}
}

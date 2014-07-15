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

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.StandardJavaElementContentProvider;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;


class SourceFirstPackageSelectionDialogField {

	private SourceFolderSelectionDialogButtonField fSourceFolderSelection;
	private PackageFragmentSelection fPackageSelection;
	private Shell fShell;

	public SourceFirstPackageSelectionDialogField(String sourceLabel, String packageLabel, String browseLabel1,
		String browseLabel2, String statusHint, String dialogTitle, String dialogMessage, String dialogEmptyMessage,
		ICompilationUnit cu, IDialogFieldListener updateListener, IPackageFragment fragment) {
		fSourceFolderSelection= new SourceFolderSelectionDialogButtonField(sourceLabel, browseLabel1, 	new SFStringButtonAdapter());

		fPackageSelection= new PackageFragmentSelection(this, packageLabel, browseLabel2, statusHint,
			new PackageSelectionStringButtonAdapter(this, dialogTitle, dialogMessage, dialogEmptyMessage));
		fPackageSelection.setDialogFieldListener(new PackageSelectionDialogFieldListener());

		fSourceFolderSelection.setSourceChangeListener(fPackageSelection);

		setDefaults(fragment, cu);

		fPackageSelection.setUpdateListener(updateListener);
		fSourceFolderSelection.setUpdateListener(updateListener);
	}

	private void setDefaults(IPackageFragment fragment, ICompilationUnit cu) {
		IJavaElement element= fragment;
		if (element == null) {
			element= cu;
		}

		fSourceFolderSelection.setRoot(searchSourcePackageFragmentRoot(element));
		fPackageSelection.setPackageFragment(searchPackageFragment(element));
	}

	private IPackageFragment searchPackageFragment(IJavaElement jElement) {
		return (IPackageFragment)jElement.getAncestor(IJavaElement.PACKAGE_FRAGMENT);
	}

	private IPackageFragmentRoot searchSourcePackageFragmentRoot(IJavaElement jElement) {
		IJavaElement parent= jElement.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
		if (parent == null) {
			return null;
		}

		IPackageFragmentRoot res= (IPackageFragmentRoot)parent;
		try {
			if (res.getKind() == IPackageFragmentRoot.K_SOURCE) {
				return res;
			}
		} catch (JavaModelException e) {
			// nothing to do
		}

		return null;
	}

	class PackageSelectionDialogFieldListener implements IDialogFieldListener {

		public void dialogFieldChanged(DialogField field) {
			String packName= fPackageSelection.getText();
			if (packName.length() == 0)
				fPackageSelection.setStatus(NLSUIMessages.NLSAccessorConfigurationDialog_default);
			else
				fPackageSelection.setStatus(""); //$NON-NLS-1$
		}
	}

	class SFStringButtonAdapter implements IStringButtonAdapter {
		public void changeControlPressed(DialogField field) {

			IPackageFragmentRoot newSourceContainer= chooseSourceContainer(fSourceFolderSelection.getRoot());
			if (newSourceContainer != null) {
				fSourceFolderSelection.setRoot(newSourceContainer);
			}
		}
	}

	private IPackageFragmentRoot chooseSourceContainer(IJavaElement initElement) {
		Class<?>[] acceptedClasses= new Class[] { IPackageFragmentRoot.class, IJavaProject.class };
		TypedElementSelectionValidator validator= new TypedElementSelectionValidator(acceptedClasses, false) {
			@Override
			public boolean isSelectedValid(Object element) {
				try {
					if (element instanceof IJavaProject) {
						IJavaProject jproject= (IJavaProject)element;
						IPath path= jproject.getProject().getFullPath();
						return (jproject.findPackageFragmentRoot(path) != null);
					} else if (element instanceof IPackageFragmentRoot) {
						return (((IPackageFragmentRoot)element).getKind() == IPackageFragmentRoot.K_SOURCE);
					}
					return true;
				} catch (JavaModelException e) {
					JavaPlugin.log(e.getStatus()); // just log, no ui in validation
				}
				return false;
			}
		};

		acceptedClasses= new Class[] { IJavaModel.class, IPackageFragmentRoot.class, IJavaProject.class };
		ViewerFilter filter= new TypedViewerFilter(acceptedClasses) {
			@Override
			public boolean select(Viewer viewer, Object parent, Object element) {
				if (element instanceof IPackageFragmentRoot) {
					try {
						return (((IPackageFragmentRoot)element).getKind() == IPackageFragmentRoot.K_SOURCE);
					} catch (JavaModelException e) {
						JavaPlugin.log(e.getStatus()); // just log, no ui in validation
						return false;
					}
				}
				return super.select(viewer, parent, element);
			}
		};

		StandardJavaElementContentProvider provider= new StandardJavaElementContentProvider();
		ILabelProvider labelProvider= new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT);
		ElementTreeSelectionDialog dialog= new ElementTreeSelectionDialog(fShell, labelProvider, provider);
		dialog.setValidator(validator);
		dialog.setComparator(new JavaElementComparator());
		dialog.setTitle(NLSUIMessages.SourceFirstPackageSelectionDialogField_ChooseSourceContainerDialog_title);
		dialog.setMessage(NLSUIMessages.SourceFirstPackageSelectionDialogField_ChooseSourceContainerDialog_description);
		dialog.addFilter(filter);
		dialog.setInput(JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()));
		dialog.setInitialSelection(initElement);

		if (dialog.open() == Window.OK) {
			Object element= dialog.getFirstResult();
			if (element instanceof IJavaProject) {
				IJavaProject jproject= (IJavaProject)element;
				return jproject.getPackageFragmentRoot(jproject.getProject());
			} else if (element instanceof IPackageFragmentRoot) {
				return (IPackageFragmentRoot)element;
			}
			return null;
		}
		return null;
	}


	public IPackageFragment getSelected() {
		IPackageFragment res= fPackageSelection.getPackageFragment();
		return res;
	}

	public IPackageFragmentRoot getSelectedFragmentRoot() {
		return fSourceFolderSelection.getRoot();
	}

	public void setSelected(IPackageFragment newSelection) {
		fPackageSelection.setPackageFragment(newSelection);
		fSourceFolderSelection.setRoot(searchSourcePackageFragmentRoot(newSelection));
	}

	public void createControl(Composite parent, int nOfColumns, int textWidth) {
		fShell= parent.getShell();
		PixelConverter converter= new PixelConverter(parent);
		fSourceFolderSelection.doFillIntoGrid(parent, nOfColumns, textWidth);
		LayoutUtil.setWidthHint(fSourceFolderSelection.getTextControl(null), converter.convertWidthInCharsToPixels(60));

		fPackageSelection.doFillIntoGrid(parent, nOfColumns, textWidth);
		LayoutUtil.setWidthHint(fPackageSelection.getTextControl(null), converter.convertWidthInCharsToPixels(60));
	}

}

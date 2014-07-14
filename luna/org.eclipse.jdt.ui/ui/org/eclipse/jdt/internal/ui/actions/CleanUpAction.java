/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Alex Blewitt - Bug 133277 Allow Sort Members to be performed on package and project levels
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.IWorkingSet;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringExecutionStarter;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;
import org.eclipse.jdt.ui.cleanup.ICleanUp;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.browsing.LogicalPackage;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.util.ElementValidator;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.workingsets.IWorkingSetIDs;

public abstract class CleanUpAction extends SelectionDispatchAction {

	private JavaEditor fEditor;

	public CleanUpAction(IWorkbenchSite site) {
		super(site);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call
	 * this constructor.
	 *
	 * @param editor
	 *            the Java editor
	 */
	public CleanUpAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(getCompilationUnit(fEditor) != null);
	}

	/**
	 * @return the name of this action, not <b>null</b>
	 */
	protected abstract String getActionName();

	/**
	 * @param units
	 *            the units to clean up
	 * @return the clean ups to be performed or <b>null</b> if none to be
	 *         performed
	 */
	protected abstract ICleanUp[] getCleanUps(ICompilationUnit[] units);

	protected void performRefactoring(ICompilationUnit[] units, ICleanUp[] cleanUps) throws InvocationTargetException {
		RefactoringExecutionStarter.startCleanupRefactoring(units, cleanUps, false, getShell(), false, getActionName());
	}

	@Override
	public void run(ITextSelection selection) {
		ICompilationUnit cu= getCompilationUnit(fEditor);
		if (cu != null) {
			run(cu);
		}
	}

	@Override
	public void run(IStructuredSelection selection) {
		ICompilationUnit[] cus= getCompilationUnits(selection);
		if (cus.length == 0) {
			MessageDialog.openInformation(getShell(), getActionName(), ActionMessages.CleanUpAction_EmptySelection_description);
		} else if (cus.length == 1) {
			run(cus[0]);
		} else {
			runOnMultiple(cus);
		}
	}

	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(getCompilationUnit(fEditor) != null);
	}

	@Override
	public void selectionChanged(IStructuredSelection selection) {
		setEnabled(isEnabled(selection));
	}

	private boolean isEnabled(IStructuredSelection selection) {
		Object[] selected= selection.toArray();
		for (int i= 0; i < selected.length; i++) {
			try {
				if (selected[i] instanceof IJavaElement) {
					IJavaElement elem= (IJavaElement)selected[i];
					if (elem.exists()) {
						switch (elem.getElementType()) {
							case IJavaElement.TYPE:
								return elem.getParent().getElementType() == IJavaElement.COMPILATION_UNIT; // for browsing perspective
							case IJavaElement.COMPILATION_UNIT:
								return true;
							case IJavaElement.IMPORT_CONTAINER:
								return true;
							case IJavaElement.PACKAGE_FRAGMENT:
							case IJavaElement.PACKAGE_FRAGMENT_ROOT:
								IPackageFragmentRoot root= (IPackageFragmentRoot)elem.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
								return (root.getKind() == IPackageFragmentRoot.K_SOURCE);
							case IJavaElement.JAVA_PROJECT:
								// https://bugs.eclipse.org/bugs/show_bug.cgi?id=65638
								return true;
						}
					}
				} else if (selected[i] instanceof LogicalPackage) {
					return true;
				} else if (selected[i] instanceof IWorkingSet) {
					IWorkingSet workingSet= (IWorkingSet) selected[i];
					return IWorkingSetIDs.JAVA.equals(workingSet.getId());
				}
			} catch (JavaModelException e) {
				if (!e.isDoesNotExist()) {
					JavaPlugin.log(e);
				}
			}
		}
		return false;
	}

	private void run(ICompilationUnit cu) {
		if (!ActionUtil.isEditable(fEditor, getShell(), cu))
			return;

		ICleanUp[] cleanUps= getCleanUps(new ICompilationUnit[] {
			cu
		});
		if (cleanUps == null)
			return;

		if (!ElementValidator.check(cu, getShell(), getActionName(), fEditor != null))
			return;

		try {
			performRefactoring(new ICompilationUnit[] {
				cu
			}, cleanUps);
		} catch (InvocationTargetException e) {
			JavaPlugin.log(e);
			if (e.getCause() instanceof CoreException)
				showUnexpectedError((CoreException)e.getCause());
		}
	}

	private void runOnMultiple(final ICompilationUnit[] cus) {
		ICleanUp[] cleanUps= getCleanUps(cus);
		if (cleanUps == null)
			return;

		MultiStatus status= new MultiStatus(JavaUI.ID_PLUGIN, IStatus.OK, ActionMessages.CleanUpAction_MultiStateErrorTitle, null);
		for (int i= 0; i < cus.length; i++) {
			ICompilationUnit cu= cus[i];

			if (!ActionUtil.isOnBuildPath(cu)) {
				String cuLocation= BasicElementLabels.getPathLabel(cu.getPath(), false);
				String message= Messages.format(ActionMessages.CleanUpAction_CUNotOnBuildpathMessage, cuLocation);
				status.add(new Status(IStatus.INFO, JavaUI.ID_PLUGIN, IStatus.ERROR, message, null));
			}
		}
		if (!status.isOK()) {
			ErrorDialog.openError(getShell(), getActionName(), null, status);
			return;
		}

		try {
			performRefactoring(cus, cleanUps);
		} catch (InvocationTargetException e) {
			JavaPlugin.log(e);
			if (e.getCause() instanceof CoreException)
				showUnexpectedError((CoreException)e.getCause());
		}
	}

	private void showUnexpectedError(CoreException e) {
		String message2= Messages.format(ActionMessages.CleanUpAction_UnexpectedErrorMessage, e.getStatus().getMessage());
		IStatus status= new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.ERROR, message2, null);
		ErrorDialog.openError(getShell(), getActionName(), null, status);
	}

	public ICompilationUnit[] getCompilationUnits(IStructuredSelection selection) {
		HashSet<IJavaElement> result= new HashSet<IJavaElement>();
		Object[] selected= selection.toArray();
		for (int i= 0; i < selected.length; i++) {
			Object element= selected[i];
			collectCompilationUnits(element, result);
		}
		return result.toArray(new ICompilationUnit[result.size()]);
	}

	private void collectCompilationUnits(Object element, Collection<IJavaElement> result) {
		try {
			if (element instanceof IJavaElement) {
				IJavaElement elem= (IJavaElement)element;
				if (elem.exists()) {
					switch (elem.getElementType()) {
						case IJavaElement.TYPE:
							if (elem.getParent().getElementType() == IJavaElement.COMPILATION_UNIT) {
								result.add(elem.getParent());
							}
							break;
						case IJavaElement.COMPILATION_UNIT:
							result.add(elem);
							break;
						case IJavaElement.IMPORT_CONTAINER:
							result.add(elem.getParent());
							break;
						case IJavaElement.PACKAGE_FRAGMENT:
							collectCompilationUnits((IPackageFragment)elem, result);
							break;
						case IJavaElement.PACKAGE_FRAGMENT_ROOT:
							collectCompilationUnits((IPackageFragmentRoot)elem, result);
							break;
						case IJavaElement.JAVA_PROJECT:
							IPackageFragmentRoot[] roots= ((IJavaProject)elem).getPackageFragmentRoots();
							for (int k= 0; k < roots.length; k++) {
								collectCompilationUnits(roots[k], result);
							}
							break;
					}
				}
			} else if (element instanceof LogicalPackage) {
				IPackageFragment[] packageFragments= ((LogicalPackage)element).getFragments();
				for (int k= 0; k < packageFragments.length; k++) {
					IPackageFragment pack= packageFragments[k];
					if (pack.exists()) {
						collectCompilationUnits(pack, result);
					}
				}
			} else if (element instanceof IWorkingSet) {
				IWorkingSet workingSet= (IWorkingSet) element;
				IAdaptable[] elements= workingSet.getElements();
				for (int j= 0; j < elements.length; j++) {
					collectCompilationUnits(elements[j], result);
				}
			}
		} catch (JavaModelException e) {
			if (JavaModelUtil.isExceptionToBeLogged(e))
				JavaPlugin.log(e);
		}
	}

	private void collectCompilationUnits(IPackageFragment pack, Collection<IJavaElement> result) throws JavaModelException {
		result.addAll(Arrays.asList(pack.getCompilationUnits()));
	}

	private void collectCompilationUnits(IPackageFragmentRoot root, Collection<IJavaElement> result) throws JavaModelException {
		if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
			IJavaElement[] children= root.getChildren();
			for (int i= 0; i < children.length; i++) {
				collectCompilationUnits((IPackageFragment)children[i], result);
			}
		}
	}

	private static ICompilationUnit getCompilationUnit(JavaEditor editor) {
		IJavaElement element= JavaUI.getEditorInputJavaElement(editor.getEditorInput());
		if (!(element instanceof ICompilationUnit))
			return null;

		return (ICompilationUnit)element;
	}

}

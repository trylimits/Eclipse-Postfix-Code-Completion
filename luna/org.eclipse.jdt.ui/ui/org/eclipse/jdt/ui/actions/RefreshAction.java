/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.IShellProvider;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.packageview.PackageFragmentRootContainer;

/**
 * Action for refreshing the workspace from the local file system for
 * the selected resources and all of their descendants. This action
 * also considers external Jars managed by the Java Model.
 * <p>
 * Action is applicable to selections containing resources and Java
 * elements down to compilation units.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class RefreshAction extends SelectionDispatchAction {

	/**
	 * As the JDT  RefreshAction is already API, we have to wrap the workbench action.
	 */
	private static class WrappedWorkbenchRefreshAction extends org.eclipse.ui.actions.RefreshAction {

		public WrappedWorkbenchRefreshAction(IShellProvider provider) {
			super(provider);
		}

		@Override
		protected List<IResource> getSelectedResources() {
			List<IResource> selectedResources= super.getSelectedResources();
			if (!getStructuredSelection().isEmpty() && selectedResources.size() == 1 && selectedResources.get(0) instanceof IWorkspaceRoot) {
				selectedResources= Collections.emptyList(); // Refresh action refreshes root when it can't find any resources in selection
			}

			ArrayList<IResource> allResources= new ArrayList<IResource>(selectedResources);
			addWorkingSetResources(allResources);
			return allResources;
		}

		private void addWorkingSetResources(List<IResource> selectedResources) {
			Object[] elements= getStructuredSelection().toArray();
			for (int i= 0; i < elements.length; i++) {
				Object curr= elements[i];
				if (curr instanceof IWorkingSet) {
					IAdaptable[] members= ((IWorkingSet) curr).getElements();
					for (int k= 0; k < members.length; k++) {
						IResource adapted= (IResource) members[k].getAdapter(IResource.class);
						if (adapted != null) {
							selectedResources.add(adapted);
						}
					}
				}
			}
		}

		public void run(IProgressMonitor monitor) throws CoreException, OperationCanceledException {
			try {
				final IStatus[] errorStatus= new IStatus[] { Status.OK_STATUS };
				createOperation(errorStatus).run(monitor);
				if (errorStatus[0].matches(IStatus.ERROR)) {
					throw new CoreException(errorStatus[0]);
				}
			} catch (InvocationTargetException e) {
				Throwable targetException= e.getTargetException();
				if (targetException instanceof CoreException)
					throw (CoreException) targetException;
				throw new CoreException(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, ActionMessages.RefreshAction_error_workbenchaction_message, targetException));
			} catch (InterruptedException e) {
				throw new OperationCanceledException();
			}
		}
	}

	/**
	 * Creates a new <code>RefreshAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public RefreshAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.RefreshAction_label);
		setToolTipText(ActionMessages.RefreshAction_toolTip);
		JavaPluginImages.setLocalImageDescriptors(this, "refresh.gif");//$NON-NLS-1$
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.REFRESH_ACTION);
	}

	/* (non-Javadoc)
	 * Method declared in SelectionDispatchAction
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		setEnabled(checkEnabled(selection));
	}

	private boolean checkEnabled(IStructuredSelection selection) {
		if (selection.isEmpty())
			return true;

		boolean okToRefresh= false;
		for (Iterator<?> iter= selection.iterator(); iter.hasNext();) {
			Object element= iter.next();
			if (element instanceof IWorkingSet) {
				// don't inspect working sets any deeper.
				okToRefresh= true;
			} else if (element instanceof IPackageFragmentRoot) {
				// on internal folders/JARs we do a normal refresh, and Java archive refresh on external
				okToRefresh= true;
			} else if (element instanceof PackageFragmentRootContainer) {
				// too expensive to look at children. assume we can refresh
				okToRefresh= true;
			} else if (element instanceof IAdaptable) { // test for IAdaptable last (types before are IAdaptable as well)
				IResource resource= (IResource)((IAdaptable)element).getAdapter(IResource.class);
				okToRefresh|= resource != null && (resource.getType() != IResource.PROJECT || ((IProject) resource).isOpen());
			} else {
				// nothing to say;
			}
		}
		return okToRefresh;
	}

	/* (non-Javadoc)
	 * Method declared in SelectionDispatchAction
	 */
	@Override
	public void run(final IStructuredSelection selection) {
		IWorkspaceRunnable operation= new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {
				performRefresh(selection, monitor);
			}
		};
		new WorkbenchRunnableAdapter(operation).runAsUserJob(ActionMessages.RefreshAction_refresh_operation_label, null);
	}

	private void performRefresh(IStructuredSelection selection, IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		monitor.beginTask(ActionMessages.RefreshAction_progressMessage, 2);

		WrappedWorkbenchRefreshAction workbenchAction= new WrappedWorkbenchRefreshAction(getSite());
		workbenchAction.selectionChanged(selection);
		workbenchAction.run(new SubProgressMonitor(monitor, 1));
		refreshJavaElements(selection, new SubProgressMonitor(monitor, 1));
	}

	private void refreshJavaElements(IStructuredSelection selection, IProgressMonitor monitor) throws JavaModelException {
		Object[] selectedElements= selection.toArray();
		ArrayList<IJavaElement> javaElements= new ArrayList<IJavaElement>();
		for (int i= 0; i < selectedElements.length; i++) {
			Object curr= selectedElements[i];
			if (curr instanceof IPackageFragmentRoot) {
				javaElements.add((IPackageFragmentRoot) curr);
			} else if (curr instanceof PackageFragmentRootContainer) {
				javaElements.addAll(Arrays.asList(((PackageFragmentRootContainer) curr).getPackageFragmentRoots()));
			} else if (curr instanceof IWorkingSet) {
				IAdaptable[] members= ((IWorkingSet) curr).getElements();
				for (int k= 0; k < members.length; k++) {
					IJavaElement adapted= (IJavaElement)members[k].getAdapter(IJavaElement.class);
					if (adapted instanceof IPackageFragmentRoot) {
						javaElements.add(adapted);
					}
				}
			}
 		}
		if (!javaElements.isEmpty()) {
			IJavaModel model= JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
			model.refreshExternalArchives(javaElements.toArray(new IJavaElement[javaElements.size()]), monitor);
		}
	}
}
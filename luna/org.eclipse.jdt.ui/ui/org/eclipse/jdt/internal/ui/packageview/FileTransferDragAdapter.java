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
package org.eclipse.jdt.internal.ui.packageview;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.util.TransferDragSourceListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.actions.WorkspaceModifyOperation;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;

import org.eclipse.jdt.internal.corext.util.Resources;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

/**
 * Drag support class to allow dragging of files and folder from
 * the packages view to another application.
 */
public class FileTransferDragAdapter extends DragSourceAdapter implements TransferDragSourceListener {

	private ISelectionProvider fProvider;

	public FileTransferDragAdapter(ISelectionProvider provider) {
		fProvider= provider;
		Assert.isNotNull(fProvider);
	}

	public Transfer getTransfer() {
		return FileTransfer.getInstance();
	}

	@Override
	public void dragStart(DragSourceEvent event) {
		event.doit= isDragable(fProvider.getSelection());
	}

	private boolean isDragable(ISelection s) {
		if (!(s instanceof IStructuredSelection))
			return false;
		IStructuredSelection selection= (IStructuredSelection)s;
		for (Iterator<?> iter= selection.iterator(); iter.hasNext();) {
			Object element= iter.next();
			if (element instanceof IJavaElement) {
				IJavaElement jElement= (IJavaElement)element;
				int type= jElement.getElementType();
				// valid elements are: roots, units and types. Don't allow dragging
				// projects outside of eclipse
				if (type != IJavaElement.PACKAGE_FRAGMENT_ROOT &&
					type != IJavaElement.COMPILATION_UNIT && type != IJavaElement.TYPE)
					return false;
				IPackageFragmentRoot root= (IPackageFragmentRoot)jElement.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
				if (root != null && (root.isArchive() || root.isExternal()))
					return false;
			} else if (element instanceof IProject) {
				return false;
			}
		}
		List<IResource> resources= convertIntoResources(selection);
		return resources.size() == selection.size();
	}

	@Override
	public void dragSetData(DragSourceEvent event){
		List<IResource> elements= getResources();
		if (elements == null || elements.size() == 0) {
			event.data= null;
			return;
		}

		event.data= getResourceLocations(elements);
	}

	private static String[] getResourceLocations(List<IResource> resources) {
		return Resources.getLocationOSStrings(resources.toArray(new IResource[resources.size()]));
	}

	@Override
	public void dragFinished(DragSourceEvent event) {
		if (!event.doit)
			return;

		if (event.detail == DND.DROP_MOVE) {
			// http://bugs.eclipse.org/bugs/show_bug.cgi?id=30543
			// handleDropMove(event);
		} else if (event.detail == DND.DROP_TARGET_MOVE) {
			handleRefresh();
		}
	}

	/* package */ void handleDropMove() {
		final List<IResource> elements= getResources();
		if (elements == null || elements.size() == 0)
			return;

		WorkspaceModifyOperation op= new WorkspaceModifyOperation() {
			@Override
			public void execute(IProgressMonitor monitor) throws CoreException {
				try {
					monitor.beginTask(PackagesMessages.DragAdapter_deleting, elements.size());
					MultiStatus status= createMultiStatus();
					Iterator<IResource> iter= elements.iterator();
					while(iter.hasNext()) {
						IResource resource= iter.next();
						try {
							monitor.subTask(BasicElementLabels.getPathLabel(resource.getFullPath(), true));
							resource.delete(true, null);

						} catch (CoreException e) {
							status.add(e.getStatus());
						} finally {
							monitor.worked(1);
						}
					}
					if (!status.isOK()) {
						throw new CoreException(status);
					}
				} finally {
					monitor.done();
				}
			}
		};
		runOperation(op, true, false);
	}

	private void handleRefresh() {
		final Set<IResource> roots= collectRoots(getResources());

		WorkspaceModifyOperation op= new WorkspaceModifyOperation() {
			@Override
			public void execute(IProgressMonitor monitor) throws CoreException {
				try {
					monitor.beginTask(PackagesMessages.DragAdapter_refreshing, roots.size());
					MultiStatus status= createMultiStatus();
					Iterator<IResource> iter= roots.iterator();
					while (iter.hasNext()) {
						IResource r= iter.next();
						try {
							r.refreshLocal(IResource.DEPTH_ONE, new SubProgressMonitor(monitor, 1));
						} catch (CoreException e) {
							status.add(e.getStatus());
						}
					}
					if (!status.isOK()) {
						throw new CoreException(status);
					}
				} finally {
					monitor.done();
				}
			}
		};

		runOperation(op, true, false);
	}

	protected Set<IResource> collectRoots(final List<IResource> elements) {
		final Set<IResource> roots= new HashSet<IResource>(10);

		Iterator<IResource> iter= elements.iterator();
		while (iter.hasNext()) {
			IResource resource= iter.next();
			IResource parent= resource.getParent();
			if (parent == null) {
				roots.add(resource);
			} else {
				roots.add(parent);
			}
		}
		return roots;
	}

	private List<IResource> getResources() {
		ISelection s= fProvider.getSelection();
		if (!(s instanceof IStructuredSelection))
			return null;

		return convertIntoResources((IStructuredSelection)s);
	}

	private List<IResource> convertIntoResources(IStructuredSelection selection) {
		List<IResource> result= new ArrayList<IResource>(selection.size());
		for (Iterator<?> iter= selection.iterator(); iter.hasNext();) {
			Object o= iter.next();
			IResource r= null;
			if (o instanceof IResource) {
				r= (IResource)o;
			} else if (o instanceof IAdaptable) {
				r= (IResource)((IAdaptable)o).getAdapter(IResource.class);
			}
			// Only add resource for which we have a location
			// in the local file system.
			if (r != null && r.getLocation() != null) {
				result.add(r);
			}
		}
		return result;
	}

	private MultiStatus createMultiStatus() {
		return new MultiStatus(JavaPlugin.getPluginId(),
			IStatus.OK, PackagesMessages.DragAdapter_problem, null);
	}

	private void runOperation(IRunnableWithProgress op, boolean fork, boolean cancelable) {
		try {
			Shell parent= JavaPlugin.getActiveWorkbenchShell();
			new ProgressMonitorDialog(parent).run(fork, cancelable, op);
		} catch (InvocationTargetException e) {
			String message= PackagesMessages.DragAdapter_problem;
			String title= PackagesMessages.DragAdapter_problemTitle;
			ExceptionHandler.handle(e, title, message);
		} catch (InterruptedException e) {
			// Do nothing. Operation has been canceled by user.
		}
	}
}

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

import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.util.TransferDropTargetListener;
import org.eclipse.jface.viewers.StructuredViewer;

import org.eclipse.ui.actions.CopyFilesAndFoldersOperation;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.Resources;

import org.eclipse.jdt.internal.ui.dnd.JdtViewerDropAdapter;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * Adapter to handle file drop from other applications.
 */
public class FileTransferDropAdapter extends JdtViewerDropAdapter implements TransferDropTargetListener {

	public FileTransferDropAdapter(StructuredViewer viewer) {
		super(viewer);

		setScrollEnabled(true);
		setExpandEnabled(true);
		setFeedbackEnabled(false);
	}

	//---- TransferDropTargetListener interface ---------------------------------------

	/**
	 * {@inheritDoc}
	 */
	public Transfer getTransfer() {
		return FileTransfer.getInstance();
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isEnabled(DropTargetEvent event) {
		Object target= event.item != null ? event.item.getData() : null;
		if (target == null)
			return false;
		return target instanceof IJavaElement || target instanceof IResource;
	}

	//---- Actual DND -----------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean validateDrop(Object target, int operation, TransferData transferType) {
		return determineOperation(target, operation, transferType, DND.DROP_MOVE | DND.DROP_LINK | DND.DROP_COPY) != DND.DROP_NONE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected int determineOperation(Object target, int operation, TransferData transferType, int operations) {

		boolean isPackageFragment= target instanceof IPackageFragment;
		boolean isJavaProject= target instanceof IJavaProject;
		boolean isPackageFragmentRoot= target instanceof IPackageFragmentRoot;
		boolean isContainer= target instanceof IContainer;

		if (!(isPackageFragment || isJavaProject || isPackageFragmentRoot || isContainer))
			return DND.DROP_NONE;

		if (isContainer) {
			IContainer container= (IContainer)target;
			if (container.isAccessible() && !Resources.isReadOnly(container))
				return DND.DROP_COPY;
		} else {
			IJavaElement element= (IJavaElement)target;
			if (!element.isReadOnly())
				return DND.DROP_COPY;
		}

		return DND.DROP_NONE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean performDrop(final Object data) {
		try {
			final int currentOperation= getCurrentOperation();

			if (data == null || !(data instanceof String[]) || currentOperation != DND.DROP_COPY)
				return false;

			final IContainer target= getActualTarget(getCurrentTarget());
			if (target == null)
				return false;

			// Run the import operation asynchronously.
			// Otherwise the drag source (e.g., Windows Explorer) will be blocked
			// while the operation executes. Fixes bug 35796.
			Display.getCurrent().asyncExec(new Runnable() {
				public void run() {
					getShell().forceActive();
					new CopyFilesAndFoldersOperation(getShell()).copyOrLinkFiles((String[])data, target, currentOperation);
				}
			});

			return true;
		} catch (JavaModelException e) {
			String title= PackagesMessages.DropAdapter_errorTitle;
			String message= PackagesMessages.DropAdapter_errorMessage;
			ExceptionHandler.handle(e, getShell(), title, message);
			return false;
		}
	}

	private IContainer getActualTarget(Object dropTarget) throws JavaModelException{
		if (dropTarget instanceof IContainer)
			return (IContainer)dropTarget;
		else if (dropTarget instanceof IJavaElement)
			return getActualTarget(((IJavaElement)dropTarget).getCorrespondingResource());
		return null;
	}

	private Shell getShell() {
		return getViewer().getControl().getShell();
	}
}

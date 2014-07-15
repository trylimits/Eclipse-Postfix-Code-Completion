/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.dnd;

import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.util.DelegatingDropAdapter;
import org.eclipse.jface.util.TransferDropTargetListener;
import org.eclipse.jface.viewers.StructuredViewer;

import org.eclipse.ui.part.PluginTransfer;
import org.eclipse.ui.views.navigator.LocalSelectionTransfer;

import org.eclipse.jdt.internal.ui.packageview.FileTransferDropAdapter;
import org.eclipse.jdt.internal.ui.packageview.PluginTransferDropAdapter;
import org.eclipse.jdt.internal.ui.packageview.SelectionTransferDropAdapter;

public class JdtViewerDropSupport {

	private final StructuredViewer fViewer;
	private final DelegatingDropAdapter fDelegatingDropAdapter;
	private final SelectionTransferDropAdapter fReorgDropListener;
	private boolean fStarted;

	public JdtViewerDropSupport(StructuredViewer viewer) {
		fViewer= viewer;

		fDelegatingDropAdapter= new DelegatingDropAdapter();
		fReorgDropListener= new SelectionTransferDropAdapter(fViewer);
		fDelegatingDropAdapter.addDropTargetListener(fReorgDropListener);
		fDelegatingDropAdapter.addDropTargetListener(new FileTransferDropAdapter(fViewer));
		fDelegatingDropAdapter.addDropTargetListener(new PluginTransferDropAdapter(fViewer));

		fStarted= false;
	}

	public void addDropTargetListener(TransferDropTargetListener listener) {
		Assert.isLegal(!fStarted);

		fDelegatingDropAdapter.addDropTargetListener(listener);
	}

	public void start() {
		Assert.isLegal(!fStarted);

		int ops= DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_LINK | DND.DROP_DEFAULT;

		Transfer[] transfers= new Transfer[] {
			LocalSelectionTransfer.getInstance(),
			FileTransfer.getInstance(),
			PluginTransfer.getInstance()};

		fViewer.addDropSupport(ops, transfers, fDelegatingDropAdapter);

		fStarted= true;
	}

	public void setFeedbackEnabled(boolean enabled) {
		fReorgDropListener.setFeedbackEnabled(enabled);
	}

}

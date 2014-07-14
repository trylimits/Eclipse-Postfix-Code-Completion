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
package org.eclipse.jdt.internal.ui.dnd;

import org.eclipse.swt.dnd.DragSourceEvent;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.util.DelegatingDragAdapter;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;

public class JdtViewerDragAdapter extends DelegatingDragAdapter {

	private StructuredViewer fViewer;

	public JdtViewerDragAdapter(StructuredViewer viewer) {
		super();
		Assert.isNotNull(viewer);
		fViewer= viewer;
	}

	@Override
	public void dragStart(DragSourceEvent event) {
		IStructuredSelection selection= (IStructuredSelection)fViewer.getSelection();
		if (selection.isEmpty()) {
			event.doit= false;
			return;
		}
		super.dragStart(event);
	}
}

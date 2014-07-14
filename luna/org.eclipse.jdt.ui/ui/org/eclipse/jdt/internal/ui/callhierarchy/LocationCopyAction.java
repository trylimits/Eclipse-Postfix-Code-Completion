/*******************************************************************************
 * Copyright (c) 2006, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import java.util.Iterator;

import org.eclipse.swt.SWTError;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;

import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchCommandConstants;


import org.eclipse.jdt.internal.corext.callhierarchy.CallLocation;

/**
 * Copies the selection from the location viewer.
 */
class LocationCopyAction extends Action {
	private final Clipboard fClipboard;
	private final IViewSite fViewSite;
	private final LocationViewer fLocationViewer;

	LocationCopyAction(IViewSite viewSite, Clipboard clipboard, LocationViewer locationViewer) {
		fClipboard= clipboard;
		fViewSite= viewSite;
		fLocationViewer= locationViewer;

		setText(CallHierarchyMessages.LocationCopyAction_copy);
		setActionDefinitionId(IWorkbenchCommandConstants.EDIT_COPY);
		setEnabled(!fLocationViewer.getSelection().isEmpty());

		locationViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				setEnabled(! event.getSelection().isEmpty());
			}
		});
	}

	@Override
	public void run() {
		IStructuredSelection selection= (IStructuredSelection) fLocationViewer.getSelection();
		StringBuffer buf= new StringBuffer();
		for (Iterator<?> iterator= selection.iterator(); iterator.hasNext();) {
			CallLocation location= (CallLocation) iterator.next();
			buf.append(location.getLineNumber()).append('\t').append(location.getCallText());
			buf.append('\n');
		}
		TextTransfer plainTextTransfer = TextTransfer.getInstance();
		try {
			fClipboard.setContents(
					new String[]{ CopyCallHierarchyAction.convertLineTerminators(buf.toString()) },
					new Transfer[]{ plainTextTransfer });
		} catch (SWTError e){
			if (e.code != DND.ERROR_CANNOT_SET_CLIPBOARD)
				throw e;
			if (MessageDialog.openQuestion(fViewSite.getShell(), CallHierarchyMessages.CopyCallHierarchyAction_problem, CallHierarchyMessages.CopyCallHierarchyAction_clipboard_busy))
				run();
		}
	}
}
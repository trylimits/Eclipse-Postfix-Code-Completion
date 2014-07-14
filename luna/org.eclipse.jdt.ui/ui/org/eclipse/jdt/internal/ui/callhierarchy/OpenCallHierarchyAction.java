/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 *          (report 36180: Callers/Callees view)
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.callhierarchy.CallHierarchy;

import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * This action opens a call hierarchy on the selected members.
 */
public class OpenCallHierarchyAction extends SelectionDispatchAction {

	private JavaEditor fEditor;

	/**
	 * Creates a new <code>OpenCallHierarchyAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type
	 * <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public OpenCallHierarchyAction(IWorkbenchSite site) {
		super(site);
		setText(CallHierarchyMessages.OpenCallHierarchyAction_label);
		setToolTipText(CallHierarchyMessages.OpenCallHierarchyAction_tooltip);
		setDescription(CallHierarchyMessages.OpenCallHierarchyAction_description);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.CALL_HIERARCHY_OPEN_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor internal
	 */
	public OpenCallHierarchyAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor));
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		// Do nothing
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		setEnabled(CallHierarchy.arePossibleInputElements(selection.toList()));
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(ITextSelection selection) {
		ITypeRoot input= SelectionConverter.getInput(fEditor);
		if (!ActionUtil.isProcessable(getShell(), input))
			return;

		try {
			IJavaElement[] elements= SelectionConverter.codeResolveOrInputForked(fEditor);
			if (elements == null)
				return;
			List<IJavaElement> candidates= new ArrayList<IJavaElement>(elements.length);
			for (int i= 0; i < elements.length; i++) {
				IJavaElement element= elements[i];
				if (CallHierarchy.isPossibleInputElement(element)) {
					candidates.add(element);
				}
			}
			if (candidates.isEmpty()) {
				IJavaElement enclosingMethod= getEnclosingMethod(input, selection);
				if (enclosingMethod != null) {
					candidates.add(enclosingMethod);
				}
			}
			CallHierarchyUI.openSelectionDialog(candidates.toArray(new IMember[candidates.size()]), getSite().getWorkbenchWindow());

		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(),
					CallHierarchyMessages.OpenCallHierarchyAction_dialog_title,
					ActionMessages.SelectionConverter_codeResolve_failed);
		} catch (InterruptedException e) {
			// cancelled
		}
	}

	private IJavaElement getEnclosingMethod(ITypeRoot input, ITextSelection selection) {
		try {
			IJavaElement enclosingElement= input.getElementAt(selection.getOffset());
			if (enclosingElement instanceof IMethod || enclosingElement instanceof IInitializer || enclosingElement instanceof IField) {
				// opening on the enclosing type would be too confusing (since the type resolves to the constructors)
				return enclosingElement;
			}
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		}

		return null;
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(IStructuredSelection selection) {
		List<?> elements= selection.toList();
		if (!CallHierarchy.arePossibleInputElements(elements)) {
			elements= Collections.EMPTY_LIST;
		}

		IMember[] members= elements.toArray(new IMember[elements.size()]);
		if (!ActionUtil.areProcessable(getShell(), members))
			return;

		CallHierarchyUI.openView(members, getSite().getWorkbenchWindow());
	}

}

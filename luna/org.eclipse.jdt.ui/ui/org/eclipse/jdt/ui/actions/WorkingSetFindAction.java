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
package org.eclipse.jdt.ui.actions;

import org.eclipse.core.runtime.Assert;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

/**
 * Wraps a <code>JavaElementSearchActions</code> to find its results
 * in the specified working set.
 * <p>
 * The action is applicable to selections and Search view entries
 * representing a Java element.
 *
 * <p>
 * Note: This class is for internal use only. Clients should not use this class.
 * </p>
 *
 * @since 2.0
 *
 * @noinstantiate This class is not intended to be instantiated by clients.
 * @noextend This class is not intended to be subclassed by clients.
 */
public class WorkingSetFindAction extends FindAction {

	private FindAction fAction;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param site the site
	 * @param action the action
	 * @param workingSetName the working set name
	 */
	public WorkingSetFindAction(IWorkbenchSite site, FindAction action, String workingSetName) {
		super(site);
		init(action, workingSetName);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor (internal type)
	 * @param action the action
	 * @param workingSetName the working set name
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public WorkingSetFindAction(JavaEditor editor, FindAction action, String workingSetName) {
		super(editor);
		init(action, workingSetName);
	}

	@Override
	Class<?>[] getValidTypes() {
		return null; // ignore, we override canOperateOn
	}

	@Override
	void init() {
		// ignore: do our own init in 'init(FindAction, String)'
	}

	private void init(FindAction action, String workingSetName) {
		Assert.isNotNull(action);
		fAction= action;
		setText(workingSetName);
		setImageDescriptor(action.getImageDescriptor());
		setToolTipText(action.getToolTipText());
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.WORKING_SET_FIND_ACTION);
	}

	@Override
	public void run(IJavaElement element) {
		fAction.run(element);
	}

	@Override
	boolean canOperateOn(IJavaElement element) {
		return fAction.canOperateOn(element);
	}

	@Override
	int getLimitTo() {
		return -1;
	}

	@Override
	String getOperationUnavailableMessage() {
		return fAction.getOperationUnavailableMessage();
	}

}

/*******************************************************************************
 * Copyright (c) 2008, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.javaeditor;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.breadcrumb.IBreadcrumb;


/**
 * Action to set the focus into the editor breadcrumb.
 * The breadcrumb is made visible if it is hidden.
 *
 * @since 3.4
 */
public class ShowInBreadcrumbAction extends Action {

	private final JavaEditor fEditor;

	public ShowInBreadcrumbAction(JavaEditor editor) {
		super(JavaEditorMessages.ShowInBreadcrumbAction_label);
		fEditor= editor;
		setEnabled(true);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.SHOW_IN_BREADCRUMB_ACTION);
	}

	/*
	 * @see org.eclipse.jface.action.Action#run()
	 */
	@Override
	public void run() {
		IBreadcrumb breadcrumb= fEditor.getBreadcrumb();
		if (breadcrumb == null)
			return;

		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(getPreferenceKey(), true);

		breadcrumb.activate();
	}

	/**
	 * Returns the preference key for the breadcrumb. The
	 * value depends on the current perspective.
	 *
	 * @return the preference key or <code>null</code> if there's no perspective
	 */
	private String getPreferenceKey() {
		return fEditor.getBreadcrumbPreferenceKey();
	}

}

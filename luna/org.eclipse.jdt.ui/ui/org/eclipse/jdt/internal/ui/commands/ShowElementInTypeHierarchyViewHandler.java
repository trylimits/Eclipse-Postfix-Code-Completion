/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.ui.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import org.eclipse.ui.IWorkbenchWindow;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.OpenTypeHierarchyUtil;

/**
 * A command handler to show a java element in the type hierarchy view.
 *
 * @since 3.2
 */
public class ShowElementInTypeHierarchyViewHandler extends AbstractHandler {

	private static final String PARAM_ID_ELEMENT_REF= "elementRef"; //$NON-NLS-1$

	public Object execute(ExecutionEvent event) throws ExecutionException {

		IWorkbenchWindow window= JavaPlugin.getActiveWorkbenchWindow();
		if (window == null)
			return null;

		IJavaElement javaElement= (IJavaElement) event.getObjectParameterForExecution(PARAM_ID_ELEMENT_REF);

		OpenTypeHierarchyUtil.open(javaElement, window);

		return null;
	}
}

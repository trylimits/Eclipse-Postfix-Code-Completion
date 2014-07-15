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
package org.eclipse.jdt.internal.ui.browsing;

import java.util.List;

import org.eclipse.swt.widgets.Widget;

/**
 * Allows accessing the PackagesViewTableViewer and the
 * PackagesViewTreeViewer with identical API.
 *
 * @since 2.1
 */
interface IPackagesViewViewer {

	public void mapElement(Object element, Widget item);

	public void unmapElement(Object element, Widget item);

	public Widget doFindInputItem(Object element);

	public Widget doFindItem(Object element);

	public void doUpdateItem(Widget item, Object element, boolean fullMap);

	@SuppressWarnings("rawtypes")
	public List getSelectionFromWidget();

	public void internalRefresh(Object element);

	@SuppressWarnings("rawtypes")
	public void setSelectionToWidget(List l, boolean reveal);
}

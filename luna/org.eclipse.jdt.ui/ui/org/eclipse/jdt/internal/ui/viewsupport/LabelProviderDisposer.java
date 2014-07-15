/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.viewsupport;


import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;

import org.eclipse.jface.viewers.ILabelProvider;

/**
 * Helper class to manage images that should be disposed when a control is disposed
 * contol.addWidgetListener(new LabelProviderDisposer(myLabelProvider));
 */
public class LabelProviderDisposer implements DisposeListener {

	private ILabelProvider fLabelProvider;

	public LabelProviderDisposer(ILabelProvider labelProvider) {
		fLabelProvider= labelProvider;
	}

	public void widgetDisposed(DisposeEvent e) {
		fLabelProvider.dispose();
	}
}



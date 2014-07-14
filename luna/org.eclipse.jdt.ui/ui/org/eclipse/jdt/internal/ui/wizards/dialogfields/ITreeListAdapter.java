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
package org.eclipse.jdt.internal.ui.wizards.dialogfields;

import org.eclipse.swt.events.KeyEvent;

/**
 * Change listener used by <code>TreeListDialogField</code>
 * 
 * @param <E> the type of the root elements
 */
public interface ITreeListAdapter<E> {

	/**
	 * A button from the button bar has been pressed.
	 */
	void customButtonPressed(TreeListDialogField<E> field, int index);

	/**
	 * The selection of the list has changed.
	 */
	void selectionChanged(TreeListDialogField<E> field);

	/**
	 * The list has been double clicked
	 */
	void doubleClicked(TreeListDialogField<E> field);

	/**
	 * A key has been pressed
	 */
	void keyPressed(TreeListDialogField<E> field, KeyEvent event);

	Object[] getChildren(TreeListDialogField<E> field, Object element);

	Object getParent(TreeListDialogField<E> field, Object element);

	boolean hasChildren(TreeListDialogField<E> field, Object element);

}

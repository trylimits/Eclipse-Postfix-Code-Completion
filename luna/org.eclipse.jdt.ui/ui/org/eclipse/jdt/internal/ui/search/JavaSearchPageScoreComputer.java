/*******************************************************************************
 * Copyright (c) 2000, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.search;

import org.eclipse.ui.IEditorInput;

import org.eclipse.search.ui.ISearchPageScoreComputer;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.browsing.LogicalPackage;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;

public class JavaSearchPageScoreComputer implements ISearchPageScoreComputer {

	public int computeScore(String id, Object element) {
		if (!JavaSearchPage.EXTENSION_POINT_ID.equals(id))
			// Can't decide
			return ISearchPageScoreComputer.UNKNOWN;

		if (element instanceof IJavaElement || element instanceof IClassFileEditorInput || element instanceof LogicalPackage
				|| (element instanceof IEditorInput && JavaUI.getEditorInputJavaElement((IEditorInput)element) != null))
			return 90;

		return ISearchPageScoreComputer.LOWEST;
	}
}

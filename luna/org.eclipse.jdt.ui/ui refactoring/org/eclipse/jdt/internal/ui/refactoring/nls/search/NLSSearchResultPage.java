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

package org.eclipse.jdt.internal.ui.refactoring.nls.search;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerComparator;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.IShowInTargetList;

import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.search.ui.text.AbstractTextSearchViewPage;
import org.eclipse.search.ui.text.Match;

import org.eclipse.jdt.internal.ui.search.JavaSearchResultPage;
import org.eclipse.jdt.internal.ui.search.TextSearchTableContentProvider;
import org.eclipse.jdt.internal.ui.viewsupport.ColoringLabelProvider;


public class NLSSearchResultPage extends AbstractTextSearchViewPage  implements IAdaptable {

	private TextSearchTableContentProvider fContentProvider;
	private NLSSearchEditorOpener fEditorOpener= new NLSSearchEditorOpener();

	public NLSSearchResultPage() {
		super(AbstractTextSearchViewPage.FLAG_LAYOUT_FLAT);
	}

	/*
	 * @see org.eclipse.search.ui.text.AbstractTextSearchViewPage#showMatch(org.eclipse.search.ui.text.Match,
	 *      int, int)
	 */
	@Override
	protected void showMatch(Match match, int currentOffset, int currentLength, boolean activate) throws PartInitException {
		IEditorPart editor= fEditorOpener.openMatch(match);
		if (editor != null && activate)
			editor.getEditorSite().getPage().activate(editor);
		if (editor instanceof ITextEditor) {
			ITextEditor textEditor= (ITextEditor) editor;
			textEditor.selectAndReveal(currentOffset, currentLength);
		}
	}

	/*
	 * @see org.eclipse.search.ui.text.AbstractTextSearchViewPage#elementsChanged(java.lang.Object[])
	 */
	@Override
	protected void elementsChanged(Object[] objects) {
		if (fContentProvider != null)
			fContentProvider.elementsChanged(objects);
	}

	/*
	 * @see org.eclipse.search.ui.text.AbstractTextSearchViewPage#clear()
	 */
	@Override
	protected void clear() {
		if (fContentProvider != null)
			fContentProvider.clear();
	}

	/*
	 * @see org.eclipse.search.ui.text.AbstractTextSearchViewPage#configureTreeViewer(org.eclipse.jface.viewers.TreeViewer)
	 */
	@Override
	protected void configureTreeViewer(TreeViewer viewer) {
		throw new IllegalStateException("Doesn't support tree mode."); //$NON-NLS-1$
	}

	/*
	 * @see org.eclipse.search.ui.text.AbstractTextSearchViewPage#configureTableViewer(org.eclipse.jface.viewers.TableViewer)
	 */
	@Override
	protected void configureTableViewer(TableViewer viewer) {
		viewer.setComparator(new ViewerComparator() {
			@Override
			public int category(Object element) {
				if (element instanceof FileEntry) {
					return 0;
				} else {
					return 1;
				}
			}
		});
		viewer.setLabelProvider(new ColoringLabelProvider(new NLSSearchResultLabelProvider2(this)));
		fContentProvider= new TextSearchTableContentProvider();
		viewer.setContentProvider(fContentProvider);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class adapter) {
		if (IShowInTargetList.class.equals(adapter)) {
			return JavaSearchResultPage.SHOW_IN_TARGET_LIST;
		}
		return null;
	}


}

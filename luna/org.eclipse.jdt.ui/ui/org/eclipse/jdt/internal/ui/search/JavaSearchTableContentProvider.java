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
package org.eclipse.jdt.internal.ui.search;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.widgets.Table;

import org.eclipse.jface.viewers.TableViewer;

import org.eclipse.search.ui.text.AbstractTextSearchResult;

public class JavaSearchTableContentProvider extends JavaSearchContentProvider {
	public JavaSearchTableContentProvider(JavaSearchResultPage page) {
		super(page);
	}
	public Object[] getElements(Object inputElement) {
		if (inputElement instanceof AbstractTextSearchResult) {
			Set<Object> filteredElements= new HashSet<Object>();
			Object[] rawElements= ((AbstractTextSearchResult)inputElement).getElements();
			int limit= getPage().getElementLimit().intValue();
			for (int i= 0; i < rawElements.length; i++) {
				if (getPage().getDisplayedMatchCount(rawElements[i]) > 0) {
					filteredElements.add(rawElements[i]);
					if (limit != -1 && limit < filteredElements.size()) {
						break;
					}
				}
			}
			return filteredElements.toArray();
		}
		return EMPTY_ARR;
	}

	@Override
	public void elementsChanged(Object[] updatedElements) {
		if (getSearchResult() == null)
			return;

		int addLimit= getAddLimit();

		TableViewer viewer= (TableViewer) getPage().getViewer();
		Set<Object> updated= new HashSet<Object>();
		Set<Object> added= new HashSet<Object>();
		Set<Object> removed= new HashSet<Object>();
		for (int i= 0; i < updatedElements.length; i++) {
			if (getPage().getDisplayedMatchCount(updatedElements[i]) > 0) {
				if (viewer.testFindItem(updatedElements[i]) != null)
					updated.add(updatedElements[i]);
				else {
					if (addLimit > 0) {
						added.add(updatedElements[i]);
						addLimit--;
					}
				}
			} else {
				removed.add(updatedElements[i]);
			}
		}

		viewer.add(added.toArray());
		viewer.update(updated.toArray(), new String[] { SearchLabelProvider.PROPERTY_MATCH_COUNT });
		viewer.remove(removed.toArray());
	}

	private int getAddLimit() {
		int limit= getPage().getElementLimit().intValue();
		if (limit != -1) {
			Table table= (Table) getPage().getViewer().getControl();
			int itemCount= table.getItemCount();
			if (itemCount >= limit) {
				return 0;
			}
			return limit - itemCount;
		}
		return Integer.MAX_VALUE;
	}

	@Override
	public void clear() {
		getPage().getViewer().refresh();
	}

}

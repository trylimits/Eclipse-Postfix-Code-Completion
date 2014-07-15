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

package org.eclipse.jdt.internal.ui.viewsupport;

import java.util.ArrayList;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Widget;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.jface.viewers.TableViewer;

import org.eclipse.jdt.ui.IWorkingCopyProvider;
import org.eclipse.jdt.ui.ProblemsLabelDecorator.ProblemsLabelChangedEvent;

/**
 * Extends a  TableViewer to allow more performance when showing error ticks.
 * A <code>ProblemItemMapper</code> is contained that maps all items in
 * the tree to underlying resource
 */
public class ProblemTableViewer extends TableViewer implements ResourceToItemsMapper.IContentViewerAccessor {

	protected ResourceToItemsMapper fResourceToItemsMapper;

	/**
	 * Constructor for ProblemTableViewer.
	 * 
	 * @param parent the parent composite
	 */
	public ProblemTableViewer(Composite parent) {
		super(parent);
		initMapper();
	}

	/**
	 * Constructor for ProblemTableViewer.
	 * 
	 * @param parent the parent composite
	 * @param style the style
	 */
	public ProblemTableViewer(Composite parent, int style) {
		super(parent, style);
		initMapper();
	}

	/**
	 * Constructor for ProblemTableViewer.
	 * 
	 * @param table the table
	 */
	public ProblemTableViewer(Table table) {
		super(table);
		initMapper();
	}

	private void initMapper() {
		fResourceToItemsMapper= new ResourceToItemsMapper(this);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.viewsupport.ResourceToItemsMapper.IContentViewerAccessor#doUpdateItem(org.eclipse.swt.widgets.Widget)
	 */
	public void doUpdateItem(Widget item) {
		doUpdateItem(item, item.getData(), true);
	}

	/*
	 * @see StructuredViewer#mapElement(Object, Widget)
	 */
	@Override
	protected void mapElement(Object element, Widget item) {
		super.mapElement(element, item);
		if (item instanceof Item) {
			fResourceToItemsMapper.addToMap(element, (Item) item);
		}
	}

	/*
	 * @see StructuredViewer#unmapElement(Object, Widget)
	 */
	@Override
	protected void unmapElement(Object element, Widget item) {
		if (item instanceof Item) {
			fResourceToItemsMapper.removeFromMap(element, (Item) item);
		}
		super.unmapElement(element, item);
	}

	/*
	 * @see StructuredViewer#unmapAllElements()
	 */
	@Override
	protected void unmapAllElements() {
		fResourceToItemsMapper.clearMap();
		super.unmapAllElements();
	}

	/*
	 * @see ContentViewer#handleLabelProviderChanged(LabelProviderChangedEvent)
	 */
	@Override
	protected void handleLabelProviderChanged(LabelProviderChangedEvent event) {
		if (event instanceof ProblemsLabelChangedEvent) {
			ProblemsLabelChangedEvent e= (ProblemsLabelChangedEvent) event;
			if (!e.isMarkerChange() && canIgnoreChangesFromAnnotionModel()) {
				return;
			}
		}

		Object[] changed= event.getElements();
		if (changed != null && !fResourceToItemsMapper.isEmpty()) {
			ArrayList<Object> others= new ArrayList<Object>(changed.length);
			for (int i= 0; i < changed.length; i++) {
				Object curr= changed[i];
				if (curr instanceof IResource) {
					fResourceToItemsMapper.resourceChanged((IResource) curr);
				} else {
					others.add(curr);
				}
			}
			if (others.isEmpty()) {
				return;
			}
			event= new LabelProviderChangedEvent((IBaseLabelProvider) event.getSource(), others.toArray());
		}
		super.handleLabelProviderChanged(event);
	}

	/**
	 * Answers whether this viewer can ignore label provider changes resulting from
	 * marker changes in annotation models
	 * @return returns <code>true</code> if annotation model changes can be ignored
	 */
	private boolean canIgnoreChangesFromAnnotionModel() {
		Object contentProvider= getContentProvider();
		return contentProvider instanceof IWorkingCopyProvider && !((IWorkingCopyProvider)contentProvider).providesWorkingCopies();
	}
}

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
package org.eclipse.jdt.internal.ui.javaeditor.breadcrumb;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;


/**
 * An item in a breadcrumb viewer.
 * <p>
 * The item shows a label and an image. It also has the ability to expand, that is to open a drop
 * down menu.
 * </p>
 * <p>
 * The drop down allows to select any child of the items input element. The item shows the label and
 * icon of its data element, if any.
 * </p>
 *
 * @since 3.4
 */
class BreadcrumbItem extends Item {

	private ILabelProvider fLabelProvider;
	private ITreeContentProvider fContentProvider;

	private final BreadcrumbViewer fParent;
	private Composite fContainer;

	private BreadcrumbItemDetails fDetailsBlock;
	private BreadcrumbItemDropDown fExpandBlock;
	private ILabelProvider fToolTipLabelProvider;
	private boolean fIsLast;

	/**
	 * A new breadcrumb item which is shown inside the given viewer.
	 *
	 * @param viewer the items viewer
	 * @param parent the container containing the item
	 */
	public BreadcrumbItem(BreadcrumbViewer viewer, Composite parent) {
		super(parent, SWT.NONE);

		fParent= viewer;

		fContainer= new Composite(parent, SWT.NONE);
		fContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		GridLayout layout= new GridLayout(2, false);
		layout.marginBottom= 1;
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.horizontalSpacing= 0;
		fContainer.setLayout(layout);

		fDetailsBlock= new BreadcrumbItemDetails(this, fContainer);

		fExpandBlock= new BreadcrumbItemDropDown(this, fContainer);
	}

	/**
	 * Returns this items viewer.
	 *
	 * @return the viewer showing this item
	 */
	public BreadcrumbViewer getViewer() {
		return fParent;
	}

	/**
	 * Sets the content provider of this item.
	 *
	 * @param contentProvider the content provider to use
	 */
	public void setContentProvider(ITreeContentProvider contentProvider) {
		fContentProvider= contentProvider;
	}

	/**
	 * Sets the label provider of this item.
	 *
	 * @param labelProvider the label provider to use
	 */
	public void setLabelProvider(ILabelProvider labelProvider) {
		fLabelProvider= labelProvider;
	}

	/**
	 * Sets the the label provider for the tool tips of this item.
	 *
	 * @param toolTipLabelProvider the label provider for the tool tips
	 */
	public void setToolTipLabelProvider(ILabelProvider toolTipLabelProvider) {
		fToolTipLabelProvider= toolTipLabelProvider;
	}

	/*
	 * @see org.eclipse.swt.widgets.Widget#dispose()
	 */
	@Override
	public void dispose() {
		fContainer.dispose();
		super.dispose();
	}

	/**
	 * Should this item show a text label.
	 *
	 * @param enabled true if it should
	 */
	void setShowText(boolean enabled) {
		fDetailsBlock.setTextVisible(enabled);
	}

	/**
	 * Does this item show a text label?
	 *
	 * @return true if it does.
	 */
	boolean isShowText() {
		return fDetailsBlock.isTextVisible();
	}

	/**
	 * Returns the width of this item.
	 *
	 * @return the width of this item
	 */
	int getWidth() {
		return fDetailsBlock.getWidth() + fExpandBlock.getWidth() + 2;
	}

	/**
	 * Sets whether this item has to be marked as
	 * selected or not.
	 *
	 * @param selected true if marked as selected
	 */
	void setSelected(boolean selected) {
		fDetailsBlock.setSelected(selected);
	}

	/**
	 * Sets whether this item has the keyboard focus.
	 *
	 * @param state <code>true</code> if it has focus, <code>false</code> otherwise
	 */
	void setFocus(boolean state) {
		fDetailsBlock.setFocus(state);
	}

	/**
	 * Returns whether this item has the keyboard focus.
	 *
	 * @return <code>true</code> if this item has the keyboard focus
	 */
	boolean hasFocus() {
		return fDetailsBlock.hasFocus();
	}

	/**
	 * Redraw this item, retrieves new labels from its label provider.
	 */
	void refresh() {
		String text= fLabelProvider.getText(getData());
		Image image= fLabelProvider.getImage(getData());
		String toolTip= fToolTipLabelProvider.getText(getData());

		fDetailsBlock.setText(text);
		fDetailsBlock.setImage(image);
		fDetailsBlock.setToolTip(toolTip);

		refreshArrow();
	}

	/**
	 * Refresh the arrows visibility.
	 */
	void refreshArrow() {
		fExpandBlock.setEnabled(fContentProvider.hasChildren(getData()));
	}

	/**
	 * Set whether this is the last item in the breadcrumb item chain or not.
	 *
	 * @param isLast <code>true</code> if this is the last item, <code>false</code> otherwise
	 */
	void setIsLastItem(boolean isLast) {
		fIsLast= isLast;

		GridData data= (GridData) fContainer.getLayoutData();
		data.grabExcessHorizontalSpace= isLast;
	}

	/**
	 * Sets whether or not the this item should show the details (name and label).
	 *
	 * @param visible true if the item shows details
	 */
	void setDetailsVisible(boolean visible) {
		fDetailsBlock.setVisible(visible);
	}

	/**
	 * Expand this item, shows the drop down menu.
	 */
	void openDropDownMenu() {
		fExpandBlock.showMenu();
	}

	/**
	 * @return true if this item is expanded
	 */
	boolean isMenuShown() {
		return fExpandBlock.isMenuShown();
	}

	/**
	 * Returns the drop down shell.
	 *
	 * @return the shell of the drop down if shown, <code>null</code> otherwise
	 */
	Shell getDropDownShell() {
		return fExpandBlock.getDropDownShell();
	}

	/**
	 * Returns the drop down selection provider of this item.
	 *
	 * @return the selection provider of the drop down or <code>null</code>
	 */
	ISelectionProvider getDropDownSelectionProvider() {
		return fExpandBlock.getDropDownSelectionProvider();
	}

	/**
	 * Returns the bounds of this item.
	 *
	 * @return the bounds of this item
	 */
	public Rectangle getBounds() {
		return fContainer.getBounds();
	}

	/**
	 * Set the tool tip of the item to the given text.
	 *
	 * @param text the tool tip for the item
	 */
	public void setToolTip(String text) {
		fDetailsBlock.setToolTip(text);
	}

	/*
	 * @see org.eclipse.swt.widgets.Item#setText(java.lang.String)
	 */
	@Override
	public void setText(String string) {
		super.setText(string);
		fDetailsBlock.setText(string);

		//more or less space might be required for the label
		if (fIsLast)
			fContainer.layout(true, true);
	}

	/*
	 * @see org.eclipse.swt.widgets.Item#setImage(org.eclipse.swt.graphics.Image)
	 */
	@Override
	public void setImage(Image image) {
		super.setImage(image);
		fDetailsBlock.setImage(image);
	}

}

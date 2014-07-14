/*******************************************************************************
 * Copyright (c) 2003, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.navigator;

import org.eclipse.swt.events.HelpListener;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;

import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.actions.OpenAction;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;

public class OpenAndExpand extends SelectionDispatchAction {

	private OpenAction fOpenAction;
	private TreeViewer fViewer;

	public OpenAndExpand(IWorkbenchSite site, OpenAction openAction, TreeViewer viewer) {
		super(site);
		fOpenAction = openAction;
		fViewer = viewer;
	}

	@Override
	public void run() {
		fOpenAction.run();
		if(getSelection() != null && getSelection() instanceof IStructuredSelection)
			expand(((IStructuredSelection)getSelection()).getFirstElement());

	}

	@Override
	public void run(ISelection selection) {
		fOpenAction.run(selection);
		if(selection != null && selection instanceof IStructuredSelection)
			expand(((IStructuredSelection)selection).getFirstElement());
	}

	@Override
	public void run(IStructuredSelection selection) {
		fOpenAction.run(selection);
		if(selection != null)
			expand(selection.getFirstElement());
	}

	@Override
	public void run(ITextSelection selection) {
		fOpenAction.run(selection);
	}

	@Override
	public void run(JavaTextSelection selection) {
		fOpenAction.run(selection);
	}

	public void run(Object[] elements) {
		fOpenAction.run(elements);
	}

	@Override
	public void runWithEvent(Event event) {
		fOpenAction.runWithEvent(event);
	}

	@Override
	public void addPropertyChangeListener(IPropertyChangeListener listener) {
		fOpenAction.addPropertyChangeListener(listener);
	}

	@Override
	public boolean equals(Object obj) {
		return fOpenAction.equals(obj);
	}

	@Override
	public int getAccelerator() {
		return fOpenAction.getAccelerator();
	}

	@Override
	public String getActionDefinitionId() {
		return fOpenAction.getActionDefinitionId();
	}

	@Override
	public String getDescription() {
		return fOpenAction.getDescription();
	}

	@Override
	public ImageDescriptor getDisabledImageDescriptor() {
		return fOpenAction.getDisabledImageDescriptor();
	}

	public Object getElementToOpen(Object object) throws JavaModelException {
		return fOpenAction.getElementToOpen(object);
	}

	@Override
	public HelpListener getHelpListener() {
		return fOpenAction.getHelpListener();
	}

	@Override
	public ImageDescriptor getHoverImageDescriptor() {
		return fOpenAction.getHoverImageDescriptor();
	}

	@Override
	public String getId() {
		return fOpenAction.getId();
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return fOpenAction.getImageDescriptor();
	}

	@Override
	public IMenuCreator getMenuCreator() {
		return fOpenAction.getMenuCreator();
	}

	@Override
	public ISelection getSelection() {
		return fOpenAction.getSelection();
	}

	@Override
	public ISelectionProvider getSelectionProvider() {
		return fOpenAction.getSelectionProvider();
	}

	@Override
	public Shell getShell() {
		return fOpenAction.getShell();
	}

	@Override
	public IWorkbenchSite getSite() {
		return fOpenAction.getSite();
	}

	@Override
	public int getStyle() {
		return fOpenAction.getStyle();
	}

	@Override
	public String getText() {
		return fOpenAction.getText();
	}

	@Override
	public String getToolTipText() {
		return fOpenAction.getToolTipText();
	}

	@Override
	public int hashCode() {
		return fOpenAction.hashCode();
	}

	@Override
	public boolean isChecked() {
		return fOpenAction.isChecked();
	}

	@Override
	public boolean isEnabled() {
		return fOpenAction.isEnabled();
	}

	@Override
	public boolean isHandled() {
		return fOpenAction.isHandled();
	}

	@Override
	public void removePropertyChangeListener(IPropertyChangeListener listener) {
		fOpenAction.removePropertyChangeListener(listener);
	}

	@Override
	public void selectionChanged(ISelection selection) {
		fOpenAction.selectionChanged(selection);
	}

	@Override
	public void selectionChanged(IStructuredSelection selection) {
		fOpenAction.selectionChanged(selection);
	}

	@Override
	public void selectionChanged(ITextSelection selection) {
		fOpenAction.selectionChanged(selection);
	}

	@Override
	public void selectionChanged(JavaTextSelection selection) {
		fOpenAction.selectionChanged(selection);
	}

	@Override
	public void selectionChanged(SelectionChangedEvent event) {
		fOpenAction.selectionChanged(event);
	}

	@Override
	public void setAccelerator(int keycode) {
		fOpenAction.setAccelerator(keycode);
	}

	@Override
	public void setActionDefinitionId(String id) {
		fOpenAction.setActionDefinitionId(id);
	}

	@Override
	public void setChecked(boolean checked) {
		fOpenAction.setChecked(checked);
	}

	@Override
	public void setDescription(String text) {
		fOpenAction.setDescription(text);
	}

	@Override
	public void setDisabledImageDescriptor(ImageDescriptor newImage) {
		fOpenAction.setDisabledImageDescriptor(newImage);
	}

	@Override
	public void setEnabled(boolean enabled) {
		fOpenAction.setEnabled(enabled);
	}

	@Override
	public void setHelpListener(HelpListener listener) {
		fOpenAction.setHelpListener(listener);
	}

	@Override
	public void setHoverImageDescriptor(ImageDescriptor newImage) {
		fOpenAction.setHoverImageDescriptor(newImage);
	}

	@Override
	public void setId(String id) {
		fOpenAction.setId(id);
	}

	@Override
	public void setImageDescriptor(ImageDescriptor newImage) {
		fOpenAction.setImageDescriptor(newImage);
	}

	@Override
	public void setMenuCreator(IMenuCreator creator) {
		fOpenAction.setMenuCreator(creator);
	}

	@Override
	public void setSpecialSelectionProvider(ISelectionProvider provider) {
		fOpenAction.setSpecialSelectionProvider(provider);
	}

	@Override
	public void setText(String text) {
		fOpenAction.setText(text);
	}

	@Override
	public void setToolTipText(String toolTipText) {
		fOpenAction.setToolTipText(toolTipText);
	}

	@Override
	public String toString() {
		return fOpenAction.toString();
	}

	@Override
	public void update(ISelection selection) {
		fOpenAction.update(selection);
	}

	private void expand(Object target) {
		if (! fOpenAction.isEnabled() && target != null)
			fViewer.setExpandedState(target, !fViewer.getExpandedState(target));
	}
}

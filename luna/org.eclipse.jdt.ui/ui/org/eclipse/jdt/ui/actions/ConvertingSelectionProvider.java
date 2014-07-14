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
package org.eclipse.jdt.ui.actions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.ListenerList;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jdt.core.IJavaElement;



/**
 * A converting selection provider is a special selection provider which converts
 * a selection before notifying any listeners. Additional it converts the selection
 * on <code>getSelection</code> and <code>setSelection</code>. The default strategy
 * used to adapt the elements of the selection to {@link IJavaElement} or a {@link IResource},
 * but implementors can override this behavior.
 *
 * @since 3.2
 */
public class ConvertingSelectionProvider implements ISelectionProvider {

	private final ISelectionProvider fProvider;
	private SelectionChangedListener fListener;

	private class SelectionChangedListener implements ISelectionChangedListener {

		ListenerList fListeners= new ListenerList();

		public void selectionChanged(SelectionChangedEvent event) {
			ISelection selection= convertFrom(event.getSelection());
			SelectionChangedEvent newEvent= new SelectionChangedEvent(ConvertingSelectionProvider.this, selection);
			Object[] listeners= fListeners.getListeners();
			for (int i= 0; i < listeners.length; i++) {
				((ISelectionChangedListener)listeners[i]).selectionChanged(newEvent);
			}
		}
		public void addListener(ISelectionChangedListener listener) {
			fListeners.add(listener);
		}
		public void removeListener(ISelectionChangedListener listener) {
			fListeners.remove(listener);
		}
		public boolean isEmpty() {
			return fListeners.isEmpty();
		}
	}

	/**
	 * Creates a {@link ConvertingSelectionProvider} to convert from a given selection provider
	 * using the default mechanism.
	 *
	 * @param provider the provider to covert from and to
	 */
	public ConvertingSelectionProvider(ISelectionProvider provider) {
		Assert.isNotNull(provider);
		fProvider= provider;
	}

	/**
	 * Converts the given original viewer selection into a new
	 * selection. The default behavior adapts the elements in the selection
	 * first to {@link IJavaElement} then to {@link IResource}.
	 * Implementors want to override this method.
	 *
	 * @param viewerSelection the original viewer selection
	 *
	 * @return the new selection to be used
	 */
	public ISelection convertFrom(ISelection viewerSelection) {
		return convertFromUsingDefaultMechanism(viewerSelection);
	}


	private ISelection convertFromUsingDefaultMechanism(ISelection viewerSelection) {
		if (! (viewerSelection instanceof IStructuredSelection))
			return viewerSelection;
		IStructuredSelection selection= (IStructuredSelection)viewerSelection;
		List<Object> result= new ArrayList<Object>(selection.size());
		for (Iterator<?> iter= selection.iterator(); iter.hasNext();) {
			Object element= iter.next();
			if (element instanceof IResource || element instanceof IJavaElement) {
				result.add(element);
			} else if (element instanceof IAdaptable) {
				IAdaptable adaptable= (IAdaptable)element;
				IJavaElement jElement= (IJavaElement)adaptable.getAdapter(IJavaElement.class);
				if (jElement != null) {
					result.add(jElement);
				} else {
					IResource resource= (IResource)adaptable.getAdapter(IResource.class);
					if (resource != null) {
						result.add(resource);
					} else {
						result.add(element);
					}
				}
			} else {
				result.add(element);
			}
		}
		return new StructuredSelection(result);
	}

	/**
	 * Converts a selection to a viewer selection. The default implementation does not convert
	 * the selection. Implementors want to override this behavior.
	 *
	 * @param selection the selection to convert
	 *
	 * @return a viewer selection
	 */
	public ISelection convertTo(ISelection selection) {
		return selection;
	}


	/**
	 * {@inheritDoc}
	 */
	public final ISelection getSelection() {
		return convertFrom(fProvider.getSelection());
	}

	/**
	 * {@inheritDoc}
	 */
	public final void setSelection(ISelection selection) {
		fProvider.setSelection(convertTo(selection));
	}

	/**
	 * {@inheritDoc}
	 */
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		if (fListener == null) {
			fListener= new SelectionChangedListener();
			fProvider.addSelectionChangedListener(fListener);
		}
		fListener.addListener(listener);
	}

	/**
	 * {@inheritDoc}
	 */
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		if (fListener == null)
			return;
		fListener.removeListener(listener);
		if (fListener.isEmpty()) {
			fProvider.removeSelectionChangedListener(fListener);
			fListener= null;
		}
	}
}

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
package org.eclipse.jdt.internal.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.ListenerList;

import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.JavaCore;

public abstract class AbstractJavaElementLabelDecorator implements ILightweightLabelDecorator {

	private class DecoratorElementChangeListener implements IElementChangedListener {

		/**
		 * {@inheritDoc}
		 */
		public void elementChanged(ElementChangedEvent event) {
			List<IJavaElement> changed= new ArrayList<IJavaElement>();
			processDelta(event.getDelta(), changed);
			if (changed.size() == 0)
				return;

			fireChange(changed.toArray(new IJavaElement[changed.size()]));
		}

	}

	private ListenerList fListeners;
	private IElementChangedListener fChangeListener;

	/**
	 * {@inheritDoc}
	 */
	public void addListener(ILabelProviderListener listener) {
		if (fChangeListener == null) {
			fChangeListener= new DecoratorElementChangeListener();
			JavaCore.addElementChangedListener(fChangeListener);
		}

		if (fListeners == null) {
			fListeners= new ListenerList();
		}

		fListeners.add(listener);
	}

	/**
	 * {@inheritDoc}
	 */
	public void dispose() {
		if (fChangeListener != null) {
			JavaCore.removeElementChangedListener(fChangeListener);
			fChangeListener= null;
		}
		if (fListeners != null) {
			Object[] listeners= fListeners.getListeners();
			for (int i= 0; i < listeners.length; i++) {
				fListeners.remove(listeners[i]);
			}
			fListeners= null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isLabelProperty(Object element, String property) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	public void removeListener(ILabelProviderListener listener) {
		if (fListeners == null)
			return;

		fListeners.remove(listener);

		if (fListeners.isEmpty() && fChangeListener != null) {
			JavaCore.removeElementChangedListener(fChangeListener);
			fChangeListener= null;
		}
	}
	
	private void fireChange(IJavaElement[] elements) {
		if (fListeners != null && !fListeners.isEmpty()) {
			LabelProviderChangedEvent event= new LabelProviderChangedEvent(this, elements);
			Object[] listeners= fListeners.getListeners();
			for (int i= 0; i < listeners.length; i++) {
				((ILabelProviderListener) listeners[i]).labelProviderChanged(event);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public abstract void decorate(Object element, IDecoration decoration);

	protected abstract void processDelta(IJavaElementDelta delta, List<IJavaElement> result);

	protected boolean processChildrenDelta(IJavaElementDelta delta, List<IJavaElement> result) {
		IJavaElementDelta[] children= delta.getAffectedChildren();
		for (int i= 0; i < children.length; i++) {
			processDelta(children[i], result);
		}
		return false;
	}

}

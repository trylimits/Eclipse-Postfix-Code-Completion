/*******************************************************************************
 * Copyright (c) 2005, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.compare;

import org.osgi.service.prefs.BackingStoreException;

import org.eclipse.swt.widgets.Display;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

/**
 * Adapts an options {@link IEclipsePreferences} to {@link org.eclipse.jface.preference.IPreferenceStore}.
 * <p>
 * This preference store is read-only i.e. write access
 * throws an {@link java.lang.UnsupportedOperationException}.
 * </p>
 *
 * @since 3.1
 */
class EclipsePreferencesAdapter implements IPreferenceStore {

	/**
	 * Preference change listener. Listens for events preferences
	 * fires a {@link org.eclipse.jface.util.PropertyChangeEvent}
	 * on this adapter with arguments from the received event.
	 */
	private class PreferenceChangeListener implements IEclipsePreferences.IPreferenceChangeListener {

		/**
		 * {@inheritDoc}
		 */
		public void preferenceChange(final IEclipsePreferences.PreferenceChangeEvent event) {
			if (Display.getCurrent() == null) {
				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						firePropertyChangeEvent(event.getKey(), event.getOldValue(), event.getNewValue());
					}
				});
			} else {
				firePropertyChangeEvent(event.getKey(), event.getOldValue(), event.getNewValue());
			}
		}
	}

	/** Listeners on on this adapter */
	private ListenerList fListeners= new ListenerList(ListenerList.IDENTITY);

	/** Listener on the node */
	private IEclipsePreferences.IPreferenceChangeListener fListener= new PreferenceChangeListener();

	/** wrapped node */
	private final IScopeContext fContext;
	private final String fQualifier;

	/**
	 * Initialize with the node to wrap
	 *
	 * @param context The context to access
	 */
	public EclipsePreferencesAdapter(IScopeContext context, String qualifier) {
		fContext= context;
		fQualifier= qualifier;
	}

	private IEclipsePreferences getNode() {
		return fContext.getNode(fQualifier);
	}

	/**
	 * {@inheritDoc}
	 */
	public void addPropertyChangeListener(IPropertyChangeListener listener) {
		if (fListeners.size() == 0)
			getNode().addPreferenceChangeListener(fListener);
		fListeners.add(listener);
	}

	/**
	 * {@inheritDoc}
	 */
	public void removePropertyChangeListener(IPropertyChangeListener listener) {
		fListeners.remove(listener);
		if (fListeners.size() == 0) {
			getNode().removePreferenceChangeListener(fListener);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean contains(String name) {
		return getNode().get(name, null) != null;
	}

	/**
	 * {@inheritDoc}
	 */
	public void firePropertyChangeEvent(String name, Object oldValue, Object newValue) {
		PropertyChangeEvent event= new PropertyChangeEvent(this, name, oldValue, newValue);
		Object[] listeners= fListeners.getListeners();
		for (int i= 0; i < listeners.length; i++)
			((IPropertyChangeListener) listeners[i]).propertyChange(event);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean getBoolean(String name) {
		return getNode().getBoolean(name, BOOLEAN_DEFAULT_DEFAULT);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean getDefaultBoolean(String name) {
		return BOOLEAN_DEFAULT_DEFAULT;
	}

	/**
	 * {@inheritDoc}
	 */
	public double getDefaultDouble(String name) {
		return DOUBLE_DEFAULT_DEFAULT;
	}

	/**
	 * {@inheritDoc}
	 */
	public float getDefaultFloat(String name) {
		return FLOAT_DEFAULT_DEFAULT;
	}

	/**
	 * {@inheritDoc}
	 */
	public int getDefaultInt(String name) {
		return INT_DEFAULT_DEFAULT;
	}

	/**
	 * {@inheritDoc}
	 */
	public long getDefaultLong(String name) {
		return LONG_DEFAULT_DEFAULT;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getDefaultString(String name) {
		return STRING_DEFAULT_DEFAULT;
	}

	/**
	 * {@inheritDoc}
	 */
	public double getDouble(String name) {
		return getNode().getDouble(name, DOUBLE_DEFAULT_DEFAULT);
	}

	/**
	 * {@inheritDoc}
	 */
	public float getFloat(String name) {
		return getNode().getFloat(name, FLOAT_DEFAULT_DEFAULT);
	}

	/**
	 * {@inheritDoc}
	 */
	public int getInt(String name) {
		return getNode().getInt(name, INT_DEFAULT_DEFAULT);
	}

	/**
	 * {@inheritDoc}
	 */
	public long getLong(String name) {
		return getNode().getLong(name, LONG_DEFAULT_DEFAULT);
	}

	/**
	 * {@inheritDoc}
	 */
	public String getString(String name) {
		return getNode().get(name, STRING_DEFAULT_DEFAULT);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isDefault(String name) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean needsSaving() {
		try {
			return getNode().keys().length > 0;
		} catch (BackingStoreException e) {
			// ignore
		}
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	public void putValue(String name, String value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setDefault(String name, double value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setDefault(String name, float value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setDefault(String name, int value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setDefault(String name, long value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setDefault(String name, String defaultObject) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setDefault(String name, boolean value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setToDefault(String name) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setValue(String name, double value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setValue(String name, float value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setValue(String name, int value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setValue(String name, long value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setValue(String name, String value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public void setValue(String name, boolean value) {
		throw new UnsupportedOperationException();
	}

}

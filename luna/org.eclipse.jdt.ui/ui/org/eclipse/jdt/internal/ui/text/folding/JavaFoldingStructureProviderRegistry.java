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
package org.eclipse.jdt.internal.ui.text.folding;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.folding.IJavaFoldingStructureProvider;

import org.eclipse.jdt.internal.ui.JavaPlugin;


/**
 * @since 3.0
 */
public class JavaFoldingStructureProviderRegistry {

	private static final String EXTENSION_POINT= "foldingStructureProviders"; //$NON-NLS-1$

	/** The map of descriptors, indexed by their identifiers. */
	private Map<String, JavaFoldingStructureProviderDescriptor> fDescriptors;

	/**
	 * Creates a new instance.
	 */
	public JavaFoldingStructureProviderRegistry() {
	}

	/**
	 * Returns an array of <code>JavaFoldingStructureProviderDescriptor</code> describing
	 * all extension to the <code>foldingProviders</code> extension point.
	 *
	 * @return the list of extensions to the
	 *         <code>quickDiffReferenceProvider</code> extension point
	 */
	public JavaFoldingStructureProviderDescriptor[] getFoldingProviderDescriptors() {
		synchronized (this) {
			ensureRegistered();
			return fDescriptors.values().toArray(new JavaFoldingStructureProviderDescriptor[fDescriptors.size()]);
		}
	}

	/**
	 * Returns the folding provider descriptor with identifier <code>id</code> or
	 * <code>null</code> if no such provider is registered.
	 *
	 * @param id the identifier for which a provider is wanted
	 * @return the corresponding provider descriptor, or <code>null</code> if none can be
	 *         found
	 */
	public JavaFoldingStructureProviderDescriptor getFoldingProviderDescriptor(String id) {
		synchronized (this) {
			ensureRegistered();
			return fDescriptors.get(id);
		}
	}

	/**
	 * Instantiates and returns the provider that is currently configured in the
	 * preferences.
	 *
	 * @return the current provider according to the preferences
	 */
	public IJavaFoldingStructureProvider getCurrentFoldingProvider() {
		IPreferenceStore preferenceStore= JavaPlugin.getDefault().getPreferenceStore();
		String currentProviderId= preferenceStore.getString(PreferenceConstants.EDITOR_FOLDING_PROVIDER);
		JavaFoldingStructureProviderDescriptor desc= getFoldingProviderDescriptor(currentProviderId);

		// Fallback to default if extension has gone
		if (desc == null) {
			String message= Messages.format(FoldingMessages.JavaFoldingStructureProviderRegistry_warning_providerNotFound_resetToDefault, currentProviderId);
			JavaPlugin.log(new Status(IStatus.WARNING, JavaPlugin.getPluginId(), IStatus.OK, message, null));

			String defaultProviderId= preferenceStore.getDefaultString(PreferenceConstants.EDITOR_FOLDING_PROVIDER);

			desc= getFoldingProviderDescriptor(defaultProviderId);
			Assert.isNotNull(desc);

			preferenceStore.setToDefault(PreferenceConstants.EDITOR_FOLDING_PROVIDER);
		}

		try {
			return desc.createProvider();
		} catch (CoreException e) {
			JavaPlugin.log(e);
			return null;
		}
	}

	/**
	 * Ensures that the extensions are read and stored in
	 * <code>fDescriptors</code>.
	 */
	private void ensureRegistered() {
		if (fDescriptors == null)
			reloadExtensions();
	}

	/**
	 * Reads all extensions.
	 * <p>
	 * This method can be called more than once in
	 * order to reload from a changed extension registry.
	 * </p>
	 */
	public void reloadExtensions() {
		IExtensionRegistry registry= Platform.getExtensionRegistry();
		Map<String, JavaFoldingStructureProviderDescriptor> map= new HashMap<String, JavaFoldingStructureProviderDescriptor>();

		IConfigurationElement[] elements= registry.getConfigurationElementsFor(JavaPlugin.getPluginId(), EXTENSION_POINT);
		for (int i= 0; i < elements.length; i++) {
			JavaFoldingStructureProviderDescriptor desc= new JavaFoldingStructureProviderDescriptor(elements[i]);
			map.put(desc.getId(), desc);
		}

		synchronized(this) {
			fDescriptors= Collections.unmodifiableMap(map);
		}
	}

}

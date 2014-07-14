/*******************************************************************************
 * Copyright (c) 2006, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * @since 3.2
 */
public final class ProposalSorterRegistry {
	private static final String EXTENSION_POINT= "javaCompletionProposalSorters"; //$NON-NLS-1$
	private static final String DEFAULT_ID= "org.eclipse.jdt.ui.RelevanceSorter"; //$NON-NLS-1$

	private static ProposalSorterRegistry fInstance;

	public static synchronized ProposalSorterRegistry getDefault() {
		if (fInstance == null)
			fInstance= new ProposalSorterRegistry(JavaPlugin.getDefault().getPreferenceStore(), PreferenceConstants.CODEASSIST_SORTER);
		return fInstance;
	}

	private final IPreferenceStore fPreferenceStore;
	private final String fKey;

	private Map<String, ProposalSorterHandle> fSorters= null;
	private ProposalSorterHandle fDefaultSorter;

	private ProposalSorterRegistry(final IPreferenceStore preferenceStore, final String key) {
		Assert.isTrue(preferenceStore != null);
		Assert.isTrue(key != null);
		fPreferenceStore= preferenceStore;
		fKey= key;
	}

	public ProposalSorterHandle getCurrentSorter() {
		ensureSortersRead();
		String id= fPreferenceStore.getString(fKey);
		ProposalSorterHandle sorter= fSorters.get(id);
		return sorter != null ? sorter : fDefaultSorter;
	}

	private synchronized void ensureSortersRead() {
		if (fSorters != null)
			return;

		Map<String, ProposalSorterHandle> sorters= new LinkedHashMap<String, ProposalSorterHandle>();
		IExtensionRegistry registry= Platform.getExtensionRegistry();
		List<IConfigurationElement> elements= new ArrayList<IConfigurationElement>(Arrays.asList(registry.getConfigurationElementsFor(JavaPlugin.getPluginId(), EXTENSION_POINT)));

		for (Iterator<IConfigurationElement> iter= elements.iterator(); iter.hasNext();) {
			IConfigurationElement element= iter.next();

			try {

				ProposalSorterHandle handle= new ProposalSorterHandle(element);
				final String id= handle.getId();
				sorters.put(id, handle);
				if (DEFAULT_ID.equals(id))
					fDefaultSorter= handle;

			} catch (InvalidRegistryObjectException x) {
				/*
				 * Element is not valid any longer as the contributing plug-in was unloaded or for
				 * some other reason. Do not include the extension in the list and inform the user
				 * about it.
				 */
				Object[] args= { element.toString() };
				String message= Messages.format(JavaTextMessages.CompletionProposalComputerRegistry_invalid_message, args);
				IStatus status= new Status(IStatus.WARNING, JavaPlugin.getPluginId(), IStatus.OK, message, x);
				informUser(status);
			} catch (CoreException x) {
				informUser(x.getStatus());
			}
		}

		fSorters= sorters;
	}

	private void informUser(IStatus status) {
		JavaPlugin.log(status);
		String title= JavaTextMessages.CompletionProposalComputerRegistry_error_dialog_title;
		String message= status.getMessage();
		MessageDialog.openError(JavaPlugin.getActiveWorkbenchShell(), title, message);
	}

	public ProposalSorterHandle[] getSorters() {
		ensureSortersRead();
		Collection<ProposalSorterHandle> sorters= fSorters.values();
		return sorters.toArray(new ProposalSorterHandle[sorters.size()]);
	}

	public void select(ProposalSorterHandle handle) {
		Assert.isTrue(handle != null);
		String id= handle.getId();

		fPreferenceStore.setValue(fKey, id);
	}
}

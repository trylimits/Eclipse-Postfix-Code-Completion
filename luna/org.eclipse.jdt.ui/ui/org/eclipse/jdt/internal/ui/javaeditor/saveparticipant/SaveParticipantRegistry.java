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
package org.eclipse.jdt.internal.ui.javaeditor.saveparticipant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IScopeContext;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;

import org.eclipse.jface.text.IRegion;

import org.eclipse.jdt.core.ICompilationUnit;

import org.eclipse.jdt.internal.corext.fix.CleanUpPostSaveListener;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaStatusConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.fix.CleanUpSaveParticipantPreferenceConfiguration;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider;

/**
 * A registry for save participants. This registry manages
 * {@link SaveParticipantDescriptor}s and keeps track of enabled save
 * participants.
 * <p>
 * Save participants can be enabled and disabled on the Java &gt; Editor &gt;
 * Save Participants preference page. Enabled save participants are notified
 * through a call to
 * {@link IPostSaveListener#saved(org.eclipse.jdt.core.ICompilationUnit, IRegion[], org.eclipse.core.runtime.IProgressMonitor)}
 * whenever the {@link CompilationUnitDocumentProvider} saves a compilation unit
 * that is in the workspace.</p>
 * <p>
 * An instance of this registry can be received through a call to {@link JavaPlugin#getSaveParticipantRegistry()}.</p>
 *
 * @since 3.3
 */
public final class SaveParticipantRegistry {

	private static final IPostSaveListener[] EMPTY_ARRAY= new IPostSaveListener[0];

	/** The map of descriptors, indexed by their identifiers. */
	private Map<String, SaveParticipantDescriptor> fDescriptors;

	/**
	 * Creates a new instance.
	 */
	public SaveParticipantRegistry() {
	}

	/**
	 * Returns an array of <code>SaveParticipantDescriptor</code> describing
	 * all registered save participants.
	 *
	 * @return the array of registered save participant descriptors
	 */
	public synchronized SaveParticipantDescriptor[] getSaveParticipantDescriptors() {
		ensureRegistered();
		return fDescriptors.values().toArray(new SaveParticipantDescriptor[fDescriptors.size()]);
	}

	/**
	 * Returns the save participant descriptor for the given <code>id</code> or
	 * <code>null</code> if no such listener is registered.
	 *
	 * @param id the identifier of the requested save participant
	 * @return the corresponding descriptor, or <code>null</code> if none can be found
	 */
	public synchronized SaveParticipantDescriptor getSaveParticipantDescriptor(String id) {
		ensureRegistered();
		return fDescriptors.get(id);
	}

	/**
	 * Ensures that all descriptors are created and stored in
	 * <code>fDescriptors</code>.
	 */
	private void ensureRegistered() {
		if (fDescriptors == null)
			reloadDescriptors();
	}

	/**
	 * Loads the save participants.
	 * <p>
	 * This method can be called more than once in
	 * order to reload from a changed extension registry.
	 * </p>
	 */
	private void reloadDescriptors() {
		Map<String, SaveParticipantDescriptor> map= new HashMap<String, SaveParticipantDescriptor>();
		SaveParticipantDescriptor desc= new SaveParticipantDescriptor(new CleanUpPostSaveListener()) {
			/**
			 * {@inheritDoc}
			 */
			@Override
			public ISaveParticipantPreferenceConfiguration createPreferenceConfiguration() {
				return new CleanUpSaveParticipantPreferenceConfiguration();
			}
		};
		map.put(desc.getId(), desc);

		fDescriptors= map;
	}

	public void dispose() {
	}

	/**
	 * Checks weather there are enabled or disabled post save listener in the given context.
	 *
	 * @param context to context to check, not null
	 * @return true if there are settings in context
	 */
	public synchronized boolean hasSettingsInScope(IScopeContext context) {
		ensureRegistered();

    	for (Iterator<SaveParticipantDescriptor> iterator= fDescriptors.values().iterator(); iterator.hasNext();) {
	        SaveParticipantDescriptor descriptor= iterator.next();
	        if (descriptor.getPreferenceConfiguration().hasSettingsInScope(context))
	        	return true;
    	}

    	return false;
    }

	public IPostSaveListener[] getEnabledPostSaveListeners(IProject project) {
	    return getEnabledPostSaveListeners(new ProjectScope(project));
    }

	/**
	 * Returns an array of <code>IPostSaveListener</code> which are
	 * enabled in the given context.
	 *
	 * @param context the context from which to retrieve the settings from, not null
	 * @return the current enabled post save listeners according to the preferences
	 */
	public synchronized IPostSaveListener[] getEnabledPostSaveListeners(IScopeContext context) {
		ensureRegistered();

		ArrayList<IPostSaveListener> result= null;
		for (Iterator<SaveParticipantDescriptor> iterator= fDescriptors.values().iterator(); iterator.hasNext();) {
			SaveParticipantDescriptor descriptor= iterator.next();
			if (descriptor.getPreferenceConfiguration().isEnabled(context)) {
				if (result == null) {
					result= new ArrayList<IPostSaveListener>();
				}
				result.add(descriptor.getPostSaveListener());
			}
		}

		if (result == null) {
			return EMPTY_ARRAY;
		} else {
			return result.toArray(new IPostSaveListener[result.size()]);
		}
	}

	/**
	 * Tells whether one of the active post save listeners needs to be informed about the changed
	 * region in this save cycle.
	 * 
	 * @param unit the unit which is about to be saved
	 * @return true if the change regions need do be determined
	 * @throws CoreException if something went wrong
	 * @since 3.4
	 */
	public static boolean isChangedRegionsRequired(final ICompilationUnit unit) throws CoreException {
		String message= SaveParticipantMessages.SaveParticipantRegistry_needsChangedRegionFailed;
		final MultiStatus errorStatus= new MultiStatus(JavaUI.ID_PLUGIN, IJavaStatusConstants.EDITOR_CHANGED_REGION_CALCULATION, message, null);

		IPostSaveListener[] listeners= JavaPlugin.getDefault().getSaveParticipantRegistry().getEnabledPostSaveListeners(unit.getJavaProject().getProject());
		try {
			final boolean result[]= new boolean[] {false};
			for (int i= 0; i < listeners.length; i++) {
				final IPostSaveListener listener= listeners[i];
				SafeRunner.run(new ISafeRunnable() {

					public void run() throws Exception {
						if (listener.needsChangedRegions(unit))
							result[0]= true;
					}

					public void handleException(Throwable ex) {
						String msg= Messages.format("The save participant ''{0}'' caused an exception.", new String[] { listener.getId() }); //$NON-NLS-1$
						JavaPlugin.log(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IJavaStatusConstants.EDITOR_POST_SAVE_NOTIFICATION, msg, ex));

						final String participantName= listener.getName();
						msg= Messages.format(SaveParticipantMessages.SaveParticipantRegistry_needsChangedRegionCausedException, new String[] { participantName, ex.toString() });
						errorStatus.add(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IJavaStatusConstants.EDITOR_CHANGED_REGION_CALCULATION, msg, null));
					}

				});
				if (result[0])
					return true;
			}
		} finally {
			if (!errorStatus.isOK())
				throw new CoreException(errorStatus);
		}

		return false;
	}

}

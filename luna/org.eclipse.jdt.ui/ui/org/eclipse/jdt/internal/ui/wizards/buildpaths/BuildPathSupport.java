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
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.MessageDialog;

import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;

/**
 *
 */
public class BuildPathSupport {

	public static final String JRE_PREF_PAGE_ID= "org.eclipse.jdt.debug.ui.preferences.VMPreferencePage"; //$NON-NLS-1$
	public static final String EE_PREF_PAGE_ID= "org.eclipse.jdt.debug.ui.jreProfiles"; //$NON-NLS-1$
	
	/* see also ComplianceConfigurationBlock#PREFS_COMPLIANCE */
	private static final String[] PREFS_COMPLIANCE= new String[] {
			JavaCore.COMPILER_PB_ASSERT_IDENTIFIER, JavaCore.COMPILER_PB_ENUM_IDENTIFIER,
			JavaCore.COMPILER_SOURCE, JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM,
			JavaCore.COMPILER_COMPLIANCE
	};


	private BuildPathSupport() {
		super();
	}

	/**
	 * Returns a deprecation message for a classpath variable name.
	 *
	 * @param variableName classpath variable name
	 * @return the deprecation message, or <code>null</code> iff
	 *         <code>variableName</code> is not a classpath variable or the
	 *         variable is not deprecated
	 */
	public static String getDeprecationMessage(String variableName) {
		String deprecationMessage= JavaCore.getClasspathVariableDeprecationMessage(variableName);
		if (deprecationMessage == null	)
			return null;
		else
			return Messages.format(NewWizardMessages.BuildPathSupport_deprecated,
					new Object[] {variableName, deprecationMessage});
	}

	/**
	 * Finds a source attachment for a new archive in the existing classpaths.
	 * @param elem The new classpath entry
	 * @return A path to be taken for the source attachment or <code>null</code>
	 */
	public static IPath guessSourceAttachment(CPListElement elem) {
		if (elem.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
			return null;
		}
		IJavaProject currProject= elem.getJavaProject(); // can be null
		try {
			IJavaModel jmodel= JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
			IJavaProject[] jprojects= jmodel.getJavaProjects();
			for (int i= 0; i < jprojects.length; i++) {
				IJavaProject curr= jprojects[i];
				if (!curr.equals(currProject)) {
					IClasspathEntry[] entries= curr.getRawClasspath();
					for (int k= 0; k < entries.length; k++) {
						IClasspathEntry entry= entries[k];
						if (entry.getEntryKind() == elem.getEntryKind()
							&& entry.getPath().equals(elem.getPath())) {
							IPath attachPath= entry.getSourceAttachmentPath();
							if (attachPath != null && !attachPath.isEmpty()) {
								return attachPath;
							}
						}
					}
				}
			}
		} catch (JavaModelException e) {
			JavaPlugin.log(e.getStatus());
		}
		return null;
	}

	/**
	 * Finds a javadoc location for a new archive in the existing classpaths.
	 * @param elem The new classpath entry
	 * @return A javadoc location found in a similar classpath entry or <code>null</code>.
	 */
	public static String guessJavadocLocation(CPListElement elem) {
		if (elem.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
			return null;
		}
		IJavaProject currProject= elem.getJavaProject(); // can be null
		try {
			// try if the jar itself contains the source
			IJavaModel jmodel= JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
			IJavaProject[] jprojects= jmodel.getJavaProjects();
			for (int i= 0; i < jprojects.length; i++) {
				IJavaProject curr= jprojects[i];
				if (!curr.equals(currProject)) {
					IClasspathEntry[] entries= curr.getRawClasspath();
					for (int k= 0; k < entries.length; k++) {
						IClasspathEntry entry= entries[k];
						if (entry.getEntryKind() == elem.getEntryKind() && entry.getPath().equals(elem.getPath())) {
							IClasspathAttribute[] attributes= entry.getExtraAttributes();
							for (int n= 0; n < attributes.length; n++) {
								IClasspathAttribute attrib= attributes[n];
								if (IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME.equals(attrib.getName())) {
									return attrib.getValue();
								}
							}
						}
					}
				}
			}
		} catch (JavaModelException e) {
			JavaPlugin.log(e.getStatus());
		}
		return null;
	}

	private static class UpdatedClasspathContainer implements IClasspathContainer {

		private IClasspathEntry[] fNewEntries;
		private IClasspathContainer fOriginal;

		public UpdatedClasspathContainer(IClasspathContainer original, IClasspathEntry[] newEntries) {
			fNewEntries= newEntries;
			fOriginal= original;
		}

		public IClasspathEntry[] getClasspathEntries() {
			return fNewEntries;
		}

		public String getDescription() {
			return fOriginal.getDescription();
		}

		public int getKind() {
			return fOriginal.getKind();
		}

		public IPath getPath() {
			return fOriginal.getPath();
		}
	}

	/**
	 * Apply a modified classpath entry to the classpath. The classpath entry can also be from a classpath container.
	 * @param shell If not null and the entry could not be found on the projects classpath, a dialog will ask to put the entry on the classpath
	 * @param newEntry The modified entry. The entry's kind or path must be unchanged.
	 * @param changedAttributes The attributes that have changed. See {@link CPListElement} for constants values.
	 * @param jproject Project where the entry belongs to
	 * @param containerPath The path of the entry's parent container or <code>null</code> if the entry is not in a container
	 * @param isReferencedEntry <code>true</code> iff the entry has a {@link IClasspathEntry#getReferencingEntry() referencing entry}
	 * @param monitor The progress monitor to use
	 * @throws CoreException if the update failed
	 */
	public static void modifyClasspathEntry(Shell shell, IClasspathEntry newEntry, String[] changedAttributes, IJavaProject jproject, IPath containerPath, boolean isReferencedEntry, IProgressMonitor monitor) throws CoreException {
		if (containerPath != null) {
			updateContainerClasspath(jproject, containerPath, newEntry, changedAttributes, monitor);
		} else if (isReferencedEntry) {
			updateReferencedClasspathEntry(jproject, newEntry, changedAttributes, monitor);
		} else {
			updateProjectClasspath(shell, jproject, newEntry, changedAttributes, monitor);
		}
	}

	private static void updateContainerClasspath(IJavaProject jproject, IPath containerPath, IClasspathEntry newEntry, String[] changedAttributes, IProgressMonitor monitor) throws CoreException {
		IClasspathContainer container= JavaCore.getClasspathContainer(containerPath, jproject);
		if (container == null) {
			throw new CoreException(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.ERROR, "Container " + containerPath + " cannot be resolved", null));  //$NON-NLS-1$//$NON-NLS-2$
		}
		IClasspathEntry[] entries= container.getClasspathEntries();
		IClasspathEntry[] newEntries= new IClasspathEntry[entries.length];
		for (int i= 0; i < entries.length; i++) {
			IClasspathEntry curr= entries[i];
			if (curr.getEntryKind() == newEntry.getEntryKind() && curr.getPath().equals(newEntry.getPath())) {
				newEntries[i]= getUpdatedEntry(curr, newEntry, changedAttributes, jproject);
			} else {
				newEntries[i]= curr;
			}
		}
		requestContainerUpdate(jproject, container, newEntries);
		monitor.worked(1);
	}

	private static IClasspathEntry getUpdatedEntry(IClasspathEntry currEntry, IClasspathEntry updatedEntry, String[] updatedAttributes, IJavaProject jproject) {
		if (updatedAttributes == null) {
			return updatedEntry; // used updated entry 'as is'
		}
		CPListElement currElem= CPListElement.createFromExisting(currEntry, jproject);
		CPListElement newElem= CPListElement.createFromExisting(updatedEntry, jproject);
		for (int i= 0; i < updatedAttributes.length; i++) {
			String attrib= updatedAttributes[i];
			currElem.setAttribute(attrib, newElem.getAttribute(attrib));
		}
		return currElem.getClasspathEntry();
	}

	/**
	 * Request a container update.
	 * @param jproject The project of the container
	 * @param container The container to request a change to
	 * @param newEntries The updated entries
	 * @throws CoreException if the request failed
	 */
	public static void requestContainerUpdate(IJavaProject jproject, IClasspathContainer container, IClasspathEntry[] newEntries) throws CoreException {
		IPath containerPath= container.getPath();
		IClasspathContainer updatedContainer= new UpdatedClasspathContainer(container, newEntries);
		ClasspathContainerInitializer initializer= JavaCore.getClasspathContainerInitializer(containerPath.segment(0));
		if (initializer != null) {
			initializer.requestClasspathContainerUpdate(containerPath, jproject, updatedContainer);
		}
	}

	private static void updateProjectClasspath(Shell shell, IJavaProject jproject, IClasspathEntry newEntry, String[] changedAttributes, IProgressMonitor monitor) throws JavaModelException {
		IClasspathEntry[] oldClasspath= jproject.getRawClasspath();
		int nEntries= oldClasspath.length;
		ArrayList<IClasspathEntry> newEntries= new ArrayList<IClasspathEntry>(nEntries + 1);
		int entryKind= newEntry.getEntryKind();
		IPath jarPath= newEntry.getPath();
		boolean found= false;
		for (int i= 0; i < nEntries; i++) {
			IClasspathEntry curr= oldClasspath[i];
			if (curr.getEntryKind() == entryKind && curr.getPath().equals(jarPath)) {
				// add modified entry
				newEntries.add(getUpdatedEntry(curr, newEntry, changedAttributes, jproject));
				found= true;
			} else {
				newEntries.add(curr);
			}
		}
		if (!found) {
			if (!putJarOnClasspathDialog(shell)) {
				return;
			}
			// add new
			newEntries.add(newEntry);
		}
		IClasspathEntry[] newClasspath= newEntries.toArray(new IClasspathEntry[newEntries.size()]);
		jproject.setRawClasspath(newClasspath, monitor);
	}

	private static boolean putJarOnClasspathDialog(final Shell shell) {
		if (shell == null) {
			return false;
		}

		final boolean[] result= new boolean[1];
		shell.getDisplay().syncExec(new Runnable() {
			public void run() {
				String title= NewWizardMessages.BuildPathSupport_putoncpdialog_title;
				String message= NewWizardMessages.BuildPathSupport_putoncpdialog_message;
				result[0]= MessageDialog.openQuestion(shell, title, message);
			}
		});
		return result[0];
	}

	/**
	 * Apply a modified referenced classpath entry to the classpath.
	 * @param newReferencedEntry the modified entry. The entry's kind or path must be unchanged.
	 * @param changedAttributes the attributes that have changed. See {@link CPListElement} for constants values.
	 * @param jproject project where the entry belongs to
	 * @param monitor the progress monitor to use
	 * @throws CoreException if the update failed
	 */
	private static void updateReferencedClasspathEntry(IJavaProject jproject, IClasspathEntry newReferencedEntry, String[] changedAttributes, IProgressMonitor monitor) throws CoreException {
		IClasspathEntry[] oldReferencedClasspath= jproject.getReferencedClasspathEntries();
		int nEntries= oldReferencedClasspath.length;
		ArrayList<IClasspathEntry> newReferencedEntries= new ArrayList<IClasspathEntry>(nEntries + 1);
		int entryKind= newReferencedEntry.getEntryKind();
		IPath jarPath= newReferencedEntry.getPath();
		boolean found= false;
		for (int i= 0; i < nEntries; i++) {
			IClasspathEntry curr= oldReferencedClasspath[i];
			if (curr.getEntryKind() == entryKind && curr.getPath().equals(jarPath)) {
				// add modified entry
				newReferencedEntries.add(getUpdatedEntry(curr, newReferencedEntry, changedAttributes, jproject));
				found= true;
			} else {
				newReferencedEntries.add(curr);
			}
		}
		if (!found) {
			newReferencedEntries.add(newReferencedEntry);
		}
		IClasspathEntry[] newReferencedClasspath= newReferencedEntries.toArray(new IClasspathEntry[newReferencedEntries.size()]);
		
		jproject.setRawClasspath(jproject.getRawClasspath(), newReferencedClasspath, jproject.getOutputLocation(), monitor);
	}
	
	/**
	 * Sets the default compiler compliance options iff <code>modifiedClassPathEntries</code>
	 * contains a classpath container entry that is modified or new and that points to an execution
	 * environment. Does nothing if the EE or the options could not be resolved.
	 * 
	 * @param javaProject the Java project
	 * @param modifiedClassPathEntries a list of {@link CPListElement}
	 * 
	 * @see #getEEOptions(IExecutionEnvironment)
	 * 
	 * @since 3.5
	 */
	public static void setEEComplianceOptions(IJavaProject javaProject, List<CPListElement> modifiedClassPathEntries) {
		for (Iterator<CPListElement> iter= modifiedClassPathEntries.iterator(); iter.hasNext();) {
			CPListElement entry= iter.next();
			if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
				IPath path= entry.getPath();
				if (! path.equals(entry.getOrginalPath())) {
					String eeID= JavaRuntime.getExecutionEnvironmentId(path);
					if (eeID != null) {
						setEEComplianceOptions(javaProject, eeID, null);
						return;
					}
				}
			}
		}
	}

	/**
	 * Sets the default compiler compliance options based on the given execution environment.
	 * Does nothing if the EE or the options could not be resolved.
	 * 
	 * @param javaProject the Java project
	 * @param eeID the execution environment ID
	 * @param newProjectCompliance compliance to set for a new project, can be <code>null</code>
	 * 
	 * @see #getEEOptions(IExecutionEnvironment)
	 * 
	 * @since 3.5
	 */
	public static void setEEComplianceOptions(IJavaProject javaProject, String eeID, String newProjectCompliance) {
		IExecutionEnvironment ee= JavaRuntime.getExecutionEnvironmentsManager().getEnvironment(eeID);
		if (ee != null) {
			Map<String, String> options= javaProject.getOptions(false);
			Map<String, String> eeOptions= getEEOptions(ee);
			if (eeOptions != null) {
				for (int i= 0; i < PREFS_COMPLIANCE.length; i++) {
					String option= PREFS_COMPLIANCE[i];
					options.put(option, eeOptions.get(option));
				}
				
				if (newProjectCompliance != null) {
					JavaModelUtil.setDefaultClassfileOptions(options, newProjectCompliance); // complete compliance options
				}
				
				String option= JavaCore.COMPILER_CODEGEN_INLINE_JSR_BYTECODE;
				String inlineJSR= eeOptions.get(option);
				if (inlineJSR != null) {
					options.put(option, inlineJSR);
				}
				
				javaProject.setOptions(options);
			}
		}
	}

	/**
	 * Returns the compliance options from the given EE. If the result is not <code>null</code>,
	 * it contains at least these core options:
	 * <ul>
	 * <li>{@link JavaCore#COMPILER_COMPLIANCE}</li>
	 * <li>{@link JavaCore#COMPILER_SOURCE}</li>
	 * <li>{@link JavaCore#COMPILER_CODEGEN_TARGET_PLATFORM}</li>
	 * <li>{@link JavaCore#COMPILER_PB_ASSERT_IDENTIFIER}</li>
	 * <li>{@link JavaCore#COMPILER_PB_ENUM_IDENTIFIER}</li>
	 * <li>{@link JavaCore#COMPILER_CODEGEN_INLINE_JSR_BYTECODE} for compliance levels 1.5 and greater</li>
	 * </ul>
	 * 
	 * @param ee the EE, can be <code>null</code>
	 * @return the options, or <code>null</code> if none
	 * @since 3.5
	 */
	public static Map<String, String> getEEOptions(IExecutionEnvironment ee) {
		if (ee == null)
			return null;
		Map<String, String> eeOptions= ee.getComplianceOptions();
		if (eeOptions == null)
			return null;
		
		Object complianceOption= eeOptions.get(JavaCore.COMPILER_COMPLIANCE);
		if (!(complianceOption instanceof String))
			return null;
	
		// eeOptions can miss some options, make sure they are complete:
		HashMap<String, String> options= new HashMap<String, String>();
		JavaModelUtil.setComplianceOptions(options, (String)complianceOption);
		options.putAll(eeOptions);
		return options;
	}
}

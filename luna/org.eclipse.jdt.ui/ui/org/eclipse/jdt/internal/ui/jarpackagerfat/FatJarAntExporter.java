/*******************************************************************************
 * Copyright (c) 2007, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 213638 [jar exporter] create ANT build file for current settings
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 220257 [jar application] ANT build file does not create Class-Path Entry in Manifest
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 219530 [jar application] add Jar-in-Jar ClassLoader option
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 262766 [jar exporter] ANT file for Jar-in-Jar option contains relative path to jar-rsrc-loader.zip
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackagerfat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;

import org.eclipse.debug.core.ILaunchConfiguration;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

/**
 * Create an ANT script for a runnable JAR.
 * The script is generated based on the classpath of the
 * selected launch-configuration.
 *
 * @since 3.4
 */
public abstract class FatJarAntExporter {

	protected static class SourceInfo {

		public final boolean isJar;
		public final String absPath;
		public final String relJarPath;

		public SourceInfo(boolean isJar, String absPath, String relJarPath) {
			this.isJar= isJar;
			this.absPath= absPath;
			this.relJarPath= relJarPath;
		}
	}

	private ILaunchConfiguration fLaunchConfiguration;
	private IPath fAbsJarfile;
	private IPath fAntScriptLocation;

	/**
	 * Create an instance of the ANT exporter. An ANT exporter can generate an ANT script
	 * at the given ant script location for the given launch configuration
	 * @param antScriptLocation the location of the ANT script to generate
	 * @param jarLocation the location of the jar file which the ANT script will generate
	 * @param launchConfiguration the launch configuration to generate a ANT script for
	 */
	public FatJarAntExporter(IPath antScriptLocation, IPath jarLocation, ILaunchConfiguration launchConfiguration) {
		fLaunchConfiguration= launchConfiguration;
		fAbsJarfile= jarLocation;
		fAntScriptLocation= antScriptLocation;
	}

	/**
	 * Create the ANT script based on the information
	 * given in the constructor.
	 *
	 * @param status to report warnings to
	 * @throws CoreException if something went wrong while generating the ant script
	 */
	public void run(MultiStatus status) throws CoreException {
		try {

			IPath[] classpath= getClasspath(fLaunchConfiguration);
			String mainClass= getMainClass(fLaunchConfiguration, status);
			String projectName= fLaunchConfiguration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, ""); //$NON-NLS-1$

			buildANTScript(fAntScriptLocation, projectName, fAbsJarfile, mainClass, convert(classpath));

		} catch (FileNotFoundException e) {
			throw new CoreException(
					new Status(IStatus.ERROR, JavaUI.ID_PLUGIN,
							Messages.format(FatJarPackagerMessages.FatJarPackageWizard_antScript_error_readingOutputFile, new Object[] {
									BasicElementLabels.getPathLabel(fAntScriptLocation, true), e.getLocalizedMessage() })
							)
					);
		} catch (IOException e) {
			throw new CoreException(
					new Status(IStatus.ERROR, JavaUI.ID_PLUGIN,
							Messages.format(FatJarPackagerMessages.FatJarPackageWizard_antScript_error_writingOutputFile, new Object[] {
									BasicElementLabels.getPathLabel(fAntScriptLocation, true), e.getLocalizedMessage() })
							)
					);
		}
	}

	private static IPath[] getClasspath(ILaunchConfiguration configuration) throws CoreException {
		IRuntimeClasspathEntry[] entries= JavaRuntime.computeUnresolvedRuntimeClasspath(configuration);
		entries= JavaRuntime.resolveRuntimeClasspath(entries, configuration);

		ArrayList<IPath> userEntries= new ArrayList<IPath>(entries.length);
		for (int i= 0; i < entries.length; i++) {
			if (entries[i].getClasspathProperty() == IRuntimeClasspathEntry.USER_CLASSES) {

				String location= entries[i].getLocation();
				if (location != null) {
					IPath entry= Path.fromOSString(location);
					if (!userEntries.contains(entry)) {
						userEntries.add(entry);
					}
				}
			}
		}
		return userEntries.toArray(new IPath[userEntries.size()]);
	}

	private static String getMainClass(ILaunchConfiguration launchConfig, MultiStatus status) {
		String result= null;
		if (launchConfig != null) {
			try {
				result= launchConfig.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, (String) null);
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		}
		if (result == null) {
			status.add(new Status(IStatus.WARNING, JavaUI.ID_PLUGIN, FatJarPackagerMessages.FatJarPackageWizardPage_LaunchConfigurationWithoutMainType_warning));
			result= ""; //$NON-NLS-1$
		}
		return result;
	}


	protected static SourceInfo[] convert(IPath[] classpath) {
		SourceInfo[] result= new SourceInfo[classpath.length];
		for (int i= 0; i < classpath.length; i++) {
			IPath path= classpath[i];
			if (path != null) {
				if (path.toFile().isDirectory()) {
					result[i]= new SourceInfo(false, path.toString(), ""); //$NON-NLS-1$
				} else if (path.toFile().isFile()) {
					// TODO: check for ".jar" extension?
					result[i]= new SourceInfo(true, path.toString(), ""); //$NON-NLS-1$
				}
			}
		}

		return result;
	}

	/**
	 * Create an ANT script at the given location.
	 * 
	 * @param antScriptLocation to write ANT script to
	 * @param projectName base project for informational purpose only
	 * @param absJarfile path to the destination
	 * @param mainClass the optional main-class
	 * @param sourceInfos array of sources
	 * @throws IOException if an exception occurred while writing to the stream,
	 */
	protected abstract void buildANTScript(IPath antScriptLocation, String projectName, IPath absJarfile, String mainClass, SourceInfo[] sourceInfos) throws IOException;

}

/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 83258 [jar exporter] Deploy java application as executable jar
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 211045 [jar application] program arguments are ignored
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 213638 [jar exporter] create ANT build file for current settings
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 219530 [jar application] add Jar-in-Jar ClassLoader option
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackagerfat;

import org.eclipse.osgi.util.NLS;

public final class FatJarPackagerMessages extends NLS {

	private static final String BUNDLE_NAME= "org.eclipse.jdt.internal.ui.jarpackagerfat.FatJarPackagerMessages";//$NON-NLS-1$

	public static String JarPackageWizard_jarExport_title;
	public static String JarPackageWizard_jarExportError_message;
	public static String JarPackageWizard_jarExportError_title;
	public static String JarPackageWizard_windowTitle;

	public static String FatJarPackageWizard_antScript_error_readingOutputFile;
	public static String FatJarPackageWizard_antScript_error_writingOutputFile;

	public static String JarPackageWizardPage_title;

	public static String FatJarBuilder_error_readingArchiveFile;

	public static String FatJarPackageWizard_JarExportProblems_message;

	public static String FatJarPackageWizardPage_destinationGroupTitle;
	public static String FatJarPackageWizardPage_saveAntScript_text;
	public static String FatJarPackageWizardPage_antScriptLocation_text;
	public static String FatJarPackageWizardPage_antScriptLocationBrowse_text;
	public static String FatJarPackageWizardPage_libraryHandlingGroupTitle;

	public static String FatJarPackageWizardPage_extractJars_text;

	public static String FatJarPackageWizardPage_packageJars_text;

	public static String FatJarPackageWizardPage_copyJarFiles_text;

	public static String FatJarPackageWizardPage_error_missingClassFile;
	public static String FatJarPackageWizard_IPIssueDialog_message;

	public static String FatJarPackageWizard_IPIssueDialog_title;

	public static String FatJarPackageWizardPage_info_antScriptLocationRelative;
	public static String FatJarPackageWizardPage_warning_launchConfigContainsProgramArgs;
	public static String FatJarPackageWizardPage_warning_launchConfigContainsVMArgs;
	public static String FatJarPackageWizardPage_error_noMainMethod;
	public static String FatJarPackageWizardPage_error_ant_script_generation_failed;

	public static String FatJarPackageWizardPage_error_antScriptLocationMissing;
	public static String FatJarPackageWizardPage_error_antScriptLocationIsDir;
	public static String FatJarPackageWizardPage_error_antScriptLocationUnwritable;
	public static String FatJarPackageWizardPage_launchConfigGroupTitle;
	public static String FatJarPackageWizardPage_LaunchConfigurationWithoutMainType_warning;
	public static String FatJarPackageWizardPage_description;

	public static String FatJarPackage_confirmCreate_title;

	public static String FatJarPackageAntScript_confirmCreate_message;
	public static String FatJarPackageAntScript_error_couldNotGetXmlBuilder;
	public static String FatJarPackageAntScript_error_couldNotTransformToXML;

	static {
		NLS.initializeMessages(BUNDLE_NAME, FatJarPackagerMessages.class);
	}

	private FatJarPackagerMessages() {
		// Do not instantiate
	}
}

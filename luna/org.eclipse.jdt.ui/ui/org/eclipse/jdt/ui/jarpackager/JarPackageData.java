/*******************************************************************************
 * Copyright (c) 2000, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 83258 [jar exporter] Deploy java application as executable jar
 *******************************************************************************/
package org.eclipse.jdt.ui.jarpackager;

import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.operation.IRunnableContext;

import org.eclipse.ltk.core.refactoring.RefactoringDescriptorProxy;

import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.jarpackager.JarFileExportOperation;
import org.eclipse.jdt.internal.ui.jarpackager.JarPackageReader;
import org.eclipse.jdt.internal.ui.jarpackager.JarPackageWriter;
import org.eclipse.jdt.internal.ui.jarpackager.JarPackagerUtil;
import org.eclipse.jdt.internal.ui.jarpackager.PlainJarBuilder;
import org.eclipse.jdt.internal.ui.jarpackagerfat.UnpackFatJarBuilder;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;

/**
 * Model for a JAR package which stores information used during JAR export and
 * import.
 * <p>
 * Clients may subclass.
 * </p>
 *
 * @see org.eclipse.jdt.ui.jarpackager.JarWriter3
 * @since 2.0
 */
public class JarPackageData {

	private String	fManifestVersion;

	/*
	 * What to export - internal locations
	 * The list fExported* is null if fExport* is false)
	 */
	private boolean fExportClassFiles;	// export generated class files and resources
	private boolean fExportOutputFolders;	// export all output folder of enclosing projects
	private boolean fExportJavaFiles;		// export java files and resources

	/*
	 * Source folder hierarchy is created in the JAR if true
	 */
	private boolean fUseSourceFolderHierarchy;

	/*
	 * Projects of which files are exported will be built if true
	 * and auto-build is off.
	 */
	private boolean fBuildIfNeeded;

	/*
	 * Leaf elements (no containers) to export
	 */
	private Object[]	fElements; // inside workspace

	private IPath		fJarLocation; // external location
	private boolean	fOverwrite;
	private boolean	fCompress;

	private boolean	fSaveDescription;
	private IPath		fDescriptionLocation; // internal location

	/*
	 * A normal JAR has a manifest (fUsesManifest is true)
	 * The manifest can be contributed in two ways
	 * - it can be generated (fGenerateManifest is true) and
	 *		- saved (fSaveManifest is true)
	 *		- saved and reused (fReuseManifest is true implies: fSaveManifest is true)
	 * - it can be specified (fGenerateManifest is false and the
	 *		manifest location must be specified (fManifestLocation))
	 */
	private boolean	fUsesManifest;
	private boolean	fSaveManifest;
	private boolean	fReuseManifest;
	private boolean	fGenerateManifest;
	private IPath		fManifestLocation; // internal location

	/*
	 * Sealing: a JAR can be
	 * - sealed (fSealJar is true) and a list of
	 *		unsealed packages can be defined (fPackagesToUnseal)
	 *		while the list of sealed packages is ignored
	 * - unsealed (fSealJar is false) and the list of
	 *		sealed packages can be defined (fPackagesToSeal)
	 *		while the list of unsealed packages is ignored
	 */
	private boolean fSealJar;
	private IPackageFragment[] fPackagesToSeal;
	private IPackageFragment[] fPackagesToUnseal;

 	private IType fManifestMainClass;

 	private String fComment; // the JAR comment

	/*
	 * Error handling
	 */
	private boolean fExportErrors;
	private boolean fExportWarnings;

	// The provider for the manifest file
	private IManifestProvider fManifestProvider;

	// Add directory entries to the jar
	private boolean fIncludeDirectoryEntries;

	// Projects for which to store refactoring information
	private IProject[] fRefactoringProjects= {};

	// Should the package be refactoring aware?
	private boolean fRefactoringAware= false;

	// Should the exporter only export refactorings causing structural changes?
	private boolean fRefactoringStructural= false;

	// Should the exporter include deprecation resolving information?
	private boolean fDeprecationAware= true;

	// The refactoring descriptors to export
	private RefactoringDescriptorProxy[] fRefactoringDescriptors= {};

	// Builder used by the JarFileExportOperation to build the jar file
	private IJarBuilder fJarBuilder;

	// The launch configuration used by the fat jar builder to determine dependencies
 	private String  fLaunchConfigurationName;

	/**
	 * Creates a new Jar Package Data structure
	 */
	public JarPackageData() {
		setExportClassFiles(true);
		setExportOutputFolders(false);
		setUseSourceFolderHierarchy(false);
		setCompress(true);
		setSaveDescription(false);
		setJarLocation(Path.EMPTY);
		setDescriptionLocation(Path.EMPTY);
		setUsesManifest(true);
		setGenerateManifest(true);
		setReuseManifest(false);
		setSaveManifest(false);
		setManifestLocation(Path.EMPTY);
		setExportErrors(true);
		setExportWarnings(true);
		setBuildIfNeeded(true);
		setIncludeDirectoryEntries(false);
	}

	// ----------- Accessors -----------

	/**
	 * Tells whether the JAR is compressed or not.
	 *
	 * @return	<code>true</code> if the JAR is compressed
	 */
	public boolean isCompressed() {
		return fCompress;
	}

	/**
	 * Set whether the JAR is compressed or not.
	 *
	 * @param state a boolean indicating the new state
	 */
	public void setCompress(boolean state) {
		fCompress= state;
	}

	/**
	 * Tells whether files can be overwritten without warning.
	 *
	 * @return	<code>true</code> if files can be overwritten without warning
	 */
	public boolean allowOverwrite() {
		return fOverwrite;
	}

	/**
	 * Sets whether files can be overwritten without warning.
	 *
	 * @param state a boolean indicating the new state
	 */
	public void setOverwrite(boolean state) {
		fOverwrite= state;
	}

	/**
	 * Tells whether class files and resources are exported.
	 *
	 * @return	<code>true</code> if class files and resources are exported
	 */
	public boolean areClassFilesExported() {
		return fExportClassFiles;
	}

	/**
	 * Sets option to export class files and resources.
	 *
	 * @param state a boolean indicating the new state
	 */
	public void setExportClassFiles(boolean state) {
		fExportClassFiles= state;
	}

	/**
	 * Tells whether all output folders for the
	 * enclosing projects of the exported elements.
	 *
	 * @return	<code>true</code> if output folder are exported
	 * @since 3.0
	 */
	public boolean areOutputFoldersExported() {
		return fExportOutputFolders;
	}

	/**
	 * Sets option to export all output folders for the
	 * enclosing projects of the exported elements.
	 *
	 * @param state a boolean indicating the new state
	 * @since 3.0
	 */
	public void setExportOutputFolders(boolean state) {
		fExportOutputFolders= state;
	}

	/**
	 * Tells whether files created by the Java builder are exported.
	 *
	 * @return	<code>true</code> if output folder are exported
	 * @since 3.0
	 */
	public boolean areGeneratedFilesExported() {
		return fExportOutputFolders || fExportClassFiles;
	}

	/**
	 * Tells whether java files and resources are exported.
	 *
	 * @return	<code>true</code> if java files and resources are exported
	 */
	public boolean areJavaFilesExported() {
		return fExportJavaFiles;
	}

	/**
	 * Sets the option to export Java source and resources.
	 *
	 * @param state the new state
	 */
	public void setExportJavaFiles(boolean state) {
		fExportJavaFiles= state;
	}

	/**
	 * Tells whether the source folder hierarchy is used.
	 * <p>
	 * Using the source folder hierarchy only makes sense if
	 * java files are but class files aren't exported.
	 * </p>
	 *
	 * @return	<code>true</code> if source folder hierarchy is used
	 */
	public boolean useSourceFolderHierarchy() {
		return fUseSourceFolderHierarchy;
	}

	/**
	 * Set the option to export the source folder hierarchy.
	 *
	 * @param state the new state
	 */
	public void setUseSourceFolderHierarchy(boolean state) {
		fUseSourceFolderHierarchy= state;
	}

	/**
	 * Gets the absolute location of the JAR file.
	 * This path is normally external to the workspace.
	 *
	 * @return the absolute path representing the location of the JAR file
	 *
	 * @since 3.0
	 */
	public IPath getAbsoluteJarLocation() {
		if (!fJarLocation.isAbsolute()) {
			IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
			if (fJarLocation.segmentCount() >= 2 && !"..".equals(fJarLocation.segment(0))) { //$NON-NLS-1$
				// reverse of AbstractJarDestinationWizardPage#handleDestinationBrowseButtonPressed()
				IFile file= root.getFile(fJarLocation);
				IPath absolutePath= file.getLocation();
				if (absolutePath != null) {
					return absolutePath;
				}
			}
			// The path does not exist in the workspace (e.g. because there's no such project).
			// Fallback is to just append the path to the workspace root.
			return root.getLocation().append(fJarLocation);
		}
		return fJarLocation;
	}

	/**
	 * Gets the location of the JAR file.
	 * This path is normally external to the workspace.
	 *
	 * @return the path representing the location of the JAR file
	 */
	public IPath getJarLocation() {
		return fJarLocation;
	}

	/**
	 * Sets the JAR file location.
	 *
	 * @param jarLocation a path denoting the location of the JAR file
	 */
	public void setJarLocation(IPath jarLocation) {
		fJarLocation= jarLocation;
	}

	/**
	 * Tells whether the manifest file must be generated.
	 *
	 * @return <code>true</code> if the manifest has to be generated
	 */
	public boolean isManifestGenerated() {
		return fGenerateManifest;
	}

	/**
	 * Set whether a manifest must be generated or not.
	 *
	 * @param state the new state
	 */
	public void setGenerateManifest(boolean state) {
		fGenerateManifest= state;
	}

	/**
	 * Tells whether the manifest file must be saved to the
	 * specified file during the export operation.
	 *
	 * @return	<code>true</code> if the manifest must be saved
	 * @see #getManifestLocation()
	 */
	public boolean isManifestSaved() {
		return fSaveManifest;
	}

	/**
	 * Sets whether the manifest file must be saved during export
	 * operation or not.
	 *
	 * @param state the new state
	 * @see #getManifestLocation()
	 */
	public void setSaveManifest(boolean state) {
		fSaveManifest= state;
		if (!fSaveManifest)
			// can't reuse manifest if it is not saved
			setReuseManifest(false);
	}

	/**
	 * Tells whether a previously generated manifest should be reused.
	 *
	 * @return	<code>true</code> if the generated manifest will be reused when regenerating this JAR,
	 * 			<code>false</code> if the manifest has to be regenerated
	 */
	public boolean isManifestReused() {
		return fReuseManifest;
	}

	/**
	 * Sets whether a previously generated manifest should be reused.
	 *
	 * @param state the new state
	 */
	public void setReuseManifest(boolean state) {
		fReuseManifest= state;
		if (fReuseManifest)
			// manifest must be saved in order to be reused
			setSaveManifest(true);
	}

	/**
	 * Returns the location of a user-defined manifest file.
	 *
	 * @return	the path of the user-defined manifest file location,
	 * 			or <code>null</code> if none is specified
	 */
	public IPath getManifestLocation() {
		return fManifestLocation;
	}

	/**
	 * Sets the location of a user-defined manifest file.
	 *
	 * @param manifestLocation the path of the user-define manifest location
	 */
	public void setManifestLocation(IPath manifestLocation) {
		fManifestLocation= manifestLocation;
	}

	/**
	 * Gets the manifest file (as workspace resource).
	 *
	 * @return a file which points to the manifest
	 */
	public IFile getManifestFile() {
		IPath path= getManifestLocation();
		if (path.isValidPath(path.toString()) && path.segmentCount() >= 2)
			return JavaPlugin.getWorkspace().getRoot().getFile(path);
		else
			return null;
	}

	/**
	 * Gets the manifest version.
	 *
	 * @return a string containing the manifest version
	 */
	public String getManifestVersion() {
		if (fManifestVersion == null)
			return "1.0"; //$NON-NLS-1$
		return fManifestVersion;
	}

	/**
	 * Sets the manifest version.
	 *
	 * @param manifestVersion the string which contains the manifest version
	 */
	public void setManifestVersion(String manifestVersion) {
		fManifestVersion= manifestVersion;
	}

	/**
	 * Answers whether a manifest must be included in the JAR.
	 *
	 * @return <code>true</code> if a manifest has to be included
	 */
	public boolean usesManifest() {
		return fUsesManifest;
	}

	/**
	 * Sets whether a manifest must be included in the JAR.
	 *
	 * @param state the new state
	 */
	public void setUsesManifest(boolean state) {
		fUsesManifest= state;
	}

	/**
	 * Gets the manifest provider for this JAR package.
	 *
	 * @return the IManifestProvider
	 */
	public IManifestProvider getManifestProvider() {
		if (fManifestProvider == null)
			return getJarBuilder().getManifestProvider();
		return fManifestProvider;
	}

	/**
	 * Sets the manifest provider.
	 *
	 * @param manifestProvider the ManifestProvider to set
	 */
	public void setManifestProvider(IManifestProvider manifestProvider) {
		fManifestProvider= manifestProvider;
	}

	/**
	 * Answers whether the JAR itself is sealed.
	 * The manifest will contain a "Sealed: true" statement.
	 * <p>
	 * This option should only be considered when the
	 * manifest file is generated.
	 * </p>
	 *
	 * @return <code>true</code> if the JAR must be sealed
	 * @see #isManifestGenerated()
	 */
	public boolean isJarSealed() {
		return fSealJar;
	}

	/**
	 * Sets whether the JAR itself is sealed.
	 * The manifest will contain the following entry:
	 * Sealed: true
	 * <p>
	 * This option should only be considered when the
	 * manifest file is generated.
	 * </p>
	 *
	 * @param sealJar <code>true</code> if the JAR must be sealed
	 * @see #isManifestGenerated()
	 */
	public void setSealJar(boolean sealJar) {
		fSealJar= sealJar;
	}

	/**
	 * Sets the packages which should be sealed.
	 * The following entry will be added to the manifest file for each package:
	 * Name: &lt;name of the package&gt;
	 * Sealed: true
	 * <p>
	 * This should only be used if the JAR itself is not sealed.
	 * </p>
	 *
	 * @param packagesToSeal an array of <code>IPackageFragment</code> to seal
	 */
	public void setPackagesToSeal(IPackageFragment[] packagesToSeal) {
		fPackagesToSeal= packagesToSeal;
	}

	/**
	 * Gets the packages which should be sealed.
	 * The following entry will be added to the manifest file for each package:
	 * Name: &lt;name of the package&gt;
	 * Sealed: true
	 * <p>
	 * This should only be used if the JAR itself is not sealed.
	 * </p>
	 *
	 * @return an array of <code>IPackageFragment</code>
	 */
	public IPackageFragment[] getPackagesToSeal() {
		if (fPackagesToSeal == null)
			return new IPackageFragment[0];
		else
			return fPackagesToSeal;
	}

	/**
	 * Gets the packages which should explicitly be unsealed.
	 * The following entry will be added to the manifest file for each package:
	 * Name: &lt;name of the package&gt;
	 * Sealed: false
	 * <p>
	 * This should only be used if the JAR itself is sealed.
	 * </p>
	 *
	 * @return an array of <code>IPackageFragment</code>
	 */
	public IPackageFragment[] getPackagesToUnseal() {
		if (fPackagesToUnseal == null)
			return new IPackageFragment[0];
		else
			return fPackagesToUnseal;
	}

	/**
	 * Set the packages which should explicitly be unsealed.
	 * The following entry will be added to the manifest file for each package:
	 * Name: &lt;name of the package&gt;
	 * Sealed: false
	 * <p>
	 * This should only be used if the JAR itself is sealed.
	 * </p>
	 *
	 * @param packagesToUnseal an array of <code>IPackageFragment</code>
	 */
	public void setPackagesToUnseal(IPackageFragment[] packagesToUnseal) {
		fPackagesToUnseal= packagesToUnseal;
	}

	/**
	 * Tells whether a description of this JAR package must be saved
	 * to a file by a JAR description writer during the export operation.
	 * <p>
	 * The JAR writer defines the format of the file.
	 * </p>
	 *
	 * @return	<code>true</code> if this JAR package will be saved
	 * @see #getDescriptionLocation()
	 */
	public boolean isDescriptionSaved() {
		return fSaveDescription;
	}

	/**
	 * Set whether a description of this JAR package must be saved
	 * to a file by a JAR description writer during the export operation.
	 * <p>
	 * The format is defined by the client who implements the
	 * reader/writer pair.
	 * </p>
	 * @param state a boolean containing the new state
	 * @see #getDescriptionLocation()
	 * @see IJarDescriptionWriter
	 */
	public void setSaveDescription(boolean state) {
		fSaveDescription= state;
	}

	/**
	 * Returns the location of file containing the description of a JAR.
	 * This location is inside the workspace.
	 *
	 * @return	the path of the description file location,
	 * 			or <code>null</code> if none is specified
	 */
	public IPath getDescriptionLocation() {
		return fDescriptionLocation;
	}

	/**
	 * Set the location of the JAR description file.
	 *
	 * @param descriptionLocation the path of location
	 */
	public void setDescriptionLocation(IPath descriptionLocation) {
		fDescriptionLocation= descriptionLocation;
	}

	/**
	 * Gets the description file (as workspace resource).
	 *
	 * @return a file which points to the description
	 */
	public IFile getDescriptionFile() {
		IPath path= getDescriptionLocation();
		if (path.isValidPath(path.toString()) && path.segmentCount() >= 2)
			return JavaPlugin.getWorkspace().getRoot().getFile(path);
		else
			return null;
	}

	/**
	 * Gets the manifest's main class.
	 *
	 * @return	the type which contains the main class or,
	 * 			<code>null</code> if none is specified
	 */
	public IType getManifestMainClass() {
		return fManifestMainClass;
	}

	/**
	 * Set the manifest's main class.
	 *
	 * @param manifestMainClass the type with the main class for the manifest file
	 */
	public void setManifestMainClass(IType manifestMainClass) {
		fManifestMainClass= manifestMainClass;
	}

	/**
	 * Returns the elements which will be exported.
	 * These elements are leaf objects e.g. <code>IFile</code>
	 * and not containers.
	 *
	 * @return an array of leaf objects
	 */
	public Object[] getElements() {
		if (fElements == null)
			setElements(new Object[0]);
		return fElements;
	}

	/**
	 * Set the elements which will be exported.
	 *
	 * These elements are leaf objects e.g. <code>IFile</code>.
	 * and not containers.
	 *
	 * @param elements	an array with leaf objects
	 */
	public void setElements(Object[] elements) {
		fElements= elements;
	}

	/**
	 * Returns the JAR's comment.
	 *
	 * @return the comment string or <code>null</code>
	 * 			if the JAR does not contain a comment
	 */
	public String getComment() {
		return fComment;
	}

	/**
	 * Sets the JAR's comment.
	 *
	 * @param comment	a string or <code>null</code>
	 * 					if the JAR does not contain a comment
	 */
	public void setComment(String comment) {
		fComment= comment;
	}

	/**
	 * Tell whether errors are logged.
	 * <p>
	 * The export operation decides where and
	 * how the errors are logged.
	 * </p>
	 *
	 * @return <code>true</code> if errors are logged
	 * @deprecated will be removed in final 2.0
	 */
	public boolean logErrors() {
		return true;
	}

	/**
	 * Sets whether errors are logged.
	 * <p>
	 * The export operation decides where and
	 * how the errors are logged.
	 * </p>
	 *
	 * @param logErrors <code>true</code> if errors are logged
	 * @deprecated will be removed in final 2.0
	 */
	public void setLogErrors(boolean logErrors) {
		// always true
	}

	/**
	 * Tells whether warnings are logged or not.
	 * <p>
	 * The export operation decides where and
	 * how the warnings are logged.
	 * </p>
	 *
	 * @return <code>true</code> if warnings are logged
	 * @deprecated will be removed in final 2.0
	 */
	public boolean logWarnings() {
		return true;
	}

	/**
	 * Sets if warnings are logged.
	 * <p>
	 * The export operation decides where and
	 * how the warnings are logged.
	 * </p>
	 *
	 * @param logWarnings <code>true</code> if warnings are logged
	 * @deprecated will be removed in final 2.0
	 */
	public void setLogWarnings(boolean logWarnings) {
		// always true
	}

	/**
	 * Answers if compilation units with errors are exported.
	 *
	 * @return <code>true</code> if CUs with errors should be exported
	 */
	public boolean areErrorsExported() {
		return fExportErrors;
	}

	/**
	 * Sets if compilation units with errors are exported.
	 *
	 * @param exportErrors <code>true</code> if CUs with errors should be exported
	 */
	public void setExportErrors(boolean exportErrors) {
		fExportErrors= exportErrors;
	}

	/**
	 * Answers if compilation units with warnings are exported.
	 *
	 * @return <code>true</code> if CUs with warnings should be exported
	 */
	public boolean exportWarnings() {
		return fExportWarnings;
	}

	/**
	 * Sets if compilation units with warnings are exported.
	 *
	 * @param exportWarnings <code>true</code> if CUs with warnings should be exported
	 */
	public void setExportWarnings(boolean exportWarnings) {
		fExportWarnings= exportWarnings;
	}

	/**
	 * Answers if a build should be performed before exporting files.
	 * This flag is only considered if auto-build is off.
	 *
	 * @return a boolean telling if a build should be performed
	 */
	public boolean isBuildingIfNeeded() {
		return fBuildIfNeeded;
	}

	/**
	 * Sets if a build should be performed before exporting files.
	 * This flag is only considered if auto-build is off.
	 *
	 * @param buildIfNeeded a boolean telling if a build should be performed
	 */
	public void setBuildIfNeeded(boolean buildIfNeeded) {
		fBuildIfNeeded= buildIfNeeded;
	}
	// ----------- Utility methods -----------

	/**
	 * Finds the class files for the given java file
	 * and returns them.
	 * <p>
	 * This is a hook for subclasses which want to implement
	 * a different strategy for finding the class files. The default
	 * strategy is to query the class files for the source file
	 * name attribute. If this attribute is missing then all class
	 * files in the corresponding output folder are exported.
	 * </p>
	 * <p>
	 * A CoreException can be thrown if an error occurs during this
	 * operation. The <code>CoreException</code> will not stop the export
	 * process but adds the status object to the status of the
	 * export runnable.
	 * </p>
	 *
	 * @param	javaFile a .java file
	 * @return	an array with class files or <code>null</code> to used the default strategy
	 * @throws	CoreException	if find failed, e.g. I/O error or resource out of sync
	 * @see	IJarExportRunnable#getStatus()
	 */
	public IFile[] findClassfilesFor(IFile javaFile) throws CoreException {
		return null;
	}

	/**
	 * Creates and returns a JarWriter for this JAR package.
	 *
	 * @param parent	the shell used to display question dialogs,
	 *				 	or <code>null</code> if "false/no/cancel" is the answer
	 * 					and no dialog should be shown
	 * @return a JarWriter2
	 * @see JarWriter2
	 * @throws CoreException if an unexpected exception happens
	 *
	 * @deprecated Use {@link #createJarWriter3(Shell)} instead
	 */
	public JarWriter2 createJarWriter(Shell parent) throws CoreException {
		return new JarWriter2(this, parent);
	}

	/**
	 * Creates and returns a JarWriter for this JAR package.
	 *
	 * @param parent	the shell used to display question dialogs,
	 *				 	or <code>null</code> if "false/no/cancel" is the answer
	 * 					and no dialog should be shown
	 * @return a JarWriter3
	 * @see JarWriter3
	 * @throws CoreException if an unexpected exception happens
	 *
	 * @since 3.2
	 * @deprecated use {@link #createPlainJarBuilder()} instead
	 */
	public JarWriter3 createJarWriter3(Shell parent) throws CoreException {
		return new JarWriter3(this, parent);
	}

	/**
	 * Creates and returns a jar builder capable of handling
	 * files but not archives.
	 *
	 * @return a new instance of a plain jar builder
	 *
	 * @since 3.4
	 */
	public IJarBuilder createPlainJarBuilder() {
		return new PlainJarBuilder();
	}

	/**
	 * Creates and returns a jar builder capable of handling
	 * files and archives.
	 *
	 * @return a new instance of a fat jar builder
	 *
	 * @since 3.4
	 */
	public IJarBuilder createFatJarBuilder() {
		return new UnpackFatJarBuilder();
	}

	/**
	 * Creates and returns a JarExportRunnable.
	 *
	 * @param	parent	the parent for the dialog,
	 * 			or <code>null</code> if no questions should be asked and
	 * 			no checks for unsaved files should be made.
	 * @return a JarExportRunnable
	 */
	public IJarExportRunnable createJarExportRunnable(Shell parent) {
		return new JarFileExportOperation(this, parent);
	}

	/**
	 * Creates and returns a JarExportRunnable for a list of JAR package
	 * data objects.
	 *
	 * @param	jarPackagesData	an array with JAR package data objects
	 * @param	parent			the parent for the dialog,
	 * 							or <code>null</code> if no dialog should be presented
	 * @return the {@link IJarExportRunnable}
	 */
	public IJarExportRunnable createJarExportRunnable(JarPackageData[] jarPackagesData, Shell parent) {
		return new JarFileExportOperation(jarPackagesData, parent);
	}

	/**
	 * Creates and returns a JAR package data description writer
	 * for this JAR package data object.
	 * <p>
     * It is the client's responsibility to close this writer.
	 * </p>
	 * @param outputStream	the output stream to write to
	 * @return a JarWriter
	 * @deprecated Use {@link #createJarDescriptionWriter(OutputStream, String)} instead
	 */
	public IJarDescriptionWriter createJarDescriptionWriter(OutputStream outputStream) {
		return new JarPackageWriter(outputStream, "UTF-8"); //$NON-NLS-1$
	}

	/**
	 * Creates and returns a JAR package data description writer
	 * for this JAR package data object.
	 * <p>
     * It is the client's responsibility to close this writer.
	 * </p>
	 * @param outputStream	the output stream to write to
	 * @param encoding the encoding to use
	 * @return a JarWriter
     * @since 3.3
	 */
	public IJarDescriptionWriter createJarDescriptionWriter(OutputStream outputStream, String encoding) {
		return new JarPackageWriter(outputStream, encoding);
	}

	/**
	 * Creates and returns a JAR package data description reader
	 * for this JAR package data object.
	 * <p>
     * It is the client's responsibility to close this reader.
	 * </p>
	 * @param inputStream	the input stream to read from
	 * @return a JarWriter
	 */
	public IJarDescriptionReader createJarDescriptionReader(InputStream inputStream) {
		return new JarPackageReader(inputStream);
	}

	/**
	 * Tells whether this JAR package data can be used to generate
	 * a valid JAR.
	 *
	 * @return <code>true</code> if the JAR Package info is valid
	 */
	public boolean isValid() {
		return (areGeneratedFilesExported() || areJavaFilesExported())
			&& getElements() != null && getElements().length > 0
			&& getAbsoluteJarLocation() != null
			&& isManifestAccessible()
			&& isMainClassValid(new BusyIndicatorRunnableContext());
	}

	/**
	 * Tells whether a manifest is available.
	 *
	 * @return <code>true</code> if the manifest is generated or the provided one is accessible
	 */
	public boolean isManifestAccessible() {
		if (isManifestGenerated())
			return true;
		IFile file= getManifestFile();
		return file != null && file.isAccessible();
	}

	/**
	 * Tells whether the specified manifest main class is valid.
	 *
	 * @param context the {@link IRunnableContext}
	 * @return <code>true</code> if a main class is specified and valid
	 */
	public boolean isMainClassValid(IRunnableContext context) {
		return JarPackagerUtil.isMainClassValid(this, context);
	}

	/**
	 * Tells whether directory entries are added to the jar.
	 *
	 * @return	<code>true</code> if directory entries are to be included
	 *
	 * @since 3.1
	 */
	public boolean areDirectoryEntriesIncluded() {
		return fIncludeDirectoryEntries;
	}

	/**
	 * Sets the option to include directory entries into the jar.
	 *
	 * @param includeDirectoryEntries <code>true</code> to include
	 *  directory entries <code>false</code> otherwise
	 *
	 *  @since 3.1
	 */
	public void setIncludeDirectoryEntries(boolean includeDirectoryEntries) {
		fIncludeDirectoryEntries = includeDirectoryEntries;
	}

	/**
	 * Returns the projects for which refactoring information should be stored.
	 * <p>
	 * This information is used for JAR export.
	 * </p>
	 *
	 * @return the projects for which refactoring information should be stored,
	 *         or an empty array
	 *
	 * @since 3.2
	 */
	public IProject[] getRefactoringProjects() {
		return fRefactoringProjects;
	}

	/**
	 * Is the JAR export wizard only exporting refactorings causing structural
	 * changes?
	 * <p>
	 * This information is used for JAR export.
	 * </p>
	 *
	 * @return <code>true</code> if exporting structural changes only,
	 *         <code>false</code> otherwise
	 * @since 3.2
	 */
	public boolean isExportStructuralOnly() {
		return fRefactoringStructural;
	}

	/**
	 * Is the JAR package refactoring aware?
	 * <p>
	 * This information is used both in JAR export and import
	 * </p>
	 *
	 * @return <code>true</code> if it is refactoring aware,
	 *         <code>false</code> otherwise
	 *
	 * @since 3.2
	 */
	public boolean isRefactoringAware() {
		return fRefactoringAware;
	}

	/**
	 * Is the JAR package deprecation aware?
	 * <p>
	 * This information is used in JAR export.
	 * </p>
	 *
	 * @return <code>true</code> if it is deprecation aware,
	 *         <code>false</code> otherwise
	 *
	 * @since 3.2
	 */
	public boolean isDeprecationAware() {
		return fDeprecationAware;
	}

	/**
	 * Sets the projects for which refactoring information should be stored.
	 * <p>
	 * This information is used for JAR export.
	 * </p>
	 *
	 * @param projects
	 *            the projects for which refactoring information should be
	 *            stored
	 *
	 * @since 3.2
	 */
	public void setRefactoringProjects(IProject[] projects) {
		Assert.isNotNull(projects);
		fRefactoringProjects= projects;
	}

	/**
	 * Determines whether the jar package is refactoring aware.
	 * <p>
	 * This information is used both in JAR export and import.
	 * </p>
	 *
	 * @param aware
	 *            <code>true</code> if it is refactoring aware,
	 *            <code>false</code> otherwise
	 *
	 * @since 3.2
	 */
	public void setRefactoringAware(boolean aware) {
		fRefactoringAware= aware;
	}

	/**
	 * Determines whether the jar package is deprecation aware.
	 * <p>
	 * This information is used in JAR export.
	 * </p>
	 *
	 * @param aware
	 *            <code>true</code> if it is deprecation aware,
	 *            <code>false</code> otherwise
	 *
	 * @since 3.2
	 */
	public void setDeprecationAware(boolean aware) {
		fDeprecationAware= aware;
	}

	/**
	 * Sets the refactoring descriptors to export.
	 * <p>
	 * This information is used in JAR export.
	 * </p>
	 *
	 * @param descriptors
	 *            the refactoring descriptors
	 *
	 * @since 3.2
	 */
	public void setRefactoringDescriptors(RefactoringDescriptorProxy[] descriptors) {
		Assert.isNotNull(descriptors);
		fRefactoringDescriptors= descriptors;
	}

	/**
	 * Returns the refactoring descriptors to export.
	 * <p>
	 * This information is used in JAR export.
	 * </p>
	 *
	 * @return the refactoring descriptors to export
	 *
	 * @since 3.2
	 */
	public RefactoringDescriptorProxy[] getRefactoringDescriptors() {
		return fRefactoringDescriptors;
	}

	/**
	 * Determines whether the jar packager exports only refactorings causing
	 * structural changes.
	 * <p>
	 * This information is used for JAR export.
	 * </p>
	 *
	 * @param structural
	 *            <code>true</code> if it exports only refactorings causing
	 *            structural changes, <code>false</code> otherwise
	 *
	 * @since 3.2
	 */
	public void setExportStructuralOnly(boolean structural) {
		fRefactoringStructural= structural;
	}

	/**
	 * Returns the jar builder which can be used to build the jar
	 * described by this package data.
	 *
	 * @return the builder to use
	 * @since 3.4
	 */
	public IJarBuilder getJarBuilder() {
		if (fJarBuilder == null)
			fJarBuilder= createPlainJarBuilder();
		return fJarBuilder;
	}

	/**
	 * Set the jar builder to use to build the jar.
	 *
	 * @param jarBuilder
	 *        the builder to use
	 * @since 3.4
	 */
	public void setJarBuilder(IJarBuilder jarBuilder) {
		fJarBuilder= jarBuilder;
	}

	/**
	 * Get the name of the launch configuration from
	 * which to retrieve classpath information.
	 *
	 * @return the name of the launch configuration
	 * @since 3.4
	 */
	public String getLaunchConfigurationName() {
		return fLaunchConfigurationName;
	}

	/**
	 * Set the name of the launch configuration form which
	 * to retrieve classpath information.
	 *
	 * @param name
	 *        name of the launch configuration
	 * @since 3.4
	 */
	public void setLaunchConfigurationName(String name) {
		fLaunchConfigurationName= name;
	}

}

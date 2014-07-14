/*******************************************************************************
 * Copyright (c) 2008, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 219530 [jar application] add Jar-in-Jar ClassLoader option
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.jarpackagerfat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.ZipFile;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.MultiStatus;

import org.eclipse.jdt.core.IPackageFragmentRoot;

import org.eclipse.jdt.ui.jarpackager.IManifestProvider;
import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.jarpackager.JarPackagerUtil;

/**
 * A jar builder which extracts the required libraries into a folder next to the generated jar.
 * 
 * @since 3.5
 */
public class UnpackJarBuilder extends FatJarBuilder {

	public static final String BUILDER_ID= "org.eclipse.jdt.ui.unpack_jar_builder"; //$NON-NLS-1$
	private static final String SUBFOLDER_SUFFIX= "_lib"; //$NON-NLS-1$
	
	private final String fSubfolder;
	private final IPath fSubfolderPath;

	private JarPackageData fJarPackage;
	
	private Set<String> jarNames;

	public UnpackJarBuilder(JarPackageData jarPackage) {
		fSubfolder= jarPackage.getAbsoluteJarLocation().removeFileExtension().lastSegment() + SUBFOLDER_SUFFIX;
		fSubfolderPath= jarPackage.getAbsoluteJarLocation().removeLastSegments(1).append(fSubfolder);
		fJarPackage= jarPackage;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getId() {
		return BUILDER_ID;
	}

	/**
	 * we do not need to merge any manifests here.
	 * @return false
	 */
	@Override
	public boolean isMergeManifests() {
		return false;
	}

	/**
	 * we do not need to remove signers here.
	 * @return false
	 */
	@Override
	public boolean isRemoveSigners() {
		return false;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public IManifestProvider getManifestProvider() {
		return new FatJarManifestProvider(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getManifestClasspath() {
		ArrayList<String> renamedJarNames= new ArrayList<String>();
		Object[] elements= fJarPackage.getElements();
		for (int i= 0; i < elements.length; i++) {
			Object element= elements[i];
			if (element instanceof IPackageFragmentRoot && ((IPackageFragmentRoot)element).isArchive()) {
				String jarName= ((IPackageFragmentRoot)element).getPath().toFile().getName();
				while (renamedJarNames.contains(jarName)) {
					jarName= FatJarPackagerUtil.nextNumberedFileName(jarName);
				}
				renamedJarNames.add(jarName);
			}
		}
		StringBuffer result= new StringBuffer();
		result.append("."); //$NON-NLS-1$
		for (Iterator<String> iterator= renamedJarNames.iterator(); iterator.hasNext();) {
			String jarName= iterator.next();
			result.append(" ").append(fSubfolder).append("/").append(jarName); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return result.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void open(JarPackageData jarPackage, Shell displayShell, MultiStatus status) throws CoreException {
		super.open(jarPackage, displayShell, status);
		fJarPackage= jarPackage;
		jarNames= new HashSet<String>();
		createBlankSubfolder(displayShell, jarPackage.allowOverwrite());
	}

	/**
	 * creates the subfolder or cleanup an existing subfolder. A dialog will ask the user.
	 * 
	 * @param parent the parent for the dialog, or <code>null</code> if no dialog should be
	 *            presented
	 * @param allowOverwrite true if the packager is allowed to overwrite existing folders
	 * @throws CoreException if creation of folder failed
	 */
	protected void createBlankSubfolder(Shell parent, boolean allowOverwrite) throws CoreException {
		File folder= fSubfolderPath.toFile();
		// Test if directory exists
		if (!folder.exists()) {
			// create sub-folder
			if (!folder.mkdirs())
				throw JarPackagerUtil.createCoreException("Could not create lib-folder '" + folder.getAbsolutePath() + "'", null); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			if (!folder.canWrite())
				throw JarPackagerUtil.createCoreException("Folder '" + folder.getAbsolutePath() + "' is not writable", null); //$NON-NLS-1$ //$NON-NLS-2$
			if (!allowOverwrite)
				if (parent == null || !JarPackagerUtil.askForOverwriteFolderPermission(parent, fSubfolderPath, true))
					throw JarPackagerUtil.createCoreException("Folder '" + folder.getAbsolutePath() + "' exists and should not be overwritten", null); //$NON-NLS-1$ //$NON-NLS-2$
			File[] jarFiles= folder.listFiles();
			for (int i= 0; i < jarFiles.length; i++) {
				if (!jarFiles[i].isFile())
					throw JarPackagerUtil.createCoreException("Subfolder '" + jarFiles[i].getAbsolutePath() + "' exists", null); //$NON-NLS-1$ //$NON-NLS-2$
				if (!jarFiles[i].delete())
					throw JarPackagerUtil.createCoreException("Could not delete file '" + jarFiles[i].getAbsolutePath() + "'", null); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
	}

	/**
	 * 
	 * {@inheritDoc}
	 */

	public void writeArchive(ZipFile jarFile, IProgressMonitor progressMonitor) {
		File jarPathFile= new File(jarFile.getName());
		String jarName= jarPathFile.getName();
		while (jarNames.contains(jarName)) {
			jarName= FatJarPackagerUtil.nextNumberedFileName(jarName);
		}
		jarNames.add(jarName);
		File destJarPathFile= new File(fSubfolderPath.toFile(), jarName);
		copyFile(jarPathFile, destJarPathFile);
	}

	private void copyFile(File src, File dest) {
		InputStream in= null;
		OutputStream out= null;
		try {
			in= new FileInputStream(src);
			out= new FileOutputStream(dest);
			byte[] buf= new byte[4096];
			int cnt= in.read(buf);
			while (cnt > 0) {
				out.write(buf, 0, cnt);
				cnt= in.read(buf);
			}
		} catch (RuntimeException e) {
			throw new RuntimeException(e);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			try {
				out.close();
			} catch (IOException ignore) {
			}
			try {
				in.close();
			} catch (IOException ignore) {
			}
		}
	}
	
}

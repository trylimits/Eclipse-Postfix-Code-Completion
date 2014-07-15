/*******************************************************************************
 * Copyright (c) 2007, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 83258 [jar exporter] Deploy java application as executable jar
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 219530 [jar application] add Jar-in-Jar ClassLoader option
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackagerfat;

import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.jarpackager.IManifestProvider;

import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

/**
 * A jar builder wich unpacks all referenced libraries into the generated jar.
 * 
 * @since 3.5
 */
public class UnpackFatJarBuilder extends FatJarBuilder {

	public static final String BUILDER_ID= "org.eclipse.jdt.ui.fat_jar_builder"; //$NON-NLS-1$

	/**
	 * {@inheritDoc}
	 */
	public String getId() {
		return BUILDER_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	public IManifestProvider getManifestProvider() {
		return new FatJarManifestProvider(this);
	}

	@Override
	public String getManifestClasspath() {
		return "."; //$NON-NLS-1$
	}

	@Override
	public boolean isMergeManifests() {
		return true;
	}

	@Override
	public boolean isRemoveSigners() {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	public void writeArchive(ZipFile jarFile, IProgressMonitor progressMonitor) {
		Enumeration<? extends ZipEntry> jarEntriesEnum= jarFile.entries();
		while (jarEntriesEnum.hasMoreElements()) {
			ZipEntry jarEntry= jarEntriesEnum.nextElement();
			if (!jarEntry.isDirectory()) {
				String entryName= jarEntry.getName();
				addFile(entryName, jarEntry, jarFile);
			}
			progressMonitor.worked(1);
			if (progressMonitor.isCanceled())
				throw new OperationCanceledException();
		}
	}

	private void addFile(String destinationPath, ZipEntry jarEntry, ZipFile zipFile) {
		// Handle META-INF/MANIFEST.MF
		if (destinationPath.equalsIgnoreCase("META-INF/MANIFEST.MF") //$NON-NLS-1$
				|| (isRemoveSigners() && destinationPath.startsWith("META-INF/") && destinationPath.endsWith(".SF"))) { //$NON-NLS-1$//$NON-NLS-2$
			return;
		}
		try {
			getJarWriter().addZipEntry(jarEntry, zipFile, destinationPath);
		} catch (IOException ex) {
			if (ex instanceof ZipException && ex.getMessage() != null && ex.getMessage().startsWith("duplicate entry:")) {//$NON-NLS-1$
				// ignore duplicates in META-INF (*.SF, *.RSA)
				if (!destinationPath.startsWith("META-INF/")) { //$NON-NLS-1$
					addWarning(ex.getMessage(), ex);
				}
			} else
				addWarning(Messages.format(FatJarPackagerMessages.FatJarBuilder_error_readingArchiveFile, new Object[] { BasicElementLabels.getResourceName(zipFile.getName()), ex.getLocalizedMessage() }), ex);
		}
	}
}

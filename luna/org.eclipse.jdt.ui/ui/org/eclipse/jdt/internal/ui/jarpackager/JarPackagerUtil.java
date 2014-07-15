/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
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
package org.eclipse.jdt.internal.ui.jarpackager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.IJavaStatusConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

/**
 * Utility methods for JAR Import/Export.
 */
public final class JarPackagerUtil {

	static final String JAR_EXTENSION= "jar"; //$NON-NLS-1$
	static final String DESCRIPTION_EXTENSION= "jardesc"; //$NON-NLS-1$

	private static final String META_INF_ENTRY= "META-INF"; //$NON-NLS-1$
	private static final String REFACTORINGS_ENTRY= META_INF_ENTRY + "/REFACTORINGS.XML"; //$NON-NLS-1$

	private JarPackagerUtil() {
		// Do nothing
	}

	public static boolean askToCreateDirectory(final Shell parent, File directory) {
		if (parent == null)
			return false;
		return queryDialog(parent, JarPackagerMessages.JarPackage_confirmCreate_title, Messages.format(JarPackagerMessages.JarPackage_confirmCreate_message, BasicElementLabels.getPathLabel(directory)));
	}

	/**
	 * Returns the name of the refactorings zip entry.
	 *
	 * @return the name of the refactorings zip entry
	 *
	 * @since 3.2
	 */
	public static String getRefactoringsEntry() {
		return REFACTORINGS_ENTRY;
	}

	/**
	 * Returns the name of the deprecations zip entry for the specified file.
	 *
	 * @param name
	 *            the name of the file
	 * @return the name of the deprecations zip entry
	 *
	 * @since 3.2
	 */
	public static String getDeprecationEntry(final String name) {
		return META_INF_ENTRY + "/" + name; //$NON-NLS-1$
	}

	/**
	 * Returns the name of the meta entry.
	 *
	 * @return the name of the meta entry
	 *
	 * @since 3.2
	 */
	public static String getMetaEntry() {
		return META_INF_ENTRY;
	}

	/**
	 * Computes and returns the elements as resources.
	 * The underlying resource is used for Java elements.
	 *
	 * @param elements elements for which to retrieve the resources from
	 * @return a List with the selected resources
	 */
	public static List<IResource> asResources(Object[] elements) {
		if (elements == null)
			return null;
		List<IResource> selectedResources= new ArrayList<IResource>(elements.length);
		for (int i= 0; i < elements.length; i++) {
			Object element= elements[i];
			if (element instanceof IJavaElement) {
				selectedResources.add(((IJavaElement)element).getResource());
			}
			else if (element instanceof IResource)
				selectedResources.add((IResource) element);
		}
		return selectedResources;
	}

	public static boolean askForOverwritePermission(final Shell parent, IPath filePath, boolean isOSPath) {
		if (parent == null)
			return false;
		return queryDialog(parent, JarPackagerMessages.JarPackage_confirmReplace_title, Messages.format(JarPackagerMessages.JarPackage_confirmReplace_message, BasicElementLabels.getPathLabel(filePath, isOSPath)));
	}

	public static boolean askForOverwriteFolderPermission(final Shell parent, IPath filePath, boolean isOSPath) {
		if (parent == null)
			return false;
		return queryDialog(parent, JarPackagerMessages.JarPackage_confirmOverwriteFolder_title, Messages.format(JarPackagerMessages.JarPackage_confirmOverwriteFolder_message, BasicElementLabels
				.getPathLabel(filePath, isOSPath)));
	}

	/**
	 * Gets the name of the manifest's main class
	 * 
	 * @param jarPackage the Jar package data
	 * @return a string with the name
	 */
	static String getMainClassName(JarPackageData jarPackage) {
		if (jarPackage.getManifestMainClass() == null)
			return ""; //$NON-NLS-1$
		else
			return jarPackage.getManifestMainClass().getFullyQualifiedName();
	}


	private static boolean queryDialog(final Shell parent, final String title, final String message) {
		Display display= parent.getDisplay();
		if (display == null || display.isDisposed())
			return false;
		final boolean[] returnValue= new boolean[1];
		Runnable runnable= new Runnable() {
			public void run() {
				returnValue[0]= MessageDialog.openQuestion(parent, title, message);
			}
		};
		display.syncExec(runnable);
		return returnValue[0];
	}

	/**
	 * Creates a <code>CoreException</code> with the given parameters.
	 *
	 * @param	message	a string with the message
	 * @param	ex		the exception to be wrapped or <code>null</code> if none
	 * @return a CoreException
	 */
	public static CoreException createCoreException(String message, Exception ex) {
		if (message == null)
			message= ""; //$NON-NLS-1$
		return new CoreException(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IJavaStatusConstants.INTERNAL_ERROR, message, ex));
	}

	/**
	 * Tells whether the specified manifest main class is valid.
	 * 
	 * @param data the Jar package data
	 * @param context the runnable context
	 * @return <code>true</code> if a main class is specified and valid
	 */
	public static boolean isMainClassValid(JarPackageData data, IRunnableContext context) {
		if (data == null)
			return false;

		IType mainClass= data.getManifestMainClass();
		if (mainClass == null)
			// no main class specified
			return true;

		try {
			// Check if main method is in scope
			IFile file= (IFile)mainClass.getResource();
			if (file == null || !contains(asResources(data.getElements()), file))
				return false;

			// Test if it has a main method
			return JavaModelUtil.hasMainMethod(mainClass);
		} catch (JavaModelException e) {
			JavaPlugin.log(e.getStatus());
		}
		return false;
	}

	static boolean contains(List<IResource> resources, IFile file) {
		if (resources == null || file == null)
			return false;

		if (resources.contains(file))
			return true;

		Iterator<IResource> iter= resources.iterator();
		while (iter.hasNext()) {
			IResource resource= iter.next();
			if (resource != null && resource.getType() != IResource.FILE) {
				List<IResource> children= null;
				try {
					children= Arrays.asList(((IContainer)resource).members());
				} catch (CoreException ex) {
					// ignore this folder
					continue;
				}
				if (children != null && contains(children, file))
					return true;
			}
		}
		return false;
	}

	/**
	 * Calculates the crc and size of the resource and updates the entry.
	 *
	 * @param entry
	 *            the jar entry to update
	 * @param stream
	 *            the input stream
	 * @param buffer
	 *            a shared buffer to store temporary data
	 *
	 * @throws IOException
	 *             if an input/output error occurs
	 */
	public static void calculateCrcAndSize(final ZipEntry entry, final InputStream stream, final byte[] buffer) throws IOException {
		int size= 0;
		final CRC32 crc= new CRC32();
		int count;
		try {
			while ((count= stream.read(buffer, 0, buffer.length)) != -1) {
				crc.update(buffer, 0, count);
				size+= count;
			}
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException exception) {
					// Do nothing
				}
			}
		}
		entry.setSize(size);
		entry.setCrc(crc.getValue());
	}

	/**
	 * Opens the archive file at the given location.<br>
	 * <em>Note: It is the caller's responsibility to close the returned
	 * {@link ZipFile}.</em>
	 * 
	 * @param location the location of the archive file
	 * @return the archive or <code>null</code> if it could not be retrieved
	 * @throws CoreException if the archive could not be read
	 * 
	 * @since 3.4
	 */
	public static ZipFile getArchiveFile(IPath location) throws CoreException {
		File localFile= null;

		IResource file= ResourcesPlugin.getWorkspace().getRoot().findMember(location);
		if (file != null) {
			// internal resource
			URI fileLocation= file.getLocationURI();

			IFileStore fileStore= EFS.getStore(fileLocation);
			localFile= fileStore.toLocalFile(EFS.NONE, null);
			if (localFile == null)
				// non local file system
				localFile= fileStore.toLocalFile(EFS.CACHE, null);
		} else {
			// external resource -> it is ok to use toFile()
			localFile= location.toFile();
		}

		if (localFile == null)
			return null;

		try {
			return new ZipFile(localFile);
		} catch (ZipException e) {
			throw new CoreException(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, e.getLocalizedMessage(), e));
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, e.getLocalizedMessage(), e));
		}
	}
}
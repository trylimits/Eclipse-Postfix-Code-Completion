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
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackagerfat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;

import org.eclipse.jdt.ui.jarpackager.IManifestProvider;
import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.jarpackager.JarPackagerUtil;

/**
 * A manifest provider creates manifest files for a fat jar.
 *
 * @since 3.4
 */
public class FatJarManifestProvider implements IManifestProvider {

	private static final String SEALED_VALUE= "true"; //$NON-NLS-1$
	private static final String UNSEALED_VALUE= "false"; //$NON-NLS-1$

	private FatJarBuilder fBuilder;

	public FatJarManifestProvider(FatJarBuilder builder) {
		fBuilder= builder;
	}

	/**
	 * {@inheritDoc}
	 */
	public Manifest create(JarPackageData jarPackage) throws CoreException {
		Manifest result;
		Manifest ownManifest= createOwn(jarPackage);
		setManifestClasspath(ownManifest, fBuilder.getManifestClasspath());
		if (fBuilder.isMergeManifests()) {
			List<ZipFile> openZips= new ArrayList<ZipFile>();
			try {
				List<Manifest> otherManifests= new ArrayList<Manifest>();
				Object[] elements= jarPackage.getElements();
				for (int i= 0; i < elements.length; i++) {
					Object element= elements[i];
					if (element instanceof IPackageFragmentRoot && ((IPackageFragmentRoot) element).isArchive()) {
						ZipFile zip= JarPackagerUtil.getArchiveFile(((IPackageFragmentRoot) element).getPath());
						openZips.add(zip);
						Enumeration<? extends ZipEntry> entries= zip.entries();
						while (entries.hasMoreElements()) {
							ZipEntry entry= entries.nextElement();
							if (entry.getName().equalsIgnoreCase("META-INF/MANIFEST.MF")) { //$NON-NLS-1$
								InputStream inputStream= null;
								try {
									inputStream= zip.getInputStream(entry);
									Manifest otherManifest= new Manifest(inputStream);
									otherManifests.add(otherManifest);
								} catch (IOException e) {
									JavaPlugin.log(e);
								} finally {
									if (inputStream != null) {
										try {
											inputStream.close();
										} catch(IOException e){
										}
									}
								}
							}
						}
					}
				}
				result= merge(ownManifest, otherManifests);
			} finally {
				for (Iterator<ZipFile> iter= openZips.iterator(); iter.hasNext(); ) {
					ZipFile file= iter.next();
					try {
						file.close();
					} catch (IOException e) {
						JavaPlugin.log(e);
					}
				}
			}
		} else {
			result= ownManifest;
		}
		return result;
	}

	private void setManifestClasspath(Manifest ownManifest, String manifestClasspath) {
		if ((manifestClasspath != null) && !manifestClasspath.trim().equals("")) { //$NON-NLS-1$
			Attributes mainAttr= ownManifest.getMainAttributes();
			mainAttr.putValue("Class-Path", manifestClasspath); //$NON-NLS-1$
		}
	}

	private Manifest merge(Manifest ownManifest, List<Manifest> otherManifests) {
		Manifest mergedManifest= new Manifest(ownManifest);
		Map<String, Attributes> mergedEntries= mergedManifest.getEntries();
		for (Iterator<Manifest> iter= otherManifests.iterator(); iter.hasNext();) {
			Manifest otherManifest= iter.next();
			Map<String, Attributes> otherEntries= otherManifest.getEntries();
			for (Iterator<String> iterator= otherEntries.keySet().iterator(); iterator.hasNext();) {
				String attributeName= iterator.next();
				if (mergedEntries.containsKey(attributeName)) {
					// TODO: WARNING
				} else {
					mergedEntries.put(attributeName, otherEntries.get(attributeName));
				}
			}
		}
		return mergedManifest;
	}

	private Manifest createOwn(JarPackageData jarPackage) throws CoreException {
		if (jarPackage.isManifestGenerated())
			return createGeneratedManifest(jarPackage);

		try {
			return createSuppliedManifest(jarPackage);
		} catch (IOException ex) {
			throw JarPackagerUtil.createCoreException(ex.getLocalizedMessage(), ex);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public Manifest createDefault(String manifestVersion) {
		Manifest manifest= new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, manifestVersion);
		return manifest;
	}

	/**
	 * Hook for subclasses to add additional manifest entries.
	 *
	 * @param	manifest	the manifest to which the entries should be added
	 * @param	jarPackage	the JAR package specification
	 */
	protected void putAdditionalEntries(Manifest manifest, JarPackageData jarPackage) {
	}

	private Manifest createGeneratedManifest(JarPackageData jarPackage) {
		Manifest manifest= new Manifest();
		putVersion(manifest, jarPackage);
		putSealing(manifest, jarPackage);
		putMainClass(manifest, jarPackage);
		putAdditionalEntries(manifest, jarPackage);
		return manifest;
	}

	private void putVersion(Manifest manifest, JarPackageData jarPackage) {
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, jarPackage.getManifestVersion());
	}

	private void putSealing(Manifest manifest, JarPackageData jarPackage) {
		if (jarPackage.isJarSealed()) {
			manifest.getMainAttributes().put(Attributes.Name.SEALED, SEALED_VALUE);
			IPackageFragment[] packages= jarPackage.getPackagesToUnseal();
			if (packages != null) {
				for (int i= 0; i < packages.length; i++) {
					Attributes attributes= new Attributes();
					attributes.put(Attributes.Name.SEALED, UNSEALED_VALUE);
					manifest.getEntries().put(getInManifestFormat(packages[i]), attributes);
				}
			}
		} else {
			IPackageFragment[] packages= jarPackage.getPackagesToSeal();
			if (packages != null)
				for (int i= 0; i < packages.length; i++) {
					Attributes attributes= new Attributes();
					attributes.put(Attributes.Name.SEALED, SEALED_VALUE);
					manifest.getEntries().put(getInManifestFormat(packages[i]), attributes);
				}
		}
	}

	private void putMainClass(Manifest manifest, JarPackageData jarPackage) {
		if (jarPackage.getManifestMainClass() != null && jarPackage.getManifestMainClass().getFullyQualifiedName().length() > 0)
			manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, jarPackage.getManifestMainClass().getFullyQualifiedName());
	}

	private String getInManifestFormat(IPackageFragment packageFragment) {
		String name= packageFragment.getElementName();
		return name.replace('.', '/') + '/';
	}

	private Manifest createSuppliedManifest(JarPackageData jarPackage) throws CoreException, IOException {
		Manifest manifest;
		// No need to use buffer here because Manifest(...) does
		InputStream stream= jarPackage.getManifestFile().getContents(false);
		try {
			manifest= new Manifest(stream);
		} finally {
			if (stream != null)
				stream.close();
		}
		return manifest;
	}

}

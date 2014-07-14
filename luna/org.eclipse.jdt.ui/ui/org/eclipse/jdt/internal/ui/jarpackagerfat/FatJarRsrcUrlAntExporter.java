/*******************************************************************************
 * Copyright (c) 2008, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 219530 [jar application] add Jar-in-Jar ClassLoader option
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 262766 [jar exporter] ANT file for Jar-in-Jar option contains relative path to jar-rsrc-loader.zip
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 262763 [jar exporter] remove Built-By attribute in ANT files from Fat JAR Exporter
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 262748 [jar exporter] extract constants for string literals in JarRsrcLoader et al.
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackagerfat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.eclipse.core.runtime.IPath;

import org.eclipse.debug.core.ILaunchConfiguration;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Create an ANT script for a runnable JAR with class loader export. The script is generated based
 * on the classpath of the selected launch-configuration.
 * 
 * @since 3.5
 */
public class FatJarRsrcUrlAntExporter extends FatJarAntExporter {

	public FatJarRsrcUrlAntExporter(IPath antScriptLocation, IPath jarLocation, ILaunchConfiguration launchConfiguration) {
		super(antScriptLocation, jarLocation, launchConfiguration);
	}

	@Override
	protected void buildANTScript(IPath antScriptLocation, String projectName, IPath absJarfile, String mainClass, SourceInfo[] sourceInfos) throws FileNotFoundException, IOException {
		File antScriptFile= antScriptLocation.toFile();
		buildANTScript(new FileOutputStream(antScriptFile), projectName, absJarfile, mainClass, sourceInfos);
		copyJarInJarLoader(new File(antScriptFile.getParentFile(), FatJarRsrcUrlBuilder.JAR_RSRC_LOADER_ZIP));
	}

	private void copyJarInJarLoader(File targetFile) throws IOException {
		InputStream is= JavaPlugin.getDefault().getBundle().getEntry(FatJarRsrcUrlBuilder.JAR_RSRC_LOADER_ZIP).openStream();
		OutputStream os= new FileOutputStream(targetFile);
		byte[] buf= new byte[1024];
		while (true) {
			int cnt= is.read(buf);
			if (cnt <= 0)
				break;
			os.write(buf, 0, cnt);
		}
		os.close();
		is.close();
	}

	protected void buildANTScript(OutputStream outputStream, String projectName, IPath absJarfile, String mainClass, SourceInfo[] sourceInfos) throws IOException {

		String absJarname= absJarfile.toString();

		DocumentBuilder docBuilder= null;
		DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
		factory.setValidating(false);
		try {
			docBuilder= factory.newDocumentBuilder();
		} catch (ParserConfigurationException ex) {
			throw new IOException(FatJarPackagerMessages.FatJarPackageAntScript_error_couldNotGetXmlBuilder);
		}
		Document document= docBuilder.newDocument();

		Node comment;

		// Create the document
		Element project= document.createElement("project"); //$NON-NLS-1$
		project.setAttribute("name", "Create Runnable Jar for Project " + projectName + " with Jar-in-Jar Loader"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		project.setAttribute("default", "create_run_jar"); //$NON-NLS-1$ //$NON-NLS-2$
		comment= document.createComment("this file was created by Eclipse Runnable JAR Export Wizard"); //$NON-NLS-1$
		project.appendChild(comment);
		comment= document.createComment("ANT 1.7 is required                                        "); //$NON-NLS-1$
		project.appendChild(comment);
		document.appendChild(project);

		Element target= document.createElement("target"); //$NON-NLS-1$
		target.setAttribute("name", "create_run_jar"); //$NON-NLS-1$ //$NON-NLS-2$
		project.appendChild(target);

		Element jar= document.createElement("jar"); //$NON-NLS-1$
		jar.setAttribute("destfile", absJarname); //$NON-NLS-1$s 
		target.appendChild(jar);

		Element manifest= document.createElement("manifest"); //$NON-NLS-1$
		jar.appendChild(manifest);

		Element attribute= document.createElement("attribute"); //$NON-NLS-1$
		attribute.setAttribute("name", "Main-Class"); //$NON-NLS-1$ //$NON-NLS-2$s 
		attribute.setAttribute("value", JIJConstants.LOADER_MAIN_CLASS); //$NON-NLS-1$ 
		manifest.appendChild(attribute);

		attribute= document.createElement("attribute"); //$NON-NLS-1$
		attribute.setAttribute("name", JIJConstants.REDIRECTED_MAIN_CLASS_MANIFEST_NAME); //$NON-NLS-1$ 
		attribute.setAttribute("value", mainClass); //$NON-NLS-1$ 
		manifest.appendChild(attribute);

		attribute= document.createElement("attribute"); //$NON-NLS-1$
		attribute.setAttribute("name", "Class-Path"); //$NON-NLS-1$ //$NON-NLS-2$s 
		attribute.setAttribute("value", "."); //$NON-NLS-1$ //$NON-NLS-2$ 
		manifest.appendChild(attribute);

		attribute= document.createElement("attribute"); //$NON-NLS-1$
		attribute.setAttribute("name", JIJConstants.REDIRECTED_CLASS_PATH_MANIFEST_NAME); //$NON-NLS-1$ 
		StringBuffer rsrcClassPath= new StringBuffer();
		rsrcClassPath.append(JIJConstants.CURRENT_DIR); 
		for (int i= 0; i < sourceInfos.length; i++) {
			SourceInfo sourceInfo= sourceInfos[i];
			if (sourceInfo.isJar) {
				rsrcClassPath.append(" ") //$NON-NLS-1$
						.append(new File(sourceInfo.absPath).getName());
			}
		}
		attribute.setAttribute("value", rsrcClassPath.toString()); //$NON-NLS-1$  
		manifest.appendChild(attribute);

		Element zipfileset= document.createElement("zipfileset"); //$NON-NLS-1$
		zipfileset.setAttribute("src", FatJarRsrcUrlBuilder.JAR_RSRC_LOADER_ZIP); //$NON-NLS-1$ 
		jar.appendChild(zipfileset);
		
		for (int i= 0; i < sourceInfos.length; i++) {
			SourceInfo sourceInfo= sourceInfos[i];
			if (sourceInfo.isJar) {
				File jarFile= new File(sourceInfo.absPath);
				Element fileset= document.createElement("zipfileset"); //$NON-NLS-1$
				fileset.setAttribute("dir", jarFile.getParent()); //$NON-NLS-1$
				fileset.setAttribute("includes", jarFile.getName()); //$NON-NLS-1$ 
				jar.appendChild(fileset);
			} else {
				Element fileset= document.createElement("fileset"); //$NON-NLS-1$
				fileset.setAttribute("dir", sourceInfo.absPath); //$NON-NLS-1$
				jar.appendChild(fileset);
			}
		}

		try {
			// Write the document to the stream
			Transformer transformer= TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.METHOD, "xml"); //$NON-NLS-1$
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8"); //$NON-NLS-1$
			transformer.setOutputProperty(OutputKeys.INDENT, "yes"); //$NON-NLS-1$
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4"); //$NON-NLS-1$ //$NON-NLS-2$
			DOMSource source= new DOMSource(document);
			StreamResult result= new StreamResult(outputStream);
			transformer.transform(source, result);
		} catch (TransformerException e) {
			throw new IOException(FatJarPackagerMessages.FatJarPackageAntScript_error_couldNotTransformToXML);
		}
	}

}

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
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackagerfat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

/**
 * Create an ANT script for a runnable JAR wit libraries in a sub-folder. The script is generated
 * based on the classpath of the selected launch-configuration.
 * 
 * @since 3.5
 */
public class UnpackJarAntExporter extends FatJarAntExporter {

	public UnpackJarAntExporter(IPath antScriptLocation, IPath jarLocation, ILaunchConfiguration launchConfiguration) {
		super(antScriptLocation, jarLocation, launchConfiguration);
	}

	@Override
	protected void buildANTScript(IPath antScriptLocation, String projectName, IPath absJarfile, String mainClass, SourceInfo[] sourceInfos) throws IOException {
		OutputStream outputStream= null;
		try {
			outputStream= new FileOutputStream(antScriptLocation.toFile());
			String absJarname= absJarfile.toString();
			String subfolder= absJarfile.removeFileExtension().lastSegment() + "_lib"; //$NON-NLS-1$
			String absSubfolder= absJarfile.removeLastSegments(1).append(subfolder).toString();

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
			project.setAttribute("name", "Create Runnable Jar for Project " + projectName + " with libraries in sub-folder"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
			attribute.setAttribute("value", mainClass); //$NON-NLS-1$
			manifest.appendChild(attribute);

			attribute= document.createElement("attribute"); //$NON-NLS-1$
			attribute.setAttribute("name", "Class-Path"); //$NON-NLS-1$ //$NON-NLS-2$s
			StringBuffer classPath= new StringBuffer();
			classPath.append("."); //$NON-NLS-1$
			for (int i= 0; i < sourceInfos.length; i++) {
				SourceInfo sourceInfo= sourceInfos[i];
				if (sourceInfo.isJar) {
					classPath.append(" ").append(subfolder).append("/") //$NON-NLS-1$ //$NON-NLS-2$
							.append(new File(sourceInfo.absPath).getName());
				}
			}
			attribute.setAttribute("value", classPath.toString()); //$NON-NLS-1$
			manifest.appendChild(attribute);

			// add folders
			for (int i= 0; i < sourceInfos.length; i++) {
				SourceInfo sourceInfo= sourceInfos[i];
				if (!sourceInfo.isJar) {
					Element fileset= document.createElement("fileset"); //$NON-NLS-1$
					fileset.setAttribute("dir", sourceInfo.absPath); //$NON-NLS-1$
					jar.appendChild(fileset);
				}
			}


			Element delete= document.createElement("delete"); //$NON-NLS-1$
			delete.setAttribute("dir", absSubfolder); //$NON-NLS-1$s
			target.appendChild(delete);

			Element mkdir= document.createElement("mkdir"); //$NON-NLS-1$
			mkdir.setAttribute("dir", absSubfolder); //$NON-NLS-1$s
			target.appendChild(mkdir);

			// add libraries
			for (int i= 0; i < sourceInfos.length; i++) {
				SourceInfo sourceInfo= sourceInfos[i];
				if (sourceInfo.isJar) {
					Element copy= document.createElement("copy"); //$NON-NLS-1$
					copy.setAttribute("file", sourceInfo.absPath); //$NON-NLS-1$
					copy.setAttribute("todir", absSubfolder); //$NON-NLS-1$
					target.appendChild(copy);
				}
			}

			// add folders
			for (int i= 0; i < sourceInfos.length; i++) {
				SourceInfo sourceInfo= sourceInfos[i];
				if (!sourceInfo.isJar) {
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
		} finally {
			if (outputStream != null) {
				outputStream.close();
			}
		}
	}

}

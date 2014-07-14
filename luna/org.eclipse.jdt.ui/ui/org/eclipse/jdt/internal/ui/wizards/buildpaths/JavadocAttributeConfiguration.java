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

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.javadoc.JavaDocLocations;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.wizards.BuildPathDialogAccess;
import org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration;

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;

public class JavadocAttributeConfiguration extends ClasspathAttributeConfiguration {

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration#getImageDescriptor(org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration.ClasspathAttributeAccess)
	 */
	@Override
	public ImageDescriptor getImageDescriptor(ClasspathAttributeAccess attribute) {
		return JavaPluginImages.DESC_OBJS_JAVADOC_LOCATION_ATTRIB;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration#getNameLabel(org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration.ClasspathAttributeAccess)
	 */
	@Override
	public String getNameLabel(ClasspathAttributeAccess attribute) {
		return NewWizardMessages.CPListLabelProvider_javadoc_location_label;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration#getValueLabel(org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration.ClasspathAttributeAccess)
	 */
	@Override
	public String getValueLabel(ClasspathAttributeAccess access) {
		String arg= null;
		String str= access.getClasspathAttribute().getValue();
		if (str != null) {
			String prefix= JavaDocLocations.ARCHIVE_PREFIX;
			if (str.startsWith(prefix)) {
				int sepIndex= str.lastIndexOf("!/"); //$NON-NLS-1$
				if (sepIndex == -1) {
					arg= str.substring(prefix.length());
				} else {
					String archive= str.substring(prefix.length(), sepIndex);
					String root= str.substring(sepIndex + 2);
					if (root.length() > 0) {
						arg= Messages.format(NewWizardMessages.CPListLabelProvider_twopart, new String[] { BasicElementLabels.getURLPart(archive), BasicElementLabels.getURLPart(root) });
					} else {
						arg= BasicElementLabels.getURLPart(archive);
					}
				}
			} else {
				arg= BasicElementLabels.getURLPart(str);
			}
		} else {
			arg= NewWizardMessages.CPListLabelProvider_none;
		}
		return arg;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration#performEdit(org.eclipse.swt.widgets.Shell, org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration.ClasspathAttributeAccess)
	 */
	@Override
	public IClasspathAttribute performEdit(Shell shell, ClasspathAttributeAccess attribute) {
		String initialLocation= attribute.getClasspathAttribute().getValue();
		String elementName= attribute.getParentClasspassEntry().getPath().lastSegment();
		try {
			URL locationURL= initialLocation != null ? new URL(initialLocation) : null;
			URL[] result= BuildPathDialogAccess.configureJavadocLocation(shell, elementName, locationURL);
			if (result != null) {
				URL newURL= result[0];
				String string= newURL != null ? newURL.toExternalForm() : null;
				return JavaCore.newClasspathAttribute(IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME, string);
			}
		} catch (MalformedURLException e) {
			// todo
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration#performRemove(org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration.ClasspathAttributeAccess)
	 */
	@Override
	public IClasspathAttribute performRemove(ClasspathAttributeAccess attribute) {
		return JavaCore.newClasspathAttribute(IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME, null);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration#canEdit(org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration.ClasspathAttributeAccess)
	 */
	@Override
	public boolean canEdit(ClasspathAttributeAccess attribute) {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration#canRemove(org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration.ClasspathAttributeAccess)
	 */
	@Override
	public boolean canRemove(ClasspathAttributeAccess attribute) {
		return attribute.getClasspathAttribute().getValue() != null;
	}



}

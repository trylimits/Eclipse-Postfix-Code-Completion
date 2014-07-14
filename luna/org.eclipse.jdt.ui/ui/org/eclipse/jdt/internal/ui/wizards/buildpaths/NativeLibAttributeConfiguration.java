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

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.window.Window;

import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration;

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;


public class NativeLibAttributeConfiguration extends ClasspathAttributeConfiguration {

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration#getImageDescriptor(org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration.ClasspathAttributeAccess)
	 */
	@Override
	public ImageDescriptor getImageDescriptor(ClasspathAttributeAccess attribute) {
		return JavaPluginImages.DESC_OBJS_NATIVE_LIB_PATH_ATTRIB;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration#getNameLabel(org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration.ClasspathAttributeAccess)
	 */
	@Override
	public String getNameLabel(ClasspathAttributeAccess attribute) {
		return NewWizardMessages.CPListLabelProvider_native_library_path;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration#getValueLabel(org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration.ClasspathAttributeAccess)
	 */
	@Override
	public String getValueLabel(ClasspathAttributeAccess attribute) {
		String arg= attribute.getClasspathAttribute().getValue();
		if (arg == null) {
			arg= NewWizardMessages.CPListLabelProvider_none;
		}
		return arg;
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

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration#performEdit(org.eclipse.swt.widgets.Shell, org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration.ClasspathAttributeAccess)
	 */
	@Override
	public IClasspathAttribute performEdit(Shell shell, ClasspathAttributeAccess attribute) {
		NativeLibrariesDialog dialog= new NativeLibrariesDialog(shell, attribute.getClasspathAttribute().getValue(), attribute.getParentClasspassEntry());
		if (dialog.open() == Window.OK) {
			return JavaCore.newClasspathAttribute(JavaRuntime.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY, dialog.getNativeLibraryPath());
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration#performRemove(org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration.ClasspathAttributeAccess)
	 */
	@Override
	public IClasspathAttribute performRemove(ClasspathAttributeAccess attribute) {
		return JavaCore.newClasspathAttribute(JavaRuntime.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY, null);
	}

}

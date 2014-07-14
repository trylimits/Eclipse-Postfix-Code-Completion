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

import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import org.eclipse.ui.activities.WorkbenchActivityHelper;

import org.eclipse.jdt.core.IClasspathEntry;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.wizards.IClasspathContainerPage;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.CoreUtility;

/**
  */
public class ClasspathContainerDescriptor {

	private IConfigurationElement fConfigElement;
	private IClasspathContainerPage fPage;

	private static final String ATT_EXTENSION = "classpathContainerPage"; //$NON-NLS-1$

	private static final String ATT_ID = "id"; //$NON-NLS-1$
	private static final String ATT_NAME = "name"; //$NON-NLS-1$
	private static final String ATT_PAGE_CLASS = "class"; //$NON-NLS-1$

	public ClasspathContainerDescriptor(IConfigurationElement configElement) throws CoreException {
		super();
		fConfigElement = configElement;
		fPage= null;

		String id = fConfigElement.getAttribute(ATT_ID);
		String name = configElement.getAttribute(ATT_NAME);
		String pageClassName = configElement.getAttribute(ATT_PAGE_CLASS);

		if (name == null) {
			throw new CoreException(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, 0, "Invalid extension (missing name): " + id, null)); //$NON-NLS-1$
		}
		if (pageClassName == null) {
			throw new CoreException(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, 0, "Invalid extension (missing page class name): " + id, null)); //$NON-NLS-1$
		}
	}

	public IClasspathContainerPage createPage() throws CoreException  {
		if (fPage == null) {
			Object elem= CoreUtility.createExtension(fConfigElement, ATT_PAGE_CLASS);
			if (elem instanceof IClasspathContainerPage) {
				fPage= (IClasspathContainerPage) elem;
			} else {
				String id= fConfigElement.getAttribute(ATT_ID);
				throw new CoreException(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, 0, "Invalid extension (page not of type IClasspathContainerPage): " + id, null)); //$NON-NLS-1$
			}
		}
		return fPage;
	}

	public IClasspathContainerPage getPage() {
		return fPage;
	}

	public void setPage(IClasspathContainerPage page) {
		fPage= page;
	}

	public void dispose() {
		if (fPage != null) {
			fPage.dispose();
			fPage= null;
		}
	}

	public String getName() {
		return fConfigElement.getAttribute(ATT_NAME);
	}

	public String getPageClass() {
		return fConfigElement.getAttribute(ATT_PAGE_CLASS);
	}

	public boolean canEdit(IClasspathEntry entry) {
		String id = fConfigElement.getAttribute(ATT_ID);
		if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
			String type = entry.getPath().segment(0);
			return id.equals(type);
		}
		return false;
	}

	public static ClasspathContainerDescriptor[] getDescriptors() {
		ArrayList<ClasspathContainerDescriptor> containers= new ArrayList<ClasspathContainerDescriptor>();

		IExtensionPoint extensionPoint = Platform.getExtensionRegistry().getExtensionPoint(JavaUI.ID_PLUGIN, ATT_EXTENSION);
		if (extensionPoint != null) {
			ClasspathContainerDescriptor defaultPage= null;
			String defaultPageName= ClasspathContainerDefaultPage.class.getName();

			IConfigurationElement[] elements = extensionPoint.getConfigurationElements();
			for (int i = 0; i < elements.length; i++) {
				try {
					ClasspathContainerDescriptor curr= new ClasspathContainerDescriptor(elements[i]);
					if (!WorkbenchActivityHelper.filterItem(curr)) {
						if (defaultPageName.equals(curr.getPageClass())) {
							defaultPage= curr;
						} else {
							containers.add(curr);
						}
					}
				} catch (CoreException e) {
					JavaPlugin.log(e);
				}
			}
			if (defaultPageName != null && containers.isEmpty()) {
				// default page only added of no other extensions found
				containers.add(defaultPage);
			}
		}
		return containers.toArray(new ClasspathContainerDescriptor[containers.size()]);
	}

}

/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt McCutchen - Bug 148313 [build path] "Configure Build Path" incorrectly appears for non-Java projects
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage;

import java.util.HashMap;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.dialogs.PreferencesUtil;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.packageview.ClassPathContainer;
import org.eclipse.jdt.internal.ui.packageview.PackageFragmentRootContainer;
import org.eclipse.jdt.internal.ui.preferences.BuildPathsPropertyPage;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;

//SelectedElements iff enabled: (IJavaElement || ClassPathContainer || IAdaptable) && size == 1
public class ConfigureBuildPathAction extends BuildpathModifierAction {

	public ConfigureBuildPathAction(IWorkbenchSite site) {
		super(site, null, BuildpathModifierAction.CONFIGURE_BUILD_PATH);

		setText(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_ConfigureBP_label);
		setImageDescriptor(JavaPluginImages.DESC_ELCL_CONFIGURE_BUILDPATH);
		setToolTipText(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_ConfigureBP_tooltip);
		setDisabledImageDescriptor(JavaPluginImages.DESC_DLCL_CONFIGURE_BUILDPATH);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getDetailedDescription() {
	    return null;
	}

	@Override
	public void run() {
		IProject project= null;
		Object firstElement= getSelectedElements().get(0);
		HashMap<Object, IClasspathEntry> data= new HashMap<Object, IClasspathEntry>();

		if (firstElement instanceof IJavaElement) {
			IJavaElement element= (IJavaElement) firstElement;
			IPackageFragmentRoot root= (IPackageFragmentRoot) element.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			if (root != null) {
				try {
					data.put(BuildPathsPropertyPage.DATA_REVEAL_ENTRY, root.getRawClasspathEntry());
				} catch (JavaModelException e) {
					// ignore
				}
			}
			project= element.getJavaProject().getProject();
		} else if (firstElement instanceof PackageFragmentRootContainer) {
			PackageFragmentRootContainer container= (PackageFragmentRootContainer) firstElement;
			project= container.getJavaProject().getProject();
			IClasspathEntry entry= container instanceof ClassPathContainer ? ((ClassPathContainer) container).getClasspathEntry() : JavaCore.newLibraryEntry(new Path("/x/y"), null, null); //$NON-NLS-1$
			data.put(BuildPathsPropertyPage.DATA_REVEAL_ENTRY, entry);
		} else {
			project= ((IResource) ((IAdaptable) firstElement).getAdapter(IResource.class)).getProject();
		}
		PreferencesUtil.createPropertyDialogOn(getShell(), project, BuildPathsPropertyPage.PROP_ID, null, data).open();
	}

	@Override
	protected boolean canHandle(IStructuredSelection elements) {
		if (elements.size() != 1)
			return false;

		Object firstElement= elements.getFirstElement();

		if (firstElement instanceof IJavaElement) {
			IJavaElement element= (IJavaElement) firstElement;
			IPackageFragmentRoot root= JavaModelUtil.getPackageFragmentRoot(element);
			if (root != null && root != element && (root.isArchive() || root.isExternal())) {
				return false;
			}
			IJavaProject project= element.getJavaProject();
			if (project == null)
				return false;

			return project.getProject() != null;
		} else if (firstElement instanceof PackageFragmentRootContainer) {
			return true;
		} else if (firstElement instanceof IAdaptable) {
			IResource res= (IResource) ((IAdaptable) firstElement).getAdapter(IResource.class);
			if (res == null)
				return false;

			IProject project = res.getProject();
			if (project == null || !project.isOpen())
				return false;

			try {
				return project.hasNature(JavaCore.NATURE_ID);
			} catch (CoreException e) {
				return false;
			}
		}
		return false;
	}

}

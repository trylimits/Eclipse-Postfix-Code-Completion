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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IMarker;

import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;

import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolution2;
import org.eclipse.ui.IMarkerResolutionGenerator2;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;

import org.eclipse.jdt.core.CorrectionEngine;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.wizards.BuildPathDialogAccess;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.preferences.BuildPathsPropertyPage;
import org.eclipse.jdt.internal.ui.preferences.UserLibraryPreferencePage;
import org.eclipse.jdt.internal.ui.text.correction.CorrectionMessages;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;


public class UserLibraryMarkerResolutionGenerator implements IMarkerResolutionGenerator2 {

	private final static IMarkerResolution[] NO_RESOLUTION = new IMarkerResolution[0];

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IMarkerResolutionGenerator2#hasResolutions(org.eclipse.core.resources.IMarker)
	 */
	public boolean hasResolutions(IMarker marker) {
		int id= marker.getAttribute(IJavaModelMarker.ID, -1);
		if (id == IJavaModelStatusConstants.CP_CONTAINER_PATH_UNBOUND
				|| id == IJavaModelStatusConstants.CP_VARIABLE_PATH_UNBOUND
				|| id == IJavaModelStatusConstants.INVALID_CP_CONTAINER_ENTRY
				|| id == IJavaModelStatusConstants.DEPRECATED_VARIABLE
				|| id == IJavaModelStatusConstants.INVALID_CLASSPATH) {
			return true;
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IMarkerResolutionGenerator#getResolutions(org.eclipse.core.resources.IMarker)
	 */
	public IMarkerResolution[] getResolutions(IMarker marker) {
		final Shell shell= JavaPlugin.getActiveWorkbenchShell();
		if (!hasResolutions(marker) || shell == null) {
			return NO_RESOLUTION;
		}

		ArrayList<IMarkerResolution2> resolutions= new ArrayList<IMarkerResolution2>();

		final IJavaProject project= getJavaProject(marker);

		int id= marker.getAttribute(IJavaModelMarker.ID, -1);
		if (id == IJavaModelStatusConstants.CP_CONTAINER_PATH_UNBOUND) {
			String[] arguments= CorrectionEngine.getProblemArguments(marker);
			final IPath path= new Path(arguments[0]);

			if (path.segment(0).equals(JavaCore.USER_LIBRARY_CONTAINER_ID)) {
				String label= NewWizardMessages.UserLibraryMarkerResolutionGenerator_changetouserlib_label;
				Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
				resolutions.add(new UserLibraryMarkerResolution(label, image) {
					public void run(IMarker m) {
						changeToExistingLibrary(shell, path, false, project);
					}
				});
				if (path.segmentCount() == 2) {
					String label2= Messages.format(NewWizardMessages.UserLibraryMarkerResolutionGenerator_createuserlib_label, path.segment(1));
					Image image2= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_LIBRARY);
					resolutions.add(new UserLibraryMarkerResolution(label2, image2) {
						public void run(IMarker m) {
							createUserLibrary(shell, path);
						}
					});
				}
			}
			String label= NewWizardMessages.UserLibraryMarkerResolutionGenerator_changetoother;
			Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
			resolutions.add(new UserLibraryMarkerResolution(label, image) {
				public void run(IMarker m) {
					changeToExistingLibrary(shell, path, true, project);
				}
			});
		}

		if (project != null) {
			resolutions.add(new OpenBuildPathMarkerResolution(project));
		}

		return resolutions.toArray(new IMarkerResolution[resolutions.size()]);
	}

	protected void changeToExistingLibrary(Shell shell, IPath path, boolean isNew, final IJavaProject project) {
		try {
			IClasspathEntry[] entries= project.getRawClasspath();
			int idx= indexOfClasspath(entries, path);
			if (idx == -1) {
				return;
			}
			IClasspathEntry[] res;
			if (isNew) {
				res= BuildPathDialogAccess.chooseContainerEntries(shell, project, entries);
				if (res == null) {
					return;
				}
			} else {
				IClasspathEntry resEntry= BuildPathDialogAccess.configureContainerEntry(shell, entries[idx], project, entries);
				if (resEntry == null) {
					return;
				}
				res= new IClasspathEntry[] { resEntry };
			}
			final IClasspathEntry[] newEntries= new IClasspathEntry[entries.length - 1 + res.length];
			System.arraycopy(entries, 0, newEntries, 0, idx);
			System.arraycopy(res, 0, newEntries, idx, res.length);
			System.arraycopy(entries, idx + 1, newEntries, idx + res.length, entries.length - idx - 1);

			IRunnableContext context= JavaPlugin.getActiveWorkbenchWindow();
			if (context == null) {
				context= PlatformUI.getWorkbench().getProgressService();
			}
			context.run(true, true, new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					try {
						project.setRawClasspath(newEntries, project.getOutputLocation(), monitor);
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					}
				}
			});
		} catch (JavaModelException e) {
			String title= NewWizardMessages.UserLibraryMarkerResolutionGenerator_error_title;
			String message= NewWizardMessages.UserLibraryMarkerResolutionGenerator_error_creationfailed_message;
			ExceptionHandler.handle(e, shell, title, message);
		} catch (InvocationTargetException e) {
			String title= NewWizardMessages.UserLibraryMarkerResolutionGenerator_error_title;
			String message= NewWizardMessages.UserLibraryMarkerResolutionGenerator_error_applyingfailed_message;
			ExceptionHandler.handle(e, shell, title, message);
		} catch (InterruptedException e) {
			// user cancelled
		}
	}

	private int indexOfClasspath(IClasspathEntry[] entries, IPath path) {
		for (int i= 0; i < entries.length; i++) {
			IClasspathEntry curr= entries[i];
			if (curr.getEntryKind() == IClasspathEntry.CPE_CONTAINER && curr.getPath().equals(path)) {
				return i;
			}
		}
		return -1;
	}

	protected void createUserLibrary(final Shell shell, IPath unboundPath) {
		String name= unboundPath.segment(1);
		String id= UserLibraryPreferencePage.ID;
		HashMap<String, Object> data= new HashMap<String, Object>(3);
		data.put(UserLibraryPreferencePage.DATA_LIBRARY_TO_SELECT, name);
		data.put(UserLibraryPreferencePage.DATA_DO_CREATE, Boolean.TRUE);
		PreferencesUtil.createPreferenceDialogOn(shell, id, new String[] { id }, data).open();
	}

	private IJavaProject getJavaProject(IMarker marker) {
		return JavaCore.create(marker.getResource().getProject());
	}

	/**
	 * Library quick fix base class
	 */
	private static abstract class UserLibraryMarkerResolution implements IMarkerResolution2 {

		private final String fLabel;
		private final Image fImage;

		public UserLibraryMarkerResolution(String label, Image image) {
			fLabel= label;
			fImage= image;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.ui.IMarkerResolution#getLabel()
		 */
		public String getLabel() {
			return fLabel;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.ui.IMarkerResolution2#getDescription()
		 */
		public String getDescription() {
			return null;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.ui.IMarkerResolution2#getImage()
		 */
		public Image getImage() {
			return fImage;
		}
	}

	private static class OpenBuildPathMarkerResolution implements IMarkerResolution2 {
		private final IJavaProject fProject;

		public OpenBuildPathMarkerResolution(IJavaProject project) {
			fProject= project;
		}

		public String getDescription() {
			return Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_configure_buildpath_description, BasicElementLabels.getResourceName(fProject.getElementName()));
		}

		public Image getImage() {
			return JavaPluginImages.get(JavaPluginImages.IMG_OBJS_ACCESSRULES_ATTRIB);
		}

		public String getLabel() {
			return CorrectionMessages.ReorgCorrectionsSubProcessor_configure_buildpath_label;
		}

		public void run(IMarker marker) {
			PreferencesUtil.createPropertyDialogOn(JavaPlugin.getActiveWorkbenchShell(), fProject, BuildPathsPropertyPage.PROP_ID, null, null).open();
		}
	}

}

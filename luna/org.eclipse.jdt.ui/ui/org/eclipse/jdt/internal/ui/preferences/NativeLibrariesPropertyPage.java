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
package org.eclipse.jdt.internal.ui.preferences;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.operation.IRunnableWithProgress;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PropertyPage;

import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.ArchiveFileFilter;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathSupport;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;

public class NativeLibrariesPropertyPage extends PropertyPage implements IStatusChangeListener {

	private NativeLibrariesConfigurationBlock fConfigurationBlock;
	private boolean fIsValidElement;
	private boolean fIsReadOnly;
	private IClasspathEntry fEntry;
	private IPath fContainerPath;
	private String fInitialNativeLibPath;

	/**
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public void createControl(Composite parent) {
		IJavaElement elem= getJavaElement();
		try {
			if (elem instanceof IPackageFragmentRoot) {
				IPackageFragmentRoot root= (IPackageFragmentRoot) elem;

				IClasspathEntry entry= JavaModelUtil.getClasspathEntry(root);
				if (entry == null) {
					fIsValidElement= false;
					setDescription(PreferencesMessages.NativeLibrariesPropertyPage_invalidElementSelection_desription);
				} else {
					if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
						fContainerPath= entry.getPath();
						fEntry= handleContainerEntry(fContainerPath, elem.getJavaProject(), root.getPath());
						fIsValidElement= fEntry != null && !fIsReadOnly;
					} else {
						fContainerPath= null;
						fEntry= entry;
						fIsValidElement= true;
					}
				}
			} else {
				fIsValidElement= false;
				setDescription(PreferencesMessages.NativeLibrariesPropertyPage_invalidElementSelection_desription);
			}
		} catch (JavaModelException e) {
			fIsValidElement= false;
			setDescription(PreferencesMessages.NativeLibrariesPropertyPage_invalidElementSelection_desription);
		}
		super.createControl(parent);
	}

	private IClasspathEntry handleContainerEntry(IPath containerPath, IJavaProject jproject, IPath jarPath) throws JavaModelException {
		ClasspathContainerInitializer initializer= JavaCore.getClasspathContainerInitializer(containerPath.segment(0));
		IClasspathContainer container= JavaCore.getClasspathContainer(containerPath, jproject);
		if (initializer == null || container == null) {
			setDescription(Messages.format(PreferencesMessages.NativeLibrariesPropertyPage_invalid_container, BasicElementLabels.getPathLabel(containerPath, false)));
			return null;
		}
		String containerName= container.getDescription();
		IStatus status= initializer.getAttributeStatus(containerPath, jproject, JavaRuntime.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY);
		if (status.getCode() == ClasspathContainerInitializer.ATTRIBUTE_NOT_SUPPORTED) {
			setDescription(Messages.format(PreferencesMessages.NativeLibrariesPropertyPage_not_supported, containerName));
			return null;
		}
		IClasspathEntry entry= JavaModelUtil.findEntryInContainer(container, jarPath);
		if (status.getCode() == ClasspathContainerInitializer.ATTRIBUTE_READ_ONLY) {
			setDescription(Messages.format(PreferencesMessages.NativeLibrariesPropertyPage_read_only, containerName));
			fIsReadOnly= true;
			return entry;
		}
		Assert.isNotNull(entry);
		return entry;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Control createContents(Composite parent) {
		if (!fIsValidElement || fIsReadOnly) {
			Composite inner= new Composite(parent, SWT.NONE);
			
			if (fIsReadOnly) {
				GridLayout layout= new GridLayout();
				layout.marginWidth= 0;
				inner.setLayout(layout);

				Label label= new Label(inner, SWT.WRAP);
				label.setText(PreferencesMessages.NativeLibrariesPropertyPage_location_path);
				
				Text location= new Text(inner, SWT.READ_ONLY | SWT.WRAP);
				SWTUtil.fixReadonlyTextBackground(location);
				GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
				gd.widthHint= convertWidthInCharsToPixels(80);
				location.setLayoutData(gd);
				String locationPath= PreferencesMessages.NativeLibrariesPropertyPage_locationPath_none;
				if (fEntry != null) {
					String nativeLibrariesPath= getNativeLibrariesPath(fEntry);
					if (nativeLibrariesPath != null)
						locationPath= nativeLibrariesPath;
				}
				location.setText(locationPath);
				Dialog.applyDialogFont(inner);
			}
			return inner;
		}

		IJavaElement elem= getJavaElement();
		if (elem == null)
			return new Composite(parent, SWT.NONE);

		fInitialNativeLibPath= getNativeLibrariesPath(fEntry);
		fConfigurationBlock= new NativeLibrariesConfigurationBlock(this, getShell(), fInitialNativeLibPath, fEntry);
		Control control= fConfigurationBlock.createContents(parent);

		Dialog.applyDialogFont(control);
		return control;
	}

	private static String getNativeLibrariesPath(IClasspathEntry entry) {
		IClasspathAttribute[] extraAttributes= entry.getExtraAttributes();
		for (int i= 0; i < extraAttributes.length; i++) {
			if (extraAttributes[i].getName().equals(JavaRuntime.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY)) {
				return extraAttributes[i].getValue();
			}
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public void statusChanged(IStatus status) {
		setValid(!status.matches(IStatus.ERROR));
		StatusUtil.applyToStatusLine(this, status);
	}

	/*
	 * @see PreferencePage#performDefaults()
	 */
	@Override
	protected void performDefaults() {
		if (fConfigurationBlock != null) {
			fConfigurationBlock.performDefaults();
		}
		super.performDefaults();
	}

	/**
	 * @see org.eclipse.jface.preference.IPreferencePage#performOk()
	 */
	@Override
	public boolean performOk() {
		if (fConfigurationBlock != null) {
			String nativeLibraryPath= fConfigurationBlock.getNativeLibraryPath();
			if (nativeLibraryPath == null && fInitialNativeLibPath == null
					|| nativeLibraryPath != null && fInitialNativeLibPath != null && nativeLibraryPath.equals(fInitialNativeLibPath)) {
				return true; //no change
			}

			IJavaElement elem= getJavaElement();
			try {
				IRunnableWithProgress runnable= getRunnable(getShell(), elem, nativeLibraryPath, fEntry, fContainerPath, fEntry.getReferencingEntry() != null);
				PlatformUI.getWorkbench().getProgressService().run(true, true, runnable);
			} catch (InvocationTargetException e) {
				String title= PreferencesMessages.NativeLibrariesPropertyPage_errorAttaching_title;
				String message= PreferencesMessages.NativeLibrariesPropertyPage_errorAttaching_message;
				ExceptionHandler.handle(e, getShell(), title, message);
				return false;
			} catch (InterruptedException e) {
				// Canceled
				return false;
			}
		}
		return true;
	}

	private static IRunnableWithProgress getRunnable(final Shell shell, final IJavaElement elem, final String nativeLibraryPath, final IClasspathEntry entry, final IPath containerPath, final boolean isReferencedEntry) {
		return new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				try {
					IJavaProject project= elem.getJavaProject();
					if (elem instanceof IPackageFragmentRoot) {
						CPListElement cpElem= CPListElement.createFromExisting(entry, project);
						cpElem.setAttribute(CPListElement.NATIVE_LIB_PATH, nativeLibraryPath);
						IClasspathEntry newEntry= cpElem.getClasspathEntry();
						String[] changedAttributes= { CPListElement.NATIVE_LIB_PATH };
						BuildPathSupport.modifyClasspathEntry(shell, newEntry, changedAttributes, project, containerPath, isReferencedEntry,  monitor);
					}
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				}
			}
		};
	}

	private IJavaElement getJavaElement() {
		IAdaptable adaptable= getElement();
		IJavaElement elem= (IJavaElement) adaptable.getAdapter(IJavaElement.class);
		if (elem == null) {

			IResource resource= (IResource) adaptable.getAdapter(IResource.class);
			//special case when the .jar is a file
			try {
				if (resource instanceof IFile && ArchiveFileFilter.isArchivePath(resource.getFullPath(), false)) {
					IProject proj= resource.getProject();
					if (proj.hasNature(JavaCore.NATURE_ID)) {
						IJavaProject jproject= JavaCore.create(proj);
						elem= jproject.getPackageFragmentRoot(resource); // create a handle
					}
				}
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		}
		return elem;
	}

}

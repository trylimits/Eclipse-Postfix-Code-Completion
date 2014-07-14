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
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.wizards.NewElementWizard;

public abstract class BuildPathWizard extends NewElementWizard {

	private boolean fDoFlushChange;
	private final CPListElement fEntryToEdit;
	private IPackageFragmentRoot fPackageFragmentRoot;
	private IPath fOutputLocation;
	private final ArrayList<CPListElement> fExistingEntries;

	public BuildPathWizard(CPListElement[] existingEntries, CPListElement newEntry, IPath outputLocation, String titel, ImageDescriptor image) {
		fOutputLocation= outputLocation;
		if (image != null)
			setDefaultPageImageDescriptor(image);

		setDialogSettings(JavaPlugin.getDefault().getDialogSettings());
		setWindowTitle(titel);

		fEntryToEdit= newEntry;
		fExistingEntries= new ArrayList<CPListElement>(Arrays.asList(existingEntries));
		fDoFlushChange= true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void finishPage(IProgressMonitor monitor) throws InterruptedException, CoreException {
		if (fDoFlushChange) {
			IJavaProject javaProject= getEntryToEdit().getJavaProject();

			BuildPathsBlock.flush(getExistingEntries(), getOutputLocation(), javaProject, null, monitor);

			IProject project= javaProject.getProject();
			IPath path= getEntryToEdit().getPath();

			IResource folder= project.getWorkspace().getRoot().findMember(path);
			fPackageFragmentRoot= javaProject.getPackageFragmentRoot(folder);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IJavaElement getCreatedElement() {
		return fPackageFragmentRoot;
	}

	public void setDoFlushChange(boolean b) {
		fDoFlushChange= b;
	}

	public ArrayList<CPListElement> getExistingEntries() {
		return fExistingEntries;
	}

	public IPath getOutputLocation() {
		return fOutputLocation;
	}

	protected void setOutputLocation(IPath outputLocation) {
		fOutputLocation= outputLocation;
	}

	protected CPListElement getEntryToEdit() {
		return fEntryToEdit;
	}

	public List<CPListElement> getInsertedElements() {
		return new ArrayList<CPListElement>();
	}

	public List<CPListElement> getRemovedElements() {
		return new ArrayList<CPListElement>();
	}

	public List<CPListElement> getModifiedElements() {
		ArrayList<CPListElement> result= new ArrayList<CPListElement>(1);
		result.add(fEntryToEdit);
		return result;
	}

	public abstract void cancel();

}

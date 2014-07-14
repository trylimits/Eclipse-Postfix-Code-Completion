/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.rename;

import java.net.URI;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameArguments;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor;

import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringDescriptorUtil;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenameJavaProjectChange;
import org.eclipse.jdt.internal.corext.refactoring.participants.JavaProcessors;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdating;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.Resources;

import org.eclipse.jdt.ui.refactoring.IRefactoringProcessorIds;
import org.eclipse.jdt.ui.refactoring.RefactoringSaveHelper;

import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

public final class RenameJavaProjectProcessor extends JavaRenameProcessor implements IReferenceUpdating {

	private IJavaProject fProject;
	private boolean fUpdateReferences;

	/**
	 * Creates a new rename java project processor.
	 * @param project the java project, or <code>null</code> if invoked by scripting
	 */
	public RenameJavaProjectProcessor(IJavaProject project) {
		fProject= project;
		if (fProject != null)
			setNewElementName(fProject.getElementName());
		fUpdateReferences= true;
	}

	public RenameJavaProjectProcessor(JavaRefactoringArguments arguments, RefactoringStatus status) {
		this(null);
		RefactoringStatus initializeStatus= initialize(arguments);
		status.merge(initializeStatus);
	}

	@Override
	public String getIdentifier() {
		return IRefactoringProcessorIds.RENAME_JAVA_PROJECT_PROCESSOR;
	}

	@Override
	public boolean isApplicable() throws CoreException {
		return RefactoringAvailabilityTester.isRenameAvailable(fProject);
	}

	@Override
	public String getProcessorName() {
		return RefactoringCoreMessages.RenameJavaProjectRefactoring_rename;
	}

	@Override
	protected String[] getAffectedProjectNatures() throws CoreException {
		return JavaProcessors.computeAffectedNatures(fProject);
	}

	@Override
	public Object[] getElements() {
		return new Object[] {fProject};
	}

	public Object getNewElement() {
		IPath newPath= fProject.getPath().removeLastSegments(1).append(getNewElementName());
		return JavaCore.create(ResourcesPlugin.getWorkspace().getRoot().findMember(newPath));
	}

	@Override
	protected RenameModifications computeRenameModifications() throws CoreException {
		RenameModifications result= new RenameModifications();
		result.rename(fProject, new RenameArguments(getNewElementName(), getUpdateReferences()));
		return result;
	}

	@Override
	protected IFile[] getChangedFiles() throws CoreException {
		IFile projectFile= fProject.getProject().getFile(".project"); //$NON-NLS-1$
		if (projectFile != null && projectFile.exists()) {
			return new IFile[] {projectFile};
		}
		return new IFile[0];
	}

	@Override
	public int getSaveMode() {
		return RefactoringSaveHelper.SAVE_ALL;
	}

	//---- IReferenceUpdating --------------------------------------

	public void setUpdateReferences(boolean update) {
		fUpdateReferences= update;
	}

	public boolean getUpdateReferences() {
		return fUpdateReferences;
	}

	//---- IRenameProcessor ----------------------------------------------

	public String getCurrentElementName() {
		return fProject.getElementName();
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
		return new RefactoringStatus();
	}

	public RefactoringStatus checkNewElementName(String newName) throws CoreException {
		Assert.isNotNull(newName, "new name"); //$NON-NLS-1$
		RefactoringStatus result= RefactoringStatus.create(ResourcesPlugin.getWorkspace().validateName(newName, IResource.PROJECT));
		if (result.hasFatalError())
			return result;

		if (projectNameAlreadyExists(newName))
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.RenameJavaProjectRefactoring_already_exists);
		if (projectFolderAlreadyExists(newName))
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.RenameJavaProjectProcessor_folder_already_exists);

		return new RefactoringStatus();
	}

	@Override
	protected RefactoringStatus doCheckFinalConditions(IProgressMonitor pm, CheckConditionsContext context) throws CoreException {
		pm.beginTask("", 1); //$NON-NLS-1$
		try{
			if (isReadOnly()){
				String message= Messages.format(RefactoringCoreMessages.RenameJavaProjectRefactoring_read_only,
						BasicElementLabels.getJavaElementName(fProject.getElementName()));
				return RefactoringStatus.createErrorStatus(message);
			}
			return new RefactoringStatus();
		} finally{
			pm.done();
		}
	}

	private boolean isReadOnly() {
		return Resources.isReadOnly(fProject.getResource());
	}

	private boolean projectNameAlreadyExists(String newName){
		return ResourcesPlugin.getWorkspace().getRoot().getProject(newName).exists();
	}

	private boolean projectFolderAlreadyExists(String newName) throws CoreException {
		boolean isNotInWorkpace= fProject.getProject().getDescription().getLocationURI() != null;
		if (isNotInWorkpace)
			return false; // projects outside of the workspace are not renamed

		URI locationURI= fProject.getProject().getLocationURI();
		IFileStore projectStore= EFS.getStore(locationURI);

		if (!projectStore.getFileSystem().isCaseSensitive() && newName.equalsIgnoreCase(fProject.getElementName()))
			return false; // allow to change case

		IFileStore newProjectStore= projectStore.getParent().getChild(newName);

		return newProjectStore.fetchInfo().exists();
	}

	@Override
	public Change createChange(IProgressMonitor monitor) throws CoreException {
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			final String description= Messages.format(RefactoringCoreMessages.RenameJavaProjectProcessor_descriptor_description_short, BasicElementLabels.getJavaElementName(fProject.getElementName()));
			final String header= Messages.format(RefactoringCoreMessages.RenameJavaProjectChange_descriptor_description, new String[] { BasicElementLabels.getJavaElementName(fProject.getElementName()), BasicElementLabels.getJavaElementName(getNewElementName())});
			final String comment= new JDTRefactoringDescriptorComment(null, this, header).asString();
			final int flags= RefactoringDescriptor.STRUCTURAL_CHANGE | RefactoringDescriptor.MULTI_CHANGE | RefactoringDescriptor.BREAKING_CHANGE;
			final RenameJavaElementDescriptor descriptor= RefactoringSignatureDescriptorFactory.createRenameJavaElementDescriptor(IJavaRefactorings.RENAME_JAVA_PROJECT);
			descriptor.setProject(null);
			descriptor.setDescription(description);
			descriptor.setComment(comment);
			descriptor.setFlags(flags);
			descriptor.setJavaElement(fProject);
			descriptor.setNewName(getNewElementName());
			descriptor.setUpdateReferences(fUpdateReferences);
			return new DynamicValidationRefactoringChange(descriptor, RefactoringCoreMessages.RenameJavaProjectRefactoring_rename, new Change[] { new RenameJavaProjectChange(fProject, getNewElementName(), fUpdateReferences)});
		} finally {
			monitor.done();
		}
	}

	private RefactoringStatus initialize(JavaRefactoringArguments extended) {
		final String handle= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT);
		if (handle != null) {
			final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(extended.getProject(), handle, false);
			if (element == null || !element.exists() || element.getElementType() != IJavaElement.JAVA_PROJECT)
				return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getProcessorName(), IJavaRefactorings.RENAME_JAVA_PROJECT);
			else
				fProject= (IJavaProject) element;
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT));
		final String name= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME);
		if (name != null && !"".equals(name)) //$NON-NLS-1$
			setNewElementName(name);
		else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME));
		final String references= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_REFERENCES);
		if (references != null) {
			fUpdateReferences= Boolean.valueOf(references).booleanValue();
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_REFERENCES));
		return new RefactoringStatus();
	}
}

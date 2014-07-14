/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.wizards;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.equinox.bidi.StructuredTextTypeHandlerFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.util.BidiUtils;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceComparator;

import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelStatus;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.wizards.NewElementWizardPage;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.util.CoreUtility;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathsBlock;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;


public class NewSourceFolderWizardPage extends NewElementWizardPage {
	private static final IClasspathAttribute ATTR_IGNORE_OPTIONAL_PROBLEMS_TRUE= JavaCore.newClasspathAttribute(IClasspathAttribute.IGNORE_OPTIONAL_PROBLEMS, "true"); //$NON-NLS-1$

	private static final String PAGE_NAME= "NewSourceFolderWizardPage"; //$NON-NLS-1$

	private StringButtonDialogField fProjectField;
	private StatusInfo fProjectStatus;

	private StringButtonDialogField fRootDialogField;
	private StatusInfo fRootStatus;

	private SelectionButtonDialogField fExcludeInOthersFields;
	private SelectionButtonDialogField fIgnoreOptionalProblemsField;

	private IWorkspaceRoot fWorkspaceRoot;

	private IJavaProject fCurrJProject;
	private IClasspathEntry[] fEntries;
	private IPath fOutputLocation;

	private IClasspathEntry[] fNewEntries;
	private IPath fNewOutputLocation;

	private boolean fIsProjectAsSourceFolder;

	private IPackageFragmentRoot fCreatedRoot;

	public NewSourceFolderWizardPage() {
		super(PAGE_NAME);

		setTitle(NewWizardMessages.NewSourceFolderWizardPage_title);
		setDescription(NewWizardMessages.NewSourceFolderWizardPage_description);

		fWorkspaceRoot= ResourcesPlugin.getWorkspace().getRoot();

		RootFieldAdapter adapter= new RootFieldAdapter();

		fProjectField= new StringButtonDialogField(adapter);
		fProjectField.setDialogFieldListener(adapter);
		fProjectField.setLabelText(NewWizardMessages.NewSourceFolderWizardPage_project_label);
		fProjectField.setButtonLabel(NewWizardMessages.NewSourceFolderWizardPage_project_button);

		fRootDialogField= new StringButtonDialogField(adapter);
		fRootDialogField.setDialogFieldListener(adapter);
		fRootDialogField.setLabelText(NewWizardMessages.NewSourceFolderWizardPage_root_label);
		fRootDialogField.setButtonLabel(NewWizardMessages.NewSourceFolderWizardPage_root_button);

		fExcludeInOthersFields= new SelectionButtonDialogField(SWT.CHECK);
		fExcludeInOthersFields.setDialogFieldListener(adapter);
		fExcludeInOthersFields.setLabelText(NewWizardMessages.NewSourceFolderWizardPage_exclude_label);
		fExcludeInOthersFields.setEnabled(JavaCore.ENABLED.equals(JavaCore.getOption(JavaCore.CORE_ENABLE_CLASSPATH_EXCLUSION_PATTERNS)));

		fIgnoreOptionalProblemsField= new SelectionButtonDialogField(SWT.CHECK);
		fIgnoreOptionalProblemsField.setDialogFieldListener(adapter);
		fIgnoreOptionalProblemsField.setLabelText(NewWizardMessages.NewSourceFolderWizardPage_ignore_optional_problems_label);
		fIgnoreOptionalProblemsField.setSelection(false);

		fRootStatus= new StatusInfo();
		fProjectStatus= new StatusInfo();
	}

	// -------- Initialization ---------

	public void init(IStructuredSelection selection) {
		String projPath= getProjectPath(selection);
		fProjectField.setText(projPath != null ? projPath : ""); //$NON-NLS-1$
		fRootDialogField.setText(""); //$NON-NLS-1$
	}

	private String getProjectPath(IStructuredSelection selection) {
		Object selectedElement= null;
		if (selection == null || selection.isEmpty()) {
			selectedElement= EditorUtility.getActiveEditorJavaInput();
		} else if (selection.size() == 1) {
			selectedElement= selection.getFirstElement();
		}

		if (selectedElement instanceof IResource) {
			IProject proj= ((IResource)selectedElement).getProject();
			if (proj != null) {
				return proj.getFullPath().makeRelative().toString();
			}
		} else if (selectedElement instanceof IJavaElement) {
			IJavaProject jproject= ((IJavaElement)selectedElement).getJavaProject();
			if (jproject != null) {
				return jproject.getProject().getFullPath().makeRelative().toString();
			}
		}

		return null;
	}

	// -------- UI Creation ---------

	/*
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		initializeDialogUnits(parent);

		Composite composite= new Composite(parent, SWT.NONE);

		GridLayout layout= new GridLayout();
		layout.numColumns= 3;
		composite.setLayout(layout);

		fProjectField.doFillIntoGrid(composite, 3);
		fRootDialogField.doFillIntoGrid(composite, 3);
		fExcludeInOthersFields.doFillIntoGrid(composite, 3);
		fIgnoreOptionalProblemsField.doFillIntoGrid(composite, 3);

		int maxFieldWidth= convertWidthInCharsToPixels(40);
		LayoutUtil.setWidthHint(fProjectField.getTextControl(null), maxFieldWidth);
		LayoutUtil.setHorizontalGrabbing(fProjectField.getTextControl(null));
		LayoutUtil.setWidthHint(fRootDialogField.getTextControl(null), maxFieldWidth);
		BidiUtils.applyBidiProcessing(fRootDialogField.getTextControl(null), StructuredTextTypeHandlerFactory.FILE);

		setControl(composite);
		Dialog.applyDialogFont(composite);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IJavaHelpContextIds.NEW_PACKAGEROOT_WIZARD_PAGE);
	}

	/*
	 * @see org.eclipse.jface.dialogs.IDialogPage#setVisible(boolean)
	 */
	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible) {
			if (fProjectField.getText().length() > 0)
				fRootDialogField.setFocus();
		}
	}

	// -------- ContainerFieldAdapter --------

	private class RootFieldAdapter implements IStringButtonAdapter, IDialogFieldListener {

		// -------- IStringButtonAdapter
		public void changeControlPressed(DialogField field) {
			packRootChangeControlPressed(field);
		}

		// -------- IDialogFieldListener
		public void dialogFieldChanged(DialogField field) {
			packRootDialogFieldChanged(field);
		}
	}
	protected void packRootChangeControlPressed(DialogField field) {
		if (field == fRootDialogField) {
			IPath initialPath= new Path(fRootDialogField.getText());
			String title= NewWizardMessages.NewSourceFolderWizardPage_ChooseExistingRootDialog_title;
			String message= NewWizardMessages.NewSourceFolderWizardPage_ChooseExistingRootDialog_description;
			IFolder folder= chooseFolder(title, message, initialPath);
			if (folder != null) {
				IPath path= folder.getFullPath().removeFirstSegments(1);
				fRootDialogField.setText(path.toString());
			}
		} else if (field == fProjectField) {
			IJavaProject jproject= chooseProject();
			if (jproject != null) {
				IPath path= jproject.getProject().getFullPath().makeRelative();
				fProjectField.setText(path.toString());
			}
		}
	}

	protected void packRootDialogFieldChanged(DialogField field) {
		if (field == fRootDialogField) {
			updateRootStatus();
		} else if (field == fProjectField) {
			updateProjectStatus();
			updateRootStatus();
		} else if (field == fExcludeInOthersFields) {
			updateRootStatus();
		} else if (field == fIgnoreOptionalProblemsField) {
			updateRootStatus();
		}
		updateStatus(new IStatus[] { fProjectStatus, fRootStatus });
	}


	private void updateProjectStatus() {
		fCurrJProject= null;
		fIsProjectAsSourceFolder= false;

		String str= fProjectField.getText();
		if (str.length() == 0) {
			fProjectStatus.setError(NewWizardMessages.NewSourceFolderWizardPage_error_EnterProjectName);
			return;
		}
		IPath path= new Path(str);
		if (path.segmentCount() != 1) {
			fProjectStatus.setError(NewWizardMessages.NewSourceFolderWizardPage_error_InvalidProjectPath);
			return;
		}
		IProject project= fWorkspaceRoot.getProject(path.toString());
		if (!project.exists()) {
			fProjectStatus.setError(NewWizardMessages.NewSourceFolderWizardPage_error_ProjectNotExists);
			return;
		}
		if (!project.isOpen()) {
			fProjectStatus.setError(NewWizardMessages.NewSourceFolderWizardPage_error_ProjectNotOpen);
			return;
		}
		try {
			if (project.hasNature(JavaCore.NATURE_ID)) {
				fCurrJProject= JavaCore.create(project);
				fEntries= fCurrJProject.getRawClasspath();
				fOutputLocation= fCurrJProject.getOutputLocation();
				fProjectStatus.setOK();
				return;
			}
		} catch (CoreException e) {
			JavaPlugin.log(e);
			fCurrJProject= null;
		}
		fProjectStatus.setError(NewWizardMessages.NewSourceFolderWizardPage_error_NotAJavaProject);
	}

	private void updateRootStatus() {
		fRootDialogField.enableButton(fCurrJProject != null);
		fIsProjectAsSourceFolder= false;
		if (fCurrJProject == null) {
			return;
		}
		fRootStatus.setOK();

		IPath projPath= fCurrJProject.getProject().getFullPath();
		String str= fRootDialogField.getText();
		if (str.length() == 0) {
			fRootStatus.setError(Messages.format(NewWizardMessages.NewSourceFolderWizardPage_error_EnterRootName, BasicElementLabels.getPathLabel(fCurrJProject.getPath(), false)));
		} else {
			IPath path= projPath.append(str);
			IStatus validate= fWorkspaceRoot.getWorkspace().validatePath(path.toString(), IResource.FOLDER);
			if (validate.matches(IStatus.ERROR)) {
				fRootStatus.setError(Messages.format(NewWizardMessages.NewSourceFolderWizardPage_error_InvalidRootName, validate.getMessage()));
			} else {
				IResource res= fWorkspaceRoot.findMember(path);
				if (res != null) {
					if (res.getType() != IResource.FOLDER) {
						fRootStatus.setError(NewWizardMessages.NewSourceFolderWizardPage_error_NotAFolder);
						return;
					}
					if (res.isVirtual()) {
						fRootStatus.setError(NewWizardMessages.NewSourceFolderWizardPage_error_FolderIsVirtual);
						return;
					}
				} else {
					if (!ResourcesPlugin.getWorkspace().validateFiltered(fWorkspaceRoot.getFolder(path)).isOK()) {
						fRootStatus.setError(NewWizardMessages.NewSourceFolderWizardPage_error_FolderNameFiltered);
						return;
					}
					URI projLocation= fCurrJProject.getProject().getLocationURI();
					if (projLocation != null) {
						try {
							IFileStore store= EFS.getStore(projLocation).getChild(str);
							if (store.fetchInfo().exists()) {
								fRootStatus.setError(NewWizardMessages.NewSourceFolderWizardPage_error_AlreadyExistingDifferentCase);
								return;
							}
						} catch (CoreException e) {
							// we couldn't create the file store. Ignore the exception
							// since we can't check if the file exist. Pretend that it
							// doesn't.
						}
					}
				}
				ArrayList<IClasspathEntry> newEntries= new ArrayList<IClasspathEntry>(fEntries.length + 1);
				int projectEntryIndex= -1;

				for (int i= 0; i < fEntries.length; i++) {
					IClasspathEntry curr= fEntries[i];
					if (curr.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
						if (path.equals(curr.getPath())) {
							fRootStatus.setError(NewWizardMessages.NewSourceFolderWizardPage_error_AlreadyExisting);
							return;
						}
						if (projPath.equals(curr.getPath())) {
							projectEntryIndex= i;
						}
					}
					newEntries.add(curr);
				}

				IClasspathAttribute[] attributes;
				if (fIgnoreOptionalProblemsField.isSelected()) {
					attributes= new IClasspathAttribute[] { ATTR_IGNORE_OPTIONAL_PROBLEMS_TRUE };
				} else {
					attributes= new IClasspathAttribute[] {};
				}
				IClasspathEntry newEntry= JavaCore.newSourceEntry(path, null, null, null, attributes);

				Set<IClasspathEntry> modified= new HashSet<IClasspathEntry>();
				if (fExcludeInOthersFields.isSelected()) {
					addExclusionPatterns(newEntry, newEntries, modified);
					IClasspathEntry entry= JavaCore.newSourceEntry(path, null, null, null, attributes);
					insertAtEndOfCategory(entry, newEntries);
				} else {
					if (projectEntryIndex != -1) {
						fIsProjectAsSourceFolder= true;
						newEntries.set(projectEntryIndex, newEntry);
					} else {
						IClasspathEntry entry= JavaCore.newSourceEntry(path, null, null, null, attributes);
						insertAtEndOfCategory(entry, newEntries);
					}
				}

				fNewEntries= newEntries.toArray(new IClasspathEntry[newEntries.size()]);
				fNewOutputLocation= fOutputLocation;

				IJavaModelStatus status= JavaConventions.validateClasspath(fCurrJProject, fNewEntries, fNewOutputLocation);
				if (!status.isOK()) {
					if (fOutputLocation.equals(projPath)) {
						fNewOutputLocation= projPath.append(PreferenceConstants.getPreferenceStore().getString(PreferenceConstants.SRCBIN_BINNAME));
						IStatus status2= JavaConventions.validateClasspath(fCurrJProject, fNewEntries, fNewOutputLocation);
						if (status2.isOK()) {
							if (fIsProjectAsSourceFolder) {
								fRootStatus.setInfo(Messages.format(NewWizardMessages.NewSourceFolderWizardPage_warning_ReplaceSFandOL, BasicElementLabels.getPathLabel(fNewOutputLocation, false)));
							} else {
								fRootStatus.setInfo(Messages.format(NewWizardMessages.NewSourceFolderWizardPage_warning_ReplaceOL, BasicElementLabels.getPathLabel(fNewOutputLocation, false)));
							}
							return;
						}
					}
					fRootStatus.setError(status.getMessage());
					return;
				} else if (fIsProjectAsSourceFolder) {
					fRootStatus.setInfo(NewWizardMessages.NewSourceFolderWizardPage_warning_ReplaceSF);
					return;
				}
				if (!modified.isEmpty()) {
					String info= modified.size() == 1 ? Messages.format(NewWizardMessages.NewSourceFolderWizardPage_warning_AddedExclusions_singular, (modified.iterator().next())
							.getPath()) : Messages.format(NewWizardMessages.NewSourceFolderWizardPage_warning_AddedExclusions_plural, String.valueOf(modified.size()));
					fRootStatus.setInfo(info);
					return;
				}
			}
		}
	}

	private void insertAtEndOfCategory(IClasspathEntry entry, List<IClasspathEntry> entries) {
		int length= entries.size();
		IClasspathEntry[] elements= entries.toArray(new IClasspathEntry[length]);
		int i= 0;
		while (i < length && elements[i].getEntryKind() != entry.getEntryKind()) {
			i++;
		}
		if (i < length) {
			i++;
			while (i < length && elements[i].getEntryKind() == entry.getEntryKind()) {
				i++;
			}
			entries.add(i, entry);
			return;
		}

		switch (entry.getEntryKind()) {
		case IClasspathEntry.CPE_SOURCE:
			entries.add(0, entry);
			break;
		case IClasspathEntry.CPE_CONTAINER:
		case IClasspathEntry.CPE_LIBRARY:
		case IClasspathEntry.CPE_PROJECT:
		case IClasspathEntry.CPE_VARIABLE:
		default:
			entries.add(entry);
			break;
		}
	}

	private void addExclusionPatterns(IClasspathEntry newEntry, List<IClasspathEntry> existing, Set<IClasspathEntry> modifiedEntries) {
		IPath entryPath= newEntry.getPath();
		for (int i= 0; i < existing.size(); i++) {
			IClasspathEntry curr= existing.get(i);
			IPath currPath= curr.getPath();
			if (curr.getEntryKind() == IClasspathEntry.CPE_SOURCE && currPath.isPrefixOf(entryPath)) {
				IPath[] exclusionFilters= curr.getExclusionPatterns();
				if (!JavaModelUtil.isExcludedPath(entryPath, exclusionFilters)) {
					IPath pathToExclude= entryPath.removeFirstSegments(currPath.segmentCount()).addTrailingSeparator();
					IPath[] newExclusionFilters= new IPath[exclusionFilters.length + 1];
					System.arraycopy(exclusionFilters, 0, newExclusionFilters, 0, exclusionFilters.length);
					newExclusionFilters[exclusionFilters.length]= pathToExclude;

					IClasspathEntry updated= JavaCore.newSourceEntry(currPath, newExclusionFilters, curr.getOutputLocation());
					existing.set(i, updated);
					modifiedEntries.add(updated);
				}
			}
		}
	}

	// ---- creation ----------------

	public IPackageFragmentRoot getNewPackageFragmentRoot() {
		return fCreatedRoot;
	}

	public IResource getCorrespondingResource() {
		return fCurrJProject.getProject().getFolder(fRootDialogField.getText());
	}

	public void createPackageFragmentRoot(IProgressMonitor monitor) throws CoreException, InterruptedException {
		if (monitor == null) {
			monitor= new NullProgressMonitor();
		}
		monitor.beginTask(NewWizardMessages.NewSourceFolderWizardPage_operation, 3);
		try {
			IPath projPath= fCurrJProject.getProject().getFullPath();
			if (fOutputLocation.equals(projPath) && !fNewOutputLocation.equals(projPath)) {
				if (BuildPathsBlock.hasClassfiles(fCurrJProject.getProject())) {
					if (BuildPathsBlock.getRemoveOldBinariesQuery(getShell()).doQuery(false, projPath)) {
						BuildPathsBlock.removeOldClassfiles(fCurrJProject.getProject());
					}
				}
			}

			String relPath= fRootDialogField.getText();

			IFolder folder= fCurrJProject.getProject().getFolder(relPath);
			if (!folder.exists()) {
				CoreUtility.createFolder(folder, true, true, new SubProgressMonitor(monitor, 1));
			}
			if (monitor.isCanceled()) {
				throw new InterruptedException();
			}

			fCurrJProject.setRawClasspath(fNewEntries, fNewOutputLocation, new SubProgressMonitor(monitor, 2));

			fCreatedRoot= fCurrJProject.getPackageFragmentRoot(folder);
		} finally {
			monitor.done();
		}
	}

	// ------------- choose dialogs

	private IFolder chooseFolder(String title, String message, IPath initialPath) {
		Class<?>[] acceptedClasses= new Class[] { IFolder.class };
		ISelectionStatusValidator validator= new TypedElementSelectionValidator(acceptedClasses, false);
		ViewerFilter filter= new TypedViewerFilter(acceptedClasses, null);

		ILabelProvider lp= new WorkbenchLabelProvider();
		ITreeContentProvider cp= new WorkbenchContentProvider();

		IProject currProject= fCurrJProject.getProject();

		ElementTreeSelectionDialog dialog= new ElementTreeSelectionDialog(getShell(), lp, cp);
		dialog.setValidator(validator);
		dialog.setTitle(title);
		dialog.setMessage(message);
		dialog.addFilter(filter);
		dialog.setInput(currProject);
		dialog.setComparator(new ResourceComparator(ResourceComparator.NAME));
		IResource res= currProject.findMember(initialPath);
		if (res != null) {
			dialog.setInitialSelection(res);
		}

		if (dialog.open() == Window.OK) {
			return (IFolder) dialog.getFirstResult();
		}
		return null;
	}

	private IJavaProject chooseProject() {
		IJavaProject[] projects;
		try {
			projects= JavaCore.create(fWorkspaceRoot).getJavaProjects();
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
			projects= new IJavaProject[0];
		}

		ILabelProvider labelProvider= new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT);
		ElementListSelectionDialog dialog= new ElementListSelectionDialog(getShell(), labelProvider);
		dialog.setTitle(NewWizardMessages.NewSourceFolderWizardPage_ChooseProjectDialog_title);
		dialog.setMessage(NewWizardMessages.NewSourceFolderWizardPage_ChooseProjectDialog_description);
		dialog.setElements(projects);
		dialog.setInitialSelections(new Object[] { fCurrJProject });
		dialog.setHelpAvailable(false);
		if (dialog.open() == Window.OK) {
			return (IJavaProject) dialog.getFirstResult();
		}
		return null;
	}

}

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
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.equinox.bidi.StructuredTextTypeHandlerFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IPathVariableManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.util.BidiUtils;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.ide.dialogs.PathVariableSelectionDialog;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceComparator;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModelStatus;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaConventions;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.wizards.NewElementWizardPage;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringDialogField;


public class AddSourceFolderWizardPage extends NewElementWizardPage {

	private final class LinkFields implements IStringButtonAdapter, IDialogFieldListener{
		private StringButtonDialogField fLinkLocation;

		private static final String DIALOGSTORE_LAST_EXTERNAL_LOC= JavaUI.ID_PLUGIN + ".last.external.project"; //$NON-NLS-1$

		private RootFieldAdapter fAdapter;

		private SelectionButtonDialogField fVariables;

		public LinkFields() {
			fLinkLocation= new StringButtonDialogField(this);

			fLinkLocation.setLabelText(NewWizardMessages.LinkFolderDialog_dependenciesGroup_locationLabel_desc);
			fLinkLocation.setButtonLabel(NewWizardMessages.LinkFolderDialog_dependenciesGroup_browseButton_desc);
			fLinkLocation.setDialogFieldListener(this);			

			fVariables= new SelectionButtonDialogField(SWT.PUSH);
			fVariables.setLabelText(NewWizardMessages.LinkFolderDialog_dependenciesGroup_variables_desc);
			fVariables.setDialogFieldListener(new IDialogFieldListener() {
				public void dialogFieldChanged(DialogField field) {
					handleVariablesButtonPressed();
				}
			});
		}

		public void setDialogFieldListener(RootFieldAdapter adapter) {
			fAdapter= adapter;
		}

		private void doFillIntoGrid(Composite parent, int numColumns) {
			fLinkLocation.doFillIntoGrid(parent, numColumns);

			LayoutUtil.setHorizontalSpan(fLinkLocation.getLabelControl(null), numColumns);
			LayoutUtil.setHorizontalGrabbing(fLinkLocation.getTextControl(null));
			BidiUtils.applyBidiProcessing(fLinkLocation.getTextControl(null), StructuredTextTypeHandlerFactory.FILE);

			fVariables.doFillIntoGrid(parent, 1);
		}

		public IPath getLinkTarget() {
			return Path.fromOSString(fLinkLocation.getText());
		}

		public void setLinkTarget(IPath path) {
			fLinkLocation.setText(path.toOSString());
		}

		/*(non-Javadoc)
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter#changeControlPressed(org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField)
		 */
		public void changeControlPressed(DialogField field) {
			final DirectoryDialog dialog= new DirectoryDialog(getShell());
			dialog.setMessage(NewWizardMessages.AddSourceFolderWizardPage_directory_message);
			String directoryName = fLinkLocation.getText().trim();
			if (directoryName.length() == 0) {
				String prevLocation= JavaPlugin.getDefault().getDialogSettings().get(DIALOGSTORE_LAST_EXTERNAL_LOC);
				if (prevLocation != null) {
					directoryName= prevLocation;
				}
			}

			if (directoryName.length() > 0) {
				final File path = new File(directoryName);
				if (path.exists())
					dialog.setFilterPath(directoryName);
			}
			final String selectedDirectory = dialog.open();
			if (selectedDirectory != null) {
				fLinkLocation.setText(selectedDirectory);
				fRootDialogField.setText(selectedDirectory.substring(selectedDirectory.lastIndexOf(File.separatorChar) + 1));
				JavaPlugin.getDefault().getDialogSettings().put(DIALOGSTORE_LAST_EXTERNAL_LOC, selectedDirectory);
				if (fAdapter != null) {
					fAdapter.dialogFieldChanged(fRootDialogField);
				}
			}
		}

		/**
		 * Opens a path variable selection dialog
		 */
		private void handleVariablesButtonPressed() {
			int variableTypes = IResource.FOLDER;
			PathVariableSelectionDialog dialog = new PathVariableSelectionDialog(getShell(), variableTypes);
			dialog.setResource(fParent);
			if (dialog.open() == IDialogConstants.OK_ID) {
				String[] variableNames = (String[]) dialog.getResult();
				if (variableNames != null && variableNames.length == 1) {
					fLinkLocation.setText(variableNames[0]);
					fRootDialogField.setText(new Path(variableNames[0]).lastSegment());
					if (fAdapter != null) {
						fAdapter.dialogFieldChanged(fRootDialogField);
					}
				}
			}
		}

		public void dialogFieldChanged(DialogField field) {
			if (fAdapter != null) {
				fAdapter.dialogFieldChanged(fLinkLocation);
			}
		}
	}

	private static final String PAGE_NAME= "NewSourceFolderWizardPage"; //$NON-NLS-1$

	private final StringDialogField fRootDialogField;
	private final SelectionButtonDialogField fAddExclusionPatterns, fRemoveProjectFolder, fIgnoreConflicts;
	private final LinkFields fLinkFields;

	private final CPListElement fNewElement;
	private final List<CPListElement> fExistingEntries;
	private final Hashtable<CPListElement, IPath[]> fOrginalExlusionFilters, fOrginalInclusionFilters, fOrginalExlusionFiltersCopy, fOrginalInclusionFiltersCopy;
	private final IPath fOrginalPath;
	private final boolean fLinkedMode;

	private IPath fOutputLocation;
	private IPath fNewOutputLocation;
	private CPListElement fOldProjectSourceFolder;

	private List<CPListElement> fModifiedElements;
	private List<CPListElement> fRemovedElements;

	private final boolean fAllowConflict;
	private final boolean fAllowRemoveProjectFolder;
	private final boolean fAllowAddExclusionPatterns;
	private final boolean fCanCommitConflictingBuildpath;
	private final IContainer fParent;

	public AddSourceFolderWizardPage(CPListElement newElement, List<CPListElement> existingEntries, IPath outputLocation,
			boolean linkedMode, boolean canCommitConflictingBuildpath,
			boolean allowIgnoreConflicts, boolean allowRemoveProjectFolder, boolean allowAddExclusionPatterns, IContainer parent) {

		super(PAGE_NAME);

		fLinkedMode= linkedMode;
		fCanCommitConflictingBuildpath= canCommitConflictingBuildpath;
		fAllowConflict= allowIgnoreConflicts;
		fAllowRemoveProjectFolder= allowRemoveProjectFolder;
		fAllowAddExclusionPatterns= allowAddExclusionPatterns;
		fParent= parent;

		fOrginalExlusionFilters= new Hashtable<CPListElement, IPath[]>();
		fOrginalInclusionFilters= new Hashtable<CPListElement, IPath[]>();
		fOrginalExlusionFiltersCopy= new Hashtable<CPListElement, IPath[]>();
		fOrginalInclusionFiltersCopy= new Hashtable<CPListElement, IPath[]>();
		for (Iterator<CPListElement> iter= existingEntries.iterator(); iter.hasNext();) {
			CPListElement element= iter.next();
			IPath[] exlusions= (IPath[])element.getAttribute(CPListElement.EXCLUSION);
			if (exlusions != null) {
				IPath[] save= new IPath[exlusions.length];
				for (int i= 0; i < save.length; i++) {
					save[i]= exlusions[i];
				}
				fOrginalExlusionFiltersCopy.put(element, save);
				fOrginalExlusionFilters.put(element, exlusions);
			}
			IPath[] inclusions= (IPath[])element.getAttribute(CPListElement.INCLUSION);
			if (inclusions != null) {
				IPath[] save= new IPath[inclusions.length];
				for (int i= 0; i < save.length; i++) {
					save[i]= inclusions[i];
				}
				fOrginalInclusionFiltersCopy.put(element, save);
				fOrginalInclusionFilters.put(element, inclusions);
			}
		}

		setTitle(NewWizardMessages.NewSourceFolderWizardPage_title);
		fOrginalPath= newElement.getPath();
		if (fOrginalPath == null) {
			if (linkedMode) {
				setDescription(Messages.format(NewWizardMessages.NewFolderDialog_createIn, BasicElementLabels.getJavaElementName(newElement.getJavaProject().getElementName())));
			} else {
				setDescription(Messages.format(NewWizardMessages.AddSourceFolderWizardPage_description, BasicElementLabels.getPathLabel(fParent.getFullPath(), false)));
			}
		} else {
			setDescription(NewWizardMessages.NewSourceFolderWizardPage_edit_description);
		}

		fNewElement= newElement;
		fExistingEntries= existingEntries;
		fModifiedElements= new ArrayList<CPListElement>();
		fRemovedElements= new ArrayList<CPListElement>();
		fOutputLocation= outputLocation;

		RootFieldAdapter adapter= new RootFieldAdapter();

		fRootDialogField= new StringDialogField();
		fRootDialogField.setLabelText(NewWizardMessages.NewSourceFolderWizardPage_root_label);
		if (fNewElement.getPath() == null) {
			fRootDialogField.setText(""); //$NON-NLS-1$
		} else {
			setFolderDialogText(fNewElement.getPath());
		}
		fRootDialogField.setEnabled(fNewElement.getJavaProject() != null);

		int buttonStyle= SWT.CHECK;
		if ((fAllowConflict && fAllowAddExclusionPatterns) ||
			(fAllowConflict && fAllowRemoveProjectFolder) ||
			(fAllowAddExclusionPatterns && fAllowRemoveProjectFolder)) {
			buttonStyle= SWT.RADIO;
		}

		fAddExclusionPatterns= new SelectionButtonDialogField(buttonStyle);
		fAddExclusionPatterns.setLabelText(NewWizardMessages.NewSourceFolderWizardPage_exclude_label);
		fAddExclusionPatterns.setSelection(!fCanCommitConflictingBuildpath && !fAllowRemoveProjectFolder);

		fRemoveProjectFolder= new SelectionButtonDialogField(buttonStyle);
		fRemoveProjectFolder.setLabelText(NewWizardMessages.NewSourceFolderWizardPage_ReplaceExistingSourceFolder_label);
		fRemoveProjectFolder.setSelection(!fCanCommitConflictingBuildpath && fAllowRemoveProjectFolder);

		fIgnoreConflicts= new SelectionButtonDialogField(buttonStyle);
		fIgnoreConflicts.setLabelText(NewWizardMessages.AddSourceFolderWizardPage_ignoreNestingConflicts);
		fIgnoreConflicts.setSelection(fCanCommitConflictingBuildpath);

		fLinkFields= new LinkFields();
		if (fNewElement.getLinkTarget() != null) {
			fLinkFields.setLinkTarget(fNewElement.getLinkTarget());
		}

		fRemoveProjectFolder.setDialogFieldListener(adapter);
		fAddExclusionPatterns.setDialogFieldListener(adapter);
		fIgnoreConflicts.setDialogFieldListener(adapter);
		fRootDialogField.setDialogFieldListener(adapter);
		fLinkFields.setDialogFieldListener(adapter);

		packRootDialogFieldChanged();
	}

	// -------- UI Creation ---------

	/*
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		initializeDialogUnits(parent);

		Composite composite= new Composite(parent, SWT.NONE);

		GridLayout layout= new GridLayout();
		layout.numColumns= 4;
		composite.setLayout(layout);

		if (fLinkedMode) {
			fLinkFields.doFillIntoGrid(composite, layout.numColumns);
			fRootDialogField.doFillIntoGrid(composite, layout.numColumns - 1);
		} else {
			fRootDialogField.doFillIntoGrid(composite, layout.numColumns - 1);
		}

		if (fAllowRemoveProjectFolder)
			fRemoveProjectFolder.doFillIntoGrid(composite, layout.numColumns);

		if (fAllowAddExclusionPatterns)
			fAddExclusionPatterns.doFillIntoGrid(composite, layout.numColumns);

		if (fAllowConflict)
			fIgnoreConflicts.doFillIntoGrid(composite, layout.numColumns);

		LayoutUtil.setHorizontalSpan(fRootDialogField.getLabelControl(null), layout.numColumns);
		LayoutUtil.setHorizontalGrabbing(fRootDialogField.getTextControl(null));

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
			packRootDialogFieldChanged();
		}
	}

	protected void packRootChangeControlPressed(DialogField field) {
		if (field == fRootDialogField) {
			IPath initialPath= new Path(fRootDialogField.getText());
			String title= NewWizardMessages.NewSourceFolderWizardPage_ChooseExistingRootDialog_title;
			String message= NewWizardMessages.NewSourceFolderWizardPage_ChooseExistingRootDialog_description;
			IFolder folder= chooseFolder(title, message, initialPath);
			if (folder != null) {
				setFolderDialogText(folder.getFullPath());
			}
		}
	}

	private void setFolderDialogText(IPath path) {
		IPath shortPath= path.removeFirstSegments(1);
		fRootDialogField.setText(shortPath.toString());
	}

	protected void packRootDialogFieldChanged() {
		StatusInfo status= updateRootStatus();
		updateStatus(new IStatus[] {status});
	}

	private StatusInfo updateRootStatus() {
		IJavaProject javaProject= fNewElement.getJavaProject();
		IProject project= javaProject.getProject();

		StatusInfo pathNameStatus= validatePathName(fRootDialogField.getText(), fParent);

		if (!pathNameStatus.isOK())
			return pathNameStatus;

		if (fLinkedMode) {
			IStatus linkNameStatus= validateLinkLocation(fRootDialogField.getText());
			if (linkNameStatus.matches(IStatus.ERROR)) {
				StatusInfo result= new StatusInfo();
				result.setError(linkNameStatus.getMessage());
				return result;
			}
		}

		StatusInfo result= new StatusInfo();
		result.setOK();

		IPath projPath= project.getFullPath();
		IPath path= fParent.getFullPath().append(fRootDialogField.getText());

		restoreCPElements();

		int projectEntryIndex= -1;
		boolean createFolderForExisting= false;

		IFolder folder= fParent.getFolder(new Path(fRootDialogField.getText()));
		for (int i= 0; i < fExistingEntries.size(); i++) {
			IClasspathEntry curr= fExistingEntries.get(i).getClasspathEntry();
			if (curr.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				if (path.equals(curr.getPath()) && fExistingEntries.get(i) != fNewElement) {
					if (folder.exists()) {
						result.setError(NewWizardMessages.NewSourceFolderWizardPage_error_AlreadyExisting);
						return result;
					} else {
						createFolderForExisting= true;
					}
				}
				if (projPath.equals(curr.getPath())) {
					projectEntryIndex= i;
				}
			}
		}

		if (folder.exists() && !folder.getFullPath().equals(fOrginalPath))
			return new StatusInfo(IStatus.ERROR, Messages.format(NewWizardMessages.NewFolderDialog_folderNameEmpty_alreadyExists, BasicElementLabels.getPathLabel(folder.getFullPath(), false)));

		boolean isProjectASourceFolder= projectEntryIndex != -1;

		fModifiedElements.clear();
		updateFilters(fNewElement.getPath(), path);

		fNewElement.setPath(path);
		if (fLinkedMode) {
			fNewElement.setLinkTarget(fLinkFields.getLinkTarget());
		}
		fRemovedElements.clear();
		Set<CPListElement> modified= new HashSet<CPListElement>();
		boolean isProjectSourceFolderReplaced= false;
		if (fAddExclusionPatterns.isSelected()) {
			if (fOrginalPath == null) {
				addExclusionPatterns(fNewElement, fExistingEntries, modified);
				fModifiedElements.addAll(modified);
				if (!createFolderForExisting)
					CPListElement.insert(fNewElement, fExistingEntries);
			}
		} else {
			if (isProjectASourceFolder) {
				if (fRemoveProjectFolder.isSelected()) {
					fOldProjectSourceFolder= fExistingEntries.get(projectEntryIndex);
					fRemovedElements.add(fOldProjectSourceFolder);
					fExistingEntries.set(projectEntryIndex, fNewElement);
					isProjectSourceFolderReplaced= true;
				} else {
					if (!createFolderForExisting)
					CPListElement.insert(fNewElement, fExistingEntries);
				}
			} else {
				if (!createFolderForExisting)
					CPListElement.insert(fNewElement, fExistingEntries);
			}
		}

		if ((!fAllowConflict && fCanCommitConflictingBuildpath) || createFolderForExisting)
			return new StatusInfo();

		fNewOutputLocation= null;
		IJavaModelStatus status= JavaConventions.validateClasspath(javaProject, CPListElement.convertToClasspathEntries(fExistingEntries), fOutputLocation);
		if (!status.isOK()) {
			if (fOutputLocation.equals(projPath)) {
				//Try to change the output folder
				fNewOutputLocation= projPath.append(PreferenceConstants.getPreferenceStore().getString(PreferenceConstants.SRCBIN_BINNAME));
				IStatus status2= JavaConventions.validateClasspath(javaProject, CPListElement.convertToClasspathEntries(fExistingEntries), fNewOutputLocation);
				if (status2.isOK()) {
					if (isProjectSourceFolderReplaced) {
						result.setInfo(Messages.format(NewWizardMessages.NewSourceFolderWizardPage_warning_ReplaceSFandOL, BasicElementLabels.getPathLabel(fNewOutputLocation, false)));
					} else {
						result.setInfo(Messages.format(NewWizardMessages.NewSourceFolderWizardPage_warning_ReplaceOL, BasicElementLabels.getPathLabel(fNewOutputLocation, false)));
					}
					return result;
				}
			}
			//Don't know what the problem is, report to user
			fNewOutputLocation= null;
			if (fCanCommitConflictingBuildpath) {
				result.setInfo(NewWizardMessages.AddSourceFolderWizardPage_conflictWarning + status.getMessage());
			} else {
				result.setError(status.getMessage());
			}
			return result;
		}
		if (!modified.isEmpty()) {
			//Added exclusion patterns to solve problem
			if (modified.size() == 1) {
				CPListElement elem= (CPListElement)modified.toArray()[0];
				String changed= BasicElementLabels.getPathLabel(elem.getPath(), false);
				String excl= BasicElementLabels.getPathLabel(fNewElement.getPath(), false);
				result.setInfo(Messages.format(NewWizardMessages.AddSourceFolderWizardPage_addSinglePattern, new Object[] {excl, changed}));
			} else {
				result.setInfo(Messages.format(NewWizardMessages.NewSourceFolderWizardPage_warning_AddedExclusions_plural, String.valueOf(modified.size())));
			}
			return result;
		}
		if (isProjectSourceFolderReplaced) {
			result.setInfo(NewWizardMessages.AddSourceFolderWizardPage_replaceSourceFolderInfo);
			return result;
		}

		return result;
	}

	public void restore() {
		for (Iterator<CPListElement> iter= fExistingEntries.iterator(); iter.hasNext();) {
			CPListElement element= iter.next();
			if (fOrginalExlusionFilters.containsKey(element)) {
				element.setAttribute(CPListElement.EXCLUSION, fOrginalExlusionFiltersCopy.get(element));
			}
			if (fOrginalInclusionFilters.containsKey(element)) {
				element.setAttribute(CPListElement.INCLUSION, fOrginalInclusionFiltersCopy.get(element));
			}
		}
		fNewElement.setPath(fOrginalPath);
	}

	private void restoreCPElements() {
		if (fNewElement.getPath() != null) {
			for (Iterator<CPListElement> iter= fExistingEntries.iterator(); iter.hasNext();) {
				CPListElement element= iter.next();
				if (fOrginalExlusionFilters.containsKey(element)) {
					element.setAttribute(CPListElement.EXCLUSION, fOrginalExlusionFilters.get(element));
				}
				if (fOrginalInclusionFilters.containsKey(element)) {
					element.setAttribute(CPListElement.INCLUSION, fOrginalInclusionFilters.get(element));
				}
			}

			if (fOldProjectSourceFolder != null) {
				fExistingEntries.set(fExistingEntries.indexOf(fNewElement), fOldProjectSourceFolder);
				fOldProjectSourceFolder= null;
			} else if (fExistingEntries.contains(fNewElement)) {
				fExistingEntries.remove(fNewElement);
			}
		}
	}

	private void updateFilters(IPath oldPath, IPath newPath) {
		if (oldPath == null)
			return;

		IPath projPath= fNewElement.getJavaProject().getProject().getFullPath();
		if (projPath.isPrefixOf(oldPath)) {
			oldPath= oldPath.removeFirstSegments(projPath.segmentCount()).addTrailingSeparator();
		}
		if (projPath.isPrefixOf(newPath)) {
			newPath= newPath.removeFirstSegments(projPath.segmentCount()).addTrailingSeparator();
		}

		for (Iterator<CPListElement> iter= fExistingEntries.iterator(); iter.hasNext();) {
			CPListElement element= iter.next();
			IPath elementPath= element.getPath();
			if (projPath.isPrefixOf(elementPath)) {
				elementPath= elementPath.removeFirstSegments(projPath.segmentCount());
				if (elementPath.segmentCount() > 0)
					elementPath= elementPath.addTrailingSeparator();
			}

			IPath[] exlusions= (IPath[])element.getAttribute(CPListElement.EXCLUSION);
			if (exlusions != null) {
				for (int i= 0; i < exlusions.length; i++) {
					if (elementPath.append(exlusions[i]).equals(oldPath)) {
						fModifiedElements.add(element);
						exlusions[i]= newPath.removeFirstSegments(elementPath.segmentCount());
					}
				}
				element.setAttribute(CPListElement.EXCLUSION, exlusions);
			}

			IPath[] inclusion= (IPath[])element.getAttribute(CPListElement.INCLUSION);
			if (inclusion != null) {
				for (int i= 0; i < inclusion.length; i++) {
					if (elementPath.append(inclusion[i]).equals(oldPath)) {
						fModifiedElements.add(element);
						inclusion[i]= newPath.removeFirstSegments(elementPath.segmentCount());
					}
				}
				element.setAttribute(CPListElement.INCLUSION, inclusion);
			}
		}
	}

    /**
	 * Validates this page's controls.
     * @param folderName the folder name
	 *
	 * @return IStatus indicating the validation result. IStatus.OK if the
	 *  specified link target is valid given the linkHandle.
	 */
	private IStatus validateLinkLocation(String folderName) {
		IWorkspace workspace= JavaPlugin.getWorkspace();
		IPath path= Path.fromOSString(fLinkFields.fLinkLocation.getText());

		Path folderLocation= new Path(folderName);
		if (folderLocation.isAbsolute())
			return new StatusInfo(IStatus.ERROR, NewWizardMessages.AddSourceFolderWizardPage_error_NotARelativePathName);

		IProject project= fNewElement.getJavaProject().getProject();
		IFolder folder= project.getFolder(folderLocation);
		IStatus locationStatus= workspace.validateLinkLocation(folder, path);
		if (locationStatus.matches(IStatus.ERROR))
			return locationStatus;

		IPathVariableManager pathVariableManager= project.getPathVariableManager();
		IPath path1= Path.fromOSString(fLinkFields.fLinkLocation.getText());
		IPath resolvedPath= pathVariableManager.resolvePath(path1);
		// use the resolved link target name
		String resolvedLinkTarget= resolvedPath.toOSString();

		path= new Path(resolvedLinkTarget);
		File linkTargetFile= new Path(resolvedLinkTarget).toFile();
		if (linkTargetFile.exists()) {
			if (!linkTargetFile.isDirectory())
	            return new StatusInfo(IStatus.ERROR, NewWizardMessages.NewFolderDialog_linkTargetNotFolder);
		} else {
			return new StatusInfo(IStatus.ERROR, NewWizardMessages.NewFolderDialog_linkTargetNonExistent);
		}
		if (locationStatus.isOK()) {
			return new StatusInfo();
		}
		return new StatusInfo(locationStatus.getSeverity(), locationStatus.getMessage());
	}

	private static StatusInfo validatePathName(String str, IContainer parent) {
		StatusInfo result= new StatusInfo();
		result.setOK();

		IPath parentPath= parent.getFullPath();

		if (str.length() == 0) {
			result.setError(Messages.format(NewWizardMessages.NewSourceFolderWizardPage_error_EnterRootName, BasicElementLabels.getPathLabel(parentPath, false)));
			return result;
		}

		IPath path= parentPath.append(str);

		IWorkspaceRoot workspaceRoot= ResourcesPlugin.getWorkspace().getRoot();
		IStatus validate= workspaceRoot.getWorkspace().validatePath(path.toString(), IResource.FOLDER);
		if (validate.matches(IStatus.ERROR)) {
			result.setError(Messages.format(NewWizardMessages.NewSourceFolderWizardPage_error_InvalidRootName, validate.getMessage()));
			return result;
		}

		IResource res= workspaceRoot.findMember(path);
		if (res != null) {
			if (res.getType() != IResource.FOLDER) {
				result.setError(NewWizardMessages.NewSourceFolderWizardPage_error_NotAFolder);
				return result;
			}
		} else {

			URI parentLocation= parent.getLocationURI();
			if (parentLocation != null) {
				try {
					IFileStore store= EFS.getStore(parentLocation).getChild(str);
					if (store.fetchInfo().exists()) {
						result.setError(NewWizardMessages.NewSourceFolderWizardPage_error_AlreadyExistingDifferentCase);
						return result;
					}
				} catch (CoreException e) {
					// we couldn't create the file store. Ignore the exception
					// since we can't check if the file exist. Pretend that it
					// doesn't.
				}
			}
		}

		return result;
	}

	private void addExclusionPatterns(CPListElement newEntry, List<CPListElement> existing, Set<CPListElement> modifiedEntries) {
		IPath entryPath= newEntry.getPath();
		for (int i= 0; i < existing.size(); i++) {
			CPListElement curr= existing.get(i);
			IPath currPath= curr.getPath();
			if (curr != newEntry && curr.getEntryKind() == IClasspathEntry.CPE_SOURCE && currPath.isPrefixOf(entryPath)) {
				boolean added= curr.addToExclusions(entryPath);
				if (added) {
					modifiedEntries.add(curr);
				}
			}
		}
	}

	public IResource getCorrespondingResource() {
		return fParent.getFolder(new Path(fRootDialogField.getText()));
	}

	public IPath getOutputLocation() {
		if (fNewOutputLocation != null) {
			return fNewOutputLocation;
		}

		return fOutputLocation;
	}

	// ------------- choose dialogs

	private IFolder chooseFolder(String title, String message, IPath initialPath) {
		Class<?>[] acceptedClasses= new Class[] { IFolder.class };
		ISelectionStatusValidator validator= new TypedElementSelectionValidator(acceptedClasses, false);
		ViewerFilter filter= new TypedViewerFilter(acceptedClasses, null);

		ILabelProvider lp= new WorkbenchLabelProvider();
		ITreeContentProvider cp= new WorkbenchContentProvider();

		IProject currProject= fNewElement.getJavaProject().getProject();

		ElementTreeSelectionDialog dialog= new ElementTreeSelectionDialog(getShell(), lp, cp) {
			@Override
			protected Control createDialogArea(Composite parent) {
				Control result= super.createDialogArea(parent);
				PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, IJavaHelpContextIds.BP_CHOOSE_EXISTING_FOLDER_TO_MAKE_SOURCE_FOLDER);
				return result;
			}
		};
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

	public List<CPListElement> getModifiedElements() {
		if (fOrginalPath != null && !fModifiedElements.contains(fNewElement))
			fModifiedElements.add(fNewElement);

		return fModifiedElements;
	}

	public List<CPListElement> getRemovedElements() {
		return fRemovedElements;
	}

}

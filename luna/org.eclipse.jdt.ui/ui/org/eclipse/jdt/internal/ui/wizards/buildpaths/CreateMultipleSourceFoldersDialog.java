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
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.INewWizard;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.model.BaseWorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.actions.AbstractOpenWizardAction;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter;

public class CreateMultipleSourceFoldersDialog extends TrayDialog {

	private final class FakeFolderBaseWorkbenchContentProvider extends BaseWorkbenchContentProvider {
		/**
		 * {@inheritDoc}
		 */
		@Override
		public Object getParent(Object element) {
			Object object= fNonExistingFolders.get(element);
			if (object != null)
				return object;

			return super.getParent(element);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Object[] getChildren(Object element) {
			List<Object> result= new ArrayList<Object>();
			//all keys with value element
			Set<IFolder> keys= fNonExistingFolders.keySet();
			for (Iterator<IFolder> iter= keys.iterator(); iter.hasNext();) {
				Object key= iter.next();
				if (fNonExistingFolders.get(key).equals(element)) {
					result.add(key);
				}
			}
			if (result.size() == 0)
				return super.getChildren(element);

			Object[] children= super.getChildren(element);
			for (int i= 0; i < children.length; i++) {
				result.add(children[i]);
			}
			return result.toArray();
		}
	}

	private final IJavaProject fJavaProject;
	private final CPListElement[] fExistingElements;
	private String fOutputLocation;
	private final HashSet<CPListElement> fRemovedElements;
	private final HashSet<CPListElement> fModifiedElements;
	private final HashSet<CPListElement> fInsertedElements;
	private final Hashtable<IFolder, IContainer> fNonExistingFolders;

	public CreateMultipleSourceFoldersDialog(final IJavaProject javaProject, final CPListElement[] existingElements, final String outputLocation, Shell shell) {
		super(shell);
		fJavaProject= javaProject;
		fExistingElements= existingElements;
		fOutputLocation= outputLocation;
		fRemovedElements= new HashSet<CPListElement>();
		fModifiedElements= new HashSet<CPListElement>();
		fInsertedElements= new HashSet<CPListElement>();
		fNonExistingFolders= new Hashtable<IFolder, IContainer>();

		for (int i= 0; i < existingElements.length; i++) {
			CPListElement cur= existingElements[i];
			if (cur.getResource() == null || !cur.getResource().exists()) {
				addFakeFolder(fJavaProject.getProject(), cur);
			}
		}
	}

	@Override
	public int open() {
		Class<?>[] acceptedClasses= new Class<?>[] { IProject.class, IFolder.class };
		List<IResource> existingContainers= getExistingContainers(fExistingElements);

		IProject[] allProjects= ResourcesPlugin.getWorkspace().getRoot().getProjects();
		ArrayList<IProject> rejectedElements= new ArrayList<IProject>(allProjects.length);
		IProject currProject= fJavaProject.getProject();
		for (int i= 0; i < allProjects.length; i++) {
			if (!allProjects[i].equals(currProject)) {
				rejectedElements.add(allProjects[i]);
			}
		}
		ViewerFilter filter= new TypedViewerFilter(acceptedClasses, rejectedElements.toArray()){

			/* (non-Javadoc)
			 * @see org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter#select(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
			 */
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				if (element instanceof IFolder && ((IFolder)element).isVirtual()) {
					return false;
				}
				return super.select(viewer, parentElement, element);
			}
		};

		ILabelProvider lp= new WorkbenchLabelProvider();
		ITreeContentProvider cp= new FakeFolderBaseWorkbenchContentProvider();

		String title= NewWizardMessages.SourceContainerWorkbookPage_ExistingSourceFolderDialog_new_title;
		String message= NewWizardMessages.SourceContainerWorkbookPage_ExistingSourceFolderDialog_edit_description;


		MultipleFolderSelectionDialog dialog= new MultipleFolderSelectionDialog(getShell(), lp, cp) {
			@Override
			protected Control createDialogArea(Composite parent) {
				Control result= super.createDialogArea(parent);
				PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, IJavaHelpContextIds.BP_CHOOSE_EXISTING_FOLDER_TO_MAKE_SOURCE_FOLDER);
				return result;
			}

			@Override
			protected Object createFolder(final IContainer container) {
				final Object[] result= new Object[1];
				final CPListElement newElement= new CPListElement(fJavaProject, IClasspathEntry.CPE_SOURCE);
				final AddSourceFolderWizard wizard= newSourceFolderWizard(newElement, fExistingElements, fOutputLocation, container);
				AbstractOpenWizardAction action= new AbstractOpenWizardAction() {
					@Override
					protected INewWizard createWizard() throws CoreException {
						return wizard;
					}
				};
				action.addPropertyChangeListener(new IPropertyChangeListener() {
					public void propertyChange(PropertyChangeEvent event) {
						if (event.getProperty().equals(IAction.RESULT)) {
							if (event.getNewValue().equals(Boolean.TRUE)) {
								result[0]= addFakeFolder(fJavaProject.getProject(), newElement);
							} else {
								wizard.cancel();
							}
						}
					}
				});
				action.run();
				return result[0];
			}
		};
		dialog.setExisting(existingContainers.toArray());
		dialog.setTitle(title);
		dialog.setMessage(message);
		dialog.addFilter(filter);
		dialog.setInput(fJavaProject.getProject().getParent());
		dialog.setInitialFocus(fJavaProject.getProject());

		if (dialog.open() == Window.OK) {
			Object[] elements= dialog.getResult();
			for (int i= 0; i < elements.length; i++) {
				IResource res= (IResource)elements[i];
				fInsertedElements.add(new CPListElement(fJavaProject, IClasspathEntry.CPE_SOURCE, res.getFullPath(), res));
			}

			if (fExistingElements.length == 1) {
				CPListElement existingElement= fExistingElements[0];
				if (existingElement.getResource() instanceof IProject) {
					if (!removeProjectFromBP(existingElement)) {
						ArrayList<CPListElement> added= new ArrayList<CPListElement>(fInsertedElements);
						HashSet<CPListElement> updatedEclusionPatterns= new HashSet<CPListElement>();
						addExlusionPatterns(added, updatedEclusionPatterns);
						fModifiedElements.addAll(updatedEclusionPatterns);
					}
				}
			} else {
				ArrayList<CPListElement> added= new ArrayList<CPListElement>(fInsertedElements);
				HashSet<CPListElement> updatedEclusionPatterns= new HashSet<CPListElement>();
				addExlusionPatterns(added, updatedEclusionPatterns);
				fModifiedElements.addAll(updatedEclusionPatterns);
			}
			return Window.OK;
		} else {
			return Window.CANCEL;
		}
	}

	public List<CPListElement> getInsertedElements() {
		return new ArrayList<CPListElement>(fInsertedElements);
	}

	public List<CPListElement> getRemovedElements() {
		return new ArrayList<CPListElement>(fRemovedElements);
	}

	public List<CPListElement> getModifiedElements() {
		return new ArrayList<CPListElement>(fModifiedElements);
	}

	public IPath getOutputLocation() {
		return new Path(fOutputLocation).makeAbsolute();
	}

	/**
	 * Asks to change the output folder to 'proj/bin' when no source folders were existing
	 *
	 * @param existing the element to remove
	 * @return returns <code>true</code> if the element has been removed
	 */
	private boolean removeProjectFromBP(CPListElement existing) {
		IPath outputFolder= new Path(fOutputLocation);

		IPath newOutputFolder= null;
		String message;
		if (outputFolder.segmentCount() == 1) {
			String outputFolderName= PreferenceConstants.getPreferenceStore().getString(PreferenceConstants.SRCBIN_BINNAME);
			newOutputFolder= outputFolder.append(outputFolderName);
			message= Messages.format(NewWizardMessages.SourceContainerWorkbookPage_ChangeOutputLocationDialog_project_and_output_message, BasicElementLabels.getPathLabel(newOutputFolder, false));
		} else {
			message= NewWizardMessages.SourceContainerWorkbookPage_ChangeOutputLocationDialog_project_message;
		}
		String title= NewWizardMessages.SourceContainerWorkbookPage_ChangeOutputLocationDialog_title;
		if (MessageDialog.openQuestion(getShell(), title, message)) {
			fRemovedElements.add(existing);
			if (newOutputFolder != null) {
				fOutputLocation= newOutputFolder.toString();
			}
			return true;
		}
		return false;
	}

	private void addExlusionPatterns(List<CPListElement> newEntries, Set<CPListElement> modifiedEntries) {
		BuildPathBasePage.fixNestingConflicts(newEntries.toArray(new CPListElement[newEntries.size()]), fExistingElements, modifiedEntries);
		if (!modifiedEntries.isEmpty()) {
			String title= NewWizardMessages.SourceContainerWorkbookPage_exclusion_added_title;
			String message= NewWizardMessages.SourceContainerWorkbookPage_exclusion_added_message;
			MessageDialog.openInformation(getShell(), title, message);
		}
	}

	private AddSourceFolderWizard newSourceFolderWizard(CPListElement element, CPListElement[] existing, String outputLocation, IContainer parent) {
		AddSourceFolderWizard wizard= new AddSourceFolderWizard(existing, element, new Path(outputLocation).makeAbsolute(), false, true, false, false, false, parent);
		wizard.setDoFlushChange(false);
		return wizard;
	}

	private List<IResource> getExistingContainers(CPListElement[] existingElements) {
		List<IResource> res= new ArrayList<IResource>();
		for (int i= 0; i < existingElements.length; i++) {
			IResource resource= existingElements[i].getResource();
			if (resource instanceof IContainer) {
				res.add(resource);
			}
		}
		Set<IFolder> keys= fNonExistingFolders.keySet();
		for (Iterator<IFolder> iter= keys.iterator(); iter.hasNext();) {
			IFolder folder= iter.next();
			res.add(folder);
		}
		return res;
	}

	private IFolder addFakeFolder(final IContainer container, final CPListElement element) {
		IFolder result;
		IPath projectPath= fJavaProject.getPath();
		IPath path= element.getPath();
		if (projectPath.isPrefixOf(path)) {
			path= path.removeFirstSegments(projectPath.segmentCount());
		}
		result= container.getFolder(path);
		IFolder folder= result;
		do {
			IContainer parent= folder.getParent();
			fNonExistingFolders.put(folder, parent);
			if (parent instanceof IFolder) {
				folder= (IFolder)parent;
			} else {
				folder= null;
			}
		} while (folder != null && !folder.exists());
		return result;
	}
}

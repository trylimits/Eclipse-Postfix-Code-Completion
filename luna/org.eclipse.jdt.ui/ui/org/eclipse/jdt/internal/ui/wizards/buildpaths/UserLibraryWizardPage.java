/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Konstantin Komissarchik <konstantin.komissarchik@oracle.com> - [build path] editing user library properties drops classpath entry attributes - http://bugs.eclipse.org/311603
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ibm.icu.text.Collator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.Dialog;

import org.eclipse.ui.dialogs.PreferencesUtil;

import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.wizards.IClasspathContainerPage;
import org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension;
import org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension2;
import org.eclipse.jdt.ui.wizards.NewElementWizardPage;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.preferences.UserLibraryPreferencePage;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.CheckedListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;

/**
 *
 */
public class UserLibraryWizardPage extends NewElementWizardPage implements IClasspathContainerPage, IClasspathContainerPageExtension, IClasspathContainerPageExtension2  {

	private CheckedListDialogField<CPUserLibraryElement> fLibrarySelector;
	private CPUserLibraryElement fEditResult;
	private Set<IPath> fUsedPaths;
	private boolean fIsEditMode;
	private IJavaProject fProject;
	private IClasspathEntry fOldClasspathEntry;

	public UserLibraryWizardPage() {
		super("UserLibraryWizardPage"); //$NON-NLS-1$
		setTitle(NewWizardMessages.UserLibraryWizardPage_title);
		setImageDescriptor(JavaPluginImages.DESC_WIZBAN_ADD_LIBRARY);
		updateDescription(null);
		fUsedPaths= new HashSet<IPath>();
		fProject= createPlaceholderProject();

		LibraryListAdapter adapter= new LibraryListAdapter();
		String[] buttonLabels= new String[] {
				NewWizardMessages.UserLibraryWizardPage_list_config_button
		};
		fLibrarySelector= new CheckedListDialogField<CPUserLibraryElement>(adapter, buttonLabels, new CPListLabelProvider());
		fLibrarySelector.setDialogFieldListener(adapter);
		fLibrarySelector.setLabelText(NewWizardMessages.UserLibraryWizardPage_list_label);
		fEditResult= null;
		updateStatus(validateSetting(Collections.<CPUserLibraryElement>emptyList()));
	}

    private static IJavaProject createPlaceholderProject() {
        String name= "####internal"; //$NON-NLS-1$
        IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
        while (true) {
            IProject project= root.getProject(name);
            if (!project.exists()) {
                return JavaCore.create(project);
            }
            name += '1';
        }
    }

	private void updateDescription(IClasspathEntry containerEntry) {
		if (containerEntry == null || containerEntry.getPath().segmentCount() != 2) {
			setDescription(NewWizardMessages.UserLibraryWizardPage_description_new);
		} else {
			setDescription(NewWizardMessages.UserLibraryWizardPage_description_edit);
		}
	}

	private List<CPUserLibraryElement> updateLibraryList() {
		HashSet<String> oldNames= new HashSet<String>();
		HashSet<String> oldCheckedNames= new HashSet<String>();
		List<CPUserLibraryElement> oldElements= fLibrarySelector.getElements();
		for (int i= 0; i < oldElements.size(); i++) {
			CPUserLibraryElement curr= oldElements.get(i);
			oldNames.add(curr.getName());
			if (fLibrarySelector.isChecked(curr)) {
				oldCheckedNames.add(curr.getName());
			}
		}

		ArrayList<CPUserLibraryElement> entriesToCheck= new ArrayList<CPUserLibraryElement>();

		String[] names= JavaCore.getUserLibraryNames();
		Arrays.sort(names, Collator.getInstance());

		ArrayList<CPUserLibraryElement> elements= new ArrayList<CPUserLibraryElement>(names.length);
		for (int i= 0; i < names.length; i++) {
			String curr= names[i];
			IPath path= new Path(JavaCore.USER_LIBRARY_CONTAINER_ID).append(curr);
			try {
				IClasspathContainer container= JavaCore.getClasspathContainer(path, fProject);
				CPUserLibraryElement elem= new CPUserLibraryElement(curr, container, fProject);
				elements.add(elem);
				if (!oldCheckedNames.isEmpty()) {
					if (oldCheckedNames.contains(curr)) {
						entriesToCheck.add(elem);
					}
				} else {
					if (!oldNames.contains(curr)) {
						entriesToCheck.add(elem);
					}
				}
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
				// ignore
			}
		}
		fLibrarySelector.setElements(elements);
		return entriesToCheck;
	}

	private void doDialogFieldChanged(DialogField field) {
		if (field == fLibrarySelector) {
			List<CPUserLibraryElement> list= fLibrarySelector.getCheckedElements();
			if (fIsEditMode) {
				if (list.size() > 1) {
					if (fEditResult != null && list.remove(fEditResult)) {
						fLibrarySelector.setCheckedWithoutUpdate(fEditResult, false);
					}
					fEditResult= list.get(0); // take the first
					for (int i= 1; i < list.size(); i++) { // uncheck the rest
						fLibrarySelector.setCheckedWithoutUpdate(list.get(i), false);
					}
				} else if (list.size() == 1) {
					fEditResult= list.get(0);
				}
			}
			updateStatus(validateSetting(list));
		}
	}

	private IStatus validateSetting(List<CPUserLibraryElement> selected) {
		int nSelected= selected.size();
		if (nSelected == 0) {
			return new StatusInfo(IStatus.ERROR, NewWizardMessages.UserLibraryWizardPage_error_selectentry);
		} else if (fIsEditMode && nSelected > 1) {
			return new StatusInfo(IStatus.ERROR, NewWizardMessages.UserLibraryWizardPage_error_selectonlyone);
		}
		for (int i= 0; i < selected.size(); i++) {
			CPUserLibraryElement curr= selected.get(i);
			if (fUsedPaths.contains(curr.getPath())) {
				return new StatusInfo(IStatus.ERROR, NewWizardMessages.UserLibraryWizardPage_error_alreadyoncp);
			}
		}
		return new StatusInfo();
	}

	private void doButtonPressed(int index) {
		if (index == 0) {
			HashMap<String, String> data= new HashMap<String, String>(3);
			if (fEditResult != null) {
				data.put(UserLibraryPreferencePage.DATA_LIBRARY_TO_SELECT, fEditResult.getName());
			}
			String id= UserLibraryPreferencePage.ID;
			PreferencesUtil.createPreferenceDialogOn(getShell(), id, new String[] { id }, data).open();

			List<CPUserLibraryElement> newEntries= updateLibraryList();
			if (newEntries.size() > 0) {
				if (fIsEditMode) {
					fLibrarySelector.setChecked(newEntries.get(0), true);
				} else {
					fLibrarySelector.setCheckedElements(newEntries);
				}
			}
		} else {
			fLibrarySelector.setCheckedElements(fLibrarySelector.getSelectedElements());
		}
	}

	private void doDoubleClicked(ListDialogField<CPUserLibraryElement> field) {
		if (field == fLibrarySelector) {
			List<CPUserLibraryElement> list= fLibrarySelector.getSelectedElements();
			if (list.size() == 1) {
				CPUserLibraryElement elem= list.get(0);
				boolean state= fLibrarySelector.isChecked(elem);
				if (!state || !fIsEditMode) {
					fLibrarySelector.setChecked(elem, !state);
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
		composite.setFont(parent.getFont());

		LayoutUtil.doDefaultLayout(composite, new DialogField[] { fLibrarySelector }, true, SWT.DEFAULT, SWT.DEFAULT);
		LayoutUtil.setHorizontalGrabbing(fLibrarySelector.getListControl(null));
		Dialog.applyDialogFont(composite);
		setControl(composite);
	}

	/*
	 * @see org.eclipse.jdt.ui.wizards.NewElementWizardPage#setVisible(boolean)
	 * @since 3.7
	 */
	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible) {
			fLibrarySelector.setFocus();
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.IClasspathContainerPage#finish()
	 */
	public boolean finish() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.IClasspathContainerPage#getSelection()
	 */
	public IClasspathEntry getSelection() {
		if (fEditResult != null) {
			if (fOldClasspathEntry != null && fOldClasspathEntry.getPath().equals(fEditResult.getPath())) {
				return JavaCore.newContainerEntry(fEditResult.getPath(), fOldClasspathEntry.getAccessRules(), fOldClasspathEntry.getExtraAttributes(), fOldClasspathEntry.isExported());
			} else {
				return JavaCore.newContainerEntry(fEditResult.getPath(), false);
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension2#getNewContainers()
	 */
	public IClasspathEntry[] getNewContainers() {
		List<CPUserLibraryElement> selected= fLibrarySelector.getCheckedElements();
		IClasspathEntry[] res= new IClasspathEntry[selected.size()];
		for (int i= 0; i < res.length; i++) {
			CPUserLibraryElement curr= selected.get(i);
			res[i]= JavaCore.newContainerEntry(curr.getPath(), false);
		}
		return res;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.IClasspathContainerPage#setSelection(org.eclipse.jdt.core.IClasspathEntry)
	 */
	public void setSelection(IClasspathEntry containerEntry) {
		fOldClasspathEntry= containerEntry;

		updateDescription(containerEntry);
		fIsEditMode= (containerEntry != null);
		if (containerEntry != null) {
			fUsedPaths.remove(containerEntry.getPath());
		}

		String selected= null;
		if (containerEntry != null && containerEntry.getPath().segmentCount() == 2) {
			selected= containerEntry.getPath().segment(1);
		} else {
			// get from dialog store
		}
		updateLibraryList();
		if (selected != null) {
			List<CPUserLibraryElement> elements= fLibrarySelector.getElements();
			for (int i= 0; i < elements.size(); i++) {
				CPUserLibraryElement curr= elements.get(i);
				if (curr.getName().equals(selected)) {
					fLibrarySelector.setChecked(curr, true);
					return;
				}
			}
		}
	}

	private class LibraryListAdapter implements IListAdapter<CPUserLibraryElement>, IDialogFieldListener {

		public LibraryListAdapter() {
		}

		public void dialogFieldChanged(DialogField field) {
			doDialogFieldChanged(field);
		}

		public void customButtonPressed(ListDialogField<CPUserLibraryElement> field, int index) {
			doButtonPressed(index);
		}

		public void selectionChanged(ListDialogField<CPUserLibraryElement> field) {
		}

		public void doubleClicked(ListDialogField<CPUserLibraryElement> field) {
			doDoubleClicked(field);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension#initialize(org.eclipse.jdt.core.IJavaProject, org.eclipse.jdt.core.IClasspathEntry[])
	 */
	public void initialize(IJavaProject project, IClasspathEntry[] currentEntries) {
		for (int i= 0; i < currentEntries.length; i++) {
			IClasspathEntry curr= currentEntries[i];
			if (curr.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
				fUsedPaths.add(curr.getPath());
			}
		}
	}
}

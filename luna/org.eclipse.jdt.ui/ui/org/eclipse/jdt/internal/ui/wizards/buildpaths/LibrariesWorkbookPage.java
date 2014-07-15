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
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;

import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.jdt.ui.wizards.BuildPathDialogAccess;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.JarImportWizardAction;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.jarimport.JarImportWizard;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.CheckedListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ITreeListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.TreeListDialogField;


public class LibrariesWorkbookPage extends BuildPathBasePage {

	private final ListDialogField<CPListElement> fClassPathList;
	private IJavaProject fCurrJProject;

	private final TreeListDialogField<CPListElement> fLibrariesList;

	private Control fSWTControl;
	private final IWorkbenchPreferenceContainer fPageContainer;

	private final int IDX_ADDJAR= 0;
	private final int IDX_ADDEXT= 1;
	private final int IDX_ADDVAR= 2;
	private final int IDX_ADDLIB= 3;
	private final int IDX_ADDFOL= 4;
	private final int IDX_ADDEXTFOL= 5;

	private final int IDX_EDIT= 7;
	private final int IDX_REMOVE= 8;

	private final int IDX_REPLACE= 10;

	public LibrariesWorkbookPage(CheckedListDialogField<CPListElement> classPathList, IWorkbenchPreferenceContainer pageContainer) {
		fClassPathList= classPathList;
		fPageContainer= pageContainer;
		fSWTControl= null;

		String[] buttonLabels= new String[] {
			NewWizardMessages.LibrariesWorkbookPage_libraries_addjar_button,
			NewWizardMessages.LibrariesWorkbookPage_libraries_addextjar_button,
			NewWizardMessages.LibrariesWorkbookPage_libraries_addvariable_button,
			NewWizardMessages.LibrariesWorkbookPage_libraries_addlibrary_button,
			NewWizardMessages.LibrariesWorkbookPage_libraries_addclassfolder_button,
			NewWizardMessages.LibrariesWorkbookPage_libraries_addextfolder_button,
			/* */ null,
			NewWizardMessages.LibrariesWorkbookPage_libraries_edit_button,
			NewWizardMessages.LibrariesWorkbookPage_libraries_remove_button,
			/* */ null,
			NewWizardMessages.LibrariesWorkbookPage_libraries_replace_button
		};

		LibrariesAdapter adapter= new LibrariesAdapter();

		fLibrariesList= new TreeListDialogField<CPListElement>(adapter, buttonLabels, new CPListLabelProvider());
		fLibrariesList.setDialogFieldListener(adapter);
		fLibrariesList.setLabelText(NewWizardMessages.LibrariesWorkbookPage_libraries_label);

		fLibrariesList.enableButton(IDX_REMOVE, false);
		fLibrariesList.enableButton(IDX_EDIT, false);
		fLibrariesList.enableButton(IDX_REPLACE, false);

		fLibrariesList.setViewerComparator(new CPListElementSorter());

	}

	@Override
	public void init(IJavaProject jproject) {
		fCurrJProject= jproject;
		if (Display.getCurrent() != null) {
			updateLibrariesList();
		} else {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					updateLibrariesList();
				}
			});
		}
	}

	private void updateLibrariesList() {
		List<CPListElement> cpelements= fClassPathList.getElements();
		List<CPListElement> libelements= new ArrayList<CPListElement>(cpelements.size());

		int nElements= cpelements.size();
		for (int i= 0; i < nElements; i++) {
			CPListElement cpe= cpelements.get(i);
			if (isEntryKind(cpe.getEntryKind())) {
				libelements.add(cpe);
			}
		}
		fLibrariesList.setElements(libelements);
	}

	// -------- UI creation

	@Override
	public Control getControl(Composite parent) {
		PixelConverter converter= new PixelConverter(parent);

		Composite composite= new Composite(parent, SWT.NONE);

		LayoutUtil.doDefaultLayout(composite, new DialogField[] { fLibrariesList }, true, SWT.DEFAULT, SWT.DEFAULT);
		LayoutUtil.setHorizontalGrabbing(fLibrariesList.getTreeControl(null));

		int buttonBarWidth= converter.convertWidthInCharsToPixels(24);
		fLibrariesList.setButtonsMinWidth(buttonBarWidth);

		fLibrariesList.setViewerComparator(new CPListElementSorter());

		fSWTControl= composite;

		return composite;
	}

	private Shell getShell() {
		if (fSWTControl != null) {
			return fSWTControl.getShell();
		}
		return JavaPlugin.getActiveWorkbenchShell();
	}


	private class LibrariesAdapter implements IDialogFieldListener, ITreeListAdapter<CPListElement> {

		private final Object[] EMPTY_ARR= new Object[0];

		// -------- IListAdapter --------
		public void customButtonPressed(TreeListDialogField<CPListElement> field, int index) {
			libaryPageCustomButtonPressed(field, index);
		}

		public void selectionChanged(TreeListDialogField<CPListElement> field) {
			libaryPageSelectionChanged(field);
		}

		public void doubleClicked(TreeListDialogField<CPListElement> field) {
			libaryPageDoubleClicked(field);
		}

		public void keyPressed(TreeListDialogField<CPListElement> field, KeyEvent event) {
			libaryPageKeyPressed(field, event);
		}

		public Object[] getChildren(TreeListDialogField<CPListElement> field, Object element) {
			if (element instanceof CPListElement) {
				return ((CPListElement) element).getChildren(false);
			} else if (element instanceof CPListElementAttribute) {
				CPListElementAttribute attribute= (CPListElementAttribute) element;
				if (CPListElement.ACCESSRULES.equals(attribute.getKey())) {
					return (IAccessRule[]) attribute.getValue();
				}
			}
			return EMPTY_ARR;
		}

		public Object getParent(TreeListDialogField<CPListElement> field, Object element) {
			if (element instanceof CPListElementAttribute) {
				return ((CPListElementAttribute) element).getParent();
			}
			return null;
		}

		public boolean hasChildren(TreeListDialogField<CPListElement> field, Object element) {
			return getChildren(field, element).length > 0;
		}

		// ---------- IDialogFieldListener --------

		public void dialogFieldChanged(DialogField field) {
			libaryPageDialogFieldChanged(field);
		}
	}

	/**
	 * A button has been pressed.
	 *
	 * @param field the dialog field containing the button
	 * @param index the index of the button
	 */
	private void libaryPageCustomButtonPressed(DialogField field, int index) {
		CPListElement[] libentries= null;
		switch (index) {
		case IDX_ADDJAR: /* add jar */
			libentries= openJarFileDialog(null);
			break;
		case IDX_ADDEXT: /* add external jar */
			libentries= openExtJarFileDialog(null);
			break;
		case IDX_ADDVAR: /* add variable */
			libentries= openVariableSelectionDialog(null);
			break;
		case IDX_ADDLIB: /* add library */
			libentries= openContainerSelectionDialog(null);
			break;
		case IDX_ADDFOL: /* add folder */
			libentries= openClassFolderDialog(null);
			break;
		case IDX_ADDEXTFOL: /* add external folder */
			libentries= openExternalClassFolderDialog(null);
			break;
		case IDX_EDIT: /* edit */
			editEntry();
			return;
		case IDX_REMOVE: /* remove */
			removeEntry();
			return;
		case IDX_REPLACE: /* replace */
			replaceJarFile();
			return;
		}
		if (libentries != null) {
			int nElementsChosen= libentries.length;
			// remove duplicates
			List<?> cplist= fLibrariesList.getElements();
			List<CPListElement> elementsToAdd= new ArrayList<CPListElement>(nElementsChosen);

			for (int i= 0; i < nElementsChosen; i++) {
				CPListElement curr= libentries[i];
				if (!cplist.contains(curr) && !elementsToAdd.contains(curr)) {
					elementsToAdd.add(curr);
					curr.setAttribute(CPListElement.SOURCEATTACHMENT, BuildPathSupport.guessSourceAttachment(curr));
					curr.setAttribute(CPListElement.JAVADOC, BuildPathSupport.guessJavadocLocation(curr));
				}
			}
			if (!elementsToAdd.isEmpty() && (index == IDX_ADDFOL)) {
				askForAddingExclusionPatternsDialog(elementsToAdd);
			}

			fLibrariesList.addElements(elementsToAdd);
			if (index == IDX_ADDLIB || index == IDX_ADDVAR) {
				fLibrariesList.refresh();
			}
			fLibrariesList.postSetSelection(new StructuredSelection(libentries));
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathBasePage#addElement(org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement)
	 */
	@Override
	public void addElement(CPListElement element) {
		fLibrariesList.addElement(element);
		fLibrariesList.postSetSelection(new StructuredSelection(element));
	}

	private void askForAddingExclusionPatternsDialog(List<CPListElement> newEntries) {
		HashSet<CPListElement> modified= new HashSet<CPListElement>();
		List<CPListElement> existing= fClassPathList.getElements();
		fixNestingConflicts(newEntries.toArray(new CPListElement[newEntries.size()]), existing.toArray(new CPListElement[existing.size()]), modified);
		if (!modified.isEmpty()) {
			String title= NewWizardMessages.LibrariesWorkbookPage_exclusion_added_title;
			String message= NewWizardMessages.LibrariesWorkbookPage_exclusion_added_message;
			MessageDialog.openInformation(getShell(), title, message);
		}
	}

	protected void libaryPageDoubleClicked(TreeListDialogField<CPListElement> field) {
		List<?> selection= field.getSelectedElements();
		if (canEdit(selection)) {
			editEntry();
		}
	}

	protected void libaryPageKeyPressed(TreeListDialogField<CPListElement> field, KeyEvent event) {
		if (field == fLibrariesList) {
			if (event.character == SWT.DEL && event.stateMask == 0) {
				List<?> selection= field.getSelectedElements();
				if (canRemove(selection)) {
					removeEntry();
				}
			}
		}
	}

	private void replaceJarFile() {
		final IPackageFragmentRoot root= getSelectedPackageFragmentRoot();
		if (root != null) {
			final IImportWizard wizard= new JarImportWizard(false);
			wizard.init(PlatformUI.getWorkbench(), new StructuredSelection(root));
			final WizardDialog dialog= new WizardDialog(getShell(), wizard);
			dialog.create();
			dialog.getShell().setSize(Math.max(JarImportWizardAction.SIZING_WIZARD_WIDTH, dialog.getShell().getSize().x), JarImportWizardAction.SIZING_WIZARD_HEIGHT);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(dialog.getShell(), IJavaHelpContextIds.JARIMPORT_WIZARD_PAGE);
			dialog.open();
		}
	}

	private IPackageFragmentRoot getSelectedPackageFragmentRoot() {
		final List<Object> elements= fLibrariesList.getSelectedElements();
		if (elements.size() == 1) {
			final Object object= elements.get(0);
			if (object instanceof CPListElement) {
				final CPListElement element= (CPListElement) object;
				final IClasspathEntry entry= element.getClasspathEntry();
				if (JarImportWizard.isValidClassPathEntry(entry)) {
					final IJavaProject project= element.getJavaProject();
					if (project != null) {
						try {
							final IPackageFragmentRoot[] roots= project.getPackageFragmentRoots();
							for (int index= 0; index < roots.length; index++) {
								if (entry.equals(roots[index].getRawClasspathEntry()))
									return roots[index];
							}
						} catch (JavaModelException exception) {
							JavaPlugin.log(exception);
						}
					}
				}
			}
		}
		return null;
	}

	private void removeEntry() {
		List<Object> selElements= fLibrariesList.getSelectedElements();
		HashMap<CPListElement, HashSet<String>> containerEntriesToUpdate= new HashMap<CPListElement, HashSet<String>>();
		for (int i= selElements.size() - 1; i >= 0 ; i--) {
			Object elem= selElements.get(i);
			if (elem instanceof CPListElementAttribute) {
				CPListElementAttribute attrib= (CPListElementAttribute) elem;
				String key= attrib.getKey();
				if (attrib.isBuiltIn()) {
					Object value= null;
					if (key.equals(CPListElement.ACCESSRULES)) {
						value= new IAccessRule[0];
					}
					attrib.setValue(value);
				} else {
					removeCustomAttribute(attrib);
				}
				selElements.remove(i);
				if (attrib.getParent().getParentContainer() instanceof CPListElement) { // inside a container: apply changes right away
					CPListElement containerEntry= attrib.getParent();
					HashSet<String> changedAttributes= containerEntriesToUpdate.get(containerEntry);
					if (changedAttributes == null) {
						changedAttributes= new HashSet<String>();
						containerEntriesToUpdate.put(containerEntry, changedAttributes);
					}
					changedAttributes.add(key); // collect the changed attributes
				}
			}
		}
		if (selElements.isEmpty()) {
			fLibrariesList.refresh();
			fClassPathList.dialogFieldChanged(); // validate
		} else {
			fLibrariesList.removeElements(selElements);
		}
		for (Iterator<Map.Entry<CPListElement, HashSet<String>>> iter= containerEntriesToUpdate.entrySet().iterator(); iter.hasNext();) {
			Map.Entry<CPListElement, HashSet<String>> entry= iter.next();
			CPListElement curr= entry.getKey();
			HashSet<String> attribs= entry.getValue();
			String[] changedAttributes= attribs.toArray(new String[attribs.size()]);
			IClasspathEntry changedEntry= curr.getClasspathEntry();
			updateContainerEntry(changedEntry, changedAttributes, fCurrJProject, ((CPListElement) curr.getParentContainer()).getPath());
		}
	}

	private boolean canRemove(List<?> selElements) {
		if (selElements.size() == 0) {
			return false;
		}
		for (int i= 0; i < selElements.size(); i++) {
			Object elem= selElements.get(i);
			if (elem instanceof CPListElementAttribute) {
				CPListElementAttribute attrib= (CPListElementAttribute) elem;
				if (attrib.isNonModifiable()) {
					return false;
				}
				if (attrib.isBuiltIn()) {
					if (attrib.getParent().isInContainer(JavaRuntime.JRE_CONTAINER) && CPListElement.ACCESSRULES.equals(attrib.getKey())) {
						return false; // workaround for 166519 until we have full story
					}
					if (attrib.getKey().equals(CPListElement.ACCESSRULES)) {
						return ((IAccessRule[]) attrib.getValue()).length > 0;
					}
					if (attrib.getValue() == null) {
						return false;
					}
				} else {
					if (!canRemoveCustomAttribute(attrib)) {
						return false;
					}
				}
			} else if (elem instanceof CPListElement) {
				CPListElement curr= (CPListElement) elem;
				if (curr.getParentContainer() != null) {
					return false;
				}
			} else { // unknown element
				return false;
			}
		}
		return true;
	}

	/**
	 * Method editEntry.
	 */
	private void editEntry() {
		List<?> selElements= fLibrariesList.getSelectedElements();
		if (selElements.size() != 1) {
			return;
		}
		Object elem= selElements.get(0);
		if (fLibrariesList.getIndexOfElement(elem) != -1) {
			editElementEntry((CPListElement) elem);
		} else if (elem instanceof CPListElementAttribute) {
			editAttributeEntry((CPListElementAttribute) elem);
		}
	}

	private void editAttributeEntry(CPListElementAttribute elem) {
		String key= elem.getKey();
		CPListElement selElement= elem.getParent();

		CPListElementAttribute[] allAttributes= selElement.getAllAttributes();
		boolean canEditEncoding= false;
		for (int i= 0; i < allAttributes.length; i++) {
			if (CPListElement.SOURCE_ATTACHMENT_ENCODING.equals(allAttributes[i].getKey())) {
				canEditEncoding= !(allAttributes[i].isNonModifiable() || allAttributes[i].isNotSupported());
			}
		}
		if (key.equals(CPListElement.SOURCEATTACHMENT)) {
			IClasspathEntry result= BuildPathDialogAccess.configureSourceAttachment(getShell(), selElement.getClasspathEntry(), canEditEncoding);
			if (result != null) {
				selElement.setAttribute(CPListElement.SOURCEATTACHMENT, result.getSourceAttachmentPath());
				selElement.setAttribute(CPListElement.SOURCE_ATTACHMENT_ENCODING, SourceAttachmentBlock.getSourceAttachmentEncoding(result));
				String[] changedAttributes= { CPListElement.SOURCEATTACHMENT, CPListElement.SOURCE_ATTACHMENT_ENCODING };
				attributeUpdated(selElement, changedAttributes);
				fLibrariesList.refresh(elem);
				fLibrariesList.update(selElement); // image
				fClassPathList.refresh(); // images
				updateEnabledState();
			}
		} else if (key.equals(CPListElement.ACCESSRULES)) {
			AccessRulesDialog dialog= new AccessRulesDialog(getShell(), selElement, fCurrJProject, fPageContainer != null);
			int res= dialog.open();
			if (res == Window.OK || res == AccessRulesDialog.SWITCH_PAGE) {
				selElement.setAttribute(CPListElement.ACCESSRULES, dialog.getAccessRules());
				String[] changedAttributes= { CPListElement.ACCESSRULES };
				attributeUpdated(selElement, changedAttributes);

				fLibrariesList.refresh(elem);
				fClassPathList.dialogFieldChanged(); // validate
				updateEnabledState();

				if (res == AccessRulesDialog.SWITCH_PAGE) { // switch after updates and validation
					dialog.performPageSwitch(fPageContainer);
				}
			}
		} else {
			if (editCustomAttribute(getShell(), elem)) {
				String[] changedAttributes= { key };
				attributeUpdated(selElement, changedAttributes);
				fLibrariesList.refresh(elem);
				fClassPathList.dialogFieldChanged(); // validate
				updateEnabledState();
			}
		}
	}

	private void attributeUpdated(CPListElement selElement, String[] changedAttributes) {
		Object parentContainer= selElement.getParentContainer();
		if (parentContainer instanceof CPListElement) { // inside a container: apply changes right away
			IClasspathEntry updatedEntry= selElement.getClasspathEntry();
			updateContainerEntry(updatedEntry, changedAttributes, fCurrJProject, ((CPListElement) parentContainer).getPath());
		}
	}

	private void updateContainerEntry(final IClasspathEntry newEntry, final String[] changedAttributes, final IJavaProject jproject, final IPath containerPath) {
		try {
			IWorkspaceRunnable runnable= new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					BuildPathSupport.modifyClasspathEntry(null, newEntry, changedAttributes, jproject, containerPath, false, monitor);
				}
			};
			PlatformUI.getWorkbench().getProgressService().run(true, true, new WorkbenchRunnableAdapter(runnable));

		} catch (InvocationTargetException e) {
			String title= NewWizardMessages.LibrariesWorkbookPage_configurecontainer_error_title;
			String message= NewWizardMessages.LibrariesWorkbookPage_configurecontainer_error_message;
			ExceptionHandler.handle(e, getShell(), title, message);
		} catch (InterruptedException e) {
			//
		}
	}

	private void editElementEntry(CPListElement elem) {
		CPListElement[] res= null;

		switch (elem.getEntryKind()) {
		case IClasspathEntry.CPE_CONTAINER:
			res= openContainerSelectionDialog(elem);
			break;
		case IClasspathEntry.CPE_LIBRARY:
			IResource resource= elem.getResource();
			if (resource == null) {
				File file= elem.getPath().toFile();
				if (file.isDirectory()) {
					res= openExternalClassFolderDialog(elem);
				} else {
					res= openExtJarFileDialog(elem);
				}
			} else if (resource.getType() == IResource.FOLDER) {
				if (resource.exists()) {
					res= openClassFolderDialog(elem);
				} else {
					res= openNewClassFolderDialog(elem);
				}
			} else if (resource.getType() == IResource.FILE) {
				res= openJarFileDialog(elem);
			}
			break;
		case IClasspathEntry.CPE_VARIABLE:
			res= openVariableSelectionDialog(elem);
			break;
		}
		if (res != null && res.length > 0) {
			CPListElement curr= res[0];
			curr.setExported(elem.isExported());
			curr.setAttributesFromExisting(elem);
			fLibrariesList.replaceElement(elem, curr);
			if (elem.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
				fLibrariesList.refresh();
			}
		}

	}

	/**
	 * @param field  the dilaog field
	 */
	private void libaryPageSelectionChanged(DialogField field) {
		updateEnabledState();
	}

	private void updateEnabledState() {
		List<?> selElements= fLibrariesList.getSelectedElements();
		fLibrariesList.enableButton(IDX_EDIT, canEdit(selElements));
		fLibrariesList.enableButton(IDX_REMOVE, canRemove(selElements));
		fLibrariesList.enableButton(IDX_REPLACE, getSelectedPackageFragmentRoot() != null);

		boolean noAttributes= containsOnlyTopLevelEntries(selElements);
		fLibrariesList.enableButton(IDX_ADDEXT, noAttributes);
		fLibrariesList.enableButton(IDX_ADDFOL, noAttributes);
		fLibrariesList.enableButton(IDX_ADDEXTFOL, noAttributes);
		fLibrariesList.enableButton(IDX_ADDJAR, noAttributes);
		fLibrariesList.enableButton(IDX_ADDLIB, noAttributes);
		fLibrariesList.enableButton(IDX_ADDVAR, noAttributes);
	}

	private boolean canEdit(List<?> selElements) {
		if (selElements.size() != 1) {
			return false;
		}
		Object elem= selElements.get(0);
		if (elem instanceof CPListElement) {
			CPListElement curr= (CPListElement) elem;
			return !(curr.getResource() instanceof IFolder) && curr.getParentContainer() == null;
		}
		if (elem instanceof CPListElementAttribute) {
			CPListElementAttribute attrib= (CPListElementAttribute) elem;
			if (attrib.isNonModifiable()) {
				return false;
			}
			if (!attrib.isBuiltIn()) {
				return canEditCustomAttribute(attrib);
			}
			return true;
		}
		return false;
	}

	/**
	 * @param field the dialog field
	 */
	private void libaryPageDialogFieldChanged(DialogField field) {
		if (fCurrJProject != null) {
			// already initialized
			updateClasspathList();
		}
	}

	private void updateClasspathList() {
		List<CPListElement> projelements= fLibrariesList.getElements();

		List<CPListElement> cpelements= fClassPathList.getElements();
		int nEntries= cpelements.size();
		// backwards, as entries will be deleted
		int lastRemovePos= nEntries;
		for (int i= nEntries - 1; i >= 0; i--) {
			CPListElement cpe= cpelements.get(i);
			int kind= cpe.getEntryKind();
			if (isEntryKind(kind)) {
				if (!projelements.remove(cpe)) {
					cpelements.remove(i);
					lastRemovePos= i;
				}
			}
		}

		cpelements.addAll(lastRemovePos, projelements);

		if (lastRemovePos != nEntries || !projelements.isEmpty()) {
			fClassPathList.setElements(cpelements);
		}
	}


	private CPListElement[] openNewClassFolderDialog(CPListElement existing) {
		String title= (existing == null) ? NewWizardMessages.LibrariesWorkbookPage_NewClassFolderDialog_new_title : NewWizardMessages.LibrariesWorkbookPage_NewClassFolderDialog_edit_title;
		IProject currProject= fCurrJProject.getProject();

		NewContainerDialog dialog= new NewContainerDialog(getShell(), title, currProject, getUsedContainers(existing), existing);
		IPath projpath= currProject.getFullPath();
		dialog.setMessage(Messages.format(NewWizardMessages.LibrariesWorkbookPage_NewClassFolderDialog_description, BasicElementLabels.getPathLabel(projpath, false)));
		if (dialog.open() == Window.OK) {
			IFolder folder= dialog.getFolder();
			return new CPListElement[] { newCPLibraryElement(folder) };
		}
		return null;
	}


	private CPListElement[] openClassFolderDialog(CPListElement existing) {
		if (existing == null) {
			IPath[] selected= BuildPathDialogAccess.chooseClassFolderEntries(getShell(), fCurrJProject.getPath(), getUsedContainers(existing));
			if (selected != null) {
				IWorkspaceRoot root= fCurrJProject.getProject().getWorkspace().getRoot();
				ArrayList<CPListElement> res= new ArrayList<CPListElement>();
				for (int i= 0; i < selected.length; i++) {
					IPath curr= selected[i];
					IResource resource= root.findMember(curr);
					if (resource instanceof IContainer) {
						res.add(newCPLibraryElement(resource));
					}
				}
				return res.toArray(new CPListElement[res.size()]);
			}
		} else {
			// disabled
		}
		return null;
	}

	private CPListElement[] openJarFileDialog(CPListElement existing) {
		IWorkspaceRoot root= fCurrJProject.getProject().getWorkspace().getRoot();

		if (existing == null) {
			IPath[] selected= BuildPathDialogAccess.chooseJAREntries(getShell(), fCurrJProject.getPath(), getUsedJARFiles(existing));
			if (selected != null) {
				ArrayList<CPListElement> res= new ArrayList<CPListElement>();

				for (int i= 0; i < selected.length; i++) {
					IPath curr= selected[i];
					IResource resource= root.findMember(curr);
					if (resource instanceof IFile) {
						res.add(newCPLibraryElement(resource));
					}
				}
				return res.toArray(new CPListElement[res.size()]);
			}
		} else {
			IPath configured= BuildPathDialogAccess.configureJAREntry(getShell(), existing.getPath(), getUsedJARFiles(existing));
			if (configured != null) {
				IResource resource= root.findMember(configured);
				if (resource instanceof IFile) {
					return new CPListElement[] { newCPLibraryElement(resource) };
				}
			}
		}
		return null;
	}

	private IPath[] getUsedContainers(CPListElement existing) {
		ArrayList<IPath> res= new ArrayList<IPath>();
		if (fCurrJProject.exists()) {
			try {
				IPath outputLocation= fCurrJProject.getOutputLocation();
				if (outputLocation != null && outputLocation.segmentCount() > 1) { // != Project
					res.add(outputLocation);
				}
			} catch (JavaModelException e) {
				// ignore it here, just log
				JavaPlugin.log(e.getStatus());
			}
		}

		List<CPListElement> cplist= fLibrariesList.getElements();
		for (int i= 0; i < cplist.size(); i++) {
			CPListElement elem= cplist.get(i);
			if (elem.getEntryKind() == IClasspathEntry.CPE_LIBRARY && (elem != existing)) {
				IResource resource= elem.getResource();
				if (resource instanceof IContainer && !resource.equals(existing)) {
					res.add(resource.getFullPath());
				}
			}
		}
		return res.toArray(new IPath[res.size()]);
	}

	private IPath[] getUsedJARFiles(CPListElement existing) {
		List<IPath> res= new ArrayList<IPath>();
		List<CPListElement> cplist= fLibrariesList.getElements();
		for (int i= 0; i < cplist.size(); i++) {
			CPListElement elem= cplist.get(i);
			if (elem.getEntryKind() == IClasspathEntry.CPE_LIBRARY && (elem != existing)) {
				IResource resource= elem.getResource();
				if (resource instanceof IFile) {
					res.add(resource.getFullPath());
				}
			}
		}
		return res.toArray(new IPath[res.size()]);
	}

	private CPListElement newCPLibraryElement(IResource res) {
		return new CPListElement(fCurrJProject, IClasspathEntry.CPE_LIBRARY, res.getFullPath(), res);
	}

	private CPListElement[] openExtJarFileDialog(CPListElement existing) {
		if (existing == null) {
			IPath[] selected= BuildPathDialogAccess.chooseExternalJAREntries(getShell());
			if (selected != null) {
				ArrayList<CPListElement> res= new ArrayList<CPListElement>();
				for (int i= 0; i < selected.length; i++) {
					res.add(new CPListElement(fCurrJProject, IClasspathEntry.CPE_LIBRARY, selected[i], null));
				}
				return res.toArray(new CPListElement[res.size()]);
			}
		} else {
			IPath path;
			IPackageFragmentRoot[] roots= existing.getJavaProject().findPackageFragmentRoots(existing.getClasspathEntry());
			if (roots.length == 1)
				path= roots[0].getPath();
			else
				path= existing.getPath();
			IPath configured= BuildPathDialogAccess.configureExternalJAREntry(getShell(), path);
			if (configured != null) {
				return new CPListElement[] { new CPListElement(fCurrJProject, IClasspathEntry.CPE_LIBRARY, configured, null) };
			}
		}
		return null;
	}

	private CPListElement[] openExternalClassFolderDialog(CPListElement existing) {
		if (existing == null) {
			IPath[] selected= BuildPathDialogAccess.chooseExternalClassFolderEntries(getShell());
			if (selected != null) {
				ArrayList<CPListElement> res= new ArrayList<CPListElement>();
				for (int i= 0; i < selected.length; i++) {
					res.add(new CPListElement(fCurrJProject, IClasspathEntry.CPE_LIBRARY, selected[i], null));
				}
				return res.toArray(new CPListElement[res.size()]);
			}
		} else {
			IPath configured= BuildPathDialogAccess.configureExternalClassFolderEntries(getShell(), existing.getPath());
			if (configured != null) {
				return new CPListElement[] { new CPListElement(fCurrJProject, IClasspathEntry.CPE_LIBRARY, configured, null) };
			}
		}
		return null;
	}

	private CPListElement[] openVariableSelectionDialog(CPListElement existing) {
		List<CPListElement> existingElements= fLibrariesList.getElements();
		ArrayList<IPath> existingPaths= new ArrayList<IPath>(existingElements.size());
		for (int i= 0; i < existingElements.size(); i++) {
			CPListElement elem= existingElements.get(i);
			if (elem.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
				existingPaths.add(elem.getPath());
			}
		}
		IPath[] existingPathsArray= existingPaths.toArray(new IPath[existingPaths.size()]);

		if (existing == null) {
			IPath[] paths= BuildPathDialogAccess.chooseVariableEntries(getShell(), existingPathsArray);
			if (paths != null) {
				ArrayList<CPListElement> result= new ArrayList<CPListElement>();
				for (int i = 0; i < paths.length; i++) {
					IPath path= paths[i];
					CPListElement elem= createCPVariableElement(path);
					if (!existingElements.contains(elem)) {
						result.add(elem);
					}
				}
				return result.toArray(new CPListElement[result.size()]);
			}
		} else {
			IPath path= BuildPathDialogAccess.configureVariableEntry(getShell(), existing.getPath(), existingPathsArray);
			if (path != null) {
				return new CPListElement[] { createCPVariableElement(path) };
			}
		}
		return null;
	}

	private CPListElement createCPVariableElement(IPath path) {
		CPListElement elem= new CPListElement(fCurrJProject, IClasspathEntry.CPE_VARIABLE, path, null);
		IPath resolvedPath= JavaCore.getResolvedVariablePath(path);
		elem.setIsMissing((resolvedPath == null) || !resolvedPath.toFile().exists());
		return elem;
	}

	private CPListElement[] openContainerSelectionDialog(CPListElement existing) {
		if (existing == null) {
			IClasspathEntry[] created= BuildPathDialogAccess.chooseContainerEntries(getShell(), fCurrJProject, getRawClasspath());
			if (created != null) {
				CPListElement[] res= new CPListElement[created.length];
				for (int i= 0; i < res.length; i++) {
					res[i]= CPListElement.create(created[i], true, fCurrJProject);
				}
				return res;
			}
		} else {
			IClasspathEntry existingEntry= existing.getClasspathEntry();
			IClasspathEntry created= BuildPathDialogAccess.configureContainerEntry(getShell(), existingEntry, fCurrJProject, getRawClasspath());
			if (created != null) {
				CPListElement elem= new CPListElement(null, fCurrJProject, IClasspathEntry.CPE_CONTAINER, created.getPath(), ! created.equals(existingEntry), null, null);
				return new CPListElement[] { elem };
			}
		}
		return null;
	}

	private IClasspathEntry[] getRawClasspath() {
		IClasspathEntry[] currEntries= new IClasspathEntry[fClassPathList.getSize()];
		for (int i= 0; i < currEntries.length; i++) {
			CPListElement curr= fClassPathList.getElement(i);
			currEntries[i]= curr.getClasspathEntry();
		}
		return currEntries;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathBasePage#isEntryKind(int)
	 */
	@Override
	public boolean isEntryKind(int kind) {
		return kind == IClasspathEntry.CPE_LIBRARY || kind == IClasspathEntry.CPE_VARIABLE || kind == IClasspathEntry.CPE_CONTAINER;
	}

	/*
	 * @see BuildPathBasePage#getSelection
	 */
	@Override
	public List<?> getSelection() {
		return fLibrariesList.getSelectedElements();
	}

	/*
	 * @see BuildPathBasePage#setSelection
	 */
	@Override
	public void setSelection(List<?> selElements, boolean expand) {
		fLibrariesList.selectElements(new StructuredSelection(selElements));
		if (expand) {
			for (int i= 0; i < selElements.size(); i++) {
				fLibrariesList.expandElement(selElements.get(i), 1);
			}
		}
	}

	/**
     * {@inheritDoc}
     */
    @Override
	public void setFocus() {
    	fLibrariesList.setFocus();
    }

}

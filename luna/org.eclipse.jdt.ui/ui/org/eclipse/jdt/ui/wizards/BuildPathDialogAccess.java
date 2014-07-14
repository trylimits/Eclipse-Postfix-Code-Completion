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
package org.eclipse.jdt.ui.wizards;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.window.Window;

import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceComparator;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.corext.javadoc.JavaDocLocations;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IUIConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.FilteredElementTreeSelectionDialog;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.ArchiveFileFilter;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.ClasspathContainerWizard;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.EditVariableEntryDialog;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.JavadocLocationDialog;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.MultipleFolderSelectionDialog;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.NewVariableEntryDialog;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.SourceAttachmentDialog;

/**
 * Class that gives access to dialogs used by the Java build path page to configure classpath entries
 * and properties of classpath entries.
 * Static methods are provided to show dialogs for:
 * <ul>
 *  <li> configuration of source attachments</li>
 *  <li> configuration of Javadoc locations</li>
 *  <li> configuration and selection of classpath variable entries</li>
 *  <li> configuration and selection of classpath container entries</li>
 *  <li> configuration and selection of JAR and external JAR entries</li>
 *  <li> selection of class and source folders</li>
 * </ul>
 * <p>
 * This class is not intended to be instantiated or subclassed by clients.
 * </p>
 * @since 3.0
 *
 * @noinstantiate This class is not intended to be instantiated by clients.
 */
public final class BuildPathDialogAccess {

	private BuildPathDialogAccess() {
		// do not instantiate
	}

	/**
	 * Shows the UI for configuring source attachments, with editing of source attachment encoding
	 * disabled. <code>null</code> is returned if the user cancels the dialog. The dialog does not
	 * apply any changes.
	 * 
	 * @param shell The parent shell for the dialog
	 * @param initialEntry The entry to edit. The kind of the classpath entry must be either
	 *            <code>IClasspathEntry.CPE_LIBRARY</code> or
	 *            <code>IClasspathEntry.CPE_VARIABLE</code>.
	 * @return Returns the resulting classpath entry containing a potentially modified source
	 *         attachment path, source attachment root and source attachment encoding. The resulting
	 *         entry can be used to replace the original entry on the classpath. Note that the
	 *         dialog does not make any changes on the passed entry nor on the classpath that
	 *         contains it.
	 */
	public static IClasspathEntry configureSourceAttachment(Shell shell, IClasspathEntry initialEntry) {
		return configureSourceAttachment(shell, initialEntry, false);
	}

	/**
	 * Shows the UI for configuring source attachments. The source attachment encoding can be edited
	 * depending on the parameter <code>canEditEncoding</code>. <code>null</code> is returned if the
	 * user cancels the dialog. The dialog does not apply any changes.
	 * 
	 * @param shell The parent shell for the dialog
	 * @param initialEntry The entry to edit. The kind of the classpath entry must be either
	 *            <code>IClasspathEntry.CPE_LIBRARY</code> or
	 *            <code>IClasspathEntry.CPE_VARIABLE</code>.
	 * @param canEditEncoding whether the source attachment encoding can be edited
	 * @return Returns the resulting classpath entry containing a potentially modified source
	 *         attachment path, source attachment root and source attachment encoding. The resulting
	 *         entry can be used to replace the original entry on the classpath. Note that the
	 *         dialog does not make any changes on the passed entry nor on the classpath that
	 *         contains it.
	 * @since 3.8
	 */
	public static IClasspathEntry configureSourceAttachment(Shell shell, IClasspathEntry initialEntry, boolean canEditEncoding) {
		if (initialEntry == null) {
			throw new IllegalArgumentException();
		}
		int entryKind= initialEntry.getEntryKind();
		if (entryKind != IClasspathEntry.CPE_LIBRARY && entryKind != IClasspathEntry.CPE_VARIABLE) {
			throw new IllegalArgumentException();
		}

		SourceAttachmentDialog dialog=  new SourceAttachmentDialog(shell, initialEntry, canEditEncoding);
		if (dialog.open() == Window.OK) {
			return dialog.getResult();
		}
		return null;
	}
	
	/**
	 * Shows the UI for configuring a javadoc location. <code>null</code> is returned
	 * if the user cancels the dialog. If OK is pressed, an array of length 1 containing the configured URL is
	 * returned. Note that the configured URL can be <code>null</code> when the user
	 * wishes to have no URL location specified. The dialog does not apply any changes.
	 * Use {@link org.eclipse.jdt.ui.JavaUI} to access and configure
	 * Javadoc locations.
	 *
	 * @param shell The parent shell for the dialog.
	 * @param libraryName Name of of the library to which configured javadoc location belongs.
	 * @param initialURL The initial URL or <code>null</code>.
	 * @return Returns an array of size 1 that contains the resulting javadoc location or
	 * <code>null</code> if the dialog has been canceled. Note that the configured URL can be <code>null</code> when the user
	 * wishes to have no URL location specified.
	 */
	public static URL[] configureJavadocLocation(Shell shell, String libraryName, URL initialURL) {
		if (libraryName == null) {
			throw new IllegalArgumentException();
		}
		
		if (initialURL != null) {
			try {
				initialURL.toURI();
			} catch (URISyntaxException e) {
				initialURL= JavaDocLocations.parseURL(initialURL.toExternalForm());
			}
		}

		JavadocLocationDialog dialog=  new JavadocLocationDialog(shell, libraryName, initialURL);
		if (dialog.open() == Window.OK) {
			return new URL[] { dialog.getResult() };
		}
		return null;
	}

	/**
	 * Shows the UI for configuring a javadoc location attribute of the classpath entry. <code>null</code> is returned
	 * if the user cancels the dialog. The dialog does not apply any changes.
	 *
	 * @param shell The parent shell for the dialog.
	 * @param initialEntry The entry to edit. The kind of the classpath entry must be either
	 * <code>IClasspathEntry.CPE_LIBRARY</code> or <code>IClasspathEntry.CPE_VARIABLE</code>.
	 * @return Returns the resulting classpath entry containing a potentially modified javadoc location attribute
	 * The resulting entry can be used to replace the original entry on the classpath.
	 * Note that the dialog does not make any changes on the passed entry nor on the classpath that
	 * contains it.
	 *
	 * @since 3.1
	 */
	public static IClasspathEntry configureJavadocLocation(Shell shell, IClasspathEntry initialEntry) {
		if (initialEntry == null) {
			throw new IllegalArgumentException();
		}
		int entryKind= initialEntry.getEntryKind();
		if (entryKind != IClasspathEntry.CPE_LIBRARY && entryKind != IClasspathEntry.CPE_VARIABLE) {
			throw new IllegalArgumentException();
		}

		URL location= JavaUI.getLibraryJavadocLocation(initialEntry);
		JavadocLocationDialog dialog=  new JavadocLocationDialog(shell, BasicElementLabels.getPathLabel(initialEntry.getPath(), false), location);
		if (dialog.open() == Window.OK) {
			CPListElement element= CPListElement.createFromExisting(initialEntry, null);
			URL res= dialog.getResult();
			element.setAttribute(CPListElement.JAVADOC, res != null ? res.toExternalForm() : null);
			return element.getClasspathEntry();
		}
		return null;
	}

	/**
	 * Shows the UI for configuring a variable classpath entry. See {@link IClasspathEntry#CPE_VARIABLE} for
	 * details about variable classpath entries.
	 * The dialog returns the configured classpath entry path or <code>null</code> if the dialog has
	 * been canceled. The dialog does not apply any changes.
	 *
	 * @param shell The parent shell for the dialog.
	 * @param initialEntryPath The initial variable classpath variable path or <code>null</code> to use
	 * an empty path.
	 * @param existingPaths An array of paths that are already on the classpath and therefore should not be
	 * selected again.
	 * @return Returns the configures classpath entry path or <code>null</code> if the dialog has
	 * been canceled.
	 */
	public static IPath configureVariableEntry(Shell shell, IPath initialEntryPath, IPath[] existingPaths) {
		if (existingPaths == null) {
			throw new IllegalArgumentException();
		}

		EditVariableEntryDialog dialog= new EditVariableEntryDialog(shell, initialEntryPath, existingPaths);
		if (dialog.open() == Window.OK) {
			return dialog.getPath();
		}
		return null;
	}

	/**
	 * Shows the UI for selecting new variable classpath entries. See {@link IClasspathEntry#CPE_VARIABLE} for
	 * details about variable classpath entries.
	 * The dialog returns an array of the selected variable entries or <code>null</code> if the dialog has
	 * been canceled. The dialog does not apply any changes.
	 *
	 * @param shell The parent shell for the dialog.
	 * @param existingPaths An array of paths that are already on the classpath and therefore should not be
	 * selected again.
	 * @return Returns an non empty array of the selected variable entries or <code>null</code> if the dialog has
	 * been canceled.
	 */
	public static IPath[] chooseVariableEntries(Shell shell, IPath[] existingPaths) {
		if (existingPaths == null) {
			throw new IllegalArgumentException();
		}
		NewVariableEntryDialog dialog= new NewVariableEntryDialog(shell);
		if (dialog.open() == Window.OK) {
			return dialog.getResult();
		}
		return null;
	}

	/**
	 * Shows the UI to configure a classpath container classpath entry. See {@link IClasspathEntry#CPE_CONTAINER} for
	 * details about container classpath entries.
	 * The dialog returns the configured classpath entry or <code>null</code> if the dialog has
	 * been canceled. The dialog does not apply any changes.
	 *
	 * @param shell The parent shell for the dialog.
	 * @param initialEntry The initial classpath container entry.
	 * @param project The project the entry belongs to. The project does not have to exist and can also be <code>null</code>.
	 * @param currentClasspath The class path entries currently selected to be set as the projects classpath. This can also
	 * include the entry to be edited. The dialog uses these entries as information only (e.g. to avoid duplicate entries); The user still can make changes after the
	 * the classpath container dialog has been closed. See {@link IClasspathContainerPageExtension} for
	 * more information.
	 * @return Returns the configured classpath container entry or <code>null</code> if the dialog has
	 * been canceled by the user.
	 */
	public static IClasspathEntry configureContainerEntry(Shell shell, IClasspathEntry initialEntry, IJavaProject project, IClasspathEntry[] currentClasspath) {
		if (initialEntry == null || currentClasspath == null) {
			throw new IllegalArgumentException();
		}

		ClasspathContainerWizard wizard= new ClasspathContainerWizard(initialEntry, project, currentClasspath);
		if (ClasspathContainerWizard.openWizard(shell, wizard) == Window.OK) {
			IClasspathEntry[] created= wizard.getNewEntries();
			if (created != null && created.length == 1) {
				return created[0];
			}
		}
		return null;
	}

	/**
	 * Shows the UI to choose new classpath container classpath entries. See {@link IClasspathEntry#CPE_CONTAINER} for
	 * details about container classpath entries.
	 * The dialog returns the selected classpath entries or <code>null</code> if the dialog has
	 * been canceled. The dialog does not apply any changes.
	 *
	 * @param shell The parent shell for the dialog.
	 * @param project The project the entry belongs to. The project does not have to exist and
	 * can also be <code>null</code>.
	 * @param currentClasspath The class path entries currently selected to be set as the projects classpath. This can also
	 * include the entry to be edited. The dialog uses these entries as information only; The user still can make changes after the
	 * the classpath container dialog has been closed. See {@link IClasspathContainerPageExtension} for
	 * more information.
	 * @return Returns the selected classpath container entries or <code>null</code> if the dialog has
	 * been canceled by the user.
	 */
	public static IClasspathEntry[] chooseContainerEntries(Shell shell, IJavaProject project, IClasspathEntry[] currentClasspath) {
		if (currentClasspath == null) {
			throw new IllegalArgumentException();
		}

		ClasspathContainerWizard wizard= new ClasspathContainerWizard((IClasspathEntry) null, project, currentClasspath);
		if (ClasspathContainerWizard.openWizard(shell, wizard) == Window.OK) {
			return wizard.getNewEntries();
		}
		return null;
	}


	/**
	 * Shows the UI to configure a JAR or ZIP archive located in the workspace.
	 * The dialog returns the configured classpath entry path or <code>null</code> if the dialog has
	 * been canceled. The dialog does not apply any changes.
	 *
	 * @param shell The parent shell for the dialog.
	 * @param initialEntry The path of the initial archive entry
	 * @param usedEntries An array of paths that are already on the classpath and therefore should not be
	 * selected again.
	 * @return Returns the configured JAR path or <code>null</code> if the dialog has
	 * been canceled by the user.
	 */
	public static IPath configureJAREntry(Shell shell, IPath initialEntry, IPath[] usedEntries) {
		if (initialEntry == null || usedEntries == null) {
			throw new IllegalArgumentException();
		}

		Class<?>[] acceptedClasses= new Class[] { IFile.class };
		TypedElementSelectionValidator validator= new TypedElementSelectionValidator(acceptedClasses, false);

		ArrayList<IResource> usedJars= new ArrayList<IResource>(usedEntries.length);
		IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
		for (int i= 0; i < usedEntries.length; i++) {
			IPath curr= usedEntries[i];
			if (!curr.equals(initialEntry)) {
				IResource resource= root.findMember(usedEntries[i]);
				if (resource instanceof IFile) {
					usedJars.add(resource);
				}
			}
		}

		IResource existing= root.findMember(initialEntry);

		FilteredElementTreeSelectionDialog dialog= new FilteredElementTreeSelectionDialog(shell, new WorkbenchLabelProvider(), new WorkbenchContentProvider());
		dialog.setValidator(validator);
		dialog.setTitle(NewWizardMessages.BuildPathDialogAccess_JARArchiveDialog_edit_title);
		dialog.setMessage(NewWizardMessages.BuildPathDialogAccess_JARArchiveDialog_edit_description);
		dialog.setInitialFilter(ArchiveFileFilter.JARZIP_FILTER_STRING);
		dialog.addFilter(new ArchiveFileFilter(usedJars, true, true));
		dialog.setInput(root);
		dialog.setComparator(new ResourceComparator(ResourceComparator.NAME));
		dialog.setInitialSelection(existing);

		if (dialog.open() == Window.OK) {
			IResource element= (IResource) dialog.getFirstResult();
			return element.getFullPath();
		}
		return null;
	}

	/**
	 * Shows the UI to select new JAR or ZIP archive entries located in the workspace.
	 * The dialog returns the selected entries or <code>null</code> if the dialog has
	 * been canceled. The dialog does not apply any changes.
	 *
	 * @param shell The parent shell for the dialog.
	 * @param initialSelection The path of the element (container or archive) to initially select or <code>null</code> to not select an entry.
	 * @param usedEntries An array of paths that are already on the classpath and therefore should not be
	 * selected again.
	 * @return Returns the new JAR paths or <code>null</code> if the dialog has
	 * been canceled by the user.
	 */
	public static IPath[] chooseJAREntries(Shell shell, IPath initialSelection, IPath[] usedEntries) {
		if (usedEntries == null) {
			throw new IllegalArgumentException();
		}

		Class<?>[] acceptedClasses= new Class[] { IFile.class };
		TypedElementSelectionValidator validator= new TypedElementSelectionValidator(acceptedClasses, true);
		ArrayList<IResource> usedJars= new ArrayList<IResource>(usedEntries.length);
		IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
		for (int i= 0; i < usedEntries.length; i++) {
			IResource resource= root.findMember(usedEntries[i]);
			if (resource instanceof IFile) {
				usedJars.add(resource);
			}
		}
		IResource focus= initialSelection != null ? root.findMember(initialSelection) : null;

		FilteredElementTreeSelectionDialog dialog= new FilteredElementTreeSelectionDialog(shell, new WorkbenchLabelProvider(), new WorkbenchContentProvider());
		dialog.setHelpAvailable(false);
		dialog.setValidator(validator);
		dialog.setTitle(NewWizardMessages.BuildPathDialogAccess_JARArchiveDialog_new_title);
		dialog.setMessage(NewWizardMessages.BuildPathDialogAccess_JARArchiveDialog_new_description);
		dialog.setInitialFilter(ArchiveFileFilter.JARZIP_FILTER_STRING);
		dialog.addFilter(new ArchiveFileFilter(usedJars, true, true));
		dialog.setInput(root);
		dialog.setComparator(new ResourceComparator(ResourceComparator.NAME));
		dialog.setInitialSelection(focus);

		if (dialog.open() == Window.OK) {
			Object[] elements= dialog.getResult();
			IPath[] res= new IPath[elements.length];
			for (int i= 0; i < res.length; i++) {
				IResource elem= (IResource)elements[i];
				res[i]= elem.getFullPath();
			}
			return res;
		}
		return null;
	}

	/**
	 * Shows the UI to configure an external JAR or ZIP archive.
	 * The dialog returns the configured or <code>null</code> if the dialog has
	 * been canceled. The dialog does not apply any changes.
	 *
	 * @param shell The parent shell for the dialog.
	 * @param initialEntry The path of the initial archive entry.
	 * @return Returns the configured external JAR path or <code>null</code> if the dialog has
	 * been canceled by the user.
	 */
	public static IPath configureExternalJAREntry(Shell shell, IPath initialEntry) {
		if (initialEntry == null) {
			throw new IllegalArgumentException();
		}

		String lastUsedPath= initialEntry.removeLastSegments(1).toOSString();

		FileDialog dialog= new FileDialog(shell, SWT.SINGLE);
		dialog.setText(NewWizardMessages.BuildPathDialogAccess_ExtJARArchiveDialog_edit_title);
		dialog.setFilterExtensions(ArchiveFileFilter.JAR_ZIP_FILTER_EXTENSIONS);
		dialog.setFilterPath(lastUsedPath);
		dialog.setFileName(initialEntry.lastSegment());

		String res= dialog.open();
		if (res == null) {
			return null;
		}
		JavaPlugin.getDefault().getDialogSettings().put(IUIConstants.DIALOGSTORE_LASTEXTJAR, dialog.getFilterPath());

		return Path.fromOSString(res).makeAbsolute();
	}

	/**
	 * Shows the UI to select new external JAR or ZIP archive entries.
	 * The dialog returns the selected entry paths or <code>null</code> if the dialog has
	 * been canceled. The dialog does not apply any changes.
	 *
	 * @param shell The parent shell for the dialog.
	 * @return Returns the new external JAR paths or <code>null</code> if the dialog has
	 * been canceled by the user.
	 */
	public static IPath[] chooseExternalJAREntries(Shell shell) {
		String lastUsedPath= JavaPlugin.getDefault().getDialogSettings().get(IUIConstants.DIALOGSTORE_LASTEXTJAR);
		if (lastUsedPath == null) {
			lastUsedPath= ""; //$NON-NLS-1$
		}
		FileDialog dialog= new FileDialog(shell, SWT.MULTI);
		dialog.setText(NewWizardMessages.BuildPathDialogAccess_ExtJARArchiveDialog_new_title);
		dialog.setFilterExtensions(ArchiveFileFilter.ALL_ARCHIVES_FILTER_EXTENSIONS);
		dialog.setFilterPath(lastUsedPath);

		String res= dialog.open();
		if (res == null) {
			return null;
		}
		String[] fileNames= dialog.getFileNames();
		int nChosen= fileNames.length;

		IPath filterPath= Path.fromOSString(dialog.getFilterPath());
		IPath[] elems= new IPath[nChosen];
		for (int i= 0; i < nChosen; i++) {
			elems[i]= filterPath.append(fileNames[i]).makeAbsolute();
		}
		JavaPlugin.getDefault().getDialogSettings().put(IUIConstants.DIALOGSTORE_LASTEXTJAR, dialog.getFilterPath());

		return elems;
	}

	/**
	 * Shows the UI to select new external class folder entries.
	 * The dialog returns the selected entry paths or <code>null</code> if the dialog has
	 * been canceled. The dialog does not apply any changes.
	 *
	 * @param shell The parent shell for the dialog.
	 * @return Returns the new external class folder path or <code>null</code> if the dialog has
	 * been canceled by the user.
	 *
	 * @since 3.4
	 */
	public static IPath[] chooseExternalClassFolderEntries(Shell shell) {
		String lastUsedPath= JavaPlugin.getDefault().getDialogSettings().get(IUIConstants.DIALOGSTORE_LASTEXTJARFOLDER);
		if (lastUsedPath == null) {
			lastUsedPath= ""; //$NON-NLS-1$
		}
		DirectoryDialog dialog= new DirectoryDialog(shell, SWT.MULTI);
		dialog.setText(NewWizardMessages.BuildPathDialogAccess_ExtClassFolderDialog_new_title);
		dialog.setMessage(NewWizardMessages.BuildPathDialogAccess_ExtClassFolderDialog_new_description);
		dialog.setFilterPath(lastUsedPath);

		String res= dialog.open();
		if (res == null) {
			return null;
		}

		File file= new File(res);
		if (file.isDirectory())
			return new IPath[] { new Path(file.getAbsolutePath()) };

		return null;
	}

	/**
	 * Shows the UI to configure an external class folder.
	 * The dialog returns the configured or <code>null</code> if the dialog has
	 * been canceled. The dialog does not apply any changes.
	 *
	 * @param shell The parent shell for the dialog.
	 * @param initialEntry The path of the initial archive entry.
	 * @return Returns the configured external class folder path or <code>null</code> if the dialog has
	 * been canceled by the user.
	 *
	 * @since 3.4
	 */
	public static IPath configureExternalClassFolderEntries(Shell shell, IPath initialEntry) {
		DirectoryDialog dialog= new DirectoryDialog(shell, SWT.SINGLE);
		dialog.setText(NewWizardMessages.BuildPathDialogAccess_ExtClassFolderDialog_edit_title);
		dialog.setMessage(NewWizardMessages.BuildPathDialogAccess_ExtClassFolderDialog_edit_description);
		dialog.setFilterPath(initialEntry.toString());

		String res= dialog.open();
		if (res == null) {
			return null;
		}

		File file= new File(res);
		if (file.isDirectory())
			return new Path(file.getAbsolutePath());

		return null;
	}

	/**
	 * Shows the UI to select new class folders.
	 * The dialog returns the selected class folder entry paths or <code>null</code> if the dialog has
	 * been canceled. The dialog does not apply any changes.
	 *
	 * @param shell The parent shell for the dialog.
	 * @param initialSelection The path of the element to initially select or <code>null</code>.
	 * @param usedEntries An array of paths that are already on the classpath and therefore should not be
	 * selected again.
	 * @return Returns the configured class folder paths or <code>null</code> if the dialog has
	 * been canceled by the user.
	 */
	public static IPath[] chooseClassFolderEntries(Shell shell, IPath initialSelection, IPath[] usedEntries) {
		if (usedEntries == null) {
			throw new IllegalArgumentException();
		}
		String title= NewWizardMessages.BuildPathDialogAccess_ExistingClassFolderDialog_new_title;
		String message= NewWizardMessages.BuildPathDialogAccess_ExistingClassFolderDialog_new_description;
		return internalChooseFolderEntry(shell, initialSelection, usedEntries, title, message);
	}

	/**
	 * Shows the UI to select new source folders.
	 * The dialog returns the selected classpath entry paths or <code>null</code> if the dialog has
	 * been canceled. The dialog does not apply any changes.
	 *
	 * @param shell The parent shell for the dialog.
	 * @param initialSelection The path of the element to initially select or <code>null</code>
	 * @param usedEntries An array of paths that are already on the classpath and therefore should not be
	 * selected again.
	 * @return Returns the configured class folder entry paths or <code>null</code> if the dialog has
	 * been canceled by the user.
	 */
	public static IPath[] chooseSourceFolderEntries(Shell shell, IPath initialSelection, IPath[] usedEntries) {
		if (usedEntries == null) {
			throw new IllegalArgumentException();
		}
		String title= NewWizardMessages.BuildPathDialogAccess_ExistingSourceFolderDialog_new_title;
		String message= NewWizardMessages.BuildPathDialogAccess_ExistingSourceFolderDialog_new_description;
		return internalChooseFolderEntry(shell, initialSelection, usedEntries, title, message);
	}


	private static IPath[] internalChooseFolderEntry(Shell shell, IPath initialSelection, IPath[] usedEntries, String title, String message) {
		Class<?>[] acceptedClasses= new Class[] { IProject.class, IFolder.class };

		ArrayList<IResource> usedContainers= new ArrayList<IResource>(usedEntries.length);
		IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
		for (int i= 0; i < usedEntries.length; i++) {
			IResource resource= root.findMember(usedEntries[i]);
			if (resource instanceof IContainer) {
				usedContainers.add(resource);
			}
		}

		IResource focus= initialSelection != null ? root.findMember(initialSelection) : null;
		Object[] used= usedContainers.toArray();

		MultipleFolderSelectionDialog dialog= new MultipleFolderSelectionDialog(shell, new WorkbenchLabelProvider(), new WorkbenchContentProvider());
		dialog.setExisting(used);
		dialog.setTitle(title);
		dialog.setMessage(message);
		dialog.setHelpAvailable(false);
		dialog.addFilter(new TypedViewerFilter(acceptedClasses, used));
		dialog.setInput(root);
		dialog.setInitialFocus(focus);

		if (dialog.open() == Window.OK) {
			Object[] elements= dialog.getResult();
			IPath[] res= new IPath[elements.length];
			for (int i= 0; i < res.length; i++) {
				IResource elem= (IResource) elements[i];
				res[i]= elem.getFullPath();
			}
			return res;
		}
		return null;
	}
}

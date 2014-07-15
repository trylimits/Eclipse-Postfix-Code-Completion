/*******************************************************************************
 * Copyright (c) 2005, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarimport;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;

import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptorProxy;
import org.eclipse.ltk.core.refactoring.history.RefactoringHistory;
import org.eclipse.ltk.ui.refactoring.history.RefactoringHistoryControlConfiguration;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.jarpackager.JarPackagerUtil;
import org.eclipse.jdt.internal.ui.refactoring.binary.BinaryRefactoringHistoryWizard;

/**
 * Import wizard to import a refactoring-aware Java Archive (JAR) file.
 * <p>
 * This class may be instantiated and used without further configuration; this
 * class is not intended to be subclassed.
 * </p>
 * <p>
 * Example:
 *
 * <pre>
 * IWizard wizard= new JarImportWizard();
 * wizard.init(workbench, selection);
 * WizardDialog dialog= new WizardDialog(shell, wizard);
 * dialog.open();
 * </pre>
 *
 * During the call to <code>open</code>, the wizard dialog is presented to
 * the user. When the user hits Finish, the user-selected JAR file is inspected
 * for associated refactorings, the wizard executes eventual refactorings,
 * copies the JAR file over its old version, the dialog closes, and the call to
 * <code>open</code> returns.
 * </p>
 *
 * @since 3.2
 */
public final class JarImportWizard extends BinaryRefactoringHistoryWizard implements IImportWizard {

	/** Proxy which requests the refactoring history from the import data */
	private final class RefactoringHistoryProxy extends RefactoringHistory {

		/** The cached refactoring history delta */
		private RefactoringDescriptorProxy[] fHistoryDelta= null;

		/**
		 * {@inheritDoc}
		 */
		@Override
		public RefactoringDescriptorProxy[] getDescriptors() {
			if (fHistoryDelta != null)
				return fHistoryDelta;
			final RefactoringHistory incoming= fImportData.getRefactoringHistory();
			if (incoming != null) {
				fHistoryDelta= incoming.getDescriptors();
				final IPackageFragmentRoot root= fImportData.getPackageFragmentRoot();
				if (root != null) {
					try {
						final URI uri= getLocationURI(root.getRawClasspathEntry());
						if (uri != null) {
							final File file= new File(uri);
							if (file.exists()) {
								ZipFile zip= null;
								try {
									zip= new ZipFile(file, ZipFile.OPEN_READ);
									ZipEntry entry= zip.getEntry(JarPackagerUtil.getRefactoringsEntry());
									if (entry != null) {
										InputStream stream= null;
										try {
											stream= zip.getInputStream(entry);
											final RefactoringHistory existing= RefactoringCore.getHistoryService().readRefactoringHistory(stream, JavaRefactoringDescriptor.JAR_MIGRATION | JavaRefactoringDescriptor.JAR_REFACTORING);
											if (existing != null)
												fHistoryDelta= incoming.removeAll(existing).getDescriptors();
										} finally {
											if (stream != null) {
												try {
													stream.close();
												} catch (IOException exception) {
													// Do nothing
												}
											}
										}
									}
								} catch (IOException exception) {
									try {
										zip.close();
									} catch(IOException e){
									}
								}
							}
						}
					} catch (CoreException exception) {
						JavaPlugin.log(exception);
					}
				}
				return fHistoryDelta;
			}
			return new RefactoringDescriptorProxy[0];
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean isEmpty() {
			final RefactoringDescriptorProxy[] proxies= getDescriptors();
			if (proxies != null)
				return proxies.length == 0;
			return true;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public RefactoringHistory removeAll(final RefactoringHistory history) {
			throw new UnsupportedOperationException();
		}
	}

	/** The dialog settings key */
	private static String DIALOG_SETTINGS_KEY= "JarImportWizard"; //$NON-NLS-1$

	/**
	 * Is the specified class path entry pointing to a valid location for
	 * import?
	 *
	 * @param entry
	 *            the class path entry
	 * @return <code>true</code> if it is a valid package fragment root,
	 *         <code>false</code> otherwise
	 */
	public static boolean isValidClassPathEntry(final IClasspathEntry entry) {
		Assert.isNotNull(entry);
		final int kind= entry.getEntryKind();
		if (kind == IClasspathEntry.CPE_LIBRARY)
			return entry.getContentKind() == IPackageFragmentRoot.K_BINARY;
		else if (kind == IClasspathEntry.CPE_VARIABLE)
			return true; // be optimistic
		return false;
	}

	/**
	 * Is the specified java project a valid project for import?
	 *
	 * @param project
	 *            the java project
	 * @return
	 * 	           returns <code>true</code> if the project is valid
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	public static boolean isValidJavaProject(final IJavaProject project) throws JavaModelException {
		Assert.isNotNull(project);
		return project.getProject().isAccessible();
	}

	/** The refactoring history proxy */
	private final RefactoringHistoryProxy fHistoryProxy;

	/** The jar import data */
	private final JarImportData fImportData= new JarImportData();

	/** The jar import page, or <code>null</code> */
	private JarImportWizardPage fImportPage= null;

	/** Is the wizard part of an import wizard? */
	private boolean fImportWizard= true;

	/** Has the wizard new dialog settings? */
	private boolean fNewSettings;

	/**
	 * Creates a new jar import wizard.
	 */
	public JarImportWizard() {
		super(JarImportMessages.JarImportWizard_window_title, JarImportMessages.RefactoringImportPreviewPage_title, JarImportMessages.RefactoringImportPreviewPage_description);
		fImportData.setRefactoringAware(true);
		fImportData.setIncludeDirectoryEntries(true);
		fHistoryProxy= new RefactoringHistoryProxy();
		setInput(fHistoryProxy);
		final IDialogSettings section= JavaPlugin.getDefault().getDialogSettings().getSection(DIALOG_SETTINGS_KEY);
		if (section == null)
			fNewSettings= true;
		else {
			fNewSettings= false;
			setDialogSettings(section);
		}
		setConfiguration(new RefactoringHistoryControlConfiguration(null, false, false) {

			@Override
			public String getProjectPattern() {
				return JarImportMessages.JarImportWizard_project_pattern;
			}

			@Override
			public String getWorkspaceCaption() {
				return JarImportMessages.JarImportWizard_workspace_caption;
			}
		});
		setDefaultPageImageDescriptor(JavaPluginImages.DESC_WIZBAN_REPLACE_JAR);
	}

	/**
	 * Creates a new jar import wizard.
	 *
	 * @param wizard
	 *            <code>true</code> if the wizard is part of an import wizard,
	 *            <code>false</code> otherwise
	 */
	public JarImportWizard(final boolean wizard) {
		this();
		fImportWizard= wizard;
		setWindowTitle(JarImportMessages.JarImportWizard_replace_title);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void addUserDefinedPages() {
		fImportPage= new JarImportWizardPage(this, fImportWizard);
		addPage(fImportPage);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean canFinish() {
		return super.canFinish() && fImportData.getPackageFragmentRoot() != null && fImportData.getRefactoringFileLocation() != null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean deconfigureClasspath(final IClasspathEntry[] entries, final IProgressMonitor monitor) throws CoreException {
		final boolean rename= fImportData.isRenameJarFile();
		if (rename && !fCancelled) {
			final IPackageFragmentRoot root= getPackageFragmentRoot();
			if (root != null) {
				final IClasspathEntry entry= root.getRawClasspathEntry();
				for (int index= 0; index < entries.length; index++) {
					if (entries[index].equals(entry)) {
						final IPath path= getTargetPath(entries[index]);
						if (path != null)
							entries[index]= JavaCore.newLibraryEntry(path, entries[index].getSourceAttachmentPath(), entries[index].getSourceAttachmentRootPath(), entries[index].getAccessRules(), entries[index].getExtraAttributes(), entries[index].isExported());
					}
				}
			}
		}
		if (!fCancelled)
			replaceJarFile(new SubProgressMonitor(monitor, 100, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
		return rename;
	}

	/**
	 * Returns the jar import data.
	 *
	 * @return the jar import data
	 */
	public JarImportData getImportData() {
		return fImportData;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IWizardPage getNextPage(final IWizardPage page) {
		if (page == fImportPage && fImportData.getRefactoringHistory() == null)
			return null;
		return super.getNextPage(page);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected IPackageFragmentRoot getPackageFragmentRoot() {
		return fImportData.getPackageFragmentRoot();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected RefactoringHistory getRefactoringHistory() {
		return fHistoryProxy;
	}

	/**
	 * Returns the target path to be used for the updated classpath entry.
	 *
	 * @param entry
	 *            the classpath entry
	 * @return the target path, or <code>null</code>
	 * @throws CoreException
	 *             if an error occurs
	 */
	private IPath getTargetPath(final IClasspathEntry entry) throws CoreException {
		final URI location= getLocationURI(entry);
		if (location != null) {
			final URI target= getTargetURI(location);
			if (target != null) {
				IPath path= URIUtil.toPath(target);
				if (path != null) {
					final IPath workspace= ResourcesPlugin.getWorkspace().getRoot().getLocation();
					if (workspace.isPrefixOf(path)) {
						path= path.removeFirstSegments(workspace.segmentCount());
						path= path.setDevice(null);
						path= path.makeAbsolute();
					}
				}
				return path;
			}
		}
		return null;
	}

	/**
	 * Returns the target uri taking any renaming of the jar file into account.
	 *
	 * @param uri
	 *            the location uri
	 * @return the target uri
	 * @throws CoreException
	 *             if an error occurs
	 */
	private URI getTargetURI(final URI uri) throws CoreException {
		final IFileStore parent= EFS.getStore(uri).getParent();
		if (parent != null) {
			final URI location= fImportData.getRefactoringFileLocation();
			if (location != null)
				return parent.getChild(EFS.getStore(location).getName()).toURI();
		}
		return uri;
	}

	/**
	 * {@inheritDoc}
	 */
	public void init(final IWorkbench workbench, final IStructuredSelection selection) {
		if (selection != null && selection.size() == 1) {
			final Object element= selection.getFirstElement();
			if (element instanceof IPackageFragmentRoot) {
				final IPackageFragmentRoot root= (IPackageFragmentRoot) element;
				try {
					final IClasspathEntry entry= root.getRawClasspathEntry();
					if (isValidClassPathEntry(entry)
							&& root.getResolvedClasspathEntry().getReferencingEntry() == null)
						fImportData.setPackageFragmentRoot(root);
				} catch (JavaModelException exception) {
					JavaPlugin.log(exception);
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean performFinish() {
		if (fNewSettings) {
			final IDialogSettings settings= JavaPlugin.getDefault().getDialogSettings();
			IDialogSettings section= settings.getSection(DIALOG_SETTINGS_KEY);
			section= settings.addNewSection(DIALOG_SETTINGS_KEY);
			setDialogSettings(section);
		}
		fImportPage.performFinish();
		return super.performFinish();
	}

	/**
	 * Replaces the old jar file with the new one.
	 *
	 * @param monitor
	 *            the progress monitor to use
	 * @throws CoreException
	 *             if an error occurs
	 */
	private void replaceJarFile(final IProgressMonitor monitor) throws CoreException {
		try {
			monitor.beginTask(JarImportMessages.JarImportWizard_cleanup_import, 250);
			final URI location= fImportData.getRefactoringFileLocation();
			if (location != null) {
				final IPackageFragmentRoot root= fImportData.getPackageFragmentRoot();
				if (root != null) {
					final URI uri= getLocationURI(root.getRawClasspathEntry());
					if (uri != null) {
						final IFileStore store= EFS.getStore(location);
						if (fImportData.isRenameJarFile()) {
							final URI target= getTargetURI(uri);
							store.copy(EFS.getStore(target), EFS.OVERWRITE, new SubProgressMonitor(monitor, 50, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
							if (!uri.equals(target))
								EFS.getStore(uri).delete(EFS.NONE, new SubProgressMonitor(monitor, 50, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
						} else
							store.copy(EFS.getStore(uri), EFS.OVERWRITE, new SubProgressMonitor(monitor, 100, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
						if (fJavaProject != null)
							fJavaProject.getResource().refreshLocal(IResource.DEPTH_INFINITE, new SubProgressMonitor(monitor, 50, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
						return;
					}
				}
			}
			throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), 0, JarImportMessages.JarImportWizard_error_copying_jar, null));
		} finally {
			monitor.done();
		}
	}
}

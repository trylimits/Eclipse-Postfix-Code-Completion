/*******************************************************************************
 * Copyright (c) 2005, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sergey Prigogin <eclipse.sprigogin@gmail.com> - [refactoring] Provide a way to implement refactorings that depend on resources that have to be explicitly released - https://bugs.eclipse.org/347599
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring.binary;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringContext;
import org.eclipse.ltk.core.refactoring.RefactoringContribution;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptorProxy;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;
import org.eclipse.ltk.core.refactoring.history.RefactoringHistory;
import org.eclipse.ltk.ui.refactoring.history.RefactoringHistoryWizard;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;

import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringDescriptorUtil;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.binary.SourceCreationOperation;
import org.eclipse.jdt.internal.corext.refactoring.binary.StubCreationOperation;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.jarimport.JarImportMessages;
import org.eclipse.jdt.internal.ui.util.CoreUtility;

/**
 * Partial implementation of a refactoring history wizard which creates stubs
 * from a binary package fragment root while refactoring.
 *
 * @since 3.2
 */
public abstract class BinaryRefactoringHistoryWizard extends RefactoringHistoryWizard {

	/** The meta-inf fragment */
	private static final String META_INF_FRAGMENT= JarFile.MANIFEST_NAME.substring(0, JarFile.MANIFEST_NAME.indexOf('/'));

	/** The temporary linked source folder */
	private static final String SOURCE_FOLDER= ".src"; //$NON-NLS-1$

	/** The temporary stubs folder */
	private static final String STUB_FOLDER= ".stubs"; //$NON-NLS-1$

	/**
	 * Updates the new classpath with exclusion patterns for the specified path.
	 *
	 * @param entries
	 *            the classpath entries
	 * @param path
	 *            the path
	 */
	private static void addExclusionPatterns(final List<IClasspathEntry> entries, final IPath path) {
		for (int index= 0; index < entries.size(); index++) {
			final IClasspathEntry entry= entries.get(index);
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE && entry.getPath().isPrefixOf(path)) {
				final IPath[] patterns= entry.getExclusionPatterns();
				if (!JavaModelUtil.isExcludedPath(path, patterns)) {
					final IPath[] filters= new IPath[patterns.length + 1];
					System.arraycopy(patterns, 0, filters, 0, patterns.length);
					filters[patterns.length]= path.removeFirstSegments(entry.getPath().segmentCount()).addTrailingSeparator();
					entries.set(index, JavaCore.newSourceEntry(entry.getPath(), filters, entry.getOutputLocation()));
				}
			}
		}
	}

	/**
	 * Checks whether the archive referenced by the package fragment root is not
	 * shared with multiple java projects in the workspace.
	 *
	 * @param root
	 *            the package fragment root
	 * @param monitor
	 *            the progress monitor to use
	 * @return the status of the operation
	 */
	private static RefactoringStatus checkPackageFragmentRoots(final IPackageFragmentRoot root, final IProgressMonitor monitor) {
		final RefactoringStatus status= new RefactoringStatus();
		try {
			monitor.beginTask(JarImportMessages.JarImportWizard_prepare_import, 100);
			final IWorkspaceRoot workspace= ResourcesPlugin.getWorkspace().getRoot();
			if (workspace != null) {
				final IJavaModel model= JavaCore.create(workspace);
				if (model != null) {
					try {
						final URI uri= getLocationURI(root.getRawClasspathEntry());
						if (uri != null) {
							final IJavaProject[] projects= model.getJavaProjects();
							final IProgressMonitor subMonitor= new SubProgressMonitor(monitor, 100, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL);
							try {
								subMonitor.beginTask(JarImportMessages.JarImportWizard_prepare_import, projects.length * 100);
								for (int index= 0; index < projects.length; index++) {
									final IPackageFragmentRoot[] roots= projects[index].getPackageFragmentRoots();
									final IProgressMonitor subsubMonitor= new SubProgressMonitor(subMonitor, 100, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL);
									try {
										subsubMonitor.beginTask(JarImportMessages.JarImportWizard_prepare_import, roots.length);
										for (int offset= 0; offset < roots.length; offset++) {
											final IPackageFragmentRoot current= roots[offset];
											if (!current.equals(root) && current.getKind() == IPackageFragmentRoot.K_BINARY) {
												final IClasspathEntry entry= current.getRawClasspathEntry();
												if (entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
													final URI location= getLocationURI(entry);
													if (uri.equals(location))
														status.addFatalError(Messages.format(JarImportMessages.JarImportWizard_error_shared_jar, new String[] { JavaElementLabels.getElementLabel(current.getJavaProject(), JavaElementLabels.ALL_DEFAULT) }));
												}
											}
											subsubMonitor.worked(1);
										}
									} finally {
										subsubMonitor.done();
									}
								}
							} finally {
								subMonitor.done();
							}
						}
					} catch (CoreException exception) {
						status.addError(exception.getLocalizedMessage());
					}
				}
			}
		} finally {
			monitor.done();
		}
		return status;
	}

	/**
	 * Configures the classpath of the project before refactoring.
	 *
	 * @param project
	 *            the java project
	 * @param root
	 *            the package fragment root to refactor
	 * @param folder
	 *            the temporary source folder
	 * @param monitor
	 *            the progress monitor to use
	 * @throws IllegalStateException
	 *             if the plugin state location does not exist
	 * @throws CoreException
	 *             if an error occurs while configuring the class path
	 */
	private static void configureClasspath(final IJavaProject project, final IPackageFragmentRoot root, final IFolder folder, final IProgressMonitor monitor) throws IllegalStateException, CoreException {
		try {
			monitor.beginTask(JarImportMessages.JarImportWizard_prepare_import, 200);
			final IClasspathEntry entry= root.getRawClasspathEntry();
			final IClasspathEntry[] entries= project.getRawClasspath();
			final List<IClasspathEntry> list= new ArrayList<IClasspathEntry>();
			list.addAll(Arrays.asList(entries));
			final IFileStore store= EFS.getLocalFileSystem().getStore(JavaPlugin.getDefault().getStateLocation().append(STUB_FOLDER).append(project.getElementName()));
			if (store.fetchInfo(EFS.NONE, new SubProgressMonitor(monitor, 25, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL)).exists())
				store.delete(EFS.NONE, new SubProgressMonitor(monitor, 25, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
			store.mkdir(EFS.NONE, new SubProgressMonitor(monitor, 25, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
			folder.createLink(store.toURI(), IResource.NONE, new SubProgressMonitor(monitor, 25, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
			addExclusionPatterns(list, folder.getFullPath());
			for (int index= 0; index < entries.length; index++) {
				if (entries[index].equals(entry))
					list.add(index, JavaCore.newSourceEntry(folder.getFullPath()));
			}
			project.setRawClasspath(list.toArray(new IClasspathEntry[list.size()]), false, new SubProgressMonitor(monitor, 100, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
		} finally {
			monitor.done();
		}
	}

	/**
	 * Returns the location URI of the classpath entry
	 *
	 * @param entry
	 *            the classpath entry
	 * @return the location URI
	 */
	public static URI getLocationURI(final IClasspathEntry entry) {
		IPath path= null;
		if (entry.getEntryKind() == IClasspathEntry.CPE_VARIABLE)
			path= JavaCore.getResolvedVariablePath(entry.getPath());
		else
			path= entry.getPath();
		final IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
		URI location= null;
		if (root.exists(path)) {
			location= root.getFile(path).getRawLocationURI();
		} else
			location= URIUtil.toURI(path);
		return location;
	}

	/** Is auto build enabled? */
	private boolean fAutoBuild= true;

	/** Has the wizard been cancelled? */
	protected boolean fCancelled= false;

	/** The java project or <code>null</code> */
	protected IJavaProject fJavaProject= null;

	/**
	 * The packages which already have been processed (element type:
	 * &lt;IPackageFragment&gt;)
	 */
	private final Collection<IJavaElement> fProcessedFragments= new HashSet<IJavaElement>();

	/** The temporary source folder, or <code>null</code> */
	private IFolder fSourceFolder= null;

	/**
	 * Creates a new stub refactoring history wizard.
	 *
	 * @param overview
	 *            <code>true</code> to show an overview of the refactorings,
	 *            <code>false</code> otherwise
	 * @param caption
	 *            the wizard caption
	 * @param title
	 *            the wizard title
	 * @param description
	 *            the wizard description
	 */
	protected BinaryRefactoringHistoryWizard(final boolean overview, final String caption, final String title, final String description) {
		super(overview, caption, title, description);
	}

	/**
	 * Creates a new stub refactoring history wizard.
	 *
	 * @param caption
	 *            the wizard caption
	 * @param title
	 *            the wizard title
	 * @param description
	 *            the wizard description
	 */
	protected BinaryRefactoringHistoryWizard(final String caption, final String title, final String description) {
		super(caption, title, description);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected RefactoringStatus aboutToPerformHistory(final IProgressMonitor monitor) {
		final RefactoringStatus status= new RefactoringStatus();
		try {
			fJavaProject= null;
			fSourceFolder= null;
			fProcessedFragments.clear();
			monitor.beginTask(JarImportMessages.JarImportWizard_prepare_import, 520);
			status.merge(super.aboutToPerformHistory(new SubProgressMonitor(monitor, 10, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL)));
			if (!status.hasFatalError()) {
				final IPackageFragmentRoot root= getPackageFragmentRoot();
				if (root != null) {
					status.merge(checkPackageFragmentRoots(root, new SubProgressMonitor(monitor, 90, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL)));
					if (!status.hasFatalError()) {
						status.merge(checkSourceAttachmentRefactorings(new SubProgressMonitor(monitor, 20, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL)));
						if (!status.hasFatalError()) {
							final IJavaProject project= root.getJavaProject();
							if (project != null) {
								final IFolder folder= project.getProject().getFolder(SOURCE_FOLDER + String.valueOf(System.currentTimeMillis()));
								try {
									fAutoBuild= CoreUtility.setAutoBuilding(false);
									final RefactoringHistory history= getRefactoringHistory();
									if (history != null && !history.isEmpty())
										configureClasspath(project, root, folder, new SubProgressMonitor(monitor, 300, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
								} catch (CoreException exception) {
									status.merge(RefactoringStatus.createFatalErrorStatus(exception.getLocalizedMessage()));
									try {
										project.setRawClasspath(project.readRawClasspath(), false, new SubProgressMonitor(monitor, 100, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
									} catch (CoreException throwable) {
										JavaPlugin.log(throwable);
									}
								} finally {
									if (!status.hasFatalError()) {
										fJavaProject= project;
										fSourceFolder= folder;
									}
								}
							}
						}
					}
				}
			}
		} finally {
			monitor.done();
		}
		return status;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected RefactoringStatus aboutToPerformRefactoring(final Refactoring refactoring, final RefactoringDescriptor descriptor, final IProgressMonitor monitor) {
		final RefactoringStatus status= new RefactoringStatus();
		try {
			// nothing to do
		} finally {
			monitor.done();
		}
		return status;
	}

	/**
	 * Can this wizard use the source attachment of the package fragment root if
	 * necessary?
	 *
	 * @return <code>true</code> to use the source attachment,
	 *         <code>false</code> otherwise
	 */
	protected boolean canUseSourceAttachment() {
		final IPackageFragmentRoot root= getPackageFragmentRoot();
		if (root != null) {
			try {
				return root.getSourceAttachmentPath() != null;
			} catch (JavaModelException exception) {
				JavaPlugin.log(exception);
			}
		}
		return false;
	}

	/**
	 * Checks whether there are any refactorings to be executed which need a
	 * source attachment, but none exists.
	 *
	 * @param monitor
	 *            the progress monitor
	 * @return a status describing the outcome of the check
	 */
	protected RefactoringStatus checkSourceAttachmentRefactorings(final IProgressMonitor monitor) {
		final RefactoringStatus status= new RefactoringStatus();
		try {
			if (!canUseSourceAttachment()) {
				final RefactoringDescriptorProxy[] proxies= getRefactoringHistory().getDescriptors();
				monitor.beginTask(JarImportMessages.JarImportWizard_prepare_import, proxies.length * 100);
				for (int index= 0; index < proxies.length; index++) {
					final RefactoringDescriptor descriptor= proxies[index].requestDescriptor(new SubProgressMonitor(monitor, 100, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
					if (descriptor != null) {
						final int flags= descriptor.getFlags();
						if ((flags & JavaRefactoringDescriptor.JAR_SOURCE_ATTACHMENT) != 0)
							status.merge(RefactoringStatus.createFatalErrorStatus(Messages.format(JarImportMessages.BinaryRefactoringHistoryWizard_error_missing_source_attachment, descriptor.getDescription())));
					}
				}
			} else
				monitor.beginTask(JarImportMessages.JarImportWizard_prepare_import, 1);
		} finally {
			monitor.done();
		}
		return status;
	}

	/**
	 * Creates the necessary source code for the refactoring.
	 *
	 * @param monitor
	 *            the progress monitor to use
	 * @return
	 *            the resulting status
	 */
	private RefactoringStatus createNecessarySourceCode(final IProgressMonitor monitor) {
		final RefactoringStatus status= new RefactoringStatus();
		try {
			monitor.beginTask(JarImportMessages.JarImportWizard_prepare_import, 240);
			final IPackageFragmentRoot root= getPackageFragmentRoot();
			if (root != null && fSourceFolder != null && fJavaProject != null) {
				try {
					final SubProgressMonitor subMonitor= new SubProgressMonitor(monitor, 40, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL);
					final IJavaElement[] elements= root.getChildren();
					final List<IPackageFragment> list= new ArrayList<IPackageFragment>(elements.length);
					try {
						subMonitor.beginTask(JarImportMessages.JarImportWizard_prepare_import, elements.length);
						for (int index= 0; index < elements.length; index++) {
							final IJavaElement element= elements[index];
							if (!fProcessedFragments.contains(element) && !element.getElementName().equals(META_INF_FRAGMENT))
								list.add((IPackageFragment) element);
							subMonitor.worked(1);
						}
					} finally {
						subMonitor.done();
					}
					if (!list.isEmpty()) {
						fProcessedFragments.addAll(list);
						final URI uri= fSourceFolder.getRawLocationURI();
						if (uri != null) {
							final IPackageFragmentRoot sourceFolder= fJavaProject.getPackageFragmentRoot(fSourceFolder);
							IWorkspaceRunnable runnable= null;
							if (canUseSourceAttachment()) {
								runnable= new SourceCreationOperation(uri, list) {

									private IPackageFragment fFragment= null;

									@Override
									protected final void createCompilationUnit(final IFileStore store, final String name, final String content, final IProgressMonitor pm) throws CoreException {
										fFragment.createCompilationUnit(name, content, true, pm);
									}

									@Override
									protected final void createPackageFragment(final IFileStore store, final String name, final IProgressMonitor pm) throws CoreException {
										fFragment= sourceFolder.createPackageFragment(name, true, pm);
									}
								};
							} else {
								runnable= new StubCreationOperation(uri, list, true) {

									private IPackageFragment fFragment= null;

									@Override
									protected final void createCompilationUnit(final IFileStore store, final String name, final String content, final IProgressMonitor pm) throws CoreException {
										fFragment.createCompilationUnit(name, content, true, pm);
									}

									@Override
									protected final void createPackageFragment(final IFileStore store, final String name, final IProgressMonitor pm) throws CoreException {
										fFragment= sourceFolder.createPackageFragment(name, true, pm);
									}
								};
							}
							try {
								runnable.run(new SubProgressMonitor(monitor, 150, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
							} finally {
								fSourceFolder.refreshLocal(IResource.DEPTH_INFINITE, new SubProgressMonitor(monitor, 50, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
							}
						}
					}
				} catch (CoreException exception) {
					status.addFatalError(exception.getLocalizedMessage());
				}
			}
		} finally {
			monitor.done();
		}
		return status;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected RefactoringContext createRefactoringContext(RefactoringDescriptor descriptor, RefactoringStatus status, IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(descriptor);

		createNecessarySourceCode(monitor);

		if (descriptor instanceof JavaRefactoringDescriptor) {
			JavaRefactoringDescriptor javaDescriptor= (JavaRefactoringDescriptor) descriptor;
			RefactoringContribution contribution= RefactoringCore.getRefactoringContribution(javaDescriptor.getID());

			Map<String, String> map= contribution.retrieveArgumentMap(descriptor);
			if (fJavaProject == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.InitializableRefactoring_inacceptable_arguments));
				return null;
			}

			String name= fJavaProject.getElementName();

			String handle= map.get(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT);
			if (handle != null && handle.length() > 0)
				map.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT, getTransformedHandle(name, handle));

			int count= 1;
			String attribute= JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + count;
			while ((handle= map.get(attribute)) != null) {
				if (handle.length() > 0)
					map.put(attribute, getTransformedHandle(name, handle));
				count++;
				attribute= JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + count;
			}

			// create adapted descriptor
			try {
				descriptor= contribution.createDescriptor(descriptor.getID(), name, descriptor.getDescription(), descriptor.getComment(), map, descriptor.getFlags());
			} catch (IllegalArgumentException e) {
				status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.InitializableRefactoring_inacceptable_arguments));
				return null;
			}
		}
		return descriptor.createRefactoringContext(status);
	}

	/**
	 * Deconfigures the classpath after all refactoring have been performed.
	 *
	 * @param entries
	 *            the classpath entries to reset the project to
	 * @param monitor
	 *            the progress monitor to use
	 * @return <code>true</code> if the classpath has been changed,
	 *         <code>false</code> otherwise
	 * @throws CoreException
	 *             if an error occurs while deconfiguring the classpath
	 */
	protected boolean deconfigureClasspath(IClasspathEntry[] entries, IProgressMonitor monitor) throws CoreException {
		return false;
	}

	/**
	 * Deconfigures the classpath of the project after refactoring.
	 *
	 * @param monitor
	 *            the progress monitor to use
	 * @throws CoreException
	 *             if an error occurs while deconfiguring the classpath
	 */
	private void deconfigureClasspath(final IProgressMonitor monitor) throws CoreException {
		try {
			monitor.beginTask(JarImportMessages.JarImportWizard_cleanup_import, 300);
			if (fJavaProject != null) {
				final IClasspathEntry[] entries= fJavaProject.readRawClasspath();
				final boolean changed= deconfigureClasspath(entries, new SubProgressMonitor(monitor, 100, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
				final RefactoringHistory history= getRefactoringHistory();
				final boolean valid= history != null && !history.isEmpty();
				if (valid)
					RefactoringCore.getUndoManager().flush();
				if (valid || changed)
					fJavaProject.setRawClasspath(entries, changed, new SubProgressMonitor(monitor, 60, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
			}
			if (fSourceFolder != null) {
				final IFileStore store= EFS.getStore(fSourceFolder.getRawLocationURI());
				if (store.fetchInfo(EFS.NONE, new SubProgressMonitor(monitor, 10, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL)).exists())
					store.delete(EFS.NONE, new SubProgressMonitor(monitor, 10, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
				fSourceFolder.delete(true, false, new SubProgressMonitor(monitor, 10, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
				fSourceFolder.clearHistory(new SubProgressMonitor(monitor, 10, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
				fSourceFolder= null;
			}
			if (fJavaProject != null) {
				try {
					fJavaProject.getResource().refreshLocal(IResource.DEPTH_INFINITE, new SubProgressMonitor(monitor, 100, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
				} catch (CoreException exception) {
					JavaPlugin.log(exception);
				}
			}
		} finally {
			fJavaProject= null;
			monitor.done();
		}
	}

	/**
	 * Returns the package fragment root to stub.
	 *
	 * @return the package fragment root to stub, or <code>null</code>
	 */
	protected abstract IPackageFragmentRoot getPackageFragmentRoot();

	/**
	 * Returns the refactoring history to perform.
	 *
	 * @return the refactoring history to perform, or the empty history
	 */
	protected abstract RefactoringHistory getRefactoringHistory();

	/**
	 * Returns the transformed handle corresponding to the specified input
	 * handle.
	 *
	 * @param project
	 *            the project, or <code>null</code> for the workspace
	 * @param handle
	 *            the handle to transform
	 * @return the transformed handle, or the original one if nothing needed to
	 *         be transformed
	 */
	private String getTransformedHandle(final String project, final String handle) {
		if (fSourceFolder != null) {
			final IJavaElement target= JavaCore.create(fSourceFolder);
			if (target instanceof IPackageFragmentRoot) {
				final IPackageFragmentRoot extended= (IPackageFragmentRoot) target;
				String sourceIdentifier= null;
				final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(project, handle, false);
				if (element != null) {
					final IPackageFragmentRoot root= (IPackageFragmentRoot) element.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
					if (root != null)
						sourceIdentifier= root.getHandleIdentifier();
					else {
						final IJavaProject javaProject= element.getJavaProject();
						if (javaProject != null)
							sourceIdentifier= javaProject.getHandleIdentifier();
					}
					if (sourceIdentifier != null) {
						final IJavaElement result= JavaCore.create(extended.getHandleIdentifier() + element.getHandleIdentifier().substring(sourceIdentifier.length()));
						if (result != null)
							return JavaRefactoringDescriptorUtil.elementToHandle(project, result);
					}
				}
			}
		}
		return handle;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected RefactoringStatus historyPerformed(final IProgressMonitor monitor) {
		try {
			monitor.beginTask(JarImportMessages.JarImportWizard_cleanup_import, 100);
			final RefactoringStatus status= super.historyPerformed(new SubProgressMonitor(monitor, 10, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
			if (!status.hasFatalError()) {
				try {
					deconfigureClasspath(new SubProgressMonitor(monitor, 90, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
				} catch (CoreException exception) {
					status.addError(exception.getLocalizedMessage());
				} finally {
					try {
						CoreUtility.setAutoBuilding(fAutoBuild);
					} catch (CoreException exception) {
						JavaPlugin.log(exception);
					}
				}
			}
			return status;
		} finally {
			monitor.done();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean performCancel() {
		fCancelled= true;
		return super.performCancel();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected RefactoringStatus refactoringPerformed(final Refactoring refactoring, final IProgressMonitor monitor) {
		try {
			monitor.beginTask("", 120); //$NON-NLS-1$
			final RefactoringStatus status= super.refactoringPerformed(refactoring, new SubProgressMonitor(monitor, 100, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
			if (!status.hasFatalError()) {
				if (fSourceFolder != null) {
					try {
						fSourceFolder.refreshLocal(IResource.DEPTH_INFINITE, new SubProgressMonitor(monitor, 100, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
					} catch (CoreException exception) {
						JavaPlugin.log(exception);
					}
				}
			}
			return status;
		} finally {
			monitor.done();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean selectPreviewChange(final Change change) {
		if (fSourceFolder != null) {
			final IPath source= fSourceFolder.getFullPath();
			final Object element= change.getModifiedElement();
			if (element instanceof IAdaptable) {
				final IAdaptable adaptable= (IAdaptable) element;
				final IResource resource= (IResource) adaptable.getAdapter(IResource.class);
				if (resource != null && source.isPrefixOf(resource.getFullPath()))
					return false;
			}
		}
		return super.selectPreviewChange(change);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean selectStatusEntry(final RefactoringStatusEntry entry) {
		if (fSourceFolder != null) {
			final IPath source= fSourceFolder.getFullPath();
			final RefactoringStatusContext context= entry.getContext();
			if (context instanceof JavaStatusContext) {
				final JavaStatusContext extended= (JavaStatusContext) context;
				final ICompilationUnit unit= extended.getCompilationUnit();
				if (unit != null) {
					final IResource resource= unit.getResource();
					if (resource != null && source.isPrefixOf(resource.getFullPath()))
						return false;
				}
			}
		}
		return super.selectStatusEntry(entry);
	}
}

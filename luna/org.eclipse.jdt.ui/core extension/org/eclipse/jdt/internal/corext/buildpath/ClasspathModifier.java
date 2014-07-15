/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman, mpchapman@gmail.com - 89977 Make JDT .java agnostic
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.buildpath;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelStatus;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.ArchiveFileFilter;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathSupport;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElementAttribute;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage.ClasspathModifierQueries.OutputFolderValidator;

public class ClasspathModifier {

	private ClasspathModifier() {}

	public static BuildpathDelta setOutputLocation(CPListElement elementToChange, IPath outputPath, boolean allowInvalidCP, CPJavaProject cpProject) throws CoreException {
		BuildpathDelta result= new BuildpathDelta(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_EditOutput_tooltip);

		IJavaProject javaProject= cpProject.getJavaProject();
		IProject project= javaProject.getProject();
		IWorkspace workspace= project.getWorkspace();

		IPath projectPath= project.getFullPath();

		if (!allowInvalidCP && cpProject.getDefaultOutputLocation().segmentCount() == 1 && !projectPath.equals(elementToChange.getPath())) {
			String outputFolderName= PreferenceConstants.getPreferenceStore().getString(PreferenceConstants.SRCBIN_BINNAME);
			cpProject.setDefaultOutputLocation(cpProject.getDefaultOutputLocation().append(outputFolderName));
			List<CPListElement> existingEntries= cpProject.getCPListElements();
			CPListElement elem= ClasspathModifier.getListElement(javaProject.getPath(), existingEntries);
            if (elem != null) {
            	existingEntries.remove(elem);
            	result.removeEntry(elem);
            }
		}

		if (outputPath != null)
			exclude(outputPath, cpProject.getCPListElements(), new ArrayList<CPListElement>(), cpProject.getJavaProject(), null);

		IPath oldOutputLocation= (IPath)elementToChange.getAttribute(CPListElement.OUTPUT);
        if (oldOutputLocation != null && oldOutputLocation.segmentCount() > 1 && !oldOutputLocation.equals(cpProject.getDefaultOutputLocation())) {
			include(cpProject, oldOutputLocation);
        	result.addDeletedResource(workspace.getRoot().getFolder(oldOutputLocation));
        }
		elementToChange.setAttribute(CPListElement.OUTPUT, outputPath);

		result.setDefaultOutputLocation(cpProject.getDefaultOutputLocation());
		result.setNewEntries(cpProject.getCPListElements().toArray(new CPListElement[cpProject.getCPListElements().size()]));
		if (outputPath != null && outputPath.segmentCount() > 1) {
			result.addCreatedResource(workspace.getRoot().getFolder(outputPath));
		}

		return result;
	}

	public static IStatus checkSetOutputLocationPrecondition(CPListElement elementToChange, IPath outputPath, boolean allowInvalidCP, CPJavaProject cpProject) throws CoreException {
		IJavaProject javaProject= cpProject.getJavaProject();
		IProject project= javaProject.getProject();
		IWorkspace workspace= project.getWorkspace();

		IPath projectPath= project.getFullPath();

		if (outputPath == null)
			outputPath= cpProject.getDefaultOutputLocation();

		IStatus pathValidation= workspace.validatePath(outputPath.toString(), IResource.PROJECT | IResource.FOLDER);
		if (!pathValidation.isOK())
			return new StatusInfo(IStatus.ERROR, Messages.format(NewWizardMessages.OutputLocationDialog_error_invalidpath, pathValidation.getMessage()));

		IWorkspaceRoot root= workspace.getRoot();
		IResource res= root.findMember(outputPath);
		if (res != null) {
			// if exists, must be a folder or project
			if (res.getType() == IResource.FILE)
				return new StatusInfo(IStatus.ERROR, NewWizardMessages.OutputLocationDialog_error_existingisfile);
		}

		IStatus result= StatusInfo.OK_STATUS;

		int index= cpProject.indexOf(elementToChange);
		cpProject= cpProject.createWorkingCopy();
		elementToChange= cpProject.get(index);

		if (!allowInvalidCP && cpProject.getDefaultOutputLocation().segmentCount() == 1 && !projectPath.equals(elementToChange.getPath())) {
			String outputFolderName= PreferenceConstants.getPreferenceStore().getString(PreferenceConstants.SRCBIN_BINNAME);
			cpProject.setDefaultOutputLocation(cpProject.getDefaultOutputLocation().append(outputFolderName));
			ClasspathModifier.removeFromClasspath(javaProject, cpProject.getCPListElements(), null);
			result= new StatusInfo(IStatus.INFO, Messages.format(NewWizardMessages.OutputLocationDialog_removeProjectFromBP, BasicElementLabels.getPathLabel(cpProject.getDefaultOutputLocation(), false)));
		}

		exclude(outputPath, cpProject.getCPListElements(), new ArrayList<CPListElement>(), cpProject.getJavaProject(), null);

		IPath oldOutputLocation= (IPath)elementToChange.getAttribute(CPListElement.OUTPUT);
        if (oldOutputLocation != null && oldOutputLocation.segmentCount() > 1 && !oldOutputLocation.equals(cpProject.getDefaultOutputLocation())) {
			include(cpProject, oldOutputLocation);
        }
		elementToChange.setAttribute(CPListElement.OUTPUT, outputPath);

		IJavaModelStatus status= JavaConventions.validateClasspath(javaProject, cpProject.getClasspathEntries(), cpProject.getDefaultOutputLocation());
		if (!status.isOK()) {
			if (allowInvalidCP) {
				return new StatusInfo(IStatus.WARNING, status.getMessage());
			} else {
				return new StatusInfo(IStatus.ERROR, status.getMessage());
			}
		}

		if (outputPath.segmentCount() - projectPath.segmentCount() < 1)
			return result;

		String lastSegment= outputPath.lastSegment();
		if (lastSegment == null)
			return result;

		if (lastSegment.equals(".settings") && outputPath.segmentCount() - projectPath.segmentCount() == 1) { //$NON-NLS-1$

			StatusInfo statusInfo= new StatusInfo(IStatus.WARNING, NewWizardMessages.OutputLocation_SettingsAsLocation);
			if (result.isOK()) {
				return statusInfo;
			} else {
				MultiStatus ms= new MultiStatus(result.getPlugin(), result.getCode(), new IStatus[] {result, statusInfo}, statusInfo.getMessage(), null);
				return ms;
			}
		}

		if (lastSegment.length() > 1 && lastSegment.charAt(0) == '.') {
			StatusInfo statusInfo= new StatusInfo(IStatus.WARNING, Messages.format(NewWizardMessages.OutputLocation_DotAsLocation, BasicElementLabels.getPathLabel(outputPath, false)));
			if (result.isOK()) {
				return statusInfo;
			} else {
				MultiStatus ms= new MultiStatus(result.getPlugin(), result.getCode(), new IStatus[] {result, statusInfo}, statusInfo.getMessage(), null);
				return ms;
			}
		}

		return result;
	}

    public static IStatus checkAddExternalJarsPrecondition(IPath[] absolutePaths, CPJavaProject cpProject) {
    	IStatus result= StatusInfo.OK_STATUS;

    	IJavaProject javaProject= cpProject.getJavaProject();

    	List<CPListElement> newEntries= new ArrayList<CPListElement>();
    	List<CPListElement> duplicateEntries= new ArrayList<CPListElement>();
    	List<CPListElement> existingEntries= cpProject.getCPListElements();
    	for (int i= 0; i < absolutePaths.length; i++) {
	        CPListElement newEntry= new CPListElement(javaProject, IClasspathEntry.CPE_LIBRARY, absolutePaths[i], null);
	        if (existingEntries.contains(newEntry)) {
	        	duplicateEntries.add(newEntry);
	        } else {
	        	newEntries.add(newEntry);
	        }
        }

		if (duplicateEntries.size() > 0) {
			String message;
			if (duplicateEntries.size() > 1) {
				StringBuffer buf= new StringBuffer();
				for (Iterator<CPListElement> iterator= duplicateEntries.iterator(); iterator.hasNext();) {
	                CPListElement dup= iterator.next();
	                buf.append('\n').append(BasicElementLabels.getResourceName(dup.getPath().lastSegment()));
                }
				message= Messages.format(NewWizardMessages.AddArchiveToBuildpathAction_DuplicateArchivesInfo_message, buf.toString());
			} else {
				message= Messages.format(NewWizardMessages.AddArchiveToBuildpathAction_DuplicateArchiveInfo_message, BasicElementLabels.getResourceName(duplicateEntries.get(0).getPath().lastSegment()));
			}
			result= new StatusInfo(IStatus.INFO, message);
		}

		if (newEntries.size() == 0)
			return result;

		cpProject= cpProject.createWorkingCopy();
		existingEntries= cpProject.getCPListElements();

		for (Iterator<CPListElement> iterator= newEntries.iterator(); iterator.hasNext();) {
            CPListElement newEntry= iterator.next();
            insertAtEndOfCategory(newEntry, existingEntries);
        }

		IJavaModelStatus cpStatus= JavaConventions.validateClasspath(javaProject, cpProject.getClasspathEntries(), cpProject.getDefaultOutputLocation());
		if (!cpStatus.isOK())
			return cpStatus;

		return result;
    }

    public static BuildpathDelta addExternalJars(IPath[] absolutePaths, CPJavaProject cpProject) {
    	BuildpathDelta result= new BuildpathDelta(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_AddJarCP_tooltip);

    	IJavaProject javaProject= cpProject.getJavaProject();

    	List<CPListElement> existingEntries= cpProject.getCPListElements();
    	for (int i= 0; i < absolutePaths.length; i++) {
	        CPListElement newEntry= new CPListElement(javaProject, IClasspathEntry.CPE_LIBRARY, absolutePaths[i], null);
	        if (!existingEntries.contains(newEntry)) {
	        	insertAtEndOfCategory(newEntry, existingEntries);
	        	result.addEntry(newEntry);
	        }
        }

		result.setNewEntries(existingEntries.toArray(new CPListElement[existingEntries.size()]));
		result.setDefaultOutputLocation(cpProject.getDefaultOutputLocation());
		return result;
    }

    public static BuildpathDelta removeFromBuildpath(CPListElement[] toRemove, CPJavaProject cpProject) {

        IJavaProject javaProject= cpProject.getJavaProject();
		IPath projectPath= javaProject.getPath();
        IWorkspaceRoot workspaceRoot= javaProject.getProject().getWorkspace().getRoot();

    	List<CPListElement> existingEntries= cpProject.getCPListElements();
		BuildpathDelta result= new BuildpathDelta(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_RemoveFromCP_tooltip);

		for (int i= 0; i < toRemove.length; i++) {
	        CPListElement element= toRemove[i];
	        existingEntries.remove(element);
	        result.removeEntry(element);
	        IPath path= element.getPath();
			removeFilters(path, javaProject, existingEntries);
			if (!path.equals(projectPath)) {
	            IResource member= workspaceRoot.findMember(path);
	            if (member != null)
	            	result.addDeletedResource(member);
            } else if (cpProject.getDefaultOutputLocation().equals(projectPath) && containsSourceFolders(cpProject)) {
            	String outputFolderName= PreferenceConstants.getPreferenceStore().getString(PreferenceConstants.SRCBIN_BINNAME);
    			cpProject.setDefaultOutputLocation(cpProject.getDefaultOutputLocation().append(outputFolderName));
            }
        }

		result.setDefaultOutputLocation(cpProject.getDefaultOutputLocation());
    	result.setNewEntries(existingEntries.toArray(new CPListElement[existingEntries.size()]));

	    return result;
    }

    private static boolean containsSourceFolders(CPJavaProject cpProject) {
    	List<CPListElement> elements= cpProject.getCPListElements();
    	for (Iterator<CPListElement> iterator= elements.iterator(); iterator.hasNext();) {
	        CPListElement element= iterator.next();
	        if (element.getEntryKind() == IClasspathEntry.CPE_SOURCE)
	        	return true;
        }
	    return false;
    }

	private static void include(CPJavaProject cpProject, IPath path) {
	    List<CPListElement> elements= cpProject.getCPListElements();
	    for (Iterator<CPListElement> iterator= elements.iterator(); iterator.hasNext();) {
	        CPListElement element= iterator.next();
	        element.removeFromExclusions(path);
	    }
    }

	/**
	 * Get the <code>IClasspathEntry</code> from the project and
	 * convert it into a list of <code>CPListElement</code>s.
	 *
	 * @param project the Java project to get it's build path entries from
	 * @return a list of <code>CPListElement</code>s corresponding to the
	 * build path entries of the project
	 * @throws JavaModelException
	 */
	public static List<CPListElement> getExistingEntries(IJavaProject project) throws JavaModelException {
		IClasspathEntry[] classpathEntries= project.getRawClasspath();
		ArrayList<CPListElement> newClassPath= new ArrayList<CPListElement>();
		for (int i= 0; i < classpathEntries.length; i++) {
			IClasspathEntry curr= classpathEntries[i];
			newClassPath.add(CPListElement.createFromExisting(curr, project));
		}
		return newClassPath;
	}

	/**
	 * Try to find the corresponding and modified <code>CPListElement</code> for the root
	 * in the list of elements and return it.
	 * If no one can be found, the roots <code>ClasspathEntry</code> is converted to a
	 * <code>CPListElement</code> and returned.
	 *
	 * @param elements a list of <code>CPListElements</code>
	 * @param root the root to find the <code>ClasspathEntry</code> for represented by
	 * a <code>CPListElement</code>
	 * @return the <code>CPListElement</code> found in the list (matching by using the path) or
	 * the roots own <code>IClasspathEntry</code> converted to a <code>CPListElement</code>.
	 * @throws JavaModelException
	 */
	public static CPListElement getClasspathEntry(List<CPListElement> elements, IPackageFragmentRoot root) throws JavaModelException {
		IClasspathEntry entry= root.getRawClasspathEntry();
		for (int i= 0; i < elements.size(); i++) {
			CPListElement element= elements.get(i);
			if (element.getPath().equals(root.getPath()) && element.getEntryKind() == entry.getEntryKind())
				return elements.get(i);
		}
		CPListElement newElement= CPListElement.createFromExisting(entry, root.getJavaProject());
		elements.add(newElement);
		return newElement;
	}

	/**
	 * For a given <code>IResource</code>, try to
	 * convert it into a <code>IPackageFragmentRoot</code>
	 * if possible or return <code>null</code> if no
	 * fragment root could be created.
	 *
	 * @param resource the resource to be converted
	 * @return the <code>resource<code> as
	 * <code>IPackageFragment</code>,or <code>null</code>
	 * if failed to convert
	 */
	public static IPackageFragment getFragment(IResource resource) {
		IJavaElement elem= JavaCore.create(resource);
		if (elem instanceof IPackageFragment)
			return (IPackageFragment) elem;
		return null;
	}

	/**
	 * Get the source folder of a given <code>IResource</code> element,
	 * starting with the resource's parent.
	 *
	 * @param resource the resource to get the fragment root from
	 * @param project the Java project
	 * @param monitor progress monitor, can be <code>null</code>
	 * @return resolved fragment root, or <code>null</code> the resource is not (in) a source folder
	 * @throws JavaModelException
	 */
	public static IPackageFragmentRoot getFragmentRoot(IResource resource, IJavaProject project, IProgressMonitor monitor) throws JavaModelException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		IJavaElement javaElem= null;
		if (resource.getFullPath().equals(project.getPath()))
			return project.getPackageFragmentRoot(resource);
		IContainer container= resource.getParent();
		do {
			if (container instanceof IFolder)
				javaElem= JavaCore.create((IFolder) container);
			if (container.getFullPath().equals(project.getPath())) {
				javaElem= project;
				break;
			}
			container= container.getParent();
			if (container == null)
				return null;
		} while (javaElem == null || !(javaElem instanceof IPackageFragmentRoot));
		if (javaElem instanceof IJavaProject) {
			if (!isSourceFolder((IJavaProject)javaElem))
				return null;
			javaElem= project.getPackageFragmentRoot(project.getResource());
		}
		return (IPackageFragmentRoot) javaElem;
	}

	/**
	 * Get the <code>IClasspathEntry</code> for the
	 * given path by looking up all
	 * build path entries on the project
	 *
	 * @param path the path to find a build path entry for
	 * @param project the Java project
	 * @param entryKind
	 * @return the <code>IClasspathEntry</code> corresponding
	 * to the <code>path</code> or <code>null</code> if there
	 * is no such entry
	 * @throws JavaModelException
	 */
	public static IClasspathEntry getClasspathEntryFor(IPath path, IJavaProject project, int entryKind) throws JavaModelException {
		IClasspathEntry[] entries= project.getRawClasspath();
		for (int i= 0; i < entries.length; i++) {
			IClasspathEntry entry= entries[i];
			if (entry.getPath().equals(path) && equalEntryKind(entry, entryKind))
				return entry;
		}
		return null;
	}

	/**
	 * Check whether the current selection is the project's
	 * default output folder or not
	 *
	 * @param attrib the attribute to be checked
	 * @return <code>true</code> if is the default output folder,
	 * <code>false</code> otherwise.
	 */
	public static boolean isDefaultOutputFolder(CPListElementAttribute attrib) {
		return attrib.getValue() == null;
	}

	/**
	 * Determines whether the current selection (of type
	 * <code>ICompilationUnit</code> or <code>IPackageFragment</code>)
	 * is on the inclusion filter of it's parent source folder.
	 *
	 * @param selection the current Java element
	 * @param project the Java project
	 * @param monitor progress monitor, can be <code>null</code>
	 * @return <code>true</code> if the current selection is included,
	 * <code>false</code> otherwise.
	 * @throws JavaModelException
	 */
	public static boolean isIncluded(IJavaElement selection, IJavaProject project, IProgressMonitor monitor) throws JavaModelException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_ContainsPath, 4);
			IPackageFragmentRoot root= (IPackageFragmentRoot) selection.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			IClasspathEntry entry= root.getRawClasspathEntry();
			if (entry == null)
				return false;
			return contains(selection.getPath().removeFirstSegments(root.getPath().segmentCount()), entry.getInclusionPatterns(), new SubProgressMonitor(monitor, 2));
		} finally {
			monitor.done();
		}
	}

	/**
	 * Find out whether the <code>IResource</code> excluded or not.
	 *
	 * @param resource the resource to be checked
	 * @param project the Java project
	 * @return <code>true</code> if the resource is excluded, <code>
	 * false</code> otherwise
	 * @throws JavaModelException
	 */
	public static boolean isExcluded(IResource resource, IJavaProject project) throws JavaModelException {
		IPackageFragmentRoot root= getFragmentRoot(resource, project, null);
		if (root == null)
			return false;
		String fragmentName= getName(resource.getFullPath(), root.getPath());
		fragmentName= completeName(fragmentName);
		IClasspathEntry entry= root.getRawClasspathEntry();
		return entry != null && contains(new Path(fragmentName), entry.getExclusionPatterns(), null);
	}

	/**
	 * Find out whether one of the <code>IResource</code>'s parents
	 * is excluded.
	 *
	 * @param resource check the resources parents whether they are
	 * excluded or not
	 * @param project the Java project
	 * @return <code>true</code> if there is an excluded parent,
	 * <code>false</code> otherwise
	 * @throws JavaModelException
	 */
	public static boolean parentExcluded(IResource resource, IJavaProject project) throws JavaModelException {
		if (resource.getFullPath().equals(project.getPath()))
			return false;
		IPackageFragmentRoot root= getFragmentRoot(resource, project, null);
		if (root == null) {
			return true;
		}
		IPath path= resource.getFullPath().removeFirstSegments(root.getPath().segmentCount());
		IClasspathEntry entry= root.getRawClasspathEntry();
		if (entry == null)
			return true; // there is no build path entry, this is equal to the fact that the parent is excluded
		while (path.segmentCount() > 0) {
			if (contains(path, entry.getExclusionPatterns(), null))
				return true;
			path= path.removeLastSegments(1);
		}
		return false;
	}

	/**
	 * Check whether the output location of the <code>IPackageFragmentRoot</code>
	 * is <code>null</code>. If this holds, then the root
	 * does use the default output folder.
	 *
	 * @param root the root to examine the output location for
	 * @return <code>true</code> if the root uses the default output folder, <code>false
	 * </code> otherwise.
	 * @throws JavaModelException
	 */
	public static boolean hasDefaultOutputFolder(IPackageFragmentRoot root) throws JavaModelException {
		return root.getRawClasspathEntry().getOutputLocation() == null;
	}

	/**
	 * Check whether at least one source folder of the given
	 * Java project has an output folder set.
	 *
	 * @param project the Java project
	 * @param monitor progress monitor, can be <code>null</code>
	 * @return <code>true</code> if at least one outputfolder
	 * is set, <code>false</code> otherwise
	 * @throws JavaModelException
	 */
	public static boolean hasOutputFolders(IJavaProject project, IProgressMonitor monitor) throws JavaModelException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			IPackageFragmentRoot[] roots= project.getPackageFragmentRoots();
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_CheckOutputFolders, roots.length);
			for (int i= 0; i < roots.length; i++) {
				if (roots[i].getRawClasspathEntry().getOutputLocation() != null)
					return true;
				monitor.worked(1);
			}
		} finally {
			monitor.done();
		}
		return false;
	}

	public static String escapeSpecialChars(String value) {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);

			switch (c) {
			case '&':
				buf.append("&amp;"); //$NON-NLS-1$
				break;
			case '<':
				buf.append("&lt;"); //$NON-NLS-1$
				break;
			case '>':
				buf.append("&gt;"); //$NON-NLS-1$
				break;
			case '\'':
				buf.append("&apos;"); //$NON-NLS-1$
				break;
			case '\"':
				buf.append("&quot;"); //$NON-NLS-1$
				break;
			case 160:
				buf.append(" "); //$NON-NLS-1$
				break;
			default:
				buf.append(c);
				break;
			}
		}
		return buf.toString();
	}


	/**
	 * Check whether the <code>IJavaProject</code>
	 * is a source folder
	 *
	 * @param project the project to test
	 * @return <code>true</code> if <code>project</code> is a source folder
	 * <code>false</code> otherwise.
	 * @throws JavaModelException
	 */
	public static boolean isSourceFolder(IJavaProject project) throws JavaModelException {
		return ClasspathModifier.getClasspathEntryFor(project.getPath(), project, IClasspathEntry.CPE_SOURCE) != null;
	}

	/**
	 * Check whether the <code>IPackageFragment</code>
	 * corresponds to the project's default fragment.
	 *
	 * @param fragment the package fragment to be checked
	 * @return <code>true</code> if is the default package fragment,
	 * <code>false</code> otherwise.
	 */
	public static boolean isDefaultFragment(IPackageFragment fragment) {
		return fragment.isDefaultPackage();
	}

	/**
	 * Determines whether the inclusion filter of the element's source folder is empty
	 * or not
	 * @param resource
	 * @param project
	 * @param monitor
	 * @return <code>true</code> if the inclusion filter is empty,
	 * <code>false</code> otherwise.
	 * @throws JavaModelException
	 */
	public static boolean includeFiltersEmpty(IResource resource, IJavaProject project, IProgressMonitor monitor) throws JavaModelException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_ExamineInputFilters, 4);
			IPackageFragmentRoot root= getFragmentRoot(resource, project, new SubProgressMonitor(monitor, 4));
			if (root != null) {
				IClasspathEntry entry= root.getRawClasspathEntry();
				return entry.getInclusionPatterns().length == 0;
			}
			return true;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Check whether the input parameter of type <code>
	 * IPackageFragmentRoot</code> has either it's inclusion or
	 * exclusion filter or both set (that means they are
	 * not empty).
	 *
	 * @param root the fragment root to be inspected
	 * @return <code>true</code> inclusion or exclusion filter set,
	 * <code>false</code> otherwise.
	 * @throws JavaModelException
	 */
	public static boolean filtersSet(IPackageFragmentRoot root) throws JavaModelException {
		if (root == null)
			return false;
		IClasspathEntry entry= root.getRawClasspathEntry();
		IPath[] inclusions= entry.getInclusionPatterns();
		IPath[] exclusions= entry.getExclusionPatterns();
		if (inclusions != null && inclusions.length > 0)
			return true;
		if (exclusions != null && exclusions.length > 0)
			return true;
		return false;
	}

	/**
	 * Add a resource to the build path.
	 *
	 * @param resource the resource to be added to the build path
	 * @param existingEntries
	 * @param newEntries
	 * @param project the Java project
	 * @param monitor progress monitor, can be <code>null</code>
	 * @return returns the new element of type <code>IPackageFragmentRoot</code> that has been added to the build path
	 * @throws CoreException
	 * @throws OperationCanceledException
	 */
	public static CPListElement addToClasspath(IResource resource, List<CPListElement> existingEntries, List<CPListElement> newEntries, IJavaProject project, IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_AddToBuildpath, 2);
			exclude(resource.getFullPath(), existingEntries, newEntries, project, new SubProgressMonitor(monitor, 1));
			CPListElement entry= new CPListElement(project, IClasspathEntry.CPE_SOURCE, resource.getFullPath(), resource);
			return entry;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Check whether the provided file is an archive (.jar or .zip).
	 *
	 * @param file the file to be checked
	 * @param project the Java project
	 * @return <code>true</code> if the file is an archive, <code>false</code>
	 * otherwise
	 * @throws JavaModelException
	 */
	public static boolean isArchive(IFile file, IJavaProject project) throws JavaModelException {
		if (!ArchiveFileFilter.isArchivePath(file.getFullPath(), true))
			return false;
		if (project != null && project.exists() && (project.findPackageFragmentRoot(file.getFullPath()) == null))
			return true;
		return false;
	}

	/**
	 * Add a Java element to the build path.
	 *
	 * @param javaElement element to be added to the build path
	 * @param existingEntries
	 * @param newEntries
	 * @param project the Java project
	 * @param monitor progress monitor, can be <code>null</code>
	 * @return returns the new element of type <code>IPackageFragmentRoot</code> that has been added to the build path
	 * @throws CoreException
	 * @throws OperationCanceledException
	 */
	public static CPListElement addToClasspath(IJavaElement javaElement, List<CPListElement> existingEntries, List<CPListElement> newEntries, IJavaProject project, IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_AddToBuildpath, 10);
			CPListElement entry= new CPListElement(project, IClasspathEntry.CPE_SOURCE, javaElement.getPath(), javaElement.getResource());
			return entry;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Remove the Java project from the build path
	 *
	 * @param project the project to be removed
	 * @param existingEntries a list of existing <code>CPListElement</code>. This list
	 * will be traversed and the entry for the project will be removed.
	 * @param monitor progress monitor, can be <code>null</code>
	 * @return returns the Java project
	 */
	public static IJavaProject removeFromClasspath(IJavaProject project, List<CPListElement> existingEntries, IProgressMonitor monitor) {
		CPListElement elem= getListElement(project.getPath(), existingEntries);
		if (elem != null) {
			existingEntries.remove(elem);
		}
		return project;
	}

	/**
	 * Remove <code>path</code> from inclusion/exlusion filters in all <code>existingEntries</code>
	 *
	 * @param path the path to remove
	 * @param project the Java project
	 * @param existingEntries a list of <code>CPListElement</code> representing the build path
	 * entries of the project.
	 * @return returns a <code>List</code> of <code>CPListElement</code> of modified elements, not null.
	 */
	public static List<CPListElement> removeFilters(IPath path, IJavaProject project, List<CPListElement> existingEntries) {
		if (path == null)
			return Collections.emptyList();

		IPath projPath= project.getPath();
		if (projPath.isPrefixOf(path)) {
			path= path.removeFirstSegments(projPath.segmentCount()).addTrailingSeparator();
		}

		List<CPListElement> result= new ArrayList<CPListElement>();
		for (Iterator<CPListElement> iter= existingEntries.iterator(); iter.hasNext();) {
			CPListElement element= iter.next();
			boolean hasChange= false;
			IPath[] exlusions= (IPath[])element.getAttribute(CPListElement.EXCLUSION);
			if (exlusions != null) {
				List<IPath> exlusionList= new ArrayList<IPath>(exlusions.length);
				for (int i= 0; i < exlusions.length; i++) {
					if (!exlusions[i].equals(path)) {
						exlusionList.add(exlusions[i]);
					} else {
						hasChange= true;
					}
				}
				element.setAttribute(CPListElement.EXCLUSION, exlusionList.toArray(new IPath[exlusionList.size()]));
			}

			IPath[] inclusion= (IPath[])element.getAttribute(CPListElement.INCLUSION);
			if (inclusion != null) {
				List<IPath> inclusionList= new ArrayList<IPath>(inclusion.length);
				for (int i= 0; i < inclusion.length; i++) {
					if (!inclusion[i].equals(path)) {
						inclusionList.add(inclusion[i]);
					} else {
						hasChange= true;
					}
				}
				element.setAttribute(CPListElement.INCLUSION, inclusionList.toArray(new IPath[inclusionList.size()]));
			}
			if (hasChange) {
				result.add(element);
			}
		}
		return result;
	}

	/**
	 * Exclude an element with a given name and absolute path
	 * from the build path.
	 *
	 * @param name the name of the element to be excluded
	 * @param fullPath the absolute path of the element
	 * @param entry the build path entry to be modified
	 * @param project the Java project
	 * @param monitor progress monitor, can be <code>null</code>
	 * @return a <code>IResource</code> corresponding to the excluded element
	 * @throws JavaModelException
	 */
	private static IResource exclude(String name, IPath fullPath, CPListElement entry, IJavaProject project, IProgressMonitor monitor) throws JavaModelException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		IResource result;
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_Excluding, 6);
			IPath[] excludedPath= (IPath[]) entry.getAttribute(CPListElement.EXCLUSION);
			IPath[] newExcludedPath= new IPath[excludedPath.length + 1];
			name= completeName(name);
			IPath path= new Path(name);
			if (!contains(path, excludedPath, new SubProgressMonitor(monitor, 2))) {
				System.arraycopy(excludedPath, 0, newExcludedPath, 0, excludedPath.length);
				newExcludedPath[excludedPath.length]= path;
				entry.setAttribute(CPListElement.EXCLUSION, newExcludedPath);
				entry.setAttribute(CPListElement.INCLUSION, remove(path, (IPath[]) entry.getAttribute(CPListElement.INCLUSION), new SubProgressMonitor(monitor, 4)));
			}
			result= fullPath == null ? null : getResource(fullPath, project);
		} finally {
			monitor.done();
		}
		return result;
	}

	/**
	 * Exclude an object at a given path.
	 * This means that the exclusion filter for the
	 * corresponding <code>IPackageFragmentRoot</code> needs to be modified.
	 *
	 * First, the fragment root needs to be found. To do so, the new entries
	 * are and the existing entries are traversed for a match and the entry
	 * with the path is removed from one of those lists.
	 *
	 * Note: the <code>IJavaElement</code>'s fragment (if there is one)
	 * is not allowed to be excluded! However, inclusion (or simply no
	 * filter) on the parent fragment is allowed.
	 *
	 * @param path absolute path of an object to be excluded
	 * @param existingEntries a list of existing build path entries
	 * @param newEntries a list of new build path entries
	 * @param project the Java project
	 * @param monitor progress monitor, can be <code>null</code>
	 * @throws JavaModelException
	 */
	public static void exclude(IPath path, List<CPListElement> existingEntries, List<CPListElement> newEntries, IJavaProject project, IProgressMonitor monitor) throws JavaModelException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_Excluding, 1);
			CPListElement elem= null;
			CPListElement existingElem= null;
			int i= 0;
			do {
				i++;
				IPath rootPath= path.removeLastSegments(i);

				if (rootPath.segmentCount() == 0)
					return;

				elem= getListElement(rootPath, newEntries);
				existingElem= getListElement(rootPath, existingEntries);
			} while (existingElem == null && elem == null);
			if (elem == null) {
				elem= existingElem;
			}
			exclude(path.removeFirstSegments(path.segmentCount() - i).toString(), null, elem, project, new SubProgressMonitor(monitor, 1));
		} finally {
			monitor.done();
		}
	}

	/**
	 * Exclude a <code>IJavaElement</code>. This means that the exclusion filter for the
	 * corresponding <code>IPackageFragmentRoot</code>s need to be modified.
	 *
	 * Note: the <code>IJavaElement</code>'s fragment (if there is one)
	 * is not allowed to be excluded! However, inclusion (or simply no
	 * filter) on the parent fragment is allowed.
	 *
	 * @param javaElement the Java element to be excluded
	 * @param entry the <code>CPListElement</code> representing the
	 * <code>IClasspathEntry</code> of the Java element's root.
	 * @param project the Java project
	 * @param monitor progress monitor, can be <code>null</code>
	 *
	 * @return the resulting <code>IResource<code>
	 * @throws JavaModelException
	 */
	public static IResource exclude(IJavaElement javaElement, CPListElement entry, IJavaProject project, IProgressMonitor monitor) throws JavaModelException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			String name= getName(javaElement.getPath(), entry.getPath());
			return exclude(name, javaElement.getPath(), entry, project, new SubProgressMonitor(monitor, 1));
		} finally {
			monitor.done();
		}
	}

	/**
	 * Inverse operation to <code>exclude</code>.
	 * The resource removed from it's fragment roots exlusion filter.
	 *
	 * Note: the <code>IJavaElement</code>'s fragment (if there is one)
	 * is not allowed to be excluded! However, inclusion (or simply no
	 * filter) on the parent fragment is allowed.
	 *
	 * @param resource the resource to be unexcluded
	 * @param entry the <code>CPListElement</code> representing the
	 * <code>IClasspathEntry</code> of the resource's root.
	 * @param project the Java project
	 * @param monitor progress monitor, can be <code>null</code>
	 * @throws JavaModelException
	 *
	 */
	public static void unExclude(IResource resource, CPListElement entry, IJavaProject project, IProgressMonitor monitor) throws JavaModelException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_RemoveExclusion, 10);
			String name= getName(resource.getFullPath(), entry.getPath());
			IPath[] excludedPath= (IPath[]) entry.getAttribute(CPListElement.EXCLUSION);
			IPath[] newExcludedPath= remove(new Path(completeName(name)), excludedPath, new SubProgressMonitor(monitor, 3));
			entry.setAttribute(CPListElement.EXCLUSION, newExcludedPath);
		} finally {
			monitor.done();
		}
	}

	/**
	 * Resets inclusion and exclusion filters for the given
	 * <code>IJavaElement</code>
	 *
	 * @param element element to reset it's filters
	 * @param entry the <code>CPListElement</code> to reset its filters for
	 * @param project the Java project
	 * @param monitor progress monitor, can be <code>null</code>
	 * @throws JavaModelException
	 */
	public static void resetFilters(IJavaElement element, CPListElement entry, IJavaProject project, IProgressMonitor monitor) throws JavaModelException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_ResetFilters, 3);

			List<Path> exclusionList= getFoldersOnCP(element.getPath(), project, new SubProgressMonitor(monitor, 2));
			IPath outputLocation= (IPath) entry.getAttribute(CPListElement.OUTPUT);
			if (outputLocation != null) {
				IPath[] exclusionPatterns= (IPath[]) entry.getAttribute(CPListElement.EXCLUSION);
				if (contains(new Path(completeName(outputLocation.lastSegment())), exclusionPatterns, null)) {
					exclusionList.add(new Path(completeName(outputLocation.lastSegment())));
				}
			}
			IPath[] exclusions= exclusionList.toArray(new IPath[exclusionList.size()]);

			entry.setAttribute(CPListElement.INCLUSION, new IPath[0]);
			entry.setAttribute(CPListElement.EXCLUSION, exclusions);
		} finally {
			monitor.done();
		}
	}

	/**
	 * Reset the output folder for the given entry to the default output folder
	 *
	 * @param entry the <code>CPListElement</code> to be edited
	 * @param project the Java project
	 * @return an attribute representing the modified output folder
	 * @throws JavaModelException
	 */
	public static CPListElementAttribute resetOutputFolder(CPListElement entry, IJavaProject project) throws JavaModelException {
		entry.setAttribute(CPListElement.OUTPUT, null);
		CPListElementAttribute outputFolder= new CPListElementAttribute(entry, CPListElement.OUTPUT, entry.getAttribute(CPListElement.OUTPUT), true);
		return outputFolder;
	}

	/**
	 * Try to find the corresponding and modified <code>CPListElement</code> for the provided
	 * <code>CPListElement</code> in the list of elements and return it.
	 * If no one can be found, the provided <code>CPListElement</code> is returned.
	 *
	 * @param elements a list of <code>CPListElements</code>
	 * @param cpElement the <code>CPListElement</code> to find the corresponding entry in
	 * the list
	 * @return the <code>CPListElement</code> found in the list (matching by using the path) or
	 * the second <code>CPListElement</code> parameter itself if there is no match.
	 */
	public static CPListElement getClasspathEntry(List<CPListElement> elements, CPListElement cpElement) {
		for (int i= 0; i < elements.size(); i++) {
			if (elements.get(i).getPath().equals(cpElement.getPath()))
				return elements.get(i);
		}
		elements.add(cpElement);
		return cpElement;
	}

	/**
	 * For a given path, find the corresponding element in the list.
	 *
	 * @param path the path to found an entry for
	 * @param elements a list of <code>CPListElement</code>s
	 * @return the matched <code>CPListElement</code> or <code>null</code> if
	 * no match could be found
	 */
	public static CPListElement getListElement(IPath path, List<CPListElement> elements) {
		for (int i= 0; i < elements.size(); i++) {
			CPListElement element= elements.get(i);
			if (element.getEntryKind() == IClasspathEntry.CPE_SOURCE && element.getPath().equals(path)) {
				return element;
			}
		}
		return null;
	}

	public static void commitClassPath(List<CPListElement> newEntries, IJavaProject project, IProgressMonitor monitor) throws JavaModelException {
		if (monitor == null)
			monitor= new NullProgressMonitor();

		monitor.beginTask("", 2); //$NON-NLS-1$

		try {
			IClasspathEntry[] entries= convert(newEntries);
			IPath outputLocation= project.getOutputLocation();

			IJavaModelStatus status= JavaConventions.validateClasspath(project, entries, outputLocation);
			if (!status.isOK())
				throw new JavaModelException(status);
			
			BuildPathSupport.setEEComplianceOptions(project, newEntries);
			project.setRawClasspath(entries, outputLocation, new SubProgressMonitor(monitor, 2));
		} finally {
			monitor.done();
		}
	}

    public static void commitClassPath(CPJavaProject cpProject, IProgressMonitor monitor) throws JavaModelException {
		if (monitor == null)
			monitor= new NullProgressMonitor();

		monitor.beginTask("", 2); //$NON-NLS-1$

		try {
			List<CPListElement> cpListElements= cpProject.getCPListElements();
			IClasspathEntry[] entries= convert(cpListElements);
			IPath outputLocation= cpProject.getDefaultOutputLocation();

			IJavaProject javaProject= cpProject.getJavaProject();
			IJavaModelStatus status= JavaConventions.validateClasspath(javaProject, entries, outputLocation);
			if (!status.isOK())
				throw new JavaModelException(status);

			BuildPathSupport.setEEComplianceOptions(javaProject, cpListElements);
			javaProject.setRawClasspath(entries, outputLocation, new SubProgressMonitor(monitor, 2));
		} finally {
			monitor.done();
		}
    }

	/**
	 * For a given list of entries, find out what representation they
	 * will have in the project and return a list with corresponding
	 * elements.
	 *
	 * @param entries a list of entries to find an appropriate representation
	 * for. The list can contain elements of two types:
	 * <li><code>IResource</code></li>
	 * <li><code>IJavaElement</code></li>
	 * @param project the Java project
	 * @return a list of elements corresponding to the passed entries.
	 */
	public static List<?> getCorrespondingElements(List<?> entries, IJavaProject project) {
		List<IAdaptable> result= new ArrayList<IAdaptable>();
		for (int i= 0; i < entries.size(); i++) {
			Object element= entries.get(i);
			IPath path;
			if (element instanceof IResource)
				path= ((IResource) element).getFullPath();
			else
				path= ((IJavaElement) element).getPath();
			IResource resource= getResource(path, project);
			if (resource != null) {
				IJavaElement elem= JavaCore.create(resource);
				if (elem != null && project.isOnClasspath(elem))
					result.add(elem);
				else
					result.add(resource);
			}

		}
		return result;
	}

	/**
	 * Returns for the given absolute path the corresponding
	 * resource, this is either element of type <code>IFile</code>
	 * or <code>IFolder</code>.
	 *
	 * @param path an absolute path to a resource
	 * @param project the Java project
	 * @return the resource matching to the path. Can be
	 * either an <code>IFile</code> or an <code>IFolder</code>.
	 */
	private static IResource getResource(IPath path, IJavaProject project) {
		return project.getProject().getWorkspace().getRoot().findMember(path);
	}

	/**
	 * Find out whether the provided path equals to one
	 * in the array.
	 *
	 * @param path path to find an equivalent for
	 * @param paths set of paths to compare with
	 * @param monitor progress monitor, can be <code>null</code>
	 * @return <code>true</code> if there is an occurrence, <code>
	 * false</code> otherwise
	 */
	private static boolean contains(IPath path, IPath[] paths, IProgressMonitor monitor) {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		if (path == null)
			return false;
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_ComparePaths, paths.length);
			if (path.getFileExtension() == null)
				path= new Path(completeName(path.toString()));
			for (int i= 0; i < paths.length; i++) {
				if (paths[i].equals(path))
					return true;
				monitor.worked(1);
			}
		} finally {
			monitor.done();
		}
		return false;
	}

	/**
	 * Add a '/' at the end of the name if
	 * it does not end with '.java', or other Java-like extension.
	 *
	 * @param name append '/' at the end if
	 * necessary
	 * @return modified string
	 */
	private static String completeName(String name) {
		if (!JavaCore.isJavaLikeFileName(name)) {
			name= name + "/"; //$NON-NLS-1$
			name= name.replace('.', '/');
			return name;
		}
		return name;
	}

	/**
	 * Removes <code>path</code> out of the set of given <code>
	 * paths</code>. If the path is not contained, then the
	 * initially provided array of paths is returned.
	 *
	 * Only the first occurrence will be removed.
	 *
	 * @param path path to be removed
	 * @param paths array of path to apply the removal on
	 * @param monitor progress monitor, can be <code>null</code>
	 * @return array which does not contain <code>path</code>
	 */
	private static IPath[] remove(IPath path, IPath[] paths, IProgressMonitor monitor) {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_RemovePath, paths.length + 5);
			if (!contains(path, paths, new SubProgressMonitor(monitor, 5)))
				return paths;

			ArrayList<IPath> newPaths= new ArrayList<IPath>();
			for (int i= 0; i < paths.length; i++) {
				monitor.worked(1);
				if (!paths[i].equals(path))
					newPaths.add(paths[i]);
			}

			return newPaths.toArray(new IPath[newPaths.size()]);
		} finally {
			monitor.done();
		}

	}

	/**
	 * Find all folders that are on the build path and
	 * <code>path</code> is a prefix of those folders
	 * path entry, that is, all folders which are a
	 * subfolder of <code>path</code>.
	 *
	 * For example, if <code>path</code>=/MyProject/src
	 * then all folders having a path like /MyProject/src/*,
	 * where * can be any valid string are returned if
	 * they are also on the project's build path.
	 *
	 * @param path absolute path
	 * @param project the Java project
	 * @param monitor progress monitor, can be <code>null</code>
	 * @return an array of paths which belong to subfolders
	 * of <code>path</code> and which are on the build path
	 * @throws JavaModelException
	 */
	private static List<Path> getFoldersOnCP(IPath path, IJavaProject project, IProgressMonitor monitor) throws JavaModelException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		List<Path> srcFolders= new ArrayList<Path>();
		IClasspathEntry[] cpEntries= project.getRawClasspath();
		for (int i= 0; i < cpEntries.length; i++) {
			IPath cpPath= cpEntries[i].getPath();
			if (path.isPrefixOf(cpPath) && path.segmentCount() + 1 == cpPath.segmentCount())
				srcFolders.add(new Path(completeName(cpPath.lastSegment())));
		}
		return srcFolders;
	}

	/**
	 * Returns a string corresponding to the <code>path</code>
	 * with the <code>rootPath<code>'s number of segments
	 * removed
	 *
	 * @param path path to remove segments
	 * @param rootPath provides the number of segments to
	 * be removed
	 * @return a string corresponding to the mentioned
	 * action
	 */
	private static String getName(IPath path, IPath rootPath) {
		return path.removeFirstSegments(rootPath.segmentCount()).toString();
	}

	/**
	 * Sets and validates the new entries. Note that the elements of
	 * the list containing the new entries will be added to the list of
	 * existing entries (therefore, there is no return list for this method).
	 *
	 * @param existingEntries a list of existing classpath entries
	 * @param newEntries a list of entries to be added to the existing ones
	 * @param project the Java project
	 * @param monitor a progress monitor, can be <code>null</code>
	 * @throws CoreException in case that validation on one of the new entries fails
	 */
	public static void setNewEntry(List<CPListElement> existingEntries, List<CPListElement> newEntries, IJavaProject project, IProgressMonitor monitor) throws CoreException {
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_SetNewEntry, existingEntries.size());
			for (int i= 0; i < newEntries.size(); i++) {
				CPListElement entry= newEntries.get(i);
				validateAndAddEntry(entry, existingEntries, project);
				monitor.worked(1);
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Convert a list of <code>CPListElement</code>s to
	 * an array of <code>IClasspathEntry</code>.
	 *
	 * @param list the list to be converted
	 * @return an array containing build path entries
	 * corresponding to the list
	 */
	private static IClasspathEntry[] convert(List<CPListElement> list) {
		IClasspathEntry[] entries= new IClasspathEntry[list.size()];
		for (int i= 0; i < list.size(); i++) {
			CPListElement element= list.get(i);
			entries[i]= element.getClasspathEntry();
		}
		return entries;
	}

	/**
	 * Validate the new entry in the context of the existing entries. Furthermore,
	 * check if exclusion filters need to be applied and do so if necessary.
	 *
	 * If validation was successful, add the new entry to the list of existing entries.
	 *
	 * @param entry the entry to be validated and added to the list of existing entries.
	 * @param existingEntries a list of existing entries representing the build path
	 * @param project the Java project
	 * @throws CoreException in case that validation fails
	 */
	private static void validateAndAddEntry(CPListElement entry, List<CPListElement> existingEntries, IJavaProject project) throws CoreException {
		IPath path= entry.getPath();
		IPath projPath= project.getProject().getFullPath();
		IWorkspaceRoot workspaceRoot= ResourcesPlugin.getWorkspace().getRoot();
		IStatus validate= workspaceRoot.getWorkspace().validatePath(path.toString(), IResource.FOLDER);
		StatusInfo rootStatus= new StatusInfo();
		rootStatus.setOK();
		boolean isExternal= isExternalArchiveOrLibrary(entry);
		if (!isExternal && validate.matches(IStatus.ERROR) && !project.getPath().equals(path)) {
			rootStatus.setError(Messages.format(NewWizardMessages.NewSourceFolderWizardPage_error_InvalidRootName, validate.getMessage()));
			throw new CoreException(rootStatus);
		} else {
			if (!isExternal && !project.getPath().equals(path)) {
				IResource res= workspaceRoot.findMember(path);
				if (res != null) {
					if (res.getType() != IResource.FOLDER && res.getType() != IResource.FILE) {
						rootStatus.setError(NewWizardMessages.NewSourceFolderWizardPage_error_NotAFolder);
						throw new CoreException(rootStatus);
					}
				} else {
					URI projLocation= project.getProject().getLocationURI();
					if (projLocation != null) {
						IFileStore store= EFS.getStore(projLocation).getFileStore(path);
						if (store.fetchInfo().exists()) {
							rootStatus.setError(NewWizardMessages.NewSourceFolderWizardPage_error_AlreadyExistingDifferentCase);
							throw new CoreException(rootStatus);
						}
					}
				}
			}

			for (int i= 0; i < existingEntries.size(); i++) {
				CPListElement curr= existingEntries.get(i);
				if (curr.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
					if (path.equals(curr.getPath()) && !project.getPath().equals(path)) {
						rootStatus.setError(NewWizardMessages.NewSourceFolderWizardPage_error_AlreadyExisting);
						throw new CoreException(rootStatus);
					}
				}
			}

			if (!isExternal && !entry.getPath().equals(project.getPath()))
				exclude(entry.getPath(), existingEntries, new ArrayList<CPListElement>(), project, null);

			IPath outputLocation= project.getOutputLocation();
			insertAtEndOfCategory(entry, existingEntries);

			IClasspathEntry[] entries= convert(existingEntries);

			IJavaModelStatus status= JavaConventions.validateClasspath(project, entries, outputLocation);
			if (!status.isOK()) {
				if (outputLocation.equals(projPath)) {
					IStatus status2= JavaConventions.validateClasspath(project, entries, outputLocation);
					if (status2.isOK()) {
						if (project.isOnClasspath(project)) {
							rootStatus.setInfo(Messages.format(NewWizardMessages.NewSourceFolderWizardPage_warning_ReplaceSFandOL, BasicElementLabels.getPathLabel(outputLocation, false)));
						} else {
							rootStatus.setInfo(Messages.format(NewWizardMessages.NewSourceFolderWizardPage_warning_ReplaceOL, BasicElementLabels.getPathLabel(outputLocation, false)));
						}
						return;
					}
				}
				rootStatus.setError(status.getMessage());
				throw new CoreException(rootStatus);
			}

			if (isSourceFolder(project) || project.getPath().equals(path)) {
				rootStatus.setWarning(NewWizardMessages.NewSourceFolderWizardPage_warning_ReplaceSF);
				return;
			}

			rootStatus.setOK();
			return;
		}
	}

	private static void insertAtEndOfCategory(CPListElement entry, List<CPListElement> existingEntries) {
		int length= existingEntries.size();
		CPListElement[] elements= existingEntries.toArray(new CPListElement[length]);
		int i= 0;
		while (i < length && elements[i].getClasspathEntry().getEntryKind() != entry.getClasspathEntry().getEntryKind()) {
			i++;
		}
		if (i < length) {
			i++;
			while (i < length && elements[i].getClasspathEntry().getEntryKind() == entry.getClasspathEntry().getEntryKind()) {
				i++;
			}
			existingEntries.add(i, entry);
			return;
		}

		switch (entry.getClasspathEntry().getEntryKind()) {
		case IClasspathEntry.CPE_SOURCE:
			existingEntries.add(0, entry);
			break;
		case IClasspathEntry.CPE_CONTAINER:
		case IClasspathEntry.CPE_LIBRARY:
		case IClasspathEntry.CPE_PROJECT:
		case IClasspathEntry.CPE_VARIABLE:
		default:
			existingEntries.add(entry);
			break;
		}
	}

	private static boolean isExternalArchiveOrLibrary(CPListElement entry) {
		if (entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY || entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
			if (entry.getResource() instanceof IFolder) {
				return false;
			}
			return true;
		}
		return false;
	}

	public static boolean isInExternalOrArchive(IJavaElement element) {
		IPackageFragmentRoot root= (IPackageFragmentRoot) element.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
		return root != null && (root.isArchive() || root.isExternal());
	}

	/**
	 * Test if the provided kind is of type
	 * <code>IClasspathEntry.CPE_SOURCE</code>
	 *
	 * @param entry the classpath entry to be compared with the provided type
	 * @param kind the kind to be checked
	 * @return <code>true</code> if kind equals
	 * <code>IClasspathEntry.CPE_SOURCE</code>,
	 * <code>false</code> otherwise
	 */
	private static boolean equalEntryKind(IClasspathEntry entry, int kind) {
		return entry.getEntryKind() == kind;
	}

	public static OutputFolderValidator getValidator(final List<?> newElements, final IJavaProject project) throws JavaModelException {
		return new OutputFolderValidator(newElements, project) {

			@Override
			public boolean validate(IPath outputLocation) {
				for (int i= 0; i < newElements.size(); i++) {
					if (isInvalid(newElements.get(i), outputLocation))
						return false;
				}

				for (int i= 0; i < fEntries.length; i++) {
					if (isInvalid(fEntries[i], outputLocation))
						return false;
				}
				return true;
			}

			/**
			 * Check if the output location for the given object is valid
			 *
			 * @param object the object to retrieve its path from and compare it
			 * to the output location
			 * @param outputLocation the output location
			 * @return <code>true</code> if the output location is invalid, that is,
			 * if it is a subfolder of the provided object.
			 */
			private boolean isInvalid(Object object, IPath outputLocation) {
				IPath path= null;
				if (object instanceof IFolder)
					path= getFolderPath(object);
				else
					if (object instanceof IJavaElement)
						path= getJavaElementPath(object);
					else
						if (object instanceof IClasspathEntry)
							path= getCPEntryPath(object);
				return isSubFolderOf(path, outputLocation);
			}

			/**
			 * Get an <code>IFolder</code>'s path
			 *
			 * @param element an element which is of type <code>IFolder</code>
			 * @return the path of the folder
			 */
			private IPath getFolderPath(Object element) {
				return ((IFolder) element).getFullPath();
			}

			/**
			 * Get an <code>IJavaElement</code>'s path
			 *
			 * @param element an element which is of type <code>IJavaElement</code>
			 * @return the path of the Java element
			 */
			private IPath getJavaElementPath(Object element) {
				return ((IJavaElement) element).getPath();
			}

			/**
			 * Get an <code>IClasspathEntry</code>'s path
			 *
			 * @param entry an element which is of type <code>IClasspathEntry</code>
			 * @return the path of the classpath entry
			 */
			private IPath getCPEntryPath(Object entry) {
				return ((IClasspathEntry) entry).getPath();
			}

			/**
			 *
			 * @param path1 the first path
			 * @param path2 the second path
			 * @return <code>true</code> if path1 is a subfolder of
			 * path2, <code>false</code> otherwise
			 */
			private boolean isSubFolderOf(IPath path1, IPath path2) {
				if (path1 == null || path2 == null) {
					if (path1 == null && path2 == null)
						return true;
					return false;
				}
				return path2.matchingFirstSegments(path1) == path2.segmentCount();
			}

		};
	}

}

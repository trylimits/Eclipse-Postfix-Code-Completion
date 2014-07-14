/*******************************************************************************
 * Copyright (c) 2007, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.correction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import org.eclipse.swt.graphics.Image;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.ltk.core.refactoring.Change;

import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameMatch;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.TypeNameMatchCollector;

import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.text.java.ClasspathFixProcessor;

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

/**
 * Default contribution to org.eclipse.jdt.ui.classpathFixProcessors
 */
public class DefaultClasspathFixProcessor extends ClasspathFixProcessor {

	private static class DefaultClasspathFixProposal extends ClasspathFixProposal {

		private String fName;
		private Change fChange;
		private String fDescription;
		private int fRelevance;

		public DefaultClasspathFixProposal(String name, Change change, String description, int relevance) {
			fName= name;
			fChange= change;
			fDescription= description;
			fRelevance= relevance;
		}

		@Override
		public String getAdditionalProposalInfo() {
			return fDescription;
		}

		@Override
		public Change createChange(IProgressMonitor monitor) {
			return fChange;
		}

		@Override
		public String getDisplayString() {
			return fName;
		}

		@Override
		public Image getImage() {
			return JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
		}

		@Override
		public int getRelevance() {
			return fRelevance;
		}
	}

	@Override
	public ClasspathFixProposal[] getFixImportProposals(IJavaProject project, String missingType) throws CoreException {
		ArrayList<DefaultClasspathFixProposal> res= new ArrayList<DefaultClasspathFixProposal>();
		collectProposals(project, missingType, res);
		return res.toArray(new ClasspathFixProposal[res.size()]);
	}

	private void collectProposals(IJavaProject project, String name, Collection<DefaultClasspathFixProposal> proposals) throws CoreException {
		int idx= name.lastIndexOf('.');
		char[] packageName= idx != -1 ? name.substring(0, idx).toCharArray() : null; // no package provided
		char[] typeName= name.substring(idx + 1).toCharArray();

		if (typeName.length == 1 && typeName[0] == '*') {
			typeName= null;
		}

		IJavaSearchScope scope= SearchEngine.createWorkspaceScope();
		ArrayList<TypeNameMatch> res= new ArrayList<TypeNameMatch>();
		TypeNameMatchCollector requestor= new TypeNameMatchCollector(res);
		int matchMode= SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE;
		new SearchEngine().searchAllTypeNames(packageName, matchMode, typeName,
				matchMode, IJavaSearchConstants.TYPE, scope, requestor,
				IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, null);

		if (res.isEmpty()) {
			return;
		}
		HashSet<Object> addedClaspaths= new HashSet<Object>();
		for (int i= 0; i < res.size(); i++) {
			TypeNameMatch curr= res.get(i);
			IType type= curr.getType();
			if (type != null) {
				IPackageFragmentRoot root= (IPackageFragmentRoot) type.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
				try {
					IClasspathEntry entry= root.getRawClasspathEntry();
					if (entry == null) {
						continue;
					}
					IJavaProject other= root.getJavaProject();
					int entryKind= entry.getEntryKind();
					if ((entry.isExported() || entryKind == IClasspathEntry.CPE_SOURCE) && addedClaspaths.add(other)) {
						IClasspathEntry newEntry= JavaCore.newProjectEntry(other.getPath());
						Change change= ClasspathFixProposal.newAddClasspathChange(project, newEntry);
						if (change != null) {
							String[] args= { BasicElementLabels.getResourceName(other.getElementName()), BasicElementLabels.getResourceName(project.getElementName()) };
							String label= Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_project_description, args);
							String desc= label;
							DefaultClasspathFixProposal proposal= new DefaultClasspathFixProposal(label, change, desc, IProposalRelevance.ADD_PROJECT_TO_BUILDPATH);
							proposals.add(proposal);
						}
					}
					if (entryKind == IClasspathEntry.CPE_CONTAINER) {
						IPath entryPath= entry.getPath();
						if (isNonProjectSpecificContainer(entryPath)) {
							addLibraryProposal(project, root, entry, addedClaspaths, proposals);
						} else {
							try {
								IClasspathContainer classpathContainer= JavaCore.getClasspathContainer(entryPath, root.getJavaProject());
								if (classpathContainer != null) {
									IClasspathEntry entryInContainer= JavaModelUtil.findEntryInContainer(classpathContainer, root.getPath());
									if (entryInContainer != null) {
										addLibraryProposal(project, root, entryInContainer, addedClaspaths, proposals);
									}
								}
							} catch (CoreException e) {
								// ignore
							}
						}
					} else if ((entryKind == IClasspathEntry.CPE_LIBRARY || entryKind == IClasspathEntry.CPE_VARIABLE)) {
						addLibraryProposal(project, root, entry, addedClaspaths, proposals);
					}
				} catch (JavaModelException e) {
					// ignore
				}
			}
		}
	}

	private void addLibraryProposal(IJavaProject project, IPackageFragmentRoot root, IClasspathEntry entry, Collection<Object> addedClaspaths, Collection<DefaultClasspathFixProposal> proposals) throws JavaModelException {
		if (addedClaspaths.add(entry)) {
			String label= getAddClasspathLabel(entry, root, project);
			if (label != null) {
				Change change= ClasspathFixProposal.newAddClasspathChange(project, entry);
				if (change != null) {
					DefaultClasspathFixProposal proposal= new DefaultClasspathFixProposal(label, change, label, IProposalRelevance.ADD_TO_BUILDPATH);
					proposals.add(proposal);
				}
			}
		}
	}

	private boolean isNonProjectSpecificContainer(IPath containerPath) {
		if (containerPath.segmentCount() > 0) {
			String id= containerPath.segment(0);
			if (id.equals(JavaCore.USER_LIBRARY_CONTAINER_ID) || id.equals(JavaRuntime.JRE_CONTAINER)) {
				return true;
			}
		}
		return false;
	}


	private static String getAddClasspathLabel(IClasspathEntry entry, IPackageFragmentRoot root, IJavaProject project) {
		switch (entry.getEntryKind()) {
			case IClasspathEntry.CPE_LIBRARY:
				if (root.isArchive()) {
					String[] args= { JavaElementLabels.getElementLabel(root, JavaElementLabels.REFERENCED_ROOT_POST_QUALIFIED), BasicElementLabels.getJavaElementName(project.getElementName()) };
					return Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_archive_description, args);
				} else {
					String[] args= { JavaElementLabels.getElementLabel(root, JavaElementLabels.REFERENCED_ROOT_POST_QUALIFIED), BasicElementLabels.getJavaElementName(project.getElementName()) };
					return Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_classfolder_description, args);
				}
			case IClasspathEntry.CPE_VARIABLE: {
				String[] args= { JavaElementLabels.getElementLabel(root, 0), BasicElementLabels.getJavaElementName(project.getElementName()) };
				return Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_variable_description, args);
			}
			case IClasspathEntry.CPE_CONTAINER:
				try {
					String[] args= { JavaElementLabels.getContainerEntryLabel(entry.getPath(), root.getJavaProject()), BasicElementLabels.getJavaElementName(project.getElementName()) };
					return Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_library_description, args);
				} catch (JavaModelException e) {
					// ignore
				}
				break;
		}
		return null;
	}

}

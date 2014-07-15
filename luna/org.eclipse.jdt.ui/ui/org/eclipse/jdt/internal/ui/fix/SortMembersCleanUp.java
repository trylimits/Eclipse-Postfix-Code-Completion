/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Alex Blewitt - https://bugs.eclipse.org/bugs/show_bug.cgi?id=168954
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.fix;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import com.ibm.icu.text.MessageFormat;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;

import org.eclipse.debug.core.model.IBreakpoint;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.SortMembersFix;

import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

public class SortMembersCleanUp extends AbstractCleanUp {

	private HashSet<IResource> fTouchedFiles;

	public SortMembersCleanUp() {
		super();
    }

	public SortMembersCleanUp(Map<String, String> options) {
		super(options);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit= context.getAST();
		if (compilationUnit == null)
			return null;

		boolean sortMembers= isEnabled(CleanUpConstants.SORT_MEMBERS);
		ICleanUpFix fix= SortMembersFix.createCleanUp(compilationUnit, sortMembers, sortMembers && isEnabled(CleanUpConstants.SORT_MEMBERS_ALL));
		if (fix != null) {
			if (fTouchedFiles == null) {
				fTouchedFiles= new HashSet<IResource>();
			}
			fTouchedFiles.add(((ICompilationUnit)compilationUnit.getJavaElement()).getResource());
		}
		return fix;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RefactoringStatus checkPostConditions(IProgressMonitor monitor) throws CoreException {
		if (fTouchedFiles == null) {
			return super.checkPostConditions(monitor);
		} else {
			if (monitor == null)
				monitor= new NullProgressMonitor();

			monitor.beginTask("", fTouchedFiles.size()); //$NON-NLS-1$

			try {
				RefactoringStatus result= new RefactoringStatus();
    			for (Iterator<IResource> iterator= fTouchedFiles.iterator(); iterator.hasNext();) {
    	            IFile file= (IFile)iterator.next();
    	            if (containsRelevantMarkers(file)) {
    	            	String fileLocation= BasicElementLabels.getPathLabel(file.getProjectRelativePath(), false);
    	            	String projectName= file.getProject().getName();
						result.addWarning(MessageFormat.format(MultiFixMessages.SortMembersCleanUp_RemoveMarkersWarning0, new Object[] {fileLocation, projectName}));
    	            }

    	            monitor.worked(1);
                }

    			return result;
			} finally {
				monitor.done();
				fTouchedFiles= null;
			}

		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String[] getStepDescriptions() {
		if (isEnabled(CleanUpConstants.SORT_MEMBERS)) {
			if (isEnabled(CleanUpConstants.SORT_MEMBERS_ALL)) {
				return new String[] {MultiFixMessages.SortMembersCleanUp_AllMembers_description};
			} else {
				return new String[] {MultiFixMessages.SortMembersCleanUp_Excluding_description};
			}
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getPreview() {
		StringBuffer buf= new StringBuffer();

		buf.append("class SortExample {\n"); //$NON-NLS-1$

		if ((isEnabled(CleanUpConstants.SORT_MEMBERS) && isEnabled(CleanUpConstants.SORT_MEMBERS_ALL))) {
			buf.append("  private String bar;\n"); //$NON-NLS-1$
			buf.append("  private String foo;\n"); //$NON-NLS-1$
		} else {
			buf.append("  private String foo;\n"); //$NON-NLS-1$
			buf.append("  private String bar;\n"); //$NON-NLS-1$
		}

		if (isEnabled(CleanUpConstants.SORT_MEMBERS)) {
			buf.append("  private void bar() {}\n"); //$NON-NLS-1$
			buf.append("  private void foo() {}\n"); //$NON-NLS-1$
		} else {
			buf.append("  private void foo() {}\n"); //$NON-NLS-1$
			buf.append("  private void bar() {}\n"); //$NON-NLS-1$
		}

		buf.append("}\n"); //$NON-NLS-1$

		return buf.toString();
	}

    /**
     * {@inheritDoc}
     */
    @Override
	public CleanUpRequirements getRequirements() {
    	return new CleanUpRequirements(isEnabled(CleanUpConstants.SORT_MEMBERS), false, false, null);
    }

	private static boolean containsRelevantMarkers(IFile file) throws CoreException {
		IMarker[] bookmarks= file.findMarkers(IMarker.BOOKMARK, true, IResource.DEPTH_INFINITE);
		if (bookmarks.length != 0)
			return true;

		IMarker[] tasks= file.findMarkers(IMarker.TASK, true, IResource.DEPTH_INFINITE);
		if (tasks.length != 0)
			return true;

		IMarker[] breakpoints= file.findMarkers(IBreakpoint.BREAKPOINT_MARKER, true, IResource.DEPTH_INFINITE);
		if (breakpoints.length != 0)
			return true;

		return false;
	}

}

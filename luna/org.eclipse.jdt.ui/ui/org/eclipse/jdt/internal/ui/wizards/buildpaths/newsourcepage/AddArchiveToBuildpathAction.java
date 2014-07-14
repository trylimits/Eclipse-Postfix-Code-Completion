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
package org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ISetSelectionTarget;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.corext.buildpath.BuildpathDelta;
import org.eclipse.jdt.internal.corext.buildpath.CPJavaProject;
import org.eclipse.jdt.internal.corext.buildpath.ClasspathModifier;

import org.eclipse.jdt.ui.wizards.BuildPathDialogAccess;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;

//SelectedElements iff enabled: IJavaProject && size==1
public class AddArchiveToBuildpathAction extends BuildpathModifierAction {

	private final IRunnableContext fContext;

	public AddArchiveToBuildpathAction(IWorkbenchSite site) {
		this(site, null, PlatformUI.getWorkbench().getProgressService());
	}

	public AddArchiveToBuildpathAction(IRunnableContext context, ISetSelectionTarget selectionTarget) {
		this(null, selectionTarget, context);
    }

	private AddArchiveToBuildpathAction(IWorkbenchSite site, ISetSelectionTarget selectionTarget, IRunnableContext context) {
		super(site, selectionTarget, BuildpathModifierAction.ADD_LIB_TO_BP);

		fContext= context;

		setText(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_AddJarCP_label);
		setImageDescriptor(JavaPluginImages.DESC_OBJS_EXTJAR);
		setToolTipText(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_AddJarCP_tooltip);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getDetailedDescription() {
		return NewWizardMessages.PackageExplorerActionGroup_FormText_Default_toBuildpath_archives;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run() {

		final Shell shell= getShell();
		final IPath[] selected= BuildPathDialogAccess.chooseExternalJAREntries(shell);
		if (selected == null)
			return;

		try {
			final IJavaProject javaProject= (IJavaProject)getSelectedElements().get(0);
            IStatus status= ClasspathModifier.checkAddExternalJarsPrecondition(selected, CPJavaProject.createFromExisting(javaProject));
            if (status.getSeverity() == IStatus.ERROR) {
            	MessageDialog.openError(getShell(), NewWizardMessages.AddArchiveToBuildpathAction_InfoTitle, status.getMessage());
            	return;
            } else if (status.getSeverity() == IStatus.INFO) {
            	MessageDialog.openInformation(getShell(), NewWizardMessages.AddArchiveToBuildpathAction_InfoTitle, status.getMessage());
            } else if (status.getSeverity() == IStatus.WARNING) {
            	MessageDialog.openWarning(getShell(), NewWizardMessages.AddArchiveToBuildpathAction_InfoTitle, status.getMessage());
            }

        	final IRunnableWithProgress runnable= new IRunnableWithProgress() {
        		public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
        			try {
        				List<IJavaElement> result= addExternalJars(selected, javaProject, monitor);
        				if (result.size() > 0)
        					selectAndReveal(new StructuredSelection(result));
        			} catch (CoreException e) {
        				throw new InvocationTargetException(e);
        			}
        		}
        	};
        	fContext.run(false, false, runnable);
		} catch (final InvocationTargetException e) {
			if (e.getCause() instanceof CoreException) {
				showExceptionDialog((CoreException)e.getCause(), NewWizardMessages.AddArchiveToBuildpathAction_ErrorTitle);
			} else {
				JavaPlugin.log(e);
			}
		} catch (CoreException e) {
			showExceptionDialog(e, NewWizardMessages.AddArchiveToBuildpathAction_ErrorTitle);
			JavaPlugin.log(e);
        } catch (InterruptedException e) {
        }
	}

	private List<IJavaElement> addExternalJars(IPath[] jarPaths, IJavaProject project, IProgressMonitor monitor) throws CoreException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_AddToBuildpath, 4);

			CPJavaProject cpProject= CPJavaProject.createFromExisting(project);
			BuildpathDelta delta= ClasspathModifier.addExternalJars(jarPaths, cpProject);
			ClasspathModifier.commitClassPath(cpProject, new SubProgressMonitor(monitor, 4));

    		informListeners(delta);

    		List<CPListElement> addedEntries= delta.getAddedEntries();
			List<IJavaElement> result= new ArrayList<IJavaElement>(addedEntries.size());
			for (int i= 0; i < addedEntries.size(); i++) {
				IClasspathEntry entry= addedEntries.get(i).getClasspathEntry();
				IJavaElement elem= project.findPackageFragmentRoot(entry.getPath());
				if (elem != null) {
					result.add(elem);
				}
			}
			monitor.worked(1);
			return result;
		} finally {
			monitor.done();
		}
	}

	@Override
	protected boolean canHandle(IStructuredSelection selection) {
		if (selection.size() != 1)
			return false;

		Object first= selection.getFirstElement();
		if (!(first instanceof IJavaProject))
			return false;

		return true;
	}
}

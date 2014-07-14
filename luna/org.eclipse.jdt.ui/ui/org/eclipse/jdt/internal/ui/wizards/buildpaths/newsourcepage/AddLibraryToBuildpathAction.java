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
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;

import org.eclipse.ui.IWorkbenchSite;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.buildpath.BuildpathDelta;
import org.eclipse.jdt.internal.corext.buildpath.ClasspathModifier;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.packageview.ClassPathContainer;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.ClasspathContainerWizard;

//SelectedElements: IJavaProject && size == 1
public class AddLibraryToBuildpathAction extends BuildpathModifierAction {

	public AddLibraryToBuildpathAction(IWorkbenchSite site) {
		super(site, null, BuildpathModifierAction.ADD_LIB_TO_BP);

		setText(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_AddLibCP_label);
		setImageDescriptor(JavaPluginImages.DESC_OBJS_LIBRARY);
		setToolTipText(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_AddLibCP_tooltip);
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getDetailedDescription() {
	    return NewWizardMessages.PackageExplorerActionGroup_FormText_Default_toBuildpath_library;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run() {
		final IJavaProject project= (IJavaProject)getSelectedElements().get(0);

		Shell shell= getShell();
		if (shell == null) {
			shell= JavaPlugin.getActiveWorkbenchShell();
		}

		IClasspathEntry[] classpath;
		try {
			classpath= project.getRawClasspath();
		} catch (JavaModelException e1) {
			showExceptionDialog(e1, NewWizardMessages.AddLibraryToBuildpathAction_ErrorTitle);
			return;
		}

		ClasspathContainerWizard wizard= new ClasspathContainerWizard((IClasspathEntry) null, project, classpath) {

			/**
			 * {@inheritDoc}
			 */
			@Override
			public boolean performFinish() {
				if (super.performFinish()) {
					IWorkspaceRunnable op= new IWorkspaceRunnable() {
						public void run(IProgressMonitor monitor) throws CoreException, OperationCanceledException {
							try {
								finishPage(monitor);
							} catch (InterruptedException e) {
								throw new OperationCanceledException(e.getMessage());
							}
						}
					};
					try {
						ISchedulingRule rule= null;
						Job job= Job.getJobManager().currentJob();
						if (job != null)
							rule= job.getRule();
						IRunnableWithProgress runnable= null;
						if (rule != null)
							runnable= new WorkbenchRunnableAdapter(op, rule, true);
						else
							runnable= new WorkbenchRunnableAdapter(op, ResourcesPlugin.getWorkspace().getRoot());
						getContainer().run(false, true, runnable);
					} catch (InvocationTargetException e) {
						JavaPlugin.log(e);
						return false;
					} catch  (InterruptedException e) {
						return false;
					}
					return true;
				}
				return false;
			}

			private void finishPage(IProgressMonitor pm) throws InterruptedException {
				IClasspathEntry[] selected= getNewEntries();
				if (selected != null) {
					try {
						pm.beginTask(NewWizardMessages.ClasspathModifier_Monitor_AddToBuildpath, 4);

						List<CPListElement> addedEntries= new ArrayList<CPListElement>();
						for (int i= 0; i < selected.length; i++) {
							addedEntries.add(CPListElement.create(selected[i], true, project));
						}

						pm.worked(1);
						if (pm.isCanceled())
							throw new InterruptedException();

						List<CPListElement> existingEntries= ClasspathModifier.getExistingEntries(project);
						ClasspathModifier.setNewEntry(existingEntries, addedEntries, project, new SubProgressMonitor(pm, 1));
						if (pm.isCanceled())
							throw new InterruptedException();

						ClasspathModifier.commitClassPath(existingEntries, project, new SubProgressMonitor(pm, 1));

			        	BuildpathDelta delta= new BuildpathDelta(getToolTipText());
			        	delta.setNewEntries(existingEntries.toArray(new CPListElement[existingEntries.size()]));
			        	informListeners(delta);

						List<ClassPathContainer> result= new ArrayList<ClassPathContainer>(addedEntries.size());
						for (int i= 0; i < addedEntries.size(); i++) {
							result.add(new ClassPathContainer(project, selected[i]));
						}
						selectAndReveal(new StructuredSelection(result));

						pm.worked(1);
					} catch (CoreException e) {
						showExceptionDialog(e, NewWizardMessages.AddLibraryToBuildpathAction_ErrorTitle);
					} finally {
						pm.done();
					}
				}
			}
		};
		wizard.setNeedsProgressMonitor(true);

		WizardDialog dialog= new WizardDialog(shell, wizard);
		PixelConverter converter= new PixelConverter(shell);
		dialog.setMinimumPageSize(converter.convertWidthInCharsToPixels(70), converter.convertHeightInCharsToPixels(20));
		dialog.create();
		dialog.open();
	}

	@Override
	protected boolean canHandle(IStructuredSelection selection) {
		if (selection.size() != 1)
			return false;

		if (!(selection.getFirstElement() instanceof IJavaProject))
			return false;

		return true;
	}

}

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
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

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
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.buildpath.BuildpathDelta;
import org.eclipse.jdt.internal.corext.buildpath.ClasspathModifier;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;

//SelectedElements iff enabled: IFile
public class AddSelectedLibraryToBuildpathAction extends BuildpathModifierAction {

	private final IRunnableContext fContext;

	public AddSelectedLibraryToBuildpathAction(IWorkbenchSite site) {
		this(site, null, PlatformUI.getWorkbench().getProgressService());
	}

	public AddSelectedLibraryToBuildpathAction(IRunnableContext context, ISetSelectionTarget selectionTarget) {
		this(null, selectionTarget, context);
    }

	private AddSelectedLibraryToBuildpathAction(IWorkbenchSite site, ISetSelectionTarget selectionTarget, IRunnableContext context) {
		super(site, selectionTarget, BuildpathModifierAction.ADD_SEL_LIB_TO_BP);

		fContext= context;

		setText(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_AddSelLibToCP_label);
		setImageDescriptor(JavaPluginImages.DESC_OBJS_EXTJAR);
		setToolTipText(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_AddSelLibToCP_tooltip);
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getDetailedDescription() {
		if (!isEnabled())
			return null;

		IFile file= (IFile)getSelectedElements().get(0);
        IJavaProject project= JavaCore.create(file.getProject());

        try {
	        if (ClasspathModifier.isArchive(file, project)) {
	            String name= ClasspathModifier.escapeSpecialChars(BasicElementLabels.getResourceName(file));
	            return Messages.format(NewWizardMessages.PackageExplorerActionGroup_FormText_ArchiveToBuildpath, name);
	        }
        } catch (JavaModelException e) {
	        JavaPlugin.log(e);
        }

        return NewWizardMessages.PackageExplorerActionGroup_FormText_Default_toBuildpath;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run() {
		try {
			final IFile[] files= getSelectedElements().toArray(new IFile[getSelectedElements().size()]);

			final IRunnableWithProgress runnable= new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					try {
				        IJavaProject project= JavaCore.create(files[0].getProject());
				        List<IJavaElement> result= addLibraryEntries(files, project, monitor);
						selectAndReveal(new StructuredSelection(result));
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					}
				}
			};
			fContext.run(false, false, runnable);
		} catch (final InvocationTargetException e) {
			if (e.getCause() instanceof CoreException) {
				showExceptionDialog((CoreException)e.getCause(), NewWizardMessages.AddSelectedLibraryToBuildpathAction_ErrorTitle);
			} else {
				JavaPlugin.log(e);
			}
		} catch (final InterruptedException e) {
		}
	}

	private List<IJavaElement> addLibraryEntries(IFile[] resources, IJavaProject project, IProgressMonitor monitor) throws CoreException {
		List<CPListElement> addedEntries= new ArrayList<CPListElement>();
		try {
			monitor.beginTask(NewWizardMessages.ClasspathModifier_Monitor_AddToBuildpath, 4);
			for (int i= 0; i < resources.length; i++) {
				IResource res= resources[i];
				addedEntries.add(new CPListElement(project, IClasspathEntry.CPE_LIBRARY, res.getFullPath(), res));
			}
			monitor.worked(1);

			List<CPListElement> existingEntries= ClasspathModifier.getExistingEntries(project);
			ClasspathModifier.setNewEntry(existingEntries, addedEntries, project, new SubProgressMonitor(monitor, 1));
			ClasspathModifier.commitClassPath(existingEntries, project, new SubProgressMonitor(monitor, 1));

        	BuildpathDelta delta= new BuildpathDelta(getToolTipText());
        	delta.setNewEntries(existingEntries.toArray(new CPListElement[existingEntries.size()]));
        	informListeners(delta);

			List<IJavaElement> result= new ArrayList<IJavaElement>(addedEntries.size());
			for (int i= 0; i < resources.length; i++) {
				IResource res= resources[i];
				IJavaElement elem= project.getPackageFragmentRoot(res);
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
	protected boolean canHandle(IStructuredSelection elements) {
		if (elements.size() == 0)
			return false;

		try {
			for (Iterator<?> iter= elements.iterator(); iter.hasNext();) {
				Object element= iter.next();
				if (element instanceof IFile) {
					IFile file= (IFile)element;
					IJavaProject project= JavaCore.create(file.getProject());
					if (project == null)
						return false;

					if (!ClasspathModifier.isArchive(file, project))
						return false;
				} else {
					return false;
				}
			}
		} catch (CoreException e) {
			return false;
		}
		return true;
	}
}

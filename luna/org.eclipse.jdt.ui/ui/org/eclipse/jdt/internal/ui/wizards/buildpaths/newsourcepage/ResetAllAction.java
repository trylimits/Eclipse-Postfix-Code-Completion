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
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.ui.part.ISetSelectionTarget;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.buildpath.BuildpathDelta;
import org.eclipse.jdt.internal.corext.buildpath.ClasspathModifier;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;

//TODO: Use global history
public class ResetAllAction extends BuildpathModifierAction {

	private final HintTextGroup fProvider;
	private final IRunnableContext fContext;
	private IJavaProject fJavaProject;
	private List<CPListElement> fEntries;
	private IPath fOutputLocation;

	public ResetAllAction(HintTextGroup provider, IRunnableContext context, ISetSelectionTarget selectionTarget) {
		super(null, selectionTarget, BuildpathModifierAction.RESET_ALL);

		fProvider= provider;
		fContext= context;

		setImageDescriptor(JavaPluginImages.DESC_ELCL_CLEAR);
		setDisabledImageDescriptor(JavaPluginImages.DESC_DLCL_CLEAR);
		setText(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_ClearAll_label);
		setToolTipText(NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_ClearAll_tooltip);
		setEnabled(false);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getDetailedDescription() {
		return NewWizardMessages.PackageExplorerActionGroup_FormText_Default_ResetAll;
	}

	public void setBreakPoint(IJavaProject javaProject) {
		fJavaProject= javaProject;
		if (fJavaProject.exists()) {
			try {
	            fEntries= ClasspathModifier.getExistingEntries(fJavaProject);
	            fOutputLocation= fJavaProject.getOutputLocation();
            } catch (JavaModelException e) {
	            JavaPlugin.log(e);
	            return;
            }
			setEnabled(true);
		} else {
			JavaCore.addElementChangedListener(new IElementChangedListener() {

				public void elementChanged(ElementChangedEvent event) {
					if (fJavaProject.exists()) {
						try {
	                        fEntries= ClasspathModifier.getExistingEntries(fJavaProject);
	                        fOutputLocation= fJavaProject.getOutputLocation();
                        } catch (JavaModelException e) {
                        	JavaPlugin.log(e);
                        	return;
                        } finally {
							JavaCore.removeElementChangedListener(this);
                        }
						setEnabled(true);
					}
		        }

			}, ElementChangedEvent.POST_CHANGE);
		}
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run() {

		try {
	        final IRunnableWithProgress runnable= new IRunnableWithProgress() {
	        	public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {

        			monitor.beginTask("", 3); //$NON-NLS-1$
	        		try {
	        			if (!hasChange(fJavaProject))
	        				return;

	        			BuildpathDelta delta= new BuildpathDelta(getToolTipText());

	        			ClasspathModifier.commitClassPath(fEntries, fJavaProject, monitor);
        				delta.setNewEntries(fEntries.toArray(new CPListElement[fEntries.size()]));

	        			fJavaProject.setOutputLocation(fOutputLocation, monitor);
	        			delta.setDefaultOutputLocation(fOutputLocation);

	        			for (Iterator<IResource> iterator= fProvider.getCreatedResources().iterator(); iterator.hasNext();) {
	                        IResource resource= iterator.next();
	                        resource.delete(false, null);
	                        delta.addDeletedResource(resource);
                        }

	        			fProvider.resetCreatedResources();

	                    informListeners(delta);

	            		selectAndReveal(new StructuredSelection(fJavaProject));
	                } catch (JavaModelException e) {
	                    showExceptionDialog(e, NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_ClearAll_tooltip);
	                } catch (CoreException e) {
	                    showExceptionDialog(e, NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_ClearAll_tooltip);
                    } finally {
	                	monitor.done();
	                }
	        	}
	        };
	        fContext.run(false, false, runnable);
        } catch (InvocationTargetException e) {
			if (e.getCause() instanceof CoreException) {
				showExceptionDialog((CoreException)e.getCause(), NewWizardMessages.NewSourceContainerWorkbookPage_ToolBar_ClearAll_tooltip);
			} else {
				JavaPlugin.log(e);
			}
        } catch (InterruptedException e) {
        }
	}


	/**
     * {@inheritDoc}
     */
    @Override
	protected boolean canHandle(IStructuredSelection elements) {
    	if (fJavaProject == null)
    		return false;

	    return true;
    }


	//TODO: Remove, action should be disabled if not hasChange
	private boolean hasChange(IJavaProject project) throws JavaModelException {
		if (!project.getOutputLocation().equals(fOutputLocation))
            return true;

		IClasspathEntry[] currentEntries= project.getRawClasspath();
        if (currentEntries.length != fEntries.size())
            return true;

        int i= 0;
        for (Iterator<CPListElement> iterator= fEntries.iterator(); iterator.hasNext();) {
	        CPListElement oldEntrie= iterator.next();
	        if (!oldEntrie.getClasspathEntry().equals(currentEntries[i]))
	        	return true;
	        i++;
        }
        return false;
	}
}

/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.IWorkspaceRunnable;

import org.eclipse.ui.dialogs.PropertyPage;

import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathSupport;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;

public class JavaCompilerPropertyPage extends PropertyPage {

	public static final String PROP_ID= "org.eclipse.jdt.ui.propertyPages.JavaCompilerPropertyPage"; //$NON-NLS-1$

	private IJavaProject fProject;

	private CPListElement fElement;

	private boolean fIsValidElement;

	private SelectionButtonDialogField fIgnoreOptionalProblemsField;

	/**
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public void createControl(Composite parent) {
		IAdaptable adaptable= getElement();
		IJavaElement elem= (IJavaElement) adaptable.getAdapter(IJavaElement.class);
		try {
			if (elem instanceof IPackageFragmentRoot) {
				fProject= elem.getJavaProject();
				fElement= CPListElement.createFromExisting(((IPackageFragmentRoot) elem).getRawClasspathEntry(), fProject);
				fIsValidElement= fElement != null;
			} else {
				fIsValidElement= false;
				setDescription(PreferencesMessages.JavaCompilerPropertyPage_invalid_element_selection);
			}
		} catch (JavaModelException e) {
			fIsValidElement= false;
			setDescription(PreferencesMessages.JavaCompilerPropertyPage_invalid_element_selection);
		}
		super.createControl(parent);
	}

	/*
	 * @see PreferencePage#createContents(Composite)
	 */
	@Override
	protected Control createContents(Composite parent) {
		if (!fIsValidElement) {
			return new Composite(parent, SWT.NONE);
		}
		Composite composite= new Composite(parent, SWT.NONE);
		composite.setFont(parent.getFont());
		GridLayout topLayout= new GridLayout();
		topLayout.marginWidth= 0;
		topLayout.marginHeight= 0;
		composite.setLayout(topLayout);

		fIgnoreOptionalProblemsField= new SelectionButtonDialogField(SWT.CHECK);
		fIgnoreOptionalProblemsField.setLabelText(PreferencesMessages.JavaCompilerPropertyPage_ignore_optional_problems_label);
		fIgnoreOptionalProblemsField.setSelection(isIgnoringOptionalProblems());
		fIgnoreOptionalProblemsField.doFillIntoGrid(composite, 1);
		return composite;
	}

	/*
	 * @see PreferencePage#performDefaults()
	 */
	@Override
	protected void performDefaults() {
		fIgnoreOptionalProblemsField.setSelection(false);
		super.performDefaults();
	}

	/**
	 * @see org.eclipse.jface.preference.IPreferencePage#performOk()
	 */
	@Override
	public boolean performOk() {
		if (fIsValidElement && fIgnoreOptionalProblemsField.isSelected() != isIgnoringOptionalProblems()) {
			String newValue= fIgnoreOptionalProblemsField.isSelected() ? "true" : null; //$NON-NLS-1$
			fElement.setAttribute(IClasspathAttribute.IGNORE_OPTIONAL_PROBLEMS, newValue);
			IWorkspaceRunnable runnable= getRunnable(getShell(), fProject, fElement.getClasspathEntry());
			WorkbenchRunnableAdapter op= new WorkbenchRunnableAdapter(runnable);
			op.runAsUserJob(PreferencesMessages.BuildPathsPropertyPage_job_title, null);
		}
		return true;
	}

	private static IWorkspaceRunnable getRunnable(final Shell shell, final IJavaProject project, final IClasspathEntry entry) {
		return new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {
				String[] changedAttributes= { CPListElement.IGNORE_OPTIONAL_PROBLEMS };
				BuildPathSupport.modifyClasspathEntry(shell, entry, changedAttributes, project, null, entry.getReferencingEntry() != null, monitor);
			}
		};
	}

	private boolean isIgnoringOptionalProblems() {
		return "true".equals(fElement.getAttribute(IClasspathAttribute.IGNORE_OPTIONAL_PROBLEMS)); //$NON-NLS-1$
	}
}

/*******************************************************************************
 * Copyright (c) 2006, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackager;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.TrayDialog;

import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.core.refactoring.RefactoringDescriptorProxy;
import org.eclipse.ltk.core.refactoring.history.RefactoringHistory;
import org.eclipse.ltk.ui.refactoring.RefactoringUI;
import org.eclipse.ltk.ui.refactoring.history.ISortableRefactoringHistoryControl;
import org.eclipse.ltk.ui.refactoring.history.RefactoringHistoryControlConfiguration;

import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;

/**
 * Dialog to configure the refactorings to export.
 *
 * @since 3.2
 */
public final class JarRefactoringDialog extends TrayDialog {

	/** The sort dialog setting */
	private static final String SETTING_SORT= "org.eclipse.jdt.ui.jar.export.sortRefactorings"; //$NON-NLS-1$

	/** The jar package data */
	private final JarPackageData fData;

	/** The export structural button */
	private Button fExportStructural= null;

	/** The refactoring history */
	private final RefactoringHistory fHistory;

	/** The refactoring history control */
	private ISortableRefactoringHistoryControl fHistoryControl= null;

	/** The dialog settings */
	private final IDialogSettings fSettings;

	/**
	 * Creates a new jar refactoring dialog.
	 *
	 * @param shell
	 *            the parent shell
	 * @param settings
	 *            the dialog settings, or <code>null</code>
	 * @param data
	 *            the jar package data
	 * @param history
	 *            the refactoring history
	 */
	public JarRefactoringDialog(final Shell shell, final IDialogSettings settings, final JarPackageData data, final RefactoringHistory history) {
		super(shell);
		Assert.isNotNull(data);
		Assert.isNotNull(history);
		fSettings= settings;
		fData= data;
		fHistory= history;
	}

	/*
	 * @see org.eclipse.jface.dialogs.Dialog#isResizable()
	 * @since 3.4
	 */
	@Override
	protected boolean isResizable() {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void buttonPressed(final int buttonId) {
		if (buttonId == IDialogConstants.OK_ID) {
			fData.setRefactoringAware(true);
			final RefactoringDescriptorProxy[] descriptors= fHistoryControl.getCheckedDescriptors();
			Set<IProject> set= new HashSet<IProject>();
			IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
			for (int index= 0; index < descriptors.length; index++) {
				final String project= descriptors[index].getProject();
				if (project != null && !"".equals(project)) //$NON-NLS-1$
					set.add(root.getProject(project));
			}
			fData.setRefactoringProjects(set.toArray(new IProject[set.size()]));
			fData.setRefactoringDescriptors(descriptors);
			fData.setExportStructuralOnly(fExportStructural.getSelection());
			final IDialogSettings settings= fSettings;
			if (settings != null)
				settings.put(SETTING_SORT, fHistoryControl.isSortByProjects());
		}
		super.buttonPressed(buttonId);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void configureShell(final Shell shell) {
		super.configureShell(shell);
		shell.setText(JarPackagerMessages.JarRefactoringDialog_dialog_title);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(shell, IJavaHelpContextIds.JARPACKAGER_REFACTORING_DIALOG);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void create() {
		super.create();
		getButton(OK).setEnabled(!fHistory.isEmpty());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Control createDialogArea(final Composite parent) {
		final Composite container= (Composite) super.createDialogArea(parent);
		initializeDialogUnits(container);
		final Composite composite= new Composite(container, SWT.NULL);
		final GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL));
		final RefactoringHistoryControlConfiguration configuration= new RefactoringHistoryControlConfiguration(null, true, true) {

			@Override
			public final String getWorkspaceCaption() {
				return JarPackagerMessages.JarRefactoringDialog_workspace_caption;
			}
		};
		fHistoryControl= (ISortableRefactoringHistoryControl) RefactoringUI.createSortableRefactoringHistoryControl(composite, configuration);
		fHistoryControl.createControl();
		boolean sortProjects= true;
		final IDialogSettings settings= fSettings;
		if (settings != null)
			sortProjects= settings.getBoolean(SETTING_SORT);
		if (sortProjects)
			fHistoryControl.sortByProjects();
		else
			fHistoryControl.sortByDate();
		GridData data= new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL);
		data.heightHint= convertHeightInCharsToPixels(32);
		data.widthHint= convertWidthInCharsToPixels(72);
		fHistoryControl.getControl().setLayoutData(data);
		fHistoryControl.setInput(fHistory);
		fHistoryControl.setCheckedDescriptors(fData.getRefactoringDescriptors());
		createPlainLabel(composite, JarPackagerMessages.JarPackageWizardPage_options_label);
		createOptionsGroup(composite);
		Dialog.applyDialogFont(parent);
		return composite;
	}

	/**
	 * Create the export option group.
	 *
	 * @param parent
	 *            the parent composite
	 */
	protected void createOptionsGroup(Composite parent) {
		Composite optionsGroup= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		optionsGroup.setLayout(layout);

		fExportStructural= new Button(optionsGroup, SWT.CHECK | SWT.LEFT);
		fExportStructural.setText(JarPackagerMessages.JarRefactoringDialog_export_structural);
		fExportStructural.setSelection(fData.isExportStructuralOnly());
	}

	/**
	 * Creates a new label.
	 *
	 * @param parent
	 *            the parent control
	 * @param text
	 *            the label text
	 * @return the new label control
	 */
	protected Label createPlainLabel(Composite parent, String text) {
		Label label= new Label(parent, SWT.NONE);
		label.setText(text);
		label.setFont(parent.getFont());
		GridData data= new GridData();
		data.verticalAlignment= GridData.FILL;
		data.horizontalAlignment= GridData.FILL;
		label.setLayoutData(data);
		return label;
	}
}
/*******************************************************************************
 * Copyright (c) 2004, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.wizards;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;

/**
 * Wizard page to create a new annotation type.
 * <p>
 * Note: This class is not intended to be subclassed, but clients can instantiate.
 * To implement a different kind of a new annotation wizard page, extend <code>NewTypeWizardPage</code>.
 * </p>
 *
 * @since 3.1
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class NewAnnotationWizardPage extends NewTypeWizardPage {

    private final static String PAGE_NAME= "NewAnnotationWizardPage"; //$NON-NLS-1$
    private final static int TYPE = NewTypeWizardPage.ANNOTATION_TYPE;

	/**
	 * Create a new <code>NewAnnotationWizardPage</code>
	 */
	public NewAnnotationWizardPage() {
		super(TYPE, PAGE_NAME);

		setTitle(NewWizardMessages.NewAnnotationWizardPage_title);
		setDescription(NewWizardMessages.NewAnnotationWizardPage_description);
	}

	// -------- Initialization ---------

	/**
	 * The wizard owning this page is responsible for calling this method with the
	 * current selection. The selection is used to initialize the fields of the wizard
	 * page.
	 *
	 * @param selection used to initialize the fields
	 */
	public void init(IStructuredSelection selection) {
		IJavaElement jelem= getInitialJavaElement(selection);

		initContainerPage(jelem);
		initTypePage(jelem);
		doStatusUpdate();
	}

	// ------ validation --------

	private void doStatusUpdate() {
		// all used component status
		IStatus[] status= new IStatus[] {
			fContainerStatus,
			isEnclosingTypeSelected() ? fEnclosingTypeStatus : fPackageStatus,
			fTypeNameStatus,
			fModifierStatus,
		};

		// the mode severe status will be displayed and the OK button enabled/disabled.
		updateStatus(status);
	}


	/*
	 * @see NewContainerWizardPage#handleFieldChanged
	 */
	@Override
	protected void handleFieldChanged(String fieldName) {
		super.handleFieldChanged(fieldName);

		doStatusUpdate();
	}


	// ------ UI --------

	/*
	 * @see WizardPage#createControl
	 */
	public void createControl(Composite parent) {
		initializeDialogUnits(parent);

		Composite composite= new Composite(parent, SWT.NONE);

		int nColumns= 4;

		GridLayout layout= new GridLayout();
		layout.numColumns= nColumns;
		composite.setLayout(layout);

		createContainerControls(composite, nColumns);
		createPackageControls(composite, nColumns);
		createEnclosingTypeControls(composite, nColumns);

		createSeparator(composite, nColumns);

		createTypeNameControls(composite, nColumns);
		createModifierControls(composite, nColumns);

		createCommentControls(composite, nColumns);
		enableCommentControl(true);

		setControl(composite);

		Dialog.applyDialogFont(composite);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IJavaHelpContextIds.NEW_ANNOTATION_WIZARD_PAGE);
	}

	/*
	 * @see WizardPage#becomesVisible
	 */
	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible) {
			setFocus();
		}
	}
}

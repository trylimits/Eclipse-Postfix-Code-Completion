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

package org.eclipse.jdt.internal.ui.refactoring.reorg;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

import org.eclipse.jdt.internal.corext.refactoring.rename.RenamingNameSuggestor;
import org.eclipse.jdt.internal.corext.refactoring.tagging.INameUpdating;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;

public class RenameRefactoringWizard extends RefactoringWizard {

	private final String fInputPageDescription;
	private final String fPageContextHelpId;
	private final ImageDescriptor fInputPageImageDescriptor;

	// dialog settings constants:

	/**
	 * Dialog settings key (value is of type boolean).
	 */
	public static final String UPDATE_TEXTUAL_MATCHES= "updateTextualMatches"; //$NON-NLS-1$
	/**
	 * Dialog settings key (value is of type boolean).
	 */
	public static final String UPDATE_QUALIFIED_NAMES= "updateQualifiedNames"; //$NON-NLS-1$
	/**
	 * Dialog settings key (value is of type String).
	 */
	public static final String QUALIFIED_NAMES_PATTERNS= "patterns"; //$NON-NLS-1$

	/**
	 * Dialog settings key (value is of type boolean).
	 */
	public static final String TYPE_UPDATE_SIMILAR_ELEMENTS= "updateSimilarElements"; //$NON-NLS-1$
	/**
	 * Dialog settings key (value is of type int).
	 * @see RenamingNameSuggestor
	 */
	public static final String TYPE_SIMILAR_MATCH_STRATEGY= "updateSimilarElementsMatchStrategy"; //$NON-NLS-1$

	/**
	 * Dialog settings key (value is of type boolean).
	 */
	public static final String PACKAGE_RENAME_SUBPACKAGES= "renameSubpackages"; //$NON-NLS-1$

	/**
	 * Dialog settings key (value is of type boolean).
	 */
	public static final String FIELD_RENAME_GETTER= "renameGetter"; //$NON-NLS-1$
	/**
	 * Dialog settings key (value is of type boolean).
	 */
	public static final String FIELD_RENAME_SETTER= "renameSetter"; //$NON-NLS-1$


	public RenameRefactoringWizard(Refactoring refactoring, String defaultPageTitle, String inputPageDescription,
			ImageDescriptor inputPageImageDescriptor, String pageContextHelpId) {
		super(refactoring, DIALOG_BASED_USER_INTERFACE);
		setDefaultPageTitle(defaultPageTitle);
		fInputPageDescription= inputPageDescription;
		fInputPageImageDescriptor= inputPageImageDescriptor;
		fPageContextHelpId= pageContextHelpId;
		setDialogSettings(JavaPlugin.getDefault().getDialogSettings());
	}

	/* non java-doc
	 * @see RefactoringWizard#addUserInputPages
	 */
	@Override
	protected void addUserInputPages() {
		String initialSetting= getNameUpdating().getCurrentElementName();
		RenameInputWizardPage inputPage= createInputPage(fInputPageDescription, initialSetting);
		inputPage.setImageDescriptor(fInputPageImageDescriptor);
		addPage(inputPage);
	}

	private INameUpdating getNameUpdating() {
		return (INameUpdating)getRefactoring().getAdapter(INameUpdating.class);
	}

	protected RenameInputWizardPage createInputPage(String message, String initialSetting) {
		return new RenameInputWizardPage(message, fPageContextHelpId, true, initialSetting) {
			@Override
			protected RefactoringStatus validateTextField(String text) {
				return validateNewName(text);
			}
		};
	}

	/**
	 * Sets a new name, validates the input, and returns the status.
	 *
	 * @param newName the new name
	 * @return validation status
	 */
	protected RefactoringStatus validateNewName(String newName) {
		INameUpdating ref= getNameUpdating();
		ref.setNewElementName(newName);
		try{
			return ref.checkNewElementName(newName);
		} catch (CoreException e){
			JavaPlugin.log(e);
			return RefactoringStatus.createFatalErrorStatus(RefactoringMessages.RenameRefactoringWizard_internal_error);
		}
	}
}

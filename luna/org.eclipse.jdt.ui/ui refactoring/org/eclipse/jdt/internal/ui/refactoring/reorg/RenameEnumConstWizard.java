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

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;

public final class RenameEnumConstWizard extends RenameRefactoringWizard {

	public RenameEnumConstWizard(Refactoring refactoring) {
		super(refactoring, RefactoringMessages.RenameEnumConstWizard_defaultPageTitle, RefactoringMessages.RenameEnumConstWizard_inputPage_description, JavaPluginImages.DESC_WIZBAN_REFACTOR_FIELD, IJavaHelpContextIds.RENAME_FIELD_WIZARD_PAGE);
	}

	@Override
	protected RenameInputWizardPage createInputPage(String message, String initialSetting) {
		return new RenameInputWizardPage(message, IJavaHelpContextIds.RENAME_FIELD_WIZARD_PAGE, true, initialSetting) {

			@Override
			protected RefactoringStatus validateTextField(String text) {
				return validateNewName(text);
			}
		};
	}
}

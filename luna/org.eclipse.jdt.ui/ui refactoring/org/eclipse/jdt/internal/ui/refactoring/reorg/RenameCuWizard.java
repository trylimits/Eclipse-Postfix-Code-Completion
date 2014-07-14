/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman, mpchapman@gmail.com - 89977 Make JDT .java agnostic
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring.reorg;


import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.refactoring.rename.RenameCompilationUnitProcessor;
import org.eclipse.jdt.internal.corext.refactoring.tagging.INameUpdating;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;

public class RenameCuWizard extends RenameTypeWizard {

	public RenameCuWizard(Refactoring refactoring) {
		super(refactoring,
			RefactoringMessages.RenameCuWizard_defaultPageTitle,
			RefactoringMessages.RenameCuWizard_inputPage_description,
			JavaPluginImages.DESC_WIZBAN_REFACTOR_CU,
			IJavaHelpContextIds.RENAME_CU_WIZARD_PAGE);
	}

	@Override
	protected RefactoringStatus validateNewName(String newName) {
		String fullName= JavaModelUtil.getRenamedCUName(getCompilationUnit(), newName);
		return super.validateNewName(fullName);
	}

	private ICompilationUnit getCompilationUnit() {
		return (ICompilationUnit) getCompilationUnitProcessor().getElements()[0];
	}

	@Override
	protected RenameInputWizardPage createInputPage(String message, String initialSetting) {
		return new RenameTypeWizardInputPage(message, IJavaHelpContextIds.RENAME_CU_WIZARD_PAGE, true, initialSetting) {
			@Override
			protected RefactoringStatus validateTextField(String text) {
				return validateNewName(text);
			}
			@Override
			protected String getNewName(INameUpdating nameUpdating) {
				String result= nameUpdating.getNewElementName();
				// If renaming a CU we have to remove the java file extension
				return JavaCore.removeJavaLikeExtension(result);
			}
		};
	}

	@Override
	protected boolean isRenameType() {
		// the flag 'willRenameType' may change in checkInitialConditions(), but
		// only from true to false.
		return getCompilationUnitProcessor().isWillRenameType();
	}

	private RenameCompilationUnitProcessor getCompilationUnitProcessor() {
		return ((RenameCompilationUnitProcessor) ((RenameRefactoring) getRefactoring()).getProcessor());
	}
}

/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
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

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;

public class RenameJavaProjectWizard extends RenameRefactoringWizard {

	public RenameJavaProjectWizard(Refactoring refactoring) {
		super(refactoring,
			RefactoringMessages.RenameJavaProject_defaultPageTitle,
			RefactoringMessages.RenameJavaProject_inputPage_description,
			JavaPluginImages.DESC_WIZBAN_REFACTOR,
			IJavaHelpContextIds.RENAME_JAVA_PROJECT_WIZARD_PAGE);
	}
}

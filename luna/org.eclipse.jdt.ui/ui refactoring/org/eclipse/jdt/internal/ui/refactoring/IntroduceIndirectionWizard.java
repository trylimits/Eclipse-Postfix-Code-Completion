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
package org.eclipse.jdt.internal.ui.refactoring;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

import org.eclipse.jdt.internal.corext.refactoring.code.IntroduceIndirectionRefactoring;

/**
 * @since 3.2
 */
public class IntroduceIndirectionWizard extends RefactoringWizard {

	/**
	 * Constructor for IntroduceIndirectionWizard.
	 * @param ref the refactoring
	 * @param pageTitle the page title
	 */
	public IntroduceIndirectionWizard(IntroduceIndirectionRefactoring ref, String pageTitle) {
		super(ref, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
		setDefaultPageTitle(pageTitle);
	}

	/**
	 * @see RefactoringWizard#addUserInputPages
	 */
	@Override
	protected void addUserInputPages() {
		IntroduceIndirectionInputPage page= new IntroduceIndirectionInputPage("IntroduceIndirectionInputPage"); //$NON-NLS-1$
		addPage(page);
	}

	public IntroduceIndirectionRefactoring getIntroduceIndirectionRefactoring() {
		return (IntroduceIndirectionRefactoring) getRefactoring();
	}
}

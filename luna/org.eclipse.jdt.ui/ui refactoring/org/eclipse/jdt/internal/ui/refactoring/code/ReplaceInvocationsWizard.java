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
package org.eclipse.jdt.internal.ui.refactoring.code;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

import org.eclipse.jdt.internal.corext.refactoring.code.ReplaceInvocationsRefactoring;

import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;

public class ReplaceInvocationsWizard extends RefactoringWizard {

	public ReplaceInvocationsWizard(ReplaceInvocationsRefactoring ref){
		super(ref, DIALOG_BASED_USER_INTERFACE);
		setDefaultPageTitle(RefactoringMessages.ReplaceInvocationsWizard_title);
	}

	@Override
	protected void addUserInputPages(){
		addPage(new ReplaceInvocationsInputPage());
	}
}

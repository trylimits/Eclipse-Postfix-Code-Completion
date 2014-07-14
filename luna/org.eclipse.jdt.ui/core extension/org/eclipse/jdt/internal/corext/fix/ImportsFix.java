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
package org.eclipse.jdt.internal.corext.fix;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.search.TypeNameMatch;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.OrganizeImportsOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.OrganizeImportsOperation.IChooseImportQuery;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

public class ImportsFix extends TextEditFix {

	public static ICleanUpFix createCleanUp(final CompilationUnit cu, CodeGenerationSettings settings, boolean organizeImports, RefactoringStatus status) throws CoreException {
		if (!organizeImports)
			return null;

		final boolean hasAmbiguity[]= new boolean[] { false };
		IChooseImportQuery query= new IChooseImportQuery() {
			public TypeNameMatch[] chooseImports(TypeNameMatch[][] openChoices, ISourceRange[] ranges) {
				hasAmbiguity[0]= true;
				return new TypeNameMatch[0];
			}
		};

		final ICompilationUnit unit= (ICompilationUnit)cu.getJavaElement();
		OrganizeImportsOperation op= new OrganizeImportsOperation(unit, cu, settings.importIgnoreLowercase, false, false, query);
		final TextEdit edit= op.createTextEdit(null);
		if (hasAmbiguity[0]) {
			status.addInfo(Messages.format(ActionMessages.OrganizeImportsAction_multi_error_unresolvable, getLocationString(cu)));
		}

		if (op.getParseError() != null) {
			status.addInfo(Messages.format(ActionMessages.OrganizeImportsAction_multi_error_parse, getLocationString(cu)));
			return null;
		}

		if (edit == null || (edit instanceof MultiTextEdit && edit.getChildrenSize() == 0))
			return null;

		return new ImportsFix(edit, unit, FixMessages.ImportsFix_OrganizeImports_Description);
    }

	private static String getLocationString(final CompilationUnit cu) {
		return BasicElementLabels.getPathLabel(cu.getJavaElement().getPath(), false);
	}

	public ImportsFix(TextEdit edit, ICompilationUnit unit, String description) {
		super(edit, unit, description);
	}
}

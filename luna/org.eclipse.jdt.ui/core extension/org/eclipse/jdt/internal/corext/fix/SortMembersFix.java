/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Alex Blewitt - https://bugs.eclipse.org/bugs/show_bug.cgi?id=168954
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.fix;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.text.edits.TextEdit;

import org.eclipse.ltk.core.refactoring.CategorizedTextEditGroup;
import org.eclipse.ltk.core.refactoring.GroupCategory;
import org.eclipse.ltk.core.refactoring.GroupCategorySet;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.util.CompilationUnitSorter;

import org.eclipse.jdt.internal.corext.codemanipulation.SortMembersOperation.DefaultJavaElementComparator;

import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

public class SortMembersFix extends TextEditFix {

	public static ICleanUpFix createCleanUp(CompilationUnit compilationUnit, boolean sortMembers, boolean sortFields) throws CoreException {
		if (!sortMembers && !sortFields)
			return null;

		ICompilationUnit cu= (ICompilationUnit)compilationUnit.getJavaElement();

		String label= FixMessages.SortMembersFix_Change_description;
		CategorizedTextEditGroup group= new CategorizedTextEditGroup(label, new GroupCategorySet(new GroupCategory(label, label, label)));

		TextEdit edit= CompilationUnitSorter.sort(compilationUnit, new DefaultJavaElementComparator(!sortFields), 0, group, null);
		if (edit == null)
			return null;

		return new SortMembersFix(edit, cu, FixMessages.SortMembersFix_Fix_description);
	}

	public SortMembersFix(TextEdit edit, ICompilationUnit compilationUnit, String description) {
		super(edit, compilationUnit, description);
	}
}

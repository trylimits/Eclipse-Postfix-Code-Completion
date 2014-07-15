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
package org.eclipse.jdt.internal.ui.search;

import org.eclipse.search.ui.NewSearchUI;

import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.ui.SharedASTProvider;


public final class FindOccurrencesEngine {

	public static FindOccurrencesEngine create(IOccurrencesFinder finder) {
		return new FindOccurrencesEngine(finder);
	}

	private IOccurrencesFinder fFinder;

	private FindOccurrencesEngine(IOccurrencesFinder finder) {
		if (finder == null)
			throw new IllegalArgumentException();
		fFinder= finder;
	}

	private String run(CompilationUnit astRoot, int offset, int length) {
		String message= fFinder.initialize(astRoot, offset, length);
		if (message != null)
			return message;

		performNewSearch(fFinder, astRoot.getTypeRoot());
		return null;
	}

	public String run(ITypeRoot input, int offset, int length) throws JavaModelException {
		if (input.getSourceRange() == null) {
			return SearchMessages.FindOccurrencesEngine_noSource_text;
		}

		final CompilationUnit root= SharedASTProvider.getAST(input, SharedASTProvider.WAIT_YES, null);
		if (root == null) {
			return SearchMessages.FindOccurrencesEngine_cannotParse_text;
		}
		return run(root, offset, length);
	}

	private void performNewSearch(IOccurrencesFinder finder, ITypeRoot element) {
		NewSearchUI.runQueryInBackground(new OccurrencesSearchQuery(finder, element));
	}
}

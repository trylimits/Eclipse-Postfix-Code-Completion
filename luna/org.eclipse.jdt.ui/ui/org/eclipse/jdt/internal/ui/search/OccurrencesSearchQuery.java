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

package org.eclipse.jdt.internal.ui.search;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;

import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.text.Match;

import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.search.IOccurrencesFinder.OccurrenceLocation;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;


public class OccurrencesSearchQuery implements ISearchQuery {

	private final OccurrencesSearchResult fResult;
	private IOccurrencesFinder fFinder;
	private final ITypeRoot fElement;
	private final String fJobLabel;
	private final String fSingularLabel;
	private final String fPluralLabel;
	private final String fName;
	private final String fFinderId;

	public OccurrencesSearchQuery(IOccurrencesFinder finder, ITypeRoot element) {
		fFinder= finder;
		fElement= element;
		fJobLabel= fFinder.getJobLabel();
		fResult= new OccurrencesSearchResult(this);
		fSingularLabel= fFinder.getUnformattedSingularLabel();
		fPluralLabel= fFinder.getUnformattedPluralLabel();
		fName= fFinder.getElementName();
		fFinderId= fFinder.getID();
	}

	/*
	 * @see org.eclipse.search.ui.ISearchQuery#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public IStatus run(IProgressMonitor monitor) {
		if (fFinder == null) {
			return new StatusInfo(IStatus.ERROR, "Query has already been running"); //$NON-NLS-1$
		}
		if (monitor == null)
			monitor= new NullProgressMonitor();

		try {
			OccurrenceLocation[] occurrences= fFinder.getOccurrences();
			if (occurrences != null) {
				HashMap<Integer, JavaElementLine> lineMap= new HashMap<Integer, JavaElementLine>();
				CompilationUnit astRoot= fFinder.getASTRoot();
				ArrayList<OccurrenceMatch> resultingMatches= new ArrayList<OccurrenceMatch>();

				for (int i= 0; i < occurrences.length; i++) {
					OccurrenceLocation loc= occurrences[i];

					JavaElementLine lineKey= getLineElement(astRoot, loc, lineMap);
					if (lineKey != null) {
						OccurrenceMatch match= new OccurrenceMatch(lineKey, loc.getOffset(), loc.getLength(), loc.getFlags());
						resultingMatches.add(match);

						lineKey.setFlags(lineKey.getFlags() | loc.getFlags());
					}
				}

				if (!resultingMatches.isEmpty()) {
					fResult.addMatches(resultingMatches.toArray(new Match[resultingMatches.size()]));
				}
			}

		} finally {
			//Don't leak AST:
			fFinder= null;
			monitor.done();
		}
		return Status.OK_STATUS;
	}

	private JavaElementLine getLineElement(CompilationUnit astRoot, OccurrenceLocation location, HashMap<Integer, JavaElementLine> lineToGroup) {
		int lineNumber= astRoot.getLineNumber(location.getOffset());
		if (lineNumber <= 0) {
			return null;
		}
		JavaElementLine lineElement= null;
		try {
			Integer key= new Integer(lineNumber);
			lineElement= lineToGroup.get(key);
			if (lineElement == null) {
				int lineStartOffset= astRoot.getPosition(lineNumber, 0);
				if (lineStartOffset >= 0) {
					lineElement= new JavaElementLine(astRoot.getTypeRoot(), lineNumber - 1, lineStartOffset);
					lineToGroup.put(key, lineElement);
				}
			}
		} catch (CoreException e) {
			//nothing
		}
		return lineElement;
	}

	/*
	 * @see org.eclipse.search.ui.ISearchQuery#getLabel()
	 */
	public String getLabel() {
		return fJobLabel;
	}

	public String getResultLabel(int nMatches) {
		if (nMatches == 1) {
			return Messages.format(fSingularLabel, new Object[] { fName, BasicElementLabels.getFileName(fElement) });
		} else {
			return Messages.format(fPluralLabel, new Object[] { fName, new Integer(nMatches), BasicElementLabels.getFileName(fElement) });
		}
	}

	/*
	 * @see org.eclipse.search.ui.ISearchQuery#canRerun()
	 */
	public boolean canRerun() {
		return false; // must release finder to not keep AST reference
	}

	/*
	 * @see org.eclipse.search.ui.ISearchQuery#canRunInBackground()
	 */
	public boolean canRunInBackground() {
		return true;
	}

	/*
	 * @see org.eclipse.search.ui.ISearchQuery#getSearchResult()
	 */
	public ISearchResult getSearchResult() {
		return fResult;
	}

	/**
	 * Returns the finder ID.
	 * @return the finder ID
	 */
	public String getFinderId() {
		return fFinderId;
	}
}

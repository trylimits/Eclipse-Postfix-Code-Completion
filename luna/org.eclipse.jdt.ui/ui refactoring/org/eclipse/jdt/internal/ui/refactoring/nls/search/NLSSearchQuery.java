/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.refactoring.nls.search;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFile;

import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.Match;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.corext.refactoring.nls.NLSRefactoring;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.SearchUtils;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaUIStatus;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;


public class NLSSearchQuery implements ISearchQuery {

	private NLSSearchResult fResult;
	private IJavaElement[] fWrapperClass;
	private IFile[] fPropertiesFile;
	private IJavaSearchScope fScope;
	private String fScopeDescription;

	public NLSSearchQuery(IJavaElement[] wrapperClass, IFile[] propertiesFile, IJavaSearchScope scope, String scopeDescription) {
		fWrapperClass= wrapperClass;
		fPropertiesFile= propertiesFile;
		fScope= scope;
		fScopeDescription= scopeDescription;
	}

	/*
	 * @see org.eclipse.search.ui.ISearchQuery#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public IStatus run(IProgressMonitor monitor) {
		monitor.beginTask("", 5 * fWrapperClass.length); //$NON-NLS-1$

		try {
			final AbstractTextSearchResult textResult= (AbstractTextSearchResult) getSearchResult();
			textResult.removeAll();

			for (int i= 0; i < fWrapperClass.length; i++) {
				IJavaElement wrapperClass= fWrapperClass[i];
				IFile propertieFile= fPropertiesFile[i];
				if (! wrapperClass.exists())
					return JavaUIStatus.createError(0, Messages.format(NLSSearchMessages.NLSSearchQuery_wrapperNotExists, JavaElementLabels.getElementLabel(wrapperClass, JavaElementLabels.ALL_DEFAULT)), null);
				if (! propertieFile.exists())
					return JavaUIStatus.createError(0, Messages.format(NLSSearchMessages.NLSSearchQuery_propertiesNotExists, BasicElementLabels.getResourceName(propertieFile)), null);

				SearchPattern pattern= SearchPattern.createPattern(wrapperClass, IJavaSearchConstants.REFERENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE);
				SearchParticipant[] participants= new SearchParticipant[] {SearchEngine.getDefaultSearchParticipant()};

				NLSSearchResultRequestor requestor= new NLSSearchResultRequestor(propertieFile, fResult);
				try {
					SearchEngine engine= new SearchEngine();
					engine.search(pattern, participants, fScope, requestor, new SubProgressMonitor(monitor, 4));
					requestor.reportUnusedPropertyNames(new SubProgressMonitor(monitor, 1));

					ICompilationUnit compilationUnit= ((IType)wrapperClass).getCompilationUnit();
					CompilationUnitEntry groupElement= new CompilationUnitEntry(NLSSearchMessages.NLSSearchResultCollector_unusedKeys, compilationUnit);

					boolean hasUnusedPropertie= false;
					IField[] fields= ((IType)wrapperClass).getFields();
					for (int j= 0; j < fields.length; j++) {
						IField field= fields[j];
						if (isNLSField(field)) {
							ISourceRange sourceRange= field.getSourceRange();
							if (sourceRange != null) {
								String fieldName= field.getElementName();
								if (!requestor.hasPropertyKey(fieldName)) {
									fResult.addMatch(new Match(compilationUnit, sourceRange.getOffset(), sourceRange.getLength()));
								}
								if (!requestor.isUsedPropertyKey(fieldName)) {
									hasUnusedPropertie= true;
									fResult.addMatch(new Match(groupElement, sourceRange.getOffset(), sourceRange.getLength()));
								}
							}
						}
					}
					if (hasUnusedPropertie)
						fResult.addCompilationUnitGroup(groupElement);

				} catch (CoreException e) {
					return new Status(e.getStatus().getSeverity(), JavaPlugin.getPluginId(), IStatus.OK, NLSSearchMessages.NLSSearchQuery_error, e);
				}
			}
		} finally {
			monitor.done();
		}
		return 	Status.OK_STATUS;
	}

	private boolean isNLSField(IField field) throws JavaModelException {
		int flags= field.getFlags();
		if (!Flags.isPublic(flags))
			return false;

		if (!Flags.isStatic(flags))
			return false;

		String fieldName= field.getElementName();
		if (NLSRefactoring.BUNDLE_NAME_FIELD.equals(fieldName))
			return false;

		if ("RESOURCE_BUNDLE".equals(fieldName)) //$NON-NLS-1$
			return false;

		return true;
	}

	/*
	 * @see org.eclipse.search.ui.ISearchQuery#getLabel()
	 */
	public String getLabel() {
		return NLSSearchMessages.NLSSearchQuery_label;
	}

	public String getResultLabel(int nMatches) {
		if (fWrapperClass.length == 1) {
			if (nMatches == 1) {
				String[] args= new String[] {JavaElementLabels.getElementLabel(fWrapperClass[0], JavaElementLabels.ALL_DEFAULT), fScopeDescription};
				return Messages.format(NLSSearchMessages.SearchOperation_singularLabelPostfix, args);
			}
			String[] args= new String[] {JavaElementLabels.getElementLabel(fWrapperClass[0], JavaElementLabels.ALL_DEFAULT), String.valueOf(nMatches), fScopeDescription};
			return Messages.format(NLSSearchMessages.SearchOperation_pluralLabelPatternPostfix, args);
		} else {
			if (nMatches == 1) {
				return Messages.format(NLSSearchMessages.NLSSearchQuery_oneProblemInScope_description, fScopeDescription);
			}
			return Messages.format(NLSSearchMessages.NLSSearchQuery_xProblemsInScope_description, new Object[] {String.valueOf(nMatches), fScopeDescription});
		}
	}

	/*
	 * @see org.eclipse.search.ui.ISearchQuery#canRerun()
	 */
	public boolean canRerun() {
		return true;
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
		if (fResult == null)
			fResult= new NLSSearchResult(this);
		return fResult;
	}
}

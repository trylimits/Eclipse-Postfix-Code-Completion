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
package org.eclipse.jdt.internal.ui.util;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.SearchUtils;

import org.eclipse.jdt.ui.IJavaElementSearchConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;

public class MainMethodSearchEngine{

	private static class MethodCollector extends SearchRequestor {
			private List<IType> fResult;
			private int fStyle;

			public MethodCollector(List<IType> result, int style) {
				Assert.isNotNull(result);
				fResult= result;
				fStyle= style;
			}

			private boolean considerExternalJars() {
				return (fStyle & IJavaElementSearchConstants.CONSIDER_EXTERNAL_JARS) != 0;
			}

			private boolean considerBinaries() {
				return (fStyle & IJavaElementSearchConstants.CONSIDER_BINARIES) != 0;
			}

			/* (non-Javadoc)
			 * @see org.eclipse.jdt.core.search.SearchRequestor#acceptSearchMatch(org.eclipse.jdt.core.search.SearchMatch)
			 */
			@Override
			public void acceptSearchMatch(SearchMatch match) throws CoreException {
				Object enclosingElement= match.getElement();
				if (enclosingElement instanceof IMethod) { // defensive code
					try {
						IMethod curr= (IMethod) enclosingElement;
						if (curr.isMainMethod()) {
							if (!considerExternalJars()) {
								IPackageFragmentRoot root= JavaModelUtil.getPackageFragmentRoot(curr);
								if (root == null || root.isArchive()) {
									return;
								}
							}
							if (!considerBinaries() && curr.isBinary()) {
								return;
							}
							fResult.add(curr.getDeclaringType());
						}
					} catch (JavaModelException e) {
						JavaPlugin.log(e.getStatus());
					}
				}
			}
	}

	/**
	 * Searches for all main methods in the given scope.
	 * Valid styles are IJavaElementSearchConstants.CONSIDER_BINARIES and
	 * IJavaElementSearchConstants.CONSIDER_EXTERNAL_JARS
	 * @param pm progress monitor
	 * @param scope the search scope
	 * @param style search style constants (see {@link IJavaElementSearchConstants})
	 * @return the types found
	 * @throws CoreException
	 */
	public IType[] searchMainMethods(IProgressMonitor pm, IJavaSearchScope scope, int style) throws CoreException {
		List<IType> typesFound= new ArrayList<IType>(200);

		SearchPattern pattern= SearchPattern.createPattern("main(String[]) void", //$NON-NLS-1$
				IJavaSearchConstants.METHOD, IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE);
		SearchRequestor requestor= new MethodCollector(typesFound, style);
		new SearchEngine().search(pattern, SearchUtils.getDefaultSearchParticipants(), scope, requestor, pm);

		return typesFound.toArray(new IType[typesFound.size()]);
	}



	/**
	 * Searches for all main methods in the given scope.
	 * Valid styles are IJavaElementSearchConstants.CONSIDER_BINARIES and
	 * IJavaElementSearchConstants.CONSIDER_EXTERNAL_JARS
	 * @param context runnable context
	 * @param scope the search scope
	 * @param style style search style constants (see {@link IJavaElementSearchConstants})
	 * @return the types found
	 * @throws InvocationTargetException
	 * @throws InterruptedException
	 */
	public IType[] searchMainMethods(IRunnableContext context, final IJavaSearchScope scope, final int style) throws InvocationTargetException, InterruptedException  {
		int allFlags=  IJavaElementSearchConstants.CONSIDER_EXTERNAL_JARS | IJavaElementSearchConstants.CONSIDER_BINARIES;
		Assert.isTrue((style | allFlags) == allFlags);

		final IType[][] res= new IType[1][];

		IRunnableWithProgress runnable= new IRunnableWithProgress() {
			public void run(IProgressMonitor pm) throws InvocationTargetException {
				try {
					res[0]= searchMainMethods(pm, scope, style);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				}
			}
		};
		context.run(true, true, runnable);

		return res[0];
	}

}

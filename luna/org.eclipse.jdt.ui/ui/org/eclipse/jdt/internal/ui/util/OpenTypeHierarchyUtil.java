/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.util;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.WorkbenchException;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaUIMessages;
import org.eclipse.jdt.internal.ui.typehierarchy.TypeHierarchyViewPart;

public class OpenTypeHierarchyUtil {

	private OpenTypeHierarchyUtil() {
	}

	public static TypeHierarchyViewPart open(IJavaElement element, IWorkbenchWindow window) {
		IJavaElement[] candidates= getCandidates(element);
		if (candidates != null) {
			return open(candidates, window);
		}
		return null;
	}

	public static TypeHierarchyViewPart open(IJavaElement[] candidates, IWorkbenchWindow window) {
		Assert.isTrue(candidates != null && candidates.length != 0);

		try {
			if (PreferenceConstants.OPEN_TYPE_HIERARCHY_IN_PERSPECTIVE.equals(PreferenceConstants.getPreferenceStore().getString(PreferenceConstants.OPEN_TYPE_HIERARCHY))) {
				return openInPerspective(window, candidates);
			} else {
				return openInViewPart(window, candidates);
			}

		} catch (WorkbenchException e) {
			ExceptionHandler.handle(e, window.getShell(),
				JavaUIMessages.OpenTypeHierarchyUtil_error_open_perspective,
				e.getMessage());
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, window.getShell(),
				JavaUIMessages.OpenTypeHierarchyUtil_error_open_editor,
				e.getMessage());
		}
		return null;
	}

	private static TypeHierarchyViewPart openInViewPart(IWorkbenchWindow window, IJavaElement[] input) {
		IWorkbenchPage page= window.getActivePage();
		try {
			TypeHierarchyViewPart result= (TypeHierarchyViewPart) page.findView(JavaUI.ID_TYPE_HIERARCHY);
			if (result != null) {
				result.clearNeededRefresh(); // avoid refresh of old hierarchy on 'becomes visible'
			}
			result= (TypeHierarchyViewPart) page.showView(JavaUI.ID_TYPE_HIERARCHY);
			result.setInputElements(input);
			return result;
		} catch (CoreException e) {
			ExceptionHandler.handle(e, window.getShell(),
				JavaUIMessages.OpenTypeHierarchyUtil_error_open_view, e.getMessage());
		}
		return null;
	}

	private static TypeHierarchyViewPart openInPerspective(IWorkbenchWindow window, IJavaElement[] input) throws WorkbenchException, JavaModelException {
		IWorkbench workbench= JavaPlugin.getDefault().getWorkbench();
		IJavaElement perspectiveInput= input.length == 1 ? input[0] : null;

		if (perspectiveInput != null && input[0] instanceof IMember) {
			if (input[0].getElementType() != IJavaElement.TYPE) {
				perspectiveInput= ((IMember)input[0]).getDeclaringType();
			} else {
				perspectiveInput= input[0];
			}
		}
		IWorkbenchPage page= workbench.showPerspective(JavaUI.ID_HIERARCHYPERSPECTIVE, window, perspectiveInput);

		TypeHierarchyViewPart part= (TypeHierarchyViewPart) page.findView(JavaUI.ID_TYPE_HIERARCHY);
		if (part != null) {
			part.clearNeededRefresh(); // avoid refresh of old hierarchy on 'becomes visible'
		}
		part= (TypeHierarchyViewPart) page.showView(JavaUI.ID_TYPE_HIERARCHY);
		part.setInputElements(input);
		if (perspectiveInput != null) {
			if (page.getEditorReferences().length == 0) {
				JavaUI.openInEditor(input[0], false, false); // only open when the perspective has been created
			}
		}
		return part;
	}


	/**
	 * Converts the input to a possible input candidates
	 * @param input input
	 * @return the possible candidates
	 */
	public static IJavaElement[] getCandidates(Object input) {
		if (!(input instanceof IJavaElement)) {
			return null;
		}
		try {
			IJavaElement elem= (IJavaElement) input;
			switch (elem.getElementType()) {
				case IJavaElement.INITIALIZER:
				case IJavaElement.METHOD:
				case IJavaElement.FIELD:
				case IJavaElement.TYPE:
				case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				case IJavaElement.JAVA_PROJECT:
					return new IJavaElement[] { elem };
				case IJavaElement.PACKAGE_FRAGMENT:
					if (((IPackageFragment)elem).containsJavaResources())
						return new IJavaElement[] {elem};
					break;
				case IJavaElement.PACKAGE_DECLARATION:
					return new IJavaElement[] { elem.getAncestor(IJavaElement.PACKAGE_FRAGMENT) };
				case IJavaElement.IMPORT_DECLARATION:
					IImportDeclaration decl= (IImportDeclaration) elem;
					if (decl.isOnDemand()) {
						elem= JavaModelUtil.findTypeContainer(elem.getJavaProject(), Signature.getQualifier(elem.getElementName()));
					} else {
						elem= elem.getJavaProject().findType(elem.getElementName());
					}
					if (elem == null)
						return null;
					return new IJavaElement[] {elem};

				case IJavaElement.CLASS_FILE:
					return new IJavaElement[] { ((IClassFile)input).getType() };
				case IJavaElement.COMPILATION_UNIT: {
					ICompilationUnit cu= (ICompilationUnit) elem.getAncestor(IJavaElement.COMPILATION_UNIT);
					if (cu != null) {
						IType[] types= cu.getTypes();
						if (types.length > 0) {
							return types;
						}
					}
					break;
				}
				default:
			}
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		}
		return null;
	}
}

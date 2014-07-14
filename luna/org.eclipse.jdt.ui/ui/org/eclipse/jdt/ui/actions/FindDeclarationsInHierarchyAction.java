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
package org.eclipse.jdt.ui.actions;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.ui.search.ElementQuerySpecification;
import org.eclipse.jdt.ui.search.QuerySpecification;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.search.JavaSearchScopeFactory;
import org.eclipse.jdt.internal.ui.search.SearchMessages;

/**
 * Finds declarations of the selected element in its hierarchy.
 * The action is applicable to selections representing a Java element.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class FindDeclarationsInHierarchyAction extends FindDeclarationsAction {

	/**
	 * Creates a new <code>FindDeclarationsInHierarchyAction</code>. The action
	 * requires that the selection provided by the site's selection provider is of type
	 * <code>IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public FindDeclarationsInHierarchyAction(IWorkbenchSite site) {
		super(site);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public FindDeclarationsInHierarchyAction(JavaEditor editor) {
		super(editor);
	}

	@Override
	Class<?>[] getValidTypes() {
		return new Class[] { IField.class, IMethod.class, ILocalVariable.class, ITypeParameter.class };
	}

	@Override
	void init() {
		setText(SearchMessages.Search_FindHierarchyDeclarationsAction_label);
		setToolTipText(SearchMessages.Search_FindHierarchyDeclarationsAction_tooltip);
		setImageDescriptor(JavaPluginImages.DESC_OBJS_SEARCH_DECL);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.FIND_DECLARATIONS_IN_HIERARCHY_ACTION);
	}

	@Override
	QuerySpecification createQuery(IJavaElement element) throws JavaModelException, InterruptedException {
		JavaSearchScopeFactory factory= JavaSearchScopeFactory.getInstance();

		IType type= getType(element);
		if (type == null) {
			return super.createQuery(element);
		}
		IJavaSearchScope scope= SearchEngine.createHierarchyScope(type);
		String description= factory.getHierarchyScopeDescription(type);
		return new ElementQuerySpecification(element, getLimitTo(), scope, description);
	}
}

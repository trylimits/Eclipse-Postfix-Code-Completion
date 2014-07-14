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
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.search.IJavaSearchConstants;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.search.SearchMessages;

/**
 * Finds field write accesses of the selected element in working sets.
 * The action is applicable to selections representing a Java field.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class FindWriteReferencesInWorkingSetAction extends FindReferencesInWorkingSetAction {

	/**
	 * Creates a new <code>FindWriteReferencesInWorkingSetAction</code>. The action
	 * requires that the selection provided by the site's selection provider is of type
	 * <code>org.eclipse.jface.viewers.IStructuredSelection</code>. The user will be
	 * prompted to select the working sets.
	 *
	 * @param site the site providing context information for this action
	 */
	public FindWriteReferencesInWorkingSetAction(IWorkbenchSite site) {
		super(site);
	}

	/**
	 * Creates a new <code>FindWriteReferencesInWorkingSetAction</code>. The action
	 * requires that the selection provided by the site's selection provider is of type
	 * <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site			the site providing context information for this action
	 * @param workingSets	the working sets to be used in the search
	 */
	public FindWriteReferencesInWorkingSetAction(IWorkbenchSite site, IWorkingSet[] workingSets) {
		super(site, workingSets);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public FindWriteReferencesInWorkingSetAction(JavaEditor editor) {
		super(editor);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 * @param workingSets the working sets to be used in the search
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public FindWriteReferencesInWorkingSetAction(JavaEditor editor, IWorkingSet[] workingSets) {
		super(editor, workingSets);
	}

	@Override
	Class<?>[] getValidTypes() {
		return new Class[] { IField.class, ILocalVariable.class };
	}

	@Override
	void init() {
		setText(SearchMessages.Search_FindWriteReferencesInWorkingSetAction_label);
		setToolTipText(SearchMessages.Search_FindWriteReferencesInWorkingSetAction_tooltip);
		setImageDescriptor(JavaPluginImages.DESC_OBJS_SEARCH_REF);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.FIND_WRITE_REFERENCES_IN_WORKING_SET_ACTION);
	}

	@Override
	int getLimitTo() {
		return IJavaSearchConstants.WRITE_ACCESSES;
	}

	@Override
	String getOperationUnavailableMessage() {
		return SearchMessages.JavaElementAction_operationUnavailable_field;
	}
}


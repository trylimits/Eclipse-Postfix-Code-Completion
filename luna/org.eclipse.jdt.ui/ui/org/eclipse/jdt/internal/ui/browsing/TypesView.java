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
package org.eclipse.jdt.internal.ui.browsing;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.IShowInTargetList;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.SelectAllAction;
import org.eclipse.jdt.internal.ui.filters.NonJavaElementFilter;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.DecoratingJavaLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaUILabelProvider;

public class TypesView extends JavaBrowsingPart {

	private SelectAllAction fSelectAllAction;

	/**
	 * Creates and returns the label provider for this part.
	 *
	 * @return the label provider
	 * @see org.eclipse.jface.viewers.ILabelProvider
	 */
	@Override
	protected JavaUILabelProvider createLabelProvider() {
		return new AppearanceAwareLabelProvider(
						AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS | JavaElementLabels.T_CATEGORY,
						AppearanceAwareLabelProvider.DEFAULT_IMAGEFLAGS);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.browsing.JavaBrowsingPart#getAdapter(java.lang.Class)
	 */
	@Override
	public Object getAdapter(Class key) {
		if (key == IShowInTargetList.class) {
			return new IShowInTargetList() {
				public String[] getShowInTargetIds() {
					return new String[] { JavaUI.ID_PACKAGES, JavaPlugin.ID_RES_NAV };
				}

			};
		}
		return super.getAdapter(key);
	}

	/**
	 * Adds filters the viewer of this part.
	 */
	@Override
	protected void addFilters() {
		super.addFilters();
		getViewer().addFilter(new NonJavaElementFilter());
	}

	/**
	 * Answers if the given <code>element</code> is a valid
	 * input for this part.
	 *
	 * @param 	element	the object to test
	 * @return	<true> if the given element is a valid input
	 */
	@Override
	protected boolean isValidInput(Object element) {
		return element instanceof IPackageFragment;
	}

	/**
	 * Answers if the given <code>element</code> is a valid
	 * element for this part.
	 *
	 * @param 	element	the object to test
	 * @return	<true> if the given element is a valid element
	 */
	@Override
	protected boolean isValidElement(Object element) {
		if (element instanceof ICompilationUnit)
			return super.isValidElement(((ICompilationUnit)element).getParent());
		else if (element instanceof IType) {
			IType type= (IType)element;
			return type.getDeclaringType() == null && isValidElement(type.getCompilationUnit());
		}
		return false;
	}

	/**
	 * Finds the element which has to be selected in this part.
	 *
	 * @param je	the Java element which has the focus
	 * @return the element to select
	 */
	@Override
	protected IJavaElement findElementToSelect(IJavaElement je) {
		if (je == null)
			return null;

		switch (je.getElementType()) {
			case IJavaElement.TYPE:
				IType type= ((IType)je).getDeclaringType();
				if (type == null)
					type= (IType)je;
				return type;
			case IJavaElement.COMPILATION_UNIT:
				return getTypeForCU((ICompilationUnit)je);
			case IJavaElement.CLASS_FILE:
				return findElementToSelect(((IClassFile)je).getType());
			case IJavaElement.IMPORT_CONTAINER:
			case IJavaElement.IMPORT_DECLARATION:
			case IJavaElement.PACKAGE_DECLARATION:
				return findElementToSelect(je.getParent());
			default:
				if (je instanceof IMember)
					return findElementToSelect(((IMember)je).getDeclaringType());
				return null;

		}
	}

	/**
	 * Returns the context ID for the Help system
	 *
	 * @return	the string used as ID for the Help context
	 */
	@Override
	protected String getHelpContextId() {
		return IJavaHelpContextIds.TYPES_VIEW;
	}

	@Override
	protected String getLinkToEditorKey() {
		return PreferenceConstants.LINK_BROWSING_TYPES_TO_EDITOR;
	}

	@Override
	protected void createActions() {
		super.createActions();
		fSelectAllAction= new SelectAllAction((TableViewer)getViewer());
	}

	@Override
	protected void fillActionBars(IActionBars actionBars) {
		super.fillActionBars(actionBars);

		// Add selectAll action handlers.
		actionBars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(), fSelectAllAction);
	}

	/**
	 * Handles selection of LogicalPackage in Packages view.
	 *
	 * @see org.eclipse.ui.ISelectionListener#selectionChanged(org.eclipse.ui.IWorkbenchPart, org.eclipse.jface.viewers.ISelection)
	 * @since 2.1
	 */
	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		if (!needsToProcessSelectionChanged(part))
			return;

		if (selection instanceof IStructuredSelection) {
			IStructuredSelection sel= (IStructuredSelection) selection;
			Object selectedElement= sel.getFirstElement();
			if (sel.size() == 1 && (selectedElement instanceof LogicalPackage)) {
				IPackageFragment[] fragments= ((LogicalPackage)selectedElement).getFragments();
				List<IPackageFragment> selectedElements= Arrays.asList(fragments);
				if (selectedElements.size() > 1) {
					adjustInput(selectedElements);
					fPreviousSelectedElement= selectedElements;
					fPreviousSelectionProvider= part;
				} else if (selectedElements.size() == 1)
					super.selectionChanged(part, new StructuredSelection(selectedElements.get(0)));
				else
					Assert.isLegal(false);
				return;
			}
		}
		super.selectionChanged(part, selection);
	}

	private void adjustInput(List<IPackageFragment> selectedElements) {
		Object currentInput= getViewer().getInput();
		if (!selectedElements.equals(currentInput))
			setInput(selectedElements);
	}
	/*
	 * @see org.eclipse.jdt.internal.ui.browsing.JavaBrowsingPart#createDecoratingLabelProvider(org.eclipse.jdt.internal.ui.viewsupport.JavaUILabelProvider)
	 */
	@Override
	protected DecoratingJavaLabelProvider createDecoratingLabelProvider(JavaUILabelProvider provider) {
		DecoratingJavaLabelProvider decoratingLabelProvider= super.createDecoratingLabelProvider(provider);
		provider.addLabelDecorator(new TopLevelTypeProblemsLabelDecorator(null));
		return decoratingLabelProvider;
	}

}

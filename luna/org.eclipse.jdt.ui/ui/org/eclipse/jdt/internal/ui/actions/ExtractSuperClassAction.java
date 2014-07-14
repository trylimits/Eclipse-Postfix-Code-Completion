/*******************************************************************************
 * Copyright (c) 2006, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.actions;

import java.io.CharConversionException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringExecutionStarter;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * Action to extract a supertype from a class.
 * <p>
 * Action is applicable to selections containing elements of type
 * <code>IType</code> (top-level types only), <code>IField</code> and
 * <code>IMethod</code>.
 * </p>
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 3.2
 */
public class ExtractSuperClassAction extends SelectionDispatchAction {

	/**
	 * Action definition ID of the refactor -> extract supertype action (value
	 * <code>"org.eclipse.jdt.ui.edit.text.java.extract.superclass"</code>).
	 *
	 * @since 3.2
	 */
	public static final String EXTRACT_SUPERTYPE= "org.eclipse.jdt.ui.edit.text.java.extract.superclass"; //$NON-NLS-1$

	/**
	 * Refactor menu: name of standard Extract Supertype global action (value
	 * <code>"org.eclipse.jdt.ui.actions.ExtractSuperclass"</code>).
	 *
	 * @since 3.2
	 */
	public static final String EXTRACT_SUPERTYPES= "org.eclipse.jdt.ui.actions.ExtractSuperclass"; //$NON-NLS-1$

	private static IMember[] getSelectedMembers(final IStructuredSelection selection) {
		if (selection.isEmpty())
			return null;
		if (selection.size() == 1) {
			try {
				final IType type= RefactoringAvailabilityTester.getSingleSelectedType(selection);
				if (type != null)
					return new IType[] {type};
			} catch (JavaModelException exception) {
				JavaPlugin.log(exception);
			}
		}
		for (final Iterator<?> iterator= selection.iterator(); iterator.hasNext();) {
			if (!(iterator.next() instanceof IMember))
				return null;
		}
		final Set<IMember> set= new HashSet<IMember>();
		@SuppressWarnings("unchecked")
		List<IMember> selectionList= (List<IMember>) (List<?>) Arrays.asList(selection.toArray());
		set.addAll(selectionList);
		return set.toArray(new IMember[set.size()]);
	}

	/** The java editor */
	private JavaEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call
	 * this constructor.
	 *
	 * @param editor
	 *            the java editor
	 */
	public ExtractSuperClassAction(final JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor));
	}

	/**
	 * Creates a new extract super type action. The action requires that the
	 * selection provided by the site's selection provider is of type
	 * <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site
	 *            the workbench site
	 */
	public ExtractSuperClassAction(final IWorkbenchSite site) {
		super(site);
		setText(RefactoringMessages.ExtractSuperTypeAction_label);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.EXTRACT_SUPERTYPE_ACTION);
	}

	private IMember getSelectedMemberFromEditor() throws JavaModelException {
		final IJavaElement element= SelectionConverter.resolveEnclosingElement(fEditor, (ITextSelection) fEditor.getSelectionProvider().getSelection());
		if (element == null || !(element instanceof IMember))
			return null;
		return (IMember) element;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run(final IStructuredSelection selection) {
		try {
			final IMember[] members= getSelectedMembers(selection);
			if (RefactoringAvailabilityTester.isExtractSupertypeAvailable(members) && ActionUtil.isEditable(getShell(), members[0]))
				RefactoringExecutionStarter.startExtractSupertypeRefactoring(members, getShell());
		} catch (final JavaModelException exception) {
			ExceptionHandler.handle(exception, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run(final ITextSelection selection) {
		try {
			if (! ActionUtil.isEditable(fEditor))
				return;
			final IMember member= getSelectedMemberFromEditor();
			final IMember[] array= new IMember[] { member};
			if (member != null && RefactoringAvailabilityTester.isExtractSupertypeAvailable(array)) {
				RefactoringExecutionStarter.startExtractSupertypeRefactoring(array, getShell());
			} else {
				MessageDialog.openInformation(getShell(), RefactoringMessages.OpenRefactoringWizardAction_unavailable, RefactoringMessages.ExtractSuperTypeAction_unavailable);
			}
		} catch (JavaModelException exception) {
			ExceptionHandler.handle(exception, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void selectionChanged(final IStructuredSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isExtractSupertypeAvailable(selection));
		} catch (JavaModelException exception) {
			// http://bugs.eclipse.org/bugs/show_bug.cgi?id=19253
			if (!(exception.getException() instanceof CharConversionException) && JavaModelUtil.isExceptionToBeLogged(exception))
				JavaPlugin.log(exception);
			setEnabled(false);//no UI - happens on selection changes
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void selectionChanged(final ITextSelection selection) {
		setEnabled(true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void selectionChanged(final JavaTextSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isExtractSupertypeAvailable(selection));
		} catch (JavaModelException event) {
			setEnabled(false);
		}
	}
}

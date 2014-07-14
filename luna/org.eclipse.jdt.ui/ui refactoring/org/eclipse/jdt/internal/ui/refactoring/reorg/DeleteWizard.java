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
package org.eclipse.jdt.internal.ui.refactoring.reorg;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaDeleteProcessor;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgUtils;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.refactoring.MessageWizardPage;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;

public class DeleteWizard extends RefactoringWizard {

	private final JavaDeleteProcessor fProcessor;

	public DeleteWizard(Refactoring refactoring) {
		super(refactoring, DIALOG_BASED_USER_INTERFACE);
		setDefaultPageTitle(RefactoringMessages.DeleteWizard_1);
		fProcessor= (JavaDeleteProcessor) refactoring.getAdapter(JavaDeleteProcessor.class);
		fProcessor.setQueries(new ReorgQueries(this));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.refactoring.RefactoringWizard#addUserInputPages()
	 */
	@Override
	protected void addUserInputPages() {
		addPage(new DeleteInputPage(fProcessor));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.refactoring.RefactoringWizard#getMessageLineWidthInChars()
	 */
	@Override
	public int getMessageLineWidthInChars() {
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#needsProgressMonitor()
	 */
	@Override
	public boolean needsProgressMonitor() {
		return fProcessor.needsProgressMonitor();
	}

	private static class DeleteInputPage extends MessageWizardPage {
		private static final String PAGE_NAME= "DeleteInputPage"; //$NON-NLS-1$
		private static final String DIALOG_SETTINGS_DELETE_SUB_PACKAGES= "deleteSubPackages"; //$NON-NLS-1$
		private Button fDeleteSubPackagesCheckBox;
		private final JavaDeleteProcessor fProcessor;

		public DeleteInputPage(JavaDeleteProcessor processor) {
			super(PAGE_NAME, true, MessageWizardPage.STYLE_QUESTION);
			fProcessor= processor;
		}

		@Override
		protected String getMessageString() {
			int numberOfSelectedElements= numberOfSelectedElements();
			try {
				if (1 == numberOfSelectedElements) {
					String pattern= createConfirmationStringForOneElement();
					String name= getNameOfSingleSelectedElement();
					return Messages.format(pattern, new String[] { name });
				} else {
					String pattern= createConfirmationStringForManyElements();
					return Messages.format(pattern, new String[] { String.valueOf(numberOfSelectedElements())});
				}
			} catch (JavaModelException e) {
				// http://bugs.eclipse.org/bugs/show_bug.cgi?id=19253
				if (JavaModelUtil.isExceptionToBeLogged(e))
					JavaPlugin.log(e);
				setPageComplete(false);
				if (e.isDoesNotExist())
					return 1 == numberOfSelectedElements ? RefactoringMessages.DeleteWizard_12_singular : Messages.format(RefactoringMessages.DeleteWizard_12_plural, String
							.valueOf(numberOfSelectedElements));
				return RefactoringMessages.DeleteWizard_2;
			}
		}

		@Override
		public void createControl(Composite parent) {
			super.createControl(parent);

			if (getDeleteProcessor().hasSubPackagesToDelete())
				addDeleteSubPackagesCheckBox();
		}

		/**
		 * Adds the "delete subpackages" checkbox to the composite. Note that
		 * this code assumes that the control of the parent is a Composite with
		 * GridLayout and a horizontal span of 2.
		 *
		 * @see MessageWizardPage#createControl(Composite)
		 */
		private void addDeleteSubPackagesCheckBox() {

			Composite c= new Composite((Composite) getControl(), SWT.NONE);
			GridLayout gd= new GridLayout();
			gd.horizontalSpacing= 10;
			c.setLayout(gd);

			GridData data= new GridData(GridData.FILL_HORIZONTAL);
			data.horizontalSpan= 2;
			c.setLayoutData(data);

			final boolean selection= getRefactoringSettings().getBoolean(DIALOG_SETTINGS_DELETE_SUB_PACKAGES);

			fDeleteSubPackagesCheckBox= new Button(c, SWT.CHECK);
			fDeleteSubPackagesCheckBox.setText(RefactoringMessages.DeleteWizard_also_delete_sub_packages);
			fDeleteSubPackagesCheckBox.setSelection(selection);

			fDeleteSubPackagesCheckBox.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent event) {
					getDeleteProcessor().setDeleteSubPackages(fDeleteSubPackagesCheckBox.getSelection());
				}
			});
			fDeleteSubPackagesCheckBox.setFocus();

			getDeleteProcessor().setDeleteSubPackages(fDeleteSubPackagesCheckBox.getSelection());
		}

		private String getNameOfSingleSelectedElement() throws JavaModelException {
			if (getSingleSelectedResource() != null)
				return ReorgUtils.getName(getSingleSelectedResource());
			else
				return ReorgUtils.getName(getSingleSelectedJavaElement());
		}

		private IJavaElement getSingleSelectedJavaElement() {
			IJavaElement[] elements= getSelectedJavaElements();
			return elements.length == 1 ? elements[0] : null;
		}

		private IResource getSingleSelectedResource() {
			IResource[] resources= getSelectedResources();
			return resources.length == 1 ? resources[0] : null;
		}

		private int numberOfSelectedElements() {
			return getSelectedJavaElements().length + getSelectedResources().length;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.ui.refactoring.RefactoringWizardPage#performFinish()
		 */
		@Override
		protected boolean performFinish() {
			return super.performFinish() || getDeleteProcessor().wasCanceled(); //close the dialog if canceled
		}

		protected boolean saveSettings() {
			if (getContainer() instanceof Dialog)
				return ((Dialog) getContainer()).getReturnCode() == IDialogConstants.OK_ID;
			return true;
		}

		@Override
		public void dispose() {
			if (fDeleteSubPackagesCheckBox != null && saveSettings())
				getRefactoringSettings().put(DIALOG_SETTINGS_DELETE_SUB_PACKAGES, fDeleteSubPackagesCheckBox.getSelection());
			super.dispose();
		}

		private String createConfirmationStringForOneElement() throws JavaModelException {
			IJavaElement[] elements= getSelectedJavaElements();
			if (elements.length == 1) {
				IJavaElement element= elements[0];
				if (isDefaultPackageWithLinkedFiles(element))
					return RefactoringMessages.DeleteWizard_3;

				if (!isLinkedResource(element))
					return RefactoringMessages.DeleteWizard_4;

				if (isLinkedPackageOrPackageFragmentRoot(element))
					//XXX workaround for jcore bugs 31998 and 31456 - linked packages or source folders cannot be deleted properly
					return RefactoringMessages.DeleteWizard_6;

				return RefactoringMessages.DeleteWizard_5;
			} else {
				if (isLinked(getSelectedResources()[0])) //checked before that this will work
					return RefactoringMessages.DeleteWizard_7;
				else
					return RefactoringMessages.DeleteWizard_8;
			}
		}

		private String createConfirmationStringForManyElements() throws JavaModelException {
			IResource[] resources= getSelectedResources();
			IJavaElement[] javaElements= getSelectedJavaElements();
			if (!containsLinkedResources(resources, javaElements))
				return RefactoringMessages.DeleteWizard_9;

			if (!containsLinkedPackagesOrPackageFragmentRoots(javaElements))
				return RefactoringMessages.DeleteWizard_10;

			//XXX workaround for jcore bugs - linked packages or source folders cannot be deleted properly
			return RefactoringMessages.DeleteWizard_11;
		}

		private static boolean isLinkedPackageOrPackageFragmentRoot(IJavaElement element) {
			if ((element instanceof IPackageFragment) || (element instanceof IPackageFragmentRoot))
				return isLinkedResource(element);
			else
				return false;
		}

		private static boolean containsLinkedPackagesOrPackageFragmentRoots(IJavaElement[] javaElements) {
			for (int i= 0; i < javaElements.length; i++) {
				IJavaElement element= javaElements[i];
				if (isLinkedPackageOrPackageFragmentRoot(element))
					return true;
			}
			return false;
		}

		private static boolean containsLinkedResources(IResource[] resources, IJavaElement[] javaElements) throws JavaModelException {
			for (int i= 0; i < javaElements.length; i++) {
				IJavaElement element= javaElements[i];
				if (isLinkedResource(element))
					return true;
				if (isDefaultPackageWithLinkedFiles(element))
					return true;
			}
			for (int i= 0; i < resources.length; i++) {
				IResource resource= resources[i];
				if (isLinked(resource))
					return true;
			}
			return false;
		}

		private static boolean isDefaultPackageWithLinkedFiles(Object firstElement) throws JavaModelException {
			if (!JavaElementUtil.isDefaultPackage(firstElement))
				return false;
			IPackageFragment defaultPackage= (IPackageFragment)firstElement;
			ICompilationUnit[] cus= defaultPackage.getCompilationUnits();
			for (int i= 0; i < cus.length; i++) {
				if (isLinkedResource(cus[i]))
					return true;
			}
			return false;
		}

		private static boolean isLinkedResource(IJavaElement element) {
			return isLinked(ReorgUtils.getResource(element));
		}

		private static boolean isLinked(IResource resource) {
			return resource != null && resource.isLinked();
		}

		private IJavaElement[] getSelectedJavaElements() {
			return getDeleteProcessor().getJavaElementsToDelete();
		}

		private IResource[] getSelectedResources() {
			return getDeleteProcessor().getResourcesToDelete();
		}

		private JavaDeleteProcessor getDeleteProcessor() {
			return fProcessor;
		}

	}
}

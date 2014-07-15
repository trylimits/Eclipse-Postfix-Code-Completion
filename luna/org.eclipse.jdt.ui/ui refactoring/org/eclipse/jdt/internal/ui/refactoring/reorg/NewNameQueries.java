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

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.Wizard;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenamePackageProcessor;
import org.eclipse.jdt.internal.corext.refactoring.reorg.INewNameQueries;
import org.eclipse.jdt.internal.corext.refactoring.reorg.INewNameQuery;
import org.eclipse.jdt.internal.corext.util.JavaConventionsUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.TextFieldNavigationHandler;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

public class NewNameQueries implements INewNameQueries {

	private static final String INVALID_NAME_NO_MESSAGE= "";//$NON-NLS-1$
	private final Wizard fWizard;
	private final Shell fShell;

	public NewNameQueries() {
		fShell= null;
		fWizard= null;
	}

	public NewNameQueries(Wizard wizard) {
		fWizard= wizard;
		fShell= null;
	}

	public NewNameQueries(Shell shell) {
		fShell = shell;
		fWizard= null;
	}

	private Shell getShell() {
		Assert.isTrue(fWizard == null || fShell == null);
		if (fWizard != null)
			return fWizard.getContainer().getShell();

		if (fShell != null)
			return fShell;
		return JavaPlugin.getActiveWorkbenchShell();
	}

	public INewNameQuery createNewCompilationUnitNameQuery(ICompilationUnit cu, String initialSuggestedName) {
		String[] keys= { BasicElementLabels.getJavaElementName(JavaCore.removeJavaLikeExtension(cu.getElementName()))};
		String message= Messages.format(ReorgMessages.ReorgQueries_enterNewNameQuestion, keys);
		return createStaticQuery(createCompilationUnitNameValidator(cu), message, initialSuggestedName, getShell());
	}


	public INewNameQuery createNewResourceNameQuery(IResource res, String initialSuggestedName) {
		String[] keys= { BasicElementLabels.getResourceName(res)};
		String message= Messages.format(ReorgMessages.ReorgQueries_enterNewNameQuestion, keys);
		return createStaticQuery(createResourceNameValidator(res), message, initialSuggestedName, getShell());
	}


	public INewNameQuery createNewPackageNameQuery(IPackageFragment pack, String initialSuggestedName) {
		String[] keys= {JavaElementLabels.getElementLabel(pack, JavaElementLabels.ALL_DEFAULT)};
		String message= Messages.format(ReorgMessages.ReorgQueries_enterNewNameQuestion, keys);
		return createStaticQuery(createPackageNameValidator(pack), message, initialSuggestedName, getShell());
	}

	public INewNameQuery createNewPackageFragmentRootNameQuery(IPackageFragmentRoot root, String initialSuggestedName) {
		String[] keys= {JavaElementLabels.getElementLabel(root, JavaElementLabels.ALL_DEFAULT)};
		String message= Messages.format(ReorgMessages.ReorgQueries_enterNewNameQuestion, keys);
		return createStaticQuery(createPackageFragmentRootNameValidator(root), message, initialSuggestedName, getShell());
	}


	public INewNameQuery createNullQuery(){
		return createStaticQuery(null);
	}


	public INewNameQuery createStaticQuery(final String newName){
		return new INewNameQuery(){
			public String getNewName() {
				return newName;
			}
		};
	}

	private static INewNameQuery createStaticQuery(final IInputValidator validator, final String message, final String initial, final Shell shell){
		return new INewNameQuery(){
			public String getNewName() throws OperationCanceledException {
				InputDialog dialog= new InputDialog(shell, ReorgMessages.ReorgQueries_nameConflictMessage, message, initial, validator) {
					/* (non-Javadoc)
					 * @see org.eclipse.jface.dialogs.InputDialog#createDialogArea(org.eclipse.swt.widgets.Composite)
					 */
					@Override
					protected Control createDialogArea(Composite parent) {
						Control area= super.createDialogArea(parent);
						TextFieldNavigationHandler.install(getText());
						return area;
					}
				};
				if (dialog.open() == Window.CANCEL)
					throw new OperationCanceledException();
				return dialog.getValue();
			}
		};
	}

	private static IInputValidator createResourceNameValidator(final IResource res){
		IInputValidator validator= new IInputValidator(){
			public String isValid(String newText) {
				if (newText == null || "".equals(newText) || res.getParent() == null) //$NON-NLS-1$
					return INVALID_NAME_NO_MESSAGE;
				if (res.getParent().findMember(newText) != null)
					return ReorgMessages.ReorgQueries_resourceWithThisNameAlreadyExists;
				if (! res.getParent().getFullPath().isValidSegment(newText))
					return ReorgMessages.ReorgQueries_invalidNameMessage;
				IStatus status= res.getParent().getWorkspace().validateName(newText, res.getType());
				if (status.getSeverity() == IStatus.ERROR)
					return status.getMessage();

				if (res.getName().equalsIgnoreCase(newText))
					return ReorgMessages.ReorgQueries_resourceExistsWithDifferentCaseMassage;

				return null;
			}
		};
		return validator;
	}

	private static IInputValidator createCompilationUnitNameValidator(final ICompilationUnit cu) {
		IInputValidator validator= new IInputValidator(){
			public String isValid(String newText) {
				if (newText == null || "".equals(newText)) //$NON-NLS-1$
					return INVALID_NAME_NO_MESSAGE;
				String newCuName= JavaModelUtil.getRenamedCUName(cu, newText);
				IStatus status= JavaConventionsUtil.validateCompilationUnitName(newCuName, cu);
				if (status.getSeverity() == IStatus.ERROR)
					return status.getMessage();
				RefactoringStatus refStatus;
				refStatus= Checks.checkCompilationUnitNewName(cu, newText);
				if (refStatus.hasFatalError())
					return refStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL);

				if (cu.getElementName().equalsIgnoreCase(newCuName))
					return ReorgMessages.ReorgQueries_resourceExistsWithDifferentCaseMassage;

				return null;
			}
		};
		return validator;
	}


	private static IInputValidator createPackageFragmentRootNameValidator(final IPackageFragmentRoot root) {
		return new IInputValidator() {
			IInputValidator resourceNameValidator= createResourceNameValidator(root.getResource());
			public String isValid(String newText) {
				return resourceNameValidator.isValid(newText);
			}
		};
	}

	private static IInputValidator createPackageNameValidator(final IPackageFragment pack) {
		IInputValidator validator= new IInputValidator(){
			public String isValid(String newText) {
				if (newText == null || "".equals(newText)) //$NON-NLS-1$
					return INVALID_NAME_NO_MESSAGE;
				IStatus status= JavaConventionsUtil.validatePackageName(newText, pack);
				if (status.getSeverity() == IStatus.ERROR)
					return status.getMessage();

				IJavaElement parent= pack.getParent();
				try {
					if (parent instanceof IPackageFragmentRoot){
						if (! RenamePackageProcessor.isPackageNameOkInRoot(newText, (IPackageFragmentRoot)parent))
							return ReorgMessages.ReorgQueries_packagewithThatNameexistsMassage;
					}
				} catch (CoreException e) {
					return INVALID_NAME_NO_MESSAGE;
				}
				if (pack.getElementName().equalsIgnoreCase(newText))
					return ReorgMessages.ReorgQueries_resourceExistsWithDifferentCaseMassage;

				return null;
			}
		};
		return validator;
	}
}

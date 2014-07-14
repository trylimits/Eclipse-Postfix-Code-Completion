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
package org.eclipse.jdt.internal.ui.refactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.internal.corext.refactoring.structure.MoveStaticMembersProcessor;
import org.eclipse.jdt.internal.corext.util.JavaConventionsUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.FilteredTypesSelectionDialog;
import org.eclipse.jdt.internal.ui.dialogs.TextFieldNavigationHandler;
import org.eclipse.jdt.internal.ui.refactoring.contentassist.ControlContentAssistHelper;
import org.eclipse.jdt.internal.ui.refactoring.contentassist.JavaTypeCompletionProcessor;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

public class MoveMembersWizard extends RefactoringWizard {

	private final MoveStaticMembersProcessor fProcessor;

	public MoveMembersWizard(MoveStaticMembersProcessor processor, Refactoring ref) {
		super(ref, DIALOG_BASED_USER_INTERFACE);
		fProcessor= processor;
		setDefaultPageTitle(RefactoringMessages.MoveMembersWizard_page_title);
	}

	/* non java-doc
	 * @see RefactoringWizard#addUserInputPages
	 */
	@Override
	protected void addUserInputPages(){
		addPage(new MoveMembersInputPage(fProcessor));
	}

	private static class MoveMembersInputPage extends UserInputWizardPage {

		public static final String PAGE_NAME= "MoveMembersInputPage"; //$NON-NLS-1$
		private static final long LABEL_FLAGS= JavaElementLabels.ALL_DEFAULT;

		private Combo fDestinationField;
		private Button fLeaveDelegateCheckBox;
		private Button fDeprecateDelegateCheckBox;
		private static final int MRU_COUNT= 10;
		private static List<String> fgMruDestinations= new ArrayList<String>(MRU_COUNT);
		private final MoveStaticMembersProcessor fProcessor;

		public MoveMembersInputPage(MoveStaticMembersProcessor processor) {
			super(PAGE_NAME);
			fProcessor= processor;
		}

		@Override
		public void setVisible(boolean visible){
			if (visible){
				IMember[] membersToMove= getMoveProcessor().getMembersToMove();
				int membersToMoveCount= membersToMove.length;
				String label= JavaElementLabels.getElementLabel(getMoveProcessor().getDeclaringType(), JavaElementLabels.ALL_FULLY_QUALIFIED);
				String message= membersToMoveCount == 1 ? Messages.format(RefactoringMessages.MoveMembersInputPage_descriptionKey_singular, new String[] {
						JavaElementLabels.getTextLabel(membersToMove[0], JavaElementLabels.ALL_FULLY_QUALIFIED), label }) : Messages.format(
						RefactoringMessages.MoveMembersInputPage_descriptionKey_plural, new String[] { new Integer(membersToMoveCount).toString(), label });
				setDescription(message);
			}
			super.setVisible(visible);
		}

		public void createControl(Composite parent) {
			Composite composite= new Composite(parent, SWT.NONE);
			GridLayout layout= new GridLayout();
			layout.numColumns= 2;
			composite.setLayout(layout);

			addLabel(composite);
			addDestinationControls(composite);
			fLeaveDelegateCheckBox= DelegateUIHelper.generateLeaveDelegateCheckbox(composite, getRefactoring(), getMoveProcessor().getMembersToMove().length > 1);
			GridData data= new GridData();
			data.horizontalSpan= 2;
			if (fLeaveDelegateCheckBox != null) {
				fLeaveDelegateCheckBox.setLayoutData(data);
				fDeprecateDelegateCheckBox= new Button(composite, SWT.CHECK);
				data= new GridData();
				data.horizontalAlignment= GridData.FILL;
				data.horizontalIndent= (layout.marginWidth + fDeprecateDelegateCheckBox.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);
				data.horizontalSpan= 2;
				fDeprecateDelegateCheckBox.setLayoutData(data);
				fDeprecateDelegateCheckBox.setText(DelegateUIHelper.getDeprecateDelegateCheckBoxTitle());
				fDeprecateDelegateCheckBox.setSelection(DelegateUIHelper.loadDeprecateDelegateSetting(getMoveProcessor()));
				getMoveProcessor().setDeprecateDelegates(fDeprecateDelegateCheckBox.getSelection());
				fDeprecateDelegateCheckBox.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						getMoveProcessor().setDeprecateDelegates(fDeprecateDelegateCheckBox.getSelection());
					}
				});
				fDeprecateDelegateCheckBox.setEnabled(fLeaveDelegateCheckBox.getSelection());
				fLeaveDelegateCheckBox.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						fDeprecateDelegateCheckBox.setEnabled(fLeaveDelegateCheckBox.getSelection());
					}
				});
			}
			setControl(composite);
			Dialog.applyDialogFont(composite);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IJavaHelpContextIds.MOVE_MEMBERS_WIZARD_PAGE);
		}

		@Override
		public void dispose() {
			DelegateUIHelper.saveLeaveDelegateSetting(fLeaveDelegateCheckBox);
			DelegateUIHelper.saveDeprecateDelegateSetting(fDeprecateDelegateCheckBox);
			super.dispose();
		}

		private void addLabel(Composite parent) {
			Label label= new Label(parent, SWT.NONE);
			IMember[] members= getMoveProcessor().getMembersToMove();
			if (members.length == 1) {
				label.setText(Messages.format(
						RefactoringMessages.MoveMembersInputPage_destination_single,
						JavaElementLabels.getElementLabel(members[0], LABEL_FLAGS)));
			} else {
				label.setText(Messages.format(
						RefactoringMessages.MoveMembersInputPage_destination_multi,
						String.valueOf(members.length)));
			}
			GridData gd= new GridData();
			gd.horizontalSpan= 2;
			label.setLayoutData(gd);
		}

		private void addDestinationControls(Composite composite) {
			fDestinationField= new Combo(composite, SWT.SINGLE | SWT.BORDER);
			SWTUtil.setDefaultVisibleItemCount(fDestinationField);
			fDestinationField.setFocus();
			fDestinationField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			fDestinationField.setItems(fgMruDestinations.toArray(new String[fgMruDestinations.size()]));
			fDestinationField.addModifyListener(new ModifyListener(){
				public void modifyText(ModifyEvent e) {
					handleDestinationChanged();
				}
				private void handleDestinationChanged() {
					IType declaring= getMoveProcessor().getDeclaringType();
					IJavaProject javaProject= declaring.getJavaProject();
					IStatus status= JavaConventionsUtil.validateJavaTypeName(fDestinationField.getText(), javaProject);
					if (status.getSeverity() == IStatus.ERROR){
						error(status.getMessage());
					} else {
						try {
							IType resolvedType= javaProject.findType(fDestinationField.getText());
							if (resolvedType == null)
								resolvedType= javaProject.findType(declaring.getPackageFragment().getElementName(), fDestinationField.getText());
							IStatus validationStatus= validateDestinationType(resolvedType, fDestinationField.getText());
							if (validationStatus.isOK()){
								setErrorMessage(null);
								setPageComplete(true);
							} else {
								error(validationStatus.getMessage());
							}
						} catch(JavaModelException ex) {
							JavaPlugin.log(ex); //no ui here
							error(RefactoringMessages.MoveMembersInputPage_invalid_name);
						}
					}
				}
				private void error(String message){
					setErrorMessage(message);
					setPageComplete(false);
				}
			});
			if (fgMruDestinations.size() > 0) {
				fDestinationField.select(0);
			} else {
				setPageComplete(false);
			}
			JavaTypeCompletionProcessor processor= new JavaTypeCompletionProcessor(false, false, true);
			IPackageFragment context= (IPackageFragment) getMoveProcessor().getDeclaringType().getAncestor(IJavaElement.PACKAGE_FRAGMENT);
			processor.setPackageFragment(context);
			ControlContentAssistHelper.createComboContentAssistant(fDestinationField, processor);
			TextFieldNavigationHandler.install(fDestinationField);

			Button button= new Button(composite, SWT.PUSH);
			button.setText(RefactoringMessages.MoveMembersInputPage_browse);
			button.setLayoutData(new GridData());
			SWTUtil.setButtonDimensionHint(button);
			button.addSelectionListener(new SelectionAdapter(){
				@Override
				public void widgetSelected(SelectionEvent e) {
					openTypeSelectionDialog();
				}
			});
		}

		@Override
		protected boolean performFinish() {
			initializeRefactoring();
			return super.performFinish();
		}

		@Override
		public IWizardPage getNextPage() {
			initializeRefactoring();
			return super.getNextPage();
		}

		private void initializeRefactoring() {
			try {
				String destination= fDestinationField.getText();
				if (!fgMruDestinations.remove(destination) && fgMruDestinations.size() >= MRU_COUNT)
					fgMruDestinations.remove(fgMruDestinations.size() - 1);
				fgMruDestinations.add(0, destination);

				getMoveProcessor().setDestinationTypeFullyQualifiedName(destination);
			} catch(JavaModelException e) {
				ExceptionHandler.handle(e, getShell(), RefactoringMessages.MoveMembersInputPage_move_Member, RefactoringMessages.MoveMembersInputPage_exception);
			}
		}

		private IJavaSearchScope createWorkspaceSourceScope(){
			IJavaElement[] project= new IJavaElement[] { getMoveProcessor().getDeclaringType().getJavaProject() };
			return SearchEngine.createJavaSearchScope(project, IJavaSearchScope.REFERENCED_PROJECTS | IJavaSearchScope.SOURCES);
		}

		private void openTypeSelectionDialog(){
			int elementKinds= IJavaSearchConstants.TYPE;
			final IJavaSearchScope scope= createWorkspaceSourceScope();
			FilteredTypesSelectionDialog dialog= new FilteredTypesSelectionDialog(getShell(), false,
				getWizard().getContainer(), scope, elementKinds);
			dialog.setTitle(RefactoringMessages.MoveMembersInputPage_choose_Type);
			dialog.setMessage(RefactoringMessages.MoveMembersInputPage_dialogMessage);
			dialog.setValidator(new ISelectionStatusValidator(){
				public IStatus validate(Object[] selection) {
					Assert.isTrue(selection.length <= 1);
					if (selection.length == 0)
						return new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.OK, RefactoringMessages.MoveMembersInputPage_Invalid_selection, null);
					Object element= selection[0];
					if (! (element instanceof IType))
						return new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.OK, RefactoringMessages.MoveMembersInputPage_Invalid_selection, null);
					IType type= (IType)element;
					return validateDestinationType(type, type.getElementName());
				}
			});
			dialog.setInitialPattern(createInitialFilter());
			if (dialog.open() == Window.CANCEL)
				return;
			IType firstResult= (IType)dialog.getFirstResult();
			fDestinationField.setText(firstResult.getFullyQualifiedName('.'));
		}

		private String createInitialFilter() {
			if (! fDestinationField.getText().trim().equals("")) //$NON-NLS-1$
				return fDestinationField.getText();
			else
				return getMoveProcessor().getDeclaringType().getElementName();
		}

		private static IStatus validateDestinationType(IType type, String typeName){
			if (type == null || ! type.exists())
				return new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.OK, Messages.format(RefactoringMessages.MoveMembersInputPage_not_found, BasicElementLabels.getJavaElementName(typeName)), null);
			if (type.isBinary())
				return new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.OK, RefactoringMessages.MoveMembersInputPage_no_binary, null);
			return Status.OK_STATUS;
		}

		private MoveStaticMembersProcessor getMoveProcessor() {
			return fProcessor;
		}
	}
}

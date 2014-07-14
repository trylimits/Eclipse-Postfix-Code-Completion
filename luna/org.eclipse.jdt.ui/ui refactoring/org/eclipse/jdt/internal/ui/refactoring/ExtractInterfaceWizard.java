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

import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.structure.ExtractInterfaceProcessor;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.DecoratingJavaLabelProvider;

public class ExtractInterfaceWizard extends RefactoringWizard {

	private final ExtractInterfaceProcessor fProcessor;

	public ExtractInterfaceWizard(ExtractInterfaceProcessor processor, Refactoring refactoring) {
		super(refactoring, DIALOG_BASED_USER_INTERFACE);
		fProcessor= processor;
		setDefaultPageTitle(RefactoringMessages.ExtractInterfaceWizard_Extract_Interface);
	}

	/* non java-doc
	 * @see RefactoringWizard#addUserInputPages
	 */
	@Override
	protected void addUserInputPages(){
		addPage(new ExtractInterfaceInputPage(fProcessor));
	}

	private static class ExtractInterfaceInputPage extends TextInputWizardPage {

		private Button fReplaceAllCheckbox;
		private Button fDeclarePublicCheckbox;
		private Button fDeclareAbstractCheckbox;
		private Button fGenerateAnnotationsCheckbox;
		private Button fGenerateCommentsCheckbox;
		private Button fInstanceofCheckbox;
		private CheckboxTableViewer fTableViewer;
		private static final String DESCRIPTION = RefactoringMessages.ExtractInterfaceInputPage_description;
		private static final String SETTING_PUBLIC= 		"Public";//$NON-NLS-1$
		private static final String SETTING_ABSTRACT= 		"Abstract";//$NON-NLS-1$
		private static final String SETTING_ANNOTATIONS= 		"Annotations";//$NON-NLS-1$
		private static final String SETTING_REPLACE= "Replace"; //$NON-NLS-1$
		private static final String SETTING_COMMENTS= "Comments"; //$NON-NLS-1$
		private static final String SETTING_INSTANCEOF= "InstanceOf"; //$NON-NLS-1$
		private Button fSelectAllButton;
		private Button fDeselectAllButton;

		private final ExtractInterfaceProcessor fProcessor;

		public ExtractInterfaceInputPage(ExtractInterfaceProcessor processor) {
			super(DESCRIPTION, true);
			fProcessor= processor;
		}

		public void createControl(Composite parent) {
			initializeDialogUnits(parent);
			Composite result= new Composite(parent, SWT.NONE);
			setControl(result);
			GridLayout layout= new GridLayout();
			layout.numColumns= 2;
			result.setLayout(layout);

			Label label= new Label(result, SWT.NONE);
			label.setText(RefactoringMessages.ExtractInterfaceInputPage_Interface_name);

			Text text= createTextInputField(result);
			text.selectAll();
			text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			addReplaceAllCheckbox(result);
			addInstanceofCheckbox(result, layout.marginWidth);
			fReplaceAllCheckbox.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {
					fInstanceofCheckbox.setEnabled(fReplaceAllCheckbox.getSelection());
				}
			});
			addDeclareAsPublicCheckbox(result);
			addDeclareAsAbstractCheckbox(result);
			addGenerateAnnotationsCheckbox(result);

			Label separator= new Label(result, SWT.NONE);
			GridData gd= new GridData();
			gd.horizontalSpan= 2;
			separator.setLayoutData(gd);

			Label tableLabel= new Label(result, SWT.NONE);
			tableLabel.setText(RefactoringMessages.ExtractInterfaceInputPage_Members);
			tableLabel.setEnabled(anyMembersToExtract());
			gd= new GridData();
			gd.horizontalSpan= 2;
			tableLabel.setLayoutData(gd);

			addMemberListComposite(result);
			addGenerateCommentsCheckbox(result);
			Dialog.applyDialogFont(result);
			initializeCheckboxes();
			updateUIElementEnablement();
			PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.EXTRACT_INTERFACE_WIZARD_PAGE);
		}

		private void addGenerateAnnotationsCheckbox(Composite result) {
			String title= RefactoringMessages.ExtractInterfaceWizard_generate_annotations;
			fGenerateAnnotationsCheckbox= createCheckbox(result,  title, false);
			fProcessor.setAnnotations(fGenerateAnnotationsCheckbox.getSelection());
			fGenerateAnnotationsCheckbox.addSelectionListener(new SelectionAdapter(){
				@Override
				public void widgetSelected(SelectionEvent e) {
					fProcessor.setAnnotations(fGenerateAnnotationsCheckbox.getSelection());
				}
			});		
		}

		private void addGenerateCommentsCheckbox(Composite result) {
			String title= RefactoringMessages.ExtractInterfaceWizard_generate_comments;
			fGenerateCommentsCheckbox= createCheckbox(result,  title, false);
			fProcessor.setComments(fGenerateCommentsCheckbox.getSelection());
			fGenerateCommentsCheckbox.addSelectionListener(new SelectionAdapter(){
				@Override
				public void widgetSelected(SelectionEvent e) {
					fProcessor.setComments(fGenerateCommentsCheckbox.getSelection());
				}
			});
		}

		private void addInstanceofCheckbox(Composite result, int margin) {
			String title= RefactoringMessages.ExtractInterfaceWizard_use_supertype;
			fInstanceofCheckbox= new Button(result, SWT.CHECK);
			fInstanceofCheckbox.setSelection(false);
			GridData gd= new GridData();
			gd.horizontalIndent= (margin + fInstanceofCheckbox.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);
			gd.horizontalSpan= 2;
			fInstanceofCheckbox.setLayoutData(gd);
			fInstanceofCheckbox.setText(title);
			fProcessor.setInstanceOf(fInstanceofCheckbox.getSelection());
			fInstanceofCheckbox.addSelectionListener(new SelectionAdapter(){
				@Override
				public void widgetSelected(SelectionEvent e) {
					fProcessor.setInstanceOf(fInstanceofCheckbox.getSelection());
				}
			});
		}

		private void addMemberListComposite(Composite result) {
			Composite composite= new Composite(result, SWT.NONE);
			GridLayout layout= new GridLayout();
			layout.numColumns= 2;
			layout.marginWidth= 0;
			layout.marginHeight= 0;
			composite.setLayout(layout);
			GridData gd= new GridData(GridData.FILL_BOTH);
			gd.heightHint= convertHeightInCharsToPixels(12);
			gd.horizontalSpan= 2;
			composite.setLayoutData(gd);

			fTableViewer= CheckboxTableViewer.newCheckList(composite, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
			fTableViewer.getTable().setLayoutData(new GridData(GridData.FILL_BOTH));
			fTableViewer.setLabelProvider(createLabelProvider());
			fTableViewer.setContentProvider(new ArrayContentProvider());
			try {
				fTableViewer.setInput(getExtractableMembers());
			} catch (JavaModelException e) {
				ExceptionHandler.handle(e, RefactoringMessages.ExtractInterfaceInputPage_Extract_Interface, RefactoringMessages.ExtractInterfaceInputPage_Internal_Error);
				fTableViewer.setInput(new IMember[0]);
			}
			fTableViewer.addCheckStateListener(new ICheckStateListener(){
				public void checkStateChanged(CheckStateChangedEvent event) {
					ExtractInterfaceInputPage.this.updateUIElementEnablement();
				}
			});
			fTableViewer.setComparator(new JavaElementComparator());
			fTableViewer.getControl().setEnabled(anyMembersToExtract());

			createButtonComposite(composite);
		}

		private IMember[] getExtractableMembers() throws JavaModelException {
			return fProcessor.getExtractableMembers();
		}

		protected void updateUIElementEnablement() {
			final IMember[] checked= getCheckedMembers();
			IMember[] extractable;
			try {
				extractable= getExtractableMembers();
			} catch (JavaModelException exception) {
				extractable= new IMember[0];
				JavaPlugin.log(exception);
			}
			final boolean enabled= containsMethods(checked);
			fDeclarePublicCheckbox.setEnabled(enabled);
			fDeclareAbstractCheckbox.setEnabled(enabled);
			fGenerateAnnotationsCheckbox.setEnabled(enabled);
			fGenerateCommentsCheckbox.setEnabled(enabled);
			fInstanceofCheckbox.setEnabled(fReplaceAllCheckbox.getSelection());
			fSelectAllButton.setEnabled(checked.length < extractable.length);
			fDeselectAllButton.setEnabled(checked.length > 0);
		}

		private static boolean containsMethods(IMember[] members) {
			for (int i= 0; i < members.length; i++) {
				if (members[i].getElementType() == IJavaElement.METHOD)
					return true;
			}
			return false;
		}

		private ILabelProvider createLabelProvider(){
			AppearanceAwareLabelProvider lprovider= new AppearanceAwareLabelProvider(
				AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS |  JavaElementLabels.F_APP_TYPE_SIGNATURE,
				AppearanceAwareLabelProvider.DEFAULT_IMAGEFLAGS
			);

			return new DecoratingJavaLabelProvider(lprovider);
		}

		private void createButtonComposite(Composite composite) {
			GridData gd;
			Composite buttonComposite= new Composite(composite, SWT.NONE);
			GridLayout gl= new GridLayout();
			gl.marginHeight= 0;
			gl.marginWidth= 0;
			buttonComposite.setLayout(gl);
			gd= new GridData(GridData.FILL_VERTICAL);
			buttonComposite.setLayoutData(gd);

			fSelectAllButton= new Button(buttonComposite, SWT.PUSH);
			fSelectAllButton.setText(RefactoringMessages.ExtractInterfaceInputPage_Select_All);
			fSelectAllButton.setEnabled(anyMembersToExtract());
			fSelectAllButton.setLayoutData(new GridData());
			SWTUtil.setButtonDimensionHint(fSelectAllButton);
			fSelectAllButton.addSelectionListener(new SelectionAdapter(){
				@Override
				public void widgetSelected(SelectionEvent e) {
					fTableViewer.setAllChecked(true);
					ExtractInterfaceInputPage.this.updateUIElementEnablement();
				}
			});

			fDeselectAllButton= new Button(buttonComposite, SWT.PUSH);
			fDeselectAllButton.setText(RefactoringMessages.ExtractInterfaceInputPage_Deselect_All);
			fDeselectAllButton.setEnabled(anyMembersToExtract());
			fDeselectAllButton.setLayoutData(new GridData());
			SWTUtil.setButtonDimensionHint(fDeselectAllButton);
			fDeselectAllButton.addSelectionListener(new SelectionAdapter(){
				@Override
				public void widgetSelected(SelectionEvent e) {
					fTableViewer.setAllChecked(false);
					ExtractInterfaceInputPage.this.updateUIElementEnablement();
				}
			});
		}

		private boolean anyMembersToExtract() {
			try {
				return getExtractableMembers().length > 0;
			} catch (JavaModelException e) {
				return false;
			}
		}

		private void addReplaceAllCheckbox(Composite result) {
			String title= RefactoringMessages.ExtractInterfaceInputPage_change_references;
			boolean defaultValue= fProcessor.isReplace();
			fReplaceAllCheckbox= createCheckbox(result,  title, defaultValue);
			fProcessor.setReplace(fReplaceAllCheckbox.getSelection());
			fReplaceAllCheckbox.addSelectionListener(new SelectionAdapter(){
				@Override
				public void widgetSelected(SelectionEvent e) {
					fProcessor.setReplace(fReplaceAllCheckbox.getSelection());
				}
			});
		}

		private void addDeclareAsPublicCheckbox(Composite result) {
			String[] keys= {RefactoringMessages.ExtractInterfaceWizard_public_label};
			String title= Messages.format(RefactoringMessages.ExtractInterfaceWizard_12, keys);
			boolean defaultValue= fProcessor.getPublic();
			fDeclarePublicCheckbox= createCheckbox(result,  title, defaultValue);
			fProcessor.setPublic(fDeclarePublicCheckbox.getSelection());
			fDeclarePublicCheckbox.addSelectionListener(new SelectionAdapter(){
				@Override
				public void widgetSelected(SelectionEvent e) {
					fProcessor.setPublic(fDeclarePublicCheckbox.getSelection());
				}
			});
		}

		private void addDeclareAsAbstractCheckbox(Composite result) {
			final ExtractInterfaceProcessor processor= fProcessor;
			String[] keys= {RefactoringMessages.ExtractInterfaceWizard_abstract_label};
			String title= Messages.format(RefactoringMessages.ExtractInterfaceWizard_12, keys);
			boolean defaultValue= processor.getAbstract();
			fDeclareAbstractCheckbox= createCheckbox(result,  title, defaultValue);
			processor.setAbstract(fDeclareAbstractCheckbox.getSelection());
			fDeclareAbstractCheckbox.addSelectionListener(new SelectionAdapter(){
				@Override
				public void widgetSelected(SelectionEvent e) {
					processor.setAbstract(fDeclareAbstractCheckbox.getSelection());
				}
			});
		}

		private static Button createCheckbox(Composite parent, String title, boolean value){
			Button checkBox= new Button(parent, SWT.CHECK);
			checkBox.setText(title);
			checkBox.setSelection(value);
			GridData layoutData= new GridData();
			layoutData.horizontalSpan= 2;

			checkBox.setLayoutData(layoutData);
			return checkBox;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.refactoring.TextInputWizardPage#validateTextField(String)
		 */
		@Override
		protected RefactoringStatus validateTextField(String text) {
			final ExtractInterfaceProcessor processor= fProcessor;
			processor.setTypeName(text);
			return processor.checkTypeName(text);
		}

		/*
		 * @see org.eclipse.jface.wizard.IWizardPage#getNextPage()
		 */
		@Override
		public IWizardPage getNextPage() {
			try {
				initializeRefactoring();
				storeDialogSettings();
				return super.getNextPage();
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
				return null;
			}
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.refactoring.RefactoringWizardPage#performFinish()
		 */
		@Override
		public boolean performFinish(){
			try {
				initializeRefactoring();
				storeDialogSettings();
				return super.performFinish();
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
				return false;
			}
		}

		private void initializeRefactoring() throws JavaModelException {
			fProcessor.setTypeName(getText());
			fProcessor.setReplace(fReplaceAllCheckbox.getSelection());
			fProcessor.setExtractedMembers(getCheckedMembers());
			fProcessor.setAbstract(fDeclareAbstractCheckbox.getSelection());
			fProcessor.setPublic(fDeclarePublicCheckbox.getSelection());
			fProcessor.setAnnotations(fGenerateAnnotationsCheckbox.getSelection());
			fProcessor.setComments(fGenerateCommentsCheckbox.getSelection());
			fProcessor.setInstanceOf(fInstanceofCheckbox.getSelection());
		}

		private IMember[] getCheckedMembers() {
			List<?> checked= Arrays.asList(fTableViewer.getCheckedElements());
			return checked.toArray(new IMember[checked.size()]);
		}

		@Override
		public void dispose() {
			fInstanceofCheckbox= null;
			fGenerateCommentsCheckbox= null;
			fReplaceAllCheckbox= null;
			fTableViewer= null;
			super.dispose();
		}

		private void initializeCheckboxes() {
			initializeCheckBox(fDeclarePublicCheckbox, SETTING_PUBLIC, true);
			initializeCheckBox(fDeclareAbstractCheckbox, SETTING_ABSTRACT, true);
			initializeCheckBox(fGenerateAnnotationsCheckbox, SETTING_ANNOTATIONS, true);				
			initializeCheckBox(fReplaceAllCheckbox, SETTING_REPLACE, true);
			initializeCheckBox(fGenerateCommentsCheckbox, SETTING_COMMENTS, true);
			initializeCheckBox(fInstanceofCheckbox, SETTING_INSTANCEOF, false);
		}

		private void initializeCheckBox(Button checkbox, String property, boolean def) {
			String s= JavaPlugin.getDefault().getDialogSettings().get(property);
			if (s != null)
				checkbox.setSelection(new Boolean(s).booleanValue());
			else
				checkbox.setSelection(def);
		}

		private void storeDialogSettings() {
			final IDialogSettings settings= JavaPlugin.getDefault().getDialogSettings();
			settings.put(SETTING_PUBLIC, fDeclarePublicCheckbox.getSelection());
			settings.put(SETTING_ABSTRACT, fDeclareAbstractCheckbox.getSelection());
			settings.put(SETTING_ANNOTATIONS, fGenerateAnnotationsCheckbox.getSelection());
			settings.put(SETTING_REPLACE, fReplaceAllCheckbox.getSelection());
			settings.put(SETTING_COMMENTS, fGenerateCommentsCheckbox.getSelection());
			settings.put(SETTING_INSTANCEOF, fInstanceofCheckbox.getSelection());
		}
	}
}

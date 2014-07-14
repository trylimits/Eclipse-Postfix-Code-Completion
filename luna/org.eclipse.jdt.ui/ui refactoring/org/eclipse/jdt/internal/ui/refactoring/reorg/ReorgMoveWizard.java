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
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.reorg.ICreateTargetQuery;
import org.eclipse.jdt.internal.corext.refactoring.reorg.IReorgDestinationValidator;
import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaMoveProcessor;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgDestinationFactory;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.refactoring.QualifiedNameComponent;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;


public class ReorgMoveWizard extends RefactoringWizard {

	private static final String UPDATE_QUALIFIED_NAMES= "moveWizard.updateQualifiedNames"; //$NON-NLS-1$

	private final JavaMoveProcessor fMoveProcessor;

	public ReorgMoveWizard(JavaMoveProcessor moveProcessor, Refactoring ref) {
		super(ref, DIALOG_BASED_USER_INTERFACE);
		fMoveProcessor= moveProcessor;
		if (isTextualMove(fMoveProcessor))
			setDefaultPageTitle(ReorgMessages.ReorgMoveWizard_textual_move);
		else
			setDefaultPageTitle(ReorgMessages.ReorgMoveWizard_3);
	}

	private static boolean isTextualMove(JavaMoveProcessor moveProcessor) {
		return moveProcessor.isTextualMove();
	}

	@Override
	protected void addUserInputPages() {
		addPage(new MoveInputPage(fMoveProcessor));
	}

	private static class MoveInputPage extends ReorgUserInputPage{

		private static final String PAGE_NAME= "MoveInputPage"; //$NON-NLS-1$
		private static final long LABEL_FLAGS= JavaElementLabels.ALL_DEFAULT
				| JavaElementLabels.M_PRE_RETURNTYPE | JavaElementLabels.M_PARAMETER_NAMES | JavaElementLabels.F_PRE_TYPE_SIGNATURE;

		private Button fReferenceCheckbox;
		private Button fQualifiedNameCheckbox;
		private QualifiedNameComponent fQualifiedNameComponent;
		private ICreateTargetQuery fCreateTargetQuery;

		private Object fDestination;
		private final JavaMoveProcessor fMoveProcessor;

		public MoveInputPage(JavaMoveProcessor moveProcessor) {
			super(PAGE_NAME);
			fMoveProcessor= moveProcessor;
		}

		private JavaMoveProcessor getJavaMoveProcessor(){
			return fMoveProcessor;
		}

		@Override
		protected Object getInitiallySelectedElement() {
			return getJavaMoveProcessor().getCommonParentForInputElements();
		}

		@Override
		protected IJavaElement[] getJavaElements() {
			return getJavaMoveProcessor().getJavaElements();
		}

		@Override
		protected IResource[] getResources() {
			return getJavaMoveProcessor().getResources();
		}

		@Override
		protected IReorgDestinationValidator getDestinationValidator() {
			return getJavaMoveProcessor();
		}

		@Override
		protected boolean performFinish() {
			return super.performFinish() || getJavaMoveProcessor().wasCanceled(); //close the dialog if canceled
		}

		@Override
		protected RefactoringStatus verifyDestination(Object selected) throws JavaModelException{
			JavaMoveProcessor processor= getJavaMoveProcessor();
			final RefactoringStatus refactoringStatus= processor.setDestination(ReorgDestinationFactory.createDestination(selected));

			updateUIStatus();
			fDestination= selected;
			return refactoringStatus;
		}

		private void updateUIStatus() {
			getRefactoringWizard().setForcePreviewReview(false);
			JavaMoveProcessor processor= getJavaMoveProcessor();
			if (fReferenceCheckbox != null){
				processor.setUpdateReferences(fReferenceCheckbox.getSelection());
			}
			if (fQualifiedNameCheckbox != null){
				boolean enabled= processor.canEnableQualifiedNameUpdating();
				fQualifiedNameCheckbox.setEnabled(enabled);
				if (enabled) {
					fQualifiedNameComponent.setEnabled(processor.getUpdateQualifiedNames());
					if (processor.getUpdateQualifiedNames())
						getRefactoringWizard().setForcePreviewReview(true);
				} else {
					fQualifiedNameComponent.setEnabled(false);
				}
				processor.setUpdateQualifiedNames(fQualifiedNameCheckbox.getEnabled() && fQualifiedNameCheckbox.getSelection());
			}
		}

		private void addUpdateReferenceComponent(Composite result) {
			final JavaMoveProcessor processor= getJavaMoveProcessor();
			if (! processor.canUpdateJavaReferences())
				return;

			String text;
			int resources= getResources().length;
			int javaElements= getJavaElements().length;
			if (resources == 0 && javaElements == 1) {
				text= Messages.format(
						ReorgMessages.JdtMoveAction_update_references_singular,
						JavaElementLabels.getElementLabel(getJavaElements()[0], LABEL_FLAGS));
			} else if (resources == 1 && javaElements == 0) {
				text= Messages.format(
						ReorgMessages.JdtMoveAction_update_references_singular,
						BasicElementLabels.getResourceName(getResources()[0]));
			} else {
				text= Messages.format(
						ReorgMessages.JdtMoveAction_update_references_plural,
						String.valueOf(resources + javaElements));
			}

			fReferenceCheckbox= new Button(result, SWT.CHECK);
			fReferenceCheckbox.setText(text);
			fReferenceCheckbox.setSelection(processor.getUpdateReferences());

			fReferenceCheckbox.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					processor.setUpdateReferences(((Button)e.widget).getSelection());
					updateUIStatus();
				}
			});
		}

		private void addUpdateQualifiedNameComponent(Composite parent, int marginWidth) {
			final JavaMoveProcessor processor= getJavaMoveProcessor();
			if (!processor.canEnableQualifiedNameUpdating() || !processor.canUpdateQualifiedNames())
				return;
			fQualifiedNameCheckbox= new Button(parent, SWT.CHECK);
			int indent= marginWidth + fQualifiedNameCheckbox.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
			fQualifiedNameCheckbox.setText(RefactoringMessages.RenameInputWizardPage_update_qualified_names);
			fQualifiedNameCheckbox.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			fQualifiedNameCheckbox.setSelection(processor.getUpdateQualifiedNames());

			fQualifiedNameComponent= new QualifiedNameComponent(parent, SWT.NONE, processor, getRefactoringSettings());
			fQualifiedNameComponent.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			GridData gd= (GridData)fQualifiedNameComponent.getLayoutData();
			gd.horizontalAlignment= GridData.FILL;
			gd.horizontalIndent= indent;
			updateQualifiedNameUpdating(processor, processor.getUpdateQualifiedNames());

			fQualifiedNameCheckbox.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					boolean enabled= ((Button)e.widget).getSelection();
					updateQualifiedNameUpdating(processor, enabled);
				}
			});
			fQualifiedNameCheckbox.setSelection(getRefactoringSettings().getBoolean(UPDATE_QUALIFIED_NAMES));
			updateQualifiedNameUpdating(processor, fQualifiedNameCheckbox.getSelection());
		}

		private void updateQualifiedNameUpdating(final JavaMoveProcessor processor, boolean enabled) {
			fQualifiedNameComponent.setEnabled(enabled);
			processor.setUpdateQualifiedNames(enabled);
			updateUIStatus();
		}

		@Override
		public void createControl(Composite parent) {
			Composite result;

			boolean showDestinationTree= ! getJavaMoveProcessor().hasDestinationSet();
			if (showDestinationTree) {
				fCreateTargetQuery= getJavaMoveProcessor().getCreateTargetQuery();
				super.createControl(parent);
				getTreeViewer().getTree().setFocus();
				result= (Composite)super.getControl();
			} else  {
				initializeDialogUnits(parent);
				result= new Composite(parent, SWT.NONE);
				setControl(result);
				result.setLayout(new GridLayout());
				Dialog.applyDialogFont(result);
			}
			addUpdateReferenceComponent(result);
			addUpdateQualifiedNameComponent(result, ((GridLayout)result.getLayout()).marginWidth);
			setControl(result);
			Dialog.applyDialogFont(result);
		}

		@Override
		protected Control addLabel(Composite parent) {
			if (fCreateTargetQuery != null) {
				Composite firstLine= new Composite(parent, SWT.NONE);
				GridLayout layout= new GridLayout(2, false);
				layout.marginHeight= layout.marginWidth= 0;
				firstLine.setLayout(layout);
				firstLine.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

				Control label= super.addLabel(firstLine);
				label.addTraverseListener(new TraverseListener() {
					public void keyTraversed(TraverseEvent e) {
						if (e.detail == SWT.TRAVERSE_MNEMONIC && e.doit) {
							e.detail= SWT.TRAVERSE_NONE;
							getTreeViewer().getTree().setFocus();
						}
					}
				});

				Button newButton= new Button(firstLine, SWT.PUSH);
				newButton.setText(fCreateTargetQuery.getNewButtonLabel());
				GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_END | GridData.GRAB_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING);
				gd.widthHint = SWTUtil.getButtonWidthHint(newButton);
				newButton.setLayoutData(gd);
				newButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						doNewButtonPressed();
					}
				});

				return firstLine;

			} else {
				return super.addLabel(parent);
			}
		}

		private void doNewButtonPressed() {
			Object newElement= fCreateTargetQuery.getCreatedTarget(fDestination);
			if (newElement != null) {
				TreeViewer viewer= getTreeViewer();
				ITreeContentProvider contentProvider= (ITreeContentProvider) viewer.getContentProvider();
				viewer.refresh(contentProvider.getParent(newElement));
				viewer.setSelection(new StructuredSelection(newElement), true);
				viewer.getTree().setFocus();
			}
		}

		/*
		 * @see org.eclipse.jface.dialogs.DialogPage#dispose()
		 */
		@Override
		public void dispose() {
			super.dispose();

			IDialogSettings settings= getRefactoringSettings();
			if (settings == null)
				return;

			if (fQualifiedNameCheckbox != null)
				settings.put(ReorgMoveWizard.UPDATE_QUALIFIED_NAMES, fQualifiedNameCheckbox.getSelection());

			if (fQualifiedNameComponent != null)
				fQualifiedNameComponent.savePatterns(settings);
		}
	}
}

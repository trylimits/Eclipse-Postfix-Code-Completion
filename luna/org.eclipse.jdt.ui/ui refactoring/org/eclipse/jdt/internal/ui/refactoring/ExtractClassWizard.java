/*******************************************************************************
 * Copyright (c) 2007, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Lars Vogel <Lars.Vogel@gmail.com> - Bug 427883
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.window.Window;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.refactoring.descriptors.ExtractClassDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.ExtractClassDescriptor.Field;

import org.eclipse.jdt.internal.corext.refactoring.structure.ExtractClassRefactoring.ExtractClassDescriptorVerification;
import org.eclipse.jdt.internal.corext.util.JavaConventionsUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.util.TableLayoutComposite;

public class ExtractClassWizard extends RefactoringWizard {

	private ExtractClassDescriptor fDescriptor;

	public ExtractClassWizard(ExtractClassDescriptor descriptor, Refactoring refactoring) {
		super(refactoring, DIALOG_BASED_USER_INTERFACE);
		fDescriptor= descriptor;
		setDefaultPageTitle(RefactoringMessages.ExtractClassWizard_page_title);
	}

	@Override
	protected void addUserInputPages() {
		addPage(new ExtractClassUserInputWizardPage());
	}

	private class ExtractClassUserInputWizardPage extends UserInputWizardPage {

		private static final String CREATE_GETTER_SETTER= "CREATE_GETTER_SETTER"; //$NON-NLS-1$
		private static final String CREATE_TOP_LEVEL_SETTING= "CREATE_TOPLEVEL"; //$NON-NLS-1$
		private ControlDecoration fClassNameDecoration;
		private ControlDecoration fParameterNameDecoration;

		public ExtractClassUserInputWizardPage() {
			super("ExtractClass"); //$NON-NLS-1$
			setTitle(RefactoringMessages.ExtractClassWizard_page_title);
		}

		public void createControl(Composite parent) {
			initializeDialogUnits(parent);
			Composite result= new Composite(parent, SWT.NONE);
			result.setLayout(new GridLayout(2, false));
			createClassNameInput(result);
			createLocationInput(result);
			createTable(result);
			createGetterSetterInput(result);
			createParameterNameInput(result);
			setControl(result);
			validateRefactoring();
		}

		private void createLocationInput(Composite parent) {
			Label l= new Label(parent, SWT.NONE);
			l.setText(RefactoringMessages.ExtractClassWizard_label_destination);

			Composite composite= new Composite(parent, SWT.None);
			GridLayout gridLayout= new GridLayout(2, false);
			gridLayout.marginHeight= 0;
			gridLayout.marginWidth= 0;
			composite.setLayout(gridLayout);

			GridData gridData= new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalIndent= FieldDecorationRegistry.getDefault().getMaximumDecorationWidth();
			composite.setLayoutData(gridData);

			final Button topLvlRadio= new Button(composite, SWT.RADIO);
			topLvlRadio.setText(RefactoringMessages.ExtractClassWizard_radio_top_level);
			topLvlRadio.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					boolean fAsTopLevel= topLvlRadio.getSelection();
					fDescriptor.setCreateTopLevel(fAsTopLevel);
					validateRefactoring();
				}
			});

			Button nestedRadio= new Button(composite, SWT.RADIO);
			nestedRadio.setText(Messages.format(RefactoringMessages.ExtractClassWizard_radio_nested, JavaElementLabels.getElementLabel(fDescriptor.getType(), JavaElementLabels.ALL_DEFAULT)));

			boolean createAsTopLevel= getBooleanSetting(CREATE_TOP_LEVEL_SETTING, fDescriptor.isCreateTopLevel());
			fDescriptor.setCreateTopLevel(createAsTopLevel);
			topLvlRadio.setSelection(createAsTopLevel);
			nestedRadio.setSelection(!createAsTopLevel);

		}

		private void createGetterSetterInput(Composite result) {
			final Button button= new Button(result, SWT.CHECK);
			button.setText(RefactoringMessages.ExtractClassWizard_checkbox_create_gettersetter);
			button.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {
					fDescriptor.setCreateGetterSetter(button.getSelection());
					validateRefactoring();
				}

			});
			button.setSelection(getBooleanSetting(CREATE_GETTER_SETTER, fDescriptor.isCreateGetterSetter()));
			fDescriptor.setCreateGetterSetter(button.getSelection());
			GridData gridData= new GridData();
			gridData.horizontalSpan= 2;
			button.setLayoutData(gridData);
		}

		private void createClassNameInput(Composite result) {
			Label label= new Label(result, SWT.LEAD);
			label.setText(RefactoringMessages.ExtractClassWizard_label_class_name);
			final Text text= new Text(result, SWT.SINGLE | SWT.BORDER);
			fClassNameDecoration= new ControlDecoration(text, SWT.TOP | SWT.LEAD);
			text.setText(fDescriptor.getClassName());
			text.selectAll();
			text.setFocus();
			text.addModifyListener(new ModifyListener() {

				public void modifyText(ModifyEvent e) {
					fDescriptor.setClassName(text.getText());
					validateRefactoring();
				}

			});
			GridData gridData= new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalIndent= FieldDecorationRegistry.getDefault().getMaximumDecorationWidth();
			text.setLayoutData(gridData);
		}


		protected void updateDecoration(ControlDecoration decoration, RefactoringStatus status) {
			RefactoringStatusEntry highestSeverity= status.getEntryWithHighestSeverity();
			if (highestSeverity != null) {
				Image newImage= null;
				FieldDecorationRegistry registry= FieldDecorationRegistry.getDefault();
				switch (highestSeverity.getSeverity()) {
					case RefactoringStatus.INFO:
						newImage= registry.getFieldDecoration(FieldDecorationRegistry.DEC_INFORMATION).getImage();
						break;
					case RefactoringStatus.WARNING:
						newImage= registry.getFieldDecoration(FieldDecorationRegistry.DEC_WARNING).getImage();
						break;
					case RefactoringStatus.FATAL:
					case RefactoringStatus.ERROR:
						newImage= registry.getFieldDecoration(FieldDecorationRegistry.DEC_ERROR).getImage();
				}
				decoration.setDescriptionText(highestSeverity.getMessage());
				decoration.setImage(newImage);
				decoration.show();
			} else {
				decoration.setDescriptionText(null);
				decoration.hide();
			}
		}

		private void createParameterNameInput(Composite group) {
			Label l= new Label(group, SWT.NONE);
			l.setText(RefactoringMessages.ExtractClassWizard_field_name);

			final Text text= new Text(group, SWT.BORDER);
			fParameterNameDecoration= new ControlDecoration(text, SWT.TOP | SWT.LEAD);
			text.setText(fDescriptor.getFieldName());
			text.addModifyListener(new ModifyListener() {

				public void modifyText(ModifyEvent e) {
					fDescriptor.setFieldName(text.getText());
					validateRefactoring();
				}

			});
			GridData gridData= new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalIndent= FieldDecorationRegistry.getDefault().getMaximumDecorationWidth();
			text.setLayoutData(gridData);
		}

		private void createTable(Composite parent) {
			Composite result= new Composite(parent, SWT.NONE);
			GridLayout layout= new GridLayout(2, false);
			layout.marginHeight= 0;
			layout.marginWidth= 0;
			result.setLayout(layout);
			GridData gridData= new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan= 2;
			result.setLayoutData(gridData);

			Label l= new Label(result, SWT.NONE);
			l.setText(RefactoringMessages.ExtractClassWizard_label_select_fields);
			gridData= new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan= 2;
			gridData.verticalIndent= 5;
			//gridData.horizontalIndent= FieldDecorationRegistry.getDefault().getMaximumDecorationWidth();
			l.setLayoutData(gridData);
			//fFieldDecoration= new ControlDecoration(l, SWT.TOP | SWT.LEAD);

			TableLayoutComposite layoutComposite= new TableLayoutComposite(result, SWT.NONE);
			layoutComposite.addColumnData(new ColumnWeightData(40, convertWidthInCharsToPixels(20), true));
			layoutComposite.addColumnData(new ColumnWeightData(60, convertWidthInCharsToPixels(20), true));
			final CheckboxTableViewer tv= CheckboxTableViewer.newCheckList(layoutComposite, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
			tv.setContentProvider(ArrayContentProvider.getInstance());
			createColumns(tv);

			Table table= tv.getTable();
			table.setLinesVisible(true);
			table.setHeaderVisible(true);
			gridData= new GridData(GridData.FILL_BOTH);
			table.setLayoutData(gridData);
			Field[] fields= fDescriptor.getFields();
			tv.setInput(fields);
			for (int i= 0; i < fields.length; i++) {
				Field field= fields[i];
				tv.setChecked(field, field.isCreateField());
			}

			gridData= new GridData(GridData.FILL_BOTH);
			gridData.heightHint= SWTUtil.getTableHeightHint(table, Math.max(fields.length,5));
			gridData.widthHint= convertWidthInCharsToPixels(50);
			layoutComposite.setLayoutData(gridData);
			Composite controls= new Composite(result, SWT.NONE);
			gridData= new GridData(GridData.FILL, GridData.FILL, false, false);
			controls.setLayoutData(gridData);
			GridLayout gridLayout= new GridLayout();
			gridLayout.marginHeight= 0;
			gridLayout.marginWidth= 0;
			controls.setLayout(gridLayout);

			final Button editButton= new Button(controls, SWT.NONE);
			editButton.setText(RefactoringMessages.ExtractClassWizard_button_edit);
			editButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			editButton.setEnabled(!tv.getSelection().isEmpty());
			SWTUtil.setButtonDimensionHint(editButton);
			editButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ISelection selection= tv.getSelection();
					if (selection instanceof IStructuredSelection) {
						IStructuredSelection ss= (IStructuredSelection) selection;
						Field selected= (Field) ss.getFirstElement();
						String message= RefactoringMessages.ExtractClassWizard_dialog_message;
						String title= RefactoringMessages.ExtractClassWizard_dialog_title;
						InputDialog inputDialog= new InputDialog(getShell(), title, message, selected.getNewFieldName(), new IInputValidator() {

							public String isValid(String newText) {
								IStatus status= JavaConventionsUtil.validateIdentifier(newText, fDescriptor.getType());
								if (!status.isOK())
									return status.getMessage();
								return null;
							}

						});
						if (inputDialog.open() == Window.OK) {
							selected.setNewFieldName(inputDialog.getValue());
							tv.refresh(selected);
						}

					}
				}
			});
			tv.addCheckStateListener(new ICheckStateListener() {
				public void checkStateChanged(CheckStateChangedEvent event) {
					Field element= (Field) event.getElement();
					element.setCreateField(event.getChecked());
					validateRefactoring();
					tv.refresh(element, true);
				}

			});
			tv.addSelectionChangedListener(new ISelectionChangedListener() {
				public void selectionChanged(SelectionChangedEvent event) {
					IStructuredSelection selection= (IStructuredSelection) tv.getSelection();
					Field field= (Field) selection.getFirstElement();
					if (selection.isEmpty()) {
						editButton.setEnabled(false);
					} else {
						editButton.setEnabled(tv.getChecked(field));
					}
				}
			});
		}

		private abstract class FieldInfoLabelProvider extends CellLabelProvider {
			@Override
			public void update(ViewerCell cell) {
				Field pi= (Field) cell.getElement();
				cell.setText(doGetValue(pi));
			}

			protected abstract String doGetValue(Field pi);
		}

		private abstract class FieldInfoEditingSupport extends EditingSupport {
			private CellEditor fTextEditor;

			private FieldInfoEditingSupport(CellEditor textEditor, ColumnViewer tv) {
				super(tv);
				fTextEditor= textEditor;
			}

			@Override
			protected void setValue(Object element, Object value) {
				if (element instanceof Field) {
					Field pi= (Field) element;
					doSetValue(pi, value.toString());
					getViewer().update(element, null);
				}
				validateRefactoring();
			}

			public abstract void doSetValue(Field pi, String string);

			@Override
			protected Object getValue(Object element) {
				if (element instanceof Field) {
					Field pi= (Field) element;
					return doGetValue(pi);
				}
				return null;
			}

			public abstract String doGetValue(Field pi);

			@Override
			protected CellEditor getCellEditor(Object element) {
				return fTextEditor;
			}

			@Override
			protected boolean canEdit(Object element) {
				if (element instanceof Field) {
					return fTextEditor != null;
				}
				return false;
			}
		}

		private void createColumns(final CheckboxTableViewer tv) {
			TextCellEditor cellEditor= new TextCellEditor(tv.getTable());

			TableViewerColumn viewerColumn= new TableViewerColumn(tv, SWT.LEAD);
			viewerColumn.setLabelProvider(new FieldInfoLabelProvider() {
				@Override
				protected String doGetValue(Field pi) {
					IField field= fDescriptor.getType().getField(pi.getFieldName());
					try {
						return Signature.toString(field.getTypeSignature());
					} catch (JavaModelException e) {
						return RefactoringMessages.ExtractClassWizard_not_available;
					}
				}
			});

			TableColumn column= viewerColumn.getColumn();
			column.setText(RefactoringMessages.ExtractClassWizard_column_type);
			viewerColumn= new TableViewerColumn(tv, SWT.LEAD);
			viewerColumn.setLabelProvider(new FieldInfoLabelProvider() {
				@Override
				protected String doGetValue(Field pi) {
					if (pi.isCreateField())
						return pi.getNewFieldName();
					else
						return pi.getFieldName();
				}
			});
			viewerColumn.setEditingSupport(new FieldInfoEditingSupport(cellEditor, tv) {
				@Override
				public String doGetValue(Field pi) {
					return pi.getNewFieldName();
				}

				@Override
				public void doSetValue(Field pi, String string) {
					pi.setNewFieldName(string);
				}

				@Override
				protected boolean canEdit(Object element) {
					if (element instanceof Field) {
						Field field= (Field) element;
						return field.isCreateField();
					}
					return super.canEdit(element);
				}
			});
			column= viewerColumn.getColumn();
			column.setText(RefactoringMessages.ExtractClassWizard_column_name);
		}

		protected RefactoringStatus validateParameterName() {
			RefactoringStatus status= new RefactoringStatus();
			ExtractClassDescriptorVerification verification= getVerification();
			status.merge(verification.validateParameterName());
			updateDecoration(fParameterNameDecoration, status);
			return status;
		}

		protected RefactoringStatus validateTopLevel() {
			RefactoringStatus status= new RefactoringStatus();
			ExtractClassDescriptorVerification verification= getVerification();
			status.merge(verification.validateTopLevel());
			updateDecoration(fClassNameDecoration, status);
			return status;
		}

		protected RefactoringStatus validateClassName() {
			RefactoringStatus status= new RefactoringStatus();
			ExtractClassDescriptorVerification verification= getVerification();
			status.merge(verification.validateClassName());
			updateDecoration(fClassNameDecoration, status);
			return status;
		}

		protected RefactoringStatus validateFields() {
			RefactoringStatus status= new RefactoringStatus();
			ExtractClassDescriptorVerification verification= getVerification();
			status.merge(verification.validateFields());
			return status;
		}

		protected RefactoringStatus validateRefactoring() {
			RefactoringStatus status= new RefactoringStatus();
			setErrorMessage(null);
			setMessage(null);
			setPageComplete(true);
			status.merge(validateTopLevel());
			status.merge(validateClassName());
			status.merge(validateParameterName());
			status.merge(validateFields());
			RefactoringStatusEntry highestSeverity= status.getEntryWithHighestSeverity();
			if (highestSeverity != null) {
				switch (highestSeverity.getSeverity()) {
					case RefactoringStatus.ERROR:
					case RefactoringStatus.FATAL:
						setErrorMessage(highestSeverity.getMessage());
						setPageComplete(false);
						break;
					case RefactoringStatus.WARNING:
						setMessage(highestSeverity.getMessage(), IMessageProvider.WARNING);
						break;
					case RefactoringStatus.INFO:
						setMessage(highestSeverity.getMessage(), IMessageProvider.INFORMATION);
						break;
				}
			}
			return status;
		}

		protected boolean getBooleanSetting(String key, boolean defaultValue) {
			String update= getRefactoringSettings().get(key);
			if (update != null)
				return Boolean.valueOf(update).booleanValue();
			else
				return defaultValue;
		}

		@Override
		public void dispose() {
			IDialogSettings settings= getRefactoringSettings();
			settings.put(CREATE_GETTER_SETTER, fDescriptor.isCreateGetterSetter());
			settings.put(CREATE_TOP_LEVEL_SETTING, fDescriptor.isCreateTopLevel());
			super.dispose();
		}

	}

	public ExtractClassDescriptorVerification getVerification() {
		ExtractClassDescriptorVerification adapter= (ExtractClassDescriptorVerification) getRefactoring().getAdapter(ExtractClassDescriptorVerification.class);
		return adapter;
	}

}

/*******************************************************************************
 * Copyright (c) 2007, 2012 IBM Corporation and others.
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.layout.TableColumnLayout;
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
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.window.Window;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo;
import org.eclipse.jdt.internal.corext.refactoring.structure.ChangeSignatureProcessor;
import org.eclipse.jdt.internal.corext.refactoring.structure.IntroduceParameterObjectProcessor;
import org.eclipse.jdt.internal.corext.util.JavaConventionsUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

public class IntroduceParameterObjectWizard extends RefactoringWizard {

	private final IntroduceParameterObjectProcessor fProcessor;

	public IntroduceParameterObjectWizard(IntroduceParameterObjectProcessor processor, Refactoring refactoring) {
		super(refactoring, DIALOG_BASED_USER_INTERFACE);
		fProcessor= processor;
		setDefaultPageTitle(RefactoringMessages.IntroduceParameterObjectWizard_wizardpage_title);
	}

	@Override
	protected void addUserInputPages() {
		addPage(new IntroduceParameterObjectInputPage(fProcessor));
	}

	private static class IntroduceParameterObjectInputPage extends UserInputWizardPage {

		private static final String CREATE_TOP_LEVEL_SETTING= "CreateTopLevel"; //$NON-NLS-1$
		private static final String CREATE_SETTERS_SETTING= "CreateSetters"; //$NON-NLS-1$
		private static final String CREATE_GETTERS_SETTING= "CreateGetters"; //$NON-NLS-1$

		private final class ParameterObjectCreatorContentProvider implements IStructuredContentProvider {
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			}

			public void dispose() {
			}

			public Object[] getElements(Object inputElement) {
				if (inputElement instanceof IntroduceParameterObjectProcessor) {
					IntroduceParameterObjectProcessor refactoring= (IntroduceParameterObjectProcessor) inputElement;
					List<ParameterInfo> parameterInfos= refactoring.getParameterInfos();
					List<ParameterInfo> result= new ArrayList<ParameterInfo>(parameterInfos.size());
					for (Iterator<ParameterInfo> iter= parameterInfos.iterator(); iter.hasNext();) {
						ParameterInfo pi= iter.next();
						if (!pi.isAdded())
							result.add(pi);
					}
					return result.toArray();
				}
				return null;
			}
		}

		private abstract class ParameterInfoLabelProvider extends CellLabelProvider {
			@Override
			public void update(ViewerCell cell) {
				ParameterInfo pi= (ParameterInfo) cell.getElement();
				cell.setText(doGetValue(pi));
			}

			protected abstract String doGetValue(ParameterInfo pi);
		}

		private abstract class ParameterInfoEditingSupport extends EditingSupport {
			private CellEditor fTextEditor;

			private ParameterInfoEditingSupport(CellEditor textEditor, ColumnViewer tv) {
				super(tv);
				fTextEditor= textEditor;
			}

			@Override
			protected void setValue(Object element, Object value) {
				if (element instanceof ParameterInfo) {
					ParameterInfo pi= (ParameterInfo) element;
					doSetValue(pi, value.toString());
					getViewer().update(element, null);
				}
				validateRefactoring();
				updateSignaturePreview();
			}

			public abstract void doSetValue(ParameterInfo pi, String string);

			@Override
			protected Object getValue(Object element) {
				if (element instanceof ParameterInfo) {
					ParameterInfo pi= (ParameterInfo) element;
					return doGetValue(pi);
				}
				return null;
			}

			public abstract String doGetValue(ParameterInfo pi);

			@Override
			protected CellEditor getCellEditor(Object element) {
				return fTextEditor;
			}

			@Override
			protected boolean canEdit(Object element) {
				if (element instanceof ParameterInfo) {
					ParameterInfo pi= (ParameterInfo) element;
					return fTextEditor!=null && pi.isCreateField();
				}
				return false;
			}
		}

		private IntroduceParameterObjectProcessor fProcessor;
		private JavaSourceViewer fSignaturePreview;
		private Button fLeaveDelegateCheckBox;
		private Button fDeprecateDelegateCheckBox;

		public IntroduceParameterObjectInputPage(IntroduceParameterObjectProcessor processor) {
			super(RefactoringMessages.IntroduceParameterObjectWizard_wizardpage_name);
			fProcessor= processor;
			setTitle(RefactoringMessages.IntroduceParameterObjectWizard_wizardpage_title);
			setDescription(RefactoringMessages.IntroduceParameterObjectWizard_wizardpage_description);
		}

		public void createControl(Composite parent) {
			initializeDialogUnits(parent);
			Composite result= new Composite(parent, SWT.NONE);
			result.setLayout(new GridLayout(2, false));
			Group group= createGroup(result, RefactoringMessages.IntroduceParameterObjectWizard_type_group);

			createClassNameInput(group);
			createLocationInput(group);

			createTable(group);
			createGetterInput(group);
			createSetterInput(group);

			group= createGroup(result, RefactoringMessages.IntroduceParameterObjectWizard_method_group);
			createParameterNameInput(group);
			createDelegateInput(group);
			createSignaturePreview(group);

			validateRefactoring();

			setControl(result);
		}

		private void createParameterNameInput(Group group) {
			Label l= new Label(group, SWT.NONE);
			l.setText(RefactoringMessages.IntroduceParameterObjectWizard_parameterfield_label);
			final Text text= new Text(group, SWT.BORDER);
			text.setText(fProcessor.getParameterName());
			text.addModifyListener(new ModifyListener() {

				public void modifyText(ModifyEvent e) {
					fProcessor.setParameterName(text.getText());
					updateSignaturePreview();
					validateRefactoring();
				}

			});
			text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		}

		protected void validateRefactoring() {
			List<String> names= new ArrayList<String>();
			boolean oneChecked= false;
			setMessage(null);
			setErrorMessage(null);
			setPageComplete(true);
			IJavaProject project= fProcessor.getMethod().getJavaProject();
			String sourceLevel= project.getOption(JavaCore.COMPILER_SOURCE, true);
			String compliance= project.getOption(JavaCore.COMPILER_COMPLIANCE, true);
			List<ParameterInfo> parameterInfos= fProcessor.getParameterInfos();
			for (Iterator<ParameterInfo> iter= parameterInfos.iterator(); iter.hasNext();) {
				ParameterInfo pi= iter.next();
				if (names.contains(pi.getNewName())) {
					setErrorMessage(Messages.format(RefactoringMessages.IntroduceParameterObjectWizard_parametername_check_notunique, BasicElementLabels.getJavaElementName(pi.getNewName())));
					setPageComplete(false);
					return;
				}
				names.add(pi.getNewName());
				IStatus validateIdentifier= JavaConventions.validateIdentifier(pi.getNewName(), sourceLevel, compliance);
				if (isErrorMessage(validateIdentifier))
					return;
				if (pi.isCreateField())
					oneChecked= true;
			}
			if (!oneChecked) {
				setErrorMessage(RefactoringMessages.IntroduceParameterObjectWizard_parametername_check_atleastoneparameter);
				setPageComplete(false);
				return;
			}
			IStatus validateJavaTypeName= JavaConventions.validateJavaTypeName(fProcessor.getClassName(), sourceLevel, compliance);
			if (isErrorMessage(validateJavaTypeName))
				return;
			if (fProcessor.getClassName().indexOf('.') != -1) {
				setErrorMessage(RefactoringMessages.IntroduceParameterObjectWizard_dot_not_allowed_error);
				setPageComplete(false);
			}
			if (!"".equals(fProcessor.getPackage())) { //$NON-NLS-1$
				IStatus validatePackageName= JavaConventions.validatePackageName(fProcessor.getPackage(), sourceLevel, compliance);
				if (isErrorMessage(validatePackageName))
					return;
			}
			try {
				IType type= project.findType(fProcessor.getNewTypeName());
				if (type != null) {
					String packageLabel= JavaElementLabels.getElementLabel(type.getPackageFragment(), JavaElementLabels.ALL_DEFAULT);
					if (fProcessor.isCreateAsTopLevel()) {
						setErrorMessage(Messages.format(RefactoringMessages.IntroduceParameterObjectWizard_type_already_exists_in_package_info,
								new Object[] { BasicElementLabels.getJavaElementName(fProcessor.getClassName()), packageLabel }));
						setPageComplete(false);
						return;
					} else {
						setErrorMessage(Messages.format(RefactoringMessages.IntroduceParameterObjectWizard_parametername_check_alreadyexists,
								new Object[] { BasicElementLabels.getJavaElementName(fProcessor.getClassName()), BasicElementLabels.getFileName(type.getCompilationUnit()) }));
						setPageComplete(false);
						return;
					}
				}
			} catch (JavaModelException e) {
				// Don't care. The error will popup later anyway..
			}
		}

		private boolean isErrorMessage(IStatus validationStatus) {
			if (!validationStatus.isOK()) {
				if (validationStatus.getSeverity() == IStatus.ERROR) {
					setErrorMessage(validationStatus.getMessage());
					setPageComplete(false);
					return true;
				} else {
					if (validationStatus.getSeverity() == IStatus.INFO)
						setMessage(validationStatus.getMessage(), IMessageProvider.INFORMATION);
					else
						setMessage(validationStatus.getMessage(), IMessageProvider.WARNING);
				}
			}
			return false;
		}

		private void createSignaturePreview(Composite composite) {
			Label previewLabel= new Label(composite, SWT.NONE);
			previewLabel.setText(RefactoringMessages.IntroduceParameterObjectWizard_signaturepreview_label);
			GridData gridData= new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan= 2;
			previewLabel.setLayoutData(gridData);

			fSignaturePreview= InputPageUtil.createSignaturePreview(composite);
			((GridData) fSignaturePreview.getControl().getLayoutData()).horizontalSpan= 2;

			updateSignaturePreview();
		}

		private void createDelegateInput(Group group) {
			fLeaveDelegateCheckBox= DelegateUIHelper.generateLeaveDelegateCheckbox(group, getRefactoring(), false);
			GridData gridData= new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan= 2;
			fLeaveDelegateCheckBox.setLayoutData(gridData);
			if (fLeaveDelegateCheckBox != null) {
				fDeprecateDelegateCheckBox= new Button(group, SWT.CHECK);
				GridData data= new GridData();
				data.horizontalAlignment= GridData.FILL;
				GridLayout layout= (GridLayout) group.getLayout();
				data.horizontalIndent= (layout.marginWidth + fDeprecateDelegateCheckBox.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);
				data.horizontalSpan= 2;
				fDeprecateDelegateCheckBox.setLayoutData(data);
				fDeprecateDelegateCheckBox.setText(DelegateUIHelper.getDeprecateDelegateCheckBoxTitle());
				final ChangeSignatureProcessor refactoring= fProcessor;
				fDeprecateDelegateCheckBox.setSelection(DelegateUIHelper.loadDeprecateDelegateSetting(refactoring));
				refactoring.setDeprecateDelegates(fDeprecateDelegateCheckBox.getSelection());
				fDeprecateDelegateCheckBox.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						refactoring.setDeprecateDelegates(fDeprecateDelegateCheckBox.getSelection());
						validateRefactoring();
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
		}

		private Group createGroup(Composite result, String caption) {
			Group group= new Group(result, SWT.None);
			group.setLayout(new GridLayout(2, false));
			group.setText(caption);
			GridData gridData= new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan= 2;
			group.setLayoutData(gridData);
			return group;
		}

		private void createLocationInput(Composite parent) {
			Label l= new Label(parent, SWT.NONE);
			l.setText(RefactoringMessages.IntroduceParameterObjectWizard_destination_label);

			Composite composite= new Composite(parent, SWT.None);
			GridLayout gridLayout= new GridLayout(2, false);
			gridLayout.marginHeight= 0;
			gridLayout.marginWidth= 0;
			composite.setLayout(gridLayout);
			GridData gridData= new GridData(GridData.FILL_HORIZONTAL);
			composite.setLayoutData(gridData);

			final Button topLvlRadio= new Button(composite, SWT.RADIO);
			topLvlRadio.setText(RefactoringMessages.IntroduceParameterObjectWizard_createastoplevel_radio);
			topLvlRadio.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					boolean fAsTopLevel= topLvlRadio.getSelection();
					fProcessor.setCreateAsTopLevel(fAsTopLevel);
					updateSignaturePreview();
					validateRefactoring();
				}
			});

			Button nestedRadio= new Button(composite, SWT.RADIO);
			nestedRadio.setText(Messages.format(RefactoringMessages.IntroduceParameterObjectWizard_createasnestedclass_radio, BasicElementLabels.getJavaElementName(fProcessor.getContainingClass().getName())));
			boolean createAsTopLevel= getBooleanSetting(CREATE_TOP_LEVEL_SETTING, fProcessor.isCreateAsTopLevel());
			fProcessor.setCreateAsTopLevel(createAsTopLevel);
			topLvlRadio.setSelection(createAsTopLevel);
			nestedRadio.setSelection(!createAsTopLevel);

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
			l.setText(RefactoringMessages.IntroduceParameterObjectWizard_fields_selection_label);
			gridData= new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan= 2;
			gridData.verticalIndent= 5;
			l.setLayoutData(gridData);

			Composite layoutComposite= new Composite(result, SWT.NONE);
			final CheckboxTableViewer tv= CheckboxTableViewer.newCheckList(layoutComposite, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
			tv.setContentProvider(new ParameterObjectCreatorContentProvider());
			createColumns(tv);

			Table table= tv.getTable();
			table.setLinesVisible(true);
			table.setHeaderVisible(true);
			gridData= new GridData(GridData.FILL_BOTH);
			table.setLayoutData(gridData);
			tv.setInput(fProcessor);
			List<ParameterInfo> parameterInfos= fProcessor.getParameterInfos();
			for (Iterator<ParameterInfo> iter= parameterInfos.iterator(); iter.hasNext();) {
				ParameterInfo pi= iter.next();
				tv.setChecked(pi, pi.isCreateField());
			}
			tv.refresh(true);
			gridData= new GridData(GridData.FILL_BOTH);
			gridData.heightHint= SWTUtil.getTableHeightHint(table, parameterInfos.size());
			gridData.widthHint= convertWidthInCharsToPixels(30);
			layoutComposite.setLayoutData(gridData);
			Composite controls= new Composite(result, SWT.NONE);
			gridData= new GridData(GridData.FILL, GridData.FILL, false, false);
			controls.setLayoutData(gridData);
			GridLayout gridLayout= new GridLayout();
			gridLayout.marginHeight= 0;
			gridLayout.marginWidth= 0;
			controls.setLayout(gridLayout);

			final Button upButton= new Button(controls, SWT.NONE);
			upButton.setText(RefactoringMessages.IntroduceParameterObjectWizard_moveentryup_button);
			gridData= new GridData(GridData.FILL_HORIZONTAL);
			upButton.setLayoutData(gridData);
			SWTUtil.setButtonDimensionHint(upButton);
			upButton.setEnabled(false);

			final Button downButton= new Button(controls, SWT.NONE);
			downButton.setText(RefactoringMessages.IntroduceParameterObjectWizard_moventrydown_button);
			gridData= new GridData(GridData.FILL_HORIZONTAL);
			downButton.setLayoutData(gridData);
			SWTUtil.setButtonDimensionHint(downButton);
			downButton.setEnabled(false);

			addSpacer(controls);

			final Button editButton= new Button(controls, SWT.NONE);
			editButton.setText(RefactoringMessages.IntroduceParameterObjectWizard_edit_button);
			editButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			editButton.setEnabled(!tv.getSelection().isEmpty());
			SWTUtil.setButtonDimensionHint(editButton);
			editButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ISelection selection= tv.getSelection();
					if (selection instanceof IStructuredSelection) {
						IStructuredSelection ss= (IStructuredSelection) selection;
						ParameterInfo selected= (ParameterInfo) ss.getFirstElement();
						String message= RefactoringMessages.IntroduceParameterObjectWizard_fieldname_message;
						String title= RefactoringMessages.IntroduceParameterObjectWizard_fieldname_title;
						InputDialog inputDialog= new InputDialog(getShell(), title, message, selected.getNewName(), new IInputValidator() {

							public String isValid(String newText) {
								IStatus status= JavaConventionsUtil.validateIdentifier(newText, fProcessor.getCompilationUnit());
								if (!status.isOK())
									return status.getMessage();
								return null;
							}

						});
						if (inputDialog.open() == Window.OK) {
							selected.setNewName(inputDialog.getValue());
							tv.refresh(selected);
							updateSignaturePreview();
						}

					}
				}
			});

			downButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					int[] indices= tv.getTable().getSelectionIndices();
					Arrays.sort(indices);
					for (int i= indices.length-1; i >=0; i--) {
						int idx= indices[i];
						ParameterInfo pi= (ParameterInfo) tv.getElementAt(idx);
						fProcessor.moveFieldDown(pi);
					}
					tv.refresh();
					updateButtons(tv, upButton, downButton, editButton);
				}

			});
			upButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					int[] indices= tv.getTable().getSelectionIndices();
					Arrays.sort(indices);
					for (int i= 0; i <indices.length; i++) {
						int idx= indices[i];
						ParameterInfo pi= (ParameterInfo) tv.getElementAt(idx);
						fProcessor.moveFieldUp(pi);
					}
					tv.refresh();
					updateButtons(tv, upButton, downButton, editButton);
				}

			});
			tv.addCheckStateListener(new ICheckStateListener() {
				Map<ParameterInfo, String> fLastNames=new HashMap<ParameterInfo, String>();
				public void checkStateChanged(CheckStateChangedEvent event) {
					ParameterInfo element= (ParameterInfo) event.getElement();
					element.setCreateField(event.getChecked());
					if (element.isCreateField()){
						String lastName= fLastNames.get(element);
						if (lastName==null){
							lastName= fProcessor.getFieldName(element);
						}
						element.setNewName(lastName);
					} else {
						fLastNames.put(element, element.getNewName());
						element.setNewName(element.getOldName());
					}
					tv.update(element, null);
					updateButtons(tv, upButton, downButton, editButton);
					validateRefactoring();
				}

			});
			tv.addSelectionChangedListener(new ISelectionChangedListener() {
				public void selectionChanged(SelectionChangedEvent event) {
					updateButtons(tv, upButton, downButton, editButton);
				}
			});
		}

		private void addSpacer(Composite parent) {
			Label label= new Label(parent, SWT.NONE);
			GridData gd= new GridData(GridData.FILL_HORIZONTAL);
			gd.heightHint= 5;
			label.setLayoutData(gd);
		}

		private void createColumns(final CheckboxTableViewer tv) {
			TextCellEditor cellEditor= new TextCellEditor(tv.getTable());

			TableViewerColumn viewerColumn= new TableViewerColumn(tv, SWT.LEAD);
			viewerColumn.setLabelProvider(new ParameterInfoLabelProvider() {
				@Override
				protected String doGetValue(ParameterInfo pi) {
					return pi.getNewTypeName();
				}
			});

			TableColumn columnType= viewerColumn.getColumn();
			columnType.setText(RefactoringMessages.IntroduceParameterObjectWizard_type_column);
			viewerColumn= new TableViewerColumn(tv, SWT.LEAD);
			viewerColumn.setLabelProvider(new ParameterInfoLabelProvider() {
				@Override
				protected String doGetValue(ParameterInfo pi) {
					return pi.getNewName();
				}
			});
			viewerColumn.setEditingSupport(new ParameterInfoEditingSupport(cellEditor, tv) {
				@Override
				public String doGetValue(ParameterInfo pi) {
					return pi.getNewName();
				}
				@Override
				public void doSetValue(ParameterInfo pi, String string) {
					pi.setNewName(string);
				}
			});
			
			TableColumn columnName= viewerColumn.getColumn();
			columnName.setText(RefactoringMessages.IntroduceParameterObjectWizard_name_column);
			
			TableColumnLayout layout= new TableColumnLayout();
			layout.setColumnData(columnType, new ColumnWeightData(50, convertWidthInCharsToPixels(20), true));
			layout.setColumnData(columnName, new ColumnWeightData(50, convertWidthInCharsToPixels(20), true));
			tv.getTable().getParent().setLayout(layout);
		}

		private void createGetterInput(Composite result) {
			Composite buttons= new Composite(result, SWT.NONE);
			GridLayout gridLayout= new GridLayout(2, true);
			gridLayout.marginHeight= 0;
			gridLayout.marginWidth= 0;
			buttons.setLayout(gridLayout);
			GridData gridData= new GridData();
			gridData.horizontalSpan= 2;
			buttons.setLayoutData(gridData);

			final Button button= new Button(buttons, SWT.CHECK);
			button.setText(RefactoringMessages.IntroduceParameterObjectWizard_creategetter_checkbox);
			button.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {
					fProcessor.setCreateGetter(button.getSelection());
					validateRefactoring();
				}

			});
			button.setSelection(getBooleanSetting(CREATE_GETTERS_SETTING, fProcessor.isCreateGetter()));
			fProcessor.setCreateGetter(button.getSelection());
			gridData= new GridData();
			button.setLayoutData(gridData);
		}

		private void createSetterInput(Composite result) {
			final Button button= new Button(result, SWT.CHECK);
			button.setText(RefactoringMessages.IntroduceParameterObjectWizard_createsetter_checkbox);
			button.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {
					fProcessor.setCreateSetter(button.getSelection());
					validateRefactoring();
				}

			});
			button.setSelection(getBooleanSetting(CREATE_SETTERS_SETTING, fProcessor.isCreateSetter()));
			fProcessor.setCreateSetter(button.getSelection());
			GridData gridData= new GridData();
			button.setLayoutData(gridData);
		}

		private void createClassNameInput(Composite result) {
			Label label= new Label(result, SWT.LEAD);
			label.setText(RefactoringMessages.IntroduceParameterObjectWizard_classnamefield_label);
			final Text text= new Text(result, SWT.SINGLE | SWT.BORDER);
			text.setText(fProcessor.getClassName());
			text.selectAll();
			text.setFocus();
			text.addModifyListener(new ModifyListener() {

				public void modifyText(ModifyEvent e) {
					fProcessor.setClassName(text.getText());
					updateSignaturePreview();
					validateRefactoring();
				}

			});
			text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		}

		private void updateButtons(final TableViewer tv, Button upButton, Button downButton, Button editButton) {
			IStructuredSelection selection= (IStructuredSelection) tv.getSelection();
			ParameterInfo firstElement= (ParameterInfo) selection.getFirstElement();
			if (selection.isEmpty()) {
				upButton.setEnabled(false);
				downButton.setEnabled(false);
				editButton.setEnabled(false);
			} else {
				int[] selectionIndex= tv.getTable().getSelectionIndices();
				upButton.setEnabled(selectionIndex[0] != 0);
				downButton.setEnabled(selectionIndex[selectionIndex.length-1] != tv.getTable().getItemCount() - 1);
				editButton.setEnabled(firstElement.isCreateField() && selectionIndex.length==1);
			}
			fProcessor.updateParameterPosition();
			updateSignaturePreview();
		}

		private void updateSignaturePreview() {
			try {
				int top= fSignaturePreview.getTextWidget().getTopPixel();
				fSignaturePreview.getDocument().set(fProcessor.getNewMethodSignature());
				fSignaturePreview.getTextWidget().setTopPixel(top);
			} catch (JavaModelException e) {
				ExceptionHandler.handle(e, RefactoringMessages.IntroduceParameterObjectWizard_error_title, RefactoringMessages.IntroduceParameterObjectWizard_error_description);
			}
		}

		@Override
		public void dispose() {
			DelegateUIHelper.saveDeprecateDelegateSetting(fDeprecateDelegateCheckBox);
			DelegateUIHelper.saveLeaveDelegateSetting(fLeaveDelegateCheckBox);
			IDialogSettings settings= getRefactoringSettings();
			settings.put(IntroduceParameterObjectInputPage.CREATE_GETTERS_SETTING, fProcessor.isCreateGetter());
			settings.put(IntroduceParameterObjectInputPage.CREATE_SETTERS_SETTING, fProcessor.isCreateSetter());
			settings.put(IntroduceParameterObjectInputPage.CREATE_TOP_LEVEL_SETTING, fProcessor.isCreateAsTopLevel());
			super.dispose();
		}

		protected boolean getBooleanSetting(String key, boolean defaultValue) {
			String update= getRefactoringSettings().get(key);
			if (update != null)
				return Boolean.valueOf(update).booleanValue();
			else
				return defaultValue;
		}
	}

}

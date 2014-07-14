/*******************************************************************************
 * Copyright (c) 2005, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.dialogs;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IVariableBinding;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaUIMessages;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.viewsupport.BindingLabelProvider;

/**
 * Dialog for the generate hashCode() and equals() action.
 *
 * @since 3.2
 */
public class GenerateHashCodeEqualsDialog extends SourceActionDialog {

	/**
	 * Content provider for the generate hashCode() and equals() tree viewer.
	 *
	 * @since 3.2
	 *
	 */
	private static class GenerateHashCodeEqualsContentProvider implements ITreeContentProvider {

		IVariableBinding[] fBindings;

		public GenerateHashCodeEqualsContentProvider(IVariableBinding[] allFields) {
			this.fBindings= allFields;
		}

		public void dispose() {
		}

		public Object[] getChildren(Object parentElement) {
			return new Object[0];
		}

		public Object[] getElements(Object inputElement) {
			return fBindings;
		}

		public Object getParent(Object element) {
			return null;
		}

		public boolean hasChildren(Object element) {
			return false;
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}

	}

	/**
	 * Validator for the input of the generate hashCode() and equals() dialog.
	 *
	 * @since 3.2
	 *
	 */
	private static class GenerateHashCodeEqualsValidator implements ISelectionStatusValidator {

		private static int fNumFields;

		public GenerateHashCodeEqualsValidator(int entries) {
			fNumFields= entries;
		}

		public IStatus validate(Object[] selection) {
			int count= 0;
			for (int index= 0; index < selection.length; index++) {
				if (selection[index] instanceof IVariableBinding)
					count++;
			}
			if (count == 0)
				return new StatusInfo(IStatus.ERROR, JavaUIMessages.GenerateHashCodeEqualsDialog_select_at_least_one_field);

			return new StatusInfo(IStatus.INFO, Messages.format(JavaUIMessages.GenerateHashCodeEqualsDialog_selectioninfo_more, new String[] { String.valueOf(count), String.valueOf(fNumFields)}));
		}
	}

	private static final String SETTINGS_INSTANCEOF= "InstanceOf"; //$NON-NLS-1$
	private static final String SETTINGS_BLOCKS= "Blocks"; //$NON-NLS-1$

	private boolean fUseInstanceOf;
	private boolean fUseBlocks;

	public GenerateHashCodeEqualsDialog(Shell shell, CompilationUnitEditor editor, IType type, IVariableBinding[] allFields, IVariableBinding[] selectedFields) throws JavaModelException {
		super(shell, new BindingLabelProvider(), new GenerateHashCodeEqualsContentProvider(allFields), editor, type, false);
		setEmptyListMessage(JavaUIMessages.GenerateHashCodeEqualsDialog_no_entries);

		setInitialSelections(selectedFields);

		setTitle(JavaUIMessages.GenerateHashCodeEqualsDialog_dialog_title);
		setMessage(JavaUIMessages.GenerateHashCodeEqualsDialog_select_fields_to_include);
		setValidator(new GenerateHashCodeEqualsValidator(allFields.length));
		setSize(60, 18);
		setInput(new Object());

		fUseInstanceOf= asBoolean(getDialogSettings().get(SETTINGS_INSTANCEOF), false);
		fUseBlocks= asBoolean(getDialogSettings().get(SETTINGS_BLOCKS), false);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean close() {
		getDialogSettings().put(SETTINGS_INSTANCEOF, fUseInstanceOf);
		getDialogSettings().put(SETTINGS_BLOCKS, fUseBlocks);
		return super.close();
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(shell, IJavaHelpContextIds.GENERATE_HASHCODE_EQUALS_SELECTION_DIALOG);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Composite createCommentSelection(final Composite parent) {
		final Composite composite= super.createCommentSelection(parent);

		Button button= new Button(composite, SWT.CHECK);
		button.setText(JavaUIMessages.GenerateHashCodeEqualsDialog_instanceof_button);

		button.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent event) {
				setUseInstanceOf((((Button) event.widget).getSelection()));
			}
		});
		button.setSelection(isUseInstanceOf());
		GridData data= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		data.horizontalSpan= 2;
		button.setLayoutData(data);

		button= new Button(composite, SWT.CHECK);
		button.setText(JavaUIMessages.GenerateHashCodeEqualsDialog_blocks_button);
		
		button.addSelectionListener(new SelectionAdapter() {
			
			@Override
			public void widgetSelected(SelectionEvent event) {
				setUseBlocks((((Button) event.widget).getSelection()));
			}
		});
		button.setSelection(isUseBlocks());
		data= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		data.horizontalSpan= 2;
		button.setLayoutData(data);
		
		return composite;
	}

	public boolean isUseInstanceOf() {
		return fUseInstanceOf;
	}

	public void setUseInstanceOf(boolean use) {
		fUseInstanceOf= use;
	}

	public boolean isUseBlocks() {
		return fUseBlocks;
	}

	public void setUseBlocks(boolean useBlocks) {
		fUseBlocks= useBlocks;
	}
}
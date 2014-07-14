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
package org.eclipse.jdt.internal.ui.typehierarchy;

import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;

public class HistoryListAction extends Action {

	private class HistoryListDialog extends StatusDialog {

		private ListDialogField<IJavaElement[]> fHistoryList;
		private IStatus fHistoryStatus;
		private IJavaElement[] fResult;

		private HistoryListDialog(Shell shell, IJavaElement[][] elements) {
			super(shell);
			setTitle(TypeHierarchyMessages.HistoryListDialog_title);

			String[] buttonLabels= new String[] {
				TypeHierarchyMessages.HistoryListDialog_remove_button,
			};

			IListAdapter<IJavaElement[]> adapter= new IListAdapter<IJavaElement[]>() {
				public void customButtonPressed(ListDialogField<IJavaElement[]> field, int index) {
					doCustomButtonPressed();
				}
				public void selectionChanged(ListDialogField<IJavaElement[]> field) {
					doSelectionChanged();
				}

				public void doubleClicked(ListDialogField<IJavaElement[]> field) {
					doDoubleClicked();
				}
			};

			JavaElementLabelProvider labelProvider= new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_QUALIFIED | JavaElementLabelProvider.SHOW_ROOT) {
				/*
				 * @see org.eclipse.jdt.ui.JavaElementLabelProvider#getStyledText(java.lang.Object)
				 * @since 3.7
				 */
				@Override
				public StyledString getStyledText(Object element) {
					IJavaElement[] elem= (IJavaElement[])element;
					if (elem.length == 1)
						return HistoryAction.getSingleElementLabel(elem[0]);
					else
						return new StyledString(HistoryAction.getElementLabel(elem));
				}

				/*
				 * @see org.eclipse.jdt.ui.JavaElementLabelProvider#getText(java.lang.Object)
				 * @since 3.7
				 */
				@Override
				public String getText(Object element) {
					IJavaElement[] elem= (IJavaElement[])element;
					return HistoryAction.getElementLabel(elem);
				}

				/*
				 * @see org.eclipse.jdt.ui.JavaElementLabelProvider#getImage(java.lang.Object)
				 * @since 3.7
				 */
				@Override
				public Image getImage(Object element) {
					IJavaElement[] elem= (IJavaElement[])element;
					return super.getImage(elem[0]);
					
				}
			};

			fHistoryList= new ListDialogField<IJavaElement[]>(adapter, buttonLabels, labelProvider);
			fHistoryList.setLabelText(TypeHierarchyMessages.HistoryListDialog_label);
			fHistoryList.setElements(Arrays.asList(elements));

			ISelection sel;
			if (elements.length > 0) {
				sel= new StructuredSelection(elements[0]);
			} else {
				sel= new StructuredSelection();
			}

			fHistoryList.selectElements(sel);
		}

		/*
		 * @see org.eclipse.jface.dialogs.Dialog#isResizable()
		 * @since 3.4
		 */
		@Override
		protected boolean isResizable() {
			return true;
		}

		/*
		 * @see Dialog#createDialogArea(Composite)
		 */
		@Override
		protected Control createDialogArea(Composite parent) {
			initializeDialogUnits(parent);

			Composite composite= (Composite) super.createDialogArea(parent);

			Composite inner= new Composite(composite, SWT.NONE);
			inner.setFont(parent.getFont());

			inner.setLayoutData(new GridData(GridData.FILL_BOTH));

			LayoutUtil.doDefaultLayout(inner, new DialogField[] { fHistoryList }, true, 0, 0);
			LayoutUtil.setHeightHint(fHistoryList.getListControl(null), convertHeightInCharsToPixels(12));
			LayoutUtil.setHorizontalGrabbing(fHistoryList.getListControl(null));

			applyDialogFont(composite);
			return composite;
		}

		/**
		 * Method doCustomButtonPressed.
		 */
		private void doCustomButtonPressed() {
			fHistoryList.removeElements(fHistoryList.getSelectedElements());
		}

		private void doDoubleClicked() {
			if (fHistoryStatus.isOK()) {
				okPressed();
			}
		}


		private void doSelectionChanged() {
			StatusInfo status= new StatusInfo();
			List<IJavaElement[]> selected= fHistoryList.getSelectedElements();
			if (selected.size() != 1) {
				status.setError(""); //$NON-NLS-1$
				fResult= null;
			} else {
				fResult= selected.get(0);
			}
			fHistoryList.enableButton(0, fHistoryList.getSize() > selected.size() && selected.size() != 0);
			fHistoryStatus= status;
			updateStatus(status);
		}

		public IJavaElement[] getResult() {
			return fResult;
		}

		/**
		 * Gets the remaining elements in the list.
		 * 
		 * @return the remaining elements in the list
		 * @since 3.7
		 */
		public List<IJavaElement[]> getRemaining() {
			List<IJavaElement[]> elems= fHistoryList.getElements();
			return elems;
		}

		/*
		 * @see org.eclipse.jface.window.Window#configureShell(Shell)
		 */
		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell, IJavaHelpContextIds.HISTORY_LIST_DIALOG);
		}

	}

	private TypeHierarchyViewPart fView;

	public HistoryListAction(TypeHierarchyViewPart view) {
		fView= view;
		setText(TypeHierarchyMessages.HistoryListAction_label);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.HISTORY_LIST_ACTION);
	}

	/*
	 * @see IAction#run()
	 */
	@Override
	public void run() {
		List<IJavaElement[]> historyEntries= fView.getHistoryEntries();
		IJavaElement[][] entries= historyEntries.toArray(new IJavaElement[historyEntries.size()][]);
		HistoryListDialog dialog= new HistoryListDialog(JavaPlugin.getActiveWorkbenchShell(), entries);
		if (dialog.open() == Window.OK) {
			fView.setHistoryEntries(dialog.getRemaining());
			fView.setInputElements(dialog.getResult());
		}
	}

}


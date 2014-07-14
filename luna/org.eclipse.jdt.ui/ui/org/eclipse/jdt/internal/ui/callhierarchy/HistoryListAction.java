/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 *          (report 36180: Callers/Callees view)
 *   Stephan Herrmann (stephan@cs.tu-berlin.de):
 *          - bug 75800: [call hierarchy] should allow searches for fields
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

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

import org.eclipse.jdt.core.IMember;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.viewsupport.ColoringLabelProvider;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;

public class HistoryListAction extends Action {

	private class HistoryListDialog extends StatusDialog {

		private ListDialogField<IMember[]> fHistoryList;
		private IStatus fHistoryStatus;
		private IMember[] fResult;

		private HistoryListDialog(Shell shell, IMember[][] elements) {
			super(shell);
			setTitle(CallHierarchyMessages.HistoryListDialog_title);

			String[] buttonLabels= new String[] {
				CallHierarchyMessages.HistoryListDialog_remove_button,
			};

			IListAdapter<IMember[]> adapter= new IListAdapter<IMember[]>() {
				public void customButtonPressed(ListDialogField<IMember[]> field, int index) {
					doCustomButtonPressed();
				}
				public void selectionChanged(ListDialogField<IMember[]> field) {
					doSelectionChanged();
				}

				public void doubleClicked(ListDialogField<IMember[]> field) {
					doDoubleClicked();
				}
			};

			JavaElementLabelProvider labelProvider= new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_QUALIFIED | JavaElementLabelProvider.SHOW_ROOT) {
				/*
				 * @see org.eclipse.jdt.ui.JavaElementLabelProvider#getStyledText(java.lang.Object)
				 * @since 3.5
				 */
				@Override
				public StyledString getStyledText(Object element) {
					IMember[] members= (IMember[]) element;
					return new StyledString(HistoryAction.getElementLabel(members));
				}
				/*
				 * @see org.eclipse.jdt.ui.JavaElementLabelProvider#getText(java.lang.Object)
				 */
				@Override
				public String getText(Object element) {
					IMember[] members= (IMember[]) element;
					return HistoryAction.getElementLabel(members);
				}
				/*
				 * @see org.eclipse.jdt.ui.JavaElementLabelProvider#getImage(java.lang.Object)
				 */
				@Override
				public Image getImage(Object element) {
					IMember[] members= (IMember[]) element;
					return super.getImage(members[0]);
				}
			};

			fHistoryList= new ListDialogField<IMember[]>(adapter, buttonLabels, new ColoringLabelProvider(labelProvider));
			fHistoryList.setLabelText(CallHierarchyMessages.HistoryListDialog_label);
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
		 * @see Dialog#createDialogArea(Composite)
		 */
		@Override
		protected Control createDialogArea(Composite parent) {
			initializeDialogUnits(parent);

			Composite composite= (Composite) super.createDialogArea(parent);

			Composite inner= new Composite(composite, SWT.NONE);
			inner.setLayoutData(new GridData(GridData.FILL_BOTH));
			inner.setFont(composite.getFont());

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
        	List<IMember[]> selected= fHistoryList.getSelectedElements();
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

		public IMember[] getResult() {
			return fResult;
		}

		public IMember[][] getRemaining() {
			List<IMember[]> elems= fHistoryList.getElements();
			return elems.toArray(new IMember[elems.size()][]);
		}

		/*
		 * @see org.eclipse.jface.window.Window#configureShell(Shell)
		 */
		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell, IJavaHelpContextIds.HISTORY_LIST_DIALOG);
		}

		/*
		 * @see org.eclipse.jface.dialogs.Dialog#isResizable()
		 * @since 3.4
		 */
		@Override
		protected boolean isResizable() {
			return true;
		}

	}

	private CallHierarchyViewPart fView;

	public HistoryListAction(CallHierarchyViewPart view) {
		fView= view;
		setText(CallHierarchyMessages.HistoryListAction_label);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.HISTORY_LIST_ACTION);
	}

	/*
	 * @see IAction#run()
	 */
	@Override
	public void run() {
		IMember[][] historyEntries= fView.getHistoryEntries();
		HistoryListDialog dialog= new HistoryListDialog(JavaPlugin.getActiveWorkbenchShell(), historyEntries);
		if (dialog.open() == Window.OK) {
			fView.setHistoryEntries(dialog.getRemaining());
			fView.setInputElements(dialog.getResult());
		}
	}

}


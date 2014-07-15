/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      IBM Corporation - initial API and implementation
 * 		Sebastian Davids <sdavids@gmx.de> - Fix for bug 19346 - Dialog font
 *   	should be activated and used by other components.
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.workingsets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;

import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.IWorkingSetEditWizard;
import org.eclipse.ui.dialogs.IWorkingSetNewWizard;
import org.eclipse.ui.dialogs.SelectionDialog;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

public class WorkingSetConfigurationDialog extends SelectionDialog {

	private static class WorkingSetLabelProvider extends LabelProvider {
		private Map<ImageDescriptor, Image> fIcons;
		public WorkingSetLabelProvider() {
			fIcons= new Hashtable<ImageDescriptor, Image>();
		}
		@Override
		public void dispose() {
			Iterator<Image> iterator= fIcons.values().iterator();
			while (iterator.hasNext()) {
				Image icon= iterator.next();
				icon.dispose();
			}
			super.dispose();
		}
		@Override
		public Image getImage(Object object) {
			Assert.isTrue(object instanceof IWorkingSet);
			IWorkingSet workingSet= (IWorkingSet)object;
			ImageDescriptor imageDescriptor= workingSet.getImageDescriptor();
			if (imageDescriptor == null)
				return null;
			Image icon= fIcons.get(imageDescriptor);
			if (icon == null) {
				icon= imageDescriptor.createImage();
				fIcons.put(imageDescriptor, icon);
			}
			return icon;
		}
		@Override
		public String getText(Object object) {
			Assert.isTrue(object instanceof IWorkingSet);
			IWorkingSet workingSet= (IWorkingSet)object;
			return BasicElementLabels.getWorkingSetLabel(workingSet);
		}
	}

	private List<IWorkingSet> fAllWorkingSets;
	private CheckboxTableViewer fTableViewer;

	private Button fNewButton;
	private Button fEditButton;
	private Button fRemoveButton;
	private Button fUpButton;
	private Button fDownButton;
	private Button fSelectAll;
	private Button fDeselectAll;

	/**
	 * Sort working sets button.
	 * 
	 * @since 3.5
	 */
	private Button fSortWorkingSet;

	private IWorkingSet[] fResult;
	private List<IWorkingSet> fAddedWorkingSets;
	private List<IWorkingSet> fRemovedWorkingSets;
	private Map<IWorkingSet, IWorkingSet> fEditedWorkingSets;
	private List<IWorkingSet> fRemovedMRUWorkingSets;

	private int nextButtonId= IDialogConstants.CLIENT_ID + 1;

	/**
	 * Value of sorted state of working sets.
	 * 
	 * @since 3.5
	 */
	private boolean fIsSortingEnabled;

	/**
	 * The working set comparator.
	 * 
	 * @since 3.5
	 */
	private WorkingSetComparator fComparator;

	public WorkingSetConfigurationDialog(Shell parentShell, IWorkingSet[] allWorkingSets, boolean isSortingEnabled) {
		super(parentShell);
		setTitle(WorkingSetMessages.WorkingSetConfigurationDialog_title);
		setMessage(WorkingSetMessages.WorkingSetConfigurationDialog_message);
		fAllWorkingSets= new ArrayList<IWorkingSet>(allWorkingSets.length);
		for (int i= 0; i < allWorkingSets.length; i++) {
			fAllWorkingSets.add(allWorkingSets[i]);
		}
		fIsSortingEnabled= isSortingEnabled;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(shell, IJavaHelpContextIds.WORKING_SET_CONFIGURATION_DIALOG);
	}

	/**
	 * Returns the selected working sets
	 *
	 * @return the selected working sets
	 */
	public IWorkingSet[] getSelection() {
		return fResult;
	}

	/**
	 * Sets the initial selection
	 *
	 * @param workingSets the initial selection
	 */
	public void setSelection(IWorkingSet[] workingSets) {
		fResult= workingSets;
		setInitialSelections(workingSets);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Control createContents(Composite parent) {
		Control control= super.createContents(parent);
		setInitialSelection();
		updateButtonAvailability();
		return control;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite= (Composite)super.createDialogArea(parent);

		createMessageArea(composite);
		Composite inner= new Composite(composite, SWT.NONE);
		inner.setLayoutData(new GridData(GridData.FILL_BOTH));
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		inner.setLayout(layout);
		createTableViewer(inner);
		createOrderButtons(inner);
		createModifyButtons(composite);
		if (fIsSortingEnabled) {
			fTableViewer.setComparator(new ViewerComparator(getComparator()) {
				/*
				 * @see ViewerComparator#compare(Viewer, Object, Object)
				 * @since 3.5
				 */
				@Override
				public int compare(Viewer viewer, Object e1, Object e2) {
					return getComparator().compare(e1, e2);
				}
			});
		}
		fTableViewer.setInput(fAllWorkingSets);
		applyDialogFont(composite);

		return composite;
	}

	private void createTableViewer(Composite parent) {
		fTableViewer= CheckboxTableViewer.newCheckList(parent, SWT.BORDER | SWT.MULTI);
		fTableViewer.addCheckStateListener(new ICheckStateListener() {
			public void checkStateChanged(CheckStateChangedEvent event) {
				updateButtonAvailability();
			}
		});
		GridData data= new GridData(GridData.FILL_BOTH);
		data.heightHint= convertHeightInCharsToPixels(20);
		data.widthHint= convertWidthInCharsToPixels(50);
		fTableViewer.getTable().setLayoutData(data);

		fTableViewer.setLabelProvider(new WorkingSetLabelProvider());
		fTableViewer.setContentProvider(new ArrayContentProvider());
		fTableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				handleSelectionChanged();
			}
		});
		fTableViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				if (fEditButton.isEnabled())
					editSelectedWorkingSet();
			}
		});
	}

	private void createModifyButtons(Composite composite) {
		Composite buttonComposite= new Composite(composite, SWT.RIGHT);
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		buttonComposite.setLayout(layout);
		GridData data= new GridData(GridData.HORIZONTAL_ALIGN_END | GridData.GRAB_HORIZONTAL);
		data.grabExcessHorizontalSpace= true;
		composite.setData(data);

		fNewButton= createButton(buttonComposite, nextButtonId++,
			WorkingSetMessages.WorkingSetConfigurationDialog_new_label, false);
		fNewButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				createWorkingSet();
			}
		});

		fEditButton= createButton(buttonComposite, nextButtonId++,
			WorkingSetMessages.WorkingSetConfigurationDialog_edit_label, false);
		fEditButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				editSelectedWorkingSet();
			}
		});

		fRemoveButton= createButton(buttonComposite, nextButtonId++,
			WorkingSetMessages.WorkingSetConfigurationDialog_remove_label, false);
		fRemoveButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				removeSelectedWorkingSets();
			}
		});
	}

	private void createOrderButtons(Composite parent) {
		Composite buttons= new Composite(parent, SWT.NONE);
		buttons.setLayoutData(new GridData(GridData.FILL_VERTICAL));
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		buttons.setLayout(layout);

		fUpButton= new Button(buttons, SWT.PUSH);
		fUpButton.setText(WorkingSetMessages.WorkingSetConfigurationDialog_up_label);
		setButtonLayoutData(fUpButton);
		fUpButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				moveUp(((IStructuredSelection)fTableViewer.getSelection()).toList());
			}
		});

		fDownButton= new Button(buttons, SWT.PUSH);
		fDownButton.setText(WorkingSetMessages.WorkingSetConfigurationDialog_down_label);
		setButtonLayoutData(fDownButton);
		fDownButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				moveDown(((IStructuredSelection)fTableViewer.getSelection()).toList());
			}
		});

		fSelectAll= new Button(buttons, SWT.PUSH);
		fSelectAll.setText(WorkingSetMessages.WorkingSetConfigurationDialog_selectAll_label);
		setButtonLayoutData(fSelectAll);
		fSelectAll.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectAll();
			}
		});

		fDeselectAll= new Button(buttons, SWT.PUSH);
		fDeselectAll.setText(WorkingSetMessages.WorkingSetConfigurationDialog_deselectAll_label);
		setButtonLayoutData(fDeselectAll);
		fDeselectAll.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				deselectAll();
			}
		});
		/**
		 * A check box that has persistence to sort the working sets alphabetically in the
		 * WorkingSetConfigurationDialog. It restores the unsorted order of the working sets when
		 * unchecked.
		 * 
		 * @since 3.5
		 */
		fSortWorkingSet= new Button(parent, SWT.CHECK);
		fSortWorkingSet.setText(WorkingSetMessages.WorkingSetConfigurationDialog_sort_working_sets);
		fSortWorkingSet.setLayoutData(new GridData(SWT.LEAD, SWT.CENTER, true, false));
		fSortWorkingSet.setSelection(fIsSortingEnabled);
		fSortWorkingSet.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				fIsSortingEnabled= fSortWorkingSet.getSelection();
				updateButtonAvailability();
				if (fIsSortingEnabled) {
					fTableViewer.setComparator(new ViewerComparator(getComparator()) {
						/*
						 * @see ViewerComparator#compare(Viewer, Object, Object)
						 * @since 3.5
						 */
						@Override
						public int compare(Viewer viewer, Object e1, Object e2) {
							return getComparator().compare(e1, e2);
						}
					});
				} else {
					fTableViewer.setComparator(null);
				}
			}
		});
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void okPressed() {
		List<IWorkingSet> newResult= getResultWorkingSets();
		fResult= newResult.toArray(new IWorkingSet[newResult.size()]);
		if (fIsSortingEnabled) {
			Collections.sort(fAllWorkingSets, getComparator());
		}
		setResult(newResult);
		super.okPressed();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private List<IWorkingSet> getResultWorkingSets() {
		Object[] checked= fTableViewer.getCheckedElements();
		return new ArrayList(Arrays.asList(checked));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void cancelPressed() {
		restoreAddedWorkingSets();
		restoreChangedWorkingSets();
		restoreRemovedWorkingSets();
		super.cancelPressed();
	}

	private void setInitialSelection() {
		List<Object[]> selections= getInitialElementSelections();
		if (!selections.isEmpty()) {
			fTableViewer.setCheckedElements(selections.toArray());
		}
	}

	private void createWorkingSet() {
		IWorkingSetManager manager= PlatformUI.getWorkbench().getWorkingSetManager();
		IWorkingSetNewWizard wizard= manager.createWorkingSetNewWizard(new String[] {IWorkingSetIDs.JAVA});
		// the wizard can't be null since we have at least the Java working set.
		WizardDialog dialog= new WizardDialog(getShell(), wizard);
		dialog.create();
		if (dialog.open() == Window.OK) {
			IWorkingSet workingSet= wizard.getSelection();
			if (WorkingSetModel.isSupportedAsTopLevelElement(workingSet)) {
				fAllWorkingSets.add(workingSet);
				fTableViewer.add(workingSet);
				fTableViewer.setSelection(new StructuredSelection(workingSet), true);
				fTableViewer.setChecked(workingSet, true);
				manager.addWorkingSet(workingSet);
				fAddedWorkingSets.add(workingSet);
			}
		}
	}

	private void editSelectedWorkingSet() {
		IWorkingSetManager manager= PlatformUI.getWorkbench().getWorkingSetManager();
		IWorkingSet editWorkingSet= (IWorkingSet)((IStructuredSelection)fTableViewer.getSelection()).getFirstElement();
		IWorkingSetEditWizard wizard= manager.createWorkingSetEditWizard(editWorkingSet);
		WizardDialog dialog= new WizardDialog(getShell(), wizard);
		IWorkingSet originalWorkingSet= fEditedWorkingSets.get(editWorkingSet);
		boolean firstEdit= originalWorkingSet == null;

		// save the original working set values for restoration when selection
		// dialog is cancelled.
		if (firstEdit) {
			originalWorkingSet=
				PlatformUI.getWorkbench().getWorkingSetManager().
				createWorkingSet(editWorkingSet.getName(), editWorkingSet.getElements());
		} else {
			fEditedWorkingSets.remove(editWorkingSet);
		}
		dialog.create();
		if (dialog.open() == Window.OK) {
			editWorkingSet= wizard.getSelection();
			if (fIsSortingEnabled)
				fTableViewer.refresh();
			else
				fTableViewer.update(editWorkingSet, null);

			// make sure ok button is enabled when the selected working set
			// is edited. Fixes bug 33386.
			updateButtonAvailability();
		}
		fEditedWorkingSets.put(editWorkingSet, originalWorkingSet);
	}

	/**
	 * Called when the selection has changed.
	 */
	void handleSelectionChanged() {
		updateButtonAvailability();
	}

	/**
	 * Overrides method in Dialog
	 *
	 * @see org.eclipse.jface.dialogs.Dialog#open()
	 */
	@Override
	public int open() {
		fAddedWorkingSets= new ArrayList<IWorkingSet>();
		fRemovedWorkingSets= new ArrayList<IWorkingSet>();
		fEditedWorkingSets= new HashMap<IWorkingSet, IWorkingSet>();
		fRemovedMRUWorkingSets= new ArrayList<IWorkingSet>();
		return super.open();
	}

	/**
	 * Removes the selected working sets from the workbench.
	 */
	private void removeSelectedWorkingSets() {
		ISelection selection= fTableViewer.getSelection();
		if (selection instanceof IStructuredSelection) {
			IWorkingSetManager manager= PlatformUI.getWorkbench().getWorkingSetManager();
			Iterator<?> iter= ((IStructuredSelection)selection).iterator();
			while (iter.hasNext()) {
				IWorkingSet workingSet= (IWorkingSet)iter.next();
				if (fAddedWorkingSets.contains(workingSet)) {
					fAddedWorkingSets.remove(workingSet);
				} else {
					IWorkingSet[] recentWorkingSets= manager.getRecentWorkingSets();
					for (int i= 0; i < recentWorkingSets.length; i++) {
						if (workingSet.equals(recentWorkingSets[i])) {
							fRemovedMRUWorkingSets.add(workingSet);
							break;
						}
					}
					fRemovedWorkingSets.add(workingSet);
				}
				fAllWorkingSets.remove(workingSet);
				manager.removeWorkingSet(workingSet);
			}
			fTableViewer.remove(((IStructuredSelection)selection).toArray());
		}
	}

	/**
	 * Removes newly created working sets from the working set manager.
	 */
	private void restoreAddedWorkingSets() {
		IWorkingSetManager manager= PlatformUI.getWorkbench().getWorkingSetManager();
		Iterator<IWorkingSet> iterator= fAddedWorkingSets.iterator();

		while (iterator.hasNext()) {
			manager.removeWorkingSet(iterator.next());
		}
	}

	/**
	 * Rolls back changes to working sets.
	 */
	private void restoreChangedWorkingSets() {
		Iterator<IWorkingSet> iterator= fEditedWorkingSets.keySet().iterator();

		while (iterator.hasNext()) {
			IWorkingSet editedWorkingSet= iterator.next();
			IWorkingSet originalWorkingSet= fEditedWorkingSets.get(editedWorkingSet);

			if (editedWorkingSet.getName().equals(originalWorkingSet.getName()) == false) {
				editedWorkingSet.setName(originalWorkingSet.getName());
			}
			if (editedWorkingSet.getElements().equals(originalWorkingSet.getElements()) == false) {
				editedWorkingSet.setElements(originalWorkingSet.getElements());
			}
		}
	}

	/**
	 * Adds back removed working sets to the working set manager.
	 */
	private void restoreRemovedWorkingSets() {
		IWorkingSetManager manager= PlatformUI.getWorkbench().getWorkingSetManager();
		Iterator<IWorkingSet> iterator= fRemovedWorkingSets.iterator();

		while (iterator.hasNext()) {
			manager.addWorkingSet(iterator.next());
		}
		iterator= fRemovedMRUWorkingSets.iterator();
		while (iterator.hasNext()) {
			manager.addRecentWorkingSet(iterator.next());
		}
	}

	/**
	 * Updates the modify buttons' enabled state based on the current seleciton.
	 */
	private void updateButtonAvailability() {
		IStructuredSelection selection= (IStructuredSelection) fTableViewer.getSelection();
		boolean hasSelection= !selection.isEmpty();
		boolean hasSingleSelection= selection.size() == 1;

		fRemoveButton.setEnabled(hasSelection && areAllGlobalWorkingSets(selection));
		fEditButton.setEnabled(hasSingleSelection && ((IWorkingSet)selection.getFirstElement()).isEditable());
		if (fUpButton != null) {
			fUpButton.setEnabled(canMoveUp());
		}
		if (fDownButton != null) {
			fDownButton.setEnabled(canMoveDown());
		}
	}

	private boolean areAllGlobalWorkingSets(IStructuredSelection selection) {
		Set<IWorkingSet> globals= new HashSet<IWorkingSet>(Arrays.asList(PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSets()));
		for (Iterator<?> iter= selection.iterator(); iter.hasNext();) {
			if (!globals.contains(iter.next()))
				return false;
		}
		return true;
	}

	private void moveUp(List<IWorkingSet> toMoveUp) {
		if (toMoveUp.size() > 0) {
			setElements(moveUp(fAllWorkingSets, toMoveUp));
			fTableViewer.reveal(toMoveUp.get(0));
		}
	}

	private void moveDown(List<IWorkingSet> toMoveDown) {
		if (toMoveDown.size() > 0) {
			setElements(reverse(moveUp(reverse(fAllWorkingSets), toMoveDown)));
			fTableViewer.reveal(toMoveDown.get(toMoveDown.size() - 1));
		}
	}

	private void setElements(List<IWorkingSet> elements) {
		fAllWorkingSets= elements;
		fTableViewer.setInput(fAllWorkingSets);
		updateButtonAvailability();
	}

	private List<IWorkingSet> moveUp(List<IWorkingSet> elements, List<IWorkingSet> move) {
		int nElements= elements.size();
		List<IWorkingSet> res= new ArrayList<IWorkingSet>(nElements);
		IWorkingSet floating= null;
		for (int i= 0; i < nElements; i++) {
			IWorkingSet curr= elements.get(i);
			if (move.contains(curr)) {
				res.add(curr);
			} else {
				if (floating != null) {
					res.add(floating);
				}
				floating= curr;
			}
		}
		if (floating != null) {
			res.add(floating);
		}
		return res;
	}

	private List<IWorkingSet> reverse(List<IWorkingSet> p) {
		List<IWorkingSet> reverse= new ArrayList<IWorkingSet>(p.size());
		for (int i= p.size() - 1; i >= 0; i--) {
			reverse.add(p.get(i));
		}
		return reverse;
	}

	private boolean canMoveUp() {
		if (!fIsSortingEnabled) {
			int[] indc= fTableViewer.getTable().getSelectionIndices();
			for (int i= 0; i < indc.length; i++) {
				if (indc[i] != i) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean canMoveDown() {
		if (!fIsSortingEnabled) {
			int[] indc= fTableViewer.getTable().getSelectionIndices();
			int k= fAllWorkingSets.size() - 1;
			for (int i= indc.length - 1; i >= 0; i--, k--) {
				if (indc[i] != k) {
					return true;
				}
			}
		}
		return false;
	}

	//---- select / deselect --------------------------------------------------

	private void selectAll() {
		fTableViewer.setAllChecked(true);
	}

	private void deselectAll() {
		fTableViewer.setAllChecked(false);
	}

	/**
	 * Returns the list of newly added working sets through this dialog.
	 * 
	 * @return the list of newly added working sets
	 * @since 3.5
	 */
	public List<IWorkingSet> getNewlyAddedWorkingSets() {
		return fAddedWorkingSets;
		
	}

	/**
	 * Returns whether sorting is enabled for working sets.
	 * 
	 * @return <code>true</code> if sorting is enabled, <code>false</code> otherwise
	 * @since 3.5
	 */
	public boolean isSortingEnabled() {
		return fIsSortingEnabled;
	}

	/**
	 * Returns the working set comparator.
	 * 
	 * @return the working set comparator
	 * @since 3.5
	 */
	private WorkingSetComparator getComparator() {
		if (fComparator == null) {
			fComparator= new WorkingSetComparator(true);
		}
		return fComparator;
	}

	/**
	 * Returns all the working sets.
	 * 
	 * @return all the working sets
	 * @since 3.7
	 */
	public IWorkingSet[] getAllWorkingSets() {
		return fAllWorkingSets.toArray(new IWorkingSet[fAllWorkingSets.size()]);
	}
}

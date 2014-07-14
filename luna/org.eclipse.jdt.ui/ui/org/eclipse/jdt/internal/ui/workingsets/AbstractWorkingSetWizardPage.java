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
package org.eclipse.jdt.internal.ui.workingsets;

import java.util.Arrays;
import java.util.HashSet;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeItem;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.wizard.WizardPage;

import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.IWorkingSetPage;

/**
 * A tree viewer on the left is used to show the workspace content, a table viewer on the
 * right is used to show the working set content. Buttons to move content from right
 * to left and vice versa are available between the two viewers. A text field allows to
 * set/change the working sets name.
 *
 * @since 3.1
 */
public abstract class AbstractWorkingSetWizardPage extends WizardPage implements IWorkingSetPage {

	private final class AddedElementsFilter extends ViewerFilter {

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.ViewerFilter#select(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
		 */
		@Override
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			return !fSelectedElements.contains(element);
		}

	}

	private Text fWorkingSetName;
	private TreeViewer fTree;
	private TableViewer fTable;
	private ITreeContentProvider fTreeContentProvider;

	private boolean fFirstCheck;
	private final HashSet<Object> fSelectedElements;
	private IWorkingSet fWorkingSet;

	protected AbstractWorkingSetWizardPage(String pageName, String title, ImageDescriptor titleImage) {
		super(pageName, title, titleImage);

		fSelectedElements= new HashSet<Object>();
		fFirstCheck= true;
	}

	/**
	 * Returns the page id as specified in the extension point.
	 * @return the page id
	 */
	protected abstract String getPageId();

	/**
	 * Configure the tree viewer used on the left side of the dialog.
	 *
	 * Implementors must set:
	 * <ul>
	 * 	<li>The content provider</li>
	 *  <li>The label provider</li>
	 *  <li>The viewers input</li>
	 * </ul>
	 * They may also set:
	 * <ul>
	 *   <li>The viewer comparator</li>
	 *   <li>Any viewer filter</li>
	 *   <li>The selection</li>
	 * </ul>
	 *
	 * @param tree the tree to configure
	 */
	protected abstract void configureTree(TreeViewer tree);

	/**
	 * Configure the table viewer used on the right side of the dialog.
	 *
	 * Implementors must set:
	 * <ul>
	 *  <li>The label provider</li>
	 * </ul>
	 * They may also set:
	 * <ul>
	 *   <li>The viewer comparator</li>
	 * </ul>
	 * They must not set:
	 * <ul>
	 * 	<li>The viewers content provider</li>
	 * 	<li>The viewers input</li>
	 *  <li>Any viewer filter</li>
	 * </ul>
	 *
	 * @param table the table to configure
	 */
	protected abstract void configureTable(TableViewer table);

	/**
	 * Returns the elements which are shown in the table initially. Return an empty
	 * array if the table should be empty. The given working set is the working set
	 * which will be configured by this dialog, or <b>null</b> if it does not yet
	 * exist.
	 *
	 * @param workingSet the working set to configure or <b>null</b> if not yet exist
	 * @return the elements to show in the table
	 */
	protected abstract Object[] getInitialWorkingSetElements(IWorkingSet workingSet);

	/*
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		initializeDialogUnits(parent);

		Composite composite= new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		setControl(composite);

		Label label= new Label(composite, SWT.WRAP);
		label.setText(WorkingSetMessages.JavaWorkingSetPage_workingSet_name);
		GridData gd= new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_CENTER);
		label.setLayoutData(gd);

		fWorkingSetName= new Text(composite, SWT.SINGLE | SWT.BORDER);
		fWorkingSetName.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
		fWorkingSetName.addModifyListener(
			new ModifyListener() {
				public void modifyText(ModifyEvent e) {
					validateInput();
				}
			}
		);

		Composite leftCenterRightComposite= new Composite(composite, SWT.NONE);
		GridData gridData= new GridData(SWT.FILL, SWT.FILL, true, true);
		gridData.heightHint= convertHeightInCharsToPixels(20);
		leftCenterRightComposite.setLayoutData(gridData);
		GridLayout gridLayout= new GridLayout(3, false);
		gridLayout.marginHeight= 0;
		gridLayout.marginWidth= 0;
		leftCenterRightComposite.setLayout(gridLayout);

		Composite leftComposite= new Composite(leftCenterRightComposite, SWT.NONE);
		gridData= new GridData(SWT.FILL, SWT.FILL, true, true);
		gridData.widthHint= convertWidthInCharsToPixels(40);
		leftComposite.setLayoutData(gridData);
		gridLayout= new GridLayout(1, false);
		gridLayout.marginHeight= 0;
		gridLayout.marginWidth= 0;
		leftComposite.setLayout(gridLayout);

		Composite centerComposite = new Composite(leftCenterRightComposite, SWT.NONE);
		gridLayout= new GridLayout(1, false);
		gridLayout.marginHeight= 0;
		gridLayout.marginWidth= 0;
		centerComposite.setLayout(gridLayout);
		centerComposite.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));

		Composite rightComposite= new Composite(leftCenterRightComposite, SWT.NONE);
		gridData= new GridData(SWT.FILL, SWT.FILL, true, true);
		gridData.widthHint= convertWidthInCharsToPixels(40);
		rightComposite.setLayoutData(gridData);
		gridLayout= new GridLayout(1, false);
		gridLayout.marginHeight= 0;
		gridLayout.marginWidth= 0;
		rightComposite.setLayout(gridLayout);

		createTree(leftComposite);
		createTable(rightComposite);

		if (fWorkingSet != null)
			fWorkingSetName.setText(fWorkingSet.getName());

		initializeSelectedElements();
		validateInput();

		fTable.setInput(fSelectedElements);
		fTable.refresh(true);
		fTree.refresh(true);

		createButtonBar(centerComposite);

		fWorkingSetName.setFocus();
		fWorkingSetName.setSelection(0, fWorkingSetName.getText().length());

		Dialog.applyDialogFont(composite);
	}

	private void createTree(Composite parent) {

		Label label= new Label(parent, SWT.NONE);
		label.setLayoutData(new GridData(SWT.LEAD, SWT.CENTER, false, false));
		label.setText(WorkingSetMessages.JavaWorkingSetPage_workspace_content);

		fTree= new TreeViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI);
		fTree.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		fTree.addFilter(new AddedElementsFilter());
		fTree.setUseHashlookup(true);

		configureTree(fTree);

		fTreeContentProvider= (ITreeContentProvider) fTree.getContentProvider();
	}

	private void createButtonBar(Composite parent) {
		Label spacer= new Label(parent, SWT.NONE);
		spacer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		final Button addButton= new Button(parent, SWT.PUSH);
		addButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		addButton.setText(WorkingSetMessages.JavaWorkingSetPage_add_button);
		addButton.setEnabled(!fTree.getSelection().isEmpty());

		final Button addAllButton= new Button(parent, SWT.PUSH);
		addAllButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		addAllButton.setText(WorkingSetMessages.JavaWorkingSetPage_addAll_button);
		addAllButton.setEnabled(fTree.getTree().getItems().length > 0);

		final Button removeButton= new Button(parent, SWT.PUSH);
		removeButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		removeButton.setText(WorkingSetMessages.JavaWorkingSetPage_remove_button);
		removeButton.setEnabled(!fTable.getSelection().isEmpty());

		final Button removeAllButton= new Button(parent, SWT.PUSH);
		removeAllButton.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));
		removeAllButton.setText(WorkingSetMessages.JavaWorkingSetPage_removeAll_button);
		removeAllButton.setEnabled(!fSelectedElements.isEmpty());

		fTree.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				addButton.setEnabled(!event.getSelection().isEmpty());
			}
		});

		addButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				addTreeSelection();

				removeAllButton.setEnabled(true);
				addAllButton.setEnabled(fTree.getTree().getItems().length > 0);
			}
		});

		fTree.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				addTreeSelection();

				removeAllButton.setEnabled(true);
				addAllButton.setEnabled(fTree.getTree().getItems().length > 0);
			}
		});

		fTable.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				removeButton.setEnabled(!event.getSelection().isEmpty());
			}
		});

		removeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				removeTableSelection();

				addAllButton.setEnabled(true);
				removeAllButton.setEnabled(!fSelectedElements.isEmpty());
			}
		});

		fTable.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				removeTableSelection();

				addAllButton.setEnabled(true);
				removeAllButton.setEnabled(!fSelectedElements.isEmpty());
			}
		});

		addAllButton.addSelectionListener(new SelectionAdapter() {
			/* (non-Javadoc)
			 * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
			 */
			@Override
			public void widgetSelected(SelectionEvent e) {
				TreeItem[] items= fTree.getTree().getItems();
				for (int i= 0; i < items.length; i++) {
					fSelectedElements.add(items[i].getData());
				}
				fTable.refresh();
				fTree.refresh();

				addAllButton.setEnabled(false);
				removeAllButton.setEnabled(true);
			}
		});

		removeAllButton.addSelectionListener(new SelectionAdapter() {
			/* (non-Javadoc)
			 * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
			 */
			@Override
			public void widgetSelected(SelectionEvent e) {
				fSelectedElements.clear();

				fTable.refresh();
				fTree.refresh();

				removeAllButton.setEnabled(false);
				addAllButton.setEnabled(true);
			}
		});

	}

	/**
	 * Moves selected elements in the tree into the table
	 */
	private void addTreeSelection() {
		IStructuredSelection selection= (IStructuredSelection) fTree.getSelection();
		fSelectedElements.addAll(selection.toList());
		Object[] selectedElements= selection.toArray();
		fTable.add(selectedElements);
		fTree.remove(selectedElements);
		fTable.setSelection(selection);
		fTable.getControl().setFocus();
		validateInput();
	}

	/**
	 * Moves the selected elements in the table into the tree
	 */
	private void removeTableSelection() {
		IStructuredSelection selection= (IStructuredSelection) fTable.getSelection();
		fSelectedElements.removeAll(selection.toList());
		Object[] selectedElements= selection.toArray();
		fTable.remove(selectedElements);
		try {
			fTree.getTree().setRedraw(false);
			for (int i= 0; i < selectedElements.length; i++) {
				fTree.refresh(fTreeContentProvider.getParent(selectedElements[i]), true);
			}
		} finally {
			fTree.getTree().setRedraw(true);
		}
		fTree.setSelection(selection);
		fTree.getControl().setFocus();
		validateInput();
	}

	private void createTable(Composite parent) {
		Label label= new Label(parent, SWT.WRAP);
		label.setText(WorkingSetMessages.JavaWorkingSetPage_workingSet_content);
		label.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		fTable= new TableViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI);

		GridData gd= new GridData(SWT.FILL, SWT.FILL, true, true);
		fTable.getControl().setLayoutData(gd);

		fTable.setUseHashlookup(true);

		configureTable(fTable);

		fTable.setContentProvider(new IStructuredContentProvider() {

			public Object[] getElements(Object inputElement) {
				return fSelectedElements.toArray();
			}

			public void dispose() {
			}

			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			}

		});
	}

	/*
	 * Implements method from IWorkingSetPage
	 */
	public IWorkingSet getSelection() {
		return fWorkingSet;
	}

	/*
	 * Implements method from IWorkingSetPage
	 */
	public void setSelection(IWorkingSet workingSet) {
		Assert.isNotNull(workingSet, "Working set must not be null"); //$NON-NLS-1$
		fWorkingSet= workingSet;
		if (getContainer() != null && getShell() != null && fWorkingSetName != null) {
			fFirstCheck= false;
			fWorkingSetName.setText(fWorkingSet.getName());
			initializeSelectedElements();
			validateInput();
		}
	}

	/*
	 * Implements method from IWorkingSetPage
	 */
	public void finish() {
		String workingSetName= fWorkingSetName.getText();
		HashSet<Object> elements= fSelectedElements;

		if (fWorkingSet == null) {
			IWorkingSetManager workingSetManager= PlatformUI.getWorkbench().getWorkingSetManager();
			fWorkingSet= workingSetManager.createWorkingSet(workingSetName, elements.toArray(new IAdaptable[elements.size()]));
			fWorkingSet.setId(getPageId());
		} else {
			// Add inaccessible resources
			IAdaptable[] oldItems= fWorkingSet.getElements();
			HashSet<IProject> closedProjectsToRetain= new HashSet<IProject>(elements.size());
			HashSet<IProject> closedProjectsToRemove= new HashSet<IProject>(elements.size());
			for (int i= 0; i < oldItems.length; i++) {
				IResource oldResource= null;
				if (oldItems[i] instanceof IResource) {
					oldResource= (IResource) oldItems[i];
				} else {
					oldResource= (IResource) oldItems[i].getAdapter(IResource.class);
				}
				if (oldResource != null && oldResource.isAccessible() == false) {
					IProject project= oldResource.getProject();
					if (oldResource.equals(project)) {
						closedProjectsToRetain.add(project);
					} else	if (elements.contains(project)) {
						elements.add(oldItems[i]);
						closedProjectsToRemove.add(project);
					}
				}
			}
			closedProjectsToRemove.removeAll(closedProjectsToRetain);
			elements.removeAll(closedProjectsToRemove);

			fWorkingSet.setName(workingSetName);
			fWorkingSet.setElements(elements.toArray(new IAdaptable[elements.size()]));
		}
	}

	private void validateInput() {
		String errorMessage= null;
		String infoMessage= null;
		String newText= fWorkingSetName.getText();

		if (newText.equals(newText.trim()) == false)
			errorMessage = WorkingSetMessages.JavaWorkingSetPage_warning_nameWhitespace;
		if (newText.equals("")) { //$NON-NLS-1$
			if (fFirstCheck) {
				setPageComplete(false);
				fFirstCheck= false;
				return;
			} else
				errorMessage= WorkingSetMessages.JavaWorkingSetPage_warning_nameMustNotBeEmpty;
		}

		fFirstCheck= false;

		if (errorMessage == null && (fWorkingSet == null || newText.equals(fWorkingSet.getName()) == false)) {
			IWorkingSet[] workingSets= PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSets();
			for (int i= 0; i < workingSets.length; i++) {
				if (newText.equals(workingSets[i].getName())) {
					errorMessage= WorkingSetMessages.JavaWorkingSetPage_warning_workingSetExists;
				}
			}
		}

		if (!hasSelectedElement())
			infoMessage= WorkingSetMessages.JavaWorkingSetPage_warning_resourceMustBeChecked;

		setMessage(infoMessage, INFORMATION);
		setErrorMessage(errorMessage);
		setPageComplete(errorMessage == null);
	}

	private boolean hasSelectedElement() {
		return !fSelectedElements.isEmpty();
	}

	private void initializeSelectedElements() {
		fSelectedElements.addAll(Arrays.asList(getInitialWorkingSetElements(fWorkingSet)));
	}

}

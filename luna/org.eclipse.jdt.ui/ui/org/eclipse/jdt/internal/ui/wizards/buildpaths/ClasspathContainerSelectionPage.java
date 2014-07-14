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
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.wizard.WizardPage;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.util.SelectionUtil;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;

/**
  */
public class ClasspathContainerSelectionPage extends WizardPage {

	private static final String DIALOGSTORE_SECTION= "ClasspathContainerSelectionPage"; //$NON-NLS-1$
	private static final String DIALOGSTORE_CONTAINER_IDX= "index"; //$NON-NLS-1$


	private static class ClasspathContainerLabelProvider extends LabelProvider {
		@Override
		public String getText(Object element) {
			return ((ClasspathContainerDescriptor) element).getName();
		}
	}

	private ListViewer fListViewer;
	private ClasspathContainerDescriptor[] fContainers;
	private IDialogSettings fDialogSettings;

	/**
	 * Constructor for ClasspathContainerWizardPage.
	 * 
	 * @param containerPages the array of container pages
	 */
	protected ClasspathContainerSelectionPage(ClasspathContainerDescriptor[] containerPages) {
		super("ClasspathContainerWizardPage"); //$NON-NLS-1$
		setTitle(NewWizardMessages.ClasspathContainerSelectionPage_title);
		setDescription(NewWizardMessages.ClasspathContainerSelectionPage_description);
		setImageDescriptor(JavaPluginImages.DESC_WIZBAN_ADD_LIBRARY);

		fContainers= containerPages;

		IDialogSettings settings= JavaPlugin.getDefault().getDialogSettings();
		fDialogSettings= settings.getSection(DIALOGSTORE_SECTION);
		if (fDialogSettings == null) {
			fDialogSettings= settings.addNewSection(DIALOGSTORE_SECTION);
			fDialogSettings.put(DIALOGSTORE_CONTAINER_IDX, 0);
		}
		validatePage();
	}

	/* (non-Javadoc)
	 * @see IDialogPage#createControl(Composite)
	 */
	public void createControl(Composite parent) {
		fListViewer= new ListViewer(parent, SWT.SINGLE | SWT.BORDER);
		fListViewer.setLabelProvider(new ClasspathContainerLabelProvider());
		fListViewer.setContentProvider(new ArrayContentProvider());
		fListViewer.setComparator(new ViewerComparator());
		fListViewer.setInput(Arrays.asList(fContainers));
		fListViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				validatePage();
			}
		});
		fListViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				doDoubleClick();
			}
		});

		int selectionIndex= fDialogSettings.getInt(DIALOGSTORE_CONTAINER_IDX);
		if (selectionIndex >= fContainers.length) {
			selectionIndex= 0;
		}
		fListViewer.getList().select(selectionIndex);
		validatePage();
		setControl(fListViewer.getList());
		Dialog.applyDialogFont(fListViewer.getList());

		PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, IJavaHelpContextIds.BP_SELECT_CLASSPATH_CONTAINER);
	}

	/**
	 * Method validatePage.
	 */
	private void validatePage() {
		setPageComplete(getSelected() != null);
	}


	public ClasspathContainerDescriptor getSelected() {
		if (fListViewer != null) {
			ISelection selection= fListViewer.getSelection();
			return (ClasspathContainerDescriptor) SelectionUtil.getSingleElement(selection);
		}
		return null;
	}

	public ClasspathContainerDescriptor[] getContainers() {
		return fContainers;
	}

	protected void doDoubleClick() {
		if (canFlipToNextPage()) {
			getContainer().showPage(getNextPage());
		}
	}

	/* (non-Javadoc)
	 * @see IWizardPage#canFlipToNextPage()
	 */
	@Override
	public boolean canFlipToNextPage() {
		return isPageComplete(); // avoid the getNextPage call to prevent potential plugin load
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.IDialogPage#setVisible(boolean)
	 */
	@Override
	public void setVisible(boolean visible) {
		if (!visible && fListViewer != null) {
			fDialogSettings.put(DIALOGSTORE_CONTAINER_IDX, fListViewer.getList().getSelectionIndex());
		}
		super.setVisible(visible);
	}

}

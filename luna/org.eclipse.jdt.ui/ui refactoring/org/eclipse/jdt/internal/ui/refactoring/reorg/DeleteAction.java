/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring.reorg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.ibm.icu.text.MessageFormat;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.DeleteResourceAction;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringExecutionStarter;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgUtils;

import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.SelectionUtil;
import org.eclipse.jdt.internal.ui.workingsets.IWorkingSetIDs;
import org.eclipse.jdt.internal.ui.workingsets.WorkingSetModel;


public class DeleteAction extends SelectionDispatchAction {

	/**
	 * 'Hide' button index.
	 * 
	 * @since 3.5
	 */
	private static final int HIDE_BUTTON= 0;

	/**
	 * 'Remove' button index.
	 * 
	 * @since 3.5
	 */
	private static final int REMOVE_BUTTON= 1;


	public DeleteAction(IWorkbenchSite site) {
		super(site);
		setText(ReorgMessages.DeleteAction_3);
		setDescription(ReorgMessages.DeleteAction_4);
		ISharedImages workbenchImages= JavaPlugin.getDefault().getWorkbench().getSharedImages();
		setDisabledImageDescriptor(workbenchImages.getImageDescriptor(ISharedImages.IMG_TOOL_DELETE_DISABLED));
		setImageDescriptor(workbenchImages.getImageDescriptor(ISharedImages.IMG_TOOL_DELETE));
		setHoverImageDescriptor(workbenchImages.getImageDescriptor(ISharedImages.IMG_TOOL_DELETE));

		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.DELETE_ACTION);
	}

	/*
	 * @see SelectionDispatchAction#selectionChanged(IStructuredSelection)
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		if (ReorgUtils.containsOnlyProjects(selection.toList())) {
			setEnabled(createWorkbenchAction(selection).isEnabled());
			return;
		}
		setEnabled(RefactoringAvailabilityTester.isDeleteAvailable(selection.toArray()));
	}

	private IAction createWorkbenchAction(IStructuredSelection selection) {
		DeleteResourceAction action= new DeleteResourceAction(getSite());
		action.selectionChanged(selection);
		return action;
	}

	/**
	 * Removes or hides the selected working sets.
	 * 
	 * @param selection the selected working sets
	 * @since 3.5
	 */
	private void deleteWorkingSets(IStructuredSelection selection) {
		MessageDialog dialog;
		if (selection.size() == 1) {
			IWorkingSet workingSet= (IWorkingSet)selection.getFirstElement();
			final String workingSetID= workingSet.getId();
			String dialogMessage;
			if (isDefaultWorkingSet(workingSetID))
				dialogMessage= MessageFormat.format(ReorgMessages.DeleteWorkingSet_hideworkingset_single, new Object[] { workingSet.getLabel() });
			else
				dialogMessage= MessageFormat.format(ReorgMessages.DeleteWorkingSet_removeorhideworkingset_single, new Object[] { workingSet.getLabel() });
			
			dialog= new MessageDialog(getShell(), ReorgMessages.DeleteWorkingSet_single, null, dialogMessage, MessageDialog.QUESTION, new String[] { ReorgMessages.DeleteWorkingSet_Hide,
					ReorgMessages.DeleteWorkingSet_Remove,
					IDialogConstants.CANCEL_LABEL }, 0) {
				/*
				 * @see org.eclipse.jface.dialogs.MessageDialog#createButton(org.eclipse.swt.widgets.Composite, int, java.lang.String, boolean)
				 * @since 3.5
				 */
				@Override
				protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
					Button button= super.createButton(parent, id, label, defaultButton);
					if (id == REMOVE_BUTTON && IWorkingSetIDs.OTHERS.equals(workingSetID))
						button.setEnabled(false);
					return button;
				}
			};
		} else {
			dialog= new MessageDialog(getShell(), ReorgMessages.DeleteWorkingSet_multiple, null, MessageFormat.format(ReorgMessages.DeleteWorkingSet_removeorhideworkingset_multiple,
					new Object[] { new Integer(selection.size()) }),
					MessageDialog.QUESTION, new String[] { ReorgMessages.DeleteWorkingSet_Hide, ReorgMessages.DeleteWorkingSet_Remove,
							IDialogConstants.CANCEL_LABEL }, 0);
		}

		int dialogResponse= dialog.open();
		if (dialogResponse == REMOVE_BUTTON) {
			Iterator<?> iter= selection.iterator();
			IWorkingSetManager manager= PlatformUI.getWorkbench().getWorkingSetManager();
			while (iter.hasNext()) {
				IWorkingSet workingSet= (IWorkingSet) iter.next();
				if (isDefaultWorkingSet(workingSet.getId()))
					hideWorkingSets(Collections.singletonList(workingSet));
				else
					manager.removeWorkingSet(workingSet);
			}
		} else if (dialogResponse == HIDE_BUTTON) {
			@SuppressWarnings("unchecked")
			List<IWorkingSet> workingSets= (List<IWorkingSet>) SelectionUtil.toList(selection);
			hideWorkingSets(workingSets);
		}
	}

	/**
	 * Hides all the working sets in the list from the Package Explorer.
	 * 
	 * @param selection the selection of working sets 
	 * @since 3.8
	 */
	private void hideWorkingSets(List<IWorkingSet> selection) {
		IWorkbenchPage page= JavaPlugin.getActivePage();
		if (page != null) {
			IWorkbenchPart activePart= page.getActivePart();
			if (activePart instanceof PackageExplorerPart) {
				PackageExplorerPart packagePart= (PackageExplorerPart) activePart;
				WorkingSetModel model= packagePart.getWorkingSetModel();
				List<IWorkingSet> activeWorkingSets= new ArrayList<IWorkingSet>(Arrays.asList(model.getActiveWorkingSets()));
				activeWorkingSets.removeAll(selection);
				model.setActiveWorkingSets(activeWorkingSets.toArray(new IWorkingSet[activeWorkingSets.size()]));
			}
		}
	}

	/**
	 * Checks if the working set is the default working set.
	 * 
	 * @param workingSetID the working set id, can be <code>null</code>
	 * @return <code>true</code> if default working set, <code>false</code> otherwise 
	 * @since 3.8
	 */
	private boolean isDefaultWorkingSet(String workingSetID) {
		return IWorkingSetIDs.OTHERS.equals(workingSetID);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#run(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	@Override
	public void run(IStructuredSelection selection) {
		if (ReorgUtils.containsOnlyProjects(selection.toList())) {
			createWorkbenchAction(selection).run();
			return;
		}
		if (ReorgUtils.containsOnlyWorkingSets(selection.toList())){
			deleteWorkingSets(selection);
			return;
		}
		try {
			RefactoringExecutionStarter.startDeleteRefactoring(selection.toArray(), getShell());
		} catch (CoreException e) {
			ExceptionHandler.handle(e, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
		}
	}
}

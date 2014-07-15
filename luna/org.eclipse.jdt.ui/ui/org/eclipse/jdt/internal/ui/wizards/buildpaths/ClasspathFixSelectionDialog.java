/*******************************************************************************
 * Copyright (c) 2008, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.dialogs.PreferencesUtil;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.RefactoringCore;

import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.text.java.ClasspathFixProcessor.ClasspathFixProposal;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.text.correction.ClasspathFixProcessorDescriptor;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;

/**
 * @since 3.4
 */
public class ClasspathFixSelectionDialog extends StatusDialog {

	public static boolean openClasspathFixSelectionDialog(Shell parent, final IJavaProject project, final String missingType, IRunnableContext context) {
		final ClasspathFixProposal[][] classPathFixProposals= { null };
		try {
			context.run(true, true, new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					MultiStatus status= new MultiStatus(JavaUI.ID_PLUGIN, IStatus.OK, NewWizardMessages.ClasspathFixSelectionDialog_eval_proposals_error_message, null);
					classPathFixProposals[0]= ClasspathFixProcessorDescriptor.getProposals(project, missingType, status);
					if (!status.isOK()) {
						JavaPlugin.log(status);
					}
				}
			});
		} catch (InvocationTargetException e) {
			JavaPlugin.log(e);
		} catch (InterruptedException e) {
			// user pressed cancel
		}
		final ClasspathFixSelectionDialog dialog= new ClasspathFixSelectionDialog(parent, project, missingType, classPathFixProposals[0]);
		if (dialog.open() == Window.OK) {
			try {
				context.run(false, true, new IRunnableWithProgress() { // don't fork, because change execution must be in UI thread
					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						if (monitor == null) {
							monitor= new NullProgressMonitor();
						}
						monitor.beginTask(NewWizardMessages.ClasspathFixSelectionDialog_process_fix_description, 4);
						try {
							ClasspathFixProposal fix= dialog.getSelectedClasspathFix();
							Change change= fix.createChange(new SubProgressMonitor(monitor, 1));

							PerformChangeOperation op= new PerformChangeOperation(change);
							op.setUndoManager(RefactoringCore.getUndoManager(), change.getName());
							op.run(new SubProgressMonitor(monitor, 1));
						} catch (OperationCanceledException e) {
							throw new InterruptedException();
						} catch (CoreException e) {
							throw new InvocationTargetException(e);
						} finally {
							monitor.done();
						}
					}
				});
				return true;
			} catch (InvocationTargetException e) {
				ExceptionHandler.handle(e, NewWizardMessages.ClasspathFixSelectionDialog_apply_proposal_error_title , NewWizardMessages.ClasspathFixSelectionDialog_apply_proposal_error_message);
			} catch (InterruptedException e) {
				// user pressed cancel
			}
		}
		return false;
	}

	private static final String BUILD_PATH_PAGE_ID= "org.eclipse.jdt.ui.propertyPages.BuildPathsPropertyPage"; //$NON-NLS-1$
	private static final Object BUILD_PATH_BLOCK= "block_until_buildpath_applied"; //$NON-NLS-1$


	private TableViewer fFixSelectionTable;
	private ClasspathFixProposal fSelectedFix;

	private final IJavaProject fProject;
	private final String fMissingType;
	private final ClasspathFixProposal[] fClasspathFixProposals;

	private ClasspathFixSelectionDialog(Shell parent, IJavaProject project, String missingType, ClasspathFixProposal[] classpathFixProposals) {
		super(parent);

		setTitle(NewWizardMessages.ClasspathFixSelectionDialog_dialog_title);

		fClasspathFixProposals= classpathFixProposals;
		fProject= project;
		fMissingType= missingType;
		fSelectedFix= null;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	private Link createLink(Composite composite, ListenerMix listener) {
		Link link= new Link(composite, SWT.WRAP);
		GridData layoutData= new GridData(SWT.FILL, SWT.CENTER, true, false);
		layoutData.widthHint= convertWidthInCharsToPixels(80);
		link.setLayoutData(layoutData);
		link.addSelectionListener(listener);
		return link;
	}


	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite= (Composite) super.createDialogArea(parent);

		ListenerMix listener= new ListenerMix();

		int count= fClasspathFixProposals.length;
		if (count == 0) {
			Link link= createLink(composite, listener);
			String[] args= {  BasicElementLabels.getJavaElementName(fMissingType), BasicElementLabels.getJavaElementName(fProject.getElementName()) };
			link.setText(Messages.format(NewWizardMessages.ClasspathFixSelectionDialog_no_proposals_message, args));
			updateStatus(new StatusInfo(IStatus.ERROR, new String()));
		} else {
			Label label= new Label(composite, SWT.WRAP);
			GridData layoutData= new GridData(SWT.FILL, SWT.CENTER, false, false);
			layoutData.widthHint= convertWidthInCharsToPixels(80);
			label.setLayoutData(layoutData);

			String[] args= { BasicElementLabels.getJavaElementName(fMissingType) };
			label.setText(Messages.format(NewWizardMessages.ClasspathFixSelectionDialog_proposals_message, args));

			fFixSelectionTable= new TableViewer(composite, SWT.SINGLE | SWT.BORDER);
			fFixSelectionTable.setContentProvider(new ArrayContentProvider());
			fFixSelectionTable.setLabelProvider(new ClasspathFixLabelProvider());
			fFixSelectionTable.setComparator(new ViewerComparator() {
				@Override
				public int category(Object element) {
					return - ((ClasspathFixProposal) element).getRelevance();
				}
			});
			fFixSelectionTable.addDoubleClickListener(listener);
			fFixSelectionTable.setInput(fClasspathFixProposals);
			Table table= fFixSelectionTable.getTable();
			table.select(0);
			fFixSelectionTable.addSelectionChangedListener(listener);

			Dialog.applyDialogFont(table);
			
			GridData gridData= new GridData(SWT.FILL, SWT.FILL, true, true);
			gridData.heightHint= table.getItemHeight() * Math.max(4, Math.min(10, count));
			gridData.widthHint= convertWidthInCharsToPixels(50);
			table.setLayoutData(gridData);

			Link link= createLink(composite, listener);
			link.setText(Messages.format(NewWizardMessages.ClasspathFixSelectionDialog_open_buld_path_dialog_message, BasicElementLabels.getJavaElementName(fProject.getElementName())));

			performSelectionChanged();
		}

		Dialog.applyDialogFont(composite);

		return composite;
	}


	protected final void configureBuildPathPressed() {
		cancelPressed();
		String id= BUILD_PATH_PAGE_ID;
		Map<Object, Boolean> input= new HashMap<Object, Boolean>();
		input.put(BUILD_PATH_BLOCK, Boolean.TRUE);
		if (PreferencesUtil.createPropertyDialogOn(getShell(), fProject, id, new String[] { id }, input).open() != Window.OK) {
			return;
		}
	}

	protected final void performSelectionChanged() {
		StatusInfo status= new StatusInfo();
		IStructuredSelection selection= (IStructuredSelection) fFixSelectionTable.getSelection();
		Object firstElement= selection.getFirstElement();
		if (firstElement instanceof ClasspathFixProposal) {
			fSelectedFix= (ClasspathFixProposal) firstElement;
		} else {
			status.setError(""); //$NON-NLS-1$
		}
		updateStatus(status);
	}


	protected final void performDoubleClick() {
		if (fSelectedFix != null) {
			okPressed();
		}
	}

	public ClasspathFixProposal getSelectedClasspathFix() {
		return fSelectedFix;
	}

	private class ListenerMix implements IDoubleClickListener, ISelectionChangedListener, SelectionListener {

		public void doubleClick(DoubleClickEvent event) {
			performDoubleClick();
		}

		public void selectionChanged(SelectionChangedEvent event) {
			performSelectionChanged();
		}

		public void widgetDefaultSelected(SelectionEvent e) {
			configureBuildPathPressed();
		}

		public void widgetSelected(SelectionEvent e) {
			configureBuildPathPressed();
		}
	}

	private static class ClasspathFixLabelProvider extends LabelProvider {

		@Override
		public Image getImage(Object element) {
			if (element instanceof ClasspathFixProposal) {
				ClasspathFixProposal classpathFixProposal= (ClasspathFixProposal) element;
				return classpathFixProposal.getImage();
			}
			return null;
		}

		@Override
		public String getText(Object element) {
			if (element instanceof ClasspathFixProposal) {
				ClasspathFixProposal classpathFixProposal= (ClasspathFixProposal) element;
				return classpathFixProposal.getDisplayString();
			}
			return null;
		}
	}

}

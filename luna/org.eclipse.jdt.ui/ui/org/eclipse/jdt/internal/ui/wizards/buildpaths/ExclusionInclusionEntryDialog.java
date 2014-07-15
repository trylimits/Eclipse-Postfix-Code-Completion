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

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;

import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceComparator;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;

public class ExclusionInclusionEntryDialog extends StatusDialog {

	private StringButtonDialogField fExclusionPatternDialog;
	private StatusInfo fExclusionPatternStatus;

	private IContainer fCurrSourceFolder;
	private String fExclusionPattern;
	private List<String> fExistingPatterns;
	private boolean fIsExclusion;

	public ExclusionInclusionEntryDialog(Shell parent, boolean isExclusion, String patternToEdit, List<String> existingPatterns, CPListElement entryToEdit) {
		super(parent);
		fIsExclusion= isExclusion;
		fExistingPatterns= existingPatterns;
		String title, message;
		if (isExclusion) {
			if (patternToEdit == null) {
				title= NewWizardMessages.ExclusionInclusionEntryDialog_exclude_add_title;
			} else {
				title= NewWizardMessages.ExclusionInclusionEntryDialog_exclude_edit_title;
			}
			message= Messages.format(NewWizardMessages.ExclusionInclusionEntryDialog_exclude_pattern_label, BasicElementLabels.getPathLabel(entryToEdit.getPath(), false));
		} else {
			if (patternToEdit == null) {
				title= NewWizardMessages.ExclusionInclusionEntryDialog_include_add_title;
			} else {
				title= NewWizardMessages.ExclusionInclusionEntryDialog_include_edit_title;
			}
			message= Messages.format(NewWizardMessages.ExclusionInclusionEntryDialog_include_pattern_label, BasicElementLabels.getPathLabel(entryToEdit.getPath(), false));
		}
		setTitle(title);
		if (patternToEdit != null) {
			fExistingPatterns.remove(patternToEdit);
		}


		IWorkspaceRoot root= entryToEdit.getJavaProject().getProject().getWorkspace().getRoot();
		IResource res= root.findMember(entryToEdit.getPath());
		if (res instanceof IContainer) {
			fCurrSourceFolder= (IContainer) res;
		}

		fExclusionPatternStatus= new StatusInfo();

		ExclusionPatternAdapter adapter= new ExclusionPatternAdapter();
		fExclusionPatternDialog= new StringButtonDialogField(adapter);
		fExclusionPatternDialog.setLabelText(message);
		fExclusionPatternDialog.setButtonLabel(NewWizardMessages.ExclusionInclusionEntryDialog_pattern_button);
		fExclusionPatternDialog.setDialogFieldListener(adapter);
		fExclusionPatternDialog.enableButton(fCurrSourceFolder != null);

		if (patternToEdit == null) {
			fExclusionPatternDialog.setText(""); //$NON-NLS-1$
		} else {
			fExclusionPatternDialog.setText(patternToEdit.toString());
		}
	}


	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite= (Composite)super.createDialogArea(parent);

		int widthHint= convertWidthInCharsToPixels(60);

		Composite inner= new Composite(composite, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.numColumns= 2;
		inner.setLayout(layout);

		Label description= new Label(inner, SWT.WRAP);

		if (fIsExclusion) {
			description.setText(NewWizardMessages.ExclusionInclusionEntryDialog_exclude_description);
		} else {
			description.setText(NewWizardMessages.ExclusionInclusionEntryDialog_include_description);
		}
		GridData gd= new GridData();
		gd.horizontalSpan= 2;
		gd.widthHint= convertWidthInCharsToPixels(80);
		description.setLayoutData(gd);

		fExclusionPatternDialog.doFillIntoGrid(inner, 3);

		LayoutUtil.setWidthHint(fExclusionPatternDialog.getLabelControl(null), widthHint);
		LayoutUtil.setHorizontalSpan(fExclusionPatternDialog.getLabelControl(null), 2);

		LayoutUtil.setWidthHint(fExclusionPatternDialog.getTextControl(null), widthHint);
		LayoutUtil.setHorizontalGrabbing(fExclusionPatternDialog.getTextControl(null));

		fExclusionPatternDialog.postSetFocusOnDialogField(parent.getDisplay());
		applyDialogFont(composite);
		return composite;
	}


	// -------- ExclusionPatternAdapter --------

	private class ExclusionPatternAdapter implements IDialogFieldListener, IStringButtonAdapter {

		// -------- IDialogFieldListener

		public void dialogFieldChanged(DialogField field) {
			doStatusLineUpdate();
		}

		public void changeControlPressed(DialogField field) {
			doChangeControlPressed();
		}
	}

	protected void doChangeControlPressed() {
		IPath pattern= chooseExclusionPattern();
		if (pattern != null) {
			fExclusionPatternDialog.setText(pattern.toString());
		}
	}

	protected void doStatusLineUpdate() {
		checkIfPatternValid();
		updateStatus(fExclusionPatternStatus);
	}

	protected void checkIfPatternValid() {
		String pattern= fExclusionPatternDialog.getText().trim();
		if (pattern.length() == 0) {
			fExclusionPatternStatus.setError(NewWizardMessages.ExclusionInclusionEntryDialog_error_empty);
			return;
		}
		IPath path= new Path(pattern);
		if (path.isAbsolute() || path.getDevice() != null) {
			fExclusionPatternStatus.setError(NewWizardMessages.ExclusionInclusionEntryDialog_error_notrelative);
			return;
		}
		if (fExistingPatterns.contains(pattern)) {
			fExclusionPatternStatus.setError(NewWizardMessages.ExclusionInclusionEntryDialog_error_exists);
			return;
		}

		fExclusionPattern= pattern;
		fExclusionPatternStatus.setOK();
	}


	public String getExclusionPattern() {
		return fExclusionPattern;
	}

	/*
	 * @see org.eclipse.jface.window.Window#configureShell(Shell)
	 */
	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell, IJavaHelpContextIds.EXCLUSION_PATTERN_DIALOG);
	}

	// ---------- util method ------------

	private IPath chooseExclusionPattern() {
		String title, message;
		if (fIsExclusion) {
			title= NewWizardMessages.ExclusionInclusionEntryDialog_ChooseExclusionPattern_title;
			message= NewWizardMessages.ExclusionInclusionEntryDialog_ChooseExclusionPattern_description;
		} else {
			title= NewWizardMessages.ExclusionInclusionEntryDialog_ChooseInclusionPattern_title;
			message= NewWizardMessages.ExclusionInclusionEntryDialog_ChooseInclusionPattern_description;
		}
		IPath initialPath= new Path(fExclusionPatternDialog.getText());

		IPath[] res= chooseExclusionPattern(getShell(), fCurrSourceFolder, title, message, initialPath, false);
		if (res == null) {
			return null;
		}
		return res[0];
	}

	public static IPath[] chooseExclusionPattern(Shell shell, IContainer currentSourceFolder, String title, String message, IPath initialPath, boolean multiSelection) {
		Class<?>[] acceptedClasses= new Class[] { IFolder.class, IFile.class };
		ISelectionStatusValidator validator= new TypedElementSelectionValidator(acceptedClasses, multiSelection);
		ViewerFilter filter= new TypedViewerFilter(acceptedClasses);


		ILabelProvider lp= new WorkbenchLabelProvider();
		ITreeContentProvider cp= new WorkbenchContentProvider();

		IResource initialElement= null;
		if (initialPath != null) {
			IContainer curr= currentSourceFolder;
			int nSegments= initialPath.segmentCount();
			for (int i= 0; i < nSegments; i++) {
				IResource elem= curr.findMember(initialPath.segment(i));
				if (elem != null) {
					initialElement= elem;
				}
				if (elem instanceof IContainer) {
					curr= (IContainer) elem;
				} else {
					break;
				}
			}
		}

		ElementTreeSelectionDialog dialog= new ElementTreeSelectionDialog(shell, lp, cp);
		dialog.setTitle(title);
		dialog.setValidator(validator);
		dialog.setMessage(message);
		dialog.addFilter(filter);
		dialog.setInput(currentSourceFolder);
		dialog.setInitialSelection(initialElement);
		dialog.setComparator(new ResourceComparator(ResourceComparator.NAME));
		dialog.setHelpAvailable(false);

		if (dialog.open() == Window.OK) {
			Object[] objects= dialog.getResult();
			int existingSegments= currentSourceFolder.getFullPath().segmentCount();

			IPath[] resArr= new IPath[objects.length];
			for (int i= 0; i < objects.length; i++) {
				IResource currRes= (IResource) objects[i];
				IPath path= currRes.getFullPath().removeFirstSegments(existingSegments).makeRelative();
				if (currRes instanceof IContainer) {
					path= path.addTrailingSeparator();
				}
				resArr[i]= path;
			}
			return resArr;
		}
		return null;
	}



}

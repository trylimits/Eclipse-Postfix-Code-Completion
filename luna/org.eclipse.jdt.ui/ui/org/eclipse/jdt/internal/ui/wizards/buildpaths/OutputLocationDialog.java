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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;

import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceComparator;

import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.corext.buildpath.CPJavaProject;
import org.eclipse.jdt.internal.corext.buildpath.ClasspathModifier;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;

public class OutputLocationDialog extends StatusDialog {

	private StringButtonDialogField fContainerDialogField;
	private SelectionButtonDialogField fUseDefault;
	private SelectionButtonDialogField fUseSpecific;
	private IStatus fContainerFieldStatus;

	private IPath fOutputLocation;
	private final IProject fCurrProject;
	private final CPListElement fEntryToEdit;
	private final boolean fAllowInvalidClasspath;
	private CPJavaProject fCPJavaProject;

	public OutputLocationDialog(Shell parent, CPListElement entryToEdit, List<CPListElement> classPathList, IPath defaultOutputFolder, boolean allowInvalidClasspath) {
		super(parent);
		fEntryToEdit= entryToEdit;
		fAllowInvalidClasspath= allowInvalidClasspath;
		setTitle(NewWizardMessages.OutputLocationDialog_title);
		fContainerFieldStatus= new StatusInfo();

		OutputLocationAdapter adapter= new OutputLocationAdapter();

		fUseDefault= new SelectionButtonDialogField(SWT.RADIO);
		fUseDefault.setLabelText(Messages.format(NewWizardMessages.OutputLocationDialog_usedefault_label, BasicElementLabels.getPathLabel(defaultOutputFolder, false)));
		fUseDefault.setDialogFieldListener(adapter);

		String label= Messages.format(NewWizardMessages.OutputLocationDialog_usespecific_label, BasicElementLabels.getResourceName(entryToEdit.getPath().segment(0)));
		fUseSpecific= new SelectionButtonDialogField(SWT.RADIO);
		fUseSpecific.setLabelText(label);
		fUseSpecific.setDialogFieldListener(adapter);

		fContainerDialogField= new StringButtonDialogField(adapter);
		fContainerDialogField.setButtonLabel(NewWizardMessages.OutputLocationDialog_location_button);
		fContainerDialogField.setDialogFieldListener(adapter);

		fUseSpecific.attachDialogField(fContainerDialogField);

		IJavaProject javaProject= entryToEdit.getJavaProject();
		fCurrProject= javaProject.getProject();
		fCPJavaProject= new CPJavaProject(javaProject, classPathList, defaultOutputFolder);

		IPath outputLocation= (IPath) entryToEdit.getAttribute(CPListElement.OUTPUT);
		if (outputLocation == null) {
			fUseDefault.setSelection(true);
		} else {
			fUseSpecific.setSelection(true);
			fContainerDialogField.setText(outputLocation.removeFirstSegments(1).makeRelative().toString());
		}
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite= (Composite)super.createDialogArea(parent);

		int widthHint= convertWidthInCharsToPixels(70);
		int indent= convertWidthInCharsToPixels(4);

		Composite inner= new Composite(composite, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.numColumns= 2;
		inner.setLayout(layout);

		fUseDefault.doFillIntoGrid(inner, 2);
		fUseSpecific.doFillIntoGrid(inner, 2);

		Text textControl= fContainerDialogField.getTextControl(inner);
		GridData textData= new GridData();
		textData.widthHint= widthHint;
		textData.grabExcessHorizontalSpace= true;
		textData.horizontalIndent= indent;
		textControl.setLayoutData(textData);

		Button buttonControl= fContainerDialogField.getChangeControl(inner);
		GridData buttonData= new GridData();
		buttonData.widthHint= SWTUtil.getButtonWidthHint(buttonControl);
		buttonControl.setLayoutData(buttonData);

		applyDialogFont(composite);
		return composite;
	}


	// -------- OutputLocationAdapter --------

	private class OutputLocationAdapter implements IDialogFieldListener, IStringButtonAdapter {

		// -------- IDialogFieldListener

		public void dialogFieldChanged(DialogField field) {
			doStatusLineUpdate();
		}

		public void changeControlPressed(DialogField field) {
			doChangeControlPressed();
		}
	}

	protected void doChangeControlPressed() {
		IContainer container= chooseOutputLocation();
		if (container != null) {
			fContainerDialogField.setText(container.getProjectRelativePath().toString());
		}
	}

	protected void doStatusLineUpdate() {
		checkIfPathValid();
		updateStatus(fContainerFieldStatus);
	}

	protected void checkIfPathValid() {
		fOutputLocation= null;
		fContainerFieldStatus= StatusInfo.OK_STATUS;

		if (fUseDefault.isSelected()) {
			return;
		}

		String pathStr= fContainerDialogField.getText();
		if (pathStr.length() == 0) {
			fContainerFieldStatus= new StatusInfo(IStatus.ERROR, ""); //$NON-NLS-1$
			return;
		}

		IPath projectPath= fCPJavaProject.getJavaProject().getProject().getFullPath();
		IPath outputPath= projectPath.append(pathStr);

		try {
	        fContainerFieldStatus= ClasspathModifier.checkSetOutputLocationPrecondition(fEntryToEdit, outputPath, fAllowInvalidClasspath, fCPJavaProject);
	        if (fContainerFieldStatus.getSeverity() != IStatus.ERROR) {
	        	fOutputLocation= outputPath;
	        }
        } catch (CoreException e) {
	        JavaPlugin.log(e);
        }
	}

	public IPath getOutputLocation() {
		return fOutputLocation;
	}

	/*
	 * @see org.eclipse.jface.window.Window#configureShell(Shell)
	 */
	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell, IJavaHelpContextIds.OUTPUT_LOCATION_DIALOG);
	}

	// ---------- util method ------------

	private IContainer chooseOutputLocation() {
		IWorkspaceRoot root= fCurrProject.getWorkspace().getRoot();
		final Class<?>[] acceptedClasses= new Class[] { IProject.class, IFolder.class };
		IProject[] allProjects= root.getProjects();
		ArrayList<IProject> rejectedElements= new ArrayList<IProject>(allProjects.length);
		for (int i= 0; i < allProjects.length; i++) {
			if (!allProjects[i].equals(fCurrProject)) {
				rejectedElements.add(allProjects[i]);
			}
		}
		ViewerFilter filter= new TypedViewerFilter(acceptedClasses, rejectedElements.toArray());

		ILabelProvider lp= new WorkbenchLabelProvider();
		ITreeContentProvider cp= new WorkbenchContentProvider();

		IResource initSelection= null;
		if (fOutputLocation != null) {
			initSelection= root.findMember(fOutputLocation);
		}

		FolderSelectionDialog dialog= new FolderSelectionDialog(getShell(), lp, cp);
		dialog.setTitle(NewWizardMessages.OutputLocationDialog_ChooseOutputFolder_title);

        dialog.setValidator(new ISelectionStatusValidator() {
            ISelectionStatusValidator validator= new TypedElementSelectionValidator(acceptedClasses, false);
            public IStatus validate(Object[] selection) {
                IStatus typedStatus= validator.validate(selection);
                if (!typedStatus.isOK())
                    return typedStatus;
                if (selection[0] instanceof IFolder) {
                    IFolder folder= (IFolder) selection[0];
                    try {
                    	IStatus result= ClasspathModifier.checkSetOutputLocationPrecondition(fEntryToEdit, folder.getFullPath(), fAllowInvalidClasspath, fCPJavaProject);
                    	if (result.getSeverity() == IStatus.ERROR)
	                    	return result;
                    } catch (CoreException e) {
	                    JavaPlugin.log(e);
                    }
                    return new StatusInfo();
                } else {
                	return new StatusInfo(IStatus.ERROR, ""); //$NON-NLS-1$
                }
            }
        });
		dialog.setMessage(NewWizardMessages.OutputLocationDialog_ChooseOutputFolder_description);
		dialog.addFilter(filter);
		dialog.setInput(root);
		dialog.setInitialSelection(initSelection);
		dialog.setComparator(new ResourceComparator(ResourceComparator.NAME));

		if (dialog.open() == Window.OK) {
			return (IContainer)dialog.getFirstResult();
		}
		return null;
	}
}

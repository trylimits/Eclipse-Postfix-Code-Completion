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
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDEEncoding;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ComboDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;


/**
 * UI to set the source attachment archive and root.
 * Same implementation for both setting attachments for libraries from
 * variable entries and for normal (internal or external) jar.
 */
public class SourceAttachmentBlock {

	private final IStatusChangeListener fContext;

	private StringButtonDialogField fWorkspaceFileNameField;
	private StringButtonDialogField fExternalFileNameField;
	private StringButtonDialogField fVariableFileNameField;
	private SelectionButtonDialogField fExternalFolderButton;
	private SelectionButtonDialogField fExternalRadio, fWorkspaceRadio;
	private ComboDialogField fEncodingCombo;

	private IStatus fWorkspaceNameStatus;
	private IStatus fExternalNameStatus;
	private IStatus fVariableNameStatus;

	/**
	 * The path to which the archive variable points.
	 * Null if invalid path or not resolvable. Must not exist.
	 */
	private IPath fFileVariablePath;

	private final IWorkspaceRoot fWorkspaceRoot;

	private Control fSWTWidget;
	private Label fFullPathResolvedLabel;

	private IJavaProject fProject;
	private final IClasspathEntry fEntry;
	private final boolean fCanEditEncoding;
	private String fDefaultEncodingName;

	/**
	 * @param context listeners for status updates
	 * @param entry The entry to edit
	 */
	public SourceAttachmentBlock(IStatusChangeListener context, IClasspathEntry entry) {
		this(context, entry, false);
	}

	/**
	 * @param context listeners for status updates
	 * @param entry The entry to edit
	 * @param canEditEncoding whether the source attachment encoding can be edited
	 */
	public SourceAttachmentBlock(IStatusChangeListener context, IClasspathEntry entry, boolean canEditEncoding) {
		Assert.isNotNull(entry);

		fContext= context;
		fEntry= entry;
		fCanEditEncoding= canEditEncoding;

		try {
			String defaultEncoding = ResourcesPlugin.getWorkspace().getRoot().getDefaultCharset();
			fDefaultEncodingName= Messages.format(NewWizardMessages.SourceAttachmentBlock_encoding_default, defaultEncoding);
		} catch (CoreException e) {
			//do nothing
		}
		
		int kind= entry.getEntryKind();
		Assert.isTrue(kind == IClasspathEntry.CPE_LIBRARY || kind == IClasspathEntry.CPE_VARIABLE);

		fWorkspaceRoot= ResourcesPlugin.getWorkspace().getRoot();

		fWorkspaceNameStatus= new StatusInfo();
		fExternalNameStatus= new StatusInfo();
		fVariableNameStatus= new StatusInfo();

		SourceAttachmentAdapter adapter= new SourceAttachmentAdapter();

		// create the dialog fields (no widgets yet)
		if (isVariableEntry()) {
			fVariableFileNameField= new VariablePathDialogField(adapter);
			fVariableFileNameField.setDialogFieldListener(adapter);
			fVariableFileNameField.setLabelText(NewWizardMessages.SourceAttachmentBlock_filename_varlabel);
			fVariableFileNameField.setButtonLabel(NewWizardMessages.SourceAttachmentBlock_filename_external_varbutton);
			((VariablePathDialogField) fVariableFileNameField).setVariableButtonLabel(NewWizardMessages.SourceAttachmentBlock_filename_variable_button);
		} else {
			fWorkspaceRadio= new SelectionButtonDialogField(SWT.RADIO);
			fWorkspaceRadio.setDialogFieldListener(adapter);
			fWorkspaceRadio.setLabelText(NewWizardMessages.SourceAttachmentBlock_workspace_radiolabel);

			fWorkspaceFileNameField= new StringButtonDialogField(adapter);
			fWorkspaceFileNameField.setDialogFieldListener(adapter);
			fWorkspaceFileNameField.setLabelText(NewWizardMessages.SourceAttachmentBlock_filename_workspace_label);
			fWorkspaceFileNameField.setButtonLabel(NewWizardMessages.SourceAttachmentBlock_filename_workspace_browse);

			fExternalRadio= new SelectionButtonDialogField(SWT.RADIO);
			fExternalRadio.setDialogFieldListener(adapter);
			fExternalRadio.setLabelText(NewWizardMessages.SourceAttachmentBlock_external_radiolabel);

			fExternalFileNameField= new StringButtonDialogField(adapter);
			fExternalFileNameField.setDialogFieldListener(adapter);
			fExternalFileNameField.setLabelText(NewWizardMessages.SourceAttachmentBlock_filename_external_label);
			fExternalFileNameField.setButtonLabel(NewWizardMessages.SourceAttachmentBlock_filename_externalfile_button);

			fExternalFolderButton= new SelectionButtonDialogField(SWT.PUSH);
			fExternalFolderButton.setDialogFieldListener(adapter);
			fExternalFolderButton.setLabelText(NewWizardMessages.SourceAttachmentBlock_filename_externalfolder_button);
		}

		fEncodingCombo= new ComboDialogField(SWT.DROP_DOWN);
		fEncodingCombo.setDialogFieldListener(adapter);
		fEncodingCombo.setLabelText(NewWizardMessages.SourceAttachmentBlock_encoding_label);
		List<String> encodings= IDEEncoding.getIDEEncodings();
		String[] encodingsArray= encodings.toArray(new String[encodings.size() + 1]);
		System.arraycopy(encodingsArray, 0, encodingsArray, 1, encodingsArray.length - 1);
		encodingsArray[0]= fDefaultEncodingName;
		fEncodingCombo.setItems(encodingsArray);

		// set the old settings
		setDefaults();
	}

	public void setDefaults() {
		IPath sourceAttachmentPath= fEntry.getSourceAttachmentPath();
		String encoding= getSourceAttachmentEncoding(fEntry);
		String path= (sourceAttachmentPath == null) ? "" : sourceAttachmentPath.toString(); //$NON-NLS-1$

		if (isVariableEntry()) {
			fVariableFileNameField.setText(path);
		} else {
			if (isWorkspacePath(sourceAttachmentPath)) {
				fWorkspaceRadio.setSelection(true);
				fWorkspaceFileNameField.setText(path);
			} else if (path.length() != 0 || encoding != null) {
				fExternalRadio.setSelection(true);
				fExternalFileNameField.setText(path);
			} else {
				fWorkspaceRadio.setSelection(true);
				fExternalRadio.setSelection(false);
			}
		}
		if (encoding == null) {
			encoding= fDefaultEncodingName;
		}
		fEncodingCombo.setText(encoding);
	}

	private boolean isWorkspacePath(IPath path) {
		if (path == null || path.getDevice() != null)
			return false;
		IWorkspace workspace= ResourcesPlugin.getWorkspace();
		if (workspace == null)
			return false;
		return workspace.getRoot().findMember(path) != null;
	}

	private boolean isVariableEntry() {
		return fEntry.getEntryKind() == IClasspathEntry.CPE_VARIABLE;
	}

	/**
	 * Gets the source attachment path chosen by the user
	 * @return the source attachment path
	 */
	public IPath getSourceAttachmentPath() {
		return (getFilePath().segmentCount() == 0) ? null : getFilePath();
	}

	/**
	 * Gets the source attachment root chosen by the user
	 * Returns null to let JCore automatically detect the root.
	 * @return the source attachment root path
	 */
	public IPath getSourceAttachmentRootPath() {
		return null;
	}

	private String getEncoding() {
		if (isVariableEntry() || (fExternalRadio != null && fExternalRadio.isSelected())) {
			String encoding= fEncodingCombo.getText();
			if (encoding.length() != 0 && !encoding.equals(fDefaultEncodingName))
				return encoding;
		}
		return null;
	}

	public IClasspathEntry getNewEntry() {
		CPListElement elem= CPListElement.createFromExisting(fEntry, fProject);
		IPath sourceAttachmentPath= getSourceAttachmentPath();
		String encoding= getEncoding();

		elem.setAttribute(CPListElement.SOURCEATTACHMENT, sourceAttachmentPath);
		elem.setAttribute(CPListElement.SOURCE_ATTACHMENT_ENCODING, encoding);
		return elem.getClasspathEntry();
	}

	/**
	 * Creates the control
	 * @param parent the parent
	 * @return the created control
	 */
	public Control createControl(Composite parent) {
		PixelConverter converter= new PixelConverter(parent);

		fSWTWidget= parent;

		Composite composite= new Composite(parent, SWT.NONE);

		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.numColumns= 4;
		composite.setLayout(layout);


		if (isVariableEntry()) {
			int widthHint= converter.convertWidthInCharsToPixels(40);
			int labelWidthHint= widthHint * 2;

			Label message= new Label(composite, SWT.WRAP);
			GridData gd= new GridData(GridData.FILL, GridData.BEGINNING, false, false, 4, 1);
			message.setLayoutData(gd);
			message.setText(Messages.format(NewWizardMessages.SourceAttachmentBlock_message, BasicElementLabels.getResourceName(fEntry.getPath().lastSegment())));

			//DialogField.createEmptySpace(composite, 1);

			Label desc= new Label(composite, SWT.WRAP);
			gd= new GridData(GridData.FILL, GridData.BEGINNING, false, false, 4, 1);
			gd.widthHint= labelWidthHint;
			desc.setLayoutData(gd);
			desc.setText(NewWizardMessages.SourceAttachmentBlock_filename_description);

			fVariableFileNameField.doFillIntoGrid(composite, 4);
			LayoutUtil.setWidthHint(fVariableFileNameField.getTextControl(null), widthHint);

			// label that shows the resolved path for variable jars
			//DialogField.createEmptySpace(composite, 1);
			fFullPathResolvedLabel= new Label(composite, SWT.WRAP);
			fFullPathResolvedLabel.setText(getResolvedLabelString());
			gd= new GridData(GridData.FILL, GridData.BEGINNING, false, false, 4, 1);
			gd.widthHint= labelWidthHint;
			fFullPathResolvedLabel.setLayoutData(gd);

			DialogField.createEmptySpace(composite, 4);

			fEncodingCombo.doFillIntoGrid(composite, 2);
		} else {
			int widthHint= converter.convertWidthInCharsToPixels(60);

			GridData gd= new GridData(GridData.FILL, GridData.BEGINNING, false, false, 3, 1);
			gd.widthHint= converter.convertWidthInCharsToPixels(50);

			Label message= new Label(composite, SWT.LEFT + SWT.WRAP);
			message.setLayoutData(gd);
			message.setText(Messages.format(NewWizardMessages.SourceAttachmentBlock_message, BasicElementLabels.getResourceName(fEntry.getPath().lastSegment())));

			fWorkspaceRadio.doFillIntoGrid(composite, 4);

			fWorkspaceFileNameField.doFillIntoGrid(composite, 4);
			LayoutUtil.setWidthHint(fWorkspaceFileNameField.getTextControl(null), widthHint);
			LayoutUtil.setHorizontalGrabbing(fWorkspaceFileNameField.getTextControl(null));

			DialogField.createEmptySpace(composite, 4);

			fExternalRadio.doFillIntoGrid(composite, 4);
			fExternalFileNameField.doFillIntoGrid(composite, 4);
			LayoutUtil.setWidthHint(fExternalFileNameField.getTextControl(null), widthHint);
			LayoutUtil.setHorizontalGrabbing(fExternalFileNameField.getTextControl(null));

			fEncodingCombo.doFillIntoGrid(composite, 2);
			DialogField.createEmptySpace(composite, 1);

			fExternalFolderButton.doFillIntoGrid(composite, 1);

			LayoutUtil.setHorizontalIndent(fWorkspaceFileNameField.getLabelControl(null));
			LayoutUtil.setHorizontalIndent(fExternalFileNameField.getLabelControl(null));
			LayoutUtil.setHorizontalIndent(fEncodingCombo.getLabelControl(null));

			fWorkspaceRadio.attachDialogField(fWorkspaceFileNameField);
			if (fCanEditEncoding) {
				fExternalRadio.attachDialogFields(new DialogField[] { fExternalFileNameField, fExternalFolderButton, fEncodingCombo });
			} else {
				fEncodingCombo.setEnabled(false);
				fExternalRadio.attachDialogFields(new DialogField[] { fExternalFileNameField, fExternalFolderButton });
			}
		}

		Dialog.applyDialogFont(composite);

		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IJavaHelpContextIds.SOURCE_ATTACHMENT_BLOCK);
		return composite;
	}


	private class SourceAttachmentAdapter implements IStringButtonAdapter, IDialogFieldListener {

		// -------- IStringButtonAdapter --------
		public void changeControlPressed(DialogField field) {
			attachmentChangeControlPressed(field);
		}

		// ---------- IDialogFieldListener --------
		public void dialogFieldChanged(DialogField field) {
			attachmentDialogFieldChanged(field);
		}
	}

	private void attachmentChangeControlPressed(DialogField field) {
		if (field == fWorkspaceFileNameField) {
			IPath jarFilePath= chooseInternal();
			if (jarFilePath != null) {
				fWorkspaceFileNameField.setText(jarFilePath.toString());
			}
		} else if (field == fExternalFileNameField) {
			IPath jarFilePath= chooseExtJarFile();
			if (jarFilePath != null) {
				fExternalFileNameField.setText(jarFilePath.toString());
			}
		} else if (field == fVariableFileNameField) {
			IPath jarFilePath= chooseExtension();
			if (jarFilePath != null) {
				fVariableFileNameField.setText(jarFilePath.toString());
			}
		}
	}

	// ---------- IDialogFieldListener --------

	private void attachmentDialogFieldChanged(DialogField field) {
		if (field == fVariableFileNameField) {
			fVariableNameStatus= updateFileNameStatus(fVariableFileNameField);
		} else if (field == fWorkspaceFileNameField) {
			fWorkspaceNameStatus= updateFileNameStatus(fWorkspaceFileNameField);
		} else if (field == fExternalFileNameField) {
			fExternalNameStatus= updateFileNameStatus(fExternalFileNameField);
		} else if (field == fExternalFolderButton) {
			IPath folderPath= chooseExtFolder();
			if (folderPath != null) {
				fExternalFileNameField.setText(folderPath.toString());
			}
			return;
		}
		doStatusLineUpdate();
	}

	private void doStatusLineUpdate() {
		IStatus status;
		if (isVariableEntry()) {
			// set the resolved path for variable jars
			if (fFullPathResolvedLabel != null) {
				fFullPathResolvedLabel.setText(getResolvedLabelString());
			}
			fVariableFileNameField.enableButton(canBrowseFileName());
			status= fVariableNameStatus;
		} else {
			boolean isWorkSpace= fWorkspaceRadio.isSelected();
			if (isWorkSpace) {
				fWorkspaceFileNameField.enableButton(canBrowseFileName());
				status= fWorkspaceNameStatus;
			} else {
				fExternalFileNameField.enableButton(canBrowseFileName());
				status= fExternalNameStatus;
			}
		}
		fContext.statusChanged(status);
	}

	private boolean canBrowseFileName() {
		if (!isVariableEntry()) {
			return true;
		}
		// to browse with a variable JAR, the variable name must point to a directory
		if (fFileVariablePath != null) {
			return fFileVariablePath.toFile().isDirectory();
		}
		return false;
	}

	private String getResolvedLabelString() {
		IPath resolvedPath= getResolvedPath(getFilePath());
		if (resolvedPath != null) {
			return BasicElementLabels.getPathLabel(resolvedPath, true);
		}
		return ""; //$NON-NLS-1$
	}

	private IPath getResolvedPath(IPath path) {
		if (path != null) {
			String varName= path.segment(0);
			if (varName != null) {
				IPath varPath= JavaCore.getClasspathVariable(varName);
				if (varPath != null) {
					return varPath.append(path.removeFirstSegments(1));
				}
			}
		}
		return null;
	}

	private IStatus updateFileNameStatus(StringButtonDialogField field) {
		StatusInfo status= new StatusInfo();
		fFileVariablePath= null;

		String fileName= field.getText();
		if (fileName.length() == 0) {
			// no source attachment
			return status;
		} else {
			if (!Path.EMPTY.isValidPath(fileName)) {
				status.setError(NewWizardMessages.SourceAttachmentBlock_filename_error_notvalid);
				return status;
			}
			IPath filePath= Path.fromOSString(fileName);
			IPath resolvedPath;
			if (isVariableEntry()) {
				if (filePath.getDevice() != null) {
					status.setError(NewWizardMessages.SourceAttachmentBlock_filename_error_deviceinpath);
					return status;
				}
				String varName= filePath.segment(0);
				if (varName == null) {
					status.setError(NewWizardMessages.SourceAttachmentBlock_filename_error_notvalid);
					return status;
				}
				fFileVariablePath= JavaCore.getClasspathVariable(varName);
				if (fFileVariablePath == null) {
					status.setError(NewWizardMessages.SourceAttachmentBlock_filename_error_varnotexists);
					return status;
				}
				resolvedPath= fFileVariablePath.append(filePath.removeFirstSegments(1));

				if (resolvedPath.isEmpty()) {
					status.setWarning(NewWizardMessages.SourceAttachmentBlock_filename_warning_varempty);
					return status;
				}
				File file= resolvedPath.toFile();
				if (!file.exists()) {
					String message= Messages.format(NewWizardMessages.SourceAttachmentBlock_filename_error_filenotexists, BasicElementLabels.getPathLabel(resolvedPath, true));
					status.setWarning(message);
					return status;
				}
				if (!resolvedPath.isAbsolute()) {
					String message=  Messages.format(NewWizardMessages.SourceAttachmentBlock_filename_error_notabsolute, BasicElementLabels.getPathLabel(filePath, false));
					status.setError(message);
					return status;
				}

				String deprecationMessage= BuildPathSupport.getDeprecationMessage(varName);
				if (deprecationMessage != null) {
					status.setWarning(deprecationMessage);
					return status;
				}

			} else {
				// JDT/Core only supports source attachments on the
				// local file system. So using getLocation is save here.
				File file= filePath.toFile();
				IResource res= fWorkspaceRoot.findMember(filePath);
				boolean exists;
				if (res != null) {
					IPath location= res.getLocation();
					if (location != null) {
						exists= location.toFile().exists();
					} else {
						exists= res.exists();
					}
				} else {
					exists= file.exists();
				}
				if (!exists) {
					String message=  Messages.format(NewWizardMessages.SourceAttachmentBlock_filename_error_filenotexists, BasicElementLabels.getPathLabel(filePath, false));
					status.setError(message);
					return status;
				}
				if (!filePath.isAbsolute()) {
					String message=  Messages.format(NewWizardMessages.SourceAttachmentBlock_filename_error_notabsolute, BasicElementLabels.getPathLabel(filePath, false));
					status.setError(message);
					return status;
				}
			}

		}
		return status;
	}

	private IPath getFilePath() {
		String filePath= isVariableEntry() ? fVariableFileNameField.getText() : (fWorkspaceRadio.isSelected() ? fWorkspaceFileNameField.getText() : fExternalFileNameField.getText());
		return Path.fromOSString(filePath).makeAbsolute();
	}

	private IPath chooseExtension() {
		IPath currPath= getFilePath();
		if (currPath.segmentCount() == 0) {
			currPath= fEntry.getPath();
		}

		IPath resolvedPath= getResolvedPath(currPath);
		File initialSelection= resolvedPath != null ? resolvedPath.toFile() : null;

		String currVariable= currPath.segment(0);
		JARFileSelectionDialog dialog= new JARFileSelectionDialog(getShell(), false, true, false);
		dialog.setTitle(NewWizardMessages.SourceAttachmentBlock_extvardialog_title);
		dialog.setMessage(NewWizardMessages.SourceAttachmentBlock_extvardialog_description);
		dialog.setInput(fFileVariablePath.toFile());
		dialog.setInitialSelection(initialSelection);
		if (dialog.open() == Window.OK) {
			File result= (File) dialog.getResult()[0];
			IPath returnPath= Path.fromOSString(result.getPath()).makeAbsolute();
			return modifyPath(returnPath, currVariable);
		}
		return null;
	}

	/*
	 * Opens a dialog to choose a jar from the file system.
	 */
	private IPath chooseExtJarFile() {
		IPath currPath= getFilePath();
		if (currPath.segmentCount() == 0) {
			currPath= fEntry.getPath();
		}

		if (ArchiveFileFilter.isArchivePath(currPath, true)) {
			currPath= currPath.removeLastSegments(1);
		}

		FileDialog dialog= new FileDialog(getShell());
		dialog.setText(NewWizardMessages.SourceAttachmentBlock_extjardialog_text);
		dialog.setFilterExtensions(ArchiveFileFilter.JAR_ZIP_FILTER_EXTENSIONS);
		dialog.setFilterPath(currPath.toOSString());
		String res= dialog.open();
		if (res != null) {
			return Path.fromOSString(res).makeAbsolute();
		}
		return null;
	}

	private IPath chooseExtFolder() {
		IPath currPath= getFilePath();
		if (currPath.segmentCount() == 0) {
			currPath= fEntry.getPath();
		}
		if (ArchiveFileFilter.isArchivePath(currPath, true)) {
			currPath= currPath.removeLastSegments(1);
		}

		DirectoryDialog dialog= new DirectoryDialog(getShell());
		dialog.setMessage(NewWizardMessages.SourceAttachmentBlock_extfolderdialog_message);
		dialog.setText(NewWizardMessages.SourceAttachmentBlock_extfolderdialog_text);
		dialog.setFilterPath(currPath.toOSString());
		String res= dialog.open();
		if (res != null) {
			return Path.fromOSString(res).makeAbsolute();
		}
		return null;
	}

	/*
	 * Opens a dialog to choose an internal jar.
	 */
	private IPath chooseInternal() {
		String initSelection= fWorkspaceFileNameField.getText();

		ViewerFilter filter= new ArchiveFileFilter((List<IResource>) null, false, false);

		ILabelProvider lp= new WorkbenchLabelProvider();
		ITreeContentProvider cp= new WorkbenchContentProvider();

		IResource initSel= null;
		if (initSelection.length() > 0) {
			initSel= fWorkspaceRoot.findMember(new Path(initSelection));
		}
		if (initSel == null) {
			initSel= fWorkspaceRoot.findMember(fEntry.getPath());
		}

		FolderSelectionDialog dialog= new FolderSelectionDialog(getShell(), lp, cp);
		dialog.setAllowMultiple(false);
		dialog.addFilter(filter);
		dialog.setTitle(NewWizardMessages.SourceAttachmentBlock_intjardialog_title);
		dialog.setMessage(NewWizardMessages.SourceAttachmentBlock_intjardialog_message);
		dialog.setInput(fWorkspaceRoot);
		dialog.setInitialSelection(initSel);
		if (dialog.open() == Window.OK) {
			IResource res= (IResource) dialog.getFirstResult();
			return res.getFullPath();
		}
		return null;
	}

	private Shell getShell() {
		if (fSWTWidget != null) {
			return fSWTWidget.getShell();
		}
		return JavaPlugin.getActiveWorkbenchShell();
	}

	/**
	 * Takes a path and replaces the beginning with a variable name
	 * (if the beginning matches with the variables value)
	 * @param path the path
	 * @param varName the variable
	 * @return the modified path
	 */
	private IPath modifyPath(IPath path, String varName) {
		if (varName == null || path == null) {
			return null;
		}
		if (path.isEmpty()) {
			return new Path(varName);
		}

		IPath varPath= JavaCore.getClasspathVariable(varName);
		if (varPath != null) {
			if (varPath.isPrefixOf(path)) {
				path= path.removeFirstSegments(varPath.segmentCount());
			} else {
				path= new Path(path.lastSegment());
			}
		} else {
			path= new Path(path.lastSegment());
		}
		return new Path(varName).append(path);
	}


	/**
	 * Creates a runnable that sets the source attachment by modifying the project's classpath.
	 * @param shell the shell
	 * @param newEntry the new entry
	 * @param jproject the Java project
	 * @param containerPath the path of the parent container or <code>null</code> if the element is not in a container
	 * @param isReferencedEntry <code>true</code> iff the entry has a {@link IClasspathEntry#getReferencingEntry() referencing entry}
	 * @return return the runnable
	 */
	public static IRunnableWithProgress getRunnable(final Shell shell, final IClasspathEntry newEntry, final IJavaProject jproject, final IPath containerPath, final boolean isReferencedEntry) {
		return new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				try {
					String[] changedAttributes= { CPListElement.SOURCEATTACHMENT, CPListElement.SOURCE_ATTACHMENT_ENCODING };
					BuildPathSupport.modifyClasspathEntry(shell, newEntry, changedAttributes, jproject, containerPath, isReferencedEntry, monitor);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				}
			}
		};
	}

	public static String getSourceAttachmentEncoding(IClasspathEntry entry) {
		if (entry == null) {
			throw new IllegalArgumentException("Entry must not be null"); //$NON-NLS-1$
		}

		int kind= entry.getEntryKind();
		if (kind != IClasspathEntry.CPE_LIBRARY && kind != IClasspathEntry.CPE_VARIABLE) {
			throw new IllegalArgumentException("Entry must be of kind CPE_LIBRARY or CPE_VARIABLE"); //$NON-NLS-1$
		}

		IClasspathAttribute[] extraAttributes= entry.getExtraAttributes();
		for (int i= 0; i < extraAttributes.length; i++) {
			IClasspathAttribute attrib= extraAttributes[i];
			if (IClasspathAttribute.SOURCE_ATTACHMENT_ENCODING.equals(attrib.getName())) {
				return attrib.getValue();
			}
		}
		return null;
	}
}

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
package org.eclipse.jdt.internal.ui.preferences;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import org.eclipse.equinox.bidi.StructuredTextTypeHandlerFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.util.BidiUtils;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceComparator;
import org.eclipse.ui.wizards.datatransfer.ZipFileStructureProvider;

import org.eclipse.jdt.internal.corext.javadoc.JavaDocLocations;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.OpenBrowserUtil;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.FilteredElementTreeSelectionDialog;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.ArchiveFileFilter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringDialogField;


public class JavadocConfigurationBlock {

	private StringDialogField fURLField;
	private StringDialogField fArchiveField;
	private StringDialogField fArchivePathField;
	private URL fInitialURL;
	private SelectionButtonDialogField fValidateURLButton;
	private SelectionButtonDialogField fValidateArchiveButton;
	private SelectionButtonDialogField fBrowseFolder;
	private SelectionButtonDialogField fURLRadioButton;
	private SelectionButtonDialogField fArchiveRadioButton;
	private SelectionButtonDialogField fBrowseArchive;
	private SelectionButtonDialogField fExternalRadio, fWorkspaceRadio;
	private SelectionButtonDialogField fBrowseArchivePath;
	private Shell fShell;
	private IStatusChangeListener fContext;

	private IStatus fURLStatus;
	private IStatus fArchiveStatus;
	private IStatus fArchivePathStatus;

	private URL fURLResult;
	private URL fArchiveURLResult;

	boolean fIsForSource;


	public JavadocConfigurationBlock(Shell shell,  IStatusChangeListener context, URL initURL, boolean forSource) {
		fShell= shell;
		fContext= context;
		fInitialURL= initURL;
		fIsForSource= forSource;

		JDocConfigurationAdapter adapter= new JDocConfigurationAdapter();

		if (!forSource) {
			fURLRadioButton= new SelectionButtonDialogField(SWT.RADIO);
			fURLRadioButton.setDialogFieldListener(adapter);
			fURLRadioButton.setLabelText(PreferencesMessages.JavadocConfigurationBlock_location_type_path_label);
		}

		fURLField= new StringDialogField();
		fURLField.setDialogFieldListener(adapter);
		fURLField.setLabelText(PreferencesMessages.JavadocConfigurationBlock_location_path_label);

		fBrowseFolder= new SelectionButtonDialogField(SWT.PUSH);
		fBrowseFolder.setDialogFieldListener(adapter);
		fBrowseFolder.setLabelText(PreferencesMessages.JavadocConfigurationBlock_browse_folder_button);

		fValidateURLButton= new SelectionButtonDialogField(SWT.PUSH);
		fValidateURLButton.setDialogFieldListener(adapter);
		fValidateURLButton.setLabelText(PreferencesMessages.JavadocConfigurationBlock_validate_button);

		if (!forSource) {
			fArchiveRadioButton= new SelectionButtonDialogField(SWT.RADIO);
			fArchiveRadioButton.setDialogFieldListener(adapter);
			fArchiveRadioButton.setLabelText(PreferencesMessages.JavadocConfigurationBlock_location_type_jar_label);

			fExternalRadio= new SelectionButtonDialogField(SWT.RADIO);
			fExternalRadio.setDialogFieldListener(adapter);
			fExternalRadio.setLabelText(PreferencesMessages.JavadocConfigurationBlock_external_radio);

			fWorkspaceRadio= new SelectionButtonDialogField(SWT.RADIO);
			fWorkspaceRadio.setDialogFieldListener(adapter);
			fWorkspaceRadio.setLabelText(PreferencesMessages.JavadocConfigurationBlock_workspace_radio);

			fArchiveField= new StringDialogField();
			fArchiveField.setDialogFieldListener(adapter);
			fArchiveField.setLabelText(PreferencesMessages.JavadocConfigurationBlock_location_jar_label);

			fBrowseArchive= new SelectionButtonDialogField(SWT.PUSH);
			fBrowseArchive.setDialogFieldListener(adapter);
			fBrowseArchive.setLabelText(PreferencesMessages.JavadocConfigurationBlock_browse_archive_button);

			fArchivePathField= new StringDialogField();
			fArchivePathField.setDialogFieldListener(adapter);
			fArchivePathField.setLabelText(PreferencesMessages.JavadocConfigurationBlock_jar_path_label);

			fBrowseArchivePath= new SelectionButtonDialogField(SWT.PUSH);
			fBrowseArchivePath.setDialogFieldListener(adapter);
			fBrowseArchivePath.setLabelText(PreferencesMessages.JavadocConfigurationBlock_browse_archive_path_button);

			fValidateArchiveButton= new SelectionButtonDialogField(SWT.PUSH);
			fValidateArchiveButton.setDialogFieldListener(adapter);
			fValidateArchiveButton.setLabelText(PreferencesMessages.JavadocConfigurationBlock_validate_button);
		}

		fURLStatus= new StatusInfo();
		fArchiveStatus= new StatusInfo();
		fArchivePathStatus= new StatusInfo();

		initializeSelections();
	}

	public Control createContents(Composite parent) {
		fShell= parent.getShell();

		PixelConverter converter= new PixelConverter(parent);
		Composite topComp= new Composite(parent, SWT.NONE);
		GridLayout topLayout= new GridLayout();
		topLayout.numColumns= 3;
		topLayout.marginWidth= 0;
		topLayout.marginHeight= 0;
		topComp.setLayout(topLayout);

		// Add the first radio button for the path
		if (!fIsForSource) {
			fURLRadioButton.doFillIntoGrid(topComp, 3);
		}

		fURLField.doFillIntoGrid(topComp, 2);
		LayoutUtil.setWidthHint(fURLField.getTextControl(null), converter.convertWidthInCharsToPixels(43));
		LayoutUtil.setHorizontalGrabbing(fURLField.getTextControl(null));
		BidiUtils.applyBidiProcessing(fURLField.getTextControl(null), StructuredTextTypeHandlerFactory.URL);

		fBrowseFolder.doFillIntoGrid(topComp, 1);

		DialogField.createEmptySpace(topComp, 2);
		fValidateURLButton.doFillIntoGrid(topComp, 1);

		//DialogField.createEmptySpace(topComp, 3);

		if (!fIsForSource) {
			// Add the second radio button for the jar/zip
			fArchiveRadioButton.doFillIntoGrid(topComp, 3);


			// external - workspace selection
			DialogField.createEmptySpace(topComp, 1);
			Composite radioComposite= new Composite(topComp, SWT.NONE);
			radioComposite.setLayoutData(new GridData(GridData.FILL, GridData.FILL, false, false));
			GridLayout layout= new GridLayout(2, true);
			layout.marginHeight= 0;
			layout.marginWidth= 0;
			radioComposite.setLayout(layout);
			fExternalRadio.doFillIntoGrid(radioComposite, 1);
			fWorkspaceRadio.doFillIntoGrid(radioComposite, 1);
			DialogField.createEmptySpace(topComp, 1);

			// Add the jar/zip field
			fArchiveField.doFillIntoGrid(topComp, 2);
			LayoutUtil.setWidthHint(fArchiveField.getTextControl(null), converter.convertWidthInCharsToPixels(43));
			LayoutUtil.setHorizontalGrabbing(fArchiveField.getTextControl(null));
			BidiUtils.applyBidiProcessing(fArchiveField.getTextControl(null), StructuredTextTypeHandlerFactory.FILE);

			fBrowseArchive.doFillIntoGrid(topComp, 1);

			// Add the path chooser for the jar/zip
			fArchivePathField.doFillIntoGrid(topComp, 2);
			LayoutUtil.setWidthHint(fArchivePathField.getTextControl(null), converter.convertWidthInCharsToPixels(43));
			LayoutUtil.setHorizontalGrabbing(fArchivePathField.getTextControl(null));
			BidiUtils.applyBidiProcessing(fArchivePathField.getTextControl(null), StructuredTextTypeHandlerFactory.FILE);

			fBrowseArchivePath.doFillIntoGrid(topComp, 1);

			DialogField.createEmptySpace(topComp, 2);
			fValidateArchiveButton.doFillIntoGrid(topComp, 1);

			LayoutUtil.setHorizontalIndent(fArchiveField.getLabelControl(null));
			LayoutUtil.setHorizontalIndent(fArchivePathField.getLabelControl(null));
			LayoutUtil.setHorizontalIndent(fURLField.getLabelControl(null));

			fURLRadioButton.attachDialogFields(new DialogField[] {fURLField,  fBrowseFolder, fValidateURLButton });
			fArchiveRadioButton.attachDialogFields(new DialogField[] {fArchiveField,  fBrowseArchive, fExternalRadio, fWorkspaceRadio, fArchivePathField, fBrowseArchivePath, fValidateArchiveButton });
		}


		return topComp;
	}

	private void initializeSelections() {
		String initialValue = fInitialURL != null ? fInitialURL.toExternalForm() : ""; //$NON-NLS-1$

		if (fIsForSource) {
			fURLField.setText(initialValue);
			return;
		}
		String prefix= JavaDocLocations.ARCHIVE_PREFIX;
		boolean isArchive= initialValue.startsWith(prefix);

		boolean isWorkspaceArchive= false;

		fURLRadioButton.setSelection(!isArchive);
		fArchiveRadioButton.setSelection(isArchive);

		if (isArchive) {
			String jarPathStr;
			String insidePathStr= ""; //$NON-NLS-1$
			int excIndex= initialValue.indexOf("!/"); //$NON-NLS-1$
			if (excIndex == -1) {
				jarPathStr= initialValue.substring(prefix.length());
			} else {
				jarPathStr= initialValue.substring(prefix.length(), excIndex);
				insidePathStr= initialValue.substring(excIndex + 2);
			}

			final String fileProtocol= "file:/"; //$NON-NLS-1$
			final String resourceProtocol= "platform:/resource/"; //$NON-NLS-1$

			if (jarPathStr.startsWith(fileProtocol)) {
				jarPathStr= jarPathStr.substring(fileProtocol.length());
			} else if (jarPathStr.startsWith(resourceProtocol)) {
				jarPathStr= jarPathStr.substring(resourceProtocol.length());
				isWorkspaceArchive= true;
			} else {
				fURLField.setText(initialValue);
				return;
			}
			try {
				URI jarPathUri= new URI(jarPathStr);
				IPath jarPath= new Path(jarPathUri.getSchemeSpecificPart());
				URI insidePathUri= new URI(insidePathStr);
				String insidePath= insidePathUri.getSchemeSpecificPart();
				
				fArchivePathField.setText(insidePath);
				if (isWorkspaceArchive) {
					fArchiveField.setText(jarPath.makeRelative().toString());
				} else {
					fArchiveField.setText(jarPath.makeAbsolute().toOSString());
				}
			} catch (URISyntaxException e) {
				JavaPlugin.log(e);
				fURLField.setText(initialValue);
				return;
			}
		} else {
			fURLField.setText(initialValue);
		}
		fExternalRadio.setSelection(!isWorkspaceArchive);
		fWorkspaceRadio.setSelection(isWorkspaceArchive);

	}

	public void setFocus() {
		fURLField.postSetFocusOnDialogField(fShell.getDisplay());
	}

	public void performDefaults() {
		initializeSelections();
	}

	public URL getJavadocLocation() {
		if (fIsForSource || fURLRadioButton.isSelected()) {
			return fURLResult;
		}
		return fArchiveURLResult;
	}

	private class EntryValidator implements Runnable {

		private String fInvalidMessage= PreferencesMessages.JavadocConfigurationBlock_InvalidLocation_message;
		private String fTitle=  PreferencesMessages.JavadocConfigurationBlock_MessageDialog_title;
		private String fUnable= PreferencesMessages.JavadocConfigurationBlock_UnableToValidateLocation_message;

		public void run() {

			URL location= getJavadocLocation();
			if (location == null) {
				MessageDialog.openWarning(fShell, fTitle, fInvalidMessage);
				return;
			}

			try {
				String protocol = location.getProtocol();
				if (protocol.startsWith("http") || protocol.equals("jar")) { //$NON-NLS-1$ //$NON-NLS-2$
					validateURL(location);
				} else if (protocol.equals("file")) { //$NON-NLS-1$
					validateFile(location);
				} else {
					MessageDialog.openWarning(fShell, fTitle, fUnable);
				}
			} catch (MalformedURLException e) {
				MessageDialog.openWarning(fShell, fTitle, fUnable);
			} catch (URISyntaxException e) {
				MessageDialog.openWarning(fShell, fTitle, fUnable);
			}

		}

		public void spawnInBrowser(URL url) {
			OpenBrowserUtil.openExternal(url, fShell.getDisplay());
		}

		private void validateFile(URL location) throws MalformedURLException {
			File folder = JavaDocLocations.toFile(location);
			if (folder.isDirectory()) {
				File indexFile= new File(folder, "index.html"); //$NON-NLS-1$
				if (indexFile.isFile()) {
					File packageList= new File(folder, "package-list"); //$NON-NLS-1$
					if (packageList.exists()) {
						showConfirmValidationDialog(indexFile.toURI().toURL());
						return;
					}
				}
			}
			MessageDialog.openWarning(fShell, fTitle, fInvalidMessage);
		}

		private void validateURL(URL location) throws MalformedURLException, URISyntaxException {
			URI path= URIUtil.toURI(location);
			URI index = URIUtil.append(path, "index.html"); //$NON-NLS-1$
			URI packagelist = URIUtil.append(path, "package-list"); //$NON-NLS-1$
			URL indexURL = URIUtil.toURL(index);
			URL packagelistURL = URIUtil.toURL(packagelist);
			
			boolean suc= checkURLConnection(indexURL) && checkURLConnection(packagelistURL);
			if (suc) {
				showConfirmValidationDialog(indexURL);
			} else {
				MessageDialog.openWarning(fShell, fTitle, fInvalidMessage);
			}
		}

		private void showConfirmValidationDialog(URL url) {
			String message= PreferencesMessages.JavadocConfigurationBlock_ValidLocation_message;
			String okLabel= PreferencesMessages.JavadocConfigurationBlock_OK_label;
			String openLabel= PreferencesMessages.JavadocConfigurationBlock_Open_label;
			MessageDialog dialog= new MessageDialog(fShell, fTitle, null, message, MessageDialog.INFORMATION, new String[] { okLabel, openLabel }, 0);
			if (dialog.open() == 1)
				spawnInBrowser(url);
		}
	}

	private boolean checkURLConnection(URL url) {
		int res= 0;
		URLConnection connection= null;
		try {
			connection= url.openConnection();
			if (connection instanceof HttpURLConnection) {
				connection.connect();
				res= ((HttpURLConnection) connection).getResponseCode();
			}
			InputStream is= null;
			try {
				is= connection.getInputStream();
				byte[] buffer= new byte[256];
				while (is.read(buffer) != -1) {
					// just read
				}
			} finally {
				if (is != null)
					is.close();
			}
		} catch (IllegalArgumentException e) {
			return false; // workaround for bug 91072
		} catch (NullPointerException e) {
			return false; // workaround for http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6536522
		} catch (IOException e) {
			return false;
		}
		return res < 400;
	}


	private class JDocConfigurationAdapter implements IDialogFieldListener {

		// ---------- IDialogFieldListener --------
		public void dialogFieldChanged(DialogField field) {
			jdocDialogFieldChanged(field);
		}
	}


	private void jdocDialogFieldChanged(DialogField field) {
		if (field == fURLField) {
			fURLStatus= updateURLStatus();
			statusChanged();
		} else if (field == fArchiveField) {
			fArchiveStatus= updateArchiveStatus();
			statusChanged();
		} else if (field == fArchivePathField) {
			fArchivePathStatus= updateArchivePathStatus();
			statusChanged();
		} else if (field == fValidateURLButton || field == fValidateArchiveButton) {
			EntryValidator validator= new EntryValidator();
			BusyIndicator.showWhile(fShell.getDisplay(), validator);
		} else if (field == fBrowseFolder) {
			String url= chooseJavaDocFolder();
			if (url != null) {
				fURLField.setText(url);
			}
		} else if (field == fBrowseArchive) {
			String jarPath= chooseArchive();
			if (jarPath != null) {
				fArchiveField.setText(jarPath);
			}
		} else if (field == fExternalRadio || field == fWorkspaceRadio) {
			fArchiveStatus= updateArchiveStatus();
			statusChanged();
		} else if (field == fBrowseArchivePath) {
			String archivePath= chooseArchivePath();
			if (archivePath != null) {
				fArchivePathField.setText(archivePath);
			}
		} else if (field == fURLRadioButton || field == fArchiveRadioButton) {
			statusChanged();
		}
	}

	private void statusChanged() {
		IStatus status;
		boolean isURL= fIsForSource || fURLRadioButton.isSelected();
		if (isURL) {
			status= fURLStatus;
		} else {
			status= StatusUtil.getMoreSevere(fArchiveStatus, fArchivePathStatus);
		}
		if (!fIsForSource) {
			boolean canBrowseArchivePath= !isURL && fArchiveStatus.isOK() && fArchiveField.getText().length() > 0;
			if (canBrowseArchivePath && fWorkspaceRadio.isSelected()) {
				IResource resource= ResourcesPlugin.getWorkspace().getRoot().findMember(new Path(fArchiveField.getText()));
				canBrowseArchivePath= resource != null && resource.getLocation() != null;
			}
			fBrowseArchivePath.setEnabled(canBrowseArchivePath);
		}
		fContext.statusChanged(status);
	}


	private String chooseArchivePath() {
		final String[] res= new String[] { null };
		BusyIndicator.showWhile(fShell.getDisplay(), new Runnable() {
			public void run() {
				res[0]= internalChooseArchivePath();
			}
		});
		return res[0];
	}

	private String encodeExclamationMarks(String str) {
		StringBuffer buf= new StringBuffer(str.length());
		for (int i= 0; i < str.length(); i++) {
			char ch= str.charAt(i);
			if (ch == '!') {
				buf.append("%21"); //$NON-NLS-1$
			} else {
				buf.append(ch);
			}
		}
		return buf.toString();
	}

	private String internalChooseArchivePath() {
		ZipFile zipFile= null;
		try {
			if (fWorkspaceRadio.isSelected()) {
				IResource resource= ResourcesPlugin.getWorkspace().getRoot().findMember(new Path(fArchiveField.getText()));
				if (resource != null) {
					IPath location= resource.getLocation();
					if (location != null) {
						zipFile= new ZipFile(location.toOSString());
					}
				}
			} else {
				zipFile= new ZipFile(fArchiveField.getText());
			}
			if (zipFile == null) {
				return null;
			}

			ZipFileStructureProvider provider= new ZipFileStructureProvider(zipFile);

			ILabelProvider lp= new ZipDialogLabelProvider(provider);
			ZipDialogContentProvider cp= new ZipDialogContentProvider(provider);

			ElementTreeSelectionDialog dialog= new ElementTreeSelectionDialog(fShell, lp, cp);
			dialog.setAllowMultiple(false);
			dialog.setValidator(new ZipDialogValidator());
			dialog.setTitle(PreferencesMessages.JavadocConfigurationBlock_browse_jarorzip_path_title);
			dialog.setMessage(PreferencesMessages.JavadocConfigurationBlock_location_in_jarorzip_message);
			dialog.setComparator(new ViewerComparator());

			String init= fArchivePathField.getText();
			if (init.length() == 0) {
				init= "docs/api"; //$NON-NLS-1$
			}
			dialog.setInitialSelection(cp.findElement(new Path(init)));

			dialog.setInput(this);
			if (dialog.open() == Window.OK) {
				String name= provider.getFullPath(dialog.getFirstResult());
				return new Path(name).removeTrailingSeparator().toString();
			}
		} catch (IOException e) {
			JavaPlugin.log(e);
		} finally {
			if (zipFile != null) {
				try {
					zipFile.close();
				} catch (IOException e1) {
					// ignore
				}
			}
		}
		return null;
	}

	private String chooseArchive() {
		if (fWorkspaceRadio.isSelected()) {
			return chooseWorkspaceArchive();
		}

		IPath currPath= new Path(fArchiveField.getText());
		if (ArchiveFileFilter.isArchivePath(currPath, true)) {
			currPath= currPath.removeLastSegments(1);
		}

		FileDialog dialog= new FileDialog(fShell, SWT.OPEN);
		dialog.setFilterExtensions(ArchiveFileFilter.JAR_ZIP_FILTER_EXTENSIONS);
		dialog.setText(PreferencesMessages.JavadocConfigurationBlock_zipImportSource_title);
		dialog.setFilterPath(currPath.toOSString());

		return dialog.open();
	}

	private String chooseWorkspaceArchive() {
		String initSelection= fArchiveField.getText();

		ILabelProvider lp= new WorkbenchLabelProvider();
		ITreeContentProvider cp= new WorkbenchContentProvider();
		Class<?>[] acceptedClasses= new Class[] { IFile.class };
		TypedElementSelectionValidator validator= new TypedElementSelectionValidator(acceptedClasses, true);

		IResource initSel= null;
		IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
		if (initSelection.length() > 0) {
			initSel= root.findMember(new Path(initSelection));
		}

		FilteredElementTreeSelectionDialog dialog= new FilteredElementTreeSelectionDialog(fShell, lp, cp);
		dialog.setInitialFilter(ArchiveFileFilter.JARZIP_FILTER_STRING);
		dialog.setAllowMultiple(false);
		dialog.setValidator(validator);
		dialog.setComparator(new ResourceComparator(ResourceComparator.NAME));
		dialog.setTitle(PreferencesMessages.JavadocConfigurationBlock_workspace_archive_selection_dialog_title);
		dialog.setMessage(PreferencesMessages.JavadocConfigurationBlock_workspace_archive_selection_dialog_description);
		dialog.setInput(root);
		dialog.setInitialSelection(initSel);
		dialog.setHelpAvailable(false);
		if (dialog.open() == Window.OK) {
			IResource res= (IResource) dialog.getFirstResult();
			return res.getFullPath().makeRelative().toString();
		}
		return null;
	}

	/**
	 * Display an error dialog with the specified message.
	 *
	 * @param message the error message
	 */
	protected void displayErrorDialog(String message) {
		MessageDialog.openError(fShell, PreferencesMessages.JavadocConfigurationBlock_error_dialog_title, message);
	}

	private String chooseJavaDocFolder() {
		String initPath= ""; //$NON-NLS-1$
		if (fURLResult != null && "file".equals(fURLResult.getProtocol())) { //$NON-NLS-1$
			initPath= JavaDocLocations.toFile(fURLResult).getPath();
		}
		DirectoryDialog dialog= new DirectoryDialog(fShell);
		dialog.setText(PreferencesMessages.JavadocConfigurationBlock_javadocFolderDialog_label);
		dialog.setMessage(PreferencesMessages.JavadocConfigurationBlock_javadocFolderDialog_message);
		dialog.setFilterPath(initPath);
		String result= dialog.open();
		if (result != null) {
			try {
				URL url= new File(result).toURI().toURL();
				return url.toExternalForm();
			} catch (MalformedURLException e) {
				JavaPlugin.log(e);
			}
		}
		return null;
	}

	private IStatus updateURLStatus() {
		StatusInfo status= new StatusInfo();
		fURLResult= null;
		try {
			String jdocLocation= fURLField.getText();
			if (jdocLocation.length() == 0) {
				return status;
			}
			URL url= new URL(jdocLocation);
			if ("file".equals(url.getProtocol())) { //$NON-NLS-1$
				if (url.getFile() == null) {
					status.setError(PreferencesMessages.JavadocConfigurationBlock_error_notafolder);
					return status;
				}
			}
			fURLResult= url;
		} catch (MalformedURLException e) {
			status.setError(PreferencesMessages.JavadocConfigurationBlock_MalformedURL_error);
			return status;
		}

		return status;
	}

	private IStatus updateArchiveStatus() {
		try {
			fArchiveURLResult= null;

			StatusInfo status= new StatusInfo();
			String jdocLocation= fArchiveField.getText();
			if (jdocLocation.length() > 0)  {
				if (!Path.ROOT.isValidPath(jdocLocation)) {
					status.setError(PreferencesMessages.JavadocConfigurationBlock_error_invalidarchivepath);
					return status;
				}
				if (fWorkspaceRadio.isSelected()) {
					IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
					IResource res= root.findMember(new Path(jdocLocation));
					if (res != null) {
						if (!(res instanceof IFile)) {
							status.setError(PreferencesMessages.JavadocConfigurationBlock_error_archive_not_found_in_workspace);
							return status;
						}
					} else {
						status.setError(PreferencesMessages.JavadocConfigurationBlock_error_archive_not_found_in_workspace);
						return status;
					}
				} else {
					IPath path= Path.fromOSString(jdocLocation);
					if (!path.isAbsolute()) {
						status.setError(PreferencesMessages.JavadocConfigurationBlock_error_archivepathnotabsolute);
						return status;
					}
					File jarFile= new File(jdocLocation);
					if (jarFile.isDirectory())  {
						status.setError(PreferencesMessages.JavadocConfigurationBlock_error_notafile);
						return status;
					}
					if (!jarFile.exists())  {
						status.setWarning(PreferencesMessages.JavadocConfigurationBlock_error_notafile);
					}
				}
				fArchiveURLResult= getArchiveURL();
			}
			return status;
		} catch (MalformedURLException e) {
			StatusInfo status= new StatusInfo();
			status.setError(e.getMessage());
			return status;
		}
	}

	private IStatus updateArchivePathStatus() {
		// no validation yet
		try {
			fArchiveURLResult= getArchiveURL();
		} catch (MalformedURLException e) {
			fArchiveURLResult= null;
			StatusInfo status= new StatusInfo();
			status.setError(e.getMessage());
			//status.setError(PreferencesMessages.getString("JavadocConfigurationBlock.MalformedURL.error"));  //$NON-NLS-1$
			return status;
		}
		return new StatusInfo();

	}


	private URL getArchiveURL() throws MalformedURLException {
		String jarLoc= fArchiveField.getText();
		String innerPath= fArchivePathField.getText().trim();

		try {
			URI baseUri;

			if (fWorkspaceRadio.isSelected()) {
				IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
				IResource res= root.findMember(new Path(jarLoc));
				if (res != null) {
					// 3-arg constructor of URI is broken, see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7177117
					baseUri= new URI("platform", null, "/resource" + res.getFullPath().toString(), null, null); //$NON-NLS-1$ //$NON-NLS-2$
				} else {
					throw new URISyntaxException(jarLoc, ""); //$NON-NLS-1$
				}
			} else {
				baseUri= new File(jarLoc).toURI();
			}
			
			if (innerPath.length() == 0 || innerPath.charAt(0) != '/') {
				innerPath= '/' + innerPath;
			}
			String encodedInnerPath= new URI(null, null, innerPath, null, null).getRawSchemeSpecificPart();
			
			return new URI("jar:" + encodeExclamationMarks(baseUri.toString()) + '!' + encodeExclamationMarks(encodedInnerPath)).toURL(); //$NON-NLS-1$
			
		} catch (URISyntaxException e) {
			throw new MalformedURLException(e.getMessage());
		}
	}


	/**
	 * An adapter for presenting a zip file in a tree viewer.
	 */
	private static class ZipDialogContentProvider implements ITreeContentProvider {

		private ZipFileStructureProvider fProvider;

		public ZipDialogContentProvider(ZipFileStructureProvider provider) {
			fProvider= provider;
		}

		public Object findElement(IPath path) {
			String[] segments= path.segments();

			Object elem= fProvider.getRoot();
			for (int i= 0; i < segments.length && elem != null; i++) {
				List<?> list= fProvider.getChildren(elem);
				String name= segments[i];
				elem= null;
				for (int k= 0; k < list.size(); k++) {
					Object curr= list.get(k);
					if (fProvider.isFolder(curr) && name.equals(fProvider.getLabel(curr))) {
						elem= curr;
						break;
					}
				}
			}
			return elem;
		}

		/* non java-doc
		 * @see ITreeContentProvider#inputChanged
		 */
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			// nothing to do
		}

		/* non java-doc
		  * @see ITreeContentProvider#getParent
		  */
		public Object getParent(Object element) {
			if (element.equals(fProvider.getRoot())) {
				return null;
			}
			IPath path= new Path(fProvider.getFullPath(element));
			if (path.segmentCount() > 0) {
				return findElement(path.removeLastSegments(1));
			}
			return fProvider.getRoot();
		}

		/* non java-doc
		 * @see ITreeContentProvider#hasChildren
		 */
		public boolean hasChildren(Object element) {
			List<?> list= fProvider.getChildren(element);
			if (list != null) {
				for (int i= 0; i < list.size(); i++) {
					if (fProvider.isFolder(list.get(i))) {
						return true;
					}
				}
			}
			return false;
		}

		/* non java-doc
		 * @see ITreeContentProvider#getChildren
		 */
		public Object[] getChildren(Object element) {
			List<?> list= fProvider.getChildren(element);
			ArrayList<Object> res= new ArrayList<Object>();
			if (list != null) {
				for (int i= 0; i < list.size(); i++) {
					Object curr= list.get(i);
					if (fProvider.isFolder(curr)) {
						res.add(curr);
					}
				}
			}
			return res.toArray();
		}

		/* non java-doc
		 * @see ITreeContentProvider#getElements
		 */
		public Object[] getElements(Object element) {
			return new Object[] {fProvider.getRoot() };
		}

		/* non java-doc
		 * @see IContentProvider#dispose
		 */
		public void dispose() {
			// nothing to do
		}
	}

	private static class ZipDialogLabelProvider extends LabelProvider {

		private final Image IMG_JAR=
			JavaUI.getSharedImages().getImage(org.eclipse.jdt.ui.ISharedImages.IMG_OBJS_JAR);
		private final Image IMG_FOLDER=
			PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FOLDER);

		private ZipFileStructureProvider fProvider;

		public ZipDialogLabelProvider(ZipFileStructureProvider provider) {
			fProvider= provider;
		}

		@Override
		public Image getImage(Object element) {
			if (element == fProvider.getRoot()) {
				return IMG_JAR;
			} else {
				return IMG_FOLDER;
			}
		}

		@Override
		public String getText(Object element) {
			if (element == fProvider.getRoot()) {
				return BasicElementLabels.getResourceName(fProvider.getZipFile().getName());
			}
			return BasicElementLabels.getResourceName(fProvider.getLabel(element));
		}
	}

	private static class ZipDialogValidator implements ISelectionStatusValidator {
		public ZipDialogValidator() {
			super();
		}

		/*
		 * @see ISelectionValidator#validate(Object[])
		 */
		public IStatus validate(Object[] selection) {
			String message= ""; //$NON-NLS-1$
			return new StatusInfo(IStatus.INFO, message);
		}
	}

}

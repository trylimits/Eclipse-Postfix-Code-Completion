/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;


/**
 * The preference page for defaults for classpath entries in new java projects.
 * <p>
 * See {@link PreferenceConstants} on how to access or change these values through public API.
 * </p>
 * 
 * @since 2.0
 */
public class NewJavaProjectPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	public static final String ID= "org.eclipse.jdt.ui.preferences.BuildPathPreferencePage"; //$NON-NLS-1$

	private static final String SRCBIN_FOLDERS_IN_NEWPROJ= PreferenceConstants.SRCBIN_FOLDERS_IN_NEWPROJ;
	private static final String SRCBIN_SRCNAME= PreferenceConstants.SRCBIN_SRCNAME;
	private static final String SRCBIN_BINNAME= PreferenceConstants.SRCBIN_BINNAME;

	private static final String CLASSPATH_JRELIBRARY_INDEX= PreferenceConstants.NEWPROJECT_JRELIBRARY_INDEX;
	private static final String CLASSPATH_JRELIBRARY_LIST= PreferenceConstants.NEWPROJECT_JRELIBRARY_LIST;


	private static final String ENCODING= "UTF-8"; //$NON-NLS-1$

	public static IClasspathEntry[] getDefaultJRELibrary() {
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();

		String str= store.getString(CLASSPATH_JRELIBRARY_LIST);
		int index= store.getInt(CLASSPATH_JRELIBRARY_INDEX);

		StringTokenizer tok= new StringTokenizer(str, ";"); //$NON-NLS-1$
		while (tok.hasMoreTokens() && index > 0) {
			tok.nextToken();
			index--;
		}

		if (tok.hasMoreTokens()) {
			IClasspathEntry[] res= decodeJRELibraryClasspathEntries(tok.nextToken());
			if (res.length > 0) {
				return res;
			}
		}
		return new IClasspathEntry[] { getJREContainerEntry() };
	}

	// JRE Entry

	public static String decodeJRELibraryDescription(String encoded) {
		int end= encoded.indexOf(' ');
		if (end != -1) {
			return decode(encoded.substring(0, end));
		}
		return ""; //$NON-NLS-1$
	}

	private static String decode(String str) {
		try {
			return URLDecoder.decode(str, ENCODING);
		} catch (UnsupportedEncodingException e) {
			JavaPlugin.log(e);
		}
		return ""; //$NON-NLS-1$
	}

	private static String encode(String str) {
		try {
			return URLEncoder.encode(str, ENCODING);
		} catch (UnsupportedEncodingException e) {
			JavaPlugin.log(e);
		}
		return ""; //$NON-NLS-1$
	}

	public static IClasspathEntry[] decodeJRELibraryClasspathEntries(String encoded) {
		StringTokenizer tok= new StringTokenizer(encoded, " "); //$NON-NLS-1$
		ArrayList<IClasspathEntry> res= new ArrayList<IClasspathEntry>();
		while (tok.hasMoreTokens()) {
			try {
				tok.nextToken(); // desc: ignore
				int kind= Integer.parseInt(tok.nextToken());
				IPath path= decodePath(tok.nextToken());
				IPath attachPath= decodePath(tok.nextToken());
				IPath attachRoot= decodePath(tok.nextToken());
				boolean isExported= Boolean.valueOf(tok.nextToken()).booleanValue();
				switch (kind) {
					case IClasspathEntry.CPE_SOURCE:
						res.add(JavaCore.newSourceEntry(path));
						break;
					case IClasspathEntry.CPE_LIBRARY:
						res.add(JavaCore.newLibraryEntry(path, attachPath, attachRoot, isExported));
						break;
					case IClasspathEntry.CPE_VARIABLE:
						res.add(JavaCore.newVariableEntry(path, attachPath, attachRoot, isExported));
						break;
					case IClasspathEntry.CPE_PROJECT:
						res.add(JavaCore.newProjectEntry(path, isExported));
						break;
					case IClasspathEntry.CPE_CONTAINER:
						res.add(JavaCore.newContainerEntry(path, isExported));
						break;
				}
			} catch (NumberFormatException e) {
				String message= PreferencesMessages.NewJavaProjectPreferencePage_error_decode;
				JavaPlugin.log(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.ERROR, message, e));
			} catch (NoSuchElementException e) {
				String message= PreferencesMessages.NewJavaProjectPreferencePage_error_decode;
				JavaPlugin.log(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.ERROR, message, e));
			}
		}
		return res.toArray(new IClasspathEntry[res.size()]);
	}


	public static String encodeJRELibrary(String desc, IClasspathEntry[] cpentries) {
		StringBuffer buf= new StringBuffer();
		for (int i= 0; i < cpentries.length; i++) {
			IClasspathEntry entry= cpentries[i];
			buf.append(encode(desc));
			buf.append(' ');
			buf.append(entry.getEntryKind());
			buf.append(' ');
			buf.append(encodePath(entry.getPath()));
			buf.append(' ');
			buf.append(encodePath(entry.getSourceAttachmentPath()));
			buf.append(' ');
			buf.append(encodePath(entry.getSourceAttachmentRootPath()));
			buf.append(' ');
			buf.append(entry.isExported());
			buf.append(' ');
		}
		return buf.toString();
	}

	private static String encodePath(IPath path) {
		if (path == null) {
			return "#"; //$NON-NLS-1$
		} else if (path.isEmpty()) {
			return "&"; //$NON-NLS-1$
		} else {
			return encode(path.toPortableString());
		}
	}

	private static IPath decodePath(String str) {
		if ("#".equals(str)) { //$NON-NLS-1$
			return null;
		} else if ("&".equals(str)) { //$NON-NLS-1$
			return Path.EMPTY;
		} else {
			return Path.fromPortableString(decode(str));
		}
	}


	private ArrayList<Button> fCheckBoxes;
	private ArrayList<Button> fRadioButtons;
	private ArrayList<Text> fTextControls;

	private SelectionListener fSelectionListener;
	private ModifyListener fModifyListener;

	private Text fBinFolderNameText;
	private Text fSrcFolderNameText;

	private Combo fJRECombo;

	private Button fProjectAsSourceFolder;
	private Button fFoldersAsSourceFolder;

	private Label fSrcFolderNameLabel;
	private Label fBinFolderNameLabel;

	public NewJavaProjectPreferencePage() {
		super();
		setPreferenceStore(JavaPlugin.getDefault().getPreferenceStore());
		setDescription(PreferencesMessages.NewJavaProjectPreferencePage_description);

		// title used when opened programatically
		setTitle(PreferencesMessages.NewJavaProjectPreferencePage_title);

		fRadioButtons= new ArrayList<Button>();
		fCheckBoxes= new ArrayList<Button>();
		fTextControls= new ArrayList<Text>();

		fSelectionListener= new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {}

			public void widgetSelected(SelectionEvent e) {
				controlChanged(e.widget);
			}
		};

		fModifyListener= new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				controlModified(e.widget);
			}
		};

	}

	public static void initDefaults(IPreferenceStore store) {
		store.setDefault(SRCBIN_FOLDERS_IN_NEWPROJ, true);
		store.setDefault(SRCBIN_SRCNAME, "src"); //$NON-NLS-1$
		store.setDefault(SRCBIN_BINNAME, "bin"); //$NON-NLS-1$

		store.setDefault(CLASSPATH_JRELIBRARY_LIST, getDefaultJRELibraries());
		store.setDefault(CLASSPATH_JRELIBRARY_INDEX, 0);
	}

	private static String getDefaultJRELibraries() {
		StringBuffer buf= new StringBuffer();
		IClasspathEntry cntentry= getJREContainerEntry();
		buf.append(encodeJRELibrary(PreferencesMessages.NewJavaProjectPreferencePage_jre_container_description, new IClasspathEntry[] { cntentry} ));
		buf.append(';');
		IClasspathEntry varentry= getJREVariableEntry();
		buf.append(encodeJRELibrary(PreferencesMessages.NewJavaProjectPreferencePage_jre_variable_description, new IClasspathEntry[] { varentry }));
		buf.append(';');
		return buf.toString();
	}

	private static IClasspathEntry getJREContainerEntry() {
		return JavaCore.newContainerEntry(new Path("org.eclipse.jdt.launching.JRE_CONTAINER")); //$NON-NLS-1$
	}

	private static IClasspathEntry getJREVariableEntry() {
		return JavaCore.newVariableEntry(new Path("JRE_LIB"), new Path("JRE_SRC"), new Path("JRE_SRCROOT")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	/*
	 * @see IWorkbenchPreferencePage#init(IWorkbench)
	 */
	public void init(IWorkbench workbench) {
	}

	/**
	 * @see PreferencePage#createControl(Composite)
	 */
	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.NEW_JAVA_PROJECT_PREFERENCE_PAGE);
	}


	private Button addRadioButton(Composite parent, String label, String key, String value, int indent) {
		GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalSpan= 2;
		gd.horizontalIndent= indent;

		Button button= new Button(parent, SWT.RADIO);
		button.setText(label);
		button.setData(new String[] { key, value });
		button.setLayoutData(gd);

		button.setSelection(value.equals(getPreferenceStore().getString(key)));

		fRadioButtons.add(button);
		return button;
	}

	private Text addTextControl(Composite parent, Label labelControl, String key, int indent) {
		GridData gd= new GridData();
		gd.horizontalIndent= indent;

		labelControl.setLayoutData(gd);

		gd= new GridData(GridData.FILL_HORIZONTAL);
		gd.widthHint= convertWidthInCharsToPixels(30);

		Text text= new Text(parent, SWT.SINGLE | SWT.BORDER);
		text.setText(getPreferenceStore().getString(key));
		text.setData(key);
		text.setLayoutData(gd);

		fTextControls.add(text);
		return text;
	}


	@Override
	protected Control createContents(Composite parent) {
		initializeDialogUnits(parent);

		Composite result= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginHeight= convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		layout.marginWidth= 0;
		layout.verticalSpacing= convertVerticalDLUsToPixels(10);
		layout.horizontalSpacing= convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		layout.numColumns= 2;
		result.setLayout(layout);

		GridData gd= new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan= 2;

		Group sourceFolderGroup= new Group(result, SWT.NONE);
		layout= new GridLayout();
		layout.numColumns= 2;
		sourceFolderGroup.setLayout(layout);
		sourceFolderGroup.setLayoutData(gd);
		sourceFolderGroup.setText(PreferencesMessages.NewJavaProjectPreferencePage_sourcefolder_label);

		int indent= 0;

		fProjectAsSourceFolder= addRadioButton(sourceFolderGroup, PreferencesMessages.NewJavaProjectPreferencePage_sourcefolder_project, SRCBIN_FOLDERS_IN_NEWPROJ, IPreferenceStore.FALSE, indent);
		fProjectAsSourceFolder.addSelectionListener(fSelectionListener);

		fFoldersAsSourceFolder= addRadioButton(sourceFolderGroup, PreferencesMessages.NewJavaProjectPreferencePage_sourcefolder_folder, SRCBIN_FOLDERS_IN_NEWPROJ, IPreferenceStore.TRUE, indent);
		fFoldersAsSourceFolder.addSelectionListener(fSelectionListener);

		indent= convertWidthInCharsToPixels(4);

		fSrcFolderNameLabel= new Label(sourceFolderGroup, SWT.NONE);
		fSrcFolderNameLabel.setText(PreferencesMessages.NewJavaProjectPreferencePage_folders_src);
		fSrcFolderNameText= addTextControl(sourceFolderGroup, fSrcFolderNameLabel, SRCBIN_SRCNAME, indent);
		fSrcFolderNameText.addModifyListener(fModifyListener);

		fBinFolderNameLabel= new Label(sourceFolderGroup, SWT.NONE);
		fBinFolderNameLabel.setText(PreferencesMessages.NewJavaProjectPreferencePage_folders_bin);
		fBinFolderNameText= addTextControl(sourceFolderGroup, fBinFolderNameLabel, SRCBIN_BINNAME, indent);
		fBinFolderNameText.addModifyListener(fModifyListener);

		String[] jreNames= getJRENames();
		if (jreNames.length > 0) {
			Label jreSelectionLabel= new Label(result, SWT.NONE);
			jreSelectionLabel.setText(PreferencesMessages.NewJavaProjectPreferencePage_jrelibrary_label);
			jreSelectionLabel.setLayoutData(new GridData());

			int index= getPreferenceStore().getInt(CLASSPATH_JRELIBRARY_INDEX);
			fJRECombo= new Combo(result, SWT.READ_ONLY);
			fJRECombo.setItems(jreNames);
			fJRECombo.select(index);
			fJRECombo.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		}

		validateFolders();

		Dialog.applyDialogFont(result);
		return result;
	}

	private void validateFolders() {
		boolean useFolders= fFoldersAsSourceFolder.getSelection();

		fSrcFolderNameText.setEnabled(useFolders);
		fBinFolderNameText.setEnabled(useFolders);
		fSrcFolderNameLabel.setEnabled(useFolders);
		fBinFolderNameLabel.setEnabled(useFolders);
		if (useFolders) {
			String srcName= fSrcFolderNameText.getText();
			String binName= fBinFolderNameText.getText();
			if (srcName.length() + binName.length() == 0) {
				updateStatus(new StatusInfo(IStatus.ERROR,  PreferencesMessages.NewJavaProjectPreferencePage_folders_error_namesempty));
				return;
			}
			IWorkspace workspace= JavaPlugin.getWorkspace();
			IProject dmy= workspace.getRoot().getProject("project"); //$NON-NLS-1$

			IStatus status;
			IPath srcPath= dmy.getFullPath().append(srcName);
			if (srcName.length() != 0) {
				status= workspace.validatePath(srcPath.toString(), IResource.FOLDER);
				if (!status.isOK()) {
					String message= Messages.format(PreferencesMessages.NewJavaProjectPreferencePage_folders_error_invalidsrcname, status.getMessage());
					updateStatus(new StatusInfo(IStatus.ERROR, message));
					return;
				}
			}
			IPath binPath= dmy.getFullPath().append(binName);
			if (binName.length() != 0) {
				status= workspace.validatePath(binPath.toString(), IResource.FOLDER);
				if (!status.isOK()) {
					String message= Messages.format(PreferencesMessages.NewJavaProjectPreferencePage_folders_error_invalidbinname, status.getMessage());
					updateStatus(new StatusInfo(IStatus.ERROR, message));
					return;
				}
			}
			IClasspathEntry entry= JavaCore.newSourceEntry(srcPath);
			status= JavaConventions.validateClasspath(JavaCore.create(dmy), new IClasspathEntry[] { entry }, binPath);
			if (!status.isOK()) {
				String message= PreferencesMessages.NewJavaProjectPreferencePage_folders_error_invalidcp;
				updateStatus(new StatusInfo(IStatus.ERROR, message));
				return;
			}
		}
		updateStatus(new StatusInfo()); // set to OK
	}

	private void updateStatus(IStatus status) {
		setValid(!status.matches(IStatus.ERROR));
		StatusUtil.applyToStatusLine(this, status);
	}

	private void controlChanged(Widget widget) {
		if (widget == fFoldersAsSourceFolder || widget == fProjectAsSourceFolder) {
			validateFolders();
		}
	}

	private void controlModified(Widget widget) {
		if (widget == fSrcFolderNameText || widget == fBinFolderNameText) {
			validateFolders();
		}
	}

	/*
	 * @see PreferencePage#performDefaults()
	 */
	@Override
	protected void performDefaults() {
		IPreferenceStore store= getPreferenceStore();
		for (int i= 0; i < fCheckBoxes.size(); i++) {
			Button button= fCheckBoxes.get(i);
			String key= (String) button.getData();
			button.setSelection(store.getDefaultBoolean(key));
		}
		for (int i= 0; i < fRadioButtons.size(); i++) {
			Button button= fRadioButtons.get(i);
			String[] info= (String[]) button.getData();
			button.setSelection(info[1].equals(store.getDefaultString(info[0])));
		}
		for (int i= 0; i < fTextControls.size(); i++) {
			Text text= fTextControls.get(i);
			String key= (String) text.getData();
			text.setText(store.getDefaultString(key));
		}
		if (fJRECombo != null) {
			fJRECombo.select(store.getDefaultInt(CLASSPATH_JRELIBRARY_INDEX));
		}

		validateFolders();
		super.performDefaults();
	}

	/*
	 * @see IPreferencePage#performOk()
	 */
	@Override
	public boolean performOk() {
		IPreferenceStore store= getPreferenceStore();
		for (int i= 0; i < fCheckBoxes.size(); i++) {
			Button button= fCheckBoxes.get(i);
			String key= (String) button.getData();
			store.setValue(key, button.getSelection());
		}
		for (int i= 0; i < fRadioButtons.size(); i++) {
			Button button= fRadioButtons.get(i);
			if (button.getSelection()) {
				String[] info= (String[]) button.getData();
				store.setValue(info[0], info[1]);
			}
		}
		for (int i= 0; i < fTextControls.size(); i++) {
			Text text= fTextControls.get(i);
			String key= (String) text.getData();
			store.setValue(key, text.getText());
		}

		if (fJRECombo != null) {
			store.setValue(CLASSPATH_JRELIBRARY_INDEX, fJRECombo.getSelectionIndex());
		}

		JavaPlugin.flushInstanceScope();
		return super.performOk();
	}

	private String[] getJRENames() {
		String prefString= getPreferenceStore().getString(CLASSPATH_JRELIBRARY_LIST);
		ArrayList<String> list= new ArrayList<String>();
		StringTokenizer tok= new StringTokenizer(prefString, ";"); //$NON-NLS-1$
		while (tok.hasMoreTokens()) {
			list.add(decodeJRELibraryDescription(tok.nextToken()));
		}
		return list.toArray(new String[list.size()]);
	}

}



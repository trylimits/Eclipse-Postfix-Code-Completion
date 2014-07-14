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
package org.eclipse.jdt.internal.ui.jarpackager;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.SaveAsDialog;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.SWTUtil;

/**
 *	Page 2 of the JAR Package wizard
 */
class JarOptionsPage extends WizardPage implements IJarPackageWizardPage {

	// Untyped listener
	private class UntypedListener implements Listener {
		/*
		 * Implements method from Listener
		 */
		public void handleEvent(Event e) {
			if (getControl() == null)
				return;
			update();
		}
	}

	private JarPackageData fJarPackage;

	// widgets
	private Button		fExportErrorsCheckbox;
	private Button		fExportWarningsCheckbox;
	private Button		fUseSourceFoldersCheckbox;
	private Composite	fDescriptionFileGroup;
	private Button		fSaveDescriptionCheckbox;
	private Label		fDescriptionFileLabel;
	private Text		fDescriptionFileText;
	private Button		fDescriptionFileBrowseButton;
	private Button		fBuildIfNeededCheckbox;

	// dialog store id constants
	private final static String PAGE_NAME= "jarOptionsWizardPage"; //$NON-NLS-1$

	private final static String STORE_EXPORT_WARNINGS= PAGE_NAME + ".EXPORT_WARNINGS"; //$NON-NLS-1$
	private final static String STORE_EXPORT_ERRORS= PAGE_NAME + ".EXPORT_ERRORS"; //$NON-NLS-1$
	private final static String STORE_SAVE_DESCRIPTION= PAGE_NAME + ".SAVE_DESCRIPTION"; //$NON-NLS-1$
	private final static String STORE_DESCRIPTION_LOCATION= PAGE_NAME + ".DESCRIPTION_LOCATION"; //$NON-NLS-1$
	private final static String STORE_USE_SRC_FOLDERS= PAGE_NAME + ".STORE_USE_SRC_FOLDERS"; //$NON-NLS-1$
	private final static String STORE_BUILD_IF_NEEDED= PAGE_NAME + ".BUILD_IF_NEEDED"; //$NON-NLS-1$

	/**
	 *	Create an instance of this class
	 * @param jarPackage
	 */
	public JarOptionsPage(JarPackageData jarPackage) {
		super(PAGE_NAME);
		setTitle(JarPackagerMessages.JarOptionsPage_title);
		setDescription(JarPackagerMessages.JarOptionsPage_description);
		fJarPackage= jarPackage;
	}

	/*
	 * Method declared on IDialogPage.
	 */
	public void createControl(Composite parent) {
		Composite composite= new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(
			new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));

		createOptionsGroup(composite);

		restoreWidgetValues();
		setControl(composite);
		update();

		Dialog.applyDialogFont(composite);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IJavaHelpContextIds.JAROPTIONS_WIZARD_PAGE);
	}

	/**
	 *	Create the export options specification widgets.
	 *
	 *	@param parent org.eclipse.swt.widgets.Composite
	 */
	protected void createOptionsGroup(Composite parent) {

		initializeDialogUnits(parent);

		Composite optionsGroup= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		optionsGroup.setLayout(layout);

		createLabel(optionsGroup, JarPackagerMessages.JarOptionsPage_howTreatProblems_label, false);

		UntypedListener selectionListener= new UntypedListener();

		fExportErrorsCheckbox= new Button(optionsGroup, SWT.CHECK | SWT.LEFT);
		fExportErrorsCheckbox.setText(JarPackagerMessages.JarOptionsPage_exportErrors_text);
		fExportErrorsCheckbox.addListener(SWT.Selection, selectionListener);

		fExportWarningsCheckbox= new Button(optionsGroup, SWT.CHECK | SWT.LEFT);
		fExportWarningsCheckbox.setText(JarPackagerMessages.JarOptionsPage_exportWarnings_text);
		fExportWarningsCheckbox.addListener(SWT.Selection, selectionListener);

		createSpacer(optionsGroup);

		fUseSourceFoldersCheckbox= new Button(optionsGroup, SWT.CHECK | SWT.LEFT);
		fUseSourceFoldersCheckbox.setText(JarPackagerMessages.JarOptionsPage_useSourceFoldersHierarchy);
		fUseSourceFoldersCheckbox.addListener(SWT.Selection, selectionListener);
		fUseSourceFoldersCheckbox.setEnabled(fJarPackage.areJavaFilesExported() && !fJarPackage.areGeneratedFilesExported());

		createSpacer(optionsGroup);

		fBuildIfNeededCheckbox= new Button(optionsGroup, SWT.CHECK | SWT.LEFT);
		fBuildIfNeededCheckbox.setText(JarPackagerMessages.JarOptionsPage_buildIfNeeded);
		fBuildIfNeededCheckbox.addListener(SWT.Selection, selectionListener);

		createSpacer(optionsGroup);

		fSaveDescriptionCheckbox= new Button(optionsGroup, SWT.CHECK | SWT.LEFT);
		fSaveDescriptionCheckbox.setText(JarPackagerMessages.JarOptionsPage_saveDescription_text);
		fSaveDescriptionCheckbox.addListener(SWT.Selection, selectionListener);
		createDescriptionFileGroup(parent);
	}

	/**
	 * Persists resource specification control setting that are to be restored
	 * in the next instance of this page. Subclasses wishing to persist
	 * settings for their controls should extend the hook method
	 * <code>internalSaveWidgetValues</code>.
	 */
	public final void saveWidgetValues() {
		// update directory names history
		IDialogSettings settings= getDialogSettings();
		if (settings != null) {
			settings.put(STORE_EXPORT_WARNINGS, fJarPackage.exportWarnings());
			settings.put(STORE_EXPORT_ERRORS, fJarPackage.areErrorsExported());
			settings.put(STORE_USE_SRC_FOLDERS, fJarPackage.useSourceFolderHierarchy());
			settings.put(STORE_BUILD_IF_NEEDED, fJarPackage.isBuildingIfNeeded());
			settings.put(STORE_SAVE_DESCRIPTION, fJarPackage.isDescriptionSaved());
			settings.put(STORE_DESCRIPTION_LOCATION, fJarPackage.getDescriptionLocation().toString());
		}
		// Allow subclasses to save values
		internalSaveWidgetValues();
	}

	/**
	 * Hook method for subclasses to persist their settings.
	 */
	protected void internalSaveWidgetValues() {
	}

	/**
	 *	Hook method for restoring widget values to the values that they held
	 *	last time this wizard was used to completion.
	 */
	protected void restoreWidgetValues() {
		if (!((JarPackageWizard)getWizard()).isInitializingFromJarPackage())
			initializeJarPackage();

		fExportWarningsCheckbox.setSelection(fJarPackage.exportWarnings());
		fExportErrorsCheckbox.setSelection(fJarPackage.areErrorsExported());
		fBuildIfNeededCheckbox.setSelection(fJarPackage.isBuildingIfNeeded());
		fUseSourceFoldersCheckbox.setSelection(fJarPackage.useSourceFolderHierarchy());
		fSaveDescriptionCheckbox.setSelection(fJarPackage.isDescriptionSaved());
		fDescriptionFileText.setText(fJarPackage.getDescriptionLocation().toString());
	}

	/**
	 *	Initializes the JAR package from last used wizard page values.
	 */
	protected void initializeJarPackage() {
		IDialogSettings settings= getDialogSettings();
		if (settings != null) {
			fJarPackage.setExportWarnings(settings.getBoolean(STORE_EXPORT_WARNINGS));
			fJarPackage.setExportErrors(settings.getBoolean(STORE_EXPORT_ERRORS));
			fJarPackage.setUseSourceFolderHierarchy(settings.getBoolean(STORE_USE_SRC_FOLDERS));
			fJarPackage.setSaveDescription(false); // bug 15877
			String pathStr= settings.get(STORE_DESCRIPTION_LOCATION);
			if (pathStr == null)
				pathStr= ""; //$NON-NLS-1$
			fJarPackage.setDescriptionLocation(new Path(pathStr));
			if (settings.get(STORE_BUILD_IF_NEEDED) != null)
				fJarPackage.setBuildIfNeeded(settings.getBoolean(STORE_BUILD_IF_NEEDED));
		}
	}

	private void update() {
		updateModel();
		updateWidgetEnablements();
		updatePageCompletion();
	}

	/**
	 *	Stores the widget values in the JAR package.
	 */
	protected void updateModel() {
		if (getControl() == null)
			return;
		fJarPackage.setExportWarnings(fExportWarningsCheckbox.getSelection());
		fJarPackage.setExportErrors(fExportErrorsCheckbox.getSelection());
		fJarPackage.setBuildIfNeeded(fBuildIfNeededCheckbox.getSelection());
		fJarPackage.setSaveDescription(fSaveDescriptionCheckbox.getSelection());
		fJarPackage.setDescriptionLocation(new Path(fDescriptionFileText.getText()));
		fJarPackage.setUseSourceFolderHierarchy(fUseSourceFoldersCheckbox.getSelection());
	}

	/**
	 *	Open an appropriate destination browser so that the user can specify a source
	 *	to import from
	 */
	protected void handleDescriptionFileBrowseButtonPressed() {
		SaveAsDialog dialog= new SaveAsDialog(getContainer().getShell());
		dialog.create();
		dialog.getShell().setText(JarPackagerMessages.JarOptionsPage_saveAsDialog_title);
		dialog.setMessage(JarPackagerMessages.JarOptionsPage_saveAsDialog_message);
		dialog.setOriginalFile(createFileHandle(fJarPackage.getDescriptionLocation()));
		if (dialog.open() == Window.OK) {
			IPath path= dialog.getResult();
			path= path.removeFileExtension().addFileExtension(JarPackagerUtil.DESCRIPTION_EXTENSION);
			fDescriptionFileText.setText(path.toString());
		}
	}

	/**
	 * Updates the enablements of this page's controls. Subclasses may extend.
	 */
	protected void updateWidgetEnablements() {
		boolean saveDescription= fSaveDescriptionCheckbox.getSelection();
		fDescriptionFileGroup.setEnabled(saveDescription);
		fDescriptionFileBrowseButton.setEnabled(saveDescription);
		fDescriptionFileText.setEnabled(saveDescription);
		fDescriptionFileLabel.setEnabled(saveDescription);

		boolean exportClassFiles= fJarPackage.areClassFilesExported() && !fJarPackage.areOutputFoldersExported();
		fExportWarningsCheckbox.setEnabled(exportClassFiles);
		fExportErrorsCheckbox.setEnabled(exportClassFiles);

		boolean isAutobuilding= ResourcesPlugin.getWorkspace().isAutoBuilding();
		fBuildIfNeededCheckbox.setEnabled(exportClassFiles && !isAutobuilding);

		fUseSourceFoldersCheckbox.setEnabled(fJarPackage.areJavaFilesExported() && !fJarPackage.areGeneratedFilesExported());
	}

	/*
	 * Implements method from IJarPackageWizardPage
	 */
	@Override
	public boolean isPageComplete() {
		if (fJarPackage.isDescriptionSaved()){
			if (fJarPackage.getDescriptionLocation().toString().length() == 0) {
				setErrorMessage(null);
				return false;
			}
			IPath location= fJarPackage.getDescriptionLocation();
			if (!location.toString().startsWith("/")) { //$NON-NLS-1$
				setErrorMessage(JarPackagerMessages.JarOptionsPage_error_descriptionMustBeAbsolute);
				return false;
			}
			IResource resource= findResource(location);
			if (resource != null && resource.getType() != IResource.FILE) {
				setErrorMessage(JarPackagerMessages.JarOptionsPage_error_descriptionMustNotBeExistingContainer);
				return false;
			}
			resource= findResource(location.removeLastSegments(1));
			if (resource == null || resource.getType() == IResource.FILE) {
				setErrorMessage(JarPackagerMessages.JarOptionsPage_error_descriptionContainerDoesNotExist);
				return false;
			}
			String fileExtension= fJarPackage.getDescriptionLocation().getFileExtension();
			if (fileExtension == null || !fileExtension.equals(JarPackagerUtil.DESCRIPTION_EXTENSION)) {
				setErrorMessage(Messages.format(JarPackagerMessages.JarOptionsPage_error_invalidDescriptionExtension, JarPackagerUtil.DESCRIPTION_EXTENSION));
				return false;
			}
		}
		setErrorMessage(null);
		return true;
	}

	@Override
	public boolean canFlipToNextPage() {
		return fJarPackage.areGeneratedFilesExported() && super.canFlipToNextPage();
	}

	/*
	 * Overrides method from WizardDataTransferPage
	 */
	protected void createDescriptionFileGroup(Composite parent) {
		// destination specification group
		fDescriptionFileGroup= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.numColumns= 3;
		fDescriptionFileGroup.setLayout(layout);
		fDescriptionFileGroup.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));

		fDescriptionFileLabel= new Label(fDescriptionFileGroup, SWT.NONE);
		fDescriptionFileLabel.setText(JarPackagerMessages.JarOptionsPage_descriptionFile_label);

		// destination name entry field
		fDescriptionFileText= new Text(fDescriptionFileGroup, SWT.SINGLE | SWT.BORDER);
		fDescriptionFileText.addListener(SWT.Modify, new UntypedListener());
		GridData data= new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL);
		data.widthHint= convertWidthInCharsToPixels(40);
		fDescriptionFileText.setLayoutData(data);

		// destination browse button
		fDescriptionFileBrowseButton= new Button(fDescriptionFileGroup, SWT.PUSH);
		fDescriptionFileBrowseButton.setText(JarPackagerMessages.JarOptionsPage_browseButton_text);
		fDescriptionFileBrowseButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		SWTUtil.setButtonDimensionHint(fDescriptionFileBrowseButton);
		fDescriptionFileBrowseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleDescriptionFileBrowseButtonPressed();
			}
		});
	}

	/**
	 * Creates a file resource handle for the file with the given workspace path.
	 * This method does not create the file resource; this is the responsibility
	 * of <code>createFile</code>.
	 *
	 * @param filePath the path of the file resource to create a handle for
	 * @return the new file resource handle
	 */
	protected IFile createFileHandle(IPath filePath) {
		if (filePath.isValidPath(filePath.toString()) && filePath.segmentCount() >= 2)
			return JavaPlugin.getWorkspace().getRoot().getFile(filePath);
		else
			return null;
	}
	/*
	 * Method declared on IWizardPage.
	 */
	@Override
	public void setPreviousPage(IWizardPage page) {
		super.setPreviousPage(page);
		updateWidgetEnablements();
		if (getControl() != null)
			updatePageCompletion();
	}

	/*
	 * Implements method from IJarPackageWizardPage.
	 */
	public void finish() {
		saveWidgetValues();
	}

	/**
	 * Creates a new label with a bold font.
	 *
	 * @param parent the parent control
	 * @param text the label text
	 * @param bold bold or not
	 * @return the new label control
	 */
	protected Label createLabel(Composite parent, String text, boolean bold) {
		Label label= new Label(parent, SWT.NONE);
		if (bold)
			label.setFont(JFaceResources.getBannerFont());
		label.setText(text);
		GridData data= new GridData();
		data.verticalAlignment= GridData.FILL;
		data.horizontalAlignment= GridData.FILL;
		label.setLayoutData(data);
		return label;
	}

	/**
	 * Creates a horizontal spacer line that fills the width of its container.
	 *
	 * @param parent the parent control
	 */
	protected void createSpacer(Composite parent) {
		Label spacer= new Label(parent, SWT.NONE);
		GridData data= new GridData();
		data.horizontalAlignment= GridData.FILL;
		data.verticalAlignment= GridData.BEGINNING;
		spacer.setLayoutData(data);
	}

	/**
	 * Determine if the page is complete and update the page appropriately.
	 */
	protected void updatePageCompletion() {
		boolean pageComplete= isPageComplete();
		setPageComplete(pageComplete);
		if (pageComplete) {
			setErrorMessage(null);
		}
	}

	/**
	 * Returns the resource for the specified path.
	 *
	 * @param path	the path for which the resource should be returned
	 * @return the resource specified by the path or <code>null</code>
	 */
	protected IResource findResource(IPath path) {
		IWorkspace workspace= JavaPlugin.getWorkspace();
		IStatus result= workspace.validatePath(
							path.toString(),
							IResource.ROOT | IResource.PROJECT | IResource.FOLDER | IResource.FILE);
		if (result.isOK() && workspace.getRoot().exists(path))
			return workspace.getRoot().findMember(path);
		return null;
	}
}

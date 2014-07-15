/*******************************************************************************
 * Copyright (c) 2007, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 83258 [jar exporter] Deploy java application as executable jar
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackager;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;

import org.eclipse.core.filesystem.URIUtil;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.dialogs.WizardExportResourcesPage;

import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.ArchiveFileFilter;

/**
 * A wizard page containing a destination block for a jar file. Including
 * all required validation code. Clients should overwrite <code>createControl</code>.
 *
 * @since 3.4
 */
public abstract class AbstractJarDestinationWizardPage extends WizardExportResourcesPage implements IJarPackageWizardPage {

	private final String fStoreDestinationNamesId;

	private Combo fDestinationNamesCombo;
	private Button fDestinationBrowseButton;
	private final JarPackageData fJarPackage;

	public AbstractJarDestinationWizardPage(String pageName, IStructuredSelection selection, JarPackageData jarPackage) {
		super(pageName, selection);
		fStoreDestinationNamesId= pageName + ".DESTINATION_NAMES_ID"; //$NON-NLS-1$
		fJarPackage= jarPackage;
	}

	/*
	 * Overrides method from WizardExportPage
	 */
	@Override
	protected void createDestinationGroup(Composite parent) {

		initializeDialogUnits(parent);

		// destination specification group
		Composite destinationSelectionGroup= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.numColumns= 3;
		destinationSelectionGroup.setLayout(layout);
		destinationSelectionGroup.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL));

		String label= getDestinationLabel();
		if (label != null) {
			new Label(destinationSelectionGroup, SWT.NONE).setText(label);
		} else {
			layout.marginWidth= 0;
			layout.marginHeight= 0;
		}

		// destination name entry field
		fDestinationNamesCombo= new Combo(destinationSelectionGroup, SWT.SINGLE | SWT.BORDER);
		SWTUtil.setDefaultVisibleItemCount(fDestinationNamesCombo);
		fDestinationNamesCombo.addListener(SWT.Modify, this);
		fDestinationNamesCombo.addListener(SWT.Selection, this);
		GridData data= new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL);
		data.widthHint= SIZING_TEXT_FIELD_WIDTH;
		data.horizontalSpan= label == null ? 2 : 1;
		fDestinationNamesCombo.setLayoutData(data);
		if (label == null) {
			SWTUtil.setAccessibilityText(fDestinationNamesCombo, JarPackagerMessages.AbstractJarDestinationWizardPage_destinationCombo_AccessibilityText);
		}

		// destination browse button
		fDestinationBrowseButton= new Button(destinationSelectionGroup, SWT.PUSH);
		fDestinationBrowseButton.setText(JarPackagerMessages.JarPackageWizardPage_browseButton_text);
		fDestinationBrowseButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		SWTUtil.setButtonDimensionHint(fDestinationBrowseButton);
		fDestinationBrowseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleDestinationBrowseButtonPressed();
			}
		});
	}

	/**
	 *	Open an appropriate destination browser so that the user can specify a source
	 *	to import from
	 */
	protected void handleDestinationBrowseButtonPressed() {
		FileDialog dialog= new FileDialog(getContainer().getShell(), SWT.SAVE);
		dialog.setFilterExtensions(ArchiveFileFilter.JAR_ZIP_FILTER_EXTENSIONS);

		String currentSourceString= getDestinationValue();
		int lastSeparatorIndex= currentSourceString.lastIndexOf(File.separator);
		if (lastSeparatorIndex != -1) {
			dialog.setFilterPath(currentSourceString.substring(0, lastSeparatorIndex));
			dialog.setFileName(currentSourceString.substring(lastSeparatorIndex + 1, currentSourceString.length()));
		}
		String selectedFileName= dialog.open();
		if (selectedFileName != null) {
			IContainer[] findContainersForLocation= ResourcesPlugin.getWorkspace().getRoot().findContainersForLocationURI(URIUtil.toURI(new Path(selectedFileName).makeAbsolute()));
			if (findContainersForLocation.length > 0) {
				selectedFileName= findContainersForLocation[0].getFullPath().makeRelative().toString();
			}
			fDestinationNamesCombo.setText(selectedFileName);
		}
	}


	/**
	 * Answer the contents of the destination specification widget. If this
	 * value does not have the required suffix then add it first.
	 *
	 * @return java.lang.String
	 */
	protected String getDestinationValue() {
		String destinationText= fDestinationNamesCombo.getText().trim();
		if (destinationText.indexOf('.') < 0)
			destinationText+= getOutputSuffix();
		return destinationText;
	}

	/**
	 *	Answer the string to display in self as the destination type
	 *
	 *	@return java.lang.String
	 */
	protected String getDestinationLabel() {
		return JarPackagerMessages.JarPackageWizardPage_destination_label;
	}

	/**
	 *	Answer the suffix that files exported from this wizard must have.
	 *	If this suffix is a file extension (which is typically the case)
	 *	then it must include the leading period character.
	 *
	 *	@return java.lang.String
	 */
	protected String getOutputSuffix() {
		return "." + JarPackagerUtil.JAR_EXTENSION; //$NON-NLS-1$
	}

	@Override
	protected void restoreWidgetValues() {
		// destination
		if (fJarPackage.getJarLocation().isEmpty())
			fDestinationNamesCombo.setText(""); //$NON-NLS-1$
		else
			fDestinationNamesCombo.setText(fJarPackage.getJarLocation().toOSString());
		IDialogSettings settings= getDialogSettings();
		if (settings != null) {
			String[] directoryNames= settings.getArray(fStoreDestinationNamesId);
			if (directoryNames == null)
				return; // ie.- no settings stored
			if (!fDestinationNamesCombo.getText().equals(directoryNames[0]))
				fDestinationNamesCombo.add(fDestinationNamesCombo.getText());
			for (int i= 0; i < directoryNames.length; i++)
				fDestinationNamesCombo.add(directoryNames[i]);
		}
	}

	protected void updateModel() {
		// destination
		String comboText= fDestinationNamesCombo.getText();
		IPath path= Path.fromOSString(comboText);

		if (path.segmentCount() > 0 && ensureTargetFileIsValid(path.toFile()) && path.getFileExtension() == null)
			// append .jar
			path= path.addFileExtension(JarPackagerUtil.JAR_EXTENSION);

		fJarPackage.setJarLocation(path);
	}

	/**
	 * Returns a boolean indicating whether the passed File handle is
	 * is valid and available for use.
	 *
	 * @param targetFile the target
	 * @return boolean
	 */
	protected boolean ensureTargetFileIsValid(File targetFile) {
		if (targetFile.exists() && targetFile.isDirectory() && fDestinationNamesCombo.getText().length() > 0) {
			setErrorMessage(JarPackagerMessages.JarPackageWizardPage_error_exportDestinationMustNotBeDirectory);
			fDestinationNamesCombo.setFocus();
			return false;
		}
		if (targetFile.exists()) {
			if (!targetFile.canWrite()) {
				setErrorMessage(JarPackagerMessages.JarPackageWizardPage_error_jarFileExistsAndNotWritable);
				fDestinationNamesCombo.setFocus();
				return false;
			}
		}
		return true;
	}

	/*
	 * Overrides method from WizardDataTransferPage
	 */
	@Override
	protected boolean validateDestinationGroup() {
		if (fDestinationNamesCombo.getText().length() == 0) {
			// Clear error
			if (getErrorMessage() != null)
				setErrorMessage(null);
			if (getMessage() != null)
				setMessage(null);
			return false;
		}
		if (fJarPackage.getAbsoluteJarLocation().toString().endsWith("/")) { //$NON-NLS-1$
			setErrorMessage(JarPackagerMessages.JarPackageWizardPage_error_exportDestinationMustNotBeDirectory);
			fDestinationNamesCombo.setFocus();
			return false;
		}
		// Check if the Jar is put into the workspace and conflicts with the containers
		// exported. If the workspace isn't on the local files system we are fine since
		// the Jar is always created in the local file system
		IPath workspaceLocation= ResourcesPlugin.getWorkspace().getRoot().getLocation();
		if (workspaceLocation != null && workspaceLocation.isPrefixOf(fJarPackage.getAbsoluteJarLocation())) {
			int segments= workspaceLocation.matchingFirstSegments(fJarPackage.getAbsoluteJarLocation());
			IPath path= fJarPackage.getAbsoluteJarLocation().removeFirstSegments(segments);
			IResource resource= ResourcesPlugin.getWorkspace().getRoot().findMember(path);
			if (resource != null && resource.getType() == IResource.FILE) {
				// test if included
				if (JarPackagerUtil.contains(JarPackagerUtil.asResources(fJarPackage.getElements()), (IFile) resource)) {
					setErrorMessage(JarPackagerMessages.JarPackageWizardPage_error_cantExportJARIntoItself);
					return false;
				}
			}
		}
		// Inform user about relative directory
		String currentMessage= getMessage();
		if (!(Path.fromOSString(fDestinationNamesCombo.getText()).isAbsolute())) {
			if (currentMessage == null)
				setMessage(JarPackagerMessages.JarPackageWizardPage_info_relativeExportDestination, IMessageProvider.INFORMATION);
		} else {
			if (currentMessage != null)
				setMessage(null);
		}
		return ensureTargetFileIsValid(fJarPackage.getAbsoluteJarLocation().toFile());
	}

	/**
	 * Set the current input focus to self's destination entry field
	 */
	protected void giveFocusToDestination() {
		fDestinationNamesCombo.setFocus();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveWidgetValues() {
		IDialogSettings settings= getDialogSettings();
		if (settings != null) {
			String[] directoryNames= settings.getArray(fStoreDestinationNamesId);
			if (directoryNames == null)
				directoryNames= new String[0];
			directoryNames= addToHistory(directoryNames, getDestinationValue());
			settings.put(fStoreDestinationNamesId, directoryNames);
		}
	}

	/**
	 *	Initializes the JAR package from last used wizard page values.
	 */
	protected void initializeJarPackage() {
		IDialogSettings settings= getDialogSettings();
		if (settings != null) {
			// destination
			String[] directoryNames= settings.getArray(fStoreDestinationNamesId);
			if (directoryNames == null)
				return; // ie.- no settings stored
			fJarPackage.setJarLocation(Path.fromOSString(directoryNames[0]));
		}
	}

	/*
	 * Implements method from IJarPackageWizardPage.
	 */
	public void finish() {
		saveWidgetValues();
	}

	/*
	 * Implements method from Listener
	 */
	public void handleEvent(Event e) {
		if (getControl() == null)
			return;
		update();
	}

	protected void update() {
		updateModel();
		updateWidgetEnablements();
		updatePageCompletion();
	}

	@Override
	protected void updatePageCompletion() {
		boolean pageComplete= isPageComplete();
		setPageComplete(pageComplete);
		if (pageComplete)
			setErrorMessage(null);
	}
}

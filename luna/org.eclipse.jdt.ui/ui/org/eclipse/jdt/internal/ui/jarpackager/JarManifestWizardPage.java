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
package org.eclipse.jdt.internal.ui.jarpackager;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.dialogs.SaveAsDialog;
import org.eclipse.ui.dialogs.SelectionDialog;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchScope;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.ProblemsLabelDecorator;
import org.eclipse.jdt.ui.StandardJavaElementContentProvider;
import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.filters.EmptyInnerPackageFilter;
import org.eclipse.jdt.internal.ui.search.JavaSearchScopeFactory;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.MainMethodSearchEngine;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.viewsupport.LibraryFilter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;

/**
 *	Page 3 of the JAR Package wizard
 */
class JarManifestWizardPage extends WizardPage implements IJarPackageWizardPage {

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
	private UntypedListener fUntypedListener= new UntypedListener();

	// Model
	private JarPackageData fJarPackage;

	// Cache for main types
	private IType[] fMainTypes;

	// Widgets
	private Composite	fManifestGroup;
	private Button		fGenerateManifestRadioButton;
	private Button		fSaveManifestCheckbox;
	private Button		fReuseManifestCheckbox;
	private Text		fNewManifestFileText;
	private Label		fNewManifestFileLabel;
	private Button		fNewManifestFileBrowseButton;
	private Button		fUseManifestRadioButton;
	private Text		fManifestFileText;
	private Label		fManifestFileLabel;
	private Button		fManifestFileBrowseButton;

	private Label		fSealingHeaderLabel;
	private Button		fSealJarRadioButton;
	private Label		fSealJarLabel;
	private Button		fSealedPackagesDetailsButton;
	private Button		fSealPackagesRadioButton;
	private Label		fSealPackagesLabel;
	private Button		fUnSealedPackagesDetailsButton;

	private Label		fMainClassHeaderLabel;
	private Label		fMainClassLabel;
	private Text		fMainClassText;
	private Button		fMainClassBrowseButton;

	// Dialog store id constants
	private final static String PAGE_NAME= "JarManifestWizardPage"; //$NON-NLS-1$

	// Manifest creation
	private final static String STORE_GENERATE_MANIFEST= PAGE_NAME + ".GENERATE_MANIFEST"; //$NON-NLS-1$
	private final static String STORE_SAVE_MANIFEST= PAGE_NAME + ".SAVE_MANIFEST"; //$NON-NLS-1$
	private final static String STORE_REUSE_MANIFEST= PAGE_NAME + ".REUSE_MANIFEST"; //$NON-NLS-1$
	private final static String STORE_MANIFEST_LOCATION= PAGE_NAME + ".MANIFEST_LOCATION"; //$NON-NLS-1$

	// Sealing
	private final static String STORE_SEAL_JAR= PAGE_NAME + ".SEAL_JAR"; //$NON-NLS-1$

	/**
	 * Creates an instance of this class.
	 * 
	 * @param jarPackage the JAR package data
	 */
	public JarManifestWizardPage(JarPackageData jarPackage) {
		super(PAGE_NAME);
		setTitle(JarPackagerMessages.JarManifestWizardPage_title);
		setDescription(JarPackagerMessages.JarManifestWizardPage_description);
		fJarPackage= jarPackage;
	}

	// ----------- Widget creation  -----------

	/*
	 * Method declared on IDialogPage.
	 */
	public void createControl(Composite parent) {

		initializeDialogUnits(parent);

		Composite composite= new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));

		createLabel(composite, JarPackagerMessages.JarManifestWizardPage_manifestSource_label, false);
		createManifestGroup(composite);

		createSpacer(composite);

		fSealingHeaderLabel= createLabel(composite, JarPackagerMessages.JarManifestWizardPage_sealingHeader_label, false);
		createSealingGroup(composite);

		createSpacer(composite);

		fMainClassHeaderLabel= createLabel(composite, JarPackagerMessages.JarManifestWizardPage_mainClassHeader_label, false);
		createMainClassGroup(composite);

		setEqualButtonSizes();

		restoreWidgetValues();

		setControl(composite);
		update();

		Dialog.applyDialogFont(composite);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IJavaHelpContextIds.JARMANIFEST_WIZARD_PAGE);

	}
	/**
	 *	Create the export options specification widgets.
	 *
	 *	@param parent org.eclipse.swt.widgets.Composite
	 */
	protected void createManifestGroup(Composite parent) {
		fManifestGroup= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		fManifestGroup.setLayout(layout);
		fManifestGroup.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));

		fGenerateManifestRadioButton= new Button(fManifestGroup, SWT.RADIO | SWT.LEFT);
		fGenerateManifestRadioButton.setText(JarPackagerMessages.JarManifestWizardPage_genetateManifest_text);
		fGenerateManifestRadioButton.addListener(SWT.Selection, fUntypedListener);

		Composite saveOptions= new Composite(fManifestGroup, SWT.NONE);
		GridLayout saveOptionsLayout= new GridLayout();
		saveOptionsLayout.marginWidth= 0;
		saveOptions.setLayout(saveOptionsLayout);

		GridData data= new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL);
		data.horizontalIndent= LayoutUtil.getIndent();
		saveOptions.setLayoutData(data);

		fSaveManifestCheckbox= new Button(saveOptions, SWT.CHECK | SWT.LEFT);
		fSaveManifestCheckbox.setText(JarPackagerMessages.JarManifestWizardPage_saveManifest_text);
		fSaveManifestCheckbox.addListener(SWT.MouseUp, fUntypedListener);

		fReuseManifestCheckbox= new Button(saveOptions, SWT.CHECK | SWT.LEFT);
		fReuseManifestCheckbox.setText(JarPackagerMessages.JarManifestWizardPage_reuseManifest_text);
		fReuseManifestCheckbox.addListener(SWT.MouseUp, fUntypedListener);

		createNewManifestFileGroup(saveOptions);

		fUseManifestRadioButton= new Button(fManifestGroup, SWT.RADIO | SWT.LEFT);
		fUseManifestRadioButton.setText(JarPackagerMessages.JarManifestWizardPage_useManifest_text);

		fUseManifestRadioButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		Composite existingManifestGroup= new Composite(fManifestGroup, SWT.NONE);
		GridLayout existingManifestLayout= new GridLayout();
		existingManifestLayout.marginWidth= 0;
		existingManifestGroup.setLayout(existingManifestLayout);
		data= new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL);
		data.horizontalIndent= LayoutUtil.getIndent();
		existingManifestGroup.setLayoutData(data);
		createManifestFileGroup(existingManifestGroup);
	}

	protected void createNewManifestFileGroup(Composite parent) {
		// destination specification group
		Composite manifestFileGroup= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginWidth= 0;
		layout.numColumns= 3;
		manifestFileGroup.setLayout(layout);
		manifestFileGroup.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));

		fNewManifestFileLabel= new Label(manifestFileGroup, SWT.NONE);
		fNewManifestFileLabel.setText(JarPackagerMessages.JarManifestWizardPage_newManifestFile_text);

		// entry field
		fNewManifestFileText= new Text(manifestFileGroup, SWT.SINGLE | SWT.BORDER);
		fNewManifestFileText.addListener(SWT.Modify, fUntypedListener);

		GridData data= new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL);
		data.widthHint= convertWidthInCharsToPixels(40);
		fNewManifestFileText.setLayoutData(data);

		// browse button
		fNewManifestFileBrowseButton= new Button(manifestFileGroup, SWT.PUSH);
		fNewManifestFileBrowseButton.setText(JarPackagerMessages.JarManifestWizardPage_newManifestFileBrowseButton_text);
		fNewManifestFileBrowseButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		fNewManifestFileBrowseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleNewManifestFileBrowseButtonPressed();
			}
		});
	}

	protected void createManifestFileGroup(Composite parent) {
		// destination specification group
		Composite manifestFileGroup= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.numColumns= 3;
		layout.marginWidth= 0;

		manifestFileGroup.setLayout(layout);
		manifestFileGroup.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));

		fManifestFileLabel= new Label(manifestFileGroup, SWT.NONE);
		fManifestFileLabel.setText(JarPackagerMessages.JarManifestWizardPage_manifestFile_text);

		// entry field
		fManifestFileText= new Text(manifestFileGroup, SWT.SINGLE | SWT.BORDER);
		fManifestFileText.addListener(SWT.Modify, fUntypedListener);
		GridData data= new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL);
		data.widthHint= convertWidthInCharsToPixels(40);
		fManifestFileText.setLayoutData(data);

		// browse button
		fManifestFileBrowseButton= new Button(manifestFileGroup, SWT.PUSH);
		fManifestFileBrowseButton.setText(JarPackagerMessages.JarManifestWizardPage_manifestFileBrowse_text);
		fManifestFileBrowseButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		fManifestFileBrowseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleManifestFileBrowseButtonPressed();
			}
		});
	}
	/**
	 * Creates the JAR sealing specification controls.
	 *
	 * @param parent the parent control
	 */
	protected void createSealingGroup(Composite parent) {
		// destination specification group
		Composite sealingGroup= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.numColumns= 3;
		layout.horizontalSpacing += 3;
		sealingGroup.setLayout(layout);
		sealingGroup.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL));
		createSealJarGroup(sealingGroup);
		createSealPackagesGroup(sealingGroup);

	}
	/**
	 * Creates the JAR sealing specification controls to seal the whole JAR.
	 *
	 * @param sealGroup the parent control
	 */
	protected void createSealJarGroup(Composite sealGroup) {
		fSealJarRadioButton= new Button(sealGroup, SWT.RADIO);
		fSealJarRadioButton.setText(JarPackagerMessages.JarManifestWizardPage_sealJar_text);
		fSealJarRadioButton.addListener(SWT.Selection, fUntypedListener);

		fSealJarLabel= new Label(sealGroup, SWT.RIGHT);
		fSealJarLabel.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
		fSealJarLabel.setText(""); //$NON-NLS-1$

		fUnSealedPackagesDetailsButton= new Button(sealGroup, SWT.PUSH);
		fUnSealedPackagesDetailsButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		fUnSealedPackagesDetailsButton.setText(JarPackagerMessages.JarManifestWizardPage_unsealPackagesButton_text);
		fUnSealedPackagesDetailsButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleUnSealPackagesDetailsButtonPressed();
			}
		});

	}
	/**
	 * Creates the JAR sealing specification controls to seal packages.
	 *
	 * @param sealGroup the parent control
	 */
	protected void createSealPackagesGroup(Composite sealGroup) {
		fSealPackagesRadioButton= new Button(sealGroup, SWT.RADIO);
		fSealPackagesRadioButton.setText(JarPackagerMessages.JarManifestWizardPage_sealPackagesButton_text);

		fSealPackagesLabel= new Label(sealGroup, SWT.RIGHT);
		fSealPackagesLabel.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
		fSealPackagesLabel.setText(""); //$NON-NLS-1$

		fSealedPackagesDetailsButton= new Button(sealGroup, SWT.PUSH);
		fSealedPackagesDetailsButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		fSealedPackagesDetailsButton.setText(JarPackagerMessages.JarManifestWizardPage_sealedPackagesDetailsButton_text);
		fSealedPackagesDetailsButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleSealPackagesDetailsButtonPressed();
			}
		});
	}

	protected void createMainClassGroup(Composite parent) {
		// main type group
		Composite mainClassGroup= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.numColumns= 3;
		mainClassGroup.setLayout(layout);
		mainClassGroup.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL));

		fMainClassLabel= new Label(mainClassGroup, SWT.NONE);
		fMainClassLabel.setText(JarPackagerMessages.JarManifestWizardPage_mainClass_label);

		// entry field
		fMainClassText= new Text(mainClassGroup, SWT.SINGLE | SWT.BORDER);
		fMainClassText.addListener(SWT.Modify, fUntypedListener);
		GridData data= new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL);
		data.widthHint= convertWidthInCharsToPixels(40);
		fMainClassText.setLayoutData(data);
		fMainClassText.addKeyListener(new KeyAdapter() {
			/*
			 * @see KeyListener#keyReleased(KeyEvent)
			 */
			@Override
			public void keyReleased(KeyEvent e) {
				fJarPackage.setManifestMainClass(findMainMethodByName(fMainClassText.getText()));
				update();
			}
		});

		// browse button
		fMainClassBrowseButton= new Button(mainClassGroup, SWT.PUSH);
		fMainClassBrowseButton.setText(JarPackagerMessages.JarManifestWizardPage_mainClassBrowseButton_text);
		fMainClassBrowseButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		fMainClassBrowseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleMainClassBrowseButtonPressed();
			}
		});
	}

	// ----------- Event handlers  -----------

	private void update() {
		updateModel();
		updateEnableState();
		updatePageCompletion();
	}

	/**
	 *	Open an appropriate dialog so that the user can specify a manifest
	 *	to save
	 */
	protected void handleNewManifestFileBrowseButtonPressed() {
		// Use Save As dialog to select a new file inside the workspace
		SaveAsDialog dialog= new SaveAsDialog(getContainer().getShell());
		dialog.create();
		dialog.getShell().setText(JarPackagerMessages.JarManifestWizardPage_saveAsDialog_title);
		dialog.setMessage(JarPackagerMessages.JarManifestWizardPage_saveAsDialog_message);
		dialog.setOriginalFile(createFileHandle(fJarPackage.getManifestLocation()));
		if (dialog.open() == Window.OK) {
			fJarPackage.setManifestLocation(dialog.getResult());
			fNewManifestFileText.setText(dialog.getResult().toString());
		}
	}

	protected void handleManifestFileBrowseButtonPressed() {
		ElementTreeSelectionDialog dialog= createWorkspaceFileSelectionDialog(JarPackagerMessages.JarManifestWizardPage_manifestSelectionDialog_title, JarPackagerMessages.JarManifestWizardPage_manifestSelectionDialog_message);
		if (fJarPackage.isManifestAccessible())
			dialog.setInitialSelections(new IResource[] {fJarPackage.getManifestFile()});
		if (dialog.open() ==  Window.OK) {
			Object[] resources= dialog.getResult();
			if (resources.length != 1)
				setErrorMessage(JarPackagerMessages.JarManifestWizardPage_error_onlyOneManifestMustBeSelected);
			else {
				setErrorMessage(""); //$NON-NLS-1$
				fJarPackage.setManifestLocation(((IResource)resources[0]).getFullPath());
				fManifestFileText.setText(fJarPackage.getManifestLocation().toString());
			}
		}
	}

	private IType findMainMethodByName(String name) {
		if (fMainTypes == null) {
			List<IResource> resources= JarPackagerUtil.asResources(fJarPackage.getElements());
			if (resources == null) {
				setErrorMessage(JarPackagerMessages.JarManifestWizardPage_error_noResourceSelected);
				return null;
			}
			IJavaSearchScope searchScope= JavaSearchScopeFactory.getInstance().createJavaSearchScope(resources.toArray(new IResource[resources.size()]), true);
			MainMethodSearchEngine engine= new MainMethodSearchEngine();
			try {
				fMainTypes= engine.searchMainMethods(getContainer(), searchScope, 0);
			} catch (InvocationTargetException ex) {
				// null
			} catch (InterruptedException e) {
				// null
			}
		}
		for (int i= 0; i < fMainTypes.length; i++) {
			if (fMainTypes[i].getFullyQualifiedName().equals(name))
			 return fMainTypes[i];
		}
		return null;
	}

	protected void handleMainClassBrowseButtonPressed() {
		List<IResource> resources= JarPackagerUtil.asResources(fJarPackage.getElements());
		if (resources == null) {
			setErrorMessage(JarPackagerMessages.JarManifestWizardPage_error_noResourceSelected);
			return;
		}
		IJavaSearchScope searchScope= JavaSearchScopeFactory.getInstance().createJavaSearchScope(resources.toArray(new IResource[resources.size()]), true);
		SelectionDialog dialog= JavaUI.createMainTypeDialog(getContainer().getShell(), getContainer(), searchScope, 0, false, ""); //$NON-NLS-1$
		dialog.setTitle(JarPackagerMessages.JarManifestWizardPage_mainTypeSelectionDialog_title);
		dialog.setMessage(JarPackagerMessages.JarManifestWizardPage_mainTypeSelectionDialog_message);
		if (fJarPackage.getManifestMainClass() != null)
			dialog.setInitialSelections(new Object[] {fJarPackage.getManifestMainClass()});

		if (dialog.open() == Window.OK) {
			fJarPackage.setManifestMainClass((IType)dialog.getResult()[0]);
			fMainClassText.setText(JarPackagerUtil.getMainClassName(fJarPackage));
		} else if (!fJarPackage.isMainClassValid(getContainer())) {
			// user did not cancel: no types were found
			fJarPackage.setManifestMainClass(null);
			fMainClassText.setText(JarPackagerUtil.getMainClassName(fJarPackage));
		}
	}

	protected void handleSealPackagesDetailsButtonPressed() {
		SelectionDialog dialog= createPackageDialog(getPackagesForSelectedResources());
		dialog.setTitle(JarPackagerMessages.JarManifestWizardPage_sealedPackagesSelectionDialog_title);
		dialog.setMessage(JarPackagerMessages.JarManifestWizardPage_sealedPackagesSelectionDialog_message);
		dialog.setInitialSelections(fJarPackage.getPackagesToSeal());
		if (dialog.open() == Window.OK)
			fJarPackage.setPackagesToSeal(getPackagesFromDialog(dialog));
		updateSealingInfo();
	}

	protected void handleUnSealPackagesDetailsButtonPressed() {
		SelectionDialog dialog= createPackageDialog(getPackagesForSelectedResources());
		dialog.setTitle(JarPackagerMessages.JarManifestWizardPage_unsealedPackagesSelectionDialog_title);
		dialog.setMessage(JarPackagerMessages.JarManifestWizardPage_unsealedPackagesSelectionDialog_message);
		dialog.setInitialSelections(fJarPackage.getPackagesToUnseal());
		if (dialog.open() == Window.OK)
			fJarPackage.setPackagesToUnseal(getPackagesFromDialog(dialog));
		updateSealingInfo();
	}
	/**
	 * Updates the enable state of this page's controls. Subclasses may extend.
	 */
	protected void updateEnableState() {
		boolean generate= fGenerateManifestRadioButton.getSelection();

		boolean save= generate && fSaveManifestCheckbox.getSelection();
		fSaveManifestCheckbox.setEnabled(generate);
		fReuseManifestCheckbox.setEnabled(fJarPackage.isDescriptionSaved() && save);
		fNewManifestFileText.setEnabled(save);
		fNewManifestFileLabel.setEnabled(save);
		fNewManifestFileBrowseButton.setEnabled(save);

		fManifestFileText.setEnabled(!generate);
		fManifestFileLabel.setEnabled(!generate);
		fManifestFileBrowseButton.setEnabled(!generate);

		fSealingHeaderLabel.setEnabled(generate);
		boolean sealState= fSealJarRadioButton.getSelection();
		fSealJarRadioButton.setEnabled(generate);
		fSealJarLabel.setEnabled(generate);
		fUnSealedPackagesDetailsButton.setEnabled(sealState && generate);
		fSealPackagesRadioButton.setEnabled(generate);
		fSealPackagesLabel.setEnabled(generate);
		fSealedPackagesDetailsButton.setEnabled(!sealState && generate);

		fMainClassHeaderLabel.setEnabled(generate);
		fMainClassLabel.setEnabled(generate);
		fMainClassText.setEnabled(generate);
		fMainClassBrowseButton.setEnabled(generate);

		updateSealingInfo();
	}

	protected void updateSealingInfo() {
		if (fJarPackage.isJarSealed()) {
			fSealPackagesLabel.setText(""); //$NON-NLS-1$
			int i= fJarPackage.getPackagesToUnseal().length;
			if (i == 0)
				fSealJarLabel.setText(JarPackagerMessages.JarManifestWizardPage_jarSealed);
			else if (i == 1)
				fSealJarLabel.setText(JarPackagerMessages.JarManifestWizardPage_jarSealedExceptOne);
			else
				fSealJarLabel.setText(Messages.format(JarPackagerMessages.JarManifestWizardPage_jarSealedExceptSome, new Integer(i)));

		}
		else {
			fSealJarLabel.setText(""); //$NON-NLS-1$
			int i= fJarPackage.getPackagesToSeal().length;
			if (i == 0)
				fSealPackagesLabel.setText(JarPackagerMessages.JarManifestWizardPage_nothingSealed);
			else if (i == 1)
				fSealPackagesLabel.setText(JarPackagerMessages.JarManifestWizardPage_onePackageSealed);
			else
				fSealPackagesLabel.setText(Messages.format(JarPackagerMessages.JarManifestWizardPage_somePackagesSealed, new Integer(i)));
		}
	}
	/*
	 * Implements method from IJarPackageWizardPage
	 */
	@Override
	public boolean isPageComplete() {
		boolean isPageComplete= true;
		setMessage(null);

		if (!fJarPackage.areGeneratedFilesExported())
			return true;

		if (fJarPackage.isManifestGenerated() && fJarPackage.isManifestSaved()) {
			if (fJarPackage.getManifestLocation().toString().length() == 0)
					isPageComplete= false;
			else {
				IPath location= fJarPackage.getManifestLocation();
				if (!location.toString().startsWith("/")) { //$NON-NLS-1$
					setErrorMessage(JarPackagerMessages.JarManifestWizardPage_error_manifestPathMustBeAbsolute);
					return false;
				}
				IResource resource= findResource(location);
				if (resource != null && resource.getType() != IResource.FILE) {
					setErrorMessage(JarPackagerMessages.JarManifestWizardPage_error_manifestMustNotBeExistingContainer);
					return false;
				}
				resource= findResource(location.removeLastSegments(1));
				if (resource == null || resource.getType() == IResource.FILE) {
					setErrorMessage(JarPackagerMessages.JarManifestWizardPage_error_manifestContainerDoesNotExist);
					return false;
				}
			}
		}
		if (!fJarPackage.isManifestGenerated()) {
			if (fJarPackage.isManifestAccessible()) {
				Manifest manifest= null;
				try {
					manifest= fJarPackage.getManifestProvider().create(fJarPackage);
				} catch (CoreException ex) {
					// nothing reported in the wizard
				}
				if (manifest != null && manifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION) == null)
					setMessage(JarPackagerMessages.JarManifestWizardPage_warning_noManifestVersion, IMessageProvider.WARNING);
			} else {
				if (fJarPackage.getManifestLocation().toString().length() == 0)
					setErrorMessage(JarPackagerMessages.JarManifestWizardPage_error_noManifestFile);
				else
					setErrorMessage(JarPackagerMessages.JarManifestWizardPage_error_invalidManifestFile);
				return false;
			}
		}
		Set<IJavaElement> selectedPackages= getPackagesForSelectedResources();
		if (fJarPackage.isJarSealed()
				&& !selectedPackages.containsAll(Arrays.asList(fJarPackage.getPackagesToUnseal()))) {
			setErrorMessage(JarPackagerMessages.JarManifestWizardPage_error_unsealedPackagesNotInSelection);
			return false;
		}
		if (!fJarPackage.isJarSealed()
				&& !selectedPackages.containsAll(Arrays.asList(fJarPackage.getPackagesToSeal()))) {
			setErrorMessage(JarPackagerMessages.JarManifestWizardPage_error_sealedPackagesNotInSelection);
			return false;
		}
		if (!fJarPackage.isMainClassValid(getContainer()) || (fJarPackage.getManifestMainClass() == null && fMainClassText.getText().length() > 0)) {
			setErrorMessage(JarPackagerMessages.JarManifestWizardPage_error_invalidMainClass);
			return false;
		}

		setErrorMessage(null);
		return isPageComplete;
	}

	/*
	 * Implements method from IWizardPage.
	 */
	@Override
	public void setPreviousPage(IWizardPage page) {
		super.setPreviousPage(page);
		fMainTypes= null;
		updateEnableState();
		if (getContainer() != null)
			updatePageCompletion();
	}

	/*
	 * Implements method from IJarPackageWizardPage.
	 */
	public void finish() {
		saveWidgetValues();
	}

	// ----------- Model handling -----------

	/**
	 * Persists resource specification control setting that are to be restored
	 * in the next instance of this page. Subclasses wishing to persist
	 * settings for their controls should extend the hook method
	 * <code>internalSaveWidgetValues</code>.
	 */
	public final void saveWidgetValues() {
		IDialogSettings settings= getDialogSettings();
		if (settings != null) {
			// Manifest creation
			settings.put(STORE_GENERATE_MANIFEST, fJarPackage.isManifestGenerated());
			settings.put(STORE_SAVE_MANIFEST, fJarPackage.isManifestSaved());
			settings.put(STORE_REUSE_MANIFEST, fJarPackage.isManifestReused());
			settings.put(STORE_MANIFEST_LOCATION, fJarPackage.getManifestLocation().toString());

			// Sealing
			settings.put(STORE_SEAL_JAR, fJarPackage.isJarSealed());
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

		// Manifest creation
		if (fJarPackage.isManifestGenerated())
			fGenerateManifestRadioButton.setSelection(true);
		else
			fUseManifestRadioButton.setSelection(true);
		fSaveManifestCheckbox.setSelection(fJarPackage.isManifestSaved());
		fReuseManifestCheckbox.setSelection(fJarPackage.isManifestReused());
		fManifestFileText.setText(fJarPackage.getManifestLocation().toString());
		fNewManifestFileText.setText(fJarPackage.getManifestLocation().toString());

		// Sealing
		if (fJarPackage.isJarSealed())
			fSealJarRadioButton.setSelection(true);
		else
			fSealPackagesRadioButton.setSelection(true);

		// Main-Class
		fMainClassText.setText(JarPackagerUtil.getMainClassName(fJarPackage));
	}
	/**
	 *	Initializes the JAR package from last used wizard page values.
	 */
	protected void initializeJarPackage() {
		IDialogSettings settings= getDialogSettings();
		if (settings != null) {
			// Manifest creation
			fJarPackage.setGenerateManifest(settings.getBoolean(STORE_GENERATE_MANIFEST));
			fJarPackage.setSaveManifest(settings.getBoolean(STORE_SAVE_MANIFEST));
			fJarPackage.setReuseManifest(settings.getBoolean(STORE_REUSE_MANIFEST));
			String pathStr= settings.get(STORE_MANIFEST_LOCATION);
			if (pathStr == null)
				pathStr= ""; //$NON-NLS-1$
			fJarPackage.setManifestLocation(new Path(pathStr));

			// Sealing
			fJarPackage.setSealJar(settings.getBoolean(STORE_SEAL_JAR));
		}
	}
	/**
	 *	Stores the widget values in the JAR package.
	 */
	protected void updateModel() {
		if (getControl() == null)
			return;

		// Manifest creation
		fJarPackage.setGenerateManifest(fGenerateManifestRadioButton.getSelection());
		fJarPackage.setSaveManifest(fSaveManifestCheckbox.getSelection());
		fJarPackage.setReuseManifest(fJarPackage.isManifestSaved() && fReuseManifestCheckbox.getSelection());
		String path;
		if (fJarPackage.isManifestGenerated())
			path= fNewManifestFileText.getText();
		else
			path= fManifestFileText.getText();
		if (path == null)
			path= ""; //$NON-NLS-1$
		fJarPackage.setManifestLocation(new Path(path));

		// Sealing
		fJarPackage.setSealJar(fSealJarRadioButton.getSelection());
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

	// ----------- Utility methods -----------

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
	 * Sets the size of a control.
	 *
	 * @param control the control for which to set the size
	 * @param width the new  width of the control
	 * @param height the new height of the control
	 */
	protected void setSize(Control control, int width, int height) {
		GridData gd= new GridData(GridData.END);
		gd.widthHint= width ;
		gd.heightHint= height;
		control.setLayoutData(gd);
	}
	/**
	 * Makes the size of all buttons equal.
	 */
	protected void setEqualButtonSizes() {
		int width= SWTUtil.getButtonWidthHint(fManifestFileBrowseButton);
		int width2= SWTUtil.getButtonWidthHint(fNewManifestFileBrowseButton);
		width= Math.max(width, width2);

		width2= SWTUtil.getButtonWidthHint(fSealedPackagesDetailsButton);
		width= Math.max(width, width2);

		width2= SWTUtil.getButtonWidthHint(fUnSealedPackagesDetailsButton);
		width= Math.max(width, width2);

		width2= SWTUtil.getButtonWidthHint(fMainClassBrowseButton);
		width= Math.max(width, width2);

		setSize(fManifestFileBrowseButton, width, SWT.DEFAULT);
		setSize(fNewManifestFileBrowseButton, width, SWT.DEFAULT);
		setSize(fSealedPackagesDetailsButton, width, SWT.DEFAULT);
		setSize(fUnSealedPackagesDetailsButton, width, SWT.DEFAULT);
		setSize(fMainClassBrowseButton, width, SWT.DEFAULT);
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

	protected IPath getPathFromString(String text) {
		return new Path(text).makeAbsolute();
	}
	/**
	 * Creates a selection dialog that lists all packages under the given package
	 * fragment root.
	 * The caller is responsible for opening the dialog with <code>Window.open</code>,
	 * and subsequently extracting the selected packages (of type
	 * <code>IPackageFragment</code>) via <code>SelectionDialog.getResult</code>.
	 *
	 * @param packageFragments the package fragments
	 * @return a new selection dialog
	 */
	protected SelectionDialog createPackageDialog(Set<IJavaElement> packageFragments) {
		List<IPackageFragment> packages= new ArrayList<IPackageFragment>(packageFragments.size());
		for (Iterator<IJavaElement> iter= packageFragments.iterator(); iter.hasNext();) {
			IPackageFragment fragment= (IPackageFragment)iter.next();
			boolean containsJavaElements= false;
			int kind;
			try {
				kind= fragment.getKind();
				containsJavaElements= fragment.getChildren().length > 0;
			} catch (JavaModelException ex) {
				ExceptionHandler.handle(ex, getContainer().getShell(), JarPackagerMessages.JarManifestWizardPage_error_jarPackageWizardError_title, Messages.format(JarPackagerMessages.JarManifestWizardPage_error_jarPackageWizardError_message, JavaElementLabels.getElementLabel(fragment, JavaElementLabels.ALL_DEFAULT)));
				continue;
			}
			if (kind != IPackageFragmentRoot.K_BINARY && containsJavaElements)
				packages.add(fragment);
		}
		StandardJavaElementContentProvider cp= new StandardJavaElementContentProvider() {
			@Override
			public boolean hasChildren(Object element) {
				// prevent the + from being shown in front of packages
				return !(element instanceof IPackageFragment) && super.hasChildren(element);
			}
		};
		final DecoratingLabelProvider provider= new DecoratingLabelProvider(new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT), new ProblemsLabelDecorator(null));
		ElementTreeSelectionDialog dialog= new ElementTreeSelectionDialog(getContainer().getShell(), provider, cp);
		dialog.setDoubleClickSelects(false);
		dialog.setComparator(new JavaElementComparator());
		dialog.setInput(JavaCore.create(JavaPlugin.getWorkspace().getRoot()));
		dialog.addFilter(new EmptyInnerPackageFilter());
		dialog.addFilter(new LibraryFilter());
		dialog.addFilter(new SealPackagesFilter(packages));
		dialog.setValidator(new ISelectionStatusValidator() {
			public IStatus validate(Object[] selection) {
				StatusInfo res= new StatusInfo();
				for (int i= 0; i < selection.length; i++) {
					if (!(selection[i] instanceof IPackageFragment)) {
						res.setError(JarPackagerMessages.JarManifestWizardPage_error_mustContainPackages);
						return res;
					}
				}
				res.setOK();
				return res;
			}
		});
		return dialog;
	}
	/**
	 * Converts selection dialog results into an array of IPackageFragments.
	 * An empty array is returned in case of errors.
	 * @param dialog the dialog
	 * @return the selected IPackageFragments
	 * @throws ClassCastException if results are not IPackageFragments
	 */
	protected IPackageFragment[] getPackagesFromDialog(SelectionDialog dialog) {
		if (dialog.getReturnCode() == Window.OK && dialog.getResult().length > 0)
			return Arrays.asList(dialog.getResult()).toArray(new IPackageFragment[dialog.getResult().length]);
		else
			return new IPackageFragment[0];
	}
	/**
	 * Creates and returns a dialog to choose an existing workspace file.
	 * @param title the title
	 * @param message the dialog message
	 * @return the dialog
	 */
	protected ElementTreeSelectionDialog createWorkspaceFileSelectionDialog(String title, String message) {
		int labelFlags= JavaElementLabelProvider.SHOW_BASICS
						| JavaElementLabelProvider.SHOW_OVERLAY_ICONS
						| JavaElementLabelProvider.SHOW_SMALL_ICONS;
		final DecoratingLabelProvider provider= new DecoratingLabelProvider(new JavaElementLabelProvider(labelFlags), new ProblemsLabelDecorator(null));
		ElementTreeSelectionDialog dialog= new ElementTreeSelectionDialog(getShell(), provider, new StandardJavaElementContentProvider());
		dialog.setComparator(new JavaElementComparator());
		dialog.setAllowMultiple(false);
		dialog.setValidator(new ISelectionStatusValidator() {
			public IStatus validate(Object[] selection) {
				StatusInfo res= new StatusInfo();
				// only single selection
				if (selection.length == 1 && (selection[0] instanceof IFile))
					res.setOK();
				else
					res.setError(""); //$NON-NLS-1$
				return res;
			}
		});
		dialog.addFilter(new EmptyInnerPackageFilter());
		dialog.addFilter(new LibraryFilter());
		dialog.setTitle(title);
		dialog.setMessage(message);
		dialog.setStatusLineAboveButtons(true);
		dialog.setInput(JavaCore.create(JavaPlugin.getWorkspace().getRoot()));
		return dialog;
	}

	/**
	 * Returns the minimal set of packages which contain all the selected Java resources.
	 * @return	the Set of IPackageFragments which contain all the selected resources
	 */
	private Set<IJavaElement> getPackagesForSelectedResources() {
		Set<IJavaElement> packages= new HashSet<IJavaElement>();
		int n= fJarPackage.getElements().length;
		for (int i= 0; i < n; i++) {
			Object element= fJarPackage.getElements()[i];
			if (element instanceof ICompilationUnit) {
				packages.add(((ICompilationUnit) element).getParent());
			}
		}
		return packages;
	}
}

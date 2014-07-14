/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
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

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.TreeItem;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.history.IRefactoringHistoryService;
import org.eclipse.ltk.core.refactoring.history.RefactoringHistory;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;

import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.ProblemsLabelDecorator;
import org.eclipse.jdt.ui.StandardJavaElementContentProvider;
import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.filters.EmptyInnerPackageFilter;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.SWTUtil;

/**
 *	Page 1 of the JAR Package wizard
 */
class JarPackageWizardPage extends AbstractJarDestinationWizardPage {

	private JarPackageData fJarPackage;
	private IStructuredSelection fInitialSelection;
	private CheckboxTreeAndListGroup fInputGroup;

	// widgets
	private Button	fExportClassFilesCheckbox;
	private Button	fExportOutputFoldersCheckbox;
	private Button	fExportJavaFilesCheckbox;
	private Button	fExportRefactoringsCheckbox;
	private Link fRefactoringLink;

	private Button		fCompressCheckbox;
	private Button		fOverwriteCheckbox;
	private Button		fIncludeDirectoryEntriesCheckbox;
	private boolean	fInitiallySelecting= true;

	// dialog store id constants
	private static final String PAGE_NAME= "JarPackageWizardPage"; //$NON-NLS-1$

	private static final String STORE_EXPORT_CLASS_FILES= PAGE_NAME + ".EXPORT_CLASS_FILES"; //$NON-NLS-1$
	private static final String STORE_EXPORT_OUTPUT_FOLDERS= PAGE_NAME + ".EXPORT_OUTPUT_FOLDER"; //$NON-NLS-1$
	private static final String STORE_EXPORT_JAVA_FILES= PAGE_NAME + ".EXPORT_JAVA_FILES"; //$NON-NLS-1$

	private static final String STORE_REFACTORINGS= PAGE_NAME + ".REFACTORINGS"; //$NON-NLS-1$
	private static final String STORE_COMPRESS= PAGE_NAME + ".COMPRESS"; //$NON-NLS-1$
	private final static String STORE_OVERWRITE= PAGE_NAME + ".OVERWRITE"; //$NON-NLS-1$
	private final static String STORE_INCLUDE_DIRECTORY_ENTRIES= PAGE_NAME + ".INCLUDE_DIRECTORY_ENTRIES"; //$NON-NLS-1$

	// other constants
	private static final int SIZING_SELECTION_WIDGET_WIDTH= 480;
	private static final int SIZING_SELECTION_WIDGET_HEIGHT= 150;

	/**
	 *	Create an instance of this class
	 *
	 * @param jarPackage an object containing all required information to make an export
	 * @param selection the initial selection
	 */
	public JarPackageWizardPage(JarPackageData jarPackage, IStructuredSelection selection) {
		super(PAGE_NAME, selection, jarPackage);
		setTitle(JarPackagerMessages.JarPackageWizardPage_title);
		setDescription(JarPackagerMessages.JarPackageWizardPage_description);
		fJarPackage= jarPackage;
		fInitialSelection= selection;
	}

	/*
	 * Method declared on IDialogPage.
	 */
	@Override
	public void createControl(final Composite parent) {

		initializeDialogUnits(parent);

		Composite composite= new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(
			new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));

		createPlainLabel(composite, JarPackagerMessages.JarPackageWizardPage_whatToExport_label);
		createInputGroup(composite);

		createExportTypeGroup(composite);

		new Label(composite, SWT.NONE); // vertical spacer


		createPlainLabel(composite, JarPackagerMessages.JarPackageWizardPage_whereToExport_label);
		createDestinationGroup(composite);

		createPlainLabel(composite, JarPackagerMessages.JarPackageWizardPage_options_label);
		createOptionsGroup(composite);

		restoreResourceSpecificationWidgetValues(); // superclass API defines this hook
		restoreWidgetValues();
		if (fInitialSelection != null)
			BusyIndicator.showWhile(parent.getDisplay(), new Runnable() {
				public void run() {
					setupBasedOnInitialSelections();
				}
			});

		setControl(composite);
		update();
		fRefactoringLink.setEnabled(fExportRefactoringsCheckbox.getSelection());
		giveFocusToDestination();

		Dialog.applyDialogFont(composite);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IJavaHelpContextIds.JARPACKAGER_WIZARD_PAGE);
	}

	/**
	 *	Create the export options specification widgets.
	 *
	 *	@param parent org.eclipse.swt.widgets.Composite
	 */
	@Override
	protected void createOptionsGroup(Composite parent) {
		Composite optionsGroup= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		optionsGroup.setLayout(layout);

		fCompressCheckbox= new Button(optionsGroup, SWT.CHECK | SWT.LEFT);
		fCompressCheckbox.setText(JarPackagerMessages.JarPackageWizardPage_compress_text);
		fCompressCheckbox.addListener(SWT.Selection, this);

		fIncludeDirectoryEntriesCheckbox= new Button(optionsGroup, SWT.CHECK | SWT.LEFT);
		fIncludeDirectoryEntriesCheckbox.setText(JarPackagerMessages.JarPackageWizardPage_includeDirectoryEntries_text);
		fIncludeDirectoryEntriesCheckbox.addListener(SWT.Selection, this);

		fOverwriteCheckbox= new Button(optionsGroup, SWT.CHECK | SWT.LEFT);
		fOverwriteCheckbox.setText(JarPackagerMessages.JarPackageWizardPage_overwrite_text);
		fOverwriteCheckbox.addListener(SWT.Selection, this);
	}

	/**
	 * Returns an iterator over this page's collection of currently-specified
	 * elements to be exported. This is the primary element selection facility
	 * accessor for subclasses.
	 *
	 * @return an iterator over the collection of elements currently selected for export
	 */
	@Override
	protected Iterator<Object> getSelectedResourcesIterator() {
		return fInputGroup.getAllCheckedListItems();
	}

	/**
	 * Persists resource specification control setting that are to be restored
	 * in the next instance of this page. Subclasses wishing to persist
	 * settings for their controls should extend the hook method
	 * <code>internalSaveWidgetValues</code>.
	 */
	@Override
	public final void saveWidgetValues() {
		super.saveWidgetValues();
		// update directory names history
		IDialogSettings settings= getDialogSettings();
		if (settings != null) {
			settings.put(STORE_EXPORT_CLASS_FILES, fJarPackage.areClassFilesExported());
			settings.put(STORE_EXPORT_OUTPUT_FOLDERS, fJarPackage.areOutputFoldersExported());
			settings.put(STORE_EXPORT_JAVA_FILES, fJarPackage.areJavaFilesExported());

			// options
			settings.put(STORE_REFACTORINGS, fJarPackage.isRefactoringAware());
			settings.put(STORE_COMPRESS, fJarPackage.isCompressed());
			settings.put(STORE_INCLUDE_DIRECTORY_ENTRIES, fJarPackage.areDirectoryEntriesIncluded());
			settings.put(STORE_OVERWRITE, fJarPackage.allowOverwrite());
		}
		// Allow subclasses to save values
		internalSaveWidgetValues();
	}

	/**
	 * Hook method for subclasses to persist their settings.
	 */
	@Override
	protected void internalSaveWidgetValues() {
	}

	/**
	 *	Hook method for restoring widget values to the values that they held
	 *	last time this wizard was used to completion.
	 */
	@Override
	protected void restoreWidgetValues() {
		if (!((JarPackageWizard)getWizard()).isInitializingFromJarPackage())
			initializeJarPackage();

		fExportClassFilesCheckbox.setSelection(fJarPackage.areClassFilesExported());
		fExportOutputFoldersCheckbox.setSelection(fJarPackage.areOutputFoldersExported());
		fExportJavaFilesCheckbox.setSelection(fJarPackage.areJavaFilesExported());

		super.restoreWidgetValues();

		// options
		if (fExportRefactoringsCheckbox != null)
			fExportRefactoringsCheckbox.setSelection(fJarPackage.isRefactoringAware());
		fCompressCheckbox.setSelection(fJarPackage.isCompressed());
		fIncludeDirectoryEntriesCheckbox.setSelection(fJarPackage.areDirectoryEntriesIncluded());
		fOverwriteCheckbox.setSelection(fJarPackage.allowOverwrite());
	}

	/**
	 *	Initializes the JAR package from last used wizard page values.
	 */
	@Override
	protected void initializeJarPackage() {
		super.initializeJarPackage();

		IDialogSettings settings= getDialogSettings();
		if (settings != null) {
			// source
			fJarPackage.setElements(getSelectedElements());
			fJarPackage.setExportClassFiles(settings.getBoolean(STORE_EXPORT_CLASS_FILES));
			fJarPackage.setExportOutputFolders(settings.getBoolean(STORE_EXPORT_OUTPUT_FOLDERS));
			fJarPackage.setExportJavaFiles(settings.getBoolean(STORE_EXPORT_JAVA_FILES));

			// options
			fJarPackage.setRefactoringAware(settings.getBoolean(STORE_REFACTORINGS));
			fJarPackage.setCompress(settings.getBoolean(STORE_COMPRESS));
			fJarPackage.setIncludeDirectoryEntries(settings.getBoolean(STORE_INCLUDE_DIRECTORY_ENTRIES));
			fJarPackage.setOverwrite(settings.getBoolean(STORE_OVERWRITE));
		}
	}

	/**
	 *	Stores the widget values in the JAR package.
	 */
	@Override
	protected void updateModel() {
		if (getControl() == null)
			return;

		// source
		if (fExportClassFilesCheckbox.getSelection() && !fJarPackage.areClassFilesExported())
			fExportOutputFoldersCheckbox.setSelection(false);
		if (fExportOutputFoldersCheckbox.getSelection() && !fJarPackage.areOutputFoldersExported())
			fExportClassFilesCheckbox.setSelection(false);
		fJarPackage.setExportClassFiles(fExportClassFilesCheckbox.getSelection());
		fJarPackage.setExportOutputFolders(fExportOutputFoldersCheckbox.getSelection());
		fJarPackage.setExportJavaFiles(fExportJavaFilesCheckbox.getSelection());
		fJarPackage.setElements(getSelectedElements());

		super.updateModel();
		// options
		if (fExportRefactoringsCheckbox != null)
			fJarPackage.setRefactoringAware(fExportRefactoringsCheckbox.getSelection());
		else
			fJarPackage.setRefactoringAware(false);
		fJarPackage.setCompress(fCompressCheckbox.getSelection());
		fJarPackage.setIncludeDirectoryEntries(fIncludeDirectoryEntriesCheckbox.getSelection());
		fJarPackage.setOverwrite(fOverwriteCheckbox.getSelection());
	}

	/**
	 * Returns the resource for the specified path.
	 *
	 * @param path	the path for which the resource should be returned
	 * @return the resource specified by the path or <code>null</code>
	 */
	protected IResource findResource(IPath path) {
		IWorkspace workspace= ResourcesPlugin.getWorkspace();
		IStatus result= workspace.validatePath(
							path.toString(),
							IResource.ROOT | IResource.PROJECT | IResource.FOLDER | IResource.FILE);
		if (result.isOK() && workspace.getRoot().exists(path))
			return workspace.getRoot().findMember(path);
		return null;
	}

	/**
	 * Creates the checkbox tree and list for selecting resources.
	 *
	 * @param parent the parent control
	 */
	protected void createInputGroup(Composite parent) {
		int labelFlags= JavaElementLabelProvider.SHOW_BASICS
						| JavaElementLabelProvider.SHOW_OVERLAY_ICONS
						| JavaElementLabelProvider.SHOW_SMALL_ICONS;
		ITreeContentProvider treeContentProvider=
			new StandardJavaElementContentProvider() {
				@Override
				public boolean hasChildren(Object element) {
					// prevent the + from being shown in front of packages
					return !(element instanceof IPackageFragment) && super.hasChildren(element);
				}
			};
		final DecoratingLabelProvider provider= new DecoratingLabelProvider(new JavaElementLabelProvider(labelFlags), new ProblemsLabelDecorator(null));
		fInputGroup= new CheckboxTreeAndListGroup(
					parent,
					JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()),
					treeContentProvider,
					provider,
					new StandardJavaElementContentProvider(),
					provider,
					SWT.NONE,
					SIZING_SELECTION_WIDGET_WIDTH,
					SIZING_SELECTION_WIDGET_HEIGHT) {

						@Override
						protected void setTreeChecked(final Object element, final boolean state) {
							if (fInitiallySelecting && element instanceof IResource) {
								final IResource resource= (IResource) element;
								if (resource.getName().charAt(0) == '.')
									return;
							}
							super.setTreeChecked(element, state);
						}
		};
		fInputGroup.addTreeFilter(new EmptyInnerPackageFilter());
		fInputGroup.setTreeComparator(new JavaElementComparator());
		fInputGroup.setListComparator(new JavaElementComparator());
		fInputGroup.addTreeFilter(new ContainerFilter(ContainerFilter.FILTER_NON_CONTAINERS));
		fInputGroup.addTreeFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object p, Object element) {
				if (element instanceof IPackageFragmentRoot) {
					IPackageFragmentRoot root= (IPackageFragmentRoot) element;
					return !root.isArchive() && !root.isExternal();
				}
				return true;
			}
		});
		fInputGroup.addListFilter(new ContainerFilter(ContainerFilter.FILTER_CONTAINERS));
		fInputGroup.getTree().addListener(SWT.MouseUp, this);
		fInputGroup.getTable().addListener(SWT.MouseUp, this);

		SWTUtil.setAccessibilityText(fInputGroup.getTree(), JarPackagerMessages.JarPackageWizardPage_tree_accessibility_message);
		SWTUtil.setAccessibilityText(fInputGroup.getTable(), JarPackagerMessages.JarPackageWizardPage_table_accessibility_message);

		ICheckStateListener listener = new ICheckStateListener() {
            public void checkStateChanged(CheckStateChangedEvent event) {
                update();
            }
        };

        fInputGroup.addCheckStateListener(listener);
	}

	/**
	 * Creates the export type controls.
	 *
	 * @param parent
	 *            the parent control
	 */
	protected void createExportTypeGroup(Composite parent) {
		Composite optionsGroup= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		optionsGroup.setLayout(layout);

		fExportClassFilesCheckbox= new Button(optionsGroup, SWT.CHECK | SWT.LEFT);
		fExportClassFilesCheckbox.setText(JarPackagerMessages.JarPackageWizardPage_exportClassFiles_text);
		fExportClassFilesCheckbox.addListener(SWT.Selection, this);

		fExportOutputFoldersCheckbox= new Button(optionsGroup, SWT.CHECK | SWT.LEFT);
		fExportOutputFoldersCheckbox.setText(JarPackagerMessages.JarPackageWizardPage_exportOutputFolders_text);
		fExportOutputFoldersCheckbox.addListener(SWT.Selection, this);

		fExportJavaFilesCheckbox= new Button(optionsGroup, SWT.CHECK | SWT.LEFT);
		fExportJavaFilesCheckbox.setText(JarPackagerMessages.JarPackageWizardPage_exportJavaFiles_text);
		fExportJavaFilesCheckbox.addListener(SWT.Selection, this);

		Composite refactoringsGroup= new Composite(optionsGroup, SWT.NONE);
		layout= new GridLayout();
		layout.horizontalSpacing= 0;
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		layout.numColumns= 2;
		refactoringsGroup.setLayout(layout);

		fExportRefactoringsCheckbox= new Button(refactoringsGroup, SWT.CHECK | SWT.LEFT);
		fExportRefactoringsCheckbox.setText(JarPackagerMessages.JarPackageWizardPage_refactorings_text);
		fExportRefactoringsCheckbox.addListener(SWT.Selection, this);

		fRefactoringLink= new Link(refactoringsGroup, SWT.WRAP);
		fRefactoringLink.setText(JarPackagerMessages.JarPackageWizardPage_configure_label);
		fRefactoringLink.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent event) {
				openRefactoringDialog();
			}

		});
		fRefactoringLink.setToolTipText(JarPackagerMessages.JarPackageWizardPage_configure_tooltip);
		GridData data= new GridData(GridData.BEGINNING | GridData.GRAB_HORIZONTAL);
		fRefactoringLink.setLayoutData(data);

		fExportRefactoringsCheckbox.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent event) {
				fRefactoringLink.setEnabled(fExportRefactoringsCheckbox.getSelection());
			}
		});
	}

	/**
	 * Opens the dialog to configure refactorings.
	 */
	protected void openRefactoringDialog() {
		final RefactoringHistory[] history= { null};
		final IRefactoringHistoryService service= RefactoringCore.getHistoryService();
		try {
			service.connect();
			final Set<IProject> set= new HashSet<IProject>();
			final Object[] elements= fJarPackage.getElements();
			for (int index= 0; index < elements.length; index++) {
				if (elements[index] instanceof IAdaptable) {
					final IAdaptable adaptable= (IAdaptable) elements[index];
					final IResource resource= (IResource) adaptable.getAdapter(IResource.class);
					if (resource != null)
						set.add(resource.getProject());
				}
			}
			try {
				getContainer().run(false, true, new IRunnableWithProgress() {

					public void run(final IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						history[0]= service.getRefactoringHistory(set.toArray(new IProject[set.size()]), 0, Long.MAX_VALUE, JavaRefactoringDescriptor.JAR_MIGRATION, monitor);
					}
				});
			} catch (InvocationTargetException exception) {
				ExceptionHandler.handle(exception, getShell(), JarPackagerMessages.JarPackageWizardPage_error_caption, JarPackagerMessages.JarPackageWizardPage_error_label);
				return;
			} catch (InterruptedException exception) {
				return;
			}
			new JarRefactoringDialog(getShell(), getDialogSettings(), fJarPackage, history[0]).open();
			updatePageCompletion();
		} finally {
			service.disconnect();
		}
	}

	/**
	 * Updates the enablement of this page's controls. Subclasses may extend.
	 */
	@Override
	protected void updateWidgetEnablements() {
		if (fExportRefactoringsCheckbox != null) {
			final boolean selection= fExportRefactoringsCheckbox.getSelection();
			fIncludeDirectoryEntriesCheckbox.setEnabled(!selection);
			if (selection) {
				fIncludeDirectoryEntriesCheckbox.setSelection(true);
				fJarPackage.setIncludeDirectoryEntries(true);
			}
		}
	}

	/*
	 * Overrides method from IJarPackageWizardPage
	 */
	@Override
	public boolean isPageComplete() {
		boolean complete= validateSourceGroup();
		complete= validateDestinationGroup() && complete;
		complete= validateOptionsGroup() && complete;
		if (complete)
			setErrorMessage(null);
		return complete;
	}

	@Override
	protected void updatePageCompletion() {
		boolean pageComplete= isPageComplete();
		setPageComplete(pageComplete);
		if (pageComplete)
			setErrorMessage(null);
		updateRefactoringMessage();
	}

	protected void updateRefactoringMessage() {
		String currentMessage= getMessage();
		if (fJarPackage.isRefactoringAware() && fJarPackage.getRefactoringDescriptors().length == 0) {
			if (currentMessage == null)
				setMessage(JarPackagerMessages.JarPackageWizardPage_no_refactorings_selected, IMessageProvider.INFORMATION);
		} else if (JarPackagerMessages.JarPackageWizardPage_no_refactorings_selected.equals(currentMessage))
			setMessage(null);
	}

	/*
	 * Overrides method from WizardDataTransferPage
	 */
	@Override
	protected boolean validateOptionsGroup() {
		return true;
	}

	/*
	 * Overrides method from WizardDataTransferPage
	 */
	@Override
	protected boolean validateSourceGroup() {
		if (!(fExportClassFilesCheckbox.getSelection() || fExportOutputFoldersCheckbox.getSelection() || fExportJavaFilesCheckbox.getSelection())) {
			setErrorMessage(JarPackagerMessages.JarPackageWizardPage_error_noExportTypeChecked);
			return false;
		}

		if (getSelectedResources().size() == 0) {
			if (getErrorMessage() != null)
				setErrorMessage(null);
			return false;
		}
		if (fExportClassFilesCheckbox.getSelection() || fExportOutputFoldersCheckbox.getSelection())
			return true;

		// Source file only export - check if there are source files
		Iterator<Object> iter= getSelectedResourcesIterator();
		while (iter.hasNext()) {
			Object element= iter.next();
			if (element instanceof IClassFile) {
				IPackageFragmentRoot root= (IPackageFragmentRoot)((IClassFile)element).getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
				if (root == null)
					continue;
				IClasspathEntry cpEntry;
				try {
					cpEntry= root.getRawClasspathEntry();
				} catch (JavaModelException e) {
					continue;
				}
				if (cpEntry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
					return true;
				}
			} else {
				return true;
			}
		}

		if (getErrorMessage() != null)
			setErrorMessage(null);
		return false;
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
			return ResourcesPlugin.getWorkspace().getRoot().getFile(filePath);
		else
			return null;
	}

	/*
	 * Overrides method from WizardExportResourcePage
	 */
	@Override
	protected void setupBasedOnInitialSelections() {
		Iterator<?> iterator= fInitialSelection.iterator();
		while (iterator.hasNext()) {
			Object selectedElement= iterator.next();

			if (selectedElement instanceof IResource && !((IResource)selectedElement).isAccessible())
				continue;

			if (selectedElement instanceof IJavaElement && !((IJavaElement)selectedElement).exists())
				continue;

			if (selectedElement instanceof ICompilationUnit || selectedElement instanceof IClassFile || selectedElement instanceof IFile)
				fInputGroup.initialCheckListItem(selectedElement);
			else {
				if (selectedElement instanceof IFolder) {
					// Convert resource to Java element if possible
					IJavaElement je= JavaCore.create((IResource)selectedElement);
					if (je != null && je.exists() &&  je.getJavaProject().isOnClasspath((IResource)selectedElement))
						selectedElement= je;
				}
				try {
					fInputGroup.initialCheckTreeItem(selectedElement);
				} finally {
					fInitiallySelecting= false;
				}
			}
		}

		TreeItem[] items= fInputGroup.getTree().getItems();
		int i= 0;
		while (i < items.length && !items[i].getChecked())
			i++;
		if (i < items.length) {
			fInputGroup.getTree().setSelection(new TreeItem[] {items[i]});
			fInputGroup.getTree().showSelection();
			fInputGroup.populateListViewer(items[i].getData());
		}
	}

	/*
	 * Method declared on IWizardPage.
	 */
	@Override
	public void setPreviousPage(IWizardPage page) {
		super.setPreviousPage(page);
		if (getControl() != null)
			updatePageCompletion();
	}

	Object[] getSelectedElementsWithoutContainedChildren() {
		Set<Object> closure= removeContainedChildren(fInputGroup.getWhiteCheckedTreeItems());
		closure.addAll(getExportedNonContainers());
		return closure.toArray();
	}

	private Set<Object> removeContainedChildren(Set<Object> elements) {
		Set<Object> newList= new HashSet<Object>(elements.size());
		Set<Object> javaElementResources= getCorrespondingContainers(elements);
		Iterator<Object> iter= elements.iterator();
		boolean removedOne= false;
		while (iter.hasNext()) {
			Object element= iter.next();
			Object parent;
			if (element instanceof IResource)
				parent= ((IResource)element).getParent();
			else if (element instanceof IJavaElement) {
				parent= ((IJavaElement)element).getParent();
				if (parent instanceof IPackageFragmentRoot) {
					IPackageFragmentRoot pkgRoot= (IPackageFragmentRoot)parent;
					try {
						if (pkgRoot.getCorrespondingResource() instanceof IProject)
							parent= pkgRoot.getJavaProject();
					} catch (JavaModelException ex) {
						// leave parent as is
					}
				}
			}
			else {
				// unknown type
				newList.add(element);
				continue;
			}
			if (element instanceof IJavaModel || ((!(parent instanceof IJavaModel)) && (elements.contains(parent) || javaElementResources.contains(parent))))
				removedOne= true;
			else
				newList.add(element);
		}
		if (removedOne)
			return removeContainedChildren(newList);
		else
			return newList;
	}

	private Set<Object> getExportedNonContainers() {
		Set<Object> whiteCheckedTreeItems= fInputGroup.getWhiteCheckedTreeItems();
		Set<Object> exportedNonContainers= new HashSet<Object>(whiteCheckedTreeItems.size());
		Set<Object> javaElementResources= getCorrespondingContainers(whiteCheckedTreeItems);
		Iterator<Object> iter= fInputGroup.getAllCheckedListItems();
		while (iter.hasNext()) {
			Object element= iter.next();
			Object parent= null;
			if (element instanceof IResource)
				parent= ((IResource)element).getParent();
			else if (element instanceof IJavaElement)
				parent= ((IJavaElement)element).getParent();
			if (!whiteCheckedTreeItems.contains(parent) && !javaElementResources.contains(parent))
				exportedNonContainers.add(element);
		}
		return exportedNonContainers;
	}

	/*
	 * Create a list with the folders / projects that correspond
	 * to the Java elements (Java project, package, package root)
	 */
	private Set<Object> getCorrespondingContainers(Set<Object> elements) {
		Set<Object> javaElementResources= new HashSet<Object>(elements.size());
		Iterator<Object> iter= elements.iterator();
		while (iter.hasNext()) {
			Object element= iter.next();
			if (element instanceof IJavaElement) {
				IJavaElement je= (IJavaElement)element;
				int type= je.getElementType();
				if (type == IJavaElement.JAVA_PROJECT || type == IJavaElement.PACKAGE_FRAGMENT || type == IJavaElement.PACKAGE_FRAGMENT_ROOT) {
					// exclude default package since it is covered by the root
					if (!(type == IJavaElement.PACKAGE_FRAGMENT && ((IPackageFragment)element).isDefaultPackage())) {
						Object resource;
						try {
							resource= je.getCorrespondingResource();
						} catch (JavaModelException ex) {
							resource= null;
						}
						if (resource != null)
							javaElementResources.add(resource);
					}
				}
			}
		}
		return javaElementResources;
	}

	private Object[] getSelectedElements() {
		return getSelectedResources().toArray();
	}
}

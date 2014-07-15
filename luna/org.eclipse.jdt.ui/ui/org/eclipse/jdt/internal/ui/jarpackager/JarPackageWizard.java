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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;

import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.jarpackager.IJarExportRunnable;
import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * Wizard for exporting resources from the workspace to a Java Archive (JAR) file.
 * <p>
 * This class may be instantiated and used without further configuration;
 * this class is not intended to be subclassed.
 * </p>
 * <p>
 * Example:
 * <pre>
 * IWizard wizard = new JarPackageWizard();
 * wizard.init(workbench, selection);
 * WizardDialog dialog = new WizardDialog(shell, wizard);
 * dialog.open();
 * </pre>
 * During the call to <code>open</code>, the wizard dialog is presented to the
 * user. When the user hits Finish, the user-selected workspace resources
 * are exported to the user-specified zip file, the dialog closes, and the call
 * to <code>open</code> returns.
 * </p>
 */
public class JarPackageWizard extends Wizard implements IExportWizard {

	private static String DIALOG_SETTINGS_KEY= "JarPackageWizard"; //$NON-NLS-1$

	private boolean fHasNewDialogSettings;

	private boolean fInitializeFromJarPackage;

	private JarOptionsPage fJarOptionsWizardPage;

	private JarPackageData fJarPackage;

	private JarPackageWizardPage fJarPackageWizardPage;

	private IStructuredSelection fSelection;

	/**
	 * Creates a wizard for exporting workspace resources to a JAR file.
	 */
	public JarPackageWizard() {
		IDialogSettings workbenchSettings= JavaPlugin.getDefault().getDialogSettings();
		IDialogSettings section= workbenchSettings.getSection(DIALOG_SETTINGS_KEY);
		if (section == null)
			fHasNewDialogSettings= true;
		else {
			fHasNewDialogSettings= false;
			setDialogSettings(section);
		}
	}

	private void addJavaElement(List<Object> selectedElements, IJavaElement je) {
		if (je.getElementType() == IJavaElement.COMPILATION_UNIT)
			selectedElements.add(je);
		else if (je.getElementType() == IJavaElement.CLASS_FILE)
			selectedElements.add(je);
		else if (je.getElementType() == IJavaElement.JAVA_PROJECT)
			selectedElements.add(je);
		else if (je.getElementType() == IJavaElement.PACKAGE_FRAGMENT) {
			if (!isInArchiveOrExternal(je))
				selectedElements.add(je);
		} else if (je.getElementType() == IJavaElement.PACKAGE_FRAGMENT_ROOT) {
			if (!isInArchiveOrExternal(je))
				selectedElements.add(je);
		} else {
			IOpenable openable= je.getOpenable();
			if (openable instanceof ICompilationUnit)
				selectedElements.add(((ICompilationUnit) openable).getPrimary());
			else if (openable instanceof IClassFile && !isInArchiveOrExternal(je))
				selectedElements.add(openable);
		}
	}

	private static boolean isInArchiveOrExternal(IJavaElement element) {
		IPackageFragmentRoot root= JavaModelUtil.getPackageFragmentRoot(element);
		return root != null && (root.isArchive() || root.isExternal());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addPages() {
		super.addPages();
		fJarPackageWizardPage= new JarPackageWizardPage(fJarPackage, fSelection);
		addPage(fJarPackageWizardPage);
		fJarOptionsWizardPage= new JarOptionsPage(fJarPackage);
		addPage(fJarOptionsWizardPage);
		addPage(new JarManifestWizardPage(fJarPackage));
	}

	private void addProject(List<Object> selectedElements, IProject project) {
		try {
			if (project.hasNature(JavaCore.NATURE_ID))
				selectedElements.add(JavaCore.create(project));
		} catch (CoreException ex) {
			// ignore selected element
		}
	}

	private void addResource(List<Object> selectedElements, IResource resource) {
		IJavaElement je= JavaCore.create(resource);
		if (je != null && je.exists() && je.getElementType() == IJavaElement.COMPILATION_UNIT)
			selectedElements.add(je);
		else
			selectedElements.add(resource);
	}

	/**
	 * Exports the JAR package.
	 *
	 * @param op the op
	 * @return a boolean indicating success or failure
	 */
	protected boolean executeExportOperation(IJarExportRunnable op) {
		try {
			getContainer().run(true, true, op);
		} catch (InterruptedException e) {
			return false;
		} catch (InvocationTargetException ex) {
			if (ex.getTargetException() != null) {
				ExceptionHandler.handle(ex, getShell(), JarPackagerMessages.JarPackageWizard_jarExportError_title, JarPackagerMessages.JarPackageWizard_jarExportError_message);
				return false;
			}
		}
		IStatus status= op.getStatus();
		if (!status.isOK()) {
			ErrorDialog.openError(getShell(), JarPackagerMessages.JarPackageWizard_jarExport_title, null, status);
			return !(status.matches(IStatus.ERROR));
		}
		return true;
	}

	@Override
	public IWizardPage getNextPage(IWizardPage page) {
		if (page == fJarPackageWizardPage && !fJarPackage.isRefactoringAware())
			return fJarOptionsWizardPage;
		return super.getNextPage(page);
	}

	@Override
	public IWizardPage getPreviousPage(IWizardPage page) {
		if (page == fJarOptionsWizardPage && !fJarPackage.isRefactoringAware())
			return fJarPackageWizardPage;
		return super.getPreviousPage(page);
	}

	/**
	 * Gets the current workspace page selection and converts it to a valid
	 * selection for this wizard: - resources and projects are OK - CUs are OK -
	 * Java projects are OK - Source package fragments and source packages
	 * fragement roots are ok - Java elements below a CU are converted to their
	 * CU - all other input elements are ignored
	 *
	 * @return a valid structured selection based on the current selection
	 */
	protected IStructuredSelection getValidSelection() {
		ISelection currentSelection= JavaPlugin.getActiveWorkbenchWindow().getSelectionService().getSelection();
		if (currentSelection instanceof IStructuredSelection) {
			IStructuredSelection structuredSelection= (IStructuredSelection) currentSelection;
			List<Object> selectedElements= new ArrayList<Object>(structuredSelection.size());
			Iterator<?> iter= structuredSelection.iterator();
			while (iter.hasNext()) {
				Object selectedElement= iter.next();
				if (selectedElement instanceof IProject)
					addProject(selectedElements, (IProject) selectedElement);
				else if (selectedElement instanceof IResource)
					addResource(selectedElements, (IResource) selectedElement);
				else if (selectedElement instanceof IJavaElement)
					addJavaElement(selectedElements, (IJavaElement) selectedElement);
			}
			return new StructuredSelection(selectedElements);
		} else
			return StructuredSelection.EMPTY;
	}

	/**
	 * {@inheritDoc}
	 */
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		// ignore the selection argument since the main export wizard changed it
		fSelection= getValidSelection();
		fJarPackage= new JarPackageData();
		setInitializeFromJarPackage(false);
		setWindowTitle(JarPackagerMessages.JarPackageWizard_windowTitle);
		setDefaultPageImageDescriptor(JavaPluginImages.DESC_WIZBAN_JAR_PACKAGER);
		setNeedsProgressMonitor(true);
	}

	/**
	 * Initializes this wizard from the given JAR package description.
	 *
	 * @param workbench
	 *            the workbench which launched this wizard
	 * @param jarPackage
	 *            the JAR package description used to initialize this wizard
	 */
	public void init(IWorkbench workbench, JarPackageData jarPackage) {
		Assert.isNotNull(workbench);
		Assert.isNotNull(jarPackage);
		fJarPackage= jarPackage;
		setInitializeFromJarPackage(true);
		fSelection= new StructuredSelection(fJarPackage.getElements());
		setWindowTitle(JarPackagerMessages.JarPackageWizard_windowTitle);
		setDefaultPageImageDescriptor(JavaPluginImages.DESC_WIZBAN_JAR_PACKAGER);
		setNeedsProgressMonitor(true);
	}

	boolean isInitializingFromJarPackage() {
		return fInitializeFromJarPackage;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean performFinish() {
		fJarPackage.setElements(fJarPackageWizardPage.getSelectedElementsWithoutContainedChildren());

		if (!executeExportOperation(fJarPackage.createJarExportRunnable(getShell())))
			return false;

		// Save the dialog settings
		if (fHasNewDialogSettings) {
			IDialogSettings workbenchSettings= JavaPlugin.getDefault().getDialogSettings();
			IDialogSettings section= workbenchSettings.getSection(DIALOG_SETTINGS_KEY);
			section= workbenchSettings.addNewSection(DIALOG_SETTINGS_KEY);
			setDialogSettings(section);
		}
		IWizardPage[] pages= getPages();
		for (int i= 0; i < getPageCount(); i++) {
			IWizardPage page= pages[i];
			if (page instanceof IJarPackageWizardPage)
				((IJarPackageWizardPage) page).finish();
		}
		return true;
	}

	void setInitializeFromJarPackage(boolean state) {
		fInitializeFromJarPackage= state;
	}
}
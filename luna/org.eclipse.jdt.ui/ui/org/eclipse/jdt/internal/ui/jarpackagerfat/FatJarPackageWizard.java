/*******************************************************************************
 * Copyright (c) 2007, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 83258 [jar exporter] Deploy java application as executable jar
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 213638 [jar exporter] create ANT build file for current settings
 *     Ferenc Hechler <ferenc_hechler@users.sourceforge.net> - [jar exporter] export directory entries in "Runnable JAR File" - https://bugs.eclipse.org/bugs/show_bug.cgi?id=243163
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 219530 [jar application] add Jar-in-Jar ClassLoader option
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackagerfat;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Iterator;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;

import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;

import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;

import org.eclipse.jdt.ui.jarpackager.IJarExportRunnable;
import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.dialogs.OptionalMessageDialog;
import org.eclipse.jdt.internal.ui.jarpackagerfat.FatJarPackageWizardPage.LibraryHandler;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;


/**
 * Wizard for exporting resources from the workspace to a Fat Java Archive (JAR) file.
 * The exported jar will contain all required libraries.
 *
 * @since 3.4
 */
public class FatJarPackageWizard extends Wizard implements IExportWizard {

	private static String DIALOG_SETTINGS_KEY= "FatJarPackageWizard"; //$NON-NLS-1$

	private static final class IPIssueWarningDialog extends OptionalMessageDialog {

		private static final String ID= "RunnableJar.export.ipwarning"; //$NON-NLS-1$

		protected IPIssueWarningDialog(Shell parent, String title, String message) {
			super(ID, parent, title, null, message, MessageDialog.WARNING, new String[] { IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL }, 0);
		}

	}

	private boolean fHasNewDialogSettings;
	private boolean fInitializeFromJarPackage;
	private JarPackageData fJarPackage;
	private FatJarPackageWizardPage fJarPackageWizardPage;
	private IStructuredSelection fSelection;

	/**
	 * Creates a wizard for exporting workspace resources to a JAR file.
	 */
	public FatJarPackageWizard() {
		IDialogSettings workbenchSettings= JavaPlugin.getDefault().getDialogSettings();
		IDialogSettings section= workbenchSettings.getSection(DIALOG_SETTINGS_KEY);
		if (section == null)
			fHasNewDialogSettings= true;
		else {
			fHasNewDialogSettings= false;
			setDialogSettings(section);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addPages() {
		super.addPages();
		fJarPackageWizardPage= new FatJarPackageWizardPage(fJarPackage, fSelection);
		addPage(fJarPackageWizardPage);
	}

	/**
	 * Exports the JAR package.
	 *
	 * @param op the operation to run
	 * @param wizardPageStatus the status returned by the wizard page
	 * @return a boolean indicating success or failure
	 */
	protected boolean executeExportOperation(IJarExportRunnable op, IStatus wizardPageStatus) {
		try {
			getContainer().run(true, true, op);
		} catch (InterruptedException e) {
			return false;
		} catch (InvocationTargetException ex) {
			if (ex.getTargetException() != null) {
				ExceptionHandler.handle(ex, getShell(), FatJarPackagerMessages.JarPackageWizard_jarExportError_title, FatJarPackagerMessages.JarPackageWizard_jarExportError_message);
				return false;
			}
		}
		IStatus status= op.getStatus();
		if (!status.isOK()) {
			if (!wizardPageStatus.isOK()) {
				if (!(status instanceof MultiStatus))
					status= new MultiStatus(status.getPlugin(), status.getCode(), status.getMessage(), status.getException());

				((MultiStatus) status).add(wizardPageStatus);
			}
			ErrorDialog.openError(getShell(), FatJarPackagerMessages.JarPackageWizard_jarExport_title, null, status);
			return !(status.matches(IStatus.ERROR));
		} else if (!wizardPageStatus.isOK()) {
			ErrorDialog.openError(getShell(), FatJarPackagerMessages.JarPackageWizard_jarExport_title, null, wizardPageStatus);
		}
		return true;
	}

	@Override
	public IWizardPage getNextPage(IWizardPage page) {
		return super.getNextPage(page);
	}

	@Override
	public IWizardPage getPreviousPage(IWizardPage page) {
		return super.getPreviousPage(page);
	}

	/**
	 * @return all java projects which contain the selected elements in the active workbench window
	 */
	protected IStructuredSelection getSelectedJavaProjects() {
		ISelection currentSelection= JavaPlugin.getActiveWorkbenchWindow().getSelectionService().getSelection();
		if (currentSelection instanceof IStructuredSelection) {
			IStructuredSelection structuredSelection= (IStructuredSelection) currentSelection;
			HashSet<IJavaProject> selectedElements= new HashSet<IJavaProject>();
			Iterator<?> iter= structuredSelection.iterator();
			while (iter.hasNext()) {
				Object selectedElement= iter.next();
				if (selectedElement instanceof IJavaElement) {
					IJavaProject javaProject= ((IJavaElement) selectedElement).getJavaProject();
					if (javaProject != null)
						selectedElements.add(javaProject);
				}
			}
			return new StructuredSelection(selectedElements);
		} else
			return StructuredSelection.EMPTY;
	}

	/**
	 * {@inheritDoc}
	 */
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		fSelection= getSelectedJavaProjects();
		fJarPackage= new JarPackageData();
		fJarPackage.setIncludeDirectoryEntries(true);
		setInitializeFromJarPackage(false);
		setWindowTitle(FatJarPackagerMessages.JarPackageWizard_windowTitle);
		setDefaultPageImageDescriptor(JavaPluginImages.DESC_WIZBAN_FAT_JAR_PACKAGER);
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
		setWindowTitle(FatJarPackagerMessages.JarPackageWizard_windowTitle);
		setDefaultPageImageDescriptor(JavaPluginImages.DESC_WIZBAN_FAT_JAR_PACKAGER);
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
		LibraryHandler libraryHandler= fJarPackageWizardPage.getLibraryHandler();
		fJarPackage.setJarBuilder(libraryHandler.getBuilder(fJarPackage));
		MultiStatus status= new MultiStatus(JavaPlugin.getPluginId(), IStatus.OK, FatJarPackagerMessages.FatJarPackageWizard_JarExportProblems_message, null);
		Object[] elements= fJarPackageWizardPage.getSelectedElementsWithoutContainedChildren(status);
		fJarPackage.setElements(elements);

		if ((libraryHandler.isShowWarning()) && hasArchive(elements)) {
			if (OptionalMessageDialog.isDialogEnabled(IPIssueWarningDialog.ID)) {
				IPIssueWarningDialog dialog= new IPIssueWarningDialog(getShell(), FatJarPackagerMessages.FatJarPackageWizard_IPIssueDialog_title,
						FatJarPackagerMessages.FatJarPackageWizard_IPIssueDialog_message);
				if (dialog.open() != Window.OK)
					return false;
			}
		}

		fJarPackageWizardPage.exportAntScript(status);

		if (!executeExportOperation(fJarPackage.createJarExportRunnable(getShell()), status))
			return false;

		// Save the dialog settings
		if (fHasNewDialogSettings) {
			IDialogSettings workbenchSettings= JavaPlugin.getDefault().getDialogSettings();
			IDialogSettings section= workbenchSettings.getSection(DIALOG_SETTINGS_KEY);
			section= workbenchSettings.addNewSection(DIALOG_SETTINGS_KEY);
			setDialogSettings(section);
		}

		fJarPackageWizardPage.finish();
		return true;
	}

	private boolean hasArchive(Object[] elements) {
		for (int i= 0; i < elements.length; i++) {
			if (elements[i] instanceof IPackageFragmentRoot) {
				IPackageFragmentRoot root= (IPackageFragmentRoot) elements[i];
				if (root.isArchive())
					return true;
			}
		}
		return false;
	}

	void setInitializeFromJarPackage(boolean state) {
		fInitializeFromJarPackage= state;
	}
}

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
package org.eclipse.jdt.ui.wizards;

import org.eclipse.equinox.bidi.StructuredTextTypeHandlerFactory;

import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.util.BidiUtils;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.views.contentoutline.ContentOutline;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.StandardJavaElementContentProvider;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.IViewPartInputProvider;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;

/**
 * Wizard page that acts as a base class for wizard pages that create new Java elements.
 * The class provides a input field for source folders (called container in this class) and
 * API to validate the enter source folder name.
 *
 * <p>
 * Clients may subclass.
 * </p>
 *
 * @since 2.0
 */
public abstract class NewContainerWizardPage extends NewElementWizardPage {

	/** Id of the container field */
	protected static final String CONTAINER= "NewContainerWizardPage.container"; //$NON-NLS-1$

	/** The status of the last validation. */
	protected IStatus fContainerStatus;

	private StringButtonDialogField fContainerDialogField;

	/*
	 * package fragment root corresponding to the input type (can be null)
	 */
	private IPackageFragmentRoot fCurrRoot;

	private IWorkspaceRoot fWorkspaceRoot;

	/**
	 * Create a new <code>NewContainerWizardPage</code>
	 *
	 * @param name the wizard page's name
	 */
	public NewContainerWizardPage(String name) {
		super(name);
		fWorkspaceRoot= ResourcesPlugin.getWorkspace().getRoot();
		ContainerFieldAdapter adapter= new ContainerFieldAdapter();

		fContainerDialogField= new StringButtonDialogField(adapter);
		fContainerDialogField.setDialogFieldListener(adapter);
		fContainerDialogField.setLabelText(getContainerLabel());
		fContainerDialogField.setButtonLabel(NewWizardMessages.NewContainerWizardPage_container_button);

		fContainerStatus= new StatusInfo();
		fCurrRoot= null;
	}

	/**
	 * Returns the label that is used for the container input field.
	 *
	 * @return the label that is used for the container input field.
	 * @since 3.2
	 */
	protected String getContainerLabel() {
		return NewWizardMessages.NewContainerWizardPage_container_label;
	}

	/**
	 * Initializes the source folder field with a valid package fragment root.
	 * The package fragment root is computed from the given Java element.
	 *
	 * @param elem the Java element used to compute the initial package
	 *    fragment root used as the source folder
	 */
	protected void initContainerPage(IJavaElement elem) {
		IPackageFragmentRoot initRoot= null;
		if (elem != null) {
			initRoot= JavaModelUtil.getPackageFragmentRoot(elem);
			try {
				if (initRoot == null || initRoot.getKind() != IPackageFragmentRoot.K_SOURCE) {
					IJavaProject jproject= elem.getJavaProject();
					if (jproject != null) {
							initRoot= null;
							if (jproject.exists()) {
								IPackageFragmentRoot[] roots= jproject.getPackageFragmentRoots();
								for (int i= 0; i < roots.length; i++) {
									if (roots[i].getKind() == IPackageFragmentRoot.K_SOURCE) {
										initRoot= roots[i];
										break;
									}
								}
							}
						if (initRoot == null) {
							initRoot= jproject.getPackageFragmentRoot(jproject.getResource());
						}
					}
				}
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
			}
		}
		setPackageFragmentRoot(initRoot, true);
	}

	/**
	 * Utility method to inspect a selection to find a Java element.
	 *
	 * @param selection the selection to be inspected
	 * @return a Java element to be used as the initial selection, or <code>null</code>,
	 * if no Java element exists in the given selection
	 */
	protected IJavaElement getInitialJavaElement(IStructuredSelection selection) {
		IJavaElement jelem= null;
		if (selection != null && !selection.isEmpty()) {
			Object selectedElement= selection.getFirstElement();
			if (selectedElement instanceof IAdaptable) {
				IAdaptable adaptable= (IAdaptable) selectedElement;

				jelem= (IJavaElement) adaptable.getAdapter(IJavaElement.class);
				if (jelem == null || !jelem.exists()) {
					jelem= null;
					IResource resource= (IResource) adaptable.getAdapter(IResource.class);
					if (resource != null && resource.getType() != IResource.ROOT) {
						while (jelem == null && resource.getType() != IResource.PROJECT) {
							resource= resource.getParent();
							jelem= (IJavaElement) resource.getAdapter(IJavaElement.class);
						}
						if (jelem == null) {
							jelem= JavaCore.create(resource); // java project
						}
					}
				}
			}
		}
		if (jelem == null) {
			IWorkbenchPart part= JavaPlugin.getActivePage().getActivePart();
			if (part instanceof ContentOutline) {
				part= JavaPlugin.getActivePage().getActiveEditor();
			}

			if (part instanceof IViewPartInputProvider) {
				Object elem= ((IViewPartInputProvider)part).getViewPartInput();
				if (elem instanceof IJavaElement) {
					jelem= (IJavaElement) elem;
				}
			}
		}

		if (jelem == null || jelem.getElementType() == IJavaElement.JAVA_MODEL) {
			try {
				IJavaProject[] projects= JavaCore.create(getWorkspaceRoot()).getJavaProjects();
				if (projects.length == 1) {
					IClasspathEntry[] rawClasspath= projects[0].getRawClasspath();
					for (int i= 0; i < rawClasspath.length; i++) {
						if (rawClasspath[i].getEntryKind() == IClasspathEntry.CPE_SOURCE) {// add only if the project contains a source folder
							jelem= projects[0];
							break;
						}
					}
				}
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
			}
		}
		return jelem;
	}

	/**
	 * Returns the text selection of the current editor. <code>null</code> is returned
	 * when the current editor does not have focus or does not return a text selection.
	 * @return Returns the text selection of the current editor or <code>null</code>.
     *
     * @since 3.0
	 */
	protected ITextSelection getCurrentTextSelection() {
		IWorkbenchPart part= JavaPlugin.getActivePage().getActivePart();
		if (part instanceof IEditorPart) {
			ISelectionProvider selectionProvider= part.getSite().getSelectionProvider();
			if (selectionProvider != null) {
				ISelection selection= selectionProvider.getSelection();
				if (selection instanceof ITextSelection) {
					return (ITextSelection) selection;
				}
			}
		}
		return null;
	}


	/**
	 * Returns the recommended maximum width for text fields (in pixels). This
	 * method requires that createContent has been called before this method is
	 * call. Subclasses may override to change the maximum width for text
	 * fields.
	 *
	 * @return the recommended maximum width for text fields.
	 */
	protected int getMaxFieldWidth() {
		return convertWidthInCharsToPixels(40);
	}


	/**
	 * Creates the necessary controls (label, text field and browse button) to edit
	 * the source folder location. The method expects that the parent composite
	 * uses a <code>GridLayout</code> as its layout manager and that the
	 * grid layout has at least 3 columns.
	 *
	 * @param parent the parent composite
	 * @param nColumns the number of columns to span. This number must be
	 *  greater or equal three
	 */
	protected void createContainerControls(Composite parent, int nColumns) {
		fContainerDialogField.doFillIntoGrid(parent, nColumns);
		LayoutUtil.setWidthHint(fContainerDialogField.getTextControl(null), getMaxFieldWidth());
		BidiUtils.applyBidiProcessing(fContainerDialogField.getTextControl(null), StructuredTextTypeHandlerFactory.FILE);
	}

	/**
	 * Sets the focus to the source folder's text field.
	 */
	protected void setFocusOnContainer() {
		fContainerDialogField.setFocus();
	}

	// -------- ContainerFieldAdapter --------

	private class ContainerFieldAdapter implements IStringButtonAdapter, IDialogFieldListener {

		// -------- IStringButtonAdapter
		public void changeControlPressed(DialogField field) {
			containerChangeControlPressed(field);
		}

		// -------- IDialogFieldListener
		public void dialogFieldChanged(DialogField field) {
			containerDialogFieldChanged(field);
		}
	}

	private void containerChangeControlPressed(DialogField field) {
		if (field == fContainerDialogField) {
			// take the current jproject as init element of the dialog
			IPackageFragmentRoot root= chooseContainer();
			if (root != null) {
				setPackageFragmentRoot(root, true);
			}
		}
	}

	private void containerDialogFieldChanged(DialogField field) {
		if (field == fContainerDialogField) {
			fContainerStatus= containerChanged();
		}
		// tell all others
		handleFieldChanged(CONTAINER);
	}

	// ----------- validation ----------

	/**
	 * This method is a hook which gets called after the source folder's
	 * text input field has changed. This default implementation updates
	 * the model and returns an error status. The underlying model
	 * is only valid if the returned status is OK.
	 *
	 * @return the model's error status
	 */
	protected IStatus containerChanged() {
		StatusInfo status= new StatusInfo();

		fCurrRoot= null;
		String str= getPackageFragmentRootText();
		if (str.length() == 0) {
			status.setError(NewWizardMessages.NewContainerWizardPage_error_EnterContainerName);
			return status;
		}
		IPath path= new Path(str);
		IResource res= fWorkspaceRoot.findMember(path);
		if (res != null) {
			int resType= res.getType();
			if (resType == IResource.PROJECT || resType == IResource.FOLDER) {
				IProject proj= res.getProject();
				if (!proj.isOpen()) {
					status.setError(Messages.format(NewWizardMessages.NewContainerWizardPage_error_ProjectClosed, BasicElementLabels.getPathLabel(proj.getFullPath(), false)));
					return status;
				}
				IJavaProject jproject= JavaCore.create(proj);
				fCurrRoot= jproject.getPackageFragmentRoot(res);
				if (res.exists()) {
					if (res.isVirtual()) {
						status.setError(NewWizardMessages.NewContainerWizardPage_error_FolderIsVirtual);
						return status;
					}
					try {
						if (!proj.hasNature(JavaCore.NATURE_ID)) {
							if (resType == IResource.PROJECT) {
								status.setError(NewWizardMessages.NewContainerWizardPage_warning_NotAJavaProject);
							} else {
								status.setWarning(NewWizardMessages.NewContainerWizardPage_warning_NotInAJavaProject);
							}
							return status;
						}
						if (fCurrRoot.isArchive()) {
							status.setError(Messages.format(NewWizardMessages.NewContainerWizardPage_error_ContainerIsBinary, BasicElementLabels.getPathLabel(path, false)));
							return status;
						}
						if (fCurrRoot.getKind() == IPackageFragmentRoot.K_BINARY) {
							status.setWarning(Messages.format(NewWizardMessages.NewContainerWizardPage_warning_inside_classfolder, BasicElementLabels.getPathLabel(path, false)));
						} else if (!jproject.isOnClasspath(fCurrRoot)) {
							status.setWarning(Messages.format(NewWizardMessages.NewContainerWizardPage_warning_NotOnClassPath, BasicElementLabels.getPathLabel(path, false)));
						}
					} catch (JavaModelException e) {
						status.setWarning(NewWizardMessages.NewContainerWizardPage_warning_NotOnClassPath);
					} catch (CoreException e) {
						status.setWarning(NewWizardMessages.NewContainerWizardPage_warning_NotAJavaProject);
					}
				}
				return status;
			} else {
				status.setError(Messages.format(NewWizardMessages.NewContainerWizardPage_error_NotAFolder, BasicElementLabels.getPathLabel(path, false)));
				return status;
			}
		} else {
			status.setError(Messages.format(NewWizardMessages.NewContainerWizardPage_error_ContainerDoesNotExist, BasicElementLabels.getPathLabel(path, false)));
			return status;
		}
	}

	// -------- update message ----------------

	/**
	 * Hook method that gets called when a field on this page has changed. For this page the
	 * method gets called when the source folder field changes.
	 * <p>
	 * Every sub type is responsible to call this method when a field on its page has changed.
	 * Subtypes override (extend) the method to add verification when a own field has a
	 * dependency to an other field. For example the class name input must be verified
	 * again when the package field changes (check for duplicated class names).
	 *
	 * @param fieldName The name of the field that has changed (field id). For the
	 * source folder the field id is <code>CONTAINER</code>
	 */
	protected void handleFieldChanged(String fieldName) {
	}


	// ---- get ----------------

	/**
	 * Returns the workspace root.
	 *
	 * @return the workspace root
	 */
	protected IWorkspaceRoot getWorkspaceRoot() {
		return fWorkspaceRoot;
	}

	/**
	 * Returns the Java project of the currently selected package fragment root or <code>null</code>
	 * if no package fragment root is configured.
	 *
	 * @return The current Java project or <code>null</code>.
	 * @since 3.3
	 */
	public IJavaProject getJavaProject() {
		IPackageFragmentRoot root= getPackageFragmentRoot();
		if (root != null) {
			return root.getJavaProject();
		}
		return null;
	}

	/**
	 * Returns the <code>IPackageFragmentRoot</code> that corresponds to the current
	 * value of the source folder field.
	 *
	 * @return the IPackageFragmentRoot or <code>null</code> if the current source
	 * folder value is not a valid package fragment root
	 *
	 */
	public IPackageFragmentRoot getPackageFragmentRoot() {
		return fCurrRoot;
	}

	/**
	 * Returns the current text of source folder text field.
	 *
	 * @return the text of the source folder text field
	 */
	public String getPackageFragmentRootText() {
		return fContainerDialogField.getText();
	}


	/**
	 * Sets the current source folder (model and text field) to the given package
	 * fragment root.

	 * @param root The new root.
	 * @param canBeModified if <code>false</code> the source folder field can
	 * not be changed by the user. If <code>true</code> the field is editable
	 */
	public void setPackageFragmentRoot(IPackageFragmentRoot root, boolean canBeModified) {
		fCurrRoot= root;
		String str= (root == null) ? "" : root.getPath().makeRelative().toString(); //$NON-NLS-1$
		fContainerDialogField.setText(str);
		fContainerDialogField.setEnabled(canBeModified);
	}

	// ------------- choose source container dialog

	/**
	 * Opens a selection dialog that allows to select a source container.
	 *
	 * @return returns the selected package fragment root  or <code>null</code> if the dialog has been canceled.
	 * The caller typically sets the result to the container input field.
	 * <p>
	 * Clients can override this method if they want to offer a different dialog.
	 * </p>
	 *
	 * @since 3.2
	 */
	protected IPackageFragmentRoot chooseContainer() {
		IJavaElement initElement= getPackageFragmentRoot();
		Class<?>[] acceptedClasses= new Class[] { IPackageFragmentRoot.class, IJavaProject.class };
		TypedElementSelectionValidator validator= new TypedElementSelectionValidator(acceptedClasses, false) {
			@Override
			public boolean isSelectedValid(Object element) {
				try {
					if (element instanceof IJavaProject) {
						IJavaProject jproject= (IJavaProject)element;
						IPath path= jproject.getProject().getFullPath();
						return (jproject.findPackageFragmentRoot(path) != null);
					} else if (element instanceof IPackageFragmentRoot) {
						return (((IPackageFragmentRoot)element).getKind() == IPackageFragmentRoot.K_SOURCE);
					}
					return true;
				} catch (JavaModelException e) {
					JavaPlugin.log(e.getStatus()); // just log, no UI in validation
				}
				return false;
			}
		};

		acceptedClasses= new Class[] { IJavaModel.class, IPackageFragmentRoot.class, IJavaProject.class };
		ViewerFilter filter= new TypedViewerFilter(acceptedClasses) {
			@Override
			public boolean select(Viewer viewer, Object parent, Object element) {
				if (element instanceof IPackageFragmentRoot) {
					try {
						return (((IPackageFragmentRoot)element).getKind() == IPackageFragmentRoot.K_SOURCE);
					} catch (JavaModelException e) {
						JavaPlugin.log(e.getStatus()); // just log, no UI in validation
						return false;
					}
				}
				return super.select(viewer, parent, element);
			}
		};

		StandardJavaElementContentProvider provider= new StandardJavaElementContentProvider();
		ILabelProvider labelProvider= new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT);
		ElementTreeSelectionDialog dialog= new ElementTreeSelectionDialog(getShell(), labelProvider, provider);
		dialog.setValidator(validator);
		dialog.setComparator(new JavaElementComparator());
		dialog.setTitle(NewWizardMessages.NewContainerWizardPage_ChooseSourceContainerDialog_title);
		dialog.setMessage(NewWizardMessages.NewContainerWizardPage_ChooseSourceContainerDialog_description);
		dialog.addFilter(filter);
		dialog.setInput(JavaCore.create(fWorkspaceRoot));
		dialog.setInitialSelection(initElement);
		dialog.setHelpAvailable(false);

		if (dialog.open() == Window.OK) {
			Object element= dialog.getFirstResult();
			if (element instanceof IJavaProject) {
				IJavaProject jproject= (IJavaProject)element;
				return jproject.getPackageFragmentRoot(jproject.getProject());
			} else if (element instanceof IPackageFragmentRoot) {
				return (IPackageFragmentRoot)element;
			}
			return null;
		}
		return null;
	}

}

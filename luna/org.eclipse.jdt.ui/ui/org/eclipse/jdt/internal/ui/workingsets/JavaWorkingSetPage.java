/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Rodrigo Kumpera <kumpera AT gmail.com> - bug 95232
 *
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.workingsets;

import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;

import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.filters.EmptyInnerPackageFilter;
import org.eclipse.jdt.internal.ui.packageview.PackageFragmentRootContainer;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.DecoratingJavaLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;

/**
 * The Java working set page allows the user to create
 * and edit a Java working set.
 * <p>
 * Workspace elements can be added/removed from a tree into
 * a list.
 * </p>
 *
 * @since 2.0
 */
public class JavaWorkingSetPage extends AbstractWorkingSetWizardPage {

	final private static String PAGE_TITLE= WorkingSetMessages.JavaWorkingSetPage_title;
	final private static String PAGE_NAME= "javaWorkingSetPage"; //$NON-NLS-1$

	private IStructuredSelection fInitialSelection;

	/**
	 * Default constructor.
	 */
	public JavaWorkingSetPage() {
		super(PAGE_NAME, PAGE_TITLE, JavaPluginImages.DESC_WIZBAN_JAVA_WORKINGSET);
		setDescription(WorkingSetMessages.JavaWorkingSetPage_workingSet_description);
		IWorkbenchWindow activeWorkbenchWindow= JavaPlugin.getActiveWorkbenchWindow();
		if (activeWorkbenchWindow != null) {
			ISelection selection= activeWorkbenchWindow.getSelectionService().getSelection();
			if (selection instanceof IStructuredSelection)
				fInitialSelection= (IStructuredSelection)selection;
		}
	}

	public void setInitialSelection(IStructuredSelection selection) {
		fInitialSelection= selection;
	}

	@Override
	protected String getPageId() {
		return "org.eclipse.jdt.ui.JavaWorkingSetPage"; //$NON-NLS-1$
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);

		// Set help for the page
		PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, IJavaHelpContextIds.JAVA_WORKING_SET_PAGE);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void configureTree(TreeViewer tree) {
		tree.setContentProvider(new JavaWorkingSetPageContentProvider());

		AppearanceAwareLabelProvider javaElementLabelProvider=
			new AppearanceAwareLabelProvider(
				AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS | JavaElementLabels.P_COMPRESSED,
				AppearanceAwareLabelProvider.DEFAULT_IMAGEFLAGS | JavaElementImageProvider.SMALL_ICONS
			);

		tree.setLabelProvider(new DecoratingJavaLabelProvider(javaElementLabelProvider));
		tree.setComparator(new JavaElementComparator());
		tree.addFilter(new EmptyInnerPackageFilter());

		tree.setInput(JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()));

		if (getSelection() == null)
			return;

		Object[] selection= getInitialTreeSelection();
		if (selection.length > 0) {
			try {
				tree.getTree().setRedraw(false);

				for (int i= 0; i < selection.length; i++) {
					tree.expandToLevel(selection[i], 0);
				}
				tree.setSelection(new StructuredSelection(selection));
			} finally {
				tree.getTree().setRedraw(true);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void configureTable(TableViewer table) {
		AppearanceAwareLabelProvider javaElementLabelProvider= new AppearanceAwareLabelProvider(
				AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS | JavaElementLabels.P_COMPRESSED
				| JavaElementLabels.ROOT_POST_QUALIFIED | JavaElementLabels.P_POST_QUALIFIED,
				AppearanceAwareLabelProvider.DEFAULT_IMAGEFLAGS | JavaElementImageProvider.SMALL_ICONS
			);

		table.setLabelProvider(new DecoratingJavaLabelProvider(javaElementLabelProvider));
		table.setComparator(new JavaElementComparator());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Object[] getInitialWorkingSetElements(IWorkingSet workingSet) {
		Object[] elements;
		if (workingSet == null) {
			if (fInitialSelection == null)
				return new IAdaptable[0];

			elements= fInitialSelection.toArray();
		} else {
			elements= workingSet.getElements();
		}

		// Use closed project for elements in closed project and remove PackageFragmentRootContainer elements
		int deletedElements= 0;
		for (int i= 0; i < elements.length; i++) {
			Object element= elements[i];
			if (element instanceof IResource) {
				IProject project= ((IResource)element).getProject();
				if (!project.isAccessible())
					elements[i]= project;
			} else if (element instanceof PackageFragmentRootContainer) {
				for (int j= i; j < elements.length - 1; j++)
					elements[j]= elements[j + 1];
				deletedElements++;
			} else if (element instanceof IJavaElement) {
				IJavaProject jProject= ((IJavaElement)element).getJavaProject();
				if (jProject != null && !jProject.getProject().isAccessible())
					elements[i]= jProject.getProject();
			} else if (!(element instanceof IAdaptable) || ((IAdaptable)element).getAdapter(IProject.class) == null) {
				for (int j= i; j < elements.length - 1; j++)
					elements[j]= elements[j + 1];
				deletedElements++;
			}
		}

		if (deletedElements == 0)
			return elements;

		IAdaptable[] result= new IAdaptable[elements.length - deletedElements];
		System.arraycopy(elements, 0, result, 0, result.length);
		return result;
	}

	private Object[] getInitialTreeSelection() {
		final Object[][] result= new Object[1][];
		BusyIndicator.showWhile(getShell().getDisplay(), new Runnable() {
			public void run() {
				IStructuredSelection selection= fInitialSelection;
				if (selection == null) {

					IWorkbenchPage page= JavaPlugin.getActivePage();
					if (page == null)
						return;

					IWorkbenchPart part= page.getActivePart();
					if (part == null)
						return;

					try {
						selection= SelectionConverter.getStructuredSelection(part);
					} catch (JavaModelException e) {
						return;
					}
				}

				Object[] elements= selection.toArray();
				for (int i= 0; i < elements.length; i++) {
					if (elements[i] instanceof IResource) {
						IJavaElement je= (IJavaElement)((IResource)elements[i]).getAdapter(IJavaElement.class);
						if (je != null && je.exists() &&  je.getJavaProject().isOnClasspath((IResource)elements[i]))
							elements[i]= je;
					}
				}
				result[0]= elements;
			}
		});

		if (result[0] == null)
			return new Object[0];

		return result[0];
	}
}

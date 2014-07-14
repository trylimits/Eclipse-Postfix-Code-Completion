/*******************************************************************************
 * Copyright (c) 2000, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.browsing;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.IPlaceholderFolderLayout;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.progress.IProgressConstants;

import org.eclipse.ui.texteditor.templates.TemplatesView;

import org.eclipse.debug.ui.IDebugUIConstants;

import org.eclipse.search.ui.NewSearchUI;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;


public class JavaBrowsingPerspectiveFactory implements IPerspectiveFactory {

	/*
	 * XXX: This is a workaround for: http://dev.eclipse.org/bugs/show_bug.cgi?id=13070
	 */
	static IJavaElement fgJavaElementFromAction;

	/**
	 * Constructs a new Default layout engine.
	 */
	public JavaBrowsingPerspectiveFactory() {
		super();
	}

	public void createInitialLayout(IPageLayout layout) {
		if (stackBrowsingViewsVertically())
			createVerticalLayout(layout);
		else
			createHorizontalLayout(layout);

		// action sets
		layout.addActionSet(IDebugUIConstants.LAUNCH_ACTION_SET);
		layout.addActionSet(JavaUI.ID_ACTION_SET);
		layout.addActionSet(JavaUI.ID_ELEMENT_CREATION_ACTION_SET);
		layout.addActionSet(IPageLayout.ID_NAVIGATE_ACTION_SET);

		// views - java
		layout.addShowViewShortcut(JavaUI.ID_TYPE_HIERARCHY);
		layout.addShowViewShortcut(JavaUI.ID_PACKAGES);
		layout.addShowViewShortcut(JavaUI.ID_PROJECTS_VIEW);
		layout.addShowViewShortcut(JavaUI.ID_PACKAGES_VIEW);
		layout.addShowViewShortcut(JavaUI.ID_TYPES_VIEW);
		layout.addShowViewShortcut(JavaUI.ID_MEMBERS_VIEW);
		layout.addShowViewShortcut(JavaUI.ID_SOURCE_VIEW);
		layout.addShowViewShortcut(JavaUI.ID_JAVADOC_VIEW);
		layout.addShowViewShortcut(TemplatesView.ID);

		// views - search
		layout.addShowViewShortcut(NewSearchUI.SEARCH_VIEW_ID);

		// views - debugging
		layout.addShowViewShortcut(IConsoleConstants.ID_CONSOLE_VIEW);

		// views - standard workbench
		layout.addShowViewShortcut(IPageLayout.ID_OUTLINE);
		layout.addShowViewShortcut(IPageLayout.ID_PROBLEM_VIEW);
		layout.addShowViewShortcut(JavaPlugin.ID_RES_NAV);
		layout.addShowViewShortcut(IPageLayout.ID_TASK_LIST);
		layout.addShowViewShortcut(IProgressConstants.PROGRESS_VIEW_ID);
		layout.addShowViewShortcut(IPageLayout.ID_PROJECT_EXPLORER);
		layout.addShowViewShortcut("org.eclipse.pde.runtime.LogView"); //$NON-NLS-1$

		// new actions - Java project creation wizard
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.JavaProjectWizard"); //$NON-NLS-1$
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewPackageCreationWizard"); //$NON-NLS-1$
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewClassCreationWizard"); //$NON-NLS-1$
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewInterfaceCreationWizard"); //$NON-NLS-1$
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewEnumCreationWizard"); //$NON-NLS-1$
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewAnnotationCreationWizard"); //$NON-NLS-1$
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewSourceFolderCreationWizard");	 //$NON-NLS-1$
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewSnippetFileCreationWizard"); //$NON-NLS-1$
		layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewJavaWorkingSetWizard"); //$NON-NLS-1$
		layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.folder");//$NON-NLS-1$
		layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.file");//$NON-NLS-1$
		layout.addNewWizardShortcut("org.eclipse.ui.editors.wizards.UntitledTextFileWizard");//$NON-NLS-1$

		// 'Window' > 'Open Perspective' contributions
		layout.addPerspectiveShortcut(JavaUI.ID_PERSPECTIVE);
		layout.addPerspectiveShortcut(IDebugUIConstants.ID_DEBUG_PERSPECTIVE);

	}

	private void createVerticalLayout(IPageLayout layout) {
		String relativePartId= IPageLayout.ID_EDITOR_AREA;
		int relativePos= IPageLayout.LEFT;

		IPlaceholderFolderLayout placeHolderLeft= layout.createPlaceholderFolder("left", IPageLayout.LEFT, (float)0.25, IPageLayout.ID_EDITOR_AREA); //$NON-NLS-1$
		placeHolderLeft.addPlaceholder(JavaUI.ID_TYPE_HIERARCHY);
		placeHolderLeft.addPlaceholder(IPageLayout.ID_OUTLINE);
		placeHolderLeft.addPlaceholder(JavaUI.ID_PACKAGES);
		placeHolderLeft.addPlaceholder(JavaPlugin.ID_RES_NAV);
		placeHolderLeft.addPlaceholder(IPageLayout.ID_PROJECT_EXPLORER);

		if (shouldShowProjectsView()) {
			layout.addView(JavaUI.ID_PROJECTS_VIEW, IPageLayout.LEFT, (float)0.25, IPageLayout.ID_EDITOR_AREA);
			relativePartId= JavaUI.ID_PROJECTS_VIEW;
			relativePos= IPageLayout.BOTTOM;
		}
		if (shouldShowPackagesView()) {
			layout.addView(JavaUI.ID_PACKAGES_VIEW, relativePos, (float)0.25, relativePartId);
			relativePartId= JavaUI.ID_PACKAGES_VIEW;
			relativePos= IPageLayout.BOTTOM;
		}
		layout.addView(JavaUI.ID_TYPES_VIEW, relativePos, (float)0.33, relativePartId);
		layout.addView(JavaUI.ID_MEMBERS_VIEW, IPageLayout.BOTTOM, (float)0.50, JavaUI.ID_TYPES_VIEW);

		IPlaceholderFolderLayout placeHolderBottom= layout.createPlaceholderFolder("bottom", IPageLayout.BOTTOM, (float)0.75, IPageLayout.ID_EDITOR_AREA); //$NON-NLS-1$
		placeHolderBottom.addPlaceholder(IPageLayout.ID_PROBLEM_VIEW);
		placeHolderBottom.addPlaceholder(NewSearchUI.SEARCH_VIEW_ID);
		placeHolderBottom.addPlaceholder(IConsoleConstants.ID_CONSOLE_VIEW);
		placeHolderBottom.addPlaceholder(IPageLayout.ID_BOOKMARKS);
		placeHolderBottom.addPlaceholder(JavaUI.ID_SOURCE_VIEW);
		placeHolderBottom.addPlaceholder(JavaUI.ID_JAVADOC_VIEW);
		placeHolderBottom.addPlaceholder(IProgressConstants.PROGRESS_VIEW_ID);
	}

	private void createHorizontalLayout(IPageLayout layout) {
		String relativePartId= IPageLayout.ID_EDITOR_AREA;
		int relativePos= IPageLayout.TOP;

		if (shouldShowProjectsView()) {
			layout.addView(JavaUI.ID_PROJECTS_VIEW, IPageLayout.TOP, (float)0.25, IPageLayout.ID_EDITOR_AREA);
			relativePartId= JavaUI.ID_PROJECTS_VIEW;
			relativePos= IPageLayout.RIGHT;
		}
		if (shouldShowPackagesView()) {
			layout.addView(JavaUI.ID_PACKAGES_VIEW, relativePos, (float)0.25, relativePartId);
			relativePartId= JavaUI.ID_PACKAGES_VIEW;
			relativePos= IPageLayout.RIGHT;
		}
		layout.addView(JavaUI.ID_TYPES_VIEW, relativePos, (float)0.33, relativePartId);
		layout.addView(JavaUI.ID_MEMBERS_VIEW, IPageLayout.RIGHT, (float)0.50, JavaUI.ID_TYPES_VIEW);

		IPlaceholderFolderLayout placeHolderLeft= layout.createPlaceholderFolder("left", IPageLayout.LEFT, (float)0.25, IPageLayout.ID_EDITOR_AREA); //$NON-NLS-1$
		placeHolderLeft.addPlaceholder(JavaUI.ID_TYPE_HIERARCHY);
		placeHolderLeft.addPlaceholder(IPageLayout.ID_OUTLINE);
		placeHolderLeft.addPlaceholder(JavaUI.ID_PACKAGES);
		placeHolderLeft.addPlaceholder(JavaPlugin.ID_RES_NAV);
		placeHolderLeft.addPlaceholder(IPageLayout.ID_PROJECT_EXPLORER);


		IPlaceholderFolderLayout placeHolderBottom= layout.createPlaceholderFolder("bottom", IPageLayout.BOTTOM, (float)0.75, IPageLayout.ID_EDITOR_AREA); //$NON-NLS-1$
		placeHolderBottom.addPlaceholder(IPageLayout.ID_PROBLEM_VIEW);
		placeHolderBottom.addPlaceholder(NewSearchUI.SEARCH_VIEW_ID);
		placeHolderBottom.addPlaceholder(IConsoleConstants.ID_CONSOLE_VIEW);
		placeHolderBottom.addPlaceholder(IPageLayout.ID_BOOKMARKS);
		placeHolderBottom.addPlaceholder(JavaUI.ID_SOURCE_VIEW);
		placeHolderBottom.addPlaceholder(JavaUI.ID_JAVADOC_VIEW);
		placeHolderBottom.addPlaceholder(IProgressConstants.PROGRESS_VIEW_ID);
	}

	private boolean shouldShowProjectsView() {
		return fgJavaElementFromAction == null || fgJavaElementFromAction.getElementType() == IJavaElement.JAVA_MODEL;
	}

	private boolean shouldShowPackagesView() {
		if (fgJavaElementFromAction == null)
			return true;
		int type= fgJavaElementFromAction.getElementType();
		return type == IJavaElement.JAVA_MODEL || type == IJavaElement.JAVA_PROJECT || type == IJavaElement.PACKAGE_FRAGMENT_ROOT;
	}

	private boolean stackBrowsingViewsVertically() {
		return PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.BROWSING_STACK_VERTICALLY);
	}

	/*
	 * XXX: This is a workaround for: http://dev.eclipse.org/bugs/show_bug.cgi?id=13070
	 */
	static void setInputFromAction(IAdaptable input) {
		if (input instanceof IJavaElement)
			fgJavaElementFromAction= (IJavaElement)input;
		else
			fgJavaElementFromAction= null;
	}
}

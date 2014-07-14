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
package org.eclipse.jdt.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.IJavaStatusConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.browsing.LogicalPackage;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.OpenTypeHierarchyUtil;

/**
 * This action opens a type hierarchy on the selected type.
 * <p>
 * The action is applicable to selections containing elements of type
 * <code>IType</code>.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class OpenTypeHierarchyAction extends SelectionDispatchAction {

	private JavaEditor fEditor;

	/**
	 * Creates a new <code>OpenTypeHierarchyAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public OpenTypeHierarchyAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.OpenTypeHierarchyAction_label);
		setToolTipText(ActionMessages.OpenTypeHierarchyAction_tooltip);
		setDescription(ActionMessages.OpenTypeHierarchyAction_description);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.OPEN_TYPE_HIERARCHY_ACTION);
	}

	/**
	 * Creates a new <code>OpenTypeHierarchyAction</code>. The action requires
	 * that the selection provided by the given selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 * @param provider a special selection provider which is used instead
	 *  of the site's selection provider or <code>null</code> to use the site's
	 *  selection provider
	 *
	 * @since 3.2
	 * @deprecated Use {@link #setSpecialSelectionProvider(ISelectionProvider)} instead. This API will be
	 * removed after 3.2 M5.
     */
    public OpenTypeHierarchyAction(IWorkbenchSite site, ISelectionProvider provider) {
        this(site);
        setSpecialSelectionProvider(provider);
    }


	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public OpenTypeHierarchyAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor));
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		setEnabled(isEnabled(selection));
	}

	private boolean isEnabled(IStructuredSelection selection) {
		Object[] elements= selection.toArray();
		if (elements.length == 0)
			return false;
		
		if (elements.length == 1) {
			Object input= elements[0];
			if (input instanceof LogicalPackage)
				return true;
			if (!(input instanceof IJavaElement))
				return false;

			switch (((IJavaElement)input).getElementType()) {
				case IJavaElement.INITIALIZER:
				case IJavaElement.METHOD:
				case IJavaElement.FIELD:
				case IJavaElement.TYPE:
				case IJavaElement.IMPORT_DECLARATION:
				case IJavaElement.CLASS_FILE:
				case IJavaElement.COMPILATION_UNIT:
					return true;
				case IJavaElement.LOCAL_VARIABLE:
				case IJavaElement.TYPE_PARAMETER:
				case IJavaElement.ANNOTATION:
					return false;
				default:
					// continue below
			}
		}
		
		// strategy: allow non-IJavaElements (e.g. an IResource), but stop for invalid IJavaElements
		boolean hasValidElement= false;
		for (int j= 0; j < elements.length; j++) {
			Object input= elements[j];
			if (input instanceof LogicalPackage) {
				hasValidElement= true;
				continue;
			}
			if (!(input instanceof IJavaElement))
				continue;
			
			switch (((IJavaElement)input).getElementType()) {
				case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				case IJavaElement.JAVA_PROJECT:
				case IJavaElement.PACKAGE_FRAGMENT:
				case IJavaElement.PACKAGE_DECLARATION:
					hasValidElement= true;
					continue;
				default:
					return false;
			}
		}
		return hasValidElement;
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(ITextSelection selection) {
		IJavaElement input= SelectionConverter.getInput(fEditor);
		if (!ActionUtil.isProcessable(getShell(), input))
			return;

		try {
			IJavaElement[] elements= SelectionConverter.codeResolveOrInputForked(fEditor);
			if (elements == null)
				return;
			List<IJavaElement> candidates= new ArrayList<IJavaElement>(elements.length);
			for (int i= 0; i < elements.length; i++) {
				IJavaElement[] resolvedElements= OpenTypeHierarchyUtil.getCandidates(elements[i]);
				if (resolvedElements != null)
					candidates.addAll(Arrays.asList(resolvedElements));
			}
			run(candidates.toArray(new IJavaElement[candidates.size()]));
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.SelectionConverter_codeResolve_failed);
		} catch (InterruptedException e) {
			// cancelled
		}
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(IStructuredSelection selection) {
		List<IJavaElement> validElements= new ArrayList<IJavaElement>();
		Object[] selectedElements= selection.toArray();

		for (int i= 0; i < selectedElements.length; i++) {
			Object input= selectedElements[i];
			if (input instanceof LogicalPackage) {
				IPackageFragment[] fragments= ((LogicalPackage)input).getFragments();
				if (fragments.length == 0)
					continue;
				for (int j= 0; j < fragments.length; j++) {
					validElements.add(fragments[j]);
				}
			} else if (input instanceof IPackageFragment) {
				IPackageFragment fragment= (IPackageFragment)input;
				IPackageFragmentRoot[] roots;
				try {
					roots= fragment.getJavaProject().getPackageFragmentRoots();
				} catch (JavaModelException e) {
					JavaPlugin.log(e);
					continue;
				}
				String name= fragment.getElementName();
				for (int j= 0; j < roots.length; j++) {
					IPackageFragment pack= roots[j].getPackageFragment(name);
					if (pack.exists())
						validElements.add(pack);
				}
			} else {
				if (!(input instanceof IJavaElement) || !ActionUtil.isProcessable(getShell(), (IJavaElement)input))
					continue;
				IJavaElement element= (IJavaElement)input;
				validElements.add(element);
			}
		}
		if (validElements.size() == 0) {
			IStatus status= createStatus(ActionMessages.OpenTypeHierarchyAction_messages_no_java_elements);
			ErrorDialog.openError(getShell(), getDialogTitle(), ActionMessages.OpenTypeHierarchyAction_messages_title, status);
			return;
		}
		List<IJavaElement> result= new ArrayList<IJavaElement>();
		IStatus status= compileCandidates(result, validElements);
		if (status.isOK()) {
			run(result.toArray(new IJavaElement[result.size()]));
		} else {
			ErrorDialog.openError(getShell(), getDialogTitle(), ActionMessages.OpenTypeHierarchyAction_messages_title, status);
		}
	}

	/*
	 * No Javadoc since the method isn't meant to be public but is
	 * since the beginning
	 */
	public void run(IJavaElement[] elements) {
		if (elements.length == 0) {
			getShell().getDisplay().beep();
			return;
		}
		OpenTypeHierarchyUtil.open(elements, getSite().getWorkbenchWindow());
	}

	private static String getDialogTitle() {
		return ActionMessages.OpenTypeHierarchyAction_dialog_title;
	}

	private static IStatus compileCandidates(List<IJavaElement> result, List<IJavaElement> elements) {
		IStatus ok= Status.OK_STATUS;
		boolean onlyContainers= true;
		for (Iterator<IJavaElement> iter= elements.iterator(); iter.hasNext();) {
			IJavaElement elem= iter.next();
			try {
				switch (elem.getElementType()) {
					case IJavaElement.INITIALIZER:
					case IJavaElement.METHOD:
					case IJavaElement.FIELD:
					case IJavaElement.TYPE:
						onlyContainers= false;
						//$FALL-THROUGH$
					case IJavaElement.PACKAGE_FRAGMENT_ROOT:
					case IJavaElement.JAVA_PROJECT:
						result.add(elem);
						break;
					case IJavaElement.PACKAGE_FRAGMENT:
						if (((IPackageFragment)elem).containsJavaResources())
							result.add(elem);
						break;
					case IJavaElement.PACKAGE_DECLARATION:
						result.add(elem.getAncestor(IJavaElement.PACKAGE_FRAGMENT));
						break;
					case IJavaElement.IMPORT_DECLARATION:
						IImportDeclaration decl= (IImportDeclaration)elem;
						if (decl.isOnDemand())
							elem= JavaModelUtil.findTypeContainer(elem.getJavaProject(), Signature.getQualifier(elem.getElementName()));
						else
							elem= elem.getJavaProject().findType(elem.getElementName());
						if (elem != null) {
							onlyContainers= false;
							result.add(elem);
						}
						break;
					case IJavaElement.CLASS_FILE:
						onlyContainers= false;
						result.add(((IClassFile)elem).getType());
						break;
					case IJavaElement.COMPILATION_UNIT:
						ICompilationUnit cu= (ICompilationUnit)elem;
						IType[] types= cu.getTypes();
						if (types.length > 0) {
							onlyContainers= false;
							result.addAll(Arrays.asList(types));
						}
				}
			} catch (JavaModelException e) {
				return e.getStatus();
			}
		}
		int size= result.size();
		if (size == 0 || (size > 1 && !onlyContainers))
			return createStatus(ActionMessages.OpenTypeHierarchyAction_messages_no_valid_java_element);
		return ok;
	}

	private static IStatus createStatus(String message) {
		return new Status(IStatus.INFO, JavaPlugin.getPluginId(), IJavaStatusConstants.INTERNAL_ERROR, message, null);
	}
}

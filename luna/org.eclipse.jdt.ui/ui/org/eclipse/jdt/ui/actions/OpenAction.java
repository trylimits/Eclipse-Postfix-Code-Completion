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
package org.eclipse.jdt.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.core.resources.IFile;

import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.util.OpenStrategy;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Region;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import org.eclipse.ui.texteditor.IEditorStatusLine;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaElementHyperlinkDetector;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart;
import org.eclipse.jdt.internal.ui.search.IOccurrencesFinder.OccurrenceLocation;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;


/**
 * This action opens a Java editor on a Java element or file.
 * <p>
 * The action is applicable to selections containing elements of
 * type <code>ICompilationUnit</code>, <code>IMember</code>
 * or <code>IFile</code>.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class OpenAction extends SelectionDispatchAction {

	private JavaEditor fEditor;

	/**
	 * Creates a new <code>OpenAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public OpenAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.OpenAction_label);
		setToolTipText(ActionMessages.OpenAction_tooltip);
		setDescription(ActionMessages.OpenAction_description);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.OPEN_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public OpenAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setText(ActionMessages.OpenAction_declaration_label);
		setEnabled(EditorUtility.getEditorInputJavaElement(fEditor, false) != null);
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
		setEnabled(checkEnabled(selection));
	}

	private boolean checkEnabled(IStructuredSelection selection) {
		if (selection.isEmpty())
			return false;
		for (Iterator<?> iter= selection.iterator(); iter.hasNext();) {
			Object element= iter.next();
			if (element instanceof ISourceReference)
				continue;
			if (element instanceof IFile)
				continue;
			if (JavaModelUtil.isOpenableStorage(element))
				continue;
			return false;
		}
		return true;
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(ITextSelection selection) {
		ITypeRoot input= EditorUtility.getEditorInputJavaElement(fEditor, false);
		if (input == null) {
			setStatusLineMessage();
			return;
		}
		IRegion region= new Region(selection.getOffset(), selection.getLength());
		OccurrenceLocation location= JavaElementHyperlinkDetector.findBreakOrContinueTarget(input, region);
		if (location != null) {
			fEditor.selectAndReveal(location.getOffset(), location.getLength());
			return;
		}
		try {
			IJavaElement[] elements= SelectionConverter.codeResolveForked(fEditor, false);
			elements= selectOpenableElements(elements);
			if (elements == null || elements.length == 0) {
				if (!ActionUtil.isProcessable(fEditor))
					return;
				setStatusLineMessage();
				return;
			}

			IJavaElement element= elements[0];
			if (elements.length > 1) {
				// If there are multiple IPackageFragments that could be selected, use the first one on the build path.
				if (!(element instanceof IPackageFragment)) {
					element= SelectionConverter.selectJavaElement(elements, getShell(), getDialogTitle(), ActionMessages.OpenAction_select_element);
					if (element == null)
						return;
				}
			}

			run(new Object[] {element} );
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.OpenAction_error_message);
		} catch (InterruptedException e) {
			// ignore
		}
	}

	/**
	 * Sets the error message in the status line.
	 * 
	 * @since 3.7
	 */
	private void setStatusLineMessage() {
		IEditorStatusLine statusLine= (IEditorStatusLine) fEditor.getAdapter(IEditorStatusLine.class);
		if (statusLine != null)
			statusLine.setMessage(true, ActionMessages.OpenAction_error_messageBadSelection, null);
		getShell().getDisplay().beep();
		return;
	}

	/**
	 * Selects the openable elements out of the given ones.
	 *
	 * @param elements the elements to filter
	 * @return the openable elements
	 * @since 3.4
	 */
	private IJavaElement[] selectOpenableElements(IJavaElement[] elements) {
		List<IJavaElement> result= new ArrayList<IJavaElement>(elements.length);
		for (int i= 0; i < elements.length; i++) {
			IJavaElement element= elements[i];
			switch (element.getElementType()) {
				case IJavaElement.PACKAGE_DECLARATION:
				case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				case IJavaElement.JAVA_PROJECT:
				case IJavaElement.JAVA_MODEL:
					break;
				default:
					result.add(element);
					break;
			}
		}
		return result.toArray(new IJavaElement[result.size()]);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(IStructuredSelection selection) {
		if (!checkEnabled(selection))
			return;
		run(selection.toArray());
	}

	/**
	 * Note: this method is for internal use only. Clients should not call this method.
	 *
	 * @param elements the elements to process
	 *
	 * @noreference This method is not intended to be referenced by clients.
	 */
	public void run(Object[] elements) {
		if (elements == null)
			return;

		MultiStatus status= new MultiStatus(JavaUI.ID_PLUGIN, IStatus.OK, ActionMessages.OpenAction_multistatus_message, null);

		for (int i= 0; i < elements.length; i++) {
			Object element= elements[i];
			try {
				Object javaElement= getElementToOpen(element);
				if (javaElement instanceof IPackageFragment) {
					if (fEditor == null) {
						try {
							PackageExplorerPart view= (PackageExplorerPart) JavaPlugin.getActivePage().showView(JavaUI.ID_PACKAGES);
							view.tryToReveal(element);
						} catch (PartInitException e) {
							JavaPlugin.log(e);
						}
					} else {
						setStatusLineMessage();
						return;
					}
					
				} else {
					boolean activateOnOpen= fEditor != null ? true : OpenStrategy.activateOnOpen();
					IEditorPart part= EditorUtility.openInEditor(javaElement, activateOnOpen);
					if (part != null && javaElement instanceof IJavaElement)
						JavaUI.revealInEditor(part, (IJavaElement) javaElement);
				}
			} catch (PartInitException e) {
				String message= Messages.format(ActionMessages.OpenAction_error_problem_opening_editor, new String[] { JavaElementLabels.getTextLabel(element, JavaElementLabels.ALL_DEFAULT), e.getStatus().getMessage() });
				status.add(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.ERROR, message, null));
			} catch (CoreException e) {
				String message= Messages.format(ActionMessages.OpenAction_error_problem_opening_editor, new String[] { JavaElementLabels.getTextLabel(element, JavaElementLabels.ALL_DEFAULT), e.getStatus().getMessage() });
				status.add(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.ERROR, message, null));
				JavaPlugin.log(e);
			}
		}
		if (!status.isOK()) {
			IStatus[] children= status.getChildren();
			ErrorDialog.openError(getShell(), getDialogTitle(), ActionMessages.OpenAction_error_message, children.length == 1 ? children[0] : status);
		}
	}

	/**
	 * Note: this method is for internal use only. Clients should not call this method.
	 *
	 * @param object the element to open
	 * @return the real element to open
	 * @throws JavaModelException if an error occurs while accessing the Java model
	 *
	 * @noreference This method is not intended to be referenced by clients.
	 */
	public Object getElementToOpen(Object object) throws JavaModelException {
		if (object instanceof IPackageFragment) {
			return getPackageFragmentObjectToOpen((IPackageFragment) object);
		}
		return object;
	}

	private Object getPackageFragmentObjectToOpen(IPackageFragment packageFragment) throws JavaModelException {
		ITypeRoot typeRoot= null;
		IPackageFragmentRoot root= (IPackageFragmentRoot) packageFragment.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
		if (root.getKind() == IPackageFragmentRoot.K_BINARY)
			typeRoot= (packageFragment).getClassFile(JavaModelUtil.PACKAGE_INFO_CLASS);
		else
			typeRoot= (packageFragment).getCompilationUnit(JavaModelUtil.PACKAGE_INFO_JAVA);
		if (typeRoot.exists())
			return typeRoot;
		
		Object[] nonJavaResources= (packageFragment).getNonJavaResources();
		for (Object nonJavaResource : nonJavaResources) {
			if (nonJavaResource instanceof IFile) {
				IFile file= (IFile) nonJavaResource;
				if (file.exists() && JavaModelUtil.PACKAGE_HTML.equals(file.getName())) {
					return file;
				}
			}
		}
		return packageFragment;
	}

	private String getDialogTitle() {
		return ActionMessages.OpenAction_error_title;
	}
}

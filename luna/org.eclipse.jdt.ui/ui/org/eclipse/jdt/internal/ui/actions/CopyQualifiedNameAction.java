/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.actions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.osgi.util.TextProcessor;

import org.eclipse.swt.SWTError;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Point;

import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.source.ISourceViewer;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ResourceTransfer;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclaration;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.browsing.LogicalPackage;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.viewsupport.BindingLabelProvider;


public class CopyQualifiedNameAction extends SelectionDispatchAction {

	private static final long LABEL_FLAGS= new Long(JavaElementLabels.F_FULLY_QUALIFIED | JavaElementLabels.M_FULLY_QUALIFIED | JavaElementLabels.I_FULLY_QUALIFIED
			| JavaElementLabels.T_FULLY_QUALIFIED | JavaElementLabels.M_PARAMETER_TYPES | JavaElementLabels.USE_RESOLVED | JavaElementLabels.T_TYPE_PARAMETERS | JavaElementLabels.CU_QUALIFIED
			| JavaElementLabels.CF_QUALIFIED).longValue();

	//TODO: Make API
	public static final String ACTION_DEFINITION_ID= "org.eclipse.jdt.ui.edit.text.java.copy.qualified.name"; //$NON-NLS-1$

	//TODO: Make API
	public static final String ACTION_HANDLER_ID= "org.eclipse.jdt.ui.actions.CopyQualifiedName"; //$NON-NLS-1$

	private JavaEditor fEditor;

	public CopyQualifiedNameAction(JavaEditor editor) {
		this(editor.getSite());
		fEditor= editor;
		setEnabled(true);
	}

	public CopyQualifiedNameAction(IWorkbenchSite site) {
		super(site);

		setText(ActionMessages.CopyQualifiedNameAction_ActionName);
		setToolTipText(ActionMessages.CopyQualifiedNameAction_ToolTipText);
		setDisabledImageDescriptor(JavaPluginImages.DESC_DLCL_COPY_QUALIFIED_NAME);
		setImageDescriptor(JavaPluginImages.DESC_ELCL_COPY_QUALIFIED_NAME);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.COPY_QUALIFIED_NAME_ACTION);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		setEnabled(canEnable(selection.toArray()));
	}

	@Override
	public void selectionChanged(ITextSelection selection) {
		//Must not create an AST
	}

	private boolean canEnable(Object[] objects) {
		for (int i= 0; i < objects.length; i++) {
			Object element= objects[i];
			if (isValidElement(element))
				return true;
		}

		return false;
	}

	private boolean isValidElement(Object element) {
		if (element instanceof IMember)
			return true;

		if (element instanceof IClassFile)
			return true;

		if (element instanceof ICompilationUnit)
			return true;

		if (element instanceof IPackageDeclaration)
			return true;

		if (element instanceof IImportDeclaration)
			return true;

		if (element instanceof IPackageFragment)
			return true;

		if (element instanceof IPackageFragmentRoot)
			return true;

		if (element instanceof IJavaProject)
			return true;

		if (element instanceof IJarEntryResource)
			return true;

		if (element instanceof IResource)
			return true;

		if (element instanceof LogicalPackage)
			return true;

		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.action.Action#run()
	 */
	@Override
	public void run() {

		try {
			Object[] elements= getSelectedElements();
			if (elements == null) {
				MessageDialog.openInformation(getShell(), ActionMessages.CopyQualifiedNameAction_InfoDialogTitel, ActionMessages.CopyQualifiedNameAction_NoElementToQualify);
				return;
			}

			Object[] data= null;
			Transfer[] dataTypes= null;

			if (elements.length == 1) {
				Object element= elements[0];
				String qualifiedName= getQualifiedName(element);
				IResource resource= null;
				if (element instanceof IJavaElement) {
					IJavaElement je= ((IJavaElement)element);
					if (je.exists())
						resource= je.getCorrespondingResource();
				} else if (element instanceof IResource)
					resource= (IResource)element;

				if (resource != null) {
					IPath location= resource.getLocation();
					if (location != null) {
						data= new Object[] { qualifiedName, resource, new String[] { location.toOSString() } };
						dataTypes= new Transfer[] { TextTransfer.getInstance(), ResourceTransfer.getInstance(), FileTransfer.getInstance() };
					} else {
						data= new Object[] { qualifiedName, resource };
						dataTypes= new Transfer[] { TextTransfer.getInstance(), ResourceTransfer.getInstance() };
					}
				} else {
					data= new Object[] { qualifiedName };
					dataTypes= new Transfer[] { TextTransfer.getInstance() };
				}
			} else {
				StringBuffer buf= new StringBuffer();
				buf.append(getQualifiedName(elements[0]));
				for (int i= 1; i < elements.length; i++) {
					String qualifiedName= getQualifiedName(elements[i]);
					buf.append(System.getProperty("line.separator")).append(qualifiedName); //$NON-NLS-1$
				}
				data= new Object[] { buf.toString() };
				dataTypes= new Transfer[] { TextTransfer.getInstance() };
			}

			Clipboard clipboard= new Clipboard(getShell().getDisplay());
			try {
				clipboard.setContents(data, dataTypes);
			} catch (SWTError e) {
				if (e.code != DND.ERROR_CANNOT_SET_CLIPBOARD) {
					throw e;
				}
				if (MessageDialog.openQuestion(getShell(), ActionMessages.CopyQualifiedNameAction_ErrorTitle, ActionMessages.CopyQualifiedNameAction_ErrorDescription)) {
					clipboard.setContents(data, dataTypes);
				}
			} finally {
				clipboard.dispose();
			}
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		}
	}

	private String getQualifiedName(Object element) throws JavaModelException {
		if (element instanceof IResource)
			return ((IResource)element).getFullPath().toString();

		if (element instanceof IJarEntryResource)
			return ((IJarEntryResource)element).getFullPath().toString();

		if (element instanceof LogicalPackage)
			return ((LogicalPackage)element).getElementName();

		if (element instanceof IJavaProject || element instanceof IPackageFragmentRoot || element instanceof ITypeRoot) {
			IResource resource= ((IJavaElement)element).getCorrespondingResource();
			if (resource != null)
				return getQualifiedName(resource);
		}

		if (element instanceof IBinding)
			return BindingLabelProvider.getBindingLabel((IBinding)element, LABEL_FLAGS);

		return TextProcessor.deprocess(JavaElementLabels.getTextLabel(element, LABEL_FLAGS));
	}

	private Object[] getSelectedElements() {
		if (fEditor != null) {
			Object element= getSelectedElement(fEditor);
			if (element == null)
				return null;

			return new Object[] { element };
		}

		ISelection selection= getSelection();
		if (!(selection instanceof IStructuredSelection))
			return null;

		List<Object> result= new ArrayList<Object>();
		for (Iterator<?> iter= ((IStructuredSelection)selection).iterator(); iter.hasNext();) {
			Object element= iter.next();
			if (isValidElement(element))
				result.add(element);
		}
		if (result.isEmpty())
			return null;

		return result.toArray(new Object[result.size()]);
	}

	private Object getSelectedElement(JavaEditor editor) {
		ISourceViewer viewer= editor.getViewer();
		if (viewer == null)
			return null;

		Point selectedRange= viewer.getSelectedRange();
		int length= selectedRange.y;
		int offset= selectedRange.x;

		ITypeRoot element= JavaUI.getEditorInputTypeRoot(editor.getEditorInput());
		if (element == null)
			return null;

		CompilationUnit ast= SharedASTProvider.getAST(element, SharedASTProvider.WAIT_YES, null);
		if (ast == null)
			return null;

		NodeFinder finder= new NodeFinder(ast, offset, length);
		ASTNode node= finder.getCoveringNode();

		IBinding binding= null;
		if (node instanceof Name) {
			binding= getConstructorBindingIfAvailable((Name)node);
			if (binding != null)
				return binding;
			binding= ((Name)node).resolveBinding();
		} else if (node instanceof MethodInvocation) {
			binding= ((MethodInvocation)node).resolveMethodBinding();
		} else if (node instanceof MethodDeclaration) {
			binding= ((MethodDeclaration)node).resolveBinding();
		} else if (node instanceof Type) {
			binding= ((Type)node).resolveBinding();
		} else if (node instanceof AnonymousClassDeclaration) {
			binding= ((AnonymousClassDeclaration)node).resolveBinding();
		} else if (node instanceof TypeDeclaration) {
			binding= ((TypeDeclaration)node).resolveBinding();
		} else if (node instanceof CompilationUnit) {
			return ((CompilationUnit)node).getJavaElement();
		} else if (node instanceof Expression) {
			binding= ((Expression)node).resolveTypeBinding();
		} else if (node instanceof ImportDeclaration) {
			binding= ((ImportDeclaration)node).resolveBinding();
		} else if (node instanceof MemberRef) {
			binding= ((MemberRef)node).resolveBinding();
		} else if (node instanceof MemberValuePair) {
			binding= ((MemberValuePair)node).resolveMemberValuePairBinding();
		} else if (node instanceof PackageDeclaration) {
			binding= ((PackageDeclaration)node).resolveBinding();
		} else if (node instanceof TypeParameter) {
			binding= ((TypeParameter)node).resolveBinding();
		} else if (node instanceof VariableDeclaration) {
			binding= ((VariableDeclaration)node).resolveBinding();
		}

		if (binding != null)
			return binding.getJavaElement();

		return null;
	}

	/**
	 * Checks whether the given name belongs to a {@link ClassInstanceCreation} and if so, returns
	 * its constructor binding.
	 * 
	 * @param nameNode the name node
	 * @return the constructor binding or <code>null</code> if not found
	 * @since 3.7
	 */
	private IBinding getConstructorBindingIfAvailable(Name nameNode) {
		ASTNode type= ASTNodes.getNormalizedNode(nameNode);
		StructuralPropertyDescriptor loc= type.getLocationInParent();
		if (loc == ClassInstanceCreation.TYPE_PROPERTY) {
			return ((ClassInstanceCreation) type.getParent()).resolveConstructorBinding();
		}
		return null;
	}

}

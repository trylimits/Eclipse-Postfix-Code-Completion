/*******************************************************************************
 * Copyright (c) 2009, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.javaeditor;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.JavaPlugin;


/**
 * Java element implementation hyperlink.
 * 
 * @since 3.5
 */
public class JavaElementImplementationHyperlink implements IHyperlink {
	
	private final IRegion fRegion;
	private final SelectionDispatchAction fOpenAction;
	private final IMethod fMethod;
	private final boolean fQualify;

	/**
	 * The editor.
	 */
	private IEditorPart fEditor;

	/**
	 * Creates a new Java element implementation hyperlink for methods.
	 * 
	 * @param region the region of the link
	 * @param openAction the action to use to open the java elements
	 * @param method the method to open
	 * @param qualify <code>true</code> if the hyperlink text should show a qualified name for
	 *            element.
	 * @param editor the editor
	 */
	public JavaElementImplementationHyperlink(IRegion region, SelectionDispatchAction openAction, IMethod method, boolean qualify, IEditorPart editor) {
		Assert.isNotNull(openAction);
		Assert.isNotNull(region);
		Assert.isNotNull(method);

		fRegion= region;
		fOpenAction= openAction;
		fMethod= method;
		fQualify= qualify;
		fEditor= editor;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.IHyperlink#getHyperlinkRegion()
	 */
	public IRegion getHyperlinkRegion() {
		return fRegion;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.IHyperlink#getHyperlinkText()
	 */
	public String getHyperlinkText() {
		if (fQualify) {
			String methodLabel= JavaElementLabels.getElementLabel(fMethod, JavaElementLabels.ALL_FULLY_QUALIFIED);
			return Messages.format(JavaEditorMessages.JavaElementImplementationHyperlink_hyperlinkText_qualified, new Object[] { methodLabel });
		} else {
			return JavaEditorMessages.JavaElementImplementationHyperlink_hyperlinkText;
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.IHyperlink#getTypeLabel()
	 */
	public String getTypeLabel() {
		return null;
	}

	/**
	 * Opens the given implementation hyperlink for methods.
	 * <p>
	 * If there's only one implementor that hyperlink is opened in the editor, otherwise the
	 * Quick Hierarchy is opened.
	 * </p>
	 */
	public void open() {
		openImplementations(fEditor, fRegion, fMethod, fOpenAction);
	}

	/**
	 * Finds the implementations for the method.
	 * <p>
	 * If there's only one implementor that method is opened in the editor, otherwise the Quick
	 * Hierarchy is opened.
	 * </p>
	 * 
	 * @param editor the editor
	 * @param region the region of the selection
	 * @param method the method
	 * @param openAction the action to use to open the methods
	 * @since 3.6
	 */
	public static void openImplementations(IEditorPart editor, IRegion region, final IMethod method, SelectionDispatchAction openAction) {
		try {
			if (cannotBeOverriddenMethod(method)) {
				openAction.run(new StructuredSelection(method));
				return;
			}
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
			return;
		}
		ITypeRoot editorInput= EditorUtility.getEditorInputJavaElement(editor, false);

		CompilationUnit ast= SharedASTProvider.getAST(editorInput, SharedASTProvider.WAIT_ACTIVE_ONLY, null);
		if (ast == null) {
			openQuickHierarchy(editor);
			return;
		}

		ASTNode node= NodeFinder.perform(ast, region.getOffset(), region.getLength());
		ITypeBinding parentTypeBinding= null;
		if (node instanceof SimpleName) {
			ASTNode parent= node.getParent();
			if (parent instanceof MethodInvocation) {
				Expression expression= ((MethodInvocation)parent).getExpression();
				if (expression == null) {
					parentTypeBinding= Bindings.getBindingOfParentType(node);
				} else {
					parentTypeBinding= expression.resolveTypeBinding();
				}
			} else if (parent instanceof SuperMethodInvocation) {
				// Directly go to the super method definition
				openAction.run(new StructuredSelection(method));
				return;
			} else if (parent instanceof MethodDeclaration) {
				parentTypeBinding= Bindings.getBindingOfParentType(node);
			}
		}
		final IType receiverType= parentTypeBinding != null ? (IType)parentTypeBinding.getJavaElement() : null;
		if (receiverType == null) {
			openQuickHierarchy(editor);
			return;
		}

		final boolean isMethodAbstract[]= new boolean[1];
		final String dummyString= new String();
		final ArrayList<IMethod> links= new ArrayList<IMethod>();
		IRunnableWithProgress runnable= new IRunnableWithProgress() {

			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				if (monitor == null) {
					monitor= new NullProgressMonitor();
				}
				try {
					String methodLabel= JavaElementLabels.getElementLabel(method, JavaElementLabels.DEFAULT_QUALIFIED);
					monitor.beginTask(Messages.format(JavaEditorMessages.JavaElementImplementationHyperlink_search_method_implementors, methodLabel), 10);
					SearchRequestor requestor= new SearchRequestor() {
						@Override
						public void acceptSearchMatch(SearchMatch match) throws CoreException {
							if (match.getAccuracy() == SearchMatch.A_ACCURATE) {
								Object element= match.getElement();
								if (element instanceof IMethod) {
									IMethod methodFound= (IMethod)element;
									if (!JdtFlags.isAbstract(methodFound)) {
										links.add(methodFound);
										if (links.size() > 1) {
											throw new OperationCanceledException(dummyString);
										}
									}
								}
							}
						}
					};

					IJavaSearchScope hierarchyScope;
					if (receiverType.isInterface()) {
						hierarchyScope= SearchEngine.createHierarchyScope(method.getDeclaringType());
					} else {
						if (isFullHierarchyNeeded(new SubProgressMonitor(monitor, 3), method, receiverType))
							hierarchyScope= SearchEngine.createHierarchyScope(receiverType);
						else {
							isMethodAbstract[0]= JdtFlags.isAbstract(method);
							hierarchyScope= SearchEngine.createStrictHierarchyScope(null, receiverType, true, !isMethodAbstract[0], null);
						}
					}

					int limitTo= IJavaSearchConstants.DECLARATIONS | IJavaSearchConstants.IGNORE_DECLARING_TYPE | IJavaSearchConstants.IGNORE_RETURN_TYPE;
					SearchPattern pattern= SearchPattern.createPattern(method, limitTo);
					Assert.isNotNull(pattern);
					SearchParticipant[] participants= new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() };
					SearchEngine engine= new SearchEngine();
					engine.search(pattern, participants, hierarchyScope, requestor, new SubProgressMonitor(monitor, 7));
					if (monitor.isCanceled()) {
						throw new OperationCanceledException();
					}
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				} finally {
					monitor.done();
				}
			}
		};

		try {
			IRunnableContext context= editor.getSite().getWorkbenchWindow();
			context.run(true, true, runnable);
		} catch (InvocationTargetException e) {
			IStatus status= new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.OK,
					Messages.format(JavaEditorMessages.JavaElementImplementationHyperlink_error_status_message, method.getElementName()), e.getCause());
			JavaPlugin.log(status);
			ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
					JavaEditorMessages.JavaElementImplementationHyperlink_hyperlinkText,
					JavaEditorMessages.JavaElementImplementationHyperlink_error_no_implementations_found_message, status);
		} catch (InterruptedException e) {
			if (e.getMessage() != dummyString) {
				return;
			}
		}

		if (links.isEmpty() && isMethodAbstract[0])
			openAction.run(new StructuredSelection(method));
		else if (links.size() == 1)
			openAction.run(new StructuredSelection(links.get(0)));
		else
			openQuickHierarchy(editor);
	}

	/**
	 * Checks whether or not a method can be overridden.
	 * 
	 * @param method the method
	 * @return <code>true</code> if the method cannot be overridden, <code>false</code> otherwise
	 * @throws JavaModelException if this element does not exist or if an exception occurs while
	 *             accessing its corresponding resource
	 * @since 3.7
	 */
	private static boolean cannotBeOverriddenMethod(IMethod method) throws JavaModelException {
		return JdtFlags.isPrivate(method) || JdtFlags.isFinal(method) || JdtFlags.isStatic(method) || method.isConstructor()
				|| JdtFlags.isFinal((IMember)method.getParent());
	}

	/**
	 * Checks whether a full type hierarchy is needed to search for implementors.
	 * 
	 * @param monitor the progress monitor
	 * @param method the method
	 * @param receiverType the receiver type
	 * @return <code>true</code> if a full type hierarchy is needed, <code>false</code> otherwise
	 * @throws JavaModelException if the java element does not exist or if an exception occurs while
	 *             accessing its corresponding resource
	 * @since 3.6
	 */
	private static boolean isFullHierarchyNeeded(IProgressMonitor monitor, IMethod method, IType receiverType) throws JavaModelException {
		ITypeHierarchy superTypeHierarchy= receiverType.newSupertypeHierarchy(monitor);
		MethodOverrideTester methodOverrideTester= new MethodOverrideTester(receiverType, superTypeHierarchy);
		return methodOverrideTester.findOverriddenMethodInType(receiverType, method) == null;
	}

	/**
	 * Opens the quick type hierarchy for the given editor.
	 *
	 * @param editor the editor for which to open the quick hierarchy
	 */
	private static void openQuickHierarchy(IEditorPart editor) {
		ITextOperationTarget textOperationTarget= (ITextOperationTarget)editor.getAdapter(ITextOperationTarget.class);
		textOperationTarget.doOperation(JavaSourceViewer.SHOW_HIERARCHY);
	}
}

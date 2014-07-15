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

package org.eclipse.jdt.internal.ui.javaeditor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jface.dialogs.MessageDialog;

import org.eclipse.jface.text.ISynchronizable;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;

import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.SharedASTProvider;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.java.IJavaReconcilingListener;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

/**
 * Manages the override and overwrite indicators for
 * the given Java element and annotation model.
 *
 * @since 3.0
 */
class OverrideIndicatorManager implements IJavaReconcilingListener {

	/**
	 * Overwrite and override indicator annotation.
	 *
	 * @since 3.0
	 */
	class OverrideIndicator extends Annotation {

		private boolean fIsOverwriteIndicator;
		private String fAstNodeKey;

		/**
		 * Creates a new override annotation.
		 *
		 * @param isOverwriteIndicator <code>true</code> if this annotation is
		 *            an overwrite indicator, <code>false</code> otherwise
		 * @param text the text associated with this annotation
		 * @param key the method binding key
		 * @since 3.0
		 */
		OverrideIndicator(boolean isOverwriteIndicator, String text, String key) {
			super(ANNOTATION_TYPE, false, text);
			fIsOverwriteIndicator= isOverwriteIndicator;
			fAstNodeKey= key;
		}

		/**
		 * Tells whether this is an overwrite or an override indicator.
		 *
		 * @return <code>true</code> if this is an overwrite indicator
		 */
		public boolean isOverwriteIndicator() {
			return fIsOverwriteIndicator;
		}

		/**
		 * Opens and reveals the defining method.
		 */
		public void open() {
			CompilationUnit ast= SharedASTProvider.getAST(fJavaElement, SharedASTProvider.WAIT_ACTIVE_ONLY, null);
			if (ast != null) {
				ASTNode node= ast.findDeclaringNode(fAstNodeKey);
				if (node instanceof MethodDeclaration) {
					try {
						IMethodBinding methodBinding= ((MethodDeclaration)node).resolveBinding();
						IMethodBinding definingMethodBinding= Bindings.findOverriddenMethod(methodBinding, true);
						if (definingMethodBinding != null) {
							IJavaElement definingMethod= definingMethodBinding.getJavaElement();
							if (definingMethod != null) {
								JavaUI.openInEditor(definingMethod, true, true);
								return;
							}
						}
					} catch (CoreException e) {
						ExceptionHandler.handle(e, JavaEditorMessages.OverrideIndicatorManager_open_error_title, JavaEditorMessages.OverrideIndicatorManager_open_error_messageHasLogEntry);
						return;
					}
				}
			}
			String title= JavaEditorMessages.OverrideIndicatorManager_open_error_title;
			String message= JavaEditorMessages.OverrideIndicatorManager_open_error_message;
			MessageDialog.openError(JavaPlugin.getActiveWorkbenchShell(), title, message);
		}
	}

	static final String ANNOTATION_TYPE= "org.eclipse.jdt.ui.overrideIndicator"; //$NON-NLS-1$

	private IAnnotationModel fAnnotationModel;
	private Object fAnnotationModelLockObject;
	private Annotation[] fOverrideAnnotations;
	private ITypeRoot fJavaElement;


	public OverrideIndicatorManager(IAnnotationModel annotationModel, ITypeRoot javaElement, CompilationUnit ast) {
		Assert.isNotNull(annotationModel);
		Assert.isNotNull(javaElement);

		fJavaElement= javaElement;
		fAnnotationModel=annotationModel;
		fAnnotationModelLockObject= getLockObject(fAnnotationModel);

		updateAnnotations(ast, new NullProgressMonitor());
	}

	/**
	 * Returns the lock object for the given annotation model.
	 *
	 * @param annotationModel the annotation model
	 * @return the annotation model's lock object
	 * @since 3.0
	 */
	private Object getLockObject(IAnnotationModel annotationModel) {
		if (annotationModel instanceof ISynchronizable) {
			Object lock= ((ISynchronizable)annotationModel).getLockObject();
			if (lock != null)
				return lock;
		}
		return annotationModel;
	}

	/**
	 * Updates the override and implements annotations based
	 * on the given AST.
	 *
	 * @param ast the compilation unit AST
	 * @param progressMonitor the progress monitor
	 * @since 3.0
	 */
	protected void updateAnnotations(CompilationUnit ast, IProgressMonitor progressMonitor) {

		if (ast == null || progressMonitor.isCanceled())
			return;

		final Map<Annotation, Position> annotationMap= new HashMap<Annotation, Position>(50);

		ast.accept(new ASTVisitor(false) {
			/*
			 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.MethodDeclaration)
			 */
			@Override
			public boolean visit(MethodDeclaration node) {
				IMethodBinding binding= node.resolveBinding();
				if (binding != null) {
					IMethodBinding definingMethod= Bindings.findOverriddenMethod(binding, true);
					if (definingMethod != null) {

						ITypeBinding definingType= definingMethod.getDeclaringClass();
						String qualifiedMethodName= definingType.getQualifiedName() + "." + binding.getName(); //$NON-NLS-1$

						boolean isImplements= JdtFlags.isAbstract(definingMethod);
						String text;
						if (isImplements)
							text= Messages.format(JavaEditorMessages.OverrideIndicatorManager_implements, BasicElementLabels.getJavaElementName(qualifiedMethodName));
						else
							text= Messages.format(JavaEditorMessages.OverrideIndicatorManager_overrides, BasicElementLabels.getJavaElementName(qualifiedMethodName));

						SimpleName name= node.getName();
						Position position= new Position(name.getStartPosition(), name.getLength());

						annotationMap.put(
								new OverrideIndicator(isImplements, text, binding.getKey()),
								position);

					}
				}
				return true;
			}
		});

		if (progressMonitor.isCanceled())
			return;

		synchronized (fAnnotationModelLockObject) {
			if (fAnnotationModel instanceof IAnnotationModelExtension) {
				((IAnnotationModelExtension)fAnnotationModel).replaceAnnotations(fOverrideAnnotations, annotationMap);
			} else {
				removeAnnotations();
				Iterator<Entry<Annotation, Position>> iter= annotationMap.entrySet().iterator();
				while (iter.hasNext()) {
					Entry<Annotation, Position> mapEntry= iter.next();
					fAnnotationModel.addAnnotation(mapEntry.getKey(), mapEntry.getValue());
				}
			}
			fOverrideAnnotations= annotationMap.keySet().toArray(new Annotation[annotationMap.keySet().size()]);
		}
	}

	/**
	 * Removes all override indicators from this manager's annotation model.
	 */
	void removeAnnotations() {
		if (fOverrideAnnotations == null)
			return;

		synchronized (fAnnotationModelLockObject) {
			if (fAnnotationModel instanceof IAnnotationModelExtension) {
				((IAnnotationModelExtension)fAnnotationModel).replaceAnnotations(fOverrideAnnotations, null);
			} else {
				for (int i= 0, length= fOverrideAnnotations.length; i < length; i++)
					fAnnotationModel.removeAnnotation(fOverrideAnnotations[i]);
			}
			fOverrideAnnotations= null;
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.java.IJavaReconcilingListener#aboutToBeReconciled()
	 */
	public void aboutToBeReconciled() {
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.java.IJavaReconcilingListener#reconciled(CompilationUnit, boolean, IProgressMonitor)
	 */
	public void reconciled(CompilationUnit ast, boolean forced, IProgressMonitor progressMonitor) {
		updateAnnotations(ast, progressMonitor);
	}
}


/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.jface.text.source.IAnnotationModel;

import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;
import org.eclipse.jdt.internal.ui.javaeditor.WorkingCopyManager;

public class JavaReconcilingStrategy implements IReconcilingStrategy, IReconcilingStrategyExtension {


	private ITextEditor fEditor;

	private WorkingCopyManager fManager;
	private IDocumentProvider fDocumentProvider;
	private IProgressMonitor fProgressMonitor;
	private boolean fNotify= true;

	private IJavaReconcilingListener fJavaReconcilingListener;
	private boolean fIsJavaReconcilingListener;

	/**
	 * Short cache to transfer the reconcile AST to
	 * the {@link #reconciled()} method.
	 *
	 * @since 3.4
	 */
	private CompilationUnit fAST;


	public JavaReconcilingStrategy(ITextEditor editor) {
		fEditor= editor;
		fManager= JavaPlugin.getDefault().getWorkingCopyManager();
		fDocumentProvider= JavaPlugin.getDefault().getCompilationUnitDocumentProvider();
		fIsJavaReconcilingListener= fEditor instanceof IJavaReconcilingListener;
		if (fIsJavaReconcilingListener)
			fJavaReconcilingListener= (IJavaReconcilingListener)fEditor;
	}

	private IProblemRequestorExtension getProblemRequestorExtension() {
		IAnnotationModel model= fDocumentProvider.getAnnotationModel(fEditor.getEditorInput());
		if (model instanceof IProblemRequestorExtension)
			return (IProblemRequestorExtension) model;
		return null;
	}

	private void reconcile(final boolean initialReconcile) {
		Assert.isTrue(fAST == null); // we'll see how this behaves ;-)
		final ICompilationUnit unit= fManager.getWorkingCopy(fEditor.getEditorInput(), false);
		if (unit != null) {
			SafeRunner.run(new ISafeRunnable() {
				public void run() throws JavaModelException {
					fAST= reconcile(unit, initialReconcile);
				}
				public void handleException(Throwable ex) {
					IStatus status= new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.OK, "Error in JDT Core during reconcile", ex);  //$NON-NLS-1$
					JavaPlugin.getDefault().getLog().log(status);
				}
			});
		}
	}

	/**
	 * Performs the reconcile and returns the AST if it was computed.
	 *
	 * @param unit the compilation unit
	 * @param initialReconcile <code>true</code> if this is the initial reconcile
	 * @return the AST or <code>null</code> if none
	 * @throws JavaModelException if the original Java element does not exist
	 * @since 3.4
	 */
	private CompilationUnit reconcile(ICompilationUnit unit, boolean initialReconcile) throws JavaModelException {
		/* fix for missing cancel flag communication */
		IProblemRequestorExtension extension= getProblemRequestorExtension();
		if (extension != null) {
			extension.setProgressMonitor(fProgressMonitor);
			extension.setIsActive(true);
		}

		try {
			boolean isASTNeeded= initialReconcile || JavaPlugin.getDefault().getASTProvider().isActive(unit);
			// reconcile
			if (fIsJavaReconcilingListener && isASTNeeded) {
				int reconcileFlags= ICompilationUnit.FORCE_PROBLEM_DETECTION;
				if (ASTProvider.SHARED_AST_STATEMENT_RECOVERY)
					reconcileFlags|= ICompilationUnit.ENABLE_STATEMENTS_RECOVERY;
				if (ASTProvider.SHARED_BINDING_RECOVERY)
					reconcileFlags|= ICompilationUnit.ENABLE_BINDINGS_RECOVERY;

				CompilationUnit ast= unit.reconcile(ASTProvider.SHARED_AST_LEVEL, reconcileFlags, null, fProgressMonitor);
				if (ast != null) {
					// mark as unmodifiable
					ASTNodes.setFlagsToAST(ast, ASTNode.PROTECT);
					return ast;
				}
			} else
				unit.reconcile(ICompilationUnit.NO_AST, true, null, fProgressMonitor);
		} catch (OperationCanceledException ex) {
			Assert.isTrue(fProgressMonitor == null || fProgressMonitor.isCanceled());
		} finally {
			/* fix for missing cancel flag communication */
			if (extension != null) {
				extension.setProgressMonitor(null);
				extension.setIsActive(false);
			}
		}

		return null;
	}

	/*
	 * @see IReconcilingStrategy#reconcile(IRegion)
	 */
	public void reconcile(IRegion partition) {
		reconcile(false);
	}

	/*
	 * @see IReconcilingStrategy#reconcile(DirtyRegion, IRegion)
	 */
	public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
		reconcile(false);
	}

	/*
	 * @see IReconcilingStrategy#setDocument(IDocument)
	 */
	public void setDocument(IDocument document) {
	}

	/*
	 * @see IReconcilingStrategyExtension#setProgressMonitor(IProgressMonitor)
	 */
	public void setProgressMonitor(IProgressMonitor monitor) {
		fProgressMonitor= monitor;
	}

	/*
	 * @see IReconcilingStrategyExtension#initialReconcile()
	 */
	public void initialReconcile() {
		reconcile(true);
	}

	/**
	 * Tells this strategy whether to inform its listeners.
	 *
	 * @param notify <code>true</code> if listeners should be notified
	 */
	public void notifyListeners(boolean notify) {
		fNotify= notify;
	}

	/**
	 * Called before reconciling is started.
	 *
	 * @since 3.0
	 */
	public void aboutToBeReconciled() {
		if (fIsJavaReconcilingListener)
			fJavaReconcilingListener.aboutToBeReconciled();
	}

	/**
	 * Called when reconcile has finished.
	 *
	 * @since 3.4
	 */
	public void reconciled() {
		// Always notify listeners, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=55969 for the final solution
		try {
			if (fIsJavaReconcilingListener) {
				IProgressMonitor pm= fProgressMonitor;
				if (pm == null)
					pm= new NullProgressMonitor();
				fJavaReconcilingListener.reconciled(fAST, !fNotify, pm);
			}
		} finally {
			fNotify= true;
			fAST= null;
		}
	}
}

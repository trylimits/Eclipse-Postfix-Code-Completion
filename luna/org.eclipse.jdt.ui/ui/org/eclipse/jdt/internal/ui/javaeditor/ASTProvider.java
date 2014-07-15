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
package org.eclipse.jdt.internal.ui.javaeditor;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;

import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jdt.ui.SharedASTProvider.WAIT_FLAG;

import org.eclipse.jdt.internal.ui.JavaPlugin;


/**
 * Provides a shared AST for clients. The shared AST is
 * the AST of the active Java editor's input element.
 *
 * @since 3.0
 */
public final class ASTProvider {

	/**
	 * @deprecated Use {@link SharedASTProvider#WAIT_YES} instead.
	 */
	public static final WAIT_FLAG WAIT_YES= SharedASTProvider.WAIT_YES;

	/**
	 * @deprecated Use {@link SharedASTProvider#WAIT_ACTIVE_ONLY} instead.
	 */
	public static final WAIT_FLAG WAIT_ACTIVE_ONLY= SharedASTProvider.WAIT_ACTIVE_ONLY;

	/**
	 * @deprecated Use {@link SharedASTProvider#WAIT_NO} instead.
	 */
	public static final WAIT_FLAG WAIT_NO= SharedASTProvider.WAIT_NO;


	/**
	 * Tells whether this class is in debug mode.
	 * @since 3.0
	 */
	private static final boolean DEBUG= "true".equalsIgnoreCase(Platform.getDebugOption("org.eclipse.jdt.ui/debug/ASTProvider"));  //$NON-NLS-1$//$NON-NLS-2$


	/**
	 * Internal activation listener.
	 *
	 * @since 3.0
	 */
	private class ActivationListener implements IPartListener2, IWindowListener {


		/*
		 * @see org.eclipse.ui.IPartListener2#partActivated(org.eclipse.ui.IWorkbenchPartReference)
		 */
		public void partActivated(IWorkbenchPartReference ref) {
			if (isJavaEditor(ref) && !isActiveEditor(ref))
				activeJavaEditorChanged(ref.getPart(true));
		}

		/*
		 * @see org.eclipse.ui.IPartListener2#partBroughtToTop(org.eclipse.ui.IWorkbenchPartReference)
		 */
		public void partBroughtToTop(IWorkbenchPartReference ref) {
			if (isJavaEditor(ref) && !isActiveEditor(ref))
				activeJavaEditorChanged(ref.getPart(true));
		}

		/*
		 * @see org.eclipse.ui.IPartListener2#partClosed(org.eclipse.ui.IWorkbenchPartReference)
		 */
		public void partClosed(IWorkbenchPartReference ref) {
			if (isActiveEditor(ref)) {
				if (DEBUG)
					System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "closed active editor: " + ref.getTitle()); //$NON-NLS-1$ //$NON-NLS-2$

				activeJavaEditorChanged(null);
			}
		}

		/*
		 * @see org.eclipse.ui.IPartListener2#partDeactivated(org.eclipse.ui.IWorkbenchPartReference)
		 */
		public void partDeactivated(IWorkbenchPartReference ref) {
		}

		/*
		 * @see org.eclipse.ui.IPartListener2#partOpened(org.eclipse.ui.IWorkbenchPartReference)
		 */
		public void partOpened(IWorkbenchPartReference ref) {
			if (isJavaEditor(ref) && !isActiveEditor(ref))
				activeJavaEditorChanged(ref.getPart(true));
		}

		/*
		 * @see org.eclipse.ui.IPartListener2#partHidden(org.eclipse.ui.IWorkbenchPartReference)
		 */
		public void partHidden(IWorkbenchPartReference ref) {
		}

		/*
		 * @see org.eclipse.ui.IPartListener2#partVisible(org.eclipse.ui.IWorkbenchPartReference)
		 */
		public void partVisible(IWorkbenchPartReference ref) {
			if (isJavaEditor(ref) && !isActiveEditor(ref))
				activeJavaEditorChanged(ref.getPart(true));
		}

		/*
		 * @see org.eclipse.ui.IPartListener2#partInputChanged(org.eclipse.ui.IWorkbenchPartReference)
		 */
		public void partInputChanged(IWorkbenchPartReference ref) {
			if (isJavaEditor(ref) && isActiveEditor(ref))
				activeJavaEditorChanged(ref.getPart(true));
		}

		/*
		 * @see org.eclipse.ui.IWindowListener#windowActivated(org.eclipse.ui.IWorkbenchWindow)
		 */
		public void windowActivated(IWorkbenchWindow window) {
			IWorkbenchPartReference ref= window.getPartService().getActivePartReference();
			if (isJavaEditor(ref) && !isActiveEditor(ref))
				activeJavaEditorChanged(ref.getPart(true));
		}

		/*
		 * @see org.eclipse.ui.IWindowListener#windowDeactivated(org.eclipse.ui.IWorkbenchWindow)
		 */
		public void windowDeactivated(IWorkbenchWindow window) {
		}

		/*
		 * @see org.eclipse.ui.IWindowListener#windowClosed(org.eclipse.ui.IWorkbenchWindow)
		 */
		public void windowClosed(IWorkbenchWindow window) {
			if (fActiveEditor != null && fActiveEditor.getSite() != null && window == fActiveEditor.getSite().getWorkbenchWindow()) {
				if (DEBUG)
					System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "closed active editor: " + fActiveEditor.getTitle()); //$NON-NLS-1$ //$NON-NLS-2$

				activeJavaEditorChanged(null);
			}
			window.getPartService().removePartListener(this);
		}

		/*
		 * @see org.eclipse.ui.IWindowListener#windowOpened(org.eclipse.ui.IWorkbenchWindow)
		 */
		public void windowOpened(IWorkbenchWindow window) {
			window.getPartService().addPartListener(this);
		}

		private boolean isActiveEditor(IWorkbenchPartReference ref) {
			return ref != null && isActiveEditor(ref.getPart(false));
		}

		private boolean isActiveEditor(IWorkbenchPart part) {
			return part != null && (part == fActiveEditor);
		}

		private boolean isJavaEditor(IWorkbenchPartReference ref) {
			if (ref == null)
				return false;

			String id= ref.getId();

			// The instanceof check is not need but helps clients, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=84862
			return JavaUI.ID_CF_EDITOR.equals(id) || JavaUI.ID_CU_EDITOR.equals(id) || ref.getPart(false) instanceof JavaEditor;
		}
	}

	public static final int SHARED_AST_LEVEL= AST.JLS8;
	public static final boolean SHARED_AST_STATEMENT_RECOVERY= true;
	public static final boolean SHARED_BINDING_RECOVERY= true;

	private static final String DEBUG_PREFIX= "ASTProvider > "; //$NON-NLS-1$


	private volatile ITypeRoot fReconcilingJavaElement;
	private ITypeRoot fActiveJavaElement;
	private CompilationUnit fAST;
	private ActivationListener fActivationListener;
	private Object fReconcileLock= new Object();
	private Object fWaitLock= new Object();
	private volatile boolean fIsReconciling;
	private IWorkbenchPart fActiveEditor;


	/**
	 * Returns the Java plug-in's AST provider.
	 *
	 * @return the AST provider
	 * @since 3.2
	 */
	public static ASTProvider getASTProvider() {
		return JavaPlugin.getDefault().getASTProvider();
	}

	/**
	 * Creates a new AST provider.
	 */
	public ASTProvider() {
		install();
	}

	/**
	 * Installs this AST provider.
	 */
	void install() {
		// Create and register activation listener
		fActivationListener= new ActivationListener();
		PlatformUI.getWorkbench().addWindowListener(fActivationListener);

		// Ensure existing windows get connected
		IWorkbenchWindow[] windows= PlatformUI.getWorkbench().getWorkbenchWindows();
		for (int i= 0, length= windows.length; i < length; i++)
			windows[i].getPartService().addPartListener(fActivationListener);
	}

	void activeJavaEditorChanged(IWorkbenchPart editor) {

		ITypeRoot javaElement= null;
		if (editor instanceof JavaEditor)
			javaElement= ((JavaEditor)editor).getInputJavaElement();

		synchronized (this) {
			fActiveEditor= editor;
			fActiveJavaElement= javaElement;
			cache(null, javaElement);
		}

		if (DEBUG)
			System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "active editor is: " + toString(javaElement)); //$NON-NLS-1$ //$NON-NLS-2$

		synchronized (fReconcileLock) {
			if (fIsReconciling && (fReconcilingJavaElement == null || !fReconcilingJavaElement.equals(javaElement))) {
				fIsReconciling= false;
				fReconcilingJavaElement= null;
			} else if (javaElement == null) {
				fIsReconciling= false;
				fReconcilingJavaElement= null;
			}
		}
	}

	/**
	 * Returns whether the given compilation unit AST is
	 * cached by this AST provided.
	 *
	 * @param ast the compilation unit AST
	 * @return <code>true</code> if the given AST is the cached one
	 */
	public boolean isCached(CompilationUnit ast) {
		return ast != null && fAST == ast;
	}

	/**
	 * Returns whether this AST provider is active on the given
	 * compilation unit.
	 *
	 * @param cu the compilation unit
	 * @return <code>true</code> if the given compilation unit is the active one
	 * @since 3.1
	 */
	public synchronized boolean isActive(ICompilationUnit cu) {
		return cu != null && cu.equals(fActiveJavaElement);
	}

	/**
	 * Informs that reconciling for the given element is about to be started.
	 *
	 * @param javaElement the Java element
	 * @see org.eclipse.jdt.internal.ui.text.java.IJavaReconcilingListener#aboutToBeReconciled()
	 */
	void aboutToBeReconciled(ITypeRoot javaElement) {

		if (javaElement == null)
			return;

		if (DEBUG)
			System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "about to reconcile: " + toString(javaElement)); //$NON-NLS-1$ //$NON-NLS-2$

		synchronized (fReconcileLock) {
			fReconcilingJavaElement= javaElement;
			fIsReconciling= true;
		}
		cache(null, javaElement);
	}

	/**
	 * Disposes the cached AST.
	 */
	private synchronized void disposeAST() {

		if (fAST == null)
			return;

		if (DEBUG)
			System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "disposing AST: " + toString(fAST) + " for: " + toString(fActiveJavaElement)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		fAST= null;

		cache(null, null);
	}

	/**
	 * Returns a string for the given Java element used for debugging.
	 *
	 * @param javaElement the compilation unit AST
	 * @return a string used for debugging
	 */
	private String toString(ITypeRoot javaElement) {
		if (javaElement == null)
			return "null"; //$NON-NLS-1$
		else
			return javaElement.getElementName();

	}

	/**
	 * Returns a string for the given AST used for debugging.
	 *
	 * @param ast the compilation unit AST
	 * @return a string used for debugging
	 */
	private String toString(CompilationUnit ast) {
		if (ast == null)
			return "null"; //$NON-NLS-1$

		List<AbstractTypeDeclaration> types= ast.types();
		if (types != null && types.size() > 0)
			return types.get(0).getName().getIdentifier() + "(" + ast.hashCode() + ")"; //$NON-NLS-1$//$NON-NLS-2$
		else
			return "AST without any type"; //$NON-NLS-1$
	}

	/**
	 * Caches the given compilation unit AST for the given Java element.
	 *
	 * @param ast the ast
	 * @param javaElement the java element
	 */
	private synchronized void cache(CompilationUnit ast, ITypeRoot javaElement) {

		if (fActiveJavaElement != null && !fActiveJavaElement.equals(javaElement)) {
			if (DEBUG && javaElement != null) // don't report call from disposeAST()
				System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "don't cache AST for inactive: " + toString(javaElement)); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}

		if (DEBUG && (javaElement != null || ast != null)) // don't report call from disposeAST()
			System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "caching AST: " + toString(ast) + " for: " + toString(javaElement)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		if (fAST != null)
			disposeAST();

		fAST= ast;

		// Signal AST change
		synchronized (fWaitLock) {
			fWaitLock.notifyAll();
		}
	}

	/**
	 * Returns a shared compilation unit AST for the given Java element.
	 * <p>
	 * Clients are not allowed to modify the AST and must synchronize all access to its nodes.
	 * </p>
	 *
	 * @param input the Java element, must not be <code>null</code>
	 * @param waitFlag {@link SharedASTProvider#WAIT_YES}, {@link SharedASTProvider#WAIT_NO} or
	 *            {@link SharedASTProvider#WAIT_ACTIVE_ONLY}
	 * @param progressMonitor the progress monitor or <code>null</code>
	 * @return the AST or <code>null</code> if the AST is not available
	 */
	public CompilationUnit getAST(final ITypeRoot input, WAIT_FLAG waitFlag, IProgressMonitor progressMonitor) {
		if (input == null || waitFlag == null)
			throw new IllegalArgumentException("input or wait flag are null"); //$NON-NLS-1$

		if (progressMonitor != null && progressMonitor.isCanceled())
			return null;

		boolean isActiveElement;
		synchronized (this) {
			isActiveElement= input.equals(fActiveJavaElement);
			if (isActiveElement) {
				if (fAST != null) {
					if (DEBUG)
						System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "returning cached AST:" + toString(fAST) + " for: " + input.getElementName()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

					return fAST;
				}
				if (waitFlag == SharedASTProvider.WAIT_NO) {
					if (DEBUG)
						System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "returning null (WAIT_NO) for: " + input.getElementName()); //$NON-NLS-1$ //$NON-NLS-2$

					return null;

				}
			}
		}

		final boolean canReturnNull= waitFlag == SharedASTProvider.WAIT_NO || (waitFlag == SharedASTProvider.WAIT_ACTIVE_ONLY && !(isActiveElement && fAST == null));
		boolean isReconciling= false;
		final ITypeRoot activeElement;
		if (isActiveElement) {
			synchronized (fReconcileLock) {
				activeElement= fReconcilingJavaElement;
				isReconciling= isReconciling(input);
				if (!isReconciling && !canReturnNull)
					aboutToBeReconciled(input);
			}
		} else
			activeElement= null;

		if (isReconciling) {
			try {
				// Wait for AST
				synchronized (fWaitLock) {
					if (isReconciling(input)) {
						if (DEBUG)
							System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "waiting for AST for: " + input.getElementName()); //$NON-NLS-1$ //$NON-NLS-2$
						fWaitLock.wait(30000); // XXX: The 30 seconds timeout is an attempt to at least avoid a deadlock. See https://bugs.eclipse.org/366048#c21
					}
				}

				// Check whether active element is still valid
				synchronized (this) {
					if (activeElement == fActiveJavaElement && fAST != null) {
						if (DEBUG)
							System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "...got AST: " + toString(fAST) + " for: " + input.getElementName()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

						return fAST;
					}
				}
				return getAST(input, waitFlag, progressMonitor);
			} catch (InterruptedException e) {
				return null; // thread has been interrupted don't compute AST
			}
		} else if (canReturnNull)
			return null;


		CompilationUnit ast= null;
		try {
			ast= createAST(input, progressMonitor);
			if (progressMonitor != null && progressMonitor.isCanceled()) {
				ast= null;
				if (DEBUG)
					System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "Ignore created AST for: " + input.getElementName() + " - operation has been cancelled"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
		} finally {
			if (isActiveElement) {
				if (fAST != null) {
					// in the meantime, reconcile created a new AST. Return that one
					if (DEBUG)
						System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "Ignore created AST for " + input.getElementName() + " - AST from reconciler is newer"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					reconciled(fAST, input, null);
					return fAST;
				} else
					reconciled(ast, input, null);
			}
		}
		return ast;
	}

	/**
	 * Tells whether the given Java element is the one
	 * reported as currently being reconciled.
	 *
	 * @param javaElement the Java element
	 * @return <code>true</code> if reported as currently being reconciled
	 */
	private boolean isReconciling(ITypeRoot javaElement) {
		return javaElement != null && javaElement.equals(fReconcilingJavaElement) && fIsReconciling;
	}

	/**
	 * Creates a new compilation unit AST.
	 *
	 * @param input the Java element for which to create the AST
	 * @param progressMonitor the progress monitor
	 * @return AST
	 */
	private static CompilationUnit createAST(final ITypeRoot input, final IProgressMonitor progressMonitor) {
		if (!hasSource(input))
			return null;

		if (progressMonitor != null && progressMonitor.isCanceled())
			return null;

		final ASTParser parser = ASTParser.newParser(SHARED_AST_LEVEL);
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(SHARED_AST_STATEMENT_RECOVERY);
		parser.setBindingsRecovery(SHARED_BINDING_RECOVERY);
		parser.setSource(input);

		if (progressMonitor != null && progressMonitor.isCanceled())
			return null;

		final CompilationUnit root[]= new CompilationUnit[1];

		SafeRunner.run(new ISafeRunnable() {
			public void run() {
				try {
					if (progressMonitor != null && progressMonitor.isCanceled())
						return;
					if (DEBUG)
						System.err.println(getThreadName() + " - " + DEBUG_PREFIX + "creating AST for: " + input.getElementName()); //$NON-NLS-1$ //$NON-NLS-2$
					root[0]= (CompilationUnit)parser.createAST(progressMonitor);

					//mark as unmodifiable
					ASTNodes.setFlagsToAST(root[0], ASTNode.PROTECT);
				} catch (OperationCanceledException ex) {
					return;
				}
			}
			public void handleException(Throwable ex) {
				IStatus status= new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.OK, "Error in JDT Core during AST creation", ex);  //$NON-NLS-1$
				JavaPlugin.getDefault().getLog().log(status);
			}
		});
		return root[0];
	}

	/**
	 * Checks whether the given Java element has accessible source.
	 *
	 * @param je the Java element to test
	 * @return <code>true</code> if the element has source
	 * @since 3.2
	 */
	private static boolean hasSource(ITypeRoot je) {
		if (je == null || !je.exists())
			return false;

		try {
			return je.getBuffer() != null;
		} catch (JavaModelException ex) {
			IStatus status= new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.OK, "Error in JDT Core during AST creation", ex);  //$NON-NLS-1$
			JavaPlugin.getDefault().getLog().log(status);
		}
		return false;
	}

	/**
	 * Disposes this AST provider.
	 */
	public void dispose() {

		// Dispose activation listener
		PlatformUI.getWorkbench().removeWindowListener(fActivationListener);
		fActivationListener= null;

		disposeAST();

		synchronized (fWaitLock) {
			fWaitLock.notifyAll();
		}
	}

	/**
	 * Update internal structures after reconcile.
	 * 
	 * @param ast the compilation unit AST or <code>null</code> if the working copy was consistent
	 *            or reconciliation has been cancelled
	 * @param javaElement the Java element for which the AST was built
	 * @param progressMonitor the progress monitor
	 * @see org.eclipse.jdt.internal.ui.text.java.IJavaReconcilingListener#reconciled(CompilationUnit,
	 *      boolean, IProgressMonitor)
	 */
	void reconciled(CompilationUnit ast, ITypeRoot javaElement, IProgressMonitor progressMonitor) {
		if (DEBUG)
			System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "reconciled: " + toString(javaElement) + ", AST: " + toString(ast)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		synchronized (fReconcileLock) {
			fIsReconciling= false;
			if (javaElement == null || !javaElement.equals(fReconcilingJavaElement)) {

				if (DEBUG)
					System.out.println(getThreadName() + " - " + DEBUG_PREFIX + "  ignoring AST of out-dated editor"); //$NON-NLS-1$ //$NON-NLS-2$

				// Signal - threads might wait for wrong element
				synchronized (fWaitLock) {
					fWaitLock.notifyAll();
				}

				return;
			}
			cache(ast, javaElement);
		}
	}

	private static String getThreadName() {
		String name= Thread.currentThread().getName();
		if (name != null)
			return name;
		else
			return Thread.currentThread().toString();
	}

}


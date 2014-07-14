/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Mateusz Wenus <mateusz.wenus@gmail.com> - [override method] generate in declaration order [code generation] - https://bugs.eclipse.org/bugs/show_bug.cgi?id=140971
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.codemanipulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.jdt.internal.corext.util.DelegateEntryComparator;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

/**
 * Workspace runnable to add delegate methods.
 *
 * @since 3.1
 */
public final class AddDelegateMethodsOperation implements IWorkspaceRunnable {

	/**
	 * Represents a delegated method
	 */
	public static class DelegateEntry {

		public DelegateEntry(IMethodBinding delegateMethod, IVariableBinding field) {
			Assert.isNotNull(delegateMethod);
			Assert.isNotNull(field);
			this.delegateMethod= delegateMethod;
			this.field= field;
		}

		public final IMethodBinding delegateMethod;
		public final IVariableBinding field;

	}

	/** Should the resulting edit be applied? */
	private boolean fApply= true;

	/** The method binding keys for which a method was generated */
	private final List<IMethodBinding> fCreated= new ArrayList<IMethodBinding>();

	/** The resulting text edit */
	private TextEdit fResultingEdit= null;

	/** The insertion point, or <code>null</code> */
	private final IJavaElement fInsert;

	/** Should the compilation unit content be saved? */
	private final boolean fSave;

	/** The code generation settings to use */
	private final CodeGenerationSettings fSettings;

	/** The compilation unit ast node */
	private final CompilationUnit fASTRoot;

	private final DelegateEntry[] fDelegatesToCreate;

	/**
	 * Creates a new add delegate methods operation.
	 *
	 * @param astRoot the AST of the current compilation unit
	 * @param delegatesToCreate the delegates to create
	 * @param insert the insertion point, or <code>null</code>
	 * @param settings the code generation settings to use
	 * @param apply <code>true</code> if the resulting edit should be applied, <code>false</code> otherwise
	 * @param save <code>true</code> if the changed compilation unit should be saved, <code>false</code> otherwise
	 */
	public AddDelegateMethodsOperation(CompilationUnit astRoot, DelegateEntry[] delegatesToCreate, IJavaElement insert, CodeGenerationSettings settings, boolean apply, boolean save) {
		Assert.isTrue(astRoot != null && astRoot.getTypeRoot() instanceof ICompilationUnit);
		Assert.isTrue(delegatesToCreate != null && delegatesToCreate.length > 0);
		Assert.isNotNull(settings);
		fASTRoot= astRoot;
		fInsert= insert;
		fDelegatesToCreate= delegatesToCreate;
		fSettings= settings;
		fSave= save;
		fApply= apply;
	}

	/**
	 * Returns the method binding keys for which a method has been generated.
	 *
	 * @return the method binding keys
	 */
	public final String[] getCreatedMethods() {
		final String[] keys= new String[fCreated.size()];
		fCreated.toArray(keys);
		return keys;
	}

	/**
	 * Returns the resulting text edit.
	 *
	 * @return the resulting text edit
	 */
	public final TextEdit getResultingEdit() {
		return fResultingEdit;
	}

	/**
	 * Returns the scheduling rule for this operation.
	 *
	 * @return the scheduling rule
	 */
	public final ISchedulingRule getSchedulingRule() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}

	/*
	 * @see org.eclipse.core.resources.IWorkspaceRunnable#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public final void run(IProgressMonitor monitor) throws CoreException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(CodeGenerationMessages.AddDelegateMethodsOperation_monitor_message);
			fCreated.clear();
			ICompilationUnit cu= (ICompilationUnit) fASTRoot.getTypeRoot();

			ASTRewrite astRewrite= ASTRewrite.create(fASTRoot.getAST());
			ImportRewrite importRewrite= StubUtility.createImportRewrite(fASTRoot, true);

			ITypeBinding parentType= fDelegatesToCreate[0].field.getDeclaringClass();

			ASTNode typeDecl= fASTRoot.findDeclaringNode(parentType);

			ListRewrite listRewriter= null;
			if (typeDecl instanceof AbstractTypeDeclaration) {
				listRewriter= astRewrite.getListRewrite(typeDecl, ((AbstractTypeDeclaration) typeDecl).getBodyDeclarationsProperty());
			} else if (typeDecl instanceof AnonymousClassDeclaration) {
				listRewriter= astRewrite.getListRewrite(typeDecl, AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY);
			}

			if (listRewriter != null) {
				ASTNode insertion= StubUtility2.getNodeToInsertBefore(listRewriter, fInsert);

				ContextSensitiveImportRewriteContext context= new ContextSensitiveImportRewriteContext(fASTRoot, typeDecl.getStartPosition(), importRewrite);

				Arrays.sort(fDelegatesToCreate, new DelegateEntryComparator());

				for (int i= 0; i < fDelegatesToCreate.length; i++) {
					IMethodBinding delegateMethod= fDelegatesToCreate[i].delegateMethod;
					IVariableBinding field= fDelegatesToCreate[i].field;
					MethodDeclaration newMethod= StubUtility2.createDelegationStub(cu, astRewrite, importRewrite, context, delegateMethod, field, fSettings);
					if (newMethod != null) {
						fCreated.add(delegateMethod);
						if (insertion != null && insertion.getParent() == typeDecl)
							listRewriter.insertBefore(newMethod, insertion, null);
						else
							listRewriter.insertLast(newMethod, null);
					}
				}
				fResultingEdit= new MultiTextEdit();
				fResultingEdit.addChild(astRewrite.rewriteAST());
				fResultingEdit.addChild(importRewrite.rewriteImports(new SubProgressMonitor(monitor, 1)));

				if (fApply) {
					JavaModelUtil.applyEdit(cu, fResultingEdit, fSave, new SubProgressMonitor(monitor, 1));
				}
			}
		} finally {
			monitor.done();
		}
	}
}

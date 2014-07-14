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
package org.eclipse.jdt.internal.corext.codemanipulation;

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
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

/**
 * Workspace runnable to add custom constructors initializing fields.
 *
 * @since 3.1
 */
public final class AddCustomConstructorOperation implements IWorkspaceRunnable {

	/** Should the resulting edit be applied? */
	private boolean fApply= true;

	/** The super constructor method binding */
	private final IMethodBinding fConstructorBinding;

	/** The variable bindings to implement */
	private final IVariableBinding[] fFieldBindings;

	/** The resulting text edit */
	private TextEdit fResultingEdit= null;

	/** The insertion point, or <code>null</code> */
	private final IJavaElement fInsert;

	/** Should the call to the super constructor be omitted? */
	private boolean fOmitSuper= false;

	/** Should the compilation unit content be saved? */
	private final boolean fSave;

	/** The code generation settings to use */
	private final CodeGenerationSettings fSettings;

	/** The type declaration to add the constructors to */
	private final ITypeBinding fParentType;

	/** The compilation unit ast node */
	private final CompilationUnit fASTRoot;

	/** The visibility flags of the new constructor */
	private int fVisibility= 0;

	/**
	 * Creates a new add custom constructor operation.
	 *
	 * @param astRoot the compilation unit ast node
	 * @param parentType the type to add the methods to
	 * 	@param variables the variable bindings to use in the constructor
	 * @param constructor the method binding of the super constructor
	 * @param insert the insertion point, or <code>null</code>


	 * @param settings the code generation settings to use
	 * @param apply <code>true</code> if the resulting edit should be applied, <code>false</code> otherwise
	 * @param save <code>true</code> if the changed compilation unit should be saved, <code>false</code> otherwise
	 */
	public AddCustomConstructorOperation(CompilationUnit astRoot, ITypeBinding parentType, IVariableBinding[] variables, IMethodBinding constructor, IJavaElement insert, CodeGenerationSettings settings, boolean apply, boolean save) {
		Assert.isTrue(astRoot != null && astRoot.getTypeRoot() instanceof ICompilationUnit);
		Assert.isNotNull(parentType);
		Assert.isNotNull(variables);
		Assert.isNotNull(constructor);
		Assert.isNotNull(settings);
		fParentType= parentType;
		fInsert= insert;
		fASTRoot= astRoot;
		fFieldBindings= variables;
		fConstructorBinding= constructor;
		fSettings= settings;
		fSave= save;
		fApply= apply;
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

	/**
	 * Returns the visibility modifier of the generated constructors.
	 *
	 * @return the visibility modifier
	 */
	public final int getVisibility() {
		return fVisibility;
	}

	/**
	 * Should the call to the super constructor be omitted?
	 *
	 * @return <code>true</code> to omit the call, <code>false</code> otherwise
	 */
	public final boolean isOmitSuper() {
		return fOmitSuper;
	}

	/*
	 * @see org.eclipse.core.resources.IWorkspaceRunnable#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public final void run(IProgressMonitor monitor) throws CoreException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			monitor.beginTask("", 2); //$NON-NLS-1$
			monitor.setTaskName(CodeGenerationMessages.AddCustomConstructorOperation_description);

			ICompilationUnit cu= (ICompilationUnit) fASTRoot.getTypeRoot();

			ASTRewrite astRewrite= ASTRewrite.create(fASTRoot.getAST());
			ImportRewrite importRewrite= StubUtility.createImportRewrite(fASTRoot, true);

			ListRewrite listRewriter= null;

			ASTNode typeDecl= fASTRoot.findDeclaringNode(fParentType);
			if (typeDecl instanceof AbstractTypeDeclaration) {
				listRewriter= astRewrite.getListRewrite(typeDecl, ((AbstractTypeDeclaration) typeDecl).getBodyDeclarationsProperty());
			} else if (typeDecl instanceof AnonymousClassDeclaration) {
				listRewriter= astRewrite.getListRewrite(typeDecl, AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY);
			}

			if (listRewriter != null) {
				ImportRewriteContext context= new ContextSensitiveImportRewriteContext(typeDecl, importRewrite);
				MethodDeclaration stub= StubUtility2.createConstructorStub(cu, astRewrite, importRewrite, context, fParentType, fOmitSuper ? null : fConstructorBinding, fFieldBindings, fVisibility, fSettings);
				if (stub != null) {
					ASTNode insertion= StubUtility2.getNodeToInsertBefore(listRewriter, fInsert);
					if (insertion != null && insertion.getParent() == typeDecl) {
						listRewriter.insertBefore(stub, insertion, null);
					} else {
						listRewriter.insertLast(stub, null);
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

	/**
	 * Determines whether the call to the super constructor should be omitted.
	 *
	 * @param omit <code>true</code> to omit the call, <code>false</code> otherwise
	 */
	public final void setOmitSuper(final boolean omit) {
		fOmitSuper= omit;
	}

	/**
	 * Sets the visibility modifier of the generated constructors.
	 *
	 * @param visibility the visibility modifier
	 */
	public final void setVisibility(final int visibility) {
		fVisibility= visibility;
	}
}

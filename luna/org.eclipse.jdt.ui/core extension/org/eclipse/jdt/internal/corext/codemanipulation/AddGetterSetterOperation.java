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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.text.edits.TextEdit;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.formatter.CodeFormatter;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.JavaUI;

/**
 * Workspace runnable to add accessor methods to fields.
 *
 * @since 3.1
 */
public final class AddGetterSetterOperation implements IWorkspaceRunnable {

	/** The empty strings constant */
	private static final String[] EMPTY_STRINGS= new String[0];

	/** The accessor fields */
	private final IField[] fAccessorFields;

	/** Should the resulting edit be applied? */
	private boolean fApply= true;

	/** The resulting text edit */
	private TextEdit fEdit= null;

	/** The getter fields */
	private final IField[] fGetterFields;

	/** The insertion point, or <code>null</code> */
	private final IJavaElement fInsert;

	/** Should the compilation unit content be saved? */
	private final boolean fSave;

	/** The setter fields */
	private final IField[] fSetterFields;

	/** The code generation settings to use */
	private final CodeGenerationSettings fSettings;

	/** Should all existing members be skipped? */
	private boolean fSkipAllExisting= false;

	/** The skip existing request query */
	private final IRequestQuery fSkipExistingQuery;

	/** Should the accessors be sorted? */
	private boolean fSort= false;

	/** The type declaration to add the constructors to */
	private final IType fType;

	/** The compilation unit ast node */
	private final CompilationUnit fASTRoot;

	/** The visibility flags of the new accessors */
	private int fVisibility= Modifier.PUBLIC;

	/**
	 * Creates a new add getter setter operation.
	 *
	 * @param type the type to add the accessors to
	 * @param getters the fields to create getters for
	 * @param setters the fields to create setters for
	 * @param accessors the fields to create both
	 * @param unit the compilation unit ast node
	 * @param skipExistingQuery the request query
	 * @param insert the insertion point, or <code>null</code>
	 * @param settings the code generation settings to use
	 * @param apply <code>true</code> if the resulting edit should be applied, <code>false</code> otherwise
	 * @param save <code>true</code> if the changed compilation unit should be saved, <code>false</code> otherwise
	 */
	public AddGetterSetterOperation(final IType type, final IField[] getters, final IField[] setters, final IField[] accessors, final CompilationUnit unit, final IRequestQuery skipExistingQuery, final IJavaElement insert, final CodeGenerationSettings settings, final boolean apply, final boolean save) {
		Assert.isNotNull(type);
		Assert.isNotNull(unit);
		Assert.isNotNull(settings);
		fType= type;
		fGetterFields= getters;
		fSetterFields= setters;
		fAccessorFields= accessors;
		fASTRoot= unit;
		fSkipExistingQuery= skipExistingQuery;
		fInsert= insert;
		fSettings= settings;
		fSave= save;
		fApply= apply;
	}

	/**
	 * Adds a new accessor for the specified field.
	 *
	 * @param type the type
	 * @param field the field
	 * @param contents the contents of the accessor method
	 * @param rewrite the list rewrite to use
	 * @param insertion the insertion point
	 * @throws JavaModelException if an error occurs
	 */
	private void addNewAccessor(final IType type, final IField field, final String contents, final ListRewrite rewrite, final ASTNode insertion) throws JavaModelException {
		final String delimiter= StubUtility.getLineDelimiterUsed(type);
		final MethodDeclaration declaration= (MethodDeclaration) rewrite.getASTRewrite().createStringPlaceholder(CodeFormatterUtil.format(CodeFormatter.K_CLASS_BODY_DECLARATIONS, contents, 0, delimiter, field.getJavaProject()), ASTNode.METHOD_DECLARATION);
		if (insertion != null)
			rewrite.insertBefore(declaration, insertion, null);
		else
			rewrite.insertLast(declaration, null);
	}

	/**
	 * Generates a new getter method for the specified field
	 *
	 * @param field the field
	 * @param rewrite the list rewrite to use
	 * @throws CoreException if an error occurs
	 * @throws OperationCanceledException if the operation has been cancelled
	 */
	private void generateGetterMethod(final IField field, final ListRewrite rewrite) throws CoreException, OperationCanceledException {
		final IType type= field.getDeclaringType();
		final String name= GetterSetterUtil.getGetterName(field, null);
		final IMethod existing= JavaModelUtil.findMethod(name, EMPTY_STRINGS, false, type);
		if (existing == null || !querySkipExistingMethods(existing)) {
			IJavaElement sibling= null;
			if (existing != null) {
				sibling= StubUtility.findNextSibling(existing);
				removeExistingAccessor(existing, rewrite);
			} else
				sibling= fInsert;
			ASTNode insertion= StubUtility2.getNodeToInsertBefore(rewrite, sibling);
			addNewAccessor(type, field, GetterSetterUtil.getGetterStub(field, name, fSettings.createComments, fVisibility | (field.getFlags() & Flags.AccStatic)), rewrite, insertion);
		}
	}

	/**
	 * Generates a new setter method for the specified field
	 * 
	 * @param field the field
	 * @param astRewrite the AST rewrite to use
	 * @param rewrite the list rewrite to use
	 * @throws CoreException if an error occurs
	 * @throws OperationCanceledException if the operation has been cancelled
	 */
	private void generateSetterMethod(final IField field, ASTRewrite astRewrite, final ListRewrite rewrite) throws CoreException, OperationCanceledException {
		final IType type= field.getDeclaringType();
		final String name= GetterSetterUtil.getSetterName(field, null);
		final IMethod existing= JavaModelUtil.findMethod(name, new String[] { field.getTypeSignature()}, false, type);
		if (existing == null || !querySkipExistingMethods(existing)) {
			IJavaElement sibling= null;
			if (existing != null) {
				sibling= StubUtility.findNextSibling(existing);
				removeExistingAccessor(existing, rewrite);
			} else
				sibling= fInsert;
			ASTNode insertion= StubUtility2.getNodeToInsertBefore(rewrite, sibling);
			addNewAccessor(type, field, GetterSetterUtil.getSetterStub(field, name, fSettings.createComments, fVisibility | (field.getFlags() & Flags.AccStatic)), rewrite, insertion);
			if (Flags.isFinal(field.getFlags())) {
				ASTNode fieldDecl= ASTNodes.getParent(NodeFinder.perform(fASTRoot, field.getNameRange()), FieldDeclaration.class);
				if (fieldDecl != null) {
					ModifierRewrite.create(astRewrite, fieldDecl).setModifiers(0, Modifier.FINAL, null);
				}
			}

		}
	}

	/**
	 * Returns the resulting text edit.
	 *
	 * @return the resulting text edit
	 */
	public final TextEdit getResultingEdit() {
		return fEdit;
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
	 * Should all existing members be skipped?
	 *
	 * @return <code>true</code> if they should be skipped, <code>false</code> otherwise
	 */
	public final boolean isSkipAllExisting() {
		return fSkipAllExisting;
	}

	/**
	 * Queries the user whether to skip existing methods.
	 *
	 * @param method the method in question
	 * @return <code>true</code> to skip existing methods, <code>false</code> otherwise
	 * @throws OperationCanceledException if the operation has been cancelled
	 */
	private boolean querySkipExistingMethods(final IMethod method) throws OperationCanceledException {
		if (!fSkipAllExisting) {
			switch (fSkipExistingQuery.doQuery(method)) {
				case IRequestQuery.CANCEL:
					throw new OperationCanceledException();
				case IRequestQuery.NO:
					return false;
				case IRequestQuery.YES_ALL:
					fSkipAllExisting= true;
			}
		}
		return true;
	}

	/**
	 * Removes an existing accessor method.
	 *
	 * @param accessor the accessor method to remove
	 * @param rewrite the list rewrite to use
	 * @throws JavaModelException if an error occurs
	 */
	private void removeExistingAccessor(final IMethod accessor, final ListRewrite rewrite) throws JavaModelException {
		final MethodDeclaration declaration= (MethodDeclaration) ASTNodes.getParent(NodeFinder.perform(rewrite.getParent().getRoot(), accessor.getNameRange()), MethodDeclaration.class);
		if (declaration != null)
			rewrite.remove(declaration, null);
	}

	/*
	 * @see org.eclipse.core.resources.IWorkspaceRunnable#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public final void run(IProgressMonitor monitor) throws CoreException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			monitor.setTaskName(CodeGenerationMessages.AddGetterSetterOperation_description);
			monitor.beginTask("", fGetterFields.length + fSetterFields.length); //$NON-NLS-1$
			final ICompilationUnit unit= fType.getCompilationUnit();
			final ASTRewrite astRewrite= ASTRewrite.create(fASTRoot.getAST());
			ListRewrite listRewriter= null;
			if (fType.isAnonymous()) {
				final ClassInstanceCreation creation= (ClassInstanceCreation) ASTNodes.getParent(NodeFinder.perform(fASTRoot, fType.getNameRange()), ClassInstanceCreation.class);
				if (creation != null) {
					final AnonymousClassDeclaration declaration= creation.getAnonymousClassDeclaration();
					if (declaration != null)
						listRewriter= astRewrite.getListRewrite(declaration, AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY);
				}
			} else {
				final AbstractTypeDeclaration declaration= (AbstractTypeDeclaration) ASTNodes.getParent(NodeFinder.perform(fASTRoot, fType.getNameRange()), AbstractTypeDeclaration.class);
				if (declaration != null)
					listRewriter= astRewrite.getListRewrite(declaration, declaration.getBodyDeclarationsProperty());
			}
			if (listRewriter == null) {
				throw new CoreException(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.ERROR, CodeGenerationMessages.AddGetterSetterOperation_error_input_type_not_found, null));
			}

			fSkipAllExisting= (fSkipExistingQuery == null);
			
			Set<IField> accessors = new HashSet<IField>(Arrays.asList(fAccessorFields));
			Set<IField> getters = new HashSet<IField>(Arrays.asList(fGetterFields));
			Set<IField> setters= new HashSet<IField>(Arrays.asList(fSetterFields));
			IField[] fields= fType.getFields(); // generate methods in order of field declarations
			if (!fSort) {
				for (int i= 0; i < fields.length; i++) {
					if (accessors.contains(fields[i])) {
						generateGetterMethod(fields[i], listRewriter);
						generateSetterMethod(fields[i], astRewrite, listRewriter);
						monitor.worked(1);
						if (monitor.isCanceled()) {
							throw new OperationCanceledException();
						}
					}
				}
			}
			for (int i= 0; i < fields.length; i++) {
				if (getters.contains(fields[i])) {
					generateGetterMethod(fields[i], listRewriter);
					monitor.worked(1);
					if (monitor.isCanceled()) {
						throw new OperationCanceledException();
					}
				}
			}
			for (int i= 0; i < fields.length; i++) {
				if (setters.contains(fields[i])) {
					generateSetterMethod(fields[i], astRewrite, listRewriter);
					monitor.worked(1);
					if (monitor.isCanceled()) {
						throw new OperationCanceledException();
					}
				}
			}
			fEdit= astRewrite.rewriteAST();
			if (fApply) {
				JavaModelUtil.applyEdit(unit, fEdit, fSave, new SubProgressMonitor(monitor, 1));
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Determines whether existing members should be skipped.
	 *
	 * @param skip <code>true</code> to skip existing members, <code>false</code> otherwise
	 */
	public final void setSkipAllExisting(final boolean skip) {
		fSkipAllExisting= skip;
	}

	public void setSort(boolean sort) {
		fSort= sort;
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

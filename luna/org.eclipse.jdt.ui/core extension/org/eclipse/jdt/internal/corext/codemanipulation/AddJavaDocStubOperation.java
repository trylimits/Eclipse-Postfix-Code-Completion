/*******************************************************************************
 * Copyright (c) 2000, 2009 IBM Corporation and others.
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
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;

import org.eclipse.core.resources.IWorkspaceRunnable;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;

import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

import org.eclipse.jdt.internal.corext.dom.TokenScanner;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;
import org.eclipse.jdt.internal.corext.util.Strings;
import org.eclipse.jdt.internal.corext.util.SuperTypeHierarchyCache;

import org.eclipse.jdt.ui.CodeGeneration;

import org.eclipse.jdt.internal.ui.JavaUIStatus;

/**
 * Add javadoc stubs to members. All members must belong to the same compilation unit.
 * If the parent type is open in an editor, be sure to pass over its working copy.
 */
public class AddJavaDocStubOperation implements IWorkspaceRunnable {

	private IMember[] fMembers;

	public AddJavaDocStubOperation(IMember[] members) {
		Assert.isLegal(members.length > 0);
		fMembers= members;
	}

	private String createTypeComment(IType type, String lineDelimiter) throws CoreException {
		String[] typeParameterNames= StubUtility.getTypeParameterNames(type.getTypeParameters());
		return CodeGeneration.getTypeComment(type.getCompilationUnit(), type.getTypeQualifiedName('.'), typeParameterNames, lineDelimiter);
	}

	private String createMethodComment(IMethod meth, String lineDelimiter) throws CoreException {
		IType declaringType= meth.getDeclaringType();

		IMethod overridden= null;
		if (!meth.isConstructor()) {
			ITypeHierarchy hierarchy= SuperTypeHierarchyCache.getTypeHierarchy(declaringType);
			MethodOverrideTester tester= new MethodOverrideTester(declaringType, hierarchy);
			overridden= tester.findOverriddenMethod(meth, true);
		}
		return CodeGeneration.getMethodComment(meth, overridden, lineDelimiter);
	}

	private String createFieldComment(IField field, String lineDelimiter) throws JavaModelException, CoreException {
		String typeName= Signature.toString(field.getTypeSignature());
		String fieldName= field.getElementName();
		return CodeGeneration.getFieldComment(field.getCompilationUnit(), typeName, fieldName, lineDelimiter);
	}

	/**
	 * @return Returns the scheduling rule for this operation
	 */
	public ISchedulingRule getScheduleRule() {
		return fMembers[0].getResource();
	}

	/*
	 * @see org.eclipse.core.resources.IWorkspaceRunnable#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void run(IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		if (monitor == null)
			monitor= new NullProgressMonitor();

		try {
			monitor.beginTask(CodeGenerationMessages.AddJavaDocStubOperation_description, fMembers.length + 2);

			addJavadocComments(monitor);
		} finally {
			monitor.done();
		}
	}

	private void addJavadocComments(IProgressMonitor monitor) throws CoreException {
		ICompilationUnit cu= fMembers[0].getCompilationUnit();

		ITextFileBufferManager manager= FileBuffers.getTextFileBufferManager();
		IPath path= cu.getPath();

		manager.connect(path, LocationKind.IFILE, new SubProgressMonitor(monitor, 1));
		try {
			IDocument document= manager.getTextFileBuffer(path, LocationKind.IFILE).getDocument();

			String lineDelim= TextUtilities.getDefaultLineDelimiter(document);
			MultiTextEdit edit= new MultiTextEdit();

			for (int i= 0; i < fMembers.length; i++) {
				IMember curr= fMembers[i];
				int memberStartOffset= getMemberStartOffset(curr, document);

				String comment= null;
				switch (curr.getElementType()) {
					case IJavaElement.TYPE:
						comment= createTypeComment((IType) curr, lineDelim);
						break;
					case IJavaElement.FIELD:
						comment= createFieldComment((IField) curr, lineDelim);
						break;
					case IJavaElement.METHOD:
						comment= createMethodComment((IMethod) curr, lineDelim);
						break;
				}
				if (comment == null) {
					StringBuffer buf= new StringBuffer();
					buf.append("/**").append(lineDelim); //$NON-NLS-1$
					buf.append(" *").append(lineDelim); //$NON-NLS-1$
					buf.append(" */").append(lineDelim); //$NON-NLS-1$
					comment= buf.toString();
				} else {
					if (!comment.endsWith(lineDelim)) {
						comment= comment + lineDelim;
					}
				}

				final IJavaProject project= cu.getJavaProject();
				IRegion region= document.getLineInformationOfOffset(memberStartOffset);

				String line= document.get(region.getOffset(), region.getLength());
				String indentString= Strings.getIndentString(line, project);

				String indentedComment= Strings.changeIndent(comment, 0, project, indentString, lineDelim);

				edit.addChild(new InsertEdit(memberStartOffset, indentedComment));

				monitor.worked(1);
			}
			edit.apply(document); // apply all edits
		} catch (BadLocationException e) {
			throw new CoreException(JavaUIStatus.createError(IStatus.ERROR, e));
		} finally {
			manager.disconnect(path, LocationKind.IFILE,new SubProgressMonitor(monitor, 1));
		}
	}

	private int getMemberStartOffset(IMember curr, IDocument document) throws JavaModelException {
		int offset= curr.getSourceRange().getOffset();
		TokenScanner scanner= new TokenScanner(document, curr.getJavaProject());
		try {
			return scanner.getNextStartOffset(offset, true); // read to the first real non comment token
		} catch (CoreException e) {
			// ignore
		}
		return offset;
	}

}

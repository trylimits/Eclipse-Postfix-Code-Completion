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
package org.eclipse.jdt.internal.corext.refactoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IImportContainer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgUtils;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.util.Strings;

import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;

/**
 * A tuple used to keep source of an element and its type.
 * @see IJavaElement
 * @see org.eclipse.jdt.core.ISourceReference
 */
public class TypedSource {

	private static class SourceTuple {

		private SourceTuple(ICompilationUnit unit) {
			this.unit= unit;
		}
		private ICompilationUnit unit;
		private CompilationUnit node;
	}

	private final String fSource;
	private final int fType;

	private TypedSource(String source, int type){
		Assert.isNotNull(source);
		Assert.isTrue(canCreateForType(type));
		fSource= source;
		fType= type;
	}

	public static TypedSource create(String source, int type) {
		if (source == null || ! canCreateForType(type))
			return null;
		return new TypedSource(source, type);
	}

	public String getSource() {
		return fSource;
	}

	public int getType() {
		return fType;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object other) {
		if (! (other instanceof TypedSource))
			return false;

		TypedSource ts= (TypedSource)other;
		return ts.getSource().equals(getSource()) && ts.getType() == getType();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return getSource().hashCode() ^ (97 * getType());
	}

	private static boolean canCreateForType(int type){
		return 		type == IJavaElement.FIELD
				|| 	type == IJavaElement.TYPE
				|| 	type == IJavaElement.IMPORT_CONTAINER
				|| 	type == IJavaElement.IMPORT_DECLARATION
				|| 	type == IJavaElement.INITIALIZER
				|| 	type == IJavaElement.METHOD
				|| 	type == IJavaElement.PACKAGE_DECLARATION;
	}


	public static void sortByType(TypedSource[] typedSources){
		Arrays.sort(typedSources, createTypeComparator());
	}

	public static Comparator<TypedSource> createTypeComparator() {
		return new Comparator<TypedSource>(){
			public int compare(TypedSource arg0, TypedSource arg1) {
				return arg0.getType() - arg1.getType();
			}
		};
	}
	public static TypedSource[] createTypedSources(IJavaElement[] javaElements) throws CoreException {
		//Map<ICompilationUnit, List<IJavaElement>>
		Map<ICompilationUnit, List<IJavaElement>> grouped= ReorgUtils.groupByCompilationUnit(Arrays.asList(javaElements));
		List<TypedSource> result= new ArrayList<TypedSource>(javaElements.length);
		for (Iterator<ICompilationUnit> iter= grouped.keySet().iterator(); iter.hasNext();) {
			ICompilationUnit cu= iter.next();
			for (Iterator<IJavaElement> iterator= grouped.get(cu).iterator(); iterator.hasNext();) {
				SourceTuple tuple= new SourceTuple(cu);
				TypedSource[] ts= createTypedSources(iterator.next(), tuple);
				if (ts != null)
					result.addAll(Arrays.asList(ts));
			}
		}
		return result.toArray(new TypedSource[result.size()]);
	}

	private static TypedSource[] createTypedSources(IJavaElement elem, SourceTuple tuple) throws CoreException {
		if (! ReorgUtils.isInsideCompilationUnit(elem))
			return null;
		if (elem.getElementType() == IJavaElement.IMPORT_CONTAINER)
			return createTypedSourcesForImportContainer(tuple, (IImportContainer)elem);
		else if (elem.getElementType() == IJavaElement.FIELD)
			return new TypedSource[] {create(getFieldSource((IField)elem, tuple), elem.getElementType())};
		return new TypedSource[] {create(getSourceOfDeclararationNode(elem, tuple.unit), elem.getElementType())};
	}

	private static TypedSource[] createTypedSourcesForImportContainer(SourceTuple tuple, IImportContainer container) throws JavaModelException, CoreException {
		IJavaElement[] imports= container.getChildren();
		List<TypedSource> result= new ArrayList<TypedSource>(imports.length);
		for (int i= 0; i < imports.length; i++) {
			result.addAll(Arrays.asList(createTypedSources(imports[i], tuple)));
		}
		return result.toArray(new TypedSource[result.size()]);
	}

	private static String getFieldSource(IField field, SourceTuple tuple) throws CoreException {
		if (Flags.isEnum(field.getFlags())) {
			String source= field.getSource();
			if (source != null)
				return source;
		} else {
			if (tuple.node == null) {
				ASTParser parser= ASTParser.newParser(ASTProvider.SHARED_AST_LEVEL);
				parser.setSource(tuple.unit);
				tuple.node= (CompilationUnit) parser.createAST(null);
			}
			FieldDeclaration declaration= ASTNodeSearchUtil.getFieldDeclarationNode(field, tuple.node);
			if (declaration.fragments().size() == 1)
				return getSourceOfDeclararationNode(field, tuple.unit);
			VariableDeclarationFragment declarationFragment= ASTNodeSearchUtil.getFieldDeclarationFragmentNode(field, tuple.node);
			IBuffer buffer= tuple.unit.getBuffer();
			StringBuffer buff= new StringBuffer();
			buff.append(buffer.getText(declaration.getStartPosition(), ((ASTNode) declaration.fragments().get(0)).getStartPosition() - declaration.getStartPosition()));
			buff.append(buffer.getText(declarationFragment.getStartPosition(), declarationFragment.getLength()));
			buff.append(";"); //$NON-NLS-1$
			return buff.toString();
		}
		return ""; //$NON-NLS-1$
	}

	private static String getSourceOfDeclararationNode(IJavaElement elem, ICompilationUnit cu) throws JavaModelException, CoreException {
		Assert.isTrue(elem.getElementType() != IJavaElement.IMPORT_CONTAINER);
		if (elem instanceof ISourceReference) {
			ISourceReference reference= (ISourceReference) elem;
			String source= reference.getSource();
			if (source != null)
				return Strings.trimIndentation(source, cu.getJavaProject(), false);
		}
		return ""; //$NON-NLS-1$
	}
}

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
package org.eclipse.jdt.internal.ui.search;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;


/**
 * Finds all implement occurrences of an extended class or an implemented interface.
 *
 * @since 3.1
 */
public class ImplementOccurrencesFinder implements IOccurrencesFinder {


	public static final String ID= "ImplementOccurrencesFinder"; //$NON-NLS-1$

	private class MethodVisitor extends ASTVisitor {

		/*
		 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.MethodDeclaration)
		 */
		@Override
		public boolean visit(MethodDeclaration node) {
			IMethodBinding binding= node.resolveBinding();
			if (binding != null && !Modifier.isStatic(binding.getModifiers())) {
				IMethodBinding method= Bindings.findOverriddenMethodInHierarchy(fSelectedType, binding);
				if (method != null) {
					SimpleName name= node.getName();
					fResult.add(new OccurrenceLocation(name.getStartPosition(), name.getLength(), 0, fDescription));
				}
			}
			return super.visit(node);
		}

		/*
		 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.AnonymousClassDeclaration)
		 */
		@Override
		public boolean visit(AnonymousClassDeclaration node) {
			// don't dive into anonymous type declarations.
			return false;
		}

		/*
		 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.TypeDeclarationStatement)
		 */
		@Override
		public boolean visit(TypeDeclarationStatement node) {
			// don't dive into local type declarations.
			return false;
		}
	}

	private CompilationUnit fASTRoot;
	private ASTNode fStart;
	private List<OccurrenceLocation> fResult;
	private ASTNode fSelectedNode;
	private ITypeBinding fSelectedType;
	private String fDescription;

	public ImplementOccurrencesFinder() {
		fResult= new ArrayList<OccurrenceLocation>();
	}

	public String initialize(CompilationUnit root, int offset, int length) {
		return initialize(root, NodeFinder.perform(root, offset, length));
	}

	public String initialize(CompilationUnit root, ASTNode node) {
		if (!(node instanceof Name))
			return SearchMessages.ImplementOccurrencesFinder_invalidTarget;

		fSelectedNode= ASTNodes.getNormalizedNode(node);
		if (!(fSelectedNode instanceof Type))
			return SearchMessages.ImplementOccurrencesFinder_invalidTarget;

		StructuralPropertyDescriptor location= fSelectedNode.getLocationInParent();
		if (location != TypeDeclaration.SUPERCLASS_TYPE_PROPERTY && location != TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY && location != EnumDeclaration.SUPER_INTERFACE_TYPES_PROPERTY)
			return SearchMessages.ImplementOccurrencesFinder_invalidTarget;

		fSelectedType= ((Type)fSelectedNode).resolveBinding();
		if (fSelectedType == null)
			return SearchMessages.ImplementOccurrencesFinder_invalidTarget;

		fStart= fSelectedNode.getParent(); // type declaration
		fASTRoot= root;
		fDescription= Messages.format(SearchMessages.ImplementOccurrencesFinder_occurrence_description, BasicElementLabels.getJavaElementName(fSelectedType.getName()));

		return null;
	}

	private void performSearch() {
		fStart.accept(new MethodVisitor());
		if (fSelectedNode != null)
			fResult.add(new OccurrenceLocation(fSelectedNode.getStartPosition(), fSelectedNode.getLength(), 0, fDescription));
	}

	public OccurrenceLocation[] getOccurrences() {
		performSearch();
		if (fResult.isEmpty())
			return null;
		return fResult.toArray(new OccurrenceLocation[fResult.size()]);
	}

	public int getSearchKind() {
		return K_IMPLEMENTS_OCCURRENCE;
	}

	public CompilationUnit getASTRoot() {
		return fASTRoot;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.search.IOccurrencesFinder#getJobLabel()
	 */
	public String getJobLabel() {
		return SearchMessages.ImplementOccurrencesFinder_searchfor ;
	}

	public String getElementName() {
		if (fSelectedNode != null) {
			return ASTNodes.asString(fSelectedNode);
		}
		return null;
	}

	public String getUnformattedPluralLabel() {
		return SearchMessages.ImplementOccurrencesFinder_label_plural;
	}

	public String getUnformattedSingularLabel() {
		return SearchMessages.ImplementOccurrencesFinder_label_singular;
	}

	public String getID() {
		return ID;
	}

}

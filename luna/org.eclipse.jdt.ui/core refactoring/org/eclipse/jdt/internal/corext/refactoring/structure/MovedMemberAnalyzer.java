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
package org.eclipse.jdt.internal.corext.refactoring.structure;

import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;

import org.eclipse.jdt.internal.corext.dom.Bindings;

/**
 * Updates references in moved static members.
 * Accepts <code>BodyDeclaration</code>s.
 */
/* package */ class MovedMemberAnalyzer extends MoveStaticMemberAnalyzer {
/*
 * cases:
 * - access to moved member (or to member of moved member) -> do nothing.
 * - (static) access to source -> change to source, import source.
 * - (static) access to target -> change to target.
 * - access to other type -> do nothing (import is done in MoveStaticMembersRefactoring#getUpdatedMemberSource())
 */
// TW: Adapted references to type
//	- Reference to type inside moved type:
//	  - if originally resolved by qualification -> no problem
//	  - if originally resolved by import -> must add import in target too (qualify if import ambiguous)

	public MovedMemberAnalyzer(CompilationUnitRewrite cuRewrite,
			IBinding[] members, ITypeBinding source, ITypeBinding target) {
		super(cuRewrite, members, source, target);
	}

	public boolean targetNeedsSourceImport() {
		return fNeedsImport;
	}

	//---- types and fields --------------------------

	@Override
	public boolean visit(SimpleName node) {
		if (node.isDeclaration() || isProcessed(node))
			return super.visit(node);
		IBinding binding= node.resolveBinding();
		if (isMovedMember(binding))
			return super.visit(node);

		if (isSourceAccess(binding))
			rewrite(node, fSource);
		return super.visit(node);
	}

	@Override
	public boolean visit(QualifiedName node) {
		IBinding binding= node.resolveBinding();
		if (isSourceAccess(binding)) {
			if (isMovedMember(binding)) {
				rewrite(node, fTarget);
				return false;
			} else {
				rewrite(node, fSource);
				return false;
			}
		} else if (isTargetAccess(binding)) {
			// remove qualifier:
			SimpleName replace= (SimpleName)fCuRewrite.getASTRewrite().createCopyTarget(node.getName());
			fCuRewrite.getASTRewrite().replace(node, replace, null);
			fCuRewrite.getImportRemover().registerRemovedNode(node);
			return false;
		}
		return super.visit(node);
	}

	@Override
	public boolean visit(FieldAccess node) {
		IBinding binding= node.resolveFieldBinding();
		if (isSourceAccess(binding)) {
			if (isMovedMember(binding)) {
				if (node.getExpression() != null)
					rewrite(node, fTarget);
			} else
				rewrite(node, fSource);

		} else if (isTargetAccess(binding)) {
			fCuRewrite.getASTRewrite().remove(node.getExpression(), null);
			fCuRewrite.getImportRemover().registerRemovedNode(node.getExpression());
		}
		return super.visit(node);
	}

	//---- method invocations ----------------------------------

	@Override
	public boolean visit(MethodInvocation node) {
		IBinding binding= node.resolveMethodBinding();
		if (isSourceAccess(binding)) {
			if (isMovedMember(binding)) {
				if (node.getExpression() != null)
					rewrite(node, fTarget);
			} else
				rewrite(node, fSource);

		} else if (isTargetAccess(binding)) {
			if (node.getExpression() != null) {
				fCuRewrite.getASTRewrite().remove(node.getExpression(), null);
				fCuRewrite.getImportRemover().registerRemovedNode(node.getExpression());
			}
		}
		return super.visit(node);
	}

	//---- javadoc references ----------------------------------

	@Override
	public boolean visit(MemberRef node) {
		IBinding binding= node.resolveBinding();
		if (isSourceAccess(binding)) {
			if (isMovedMember(binding)) {
				if (node.getQualifier() != null)
					rewrite(node, fTarget);
			} else
				rewrite(node, fSource);

		} else if (isTargetAccess(binding)) {
			// remove qualifier:
			SimpleName replace= (SimpleName)fCuRewrite.getASTRewrite().createCopyTarget(node.getName());
			fCuRewrite.getASTRewrite().replace(node, replace, null);
			fCuRewrite.getImportRemover().registerRemovedNode(node);
		}
		return super.visit(node);
	}

	@Override
	public boolean visit(MethodRef node) {
		IBinding binding= node.resolveBinding();
		if (isSourceAccess(binding)) {
			if (isMovedMember(binding)) {
				if (node.getQualifier() != null)
					rewrite(node, fTarget);
			} else
				rewrite(node, fSource);

		} else if (isTargetAccess(binding)) {
			// remove qualifier:
			SimpleName replace= (SimpleName)fCuRewrite.getASTRewrite().createCopyTarget(node.getName());
			fCuRewrite.getASTRewrite().replace(node, replace, null);
			fCuRewrite.getImportRemover().registerRemovedNode(node);
		}
		return super.visit(node);
	}

	//---- helper methods --------------------------------------

	private boolean isSourceAccess(IBinding binding) {
		if (binding instanceof IMethodBinding) {
			IMethodBinding method= (IMethodBinding)binding;
			return Modifier.isStatic(method.getModifiers()) && Bindings.equals(fSource, method.getDeclaringClass());
		} else if (binding instanceof ITypeBinding) {
			ITypeBinding type= (ITypeBinding)binding;
			return Modifier.isStatic(type.getModifiers()) && Bindings.equals(fSource, type.getDeclaringClass());
		} else if (binding instanceof IVariableBinding) {
			IVariableBinding field= (IVariableBinding)binding;
			return field.isField() && Modifier.isStatic(field.getModifiers()) && Bindings.equals(fSource, field.getDeclaringClass());
		}
		return false;
	}

	private boolean isTargetAccess(IBinding binding) {
		if (binding instanceof IMethodBinding) {
			IMethodBinding method= (IMethodBinding)binding;
			return Modifier.isStatic(method.getModifiers()) && Bindings.equals(fTarget, method.getDeclaringClass());
		} else if (binding instanceof ITypeBinding) {
			ITypeBinding type= (ITypeBinding)binding;
			return Modifier.isStatic(type.getModifiers()) && Bindings.equals(fTarget, type.getDeclaringClass());
		} else if (binding instanceof IVariableBinding) {
			IVariableBinding field= (IVariableBinding)binding;
			return field.isField() && Modifier.isStatic(field.getModifiers()) && Bindings.equals(fTarget, field.getDeclaringClass());
		}
		return false;
	}
}

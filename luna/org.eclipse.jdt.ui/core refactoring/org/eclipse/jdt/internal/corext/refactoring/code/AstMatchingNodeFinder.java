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
package org.eclipse.jdt.internal.corext.refactoring.code;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BlockComment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.MethodRefParameter;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.WildcardType;

import org.eclipse.jdt.internal.corext.dom.JdtASTMatcher;

class AstMatchingNodeFinder {

	private AstMatchingNodeFinder(){
	}

	public static ASTNode[] findMatchingNodes(ASTNode scope, ASTNode node){
		Visitor visitor= new Visitor(node);
		scope.accept(visitor);
		return visitor.getMatchingNodes();
	}

	private static class Visitor extends ASTVisitor{

		Collection<ASTNode> fFound;
		ASTMatcher fMatcher;
		ASTNode fNodeToMatch;

		Visitor(ASTNode nodeToMatch){
			fNodeToMatch= nodeToMatch;
			fFound= new ArrayList<ASTNode>();
			fMatcher= new JdtASTMatcher();
		}

		ASTNode[] getMatchingNodes(){
			return fFound.toArray(new ASTNode[fFound.size()]);
		}

		private boolean matches(ASTNode node){
			fFound.add(node);
			return false;
		}

		@Override
		public boolean visit(AnonymousClassDeclaration node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ArrayAccess node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ArrayCreation node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ArrayInitializer node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ArrayType node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(AssertStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(Assignment node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(Block node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(BooleanLiteral node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(BreakStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(CastExpression node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(CatchClause node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(CharacterLiteral node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ClassInstanceCreation node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(CompilationUnit node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ConditionalExpression node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ConstructorInvocation node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ContinueStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(DoStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(EmptyStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ExpressionStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(FieldAccess node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(FieldDeclaration node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ForStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(IfStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ImportDeclaration node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(InfixExpression node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(Initializer node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

        @Override
		public boolean visit(InstanceofExpression node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
        }

		@Override
		public boolean visit(Javadoc node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(LabeledStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(MethodDeclaration node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(MethodInvocation node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(NullLiteral node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(NumberLiteral node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(PackageDeclaration node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ParenthesizedExpression node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(PostfixExpression node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(PrefixExpression node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(PrimitiveType node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(QualifiedName node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ReturnStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(SimpleName node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(SimpleType node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(SingleVariableDeclaration node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(StringLiteral node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(SuperConstructorInvocation node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(SuperFieldAccess node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(SuperMethodInvocation node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(SwitchCase node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(SwitchStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(SynchronizedStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ThisExpression node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ThrowStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(TryStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(TypeDeclaration node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(TypeDeclarationStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(TypeLiteral node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(VariableDeclarationExpression node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(VariableDeclarationFragment node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(VariableDeclarationStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(WhileStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(AnnotationTypeDeclaration node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(AnnotationTypeMemberDeclaration node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(BlockComment node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(EnhancedForStatement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(EnumConstantDeclaration node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(EnumDeclaration node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(LineComment node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(MarkerAnnotation node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(MemberRef node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(MemberValuePair node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(MethodRef node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(MethodRefParameter node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(Modifier node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(NormalAnnotation node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(ParameterizedType node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(QualifiedType node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(SingleMemberAnnotation node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(TagElement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(TextElement node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(TypeParameter node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}

		@Override
		public boolean visit(WildcardType node) {
			if (node.subtreeMatch(fMatcher, fNodeToMatch))
				return matches(node);
			return super.visit(node);
		}
	}
}

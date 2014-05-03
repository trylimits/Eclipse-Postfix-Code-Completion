package org.eclipse.jdt.postfixcompletion.parser;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;

public class StatementParser extends ASTVisitor implements IStatementParser {
	/*
	private String outerExpression;
	private String innerExpression;
	
	@Override
	public void preVisit(ASTNode node) {
	
		if (((node instanceof ExpressionStatement) || node instanceof ReturnStatement)
				&& c.getInvocationOffset() - 1 > node
						.getStartPosition()
				&& c.getInvocationOffset() <= node
						.getStartPosition()
						+ node.getLength()
						+ 1) {
			resultList.add(node);
			System.out.println("-------------------------");
		}
		if (resultList.size() == 1) {
			if (node instanceof SimpleName) {
				SimpleName sn = ((SimpleName)node);
				System.out.println("SimpleName: " + ((sn.resolveTypeBinding() != null) ? sn.resolveTypeBinding().getQualifiedName() : ""));
			}
			if (node instanceof InfixExpression) {
				infixFound = true;
				InfixExpression ie = (InfixExpression) node;
				if (ie.resolveTypeBinding() != null)
					System.out.println("Infix: " + ie.resolveTypeBinding().getQualifiedName());
			}
			System.out.println("Found node : " + ((node != null) ? node.getNodeType() + "(" + node.getClass().getSimpleName() + ") " + node : ""));
		}	
	}
	*/

}

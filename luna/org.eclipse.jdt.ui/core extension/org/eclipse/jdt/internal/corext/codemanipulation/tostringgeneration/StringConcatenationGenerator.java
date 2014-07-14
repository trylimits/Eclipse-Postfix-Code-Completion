/*******************************************************************************
 * Copyright (c) 2008, 2011 Mateusz Matela and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Mateusz Matela <mateusz.matela@gmail.com> - [code manipulation] [dcr] toString() builder wizard - https://bugs.eclipse.org/bugs/show_bug.cgi?id=26070
 *     Mateusz Matela <mateusz.matela@gmail.com> - [toString] Wrong code generated with String concatenation - https://bugs.eclipse.org/bugs/show_bug.cgi?id=275360
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;


/**
 * <p>
 * Implementation of <code>AbstractToStringGenerator</code> that creates <code>toString()</code>
 * method by concatenating <code>String</code>s
 * </p>
 * <p>
 * Generated methods look like this:
 * 
 * <pre>
 * public String toString() {
 * 	return &quot;FooClass( field1=&quot; + field1 + &quot;, field2=&quot; + field2 + &quot;, method1()=&quot; + method1 + &quot;)&quot;;
 * }
 * </pre>
 * 
 * </p>
 * 
 * @since 3.5
 */
public class StringConcatenationGenerator extends AbstractToStringGenerator {

	private class SumExpressionBuilder {
		private Expression expression;

		private StringBuffer buffer;

		public SumExpressionBuilder(Expression expression) {
			this.expression= expression;
			buffer= new StringBuffer();
		}

		public Expression getExpression() {
			flushBuffer();
			return expression;
		}

		private void flushBuffer() {
			if (buffer.length() > 0) {
				StringLiteral bufferedStringLiteral= fAst.newStringLiteral();
				bufferedStringLiteral.setLiteralValue(buffer.toString());
				buffer.setLength(0);
				expression= createSumExpression(expression, bufferedStringLiteral);
			}
		}

		public void addString(String string) {
			buffer.append(string);
		}

		public void addExpression(Expression exp) {
			flushBuffer();
			expression= createSumExpression(expression, exp);
		}
	}

	private SumExpressionBuilder toStringExpressionBuilder;

	@Override
	protected void initialize() {
		super.initialize();
		toStringExpressionBuilder= new SumExpressionBuilder(null);
	}

	@Override
	protected void complete() throws CoreException {
		super.complete();
		ReturnStatement returnStatement= fAst.newReturnStatement();
		returnStatement.setExpression(toStringExpressionBuilder.getExpression());
		toStringMethod.getBody().statements().add(returnStatement);
	}

	@Override
	protected void addElement(Object element) {
		addElement(element, toStringExpressionBuilder);
	}

	private void addElement(Object element, SumExpressionBuilder builder) {
		if (element instanceof String) {
			builder.addString((String)element);
		}
		if (element instanceof Expression) {
			Expression expr= (Expression)element;
			if (expr instanceof ConditionalExpression) {
				ParenthesizedExpression expr2= fAst.newParenthesizedExpression();
				expr2.setExpression(expr);
				expr= expr2;
			}
			builder.addExpression(expr);
		}
	}

	@Override
	protected void addMember(Object member, boolean addSeparator) {
		boolean[] interfaces= implementsInterfaces(getMemberType(member).getErasure(), new String[] { "java.util.Collection", "java.util.Map" }); //$NON-NLS-1$ //$NON-NLS-2$
		if (getContext().isLimitItems() && getContext().isSkipNulls() && (interfaces[0] || interfaces[1] || getMemberType(member).isArray())) {
			addMemberCheckNull(member, addSeparator);
		} else {
			super.addMember(member, addSeparator);
		}
	}

	@Override
	protected void addMemberCheckNull(Object member, boolean addSeparator) {
		ConditionalExpression cExpression= fAst.newConditionalExpression();

		// member != null ?
		InfixExpression infExpression= fAst.newInfixExpression();
		infExpression.setLeftOperand(createMemberAccessExpression(member, true, true));
		infExpression.setRightOperand(fAst.newNullLiteral());
		infExpression.setOperator(Operator.NOT_EQUALS);
		cExpression.setExpression(infExpression);

		SumExpressionBuilder builder= new SumExpressionBuilder(null);
		String[] arrayString= getContext().getTemplateParser().getBody();
		for (int i= 0; i < arrayString.length; i++) {
			addElement(processElement(arrayString[i], member), builder);
		}
		if (addSeparator)
			addElement(getContext().getTemplateParser().getSeparator(), builder);
		cExpression.setThenExpression(builder.getExpression());

		StringLiteral literal= fAst.newStringLiteral();
		literal.setLiteralValue(getContext().isSkipNulls() ? "" : "null"); //$NON-NLS-1$ //$NON-NLS-2$
		cExpression.setElseExpression(literal);

		ParenthesizedExpression pExpression= fAst.newParenthesizedExpression();
		pExpression.setExpression(cExpression);
		toStringExpressionBuilder.addExpression(pExpression);
	}

	private Expression createSumExpression(Expression left, Expression right) {
		if (right == null)
			return left;
		if (left == null)
			return right;
		return createInfixExpression(left, Operator.PLUS, right);
	}


}

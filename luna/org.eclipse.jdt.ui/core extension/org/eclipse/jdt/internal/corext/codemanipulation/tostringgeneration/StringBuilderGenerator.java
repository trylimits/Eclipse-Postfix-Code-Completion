/*******************************************************************************
 * Copyright (c) 2008, 2011 Mateusz Matela and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Mateusz Matela <mateusz.matela@gmail.com> - [code manipulation] [dcr] toString() builder wizard - https://bugs.eclipse.org/bugs/show_bug.cgi?id=26070
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;


/**
 * <p>
 * Implementation of <code>AbstractToStringGenerator</code> that creates <code>toString()</code>
 * method using <code>StringBuilder</code> (or <code>StringBuffer</code> for old versions of JDK)
 * </p>
 * <p>
 * Generated methods look like this:
 * 
 * <pre>
 * public String toString() {
 * 	StringBuilder builder= new StringBuilder();
 * 	builder.append(&quot;FooClass( field1=&quot;);
 * 	builder.append(field1);
 * 	builder.append(&quot;, field2=&quot;);
 * 	builder.append(field2);
 * 	builder.append(&quot; )&quot;);
 * 	return builder.toString();
 * }
 * </pre>
 * 
 * </p>
 * 
 * @since 3.5
 */
public class StringBuilderGenerator extends AbstractToStringGenerator {
	protected StringBuffer fBuffer;

	protected String fBuilderVariableName;

	protected final String APPEND_METHOD_NAME= "append"; //$NON-NLS-1$

	protected void flushBuffer(Block target) {
		if (fBuffer.length() > 0) {
			StringLiteral literal= fAst.newStringLiteral();
			literal.setLiteralValue(fBuffer.toString());
			if (target == null)
				target= toStringMethod.getBody();
			target.statements().add(fAst.newExpressionStatement(createMethodInvocation(fBuilderVariableName, APPEND_METHOD_NAME, literal)));
			fBuffer.setLength(0);
		}
	}

	@Override
	protected void initialize() {
		super.initialize();
		fBuilderVariableName= createNameSuggestion(getContext().is50orHigher() ? "builder" : "buffer", NamingConventions.VK_LOCAL); //$NON-NLS-1$ //$NON-NLS-2$
		fBuffer= new StringBuffer();
		VariableDeclarationFragment fragment= fAst.newVariableDeclarationFragment();
		fragment.setName(fAst.newSimpleName(fBuilderVariableName));
		ClassInstanceCreation classInstance= fAst.newClassInstanceCreation();
		Name typeName= addImport(getContext().is50orHigher() ? "java.lang.StringBuilder" : "java.lang.StringBuffer"); //$NON-NLS-1$ //$NON-NLS-2$
		classInstance.setType(fAst.newSimpleType(typeName));
		fragment.setInitializer(classInstance);
		VariableDeclarationStatement vStatement= fAst.newVariableDeclarationStatement(fragment);
		vStatement.setType(fAst.newSimpleType((Name)ASTNode.copySubtree(fAst, typeName)));
		toStringMethod.getBody().statements().add(vStatement);
	}

	@Override
	protected void complete() throws CoreException {
		flushBuffer(null);
		super.complete();
		ReturnStatement rStatement= fAst.newReturnStatement();
		rStatement.setExpression(createMethodInvocation(fBuilderVariableName, "toString", null)); //$NON-NLS-1$
		toStringMethod.getBody().statements().add(rStatement);
	}

	protected void addElement(Object element, Block block) {
		if (element instanceof String)
			fBuffer.append((String)element);
		if (element instanceof Expression) {
			flushBuffer(block);
			block.statements().add(fAst.newExpressionStatement(createMethodInvocation(fBuilderVariableName, APPEND_METHOD_NAME, (Expression)element)));
		}
	}

	@Override
	protected void addMemberCheckNull(Object member, boolean addSeparator) {
		IfStatement ifStatement= fAst.newIfStatement();
		ifStatement.setExpression(createInfixExpression(createMemberAccessExpression(member, true, true), Operator.NOT_EQUALS, fAst.newNullLiteral()));
		Block thenBlock= fAst.newBlock();
		flushBuffer(null);
		String[] arrayString= getContext().getTemplateParser().getBody();
		for (int i= 0; i < arrayString.length; i++) {
			addElement(processElement(arrayString[i], member), thenBlock);
		}
		if (addSeparator)
			addElement(getContext().getTemplateParser().getSeparator(), thenBlock);
		flushBuffer(thenBlock);

		if (thenBlock.statements().size() == 1 && !getContext().isForceBlocks()) {
			ifStatement.setThenStatement((Statement)ASTNode.copySubtree(fAst, (ASTNode)thenBlock.statements().get(0)));
		} else {
			ifStatement.setThenStatement(thenBlock);
		}
		toStringMethod.getBody().statements().add(ifStatement);
	}

	@Override
	protected void addElement(Object element) {
		addElement(element, toStringMethod.getBody());
	}
}

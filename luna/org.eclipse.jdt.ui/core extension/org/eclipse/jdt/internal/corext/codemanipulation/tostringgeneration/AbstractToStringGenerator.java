/*******************************************************************************
 * Copyright (c) 2008, 2011 Mateusz Matela and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Mateusz Matela <mateusz.matela@gmail.com> - [code manipulation] [dcr] toString() builder wizard - https://bugs.eclipse.org/bugs/show_bug.cgi?id=26070
 *     Mateusz Matela <mateusz.matela@gmail.com> - [toString] Generator uses wrong suffixes and prefixes - https://bugs.eclipse.org/bugs/show_bug.cgi?id=275370
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;

import org.eclipse.jdt.ui.CodeGeneration;

/**
 * <p>
 * This generator creates an implementation of <code>{@link java.lang.Object#toString()}</code>
 * which lists all selected fields and methods. What exactly is listed and how members are separated
 * is determined by a format template.
 * <p>
 * 
 * <p>
 * To print out items of arrays and/or limit number items printed for arrays, collections and so on,
 * various methods are used according to actual jdk compatibility:
 * <table border="10">
 * <tr>
 * <td></td>
 * <th><code>java.util.List</code></th>
 * <th><code>java.util.Collection</code></th>
 * <th><code>java.util.Map</code></th>
 * <th>Array of primitive types</th>
 * <th>Array of non-primitive types</th>
 * </tr>
 * <tr>
 * <th>jdk 1.4</th>
 * <td>-</td>
 * <td>-</td>
 * <td>-</td>
 * <td>helper method <code>arrayToString(Object array, int length)</code></td>
 * <td><code>Arrays.asList(member)</code></td>
 * </tr>
 * <tr>
 * <th>jdk 1.4/1.5, limit elements</th>
 * <td><code>member.subList()</code></td>
 * <td>helper method <code>toSting(Collection)</code></td>
 * <td>helper method <code>toString(Collection)</code> with <code>member.entrySet()</code></td>
 * <td>helper method <code>arrayToString(Object array, int length)</code></td>
 * <td><code>Arrays.asList(member).subList()</code></td>
 * </tr>
 * <tr>
 * <th>jdk 1.5</th>
 * <td>-</td>
 * <td>-</td>
 * <td>-</td>
 * <td><code>Arrays.toString()<code></td>
 * <td><code>Arrays.asList(member)</code></td>
 * </tr>
 * <tr>
 * <th>jdk 1.6</th>
 * <td>-</td>
 * <td>-</td>
 * <td>-</td>
 * <td><code>Arrays.toString()</code></td>
 * <td><code>Arrays.toString()</code></td>
 * </tr>
 * <tr>
 * <th>jdk 1.6, limit elements</th>
 * <td><code>member.subList()</code></td>
 * <td>helper method <code>toSting(Collection)</code></td>
 * <td>helper method <code>toString(Collection)</code> with <code>member.entrySet()</code></td>
 * <td><code>Arrays.toString(Arrays.copyOf(member, ...))</code></td>
 * <td><code>Arrays.asList(member).subList()</code></td>
 * </tr>
 * </table>
 * Additionally, if helper method is generated it is also used for other members members (even if
 * other solution could be used), as it makes the code cleaner.
 * </p>
 * 
 * @since 3.5
 */
public abstract class AbstractToStringGenerator {

	protected static final String METHODNAME_TO_STRING= "toString"; //$NON-NLS-1$

	protected static final String TYPENAME_STRING= "String"; //$NON-NLS-1$

	final protected String HELPER_TOSTRING_METHOD_NAME= "toString"; //$NON-NLS-1$

	final private String HELPER_ARRAYTOSTRING_METHOD_NAME= "arrayToString"; //$NON-NLS-1$

	final private String MAX_LEN_VARIABLE_NAME= "maxLen"; //$NON-NLS-1$
	protected String fMaxLenVariableName= MAX_LEN_VARIABLE_NAME;
	
	/**
	* The name of the property that every <code>MethodDeclaration</code> generated should have.
	* This property determines whether the method should be overwritten if already exists. The data
	* of this property is a Boolean.
	*/
	protected final static String OVERWRITE_METHOD_PROPERTY= "override_method"; //$NON-NLS-1$

	public ToStringTemplateParser getTemplateParser() {
		return new ToStringTemplateParser();
	}

	protected ToStringGenerationContext fContext;

	/** The ast to be used. Convenience accessor field */
	protected AST fAst;

	protected MethodDeclaration toStringMethod;

	protected boolean needMaxLenVariable;

	protected boolean needCollectionToStringMethod;

	protected List<ITypeBinding> typesThatNeedArrayToStringMethod;

	public RefactoringStatus checkConditions() {
		return new RefactoringStatus();
	}

	/**
	 * This method is an implementation of Director in Builder pattern. It goes through all elements
	 * of the format template and calls methods responsible for processing them.
	 * 
	 * @return declaration of the generated <code>toString()</code> method
	 * @throws CoreException if creation failed
	 */
	public MethodDeclaration generateToStringMethod() throws CoreException {
		initialize();

		String[] stringArray= fContext.getTemplateParser().getBeginning();
		for (int i= 0; i < stringArray.length; i++) {
			addElement(processElement(stringArray[i], null));
		}

		stringArray= fContext.getTemplateParser().getBody();
		Object[] members= fContext.getSelectedMembers();
		for (int i= 0; i < members.length; i++) {
			if (!fContext.isSkipNulls() || getMemberType(members[i]).isPrimitive())
				addMember(members[i], i != members.length - 1);
			else
				addMemberCheckNull(members[i], i != members.length - 1);
		}

		stringArray= fContext.getTemplateParser().getEnding();
		for (int i= 0; i < stringArray.length; i++) {
			addElement(processElement(stringArray[i], null));
		}

		complete();

		return toStringMethod;
	}

	public List<MethodDeclaration> generateHelperMethods() {
		List<MethodDeclaration> result= new ArrayList<MethodDeclaration>();
		if (needCollectionToStringMethod)
			result.add(createHelperToStringMethod(false));

		if (!typesThatNeedArrayToStringMethod.isEmpty())
			result.add(createHelperToStringMethod(true));

		return result;
	}

	/**
	 * adds a comment (if necessary) and an <code>@Override</code> annotation to the generated
	 * method
	 * 
	 * @throws CoreException if creation failed
	 */
	protected void createMethodComment() throws CoreException {
		ITypeBinding object= fAst.resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
		IMethodBinding[] objms= object.getDeclaredMethods();
		IMethodBinding objectMethod= null;
		for (int i= 0; i < objms.length; i++) {
			if (objms[i].getName().equals(METHODNAME_TO_STRING) && objms[i].getParameterTypes().length == 0)
				objectMethod= objms[i];
		}
		if (fContext.isCreateComments()) {
			String docString= CodeGeneration.getMethodComment(fContext.getCompilationUnit(), fContext.getTypeBinding().getQualifiedName(), toStringMethod, objectMethod, StubUtility
					.getLineDelimiterUsed(fContext.getCompilationUnit()));
			if (docString != null) {
				Javadoc javadoc= (Javadoc)fContext.getASTRewrite().createStringPlaceholder(docString, ASTNode.JAVADOC);
				toStringMethod.setJavadoc(javadoc);
			}
		}
		if (fContext.isOverrideAnnotation() && fContext.is50orHigher())
			StubUtility2.addOverrideAnnotation(fContext.getTypeBinding().getJavaElement().getJavaProject(), fContext.getASTRewrite(), toStringMethod, objectMethod);
	}

	/**
	 * Creates a method that takes a <code>Collection</code> or an Array and returns a
	 * <code>String</code> containing it's first <code>fSettings.limitValue</code> elements
	 * 
	 * @param array if true, generated method will convert array to string, otherwise -
	 *            <code>Collection</code>
	 * @return <code>arrayToString(Object[] array)</code> or </code>collectionToString(Collection
	 *         collection)</code> method
	 */
	protected MethodDeclaration createHelperToStringMethod(boolean array) {
		final String iteratorName= createNameSuggestion("iterator", NamingConventions.VK_LOCAL); //$NON-NLS-1$
		final String appendMethodName= "append"; //$NON-NLS-1$
		final String indexName= createNameSuggestion("i", NamingConventions.VK_LOCAL); //$NON-NLS-1$
		final String lengthParamName= createNameSuggestion("len", NamingConventions.VK_PARAMETER); //$NON-NLS-1$
		final String maxLenParamName= createNameSuggestion(MAX_LEN_VARIABLE_NAME, NamingConventions.VK_PARAMETER);
		String paramName;
		String stringBuilderName;
		String stringBuilderTypeName;

		if (fContext.is50orHigher()) {
			stringBuilderTypeName= "java.lang.StringBuilder"; //$NON-NLS-1$
			stringBuilderName= createNameSuggestion("builder", NamingConventions.VK_LOCAL); //$NON-NLS-1$
		} else {
			stringBuilderTypeName= "java.lang.StringBuffer"; //$NON-NLS-1$
			stringBuilderName= createNameSuggestion("buffer", NamingConventions.VK_LOCAL); //$NON-NLS-1$
		}

		//private arrayToString() {
		MethodDeclaration arrayToStringMethod= fAst.newMethodDeclaration();
		arrayToStringMethod.setName(fAst.newSimpleName(array ? HELPER_ARRAYTOSTRING_METHOD_NAME : HELPER_TOSTRING_METHOD_NAME));
		arrayToStringMethod.modifiers().add(fAst.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
		arrayToStringMethod.setReturnType2(fAst.newSimpleType(fAst.newName(TYPENAME_STRING)));

		if (array) {
			//(Objec array, int length, ...)
			paramName= createNameSuggestion("array", NamingConventions.VK_PARAMETER); //$NON-NLS-1$
			SingleVariableDeclaration param= fAst.newSingleVariableDeclaration();
			param.setType(fAst.newSimpleType(addImport("java.lang.Object"))); //$NON-NLS-1$
			param.setName(fAst.newSimpleName(paramName));
			arrayToStringMethod.parameters().add(param);

			param= fAst.newSingleVariableDeclaration();
			param.setType(fAst.newPrimitiveType(PrimitiveType.INT));
			param.setName(fAst.newSimpleName(lengthParamName));
			arrayToStringMethod.parameters().add(param);
		} else {
			//(Collection<?> collection, ...)
			paramName= createNameSuggestion("collection", NamingConventions.VK_PARAMETER); //$NON-NLS-1$
			SingleVariableDeclaration param= fAst.newSingleVariableDeclaration();
			Type collectionType= fAst.newSimpleType(addImport("java.util.Collection")); //$NON-NLS-1$
			if (fContext.is50orHigher()) {
				ParameterizedType genericType= fAst.newParameterizedType(collectionType);
				genericType.typeArguments().add(fAst.newWildcardType());
				param.setType(genericType);
			} else {
				param.setType(collectionType);
			}
			param.setName(fAst.newSimpleName(paramName));
			arrayToStringMethod.parameters().add(param);
		}
		if (fContext.isLimitItems()) {
			SingleVariableDeclaration param= fAst.newSingleVariableDeclaration();
			param.setType(fAst.newPrimitiveType(PrimitiveType.INT));
			param.setName(fAst.newSimpleName(maxLenParamName));
			arrayToStringMethod.parameters().add(param);
		}

		Block body= fAst.newBlock();
		arrayToStringMethod.setBody(body);

		//StringBuilder stringBuilder= new StringBuilder();
		VariableDeclarationFragment fragment= fAst.newVariableDeclarationFragment();
		fragment.setName(fAst.newSimpleName(stringBuilderName));
		ClassInstanceCreation classInstance= fAst.newClassInstanceCreation();
		classInstance.setType(fAst.newSimpleType(addImport(stringBuilderTypeName)));
		fragment.setInitializer(classInstance);
		VariableDeclarationStatement vStatement= fAst.newVariableDeclarationStatement(fragment);
		vStatement.setType(fAst.newSimpleType(addImport(stringBuilderTypeName)));
		body.statements().add(vStatement);

		if (array && fContext.isLimitItems()) {
			//length = Math.min(length, maxLen);
			MethodInvocation minInvocation= createMethodInvocation(addImport("java.lang.Math"), "min", fAst.newSimpleName(lengthParamName)); //$NON-NLS-1$ //$NON-NLS-2$
			minInvocation.arguments().add(fAst.newSimpleName(maxLenParamName));
			Assignment lengthAssignment= fAst.newAssignment();
			lengthAssignment.setLeftHandSide(fAst.newSimpleName(lengthParamName));
			lengthAssignment.setRightHandSide(minInvocation);
			body.statements().add(fAst.newExpressionStatement(lengthAssignment));
		}

		//stringBuilder.add("[");
		StringLiteral literal= fAst.newStringLiteral();
		literal.setLiteralValue("["); //$NON-NLS-1$
		body.statements().add(fAst.newExpressionStatement(createMethodInvocation(stringBuilderName, appendMethodName, literal)));

		//for(...
		ForStatement forStatement= fAst.newForStatement();
		Block forBlock= fAst.newBlock();
		forStatement.setBody(forBlock);

		//int i = 0;
		VariableDeclarationFragment indexDeclFragment= fAst.newVariableDeclarationFragment();
		indexDeclFragment.setName(fAst.newSimpleName(indexName));
		indexDeclFragment.setInitializer(fAst.newNumberLiteral("0")); //$NON-NLS-1$
		VariableDeclarationExpression indexDeclExpression= fAst.newVariableDeclarationExpression(indexDeclFragment);
		indexDeclExpression.setType(fAst.newPrimitiveType(PrimitiveType.INT));

		// i++
		PostfixExpression postfixExpr= fAst.newPostfixExpression();
		postfixExpr.setOperand(fAst.newSimpleName(indexName));
		postfixExpr.setOperator(org.eclipse.jdt.core.dom.PostfixExpression.Operator.INCREMENT);
		forStatement.updaters().add(postfixExpr);

		//if (i > 0) builder.append(", ");
		IfStatement ifStatement= fAst.newIfStatement();
		ifStatement.setExpression(createInfixExpression(fAst.newSimpleName(indexName), Operator.GREATER, fAst.newNumberLiteral(String.valueOf(0))));
		literal= fAst.newStringLiteral();
		literal.setLiteralValue(", "); //$NON-NLS-1$
		ifStatement.setThenStatement(createOneStatementBlock(createMethodInvocation(stringBuilderName, appendMethodName, literal)));
		forBlock.statements().add(ifStatement);

		if (array) {
			forStatement.initializers().add(indexDeclExpression);

			// i < length;
			forStatement.setExpression(createInfixExpression(fAst.newSimpleName(indexName), Operator.LESS, fAst.newSimpleName(lengthParamName)));

			for (Iterator<ITypeBinding> iterator= typesThatNeedArrayToStringMethod.iterator(); iterator.hasNext();) {
				ITypeBinding typeBinding= iterator.next();
				//if (array instanceof int[]) {
				String typeName= typeBinding.getName();
				PrimitiveType.Code code= null;
				if (typeName.equals("byte")) code= PrimitiveType.BYTE; //$NON-NLS-1$
				if (typeName.equals("short")) code= PrimitiveType.SHORT; //$NON-NLS-1$
				if (typeName.equals("char")) code= PrimitiveType.CHAR; //$NON-NLS-1$
				if (typeName.equals("int")) code= PrimitiveType.INT; //$NON-NLS-1$
				if (typeName.equals("long")) code= PrimitiveType.LONG; //$NON-NLS-1$
				if (typeName.equals("float")) code= PrimitiveType.FLOAT; //$NON-NLS-1$
				if (typeName.equals("double")) code= PrimitiveType.DOUBLE; //$NON-NLS-1$
				if (typeName.equals("boolean")) code= PrimitiveType.BOOLEAN; //$NON-NLS-1$
				if (code == null && !typeName.equals("Object"))continue; //$NON-NLS-1$
				InstanceofExpression instanceOf= fAst.newInstanceofExpression();
				instanceOf.setLeftOperand(fAst.newSimpleName(paramName));
				instanceOf.setRightOperand(fAst.newArrayType(code != null ? (Type)fAst.newPrimitiveType(code) : fAst.newSimpleType(addImport("java.lang.Object")))); //$NON-NLS-1$
				ifStatement= fAst.newIfStatement();
				ifStatement.setExpression(instanceOf);

				//builder.append(((int[]) array)[i]);
				CastExpression arrayCast= fAst.newCastExpression();
				arrayCast.setExpression(fAst.newSimpleName(paramName));
				arrayCast.setType(fAst.newArrayType(code != null ? (Type)fAst.newPrimitiveType(code) : fAst.newSimpleType(addImport("java.lang.Object")))); //$NON-NLS-1$
				ParenthesizedExpression parenthesizedCast= fAst.newParenthesizedExpression();
				parenthesizedCast.setExpression(arrayCast);
				ArrayAccess arrayAccess= fAst.newArrayAccess();
				arrayAccess.setArray(parenthesizedCast);
				arrayAccess.setIndex(fAst.newSimpleName(indexName));
				ifStatement.setThenStatement(createOneStatementBlock(createMethodInvocation(stringBuilderName, appendMethodName, arrayAccess)));

				forBlock.statements().add(ifStatement);
			}
		} else {
			body.statements().add(fAst.newExpressionStatement(indexDeclExpression));

			//... Iterator iterator= collection.iterator() ...
			fragment= fAst.newVariableDeclarationFragment();
			fragment.setName(fAst.newSimpleName(iteratorName));
			fragment.setInitializer(createMethodInvocation(paramName, "iterator", null)); //$NON-NLS-1$
			VariableDeclarationExpression vExpression= fAst.newVariableDeclarationExpression(fragment);
			SimpleType iteratorType= fAst.newSimpleType(addImport("java.util.Iterator")); //$NON-NLS-1$
			if (fContext.is50orHigher()) {
				ParameterizedType pType= fAst.newParameterizedType(iteratorType);
				pType.typeArguments().add(fAst.newWildcardType());
				vExpression.setType(pType);
			} else {
				vExpression.setType(iteratorType);
			}

			forStatement.initializers().add(vExpression);

			//iterator.hasNext() && i < maxLen;
			Expression indexExpression= createInfixExpression(fAst.newSimpleName(indexName), Operator.LESS, fAst.newSimpleName(maxLenParamName));
			forStatement.setExpression(createInfixExpression(createMethodInvocation(iteratorName, "hasNext", null), Operator.CONDITIONAL_AND, indexExpression)); //$NON-NLS-1$

			//if (i > 0) 
			//stringBuilder.append(iterator.next());
			MethodInvocation nextInvocation= createMethodInvocation(iteratorName, "next", null); //$NON-NLS-1$
			forBlock.statements().add(fAst.newExpressionStatement(createMethodInvocation(stringBuilderName, appendMethodName, nextInvocation)));

		}

		body.statements().add(forStatement);

		//stringBuilder.add("]");
		literal= fAst.newStringLiteral();
		literal.setLiteralValue("]"); //$NON-NLS-1$
		body.statements().add(fAst.newExpressionStatement(createMethodInvocation(stringBuilderName, appendMethodName, literal)));

		//return stringBuilder.toString();
		ReturnStatement returnStatement= fAst.newReturnStatement();
		returnStatement.setExpression(createMethodInvocation(stringBuilderName, "toString", null)); //$NON-NLS-1$
		body.statements().add(returnStatement);

		arrayToStringMethod.setProperty(OVERWRITE_METHOD_PROPERTY, Boolean.valueOf(array));

		return arrayToStringMethod;
	}


	/**
	 * This method initializes all variables used in the process of generating <code>toString</code>
	 * method.
	 */
	protected void initialize() {
		needMaxLenVariable= false;
		needCollectionToStringMethod= false;
		typesThatNeedArrayToStringMethod= new ArrayList<ITypeBinding>();

		checkNeedForHelperMethods();

		toStringMethod= fAst.newMethodDeclaration();
		toStringMethod.modifiers().addAll(ASTNodeFactory.newModifiers(fAst, Modifier.PUBLIC));
		toStringMethod.setName(fAst.newSimpleName(METHODNAME_TO_STRING));
		toStringMethod.setConstructor(false);
		toStringMethod.setReturnType2(fAst.newSimpleType(fAst.newName(TYPENAME_STRING)));

		Block body= fAst.newBlock();
		toStringMethod.setBody(body);
		
		fMaxLenVariableName= createNameSuggestion(MAX_LEN_VARIABLE_NAME, NamingConventions.VK_LOCAL);
	}

	/**
	 * This method is called at the end of the process of generating <code>toString</code> method.
	 * It should make sure the processed properly and clean the environment.
	 * 
	 * @throws CoreException if creation failed
	 */
	protected void complete() throws CoreException {
		if (needMaxLenVariable) {
			toStringMethod.getBody().statements().add(0, createMaxLenDeclaration());
		}
		createMethodComment();
		toStringMethod.setProperty(OVERWRITE_METHOD_PROPERTY, Boolean.valueOf(true));
	}

	/**
	 * Iterates over selected members to determine whether helper methods will be needed.
	 */
	protected void checkNeedForHelperMethods() {
		if ((!fContext.isLimitItems() && !fContext.isCustomArray()) || (fContext.isLimitItems() && fContext.getLimitItemsValue() == 0))
			return;

		boolean isNonPrimitive= false;
		for (int i= 0; i < fContext.getSelectedMembers().length; i++) {
			ITypeBinding memberType= getMemberType(fContext.getSelectedMembers()[i]);
			boolean[] implementsInterfaces= implementsInterfaces(memberType.getErasure(), new String[] { "java.util.Collection", "java.util.List", "java.util.Map" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			boolean isCollection= implementsInterfaces[0];
			boolean isList= implementsInterfaces[1];
			boolean isMap= implementsInterfaces[2];

			if (fContext.isLimitItems() && (isCollection || isMap) && !isList) {
				needCollectionToStringMethod= true;
			}
			if (fContext.isCustomArray() && memberType.isArray()) {
				ITypeBinding componentType= memberType.getComponentType();
				if (componentType.isPrimitive() && (!fContext.is50orHigher() || (!fContext.is60orHigher() && fContext.isLimitItems()))) {
					if (!typesThatNeedArrayToStringMethod.contains(componentType))
						typesThatNeedArrayToStringMethod.add(componentType);
				} else if (!componentType.isPrimitive())
					isNonPrimitive= true;
			}
		}
		if (!typesThatNeedArrayToStringMethod.isEmpty() && isNonPrimitive)
			typesThatNeedArrayToStringMethod.add(fAst.resolveWellKnownType("java.lang.Object")); //$NON-NLS-1$
	}

	/**
	 * 
	 * @param templateElement the template element, see constants in {@link ToStringTemplateParser}
	 * @param member the member
	 * @return <code>String</code> or <code>Expression</code> switching
	 */
	protected Object processElement(String templateElement, Object member) {
		Object result= templateElement;
		if (templateElement == ToStringTemplateParser.OBJECT_NAME_VARIABLE) {
			result= fContext.getTypeBinding().getName();
		}
		if (templateElement == ToStringTemplateParser.OBJECT_GET_NAME_VARIABLE) {
			//this.getClass().getName()
			MethodInvocation getClassInvocation= fAst.newMethodInvocation();
			if (fContext.isKeywordThis())
				getClassInvocation.setExpression(fAst.newThisExpression());
			getClassInvocation.setName(fAst.newSimpleName("getClass")); //$NON-NLS-1$
			MethodInvocation getNameInvocation= fAst.newMethodInvocation();
			getNameInvocation.setExpression(getClassInvocation);
			getNameInvocation.setName(fAst.newSimpleName("getName")); //$NON-NLS-1$
			result= getNameInvocation;
		}
		if (templateElement == ToStringTemplateParser.OBJECT_SUPER_TOSTRING_VARIABLE) {
			//super.toString()
			SuperMethodInvocation superToStringInvocation= fAst.newSuperMethodInvocation();
			superToStringInvocation.setName(fAst.newSimpleName(METHODNAME_TO_STRING));
			result= superToStringInvocation;
		}
		if (templateElement == ToStringTemplateParser.OBJECT_HASHCODE_VARIABLE) {
			//this.hashCode()
			MethodInvocation hashCodeInvocation= fAst.newMethodInvocation();
			if (fContext.isKeywordThis())
				hashCodeInvocation.setExpression(fAst.newThisExpression());
			hashCodeInvocation.setName(fAst.newSimpleName("hashCode")); //$NON-NLS-1$
			result= hashCodeInvocation;
		}
		if (templateElement == ToStringTemplateParser.OBJECT_SYSTEM_HASHCODE_VARIABLE) {
			//system.identityHashCode(this)
			result= createMethodInvocation(addImport("java.lang.System"), "identityHashCode", fAst.newThisExpression()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (templateElement == ToStringTemplateParser.MEMBER_NAME_VARIABLE || templateElement == ToStringTemplateParser.MEMBER_NAME_PARENTHESIS_VARIABLE) {
			result= getMemberName(member, templateElement);
		}
		if (templateElement == ToStringTemplateParser.MEMBER_VALUE_VARIABLE) {
			result= createMemberAccessExpression(member, false, fContext.isSkipNulls());
		}
		if (result instanceof StringLiteral)
			return ((StringLiteral)result).getLiteralValue();
		else
			return result;
	}

	/**
	 * Adds an element to the generated <code>toString</code> method. This method is called for
	 * every element of the format template.
	 * 
	 * @param element String or expression to be added (<code>IVariableBinding</code> or
	 *            <code>IMethodBinding</code>
	 */
	protected abstract void addElement(Object element);

	/**
	 * Adds a member to the <code>toString</code> method. This method is called for every member if
	 * "Skip null values" options is unchecked.
	 * 
	 * @param member a member to be added
	 * @param addSeparator true, if separator should be added after the member (i.e. this is not the
	 *            last member)
	 */
	protected void addMember(Object member, boolean addSeparator) {
		String[] stringArray= fContext.getTemplateParser().getBody();
		for (int i= 0; i < stringArray.length; i++) {
			addElement(processElement(stringArray[i], member));
		}
		if (addSeparator)
			addElement(fContext.getTemplateParser().getSeparator());
	}

	/**
	 * Adds a code checking if member's value is not null and adding it to the generated string.
	 * This method is called for every non-primitive type member if "Skip null values" options is
	 * checked, or for every <code>Collection</code> and <code>Map</code> member if there's a limit
	 * for number of elements.
	 * 
	 * @param member a member to be added
	 * @param addSeparator true, if separator should be added after the member (i.e. this is not the
	 *            last member)
	 */
	protected void addMemberCheckNull(Object member, boolean addSeparator) {
		addMember(member, addSeparator);
	}

	/**
	 * Creates an invocation of a method that takes zero or one argument
	 * 
	 * @param expression the receiver expression
	 * @param methodName the method name
	 * @param argument the argument, can be <code>null</code> if the method does not take any arguments
	 * @return MethodInvocation in following form: <code>expression.methodName(argument)</code>
	 */
	protected MethodInvocation createMethodInvocation(Expression expression, String methodName, Expression argument) {
		MethodInvocation invocation= fAst.newMethodInvocation();
		invocation.setExpression(expression);
		invocation.setName(fAst.newSimpleName(methodName));
		if (argument != null)
			invocation.arguments().add(argument);
		return invocation;
	}

	/**
	 * Creates an invocation of a method that takes zero or one argument
	 * 
	 * @param receiver the receiver name
	 * @param methodName the method name
	 * @param argument the argument, can be <code>null</code> if the method does not take any arguments
	 * @return MethodInvocation in following form: <code>expression.methodName(argument)</code>
	 */
	protected MethodInvocation createMethodInvocation(String receiver, String methodName, Expression argument) {
		return createMethodInvocation(fAst.newName(receiver), methodName, argument);
	}
	
	/**
	 * Creates a statement that can be used as for/while/if-then-else block
	 * 
	 * @param expression an expression
	 * @return a single-line statement, or a block, depending on settings
	 */
	protected Statement createOneStatementBlock(Expression expression) {
		if (fContext.isForceBlocks()) {
			Block forBlock= fAst.newBlock();
			forBlock.statements().add(fAst.newExpressionStatement(expression));
			return forBlock;
		} else {
			return fAst.newExpressionStatement(expression);
		}
	}

	protected InfixExpression createInfixExpression(Expression leftOperand, Operator operator, Expression rightOperand) {
		InfixExpression expression= fAst.newInfixExpression();
		expression.setLeftOperand(leftOperand);
		expression.setOperator(operator);
		expression.setRightOperand(rightOperand);
		return expression;
	}

	/**
	 * @return a statement in form of <code>final int maxLen = 10;</code>
	 */
	protected VariableDeclarationStatement createMaxLenDeclaration() {
		VariableDeclarationFragment fragment= fAst.newVariableDeclarationFragment();
		fragment.setName(fAst.newSimpleName(fMaxLenVariableName));
		fragment.setInitializer(fAst.newNumberLiteral(String.valueOf(fContext.getLimitItemsValue())));
		VariableDeclarationStatement declExpression= fAst.newVariableDeclarationStatement(fragment);
		declExpression.setType(fAst.newPrimitiveType(PrimitiveType.INT));
		declExpression.modifiers().add(fAst.newModifier(ModifierKeyword.FINAL_KEYWORD));
		return declExpression;
	}

	/**
	 * @param member <code>IVariableBinding</code> or <code>IMethodBinding</code> representing a
	 *            member
	 * @param ignoreArraysCollections if false and <i>limit number of items</i> is set, this method
	 *            will use custom methods to print out Arrays, Collections and Sets
	 * @param ignoreNulls if false, method will add checking for nulls when using custom methods
	 * @return an expression that accesses given member
	 */
	protected Expression createMemberAccessExpression(Object member, boolean ignoreArraysCollections, boolean ignoreNulls) {
		if (!ignoreArraysCollections) {
			ITypeBinding memberType= getMemberType(member);

			boolean isArray= memberType.isArray();

			boolean[] implementsInterfaces= implementsInterfaces(memberType.getErasure(), new String[] { "java.util.Collection", "java.util.List", "java.util.Map" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			boolean isCollection= implementsInterfaces[0];
			boolean isList= implementsInterfaces[1];
			boolean isMap= implementsInterfaces[2];

			if (isCollection || isMap || (isArray && fContext.isCustomArray())) {
				Expression accessExpression= null;
				if (fContext.isLimitItems()) {
					if (fContext.getLimitItemsValue() == 0) {
						accessExpression= fAst.newStringLiteral();
						((StringLiteral)accessExpression).setLiteralValue("[]"); //$NON-NLS-1$
					} else {
						if (isList && !needCollectionToStringMethod) {
							//member.subList(0, Math.min(maxLen, member.size())
							MethodInvocation memberSizeInvocation= fAst.newMethodInvocation();
							memberSizeInvocation.setExpression(createMemberAccessExpression(member, true, true));
							memberSizeInvocation.setName(fAst.newSimpleName("size")); //$NON-NLS-1$

							accessExpression= createSubListInvocation(createMemberAccessExpression(member, true, true), memberSizeInvocation);
							needMaxLenVariable= true;
						} else if (isCollection || isMap) {
							//toString(member, maxLen)
							Expression memberAccess= createMemberAccessExpression(member, true, true);
							if (isMap) {
								//member.entrySet();
								MethodInvocation entrySetInvocation= fAst.newMethodInvocation();
								entrySetInvocation.setExpression(memberAccess);
								entrySetInvocation.setName(fAst.newSimpleName("entrySet")); //$NON-NLS-1$
								memberAccess= entrySetInvocation;
							}
							MethodInvocation toStringInvocation= fAst.newMethodInvocation();
							if (fContext.isKeywordThis())
								toStringInvocation.setExpression(fAst.newThisExpression());
							toStringInvocation.setName(fAst.newSimpleName(HELPER_TOSTRING_METHOD_NAME));
							toStringInvocation.arguments().add(memberAccess);
							toStringInvocation.arguments().add(fAst.newSimpleName(fMaxLenVariableName));
							needMaxLenVariable= true;
							accessExpression= toStringInvocation;
						} else if (isArray) {
							FieldAccess lengthAccess= fAst.newFieldAccess();
							lengthAccess.setExpression(createMemberAccessExpression(member, true, true));
							lengthAccess.setName(fAst.newSimpleName("length")); //$NON-NLS-1$
							ITypeBinding arrayComponentType= memberType.getComponentType();
							if (!arrayComponentType.isPrimitive() && typesThatNeedArrayToStringMethod.isEmpty()) {
								//Arrays.asList(member).subList(0, Math.min(maxLen, member.length))
								MethodInvocation asListInvocation= createMethodInvocation(addImport("java.util.Arrays"), "asList", createMemberAccessExpression(member, true, true)); //$NON-NLS-1$ //$NON-NLS-2$
								accessExpression= createSubListInvocation(asListInvocation, lengthAccess);
							} else {
								if (fContext.is60orHigher()) {
									// Arrays.toString( Arrays.copyOf ( member, Math.min (maxLen, member.length) )
									Name arraysImport= addImport("java.util.Arrays"); //$NON-NLS-1$
									MethodInvocation minInvocation= createMethodInvocation(addImport("java.lang.Math"), "min", lengthAccess); //$NON-NLS-1$ //$NON-NLS-2$
									minInvocation.arguments().add(fAst.newSimpleName(fMaxLenVariableName));
									needMaxLenVariable= true;
									MethodInvocation copyOfInvocation= createMethodInvocation(arraysImport, "copyOf", createMemberAccessExpression(member, true, true)); //$NON-NLS-1$
									copyOfInvocation.arguments().add(minInvocation);
									Name arraysImportCopy= (Name)ASTNode.copySubtree(fAst, arraysImport);
									accessExpression= createMethodInvocation(arraysImportCopy, "toString", copyOfInvocation); //$NON-NLS-1$
								} else {
									// arrayToString(member, member.length, maxLen)
									MethodInvocation arrayToStringInvocation= fAst.newMethodInvocation();
									if (fContext.isKeywordThis())
										arrayToStringInvocation.setExpression(fAst.newThisExpression());
									arrayToStringInvocation.setName(fAst.newSimpleName(HELPER_ARRAYTOSTRING_METHOD_NAME));
									arrayToStringInvocation.arguments().add(createMemberAccessExpression(member, true, true));
									arrayToStringInvocation.arguments().add(lengthAccess);
									arrayToStringInvocation.arguments().add(fAst.newSimpleName(fMaxLenVariableName));
									needMaxLenVariable= true;
									accessExpression= arrayToStringInvocation;
								}
							}
						}
					}
				} else {
					if (isArray && fContext.isCustomArray()) {
						if (fContext.is50orHigher()) {
							// Arrays.toString(member)
							return createMethodInvocation(addImport("java.util.Arrays"), "toString", createMemberAccessExpression(member, true, true)); //$NON-NLS-1$ //$NON-NLS-2$
						} else {
							ITypeBinding arrayComponentType= memberType.getComponentType();
							if (!arrayComponentType.isPrimitive() && typesThatNeedArrayToStringMethod.isEmpty()) {
								// Arrays.asList(member)
								accessExpression= createMethodInvocation(addImport("java.util.Arrays"), "asList", createMemberAccessExpression(member, true, true)); //$NON-NLS-1$ //$NON-NLS-2$
							} else {
								// arrayToString(member, member.length, maxLen)
								FieldAccess lengthAccess= fAst.newFieldAccess();
								lengthAccess.setExpression(createMemberAccessExpression(member, true, true));
								lengthAccess.setName(fAst.newSimpleName("length")); //$NON-NLS-1$
								MethodInvocation arrayToStringInvocation= fAst.newMethodInvocation();
								if (fContext.isKeywordThis())
									arrayToStringInvocation.setExpression(fAst.newThisExpression());
								arrayToStringInvocation.setName(fAst.newSimpleName(HELPER_ARRAYTOSTRING_METHOD_NAME));
								arrayToStringInvocation.arguments().add(createMemberAccessExpression(member, true, true));
								arrayToStringInvocation.arguments().add(lengthAccess);
								accessExpression= arrayToStringInvocation;
							}
						}
					}
				}
				if (accessExpression != null) {
					if (!ignoreNulls) {
						ConditionalExpression conditional= fAst.newConditionalExpression();
						conditional.setExpression(createInfixExpression(createMemberAccessExpression(member, true, true), Operator.NOT_EQUALS, fAst.newNullLiteral()));
						conditional.setThenExpression(accessExpression);
						conditional.setElseExpression(fAst.newNullLiteral());
						return conditional;
					}
					return accessExpression;
				}
			}
		}
		if (member instanceof IVariableBinding) {
			if (fContext.isKeywordThis()) {
				FieldAccess fa= fAst.newFieldAccess();
				fa.setExpression(fAst.newThisExpression());
				fa.setName(fAst.newSimpleName(((IVariableBinding)member).getName()));
				return fa;
			}
			return fAst.newSimpleName(((IVariableBinding)member).getName());
		}
		if (member instanceof IMethodBinding) {
			if (((IMethodBinding)member).getName().equals(METHODNAME_TO_STRING)) {
				SuperMethodInvocation invocation= fAst.newSuperMethodInvocation();
				invocation.setName(fAst.newSimpleName(((IMethodBinding)member).getName()));
				return invocation;
			}
			MethodInvocation invocation= fAst.newMethodInvocation();
			if (fContext.isKeywordThis()) {
				invocation.setExpression(fAst.newThisExpression());
			}
			invocation.setName(fAst.newSimpleName(((IMethodBinding)member).getName()));
			return invocation;
		}
		return null;
	}

	protected Expression createSubListInvocation(Expression memberAccess, Expression sizeAccess) {
		MethodInvocation subListInvocation= fAst.newMethodInvocation();
		subListInvocation.setExpression(memberAccess);
		subListInvocation.setName(fAst.newSimpleName("subList")); //$NON-NLS-1$
		subListInvocation.arguments().add(fAst.newNumberLiteral(String.valueOf(0)));

		MethodInvocation minInvocation= createMethodInvocation(addImport("java.lang.Math"), "min", sizeAccess); //$NON-NLS-1$ //$NON-NLS-2$
		minInvocation.arguments().add(fAst.newSimpleName(fMaxLenVariableName));
		subListInvocation.arguments().add(minInvocation);
		needMaxLenVariable= true;
		return subListInvocation;
	}

	/**
	 * Adds an import to the class. This method should be used for every class reference added to
	 * the generated code.
	 * 
	 * @param typeName a fully qualified name of a type
	 * @return simple name of a class if the import was added and fully qualified name if there was
	 *         a conflict
	 */
	protected Name addImport(String typeName) {
		String importedName= fContext.getImportRewrite().addImport(typeName);
		return fAst.newName(importedName);
	}
	
	private Set<String> excluded;
	protected String createNameSuggestion(String baseName, int variableKind) {
		if (excluded == null) {
			excluded= new HashSet<String>();
			IVariableBinding[] fields= fContext.getTypeBinding().getDeclaredFields();
			for (int i= 0; i < fields.length; i++) {
				excluded.add(fields[i].getName());
			}
			ITypeBinding superType= fContext.getTypeBinding().getSuperclass();
			while (superType != null) {
				fields= superType.getDeclaredFields();
				for (int i= 0; i < fields.length; i++) {
					if (!Modifier.isPrivate(fields[i].getModifiers())) {
						excluded.add(fields[i].getName());
					}
				}
				superType= superType.getSuperclass();
			}
			ITypeBinding[] types= fContext.getTypeBinding().getDeclaredTypes();
			for (int i= 0; i < types.length; i++) {
				excluded.add(types[i].getName());
			}
			superType= fContext.getTypeBinding().getSuperclass();
			while (superType != null) {
				types= superType.getDeclaredTypes();
				for (int i= 0; i < types.length; i++) {
					if (!Modifier.isPrivate(types[i].getModifiers())) {
						excluded.add(types[i].getName());
					}
				}
				superType= superType.getSuperclass();
			}
		}
		return StubUtility.getVariableNameSuggestions(variableKind, fContext.getCompilationUnit().getJavaProject(), baseName, 0, excluded, true)[0];
	}

	/**
	 * Checks whether given type implements given interface
	 * 
	 * @param memberType binding of the type to check
	 * @param interfaceNames fully qualified names of the interfaces to seek for
	 * @return array of booleans, every element is set to true if interface at the same position in
	 *         <code>interfaceNames</code> is implemented by <code>memberType</code>
	 */
	protected boolean[] implementsInterfaces(ITypeBinding memberType, String[] interfaceNames) {
		boolean[] result= new boolean[interfaceNames.length];
		for (int i= 0; i < interfaceNames.length; i++) {
			if (memberType.getQualifiedName().equals(interfaceNames[i]))
				result[i]= true;
		}
		ITypeBinding[] interfaces= memberType.getInterfaces();
		for (int i= 0; i < interfaces.length; i++) {
			boolean[] deeper= implementsInterfaces(interfaces[i].getErasure(), interfaceNames);
			for (int j= 0; j < interfaceNames.length; j++) {
				result[j]= result[j] || deeper[j];
			}
		}
		return result;
	}

	/**
	 * 
	 * @param member <code>IVariableBinding</code> or <code>IMethodBinding</code> representing a
	 *            member
	 * @param templateElement the template element
	 * @return the name of the member, with parenthesis at the end if the member is a method
	 */
	protected String getMemberName(Object member, String templateElement) {
		if (member instanceof IVariableBinding) {
			return ((IVariableBinding)member).getName();
		}
		if (member instanceof IMethodBinding) {
			String result= ((IMethodBinding)member).getName();
			if (templateElement == ToStringTemplateParser.MEMBER_NAME_PARENTHESIS_VARIABLE)
				result+= "()"; //$NON-NLS-1$
			return result;
		}
		return null;
	}

	/**
	 * 
	 * @param member member to check
	 * @return type of field or method's return type
	 */
	protected ITypeBinding getMemberType(Object member) {
		if (member instanceof IVariableBinding) {
			return ((IVariableBinding)member).getType();
		}
		if (member instanceof IMethodBinding) {
			return ((IMethodBinding)member).getReturnType();
		}
		return null;
	}

	public void setContext(ToStringGenerationContext context) {
		fContext= context;
		fAst= fContext.getAST();
		excluded= null;
	}

	public ToStringGenerationContext getContext() {
		return fContext;
	}

}

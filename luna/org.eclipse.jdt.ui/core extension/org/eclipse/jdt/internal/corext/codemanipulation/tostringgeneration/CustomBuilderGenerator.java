/*******************************************************************************
 * Copyright (c) 2008, 2011 Mateusz Matela and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Mateusz Matela <mateusz.matela@gmail.com> - [code manipulation] [dcr] toString() builder wizard - https://bugs.eclipse.org/bugs/show_bug.cgi?id=26070
 *     Mateusz Matela <mateusz.matela@gmail.com> - [toString] finish toString() builder wizard - https://bugs.eclipse.org/bugs/show_bug.cgi?id=267710
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationMessages;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;


/**
 * <p>
 * Implementation of <code>AbstractToStringGenerator</code> that creates <code>toString()</code>
 * method using an external library. The library must supply a string builder, that is a class that
 * fulfills the following requirements:
 * <ul>
 * <li>Provides a constructor taking single Object</li>
 * <li>Provides methods for appending objects. There may be many such methods (with the same name,
 * for example <code>append(...)</code>), but there must be at least a version that takes single
 * Object or an Object and a String (in any order). These methods should return builder object,
 * otherwise generator will not be able to make call chains.</li>
 * <li>Provides a result method (usually <code>toString()</code>), that is a method that takes no
 * arguments and returns a String</li>
 * </ul>
 * </p>
 * <p>
 * Generated methods look like this:
 * 
 * <pre>
 * public String toString() {
 * 	ExternalBuilder builder= new ExternalBuilder();
 * 	builder.append(&quot;field1&quot;, field1);
 * 	builder.append(&quot;field2&quot;, field2);
 * 	return builder.toString();
 * }
 * </pre>
 * 
 * </p>
 * 
 * @since 3.5
 */
public class CustomBuilderGenerator extends AbstractToStringGenerator {

	private final List<String> primitiveTypes= Arrays.asList(new String[] { "byte", "short", "char", "int", "long", "float", "double", "boolean" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$

	private final String[] wrapperTypes= new String[] { "java.lang.Byte", "java.lang.Short", "java.lang.Character", "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
			"java.lang.Boolean" }; //$NON-NLS-1$

	/**
	 * true, if the last expression created with {@link #createAppendMethodForMember(Object)}
	 * returns builder type and therefore can be used to chain calls
	 **/
	private boolean canChainLastAppendCall;

	/**
	 * Class for storing information about versions of append method in the builder class that can
	 * be used for different member types
	 */
	private class AppendMethodInformation {
		/**
		 * Type of method with respect to taken parameter types. Possible values:
		 * <ol>
		 * <li>method takes one type parameter</li>
		 * <li>method takes one type parameter and one string</li>
		 * <li>method takes one string and one type parameter</li>
		 * </ol>
		 */
		public int methodType;

		/**
		 * true if method returns the builder class object so it can be used to form chains of calls
		 **/
		public boolean returnsBuilder;
	}

	/**
	 * Information about versions of append method in the builder type
	 * 
	 * key: String - fully qualified name of a member type
	 * 
	 * value: {@link AppendMethodInformation} - information about corresponding method
	 */
	private HashMap<String, AppendMethodInformation> appendMethodSpecificTypes= new HashMap<String, AppendMethodInformation>();

	@Override
	public RefactoringStatus checkConditions() {
		RefactoringStatus status= super.checkConditions();
		if (fContext.isCustomArray() || fContext.isLimitItems())
			status.addWarning(CodeGenerationMessages.GenerateToStringOperation_warning_no_arrays_collections_with_this_style);
		return status;
	}

	@Override
	protected void addElement(Object element) {
	}

	@Override
	protected void initialize() {
		super.initialize();

		fillAppendMethodsMap();

		tidyAppendsMethodsMap();
	}

	@Override
	public MethodDeclaration generateToStringMethod() throws CoreException {
		initialize();

		//ToStringBuilder builder= new ToStringBuilder(this);
		String builderVariableName= createNameSuggestion(getContext().getCustomBuilderVariableName(), NamingConventions.VK_LOCAL);
		VariableDeclarationFragment fragment= fAst.newVariableDeclarationFragment();
		fragment.setName(fAst.newSimpleName(builderVariableName));
		ClassInstanceCreation classInstance= fAst.newClassInstanceCreation();
		Name typeName= addImport(getContext().getCustomBuilderClass());
		classInstance.setType(fAst.newSimpleType(typeName));
		classInstance.arguments().add(fAst.newThisExpression());
		fragment.setInitializer(classInstance);
		VariableDeclarationStatement vStatement= fAst.newVariableDeclarationStatement(fragment);
		vStatement.setType(fAst.newSimpleType((Name)ASTNode.copySubtree(fAst, typeName)));
		toStringMethod.getBody().statements().add(vStatement);

		/* expression for accumulating chained calls */
		Expression expression= null;

		for (int i= 0; i < getContext().getSelectedMembers().length; i++) {
			//builder.append("member", member);
			MethodInvocation appendInvocation= createAppendMethodForMember(getContext().getSelectedMembers()[i]);
			if (getContext().isSkipNulls() && !getMemberType(getContext().getSelectedMembers()[i]).isPrimitive()) {
				if (expression != null) {
					toStringMethod.getBody().statements().add(fAst.newExpressionStatement(expression));
					expression= null;
				}
				appendInvocation.setExpression(fAst.newSimpleName(builderVariableName));
				IfStatement ifStatement= fAst.newIfStatement();
				ifStatement.setExpression(createInfixExpression(createMemberAccessExpression(getContext().getSelectedMembers()[i], true, true), Operator.NOT_EQUALS, fAst.newNullLiteral()));
				ifStatement.setThenStatement(createOneStatementBlock(appendInvocation));
				toStringMethod.getBody().statements().add(ifStatement);
			} else {
				if (expression != null) {
					appendInvocation.setExpression(expression);
				} else {
					appendInvocation.setExpression(fAst.newSimpleName(builderVariableName));
				}
				if (getContext().isCustomBuilderChainedCalls() && canChainLastAppendCall) {
					expression= appendInvocation;
				} else {
					expression= null;
					toStringMethod.getBody().statements().add(fAst.newExpressionStatement(appendInvocation));
				}
			}
		}

		if (expression != null) {
			toStringMethod.getBody().statements().add(fAst.newExpressionStatement(expression));
		}
		// return builder.toString();
		ReturnStatement rStatement= fAst.newReturnStatement();
		rStatement.setExpression(createMethodInvocation(builderVariableName, getContext().getCustomBuilderResultMethod(), null));
		toStringMethod.getBody().statements().add(rStatement);

		complete();

		return toStringMethod;
	}

	/**
	 * Searches through methods with proper name and for each argument type remembers the best
	 * option
	 */
	private void fillAppendMethodsMap() {
		try {
			IJavaProject javaProject= getContext().getTypeBinding().getJavaElement().getJavaProject();
			IType type= javaProject.findType(getContext().getCustomBuilderClass());
			IType[] types= type.newSupertypeHierarchy(null).getAllClasses();
			for (int i= 0; i < types.length; i++) {
				IMethod[] methods= types[i].getMethods();
				for (int j= 0; j < methods.length; j++) {
					if (!Flags.isPublic(methods[j].getFlags()) || !methods[j].getElementName().equals(getContext().getCustomBuilderAppendMethod()))
						continue;
					String[] parameterTypes= methods[j].getParameterTypes();
					AppendMethodInformation appendMethodInformation= new AppendMethodInformation();
					String specyficType;
					if (parameterTypes.length == 1) {
						specyficType= JavaModelUtil.getResolvedTypeName(parameterTypes[0], types[i]);
						appendMethodInformation.methodType= 1;
					} else if (parameterTypes.length == 2) {
						String resolvedParameterTypeName1= JavaModelUtil.getResolvedTypeName(parameterTypes[0], types[i]);
						String resolvedParameterTypeName2= JavaModelUtil.getResolvedTypeName(parameterTypes[1], types[i]);
						if (resolvedParameterTypeName1.equals("java.lang.String")) {//$NON-NLS-1$
							specyficType= resolvedParameterTypeName2;
							appendMethodInformation.methodType= 3;
						} else if (resolvedParameterTypeName2.equals("java.lang.String")) {//$NON-NLS-1$
							specyficType= resolvedParameterTypeName1;
							appendMethodInformation.methodType= 2;
						} else
							continue;
					} else
						continue;

					String returnTypeName= JavaModelUtil.getResolvedTypeName(methods[j].getReturnType(), types[i]);
					IType returnType= javaProject.findType(returnTypeName);
					appendMethodInformation.returnsBuilder= (returnType != null) && returnType.newSupertypeHierarchy(null).contains(type);

					AppendMethodInformation oldAMI= appendMethodSpecificTypes.get(specyficType);
					if (oldAMI == null || oldAMI.methodType < appendMethodInformation.methodType) {
						appendMethodSpecificTypes.put(specyficType, appendMethodInformation);
					}
				}
			}
		} catch (JavaModelException e) {
			throw new RuntimeException("couldn't initialize custom toString() builder generator", e); //$NON-NLS-1$
		}
	}

	/**
	 * Removes information about types from {@link #appendMethodSpecificTypes} if their
	 * parametersType is worse than for java.lang.Object.
	 */
	private void tidyAppendsMethodsMap() {
		int objectParametersType= appendMethodSpecificTypes.get("java.lang.Object").methodType; //$NON-NLS-1$
		Set<Map.Entry<String, AppendMethodInformation>> entrySet= appendMethodSpecificTypes.entrySet();
		for (Iterator<Map.Entry<String, AppendMethodInformation>> iterator= entrySet.iterator(); iterator.hasNext();) {
			Map.Entry<String, AppendMethodInformation> entry= iterator.next();
			if (entry.getValue().methodType < objectParametersType) {
				iterator.remove();
			}
		}
	}

	private MethodInvocation createAppendMethodForMember(Object member) {
		ITypeBinding memberType= getMemberType(member);
		String memberTypeName= memberType.getQualifiedName();

		Expression memberAccessExpression= null;

		AppendMethodInformation ami= appendMethodSpecificTypes.get(memberTypeName);
		if (ami == null && memberType.isPrimitive()) {
			memberTypeName= wrapperTypes[primitiveTypes.indexOf(memberTypeName)];
			memberType= fAst.resolveWellKnownType(memberTypeName);
			ami= appendMethodSpecificTypes.get(memberTypeName);
			if (!getContext().is50orHigher()) {
				ClassInstanceCreation classInstance= fAst.newClassInstanceCreation();
				classInstance.setType(fAst.newSimpleType(addImport(memberTypeName)));
				classInstance.arguments().add(createMemberAccessExpression(member, true, true));
				memberAccessExpression= classInstance;
			}
		}
		while (ami == null) {
			memberType= memberType.getSuperclass();
			if (memberType != null)
				memberTypeName= memberType.getQualifiedName();
			else
				memberTypeName= "java.lang.Object"; //$NON-NLS-1$
			ami= appendMethodSpecificTypes.get(memberTypeName);
		}

		if (memberAccessExpression == null) {
			memberAccessExpression= createMemberAccessExpression(member, false, getContext().isSkipNulls());
		}

		MethodInvocation appendInvocation= fAst.newMethodInvocation();
		appendInvocation.setName(fAst.newSimpleName(getContext().getCustomBuilderAppendMethod()));
		if (ami.methodType == 1 || ami.methodType == 2) {
			appendInvocation.arguments().add(memberAccessExpression);
		}
		if (ami.methodType == 2 || ami.methodType == 3) {
			StringLiteral literal= fAst.newStringLiteral();
			literal.setLiteralValue(getMemberName(member, ToStringTemplateParser.MEMBER_NAME_PARENTHESIS_VARIABLE));
			appendInvocation.arguments().add(literal);
		}
		if (ami.methodType == 3) {
			appendInvocation.arguments().add(memberAccessExpression);
		}

		canChainLastAppendCall= ami.returnsBuilder;

		return appendInvocation;
	}
}

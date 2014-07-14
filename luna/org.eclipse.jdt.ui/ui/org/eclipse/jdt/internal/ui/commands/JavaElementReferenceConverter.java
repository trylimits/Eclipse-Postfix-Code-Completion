/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.ui.commands;

import org.eclipse.core.commands.AbstractParameterValueConverter;
import org.eclipse.core.commands.ParameterValueConversionException;

import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

/**
 * A command parameter value converter to convert between Java elements and
 * String references that identify them.
 * <p>
 * References can be made to Java types, methods and fields. The reference
 * identifies the project to use as a search scope as well as the java element
 * information. Note that non-source elements may be referenced (such as
 * java.lang.Object), but they must be resolved within the scope of some
 * project.
 * </p>
 * <p>
 * References take the form:
 *
 * <pre>
 *        elementRef := typeRef | fieldRef | methodRef
 *        typeRef := projectName '/' fullyQualifiedTypeName
 *        fieldRef := typeRef '#' fieldName
 *        methodRef := typeRef '#' methodName '(' parameterSignatures ')'
 * </pre>
 *
 * where <code>parameterSignatures</code> uses the signature format documented
 * in the {@link org.eclipse.jdt.core.Signature Signature} class.
 * </p>
 *
 * @since 3.2
 */
public class JavaElementReferenceConverter extends AbstractParameterValueConverter {

	private static final char PROJECT_END_CHAR= '/';

	private static final char TYPE_END_CHAR= '#';

	private static final char PARAM_START_CHAR= Signature.C_PARAM_START;

	private static final char PARAM_END_CHAR= Signature.C_PARAM_END;

	@Override
	public Object convertToObject(String parameterValue) throws ParameterValueConversionException {

		assertWellFormed(parameterValue != null);

		final int projectEndPosition= parameterValue.indexOf(PROJECT_END_CHAR);
		assertWellFormed(projectEndPosition != -1);

		String projectName= parameterValue.substring(0, projectEndPosition);
		String javaElementRef= parameterValue.substring(projectEndPosition + 1);

		IJavaModel javaModel= JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
		assertExists(javaModel);

		IJavaProject javaProject= javaModel.getJavaProject(projectName);
		assertExists(javaProject);

		final int typeEndPosition= javaElementRef.indexOf(TYPE_END_CHAR);
		String typeName;
		if (typeEndPosition == -1) {
			typeName= javaElementRef;
		} else {
			typeName= javaElementRef.substring(0, typeEndPosition);
		}

		IType type= null;
		try {
			type= javaProject.findType(typeName);
		} catch (JavaModelException ex) {
			// type == null
		}
		assertExists(type);

		if (typeEndPosition == -1) {
			return type;
		}

		String memberRef= javaElementRef.substring(typeEndPosition + 1);

		final int paramStartPosition= memberRef.indexOf(PARAM_START_CHAR);
		if (paramStartPosition == -1) {
			IField field= type.getField(memberRef);
			assertExists(field);
			return field;
		}
		String methodName= memberRef.substring(0, paramStartPosition);
		String signature= memberRef.substring(paramStartPosition);
		String[] parameterTypes= null;
		try {
			parameterTypes= Signature.getParameterTypes(signature);
		} catch (IllegalArgumentException ex) {
			// parameterTypes == null
		}
		assertWellFormed(parameterTypes != null);
		IMethod method= type.getMethod(methodName, parameterTypes);
		assertExists(method);
		return method;
	}

	/**
	 * Throws a <code>ParameterValueConversionException</code> if the java
	 * element reference string does not meet some well-formedness condition.
	 *
	 * @param assertion
	 *            a boolean check for well-formedness
	 * @throws ParameterValueConversionException
	 */
	private void assertWellFormed(boolean assertion) throws ParameterValueConversionException {
		if (!assertion) {
			throw new ParameterValueConversionException("Malformed parameterValue"); //$NON-NLS-1$
		}
	}

	/**
	 * Throws a <code>ParameterValueConversionException</code> if the java
	 * element reference string identifies an element that does not exist.
	 *
	 * @param javaElement
	 *            an element to check for existence
	 * @throws ParameterValueConversionException
	 */
	private void assertExists(IJavaElement javaElement) throws ParameterValueConversionException {
		if ((javaElement == null) || (!javaElement.exists())) {
			throw new ParameterValueConversionException("parameterValue must reference an existing IJavaElement"); //$NON-NLS-1$
		}
	}

	@Override
	public String convertToString(Object parameterValue) throws ParameterValueConversionException {

		if (!(parameterValue instanceof IJavaElement)) {
			throw new ParameterValueConversionException("parameterValue must be an IJavaElement"); //$NON-NLS-1$
		}

		IJavaElement javaElement= (IJavaElement) parameterValue;

		IJavaProject javaProject= javaElement.getJavaProject();
		if (javaProject == null) {
			throw new ParameterValueConversionException("Could not get IJavaProject for element"); //$NON-NLS-1$
		}

		StringBuffer buffer;

		if (javaElement instanceof IType) {
			IType type= (IType) javaElement;
			buffer= composeTypeReference(type);
		} else
			if (javaElement instanceof IMethod) {
				IMethod method= (IMethod) javaElement;
				buffer= composeTypeReference(method.getDeclaringType());
				buffer.append(TYPE_END_CHAR);
				buffer.append(method.getElementName());
				String[] parameterTypes= method.getParameterTypes();
				buffer.append(PARAM_START_CHAR);
				for (int i= 0; i < parameterTypes.length; i++) {
					buffer.append(parameterTypes[i]);
				}
				buffer.append(PARAM_END_CHAR);
			} else
				if (javaElement instanceof IField) {
					IField field= (IField) javaElement;
					buffer= composeTypeReference(field.getDeclaringType());
					buffer.append(TYPE_END_CHAR);
					buffer.append(field.getElementName());
				} else {
					throw new ParameterValueConversionException("Unsupported IJavaElement type"); //$NON-NLS-1$
				}

		return buffer.toString();
	}

	private StringBuffer composeTypeReference(IType type) {
		StringBuffer buffer= new StringBuffer();
		buffer.append(type.getJavaProject().getElementName());
		buffer.append(PROJECT_END_CHAR);
		buffer.append(type.getFullyQualifiedName());
		return buffer;
	}

}

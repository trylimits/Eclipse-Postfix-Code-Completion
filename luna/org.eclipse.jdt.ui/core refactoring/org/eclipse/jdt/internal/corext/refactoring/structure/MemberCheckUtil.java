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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

class MemberCheckUtil {

	private MemberCheckUtil(){
		//static only
	}

	public static RefactoringStatus checkMembersInDestinationType(IMember[] members, IType destinationType) throws JavaModelException {
		RefactoringStatus result= new RefactoringStatus();
		for (int i= 0; i < members.length; i++) {
			if (members[i].getElementType() == IJavaElement.METHOD)
				checkMethodInType(destinationType, result, (IMethod)members[i]);
			else if (members[i].getElementType() == IJavaElement.FIELD)
				checkFieldInType(destinationType, result, (IField)members[i]);
			else if (members[i].getElementType() == IJavaElement.TYPE)
				checkTypeInType(destinationType, result, (IType)members[i]);
		}
		return result;
	}

	private static void checkMethodInType(IType destinationType, RefactoringStatus result, IMethod method) throws JavaModelException {
		IMethod[] destinationTypeMethods= destinationType.getMethods();
		IMethod found= findMethod(method, destinationTypeMethods);
		if (found != null){
			RefactoringStatusContext context= JavaStatusContext.create(destinationType.getCompilationUnit(), found.getSourceRange());
			String message= Messages.format(RefactoringCoreMessages.MemberCheckUtil_signature_exists,
					new String[]{ BasicElementLabels.getJavaElementName(method.getElementName()), getQualifiedLabel(destinationType)});
			result.addError(message, context);
		} else {
			IMethod similar= Checks.findMethod(method, destinationType);
			if (similar != null){
				String message= Messages.format(RefactoringCoreMessages.MemberCheckUtil_same_param_count,
						 new String[]{ BasicElementLabels.getJavaElementName(method.getElementName()), getQualifiedLabel(destinationType)});
				RefactoringStatusContext context= JavaStatusContext.create(destinationType.getCompilationUnit(), similar.getSourceRange());
				result.addWarning(message, context);
			}
		}
	}

	private static void checkFieldInType(IType destinationType, RefactoringStatus result, IField field) throws JavaModelException {
		IField destinationTypeField= destinationType.getField(field.getElementName());
		if (! destinationTypeField.exists())
			return;
		String message= Messages.format(RefactoringCoreMessages.MemberCheckUtil_field_exists,
				new String[]{ BasicElementLabels.getJavaElementName(field.getElementName()), getQualifiedLabel(destinationType)});
		RefactoringStatusContext context= JavaStatusContext.create(destinationType.getCompilationUnit(), destinationTypeField.getSourceRange());
		result.addError(message, context);
	}

	private static void checkTypeInType(IType destinationType, RefactoringStatus result, IType type) throws JavaModelException {
		String typeName= type.getElementName();
		IType destinationTypeType= destinationType.getType(typeName);
		if (destinationTypeType.exists()){
			String message= Messages.format(RefactoringCoreMessages.MemberCheckUtil_type_name_conflict0,
					new String[]{ BasicElementLabels.getJavaElementName(typeName), getQualifiedLabel(destinationType)});
			RefactoringStatusContext context= JavaStatusContext.create(destinationType.getCompilationUnit(), destinationTypeType.getNameRange());
			result.addError(message, context);
		} else {
			//need to check the hierarchy of enclosing and enclosed types
			if (destinationType.getElementName().equals(typeName)){
				String message= Messages.format(RefactoringCoreMessages.MemberCheckUtil_type_name_conflict1, getQualifiedLabel(type));
				RefactoringStatusContext context= JavaStatusContext.create(destinationType.getCompilationUnit(), destinationType.getNameRange());
				result.addError(message, context);
			}
			if (typeNameExistsInEnclosingTypeChain(destinationType, typeName)){
				String message= Messages.format(RefactoringCoreMessages.MemberCheckUtil_type_name_conflict2, getQualifiedLabel(type));
				RefactoringStatusContext context= JavaStatusContext.create(destinationType.getCompilationUnit(), destinationType.getNameRange());
				result.addError(message, context);
			}
			checkHierarchyOfEnclosedTypes(destinationType, result, type);
		}
	}

	private static void checkHierarchyOfEnclosedTypes(IType destinationType, RefactoringStatus result, IType type) throws JavaModelException {
		IType[] enclosedTypes= getAllEnclosedTypes(type);
		for (int i= 0; i < enclosedTypes.length; i++) {
			IType enclosedType= enclosedTypes[i];
			if (destinationType.getElementName().equals(enclosedType.getElementName())){
				String message= Messages.format(RefactoringCoreMessages.MemberCheckUtil_type_name_conflict3,
						new String[] { getQualifiedLabel(enclosedType), getQualifiedLabel(type)});
				RefactoringStatusContext context= JavaStatusContext.create(destinationType.getCompilationUnit(), destinationType.getNameRange());
				result.addError(message, context);
			}
			if (typeNameExistsInEnclosingTypeChain(destinationType, enclosedType.getElementName())){
				String message= Messages.format(RefactoringCoreMessages.MemberCheckUtil_type_name_conflict4,
						new String[] { getQualifiedLabel(enclosedType), getQualifiedLabel(type)});
				RefactoringStatusContext context= JavaStatusContext.create(destinationType.getCompilationUnit(), destinationType.getNameRange());
				result.addError(message, context);
			}
		}
	}

	private static String getQualifiedLabel(IType enclosedType) {
		return BasicElementLabels.getJavaElementName(enclosedType.getFullyQualifiedName('.'));
	}

	private static IType[] getAllEnclosedTypes(IType type) throws JavaModelException {
		List<IType> result= new ArrayList<IType>(2);
		IType[] directlyEnclosed= type.getTypes();
		result.addAll(Arrays.asList(directlyEnclosed));
		for (int i= 0; i < directlyEnclosed.length; i++) {
			IType enclosedType= directlyEnclosed[i];
			result.addAll(Arrays.asList(getAllEnclosedTypes(enclosedType)));
		}
		return result.toArray(new IType[result.size()]);
	}

	private static boolean typeNameExistsInEnclosingTypeChain(IType type, String typeName){
		IType enclosing= type.getDeclaringType();
		while (enclosing != null){
			if (enclosing.getElementName().equals(typeName))
				return true;
			enclosing= enclosing.getDeclaringType();
		}
		return false;
	}

	/**
	 * Finds a method in a list of methods. Compares methods by signature
	 * (only SimpleNames of types), and not by the declaring type.
	 * @param method the method to find
	 * @param allMethods the methods to look at
	 * @return The found method or <code>null</code>, if nothing found
	 * @throws JavaModelException
	 */
	public static IMethod findMethod(IMethod method, IMethod[] allMethods) throws JavaModelException {
		String name= method.getElementName();
		String[] paramTypes= method.getParameterTypes();
		boolean isConstructor= method.isConstructor();

		for (int i= 0; i < allMethods.length; i++) {
			if (JavaModelUtil.isSameMethodSignature(name, paramTypes, isConstructor, allMethods[i]))
				return allMethods[i];
		}
		return null;
	}
}

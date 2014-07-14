/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Andre Soereng <andreis@fast.no> - [syntax highlighting] highlight numbers - https://bugs.eclipse.org/bugs/show_bug.cgi?id=63573
 *     Björn Michael <b.michael@gmx.de> - [syntax highlighting] Syntax coloring for abstract classes - https://bugs.eclipse.org/331311
 *     Björn Michael <b.michael@gmx.de> - [syntax highlighting] Add highlight for inherited fields - https://bugs.eclipse.org/348368
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.javaeditor;

import org.eclipse.swt.graphics.RGB;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;

import org.eclipse.jdt.ui.PreferenceConstants;

/**
 * Semantic highlightings
 *
 * @since 3.0
 */
public class SemanticHighlightings {

	/**
	 * A named preference part that controls the highlighting of static final fields.
	 */
	public static final String STATIC_FINAL_FIELD="staticFinalField"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of static fields.
	 */
	public static final String STATIC_FIELD="staticField"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of fields.
	 */
	public static final String FIELD="field"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of method declarations.
	 */
	public static final String METHOD_DECLARATION="methodDeclarationName"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of static method invocations.
	 */
	public static final String STATIC_METHOD_INVOCATION="staticMethodInvocation"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of inherited method invocations.
	 */
	public static final String INHERITED_METHOD_INVOCATION="inheritedMethodInvocation"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of annotation element references.
	 * @since 3.1
	 */
	public static final String ANNOTATION_ELEMENT_REFERENCE="annotationElementReference"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of abstract method invocations.
	 */
	public static final String ABSTRACT_METHOD_INVOCATION="abstractMethodInvocation"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of local variables.
	 */
	public static final String LOCAL_VARIABLE_DECLARATION="localVariableDeclaration"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of local variables.
	 */
	public static final String LOCAL_VARIABLE="localVariable"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of parameter variables.
	 */
	public static final String PARAMETER_VARIABLE="parameterVariable"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of deprecated members.
	 */
	public static final String DEPRECATED_MEMBER="deprecatedMember"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of type parameters.
	 * @since 3.1
	 */
	public static final String TYPE_VARIABLE="typeParameter"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of methods
	 * (invocations and declarations).
	 *
	 * @since 3.1
	 */
	public static final String METHOD="method"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of auto(un)boxed
	 * expressions.
	 *
	 * @since 3.1
	 */
	public static final String AUTOBOXING="autoboxing"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of classes.
	 *
	 * @since 3.2
	 */
	public static final String CLASS="class"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of enums.
	 *
	 * @since 3.2
	 */
	public static final String ENUM="enum"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of interfaces.
	 *
	 * @since 3.2
	 */
	public static final String INTERFACE="interface"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of annotations.
	 *
	 * @since 3.2
	 */
	public static final String ANNOTATION="annotation"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of type arguments.
	 *
	 * @since 3.2
	 */
	public static final String TYPE_ARGUMENT="typeArgument"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of numbers.
	 *
	 * @since 3.4
	 */
	public static final String NUMBER="number"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of abstract classes.
	 *
	 * @since 3.7
	 */
	public static final String ABSTRACT_CLASS="abstractClass"; //$NON-NLS-1$

	/**
	 * A named preference part that controls the highlighting of inherited fields.
	 *
	 * @since 3.8
	 */
	public static final String INHERITED_FIELD="inheritedField"; //$NON-NLS-1$

	/**
	 * Semantic highlightings
	 */
	private static SemanticHighlighting[] fgSemanticHighlightings;

	/**
	 * Semantic highlighting for static final fields.
	 */
	private static final class StaticFinalFieldHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return STATIC_FINAL_FIELD;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(0, 0, 192);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_staticFinalField;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			IBinding binding= token.getBinding();
			return binding != null && binding.getKind() == IBinding.VARIABLE && ((IVariableBinding)binding).isField() && (binding.getModifiers() & (Modifier.FINAL | Modifier.STATIC)) == (Modifier.FINAL | Modifier.STATIC);
		}
	}

	/**
	 * Semantic highlighting for static fields.
	 */
	private static final class StaticFieldHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return STATIC_FIELD;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(0, 0, 192);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_staticField;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			IBinding binding= token.getBinding();
			return binding != null && binding.getKind() == IBinding.VARIABLE && ((IVariableBinding)binding).isField() && (binding.getModifiers() & Modifier.STATIC) == Modifier.STATIC;
		}
	}

	/**
	 * Semantic highlighting for fields.
	 */
	private static final class FieldHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return FIELD;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(0, 0, 192);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_field;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			IBinding binding= token.getBinding();
			return binding != null && binding.getKind() == IBinding.VARIABLE && ((IVariableBinding)binding).isField();
		}
	}

	/**
	 * Semantic highlighting for auto(un)boxed expressions.
	 */
	private static final class AutoboxHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return AUTOBOXING;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(171, 48, 0);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_autoboxing;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumesLiteral(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumesLiteral(SemanticToken token) {
			return isAutoUnBoxing(token.getLiteral());
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			return isAutoUnBoxing(token.getNode());
		}

		private boolean isAutoUnBoxing(Expression node) {
			if (isAutoUnBoxingExpression(node))
				return true;
			// special cases: the autoboxing conversions happens at a
			// location that is not mapped directly to a simple name
			// or a literal, but can still be mapped somehow
			// A) expressions
			StructuralPropertyDescriptor desc= node.getLocationInParent();
			if (desc == ArrayAccess.ARRAY_PROPERTY
					|| desc == InfixExpression.LEFT_OPERAND_PROPERTY
					|| desc == InfixExpression.RIGHT_OPERAND_PROPERTY
					|| desc == ConditionalExpression.THEN_EXPRESSION_PROPERTY
					|| desc == PrefixExpression.OPERAND_PROPERTY
					|| desc == CastExpression.EXPRESSION_PROPERTY
					|| desc == ConditionalExpression.ELSE_EXPRESSION_PROPERTY) {
				ASTNode parent= node.getParent();
				if (parent instanceof Expression)
					return isAutoUnBoxingExpression((Expression) parent);
			}
			// B) constructor invocations
			if (desc == QualifiedName.NAME_PROPERTY) {
				node= (Expression) node.getParent();
				desc= node.getLocationInParent();
			}
			if (desc == SimpleType.NAME_PROPERTY || desc == NameQualifiedType.NAME_PROPERTY) {
				ASTNode parent= node.getParent();
				if (parent != null && parent.getLocationInParent() == ClassInstanceCreation.TYPE_PROPERTY) {
					parent= parent.getParent();
					return isAutoUnBoxingExpression((ClassInstanceCreation) parent);
				}
			}
			return false;
		}

		private boolean isAutoUnBoxingExpression(Expression expression) {
			return expression.resolveBoxing() || expression.resolveUnboxing();
		}
	}

	/**
	 * Semantic highlighting for method declarations.
	 */
	private static final class MethodDeclarationHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return METHOD_DECLARATION;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(0, 0, 0);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_methodDeclaration;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#isMatched(org.eclipse.jdt.core.dom.ASTNode)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			StructuralPropertyDescriptor location= token.getNode().getLocationInParent();
			return location == MethodDeclaration.NAME_PROPERTY || location == AnnotationTypeMemberDeclaration.NAME_PROPERTY;
		}
	}

	/**
	 * Semantic highlighting for static method invocations.
	 */
	private static final class StaticMethodInvocationHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return STATIC_METHOD_INVOCATION;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(0, 0, 0);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_staticMethodInvocation;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#isMatched(org.eclipse.jdt.core.dom.ASTNode)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			SimpleName node= token.getNode();
			if (node.isDeclaration())
				return false;

			IBinding binding= token.getBinding();
			return binding != null && binding.getKind() == IBinding.METHOD && (binding.getModifiers() & Modifier.STATIC) == Modifier.STATIC;
		}
	}

	/**
	 * Semantic highlighting for annotation element references.
	 * @since 3.1
	 */
	private static final class AnnotationElementReferenceHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return ANNOTATION_ELEMENT_REFERENCE;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(0, 0, 0);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_annotationElementReference;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#isMatched(org.eclipse.jdt.core.dom.ASTNode)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			SimpleName node= token.getNode();
			if (node.getParent() instanceof MemberValuePair) {
				IBinding binding= token.getBinding();
				boolean isAnnotationElement= binding != null && binding.getKind() == IBinding.METHOD;

				return isAnnotationElement;
			}

			return false;
		}
	}

	/**
	 * Semantic highlighting for abstract method invocations.
	 */
	private static final class AbstractMethodInvocationHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return ABSTRACT_METHOD_INVOCATION;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(0, 0, 0);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_abstractMethodInvocation;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#isMatched(org.eclipse.jdt.core.dom.ASTNode)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			SimpleName node= token.getNode();
			if (node.isDeclaration())
				return false;

			IBinding binding= token.getBinding();
			boolean isAbstractMethod= binding != null && binding.getKind() == IBinding.METHOD && (binding.getModifiers() & Modifier.ABSTRACT) == Modifier.ABSTRACT;
			if (!isAbstractMethod)
				return false;

			// filter out annotation value references
			if (binding != null) {
				ITypeBinding declaringType= ((IMethodBinding)binding).getDeclaringClass();
				if (declaringType.isAnnotation())
					return false;
			}

			return true;
		}
	}

	/**
	 * Semantic highlighting for inherited method invocations.
	 */
	private static final class InheritedMethodInvocationHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return INHERITED_METHOD_INVOCATION;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(0, 0, 0);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_inheritedMethodInvocation;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#isMatched(org.eclipse.jdt.core.dom.ASTNode)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			SimpleName node= token.getNode();
			if (node.isDeclaration())
				return false;

			IBinding binding= token.getBinding();
			if (binding == null || binding.getKind() != IBinding.METHOD)
				return false;

			ITypeBinding currentType= Bindings.getBindingOfParentType(node);
			ITypeBinding declaringType= ((IMethodBinding) binding).getDeclaringClass();
			if (currentType == declaringType || currentType == null)
				return false;

			return Bindings.isSuperType(declaringType, currentType);
		}
	}

	/**
	 * Semantic highlighting for inherited method invocations.
	 */
	private static final class MethodHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return METHOD;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(0, 0, 0);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_method;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#isMatched(org.eclipse.jdt.core.dom.ASTNode)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			IBinding binding= getBinding(token);
			return binding != null && binding.getKind() == IBinding.METHOD;
		}
	}

	/**
	 * Semantic highlighting for local variable declarations.
	 */
	private static final class LocalVariableDeclarationHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return LOCAL_VARIABLE_DECLARATION;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(106, 62, 62);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_localVariableDeclaration;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			SimpleName node= token.getNode();
			StructuralPropertyDescriptor location= node.getLocationInParent();
			if (location == VariableDeclarationFragment.NAME_PROPERTY || location == SingleVariableDeclaration.NAME_PROPERTY) {
				ASTNode parent= node.getParent();
				if (parent instanceof VariableDeclaration) {
					parent= parent.getParent();
					return parent == null || !(parent instanceof FieldDeclaration);
				}
			}
			return false;
		}
	}

	/**
	 * Semantic highlighting for local variables.
	 */
	private static final class LocalVariableHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return LOCAL_VARIABLE;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(106, 62, 62);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_localVariable;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			IBinding binding= token.getBinding();
			if (binding != null && binding.getKind() == IBinding.VARIABLE && !((IVariableBinding) binding).isField()) {
				ASTNode decl= token.getRoot().findDeclaringNode(binding);
				return decl instanceof VariableDeclaration;
			}
			return false;
		}
	}

	/**
	 * Semantic highlighting for parameter variables.
	 */
	private static final class ParameterVariableHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return PARAMETER_VARIABLE;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(106, 62, 62);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_parameterVariable;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			IBinding binding= token.getBinding();
			if (binding != null && binding.getKind() == IBinding.VARIABLE && !((IVariableBinding) binding).isField()) {
				ASTNode decl= token.getRoot().findDeclaringNode(binding);
				return decl != null && decl.getLocationInParent() == MethodDeclaration.PARAMETERS_PROPERTY;
			}
			return false;
		}
	}

	/**
	 * Semantic highlighting for deprecated members.
	 */
	static final class DeprecatedMemberHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return DEPRECATED_MEMBER;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(0, 0, 0);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isStrikethroughByDefault()
		 * @since 3.1
		 */
		@Override
		public boolean isStrikethroughByDefault() {
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_deprecatedMember;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			IBinding binding= getBinding(token);
			if (binding != null) {
				if (binding.isDeprecated())
					return true;
				if (binding instanceof IMethodBinding) {
					IMethodBinding methodBinding= (IMethodBinding) binding;
					if (methodBinding.isConstructor() && methodBinding.getJavaElement() == null) {
						ITypeBinding declaringClass= methodBinding.getDeclaringClass();
						if (declaringClass.isAnonymous()) {
							ITypeBinding[] interfaces= declaringClass.getInterfaces();
							if (interfaces.length > 0)
								return interfaces[0].isDeprecated();
							else
								return declaringClass.getSuperclass().isDeprecated();
						}
						return declaringClass.isDeprecated();
					}
				}
			}
			return false;
		}
	}

	/**
	 * Semantic highlighting for type variables.
	 * @since 3.1
	 */
	private static final class TypeVariableHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return TYPE_VARIABLE;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(100, 70, 50);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_typeVariables;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {

			// 1: match types in type parameter lists
			SimpleName name= token.getNode();
			ASTNode node= name.getParent();
			if (node.getNodeType() != ASTNode.SIMPLE_TYPE && node.getNodeType() != ASTNode.TYPE_PARAMETER)
				return false;

			// 2: match generic type variable references
			IBinding binding= token.getBinding();
			return binding instanceof ITypeBinding && ((ITypeBinding) binding).isTypeVariable();
		}
	}

	/**
	 * Semantic highlighting for classes.
	 * @since 3.2
	 */
	private static final class ClassHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return CLASS;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(0, 80, 50);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_classes;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {

			// 1: match types
			SimpleName name= token.getNode();
			ASTNode node= name.getParent();
			int nodeType= node.getNodeType();
			if (nodeType != ASTNode.SIMPLE_TYPE && nodeType != ASTNode.THIS_EXPRESSION && nodeType != ASTNode.QUALIFIED_TYPE  && nodeType != ASTNode.QUALIFIED_NAME && nodeType != ASTNode.TYPE_DECLARATION && nodeType != ASTNode.METHOD_INVOCATION)
				return false;
			while (nodeType == ASTNode.QUALIFIED_NAME) {
				node= node.getParent();
				nodeType= node.getNodeType();
				if (nodeType == ASTNode.IMPORT_DECLARATION)
					return false;
			}

			// 2: match classes
			IBinding binding= token.getBinding();
			return binding instanceof ITypeBinding && ((ITypeBinding) binding).isClass();
		}
	}

	/**
	 * Semantic highlighting for enums.
	 * @since 3.2
	 */
	private static final class EnumHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return ENUM;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(100, 70, 50);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_enums;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {

			// 1: match types
			SimpleName name= token.getNode();
			ASTNode node= name.getParent();
			int nodeType= node.getNodeType();
			if (nodeType != ASTNode.METHOD_INVOCATION && nodeType != ASTNode.SIMPLE_TYPE && nodeType != ASTNode.QUALIFIED_TYPE && nodeType != ASTNode.QUALIFIED_NAME
					&& nodeType != ASTNode.QUALIFIED_NAME && nodeType != ASTNode.ENUM_DECLARATION)
				return false;
			while (nodeType == ASTNode.QUALIFIED_NAME) {
				node= node.getParent();
				nodeType= node.getNodeType();
				if (nodeType == ASTNode.IMPORT_DECLARATION)
					return false;
			}

			// 2: match enums
			IBinding binding= token.getBinding();
			return binding instanceof ITypeBinding && ((ITypeBinding) binding).isEnum();
		}
	}

	/**
	 * Semantic highlighting for interfaces.
	 * @since 3.2
	 */
	private static final class InterfaceHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return INTERFACE;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(50, 63, 112);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_interfaces;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {

			// 1: match types
			SimpleName name= token.getNode();
			ASTNode node= name.getParent();
			int nodeType= node.getNodeType();
			if (nodeType != ASTNode.SIMPLE_TYPE && nodeType != ASTNode.QUALIFIED_TYPE  && nodeType != ASTNode.QUALIFIED_NAME && nodeType != ASTNode.TYPE_DECLARATION)
				return false;
			while (nodeType == ASTNode.QUALIFIED_NAME) {
				node= node.getParent();
				nodeType= node.getNodeType();
				if (nodeType == ASTNode.IMPORT_DECLARATION)
					return false;
			}

			// 2: match interfaces
			IBinding binding= token.getBinding();
			return binding instanceof ITypeBinding && ((ITypeBinding) binding).isInterface();
		}
	}

	/**
	 * Semantic highlighting for annotation types.
	 * @since 3.2
	 */
	private static final class AnnotationHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return ANNOTATION;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(100, 100, 100);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return true; // as it replaces the syntax based highlighting which is always enabled
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_annotations;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {

			// 1: match types
			SimpleName name= token.getNode();
			ASTNode node= name.getParent();
			int nodeType= node.getNodeType();
			if (nodeType != ASTNode.SIMPLE_TYPE && nodeType != ASTNode.QUALIFIED_TYPE  && nodeType != ASTNode.QUALIFIED_NAME && nodeType != ASTNode.ANNOTATION_TYPE_DECLARATION
					&& nodeType != ASTNode.MARKER_ANNOTATION && nodeType != ASTNode.NORMAL_ANNOTATION && nodeType != ASTNode.SINGLE_MEMBER_ANNOTATION)
				return false;
			while (nodeType == ASTNode.QUALIFIED_NAME) {
				node= node.getParent();
				nodeType= node.getNodeType();
				if (nodeType == ASTNode.IMPORT_DECLARATION)
					return false;
			}

			// 2: match annotations
			IBinding binding= token.getBinding();
			return binding instanceof ITypeBinding && ((ITypeBinding) binding).isAnnotation();
		}
	}

	/**
	 * Semantic highlighting for annotation types.
	 * @since 3.2
	 */
	private static final class TypeArgumentHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return TYPE_ARGUMENT;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(13, 100, 0);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_typeArguments;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {

			// 1: match types
			SimpleName name= token.getNode();
			ASTNode node= name.getParent();
			int nodeType= node.getNodeType();
			if (nodeType != ASTNode.SIMPLE_TYPE && nodeType != ASTNode.QUALIFIED_TYPE)
				return false;

			// 2: match type arguments
			StructuralPropertyDescriptor locationInParent= node.getLocationInParent();
			if (locationInParent == ParameterizedType.TYPE_ARGUMENTS_PROPERTY)
				return true;

			return false;
		}
	}

	/**
	 * Semantic highlighting for numbers.
	 * @since 3.4
	 */
	private static final class NumberHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return NUMBER;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(42, 0, 255);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_numbers;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumesLiteral(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumesLiteral(SemanticToken token) {
			Expression expr= token.getLiteral();
			return expr != null && expr.getNodeType() == ASTNode.NUMBER_LITERAL;
		}
	}

	/**
	 * Semantic highlighting for classes.
	 * @since 3.7
	 */
	private static final class AbstractClassHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return ABSTRACT_CLASS;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(139, 136, 22);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_abstractClasses;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
		 */
		@Override
		public boolean consumes(SemanticToken token) {

			// 1: match types
			SimpleName name= token.getNode();
			ASTNode node= name.getParent();
			int nodeType= node.getNodeType();
			if (nodeType != ASTNode.SIMPLE_TYPE && nodeType != ASTNode.THIS_EXPRESSION && nodeType != ASTNode.QUALIFIED_TYPE  && nodeType != ASTNode.QUALIFIED_NAME && nodeType != ASTNode.TYPE_DECLARATION && nodeType != ASTNode.METHOD_INVOCATION)
				return false;
			while (nodeType == ASTNode.QUALIFIED_NAME) {
				node= node.getParent();
				nodeType= node.getNodeType();
				if (nodeType == ASTNode.IMPORT_DECLARATION)
					return false;
			}

			// 2: match classes
			IBinding binding= token.getBinding();
			if (binding instanceof ITypeBinding) {
				ITypeBinding typeBinding= (ITypeBinding) binding;
				// see also ClassHighlighting
				return typeBinding.isClass() && (typeBinding.getModifiers() & Modifier.ABSTRACT) != 0;
			}

			return false;
		}
	}

	/**
	 * Semantic highlighting for inherited field access.
	 * @since 3.8
	 */
	private static final class InheritedFieldHighlighting extends SemanticHighlighting {

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#getPreferenceKey()
		 */
		@Override
		public String getPreferenceKey() {
			return INHERITED_FIELD;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextColor()
		 */
		@Override
		public RGB getDefaultDefaultTextColor() {
			return new RGB(0, 0, 192);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDefaultTextStyleBold()
		 */
		@Override
		public boolean isBoldByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isItalicByDefault()
		 */
		@Override
		public boolean isItalicByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting#isEnabledByDefault()
		 */
		@Override
		public boolean isEnabledByDefault() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return JavaEditorMessages.SemanticHighlighting_inheritedField;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.ISemanticHighlighting#isMatched(org.eclipse.jdt.core.dom.ASTNode)
		 */
		@Override
		public boolean consumes(final SemanticToken token) {
			final SimpleName node= token.getNode();
			if (node.isDeclaration()) {
				return false;
			}

			final IBinding binding= token.getBinding();
			if (binding == null || binding.getKind() != IBinding.VARIABLE) {
				return false;
			}

			ITypeBinding currentType= Bindings.getBindingOfParentType(node);
			ITypeBinding declaringType= ((IVariableBinding) binding).getDeclaringClass();
			if (declaringType == null || currentType == declaringType)
				return false;

			return Bindings.isSuperType(declaringType, currentType);
		}
	}

	/**
	 * A named preference that controls the given semantic highlighting's color.
	 *
	 * @param semanticHighlighting the semantic highlighting
	 * @return the color preference key
	 */
	public static String getColorPreferenceKey(SemanticHighlighting semanticHighlighting) {
		return PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + semanticHighlighting.getPreferenceKey() + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_COLOR_SUFFIX;
	}

	/**
	 * A named preference that controls if the given semantic highlighting has the text attribute bold.
	 *
	 * @param semanticHighlighting the semantic highlighting
	 * @return the bold preference key
	 */
	public static String getBoldPreferenceKey(SemanticHighlighting semanticHighlighting) {
		return PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + semanticHighlighting.getPreferenceKey() + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_BOLD_SUFFIX;
	}

	/**
	 * A named preference that controls if the given semantic highlighting has the text attribute italic.
	 *
	 * @param semanticHighlighting the semantic highlighting
	 * @return the italic preference key
	 */
	public static String getItalicPreferenceKey(SemanticHighlighting semanticHighlighting) {
		return PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + semanticHighlighting.getPreferenceKey() + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_ITALIC_SUFFIX;
	}

	/**
	 * A named preference that controls if the given semantic highlighting has the text attribute strikethrough.
	 *
	 * @param semanticHighlighting the semantic highlighting
	 * @return the strikethrough preference key
	 * @since 3.1
	 */
	public static String getStrikethroughPreferenceKey(SemanticHighlighting semanticHighlighting) {
		return PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + semanticHighlighting.getPreferenceKey() + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_STRIKETHROUGH_SUFFIX;
	}

	/**
	 * A named preference that controls if the given semantic highlighting has the text attribute underline.
	 *
	 * @param semanticHighlighting the semantic highlighting
	 * @return the underline preference key
	 * @since 3.1
	 */
	public static String getUnderlinePreferenceKey(SemanticHighlighting semanticHighlighting) {
		return PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + semanticHighlighting.getPreferenceKey() + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_UNDERLINE_SUFFIX;
	}

	/**
	 * A named preference that controls if the given semantic highlighting is enabled.
	 *
	 * @param semanticHighlighting the semantic highlighting
	 * @return the enabled preference key
	 */
	public static String getEnabledPreferenceKey(SemanticHighlighting semanticHighlighting) {
		return PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + semanticHighlighting.getPreferenceKey() + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_ENABLED_SUFFIX;
	}

	/**
	 * @return The semantic highlightings, the order defines the precedence of matches, the first match wins.
	 */
	public static SemanticHighlighting[] getSemanticHighlightings() {
		if (fgSemanticHighlightings == null)
			fgSemanticHighlightings= new SemanticHighlighting[] {
				new DeprecatedMemberHighlighting(),
				new AutoboxHighlighting(),
				new StaticFinalFieldHighlighting(),
				new StaticFieldHighlighting(),
				new InheritedFieldHighlighting(),
				new FieldHighlighting(),
				new MethodDeclarationHighlighting(),
				new StaticMethodInvocationHighlighting(),
				new AbstractMethodInvocationHighlighting(),
				new AnnotationElementReferenceHighlighting(),
				new InheritedMethodInvocationHighlighting(),
				new ParameterVariableHighlighting(),
				new LocalVariableDeclarationHighlighting(),
				new LocalVariableHighlighting(),
				new TypeVariableHighlighting(), // before type arguments!
				new MethodHighlighting(), // before types to get ctors
				new TypeArgumentHighlighting(), // before other types
				new AbstractClassHighlighting(), // before classes
				new ClassHighlighting(),
				new EnumHighlighting(),
				new AnnotationHighlighting(), // before interfaces
				new InterfaceHighlighting(),
				new NumberHighlighting(),
			};
		return fgSemanticHighlightings;
	}

	/**
	 * Initialize default preferences in the given preference store.
	 * @param store The preference store
	 */
	public static void initDefaults(IPreferenceStore store) {
		SemanticHighlighting[] semanticHighlightings= getSemanticHighlightings();
		for (int i= 0, n= semanticHighlightings.length; i < n; i++) {
			SemanticHighlighting semanticHighlighting= semanticHighlightings[i];
			setDefaultAndFireEvent(store, SemanticHighlightings.getColorPreferenceKey(semanticHighlighting), semanticHighlighting.getDefaultTextColor());
			store.setDefault(SemanticHighlightings.getBoldPreferenceKey(semanticHighlighting), semanticHighlighting.isBoldByDefault());
			store.setDefault(SemanticHighlightings.getItalicPreferenceKey(semanticHighlighting), semanticHighlighting.isItalicByDefault());
			store.setDefault(SemanticHighlightings.getStrikethroughPreferenceKey(semanticHighlighting), semanticHighlighting.isStrikethroughByDefault());
			store.setDefault(SemanticHighlightings.getUnderlinePreferenceKey(semanticHighlighting), semanticHighlighting.isUnderlineByDefault());
			store.setDefault(SemanticHighlightings.getEnabledPreferenceKey(semanticHighlighting), semanticHighlighting.isEnabledByDefault());
		}

		convertMethodHighlightingPreferences(store);
		convertAnnotationHighlightingPreferences(store);
	}

	/**
	 * Tests whether <code>event</code> in <code>store</code> affects the
	 * enablement of semantic highlighting.
	 *
	 * @param store the preference store where <code>event</code> was observed
	 * @param event the property change under examination
	 * @return <code>true</code> if <code>event</code> changed semantic
	 *         highlighting enablement, <code>false</code> if it did not
	 * @since 3.1
	 */
	public static boolean affectsEnablement(IPreferenceStore store, PropertyChangeEvent event) {
		String relevantKey= null;
		SemanticHighlighting[] highlightings= getSemanticHighlightings();
		for (int i= 0; i < highlightings.length; i++) {
			if (event.getProperty().equals(getEnabledPreferenceKey(highlightings[i]))) {
				relevantKey= event.getProperty();
				break;
			}
		}
		if (relevantKey == null)
			return false;

		for (int i= 0; i < highlightings.length; i++) {
			String key= getEnabledPreferenceKey(highlightings[i]);
			if (key.equals(relevantKey))
				continue;
			if (store.getBoolean(key))
				return false; // another is still enabled or was enabled before
		}

		// all others are disabled, so toggling relevantKey affects the enablement
		return true;
	}

	/**
	 * Tests whether semantic highlighting is currently enabled.
	 *
	 * @param store the preference store to consult
	 * @return <code>true</code> if semantic highlighting is enabled,
	 *         <code>false</code> if it is not
	 * @since 3.1
	 */
	public static boolean isEnabled(IPreferenceStore store) {
		SemanticHighlighting[] highlightings= getSemanticHighlightings();
		boolean enable= false;
		for (int i= 0; i < highlightings.length; i++) {
			String enabledKey= getEnabledPreferenceKey(highlightings[i]);
			if (store.getBoolean(enabledKey)) {
				enable= true;
				break;
			}
		}

		return enable;
	}

	/**
	 * In 3.0, methods were highlighted by a rule-based word matcher that
	 * matched any identifier that was followed by possibly white space and a
	 * left parenthesis.
	 * <p>
	 * With generics, this does not work any longer for constructors of generic
	 * types, and the highlighting has been moved to be a semantic highlighting.
	 * Because different preference key naming schemes are used, we have to
	 * migrate the old settings to the new ones, which is done here. Nothing
	 * needs to be done if the old settings were set to the default values.
	 * </p>
	 *
	 * @param store the preference store to migrate
	 * @since 3.1
	 */
	private static void convertMethodHighlightingPreferences(IPreferenceStore store) {
		String colorkey= PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + METHOD + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_COLOR_SUFFIX;
		String boldkey= PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + METHOD + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_BOLD_SUFFIX;
		String italickey= PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + METHOD + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_ITALIC_SUFFIX;
		String enabledkey= PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + METHOD + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_ENABLED_SUFFIX;

		@SuppressWarnings("deprecation") String oldColorkey= PreferenceConstants.EDITOR_JAVA_METHOD_NAME_COLOR;
		@SuppressWarnings("deprecation") String oldBoldkey= PreferenceConstants.EDITOR_JAVA_METHOD_NAME_BOLD;
		@SuppressWarnings("deprecation") String oldItalickey= PreferenceConstants.EDITOR_JAVA_METHOD_NAME_ITALIC;

		if (conditionalReset(store, oldColorkey, colorkey)
				|| conditionalReset(store, oldBoldkey, boldkey)
				|| conditionalReset(store, oldItalickey, italickey)) {
			store.setValue(enabledkey, true);
		}

	}

	/**
	 * In 3.1, annotations were highlighted by a rule-based word matcher that matched any identifier
	 * preceded by an '@' sign and possibly white space.
	 * <p>
	 * This does not work when there is a comment between the '@' and the annotation, results in
	 * stale highlighting if there is a new line between the '@' and the annotation, and does not
	 * work for highlighting annotation declarations. Because different preference key naming
	 * schemes are used, we have to migrate the old settings to the new ones, which is done here.
	 * Nothing needs to be done if the old settings were set to the default values.
	 * </p>
	 *
	 * @param store the preference store to migrate
	 * @since 3.2
	 */
	private static void convertAnnotationHighlightingPreferences(IPreferenceStore store) {
		String colorkey= PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + ANNOTATION + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_COLOR_SUFFIX;
		String boldkey= PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + ANNOTATION + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_BOLD_SUFFIX;
		String italickey= PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + ANNOTATION + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_ITALIC_SUFFIX;
		String strikethroughKey= PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + ANNOTATION + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_STRIKETHROUGH_SUFFIX;
		String underlineKey= PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + ANNOTATION + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_UNDERLINE_SUFFIX;
		String enabledkey= PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + ANNOTATION + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_ENABLED_SUFFIX;

		@SuppressWarnings("deprecation") String oldColorkey= PreferenceConstants.EDITOR_JAVA_ANNOTATION_COLOR;
		@SuppressWarnings("deprecation") String oldBoldkey= PreferenceConstants.EDITOR_JAVA_ANNOTATION_BOLD;
		@SuppressWarnings("deprecation") String oldItalickey= PreferenceConstants.EDITOR_JAVA_ANNOTATION_ITALIC;
		@SuppressWarnings("deprecation") String oldStrikethroughKey= PreferenceConstants.EDITOR_JAVA_ANNOTATION_STRIKETHROUGH;
		@SuppressWarnings("deprecation") String oldUnderlineKey= PreferenceConstants.EDITOR_JAVA_ANNOTATION_UNDERLINE;

		if (conditionalReset(store, oldColorkey, colorkey)
				|| conditionalReset(store, oldBoldkey, boldkey)
				|| conditionalReset(store, oldItalickey, italickey)
				|| conditionalReset(store, oldStrikethroughKey, strikethroughKey)
				|| conditionalReset(store, oldUnderlineKey, underlineKey)) {
			store.setValue(enabledkey, true);
		}

	}

	/**
	 * If the setting pointed to by <code>oldKey</code> is not the default
	 * setting, store that setting under <code>newKey</code> and reset
	 * <code>oldKey</code> to its default setting.
	 * <p>
	 * Returns <code>true</code> if any changes were made.
	 * </p>
	 *
	 * @param store the preference store to read from and write to
	 * @param oldKey the old preference key
	 * @param newKey the new preference key
	 * @return <code>true</code> if <code>store</code> was modified,
	 *         <code>false</code> if not
	 * @since 3.1
	 */
	private static boolean conditionalReset(IPreferenceStore store, String oldKey, String newKey) {
		if (!store.isDefault(oldKey)) {
			if (store.isDefault(newKey))
				store.setValue(newKey, store.getString(oldKey));
			store.setToDefault(oldKey);
			return true;
		}
		return false;
	}

	/**
	 * Sets the default value and fires a property
	 * change event if necessary.
	 *
	 * @param store	the preference store
	 * @param key the preference key
	 * @param newValue the new value
	 * @since 3.3
	 */
	private static void setDefaultAndFireEvent(IPreferenceStore store, String key, RGB newValue) {
		RGB oldValue= null;
		if (store.isDefault(key))
			oldValue= PreferenceConverter.getDefaultColor(store, key);

		PreferenceConverter.setDefault(store, key, newValue);

		if (oldValue != null && !oldValue.equals(newValue))
			store.firePropertyChangeEvent(key, oldValue, newValue);
	}

	/**
	 * Extracts the binding from the token's simple name.
	 * Works around bug 62605 to return the correct constructor binding in a ClassInstanceCreation.
	 *
	 * @param token the token to extract the binding from
	 * @return the token's binding, or <code>null</code>
	 */
	private static IBinding getBinding(SemanticToken token) {
		ASTNode node= token.getNode();
		ASTNode normalized= ASTNodes.getNormalizedNode(node);
		if (normalized.getLocationInParent() == ClassInstanceCreation.TYPE_PROPERTY) {
			// work around: https://bugs.eclipse.org/bugs/show_bug.cgi?id=62605
			return ((ClassInstanceCreation) normalized.getParent()).resolveConstructorBinding();
		}
		return token.getBinding();
	}

	/**
	 * Do not instantiate
	 */
	private SemanticHighlightings() {
	}
}

/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.util;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.BodyDeclarationRewrite;
import org.eclipse.jdt.internal.corext.dom.DimensionRewrite;
import org.eclipse.jdt.internal.corext.dom.GenericVisitor;
import org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.jdt.internal.corext.dom.NecessaryParenthesesChecker;
import org.eclipse.jdt.internal.corext.dom.ReplaceRewrite;
import org.eclipse.jdt.internal.corext.dom.StatementRewrite;
import org.eclipse.jdt.internal.corext.dom.TypeRules;
import org.eclipse.jdt.internal.corext.dom.VariableDeclarationRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;

import org.eclipse.jdt.internal.ui.text.correction.ASTResolving;

/**
 * The org.eclipse.jdt.ui bundle contains a few internal helper classes that simplify
 * common tasks when dealing with JDT Core or UI APIs. Here's a list of the most important ones:
 * 
 * <h2>Java Model</h2>
 * <p>
 * APIs in {@link org.eclipse.jdt.core}.
 * </p>
 * 
 * <p>
 * Static helper methods for analysis in {@link org.eclipse.jdt.internal.corext.util} and elsewhere:
 * </p>
 * <ul>
 * <li>{@link JavaModelUtil}</li>
 * <li>{@link JavaElementUtil}</li>
 * <li>{@link JdtFlags}</li>
 * <li>{@link JavaConventionsUtil}</li>
 * <li>{@link MethodOverrideTester}</li>
 * <li>{@link SuperTypeHierarchyCache}</li>
 * </ul>
 * 
 * <p>
 * Static helper methods for stubs creation:
 * </p>
 * <ul>
 * <li>{@link StubUtility}</li>
 * </ul>
 * 
 * 
 * <h2>DOM AST</h2>
 * <p>
 * APIs in {@link org.eclipse.jdt.core.dom} and {@link org.eclipse.jdt.core.dom.rewrite}.<br>
 * Core API classes that are easy to miss: {@link NodeFinder}, {@link ASTVisitor}, {@link ASTMatcher}.
 * </p>
 * 
 * <p>
 * Static helper methods for analysis:
 * </p>
 * <ul>
 * <li>{@link ASTNodes}</li>
 * <li>{@link ASTNodeSearchUtil}</li>
 * <li>{@link ASTResolving}</li>
 * <li>{@link Bindings}</li>
 * <li>{@link TypeRules}</li>
 * </ul>
 * 
 * <p>
 * Static helper methods for node/stubs creation:
 * </p>
 * <ul>
 * <li>{@link ASTNodeFactory}</li>
 * <li>{@link StubUtility2}</li>
 * </ul>
 * 
 * <p>
 * Helper classes in {@link org.eclipse.jdt.internal.corext.dom}, e.g.:
 * </p>
 * <ul>
 * <li>{@link GenericVisitor}</li>
 * <li>{@link HierarchicalASTVisitor}</li>
 * <li>{@link NecessaryParenthesesChecker}</li>
 * </ul>
 * 
 * <p>
 * Helper classes for {@link ASTRewrite}:
 * </p>
 * <ul>
 * <li>{@link CompilationUnitRewrite}</li>
 * <li>{@link BodyDeclarationRewrite}</li>
 * <li>{@link DimensionRewrite}</li>
 * <li>{@link ModifierRewrite}</li>
 * <li>{@link ReplaceRewrite}</li>
 * <li>{@link StatementRewrite}</li>
 * <li>{@link VariableDeclarationRewrite}</li>
 * </ul>
 * 
 * @noreference This class is not intended to be referenced by clients
 */
public final class JDTUIHelperClasses {
	private JDTUIHelperClasses() {
		// no instances
	}
}

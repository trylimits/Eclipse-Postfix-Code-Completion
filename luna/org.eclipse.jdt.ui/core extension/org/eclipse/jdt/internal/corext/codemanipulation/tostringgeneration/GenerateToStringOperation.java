/*******************************************************************************
 * Copyright (c) 2010, 2011 Mateusz Matela and others.
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

import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationMessages;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;


/**
 * <p>
 * A workspace runnable to add implementation for <code>{@link java.lang.Object#toString()}</code>
 * </p>
 * 
 * @since 3.5
 */
public class GenerateToStringOperation implements IWorkspaceRunnable {

	/** The insertion point, or <code>null</code> */
	private IJavaElement fInsert;

	private CompilationUnitRewrite fRewrite;

	private ToStringGenerationContext fContext;

	private AbstractToStringGenerator fGenerator;

	private CompilationUnit fUnit;

	private GenerateToStringOperation(IJavaElement insert, ToStringGenerationContext context, AbstractToStringGenerator generator, CompilationUnit unit, CompilationUnitRewrite rewrite) {
		fInsert= insert;
		fContext= context;
		fRewrite= rewrite;
		fUnit= unit;
		fGenerator= generator;
	}

	public void run(IProgressMonitor monitor) throws CoreException {
		if (monitor == null)
			monitor= new NullProgressMonitor();
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(CodeGenerationMessages.GenerateToStringOperation_description);


			AbstractTypeDeclaration declaration= (AbstractTypeDeclaration)ASTNodes.findDeclaration(fContext.getTypeBinding(), fRewrite.getRoot());
			ListRewrite rewriter= fRewrite.getASTRewrite().getListRewrite(declaration, declaration.getBodyDeclarationsProperty());
			if (fContext.getTypeBinding() != null && rewriter != null) {

				MethodDeclaration toStringMethod= fGenerator.generateToStringMethod();

				List<BodyDeclaration> list= declaration.bodyDeclarations();
				BodyDeclaration replace= findMethodToReplace(list, toStringMethod);
				if (replace == null || ((Boolean)toStringMethod.getProperty(AbstractToStringGenerator.OVERWRITE_METHOD_PROPERTY)).booleanValue())
					insertMethod(toStringMethod, rewriter, replace);

				List<MethodDeclaration> helperMethods= fGenerator.generateHelperMethods();
				for (Iterator<MethodDeclaration> iterator= helperMethods.iterator(); iterator.hasNext();) {
					MethodDeclaration method= iterator.next();
					replace= findMethodToReplace(list, method);
					if (replace == null || ((Boolean)method.getProperty(AbstractToStringGenerator.OVERWRITE_METHOD_PROPERTY)).booleanValue()) {
						insertMethod(method, rewriter, replace);
					}
				}

				JavaModelUtil.applyEdit((ICompilationUnit)fUnit.getJavaElement(), fRewrite.createChange(true).getEdit(), false, monitor);
			}

		} finally {
			monitor.done();
		}
	}

	/**
	 * @return RefactoringStatus with eventual errors and warnings
	 */
	public RefactoringStatus checkConditions() {
		return fGenerator.checkConditions();
	}



	protected void insertMethod(MethodDeclaration method, ListRewrite rewriter, BodyDeclaration replace) throws JavaModelException {
		if (replace != null) {
			rewriter.replace(replace, method, null);
		} else {
			ASTNode insertion= StubUtility2.getNodeToInsertBefore(rewriter, fInsert);
			if (insertion != null)
				rewriter.insertBefore(method, insertion, null);
			else
				rewriter.insertLast(method, null);
		}
	}

	/**
	 * Determines if given method exists in a given list
	 * 
	 * @param list list of method to search through
	 * @param method method to find
	 * @return declaration of method from the list that has the same name and parameter types, or
	 *         null if not found
	 */
	protected BodyDeclaration findMethodToReplace(final List<BodyDeclaration> list, MethodDeclaration method) {
		for (final Iterator<BodyDeclaration> iterator= list.iterator(); iterator.hasNext();) {
			final BodyDeclaration bodyDecl= iterator.next();
			if (bodyDecl instanceof MethodDeclaration) {
				final MethodDeclaration method2= (MethodDeclaration)bodyDecl;
				if (method2.getName().getIdentifier().equals(method.getName().getIdentifier()) && method2.parameters().size() == method.parameters().size()) {
					Iterator<SingleVariableDeclaration> iterator1= method.parameters().iterator();
					Iterator<SingleVariableDeclaration> iterator2= method2.parameters().iterator();
					boolean ok= true;
					while (iterator1.hasNext()) {
						if (!iterator1.next().getType().subtreeMatch(new ASTMatcher(), iterator2.next().getType())) {
							ok= false;
							break;
						}
					}
					if (ok)
						return method2;
				}
			}
		}
		return null;
	}

	public ISchedulingRule getSchedulingRule() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}

	public static final int STRING_CONCATENATION= 0;

	public static final int STRING_BUILDER= 1;

	public static final int STRING_BUILDER_CHAINED= 2;

	public static final int STRING_FORMAT= 3;

	public static final int CUSTOM_BUILDER= 4;

	private final static String[] hardcoded_styles= {
			CodeGenerationMessages.GenerateToStringOperation_stringConcatenation_style_name,
			CodeGenerationMessages.GenerateToStringOperation_stringBuilder_style_name,
			CodeGenerationMessages.GenerateToStringOperation_StringBuilder_chained_style_name,
			CodeGenerationMessages.GenerateToStringOperation_string_format_style_name,
			CodeGenerationMessages.GenerateToStringOperation_customStringBuilder_style_name
			};

	/**
	 * @return Array containing names of implemented code styles
	 */
	public static String[] getStyleNames() {
		return hardcoded_styles;
	}

	/**
	 * 
	 * @param toStringStyle id number of the code style (its position in the array returned by
	 *            {@link #getStyleNames()}
	 * @return a toString() generator implementing given code style
	 */
	private static AbstractToStringGenerator createToStringGenerator(int toStringStyle) {
		switch (toStringStyle) {
			case STRING_CONCATENATION:
				return new StringConcatenationGenerator();
			case STRING_BUILDER:
				return new StringBuilderGenerator();
			case STRING_BUILDER_CHAINED:
				return new StringBuilderChainGenerator();
			case STRING_FORMAT:
				return new StringFormatGenerator();
			case CUSTOM_BUILDER:
				return new CustomBuilderGenerator();
			default:
				throw new IllegalArgumentException("Undefined toString() code style: " + toStringStyle); //$NON-NLS-1$
		}
	}

	/**
	 * @param toStringStyle id number of the style (its position in the array returned by
	 *            {@link #getStyleNames()}
	 * @return a template parser that should be used with given code style
	 */
	public static ToStringTemplateParser createTemplateParser(int toStringStyle) {
		return new ToStringTemplateParser();
	}

	/**
	 * Creates new <code>GenerateToStringOperation</code>, using <code>settings.toStringStyle</code>
	 * field to choose the right subclass.
	 * 
	 * @param typeBinding binding for the type for which the toString() method will be created
	 * @param selectedBindings bindings for the typetype's members to be used in created method
	 * @param unit a compilation unit containing the type
	 * @param elementPosition at this position in the compilation unit created method will be added
	 * @param settings the settings for toString() generator
	 * @return a ready to use <code>GenerateToStringOperation</code> object
	 */
	public static GenerateToStringOperation createOperation(ITypeBinding typeBinding, Object[] selectedBindings, CompilationUnit unit, IJavaElement elementPosition,
			ToStringGenerationSettings settings) {
		AbstractToStringGenerator generator= createToStringGenerator(settings.toStringStyle);
		ToStringTemplateParser parser= createTemplateParser(settings.toStringStyle);
		parser.parseTemplate(settings.stringFormatTemplate);
		CompilationUnitRewrite rewrite= new CompilationUnitRewrite((ICompilationUnit)unit.getTypeRoot(), unit);
		ToStringGenerationContext context= new ToStringGenerationContext(parser, selectedBindings, settings, typeBinding, rewrite);
		generator.setContext(context);
		return new GenerateToStringOperation(elementPosition, context, generator, unit, rewrite);
	}


}

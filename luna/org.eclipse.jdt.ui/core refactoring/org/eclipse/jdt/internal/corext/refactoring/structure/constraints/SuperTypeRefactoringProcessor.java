/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Stephan Herrmann <stephan@cs.tu-berlin.de> - [refactoring] pull-up with "use the destination type where possible" creates bogus import of nested type - https://bugs.eclipse.org/393932
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.structure.constraints;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import org.eclipse.ltk.core.refactoring.GroupCategory;
import org.eclipse.ltk.core.refactoring.GroupCategorySet;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor;

import org.eclipse.jdt.core.BindingKey;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine2;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRewriteUtil;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.CompilationUnitRange;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TType;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TypeEnvironment;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.CastVariable2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ITypeConstraintVariable;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.TextEditBasedChangeManager;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.SearchUtils;

import org.eclipse.jdt.ui.CodeGeneration;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;

/**
 * Partial implementation of a refactoring processor solving supertype
 * constraint models.
 *
 * @since 3.1
 */
public abstract class SuperTypeRefactoringProcessor extends RefactoringProcessor {

	protected static final String ATTRIBUTE_INSTANCEOF= "instanceof"; //$NON-NLS-1$

	protected static final String ATTRIBUTE_REPLACE= "replace"; //$NON-NLS-1$

	/** The super type group category set */
	protected static final GroupCategorySet SET_SUPER_TYPE= new GroupCategorySet(new GroupCategory("org.eclipse.jdt.internal.corext.superType", //$NON-NLS-1$
			RefactoringCoreMessages.SuperTypeRefactoringProcessor_category_name, RefactoringCoreMessages.SuperTypeRefactoringProcessor_category_description));

	/** Number of compilation units to parse at once */
	private static final int SIZE_BATCH= 500;

	/**
	 * Returns a new ast node corresponding to the given type.
	 * 
	 * @param rewrite the compilation unit rewrite to use
	 * @param type the new type
	 * @param node the old node
	 * @return A corresponding ast node
	 */
	protected static ASTNode createCorrespondingNode(final CompilationUnitRewrite rewrite, final TType type, ASTNode node) {
		ImportRewrite importRewrite= rewrite.getImportRewrite();
		ImportRewriteContext context = new ContextSensitiveImportRewriteContext(node, importRewrite);
		return importRewrite.addImportFromSignature(new BindingKey(type.getBindingKey()).toSignature(), rewrite.getAST(), context);
	}

	/** Should type occurrences on instanceof's also be rewritten? */
	protected boolean fInstanceOf= false;

	/**
	 * The obsolete casts (element type:
	 * <code>&lt;ICompilationUnit, Collection&lt;CastVariable2&gt;&gt;</code>)
	 */
	protected Map<ICompilationUnit, Collection<CastVariable2>> fObsoleteCasts= null;

	/** The working copy owner */
	protected final WorkingCopyOwner fOwner= new WorkingCopyOwner() {
		// use default implementation
	};

	/** Should occurrences of the type be replaced by the supertype? */
	protected boolean fReplace= false;

	/** The code generation settings, or <code>null</code> */
	protected CodeGenerationSettings fSettings;

	/** The static bindings to import */
	protected final Set<IBinding> fStaticBindings= new HashSet<IBinding>();

	/** The type bindings to import */
	protected final Set<ITypeBinding> fTypeBindings= new HashSet<ITypeBinding>();

	/**
	 * The type occurrences (element type:
	 * <code>&lt;ICompilationUnit, Collection&lt;IDeclaredConstraintVariable&gt;&gt;</code>)
	 */
	protected Map<ICompilationUnit, Collection<ITypeConstraintVariable>> fTypeOccurrences= null;

	/**
	 * Creates a new supertype refactoring processor.
	 *
	 * @param settings
	 *            the code generation settings, or <code>null</code>
	 */
	protected SuperTypeRefactoringProcessor(final CodeGenerationSettings settings) {
		fSettings= settings;
	}

	/**
	 * Adds the refactoring settings to the specified comment.
	 *
	 * @param comment
	 *            the java refactoring descriptor comment
	 * @param addUseSupertype
	 *            <code>true</code> to add the use supertype setting,
	 *            <code>false</code> otherwise
	 */
	protected void addSuperTypeSettings(final JDTRefactoringDescriptorComment comment, final boolean addUseSupertype) {
		Assert.isNotNull(comment);
		if (fReplace) {
			if (addUseSupertype)
				comment.addSetting(RefactoringCoreMessages.SuperTypeRefactoringProcessor_user_supertype_setting);
			if (fInstanceOf)
				comment.addSetting(RefactoringCoreMessages.SuperTypeRefactoringProcessor_use_in_instanceof_setting);
		}
	}

	/**
	 * Creates the super type constraint solver to solve the model.
	 *
	 * @param model
	 *            the model to create a solver for
	 * @return The created super type constraint solver
	 */
	protected abstract SuperTypeConstraintsSolver createContraintSolver(SuperTypeConstraintsModel model);

	/**
	 * Creates the declarations of the new supertype members.
	 *
	 * @param sourceRewrite
	 *            the source compilation unit rewrite
	 * @param targetRewrite
	 *            the target rewrite
	 * @param targetDeclaration
	 *            the target type declaration
	 * @throws CoreException
	 *             if a buffer could not be retrieved
	 */
	protected void createMemberDeclarations(CompilationUnitRewrite sourceRewrite, ASTRewrite targetRewrite, AbstractTypeDeclaration targetDeclaration) throws CoreException {
		// Do nothing
	}

	/**
	 * Creates the declaration of the new supertype, excluding any comments or
	 * package declaration.
	 *
	 * @param sourceRewrite
	 *            the source compilation unit rewrite
	 * @param subType
	 *            the subtype
	 * @param superName
	 *            the name of the supertype
	 * @param sourceDeclaration
	 *            the type declaration of the source type
	 * @param buffer
	 *            the string buffer containing the declaration
	 * @param isInterface
	 *            <code>true</code> if the type declaration is an interface,
	 *            <code>false</code> otherwise
	 * @param status
	 *            the refactoring status
	 * @param monitor
	 *            the progress monitor to use
	 * @throws CoreException
	 *             if an error occurs
	 */
	protected final void createTypeDeclaration(final CompilationUnitRewrite sourceRewrite, final IType subType, final String superName, final AbstractTypeDeclaration sourceDeclaration, final StringBuffer buffer, boolean isInterface, final RefactoringStatus status, final IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(sourceRewrite);
		Assert.isNotNull(subType);
		Assert.isNotNull(superName);
		Assert.isNotNull(sourceDeclaration);
		Assert.isNotNull(buffer);
		Assert.isNotNull(status);
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask("", 100); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_creating);
			final String delimiter= StubUtility.getLineDelimiterUsed(subType.getJavaProject());
			if (JdtFlags.isPublic(subType)) {
				buffer.append(JdtFlags.VISIBILITY_STRING_PUBLIC);
				buffer.append(" "); //$NON-NLS-1$
			}
			if (isInterface)
				buffer.append("interface "); //$NON-NLS-1$
			else
				buffer.append("class "); //$NON-NLS-1$
			buffer.append(superName);
			buffer.append(" {"); //$NON-NLS-1$
			buffer.append(delimiter);
			buffer.append(delimiter);
			buffer.append('}');
			final IDocument document= new Document(buffer.toString());
			final ASTParser parser= ASTParser.newParser(ASTProvider.SHARED_AST_LEVEL);
			parser.setSource(document.get().toCharArray());
			final CompilationUnit unit= (CompilationUnit) parser.createAST(new SubProgressMonitor(monitor, 100));
			final ASTRewrite targetRewrite= ASTRewrite.create(unit.getAST());
			final AbstractTypeDeclaration targetDeclaration= (AbstractTypeDeclaration) unit.types().get(0);
			createTypeParameters(targetRewrite, subType, sourceDeclaration, targetDeclaration);
			createMemberDeclarations(sourceRewrite, targetRewrite, targetDeclaration);
			final TextEdit edit= targetRewrite.rewriteAST(document, subType.getJavaProject().getOptions(true));
			try {
				edit.apply(document, TextEdit.UPDATE_REGIONS);
			} catch (MalformedTreeException exception) {
				JavaPlugin.log(exception);
			} catch (BadLocationException exception) {
				JavaPlugin.log(exception);
			}
			buffer.setLength(0);
			buffer.append(document.get());
		} finally {
			monitor.done();
		}
	}

	/**
	 * Creates the necessary imports for the extracted supertype.
	 *
	 * @param unit
	 *            the working copy of the new supertype
	 * @param monitor
	 *            the progress monitor to use
	 * @return the generated import declaration
	 * @throws CoreException
	 *             if the imports could not be generated
	 */
	protected final String createTypeImports(final ICompilationUnit unit, final IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(unit);
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask("", 100); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_creating);
			final ImportRewrite rewrite= StubUtility.createImportRewrite(unit, true);
			ITypeBinding type= null;
			for (final Iterator<ITypeBinding> iterator= fTypeBindings.iterator(); iterator.hasNext();) {
				type= iterator.next();
				if (type.isTypeVariable()) {
					final ITypeBinding[] bounds= type.getTypeBounds();
					for (int index= 0; index < bounds.length; index++)
						rewrite.addImport(bounds[index]);
				}
				rewrite.addImport(type);
			}
			IBinding binding= null;
			for (final Iterator<IBinding> iterator= fStaticBindings.iterator(); iterator.hasNext();) {
				binding= iterator.next();
				rewrite.addStaticImport(binding);
			}
			final IDocument document= new Document();
			try {
				rewrite.rewriteImports(new SubProgressMonitor(monitor, 100)).apply(document);
			} catch (MalformedTreeException exception) {
				JavaPlugin.log(exception);
			} catch (BadLocationException exception) {
				JavaPlugin.log(exception);
			} catch (CoreException exception) {
				JavaPlugin.log(exception);
			}
			fTypeBindings.clear();
			fStaticBindings.clear();
			return document.get();
		} finally {
			monitor.done();
		}
	}

	/**
	 * Creates the type parameters of the new supertype.
	 *
	 * @param targetRewrite
	 *            the target compilation unit rewrite
	 * @param subType
	 *            the subtype
	 * @param sourceDeclaration
	 *            the type declaration of the source type
	 * @param targetDeclaration
	 *            the type declaration of the target type
	 */
	protected final void createTypeParameters(final ASTRewrite targetRewrite, final IType subType, final AbstractTypeDeclaration sourceDeclaration, final AbstractTypeDeclaration targetDeclaration) {
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(sourceDeclaration);
		Assert.isNotNull(targetDeclaration);
		if (sourceDeclaration instanceof TypeDeclaration) {
			TypeParameter parameter= null;
			final ListRewrite rewrite= targetRewrite.getListRewrite(targetDeclaration, TypeDeclaration.TYPE_PARAMETERS_PROPERTY);
			for (final Iterator<TypeParameter> iterator= ((TypeDeclaration) sourceDeclaration).typeParameters().iterator(); iterator.hasNext();) {
				parameter= iterator.next();
				rewrite.insertLast(ASTNode.copySubtree(targetRewrite.getAST(), parameter), null);
				ImportRewriteUtil.collectImports(subType.getJavaProject(), sourceDeclaration, fTypeBindings, fStaticBindings, false);
			}
		}
	}

	/**
	 * Creates the source for the new compilation unit containing the supertype.
	 *
	 * @param copy
	 *            the working copy of the new supertype
	 * @param subType
	 *            the subtype
	 * @param superName
	 *            the name of the supertype
	 * @param sourceRewrite
	 *            the source compilation unit rewrite
	 * @param declaration
	 *            the type declaration
	 * @param status
	 *            the refactoring status
	 * @param monitor
	 *            the progress monitor to display progress
	 * @return the source of the new compilation unit, or <code>null</code>
	 * @throws CoreException
	 *             if an error occurs
	 */
	protected final String createTypeSource(final ICompilationUnit copy, final IType subType, final String superName, final CompilationUnitRewrite sourceRewrite, final AbstractTypeDeclaration declaration, final RefactoringStatus status, final IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(copy);
		Assert.isNotNull(subType);
		Assert.isNotNull(superName);
		Assert.isNotNull(sourceRewrite);
		Assert.isNotNull(declaration);
		Assert.isNotNull(status);
		Assert.isNotNull(monitor);
		String source= null;
		try {
			monitor.beginTask("", 100); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_creating);
			final String delimiter= StubUtility.getLineDelimiterUsed(subType.getJavaProject());
			String typeComment= null;
			String fileComment= null;
			if (fSettings.createComments) {
				final ITypeParameter[] parameters= subType.getTypeParameters();
				final String[] names= new String[parameters.length];
				for (int index= 0; index < parameters.length; index++)
					names[index]= parameters[index].getElementName();
				typeComment= CodeGeneration.getTypeComment(copy, superName, names, delimiter);
				fileComment= CodeGeneration.getFileComment(copy, delimiter);
			}
			final StringBuffer buffer= new StringBuffer(64);
			createTypeDeclaration(sourceRewrite, subType, superName, declaration, buffer, true, status, new SubProgressMonitor(monitor, 40));
			final String imports= createTypeImports(copy, new SubProgressMonitor(monitor, 60));
			source= createTypeTemplate(copy, imports, fileComment, typeComment, buffer.toString());
			if (source == null) {
				if (!subType.getPackageFragment().isDefaultPackage()) {
					if (imports.length() > 0)
						buffer.insert(0, imports);
					buffer.insert(0, "package " + subType.getPackageFragment().getElementName() + ";"); //$NON-NLS-1$//$NON-NLS-2$
				}
				source= buffer.toString();
			}
			final IDocument document= new Document(source);
			final TextEdit edit= CodeFormatterUtil.format2(CodeFormatter.K_COMPILATION_UNIT, source, 0, delimiter, copy.getJavaProject().getOptions(true));
			if (edit != null) {
				try {
					edit.apply(document, TextEdit.UPDATE_REGIONS);
				} catch (MalformedTreeException exception) {
					JavaPlugin.log(exception);
					status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractInterfaceProcessor_internal_error));
				} catch (BadLocationException exception) {
					JavaPlugin.log(exception);
					status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractInterfaceProcessor_internal_error));
				}
				source= document.get();
			}
		} finally {
			monitor.done();
		}
		return source;
	}

	/**
	 * Creates the type template based on the code generation settings.
	 *
	 * @param unit
	 *            the working copy for the new supertype
	 * @param imports
	 *            the generated imports declaration
	 * @param fileComment
	 *            the file comment
	 * @param comment
	 *            the type comment
	 * @param content
	 *            the type content
	 * @return a template for the supertype, or <code>null</code>
	 * @throws CoreException
	 *             if the template could not be evaluated
	 */
	protected final String createTypeTemplate(final ICompilationUnit unit, final String imports, String fileComment, final String comment, final String content) throws CoreException {
		Assert.isNotNull(unit);
		Assert.isNotNull(imports);
		Assert.isNotNull(content);
		final IPackageFragment fragment= (IPackageFragment) unit.getParent();
		final StringBuffer buffer= new StringBuffer();
		final String delimiter= StubUtility.getLineDelimiterUsed(unit.getJavaProject());
		if (!fragment.isDefaultPackage()) {
			buffer.append("package " + fragment.getElementName() + ";"); //$NON-NLS-1$ //$NON-NLS-2$
			buffer.append(delimiter);
			buffer.append(delimiter);
		}
		if (imports.length() > 0)
			buffer.append(imports);

		return StubUtility.getCompilationUnitContent(unit, buffer.toString(), fileComment, comment, content, delimiter);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void finalize() throws Throwable {
		resetWorkingCopies();
	}

	/**
	 * Returns the field which corresponds to the specified variable declaration
	 * fragment
	 *
	 * @param fragment
	 *            the variable declaration fragment
	 * @return the corresponding field
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	protected final IField getCorrespondingField(final VariableDeclarationFragment fragment) throws JavaModelException {
		final IBinding binding= fragment.getName().resolveBinding();
		if (binding instanceof IVariableBinding) {
			final IVariableBinding variable= (IVariableBinding) binding;
			if (variable.isField()) {
				final ICompilationUnit unit= RefactoringASTParser.getCompilationUnit(fragment);
				final IJavaElement element= unit.getElementAt(fragment.getStartPosition());
				if (element instanceof IField)
					return (IField) element;
			}
		}
		return null;
	}

	/**
	 * Computes the compilation units of fields referencing the specified type
	 * occurrences.
	 *
	 * @param units
	 *            the compilation unit map (element type:
	 *            <code>&lt;IJavaProject, Set&lt;ICompilationUnit&gt;&gt;</code>)
	 * @param nodes
	 *            the ast nodes representing the type occurrences
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	protected final void getFieldReferencingCompilationUnits(final Map<IJavaProject, Set<ICompilationUnit>> units, final ASTNode[] nodes) throws JavaModelException {
		ASTNode node= null;
		IField field= null;
		IJavaProject project= null;
		for (int index= 0; index < nodes.length; index++) {
			node= nodes[index];
			project= RefactoringASTParser.getCompilationUnit(node).getJavaProject();
			if (project != null) {
				final List<IField> fields= getReferencingFields(node, project);
				for (int offset= 0; offset < fields.size(); offset++) {
					field= fields.get(offset);
					Set<ICompilationUnit> set= units.get(project);
					if (set == null) {
						set= new HashSet<ICompilationUnit>();
						units.put(project, set);
					}
					final ICompilationUnit unit= field.getCompilationUnit();
					if (unit != null)
						set.add(unit);
				}
			}
		}
	}

	/**
	 * Computes the compilation units of methods referencing the specified type
	 * occurrences.
	 *
	 * @param units
	 *            the compilation unit map (element type:
	 *            <code>&lt;IJavaProject, Set&lt;ICompilationUnit&gt;&gt;</code>)
	 * @param nodes
	 *            the ast nodes representing the type occurrences
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	protected final void getMethodReferencingCompilationUnits(final Map<IJavaProject, Set<ICompilationUnit>> units, final ASTNode[] nodes) throws JavaModelException {
		ASTNode node= null;
		IMethod method= null;
		IJavaProject project= null;
		for (int index= 0; index < nodes.length; index++) {
			node= nodes[index];
			project= RefactoringASTParser.getCompilationUnit(node).getJavaProject();
			if (project != null) {
				method= getReferencingMethod(node);
				if (method != null) {
					Set<ICompilationUnit> set= units.get(project);
					if (set == null) {
						set= new HashSet<ICompilationUnit>();
						units.put(project, set);
					}
					final ICompilationUnit unit= method.getCompilationUnit();
					if (unit != null)
						set.add(unit);
				}
			}
		}
	}

	/**
	 * Computes the compilation units referencing the subtype to replace.
	 *
	 * @param type
	 *            the subtype
	 * @param monitor
	 *            the progress monitor to use
	 * @param status
	 *            the refactoring status
	 * @return the referenced compilation units (element type:
	 *         <code>&lt;IJavaProject, Collection&lt;SearchResultGroup&gt;&gt;</code>)
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	protected final Map<IJavaProject, Set<SearchResultGroup>> getReferencingCompilationUnits(final IType type, final IProgressMonitor monitor, final RefactoringStatus status) throws JavaModelException {
		try {
			monitor.beginTask("", 100); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
			final RefactoringSearchEngine2 engine= new RefactoringSearchEngine2();
			engine.setOwner(fOwner);
			engine.setFiltering(true, true);
			engine.setStatus(status);
			engine.setScope(RefactoringScopeFactory.create(type));
			engine.setPattern(SearchPattern.createPattern(type, IJavaSearchConstants.REFERENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE));
			engine.searchPattern(new SubProgressMonitor(monitor, 100));
			@SuppressWarnings("unchecked")
			Map<IJavaProject, Set<SearchResultGroup>> result= (Map<IJavaProject, Set<SearchResultGroup>>) engine.getAffectedProjects();
			return result;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Returns the fields which reference the specified ast node.
	 *
	 * @param node
	 *            the ast node
	 * @param project
	 *            the java project
	 * @return the referencing fields
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	protected final List<IField> getReferencingFields(final ASTNode node, final IJavaProject project) throws JavaModelException {
		List<IField> result= Collections.emptyList();
		if (node instanceof Type) {
			final BodyDeclaration parent= (BodyDeclaration) ASTNodes.getParent(node, BodyDeclaration.class);
			if (parent instanceof FieldDeclaration) {
				final List<VariableDeclarationFragment> fragments= ((FieldDeclaration) parent).fragments();
				result= new ArrayList<IField>(fragments.size());
				VariableDeclarationFragment fragment= null;
				for (final Iterator<VariableDeclarationFragment> iterator= fragments.iterator(); iterator.hasNext();) {
					fragment= iterator.next();
					final IField field= getCorrespondingField(fragment);
					if (field != null)
						result.add(field);
				}
			}
		}
		return result;
	}

	/**
	 * Returns the method which references the specified ast node.
	 *
	 * @param node
	 *            the ast node
	 * @return the referencing method
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	protected final IMethod getReferencingMethod(final ASTNode node) throws JavaModelException {
		if (node instanceof Type) {
			final BodyDeclaration parent= (BodyDeclaration) ASTNodes.getParent(node, BodyDeclaration.class);
			if (parent instanceof MethodDeclaration) {
				final IMethodBinding binding= ((MethodDeclaration) parent).resolveBinding();
				if (binding != null) {
					final ICompilationUnit unit= RefactoringASTParser.getCompilationUnit(node);
					final IJavaElement element= unit.getElementAt(node.getStartPosition());
					if (element instanceof IMethod)
						return (IMethod) element;
				}
			}
		}
		return null;
	}

	protected ICompilationUnit getSharedWorkingCopy(final ICompilationUnit unit, final IProgressMonitor monitor) throws JavaModelException {
		try {
			ICompilationUnit copy= unit.findWorkingCopy(fOwner);
			if (copy == null)
				copy= unit.getWorkingCopy(fOwner, monitor);
			return copy;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Returns whether type occurrences in instanceof's should be rewritten.
	 *
	 * @return <code>true</code> if they are rewritten, <code>false</code>
	 *         otherwise
	 */
	public final boolean isInstanceOf() {
		return fInstanceOf;
	}

	/**
	 * Should occurrences of the subtype be replaced by the supertype?
	 *
	 * @return <code>true</code> if the subtype should be replaced,
	 *         <code>false</code> otherwise
	 */
	public final boolean isReplace() {
		return fReplace;
	}

	/**
	 * Performs the first pass of processing the affected compilation units.
	 *
	 * @param creator
	 *            the constraints creator to use
	 * @param units
	 *            the compilation unit map (element type:
	 *            <code>&lt;IJavaProject, Set&lt;ICompilationUnit&gt;&gt;</code>)
	 * @param groups
	 *            the search result group map (element type:
	 *            <code>&lt;ICompilationUnit, SearchResultGroup&gt;</code>)
	 * @param unit
	 *            the compilation unit of the subtype
	 * @param node
	 *            the compilation unit node of the subtype
	 * @param monitor
	 *            the progress monitor to use
	 */
	protected final void performFirstPass(final SuperTypeConstraintsCreator creator, final Map<IJavaProject, Set<ICompilationUnit>> units, final Map<ICompilationUnit, SearchResultGroup> groups, final ICompilationUnit unit, final CompilationUnit node, final IProgressMonitor monitor) {
		try {
			monitor.beginTask("", 100); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
			node.accept(creator);
			monitor.worked(20);
			final SearchResultGroup group= groups.get(unit);
			if (group != null) {
				final ASTNode[] nodes= ASTNodeSearchUtil.getAstNodes(group.getSearchResults(), node);
				try {
					getMethodReferencingCompilationUnits(units, nodes);
					monitor.worked(40);
					getFieldReferencingCompilationUnits(units, nodes);
					monitor.worked(40);
				} catch (JavaModelException exception) {
					JavaPlugin.log(exception);
				}
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Performs the second pass of processing the affected compilation units.
	 *
	 * @param creator
	 *            the constraints creator to use
	 * @param unit
	 *            the compilation unit of the subtype
	 * @param node
	 *            the compilation unit node of the subtype
	 * @param monitor
	 *            the progress monitor to use
	 */
	protected final void performSecondPass(final SuperTypeConstraintsCreator creator, final ICompilationUnit unit, final CompilationUnit node, final IProgressMonitor monitor) {
		try {
			monitor.beginTask("", 20); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
			node.accept(creator);
			monitor.worked(20);
		} finally {
			monitor.done();
		}
	}

	/**
	 * Resets the working copies.
	 */
	protected void resetWorkingCopies() {
		final ICompilationUnit[] units= JavaCore.getWorkingCopies(fOwner);
		for (int index= 0; index < units.length; index++) {
			final ICompilationUnit unit= units[index];
			try {
				unit.discardWorkingCopy();
			} catch (Exception exception) {
				// Do nothing
			}
		}
	}

	/**
	 * Resets the working copies.
	 *
	 * @param unit
	 *            the compilation unit to discard
	 */
	protected void resetWorkingCopies(final ICompilationUnit unit) {
		final ICompilationUnit[] units= JavaCore.getWorkingCopies(fOwner);
		for (int index= 0; index < units.length; index++) {
			if (!units[index].equals(unit)) {
				try {
					units[index].discardWorkingCopy();
				} catch (Exception exception) {
					// Do nothing
				}
			} else {
				try {
					units[index].getBuffer().setContents(unit.getPrimary().getBuffer().getContents());
					JavaModelUtil.reconcile(units[index]);
				} catch (JavaModelException exception) {
					JavaPlugin.log(exception);
				}
			}
		}
	}

	/**
	 * Creates the necessary text edits to replace the subtype occurrence by a
	 * supertype.
	 *
	 * @param range
	 *            the compilation unit range
	 * @param estimate
	 *            the type estimate
	 * @param requestor
	 *            the ast requestor to use
	 * @param rewrite
	 *            the compilation unit rewrite to use
	 * @param copy
	 *            the compilation unit node of the working copy ast
	 * @param replacements
	 *            the set of variable binding keys of formal parameters which
	 *            must be replaced
	 * @param group
	 *            the text edit group to use
	 */
	protected final void rewriteTypeOccurrence(final CompilationUnitRange range, final TType estimate, final ASTRequestor requestor, final CompilationUnitRewrite rewrite, final CompilationUnit copy, final Set<String> replacements, final TextEditGroup group) {
		ASTNode node= null;
		IBinding binding= null;
		final CompilationUnit target= rewrite.getRoot();
		node= NodeFinder.perform(copy, range.getSourceRange());
		if (node != null) {
			node= ASTNodes.getNormalizedNode(node).getParent();
			if (node instanceof VariableDeclaration) {
				binding= ((VariableDeclaration) node).resolveBinding();
				node= target.findDeclaringNode(binding.getKey());
				if (node instanceof SingleVariableDeclaration) {
					rewriteTypeOccurrence(estimate, rewrite, ((SingleVariableDeclaration) node).getType(), group);
					if (node.getParent() instanceof MethodDeclaration) {
						binding= ((VariableDeclaration) node).resolveBinding();
						if (binding != null)
							replacements.add(binding.getKey());
					}
				}
			} else if (node instanceof VariableDeclarationStatement) {
				binding= ((VariableDeclaration) ((VariableDeclarationStatement) node).fragments().get(0)).resolveBinding();
				node= target.findDeclaringNode(binding.getKey());
				if (node instanceof VariableDeclarationFragment)
					rewriteTypeOccurrence(estimate, rewrite, ((VariableDeclarationStatement) ((VariableDeclarationFragment) node).getParent()).getType(), group);
			} else if (node instanceof MethodDeclaration) {
				binding= ((MethodDeclaration) node).resolveBinding();
				node= target.findDeclaringNode(binding.getKey());
				if (node instanceof MethodDeclaration)
					rewriteTypeOccurrence(estimate, rewrite, ((MethodDeclaration) node).getReturnType2(), group);
			} else if (node instanceof FieldDeclaration) {
				binding= ((VariableDeclaration) ((FieldDeclaration) node).fragments().get(0)).resolveBinding();
				node= target.findDeclaringNode(binding.getKey());
				if (node instanceof VariableDeclarationFragment) {
					node= node.getParent();
					if (node instanceof FieldDeclaration)
						rewriteTypeOccurrence(estimate, rewrite, ((FieldDeclaration) node).getType(), group);
				}
			} else if (node instanceof ArrayType) {
				final ASTNode type= node;
				while (node != null && !(node instanceof MethodDeclaration) && !(node instanceof VariableDeclarationFragment))
					node= node.getParent();
				if (node != null) {
					final int delta= node.getStartPosition() + node.getLength() - type.getStartPosition();
					if (node instanceof MethodDeclaration)
						binding= ((MethodDeclaration) node).resolveBinding();
					else if (node instanceof VariableDeclarationFragment)
						binding= ((VariableDeclarationFragment) node).resolveBinding();
					if (binding != null) {
						node= target.findDeclaringNode(binding.getKey());
						if (node instanceof MethodDeclaration || node instanceof VariableDeclarationFragment) {
							node= NodeFinder.perform(target, (node.getStartPosition() + node.getLength() - delta), 0);
							if (node instanceof SimpleName)
								rewriteTypeOccurrence(estimate, rewrite, node, group);
						}
					}
				}
			} else if (node instanceof QualifiedName) {
				final ASTNode name= node;
				while (node != null && !(node instanceof MethodDeclaration) && !(node instanceof VariableDeclarationFragment))
					node= node.getParent();
				if (node != null) {
					final int delta= node.getStartPosition() + node.getLength() - name.getStartPosition();
					if (node instanceof MethodDeclaration)
						binding= ((MethodDeclaration) node).resolveBinding();
					else if (node instanceof VariableDeclarationFragment)
						binding= ((VariableDeclarationFragment) node).resolveBinding();
					if (binding != null) {
						node= target.findDeclaringNode(binding.getKey());
						if (node instanceof SimpleName || node instanceof MethodDeclaration || node instanceof VariableDeclarationFragment) {
							node= NodeFinder.perform(target, (node.getStartPosition() + node.getLength() - delta), 0);
							if (node instanceof SimpleName)
								rewriteTypeOccurrence(estimate, rewrite, node, group);
						}
					}
				}
			} else if (node instanceof CastExpression) {
				final ASTNode expression= node;
				while (node != null && !(node instanceof MethodDeclaration))
					node= node.getParent();
				if (node != null) {
					final int delta= node.getStartPosition() + node.getLength() - expression.getStartPosition();
					binding= ((MethodDeclaration) node).resolveBinding();
					node= target.findDeclaringNode(binding.getKey());
					if (node instanceof MethodDeclaration) {
						node= NodeFinder.perform(target, (node.getStartPosition() + node.getLength() - delta), 0);
						if (node instanceof CastExpression)
							rewriteTypeOccurrence(estimate, rewrite, ((CastExpression) node).getType(), group);
					}
				}
			}
		}
	}

	/**
	 * Creates the necessary text edits to replace the subtype occurrence by a
	 * supertype.
	 *
	 * @param estimate
	 *            the type estimate
	 * @param rewrite
	 *            the ast rewrite to use
	 * @param node
	 *            the ast node to rewrite
	 * @param group
	 *            the text edit group to use
	 */
	protected final void rewriteTypeOccurrence(final TType estimate, final CompilationUnitRewrite rewrite, final ASTNode node, final TextEditGroup group) {
		rewrite.getImportRemover().registerRemovedNode(node);
		rewrite.getASTRewrite().replace(node, createCorrespondingNode(rewrite, estimate, node), group);
	}

	/**
	 * Creates the necessary text edits to replace the subtype occurrence by a
	 * supertype.
	 *
	 * @param manager
	 *            the text change manager to use
	 * @param requestor
	 *            the ast requestor to use
	 * @param rewrite
	 *            the compilation unit rewrite of the subtype (not in working
	 *            copy mode)
	 * @param unit
	 *            the compilation unit
	 * @param node
	 *            the compilation unit node
	 * @param replacements
	 *            the set of variable binding keys of formal parameters which
	 *            must be replaced
	 * @param monitor
	 *            the progress monitor to use
	 * @throws CoreException
	 *             if the change could not be generated
	 */
	protected abstract void rewriteTypeOccurrences(TextEditBasedChangeManager manager, ASTRequestor requestor, CompilationUnitRewrite rewrite, ICompilationUnit unit, CompilationUnit node, Set<String> replacements, IProgressMonitor monitor) throws CoreException;

	/**
	 * Creates the necessary text edits to replace the subtype occurrences by a
	 * supertype.
	 *
	 * @param manager
	 *            the text change manager to use
	 * @param sourceRewrite
	 *            the compilation unit rewrite of the subtype (not in working
	 *            copy mode)
	 * @param sourceRequestor
	 *            the ast requestor of the subtype, or <code>null</code>
	 * @param subUnit
	 *            the compilation unit of the subtype, or <code>null</code>
	 * @param subNode
	 *            the compilation unit node of the subtype, or <code>null</code>
	 * @param replacements
	 *            the set of variable binding keys of formal parameters which
	 *            must be replaced
	 * @param status
	 *            the refactoring status
	 * @param monitor
	 *            the progress monitor to use
	 */
	protected final void rewriteTypeOccurrences(final TextEditBasedChangeManager manager, final ASTRequestor sourceRequestor, final CompilationUnitRewrite sourceRewrite, final ICompilationUnit subUnit, final CompilationUnit subNode, final Set<String> replacements, final RefactoringStatus status, final IProgressMonitor monitor) {
		try {
			monitor.beginTask("", 300); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_creating);
			if (fTypeOccurrences != null) {
				final Set<ICompilationUnit> units= new HashSet<ICompilationUnit>(fTypeOccurrences.keySet());
				if (subUnit != null)
					units.remove(subUnit);
				final Map<IJavaProject, Collection<ICompilationUnit>> projects= new HashMap<IJavaProject, Collection<ICompilationUnit>>();
				Collection<ICompilationUnit> collection= null;
				IJavaProject project= null;
				ICompilationUnit current= null;
				for (final Iterator<ICompilationUnit> iterator= units.iterator(); iterator.hasNext();) {
					current= iterator.next();
					project= current.getJavaProject();
					collection= projects.get(project);
					if (collection == null) {
						collection= new ArrayList<ICompilationUnit>();
						projects.put(project, collection);
					}
					collection.add(current);
				}
				final ASTParser parser= ASTParser.newParser(ASTProvider.SHARED_AST_LEVEL);
				final IProgressMonitor subMonitor= new SubProgressMonitor(monitor, 320);
				try {
					final Set<IJavaProject> keySet= projects.keySet();
					subMonitor.beginTask("", keySet.size() * 100); //$NON-NLS-1$
					subMonitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
					for (final Iterator<IJavaProject> iterator= keySet.iterator(); iterator.hasNext();) {
						project= iterator.next();
						collection= projects.get(project);
						parser.setWorkingCopyOwner(fOwner);
						parser.setResolveBindings(true);
						parser.setProject(project);
						parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
						final IProgressMonitor subsubMonitor= new SubProgressMonitor(subMonitor, 100);
						try {
							subsubMonitor.beginTask("", collection.size() * 100 + 200); //$NON-NLS-1$
							subsubMonitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
							parser.createASTs(collection.toArray(new ICompilationUnit[collection.size()]), new String[0], new ASTRequestor() {

								@Override
								public final void acceptAST(final ICompilationUnit unit, final CompilationUnit node) {
									final IProgressMonitor subsubsubMonitor= new SubProgressMonitor(subsubMonitor, 100);
									try {
										subsubsubMonitor.beginTask("", 100); //$NON-NLS-1$
										subsubsubMonitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
										if (sourceRewrite != null)
											rewriteTypeOccurrences(manager, this, sourceRewrite, unit, node, replacements, new SubProgressMonitor(subsubsubMonitor, 100));
									} catch (CoreException exception) {
										status.merge(RefactoringStatus.createFatalErrorStatus(exception.getLocalizedMessage()));
									} finally {
										subsubsubMonitor.done();
									}
								}

								@Override
								public final void acceptBinding(final String key, final IBinding binding) {
									// Do nothing
								}
							}, new SubProgressMonitor(subsubMonitor, 200));
						} finally {
							subsubMonitor.done();
						}
					}
					try {
						if (subUnit != null && subNode != null && sourceRewrite != null && sourceRequestor != null)
							rewriteTypeOccurrences(manager, sourceRequestor, sourceRewrite, subUnit, subNode, replacements, new SubProgressMonitor(subMonitor, 20));
					} catch (CoreException exception) {
						status.merge(RefactoringStatus.createFatalErrorStatus(exception.getLocalizedMessage()));
					}
				} finally {
					subMonitor.done();
				}
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Determines whether type occurrences in instanceof's should be rewritten.
	 *
	 * @param rewrite
	 *            <code>true</code> to rewrite them, <code>false</code>
	 *            otherwise
	 */
	public final void setInstanceOf(final boolean rewrite) {
		fInstanceOf= rewrite;
	}

	/**
	 * Determines whether occurrences of the subtype should be replaced by the
	 * supertype.
	 *
	 * @param replace
	 *            <code>true</code> to replace occurrences where possible,
	 *            <code>false</code> otherwise
	 */
	public final void setReplace(final boolean replace) {
		fReplace= replace;
	}

	/**
	 * Solves the supertype constraints to replace subtype by a supertype.
	 *
	 * @param subUnit
	 *            the compilation unit of the subtype, or <code>null</code>
	 * @param subNode
	 *            the compilation unit node of the subtype, or <code>null</code>
	 * @param subType
	 *            the java element of the subtype
	 * @param subBinding
	 *            the type binding of the subtype to replace
	 * @param superBinding
	 *            the type binding of the supertype to use as replacement
	 * @param monitor
	 *            the progress monitor to use
	 * @param status
	 *            the refactoring status
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	protected final void solveSuperTypeConstraints(final ICompilationUnit subUnit, final CompilationUnit subNode, final IType subType, final ITypeBinding subBinding, final ITypeBinding superBinding, final IProgressMonitor monitor, final RefactoringStatus status) throws JavaModelException {
		Assert.isNotNull(subType);
		Assert.isNotNull(subBinding);
		Assert.isNotNull(superBinding);
		Assert.isNotNull(monitor);
		Assert.isNotNull(status);
		int level= 3;
		TypeEnvironment environment= new TypeEnvironment();
		final SuperTypeConstraintsModel model= new SuperTypeConstraintsModel(environment, environment.create(subBinding), environment.create(superBinding));
		final SuperTypeConstraintsCreator creator= new SuperTypeConstraintsCreator(model, fInstanceOf);
		try {
			monitor.beginTask("", 300); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
			final Map<IJavaProject, Set<SearchResultGroup>> firstPass= getReferencingCompilationUnits(subType, new SubProgressMonitor(monitor, 100), status);
			final Map<IJavaProject, Set<ICompilationUnit>> secondPass= new HashMap<IJavaProject, Set<ICompilationUnit>>();
			IJavaProject project= null;
			Collection<SearchResultGroup> collection= null;
			try {
				final ASTParser parser= ASTParser.newParser(ASTProvider.SHARED_AST_LEVEL);
				Object element= null;
				ICompilationUnit current= null;
				SearchResultGroup group= null;
				SearchMatch[] matches= null;
				final Map<ICompilationUnit, SearchResultGroup> groups= new HashMap<ICompilationUnit, SearchResultGroup>();
				for (final Iterator<IJavaProject> outer= firstPass.keySet().iterator(); outer.hasNext();) {
					project= outer.next();
					if (level == 3 && !JavaModelUtil.is50OrHigher(project))
						level= 2;
					collection= firstPass.get(project);
					if (collection != null) {
						for (final Iterator<SearchResultGroup> inner= collection.iterator(); inner.hasNext();) {
							group= inner.next();
							matches= group.getSearchResults();
							for (int index= 0; index < matches.length; index++) {
								element= matches[index].getElement();
								if (element instanceof IMember) {
									current= ((IMember) element).getCompilationUnit();
									if (current != null)
										groups.put(current, group);
								}
							}
						}
					}
				}
				Set<ICompilationUnit> units= null;
				final Set<ICompilationUnit> processed= new HashSet<ICompilationUnit>();
				if (subUnit != null)
					processed.add(subUnit);
				model.beginCreation();
				IProgressMonitor subMonitor= new SubProgressMonitor(monitor, 120);
				try {
					final Set<IJavaProject> keySet= firstPass.keySet();
					subMonitor.beginTask("", keySet.size() * 100); //$NON-NLS-1$
					subMonitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
					for (final Iterator<IJavaProject> outer= keySet.iterator(); outer.hasNext();) {
						project= outer.next();
						collection= firstPass.get(project);
						if (collection != null) {
							units= new HashSet<ICompilationUnit>(collection.size());
							for (final Iterator<SearchResultGroup> inner= collection.iterator(); inner.hasNext();) {
								group= inner.next();
								matches= group.getSearchResults();
								for (int index= 0; index < matches.length; index++) {
									element= matches[index].getElement();
									if (element instanceof IMember) {
										current= ((IMember) element).getCompilationUnit();
										if (current != null)
											units.add(current);
									}
								}
							}
							final List<ICompilationUnit> batches= new ArrayList<ICompilationUnit>(units);
							final int size= batches.size();
							final int iterations= (size - 1) / SIZE_BATCH + 1;
							final IProgressMonitor subsubMonitor= new SubProgressMonitor(subMonitor, 100);
							try {
								subsubMonitor.beginTask("", iterations * 100); //$NON-NLS-1$
								subsubMonitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
								final Map<String, String> options= RefactoringASTParser.getCompilerOptions(project);
								for (int index= 0; index < iterations; index++) {
									final List<ICompilationUnit> iteration= batches.subList(index * SIZE_BATCH, Math.min(size, (index + 1) * SIZE_BATCH));
									parser.setWorkingCopyOwner(fOwner);
									parser.setResolveBindings(true);
									parser.setProject(project);
									parser.setCompilerOptions(options);
									final IProgressMonitor subsubsubMonitor= new SubProgressMonitor(subsubMonitor, 100);
									try {
										final int count= iteration.size();
										subsubsubMonitor.beginTask("", count * 100); //$NON-NLS-1$
										subsubsubMonitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
										parser.createASTs(iteration.toArray(new ICompilationUnit[count]), new String[0], new ASTRequestor() {

											@Override
											public final void acceptAST(final ICompilationUnit unit, final CompilationUnit node) {
												if (!processed.contains(unit)) {
													performFirstPass(creator, secondPass, groups, unit, node, new SubProgressMonitor(subsubsubMonitor, 100));
													processed.add(unit);
												} else
													subsubsubMonitor.worked(100);
											}

											@Override
											public final void acceptBinding(final String key, final IBinding binding) {
												// Do nothing
											}
										}, new NullProgressMonitor());
									} finally {
										subsubsubMonitor.done();
									}
								}
							} finally {
								subsubMonitor.done();
							}
						}
					}
				} finally {
					firstPass.clear();
					subMonitor.done();
				}
				if (subUnit != null && subNode != null)
					performFirstPass(creator, secondPass, groups, subUnit, subNode, new SubProgressMonitor(subMonitor, 20));
				subMonitor= new SubProgressMonitor(monitor, 100);
				try {
					final Set<IJavaProject> keySet= secondPass.keySet();
					subMonitor.beginTask("", keySet.size() * 100); //$NON-NLS-1$
					subMonitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
					for (final Iterator<IJavaProject> iterator= keySet.iterator(); iterator.hasNext();) {
						project= iterator.next();
						if (level == 3 && !JavaModelUtil.is50OrHigher(project))
							level= 2;
						Collection<ICompilationUnit> cuCollection= null;
						cuCollection= secondPass.get(project);
						if (cuCollection != null) {
							parser.setWorkingCopyOwner(fOwner);
							parser.setResolveBindings(true);
							parser.setProject(project);
							parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
							final IProgressMonitor subsubMonitor= new SubProgressMonitor(subMonitor, 100);
							try {
								subsubMonitor.beginTask("", cuCollection.size() * 100); //$NON-NLS-1$
								subsubMonitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
								parser.createASTs(cuCollection.toArray(new ICompilationUnit[cuCollection.size()]), new String[0], new ASTRequestor() {

									@Override
									public final void acceptAST(final ICompilationUnit unit, final CompilationUnit node) {
										if (!processed.contains(unit))
											performSecondPass(creator, unit, node, new SubProgressMonitor(subsubMonitor, 100));
										else
											subsubMonitor.worked(100);
									}

									@Override
									public final void acceptBinding(final String key, final IBinding binding) {
										// Do nothing
									}
								}, new NullProgressMonitor());
							} finally {
								subsubMonitor.done();
							}
						}
					}
				} finally {
					secondPass.clear();
					subMonitor.done();
				}
			} finally {
				model.endCreation();
				model.setCompliance(level);
			}
			final SuperTypeConstraintsSolver solver= createContraintSolver(model);
			solver.solveConstraints();
			fTypeOccurrences= solver.getTypeOccurrences();
			fObsoleteCasts= solver.getObsoleteCasts();
		} finally {
			monitor.done();
		}
	}
}

/*******************************************************************************
 * Copyright (c) 2006, 2011 IBM Corporation and others.
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFile;

import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditCopier;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.GroupCategory;
import org.eclipse.ltk.core.refactoring.GroupCategorySet;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.ExtractSuperclassDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;

import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringDescriptorUtil;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreateCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.MultiStateCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextEditBasedChangeManager;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.CodeGeneration;
import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;


/**
 * Refactoring processor for the extract supertype refactoring.
 *
 * @since 3.2
 */
public final class ExtractSupertypeProcessor extends PullUpRefactoringProcessor {

	/** The extract attribute */
	private static final String ATTRIBUTE_EXTRACT= "extract"; //$NON-NLS-1$

	/** The types attribute */
	private static final String ATTRIBUTE_TYPES= "types"; //$NON-NLS-1$

	/** The extract supertype group category set */
	private static final GroupCategorySet SET_EXTRACT_SUPERTYPE= new GroupCategorySet(new GroupCategory("org.eclipse.jdt.internal.corext.extractSupertype", //$NON-NLS-1$
			RefactoringCoreMessages.ExtractSupertypeProcessor_category_name, RefactoringCoreMessages.ExtractSupertypeProcessor_category_description));

	/**
	 * The changes of the working copy layer (element type:
	 * &lt;ICompilationUnit, TextEditBasedChange&gt;)
	 * <p>
	 * The compilation units are all primary working copies or normal
	 * compilation units.
	 * </p>
	 */
	private final Map<ICompilationUnit, CompilationUnitChange> fLayerChanges= new HashMap<ICompilationUnit, CompilationUnitChange>();

	/** The possible extract supertype candidates, or the empty array */
	private IType[] fPossibleCandidates= {};

	/** The source of the supertype */
	private String fSuperSource;

	/** The name of the extracted type */
	private String fTypeName= ""; //$NON-NLS-1$

	/** The types where to extract the supertype */
	private IType[] fTypesToExtract= {};

	/**
	 * Creates a new extract supertype refactoring processor.
	 *
	 * @param members
	 *            the members to extract, or <code>null</code> if invoked by
	 *            scripting
	 * @param settings
	 *            the code generation settings, or <code>null</code> if
	 *            invoked by scripting
	 */
	public ExtractSupertypeProcessor(final IMember[] members, final CodeGenerationSettings settings) {
		super(members, settings, true);
		if (members != null) {
			final IType declaring= getDeclaringType();
			if (declaring != null)
				fTypesToExtract= new IType[] { declaring};
		}
	}

	/**
	 * Creates a new extract supertype refactoring processor from refactoring arguments.
	 *
	 * @param arguments
	 *            the refactoring arguments
	 * @param status
	 *            the resulting status
	 */
	public ExtractSupertypeProcessor(JavaRefactoringArguments arguments, RefactoringStatus status) {
		super(null, null, true);
		RefactoringStatus initializeStatus= initialize(arguments);
		status.merge(initializeStatus);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getProcessorName() {
		return RefactoringCoreMessages.ExtractSupertypeProcessor_extract_supertype;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final RefactoringStatus checkDeclaringSuperTypes(final IProgressMonitor monitor) throws JavaModelException {
		return new RefactoringStatus();
	}

	@Override
	protected CompilationUnitRewrite getCompilationUnitRewrite(final Map<ICompilationUnit, CompilationUnitRewrite> rewrites, final ICompilationUnit unit) {
		Assert.isNotNull(rewrites);
		Assert.isNotNull(unit);
		CompilationUnitRewrite rewrite= rewrites.get(unit);
		if (rewrite == null) {
			rewrite= new CompilationUnitRewrite(fOwner, unit);
			rewrite.rememberContent();
			rewrites.put(unit, rewrite);
		}
		return rewrite;
	}

	/**
	 * Checks whether the compilation unit to be extracted is valid.
	 *
	 * @return a status describing the outcome of the
	 */
	public RefactoringStatus checkExtractedCompilationUnit() {
		final RefactoringStatus status= new RefactoringStatus();
		final ICompilationUnit cu= getDeclaringType().getCompilationUnit();
		if (fTypeName == null || "".equals(fTypeName)) //$NON-NLS-1$
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.Checks_Choose_name);
		status.merge(Checks.checkTypeName(fTypeName, cu));
		if (status.hasFatalError())
			return status;
		status.merge(Checks.checkCompilationUnitName(JavaModelUtil.getRenamedCUName(cu, fTypeName), cu));
		if (status.hasFatalError())
			return status;
		status.merge(Checks.checkCompilationUnitNewName(cu, fTypeName));
		return status;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RefactoringStatus checkFinalConditions(final IProgressMonitor monitor, final CheckConditionsContext context) throws CoreException, OperationCanceledException {
		final RefactoringStatus status= new RefactoringStatus();
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractSupertypeProcessor_checking);
			status.merge(checkExtractedCompilationUnit());
			if (status.hasFatalError())
				return status;
			return super.checkFinalConditions(new SubProgressMonitor(monitor, 1), context);
		} finally {
			monitor.done();
		}
	}

	/**
	 * Computes the destination type based on the new name.
	 *
	 * @param name the new name
	 * @return the destination type
	 */
	public IType computeExtractedType(final String name) {
		if (name != null && !name.equals("")) {//$NON-NLS-1$
			final IType declaring= getDeclaringType();
			try {
				final ICompilationUnit[] units= declaring.getPackageFragment().getCompilationUnits(fOwner);
				final String newName= JavaModelUtil.getRenamedCUName(declaring.getCompilationUnit(), name);
				ICompilationUnit result= null;
				for (int index= 0; index < units.length; index++) {
					if (units[index].getElementName().equals(newName))
						result= units[index];
				}
				if (result != null) {
					final IType type= result.getType(name);
					setDestinationType(type);
					return type;
				}
			} catch (JavaModelException exception) {
				JavaPlugin.log(exception);
			}
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Change createChange(final IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		try {
			final Map<String, String> arguments= new HashMap<String, String>();
			String project= null;
			final IType declaring= getDeclaringType();
			final IJavaProject javaProject= declaring.getJavaProject();
			if (javaProject != null)
				project= javaProject.getElementName();
			int flags= JavaRefactoringDescriptor.JAR_MIGRATION | JavaRefactoringDescriptor.JAR_REFACTORING | RefactoringDescriptor.STRUCTURAL_CHANGE | RefactoringDescriptor.MULTI_CHANGE;
			try {
				if (declaring.isLocal() || declaring.isAnonymous())
					flags|= JavaRefactoringDescriptor.JAR_SOURCE_ATTACHMENT;
			} catch (JavaModelException exception) {
				JavaPlugin.log(exception);
			}
			final String description= Messages.format(RefactoringCoreMessages.ExtractSupertypeProcessor_descriptor_description_short, BasicElementLabels.getJavaElementName(fTypeName));
			final String header= Messages.format(RefactoringCoreMessages.ExtractSupertypeProcessor_descriptor_description, new String[] { JavaElementLabels.getElementLabel(fDestinationType, JavaElementLabels.ALL_FULLY_QUALIFIED), JavaElementLabels.getElementLabel(fCachedDeclaringType, JavaElementLabels.ALL_FULLY_QUALIFIED)});
			final JDTRefactoringDescriptorComment comment= new JDTRefactoringDescriptorComment(project, this, header);
			final IType[] types= getTypesToExtract();
			String[] settings= new String[types.length];
			for (int index= 0; index < settings.length; index++)
				settings[index]= JavaElementLabels.getElementLabel(types[index], JavaElementLabels.ALL_FULLY_QUALIFIED);
			comment.addSetting(JDTRefactoringDescriptorComment.createCompositeSetting(RefactoringCoreMessages.ExtractSupertypeProcessor_subtypes_pattern, settings));
			comment.addSetting(Messages.format(RefactoringCoreMessages.ExtractSupertypeProcessor_refactored_element_pattern, JavaElementLabels.getElementLabel(fDestinationType, JavaElementLabels.ALL_FULLY_QUALIFIED)));
			settings= new String[fMembersToMove.length];
			for (int index= 0; index < settings.length; index++)
				settings[index]= JavaElementLabels.getElementLabel(fMembersToMove[index], JavaElementLabels.ALL_FULLY_QUALIFIED);
			comment.addSetting(JDTRefactoringDescriptorComment.createCompositeSetting(RefactoringCoreMessages.ExtractInterfaceProcessor_extracted_members_pattern, settings));
			addSuperTypeSettings(comment, true);
			final ExtractSuperclassDescriptor descriptor= RefactoringSignatureDescriptorFactory.createExtractSuperclassDescriptor(project, description, comment.asString(), arguments, flags);
			arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME, fTypeName);
			arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT, JavaRefactoringDescriptorUtil.elementToHandle(project, getDeclaringType()));
			arguments.put(ATTRIBUTE_REPLACE, Boolean.valueOf(fReplace).toString());
			arguments.put(ATTRIBUTE_INSTANCEOF, Boolean.valueOf(fInstanceOf).toString());
			arguments.put(ATTRIBUTE_STUBS, Boolean.valueOf(fCreateMethodStubs).toString());
			arguments.put(ATTRIBUTE_EXTRACT, new Integer(fMembersToMove.length).toString());
			for (int offset= 0; offset < fMembersToMove.length; offset++)
				arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + (offset + 1), JavaRefactoringDescriptorUtil.elementToHandle(project, fMembersToMove[offset]));
			arguments.put(ATTRIBUTE_DELETE, new Integer(fDeletedMethods.length).toString());
			for (int offset= 0; offset < fDeletedMethods.length; offset++)
				arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + (offset + fMembersToMove.length + 1), JavaRefactoringDescriptorUtil.elementToHandle(project, fDeletedMethods[offset]));
			arguments.put(ATTRIBUTE_ABSTRACT, new Integer(fAbstractMethods.length).toString());
			for (int offset= 0; offset < fAbstractMethods.length; offset++)
				arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + (offset + fMembersToMove.length + fDeletedMethods.length + 1), JavaRefactoringDescriptorUtil.elementToHandle(project, fAbstractMethods[offset]));
			arguments.put(ATTRIBUTE_TYPES, new Integer(fTypesToExtract.length).toString());
			for (int offset= 0; offset < fTypesToExtract.length; offset++)
				arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + (offset + fMembersToMove.length + fDeletedMethods.length + fAbstractMethods.length + 1), JavaRefactoringDescriptorUtil.elementToHandle(project, fTypesToExtract[offset]));
			final DynamicValidationRefactoringChange change= new DynamicValidationRefactoringChange(descriptor, RefactoringCoreMessages.ExtractSupertypeProcessor_extract_supertype, fChangeManager.getAllChanges());
			final IFile file= ResourceUtil.getFile(declaring.getCompilationUnit());
			if (fSuperSource != null && fSuperSource.length() > 0)
				change.add(new CreateCompilationUnitChange(declaring.getPackageFragment().getCompilationUnit(JavaModelUtil.getRenamedCUName(declaring.getCompilationUnit(), fTypeName)), fSuperSource, file.getCharset(false)));
			return change;
		} finally {
			monitor.done();
			clearCaches();
		}
	}

	/**
	 * Creates the new extracted supertype.
	 *
	 * @param superType
	 *            the super type, or <code>null</code> if no super type (ie.
	 *            <code>java.lang.Object</code>) is available
	 * @param monitor
	 *            the progress monitor
	 * @return a status describing the outcome of the operation
	 * @throws CoreException
	 *             if an error occurs
	 */
	protected final RefactoringStatus createExtractedSuperType(final IType superType, final IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(monitor);
		fSuperSource= null;
		final RefactoringStatus status= new RefactoringStatus();
		try {
			monitor.beginTask(RefactoringCoreMessages.ExtractSupertypeProcessor_preparing, 20);
			final IType declaring= getDeclaringType();
			final CompilationUnitRewrite declaringRewrite= new CompilationUnitRewrite(fOwner, declaring.getCompilationUnit());
			final AbstractTypeDeclaration declaringDeclaration= ASTNodeSearchUtil.getAbstractTypeDeclarationNode(declaring, declaringRewrite.getRoot());
			if (declaringDeclaration != null) {
				final String name= JavaModelUtil.getRenamedCUName(declaring.getCompilationUnit(), fTypeName);
				final ICompilationUnit original= declaring.getPackageFragment().getCompilationUnit(name);
				final ICompilationUnit copy= getSharedWorkingCopy(original.getPrimary(), new SubProgressMonitor(monitor, 10));
				fSuperSource= createSuperTypeSource(copy, superType, declaringDeclaration, status, new SubProgressMonitor(monitor, 10));
				if (fSuperSource != null) {
					copy.getBuffer().setContents(fSuperSource);
					JavaModelUtil.reconcile(copy);
				}
			}
		} finally {
			monitor.done();
		}
		return status;
	}

	/**
	 * Creates a working copy for the modified subtype.
	 *
	 * @param unit
	 *            the compilation unit
	 * @param root
	 *            the compilation unit node
	 * @param subDeclaration
	 *            the declaration of the subtype to modify
	 * @param extractedType
	 *            the extracted super type
	 * @param extractedBinding
	 *            the binding of the extracted super type
	 * @param status
	 *            the refactoring status
	 */
	protected final void createModifiedSubType(final ICompilationUnit unit, final CompilationUnit root, final IType extractedType, final ITypeBinding extractedBinding, final AbstractTypeDeclaration subDeclaration, final RefactoringStatus status) {
		Assert.isNotNull(unit);
		Assert.isNotNull(subDeclaration);
		Assert.isNotNull(extractedType);
		try {
			final CompilationUnitRewrite rewrite= new CompilationUnitRewrite(fOwner, unit, root);
			createTypeSignature(rewrite, subDeclaration, extractedType, extractedBinding, new NullProgressMonitor());
			final Document document= new Document(unit.getBuffer().getContents());
			final CompilationUnitChange change= rewrite.createChange(true);
			if (change != null) {
				fLayerChanges.put(unit.getPrimary(), change);
				final TextEdit edit= change.getEdit();
				if (edit != null) {
					final TextEditCopier copier= new TextEditCopier(edit);
					final TextEdit copy= copier.perform();
					copy.apply(document, TextEdit.NONE);
				}
			}
			final ICompilationUnit copy= getSharedWorkingCopy(unit, new NullProgressMonitor());
			copy.getBuffer().setContents(document.get());
			JavaModelUtil.reconcile(copy);
		} catch (CoreException exception) {
			JavaPlugin.log(exception);
			status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractSupertypeProcessor_unexpected_exception_on_layer));
		} catch (MalformedTreeException exception) {
			JavaPlugin.log(exception);
			status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractSupertypeProcessor_unexpected_exception_on_layer));
		} catch (BadLocationException exception) {
			JavaPlugin.log(exception);
			status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractSupertypeProcessor_unexpected_exception_on_layer));
		}
	}

	/**
	 * Creates the necessary constructors for the extracted supertype.
	 *
	 * @param targetRewrite
	 *            the target compilation unit rewrite
	 * @param superType
	 *            the super type, or <code>null</code> if no super type (ie.
	 *            <code>java.lang.Object</code>) is available
	 * @param targetDeclaration
	 *            the type declaration of the target type
	 * @param status
	 *            the refactoring status
	 */
	protected final void createNecessaryConstructors(final CompilationUnitRewrite targetRewrite, final IType superType, final AbstractTypeDeclaration targetDeclaration, final RefactoringStatus status) {
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(targetDeclaration);
		if (superType != null) {
			final ITypeBinding binding= targetDeclaration.resolveBinding();
			if (binding != null && binding.isClass()) {
				final IMethodBinding[] bindings= StubUtility2.getVisibleConstructors(binding, true, true);
				int deprecationCount= 0;
				for (int i= 0; i < bindings.length; i++) {
					if (bindings[i].isDeprecated())
						deprecationCount++;
				}
				final ListRewrite rewrite= targetRewrite.getASTRewrite().getListRewrite(targetDeclaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
				if (rewrite != null) {
					boolean createDeprecated= deprecationCount == bindings.length;
					for (int i= 0; i < bindings.length; i++) {
						IMethodBinding curr= bindings[i];
						if (!curr.isDeprecated() || createDeprecated) {
							MethodDeclaration stub;
							try {
								ImportRewriteContext context= new ContextSensitiveImportRewriteContext(targetDeclaration, targetRewrite.getImportRewrite());
								stub= StubUtility2.createConstructorStub(targetRewrite.getCu(), targetRewrite.getASTRewrite(), targetRewrite.getImportRewrite(), context, curr, binding.getName(),
										Modifier.PUBLIC, false, false, fSettings);
								if (stub != null)
									rewrite.insertLast(stub, null);
							} catch (CoreException exception) {
								JavaPlugin.log(exception);
								status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractSupertypeProcessor_unexpected_exception_on_layer));
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Creates the source for the new compilation unit containing the supertype.
	 *
	 * @param extractedWorkingCopy
	 *            the working copy of the new extracted supertype
	 * @param superType
	 *            the super type, or <code>null</code> if no super type (ie.
	 *            <code>java.lang.Object</code>) is available
	 * @param declaringDeclaration
	 *            the declaration of the declaring type
	 * @param status
	 *            the refactoring status
	 * @param monitor
	 *            the progress monitor to display progress
	 * @return the source of the new compilation unit, or <code>null</code>
	 * @throws CoreException
	 *             if an error occurs
	 */
	protected final String createSuperTypeSource(final ICompilationUnit extractedWorkingCopy, final IType superType, final AbstractTypeDeclaration declaringDeclaration, final RefactoringStatus status, final IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(extractedWorkingCopy);
		Assert.isNotNull(declaringDeclaration);
		Assert.isNotNull(status);
		Assert.isNotNull(monitor);
		String source= null;
		try {
			monitor.beginTask("", 2); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractSupertypeProcessor_preparing);
			final IType declaring= getDeclaringType();
			final String delimiter= StubUtility.getLineDelimiterUsed(extractedWorkingCopy.getJavaProject());
			String typeComment= null;
			String fileComment= null;
			if (fSettings.createComments) {
				final ITypeParameter[] parameters= declaring.getTypeParameters();
				final String[] names= new String[parameters.length];
				for (int index= 0; index < parameters.length; index++)
					names[index]= parameters[index].getElementName();
				typeComment= CodeGeneration.getTypeComment(extractedWorkingCopy, fTypeName, names, delimiter);
				fileComment= CodeGeneration.getFileComment(extractedWorkingCopy, delimiter);
			}
			final StringBuffer buffer= new StringBuffer(64);
			final ITypeBinding binding= declaringDeclaration.resolveBinding();
			if (binding != null) {
				final ITypeBinding superBinding= binding.getSuperclass();
				if (superBinding != null)
					fTypeBindings.add(superBinding);
				final ITypeBinding[] bindings= binding.getInterfaces();
				for (int i= 0; i < bindings.length; i++) {
	                fTypeBindings.add(bindings[i]);
                }
			}
			final String imports= createTypeImports(extractedWorkingCopy, monitor);
			if (imports != null && !"".equals(imports)) { //$NON-NLS-1$
				buffer.append(imports);
			}
			createTypeDeclaration(extractedWorkingCopy, superType, declaringDeclaration, typeComment, buffer, status, new SubProgressMonitor(monitor, 1));
			source= createTypeTemplate(extractedWorkingCopy, "", fileComment, "", buffer.toString()); //$NON-NLS-1$ //$NON-NLS-2$
			if (source == null) {
				if (!declaring.getPackageFragment().isDefaultPackage()) {
					if (imports.length() > 0)
						buffer.insert(0, imports);
					buffer.insert(0, "package " + declaring.getPackageFragment().getElementName() + ";"); //$NON-NLS-1$//$NON-NLS-2$
				}
				source= buffer.toString();
			}
			final IDocument document= new Document(source);
			final TextEdit edit= CodeFormatterUtil.format2(CodeFormatter.K_COMPILATION_UNIT, source, 0, delimiter, extractedWorkingCopy.getJavaProject().getOptions(true));
			if (edit != null) {
				try {
					edit.apply(document, TextEdit.UPDATE_REGIONS);
				} catch (MalformedTreeException exception) {
					JavaPlugin.log(exception);
					status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractSupertypeProcessor_unexpected_exception_on_layer));
				} catch (BadLocationException exception) {
					JavaPlugin.log(exception);
					status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractSupertypeProcessor_unexpected_exception_on_layer));
				}
				source= document.get();
			}
		} finally {
			monitor.done();
		}
		return source;
	}

	/**
	 * Creates the declaration of the new supertype, excluding any comments or
	 * package declaration.
	 *
	 * @param extractedWorkingCopy
	 *            the working copy of the new extracted supertype
	 * @param superType
	 *            the super type, or <code>null</code> if no super type (ie.
	 *            <code>java.lang.Object</code>) is available
	 * @param declaringDeclaration
	 *            the declaration of the declaring type
	 * @param comment
	 *            the comment of the new type declaration
	 * @param buffer
	 *            the string buffer containing the declaration
	 * @param status
	 *            the refactoring status
	 * @param monitor
	 *            the progress monitor to use
	 * @throws CoreException
	 *             if an error occurs
	 */
	protected final void createTypeDeclaration(final ICompilationUnit extractedWorkingCopy, final IType superType, final AbstractTypeDeclaration declaringDeclaration, final String comment, final StringBuffer buffer, final RefactoringStatus status, final IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(extractedWorkingCopy);
		Assert.isNotNull(declaringDeclaration);
		Assert.isNotNull(buffer);
		Assert.isNotNull(status);
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractSupertypeProcessor_preparing);
			final IJavaProject project= extractedWorkingCopy.getJavaProject();
			final String delimiter= StubUtility.getLineDelimiterUsed(project);
			if (comment != null && !"".equals(comment)) { //$NON-NLS-1$
				buffer.append(comment);
				buffer.append(delimiter);
			}
			buffer.append(JdtFlags.VISIBILITY_STRING_PUBLIC);
			if (superType != null && Flags.isAbstract(superType.getFlags())) {
				buffer.append(' ');
				buffer.append("abstract "); //$NON-NLS-1$
			}
			buffer.append(' ');
			buffer.append("class "); //$NON-NLS-1$
			buffer.append(fTypeName);
			if (superType != null && !"java.lang.Object".equals(superType.getFullyQualifiedName())) { //$NON-NLS-1$
				buffer.append(' ');
				if (superType.isInterface())
					buffer.append("implements "); //$NON-NLS-1$
				else
					buffer.append("extends "); //$NON-NLS-1$
				buffer.append(superType.getElementName());
			}
			buffer.append(" {"); //$NON-NLS-1$
			buffer.append(delimiter);
			buffer.append(delimiter);
			buffer.append('}');
			final String string= buffer.toString();
			extractedWorkingCopy.getBuffer().setContents(string);
			final IDocument document= new Document(string);
			final CompilationUnitRewrite targetRewrite= new CompilationUnitRewrite(fOwner, extractedWorkingCopy);
			final AbstractTypeDeclaration targetDeclaration= (AbstractTypeDeclaration) targetRewrite.getRoot().types().get(0);
			createTypeParameters(targetRewrite, superType, declaringDeclaration, targetDeclaration);
			createTypeSignature(targetRewrite, superType, declaringDeclaration, targetDeclaration);
			createNecessaryConstructors(targetRewrite, superType, targetDeclaration, status);
			final TextEdit edit= targetRewrite.createChange(true).getEdit();
			try {
				edit.apply(document, TextEdit.UPDATE_REGIONS);
			} catch (MalformedTreeException exception) {
				JavaPlugin.log(exception);
				status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractSupertypeProcessor_unexpected_exception_on_layer));
			} catch (BadLocationException exception) {
				JavaPlugin.log(exception);
				status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractSupertypeProcessor_unexpected_exception_on_layer));
			}
			buffer.setLength(0);
			buffer.append(document.get());
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
	protected final void createTypeParameters(final CompilationUnitRewrite targetRewrite, final IType subType, final AbstractTypeDeclaration sourceDeclaration, final AbstractTypeDeclaration targetDeclaration) {
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(sourceDeclaration);
		Assert.isNotNull(targetDeclaration);
		if (sourceDeclaration instanceof TypeDeclaration) {
			TypeParameter parameter= null;
			final ListRewrite rewrite= targetRewrite.getASTRewrite().getListRewrite(targetDeclaration, TypeDeclaration.TYPE_PARAMETERS_PROPERTY);
			for (final Iterator<TypeParameter> iterator= ((TypeDeclaration) sourceDeclaration).typeParameters().iterator(); iterator.hasNext();) {
				parameter= iterator.next();
				final ASTNode node= ASTNode.copySubtree(targetRewrite.getAST(), parameter);
				rewrite.insertLast(node, null);
			}
		}
	}

	/**
	 * Creates a new type signature of a subtype.
	 *
	 * @param subRewrite
	 *            the compilation unit rewrite of a subtype
	 * @param declaration
	 *            the type declaration of a subtype
	 * @param extractedType
	 *            the extracted super type
	 * @param extractedBinding
	 *            the binding of the extracted super type
	 * @param monitor
	 *            the progress monitor to use
	 * @throws JavaModelException
	 *             if the type parameters cannot be retrieved
	 */
	protected final void createTypeSignature(final CompilationUnitRewrite subRewrite, final AbstractTypeDeclaration declaration, final IType extractedType, final ITypeBinding extractedBinding, final IProgressMonitor monitor) throws JavaModelException {
		Assert.isNotNull(subRewrite);
		Assert.isNotNull(declaration);
		Assert.isNotNull(extractedType);
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask(RefactoringCoreMessages.ExtractSupertypeProcessor_preparing, 10);
			final AST ast= subRewrite.getAST();
			Type type= null;
			if (extractedBinding != null) {
				type= subRewrite.getImportRewrite().addImport(extractedBinding, ast);
			} else {
				subRewrite.getImportRewrite().addImport(extractedType.getFullyQualifiedName('.'));
				type= ast.newSimpleType(ast.newSimpleName(extractedType.getElementName()));
			}
			subRewrite.getImportRemover().registerAddedImport(extractedType.getFullyQualifiedName('.'));
			if (type != null) {
				final ITypeParameter[] parameters= extractedType.getTypeParameters();
				if (parameters.length > 0) {
					final ParameterizedType parameterized= ast.newParameterizedType(type);
					for (int index= 0; index < parameters.length; index++)
						parameterized.typeArguments().add(ast.newSimpleType(ast.newSimpleName(parameters[index].getElementName())));
					type= parameterized;
				}
			}
			final ASTRewrite rewriter= subRewrite.getASTRewrite();
			if (type != null && declaration instanceof TypeDeclaration) {
				final TypeDeclaration extended= (TypeDeclaration) declaration;
				final Type superClass= extended.getSuperclassType();
				if (superClass != null) {
					rewriter.replace(superClass, type, subRewrite.createCategorizedGroupDescription(RefactoringCoreMessages.ExtractSupertypeProcessor_add_supertype, SET_EXTRACT_SUPERTYPE));
					subRewrite.getImportRemover().registerRemovedNode(superClass);
				} else
					rewriter.set(extended, TypeDeclaration.SUPERCLASS_TYPE_PROPERTY, type, subRewrite.createCategorizedGroupDescription(RefactoringCoreMessages.ExtractSupertypeProcessor_add_supertype, SET_EXTRACT_SUPERTYPE));
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Creates the type signature of the extracted supertype.
	 *
	 * @param targetRewrite
	 *            the target compilation unit rewrite
	 * @param superType
	 *            the super type, or <code>null</code> if no super type (ie.
	 *            <code>java.lang.Object</code>) is available
	 * @param declaringDeclaration
	 *            the declaration of the declaring type
	 * @param targetDeclaration
	 *            the type declaration of the target type
	 */
	protected final void createTypeSignature(final CompilationUnitRewrite targetRewrite, final IType superType, final AbstractTypeDeclaration declaringDeclaration, final AbstractTypeDeclaration targetDeclaration) {
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(declaringDeclaration);
		Assert.isNotNull(targetDeclaration);
		if (declaringDeclaration instanceof TypeDeclaration) {
			final TypeDeclaration declaration= (TypeDeclaration) declaringDeclaration;
			final Type superclassType= declaration.getSuperclassType();
			if (superclassType != null) {
				Type type= null;
				final ITypeBinding binding= superclassType.resolveBinding();
				if (binding != null) {
					type= targetRewrite.getImportRewrite().addImport(binding, targetRewrite.getAST());
					targetRewrite.getImportRemover().registerAddedImports(type);
				}
				if (type != null && targetDeclaration instanceof TypeDeclaration) {
					final TypeDeclaration extended= (TypeDeclaration) targetDeclaration;
					final Type targetSuperType= extended.getSuperclassType();
					if (targetSuperType != null) {
						targetRewrite.getASTRewrite().replace(targetSuperType, type, null);
					} else {
						targetRewrite.getASTRewrite().set(extended, TypeDeclaration.SUPERCLASS_TYPE_PROPERTY, type, null);
					}
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final RefactoringStatus createWorkingCopyLayer(final IProgressMonitor monitor) {
		Assert.isNotNull(monitor);
		final RefactoringStatus status= new RefactoringStatus();
		try {
			monitor.beginTask(RefactoringCoreMessages.ExtractSupertypeProcessor_preparing, 70);
			status.merge(super.createWorkingCopyLayer(new SubProgressMonitor(monitor, 10)));
			final IType declaring= getDeclaringType();
			status.merge(createExtractedSuperType(getDeclaringSuperTypeHierarchy(new SubProgressMonitor(monitor, 10)).getSuperclass(declaring), new SubProgressMonitor(monitor, 10)));
			if (status.hasFatalError())
				return status;
			final IType extractedType= computeExtractedType(fTypeName);
			setDestinationType(extractedType);
			final List<IType> subTypes= new ArrayList<IType>(Arrays.asList(fTypesToExtract));
			if (!subTypes.contains(declaring))
				subTypes.add(declaring);
			final Map<ICompilationUnit, Collection<IType>> unitToTypes= new HashMap<ICompilationUnit, Collection<IType>>(subTypes.size());
			final Set<ICompilationUnit> units= new HashSet<ICompilationUnit>(subTypes.size());
			for (int index= 0; index < subTypes.size(); index++) {
				final IType type= subTypes.get(index);
				final ICompilationUnit unit= type.getCompilationUnit();
				units.add(unit);
				Collection<IType> collection= unitToTypes.get(unit);
				if (collection == null) {
					collection= new ArrayList<IType>(2);
					unitToTypes.put(unit, collection);
				}
				collection.add(type);
			}
			final Map<IJavaProject, Collection<ICompilationUnit>> projectToUnits= new HashMap<IJavaProject, Collection<ICompilationUnit>>();
			Collection<ICompilationUnit> collection= null;
			IJavaProject project= null;
			ICompilationUnit current= null;
			for (final Iterator<ICompilationUnit> iterator= units.iterator(); iterator.hasNext();) {
				current= iterator.next();
				project= current.getJavaProject();
				collection= projectToUnits.get(project);
				if (collection == null) {
					collection= new ArrayList<ICompilationUnit>();
					projectToUnits.put(project, collection);
				}
				collection.add(current);
			}
			final ITypeBinding[] extractBindings= { null};
			final ASTParser extractParser= ASTParser.newParser(ASTProvider.SHARED_AST_LEVEL);
			extractParser.setWorkingCopyOwner(fOwner);
			extractParser.setResolveBindings(true);
			extractParser.setProject(project);
			extractParser.setSource(extractedType.getCompilationUnit());
			final CompilationUnit extractUnit= (CompilationUnit) extractParser.createAST(new SubProgressMonitor(monitor, 10));
			if (extractUnit != null) {
				final AbstractTypeDeclaration extractDeclaration= ASTNodeSearchUtil.getAbstractTypeDeclarationNode(extractedType, extractUnit);
				if (extractDeclaration != null)
					extractBindings[0]= extractDeclaration.resolveBinding();
			}
			final ASTParser parser= ASTParser.newParser(ASTProvider.SHARED_AST_LEVEL);
			final IProgressMonitor subMonitor= new SubProgressMonitor(monitor, 30);
			try {
				final Set<IJavaProject> keySet= projectToUnits.keySet();
				subMonitor.beginTask("", keySet.size()); //$NON-NLS-1$
				subMonitor.setTaskName(RefactoringCoreMessages.ExtractSupertypeProcessor_preparing);
				for (final Iterator<IJavaProject> iterator= keySet.iterator(); iterator.hasNext();) {
					project= iterator.next();
					collection= projectToUnits.get(project);
					parser.setWorkingCopyOwner(fOwner);
					parser.setResolveBindings(true);
					parser.setProject(project);
					parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
					final IProgressMonitor subsubMonitor= new SubProgressMonitor(subMonitor, 1);
					try {
						subsubMonitor.beginTask("", collection.size()); //$NON-NLS-1$
						subsubMonitor.setTaskName(RefactoringCoreMessages.ExtractSupertypeProcessor_preparing);
						parser.createASTs(collection.toArray(new ICompilationUnit[collection.size()]), new String[0], new ASTRequestor() {

							@Override
							public final void acceptAST(final ICompilationUnit unit, final CompilationUnit node) {
								try {
									final Collection<IType> types= unitToTypes.get(unit);
									if (types != null) {
										for (final Iterator<IType> innerIterator= types.iterator(); innerIterator.hasNext();) {
											final IType currentType= innerIterator.next();
											final AbstractTypeDeclaration currentDeclaration= ASTNodeSearchUtil.getAbstractTypeDeclarationNode(currentType, node);
											if (currentDeclaration != null)
												createModifiedSubType(unit, node, extractedType, extractBindings[0], currentDeclaration, status);
										}
									}
								} catch (CoreException exception) {
									JavaPlugin.log(exception);
									status.merge(RefactoringStatus.createFatalErrorStatus(exception.getLocalizedMessage()));
								} finally {
									subsubMonitor.worked(1);
								}
							}

							@Override
							public final void acceptBinding(final String key, final IBinding binding) {
								// Do nothing
							}
						}, subsubMonitor);
					} finally {
						subsubMonitor.done();
					}
				}
			} finally {
				subMonitor.done();
			}
		} catch (CoreException exception) {
			JavaPlugin.log(exception);
			status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractSupertypeProcessor_unexpected_exception_on_layer));
		} finally {
			monitor.done();
		}
		return status;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IType[] getCandidateTypes(final RefactoringStatus status, final IProgressMonitor monitor) {
		Assert.isNotNull(monitor);
		if (fPossibleCandidates == null || fPossibleCandidates.length == 0) {
			final IType declaring= getDeclaringType();
			if (declaring != null) {
				try {
					monitor.beginTask(RefactoringCoreMessages.ExtractSupertypeProcessor_computing_possible_types, 10);
					final IType superType= getDeclaringSuperTypeHierarchy(new SubProgressMonitor(monitor, 1, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL)).getSuperclass(declaring);
					if (superType != null) {
						fPossibleCandidates= superType.newTypeHierarchy(fOwner, new SubProgressMonitor(monitor, 9, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL)).getSubtypes(superType);
						final LinkedList<IType> list= new LinkedList<IType>(Arrays.asList(fPossibleCandidates));
						final Set<String> names= new HashSet<String>();
						for (final Iterator<IType> iterator= list.iterator(); iterator.hasNext();) {
							final IType type= iterator.next();
							if (type.isReadOnly() || type.isBinary() || type.isAnonymous() || !type.isClass() || names.contains(type.getFullyQualifiedName()))
								iterator.remove();
							else
								names.add(type.getFullyQualifiedName());
						}
						fPossibleCandidates= list.toArray(new IType[list.size()]);
					}
				} catch (JavaModelException exception) {
					JavaPlugin.log(exception);
				} finally {
					monitor.done();
				}
			}
		}
		return fPossibleCandidates;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object[] getElements() {
		return new Object[] { getDeclaringType()};
	}

	/**
	 * Returns the extracted type.
	 *
	 * @return the extracted type, or <code>null</code>
	 */
	public IType getExtractedType() {
		return getDestinationType();
	}

	/**
	 * Returns the type name.
	 *
	 * @return the type name
	 */
	public String getTypeName() {
		return fTypeName;
	}

	/**
	 * Returns the types to extract. The declaring type may or may not be
	 * contained in the result.
	 *
	 * @return the types to extract
	 */
	public IType[] getTypesToExtract() {
		return fTypesToExtract;
	}

	private RefactoringStatus initialize(JavaRefactoringArguments extended) {
		final String name= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME);
		if (name != null && !"".equals(name)) //$NON-NLS-1$
			fTypeName= name;
		else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME));
		String handle= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT);
		if (handle != null) {
			final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(extended.getProject(), handle, false);
			if (element == null || element.getElementType() != IJavaElement.TYPE)
				return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getProcessorName(), IJavaRefactorings.EXTRACT_SUPERCLASS);
			IType type= null;
			final ICompilationUnit unit= ((IType) element).getCompilationUnit();
			if (unit != null && unit.exists()) {
				try {
					final ICompilationUnit copy= getSharedWorkingCopy(unit, new NullProgressMonitor());
					final IJavaElement[] elements= copy.findElements(element);
					if (elements != null && elements.length == 1 && elements[0] instanceof IType && elements[0].exists())
						type= (IType) elements[0];
				} catch (JavaModelException exception) {
					// TODO: log exception
				}
			}
			if (type != null)
				fCachedDeclaringType= type;
			else
				return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getProcessorName(), IJavaRefactorings.EXTRACT_SUPERCLASS);
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT));
		final String stubs= extended.getAttribute(ATTRIBUTE_STUBS);
		if (stubs != null) {
			fCreateMethodStubs= Boolean.valueOf(stubs).booleanValue();
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_STUBS));
		final String instance= extended.getAttribute(ATTRIBUTE_INSTANCEOF);
		if (instance != null) {
			fInstanceOf= Boolean.valueOf(instance).booleanValue();
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_INSTANCEOF));
		final String replace= extended.getAttribute(ATTRIBUTE_REPLACE);
		if (replace != null) {
			fReplace= Boolean.valueOf(replace).booleanValue();
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_REPLACE));
		int extractCount= 0;
		int abstractCount= 0;
		int deleteCount= 0;
		int typeCount= 0;
		String value= extended.getAttribute(ATTRIBUTE_ABSTRACT);
		if (value != null && !"".equals(value)) {//$NON-NLS-1$
			try {
				abstractCount= Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_ABSTRACT));
			}
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_ABSTRACT));
		value= extended.getAttribute(ATTRIBUTE_DELETE);
		if (value != null && !"".equals(value)) {//$NON-NLS-1$
			try {
				deleteCount= Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_DELETE));
			}
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_DELETE));
		value= extended.getAttribute(ATTRIBUTE_EXTRACT);
		if (value != null && !"".equals(value)) {//$NON-NLS-1$
			try {
				extractCount= Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_EXTRACT));
			}
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_EXTRACT));
		value= extended.getAttribute(ATTRIBUTE_TYPES);
		if (value != null && !"".equals(value)) {//$NON-NLS-1$
			try {
				typeCount= Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_TYPES));
			}
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_TYPES));
		final RefactoringStatus status= new RefactoringStatus();
		List<IJavaElement> elements= new ArrayList<IJavaElement>();
		for (int index= 0; index < extractCount; index++) {
			final String attribute= JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + (index + 1);
			handle= extended.getAttribute(attribute);
			if (handle != null && !"".equals(handle)) { //$NON-NLS-1$
				final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(fOwner, extended.getProject(), handle, false);
				if (element == null || !element.exists())
					status.merge(JavaRefactoringDescriptorUtil.createInputWarningStatus(element, getProcessorName(), IJavaRefactorings.EXTRACT_SUPERCLASS));
				else
					elements.add(element);
			} else
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, attribute));
		}
		fMembersToMove= elements.toArray(new IMember[elements.size()]);
		elements= new ArrayList<IJavaElement>();
		for (int index= 0; index < deleteCount; index++) {
			final String attribute= JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + (extractCount + index + 1);
			handle= extended.getAttribute(attribute);
			if (handle != null && !"".equals(handle)) { //$NON-NLS-1$
				final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(fOwner, extended.getProject(), handle, false);
				if (element == null || !element.exists())
					status.merge(JavaRefactoringDescriptorUtil.createInputWarningStatus(element, getProcessorName(), IJavaRefactorings.EXTRACT_SUPERCLASS));
				else
					elements.add(element);
			} else
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, attribute));
		}
		fDeletedMethods= elements.toArray(new IMethod[elements.size()]);
		elements= new ArrayList<IJavaElement>();
		for (int index= 0; index < abstractCount; index++) {
			final String attribute= JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + (extractCount + abstractCount + index + 1);
			handle= extended.getAttribute(attribute);
			if (handle != null && !"".equals(handle)) { //$NON-NLS-1$
				final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(fOwner, extended.getProject(), handle, false);
				if (element == null || !element.exists())
					status.merge(JavaRefactoringDescriptorUtil.createInputWarningStatus(element, getProcessorName(), IJavaRefactorings.EXTRACT_SUPERCLASS));
				else
					elements.add(element);
			} else
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, attribute));
		}
		fAbstractMethods= elements.toArray(new IMethod[elements.size()]);
		elements= new ArrayList<IJavaElement>();
		for (int index= 0; index < typeCount; index++) {
			final String attribute= JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + (extractCount + abstractCount + deleteCount + index + 1);
			handle= extended.getAttribute(attribute);
			if (handle != null && !"".equals(handle)) { //$NON-NLS-1$
				final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(fOwner, extended.getProject(), handle, false);
				if (element == null || !element.exists())
					status.merge(JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getProcessorName(), IJavaRefactorings.EXTRACT_SUPERCLASS));
				else
					elements.add(element);
			} else
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, attribute));
		}
		fTypesToExtract= elements.toArray(new IType[elements.size()]);
		IJavaProject project= null;
		if (fMembersToMove.length > 0)
			project= fMembersToMove[0].getJavaProject();
		fSettings= JavaPreferencesSettings.getCodeGenerationSettings(project);
		if (!status.isOK())
			return status;
		return new RefactoringStatus();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void registerChanges(final TextEditBasedChangeManager manager) throws CoreException {
		try {
			final ICompilationUnit extractedUnit= getExtractedType().getCompilationUnit();
			ICompilationUnit unit= null;
			CompilationUnitRewrite rewrite= null;
			for (final Iterator<ICompilationUnit> iterator= fCompilationUnitRewrites.keySet().iterator(); iterator.hasNext();) {
				unit= iterator.next();
				if (unit.equals(extractedUnit)) {
					rewrite= fCompilationUnitRewrites.get(unit);
					if (rewrite != null) {
						CompilationUnitChange change= rewrite.createChange(true);

						if (change != null) {
							final TextEdit edit= ((TextChange) change).getEdit();
							if (edit != null) {
								final IDocument document= new Document(fSuperSource);
								try {
									edit.apply(document, TextEdit.UPDATE_REGIONS);
								} catch (MalformedTreeException exception) {
									JavaPlugin.log(exception);
								} catch (BadLocationException exception) {
									JavaPlugin.log(exception);
								}
								fSuperSource= document.get();
								manager.remove(extractedUnit);
							}
						}
					}
				} else {
					rewrite= fCompilationUnitRewrites.get(unit);
					if (rewrite != null) {
						final CompilationUnitChange layerChange= fLayerChanges.get(unit.getPrimary());
						final CompilationUnitChange rewriteChange= rewrite.createChange(true);
						if (rewriteChange != null && layerChange != null) {
							final MultiStateCompilationUnitChange change= new MultiStateCompilationUnitChange(rewriteChange.getName(), unit);
							change.addChange(layerChange);
							change.addChange(rewriteChange);
							fLayerChanges.remove(unit.getPrimary());
							manager.manage(unit, change);
						} else if (layerChange != null) {
							manager.manage(unit, layerChange);
							fLayerChanges.remove(unit.getPrimary());
						} else if (rewriteChange != null) {
							manager.manage(unit, rewriteChange);
						}
					}
				}
			}
			for (Iterator<Entry<ICompilationUnit, CompilationUnitChange>> iterator= fLayerChanges.entrySet().iterator(); iterator.hasNext();) {
				final Entry<ICompilationUnit, CompilationUnitChange> entry= iterator.next();
				manager.manage(entry.getKey(), entry.getValue());
			}
			ICompilationUnit[] units= manager.getAllCompilationUnits();
			for (int index= 0; index < units.length; index++) {
				if (units[index].getPath().equals(extractedUnit.getPath()))
					manager.remove(units[index]);
			}
		} finally {
			fLayerChanges.clear();
		}
	}

	/**
	 * Resets the changes necessary for the working copy layer.
	 */
	public void resetChanges() {
		fLayerChanges.clear();
	}

	/**
	 * Sets the type name.
	 *
	 * @param name
	 *            the type name
	 */
	public void setTypeName(final String name) {
		Assert.isNotNull(name);
		fTypeName= name;
	}

	/**
	 * Sets the types to extract. Must be a subset of
	 * <code>getPossibleCandidates()</code>. If the declaring type is not
	 * contained, it will automatically be added.
	 *
	 * @param types
	 *            the types to extract
	 */
	public void setTypesToExtract(final IType[] types) {
		Assert.isNotNull(types);
		fTypesToExtract= types;
	}
}

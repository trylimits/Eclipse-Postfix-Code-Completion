/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.TargetSourceRangeComputer;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.GeneralizeTypeDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.CollectingSearchRequestor;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringDescriptorUtil;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.rename.MethodChecks;
import org.eclipse.jdt.internal.corext.refactoring.rename.RippleMethodFinder2;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.ASTCreator;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.CompositeOrTypeConstraint;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.ConstraintCollector;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.ConstraintOperator;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.ConstraintVariable;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.ConstraintVariableFactory;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.ExpressionVariable;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.FullConstraintCreator;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.ITypeConstraint;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.ParameterTypeVariable;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.ReturnTypeVariable;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.SimpleTypeConstraint;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.TypeConstraintFactory;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.TypeVariable;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.SearchUtils;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.BindingLabelProvider;

/**
 * @author tip
 */
public class ChangeTypeRefactoring extends Refactoring {

	private static final String ATTRIBUTE_TYPE= "type"; //$NON-NLS-1$

	private final Map<ICompilationUnit, List<ITypeConstraint>> fConstraintCache;
	/**
	 * Offset of the selected text area.
	 */
	private int fSelectionStart;

	/**
	 * Length of the selected text area.
	 */
	private int fSelectionLength;

	/**
	 * Offset of the effective selection
	 */
	private int fEffectiveSelectionStart;

	/**
	 * Length of the effective selection
	 */
	private int fEffectiveSelectionLength;

	/**
	 * ICompilationUnit containing the selection.
	 */
	private ICompilationUnit fCu;

	/**
	 * If the selection corresponds to a method parameter/return type, this field stores
	 * a reference to its IMethodBinding, otherwise this field remains null. Used during
	 * search for references in other CUs, and for determining the ConstraintVariable
	 * that corresponds to the selection
	 */
	private IMethodBinding fMethodBinding;

	/**
	 * If the selection corresponds to a method parameter, this field stores the parameter
	 * index (0 = first parameter for static methods, 0 = this for nonstatic methods). The
	 * value -1 is stored in the field if the selection corresponds to a method return type.
	 */
	private int fParamIndex;

	/**
	 * The name of the selected parameter, or <code>null</code>.
	 */
	private String fParamName;

	/**
	 * If the selection corresponds to a field, this field stores a reference to its IVariableBinding,
	 * otherwise this field remains null. Used during search for references in other CUs.
	 */
	private IVariableBinding fFieldBinding;

	/**
	 * The compilation units that contain constraint variables related to the selection
	 */
	private ICompilationUnit[] fAffectedUnits;

	/**
	 * The constraint variables that are of interest to this refactoring. This includes
	 * the constraint var. corresponding to the text selection, and possibly additional
	 * elements due to method overriding, method calls, etc.
	 */
	private Collection<ConstraintVariable> fRelevantVars;

	/**
	 * The set of types (other than the original type) that can be given to
	 * the selected ASTNode.
	 */
	private final Collection<ITypeBinding> fValidTypes;

	/**
	 * The type constraints that are related to the selected ASTNode.
	 */
	private Collection<ITypeConstraint> fRelevantConstraints;

	/**
	 * All type constraints in affected compilation units.
	 */
	private Collection<ITypeConstraint> fAllConstraints;

	/**
	 * The name of the new type of the selected declaration.
	 */
	private String fSelectedTypeName;

	/**
	 * The new type of the selected declaration.
	 */
	private ITypeBinding fSelectedType;

	/**
	 * Organizes SearchResults by CompilationUnit
	 */
	private Map<ICompilationUnit, SearchResultGroup> fCuToSearchResultGroup= new HashMap<ICompilationUnit, SearchResultGroup>();


	/**
	 * ITypeBinding for java.lang.Object
	 */
	private ITypeBinding fObject;

	public ITypeBinding getObject(){
		return fObject;
	}

	/**
	 * Control debugging output.
	 */
	private static final boolean DEBUG= false;

	private ConstraintVariable fCv;
	private IBinding fSelectionBinding;
	private ITypeBinding fSelectionTypeBinding;
	private ConstraintCollector fCollector;

	public ChangeTypeRefactoring(ICompilationUnit cu, int selectionStart, int selectionLength) {
		this(cu, selectionStart, selectionLength, null);
	}

	/**
	 * Constructor for ChangeTypeRefactoring (invoked from tests only)
	 * @param cu the compilation unit
	 * @param selectionStart selection offset
	 * @param selectionLength selection length
	 * @param selectedType selected type
	 */
	public ChangeTypeRefactoring(ICompilationUnit cu, int selectionStart, int selectionLength, String selectedType) {
		Assert.isTrue(selectionStart >= 0);
		Assert.isTrue(selectionLength >= 0);

		fSelectionStart= selectionStart;
		fSelectionLength= selectionLength;

		fEffectiveSelectionStart= selectionStart;
		fEffectiveSelectionLength= selectionLength;

		fCu= cu;

		if (selectedType != null)
			fSelectedTypeName= selectedType;

		fConstraintCache= new HashMap<ICompilationUnit, List<ITypeConstraint>>();
		fValidTypes= new HashSet<ITypeBinding>();
	}

    public ChangeTypeRefactoring(JavaRefactoringArguments arguments, RefactoringStatus status) {
   		this(null, 0, 0, null);
   		RefactoringStatus initializeStatus= initialize(arguments);
   		status.merge(initializeStatus);
    }

	// ------------------------------------------------------------------------------------------------- //

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.Refactoring#checkActivation(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
		if (fCu == null || !fCu.isStructureKnown())
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ChangeTypeRefactoring_invalidSelection);
		return checkSelection(new SubProgressMonitor(pm, 1));
	}

	private void setSelectionRanges(Expression exp){
		fEffectiveSelectionStart= exp.getStartPosition();
		fEffectiveSelectionLength= exp.getLength();
		fSelectionBinding= ExpressionVariable.resolveBinding(exp);
		setOriginalType(exp.resolveTypeBinding());
	}

	/**
	 * Check if the right type of AST Node is selected. Create the TypeHierarchy needed to
	 * bring up the wizard.
	 * @param pm progress monitor
	 * @return returns the resulting status
	 */
	private RefactoringStatus checkSelection(IProgressMonitor pm) {
		try {
			pm.beginTask("", 5); //$NON-NLS-1$

			ASTNode node= getTargetNode(fCu, fSelectionStart, fSelectionLength);
			if (DEBUG) {
				System.out.println(
					"selection: [" //$NON-NLS-1$
						+ fSelectionStart
						+ "," //$NON-NLS-1$
						+ (fSelectionStart + fSelectionLength)
						+ "] in " //$NON-NLS-1$
						+ fCu.getElementName());
				System.out.println("node= " + node + ", type= " + node.getClass().getName()); //$NON-NLS-1$ //$NON-NLS-2$
			}

			TypeConstraintFactory typeConstraintFactory = new TypeConstraintFactory(){
				@Override
				public boolean filter(ConstraintVariable v1, ConstraintVariable v2, ConstraintOperator o){
					if (o.isStrictSubtypeOperator()) //TODO: explain why these can be excluded
						return true;
					//Don't create constraint if fSelectionTypeBinding is not involved:
					if (v1.getBinding() != null && v2.getBinding() != null
							&& ! Bindings.equals(v1.getBinding(), fSelectionTypeBinding)
							&& ! Bindings.equals(v2.getBinding(), fSelectionTypeBinding)) {
						if (PRINT_STATS) fNrFiltered++;
						return true;
					}
					return super.filter(v1, v2, o);
				}
			};
			fCollector= new ConstraintCollector(new FullConstraintCreator(new ConstraintVariableFactory(), typeConstraintFactory));
			String selectionValid= determineSelection(node);
			if (selectionValid != null){
				if (DEBUG){
					System.out.println("invalid selection: " + selectionValid); //$NON-NLS-1$
				}
				return RefactoringStatus.createFatalErrorStatus(selectionValid);
			}

			if (fMethodBinding != null) {
				IMethod selectedMethod= (IMethod) fMethodBinding.getJavaElement();
				if (selectedMethod == null){
					return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ChangeTypeRefactoring_insideLocalTypesNotSupported);
				}
			}

			pm.worked(1);

			RefactoringStatus result= new RefactoringStatus();

			if (DEBUG){
				System.out.println("fSelectionTypeBinding: " + fSelectionTypeBinding.getName()); //$NON-NLS-1$
			}

			// produce error message if array or primitive type is selected
			if (fSelectionTypeBinding.isArray()){
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ChangeTypeRefactoring_arraysNotSupported);
			}
			if (fSelectionTypeBinding.isPrimitive()){
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ChangeTypeRefactoring_primitivesNotSupported);
			}
			if (checkOverriddenBinaryMethods())
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ChangeTypeRefactoring_notSupportedOnBinary);

			if (fSelectionTypeBinding.isLocal()){
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ChangeTypeRefactoring_localTypesNotSupported);
			}

			if (fFieldBinding != null && fFieldBinding.getDeclaringClass().isLocal()){
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ChangeTypeRefactoring_insideLocalTypesNotSupported);
			}

			if (fSelectionTypeBinding.isTypeVariable()){
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ChangeTypeRefactoring_typeParametersNotSupported);
			}

			if (fSelectionTypeBinding.isEnum()){
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ChangeTypeRefactoring_enumsNotSupported);
			}

			pm.worked(1);

			if (fSelectedType != null){ // if invoked from unit test, compute valid types here
				computeValidTypes(new NullProgressMonitor());
			}
			return result;
		} finally {
			pm.done();
		}
	}

	private boolean checkOverriddenBinaryMethods() {
		if (fMethodBinding != null){
			Set<ITypeBinding> declaringSupertypes= getDeclaringSuperTypes(fMethodBinding);
			for (Iterator<ITypeBinding> iter= declaringSupertypes.iterator(); iter.hasNext();) {
				ITypeBinding superType= iter.next();
				IMethodBinding overriddenMethod= findMethod(fMethodBinding, superType);
				Assert.isNotNull(overriddenMethod);//because we asked for declaring types
				IMethod iMethod= (IMethod) overriddenMethod.getJavaElement();
				if (iMethod.isBinary()){
					return true;
				}
			}
		}
		return false;
	}

	// copied from FullConstraintCreator
	private static IMethodBinding findMethod(IMethodBinding methodBinding, ITypeBinding type) {
		  if (methodBinding.getDeclaringClass().equals(type))
			  return methodBinding;
		  return Bindings.findOverriddenMethodInType(type, methodBinding);
	}

	// copied from FullConstraintCreator
	private static Set<ITypeBinding> getDeclaringSuperTypes(IMethodBinding methodBinding) {
		ITypeBinding[] allSuperTypes= Bindings.getAllSuperTypes(methodBinding.getDeclaringClass());
		Set<ITypeBinding> result= new HashSet<ITypeBinding>();
		for (int i= 0; i < allSuperTypes.length; i++) {
			ITypeBinding type= allSuperTypes[i];
			if (findMethod(methodBinding, type) != null)
				result.add(type);
		}
		return result;
	}

	/**
	 * Do the actual work of computing allowable types. Invoked by the wizard when
	 * "compute" button is pressed
	 * @param pm the progress monitor
	 * @return the valid types
	 */
	public Collection<ITypeBinding> computeValidTypes(IProgressMonitor pm) {

		pm.beginTask(RefactoringCoreMessages.ChangeTypeRefactoring_checking_preconditions, 100);

		try {
			fCv= findConstraintVariableForSelectedNode(new SubProgressMonitor(pm, 3));
			if (DEBUG) System.out.println("selected CV: " + fCv +  //$NON-NLS-1$
										  " (" + fCv.getClass().getName() +  //$NON-NLS-1$
										  ")");  //$NON-NLS-1$

			if (pm.isCanceled())
				throw new OperationCanceledException();
			fRelevantVars= findRelevantConstraintVars(fCv, new SubProgressMonitor(pm, 50));

			if (DEBUG)
				printCollection("relevant vars:", fRelevantVars); //$NON-NLS-1$

			if (pm.isCanceled())
				throw new OperationCanceledException();
			fRelevantConstraints= findRelevantConstraints(fRelevantVars, new SubProgressMonitor(pm, 30));

			if (pm.isCanceled())
				throw new OperationCanceledException();
			fValidTypes.addAll(computeValidTypes(fSelectionTypeBinding, fRelevantVars,
												 fRelevantConstraints, new SubProgressMonitor(pm, 20)));

			if (DEBUG)
				printCollection("valid types:", getValidTypeNames()); //$NON-NLS-1$
		} catch (CoreException e) {
			JavaPlugin.logErrorMessage("Error occurred during computation of valid types: " + e.toString()); //$NON-NLS-1$
			fValidTypes.clear(); // error occurred during computation of valid types
		}

		pm.done();

		return fValidTypes;
	}


	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.Refactoring#checkInput(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException {
		pm.beginTask(RefactoringCoreMessages.ChangeTypeRefactoring_checking_preconditions, 1);

		RefactoringStatus result= Checks.validateModifiesFiles(
			ResourceUtil.getFiles(fAffectedUnits), getValidationContext());

		pm.done();
		return result;
	}
// TODO: do sanity check somewhere if the refactoring changes any files.
	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.IRefactoring#createChange(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException {
		pm.beginTask(RefactoringCoreMessages.ChangeTypeMessages_CreateChangesForChangeType, 1);
		try {
			Map<ICompilationUnit, Set<ConstraintVariable>> relevantVarsByUnit= new HashMap<ICompilationUnit, Set<ConstraintVariable>>();
			groupChangesByCompilationUnit(relevantVarsByUnit);
			final Map<String, String> arguments= new HashMap<String, String>();
			String project= null;
			IJavaProject javaProject= fCu.getJavaProject();
			if (javaProject != null)
				project= javaProject.getElementName();
			final String description= RefactoringCoreMessages.ChangeTypeRefactoring_descriptor_description_short;
			final String header= Messages.format(RefactoringCoreMessages.ChangeTypeRefactoring_descriptor_description, new String[] { BindingLabelProvider.getBindingLabel(fSelectionBinding, JavaElementLabels.ALL_FULLY_QUALIFIED), BindingLabelProvider.getBindingLabel(fSelectedType, JavaElementLabels.ALL_FULLY_QUALIFIED)});
			final JDTRefactoringDescriptorComment comment= new JDTRefactoringDescriptorComment(project, this, header);
			comment.addSetting(Messages.format(RefactoringCoreMessages.ChangeTypeRefactoring_original_element_pattern, BindingLabelProvider.getBindingLabel(fSelectionBinding, JavaElementLabels.ALL_FULLY_QUALIFIED)));
			comment.addSetting(Messages.format(RefactoringCoreMessages.ChangeTypeRefactoring_original_type_pattern, BindingLabelProvider.getBindingLabel(getOriginalType(), JavaElementLabels.ALL_FULLY_QUALIFIED)));
			comment.addSetting(Messages.format(RefactoringCoreMessages.ChangeTypeRefactoring_refactored_type_pattern, BindingLabelProvider.getBindingLabel(fSelectedType, JavaElementLabels.ALL_FULLY_QUALIFIED)));
			final GeneralizeTypeDescriptor descriptor= RefactoringSignatureDescriptorFactory.createGeneralizeTypeDescriptor(project, description, comment.asString(), arguments, (RefactoringDescriptor.STRUCTURAL_CHANGE | JavaRefactoringDescriptor.JAR_REFACTORING | JavaRefactoringDescriptor.JAR_SOURCE_ATTACHMENT));
			arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT, JavaRefactoringDescriptorUtil.elementToHandle(project, fCu));
			arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_SELECTION, new Integer(fSelectionStart).toString() + " " + new Integer(fSelectionLength).toString()); //$NON-NLS-1$
			arguments.put(ATTRIBUTE_TYPE, fSelectedType.getQualifiedName());
			final DynamicValidationRefactoringChange result= new DynamicValidationRefactoringChange(descriptor, RefactoringCoreMessages.ChangeTypeRefactoring_allChanges);
			for (Iterator<ICompilationUnit>it= relevantVarsByUnit.keySet().iterator(); it.hasNext();) {
				ICompilationUnit icu= it.next();
				Set<ConstraintVariable> cVars= relevantVarsByUnit.get(icu);
				CompilationUnitChange cuChange= new CompilationUnitChange(getName(), icu);
				addAllChangesFor(icu, cVars, cuChange);
				result.add(cuChange);
				pm.worked(1);
				if (pm.isCanceled())
					throw new OperationCanceledException();
			}
			return result;
		} finally {
			pm.done();
		}
	}

	/**
	 * Apply all changes related to a single ICompilationUnit
	 * @param icu the compilation unit
	 * @param vars
	 * @param unitChange
	 * @throws CoreException
	 */
	private void addAllChangesFor(ICompilationUnit icu, Set<ConstraintVariable> vars, CompilationUnitChange unitChange) throws CoreException {
		CompilationUnit	unit= new RefactoringASTParser(ASTProvider.SHARED_AST_LEVEL).parse(icu, true);
		ASTRewrite unitRewriter= ASTRewrite.create(unit.getAST());
		MultiTextEdit root= new MultiTextEdit();
		unitChange.setEdit(root); // Adam sez don't need this, but then unitChange.addGroupDescription() fails an assertion!

		String typeName= updateImports(unit, root);
		updateCu(unit, vars, unitChange, unitRewriter, typeName);
		root.addChild(unitRewriter.rewriteAST());
	}

	private class SourceRangeComputer extends TargetSourceRangeComputer {
		@Override
		public SourceRange computeSourceRange(ASTNode node) {
			return new SourceRange(node.getStartPosition(),node.getLength());
		}
	}

	private void updateCu(CompilationUnit unit, Set<ConstraintVariable> vars, CompilationUnitChange unitChange,
		ASTRewrite unitRewriter, String typeName) throws JavaModelException {

        // use custom SourceRangeComputer to avoid losing comments
		unitRewriter.setTargetSourceRangeComputer(new SourceRangeComputer());

		for (Iterator<ConstraintVariable> it=vars.iterator(); it.hasNext(); ){
			ConstraintVariable cv = it.next();
			ASTNode decl= findDeclaration(unit, cv);
			if ((decl instanceof SimpleName || decl instanceof QualifiedName) && cv instanceof ExpressionVariable) {
				ASTNode gp= decl.getParent().getParent();
				updateType(unit, getType(gp), unitChange, unitRewriter, typeName);   // local variable or parameter
			} else if (decl instanceof MethodDeclaration || decl instanceof FieldDeclaration) {
				updateType(unit, getType(decl), unitChange, unitRewriter, typeName); // method return or field type
			} else if (decl instanceof ParameterizedType){
				updateType(unit, getType(decl), unitChange, unitRewriter, typeName);
			}
		}
	}

	private void updateType(CompilationUnit cu, Type oldType, CompilationUnitChange unitChange,
							ASTRewrite unitRewriter, String typeName) {

		String oldName= fSelectionTypeBinding.getName();
		String[] keys= { BasicElementLabels.getJavaElementName(oldName), BasicElementLabels.getJavaElementName(typeName)};
		String description= Messages.format(RefactoringCoreMessages.ChangeTypeRefactoring_typeChange, keys);
		TextEditGroup gd= new TextEditGroup(description);
		AST	ast= cu.getAST();

		ASTNode nodeToReplace= oldType;
		if (fSelectionTypeBinding.isParameterizedType() && !fSelectionTypeBinding.isRawType()){
			if (oldType.isSimpleType()){
				nodeToReplace= oldType.getParent();
			}
		}

	    //TODO handle types other than simple & parameterized (e.g., arrays)
		Assert.isTrue(fSelectedType.isClass() || fSelectedType.isInterface());

		Type newType= null;
		if (!fSelectedType.isParameterizedType()){
			newType= ast.newSimpleType(ASTNodeFactory.newName(ast, typeName));
		} else {
			newType= createParameterizedType(ast, fSelectedType);
		}

		unitRewriter.replace(nodeToReplace, newType, gd);
		unitChange.addTextEditGroup(gd);
	}

	/**
	 * Creates the appropriate ParameterizedType node. Recursion is needed to
	 * handle the nested case (e.g., Vector<Vector<String>>).
	 * @param ast
	 * @param typeBinding
	 * @return the created type
	 */
	private Type createParameterizedType(AST ast, ITypeBinding typeBinding){
		if (typeBinding.isParameterizedType() && !typeBinding.isRawType()){
			Type baseType= ast.newSimpleType(ASTNodeFactory.newName(ast, typeBinding.getErasure().getName()));
			ParameterizedType newType= ast.newParameterizedType(baseType);
			for (int i=0; i < typeBinding.getTypeArguments().length; i++){
				ITypeBinding typeArg= typeBinding.getTypeArguments()[i];
				Type argType= createParameterizedType(ast, typeArg); // recursive call
				newType.typeArguments().add(argType);
			}
			return newType;
		} else {
			if (!typeBinding.isTypeVariable()){
				return ast.newSimpleType(ASTNodeFactory.newName(ast, typeBinding.getErasure().getName()));
			} else {
				return ast.newSimpleType(ast.newSimpleName(typeBinding.getName()));
			}
		}
	}



	private void groupChangesByCompilationUnit(Map<ICompilationUnit, Set<ConstraintVariable>> relevantVarsByUnit) {
		for (Iterator<ConstraintVariable> it= fRelevantVars.iterator(); it.hasNext();) {
			ConstraintVariable cv= it.next();
			if (!(cv instanceof ExpressionVariable) && !(cv instanceof ReturnTypeVariable)){
				continue;
			}
			ICompilationUnit icu = null;
			if (cv instanceof ExpressionVariable) {
				ExpressionVariable ev = (ExpressionVariable)cv;
				icu = ev.getCompilationUnitRange().getCompilationUnit();
			} else if (cv instanceof ReturnTypeVariable){
				ReturnTypeVariable rtv = (ReturnTypeVariable)cv;
				IMethodBinding mb= rtv.getMethodBinding();
				icu= ((IMethod) mb.getJavaElement()).getCompilationUnit();
			}
			if (!relevantVarsByUnit.containsKey(icu)){
				relevantVarsByUnit.put(icu, new HashSet<ConstraintVariable>());
			}
			relevantVarsByUnit.get(icu).add(cv);
		}
	}

	private ASTNode findDeclaration(CompilationUnit root, ConstraintVariable cv) throws JavaModelException {

		if (fFieldBinding != null){
			IField f= (IField) fFieldBinding.getJavaElement();
			return ASTNodeSearchUtil.getFieldDeclarationNode(f, root);
		}

		if (cv instanceof ExpressionVariable){
			for (Iterator<ITypeConstraint> iter= fAllConstraints.iterator(); iter.hasNext();) {
				ITypeConstraint constraint= iter.next();
				if (constraint.isSimpleTypeConstraint()){
					SimpleTypeConstraint stc= (SimpleTypeConstraint)constraint;
					if (stc.isDefinesConstraint() && stc.getLeft().equals(cv)){
						ConstraintVariable right= stc.getRight();
						if (right instanceof TypeVariable){
							TypeVariable typeVariable= (TypeVariable)right;
							return NodeFinder.perform(root, typeVariable.getCompilationUnitRange().getSourceRange());
						}
					}
				}
			}
		} else if (cv instanceof ReturnTypeVariable) {
			ReturnTypeVariable rtv= (ReturnTypeVariable) cv;
			IMethodBinding mb= rtv.getMethodBinding();
			IMethod im= (IMethod) mb.getJavaElement();
			return ASTNodeSearchUtil.getMethodDeclarationNode(im, root);
		}
		return null;
	}

	private static Type getType(ASTNode node) {
		switch(node.getNodeType()){
			case ASTNode.SINGLE_VARIABLE_DECLARATION:
				return ((SingleVariableDeclaration) node).getType();
			case ASTNode.FIELD_DECLARATION:
				return ((FieldDeclaration) node).getType();
			case ASTNode.VARIABLE_DECLARATION_STATEMENT:
				return ((VariableDeclarationStatement) node).getType();
			case ASTNode.VARIABLE_DECLARATION_EXPRESSION:
				return ((VariableDeclarationExpression) node).getType();
			case ASTNode.METHOD_DECLARATION:
				return ((MethodDeclaration)node).getReturnType2();
			case ASTNode.PARAMETERIZED_TYPE:
				return ((ParameterizedType)node).getType();
			default:
				Assert.isTrue(false);
				return null;
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.IRefactoring#getName()
	 */
	@Override
	public String getName() {
		return RefactoringCoreMessages.ChangeTypeRefactoring_name;
	}

	// ------------------------------------------------------------------------------------------------- //
	// Method for examining if a suitable kind of ASTNode was selected. Information about this node and
	// its parents in the AST are stored in fields fBinding, theMethod, and theField

	/**
	 * Determines what kind of ASTNode has been selected.
	 * @param node the node
	 * @return  A non-null String containing an error message
	 * is returned if the ChangeTypeRefactoring refactoring cannot be applied to the selected ASTNode.
	 * A return value of null indicates a valid selection.
	 */
	private String determineSelection(ASTNode node) {
		if (node == null) {
			return RefactoringCoreMessages.ChangeTypeRefactoring_invalidSelection;
		} else {

			if (DEBUG) System.out.println("node nodeType= " + node.getClass().getName()); //$NON-NLS-1$
			if (DEBUG) System.out.println("parent nodeType= " + node.getParent().getClass().getName()); //$NON-NLS-1$
			if (DEBUG) System.out.println("GrandParent nodeType= " + node.getParent().getParent().getClass().getName()); //$NON-NLS-1$

			ASTNode parent= node.getParent();
			ASTNode grandParent= parent.getParent();
			if (grandParent == null)
				return nodeTypeNotSupported();

			// adjustment needed if part of a parameterized type is selected
			if (grandParent.getNodeType() == ASTNode.PARAMETERIZED_TYPE){
				node= grandParent;
			}

			// adjustment needed if part of a qualified name is selected
			ASTNode current= null;
			if (node.getNodeType() == ASTNode.QUALIFIED_NAME){
				current= node;
				while (current.getNodeType() == ASTNode.QUALIFIED_NAME){
					current= current.getParent();
				}
				if (current.getNodeType() != ASTNode.SIMPLE_TYPE){
					return nodeTypeNotSupported();
				}
				node= current.getParent();
			} else if (parent.getNodeType() == ASTNode.QUALIFIED_NAME){
				current= parent;
				while (current.getNodeType() == ASTNode.QUALIFIED_NAME){
					current= current.getParent();
				}
				if (current.getNodeType() != ASTNode.SIMPLE_TYPE){
					return nodeTypeNotSupported();
				}
				node= current.getParent();
			}

			fObject= node.getAST().resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
			switch (node.getNodeType()) {
				case ASTNode.SIMPLE_NAME :
					return simpleNameSelected((SimpleName)node);
				case ASTNode.VARIABLE_DECLARATION_STATEMENT :
					return variableDeclarationStatementSelected((VariableDeclarationStatement) node);
				case ASTNode.FIELD_DECLARATION :
					return fieldDeclarationSelected((FieldDeclaration) node);
				case ASTNode.SINGLE_VARIABLE_DECLARATION :
					return singleVariableDeclarationSelected((SingleVariableDeclaration) node);
				case ASTNode.PARAMETERIZED_TYPE:
					return parameterizedTypeSelected((ParameterizedType) node);
				default :
					return nodeTypeNotSupported();
			}
		}
	}
	/**
	 * The selection corresponds to an ASTNode on which "ChangeTypeRefactoring" is not defined.
	 * @return the message
	 */
	private static String nodeTypeNotSupported() {
		return RefactoringCoreMessages.ChangeTypeRefactoring_notSupportedOnNodeType;
	}

	/**
	  * The selection corresponds to a SingleVariableDeclaration
	 * @param svd
	 * @return the message
	  */
	private String singleVariableDeclarationSelected(SingleVariableDeclaration svd) {
		SimpleName name = svd.getName();
		setSelectionRanges(name);
		return simpleNameSelected(name);
	}

	/**
	  * The selection corresponds to a ParameterizedType (return type of method)
	 * @param pt the type
	 * @return the message
	  */
	private String parameterizedTypeSelected(ParameterizedType pt) {
		ASTNode parent= pt.getParent();
		if (parent.getNodeType() == ASTNode.METHOD_DECLARATION){
			fMethodBinding= ((MethodDeclaration)parent).resolveBinding();
			fParamIndex= -1;
			fEffectiveSelectionStart= pt.getStartPosition();
			fEffectiveSelectionLength= pt.getLength();
			setOriginalType(pt.resolveBinding());
		} else if (parent.getNodeType() == ASTNode.SINGLE_VARIABLE_DECLARATION){
			return singleVariableDeclarationSelected((SingleVariableDeclaration)parent);
		} else if (parent.getNodeType() == ASTNode.VARIABLE_DECLARATION_STATEMENT){
			return variableDeclarationStatementSelected((VariableDeclarationStatement)parent);
		} else if (parent.getNodeType() == ASTNode.FIELD_DECLARATION){
			return fieldDeclarationSelected((FieldDeclaration)parent);
		} else {
			return nodeTypeNotSupported();
		}
		return null;
	}

	/**
	 * The selection corresponds to a VariableDeclarationStatement
	 * @param vds the name
	 * @return the message
	 */
	private String variableDeclarationStatementSelected(VariableDeclarationStatement vds) {
		if (vds.fragments().size() != 1) {
			return RefactoringCoreMessages.ChangeTypeRefactoring_multiDeclarationsNotSupported;
		} else {
			VariableDeclarationFragment elem= (VariableDeclarationFragment) vds.fragments().iterator().next();
			SimpleName name= elem.getName();
			setSelectionRanges(name);
			return simpleNameSelected(name);
		}
	}

	/**
	 * The selection corresponds to a FieldDeclaration
	 * @param fieldDeclaration the field
	 * @return the message
	 */
	private String fieldDeclarationSelected(FieldDeclaration fieldDeclaration) {
		if (fieldDeclaration.fragments().size() != 1) {
			return RefactoringCoreMessages.ChangeTypeRefactoring_multiDeclarationsNotSupported;
		} else {
			VariableDeclarationFragment elem= (VariableDeclarationFragment) fieldDeclaration.fragments().iterator().next();
			fFieldBinding= elem.resolveBinding();
			SimpleName name= elem.getName();
			setSelectionRanges(name);
			return simpleNameSelected(name);
		}
	}

	/**
	 * The selection corresponds to a SimpleName
	 * @param simpleName the name
	 * @return the message
	 */
	private String simpleNameSelected(SimpleName simpleName) {
		ASTNode parent= simpleName.getParent();
		ASTNode grandParent= parent.getParent();

		if (parent.getNodeType() == ASTNode.VARIABLE_DECLARATION_STATEMENT){
			VariableDeclarationStatement vds= (VariableDeclarationStatement)parent;
			if (vds.fragments().size() > 1){
				return RefactoringCoreMessages.ChangeTypeRefactoring_multiDeclarationsNotSupported;
			}
		} else if (parent.getNodeType() == ASTNode.VARIABLE_DECLARATION_FRAGMENT) {
			if (grandParent.getNodeType() == ASTNode.VARIABLE_DECLARATION_STATEMENT){
				VariableDeclarationStatement vds= (VariableDeclarationStatement)grandParent;
				if (vds.fragments().size() > 1) {
					return RefactoringCoreMessages.ChangeTypeRefactoring_multiDeclarationsNotSupported;
				}
				setSelectionRanges(simpleName);
			} else if (grandParent.getNodeType() == ASTNode.VARIABLE_DECLARATION_EXPRESSION) {
				VariableDeclarationExpression vde= (VariableDeclarationExpression)grandParent;
				if (vde.fragments().size() > 1) {
					return RefactoringCoreMessages.ChangeTypeRefactoring_multiDeclarationsNotSupported;
				}
				setSelectionRanges(simpleName);
			} else if (grandParent.getNodeType() == ASTNode.FIELD_DECLARATION) {
				FieldDeclaration fd= (FieldDeclaration)grandParent;
				if (fd.fragments().size() > 1){
					return RefactoringCoreMessages.ChangeTypeRefactoring_multiDeclarationsNotSupported;
				}
				VariableDeclarationFragment fragment = (VariableDeclarationFragment)parent;
				fFieldBinding= fragment.resolveBinding();
				setSelectionRanges(fragment.getName());
			} else {
				return RefactoringCoreMessages.ChangeTypeRefactoring_notSupportedOnNodeType;
			}
		} else if (parent.getNodeType() == ASTNode.SINGLE_VARIABLE_DECLARATION) {
			SingleVariableDeclaration singleVariableDeclaration= (SingleVariableDeclaration) parent;
			if (singleVariableDeclaration.getType() instanceof UnionType) {
				return RefactoringCoreMessages.ChangeTypeRefactoring_uniontypeNotSupported;
			}
			if ((grandParent.getNodeType() == ASTNode.METHOD_DECLARATION)) {
				fMethodBinding= ((MethodDeclaration)grandParent).resolveBinding();
				setOriginalType(simpleName.resolveTypeBinding());
				fParamIndex= ((MethodDeclaration)grandParent).parameters().indexOf(parent);
				fParamName= singleVariableDeclaration.getName().getIdentifier();
			} else {
				setSelectionRanges(singleVariableDeclaration.getName());
			}
		} else if (parent.getNodeType() == ASTNode.SIMPLE_TYPE && (grandParent.getNodeType() == ASTNode.SINGLE_VARIABLE_DECLARATION)) {
			ASTNode greatGrandParent= grandParent.getParent();
			SingleVariableDeclaration singleVariableDeclaration= (SingleVariableDeclaration) grandParent;
			if (singleVariableDeclaration.getExtraDimensions() > 0 || singleVariableDeclaration.isVarargs()) {
				return RefactoringCoreMessages.ChangeTypeRefactoring_arraysNotSupported;
			}
			if (greatGrandParent != null && greatGrandParent.getNodeType() == ASTNode.METHOD_DECLARATION) {
				fMethodBinding= ((MethodDeclaration)greatGrandParent).resolveBinding();
				fParamIndex= ((MethodDeclaration)greatGrandParent).parameters().indexOf(grandParent);
				fParamName= singleVariableDeclaration.getName().getIdentifier();
				setSelectionRanges(simpleName);
			} else {
				setSelectionRanges(singleVariableDeclaration.getName());
			}
		} else if (parent.getNodeType() == ASTNode.SIMPLE_TYPE && grandParent.getNodeType() == ASTNode.METHOD_DECLARATION) {
			fMethodBinding= ((MethodDeclaration)grandParent).resolveBinding();
			setOriginalType(fMethodBinding.getReturnType());
			fParamIndex= -1;
		} else if (parent.getNodeType() == ASTNode.METHOD_DECLARATION &&
				grandParent.getNodeType() == ASTNode.TYPE_DECLARATION) {
			MethodDeclaration methodDeclaration= (MethodDeclaration)parent;
			if (methodDeclaration.getName().equals(simpleName)) {
				return RefactoringCoreMessages.ChangeTypeRefactoring_notSupportedOnNodeType;
			}
			fMethodBinding= ((MethodDeclaration)parent).resolveBinding();
			fParamIndex= -1;
		} else if (
				parent.getNodeType() == ASTNode.SIMPLE_TYPE && (grandParent.getNodeType() == ASTNode.VARIABLE_DECLARATION_STATEMENT)) {
			return variableDeclarationStatementSelected((VariableDeclarationStatement) grandParent);
		} else if (parent.getNodeType() == ASTNode.CAST_EXPRESSION) {
			ASTNode decl= findDeclaration(parent.getRoot(), fSelectionStart, fSelectionLength+1);
			VariableDeclarationFragment fragment= (VariableDeclarationFragment)decl;
			SimpleName name = fragment.getName();
			setSelectionRanges(name);
		} else if (parent.getNodeType() == ASTNode.SIMPLE_TYPE &&
				grandParent.getNodeType() == ASTNode.FIELD_DECLARATION) {
			return fieldDeclarationSelected((FieldDeclaration) grandParent);
		} else if (parent.getNodeType() == ASTNode.SIMPLE_TYPE &&
				grandParent.getNodeType() == ASTNode.ARRAY_TYPE){
			return RefactoringCoreMessages.ChangeTypeRefactoring_arraysNotSupported;
		} else if (parent.getNodeType() == ASTNode.QUALIFIED_NAME){
			setSelectionRanges(simpleName);
		} else {
			return RefactoringCoreMessages.ChangeTypeRefactoring_notSupportedOnNodeType;
		}
		return null;
	}

	// ------------------------------------------------------------------------------------------------- //
    // Methods for examining & solving type constraints. This includes:
    //  (1) locating the ConstraintVariable corresponding to the selected ASTNode
    //  (2) finding all ConstraintVariables "related" to (1) via overriding, method calls, field access
    //  (3) find all ITypeConstraints of interest that mention ConstraintVariables in (2)
    //  (4) determining all ITypes for which the ITypeConstraints in (3) are satisfied

	/**
	 * Find a ConstraintVariable that corresponds to the selected ASTNode.
	 * @param pm
	 * @return the ConstraintVariable
	 */
	private ConstraintVariable findConstraintVariableForSelectedNode(IProgressMonitor pm) {
		pm.beginTask(RefactoringCoreMessages.ChangeTypeRefactoring_analyzingMessage, 100);
		ICompilationUnit[] cus= { fCu }; // only search in CU containing selection

		if (DEBUG){
			System.out.println("Effective selection: " + fEffectiveSelectionStart + "/" + fEffectiveSelectionLength); //$NON-NLS-1$ //$NON-NLS-2$
		}

		Collection<ITypeConstraint> allConstraints= getConstraints(cus, new SubProgressMonitor(pm, 50));

		IProgressMonitor subMonitor= new SubProgressMonitor(pm, 50);
		subMonitor.beginTask(RefactoringCoreMessages.ChangeTypeRefactoring_analyzingMessage, allConstraints.size());
		for (Iterator<ITypeConstraint> it= allConstraints.iterator(); it.hasNext(); ) {
			subMonitor.worked(1);
			ITypeConstraint tc= it.next();
			if (! (tc instanceof SimpleTypeConstraint))
				continue;
			SimpleTypeConstraint stc= (SimpleTypeConstraint) tc;
			if (matchesSelection(stc.getLeft()))
				return stc.getLeft();
			if (matchesSelection(stc.getRight()))
				return stc.getRight();
		}
		subMonitor.done();
		pm.done();
		Assert.isTrue(false, RefactoringCoreMessages.ChangeTypeRefactoring_noMatchingConstraintVariable);
		return null;
	}

	/**
	 * Determine if a given ConstraintVariable matches the selected ASTNode.
	 * @param cv the ConstraintVariable
	 * @return <code>true</code> if the given ConstraintVariable matches the selected ASTNode
	 */
	private boolean matchesSelection(ConstraintVariable cv){
		if (cv instanceof ExpressionVariable){
			ExpressionVariable ev= (ExpressionVariable)cv;
			return (fSelectionBinding != null && Bindings.equals(fSelectionBinding, ev.getExpressionBinding()));
		} else if (cv instanceof ParameterTypeVariable){
			ParameterTypeVariable ptv = (ParameterTypeVariable)cv;
			if (fMethodBinding != null && Bindings.equals(ptv.getMethodBinding(), fMethodBinding) &&
				ptv.getParameterIndex() == fParamIndex){
				return true;
			}
		} else if (cv instanceof ReturnTypeVariable){
			ReturnTypeVariable rtv = (ReturnTypeVariable)cv;
			if (fMethodBinding != null && Bindings.equals(rtv.getMethodBinding(), fMethodBinding) &&
				fParamIndex == -1){
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine the set of constraint variables related to the selected
	 * expression. In addition to the expression itself, this consists of
	 * any expression that is defines-equal to it, and any expression equal
	 * to it.
	 * @param cv
	 * @param pm
	 * @return the constraint variables
	 * @throws CoreException
	 */
	private Collection<ConstraintVariable> findRelevantConstraintVars(ConstraintVariable cv, IProgressMonitor pm) throws CoreException {
		pm.beginTask(RefactoringCoreMessages.ChangeTypeRefactoring_analyzingMessage, 150);
		Collection<ConstraintVariable> result= new HashSet<ConstraintVariable>();
		result.add(cv);
		ICompilationUnit[] cus= collectAffectedUnits(new SubProgressMonitor(pm, 50));
		Collection<ITypeConstraint> allConstraints= getConstraints(cus, new SubProgressMonitor(pm, 50));

		List<ConstraintVariable> workList= new ArrayList<ConstraintVariable>(result);
		while(! workList.isEmpty()){

			pm.worked(10);

			ConstraintVariable first= workList.remove(0);
			for (Iterator<ITypeConstraint> iter= allConstraints.iterator(); iter.hasNext();) {
				pm.worked(1);
				ITypeConstraint typeConstraint= iter.next();
				if (! typeConstraint.isSimpleTypeConstraint())
					continue;
				SimpleTypeConstraint stc= (SimpleTypeConstraint)typeConstraint;
				if (! stc.isDefinesConstraint() && ! stc.isEqualsConstraint())
					continue;
				ConstraintVariable match= match(first, stc.getLeft(), stc.getRight());
				if (match instanceof ExpressionVariable
				|| match instanceof ParameterTypeVariable
				|| match instanceof ReturnTypeVariable){
					if (! result.contains(match)){
						workList.add(match);
						result.add(match);
					}
				}
			}
		}

		pm.done();

		return result;
	}
	private static ConstraintVariable match(ConstraintVariable matchee, ConstraintVariable left, ConstraintVariable right) {
		if (matchee.equals(left))
			return right;
		if (matchee.equals(right))
			return left;
		return null;
	}

	/**
	 * Select the type constraints that involve the selected ASTNode.
	 * @param relevantConstraintVars
	 * @param pm
	 * @return the result
	 * @throws CoreException
	 */
	private Collection<ITypeConstraint> findRelevantConstraints(Collection<ConstraintVariable> relevantConstraintVars,
																	IProgressMonitor pm) throws CoreException {

		ICompilationUnit[] cus= collectAffectedUnits(new SubProgressMonitor(pm, 100));

		fAllConstraints= getConstraints(cus, new SubProgressMonitor(pm, 900));

		pm.beginTask(RefactoringCoreMessages.ChangeTypeRefactoring_analyzingMessage, 1000 + fAllConstraints.size());


		if (DEBUG) printCollection("type constraints: ", fAllConstraints); //$NON-NLS-1$
		Collection<ITypeConstraint> result= new ArrayList<ITypeConstraint>();
		for (Iterator<ITypeConstraint> it= fAllConstraints.iterator(); it.hasNext(); ) {
			ITypeConstraint tc= it.next();
			if (tc.isSimpleTypeConstraint()) {
				SimpleTypeConstraint stc= (SimpleTypeConstraint) tc;
				if (stc.isDefinesConstraint() || stc.isEqualsConstraint())
					continue;
				if (stc.getLeft().equals(stc.getRight()))
					continue;
				if (isNull(stc.getLeft()))
					continue;
				if (relevantConstraintVars.contains(stc.getLeft()) || relevantConstraintVars.contains(stc.getRight()))
					result.add(tc);
			} else {
				CompositeOrTypeConstraint cotc= (CompositeOrTypeConstraint) tc;
				ITypeConstraint[] components= cotc.getConstraints();
				for (int i= 0; i < components.length; i++) {
					ITypeConstraint component= components[i];
					SimpleTypeConstraint simpleComponent= (SimpleTypeConstraint) component;
					if (relevantConstraintVars.contains(simpleComponent.getLeft()))
						result.add(tc);
				}
			}
			pm.worked(1);
		}
		if (DEBUG)
			printCollection("selected constraints: ", result); //$NON-NLS-1$
		pm.done();
		return result;
	}

	/**
	 * Finds the declaration of the ASTNode in a given AST at a specified offset and with a specified length
	 * @param root the AST
	 * @param start start
	 * @param length length
	 * @return the declaring node
	 */
	private static ASTNode findDeclaration(final ASTNode root, final int start, final int length){
		ASTNode node= NodeFinder.perform(root, start, length);
		Assert.isTrue(node instanceof SimpleName, String.valueOf(node.getNodeType()));
		Assert.isTrue(root instanceof CompilationUnit, String.valueOf(root.getNodeType()));
		return ((CompilationUnit)root).findDeclaringNode(((SimpleName)node).resolveBinding());
	}

	// For debugging
	static String print(Collection<ITypeBinding> types){
		if (types.isEmpty())
			return "{ }"; //$NON-NLS-1$
		String result = "{ "; //$NON-NLS-1$
		for (Iterator<ITypeBinding> it=types.iterator(); it.hasNext(); ){
			ITypeBinding type= it.next();
			result += type.getQualifiedName();
			if (it.hasNext()){
				result += ", ";  //$NON-NLS-1$
			} else {
				result += " }"; //$NON-NLS-1$
			}
		}
		return result;
	}


	/**
	 * Determines the set of types for which a set of type constraints is satisfied.
	 * @param originalType
	 * @param relevantVars
	 * @param relevantConstraints
	 * @param pm
	 * @return the valid types
	 * @throws JavaModelException
	 */
	private Collection<ITypeBinding> computeValidTypes(ITypeBinding originalType,
													Collection<ConstraintVariable> relevantVars,
													Collection<ITypeConstraint> relevantConstraints,
													IProgressMonitor pm) throws JavaModelException {

		Collection<ITypeBinding> result= new HashSet<ITypeBinding>();

		Collection<ITypeBinding> allTypes = new HashSet<ITypeBinding>();
		allTypes.addAll(getAllSuperTypes(originalType));

		pm.beginTask(RefactoringCoreMessages.ChangeTypeRefactoring_analyzingMessage, allTypes.size());

		for (Iterator<ITypeBinding> it= allTypes.iterator(); it.hasNext(); ) {
			ITypeBinding type= it.next();
			if (isValid(type, relevantVars, relevantConstraints, new SubProgressMonitor(pm, 1))) {
				result.add(type);
			}
		}
		// "changing" to the original type is a no-op
		result.remove(originalType);

		// TODO: remove all types that are not visible --- need to check visibility in the CUs for
		//       all relevant constraint variables

		pm.done();

		return result;
	}

	/**
	 * Determines if a given type satisfies a set of type constraints.
	 * @param type
	 * @param relevantVars
	 * @param constraints
	 * @param pm
	 * @return <code>true</code> if a the type satisfies a set of type constraints.
	 * @throws JavaModelException
	 */
	private boolean isValid(ITypeBinding type,
						    Collection<ConstraintVariable> relevantVars,
						    Collection<ITypeConstraint> constraints,
							IProgressMonitor pm) throws JavaModelException {
		pm.beginTask(RefactoringCoreMessages.ChangeTypeRefactoring_analyzingMessage, constraints.size());

		for(ICompilationUnit cu: fAffectedUnits){
			if (!JavaModelUtil.isVisibleInHierarchy((IMember) (type.getJavaElement()), (IPackageFragment) cu.getParent())) {
				return false;
			}
		}

		for (Iterator<ITypeConstraint> it= constraints.iterator(); it.hasNext(); ) {
			ITypeConstraint tc= it.next();
			if (tc instanceof SimpleTypeConstraint) {
				if (!(isValidSimpleConstraint(type,  relevantVars, (SimpleTypeConstraint) tc)))
					return false;
			} else if (tc instanceof CompositeOrTypeConstraint) {
				if (!(isValidOrConstraint(type,  relevantVars, (CompositeOrTypeConstraint) tc)))
					return false;
			}
			pm.worked(1);
		}
		pm.done();
		return true;
	}

	private boolean isValidSimpleConstraint(ITypeBinding type,
											Collection<ConstraintVariable> relevantVars,
											SimpleTypeConstraint stc){
		if (relevantVars.contains(stc.getLeft())) { // upper bound
			if (!isSubTypeOf(type, findType(stc.getRight()))) {
				return false;
			}
		}
		return true;
	}

	private boolean isValidOrConstraint(ITypeBinding type,
										Collection<ConstraintVariable> relevantVars,
										CompositeOrTypeConstraint cotc){
		ITypeConstraint[] components= cotc.getConstraints();
		for (int i= 0; i < components.length; i++) {
			if (components[i] instanceof SimpleTypeConstraint) {
				SimpleTypeConstraint sc= (SimpleTypeConstraint) components[i];
				if (relevantVars.contains(sc.getLeft())) { // upper bound
					if (isSubTypeOf(type, findType(sc.getRight())))
						return true;
				} else if (relevantVars.contains(sc.getRight())) { // lower bound
					if (isSubTypeOf(findType(sc.getLeft()), type))
						return true;
				}
			}
		}
		return false;
	}


	private ITypeBinding findType(ConstraintVariable cv) {
		return cv.getBinding();
	}

	/**
	 * Gather constraints associated with a set of compilation units.
	 * @param referringCus
	 * @param pm
	 * @return the constraints
	 */
	private Collection<ITypeConstraint> getConstraints(ICompilationUnit[] referringCus, IProgressMonitor pm) {
		pm.beginTask(RefactoringCoreMessages.ChangeTypeRefactoring_analyzingMessage, referringCus.length);
		Collection<ITypeConstraint> result= new ArrayList<ITypeConstraint>();
		for (int i= 0; i < referringCus.length; i++) {
			result.addAll(getConstraints(referringCus[i]));
			pm.worked(1);
			if (pm.isCanceled())
				throw new OperationCanceledException();
		}
		pm.done();
		return result;
	}

	private List<ITypeConstraint> getConstraints(ICompilationUnit unit) {
		if (fConstraintCache.containsKey(unit))
			return fConstraintCache.get(unit);

		CompilationUnit cu= ASTCreator.createAST(unit, null);

		// only generate type constraints for relevant MethodDeclaration subtrees
		if (fMethodBinding != null && fCuToSearchResultGroup.containsKey(unit)){
			SearchResultGroup group= fCuToSearchResultGroup.get(unit);
			ASTNode[] nodes= ASTNodeSearchUtil.getAstNodes(group.getSearchResults(), cu);
			for (int i=0; i < nodes.length; i++){
				ASTNode node = nodes[i];
				if (fMethodBinding != null){
					// find MethodDeclaration above it in the tree
					ASTNode n= node;
					while (n != null && !(n instanceof MethodDeclaration)) {
						n = n.getParent();
					}
					MethodDeclaration md= (MethodDeclaration) n;
					if (md != null)
						md.accept(fCollector);
				}
			}
		} else {
			cu.accept(fCollector);
		}
		List<ITypeConstraint> constraints= Arrays.asList(fCollector.getConstraints());
		fConstraintCache.put(unit, constraints);
		return constraints;
	}

	/**
	 * update a CompilationUnit's imports after changing the type of declarations
	 * @param astRoot the AST
	 * @param rootEdit the resulting edit
	 * @return the type name to use
	 * @throws CoreException
	 */
	private String updateImports(CompilationUnit astRoot, MultiTextEdit rootEdit) throws CoreException{
		ImportRewrite rewrite= StubUtility.createImportRewrite(astRoot, true);
		ContextSensitiveImportRewriteContext context= new ContextSensitiveImportRewriteContext(astRoot, fSelectionStart, rewrite);
		String typeName= rewrite.addImport(fSelectedType.getQualifiedName(), context);
		rootEdit.addChild(rewrite.rewriteImports(null));
		return typeName;
	}

	//	------------------------------------------------------------------------------------------------- //
	// Miscellaneous helper methods

	/**
	 * Returns the Collection of types that can be given to the selected declaration.
	 * @return return the valid type bindings
	 */
	public Collection<ITypeBinding> getValidTypes() {
		return fValidTypes;
	}

	public ITypeBinding getOriginalType(){
		return fSelectionTypeBinding;
	}

	private void setOriginalType(ITypeBinding originalType){
		fSelectionTypeBinding= originalType;
		fSelectedType= findSuperTypeByName(originalType, fSelectedTypeName);
	}

	public String getTarget() {
		String typeName= fSelectionTypeBinding == null ? "" : fSelectionTypeBinding.getName() + " ";  //$NON-NLS-1$//$NON-NLS-2$
		if (fFieldBinding != null) {
			return typeName + fFieldBinding.getName();
		} else if (fMethodBinding != null) {
			if (fParamIndex == -1) {
				return typeName + fMethodBinding.getName() + "(...)"; //$NON-NLS-1$
			} else {
				return typeName + fParamName;
			}
		} else if (fSelectionBinding != null) {
			return typeName + fSelectionBinding.getName();
		} else {
			return typeName;
		}
	}

	/**
	 * Returns the Collection<String> of names of types that can be given to the selected declaration.
	 * (used in tests only)
	 * @return Collection<String> of names of types that can be given to the selected declaration
	 */
	public Collection<String> getValidTypeNames() {
		Collection<String> typeNames= new ArrayList<String>();
		for (Iterator<ITypeBinding> it= fValidTypes.iterator(); it.hasNext();) {
			ITypeBinding type= it.next();
			typeNames.add(type.getQualifiedName());
		}

		return typeNames;
	}

	/**
	 * Find the ASTNode for the given source text selection, if it is a type
	 * declaration, or null otherwise.
	 * @param unit The compilation unit in which the selection was made
	 * @param offset
	 * @param length
	 * @return ASTNode
	 */
	private ASTNode getTargetNode(ICompilationUnit unit, int offset, int length) {
		CompilationUnit root= ASTCreator.createAST(unit, null);
		ASTNode node= NodeFinder.perform(root, offset, length);
		return node;
	}

	/**
	 * Determines the set of compilation units that may give rise to type constraints that
	 * we are interested in. This involves searching for overriding/overridden methods,
	 * method calls, field accesses.
	 * @param pm the monitor
	 * @return the affected units
	 * @throws CoreException
	 */
	private ICompilationUnit[] collectAffectedUnits(IProgressMonitor pm) throws CoreException {
		// BUG: currently, no type constraints are generated for methods that are related
		// but that do not override each other. As a result, we may miss certain relevant
		// variables

		pm.beginTask(RefactoringCoreMessages.ChangeTypeRefactoring_analyzingMessage, 100);

		if (fAffectedUnits != null) {
			if (DEBUG) printCollection("affected units: ", Arrays.asList(fAffectedUnits)); //$NON-NLS-1$
			pm.worked(100);
			return fAffectedUnits;
		}
		if (fMethodBinding != null) {
			if (fMethodBinding != null) {


				IMethod selectedMethod= (IMethod) fMethodBinding.getJavaElement();
				if (selectedMethod == null) {
					// can't happen since we checked it up front in check initial conditions
					Assert.isTrue(false, RefactoringCoreMessages.ChangeTypeRefactoring_no_method);
				}

				// the following code fragment appears to be the source of a memory leak, when
				// GT is repeatedly applied

				IMethod root= selectedMethod;
				if (! root.getDeclaringType().isInterface() && MethodChecks.isVirtual(root)) {
					final SubProgressMonitor subMonitor= new SubProgressMonitor(pm, 5);
					IMethod inInterface= MethodChecks.isDeclaredInInterface(root, root.getDeclaringType().newTypeHierarchy(new SubProgressMonitor(subMonitor, 1)), subMonitor);
					if (inInterface != null && !inInterface.equals(root))
						root= inInterface;
				}

				// end code fragment

				IMethod[] rippleMethods= RippleMethodFinder2.getRelatedMethods(
						root, new SubProgressMonitor(pm, 15), null);
				SearchPattern pattern= RefactoringSearchEngine.createOrPattern(
						rippleMethods, IJavaSearchConstants.ALL_OCCURRENCES);

				// To compute the scope we have to use the selected method. Otherwise we
				// might start from the wrong project.
				IJavaSearchScope scope= RefactoringScopeFactory.create(selectedMethod);
				CollectingSearchRequestor csr= new CollectingSearchRequestor();

				SearchResultGroup[] groups= RefactoringSearchEngine.search(
					pattern,
					null,
					scope,
					csr,
					new SubProgressMonitor(pm, 80),
					new RefactoringStatus()); //TODO: deal with errors from non-CU matches

				fAffectedUnits= getCus(groups);
			}
		} else if (fFieldBinding != null) {
			IField iField= (IField) fFieldBinding.getJavaElement();
			if (iField == null) {
				// can't happen since we checked it up front in check initial conditions
				Assert.isTrue(false, RefactoringCoreMessages.ChangeTypeRefactoring_no_filed);
			}
			SearchPattern pattern= SearchPattern.createPattern(
					iField, IJavaSearchConstants.ALL_OCCURRENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE);
			IJavaSearchScope scope= RefactoringScopeFactory.create(iField);
			CollectingSearchRequestor csr= new CollectingSearchRequestor();
			SearchResultGroup[] groups=
				RefactoringSearchEngine.search(pattern, null, scope, csr, new SubProgressMonitor(pm, 100),
						new RefactoringStatus()); //TODO: deal with errors from non-CU matches
			fAffectedUnits= getCus(groups);
		} else {
			// otherwise, selection was a local variable and we only have to search the CU
			// containing the selection
			fAffectedUnits= new ICompilationUnit[] { fCu };
		}
		if (DEBUG) {
			System.out.println("Determining affected CUs:"); //$NON-NLS-1$
			for (int i= 0; i < fAffectedUnits.length; i++) {
				System.out.println("  affected CU: " + fAffectedUnits[i].getElementName()); //$NON-NLS-1$
			}
		}
		pm.done();
		return fAffectedUnits;
	}

	public void setSelectedType(ITypeBinding type){
		fSelectedType= type;
	}

	//	-------------------------------------------------------------------------------------------- //
	// TODO The following utility methods should probably be moved to another class

	/**
	 * Determines if a constraint variable corresponds to the constant "null".
	 * @param cv
	 * @return <code>true</code> if the constraint variable corresponds to the constant "null".
	 */
	private static boolean isNull(ConstraintVariable cv) {
		return cv instanceof ExpressionVariable && ((ExpressionVariable)cv).getExpressionType() == ASTNode.NULL_LITERAL;
	}


	/*
	 * For debugging.
	 */
	void printCollection(String title, Collection<?> l) {
		System.out.println(l.size() + " " + title); //$NON-NLS-1$
		for (Iterator<?> it= l.iterator(); it.hasNext();) {
			System.out.println("  " + it.next()); //$NON-NLS-1$
		}
	}

	/**
	 * Returns the compilation units that contain the search results.
	 * @param groups
	 * @return the CUs
	 */
	private ICompilationUnit[] getCus(SearchResultGroup[] groups) {
		List<ICompilationUnit> result= new ArrayList<ICompilationUnit>(groups.length);
		for (int i= 0; i < groups.length; i++) {
			SearchResultGroup group= groups[i];
			ICompilationUnit cu= group.getCompilationUnit();
			if (cu != null) {
				result.add(cu);
				fCuToSearchResultGroup.put(cu, group);
			}
		}
		return result.toArray(new ICompilationUnit[result.size()]);
	}

    /**
	 * This always includes the type itself. It will include type
	 * Object for any type other than Object
     * @param type
     * @return the super types
	 */
	public Set<ITypeBinding> getAllSuperTypes(ITypeBinding type){
		Set<ITypeBinding> result= new HashSet<ITypeBinding>();
		result.add(type);
		if (type.getSuperclass() != null){
			result.addAll(getAllSuperTypes(type.getSuperclass()));
		}
		ITypeBinding[] interfaces= type.getInterfaces();
		for (int i=0; i < interfaces.length; i++){
			result.addAll(getAllSuperTypes(interfaces[i]));
		}
		if ((type != fObject) && !contains(result, fObject)){
			result.add(fObject);
		}
		return result;
	}

    private ITypeBinding findSuperTypeByName(ITypeBinding type, String superTypeName){
    	Set<ITypeBinding> superTypes= getAllSuperTypes(type);
    	for (Iterator<ITypeBinding> it= superTypes.iterator(); it.hasNext(); ){
    		ITypeBinding sup= it.next();
    		if (sup.getQualifiedName().equals(superTypeName)){
    			return sup;
    		}
    	}
    	return null;
    }

	public boolean isSubTypeOf(ITypeBinding type1, ITypeBinding type2){

		// to ensure that, e.g., Comparable<String> is considered a subtype of raw Comparable
		if (type1.isParameterizedType() && type1.getTypeDeclaration().isEqualTo(type2.getTypeDeclaration())){
			return true;
		}
		Set<ITypeBinding> superTypes= getAllSuperTypes(type1);
		return contains(superTypes, type2);
	}

	private static boolean contains(Collection<ITypeBinding> c, ITypeBinding binding){
		for (Iterator<ITypeBinding> it=c.iterator(); it.hasNext(); ){
			ITypeBinding b = it.next();
			if (Bindings.equals(b, binding)) return true;
		}
		return false;
	}

	private RefactoringStatus initialize(JavaRefactoringArguments arguments) {
		final String selection= arguments.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_SELECTION);
		if (selection != null) {
			int offset= -1;
			int length= -1;
			final StringTokenizer tokenizer= new StringTokenizer(selection);
			if (tokenizer.hasMoreTokens())
				offset= Integer.valueOf(tokenizer.nextToken()).intValue();
			if (tokenizer.hasMoreTokens())
				length= Integer.valueOf(tokenizer.nextToken()).intValue();
			if (offset >= 0 && length >= 0) {
				fSelectionStart= offset;
				fSelectionLength= length;
			} else
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { selection, JavaRefactoringDescriptorUtil.ATTRIBUTE_SELECTION}));
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_SELECTION));
		final String handle= arguments.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT);
		if (handle != null) {
			final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(arguments.getProject(), handle, false);
			if (element == null || !element.exists() || element.getElementType() != IJavaElement.COMPILATION_UNIT)
				return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getName(), IJavaRefactorings.GENERALIZE_TYPE);
			else
				fCu= (ICompilationUnit) element;
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT));
		final String type= arguments.getAttribute(ATTRIBUTE_TYPE);
		if (type != null && !"".equals(type)) //$NON-NLS-1$
			fSelectedTypeName= type;
		else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_TYPE));
		return new RefactoringStatus();
	}
}

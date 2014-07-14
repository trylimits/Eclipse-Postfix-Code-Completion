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
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IResource;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.SearchUtils;

import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;

/**
 * This class is used to find references to constructors.
 */
class ConstructorReferenceFinder {
	private final IType fType;
	private final IMethod[] fConstructors;

	private ConstructorReferenceFinder(IType type) throws JavaModelException{
		fConstructors= JavaElementUtil.getAllConstructors(type);
		fType= type;
	}

	private ConstructorReferenceFinder(IMethod constructor){
		fConstructors= new IMethod[]{constructor};
		fType= constructor.getDeclaringType();
	}

	public static SearchResultGroup[] getConstructorReferences(IType type, IProgressMonitor pm, RefactoringStatus status) throws JavaModelException{
		return new ConstructorReferenceFinder(type).getConstructorReferences(pm, null, IJavaSearchConstants.REFERENCES, status);
	}

	public static SearchResultGroup[] getConstructorReferences(IType type, WorkingCopyOwner owner, IProgressMonitor pm, RefactoringStatus status) throws JavaModelException{
		return new ConstructorReferenceFinder(type).getConstructorReferences(pm, owner, IJavaSearchConstants.REFERENCES, status);
	}

	public static SearchResultGroup[] getConstructorOccurrences(IMethod constructor, IProgressMonitor pm, RefactoringStatus status) throws JavaModelException{
		Assert.isTrue(constructor.isConstructor());
		return new ConstructorReferenceFinder(constructor).getConstructorReferences(pm, null, IJavaSearchConstants.ALL_OCCURRENCES, status);
	}

	private SearchResultGroup[] getConstructorReferences(IProgressMonitor pm, WorkingCopyOwner owner, int limitTo, RefactoringStatus status) throws JavaModelException{
		IJavaSearchScope scope= createSearchScope();
		SearchPattern pattern= RefactoringSearchEngine.createOrPattern(fConstructors, limitTo);
		if (pattern == null){
			if (fConstructors.length != 0)
				return new SearchResultGroup[0];
			return getImplicitConstructorReferences(pm, owner, status);
		}
		return removeUnrealReferences(RefactoringSearchEngine.search(pattern, owner, scope, pm, status));
	}

	//XXX this method is a workaround for jdt core bug 27236
	private SearchResultGroup[] removeUnrealReferences(SearchResultGroup[] groups) {
		List<SearchResultGroup> result= new ArrayList<SearchResultGroup>(groups.length);
		for (int i= 0; i < groups.length; i++) {
			SearchResultGroup group= groups[i];
			ICompilationUnit cu= group.getCompilationUnit();
			if (cu == null)
				continue;
			CompilationUnit cuNode= new RefactoringASTParser(ASTProvider.SHARED_AST_LEVEL).parse(cu, false);
			SearchMatch[] allSearchResults= group.getSearchResults();
			List<SearchMatch> realConstructorReferences= new ArrayList<SearchMatch>(Arrays.asList(allSearchResults));
			for (int j= 0; j < allSearchResults.length; j++) {
				SearchMatch searchResult= allSearchResults[j];
				if (! isRealConstructorReferenceNode(ASTNodeSearchUtil.getAstNode(searchResult, cuNode)))
					realConstructorReferences.remove(searchResult);
			}
			if (! realConstructorReferences.isEmpty())
				result.add(new SearchResultGroup(group.getResource(), realConstructorReferences.toArray(new SearchMatch[realConstructorReferences.size()])));
		}
		return result.toArray(new SearchResultGroup[result.size()]);
	}

	//XXX this method is a workaround for jdt core bug 27236
	private boolean isRealConstructorReferenceNode(ASTNode node){
		String typeName= fConstructors[0].getDeclaringType().getElementName();
		if (node.getParent() instanceof AbstractTypeDeclaration
				&& ((AbstractTypeDeclaration) node.getParent()).getNameProperty().equals(node.getLocationInParent())) {
			//Example:
			//	class A{
			//	    A(){}
			//	}
			//	class B extends A {}
			//==> "B" is found as reference to A()
			return false;
		}
		if (node.getParent() instanceof MethodDeclaration
				&& MethodDeclaration.NAME_PROPERTY.equals(node.getLocationInParent())) {
			MethodDeclaration md= (MethodDeclaration)node.getParent();
			if (md.isConstructor() && ! md.getName().getIdentifier().equals(typeName)) {
				//Example:
				//	class A{
				//	    A(){}
				//	}
				//	class B extends A{
				//	    B(){}
				//	}
				//==> "B" in "B(){}" is found as reference to A()
				return false;
			}
		}
		return true;
	}

	private IJavaSearchScope createSearchScope() throws JavaModelException{
		if (fConstructors.length == 0)
			return RefactoringScopeFactory.create(fType);
		return RefactoringScopeFactory.create(getMostVisibleConstructor());
	}

	private IMethod getMostVisibleConstructor() throws JavaModelException {
		Assert.isTrue(fConstructors.length > 0);
		IMethod candidate= fConstructors[0];
		int visibility= JdtFlags.getVisibilityCode(fConstructors[0]);
		for (int i= 1; i < fConstructors.length; i++) {
			IMethod constructor= fConstructors[i];
			if (JdtFlags.isHigherVisibility(JdtFlags.getVisibilityCode(constructor), visibility))
				candidate= constructor;
		}
		return candidate;
	}

	private SearchResultGroup[] getImplicitConstructorReferences(IProgressMonitor pm, WorkingCopyOwner owner, RefactoringStatus status) throws JavaModelException {
		pm.beginTask("", 2); //$NON-NLS-1$
		List<SearchMatch> searchMatches= new ArrayList<SearchMatch>();
		searchMatches.addAll(getImplicitConstructorReferencesFromHierarchy(owner, new SubProgressMonitor(pm, 1)));
		searchMatches.addAll(getImplicitConstructorReferencesInClassCreations(owner, new SubProgressMonitor(pm, 1), status));
		pm.done();
		return RefactoringSearchEngine.groupByCu(searchMatches.toArray(new SearchMatch[searchMatches.size()]), status);
	}

	//List of SearchResults
	private List<SearchMatch> getImplicitConstructorReferencesInClassCreations(WorkingCopyOwner owner, IProgressMonitor pm, RefactoringStatus status) throws JavaModelException {
		//XXX workaround for jdt core bug 23112
		SearchPattern pattern= SearchPattern.createPattern(fType, IJavaSearchConstants.REFERENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE);
		IJavaSearchScope scope= RefactoringScopeFactory.create(fType);
		SearchResultGroup[] refs= RefactoringSearchEngine.search(pattern, owner, scope, pm, status);
		List<SearchMatch> result= new ArrayList<SearchMatch>();
		for (int i= 0; i < refs.length; i++) {
			SearchResultGroup group= refs[i];
			ICompilationUnit cu= group.getCompilationUnit();
			if (cu == null)
				continue;
			CompilationUnit cuNode= new RefactoringASTParser(ASTProvider.SHARED_AST_LEVEL).parse(cu, false);
			SearchMatch[] results= group.getSearchResults();
			for (int j= 0; j < results.length; j++) {
				SearchMatch searchResult= results[j];
				ASTNode node= ASTNodeSearchUtil.getAstNode(searchResult, cuNode);
				if (isImplicitConstructorReferenceNodeInClassCreations(node))
					result.add(searchResult);
			}
		}
		return result;
	}

	public static boolean isImplicitConstructorReferenceNodeInClassCreations(ASTNode node) {
		if (node instanceof Type) {
			final ASTNode parent= node.getParent();
			if (parent instanceof ClassInstanceCreation) {
				return (node.equals(((ClassInstanceCreation) parent).getType()));
			} else if (parent instanceof ParameterizedType) {
				final ASTNode grandParent= parent.getParent();
				if (grandParent instanceof ClassInstanceCreation) {
					final ParameterizedType type= (ParameterizedType) ((ClassInstanceCreation) grandParent).getType();
					return (node.equals(type.getType()));
				}
			}
		}
		return false;
	}

	//List of SearchResults
	private List<SearchMatch> getImplicitConstructorReferencesFromHierarchy(WorkingCopyOwner owner, IProgressMonitor pm) throws JavaModelException{
		IType[] subTypes= getNonBinarySubtypes(owner, fType, pm);
		List<SearchMatch> result= new ArrayList<SearchMatch>(subTypes.length);
		for (int i= 0; i < subTypes.length; i++) {
			result.addAll(getAllSuperConstructorInvocations(subTypes[i]));
		}
		return result;
	}

	private static IType[] getNonBinarySubtypes(WorkingCopyOwner owner, IType type, IProgressMonitor monitor) throws JavaModelException{
		ITypeHierarchy hierarchy= null;
		if (owner == null)
			hierarchy= type.newTypeHierarchy(monitor);
		else
			hierarchy= type.newSupertypeHierarchy(owner, monitor);
		IType[] subTypes= hierarchy.getAllSubtypes(type);
		List<IType> result= new ArrayList<IType>(subTypes.length);
		for (int i= 0; i < subTypes.length; i++) {
			if (! subTypes[i].isBinary()) {
				result.add(subTypes[i]);
			}
		}
		return result.toArray(new IType[result.size()]);
	}

	//Collection of SearchResults
	private static Collection<SearchMatch> getAllSuperConstructorInvocations(IType type) throws JavaModelException {
		IMethod[] constructors= JavaElementUtil.getAllConstructors(type);
		CompilationUnit cuNode= new RefactoringASTParser(ASTProvider.SHARED_AST_LEVEL).parse(type.getCompilationUnit(), false);
		List<SearchMatch> result= new ArrayList<SearchMatch>(constructors.length);
		for (int i= 0; i < constructors.length; i++) {
			ASTNode superCall= getSuperConstructorCallNode(constructors[i], cuNode);
			if (superCall != null)
				result.add(createSearchResult(superCall, constructors[i]));
		}
		return result;
	}

	private static SearchMatch createSearchResult(ASTNode superCall, IMethod constructor) {
		int start= superCall.getStartPosition();
		int end= ASTNodes.getInclusiveEnd(superCall); //TODO: why inclusive?
		IResource resource= constructor.getResource();
		return new SearchMatch(constructor, SearchMatch.A_ACCURATE, start, end - start,
				SearchEngine.getDefaultSearchParticipant(), resource);
	}

	private static SuperConstructorInvocation getSuperConstructorCallNode(IMethod constructor, CompilationUnit cuNode) throws JavaModelException {
		Assert.isTrue(constructor.isConstructor());
		MethodDeclaration constructorNode= ASTNodeSearchUtil.getMethodDeclarationNode(constructor, cuNode);
		Assert.isTrue(constructorNode.isConstructor());
		Block body= constructorNode.getBody();
		Assert.isNotNull(body);
		List<Statement> statements= body.statements();
		if (! statements.isEmpty() && statements.get(0) instanceof SuperConstructorInvocation)
			return (SuperConstructorInvocation)statements.get(0);
		return null;
	}
}

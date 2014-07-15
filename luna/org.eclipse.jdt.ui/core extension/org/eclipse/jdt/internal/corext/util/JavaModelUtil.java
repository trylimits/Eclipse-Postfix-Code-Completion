/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman, mpchapman@gmail.com - 89977 Make JDT .java agnostic
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;

import org.eclipse.text.edits.TextEdit;

import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.CharOperation;

import org.eclipse.jdt.internal.corext.CorextMessages;
import org.eclipse.jdt.internal.corext.ValidateEditException;

import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstall2;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;

/**
 * Utility methods for the Java Model.
 * 
 * @see JDTUIHelperClasses
 */
public final class JavaModelUtil {
	
	/**
	 * The latest available {@link JavaCore}{@code #VERSION_*} level.
	 * @since 3.7
	 */
	public static final String VERSION_LATEST;
	static {
		VERSION_LATEST= JavaCore.VERSION_1_8; // make sure it is not inlined
	}
	
	/**
	 * Only use this suffix for creating new .java files.
	 * In general, use one of the three *JavaLike*(..) methods in JavaCore or create
	 * a name from an existing compilation unit with {@link #getRenamedCUName(ICompilationUnit, String)}
	 * <p>
	 * Note: Unlike {@link JavaCore#getJavaLikeExtensions()}, this suffix includes a leading ".".
	 * </p>
	 *
	 * @see JavaCore#getJavaLikeExtensions()
	 * @see JavaCore#isJavaLikeFileName(String)
	 * @see JavaCore#removeJavaLikeExtension(String)
	 * @see #getRenamedCUName(ICompilationUnit, String)
	 */
	public static final String DEFAULT_CU_SUFFIX= ".java"; //$NON-NLS-1$

	/**
	 * The name of the package-info.java file.
	 * @since 3.8
	 */
	public static final String PACKAGE_INFO_JAVA= "package-info.java"; //$NON-NLS-1$
	
	/**
	 * The name of the package-info.class file.
	 * @since 3.9
	 */
	public static final String PACKAGE_INFO_CLASS= "package-info.class"; //$NON-NLS-1$

	/**
	 * The name of the package.html file.
	 * @since 3.9
	 */
	public static final String PACKAGE_HTML= "package.html"; //$NON-NLS-1$

	/**
	 * Finds a type container by container name. The returned element will be of type
	 * <code>IType</code> or a <code>IPackageFragment</code>. <code>null</code> is returned if the
	 * type container could not be found.
	 * 
	 * @param jproject The Java project defining the context to search
	 * @param typeContainerName A dot separated name of the type container
	 * @return returns the container
	 * @throws JavaModelException thrown when the project can not be accessed
	 * @see #getTypeContainerName(IType)
	 */
	public static IJavaElement findTypeContainer(IJavaProject jproject, String typeContainerName) throws JavaModelException {
		// try to find it as type
		IJavaElement result= jproject.findType(typeContainerName);
		if (result == null) {
			// find it as package
			IPath path= new Path(typeContainerName.replace('.', '/'));
			result= jproject.findElement(path);
			if (!(result instanceof IPackageFragment)) {
				result= null;
			}

		}
		return result;
	}

	/**
	 * Finds a type in a compilation unit. Typical usage is to find the corresponding
	 * type in a working copy.
	 * @param cu the compilation unit to search in
	 * @param typeQualifiedName the type qualified name (type name with enclosing type names (separated by dots))
	 * @return the type found, or null if not existing
	 * @throws JavaModelException thrown when the cu can not be accessed
	 */
	public static IType findTypeInCompilationUnit(ICompilationUnit cu, String typeQualifiedName) throws JavaModelException {
		IType[] types= cu.getAllTypes();
		for (int i= 0; i < types.length; i++) {
			String currName= types[i].getTypeQualifiedName('.');
			if (typeQualifiedName.equals(currName)) {
				return types[i];
			}
		}
		return null;
	}

	/**
	 * Returns the element of the given compilation unit which is "equal" to the
	 * given element. Note that the given element usually has a parent different
	 * from the given compilation unit.
	 *
	 * @param cu the cu to search in
	 * @param element the element to look for
	 * @return an element of the given cu "equal" to the given element
	 */
	public static IJavaElement findInCompilationUnit(ICompilationUnit cu, IJavaElement element) {
		IJavaElement[] elements= cu.findElements(element);
		if (elements != null && elements.length > 0) {
			return elements[0];
		}
		return null;
	}

	/**
	 * Returns the fully qualified name of a type's container. (package name or enclosing type name)
	 * @param type the type
	 * @return the type container name
	 */
	public static String getTypeContainerName(IType type) {
		IType outerType= type.getDeclaringType();
		if (outerType != null) {
			return outerType.getFullyQualifiedName('.');
		} else {
			return type.getPackageFragment().getElementName();
		}
	}

	/**
	 * Concatenates two names. Uses a dot for separation.
	 * Both strings can be empty or <code>null</code>.
	 * @param name1 the first name
	 * @param name2 the second name
	 * @return the concatenated name
	 */
	public static String concatenateName(String name1, String name2) {
		StringBuffer buf= new StringBuffer();
		if (name1 != null && name1.length() > 0) {
			buf.append(name1);
		}
		if (name2 != null && name2.length() > 0) {
			if (buf.length() > 0) {
				buf.append('.');
			}
			buf.append(name2);
		}
		return buf.toString();
	}

	/**
	 * Concatenates two names. Uses a dot for separation.
	 * Both strings can be empty or <code>null</code>.
	 * @param name1 the first string
	 * @param name2 the second string
	 * @return the concatenated string
	 */
	public static String concatenateName(char[] name1, char[] name2) {
		StringBuffer buf= new StringBuffer();
		if (name1 != null && name1.length > 0) {
			buf.append(name1);
		}
		if (name2 != null && name2.length > 0) {
			if (buf.length() > 0) {
				buf.append('.');
			}
			buf.append(name2);
		}
		return buf.toString();
	}

	/**
	 * Returns whether the two names match. They match if they
	 * are equal, or if they are the same name but one is missing a dot-separated qualifier.
	 * 
	 * @param nameA a potentially qualified name 
	 * @param nameB a potentially qualified name
	 * @return <code>true</code> iff the given names match
	 * @since 3.8
	 */
	public static boolean isMatchingName(String nameA, String nameB) {
		int a= nameA.length();
		int b= nameB.length();
		if (a == b) {
			return nameA.equals(nameB);
		} else if (a < b - 1) {
			return nameB.endsWith(nameA) && nameB.charAt(b - a - 1) == '.';
		} else if (b < a - 1) {
			return nameA.endsWith(nameB) && nameA.charAt(a - b - 1) == '.';
		} else {
			return false;
		}
	}

	/**
	 * Evaluates if a member (possible from another package) is visible from
	 * elements in a package.
	 * @param member The member to test the visibility for
	 * @param pack The package in focus
	 * @return returns <code>true</code> if the member is visible from the package
	 * @throws JavaModelException thrown when the member can not be accessed
	 */
	public static boolean isVisible(IMember member, IPackageFragment pack) throws JavaModelException {

		int type= member.getElementType();
		if  (type == IJavaElement.INITIALIZER ||  (type == IJavaElement.METHOD && member.getElementName().startsWith("<"))) { //$NON-NLS-1$
			return false;
		}

		int otherflags= member.getFlags();
		IType declaringType= member.getDeclaringType();
		if (Flags.isPublic(otherflags) || (declaringType != null && isInterfaceOrAnnotation(declaringType))) {
			return true;
		} else if (Flags.isPrivate(otherflags)) {
			return false;
		}

		IPackageFragment otherpack= (IPackageFragment) member.getAncestor(IJavaElement.PACKAGE_FRAGMENT);
		return (pack != null && otherpack != null && isSamePackage(pack, otherpack));
	}

	/**
	 * Evaluates if a member in the focus' element hierarchy is visible from
	 * elements in a package.
	 * @param member The member to test the visibility for
	 * @param pack The package of the focus element focus
	 * @return returns <code>true</code> if the member is visible from the package
	 * @throws JavaModelException thrown when the member can not be accessed
	 */
	public static boolean isVisibleInHierarchy(IMember member, IPackageFragment pack) throws JavaModelException {
		int type= member.getElementType();
		if  (type == IJavaElement.INITIALIZER ||  (type == IJavaElement.METHOD && member.getElementName().startsWith("<"))) { //$NON-NLS-1$
			return false;
		}

		int otherflags= member.getFlags();

		IType declaringType= member.getDeclaringType();
		if (Flags.isPublic(otherflags) || Flags.isProtected(otherflags) || (declaringType != null && isInterfaceOrAnnotation(declaringType))) {
			return true;
		} else if (Flags.isPrivate(otherflags)) {
			return false;
		}

		IPackageFragment otherpack= (IPackageFragment) member.getAncestor(IJavaElement.PACKAGE_FRAGMENT);
		return (pack != null && pack.equals(otherpack));
	}


	/**
	 * Returns the package fragment root of <code>IJavaElement</code>. If the given
	 * element is already a package fragment root, the element itself is returned.
	 * @param element the element
	 * @return the package fragment root of the element or <code>null</code>
	 */
	public static IPackageFragmentRoot getPackageFragmentRoot(IJavaElement element) {
		return (IPackageFragmentRoot) element.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
	}

	/**
	 * Finds a method in a type.
	 * This searches for a method with the same name and signature. Parameter types are only
	 * compared by the simple name, no resolving for the fully qualified type name is done.
	 * Constructors are only compared by parameters, not the name.
	 * @param name The name of the method to find
	 * @param paramTypes The type signatures of the parameters e.g. <code>{"QString;","I"}</code>
	 * @param isConstructor If the method is a constructor
	 * @param type the type
	 * @return The first found method or <code>null</code>, if nothing foun
	 * @throws JavaModelException thrown when the type can not be accessed
	 */
	public static IMethod findMethod(String name, String[] paramTypes, boolean isConstructor, IType type) throws JavaModelException {
		IMethod[] methods= type.getMethods();
		for (int i= 0; i < methods.length; i++) {
			if (isSameMethodSignature(name, paramTypes, isConstructor, methods[i])) {
				return methods[i];
			}
		}
		return null;
	}

	/**
	 * Finds a method in a type and all its super types. The super class hierarchy is searched first, then the super interfaces.
	 * This searches for a method with the same name and signature. Parameter types are only
	 * compared by the simple name, no resolving for the fully qualified type name is done.
	 * Constructors are only compared by parameters, not the name.
	 * NOTE: For finding overridden methods or for finding the declaring method, use {@link MethodOverrideTester}
	 * @param hierarchy The hierarchy containing the type
	 * 	@param type The type to start the search from
	 * @param name The name of the method to find
	 * @param paramTypes The type signatures of the parameters e.g. <code>{"QString;","I"}</code>
	 * @param isConstructor If the method is a constructor
	 * @return The first found method or <code>null</code>, if nothing found
	 * @throws JavaModelException thrown when the type can not be accessed
	 */
	public static IMethod findMethodInHierarchy(ITypeHierarchy hierarchy, IType type, String name, String[] paramTypes, boolean isConstructor) throws JavaModelException {
		IMethod method= findMethod(name, paramTypes, isConstructor, type);
		if (method != null) {
			return method;
		}
		IType superClass= hierarchy.getSuperclass(type);
		if (superClass != null) {
			IMethod res=  findMethodInHierarchy(hierarchy, superClass, name, paramTypes, isConstructor);
			if (res != null) {
				return res;
			}
		}
		if (!isConstructor) {
			IType[] superInterfaces= hierarchy.getSuperInterfaces(type);
			for (int i= 0; i < superInterfaces.length; i++) {
				IMethod res= findMethodInHierarchy(hierarchy, superInterfaces[i], name, paramTypes, false);
				if (res != null) {
					return res;
				}
			}
		}
		return method;
	}


	/**
	 * Tests if a method equals to the given signature.
	 * Parameter types are only compared by the simple name, no resolving for
	 * the fully qualified type name is done. Constructors are only compared by
	 * parameters, not the name.
	 * @param name Name of the method
	 * @param paramTypes The type signatures of the parameters e.g. <code>{"QString;","I"}</code>
	 * @param isConstructor Specifies if the method is a constructor
	 * @param curr the method
	 * @return Returns <code>true</code> if the method has the given name and parameter types and constructor state.
	 * @throws JavaModelException thrown when the method can not be accessed
	 */
	public static boolean isSameMethodSignature(String name, String[] paramTypes, boolean isConstructor, IMethod curr) throws JavaModelException {
		if (isConstructor || name.equals(curr.getElementName())) {
			if (isConstructor == curr.isConstructor()) {
				String[] currParamTypes= curr.getParameterTypes();
				if (paramTypes.length == currParamTypes.length) {
					for (int i= 0; i < paramTypes.length; i++) {
						String t1= Signature.getSimpleName(Signature.toString(paramTypes[i]));
						String t2= Signature.getSimpleName(Signature.toString(currParamTypes[i]));
						if (!t1.equals(t2)) {
							return false;
						}
					}
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Tests if two <code>IPackageFragment</code>s represent the same logical java package.
	 * @param pack1 the first package
	 * @param pack2 the second package
	 * @return <code>true</code> if the package fragments' names are equal.
	 */
	public static boolean isSamePackage(IPackageFragment pack1, IPackageFragment pack2) {
		return pack1.getElementName().equals(pack2.getElementName());
	}

	/**
	 * Checks whether the given type has a valid main method or not.
	 * @param type the type to test
	 * @return returns <code>true</code> if the type has a main method
	 * @throws JavaModelException thrown when the type can not be accessed
	 */
	public static boolean hasMainMethod(IType type) throws JavaModelException {
		IMethod[] methods= type.getMethods();
		for (int i= 0; i < methods.length; i++) {
			if (methods[i].isMainMethod()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the field is boolean.
	 * @param field the field
	 * @return returns <code>true</code> if the field returns a boolean
	 * @throws JavaModelException thrown when the field can not be accessed
	 */
	public static boolean isBoolean(IField field) throws JavaModelException{
		return field.getTypeSignature().equals(Signature.SIG_BOOLEAN);
	}

	/**
	 * @param type the type to test
	 * @return <code>true</code> iff the type is an interface or an annotation
	 * @throws JavaModelException thrown when the field can not be accessed
	 */
	public static boolean isInterfaceOrAnnotation(IType type) throws JavaModelException {
		return type.isInterface();
	}

	/**
	 * Resolves a type name in the context of the declaring type.
	 *
	 * @param refTypeSig the type name in signature notation (for example 'QVector') this can also be an array type, but dimensions will be ignored.
	 * @param declaringType the context for resolving (type where the reference was made in)
	 * @return returns the fully qualified type name or build-in-type name. if a unresolved type couldn't be resolved null is returned
	 * @throws JavaModelException thrown when the type can not be accessed
	 */
	public static String getResolvedTypeName(String refTypeSig, IType declaringType) throws JavaModelException {
		int arrayCount= Signature.getArrayCount(refTypeSig);
		char type= refTypeSig.charAt(arrayCount);
		if (type == Signature.C_UNRESOLVED) {
			String name= ""; //$NON-NLS-1$
			int bracket= refTypeSig.indexOf(Signature.C_GENERIC_START, arrayCount + 1);
			if (bracket > 0)
				name= refTypeSig.substring(arrayCount + 1, bracket);
			else {
				int semi= refTypeSig.indexOf(Signature.C_SEMICOLON, arrayCount + 1);
				if (semi == -1) {
					throw new IllegalArgumentException();
				}
				name= refTypeSig.substring(arrayCount + 1, semi);
			}
			String[][] resolvedNames= declaringType.resolveType(name);
			if (resolvedNames != null && resolvedNames.length > 0) {
				return JavaModelUtil.concatenateName(resolvedNames[0][0], resolvedNames[0][1]);
			}
			return null;
		} else {
			return Signature.toString(refTypeSig.substring(arrayCount));
		}
	}

	/**
	 * Returns if a CU can be edited.
	 * @param cu the compilation unit
	 * @return <code>true</code> if the CU can be edited
	 */
	public static boolean isEditable(ICompilationUnit cu)  {
		Assert.isNotNull(cu);
		IResource resource= cu.getPrimary().getResource();
		return (resource.exists() && !resource.getResourceAttributes().isReadOnly());
	}

	/**
	 * Returns true if a cu is a primary cu (original or shared working copy)
	 * @param cu the compilation unit
	 * @return return <code>true</code> if the CU is primary
	 */
	public static boolean isPrimary(ICompilationUnit cu) {
		return cu.getOwner() == null;
	}

	/**
	 * Checks whether the given type signature is from a primitive type.
	 * 
	 * @param typeSignature the type signature string to check
	 * @return <code>true</code> if the type is a primitive type, <code> false</code> otherwise
	 * @throws JavaModelException if this element does not exist or if an exception occurs while
	 *             accessing its corresponding resource.
	 * @since 3.7
	 */
	public static boolean isPrimitive(String typeSignature) throws JavaModelException {
		return Signature.getTypeSignatureKind(Signature.getElementType(typeSignature)) == Signature.BASE_TYPE_SIGNATURE;
	}

	/*
     * Don't log not-exists exceptions
     *
     * Also see bug http://bugs.eclipse.org/bugs/show_bug.cgi?id=19253.
     * Since 3.4 we also don't log non-exists exception in non-working copies.
	 */
	public static boolean isExceptionToBeLogged(CoreException exception) {
		if (!(exception instanceof JavaModelException))
			return true;
		JavaModelException je= (JavaModelException)exception;
		if (!je.isDoesNotExist())
			return true;
		return false;
	}

	public static IType[] getAllSuperTypes(IType type, IProgressMonitor pm) throws JavaModelException {
		try {
			// workaround for 23656
			IType[] superTypes= SuperTypeHierarchyCache.getTypeHierarchy(type).getAllSupertypes(type);
			if (type.isInterface()) {
				IType objekt= type.getJavaProject().findType("java.lang.Object");//$NON-NLS-1$
				if (objekt != null) {
					IType[] superInterfacesAndObject= new IType[superTypes.length + 1];
					System.arraycopy(superTypes, 0, superInterfacesAndObject, 0, superTypes.length);
					superInterfacesAndObject[superTypes.length]= objekt;
					return superInterfacesAndObject;
				}
			}
			return superTypes;
		} finally {
			if (pm != null)
				pm.done();
		}
	}

	public static boolean isSuperType(ITypeHierarchy hierarchy, IType possibleSuperType, IType type) {
		// filed bug 112635 to add this method to ITypeHierarchy
		IType superClass= hierarchy.getSuperclass(type);
		if (superClass != null && (possibleSuperType.equals(superClass) || isSuperType(hierarchy, possibleSuperType, superClass))) {
			return true;
		}
		if (Flags.isInterface(hierarchy.getCachedFlags(possibleSuperType))) {
			IType[] superInterfaces= hierarchy.getSuperInterfaces(type);
			for (int i= 0; i < superInterfaces.length; i++) {
				IType curr= superInterfaces[i];
				if (possibleSuperType.equals(curr) || isSuperType(hierarchy, possibleSuperType, curr)) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isExcludedPath(IPath resourcePath, IPath[] exclusionPatterns) {
		char[] path = resourcePath.toString().toCharArray();
		for (int i = 0, length = exclusionPatterns.length; i < length; i++) {
			char[] pattern= exclusionPatterns[i].toString().toCharArray();
			if (CharOperation.pathMatch(pattern, path, true, '/')) {
				return true;
			}
		}
		return false;
	}


	/**
	 * Returns whether the given resource path matches one of the exclusion
	 * patterns.
	 * @param resourcePath the resource path
	 * @param exclusionPatterns the exclusion patterns
	 * @return returns <code> true</code> if the given resource path matches one of the exclusion
	 *
	 * @see IClasspathEntry#getExclusionPatterns
	 */
	public static boolean isExcluded(IPath resourcePath, char[][] exclusionPatterns) {
		if (exclusionPatterns == null) return false;
		char[] path = resourcePath.toString().toCharArray();
		for (int i = 0, length = exclusionPatterns.length; i < length; i++)
			if (CharOperation.pathMatch(exclusionPatterns[i], path, true, '/'))
				return true;
		return false;
	}


	/**
	 * Force a reconcile of a compilation unit.
	 * @param unit the compilation unit
	 * @throws JavaModelException thrown when the compilation unit can not be accessed
	 */
	public static void reconcile(ICompilationUnit unit) throws JavaModelException {
		unit.reconcile(
				ICompilationUnit.NO_AST,
				false /* don't force problem detection */,
				null /* use primary owner */,
				null /* no progress monitor */);
	}

	/**
	 * Helper method that tests if an classpath entry can be found in a
	 * container. <code>null</code> is returned if the entry can not be found
	 * or if the container does not allows the configuration of source
	 * attachments
	 * @param jproject The container's parent project
	 * @param containerPath The path of the container
	 * @param libPath The path of the library to be found
	 * @return IClasspathEntry A classpath entry from the container of
	 * <code>null</code> if the container can not be modified.
	 * @throws JavaModelException thrown if accessing the container failed
	 */
	public static IClasspathEntry getClasspathEntryToEdit(IJavaProject jproject, IPath containerPath, IPath libPath) throws JavaModelException {
		IClasspathContainer container= JavaCore.getClasspathContainer(containerPath, jproject);
		ClasspathContainerInitializer initializer= JavaCore.getClasspathContainerInitializer(containerPath.segment(0));
		if (container != null && initializer != null && initializer.canUpdateClasspathContainer(containerPath, jproject)) {
			return findEntryInContainer(container, libPath);
		}
		return null; // attachment not possible
	}

	/**
	 * Finds an entry in a container. <code>null</code> is returned if the entry can not be found
	 * @param container The container
	 * @param libPath The path of the library to be found
	 * @return IClasspathEntry A classpath entry from the container of
	 * <code>null</code> if the container can not be modified.
	 */
	public static IClasspathEntry findEntryInContainer(IClasspathContainer container, IPath libPath) {
		IClasspathEntry[] entries= container.getClasspathEntries();
		for (int i= 0; i < entries.length; i++) {
			IClasspathEntry curr= entries[i];
			IClasspathEntry resolved= JavaCore.getResolvedClasspathEntry(curr);
			if (resolved != null && libPath.equals(resolved.getPath())) {
				return curr; // return the real entry
			}
		}
		return null; // attachment not possible
	}

	/**
	 * Returns the classpath entry of the given package fragment root. This is the raw entry, except
	 * if the root is a referenced library, in which case it's the resolved entry.
	 * 
	 * @param root a package fragment root
	 * @return the corresponding classpath entry
	 * @throws JavaModelException if accessing the entry failed
	 * @since 3.6
	 */
	public static IClasspathEntry getClasspathEntry(IPackageFragmentRoot root) throws JavaModelException {
		IClasspathEntry rawEntry= root.getRawClasspathEntry();
		int rawEntryKind= rawEntry.getEntryKind();
		switch (rawEntryKind) {
			case IClasspathEntry.CPE_LIBRARY:
			case IClasspathEntry.CPE_VARIABLE:
			case IClasspathEntry.CPE_CONTAINER: // should not happen, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=305037
				if (root.isArchive() && root.getKind() == IPackageFragmentRoot.K_BINARY) {
					IClasspathEntry resolvedEntry= root.getResolvedClasspathEntry();
					if (resolvedEntry.getReferencingEntry() != null)
						return resolvedEntry;
					else
						return rawEntry;
				}
		}
		return rawEntry;
	}

	/**
	 * Get all compilation units of a selection.
	 *
	 * @param javaElements the selected java elements
	 * @return all compilation units containing and contained in elements from javaElements
	 * @throws JavaModelException if this element does not exist or if an exception occurs while
	 *             accessing its corresponding resource
	 */
	public static ICompilationUnit[] getAllCompilationUnits(IJavaElement[] javaElements) throws JavaModelException {
		HashSet<ICompilationUnit> result= new HashSet<ICompilationUnit>();
		for (int i= 0; i < javaElements.length; i++) {
			addAllCus(result, javaElements[i]);
		}
		return result.toArray(new ICompilationUnit[result.size()]);
	}

	private static void addAllCus(HashSet<ICompilationUnit> collector, IJavaElement javaElement) throws JavaModelException {
		switch (javaElement.getElementType()) {
			case IJavaElement.JAVA_PROJECT:
				IJavaProject javaProject= (IJavaProject) javaElement;
				IPackageFragmentRoot[] packageFragmentRoots= javaProject.getPackageFragmentRoots();
				for (int i= 0; i < packageFragmentRoots.length; i++)
					addAllCus(collector, packageFragmentRoots[i]);
				return;

			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				IPackageFragmentRoot packageFragmentRoot= (IPackageFragmentRoot) javaElement;
				if (packageFragmentRoot.getKind() != IPackageFragmentRoot.K_SOURCE)
					return;
				IJavaElement[] packageFragments= packageFragmentRoot.getChildren();
				for (int j= 0; j < packageFragments.length; j++)
					addAllCus(collector, packageFragments[j]);
				return;

			case IJavaElement.PACKAGE_FRAGMENT:
				IPackageFragment packageFragment= (IPackageFragment) javaElement;
				collector.addAll(Arrays.asList(packageFragment.getCompilationUnits()));
				return;

			case IJavaElement.COMPILATION_UNIT:
				collector.add((ICompilationUnit) javaElement);
				return;

			default:
				IJavaElement cu= javaElement.getAncestor(IJavaElement.COMPILATION_UNIT);
				if (cu != null)
					collector.add((ICompilationUnit) cu);
		}
	}


	public static void setComplianceOptions(Map<String, String> map, String compliance) {
		JavaCore.setComplianceOptions(compliance, map);
	}

	public static void setDefaultClassfileOptions(Map<String, String> map, String compliance) {
		map.put(JavaCore.COMPILER_CODEGEN_INLINE_JSR_BYTECODE, is50OrHigher(compliance) ? JavaCore.ENABLED : JavaCore.DISABLED);
		map.put(JavaCore.COMPILER_LOCAL_VARIABLE_ATTR, JavaCore.GENERATE);
		map.put(JavaCore.COMPILER_LINE_NUMBER_ATTR, JavaCore.GENERATE);
		map.put(JavaCore.COMPILER_SOURCE_FILE_ATTR, JavaCore.GENERATE);
		map.put(JavaCore.COMPILER_CODEGEN_UNUSED_LOCAL, JavaCore.PRESERVE);
	}

	/**
	 * @param version1 the first version
	 * @param version2 the second version
	 * @return <code>true</code> iff version1 is less than version2
	 */
	public static boolean isVersionLessThan(String version1, String version2) {
		if (JavaCore.VERSION_CLDC_1_1.equals(version1)) {
			version1= JavaCore.VERSION_1_1 + 'a';
		}
		if (JavaCore.VERSION_CLDC_1_1.equals(version2)) {
			version2= JavaCore.VERSION_1_1 + 'a';
		}
		return version1.compareTo(version2) < 0;
	}


	public static boolean is50OrHigher(String compliance) {
		return !isVersionLessThan(compliance, JavaCore.VERSION_1_5);
	}
	
	public static boolean is17OrHigher(String compliance) {
		return !isVersionLessThan(compliance, JavaCore.VERSION_1_7);
	}

	public static boolean is18OrHigher(String compliance) {
		return !isVersionLessThan(compliance, JavaCore.VERSION_1_8);
	}

	/**
	 * Checks if the given project or workspace has source compliance 1.5 or greater.
	 *
	 * @param project the project to test or <code>null</code> to test the workspace settings
	 * @return <code>true</code> if the given project or workspace has source compliance 1.5 or greater.
	 */
	public static boolean is50OrHigher(IJavaProject project) {
		return is50OrHigher(getSourceCompliance(project));
	}

	/**
	 * Checks if the given project or workspace has source compliance 1.7 or greater.
	 *
	 * @param project the project to test or <code>null</code> to test the workspace settings
	 * @return <code>true</code> if the given project or workspace has source compliance 1.7 or greater.
	 */
	public static boolean is17OrHigher(IJavaProject project) {
		return is17OrHigher(getSourceCompliance(project));
	}
	
	/**
	 * Checks if the given project or workspace has source compliance 1.8 or greater.
	 * 
	 * @param project the project to test or <code>null</code> to test the workspace settings
	 * @return <code>true</code> if the given project or workspace has source compliance 1.8 or
	 *         greater.
	 */
	public static boolean is18OrHigher(IJavaProject project) {
		return is18OrHigher(getSourceCompliance(project));
	}

	private static String getSourceCompliance(IJavaProject project) {
		return project != null ? project.getOption(JavaCore.COMPILER_SOURCE, true) : JavaCore.getOption(JavaCore.COMPILER_SOURCE);
	}
	
	/**
	 * Checks if the JRE of the given project or workspace default JRE have source compliance 1.5 or
	 * greater.
	 *
	 * @param project the project to test or <code>null</code> to test the workspace JRE
	 * @return <code>true</code> if the JRE of the given project or workspace default JRE have
	 *         source compliance 1.5 or greater.
	 * @throws CoreException if unable to determine the project's VM install
	 */
	public static boolean is50OrHigherJRE(IJavaProject project) throws CoreException {
		IVMInstall vmInstall;
		if (project == null) {
			vmInstall= JavaRuntime.getDefaultVMInstall();
		} else {
			vmInstall= JavaRuntime.getVMInstall(project);
		}
		if (!(vmInstall instanceof IVMInstall2))
			return true; // assume 1.5.

		String compliance= getCompilerCompliance((IVMInstall2) vmInstall, null);
		if (compliance == null)
			return true; // assume 1.5
		return is50OrHigher(compliance);
	}

	public static String getCompilerCompliance(IVMInstall2 vMInstall, String defaultCompliance) {
		String version= vMInstall.getJavaVersion();
		if (version == null) {
			return defaultCompliance;
		} else if (version.startsWith(JavaCore.VERSION_1_8)) {
			return JavaCore.VERSION_1_8;
		} else if (version.startsWith(JavaCore.VERSION_1_7)) {
			return JavaCore.VERSION_1_7;
		} else if (version.startsWith(JavaCore.VERSION_1_6)) {
			return JavaCore.VERSION_1_6;
		} else if (version.startsWith(JavaCore.VERSION_1_5)) {
			return JavaCore.VERSION_1_5;
		} else if (version.startsWith(JavaCore.VERSION_1_4)) {
			return JavaCore.VERSION_1_4;
		} else if (version.startsWith(JavaCore.VERSION_1_3)) {
			return JavaCore.VERSION_1_3;
		} else if (version.startsWith(JavaCore.VERSION_1_2)) {
			return JavaCore.VERSION_1_3;
		} else if (version.startsWith(JavaCore.VERSION_1_1)) {
			return JavaCore.VERSION_1_3;
		}
		return defaultCompliance;
	}

	public static String getExecutionEnvironmentCompliance(IExecutionEnvironment executionEnvironment) {
		Map<String, String> complianceOptions= executionEnvironment.getComplianceOptions();
		if (complianceOptions != null) {
			Object compliance= complianceOptions.get(JavaCore.COMPILER_COMPLIANCE);
			if (compliance instanceof String)
				return (String)compliance;
		}
		
		// fallback:
		String desc= executionEnvironment.getId();
		if (desc.indexOf(JavaCore.VERSION_1_8) != -1) {
			return JavaCore.VERSION_1_8;
		} else if (desc.indexOf(JavaCore.VERSION_1_7) != -1) {
			return JavaCore.VERSION_1_7;
		} else if (desc.indexOf(JavaCore.VERSION_1_6) != -1) {
			return JavaCore.VERSION_1_6;
		} else if (desc.indexOf(JavaCore.VERSION_1_5) != -1) {
			return JavaCore.VERSION_1_5;
		} else if (desc.indexOf(JavaCore.VERSION_1_4) != -1) {
			return JavaCore.VERSION_1_4;
		}
		return JavaCore.VERSION_1_3;
	}

	/**
	 * Compute a new name for a compilation unit, given the name of the new main type.
	 * This query tries to maintain the existing extension (e.g. ".java").
	 *
	 * @param cu a compilation unit
	 * @param newMainName the new name of the cu's main type (without extension)
	 * @return the new name for the compilation unit
	 */
	public static String getRenamedCUName(ICompilationUnit cu, String newMainName) {
		String oldName = cu.getElementName();
		int i = oldName.lastIndexOf('.');
		if (i != -1) {
			return newMainName + oldName.substring(i);
		} else {
			return newMainName;
		}
	}

	/**
	 * Applies an text edit to a compilation unit. Filed bug 117694 against jdt.core.
	 * 	@param cu the compilation unit to apply the edit to
	 * 	@param edit the edit to apply
	 * @param save is set, save the CU after the edit has been applied
	 * @param monitor the progress monitor to use
	 * @throws CoreException Thrown when the access to the CU failed
	 * @throws ValidateEditException if validate edit fails
	 */
	public static void applyEdit(ICompilationUnit cu, TextEdit edit, boolean save, IProgressMonitor monitor) throws CoreException, ValidateEditException {
		IFile file= (IFile) cu.getResource();
		if (!save || !file.exists()) {
			cu.applyTextEdit(edit, monitor);
		} else {
			if (monitor == null) {
				monitor= new NullProgressMonitor();
			}
			monitor.beginTask(CorextMessages.JavaModelUtil_applyedit_operation, 2);
			try {
				IStatus status= Resources.makeCommittable(file, null);
				if (!status.isOK()) {
					throw new ValidateEditException(status);
				}

				cu.applyTextEdit(edit, new SubProgressMonitor(monitor, 1));

				cu.save(new SubProgressMonitor(monitor, 1), true);
			} finally {
				monitor.done();
			}
		}
	}

	public static boolean isImplicitImport(String qualifier, ICompilationUnit cu) {
		if ("java.lang".equals(qualifier)) {  //$NON-NLS-1$
			return true;
		}
		String packageName= cu.getParent().getElementName();
		if (qualifier.equals(packageName)) {
			return true;
		}
		String typeName= JavaCore.removeJavaLikeExtension(cu.getElementName());
		String mainTypeName= JavaModelUtil.concatenateName(packageName, typeName);
		return qualifier.equals(mainTypeName);
	}

	public static boolean isOpenableStorage(Object storage) {
		if (storage instanceof IJarEntryResource) {
			return ((IJarEntryResource) storage).isFile();
		} else {
			return storage instanceof IStorage;
		}
	}

	/**
	 * Tells whether the given CU is the package-info.java.
	 *
	 * @param cu the compilation unit to test
	 * @return <code>true</code> if the given CU is the package-info.java
	 * @since 3.4
	 */
	public static boolean isPackageInfo(ICompilationUnit cu) {
		return PACKAGE_INFO_JAVA.equals(cu.getElementName());
	}

	public static boolean isPolymorphicSignature(IMethod method) {
		return method.getAnnotation("java.lang.invoke.MethodHandle$PolymorphicSignature").exists(); //$NON-NLS-1$
	}

}

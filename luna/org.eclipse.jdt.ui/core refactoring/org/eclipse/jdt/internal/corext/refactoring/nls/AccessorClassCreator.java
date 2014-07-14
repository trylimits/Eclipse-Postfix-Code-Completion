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
package org.eclipse.jdt.internal.corext.refactoring.nls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import com.ibm.icu.text.Collator;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.text.edits.TextEdit;

import org.eclipse.ltk.core.refactoring.Change;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.formatter.CodeFormatter;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.refactoring.nls.changes.CreateTextFileChange;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.CodeGeneration;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.preferences.MembersOrderPreferenceCache;

public class AccessorClassCreator {

	private final ICompilationUnit fCu;
	private final String fAccessorClassName;
	private final IPath fAccessorPath;
	private final IPath fResourceBundlePath;
	private final IPackageFragment fAccessorPackage;
	private final boolean fIsEclipseNLS;
	private final NLSSubstitution[] fNLSSubstitutions;
	private final String fSubstitutionPattern;

	private AccessorClassCreator(ICompilationUnit cu, String accessorClassname, IPath accessorPath, IPackageFragment accessorPackage, IPath resourceBundlePath, boolean isEclipseNLS, NLSSubstitution[] nlsSubstitutions, String substitutionPattern) {
		fCu= cu;
		fAccessorClassName= accessorClassname;
		fAccessorPath= accessorPath;
		fAccessorPackage= accessorPackage;
		fResourceBundlePath= resourceBundlePath;
		fIsEclipseNLS= isEclipseNLS;
		fNLSSubstitutions= nlsSubstitutions;
		fSubstitutionPattern= substitutionPattern;
	}

	public static Change create(ICompilationUnit cu, String accessorClassname, IPath accessorPath, IPackageFragment accessorPackage, IPath resourceBundlePath, boolean isEclipseNLS, NLSSubstitution[] nlsSubstitutions, String substitutionPattern, IProgressMonitor pm) throws CoreException {
		AccessorClassCreator accessorClass= new AccessorClassCreator(cu, accessorClassname, accessorPath, accessorPackage, resourceBundlePath, isEclipseNLS, nlsSubstitutions, substitutionPattern);

		return new CreateTextFileChange(accessorPath, accessorClass.createAccessorCUSource(pm), null, "java"); //$NON-NLS-1$
	}

	private String createAccessorCUSource(IProgressMonitor pm) throws CoreException {
		IProject project= getFileHandle(fAccessorPath).getProject();
		String lineDelimiter= StubUtility.getLineDelimiterPreference(project);
		return CodeFormatterUtil.format(CodeFormatter.K_COMPILATION_UNIT, getUnformattedSource(pm), 0, lineDelimiter, fCu.getJavaProject());
	}

	private static IFile getFileHandle(IPath filePath) {
		if (filePath == null)
			return null;
		return ResourcesPlugin.getWorkspace().getRoot().getFile(filePath);
	}

	private String getUnformattedSource(IProgressMonitor pm) throws CoreException {
		ICompilationUnit newCu= null;
		try {
			newCu= fAccessorPackage.getCompilationUnit(fAccessorPath.lastSegment()).getWorkingCopy(null);

			String typeComment= null, fileComment= null;
			final IJavaProject project= newCu.getJavaProject();
			final String lineDelim= StubUtility.getLineDelimiterUsed(project);
			if (StubUtility.doAddComments(project)) {
				typeComment= CodeGeneration.getTypeComment(newCu, fAccessorClassName, lineDelim);
				fileComment= CodeGeneration.getFileComment(newCu, lineDelim);
			}
			String classContent= createClass(lineDelim);
			String cuContent= CodeGeneration.getCompilationUnitContent(newCu, fileComment, typeComment, classContent, lineDelim);
			if (cuContent == null) {
				StringBuffer buf= new StringBuffer();
				if (fileComment != null) {
					buf.append(fileComment).append(lineDelim);
				}
				if (!fAccessorPackage.isDefaultPackage()) {
					buf.append("package ").append(fAccessorPackage.getElementName()).append(';'); //$NON-NLS-1$
				}
				buf.append(lineDelim).append(lineDelim);
				if (typeComment != null) {
					buf.append(typeComment).append(lineDelim);
				}
				buf.append(classContent);
				cuContent= buf.toString();
			}

			newCu.getBuffer().setContents(cuContent);
			addImportsToAccessorCu(newCu, pm);
			return newCu.getSource();
		} finally {
			if (newCu != null) {
				newCu.discardWorkingCopy();
			}
		}
	}

	private void addImportsToAccessorCu(ICompilationUnit newCu, IProgressMonitor pm) throws CoreException {
		ImportRewrite is= StubUtility.createImportRewrite(newCu, true);
		if (fIsEclipseNLS) {
			is.addImport("org.eclipse.osgi.util.NLS"); //$NON-NLS-1$
		} else {
			is.addImport("java.util.MissingResourceException"); //$NON-NLS-1$
			is.addImport("java.util.ResourceBundle"); //$NON-NLS-1$
		}
		TextEdit edit= is.rewriteImports(pm);
		JavaModelUtil.applyEdit(newCu, edit, false, null);
	}

	private String createClass(String lineDelim) throws CoreException {
		if (fIsEclipseNLS) {
			MembersOrderPreferenceCache sortOrder= JavaPlugin.getDefault().getMemberOrderPreferenceCache();
			int constructorIdx= sortOrder.getCategoryIndex(MembersOrderPreferenceCache.CONSTRUCTORS_INDEX);
			int fieldIdx= sortOrder.getCategoryIndex(MembersOrderPreferenceCache.STATIC_FIELDS_INDEX);
			int initIdx= sortOrder.getCategoryIndex(MembersOrderPreferenceCache.STATIC_INIT_INDEX);

			String constructor= createConstructor(lineDelim) + lineDelim;
			String initializer= createStaticInitializer(lineDelim) + lineDelim;
			String fields= createStaticFields() + lineDelim;

			StringBuffer result= new StringBuffer();
			result.append("public class ").append(fAccessorClassName).append(" extends NLS {"); //$NON-NLS-1$ //$NON-NLS-2$
			result.append("private static final String ").append(NLSRefactoring.BUNDLE_NAME_FIELD).append(" = \"").append(getResourceBundleName()).append("\"; "); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			result.append(NLSElement.createTagText(1)).append(lineDelim);

			if (constructorIdx < fieldIdx) {
				if (fieldIdx < initIdx) {
					result.append(constructor);
					result.append(fields);
					result.append(initializer);
				} else {
					result.append(constructor);
					result.append(initializer);
					result.append(fields);
				}
			} else {
				if (constructorIdx < initIdx) {
					result.append(fields);
					result.append(constructor);
					result.append(initializer);
				} else {
					result.append(fields);
					result.append(initializer);
					result.append(constructor);
				}
			}

			result.append('}').append(lineDelim);

			return result.toString();
		} else {
			MembersOrderPreferenceCache sortOrder= JavaPlugin.getDefault().getMemberOrderPreferenceCache();
			int constructorIdx= sortOrder.getCategoryIndex(MembersOrderPreferenceCache.CONSTRUCTORS_INDEX);
			int methodIdx= sortOrder.getCategoryIndex(MembersOrderPreferenceCache.METHOD_INDEX);

			String constructor= lineDelim	+ createConstructor(lineDelim);
			String method= lineDelim + createGetStringMethod(lineDelim);

			StringBuffer result= new StringBuffer();
			result.append("public class ").append(fAccessorClassName).append(" {"); //$NON-NLS-1$ //$NON-NLS-2$
			result.append("private static final String ").append(NLSRefactoring.BUNDLE_NAME_FIELD); //$NON-NLS-1$
			result.append(" = \"").append(getResourceBundleName()).append("\"; ").append(NLSElement.createTagText(1)).append(lineDelim); //$NON-NLS-1$ //$NON-NLS-2$

			result.append(lineDelim).append("private static final ResourceBundle ").append(getResourceBundleConstantName()); //$NON-NLS-1$
			result.append("= ResourceBundle.getBundle(").append(NLSRefactoring.BUNDLE_NAME_FIELD).append(");").append(lineDelim); //$NON-NLS-1$ //$NON-NLS-2$

			if (constructorIdx < methodIdx) {
				result.append(constructor);
				result.append(method);
			} else {
				result.append(constructor);
				result.append(method);
			}

			result.append(lineDelim).append('}').append(lineDelim);

			return result.toString();
		}
	}

	private String getResourceBundleConstantName() {
		return "RESOURCE_BUNDLE";//$NON-NLS-1$
	}

	private String createStaticFields() {
		HashSet<String> added= new HashSet<String>();
		List<NLSSubstitution> subs= new ArrayList<NLSSubstitution>();
		for (int i= 0; i < fNLSSubstitutions.length; i++) {
			NLSSubstitution substitution= fNLSSubstitutions[i];
			int newState= substitution.getState();
			if ((substitution.hasStateChanged() || substitution.isAccessorRename())&& newState == NLSSubstitution.EXTERNALIZED) {
				if (added.add(substitution.getKey()))
					subs.add(substitution);
			}
		}
		Collections.sort(subs, new Comparator<NLSSubstitution>() {
			private Collator fCollator= Collator.getInstance();
			public int compare(NLSSubstitution s0, NLSSubstitution s1) {
				return fCollator.compare(s0.getKey(), s1.getKey());
			}
		});
		StringBuffer buf= new StringBuffer();
		for (Iterator<NLSSubstitution> iter= subs.iterator(); iter.hasNext();) {
			NLSSubstitution element= iter.next();
			appendStaticField(buf, element);
		}
		return buf.toString();
	}

	private void appendStaticField(StringBuffer buf, NLSSubstitution substitution) {
		buf.append("public static String "); //$NON-NLS-1$
		buf.append(substitution.getKey());
		buf.append(';');
	}

	private String createGetStringMethod(String lineDelim) {
		StringBuffer result= new StringBuffer();

		result.append("public static String "); //$NON-NLS-1$
		int i= fSubstitutionPattern.indexOf(NLSRefactoring.KEY);
		if (i != -1) {
			result.append(fSubstitutionPattern.substring(0, i));
			result.append("String key"); //$NON-NLS-1$
			result.append(fSubstitutionPattern.substring(i + NLSRefactoring.KEY.length()));
		} else {
			//fallback
			result.append("getString(String key)"); //$NON-NLS-1$
		}
		result.append('{').append(lineDelim);

		result.append("try {").append(lineDelim) //$NON-NLS-1$
				.append("return ") //$NON-NLS-1$
				.append(getResourceBundleConstantName()).append(".getString(key);").append(lineDelim) //$NON-NLS-1$
				.append("} catch (MissingResourceException e) {").append(lineDelim) //$NON-NLS-1$
				.append("return '!' + key + '!';").append(lineDelim) //$NON-NLS-1$
				.append("}"); //$NON-NLS-1$

		result.append(lineDelim).append('}');
		return result.toString();
	}

	private String createStaticInitializer(String lineDelim) {
		return "static {" //$NON-NLS-1$
		+ lineDelim
		+ "// initialize resource bundle" //$NON-NLS-1$
		+ lineDelim
		+ "NLS.initializeMessages(BUNDLE_NAME, " + fAccessorClassName + ".class);" //$NON-NLS-1$ //$NON-NLS-2$
		+ lineDelim
		+ "}"; //$NON-NLS-1$
	}

	private String createConstructor(String lineDelim) {
		return "private " + fAccessorClassName + "(){" + //$NON-NLS-2$//$NON-NLS-1$
				lineDelim + '}';
	}

	/* Currently not used.
	 private String createGetStringMethodComment() throws CoreException {
	 if (fCodeGenerationSettings.createComments) {
	 String comment= CodeGeneration.getMethodComment(fCu, fAccessorClassName, "getString", //$NON-NLS-1$
	 new String[]{"key"}, //$NON-NLS-1$
	 new String[0], "QString;", //$NON-NLS-1$
	 null, lineDelim);
	 if (comment == null) {
	 return "";//$NON-NLS-1$
	 }

	 return comment + lineDelim;
	 } else {
	 return "";//$NON-NLS-1$
	 }
	 }
	 */

	private String getPropertyFileName() {
		return fResourceBundlePath.lastSegment();
	}

	private String getPropertyFileNameWithoutExtension() {
		String fileName= getPropertyFileName();
		return fileName.substring(0, fileName.indexOf(NLSRefactoring.PROPERTY_FILE_EXT));
	}

	private String getResourceBundleName() throws CoreException {
		IResource res= ResourcesPlugin.getWorkspace().getRoot().findMember(fResourceBundlePath.removeLastSegments(1));
		if (res != null && res.exists()) {
			IJavaElement el= JavaCore.create(res);
			if (el instanceof IPackageFragment) {
				IPackageFragment p= (IPackageFragment) el;
				return p.getElementName() + '.' + getPropertyFileNameWithoutExtension();
			} else
				if ((el instanceof IPackageFragmentRoot) || (el instanceof IJavaProject)) {
					return getPropertyFileNameWithoutExtension();
				}
		}
		throw new CoreException(new StatusInfo(IStatus.ERROR, "Resourcebundle not specified")); //$NON-NLS-1$
	}
}

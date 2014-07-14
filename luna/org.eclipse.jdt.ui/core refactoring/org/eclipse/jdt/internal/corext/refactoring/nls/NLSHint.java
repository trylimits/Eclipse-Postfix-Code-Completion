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
import java.util.List;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.osgi.util.NLS;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;

import org.eclipse.jdt.ui.SharedASTProvider;


/**
 * calculates hints for the nls-refactoring out of a compilation unit.
 * - package fragments of the accessor class and the resource bundle
 * - accessor class name, resource bundle name
 */
public class NLSHint {

	private String fAccessorName;
	private IPackageFragment fAccessorPackage;
	private String fResourceBundleName;
	private IPackageFragment fResourceBundlePackage;
	private NLSSubstitution[] fSubstitutions;

	public NLSHint(ICompilationUnit cu, CompilationUnit astRoot) {
		Assert.isNotNull(cu);
		Assert.isNotNull(astRoot);

		IPackageFragment cuPackage= (IPackageFragment) cu.getAncestor(IJavaElement.PACKAGE_FRAGMENT);

		fAccessorName= NLSRefactoring.DEFAULT_ACCESSOR_CLASSNAME;
		fAccessorPackage= cuPackage;
		fResourceBundleName= NLSRefactoring.DEFAULT_PROPERTY_FILENAME + NLSRefactoring.PROPERTY_FILE_EXT;
		fResourceBundlePackage= cuPackage;

		IJavaProject project= cu.getJavaProject();
		NLSLine[] lines= createRawLines(cu);

		AccessorClassReference accessClassRef= findFirstAccessorReference(lines, astRoot);

		if (accessClassRef == null) {
			// Look for Eclipse NLS approach
			List<NLSLine> eclipseNLSLines= new ArrayList<NLSLine>();
			accessClassRef= createEclipseNLSLines(getDocument(cu), astRoot, eclipseNLSLines);
			if (!eclipseNLSLines.isEmpty()) {
				NLSLine[] rawLines= lines;
				int rawLinesLength= rawLines.length;
				int eclipseLinesLength= eclipseNLSLines.size();
				lines= new NLSLine[rawLinesLength + eclipseLinesLength];
				for (int i= 0; i < rawLinesLength; i++)
					lines[i]= rawLines[i];
				for (int i= 0; i < eclipseLinesLength; i++)
					lines[i+rawLinesLength]= eclipseNLSLines.get(i);
			}
		}

		Properties props= null;
		if (accessClassRef != null)
			props= NLSHintHelper.getProperties(project, accessClassRef);

		if (props == null)
			props= new Properties();

		fSubstitutions= createSubstitutions(lines, props, astRoot);

		if (accessClassRef != null) {
			fAccessorName= accessClassRef.getName();
			ITypeBinding accessorClassBinding= accessClassRef.getBinding();

			try {
				IPackageFragment accessorPack= NLSHintHelper.getPackageOfAccessorClass(project, accessorClassBinding);
				if (accessorPack != null) {
					fAccessorPackage= accessorPack;
				}

				String fullBundleName= accessClassRef.getResourceBundleName();
				if (fullBundleName != null) {
					fResourceBundleName= Signature.getSimpleName(fullBundleName) + NLSRefactoring.PROPERTY_FILE_EXT;
					String packName= Signature.getQualifier(fullBundleName);

					IPackageFragment pack= NLSHintHelper.getResourceBundlePackage(project, packName, fResourceBundleName);
					if (pack != null) {
						fResourceBundlePackage= pack;
					}
				}
			} catch (JavaModelException e) {
			}
		}
	}

	private AccessorClassReference createEclipseNLSLines(final IDocument document, CompilationUnit astRoot, List<NLSLine> nlsLines) {

		final AccessorClassReference[] firstAccessor= new AccessorClassReference[1];
		final SortedMap<Integer, NLSLine> lineToNLSLine= new TreeMap<Integer, NLSLine>();

		astRoot.accept(new ASTVisitor() {

			private ICompilationUnit fCache_CU;
			private CompilationUnit fCache_AST;

			@Override
			public boolean visit(QualifiedName node) {
				ITypeBinding type= node.getQualifier().resolveTypeBinding();
				if (type != null) {
					ITypeBinding superType= type.getSuperclass();
					if (superType != null && NLS.class.getName().equals(superType.getQualifiedName())) {
						Integer line;
						try {
							line = new Integer(document.getLineOfOffset(node.getStartPosition()));
						} catch (BadLocationException e) {
							return true; // ignore and continue
						}
						NLSLine nlsLine= lineToNLSLine.get(line);
						if (nlsLine == null) {
							nlsLine=  new NLSLine(line.intValue());
							lineToNLSLine.put(line, nlsLine);
						}
						SimpleName name= node.getName();
						NLSElement element= new NLSElement(node.getName().getIdentifier(), name.getStartPosition(),
				                name.getLength(), nlsLine.size() - 1, true);
						nlsLine.add(element);
						String bundleName;
						ICompilationUnit bundleCU= (ICompilationUnit)type.getJavaElement().getAncestor(IJavaElement.COMPILATION_UNIT);
						if (fCache_CU == null || !fCache_CU.equals(bundleCU) || fCache_AST == null) {
							fCache_CU= bundleCU;
							if (fCache_CU != null)
								fCache_AST= SharedASTProvider.getAST(fCache_CU, SharedASTProvider.WAIT_YES, null);
							else
								fCache_AST= null;
						}
						bundleName= NLSHintHelper.getResourceBundleName(fCache_AST);
						element.setAccessorClassReference(new AccessorClassReference(type, bundleName, new Region(node.getStartPosition(), node.getLength())));

						if (firstAccessor[0] == null)
							firstAccessor[0]= element.getAccessorClassReference();

					}
				}
				return true;
			}
		});

		nlsLines.addAll(lineToNLSLine.values());
		return firstAccessor[0];
	}

	private IDocument getDocument(ICompilationUnit cu) {
		IPath path= cu.getPath();
		ITextFileBufferManager manager= FileBuffers.getTextFileBufferManager();
		try {
			manager.connect(path, LocationKind.NORMALIZE, null);
		} catch (CoreException e) {
			return null;
		}

		try {
			ITextFileBuffer buffer= manager.getTextFileBuffer(path, LocationKind.NORMALIZE);
			if (buffer != null)
				return buffer.getDocument();
		} finally {
			try {
				manager.disconnect(path, LocationKind.NORMALIZE, null);
			} catch (CoreException e) {
				return null;
			}
		}
		return null;
	}

	private NLSSubstitution[] createSubstitutions(NLSLine[] lines, Properties props, CompilationUnit astRoot) {
		List<NLSSubstitution> result= new ArrayList<NLSSubstitution>();

		for (int i= 0; i < lines.length; i++) {
			NLSElement[] elements= lines[i].getElements();
			for (int j= 0; j < elements.length; j++) {
				NLSElement nlsElement= elements[j];
				if (nlsElement.hasTag()) {
					AccessorClassReference accessorClassReference= NLSHintHelper.getAccessorClassReference(astRoot, nlsElement);
					if (accessorClassReference == null) {
						// no accessor class => not translated
						result.add(new NLSSubstitution(NLSSubstitution.IGNORED, stripQuotes(nlsElement.getValue()), nlsElement));
					} else {
						String key= stripQuotes(nlsElement.getValue());
						String value= props.getProperty(key);
						result.add(new NLSSubstitution(NLSSubstitution.EXTERNALIZED, key, value, nlsElement, accessorClassReference));
					}
				} else if (nlsElement.isEclipseNLS()) {
					String key= nlsElement.getValue();
					result.add(new NLSSubstitution(NLSSubstitution.EXTERNALIZED, key, props.getProperty(key), nlsElement, nlsElement.getAccessorClassReference()));
				} else {
					result.add(new NLSSubstitution(NLSSubstitution.INTERNALIZED, stripQuotes(nlsElement.getValue()), nlsElement));
				}
			}
		}
		return result.toArray(new NLSSubstitution[result.size()]);
	}

	private static AccessorClassReference findFirstAccessorReference(NLSLine[] lines, CompilationUnit astRoot) {
		for (int i= 0; i < lines.length; i++) {
			NLSElement[] elements= lines[i].getElements();
			for (int j= 0; j < elements.length; j++) {
				NLSElement nlsElement= elements[j];
				if (nlsElement.hasTag()) {
					AccessorClassReference accessorClassReference= NLSHintHelper.getAccessorClassReference(astRoot, nlsElement);
					if (accessorClassReference != null) {
						return accessorClassReference;
					}
				}
			}
		}

		// try to find a access with missing //non-nls tag (bug 75155)
		for (int i= 0; i < lines.length; i++) {
			NLSElement[] elements= lines[i].getElements();
			for (int j= 0; j < elements.length; j++) {
				NLSElement nlsElement= elements[j];
				if (!nlsElement.hasTag()) {
					AccessorClassReference accessorClassReference= NLSHintHelper.getAccessorClassReference(astRoot, nlsElement);
					if (accessorClassReference != null) {
						return accessorClassReference;
					}
				}
			}
		}
		return null;
	}

	private static String stripQuotes(String str) {
		return str.substring(1, str.length() - 1);
	}

	private static NLSLine[] createRawLines(ICompilationUnit cu) {
		try {
			return NLSScanner.scan(cu);
		} catch (JavaModelException x) {
			return new NLSLine[0];
		} catch (InvalidInputException x) {
			return new NLSLine[0];
		} catch (BadLocationException x) {
			return new NLSLine[0];
		}
	}


	public String getAccessorClassName() {
		return fAccessorName;
	}

//	public boolean isEclipseNLS() {
//		return fIsEclipseNLS;
//	}

	public IPackageFragment getAccessorClassPackage() {
		return fAccessorPackage;
	}

	public String getResourceBundleName() {
		return fResourceBundleName;
	}

	public IPackageFragment getResourceBundlePackage() {
		return fResourceBundlePackage;
	}

	public NLSSubstitution[] getSubstitutions() {
		return fSubstitutions;
	}


}

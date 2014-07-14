/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.actions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.IWorkingSet;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.Modifier;

import org.eclipse.jdt.internal.corext.refactoring.nls.NLSHintHelper;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSRefactoring;

import org.eclipse.jdt.ui.IWorkingCopyManager;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.browsing.LogicalPackage;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.refactoring.nls.search.SearchBrokenNLSKeysUtil;
import org.eclipse.jdt.internal.ui.workingsets.IWorkingSetIDs;

public class FindBrokenNLSKeysAction extends SelectionDispatchAction {

	private static class SearchPatternData {

		private final IType fAccessorType;
		private final IFile fPropertyFile;

		public SearchPatternData(IType accessorType, IFile propertyFile) {
			fAccessorType= accessorType;
			fPropertyFile= propertyFile;
		}

		public IFile getPropertyFile() {
			return fPropertyFile;
		}

		public IType getWrapperClass() {
			return fAccessorType;
		}

	}

	//TODO: Add to API: IJavaEditorActionDefinitionIds
	public static final String FIND_BROKEN_NLS_KEYS_ACTION_ID= "org.eclipse.jdt.ui.edit.text.java.find.broken.nls.keys"; //$NON-NLS-1$

	//TODO: Add to API: JdtActionConstants
	public static final String ACTION_HANDLER_ID= "org.eclipse.jdt.ui.actions.FindNLSProblems"; //$NON-NLS-1$

	private static final String JAVA_LANG_STRING= "QString;"; //$NON-NLS-1$

	private JavaEditor fEditor;

	public FindBrokenNLSKeysAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.FindNLSProblemsAction_Name);
		setToolTipText(ActionMessages.FindNLSProblemsAction_ToolTip);
		setDescription(ActionMessages.FindNLSProblemsAction_Description);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 */
	public FindBrokenNLSKeysAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(getCompilationUnit(editor) != null);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(ITextSelection selection) {
		ISelectionProvider selectionProvider= fEditor.getSelectionProvider();
		if (selectionProvider == null)
			return;

		run(new StructuredSelection(selectionProvider.getSelection()));
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(IStructuredSelection selection) {
		if (selection.size() == 1) {
			Object firstElement= selection.getFirstElement();
			if (firstElement instanceof IJavaElement) {
				IJavaElement javaElement= (IJavaElement) firstElement;
				if (!ActionUtil.isProcessable(getShell(), javaElement)) {
					return;
				}
			}
		}

		SearchPatternData[] data= getNLSFiles(selection);
		if (data == null || data.length == 0) {
			MessageDialog.openInformation(getShell(), ActionMessages.FindNLSProblemsAction_ErrorDialogTitle, ActionMessages.FindNLSProblemsAction_NoPropertieFilesFoundErrorDescription);
			return;
		}

		String scope= "workspace"; //$NON-NLS-1$
		if (selection.size() == 1) {
			Object firstElement= selection.getFirstElement();
			if (firstElement instanceof IJavaElement) {
				scope= ((IJavaElement)firstElement).getElementName();
			} else if (firstElement instanceof IFile) {
				scope= ((IFile)firstElement).getName();
			} else if (firstElement instanceof IFolder) {
				scope= ((IFolder)firstElement).getName();
			}
		}
		run(data, scope);
	}

	private void run(SearchPatternData[] data, String scope) {
		List<IType> wrappers= new ArrayList<IType>();
		List<IFile> properties= new ArrayList<IFile>();
		for (int i= 0; i < data.length; i++) {
			SearchPatternData current= data[i];
			if (current.getWrapperClass() != null || current.getPropertyFile() != null) {
				wrappers.add(current.getWrapperClass());
				properties.add(current.getPropertyFile());
			}
		}
		IType[] accessorClasses= wrappers.toArray(new IType[wrappers.size()]);
		IFile[] propertieFiles= properties.toArray(new IFile[properties.size()]);
		SearchBrokenNLSKeysUtil.search(scope, accessorClasses, propertieFiles);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		ISelectionProvider selectionProvider= fEditor.getSelectionProvider();
		if (selectionProvider == null) {
			setEnabled(false);
		} else {
			selectionChanged(new StructuredSelection(selectionProvider.getSelection()));
		}
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		setEnabled(canEnable(selection));
	}

	private SearchPatternData[] getNLSFiles(IStructuredSelection selection) {
		Object[] selectedElements= selection.toArray();
		HashMap<IType, SearchPatternData> result= new HashMap<IType, SearchPatternData>();

		collectNLSFilesFromResources(selectedElements, result);
		collectNLSFilesFromJavaElements(selectedElements, result);

		Collection<SearchPatternData> values= result.values();
		return values.toArray(new SearchPatternData[values.size()]);
	}

	private boolean canEnable(IStructuredSelection selection) {
		Object[] selected= selection.toArray();
		for (int i= 0; i < selected.length; i++) {
			try {
				if (selected[i] instanceof IJavaElement) {
					IJavaElement elem= (IJavaElement) selected[i];
					if (elem.exists()) {
						switch (elem.getElementType()) {
							case IJavaElement.TYPE:
								if (elem.getParent().getElementType() == IJavaElement.COMPILATION_UNIT) {
									return true;
								}
								return false;
							case IJavaElement.COMPILATION_UNIT:
								return true;
							case IJavaElement.IMPORT_CONTAINER:
								return false;
							case IJavaElement.PACKAGE_FRAGMENT:
							case IJavaElement.PACKAGE_FRAGMENT_ROOT:
								IPackageFragmentRoot root= (IPackageFragmentRoot) elem.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
								return (root.getKind() == IPackageFragmentRoot.K_SOURCE);
							case IJavaElement.JAVA_PROJECT:
								return true;
						}
					}
				} else if (selected[i] instanceof LogicalPackage) {
					return true;
				} else if (selected[i] instanceof IFile) {
					IFile file= (IFile)selected[i];
					if ("properties".equalsIgnoreCase(file.getFileExtension())) //$NON-NLS-1$
						return true;
				} else if (selected[i] instanceof IWorkingSet) {
					IWorkingSet workingSet= (IWorkingSet) selected[i];
					return IWorkingSetIDs.JAVA.equals(workingSet.getId());
				}
			} catch (JavaModelException e) {
				if (!e.isDoesNotExist()) {
					JavaPlugin.log(e);
				}
			}
		}
		return false;
	}

	private void collectNLSFilesFromResources(Object[] objects, HashMap<IType, SearchPatternData> result) {
		try {
			for (int i= 0; i < objects.length; i++) {
				Object object= objects[i];

				IResource resource= null;
				if (object instanceof IWorkingSet) {
					IWorkingSet workingSet= (IWorkingSet) object;
					collectNLSFilesFromResources(workingSet.getElements(), result);
				} else if (object instanceof IJavaElement) {
					resource= ((IJavaElement) object).getCorrespondingResource();
				} else if (object instanceof IResource) {
					resource= (IResource) object;
				} else if (object instanceof LogicalPackage) {
					LogicalPackage logicalPackage= (LogicalPackage)object;
					resource= logicalPackage.getJavaProject().getProject();
				}

				if (resource instanceof IContainer) {
					collectNLSFilesFromResources(((IContainer)resource).members(), result);
				} else if (resource instanceof IFile) {
					SearchPatternData data= tryIfPropertyFileSelected((IFile) resource);
					if (data != null && !result.containsKey(data.fAccessorType)) {
						result.put(data.fAccessorType, data);
					}
				}
			}
		} catch (JavaModelException e) {
			if (!e.isDoesNotExist()) {
				JavaPlugin.log(e);
			}
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
	}

	private void collectNLSFilesFromJavaElements(Object[] objects, HashMap<IType, SearchPatternData> result) {
		try {
			for (int i= 0; i < objects.length; i++) {
				if (objects[i] instanceof IJavaElement) {
					IJavaElement elem= (IJavaElement) objects[i];
					if (elem.exists()) {
						switch (elem.getElementType()) {
							case IJavaElement.TYPE:
								if (elem.getParent().getElementType() == IJavaElement.COMPILATION_UNIT) {
									ICompilationUnit unit= (ICompilationUnit)elem.getParent();
									IType[] types= unit.getTypes();
									if (types.length > 0 && !result.containsKey(types[0])) {
										SearchPatternData data= tryIfPropertyCuSelected(unit);
										if (data != null)
											result.put(data.fAccessorType, data);
									}
								}
								break;
							case IJavaElement.COMPILATION_UNIT:
								ICompilationUnit unit= (ICompilationUnit)elem;
								IType[] types= unit.getTypes();
								if (types.length > 0 && !result.containsKey(types[0])) {
									SearchPatternData data= tryIfPropertyCuSelected(unit);
									if (data != null)
										result.put(data.fAccessorType, data);
								}
								break;
							case IJavaElement.PACKAGE_FRAGMENT:
								IPackageFragment fragment= (IPackageFragment)elem;
								if (fragment.getKind() == IPackageFragmentRoot.K_SOURCE)
									collectNLSFilesFromJavaElements(fragment.getChildren(), result);
								break;
							case IJavaElement.PACKAGE_FRAGMENT_ROOT:
							{
								IPackageFragmentRoot root= (IPackageFragmentRoot) elem;
								if (root.getKind() == IPackageFragmentRoot.K_SOURCE)
									collectNLSFilesFromJavaElements(root.getChildren(), result);
								break;
							}
							case IJavaElement.JAVA_PROJECT:
							{
								IJavaProject javaProject= (IJavaProject)elem;
								IPackageFragmentRoot[] allPackageFragmentRoots= javaProject.getAllPackageFragmentRoots();
								for (int j= 0; j < allPackageFragmentRoots.length; j++) {
									IPackageFragmentRoot root= allPackageFragmentRoots[j];
									if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
										if (javaProject.equals(root.getJavaProject())) {
											collectNLSFilesFromJavaElements(new Object[] {root}, result);
										}
									}
								}
								break;
							}
						}
					}
				} else if (objects[i] instanceof LogicalPackage) {
					LogicalPackage logicalPackage= (LogicalPackage)objects[i];
					collectNLSFilesFromJavaElements(new Object[] {logicalPackage.getJavaProject()}, result);
				} else if (objects[i] instanceof IWorkingSet) {
					IWorkingSet workingSet= (IWorkingSet) objects[i];
					collectNLSFilesFromJavaElements(workingSet.getElements(), result);
				}
			}
		} catch (JavaModelException e) {
			if (!e.isDoesNotExist()) {
				JavaPlugin.log(e);
			}
		}
	}

	private SearchPatternData tryIfPropertyCuSelected(ICompilationUnit compilationUnit) throws JavaModelException {
		IStorage bundle= getResourceBundle(compilationUnit);
		if (!(bundle instanceof IFile))
			return null;

		return new SearchPatternData(compilationUnit.getTypes()[0], (IFile) bundle);
	}

	public static IStorage getResourceBundle(ICompilationUnit compilationUnit) throws JavaModelException {
		if (compilationUnit == null)
			return null;

		if (!ActionUtil.isOnBuildPath(compilationUnit))
			return null;

		IType[] types= compilationUnit.getTypes();
		if (types.length != 1)
			return null;

		if (!isPotentialNLSAccessor(compilationUnit))
			return null;

		return NLSHintHelper.getResourceBundle(compilationUnit);
	}

	/*
	 * Be conservative, for every unit this returns true an AST will to be created!
	 */
	private static boolean isPotentialNLSAccessor(ICompilationUnit unit) throws JavaModelException {
		IType type= unit.getTypes()[0];
		if (!type.exists())
			return false;

		IField bundleNameField= getBundleNameField(type.getFields());
		if (bundleNameField == null)
			return false;

		if (importsOSGIUtil(unit)) { //new school
			IInitializer[] initializers= type.getInitializers();
			for (int i= 0; i < initializers.length; i++) {
				if (Modifier.isStatic(initializers[0].getFlags()))
					return true;
			}
		} else { //old school
			IMethod[] methods= type.getMethods();
			for (int i= 0; i < methods.length; i++) {
				IMethod method= methods[i];
				if (isValueAccessor(method))
					return true;
			}
		}

		return false;
	}

	private static boolean importsOSGIUtil(ICompilationUnit unit) throws JavaModelException {
		IImportDeclaration[] imports= unit.getImports();
		for (int i= 0; i < imports.length; i++) {
			if (imports[i].getElementName().startsWith("org.eclipse.osgi.util.")) //$NON-NLS-1$
				return true;
		}

		return false;
	}

	private static boolean isValueAccessor(IMethod method) throws JavaModelException {
		if (!"getString".equals(method.getElementName())) //$NON-NLS-1$
			return false;

		int flags= method.getFlags();
		if (!Modifier.isStatic(flags) || !Modifier.isPublic(flags))
			return false;

		String returnType= method.getReturnType();
		if (!JAVA_LANG_STRING.equals(returnType))
			return false;

		String[] parameters= method.getParameterTypes();
		if (parameters.length != 1 || !JAVA_LANG_STRING.equals(parameters[0]))
			return false;

		return true;
	}

	private static IField getBundleNameField(IField[] fields) {
		for (int i= 0; i < fields.length; i++) {
			if ("BUNDLE_NAME".equals(fields[i].getElementName())) //$NON-NLS-1$
				return fields[i];
		}

		return null;
	}

	private SearchPatternData tryIfPropertyFileSelected(IFile file) {
		IType accessorType= getAccessorType(file);
		return (accessorType != null) ? new SearchPatternData(accessorType, file) : null;
	}

	public static IType getAccessorType(IFile file) {
		if (!"properties".equalsIgnoreCase(file.getFileExtension())) //$NON-NLS-1$
			return null;

		IPath propertyFullPath= file.getFullPath();
		// Try to find a corresponding CU
		String[] javaExtensions= JavaCore.getJavaLikeExtensions();
		for (int i= 0; i < javaExtensions.length; i++) {
			String extension= javaExtensions[i];
			IPath cuPath= propertyFullPath.removeFileExtension().addFileExtension(extension);
			IFile cuFile= (IFile)JavaPlugin.getWorkspace().getRoot().findMember(cuPath);

			if (cuFile == null) { //try with uppercase first char
				String filename= cuPath.removeFileExtension().lastSegment();
				if (filename != null && filename.length() > 0) {
					filename= Character.toUpperCase(filename.charAt(0)) + filename.substring(1);
					IPath dirPath= propertyFullPath.removeLastSegments(1).addTrailingSeparator();
					cuPath= dirPath.append(filename).addFileExtension(extension);
					cuFile= (IFile)JavaPlugin.getWorkspace().getRoot().findMember(cuPath);
				}
			}

			if (cuFile != null && cuFile.exists()) {
				IJavaElement  element= JavaCore.create(cuFile);
				if (element != null && element.exists() && element.getElementType() == IJavaElement.COMPILATION_UNIT && ActionUtil.isOnBuildPath(element)) {
					ICompilationUnit compilationUnit= (ICompilationUnit)element;
					IType type= compilationUnit.findPrimaryType();
					if (type != null) {
						String resourceBundleName= NLSHintHelper.getResourceBundleName(compilationUnit);
						if (resourceBundleName != null) {
							String resourceName= resourceBundleName + NLSRefactoring.PROPERTY_FILE_EXT;
							String name= file.getName();
							if (resourceName.endsWith(name)) {
								return type;
							}
						}
					}
				}
			}
		}

		return null;
	}

	private static ICompilationUnit getCompilationUnit(JavaEditor editor) {
		IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();
		ICompilationUnit cu= manager.getWorkingCopy(editor.getEditorInput());
		return cu;
	}

}

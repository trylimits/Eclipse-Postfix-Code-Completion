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
package org.eclipse.jdt.ui;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;

import org.eclipse.jface.viewers.ContentViewer;
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;

import org.eclipse.ui.model.IWorkbenchAdapter;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.packageview.PackageFragmentRootContainer;
import org.eclipse.jdt.internal.ui.preferences.MembersOrderPreferenceCache;


/**
 * Viewer comparator for Java elements. Ordered by element category, then by element name.
 * Package fragment roots are sorted as ordered on the classpath.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 3.3
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class JavaElementComparator extends ViewerComparator {

	private static final int PROJECTS= 1;
	private static final int PACKAGEFRAGMENTROOTS= 2;
	private static final int PACKAGEFRAGMENT= 3;

	private static final int COMPILATIONUNITS= 4;
	private static final int CLASSFILES= 5;

	private static final int RESOURCEFOLDERS= 7;
	private static final int RESOURCES= 8;

	private static final int PACKAGE_DECL=	10;
	private static final int IMPORT_CONTAINER= 11;
	private static final int IMPORT_DECLARATION= 12;

	// Includes all categories ordered using the OutlineSortOrderPage:
	// types, initializers, methods & fields
	private static final int MEMBERSOFFSET= 15;

	private static final int JAVAELEMENTS= 50;
	private static final int OTHERS= 51;

	private MembersOrderPreferenceCache fMemberOrderCache;

	/**
	 * Constructor.
	 */
	public JavaElementComparator() {
		super(null); // delay initialization of collator
		fMemberOrderCache= JavaPlugin.getDefault().getMemberOrderPreferenceCache();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ViewerComparator#category(java.lang.Object)
	 */
	@Override
	public int category(Object element) {
		if (element instanceof IJavaElement) {
			try {
				IJavaElement je= (IJavaElement) element;

				switch (je.getElementType()) {
					case IJavaElement.METHOD:
						{
							IMethod method= (IMethod) je;
							if (method.isConstructor()) {
								return getMemberCategory(MembersOrderPreferenceCache.CONSTRUCTORS_INDEX);
							}
							int flags= method.getFlags();
							if (Flags.isStatic(flags))
								return getMemberCategory(MembersOrderPreferenceCache.STATIC_METHODS_INDEX);
							else
								return getMemberCategory(MembersOrderPreferenceCache.METHOD_INDEX);
						}
					case IJavaElement.FIELD :
						{
							int flags= ((IField) je).getFlags();
							if (Flags.isEnum(flags)) {
								return getMemberCategory(MembersOrderPreferenceCache.ENUM_CONSTANTS_INDEX);
							}
							if (Flags.isStatic(flags))
								return getMemberCategory(MembersOrderPreferenceCache.STATIC_FIELDS_INDEX);
							else
								return getMemberCategory(MembersOrderPreferenceCache.FIELDS_INDEX);
						}
					case IJavaElement.INITIALIZER :
						{
							int flags= ((IInitializer) je).getFlags();
							if (Flags.isStatic(flags))
								return getMemberCategory(MembersOrderPreferenceCache.STATIC_INIT_INDEX);
							else
								return getMemberCategory(MembersOrderPreferenceCache.INIT_INDEX);
						}
					case IJavaElement.TYPE :
						return getMemberCategory(MembersOrderPreferenceCache.TYPE_INDEX);
					case IJavaElement.PACKAGE_DECLARATION :
						return PACKAGE_DECL;
					case IJavaElement.IMPORT_CONTAINER :
						return IMPORT_CONTAINER;
					case IJavaElement.IMPORT_DECLARATION :
						return IMPORT_DECLARATION;
					case IJavaElement.PACKAGE_FRAGMENT :
						return PACKAGEFRAGMENT;
					case IJavaElement.PACKAGE_FRAGMENT_ROOT :
						return PACKAGEFRAGMENTROOTS;
					case IJavaElement.JAVA_PROJECT :
						return PROJECTS;
					case IJavaElement.CLASS_FILE :
						return CLASSFILES;
					case IJavaElement.COMPILATION_UNIT :
						return COMPILATIONUNITS;
				}

			} catch (JavaModelException e) {
				if (!e.isDoesNotExist())
					JavaPlugin.log(e);
			}
			return JAVAELEMENTS;
		} else if (element instanceof IFile) {
			return RESOURCES;
		} else if (element instanceof IProject) {
			return PROJECTS;
		} else if (element instanceof IContainer) {
			return RESOURCEFOLDERS;
		} else if (element instanceof IJarEntryResource) {
			if (((IJarEntryResource) element).isFile()) {
				return RESOURCES;
			}
			return RESOURCEFOLDERS;
		} else if (element instanceof PackageFragmentRootContainer) {
			return PACKAGEFRAGMENTROOTS;
		}
		return OTHERS;
	}

	private int getMemberCategory(int kind) {
		int offset= fMemberOrderCache.getCategoryIndex(kind);
		return offset + MEMBERSOFFSET;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ViewerComparator#compare(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
	 */
	@Override
	public int compare(Viewer viewer, Object e1, Object e2) {
		int cat1= category(e1);
		int cat2= category(e2);

		if (needsClasspathComparision(e1, cat1, e2, cat2)) {
			IPackageFragmentRoot root1= getPackageFragmentRoot(e1);
			IPackageFragmentRoot root2= getPackageFragmentRoot(e2);
			if (root1 == null) {
				if (root2 == null) {
					return 0;
				} else {
					return 1;
				}
			} else if (root2 == null) {
				return -1;
			}
			// check if not same to avoid expensive class path access
			if (!root1.getPath().equals(root2.getPath())) {
				int p1= getClassPathIndex(root1);
				int p2= getClassPathIndex(root2);
				if (p1 != p2) {
					return p1 - p2;
				}
			}
		}

		if (cat1 != cat2)
			return cat1 - cat2;

		if (cat1 == PROJECTS || cat1 == RESOURCES || cat1 == RESOURCEFOLDERS || cat1 == OTHERS) {
			String name1= getNonJavaElementLabel(viewer, e1);
			String name2= getNonJavaElementLabel(viewer, e2);
			if (name1 != null && name2 != null) {
				return getComparator().compare(name1, name2);
			}
			return 0; // can't compare
		}
		// only java elements from this point

		if (e1 instanceof IMember) {
			if (fMemberOrderCache.isSortByVisibility()) {
				try {
					int flags1= JdtFlags.getVisibilityCode((IMember) e1);
					int flags2= JdtFlags.getVisibilityCode((IMember) e2);
					int vis= fMemberOrderCache.getVisibilityIndex(flags1) - fMemberOrderCache.getVisibilityIndex(flags2);
					if (vis != 0) {
						return vis;
					}
				} catch (JavaModelException ignore) {
				}
			}
		}

		String name1= getElementName(e1);
		String name2= getElementName(e2);

		if (e1 instanceof IType) { // handle anonymous types
			if (name1.length() == 0) {
				if (name2.length() == 0) {
					try {
						return getComparator().compare(((IType) e1).getSuperclassName(), ((IType) e2).getSuperclassName());
					} catch (JavaModelException e) {
						return 0;
					}
				} else {
					return 1;
				}
			} else if (name2.length() == 0) {
				return -1;
			}
		}

		int cmp= getComparator().compare(name1, name2);
		if (cmp != 0) {
			return cmp;
		}

		if (e1 instanceof IMethod) {
			String[] params1= ((IMethod) e1).getParameterTypes();
			String[] params2= ((IMethod) e2).getParameterTypes();
			int len= Math.min(params1.length, params2.length);
			for (int i = 0; i < len; i++) {
				cmp= getComparator().compare(Signature.toString(params1[i]), Signature.toString(params2[i]));
				if (cmp != 0) {
					return cmp;
				}
			}
			return params1.length - params2.length;
		}
		return 0;
	}

	private IPackageFragmentRoot getPackageFragmentRoot(Object element) {
		if (element instanceof PackageFragmentRootContainer) {
			// return first package fragment root from the container
			PackageFragmentRootContainer cp= (PackageFragmentRootContainer)element;
			Object[] roots= cp.getPackageFragmentRoots();
			if (roots.length > 0)
				return (IPackageFragmentRoot)roots[0];
			// non resolvable - return null
			return null;
		}
		return JavaModelUtil.getPackageFragmentRoot((IJavaElement)element);
	}

	private String getNonJavaElementLabel(Viewer viewer, Object element) {
		// try to use the workbench adapter for non - java resources or if not available, use the viewers label provider
		if (element instanceof IResource) {
			return ((IResource) element).getName();
		}
		if (element instanceof IStorage) {
			return ((IStorage) element).getName();
		}
		if (element instanceof IAdaptable) {
			IWorkbenchAdapter adapter= (IWorkbenchAdapter) ((IAdaptable) element).getAdapter(IWorkbenchAdapter.class);
			if (adapter != null) {
				return adapter.getLabel(element);
			}
		}
		if (viewer instanceof ContentViewer) {
			IBaseLabelProvider prov = ((ContentViewer) viewer).getLabelProvider();
			if (prov instanceof ILabelProvider) {
				return ((ILabelProvider) prov).getText(element);
			}
		}
		return null;
	}

	private int getClassPathIndex(IPackageFragmentRoot root) {
		try {
			IPath rootPath= root.getPath();
			IPackageFragmentRoot[] roots= root.getJavaProject().getPackageFragmentRoots();
			for (int i= 0; i < roots.length; i++) {
				if (roots[i].getPath().equals(rootPath)) {
					return i;
				}
			}
		} catch (JavaModelException e) {
		}

		return Integer.MAX_VALUE;
	}

	private boolean needsClasspathComparision(Object e1, int cat1, Object e2, int cat2) {
		if ((cat1 == PACKAGEFRAGMENTROOTS && cat2 == PACKAGEFRAGMENTROOTS) ||
			(cat1 == PACKAGEFRAGMENT &&
				((IPackageFragment)e1).getParent().getResource() instanceof IProject &&
				cat2 == PACKAGEFRAGMENTROOTS) ||
			(cat1 == PACKAGEFRAGMENTROOTS &&
				cat2 == PACKAGEFRAGMENT &&
				((IPackageFragment)e2).getParent().getResource() instanceof IProject)) {
			IJavaProject p1= getJavaProject(e1);
			return p1 != null && p1.equals(getJavaProject(e2));
		}
		return false;
	}

	private IJavaProject getJavaProject(Object element) {
		if (element instanceof IJavaElement) {
			return ((IJavaElement)element).getJavaProject();
		} else if (element instanceof PackageFragmentRootContainer) {
			return ((PackageFragmentRootContainer)element).getJavaProject();
		}
		return null;
	}

	private String getElementName(Object element) {
		if (element instanceof IJavaElement) {
			return ((IJavaElement)element).getElementName();
		} else if (element instanceof PackageFragmentRootContainer) {
			return ((PackageFragmentRootContainer)element).getLabel();
		} else {
			return element.toString();
		}
	}
}

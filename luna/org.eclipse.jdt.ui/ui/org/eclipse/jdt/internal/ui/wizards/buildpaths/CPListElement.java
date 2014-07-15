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
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.JavaPlugin;

public class CPListElement {

	public static final String SOURCEATTACHMENT= "sourcepath"; //$NON-NLS-1$
	public static final String OUTPUT= "output"; //$NON-NLS-1$
	public static final String EXCLUSION= "exclusion"; //$NON-NLS-1$
	public static final String INCLUSION= "inclusion"; //$NON-NLS-1$

	public static final String ACCESSRULES= "accessrules"; //$NON-NLS-1$
	public static final String COMBINE_ACCESSRULES= "combineaccessrules"; //$NON-NLS-1$

	public static final String JAVADOC= IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME;
	public static final String SOURCE_ATTACHMENT_ENCODING= IClasspathAttribute.SOURCE_ATTACHMENT_ENCODING;
	public static final String IGNORE_OPTIONAL_PROBLEMS= IClasspathAttribute.IGNORE_OPTIONAL_PROBLEMS;
	public static final String NATIVE_LIB_PATH= JavaRuntime.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY;

	private IJavaProject fProject;

	private int fEntryKind;
	private IPath fPath, fOrginalPath;
	private IResource fResource;
	private boolean fIsExported;
	private boolean fIsMissing;

	private Object fParentContainer;

	private IClasspathEntry fCachedEntry;
	/**
	 * List of {@link CPListElement} and {@link CPListElementAttribute}.
	 */
	private ArrayList<Object> fChildren;
	private IPath fLinkTarget, fOrginalLinkTarget;

	private CPListElement() {}

	public CPListElement(IJavaProject project, int entryKind, IPath path, IResource res) {
		this(null, project, entryKind, path, res);
	}

	public CPListElement(Object parent, IJavaProject project, int entryKind, IPath path, IResource res) {
		this(parent, project, entryKind, path, res, null);
	}

	public CPListElement(IJavaProject project, int entryKind) {
		this(null, project, entryKind, null, null);
	}

	public CPListElement(Object parent, IJavaProject project, int entryKind, IPath path, IResource res, IPath linkTarget) {
		this(parent, project, entryKind, path, false, res, linkTarget);
	}
	
	public CPListElement(Object parent, IJavaProject project, int entryKind, IPath path, boolean newElement, IResource res, IPath linkTarget) {
		fProject= project;

		fEntryKind= entryKind;
		fPath= path;
		fOrginalPath= newElement ? null : path;
		fLinkTarget= linkTarget;
		fOrginalLinkTarget= linkTarget;
		fChildren= new ArrayList<Object>();
		fResource= res;
		fIsExported= false;

		fIsMissing= false;
		fCachedEntry= null;
		fParentContainer= parent;

		switch (entryKind) {
			case IClasspathEntry.CPE_SOURCE:
				createAttributeElement(OUTPUT, null, true);
				createAttributeElement(INCLUSION, new Path[0], true);
				createAttributeElement(EXCLUSION, new Path[0], true);
				createAttributeElement(NATIVE_LIB_PATH, null, false);
				createAttributeElement(IGNORE_OPTIONAL_PROBLEMS, null, false);
				break;
			case IClasspathEntry.CPE_LIBRARY:
			case IClasspathEntry.CPE_VARIABLE:
				createAttributeElement(SOURCEATTACHMENT, null, true);
				createAttributeElement(JAVADOC, null, false);
				createAttributeElement(SOURCE_ATTACHMENT_ENCODING, null, false);
				createAttributeElement(NATIVE_LIB_PATH, null, false);
				createAttributeElement(ACCESSRULES, new IAccessRule[0], true);
				break;
			case IClasspathEntry.CPE_PROJECT:
				createAttributeElement(ACCESSRULES, new IAccessRule[0], true);
				createAttributeElement(COMBINE_ACCESSRULES, Boolean.FALSE, true); // not rendered
				createAttributeElement(NATIVE_LIB_PATH, null, false);
				break;
			case IClasspathEntry.CPE_CONTAINER:
				createAttributeElement(ACCESSRULES, new IAccessRule[0], true);
				try {
					IClasspathContainer container= JavaCore.getClasspathContainer(fPath, fProject);
					if (container != null) {
						IClasspathEntry[] entries= container.getClasspathEntries();
						if (entries != null) { // catch invalid container implementation
							for (int i= 0; i < entries.length; i++) {
								IClasspathEntry entry= entries[i];
								if (entry != null) {
									CPListElement curr= createFromExisting(this, entry, fProject);
									fChildren.add(curr);
								} else {
									JavaPlugin.logErrorMessage("Null entry in container '" + fPath + "'");  //$NON-NLS-1$//$NON-NLS-2$
								}
							}
						} else {
							JavaPlugin.logErrorMessage("container returns null as entries: '" + fPath + "'");  //$NON-NLS-1$//$NON-NLS-2$
						}
					}
				} catch (JavaModelException e) {
				}
				createAttributeElement(NATIVE_LIB_PATH, null, false);
				break;
			default:
		}
	}

	public IClasspathEntry getClasspathEntry() {
		if (fCachedEntry == null) {
			fCachedEntry= newClasspathEntry();
		}
		return fCachedEntry;
	}


	private IClasspathAttribute[] getClasspathAttributes() {
		ArrayList<IClasspathAttribute> res= new ArrayList<IClasspathAttribute>();
		for (int i= 0; i < fChildren.size(); i++) {
			Object curr= fChildren.get(i);
			if (curr instanceof CPListElementAttribute) {
				CPListElementAttribute elem= (CPListElementAttribute) curr;
				if (!elem.isBuiltIn() && elem.getValue() != null) {
					res.add(elem.getClasspathAttribute());
				}
			}
		}
		return res.toArray(new IClasspathAttribute[res.size()]);
	}


	private IClasspathEntry newClasspathEntry() {

		IClasspathAttribute[] extraAttributes= getClasspathAttributes();
		switch (fEntryKind) {
			case IClasspathEntry.CPE_SOURCE:
				IPath[] inclusionPattern= (IPath[]) getAttribute(INCLUSION);
				IPath[] exclusionPattern= (IPath[]) getAttribute(EXCLUSION);
				IPath outputLocation= (IPath) getAttribute(OUTPUT);
				return JavaCore.newSourceEntry(fPath, inclusionPattern, exclusionPattern, outputLocation, extraAttributes);
			case IClasspathEntry.CPE_LIBRARY: {
				IPath attach= (IPath) getAttribute(SOURCEATTACHMENT);
				IAccessRule[] accesRules= (IAccessRule[]) getAttribute(ACCESSRULES);
				return JavaCore.newLibraryEntry(fPath, attach, null, accesRules, extraAttributes, isExported());
			}
			case IClasspathEntry.CPE_PROJECT: {
				IAccessRule[] accesRules= (IAccessRule[]) getAttribute(ACCESSRULES);
				boolean combineAccessRules= ((Boolean) getAttribute(COMBINE_ACCESSRULES)).booleanValue();
				return JavaCore.newProjectEntry(fPath, accesRules, combineAccessRules, extraAttributes, isExported());
			}
			case IClasspathEntry.CPE_CONTAINER: {
				IAccessRule[] accesRules= (IAccessRule[]) getAttribute(ACCESSRULES);
				return JavaCore.newContainerEntry(fPath, accesRules, extraAttributes, isExported());
			}
			case IClasspathEntry.CPE_VARIABLE: {
				IPath varAttach= (IPath) getAttribute(SOURCEATTACHMENT);
				IAccessRule[] accesRules= (IAccessRule[]) getAttribute(ACCESSRULES);
				return JavaCore.newVariableEntry(fPath, varAttach, null, accesRules, extraAttributes, isExported());
			}
			default:
				return null;
		}
	}

	/**
	 * Gets the class path entry path.
	 * @return returns the path
	 * @see IClasspathEntry#getPath()
	 */
	public IPath getPath() {
		return fPath;
	}

	/**
	 * Gets the class path entry kind.
	 * @return the entry kind
	 * @see IClasspathEntry#getEntryKind()
	 */
	public int getEntryKind() {
		return fEntryKind;
	}

	/**
	 * Entries without resource are either non existing or a variable entry
	 * External jars do not have a resource
	 * @return returns the resource
	 */
	public IResource getResource() {
		return fResource;
	}

	public CPListElementAttribute setAttribute(String key, Object value) {
		CPListElementAttribute attribute= findAttributeElement(key);
		if (attribute == null) {
			return null;
		}
		if (key.equals(EXCLUSION) || key.equals(INCLUSION)) {
			Assert.isTrue(value != null || fEntryKind != IClasspathEntry.CPE_SOURCE);
		}

		if (key.equals(ACCESSRULES)) {
			Assert.isTrue(value != null || fEntryKind == IClasspathEntry.CPE_SOURCE);
		}
		if (key.equals(COMBINE_ACCESSRULES)) {
			Assert.isTrue(value instanceof Boolean);
		}

		attribute.setValue(value);
		return attribute;
	}

	public boolean addToExclusions(IPath path) {
		String key= CPListElement.EXCLUSION;
		return addFilter(path, key);
	}

	public boolean addToInclusion(IPath path) {
		String key= CPListElement.INCLUSION;
		return addFilter(path, key);
	}

	public boolean removeFromExclusions(IPath path) {
		String key= CPListElement.EXCLUSION;
		return removeFilter(path, key);
	}

	public boolean removeFromInclusion(IPath path) {
		String key= CPListElement.INCLUSION;
		return removeFilter(path, key);
	}

	private boolean addFilter(IPath path, String key) {
		IPath[] filters= (IPath[]) getAttribute(key);
		if (filters == null)
			return false;

		if (!JavaModelUtil.isExcludedPath(path, filters)) {
			IPath toAdd= path.removeFirstSegments(getPath().segmentCount()).addTrailingSeparator();
			IPath[] newFilters= new IPath[filters.length + 1];
			System.arraycopy(filters, 0, newFilters, 0, filters.length);
			newFilters[filters.length]= toAdd;
			setAttribute(key, newFilters);
			return true;
		}
		return false;
	}

	private boolean removeFilter(IPath path, String key) {
		IPath[] filters= (IPath[]) getAttribute(key);
		if (filters == null)
			return false;

		IPath toRemove= path.removeFirstSegments(getPath().segmentCount()).addTrailingSeparator();
		if (JavaModelUtil.isExcludedPath(toRemove, filters)) {
			List<IPath> l= new ArrayList<IPath>(Arrays.asList(filters));
			l.remove(toRemove);
			IPath[] newFilters= l.toArray(new IPath[l.size()]);
			setAttribute(key, newFilters);
			return true;
		}
		return false;
	}

	public CPListElementAttribute findAttributeElement(String key) {
		for (int i= 0; i < fChildren.size(); i++) {
			Object curr= fChildren.get(i);
			if (curr instanceof CPListElementAttribute) {
				CPListElementAttribute elem= (CPListElementAttribute) curr;
				if (key.equals(elem.getKey())) {
					return elem;
				}
			}
		}
		return null;
	}


	public Object getAttribute(String key) {
		CPListElementAttribute attrib= findAttributeElement(key);
		if (attrib != null) {
			return attrib.getValue();
		}
		return null;
	}

	public CPListElementAttribute[] getAllAttributes() {
		ArrayList<Object> res= new ArrayList<Object>();
		for (int i= 0; i < fChildren.size(); i++) {
			Object curr= fChildren.get(i);
			if (curr instanceof CPListElementAttribute) {
				res.add(curr);
			}
		}
		return res.toArray(new CPListElementAttribute[res.size()]);
	}


	private void createAttributeElement(String key, Object value, boolean builtIn) {
		fChildren.add(new CPListElementAttribute(this, key, value, builtIn));
	}

	private static boolean isFiltered(Object entry, String[] filteredKeys) {
		if (entry instanceof CPListElementAttribute) {
			CPListElementAttribute curr= (CPListElementAttribute) entry;
			String key= curr.getKey();
			for (int i= 0; i < filteredKeys.length; i++) {
				if (key.equals(filteredKeys[i])) {
					return true;
				}
			}
			if (curr.isNotSupported()) {
				return true;
			}
			if (!curr.isBuiltIn() && !key.equals(CPListElement.JAVADOC) && !key.equals(CPListElement.NATIVE_LIB_PATH) && !key.equals(CPListElement.IGNORE_OPTIONAL_PROBLEMS)) {
				return !JavaPlugin.getDefault().getClasspathAttributeConfigurationDescriptors().containsKey(key);
			}
		}
		return false;
	}

	private Object[] getFilteredChildren(String[] filteredKeys) {
		int nChildren= fChildren.size();
		ArrayList<Object> res= new ArrayList<Object>(nChildren);

		for (int i= 0; i < nChildren; i++) {
			Object curr= fChildren.get(i);
			if (!isFiltered(curr, filteredKeys)) {
				res.add(curr);
			}
		}
		return res.toArray();
	}

	public Object[] getChildren(boolean hideOutputFolder) {
		if (hideOutputFolder && fEntryKind == IClasspathEntry.CPE_SOURCE) {
			return getFilteredChildren(new String[] { OUTPUT });
		}
		/*if (isInContainer(JavaRuntime.JRE_CONTAINER)) {
			return getFilteredChildren(new String[] { COMBINE_ACCESSRULES, NATIVE_LIB_PATH });
		}*/
		if (fEntryKind == IClasspathEntry.CPE_PROJECT) {
			return getFilteredChildren(new String[] { COMBINE_ACCESSRULES });
		}
		return getFilteredChildren(new String[0]);
	}

	public Object getParentContainer() {
		return fParentContainer;
	}

	/**
	 * Sets the parent container.
	 * 
	 * @param parent the parent container
	 * @since 3.7
	 */
	void setParentContainer(CPUserLibraryElement parent) {
		fParentContainer= parent;
	}

	/**
	 * Notifies that an attribute has changed
	 *
	 * @param key the changed key
	 */
	protected void attributeChanged(String key) {
		fCachedEntry= null;
	}

	private IStatus evaluateContainerChildStatus(CPListElementAttribute attrib) {
		if (fProject != null) {
			ClasspathContainerInitializer initializer= JavaCore.getClasspathContainerInitializer(fPath.segment(0));
			if (initializer != null && initializer.canUpdateClasspathContainer(fPath, fProject)) {
				if (attrib.isBuiltIn()) {
					if (CPListElement.SOURCEATTACHMENT.equals(attrib.getKey())) {
						return initializer.getSourceAttachmentStatus(fPath, fProject);
					} else if (CPListElement.ACCESSRULES.equals(attrib.getKey())) {
						return initializer.getAccessRulesStatus(fPath, fProject);
					}
				} else {
					return initializer.getAttributeStatus(fPath, fProject, attrib.getKey());
				}
			}
			return new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, ClasspathContainerInitializer.ATTRIBUTE_READ_ONLY, "", null); //$NON-NLS-1$
		}
		return null;
	}

	public IStatus getContainerChildStatus(CPListElementAttribute attrib) {
		if (fParentContainer instanceof CPListElement) {
			CPListElement parent= (CPListElement) fParentContainer;
			if (parent.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
				return parent.evaluateContainerChildStatus(attrib);
			}
			return ((CPListElement) fParentContainer).getContainerChildStatus(attrib);
		}
		return null;
	}

	public boolean isInContainer(String containerName) {
		if (fParentContainer instanceof CPListElement) {
			CPListElement elem= (CPListElement) fParentContainer;
			return new Path(containerName).isPrefixOf(elem.getPath());
		}
		return false;
	}

	public boolean isDeprecated() {
		if (fEntryKind != IClasspathEntry.CPE_VARIABLE) {
			return false;
		}
		if (fPath.segmentCount() > 0) {
			return JavaCore.getClasspathVariableDeprecationMessage(fPath.segment(0)) != null;
		}
		return false;
	}

	public String getDeprecationMessage() {
		if (fEntryKind != IClasspathEntry.CPE_VARIABLE) {
			return null;
		}
		if (fPath.segmentCount() > 0) {
			String varName= fPath.segment(0);
			return BuildPathSupport.getDeprecationMessage(varName);
		}
		return null;
	}

	/*
	 * @see Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object other) {
		if (other != null && other.getClass().equals(getClass())) {
			CPListElement elem= (CPListElement) other;
			return getClasspathEntry().equals(elem.getClasspathEntry());
		}
		return false;
	}

	/*
	 * @see Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return fPath.hashCode() + fEntryKind;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return getClasspathEntry().toString();
	}

	/**
	 * Returns if a entry is missing.
	 * @return Returns a boolean
	 */
	public boolean isMissing() {
		return fIsMissing;
	}

	/**
	 * Returns if a entry has children that are missing
	 * @return Returns a boolean
	 */
	public boolean hasMissingChildren() {
		for (int i= 0; i < fChildren.size(); i++) {
			Object curr= fChildren.get(i);
			if (curr instanceof CPListElement && ((CPListElement) curr).isMissing()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Sets the 'missing' state of the entry.
	 * @param isMissing the new state
	 */
	public void setIsMissing(boolean isMissing) {
		fIsMissing= isMissing;
	}

	/**
	 * Returns if a entry is exported (only applies to libraries)
	 * @return Returns a boolean
	 */
	public boolean isExported() {
		return fIsExported;
	}

	/**
	 * Sets the export state of the entry.
	 * @param isExported the new state
	 */
	public void setExported(boolean isExported) {
		if (isExported != fIsExported) {
			fIsExported = isExported;

			attributeChanged(null);
		}
	}

	/**
	 * Gets the project.
	 * @return Returns a IJavaProject
	 */
	public IJavaProject getJavaProject() {
		return fProject;
	}

	public static CPListElement createFromExisting(IClasspathEntry curr, IJavaProject project) {
		//Note: Some old clients of this method could actually mean create(curr, true, project)
		return create(curr, false, project);
	}

	public static CPListElement createFromExisting(Object parent, IClasspathEntry curr, IJavaProject project) {
		//Note: Some old clients of this method could actually mean create(parent, curr, true, project)
		return create(parent, curr, false, project);
	}
	
	public static CPListElement create(IClasspathEntry curr, boolean newElement, IJavaProject project) {
		return create(null, curr, newElement, project);
	}
	
	public static CPListElement create(Object parent, IClasspathEntry curr, boolean newElement, IJavaProject project) {
		IPath path= curr.getPath();
		IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();

		// get the resource
		IResource res= null;
		boolean isMissing= false;
		IPath linkTarget= null;

		switch (curr.getEntryKind()) {
			case IClasspathEntry.CPE_CONTAINER:
				try {
					isMissing= project != null && (JavaCore.getClasspathContainer(path, project) == null);
				} catch (JavaModelException e) {
					isMissing= true;
				}
				break;
			case IClasspathEntry.CPE_VARIABLE:
				IPath resolvedPath= JavaCore.getResolvedVariablePath(path);
				isMissing=  root.findMember(resolvedPath) == null && !resolvedPath.toFile().exists();
				break;
			case IClasspathEntry.CPE_LIBRARY:
				res= root.findMember(path);
				if (res == null) {
					if (!ArchiveFileFilter.isArchivePath(path, true)) {
						if (root.getWorkspace().validatePath(path.toString(), IResource.FOLDER).isOK()
								&& root.getProject(path.segment(0)).exists()) {
							res= root.getFolder(path);
						}
					}

					IPath rawPath= path;
					if (project != null) {
						IPackageFragmentRoot[] roots= project.findPackageFragmentRoots(curr);
						if (roots.length == 1)
							rawPath= roots[0].getPath();
					}
					isMissing= !rawPath.toFile().exists(); // look for external JARs and folders
				} else if (res.isLinked()) {
					linkTarget= res.getLocation();
				}
				break;
			case IClasspathEntry.CPE_SOURCE:
				path= path.removeTrailingSeparator();
				res= root.findMember(path);
				if (res == null) {
					if (root.getWorkspace().validatePath(path.toString(), IResource.FOLDER).isOK()) {
						res= root.getFolder(path);
					}
					isMissing= true;
				} else if (res.isLinked()) {
					linkTarget= res.getLocation();
				}
				break;
			case IClasspathEntry.CPE_PROJECT:
				res= root.findMember(path);
				isMissing= (res == null);
				break;
		}
		CPListElement elem= new CPListElement(parent, project, curr.getEntryKind(), path, newElement, res, linkTarget);
		elem.setExported(curr.isExported());
		elem.setAttribute(SOURCEATTACHMENT, curr.getSourceAttachmentPath());
		elem.setAttribute(OUTPUT, curr.getOutputLocation());
		elem.setAttribute(EXCLUSION, curr.getExclusionPatterns());
		elem.setAttribute(INCLUSION, curr.getInclusionPatterns());
		elem.setAttribute(ACCESSRULES, curr.getAccessRules());
		elem.setAttribute(COMBINE_ACCESSRULES, new Boolean(curr.combineAccessRules()));

		IClasspathAttribute[] extraAttributes= curr.getExtraAttributes();
		for (int i= 0; i < extraAttributes.length; i++) {
			IClasspathAttribute attrib= extraAttributes[i];
			CPListElementAttribute attribElem= elem.findAttributeElement(attrib.getName());
			if (attribElem == null) {
				elem.createAttributeElement(attrib.getName(), attrib.getValue(), false);
			} else {
				attribElem.setValue(attrib.getValue());
			}
		}

		elem.setIsMissing(isMissing);
		return elem;
	}

	public static StringBuffer appendEncodePath(IPath path, StringBuffer buf) {
		if (path != null) {
			String str= path.toString();
			buf.append('[').append(str.length()).append(']').append(str);
		} else {
			buf.append('[').append(']');
		}
		return buf;
	}

	public static StringBuffer appendEncodedString(String str, StringBuffer buf) {
		if (str != null) {
			buf.append('[').append(str.length()).append(']').append(str);
		} else {
			buf.append('[').append(']');
		}
		return buf;
	}

	public static StringBuffer appendEncodedFilter(IPath[] filters, StringBuffer buf) {
		if (filters != null) {
			buf.append('[').append(filters.length).append(']');
			for (int i= 0; i < filters.length; i++) {
				appendEncodePath(filters[i], buf).append(';');
			}
		} else {
			buf.append('[').append(']');
		}
		return buf;
	}

	public static StringBuffer appendEncodedAccessRules(IAccessRule[] rules, StringBuffer buf) {
		if (rules != null) {
			buf.append('[').append(rules.length).append(']');
			for (int i= 0; i < rules.length; i++) {
				appendEncodePath(rules[i].getPattern(), buf).append(';');
				buf.append(rules[i].getKind()).append(';');
			}
		} else {
			buf.append('[').append(']');
		}
		return buf;
	}


	public StringBuffer appendEncodedSettings(StringBuffer buf) {
		buf.append(fEntryKind).append(';');
		if (getLinkTarget() == null) {
			appendEncodePath(fPath, buf).append(';');
		} else {
			appendEncodePath(fPath, buf).append('-').append('>');
			appendEncodePath(getLinkTarget(), buf).append(';');
		}
		buf.append(Boolean.valueOf(fIsExported)).append(';');
		for (int i= 0; i < fChildren.size(); i++) {
			Object curr= fChildren.get(i);
			if (curr instanceof CPListElementAttribute) {
				CPListElementAttribute elem= (CPListElementAttribute) curr;
				if (elem.isBuiltIn()) {
					String key= elem.getKey();
					if (OUTPUT.equals(key) || SOURCEATTACHMENT.equals(key)) {
						appendEncodePath((IPath) elem.getValue(), buf).append(';');
					} else if (EXCLUSION.equals(key) || INCLUSION.equals(key)) {
						appendEncodedFilter((IPath[]) elem.getValue(), buf).append(';');
					} else if (ACCESSRULES.equals(key)) {
						appendEncodedAccessRules((IAccessRule[]) elem.getValue(), buf).append(';');
					} else if (COMBINE_ACCESSRULES.equals(key)) {
						buf.append(((Boolean) elem.getValue()).booleanValue()).append(';');
					}
				} else {
					appendEncodedString((String) elem.getValue(), buf);
				}
			}
		}
		return buf;
	}

	public IPath getLinkTarget() {
		return fLinkTarget;
	}

	public void setPath(IPath path) {
		fCachedEntry= null;
		fPath= path;
	}

	public void setLinkTarget(IPath linkTarget) {
		fCachedEntry= null;
		fLinkTarget= linkTarget;
	}

	public static void insert(CPListElement element, List<CPListElement> cpList) {
		int length= cpList.size();
		CPListElement[] elements= cpList.toArray(new CPListElement[length]);
		int i= 0;
		while (i < length && elements[i].getEntryKind() != element.getEntryKind()) {
			i++;
		}
		if (i < length) {
			i++;
			while (i < length && elements[i].getEntryKind() == element.getEntryKind()) {
				i++;
			}
			cpList.add(i, element);
			return;
		}

		switch (element.getEntryKind()) {
		case IClasspathEntry.CPE_SOURCE:
			cpList.add(0, element);
			break;
		case IClasspathEntry.CPE_CONTAINER:
		case IClasspathEntry.CPE_LIBRARY:
		case IClasspathEntry.CPE_PROJECT:
		case IClasspathEntry.CPE_VARIABLE:
		default:
			cpList.add(element);
			break;
		}
	}

	public static IClasspathEntry[] convertToClasspathEntries(List<CPListElement> cpList) {
		IClasspathEntry[] result= new IClasspathEntry[cpList.size()];
		int i= 0;
		for (Iterator<CPListElement> iter= cpList.iterator(); iter.hasNext();) {
			CPListElement cur= iter.next();
			result[i]= cur.getClasspathEntry();
			i++;
		}
		return result;
	}

	public static CPListElement[] createFromExisting(IJavaProject project) throws JavaModelException {
		IClasspathEntry[] rawClasspath= project.getRawClasspath();
		CPListElement[] result= new CPListElement[rawClasspath.length];
		for (int i= 0; i < rawClasspath.length; i++) {
			result[i]= CPListElement.createFromExisting(rawClasspath[i], project);
		}
		return result;
	}

	public static boolean isProjectSourceFolder(CPListElement[] existing, IJavaProject project) {
		IPath projPath= project.getProject().getFullPath();
		for (int i= 0; i < existing.length; i++) {
			IClasspathEntry curr= existing[i].getClasspathEntry();
			if (curr.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				if (projPath.equals(curr.getPath())) {
					return true;
				}
			}
		}
		return false;
	}

	public IPath getOrginalPath() {
		return fOrginalPath;
	}

	public IPath getOrginalLinkTarget() {
		return fOrginalLinkTarget;
	}


    public CPListElement copy() {
    	CPListElement result= new CPListElement();
    	result.fProject= fProject;
    	result.fEntryKind= fEntryKind;
    	result.fPath= fPath;
    	result.fOrginalPath= fOrginalPath;
    	result.fResource= fResource;
    	result.fIsExported= fIsExported;
    	result.fIsMissing= fIsMissing;
    	result.fParentContainer= fParentContainer;
    	result.fCachedEntry= null;
    	result.fChildren= new ArrayList<Object>(fChildren.size());
    	for (Iterator<Object> iterator= fChildren.iterator(); iterator.hasNext();) {
    		Object child= iterator.next();
    		if (child instanceof CPListElement) {
    			result.fChildren.add(((CPListElement)child).copy());
    		} else {
	        	result.fChildren.add(((CPListElementAttribute)child).copy());
    		}
        }
    	result.fLinkTarget= fLinkTarget;
    	result.fOrginalLinkTarget= fOrginalLinkTarget;
	    return result;
    }

    public void setAttributesFromExisting(CPListElement existing) {
    	Assert.isTrue(existing.getEntryKind() == getEntryKind());
		CPListElementAttribute[] attributes= existing.getAllAttributes();
		for (int i= 0; i < attributes.length; i++) {
			CPListElementAttribute curr= attributes[i];
			CPListElementAttribute elem= findAttributeElement(curr.getKey());
			if (elem == null) {
				createAttributeElement(curr.getKey(), curr.getValue(), false);
			} else {
				elem.setValue(curr.getValue());
			}
		}
    }

}

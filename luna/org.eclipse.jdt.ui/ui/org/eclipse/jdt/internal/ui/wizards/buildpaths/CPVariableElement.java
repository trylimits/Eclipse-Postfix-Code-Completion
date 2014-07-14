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
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;

import org.eclipse.jdt.core.JavaCore;


public class CPVariableElement {

	private String fName;
	private IPath fPath;

	/**
	 * @param name the variable name
	 * @param path the path
	 */
	public CPVariableElement(String name, IPath path) {
		Assert.isNotNull(name);
		Assert.isNotNull(path);
		fName= name;
		fPath= path;
	}

	/**
	 * Gets the path
	 * @return Returns a IPath
	 */
	public IPath getPath() {
		return fPath;
	}

	/**
	 * Sets the path
	 * @param path The path to set
	 */
	public void setPath(IPath path) {
		Assert.isNotNull(path);
		fPath= path;
	}

	/**
	 * Gets the name
	 * @return Returns a String
	 */
	public String getName() {
		return fName;
	}

	/**
	 * Sets the name
	 * @param name The name to set
	 */
	public void setName(String name) {
		Assert.isNotNull(name);
		fName= name;
	}

	/*
	 * @see Object#equals()
	 */
	@Override
	public boolean equals(Object other) {
		if (other != null && other.getClass().equals(getClass())) {
			CPVariableElement elem= (CPVariableElement)other;
			return fName.equals(elem.fName);
		}
		return false;
	}

	/*
	 * @see Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return fName.hashCode();
	}

	/**
	 * @return <code>true</code> iff variable is read-only
	 */
	public boolean isReadOnly() {
		return JavaCore.isClasspathVariableReadOnly(fName);
	}

	/**
	 * @return whether this variable is deprecated
	 */
	public boolean isDeprecated() {
		return JavaCore.getClasspathVariableDeprecationMessage(fName) != null;
	}

	/**
	 * @return the deprecation message, or <code>null</code> iff the variable is not deprecated
	 */
	public String getDeprecationMessage() {
		return BuildPathSupport.getDeprecationMessage(fName);
	}
}

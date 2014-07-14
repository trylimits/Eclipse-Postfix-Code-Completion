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
package org.eclipse.jdt.internal.corext.buildpath;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IResource;

import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;

public class BuildpathDelta {

	private final String fOperationDescription;
	private CPListElement[] fNewEntries;
	private final List<IResource> fCreatedResources;
	private IPath fOutputLocation;
	private final List<IResource> fDeletedResources;
	private final List<CPListElement> fAddedEntries;
	private final ArrayList<CPListElement> fRemovedEntries;

	public BuildpathDelta(String operationDescription) {
		fOperationDescription= operationDescription;

		fCreatedResources= new ArrayList<IResource>();
		fDeletedResources= new ArrayList<IResource>();
		fAddedEntries= new ArrayList<CPListElement>();
		fRemovedEntries= new ArrayList<CPListElement>();
    }

	public String getOperationDescription() {
		return fOperationDescription;
	}

	public CPListElement[] getNewEntries() {
		return fNewEntries;
	}

	public IResource[] getCreatedResources() {
		return fCreatedResources.toArray(new IResource[fCreatedResources.size()]);
	}

	public IResource[] getDeletedResources() {
		return fDeletedResources.toArray(new IResource[fDeletedResources.size()]);
	}

	public IPath getDefaultOutputLocation() {
		return fOutputLocation;
	}

	public void setNewEntries(CPListElement[] newEntries) {
		fNewEntries= newEntries;
    }

	public void addCreatedResource(IResource resource) {
		fCreatedResources.add(resource);
    }

	public void setDefaultOutputLocation(IPath outputLocation) {
		fOutputLocation= outputLocation;
    }

	public void addDeletedResource(IResource resource) {
		fDeletedResources.add(resource);
    }

    public List<CPListElement> getAddedEntries() {
	    return fAddedEntries;
    }

    public void addEntry(CPListElement entry) {
    	fAddedEntries.add(entry);
    }

    public List<CPListElement> getRemovedEntries() {
    	return fRemovedEntries;
    }

    public void removeEntry(CPListElement entry) {
    	fRemovedEntries.add(entry);
    }
}

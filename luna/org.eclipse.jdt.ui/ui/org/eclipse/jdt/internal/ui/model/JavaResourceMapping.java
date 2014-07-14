/*******************************************************************************
 * Copyright (c) 2005, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.model;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.resources.mapping.ResourceMappingContext;
import org.eclipse.core.resources.mapping.ResourceTraversal;

/**
 * Resource mapping for the java model provider.
 *
 * @since 3.2
 */
public final class JavaResourceMapping extends ResourceMapping {

	/** The resource to map */
	private final IResource fResource;

	/**
	 * Creates a new java resource mapping.
	 *
	 * @param resource
	 *            the resource to map
	 */
	public JavaResourceMapping(final IResource resource) {
		Assert.isNotNull(resource);
		fResource= resource;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getModelObject() {
		return fResource;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getModelProviderId() {
		return JavaModelProvider.JAVA_MODEL_PROVIDER_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IProject[] getProjects() {
		return new IProject[] { fResource.getProject() };
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResourceTraversal[] getTraversals(final ResourceMappingContext context, final IProgressMonitor monitor) {
		return new ResourceTraversal[] { new ResourceTraversal(new IResource[] { fResource }, IResource.DEPTH_INFINITE, IResource.NONE) };
	}
}

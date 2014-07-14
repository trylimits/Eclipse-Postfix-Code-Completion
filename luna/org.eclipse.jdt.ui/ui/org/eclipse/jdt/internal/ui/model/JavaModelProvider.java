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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.mapping.ModelProvider;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.resources.mapping.ResourceMappingContext;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.util.JavaElementResourceMapping;

/**
 * Java-aware model provider.
 *
 * @since 3.2
 */
public final class JavaModelProvider extends ModelProvider {

	/** The model provider id */
	public static final String JAVA_MODEL_PROVIDER_ID= "org.eclipse.jdt.ui.modelProvider"; //$NON-NLS-1$

	/**
	 * Returns the resource associated with the corresponding model element.
	 *
	 * @param element
	 *            the model element
	 * @return the associated resource, or <code>null</code>
	 */
	public static IResource getResource(final Object element) {
		IResource resource= null;
		if (element instanceof IJavaElement) {
			resource= ((IJavaElement) element).getResource();
		} else if (element instanceof IResource) {
			resource= (IResource) element;
		} else if (element instanceof IAdaptable) {
			final IAdaptable adaptable= (IAdaptable) element;
			final Object adapted= adaptable.getAdapter(IResource.class);
			if (adapted instanceof IResource)
				resource= (IResource) adapted;
		} else {
			final Object adapted= Platform.getAdapterManager().getAdapter(element, IResource.class);
			if (adapted instanceof IResource)
				resource= (IResource) adapted;
		}
		return resource;
	}

	/**
	 * Creates a new java model provider.
	 */
	public JavaModelProvider() {
		// Used by the runtime
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResourceMapping[] getMappings(final IResource resource, final ResourceMappingContext context, final IProgressMonitor monitor) throws CoreException {
		final IJavaElement element= JavaCore.create(resource);
		if (element != null)
			return new ResourceMapping[] { JavaElementResourceMapping.create(element)};
		final Object adapted= resource.getAdapter(ResourceMapping.class);
		if (adapted instanceof ResourceMapping)
			return new ResourceMapping[] { ((ResourceMapping) adapted)};
		return new ResourceMapping[] { new JavaResourceMapping(resource)};
	}
}

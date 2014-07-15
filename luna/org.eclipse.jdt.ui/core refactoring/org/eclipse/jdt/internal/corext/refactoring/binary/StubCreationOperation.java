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
package org.eclipse.jdt.internal.corext.refactoring.binary;

import java.net.URI;
import java.util.List;

import org.eclipse.core.filesystem.IFileStore;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

/**
 * Operation, which run, creates structurally equivalent stub types for a list
 * of binary package fragments.
 *
 * @since 3.2
 */
public class StubCreationOperation extends AbstractCodeCreationOperation {

	/** Should stubs for private member be generated as well? */
	protected final boolean fStubInvisible;

	/**
	 * Creates a new stub creation operation.
	 *
	 * @param uri
	 *            the URI where to output the stubs
	 * @param packages
	 *            the list of packages to create stubs for
	 */
	public StubCreationOperation(final URI uri, final List<IPackageFragment> packages) {
		this(uri, packages, false);
	}

	/**
	 * Creates a new stub creation operation.
	 *
	 * @param uri
	 *            the URI where to output the stubs
	 * @param packages
	 *            the list of packages to create stubs for
	 * @param stub
	 *            <code>true</code> to generate stubs for private and package
	 *            visible members as well, <code>false</code> otherwise
	 */
	public StubCreationOperation(final URI uri, final List<IPackageFragment> packages, final boolean stub) {
		super(uri, packages);
		fStubInvisible= stub;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getOperationLabel() {
		return RefactoringCoreMessages.StubCreationOperation_creating_type_stubs;
	}

	/**
	 * Runs the stub generation on the specified class file.
	 *
	 * @param file
	 *            the class file
	 * @param parent
	 *            the parent store
	 * @param monitor
	 *            the progress monitor to use
	 * @throws CoreException
	 *             if an error occurs
	 */
	@Override
	protected void run(final IClassFile file, final IFileStore parent, final IProgressMonitor monitor) throws CoreException {
		try {
			monitor.beginTask(RefactoringCoreMessages.StubCreationOperation_creating_type_stubs, 2);
			SubProgressMonitor subProgressMonitor= new SubProgressMonitor(monitor, 1);
			final IType type= file.getType();
			if (type.isAnonymous() || type.isLocal() || type.isMember())
				return;
			String source= new StubCreator(fStubInvisible).createStub(type, subProgressMonitor);
			createCompilationUnit(parent, type.getElementName() + JavaModelUtil.DEFAULT_CU_SUFFIX, source, monitor);
		} finally {
			monitor.done();
		}
	}
}

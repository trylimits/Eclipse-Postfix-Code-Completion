/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Ferenc Hechler, ferenc_hechler@users.sourceforge.net - 83258 [jar exporter] Deploy java application as executable jar
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackager;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.ui.jarpackager.IJarBuilder;
import org.eclipse.jdt.ui.jarpackager.JarPackageData;

import org.eclipse.jdt.internal.ui.IJavaStatusConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Base class for all jar builders.
 *
 * @since 3.4
 */
public abstract class JarBuilder implements IJarBuilder {

	private MultiStatus fStatus;

	/**
	 * {@inheritDoc}
	 */
	public void open(JarPackageData jarPackage, Shell shell, MultiStatus status) throws CoreException {
		fStatus= status;
	}

	//some methods for convenience
	protected final void addInfo(String message, Throwable error) {
		fStatus.add(new Status(IStatus.INFO, JavaPlugin.getPluginId(), IJavaStatusConstants.INTERNAL_ERROR, message, error));
	}

	protected final void addWarning(String message, Throwable error) {
		fStatus.add(new Status(IStatus.WARNING, JavaPlugin.getPluginId(), IJavaStatusConstants.INTERNAL_ERROR, message, error));
	}

	protected final void addError(String message, Throwable error) {
		fStatus.add(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IJavaStatusConstants.INTERNAL_ERROR, message, error));
	}

	protected final void addToStatus(CoreException ex) {
		IStatus status= ex.getStatus();
		String message= ex.getLocalizedMessage();
		if (message == null || message.length() < 1) {
			message= JarPackagerMessages.JarFileExportOperation_coreErrorDuringExport;
			status= new Status(status.getSeverity(), status.getPlugin(), status.getCode(), message, ex);
		}
		fStatus.add(status);
	}
}

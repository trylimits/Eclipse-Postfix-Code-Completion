/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.cleanup;


/**
 * Initializes the default options for a clean up kind.
 * 
 * @since 3.5
 */
public interface ICleanUpOptionsInitializer {

	/**
	 * Sets the default options of this initializer.
	 * 
	 * @param options the clean up options
	 */
	public void setDefaultOptions(CleanUpOptions options);
}

/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences.cleanup;

import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpOptionsInitializer;


/**
 * The clean up initializer for save actions.
 * 
 * @since 3.5
 */
public class SaveActionCleanUpOptionsInitializer implements ICleanUpOptionsInitializer {


	/*
	 * @see org.eclipse.jdt.ui.cleanup.ICleanUpOptionsInitializer#setDefaultOptions(org.eclipse.jdt.ui.cleanup.CleanUpOptions)
	 * @since 3.5
	 */
	public void setDefaultOptions(CleanUpOptions options) {
		CleanUpConstants.setDefaultOptions(CleanUpConstants.DEFAULT_SAVE_ACTION_OPTIONS, options);
	}

}

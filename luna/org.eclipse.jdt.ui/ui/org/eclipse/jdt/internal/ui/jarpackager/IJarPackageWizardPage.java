/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.jarpackager;

import org.eclipse.jface.wizard.IWizardPage;

/**
 * Common interface for all JAR package wizard pages.
 */
interface IJarPackageWizardPage extends IWizardPage {
	/**
	 * Tells the page that the user has pressed finish.
	 */
	void finish();
}

/*******************************************************************************
 * Copyright (c) 2008, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences.cleanup;

import java.util.Map;

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.fix.MapCleanUpOptions;

public abstract class AbstractCleanUpTabPage extends CleanUpTabPage {

	private AbstractCleanUp[] fPreviewCleanUps;
	private Map<String, String> fValues;

	public AbstractCleanUpTabPage() {
		super();
	}

	protected abstract AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values);

	/* 
	 * @see org.eclipse.jdt.internal.ui.preferences.cleanup.CleanUpTabPage#setWorkingValues(java.util.Map)
	 */
	@Override
	public void setWorkingValues(Map<String, String> workingValues) {
		super.setWorkingValues(workingValues);
		fValues= workingValues;
		setOptions(new MapCleanUpOptions(workingValues));
	}

	/* 
	 * @see org.eclipse.jdt.internal.ui.preferences.cleanup.ICleanUpTabPage#setOptions(org.eclipse.jdt.internal.ui.fix.CleanUpOptions)
	 */
	public void setOptions(CleanUpOptions options) {
	}

	/* 
	 * @see org.eclipse.jdt.internal.ui.preferences.cleanup.ICleanUpTabPage#getPreview()
	 */
	public String getPreview() {
		if (fPreviewCleanUps == null) {
			fPreviewCleanUps= createPreviewCleanUps(fValues);
		}
	
		StringBuffer buf= new StringBuffer();
		for (int i= 0; i < fPreviewCleanUps.length; i++) {
			buf.append(fPreviewCleanUps[i].getPreview());
			buf.append("\n"); //$NON-NLS-1$
		}
		return buf.toString();
	}

}

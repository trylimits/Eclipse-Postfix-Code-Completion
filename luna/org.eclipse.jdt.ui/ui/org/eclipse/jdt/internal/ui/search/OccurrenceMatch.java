/*******************************************************************************
 * Copyright (c) 2007, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.search;

import org.eclipse.jface.text.Region;

import org.eclipse.search.ui.text.Match;

public class OccurrenceMatch extends Match {

	private final int fFlags;
	private Region fOriginalLocation;

	public OccurrenceMatch(JavaElementLine element, int offset, int length, int flags) {
		super(element, offset, length);
		fFlags= flags;
		fOriginalLocation= null;
	}

	@Override
	public void setOffset(int offset) {
		if (fOriginalLocation == null) {
			// remember the original location before changing it
			fOriginalLocation= new Region(getOffset(), getLength());
		}
		super.setOffset(offset);
	}

	@Override
	public void setLength(int length) {
		if (fOriginalLocation == null) {
			// remember the original location before changing it
			fOriginalLocation= new Region(getOffset(), getLength());
		}
		super.setLength(length);
	}

	public int getOriginalOffset() {
		if (fOriginalLocation != null) {
			return fOriginalLocation.getOffset();
		}
		return getOffset();
	}

	public int getOriginalLength() {
		if (fOriginalLocation != null) {
			return fOriginalLocation.getLength();
		}
		return getLength();
	}

	public int getFlags() {
		return fFlags;
	}


}

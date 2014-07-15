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
package org.eclipse.jdt.internal.ui.search;

import java.util.Comparator;

import com.ibm.icu.text.Collator;

import org.eclipse.ui.IWorkingSet;

class WorkingSetsComparator implements Comparator<IWorkingSet[]> {

	private Collator fCollator= Collator.getInstance();

	/*
	 * @see Comparator#compare(Object, Object)
	 */
	public int compare(IWorkingSet[] w1, IWorkingSet[] w2) {
		return fCollator.compare(w1[0].getLabel(), w2[0].getLabel());
	}
}

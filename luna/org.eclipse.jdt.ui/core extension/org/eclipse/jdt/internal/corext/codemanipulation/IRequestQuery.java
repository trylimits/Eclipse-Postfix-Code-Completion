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
package org.eclipse.jdt.internal.corext.codemanipulation;

import org.eclipse.jdt.core.IMember;


/**
 * Query object to let operations callback the actions.
 * Example is a callback to ask if a existing method should be replaced.
 */
public interface IRequestQuery {

	// return codes
	public static final int CANCEL= 0;
	public static final int NO= 1;
	public static final int YES= 2;
	public static final int YES_ALL= 3;

	/**
	 * Do the callback. Returns YES, NO, YES_ALL or CANCEL
	 */
	int doQuery(IMember member);
}

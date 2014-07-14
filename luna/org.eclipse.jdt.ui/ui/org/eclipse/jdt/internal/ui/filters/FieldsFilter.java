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

package org.eclipse.jdt.internal.ui.filters;

import org.eclipse.jdt.internal.ui.viewsupport.MemberFilter;

/**
 * Fields filter.
 *
 * @since 3.0
 */
public class FieldsFilter extends MemberFilter {
	public FieldsFilter() {
		addFilter(MemberFilter.FILTER_FIELDS);
	}
}

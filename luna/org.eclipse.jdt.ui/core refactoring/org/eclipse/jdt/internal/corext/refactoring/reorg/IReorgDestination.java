/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.reorg;

import org.eclipse.jdt.internal.ui.dnd.JdtViewerDropAdapter;

/**
 * {@link ReorgDestinationFactory} can create concrete
 * instances
 */
public interface IReorgDestination {

    public static final int LOCATION_BEFORE = JdtViewerDropAdapter.LOCATION_BEFORE;
    public static final int LOCATION_AFTER = JdtViewerDropAdapter.LOCATION_AFTER;
    public static final int LOCATION_ON = JdtViewerDropAdapter.LOCATION_ON;

	public Object getDestination();

	public int getLocation();
}

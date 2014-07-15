/*******************************************************************************
 * Copyright (c) 2006, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.javaeditor.saveparticipant;

import org.eclipse.osgi.util.NLS;

/**
 * Helper class to get NLSed messages.
 *
 * @since 3.3
 */
final class SaveParticipantMessages extends NLS {

	private static final String BUNDLE_NAME= SaveParticipantMessages.class.getName();

	private SaveParticipantMessages() {
		// Do not instantiate
	}

	public static String SaveParticipantRegistry_needsChangedRegionCausedException;
	public static String SaveParticipantRegistry_needsChangedRegionFailed;

	static {
		NLS.initializeMessages(BUNDLE_NAME, SaveParticipantMessages.class);
	}
}

/*******************************************************************************
 * Copyright (c) 2000, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.fix;

import org.eclipse.osgi.util.NLS;

public class SaveParticipantMessages extends NLS {
	private static final String BUNDLE_NAME= "org.eclipse.jdt.internal.ui.fix.SaveParticipantMessages"; //$NON-NLS-1$

	public static String CleanUpSaveParticipantConfigurationModifyDialog_SelectAnAction_Error;

	public static String CleanUpSaveParticipantConfigurationModifyDialog_XofYSelected_Label;

	public static String CleanUpSaveParticipantPreferenceConfiguration_AdditionalActions_Checkbox;
	public static String CleanUpSaveParticipantPreferenceConfiguration_CleanUpActionsTopNodeName_Checkbox;
	public static String CleanUpSaveParticipantPreferenceConfiguration_CleanUpSaveParticipantConfiguration_Title;
	public static String CleanUpSaveParticipantPreferenceConfiguration_Configure_Button;
	public static String CleanUpSaveParticipantPreferenceConfiguration_ConfigureFormatter_Link;
	public static String CleanUpSaveParticipantPreferenceConfiguration_ConfigureImports_Link;

	public static String CleanUpSaveParticipantPreferenceConfiguration_SaveActionPreferencePAge_FormatAllLines_Radio;

	public static String CleanUpSaveParticipantPreferenceConfiguration_SaveActionPreferencePage_FormatOnlyChangedRegions_Radio;
	public static String CleanUpSaveParticipantPreferenceConfiguration_SaveActionPreferencePage_FormatSource_Checkbox;
	public static String CleanUpSaveParticipantPreferenceConfiguration_SaveActionPreferencePage_OrganizeImports_Checkbox;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, SaveParticipantMessages.class);
	}

	private SaveParticipantMessages() {}
}

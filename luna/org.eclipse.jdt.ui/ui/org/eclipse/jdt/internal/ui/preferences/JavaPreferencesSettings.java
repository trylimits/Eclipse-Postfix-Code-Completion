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
package org.eclipse.jdt.internal.ui.preferences;

import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;

import org.eclipse.jdt.ui.PreferenceConstants;

public class JavaPreferencesSettings  {

	public static CodeGenerationSettings getCodeGenerationSettings(IJavaProject project) {
		CodeGenerationSettings res= new CodeGenerationSettings();
		res.createComments= Boolean.valueOf(PreferenceConstants.getPreference(PreferenceConstants.CODEGEN_ADD_COMMENTS, project)).booleanValue();
		res.useKeywordThis= Boolean.valueOf(PreferenceConstants.getPreference(PreferenceConstants.CODEGEN_KEYWORD_THIS, project)).booleanValue();
		res.overrideAnnotation= Boolean.valueOf(PreferenceConstants.getPreference(PreferenceConstants.CODEGEN_USE_OVERRIDE_ANNOTATION, project)).booleanValue();
		res.importIgnoreLowercase= Boolean.valueOf(PreferenceConstants.getPreference(PreferenceConstants.ORGIMPORTS_IGNORELOWERCASE, project)).booleanValue();
		res.tabWidth= CodeFormatterUtil.getTabWidth(project);
		res.indentWidth= CodeFormatterUtil.getIndentWidth(project);
		return res;
	}

}


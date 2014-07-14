/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.propertiesfileeditor;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.quickassist.QuickAssistAssistant;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPreferenceConstants;
import org.eclipse.ui.PlatformUI;

import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * The properties file correction assistant.
 * 
 * @since 3.7
 */
public class PropertiesCorrectionAssistant extends QuickAssistAssistant {

	private ITextEditor fEditor;

	public PropertiesCorrectionAssistant(ITextEditor editor) {
		super();
		Assert.isNotNull(editor);
		fEditor= editor;

		setQuickAssistProcessor(new PropertiesCorrectionProcessor(this));
		enableColoredLabels(PlatformUI.getPreferenceStore().getBoolean(IWorkbenchPreferenceConstants.USE_COLORED_LABELS));

		setInformationControlCreator(getInformationControlCreator());
	}

	private IInformationControlCreator getInformationControlCreator() {
		return new IInformationControlCreator() {
			public IInformationControl createInformationControl(Shell parent) {
				return new DefaultInformationControl(parent, JavaPlugin.getAdditionalInfoAffordanceString());
			}
		};
	}

	public IEditorPart getEditor() {
		return fEditor;
	}
}

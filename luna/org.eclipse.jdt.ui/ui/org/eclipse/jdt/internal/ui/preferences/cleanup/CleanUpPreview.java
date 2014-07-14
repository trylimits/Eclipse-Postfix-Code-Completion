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

package org.eclipse.jdt.internal.ui.preferences.cleanup;

import java.util.Map;

import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.formatter.FormattingContextProperties;
import org.eclipse.jface.text.formatter.IContentFormatter;
import org.eclipse.jface.text.formatter.IContentFormatterExtension;
import org.eclipse.jface.text.formatter.IFormattingContext;

import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.cleanup.ICleanUpConfigurationUI;

import org.eclipse.jdt.internal.ui.IJavaStatusConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.IndentAction;
import org.eclipse.jdt.internal.ui.fix.MultiFixMessages;
import org.eclipse.jdt.internal.ui.preferences.formatter.JavaPreview;
import org.eclipse.jdt.internal.ui.text.java.JavaFormattingContext;


public class CleanUpPreview extends JavaPreview {

	private ICleanUpConfigurationUI fPage;
	private boolean fFormat;
	private boolean fCorrectIndentation;

	public CleanUpPreview(Composite parent, ICleanUpConfigurationUI page) {
		super(JavaCore.getDefaultOptions(), parent);
		fPage= page;
		fFormat= false;
	}

	public void setFormat(boolean enable) {
		fFormat= enable;
	}

	public void setCorrectIndentation(boolean enabled) {
		fCorrectIndentation= enabled;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doFormatPreview() {
		format(fPage.getPreview());
	}

	private void format(String text) {
        if (text == null) {
            fPreviewDocument.set(""); //$NON-NLS-1$
            return;
        }
        fPreviewDocument.set(text);

        if (!fFormat) {
        	if (!fCorrectIndentation)
        		return;

        	fSourceViewer.setRedraw(false);
        	try {
        		IndentAction.indent(fPreviewDocument, null);
        	} catch (BadLocationException e) {
				JavaPlugin.log(e);
			} finally {
        		fSourceViewer.setRedraw(true);
        	}

			return;
        }

		fSourceViewer.setRedraw(false);
		final IFormattingContext context = new JavaFormattingContext();
		try {
			final IContentFormatter formatter =	fViewerConfiguration.getContentFormatter(fSourceViewer);
			if (formatter instanceof IContentFormatterExtension) {
				final IContentFormatterExtension extension = (IContentFormatterExtension) formatter;
				context.setProperty(FormattingContextProperties.CONTEXT_PREFERENCES, JavaCore.getOptions());
				context.setProperty(FormattingContextProperties.CONTEXT_DOCUMENT, Boolean.valueOf(true));
				extension.format(fPreviewDocument, context);
			} else
				formatter.format(fPreviewDocument, new Region(0, fPreviewDocument.getLength()));
		} catch (Exception e) {
			final IStatus status= new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IJavaStatusConstants.INTERNAL_ERROR,
				MultiFixMessages.CleanUpRefactoringWizard_formatterException_errorMessage, e);
			JavaPlugin.log(status);
		} finally {
		    context.dispose();
		    fSourceViewer.setRedraw(true);
		}
	}

    @Override
	public void setWorkingValues(Map<String, String> workingValues) {
    	//Don't change the formatter settings
    }

}

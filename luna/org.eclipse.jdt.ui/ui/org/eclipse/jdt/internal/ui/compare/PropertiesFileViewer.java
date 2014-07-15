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
package org.eclipse.jdt.internal.ui.compare;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.source.SourceViewer;

import org.eclipse.compare.IStreamContentAccessor;

import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.IPropertiesFilePartitions;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.PropertiesFileSourceViewerConfiguration;

/**
 * Properties file viewer.
 *
 * @since 3.1
 */
public class PropertiesFileViewer extends Viewer {

	private SourceViewer fSourceViewer;
	private Object fInput;


	PropertiesFileViewer(Composite parent) {
		fSourceViewer= new SourceViewer(parent, null, SWT.LEFT_TO_RIGHT | SWT.H_SCROLL | SWT.V_SCROLL);
		JavaTextTools tools= JavaCompareUtilities.getJavaTextTools();
		if (tools != null) {
			IPreferenceStore store= JavaPlugin.getDefault().getCombinedPreferenceStore();
			fSourceViewer.configure(new PropertiesFileSourceViewerConfiguration(tools.getColorManager(), store, null, IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING));
		}

		fSourceViewer.setEditable(false);

		String symbolicFontName= PropertiesFileMergeViewer.class.getName();
		Font font= JFaceResources.getFont(symbolicFontName);
		if (font != null)
			fSourceViewer.getTextWidget().setFont(font);
	}

	@Override
	public Control getControl() {
		return fSourceViewer.getControl();
	}

	@Override
	public void setInput(Object input) {
		if (input instanceof IStreamContentAccessor) {
			Document document= new Document(getString(input));
			JavaCompareUtilities.setupPropertiesFileDocument(document);
			fSourceViewer.setDocument(document);
		}
		fInput= input;
	}

	@Override
	public Object getInput() {
		return fInput;
	}

	@Override
	public ISelection getSelection() {
		return null;
	}

	@Override
	public void setSelection(ISelection s, boolean reveal) {
	}

	@Override
	public void refresh() {
	}

	/**
	 * A helper method to retrieve the contents of the given object
	 * if it implements the IStreamContentAccessor interface.
	 */
	private static String getString(Object input) {

		if (input instanceof IStreamContentAccessor) {
			IStreamContentAccessor sca= (IStreamContentAccessor) input;
			try {
				return JavaCompareUtilities.readString(sca);
			} catch (CoreException ex) {
				JavaPlugin.log(ex);
			}
		}
		return ""; //$NON-NLS-1$
	}
}

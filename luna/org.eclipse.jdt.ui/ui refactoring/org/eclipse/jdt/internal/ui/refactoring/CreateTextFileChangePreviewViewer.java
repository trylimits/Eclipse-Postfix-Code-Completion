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
package org.eclipse.jdt.internal.ui.refactoring;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;

import org.eclipse.ui.model.IWorkbenchAdapter;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.ui.refactoring.ChangePreviewViewerInput;
import org.eclipse.ltk.ui.refactoring.IChangePreviewViewer;

import org.eclipse.jdt.internal.corext.refactoring.nls.changes.CreateTextFileChange;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.IPropertiesFilePartitions;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.PropertiesFileDocumentSetupParticipant;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.PropertiesFileSourceViewerConfiguration;
import org.eclipse.jdt.internal.ui.util.ViewerPane;

/**
 * Change preview viewer for <code>CreateTextFileChange</code> objects.
 */
public final class CreateTextFileChangePreviewViewer implements IChangePreviewViewer {

	private static class CreateTextFilePreviewer extends ViewerPane {

		private ImageDescriptor fDescriptor;

		private Image fImage;

		public CreateTextFilePreviewer(Composite parent, int style) {
			super(parent, style);
			addDisposeListener(new DisposeListener() {
				public void widgetDisposed(DisposeEvent e) {
					disposeImage();
				}
			});
		}

		/*package*/ void disposeImage() {
			if (fImage != null) {
				fImage.dispose();
			}
		}

		public void setImageDescriptor(ImageDescriptor imageDescriptor) {
			fDescriptor= imageDescriptor;
		}

		@Override
		public void setText(String text) {
			super.setText(text);
			Image current= null;
			if (fDescriptor != null) {
				current= fImage;
				fImage= fDescriptor.createImage();
			} else {
				current= fImage;
				fImage= null;
			}
			setImage(fImage);
			if (current != null) {
				current.dispose();
			}
		}

	}

	private CreateTextFilePreviewer fPane;

	private SourceViewer fSourceViewer;

	/**
	 * {@inheritDoc}
	 */
	public void createControl(Composite parent) {
		fPane= new CreateTextFilePreviewer(parent, SWT.BORDER | SWT.FLAT);
		Dialog.applyDialogFont(fPane);

		fSourceViewer= new SourceViewer(fPane, null, SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI | SWT.FULL_SELECTION);
		fSourceViewer.setEditable(false);
		fSourceViewer.getControl().setFont(JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT));
		fPane.setContent(fSourceViewer.getControl());
	}

	/**
	 * {@inheritDoc}
	 */
	public Control getControl() {
		return fPane;
	}

	public void refresh() {
		fSourceViewer.refresh();
	}


	/**
	 * {@inheritDoc}
	 */
	public void setInput(ChangePreviewViewerInput input) {
		Change change= input.getChange();
		if (change != null) {
			Object element= change.getModifiedElement();
			if (element instanceof IAdaptable) {
				IAdaptable adaptable= (IAdaptable) element;
				IWorkbenchAdapter workbenchAdapter= (IWorkbenchAdapter) adaptable.getAdapter(IWorkbenchAdapter.class);
				if (workbenchAdapter != null) {
					fPane.setImageDescriptor(workbenchAdapter.getImageDescriptor(element));
				} else {
					fPane.setImageDescriptor(null);
				}
			} else {
				fPane.setImageDescriptor(null);
			}
		}
		if (!(change instanceof CreateTextFileChange)) {
			fSourceViewer.setInput(null);
			fPane.setText(""); //$NON-NLS-1$
			return;
		}
		CreateTextFileChange textFileChange= (CreateTextFileChange) change;
		fPane.setText(textFileChange.getName());
		IDocument document= new Document(textFileChange.getPreview());
		// This is a temporary work around until we get the
		// source viewer registry.
		fSourceViewer.unconfigure();
		String textType= textFileChange.getTextType();
		JavaTextTools textTools= JavaPlugin.getDefault().getJavaTextTools();
		IPreferenceStore store= JavaPlugin.getDefault().getCombinedPreferenceStore();
		if ("java".equals(textType)) { //$NON-NLS-1$
			textTools.setupJavaDocumentPartitioner(document);
			fSourceViewer.configure(new JavaSourceViewerConfiguration(textTools.getColorManager(), store, null, null));
		} else if ("properties".equals(textType)) { //$NON-NLS-1$
			PropertiesFileDocumentSetupParticipant.setupDocument(document);
			fSourceViewer.configure(new PropertiesFileSourceViewerConfiguration(textTools.getColorManager(), store, null, IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING));
		} else {
			fSourceViewer.configure(new SourceViewerConfiguration());
		}
		fSourceViewer.setInput(document);
	}
}

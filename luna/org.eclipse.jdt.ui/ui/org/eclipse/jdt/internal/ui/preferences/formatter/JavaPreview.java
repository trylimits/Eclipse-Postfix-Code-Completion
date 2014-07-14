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
package org.eclipse.jdt.internal.ui.preferences.formatter;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.MarginPainter;
import org.eclipse.jface.text.WhitespaceCharacterPainter;
import org.eclipse.jface.text.source.SourceViewer;

import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;

import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer;
import org.eclipse.jdt.internal.ui.text.SimpleJavaSourceViewerConfiguration;


public abstract class JavaPreview {


	private final class JavaSourcePreviewerUpdater {

	    final IPropertyChangeListener fontListener= new IPropertyChangeListener() {
	        public void propertyChange(PropertyChangeEvent event) {
	            if (event.getProperty().equals(PreferenceConstants.EDITOR_TEXT_FONT)) {
					final Font font= JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT);
					fSourceViewer.getTextWidget().setFont(font);
					if (fMarginPainter != null) {
						fMarginPainter.initialize();
					}
				}
			}
		};

	    final IPropertyChangeListener propertyListener= new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				if (fViewerConfiguration.affectsTextPresentation(event)) {
					fViewerConfiguration.handlePropertyChangeEvent(event);
					fSourceViewer.invalidateTextPresentation();
				}
			}
		};


		public JavaSourcePreviewerUpdater() {

		    JFaceResources.getFontRegistry().addListener(fontListener);
		    fPreferenceStore.addPropertyChangeListener(propertyListener);

			fSourceViewer.getTextWidget().addDisposeListener(new DisposeListener() {
				public void widgetDisposed(DisposeEvent e) {
					JFaceResources.getFontRegistry().removeListener(fontListener);
					fPreferenceStore.removePropertyChangeListener(propertyListener);
				}
			});
		}
	}

	protected final SimpleJavaSourceViewerConfiguration fViewerConfiguration;
	protected final Document fPreviewDocument;
	protected final SourceViewer fSourceViewer;
	protected final IPreferenceStore fPreferenceStore;

	protected final MarginPainter fMarginPainter;

	protected Map<String, String> fWorkingValues;

	private int fTabSize= 0;
	private WhitespaceCharacterPainter fWhitespaceCharacterPainter;


	public JavaPreview(Map<String, String> workingValues, Composite parent) {
		JavaTextTools tools= JavaPlugin.getDefault().getJavaTextTools();
		fPreviewDocument= new Document();
		fWorkingValues= workingValues;
		tools.setupJavaDocumentPartitioner( fPreviewDocument, IJavaPartitions.JAVA_PARTITIONING);

		PreferenceStore prioritizedSettings= new PreferenceStore();
		HashMap<String, String> complianceOptions= new HashMap<String, String>();
		JavaModelUtil.setComplianceOptions(complianceOptions, JavaModelUtil.VERSION_LATEST);
		for (Entry<String, String> complianceOption : complianceOptions.entrySet()) {
			prioritizedSettings.setValue(complianceOption.getKey(), complianceOption.getValue());
		}

		IPreferenceStore[] chain= { prioritizedSettings, JavaPlugin.getDefault().getCombinedPreferenceStore() };
		fPreferenceStore= new ChainedPreferenceStore(chain);
		fSourceViewer= new JavaSourceViewer(parent, null, null, false, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER, fPreferenceStore);
		fSourceViewer.setEditable(false);
		Cursor arrowCursor= fSourceViewer.getTextWidget().getDisplay().getSystemCursor(SWT.CURSOR_ARROW);
		fSourceViewer.getTextWidget().setCursor(arrowCursor);

		// Don't set caret to 'null' as this causes https://bugs.eclipse.org/293263
//		fSourceViewer.getTextWidget().setCaret(null);

		fViewerConfiguration= new SimpleJavaSourceViewerConfiguration(tools.getColorManager(), fPreferenceStore, null, IJavaPartitions.JAVA_PARTITIONING, true);
		fSourceViewer.configure(fViewerConfiguration);
		fSourceViewer.getTextWidget().setFont(JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT));

		fMarginPainter= new MarginPainter(fSourceViewer);
		final RGB rgb= PreferenceConverter.getColor(fPreferenceStore, AbstractDecoratedTextEditorPreferenceConstants.EDITOR_PRINT_MARGIN_COLOR);
		fMarginPainter.setMarginRulerColor(tools.getColorManager().getColor(rgb));
		fSourceViewer.addPainter(fMarginPainter);

		new JavaSourcePreviewerUpdater();
		fSourceViewer.setDocument(fPreviewDocument);
	}

	public Control getControl() {
	    return fSourceViewer.getControl();
	}


	public void update() {
		if (fWorkingValues == null) {
		    fPreviewDocument.set(""); //$NON-NLS-1$
		    return;
		}

		// update the print margin
		final String value= fWorkingValues.get(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT);
		final int lineWidth= getPositiveIntValue(value, 0);
		fMarginPainter.setMarginRulerColumn(lineWidth);

		// update the tab size
		final int tabSize= getPositiveIntValue(fWorkingValues.get(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE), 0);
		if (tabSize != fTabSize) fSourceViewer.getTextWidget().setTabs(tabSize);
		fTabSize= tabSize;

		final StyledText widget= (StyledText)fSourceViewer.getControl();
		final int height= widget.getClientArea().height;
		final int top0= widget.getTopPixel();

		final int totalPixels0= getHeightOfAllLines(widget);
		final int topPixelRange0= totalPixels0 > height ? totalPixels0 - height : 0;

		widget.setRedraw(false);
		doFormatPreview();
		fSourceViewer.setSelection(null);

		final int totalPixels1= getHeightOfAllLines(widget);
		final int topPixelRange1= totalPixels1 > height ? totalPixels1 - height : 0;

		final int top1= topPixelRange0 > 0 ? (int)(topPixelRange1 * top0 / (double)topPixelRange0) : 0;
		widget.setTopPixel(top1);
		widget.setRedraw(true);
	}

	private int getHeightOfAllLines(StyledText styledText) {
		int height= 0;
		int lineCount= styledText.getLineCount();
		for (int i= 0; i < lineCount; i++)
			height= height + styledText.getLineHeight(styledText.getOffsetAtLine(i));
		return height;
	}

	protected abstract void doFormatPreview();


	private static int getPositiveIntValue(String string, int defaultValue) {
	    try {
	        int i= Integer.parseInt(string);
	        if (i >= 0) {
	            return i;
	        }
	    } catch (NumberFormatException e) {
	    }
	    return defaultValue;
	}



	public Map<String, String> getWorkingValues() {
		return fWorkingValues;
	}


	public void setWorkingValues(Map<String, String> workingValues) {
		fWorkingValues= workingValues;
	}

	public void showInvisibleCharacters(boolean enable) {
		if (enable) {
			if (fWhitespaceCharacterPainter == null) {
				fWhitespaceCharacterPainter= new WhitespaceCharacterPainter(fSourceViewer);
				fSourceViewer.addPainter(fWhitespaceCharacterPainter);
			}
		} else {
			fSourceViewer.removePainter(fWhitespaceCharacterPainter);
			fWhitespaceCharacterPainter= null;
		}
	}
}

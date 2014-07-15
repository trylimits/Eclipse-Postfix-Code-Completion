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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.contentmergeviewer.TextMergeViewer;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.IPropertiesFilePartitions;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.PropertiesFilePartitionScanner;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.PropertiesFileSourceViewerConfiguration;

/**
 * Properties file merge viewer.
 *
 * @since 3.1
 */
public class PropertiesFileMergeViewer extends TextMergeViewer {

	private List<SourceViewerConfiguration> fSourceViewerConfigurations;

	private IPropertyChangeListener fPreferenceChangeListener;

	private IPreferenceStore fPreferenceStore;


	/**
	 * Creates a properties file merge viewer under the given parent control.
	 *
	 * @param parent the parent control
	 * @param configuration the configuration object
	 */
	public PropertiesFileMergeViewer(Composite parent, CompareConfiguration configuration) {
		super(parent, SWT.LEFT_TO_RIGHT, configuration);
	}

	/*
	 * @see org.eclipse.compare.contentmergeviewer.TextMergeViewer#configureTextViewer(org.eclipse.jface.text.TextViewer)
	 */
	@Override
	protected void configureTextViewer(TextViewer textViewer) {
		if (!(textViewer instanceof SourceViewer))
			return;

		if (fPreferenceStore == null) {
			fSourceViewerConfigurations= new ArrayList<SourceViewerConfiguration>(3);
			fPreferenceStore= JavaPlugin.getDefault().getCombinedPreferenceStore();
			fPreferenceChangeListener= new IPropertyChangeListener() {
				public void propertyChange(PropertyChangeEvent event) {
					Iterator<SourceViewerConfiguration> iter= fSourceViewerConfigurations.iterator();
					while (iter.hasNext())
						((PropertiesFileSourceViewerConfiguration)iter.next()).handlePropertyChangeEvent(event);
					invalidateTextPresentation();
				}
			};
			fPreferenceStore.addPropertyChangeListener(fPreferenceChangeListener);
		}

		SourceViewerConfiguration sourceViewerConfiguration= new PropertiesFileSourceViewerConfiguration(JavaPlugin.getDefault().getJavaTextTools().getColorManager(), fPreferenceStore, null,
				getDocumentPartitioning());

		fSourceViewerConfigurations.add(sourceViewerConfiguration);
		((SourceViewer)textViewer).configure(sourceViewerConfiguration);
	}

	/*
	 * @see org.eclipse.compare.contentmergeviewer.TextMergeViewer#getDocumentPartitioner()
	 */
	@Override
	protected IDocumentPartitioner getDocumentPartitioner() {
		return new FastPartitioner(new PropertiesFilePartitionScanner(), IPropertiesFilePartitions.PARTITIONS);
	}

	/*
	 * @see org.eclipse.compare.contentmergeviewer.TextMergeViewer#getDocumentPartitioning()
	 * @since 3.3
	 */
	@Override
	protected String getDocumentPartitioning() {
		return IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING;
	}

	/*
	 * @see org.eclipse.compare.contentmergeviewer.ContentMergeViewer#getTitle()
	 */
	@Override
	public String getTitle() {
		return CompareMessages.PropertiesFileMergeViewer_title;
	}

	/*
	 * @see org.eclipse.compare.contentmergeviewer.TextMergeViewer#handleDispose(org.eclipse.swt.events.DisposeEvent)
	 * @since 3.5
	 */
	@Override
	protected void handleDispose(DisposeEvent event) {
		if (fPreferenceStore != null) {
			fPreferenceStore.removePropertyChangeListener(fPreferenceChangeListener);
			fPreferenceStore= null;
			fPreferenceChangeListener= null;
			fSourceViewerConfigurations= null;
		}
		super.handleDispose(event);
	}
}

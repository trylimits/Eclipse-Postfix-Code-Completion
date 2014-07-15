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
package org.eclipse.jdt.internal.ui.propertiesfileeditor;

import org.eclipse.core.filebuffers.IDocumentSetupParticipant;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.rules.FastPartitioner;

/**
 * The document setup participant for a properties file document.
 *
 * @since 3.1
 */
public class PropertiesFileDocumentSetupParticipant  implements IDocumentSetupParticipant {

	/*
	 * @see org.eclipse.core.filebuffers.IDocumentSetupParticipant#setup(org.eclipse.jface.text.IDocument)
	 */
	public void setup(IDocument document) {
		setupDocument(document);
	}

	/**
	 * @param document the document
	 * @see org.eclipse.core.filebuffers.IDocumentSetupParticipant#setup(org.eclipse.jface.text.IDocument)
	 */
	public static void setupDocument(IDocument document) {
		IDocumentPartitioner partitioner= createDocumentPartitioner();
		if (document instanceof IDocumentExtension3) {
			IDocumentExtension3 extension3= (IDocumentExtension3) document;
			extension3.setDocumentPartitioner(IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING, partitioner);
		} else {
			document.setDocumentPartitioner(partitioner);
		}
		partitioner.connect(document);
	}

	/**
	 * Factory method for creating a properties file document specific document
	 * partitioner.
	 *
	 * @return a newly created properties file document partitioner
	 */
	private static IDocumentPartitioner createDocumentPartitioner() {
		return new FastPartitioner(new PropertiesFilePartitionScanner(), IPropertiesFilePartitions.PARTITIONS);
	}
}

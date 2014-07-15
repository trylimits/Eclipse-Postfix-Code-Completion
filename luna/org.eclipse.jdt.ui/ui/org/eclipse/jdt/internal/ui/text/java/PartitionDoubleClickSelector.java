/*******************************************************************************
 * Copyright (c) 2008, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultTextDoubleClickStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;


/**
 * Double click strategy that selects the partition if double click occurs at the partition
 * boundaries.
 * 
 * @since 3.5
 */
public class PartitionDoubleClickSelector extends DefaultTextDoubleClickStrategy {

	private String fPartitioning;

	int fLeftBorder;
	int fRightBorder;
	int fHitDelta;


	/**
	 * Creates a new partition double click selector for the given document partitioning.
	 * 
	 * @param partitioning the document partitioning
	 * @param leftBorder number of characters to ignore from the left border of the partition
	 * @param rightBorder number of characters to ignore from the right border of the partition
	 */
	public PartitionDoubleClickSelector(String partitioning, int leftBorder, int rightBorder) {
		this(partitioning, leftBorder, rightBorder, 1);
	}

	/**
	 * Creates a new partition double click selector for the given document partitioning.
	 * 
	 * @param partitioning the document partitioning
	 * @param leftBorder number of characters to ignore from the left border of the partition
	 * @param rightBorder number of characters to ignore from the right border of the partition
	 * @param hitDeltaOffset character offset delta relative to partition borders used to define the
	 *            trigger character
	 * @since 3.6
	 */
	public PartitionDoubleClickSelector(String partitioning, int leftBorder, int rightBorder, int hitDeltaOffset) {
		fPartitioning= partitioning;
		fLeftBorder= leftBorder;
		fRightBorder= rightBorder;
		fHitDelta= hitDeltaOffset;
	}

	/*
	 * @see org.eclipse.jface.text.DefaultTextDoubleClickStrategy#findExtendedDoubleClickSelection(org.eclipse.jface.text.IDocument, int)
	 * @since 3.5
	 */
	@Override
	protected IRegion findExtendedDoubleClickSelection(IDocument document, int offset) {
		IRegion match= super.findExtendedDoubleClickSelection(document, offset);
		if (match != null)
			return match;

		try {
			ITypedRegion region= TextUtilities.getPartition(document, fPartitioning, offset, true);
			if (offset == region.getOffset() + fHitDelta || offset == region.getOffset() + region.getLength() - fHitDelta) {
				if (fLeftBorder == 0 && fRightBorder == 0)
					return region;
				if (fRightBorder == -1) {
					String delimiter= document.getLineDelimiter(document.getLineOfOffset(region.getOffset() + region.getLength() - 1));
					if (delimiter == null)
						fRightBorder= 0;
					else
						fRightBorder= delimiter.length();
				}
				return new Region(region.getOffset() + fLeftBorder, region.getLength() - fLeftBorder - fRightBorder);
			}
		} catch (BadLocationException e) {
			return null;
		}
		return null;
	}
}

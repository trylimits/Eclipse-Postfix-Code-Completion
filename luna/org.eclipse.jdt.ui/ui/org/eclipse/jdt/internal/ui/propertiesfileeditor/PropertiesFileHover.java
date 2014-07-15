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

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.text.AbstractReusableInformationControlCreator;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPartitioningException;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IInformationControlExtension2;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITypedRegion;

import org.eclipse.ui.editors.text.EditorsUI;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Provides the default text hover info if it exists, else provides the native string as hover info.
 * 
 * @since 3.7
 */
public class PropertiesFileHover implements ITextHover, ITextHoverExtension, ITextHoverExtension2 {

	/**
	 * The offset for which the hover request has been issued.
	 */
	private int fOffset;

	/**
	 * The default text hover.
	 */
	private final ITextHover fTextHover;

	/**
	 * The hover control creator.
	 */
	private HoverControlCreator fHoverControlCreator;

	public PropertiesFileHover(ITextHover textHover) {
		fTextHover= textHover;
	}

	/**
	 * Hover control creator.
	 * 
	 * @since 3.7
	 */
	private static final class HoverControlCreator extends AbstractReusableInformationControlCreator {

		/*
		 * @see org.eclipse.jdt.internal.ui.text.java.hover.AbstractReusableInformationControlCreator#doCreateInformationControl(org.eclipse.swt.widgets.Shell)
		 */
		@Override
		public IInformationControl doCreateInformationControl(Shell parent) {
			return new PropertiesFileHoverControl(parent, EditorsUI.getTooltipAffordanceString());
		}
	}

	/**
	 * The Properties File hover control.
	 * 
	 * @since 3.7
	 */
	static class PropertiesFileHoverControl extends DefaultInformationControl implements IInformationControlExtension2 {

		/**
		 * Creates an Properties File hover control with the given shell as parent.
		 * 
		 * @param parent the parent shell
		 * @param tooltipAffordanceString the text to be used in the status field or
		 *            <code>null</code> to hide the status field
		 */
		public PropertiesFileHoverControl(Shell parent, String tooltipAffordanceString) {
			super(parent, tooltipAffordanceString, null);
		}

		/*
		 * (non-Javadoc)
		 * @see org.eclipse.jface.text.IInformationControlExtension2#setInput(java.lang.Object)
		 */
		public void setInput(Object input) {
			setInformation((String)input);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.text.ITextHoverExtension2#getHoverInfo2(org.eclipse.jface.text.ITextViewer, org.eclipse.jface.text.IRegion)
	 */
	public Object getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
		return getHoverInfo(textViewer, hoverRegion);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.text.ITextHoverExtension#getHoverControlCreator()
	 */
	public IInformationControlCreator getHoverControlCreator() {
		if (fHoverControlCreator == null)
			fHoverControlCreator= new HoverControlCreator();
		return fHoverControlCreator;
	}

	/**
	 * {@inheritDoc}
	 * @deprecated see {@link ITextHover#getHoverInfo(ITextViewer, IRegion)}
	 */
	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		String hoverInfo= fTextHover.getHoverInfo(textViewer, hoverRegion);
		if (hoverInfo != null && hoverInfo.length() > 0) {
			return hoverInfo;
		}

		String unescapedString= null;
		try {
			ITypedRegion partition= null;
			IDocument document= textViewer.getDocument();
			if (document instanceof IDocumentExtension3)
				partition= ((IDocumentExtension3)document).getPartition(IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING, fOffset, false);
			if (partition == null)
				return null;

			String type= partition.getType();
			if (!(type.equals(IPropertiesFilePartitions.PROPERTY_VALUE) || type.equals(IDocument.DEFAULT_CONTENT_TYPE))) {
				return null;
			}
			String escapedString= document.get(partition.getOffset(), partition.getLength());
			if (type.equals(IPropertiesFilePartitions.PROPERTY_VALUE)) {
				escapedString= escapedString.substring(1); //see PropertiesFilePartitionScanner()
			}

			try {
				unescapedString= PropertiesFileEscapes.unescape(escapedString);
			} catch (CoreException e) {
				return e.getStatus().getMessage();
			}
			if (escapedString.equals(unescapedString))
				return null;
		} catch (BadLocationException e) {
			JavaPlugin.log(e);
		} catch (BadPartitioningException e) {
			JavaPlugin.log(e);
		}
		return unescapedString;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.text.ITextHover#getHoverRegion(org.eclipse.jface.text.ITextViewer, int)
	 */
	public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
		fOffset= offset;
		return fTextHover.getHoverRegion(textViewer, offset);
	}
}

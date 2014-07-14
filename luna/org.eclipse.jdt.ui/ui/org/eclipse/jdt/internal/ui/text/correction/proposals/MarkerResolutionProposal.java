/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.correction.proposals;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.resources.IMarker;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.IContextInformation;

import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolution2;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.text.correction.CorrectionMessages;
import org.eclipse.jdt.internal.ui.text.correction.IProposalRelevance;

public class MarkerResolutionProposal implements IJavaCompletionProposal {

	private IMarkerResolution fResolution;
	private IMarker fMarker;

	/**
	 * Constructor for MarkerResolutionProposal.
	 * @param resolution the marker resolution
	 * @param marker the marker
	 */
	public MarkerResolutionProposal(IMarkerResolution resolution, IMarker marker) {
		fResolution= resolution;
		fMarker= marker;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.ICompletionProposal#apply(org.eclipse.jface.text.IDocument)
	 */
	public void apply(IDocument document) {
		fResolution.run(fMarker);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.ICompletionProposal#getAdditionalProposalInfo()
	 */
	public String getAdditionalProposalInfo() {
		if (fResolution instanceof IMarkerResolution2) {
			return ((IMarkerResolution2) fResolution).getDescription();
		}
		if (fResolution instanceof IJavaCompletionProposal) {
			return ((IJavaCompletionProposal) fResolution).getAdditionalProposalInfo();
		}
		try {
			String problemDesc= (String) fMarker.getAttribute(IMarker.MESSAGE);
			return Messages.format(CorrectionMessages.MarkerResolutionProposal_additionaldesc, problemDesc);
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.ICompletionProposal#getContextInformation()
	 */
	public IContextInformation getContextInformation() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.ICompletionProposal#getDisplayString()
	 */
	public String getDisplayString() {
		return fResolution.getLabel();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.ICompletionProposal#getImage()
	 */
	public Image getImage() {
		if (fResolution instanceof IMarkerResolution2) {
			return ((IMarkerResolution2) fResolution).getImage();
		}
		if (fResolution instanceof IJavaCompletionProposal) {
			return ((IJavaCompletionProposal) fResolution).getImage();
		}
		return JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.java.IJavaCompletionProposal#getRelevance()
	 */
	public int getRelevance() {
		if (fResolution instanceof IJavaCompletionProposal) {
			return ((IJavaCompletionProposal) fResolution).getRelevance();
		}
		return IProposalRelevance.MARKER_RESOLUTION;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.contentassist.ICompletionProposal#getSelection(org.eclipse.jface.text.IDocument)
	 */
	public Point getSelection(IDocument document) {
		if (fResolution instanceof IJavaCompletionProposal) {
			return ((IJavaCompletionProposal) fResolution).getSelection(document);
		}
		return null;
	}

}

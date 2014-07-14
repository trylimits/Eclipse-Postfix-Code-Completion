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
package org.eclipse.jdt.internal.ui.text.java.hover;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.IDoubleClickListener;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControlExtension2;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.IAnnotationAccess;
import org.eclipse.jface.text.source.IAnnotationAccessExtension;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationPresentation;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.ImageUtilities;

import org.eclipse.ui.texteditor.AnnotationPreference;
import org.eclipse.ui.texteditor.AnnotationPreferenceLookup;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider.ProblemAnnotation;
import org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation;
import org.eclipse.jdt.internal.ui.javaeditor.JavaMarkerAnnotation;
import org.eclipse.jdt.internal.ui.text.correction.JavaCorrectionProcessor;
import org.eclipse.jdt.internal.ui.text.java.hover.AnnotationExpansionControl.AnnotationHoverInput;


/**
 * @since 3.0
 */
public class JavaExpandHover extends AnnotationExpandHover {

	/** Id of the no breakpoint fake annotation */
	public static final String NO_BREAKPOINT_ANNOTATION= "org.eclipse.jdt.internal.ui.NoBreakpointAnnotation"; //$NON-NLS-1$

	private static class NoBreakpointAnnotation extends Annotation implements IAnnotationPresentation {

		public NoBreakpointAnnotation() {
			super(NO_BREAKPOINT_ANNOTATION, false, JavaHoverMessages.NoBreakpointAnnotation_addBreakpoint);
		}

		/*
		 * @see org.eclipse.jface.text.source.IAnnotationPresentation#paint(org.eclipse.swt.graphics.GC, org.eclipse.swt.widgets.Canvas, org.eclipse.swt.graphics.Rectangle)
		 */
		public void paint(GC gc, Canvas canvas, Rectangle bounds) {
			// draw affordance so the user know she can click here to get a breakpoint
			Image fImage= JavaPluginImages.get(JavaPluginImages.IMG_FIELD_PUBLIC);
			ImageUtilities.drawImage(fImage, gc, canvas, bounds, SWT.CENTER);
		}

		/*
		 * @see org.eclipse.jface.text.source.IAnnotationPresentation#getLayer()
		 */
		public int getLayer() {
			return IAnnotationPresentation.DEFAULT_LAYER;
		}
	}

	private AnnotationPreferenceLookup fLookup= new AnnotationPreferenceLookup();
	private IPreferenceStore fStore= JavaPlugin.getDefault().getCombinedPreferenceStore();

	public JavaExpandHover(CompositeRuler ruler, IAnnotationAccess access, IDoubleClickListener doubleClickListener) {
		super(ruler, access, doubleClickListener);
	}

	/*
	 * @see org.eclipse.ui.internal.texteditor.AnnotationExpandHover#getHoverInfoForLine(org.eclipse.jface.text.source.ISourceViewer, int)
	 */
	@Override
	protected Object getHoverInfoForLine(final ISourceViewer viewer, final int line) {
		final boolean showTemporaryProblems= PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_CORRECTION_INDICATION);
		IAnnotationModel model= viewer.getAnnotationModel();
		IDocument document= viewer.getDocument();

		if (model == null)
			return null;

		List<Annotation> exact= new ArrayList<Annotation>();
		HashMap<Position, Object> messagesAtPosition= new HashMap<Position, Object>();

		Iterator<Annotation> e= model.getAnnotationIterator();
		while (e.hasNext()) {
			Annotation annotation= e.next();

			if (fAnnotationAccess instanceof IAnnotationAccessExtension)
				if (!((IAnnotationAccessExtension)fAnnotationAccess).isPaintable(annotation))
					continue;

			if (annotation instanceof IJavaAnnotation && !isIncluded((IJavaAnnotation)annotation, showTemporaryProblems))
				continue;

			AnnotationPreference pref= fLookup.getAnnotationPreference(annotation);
			if (pref != null) {
				String key= pref.getVerticalRulerPreferenceKey();
				if (key != null && !fStore.getBoolean(key))
					continue;
			}

			Position position= model.getPosition(annotation);
			if (position == null)
				continue;

			if (compareRulerLine(position, document, line) == 1) {

				if (isDuplicateMessage(messagesAtPosition, position, annotation.getText()))
					continue;

				exact.add(annotation);
			}
		}

		sort(exact, model);

		if (exact.size() > 0)
			setLastRulerMouseLocation(viewer, line);

		if (exact.size() > 0) {
			Annotation first= exact.get(0);
			if (!isBreakpointAnnotation(first))
				exact.add(0, new NoBreakpointAnnotation());
		}

		if (exact.size() <= 1)
			return null;

		AnnotationHoverInput input= new AnnotationHoverInput();
		input.fAnnotations= exact.toArray(new Annotation[0]);
		input.fViewer= viewer;
		input.fRulerInfo= fCompositeRuler;
		input.fAnnotationListener= fgListener;
		input.fDoubleClickListener= fDblClickListener;
		input.redoAction= new AnnotationExpansionControl.ICallback() {

			public void run(IInformationControlExtension2 control) {
				control.setInput(getHoverInfoForLine(viewer, line));
			}

		};
		input.model= model;

		return input;
	}

	private boolean isIncluded(IJavaAnnotation annotation, boolean showTemporaryProblems) {

		// XXX: see https://bugs.eclipse.org/bugs/show_bug.cgi?id=138601
		if (annotation instanceof ProblemAnnotation && JavaMarkerAnnotation.TASK_ANNOTATION_TYPE.equals(annotation.getType()))
			return false;

		if (!annotation.isProblem())
			return true;

		if (annotation.isMarkedDeleted() && !annotation.hasOverlay())
			return true;

		if (annotation.hasOverlay() && !annotation.isMarkedDeleted())
			return true;


		if (annotation.hasOverlay())
			return (!isIncluded(annotation.getOverlay(), showTemporaryProblems));

		return showTemporaryProblems && JavaCorrectionProcessor.hasCorrections((Annotation)annotation);
	}

	/*
	 * @see org.eclipse.ui.internal.texteditor.AnnotationExpandHover#getOrder(org.eclipse.jface.text.source.Annotation)
	 */
	@Override
	protected int getOrder(Annotation annotation) {
		if (isBreakpointAnnotation(annotation))
			return 1000;
		else
			return super.getOrder(annotation);
	}

	private boolean isBreakpointAnnotation(Annotation a) {
		// HACK to get breakpoints to show up first
		return a.getType().equals("org.eclipse.debug.core.breakpoint"); //$NON-NLS-1$
	}
}

/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.javaeditor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;

import org.eclipse.core.resources.IEncodedStorage;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFileState;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.core.filebuffers.IAnnotationModelFactory;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultLineTracker;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ILineTracker;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.quickassist.IQuickFixableAnnotation;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.jface.text.source.AnnotationModelEvent;
import org.eclipse.jface.text.source.IAnnotationAccessExtension;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelListener;
import org.eclipse.jface.text.source.IAnnotationModelListenerExtension;
import org.eclipse.jface.text.source.IAnnotationPresentation;
import org.eclipse.jface.text.source.ImageUtilities;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IStorageEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE.SharedImages;

import org.eclipse.ui.texteditor.AbstractMarkerAnnotationModel;
import org.eclipse.ui.texteditor.AnnotationPreference;
import org.eclipse.ui.texteditor.AnnotationPreferenceLookup;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.MarkerAnnotation;
import org.eclipse.ui.texteditor.ResourceMarkerAnnotationModel;

import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.editors.text.ForwardingDocumentProvider;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.IJavaPartitions;

import org.eclipse.jdt.internal.ui.IJavaStatusConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.javaeditor.saveparticipant.IPostSaveListener;
import org.eclipse.jdt.internal.ui.javaeditor.saveparticipant.SaveParticipantRegistry;
import org.eclipse.jdt.internal.ui.text.correction.JavaCorrectionProcessor;
import org.eclipse.jdt.internal.ui.text.java.IProblemRequestorExtension;
import org.eclipse.jdt.internal.ui.text.spelling.JavaSpellingReconcileStrategy;


public class CompilationUnitDocumentProvider extends TextFileDocumentProvider implements ICompilationUnitDocumentProvider, IAnnotationModelFactory {

	/**
	 * Bundle of all required informations to allow working copy management.
	 */
	static protected class CompilationUnitInfo extends FileInfo {
		public ICompilationUnit fCopy;
	}

	/**
	 * Annotation representing an <code>IProblem</code>.
	 */
	static public class ProblemAnnotation extends Annotation implements IJavaAnnotation, IAnnotationPresentation, IQuickFixableAnnotation {

		public static final String SPELLING_ANNOTATION_TYPE= "org.eclipse.ui.workbench.texteditor.spelling"; //$NON-NLS-1$

		//XXX: To be fully correct these constants should be non-static
		/**
		 * The layer in which task problem annotations are located.
		 */
		private static final int TASK_LAYER;
		/**
		 * The layer in which info problem annotations are located.
		 */
		private static final int INFO_LAYER;
		/**
		 * The layer in which warning problem annotations representing are located.
		 */
		private static final int WARNING_LAYER;
		/**
		 * The layer in which error problem annotations representing are located.
		 */
		private static final int ERROR_LAYER;

		static {
			AnnotationPreferenceLookup lookup= EditorsUI.getAnnotationPreferenceLookup();
			TASK_LAYER= computeLayer("org.eclipse.ui.workbench.texteditor.task", lookup); //$NON-NLS-1$
			INFO_LAYER= computeLayer("org.eclipse.jdt.ui.info", lookup); //$NON-NLS-1$
			WARNING_LAYER= computeLayer("org.eclipse.jdt.ui.warning", lookup); //$NON-NLS-1$
			ERROR_LAYER= computeLayer("org.eclipse.jdt.ui.error", lookup); //$NON-NLS-1$
		}

		private static int computeLayer(String annotationType, AnnotationPreferenceLookup lookup) {
			Annotation annotation= new Annotation(annotationType, false, null);
			AnnotationPreference preference= lookup.getAnnotationPreference(annotation);
			if (preference != null)
				return preference.getPresentationLayer() + 1;
			else
				return IAnnotationAccessExtension.DEFAULT_LAYER + 1;
		}

		private static Image fgQuickFixImage;
		private static Image fgQuickFixErrorImage;
		private static Image fgTaskImage;
		private static Image fgInfoImage;
		private static Image fgWarningImage;
		private static Image fgErrorImage;
		private static boolean fgImagesInitialized= false;

		private ICompilationUnit fCompilationUnit;
		private List<IJavaAnnotation> fOverlaids;
		private IProblem fProblem;
		private Image fImage;
		private boolean fImageInitialized= false;
		private int fLayer= IAnnotationAccessExtension.DEFAULT_LAYER;
		private boolean fIsQuickFixable;
		private boolean fIsQuickFixableStateSet= false;


		public ProblemAnnotation(IProblem problem, ICompilationUnit cu) {

			fProblem= problem;
			fCompilationUnit= cu;

			if (JavaSpellingReconcileStrategy.SPELLING_PROBLEM_ID == fProblem.getID()) {
				setType(SPELLING_ANNOTATION_TYPE);
				fLayer= WARNING_LAYER;
			} else if (IProblem.Task == fProblem.getID()) {
				setType(JavaMarkerAnnotation.TASK_ANNOTATION_TYPE);
				fLayer= TASK_LAYER;
			} else if (fProblem.isWarning()) {
				setType(JavaMarkerAnnotation.WARNING_ANNOTATION_TYPE);
				fLayer= WARNING_LAYER;
			} else if (fProblem.isError()) {
				setType(JavaMarkerAnnotation.ERROR_ANNOTATION_TYPE);
				fLayer= ERROR_LAYER;
			} else {
				setType(JavaMarkerAnnotation.INFO_ANNOTATION_TYPE);
				fLayer= INFO_LAYER;
			}
		}

		/*
		 * @see org.eclipse.jface.text.source.IAnnotationPresentation#getLayer()
		 */
		public int getLayer() {
			return fLayer;
		}

		private void initializeImage() {
			// http://bugs.eclipse.org/bugs/show_bug.cgi?id=18936
			if (!fImageInitialized) {
				initializeImages();
				if (!isQuickFixableStateSet())
					setQuickFixable(isProblem() && indicateQuixFixableProblems() && JavaCorrectionProcessor.hasCorrections(this)); // no light bulb for tasks
				if (isQuickFixable()) {
					if (JavaMarkerAnnotation.ERROR_ANNOTATION_TYPE.equals(getType()))
						fImage= fgQuickFixErrorImage;
					else
						fImage= fgQuickFixImage;
				} else {
					String type= getType();
					if (JavaMarkerAnnotation.TASK_ANNOTATION_TYPE.equals(type))
						fImage= fgTaskImage;
					else if (JavaMarkerAnnotation.INFO_ANNOTATION_TYPE.equals(type))
						fImage= fgInfoImage;
					else if (JavaMarkerAnnotation.WARNING_ANNOTATION_TYPE.equals(type))
						fImage= fgWarningImage;
					else if (JavaMarkerAnnotation.ERROR_ANNOTATION_TYPE.equals(type))
						fImage= fgErrorImage;
				}
				fImageInitialized= true;
			}
		}

		private void initializeImages() {
			if (fgImagesInitialized)
				return;

			fgQuickFixImage= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_FIXABLE_PROBLEM);
			fgQuickFixErrorImage= JavaPluginImages.get(JavaPluginImages.IMG_OBJS_FIXABLE_ERROR);

			ISharedImages sharedImages= PlatformUI.getWorkbench().getSharedImages();
			fgTaskImage= sharedImages.getImage(SharedImages.IMG_OBJS_TASK_TSK);
			fgInfoImage= sharedImages.getImage(ISharedImages.IMG_OBJS_INFO_TSK);
			fgWarningImage= sharedImages.getImage(ISharedImages.IMG_OBJS_WARN_TSK);
			fgErrorImage= sharedImages.getImage(ISharedImages.IMG_OBJS_ERROR_TSK);

			fgImagesInitialized= true;
		}

		private boolean indicateQuixFixableProblems() {
			return PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_CORRECTION_INDICATION);
		}

		/*
		 * @see Annotation#paint
		 */
		public void paint(GC gc, Canvas canvas, Rectangle r) {
			initializeImage();
			if (fImage != null)
				ImageUtilities.drawImage(fImage, gc, canvas, r, SWT.CENTER, SWT.TOP);
		}

		/*
		 * @see IJavaAnnotation#getMessage()
		 */
		@Override
		public String getText() {
			return fProblem.getMessage();
		}

		/*
		 * @see IJavaAnnotation#getArguments()
		 */
		public String[] getArguments() {
			return isProblem() ? fProblem.getArguments() : null;
		}

		/*
		 * @see IJavaAnnotation#getId()
		 */
		public int getId() {
			return fProblem.getID();
		}

		/*
		 * @see IJavaAnnotation#isProblem()
		 */
		public boolean isProblem() {
			String type= getType();
			return  JavaMarkerAnnotation.WARNING_ANNOTATION_TYPE.equals(type)  ||
						JavaMarkerAnnotation.ERROR_ANNOTATION_TYPE.equals(type) ||
						SPELLING_ANNOTATION_TYPE.equals(type);
		}

		/*
		 * @see IJavaAnnotation#hasOverlay()
		 */
		public boolean hasOverlay() {
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation#getOverlay()
		 */
		public IJavaAnnotation getOverlay() {
			return null;
		}

		/*
		 * @see IJavaAnnotation#addOverlaid(IJavaAnnotation)
		 */
		public void addOverlaid(IJavaAnnotation annotation) {
			if (fOverlaids == null)
				fOverlaids= new ArrayList<IJavaAnnotation>(1);
			fOverlaids.add(annotation);
		}

		/*
		 * @see IJavaAnnotation#removeOverlaid(IJavaAnnotation)
		 */
		public void removeOverlaid(IJavaAnnotation annotation) {
			if (fOverlaids != null) {
				fOverlaids.remove(annotation);
				if (fOverlaids.size() == 0)
					fOverlaids= null;
			}
		}

		/*
		 * @see IJavaAnnotation#getOverlaidIterator()
		 */
		public Iterator<IJavaAnnotation> getOverlaidIterator() {
			if (fOverlaids != null)
				return fOverlaids.iterator();
			return null;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation#getCompilationUnit()
		 */
		public ICompilationUnit getCompilationUnit() {
			return fCompilationUnit;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation#getMarkerType()
		 */
		public String getMarkerType() {
			if (fProblem instanceof CategorizedProblem)
				return ((CategorizedProblem) fProblem).getMarkerType();
			return null;
		}

		/*
		 * @see org.eclipse.jface.text.quickassist.IQuickFixableAnnotation#setQuickFixable(boolean)
		 * @since 3.2
		 */
		public void setQuickFixable(boolean state) {
			fIsQuickFixable= state;
			fIsQuickFixableStateSet= true;
		}

		/*
		 * @see org.eclipse.jface.text.quickassist.IQuickFixableAnnotation#isQuickFixableStateSet()
		 * @since 3.2
		 */
		public boolean isQuickFixableStateSet() {
			return fIsQuickFixableStateSet;
		}

		/*
		 * @see org.eclipse.jface.text.quickassist.IQuickFixableAnnotation#isQuickFixable()
		 * @since 3.2
		 */
		public boolean isQuickFixable() {
			Assert.isTrue(isQuickFixableStateSet());
			return fIsQuickFixable;
		}
	}

	/**
	 * Internal structure for mapping positions to some value.
	 * The reason for this specific structure is that positions can
	 * change over time. Thus a lookup is based on value and not
	 * on hash value.
	 */
	protected static class ReverseMap {

		static class Entry {
			Position fPosition;
			Object fValue;
		}

		private List<Entry> fList= new ArrayList<Entry>(2);
		private int fAnchor= 0;

		public ReverseMap() {
		}

		public Object get(Position position) {

			Entry entry;

			// behind anchor
			int length= fList.size();
			for (int i= fAnchor; i < length; i++) {
				entry= fList.get(i);
				if (entry.fPosition.equals(position)) {
					fAnchor= i;
					return entry.fValue;
				}
			}

			// before anchor
			for (int i= 0; i < fAnchor; i++) {
				entry= fList.get(i);
				if (entry.fPosition.equals(position)) {
					fAnchor= i;
					return entry.fValue;
				}
			}

			return null;
		}

		private int getIndex(Position position) {
			Entry entry;
			int length= fList.size();
			for (int i= 0; i < length; i++) {
				entry= fList.get(i);
				if (entry.fPosition.equals(position))
					return i;
			}
			return -1;
		}

		public void put(Position position,  Object value) {
			int index= getIndex(position);
			if (index == -1) {
				Entry entry= new Entry();
				entry.fPosition= position;
				entry.fValue= value;
				fList.add(entry);
			} else {
				Entry entry= fList.get(index);
				entry.fValue= value;
			}
		}

		public void remove(Position position) {
			int index= getIndex(position);
			if (index > -1)
				fList.remove(index);
		}

		public void clear() {
			fList.clear();
		}
	}

	/**
	 * Annotation model dealing with java marker annotations and temporary problems.
	 * Also acts as problem requester for its compilation unit. Initially inactive. Must explicitly be
	 * activated.
	 */
	protected static class CompilationUnitAnnotationModel extends ResourceMarkerAnnotationModel implements IProblemRequestor, IProblemRequestorExtension {

		private static class ProblemRequestorState {
			boolean fInsideReportingSequence= false;
			List<IProblem> fReportedProblems;
		}

		private ThreadLocal<ProblemRequestorState> fProblemRequestorState= new ThreadLocal<ProblemRequestorState>();
		private int fStateCount= 0;

		private ICompilationUnit fCompilationUnit;
		private final List<ProblemAnnotation> fGeneratedAnnotations= new ArrayList<ProblemAnnotation>();
		private IProgressMonitor fProgressMonitor;
		private boolean fIsActive= false;
		private boolean fIsHandlingTemporaryProblems;

		private ReverseMap fReverseMap= new ReverseMap();
		private List<JavaMarkerAnnotation> fPreviouslyOverlaid= null;
		private List<JavaMarkerAnnotation> fCurrentlyOverlaid= new ArrayList<JavaMarkerAnnotation>();
		private Thread fActiveThread;


		public CompilationUnitAnnotationModel(IResource resource) {
			super(resource);
		}

		public void setCompilationUnit(ICompilationUnit unit)  {
			fCompilationUnit= unit;
		}

		@Override
		protected MarkerAnnotation createMarkerAnnotation(IMarker marker) {
			if (JavaMarkerAnnotation.isJavaAnnotation(marker))
				return new JavaMarkerAnnotation(marker);
			return super.createMarkerAnnotation(marker);
		}

		/*
		 * @see org.eclipse.jface.text.source.AnnotationModel#createAnnotationModelEvent()
		 */
		@Override
		protected AnnotationModelEvent createAnnotationModelEvent() {
			return new CompilationUnitAnnotationModelEvent(this, getResource());
		}

		protected Position createPositionFromProblem(IProblem problem) {
			int start= problem.getSourceStart();
			int end= problem.getSourceEnd();

			if (start == -1 && end == -1)
				return new Position(0);

			if (start == -1)
				return new Position(end);

			if (end == -1)
				return new Position(start);

			int length= end - start + 1;
			if (length < 0)
				return null;

			return new Position(start, length);
		}

		/*
		 * @see IProblemRequestor#beginReporting()
		 */
		public void beginReporting() {
			ProblemRequestorState state= fProblemRequestorState.get();
			if (state == null)
				internalBeginReporting(false);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.text.java.IProblemRequestorExtension#beginReportingSequence()
		 */
		public void beginReportingSequence() {
			ProblemRequestorState state= fProblemRequestorState.get();
			if (state == null)
				internalBeginReporting(true);
		}

		/**
		 * Sets up the infrastructure necessary for problem reporting.
		 *
		 * @param insideReportingSequence <code>true</code> if this method
		 *            call is issued from inside a reporting sequence
		 */
		private void internalBeginReporting(boolean insideReportingSequence) {
			if (fCompilationUnit != null && fCompilationUnit.getJavaProject().isOnClasspath(fCompilationUnit)) {
				ProblemRequestorState state= new ProblemRequestorState();
				state.fInsideReportingSequence= insideReportingSequence;
				state.fReportedProblems= new ArrayList<IProblem>();
				synchronized (getLockObject()) {
					fProblemRequestorState.set(state);
					++fStateCount;
				}
			}
		}

		/*
		 * @see IProblemRequestor#acceptProblem(IProblem)
		 */
		public void acceptProblem(IProblem problem) {
			if (fIsHandlingTemporaryProblems || problem.getID() == JavaSpellingReconcileStrategy.SPELLING_PROBLEM_ID) {
				ProblemRequestorState state= fProblemRequestorState.get();
				if (state != null)
					state.fReportedProblems.add(problem);
			}
		}

		/*
		 * @see IProblemRequestor#endReporting()
		 */
		public void endReporting() {
			ProblemRequestorState state= fProblemRequestorState.get();
			if (state != null && !state.fInsideReportingSequence)
				internalEndReporting(state);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.text.java.IProblemRequestorExtension#endReportingSequence()
		 */
		public void endReportingSequence() {
			ProblemRequestorState state= fProblemRequestorState.get();
			if (state != null && state.fInsideReportingSequence)
				internalEndReporting(state);
		}

		private void internalEndReporting(ProblemRequestorState state) {
			int stateCount= 0;
			synchronized(getLockObject()) {
				-- fStateCount;
				stateCount= fStateCount;
				fProblemRequestorState.set(null);
			}

			if (stateCount == 0)
				reportProblems(state.fReportedProblems);
		}

		/**
		 * Signals the end of problem reporting.
		 *
		 * @param reportedProblems the problems to report
		 */
		private void reportProblems(List<IProblem> reportedProblems) {
			if (fProgressMonitor != null && fProgressMonitor.isCanceled())
				return;

			boolean temporaryProblemsChanged= false;

			synchronized (getLockObject()) {

				boolean isCanceled= false;

				fPreviouslyOverlaid= fCurrentlyOverlaid;
				fCurrentlyOverlaid= new ArrayList<JavaMarkerAnnotation>();

				if (fGeneratedAnnotations.size() > 0) {
					temporaryProblemsChanged= true;
					removeAnnotations(fGeneratedAnnotations, false, true);
					fGeneratedAnnotations.clear();
				}

				if (reportedProblems != null && reportedProblems.size() > 0) {

					Iterator<IProblem> e= reportedProblems.iterator();
					while (e.hasNext()) {

						if (fProgressMonitor != null && fProgressMonitor.isCanceled()) {
							isCanceled= true;
							break;
						}

						IProblem problem= e.next();
						Position position= createPositionFromProblem(problem);
						if (position != null) {

							try {
								ProblemAnnotation annotation= new ProblemAnnotation(problem, fCompilationUnit);
								overlayMarkers(position, annotation);
								addAnnotation(annotation, position, false);
								fGeneratedAnnotations.add(annotation);

								temporaryProblemsChanged= true;
							} catch (BadLocationException x) {
								// ignore invalid position
							}
						}
					}
				}

				removeMarkerOverlays(isCanceled);
				fPreviouslyOverlaid= null;
			}

			if (temporaryProblemsChanged)
				fireModelChanged();
		}

		private void removeMarkerOverlays(boolean isCanceled) {
			if (isCanceled) {
				fCurrentlyOverlaid.addAll(fPreviouslyOverlaid);
			} else if (fPreviouslyOverlaid != null) {
				Iterator<JavaMarkerAnnotation> e= fPreviouslyOverlaid.iterator();
				while (e.hasNext()) {
					JavaMarkerAnnotation annotation= e.next();
					annotation.setOverlay(null);
				}
			}
		}

		/**
		 * Overlays value with problem annotation.
		 * 
		 * @param value the value
		 * @param problemAnnotation the problem annotation
		 */
		private void setOverlay(Object value, ProblemAnnotation problemAnnotation) {
			if (value instanceof  JavaMarkerAnnotation) {
				JavaMarkerAnnotation annotation= (JavaMarkerAnnotation) value;
				if (annotation.isProblem()) {
					annotation.setOverlay(problemAnnotation);
					fPreviouslyOverlaid.remove(annotation);
					fCurrentlyOverlaid.add(annotation);
				}
			} else {
			}
		}

		private void  overlayMarkers(Position position, ProblemAnnotation problemAnnotation) {
			Object value= getAnnotations(position);
			if (value instanceof List) {
				List<?> list= (List<?>) value;
				for (Iterator<?> e = list.iterator(); e.hasNext();)
					setOverlay(e.next(), problemAnnotation);
			} else {
				setOverlay(value, problemAnnotation);
			}
		}

		/**
		 * Tells this annotation model to collect temporary problems from now on.
		 */
		private void startCollectingProblems() {
			fGeneratedAnnotations.clear();
		}

		/**
		 * Tells this annotation model to no longer collect temporary problems.
		 */
		private void stopCollectingProblems() {
			removeAnnotations(fGeneratedAnnotations, true, true);
			fGeneratedAnnotations.clear();
		}

		/*
		 * @see IProblemRequestor#isActive()
		 */
		public synchronized boolean isActive() {
			return fIsActive && fActiveThread == Thread.currentThread();
		}

		/*
		 * @see IProblemRequestorExtension#setProgressMonitor(IProgressMonitor)
		 */
		public void setProgressMonitor(IProgressMonitor monitor) {
			fProgressMonitor= monitor;
		}

		/*
		 * @see IProblemRequestorExtension#setIsActive(boolean)
		 */
		public synchronized void setIsActive(boolean isActive) {
			Assert.isLegal(!isActive || Display.getCurrent() == null); // must not be enabled from UI threads
			fIsActive= isActive;
			if (fIsActive)
				fActiveThread= Thread.currentThread();
			else
				fActiveThread= null;
		}

		/*
		 * @see IProblemRequestorExtension#setIsHandlingTemporaryProblems(boolean)
		 * @since 3.1
		 */
		public void setIsHandlingTemporaryProblems(boolean enable) {
			if (fIsHandlingTemporaryProblems != enable) {
				fIsHandlingTemporaryProblems= enable;
				if (fIsHandlingTemporaryProblems)
					startCollectingProblems();
				else
					stopCollectingProblems();
			}

		}

		private Object getAnnotations(Position position) {
			synchronized (getLockObject()) {
				return fReverseMap.get(position);
			}
		}

		/*
		 * @see AnnotationModel#addAnnotation(Annotation, Position, boolean)
		 */
		@Override
		protected void addAnnotation(Annotation annotation, Position position, boolean fireModelChanged) throws BadLocationException {
			super.addAnnotation(annotation, position, fireModelChanged);

			synchronized (getLockObject()) {
				Object cached= fReverseMap.get(position);
				if (cached == null)
					fReverseMap.put(position, annotation);
				else if (cached instanceof List) {
					@SuppressWarnings("unchecked")
					List<Object> list= (List<Object>) cached;
					list.add(annotation);
				} else if (cached instanceof Annotation) {
					List<Object> list= new ArrayList<Object>(2);
					list.add(cached);
					list.add(annotation);
					fReverseMap.put(position, list);
				}
			}
		}

		/*
		 * @see AnnotationModel#removeAllAnnotations(boolean)
		 */
		@Override
		protected void removeAllAnnotations(boolean fireModelChanged) {
			super.removeAllAnnotations(fireModelChanged);
			synchronized (getLockObject()) {
				fReverseMap.clear();
			}
		}

		/*
		 * @see AnnotationModel#removeAnnotation(Annotation, boolean)
		 */
		@Override
		protected void removeAnnotation(Annotation annotation, boolean fireModelChanged) {
			Position position= getPosition(annotation);
			synchronized (getLockObject()) {
				Object cached= fReverseMap.get(position);
				if (cached instanceof List) {
					@SuppressWarnings("unchecked")
					List<Object> list= (List<Object>) cached;
					list.remove(annotation);
					if (list.size() == 1) {
						fReverseMap.put(position, list.get(0));
						list.clear();
					}
				} else if (cached instanceof Annotation) {
					fReverseMap.remove(position);
				}
			}
			super.removeAnnotation(annotation, fireModelChanged);
		}
	}


	protected static class GlobalAnnotationModelListener implements IAnnotationModelListener, IAnnotationModelListenerExtension {

		private ListenerList fListenerList;

		public GlobalAnnotationModelListener() {
			fListenerList= new ListenerList(ListenerList.IDENTITY);
		}

		/**
		 * @see IAnnotationModelListener#modelChanged(IAnnotationModel)
		 */
		public void modelChanged(IAnnotationModel model) {
			Object[] listeners= fListenerList.getListeners();
			for (int i= 0; i < listeners.length; i++) {
				((IAnnotationModelListener) listeners[i]).modelChanged(model);
			}
		}

		/**
		 * @see IAnnotationModelListenerExtension#modelChanged(AnnotationModelEvent)
		 */
		public void modelChanged(AnnotationModelEvent event) {
			Object[] listeners= fListenerList.getListeners();
			for (int i= 0; i < listeners.length; i++) {
				Object curr= listeners[i];
				if (curr instanceof IAnnotationModelListenerExtension) {
					((IAnnotationModelListenerExtension) curr).modelChanged(event);
				}
			}
		}

		public void addListener(IAnnotationModelListener listener) {
			fListenerList.add(listener);
		}

		public void removeListener(IAnnotationModelListener listener) {
			fListenerList.remove(listener);
		}
	}



	/** Preference key for temporary problems */
	private final static String HANDLE_TEMPORARY_PROBLEMS= PreferenceConstants.EDITOR_EVALUTE_TEMPORARY_PROBLEMS;


	/** Indicates whether the save has been initialized by this provider */
	private boolean fIsAboutToSave= false;
	/** The save policy used by this provider */
	private ISavePolicy fSavePolicy;
	/** Internal property changed listener */
	private IPropertyChangeListener fPropertyListener;
	/** Annotation model listener added to all created CU annotation models */
	private GlobalAnnotationModelListener fGlobalAnnotationModelListener;
	/**
	 * Element information of all connected elements with a fake CU but no file info.
	 * @since 3.2
	 */
	private final Map<Object, CompilationUnitInfo> fFakeCUMapForMissingInfo= new HashMap<Object, CompilationUnitInfo>();


	/**
	 * Constructor
	 */
	public CompilationUnitDocumentProvider() {

		IDocumentProvider provider= new TextFileDocumentProvider();
		provider= new ForwardingDocumentProvider(IJavaPartitions.JAVA_PARTITIONING, new JavaDocumentSetupParticipant(), provider);
		setParentDocumentProvider(provider);

		fGlobalAnnotationModelListener= new GlobalAnnotationModelListener();
		fPropertyListener= new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				if (HANDLE_TEMPORARY_PROBLEMS.equals(event.getProperty()))
					enableHandlingTemporaryProblems();
			}
		};
		JavaPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(fPropertyListener);
	}

	/**
	 * Creates a compilation unit from the given file.
	 *
	 * @param file the file from which to create the compilation unit
	 * @return the fake compilation unit
	 */
	protected ICompilationUnit createCompilationUnit(IFile file) {
		Object element= JavaCore.create(file);
		if (element instanceof ICompilationUnit)
			return (ICompilationUnit) element;
		return null;
	}

	/*
	 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider#createEmptyFileInfo()
	 */
	@Override
	protected FileInfo createEmptyFileInfo() {
		return new CompilationUnitInfo();
	}

	/*
	 * @see org.eclipse.core.filebuffers.IAnnotationModelFactory#createAnnotationModel(org.eclipse.core.runtime.IPath)
	 * @since 3.4
	 */
	public IAnnotationModel createAnnotationModel(IPath path) {
		IResource file= ResourcesPlugin.getWorkspace().getRoot().findMember(path);
		if (file instanceof IFile)
			return new CompilationUnitAnnotationModel(file);
		return new AnnotationModel();
	}

	/*
	 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider#createFileInfo(java.lang.Object)
	 */
	@Override
	protected FileInfo createFileInfo(Object element) throws CoreException {
		ICompilationUnit original= null;
		if (element instanceof IFileEditorInput) {
			IFileEditorInput input= (IFileEditorInput) element;
			original= createCompilationUnit(input.getFile());
			if (original == null)
				return null;
		}

		FileInfo info= super.createFileInfo(element);
		if (!(info instanceof CompilationUnitInfo))
			return null;

		if (original == null)
			original= createFakeCompiltationUnit(element, false);
		if (original == null)
			return null;

		CompilationUnitInfo cuInfo= (CompilationUnitInfo) info;
		setUpSynchronization(cuInfo);

		IProblemRequestor requestor= cuInfo.fModel instanceof IProblemRequestor ? (IProblemRequestor) cuInfo.fModel : null;
		if (requestor instanceof IProblemRequestorExtension) {
			IProblemRequestorExtension extension= (IProblemRequestorExtension) requestor;
			extension.setIsActive(false);
			extension.setIsHandlingTemporaryProblems(isHandlingTemporaryProblems());
		}

		IResource resource= original.getResource();
		if (JavaModelUtil.isPrimary(original) && (resource == null || resource.exists()))
			original.becomeWorkingCopy(requestor, getProgressMonitor());
		cuInfo.fCopy= original;

		if (cuInfo.fModel instanceof CompilationUnitAnnotationModel)   {
			CompilationUnitAnnotationModel model= (CompilationUnitAnnotationModel) cuInfo.fModel;
			model.setCompilationUnit(cuInfo.fCopy);
		}

		if (cuInfo.fModel != null)
			cuInfo.fModel.addAnnotationModelListener(fGlobalAnnotationModelListener);

		return cuInfo;
	}

	/**
	 * Creates a fake compilation unit.
	 *
	 * @param element the element
	 * @param setContents tells whether to read and set the contents to the new CU
	 * @return the fake compilation unit
	 * @since 3.2
	 */
	private ICompilationUnit createFakeCompiltationUnit(Object element, boolean setContents) {
		if (element instanceof IStorageEditorInput)
			return createFakeCompiltationUnit((IStorageEditorInput)element, setContents);
		else if (element instanceof IURIEditorInput)
			return createFakeCompiltationUnit((IURIEditorInput)element);
		return null;
	}

	/**
	 * Creates a fake compilation unit.
	 *
	 * @param editorInput the storage editor input
	 * @param setContents tells whether to read and set the contents to the new CU
	 * @return the fake compilation unit
	 * @since 3.2
	 */
	private ICompilationUnit createFakeCompiltationUnit(IStorageEditorInput editorInput, boolean setContents) {
		try {
			final IStorage storage= editorInput.getStorage();
			final IPath storagePath= storage.getFullPath();
			if (storage.getName() == null || storagePath == null)
				return null;

			final IPath documentPath;
			if (storage instanceof IFileState)
				documentPath= storagePath.append(Long.toString(((IFileState)storage).getModificationTime()));
			else if (isFileRevisionEditorInput(editorInput))
				documentPath= storagePath.append(Long.toString(System.currentTimeMillis()));
			else
				documentPath= storagePath;

			WorkingCopyOwner woc= new WorkingCopyOwner() {
				/*
				 * @see org.eclipse.jdt.core.WorkingCopyOwner#createBuffer(org.eclipse.jdt.core.ICompilationUnit)
				 * @since 3.2
				 */
				@Override
				public IBuffer createBuffer(ICompilationUnit workingCopy) {
					return new DocumentAdapter(workingCopy, documentPath);
				}
			};

			IClasspathEntry[] cpEntries= null;
			IJavaProject jp= findJavaProject(storagePath);
			if (jp != null)
				cpEntries= jp.getResolvedClasspath(true);

			if (cpEntries == null || cpEntries.length == 0)
				cpEntries= new IClasspathEntry[] { JavaRuntime.getDefaultJREContainerEntry() };

			final ICompilationUnit cu= woc.newWorkingCopy(storage.getName(), cpEntries, getProgressMonitor());
			if (setContents) {
				int READER_CHUNK_SIZE= 2048;
				int BUFFER_SIZE= 8 * READER_CHUNK_SIZE;

				String charsetName= null;
				if (storage instanceof IEncodedStorage)
					charsetName= ((IEncodedStorage)storage).getCharset();
				if (charsetName == null)
					charsetName= getDefaultEncoding();

				Reader in= null;
				InputStream contents= storage.getContents();
				try {
					in= new BufferedReader(new InputStreamReader(contents, charsetName));
					StringBuffer buffer= new StringBuffer(BUFFER_SIZE);
					char[] readBuffer= new char[READER_CHUNK_SIZE];
					int n;
					n= in.read(readBuffer);
					while (n > 0) {
						buffer.append(readBuffer, 0, n);
						n= in.read(readBuffer);
					}
					cu.getBuffer().setContents(buffer.toString());
				} catch (IOException e) {
					JavaPlugin.log(e);
					return null;
				} finally {
					try {
						if (in != null)
							in.close();
						else
							contents.close();
					} catch (IOException x) {
					}
				}
			}

			if (!isModifiable(editorInput))
				JavaModelUtil.reconcile(cu);

			return cu;
		} catch (CoreException ex) {
			JavaPlugin.log(ex.getStatus());
			return null;
		}
	}

	/**
	 * Tests whether the given editor input is an instance of
	 * <code>org.eclipse.team.internal.ui.history.FileRevisionEditorInput</code>.
	 * <p>
	 * XXX: Workaround for https://bugs.eclipse.org/307756, see comment 2 on how a better solution
	 * could look like.
	 * </p>
	 * 
	 * @param editorInput the editor input to test
	 * @return <code>true</code> if it is an instance of
	 *         <code>org.eclipse.team.internal.ui.history.FileRevisionEditorInput</code>
	 * @since 3.6
	 */
	private static boolean isFileRevisionEditorInput(IEditorInput editorInput) {
		try {
			return Class.forName("org.eclipse.team.internal.ui.history.FileRevisionEditorInput").isInstance(editorInput); //$NON-NLS-1$
		} catch (ClassNotFoundException ex) {
			return false;
		}
	}

	/**
	 * Creates a fake compilation unit.
	 *
	 * @param editorInput the URI editor input
	 * @return the fake compilation unit
	 * @since 3.3
	 */
	private ICompilationUnit createFakeCompiltationUnit(IURIEditorInput editorInput) {
		try {
			final URI uri= editorInput.getURI();
			final IFileStore fileStore= EFS.getStore(uri);
			final IPath path= URIUtil.toPath(uri);
			String fileStoreName= fileStore.getName();
			if (fileStoreName == null || path == null)
				return null;

			WorkingCopyOwner woc= new WorkingCopyOwner() {
				/*
				 * @see org.eclipse.jdt.core.WorkingCopyOwner#createBuffer(org.eclipse.jdt.core.ICompilationUnit)
				 * @since 3.2
				 */
				@Override
				public IBuffer createBuffer(ICompilationUnit workingCopy) {
					return new DocumentAdapter(workingCopy, fileStore, path);
				}
			};

			IClasspathEntry[] cpEntries= null;
			IJavaProject jp= findJavaProject(path);
			if (jp != null)
				cpEntries= jp.getResolvedClasspath(true);

			if (cpEntries == null || cpEntries.length == 0)
				cpEntries= new IClasspathEntry[] { JavaRuntime.getDefaultJREContainerEntry() };

			final ICompilationUnit cu= woc.newWorkingCopy(fileStoreName, cpEntries, getProgressMonitor());

			if (!isModifiable(editorInput))
				JavaModelUtil.reconcile(cu);

			return cu;
		} catch (CoreException ex) {
			return null;
		}
	}

	/**
	 * Fuzzy search for Java project in the workspace that matches
	 * the given path.
	 *
	 * @param path the path to match
	 * @return the matching Java project or <code>null</code>
	 * @since 3.2
	 */
	private IJavaProject findJavaProject(IPath path) {
		if (path == null)
			return null;

		String[] pathSegments= path.segments();
		IJavaModel model= JavaCore.create(JavaPlugin.getWorkspace().getRoot());
		IJavaProject[] projects;
		try {
			projects= model.getJavaProjects();
		} catch (JavaModelException e) {
			return null; // ignore - use default JRE
		}
		for (int i= 0; i < projects.length; i++) {
			IPath projectPath= projects[i].getProject().getFullPath();
			String projectSegment= projectPath.segments()[0];
			for (int j= 0; j < pathSegments.length; j++)
				if (projectSegment.equals(pathSegments[j]))
					return projects[i];
		}
		return null;
	}

    /*
	 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider#disposeFileInfo(java.lang.Object, org.eclipse.ui.editors.text.TextFileDocumentProvider.FileInfo)
	 */
	@Override
	protected void disposeFileInfo(Object element, FileInfo info) {
		if (info instanceof CompilationUnitInfo) {
			CompilationUnitInfo cuInfo= (CompilationUnitInfo) info;

			try  {
				cuInfo.fCopy.discardWorkingCopy();
			} catch (JavaModelException x)  {
				handleCoreException(x, x.getMessage());
			}

			if (cuInfo.fModel != null)
				cuInfo.fModel.removeAnnotationModelListener(fGlobalAnnotationModelListener);
		}
		super.disposeFileInfo(element, info);
	}

	/*
	 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider#connect(java.lang.Object)
	 * @since 3.2
	 */
	@Override
	public void connect(Object element) throws CoreException {
		super.connect(element);
		if (getFileInfo(element) != null)
			return;

		CompilationUnitInfo info= fFakeCUMapForMissingInfo.get(element);
		if (info == null) {
			ICompilationUnit cu= createFakeCompiltationUnit(element, true);
			if (cu == null)
				return;
			info= new CompilationUnitInfo();
			info.fCopy= cu;
			info.fElement= element;
			info.fModel= new AnnotationModel();
			fFakeCUMapForMissingInfo.put(element, info);
		}
		info.fCount++;
	}

	/*
	 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider#getAnnotationModel(java.lang.Object)
	 * @since 3.2
	 */
	@Override
	public IAnnotationModel getAnnotationModel(Object element) {
		IAnnotationModel model= super.getAnnotationModel(element);
		if (model != null)
			return model;

		FileInfo info= fFakeCUMapForMissingInfo.get(element);
		if (info != null) {
			if (info.fModel != null)
				return info.fModel;
			if (info.fTextFileBuffer != null)
				return info.fTextFileBuffer.getAnnotationModel();
		}

		return null;
	}

	/*
	 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider#disconnect(java.lang.Object)
	 * @since 3.2
	 */
	@Override
	public void disconnect(Object element) {
		CompilationUnitInfo info= fFakeCUMapForMissingInfo.get(element);
		if (info != null)  {
			if (info.fCount == 1) {
				fFakeCUMapForMissingInfo.remove(element);
				info.fModel= null;
				// Destroy and unregister fake working copy
				try {
					info.fCopy.discardWorkingCopy();
				} catch (JavaModelException ex) {
					handleCoreException(ex, ex.getMessage());
				}
			} else
				info.fCount--;
		}
		super.disconnect(element);
	}


	/**
	 * Creates and returns a new sub-progress monitor for the
	 * given parent monitor.
	 *
	 * @param monitor the parent progress monitor
	 * @param ticks the number of work ticks allocated from the parent monitor
	 * @return the new sub-progress monitor
	 */
	private IProgressMonitor getSubProgressMonitor(IProgressMonitor monitor, int ticks) {
		if (monitor != null)
			return new SubProgressMonitor(monitor, ticks, SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK);

		return new NullProgressMonitor();
	}

	protected void commitWorkingCopy(IProgressMonitor monitor, Object element, final CompilationUnitInfo info, boolean overwrite) throws CoreException {

		if (monitor == null)
			monitor= new NullProgressMonitor();

		monitor.beginTask("", 100); //$NON-NLS-1$

		try {
			IDocument document= info.fTextFileBuffer.getDocument();
			IResource resource= info.fCopy.getResource();

			Assert.isTrue(resource instanceof IFile);

			boolean isSynchronized= resource.isSynchronized(IResource.DEPTH_ZERO);

			/* https://bugs.eclipse.org/bugs/show_bug.cgi?id=98327
			 * Make sure file gets save in commit() if the underlying file has been deleted */
			if (!isSynchronized && isDeleted(element))
				info.fTextFileBuffer.setDirty(true);

			if (!resource.exists()) {
				// underlying resource has been deleted, just recreate file, ignore the rest
				createFileFromDocument(monitor, (IFile) resource, document);
				return;
			}

			if (fSavePolicy != null)
				fSavePolicy.preSave(info.fCopy);

			IProgressMonitor subMonitor= null;
 			try {
 				fIsAboutToSave= true;

				IPostSaveListener[] listeners= JavaPlugin.getDefault().getSaveParticipantRegistry().getEnabledPostSaveListeners(info.fCopy.getJavaProject().getProject());

				CoreException changedRegionException= null;
				boolean needsChangedRegions= false;
				try {
					if (listeners.length > 0)
						needsChangedRegions= SaveParticipantRegistry.isChangedRegionsRequired(info.fCopy);
				} catch (CoreException ex) {
					changedRegionException= ex;
				}

				IRegion[] changedRegions= null;
				if (needsChangedRegions) {
					try {
						changedRegions= EditorUtility.calculateChangedLineRegions(info.fTextFileBuffer, getSubProgressMonitor(monitor, 20));
					} catch (CoreException ex) {
						changedRegionException= ex;
					} finally {
						subMonitor= getSubProgressMonitor(monitor, 50);
					}
				} else
					subMonitor= getSubProgressMonitor(monitor, listeners.length > 0 ? 70 : 100);

				info.fCopy.commitWorkingCopy(overwrite || isSynchronized, subMonitor);
				if (listeners.length > 0)
					notifyPostSaveListeners(info, changedRegions, listeners, getSubProgressMonitor(monitor, 30));

				if (changedRegionException != null) {
					throw changedRegionException;
				}
			} catch (JavaModelException x) {
				// inform about the failure
				fireElementStateChangeFailed(element);
				if (IJavaModelStatusConstants.UPDATE_CONFLICT == x.getStatus().getCode())
					// convert JavaModelException to CoreException
					throw new CoreException(new Status(IStatus.WARNING, JavaUI.ID_PLUGIN, IResourceStatus.OUT_OF_SYNC_LOCAL, JavaEditorMessages.CompilationUnitDocumentProvider_error_outOfSync, null));
				throw x;
			} catch (CoreException x) {
				// inform about the failure
				fireElementStateChangeFailed(element);
				throw x;
			} catch (RuntimeException x) {
				// inform about the failure
				fireElementStateChangeFailed(element);
				throw x;
			} finally {
				fIsAboutToSave= false;
				if (subMonitor != null)
					subMonitor.done();
			}

			// If here, the dirty state of the editor will change to "not dirty".
			// Thus, the state changing flag will be reset.
			if (info.fModel instanceof AbstractMarkerAnnotationModel) {
				AbstractMarkerAnnotationModel model= (AbstractMarkerAnnotationModel) info.fModel;
				model.updateMarkers(document);
			}

			if (fSavePolicy != null) {
				ICompilationUnit unit= fSavePolicy.postSave(info.fCopy);
				if (unit != null && info.fModel instanceof AbstractMarkerAnnotationModel) {
					IResource r= unit.getResource();
					IMarker[] markers= r.findMarkers(IMarker.MARKER, true, IResource.DEPTH_ZERO);
					if (markers != null && markers.length > 0) {
						AbstractMarkerAnnotationModel model= (AbstractMarkerAnnotationModel) info.fModel;
						for (int i= 0; i < markers.length; i++)
							model.updateMarker(document, markers[i], null);
					}
				}
			}
		} finally {
			monitor.done();
		}
	}

	/*
	 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider#createSaveOperation(java.lang.Object, org.eclipse.jface.text.IDocument, boolean)
	 */
	@Override
	protected DocumentProviderOperation createSaveOperation(final Object element, final IDocument document, final boolean overwrite) throws CoreException {
		final FileInfo info= getFileInfo(element);
		if (info instanceof CompilationUnitInfo) {

			// Delegate handling of non-primary CUs
			ICompilationUnit cu= ((CompilationUnitInfo)info).fCopy;
			if (cu != null && !JavaModelUtil.isPrimary(cu))
				return super.createSaveOperation(element, document, overwrite);

			if (info.fTextFileBuffer.getDocument() != document) {
				// the info exists, but not for the given document
				// -> saveAs was executed with a target that is already open
				// in another editor
				// see https://bugs.eclipse.org/bugs/show_bug.cgi?id=85519
				Status status= new Status(IStatus.WARNING, EditorsUI.PLUGIN_ID, IStatus.ERROR, JavaEditorMessages.CompilationUnitDocumentProvider_saveAsTargetOpenInEditor, null);
				throw new CoreException(status);
			}

			return new DocumentProviderOperation() {
				/*
				 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider.DocumentProviderOperation#execute(org.eclipse.core.runtime.IProgressMonitor)
				 */
				@Override
				protected void execute(IProgressMonitor monitor) throws CoreException {
					commitWorkingCopy(monitor, element, (CompilationUnitInfo) info, overwrite);
				}
				/*
				 * @see org.eclipse.ui.editors.text.TextFileDocumentProvider.DocumentProviderOperation#getSchedulingRule()
				 */
				@Override
				public ISchedulingRule getSchedulingRule() {
					if (info.fElement instanceof IFileEditorInput) {
						IFile file= ((IFileEditorInput) info.fElement).getFile();
						return computeSchedulingRule(file);
					} else
						return null;
				}
			};
		}

		return null;
	}

	/**
	 * Returns the preference whether handling temporary problems is enabled.
	 *
	 * @return <code>true</code> if temporary problems are handled
	 */
	protected boolean isHandlingTemporaryProblems() {
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		return store.getBoolean(HANDLE_TEMPORARY_PROBLEMS);
	}

		/**
		 * Switches the state of problem acceptance according to the value in the preference store.
		 */
		protected void enableHandlingTemporaryProblems() {
			boolean enable= isHandlingTemporaryProblems();
			for (Iterator<FileInfo> iter= getFileInfosIterator(); iter.hasNext();) {
				FileInfo info= iter.next();
				if (info.fModel instanceof IProblemRequestorExtension) {
					IProblemRequestorExtension  extension= (IProblemRequestorExtension) info.fModel;
					extension.setIsHandlingTemporaryProblems(enable);
				}
			}
		}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider#setSavePolicy(org.eclipse.jdt.internal.ui.javaeditor.ISavePolicy)
	 */
	public void setSavePolicy(ISavePolicy savePolicy) {
		fSavePolicy= savePolicy;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider#addGlobalAnnotationModelListener(org.eclipse.jface.text.source.IAnnotationModelListener)
	 */
	public void addGlobalAnnotationModelListener(IAnnotationModelListener listener) {
		fGlobalAnnotationModelListener.addListener(listener);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider#removeGlobalAnnotationModelListener(org.eclipse.jface.text.source.IAnnotationModelListener)
	 */
	public void removeGlobalAnnotationModelListener(IAnnotationModelListener listener) {
		fGlobalAnnotationModelListener.removeListener(listener);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider#getWorkingCopy(java.lang.Object)
	 */
	public ICompilationUnit getWorkingCopy(Object element) {
		FileInfo fileInfo= getFileInfo(element);
		if (fileInfo instanceof CompilationUnitInfo) {
			CompilationUnitInfo info= (CompilationUnitInfo)fileInfo;
			return info.fCopy;
		}
		CompilationUnitInfo cuInfo= fFakeCUMapForMissingInfo.get(element);
		if (cuInfo != null)
			return cuInfo.fCopy;

		return null;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider#shutdown()
	 */
	public void shutdown() {
		JavaPlugin.getDefault().getPreferenceStore().removePropertyChangeListener(fPropertyListener);
		Iterator<?> e= getConnectedElementsIterator();
		while (e.hasNext())
			disconnect(e.next());
		fFakeCUMapForMissingInfo.clear();
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider#saveDocumentContent(org.eclipse.core.runtime.IProgressMonitor, java.lang.Object, org.eclipse.jface.text.IDocument, boolean)
	 */
	public void saveDocumentContent(IProgressMonitor monitor, Object element, IDocument document, boolean overwrite) throws CoreException {
		if (!fIsAboutToSave)
			return;
		super.saveDocument(monitor, element, document, overwrite);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.ICompilationUnitDocumentProvider#createLineTracker(java.lang.Object)
	 */
	public ILineTracker createLineTracker(Object element) {
		return new DefaultLineTracker();
	}

	/**
	 * Notify post save listeners.
	 * <p>
	 * <strong>Note:</strong> Post save listeners are not allowed to save the file and they must not
	 * assumed to be called in the UI thread i.e. if they open a dialog they must ensure it ends up
	 * in the UI thread.
	 * </p>
	 * 
	 * @param info compilation unit info
	 * @param changedRegions the array with the changed regions
	 * @param listeners the listeners to notify
	 * @param monitor the progress monitor
	 * @throws CoreException if something goes wrong
	 * @see IPostSaveListener
	 * @since 3.3
	 */
	protected void notifyPostSaveListeners(final CompilationUnitInfo info, final IRegion[] changedRegions, IPostSaveListener[] listeners, final IProgressMonitor monitor) throws CoreException {
		final ICompilationUnit unit= info.fCopy;
		final IBuffer buffer= unit.getBuffer();

		String message= JavaEditorMessages.CompilationUnitDocumentProvider_error_saveParticipantProblem;
		final MultiStatus errorStatus= new MultiStatus(JavaUI.ID_PLUGIN, IJavaStatusConstants.EDITOR_POST_SAVE_NOTIFICATION, message, null);

		monitor.beginTask(JavaEditorMessages.CompilationUnitDocumentProvider_progressNotifyingSaveParticipants, listeners.length * 5);
		try {
			for (int i= 0; i < listeners.length; i++) {
				final IPostSaveListener listener= listeners[i];
				final String participantName= listener.getName();
				SafeRunner.run(new ISafeRunnable() {
					public void run() {
						try {
							long stamp= unit.getResource().getModificationStamp();

							listener.saved(unit, changedRegions, getSubProgressMonitor(monitor, 4));

							if (stamp != unit.getResource().getModificationStamp()) {
								String msg= Messages.format(JavaEditorMessages.CompilationUnitDocumentProvider_error_saveParticipantSavedFile, participantName);
								errorStatus.add(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IJavaStatusConstants.EDITOR_POST_SAVE_NOTIFICATION, msg, null));
							}

							if (buffer.hasUnsavedChanges())
								buffer.save(getSubProgressMonitor(monitor, 1), true);

						} catch (CoreException ex) {
							handleException(ex);
						} finally {
							monitor.worked(1);
						}
					}

					public void handleException(Throwable ex) {
						String msg= Messages.format("The save participant ''{0}'' caused an exception: {1}", new String[] { listener.getId(), ex.toString()}); //$NON-NLS-1$
						JavaPlugin.log(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IJavaStatusConstants.EDITOR_POST_SAVE_NOTIFICATION, msg, ex));

						msg= Messages.format(JavaEditorMessages.CompilationUnitDocumentProvider_error_saveParticipantFailed, new String[] { participantName, ex.toString()});
						errorStatus.add(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IJavaStatusConstants.EDITOR_POST_SAVE_NOTIFICATION, msg, null));

						// Revert the changes
						if (buffer.hasUnsavedChanges()) {
							try {
								info.fTextFileBuffer.revert(getSubProgressMonitor(monitor, 1));
							} catch (CoreException e) {
								msg= Messages.format("Error on revert after failure of save participant ''{0}''.", participantName);  //$NON-NLS-1$
								IStatus status= new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IJavaStatusConstants.EDITOR_POST_SAVE_NOTIFICATION, msg, ex);
								JavaPlugin.getDefault().getLog().log(status);
							}

							if (info.fModel instanceof AbstractMarkerAnnotationModel) {
								AbstractMarkerAnnotationModel markerModel= (AbstractMarkerAnnotationModel)info.fModel;
								markerModel.resetMarkers();
							}
						}

						// XXX: Work in progress 'Save As' case
//						else if (buffer.hasUnsavedChanges()) {
//							try {
//								buffer.save(getSubProgressMonitor(monitor, 1), true);
//							} catch (JavaModelException e) {
//								message= Messages.format("Error reverting changes after failure of save participant ''{0}''.", participantName); //$NON-NLS-1$
//								IStatus status= new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.OK, message, ex);
//								JavaPlugin.getDefault().getLog().log(status);
//							}
//						}
					}
				});
			}
		} finally {
			monitor.done();
			if (!errorStatus.isOK())
				throw new CoreException(errorStatus);
		}
	}
}

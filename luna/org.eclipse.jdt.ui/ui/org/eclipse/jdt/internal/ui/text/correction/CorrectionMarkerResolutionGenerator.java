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
package org.eclipse.jdt.internal.ui.text.correction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.swt.graphics.Image;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator2;
import org.eclipse.ui.views.markers.WorkbenchMarkerResolution;

import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.MarkerUtilities;

import org.eclipse.jdt.core.CorrectionEngine;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;

import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring.MultiFixTarget;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.text.java.CompletionProposalComparator;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.fix.IMultiFix;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.JavaMarkerAnnotation;
import org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposal;


public class CorrectionMarkerResolutionGenerator implements IMarkerResolutionGenerator2 {

	public static class CorrectionMarkerResolution extends WorkbenchMarkerResolution {

		private static final IMarker[] NO_MARKERS= new IMarker[0];

		private ICompilationUnit fCompilationUnit;
		private int fOffset;
		private int fLength;
		private IJavaCompletionProposal fProposal;
		private final IMarker fMarker;

		/**
		 * Constructor for CorrectionMarkerResolution.
		 * @param cu the compilation unit
		 * @param offset the offset
		 * @param length the length
		 * @param proposal the proposal for the given marker
		 * @param marker the marker to fix
		 */
		public CorrectionMarkerResolution(ICompilationUnit cu, int offset, int length, IJavaCompletionProposal proposal, IMarker marker) {
			fCompilationUnit= cu;
			fOffset= offset;
			fLength= length;
			fProposal= proposal;
			fMarker= marker;
		}

		/* (non-Javadoc)
		 * @see IMarkerResolution#getLabel()
		 */
		public String getLabel() {
			return fProposal.getDisplayString();
		}

		/* (non-Javadoc)
		 * @see IMarkerResolution#run(IMarker)
		 */
		public void run(IMarker marker) {
			try {
				IEditorPart part= EditorUtility.isOpenInEditor(fCompilationUnit);
				if (part == null) {
					part= JavaUI.openInEditor(fCompilationUnit, true, false);
					if (part instanceof ITextEditor) {
						((ITextEditor) part).selectAndReveal(fOffset, fLength);
					}
				}
				if (part != null) {
					IEditorInput input= part.getEditorInput();
					IDocument doc= JavaPlugin.getDefault().getCompilationUnitDocumentProvider().getDocument(input);
					fProposal.apply(doc);
				}
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		}


		/**
		 * {@inheritDoc}
		 */
		@Override
		public void run(IMarker[] markers, IProgressMonitor monitor) {
			if (markers.length == 1) {
				run(markers[0]);
				return;
			}

			if (!(fProposal instanceof FixCorrectionProposal))
				return;

			if (monitor == null)
				monitor= new NullProgressMonitor();

			try {
				MultiFixTarget[] problems= getCleanUpTargets(markers);

				((FixCorrectionProposal)fProposal).resolve(problems, monitor);

				IEditorPart part= EditorUtility.isOpenInEditor(fCompilationUnit);
				if (part instanceof ITextEditor) {
					((ITextEditor) part).selectAndReveal(fOffset, fLength);
					part.setFocus();
				}
			} catch (CoreException e) {
				JavaPlugin.log(e);
			} finally {
				monitor.done();
			}
		}

		private MultiFixTarget[] getCleanUpTargets(IMarker[] markers) {
			Hashtable<ICompilationUnit, List<IProblemLocation>> problemLocations= new Hashtable<ICompilationUnit, List<IProblemLocation>>();
			for (int i= 0; i < markers.length; i++) {
				IMarker marker= markers[i];
				ICompilationUnit cu= getCompilationUnit(marker);

				if (cu != null) {
					IEditorInput input= EditorUtility.getEditorInput(cu);
					IProblemLocation location= findProblemLocation(input, marker);
					if (location != null) {
						List<IProblemLocation> l= problemLocations.get(cu.getPrimary());
						if (l == null) {
							l= new ArrayList<IProblemLocation>();
							problemLocations.put(cu.getPrimary(), l);
						}
						l.add(location);
					}
				}
			}

			MultiFixTarget[] result= new MultiFixTarget[problemLocations.size()];
			int i= 0;
			for (Iterator<Map.Entry<ICompilationUnit, List<IProblemLocation>>> iterator= problemLocations.entrySet().iterator(); iterator.hasNext();) {
				Map.Entry<ICompilationUnit, List<IProblemLocation>> entry= iterator.next();
				ICompilationUnit cu= entry.getKey();
				List<IProblemLocation> locations= entry.getValue();
				result[i]= new MultiFixTarget(cu, locations.toArray(new IProblemLocation[locations.size()]));
				i++;
			}

			return result;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.ui.IMarkerResolution2#getDescription()
		 */
		public String getDescription() {
			return fProposal.getAdditionalProposalInfo();
		}

		/* (non-Javadoc)
		 * @see org.eclipse.ui.IMarkerResolution2#getImage()
		 */
		public Image getImage() {
			return fProposal.getImage();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public IMarker[] findOtherMarkers(IMarker[] markers) {
			if (!(fProposal instanceof FixCorrectionProposal))
				return NO_MARKERS;

			FixCorrectionProposal fix= (FixCorrectionProposal)fProposal;
			final ICleanUp cleanUp= fix.getCleanUp();
			if (!(cleanUp instanceof IMultiFix))
				return NO_MARKERS;

			IMultiFix multiFix= (IMultiFix) cleanUp;

			final Hashtable<IFile, List<IMarker>> fileMarkerTable= getMarkersForFiles(markers);
			if (fileMarkerTable.isEmpty())
				return NO_MARKERS;

			final List<IMarker> result= new ArrayList<IMarker>();

			for (Iterator<Entry<IFile, List<IMarker>>> iterator= fileMarkerTable.entrySet().iterator(); iterator.hasNext();) {
				Map.Entry<IFile, List<IMarker>> entry= iterator.next();
				IFile file= entry.getKey();
				List<IMarker> fileMarkers= entry.getValue();

				IJavaElement element= JavaCore.create(file);
				if (element instanceof ICompilationUnit) {
					ICompilationUnit unit= (ICompilationUnit) element;

					for (int i= 0, size= fileMarkers.size(); i < size; i++) {
						IMarker marker= fileMarkers.get(i);
						IProblemLocation problem= createFromMarker(marker, unit);
						if (problem != null && multiFix.canFix(unit, problem)) {
							result.add(marker);
						}
					}
				}
			}

			if (result.size() == 0)
				return NO_MARKERS;

			return result.toArray(new IMarker[result.size()]);
		}

		/**
		 * Returns the markers with the same type as fMarker.getType for each IFile.
		 * @param markers the markers
		 * @return mapping files to markers
		 */
		private Hashtable<IFile, List<IMarker>> getMarkersForFiles(IMarker[] markers) {
			final Hashtable<IFile, List<IMarker>> result= new Hashtable<IFile, List<IMarker>>();

			String markerType;
			try {
				markerType= fMarker.getType();
			} catch (CoreException e1) {
				JavaPlugin.log(e1);
				return result;
			}

			for (int i= 0; i < markers.length; i++) {
				IMarker marker= markers[i];
				if (!marker.equals(fMarker)) {
					String currMarkerType= null;
					try {
						currMarkerType= marker.getType();
					} catch (CoreException e1) {
						JavaPlugin.log(e1);
					}

					if (currMarkerType != null && currMarkerType.equals(markerType)) {
						IResource res= marker.getResource();
						if (res instanceof IFile && res.isAccessible()) {
							List<IMarker> markerList= result.get(res);
							if (markerList == null) {
								markerList= new ArrayList<IMarker>();
								result.put((IFile) res, markerList);
							}
							markerList.add(marker);
						}
					}
				}
			}
			return result;
		}
	}

	private static final IMarkerResolution[] NO_RESOLUTIONS= new IMarkerResolution[0];


	/**
	 * Constructor for CorrectionMarkerResolutionGenerator.
	 */
	public CorrectionMarkerResolutionGenerator() {
		super();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IMarkerResolutionGenerator2#hasResolutions(org.eclipse.core.resources.IMarker)
	 */
	public boolean hasResolutions(IMarker marker) {
		return internalHasResolutions(marker);
	}

	/* (non-Javadoc)
	 * @see IMarkerResolutionGenerator#getResolutions(IMarker)
	 */
	public IMarkerResolution[] getResolutions(IMarker marker) {
		return internalGetResolutions(marker);
	}

	private static boolean internalHasResolutions(IMarker marker) {
		int id= marker.getAttribute(IJavaModelMarker.ID, -1);
		ICompilationUnit cu= getCompilationUnit(marker);
		return cu != null && JavaCorrectionProcessor.hasCorrections(cu, id, MarkerUtilities.getMarkerType(marker));
	}

	private static IMarkerResolution[] internalGetResolutions(IMarker marker) {
		if (!internalHasResolutions(marker)) {
			return NO_RESOLUTIONS;
		}

		ICompilationUnit cu= getCompilationUnit(marker);
		if (cu != null) {
			IEditorInput input= EditorUtility.getEditorInput(cu);
			if (input != null) {
				IProblemLocation location= findProblemLocation(input, marker);
				if (location != null) {

					IInvocationContext context= new AssistContext(cu,  location.getOffset(), location.getLength());
					if (!hasProblem (context.getASTRoot().getProblems(), location))
						return NO_RESOLUTIONS;

					ArrayList<IJavaCompletionProposal> proposals= new ArrayList<IJavaCompletionProposal>();
					JavaCorrectionProcessor.collectCorrections(context, new IProblemLocation[] { location }, proposals);
					Collections.sort(proposals, new CompletionProposalComparator());

					int nProposals= proposals.size();
					IMarkerResolution[] resolutions= new IMarkerResolution[nProposals];
					for (int i= 0; i < nProposals; i++) {
						resolutions[i]= new CorrectionMarkerResolution(context.getCompilationUnit(), location.getOffset(), location.getLength(), proposals.get(i), marker);
					}
					return resolutions;
				}
			}
		}
		return NO_RESOLUTIONS;
	}

	private static boolean hasProblem(IProblem[] problems, IProblemLocation location) {
		for (int i= 0; i < problems.length; i++) {
			IProblem problem= problems[i];
			if (problem.getID() == location.getProblemId() && problem.getSourceStart() == location.getOffset())
				return true;
		}
		return false;
	}

	private static ICompilationUnit getCompilationUnit(IMarker marker) {
		IResource res= marker.getResource();
		if (res instanceof IFile && res.isAccessible()) {
			IJavaElement element= JavaCore.create((IFile) res);
			if (element instanceof ICompilationUnit)
				return (ICompilationUnit) element;
		}
		return null;
	}

	private static IProblemLocation findProblemLocation(IEditorInput input, IMarker marker) {
		IAnnotationModel model= JavaPlugin.getDefault().getCompilationUnitDocumentProvider().getAnnotationModel(input);
		if (model != null) { // open in editor
			Iterator<Annotation> iter= model.getAnnotationIterator();
			while (iter.hasNext()) {
				Annotation curr= iter.next();
				if (curr instanceof JavaMarkerAnnotation) {
					JavaMarkerAnnotation annot= (JavaMarkerAnnotation) curr;
					if (marker.equals(annot.getMarker())) {
						Position pos= model.getPosition(annot);
						if (pos != null) {
							return new ProblemLocation(pos.getOffset(), pos.getLength(), annot);
						}
					}
				}
			}
		} else { // not open in editor
			ICompilationUnit cu= getCompilationUnit(marker);
			return createFromMarker(marker, cu);
		}
		return null;
	}

	private static IProblemLocation createFromMarker(IMarker marker, ICompilationUnit cu) {
		try {
			int id= marker.getAttribute(IJavaModelMarker.ID, -1);
			int start= marker.getAttribute(IMarker.CHAR_START, -1);
			int end= marker.getAttribute(IMarker.CHAR_END, -1);
			int severity= marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
			String[] arguments= CorrectionEngine.getProblemArguments(marker);
			String markerType= marker.getType();
			if (cu != null && id != -1 && start != -1 && end != -1 && arguments != null) {
				boolean isError= (severity == IMarker.SEVERITY_ERROR);
				return new ProblemLocation(start, end - start, id, arguments, isError, markerType);
			}
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
		return null;
	}


}

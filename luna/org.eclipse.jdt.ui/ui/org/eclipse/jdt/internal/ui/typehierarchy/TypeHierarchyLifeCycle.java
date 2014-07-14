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
package org.eclipse.jdt.internal.ui.typehierarchy;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.widgets.Display;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;

import org.eclipse.ui.progress.IWorkbenchSiteProgressService;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IRegion;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeHierarchyChangedListener;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Manages a type hierarchy, to keep it refreshed, and to allow it to be shared.
 */
public class TypeHierarchyLifeCycle implements ITypeHierarchyChangedListener, IElementChangedListener {

	private boolean fHierarchyRefreshNeeded;
	private ITypeHierarchy fHierarchy;
	private IJavaElement[] fInputElements;
	private boolean fIsSuperTypesOnly;

	private List<ITypeHierarchyLifeCycleListener> fChangeListeners;

	/**
	 * The type hierarchy view part.
	 *
	 * @since 3.6
	 */
	private TypeHierarchyViewPart fTypeHierarchyViewPart;

	/**
	 * The job that runs in the background to refresh the type hierarchy.
	 *
	 * @since 3.6
	 */
	private Job fRefreshHierarchyJob;

	/**
	 * Indicates whether the refresh job was canceled explicitly.
	 * 
	 * @since 3.6
	 */
	private boolean fRefreshJobCanceledExplicitly= true;

	/**
	 * Creates the type hierarchy life cycle.
	 *
	 * @param part the type hierarchy view part
	 * @since 3.6
	 */
	public TypeHierarchyLifeCycle(TypeHierarchyViewPart part) {
		this(false);
		fTypeHierarchyViewPart= part;
		fRefreshHierarchyJob= null;
	}

	public TypeHierarchyLifeCycle(boolean isSuperTypesOnly) {
		fHierarchy= null;
		fInputElements= null;
		fIsSuperTypesOnly= isSuperTypesOnly;
		fChangeListeners= new ArrayList<ITypeHierarchyLifeCycleListener>(2);
	}

	public ITypeHierarchy getHierarchy() {
		return fHierarchy;
	}

	/**
	 * Returns the array of input elements.
	 * 
	 * @return the input elements, or <code>null</code>
	 */
	public IJavaElement[] getInputElements() {
		return fInputElements;
	}


	public void freeHierarchy() {
		if (fHierarchy != null) {
			fHierarchy.removeTypeHierarchyChangedListener(this);
			JavaCore.removeElementChangedListener(this);
			fHierarchy= null;
			fInputElements= null;
		}
		synchronized (this) {
			if (fRefreshHierarchyJob != null) {
				fRefreshHierarchyJob.cancel();
				fRefreshHierarchyJob= null;
			}
		}
	}

	public void removeChangedListener(ITypeHierarchyLifeCycleListener listener) {
		fChangeListeners.remove(listener);
	}

	public void addChangedListener(ITypeHierarchyLifeCycleListener listener) {
		if (!fChangeListeners.contains(listener)) {
			fChangeListeners.add(listener);
		}
	}

	private void fireChange(IType[] changedTypes) {
		for (int i= fChangeListeners.size()-1; i>=0; i--) {
			ITypeHierarchyLifeCycleListener curr= fChangeListeners.get(i);
			curr.typeHierarchyChanged(this, changedTypes);
		}
	}

	/**
	 * Refreshes the type hierarchy for the java element if it exists.
	 *
	 * @param element the java element for which the type hierarchy is computed
	 * @param context the runnable context
	 * @throws InterruptedException thrown from the <code>OperationCanceledException</code> when the monitor is canceled
	 * @throws InvocationTargetException thrown from the <code>JavaModelException</code> if the java element does not exist or if an exception occurs while accessing its corresponding resource
	 */
	public void ensureRefreshedTypeHierarchy(final IJavaElement element, IRunnableContext context) throws InvocationTargetException, InterruptedException {
		ensureRefreshedTypeHierarchy(new IJavaElement[] { element }, context);
	}

	/**
	 * Refreshes the type hierarchy for the java elements if they exist.
	 * 
	 * @param elements the java elements for which the type hierarchy is computed
	 * @param context the runnable context
	 * @throws InterruptedException thrown from the <code>OperationCanceledException</code> when the monitor is canceled
	 * @throws InvocationTargetException thrown from the <code>JavaModelException</code> if a java element does not exist or if an exception occurs while accessing its corresponding resource
	 * @since 3.7
	 */
	public void ensureRefreshedTypeHierarchy(final IJavaElement[] elements, IRunnableContext context) throws InvocationTargetException, InterruptedException {
		synchronized (this) {
			if (fRefreshHierarchyJob != null) {
				fRefreshHierarchyJob.cancel();
				fRefreshJobCanceledExplicitly= false;
				try {
					fRefreshHierarchyJob.join();
				} catch (InterruptedException e) {
					// ignore
				} finally {
					fRefreshHierarchyJob= null;
					fRefreshJobCanceledExplicitly= true;
				}
			}
		}
		if (elements == null || elements.length == 0) {
			freeHierarchy();
			return;
		}
		for (int i= 0; i < elements.length; i++) {
			if (elements[i] == null || !elements[i].exists()) {
				freeHierarchy();
				return;
			}
		}
		boolean hierachyCreationNeeded= (fHierarchy == null || !Arrays.equals(elements, fInputElements));

		if (hierachyCreationNeeded || fHierarchyRefreshNeeded) {
			if (fTypeHierarchyViewPart == null) {
				IRunnableWithProgress op= new IRunnableWithProgress() {
					public void run(IProgressMonitor pm) throws InvocationTargetException, InterruptedException {
						try {
							doHierarchyRefresh(elements, pm);
						} catch (JavaModelException e) {
							throw new InvocationTargetException(e);
						} catch (OperationCanceledException e) {
							throw new InterruptedException();
						}
					}
				};
				fHierarchyRefreshNeeded= true;
				context.run(true, true, op);
				fHierarchyRefreshNeeded= false;
			} else {
				final String label= Messages.format(TypeHierarchyMessages.TypeHierarchyLifeCycle_computeInput, HistoryAction.getElementLabel(elements));
				synchronized (this) {
					fRefreshHierarchyJob= new Job(label) {
						/*
						 * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
						 */
						@Override
						public IStatus run(IProgressMonitor pm) {
							pm.beginTask(label, LONG);
							try {
								doHierarchyRefreshBackground(elements, pm);
							} catch (OperationCanceledException e) {
								if (fRefreshJobCanceledExplicitly) {
									fTypeHierarchyViewPart.showEmptyViewer();
								}
								return Status.CANCEL_STATUS;
							} catch (JavaModelException e) {
								return e.getStatus();
							} finally {
								fHierarchyRefreshNeeded= true;
								pm.done();
							}
							return Status.OK_STATUS;
						}
					};
					fRefreshHierarchyJob.setUser(true);
					IWorkbenchSiteProgressService progressService= (IWorkbenchSiteProgressService)fTypeHierarchyViewPart.getSite()
														.getAdapter(IWorkbenchSiteProgressService.class);
					progressService.schedule(fRefreshHierarchyJob, 0);
				}
			}
		}
	}

	/**
	 * Returns <code>true</code> if the refresh job is running, <code>false</code> otherwise.
	 * 
	 * @return <code>true</code> if the refresh job is running, <code>false</code> otherwise
	 * 
	 * @since 3.6
	 */
	public boolean isRefreshJobRunning() {
		return fRefreshHierarchyJob != null;
	}

	/**
	 * Refreshes the hierarchy in the background and updates the hierarchy viewer asynchronously in
	 * the UI thread.
	 * 
	 * @param elements the java elements on which the hierarchy is computed
	 * @param pm the progress monitor
	 * @throws JavaModelException if the java element does not exist or if an exception occurs while
	 *             accessing its corresponding resource.
	 * 
	 * @since 3.6
	 */
	protected void doHierarchyRefreshBackground(final IJavaElement[] elements, final IProgressMonitor pm) throws JavaModelException {
		doHierarchyRefresh(elements, pm);
		if (!pm.isCanceled()) {
			Display.getDefault().asyncExec(new Runnable() {
				/*
				 * @see java.lang.Runnable#run()
				 */
				public void run() {
					synchronized (TypeHierarchyLifeCycle.this) {
						if (fRefreshHierarchyJob == null) {
							return;
						}
						fRefreshHierarchyJob= null;
					}
					if (pm.isCanceled())
						return;
					fTypeHierarchyViewPart.setViewersInput();
					fTypeHierarchyViewPart.updateViewers();
				}
			});
		}
	}

	private ITypeHierarchy createTypeHierarchy(IJavaElement[] elements, IProgressMonitor pm) throws JavaModelException {
		if (elements.length == 1 && elements[0].getElementType() == IJavaElement.TYPE) {
			IType type= (IType)elements[0];
			if (fIsSuperTypesOnly) {
				return type.newSupertypeHierarchy(pm);
			} else {
				return type.newTypeHierarchy(pm);
			}
		} else {
			IRegion region= JavaCore.newRegion();
			for (int i= 0; i < elements.length; i++) {
				if (elements[i].getElementType() == IJavaElement.JAVA_PROJECT) {
					// for projects only add the contained source folders
					IPackageFragmentRoot[] roots= ((IJavaProject)elements[i]).getPackageFragmentRoots();
					for (int j= 0; j < roots.length; j++) {
						if (!roots[j].isExternal()) {
							region.add(roots[j]);
						}
					}
				} else {
					region.add(elements[i]);
				}
			}
			return JavaCore.newTypeHierarchy(region, null, pm);
		}
	}


	public void doHierarchyRefresh(IJavaElement[] elements, IProgressMonitor pm) throws JavaModelException {
		boolean hierachyCreationNeeded= (fHierarchy == null || !Arrays.equals(elements, fInputElements));
		// to ensure the order of the two listeners always remove / add listeners on operations
		// on type hierarchies
		if (fHierarchy != null) {
			fHierarchy.removeTypeHierarchyChangedListener(this);
			JavaCore.removeElementChangedListener(this);
		}
		if (hierachyCreationNeeded) {
			fHierarchy= createTypeHierarchy(elements, pm);
			if (pm != null && pm.isCanceled()) {
				throw new OperationCanceledException();
			}
			fInputElements= elements;
		} else {
			fHierarchy.refresh(pm);
			if (pm != null && pm.isCanceled())
				throw new OperationCanceledException();
		}
		fHierarchy.addTypeHierarchyChangedListener(this);
		JavaCore.addElementChangedListener(this);
		fHierarchyRefreshNeeded= false;
	}

	/*
	 * @see ITypeHierarchyChangedListener#typeHierarchyChanged
	 */
	public void typeHierarchyChanged(ITypeHierarchy typeHierarchy) {
	 	fHierarchyRefreshNeeded= true;
 		fireChange(null);
	}

	/*
	 * @see IElementChangedListener#elementChanged(ElementChangedEvent)
	 */
	public void elementChanged(ElementChangedEvent event) {
		if (fChangeListeners.isEmpty()) {
			return;
		}

		if (fHierarchyRefreshNeeded) {
			return;
		} else {
			ArrayList<IType> changedTypes= new ArrayList<IType>();
			processDelta(event.getDelta(), changedTypes);
			if (changedTypes.size() > 0) {
				fireChange(changedTypes.toArray(new IType[changedTypes.size()]));
			}
		}
	}

	/*
	 * Assume that the hierarchy is intact (no refresh needed)
	 */
	private void processDelta(IJavaElementDelta delta, ArrayList<IType> changedTypes) {
		IJavaElement element= delta.getElement();
		switch (element.getElementType()) {
			case IJavaElement.TYPE:
				processTypeDelta((IType) element, changedTypes);
				processChildrenDelta(delta, changedTypes); // (inner types)
				break;
			case IJavaElement.JAVA_MODEL:
			case IJavaElement.JAVA_PROJECT:
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
			case IJavaElement.PACKAGE_FRAGMENT:
				processChildrenDelta(delta, changedTypes);
				break;
			case IJavaElement.COMPILATION_UNIT:
				ICompilationUnit cu= (ICompilationUnit)element;
				if (!JavaModelUtil.isPrimary(cu)) {
					return;
				}

				if (delta.getKind() == IJavaElementDelta.CHANGED && isPossibleStructuralChange(delta.getFlags())) {
					try {
						if (cu.exists()) {
							IType[] types= cu.getAllTypes();
							for (int i= 0; i < types.length; i++) {
								processTypeDelta(types[i], changedTypes);
							}
						}
					} catch (JavaModelException e) {
						JavaPlugin.log(e);
					}
				} else {
					processChildrenDelta(delta, changedTypes);
				}
				break;
			case IJavaElement.CLASS_FILE:
				if (delta.getKind() == IJavaElementDelta.CHANGED) {
					IType type= ((IClassFile) element).getType();
					processTypeDelta(type, changedTypes);
				} else {
					processChildrenDelta(delta, changedTypes);
				}
				break;
		}
	}

	private boolean isPossibleStructuralChange(int flags) {
		return (flags & (IJavaElementDelta.F_CONTENT | IJavaElementDelta.F_FINE_GRAINED)) == IJavaElementDelta.F_CONTENT;
	}

	private void processTypeDelta(IType type, ArrayList<IType> changedTypes) {
		if (getHierarchy().contains(type)) {
			changedTypes.add(type);
		}
	}

	private void processChildrenDelta(IJavaElementDelta delta, ArrayList<IType> changedTypes) {
		IJavaElementDelta[] children= delta.getAffectedChildren();
		for (int i= 0; i < children.length; i++) {
			processDelta(children[i], changedTypes); // recursive
		}
	}


}

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
package org.eclipse.jdt.internal.ui.packageview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.util.TransferDropTargetListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.TreePath;

import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.views.navigator.LocalSelectionTransfer;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgUtils;

import org.eclipse.jdt.internal.ui.dnd.JdtViewerDropAdapter;
import org.eclipse.jdt.internal.ui.workingsets.IWorkingSetIDs;
import org.eclipse.jdt.internal.ui.workingsets.WorkingSetModel;

public class WorkingSetDropAdapter extends JdtViewerDropAdapter implements TransferDropTargetListener {

	private PackageExplorerPart fPackageExplorer;

	private IStructuredSelection fSelection;
	private Object[] fElementsToAdds;
	private Set<IAdaptable> fCurrentElements;
	private IWorkingSet fWorkingSet;

	private int fLocation;

	public WorkingSetDropAdapter(PackageExplorerPart part) {
		super(part.getTreeViewer());
		fPackageExplorer= part;

		fLocation= -1;

		setScrollEnabled(true);
		setExpandEnabled(true);
		setFeedbackEnabled(false);
	}

	//---- TransferDropTargetListener interface ---------------------------------------

	/**
	 * {@inheritDoc}
	 */
	public Transfer getTransfer() {
		return LocalSelectionTransfer.getInstance();
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isEnabled(DropTargetEvent event) {
		Object target= event.item != null ? event.item.getData() : null;
		if (target == null)
			return false;
		ISelection selection= LocalSelectionTransfer.getInstance().getSelection();
		if (!isValidSelection(selection)) {
			return false;
		}
		if (!isValidTarget(target))
			return false;

		initializeState(target, selection);
		return true;
	}

	//---- Actual DND -----------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean validateDrop(Object target, int operation, TransferData transferType) {
		return determineOperation(target, operation, transferType, DND.DROP_MOVE | DND.DROP_LINK | DND.DROP_COPY) != DND.DROP_NONE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected int determineOperation(Object target, int operation, TransferData transferType, int operations) {
		switch(operation) {
			case DND.DROP_DEFAULT:
			case DND.DROP_COPY:
			case DND.DROP_MOVE:
				return validateTarget(target, operation);
			default:
				return DND.DROP_NONE;
		}

	}

	private int validateTarget(Object target, int operation) {
		setFeedbackEnabled(false);
		setScrollEnabled(true);
		setExpandEnabled(true);
		if (!isValidTarget(target))
			return DND.DROP_NONE;
		ISelection s= LocalSelectionTransfer.getInstance().getSelection();
		if (!isValidSelection(s)) {
			return DND.DROP_NONE;
		}

		initializeState(target, s);

		if (isWorkingSetSelection()) {
			setExpandEnabled(false);
			if ((getCurrentLocation() == LOCATION_BEFORE || getCurrentLocation() == LOCATION_AFTER) &&
					!fPackageExplorer.getWorkingSetModel().isSortingEnabled()) {
				setFeedbackEnabled(true);
				return DND.DROP_MOVE;
			}
			return DND.DROP_NONE;
		} else {
			if (isOthersWorkingSet(fWorkingSet) && operation == DND.DROP_COPY)
				return DND.DROP_NONE;

			List<IJavaElement> realJavaElements= new ArrayList<IJavaElement>();
			List<IResource> realResource= new ArrayList<IResource>();
			ReorgUtils.splitIntoJavaElementsAndResources(fElementsToAdds, realJavaElements, realResource);
			if (fElementsToAdds.length != realJavaElements.size() + realResource.size())
				return DND.DROP_NONE;
			for (Iterator<IJavaElement> iter= realJavaElements.iterator(); iter.hasNext();) {
				IJavaElement element= iter.next();
				if (ReorgUtils.containsElementOrParent(fCurrentElements, element))
					return DND.DROP_NONE;
			}
			for (Iterator<IResource> iter= realResource.iterator(); iter.hasNext();) {
				IResource element= iter.next();
				if (ReorgUtils.containsElementOrParent(fCurrentElements, element))
					return DND.DROP_NONE;
			}
			if (!(fSelection instanceof ITreeSelection)) {
				return DND.DROP_COPY;
			}
			ITreeSelection treeSelection= (ITreeSelection)fSelection;
			TreePath[] paths= treeSelection.getPaths();
			for (int i= 0; i < paths.length; i++) {
				TreePath path= paths[i];
				if (path.getSegmentCount() != 2)
					return DND.DROP_COPY;
				if (!(path.getSegment(0) instanceof IWorkingSet))
					return DND.DROP_COPY;
				if (paths.length == 1) {
					IWorkingSet ws= (IWorkingSet)path.getSegment(0);
					if (isOthersWorkingSet(ws))
						return DND.DROP_MOVE;
				}
			}
		}
		if (operation == DND.DROP_DEFAULT)
			return DND.DROP_MOVE;
		return operation;
	}

	private boolean isValidTarget(Object target) {
		return target instanceof IWorkingSet;
	}

	private boolean isValidSelection(ISelection selection) {
		return selection instanceof IStructuredSelection;
	}

	private boolean isOthersWorkingSet(IWorkingSet ws) {
		return IWorkingSetIDs.OTHERS.equals(ws.getId());
	}

	private void initializeState(Object target, ISelection s) {
		fWorkingSet= (IWorkingSet)target;
		fSelection= (IStructuredSelection)s;
		fElementsToAdds= fSelection.toArray();
		fCurrentElements= new HashSet<IAdaptable>(Arrays.asList(fWorkingSet.getElements()));
	}

	private boolean isWorkingSetSelection() {
		for (int i= 0; i < fElementsToAdds.length; i++) {
			if (!(fElementsToAdds[i] instanceof IWorkingSet))
				return false;
		}
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean performDrop(Object data) {
		if (isWorkingSetSelection()) {
			performWorkingSetReordering();
		} else {
			performElementRearrange(getCurrentOperation());
		}
		return true;
	}

	private void performWorkingSetReordering() {
		WorkingSetModel model= fPackageExplorer.getWorkingSetModel();
		List<IWorkingSet> allWorkingSets= new ArrayList<IWorkingSet>(Arrays.asList(model.getAllWorkingSets()));
		int index= allWorkingSets.indexOf(fWorkingSet);
		if (index != -1) {
			if (getCurrentLocation() == LOCATION_AFTER)
				index++;
			List<IWorkingSet> result= new ArrayList<IWorkingSet>(allWorkingSets.size());
			@SuppressWarnings("unchecked") // isWorkingSetSelection() ensures that all elements are IWorkingSets
			List<IWorkingSet> selected= new ArrayList<IWorkingSet>((List<IWorkingSet>) (List<?>) Arrays.asList(fElementsToAdds));
			List<IWorkingSet> activeWorkingSets= new ArrayList<IWorkingSet>(Arrays.asList(model.getActiveWorkingSets()));
			List<IWorkingSet> active= new ArrayList<IWorkingSet>(activeWorkingSets.size());
			for (int i= 0; i < allWorkingSets.size(); i++) {
				if (i == index) {
					result.addAll(selected);
					active.addAll(selected);
				}
				IWorkingSet element= allWorkingSets.get(i);
				if (!selected.contains(element)) {
					result.add(element);
					if (activeWorkingSets.contains(element))
						active.add(element);
				}
			}
			if (index == allWorkingSets.size()) {
				result.addAll(selected);
				active.addAll(selected);
			}
			model.setWorkingSets(result.toArray(new IWorkingSet[result.size()]), model.isSortingEnabled(), active.toArray(new IWorkingSet[active.size()]));
		}
	}

	private void performElementRearrange(int eventDetail) {
		// only move if target isn't the other working set. If this is the case
		// the move will happenn automatically by refreshing the other working set
		if (!isOthersWorkingSet(fWorkingSet)) {
			List<Object> elements= new ArrayList<Object>(Arrays.asList(fWorkingSet.getElements()));
			elements.addAll(Arrays.asList(fElementsToAdds));
			fWorkingSet.setElements(elements.toArray(new IAdaptable[elements.size()]));
		}
		if (eventDetail == DND.DROP_MOVE) {
			ITreeSelection treeSelection= (ITreeSelection)fSelection;
			Map<IWorkingSet, List<Object>> workingSets= groupByWorkingSets(treeSelection.getPaths());
			for (Iterator<IWorkingSet> iter= workingSets.keySet().iterator(); iter.hasNext();) {
				IWorkingSet ws= iter.next();
				List<Object> toRemove= workingSets.get(ws);
				List<IAdaptable> currentElements= new ArrayList<IAdaptable>(Arrays.asList(ws.getElements()));
				currentElements.removeAll(toRemove);
				ws.setElements(currentElements.toArray(new IAdaptable[currentElements.size()]));
			}
		}
	}

	private Map<IWorkingSet, List<Object>> groupByWorkingSets(TreePath[] paths) {
		Map<IWorkingSet, List<Object>> result= new HashMap<IWorkingSet, List<Object>>();
		for (int i= 0; i < paths.length; i++) {
			TreePath path= paths[i];
			IWorkingSet ws= (IWorkingSet)path.getSegment(0);
			List<Object> l= result.get(ws);
			if (l == null) {
				l= new ArrayList<Object>();
				result.put(ws, l);
			}
			l.add(path.getSegment(1));
		}
		return result;
	}

	//---- test methods for JUnit test since DnD is hard to simulate

	public int internalTestValidateTarget(Object target, int operation) {
		return validateTarget(target, operation);
	}

	public void internalTestDrop(Object target, int eventDetail) {
		if (isWorkingSetSelection()) {
			performWorkingSetReordering();
		} else {
			performElementRearrange(eventDetail);
		}
	}

	public void internalTestSetLocation(int location) {
		fLocation= location;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected int getCurrentLocation() {
		if (fLocation == -1)
			return super.getCurrentLocation();

		return fLocation;
	}
}

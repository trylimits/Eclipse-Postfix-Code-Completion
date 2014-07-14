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
package org.eclipse.jdt.internal.ui.workingsets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.ListenerList;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.IElementComparer;

import org.eclipse.ui.ILocalWorkingSetManager;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.IWorkingSetUpdater;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;

public class WorkingSetModel {

	public static final String CHANGE_WORKING_SET_MODEL_CONTENT= "workingSetModelChanged"; //$NON-NLS-1$

	public static final IElementComparer COMPARER= new WorkingSetComparar();

	private static final String TAG_LOCAL_WORKING_SET_MANAGER= "localWorkingSetManager"; //$NON-NLS-1$

	/**
	 * Key associated with the state of all working sets.
	 * @since 3.7
	 */
	private static final String TAG_ALL_WORKING_SETS= "allWorkingSets";  //$NON-NLS-1$
	private static final String TAG_ACTIVE_WORKING_SET= "activeWorkingSet"; //$NON-NLS-1$
	private static final String TAG_WORKING_SET_NAME= "workingSetName"; //$NON-NLS-1$
	private static final String TAG_CONFIGURED= "configured"; //$NON-NLS-1$

	/**
	 * Key associated with the sort state of working sets.
	 * 
	 * @since 3.5
	 */
	private static final String TAG_SORT_WORKING_SETS= "sortWorkingSets"; //$NON-NLS-1$

	private final ILocalWorkingSetManager fLocalWorkingSetManager;
	private List<IWorkingSet> fActiveWorkingSets;
	private ListenerList fListeners;
	private IPropertyChangeListener fWorkingSetManagerListener;
	private OthersWorkingSetUpdater fOthersWorkingSetUpdater;

	private final ElementMapper fElementMapper= new ElementMapper();

	private boolean fConfigured;

	/**
	 * Value of the sorted state of working sets.
	 * 
	 * @since 3.5
	 */
	private boolean fIsSortingEnabled;

	/**
	 * List of all working sets. 
	 * @since 3.7
	 */
	private List<IWorkingSet> fAllWorkingSets;

	private static class WorkingSetComparar implements IElementComparer {
		public boolean equals(Object o1, Object o2) {
			IWorkingSet w1= o1 instanceof IWorkingSet ? (IWorkingSet)o1 : null;
			IWorkingSet w2= o2 instanceof IWorkingSet ? (IWorkingSet)o2 : null;
			if (w1 == null || w2 == null)
				return o1.equals(o2);
			return w1 == w2;
		}
		public int hashCode(Object element) {
			if (element instanceof IWorkingSet)
				return System.identityHashCode(element);
			return element.hashCode();
		}
	}

	private static class ElementMapper {
		private final Map<IAdaptable, Object> fElementToWorkingSet= new HashMap<IAdaptable, Object>();
		private final Map<IWorkingSet, IAdaptable[]> fWorkingSetToElement= new IdentityHashMap<IWorkingSet, IAdaptable[]>();

		private final Map<IAdaptable, Object> fResourceToWorkingSet= new HashMap<IAdaptable, Object>();
		private final List<IAdaptable> fNonProjectTopLevelElements= new ArrayList<IAdaptable>();

		public void clear() {
			fElementToWorkingSet.clear();
			fWorkingSetToElement.clear();
			fResourceToWorkingSet.clear();
			fNonProjectTopLevelElements.clear();
		}
		public void rebuild(IWorkingSet[] workingSets) {
			clear();
			for (int i= 0; i < workingSets.length; i++) {
				put(workingSets[i]);
			}
		}
		public IAdaptable[] refresh(IWorkingSet ws) {
			IAdaptable[] oldElements= fWorkingSetToElement.get(ws);
			if (oldElements == null)
				return null;
			IAdaptable[] newElements= ws.getElements();
			List<IAdaptable> toRemove= new ArrayList<IAdaptable>(Arrays.asList(oldElements));
			List<IAdaptable> toAdd= new ArrayList<IAdaptable>(Arrays.asList(newElements));
			computeDelta(toRemove, toAdd, oldElements, newElements);
			for (Iterator<IAdaptable> iter= toAdd.iterator(); iter.hasNext();) {
				addElement(iter.next(), ws);
			}
			for (Iterator<IAdaptable> iter= toRemove.iterator(); iter.hasNext();) {
				removeElement(iter.next(), ws);
			}
			if (toRemove.size() > 0 || toAdd.size() > 0)
				fWorkingSetToElement.put(ws, newElements);
			return oldElements;
		}
		private void computeDelta(List<IAdaptable> toRemove, List<IAdaptable> toAdd, IAdaptable[] oldElements, IAdaptable[] newElements) {
			for (int i= 0; i < oldElements.length; i++) {
				toAdd.remove(oldElements[i]);
			}
			for (int i= 0; i < newElements.length; i++) {
				toRemove.remove(newElements[i]);
			}

		}
		public IWorkingSet getFirstWorkingSet(Object element) {
			return (IWorkingSet)getFirstElement(fElementToWorkingSet, element);
		}
		public List<IWorkingSet> getAllWorkingSets(Object element) {
			 List<IWorkingSet> allElements= getAllElements(fElementToWorkingSet, element);
			 if (allElements.isEmpty() && element instanceof IJavaElement) {
				 // try a second time in case the working set was manually updated (bug 168032)
				 allElements= getAllElements(fElementToWorkingSet, ((IJavaElement) element).getResource());
			 }
			 return allElements;
		}
		public List<IWorkingSet> getAllWorkingSetsForResource(IResource resource) {
			return getAllElements(fResourceToWorkingSet, resource);
		}
		public List<IAdaptable> getNonProjectTopLevelElements() {
			return fNonProjectTopLevelElements;
		}
		private void put(IWorkingSet ws) {
			if (fWorkingSetToElement.containsKey(ws))
				return;
			IAdaptable[] elements= ws.getElements();
			fWorkingSetToElement.put(ws, elements);
			for (int i= 0; i < elements.length; i++) {
				IAdaptable element= elements[i];
				addElement(element, ws);
				if (!(element instanceof IProject) && !(element instanceof IJavaProject)) {
					fNonProjectTopLevelElements.add(element);
				}
			}
		}
		private void addElement(IAdaptable element, IWorkingSet ws) {
			addToMap(fElementToWorkingSet, element, ws);
			IResource resource= (IResource)element.getAdapter(IResource.class);
			if (resource != null) {
				addToMap(fResourceToWorkingSet, resource, ws);
			}
		}
		private void removeElement(IAdaptable element, IWorkingSet ws) {
			removeFromMap(fElementToWorkingSet, element, ws);
			IResource resource= (IResource)element.getAdapter(IResource.class);
			if (resource != null) {
				removeFromMap(fResourceToWorkingSet, resource, ws);
			}
		}
		private void addToMap(Map<IAdaptable, Object> map, IAdaptable key, IWorkingSet value) {
			Object obj= map.get(key);
			if (obj == null) {
				map.put(key, value);
			} else if (obj instanceof IWorkingSet) {
				List<IWorkingSet> l= new ArrayList<IWorkingSet>(2);
				l.add((IWorkingSet) obj);
				l.add(value);
				map.put(key, l);
			} else if (obj instanceof List) {
				@SuppressWarnings("unchecked")
				List<IWorkingSet> sets= (List<IWorkingSet>)obj;
				sets.add(value);
			}
		}
		private void removeFromMap(Map<IAdaptable, Object> map, IAdaptable key, IWorkingSet value) {
			Object current= map.get(key);
			if (current == null) {
				return;
			} else if (current instanceof List) {
				@SuppressWarnings("unchecked")
				List<IWorkingSet> sets= (List<IWorkingSet>)current;
				sets.remove(value);
				switch (sets.size()) {
					case 0:
						map.remove(key);
						break;
					case 1:
						map.put(key, sets.get(0));
						break;
				}
			} else if (current == value) {
				map.remove(key);
			}
		}
		private Object getFirstElement(Map<IAdaptable, Object> map, Object key) {
			Object obj= map.get(key);
			if (obj instanceof List) {
				@SuppressWarnings("unchecked")
				List<IWorkingSet> sets= (List<IWorkingSet>)obj;
				return sets.get(0);
			}
			return obj;
		}
		private List<IWorkingSet> getAllElements(Map<IAdaptable, Object> map, Object key) {
			Object obj= map.get(key);
			if (obj instanceof List) {
				@SuppressWarnings("unchecked")
				List<IWorkingSet> sets= (List<IWorkingSet>)obj;
				return sets;
			}
			if (obj == null)
				return Collections.emptyList();
			List<IWorkingSet> result= new ArrayList<IWorkingSet>(1);
			result.add((IWorkingSet) obj);
			return result;
		}
	}

	/**
	 * @param memento a memento, or <code>null</code>
	 */
	public WorkingSetModel(IMemento memento) {
		fLocalWorkingSetManager= PlatformUI.getWorkbench().createLocalWorkingSetManager();
		addListenersToWorkingSetManagers();
		fActiveWorkingSets= new ArrayList<IWorkingSet>();
		fAllWorkingSets= new ArrayList<IWorkingSet>();

		if (memento == null || ! restoreState(memento)) {
			IWorkingSet others= fLocalWorkingSetManager.createWorkingSet(WorkingSetMessages.WorkingSetModel_others_name, new IAdaptable[0]);
			others.setId(IWorkingSetIDs.OTHERS);
			fLocalWorkingSetManager.addWorkingSet(others);
			fActiveWorkingSets.add(others);
			fAllWorkingSets.add(others);
		}
		Assert.isNotNull(fOthersWorkingSetUpdater);

		fElementMapper.rebuild(getActiveWorkingSets());
		fOthersWorkingSetUpdater.updateElements();
	}

	private void addListenersToWorkingSetManagers() {
		fListeners= new ListenerList(ListenerList.IDENTITY);
		fWorkingSetManagerListener= new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				workingSetManagerChanged(event);
			}
		};
		PlatformUI.getWorkbench().getWorkingSetManager().addPropertyChangeListener(fWorkingSetManagerListener);
		fLocalWorkingSetManager.addPropertyChangeListener(fWorkingSetManagerListener);
	}

	public void dispose() {
		if (fWorkingSetManagerListener != null) {
			PlatformUI.getWorkbench().getWorkingSetManager().removePropertyChangeListener(fWorkingSetManagerListener);
			fLocalWorkingSetManager.removePropertyChangeListener(fWorkingSetManagerListener);
			fLocalWorkingSetManager.dispose();
			fWorkingSetManagerListener= null;
		}
	}

	//---- model relationships ---------------------------------------

	public IAdaptable[] getChildren(IWorkingSet workingSet) {
		return workingSet.getElements();
	}

	public Object getParent(Object element) {
		if (element instanceof IWorkingSet && fActiveWorkingSets.contains(element))
			return this;
		return fElementMapper.getFirstWorkingSet(element);
	}

	public Object[] getAllParents(Object element) {
		if (element instanceof IWorkingSet && fActiveWorkingSets.contains(element))
			return new Object[] {this};
		return fElementMapper.getAllWorkingSets(element).toArray();
	}

	public Object[] addWorkingSets(Object[] elements) {
		List<? super IWorkingSet> result= null;
		for (int i= 0; i < elements.length; i++) {
			Object element= elements[i];
			List<IWorkingSet> sets= null;
			if (element instanceof IResource) {
				sets= fElementMapper.getAllWorkingSetsForResource((IResource)element);
			} else {
				sets= fElementMapper.getAllWorkingSets(element);
			}
			if (sets != null && sets.size() > 0) {
				if (result == null)
					result= new ArrayList<Object>(Arrays.asList(elements));
				result.addAll(sets);
			}
		}
		if (result == null)
			return elements;
		return result.toArray();
	}

	public boolean needsConfiguration() {
		return !fConfigured && fActiveWorkingSets.size() == 1 &&
			IWorkingSetIDs.OTHERS.equals(fActiveWorkingSets.get(0).getId());
	}

	public void configured() {
		fConfigured= true;
	}

	//---- working set management -----------------------------------

	/**
	 * Adds a property change listener.
	 *
	 * @param listener the property change listener to add
	 */
	public void addPropertyChangeListener(IPropertyChangeListener listener) {
		fListeners.add(listener);
	}

	/**
	 * Removes the property change listener.
	 *
	 * @param listener the property change listener to remove
	 */
	public void removePropertyChangeListener(IPropertyChangeListener listener) {
		fListeners.remove(listener);
	}

	public IWorkingSet[] getActiveWorkingSets() {
		return fActiveWorkingSets.toArray(new IWorkingSet[fActiveWorkingSets.size()]);
	}

	/**
	 * Returns the array of all working sets.
	 * 
	 * @return the array of all working sets
	 * @since 3.7
	 */
	public IWorkingSet[] getAllWorkingSets() {
		if (fAllWorkingSets.size() == 1 && IWorkingSetIDs.OTHERS.equals(fAllWorkingSets.get(0).getId()))
			fAllWorkingSets= getActiveAndAllWorkingSetsFromManagers();
		return fAllWorkingSets.toArray(new IWorkingSet[fAllWorkingSets.size()]);
	}

	/**
	 * Returns the list containing active and all working sets from the working set managers.
	 * 
	 * @return the list of all the working sets
	 * @since 3.7
	 */
	private List<IWorkingSet> getActiveAndAllWorkingSetsFromManagers() {
		List<IWorkingSet> result= new ArrayList<IWorkingSet>();
		result.addAll(fActiveWorkingSets);
		IWorkingSet[] locals= fLocalWorkingSetManager.getWorkingSets();
		for (int i= 0; i < locals.length; i++) {
			if (!result.contains(locals[i]) && isSupportedAsTopLevelElement(locals[i]))
				result.add(locals[i]);
		}
		IWorkingSet[] globals= PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSets();
		for (int i= 0; i < globals.length; i++) {
			if (!result.contains(globals[i]) && isSupportedAsTopLevelElement(globals[i]))
				result.add(globals[i]);
		}

		if (fIsSortingEnabled)
			Collections.sort(result, new WorkingSetComparator(true));
		return result;
	}

	/**
	 * Adds newly created working sets to the list of all working sets.
	 * 
	 * @param result the list of all working sets from the working set managers
	 * @since 3.7
	 */
	private void addNewlyCreatedWorkingSets(List<IWorkingSet> result) {
		for (Iterator<IWorkingSet> iter= result.iterator(); iter.hasNext();) {
			IWorkingSet set= iter.next();
			if (!fAllWorkingSets.contains(set))
				fAllWorkingSets.add(set);
		}
	}

	/**
	 * Sets the working sets lists.
	 * <p>
	 * Note : All the active working sets must be contained in allWorkingSets and the relative
	 * ordering of the active working sets must be same in both allWorkingSets and activeWorkingSets
	 * arrays, else the method throws an <code>IllegalArgumentException</code.
	 * </p>
	 * 
	 * @param allWorkingSets the array of all working sets
	 * @param isSortingEnabled <code>true</code> if sorting is enabled, <code>false</code> otherwise
	 * @param activeWorkingSets the array of active working sets
	 * @since 3.7
	 */
	public void setWorkingSets(IWorkingSet[] allWorkingSets, boolean isSortingEnabled, IWorkingSet[] activeWorkingSets) {
		Assert.isLegal(Arrays.asList(allWorkingSets).containsAll(Arrays.asList(activeWorkingSets)));
		Assert.isLegal(!isOrderDifferentInWorkingSetLists(Arrays.asList(allWorkingSets), Arrays.asList(activeWorkingSets)));
		if (isSortingEnabled)
			Arrays.sort(allWorkingSets, new WorkingSetComparator(true));
		fAllWorkingSets= new ArrayList<IWorkingSet>(Arrays.asList(allWorkingSets));
		setActiveWorkingSets(activeWorkingSets, isSortingEnabled);
	}

	/**
	 * Sets the active working sets.
	 * <p>
	 * Note: If the relative ordering of the active working sets is not same in both fAllWorkingSets
	 * and fActiveWorkingSets, fAllWorkingSets is re-ordered according to fActiveWorkingSets.
	 * </p>
	 * 
	 * @param workingSets the active working sets to be set
	 * 
	 */
	public void setActiveWorkingSets(IWorkingSet[] workingSets) {
		Assert.isLegal(Arrays.asList(getAllWorkingSets()).containsAll(Arrays.asList(workingSets)));
		if (fIsSortingEnabled) {
			Arrays.sort(workingSets, new WorkingSetComparator(true));
		}
		fActiveWorkingSets= new ArrayList<IWorkingSet>(Arrays.asList(workingSets));
		if (isOrderDifferentInWorkingSetLists(fAllWorkingSets, fActiveWorkingSets)) { //see bug 338531
			adjustOrderingOfAllWorkingSets();
		}
		fElementMapper.rebuild(getActiveWorkingSets());
		fOthersWorkingSetUpdater.updateElements();
		fireEvent(new PropertyChangeEvent(this, CHANGE_WORKING_SET_MODEL_CONTENT, null, null));
	}

	/**
	 * Adjusts the relative ordering of the active working sets in fAllWorkingSets according to
	 * fActiveWorkingSets.
	 * 
	 * @since 3.7
	 */
	private void adjustOrderingOfAllWorkingSets() {
		int countActive= 0;
		for (Iterator<IWorkingSet> iter= fAllWorkingSets.iterator(); iter.hasNext();) {
			IWorkingSet set= iter.next();
			if (fActiveWorkingSets.contains(set)) {
				IWorkingSet workingSet= fActiveWorkingSets.get(countActive++);
				if (!workingSet.equals(set)) {
					int index= fAllWorkingSets.indexOf(workingSet);
					fAllWorkingSets.set(fAllWorkingSets.indexOf(set), workingSet);
					fAllWorkingSets.set(index, set);
				}
				if (countActive == fActiveWorkingSets.size())
					return;
			}
		}
	}

	/**
	 * Checks if the order of active working sets is different in the active and all working set
	 * lists.
	 * 
	 * @param allWorkingSets the list of all working sets
	 * @param activeWorkingSets the list of active working sets
	 * @return <code>true</code> if the order is different, <code>false</code> otherwise
	 * @since 3.7
	 */
	private boolean isOrderDifferentInWorkingSetLists(List<IWorkingSet> allWorkingSets, List<IWorkingSet> activeWorkingSets) {
		int count= 0;
		for (Iterator<IWorkingSet> iter= allWorkingSets.iterator(); iter.hasNext();) {
			IWorkingSet set= iter.next();
			if (activeWorkingSets.contains(set)) {
				if (!activeWorkingSets.get(count++).equals(set))
					return true;
			}
		}
		return false;
	}

	/**
	 * Sets the active working sets.
	 * 
	 * @param workingSets the array of working sets
	 * @param isSortingEnabled <code>true</code> if sorting is enabled, <code>false</code> otherwise
	 * @since 3.5
	 */
	public void setActiveWorkingSets(IWorkingSet[] workingSets, boolean isSortingEnabled) {
		fIsSortingEnabled= isSortingEnabled;
		setActiveWorkingSets(workingSets);
	}

	public void saveState(IMemento memento) {
		memento.putBoolean(TAG_SORT_WORKING_SETS, fIsSortingEnabled);
		memento.putBoolean(TAG_CONFIGURED, fConfigured);
		fLocalWorkingSetManager.saveState(memento.createChild(TAG_LOCAL_WORKING_SET_MANAGER));
		for (Iterator<IWorkingSet> iter= fActiveWorkingSets.iterator(); iter.hasNext();) {
			IMemento active= memento.createChild(TAG_ACTIVE_WORKING_SET);
			IWorkingSet workingSet= iter.next();
			active.putString(TAG_WORKING_SET_NAME, workingSet.getName());
		}
		for (Iterator<IWorkingSet> iter= Arrays.asList(getAllWorkingSets()).iterator(); iter.hasNext();) {
			IMemento allWorkingSet= memento.createChild(TAG_ALL_WORKING_SETS);
			IWorkingSet workingSet= iter.next();
			if (isSupportedAsTopLevelElement(workingSet))
				allWorkingSet.putString(TAG_WORKING_SET_NAME, workingSet.getName());
		}
	}

	public List<IAdaptable> getNonProjectTopLevelElements() {
		return fElementMapper.getNonProjectTopLevelElements();
	}

	/**
	 * Restore localWorkingSetManager and active working sets.
	 * 
	 * @param memento a memento
	 * @return whether the restore was successful
	 */
	private boolean restoreState(IMemento memento) {
		String configured= memento.getString(TAG_CONFIGURED);
		if (configured == null)
			return false;

		fConfigured= Boolean.valueOf(configured).booleanValue();
		fLocalWorkingSetManager.restoreState(memento.getChild(TAG_LOCAL_WORKING_SET_MANAGER));
		IWorkingSet[] allLocalWorkingSets= fLocalWorkingSetManager.getAllWorkingSets();
		for (int i= 0; i < allLocalWorkingSets.length; i++) {
			IWorkingSet ws= allLocalWorkingSets[i];
			if (IWorkingSetIDs.OTHERS.equals(ws.getId())) {
				// have to set the label again, since the locale could have been changed (bug 272737)
				String otherProjectsLabel= WorkingSetMessages.WorkingSetModel_others_name;
				if (! otherProjectsLabel.equals(ws.getLabel())) {
					ws.setLabel(otherProjectsLabel);
				}
			}
		}
		
		String isSortingEnabled= memento.getString(TAG_SORT_WORKING_SETS);
		if (isSortingEnabled == null) {
			fIsSortingEnabled= false;
		} else {
			fIsSortingEnabled= Boolean.valueOf(isSortingEnabled).booleanValue();
		}

		IMemento[] actives= memento.getChildren(TAG_ACTIVE_WORKING_SET);
		for (int i= 0; i < actives.length; i++) {
			String name= actives[i].getString(TAG_WORKING_SET_NAME);
			if (name != null) {
				IWorkingSet ws= fLocalWorkingSetManager.getWorkingSet(name);
				if (ws == null) {
					ws= PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSet(name);
				}
				if (ws != null) {
					fActiveWorkingSets.add(ws);
				}
			}
		}
		IMemento[] allWorkingSets= memento.getChildren(TAG_ALL_WORKING_SETS);
		for (int i= 0; i < allWorkingSets.length; i++) {
			String name= allWorkingSets[i].getString(TAG_WORKING_SET_NAME);
			if (name != null) {
				IWorkingSet ws= fLocalWorkingSetManager.getWorkingSet(name);
				if (ws == null) {
					ws= PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSet(name);
				}
				if (ws != null) {
					fAllWorkingSets.add(ws);
				}
			}
		}

		List<IWorkingSet> result= getActiveAndAllWorkingSetsFromManagers();
		if (!fAllWorkingSets.containsAll(result))
			addNewlyCreatedWorkingSets(result);
		return true;
	}
	private void workingSetManagerChanged(PropertyChangeEvent event) {
		String property= event.getProperty();
		if (IWorkingSetManager.CHANGE_WORKING_SET_UPDATER_INSTALLED.equals(property) && event.getSource() == fLocalWorkingSetManager) {
			IWorkingSetUpdater updater= (IWorkingSetUpdater)event.getNewValue();
			if (updater instanceof OthersWorkingSetUpdater) {
				fOthersWorkingSetUpdater= (OthersWorkingSetUpdater)updater;
				fOthersWorkingSetUpdater.init(this);
			}
			return;
		}

		// Add new working set to the list of active working sets and all working sets
		if (IWorkingSetManager.CHANGE_WORKING_SET_ADD.equals(property)) {
			IWorkingSet workingSet= (IWorkingSet)event.getNewValue();
			if (isSupportedAsTopLevelElement(workingSet)) {
				IWorkingSetManager manager= PlatformUI.getWorkbench().getWorkingSetManager();
				List<IWorkingSet> allWorkingSets= new ArrayList<IWorkingSet>(Arrays.asList(manager.getAllWorkingSets()));
				if (allWorkingSets.contains(workingSet)) {
					List<IWorkingSet> elements= new ArrayList<IWorkingSet>(fActiveWorkingSets);
					if (workingSet.isVisible() && !fActiveWorkingSets.contains(workingSet))
						elements.add(workingSet);
					List<IWorkingSet> allElements= new ArrayList<IWorkingSet>(Arrays.asList(getAllWorkingSets()));
					if (!allElements.contains(workingSet))
						allElements.add(workingSet);
					setWorkingSets(allElements.toArray(new IWorkingSet[allElements.size()]), fIsSortingEnabled, elements.toArray(new IWorkingSet[elements.size()]));
				}
			}
		}

		// don't handle working sets not managed by the model
		if (!isAffected(event))
			return;

		if (IWorkingSetManager.CHANGE_WORKING_SET_CONTENT_CHANGE.equals(property)) {
			IWorkingSet workingSet= (IWorkingSet)event.getNewValue();
			IAdaptable[] elements= fElementMapper.refresh(workingSet);
			if (elements != null) {
				fireEvent(event);
			}
		} else if (IWorkingSetManager.CHANGE_WORKING_SET_REMOVE.equals(property)) {
			IWorkingSet workingSet= (IWorkingSet)event.getOldValue();
			List<IWorkingSet> elements= new ArrayList<IWorkingSet>(fActiveWorkingSets);
			elements.remove(workingSet);
			List<IWorkingSet> allElements= new ArrayList<IWorkingSet>(Arrays.asList(getAllWorkingSets()));
			allElements.remove(workingSet);
			setWorkingSets(allElements.toArray(new IWorkingSet[allElements.size()]), fIsSortingEnabled, elements.toArray(new IWorkingSet[elements.size()]));
		} else if (IWorkingSetManager.CHANGE_WORKING_SET_LABEL_CHANGE.equals(property)) {
			IWorkingSet workingSet= (IWorkingSet)event.getNewValue();
			if (isSortingEnabled() && Arrays.asList(getAllWorkingSets()).contains(workingSet)) {
				setWorkingSets(getAllWorkingSets(), isSortingEnabled(), fActiveWorkingSets.toArray(new IWorkingSet[fActiveWorkingSets.size()]));
			} else {
				fireEvent(event);
			}
		}

	}

	/**
	 * Tells whether the given working set is supported as top-level element
	 * 
	 * @param workingSet the working set to test
	 * @return <code>true</code> if the given working set is supported as top-level element
	 * @since 3.6
	 */
	public static boolean isSupportedAsTopLevelElement(IWorkingSet workingSet) {
		Object id= workingSet.getId();
		if (IWorkingSetIDs.OTHERS.equals(id) || IWorkingSetIDs.JAVA.equals(id) || IWorkingSetIDs.RESOURCE.equals(id))
			return true;

		if (!workingSet.isSelfUpdating() || workingSet.isAggregateWorkingSet())
			return false;

		IAdaptable[] elements= workingSet.getElements();
		for (int i= 0; i < elements.length; i++) {
			IAdaptable element= elements[i];
			IProject p= (IProject)element.getAdapter(IProject.class);
			if (p != null && p.exists())
				return true;
		}
		return false;
	}


	private void fireEvent(PropertyChangeEvent event) {
		Object[] listeners= fListeners.getListeners();
		for (int i= 0; i < listeners.length; i++) {
			((IPropertyChangeListener)listeners[i]).propertyChange(event);
		}
	}

	private boolean isAffected(PropertyChangeEvent event) {
		if (fActiveWorkingSets == null)
			return false;
		Object oldValue= event.getOldValue();
		Object newValue= event.getNewValue();
		if ((oldValue != null && fActiveWorkingSets.contains(oldValue))
				|| (newValue != null && fActiveWorkingSets.contains(newValue))) {
			return true;
		}
		return false;
	}

	public boolean isActiveWorkingSet(IWorkingSet changedWorkingSet) {
		return fActiveWorkingSets.contains(changedWorkingSet);
	}

	public void addActiveWorkingSet(IWorkingSet workingSet) {
		IWorkingSet[] workingSets= getActiveWorkingSets();
		IWorkingSet[] activeWorkingSets= new IWorkingSet[workingSets.length+ 1];
		System.arraycopy(workingSets, 0, activeWorkingSets, 0, workingSets.length);
		activeWorkingSets[workingSets.length]= workingSet;
		setActiveWorkingSets(activeWorkingSets);
	}

	/**
	 * Returns whether sorting is enabled for working sets.
	 * 
	 * @return <code>true</code> if sorting is enabled, <code>false</code> otherwise
	 * @since 3.5
	 */
	public boolean isSortingEnabled() {
		return fIsSortingEnabled;
	}
}

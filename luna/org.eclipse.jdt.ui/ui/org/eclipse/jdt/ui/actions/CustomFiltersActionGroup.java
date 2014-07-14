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
package org.eclipse.jdt.ui.actions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.TreeSet;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.actions.ActionGroup;

import org.eclipse.jdt.core.IJavaModel;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.filters.CustomFiltersDialog;
import org.eclipse.jdt.internal.ui.filters.EmptyLibraryContainerFilter;
import org.eclipse.jdt.internal.ui.filters.FilterDescriptor;
import org.eclipse.jdt.internal.ui.filters.FilterMessages;
import org.eclipse.jdt.internal.ui.filters.NamePatternFilter;

/**
 * Action group to add the filter action to a view part's tool bar
 * menu.
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class CustomFiltersActionGroup extends ActionGroup {

	private static final String TAG_DUMMY_TO_TEST_EXISTENCE= "TAG_DUMMY_TO_TEST_EXISTENCE"; //$NON-NLS-1$

	class ShowFilterDialogAction extends Action {
		ShowFilterDialogAction() {
			setText(FilterMessages.OpenCustomFiltersDialogAction_text);
			setImageDescriptor(JavaPluginImages.DESC_ELCL_FILTER);
			setDisabledImageDescriptor(JavaPluginImages.DESC_DLCL_FILTER);
		}

		@Override
		public void run() {
			openDialog();
		}
	}

	/**
	 * Menu contribution item which shows and lets check and uncheck filters.
	 *
	 * @since 3.0
	 */
	class FilterActionMenuContributionItem extends ContributionItem {

		private int fItemNumber;
		private boolean fState;
		private String fFilterId;
		private String fFilterName;
		private CustomFiltersActionGroup fActionGroup;

		/**
		 * Constructor for FilterActionMenuContributionItem.
		 *
		 * @param actionGroup 	the action group
		 * @param filterId		the id of the filter
		 * @param filterName	the name of the filter
		 * @param state			the initial state of the filter
		 * @param itemNumber	the menu item index
		 */
		public FilterActionMenuContributionItem(CustomFiltersActionGroup actionGroup, String filterId, String filterName, boolean state, int itemNumber) {
			super(filterId);
			Assert.isNotNull(actionGroup);
			Assert.isNotNull(filterId);
			Assert.isNotNull(filterName);
			fActionGroup= actionGroup;
			fFilterId= filterId;
			fFilterName= filterName;
			fState= state;
			fItemNumber= itemNumber;
		}

		/*
		 * Overrides method from ContributionItem.
		 */
		@Override
		public void fill(Menu menu, int index) {
			MenuItem mi= new MenuItem(menu, SWT.CHECK, index);
			mi.setText("&" + fItemNumber + " " + fFilterName);  //$NON-NLS-1$  //$NON-NLS-2$
			/*
			 * XXX: Don't set the image - would look bad because other menu items don't provide image
			 * XXX: Get working set specific image name from XML - would need to cache icons
			 */
//			mi.setImage(JavaPluginImages.get(JavaPluginImages.IMG_OBJS_JAVA_WORKING_SET));
			mi.setSelection(fState);
			mi.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					fState= !fState;
					fActionGroup.setFilter(fFilterId, fState);
				}
			});
		}

		/*
		 * @see org.eclipse.jface.action.IContributionItem#isDynamic()
		 */
		@Override
		public boolean isDynamic() {
			return true;
		}
	}

	private static final String TAG_CUSTOM_FILTERS = "customFilters"; //$NON-NLS-1$
	private static final String TAG_USER_DEFINED_PATTERNS_ENABLED= "userDefinedPatternsEnabled"; //$NON-NLS-1$
	private static final String TAG_USER_DEFINED_PATTERNS= "userDefinedPatterns"; //$NON-NLS-1$
	private static final String TAG_XML_DEFINED_FILTERS= "xmlDefinedFilters"; //$NON-NLS-1$
	private static final String TAG_LRU_FILTERS = "lastRecentlyUsedFilters"; //$NON-NLS-1$

	private static final String TAG_CHILD= "child"; //$NON-NLS-1$
	private static final String TAG_PATTERN= "pattern"; //$NON-NLS-1$
	private static final String TAG_FILTER_ID= "filterId"; //$NON-NLS-1$
	private static final String TAG_IS_ENABLED= "isEnabled"; //$NON-NLS-1$

	private static final String SEPARATOR= ",";  //$NON-NLS-1$

	private static final int MAX_FILTER_MENU_ENTRIES= 3;
	private static final String RECENT_FILTERS_GROUP_NAME= "recentFiltersGroup"; //$NON-NLS-1$

	private static class FilterItem {
		boolean enabled;
		boolean previouslyEnabled;
		FilterDescriptor descriptor;
		String id;

		private ViewerFilter filterInstance= null;

		public FilterItem(FilterDescriptor descriptor) {
			this.descriptor= descriptor;
			this.id= descriptor.getId();
			this.previouslyEnabled= false;
			this.enabled= descriptor.isEnabled();
		}

		public ViewerFilter getFilterInstance() {
			if (filterInstance == null) {
				filterInstance= descriptor.createViewerFilter();
			}
			return filterInstance;

		}
	}


	private final StructuredViewer fViewer;
	private final NamePatternFilter fPatternFilter;

	private boolean fUserDefinedPatternsEnabled;
	private String[] fUserDefinedPatterns;

	private String[] fPreviousPatterns;

	private final Map<String, FilterItem> fFilterItems;

	/**
	 * Recently changed filter Ids stack with oldest on top (i.e. at the end).
	 *
	 * @since 3.0
	 */
	private Stack<String> fLRUFilterIdsStack;
	/**
	 * Handle to menu manager to dynamically update
	 * the last recently used filters.
	 *
	 * @since 3.0
	 */
	private IMenuManager fMenuManager;
	/**
	 * The menu listener which dynamically updates
	 * the last recently used filters.
	 *
	 * @since 3.0
	 */
	private IMenuListener fMenuListener;
	/**
	 * Filter Ids used in the last view menu invocation.
	 *
	 * @since 3.0
	 */
	private String[] fFilterIdsUsedInLastViewMenu;

	private final String fTargetId;

	/**
	 * Creates a new <code>CustomFiltersActionGroup</code>.
	 *
	 * @param part		the view part that owns this action group
	 * @param viewer	the viewer to be filtered
	 */
	public CustomFiltersActionGroup(IViewPart part, StructuredViewer viewer) {
		this(part.getViewSite().getId(), viewer);
	}

	/**
	 * Creates a new <code>CustomFiltersActionGroup</code>.
	 *
	 * @param ownerId	the id of this action group's owner
	 * @param viewer	the viewer to be filtered
	 */
	public CustomFiltersActionGroup(String ownerId, StructuredViewer viewer) {
		Assert.isNotNull(ownerId);
		Assert.isNotNull(viewer);
		fTargetId= ownerId;
		fViewer= viewer;
		fPatternFilter= new NamePatternFilter();

		fLRUFilterIdsStack= new Stack<String>();

		fUserDefinedPatterns= new String[0];
		fUserDefinedPatternsEnabled= false;
		fPreviousPatterns= new String[0];

		fFilterItems= new HashMap<String, FilterItem>();
		FilterDescriptor[] filterDescriptors= FilterDescriptor.getFilterDescriptors(fTargetId);
		for (int i= 0; i < filterDescriptors.length; i++) {
			FilterItem item= new FilterItem(filterDescriptors[i]);
			Object existing= fFilterItems.put(item.id, item);
			if (existing != null) {
				JavaPlugin.logErrorMessage("WARNING: Duplicate id for extension-point \"org.eclipse.jdt.ui.javaElementFilters\" in " + ownerId); //$NON-NLS-1$
			}
		}

		initializeWithViewDefaults();

		updateViewerFilters();
	}

	/*
	 * Method declared on ActionGroup.
	 */
	@Override
	public void fillActionBars(IActionBars actionBars) {
		fillViewMenu(actionBars.getMenuManager());
	}

	/**
	 * Returns a list of currently enabled filters. The filter
	 * is identified by its id.
	 * <p>
	 * This method is for internal use only and should not
	 * be called by clients outside of JDT/UI.
	 * </p>
	 *
	 * @return a list of currently enabled filters
	 *
	 * @since 3.1
	 *
	 * @noreference This method is not intended to be referenced by clients.
	 */
	public String[] internalGetEnabledFilterIds() {
		ArrayList<String> enabledFilterIds= new ArrayList<String>();
		for (Iterator<FilterItem> iterator= fFilterItems.values().iterator(); iterator.hasNext();) {
			FilterItem item= iterator.next();
			if (item.enabled) {
				enabledFilterIds.add(item.id);
			}
		}
		return enabledFilterIds.toArray(new String[enabledFilterIds.size()]);
	}

	/**
	 * Removes filters for the given parent and element
	 *
	 * @param parent the parent of the element
	 * @param element the element
	 * @param contentProvider the content provider of the viewer from which
	 *  the filters will be removed
	 *
	 * @return the array of new filter ids
	 */
	public String[] removeFiltersFor(Object parent, Object element, IContentProvider contentProvider) {
		ArrayList<String> newFilters= new ArrayList<String>();
		for (Iterator<FilterItem> iterator= fFilterItems.values().iterator(); iterator.hasNext();) {
			FilterItem item= iterator.next();
			if (item.enabled) {
				ViewerFilter filter= item.getFilterInstance();
	            if (filter != null && isSelected(parent, element, contentProvider, filter))
	                newFilters.add(item.id);
			}
		}
		if (fUserDefinedPatternsEnabled && isSelected(parent, element, contentProvider, fPatternFilter))
			newFilters.add(fPatternFilter.getClass().getName());

	    return newFilters.toArray(new String[newFilters.size()]);
	}

	/**
	 * Sets the filters to the given array of new filters
	 *
	 * @param newFilters the new filters
	 */
	public void setFilters(String[] newFilters) {
	    setEnabledFilterIds(newFilters);
	    updateViewerFilters();
	}

	private boolean isSelected(Object parent, Object element, IContentProvider contentProvider, ViewerFilter filter) {
		if (filter instanceof EmptyLibraryContainerFilter) // workaround for https://bugs.eclipse.org/341109
			return true;
	    if (contentProvider instanceof ITreeContentProvider) {
	        // the element and all its parents have to be selected
	        ITreeContentProvider provider = (ITreeContentProvider) contentProvider;
	        while (element != null && !(element instanceof IJavaModel)) {
	            if (!filter.select(fViewer, parent, element))
	                return false;
	            element= provider.getParent( element);
	        }
	        return true;
	    }
	    return filter.select(fViewer, parent, element);
	}

    /**
	 * Sets the enable state of the given filter.
	 *
	 * @param filterId the id of the filter
	 * @param state the filter state
	 */
	private void setFilter(String filterId, boolean state) {
		// Renew filter id in LRU stack
		fLRUFilterIdsStack.remove(filterId);
		fLRUFilterIdsStack.add(0, filterId);

		FilterItem item= fFilterItems.get(filterId);
		if (item != null) {
			item.enabled= state;
			storeViewDefaults();

			updateViewerFilters();
		}
	}

	private void setEnabledFilterIds(String[] enabledIds) {
		// set all to false
		fUserDefinedPatternsEnabled= false;
		for (Iterator<FilterItem> iterator= fFilterItems.values().iterator(); iterator.hasNext();) {
			FilterItem item= iterator.next();
			item.enabled= false;
		}
		// set enabled to true
		for (int i= 0; i < enabledIds.length; i++) {
			FilterItem item= fFilterItems.get(enabledIds[i]);
			if (item != null) {
				item.enabled= true;
			}
			if (fPatternFilter.getClass().getName().equals(enabledIds[i]))
				fUserDefinedPatternsEnabled= true;
		}
	}

	private void setUserDefinedPatterns(String[] patterns) {
		fUserDefinedPatterns= patterns;
	}

	/**
	 * Sets the recently changed filters.
	 *
	 * @param changeHistory the change history
	 * @since 3.0
	 */
	private void setRecentlyChangedFilters(Stack<FilterDescriptor> changeHistory) {
		Stack<String> oldestFirstStack= new Stack<String>();

		int length= Math.min(changeHistory.size(), MAX_FILTER_MENU_ENTRIES);
		for (int i= 0; i < length; i++)
			oldestFirstStack.push(changeHistory.pop().getId());

		length= Math.min(fLRUFilterIdsStack.size(), MAX_FILTER_MENU_ENTRIES - oldestFirstStack.size());
		int NEWEST= 0;
		for (int i= 0; i < length; i++) {
			String filter= fLRUFilterIdsStack.remove(NEWEST);
			if (!oldestFirstStack.contains(filter))
				oldestFirstStack.push(filter);
		}
		fLRUFilterIdsStack= oldestFirstStack;
	}

	private boolean areUserDefinedPatternsEnabled() {
		return fUserDefinedPatternsEnabled;
	}

	private void setUserDefinedPatternsEnabled(boolean state) {
		fUserDefinedPatternsEnabled= state;
	}

	/**
	 * Fills the given view menu with the entries managed by the
	 * group.
	 *
	 * @param viewMenu the menu to fill
	 */
	public void fillViewMenu(IMenuManager viewMenu) {
		/*
		 * Don't change the separator group name.
		 * Using this name ensures that other filters
		 * get contributed to the same group.
		 */
		viewMenu.add(new Separator("filters")); //$NON-NLS-1$
		viewMenu.add(new GroupMarker(RECENT_FILTERS_GROUP_NAME));
		viewMenu.add(new ShowFilterDialogAction());

		fMenuManager= viewMenu;
		fMenuListener= new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				removePreviousLRUFilterActions(manager);
				addLRUFilterActions(manager);
			}
		};
		fMenuManager.addMenuListener(fMenuListener);
	}

	private void removePreviousLRUFilterActions(IMenuManager mm) {
		if (fFilterIdsUsedInLastViewMenu == null)
			return;

		for (int i= 0; i < fFilterIdsUsedInLastViewMenu.length; i++)
			mm.remove(fFilterIdsUsedInLastViewMenu[i]);
	}

	private void addLRUFilterActions(IMenuManager mm) {
		if (fLRUFilterIdsStack.isEmpty()) {
			fFilterIdsUsedInLastViewMenu= null;
			return;
		}

		SortedSet<String> sortedFilters= new TreeSet<String>(fLRUFilterIdsStack);
		String[] recentlyChangedFilterIds= sortedFilters.toArray(new String[sortedFilters.size()]);

		fFilterIdsUsedInLastViewMenu= new String[recentlyChangedFilterIds.length];
		for (int i= 0; i < recentlyChangedFilterIds.length; i++) {
			String id= recentlyChangedFilterIds[i];
			fFilterIdsUsedInLastViewMenu[i]= id;
			FilterItem filterItem= fFilterItems.get(id);
			if (filterItem != null) {
				IContributionItem item= new FilterActionMenuContributionItem(this, id, filterItem.descriptor.getName(), filterItem.enabled, i+1);
				mm.insertBefore(RECENT_FILTERS_GROUP_NAME, item);
			}
		}
	}

	/*
	 * Method declared on ActionGroup.
	 */
	@Override
	public void dispose() {
		if (fMenuManager != null)
			fMenuManager.removeMenuListener(fMenuListener);
		fFilterItems.clear();
		super.dispose();
	}

	// ---------- viewer filter handling ----------

	private boolean updateViewerFilters() {
		ViewerFilter[] installedFilters= fViewer.getFilters();
		ArrayList<ViewerFilter> viewerFilters= new ArrayList<ViewerFilter>(Arrays.asList(installedFilters));
		HashSet<String> patterns= new HashSet<String>();

		boolean hasChange= false;
		boolean patternChange= false;

		for (Iterator<FilterItem> iterator= fFilterItems.values().iterator(); iterator.hasNext();) {
			FilterItem item= iterator.next();
			if (item.descriptor.isCustomFilter()) {
				if (item.enabled != item.previouslyEnabled) {
					ViewerFilter filter= item.getFilterInstance(); // only create when changed
					if (filter != null) {
						if (item.enabled) {
							viewerFilters.add(filter);
						} else {
							viewerFilters.remove(filter);
						}
						hasChange= true;
					}
				}
			} else if (item.descriptor.isPatternFilter()) {
				if (item.enabled) {
					patterns.add(item.descriptor.getPattern());
				}
				patternChange |= (item.enabled != item.previouslyEnabled);
			}
			item.previouslyEnabled= item.enabled;
		}

		if (areUserDefinedPatternsEnabled()) {
			for (int i= 0; i < fUserDefinedPatterns.length; i++) {
				patterns.add(fUserDefinedPatterns[i]);
			}
		}
		if (!patternChange) { // no pattern change so far, test if the user patterns made a difference
			patternChange= hasChanges(patterns, fPreviousPatterns);
		}

		fPreviousPatterns= patterns.toArray(new String[patterns.size()]);
		if (patternChange) {
			fPatternFilter.setPatterns(fPreviousPatterns);
			if (patterns.isEmpty()) {
				viewerFilters.remove(fPatternFilter);
			} else if (!viewerFilters.contains(fPatternFilter)) {
				viewerFilters.add(fPatternFilter);
			}
			hasChange= true;
		}
		if (hasChange) {
			fViewer.setFilters(viewerFilters.toArray(new ViewerFilter[viewerFilters.size()])); // will refresh
		}
		return hasChange;
	}

	private boolean hasChanges(HashSet<String> patterns, String[] oldPatterns) {
		@SuppressWarnings("unchecked")
		HashSet<String> copy= (HashSet<String>) patterns.clone();
		for (int i= 0; i < oldPatterns.length; i++) {
			boolean found= copy.remove(oldPatterns[i]);
			if (!found)
				return true;
		}
		return !copy.isEmpty();
	}

	// ---------- view kind/defaults persistency ----------

	private void initializeWithViewDefaults() {
		// get default values for view
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();

		// see bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=22533
		if (!store.contains(getPreferenceKey(TAG_DUMMY_TO_TEST_EXISTENCE)))
			return;

		fUserDefinedPatternsEnabled= store.getBoolean(getPreferenceKey(TAG_USER_DEFINED_PATTERNS_ENABLED));
		setUserDefinedPatterns(CustomFiltersDialog.convertFromString(store.getString(getPreferenceKey(TAG_USER_DEFINED_PATTERNS)), SEPARATOR));

		for (Iterator<FilterItem> iterator= fFilterItems.values().iterator(); iterator.hasNext();) {
			FilterItem item= iterator.next();
			String id= item.id;
			// set default to value from plugin contributions (fixes https://bugs.eclipse.org/bugs/show_bug.cgi?id=73991 ):
			store.setDefault(id, item.descriptor.isEnabled());
			item.enabled= store.getBoolean(id);
		}

		fLRUFilterIdsStack.clear();
		String lruFilterIds= store.getString(TAG_LRU_FILTERS);
		StringTokenizer tokenizer= new StringTokenizer(lruFilterIds, SEPARATOR);
		while (tokenizer.hasMoreTokens()) {
			String id= tokenizer.nextToken();
			if (fFilterItems.containsKey(id) && !fLRUFilterIdsStack.contains(id))
				fLRUFilterIdsStack.push(id);
		}
	}

	private void storeViewDefaults() {
		// get default values for view
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();

		// see bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=22533
		store.setValue(getPreferenceKey(TAG_DUMMY_TO_TEST_EXISTENCE), "storedViewPreferences");//$NON-NLS-1$

		store.setValue(getPreferenceKey(TAG_USER_DEFINED_PATTERNS_ENABLED), fUserDefinedPatternsEnabled);
		store.setValue(getPreferenceKey(TAG_USER_DEFINED_PATTERNS), CustomFiltersDialog.convertToString(fUserDefinedPatterns ,SEPARATOR));

		for (Iterator<FilterItem> iterator= fFilterItems.values().iterator(); iterator.hasNext();) {
			FilterItem item= iterator.next();
			store.setValue(item.id, item.enabled);
		}

		StringBuffer buf= new StringBuffer(fLRUFilterIdsStack.size() * 20);
		Iterator<String> iter= fLRUFilterIdsStack.iterator();
		while (iter.hasNext()) {
			buf.append(iter.next());
			buf.append(SEPARATOR);
		}
		store.setValue(TAG_LRU_FILTERS, buf.toString());
	}

	private String getPreferenceKey(String tag) {
		return "CustomFiltersActionGroup." + fTargetId + '.' + tag; //$NON-NLS-1$
	}

	// ---------- view instance persistency ----------

	/**
	 * Saves the state of the custom filters in a memento.
	 *
	 * @param memento the memento into which the state is saved
	 */
	public void saveState(IMemento memento) {
		IMemento customFilters= memento.createChild(TAG_CUSTOM_FILTERS);
		customFilters.putString(TAG_USER_DEFINED_PATTERNS_ENABLED, Boolean.toString(fUserDefinedPatternsEnabled));
		saveUserDefinedPatterns(customFilters);
		saveXmlDefinedFilters(customFilters);
		saveLRUFilters(customFilters);
	}

	private void saveXmlDefinedFilters(IMemento memento) {
		IMemento xmlDefinedFilters= memento.createChild(TAG_XML_DEFINED_FILTERS);

		for (Iterator<FilterItem> iterator= fFilterItems.values().iterator(); iterator.hasNext();) {
			FilterItem item= iterator.next();

			IMemento child= xmlDefinedFilters.createChild(TAG_CHILD);
			child.putString(TAG_FILTER_ID, item.id);
			child.putString(TAG_IS_ENABLED, String.valueOf(item.enabled));
		}
	}
	/**
	 * Stores the last recently used filter Ids into
	 * the given memento
	 *
	 * @param memento the memento into which to store the LRU filter Ids
	 * @since 3.0
	 */
	private void saveLRUFilters(IMemento memento) {
		if(fLRUFilterIdsStack != null && !fLRUFilterIdsStack.isEmpty()) {
			IMemento lruFilters= memento.createChild(TAG_LRU_FILTERS);
			Iterator<String> iter= fLRUFilterIdsStack.iterator();
			while (iter.hasNext()) {
				String id= iter.next();
				IMemento child= lruFilters.createChild(TAG_CHILD);
				child.putString(TAG_FILTER_ID, id);
			}
		}
	}

	private void saveUserDefinedPatterns(IMemento memento) {
		if(fUserDefinedPatterns != null && fUserDefinedPatterns.length > 0) {
			IMemento userDefinedPatterns= memento.createChild(TAG_USER_DEFINED_PATTERNS);
			for (int i= 0; i < fUserDefinedPatterns.length; i++) {
				IMemento child= userDefinedPatterns.createChild(TAG_CHILD);
				child.putString(TAG_PATTERN, fUserDefinedPatterns[i]);
			}
		}
	}

	/**
	 * Restores the state of the filter actions from a memento.
	 * <p>
	 * Note: This method does not refresh the viewer.
	 * </p>
	 *
	 * @param memento the memento from which the state is restored
	 */
	public void restoreState(IMemento memento) {
		if (memento == null)
			return;
		IMemento customFilters= memento.getChild(TAG_CUSTOM_FILTERS);
		if (customFilters == null)
			return;
		String userDefinedPatternsEnabled= customFilters.getString(TAG_USER_DEFINED_PATTERNS_ENABLED);
		if (userDefinedPatternsEnabled == null)
			return;

		fUserDefinedPatternsEnabled= Boolean.valueOf(userDefinedPatternsEnabled).booleanValue();
		restoreUserDefinedPatterns(customFilters);
		restoreXmlDefinedFilters(customFilters);
		restoreLRUFilters(customFilters);

		updateViewerFilters();
	}

	private void restoreUserDefinedPatterns(IMemento memento) {
		IMemento userDefinedPatterns= memento.getChild(TAG_USER_DEFINED_PATTERNS);
		if(userDefinedPatterns != null) {
			IMemento children[]= userDefinedPatterns.getChildren(TAG_CHILD);
			String[] patterns= new String[children.length];
			for (int i = 0; i < children.length; i++)
				patterns[i]= children[i].getString(TAG_PATTERN);

			setUserDefinedPatterns(patterns);
		} else
			setUserDefinedPatterns(new String[0]);
	}

	private void restoreXmlDefinedFilters(IMemento memento) {
		IMemento xmlDefinedFilters= memento.getChild(TAG_XML_DEFINED_FILTERS);
		if(xmlDefinedFilters != null) {
			IMemento[] children= xmlDefinedFilters.getChildren(TAG_CHILD);
			for (int i= 0; i < children.length; i++) {
				String id= children[i].getString(TAG_FILTER_ID);
				Boolean isEnabled= Boolean.valueOf(children[i].getString(TAG_IS_ENABLED));
				FilterItem item= fFilterItems.get(id);
				if (item != null) {
					item.enabled= isEnabled.booleanValue();
				}
			}
		}
	}

	private void restoreLRUFilters(IMemento memento) {
		IMemento lruFilters= memento.getChild(TAG_LRU_FILTERS);
		fLRUFilterIdsStack.clear();
		if(lruFilters != null) {
			IMemento[] children= lruFilters.getChildren(TAG_CHILD);
			for (int i= 0; i < children.length; i++) {
				String id= children[i].getString(TAG_FILTER_ID);
				if (fFilterItems.containsKey(id) && !fLRUFilterIdsStack.contains(id))
					fLRUFilterIdsStack.push(id);
			}
		}
	}

	// ---------- dialog related code ----------

	private void openDialog() {
		CustomFiltersDialog dialog= new CustomFiltersDialog(
			fViewer.getControl().getShell(),
			fTargetId,
			areUserDefinedPatternsEnabled(),
			fUserDefinedPatterns,
			internalGetEnabledFilterIds());

		if (dialog.open() == Window.OK) {
			setEnabledFilterIds(dialog.getEnabledFilterIds());
			setUserDefinedPatternsEnabled(dialog.areUserDefinedPatternsEnabled());
			setUserDefinedPatterns(dialog.getUserDefinedPatterns());
			setRecentlyChangedFilters(dialog.getFilterDescriptorChangeHistory());

			storeViewDefaults();

			updateViewerFilters();
		}
	}
}

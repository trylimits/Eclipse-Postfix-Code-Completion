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
package org.eclipse.jdt.internal.ui.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ibm.icu.text.Collator;

import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.dialogs.SelectionStatusDialog;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.CheckedListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;

public class CategoryFilterActionGroup extends ActionGroup {

	private class CategoryFilter extends ViewerFilter {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			if (element instanceof IMember) {
				IMember member= (IMember)element;
				try {
					String[] categories= member.getCategories();
					if (categories.length == 0)
						return !fFilterUncategorizedMembers;

					for (int i= 0; i < categories.length; i++) {
						if (!fFilteredCategories.contains(categories[i]))
							return true;
					}
					return false;
				} catch (JavaModelException e) {
					JavaPlugin.log(e);
				}
			}
			return true;
		}

	}

	private class CategoryFilterSelectionDialog extends SelectionStatusDialog implements IListAdapter<String> {

		private static final int SELECT_ALL= 0;
		private static final int DESELECT_ALL= 1;

		private final CheckedListDialogField<String> fCategoryList;

		public CategoryFilterSelectionDialog(Shell parent, List<String> categories, List<String> selectedCategories) {
			super(parent);

			setTitle(ActionMessages.CategoryFilterActionGroup_JavaCategoryFilter_title);

			String[] buttons= {
					ActionMessages.CategoryFilterActionGroup_SelectAllCategories,
					ActionMessages.CategoryFilterActionGroup_DeselectAllCategories
					};

			fCategoryList= new CheckedListDialogField<String>(this, buttons, new ILabelProvider() {
							public Image getImage(Object element) {return null;}
							public String getText(Object element) {return (String)element;}
							public void addListener(ILabelProviderListener listener) {}
							public void dispose() {}
							public boolean isLabelProperty(Object element, String property) {return false;}
							public void removeListener(ILabelProviderListener listener) {}
						});
			fCategoryList.addElements(categories);
			fCategoryList.setViewerComparator(new ViewerComparator());
			fCategoryList.setLabelText(ActionMessages.CategoryFilterActionGroup_SelectCategoriesDescription);
			fCategoryList.checkAll(true);
			for (Iterator<String> iter= selectedCategories.iterator(); iter.hasNext();) {
				String selected= iter.next();
				fCategoryList.setChecked(selected, false);
			}
			if (categories.size() == 0) {
				fCategoryList.setEnabled(false);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		protected Control createDialogArea(Composite parent) {
			Composite composite= (Composite) super.createDialogArea(parent);
			LayoutUtil.doDefaultLayout(composite, new DialogField[] { fCategoryList }, true, 5, 5);
			LayoutUtil.setHorizontalGrabbing(fCategoryList.getListControl(null));
			Dialog.applyDialogFont(composite);
			setHelpAvailable(false);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, IJavaHelpContextIds.VISIBLE_CATEGORIES_DIALOG);
			return composite;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		protected void computeResult() {
			setResult(fCategoryList.getCheckedElements());
		}

		/**
		 * {@inheritDoc}
		 */
		public void customButtonPressed(ListDialogField<String> field, int index) {
			if (index == SELECT_ALL) {
				fCategoryList.checkAll(true);
				fCategoryList.refresh();
			} else if (index == DESELECT_ALL) {
				fCategoryList.checkAll(false);
				fCategoryList.refresh();
			}
		}

		public void doubleClicked(ListDialogField<String> field) {
			List<?> selectedElements= field.getSelectedElements();
			if (selectedElements.size() == 1) {
				String selected= (String) selectedElements.get(0);
				fCategoryList.setChecked(selected, !fCategoryList.isChecked(selected));
			}
		}
		public void selectionChanged(ListDialogField<String> field) {}
	}

	private class CategoryFilterMenuAction extends Action {

		public CategoryFilterMenuAction() {
			setDescription(ActionMessages.CategoryFilterActionGroup_ShowCategoriesActionDescription);
			setToolTipText(ActionMessages.CategoryFilterActionGroup_ShowCategoriesToolTip);
			setText(ActionMessages.CategoryFilterActionGroup_ShowCategoriesLabel);
			JavaPluginImages.setLocalImageDescriptors(this, "category_menu.gif"); //$NON-NLS-1$
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void run() {
			showCategorySelectionDialog(fInputElement);
		}

	}

	private class CategoryFilterAction extends Action {

		private final String fCategory;

		public CategoryFilterAction(String category, int count) {
			fCategory= category;
			StringBuffer buf = new StringBuffer();
			buf.append('&').append(count).append(' ').append(fCategory);
			setText(buf.toString());
			setChecked(!fFilteredCategories.contains(fCategory));
			setId(FILTER_CATEGORY_ACTION_ID);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void run() {
			super.run();
			if (fFilteredCategories.contains(fCategory)) {
				fFilteredCategories.remove(fCategory);
			} else {
				fFilteredCategories.add(fCategory);
			}
			fLRUList.put(fCategory, fCategory);
			storeSettings();
			fireSelectionChange();
		}

	}

	private class FilterUncategorizedMembersAction extends Action {

		public FilterUncategorizedMembersAction() {
			setText(ActionMessages.CategoryFilterActionGroup_ShowUncategorizedMembers);
			setChecked(!fFilterUncategorizedMembers);
			setId(FILTER_CATEGORY_ACTION_ID);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void run() {
			fFilterUncategorizedMembers= !fFilterUncategorizedMembers;
			storeSettings();
			fireSelectionChange();
		}
	}

	private interface IResultCollector {
		public boolean accept(String[] category);
	}

	private static int COUNTER= 0;//WORKAROUND for Bug 132669 https://bugs.eclipse.org/bugs/show_bug.cgi?id=132669

	private static final String FILTER_CATEGORY_ACTION_ID= "FilterCategoryActionId"; //$NON-NLS-1$
	private final String CATEGORY_MENU_GROUP_NAME= "CategoryMenuGroup" + (COUNTER++); //$NON-NLS-1$
	private static final int MAX_NUMBER_OF_CATEGORIES_IN_MENU= 5;

	private final StructuredViewer fViewer;
	private final String fViewerId;
	private final CategoryFilter fFilter;
	private final HashSet<String> fFilteredCategories;
	private IJavaElement[] fInputElement;
	private final CategoryFilterMenuAction fMenuAction;
	private IMenuManager fMenuManager;
	private IMenuListener fMenuListener;
	private final LinkedHashMap<String, String> fLRUList;
	private boolean fFilterUncategorizedMembers;

	public CategoryFilterActionGroup(final StructuredViewer viewer, final String viewerId, IJavaElement[] input) {
		Assert.isLegal(viewer != null);
		Assert.isLegal(viewerId != null);
		Assert.isLegal(input != null);

		fLRUList= new LinkedHashMap<String, String>(MAX_NUMBER_OF_CATEGORIES_IN_MENU * 2, 0.75f, true) {
			private static final long serialVersionUID= 1L;
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
				return size() > MAX_NUMBER_OF_CATEGORIES_IN_MENU;
			}
		};
		fViewer= viewer;
		fViewerId= viewerId;
		fInputElement= input;

		fFilter= new CategoryFilter();

		fFilteredCategories= new HashSet<String>();
		loadSettings();

		fMenuAction= new CategoryFilterMenuAction();

		fViewer.addFilter(fFilter);
	}

	public void setInput(IJavaElement[] input) {
		Assert.isLegal(input != null);
		fInputElement= input;
	}

	private void loadSettings() {
		fFilteredCategories.clear();
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		String string= store.getString(getPreferenceKey());
		if (string != null && string.length() > 0) {
			String[] categories= string.split(";"); //$NON-NLS-1$
			for (int i= 0; i < categories.length; i++) {
				fFilteredCategories.add(categories[i]);
			}
		}
		string= store.getString(getPreferenceKey()+".LRU"); //$NON-NLS-1$
		if (string != null && string.length() > 0) {
			String[] categories= string.split(";"); //$NON-NLS-1$
			for (int i= categories.length - 1; i >= 0; i--) {
				fLRUList.put(categories[i], categories[i]);
			}
		}
		fFilterUncategorizedMembers= store.getBoolean(getPreferenceKey()+".FilterUncategorized"); //$NON-NLS-1$
	}

	private void storeSettings() {
		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		if (fFilteredCategories.size() == 0) {
			store.setValue(getPreferenceKey(), ""); //$NON-NLS-1$
		} else {
			StringBuffer buf= new StringBuffer();
			Iterator<String> iter= fFilteredCategories.iterator();
			String element= iter.next();
			buf.append(element);
			while (iter.hasNext()) {
				element= iter.next();
				buf.append(';');
				buf.append(element);
			}
			store.setValue(getPreferenceKey(), buf.toString());
			buf= new StringBuffer();
			iter= fLRUList.values().iterator();
			element= iter.next();
			buf.append(element);
			while (iter.hasNext()) {
				element= iter.next();
				buf.append(';');
				buf.append(element);
			}
			store.setValue(getPreferenceKey()+".LRU", buf.toString()); //$NON-NLS-1$
			store.setValue(getPreferenceKey()+".FilterUncategorized", fFilterUncategorizedMembers); //$NON-NLS-1$
		}
	}

	public void contributeToViewMenu(IMenuManager menuManager) {
		menuManager.add(new Separator(CATEGORY_MENU_GROUP_NAME));
		menuManager.appendToGroup(CATEGORY_MENU_GROUP_NAME, fMenuAction);
		fMenuListener= new IMenuListener() {
					public void menuAboutToShow(IMenuManager manager) {
						if (!manager.isVisible())
							return;
						updateMenu(manager);
					}
				};
		menuManager.addMenuListener(fMenuListener);
		fMenuManager= menuManager;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dispose() {
		super.dispose();
		if (fMenuManager != null) {
			fMenuManager.removeMenuListener(fMenuListener);
			fMenuManager= null;
			fMenuListener= null;
		}
	}

	private void updateMenu(IMenuManager manager) {
		IContributionItem[] items= manager.getItems();
		if (items != null) {
			for (int i= 0; i < items.length; i++) {
				IContributionItem item= items[i];
				if (item != null && item.getId() != null && item.getId().equals(FILTER_CATEGORY_ACTION_ID)) {
					IContributionItem removed= manager.remove(item);
					if (removed != null) {
						item.dispose();
					}
				}
			}
		}
		List<String> menuEntries= new ArrayList<String>();
		boolean hasUncategorizedMembers= getMenuCategories(menuEntries);
		Collections.sort(menuEntries, Collator.getInstance());

		if (menuEntries.size() > 0 && hasUncategorizedMembers)
			manager.appendToGroup(CATEGORY_MENU_GROUP_NAME, new FilterUncategorizedMembersAction());

		int count= 0;
		for (Iterator<String> iter= menuEntries.iterator(); iter.hasNext();) {
			String category= iter.next();
			manager.appendToGroup(CATEGORY_MENU_GROUP_NAME, new CategoryFilterAction(category, count + 1));
			count++;
		}
	}

	private boolean getMenuCategories(List<String> result) {
		final HashSet<String> categories= new HashSet<String>();
		final HashSet<String> foundLRUCategories= new HashSet<String>();
		final boolean hasUncategorizedMember[]= new boolean[] {false};
		for (int i= 0; i < fInputElement.length && !(hasUncategorizedMember[0] && foundLRUCategories.size() >= MAX_NUMBER_OF_CATEGORIES_IN_MENU); i++) {
			collectCategories(fInputElement[i], new IResultCollector() {
				public boolean accept(String[] cats) {
					if (cats.length > 0) {
						for (int j= 0; j < cats.length; j++) {
							String category= cats[j];
							categories.add(category);
							if (fLRUList.containsKey(category)) {
								foundLRUCategories.add(category);
							}
						}
					} else {
						hasUncategorizedMember[0]= true;
					}
					return hasUncategorizedMember[0] && foundLRUCategories.size() >= MAX_NUMBER_OF_CATEGORIES_IN_MENU;
				}
			});
		}
		int count= 0;
		for (Iterator<String> iter= foundLRUCategories.iterator(); iter.hasNext();) {
			String element= iter.next();
			result.add(element);
			count++;
		}
		if (count < MAX_NUMBER_OF_CATEGORIES_IN_MENU) {
			List<String> sortedCategories= new ArrayList<String>(categories);
			Collections.sort(sortedCategories, Collator.getInstance());
			for (Iterator<String> iter= sortedCategories.iterator(); iter.hasNext() && count < MAX_NUMBER_OF_CATEGORIES_IN_MENU;) {
				String element= iter.next();
				if (!foundLRUCategories.contains(element)) {
					result.add(element);
					count++;
				}
			}
		}
		return hasUncategorizedMember[0];
	}

	private boolean collectCategories(IJavaElement element, IResultCollector collector) {//HashSet result, int max, LinkedHashMap lruList) {
		try {
			if (element instanceof IMember) {
				IMember member= (IMember)element;
				collector.accept(member.getCategories());
				return processChildren(member.getChildren(), collector);
			} else if (element instanceof ICompilationUnit) {
				return processChildren(((ICompilationUnit)element).getChildren(), collector);
			} else if (element instanceof IClassFile) {
				return processChildren(((IClassFile)element).getChildren(), collector);
			} else if (element instanceof IJavaModel) {
				return processChildren(((IJavaModel)element).getChildren(), collector);
			} else if (element instanceof IJavaProject) {
				return processChildren(((IJavaProject)element).getChildren(), collector);
			} else if (element instanceof IPackageFragment) {
				return processChildren(((IPackageFragment)element).getChildren(), collector);
			} else if (element instanceof IPackageFragmentRoot)	 {
				return processChildren(((IPackageFragmentRoot)element).getChildren(), collector);
			}
			return false;
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
			return true;
		}
	}

	private boolean processChildren(IJavaElement[] children, IResultCollector collector) {
		for (int i= 0; i < children.length; i++) {
			if (collectCategories(children[i], collector))
				return true;
		}
		return false;
	}

	private void fireSelectionChange() {
		fViewer.getControl().setRedraw(false);
		BusyIndicator.showWhile(fViewer.getControl().getDisplay(), new Runnable() {
			public void run() {
				fViewer.refresh();
			}
		});
		fViewer.getControl().setRedraw(true);
	}

	private String getPreferenceKey() {
		return "CategoryFilterActionGroup." + fViewerId; //$NON-NLS-1$
	}

	private void showCategorySelectionDialog(IJavaElement[] input) {
		final HashSet<String> categories= new HashSet<String>();
		for (int i= 0; i < input.length; i++) {
			collectCategories(input[i], new IResultCollector() {
				public boolean accept(String[] cats) {
					for (int j= 0; j < cats.length; j++) {
						categories.add(cats[j]);
					}
					return false;
				}
			});
		}
		CategoryFilterSelectionDialog dialog= new CategoryFilterSelectionDialog(fViewer.getControl().getShell(), new ArrayList<String>(categories), new ArrayList<String>(fFilteredCategories));
		if (dialog.open() == Window.OK) {
			Object[] selected= dialog.getResult();
			for (Iterator<String> iter= categories.iterator(); iter.hasNext();) {
				String category= iter.next();
				if (contains(selected, category)) {
					if (fFilteredCategories.remove(category))
						fLRUList.put(category, category);
				} else {
					if (fFilteredCategories.add(category))
						fLRUList.put(category, category);
				}
			}
			storeSettings();
			fireSelectionChange();
		}
	}

	private boolean contains(Object[] selected, String category) {
		for (int i= 0; i < selected.length; i++) {
			if (selected[i].equals(category))
				return true;
		}
		return false;
	}

}

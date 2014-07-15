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
package org.eclipse.jdt.internal.ui.search;

import java.util.HashMap;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Tree;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.IShowInTargetList;
import org.eclipse.ui.part.ResourceTransfer;
import org.eclipse.ui.views.navigator.LocalSelectionTransfer;

import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.search.ui.IContextMenuConstants;
import org.eclipse.search.ui.ISearchResultViewPart;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.AbstractTextSearchViewPage;
import org.eclipse.search.ui.text.Match;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;
import org.eclipse.jdt.ui.search.IMatchPresentation;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.actions.CopyQualifiedNameAction;
import org.eclipse.jdt.internal.ui.dnd.EditorInputTransferDragAdapter;
import org.eclipse.jdt.internal.ui.dnd.JdtViewerDragAdapter;
import org.eclipse.jdt.internal.ui.dnd.ResourceTransferDragAdapter;
import org.eclipse.jdt.internal.ui.packageview.SelectionTransferDragAdapter;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.DecoratingJavaLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.ProblemTableViewer;
import org.eclipse.jdt.internal.ui.viewsupport.ProblemTreeViewer;


public class JavaSearchResultPage extends AbstractTextSearchViewPage implements IAdaptable {

	public static class DecoratorIgnoringViewerSorter extends ViewerComparator {

		private final ILabelProvider fLabelProvider;

		public DecoratorIgnoringViewerSorter(ILabelProvider labelProvider) {
			fLabelProvider= labelProvider;
		}

		@Override
		public int category(Object element) {
			if (element instanceof IJavaElement || element instanceof IResource)
				return 1;
			return 2;
		}


	    @Override
		public int compare(Viewer viewer, Object e1, Object e2) {
	        int cat1 = category(e1);
	        int cat2 = category(e2);

	        if (cat1 != cat2) {
				return cat1 - cat2;
			}
	        String name1= fLabelProvider.getText(e1);
	        String name2= fLabelProvider.getText(e2);
	        if (name1 == null)
	            name1 = "";//$NON-NLS-1$
	        if (name2 == null)
	            name2 = "";//$NON-NLS-1$
	        return getComparator().compare(name1, name2);
	    }
	}


	private static final int DEFAULT_ELEMENT_LIMIT = 1000;
	private static final String FALSE = "FALSE"; //$NON-NLS-1$
	private static final String TRUE = "TRUE"; //$NON-NLS-1$
	private static final String KEY_GROUPING= "org.eclipse.jdt.search.resultpage.grouping"; //$NON-NLS-1$
	private static final String KEY_SORTING= "org.eclipse.jdt.search.resultpage.sorting"; //$NON-NLS-1$
	private static final String KEY_LIMIT_ENABLED= "org.eclipse.jdt.search.resultpage.limit_enabled"; //$NON-NLS-1$
	private static final String KEY_LIMIT= "org.eclipse.jdt.search.resultpage.limit"; //$NON-NLS-1$

	private static final String GROUP_GROUPING= "org.eclipse.jdt.search.resultpage.grouping"; //$NON-NLS-1$
	private static final String GROUP_FILTERING = "org.eclipse.jdt.search.resultpage.filtering"; //$NON-NLS-1$

	private NewSearchViewActionGroup fActionGroup;
	private JavaSearchContentProvider fContentProvider;
	private int fCurrentSortOrder;
	private SortAction fSortByNameAction;
	private SortAction fSortByParentName;
	private SortAction fSortByPathAction;

	private GroupAction fGroupTypeAction;
	private GroupAction fGroupFileAction;
	private GroupAction fGroupPackageAction;
	private GroupAction fGroupProjectAction;

	private SelectionDispatchAction fCopyQualifiedNameAction;

	private SortingLabelProvider fSortingLabelProvider;

	private int fCurrentGrouping;

	private static final String[] SHOW_IN_TARGETS= new String[] { JavaUI.ID_PACKAGES , JavaPlugin.ID_RES_NAV };
	public static final IShowInTargetList SHOW_IN_TARGET_LIST= new IShowInTargetList() {
		public String[] getShowInTargetIds() {
			return SHOW_IN_TARGETS;
		}
	};

	private JavaSearchEditorOpener fEditorOpener= new JavaSearchEditorOpener();

	public JavaSearchResultPage() {
		fCopyQualifiedNameAction= null;

		initSortActions();
		initGroupingActions();
		setElementLimit(new Integer(DEFAULT_ELEMENT_LIMIT));
	}

	private void initSortActions() {
		fSortByNameAction= new SortAction(SearchMessages.JavaSearchResultPage_sortByName, this, SortingLabelProvider.SHOW_ELEMENT_CONTAINER);
		fSortByPathAction= new SortAction(SearchMessages.JavaSearchResultPage_sortByPath, this, SortingLabelProvider.SHOW_PATH);
		fSortByParentName= new SortAction(SearchMessages.JavaSearchResultPage_sortByParentName, this, SortingLabelProvider.SHOW_CONTAINER_ELEMENT);
	}

	private void initGroupingActions() {
		fGroupProjectAction= new GroupAction(SearchMessages.JavaSearchResultPage_groupby_project, SearchMessages.JavaSearchResultPage_groupby_project_tooltip, this, LevelTreeContentProvider.LEVEL_PROJECT);
		JavaPluginImages.setLocalImageDescriptors(fGroupProjectAction, "prj_mode.gif"); //$NON-NLS-1$
		fGroupPackageAction= new GroupAction(SearchMessages.JavaSearchResultPage_groupby_package, SearchMessages.JavaSearchResultPage_groupby_package_tooltip, this, LevelTreeContentProvider.LEVEL_PACKAGE);
		JavaPluginImages.setLocalImageDescriptors(fGroupPackageAction, "package_mode.gif"); //$NON-NLS-1$
		fGroupFileAction= new GroupAction(SearchMessages.JavaSearchResultPage_groupby_file, SearchMessages.JavaSearchResultPage_groupby_file_tooltip, this, LevelTreeContentProvider.LEVEL_FILE);
		JavaPluginImages.setLocalImageDescriptors(fGroupFileAction, "file_mode.gif"); //$NON-NLS-1$
		fGroupTypeAction= new GroupAction(SearchMessages.JavaSearchResultPage_groupby_type, SearchMessages.JavaSearchResultPage_groupby_type_tooltip, this, LevelTreeContentProvider.LEVEL_TYPE);
		JavaPluginImages.setLocalImageDescriptors(fGroupTypeAction, "type_mode.gif"); //$NON-NLS-1$
	}

	@Override
	public void setViewPart(ISearchResultViewPart part) {
		super.setViewPart(part);
		fActionGroup= new NewSearchViewActionGroup(part);
	}

	@Override
	public void showMatch(Match match, int offset, int length, boolean activate) throws PartInitException {
		IEditorPart editor= fEditorOpener.openMatch(match);

		if (editor != null && activate)
			editor.getEditorSite().getPage().activate(editor);
		Object element= match.getElement();
		if (editor instanceof ITextEditor) {
			ITextEditor textEditor= (ITextEditor) editor;
			textEditor.selectAndReveal(offset, length);
		} else if (editor != null) {
			if (element instanceof IFile) {
				IFile file= (IFile) element;
				showWithMarker(editor, file, offset, length);
			}
		} else if (getInput() instanceof JavaSearchResult) {
			JavaSearchResult result= (JavaSearchResult) getInput();
			IMatchPresentation participant= result.getSearchParticpant(element);
			if (participant != null)
				participant.showMatch(match, offset, length, activate);
		}
	}

	private void showWithMarker(IEditorPart editor, IFile file, int offset, int length) throws PartInitException {
		try {
			IMarker marker= file.createMarker(NewSearchUI.SEARCH_MARKER);
			HashMap<String, Integer> attributes= new HashMap<String, Integer>(4);
			attributes.put(IMarker.CHAR_START, new Integer(offset));
			attributes.put(IMarker.CHAR_END, new Integer(offset + length));
			marker.setAttributes(attributes);
			IDE.gotoMarker(editor, marker);
			marker.delete();
		} catch (CoreException e) {
			throw new PartInitException(SearchMessages.JavaSearchResultPage_error_marker, e);
		}
	}

	private SelectionDispatchAction getCopyQualifiedNameAction() {
		if (fCopyQualifiedNameAction == null) {
			fCopyQualifiedNameAction= new CopyQualifiedNameAction(getSite());
			fCopyQualifiedNameAction.setActionDefinitionId(CopyQualifiedNameAction.ACTION_DEFINITION_ID);
		}
		return fCopyQualifiedNameAction;
	}

	@Override
	protected void fillContextMenu(IMenuManager mgr) {
		super.fillContextMenu(mgr);
		addSortActions(mgr);

		mgr.appendToGroup(IContextMenuConstants.GROUP_EDIT, getCopyQualifiedNameAction());

		fActionGroup.setContext(new ActionContext(getSite().getSelectionProvider().getSelection()));
		fActionGroup.fillContextMenu(mgr);
	}

	private void addSortActions(IMenuManager mgr) {
		if (getLayout() != FLAG_LAYOUT_FLAT)
			return;
		MenuManager sortMenu= new MenuManager(SearchMessages.JavaSearchResultPage_sortBylabel);
		sortMenu.add(fSortByNameAction);
		sortMenu.add(fSortByPathAction);
		sortMenu.add(fSortByParentName);

		fSortByNameAction.setChecked(fCurrentSortOrder == fSortByNameAction.getSortOrder());
		fSortByPathAction.setChecked(fCurrentSortOrder == fSortByPathAction.getSortOrder());
		fSortByParentName.setChecked(fCurrentSortOrder == fSortByParentName.getSortOrder());

		mgr.appendToGroup(IContextMenuConstants.GROUP_VIEWER_SETUP, sortMenu);
	}

	@Override
	protected void fillToolbar(IToolBarManager tbm) {
		super.fillToolbar(tbm);

		IActionBars actionBars = getSite().getActionBars();
		if (actionBars != null) {
			actionBars.setGlobalActionHandler(CopyQualifiedNameAction.ACTION_HANDLER_ID, getCopyQualifiedNameAction());
		}

		if (getLayout() != FLAG_LAYOUT_FLAT)
			addGroupActions(tbm);
	}

	private void addGroupActions(IToolBarManager mgr) {
		mgr.appendToGroup(IContextMenuConstants.GROUP_VIEWER_SETUP, new Separator(GROUP_GROUPING));
		mgr.appendToGroup(GROUP_GROUPING, fGroupProjectAction);
		mgr.appendToGroup(GROUP_GROUPING, fGroupPackageAction);
		mgr.appendToGroup(GROUP_GROUPING, fGroupFileAction);
		mgr.appendToGroup(GROUP_GROUPING, fGroupTypeAction);

		updateGroupingActions();
	}


	private void updateGroupingActions() {
		fGroupProjectAction.setChecked(fCurrentGrouping == LevelTreeContentProvider.LEVEL_PROJECT);
		fGroupPackageAction.setChecked(fCurrentGrouping == LevelTreeContentProvider.LEVEL_PACKAGE);
		fGroupFileAction.setChecked(fCurrentGrouping == LevelTreeContentProvider.LEVEL_FILE);
		fGroupTypeAction.setChecked(fCurrentGrouping == LevelTreeContentProvider.LEVEL_TYPE);
	}


	@Override
	public void dispose() {
		fActionGroup.dispose();
		super.dispose();
	}

	@Override
	protected void elementsChanged(Object[] objects) {
		if (fContentProvider != null)
			fContentProvider.elementsChanged(objects);
	}

	@Override
	protected void clear() {
		if (fContentProvider != null)
			fContentProvider.clear();
	}

	private void addDragAdapters(StructuredViewer viewer) {
		Transfer[] transfers= new Transfer[] { LocalSelectionTransfer.getInstance(), ResourceTransfer.getInstance() };
		int ops= DND.DROP_COPY | DND.DROP_LINK;

		JdtViewerDragAdapter dragAdapter= new JdtViewerDragAdapter(viewer);
		dragAdapter.addDragSourceListener(new SelectionTransferDragAdapter(viewer));
		dragAdapter.addDragSourceListener(new EditorInputTransferDragAdapter(viewer));
		dragAdapter.addDragSourceListener(new ResourceTransferDragAdapter(viewer));
		viewer.addDragSupport(ops, transfers, dragAdapter);
	}

	@Override
	protected void configureTableViewer(TableViewer viewer) {
		viewer.setUseHashlookup(true);
		fSortingLabelProvider= new SortingLabelProvider(this);
		viewer.setLabelProvider(new DecoratingJavaLabelProvider(fSortingLabelProvider, false));
		fContentProvider=new JavaSearchTableContentProvider(this);
		viewer.setContentProvider(fContentProvider);
		viewer.setComparator(new DecoratorIgnoringViewerSorter(fSortingLabelProvider));
		setSortOrder(fCurrentSortOrder);
		addDragAdapters(viewer);
	}

	@Override
	protected void configureTreeViewer(TreeViewer viewer) {
		PostfixLabelProvider postfixLabelProvider= new PostfixLabelProvider(this);
		viewer.setUseHashlookup(true);
		viewer.setComparator(new DecoratorIgnoringViewerSorter(postfixLabelProvider));
		viewer.setLabelProvider(new DecoratingJavaLabelProvider(postfixLabelProvider, false));
		fContentProvider= new LevelTreeContentProvider(this, fCurrentGrouping);
		viewer.setContentProvider(fContentProvider);
		addDragAdapters(viewer);
	}

	@Override
	protected TreeViewer createTreeViewer(Composite parent) {
		return new ProblemTreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
	}

	@Override
	protected TableViewer createTableViewer(Composite parent) {
		return new ProblemTableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
	}

	void setSortOrder(int order) {
		if (fSortingLabelProvider != null) {
			fCurrentSortOrder= order;
			StructuredViewer viewer= getViewer();
			//viewer.getControl().setRedraw(false);
			fSortingLabelProvider.setOrder(order);
			//viewer.getControl().setRedraw(true);
			viewer.refresh();
			getSettings().put(KEY_SORTING, fCurrentSortOrder);
		}
	}

	@Override
	public void init(IPageSite site) {
		super.init(site);
		IMenuManager menuManager = site.getActionBars().getMenuManager();
		menuManager.insertBefore(IContextMenuConstants.GROUP_PROPERTIES, new Separator(GROUP_FILTERING));
		fActionGroup.fillActionBars(site.getActionBars());
		menuManager.appendToGroup(IContextMenuConstants.GROUP_PROPERTIES, new Action(SearchMessages.JavaSearchResultPage_preferences_label) {
			@Override
			public void run() {
				String pageId= "org.eclipse.search.preferences.SearchPreferencePage"; //$NON-NLS-1$
				String[] displayedPages= { pageId,
						"org.eclipse.ui.editors.preferencePages.Annotations", //$NON-NLS-1$
						"org.eclipse.ui.preferencePages.ColorsAndFonts" //$NON-NLS-1$
				};
				PreferencesUtil.createPreferenceDialogOn(JavaPlugin.getActiveWorkbenchShell(), pageId, displayedPages, null).open();
			}
		});
	}

	/**
	 * Precondition here: the viewer must be showing a tree with a LevelContentProvider.
	 *
	 * @param grouping the grouping which must be one of the <code>LEVEL_*</code> constants from
	 *            {@link LevelTreeContentProvider}
	 */
	void setGrouping(int grouping) {
		fCurrentGrouping= grouping;
		StructuredViewer viewer= getViewer();
		LevelTreeContentProvider cp= (LevelTreeContentProvider) viewer.getContentProvider();
		cp.setLevel(grouping);
		updateGroupingActions();
		getSettings().put(KEY_GROUPING, fCurrentGrouping);
		getViewPart().updateLabel();
	}

	@Override
	protected StructuredViewer getViewer() {
		// override so that it's visible in the package.
		return super.getViewer();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.search.ui.text.AbstractTextSearchViewPage#restoreState(org.eclipse.ui.IMemento)
	 */
	@Override
	public void restoreState(IMemento memento) {
		super.restoreState(memento);

		int sortOrder= SortingLabelProvider.SHOW_ELEMENT_CONTAINER;
		int grouping= LevelTreeContentProvider.LEVEL_PACKAGE;
		int elementLimit= DEFAULT_ELEMENT_LIMIT;

		try {
			sortOrder= getSettings().getInt(KEY_SORTING);
		} catch (NumberFormatException e) {
		}
		try {
			grouping= getSettings().getInt(KEY_GROUPING);
		} catch (NumberFormatException e) {
		}
		if (FALSE.equals(getSettings().get(KEY_LIMIT_ENABLED))) {
			elementLimit= -1;
		} else {
			try {
				elementLimit= getSettings().getInt(KEY_LIMIT);
			} catch (NumberFormatException e) {
			}
		}
		if (memento != null) {
			Integer value= memento.getInteger(KEY_GROUPING);
			if (value != null)
				grouping= value.intValue();
			value= memento.getInteger(KEY_SORTING);
			if (value != null)
				sortOrder= value.intValue();
			boolean limitElements= !FALSE.equals(memento.getString(KEY_LIMIT_ENABLED));
			value= memento.getInteger(KEY_LIMIT);
			if (value != null)
				elementLimit= limitElements ? value.intValue() : -1;
		}

		fCurrentGrouping= grouping;
		fCurrentSortOrder= sortOrder;
		setElementLimit(new Integer(elementLimit));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.search.ui.text.AbstractTextSearchViewPage#saveState(org.eclipse.ui.IMemento)
	 */
	@Override
	public void saveState(IMemento memento) {
		super.saveState(memento);
		memento.putInteger(KEY_GROUPING, fCurrentGrouping);
		memento.putInteger(KEY_SORTING, fCurrentSortOrder);
		int limit= getElementLimit().intValue();
		if (limit != -1)
			memento.putString(KEY_LIMIT_ENABLED, TRUE);
		else
			memento.putString(KEY_LIMIT_ENABLED, FALSE);
		memento.putInteger(KEY_LIMIT, limit);
	}

	private boolean isQueryRunning() {
		AbstractTextSearchResult result= getInput();
		if (result != null) {
			return NewSearchUI.isQueryRunning(result.getQuery());
		}
		return false;
	}

	@Override
	public String getLabel() {
		String label= super.getLabel();
		AbstractTextSearchResult input= getInput();
		if (input != null && input.getActiveMatchFilters() != null && input.getActiveMatchFilters().length > 0) {
			if (isQueryRunning()) {
				String message= SearchMessages.JavaSearchResultPage_filtered_message;
				return Messages.format(message, new Object[] { label });

			} else {
				int filteredOut= input.getMatchCount() - getFilteredMatchCount();
				String message= SearchMessages.JavaSearchResultPage_filteredWithCount_message;
				return Messages.format(message, new Object[] { label, String.valueOf(filteredOut) });
			}
		}
		return label;
	}

	private int getFilteredMatchCount() {
		StructuredViewer viewer= getViewer();
		if (viewer instanceof TreeViewer) {
			ITreeContentProvider tp= (ITreeContentProvider) viewer.getContentProvider();
			return getMatchCount(tp, getRootElements((TreeViewer) getViewer()));
		} else {
			return getMatchCount((TableViewer) viewer);
		}
	}

	private Object[] getRootElements(TreeViewer viewer) {
		Tree t= viewer.getTree();
		Item[] roots= t.getItems();
		Object[] elements= new Object[roots.length];
		for (int i = 0; i < elements.length; i++) {
			elements[i]= roots[i].getData();
		}
		return elements;
	}

	private Object[] getRootElements(TableViewer viewer) {
		Table t= viewer.getTable();
		Item[] roots= t.getItems();
		Object[] elements= new Object[roots.length];
		for (int i = 0; i < elements.length; i++) {
			elements[i]= roots[i].getData();
		}
		return elements;
	}


	private int getMatchCount(ITreeContentProvider cp, Object[] elements) {
		int count= 0;
		for (int j = 0; j < elements.length; j++) {
			count+= getDisplayedMatchCount(elements[j]);
			Object[] children = cp.getChildren(elements[j]);
			count+= getMatchCount(cp, children);
		}
		return count;
	}

	private int getMatchCount(TableViewer viewer) {
		Object[] elements= getRootElements(viewer);
		int count= 0;
		for (int i = 0; i < elements.length; i++) {
			count+= getDisplayedMatchCount(elements[i]);
		}
		return count;
	}


	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class adapter) {
		if (IShowInTargetList.class.equals(adapter)) {
			return SHOW_IN_TARGET_LIST;
		}
		return null;
	}

	@Override
	protected void handleOpen(OpenEvent event) {
		Object firstElement= ((IStructuredSelection)event.getSelection()).getFirstElement();
		if (firstElement instanceof ICompilationUnit ||
				firstElement instanceof IClassFile ||
				firstElement instanceof IMember) {
			if (getDisplayedMatchCount(firstElement) == 0) {
				try {
					fEditorOpener.openElement(firstElement);
				} catch (CoreException e) {
					ExceptionHandler.handle(e, getSite().getShell(), SearchMessages.JavaSearchResultPage_open_editor_error_title, SearchMessages.JavaSearchResultPage_open_editor_error_message);
				}
				return;
			}
		}
		super.handleOpen(event);
	}

	@Override
	public void setElementLimit(Integer elementLimit) {
		super.setElementLimit(elementLimit);
		int limit= elementLimit.intValue();
		getSettings().put(KEY_LIMIT, limit);
		getSettings().put(KEY_LIMIT_ENABLED, limit != -1 ? TRUE : FALSE);
	}
}

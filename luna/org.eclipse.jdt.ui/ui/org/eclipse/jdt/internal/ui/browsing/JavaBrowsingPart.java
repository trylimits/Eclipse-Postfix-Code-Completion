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
package org.eclipse.jdt.internal.ui.browsing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.eclipse.help.IContextProvider;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.INullSelectionListener;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.OpenAndLinkWithEditorHelper;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.AbstractMultiEditor;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.part.ViewPart;

import org.eclipse.search.ui.ISearchResultViewPart;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.IWorkingCopyManager;
import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.actions.BuildActionGroup;
import org.eclipse.jdt.ui.actions.CCPActionGroup;
import org.eclipse.jdt.ui.actions.CustomFiltersActionGroup;
import org.eclipse.jdt.ui.actions.GenerateActionGroup;
import org.eclipse.jdt.ui.actions.ImportActionGroup;
import org.eclipse.jdt.ui.actions.JavaSearchActionGroup;
import org.eclipse.jdt.ui.actions.OpenEditorActionGroup;
import org.eclipse.jdt.ui.actions.OpenViewActionGroup;
import org.eclipse.jdt.ui.actions.RefactorActionGroup;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.actions.NewWizardsActionGroup;
import org.eclipse.jdt.internal.ui.dnd.JdtViewerDragSupport;
import org.eclipse.jdt.internal.ui.dnd.JdtViewerDropSupport;
import org.eclipse.jdt.internal.ui.infoviews.AbstractInfoView;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.jdt.internal.ui.search.SearchUtil;
import org.eclipse.jdt.internal.ui.util.JavaUIHelp;
import org.eclipse.jdt.internal.ui.util.SelectionUtil;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.DecoratingJavaLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.IViewPartInputProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaUILabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.ProblemTableViewer;
import org.eclipse.jdt.internal.ui.viewsupport.StatusBarUpdater;
import org.eclipse.jdt.internal.ui.workingsets.WorkingSetFilterActionGroup;


abstract class JavaBrowsingPart extends ViewPart implements IMenuListener, IViewPartInputProvider, INullSelectionListener {

	private static final String TAG_SELECTED_ELEMENTS= "selectedElements"; //$NON-NLS-1$
	private static final String TAG_SELECTED_ELEMENT= "selectedElement"; //$NON-NLS-1$
	private static final String TAG_LOGICAL_PACKAGE= "logicalPackage"; //$NON-NLS-1$
	private static final String TAG_SELECTED_ELEMENT_PATH= "selectedElementPath"; //$NON-NLS-1$

	private JavaUILabelProvider fLabelProvider;
	private ILabelProvider fTitleProvider;
	private StructuredViewer fViewer;
	private IMemento fMemento;
	private JavaElementTypeComparator fTypeComparator;
	private JdtViewerDropSupport fDropSupport;

	// Actions
	private WorkingSetFilterActionGroup fWorkingSetFilterActionGroup;
	private boolean fHasWorkingSetFilter= true;
	private boolean fHasCustomFilter= true;
	private OpenEditorActionGroup fOpenEditorGroup;
	private CCPActionGroup fCCPActionGroup;
	private BuildActionGroup fBuildActionGroup;
	private ToggleLinkingAction fToggleLinkingAction;
	protected CompositeActionGroup fActionGroups;


	// Filters
	private CustomFiltersActionGroup fCustomFiltersActionGroup;

	protected IWorkbenchPart fPreviousSelectionProvider;
	protected Object fPreviousSelectedElement;

	// Linking
	private boolean fLinkingEnabled;

	/*
	 * Ensure selection changed events being processed only if
	 * initiated by user interaction with this part.
	 */
	private boolean fProcessSelectionEvents= true;

	private IPartListener2 fPartListener= new IPartListener2() {
		public void partActivated(IWorkbenchPartReference ref) {
		}
		public void partBroughtToTop(IWorkbenchPartReference ref) {
		}
	 	public void partInputChanged(IWorkbenchPartReference ref) {
	 	}
		public void partClosed(IWorkbenchPartReference ref) {
		}
		public void partDeactivated(IWorkbenchPartReference ref) {
		}
		public void partOpened(IWorkbenchPartReference ref) {
		}
		public void partVisible(IWorkbenchPartReference ref) {
			if (ref != null && ref.getId() == getSite().getId()){
				fProcessSelectionEvents= true;
				IWorkbenchPage page= getSite().getWorkbenchWindow().getActivePage();
				if (page != null) {
					ISelection selection= page.getSelection();
					if (selection != null)
						selectionChanged(page.getActivePart(), selection);
				}
			}
		}
		public void partHidden(IWorkbenchPartReference ref) {
			if (ref != null && ref.getId() == getSite().getId())
				fProcessSelectionEvents= false;
		}
	};

	/**
	 * Helper to open and activate editors.
	 *
	 * @since 3.5
	 */
	private OpenAndLinkWithEditorHelper fOpenAndLinkWithEditorHelper;

	public JavaBrowsingPart() {
		super();
		initLinkingEnabled();
	}

	/*
	 * Implements method from IViewPart.
	 */
	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		fMemento= memento;
	}

	/*
	 * Implements method from IViewPart.
	 */
	@Override
	public void saveState(IMemento memento) {
		if (fViewer == null) {
			// part has not been created
			if (fMemento != null) //Keep the old state;
				memento.putMemento(fMemento);
			return;
		}
		if (fHasWorkingSetFilter)
			fWorkingSetFilterActionGroup.saveState(memento);
		if (fHasCustomFilter)
			fCustomFiltersActionGroup.saveState(memento);
		saveSelectionState(memento);
		saveLinkingEnabled(memento);
	}

	private void saveLinkingEnabled(IMemento memento) {
		memento.putInteger(getLinkToEditorKey(), fLinkingEnabled ? 1 : 0);
	}

	private void saveSelectionState(IMemento memento) {
		Object elements[]= ((IStructuredSelection) fViewer.getSelection()).toArray();
		if (elements.length > 0) {
			IMemento selectionMem= memento.createChild(TAG_SELECTED_ELEMENTS);
			for (int i= 0; i < elements.length; i++) {
				IMemento elementMem= selectionMem.createChild(TAG_SELECTED_ELEMENT);
				Object o= elements[i];
				if (o instanceof IJavaElement)
					elementMem.putString(TAG_SELECTED_ELEMENT_PATH, ((IJavaElement) elements[i]).getHandleIdentifier());
				else if (o instanceof LogicalPackage) {
					IPackageFragment[] packages=((LogicalPackage)o).getFragments();
					for (int j= 0; j < packages.length; j++) {
						IMemento packageMem= elementMem.createChild(TAG_LOGICAL_PACKAGE);
						packageMem.putString(TAG_SELECTED_ELEMENT_PATH, packages[j].getHandleIdentifier());
					}
				}
			}
		}
	}

	protected void restoreState(IMemento memento) {
		if (fHasWorkingSetFilter)
			fWorkingSetFilterActionGroup.restoreState(memento);
		if (fHasCustomFilter)
			fCustomFiltersActionGroup.restoreState(memento);

		if (fHasCustomFilter || fHasWorkingSetFilter) {
			fViewer.getControl().setRedraw(false);
			fViewer.refresh();
			fViewer.getControl().setRedraw(true);
		}
	}

	private ISelection restoreSelectionState(IMemento memento) {
		if (memento == null)
			return null;

		IMemento childMem;
		childMem= memento.getChild(TAG_SELECTED_ELEMENTS);
		if (childMem != null) {
			ArrayList<Object> list= new ArrayList<Object>();
			IMemento[] elementMem= childMem.getChildren(TAG_SELECTED_ELEMENT);
			for (int i= 0; i < elementMem.length; i++) {
				String javaElementHandle= elementMem[i].getString(TAG_SELECTED_ELEMENT_PATH);
				if (javaElementHandle == null) {
					// logical package
					IMemento[] packagesMem= elementMem[i].getChildren(TAG_LOGICAL_PACKAGE);
					LogicalPackage lp= null;
					for (int j= 0; j < packagesMem.length; j++) {
						javaElementHandle= packagesMem[j].getString(TAG_SELECTED_ELEMENT_PATH);
						Object pack= JavaCore.create(javaElementHandle);
						if (pack instanceof IPackageFragment && ((IPackageFragment)pack).exists()) {
							if (lp == null)
								lp= new LogicalPackage((IPackageFragment)pack);
							else
								lp.add((IPackageFragment)pack);
						}
					}
					if (lp != null)
						list.add(lp);
				} else {
					IJavaElement element= JavaCore.create(javaElementHandle);
					if (element != null && element.exists())
						list.add(element);
				}
			}
			return new StructuredSelection(list);
		}
		return null;
	}

	private void restoreLinkingEnabled(IMemento memento) {
		Integer val= memento.getInteger(getLinkToEditorKey());
		if (val != null) {
			fLinkingEnabled= val.intValue() != 0;
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.WorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public void createPartControl(Composite parent) {
		Assert.isTrue(fViewer == null);


		fTypeComparator= new JavaElementTypeComparator();

		// Setup viewer
		fViewer= createViewer(parent);

		initDragAndDrop();

		fLabelProvider= createLabelProvider();
		fViewer.setLabelProvider(createDecoratingLabelProvider(fLabelProvider));

		fViewer.setComparator(createJavaElementComparator());
		fViewer.setUseHashlookup(true);
		fTitleProvider= createTitleProvider();

		createContextMenu();
		getSite().setSelectionProvider(fViewer);

		if (fMemento != null) { // initialize linking state before creating the actions
			restoreLinkingEnabled(fMemento);
		}

		createActions(); // call before registering for selection changes
		addKeyListener();

		if (fMemento != null)
			restoreState(fMemento);

		getSite().setSelectionProvider(fViewer);

		// Status line
		IStatusLineManager slManager= getViewSite().getActionBars().getStatusLineManager();
		fViewer.addSelectionChangedListener(createStatusBarUpdater(slManager));


		hookViewerListeners();

		// Filters
		addFilters();

		// Initialize viewer input
		fViewer.setContentProvider(createContentProvider());
		setInitialInput();

		// Initialize selection
		setInitialSelection();
		fMemento= null;

		// Listen to page changes
		getViewSite().getPage().addPostSelectionListener(this);
		getViewSite().getPage().addPartListener(fPartListener);

		activateHandlers((IHandlerService) getViewSite().getService(IHandlerService.class));
		fillActionBars(getViewSite().getActionBars());

		setHelp();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.WorkbenchPart#getAdapter(java.lang.Class)
	 */
	@Override
	public Object getAdapter(Class key) {
		if (key == IShowInSource.class) {
			return getShowInSource();
		}
		if (key == IContextProvider.class)
			return JavaUIHelp.getHelpContextProvider(this, getHelpContextId());

		return super.getAdapter(key);
	}

	/**
	 * Returns the <code>IShowInSource</code> for this view.
	 * @return returns the <code>IShowInSource</code>
	 */
	protected IShowInSource getShowInSource() {
		return new IShowInSource() {
			public ShowInContext getShowInContext() {
				return new ShowInContext(
					null,
				getSite().getSelectionProvider().getSelection());
			}
		};
	}

	protected DecoratingJavaLabelProvider createDecoratingLabelProvider(JavaUILabelProvider provider) {
//		XXX: Work in progress for problem decorator being a workbench decorator//
//		return new ExcludingDecoratingLabelProvider(provider, decorationMgr, "org.eclipse.jdt.ui.problem.decorator"); //$NON-NLS-1$
		return new DecoratingJavaLabelProvider(provider);
	}

	protected JavaElementComparator createJavaElementComparator() {
		return new JavaElementComparator();
	}

	protected StatusBarUpdater createStatusBarUpdater(IStatusLineManager slManager) {
		return new StatusBarUpdater(slManager);
	}

	protected void createContextMenu() {
		MenuManager menuManager= new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuManager.setRemoveAllWhenShown(true);
		menuManager.addMenuListener(this);
		Menu contextMenu= menuManager.createContextMenu(fViewer.getControl());
		fViewer.getControl().setMenu(contextMenu);
		getSite().registerContextMenu(menuManager, fViewer);
	}

	protected void initDragAndDrop() {
		fDropSupport= new JdtViewerDropSupport(fViewer);
		fDropSupport.start();
		new JdtViewerDragSupport(fViewer).start();
	}

	protected JdtViewerDropSupport getDropSupport() {
		return fDropSupport;
	}

	protected void fillActionBars(IActionBars actionBars) {
		IToolBarManager toolBar= actionBars.getToolBarManager();
		fillToolBar(toolBar);


		if (fHasWorkingSetFilter)
			fWorkingSetFilterActionGroup.fillActionBars(getViewSite().getActionBars());

		actionBars.updateActionBars();

		fActionGroups.fillActionBars(actionBars);

		if (fHasCustomFilter)
			fCustomFiltersActionGroup.fillActionBars(actionBars);

		IMenuManager menu= actionBars.getMenuManager();
		menu.add(fToggleLinkingAction);
	}

	protected void activateHandlers(IHandlerService handlerService) {
		handlerService.activateHandler(IWorkbenchCommandConstants.NAVIGATE_TOGGLE_LINK_WITH_EDITOR, new ActionHandler(fToggleLinkingAction));
	}

	//---- IWorkbenchPart ------------------------------------------------------


	@Override
	public void setFocus() {
		fViewer.getControl().setFocus();
	}

	@Override
	public void dispose() {
		if (fViewer != null) {
			getViewSite().getPage().removePostSelectionListener(this);
			getViewSite().getPage().removePartListener(fPartListener);
			fViewer= null;
		}
		if (fActionGroups != null) {
			fActionGroups.dispose();
			fActionGroups= null;
		}

		if (fWorkingSetFilterActionGroup != null) {
			fWorkingSetFilterActionGroup.dispose();
			fWorkingSetFilterActionGroup= null;
		}

		super.dispose();
	}

	/**
	 * Adds the KeyListener
	 */
	protected void addKeyListener() {
		fViewer.getControl().addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent event) {
				handleKeyReleased(event);
			}
		});
	}

	protected void handleKeyReleased(KeyEvent event) {
		if (event.stateMask != 0)
			return;

		int key= event.keyCode;
		if (key == SWT.F5) {
			IAction action= fBuildActionGroup.getRefreshAction();
			if (action.isEnabled())
				action.run();
		}
	}

	//---- Adding Action to Toolbar -------------------------------------------

	/**
	 * Hook to fill the toolbar.
	 * @param tbm tool bar manager
	 */
	protected void fillToolBar(IToolBarManager tbm) {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.action.IMenuListener#menuAboutToShow(org.eclipse.jface.action.IMenuManager)
	 */
	public void menuAboutToShow(IMenuManager menu) {
		JavaPlugin.createStandardGroups(menu);

		IStructuredSelection selection= (IStructuredSelection) fViewer.getSelection();
		int size= selection.size();
		Object element= selection.getFirstElement();

		if (size == 1)
			addOpenNewWindowAction(menu, element);
		fActionGroups.setContext(new ActionContext(selection));
		fActionGroups.fillContextMenu(menu);
		fActionGroups.setContext(null);
	}

	private void addOpenNewWindowAction(IMenuManager menu, Object element) {
		if (element instanceof IJavaElement) {
			element= ((IJavaElement)element).getResource();
		}
		if (!(element instanceof IContainer))
			return;
		menu.appendToGroup(
			IContextMenuConstants.GROUP_OPEN,
			new PatchedOpenInNewWindowAction(getSite().getWorkbenchWindow(), (IContainer)element));
	}

	protected void createActions() {
		fActionGroups= new CompositeActionGroup(new ActionGroup[] {
				new NewWizardsActionGroup(this.getSite()),
				fOpenEditorGroup= new OpenEditorActionGroup(this),
				new OpenViewActionGroup(this),
				fCCPActionGroup= new CCPActionGroup(this),
				new GenerateActionGroup(this),
				new RefactorActionGroup(this),
				new ImportActionGroup(this),
				fBuildActionGroup= new BuildActionGroup(this),
				new JavaSearchActionGroup(this)});


		if (fHasWorkingSetFilter) {
			String viewId= getConfigurationElement().getAttribute("id"); //$NON-NLS-1$
			Assert.isNotNull(viewId);
			IPropertyChangeListener workingSetListener= new IPropertyChangeListener() {
				public void propertyChange(PropertyChangeEvent event) {
					doWorkingSetChanged(event);
				}
			};
			fWorkingSetFilterActionGroup= new WorkingSetFilterActionGroup(getSite(), workingSetListener);
			fViewer.addFilter(fWorkingSetFilterActionGroup.getWorkingSetFilter());
		}

		// Custom filter group
		if (fHasCustomFilter)
			fCustomFiltersActionGroup= new CustomFiltersActionGroup(this, fViewer);

		fToggleLinkingAction= new ToggleLinkingAction(this);
		fToggleLinkingAction.setActionDefinitionId(IWorkbenchCommandConstants.NAVIGATE_TOGGLE_LINK_WITH_EDITOR);
	}

	private void doWorkingSetChanged(PropertyChangeEvent event) {
		String property= event.getProperty();
		if (IWorkingSetManager.CHANGE_WORKING_SET_LABEL_CHANGE.equals(property))
			updateTitle();
		else	if (IWorkingSetManager.CHANGE_WORKING_SET_CONTENT_CHANGE.equals(property)) {
			updateTitle();
			fViewer.getControl().setRedraw(false);
			fViewer.refresh();
			fViewer.getControl().setRedraw(true);
		}

	}


	/**
	 * Returns the shell to use for opening dialogs.
	 * Used in this class, and in the actions.
	 * @return returns the shell
	 */
	Shell getShell() {
		return fViewer.getControl().getShell();
	}

	protected final Display getDisplay() {
		return fViewer.getControl().getDisplay();
	}

	/**
	 * Returns the selection provider.
	 * @return the selection provider
	 */
	ISelectionProvider getSelectionProvider() {
		return fViewer;
	}

	/**
	 * Answers if the given <code>element</code> is a valid
	 * input for this part.
	 *
	 * @param 	element	the object to test
	 * @return	<code>true</code> if the given element is a valid input
	 */
	abstract protected boolean isValidInput(Object element);

	/**
	 * Answers if the given <code>element</code> is a valid
	 * element for this part.
	 *
	 * @param 	element	the object to test
	 * @return	<code>true</code> if the given element is a valid element
	 */
	protected boolean isValidElement(Object element) {
		if (!(element instanceof IJavaElement)) {
			return false;
		}
		Object input= getViewer().getInput();
		if (input == null)
			return false;
		if (input instanceof Collection)
			return ((Collection<?>)input).contains(element);
		else
			return input.equals(element);

	}

	boolean isInputResetBy(Object newInput, Object input, IWorkbenchPart part) {
		if (newInput == null)
			return part == fPreviousSelectionProvider;

		if (input instanceof IJavaElement && newInput instanceof IJavaElement)
			return getTypeComparator().compare((IJavaElement) newInput, (IJavaElement) input)  > 0;

		if((newInput instanceof List) && (part instanceof PackagesView))
			return true;

		else
			return false;
	}

	private boolean isInputResetBy(IWorkbenchPart part) {
		if (!(part instanceof JavaBrowsingPart))
			return true;
		Object thisInput= getViewer().getInput();
		Object partInput= ((JavaBrowsingPart)part).getViewer().getInput();

		if(thisInput instanceof Collection)
			thisInput= ((Collection<?>)thisInput).iterator().next();

		if(partInput instanceof Collection)
			partInput= ((Collection<?>)partInput).iterator().next();

		if (thisInput instanceof IJavaElement && partInput instanceof IJavaElement)
			return getTypeComparator().compare((IJavaElement) partInput, (IJavaElement) thisInput) > 0;
		else
			return true;
	}

	protected boolean isAncestorOf(Object ancestor, Object element) {
		if (element instanceof IJavaElement && ancestor instanceof IJavaElement)
			return !element.equals(ancestor) && internalIsAncestorOf((IJavaElement)ancestor, (IJavaElement)element);
		return false;
	}

	private boolean internalIsAncestorOf(IJavaElement ancestor, IJavaElement element) {
		if (element != null)
			return element.equals(ancestor) || internalIsAncestorOf(ancestor, element.getParent());
		else
			return false;
	}

	private boolean isSearchResultView(IWorkbenchPart part) {
		return SearchUtil.isSearchPlugInActivated() && part instanceof ISearchResultViewPart;
	}

	protected boolean needsToProcessSelectionChanged(IWorkbenchPart part) {
		if (!fProcessSelectionEvents || part == this || isSearchResultView(part) || part instanceof AbstractInfoView){
			if (part == this)
				fPreviousSelectionProvider= part;
			return false;
		}
		return true;
	}

	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		if (!needsToProcessSelectionChanged(part))
			return;

		if (fToggleLinkingAction.isChecked() && (part instanceof IEditorPart)) {
			setSelectionFromEditor(part, selection);
			return;
		}

		// Set selection
		Object selectedElement= getSingleElementFromSelection(selection);

		if (selectedElement != null && (part == null || part.equals(fPreviousSelectionProvider)) && selectedElement.equals(fPreviousSelectedElement))
			return;

		fPreviousSelectedElement= selectedElement;

		Object currentInput= getViewer().getInput();
		if (selectedElement != null && selectedElement.equals(currentInput)) {
			IJavaElement elementToSelect= findElementToSelect(selectedElement);
			if (elementToSelect != null && getTypeComparator().compare((IJavaElement) selectedElement, elementToSelect) < 0)
				setSelection(new StructuredSelection(elementToSelect), true);
			else if (elementToSelect == null && (this instanceof MembersView)) {
				setSelection(StructuredSelection.EMPTY, true);
				fPreviousSelectedElement= StructuredSelection.EMPTY;
			}
			fPreviousSelectionProvider= part;
			return;
		}

		// Clear input if needed
		if (part != fPreviousSelectionProvider && selectedElement != null && !selectedElement.equals(currentInput) && isInputResetBy(selectedElement, currentInput, part)) {
			if (!isAncestorOf(selectedElement, currentInput))
				setInput(null);
			fPreviousSelectionProvider= part;
			return;
		} else if (selection != null && selection.isEmpty() && !isInputResetBy(part)) {
			fPreviousSelectionProvider= part;
			return;
		} else if (selectedElement == null && part == fPreviousSelectionProvider) {
			setInput(null);
			fPreviousSelectionProvider= part;
			return;
		}
		fPreviousSelectionProvider= part;

		// Adjust input and set selection and
		adjustInputAndSetSelection(selectedElement);
	}


	void setHasWorkingSetFilter(boolean state) {
		fHasWorkingSetFilter= state;
	}

	void setHasCustomSetFilter(boolean state) {
		fHasCustomFilter= state;
	}

	protected Object getInput() {
		return fViewer.getInput();
	}

	protected void setInput(Object input) {
		setViewerInput(input);
		updateTitle();
	}

	boolean isLinkingEnabled() {
		return fLinkingEnabled;
	}

	private void initLinkingEnabled() {
		fLinkingEnabled= PreferenceConstants.getPreferenceStore().getBoolean(getLinkToEditorKey());
	}

	private void setViewerInput(Object input) {
		fProcessSelectionEvents= false;
		fViewer.setInput(input);
		fProcessSelectionEvents= true;
	}

	void updateTitle() {
		setTitleToolTip(getToolTipText(fViewer.getInput()));
	}

	/**
	 * Returns the tool tip text for the given element.
	 * @param element the element
	 * @return the tooltip for the element
	 */
	String getToolTipText(Object element) {
		String result;
		if (!(element instanceof IResource)) {
			result= JavaElementLabels.getTextLabel(element, AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS);
		} else {
			IPath path= ((IResource) element).getFullPath();
			if (path.isRoot()) {
				result= getConfigurationElement().getAttribute("name"); //$NON-NLS-1$
			} else {
				result= path.makeRelative().toString();
			}
		}

		if (fWorkingSetFilterActionGroup == null || fWorkingSetFilterActionGroup.getWorkingSet() == null)
			return result;

		IWorkingSet ws= fWorkingSetFilterActionGroup.getWorkingSet();
		String wsstr= Messages.format(JavaBrowsingMessages.JavaBrowsingPart_toolTip, new String[] { BasicElementLabels.getWorkingSetLabel(ws) });
		if (result.length() == 0)
			return wsstr;
		return Messages.format(JavaBrowsingMessages.JavaBrowsingPart_toolTip2, new String[] { result, BasicElementLabels.getWorkingSetLabel(ws) });
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.WorkbenchPart#getTitleToolTip()
	 */
	@Override
	public String getTitleToolTip() {
		if (fViewer == null)
			return super.getTitleToolTip();
		return getToolTipText(fViewer.getInput());
	}

	protected final StructuredViewer getViewer() {
		return fViewer;
	}

	protected final void setViewer(StructuredViewer viewer){
		fViewer= viewer;
	}

	protected JavaUILabelProvider createLabelProvider() {
		return new AppearanceAwareLabelProvider(
						AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS,
						AppearanceAwareLabelProvider.DEFAULT_IMAGEFLAGS | JavaElementImageProvider.SMALL_ICONS
						);
	}

	protected ILabelProvider createTitleProvider() {
		return new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_BASICS | JavaElementLabelProvider.SHOW_SMALL_ICONS);
	}

	protected final ILabelProvider getLabelProvider() {
		return fLabelProvider;
	}

	protected final ILabelProvider getTitleProvider() {
		return fTitleProvider;
	}

	/**
	 * Creates the viewer of this part.
	 *
	 * @param parent	the parent for the viewer
	 * @return the created viewer
	 */
	protected StructuredViewer createViewer(Composite parent) {
		return new ProblemTableViewer(parent, SWT.MULTI);
	}

	protected int getLabelProviderFlags() {
		return JavaElementLabelProvider.SHOW_BASICS | JavaElementLabelProvider.SHOW_OVERLAY_ICONS |
				JavaElementLabelProvider.SHOW_SMALL_ICONS | JavaElementLabelProvider.SHOW_VARIABLE | JavaElementLabelProvider.SHOW_PARAMETERS;
	}

	/**
	 * Adds filters the viewer of this part.
	 */
	protected void addFilters() {
		// default is to have no filters
	}

	/**
	 * Creates the content provider of this part.
	 * @return the content provider
	 */
	protected IContentProvider createContentProvider() {
		return new JavaBrowsingContentProvider(true, this);
	}

	protected void setInitialInput() {
		// Use the selection, if any
		ISelection selection= getSite().getPage().getSelection();
		Object input= getSingleElementFromSelection(selection);
		if (!(input instanceof IJavaElement)) {
			// Use the input of the page
			input= getSite().getPage().getInput();
			if (!(input instanceof IJavaElement) && input instanceof IAdaptable)
				input= ((IAdaptable)input).getAdapter(IJavaElement.class);
		}
		setInput(findInputForJavaElement((IJavaElement)input));
	}

	protected void setInitialSelection() {
		// Use the selection, if any
		Object input;
		IWorkbenchPage page= getSite().getPage();
		ISelection selection= null;
		if (page != null)
			selection= page.getSelection();
		if (selection instanceof ITextSelection) {
			Object part= page.getActivePart();
			if (part instanceof IEditorPart) {
				setSelectionFromEditor((IEditorPart)part);
				if (fViewer.getSelection() != null)
					return;
			}
		}

		// Use saved selection from memento
		if (selection == null || selection.isEmpty())
			selection= restoreSelectionState(fMemento);

		if (selection == null || selection.isEmpty()) {
			// Use the input of the page
			input= getSite().getPage().getInput();
			if (!(input instanceof IJavaElement)) {
				if (input instanceof IAdaptable)
					input= ((IAdaptable)input).getAdapter(IJavaElement.class);
				else
					return;
			}
			selection= new StructuredSelection(input);
		}
		selectionChanged(null, selection);
	}

	protected final void setHelp() {
		JavaUIHelp.setHelp(fViewer, getHelpContextId());
	}

	/**
	 * Returns the context ID for the Help system
	 *
	 * @return	the string used as ID for the Help context
	 */
	abstract protected String getHelpContextId();

	/**
	 * Returns the preference key for the link to editor setting.
	 *
	 * @return	the string used as key into the preference store
	 */
	abstract protected String getLinkToEditorKey();

	/**
	 * Adds additional listeners to this view.
	 * This method can be overridden but should
	 * call super.
	 */
	protected void hookViewerListeners() {


		fOpenAndLinkWithEditorHelper= new OpenAndLinkWithEditorHelper(fViewer) {
			@Override
			protected void activate(ISelection selection) {
				try {
					final Object selectedElement= SelectionUtil.getSingleElement(selection);
					if (EditorUtility.isOpenInEditor(selectedElement) != null)
						EditorUtility.openInEditor(selectedElement, true);
				} catch (PartInitException ex) {
					// ignore if no editor input can be found
				}
			}

			@Override
			protected void linkToEditor(ISelection selection) {
				if (!fProcessSelectionEvents)
					return;

				fPreviousSelectedElement= getSingleElementFromSelection(selection);

				IWorkbenchPage page= getSite().getPage();
				if (page == null)
					return;

				if (page.equals(JavaPlugin.getActivePage()) && JavaBrowsingPart.this.equals(page.getActivePart())) {
					JavaBrowsingPart.this.linkToEditor(selection);
				}
			}

			@Override
			protected void open(ISelection selection, boolean activate) {
				IAction open= fOpenEditorGroup.getOpenAction();
				if (open.isEnabled()) {
					open.run();
					restoreSelection();
				}
			}

		};
		fOpenAndLinkWithEditorHelper.setLinkWithEditor(fLinkingEnabled);

	}

	void restoreSelection() {
		// Default is to do nothing
	}

	void adjustInputAndSetSelection(Object o) {
		if (!(o instanceof IJavaElement)) {
			if (o == null)
				setInput(null);
			setSelection(StructuredSelection.EMPTY, true);
			return;
		}

		IJavaElement je= (IJavaElement)o;
		IJavaElement elementToSelect= findElementToSelect(je);
		IJavaElement newInput= findInputForJavaElement(je);
		IJavaElement oldInput= null;
		if (getInput() instanceof IJavaElement)
			oldInput= (IJavaElement)getInput();

		if (elementToSelect == null && !isValidInput(newInput) && (newInput == null && !isAncestorOf(je, oldInput)))
			// Clear input
			setInput(null);
		else if (mustSetNewInput(elementToSelect, oldInput, newInput)) {
			// Adjust input to selection
			setInput(newInput);
		}

		if (elementToSelect != null && elementToSelect.exists())
			setSelection(new StructuredSelection(elementToSelect), true);
		else
			setSelection(StructuredSelection.EMPTY, true);
	}

	/**
	 * Compute if a new input must be set.
	 *
	 * @param elementToSelect the element to select
	 * @param oldInput old input
	 * @param newInput new input
	 * @return	<code>true</code> if the input has to be set
	 * @since 3.0
	 */
	private boolean mustSetNewInput(IJavaElement elementToSelect, IJavaElement oldInput, IJavaElement newInput) {
		return (newInput == null || !newInput.equals(oldInput))
			&& (elementToSelect == null
				|| oldInput == null
				|| (!((elementToSelect instanceof IPackageDeclaration)
					&& (elementToSelect.getParent().equals(oldInput.getParent()))
					&& (!isAncestorOf(getViewPartInput(), elementToSelect)))));
	}

	/**
	 * Finds the closest Java element which can be used as input for
	 * this part and has the given Java element as child
	 *
	 * @param 	je 	the Java element for which to search the closest input
	 * @return	the closest Java element used as input for this part
	 */
	protected IJavaElement findInputForJavaElement(IJavaElement je) {
		if (je == null || !je.exists())
			return null;
		if (isValidInput(je))
			return je;
		return findInputForJavaElement(je.getParent());
	}

	protected final IJavaElement findElementToSelect(Object obj) {
		if (obj instanceof IJavaElement)
			return findElementToSelect((IJavaElement)obj);
		return null;
	}

	/**
	 * Finds the element which has to be selected in this part.
	 *
	 * @param je	the Java element which has the focus
	 * @return returns the element to select
	 */
	abstract protected IJavaElement findElementToSelect(IJavaElement je);


	protected final Object getSingleElementFromSelection(ISelection selection) {
		if (!(selection instanceof IStructuredSelection) || selection.isEmpty())
			return null;

		Iterator<?> iter= ((IStructuredSelection)selection).iterator();
		Object firstElement= iter.next();
		if (!(firstElement instanceof IJavaElement)) {
			if (firstElement instanceof IMarker)
				firstElement= ((IMarker)firstElement).getResource();
			if (firstElement instanceof IAdaptable) {
				IJavaElement je= (IJavaElement)((IAdaptable)firstElement).getAdapter(IJavaElement.class);
				if (je == null && firstElement instanceof IFile) {
					IContainer parent= ((IFile)firstElement).getParent();
					if (parent != null)
						return parent.getAdapter(IJavaElement.class);
					else return null;
				} else
					return je;

			} else
				return firstElement;
		}
		Object currentInput= getViewer().getInput();
		if (currentInput == null || !currentInput.equals(findInputForJavaElement((IJavaElement)firstElement)))
			if (iter.hasNext())
				// multi-selection and view is empty
				return null;
			else
				// OK: single selection and view is empty
				return firstElement;

		// be nice to multi-selection
		while (iter.hasNext()) {
			Object element= iter.next();
			if (!(element instanceof IJavaElement))
				return null;
			if (!currentInput.equals(findInputForJavaElement((IJavaElement)element)))
				return null;
		}
		return firstElement;
	}

	/**
	 * Gets the typeComparator.
	 * @return Returns a JavaElementTypeComparator
	 */
	protected Comparator<IJavaElement> getTypeComparator() {
		return fTypeComparator;
	}

	/**
	 * Links to editor (if option enabled)
	 * @param selection the selection
	 */
	private void linkToEditor(ISelection selection) {
		Object obj= SelectionUtil.getSingleElement(selection);
		if (obj != null) {
			IEditorPart part= EditorUtility.isOpenInEditor(obj);
			if (part != null) {
				IWorkbenchPage page= getSite().getPage();
				page.bringToTop(part);
				if (obj instanceof IJavaElement)
					EditorUtility.revealInEditor(part, (IJavaElement) obj);
			}
		}
	}

	void setSelectionFromEditor(IWorkbenchPart part) {
		if (!fProcessSelectionEvents || !linkBrowsingViewSelectionToEditor() || !(part instanceof IEditorPart))
			return;

		IWorkbenchPartSite site= part.getSite();
		if (site == null)
			return;
		ISelectionProvider provider= site.getSelectionProvider();
		if (provider != null)
			setSelectionFromEditor(part, provider.getSelection());
	}

	private void setSelectionFromEditor(IWorkbenchPart part, ISelection selection) {
		if (part instanceof IEditorPart) {
			IJavaElement element= null;
			if (selection instanceof IStructuredSelection) {
				Object obj= getSingleElementFromSelection(selection);
				if (obj instanceof IJavaElement)
					element= (IJavaElement)obj;
			}
			IEditorInput ei;
			if (part instanceof AbstractMultiEditor)
				ei= ((AbstractMultiEditor)part).getActiveEditor().getEditorInput();
			else
				ei= ((IEditorPart)part).getEditorInput();
			if (selection instanceof ITextSelection) {
				int offset= ((ITextSelection)selection).getOffset();
				element= getElementAt(ei, offset);
			}
			if (element != null) {
				adjustInputAndSetSelection(element);
				return;
			}
			if (ei instanceof IFileEditorInput) {
				IFile file= ((IFileEditorInput)ei).getFile();
				IJavaElement je= (IJavaElement)file.getAdapter(IJavaElement.class);
				IContainer container= null;
				if (je == null) {
					container= ((IFileEditorInput)ei).getFile().getParent();
					if (container != null)
						je= (IJavaElement)container.getAdapter(IJavaElement.class);
				}
				if (je == null && container == null) {
					setSelection(null, false);
					return;
				}
				adjustInputAndSetSelection(je);
			} else if (ei instanceof IClassFileEditorInput) {
				IClassFile cf= ((IClassFileEditorInput)ei).getClassFile();
				adjustInputAndSetSelection(cf);
			}
		}
	}

	/**
	 * Returns the element contained in the EditorInput
	 * @param input the editor input
	 * @return the input element
	 */
	Object getElementOfInput(IEditorInput input) {
		if (input instanceof IFileEditorInput)
			return ((IFileEditorInput)input).getFile();
		if (input != null)
			return JavaUI.getEditorInputJavaElement(input);
		return null;
	}

	void setSelection(ISelection selection, boolean reveal) {
		if (selection != null && selection.equals(fViewer.getSelection()))
			return;
		fProcessSelectionEvents= false;
		fViewer.setSelection(selection, reveal);
		fProcessSelectionEvents= true;
	}

	/**
	 * @param input the editor input
	 * @param offset the offset in the file
	 * @return the element at the given offset
	 */
	protected IJavaElement getElementAt(IEditorInput input, int offset) {
		if (input instanceof IClassFileEditorInput) {
			try {
				return ((IClassFileEditorInput)input).getClassFile().getElementAt(offset);
			} catch (JavaModelException ex) {
				return null;
			}
		}

		IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();
		ICompilationUnit unit= manager.getWorkingCopy(input);
		if (unit != null)
			try {
				if (unit.isConsistent())
					return unit.getElementAt(offset);
				else {
					/*
					 * XXX: We should set the selection later when the
					 *      CU is reconciled.
					 *      see https://bugs.eclipse.org/bugs/show_bug.cgi?id=51290
					 */
				}
			} catch (JavaModelException ex) {
				// fall through
			}
		return null;
	}

	protected IType getTypeForCU(ICompilationUnit cu) {
		// Use primary type if possible
		IType primaryType= cu.findPrimaryType();
		if (primaryType != null)
			return primaryType;

		// Use first top-level type
		try {
			IType[] types= cu.getTypes();
			if (types.length > 0)
				return types[0];
			else
				return null;
		} catch (JavaModelException ex) {
			return null;
		}
	}

	void setProcessSelectionEvents(boolean state) {
		fProcessSelectionEvents= state;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.viewsupport.IViewPartInputProvider#getViewPartInput()
	 */
	public Object getViewPartInput() {
		if (fViewer != null) {
			return fViewer.getInput();
		}
		return null;
	}

	protected void setActionGroups(CompositeActionGroup actionGroups) {
		fActionGroups= actionGroups;
	}

	protected void setBuildActionGroup(BuildActionGroup actionGroup) {
		fBuildActionGroup= actionGroup;
	}

	protected void setCCPActionGroup(CCPActionGroup actionGroup) {
		fCCPActionGroup= actionGroup;
	}

	protected void setCustomFiltersActionGroup(CustomFiltersActionGroup customFiltersActionGroup) {
		fCustomFiltersActionGroup= customFiltersActionGroup;
	}

	protected boolean hasCustomFilter() {
		return fHasCustomFilter;
	}

	protected boolean hasWorkingSetFilter() {
		return fHasWorkingSetFilter;
	}

	protected void setOpenEditorGroup(OpenEditorActionGroup openEditorGroup) {
		fOpenEditorGroup= openEditorGroup;
	}

	protected OpenEditorActionGroup getOpenEditorGroup() {
		return fOpenEditorGroup;
	}

	protected BuildActionGroup getBuildActionGroup() {
		return fBuildActionGroup;
	}

	protected CCPActionGroup getCCPActionGroup() {
		return fCCPActionGroup;
	}

	private boolean linkBrowsingViewSelectionToEditor() {
		return isLinkingEnabled();
	}

	public void setLinkingEnabled(boolean enabled) {
		fLinkingEnabled= enabled;
		PreferenceConstants.getPreferenceStore().setValue(getLinkToEditorKey(), enabled);
		if (enabled) {
			IEditorPart editor = getSite().getPage().getActiveEditor();
			if (editor != null) {
				setSelectionFromEditor(editor);
			}
		}
		fOpenAndLinkWithEditorHelper.setLinkWithEditor(enabled);
	}

}

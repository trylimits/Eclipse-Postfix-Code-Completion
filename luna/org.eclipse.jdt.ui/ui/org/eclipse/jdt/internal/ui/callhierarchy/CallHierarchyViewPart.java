/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 *             (report 36180: Callers/Callees view)
 *   Michael Fraenkel (fraenkel@us.ibm.com) - patch
 *             (report 60714: Call Hierarchy: display search scope in view title)
 *   Stephan Herrmann (stephan@cs.tu-berlin.de):
 *          - bug 75800: [call hierarchy] should allow searches for fields
 *   Stephan Herrmann (stephan@cs.tu-berlin.de):
 *          - bug 206949: [call hierarchy] filter field accesses (only write or only read)
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.help.IContextProvider;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TreeItem;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.util.DelegatingDragAdapter;
import org.eclipse.jface.util.DelegatingDropAdapter;
import org.eclipse.jface.util.OpenStrategy;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.OpenAndLinkWithEditorHelper;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.navigator.ICommonMenuConstants;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.IShowInTargetList;
import org.eclipse.ui.part.PageBook;
import org.eclipse.ui.part.PluginTransfer;
import org.eclipse.ui.part.ResourceTransfer;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.IWorkbenchSiteProgressService;
import org.eclipse.ui.views.navigator.LocalSelectionTransfer;

import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;

import org.eclipse.jdt.internal.corext.callhierarchy.CallHierarchy;
import org.eclipse.jdt.internal.corext.callhierarchy.CallLocation;
import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;
import org.eclipse.jdt.internal.corext.callhierarchy.RealCallers;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.actions.CCPActionGroup;
import org.eclipse.jdt.ui.actions.GenerateActionGroup;
import org.eclipse.jdt.ui.actions.JavaSearchActionGroup;
import org.eclipse.jdt.ui.actions.OpenEditorActionGroup;
import org.eclipse.jdt.ui.actions.OpenViewActionGroup;
import org.eclipse.jdt.ui.actions.RefactorActionGroup;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.dnd.EditorInputTransferDragAdapter;
import org.eclipse.jdt.internal.ui.dnd.ResourceTransferDragAdapter;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.packageview.FileTransferDragAdapter;
import org.eclipse.jdt.internal.ui.packageview.PluginTransferDropAdapter;
import org.eclipse.jdt.internal.ui.packageview.SelectionTransferDragAdapter;
import org.eclipse.jdt.internal.ui.util.JavaUIHelp;
import org.eclipse.jdt.internal.ui.util.SelectionUtil;
import org.eclipse.jdt.internal.ui.viewsupport.SelectionProviderMediator;
import org.eclipse.jdt.internal.ui.viewsupport.StatusBarUpdater;


/**
 * This is the main view for the callers plugin. It builds a tree of callers/callees
 * and allows the user to double click an entry to go to the selected method.
 *
 */
public class CallHierarchyViewPart extends ViewPart implements ICallHierarchyViewPart, ISelectionChangedListener {


	private class CallHierarchySelectionProvider extends SelectionProviderMediator {

		public CallHierarchySelectionProvider(StructuredViewer[] viewers) {
			super(viewers, null);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.ui.typehierarchy.SelectionProviderMediator#getSelection()
		 */
		@Override
		public ISelection getSelection() {
			ISelection selection= super.getSelection();
			if (!selection.isEmpty()) {
				return CallHierarchyUI.convertSelection(selection);
			}
			return selection;
		}
	}


	/*
	 * @since 3.5
	 */
	private final class CallHierarchyOpenEditorHelper extends OpenAndLinkWithEditorHelper {
		public CallHierarchyOpenEditorHelper(StructuredViewer viewer) {
			super(viewer);
		}

		@Override
		protected void activate(ISelection selection) {
			final Object selectedElement= SelectionUtil.getSingleElement(selection);
			if (selectedElement != null)
				CallHierarchyUI.openInEditor(selectedElement, getSite().getShell(), true);
		}

		@Override
		protected void linkToEditor(ISelection selection) {
			// not supported by this part
		}

		@Override
		protected void open(ISelection selection, boolean activate) {
			if (selection instanceof IStructuredSelection) {
				for (Iterator<?> iter= ((IStructuredSelection)selection).iterator(); iter.hasNext();) {
					boolean noError= CallHierarchyUI.openInEditor(iter.next(), getSite().getShell(), OpenStrategy.activateOnOpen());
					if (!noError)
						return;
				}
			}
		}

	}


    private static final String DIALOGSTORE_VIEWORIENTATION = "CallHierarchyViewPart.orientation"; //$NON-NLS-1$
    private static final String DIALOGSTORE_CALL_MODE = "CallHierarchyViewPart.call_mode"; //$NON-NLS-1$
    private static final String DIALOGSTORE_FIELD_MODE = "CallHierarchyViewPart.field_mode"; //$NON-NLS-1$
	/**
	 * The key to be used is <code>DIALOGSTORE_RATIO + fCurrentOrientation</code>.
	 */
	private static final String DIALOGSTORE_RATIO= "CallHierarchyViewPart.ratio"; //$NON-NLS-1$

    static final int VIEW_ORIENTATION_VERTICAL = 0;
    static final int VIEW_ORIENTATION_HORIZONTAL = 1;
    static final int VIEW_ORIENTATION_SINGLE = 2;
    static final int VIEW_ORIENTATION_AUTOMATIC = 3;
    static final int CALL_MODE_CALLERS = 0;
    static final int CALL_MODE_CALLEES = 1;
    static final String GROUP_SEARCH_SCOPE = "MENU_SEARCH_SCOPE"; //$NON-NLS-1$
	static final String ID_CALL_HIERARCHY = "org.eclipse.jdt.callhierarchy.view"; //$NON-NLS-1$

	private static final String GROUP_FOCUS = "group.focus"; //$NON-NLS-1$
    private static final int PAGE_EMPTY = 0;
    private static final int PAGE_VIEWER = 1;
    private Label fNoHierarchyShownLabel;
    private PageBook fPagebook;
    private final IDialogSettings fDialogSettings;
    private int fCurrentOrientation;
    int fOrientation= VIEW_ORIENTATION_AUTOMATIC;
    private int fCurrentCallMode;
    private int fCurrentFieldMode;
    private MethodWrapper[] fCalleeRoots;
    private MethodWrapper[] fCallerRoots;
    private IMemento fMemento;
    private IMember[] fInputElements;
    private CallHierarchySelectionProvider fSelectionProviderMediator;
    private LocationViewer fLocationViewer;
    private SashForm fHierarchyLocationSplitter;
    private Clipboard fClipboard;
    private SearchScopeActionGroup fSearchScopeActions;
    private ToggleOrientationAction[] fToggleOrientationActions;
    private ToggleCallModeAction[] fToggleCallModeActions;
    private SelectFieldModeAction[] fToggleFieldModeActions;
    private CallHierarchyFiltersActionGroup fFiltersActionGroup;
    private HistoryDropDownAction fHistoryDropDownAction;
    private RefreshElementAction fRefreshSingleElementAction;
    private RefreshViewAction fRefreshViewAction;
    private OpenLocationAction fOpenLocationAction;
	private LocationCopyAction fLocationCopyAction;
    private FocusOnSelectionAction fFocusOnSelectionAction;
    private CopyCallHierarchyAction fCopyAction;
    private CancelSearchAction fCancelSearchAction;
    private ExpandWithConstructorsAction fExpandWithConstructorsAction;
    private RemoveFromViewAction fRemoveFromViewAction;
    private ShowSearchInDialogAction fShowSearchInDialogAction;
    private CompositeActionGroup fActionGroups;
    private CallHierarchyViewer fCallHierarchyViewer;
    private boolean fShowCallDetails;
	protected Composite fParent;
	private IPartListener2 fPartListener;
	private boolean fIsPinned;
	private PinCallHierarchyViewAction fPinViewAction;


    public CallHierarchyViewPart() {
        super();

        fDialogSettings = JavaPlugin.getDefault().getDialogSettings();

        fIsPinned= false;
    }

    @Override
	public void setFocus() {
        fPagebook.setFocus();
    }

    /**
     * Sets the history entries
     * @param entries the new history entries
     */
    public void setHistoryEntries(IMember[][] entries) {
        getMethodHistory().clear();

        for (int i = 0; i < entries.length; i++) {
            getMethodHistory().add(entries[i]);
        }

        updateHistoryEntries();
    }

    /**
     * Gets all history entries.
     * @return all history entries
     */
    public IMember[][] getHistoryEntries() {
        if (getMethodHistory().size() > 0) {
            updateHistoryEntries();
        }

        return getMethodHistory().toArray(new IMember[getMethodHistory().size()][]);
    }

    public void setInputElements(IMember[] members) {
    	IMember[] oldMembers= fInputElements;
    	fInputElements= members;

    	if (members == null || members.length == 0) {
            showPage(PAGE_EMPTY);
            return;
        }

    	if (! Arrays.equals(members, oldMembers)) {
    		addHistoryEntry(members);
    	}

    	refresh();
    }

    public IMember[] getInputElements() {
        return fInputElements;
    }

    public MethodWrapper[] getCurrentMethodWrappers() {
        if (fCurrentCallMode == CALL_MODE_CALLERS) {
            return fCallerRoots;
        } else {
            return fCalleeRoots;
        }
    }

    /**
     * called from ToggleOrientationAction.
     * @param orientation VIEW_ORIENTATION_HORIZONTAL or VIEW_ORIENTATION_VERTICAL
     */
    void setOrientation(int orientation) {
        if (fCurrentOrientation != orientation) {
            if ((fLocationViewer != null) && !fLocationViewer.getControl().isDisposed() &&
                        (fHierarchyLocationSplitter != null) &&
                        !fHierarchyLocationSplitter.isDisposed()) {
                if (orientation == VIEW_ORIENTATION_SINGLE) {
                    setShowCallDetails(false);
                } else {
                    if (fCurrentOrientation == VIEW_ORIENTATION_SINGLE) {
                        setShowCallDetails(true);
                    }

                    boolean horizontal = orientation == VIEW_ORIENTATION_HORIZONTAL;
                    fHierarchyLocationSplitter.setOrientation(horizontal ? SWT.HORIZONTAL
                                                                         : SWT.VERTICAL);
                }

                fHierarchyLocationSplitter.layout();
            }

            updateCheckedState();

            fCurrentOrientation = orientation;

			restoreSplitterRatio();
        }
    }

	private void updateCheckedState() {
		for (int i= 0; i < fToggleOrientationActions.length; i++) {
			fToggleOrientationActions[i].setChecked(fOrientation == fToggleOrientationActions[i].getOrientation());
		}
	}

    /**
     * called from ToggleCallModeAction.
     * @param mode CALL_MODE_CALLERS or CALL_MODE_CALLEES
     */
    void setCallMode(int mode) {
        if (fCurrentCallMode != mode) {
            for (int i = 0; i < fToggleCallModeActions.length; i++) {
                fToggleCallModeActions[i].setChecked(mode == fToggleCallModeActions[i].getMode());
            }

            fCurrentCallMode = mode;
            fDialogSettings.put(DIALOGSTORE_CALL_MODE, mode);

            updateView();
        }
    }

	/**
	 * Returns the current call mode.
	 * 
	 * @return the current call mode: CALL_MODE_CALLERS or CALL_MODE_CALLEES
	 * @since 3.5
	 */
	int getCallMode() {
		return fCurrentCallMode;
	}


    /**
     * called from SelectFieldModeAction.
     * @param mode IJavaSearchConstants.{REFERENCES,WRITE_ACCESS,READ_ACCESS}
     */
    void setFieldMode(int mode) {
        if (fCurrentFieldMode != mode) {
            for (int i = 0; i < fToggleFieldModeActions.length; i++) {
                fToggleFieldModeActions[i].setChecked(mode == fToggleFieldModeActions[i].getMode());
            }

            fCurrentFieldMode = mode;
            fDialogSettings.put(DIALOGSTORE_FIELD_MODE, mode);

            updateView();
        }
    }

	/**
	 * Fetches the search scope with the appropriate include mask.
	 * 
	 * @param includeMask the include mask
	 * @return the search scope with the appropriate include mask
	 * @since 3.7
	 */
    public IJavaSearchScope getSearchScope(int includeMask) {
        return fSearchScopeActions.getSearchScope(includeMask);
    }

    public void setShowCallDetails(boolean show) {
        fShowCallDetails = show;
        showOrHideCallDetailsView();
    }

    private void initDragAndDrop() {
        addDragAdapters(fCallHierarchyViewer);
        addDropAdapters(fCallHierarchyViewer);
        addDropAdapters(fLocationViewer);

        //dnd on empty hierarchy
        DropTarget dropTarget = new DropTarget(fPagebook, DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK | DND.DROP_DEFAULT);
        dropTarget.setTransfer(new Transfer[] { LocalSelectionTransfer.getInstance() });
        dropTarget.addDropListener(new CallHierarchyTransferDropAdapter(this, fCallHierarchyViewer));
    }

	private void addDropAdapters(StructuredViewer viewer) {
		Transfer[] transfers= new Transfer[] { LocalSelectionTransfer.getInstance(), PluginTransfer.getInstance() };
		int ops= DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK | DND.DROP_DEFAULT;

		DelegatingDropAdapter delegatingDropAdapter= new DelegatingDropAdapter();
		delegatingDropAdapter.addDropTargetListener(new CallHierarchyTransferDropAdapter(this, viewer));
		delegatingDropAdapter.addDropTargetListener(new PluginTransferDropAdapter(viewer));

		viewer.addDropSupport(ops, transfers, delegatingDropAdapter);
	}

	private void addDragAdapters(StructuredViewer viewer) {
		int ops= DND.DROP_COPY | DND.DROP_LINK;

		Transfer[] transfers= new Transfer[] { LocalSelectionTransfer.getInstance(), ResourceTransfer.getInstance(), FileTransfer.getInstance()};

		DelegatingDragAdapter dragAdapter= new DelegatingDragAdapter() {
			@Override
			public void dragStart(DragSourceEvent event) {
				IStructuredSelection selection= (IStructuredSelection) fSelectionProviderMediator.getSelection();
				if (selection.isEmpty()) {
					event.doit= false;
					return;
				}
				super.dragStart(event);
			}
		};
		dragAdapter.addDragSourceListener(new SelectionTransferDragAdapter(fSelectionProviderMediator));
		dragAdapter.addDragSourceListener(new EditorInputTransferDragAdapter(fSelectionProviderMediator));
		dragAdapter.addDragSourceListener(new ResourceTransferDragAdapter(fSelectionProviderMediator));
		dragAdapter.addDragSourceListener(new FileTransferDragAdapter(fSelectionProviderMediator));

		viewer.addDragSupport(ops, transfers, dragAdapter);
	}

    @Override
	public void createPartControl(Composite parent) {
    	fParent= parent;
    	addResizeListener(parent);
        fPagebook = new PageBook(parent, SWT.NONE);

        // Page 1: Viewers
        createHierarchyLocationSplitter(fPagebook);
        createCallHierarchyViewer(fHierarchyLocationSplitter);
        createLocationViewer(fHierarchyLocationSplitter);

        // Page 2: Nothing selected
        fNoHierarchyShownLabel = new Label(fPagebook, SWT.TOP + SWT.LEFT + SWT.WRAP);
        fNoHierarchyShownLabel.setText(CallHierarchyMessages.CallHierarchyViewPart_empty); //

        showPage(PAGE_EMPTY);

        PlatformUI.getWorkbench().getHelpSystem().setHelp(fPagebook, IJavaHelpContextIds.CALL_HIERARCHY_VIEW);

        fSelectionProviderMediator = new CallHierarchySelectionProvider(new StructuredViewer[] {
                    fCallHierarchyViewer, fLocationViewer
                });

        IStatusLineManager slManager = getViewSite().getActionBars().getStatusLineManager();
        fSelectionProviderMediator.addSelectionChangedListener(new StatusBarUpdater(slManager));
        getSite().setSelectionProvider(fSelectionProviderMediator);

        fCallHierarchyViewer.initContextMenu(
        		new IMenuListener() {
		            public void menuAboutToShow(IMenuManager menu) {
		                fillCallHierarchyViewerContextMenu(menu);
		            }
		        }, getSite(), fSelectionProviderMediator);


        fClipboard= new Clipboard(parent.getDisplay());

        makeActions();
        fillViewMenu();
        fillActionBars();
		initDragAndDrop();

        initOrientation();
        initCallMode();
        initFieldMode();

        if (fMemento != null) {
            restoreState(fMemento);
        }
		restoreSplitterRatio();
		addPartListener();
   }

	private void restoreSplitterRatio() {
		String ratio= fDialogSettings.get(DIALOGSTORE_RATIO + fCurrentOrientation);
		if (ratio == null)
			return;
		int intRatio= Integer.parseInt(ratio);
        fHierarchyLocationSplitter.setWeights(new int[] {intRatio, 1000 - intRatio});
	}

	private void saveSplitterRatio() {
		if (fHierarchyLocationSplitter != null && ! fHierarchyLocationSplitter.isDisposed()) {
	        int[] weigths = fHierarchyLocationSplitter.getWeights();
	        int ratio = (weigths[0] * 1000) / (weigths[0] + weigths[1]);
			String key= DIALOGSTORE_RATIO + fCurrentOrientation;
	        fDialogSettings.put(key, ratio);
		}
	}

	/**
	 * Tells whether the given part reference references this view.
	 * 
	 * @param partRef the workbench part reference
	 * @return <code>true</code> if the given part reference references this view
	 * @since 3.7
	 */
	private boolean isThisView(IWorkbenchPartReference partRef) {
		if (!ID_CALL_HIERARCHY.equals(partRef.getId()))
			return false;
		String partRefSecondaryId= ((IViewReference)partRef).getSecondaryId();
		String thisSecondaryId= getViewSite().getSecondaryId();
		return thisSecondaryId == null && partRefSecondaryId == null || thisSecondaryId != null && thisSecondaryId.equals(partRefSecondaryId);
	}

	private void addPartListener() {
		fPartListener= new IPartListener2() {
			/* (non-Javadoc)
			 * @see org.eclipse.ui.IPartListener2#partActivated(org.eclipse.ui.IWorkbenchPartReference)
			 */
			public void partActivated(IWorkbenchPartReference partRef) {
				if (isThisView(partRef))
					CallHierarchyUI.getDefault().callHierarchyViewActivated(CallHierarchyViewPart.this);
			}

			public void partBroughtToTop(IWorkbenchPartReference partRef) { }

			/* (non-Javadoc)
			 * @see org.eclipse.ui.IPartListener2#partClosed(org.eclipse.ui.IWorkbenchPartReference)
			 */
			public void partClosed(IWorkbenchPartReference partRef) {
				if (isThisView(partRef)) {
					CallHierarchyUI.getDefault().callHierarchyViewClosed(CallHierarchyViewPart.this);
					saveViewSettings();
				}
			}

			/* (non-Javadoc)
			 * @see org.eclipse.ui.IPartListener2#partDeactivated(org.eclipse.ui.IWorkbenchPartReference)
			 */
			public void partDeactivated(IWorkbenchPartReference partRef) {
				if (isThisView(partRef))
					saveViewSettings();
			}

			public void partOpened(IWorkbenchPartReference partRef) { }
			public void partHidden(IWorkbenchPartReference partRef) { }
			public void partVisible(IWorkbenchPartReference partRef) { }
			public void partInputChanged(IWorkbenchPartReference partRef) { }
		};
		getViewSite().getPage().addPartListener(fPartListener);
	}

	protected void saveViewSettings() {
		saveSplitterRatio();
		fDialogSettings.put(DIALOGSTORE_VIEWORIENTATION, fOrientation);
	}

	private void addResizeListener(Composite parent) {
		parent.addControlListener(new ControlListener() {
			public void controlMoved(ControlEvent e) {
			}
			public void controlResized(ControlEvent e) {
				computeOrientation();
			}
		});
	}

	void computeOrientation() {
		saveSplitterRatio();
		fDialogSettings.put(DIALOGSTORE_VIEWORIENTATION, fOrientation);
		if (fOrientation != VIEW_ORIENTATION_AUTOMATIC) {
			setOrientation(fOrientation);
		}
		else {
			if (fOrientation == VIEW_ORIENTATION_SINGLE)
				return;
			Point size= fParent.getSize();
			if (size.x != 0 && size.y != 0) {
				if (size.x > size.y)
					setOrientation(VIEW_ORIENTATION_HORIZONTAL);
				else
					setOrientation(VIEW_ORIENTATION_VERTICAL);
			}
		}
	}

    private void showPage(int page) {
		boolean isEmpty= page == PAGE_EMPTY;
		Control control= isEmpty ? (Control) fNoHierarchyShownLabel : fHierarchyLocationSplitter;
		if (isEmpty) {
			setContentDescription(""); //$NON-NLS-1$
			setTitleToolTip(getPartName());
			getViewSite().getActionBars().getStatusLineManager().setMessage(""); //$NON-NLS-1$
			getViewer().clearViewer();
		}
		fPagebook.showPage(control);
		if (fRefreshViewAction != null)
			fRefreshViewAction.setEnabled(!isEmpty);
		if (fRefreshSingleElementAction != null)
			fRefreshSingleElementAction.setEnabled(!isEmpty);
    }

    /**
     * Restores the type hierarchy settings from a memento.
     * @param memento the memento
     */
    private void restoreState(IMemento memento) {
        fSearchScopeActions.restoreState(memento);
    }

    private void initCallMode() {
        int mode;

        try {
            mode = fDialogSettings.getInt(DIALOGSTORE_CALL_MODE);

            if ((mode < 0) || (mode > 1)) {
                mode = CALL_MODE_CALLERS;
            }
        } catch (NumberFormatException e) {
            mode = CALL_MODE_CALLERS;
        }

        // force the update
        fCurrentCallMode = -1;

        // will fill the main tool bar
        setCallMode(mode);
    }

    private void initFieldMode() {
        int mode;

        try {
            mode = fDialogSettings.getInt(DIALOGSTORE_FIELD_MODE);

            switch (mode) {
            	case IJavaSearchConstants.REFERENCES:
            	case IJavaSearchConstants.READ_ACCESSES:
            	case IJavaSearchConstants.WRITE_ACCESSES:
            		break; // OK
            default:
            	mode = IJavaSearchConstants.REFERENCES;
            }
        } catch (NumberFormatException e) {
            mode = IJavaSearchConstants.REFERENCES;
        }

        // force the update
        fCurrentFieldMode = -1;

        // will fill the main tool bar
        setFieldMode(mode);
    }

    private void initOrientation() {

        try {
            fOrientation = fDialogSettings.getInt(DIALOGSTORE_VIEWORIENTATION);

            if ((fOrientation < 0) || (fOrientation > 3)) {
            	fOrientation = VIEW_ORIENTATION_AUTOMATIC;
            }
        } catch (NumberFormatException e) {
        	fOrientation = VIEW_ORIENTATION_AUTOMATIC;
        }

        // force the update
        fCurrentOrientation = -1;
        setOrientation(fOrientation);
    }

    private void fillViewMenu() {
        IActionBars actionBars = getViewSite().getActionBars();
        IMenuManager viewMenu = actionBars.getMenuManager();
        viewMenu.add(new Separator());

        for (int i = 0; i < fToggleCallModeActions.length; i++) {
            viewMenu.add(fToggleCallModeActions[i]);
        }

        viewMenu.add(new Separator());

        MenuManager layoutSubMenu= new MenuManager(CallHierarchyMessages.CallHierarchyViewPart_layout_menu);
        for (int i = 0; i < fToggleOrientationActions.length; i++) {
        	layoutSubMenu.add(fToggleOrientationActions[i]);
        }
        viewMenu.add(layoutSubMenu);

		viewMenu.add(new Separator(IContextMenuConstants.GROUP_SEARCH));

        MenuManager fieldSubMenu= new MenuManager(CallHierarchyMessages.CallHierarchyViewPart_field_menu);
        for (int i = 0; i < fToggleFieldModeActions.length; i++) {
        	fieldSubMenu.add(fToggleFieldModeActions[i]);
        }
        viewMenu.add(fieldSubMenu);
        viewMenu.add(fShowSearchInDialogAction);
    }

	@Override
	public void dispose() {
		if (fActionGroups != null)
			fActionGroups.dispose();

		if (fClipboard != null)
			fClipboard.dispose();

		if (fPartListener != null) {
			getViewSite().getPage().removePartListener(fPartListener);
			fPartListener= null;
		}
		super.dispose();
	}

    /**
     * Goes to the selected entry, without updating the order of history entries.
     * @param entry the history entry
     */
    public void gotoHistoryEntry(IMember[] entry) {
    	for (Iterator<IMember[]> iter= getMethodHistory().iterator(); iter.hasNext(); ) {
			if (Arrays.equals(entry, iter.next())) {
				setInputElements(entry);
				return;
			}
		}
    }

    /* (non-Javadoc)
     * Method declared on IViewPart.
     */
    @Override
	public void init(IViewSite site, IMemento memento)
        throws PartInitException {
        super.init(site, memento);
        fMemento = memento;
    }

    /**
     *
     */
    public void refresh() {
        setCalleeRoots(null);
        setCallerRoots(null);

        updateView();
    }

    @Override
	public void saveState(IMemento memento) {
        if (fPagebook == null) {
            // part has not been created
            if (fMemento != null) { //Keep the old state;
                memento.putMemento(fMemento);
            }

            return;
        }

        fSearchScopeActions.saveState(memento);
    }

    public void selectionChanged(SelectionChangedEvent e) {
        if (e.getSelectionProvider() == fCallHierarchyViewer) {
            methodSelectionChanged(e.getSelection());
        }
    }

    private void methodSelectionChanged(ISelection selection) {
        if (selection instanceof IStructuredSelection && ((IStructuredSelection) selection).size() == 1) {
            Object selectedElement = ((IStructuredSelection) selection).getFirstElement();

            if (selectedElement instanceof MethodWrapper) {
                MethodWrapper methodWrapper = (MethodWrapper) selectedElement;

                revealElementInEditor(methodWrapper, fCallHierarchyViewer);
                updateLocationsView(methodWrapper);
            } else {
                updateLocationsView(null);
            }
        } else {
        	updateLocationsView(null);
        }
    }

    private void revealElementInEditor(Object elem, Viewer originViewer) {
        // only allow revealing when the type hierarchy is the active pagae
        // no revealing after selection events due to model changes
        if (getSite().getPage().getActivePart() != this) {
            return;
        }

        if (fSelectionProviderMediator.getViewerInFocus() != originViewer) {
            return;
        }

        if (elem instanceof MethodWrapper) {
            CallLocation callLocation = CallHierarchy.getCallLocation(elem);

            if (callLocation != null) {
                IEditorPart editorPart = CallHierarchyUI.isOpenInEditor(callLocation);

                if (editorPart != null) {
                    getSite().getPage().bringToTop(editorPart);

                    if (editorPart instanceof ITextEditor) {
                        ITextEditor editor = (ITextEditor) editorPart;
                        editor.selectAndReveal(callLocation.getStart(),
                            (callLocation.getEnd() - callLocation.getStart()));
                    }
                }
            } else {
                IEditorPart editorPart = CallHierarchyUI.isOpenInEditor(elem);
                getSite().getPage().bringToTop(editorPart);
                EditorUtility.revealInEditor(editorPart,
                    ((MethodWrapper) elem).getMember());
            }
        } else if (elem instanceof IJavaElement) {
            IEditorPart editorPart = EditorUtility.isOpenInEditor(elem);

            if (editorPart != null) {
                //            getSite().getPage().removePartListener(fPartListener);
                getSite().getPage().bringToTop(editorPart);
                EditorUtility.revealInEditor(editorPart, (IJavaElement) elem);

                //            getSite().getPage().addPartListener(fPartListener);
            }
        }
    }

    /**
	 * {@inheritDoc}
	 */
    @Override
	public Object getAdapter(Class adapter) {
    	if (adapter == IShowInSource.class) {
    		return getShowInSource();
    	}
    	if (adapter == IContextProvider.class) {
    		return JavaUIHelp.getHelpContextProvider(this, IJavaHelpContextIds.CALL_HIERARCHY_VIEW);
    	}
		if (adapter == IShowInTargetList.class) {
			return new IShowInTargetList() {
				public String[] getShowInTargetIds() {
					return new String[] { JavaUI.ID_PACKAGES, JavaPlugin.ID_RES_NAV };
				}
			};
		}
    	return super.getAdapter(adapter);
    }

	/**
	 * @return the <code>IShowInSource</code> for this view.
	 */
	private IShowInSource getShowInSource() {
		return new IShowInSource() {
			public ShowInContext getShowInContext() {
				return new ShowInContext(null, fSelectionProviderMediator.getSelection());
			}
		};
	}
	
    /**
     * Returns the current selection.
     * @return selection
     */
    protected ISelection getSelection() {
    	StructuredViewer viewerInFocus= fSelectionProviderMediator.getViewerInFocus();
		if (viewerInFocus != null) {
			return viewerInFocus.getSelection();
		}
		return StructuredSelection.EMPTY;
    }

    protected void fillLocationViewerContextMenu(IMenuManager menu) {
        JavaPlugin.createStandardGroups(menu);

        menu.appendToGroup(IContextMenuConstants.GROUP_SHOW, fOpenLocationAction);
		menu.appendToGroup(IContextMenuConstants.GROUP_SHOW, fRefreshSingleElementAction);
        menu.appendToGroup(IContextMenuConstants.GROUP_REORGANIZE, fLocationCopyAction);
    }

    private IActionBars getActionBars() {
        return getViewSite().getActionBars();
    }

    private void setCalleeRoots(MethodWrapper[] calleeRoots) {
        this.fCalleeRoots = calleeRoots;
    }

    private MethodWrapper[] getCalleeRoots() {
        if (fCalleeRoots == null) {
            fCalleeRoots = CallHierarchy.getDefault().getCalleeRoots(fInputElements);
        }

        return fCalleeRoots;
    }

    private void setCallerRoots(MethodWrapper[] callerRoots) {
        this.fCallerRoots = callerRoots;
    }

    private MethodWrapper[] getCallerRoots() {
    	if (fCallerRoots != null && fCallerRoots.length > 0) {
    		// all caller roots have the same field mode, just check the first:
    		if (fCallerRoots[0].getFieldSearchMode() != fCurrentFieldMode) {
    			fCallerRoots= null; // field mode changed, re-initialize below
    		}
    	}
        if (fCallerRoots == null) {
            fCallerRoots = CallHierarchy.getDefault().getCallerRoots(fInputElements);
        	for (int i= 0; i < fCallerRoots.length; i++) {
        		fCallerRoots[i].setFieldSearchMode(fCurrentFieldMode);
			}
        }

        return fCallerRoots;
    }

    /**
     * Adds the entry if new. Inserted at the beginning of the history entries list.
     * @param entry the entry to add
     */
    private void addHistoryEntry(IMember[] entry) {

		updateHistoryEntries(entry, entry);
    }

	/**
	 * Updates the input, history and description for the new input.
	 * 
	 * @param currentInput the current input
	 * @param entry the new input elements
	 * @since 3.7
	 */
	void updateInputHistoryAndDescription(IMember[] currentInput, IMember[] entry) {
		updateHistoryEntries(currentInput, entry);
		fInputElements= entry;
		setContentDescription(getIncludeMask());
	}

	/**
	 * Updates the history with the latest input.
	 * 
	 * @param currentInput the current input
	 * @param entry the new input elements
	 * @since 3.7
	 */
	private void updateHistoryEntries(IMember[] currentInput, IMember[] entry) {
		for (Iterator<IMember[]> iter= getMethodHistory().iterator(); iter.hasNext();) {
			if (Arrays.equals(currentInput, iter.next())) {
				iter.remove();
			}
		}

        getMethodHistory().add(0, entry);
        fHistoryDropDownAction.setEnabled(true);
    }

    private void createLocationViewer(Composite parent) {
        fLocationViewer= new LocationViewer(parent);


        fLocationViewer.initContextMenu(new IMenuListener() {
                public void menuAboutToShow(IMenuManager menu) {
                    fillLocationViewerContextMenu(menu);
                }
            }, ID_CALL_HIERARCHY, getSite());
    }

    private void createHierarchyLocationSplitter(Composite parent) {
        fHierarchyLocationSplitter = new SashForm(parent, SWT.NONE);
    }

    private void createCallHierarchyViewer(Composite parent) {
        fCallHierarchyViewer = new CallHierarchyViewer(parent, this);

        fCallHierarchyViewer.addSelectionChangedListener(this);
    }

    protected void fillCallHierarchyViewerContextMenu(IMenuManager menu) {
        JavaPlugin.createStandardGroups(menu);

		menu.appendToGroup(IContextMenuConstants.GROUP_SHOW, fRefreshSingleElementAction);
        menu.appendToGroup(IContextMenuConstants.GROUP_SHOW, new Separator(GROUP_FOCUS));

        if (fFocusOnSelectionAction.canActionBeAdded()) {
            menu.appendToGroup(GROUP_FOCUS, fFocusOnSelectionAction);
        }
        if (fExpandWithConstructorsAction.canActionBeAdded()) {
        	menu.appendToGroup(GROUP_FOCUS, fExpandWithConstructorsAction);
        }
        
        if (fRemoveFromViewAction.canActionBeAdded()){
        	menu.appendToGroup(GROUP_FOCUS, fRemoveFromViewAction);
        }

        fActionGroups.setContext(new ActionContext(getSelection()));
        fActionGroups.fillContextMenu(menu);
        fActionGroups.setContext(null);

		if (fCopyAction.canActionBeAdded()) {
			menu.appendToGroup(ICommonMenuConstants.GROUP_EDIT, fCopyAction);
		}
    }

    private void fillActionBars() {
        IActionBars actionBars = getActionBars();
		actionBars.setGlobalActionHandler(ActionFactory.REFRESH.getId(), fRefreshSingleElementAction);
		actionBars.setGlobalActionHandler(ActionFactory.DELETE.getId(), fRemoveFromViewAction);

        IToolBarManager toolBar = actionBars.getToolBarManager();

        fActionGroups.fillActionBars(actionBars);

        toolBar.add(fRefreshViewAction);
        toolBar.add(fCancelSearchAction);
        for (int i = 0; i < fToggleCallModeActions.length; i++) {
            toolBar.add(fToggleCallModeActions[i]);
        }
        toolBar.add(fHistoryDropDownAction);
        toolBar.add(fPinViewAction);
    }

    private void makeActions() {
        fRefreshViewAction = new RefreshViewAction(this);
        fRefreshSingleElementAction= new RefreshElementAction(fCallHierarchyViewer);

		new CallHierarchyOpenEditorHelper(fLocationViewer);
		new CallHierarchyOpenEditorHelper(fCallHierarchyViewer);

		fOpenLocationAction= new OpenLocationAction(this, getSite());
		fLocationCopyAction= fLocationViewer.initCopyAction(getViewSite(), fClipboard);
        fFocusOnSelectionAction = new FocusOnSelectionAction(this);
        fCopyAction= new CopyCallHierarchyAction(this, fClipboard, fCallHierarchyViewer);
        fSearchScopeActions = new SearchScopeActionGroup(this, fDialogSettings);
        fShowSearchInDialogAction= new ShowSearchInDialogAction(this, fCallHierarchyViewer);
        fFiltersActionGroup = new CallHierarchyFiltersActionGroup(this,
                fCallHierarchyViewer);
        fHistoryDropDownAction = new HistoryDropDownAction(this);
        fHistoryDropDownAction.setEnabled(false);
        fCancelSearchAction = new CancelSearchAction(this);
        setCancelEnabled(false);
        fExpandWithConstructorsAction= new ExpandWithConstructorsAction(this, fCallHierarchyViewer);
        fRemoveFromViewAction= new RemoveFromViewAction(this, fCallHierarchyViewer);
        fPinViewAction= new PinCallHierarchyViewAction(this);
        fToggleOrientationActions = new ToggleOrientationAction[] {
                new ToggleOrientationAction(this, VIEW_ORIENTATION_VERTICAL),
                new ToggleOrientationAction(this, VIEW_ORIENTATION_HORIZONTAL),
                new ToggleOrientationAction(this, VIEW_ORIENTATION_AUTOMATIC),
                new ToggleOrientationAction(this, VIEW_ORIENTATION_SINGLE)
            };
		fRemoveFromViewAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_DELETE);
        fToggleCallModeActions = new ToggleCallModeAction[] {
                new ToggleCallModeAction(this, CALL_MODE_CALLERS),
                new ToggleCallModeAction(this, CALL_MODE_CALLEES)
            };
        fToggleFieldModeActions = new SelectFieldModeAction[] {
                new SelectFieldModeAction(this, IJavaSearchConstants.REFERENCES),
                new SelectFieldModeAction(this, IJavaSearchConstants.READ_ACCESSES),
                new SelectFieldModeAction(this, IJavaSearchConstants.WRITE_ACCESSES)
            };
        fActionGroups = new CompositeActionGroup(new ActionGroup[] {
                    new OpenEditorActionGroup(this),
                    new OpenViewActionGroup(this),
					new CCPActionGroup(this, true),
                    new GenerateActionGroup(this),
                    new RefactorActionGroup(this),
                    new JavaSearchActionGroup(this),
                    fSearchScopeActions, fFiltersActionGroup
                });
    }

    private void showOrHideCallDetailsView() {
        if (fShowCallDetails) {
            fHierarchyLocationSplitter.setMaximizedControl(null);
        } else {
            fHierarchyLocationSplitter.setMaximizedControl(fCallHierarchyViewer.getControl());
        }
    }

    private void updateLocationsView(MethodWrapper methodWrapper) {
        if (methodWrapper != null && methodWrapper.getMethodCall().hasCallLocations()) {
            fLocationViewer.setInput(methodWrapper.getMethodCall().getCallLocations());
        } else {
            fLocationViewer.clearViewer();
        }
    }

    private void updateHistoryEntries() {
        for (int i = getMethodHistory().size() - 1; i >= 0; i--) {
            IMember[] members = getMethodHistory().get(i);
            for (int j= 0; j < members.length; j++) {
				IMember member= members[j];
				if (! member.exists()) {
					getMethodHistory().remove(i);
					break;
				}
			}
        }

        fHistoryDropDownAction.setEnabled(!getMethodHistory().isEmpty());
    }

	private void updateView() {
		if (fInputElements != null) {
			showPage(PAGE_VIEWER);

			int includeMask= getIncludeMask();
			CallHierarchy.getDefault().setSearchScope(getSearchScope(includeMask));

			// set input to null so that setComparator does not cause a refresh on the old contents:
			fCallHierarchyViewer.setInput(null);
			if (fCurrentCallMode == CALL_MODE_CALLERS) {
				// sort caller hierarchy alphabetically (bug 111423) and make RealCallers the last in 'Expand With Constructors' mode
				fCallHierarchyViewer.setComparator(new ViewerComparator() {
					@Override
					public int category(Object element) {
						return element instanceof RealCallers ? 1 : 0;
					}
				});
    			fCallHierarchyViewer.setMethodWrappers(getCallerRoots());
			} else {
				fCallHierarchyViewer.setComparator(null);
				fCallHierarchyViewer.setMethodWrappers(getCalleeRoots());
			}
			setContentDescription(includeMask);
		}
    }

	/**
	 * Sets the content description.
	 * 
	 * @param includeMask the include mask
	 * @since 3.7
	 */
	private void setContentDescription(int includeMask) {
		setContentDescription(computeContentDescription(includeMask));
	}



	/**
	 * Gets the include mask.
	 * 
	 * @return the include mask
	 * @since 3.7
	 */
	private int getIncludeMask() {
		return fShowSearchInDialogAction.getSearchInDialog().getIncludeMask();
	}

	/**
	 * Computes the content description for the call hierarchy computation.
	 * 
	 * @param includeMask the include mask
	 * @return the content description
	 * @since 3.7
	 */
	private String computeContentDescription(int includeMask) {
		// see also HistoryAction.getElementLabel(IMember[])
		String scopeDescription= fSearchScopeActions.getFullDescription(includeMask);

		if (fInputElements.length == 1) {
			IMember element= fInputElements[0];
			String elementName= JavaElementLabels.getElementLabel(element, JavaElementLabels.ALL_DEFAULT);
			String[] args= new String[] { elementName, scopeDescription };
			if (fCurrentCallMode == CALL_MODE_CALLERS) {
				switch (element.getElementType()) {
					case IJavaElement.TYPE:
						return Messages.format(CallHierarchyMessages.CallHierarchyViewPart_callsToConstructors, args);
					case IJavaElement.FIELD:
						switch (this.fCurrentFieldMode) {
				            case IJavaSearchConstants.READ_ACCESSES:
								return Messages.format(CallHierarchyMessages.CallHierarchyViewPart_callsToFieldRead, args);
				            case IJavaSearchConstants.WRITE_ACCESSES:
								return Messages.format(CallHierarchyMessages.CallHierarchyViewPart_callsToFieldWrite, args);
							default: // all references
								return Messages.format(CallHierarchyMessages.CallHierarchyViewPart_callsToField, args);
						}
					case IJavaElement.METHOD:
					case IJavaElement.INITIALIZER:
					default:
						return Messages.format(CallHierarchyMessages.CallHierarchyViewPart_callsToMethod, args);
				}
			} else {
				switch (element.getElementType()) {
					case IJavaElement.TYPE:
						return Messages.format(CallHierarchyMessages.CallHierarchyViewPart_callsFromConstructors, args);
					case IJavaElement.FIELD:
					case IJavaElement.METHOD:
					case IJavaElement.INITIALIZER:
					default:
						return Messages.format(CallHierarchyMessages.CallHierarchyViewPart_callsFromMethod, args);
				}
			}

		} else {
			if (fCurrentCallMode == CALL_MODE_CALLERS) {
				switch (fInputElements.length) {
		        	case 0:
		        		Assert.isTrue(false);
		        		return null;
		        	case 2:
		        		return Messages.format(CallHierarchyMessages.CallHierarchyViewPart_callsToMembers_2,
		        				new String[] { getShortLabel(fInputElements[0]), getShortLabel(fInputElements[1]), scopeDescription });

		        	default:
		        		return Messages.format(CallHierarchyMessages.CallHierarchyViewPart_callsToMembers_more,
		        				new String[] { getShortLabel(fInputElements[0]), getShortLabel(fInputElements[1]), scopeDescription });
				}
			} else {
				switch (fInputElements.length) {
					case 0:
						Assert.isTrue(false);
						return null;
					case 2:
						return Messages.format(CallHierarchyMessages.CallHierarchyViewPart_callsFromMembers_2,
								new String[] { getShortLabel(fInputElements[0]), getShortLabel(fInputElements[1]), scopeDescription });

					default:
						return Messages.format(CallHierarchyMessages.CallHierarchyViewPart_callsFromMembers_more,
								new String[] { getShortLabel(fInputElements[0]), getShortLabel(fInputElements[1]), scopeDescription });
				}
			}
		}
	}

	private static String getShortLabel(IMember member) {
		return JavaElementLabels.getElementLabel(member, 0L);
	}

    static CallHierarchyViewPart findAndShowCallersView(IWorkbenchPartSite site) {
        IWorkbenchPage workbenchPage = site.getPage();
        CallHierarchyViewPart callersView = null;

        try {
            callersView = (CallHierarchyViewPart) workbenchPage.showView(CallHierarchyViewPart.ID_CALL_HIERARCHY);
        } catch (PartInitException e) {
            JavaPlugin.log(e);
        }

        return callersView;
    }

    /**
     * Cancels the caller/callee search jobs that are currently running.
     */
    void cancelJobs() {
        fCallHierarchyViewer.cancelJobs();
    }

	/**
	 * Sets the enablement state of the cancel button.
	 *
	 * @param enabled <code>true</code> if cancel should be enabled
	 */
    void setCancelEnabled(boolean enabled) {
        fCancelSearchAction.setEnabled(enabled);
    }

    /**
     * Returns the call hierarchy viewer.
     * 
     * @return the call hierarchy viewer
     * @since 3.5
     */
    public CallHierarchyViewer getViewer() {
    	return fCallHierarchyViewer;
    }

	/**
	 * Returns the location viewer.
	 * 
	 * @return the location viewer
	 * @since 3.6
	 */
	protected LocationViewer getLocationViewer() {
		return fLocationViewer;
	}

	/**
	 * Marks the view as pinned.
	 * 
	 * @param pinned if <code>true</code> the view is marked as pinned
	 * @since 3.7
	 */
	void setPinned(boolean pinned) {
		fIsPinned= pinned;
	}

	/**
	 * Indicates whether the Call Hierarchy view is pinned.
	 * 
	 * @return <code>true</code> if the view is pinned, <code>false</code> otherwise
	 * @since 3.7
	 */
	boolean isPinned() {
		return fIsPinned;
	}

	/**
	 * Returns the method history.
	 * 
	 * @return the method history
	 * @since 3.7
	 */
	private List<IMember[]>getMethodHistory() {
		return CallHierarchyUI.getDefault().getMethodHistory();
	}

	/*
	 * @see org.eclipse.ui.part.WorkbenchPart#showBusy(boolean)
	 * @since 3.7
	 */
	@Override
	public void showBusy(boolean busy) {
		super.showBusy(busy);
		if (!busy)
			getProgressService().warnOfContentChange();
	}

	/**
	 * Fetches the progress service for the workbench part site.
	 * 
	 * @return the progress service for the workbench part site
	 * @since 3.7
	 */
	private IWorkbenchSiteProgressService getProgressService() {
		IWorkbenchSiteProgressService service= null;
		Object siteService= getSite().getAdapter(IWorkbenchSiteProgressService.class);
		if (siteService != null)
			service= (IWorkbenchSiteProgressService)siteService;
		return service;
	}

	/**
	 * Adds the new input elements to the current list.
	 * 
	 * @param newElements the new input elements to add
	 * @since 3.7
	 */
	void addInputElements(IMember[] newElements) {
		// Caveat: RemoveFromViewAction#run() disposes TreeItems. When we add a previously removed element,
		// we have to consider the real Tree state, not only fInputElements.
		
		List<IMember> inputElements= Arrays.asList(fInputElements);
		List<IMember> treeElements= new ArrayList<IMember>();
		TreeItem[] treeItems= fCallHierarchyViewer.getTree().getItems();
		for (int i= 0; i < treeItems.length; i++) {
			Object data= treeItems[i].getData();
			if (data instanceof MethodWrapper)
				treeElements.add(((MethodWrapper) data).getMember());
		}
		
		List<IMember> newInput= new ArrayList<IMember>();
		newInput.addAll(inputElements);
		List<IMember> addedElements= new ArrayList<IMember>();
		
		for (int i= 0; i < newElements.length; i++) {
			IMember newElement= newElements[i];
			if (! inputElements.contains(newElement))
				newInput.add(newElement);
			if (! treeElements.contains(newElement))
				addedElements.add(newElement);
		}
		if (treeElements.size() == 0)
			updateInputHistoryAndDescription(fInputElements, newElements);
		else if (newInput.size() > fInputElements.length)
			updateInputHistoryAndDescription(fInputElements, newInput.toArray(new IMember[newInput.size()]));
		if (addedElements.size() > 0)
			updateViewWithAddedElements(addedElements.toArray(new IMember[addedElements.size()]));
	}

	/**
	 * Updates the view with the newly added input elements.
	 * 
	 * @param newElements the newly added elements
	 * @since 3.7
	 */
	private void updateViewWithAddedElements(IMember[] newElements) {
		setCalleeRoots(null);
		setCallerRoots(null);
		MethodWrapper[] roots;
		if (getCallMode() == CALL_MODE_CALLERS)
			roots= CallHierarchy.getDefault().getCallerRoots(newElements);
		else
			roots= CallHierarchy.getDefault().getCalleeRoots(newElements);
		CallHierarchyViewer hierarchyViewer= getViewer();		
		TreeRoot treeRoot= hierarchyViewer.getTreeRoot(roots, true);
		hierarchyViewer.add(treeRoot, roots);
		for (int i= 0; i < roots.length; i++) {
			hierarchyViewer.setExpandedState(roots[i], true);
		}
		hierarchyViewer.setSelection(new StructuredSelection(roots), true);
	}
}

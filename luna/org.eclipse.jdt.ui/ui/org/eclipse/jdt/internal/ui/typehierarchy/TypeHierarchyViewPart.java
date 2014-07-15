/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
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
import java.util.Iterator;
import java.util.List;

import org.eclipse.help.IContextProvider;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ViewForm;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.ToolBar;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.util.DelegatingDropAdapter;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.IBasicPropertyConstants;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.OpenAndLinkWithEditorHelper;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.IShowInTargetList;
import org.eclipse.ui.part.PageBook;
import org.eclipse.ui.part.PluginTransfer;
import org.eclipse.ui.part.ResourceTransfer;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.views.navigator.LocalSelectionTransfer;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.ITypeHierarchyViewPart;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.actions.CCPActionGroup;
import org.eclipse.jdt.ui.actions.GenerateActionGroup;
import org.eclipse.jdt.ui.actions.JavaSearchActionGroup;
import org.eclipse.jdt.ui.actions.OpenAction;
import org.eclipse.jdt.ui.actions.OpenEditorActionGroup;
import org.eclipse.jdt.ui.actions.OpenViewActionGroup;
import org.eclipse.jdt.ui.actions.RefactorActionGroup;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.actions.NewWizardsActionGroup;
import org.eclipse.jdt.internal.ui.actions.SelectAllAction;
import org.eclipse.jdt.internal.ui.dnd.EditorInputTransferDragAdapter;
import org.eclipse.jdt.internal.ui.dnd.JdtViewerDragAdapter;
import org.eclipse.jdt.internal.ui.dnd.ResourceTransferDragAdapter;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.packageview.FileTransferDragAdapter;
import org.eclipse.jdt.internal.ui.packageview.PluginTransferDropAdapter;
import org.eclipse.jdt.internal.ui.packageview.SelectionTransferDragAdapter;
import org.eclipse.jdt.internal.ui.preferences.MembersOrderPreferenceCache;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.JavaUIHelp;
import org.eclipse.jdt.internal.ui.util.SelectionUtil;
import org.eclipse.jdt.internal.ui.viewsupport.IViewPartInputProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaUILabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.SelectionProviderMediator;
import org.eclipse.jdt.internal.ui.viewsupport.StatusBarUpdater;
import org.eclipse.jdt.internal.ui.workingsets.WorkingSetFilterActionGroup;


/**
 * View showing the super types/sub types of its input.
 */
public class TypeHierarchyViewPart extends ViewPart implements ITypeHierarchyViewPart, IViewPartInputProvider {

	private static final String DIALOGSTORE_HIERARCHYVIEW= "TypeHierarchyViewPart.hierarchyview";	 //$NON-NLS-1$
	private static final String DIALOGSTORE_VIEWLAYOUT= "TypeHierarchyViewPart.orientation";	 //$NON-NLS-1$
	private static final String DIALOGSTORE_QUALIFIED_NAMES= "TypeHierarchyViewPart.qualifiednames";	 //$NON-NLS-1$
	private static final String DIALOGSTORE_LINKEDITORS= "TypeHierarchyViewPart.linkeditors";	 //$NON-NLS-1$

	private static final String TAG_INPUT= "input"; //$NON-NLS-1$
	private static final String TAG_VIEW= "view"; //$NON-NLS-1$
	private static final String TAG_LAYOUT= "orientation"; //$NON-NLS-1$
	private static final String TAG_RATIO= "ratio"; //$NON-NLS-1$
	private static final String TAG_SELECTION= "selection"; //$NON-NLS-1$
	private static final String TAG_VERTICAL_SCROLL= "vertical_scroll"; //$NON-NLS-1$
	private static final String TAG_QUALIFIED_NAMES= "qualified_names"; //$NON-NLS-1$
	private static final String TAG_EDITOR_LINKING= "link_editors"; //$NON-NLS-1$

	private static final String GROUP_FOCUS= "group.focus"; //$NON-NLS-1$



	// the selected type in the hierarchy view
	private IType fSelectedType;
	// input elements or null
	private IJavaElement[] fInputElements;

	// history of input elements. No duplicates
	private ArrayList<IJavaElement[]> fInputHistory;

	private IMemento fMemento;
	private IDialogSettings fDialogSettings;

	private TypeHierarchyLifeCycle fHierarchyLifeCycle;
	private ITypeHierarchyLifeCycleListener fTypeHierarchyLifeCycleListener;

	private IPropertyChangeListener fPropertyChangeListener;

	private SelectionProviderMediator fSelectionProviderMediator;
	private ISelectionChangedListener fSelectionChangedListener;
	private IPartListener2 fPartListener;

	private int fCurrentLayout;
	private boolean fInComputeLayout;

	private boolean fLinkingEnabled;
	private boolean fShowQualifiedTypeNames;
	private boolean fSelectInEditor;

	private boolean fIsVisible;
	private boolean fNeedRefresh;
	private boolean fIsEnableMemberFilter;
	private boolean fIsRefreshRunnablePosted;

	private int fCurrentViewerIndex;
	private TypeHierarchyViewer[] fAllViewers;

	private MethodsViewer fMethodsViewer;

	private SashForm fTypeMethodsSplitter;
	private PageBook fViewerbook;
	private PageBook fPagebook;

	private Label fNoHierarchyShownLabel;
	private Label fEmptyTypesViewer;

	private ViewForm fTypeViewerViewForm;
	private ViewForm fMethodViewerViewForm;

	private CLabel fMethodViewerPaneLabel;
	private JavaUILabelProvider fPaneLabelProvider;
	private Composite fParent;

	private ToggleViewAction[] fViewActions;
	private ToggleLinkingAction fToggleLinkingAction;
	private HistoryDropDownAction fHistoryDropDownAction;
	private ToggleOrientationAction[] fToggleOrientationActions;
	private EnableMemberFilterAction fEnableMemberFilterAction;
	private ShowQualifiedTypeNamesAction fShowQualifiedTypeNamesAction;
	private FocusOnTypeAction fFocusOnTypeAction;
	private FocusOnSelectionAction fFocusOnSelectionAction;
	private CompositeActionGroup fActionGroups;
	private SelectAllAction fSelectAllAction;

	private WorkingSetFilterActionGroup fWorkingSetActionGroup;
	private Job fRestoreStateJob;

	/**
	 * Helper to open and activate editors.
	 *
	 * @since 3.5
	 */
	private OpenAndLinkWithEditorHelper fTypeOpenAndLinkWithEditorHelper;

	private OpenAction fOpenAction;

	/**
	 * Indicates whether the restore job was canceled explicitly.
	 * 
	 * @since 3.6
	 */
	private boolean fRestoreJobCanceledExplicitly= true;

	/**
	 * Indicates whether empty viewers should keep showing. If false, replace them with
	 * fEmptyTypesViewer.
	 * 
	 * @since 3.6
	 */
	private boolean fKeepShowingEmptyViewers= true;


	public TypeHierarchyViewPart() {
		fSelectedType= null;
		fInputElements= null;
		fIsVisible= false;
		fIsRefreshRunnablePosted= false;
		fSelectInEditor= true;
		fRestoreStateJob= null;

		fHierarchyLifeCycle= new TypeHierarchyLifeCycle(this);
		fTypeHierarchyLifeCycleListener= new ITypeHierarchyLifeCycleListener() {
			public void typeHierarchyChanged(TypeHierarchyLifeCycle typeHierarchy, IType[] changedTypes) {
				doTypeHierarchyChanged(typeHierarchy, changedTypes);
			}
		};
		fHierarchyLifeCycle.addChangedListener(fTypeHierarchyLifeCycleListener);

		fPropertyChangeListener= new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				doPropertyChange(event);
			}
		};
		PreferenceConstants.getPreferenceStore().addPropertyChangeListener(fPropertyChangeListener);

		fIsEnableMemberFilter= false;

		fInputHistory= new ArrayList<IJavaElement[]>();
		fAllViewers= null;

		fViewActions= new ToggleViewAction[] {
			new ToggleViewAction(this, HIERARCHY_MODE_CLASSIC),
			new ToggleViewAction(this, HIERARCHY_MODE_SUPERTYPES),
			new ToggleViewAction(this, HIERARCHY_MODE_SUBTYPES)
		};

		fDialogSettings= JavaPlugin.getDefault().getDialogSettings();

		fHistoryDropDownAction= new HistoryDropDownAction(this);
		fHistoryDropDownAction.setEnabled(false);

		fToggleOrientationActions= new ToggleOrientationAction[] {
			new ToggleOrientationAction(this, VIEW_LAYOUT_VERTICAL),
			new ToggleOrientationAction(this, VIEW_LAYOUT_HORIZONTAL),
			new ToggleOrientationAction(this, VIEW_LAYOUT_AUTOMATIC),
			new ToggleOrientationAction(this, VIEW_LAYOUT_SINGLE)
		};

		fEnableMemberFilterAction= new EnableMemberFilterAction(this, false);
		fShowQualifiedTypeNamesAction= new ShowQualifiedTypeNamesAction(this, false);

		fFocusOnTypeAction= new FocusOnTypeAction(this);

		fToggleLinkingAction= new ToggleLinkingAction(this);
		fToggleLinkingAction.setActionDefinitionId(IWorkbenchCommandConstants.NAVIGATE_TOGGLE_LINK_WITH_EDITOR);

		fPaneLabelProvider= new JavaUILabelProvider();

		fFocusOnSelectionAction= new FocusOnSelectionAction(this);

		fPartListener= new IPartListener2() {
			public void partVisible(IWorkbenchPartReference ref) {
				IWorkbenchPart part= ref.getPart(false);
				if (part == TypeHierarchyViewPart.this) {
					visibilityChanged(true);
				}
			}

			public void partHidden(IWorkbenchPartReference ref) {
				IWorkbenchPart part= ref.getPart(false);
				if (part == TypeHierarchyViewPart.this) {
					visibilityChanged(false);
				}
			}

			public void partActivated(IWorkbenchPartReference ref) {
				IWorkbenchPart part= ref.getPart(false);
				if (part instanceof IEditorPart)
					editorActivated((IEditorPart) part);
			}

		 	public void partInputChanged(IWorkbenchPartReference ref) {
				IWorkbenchPart part= ref.getPart(false);
				if (part instanceof IEditorPart)
					editorActivated((IEditorPart) part);
		 	}

			public void partBroughtToTop(IWorkbenchPartReference ref) {}
			public void partClosed(IWorkbenchPartReference ref) {}
			public void partDeactivated(IWorkbenchPartReference ref) {}
			public void partOpened(IWorkbenchPartReference ref) {}
		};

		fSelectionChangedListener= new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				doSelectionChanged(event);
			}
		};
	}

	protected void doPropertyChange(PropertyChangeEvent event) {
		String property= event.getProperty();
		if (fMethodsViewer != null) {
			if (MembersOrderPreferenceCache.isMemberOrderProperty(event.getProperty())) {
				fMethodsViewer.refresh();
			}
		}
		if (IWorkingSetManager.CHANGE_WORKING_SET_CONTENT_CHANGE.equals(property)) {
			updateHierarchyViewer(true);
			updateToolTipAndDescription();
		}
	}

	/**
	 * Adds the entry if new. Inserted at the beginning of the history entries list.
	 * @param entry The new entry
	 */
	private void addHistoryEntry(IJavaElement[] entry) {
		for (Iterator<IJavaElement[]> iter= fInputHistory.iterator(); iter.hasNext();) {
			IJavaElement[] elem= iter.next();
			if (Arrays.equals(elem, entry)) {
				fInputHistory.remove(elem);
				break;
			}
		}
		fInputHistory.add(0, entry);
		fHistoryDropDownAction.setEnabled(true);
	}

	private void updateHistoryEntries() {
		for (int i= fInputHistory.size() - 1; i >= 0; i--) {
			IJavaElement[] entries= fInputHistory.get(i);
			for (int j= 0; j < entries.length; j++) {
				IJavaElement elem= entries[j];
				if (elem != null && !elem.exists()) {
					fInputHistory.remove(i);
					break;
				}
			}
		}
		fHistoryDropDownAction.setEnabled(!fInputHistory.isEmpty());
	}

	/**
	 * Goes to the selected entry, without updating the order of history entries.
	 * 
	 * @param entry the entry to open
	 */
	public void gotoHistoryEntry(IJavaElement[] entry) {
		if (fInputHistory.contains(entry)) {
			updateInput(entry);
		}
	}

	/**
	 * Gets all history entries.
	 * @return All history entries
	 */
	public List<IJavaElement[]> getHistoryEntries() {
		if (fInputHistory.size() > 0) {
			updateHistoryEntries();
		}
		return fInputHistory;
	}

	/**
	 * Sets the history entries.
	 * 
	 * @param elems the history elements to set
	 */
	public void setHistoryEntries(IJavaElement[] elems) {
		List<IJavaElement[]> list= new ArrayList<IJavaElement[]>(1);
		list.add(elems);
		setHistoryEntries(list);
	}

	/**
	 * Sets the history entries.
	 * 
	 * @param elems the history elements to set
	 * @since 3.7
	 */
	public void setHistoryEntries(List<IJavaElement[]> elems) {
		fInputHistory.clear();
		for (Iterator<IJavaElement[]> iterator= elems.iterator(); iterator.hasNext();) {
			IJavaElement[] elements= iterator.next();
			if (elements != null && elements.length != 0)
				fInputHistory.add(elements);
		}
		updateHistoryEntries();
	}

	/**
	 * Selects an member in the methods list or in the current hierarchy.
	 * @param member The member to select
	 */
	public void selectMember(IMember member) {
		fSelectInEditor= false;
		if (member.getElementType() != IJavaElement.TYPE) {
			Control methodControl= fMethodsViewer.getControl();
			if (methodControl != null && !methodControl.isDisposed()) {
				methodControl.setFocus();
			}

			fMethodsViewer.setSelection(new StructuredSelection(member), true);
		} else {
			Control viewerControl= getCurrentViewer().getControl();
			if (viewerControl != null && !viewerControl.isDisposed()) {
				viewerControl.setFocus();
			}

			if (!member.equals(fSelectedType)) {
				getCurrentViewer().setSelection(new StructuredSelection(member), true);
			}
		}
		fSelectInEditor= true;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @deprecated use getInputElement instead
	 */
	public IType getInput() {
		if (fInputElements.length == 1 && fInputElements[0] instanceof IType) {
			return (IType)fInputElements[0];
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @deprecated use setInputElement instead
	 */
	public void setInput(IType type) {
		setInputElement(type);
	}

	/**
	 * Returns the input element of the type hierarchy. Can be of type <code>IType</code> or
	 * <code>IPackageFragment</code>
	 * 
	 * @return the input element
	 */
	public IJavaElement getInputElement() {
		return fInputElements == null ? null : (fInputElements.length == 1 ? fInputElements[0] : null);
	}

	/**
	 * Returns the input elements of the type hierarchy.
	 * 
	 * @return the input elements
	 * @since 3.7
	 */
	public IJavaElement[] getInputElements() {
		return fInputElements;
	}
	

	/**
	 * Sets the input to a new element.
	 * @param element the input element
	 */
	public void setInputElement(IJavaElement element) {
		setInputElements(element == null ? null : new IJavaElement[] { element });
	}


	/**
	 * Sets the input to a new element.
	 * 
	 * @param javaElements the input java elements
	 * @since 3.7
	 */
	public void setInputElements(IJavaElement[] javaElements) {
		IJavaElement[] newElements= null;
		IMember memberToSelect= null;
		if (javaElements != null) {
			newElements= new IJavaElement[javaElements.length];
			for (int i= 0; i < javaElements.length; i++) {
				newElements[i]= javaElements[i];
				memberToSelect= null;
				if (newElements[i] != null) {
					if (newElements[i] instanceof IMember) {
						if (newElements[i].getElementType() != IJavaElement.TYPE) {
							memberToSelect= (IMember)newElements[i];
							newElements[i]= memberToSelect.getDeclaringType();
						}
						if (!newElements[i].exists()) {
							MessageDialog.openError(getSite().getShell(), TypeHierarchyMessages.TypeHierarchyViewPart_error_title, TypeHierarchyMessages.TypeHierarchyViewPart_error_message);
							return;
						}
					} else {
						int kind= newElements[i].getElementType();
						if (kind != IJavaElement.JAVA_PROJECT && kind != IJavaElement.PACKAGE_FRAGMENT_ROOT && kind != IJavaElement.PACKAGE_FRAGMENT) {
							newElements= null;
							JavaPlugin.logErrorMessage("Invalid type hierarchy input type.");//$NON-NLS-1$
							return;
						}
					}
				}
			}
			if (!javaElements.equals(fInputElements)) {
				addHistoryEntry(javaElements);
			}
		}
		updateInput(newElements);
		if (memberToSelect != null) {
			selectMember(memberToSelect);
		}
	}

	/*
	 * Changes the input to a new type
	 * @param inputElement
	 */
	private void updateInput(IJavaElement[] inputElements) {
		IJavaElement[] prevInput= fInputElements;

		synchronized (this) {
			if (fRestoreStateJob != null) {
				fRestoreStateJob.cancel();
				fRestoreJobCanceledExplicitly= false;
				try {
					fRestoreStateJob.join();
				} catch (InterruptedException e) {
					// ignore
				} finally {
					fRestoreStateJob= null;
				}
			}
		}

		// Make sure the UI got repainted before we execute a long running
		// operation. This can be removed if we refresh the hierarchy in a
		// separate thread.
		// Work-around for http://dev.eclipse.org/bugs/show_bug.cgi?id=30881
		processOutstandingEvents();
		if (inputElements == null) {
			clearInput();
		} else {
			if (!inputElements.equals(prevInput)) {
				for (int i= 0; i < fAllViewers.length; i++) {
					fAllViewers[i].setInput(null);
				}
			}
			fInputElements= inputElements;
			fNoHierarchyShownLabel
					.setText(Messages.format(TypeHierarchyMessages.TypeHierarchyViewPart_createinput, HistoryAction.getElementLabel(fInputElements)));
			try {
				fHierarchyLifeCycle.ensureRefreshedTypeHierarchy(inputElements, JavaPlugin.getActiveWorkbenchWindow());
				// fHierarchyLifeCycle.ensureRefreshedTypeHierarchy(inputElement, getSite().getWorkbenchWindow());
			} catch (InvocationTargetException e) {
				ExceptionHandler.handle(e, getSite().getShell(), TypeHierarchyMessages.TypeHierarchyViewPart_exception_title, TypeHierarchyMessages.TypeHierarchyViewPart_exception_message);
				clearInput();
				return;// panic code. This code wont be executed.
			} catch (InterruptedException e) {
				fNoHierarchyShownLabel.setText(TypeHierarchyMessages.TypeHierarchyViewPart_empty);
				return;// panic code. This code wont be executed.
			}

			if (inputElements.length == 1 && inputElements[0].getElementType() != IJavaElement.TYPE) {
				setHierarchyMode(HIERARCHY_MODE_CLASSIC);
			}
			updateViewers();
		}
	}

	/**
	 * Updates the viewers, toolbar buttons and tooltip.
	 * 
	 * @since 3.6
	 */
	public void updateViewers() {
		if (fInputElements == null)
			return;
		if (!fHierarchyLifeCycle.isRefreshJobRunning()) {
			setViewersInput();
		}
		setViewerVisibility(true);
		// turn off member filtering
		fSelectInEditor= false;
		setMemberFilter(null);
		internalSelectType(null, false); // clear selection
		fIsEnableMemberFilter= false;
		updateHierarchyViewer(true);
		IType root= getSelectableType(fInputElements);
		internalSelectType(root, true);
		updateMethodViewer(root);
		updateToolbarButtons();
		updateToolTipAndDescription();
		showMembersInHierarchy(false);
		fPagebook.showPage(fTypeMethodsSplitter);		
		fSelectInEditor= true;		
	}

	private void processOutstandingEvents() {
		Display display= getDisplay();
		if (display != null && !display.isDisposed())
			display.update();
	}

	private void clearInput() {
		fInputElements= null;
		fHierarchyLifeCycle.freeHierarchy();

		updateHierarchyViewer(false);
		updateToolbarButtons();
		updateToolTipAndDescription();
		getViewSite().getActionBars().getStatusLineManager().setMessage(""); //$NON-NLS-1$		
	}

	/*
	 * @see IWorbenchPart#setFocus
	 */
	@Override
	public void setFocus() {
		fPagebook.setFocus();
	}

	/*
	 * @see IWorkbenchPart#dispose
	 */
	@Override
	public void dispose() {
		if (fHierarchyLifeCycle != null) {
			fHierarchyLifeCycle.freeHierarchy();
			fHierarchyLifeCycle.removeChangedListener(fTypeHierarchyLifeCycleListener);
			fHierarchyLifeCycle= null;
		}
		fPaneLabelProvider.dispose();

		if (fMethodsViewer != null) {
			fMethodsViewer.dispose();
		}

		if (fPropertyChangeListener != null) {
			JavaPlugin.getDefault().getPreferenceStore().removePropertyChangeListener(fPropertyChangeListener);
			fPropertyChangeListener= null;
		}

		getSite().getPage().removePartListener(fPartListener);

		if (fActionGroups != null)
			fActionGroups.dispose();

		if (fWorkingSetActionGroup != null)
			fWorkingSetActionGroup.dispose();

		super.dispose();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
	 */
	@Override
	public Object getAdapter(Class key) {
		if (key == IShowInSource.class) {
			return getShowInSource();
		}
		if (key == IShowInTargetList.class) {
			return new IShowInTargetList() {
				public String[] getShowInTargetIds() {
					return new String[] { JavaUI.ID_PACKAGES, JavaPlugin.ID_RES_NAV  };
				}

			};
		}
		if (key == IContextProvider.class) {
			return JavaUIHelp.getHelpContextProvider(this, IJavaHelpContextIds.TYPE_HIERARCHY_VIEW);
		}
		return super.getAdapter(key);
	}

	private Control createTypeViewerControl(Composite parent) {
		fViewerbook= new PageBook(parent, SWT.NULL);

		KeyListener keyListener= createKeyListener();

		// Create the viewers
		TypeHierarchyViewer superTypesViewer= new SuperTypeHierarchyViewer(fViewerbook, fHierarchyLifeCycle);
		initializeTypesViewer(superTypesViewer, keyListener, IContextMenuConstants.TARGET_ID_SUPERTYPES_VIEW);

		TypeHierarchyViewer subTypesViewer= new SubTypeHierarchyViewer(fViewerbook, fHierarchyLifeCycle);
		initializeTypesViewer(subTypesViewer, keyListener, IContextMenuConstants.TARGET_ID_SUBTYPES_VIEW);

		TypeHierarchyViewer vajViewer= new TraditionalHierarchyViewer(fViewerbook, fHierarchyLifeCycle);
		initializeTypesViewer(vajViewer, keyListener, IContextMenuConstants.TARGET_ID_HIERARCHY_VIEW);

		fAllViewers= new TypeHierarchyViewer[3];
		fAllViewers[HIERARCHY_MODE_SUPERTYPES]= superTypesViewer;
		fAllViewers[HIERARCHY_MODE_SUBTYPES]= subTypesViewer;
		fAllViewers[HIERARCHY_MODE_CLASSIC]= vajViewer;

		int currViewerIndex;
		try {
			currViewerIndex= fDialogSettings.getInt(DIALOGSTORE_HIERARCHYVIEW);
			if (currViewerIndex < 0 || currViewerIndex > 2) {
				currViewerIndex= HIERARCHY_MODE_CLASSIC;
			}
		} catch (NumberFormatException e) {
			currViewerIndex= HIERARCHY_MODE_CLASSIC;
		}

		fEmptyTypesViewer= new Label(fViewerbook, SWT.TOP | SWT.LEFT | SWT.WRAP);

		for (int i= 0; i < fAllViewers.length; i++) {
			fAllViewers[i].setInput(fAllViewers[i]);
		}

		// force the update
		fCurrentViewerIndex= -1;
		setHierarchyMode(currViewerIndex);

		return fViewerbook;
	}

	private KeyListener createKeyListener() {
		return new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent event) {
				if (event.stateMask == 0) {
					if (event.keyCode == SWT.F5) {
						ITypeHierarchy hierarchy= fHierarchyLifeCycle.getHierarchy();
						if (hierarchy != null) {
							fHierarchyLifeCycle.typeHierarchyChanged(hierarchy);
							doTypeHierarchyChangedOnViewers(null);
						}
						updateHierarchyViewer(false);
						return;
					}
 				}
			}
		};
	}


	private void initializeTypesViewer(final TypeHierarchyViewer typesViewer, KeyListener keyListener, String cotextHelpId) {
		typesViewer.getControl().setVisible(false);
		typesViewer.getControl().addKeyListener(keyListener);
		typesViewer.initContextMenu(new IMenuListener() {
			public void menuAboutToShow(IMenuManager menu) {
				fillTypesViewerContextMenu(typesViewer, menu);
			}
		}, cotextHelpId,	getSite());
		typesViewer.addPostSelectionChangedListener(fSelectionChangedListener);
		typesViewer.setQualifiedTypeName(isQualifiedTypeNamesEnabled());
		typesViewer.setWorkingSetFilter(fWorkingSetActionGroup.getWorkingSetFilter());
	}

	private Control createMethodViewerControl(Composite parent) {
		fMethodsViewer= new MethodsViewer(parent, fHierarchyLifeCycle);
		fMethodsViewer.initContextMenu(new IMenuListener() {
			public void menuAboutToShow(IMenuManager menu) {
				fillMethodsViewerContextMenu(menu);
			}
		}, IContextMenuConstants.TARGET_ID_MEMBERS_VIEW, getSite());
		fMethodsViewer.addPostSelectionChangedListener(fSelectionChangedListener);

		new OpenAndLinkWithEditorHelper(fMethodsViewer) {
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
				// do nothing: this is handled in more detail by the part itself
			}

			@Override
			protected void open(ISelection selection, boolean activate) {
				if (selection instanceof IStructuredSelection)
					fOpenAction.run((IStructuredSelection)selection);
			}
		};

		Control control= fMethodsViewer.getTable();
		control.addKeyListener(createKeyListener());
		control.addFocusListener(new FocusListener() {
			public void focusGained(FocusEvent e) {
				fSelectAllAction.setEnabled(true);
			}

			public void focusLost(FocusEvent e) {
				fSelectAllAction.setEnabled(false);
			}
		});

		return control;
	}

	private void initDragAndDrop() {
		for (int i= 0; i < fAllViewers.length; i++) {
			addDragAdapters(fAllViewers[i]);
			addDropAdapters(fAllViewers[i]);
		}
		addDragAdapters(fMethodsViewer);
		fMethodsViewer.addDropSupport(DND.DROP_NONE, new Transfer[0], new DropTargetAdapter());

		//DND on empty hierarchy
		DropTarget dropTarget = new DropTarget(fPagebook, DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK | DND.DROP_DEFAULT);
		dropTarget.setTransfer(new Transfer[] { LocalSelectionTransfer.getInstance() });
		dropTarget.addDropListener(new TypeHierarchyTransferDropAdapter(this, fAllViewers[0]));
	}

	private void addDropAdapters(AbstractTreeViewer viewer) {
		Transfer[] transfers= new Transfer[] { LocalSelectionTransfer.getInstance(), PluginTransfer.getInstance() };
		int ops= DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK | DND.DROP_DEFAULT;

		DelegatingDropAdapter delegatingDropAdapter= new DelegatingDropAdapter();
		delegatingDropAdapter.addDropTargetListener(new TypeHierarchyTransferDropAdapter(this, viewer));
		delegatingDropAdapter.addDropTargetListener(new PluginTransferDropAdapter(viewer));

		viewer.addDropSupport(ops, transfers, delegatingDropAdapter);
	}

	private void addDragAdapters(StructuredViewer viewer) {
		int ops= DND.DROP_COPY | DND.DROP_LINK;
		Transfer[] transfers= new Transfer[] { LocalSelectionTransfer.getInstance(), ResourceTransfer.getInstance(), FileTransfer.getInstance()};

		JdtViewerDragAdapter dragAdapter= new JdtViewerDragAdapter(viewer);
		dragAdapter.addDragSourceListener(new SelectionTransferDragAdapter(viewer));
		dragAdapter.addDragSourceListener(new EditorInputTransferDragAdapter(viewer));
		dragAdapter.addDragSourceListener(new ResourceTransferDragAdapter(viewer));
		dragAdapter.addDragSourceListener(new FileTransferDragAdapter(viewer));

		viewer.addDragSupport(ops, transfers, dragAdapter);
	}

	/**
	 * Returns the inner component in a workbench part.
	 * @see IWorkbenchPart#createPartControl(Composite)
	 */
	@Override
	public void createPartControl(Composite container) {
		fParent= container;
    	addResizeListener(container);

		fPagebook= new PageBook(container, SWT.NONE);
		fWorkingSetActionGroup= new WorkingSetFilterActionGroup(getSite(), fPropertyChangeListener);

		// page 1 of page book (no hierarchy label)

		fNoHierarchyShownLabel= new Label(fPagebook, SWT.TOP + SWT.LEFT + SWT.WRAP);
		fNoHierarchyShownLabel.setText(TypeHierarchyMessages.TypeHierarchyViewPart_empty);

		// page 2 of page book (viewers)

		fTypeMethodsSplitter= new SashForm(fPagebook, SWT.VERTICAL);
		fTypeMethodsSplitter.setVisible(false);

		fTypeViewerViewForm= new ViewForm(fTypeMethodsSplitter, SWT.NONE);

		Control typeViewerControl= createTypeViewerControl(fTypeViewerViewForm);
		fTypeViewerViewForm.setContent(typeViewerControl);

		fMethodViewerViewForm= new ViewForm(fTypeMethodsSplitter, SWT.NONE);
		fTypeMethodsSplitter.setWeights(new int[] {35, 65});

		Control methodViewerPart= createMethodViewerControl(fMethodViewerViewForm);
		fMethodViewerViewForm.setContent(methodViewerPart);

		fMethodViewerPaneLabel= new CLabel(fMethodViewerViewForm, SWT.NONE);
		fMethodViewerViewForm.setTopLeft(fMethodViewerPaneLabel);

		ToolBar methodViewerToolBar= new ToolBar(fMethodViewerViewForm, SWT.FLAT | SWT.WRAP);
		fMethodViewerViewForm.setTopCenter(methodViewerToolBar);

		initDragAndDrop();

		MenuManager menu= new MenuManager();
		menu.add(fFocusOnTypeAction);
		fNoHierarchyShownLabel.setMenu(menu.createContextMenu(fNoHierarchyShownLabel));

		fPagebook.showPage(fNoHierarchyShownLabel);

		int layout;
		try {
			layout= fDialogSettings.getInt(DIALOGSTORE_VIEWLAYOUT);
			if (layout < 0 || layout > 3) {
				layout= VIEW_LAYOUT_AUTOMATIC;
			}
		} catch (NumberFormatException e) {
			layout= VIEW_LAYOUT_AUTOMATIC;
		}
		// force the update
		fCurrentLayout= -1;
		// will fill the main tool bar
		setViewLayout(layout);

		showQualifiedTypeNames(fDialogSettings.getBoolean(DIALOGSTORE_QUALIFIED_NAMES));
		setLinkingEnabled(fDialogSettings.getBoolean(DIALOGSTORE_LINKEDITORS));

		// set the filter menu items
		IActionBars actionBars= getViewSite().getActionBars();
		IMenuManager viewMenu= actionBars.getMenuManager();
		for (int i= 0; i < fViewActions.length; i++) {
			ToggleViewAction action= fViewActions[i];
			viewMenu.add(action);
			action.setEnabled(false);
		}
		viewMenu.add(new Separator());

		fWorkingSetActionGroup.fillViewMenu(viewMenu);

		viewMenu.add(new Separator());

		IMenuManager layoutSubMenu= new MenuManager(TypeHierarchyMessages.TypeHierarchyViewPart_layout_submenu);
		viewMenu.add(layoutSubMenu);
		for (int i= 0; i < fToggleOrientationActions.length; i++) {
			layoutSubMenu.add(fToggleOrientationActions[i]);
		}
		viewMenu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		viewMenu.add(fShowQualifiedTypeNamesAction);
		viewMenu.add(fToggleLinkingAction);


		// fill the method viewer tool bar
		ToolBarManager lowertbmanager= new ToolBarManager(methodViewerToolBar);
		lowertbmanager.add(fEnableMemberFilterAction);
		lowertbmanager.add(new Separator());
		fMethodsViewer.contributeToToolBar(lowertbmanager);
		lowertbmanager.update(true);

		// selection provider
		int nHierarchyViewers= fAllViewers.length;
		StructuredViewer[] trackedViewers= new StructuredViewer[nHierarchyViewers + 1];
		for (int i= 0; i < nHierarchyViewers; i++) {
			trackedViewers[i]= fAllViewers[i];
		}
		trackedViewers[nHierarchyViewers]= fMethodsViewer;
		fSelectionProviderMediator= new SelectionProviderMediator(trackedViewers, getCurrentViewer());
		IStatusLineManager slManager= getViewSite().getActionBars().getStatusLineManager();
		fSelectionProviderMediator.addSelectionChangedListener(new StatusBarUpdater(slManager));

		getSite().setSelectionProvider(fSelectionProviderMediator);
		getSite().getPage().addPartListener(fPartListener);

		if (fMemento != null)
			restoreState(fMemento);
		else
			setViewerVisibility(false);

		PlatformUI.getWorkbench().getHelpSystem().setHelp(fPagebook, IJavaHelpContextIds.TYPE_HIERARCHY_VIEW);


		fActionGroups= new CompositeActionGroup(new ActionGroup[] {
				new NewWizardsActionGroup(this.getSite()),
				new OpenEditorActionGroup(this),
				new OpenViewActionGroup(this),
				new CCPActionGroup(this),
				new GenerateActionGroup(this),
				new RefactorActionGroup(this),
				new JavaSearchActionGroup(this)
		});

		fActionGroups.fillActionBars(actionBars);
		fSelectAllAction= new SelectAllAction(fMethodsViewer);
		fOpenAction= new OpenAction(getSite());

		actionBars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(), fSelectAllAction);

		IHandlerService handlerService= (IHandlerService) getViewSite().getService(IHandlerService.class);
		handlerService.activateHandler(IWorkbenchCommandConstants.NAVIGATE_TOGGLE_LINK_WITH_EDITOR, new ActionHandler(fToggleLinkingAction));
	}

	private void addResizeListener(Composite parent) {
		parent.addControlListener(new ControlListener() {
			public void controlMoved(ControlEvent e) {
			}
			public void controlResized(ControlEvent e) {
				int viewLayout= getViewLayout();
				if ((viewLayout == VIEW_LAYOUT_AUTOMATIC || viewLayout == -1) && !fInComputeLayout) {
					setViewLayout(VIEW_LAYOUT_AUTOMATIC);
				}
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.ITypeHierarchyViewPart#setViewLayout(int)
	 */
	public void setViewLayout(int layout) {
		if (fCurrentLayout != layout || layout == VIEW_LAYOUT_AUTOMATIC) {
			fInComputeLayout= true;
			try {
				boolean methodViewerNeedsUpdate= false;

				if (fMethodViewerViewForm != null && !fMethodViewerViewForm.isDisposed()
						&& fTypeMethodsSplitter != null && !fTypeMethodsSplitter.isDisposed()) {

					boolean horizontal= false;
					if (layout == VIEW_LAYOUT_SINGLE) {
						fMethodViewerViewForm.setVisible(false);
						showMembersInHierarchy(false);
						updateMethodViewer(null);
					} else {
						if (fCurrentLayout == VIEW_LAYOUT_SINGLE) {
							fMethodViewerViewForm.setVisible(true);
							methodViewerNeedsUpdate= true;
						}
						if (layout == VIEW_LAYOUT_AUTOMATIC) {
							if (fParent != null && !fParent.isDisposed()) {
								Point size= fParent.getSize();
								if (size.x != 0 && size.y != 0) {
									// bug 185397 - Hierarchy View flips orientation multiple times on resize
									Control viewFormToolbar= fTypeViewerViewForm.getTopLeft();
									if (viewFormToolbar != null && !viewFormToolbar.isDisposed() && viewFormToolbar.isVisible()) {
										size.y -= viewFormToolbar.getSize().y;
									}
									horizontal= size.x > size.y;
								} else if (size.x == 0 && size.y == 0) {
									return;
								}
							}
							if (fCurrentLayout == VIEW_LAYOUT_AUTOMATIC) {
								boolean wasHorizontal= fTypeMethodsSplitter.getOrientation() == SWT.HORIZONTAL;
								if (wasHorizontal == horizontal) {
									return; // no real change
								}
							}

						} else if (layout == VIEW_LAYOUT_HORIZONTAL) {
							horizontal= true;
						}
						fTypeMethodsSplitter.setOrientation(horizontal ? SWT.HORIZONTAL : SWT.VERTICAL);
					}
					updateMainToolbar(horizontal);
					fTypeMethodsSplitter.layout();
				}
				if (methodViewerNeedsUpdate) {
					updateMethodViewer(fSelectedType);
				}
				fDialogSettings.put(DIALOGSTORE_VIEWLAYOUT, layout);
				fCurrentLayout= layout;

				updateCheckedState();
			} finally {
				fInComputeLayout= false;
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.ITypeHierarchyViewPart#getViewLayout()
	 */
	public int getViewLayout() {
		return fCurrentLayout;
	}

	private void updateCheckedState() {
		for (int i= 0; i < fToggleOrientationActions.length; i++) {
			fToggleOrientationActions[i].setChecked(getViewLayout() == fToggleOrientationActions[i].getOrientation());
		}
	}

	private void updateMainToolbar(boolean horizontal) {
		IActionBars actionBars= getViewSite().getActionBars();
		IToolBarManager tbmanager= actionBars.getToolBarManager();

		if (horizontal) {
			clearMainToolBar(tbmanager);
			ToolBar typeViewerToolBar= new ToolBar(fTypeViewerViewForm, SWT.FLAT | SWT.WRAP);
			fillMainToolBar(new ToolBarManager(typeViewerToolBar));
			fTypeViewerViewForm.setTopLeft(typeViewerToolBar);
		} else {
			fTypeViewerViewForm.setTopLeft(null);
			fillMainToolBar(tbmanager);
		}
	}

	private void fillMainToolBar(IToolBarManager tbmanager) {
		tbmanager.removeAll();
		for (int i= 0; i < fViewActions.length; i++) {
			tbmanager.add(fViewActions[i]);
		}
		tbmanager.add(fHistoryDropDownAction);
		tbmanager.update(false);
	}

	private void clearMainToolBar(IToolBarManager tbmanager) {
		tbmanager.removeAll();
		tbmanager.update(false);
	}


	/*
	 * Creates the context menu for the hierarchy viewers
	 */
	private void fillTypesViewerContextMenu(TypeHierarchyViewer viewer, IMenuManager menu) {
		JavaPlugin.createStandardGroups(menu);

		menu.appendToGroup(IContextMenuConstants.GROUP_SHOW, new Separator(GROUP_FOCUS));
		// viewer entries
		viewer.contributeToContextMenu(menu);

		if (fFocusOnSelectionAction.canActionBeAdded())
			menu.appendToGroup(GROUP_FOCUS, fFocusOnSelectionAction);
		menu.appendToGroup(GROUP_FOCUS, fFocusOnTypeAction);

		fActionGroups.setContext(new ActionContext(getSite().getSelectionProvider().getSelection()));
		fActionGroups.fillContextMenu(menu);
		fActionGroups.setContext(null);
	}

	/*
	 * Creates the context menu for the method viewer
	 */
	private void fillMethodsViewerContextMenu(IMenuManager menu) {
		JavaPlugin.createStandardGroups(menu);
		// viewer entries
		fMethodsViewer.contributeToContextMenu(menu);
		fActionGroups.setContext(new ActionContext(getSite().getSelectionProvider().getSelection()));
		fActionGroups.fillContextMenu(menu);
		fActionGroups.setContext(null);
	}

	/*
	 * Toggles between the empty viewer page and the hierarchy
	 */
	private void setViewerVisibility(boolean showHierarchy) {
		if (showHierarchy) {
			fViewerbook.showPage(getCurrentViewer().getControl());
		} else {
			fViewerbook.showPage(fEmptyTypesViewer);
		}
	}

	/*
	 * Sets the member filter. <code>null</code> disables member filtering.
	 */
	private void setMemberFilter(IMember[] memberFilter) {
		Assert.isNotNull(fAllViewers);
		for (int i= 0; i < fAllViewers.length; i++) {
			fAllViewers[i].setMemberFilter(memberFilter);
		}
	}

	private IType getSelectableType(IJavaElement[] elem) {
		if (elem[0].getElementType() != IJavaElement.TYPE) {
			return getCurrentViewer().getTreeRootType();
		} else {
			return (IType)elem[0];
		}
	}

	private void internalSelectType(IMember elem, boolean reveal) {
		TypeHierarchyViewer viewer= getCurrentViewer();
		viewer.removePostSelectionChangedListener(fSelectionChangedListener);
		viewer.setSelection(elem != null ? new StructuredSelection(elem) : StructuredSelection.EMPTY, reveal);
		viewer.addPostSelectionChangedListener(fSelectionChangedListener);
	}

	/*
	 * When the input changed or the hierarchy pane becomes visible,
	 * <code>updateHierarchyViewer<code> brings up the correct view and refreshes
	 * the current tree
	 */
	private void updateHierarchyViewer(final boolean doExpand) {
		if (fInputElements == null) {
			fNoHierarchyShownLabel.setText(TypeHierarchyMessages.TypeHierarchyViewPart_empty);
			fPagebook.showPage(fNoHierarchyShownLabel);
		} else {
			if (getCurrentViewer().containsElements() != null) {
				Runnable runnable= new Runnable() {
					public void run() {
						getCurrentViewer().updateContent(doExpand); // refresh
					}
				};
				BusyIndicator.showWhile(getDisplay(), runnable);
				if (!isChildVisible(fViewerbook, getCurrentViewer().getControl())) {
					setViewerVisibility(true);
				}
			} else if (!isKeepShowingEmptyViewers()) {//Show the empty hierarchy viewer till fresh computation is done.
				fEmptyTypesViewer.setText(Messages.format(TypeHierarchyMessages.TypeHierarchyViewPart_nodecl, HistoryAction.getElementLabel(fInputElements)));
				setViewerVisibility(false);
			}
		}
	}

	private void updateMethodViewer(final IType input) {
		if (!fIsEnableMemberFilter && fCurrentLayout != VIEW_LAYOUT_SINGLE) {
			if (input == fMethodsViewer.getInput()) {
				if (input != null) {
					Runnable runnable= new Runnable() {
						public void run() {
							fMethodsViewer.refresh(); // refresh
						}
					};
					BusyIndicator.showWhile(getDisplay(), runnable);
				}
			} else {
				if (input != null) {
					fMethodViewerPaneLabel.setText(fPaneLabelProvider.getText(input));
					fMethodViewerPaneLabel.setImage(fPaneLabelProvider.getImage(input));
				} else {
					fMethodViewerPaneLabel.setText(""); //$NON-NLS-1$
					fMethodViewerPaneLabel.setImage(null);
				}
				Runnable runnable= new Runnable() {
					public void run() {
						fMethodsViewer.setInput(input); // refresh
					}
				};
				BusyIndicator.showWhile(getDisplay(), runnable);
			}
		}
	}

	protected void doSelectionChanged(SelectionChangedEvent e) {
		if (e.getSelectionProvider() == fMethodsViewer) {
			methodSelectionChanged(e.getSelection());
		} else {
			typeSelectionChanged(e.getSelection());
		}
	}



	private void methodSelectionChanged(ISelection sel) {
		if (sel instanceof IStructuredSelection) {
			List<?> selected= ((IStructuredSelection)sel).toList();
			int nSelected= selected.size();
			if (fIsEnableMemberFilter) {
				IMember[] memberFilter= null;
				if (nSelected > 0) {
					memberFilter= new IMember[nSelected];
					selected.toArray(memberFilter);
				}
				setMemberFilter(memberFilter);
				updateHierarchyViewer(true);
				updateToolTipAndDescription();
				internalSelectType(fSelectedType, true);
			}
			if (nSelected == 1 && fSelectInEditor) {
				revealElementInEditor(selected.get(0), fMethodsViewer);
			}
		}
	}

	private void typeSelectionChanged(ISelection sel) {
		if (sel instanceof IStructuredSelection) {
			List<?> selected= ((IStructuredSelection)sel).toList();
			int nSelected= selected.size();
			if (nSelected != 0) {
				List<Object> types= new ArrayList<Object>(nSelected);
				for (int i= nSelected-1; i >= 0; i--) {
					Object elem= selected.get(i);
					if (elem instanceof IType && !types.contains(elem)) {
						types.add(elem);
					}
				}
				if (types.size() == 1) {
					fSelectedType= (IType) types.get(0);
					updateMethodViewer(fSelectedType);
				} else if (types.size() == 0) {
					// method selected, no change
				}
				if (nSelected == 1 && fSelectInEditor) {
					revealElementInEditor(selected.get(0), getCurrentViewer());
				}
			} else {
				fSelectedType= null;
				updateMethodViewer(null);
			}
		}
	}

	private void revealElementInEditor(Object elem, StructuredViewer originViewer) {
		// only allow revealing when the type hierarchy is the active page
		// no revealing after selection events due to model changes

		if (getSite().getPage().getActivePart() != this) {
			return;
		}

		if (fSelectionProviderMediator.getViewerInFocus() != originViewer) {
			return;
		}

		IEditorPart editorPart= EditorUtility.isOpenInEditor(elem);
		if (editorPart != null && (elem instanceof IJavaElement)) {
			getSite().getPage().removePartListener(fPartListener);
			getSite().getPage().bringToTop(editorPart);
			EditorUtility.revealInEditor(editorPart, (IJavaElement) elem);
			getSite().getPage().addPartListener(fPartListener);
		}
	}

	private Display getDisplay() {
		if (fPagebook != null && !fPagebook.isDisposed()) {
			return fPagebook.getDisplay();
		}
		return null;
	}

	private boolean isChildVisible(Composite pb, Control child) {
		Control[] children= pb.getChildren();
		for (int i= 0; i < children.length; i++) {
			if (children[i] == child && children[i].isVisible())
				return true;
		}
		return false;
	}

	private void updateToolTipAndDescription() {
		String tooltip= ""; //$NON-NLS-1$
		String description;
		if (fInputElements != null && fInputElements.length != 0) {
			IWorkingSet workingSet= fWorkingSetActionGroup.getWorkingSet();
			String[] elementLabel= null;
			if (workingSet == null) {
				description= HistoryAction.getElementLabel(fInputElements);
				switch (fInputElements.length) {
					case 1:
						elementLabel= new String[] { HistoryAction.getShortLabel(fInputElements[0]) };
						tooltip= Messages.format(TypeHierarchyMessages.TypeHierarchyViewPart_tooltip, elementLabel);
						break;
					case 2:
						elementLabel= new String[] { HistoryAction.getShortLabel(fInputElements[0]), HistoryAction.getShortLabel(fInputElements[1]) };
						tooltip= Messages.format(TypeHierarchyMessages.TypeHierarchyViewPart_tooltip2, elementLabel);
						break;
					default:
						elementLabel= new String[] { HistoryAction.getShortLabel(fInputElements[0]), HistoryAction.getShortLabel(fInputElements[1]) };
						tooltip= Messages.format(TypeHierarchyMessages.TypeHierarchyViewPart_tooltip_more, elementLabel);
				}
			} else {
				switch (fInputElements.length) {
					case 1:
						elementLabel= new String[] { HistoryAction.getShortLabel(fInputElements[0]), workingSet.getLabel() };
						description= Messages.format(TypeHierarchyMessages.TypeHierarchyViewPart_ws_description, elementLabel);
						tooltip= Messages.format(TypeHierarchyMessages.TypeHierarchyViewPart_ws_tooltip, elementLabel);
						break;
					case 2:
						elementLabel= new String[] { HistoryAction.getShortLabel(fInputElements[0]), HistoryAction.getShortLabel(fInputElements[1]), workingSet.getLabel() };
						description= Messages.format(TypeHierarchyMessages.TypeHierarchyViewPart_ws_description2, elementLabel);
						tooltip= Messages.format(TypeHierarchyMessages.TypeHierarchyViewPart_ws_tooltip2, elementLabel);
						break;
					default:
						elementLabel= new String[] { HistoryAction.getShortLabel(fInputElements[0]), HistoryAction.getShortLabel(fInputElements[1]), workingSet.getLabel() };
						description= Messages.format(TypeHierarchyMessages.TypeHierarchyViewPart_ws_description_more, elementLabel);
						tooltip= Messages.format(TypeHierarchyMessages.TypeHierarchyViewPart_ws_tooltip_more, elementLabel);
				}

			}
		} else {
			description= ""; //$NON-NLS-1$
			tooltip= getPartName();
		}
		setContentDescription(description);
		setTitleToolTip(tooltip);
	}

	private void updateToolbarButtons() {
		boolean isNull= fInputElements == null;
		boolean isType= !isNull && fInputElements.length == 1 && fInputElements[0] instanceof IType;
		for (int i= 0; i < fViewActions.length; i++) {
			ToggleViewAction action= fViewActions[i];
			if (action.getViewerIndex() == HIERARCHY_MODE_CLASSIC) {
				action.setEnabled(!isNull);
			} else {
				action.setEnabled(isType);
			}
		}
	}


	public void setHierarchyMode(int viewerIndex) {
		Assert.isNotNull(fAllViewers);
		if (viewerIndex < fAllViewers.length && fCurrentViewerIndex != viewerIndex) {

			fCurrentViewerIndex= viewerIndex;

			updateHierarchyViewer(true);
			if (fInputElements != null) {
				ISelection currSelection= getCurrentViewer().getSelection();
				if (currSelection == null || currSelection.isEmpty()) {
					internalSelectType(getSelectableType(fInputElements), false);
					currSelection= getCurrentViewer().getSelection();
				}
				if (!fIsEnableMemberFilter) {
					typeSelectionChanged(currSelection);
				}
			}
			updateToolTipAndDescription();

			fDialogSettings.put(DIALOGSTORE_HIERARCHYVIEW, viewerIndex);
			getCurrentViewer().getTree().setFocus();

			if (fTypeOpenAndLinkWithEditorHelper != null)
				fTypeOpenAndLinkWithEditorHelper.dispose();
			fTypeOpenAndLinkWithEditorHelper= new OpenAndLinkWithEditorHelper(getCurrentViewer()) {
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
					// do nothing: this is handled in more detailed by the part itself
				}

				@Override
				protected void open(ISelection selection, boolean activate) {
					if (selection instanceof IStructuredSelection)
						fOpenAction.run((IStructuredSelection)selection);
				}
			};

		}
		for (int i= 0; i < fViewActions.length; i++) {
			ToggleViewAction action= fViewActions[i];
			action.setChecked(fCurrentViewerIndex == action.getViewerIndex());
		}
	}

	public int getHierarchyMode() {
		return fCurrentViewerIndex;
	}

	private TypeHierarchyViewer getCurrentViewer() {
		return fAllViewers[fCurrentViewerIndex];
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.ITypeHierarchyViewPart#showMembersInHierarchy(boolean)
	 */
	public void showMembersInHierarchy(boolean on) {
		if (on != fIsEnableMemberFilter) {
			fIsEnableMemberFilter= on;
			if (!on) {
				IType methodViewerInput= (IType) fMethodsViewer.getInput();
				setMemberFilter(null);
				updateHierarchyViewer(true);
				updateToolTipAndDescription();

				if (methodViewerInput != null && getCurrentViewer().isElementShown(methodViewerInput)) {
					// avoid that the method view changes content by selecting the previous input
					internalSelectType(methodViewerInput, true);
				} else if (fSelectedType != null) {
					// choose a input that exists
					internalSelectType(fSelectedType, true);
					updateMethodViewer(fSelectedType);
				}
			} else {
				methodSelectionChanged(fMethodsViewer.getSelection());
			}
		}
		fEnableMemberFilterAction.setChecked(on);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.ITypeHierarchyViewPart#isShowMembersInHierarchy()
	 */
	public boolean isShowMembersInHierarchy() {
		return fIsEnableMemberFilter;
	}


	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.ITypeHierarchyViewPart#showQualifiedTypeNames(boolean)
	 */
	public void showQualifiedTypeNames(boolean on) {
		if (on != fShowQualifiedTypeNames) {
			fShowQualifiedTypeNames= on;
			if (fAllViewers != null) {
				for (int i= 0; i < fAllViewers.length; i++) {
					fAllViewers[i].setQualifiedTypeName(on);
				}
			}
		}
		fShowQualifiedTypeNamesAction.setChecked(on);
		fDialogSettings.put(DIALOGSTORE_QUALIFIED_NAMES, on);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.ITypeHierarchyViewPart#isQualifiedTypeNamesEnabled()
	 */
	public boolean isQualifiedTypeNamesEnabled() {
		return fShowQualifiedTypeNames;
	}

	/**
	 * Called from ITypeHierarchyLifeCycleListener.
	 * Can be called from any thread
	 * @param typeHierarchy Hierarchy that has changed
	 * @param changedTypes Types in the hierarchy that have change or <code>null</code> if the full hierarchy has changed
	 */
	protected void doTypeHierarchyChanged(final TypeHierarchyLifeCycle typeHierarchy, final IType[] changedTypes) {
		if (!fIsVisible) {
			fNeedRefresh= true;
			return;
		}
		if (fIsRefreshRunnablePosted) {
			return;
		}

		Display display= getDisplay();
		if (display != null) {
			fIsRefreshRunnablePosted= true;
			display.asyncExec(new Runnable() {
				public void run() {
					try {
						if (fPagebook != null && !fPagebook.isDisposed()) {
							doTypeHierarchyChangedOnViewers(changedTypes);
						}
					} finally {
						fIsRefreshRunnablePosted= false;
					}
				}
			});
		}
	}

	protected void doTypeHierarchyChangedOnViewers(IType[] changedTypes) {
		if (fHierarchyLifeCycle.getHierarchy() == null || !fHierarchyLifeCycle.getHierarchy().exists()) {
			clearInput();
		} else {
			if (changedTypes == null) {
				// hierarchy change
				try {
					fHierarchyLifeCycle.ensureRefreshedTypeHierarchy(fInputElements, getSite().getWorkbenchWindow());
				} catch (InvocationTargetException e) {
					ExceptionHandler.handle(e, getSite().getShell(), TypeHierarchyMessages.TypeHierarchyViewPart_exception_title, TypeHierarchyMessages.TypeHierarchyViewPart_exception_message);
					clearInput();
					return;
				} catch (InterruptedException e) {
					return;
				}
				fMethodsViewer.refresh();
				updateHierarchyViewer(false);
			} else {
				// elements in hierarchy modified
				Object methodViewerInput= fMethodsViewer.getInput();
				fMethodsViewer.refresh();
				fMethodViewerPaneLabel.setText(fPaneLabelProvider.getText(methodViewerInput));
				fMethodViewerPaneLabel.setImage(fPaneLabelProvider.getImage(methodViewerInput));
				if (getCurrentViewer().isMethodFiltering()) {
					if (changedTypes.length == 1) {
						getCurrentViewer().refresh(changedTypes[0]);
					} else {
						updateHierarchyViewer(false);
					}
				} else {
					getCurrentViewer().update(changedTypes, new String[] { IBasicPropertyConstants.P_TEXT, IBasicPropertyConstants.P_IMAGE } );
				}
			}
		}
	}

	/*
	 * @see IViewPart#init
	 */
	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		fMemento= memento;
	}

	/*
	 * @see ViewPart#saveState(IMemento)
	 */
	@Override
	public void saveState(IMemento memento) {
		if (fPagebook == null) {
			// part has not been created
			if (fMemento != null) { //Keep the old state;
				memento.putMemento(fMemento);
			}
			return;
		}
		if (fInputElements != null) {
			memento.putString(TAG_INPUT, fInputElements[0].getHandleIdentifier());
			for (int i= 1; i < fInputElements.length; i++) {
				IJavaElement element= fInputElements[i];
				memento.putString(TAG_INPUT + i, element.getHandleIdentifier());
			}
		}
		memento.putInteger(TAG_VIEW, getHierarchyMode());
		memento.putInteger(TAG_LAYOUT, getViewLayout());
		memento.putInteger(TAG_QUALIFIED_NAMES, isQualifiedTypeNamesEnabled() ? 1 : 0);
		memento.putInteger(TAG_EDITOR_LINKING, isLinkingEnabled() ? 1 : 0);

		int weigths[]= fTypeMethodsSplitter.getWeights();
		int ratio= (weigths[0] * 1000) / (weigths[0] + weigths[1]);
		memento.putInteger(TAG_RATIO, ratio);

		ScrollBar bar= getCurrentViewer().getTree().getVerticalBar();
		int position= bar != null ? bar.getSelection() : 0;
		memento.putInteger(TAG_VERTICAL_SCROLL, position);

		IJavaElement selection= (IJavaElement)((IStructuredSelection) getCurrentViewer().getSelection()).getFirstElement();
		if (selection != null) {
			memento.putString(TAG_SELECTION, selection.getHandleIdentifier());
		}

		fWorkingSetActionGroup.saveState(memento);

		fMethodsViewer.saveState(memento);
	}

	/*
	 * Restores the type hierarchy settings from a memento.
	 */
	private void restoreState(final IMemento memento) {
		IJavaElement input= null;
		List<IJavaElement> inputList= new ArrayList<IJavaElement>();
		String elementId= memento.getString(TAG_INPUT);
		int i= 0;
		while (elementId != null) {
			input= JavaCore.create(elementId);
			if (input == null || !input.exists()) {
				inputList= null;
				break;
			}
			inputList.add(input);
			elementId= memento.getString(TAG_INPUT + ++i);
		}
		if (inputList == null || inputList.size() == 0) {
			doRestoreState(memento, input);
		} else {
			final IJavaElement[] hierarchyInput= inputList.toArray(new IJavaElement[inputList.size()]);

			synchronized (this) {
				String label= Messages.format(TypeHierarchyMessages.TypeHierarchyViewPart_restoreinput, HistoryAction.getElementLabel(hierarchyInput));
				fNoHierarchyShownLabel.setText(label);

				fRestoreStateJob= new Job(label) {
					@Override
					protected IStatus run(IProgressMonitor monitor) {
						try {
							doRestoreInBackground(memento, hierarchyInput, monitor);
						} catch (JavaModelException e) {
							return e.getStatus();
						} catch (OperationCanceledException e) {
							if (fRestoreJobCanceledExplicitly) {
								showEmptyViewer();
							}
							return Status.CANCEL_STATUS;
						}
						return Status.OK_STATUS;
					}
				};
				fRestoreStateJob.schedule();
			}
		}
	}


	private void doRestoreInBackground(final IMemento memento, final IJavaElement[] hierarchyInput, IProgressMonitor monitor) throws JavaModelException {
		fHierarchyLifeCycle.doHierarchyRefresh(hierarchyInput, monitor);
		final boolean doRestore= !monitor.isCanceled();
		if (doRestore) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					// running async: check first if view still exists
					if (fPagebook != null && !fPagebook.isDisposed()) {
						doRestoreState(memento, hierarchyInput);
					}
				}
			});
		}
	}


	final void doRestoreState(IMemento memento, IJavaElement input) {
		doRestoreState(memento, input == null ? null : new IJavaElement[] { input });
	}

	/**
	 * Restores the state of the type hierarchy view from the memento.
	 * 
	 * @param memento the memento
	 * @param input the input java elements
	 * @since 3.7
	 */
	final void doRestoreState(IMemento memento, IJavaElement[] input) {
		synchronized (this) {
			if (fRestoreStateJob == null) {
				return;
			}
			fRestoreStateJob= null;
		}

		fWorkingSetActionGroup.restoreState(memento);
		setKeepShowingEmptyViewers(false);
		setInputElements(input);

		Integer viewerIndex= memento.getInteger(TAG_VIEW);
		if (viewerIndex != null) {
			setHierarchyMode(viewerIndex.intValue());
		}
		Integer layout= memento.getInteger(TAG_LAYOUT);
		if (layout != null) {
			setViewLayout(layout.intValue());
		}

		Integer val= memento.getInteger(TAG_EDITOR_LINKING);
		if (val != null) {
			setLinkingEnabled(val.intValue() != 0);
		}

		Integer showQualified= memento.getInteger(TAG_QUALIFIED_NAMES);
		if (showQualified != null) {
			showQualifiedTypeNames(showQualified.intValue() != 0);
		}

		updateCheckedState();

		Integer ratio= memento.getInteger(TAG_RATIO);
		if (ratio != null) {
			fTypeMethodsSplitter.setWeights(new int[] { ratio.intValue(), 1000 - ratio.intValue() });
		}
		ScrollBar bar= getCurrentViewer().getTree().getVerticalBar();
		if (bar != null) {
			Integer vScroll= memento.getInteger(TAG_VERTICAL_SCROLL);
			if (vScroll != null) {
				bar.setSelection(vScroll.intValue());
			}
		}
		fMethodsViewer.restoreState(memento);
	}

	/**
	 * View part becomes visible.
	 *
	 * @param isVisible <code>true</code> if visible
	 */
	protected void visibilityChanged(boolean isVisible) {
		fIsVisible= isVisible;
		if (isVisible && fNeedRefresh) {
			doTypeHierarchyChangedOnViewers(null);
		}
		fNeedRefresh= false;
	}


	/**
	 * Link selection to active editor.
	 * @param editor The activated editor
	 */
	protected void editorActivated(IEditorPart editor) {
		if (!isLinkingEnabled()) {
			return;
		}
		if (fInputElements == null) {
			// no type hierarchy shown
			return;
		}

		IJavaElement elem= (IJavaElement)editor.getEditorInput().getAdapter(IJavaElement.class);
		if (elem instanceof ITypeRoot) {
			IType type= ((ITypeRoot) elem).findPrimaryType();
			if (type != null) {
				internalSelectType(type, true);
				if (getCurrentViewer().getSelection().isEmpty()) {
					updateMethodViewer(null);
				} else {
					updateMethodViewer(type);
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.viewsupport.IViewPartInputProvider#getViewPartInput()
	 */
	public Object getViewPartInput() {
		return fInputElements;
	}


	/**
	 * @return Returns the <code>IShowInSource</code> for this view.
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

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.ITypeHierarchyViewPart#isLinkingEnabled()
	 */
	public boolean isLinkingEnabled() {
		return fLinkingEnabled;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.ITypeHierarchyViewPart#setLinkingEnabled(boolean)
	 */
	public void setLinkingEnabled(boolean enabled) {
		fLinkingEnabled= enabled;
		fToggleLinkingAction.setChecked(enabled);
		fDialogSettings.put(DIALOGSTORE_LINKEDITORS, enabled);

		if (enabled) {
			IWorkbenchPartSite site= getSite();
			if (site != null) {
				IEditorPart editor = site.getPage().getActiveEditor();
				if (editor != null) {
					editorActivated(editor);
				}
			}
		}
	}

	public void clearNeededRefresh() {
		fNeedRefresh= false;
	}

	/**
	 * Sets the empty viewer when the user cancels the computation.
	 * 
	 * @since 3.6
	 */
	public void showEmptyViewer() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				if (isDisposed())
					return;

				clearInput();
				setKeepShowingEmptyViewers(true);
			}
		});
	}

	/**
	 * Checks if the type hierarchy view part has been disposed.
	 *
	 * @return <code>true</code> if the type hierarchy view part has been disposed, <code>false</code> otherwise
	 * @since 3.6
	 */
	private boolean isDisposed() {
		return fHierarchyLifeCycle == null;
	}

	/**
	 * Returns the type hierarchy life cycle.
	 *
	 * @return the type hierarchy life cycle
	 *
	 * @since 3.6
	 */
	public TypeHierarchyLifeCycle getTypeHierarchyLifeCycle() {
		return fHierarchyLifeCycle;

	}

	/**
	 * Sets the input for all the hierarchy viewers with their respective viewer instances.
	 *
	 * @since 3.6
	 */
	public void setViewersInput() {
		for (int i= 0; i < fAllViewers.length; i++) {
			fAllViewers[i].setInput(fAllViewers[i]);
		}
		setKeepShowingEmptyViewers(false);
	}

	/**
	 * Sets whether empty viewers should keep showing. If false, replace with fEmptyTypesViewer.
	 * 
	 * @param keepShowingEmptyViewers <code>true</code> if the empty viewers can be shown,
	 *            <code>false otherwise
	 * 
	 * @since 3.6
	 */
	public void setKeepShowingEmptyViewers(boolean keepShowingEmptyViewers) {
		fKeepShowingEmptyViewers= keepShowingEmptyViewers;
	}

	/**
	 * Returns value that determines whether the empty viewers should keep showing. If false,
	 * replace with fEmptyTypesViewer.
	 *
	 * @return <code>true</code> if the empty viewers can be shown, <code>false otherwise
	 *
	 * @since 3.6
	 */
	public boolean isKeepShowingEmptyViewers() {
		return fKeepShowingEmptyViewers;
	}
}

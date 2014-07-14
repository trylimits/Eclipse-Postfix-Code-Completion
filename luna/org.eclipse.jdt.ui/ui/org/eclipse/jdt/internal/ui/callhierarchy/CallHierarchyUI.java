/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 * 			(report 36180: Callers/Callees view)
 *   Stephan Herrmann (stephan@cs.tu-berlin.de):
 *          - bug 75800: [call hierarchy] should allow searches for fields
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;

import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.callhierarchy.CallHierarchy;
import org.eclipse.jdt.internal.corext.callhierarchy.CallLocation;
import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaStatusConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

public class CallHierarchyUI {
    private static final int DEFAULT_MAX_CALL_DEPTH= 10;
    private static final String PREF_MAX_CALL_DEPTH = "PREF_MAX_CALL_DEPTH"; //$NON-NLS-1$

    private static CallHierarchyUI fgInstance;
    private int fViewCount= 0;
    private final List<IMember[]> fMethodHistory= new ArrayList<IMember[]>();

	/**
	 * List of the Call Hierarchy views in LRU order, where the most recently used view is at index 0.
	 * @since 3.7
	 */
	private List<CallHierarchyViewPart> fLRUCallHierarchyViews= new ArrayList<CallHierarchyViewPart>();

    private CallHierarchyUI() {
        // Do nothing
    }

    public static CallHierarchyUI getDefault() {
        if (fgInstance == null) {
            fgInstance = new CallHierarchyUI();
        }

        return fgInstance;
    }

    /**
     * Returns the maximum tree level allowed
     * @return int
     */
    public int getMaxCallDepth() {
        int maxCallDepth;

        IPreferenceStore settings = JavaPlugin.getDefault().getPreferenceStore();
        maxCallDepth = settings.getInt(PREF_MAX_CALL_DEPTH);
        if (maxCallDepth < 1 || maxCallDepth > 99) {
            maxCallDepth= DEFAULT_MAX_CALL_DEPTH;
        }

        return maxCallDepth;
    }

    public void setMaxCallDepth(int maxCallDepth) {
        IPreferenceStore settings = JavaPlugin.getDefault().getPreferenceStore();
        settings.setValue(PREF_MAX_CALL_DEPTH, maxCallDepth);
    }

    public static void jumpToMember(IJavaElement element) {
        if (element != null) {
            try {
                JavaUI.openInEditor(element, true, true);
            } catch (JavaModelException e) {
                JavaPlugin.log(e);
            } catch (PartInitException e) {
                JavaPlugin.log(e);
            }
        }
    }

    public static void jumpToLocation(CallLocation callLocation) {
        try {
            IEditorPart methodEditor = JavaUI.openInEditor(callLocation.getMember(), false, false);
            if (methodEditor instanceof ITextEditor) {
                ITextEditor editor = (ITextEditor) methodEditor;
                editor.selectAndReveal(callLocation.getStart(),
                    (callLocation.getEnd() - callLocation.getStart()));
            }
        } catch (JavaModelException e) {
            JavaPlugin.log(e);
        } catch (PartInitException e) {
            JavaPlugin.log(e);
        }
    }

	/**
	 * Opens the element in the editor or shows an error dialog if that fails.
	 *
	 * @param element the element to open
	 * @param shell parent shell for error dialog
	 * @param activateOnOpen <code>true</code> if the editor should be activated
	 * @return <code>true</code> iff no error occurred while trying to open the editor,
	 *         <code>false</code> iff an error dialog was raised.
	 */
	public static boolean openInEditor(Object element, Shell shell, boolean activateOnOpen) {
        CallLocation callLocation= CallHierarchy.getCallLocation(element);

        try {
	        IMember enclosingMember;
	        int selectionStart;
			int selectionLength;

	        if (callLocation != null) {
				enclosingMember= callLocation.getMember();
				selectionStart= callLocation.getStart();
				selectionLength= callLocation.getEnd() - selectionStart;
	        } else if (element instanceof MethodWrapper) {
	        	enclosingMember= ((MethodWrapper) element).getMember();
	        	ISourceRange selectionRange= enclosingMember.getNameRange();
	        	if (selectionRange == null)
	        		selectionRange= enclosingMember.getSourceRange();
	        	if (selectionRange == null)
	        		return true;
	        	selectionStart= selectionRange.getOffset();
	        	selectionLength= selectionRange.getLength();
	        } else {
	            return true;
	        }

			IEditorPart methodEditor = JavaUI.openInEditor(enclosingMember, activateOnOpen, false);
            if (methodEditor instanceof ITextEditor) {
                ITextEditor editor = (ITextEditor) methodEditor;
				editor.selectAndReveal(selectionStart, selectionLength);
            }
            return true;
        } catch (JavaModelException e) {
            JavaPlugin.log(new Status(IStatus.ERROR, JavaPlugin.getPluginId(),
                    IJavaStatusConstants.INTERNAL_ERROR,
                    CallHierarchyMessages.CallHierarchyUI_open_in_editor_error_message, e));

            ErrorDialog.openError(shell, CallHierarchyMessages.OpenLocationAction_error_title,
                CallHierarchyMessages.CallHierarchyUI_open_in_editor_error_message,
                e.getStatus());
            return false;
        } catch (PartInitException x) {
            String name;
        	if (callLocation != null)
        		name= callLocation.getCalledMember().getElementName();
        	else if (element instanceof MethodWrapper)
        		name= ((MethodWrapper) element).getName();
        	else
        		name= "";  //$NON-NLS-1$
            MessageDialog.openError(shell, CallHierarchyMessages.OpenLocationAction_error_title,
                Messages.format(
                    CallHierarchyMessages.CallHierarchyUI_open_in_editor_error_messageArgs,
                    new String[] { name, x.getMessage() }));
            return false;
        }
    }

    public static IEditorPart isOpenInEditor(Object elem) {
        IJavaElement javaElement= null;
        if (elem instanceof MethodWrapper) {
            javaElement= ((MethodWrapper) elem).getMember();
        } else if (elem instanceof CallLocation) {
            javaElement= ((CallLocation) elem).getMember();
        }
        if (javaElement != null) {
            return EditorUtility.isOpenInEditor(javaElement);
        }
        return null;
    }

    public static CallHierarchyViewPart openSelectionDialog(IMember[] candidates, IWorkbenchWindow window) {
        Assert.isTrue(candidates != null);

        IMember input= null;
        if (candidates.length > 1) {
            String title= CallHierarchyMessages.CallHierarchyUI_selectionDialog_title;
            String message= CallHierarchyMessages.CallHierarchyUI_selectionDialog_message;
            input= (IMember) SelectionConverter.selectJavaElement(candidates, window.getShell(), title, message);
        } else if (candidates.length == 1) {
            input= candidates[0];
        }
        if (input == null)
            return openView(new IMember[] {}, window);

        return openView(new IMember[] { input }, window);
    }

    public static CallHierarchyViewPart openView(IMember[] input, IWorkbenchWindow window) {
    	if (input.length == 0) {
			MessageDialog.openInformation(window.getShell(), CallHierarchyMessages.CallHierarchyUI_selectionDialog_title,
					CallHierarchyMessages.CallHierarchyUI_open_operation_unavialable);
			return null;
		}
        IWorkbenchPage page= window.getActivePage();
		try {
			CallHierarchyViewPart viewPart= getDefault().findLRUCallHierarchyViewPart(page); //find the first view which is not pinned
			String secondaryId= null;
			if (viewPart == null) {
				if (page.findViewReference(CallHierarchyViewPart.ID_CALL_HIERARCHY) != null) //all the current views are pinned, open a new instance
					secondaryId= String.valueOf(++getDefault().fViewCount);
			} else
				secondaryId= viewPart.getViewSite().getSecondaryId();
			viewPart= (CallHierarchyViewPart)page.showView(CallHierarchyViewPart.ID_CALL_HIERARCHY, secondaryId, IWorkbenchPage.VIEW_ACTIVATE);
			viewPart.setInputElements(input);
			return viewPart;
        } catch (CoreException e) {
            ExceptionHandler.handle(e, window.getShell(),
                CallHierarchyMessages.CallHierarchyUI_error_open_view, e.getMessage());
        }
        return null;
    }

	/**
	 * Finds the first Call Hierarchy view part instance that is not pinned.
	 * 
	 * @param page the active page
	 * @return the Call Hierarchy view part to open or <code>null</code> if none found
	 * @since 3.7
	 */
	private CallHierarchyViewPart findLRUCallHierarchyViewPart(IWorkbenchPage page) {
		boolean viewFoundInPage= false;
		for (Iterator<CallHierarchyViewPart> iter= fLRUCallHierarchyViews.iterator(); iter.hasNext();) {
			CallHierarchyViewPart view= iter.next();
			if (page.equals(view.getSite().getPage())) {
				if (!view.isPinned()) {
					return view;
				}
				viewFoundInPage= true;
			}
		}
		if (!viewFoundInPage) {
			// find unresolved views
			IViewReference[] viewReferences= page.getViewReferences();
			for (int i= 0; i < viewReferences.length; i++) {
				IViewReference curr= viewReferences[i];
				if (CallHierarchyViewPart.ID_CALL_HIERARCHY.equals(curr.getId()) && page.equals(curr.getPage())) {
					CallHierarchyViewPart view= (CallHierarchyViewPart)curr.getView(true);
					if (view != null && !view.isPinned()) {
						return view;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Adds the activated view part to the head of the list.
	 * 
	 * @param view the Call Hierarchy view part
	 * @since 3.7
	 */
	void callHierarchyViewActivated(CallHierarchyViewPart view) {
		fLRUCallHierarchyViews.remove(view);
		fLRUCallHierarchyViews.add(0, view);
	}

	/**
	 * Removes the closed view part from the list.
	 * 
	 * @param view the closed view part
	 * @since 3.7
	 */
	void callHierarchyViewClosed(CallHierarchyViewPart view) {
		fLRUCallHierarchyViews.remove(view);
	}

    /**
     * Converts an ISelection (containing MethodWrapper instances) to an ISelection
     * with the MethodWrapper's replaced by their corresponding IMembers. If the selection
     * contains elements which are not MethodWrapper instances or not already IMember instances
     * they are discarded.
     * @param selection The selection to convert.
     * @return An ISelection containing IMember's in place of MethodWrapper instances.
     */
    static ISelection convertSelection(ISelection selection) {
        if (selection.isEmpty()) {
            return selection;
        }

        if (selection instanceof IStructuredSelection) {
            IStructuredSelection structuredSelection= (IStructuredSelection) selection;
            List<IMember> javaElements= new ArrayList<IMember>();
            for (Iterator<?> iter= structuredSelection.iterator(); iter.hasNext();) {
                Object element= iter.next();
                if (element instanceof MethodWrapper) {
                    IMember member= ((MethodWrapper)element).getMember();
                    if (member != null) {
                        javaElements.add(member);
                    }
                } else if (element instanceof IMember) {
                    javaElements.add((IMember) element);
                } else if (element instanceof CallLocation) {
                    IMember member = ((CallLocation) element).getMember();
                    javaElements.add(member);
                }
            }
            return new StructuredSelection(javaElements);
        }
        return StructuredSelection.EMPTY;
    }

	/**
	 * Clears the history and updates all the open views.
	 * 
	 * @since 3.7
	 */
	void clearHistory() {
		for (Iterator<CallHierarchyViewPart> iter= fLRUCallHierarchyViews.iterator(); iter.hasNext();) {
			CallHierarchyViewPart part= iter.next();
			part.setHistoryEntries(new IMember[0][]);
			part.setInputElements(null);
		}
	}

	/**
	 * Returns the method history.
	 * 
	 * @return the method history
	 * @since 3.7
	 */
	List<IMember[]> getMethodHistory() {
		return fMethodHistory;
	}
}

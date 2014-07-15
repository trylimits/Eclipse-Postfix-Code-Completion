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
package org.eclipse.jdt.internal.ui.search;

import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.IPageSite;

import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.AbstractTextSearchViewPage;
import org.eclipse.search.ui.text.Match;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.SharedASTProvider;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.AbstractToggleLinkingAction;
import org.eclipse.jdt.internal.ui.viewsupport.ColoringLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.ISelectionListenerWithAST;
import org.eclipse.jdt.internal.ui.viewsupport.SelectionListenerWithASTManager;


public class OccurrencesSearchResultPage extends AbstractTextSearchViewPage {

	private static final String DIALOGSTORE_LINKEDITORS= "TypeHierarchyViewPart.linkeditors"; //$NON-NLS-1$

	private TextSearchTableContentProvider fContentProvider;

	private boolean fLinkingEnabled;
	private AbstractToggleLinkingAction fToggleLinkingAction;
	private LinkWithEditorListener fLinkWithEditorListener;

	private class LinkWithEditorListener implements IPartListener2, ISelectionListenerWithAST {

		private ITextEditor fActiveEditor;
		private boolean fIsVisible;

		public void install(IWorkbenchPage page) {
			page.addPartListener(this);
			fIsVisible= page.isPartVisible(getViewPart());
			if (fIsVisible) {
				installOnActiveEditor(page);
			}
		}

		private void installOnActiveEditor(IWorkbenchPage page) {
			IEditorPart activeEditor= page.getActiveEditor();
			if (activeEditor instanceof ITextEditor) {
				editorActive(activeEditor);
				ISelection selection= activeEditor.getSite().getSelectionProvider().getSelection();
				ITypeRoot typeRoot= JavaUI.getEditorInputTypeRoot(activeEditor.getEditorInput());
				if (typeRoot != null && selection instanceof ITextSelection) {
					CompilationUnit astRoot= SharedASTProvider.getAST(typeRoot, SharedASTProvider.WAIT_ACTIVE_ONLY, null);
					if (astRoot != null) {
						preformEditorSelectionChanged((ITextSelection) selection, astRoot);
					}
				}
			}
		}

		private void uninstallOnActiveEditor() {
			if (fActiveEditor != null) {
				SelectionListenerWithASTManager.getDefault().removeListener(fActiveEditor, this);
				fActiveEditor= null;
			}
		}

		public void uninstall(IWorkbenchPage page) {
			page.removePartListener(this);
			uninstallOnActiveEditor();
		}

		public void partActivated(IWorkbenchPartReference partRef) {
			if (fIsVisible && partRef instanceof IEditorReference) {
				editorActive(((IEditorReference) partRef).getEditor(true));
			}
		}

		private void editorActive(IEditorPart editor) {
			if (editor instanceof ITextEditor) {
				if (editor != fActiveEditor) {
					setInput(null, null);
				}
				fActiveEditor= (ITextEditor) editor;
				SelectionListenerWithASTManager.getDefault().addListener(fActiveEditor, this);
			}
		}

		public void partDeactivated(IWorkbenchPartReference partRef) {
		}

		public void selectionChanged(IEditorPart part, ITextSelection selection, CompilationUnit astRoot) {
			preformEditorSelectionChanged(selection, astRoot);
		}

		public void partVisible(IWorkbenchPartReference partRef) {
			if (NewSearchUI.SEARCH_VIEW_ID.equals(partRef.getId()) && partRef.getPart(true) == getViewPart()) {
				if (fActiveEditor == null) {
					fIsVisible= true;
				}
			}
		}

		public void partHidden(IWorkbenchPartReference partRef) {
			if (NewSearchUI.SEARCH_VIEW_ID.equals(partRef.getId()) && partRef.getPart(true) == getViewPart()) {
				fIsVisible= false;
				uninstallOnActiveEditor();
			} else if (partRef instanceof IEditorReference && partRef.getPart(true) == fActiveEditor) {
				uninstallOnActiveEditor();
			}
		}

		public void partBroughtToTop(IWorkbenchPartReference partRef) {
		}

		public void partClosed(IWorkbenchPartReference partRef) {
			if (partRef instanceof IEditorReference && partRef.getPart(true) == fActiveEditor) {
				uninstallOnActiveEditor();
			}
		}

		public void partInputChanged(IWorkbenchPartReference partRef) {
		}

		public void partOpened(IWorkbenchPartReference partRef) {
		}
	}

	private class ToggleLinkingAction extends AbstractToggleLinkingAction {
		@Override
		public void run() {
			setLinkingEnabled(isChecked());
		}
	}

	public OccurrencesSearchResultPage() {
		super(AbstractTextSearchViewPage.FLAG_LAYOUT_FLAT);
		fLinkWithEditorListener= new LinkWithEditorListener();
		fLinkingEnabled= false;
		fToggleLinkingAction= new ToggleLinkingAction();
		fToggleLinkingAction.setActionDefinitionId(IWorkbenchCommandConstants.NAVIGATE_TOGGLE_LINK_WITH_EDITOR);
	}

	@Override
	public void init(IPageSite pageSite) {
		super.init(pageSite);
		// wait until site is set
		setLinkingEnabled(getDialogSettings().getBoolean(DIALOGSTORE_LINKEDITORS));
	}

	@Override
	public void dispose() {
		if (fLinkingEnabled) {
			fLinkWithEditorListener.uninstall(getSite().getPage());
		}
		super.dispose();
	}

	@Override
	protected void fillToolbar(IToolBarManager tbm) {
		super.fillToolbar(tbm);
		tbm.add(fToggleLinkingAction);
	}

	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);

		IActionBars bars= getSite().getActionBars();
		IMenuManager menu= bars.getMenuManager();
		menu.add(fToggleLinkingAction);

		IHandlerService handlerService= (IHandlerService) getSite().getService(IHandlerService.class);
		handlerService.activateHandler(IWorkbenchCommandConstants.NAVIGATE_TOGGLE_LINK_WITH_EDITOR, new ActionHandler(fToggleLinkingAction));
	}

	/*
	 * @see org.eclipse.search.ui.text.AbstractTextSearchViewPage#showMatch(org.eclipse.search.ui.text.Match, int, int)
	 */
	@Override
	protected void showMatch(Match match, int currentOffset, int currentLength, boolean activate) throws PartInitException {
		JavaElementLine element= (JavaElementLine) match.getElement();
		IJavaElement javaElement= element.getJavaElement();
		try {
			IEditorPart editor= JavaUI.openInEditor(javaElement, false, false);
			if (editor instanceof ITextEditor) {
				ITextEditor textEditor= (ITextEditor) editor;
				textEditor.selectAndReveal(currentOffset, currentLength);
			}
			// activating at the end avoids an outdated selection event from JavaUI.openInEditor(..):
			if (editor != null && activate)
				editor.getEditorSite().getPage().activate(editor);
		} catch (PartInitException e1) {
			return;
		} catch (JavaModelException e1) {
			return;
		}

	}

	/*
	 * @see org.eclipse.search.ui.text.AbstractTextSearchViewPage#elementsChanged(java.lang.Object[])
	 */
	@Override
	protected void elementsChanged(Object[] objects) {
		if (fContentProvider != null)
			fContentProvider.elementsChanged(objects);
	}

	/*
	 * @see org.eclipse.search.ui.text.AbstractTextSearchViewPage#clear()
	 */
	@Override
	protected void clear() {
		if (fContentProvider != null)
			fContentProvider.clear();
	}

	/*
	 * @see org.eclipse.search.ui.text.AbstractTextSearchViewPage#configureTreeViewer(org.eclipse.jface.viewers.TreeViewer)
	 */
	@Override
	protected void configureTreeViewer(TreeViewer viewer) {
		throw new IllegalStateException("Doesn't support tree mode."); //$NON-NLS-1$
	}

	/*
	 * @see org.eclipse.search.ui.text.AbstractTextSearchViewPage#configureTableViewer(org.eclipse.jface.viewers.TableViewer)
	 */
	@Override
	protected void configureTableViewer(TableViewer viewer) {
		viewer.setComparator(new ViewerComparator() {
			@Override
			public int compare(Viewer v, Object e1, Object e2) {
				JavaElementLine jel1= (JavaElementLine) e1;
				JavaElementLine jel2= (JavaElementLine) e2;
				return jel1.getLineNumber() - jel2.getLineNumber();
			}
		});
		viewer.setLabelProvider(new ColoringLabelProvider(new OccurrencesSearchLabelProvider(this)));
		fContentProvider= new TextSearchTableContentProvider();
		viewer.setContentProvider(fContentProvider);
	}

	public boolean isLinkingEnabled() {
		return fLinkingEnabled;
	}

	public void setLinkingEnabled(boolean enabled) {
		if (fLinkingEnabled != enabled) {
			fLinkingEnabled= enabled;
			fToggleLinkingAction.setChecked(enabled);
			getDialogSettings().put(DIALOGSTORE_LINKEDITORS, enabled);

			if (enabled) {
				fLinkWithEditorListener.install(getSite().getPage());
			} else {
				fLinkWithEditorListener.uninstall(getSite().getPage());
			}
		}
	}

	private IDialogSettings getDialogSettings() {
		return JavaPlugin.getDefault().getDialogSettingsSection("OccurrencesSearchResultPage"); //$NON-NLS-1$
	}


	private void preformEditorSelectionChanged(ITextSelection selection, CompilationUnit astRoot) {
		if (!isLinkingEnabled()) {
			return;
		}
		IOccurrencesFinder finder;

		AbstractTextSearchResult input= getInput();
		if (input == null) {
			finder= new OccurrencesFinder();
		} else {
			String id= ((OccurrencesSearchQuery) input.getQuery()).getFinderId();
			if (id == OccurrencesFinder.ID) {
				finder= new OccurrencesFinder();
			} else if (id == ExceptionOccurrencesFinder.ID) {
				finder= new ExceptionOccurrencesFinder();
			} else {
				finder= new ImplementOccurrencesFinder();
			}
		}

		int offset= selection.getOffset();
		int length= selection.getLength();
		if (finder.initialize(astRoot, offset, length) == null) {
			final OccurrencesSearchQuery query= new OccurrencesSearchQuery(finder, astRoot.getTypeRoot());
			query.run(null);
			OccurrencesSearchResult result= (OccurrencesSearchResult) query.getSearchResult();
			final JavaElementLine line= getMatchingLine(result, offset, length);

			getSite().getShell().getDisplay().asyncExec(new Runnable() {
				public void run() {
					setInput(query.getSearchResult(), line == null ? null : new StructuredSelection(line));
				}
			});
		}
	}

	private static JavaElementLine getMatchingLine(OccurrencesSearchResult result, int offset, int length) {
		Object[] elements= result.getElements();
		for (int i= 0; i < elements.length; i++) {
			JavaElementLine line= (JavaElementLine) elements[i];
			Match[] matches= result.getMatches(line);
			for (int j= 0; j < matches.length; j++) {
				OccurrenceMatch match= (OccurrenceMatch) matches[j];
				if (match.getOriginalOffset() <= offset && offset + length <= match.getOriginalOffset() + match.getOriginalLength()) {
					return line;
				}
			}
		}
		return null;
	}
}

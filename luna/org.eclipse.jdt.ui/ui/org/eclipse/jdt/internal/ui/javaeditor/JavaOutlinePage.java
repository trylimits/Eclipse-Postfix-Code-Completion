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
package org.eclipse.jdt.internal.ui.javaeditor;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.Widget;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.ListenerList;

import org.eclipse.core.resources.IResource;

import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.IPostSelectionProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.OpenAndLinkWithEditorHelper;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.handlers.CollapseAllHandler;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.model.IWorkbenchAdapter;
import org.eclipse.ui.model.WorkbenchAdapter;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.IShowInTarget;
import org.eclipse.ui.part.IShowInTargetList;
import org.eclipse.ui.part.Page;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.IUpdate;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.formatter.CodeFormatter;

import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.ProblemsLabelDecorator.ProblemsLabelChangedEvent;
import org.eclipse.jdt.ui.actions.CCPActionGroup;
import org.eclipse.jdt.ui.actions.CustomFiltersActionGroup;
import org.eclipse.jdt.ui.actions.GenerateActionGroup;
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds;
import org.eclipse.jdt.ui.actions.JavaSearchActionGroup;
import org.eclipse.jdt.ui.actions.JdtActionConstants;
import org.eclipse.jdt.ui.actions.MemberFilterActionGroup;
import org.eclipse.jdt.ui.actions.OpenViewActionGroup;
import org.eclipse.jdt.ui.actions.RefactorActionGroup;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.actions.AbstractToggleLinkingAction;
import org.eclipse.jdt.internal.ui.actions.CategoryFilterActionGroup;
import org.eclipse.jdt.internal.ui.actions.CollapseAllAction;
import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.dnd.JdtViewerDragSupport;
import org.eclipse.jdt.internal.ui.dnd.JdtViewerDropSupport;
import org.eclipse.jdt.internal.ui.preferences.MembersOrderPreferenceCache;
import org.eclipse.jdt.internal.ui.util.ElementValidator;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.DecoratingJavaLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.SourcePositionComparator;
import org.eclipse.jdt.internal.ui.viewsupport.StatusBarUpdater;


/**
 * The content outline page of the Java editor. The viewer implements a proprietary
 * update mechanism based on Java model deltas. It does not react on domain changes.
 * It is specified to show the content of ICompilationUnits and IClassFiles.
 * Publishes its context menu under <code>JavaPlugin.getDefault().getPluginId() + ".outline"</code>.
 */
public class JavaOutlinePage extends Page implements IContentOutlinePage, IAdaptable , IPostSelectionProvider {

			static Object[] NO_CHILDREN= new Object[0];

			/**
			 * The element change listener of the java outline viewer.
			 * @see IElementChangedListener
			 */
			protected class ElementChangedListener implements IElementChangedListener {

				public void elementChanged(final ElementChangedEvent e) {

					if (getControl() == null)
						return;

					Display d= getControl().getDisplay();
					if (d != null) {
						d.asyncExec(new Runnable() {
							public void run() {
								ICompilationUnit cu= (ICompilationUnit) fInput;
								IJavaElement base= cu;
								if (fTopLevelTypeOnly) {
									base= cu.findPrimaryType();
									if (base == null) {
										if (fOutlineViewer != null)
											fOutlineViewer.refresh(true);
										return;
									}
								}
								IJavaElementDelta delta= findElement(base, e.getDelta());
								if (delta != null && fOutlineViewer != null) {
									fOutlineViewer.reconcile(delta);
								}
							}
						});
					}
				}

				private boolean isPossibleStructuralChange(IJavaElementDelta cuDelta) {
					if (cuDelta.getKind() != IJavaElementDelta.CHANGED) {
						return true; // add or remove
					}
					int flags= cuDelta.getFlags();
					if ((flags & IJavaElementDelta.F_CHILDREN) != 0) {
						return true;
					}
					return (flags & (IJavaElementDelta.F_CONTENT | IJavaElementDelta.F_FINE_GRAINED)) == IJavaElementDelta.F_CONTENT;
				}

				protected IJavaElementDelta findElement(IJavaElement unit, IJavaElementDelta delta) {

					if (delta == null || unit == null)
						return null;

					IJavaElement element= delta.getElement();

					if (unit.equals(element)) {
						if (isPossibleStructuralChange(delta)) {
							return delta;
						}
						return null;
					}


					if (element.getElementType() > IJavaElement.CLASS_FILE)
						return null;

					IJavaElementDelta[] children= delta.getAffectedChildren();
					if (children == null || children.length == 0)
						return null;

					for (int i= 0; i < children.length; i++) {
						IJavaElementDelta d= findElement(unit, children[i]);
						if (d != null)
							return d;
					}

					return null;
				}
			}

			static class NoClassElement extends WorkbenchAdapter implements IAdaptable {
				/*
				 * @see java.lang.Object#toString()
				 */
				@Override
				public String toString() {
					return JavaEditorMessages.JavaOutlinePage_error_NoTopLevelType;
				}

				/*
				 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(Class)
				 */
				public Object getAdapter(Class clas) {
					if (clas == IWorkbenchAdapter.class)
						return this;
					return null;
				}
			}

			/**
			 * Content provider for the children of an ICompilationUnit or
			 * an IClassFile
			 * @see ITreeContentProvider
			 */
			protected class ChildrenProvider implements ITreeContentProvider {

				private Object[] NO_CLASS= new Object[] {new NoClassElement()};
				private ElementChangedListener fListener;

				protected boolean matches(IJavaElement element) {
					if (element.getElementType() == IJavaElement.METHOD) {
						String name= element.getElementName();
						return (name != null && name.indexOf('<') >= 0);
					}
					return false;
				}

				protected IJavaElement[] filter(IJavaElement[] children) {
					boolean initializers= false;
					for (int i= 0; i < children.length; i++) {
						if (matches(children[i])) {
							initializers= true;
							break;
						}
					}

					if (!initializers)
						return children;

					Vector<IJavaElement> v= new Vector<IJavaElement>();
					for (int i= 0; i < children.length; i++) {
						if (matches(children[i]))
							continue;
						v.addElement(children[i]);
					}

					IJavaElement[] result= new IJavaElement[v.size()];
					v.copyInto(result);
					return result;
				}

				public Object[] getChildren(Object parent) {
					if (parent instanceof IParent) {
						IParent c= (IParent) parent;
						try {
							return filter(c.getChildren());
						} catch (JavaModelException x) {
							// https://bugs.eclipse.org/bugs/show_bug.cgi?id=38341
							// don't log NotExist exceptions as this is a valid case
							// since we might have been posted and the element
							// removed in the meantime.
							if (JavaPlugin.isDebug() || !x.isDoesNotExist())
								JavaPlugin.log(x);
						}
					}
					return NO_CHILDREN;
				}

				public Object[] getElements(Object parent) {
					if (fTopLevelTypeOnly) {
						if (parent instanceof ITypeRoot) {
							try {
								IType type= ((ITypeRoot) parent).findPrimaryType();
								return type != null ? type.getChildren() : NO_CLASS;
							} catch (JavaModelException e) {
								JavaPlugin.log(e);
							}
						}
					}
					return getChildren(parent);
				}

				public Object getParent(Object child) {
					if (child instanceof IJavaElement) {
						IJavaElement e= (IJavaElement) child;
						return e.getParent();
					}
					return null;
				}

				public boolean hasChildren(Object parent) {
					if (parent instanceof IParent) {
						IParent c= (IParent) parent;
						try {
							IJavaElement[] children= filter(c.getChildren());
							return (children != null && children.length > 0);
						} catch (JavaModelException x) {
							// https://bugs.eclipse.org/bugs/show_bug.cgi?id=38341
							// don't log NotExist exceptions as this is a valid case
							// since we might have been posted and the element
							// removed in the meantime.
							if (JavaPlugin.isDebug() || !x.isDoesNotExist())
								JavaPlugin.log(x);
						}
					}
					return false;
				}

				public void dispose() {
					if (fListener != null) {
						JavaCore.removeElementChangedListener(fListener);
						fListener= null;
					}
				}

				/*
				 * @see IContentProvider#inputChanged(Viewer, Object, Object)
				 */
				public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
					boolean isCU= (newInput instanceof ICompilationUnit);

					if (isCU && fListener == null) {
						fListener= new ElementChangedListener();
						JavaCore.addElementChangedListener(fListener);
					} else if (!isCU && fListener != null) {
						JavaCore.removeElementChangedListener(fListener);
						fListener= null;
					}
				}
			}

			/**
			 * The tree viewer used for displaying the outline.
			 *
			 * @see TreeViewer
			 */
			protected class JavaOutlineViewer extends TreeViewer {

				public JavaOutlineViewer(Tree tree) {
					super(tree);
					setAutoExpandLevel(ALL_LEVELS);
					setUseHashlookup(true);
				}

				/**
				 * Investigates the given element change event and if affected
				 * incrementally updates the Java outline.
				 *
				 * @param delta the Java element delta used to reconcile the Java outline
				 */
				public void reconcile(IJavaElementDelta delta) {
					refresh(true);
				}

				/*
				 * @see TreeViewer#internalExpandToLevel
				 */
				@Override
				protected void internalExpandToLevel(Widget node, int level) {
					if (node instanceof Item) {
						Item i= (Item) node;
						if (i.getData() instanceof IJavaElement) {
							IJavaElement je= (IJavaElement) i.getData();
							if (je.getElementType() == IJavaElement.IMPORT_CONTAINER || isInnerType(je)) {
								setExpanded(i, false);
						return;
							}
						}
					}
					super.internalExpandToLevel(node, level);
				}

				/*
				 * @see org.eclipse.jface.viewers.AbstractTreeViewer#isExpandable(java.lang.Object)
				 */
				@Override
				public boolean isExpandable(Object element) {
					if (hasFilters()) {
						return getFilteredChildren(element).length > 0;
					}
					return super.isExpandable(element);
				}

				/*
				 * @see ContentViewer#handleLabelProviderChanged(LabelProviderChangedEvent)
				 */
				@Override
				protected void handleLabelProviderChanged(LabelProviderChangedEvent event) {
					Object input= getInput();
					if (event instanceof ProblemsLabelChangedEvent) {
						ProblemsLabelChangedEvent e= (ProblemsLabelChangedEvent) event;
						if (e.isMarkerChange() && input instanceof ICompilationUnit) {
							return; // marker changes can be ignored
						}
					}
					// look if the underlying resource changed
					Object[] changed= event.getElements();
					if (changed != null) {
						IResource resource= getUnderlyingResource();
						if (resource != null) {
							for (int i= 0; i < changed.length; i++) {
								if (changed[i] != null && changed[i].equals(resource)) {
									// change event to a full refresh
									event= new LabelProviderChangedEvent((IBaseLabelProvider) event.getSource());
									break;
								}
							}
						}
					}
					super.handleLabelProviderChanged(event);
				}

				private IResource getUnderlyingResource() {
					Object input= getInput();
					if (input instanceof ICompilationUnit) {
						ICompilationUnit cu= (ICompilationUnit) input;
						cu= cu.getPrimary();
						return cu.getResource();
					} else if (input instanceof IClassFile) {
						return ((IClassFile) input).getResource();
					}
					return null;
				}

			}


			class LexicalSortingAction extends Action {

				private JavaElementComparator fComparator= new JavaElementComparator();
				private SourcePositionComparator fSourcePositonComparator= new SourcePositionComparator();

				public LexicalSortingAction() {
					super();
					PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.LEXICAL_SORTING_OUTLINE_ACTION);
					setText(JavaEditorMessages.JavaOutlinePage_Sort_label);
					JavaPluginImages.setLocalImageDescriptors(this, "alphab_sort_co.gif"); //$NON-NLS-1$
					setToolTipText(JavaEditorMessages.JavaOutlinePage_Sort_tooltip);
					setDescription(JavaEditorMessages.JavaOutlinePage_Sort_description);

					boolean checked= JavaPlugin.getDefault().getPreferenceStore().getBoolean("LexicalSortingAction.isChecked"); //$NON-NLS-1$
					valueChanged(checked, false);
				}

				@Override
				public void run() {
					valueChanged(isChecked(), true);
				}

				private void valueChanged(final boolean on, boolean store) {
					setChecked(on);
					BusyIndicator.showWhile(fOutlineViewer.getControl().getDisplay(), new Runnable() {
						public void run() {
							if (on) {
								fOutlineViewer.setComparator(fComparator);
								fDropSupport.setFeedbackEnabled(false);
							} else {
								fOutlineViewer.setComparator(fSourcePositonComparator);
								fDropSupport.setFeedbackEnabled(true);
							}
						}
					});

					if (store)
						JavaPlugin.getDefault().getPreferenceStore().setValue("LexicalSortingAction.isChecked", on); //$NON-NLS-1$
				}
			}

		class ClassOnlyAction extends Action {

			public ClassOnlyAction() {
				super();
				PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.GO_INTO_TOP_LEVEL_TYPE_ACTION);
				setText(JavaEditorMessages.JavaOutlinePage_GoIntoTopLevelType_label);
				setToolTipText(JavaEditorMessages.JavaOutlinePage_GoIntoTopLevelType_tooltip);
				setDescription(JavaEditorMessages.JavaOutlinePage_GoIntoTopLevelType_description);
				JavaPluginImages.setLocalImageDescriptors(this, "gointo_toplevel_type.gif"); //$NON-NLS-1$

				IPreferenceStore preferenceStore= JavaPlugin.getDefault().getPreferenceStore();
				boolean showclass= preferenceStore.getBoolean("GoIntoTopLevelTypeAction.isChecked"); //$NON-NLS-1$
				setTopLevelTypeOnly(showclass);
			}

			/*
			 * @see org.eclipse.jface.action.Action#run()
			 */
			@Override
			public void run() {
				setTopLevelTypeOnly(!fTopLevelTypeOnly);
			}

			private void setTopLevelTypeOnly(boolean show) {
				fTopLevelTypeOnly= show;
				setChecked(show);
				fOutlineViewer.refresh(false);

				IPreferenceStore preferenceStore= JavaPlugin.getDefault().getPreferenceStore();
				preferenceStore.setValue("GoIntoTopLevelTypeAction.isChecked", show); //$NON-NLS-1$
			}
		}

		/**
		 * This action toggles whether this Java Outline page links
		 * its selection to the active editor.
		 *
		 * @since 3.0
		 */
		public class ToggleLinkingAction extends AbstractToggleLinkingAction {

			/**
			 * Constructs a new action.
			 */
			public ToggleLinkingAction() {
				boolean isLinkingEnabled= PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_SYNC_OUTLINE_ON_CURSOR_MOVE);
				setChecked(isLinkingEnabled);
				fOpenAndLinkWithEditorHelper.setLinkWithEditor(isLinkingEnabled);
			}

			/**
			 * Runs the action.
			 */
			@Override
			public void run() {
				final boolean isChecked= isChecked();
				PreferenceConstants.getPreferenceStore().setValue(PreferenceConstants.EDITOR_SYNC_OUTLINE_ON_CURSOR_MOVE, isChecked);
				if (isChecked && fEditor != null)
					fEditor.synchronizeOutlinePage(fEditor.computeHighlightRangeSourceReference(), false);
				fOpenAndLinkWithEditorHelper.setLinkWithEditor(isChecked);
			}

		}

		/**
		 * Empty selection provider.
		 *
		 * @since 3.2
		 */
		private static final class EmptySelectionProvider implements ISelectionProvider {
			public void addSelectionChangedListener(ISelectionChangedListener listener) {
			}
			public ISelection getSelection() {
				return StructuredSelection.EMPTY;
			}
			public void removeSelectionChangedListener(ISelectionChangedListener listener) {
			}
			public void setSelection(ISelection selection) {
			}
		}



	/**
	 * Formats the code associated with the elements selected in the Outline view. The action
	 * requires that the selection provided by the site's selection provider is of type
	 * {@link IStructuredSelection}
	 * 
	 * @since 3.7
	 */
	private class FormatElementAction extends SelectionDispatchAction {

		/**
		 * Creates a new <code>FormatViewElementAction</code>.
		 * 
		 * @param site the site providing context information for this action
		 */
		FormatElementAction(IPageSite site) {
			super(site);
		}

		/**
		 * Executes the action based on the Structured Selection. This formats the non-overlapping
		 * element(s) that have been selected in the view.
		 * 
		 * @param selection the current selection
		 */
		@Override
		public void run(IStructuredSelection selection) {
			ICompilationUnit compilationUnit= (ICompilationUnit)((IJavaElement)selection.getFirstElement()).getAncestor(IJavaElement.COMPILATION_UNIT);
			if (ElementValidator.check(compilationUnit, getShell(), JavaEditorMessages.JavaEditor_FormatElementDialog_label, fEditor != null)) {
				JavaSourceViewer javaSourceViewer= (JavaSourceViewer)fEditor.getViewer();
				javaSourceViewer.rememberSelection();
				javaSourceViewer.setRedraw(false);
				try {
					IDocument document= javaSourceViewer.getDocument();
					IRegion[] regions= getOrderedRegionsForNonOverlappingElements(selection, document);
					Map<String, String> formatterSettings= new HashMap<String, String>(compilationUnit.getJavaProject().getOptions(true));
					String content= compilationUnit.getBuffer().getContents();
					String lineDelimiter= TextUtilities.getDefaultLineDelimiter(document);

					TextEdit edit= CodeFormatterUtil.reformat(CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.F_INCLUDE_COMMENTS, content, regions, 0, lineDelimiter, formatterSettings);
					edit.apply(javaSourceViewer.getDocument());

				} catch (CoreException e) {
					JavaPlugin.log(e);
				} catch (MalformedTreeException e) {
					JavaPlugin.log(e);
				} catch (BadLocationException e) {
					JavaPlugin.log(e);
				} finally {
					javaSourceViewer.setRedraw(true);
					javaSourceViewer.restoreSelection();
				}
			}
		}

		/**
		 * Parses the selections for non-overlapping elements and returns their source regions
		 * ordered by their offsets.
		 * 
		 * @param selection the selected elements
		 * @param document the document containing the selected elements
		 * @return the array of ordered source regions
		 */
		private IRegion[] getOrderedRegionsForNonOverlappingElements(IStructuredSelection selection, IDocument document) {
			List<?> allElements= selection.toList();
			Iterator<?> iterator= selection.iterator();
			ArrayList<IRegion> regions= new ArrayList<IRegion>(selection.size());
			while (iterator.hasNext()) {
				Object element= iterator.next();
				if (!isElementOverlapping((IJavaElement)element, allElements)) {
					regions.add(getElementRegion(element, document));
				}
			}
			Comparator<IRegion> comparator= new Comparator<IRegion>() {
				public int compare(IRegion region0, IRegion region1) {
					int region1Offset= region0.getOffset();
					int region2Offset= region1.getOffset();
					if (region1Offset > region2Offset)
						return 1;
					else if (region1Offset == region2Offset)
						return 0;
					else
						return -1;
				}
			};
			Collections.sort(regions, comparator);
			Object[] sortedObjects= regions.toArray();
			IRegion[] sortedRegions= new Region[sortedObjects.length];
			System.arraycopy(sortedObjects, 0, sortedRegions, 0, sortedObjects.length);
			return sortedRegions;
		}

		/**
		 * Calculates the region of the element. The start is at beginning of its first line if from
		 * the source start to the beginning of the line is all whitespace.
		 * 
		 * @param element the element whose regions is to be calculated
		 * @param document the document containing the element whose region is to be calculated
		 * @return the region for the element
		 */
		private Region getElementRegion(Object element, IDocument document) {
			try {
				ISourceRange sourceRange= ((ISourceReference)element).getSourceRange();
				int sourceOffset= sourceRange.getOffset();
				int beginningOfWSOffset= sourceOffset - 1;
				int lineAtSourceOffset= document.getLineOfOffset(sourceOffset);
				while (beginningOfWSOffset >= 0 && Character.isWhitespace(document.getChar(beginningOfWSOffset)) && lineAtSourceOffset == document.getLineOfOffset(beginningOfWSOffset)) {
					beginningOfWSOffset--;
				}
				beginningOfWSOffset++;
				int sourceLength= sourceRange.getLength() + (sourceOffset - beginningOfWSOffset);
				if (lineAtSourceOffset != document.getLineOfOffset(beginningOfWSOffset))
					return new Region(document.getLineOffset(lineAtSourceOffset), sourceLength);
				else
					return new Region(beginningOfWSOffset, sourceLength);
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
			} catch (BadLocationException e) {
				JavaPlugin.log(e);
			}
			return null;
		}

		/**
		 * Checks if element has an enclosing parent among other selected elements.
		 * 
		 * @param element the element to be checked for overlap against all elements
		 * @param allElements the list of all elements
		 * @return <code>true</code> if the element has a parent in the list of all elements
		 */
		private boolean isElementOverlapping(IJavaElement element, List<?> allElements) {
			element= element.getParent();
			while (element != null) {
				if (element instanceof ISourceReference) {
					if (allElements.contains(element))
						return true;
				} else {
					return false;
				}
				element= element.getParent();
			}
			return false;
		}

		/**
		 * Notifies the action of a change in the Selection.
		 * 
		 * @param selection the new Structured Selection
		 */
		@Override
		public void selectionChanged(IStructuredSelection selection) {
			setEnabled(fEditor.isEditorInputModifiable());
		}
	}



	/** A flag to show contents of top level type only */
	private boolean fTopLevelTypeOnly;

	private IJavaElement fInput;
	private String fContextMenuID;
	private Menu fMenu;
	private JavaOutlineViewer fOutlineViewer;
	private JavaEditor fEditor;

	private MemberFilterActionGroup fMemberFilterActionGroup;

	private ListenerList fSelectionChangedListeners= new ListenerList(ListenerList.IDENTITY);
	private ListenerList fPostSelectionChangedListeners= new ListenerList(ListenerList.IDENTITY);
	private Hashtable<String, IAction> fActions= new Hashtable<String, IAction>();

	private TogglePresentationAction fTogglePresentation;

	private ToggleLinkingAction fToggleLinkingAction;

	/**
	 * Action for Collapse All.
	 * 
	 * @since 3.7
	 */
	private CollapseAllAction fCollapseAllAction;

	/**
	 * Action for Format Element
	 * 
	 * @since 3.7
	 */
	private FormatElementAction fFormatElement;

	private CompositeActionGroup fActionGroups;

	private IPropertyChangeListener fPropertyChangeListener;
	/**
	 * Custom filter action group.
	 * @since 3.0
	 */
	private CustomFiltersActionGroup fCustomFiltersActionGroup;
	/**
	 * Category filter action group.
	 * @since 3.2
	 */
	private CategoryFilterActionGroup fCategoryFilterActionGroup;

	private JdtViewerDropSupport fDropSupport;

	/**
	 * Helper to open and activate editors.
	 * @since 3.5
	 */
	private OpenAndLinkWithEditorHelper fOpenAndLinkWithEditorHelper;


	public JavaOutlinePage(String contextMenuID, JavaEditor editor) {
		super();

		Assert.isNotNull(editor);

		fContextMenuID= contextMenuID;
		fEditor= editor;

		fTogglePresentation= new TogglePresentationAction();
		fTogglePresentation.setEditor(editor);

		fPropertyChangeListener= new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				doPropertyChange(event);
			}
		};
		JavaPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(fPropertyChangeListener);
	}

	/*
	 * @see org.eclipse.ui.part.Page#init(org.eclipse.ui.part.IPageSite)
	 */
	@Override
	public void init(IPageSite pageSite) {
		super.init(pageSite);
	}

	private void doPropertyChange(PropertyChangeEvent event) {
		if (fOutlineViewer != null) {
			if (MembersOrderPreferenceCache.isMemberOrderProperty(event.getProperty())) {
				fOutlineViewer.refresh(false);
			}
		}
	}

	/*
	 * @see ISelectionProvider#addSelectionChangedListener(ISelectionChangedListener)
	 */
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		if (fOutlineViewer != null)
			fOutlineViewer.addSelectionChangedListener(listener);
		else
			fSelectionChangedListeners.add(listener);
	}

	/*
	 * @see ISelectionProvider#removeSelectionChangedListener(ISelectionChangedListener)
	 */
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		if (fOutlineViewer != null)
			fOutlineViewer.removeSelectionChangedListener(listener);
		else
			fSelectionChangedListeners.remove(listener);
	}

	/*
	 * @see ISelectionProvider#setSelection(ISelection)
	 */
	public void setSelection(ISelection selection) {
		if (fOutlineViewer != null)
			fOutlineViewer.setSelection(selection);
	}

	/*
	 * @see ISelectionProvider#getSelection()
	 */
	public ISelection getSelection() {
		if (fOutlineViewer == null)
			return StructuredSelection.EMPTY;
		return fOutlineViewer.getSelection();
	}

	/*
	 * @see org.eclipse.jface.text.IPostSelectionProvider#addPostSelectionChangedListener(org.eclipse.jface.viewers.ISelectionChangedListener)
	 */
	public void addPostSelectionChangedListener(ISelectionChangedListener listener) {
		if (fOutlineViewer != null)
			fOutlineViewer.addPostSelectionChangedListener(listener);
		else
			fPostSelectionChangedListeners.add(listener);
	}

	/*
	 * @see org.eclipse.jface.text.IPostSelectionProvider#removePostSelectionChangedListener(org.eclipse.jface.viewers.ISelectionChangedListener)
	 */
	public void removePostSelectionChangedListener(ISelectionChangedListener listener) {
		if (fOutlineViewer != null)
			fOutlineViewer.removePostSelectionChangedListener(listener);
		else
			fPostSelectionChangedListeners.remove(listener);
	}

	private void registerToolbarActions(IActionBars actionBars) {
		IToolBarManager toolBarManager= actionBars.getToolBarManager();

		fCollapseAllAction= new CollapseAllAction(fOutlineViewer);
		fCollapseAllAction.setActionDefinitionId(CollapseAllHandler.COMMAND_ID);
		toolBarManager.add(fCollapseAllAction);

		toolBarManager.add(new LexicalSortingAction());

		fMemberFilterActionGroup= new MemberFilterActionGroup(fOutlineViewer, "org.eclipse.jdt.ui.JavaOutlinePage"); //$NON-NLS-1$
		fMemberFilterActionGroup.contributeToToolBar(toolBarManager);

		fCustomFiltersActionGroup.fillActionBars(actionBars);

		IMenuManager viewMenuManager= actionBars.getMenuManager();
		viewMenuManager.add(new Separator("EndFilterGroup")); //$NON-NLS-1$

		fToggleLinkingAction= new ToggleLinkingAction();
		fToggleLinkingAction.setActionDefinitionId(IWorkbenchCommandConstants.NAVIGATE_TOGGLE_LINK_WITH_EDITOR);
		viewMenuManager.add(new ClassOnlyAction());
		viewMenuManager.add(fToggleLinkingAction);

		fCategoryFilterActionGroup= new CategoryFilterActionGroup(fOutlineViewer, "org.eclipse.jdt.ui.JavaOutlinePage", new IJavaElement[] {fInput}); //$NON-NLS-1$
		fCategoryFilterActionGroup.contributeToViewMenu(viewMenuManager);
	}

	/*
	 * @see IPage#createControl
	 */
	@Override
	public void createControl(Composite parent) {

		Tree tree= new Tree(parent, SWT.MULTI);

		AppearanceAwareLabelProvider lprovider= new AppearanceAwareLabelProvider(
			AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS |  JavaElementLabels.F_APP_TYPE_SIGNATURE | JavaElementLabels.ALL_CATEGORY,
			AppearanceAwareLabelProvider.DEFAULT_IMAGEFLAGS
		);

		fOutlineViewer= new JavaOutlineViewer(tree);
		initDragAndDrop();
		fOutlineViewer.setContentProvider(new ChildrenProvider());
		fOutlineViewer.setLabelProvider(new DecoratingJavaLabelProvider(lprovider));

		Object[] listeners= fSelectionChangedListeners.getListeners();
		for (int i= 0; i < listeners.length; i++) {
			fSelectionChangedListeners.remove(listeners[i]);
			fOutlineViewer.addSelectionChangedListener((ISelectionChangedListener) listeners[i]);
		}

		listeners= fPostSelectionChangedListeners.getListeners();
		for (int i= 0; i < listeners.length; i++) {
			fPostSelectionChangedListeners.remove(listeners[i]);
			fOutlineViewer.addPostSelectionChangedListener((ISelectionChangedListener) listeners[i]);
		}

		MenuManager manager= new MenuManager(fContextMenuID, fContextMenuID);
		manager.setRemoveAllWhenShown(true);
		manager.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager m) {
				contextMenuAboutToShow(m);
			}
		});
		fMenu= manager.createContextMenu(tree);
		tree.setMenu(fMenu);

		IPageSite site= getSite();
		site.registerContextMenu(JavaPlugin.getPluginId() + ".outline", manager, fOutlineViewer); //$NON-NLS-1$

		updateSelectionProvider(site);

		// we must create the groups after we have set the selection provider to the site
		fActionGroups= new CompositeActionGroup(new ActionGroup[] {
				new OpenViewActionGroup(this),
				new CCPActionGroup(this),
				new GenerateActionGroup(this),
				new RefactorActionGroup(this),
				new JavaSearchActionGroup(this)});

		// register global actions
		IActionBars actionBars= site.getActionBars();
		actionBars.setGlobalActionHandler(ITextEditorActionConstants.UNDO, fEditor.getAction(ITextEditorActionConstants.UNDO));
		actionBars.setGlobalActionHandler(ITextEditorActionConstants.REDO, fEditor.getAction(ITextEditorActionConstants.REDO));

		IAction action= fEditor.getAction(ITextEditorActionConstants.NEXT);
		actionBars.setGlobalActionHandler(ITextEditorActionDefinitionIds.GOTO_NEXT_ANNOTATION, action);
		actionBars.setGlobalActionHandler(ITextEditorActionConstants.NEXT, action);
		action= fEditor.getAction(ITextEditorActionConstants.PREVIOUS);
		actionBars.setGlobalActionHandler(ITextEditorActionDefinitionIds.GOTO_PREVIOUS_ANNOTATION, action);
		actionBars.setGlobalActionHandler(ITextEditorActionConstants.PREVIOUS, action);

		actionBars.setGlobalActionHandler(ITextEditorActionDefinitionIds.TOGGLE_SHOW_SELECTED_ELEMENT_ONLY, fTogglePresentation);

		fActionGroups.fillActionBars(actionBars);

		fFormatElement= new FormatElementAction(site);
		fFormatElement.setActionDefinitionId(IJavaEditorActionDefinitionIds.QUICK_FORMAT);
		site.getSelectionProvider().addSelectionChangedListener(fFormatElement);
		actionBars.setGlobalActionHandler(JdtActionConstants.FORMAT_ELEMENT, fFormatElement);

		IStatusLineManager statusLineManager= actionBars.getStatusLineManager();
		if (statusLineManager != null) {
			StatusBarUpdater updater= new StatusBarUpdater(statusLineManager);
			fOutlineViewer.addPostSelectionChangedListener(updater);
		}
		// Custom filter group
		fCustomFiltersActionGroup= new CustomFiltersActionGroup("org.eclipse.jdt.ui.JavaOutlinePage", fOutlineViewer); //$NON-NLS-1$

		fOpenAndLinkWithEditorHelper= new OpenAndLinkWithEditorHelper(fOutlineViewer) {

			@Override
			protected void activate(ISelection selection) {
				fEditor.doSelectionChanged(selection);
				getSite().getPage().activate(fEditor);
			}

			@Override
			protected void linkToEditor(ISelection selection) {
				fEditor.doSelectionChanged(selection);

			}

			@Override
			protected void open(ISelection selection, boolean activate) {
				fEditor.doSelectionChanged(selection);
				if (activate)
					getSite().getPage().activate(fEditor);
			}

		};

		registerToolbarActions(actionBars);

		IHandlerService handlerService= (IHandlerService)site.getService(IHandlerService.class);
		handlerService.activateHandler(IWorkbenchCommandConstants.NAVIGATE_TOGGLE_LINK_WITH_EDITOR, new ActionHandler(fToggleLinkingAction));
		handlerService.activateHandler(CollapseAllHandler.COMMAND_ID, new ActionHandler(fCollapseAllAction));


		fOutlineViewer.setInput(fInput);
	}

	/*
	 * @since 3.2
	 */
	private void updateSelectionProvider(IPageSite site) {
		ISelectionProvider provider= fOutlineViewer;
		if (fInput != null) {
			ICompilationUnit cu= (ICompilationUnit)fInput.getAncestor(IJavaElement.COMPILATION_UNIT);
			if (cu != null && !JavaModelUtil.isPrimary(cu))
				provider= new EmptySelectionProvider();
		}
		site.setSelectionProvider(provider);
	}

	@Override
	public void dispose() {

		if (fEditor == null)
			return;

		if (fMemberFilterActionGroup != null) {
			fMemberFilterActionGroup.dispose();
			fMemberFilterActionGroup= null;
		}

		if (fCategoryFilterActionGroup != null) {
			fCategoryFilterActionGroup.dispose();
			fCategoryFilterActionGroup= null;
		}

		if (fCustomFiltersActionGroup != null) {
			fCustomFiltersActionGroup.dispose();
			fCustomFiltersActionGroup= null;
		}


		fEditor.outlinePageClosed();
		fEditor= null;

		fSelectionChangedListeners.clear();
		fSelectionChangedListeners= null;
		getSite().getSelectionProvider().removeSelectionChangedListener(fFormatElement);

		fPostSelectionChangedListeners.clear();
		fPostSelectionChangedListeners= null;

		if (fPropertyChangeListener != null) {
			JavaPlugin.getDefault().getPreferenceStore().removePropertyChangeListener(fPropertyChangeListener);
			fPropertyChangeListener= null;
		}

		if (fMenu != null && !fMenu.isDisposed()) {
			fMenu.dispose();
			fMenu= null;
		}

		if (fActionGroups != null)
			fActionGroups.dispose();

		fTogglePresentation.setEditor(null);

		fOutlineViewer= null;

		super.dispose();
	}

	@Override
	public Control getControl() {
		if (fOutlineViewer != null)
			return fOutlineViewer.getControl();
		return null;
	}

	public void setInput(IJavaElement inputElement) {
		fInput= inputElement;
		if (fOutlineViewer != null) {
			fOutlineViewer.setInput(fInput);
			updateSelectionProvider(getSite());
		}
		if (fCategoryFilterActionGroup != null)
			fCategoryFilterActionGroup.setInput(new IJavaElement[] {fInput});
	}

	public void select(ISourceReference reference) {
		if (fOutlineViewer != null) {

			ISelection s= fOutlineViewer.getSelection();
			if (s instanceof IStructuredSelection) {
				IStructuredSelection ss= (IStructuredSelection) s;
				List<?> elements= ss.toList();
				if (!elements.contains(reference)) {
					s= (reference == null ? StructuredSelection.EMPTY : new StructuredSelection(reference));
					fOutlineViewer.setSelection(s, true);
				}
			}
		}
	}

	public void setAction(String actionID, IAction action) {
		Assert.isNotNull(actionID);
		if (action == null)
			fActions.remove(actionID);
		else
			fActions.put(actionID, action);
	}

	public IAction getAction(String actionID) {
		Assert.isNotNull(actionID);
		return fActions.get(actionID);
	}

	/*
	 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class key) {
		if (key == IShowInSource.class) {
			return getShowInSource();
		}
		if (key == IShowInTargetList.class) {
			return new IShowInTargetList() {
				public String[] getShowInTargetIds() {
					return new String[] { JavaUI.ID_PACKAGES };
				}

			};
		}
		if (key == IShowInTarget.class) {
			return getShowInTarget();
		}

		return null;
	}

	/**
	 * Convenience method to add the action installed under the given actionID to the
	 * specified group of the menu.
	 *
	 * @param menu		the menu manager
	 * @param group		the group to which to add the action
	 * @param actionID	the ID of the new action
	 */
	protected void addAction(IMenuManager menu, String group, String actionID) {
		IAction action= getAction(actionID);
		if (action != null) {
			if (action instanceof IUpdate)
				((IUpdate) action).update();

			if (action.isEnabled()) {
		 		IMenuManager subMenu= menu.findMenuUsingPath(group);
		 		if (subMenu != null)
		 			subMenu.add(action);
		 		else
		 			menu.appendToGroup(group, action);
			}
		}
	}

	protected void contextMenuAboutToShow(IMenuManager menu) {

		JavaPlugin.createStandardGroups(menu);

		IStructuredSelection selection= (IStructuredSelection)getSelection();
		fActionGroups.setContext(new ActionContext(selection));
		fActionGroups.fillContextMenu(menu);
	}

	/*
	 * @see Page#setFocus()
	 */
	@Override
	public void setFocus() {
		if (fOutlineViewer != null)
			fOutlineViewer.getControl().setFocus();
	}

	/**
	 * Checks whether a given Java element is an inner type.
	 *
	 * @param element the java element
	 * @return <code>true</code> iff the given element is an inner type
	 */
	private boolean isInnerType(IJavaElement element) {

		if (element != null && element.getElementType() == IJavaElement.TYPE) {
			IType type= (IType)element;
			try {
				return type.isMember();
			} catch (JavaModelException e) {
				IJavaElement parent= type.getParent();
				if (parent != null) {
					int parentElementType= parent.getElementType();
					return (parentElementType != IJavaElement.COMPILATION_UNIT && parentElementType != IJavaElement.CLASS_FILE);
				}
			}
		}

		return false;
	}

	/**
	 * Returns the <code>IShowInSource</code> for this view.
	 *
	 * @return the {@link IShowInSource}
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

	/**
	 * Returns the <code>IShowInTarget</code> for this view.
	 *
	 * @return the {@link IShowInTarget}
	 */
	protected IShowInTarget getShowInTarget() {
		return new IShowInTarget() {
			public boolean show(ShowInContext context) {
				ISelection sel= context.getSelection();
				if (sel instanceof ITextSelection) {
					ITextSelection tsel= (ITextSelection) sel;
					int offset= tsel.getOffset();
					IJavaElement element= fEditor.getElementAt(offset);
					if (element != null) {
						setSelection(new StructuredSelection(element));
						return true;
					}
				} else if (sel instanceof IStructuredSelection) {
					setSelection(sel);
					return true;
				}
				return false;
			}
		};
	}

	private void initDragAndDrop() {
		fDropSupport= new JdtViewerDropSupport(fOutlineViewer);
		fDropSupport.start();

		new JdtViewerDragSupport(fOutlineViewer).start();
	}

	/**
	 * Returns whether only the contents of the top level type is to be shown.
	 *
	 * @return <code>true</code> if only the contents of the top level type is to be shown.
	 * @since 3.3
	 */
	protected final boolean isTopLevelTypeOnly() {
		return fTopLevelTypeOnly;
	}

	/**
	 * Returns the <code>JavaOutlineViewer</code> of this view.
	 *
	 * @return the {@link JavaOutlineViewer}
	 * @since 3.3
	 */
	protected final JavaOutlineViewer getOutlineViewer() {
		return fOutlineViewer;
	}
}

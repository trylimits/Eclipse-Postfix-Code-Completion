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
package org.eclipse.jdt.internal.ui.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.ui.IDecoratorManager;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.keys.KeySequence;
import org.eclipse.ui.keys.SWTKeySupport;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;
import org.eclipse.jdt.internal.corext.util.SuperTypeHierarchyCache;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.OverrideIndicatorLabelDecorator;
import org.eclipse.jdt.ui.ProblemsLabelDecorator;
import org.eclipse.jdt.ui.StandardJavaElementContentProvider;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.JavaUIMessages;
import org.eclipse.jdt.internal.ui.actions.CategoryFilterActionGroup;
import org.eclipse.jdt.internal.ui.typehierarchy.AbstractHierarchyViewerSorter;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.ColoredViewersManager;
import org.eclipse.jdt.internal.ui.viewsupport.ColoringLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.FocusDescriptor;
import org.eclipse.jdt.internal.ui.viewsupport.MemberFilter;

/**
 * Show outline in light-weight control.
 *
 * @since 2.1
 */
public class JavaOutlineInformationControl extends AbstractInformationControl {

	private KeyAdapter fKeyAdapter;
	private OutlineContentProvider fOutlineContentProvider;
	private IJavaElement fInput= null;

	private OutlineSorter fOutlineSorter;

	private OutlineLabelProvider fInnerLabelProvider;

	private boolean fShowOnlyMainType;
	private LexicalSortingAction fLexicalSortingAction;
	private SortByDefiningTypeAction fSortByDefiningTypeAction;
	private ShowOnlyMainTypeAction fShowOnlyMainTypeAction;
	private Map<IType, ITypeHierarchy> fTypeHierarchies= new HashMap<IType, ITypeHierarchy>();

	/**
	 * Category filter action group.
	 * @since 3.2
	 */
	private CategoryFilterActionGroup fCategoryFilterActionGroup;
	private String fPattern;

	private class OutlineLabelProvider extends AppearanceAwareLabelProvider {

		private boolean fShowDefiningType;

		private OutlineLabelProvider() {
			super(AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS |  JavaElementLabels.F_APP_TYPE_SIGNATURE | JavaElementLabels.ALL_CATEGORY | JavaElementLabels.P_COMPRESSED, AppearanceAwareLabelProvider.DEFAULT_IMAGEFLAGS);
		}

		/*
		 * @see ILabelProvider#getText
		 */
		@Override
		public String getText(Object element) {
			// XXX: This method is NOT USED any more since this is an IStyledLabelProvider.
			// Furthermore, we have no idea what fShowDefiningType is supposed to do if inherited members are shown...
			// If this is put into use again, remember that this needs to be considered in setMatcherString(..)!
			String text= super.getText(element);
			if (fShowDefiningType) {
				try {
					IType type= getDefiningType(element);
					if (type != null) {
						StringBuffer buf= new StringBuffer(super.getText(type));
						buf.append(JavaElementLabels.CONCAT_STRING);
						buf.append(text);
						return buf.toString();
					}
				} catch (JavaModelException e) {
					// go with the simple label
				}
			}
			return text;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.viewsupport.JavaUILabelProvider#getForeground(java.lang.Object)
		 */
		@Override
		public Color getForeground(Object element) {
			if (fOutlineContentProvider.isShowingInheritedMembers()) {
				if (element instanceof IJavaElement) {
					IJavaElement je= (IJavaElement)element;
					if (fInput.getElementType() == IJavaElement.CLASS_FILE)
						je= je.getAncestor(IJavaElement.CLASS_FILE);
					else
						je= je.getAncestor(IJavaElement.COMPILATION_UNIT);
					if (fInput.equals(je)) {
						return null;
					}
				}
				return JFaceResources.getColorRegistry().get(ColoredViewersManager.INHERITED_COLOR_NAME);
			}
			return null;
		}

		public void setShowDefiningType(boolean showDefiningType) {
			fShowDefiningType= showDefiningType;
		}

		private IType getDefiningType(Object element) throws JavaModelException {
			int kind= ((IJavaElement) element).getElementType();

			if (kind != IJavaElement.METHOD && kind != IJavaElement.FIELD && kind != IJavaElement.INITIALIZER) {
				return null;
			}
			IType declaringType= ((IMember) element).getDeclaringType();
			if (kind != IJavaElement.METHOD) {
				return declaringType;
			}
			ITypeHierarchy hierarchy= getSuperTypeHierarchy(declaringType);
			if (hierarchy == null) {
				return declaringType;
			}
			IMethod method= (IMethod) element;
			MethodOverrideTester tester= new MethodOverrideTester(declaringType, hierarchy);
			IMethod res= tester.findDeclaringMethod(method, true);
			if (res == null || method.equals(res)) {
				return declaringType;
			}
			return res.getDeclaringType();
		}

		/*
		 * @see ILabelProvider#getImage
		 */
		@Override
		public Image getImage(Object element) {
			if (element.equals(fInitiallySelectedType) || (element instanceof IMember && ((IMember)element).getDeclaringType() == null)) {
				ImageDescriptor desc= fImageLabelProvider.getJavaImageDescriptor((IJavaElement)element, (evaluateImageFlags(element)));
				Image image= JavaPlugin.getImageDescriptorRegistry().get(new FocusDescriptor(desc));
				return decorateImage(image, element);
			}
			return super.getImage(element);
		}
	}


	private class OutlineTreeViewer extends TreeViewer {

		private boolean fIsFiltering= false;

		private OutlineTreeViewer(Tree tree) {
			super(tree);

		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		protected Object[] getFilteredChildren(Object parent) {
			Object[] result = getRawChildren(parent);
			int unfilteredChildren= result.length;
			ViewerFilter[] filters = getFilters();
			if (filters != null) {
				for (int i= 0; i < filters.length; i++)
					result = filters[i].filter(this, parent, result);
			}
			fIsFiltering= unfilteredChildren != result.length;
			return result;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		protected void internalExpandToLevel(Widget node, int level) {
			if (!fIsFiltering && node instanceof TreeItem && getMatcher() == null) {
				TreeItem treeItem= (TreeItem)node;
				if (treeItem.getParentItem() != null && treeItem.getData() instanceof IJavaElement) {
					IJavaElement je= (IJavaElement) treeItem.getData();
					if (je.getElementType() == IJavaElement.IMPORT_CONTAINER || isInnerType(je)) {
						setExpanded(treeItem, false);
						return;
					}
				}
			}
			super.internalExpandToLevel(node, level);
		}

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
	}


	private class OutlineContentProvider extends StandardJavaElementContentProvider {

		private boolean fShowInheritedMembers;

		/**
		 * Creates a new Outline content provider.
		 *
		 * @param showInheritedMembers <code>true</code> iff inherited members are shown
		 */
		private OutlineContentProvider(boolean showInheritedMembers) {
			super(true);
			fShowInheritedMembers= showInheritedMembers;
		}

		public boolean isShowingInheritedMembers() {
			return fShowInheritedMembers;
		}

		public void toggleShowInheritedMembers() {
			Tree tree= getTreeViewer().getTree();

			tree.setRedraw(false);
			fShowInheritedMembers= !fShowInheritedMembers;
			getTreeViewer().refresh();
			getTreeViewer().expandToLevel(2);

			// reveal selection
			Object selectedElement= getSelectedElement();
			if (selectedElement != null)
				getTreeViewer().reveal(selectedElement);
			else
				selectFirstMatch();

			tree.setRedraw(true);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Object[] getChildren(Object element) {
			if (fShowOnlyMainType) {
				if (element instanceof ITypeRoot) {
					element= ((ITypeRoot)element).findPrimaryType();
				}

				if (element == null)
					return NO_CHILDREN;
			}

			if (fShowInheritedMembers && element instanceof IType) {
				IType type= (IType)element;
				if (type.getDeclaringType() == null || type.equals(fInitiallySelectedType)) {
					ITypeHierarchy th= getSuperTypeHierarchy(type);
					if (th != null) {
						List<Object> children= new ArrayList<Object>();
						IType[] superClasses= th.getAllSupertypes(type);
						children.addAll(Arrays.asList(super.getChildren(type)));
						for (int i= 0, scLength= superClasses.length; i < scLength; i++)
							children.addAll(Arrays.asList(super.getChildren(superClasses[i])));
						return children.toArray();
					}
				}
			}
			return super.getChildren(element);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			super.inputChanged(viewer, oldInput, newInput);
			fTypeHierarchies.clear();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void dispose() {
			super.dispose();
			if (fCategoryFilterActionGroup != null) {
				fCategoryFilterActionGroup.dispose();
				fCategoryFilterActionGroup= null;
			}
			fTypeHierarchies.clear();
		}
	}


	private class ShowOnlyMainTypeAction extends Action {

		private static final String STORE_GO_INTO_TOP_LEVEL_TYPE_CHECKED= "GoIntoTopLevelTypeAction.isChecked"; //$NON-NLS-1$

		private TreeViewer fOutlineViewer;

		private ShowOnlyMainTypeAction(TreeViewer outlineViewer) {
			super(TextMessages.JavaOutlineInformationControl_GoIntoTopLevelType_label, IAction.AS_CHECK_BOX);
			setToolTipText(TextMessages.JavaOutlineInformationControl_GoIntoTopLevelType_tooltip);
			setDescription(TextMessages.JavaOutlineInformationControl_GoIntoTopLevelType_description);

			JavaPluginImages.setLocalImageDescriptors(this, "gointo_toplevel_type.gif"); //$NON-NLS-1$

			PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.GO_INTO_TOP_LEVEL_TYPE_ACTION);

			fOutlineViewer= outlineViewer;

			boolean showclass= getDialogSettings().getBoolean(STORE_GO_INTO_TOP_LEVEL_TYPE_CHECKED);
			setTopLevelTypeOnly(showclass);
		}

		/*
		 * @see org.eclipse.jface.action.Action#run()
		 */
		@Override
		public void run() {
			setTopLevelTypeOnly(!fShowOnlyMainType);
		}

		private void setTopLevelTypeOnly(boolean show) {
			fShowOnlyMainType= show;
			setChecked(show);

			Tree tree= fOutlineViewer.getTree();
			tree.setRedraw(false);

			fOutlineViewer.refresh(false);
			if (!fShowOnlyMainType)
				fOutlineViewer.expandToLevel(2);


			// reveal selection
			Object selectedElement= getSelectedElement();
			if (selectedElement != null)
				fOutlineViewer.reveal(selectedElement);

			tree.setRedraw(true);

			getDialogSettings().put(STORE_GO_INTO_TOP_LEVEL_TYPE_CHECKED, show);
		}
	}

	private class OutlineSorter extends AbstractHierarchyViewerSorter {

		/*
		 * @see org.eclipse.jdt.internal.ui.typehierarchy.AbstractHierarchyViewerSorter#getHierarchy(org.eclipse.jdt.core.IType)
		 * @since 3.2
		 */
		@Override
		protected ITypeHierarchy getHierarchy(IType type) {
			return getSuperTypeHierarchy(type);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.typehierarchy.AbstractHierarchyViewerSorter#isSortByDefiningType()
		 * @since 3.2
		 */
		@Override
		public boolean isSortByDefiningType() {
			return fSortByDefiningTypeAction.isChecked();
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.typehierarchy.AbstractHierarchyViewerSorter#isSortAlphabetically()
		 * @since 3.2
		 */
		@Override
		public boolean isSortAlphabetically() {
			return fLexicalSortingAction.isChecked();
		}
	}


	private class LexicalSortingAction extends Action {

		private static final String STORE_LEXICAL_SORTING_CHECKED= "LexicalSortingAction.isChecked"; //$NON-NLS-1$

		private TreeViewer fOutlineViewer;

		private LexicalSortingAction(TreeViewer outlineViewer) {
			super(TextMessages.JavaOutlineInformationControl_LexicalSortingAction_label, IAction.AS_CHECK_BOX);
			setToolTipText(TextMessages.JavaOutlineInformationControl_LexicalSortingAction_tooltip);
			setDescription(TextMessages.JavaOutlineInformationControl_LexicalSortingAction_description);

			JavaPluginImages.setLocalImageDescriptors(this, "alphab_sort_co.gif"); //$NON-NLS-1$

			fOutlineViewer= outlineViewer;

			boolean checked=getDialogSettings().getBoolean(STORE_LEXICAL_SORTING_CHECKED);
			setChecked(checked);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.LEXICAL_SORTING_BROWSING_ACTION);
		}

		@Override
		public void run() {
			valueChanged(isChecked(), true);
		}

		private void valueChanged(final boolean on, boolean store) {
			setChecked(on);
			BusyIndicator.showWhile(fOutlineViewer.getControl().getDisplay(), new Runnable() {
				public void run() {
					fOutlineViewer.refresh(false);
				}
			});

			if (store)
				getDialogSettings().put(STORE_LEXICAL_SORTING_CHECKED, on);
		}
	}


	private class SortByDefiningTypeAction extends Action {

		private static final String STORE_SORT_BY_DEFINING_TYPE_CHECKED= "SortByDefiningType.isChecked"; //$NON-NLS-1$

		private TreeViewer fOutlineViewer;

		/**
		 * Creates the action.
		 *
		 * @param outlineViewer the outline viewer
		 */
		private SortByDefiningTypeAction(TreeViewer outlineViewer) {
			super(TextMessages.JavaOutlineInformationControl_SortByDefiningTypeAction_label);
			setDescription(TextMessages.JavaOutlineInformationControl_SortByDefiningTypeAction_description);
			setToolTipText(TextMessages.JavaOutlineInformationControl_SortByDefiningTypeAction_tooltip);

			JavaPluginImages.setLocalImageDescriptors(this, "definingtype_sort_co.gif"); //$NON-NLS-1$

			fOutlineViewer= outlineViewer;

			PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.SORT_BY_DEFINING_TYPE_ACTION);

			boolean state= getDialogSettings().getBoolean(STORE_SORT_BY_DEFINING_TYPE_CHECKED);
			setChecked(state);
			fInnerLabelProvider.setShowDefiningType(state);
		}

		/*
		 * @see Action#actionPerformed
		 */
		@Override
		public void run() {
			BusyIndicator.showWhile(fOutlineViewer.getControl().getDisplay(), new Runnable() {
				public void run() {
					fInnerLabelProvider.setShowDefiningType(isChecked());
					getDialogSettings().put(STORE_SORT_BY_DEFINING_TYPE_CHECKED, isChecked());

					setMatcherString(fPattern, false);
					fOutlineViewer.refresh(true);

					// reveal selection
					Object selectedElement= getSelectedElement();
					if (selectedElement != null)
						fOutlineViewer.reveal(selectedElement);
				}
			});
		}
	}

	/**
	 * Creates a new Java outline information control.
	 *
	 * @param parent the parent shell
	 * @param shellStyle the additional styles for the shell
	 * @param treeStyle the additional styles for the tree widget
	 * @param commandId the id of the command that invoked this control or <code>null</code>
	 */
	public JavaOutlineInformationControl(Shell parent, int shellStyle, int treeStyle, String commandId) {
		super(parent, shellStyle, treeStyle, commandId, true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Text createFilterText(Composite parent) {
		Text text= super.createFilterText(parent);
		text.addKeyListener(getKeyAdapter());
		return text;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected TreeViewer createTreeViewer(Composite parent, int style) {
		Tree tree= new Tree(parent, SWT.SINGLE | (style & ~SWT.MULTI));
		GridData gd= new GridData(GridData.FILL_BOTH);
		gd.heightHint= tree.getItemHeight() * 12;
		tree.setLayoutData(gd);

		final TreeViewer treeViewer= new OutlineTreeViewer(tree);

		// Hard-coded filters
		treeViewer.addFilter(new NamePatternFilter());
		treeViewer.addFilter(new MemberFilter());

		fInnerLabelProvider= new OutlineLabelProvider();
		fInnerLabelProvider.addLabelDecorator(new ProblemsLabelDecorator(null));
		IDecoratorManager decoratorMgr= PlatformUI.getWorkbench().getDecoratorManager();
		if (decoratorMgr.getEnabled("org.eclipse.jdt.ui.override.decorator")) //$NON-NLS-1$
			fInnerLabelProvider.addLabelDecorator(new OverrideIndicatorLabelDecorator(null));

		treeViewer.setLabelProvider(new ColoringLabelProvider(fInnerLabelProvider));

		fLexicalSortingAction= new LexicalSortingAction(treeViewer);
		fSortByDefiningTypeAction= new SortByDefiningTypeAction(treeViewer);
		fShowOnlyMainTypeAction= new ShowOnlyMainTypeAction(treeViewer);
		fCategoryFilterActionGroup= new CategoryFilterActionGroup(treeViewer, getId(), getInputForCategories());

		fOutlineContentProvider= new OutlineContentProvider(false);
		treeViewer.setContentProvider(fOutlineContentProvider);
		fOutlineSorter= new OutlineSorter();
		treeViewer.setComparator(fOutlineSorter);
		treeViewer.setAutoExpandLevel(AbstractTreeViewer.ALL_LEVELS);


		treeViewer.getTree().addKeyListener(getKeyAdapter());

		return treeViewer;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getStatusFieldText() {
		KeySequence[] sequences= getInvokingCommandKeySequences();
		if (sequences == null || sequences.length == 0)
			return ""; //$NON-NLS-1$

		String keySequence= sequences[0].format();

		if (fOutlineContentProvider.isShowingInheritedMembers())
			return Messages.format(JavaUIMessages.JavaOutlineControl_statusFieldText_hideInheritedMembers, keySequence);
		else
			return Messages.format(JavaUIMessages.JavaOutlineControl_statusFieldText_showInheritedMembers, keySequence);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.AbstractInformationControl#getId()
	 * @since 3.0
	 */
	@Override
	protected String getId() {
		return "org.eclipse.jdt.internal.ui.text.QuickOutline"; //$NON-NLS-1$
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setInput(Object information) {
		if (information == null || information instanceof String) {
			inputChanged(null, null);
			return;
		}
		IJavaElement je= (IJavaElement)information;
		ICompilationUnit cu= (ICompilationUnit)je.getAncestor(IJavaElement.COMPILATION_UNIT);
		if (cu != null)
			fInput= cu;
		else
			fInput= je.getAncestor(IJavaElement.CLASS_FILE);

		inputChanged(fInput, information);

		fCategoryFilterActionGroup.setInput(getInputForCategories());
	}

	private KeyAdapter getKeyAdapter() {
		if (fKeyAdapter == null) {
			fKeyAdapter= new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent e) {
					int accelerator = SWTKeySupport.convertEventToUnmodifiedAccelerator(e);
					KeySequence keySequence = KeySequence.getInstance(SWTKeySupport.convertAcceleratorToKeyStroke(accelerator));
					KeySequence[] sequences= getInvokingCommandKeySequences();
					if (sequences == null)
						return;
					for (int i= 0; i < sequences.length; i++) {
						if (sequences[i].equals(keySequence)) {
							e.doit= false;
							toggleShowInheritedMembers();
							return;
						}
					}
				}
			};
		}
		return fKeyAdapter;
	}

	protected void toggleShowInheritedMembers() {
		long flags= fInnerLabelProvider.getTextFlags();
		flags ^= JavaElementLabels.ALL_POST_QUALIFIED;
		fInnerLabelProvider.setTextFlags(flags);
		fOutlineContentProvider.toggleShowInheritedMembers();
		updateStatusFieldText();
		fCategoryFilterActionGroup.setInput(getInputForCategories());
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.AbstractInformationControl#fillViewMenu(org.eclipse.jface.action.IMenuManager)
	 */
	@Override
	protected void fillViewMenu(IMenuManager viewMenu) {
		super.fillViewMenu(viewMenu);
		viewMenu.add(fShowOnlyMainTypeAction);

		viewMenu.add(new Separator("Sorters")); //$NON-NLS-1$
		viewMenu.add(fLexicalSortingAction);

		viewMenu.add(fSortByDefiningTypeAction);

		fCategoryFilterActionGroup.setInput(getInputForCategories());
		fCategoryFilterActionGroup.contributeToViewMenu(viewMenu);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.AbstractInformationControl#setMatcherString(java.lang.String, boolean)
	 * @since 3.2
	 */
	@Override
	protected void setMatcherString(String pattern, boolean update) {
		fPattern= pattern;
		super.setMatcherString(pattern, update);
	}

	private IJavaElement[] getInputForCategories() {
		if (fInput == null)
			return new IJavaElement[0];

		if (fOutlineContentProvider.isShowingInheritedMembers()) {
			IJavaElement p= fInput;
			if (p instanceof ITypeRoot) {
				p= ((ITypeRoot)p).findPrimaryType();
			}
			while (p != null && !(p instanceof IType)) {
				p= p.getParent();
			}
			if (!(p instanceof IType))
				return new IJavaElement[] {fInput};

			ITypeHierarchy hierarchy= getSuperTypeHierarchy((IType)p);
			if (hierarchy == null)
				return new IJavaElement[] {fInput};

			IType[] supertypes= hierarchy.getAllSupertypes((IType)p);
			IJavaElement[] result= new IJavaElement[supertypes.length + 1];
			result[0]= fInput;
			System.arraycopy(supertypes, 0, result, 1, supertypes.length);
			return result;
		} else {
			return new IJavaElement[] {fInput};
		}
	}

	private ITypeHierarchy getSuperTypeHierarchy(IType type) {
		ITypeHierarchy th= fTypeHierarchies.get(type);
		if (th == null) {
			try {
				th= SuperTypeHierarchyCache.getTypeHierarchy(type, getProgressMonitor());
			} catch (JavaModelException e) {
				return null;
			} catch (OperationCanceledException e) {
				return null;
			}
			fTypeHierarchies.put(type, th);
		}
		return th;
	}

	private IProgressMonitor getProgressMonitor() {
		IWorkbenchPage wbPage= JavaPlugin.getActivePage();
		if (wbPage == null)
			return null;

		IEditorPart editor= wbPage.getActiveEditor();
		if (editor == null)
			return null;

		return editor.getEditorSite().getActionBars().getStatusLineManager().getProgressMonitor();
	}

}

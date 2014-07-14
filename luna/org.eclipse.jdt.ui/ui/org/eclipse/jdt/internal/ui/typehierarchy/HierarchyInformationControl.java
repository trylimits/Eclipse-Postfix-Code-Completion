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
package org.eclipse.jdt.internal.ui.typehierarchy;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;

import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.ui.keys.KeySequence;
import org.eclipse.ui.keys.SWTKeySupport;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.ProblemsLabelDecorator;
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.AbstractInformationControl;
import org.eclipse.jdt.internal.ui.typehierarchy.SuperTypeHierarchyViewer.SuperTypeHierarchyContentProvider;
import org.eclipse.jdt.internal.ui.typehierarchy.TraditionalHierarchyViewer.TraditionalHierarchyContentProvider;
import org.eclipse.jdt.internal.ui.viewsupport.ColoringLabelProvider;

/**
 * Show hierarchy in light-weight control.
 *
 * @since 3.0
 */
public class HierarchyInformationControl extends AbstractInformationControl {

	private TypeHierarchyLifeCycle fLifeCycle;
	private HierarchyLabelProvider fLabelProvider;
	private KeyAdapter fKeyAdapter;

	private Object[] fOtherExpandedElements;
	private TypeHierarchyContentProvider fOtherContentProvider;

	private IMethod fFocus; // method to filter for or null if type hierarchy
	private boolean fDoFilter;

	private MethodOverrideTester fMethodOverrideTester;

	public HierarchyInformationControl(Shell parent, int shellStyle, int treeStyle) {
		super(parent, shellStyle, treeStyle, IJavaEditorActionDefinitionIds.OPEN_HIERARCHY, true);
		fOtherExpandedElements= null;
		fDoFilter= true;
		fMethodOverrideTester= null;
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
							toggleHierarchy();
							return;
						}
					}
				}
			};
		}
		return fKeyAdapter;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean hasHeader() {
		return true;
	}

	@Override
	protected Text createFilterText(Composite parent) {
		// text set later
		Text text= super.createFilterText(parent);
		text.addKeyListener(getKeyAdapter());
		return text;
	}


	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.JavaOutlineInformationControl#createTreeViewer(org.eclipse.swt.widgets.Composite, int)
	 */
	@Override
	protected TreeViewer createTreeViewer(Composite parent, int style) {
		Tree tree= new Tree(parent, SWT.SINGLE | (style & ~SWT.MULTI));
		GridData gd= new GridData(GridData.FILL_BOTH);
		gd.heightHint= tree.getItemHeight() * 12;
		tree.setLayoutData(gd);

		TreeViewer treeViewer= new TreeViewer(tree);
		treeViewer.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				return element instanceof IType;
			}
		});

		fLifeCycle= new TypeHierarchyLifeCycle(false);

		treeViewer.setComparator(new HierarchyViewerSorter(fLifeCycle));
		treeViewer.setAutoExpandLevel(AbstractTreeViewer.ALL_LEVELS);

		fLabelProvider= new HierarchyLabelProvider(fLifeCycle);
		fLabelProvider.setFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				return hasFocusMethod((IType) element);
			}
		});

		fLabelProvider.setTextFlags(JavaElementLabels.ALL_DEFAULT | JavaElementLabels.T_POST_QUALIFIED | JavaElementLabels.P_COMPRESSED);
		fLabelProvider.addLabelDecorator(new ProblemsLabelDecorator(null));
		treeViewer.setLabelProvider(new ColoringLabelProvider(fLabelProvider));

		treeViewer.getTree().addKeyListener(getKeyAdapter());

		return treeViewer;
	}

	protected boolean hasFocusMethod(IType type) {
		if (fFocus == null) {
			return true;
		}
		if (type.equals(fFocus.getDeclaringType())) {
			return true;
		}

		try {
			IMethod method= findMethod(fFocus, type);
			if (method != null) {
				// check visibility
				IPackageFragment pack= (IPackageFragment) fFocus.getAncestor(IJavaElement.PACKAGE_FRAGMENT);
				if (JavaModelUtil.isVisibleInHierarchy(method, pack)) {
					return true;
				}
			}
		} catch (JavaModelException e) {
			// ignore
			JavaPlugin.log(e);
		}
		return false;

	}

	private IMethod findMethod(IMethod filterMethod, IType typeToFindIn) throws JavaModelException {
		IType filterType= filterMethod.getDeclaringType();
		if (filterType.equals(typeToFindIn)) {
			return filterMethod;
		}
		
		ITypeHierarchy hierarchy= fLifeCycle.getHierarchy();

		boolean filterOverrides= JavaModelUtil.isSuperType(hierarchy, typeToFindIn, filterType);
		IType focusType= filterOverrides ? filterType : typeToFindIn;

		if (fMethodOverrideTester == null || !fMethodOverrideTester.getFocusType().equals(focusType)) {
			fMethodOverrideTester= new MethodOverrideTester(focusType, hierarchy);
		}

		if (filterOverrides) {
			return fMethodOverrideTester.findOverriddenMethodInType(typeToFindIn, filterMethod);
		} else {
			return fMethodOverrideTester.findOverridingMethodInType(typeToFindIn, filterMethod);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setInput(Object information) {
		if (!(information instanceof IJavaElement)) {
			inputChanged(null, null);
			return;
		}
		IJavaElement input= null;
		IMethod locked= null;
		try {
			IJavaElement elem= (IJavaElement) information;
			switch (elem.getElementType()) {
				case IJavaElement.LOCAL_VARIABLE :
				case IJavaElement.TYPE_PARAMETER :
					elem= elem.getParent();
			}

			switch (elem.getElementType()) {
				case IJavaElement.JAVA_PROJECT :
				case IJavaElement.PACKAGE_FRAGMENT_ROOT :
				case IJavaElement.PACKAGE_FRAGMENT :
				case IJavaElement.TYPE :
					input= elem;
					break;
				case IJavaElement.COMPILATION_UNIT :
					input= ((ICompilationUnit) elem).findPrimaryType();
					break;
				case IJavaElement.CLASS_FILE :
					input= ((IClassFile) elem).getType();
					break;
				case IJavaElement.METHOD :
					IMethod method= (IMethod) elem;
					if (!method.isConstructor()) {
						locked= method;
					}
					input= method.getDeclaringType();
					break;
				case IJavaElement.FIELD :
				case IJavaElement.INITIALIZER :
					input= ((IMember) elem).getDeclaringType();
					break;
				case IJavaElement.PACKAGE_DECLARATION :
					input= elem.getParent().getParent();
					break;
				case IJavaElement.IMPORT_DECLARATION :
					IImportDeclaration decl= (IImportDeclaration) elem;
					if (decl.isOnDemand()) {
						input= JavaModelUtil.findTypeContainer(decl.getJavaProject(), Signature.getQualifier(decl.getElementName()));
					} else {
						input= decl.getJavaProject().findType(decl.getElementName());
					}
					break;
				default :
					JavaPlugin.logErrorMessage("Element unsupported by the hierarchy: " + elem.getClass()); //$NON-NLS-1$
					// input is null;
			}
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		}

		super.setTitleText(getHeaderLabel(locked == null ? input : locked));
		try {
			fLifeCycle.ensureRefreshedTypeHierarchy(input, JavaPlugin.getActiveWorkbenchWindow());
		} catch (InvocationTargetException e1) {
			input= null;
		} catch (InterruptedException e1) {
			dispose();
			return;
		}
		IMember[] memberFilter= locked != null ? new IMember[] { locked } : null;

		TraditionalHierarchyContentProvider contentProvider= new TraditionalHierarchyContentProvider(fLifeCycle);
		contentProvider.setMemberFilter(memberFilter);
		getTreeViewer().setContentProvider(contentProvider);

		fOtherContentProvider= new SuperTypeHierarchyContentProvider(fLifeCycle);
		fOtherContentProvider.setMemberFilter(memberFilter);

		fFocus= locked;

		Object[] topLevelObjects= contentProvider.getElements(fLifeCycle);
		if (topLevelObjects.length > 0 && contentProvider.getChildren(topLevelObjects[0]).length > 40) {
			fDoFilter= false;
		} else {
			getTreeViewer().addFilter(new NamePatternFilter());
		}

		Object selection= null;
		if (input instanceof IMember) {
			selection=  input;
		} else if (topLevelObjects.length > 0) {
			selection=  topLevelObjects[0];
		}
		inputChanged(fLifeCycle, selection);
	}

	@Override
	protected void stringMatcherUpdated() {
		if (fDoFilter) {
			super.stringMatcherUpdated(); // refresh the view
		} else {
			selectFirstMatch();
		}
	}

	protected void toggleHierarchy() {
		TreeViewer treeViewer= getTreeViewer();

		treeViewer.getTree().setRedraw(false);

		Object[] expandedElements= treeViewer.getExpandedElements();
		TypeHierarchyContentProvider contentProvider= (TypeHierarchyContentProvider) treeViewer.getContentProvider();
		treeViewer.setContentProvider(fOtherContentProvider);

		treeViewer.refresh();
		if (fOtherExpandedElements != null) {
			treeViewer.setExpandedElements(fOtherExpandedElements);
		} else {
			treeViewer.expandAll();
		}

		// reveal selection
		Object selectedElement= getSelectedElement();
		if (selectedElement != null)
			getTreeViewer().reveal(selectedElement);
		else
			selectFirstMatch();

		treeViewer.getTree().setRedraw(true);

		fOtherContentProvider= contentProvider;
		fOtherExpandedElements= expandedElements;

		updateStatusFieldText();
	}


	private String getHeaderLabel(IJavaElement input) {
		if (input instanceof IMethod) {
			String[] args= { JavaElementLabels.getElementLabel(input.getParent(), JavaElementLabels.ALL_DEFAULT), JavaElementLabels.getElementLabel(input, JavaElementLabels.ALL_DEFAULT) };
			return Messages.format(TypeHierarchyMessages.HierarchyInformationControl_methodhierarchy_label, args);
		} else if (input != null) {
			String arg= JavaElementLabels.getElementLabel(input, JavaElementLabels.DEFAULT_QUALIFIED);
			return Messages.format(TypeHierarchyMessages.HierarchyInformationControl_hierarchy_label, arg);
		} else {
			return ""; //$NON-NLS-1$
		}
	}

	@Override
	protected String getStatusFieldText() {
		KeySequence[] sequences= getInvokingCommandKeySequences();
		String keyName= ""; //$NON-NLS-1$
		if (sequences != null && sequences.length > 0)
			keyName= sequences[0].format();

		if (fOtherContentProvider instanceof TraditionalHierarchyContentProvider) {
			return Messages.format(TypeHierarchyMessages.HierarchyInformationControl_toggle_traditionalhierarchy_label, keyName);
		} else {
			return Messages.format(TypeHierarchyMessages.HierarchyInformationControl_toggle_superhierarchy_label, keyName);
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.AbstractInformationControl#getId()
	 */
	@Override
	protected String getId() {
		return "org.eclipse.jdt.internal.ui.typehierarchy.QuickHierarchy"; //$NON-NLS-1$
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Object getSelectedElement() {
		Object selectedElement= super.getSelectedElement();
		if (selectedElement instanceof IType && fFocus != null) {
			IType type= (IType) selectedElement;
			try {
				IMethod method= findMethod(fFocus, type);
				if (method != null) {
					return method;
				}
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
			}
		}
		return selectedElement;
	}
}

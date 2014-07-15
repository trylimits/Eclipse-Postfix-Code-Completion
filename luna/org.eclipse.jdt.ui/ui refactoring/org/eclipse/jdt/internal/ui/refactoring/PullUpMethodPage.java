/*******************************************************************************
 * Copyright (c) 2006, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.SourceViewer;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ContainerCheckedTreeViewer;

import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.structure.PullUpRefactoringProcessor;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.Strings;

import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.SWTUtil;

/**
 * Wizard page for pull up refactoring wizards which allows to specify the
 * methods to be deleted in subtypes after pull up.
 *
 * @since 3.2
 */
public class PullUpMethodPage extends UserInputWizardPage {

	private static class PullUpFilter extends ViewerFilter {

		private static boolean anySubtypeCanBeShown(final IType type, final Map<IType, IMember[]> typeToMemberArray, final ITypeHierarchy hierarchy) {
			final IType[] subTypes= hierarchy.getSubtypes(type);
			for (int i= 0; i < subTypes.length; i++) {
				if (canBeShown(subTypes[i], typeToMemberArray, hierarchy))
					return true;
			}
			return false;
		}

		private static boolean canBeShown(final IType type, final Map<IType, IMember[]> typeToMemberArray, final ITypeHierarchy hierarchy) {
			if (typeToMemberArray.containsKey(type))
				return true;
			return anySubtypeCanBeShown(type, typeToMemberArray, hierarchy);
		}

		private static Set<IType> computeShowableSubtypesOfMainType(final ITypeHierarchy hierarchy, final Map<IType, IMember[]> typeToMemberArray) {
			final Set<IType> result= new HashSet<IType>();
			final IType[] subtypes= hierarchy.getAllSubtypes(hierarchy.getType());
			for (int i= 0; i < subtypes.length; i++) {
				final IType subtype= subtypes[i];
				if (canBeShown(subtype, typeToMemberArray, hierarchy))
					result.add(subtype);
			}
			return result;
		}

		private static Set<IType> computeTypesToShow(final ITypeHierarchy hierarchy, final Map<IType, IMember[]> typeToMemberArray) {
			final Set<IType> typesToShow= new HashSet<IType>();
			typesToShow.add(hierarchy.getType());
			typesToShow.addAll(computeShowableSubtypesOfMainType(hierarchy, typeToMemberArray));
			return typesToShow;
		}

		private final Set<IType> fTypesToShow;

		public PullUpFilter(final ITypeHierarchy hierarchy, final IMember[] members) {
			// IType -> IMember[]
			final Map<IType, IMember[]> map= PullUpMethodPage.createTypeToMemberArrayMapping(members);
			fTypesToShow= computeTypesToShow(hierarchy, map);
		}

		@Override
		public boolean select(final Viewer viewer, final Object parentElement, final Object element) {
			if (element instanceof IMethod)
				return true;
			return fTypesToShow.contains(element);
		}
	}

	private static class PullUpHierarchyContentProvider implements ITreeContentProvider {

		private IType fDeclaringType;

		private ITypeHierarchy fHierarchy;

		private Map<IType, IMember[]> fTypeToMemberArray; // IType -> IMember[]

		public PullUpHierarchyContentProvider(final IType declaringType, final IMember[] members) {
			fDeclaringType= declaringType;
			fTypeToMemberArray= PullUpMethodPage.createTypeToMemberArrayMapping(members);
		}

		public void dispose() {
			fHierarchy= null;
			fTypeToMemberArray.clear();
			fTypeToMemberArray= null;
			fDeclaringType= null;
		}

		public Object[] getChildren(final Object parentElement) {
			if (parentElement instanceof IType)
				return getSubclassesAndMembers((IType) parentElement);
			else
				return new Object[0];
		}

		public Object[] getElements(final Object inputElement) {
			Assert.isTrue(inputElement == null || inputElement instanceof ITypeHierarchy);
			return new IType[] { fHierarchy.getType()};
		}

		private IMember[] getMembers(final IType type) {
			if (fTypeToMemberArray.containsKey(type))
				return (fTypeToMemberArray.get(type));
			else
				return new IMember[0];
		}

		public Object getParent(final Object element) {
			if (element instanceof IType)
				return fHierarchy.getSuperclass((IType) element);
			if (element instanceof IMember)
				return ((IMember) element).getDeclaringType();
			Assert.isTrue(false, "Should not get here"); //$NON-NLS-1$
			return null;
		}

		private IType[] getSubclasses(final IType type) {
			if (type.equals(fDeclaringType))
				return new IType[0];
			return fHierarchy.getSubclasses(type);
		}

		private Object[] getSubclassesAndMembers(final IType type) {
			final Set<IMember> set= new HashSet<IMember>();
			set.addAll(Arrays.asList(getSubclasses(type)));
			set.addAll(Arrays.asList(getMembers(type)));
			return set.toArray();
		}

		public boolean hasChildren(final Object element) {
			if (!(element instanceof IType))
				return false;
			final IType type= (IType) element;
			return (fHierarchy.getAllSubtypes(type).length > 0) || fTypeToMemberArray.containsKey(type);
		}

		public void inputChanged(final Viewer viewer, final Object oldInput, final Object newInput) {
			Assert.isTrue(newInput == null || newInput instanceof ITypeHierarchy);
			fHierarchy= (ITypeHierarchy) newInput;
		}
	}

	private static final String PAGE_NAME= "PullUpMethodPage"; //$NON-NLS-1$

	// IType -> IMember[]
	private static Map<IType, IMember[]> createTypeToMemberArrayMapping(final IMember[] members) {
		final Map<IType, HashSet<IMember>> typeToMemberSet= createTypeToMemberSetMapping(members);

		final Map<IType, IMember[]> typeToMemberArray= new HashMap<IType, IMember[]>();
		for (final Iterator<IType> iter= typeToMemberSet.keySet().iterator(); iter.hasNext();) {
			final IType type= iter.next();
			final Set<IMember> memberSet= typeToMemberSet.get(type);
			final IMember[] memberArray= memberSet.toArray(new IMember[memberSet.size()]);
			typeToMemberArray.put(type, memberArray);
		}
		return typeToMemberArray;
	}

	// IType -> Set of IMember
	private static Map<IType, HashSet<IMember>> createTypeToMemberSetMapping(final IMember[] members) {
		final Map<IType, HashSet<IMember>> typeToMemberSet= new HashMap<IType, HashSet<IMember>>();
		for (int i= 0; i < members.length; i++) {
			final IMember member= members[i];
			final IType type= member.getDeclaringType();
			if (!typeToMemberSet.containsKey(type))
				typeToMemberSet.put(type, new HashSet<IMember>());
			typeToMemberSet.get(type).add(member);
		}
		return typeToMemberSet;
	}

	private boolean fChangedSettings= true;

	private Label fSelectionLabel;

	private SourceViewer fSourceViewer;

	private ContainerCheckedTreeViewer fTreeViewer;

	private Label fTypeHierarchyLabel;

	private final PullUpRefactoringProcessor fProcessor;

	public PullUpMethodPage(PullUpRefactoringProcessor processor) {
		super(PAGE_NAME);
		fProcessor= processor;
		setMessage(RefactoringMessages.PullUpInputPage_select_methods);
	}

	protected PullUpRefactoringProcessor getPullUpRefactoringProcessor() {
		return fProcessor;
	}

	private void checkAllParents(final IType parent) {
		final ITypeHierarchy th= getTreeInput();
		final IType root= getTreeInput().getType();
		IType type= parent;
		while (!root.equals(type)) {
			fTreeViewer.setChecked(type, true);
			type= th.getSuperclass(type);
		}
		fTreeViewer.setChecked(root, true);
	}

	public void checkPulledUp() {
		uncheckAll();
		fTreeViewer.setCheckedElements(fProcessor.getMembersToMove());
		final IType parent= fProcessor.getDeclaringType();
		fTreeViewer.setChecked(parent, true);
		checkAllParents(parent);
	}

	private void createButtonComposite(final Composite superComposite) {
		final Composite buttonComposite= new Composite(superComposite, SWT.NONE);
		buttonComposite.setLayoutData(new GridData(GridData.FILL, GridData.BEGINNING, true, false));
		final GridLayout layout= new GridLayout(2, false);
		layout.marginWidth= 0;
		buttonComposite.setLayout(layout);

		fSelectionLabel= new Label(buttonComposite, SWT.LEFT | SWT.WRAP | SWT.HORIZONTAL);
		GridData data= new GridData(GridData.BEGINNING, GridData.BEGINNING, true, false);
		data.widthHint= convertWidthInCharsToPixels(32);
		fSelectionLabel.setLayoutData(data);

		final Button button= new Button(buttonComposite, SWT.PUSH);
		button.setText(RefactoringMessages.PullUpInputPage2_Select);
		button.setLayoutData(new GridData());
		SWTUtil.setButtonDimensionHint(button);
		button.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(final SelectionEvent e) {
				checkPulledUp();
				updateSelectionLabel();
			}
		});
	}

	public void createControl(final Composite parent) {
		final Composite composite= new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout());

		createTreeAndSourceViewer(composite);
		createButtonComposite(composite);
		setControl(composite);

		Dialog.applyDialogFont(composite);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IJavaHelpContextIds.PULL_UP_WIZARD_PAGE);
	}

	private void createHierarchyTreeComposite(final Composite parent) {
		final Composite composite= new Composite(parent, SWT.NONE);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		final GridLayout layout= new GridLayout();
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		layout.horizontalSpacing= 1;
		layout.verticalSpacing= 1;
		composite.setLayout(layout);

		createTypeHierarchyLabel(composite);
		createTreeViewer(composite);
	}

	private void createSourceViewer(final Composite c) {
		final IPreferenceStore store= JavaPlugin.getDefault().getCombinedPreferenceStore();
		fSourceViewer= new JavaSourceViewer(c, null, null, false, SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION, store);
		fSourceViewer.configure(new JavaSourceViewerConfiguration(JavaPlugin.getDefault().getJavaTextTools().getColorManager(), store, null, null));
		fSourceViewer.setEditable(false);
		fSourceViewer.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
		fSourceViewer.getControl().setFont(JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT));
	}

	private void createSourceViewerComposite(final Composite parent) {
		final Composite c= new Composite(parent, SWT.NONE);
		c.setLayoutData(new GridData(GridData.FILL_BOTH));
		final GridLayout layout= new GridLayout();
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		layout.horizontalSpacing= 1;
		layout.verticalSpacing= 1;
		c.setLayout(layout);

		createSourceViewerLabel(c);
		createSourceViewer(c);
	}

	private void createSourceViewerLabel(final Composite c) {
		final Label label= new Label(c, SWT.WRAP);
		final GridData gd= new GridData(GridData.FILL_HORIZONTAL);
		label.setText(RefactoringMessages.PullUpInputPage2_Source);
		label.setLayoutData(gd);
	}

	private void createTreeAndSourceViewer(final Composite superComposite) {
		final SashForm composite= new SashForm(superComposite, SWT.HORIZONTAL);
		initializeDialogUnits(superComposite);
		final GridData gd= new GridData(GridData.FILL_BOTH);
		gd.heightHint= convertHeightInCharsToPixels(20);
		gd.widthHint= convertWidthInCharsToPixels(10);
		composite.setLayoutData(gd);
		final GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		layout.horizontalSpacing= 1;
		layout.verticalSpacing= 1;
		composite.setLayout(layout);

		createHierarchyTreeComposite(composite);
		createSourceViewerComposite(composite);
		composite.setWeights(new int[] { 50, 50});
	}

	private void createTreeViewer(final Composite composite) {
		final Tree tree= new Tree(composite, SWT.CHECK | SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		tree.setLayoutData(new GridData(GridData.FILL_BOTH));
		fTreeViewer= new ContainerCheckedTreeViewer(tree);
		fTreeViewer.setLabelProvider(new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT | JavaElementLabelProvider.SHOW_SMALL_ICONS));
		fTreeViewer.setUseHashlookup(true);
		fTreeViewer.setComparator(new JavaElementComparator());
		fTreeViewer.addSelectionChangedListener(new ISelectionChangedListener() {

			public void selectionChanged(final SelectionChangedEvent event) {
				treeViewerSelectionChanged(event);
			}
		});
		fTreeViewer.addCheckStateListener(new ICheckStateListener() {

			public void checkStateChanged(final CheckStateChangedEvent event) {
				updateSelectionLabel();
			}
		});
	}

	private void createTypeHierarchyLabel(final Composite composite) {
		fTypeHierarchyLabel= new Label(composite, SWT.WRAP);
		final GridData gd= new GridData(GridData.FILL_HORIZONTAL);
		fTypeHierarchyLabel.setLayoutData(gd);
	}

	public void fireSettingsChanged() {
		fChangedSettings= true;
	}

	private IMethod[] getCheckedMethods() {
		final Object[] checked= fTreeViewer.getCheckedElements();
		final List<IMethod> members= new ArrayList<IMethod>(checked.length);
		for (int i= 0; i < checked.length; i++) {
			if (checked[i] instanceof IMethod)
				members.add((IMethod) checked[i]);
		}
		return members.toArray(new IMethod[members.size()]);
	}

	private ISourceReference getFirstSelectedSourceReference(final SelectionChangedEvent event) {
		final ISelection s= event.getSelection();
		if (!(s instanceof IStructuredSelection))
			return null;
		final IStructuredSelection ss= (IStructuredSelection) s;
		if (ss.size() != 1)
			return null;
		final Object first= ss.getFirstElement();
		if (!(first instanceof ISourceReference))
			return null;
		return (ISourceReference) first;
	}

	@Override
	public IWizardPage getNextPage() {
		initializeRefactoring();
		return super.getNextPage();
	}

	private String getSupertypeSignature() {
		return JavaElementUtil.createSignature(fProcessor.getDestinationType());
	}

	private ITypeHierarchy getTreeInput() {
		return (ITypeHierarchy) fTreeViewer.getInput();
	}

	private void initializeRefactoring() {
		fProcessor.setDeletedMethods(getCheckedMethods());
	}

	private void initializeTreeViewer() {
		try {
			getContainer().run(false, false, new IRunnableWithProgress() {

				public void run(final IProgressMonitor pm) {
					try {
						initializeTreeViewer(pm);
					} finally {
						pm.done();
					}
				}
			});
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(), RefactoringMessages.PullUpInputPage_pull_Up, RefactoringMessages.PullUpInputPage_exception);
		} catch (InterruptedException e) {
			Assert.isTrue(false);
		}
	}

	private void initializeTreeViewer(final IProgressMonitor pm) {
		try {
			pm.beginTask(RefactoringCoreMessages.PullUpRefactoring_checking, 2);
			final IMember[] matchingMethods= fProcessor.getMatchingElements(new SubProgressMonitor(pm, 1), false);
			final ITypeHierarchy hierarchy= fProcessor.getDestinationTypeHierarchy(new SubProgressMonitor(pm, 1));
			removeAllTreeViewFilters();
			fTreeViewer.addFilter(new PullUpFilter(hierarchy, matchingMethods));
			fTreeViewer.setContentProvider(new PullUpHierarchyContentProvider(fProcessor.getDeclaringType(), matchingMethods));
			fTreeViewer.setInput(hierarchy);
			precheckElements(fTreeViewer);
			fTreeViewer.expandAll();
			updateSelectionLabel();
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.PullUpInputPage_pull_up1, RefactoringMessages.PullUpInputPage_exception);
			fTreeViewer.setInput(null);
		} finally {
			pm.done();
		}
	}

	@Override
	protected boolean performFinish() {
		initializeRefactoring();
		return super.performFinish();
	}

	private void precheckElements(final ContainerCheckedTreeViewer treeViewer) {
		final IMember[] members= fProcessor.getMembersToMove();
		for (int i= 0; i < members.length; i++) {
			treeViewer.setChecked(members[i], true);
		}
	}

	private void removeAllTreeViewFilters() {
		final ViewerFilter[] filters= fTreeViewer.getFilters();
		for (int i= 0; i < filters.length; i++) {
			fTreeViewer.removeFilter(filters[i]);
		}
	}

	private void setHierarchyLabelText() {
		final String message= Messages.format(RefactoringMessages.PullUpInputPage_subtypes, getSupertypeSignature());
		fTypeHierarchyLabel.setText(message);
	}

	private void setSourceViewerContents(String contents) {
		if (contents != null) {
			final IJavaProject project= fProcessor.getDestinationType().getJavaProject();
			final String[] lines= Strings.convertIntoLines(contents);
			if (lines.length > 0) {
				final int indent= Strings.computeIndentUnits(lines[lines.length - 1], project);
				contents= Strings.changeIndent(contents, indent, project, "", "\n"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		final IDocument document= (contents == null) ? new Document() : new Document(contents);
		JavaPlugin.getDefault().getJavaTextTools().setupJavaDocumentPartitioner(document);
		fSourceViewer.setDocument(document);
	}

	@Override
	public void setVisible(final boolean visible) {
		if (visible && fChangedSettings) {
			fChangedSettings= false;
			initializeTreeViewer();
			setHierarchyLabelText();
		}
		super.setVisible(visible);
	}

	private void showInSourceViewer(final ISourceReference selected) throws JavaModelException {
		if (selected == null)
			setSourceViewerContents(null);
		else
			setSourceViewerContents(selected.getSource());
	}

	private void treeViewerSelectionChanged(final SelectionChangedEvent event) {
		try {
			showInSourceViewer(getFirstSelectedSourceReference(event));
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.PullUpInputPage_pull_up1, RefactoringMessages.PullUpInputPage_see_log);
		}
	}

	private void uncheckAll() {
		final IType root= getTreeInput().getType();
		fTreeViewer.setChecked(root, false);
	}

	private void updateSelectionLabel() {
		IMethod[] methods= getCheckedMethods();
		int checkedMethodsCount= methods.length;
		String text= checkedMethodsCount == 1 ? Messages.format(RefactoringMessages.PullUpInputPage_hierarchyLabal_singular, JavaElementLabels.getElementLabel(methods[0],
				JavaElementLabels.M_PARAMETER_TYPES)) : Messages.format(RefactoringMessages.PullUpInputPage_hierarchyLabal_plural, String.valueOf(checkedMethodsCount));
		fSelectionLabel.setText(text);

	}
}
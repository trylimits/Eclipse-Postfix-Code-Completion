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
package org.eclipse.jdt.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ListDialog;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.InvalidInputException;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSElement;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSLine;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSScanner;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgUtils;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.refactoring.nls.ExternalizeWizard;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;


/**
 * Externalizes the strings of a compilation unit or find all strings
 * in a package or project that are not externalized yet. Opens a wizard that
 * gathers additional information to externalize the strings.
 * <p>
 * The action is applicable to structured selections containing elements
 * of type <code>ICompilationUnit</code>, <code>IType</code>, <code>IJavaProject</code>,
 * <code>IPackageFragment</code>, and <code>IPackageFragmentRoot</code>
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class ExternalizeStringsAction extends SelectionDispatchAction {

	private CompilationUnitEditor fEditor;

	private NonNLSElement[] fElements;

	/**
	 * Creates a new <code>ExternalizeStringsAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public ExternalizeStringsAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.ExternalizeStringsAction_label);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.EXTERNALIZE_STRINGS_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the compilation unit editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public ExternalizeStringsAction(CompilationUnitEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(fEditor != null && SelectionConverter.canOperateOn(fEditor));
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isExternalizeStringsAvailable(selection));
		} catch (JavaModelException e) {
			if (JavaModelUtil.isExceptionToBeLogged(e))
				JavaPlugin.log(e);
			setEnabled(false);//no UI - happens on selection changes
		}
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(ITextSelection selection) {
		IJavaElement element= SelectionConverter.getInput(fEditor);
		if (!(element instanceof ICompilationUnit))
			return;
		run((ICompilationUnit)element);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(IStructuredSelection selection) {
		ICompilationUnit unit= getCompilationUnit(selection);
		if (unit != null) {//run on cu
			run(unit);
		} else {
			//run on multiple
			try {
				PlatformUI.getWorkbench().getProgressService().run(true, true, createRunnable(selection));
			} catch(InvocationTargetException e) {
				ExceptionHandler.handle(e, getShell(),
						ActionMessages.ExternalizeStringsAction_dialog_title,
						ActionMessages.FindStringsToExternalizeAction_error_message);
				return;
			} catch(InterruptedException e) {
				// OK
				return;
			}
			showResults();
		}
	}

	/**
	 * Note: this method is for internal use only. Clients should not call this method.
	 * @param unit the compilation unit
	 *
	 * @noreference This method is not intended to be referenced by clients.
	 */
	public void run(ICompilationUnit unit) {
		if (!ActionUtil.isEditable(fEditor, getShell(), unit))
			return;

		ExternalizeWizard.open(unit, getShell());
	}


	private static ICompilationUnit getCompilationUnit(IStructuredSelection selection) {
		if (selection.isEmpty() || selection.size() != 1)
			return null;
		Object first= selection.getFirstElement();
		if (first instanceof ICompilationUnit)
			return (ICompilationUnit) first;
		if (first instanceof IType)
			return ((IType) first).getCompilationUnit();
		return null;
	}

	private IRunnableWithProgress createRunnable(final IStructuredSelection selection) {
		return new IRunnableWithProgress() {
			public void run(IProgressMonitor pm) throws InvocationTargetException {
				try {
					fElements= doRun(selection, pm);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				}
			}
		};
	}

	private NonNLSElement[] doRun(IStructuredSelection selection, IProgressMonitor pm) throws CoreException {
		List<?> elements= getSelectedElementList(selection);
		if (elements == null || elements.isEmpty())
			return new NonNLSElement[0];

		pm.beginTask(ActionMessages.FindStringsToExternalizeAction_find_strings, elements.size());

		try{
			List<NonNLSElement> result= new ArrayList<NonNLSElement>();
			for (Iterator<?> iter= elements.iterator(); iter.hasNext();) {
				Object obj= iter.next();
				result.addAll(analyze(obj, pm));
			}
			return result.toArray(new NonNLSElement[result.size()]);
		} finally{
			pm.done();
		}
	}

	private List<NonNLSElement> analyze(Object obj, IProgressMonitor pm) throws CoreException, JavaModelException {
		if (obj instanceof IJavaElement) {
			IJavaElement element= (IJavaElement) obj;
			int elementType= element.getElementType();

			if (elementType == IJavaElement.PACKAGE_FRAGMENT) {
				return analyze((IPackageFragment) element, new SubProgressMonitor(pm, 1));
			} else if (elementType == IJavaElement.PACKAGE_FRAGMENT_ROOT) {
				IPackageFragmentRoot root= (IPackageFragmentRoot)element;
				if (!root.isExternal() && !ReorgUtils.isClassFolder(root)) {
					return analyze((IPackageFragmentRoot) element, new SubProgressMonitor(pm, 1));
				} else {
					pm.worked(1);
				}
			} else if (elementType == IJavaElement.JAVA_PROJECT) {
				return analyze((IJavaProject) element, new SubProgressMonitor(pm, 1));
			} else if (elementType == IJavaElement.COMPILATION_UNIT) {
				ICompilationUnit cu= (ICompilationUnit)element;
				if (cu.exists()) {
					NonNLSElement nlsElement= analyze(cu);
					if (nlsElement != null) {
						pm.worked(1);
						ArrayList<NonNLSElement> result= new ArrayList<NonNLSElement>();
						result.add(nlsElement);
						return result;
					}
				}
				pm.worked(1);
			} else if (elementType == IJavaElement.TYPE) {
				IType type= (IType)element;
				ICompilationUnit cu= type.getCompilationUnit();
				if (cu != null && cu.exists()) {
					NonNLSElement nlsElement= analyze(cu);
					if (nlsElement != null) {
						pm.worked(1);
						ArrayList<NonNLSElement> result= new ArrayList<NonNLSElement>();
						result.add(nlsElement);
						return result;
					}
				}
				pm.worked(1);
			} else {
				pm.worked(1);
			}
		} else if (obj instanceof IWorkingSet) {
			List<NonNLSElement> result= new ArrayList<NonNLSElement>();

			IWorkingSet workingSet= (IWorkingSet) obj;
			IAdaptable[] elements= workingSet.getElements();
			for (int i= 0; i < elements.length; i++) {
				result.addAll(analyze(elements[i], new NullProgressMonitor()));
			}
			pm.worked(1);
			return result;
		} else {
			pm.worked(1);
		}

		return Collections.emptyList();
	}

	private void showResults() {
		if (noStrings())
			MessageDialog.openInformation(getShell(), ActionMessages.ExternalizeStringsAction_dialog_title, ActionMessages.FindStringsToExternalizeAction_noStrings);
		else
			new NonNLSListDialog(getShell(), fElements, countStrings()).open();
	}

	private boolean noStrings() {
		if (fElements != null) {
			for (int i= 0; i < fElements.length; i++) {
				if (fElements[i].count != 0)
					return false;
			}
		}
		return true;
	}

	/*
	 * returns List of Strings
	 */
	private List<NonNLSElement> analyze(IPackageFragment pack, IProgressMonitor pm) throws CoreException {
		try{
			if (pack == null)
				return new ArrayList<NonNLSElement>(0);

			ICompilationUnit[] cus= pack.getCompilationUnits();

			pm.beginTask("", cus.length); //$NON-NLS-1$
			pm.setTaskName(pack.getElementName());

			List<NonNLSElement> l= new ArrayList<NonNLSElement>(cus.length);
			for (int i= 0; i < cus.length; i++){
				pm.subTask(BasicElementLabels.getFileName(cus[i]));
				NonNLSElement element= analyze(cus[i]);
				if (element != null)
					l.add(element);
				pm.worked(1);
				if (pm.isCanceled())
					throw new OperationCanceledException();
			}
			return l;
		} finally {
			pm.done();
		}
	}

	/*
	 * returns List of Strings
	 */
	private List<NonNLSElement> analyze(IPackageFragmentRoot sourceFolder, IProgressMonitor pm) throws CoreException {
		try{
			IJavaElement[] children= sourceFolder.getChildren();
			pm.beginTask("", children.length); //$NON-NLS-1$
			pm.setTaskName(JavaElementLabels.getElementLabel(sourceFolder, JavaElementLabels.ALL_DEFAULT));
			List<NonNLSElement> result= new ArrayList<NonNLSElement>();
			for (int i= 0; i < children.length; i++) {
				IJavaElement iJavaElement= children[i];
				if (iJavaElement.getElementType() == IJavaElement.PACKAGE_FRAGMENT){
					IPackageFragment pack= (IPackageFragment)iJavaElement;
					if (! pack.isReadOnly())
						result.addAll(analyze(pack, new SubProgressMonitor(pm, 1)));
					else
						pm.worked(1);
				} else
					pm.worked(1);
			}
			return result;
		} finally{
			pm.done();
		}
	}

	/*
	 * returns List of Strings
	 */
	private List<NonNLSElement> analyze(IJavaProject project, IProgressMonitor pm) throws CoreException {
		try{
			IPackageFragment[] packs= project.getPackageFragments();
			pm.beginTask("", packs.length); //$NON-NLS-1$
			List<NonNLSElement> result= new ArrayList<NonNLSElement>();
			for (int i= 0; i < packs.length; i++) {
				if (! packs[i].isReadOnly())
					result.addAll(analyze(packs[i], new SubProgressMonitor(pm, 1)));
				else
					pm.worked(1);
			}
			return result;
		} finally{
			pm.done();
		}
	}

	private int countStrings() {
		int found= 0;
		if (fElements != null) {
			for (int i= 0; i < fElements.length; i++)
				found+= fElements[i].count;
		}
		return found;
	}

	private NonNLSElement analyze(ICompilationUnit cu) throws CoreException {
		int count= countNonExternalizedStrings(cu);
		if (count == 0)
			return null;
		else
			return new NonNLSElement(cu, count);
	}

	private int countNonExternalizedStrings(ICompilationUnit cu) throws CoreException {
		try{
			NLSLine[] lines= NLSScanner.scan(cu);
			int result= 0;
			for (int i= 0; i < lines.length; i++) {
				result += countNonExternalizedStrings(lines[i]);
			}
			return result;
		} catch (InvalidInputException e) {
			throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.ERROR,
					Messages.format(ActionMessages.FindStringsToExternalizeAction_error_cannotBeParsed, BasicElementLabels.getFileName(cu)), e));
		} catch (BadLocationException e) {
			throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.ERROR, Messages.format(ActionMessages.FindStringsToExternalizeAction_error_cannotBeParsed,
					BasicElementLabels.getFileName(cu)), e));
		}
	}

	private int countNonExternalizedStrings(NLSLine line){
		int result= 0;
		NLSElement[] elements= line.getElements();
		for (int i= 0; i < elements.length; i++){
			if (! elements[i].hasTag())
				result++;
		}
		return result;
	}

	/**
	 * returns <code>List</code> of <code>IPackageFragments</code>,  <code>IPackageFragmentRoots</code> or
	 * <code>IJavaProjects</code> (all entries are of the same kind)
	 * @param selection the selection
	 * @return the selected elements
	 */
	private static List<?> getSelectedElementList(IStructuredSelection selection) {
		if (selection == null)
			return null;

		return selection.toList();
	}

	//-------private classes --------------

	private static class NonNLSListDialog extends ListDialog {

		private static final int OPEN_BUTTON_ID= IDialogConstants.CLIENT_ID + 1;

		private Button fOpenButton;

		NonNLSListDialog(Shell parent, NonNLSElement[] input, int count) {
			super(parent);
			setAddCancelButton(false);
			setInput(Arrays.asList(input));
			setTitle(ActionMessages.ExternalizeStringsAction_dialog_title);
			String message= count == 1 ? ActionMessages.FindStringsToExternalizeAction_non_externalized_singular : Messages.format(
					ActionMessages.FindStringsToExternalizeAction_non_externalized_plural, new Object[] { new Integer(count) });
			setMessage(message);
			setContentProvider(new ArrayContentProvider());
			setLabelProvider(createLabelProvider());
		}

		@Override
		protected Point getInitialSize() {
			return getShell().computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite result= (Composite)super.createDialogArea(parent);
			getTableViewer().addSelectionChangedListener(new ISelectionChangedListener(){
				public void selectionChanged(SelectionChangedEvent event){
					if (fOpenButton != null){
						fOpenButton.setEnabled(! getTableViewer().getSelection().isEmpty());
					}
				}
			});
			getTableViewer().getTable().addSelectionListener(new SelectionAdapter(){
				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
					NonNLSElement element= (NonNLSElement)e.item.getData();
					ExternalizeWizard.open(element.cu, getShell());
				}
			});
			getTableViewer().getTable().setFocus();
			applyDialogFont(result);
			return result;
		}

		@Override
		protected void createButtonsForButtonBar(Composite parent) {
			fOpenButton= createButton(parent, OPEN_BUTTON_ID, ActionMessages.FindStringsToExternalizeAction_button_label, true);
			fOpenButton.setEnabled(false);

			//looks like a 'close' but it a 'cancel'
			createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CLOSE_LABEL, false);
		}

		@Override
		protected void buttonPressed(int buttonId) {
			if (buttonId != OPEN_BUTTON_ID){
				super.buttonPressed(buttonId);
				return;
			}
			ISelection s= getTableViewer().getSelection();
			if (s instanceof IStructuredSelection){
				IStructuredSelection ss= (IStructuredSelection)s;
				if (ss.getFirstElement() instanceof NonNLSElement)
					ExternalizeWizard.open(((NonNLSElement) ss.getFirstElement()).cu, getShell());
			}
		}

		private static LabelProvider createLabelProvider() {
			return new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT){
				@Override
				public String getText(Object element) {
					NonNLSElement nlsel= (NonNLSElement)element;
					String elementName= BasicElementLabels.getPathLabel(nlsel.cu.getResource().getFullPath(), false);
					return Messages.format(
							ActionMessages.FindStringsToExternalizeAction_foundStrings,
							new Object[] {new Integer(nlsel.count), elementName} );
				}
				@Override
				public Image getImage(Object element) {
					return super.getImage(((NonNLSElement)element).cu);
				}
			};
		}

		/*
		 * @see org.eclipse.jface.window.Window#configureShell(Shell)
		 */
		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell, IJavaHelpContextIds.NONNLS_DIALOG);
		}


	}

	private static class NonNLSElement{
		ICompilationUnit cu;
		int count;
		NonNLSElement(ICompilationUnit cu, int count){
			this.cu= cu;
			this.count= count;
		}
	}

}

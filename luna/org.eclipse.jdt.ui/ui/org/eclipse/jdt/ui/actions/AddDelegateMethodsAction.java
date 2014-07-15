/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Martin Moebius (m.moebius@gmx.de) - initial API and implementation
 *             (report 28793)
 *   IBM Corporation - updates
 *******************************************************************************/

package org.eclipse.jdt.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.Window;

import org.eclipse.jface.text.IRewriteTarget;
import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.eclipse.jdt.internal.corext.codemanipulation.AddDelegateMethodsOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.AddDelegateMethodsOperation.DelegateEntry;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.template.java.CodeTemplateContextType;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.SharedASTProvider;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.dialogs.SourceActionDialog;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.ui.util.ElementValidator;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.BindingLabelProvider;

/**
 * Creates delegate methods for a type's fields. Opens a dialog with a list of fields for
 * which delegate methods can be generated. User is able to check or uncheck items before
 * methods are generated.
 * <p>
 * Will open the parent compilation unit in a Java editor. The result is unsaved, so the
 * user can decide if the changes are acceptable.
 * <p>
 * The action is applicable to structured selections containing elements of type
 * <code>IField</code> or <code>IType</code>.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.1
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class AddDelegateMethodsAction extends SelectionDispatchAction {

	// ---- Helpers -------------------------------------------------------------------

	private static class AddDelegateMethodsActionStatusValidator implements ISelectionStatusValidator {

		private static int fEntries;

		AddDelegateMethodsActionStatusValidator(int entries) {
			fEntries= entries;
		}

		public IStatus validate(Object[] selection) {
			int selectedCount= 0;
			int duplicateCount= 0;
			if (selection != null && selection.length > 0) {

				HashSet<String> signatures= new HashSet<String>(selection.length);
				for (int index= 0; index < selection.length; index++) {
					if (selection[index] instanceof DelegateEntry) {
						DelegateEntry delegateEntry= (DelegateEntry) selection[index];
						if (!signatures.add(getSignature(delegateEntry.delegateMethod)))
							duplicateCount++;
						selectedCount++;
					}
				}
			}
			if (duplicateCount > 0) {
				return new StatusInfo(IStatus.ERROR, duplicateCount == 1
						? ActionMessages.AddDelegateMethodsAction_duplicate_methods_singular
						: Messages.format(ActionMessages.AddDelegateMethodsAction_duplicate_methods_plural, String.valueOf(duplicateCount)));
			}
			return new StatusInfo(IStatus.INFO, Messages.format(ActionMessages.AddDelegateMethodsAction_selectioninfo_more, new Object[] { String.valueOf(selectedCount), String.valueOf(fEntries) }));
		}

		private String getSignature(IMethodBinding binding) {
			StringBuffer buf= new StringBuffer(binding.getName()).append('(');
			ITypeBinding[] parameterTypes= binding.getParameterTypes();
			for (int i= 0; i < parameterTypes.length; i++) {
				buf.append(parameterTypes[i].getTypeDeclaration().getName());
			}
			buf.append(')');
			return buf.toString();
		}

	}

	private static class AddDelegateMethodsContentProvider implements ITreeContentProvider {

		private DelegateEntry[] fDelegateEntries;
		private IVariableBinding[] fExpanded= new IVariableBinding[0];

		AddDelegateMethodsContentProvider(CompilationUnit astRoot, IType type, IField[] fields) throws JavaModelException {

			final ITypeBinding binding= ASTNodes.getTypeBinding(astRoot, type);
			if (binding != null) {
				fDelegateEntries= StubUtility2.getDelegatableMethods(binding);

				List<IVariableBinding> expanded= new ArrayList<IVariableBinding>();
				for (int index= 0; index < fields.length; index++) {
					VariableDeclarationFragment fragment= ASTNodeSearchUtil.getFieldDeclarationFragmentNode(fields[index], astRoot);
					if (fragment != null) {
						IVariableBinding variableBinding= fragment.resolveBinding();
						if (variableBinding != null)
							expanded.add(variableBinding);
					}
				}
				fExpanded= expanded.toArray(new IVariableBinding[expanded.size()]);
			}
		}

		public void dispose() {
		}

		public Object[] getChildren(Object element) {
			if (element instanceof IVariableBinding) {
				List<DelegateEntry> result= new ArrayList<DelegateEntry>();
				for (int i= 0; i < fDelegateEntries.length; i++) {
					if (element == fDelegateEntries[i].field) {
						result.add(fDelegateEntries[i]);
					}
				}
				return result.toArray();
			}
			return null;
		}

		public int getCount() {
			return fDelegateEntries.length;
		}

		public Object[] getElements(Object inputElement) {
			HashSet<IVariableBinding> result= new HashSet<IVariableBinding>();
			for (int i= 0; i < fDelegateEntries.length; i++) {
				DelegateEntry curr= fDelegateEntries[i];
				result.add(curr.field);
			}
			return result.toArray();
		}

		public IVariableBinding[] getExpandedElements() {
			return fExpanded;
		}

		public IVariableBinding[] getInitiallySelectedElements() {
			return fExpanded;
		}

		public Object getParent(Object element) {
			if (element instanceof DelegateEntry)
				return ((DelegateEntry) element).field;
			return null;
		}

		public boolean hasChildren(Object element) {
			return element instanceof IVariableBinding;
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}
	}

	private static class AddDelegateMethodsDialog extends SourceActionDialog {

		public AddDelegateMethodsDialog(Shell parent, ILabelProvider labelProvider, ITreeContentProvider contentProvider, CompilationUnitEditor editor, IType type, boolean isConstructor) throws JavaModelException {
			super(parent, labelProvider, contentProvider, editor, type, isConstructor);
		}

		@Override
		protected void configureShell(Shell shell) {
			super.configureShell(shell);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(shell, IJavaHelpContextIds.ADD_DELEGATE_METHODS_SELECTION_DIALOG);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.dialogs.SourceActionDialog#createLinkControl(org.eclipse.swt.widgets.Composite)
		 */
		@Override
		protected Control createLinkControl(Composite composite) {
			Link link= new Link(composite, SWT.WRAP);
			link.setText(ActionMessages.AddDelegateMethodsAction_template_link_message);
			link.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					openCodeTempatePage(CodeTemplateContextType.OVERRIDECOMMENT_ID);
				}
			});
			link.setToolTipText(ActionMessages.AddDelegateMethodsAction_template_link_tooltip);

			GridData gridData= new GridData(SWT.FILL, SWT.BEGINNING, true, false);
			gridData.widthHint= convertWidthInCharsToPixels(40); // only expand further if anyone else requires it
			link.setLayoutData(gridData);
			return link;
		}
	}

	private static class AddDelegateMethodsLabelProvider extends BindingLabelProvider {

		@Override
		public Image getImage(Object element) {
			if (element instanceof DelegateEntry) {
				DelegateEntry delegateEntry= (DelegateEntry) element;
				return super.getImage(delegateEntry.delegateMethod);
			} else if (element instanceof IVariableBinding) {
				return super.getImage(element);
			}
			return null;
		}

		@Override
		public String getText(Object element) {
			if (element instanceof DelegateEntry) {
				DelegateEntry delegateEntry= (DelegateEntry) element;
				return super.getText(delegateEntry.delegateMethod);
			} else if (element instanceof IVariableBinding) {
				return super.getText(element);
			}
			return null;
		}
	}

	private static class AddDelegateMethodsViewerComparator extends ViewerComparator {

		@Override
		public int category(Object element) {
			if (element instanceof DelegateEntry)
				return 0;
			return 1;
		}

		@Override
		public int compare(Viewer viewer, Object o1, Object o2) {
			if (o1 instanceof DelegateEntry && o2 instanceof DelegateEntry) {
				String bindingLabel1= BindingLabelProvider.getBindingLabel(((DelegateEntry) o1).delegateMethod, BindingLabelProvider.DEFAULT_TEXTFLAGS);
				String bindingLabel2= BindingLabelProvider.getBindingLabel(((DelegateEntry) o2).delegateMethod, BindingLabelProvider.DEFAULT_TEXTFLAGS);
				return getComparator().compare(bindingLabel1, bindingLabel2);
			} else if (o1 instanceof IVariableBinding && o2 instanceof IVariableBinding) {
				return getComparator().compare(((IVariableBinding) o1).getName(), ((IVariableBinding) o2).getName());
			}
			return 0;
		}
	}

	private static final String DIALOG_TITLE= ActionMessages.AddDelegateMethodsAction_error_title;

	private static boolean hasPrimitiveType(IField field) throws JavaModelException {
		String signature= field.getTypeSignature();
		char first= Signature.getElementType(signature).charAt(0);
		return (first != Signature.C_RESOLVED && first != Signature.C_UNRESOLVED);
	}

	private static boolean isArray(IField field) throws JavaModelException {
		return Signature.getArrayCount(field.getTypeSignature()) > 0;
	}

	private CompilationUnitEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this
	 * constructor.
	 *
	 * @param editor the compilation unit editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public AddDelegateMethodsAction(CompilationUnitEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.getInputAsCompilationUnit(editor) != null);
	}

	/**
	 * Creates a new <code>AddDelegateMethodsAction</code>. The action requires that
	 * the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public AddDelegateMethodsAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.AddDelegateMethodsAction_label);
		setDescription(ActionMessages.AddDelegateMethodsAction_description);
		setToolTipText(ActionMessages.AddDelegateMethodsAction_tooltip);

		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.ADD_DELEGATE_METHODS_ACTION);
	}

	private boolean canEnable(IStructuredSelection selection) throws JavaModelException {
		if (getSelectedFields(selection) != null)
			return true;

		if ((selection.size() == 1) && (selection.getFirstElement() instanceof IType)) {
			IType type= (IType) selection.getFirstElement();
			return type.getCompilationUnit() != null && !type.isInterface() && !type.isAnonymous();
		}

		if ((selection.size() == 1) && (selection.getFirstElement() instanceof ICompilationUnit))
			return true;

		return false;
	}

	private boolean canRunOn(IField[] fields) throws JavaModelException {
		if (fields == null || fields.length == 0)
			return false;
		int count= 0;
		for (int index= 0; index < fields.length; index++) {
			if (!JdtFlags.isEnum(fields[index]) && !hasPrimitiveType(fields[index]) || isArray(fields[index]))
				count++;
		}
		if (count == 0)
			MessageDialog.openInformation(getShell(), DIALOG_TITLE, ActionMessages.AddDelegateMethodsAction_not_applicable);
		return (count > 0);
	}

	private boolean canRunOn(IType type) throws JavaModelException {
		if (type == null || type.getCompilationUnit() == null) {
			MessageDialog.openInformation(getShell(), DIALOG_TITLE, ActionMessages.AddDelegateMethodsAction_not_in_source_file);
			return false;
		} else if (type.isAnnotation()) {
			MessageDialog.openInformation(getShell(), DIALOG_TITLE, ActionMessages.AddDelegateMethodsAction_annotation_not_applicable);
			return false;
		} else if (type.isInterface()) {
			MessageDialog.openInformation(getShell(), DIALOG_TITLE, ActionMessages.AddDelegateMethodsAction_interface_not_applicable);
			return false;
		}
		return canRunOn(type.getFields());
	}

	private IField[] getSelectedFields(IStructuredSelection selection) {
		List<?> elements= selection.toList();
		if (elements.size() > 0) {
			IField[] result= new IField[elements.size()];
			ICompilationUnit unit= null;
			for (int index= 0; index < elements.size(); index++) {
				if (elements.get(index) instanceof IField) {
					IField field= (IField) elements.get(index);

					if (index == 0) {
						// remember the CU of the first element
						unit= field.getCompilationUnit();
						if (unit == null) {
							return null;
						}
					} else if (!unit.equals(field.getCompilationUnit())) {
						// all fields must be in the same CU
						return null;
					}
					try {
						final IType type= field.getDeclaringType();
						if (type.isInterface() || type.isAnonymous()) {
							return null;
						}
					} catch (JavaModelException exception) {
						JavaPlugin.log(exception);
						return null;
					}

					result[index]= field;
				} else {
					return null;
				}
			}
			return result;
		}
		return null;
	}

	/*
	 * (non-Javadoc) Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(IStructuredSelection selection) {
		try {
			IField[] selectedFields= getSelectedFields(selection);
			if (canRunOn(selectedFields)) {
				run(selectedFields[0].getDeclaringType(), selectedFields, false);
				return;
			}
			Object firstElement= selection.getFirstElement();
			if (firstElement instanceof IType)
				run((IType) firstElement, new IField[0], false);
			else if (firstElement instanceof ICompilationUnit)
				run(JavaElementUtil.getMainType((ICompilationUnit) firstElement), new IField[0], false);
			else if (!(firstElement instanceof IField))
				MessageDialog.openInformation(getShell(), DIALOG_TITLE, ActionMessages.AddDelegateMethodsAction_not_applicable);
		} catch (CoreException e) {
			ExceptionHandler.handle(e, getShell(), DIALOG_TITLE, ActionMessages.AddDelegateMethodsAction_error_actionfailed);
		}

	}

	/*
	 * (non-Javadoc) Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(ITextSelection selection) {
		try {
			if (!ActionUtil.isProcessable(fEditor))
				return;

			IJavaElement[] elements= SelectionConverter.codeResolveForked(fEditor, true);
			if (elements.length == 1 && (elements[0] instanceof IField)) {
				IField field= (IField) elements[0];
				run(field.getDeclaringType(), new IField[] { field}, true);
				return;
			}
			IJavaElement element= SelectionConverter.getElementAtOffset(fEditor);
			if (element != null) {
				IType type= (IType) element.getAncestor(IJavaElement.TYPE);
				if (type != null) {
					if (type.getFields().length > 0) {
						run(type, new IField[0], true);
						return;
					}
				}
			}
			MessageDialog.openInformation(getShell(), DIALOG_TITLE, ActionMessages.AddDelegateMethodsAction_not_applicable);
		} catch (CoreException e) {
			ExceptionHandler.handle(e, getShell(), DIALOG_TITLE, ActionMessages.AddDelegateMethodsAction_error_actionfailed);
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(), DIALOG_TITLE, ActionMessages.AddDelegateMethodsAction_error_actionfailed);
		} catch (InterruptedException e) {
			// cancelled
		}
	}

	private void run(IType type, IField[] preselected, boolean editor) throws CoreException {
		if (!ElementValidator.check(type, getShell(), DIALOG_TITLE, editor))
			return;
		if (!ActionUtil.isEditable(fEditor, getShell(), type))
			return;
		if (!canRunOn(type))
			return;
		showUI(type, preselected);
	}

	// ---- Structured Viewer -----------------------------------------------------------

	/*
	 * (non-Javadoc) Method declared on SelectionDispatchAction
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(canEnable(selection));
		} catch (JavaModelException e) {
			// http://bugs.eclipse.org/bugs/show_bug.cgi?id=19253
			if (JavaModelUtil.isExceptionToBeLogged(e))
				JavaPlugin.log(e);
			setEnabled(false);
		}
	}

	// ---- Java Editor --------------------------------------------------------------

	/*
	 * (non-Javadoc) Method declared on SelectionDispatchAction
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
	}

	private void showUI(IType type, IField[] fields) {
		try {
			CompilationUnit astRoot= SharedASTProvider.getAST(type.getCompilationUnit(), SharedASTProvider.WAIT_YES, new NullProgressMonitor());

			AddDelegateMethodsContentProvider provider= new AddDelegateMethodsContentProvider(astRoot, type, fields);
			SourceActionDialog dialog= new AddDelegateMethodsDialog(getShell(), new AddDelegateMethodsLabelProvider(), provider, fEditor, type, false);
			dialog.setValidator(new AddDelegateMethodsActionStatusValidator(provider.getCount()));
			AddDelegateMethodsViewerComparator comparator= new AddDelegateMethodsViewerComparator();
			dialog.setComparator(comparator);
			dialog.setInput(new Object());
			dialog.setContainerMode(true);
			dialog.setMessage(ActionMessages.AddDelegateMethodsAction_message);
			dialog.setTitle(ActionMessages.AddDelegateMethodsAction_title);
			IVariableBinding[] expanded= provider.getExpandedElements();
			if (expanded.length > 0) {
				dialog.setExpandedElements(expanded);
			} else {
				Object[] elements= provider.getElements(null);
				if (elements.length > 0) {
					comparator.sort(null, elements);
					Object[] expand= { elements[0]};
					dialog.setExpandedElements(expand);
				}
			}
			dialog.setInitialSelections(provider.getInitiallySelectedElements());
			dialog.setSize(60, 18);
			int result= dialog.open();
			if (result == Window.OK) {
				Object[] object= dialog.getResult();
				if (object == null) {
					notifyResult(false);
					return;
				}
				List<DelegateEntry> tuples= new ArrayList<DelegateEntry>(object.length);
				for (int index= 0; index < object.length; index++) {
					if (object[index] instanceof DelegateEntry)
						tuples.add((DelegateEntry) object[index]);
				}
				IEditorPart part= JavaUI.openInEditor(type);
				IRewriteTarget target= (IRewriteTarget) part.getAdapter(IRewriteTarget.class);
				try {
					if (target != null)
						target.beginCompoundChange();
					CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings(type.getJavaProject());
					settings.createComments= dialog.getGenerateComment();

					DelegateEntry[] methodToDelegate= tuples.toArray(new DelegateEntry[tuples.size()]);

					AddDelegateMethodsOperation operation= new AddDelegateMethodsOperation(astRoot, methodToDelegate, dialog.getElementPosition(), settings, true, false);
					IRunnableContext context= JavaPlugin.getActiveWorkbenchWindow();
					if (context == null)
						context= new BusyIndicatorRunnableContext();
					try {
						PlatformUI.getWorkbench().getProgressService().runInUI(context, new WorkbenchRunnableAdapter(operation, operation.getSchedulingRule()), operation.getSchedulingRule());
					} catch (InterruptedException exception) {
						// User interruption
					}
				} finally {
					if (target != null)
						target.endCompoundChange();
				}
			}
			notifyResult(result == Window.OK);
		} catch (CoreException exception) {
			ExceptionHandler.handle(exception, DIALOG_TITLE, ActionMessages.AddDelegateMethodsAction_error_actionfailed);
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, DIALOG_TITLE, ActionMessages.AddDelegateMethodsAction_error_actionfailed);
		}
	}
}

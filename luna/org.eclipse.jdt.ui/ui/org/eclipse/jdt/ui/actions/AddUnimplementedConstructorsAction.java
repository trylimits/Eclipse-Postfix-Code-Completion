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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.core.resources.IWorkspaceRunnable;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;

import org.eclipse.jface.text.IRewriteTarget;
import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.NodeFinder;

import org.eclipse.jdt.internal.corext.codemanipulation.AddUnimplementedConstructorsOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.template.java.CodeTemplateContextType;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.dialogs.SourceActionDialog;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.refactoring.IVisibilityChangeListener;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.ui.util.ElementValidator;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.BindingLabelProvider;

/**
 * Creates unimplemented constructors for a type.
 * <p>
 * Will open the parent compilation unit in a Java editor. Opens a dialog with a list of
 * constructors from the super class which can be generated. User is able to check or
 * uncheck items before constructors are generated. The result is unsaved, so the user can
 * decide if the changes are acceptable.
 * <p>
 * The action is applicable to structured selections containing elements of type
 * <code>IType</code>.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class AddUnimplementedConstructorsAction extends SelectionDispatchAction {

	private static class AddUnimplementedConstructorsContentProvider implements ITreeContentProvider {

		private static final Object[] EMPTY= new Object[0];

		private IMethodBinding[] fMethodsList= new IMethodBinding[0];

		private final CompilationUnit fUnit;

		public AddUnimplementedConstructorsContentProvider(IType type) throws JavaModelException {
			RefactoringASTParser parser= new RefactoringASTParser(ASTProvider.SHARED_AST_LEVEL);
			fUnit= parser.parse(type.getCompilationUnit(), true);
			AbstractTypeDeclaration declaration= (AbstractTypeDeclaration) ASTNodes.getParent(NodeFinder.perform(fUnit, type.getNameRange()), AbstractTypeDeclaration.class);
			if (declaration != null) {
				ITypeBinding binding= declaration.resolveBinding();
				if (binding != null)
					fMethodsList= StubUtility2.getVisibleConstructors(binding, true, false);
			}
		}

		public CompilationUnit getCompilationUnit() {
			return fUnit;
		}

		/*
		 * @see IContentProvider#dispose()
		 */
		public void dispose() {
		}

		/*
		 * @see ITreeContentProvider#getChildren(Object)
		 */
		public Object[] getChildren(Object parentElement) {
			return EMPTY;
		}

		/*
		 * @see IStructuredContentProvider#getElements(Object)
		 */
		public Object[] getElements(Object inputElement) {
			return fMethodsList;
		}

		/*
		 * @see ITreeContentProvider#getParent(Object)
		 */
		public Object getParent(Object element) {
			return null;
		}

		/*
		 * @see ITreeContentProvider#hasChildren(Object)
		 */
		public boolean hasChildren(Object element) {
			return getChildren(element).length > 0;
		}

		/*
		 * @see IContentProvider#inputChanged(Viewer, Object, Object)
		 */
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}

	}

	private static class AddUnimplementedConstructorsDialog extends SourceActionDialog {

		private IDialogSettings fAddConstructorsSettings;

		private int fHeight= 18;

		private boolean fOmitSuper;

		private int fWidth= 60;

		private final String OMIT_SUPER= "OmitCallToSuper"; //$NON-NLS-1$

		private final String SETTINGS_SECTION= "AddUnimplementedConstructorsDialog"; //$NON-NLS-1$

		public AddUnimplementedConstructorsDialog(Shell parent, ILabelProvider labelProvider, ITreeContentProvider contentProvider, CompilationUnitEditor editor, IType type) throws JavaModelException {
			super(parent, labelProvider, contentProvider, editor, type, true);

			IDialogSettings dialogSettings= JavaPlugin.getDefault().getDialogSettings();
			fAddConstructorsSettings= dialogSettings.getSection(SETTINGS_SECTION);
			if (fAddConstructorsSettings == null) {
				fAddConstructorsSettings= dialogSettings.addNewSection(SETTINGS_SECTION);
				fAddConstructorsSettings.put(OMIT_SUPER, false);
			}

			fOmitSuper= fAddConstructorsSettings.getBoolean(OMIT_SUPER);
		}

		@Override
		protected void configureShell(Shell shell) {
			super.configureShell(shell);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(shell, IJavaHelpContextIds.ADD_UNIMPLEMENTED_CONSTRUCTORS_DIALOG);
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			initializeDialogUnits(parent);

			Composite composite= new Composite(parent, SWT.NONE);
			GridLayout layout= new GridLayout();
			GridData gd= null;

			layout.marginHeight= convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
			layout.marginWidth= convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
			layout.verticalSpacing= convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
			layout.horizontalSpacing= convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
			composite.setLayout(layout);

			Label messageLabel= createMessageArea(composite);
			if (messageLabel != null) {
				gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
				gd.horizontalSpan= 2;
				messageLabel.setLayoutData(gd);
			}

			Composite inner= new Composite(composite, SWT.NONE);
			GridLayout innerLayout= new GridLayout();
			innerLayout.numColumns= 2;
			innerLayout.marginHeight= 0;
			innerLayout.marginWidth= 0;
			inner.setLayout(innerLayout);
			inner.setFont(parent.getFont());

			CheckboxTreeViewer treeViewer= createTreeViewer(inner);
			gd= new GridData(GridData.FILL_BOTH);
			gd.widthHint= convertWidthInCharsToPixels(fWidth);
			gd.heightHint= convertHeightInCharsToPixels(fHeight);
			treeViewer.getControl().setLayoutData(gd);

			Composite buttonComposite= createSelectionButtons(inner);
			gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL);
			buttonComposite.setLayoutData(gd);

			gd= new GridData(GridData.FILL_BOTH);
			inner.setLayoutData(gd);

			Composite entryComposite= createInsertPositionCombo(composite);
			entryComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			Composite commentComposite= createCommentSelection(composite);
			commentComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			Composite overrideSuperComposite= createOmitSuper(composite);
			overrideSuperComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			Control linkControl= createLinkControl(composite);
			if (linkControl != null)
				linkControl.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			gd= new GridData(GridData.FILL_BOTH);
			composite.setLayoutData(gd);

			applyDialogFont(composite);

			return composite;
		}

		@Override
		protected Composite createInsertPositionCombo(Composite composite) {
			Composite entryComposite= super.createInsertPositionCombo(composite);
			addVisibilityAndModifiersChoices(entryComposite);

			return entryComposite;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.dialogs.SourceActionDialog#createLinkControl(org.eclipse.swt.widgets.Composite)
		 */
		@Override
		protected Control createLinkControl(Composite composite) {
			Link link= new Link(composite, SWT.WRAP);
			link.setText(ActionMessages.AddUnimplementedConstructorsAction_template_link_message);
			link.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					openCodeTempatePage(CodeTemplateContextType.CONSTRUCTORCOMMENT_ID);
				}
			});
			link.setToolTipText(ActionMessages.AddUnimplementedConstructorsAction_template_link_tooltip);

			GridData gridData= new GridData(SWT.FILL, SWT.BEGINNING, true, false);
			gridData.widthHint= convertWidthInCharsToPixels(40); // only expand further if anyone else requires it
			link.setLayoutData(gridData);
			return link;
		}

		private Composite createOmitSuper(Composite composite) {
			Composite omitSuperComposite= new Composite(composite, SWT.NONE);
			GridLayout layout= new GridLayout();
			layout.marginHeight= 0;
			layout.marginWidth= 0;
			omitSuperComposite.setLayout(layout);
			omitSuperComposite.setFont(composite.getFont());

			Button omitSuperButton= new Button(omitSuperComposite, SWT.CHECK);
			omitSuperButton.setText(ActionMessages.AddUnimplementedConstructorsDialog_omit_super);
			omitSuperButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));

			omitSuperButton.addSelectionListener(new SelectionListener() {

				public void widgetDefaultSelected(SelectionEvent e) {
					widgetSelected(e);
				}

				public void widgetSelected(SelectionEvent e) {
					boolean isSelected= (((Button) e.widget).getSelection());
					setOmitSuper(isSelected);
				}
			});
			omitSuperButton.setSelection(isOmitSuper());
			GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
			gd.horizontalSpan= 2;
			omitSuperButton.setLayoutData(gd);

			return omitSuperComposite;
		}

		@Override
		protected Composite createVisibilityControlAndModifiers(Composite parent, final IVisibilityChangeListener visibilityChangeListener, int[] availableVisibilities, int correctVisibility) {
			Composite visibilityComposite= createVisibilityControl(parent, visibilityChangeListener, availableVisibilities, correctVisibility);
			return visibilityComposite;
		}

		public boolean isOmitSuper() {
			return fOmitSuper;
		}

		public void setOmitSuper(boolean omitSuper) {
			if (fOmitSuper != omitSuper) {
				fOmitSuper= omitSuper;
				fAddConstructorsSettings.put(OMIT_SUPER, omitSuper);
			}
		}
	}

	private static class AddUnimplementedConstructorsValidator implements ISelectionStatusValidator {

		private static int fEntries;

		AddUnimplementedConstructorsValidator(int entries) {
			super();
			fEntries= entries;
		}

		private int countSelectedMethods(Object[] selection) {
			int count= 0;
			for (int i= 0; i < selection.length; i++) {
				if (selection[i] instanceof IMethodBinding)
					count++;
			}
			return count;
		}

		public IStatus validate(Object[] selection) {
			int count= countSelectedMethods(selection);
			if (count == 0)
				return new StatusInfo(IStatus.ERROR, ""); //$NON-NLS-1$
			String message= Messages.format(ActionMessages.AddUnimplementedConstructorsAction_methods_selected, new Object[] { String.valueOf(count), String.valueOf(fEntries)});
			return new StatusInfo(IStatus.INFO, message);
		}
	}

	private static final String DIALOG_TITLE= ActionMessages.AddUnimplementedConstructorsAction_error_title;

	private CompilationUnitEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this
	 * constructor.
	 *
	 * @param editor the compilation unit editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public AddUnimplementedConstructorsAction(CompilationUnitEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(checkEnabledEditor());
	}

	/**
	 * Creates a new <code>AddUnimplementedConstructorsAction</code>. The action
	 * requires that the selection provided by the site's selection provider is of type
	 * <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public AddUnimplementedConstructorsAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.AddUnimplementedConstructorsAction_label);
		setDescription(ActionMessages.AddUnimplementedConstructorsAction_description);
		setToolTipText(ActionMessages.AddUnimplementedConstructorsAction_tooltip);

		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.ADD_UNIMPLEMENTED_CONSTRUCTORS_ACTION);
	}

	private boolean canEnable(IStructuredSelection selection) throws JavaModelException {
		if ((selection.size() == 1) && (selection.getFirstElement() instanceof IType)) {
			IType type= (IType) selection.getFirstElement();
			return type.getCompilationUnit() != null && !type.isInterface() && !type.isEnum() && !type.isAnonymous();
		}

		if ((selection.size() == 1) && (selection.getFirstElement() instanceof ICompilationUnit))
			return true;

		return false;
	}

	private boolean checkEnabledEditor() {
		return fEditor != null && SelectionConverter.canOperateOn(fEditor);
	}

	private String getDialogTitle() {
		return DIALOG_TITLE;
	}

	private IType getSelectedType(IStructuredSelection selection) throws JavaModelException {
		Object[] elements= selection.toArray();
		if (elements.length == 1 && (elements[0] instanceof IType)) {
			IType type= (IType) elements[0];
			if (type.getCompilationUnit() != null && !type.isInterface() && !type.isEnum()) {
				return type;
			}
		} else if (elements[0] instanceof ICompilationUnit) {
			ICompilationUnit cu= (ICompilationUnit) elements[0];
			IType type= cu.findPrimaryType();
			if (type != null && !type.isInterface() && !type.isEnum())
				return type;
		}
		return null;
	}

	/*
	 * (non-Javadoc) Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(IStructuredSelection selection) {
		Shell shell= getShell();
		try {
			IType type= getSelectedType(selection);
			if (type == null) {
				MessageDialog.openInformation(getShell(), getDialogTitle(), ActionMessages.AddUnimplementedConstructorsAction_not_applicable);
				return;
			}
			if (type.isAnnotation()) {
				MessageDialog.openInformation(getShell(), getDialogTitle(), ActionMessages.AddUnimplementedConstructorsAction_annotation_not_applicable);
				return;
			} else if (type.isInterface()) {
				MessageDialog.openInformation(getShell(), getDialogTitle(), ActionMessages.AddUnimplementedConstructorsAction_interface_not_applicable);
				return;
			} else if (type.isEnum()) {
				MessageDialog.openInformation(getShell(), getDialogTitle(), ActionMessages.AddUnimplementedConstructorsAction_enum_not_applicable);
				return;
			}
			run(shell, type, false);
		} catch (CoreException e) {
			ExceptionHandler.handle(e, shell, getDialogTitle(), null);
		}
	}

	/*
	 * (non-Javadoc) Method declared on SelectionDispatchAction
	 */
	@Override
	public void run(ITextSelection selection) {
		if (!ActionUtil.isProcessable(fEditor))
			return;
		try {
			Shell shell= getShell();
			IType type= SelectionConverter.getTypeAtOffset(fEditor);
			if (type != null)
				run(shell, type, true);
			else
				MessageDialog.openInformation(shell, getDialogTitle(), ActionMessages.AddUnimplementedConstructorsAction_not_applicable);
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), null);
		} catch (CoreException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), null);
		}
	}

	// ---- Helpers -------------------------------------------------------------------

	private void run(Shell shell, IType type, boolean activatedFromEditor) throws CoreException {
		if (!ElementValidator.check(type, getShell(), getDialogTitle(), activatedFromEditor)) {
			notifyResult(false);
			return;
		}
		if (!ActionUtil.isEditable(fEditor, getShell(), type)) {
			notifyResult(false);
			return;
		}

		AddUnimplementedConstructorsContentProvider provider= new AddUnimplementedConstructorsContentProvider(type);
		Object[] constructors= provider.getElements(null);
		if (constructors.length == 0) {
			MessageDialog.openInformation(getShell(), getDialogTitle(), ActionMessages.AddUnimplementedConstructorsAction_error_nothing_found);
			notifyResult(false);
			return;
		}

		AddUnimplementedConstructorsDialog dialog= new AddUnimplementedConstructorsDialog(shell, new BindingLabelProvider(), provider, fEditor, type);
		dialog.setCommentString(ActionMessages.SourceActionDialog_createConstructorComment);
		dialog.setTitle(ActionMessages.AddUnimplementedConstructorsAction_dialog_title);
		dialog.setInitialSelections(constructors);
		dialog.setContainerMode(true);
		dialog.setComparator(new JavaElementComparator());
		dialog.setSize(60, 18);
		dialog.setInput(new Object());
		dialog.setMessage(ActionMessages.AddUnimplementedConstructorsAction_dialog_label);
		dialog.setValidator(new AddUnimplementedConstructorsValidator(constructors.length));

		final int dialogResult= dialog.open();
		if (dialogResult == Window.OK) {
			Object[] elements= dialog.getResult();
			if (elements == null) {
				notifyResult(false);
				return;
			}

			ArrayList<IMethodBinding> result= new ArrayList<IMethodBinding>();
			for (int i= 0; i < elements.length; i++) {
				Object elem= elements[i];
				if (elem instanceof IMethodBinding) {
					result.add((IMethodBinding) elem);
				}
			}
			IMethodBinding[] selected= result.toArray(new IMethodBinding[result.size()]);

			CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings(type.getJavaProject());
			settings.createComments= dialog.getGenerateComment();
			IEditorPart editor= JavaUI.openInEditor(type, true, false);
			IRewriteTarget target= editor != null ? (IRewriteTarget) editor.getAdapter(IRewriteTarget.class) : null;
			if (target != null)
				target.beginCompoundChange();
			try {
				CompilationUnit astRoot= provider.getCompilationUnit();
				final ITypeBinding typeBinding= ASTNodes.getTypeBinding(astRoot, type);
				int insertPos= dialog.getInsertOffset();

				AddUnimplementedConstructorsOperation operation= (AddUnimplementedConstructorsOperation) createRunnable(astRoot, typeBinding, selected, insertPos, dialog.getGenerateComment(), dialog.getVisibilityModifier(), dialog.isOmitSuper());
				IRunnableContext context= JavaPlugin.getActiveWorkbenchWindow();
				if (context == null)
					context= new BusyIndicatorRunnableContext();
				PlatformUI.getWorkbench().getProgressService().runInUI(context, new WorkbenchRunnableAdapter(operation, operation.getSchedulingRule()), operation.getSchedulingRule());
				String[] created= operation.getCreatedConstructors();
				if (created == null || created.length == 0)
					MessageDialog.openInformation(shell, getDialogTitle(), ActionMessages.AddUnimplementedConstructorsAction_error_nothing_found);
			} catch (InvocationTargetException e) {
				ExceptionHandler.handle(e, shell, getDialogTitle(), null);
			} catch (InterruptedException e) {
				// Do nothing. Operation has been canceled by user.
			} finally {
				if (target != null) {
					target.endCompoundChange();
				}
			}
		}
		notifyResult(dialogResult == Window.OK);
	}

	/**
	 * Returns a runnable that creates the constructor stubs.
	 *
	 * @param astRoot the AST of the compilation unit to work on. The AST must have been created from a {@link ICompilationUnit}, that
	 * means {@link ASTParser#setSource(ICompilationUnit)} was used.
	 * @param type the binding of the type to add the new methods to. The type binding must correspond to a type declaration in the AST.
	 * @param constructorsToOverride the bindings of constructors to override or <code>null</code> to implement all visible constructors from the super class.
	 * @param insertPos a hint for a location in the source where to insert the new methods or <code>-1</code> to use the default behavior.
	 * @param createComments if set, comments will be added to the new methods.
	 * @param visibility the visibility for the new modifiers. (see {@link Flags}) for visibility constants.
	 * @param omitSuper if set, no <code>super()</code> call without arguments will be created.
	 * @return returns a runnable that creates the constructor stubs.
	 * @throws IllegalArgumentException a {@link IllegalArgumentException} is thrown if the AST passed has not been created from a {@link ICompilationUnit}.
	 *
	 * @since 3.2
	 */
	public static IWorkspaceRunnable createRunnable(CompilationUnit astRoot, ITypeBinding type, IMethodBinding[] constructorsToOverride, int insertPos, boolean createComments, int visibility, boolean omitSuper) {
		AddUnimplementedConstructorsOperation operation= new AddUnimplementedConstructorsOperation(astRoot, type, constructorsToOverride, insertPos, true, true, false);
		operation.setCreateComments(createComments);
		operation.setOmitSuper(omitSuper);
		operation.setVisibility(visibility);
		return operation;
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
}

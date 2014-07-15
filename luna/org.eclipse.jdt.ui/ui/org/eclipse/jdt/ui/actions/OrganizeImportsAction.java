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
import java.util.Comparator;

import com.ibm.icu.text.Collator;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IEditingSupport;
import org.eclipse.jface.text.IEditingSupportRegistry;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.IRewriteTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.source.ISourceViewer;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressService;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.search.TypeNameMatch;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.OrganizeImportsOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.OrganizeImportsOperation.IChooseImportQuery;
import org.eclipse.jdt.internal.corext.util.History;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.QualifiedTypeNameHistory;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.SharedASTProvider;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.MultiOrganizeImportAction;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.dialogs.MultiElementListSelectionDialog;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.util.ElementValidator;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.TypeNameMatchLabelProvider;

/**
 * Organizes the imports of a compilation unit.
 * <p>
 * The action is applicable to selections containing elements of
 * type <code>ICompilationUnit</code> or <code>IPackage
 * </code>.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class OrganizeImportsAction extends SelectionDispatchAction {

	private static final OrganizeImportComparator ORGANIZE_IMPORT_COMPARATOR= new OrganizeImportComparator();

	private JavaEditor fEditor;
	/** <code>true</code> if the query dialog is showing. */
	private boolean fIsQueryShowing= false;
	private final MultiOrganizeImportAction fCleanUpDelegate;

	/* (non-Javadoc)
	 * Class implements IObjectActionDelegate
	 */
	public static class ObjectDelegate implements IObjectActionDelegate {
		private OrganizeImportsAction fAction;
		public void setActivePart(IAction action, IWorkbenchPart targetPart) {
			fAction= new OrganizeImportsAction(targetPart.getSite());
		}
		public void run(IAction action) {
			fAction.run();
		}
		public void selectionChanged(IAction action, ISelection selection) {
			if (fAction == null)
				action.setEnabled(false);
		}
	}

	private static final class OrganizeImportComparator implements Comparator<String> {

		public int compare(String o1, String o2) {
			if (o1.equals(o2))
				return 0;

			History history= QualifiedTypeNameHistory.getDefault();

			int pos1= history.getPosition(o1);
			int pos2= history.getPosition(o2);

			if (pos1 == pos2)
				return Collator.getInstance().compare(o1, o2);

			if (pos1 > pos2) {
				return -1;
			} else {
				return 1;
			}
		}

	}

	/**
	 * Creates a new <code>OrganizeImportsAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type <code>
	 * org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public OrganizeImportsAction(IWorkbenchSite site) {
		super(site);

		fCleanUpDelegate= new MultiOrganizeImportAction(site);

		setText(ActionMessages.OrganizeImportsAction_label);
		setToolTipText(ActionMessages.OrganizeImportsAction_tooltip);
		setDescription(ActionMessages.OrganizeImportsAction_description);

		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.ORGANIZE_IMPORTS_ACTION);
	}

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the Java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public OrganizeImportsAction(JavaEditor editor) {
		super(editor.getEditorSite());

		fEditor= editor;
		fCleanUpDelegate= new MultiOrganizeImportAction(editor);

		setText(ActionMessages.OrganizeImportsAction_label);
		setToolTipText(ActionMessages.OrganizeImportsAction_tooltip);
		setDescription(ActionMessages.OrganizeImportsAction_description);

		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.ORGANIZE_IMPORTS_ACTION);

		setEnabled(fCleanUpDelegate.isEnabled());
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		fCleanUpDelegate.selectionChanged(selection);
		setEnabled(fCleanUpDelegate.isEnabled());
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		fCleanUpDelegate.selectionChanged(selection);
		setEnabled(fCleanUpDelegate.isEnabled());
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(ITextSelection selection) {
		ICompilationUnit cu= getCompilationUnit(fEditor);
		if (cu != null) {
			run(cu);
		}
	}

	private static ICompilationUnit getCompilationUnit(JavaEditor editor) {
		IJavaElement element= JavaUI.getEditorInputJavaElement(editor.getEditorInput());
		if (!(element instanceof ICompilationUnit))
			return null;

		return (ICompilationUnit)element;
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(IStructuredSelection selection) {
		ICompilationUnit[] cus= fCleanUpDelegate.getCompilationUnits(selection);
		if (cus.length == 0) {
			MessageDialog.openInformation(getShell(), ActionMessages.OrganizeImportsAction_EmptySelection_title, ActionMessages.OrganizeImportsAction_EmptySelection_description);
		} else if (cus.length == 1) {
			run(cus[0]);
		} else {
			fCleanUpDelegate.run(selection);
		}
	}

	/**
	 * Perform organize import on multiple compilation units. No editors are opened.
	 * @param cus The compilation units to run on
	 */
	public void runOnMultiple(final ICompilationUnit[] cus) {
		if (cus.length == 0)
			return;

		fCleanUpDelegate.run(new StructuredSelection(cus));
	}

	/**
	 * Runs the organize import action on a single compilation unit
	 *
	 * @param cu The compilation unit to process
	 */
	public void run(ICompilationUnit cu) {

		JavaEditor editor= null;
		if (fEditor != null) {
			editor= fEditor;

			//organize imports from within editor -> editor has focus
			if (!ElementValidator.check(cu, getShell(), ActionMessages.OrganizeImportsAction_error_title, true))
				return;
		} else {
			IEditorPart openEditor= EditorUtility.isOpenInEditor(cu);
			if (!(openEditor instanceof JavaEditor)) {
				fCleanUpDelegate.run(new StructuredSelection(cu));
				return;
			}

			editor= (JavaEditor) openEditor;
			//organize imports from package explorer -> editor does not have focus
			if (!ElementValidator.check(cu, getShell(), ActionMessages.OrganizeImportsAction_error_title, false))
				return;
		}

		Assert.isNotNull(editor);
		if (!ActionUtil.isEditable(editor, getShell(), cu))
			return;

		CompilationUnit astRoot= SharedASTProvider.getAST(cu, SharedASTProvider.WAIT_ACTIVE_ONLY, null);

		CodeGenerationSettings settings= JavaPreferencesSettings.getCodeGenerationSettings(cu.getJavaProject());
		OrganizeImportsOperation op= new OrganizeImportsOperation(cu, astRoot, settings.importIgnoreLowercase, !cu.isWorkingCopy(), true, createChooseImportQuery(editor));

		IRewriteTarget target= (IRewriteTarget) editor.getAdapter(IRewriteTarget.class);
		if (target != null) {
			target.beginCompoundChange();
		}

		IProgressService progressService= PlatformUI.getWorkbench().getProgressService();
		IRunnableContext context= getSite().getWorkbenchWindow();
		if (context == null) {
			context= progressService;
		}
		IEditingSupport helper= createViewerHelper();
		try {
			registerHelper(helper, editor);
			progressService.runInUI(context, new WorkbenchRunnableAdapter(op, op.getScheduleRule()), op.getScheduleRule());
			IProblem parseError= op.getParseError();
			if (parseError != null) {
				String message= Messages.format(ActionMessages.OrganizeImportsAction_single_error_parse, parseError.getMessage());
				MessageDialog.openInformation(getShell(), ActionMessages.OrganizeImportsAction_error_title, message);
				if (parseError.getSourceStart() != -1) {
					editor.selectAndReveal(parseError.getSourceStart(), parseError.getSourceEnd() - parseError.getSourceStart() + 1);
				}
			} else {
				setStatusBarMessage(getOrganizeInfo(op), editor);
			}
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(), ActionMessages.OrganizeImportsAction_error_title, ActionMessages.OrganizeImportsAction_error_message);
		} catch (InterruptedException e) {
		} finally {
			deregisterHelper(helper, editor);
			if (target != null) {
				target.endCompoundChange();
			}
		}
	}

	private String getOrganizeInfo(OrganizeImportsOperation op) {
		int nImportsAdded= op.getNumberOfImportsAdded();
		if (nImportsAdded >= 0) {
			if (nImportsAdded == 1) {
				return ActionMessages.OrganizeImportsAction_summary_added_singular;
			} else {
				return Messages.format(ActionMessages.OrganizeImportsAction_summary_added_plural, String.valueOf(nImportsAdded));
			}
		} else {
			if (nImportsAdded == -1) {
				return ActionMessages.OrganizeImportsAction_summary_removed_singular;
			} else {
				return Messages.format(ActionMessages.OrganizeImportsAction_summary_removed_plural, String.valueOf(-nImportsAdded));
			}
		}
	}

	private IChooseImportQuery createChooseImportQuery(final JavaEditor editor) {
		return new IChooseImportQuery() {
			public TypeNameMatch[] chooseImports(TypeNameMatch[][] openChoices, ISourceRange[] ranges) {
				return doChooseImports(openChoices, ranges, editor);
			}
		};
	}

	private TypeNameMatch[] doChooseImports(TypeNameMatch[][] openChoices, final ISourceRange[] ranges, final JavaEditor editor) {
		// remember selection
		ISelection sel= editor.getSelectionProvider().getSelection();
		TypeNameMatch[] result= null;
		ILabelProvider labelProvider= new TypeNameMatchLabelProvider(TypeNameMatchLabelProvider.SHOW_FULLYQUALIFIED);

		MultiElementListSelectionDialog dialog= new MultiElementListSelectionDialog(getShell(), labelProvider) {
			@Override
			protected void handleSelectionChanged() {
				super.handleSelectionChanged();
				// show choices in editor
				doListSelectionChanged(getCurrentPage(), ranges, editor);
			}
		};
		fIsQueryShowing= true;
		dialog.setTitle(ActionMessages.OrganizeImportsAction_selectiondialog_title);
		dialog.setMessage(ActionMessages.OrganizeImportsAction_selectiondialog_message);
		dialog.setElements(openChoices);
		dialog.setComparator(ORGANIZE_IMPORT_COMPARATOR);
		if (dialog.open() == Window.OK) {
			Object[] res= dialog.getResult();
			result= new TypeNameMatch[res.length];
			for (int i= 0; i < res.length; i++) {
				Object[] array= (Object[]) res[i];
				if (array.length > 0) {
					result[i]= (TypeNameMatch) array[0];
					QualifiedTypeNameHistory.remember(result[i].getFullyQualifiedName());
				}
			}
		}
		// restore selection
		if (sel instanceof ITextSelection) {
			ITextSelection textSelection= (ITextSelection) sel;
			editor.selectAndReveal(textSelection.getOffset(), textSelection.getLength());
		}
		fIsQueryShowing= false;
		return result;
	}

	private void doListSelectionChanged(int page, ISourceRange[] ranges, JavaEditor editor) {
		if (ranges != null && page >= 0 && page < ranges.length) {
			ISourceRange range= ranges[page];
			editor.selectAndReveal(range.getOffset(), range.getLength());
		}
	}

	private void setStatusBarMessage(String message, JavaEditor editor) {
		IStatusLineManager manager= editor.getEditorSite().getActionBars().getStatusLineManager();
		manager.setMessage(message);
	}

	private IEditingSupport createViewerHelper() {
		return new IEditingSupport() {
			public boolean isOriginator(DocumentEvent event, IRegion subjectRegion) {
				return true; // assume true, since we only register while we are active
			}
			public boolean ownsFocusShell() {
				return fIsQueryShowing;
			}

		};
	}

	private void registerHelper(IEditingSupport helper, JavaEditor editor) {
		ISourceViewer viewer= editor.getViewer();
		if (viewer instanceof IEditingSupportRegistry) {
			IEditingSupportRegistry registry= (IEditingSupportRegistry) viewer;
			registry.register(helper);
		}
	}

	private void deregisterHelper(IEditingSupport helper, JavaEditor editor) {
		ISourceViewer viewer= editor.getViewer();
		if (viewer instanceof IEditingSupportRegistry) {
			IEditingSupportRegistry registry= (IEditingSupportRegistry) viewer;
			registry.unregister(helper);
		}
	}
}

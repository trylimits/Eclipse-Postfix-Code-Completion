/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Mateusz Matela <mateusz.matela@gmail.com> - [code manipulation] [dcr] toString() builder wizard - https://bugs.eclipse.org/bugs/show_bug.cgi?id=26070
 *******************************************************************************/
package org.eclipse.jdt.ui.actions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.commands.IHandler;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.actions.AddBookmarkAction;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.ide.IDEActionFactory;
import org.eclipse.ui.part.Page;

import org.eclipse.ui.texteditor.IUpdate;

import org.eclipse.jdt.ui.IContextMenuConstants;

import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.AddTaskAction;
import org.eclipse.jdt.internal.ui.actions.AllCleanUpsAction;
import org.eclipse.jdt.internal.ui.actions.FindBrokenNLSKeysAction;
import org.eclipse.jdt.internal.ui.actions.JDTQuickMenuCreator;
import org.eclipse.jdt.internal.ui.actions.MultiSortMembersAction;
import org.eclipse.jdt.internal.ui.javaeditor.AddImportOnSelectionAction;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;


/**
 * Action group that adds the source and generate actions to a part's context
 * menu and installs handlers for the corresponding global menu actions.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class GenerateActionGroup extends ActionGroup {

	/**
	 * Pop-up menu: id of the source sub menu (value <code>org.eclipse.jdt.ui.source.menu</code>).
	 *
	 * @since 3.0
	 */
	public static final String MENU_ID= "org.eclipse.jdt.ui.source.menu"; //$NON-NLS-1$

	/**
	 * Pop-up menu: id of the import group of the source sub menu (value
	 * <code>importGroup</code>).
	 *
	 * @since 3.0
	 */
	public static final String GROUP_IMPORT= "importGroup";  //$NON-NLS-1$

	/**
	 * Pop-up menu: id of the generate group of the source sub menu (value
	 * <code>generateGroup</code>).
	 *
	 * @since 3.0
	 */
	public static final String GROUP_GENERATE= "generateGroup";  //$NON-NLS-1$

	/**
	 * Pop-up menu: id of the code group of the source sub menu (value
	 * <code>codeGroup</code>).
	 *
	 * @since 3.0
	 */
	public static final String GROUP_CODE= "codeGroup";  //$NON-NLS-1$

	/**
	 * Pop-up menu: id of the externalize group of the source sub menu (value
	 * <code>externalizeGroup</code>).
	 *
	 * TODO: Make API
	 */
	private static final String GROUP_EXTERNALIZE= "externalizeGroup"; //$NON-NLS-1$

	/**
	 * Pop-up menu: id of the comment group of the source sub menu (value
	 * <code>commentGroup</code>).
	 *
	 * TODO: Make API
	 */
	private static final String GROUP_COMMENT= "commentGroup"; //$NON-NLS-1$

	/**
	 * Pop-up menu: id of the edit group of the source sub menu (value
	 * <code>editGroup</code>).
	 *
	 * TODO: Make API
	 */
	private static final String GROUP_EDIT= "editGroup"; //$NON-NLS-1$

	private CompilationUnitEditor fEditor;
	private IWorkbenchSite fSite;
	private String fGroupName= IContextMenuConstants.GROUP_REORGANIZE;
	private List<ISelectionChangedListener> fRegisteredSelectionListeners;

	private AddImportOnSelectionAction fAddImport;
	private OverrideMethodsAction fOverrideMethods;
	private GenerateHashCodeEqualsAction fHashCodeEquals;
	private GenerateToStringAction fToString;
	private AddGetterSetterAction fAddGetterSetter;
	private AddDelegateMethodsAction fAddDelegateMethods;
	private AddUnimplementedConstructorsAction fAddUnimplementedConstructors;
	private GenerateNewConstructorUsingFieldsAction fGenerateConstructorUsingFields;
	private AddJavaDocStubAction fAddJavaDocStub;
	private AddBookmarkAction fAddBookmark;
	private AddTaskAction fAddTaskAction;
	private ExternalizeStringsAction fExternalizeStrings;
	private AllCleanUpsAction fCleanUp;
	private FindBrokenNLSKeysAction fFindNLSProblems;

	private OrganizeImportsAction fOrganizeImports;
	private MultiSortMembersAction fSortMembers;
	private FormatAllAction fFormatAll;

	private static final String QUICK_MENU_ID= "org.eclipse.jdt.ui.edit.text.java.source.quickMenu"; //$NON-NLS-1$

	private IHandlerActivation fQuickAccessHandlerActivation;
	private IHandlerService fHandlerService;

	private final ISelectionProvider fSelectionProvider;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the compilation unit editor
	 * @param groupName the group name to add the action to
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public GenerateActionGroup(CompilationUnitEditor editor, String groupName) {
		fSite= editor.getSite();
		fSelectionProvider= fSite.getSelectionProvider();
		fEditor= editor;
		fGroupName= groupName;

		fAddImport= new AddImportOnSelectionAction(editor);
		fAddImport.setActionDefinitionId(IJavaEditorActionDefinitionIds.ADD_IMPORT);
		fAddImport.update();
		editor.setAction("AddImport", fAddImport); //$NON-NLS-1$

		fOrganizeImports= new OrganizeImportsAction(editor);
		fOrganizeImports.setActionDefinitionId(IJavaEditorActionDefinitionIds.ORGANIZE_IMPORTS);
		editor.setAction("OrganizeImports", fOrganizeImports); //$NON-NLS-1$

		fSortMembers= new MultiSortMembersAction(editor);
		fSortMembers.setActionDefinitionId(IJavaEditorActionDefinitionIds.SORT_MEMBERS);
		editor.setAction("SortMembers", fSortMembers); //$NON-NLS-1$

		fOverrideMethods= new OverrideMethodsAction(editor);
		fOverrideMethods.setActionDefinitionId(IJavaEditorActionDefinitionIds.OVERRIDE_METHODS);
		editor.setAction("OverrideMethods", fOverrideMethods); //$NON-NLS-1$

		fAddGetterSetter= new AddGetterSetterAction(editor);
		fAddGetterSetter.setActionDefinitionId(IJavaEditorActionDefinitionIds.CREATE_GETTER_SETTER);
		editor.setAction("AddGetterSetter", fAddGetterSetter); //$NON-NLS-1$

		fAddDelegateMethods= new AddDelegateMethodsAction(editor);
		fAddDelegateMethods.setActionDefinitionId(IJavaEditorActionDefinitionIds.CREATE_DELEGATE_METHODS);
		editor.setAction("AddDelegateMethods", fAddDelegateMethods); //$NON-NLS-1$

		fAddUnimplementedConstructors= new AddUnimplementedConstructorsAction(editor);
		fAddUnimplementedConstructors.setActionDefinitionId(IJavaEditorActionDefinitionIds.ADD_UNIMPLEMENTED_CONTRUCTORS);
		editor.setAction("AddUnimplementedConstructors", fAddUnimplementedConstructors); //$NON-NLS-1$

		fGenerateConstructorUsingFields= new GenerateNewConstructorUsingFieldsAction(editor);
		fGenerateConstructorUsingFields.setActionDefinitionId(IJavaEditorActionDefinitionIds.GENERATE_CONSTRUCTOR_USING_FIELDS);
		editor.setAction("GenerateConstructorUsingFields", fGenerateConstructorUsingFields); //$NON-NLS-1$

		fHashCodeEquals= new GenerateHashCodeEqualsAction(editor);
		fHashCodeEquals.setActionDefinitionId(IJavaEditorActionDefinitionIds.GENERATE_HASHCODE_EQUALS);
		editor.setAction("GenerateHashCodeEquals", fHashCodeEquals); //$NON-NLS-1$
		
		fToString= new GenerateToStringAction(editor);
		fToString.setActionDefinitionId(IJavaEditorActionDefinitionIds.GENERATE_TOSTRING);
		editor.setAction("GenerateToString", fToString); //$NON-NLS-1$

		fAddJavaDocStub= new AddJavaDocStubAction(editor);
		fAddJavaDocStub.setActionDefinitionId(IJavaEditorActionDefinitionIds.ADD_JAVADOC_COMMENT);
		editor.setAction("AddJavadocComment", fAddJavaDocStub); //$NON-NLS-1$

		fCleanUp= new AllCleanUpsAction(editor);
		fCleanUp.setActionDefinitionId(IJavaEditorActionDefinitionIds.CLEAN_UP);
		editor.setAction("CleanUp", fCleanUp); //$NON-NLS-1$

		fExternalizeStrings= new ExternalizeStringsAction(editor);
		fExternalizeStrings.setActionDefinitionId(IJavaEditorActionDefinitionIds.EXTERNALIZE_STRINGS);
		editor.setAction("ExternalizeStrings", fExternalizeStrings); //$NON-NLS-1$

		installQuickAccessAction();
	}

	/**
	 * Creates a new <code>GenerateActionGroup</code>. The group
	 * requires that the selection provided by the page's selection provider
	 * is of type <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param page the page that owns this action group
	 */
	public GenerateActionGroup(Page page) {
		this(page.getSite(), null);

		installQuickAccessAction();
	}

	/**
	 * Creates a new <code>GenerateActionGroup</code>. The group
	 * requires that the selection provided by the part's selection provider
	 * is of type <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param part the view part that owns this action group
	 */
	public GenerateActionGroup(IViewPart part) {
		this(part.getSite(), null);

		installQuickAccessAction();
	}

	/**
	 * Creates a new <code>GenerateActionGroup</code>. The group requires
	 * that the selection provided by the given selection provider is of type
	 * {@link IStructuredSelection}.
	 *
	 * @param site the site that will own the action group.
	 * @param selectionProvider the selection provider used instead of the
	 *  page selection provider.
	 *
	 * @since 3.4
	 */
	public GenerateActionGroup(IWorkbenchSite site, ISelectionProvider selectionProvider) {
		fSite= site;
		fSelectionProvider= selectionProvider == null ? fSite.getSelectionProvider() : selectionProvider;
		ISelection selection= fSelectionProvider.getSelection();

		fOverrideMethods= new OverrideMethodsAction(site);
		fOverrideMethods.setActionDefinitionId(IJavaEditorActionDefinitionIds.OVERRIDE_METHODS);

		fAddGetterSetter= new AddGetterSetterAction(site);
		fAddGetterSetter.setActionDefinitionId(IJavaEditorActionDefinitionIds.CREATE_GETTER_SETTER);

		fAddDelegateMethods= new AddDelegateMethodsAction(site);
		fAddDelegateMethods.setActionDefinitionId(IJavaEditorActionDefinitionIds.CREATE_DELEGATE_METHODS);

		fAddUnimplementedConstructors= new AddUnimplementedConstructorsAction(site);
		fAddUnimplementedConstructors.setActionDefinitionId(IJavaEditorActionDefinitionIds.ADD_UNIMPLEMENTED_CONTRUCTORS);

		fGenerateConstructorUsingFields= new GenerateNewConstructorUsingFieldsAction(site);
		fGenerateConstructorUsingFields.setActionDefinitionId(IJavaEditorActionDefinitionIds.GENERATE_CONSTRUCTOR_USING_FIELDS);

		fHashCodeEquals= new GenerateHashCodeEqualsAction(site);
		fHashCodeEquals.setActionDefinitionId(IJavaEditorActionDefinitionIds.GENERATE_HASHCODE_EQUALS);
		
		fToString= new GenerateToStringAction(site);
		fToString.setActionDefinitionId(IJavaEditorActionDefinitionIds.GENERATE_TOSTRING);

		fAddJavaDocStub= new AddJavaDocStubAction(site);
		fAddJavaDocStub.setActionDefinitionId(IJavaEditorActionDefinitionIds.ADD_JAVADOC_COMMENT);

		fAddBookmark= new AddBookmarkAction(site, true);
		fAddBookmark.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_ADD_BOOKMARK);

		// context-menu only -> no action definition ids

		fAddTaskAction= new AddTaskAction(site);
		fAddTaskAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_ADD_TASK);

		fExternalizeStrings= new ExternalizeStringsAction(site);
		fExternalizeStrings.setActionDefinitionId(IJavaEditorActionDefinitionIds.EXTERNALIZE_STRINGS);

		fFindNLSProblems= new FindBrokenNLSKeysAction(site);
		fFindNLSProblems.setActionDefinitionId(FindBrokenNLSKeysAction.FIND_BROKEN_NLS_KEYS_ACTION_ID);

		fOrganizeImports= new OrganizeImportsAction(site);
		fOrganizeImports.setActionDefinitionId(IJavaEditorActionDefinitionIds.ORGANIZE_IMPORTS);

		fSortMembers= new MultiSortMembersAction(site);
		fSortMembers.setActionDefinitionId(IJavaEditorActionDefinitionIds.SORT_MEMBERS);

		fFormatAll= new FormatAllAction(site);
		fFormatAll.setActionDefinitionId(IJavaEditorActionDefinitionIds.FORMAT);

		fCleanUp= new AllCleanUpsAction(site);
		fCleanUp.setActionDefinitionId(IJavaEditorActionDefinitionIds.CLEAN_UP);


		fOverrideMethods.update(selection);
		fAddGetterSetter.update(selection);
		fAddDelegateMethods.update(selection);
		fAddUnimplementedConstructors.update(selection);
		fGenerateConstructorUsingFields.update(selection);
		fHashCodeEquals.update(selection);
		fToString.update(selection);
		fAddJavaDocStub.update(selection);
		fExternalizeStrings.update(selection);
		fFindNLSProblems.update(selection);
		fCleanUp.update(selection);
		fAddTaskAction.update(selection);
		fOrganizeImports.update(selection);
		fSortMembers.update(selection);
		fFormatAll.update(selection);
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ss= (IStructuredSelection)selection;
			fAddBookmark.selectionChanged(ss);
		} else {
			fAddBookmark.setEnabled(false);
		}

		registerSelectionListener(fSelectionProvider, fAddBookmark);
		SelectionDispatchAction[] actions= new SelectionDispatchAction[] {
				fOverrideMethods,
				fAddGetterSetter,
				fAddDelegateMethods,
				fAddUnimplementedConstructors,
				fGenerateConstructorUsingFields,
				fHashCodeEquals,
				fToString,
				fAddJavaDocStub,
				fExternalizeStrings,
				fFindNLSProblems,
				fOrganizeImports,
				fFormatAll,
				fSortMembers,
				fAddTaskAction,
				fCleanUp
		};

		for (int i= 0; i < actions.length; i++) {
			SelectionDispatchAction action= actions[i];
			registerSelectionListener(fSelectionProvider, action);
			if (selectionProvider != null) {
				action.setSpecialSelectionProvider(fSelectionProvider);
			}
		}

		// FIXME, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=213335
		//installQuickAccessAction();
	}

	private void installQuickAccessAction() {
		fHandlerService= (IHandlerService)fSite.getService(IHandlerService.class);
		if (fHandlerService != null) {
			IHandler handler= new JDTQuickMenuCreator(fEditor) {
				@Override
				protected void fillMenu(IMenuManager menu) {
					fillQuickMenu(menu);
				}
			}.createHandler();
			fQuickAccessHandlerActivation= fHandlerService.activateHandler(QUICK_MENU_ID, handler);
		}
	}

	private void registerSelectionListener(ISelectionProvider provider, ISelectionChangedListener listener) {
		if (fRegisteredSelectionListeners == null)
			fRegisteredSelectionListeners= new ArrayList<ISelectionChangedListener>(20);
		provider.addSelectionChangedListener(listener);
		fRegisteredSelectionListeners.add(listener);
	}

	/*
	 * The state of the editor owning this action group has changed.
	 * This method does nothing if the group's owner isn't an
	 * editor.
	 */
	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 * 
	 * @noreference This method is not intended to be referenced by clients.
	 * @deprecated As of 3.5, this method is no longer called
	 */
	public void editorStateChanged() {
		Assert.isTrue(isEditorOwner());
	}

	/* (non-Javadoc)
	 * Method declared in ActionGroup
	 */
	@Override
	public void fillActionBars(IActionBars actionBar) {
		super.fillActionBars(actionBar);
		setGlobalActionHandlers(actionBar);
	}

	/* (non-Javadoc)
	 * Method declared in ActionGroup
	 */
	@Override
	public void fillContextMenu(IMenuManager menu) {
		super.fillContextMenu(menu);
		MenuManager subMenu= new MenuManager(ActionMessages.SourceMenu_label, MENU_ID);
		subMenu.setActionDefinitionId(QUICK_MENU_ID);
		int added= 0;
		if (isEditorOwner()) {
			added= fillEditorSubMenu(subMenu);
		} else {
			added= fillViewSubMenu(subMenu);
		}
		if (added > 0)
			menu.appendToGroup(fGroupName, subMenu);
	}

	private void fillQuickMenu(IMenuManager menu) {
		if (isEditorOwner()) {
			if (!fEditor.isBreadcrumbActive())
				fillEditorSubMenu(menu);
		} else {
			fillViewSubMenu(menu);
		}
	}

	private int fillEditorSubMenu(IMenuManager source) {
		int added= 0;
		source.add(new Separator(GROUP_COMMENT));
		added+= addEditorAction(source, "ToggleComment"); //$NON-NLS-1$
		added+= addEditorAction(source, "AddBlockComment"); //$NON-NLS-1$
		added+= addEditorAction(source, "RemoveBlockComment"); //$NON-NLS-1$
		added+= addAction(source, fAddJavaDocStub);
		source.add(new Separator(GROUP_EDIT));
		added+= addEditorAction(source, "Indent"); //$NON-NLS-1$
		added+= addEditorAction(source, "Format"); //$NON-NLS-1$
		added+= addEditorAction(source, "QuickFormat"); //$NON-NLS-1$
		source.add(new Separator(GROUP_IMPORT));
		added+= addAction(source, fAddImport);
		added+= addAction(source, fOrganizeImports);
		added+= addAction(source, fSortMembers);
		added+= addAction(source, fCleanUp);
		source.add(new Separator(GROUP_GENERATE));
		added+= addAction(source, fOverrideMethods);
		added+= addAction(source, fAddGetterSetter);
		added+= addAction(source, fAddDelegateMethods);
		added+= addAction(source, fHashCodeEquals);
		added+= addAction(source, fToString);
		added+= addAction(source, fGenerateConstructorUsingFields);
		added+= addAction(source, fAddUnimplementedConstructors);
		source.add(new Separator(GROUP_CODE));
		source.add(new Separator(GROUP_EXTERNALIZE));
		added+= addAction(source, fExternalizeStrings);
		return added;
	}

	private int fillViewSubMenu(IMenuManager source) {
		int added= 0;
		source.add(new Separator(GROUP_COMMENT));
		added+= addAction(source, fAddJavaDocStub);
		source.add(new Separator(GROUP_EDIT));
		added+= addAction(source, fFormatAll);
		source.add(new Separator(GROUP_IMPORT));
		added+= addAction(source, fAddImport);
		added+= addAction(source, fOrganizeImports);
		added+= addAction(source, fSortMembers);
		added+= addAction(source, fCleanUp);
		source.add(new Separator(GROUP_GENERATE));
		added+= addAction(source, fOverrideMethods);
		added+= addAction(source, fAddGetterSetter);
		added+= addAction(source, fAddDelegateMethods);
		added+= addAction(source, fHashCodeEquals);
		added+= addAction(source, fToString);
		added+= addAction(source, fGenerateConstructorUsingFields);
		added+= addAction(source, fAddUnimplementedConstructors);
		source.add(new Separator(GROUP_CODE));
		source.add(new Separator(GROUP_EXTERNALIZE));
		added+= addAction(source, fExternalizeStrings);
		added+= addAction(source, fFindNLSProblems);
		return added;
	}

	/* (non-Javadoc)
	 * Method declared in ActionGroup
	 */
	@Override
	public void dispose() {
		if (fRegisteredSelectionListeners != null) {
			ISelectionProvider provider= fSelectionProvider;
			for (Iterator<ISelectionChangedListener> iter= fRegisteredSelectionListeners.iterator(); iter.hasNext();) {
				ISelectionChangedListener listener= iter.next();
				provider.removeSelectionChangedListener(listener);
			}
		}
		if (fQuickAccessHandlerActivation != null && fHandlerService != null) {
			fHandlerService.deactivateHandler(fQuickAccessHandlerActivation);
		}
		fEditor= null;
		fCleanUp.dispose();
		super.dispose();
	}

	private void setGlobalActionHandlers(IActionBars actionBar) {
		actionBar.setGlobalActionHandler(JdtActionConstants.ADD_IMPORT, fAddImport);
		actionBar.setGlobalActionHandler(JdtActionConstants.OVERRIDE_METHODS, fOverrideMethods);
		actionBar.setGlobalActionHandler(JdtActionConstants.GENERATE_GETTER_SETTER, fAddGetterSetter);
		actionBar.setGlobalActionHandler(JdtActionConstants.GENERATE_DELEGATE_METHODS, fAddDelegateMethods);
		actionBar.setGlobalActionHandler(JdtActionConstants.ADD_CONSTRUCTOR_FROM_SUPERCLASS, fAddUnimplementedConstructors);
		actionBar.setGlobalActionHandler(JdtActionConstants.GENERATE_CONSTRUCTOR_USING_FIELDS, fGenerateConstructorUsingFields);
		actionBar.setGlobalActionHandler(JdtActionConstants.GENERATE_HASHCODE_EQUALS, fHashCodeEquals);
		actionBar.setGlobalActionHandler(JdtActionConstants.GENERATE_TOSTRING, fToString);
		actionBar.setGlobalActionHandler(JdtActionConstants.ADD_JAVA_DOC_COMMENT, fAddJavaDocStub);
		actionBar.setGlobalActionHandler(JdtActionConstants.EXTERNALIZE_STRINGS, fExternalizeStrings);
		actionBar.setGlobalActionHandler(JdtActionConstants.CLEAN_UP, fCleanUp);
		actionBar.setGlobalActionHandler(FindBrokenNLSKeysAction.ACTION_HANDLER_ID, fFindNLSProblems);
		actionBar.setGlobalActionHandler(JdtActionConstants.ORGANIZE_IMPORTS, fOrganizeImports);
		actionBar.setGlobalActionHandler(JdtActionConstants.SORT_MEMBERS, fSortMembers);
		if (!isEditorOwner()) {
			// editor provides its own implementation of these actions.
			actionBar.setGlobalActionHandler(IDEActionFactory.BOOKMARK.getId(), fAddBookmark);
			actionBar.setGlobalActionHandler(IDEActionFactory.ADD_TASK.getId(), fAddTaskAction);
			actionBar.setGlobalActionHandler(JdtActionConstants.FORMAT, fFormatAll);
		}
	}

	private int addAction(IMenuManager menu, IAction action) {
		if (action != null && action.isEnabled()) {
			menu.add(action);
			return 1;
		}
		return 0;
	}

	private int addEditorAction(IMenuManager menu, String actionID) {
		if (fEditor == null)
			return 0;
		IAction action= fEditor.getAction(actionID);
		if (action == null)
			return 0;
		if (action instanceof IUpdate)
			((IUpdate)action).update();
		if (action.isEnabled()) {
			menu.add(action);
			return 1;
		}
		return 0;
	}

	private boolean isEditorOwner() {
		return fEditor != null;
	}
}

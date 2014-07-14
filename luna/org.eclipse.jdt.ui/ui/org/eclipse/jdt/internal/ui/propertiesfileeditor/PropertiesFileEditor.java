/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Brock Janiczak <brockj@tpg.com.au> - [nls tooling] Properties file editor should have "toggle comment" action - https://bugs.eclipse.org/bugs/show_bug.cgi?id=192045
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.propertiesfileeditor;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IStorage;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.IShowInTargetList;

import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;

import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.editors.text.ITextEditorHelpContextIds;
import org.eclipse.ui.editors.text.TextEditor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds;
import org.eclipse.jdt.ui.actions.JdtActionConstants;
import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.FindBrokenNLSKeysAction;
import org.eclipse.jdt.internal.ui.javaeditor.ToggleCommentAction;


/**
 * Properties file editor.
 *
 * @since 3.1
 */
public class PropertiesFileEditor extends TextEditor {


	/** Open action. */
	protected OpenAction fOpenAction;

	/**
	 * Property change listener on Editors UI store.
	 * @since 3.7
	 */
	private IPropertyChangeListener fPropertyChangeListener;

	private Map<IEditorInput, IType> fAccessorTypes= new HashMap<IEditorInput, IType>();

	private Job fJob;

	private IFile fFile;

	/*
	 * @see org.eclipse.ui.editors.text.TextEditor#initializeEditor()
	 * @since 3.4
	 */
	@Override
	protected void initializeEditor() {
		setDocumentProvider(JavaPlugin.getDefault().getPropertiesFileDocumentProvider());
		IPreferenceStore store= JavaPlugin.getDefault().getCombinedPreferenceStore();
		setPreferenceStore(store);
		JavaTextTools textTools= JavaPlugin.getDefault().getJavaTextTools();
		setSourceViewerConfiguration(new PropertiesFileSourceViewerConfiguration(textTools.getColorManager(), store, this, IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING));
		setEditorContextMenuId("#TextEditorContext"); //$NON-NLS-1$
		setRulerContextMenuId("#TextRulerContext"); //$NON-NLS-1$
		setHelpContextId(ITextEditorHelpContextIds.TEXT_EDITOR);
		configureInsertMode(SMART_INSERT, false);
		setInsertMode(INSERT);

		// Need to listen on Editors UI preference store because JDT disables this functionality in its preferences.
		fPropertyChangeListener= new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				if (AbstractDecoratedTextEditorPreferenceConstants.EDITOR_SPACES_FOR_TABS.equals(event.getProperty()))
					handlePreferenceStoreChanged(event);
			}
		};
		EditorsUI.getPreferenceStore().addPropertyChangeListener(fPropertyChangeListener);
	}

	@Override
	protected void doSetInput(IEditorInput input) throws CoreException {
		super.doSetInput(input);
		if (fJob != null)
			fJob.cancel();

		fFile= (IFile) getEditorInput().getAdapter(IFile.class);
		if (fFile == null)
			return;

		if (fJob == null) {
			fJob= new Job(PropertiesFileEditorMessages.PropertiesFileEditor_find_accessor_type) {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						fAccessorTypes.put(getEditorInput(), findAccessorType(monitor));
					} catch (JavaModelException e) {
						JavaPlugin.log(e);
					}
					return Status.OK_STATUS;
				}
			};
			fJob.setSystem(true);
		}
		fJob.schedule();
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractDecoratedTextEditor#initializeKeyBindingScopes()
	 * @since 3.4
	 */
	@Override
	protected void initializeKeyBindingScopes() {
		setKeyBindingScopes(new String[] { "org.eclipse.jdt.ui.propertiesEditorScope" });  //$NON-NLS-1$
	}

	/*
	 * @see org.eclipse.ui.editors.text.TextEditor#createActions()
	 */
	@Override
	protected void createActions() {
		super.createActions();

		IAction action= new ToggleCommentAction(PropertiesFileEditorMessages.getBundleForConstructedKeys(), "ToggleComment.", this); //$NON-NLS-1$
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.TOGGLE_COMMENT);
		setAction(IJavaEditorActionDefinitionIds.TOGGLE_COMMENT, action);
		markAsStateDependentAction(IJavaEditorActionDefinitionIds.TOGGLE_COMMENT, true);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.TOGGLE_COMMENT_ACTION);
		configureToggleCommentAction();

		fOpenAction= new OpenAction(this);
		fOpenAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_EDITOR);
		setAction(JdtActionConstants.OPEN, fOpenAction);
	}

	/**
	 * Configures the toggle comment action.
	 *
	 * @since 3.4
	 */
	private void configureToggleCommentAction() {
		IAction action= getAction(IJavaEditorActionDefinitionIds.TOGGLE_COMMENT);
		if (action instanceof ToggleCommentAction) {
			ISourceViewer sourceViewer= getSourceViewer();
			SourceViewerConfiguration configuration= getSourceViewerConfiguration();
			((ToggleCommentAction)action).configure(sourceViewer, configuration);
		}
	}

	/*
	 * @see AbstractTextEditor#handlePreferenceStoreChanged(PropertyChangeEvent)
	 */
	@Override
	protected void handlePreferenceStoreChanged(PropertyChangeEvent event) {

		try {

			ISourceViewer sourceViewer= getSourceViewer();
			if (sourceViewer == null)
				return;

			((PropertiesFileSourceViewerConfiguration) getSourceViewerConfiguration()).handlePropertyChangeEvent(event);

		} finally {
			super.handlePreferenceStoreChanged(event);
		}
	}

	/*
	 * @see AbstractTextEditor#affectsTextPresentation(PropertyChangeEvent)
	 */
	@Override
	protected boolean affectsTextPresentation(PropertyChangeEvent event) {
		return ((PropertiesFileSourceViewerConfiguration)getSourceViewerConfiguration()).affectsTextPresentation(event) || super.affectsTextPresentation(event);
	}


	/*
	 * @see org.eclipse.ui.editors.text.TextEditor#getAdapter(java.lang.Class)
	 */
	@Override
	public Object getAdapter(Class adapter) {
		if (adapter == IShowInTargetList.class) {
			return new IShowInTargetList() {
				public String[] getShowInTargetIds() {
					return new String[] { JavaUI.ID_PACKAGES, JavaPlugin.ID_RES_NAV };
				}

			};
		}
		return super.getAdapter(adapter);
	}

	/*
	 * @see org.eclipse.ui.part.WorkbenchPart#getOrientation()
	 * @since 3.2
	 */
	@Override
	public int getOrientation() {
		return SWT.LEFT_TO_RIGHT;	// properties editors are always left to right by default (see https://bugs.eclipse.org/bugs/show_bug.cgi?id=110986)
	}

	/*
	 * @see org.eclipse.ui.texteditor.StatusTextEditor#updateStatusField(java.lang.String)
	 */
	@Override
	protected void updateStatusField(String category) {
		super.updateStatusField(category);
		if (getEditorSite() != null) {
			getEditorSite().getActionBars().getStatusLineManager().setMessage(null);
			getEditorSite().getActionBars().getStatusLineManager().setErrorMessage(null);
		}
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#getSourceViewer()
	 */
	ISourceViewer internalGetSourceViewer() {
		return getSourceViewer();
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractDecoratedTextEditor#collectContextMenuPreferencePages()
	 * @since 3.1
	 */
	@Override
	protected String[] collectContextMenuPreferencePages() {
		String[] ids= super.collectContextMenuPreferencePages();
		String[] more= new String[ids.length + 1];
		more[0]= "org.eclipse.jdt.ui.preferences.PropertiesFileEditorPreferencePage"; //$NON-NLS-1$
		System.arraycopy(ids, 0, more, 1, ids.length);
		return more;
	}

	/*
	 * @see org.eclipse.ui.editors.text.TextEditor#editorContextMenuAboutToShow(org.eclipse.jface.action.IMenuManager)
	 * @since 3.4
	 */
	@Override
	protected void editorContextMenuAboutToShow(IMenuManager menu) {
		super.editorContextMenuAboutToShow(menu);

		addAction(menu, ITextEditorActionConstants.GROUP_EDIT, IJavaEditorActionDefinitionIds.TOGGLE_COMMENT);
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractDecoratedTextEditor#isTabsToSpacesConversionEnabled()
	 * @since 3.7
	 */
	@Override
	protected boolean isTabsToSpacesConversionEnabled() {
		// Can't use our own preference store because JDT disables this functionality in its preferences.
		return EditorsUI.getPreferenceStore().getBoolean(AbstractDecoratedTextEditorPreferenceConstants.EDITOR_SPACES_FOR_TABS);
	}

	/*
	 * @see org.eclipse.ui.editors.text.TextEditor#dispose()
	 * @since 3.7
	 */
	@Override
	public void dispose() {
		EditorsUI.getPreferenceStore().removePropertyChangeListener(fPropertyChangeListener);
		if (fJob != null)
			fJob.cancel();
		super.dispose();
	}

	public IType getAccessorType() {
		if (fJob != null) {
			try {
				fJob.join();
			} catch (InterruptedException e) {
				JavaPlugin.log(e);
			}
		}
		return fAccessorTypes.get(getEditorInput());
	}

	private IType findAccessorType(IProgressMonitor pm) throws JavaModelException {
		IType accessorType= FindBrokenNLSKeysAction.getAccessorType(fFile);
		if (accessorType != null)
			return accessorType;
		if (pm != null && pm.isCanceled()) {
			return null;
		}

		IContainer parent= fFile.getParent();
		IJavaElement javaElement= JavaCore.create(parent);

		if (!(javaElement instanceof IPackageFragment))
			return null;

		ICompilationUnit[] compilationUnits= ((IPackageFragment) javaElement).getCompilationUnits();
		for (int i= 0; i < compilationUnits.length; i++) {
			if (evaluateCU(compilationUnits[i], fFile)) {
				return compilationUnits[i].getTypes()[0];
			}
			if (pm != null && pm.isCanceled()) {
				return null;
			}
		}
		return null;
	}

	private boolean evaluateCU(ICompilationUnit compilationUnit, IFile file) throws JavaModelException {
		IStorage bundle= FindBrokenNLSKeysAction.getResourceBundle(compilationUnit);
		if (!(bundle instanceof IFile))
			return false;

		return file.equals(bundle);
	}
}

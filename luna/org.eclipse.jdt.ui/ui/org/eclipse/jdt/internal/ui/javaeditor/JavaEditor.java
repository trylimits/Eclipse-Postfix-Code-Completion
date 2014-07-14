/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Tom Eicher <eclipse@tom.eicher.name> - [formatting] 'Format Element' in JavaDoc does also format method body - https://bugs.eclipse.org/bugs/show_bug.cgi?id=238746
 *     Tom Eicher (Avaloq Evolution AG) - block selection mode
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.javaeditor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.ibm.icu.text.BreakIterator;

import org.osgi.service.prefs.BackingStoreException;

import org.eclipse.help.IContextProvider;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ST;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.commands.operations.IOperationApprover;
import org.eclipse.core.commands.operations.IUndoContext;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ProjectScope;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IPostSelectionProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ISelectionValidator;
import org.eclipse.jface.text.ISynchronizable;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextInputListener;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension2;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.AnnotationRulerColumn;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.text.source.IAnnotationModelExtension2;
import org.eclipse.jface.text.source.ICharacterPairMatcher;
import org.eclipse.jface.text.source.IOverviewRuler;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.ISourceViewerExtension2;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.text.source.IVerticalRulerColumn;
import org.eclipse.jface.text.source.LineChangeHover;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.jface.text.source.projection.ProjectionSupport;
import org.eclipse.jface.text.source.projection.ProjectionViewer;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.operations.NonLocalUndoUserApprover;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.IShowInTargetList;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.views.contentoutline.ContentOutline;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import org.eclipse.ui.texteditor.AbstractDecoratedTextEditor;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.AnnotationPreference;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.IUpdate;
import org.eclipse.ui.texteditor.MarkerAnnotation;
import org.eclipse.ui.texteditor.MarkerAnnotationPreferences;
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport;
import org.eclipse.ui.texteditor.TextNavigationAction;
import org.eclipse.ui.texteditor.TextOperationAction;

import org.eclipse.ui.editors.text.DefaultEncodingSupport;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.editors.text.IEncodingSupport;

import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportContainer;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.core.util.IModifierConstants;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds;
import org.eclipse.jdt.ui.actions.JavaSearchActionGroup;
import org.eclipse.jdt.ui.actions.OpenEditorActionGroup;
import org.eclipse.jdt.ui.actions.OpenViewActionGroup;
import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jdt.ui.text.JavaTextTools;
import org.eclipse.jdt.ui.text.folding.IJavaFoldingStructureProvider;
import org.eclipse.jdt.ui.text.folding.IJavaFoldingStructureProviderExtension;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.actions.CopyQualifiedNameAction;
import org.eclipse.jdt.internal.ui.actions.FoldingActionGroup;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.breadcrumb.EditorBreadcrumb;
import org.eclipse.jdt.internal.ui.javaeditor.breadcrumb.IBreadcrumb;
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions.GoToNextPreviousMemberAction;
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions.SelectionHistory;
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions.StructureSelectEnclosingAction;
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions.StructureSelectHistoryAction;
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions.StructureSelectNextAction;
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions.StructureSelectPreviousAction;
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions.StructureSelectionAction;
import org.eclipse.jdt.internal.ui.search.BreakContinueTargetFinder;
import org.eclipse.jdt.internal.ui.search.ExceptionOccurrencesFinder;
import org.eclipse.jdt.internal.ui.search.IOccurrencesFinder;
import org.eclipse.jdt.internal.ui.search.IOccurrencesFinder.OccurrenceLocation;
import org.eclipse.jdt.internal.ui.search.ImplementOccurrencesFinder;
import org.eclipse.jdt.internal.ui.search.MethodExitsFinder;
import org.eclipse.jdt.internal.ui.search.OccurrencesFinder;
import org.eclipse.jdt.internal.ui.text.DocumentCharacterIterator;
import org.eclipse.jdt.internal.ui.text.JavaChangeHover;
import org.eclipse.jdt.internal.ui.text.JavaPairMatcher;
import org.eclipse.jdt.internal.ui.text.JavaWordFinder;
import org.eclipse.jdt.internal.ui.text.JavaWordIterator;
import org.eclipse.jdt.internal.ui.text.PreferencesAdapter;
import org.eclipse.jdt.internal.ui.text.java.hover.JavaExpandHover;
import org.eclipse.jdt.internal.ui.text.java.hover.SourceViewerInformationControl;
import org.eclipse.jdt.internal.ui.util.ElementValidator;
import org.eclipse.jdt.internal.ui.util.JavaUIHelp;
import org.eclipse.jdt.internal.ui.viewsupport.ISelectionListenerWithAST;
import org.eclipse.jdt.internal.ui.viewsupport.IViewPartInputProvider;
import org.eclipse.jdt.internal.ui.viewsupport.SelectionListenerWithASTManager;


/**
 * Java specific text editor.
 */
public abstract class JavaEditor extends AbstractDecoratedTextEditor implements IViewPartInputProvider {

	/**
	 * Internal implementation class for a change listener.
	 * @since 3.0
	 */
	protected abstract class AbstractSelectionChangedListener implements ISelectionChangedListener  {

		/**
		 * Installs this selection changed listener with the given selection provider. If
		 * the selection provider is a post selection provider, post selection changed
		 * events are the preferred choice, otherwise normal selection changed events
		 * are requested.
		 *
		 * @param selectionProvider the selection provider
		 */
		public void install(ISelectionProvider selectionProvider) {
			if (selectionProvider == null)
				return;

			if (selectionProvider instanceof IPostSelectionProvider)  {
				IPostSelectionProvider provider= (IPostSelectionProvider) selectionProvider;
				provider.addPostSelectionChangedListener(this);
			} else  {
				selectionProvider.addSelectionChangedListener(this);
			}
		}

		/**
		 * Removes this selection changed listener from the given selection provider.
		 *
		 * @param selectionProvider the selection provider
		 */
		public void uninstall(ISelectionProvider selectionProvider) {
			if (selectionProvider == null)
				return;

			if (selectionProvider instanceof IPostSelectionProvider)  {
				IPostSelectionProvider provider= (IPostSelectionProvider) selectionProvider;
				provider.removePostSelectionChangedListener(this);
			} else  {
				selectionProvider.removeSelectionChangedListener(this);
			}
		}
	}

	/**
	 * Updates the Java outline page selection and this editor's range indicator.
	 *
	 * @since 3.0
	 */
	private class EditorSelectionChangedListener extends AbstractSelectionChangedListener {

		/*
		 * @see org.eclipse.jface.viewers.ISelectionChangedListener#selectionChanged(org.eclipse.jface.viewers.SelectionChangedEvent)
		 */
		public void selectionChanged(SelectionChangedEvent event) {
			// XXX: see https://bugs.eclipse.org/bugs/show_bug.cgi?id=56161
			JavaEditor.this.selectionChanged();
		}
	}


	/**
	 * Adapts an options {@link IEclipsePreferences} to {@link org.eclipse.jface.preference.IPreferenceStore}.
	 * <p>
	 * This preference store is read-only i.e. write access
	 * throws an {@link java.lang.UnsupportedOperationException}.
	 * </p>
	 *
	 * @since 3.1
	 */
	private static class EclipsePreferencesAdapter implements IPreferenceStore {

		/**
		 * Preference change listener. Listens for events preferences
		 * fires a {@link org.eclipse.jface.util.PropertyChangeEvent}
		 * on this adapter with arguments from the received event.
		 */
		private class PreferenceChangeListener implements IEclipsePreferences.IPreferenceChangeListener {

			/**
			 * {@inheritDoc}
			 */
			public void preferenceChange(final IEclipsePreferences.PreferenceChangeEvent event) {
				if (Display.getCurrent() == null) {
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							firePropertyChangeEvent(event.getKey(), event.getOldValue(), event.getNewValue());
						}
					});
				} else {
					firePropertyChangeEvent(event.getKey(), event.getOldValue(), event.getNewValue());
				}
			}
		}

		/** Listeners on on this adapter */
		private ListenerList fListeners= new ListenerList(ListenerList.IDENTITY);

		/** Listener on the node */
		private IEclipsePreferences.IPreferenceChangeListener fListener= new PreferenceChangeListener();

		/** wrapped node */
		private final IScopeContext fContext;
		private final String fQualifier;

		/**
		 * Initialize with the node to wrap
		 *
		 * @param context the context to access
		 * @param qualifier the qualifier
		 */
		public EclipsePreferencesAdapter(IScopeContext context, String qualifier) {
			fContext= context;
			fQualifier= qualifier;
		}

		private IEclipsePreferences getNode() {
			return fContext.getNode(fQualifier);
		}

		/**
		 * {@inheritDoc}
		 */
		public void addPropertyChangeListener(IPropertyChangeListener listener) {
			if (fListeners.size() == 0)
				getNode().addPreferenceChangeListener(fListener);
			fListeners.add(listener);
		}

		/**
		 * {@inheritDoc}
		 */
		public void removePropertyChangeListener(IPropertyChangeListener listener) {
			fListeners.remove(listener);
			if (fListeners.size() == 0) {
				getNode().removePreferenceChangeListener(fListener);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		public boolean contains(String name) {
			return getNode().get(name, null) != null;
		}

		/**
		 * {@inheritDoc}
		 */
		public void firePropertyChangeEvent(String name, Object oldValue, Object newValue) {
			PropertyChangeEvent event= new PropertyChangeEvent(this, name, oldValue, newValue);
			Object[] listeners= fListeners.getListeners();
			for (int i= 0; i < listeners.length; i++)
				((IPropertyChangeListener) listeners[i]).propertyChange(event);
		}

		/**
		 * {@inheritDoc}
		 */
		public boolean getBoolean(String name) {
			return getNode().getBoolean(name, BOOLEAN_DEFAULT_DEFAULT);
		}

		/**
		 * {@inheritDoc}
		 */
		public boolean getDefaultBoolean(String name) {
			return BOOLEAN_DEFAULT_DEFAULT;
		}

		/**
		 * {@inheritDoc}
		 */
		public double getDefaultDouble(String name) {
			return DOUBLE_DEFAULT_DEFAULT;
		}

		/**
		 * {@inheritDoc}
		 */
		public float getDefaultFloat(String name) {
			return FLOAT_DEFAULT_DEFAULT;
		}

		/**
		 * {@inheritDoc}
		 */
		public int getDefaultInt(String name) {
			return INT_DEFAULT_DEFAULT;
		}

		/**
		 * {@inheritDoc}
		 */
		public long getDefaultLong(String name) {
			return LONG_DEFAULT_DEFAULT;
		}

		/**
		 * {@inheritDoc}
		 */
		public String getDefaultString(String name) {
			return STRING_DEFAULT_DEFAULT;
		}

		/**
		 * {@inheritDoc}
		 */
		public double getDouble(String name) {
			return getNode().getDouble(name, DOUBLE_DEFAULT_DEFAULT);
		}

		/**
		 * {@inheritDoc}
		 */
		public float getFloat(String name) {
			return getNode().getFloat(name, FLOAT_DEFAULT_DEFAULT);
		}

		/**
		 * {@inheritDoc}
		 */
		public int getInt(String name) {
			return getNode().getInt(name, INT_DEFAULT_DEFAULT);
		}

		/**
		 * {@inheritDoc}
		 */
		public long getLong(String name) {
			return getNode().getLong(name, LONG_DEFAULT_DEFAULT);
		}

		/**
		 * {@inheritDoc}
		 */
		public String getString(String name) {
			return getNode().get(name, STRING_DEFAULT_DEFAULT);
		}

		/**
		 * {@inheritDoc}
		 */
		public boolean isDefault(String name) {
			return false;
		}

		/**
		 * {@inheritDoc}
		 */
		public boolean needsSaving() {
			try {
				return getNode().keys().length > 0;
			} catch (BackingStoreException e) {
				// ignore
			}
			return true;
		}

		/**
		 * {@inheritDoc}
		 */
		public void putValue(String name, String value) {
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		public void setDefault(String name, double value) {
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		public void setDefault(String name, float value) {
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		public void setDefault(String name, int value) {
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		public void setDefault(String name, long value) {
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		public void setDefault(String name, String defaultObject) {
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		public void setDefault(String name, boolean value) {
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		public void setToDefault(String name) {
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		public void setValue(String name, double value) {
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		public void setValue(String name, float value) {
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		public void setValue(String name, int value) {
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		public void setValue(String name, long value) {
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		public void setValue(String name, String value) {
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		public void setValue(String name, boolean value) {
			throw new UnsupportedOperationException();
		}

	}


	/**
	 * Cancels the occurrences finder job upon document changes.
	 *
	 * @since 3.0
	 */
	class OccurrencesFinderJobCanceler implements IDocumentListener, ITextInputListener {

		public void install() {
			ISourceViewer sourceViewer= getSourceViewer();
			if (sourceViewer == null)
				return;

			StyledText text= sourceViewer.getTextWidget();
			if (text == null || text.isDisposed())
				return;

			sourceViewer.addTextInputListener(this);

			IDocument document= sourceViewer.getDocument();
			if (document != null)
				document.addDocumentListener(this);
		}

		public void uninstall() {
			ISourceViewer sourceViewer= getSourceViewer();
			if (sourceViewer != null)
				sourceViewer.removeTextInputListener(this);

			IDocumentProvider documentProvider= getDocumentProvider();
			if (documentProvider != null) {
				IDocument document= documentProvider.getDocument(getEditorInput());
				if (document != null)
					document.removeDocumentListener(this);
			}
		}


		/*
		 * @see org.eclipse.jface.text.IDocumentListener#documentAboutToBeChanged(org.eclipse.jface.text.DocumentEvent)
		 */
		public void documentAboutToBeChanged(DocumentEvent event) {
			if (fOccurrencesFinderJob != null)
				fOccurrencesFinderJob.doCancel();
		}

		/*
		 * @see org.eclipse.jface.text.IDocumentListener#documentChanged(org.eclipse.jface.text.DocumentEvent)
		 */
		public void documentChanged(DocumentEvent event) {
		}

		/*
		 * @see org.eclipse.jface.text.ITextInputListener#inputDocumentAboutToBeChanged(org.eclipse.jface.text.IDocument, org.eclipse.jface.text.IDocument)
		 */
		public void inputDocumentAboutToBeChanged(IDocument oldInput, IDocument newInput) {
			if (oldInput == null)
				return;

			oldInput.removeDocumentListener(this);
		}

		/*
		 * @see org.eclipse.jface.text.ITextInputListener#inputDocumentChanged(org.eclipse.jface.text.IDocument, org.eclipse.jface.text.IDocument)
		 */
		public void inputDocumentChanged(IDocument oldInput, IDocument newInput) {
			if (newInput == null)
				return;
			newInput.addDocumentListener(this);
		}
	}

	/**
	 * This action implements smart home.
	 *
	 * Instead of going to the start of a line it does the following:
	 *
	 * - if smart home/end is enabled and the caret is after the line's first non-whitespace then the caret is moved directly before it, taking JavaDoc and multi-line comments into account.
	 * - if the caret is before the line's first non-whitespace the caret is moved to the beginning of the line
	 * - if the caret is at the beginning of the line see first case.
	 *
	 * @since 3.0
	 */
	protected class SmartLineStartAction extends LineStartAction {

		/**
		 * Creates a new smart line start action
		 *
		 * @param textWidget the styled text widget
		 * @param doSelect a boolean flag which tells if the text up to the beginning of the line should be selected
		 */
		public SmartLineStartAction(final StyledText textWidget, final boolean doSelect) {
			super(textWidget, doSelect);
		}

		/*
		 * @see org.eclipse.ui.texteditor.AbstractTextEditor.LineStartAction#getLineStartPosition(java.lang.String, int, java.lang.String)
		 */
		@Override
		protected int getLineStartPosition(final IDocument document, final String line, final int length, final int offset) {

			String type= IDocument.DEFAULT_CONTENT_TYPE;
			try {
				type= TextUtilities.getContentType(document, IJavaPartitions.JAVA_PARTITIONING, offset, true);
			} catch (BadLocationException exception) {
				// Should not happen
			}

			int index= super.getLineStartPosition(document, line, length, offset);
			if (type.equals(IJavaPartitions.JAVA_DOC) || type.equals(IJavaPartitions.JAVA_MULTI_LINE_COMMENT)) {
				if (index < length - 1 && line.charAt(index) == '*' && line.charAt(index + 1) != '/') {
					do {
						++index;
					} while (index < length && Character.isWhitespace(line.charAt(index)));
				}
			} else {
				if (index < length - 1 && line.charAt(index) == '/' && line.charAt(index + 1) == '/') {
					index++;
					do {
						++index;
					} while (index < length && Character.isWhitespace(line.charAt(index)));
				}
			}
			return index;
		}
	}

	/**
	 * Text navigation action to navigate to the next sub-word.
	 *
	 * @since 3.0
	 */
	protected abstract class NextSubWordAction extends TextNavigationAction {

		protected JavaWordIterator fIterator= new JavaWordIterator();

		/**
		 * Creates a new next sub-word action.
		 *
		 * @param code Action code for the default operation. Must be an action code from @see org.eclipse.swt.custom.ST.
		 */
		protected NextSubWordAction(int code) {
			super(getSourceViewer().getTextWidget(), code);
		}

		/*
		 * @see org.eclipse.jface.action.IAction#run()
		 */
		@Override
		public void run() {
			// Check whether we are in a java code partition and the preference is enabled
			final IPreferenceStore store= getPreferenceStore();
			if (!store.getBoolean(PreferenceConstants.EDITOR_SUB_WORD_NAVIGATION)) {
				super.run();
				return;
			}

			final ISourceViewer viewer= getSourceViewer();
			final IDocument document= viewer.getDocument();
			try {
				fIterator.setText((CharacterIterator)new DocumentCharacterIterator(document));
				int position= widgetOffset2ModelOffset(viewer, viewer.getTextWidget().getCaretOffset());
				if (position == -1)
					return;

				int next= findNextPosition(position);
				if (isBlockSelectionModeEnabled() && document.getLineOfOffset(next) != document.getLineOfOffset(position)) {
					super.run(); // may navigate into virtual white space
				} else if (next != BreakIterator.DONE) {
					setCaretPosition(next);
					getTextWidget().showSelection();
					fireSelectionChanged();
				}
			} catch (BadLocationException x) {
				// ignore
			}
		}

		/**
		 * Finds the next position after the given position.
		 *
		 * @param position the current position
		 * @return the next position
		 */
		protected int findNextPosition(int position) {
			ISourceViewer viewer= getSourceViewer();
			int widget= -1;
			int next= position;
			while (next != BreakIterator.DONE && widget == -1) { // XXX: optimize
				next= fIterator.following(next);
				if (next != BreakIterator.DONE)
					widget= modelOffset2WidgetOffset(viewer, next);
			}

			IDocument document= viewer.getDocument();
			LinkedModeModel model= LinkedModeModel.getModel(document, position);
			if (model != null && next != BreakIterator.DONE) {
				LinkedPosition linkedPosition= model.findPosition(new LinkedPosition(document, position, 0));
				if (linkedPosition != null) {
					int linkedPositionEnd= linkedPosition.getOffset() + linkedPosition.getLength();
					if (position != linkedPositionEnd && linkedPositionEnd < next)
						next= linkedPositionEnd;
				} else {
					LinkedPosition nextLinkedPosition= model.findPosition(new LinkedPosition(document, next, 0));
					if (nextLinkedPosition != null) {
						int nextLinkedPositionOffset= nextLinkedPosition.getOffset();
						if (position != nextLinkedPositionOffset && nextLinkedPositionOffset < next)
							next= nextLinkedPositionOffset;
					}
				}
			}

			return next;
		}

		/**
		 * Sets the caret position to the sub-word boundary given with <code>position</code>.
		 *
		 * @param position Position where the action should move the caret
		 */
		protected abstract void setCaretPosition(int position);
	}

	/**
	 * Text navigation action to navigate to the next sub-word.
	 *
	 * @since 3.0
	 */
	protected class NavigateNextSubWordAction extends NextSubWordAction {

		/**
		 * Creates a new navigate next sub-word action.
		 */
		public NavigateNextSubWordAction() {
			super(ST.WORD_NEXT);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.JavaEditor.NextSubWordAction#setCaretPosition(int)
		 */
		@Override
		protected void setCaretPosition(final int position) {
			getTextWidget().setCaretOffset(modelOffset2WidgetOffset(getSourceViewer(), position));
		}
	}

	/**
	 * Text operation action to delete the next sub-word.
	 *
	 * @since 3.0
	 */
	protected class DeleteNextSubWordAction extends NextSubWordAction implements IUpdate {

		/**
		 * Creates a new delete next sub-word action.
		 */
		public DeleteNextSubWordAction() {
			super(ST.DELETE_WORD_NEXT);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.JavaEditor.NextSubWordAction#setCaretPosition(int)
		 */
		@Override
		protected void setCaretPosition(final int position) {
			if (!validateEditorInputState())
				return;

			final ISourceViewer viewer= getSourceViewer();
			StyledText text= viewer.getTextWidget();
			Point widgetSelection= text.getSelection();
			if (isBlockSelectionModeEnabled() && widgetSelection.y != widgetSelection.x) {
				final int caret= text.getCaretOffset();
				final int offset= modelOffset2WidgetOffset(viewer, position);

				if (caret == widgetSelection.x)
					text.setSelectionRange(widgetSelection.y, offset - widgetSelection.y);
				else
					text.setSelectionRange(widgetSelection.x, offset - widgetSelection.x);
				text.invokeAction(ST.DELETE_NEXT);
			} else {
				Point selection= viewer.getSelectedRange();
				final int caret, length;
				if (selection.y != 0) {
					caret= selection.x;
					length= selection.y;
				} else {
					caret= widgetOffset2ModelOffset(viewer, text.getCaretOffset());
					length= position - caret;
				}

				try {
					viewer.getDocument().replace(caret, length, ""); //$NON-NLS-1$
				} catch (BadLocationException exception) {
					// Should not happen
				}
			}
		}

		/*
		 * @see org.eclipse.ui.texteditor.IUpdate#update()
		 */
		public void update() {
			setEnabled(isEditorInputModifiable());
		}
	}

	/**
	 * Text operation action to select the next sub-word.
	 *
	 * @since 3.0
	 */
	protected class SelectNextSubWordAction extends NextSubWordAction {

		/**
		 * Creates a new select next sub-word action.
		 */
		public SelectNextSubWordAction() {
			super(ST.SELECT_WORD_NEXT);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.JavaEditor.NextSubWordAction#setCaretPosition(int)
		 */
		@Override
		protected void setCaretPosition(final int position) {
			final ISourceViewer viewer= getSourceViewer();

			final StyledText text= viewer.getTextWidget();
			if (text != null && !text.isDisposed()) {

				final Point selection= text.getSelection();
				final int caret= text.getCaretOffset();
				final int offset= modelOffset2WidgetOffset(viewer, position);

				if (caret == selection.x)
					text.setSelectionRange(selection.y, offset - selection.y);
				else
					text.setSelectionRange(selection.x, offset - selection.x);
			}
		}
	}

	/**
	 * Text navigation action to navigate to the previous sub-word.
	 *
	 * @since 3.0
	 */
	protected abstract class PreviousSubWordAction extends TextNavigationAction {

		protected JavaWordIterator fIterator= new JavaWordIterator();

		/**
		 * Creates a new previous sub-word action.
		 *
		 * @param code Action code for the default operation. Must be an action code from @see org.eclipse.swt.custom.ST.
		 */
		protected PreviousSubWordAction(final int code) {
			super(getSourceViewer().getTextWidget(), code);
		}

		/*
		 * @see org.eclipse.jface.action.IAction#run()
		 */
		@Override
		public void run() {
			// Check whether we are in a java code partition and the preference is enabled
			final IPreferenceStore store= getPreferenceStore();
			if (!store.getBoolean(PreferenceConstants.EDITOR_SUB_WORD_NAVIGATION)) {
				super.run();
				return;
			}

			final ISourceViewer viewer= getSourceViewer();
			final IDocument document= viewer.getDocument();
			try {
				fIterator.setText((CharacterIterator)new DocumentCharacterIterator(document));
				int position= widgetOffset2ModelOffset(viewer, viewer.getTextWidget().getCaretOffset());
				if (position == -1)
					return;

				int previous= findPreviousPosition(position);
				if (isBlockSelectionModeEnabled() && document.getLineOfOffset(previous) != document.getLineOfOffset(position)) {
					super.run(); // may navigate into virtual white space
				} else if (previous != BreakIterator.DONE) {
					setCaretPosition(previous);
					getTextWidget().showSelection();
					fireSelectionChanged();
				}
			} catch (BadLocationException x) {
				// ignore - getLineOfOffset failed
			}

		}

		/**
		 * Finds the previous position before the given position.
		 *
		 * @param position the current position
		 * @return the previous position
		 */
		protected int findPreviousPosition(int position) {
			ISourceViewer viewer= getSourceViewer();
			int widget= -1;
			int previous= position;
			while (previous != BreakIterator.DONE && widget == -1) { // XXX: optimize
				previous= fIterator.preceding(previous);
				if (previous != BreakIterator.DONE)
					widget= modelOffset2WidgetOffset(viewer, previous);
			}

			IDocument document= viewer.getDocument();
			LinkedModeModel model= LinkedModeModel.getModel(document, position);
			if (model != null && previous != BreakIterator.DONE) {
				LinkedPosition linkedPosition= model.findPosition(new LinkedPosition(document, position, 0));
				if (linkedPosition != null) {
					int linkedPositionOffset= linkedPosition.getOffset();
					if (position != linkedPositionOffset && previous < linkedPositionOffset)
						previous= linkedPositionOffset;
				} else {
					LinkedPosition previousLinkedPosition= model.findPosition(new LinkedPosition(document, previous, 0));
					if (previousLinkedPosition != null) {
						int previousLinkedPositionEnd= previousLinkedPosition.getOffset() + previousLinkedPosition.getLength();
						if (position != previousLinkedPositionEnd && previous < previousLinkedPositionEnd)
							previous= previousLinkedPositionEnd;
					}
				}
			}

			return previous;
		}

		/**
		 * Sets the caret position to the sub-word boundary given with <code>position</code>.
		 *
		 * @param position Position where the action should move the caret
		 */
		protected abstract void setCaretPosition(int position);
	}

	/**
	 * Text navigation action to navigate to the previous sub-word.
	 *
	 * @since 3.0
	 */
	protected class NavigatePreviousSubWordAction extends PreviousSubWordAction {

		/**
		 * Creates a new navigate previous sub-word action.
		 */
		public NavigatePreviousSubWordAction() {
			super(ST.WORD_PREVIOUS);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.JavaEditor.PreviousSubWordAction#setCaretPosition(int)
		 */
		@Override
		protected void setCaretPosition(final int position) {
			getTextWidget().setCaretOffset(modelOffset2WidgetOffset(getSourceViewer(), position));
		}
	}

	/**
	 * Text operation action to delete the previous sub-word.
	 *
	 * @since 3.0
	 */
	protected class DeletePreviousSubWordAction extends PreviousSubWordAction implements IUpdate {

		/**
		 * Creates a new delete previous sub-word action.
		 */
		public DeletePreviousSubWordAction() {
			super(ST.DELETE_WORD_PREVIOUS);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.JavaEditor.PreviousSubWordAction#setCaretPosition(int)
		 */
		@Override
		protected void setCaretPosition(int position) {
			if (!validateEditorInputState())
				return;

			final int length;
			final ISourceViewer viewer= getSourceViewer();
			StyledText text= viewer.getTextWidget();
			Point widgetSelection= text.getSelection();
			if (isBlockSelectionModeEnabled() && widgetSelection.y != widgetSelection.x) {
				final int caret= text.getCaretOffset();
				final int offset= modelOffset2WidgetOffset(viewer, position);

				if (caret == widgetSelection.x)
					text.setSelectionRange(widgetSelection.y, offset - widgetSelection.y);
				else
					text.setSelectionRange(widgetSelection.x, offset - widgetSelection.x);
				text.invokeAction(ST.DELETE_PREVIOUS);
			} else {
				Point selection= viewer.getSelectedRange();
				if (selection.y != 0) {
					position= selection.x;
					length= selection.y;
				} else {
					length= widgetOffset2ModelOffset(viewer, text.getCaretOffset()) - position;
				}

				try {
					viewer.getDocument().replace(position, length, ""); //$NON-NLS-1$
				} catch (BadLocationException exception) {
					// Should not happen
				}
			}
		}

		/*
		 * @see org.eclipse.ui.texteditor.IUpdate#update()
		 */
		public void update() {
			setEnabled(isEditorInputModifiable());
		}
	}

	/**
	 * Text operation action to select the previous sub-word.
	 *
	 * @since 3.0
	 */
	protected class SelectPreviousSubWordAction extends PreviousSubWordAction {

		/**
		 * Creates a new select previous sub-word action.
		 */
		public SelectPreviousSubWordAction() {
			super(ST.SELECT_WORD_PREVIOUS);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.javaeditor.JavaEditor.PreviousSubWordAction#setCaretPosition(int)
		 */
		@Override
		protected void setCaretPosition(final int position) {
			final ISourceViewer viewer= getSourceViewer();

			final StyledText text= viewer.getTextWidget();
			if (text != null && !text.isDisposed()) {

				final Point selection= text.getSelection();
				final int caret= text.getCaretOffset();
				final int offset= modelOffset2WidgetOffset(viewer, position);

				if (caret == selection.x)
					text.setSelectionRange(selection.y, offset - selection.y);
				else
					text.setSelectionRange(selection.x, offset - selection.x);
			}
		}
	}

	/**
	 * Format element action to format the enclosing java element.
	 * <p>
	 * The format element action works as follows:
	 * <ul>
	 * <li>If there is no selection and the caret is positioned on a Java element,
	 * only this element is formatted. If the element has some accompanying comment,
	 * then the comment is formatted as well.</li>
	 * <li>If there is no selection and the caret is positioned within a comment
	 * (javadoc, multi-line or single-line), then only the comment is formatted, but
	 * not its enclosing Java element.</li>
	 * <li>If the selection spans one or more partitions of the document, then all
	 * partitions covered by the selection are entirely formatted.</li>
	 * <p>
	 * Partitions at the end of the selection are not completed, except for comments.
	 *
	 * @since 3.0
	 */
	protected class FormatElementAction extends Action implements IUpdate {

		/*
		 * @since 3.2
		 */
		FormatElementAction() {
			setText(JavaEditorMessages.JavaEditor_FormatElementAction_label);
			setDescription(JavaEditorMessages.JavaEditor_FormatElementAction_description);
			setEnabled(isEditorInputModifiable());
		}

		/*
		 * @see org.eclipse.jface.action.IAction#run()
		 */
		@Override
		public void run() {

			final JavaSourceViewer viewer= (JavaSourceViewer) getSourceViewer();
			if (viewer.isEditable() && ElementValidator.check(getInputJavaElement(), getSite().getShell(), JavaEditorMessages.JavaEditor_FormatElementDialog_label, true)) {

				final Point selection= viewer.rememberSelection();
				try {
					viewer.setRedraw(false);

					boolean emptySelection= selection.y == 0;
					if (emptySelection) {
						final ITypedRegion partition= TextUtilities.getPartition(viewer.getDocument(), IJavaPartitions.JAVA_PARTITIONING, selection.x, true);
						String type= partition.getType();
						if (IJavaPartitions.JAVA_DOC.equals(type) || IJavaPartitions.JAVA_MULTI_LINE_COMMENT.equals(type) || IJavaPartitions.JAVA_SINGLE_LINE_COMMENT.equals(type)) {
							viewer.setSelectedRange(partition.getOffset(), partition.getLength());
							viewer.doOperation(ISourceViewer.FORMAT);
							return;
						}
					}
					final IJavaElement element= getElementAt(selection.x, true);
					if (element != null && element.exists()) {
						try {
							final int kind= element.getElementType();
							if (kind == IJavaElement.TYPE || kind == IJavaElement.METHOD || kind == IJavaElement.INITIALIZER) {

								final ISourceReference reference= (ISourceReference) element;
								final ISourceRange range= reference.getSourceRange();
								final ISourceRange nameRange= reference.getNameRange();
								final boolean seletionInNameRange= nameRange != null && selection.x >= nameRange.getOffset()
										&& selection.x + selection.y <= nameRange.getOffset() + nameRange.getLength();
								if (range != null && (emptySelection || seletionInNameRange))
									viewer.setSelectedRange(range.getOffset(), range.getLength());
							}
						} catch (JavaModelException exception) {
							// Should not happen
						}
					}
					viewer.doOperation(ISourceViewer.FORMAT);
				} catch (BadLocationException e) {
					// Cannot happen
				} finally {

					viewer.setRedraw(true);
					viewer.restoreSelection();
				}
			}
		}

		/*
		 * @see org.eclipse.ui.texteditor.IUpdate#update()
		 * @since 3.2
		 */
		public void update() {
			setEnabled(isEditorInputModifiable());
		}
	}

	/**
	 * Internal activation listener.
	 * @since 3.0
	 */
	private class ActivationListener implements IWindowListener {

		/*
		 * @see org.eclipse.ui.IWindowListener#windowActivated(org.eclipse.ui.IWorkbenchWindow)
		 * @since 3.1
		 */
		public void windowActivated(IWorkbenchWindow window) {
			if (window == getEditorSite().getWorkbenchWindow() && fMarkOccurrenceAnnotations && isActivePart()) {
				fForcedMarkOccurrencesSelection= getSelectionProvider().getSelection();
				ITypeRoot inputJavaElement= getInputJavaElement();
				if (inputJavaElement != null)
					updateOccurrenceAnnotations((ITextSelection)fForcedMarkOccurrencesSelection, SharedASTProvider.getAST(inputJavaElement, SharedASTProvider.WAIT_NO, getProgressMonitor()));
			}
		}

		/*
		 * @see org.eclipse.ui.IWindowListener#windowDeactivated(org.eclipse.ui.IWorkbenchWindow)
		 * @since 3.1
		 */
		public void windowDeactivated(IWorkbenchWindow window) {
			if (window == getEditorSite().getWorkbenchWindow() && fMarkOccurrenceAnnotations && isActivePart())
				removeOccurrenceAnnotations();
		}

		/*
		 * @see org.eclipse.ui.IWindowListener#windowClosed(org.eclipse.ui.IWorkbenchWindow)
		 * @since 3.1
		 */
		public void windowClosed(IWorkbenchWindow window) {
		}

		/*
		 * @see org.eclipse.ui.IWindowListener#windowOpened(org.eclipse.ui.IWorkbenchWindow)
		 * @since 3.1
		 */
		public void windowOpened(IWorkbenchWindow window) {
		}
	}

	/**
	 * Runner that will toggle folding either instantly (if the editor is
	 * visible) or the next time it becomes visible. If a runner is started when
	 * there is already one registered, the registered one is canceled as
	 * toggling folding twice is a no-op.
	 * <p>
	 * The access to the fFoldingRunner field is not thread-safe, it is assumed
	 * that <code>runWhenNextVisible</code> is only called from the UI thread.
	 * </p>
	 *
	 * @since 3.1
	 */
	private final class ToggleFoldingRunner implements IPartListener2 {
		/**
		 * The workbench page we registered the part listener with, or
		 * <code>null</code>.
		 */
		private IWorkbenchPage fPage;

		/**
		 * Does the actual toggling of projection.
		 */
		private void toggleFolding() {
			ISourceViewer sourceViewer= getSourceViewer();
			if (sourceViewer instanceof ProjectionViewer) {
				ProjectionViewer pv= (ProjectionViewer) sourceViewer;
				if (pv.isProjectionMode() != isFoldingEnabled()) {
					if (pv.canDoOperation(ProjectionViewer.TOGGLE))
						pv.doOperation(ProjectionViewer.TOGGLE);
				}
			}
		}

		/**
		 * Makes sure that the editor's folding state is correct the next time
		 * it becomes visible. If it already is visible, it toggles the folding
		 * state. If not, it either registers a part listener to toggle folding
		 * when the editor becomes visible, or cancels an already registered
		 * runner.
		 */
		public void runWhenNextVisible() {
			// if there is one already: toggling twice is the identity
			if (fFoldingRunner != null) {
				fFoldingRunner.cancel();
				return;
			}
			IWorkbenchPartSite site= getSite();
			if (site != null) {
				IWorkbenchPage page= site.getPage();
				if (!page.isPartVisible(JavaEditor.this)) {
					// if we're not visible - defer until visible
					fPage= page;
					fFoldingRunner= this;
					page.addPartListener(this);
					return;
				}
			}
			// we're visible - run now
			toggleFolding();
		}

		/**
		 * Remove the listener and clear the field.
		 */
		private void cancel() {
			if (fPage != null) {
				fPage.removePartListener(this);
				fPage= null;
			}
			if (fFoldingRunner == this)
				fFoldingRunner= null;
		}

		/*
		 * @see org.eclipse.ui.IPartListener2#partVisible(org.eclipse.ui.IWorkbenchPartReference)
		 */
		public void partVisible(IWorkbenchPartReference partRef) {
			if (JavaEditor.this.equals(partRef.getPart(false))) {
				cancel();
				toggleFolding();
			}
		}

		/*
		 * @see org.eclipse.ui.IPartListener2#partClosed(org.eclipse.ui.IWorkbenchPartReference)
		 */
		public void partClosed(IWorkbenchPartReference partRef) {
			if (JavaEditor.this.equals(partRef.getPart(false))) {
				cancel();
			}
		}

		public void partActivated(IWorkbenchPartReference partRef) {}
		public void partBroughtToTop(IWorkbenchPartReference partRef) {}
		public void partDeactivated(IWorkbenchPartReference partRef) {}
		public void partOpened(IWorkbenchPartReference partRef) {}
		public void partHidden(IWorkbenchPartReference partRef) {}
		public void partInputChanged(IWorkbenchPartReference partRef) {}
	}


	/**
	 * Editor specific selection provider which wraps the source viewer's selection provider.
	 * @since 3.4
	 */
	class JdtSelectionProvider extends SelectionProvider {

		private ListenerList fSelectionListeners= new ListenerList();
		private ListenerList fPostSelectionListeners= new ListenerList();
		private ITextSelection fInvalidSelection;
		private ISelection fValidSelection;

		/*
		 * @see org.eclipse.jface.viewers.ISelectionProvider#addSelectionChangedListener(ISelectionChangedListener)
		 */
		@Override
		public void addSelectionChangedListener(ISelectionChangedListener listener) {
			super.addSelectionChangedListener(listener);
			if (getSourceViewer() != null)
				fSelectionListeners.add(listener);
		}

		/*
		 * @see org.eclipse.jface.viewers.ISelectionProvider#getSelection()
		 */
		@Override
		public ISelection getSelection() {
			if (fInvalidSelection != null)
				return fInvalidSelection;
			return super.getSelection();
		}

		/*
		 * @see org.eclipse.jface.viewers.ISelectionProvider#removeSelectionChangedListener(ISelectionChangedListener)
		 */
		@Override
		public void removeSelectionChangedListener(ISelectionChangedListener listener) {
			super.removeSelectionChangedListener(listener);
			if (getSourceViewer() != null)
				fSelectionListeners.remove(listener);
		}

		/*
		 * @see org.eclipse.jface.viewers.ISelectionProvider#setSelection(ISelection)
		 */
		@Override
		public void setSelection(ISelection selection) {
			if (selection instanceof ITextSelection) {
				if (fInvalidSelection != null) {
					fInvalidSelection= null;

					ITextSelection newSelection= (ITextSelection) selection;
					ITextSelection oldSelection= (ITextSelection) getSelection();

					if (newSelection.getOffset() == oldSelection.getOffset() && newSelection.getLength() == oldSelection.getLength()) {
						markValid();
					} else {
						super.setSelection(selection);
					}
				} else {
					super.setSelection(selection);
				}
			} else if (selection instanceof IStructuredSelection && ((IStructuredSelection) selection).getFirstElement() instanceof EditorBreadcrumb) {
				markInvalid();
			}
		}

		/*
		 * @see org.eclipse.jface.text.IPostSelectionProvider#addPostSelectionChangedListener(org.eclipse.jface.viewers.ISelectionChangedListener)
		 */
		@Override
		public void addPostSelectionChangedListener(ISelectionChangedListener listener) {
			super.addPostSelectionChangedListener(listener);
			if (getSourceViewer() != null && getSourceViewer().getSelectionProvider() instanceof IPostSelectionProvider)
				fPostSelectionListeners.add(listener);
		}

		/*
		 * @see org.eclipse.jface.text.IPostSelectionProvider#removePostSelectionChangedListener(org.eclipse.jface.viewers.ISelectionChangedListener)
		 */
		@Override
		public void removePostSelectionChangedListener(ISelectionChangedListener listener) {
			super.removePostSelectionChangedListener(listener);
			if (getSourceViewer() != null)
				fPostSelectionListeners.remove(listener);
		}

		/*
		 * @see org.eclipse.jface.text.IPostSelectionValidator#isValid()
		 */
		@Override
		public boolean isValid(ISelection postSelection) {
			return fInvalidSelection == null && super.isValid(postSelection);
		}

		/**
		 * Marks this selection provider as currently being invalid. An invalid
		 * selection is one which can not be selected in the source viewer.
		 */
		private void markInvalid() {
			fValidSelection= getSelection();
			fInvalidSelection= new TextSelection(0, 0);

			SelectionChangedEvent event= new SelectionChangedEvent(this, fInvalidSelection);

			Object[] listeners= fSelectionListeners.getListeners();
			for (int i= 0; i < listeners.length; i++)
				((ISelectionChangedListener) listeners[i]).selectionChanged(event);

			listeners= fPostSelectionListeners.getListeners();
			for (int i= 0; i < listeners.length; i++)
				((ISelectionChangedListener) listeners[i]).selectionChanged(event);
		}

		/**
		 * Marks this selection provider as being valid.
		 */
		private void markValid() {
			fInvalidSelection= null;

			SelectionChangedEvent event= new SelectionChangedEvent(this, fValidSelection);

			Object[] listeners= fSelectionListeners.getListeners();
			for (int i= 0; i < listeners.length; i++)
				((ISelectionChangedListener) listeners[i]).selectionChanged(event);

			listeners= fPostSelectionListeners.getListeners();
			for (int i= 0; i < listeners.length; i++)
				((ISelectionChangedListener) listeners[i]).selectionChanged(event);
		}
	}



	/** Preference key for matching brackets. */
	protected final static String MATCHING_BRACKETS=  PreferenceConstants.EDITOR_MATCHING_BRACKETS;

	/**
	 * Preference key for highlighting bracket at caret location.
	 * 
	 * @since 3.8
	 */
	protected final static String HIGHLIGHT_BRACKET_AT_CARET_LOCATION= PreferenceConstants.EDITOR_HIGHLIGHT_BRACKET_AT_CARET_LOCATION;
	
	/**
	 * Preference key for enclosing brackets.
	 * 
	 * @since 3.8
	 */
	protected final static String ENCLOSING_BRACKETS= PreferenceConstants.EDITOR_ENCLOSING_BRACKETS;

	/** Preference key for matching brackets color. */
	protected final static String MATCHING_BRACKETS_COLOR=  PreferenceConstants.EDITOR_MATCHING_BRACKETS_COLOR;
	/**
	 * A named preference that controls whether the editor shows a breadcrumb.
	 * <p>
	 * Value is of type <code>Boolean</code>.
	 * </p>
	 *
	 * @since 3.4
	 */
	public static final String EDITOR_SHOW_BREADCRUMB= "breadcrumb"; //$NON-NLS-1$


	protected final static char[] BRACKETS= { '{', '}', '(', ')', '[', ']', '<', '>' };

	/** The outline page */
	protected JavaOutlinePage fOutlinePage;
	/** Outliner context menu Id */
	protected String fOutlinerContextMenuId;
	/**
	 * The editor selection changed listener.
	 *
	 * @since 3.0
	 */
	private EditorSelectionChangedListener fEditorSelectionChangedListener;

	/**
	 * DO NOT REMOVE, used in a product.
	 * @deprecated As of 3.5
	 */
	protected AbstractSelectionChangedListener fOutlineSelectionChangedListener= new AbstractSelectionChangedListener() {
		public void selectionChanged(SelectionChangedEvent event) {
		}
		@Override
		public void install(ISelectionProvider selectionProvider) {
		}
	};

	/** The editor's bracket matcher */
	protected JavaPairMatcher fBracketMatcher= new JavaPairMatcher(BRACKETS);
	/** This editor's encoding support */
	private DefaultEncodingSupport fEncodingSupport;
	/** History for structure select action */
	private SelectionHistory fSelectionHistory;
	protected CompositeActionGroup fActionGroups;

	/**
	 * The action group for folding.
	 *
	 * @since 3.0
	 */
	private FoldingActionGroup fFoldingGroup;

	private CompositeActionGroup fContextMenuGroup;
	/**
	 * Holds the current occurrence annotations.
	 * @since 3.0
	 */
	private Annotation[] fOccurrenceAnnotations= null;
	/**
	 * Tells whether all occurrences of the element at the
	 * current caret location are automatically marked in
	 * this editor.
	 * @since 3.0
	 */
	private boolean fMarkOccurrenceAnnotations;
	/**
	 * Tells whether the editor should show a breadcrumb.
	 * The breadcrumb shows the parent chain of the active
	 * editor element.
	 * @since 3.4
	 */
	private boolean fIsBreadcrumbVisible;
	/**
	 * Tells whether the occurrence annotations are sticky
	 * i.e. whether they stay even if there's no valid Java
	 * element at the current caret position.
	 * Only valid if {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * @since 3.0
	 */
	private boolean fStickyOccurrenceAnnotations;
	/**
	 * Tells whether to mark type occurrences in this editor.
	 * Only valid if {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * @since 3.0
	 */
	private boolean fMarkTypeOccurrences;
	/**
	 * Tells whether to mark method occurrences in this editor.
	 * Only valid if {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * @since 3.0
	 */
	private boolean fMarkMethodOccurrences;
	/**
	 * Tells whether to mark constant occurrences in this editor.
	 * Only valid if {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * @since 3.0
	 */
	private boolean fMarkConstantOccurrences;
	/**
	 * Tells whether to mark field occurrences in this editor.
	 * Only valid if {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * @since 3.0
	 */
	private boolean fMarkFieldOccurrences;
	/**
	 * Tells whether to mark local variable occurrences in this editor.
	 * Only valid if {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * @since 3.0
	 */
	private boolean fMarkLocalVariableypeOccurrences;
	/**
	 * Tells whether to mark exception occurrences in this editor.
	 * Only valid if {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * @since 3.0
	 */
	private boolean fMarkExceptions;
	/**
	 * Tells whether to mark method exits in this editor.
	 * Only valid if {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * @since 3.0
	 */
	private boolean fMarkMethodExitPoints;

	/**
	 * Tells whether to mark targets of <code>break</code> and <code>continue</code> statements in this editor.
	 * Only valid if {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * @since 3.2
	 */
	private boolean fMarkBreakContinueTargets;

	/**
	 * Tells whether to mark implementors in this editor.
	 * Only valid if {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * @since 3.1
	 */
	private boolean fMarkImplementors;
	/**
	 * The selection used when forcing occurrence marking
	 * through code.
	 * @since 3.0
	 */
	private ISelection fForcedMarkOccurrencesSelection;
	/**
	 * The document modification stamp at the time when the last
	 * occurrence marking took place.
	 * @since 3.1
	 */
	private long fMarkOccurrenceModificationStamp= IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;
	/**
	 * The region of the word under the caret used to when
	 * computing the current occurrence markings.
	 * @since 3.1
	 */
	private IRegion fMarkOccurrenceTargetRegion;

	/**
	 * The internal shell activation listener for updating occurrences.
	 * @since 3.0
	 */
	private ActivationListener fActivationListener= new ActivationListener();
	private ISelectionListenerWithAST fPostSelectionListenerWithAST;
	private OccurrencesFinderJob fOccurrencesFinderJob;
	/** The occurrences finder job canceler */
	private OccurrencesFinderJobCanceler fOccurrencesFinderJobCanceler;
	/**
	 * This editor's projection support
	 * @since 3.0
	 */
	private ProjectionSupport fProjectionSupport;
	/**
	 * This editor's projection model updater
	 * @since 3.0
	 */
	private IJavaFoldingStructureProvider fProjectionModelUpdater;
	/**
	 * The override and implements indicator manager for this editor.
	 * @since 3.0
	 */
	protected OverrideIndicatorManager fOverrideIndicatorManager;
	/**
	 * Semantic highlighting manager
	 * @since 3.0, protected as of 3.3
	 */
	protected SemanticHighlightingManager fSemanticManager;
	/**
	 * The folding runner.
	 * @since 3.1
	 */
	private ToggleFoldingRunner fFoldingRunner;

	/**
	 * Tells whether the selection changed event is caused
	 * by a call to {@link #gotoAnnotation(boolean)}.
	 *
	 * @since 3.2
	 */
	private boolean fSelectionChangedViaGotoAnnotation;
	/**
	 * The cached selected range.
	 *
	 * @see ITextViewer#getSelectedRange()
	 * @since 3.3
	 */
	private Point fCachedSelectedRange;

	/**
	 * The editor breadcrumb.
	 *
	 * @since 3.4
	 */
	private IBreadcrumb fBreadcrumb;

	/**
	 * The composite containing the breadcrumb.
	 *
	 * @since 3.4
	 */
	private Composite fBreadcrumbComposite;

	/**
	 * This editor's selection provider.
	 * @since 3.4
	 */
	private SelectionProvider fSelectionProvider= new JdtSelectionProvider();

	/**
	 * Time when last error message got set.
	 * 
	 * @since 3.5
	 */
	private long fErrorMessageTime;

	/**
	 * Timeout for the error message.
	 * 
	 * @since 3.5
	 */
	private static final long ERROR_MESSAGE_TIMEOUT= 1000;

	/**
	 * Previous location history for goto matching bracket action.
	 * 
	 * @since 3.8
	 */
	private List<IRegion> fPreviousSelections;

	/**
	 * Returns the most narrow java element including the given offset.
	 *
	 * @param offset the offset inside of the requested element
	 * @return the most narrow java element
	 */
	abstract protected IJavaElement getElementAt(int offset);

	/**
	 * Returns the java element of this editor's input corresponding to the given IJavaElement.
	 *
	 * @param element the java element
	 * @return the corresponding Java element
	 */
	abstract protected IJavaElement getCorrespondingElement(IJavaElement element);


	/**
	 * Sets the input of the editor's outline page.
	 *
	 * @param page the Java outline page
	 * @param input the editor input
	 */
	protected void setOutlinePageInput(JavaOutlinePage page, IEditorInput input) {
		if (page == null)
			return;

		IJavaElement je= getInputJavaElement();
		if (je != null && je.exists())
			page.setInput(je);
		else
			page.setInput(null);

	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractDecoratedTextEditor#initializeKeyBindingScopes()
	 */
	@Override
	protected void initializeKeyBindingScopes() {
		setKeyBindingScopes(new String[] { "org.eclipse.jdt.ui.javaEditorScope" });  //$NON-NLS-1$
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractDecoratedTextEditor#initializeEditor()
	 */
	@Override
	protected void initializeEditor() {
	}

	/**
	 * Returns a new Java source viewer configuration.
	 *
	 * @return a new <code>JavaSourceViewerConfiguration</code>
	 * @since 3.3
	 */
	protected JavaSourceViewerConfiguration createJavaSourceViewerConfiguration() {
		JavaTextTools textTools= JavaPlugin.getDefault().getJavaTextTools();
		return new JavaSourceViewerConfiguration(textTools.getColorManager(), getPreferenceStore(), this, IJavaPartitions.JAVA_PARTITIONING);
	}

	/*
	 * @see AbstractTextEditor#createSourceViewer(Composite, IVerticalRuler, int)
	 */
	@Override
	protected final ISourceViewer createSourceViewer(Composite parent, IVerticalRuler verticalRuler, int styles) {

		Composite composite= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout(1, false);
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.horizontalSpacing= 0;
		layout.verticalSpacing= 0;
		composite.setLayout(layout);

		fBreadcrumbComposite= new Composite(composite, SWT.NONE);
		GridData layoutData= new GridData(SWT.FILL, SWT.TOP, true, false);
		fBreadcrumbComposite.setLayoutData(layoutData);
		layout= new GridLayout(1, false);
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.horizontalSpacing= 0;
		layout.verticalSpacing= 0;
		layoutData.exclude= true;
		fBreadcrumbComposite.setLayout(layout);

		Composite editorComposite= new Composite(composite, SWT.NONE);
		editorComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		FillLayout fillLayout= new FillLayout(SWT.VERTICAL);
		fillLayout.marginHeight= 0;
		fillLayout.marginWidth= 0;
		fillLayout.spacing= 0;
		editorComposite.setLayout(fillLayout);

		IPreferenceStore store= getPreferenceStore();
		ISourceViewer sourceViewer= createJavaSourceViewer(editorComposite, verticalRuler, getOverviewRuler(), isOverviewRulerVisible(), styles, store);

		JavaUIHelp.setHelp(this, sourceViewer.getTextWidget(), IJavaHelpContextIds.JAVA_EDITOR);

		/*
		 * This is a performance optimization to reduce the computation of
		 * the text presentation triggered by {@link #setVisibleDocument(IDocument)}
		 */
		if (sourceViewer instanceof JavaSourceViewer && isFoldingEnabled() && (store == null || !store.getBoolean(PreferenceConstants.EDITOR_SHOW_SEGMENTS)))
			((JavaSourceViewer)sourceViewer).prepareDelayedProjection();

		if (sourceViewer instanceof ProjectionViewer) {
			fProjectionSupport= new ProjectionSupport((ProjectionViewer)sourceViewer, getAnnotationAccess(), getSharedColors());
			MarkerAnnotationPreferences markerAnnotationPreferences= (MarkerAnnotationPreferences)getAdapter(MarkerAnnotationPreferences.class);
			if (markerAnnotationPreferences != null) {
				Iterator<AnnotationPreference> e= markerAnnotationPreferences.getAnnotationPreferences().iterator();
				while (e.hasNext()) {
					AnnotationPreference annotationPreference= e.next();
					Object annotationType= annotationPreference.getAnnotationType();
					if (annotationType instanceof String)
						fProjectionSupport.addSummarizableAnnotationType((String)annotationType);
				}
			} else {
				fProjectionSupport.addSummarizableAnnotationType("org.eclipse.ui.workbench.texteditor.error"); //$NON-NLS-1$
				fProjectionSupport.addSummarizableAnnotationType("org.eclipse.ui.workbench.texteditor.warning"); //$NON-NLS-1$
			}
			fProjectionSupport.setHoverControlCreator(new IInformationControlCreator() {
				public IInformationControl createInformationControl(Shell shell) {
					return new SourceViewerInformationControl(shell, false, getOrientation(), EditorsUI.getTooltipAffordanceString());
				}
			});
			fProjectionSupport.setInformationPresenterControlCreator(new IInformationControlCreator() {
				public IInformationControl createInformationControl(Shell shell) {
					return new SourceViewerInformationControl(shell, true, getOrientation(), null);
				}
			});
			fProjectionSupport.install();

			fProjectionModelUpdater= JavaPlugin.getDefault().getFoldingStructureProviderRegistry().getCurrentFoldingProvider();
			if (fProjectionModelUpdater != null)
				fProjectionModelUpdater.install(this, (ProjectionViewer)sourceViewer);
		}

		// ensure source viewer decoration support has been created and configured
		getSourceViewerDecorationSupport(sourceViewer);

		return sourceViewer;
	}

	/**
	 * Creates the breadcrumb to be used by this editor.
	 * Returns <code>null</code> if this editor can not show a breadcrumb.
	 *
	 * @return the breadcrumb or <code>null</code>
	 * @since 3.4
	 */
	protected IBreadcrumb createBreadcrumb() {
		return new JavaEditorBreadcrumb(this);
	}

	/**
	 * @return the breadcrumb used by this viewer if any.
	 * @since 3.4
	 */
	public IBreadcrumb getBreadcrumb() {
		return fBreadcrumb;
	}

	/**
	 * Returns the preference key for the breadcrumb. The
	 * value depends on the current perspective.
	 *
	 * @return the preference key or <code>null</code> if there's no perspective
	 * @since 3.4
	 */
	String getBreadcrumbPreferenceKey() {
		IPerspectiveDescriptor perspective= getSite().getPage().getPerspective();
		if (perspective == null)
			return null;
		return JavaEditor.EDITOR_SHOW_BREADCRUMB + "." + perspective.getId(); //$NON-NLS-1$
	}

	/**
	 * Returns true if the breadcrumb is active. If true
	 * then the breadcrumb has the focus if this part
	 * is the active part.
	 *
	 * @return true if the breadcrumb is active.
	 * @since 3.4
	 */
	public boolean isBreadcrumbActive() {
		return fBreadcrumb != null && fBreadcrumb.isActive();
	}

	/**
	 * Makes the breadcrumb visible. Creates its content
	 * if this is the first time it is made visible.
	 *
	 * @since 3.4
	 */
	private void showBreadcrumb() {
		if (fBreadcrumb == null)
			return;

		if (fBreadcrumbComposite.getChildren().length == 0) {
			fBreadcrumb.createContent(fBreadcrumbComposite);
		}

		((GridData) fBreadcrumbComposite.getLayoutData()).exclude= false;
		fBreadcrumbComposite.setVisible(true);

		ISourceReference selection= computeHighlightRangeSourceReference();
		if (selection == null)
			selection= getInputJavaElement();
		setBreadcrumbInput(selection);
		fBreadcrumbComposite.getParent().layout(true, true);
	}

	/**
	 * Hides the breadcrumb
	 *
	 * @since 3.4
	 */
	private void hideBreadcrumb() {
		if (fBreadcrumb == null)
			return;
		((GridData) fBreadcrumbComposite.getLayoutData()).exclude= true;
		fBreadcrumbComposite.setVisible(false);
		fBreadcrumbComposite.getParent().layout(true, true);
	}

	/**
	 * Sets the breadcrumb input to the given element.
	 * @param element the element to use as input for the breadcrumb
	 * @since 3.4
	 */
	private void setBreadcrumbInput(ISourceReference element) {
		if (fBreadcrumb == null)
			return;

		fBreadcrumb.setInput(element);
	}

	/**
	 * Returns the editor's source viewer. May return <code>null</code> before
	 * the editor's part has been created and after disposal.
	 *
	 * @return the editor's source viewer, which may be <code>null</code>
	 */
	public final ISourceViewer getViewer() {
		return getSourceViewer();
	}

	/**
	 * Creates the Java source viewer to be used by this editor.
	 * Subclasses may re-implement this method.
	 *
	 * @param parent the parent control
	 * @param verticalRuler the vertical ruler
	 * @param overviewRuler the overview ruler
	 * @param isOverviewRulerVisible <code>true</code> if the overview ruler is visible
	 * @param styles style bits, <code>SWT.WRAP</code> is currently not supported
	 * @param store the preference store
	 * @see AbstractTextEditor#createSourceViewer(Composite, IVerticalRuler, int)
	 * @return the source viewer
	 */
	protected ISourceViewer createJavaSourceViewer(Composite parent, IVerticalRuler verticalRuler, IOverviewRuler overviewRuler, boolean isOverviewRulerVisible, int styles, IPreferenceStore store) {
		return new JavaSourceViewer(parent, verticalRuler, getOverviewRuler(), isOverviewRulerVisible(), styles, store);
	}

	/*
	 * @see AbstractTextEditor#affectsTextPresentation(PropertyChangeEvent)
	 */
	@Override
	protected boolean affectsTextPresentation(PropertyChangeEvent event) {
		return ((JavaSourceViewerConfiguration)getSourceViewerConfiguration()).affectsTextPresentation(event) || super.affectsTextPresentation(event);
	}

	/**
	 * Creates and returns the preference store for this Java editor with the given input.
	 *
	 * @param input The editor input for which to create the preference store
	 * @return the preference store for this editor
	 *
	 * @since 3.0
	 */
	private IPreferenceStore createCombinedPreferenceStore(IEditorInput input) {
		List<IPreferenceStore> stores= new ArrayList<IPreferenceStore>(3);

		IJavaProject project= EditorUtility.getJavaProject(input);
		if (project != null) {
			stores.add(new EclipsePreferencesAdapter(new ProjectScope(project.getProject()), JavaCore.PLUGIN_ID));
		}

		stores.add(JavaPlugin.getDefault().getPreferenceStore());
		stores.add(new PreferencesAdapter(JavaPlugin.getJavaCorePluginPreferences()));
		stores.add(EditorsUI.getPreferenceStore());
		stores.add(PlatformUI.getPreferenceStore());

		return new ChainedPreferenceStore(stores.toArray(new IPreferenceStore[stores.size()]));
	}

	/**
	 * Sets the outliner's context menu ID.
	 *
	 * @param menuId the menu ID
	 */
	protected void setOutlinerContextMenuId(String menuId) {
		fOutlinerContextMenuId= menuId;
	}

	/**
	 * Returns the standard action group of this editor.
	 *
	 * @return returns this editor's standard action group
	 */
	protected ActionGroup getActionGroup() {
		return fActionGroups;
	}

	/*
	 * @see AbstractTextEditor#editorContextMenuAboutToShow
	 */
	@Override
	public void editorContextMenuAboutToShow(IMenuManager menu) {

		super.editorContextMenuAboutToShow(menu);
		menu.insertAfter(IContextMenuConstants.GROUP_OPEN, new GroupMarker(IContextMenuConstants.GROUP_SHOW));

		ActionContext context= new ActionContext(getSelectionProvider().getSelection());
		fContextMenuGroup.setContext(context);
		fContextMenuGroup.fillContextMenu(menu);
		fContextMenuGroup.setContext(null);

		//Breadcrumb
		IAction action= getAction(IJavaEditorActionDefinitionIds.SHOW_IN_BREADCRUMB);
		menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, action);

		// Quick views
		action= getAction(IJavaEditorActionDefinitionIds.SHOW_OUTLINE);
		menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, action);
		action= getAction(IJavaEditorActionDefinitionIds.OPEN_HIERARCHY);
		menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, action);

		// Copy qualified name
		action= getAction(IJavaEditorActionConstants.COPY_QUALIFIED_NAME);
		if (menu.find(ITextEditorActionConstants.COPY) != null)
			menu.insertAfter(ITextEditorActionConstants.COPY, action);
		else
			addAction(menu, ITextEditorActionConstants.GROUP_COPY, IJavaEditorActionConstants.COPY_QUALIFIED_NAME);

	}

	/**
	 * Creates the outline page used with this editor.
	 *
	 * @return the created Java outline page
	 */
	protected JavaOutlinePage createOutlinePage() {
		JavaOutlinePage page= new JavaOutlinePage(fOutlinerContextMenuId, this);
		setOutlinePageInput(page, getEditorInput());
		return page;
	}

	/**
	 * Informs the editor that its outliner has been closed.
	 */
	public void outlinePageClosed() {
		if (fOutlinePage != null) {
			fOutlinePage= null;
			resetHighlightRange();
		}
	}

	/**
	 * Synchronizes the outliner selection with the given element
	 * position in the editor.
	 *
	 * @param element the java element to select
	 */
	protected void synchronizeOutlinePage(ISourceReference element) {
		synchronizeOutlinePage(element, true);
	}

	/**
	 * Synchronizes the outliner selection with the given element
	 * position in the editor.
	 *
	 * @param element the java element to select
	 * @param checkIfOutlinePageActive <code>true</code> if check for active outline page needs to be done
	 */
	protected void synchronizeOutlinePage(ISourceReference element, boolean checkIfOutlinePageActive) {
		if (fOutlinePage != null && element != null && !(checkIfOutlinePageActive && isJavaOutlinePageActive())) {
			fOutlinePage.select(element);
		}
	}

	/**
	 * Synchronizes the outliner selection with the actual cursor
	 * position in the editor.
	 */
	public void synchronizeOutlinePageSelection() {
		synchronizeOutlinePage(computeHighlightRangeSourceReference());
	}

	/*
	 * @see AbstractTextEditor#getAdapter(Class)
	 */
	@Override
	public Object getAdapter(Class required) {

		if (IContentOutlinePage.class.equals(required)) {
			if (fOutlinePage == null && getSourceViewer() != null && isCalledByOutline())
				fOutlinePage= createOutlinePage();
			return fOutlinePage;
		}

		if (IEncodingSupport.class.equals(required))
			return fEncodingSupport;

		if (required == IShowInTargetList.class) {
			return new IShowInTargetList() {
				public String[] getShowInTargetIds() {
					return new String[] { JavaUI.ID_PACKAGES, IPageLayout.ID_OUTLINE, JavaPlugin.ID_RES_NAV };
				}

			};
		}

		if (required == IShowInSource.class) {
			IJavaElement inputJE= getInputJavaElement();
			if (inputJE instanceof ICompilationUnit && !JavaModelUtil.isPrimary((ICompilationUnit) inputJE))
				return null;

			return new IShowInSource() {
				public ShowInContext getShowInContext() {
					return new ShowInContext(null, null) {
						/*
						 * @see org.eclipse.ui.part.ShowInContext#getInput()
						 * @since 3.4
						 */
						@Override
						public Object getInput() {
							if (isBreadcrumbActive())
								return null;

							return getEditorInput();
						}

						/*
						 * @see org.eclipse.ui.part.ShowInContext#getSelection()
						 * @since 3.3
						 */
						@Override
						public ISelection getSelection() {
							if (isBreadcrumbActive())
								return getBreadcrumb().getSelectionProvider().getSelection();

							try {
								IJavaElement je= SelectionConverter.getElementAtOffset(JavaEditor.this);
								if (je != null)
									return new StructuredSelection(je);
								return null;
							} catch (JavaModelException ex) {
								return null;
							}
						}
					};
				}
			};
		}

		if (required == IJavaFoldingStructureProvider.class)
			return fProjectionModelUpdater;

		if (fProjectionSupport != null) {
			Object adapter= fProjectionSupport.getAdapter(getSourceViewer(), required);
			if (adapter != null)
				return adapter;
		}

		if (required == IContextProvider.class) {
			if (isBreadcrumbActive()) {
				return JavaUIHelp.getHelpContextProvider(this, IJavaHelpContextIds.JAVA_EDITOR_BREADCRUMB);
			} else {
				return JavaUIHelp.getHelpContextProvider(this, IJavaHelpContextIds.JAVA_EDITOR);
			}
		}

		return super.getAdapter(required);
	}

	/**
	 * React to changed selection.
	 *
	 * @since 3.0
	 */
	protected void selectionChanged() {
		if (getSelectionProvider() == null)
			return;
		ISourceReference element= computeHighlightRangeSourceReference();
		if (getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_SYNC_OUTLINE_ON_CURSOR_MOVE))
			synchronizeOutlinePage(element);
		if (fIsBreadcrumbVisible && fBreadcrumb != null && !fBreadcrumb.isActive())
			setBreadcrumbInput(element);
		setSelection(element, false);
		if (!fSelectionChangedViaGotoAnnotation)
			updateStatusLine();
		fSelectionChangedViaGotoAnnotation= false;
	}

	protected void setSelection(ISourceReference reference, boolean moveCursor) {
		if (getSelectionProvider() == null)
			return;

		ISelection selection= getSelectionProvider().getSelection();
		if (selection instanceof ITextSelection) {
			ITextSelection textSelection= (ITextSelection) selection;
			// PR 39995: [navigation] Forward history cleared after going back in navigation history:
			// mark only in navigation history if the cursor is being moved (which it isn't if
			// this is called from a PostSelectionEvent that should only update the magnet)
			if (moveCursor && (textSelection.getOffset() != 0 || textSelection.getLength() != 0))
				markInNavigationHistory();
		}

		if (reference != null) {

			StyledText  textWidget= null;

			ISourceViewer sourceViewer= getSourceViewer();
			if (sourceViewer != null)
				textWidget= sourceViewer.getTextWidget();

			if (textWidget == null)
				return;

			try {
				ISourceRange range= null;
				if (reference instanceof ILocalVariable || reference instanceof ITypeParameter || reference instanceof IAnnotation) {
					IJavaElement je= ((IJavaElement)reference).getParent();
					if (je instanceof ISourceReference)
						range= ((ISourceReference)je).getSourceRange();
				} else
					range= reference.getSourceRange();

				if (range == null)
					return;

				int offset= range.getOffset();
				int length= range.getLength();

				if (offset < 0 || length < 0)
					return;

				setHighlightRange(offset, length, moveCursor);

				if (!moveCursor)
					return;

				offset= -1;
				length= -1;

				range= reference.getNameRange();
				if (range != null) {
					offset= range.getOffset();
					length= range.getLength();
				}

				if (offset > -1 && length > 0) {

					try  {
						textWidget.setRedraw(false);
						sourceViewer.revealRange(offset, length);
						sourceViewer.setSelectedRange(offset, length);
					} finally {
						textWidget.setRedraw(true);
					}

					markInNavigationHistory();
				}

			} catch (JavaModelException x) {
			} catch (IllegalArgumentException x) {
			}

		} else if (moveCursor) {
			resetHighlightRange();
			markInNavigationHistory();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#setFocus()
	 */
	@Override
	public void setFocus() {
		if (fBreadcrumb != null && fBreadcrumb.isActive()) {
			fBreadcrumb.activate();
			return;
		}

		super.setFocus();
	}

	public void setSelection(IJavaElement element) {

		if (element == null || element instanceof ICompilationUnit || element instanceof IClassFile) {
			/*
			 * If the element is an ICompilationUnit this unit is either the input
			 * of this editor or not being displayed. In both cases, nothing should
			 * happened. (http://dev.eclipse.org/bugs/show_bug.cgi?id=5128)
			 */
			return;
		}

		IJavaElement corresponding= getCorrespondingElement(element);
		if (corresponding instanceof ISourceReference) {
			ISourceReference reference= (ISourceReference) corresponding;
			// set highlight range
			setSelection(reference, true);
			// set outliner selection
			if (fOutlinePage != null)
				fOutlinePage.select(reference);
		}
	}

	protected void doSelectionChanged(ISelection selection) {

		ISourceReference reference= null;

		Iterator<?> iter= ((IStructuredSelection) selection).iterator();
		while (iter.hasNext()) {
			Object o= iter.next();
			if (o instanceof ISourceReference) {
				reference= (ISourceReference) o;
				break;
			}
		}
		if (!isActivePart() && JavaPlugin.getActivePage() != null)
			JavaPlugin.getActivePage().bringToTop(this);

		setSelection(reference, !isActivePart());

		ISelectionProvider selectionProvider= getSelectionProvider();
		if (selectionProvider == null )
			return;

		ISelection textSelection= selectionProvider.getSelection();
		if (!(textSelection instanceof ITextSelection))
			return;

		ITypeRoot inputJavaElement= getInputJavaElement();
		if (inputJavaElement == null)
			return;

		CompilationUnit ast= SharedASTProvider.getAST(inputJavaElement, SharedASTProvider.WAIT_NO /* DO NOT USE WAIT_ACTIVE_ONLY */ , getProgressMonitor());
		if (ast != null) {
			fForcedMarkOccurrencesSelection= textSelection;
			updateOccurrenceAnnotations((ITextSelection)textSelection, ast);
		}

	}

	/*
	 * @see AbstractTextEditor#adjustHighlightRange(int, int)
	 */
	@Override
	protected void adjustHighlightRange(int offset, int length) {

		try {

			IJavaElement element= getElementAt(offset, false);
			while (element instanceof ISourceReference) {
				ISourceRange range= ((ISourceReference) element).getSourceRange();
				if (range != null && offset < range.getOffset() + range.getLength() && range.getOffset() < offset + length) {

					ISourceViewer viewer= getSourceViewer();
					if (viewer instanceof ITextViewerExtension5) {
						ITextViewerExtension5 extension= (ITextViewerExtension5) viewer;
						extension.exposeModelRange(new Region(range.getOffset(), range.getLength()));
					}

					setHighlightRange(range.getOffset(), range.getLength(), true);
					if (fOutlinePage != null)
						fOutlinePage.select((ISourceReference) element);

					return;
				}
				element= element.getParent();
			}

		} catch (JavaModelException x) {
			JavaPlugin.log(x.getStatus());
		}

		ISourceViewer viewer= getSourceViewer();
		if (viewer instanceof ITextViewerExtension5) {
			ITextViewerExtension5 extension= (ITextViewerExtension5) viewer;
			extension.exposeModelRange(new Region(offset, length));
		} else {
			resetHighlightRange();
		}

	}

	protected boolean isActivePart() {
		IWorkbenchPart part= getActivePart();
		return part != null && part.equals(this);
	}

	private boolean isJavaOutlinePageActive() {
		IWorkbenchPart part= getActivePart();
		return part instanceof ContentOutline && ((ContentOutline)part).getCurrentPage() == fOutlinePage;
	}

	private IWorkbenchPart getActivePart() {
		IWorkbenchWindow window= getSite().getWorkbenchWindow();
		IPartService service= window.getPartService();
		IWorkbenchPart part= service.getActivePart();
		return part;
	}

	/*
	 * @see StatusTextEditor#getStatusHeader(IStatus)
	 */
	@Override
	protected String getStatusHeader(IStatus status) {
		if (fEncodingSupport != null) {
			String message= fEncodingSupport.getStatusHeader(status);
			if (message != null)
				return message;
		}
		return super.getStatusHeader(status);
	}

	/*
	 * @see StatusTextEditor#getStatusBanner(IStatus)
	 */
	@Override
	protected String getStatusBanner(IStatus status) {
		if (fEncodingSupport != null) {
			String message= fEncodingSupport.getStatusBanner(status);
			if (message != null)
				return message;
		}
		return super.getStatusBanner(status);
	}

	/*
	 * @see StatusTextEditor#getStatusMessage(IStatus)
	 */
	@Override
	protected String getStatusMessage(IStatus status) {
		if (fEncodingSupport != null) {
			String message= fEncodingSupport.getStatusMessage(status);
			if (message != null)
				return message;
		}
		return super.getStatusMessage(status);
	}

	/*
	 * @see AbstractTextEditor#doSetInput
	 */
	@Override
	protected void doSetInput(IEditorInput input) throws CoreException {
		ISourceViewer sourceViewer= getSourceViewer();
		if (!(sourceViewer instanceof ISourceViewerExtension2)) {
			setPreferenceStore(createCombinedPreferenceStore(input));
			internalDoSetInput(input);
			return;
		}

		// uninstall & unregister preference store listener
		getSourceViewerDecorationSupport(sourceViewer).uninstall();
		((ISourceViewerExtension2)sourceViewer).unconfigure();

		setPreferenceStore(createCombinedPreferenceStore(input));

		// install & register preference store listener
		sourceViewer.configure(getSourceViewerConfiguration());
		getSourceViewerDecorationSupport(sourceViewer).install(getPreferenceStore());

		internalDoSetInput(input);
	}

	private void internalDoSetInput(IEditorInput input) throws CoreException {
		ISourceViewer sourceViewer= getSourceViewer();
		JavaSourceViewer javaSourceViewer= null;
		if (sourceViewer instanceof JavaSourceViewer)
			javaSourceViewer= (JavaSourceViewer)sourceViewer;

		IPreferenceStore store= getPreferenceStore();
		if (javaSourceViewer != null && isFoldingEnabled() &&(store == null || !store.getBoolean(PreferenceConstants.EDITOR_SHOW_SEGMENTS)))
			javaSourceViewer.prepareDelayedProjection();

		super.doSetInput(input);

		if (javaSourceViewer != null && javaSourceViewer.getReconciler() == null) {
			IReconciler reconciler= getSourceViewerConfiguration().getReconciler(javaSourceViewer);
			if (reconciler != null) {
				reconciler.install(javaSourceViewer);
				javaSourceViewer.setReconciler(reconciler);
			}
		}

		if (fEncodingSupport != null)
			fEncodingSupport.reset();

		setOutlinePageInput(fOutlinePage, input);

		if (isShowingOverrideIndicators())
			installOverrideIndicator(false);
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#setPreferenceStore(org.eclipse.jface.preference.IPreferenceStore)
	 * @since 3.0
	 */
	@Override
	protected void setPreferenceStore(IPreferenceStore store) {
		super.setPreferenceStore(store);
		SourceViewerConfiguration sourceViewerConfiguration= getSourceViewerConfiguration();
		if (sourceViewerConfiguration == null || sourceViewerConfiguration instanceof JavaSourceViewerConfiguration) {
			JavaTextTools textTools= JavaPlugin.getDefault().getJavaTextTools();
			setSourceViewerConfiguration(new JavaSourceViewerConfiguration(textTools.getColorManager(), store, this, IJavaPartitions.JAVA_PARTITIONING));
		}

		if (getSourceViewer() instanceof JavaSourceViewer)
			((JavaSourceViewer)getSourceViewer()).setPreferenceStore(store);

		fMarkOccurrenceAnnotations= store.getBoolean(PreferenceConstants.EDITOR_MARK_OCCURRENCES);
		fStickyOccurrenceAnnotations= store.getBoolean(PreferenceConstants.EDITOR_STICKY_OCCURRENCES);
		fMarkTypeOccurrences= store.getBoolean(PreferenceConstants.EDITOR_MARK_TYPE_OCCURRENCES);
		fMarkMethodOccurrences= store.getBoolean(PreferenceConstants.EDITOR_MARK_METHOD_OCCURRENCES);
		fMarkConstantOccurrences= store.getBoolean(PreferenceConstants.EDITOR_MARK_CONSTANT_OCCURRENCES);
		fMarkFieldOccurrences= store.getBoolean(PreferenceConstants.EDITOR_MARK_FIELD_OCCURRENCES);
		fMarkLocalVariableypeOccurrences= store.getBoolean(PreferenceConstants.EDITOR_MARK_LOCAL_VARIABLE_OCCURRENCES);
		fMarkExceptions= store.getBoolean(PreferenceConstants.EDITOR_MARK_EXCEPTION_OCCURRENCES);
		fMarkImplementors= store.getBoolean(PreferenceConstants.EDITOR_MARK_IMPLEMENTORS);
		fMarkMethodExitPoints= store.getBoolean(PreferenceConstants.EDITOR_MARK_METHOD_EXIT_POINTS);
		fMarkBreakContinueTargets= store.getBoolean(PreferenceConstants.EDITOR_MARK_BREAK_CONTINUE_TARGETS);
	}

	/*
	 * @see IWorkbenchPart#dispose()
	 */
	@Override
	public void dispose() {

		if (fProjectionModelUpdater != null) {
			fProjectionModelUpdater.uninstall();
			fProjectionModelUpdater= null;
		}

		if (fProjectionSupport != null) {
			fProjectionSupport.dispose();
			fProjectionSupport= null;
		}

		// cancel possible running computation
		fMarkOccurrenceAnnotations= false;
		uninstallOccurrencesFinder();

		uninstallOverrideIndicator();

		uninstallSemanticHighlighting();

		if (fActivationListener != null) {
			PlatformUI.getWorkbench().removeWindowListener(fActivationListener);
			fActivationListener= null;
		}
		
		if (fEncodingSupport != null) {
			fEncodingSupport.dispose();
			fEncodingSupport= null;
		}

		if (fBracketMatcher != null) {
			fBracketMatcher.dispose();
			fBracketMatcher= null;
		}

		if (fSelectionHistory != null) {
			fSelectionHistory.dispose();
			fSelectionHistory= null;
		}

		if (fEditorSelectionChangedListener != null)  {
			fEditorSelectionChangedListener.uninstall(getSelectionProvider());
			fEditorSelectionChangedListener= null;
		}

		if (fActionGroups != null) {
			fActionGroups.dispose();
			fActionGroups= null;
		}

		if (fBreadcrumb != null) {
			fBreadcrumb.dispose();
			fBreadcrumb= null;
		}

		super.dispose();
		fSelectionProvider= null;
	}

	@Override
	protected void createActions() {
		installEncodingSupport();

		super.createActions();

		ActionGroup oeg, ovg, jsg;
		fActionGroups= new CompositeActionGroup(new ActionGroup[] {
			oeg= new OpenEditorActionGroup(this),
			ovg= new OpenViewActionGroup(this),
			jsg= new JavaSearchActionGroup(this)
		});
		fContextMenuGroup= new CompositeActionGroup(new ActionGroup[] {oeg, ovg, jsg});

		fFoldingGroup= new FoldingActionGroup(this, getViewer());

		Action action= new GotoMatchingBracketAction(this);
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.GOTO_MATCHING_BRACKET);
		setAction(GotoMatchingBracketAction.GOTO_MATCHING_BRACKET, action);

		action= new ShowInBreadcrumbAction(this);
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.SHOW_IN_BREADCRUMB);
		setAction(IJavaEditorActionDefinitionIds.SHOW_IN_BREADCRUMB, action);

		action= new TextOperationAction(JavaEditorMessages.getBundleForConstructedKeys(),"ShowOutline.", this, JavaSourceViewer.SHOW_OUTLINE, true); //$NON-NLS-1$
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.SHOW_OUTLINE);
		setAction(IJavaEditorActionDefinitionIds.SHOW_OUTLINE, action);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.SHOW_OUTLINE_ACTION);

		action= new TextOperationAction(JavaEditorMessages.getBundleForConstructedKeys(),"OpenStructure.", this, JavaSourceViewer.OPEN_STRUCTURE, true); //$NON-NLS-1$
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_STRUCTURE);
		setAction(IJavaEditorActionDefinitionIds.OPEN_STRUCTURE, action);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.OPEN_STRUCTURE_ACTION);

		action= new TextOperationAction(JavaEditorMessages.getBundleForConstructedKeys(),"OpenHierarchy.", this, JavaSourceViewer.SHOW_HIERARCHY, true); //$NON-NLS-1$
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_HIERARCHY);
		setAction(IJavaEditorActionDefinitionIds.OPEN_HIERARCHY, action);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.OPEN_HIERARCHY_ACTION);

		fSelectionHistory= new SelectionHistory(this);

		action= new StructureSelectEnclosingAction(this, fSelectionHistory);
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.SELECT_ENCLOSING);
		setAction(StructureSelectionAction.ENCLOSING, action);

		action= new StructureSelectNextAction(this, fSelectionHistory);
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.SELECT_NEXT);
		setAction(StructureSelectionAction.NEXT, action);

		action= new StructureSelectPreviousAction(this, fSelectionHistory);
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.SELECT_PREVIOUS);
		setAction(StructureSelectionAction.PREVIOUS, action);

		StructureSelectHistoryAction historyAction= new StructureSelectHistoryAction(this, fSelectionHistory);
		historyAction.setActionDefinitionId(IJavaEditorActionDefinitionIds.SELECT_LAST);
		setAction(StructureSelectionAction.HISTORY, historyAction);
		fSelectionHistory.setHistoryAction(historyAction);

		action= GoToNextPreviousMemberAction.newGoToNextMemberAction(this);
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.GOTO_NEXT_MEMBER);
		setAction(GoToNextPreviousMemberAction.NEXT_MEMBER, action);

		action= GoToNextPreviousMemberAction.newGoToPreviousMemberAction(this);
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.GOTO_PREVIOUS_MEMBER);
		setAction(GoToNextPreviousMemberAction.PREVIOUS_MEMBER, action);

		action= new FormatElementAction();
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.QUICK_FORMAT);
		setAction("QuickFormat", action); //$NON-NLS-1$
		markAsStateDependentAction("QuickFormat", true); //$NON-NLS-1$

		action= new RemoveOccurrenceAnnotations(this);
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.REMOVE_OCCURRENCE_ANNOTATIONS);
		setAction("RemoveOccurrenceAnnotations", action); //$NON-NLS-1$

		// add annotation actions for roll-over expand hover
		action= new JavaSelectMarkerRulerAction2(JavaEditorMessages.getBundleForConstructedKeys(), "Editor.RulerAnnotationSelection.", this); //$NON-NLS-1$
		setAction("AnnotationAction", action); //$NON-NLS-1$

		createDeprecatedShowInPackageExplorerAction();

		// replace cut/copy paste actions with a version that implement 'add imports on paste'

		action= new ClipboardOperationAction(JavaEditorMessages.getBundleForConstructedKeys(), "Editor.Cut.", this, ITextOperationTarget.CUT); //$NON-NLS-1$
		setAction(ITextEditorActionConstants.CUT, action);

		action= new ClipboardOperationAction(JavaEditorMessages.getBundleForConstructedKeys(), "Editor.Copy.", this, ITextOperationTarget.COPY); //$NON-NLS-1$
		setAction(ITextEditorActionConstants.COPY, action);

		action= new ClipboardOperationAction(JavaEditorMessages.getBundleForConstructedKeys(), "Editor.Paste.", this, ITextOperationTarget.PASTE); //$NON-NLS-1$
		setAction(ITextEditorActionConstants.PASTE, action);

		action= new CopyQualifiedNameAction(this);
		action.setActionDefinitionId(CopyQualifiedNameAction.ACTION_DEFINITION_ID);
		action.setImageDescriptor(null);
		setAction(IJavaEditorActionConstants.COPY_QUALIFIED_NAME, action);
	}

	/**
	 * @deprecated As of 3.5, got replaced by generic Navigate &gt; Show In &gt;
	 */
	private void createDeprecatedShowInPackageExplorerAction() {
		IAction action= new org.eclipse.jdt.ui.actions.ShowInPackageViewAction(this);
		action.setActionDefinitionId(IJavaEditorActionDefinitionIds.SHOW_IN_PACKAGE_VIEW);
		setAction("ShowInPackageView", action); //$NON-NLS-1$
	}

	/**
	 * Sets this editor's actions into activated (default) or deactived state.
	 * <p>
	 * XXX: Currently this is done by using a private method from {@link AbstractTextEditor} as we
	 * don't want to make this risky method API at this point, since Java editor breadcrumb might
	 * become a Platform UI feature during 3.5 and hence we can then delete this workaround.
	 * </p>
	 * 
	 * @param state <code>true</code> if activated
	 * @since 3.4
	 */
	protected void setActionsActivated(boolean state) {
		Method method= null;
		try {
			method= AbstractTextEditor.class.getDeclaredMethod("setActionActivation", new Class[] { boolean.class }); //$NON-NLS-1$
		} catch (SecurityException ex) {
			JavaPlugin.log(ex);
		} catch (NoSuchMethodException ex) {
			JavaPlugin.log(ex);
		}
		Assert.isNotNull(method);
		method.setAccessible(true);
		try {
			method.invoke(this, new Object[] { new Boolean(state) });
		} catch (IllegalArgumentException ex) {
			JavaPlugin.log(ex);
		} catch (InvocationTargetException ex) {
			JavaPlugin.log(ex);
		} catch (IllegalAccessException ex) {
			JavaPlugin.log(ex);
		}
	}

	/**
	 * Installs the encoding support on the given text editor.
	 * <p>
 	 * Subclasses may override to install their own encoding
 	 * support or to disable the default encoding support.
 	 * </p>
	 * @since 3.2
	 */
	protected void installEncodingSupport() {
		fEncodingSupport= new DefaultEncodingSupport();
		fEncodingSupport.initialize(this);
	}


	public void updatedTitleImage(Image image) {
		setTitleImage(image);
	}

	/*
	 * @see AbstractTextEditor#handlePreferenceStoreChanged(PropertyChangeEvent)
	 */
	@Override
	protected void handlePreferenceStoreChanged(PropertyChangeEvent event) {

		final String property= event.getProperty();

		if (AbstractDecoratedTextEditorPreferenceConstants.EDITOR_TAB_WIDTH.equals(property)) {
			/*
			 * Ignore tab setting since we rely on the formatter preferences.
			 * We do this outside the try-finally block to avoid that EDITOR_TAB_WIDTH
			 * is handled by the sub-class (AbstractDecoratedTextEditor).
			 */
			return;
		}

		try {

			ISourceViewer sourceViewer= getSourceViewer();
			if (sourceViewer == null)
				return;

			if (isJavaEditorHoverProperty(property))
				updateHoverBehavior();

			boolean newBooleanValue= false;
			Object newValue= event.getNewValue();
			if (newValue != null)
				newBooleanValue= Boolean.valueOf(newValue.toString()).booleanValue();

			if (PreferenceConstants.EDITOR_SYNC_OUTLINE_ON_CURSOR_MOVE.equals(property)) {
				if (newBooleanValue)
					selectionChanged();
				return;
			}

			if (PreferenceConstants.EDITOR_MARK_OCCURRENCES.equals(property)) {
				if (newBooleanValue != fMarkOccurrenceAnnotations) {
					fMarkOccurrenceAnnotations= newBooleanValue;
					if (!fMarkOccurrenceAnnotations)
						uninstallOccurrencesFinder();
					else
						installOccurrencesFinder(true);
				}
				return;
			}
			if (property.equals(getBreadcrumbPreferenceKey())) {
				if (newBooleanValue != fIsBreadcrumbVisible) {
					fIsBreadcrumbVisible= newBooleanValue;
					if (fIsBreadcrumbVisible)
						showBreadcrumb();
					else
						hideBreadcrumb();
				}
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_TYPE_OCCURRENCES.equals(property)) {
				fMarkTypeOccurrences= newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_METHOD_OCCURRENCES.equals(property)) {
				fMarkMethodOccurrences= newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_CONSTANT_OCCURRENCES.equals(property)) {
				fMarkConstantOccurrences= newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_FIELD_OCCURRENCES.equals(property)) {
				fMarkFieldOccurrences= newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_LOCAL_VARIABLE_OCCURRENCES.equals(property)) {
				fMarkLocalVariableypeOccurrences= newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_EXCEPTION_OCCURRENCES.equals(property)) {
				fMarkExceptions= newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_METHOD_EXIT_POINTS.equals(property)) {
				fMarkMethodExitPoints= newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_BREAK_CONTINUE_TARGETS.equals(property)) {
				fMarkBreakContinueTargets= newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_IMPLEMENTORS.equals(property)) {
				fMarkImplementors= newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_STICKY_OCCURRENCES.equals(property)) {
				fStickyOccurrenceAnnotations= newBooleanValue;
				return;
			}
			if (SemanticHighlightings.affectsEnablement(getPreferenceStore(), event)) {
				if (isSemanticHighlightingEnabled())
					installSemanticHighlighting();
				else
					uninstallSemanticHighlighting();
				return;
			}

			if (JavaCore.COMPILER_SOURCE.equals(property)) {
				if (event.getNewValue() instanceof String)
					fBracketMatcher.setSourceVersion((String) event.getNewValue());
				// fall through as others are interested in source change as well.
			}

			((JavaSourceViewerConfiguration)getSourceViewerConfiguration()).handlePropertyChangeEvent(event);

			if (affectsOverrideIndicatorAnnotations(event)) {
				if (isShowingOverrideIndicators()) {
					if (fOverrideIndicatorManager == null)
						installOverrideIndicator(true);
				} else {
					if (fOverrideIndicatorManager != null)
						uninstallOverrideIndicator();
				}
				return;
			}

			if (PreferenceConstants.EDITOR_FOLDING_PROVIDER.equals(property)) {
				if (sourceViewer instanceof ProjectionViewer) {
					ProjectionViewer projectionViewer= (ProjectionViewer) sourceViewer;
					if (fProjectionModelUpdater != null)
						fProjectionModelUpdater.uninstall();
					// either freshly enabled or provider changed
					fProjectionModelUpdater= JavaPlugin.getDefault().getFoldingStructureProviderRegistry().getCurrentFoldingProvider();
					if (fProjectionModelUpdater != null) {
						fProjectionModelUpdater.install(this, projectionViewer);
					}
				}
				return;
			}

			if (DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE.equals(property)
					|| DefaultCodeFormatterConstants.FORMATTER_INDENTATION_SIZE.equals(property)
					|| DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR.equals(property)) {
				StyledText textWidget= sourceViewer.getTextWidget();
				int tabWidth= getSourceViewerConfiguration().getTabWidth(sourceViewer);
				if (textWidget.getTabs() != tabWidth)
					textWidget.setTabs(tabWidth);
				return;
			}

			if (PreferenceConstants.EDITOR_FOLDING_ENABLED.equals(property)) {
				if (sourceViewer instanceof ProjectionViewer) {
					new ToggleFoldingRunner().runWhenNextVisible();
				}
				return;
			}

		} finally {
			super.handlePreferenceStoreChanged(event);
		}

		if (AbstractDecoratedTextEditorPreferenceConstants.SHOW_RANGE_INDICATOR.equals(property)) {
			// superclass already installed the range indicator
			Object newValue= event.getNewValue();
			ISourceViewer viewer= getSourceViewer();
			if (newValue != null && viewer != null) {
				if (Boolean.valueOf(newValue.toString()).booleanValue()) {
					// adjust the highlightrange in order to get the magnet right after changing the selection
					Point selection= viewer.getSelectedRange();
					adjustHighlightRange(selection.x, selection.y);
				}
			}

		}
	}

	/**
	 * Initializes the given viewer's colors.
	 *
	 * @param viewer the viewer to be initialized
	 * @since 3.0
	 */
	@Override
	protected void initializeViewerColors(ISourceViewer viewer) {
		// is handled by JavaSourceViewer
	}

	private boolean isJavaEditorHoverProperty(String property) {
		return	PreferenceConstants.EDITOR_TEXT_HOVER_MODIFIERS.equals(property);
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#updatePropertyDependentActions()
	 */
	@Override
	protected void updatePropertyDependentActions() {
		super.updatePropertyDependentActions();
		if (fEncodingSupport != null)
			fEncodingSupport.reset();
	}

	/*
	 * Update the hovering behavior depending on the preferences.
	 */
	private void updateHoverBehavior() {
		SourceViewerConfiguration configuration= getSourceViewerConfiguration();
		String[] types= configuration.getConfiguredContentTypes(getSourceViewer());

		for (int i= 0; i < types.length; i++) {

			String t= types[i];

			ISourceViewer sourceViewer= getSourceViewer();
			if (sourceViewer instanceof ITextViewerExtension2) {
				// Remove existing hovers
				((ITextViewerExtension2)sourceViewer).removeTextHovers(t);

				int[] stateMasks= configuration.getConfiguredTextHoverStateMasks(getSourceViewer(), t);

				if (stateMasks != null) {
					for (int j= 0; j < stateMasks.length; j++)	{
						int stateMask= stateMasks[j];
						ITextHover textHover= configuration.getTextHover(sourceViewer, t, stateMask);
						((ITextViewerExtension2)sourceViewer).setTextHover(textHover, t, stateMask);
					}
				} else {
					ITextHover textHover= configuration.getTextHover(sourceViewer, t);
					((ITextViewerExtension2)sourceViewer).setTextHover(textHover, t, ITextViewerExtension2.DEFAULT_HOVER_STATE_MASK);
				}
			} else
				sourceViewer.setTextHover(configuration.getTextHover(sourceViewer, t), t);
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.viewsupport.IViewPartInputProvider#getViewPartInput()
	 */
	public Object getViewPartInput() {
		return getEditorInput().getAdapter(IJavaElement.class);
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#doSetSelection(ISelection)
	 */
	@Override
	protected void doSetSelection(ISelection selection) {
		super.doSetSelection(selection);
		synchronizeOutlinePageSelection();
	}

	boolean isFoldingEnabled() {
		return JavaPlugin.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_FOLDING_ENABLED);
	}

	/*
	 * @see org.eclipse.ui.part.WorkbenchPart#getOrientation()
	 * @since 3.1
	 */
	@Override
	public int getOrientation() {
		return SWT.LEFT_TO_RIGHT;	//Java editors are always left to right by default
	}

	/*
	 * @see org.eclipse.ui.IWorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);

		fEditorSelectionChangedListener= new EditorSelectionChangedListener();
		fEditorSelectionChangedListener.install(getSelectionProvider());

		if (isSemanticHighlightingEnabled())
			installSemanticHighlighting();

		fBreadcrumb= createBreadcrumb();
		fIsBreadcrumbVisible= isBreadcrumbShown();
		if (fIsBreadcrumbVisible)
			showBreadcrumb();

		PlatformUI.getWorkbench().addWindowListener(fActivationListener);
	}

	@Override
	protected void configureSourceViewerDecorationSupport(SourceViewerDecorationSupport support) {

		fBracketMatcher.setSourceVersion(getPreferenceStore().getString(JavaCore.COMPILER_SOURCE));
		support.setCharacterPairMatcher(fBracketMatcher);
		support.setMatchingCharacterPainterPreferenceKeys(MATCHING_BRACKETS, MATCHING_BRACKETS_COLOR, HIGHLIGHT_BRACKET_AT_CARET_LOCATION, ENCLOSING_BRACKETS);

		super.configureSourceViewerDecorationSupport(support);
	}

	/**
	 * Returns the lock object for the given annotation model.
	 *
	 * @param annotationModel the annotation model
	 * @return the annotation model's lock object
	 * @since 3.0
	 */
	private Object getLockObject(IAnnotationModel annotationModel) {
		if (annotationModel instanceof ISynchronizable) {
			Object lock= ((ISynchronizable)annotationModel).getLockObject();
			if (lock != null)
				return lock;
		}
		return annotationModel;
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractDecoratedTextEditor#updateMarkerViews(org.eclipse.jface.text.source.Annotation)
	 * @since 3.2
	 */
	@Override
	protected void updateMarkerViews(Annotation annotation) {
		if (annotation instanceof IJavaAnnotation) {
			Iterator<IJavaAnnotation> e= ((IJavaAnnotation) annotation).getOverlaidIterator();
			if (e != null) {
				while (e.hasNext()) {
					Object o= e.next();
					if (o instanceof MarkerAnnotation) {
						super.updateMarkerViews((MarkerAnnotation)o);
						return;
					}
				}
			}
			return;
		}
		super.updateMarkerViews(annotation);
	}

	/**
	 * Finds and marks occurrence annotations.
	 *
	 * @since 3.0
	 */
	class OccurrencesFinderJob extends Job {

		private final IDocument fDocument;
		private final ISelection fSelection;
		private final ISelectionValidator fPostSelectionValidator;
		private boolean fCanceled= false;
		private final OccurrenceLocation[] fLocations;

		public OccurrencesFinderJob(IDocument document, OccurrenceLocation[] locations, ISelection selection) {
			super(JavaEditorMessages.JavaEditor_markOccurrences_job_name);
			fDocument= document;
			fSelection= selection;
			fLocations= locations;

			if (getSelectionProvider() instanceof ISelectionValidator)
				fPostSelectionValidator= (ISelectionValidator)getSelectionProvider();
			else
				fPostSelectionValidator= null;
		}

		// cannot use cancel() because it is declared final
		void doCancel() {
			fCanceled= true;
			cancel();
		}

		private boolean isCanceled(IProgressMonitor progressMonitor) {
			return fCanceled || progressMonitor.isCanceled()
				||  fPostSelectionValidator != null && !(fPostSelectionValidator.isValid(fSelection) || fForcedMarkOccurrencesSelection == fSelection)
				|| LinkedModeModel.hasInstalledModel(fDocument);
		}

		/*
		 * @see Job#run(org.eclipse.core.runtime.IProgressMonitor)
		 */
		@Override
		public IStatus run(IProgressMonitor progressMonitor) {
			if (isCanceled(progressMonitor))
				return Status.CANCEL_STATUS;

			ITextViewer textViewer= getViewer();
			if (textViewer == null)
				return Status.CANCEL_STATUS;

			IDocument document= textViewer.getDocument();
			if (document == null)
				return Status.CANCEL_STATUS;

			IDocumentProvider documentProvider= getDocumentProvider();
			if (documentProvider == null)
				return Status.CANCEL_STATUS;

			IAnnotationModel annotationModel= documentProvider.getAnnotationModel(getEditorInput());
			if (annotationModel == null)
				return Status.CANCEL_STATUS;

			// Add occurrence annotations
			int length= fLocations.length;
			Map<Annotation, Position> annotationMap= new HashMap<Annotation, Position>(length);
			for (int i= 0; i < length; i++) {

				if (isCanceled(progressMonitor))
					return Status.CANCEL_STATUS;

				OccurrenceLocation location= fLocations[i];
				Position position= new Position(location.getOffset(), location.getLength());

				String description= location.getDescription();
				String annotationType= (location.getFlags() == IOccurrencesFinder.F_WRITE_OCCURRENCE) ? "org.eclipse.jdt.ui.occurrences.write" : "org.eclipse.jdt.ui.occurrences"; //$NON-NLS-1$ //$NON-NLS-2$

				annotationMap.put(new Annotation(annotationType, false, description), position);
			}

			if (isCanceled(progressMonitor))
				return Status.CANCEL_STATUS;

			synchronized (getLockObject(annotationModel)) {
				if (annotationModel instanceof IAnnotationModelExtension) {
					((IAnnotationModelExtension)annotationModel).replaceAnnotations(fOccurrenceAnnotations, annotationMap);
				} else {
					removeOccurrenceAnnotations();
					Iterator<Entry<Annotation, Position>> iter= annotationMap.entrySet().iterator();
					while (iter.hasNext()) {
						Entry<Annotation, Position> mapEntry= iter.next();
						annotationModel.addAnnotation(mapEntry.getKey(), mapEntry.getValue());
					}
				}
				fOccurrenceAnnotations= annotationMap.keySet().toArray(new Annotation[annotationMap.keySet().size()]);
			}

			return Status.OK_STATUS;
		}
	}

	/**
	 * Updates the occurrences annotations based
	 * on the current selection.
	 *
	 * @param selection the text selection
	 * @param astRoot the compilation unit AST
	 * @since 3.0
	 */
	protected void updateOccurrenceAnnotations(ITextSelection selection, CompilationUnit astRoot) {

		if (fOccurrencesFinderJob != null)
			fOccurrencesFinderJob.cancel();

		if (!fMarkOccurrenceAnnotations)
			return;

		if (astRoot == null || selection == null)
			return;

		IDocument document= getSourceViewer().getDocument();
		if (document == null)
			return;

		boolean hasChanged= false;
		if (document instanceof IDocumentExtension4) {
			int offset= selection.getOffset();
			long currentModificationStamp= ((IDocumentExtension4)document).getModificationStamp();
			IRegion markOccurrenceTargetRegion= fMarkOccurrenceTargetRegion;
			hasChanged= currentModificationStamp != fMarkOccurrenceModificationStamp;
			if (markOccurrenceTargetRegion != null && !hasChanged) {
				if (markOccurrenceTargetRegion.getOffset() <= offset && offset <= markOccurrenceTargetRegion.getOffset() + markOccurrenceTargetRegion.getLength())
					return;
			}
			fMarkOccurrenceTargetRegion= JavaWordFinder.findWord(document, offset);
			fMarkOccurrenceModificationStamp= currentModificationStamp;
		}

		OccurrenceLocation[] locations= null;

		ASTNode selectedNode= NodeFinder.perform(astRoot, selection.getOffset(), selection.getLength());
		if (fMarkExceptions) {
			ExceptionOccurrencesFinder finder= new ExceptionOccurrencesFinder();
			if (finder.initialize(astRoot, selectedNode) == null) {
				locations= finder.getOccurrences();
			}
		}

		if (locations == null && fMarkMethodExitPoints) {
			MethodExitsFinder finder= new MethodExitsFinder();
			if (finder.initialize(astRoot, selectedNode) == null) {
				locations= finder.getOccurrences();
			}
		}

		if (locations == null && fMarkBreakContinueTargets) {
			BreakContinueTargetFinder finder= new BreakContinueTargetFinder();
			if (finder.initialize(astRoot, selectedNode) == null) {
				locations= finder.getOccurrences();
			}
		}

		if (locations == null && fMarkImplementors) {
			ImplementOccurrencesFinder finder= new ImplementOccurrencesFinder();
			if (finder.initialize(astRoot, selectedNode) == null) {
				locations= finder.getOccurrences();
			}
		}

		if (locations == null && selectedNode instanceof Name) {
			IBinding binding= ((Name)selectedNode).resolveBinding();
			if (binding != null && markOccurrencesOfType(binding)) {
				OccurrencesFinder finder= new OccurrencesFinder();
				if (finder.initialize(astRoot, selectedNode) == null) {
					locations= finder.getOccurrences();
				}
			}
		}

		if (locations == null) {
			if (!fStickyOccurrenceAnnotations)
				removeOccurrenceAnnotations();
			else if (hasChanged) // check consistency of current annotations
				removeOccurrenceAnnotations();
			return;
		}

		fOccurrencesFinderJob= new OccurrencesFinderJob(document, locations, selection);
		//fOccurrencesFinderJob.setPriority(Job.DECORATE);
		//fOccurrencesFinderJob.setSystem(true);
		//fOccurrencesFinderJob.schedule();
		fOccurrencesFinderJob.run(new NullProgressMonitor());
	}

	protected void installOccurrencesFinder(boolean forceUpdate) {
		fMarkOccurrenceAnnotations= true;

		fPostSelectionListenerWithAST= new ISelectionListenerWithAST() {
			public void selectionChanged(IEditorPart part, ITextSelection selection, CompilationUnit astRoot) {
				updateOccurrenceAnnotations(selection, astRoot);
			}
		};
		SelectionListenerWithASTManager.getDefault().addListener(this, fPostSelectionListenerWithAST);
		if (forceUpdate && getSelectionProvider() != null) {
			fForcedMarkOccurrencesSelection= getSelectionProvider().getSelection();
			ITypeRoot inputJavaElement= getInputJavaElement();
			if (inputJavaElement != null)
				updateOccurrenceAnnotations((ITextSelection)fForcedMarkOccurrencesSelection, SharedASTProvider.getAST(inputJavaElement, SharedASTProvider.WAIT_NO, getProgressMonitor()));
		}

		if (fOccurrencesFinderJobCanceler == null) {
			fOccurrencesFinderJobCanceler= new OccurrencesFinderJobCanceler();
			fOccurrencesFinderJobCanceler.install();
		}
	}

	protected void uninstallOccurrencesFinder() {
		fMarkOccurrenceAnnotations= false;

		if (fOccurrencesFinderJob != null) {
			fOccurrencesFinderJob.cancel();
			fOccurrencesFinderJob= null;
		}

		if (fOccurrencesFinderJobCanceler != null) {
			fOccurrencesFinderJobCanceler.uninstall();
			fOccurrencesFinderJobCanceler= null;
		}

		if (fPostSelectionListenerWithAST != null) {
			SelectionListenerWithASTManager.getDefault().removeListener(this, fPostSelectionListenerWithAST);
			fPostSelectionListenerWithAST= null;
		}

		removeOccurrenceAnnotations();
	}

	protected boolean isMarkingOccurrences() {
		IPreferenceStore store= getPreferenceStore();
		return store != null && store.getBoolean(PreferenceConstants.EDITOR_MARK_OCCURRENCES);
	}

	/**
	 * @return true if editor breadcrumbs are enabled
	 * @since 3.4
	 */
	protected boolean isBreadcrumbShown() {
		IPreferenceStore store= getPreferenceStore();
		String key= getBreadcrumbPreferenceKey();
		return store != null && key != null && store.getBoolean(key);
	}

	boolean markOccurrencesOfType(IBinding binding) {

		if (binding == null)
			return false;

		int kind= binding.getKind();

		if (fMarkTypeOccurrences && kind == IBinding.TYPE)
			return true;

		if (fMarkMethodOccurrences && kind == IBinding.METHOD)
			return true;

		if (kind == IBinding.VARIABLE) {
			IVariableBinding variableBinding= (IVariableBinding)binding;
			if (variableBinding.isField()) {
				int constantModifier= IModifierConstants.ACC_STATIC | IModifierConstants.ACC_FINAL;
				boolean isConstant= (variableBinding.getModifiers() & constantModifier) == constantModifier;
				if (isConstant)
					return fMarkConstantOccurrences;
				else
					return fMarkFieldOccurrences;
			}

			return fMarkLocalVariableypeOccurrences;
		}

		return false;
	}

	void removeOccurrenceAnnotations() {
		fMarkOccurrenceModificationStamp= IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;
		fMarkOccurrenceTargetRegion= null;

		IDocumentProvider documentProvider= getDocumentProvider();
		if (documentProvider == null)
			return;

		IAnnotationModel annotationModel= documentProvider.getAnnotationModel(getEditorInput());
		if (annotationModel == null || fOccurrenceAnnotations == null)
			return;

		synchronized (getLockObject(annotationModel)) {
			if (annotationModel instanceof IAnnotationModelExtension) {
				((IAnnotationModelExtension)annotationModel).replaceAnnotations(fOccurrenceAnnotations, null);
			} else {
				for (int i= 0, length= fOccurrenceAnnotations.length; i < length; i++)
					annotationModel.removeAnnotation(fOccurrenceAnnotations[i]);
			}
			fOccurrenceAnnotations= null;
		}
	}

	protected void uninstallOverrideIndicator() {
		if (fOverrideIndicatorManager != null) {
			fOverrideIndicatorManager.removeAnnotations();
			fOverrideIndicatorManager= null;
		}
	}

	protected void installOverrideIndicator(boolean provideAST) {
		uninstallOverrideIndicator();
		IAnnotationModel model= getDocumentProvider().getAnnotationModel(getEditorInput());
		final ITypeRoot inputElement= getInputJavaElement();

		if (model == null || inputElement == null)
			return;

		fOverrideIndicatorManager= new OverrideIndicatorManager(model, inputElement, null);

		if (provideAST) {
			CompilationUnit ast= SharedASTProvider.getAST(inputElement, SharedASTProvider.WAIT_ACTIVE_ONLY, getProgressMonitor());
			fOverrideIndicatorManager.reconciled(ast, true, getProgressMonitor());
		}
	}

	/**
	 * Tells whether override indicators are shown.
	 *
	 * @return <code>true</code> if the override indicators are shown
	 * @since 3.0
	 */
	protected boolean isShowingOverrideIndicators() {
		AnnotationPreference preference= getAnnotationPreferenceLookup().getAnnotationPreference(OverrideIndicatorManager.ANNOTATION_TYPE);
		IPreferenceStore store= getPreferenceStore();
		return getBoolean(store, preference.getHighlightPreferenceKey())
			|| getBoolean(store, preference.getVerticalRulerPreferenceKey())
			|| getBoolean(store, preference.getOverviewRulerPreferenceKey())
			|| getBoolean(store, preference.getTextPreferenceKey());
	}

	/**
	 * Returns the boolean preference for the given key.
	 *
	 * @param store the preference store
	 * @param key the preference key
	 * @return <code>true</code> if the key exists in the store and its value is <code>true</code>
	 * @since 3.0
	 */
	private boolean getBoolean(IPreferenceStore store, String key) {
		return key != null && store.getBoolean(key);
	}

	/**
	 * Determines whether the preference change encoded by the given event
	 * changes the override indication.
	 *
	 * @param event the event to be investigated
	 * @return <code>true</code> if event causes a change
	 * @since 3.0
	 */
	protected boolean affectsOverrideIndicatorAnnotations(PropertyChangeEvent event) {
		String key= event.getProperty();
		AnnotationPreference preference= getAnnotationPreferenceLookup().getAnnotationPreference(OverrideIndicatorManager.ANNOTATION_TYPE);
		if (key == null || preference == null)
			return false;

		return key.equals(preference.getHighlightPreferenceKey())
			|| key.equals(preference.getVerticalRulerPreferenceKey())
			|| key.equals(preference.getOverviewRulerPreferenceKey())
			|| key.equals(preference.getTextPreferenceKey());
	}

	/**
	 * @return <code>true</code> if Semantic Highlighting is enabled.
	 *
	 * @since 3.0
	 */
	private boolean isSemanticHighlightingEnabled() {
		return SemanticHighlightings.isEnabled(getPreferenceStore());
	}

	/**
	 * Install Semantic Highlighting.
	 *
	 * @since 3.0
	 */
	protected void installSemanticHighlighting() {
		if (fSemanticManager == null) {
			fSemanticManager= new SemanticHighlightingManager();
			fSemanticManager.install(this, (JavaSourceViewer) getSourceViewer(), JavaPlugin.getDefault().getJavaTextTools().getColorManager(), getPreferenceStore());
		}
	}

	/**
	 * Uninstall Semantic Highlighting.
	 *
	 * @since 3.0
	 */
	private void uninstallSemanticHighlighting() {
		if (fSemanticManager != null) {
			fSemanticManager.uninstall();
			fSemanticManager= null;
		}
	}

	/**
	 * Returns the Java element wrapped by this editors input.
	 *
	 * @return the Java element wrapped by this editors input.
	 * @since 3.0
	 */
	protected ITypeRoot getInputJavaElement() {
		return EditorUtility.getEditorInputJavaElement(this, false);
	}

	protected void updateStatusLine() {
		ITextSelection selection= (ITextSelection) getSelectionProvider().getSelection();
		Annotation annotation= getAnnotation(selection.getOffset(), selection.getLength());
		String message= null;
		if (annotation != null) {
			updateMarkerViews(annotation);
			if (annotation instanceof IJavaAnnotation && ((IJavaAnnotation) annotation).isProblem() || isProblemMarkerAnnotation(annotation))
				message= annotation.getText();
		}
		setStatusLineErrorMessage(null);
		setStatusLineMessage(message);
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#setStatusLineErrorMessage(java.lang.String)
	 * @since 3.5
	 */
	@Override
	public void setStatusLineErrorMessage(String message) {
		long now= System.currentTimeMillis();
		if (message != null || now - fErrorMessageTime > ERROR_MESSAGE_TIMEOUT) {
			super.setStatusLineErrorMessage(message);
			fErrorMessageTime= message != null ? now : 0;
		}
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#setStatusLineMessage(java.lang.String)
	 * @since 3.5
	 */
	@Override
	protected void setStatusLineMessage(String message) {
		if (System.currentTimeMillis() - fErrorMessageTime > ERROR_MESSAGE_TIMEOUT)
			super.setStatusLineMessage(message);
	}

	/**
	 * Tells whether the given annotation stands for a problem marker.
	 *
	 * @param annotation the annotation
	 * @return <code>true</code> if it is a problem marker
	 * @since 3.4
	 */
	private static boolean isProblemMarkerAnnotation(Annotation annotation) {
		if (!(annotation instanceof MarkerAnnotation))
			return false;
		try {
			return(((MarkerAnnotation)annotation).getMarker().isSubtypeOf(IMarker.PROBLEM));
		} catch (CoreException e) {
			return false;
		}
	}

	/**
	 * Jumps to the matching bracket.
	 */
	public void gotoMatchingBracket() {

		ISourceViewer sourceViewer= getSourceViewer();
		IDocument document= sourceViewer.getDocument();
		if (document == null)
			return;

		IRegion selection= getSignedSelection(sourceViewer);
		if (fPreviousSelections == null)
			initializePreviousSelectionList();

		IRegion region= fBracketMatcher.match(document, selection.getOffset(), selection.getLength());
		if (region == null) {
			region= fBracketMatcher.findEnclosingPeerCharacters(document, selection.getOffset(), selection.getLength());
			initializePreviousSelectionList();
			fPreviousSelections.add(selection);
		} else {
			if (fPreviousSelections.size() == 2) {
				if (!selection.equals(fPreviousSelections.get(1))) {
					initializePreviousSelectionList();
				}
			} else if (fPreviousSelections.size() == 3) {
				if (selection.equals(fPreviousSelections.get(2)) && !selection.equals(fPreviousSelections.get(0))) {
					IRegion originalSelection= fPreviousSelections.get(0);
					sourceViewer.setSelectedRange(originalSelection.getOffset(), originalSelection.getLength());
					sourceViewer.revealRange(originalSelection.getOffset(), originalSelection.getLength());
					initializePreviousSelectionList();
					return;
				}
				initializePreviousSelectionList();
			}
		}

		if (region == null) {
			setStatusLineErrorMessage(JavaEditorMessages.GotoMatchingBracket_error_noMatchingBracket);
			sourceViewer.getTextWidget().getDisplay().beep();
			return;
		}

		int offset= region.getOffset();
		int length= region.getLength();

		if (length < 1)
			return;

		int anchor= fBracketMatcher.getAnchor();
		// http://dev.eclipse.org/bugs/show_bug.cgi?id=34195
		int targetOffset= (ICharacterPairMatcher.RIGHT == anchor) ? offset + 1 : offset + length - 1;

		boolean visible= false;
		if (sourceViewer instanceof ITextViewerExtension5) {
			ITextViewerExtension5 extension= (ITextViewerExtension5) sourceViewer;
			visible= (extension.modelOffset2WidgetOffset(targetOffset) > -1);
		} else {
			IRegion visibleRegion= sourceViewer.getVisibleRegion();
			// http://dev.eclipse.org/bugs/show_bug.cgi?id=34195
			visible= (targetOffset >= visibleRegion.getOffset() && targetOffset <= visibleRegion.getOffset() + visibleRegion.getLength());
		}

		if (!visible) {
			setStatusLineErrorMessage(JavaEditorMessages.GotoMatchingBracket_error_bracketOutsideSelectedElement);
			sourceViewer.getTextWidget().getDisplay().beep();
			return;
		}

		int adjustment= getOffsetAdjustment(document, selection.getOffset() + selection.getLength(), selection.getLength());
		targetOffset+= adjustment;
		int direction= (selection.getLength() == 0) ? 0 : ((selection.getLength() > 0) ? 1 : -1);
		if (fPreviousSelections.size() == 1 && direction < 0) {
			targetOffset++;
		}

		if (fPreviousSelections.size() > 0) {
			fPreviousSelections.add(new Region(targetOffset, direction));
		}
		sourceViewer.setSelectedRange(targetOffset, direction);
		sourceViewer.revealRange(targetOffset, direction);
	}

	private void initializePreviousSelectionList() {
		fPreviousSelections= new ArrayList<IRegion>(3);
	}

	private static boolean isOpeningBracket(char character) {
		for (int i= 0; i < BRACKETS.length; i+= 2) {
			if (character == BRACKETS[i])
				return true;
		}
		return false;
	}

	private static boolean isClosingBracket(char character) {
		for (int i= 1; i < BRACKETS.length; i+= 2) {
			if (character == BRACKETS[i])
				return true;
		}
		return false;
	}

	/*
	 * Copy of org.eclipse.jface.text.source.DefaultCharacterPairMatcher.getOffsetAdjustment(IDocument, int, int)
	 */
	private static int getOffsetAdjustment(IDocument document, int offset, int length) {
		if (length == 0 || Math.abs(length) > 1)
			return 0;
		try {
			if (length < 0) {
				if (isOpeningBracket(document.getChar(offset))) {
					return 1;
				}
			} else {
				if (isClosingBracket(document.getChar(offset - 1))) {
					return -1;
				}
			}
		} catch (BadLocationException e) {
			//do nothing
		}
		return 0;
	}

	/*
	 * Copy of org.eclipse.jface.text.source.MatchingCharacterPainter.getSignedSelection(ISourceViewer)
	 */
	private static final IRegion getSignedSelection(ISourceViewer sourceViewer) {
		Point viewerSelection= sourceViewer.getSelectedRange();

		StyledText text= sourceViewer.getTextWidget();
		Point selection= text.getSelectionRange();
		if (text.getCaretOffset() == selection.x) {
			viewerSelection.x= viewerSelection.x + viewerSelection.y;
			viewerSelection.y= -viewerSelection.y;
		}

		return new Region(viewerSelection.x, viewerSelection.y);
	}

	@Override
	public ISelectionProvider getSelectionProvider() {
		return fSelectionProvider;
	}

	/**
	 * Returns the cached selected range, which allows
	 * to query it from a non-UI thread.
	 * <p>
	 * The result might be outdated if queried from a non-UI thread.</em></p>
	 *
	 * @return the caret offset in the master document
	 * @see ITextViewer#getSelectedRange()
	 * @since 3.3
	 */
	public Point getCachedSelectedRange() {
		return fCachedSelectedRange;
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#handleCursorPositionChanged()
	 * @since 3.3
	 */
	@Override
	protected void handleCursorPositionChanged() {
		super.handleCursorPositionChanged();
		fCachedSelectedRange= getViewer().getSelectedRange();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Overrides the default implementation to handle {@link IJavaAnnotation}.
	 * </p>
	 *
	 * @param offset the region offset
	 * @param length the region length
	 * @param forward <code>true</code> for forwards, <code>false</code> for backward
	 * @param annotationPosition the position of the found annotation
	 * @return the found annotation
	 * @since 3.2
	 */
	@Override
	protected Annotation findAnnotation(final int offset, final int length, boolean forward, Position annotationPosition) {

		Annotation nextAnnotation= null;
		Position nextAnnotationPosition= null;
		Annotation containingAnnotation= null;
		Position containingAnnotationPosition= null;
		boolean currentAnnotation= false;

		IDocument document= getDocumentProvider().getDocument(getEditorInput());
		int endOfDocument= document.getLength();
		int distance= Integer.MAX_VALUE;

		IAnnotationModel model= getDocumentProvider().getAnnotationModel(getEditorInput());
		if (model == null)
			return null;

		Iterator<Annotation> e= new JavaAnnotationIterator(model.getAnnotationIterator(), true);
		while (e.hasNext()) {
			Annotation a= e.next();
			if ((a instanceof IJavaAnnotation) && ((IJavaAnnotation)a).hasOverlay() || !isNavigationTarget(a))
				continue;

			Position p= model.getPosition(a);
			if (p == null)
				continue;

			if (forward && p.offset == offset || !forward && p.offset + p.getLength() == offset + length) {// || p.includes(offset)) {
				if (containingAnnotation == null || (forward && p.length >= containingAnnotationPosition.length || !forward && p.length >= containingAnnotationPosition.length)) {
					containingAnnotation= a;
					containingAnnotationPosition= p;
					currentAnnotation= p.length == length;
				}
			} else {
				int currentDistance= 0;

				if (forward) {
					currentDistance= p.getOffset() - offset;
					if (currentDistance < 0)
						currentDistance= endOfDocument + currentDistance;

					if (currentDistance < distance || currentDistance == distance && p.length < nextAnnotationPosition.length) {
						distance= currentDistance;
						nextAnnotation= a;
						nextAnnotationPosition= p;
					}
				} else {
					currentDistance= offset + length - (p.getOffset() + p.length);
					if (currentDistance < 0)
						currentDistance= endOfDocument + currentDistance;

					if (currentDistance < distance || currentDistance == distance && p.length < nextAnnotationPosition.length) {
						distance= currentDistance;
						nextAnnotation= a;
						nextAnnotationPosition= p;
					}
				}
			}
		}
		if (containingAnnotationPosition != null && (!currentAnnotation || nextAnnotation == null)) {
			annotationPosition.setOffset(containingAnnotationPosition.getOffset());
			annotationPosition.setLength(containingAnnotationPosition.getLength());
			return containingAnnotation;
		}
		if (nextAnnotationPosition != null) {
			annotationPosition.setOffset(nextAnnotationPosition.getOffset());
			annotationPosition.setLength(nextAnnotationPosition.getLength());
		}

		return nextAnnotation;
	}

	/**
	 * Returns the annotation overlapping with the given range or <code>null</code>.
	 *
	 * @param offset the region offset
	 * @param length the region length
	 * @return the found annotation or <code>null</code>
	 * @since 3.0
	 */
	private Annotation getAnnotation(int offset, int length) {
		IAnnotationModel model= getDocumentProvider().getAnnotationModel(getEditorInput());
		if (model == null)
			return null;

		Iterator<Annotation> parent;
		if (model instanceof IAnnotationModelExtension2)
			parent= ((IAnnotationModelExtension2)model).getAnnotationIterator(offset, length, true, true);
		else
			parent= model.getAnnotationIterator();

		Iterator<Annotation> e= new JavaAnnotationIterator(parent, false);
		while (e.hasNext()) {
			Annotation a= e.next();
			Position p= model.getPosition(a);
			if (p != null && p.overlapsWith(offset, length))
				return a;
		}
		return null;
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractDecoratedTextEditor#gotoAnnotation(boolean)
	 * @since 3.2
	 */
	@Override
	public Annotation gotoAnnotation(boolean forward) {
		fSelectionChangedViaGotoAnnotation= true;
		return super.gotoAnnotation(forward);
	}

	/**
	 * Computes and returns the source reference that includes the caret and
	 * serves as provider for the outline page selection and the editor range
	 * indication.
	 *
	 * @return the computed source reference
	 * @since 3.0
	 */
	protected ISourceReference computeHighlightRangeSourceReference() {
		ISourceViewer sourceViewer= getSourceViewer();
		if (sourceViewer == null)
			return null;

		StyledText styledText= sourceViewer.getTextWidget();
		if (styledText == null)
			return null;

		int caret= 0;
		if (sourceViewer instanceof ITextViewerExtension5) {
			ITextViewerExtension5 extension= (ITextViewerExtension5)sourceViewer;
			caret= extension.widgetOffset2ModelOffset(styledText.getCaretOffset());
		} else {
			int offset= sourceViewer.getVisibleRegion().getOffset();
			caret= offset + styledText.getCaretOffset();
		}

		IJavaElement element= getElementAt(caret, false);

		if ( !(element instanceof ISourceReference))
			return null;

		if (element.getElementType() == IJavaElement.IMPORT_DECLARATION) {

			IImportDeclaration declaration= (IImportDeclaration) element;
			IImportContainer container= (IImportContainer) declaration.getParent();
			ISourceRange srcRange= null;

			try {
				srcRange= container.getSourceRange();
			} catch (JavaModelException e) {
			}

			if (srcRange != null && srcRange.getOffset() == caret)
				return container;
		}

		return (ISourceReference) element;
	}

	/**
	 * Returns the most narrow java element including the given offset.
	 *
	 * @param offset the offset inside of the requested element
	 * @param reconcile <code>true</code> if editor input should be reconciled in advance
	 * @return the most narrow java element
	 * @since 3.0
	 */
	protected IJavaElement getElementAt(int offset, boolean reconcile) {
		return getElementAt(offset);
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractDecoratedTextEditor#createChangeHover()
	 */
	@Override
	protected LineChangeHover createChangeHover() {
		return new JavaChangeHover(IJavaPartitions.JAVA_PARTITIONING, getOrientation());
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#createNavigationActions()
	 */
	@Override
	protected void createNavigationActions() {
		super.createNavigationActions();

		final StyledText textWidget= getSourceViewer().getTextWidget();

		IAction action= new SmartLineStartAction(textWidget, false);
		action.setActionDefinitionId(ITextEditorActionDefinitionIds.LINE_START);
		setAction(ITextEditorActionDefinitionIds.LINE_START, action);

		action= new SmartLineStartAction(textWidget, true);
		action.setActionDefinitionId(ITextEditorActionDefinitionIds.SELECT_LINE_START);
		setAction(ITextEditorActionDefinitionIds.SELECT_LINE_START, action);

		action= new NavigatePreviousSubWordAction();
		action.setActionDefinitionId(ITextEditorActionDefinitionIds.WORD_PREVIOUS);
		setAction(ITextEditorActionDefinitionIds.WORD_PREVIOUS, action);
		textWidget.setKeyBinding(SWT.CTRL | SWT.ARROW_LEFT, SWT.NULL);

		action= new NavigateNextSubWordAction();
		action.setActionDefinitionId(ITextEditorActionDefinitionIds.WORD_NEXT);
		setAction(ITextEditorActionDefinitionIds.WORD_NEXT, action);
		textWidget.setKeyBinding(SWT.CTRL | SWT.ARROW_RIGHT, SWT.NULL);

		action= new SelectPreviousSubWordAction();
		action.setActionDefinitionId(ITextEditorActionDefinitionIds.SELECT_WORD_PREVIOUS);
		setAction(ITextEditorActionDefinitionIds.SELECT_WORD_PREVIOUS, action);
		textWidget.setKeyBinding(SWT.CTRL | SWT.SHIFT | SWT.ARROW_LEFT, SWT.NULL);

		action= new SelectNextSubWordAction();
		action.setActionDefinitionId(ITextEditorActionDefinitionIds.SELECT_WORD_NEXT);
		setAction(ITextEditorActionDefinitionIds.SELECT_WORD_NEXT, action);
		textWidget.setKeyBinding(SWT.CTRL | SWT.SHIFT | SWT.ARROW_RIGHT, SWT.NULL);
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractDecoratedTextEditor#createAnnotationRulerColumn(org.eclipse.jface.text.source.CompositeRuler)
	 * @since 3.2
	 */
	@Override
	protected IVerticalRulerColumn createAnnotationRulerColumn(CompositeRuler ruler) {
		if (!getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_ANNOTATION_ROLL_OVER))
			return super.createAnnotationRulerColumn(ruler);

		AnnotationRulerColumn column= new AnnotationRulerColumn(VERTICAL_RULER_WIDTH, getAnnotationAccess());
		column.setHover(new JavaExpandHover(ruler, getAnnotationAccess(), new IDoubleClickListener() {

			public void doubleClick(DoubleClickEvent event) {
				// for now: just invoke ruler double click action
				triggerAction(ITextEditorActionConstants.RULER_DOUBLE_CLICK);
			}

			private void triggerAction(String actionID) {
				IAction action= getAction(actionID);
				if (action != null) {
					if (action instanceof IUpdate)
						((IUpdate) action).update();
					// hack to propagate line change
					if (action instanceof ISelectionListener) {
						((ISelectionListener)action).selectionChanged(null, null);
					}
					if (action.isEnabled())
						action.run();
				}
			}

		}));

		return column;
	}

	/**
	 * Returns the folding action group, or <code>null</code> if there is none.
	 *
	 * @return the folding action group, or <code>null</code> if there is none
	 * @since 3.0
	 */
	protected FoldingActionGroup getFoldingActionGroup() {
		return fFoldingGroup;
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#performRevert()
	 */
	@Override
	protected void performRevert() {
		ProjectionViewer projectionViewer= (ProjectionViewer) getSourceViewer();
		projectionViewer.setRedraw(false);
		try {

			boolean projectionMode= projectionViewer.isProjectionMode();
			if (projectionMode) {
				projectionViewer.disableProjection();
				if (fProjectionModelUpdater != null)
					fProjectionModelUpdater.uninstall();
			}

			super.performRevert();

			if (projectionMode) {
				if (fProjectionModelUpdater != null)
					fProjectionModelUpdater.install(this, projectionViewer);
				projectionViewer.enableProjection();
			}

		} finally {
			projectionViewer.setRedraw(true);
		}
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#rulerContextMenuAboutToShow(org.eclipse.jface.action.IMenuManager)
	 */
	@Override
	protected void rulerContextMenuAboutToShow(IMenuManager menu) {
		super.rulerContextMenuAboutToShow(menu);
		IMenuManager foldingMenu= new MenuManager(JavaEditorMessages.Editor_FoldingMenu_name, "projection"); //$NON-NLS-1$
		menu.appendToGroup(ITextEditorActionConstants.GROUP_RULERS, foldingMenu);

		IAction action= getAction("FoldingToggle"); //$NON-NLS-1$
		foldingMenu.add(action);
		action= getAction("FoldingExpandAll"); //$NON-NLS-1$
		foldingMenu.add(action);
		action= getAction("FoldingCollapseAll"); //$NON-NLS-1$
		foldingMenu.add(action);
		action= getAction("FoldingRestore"); //$NON-NLS-1$
		foldingMenu.add(action);
		action= getAction("FoldingCollapseMembers"); //$NON-NLS-1$
		foldingMenu.add(action);
		action= getAction("FoldingCollapseComments"); //$NON-NLS-1$
		foldingMenu.add(action);
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractDecoratedTextEditor#collectContextMenuPreferencePages()
	 * @since 3.1
	 */
	@Override
	protected String[] collectContextMenuPreferencePages() {
		String[] inheritedPages= super.collectContextMenuPreferencePages();
		int length= 10;
		String[] result= new String[inheritedPages.length + length];
		result[0]= "org.eclipse.jdt.ui.preferences.JavaEditorPreferencePage"; //$NON-NLS-1$
		result[1]= "org.eclipse.jdt.ui.preferences.JavaTemplatePreferencePage"; //$NON-NLS-1$
		result[2]= "org.eclipse.jdt.ui.preferences.CodeAssistPreferencePage"; //$NON-NLS-1$
		result[3]= "org.eclipse.jdt.ui.preferences.CodeAssistPreferenceAdvanced"; //$NON-NLS-1$
		result[4]= "org.eclipse.jdt.ui.preferences.JavaEditorHoverPreferencePage"; //$NON-NLS-1$
		result[5]= "org.eclipse.jdt.ui.preferences.JavaEditorColoringPreferencePage"; //$NON-NLS-1$
		result[6]= "org.eclipse.jdt.ui.preferences.FoldingPreferencePage"; //$NON-NLS-1$
		result[7]= "org.eclipse.jdt.ui.preferences.MarkOccurrencesPreferencePage"; //$NON-NLS-1$
		result[8]= "org.eclipse.jdt.ui.preferences.SmartTypingPreferencePage"; //$NON-NLS-1$
		result[9]= "org.eclipse.jdt.ui.preferences.SaveParticipantPreferencePage"; //$NON-NLS-1$
		System.arraycopy(inheritedPages, 0, result, length, inheritedPages.length);
		return result;
	}

	/*
	 * @see AbstractTextEditor#getUndoRedoOperationApprover(IUndoContext)
	 * @since 3.1
	 */
	@Override
	protected IOperationApprover getUndoRedoOperationApprover(IUndoContext undoContext) {
		// since IResource is a more general way to compare java elements, we
		// use this as the preferred class for comparing objects.
		return new NonLocalUndoUserApprover(undoContext, this, new Object [] { getInputJavaElement() }, IResource.class);
	}

	/**
	 * Resets the foldings structure according to the folding
	 * preferences.
	 *
	 * @since 3.2
	 */
	public void resetProjection() {
		if (fProjectionModelUpdater != null) {
			fProjectionModelUpdater.initialize();
		}
	}

	/**
	 * Collapses all foldable members if supported by the folding
	 * structure provider.
	 *
	 * @since 3.2
	 */
	public void collapseMembers() {
		if (fProjectionModelUpdater instanceof IJavaFoldingStructureProviderExtension) {
			IJavaFoldingStructureProviderExtension extension= (IJavaFoldingStructureProviderExtension) fProjectionModelUpdater;
			extension.collapseMembers();
		}
	}

	/**
	 * Collapses all foldable comments if supported by the folding
	 * structure provider.
	 *
	 * @since 3.2
	 */
	public void collapseComments() {
		if (fProjectionModelUpdater instanceof IJavaFoldingStructureProviderExtension) {
			IJavaFoldingStructureProviderExtension extension= (IJavaFoldingStructureProviderExtension) fProjectionModelUpdater;
			extension.collapseComments();
		}
	}

	/**
	 * Returns the bracket matcher.
	 * 
	 * @return the bracket matcher
	 * @since 3.8
	 */
	public JavaPairMatcher getBracketMatcher() {
		return fBracketMatcher;
	}

	/**
	 * Checks whether called from Outline view.
	 * 
	 * @return <code>true</code> if called by Outline view
	 * @since 3.9
	 */
	private static boolean isCalledByOutline() {
		Class<?>[] elements= new AccessChecker().getClassContext();
		return elements[4].equals(ContentOutline.class) || elements[5].equals(ContentOutline.class);
	}

	private static final class AccessChecker extends SecurityManager {
		@Override
		public Class<?>[] getClassContext() {
			return super.getClassContext();
		}
	}

}

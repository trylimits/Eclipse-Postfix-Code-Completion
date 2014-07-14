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
package org.eclipse.jdt.internal.ui.infoviews;

import java.io.IOException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.source.SourceViewer;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.PlatformUI;

import org.eclipse.ui.texteditor.IAbstractTextEditorHelpContextIds;

import org.eclipse.jdt.core.ICodeAssist;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.util.Strings;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds;
import org.eclipse.jdt.ui.actions.JdtActionConstants;
import org.eclipse.jdt.ui.actions.OpenAction;
import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer;
import org.eclipse.jdt.internal.ui.text.JavaCodeReader;
import org.eclipse.jdt.internal.ui.text.SimpleJavaSourceViewerConfiguration;

/**
 * View which shows source for a given Java element.
 *
 * @since 3.0
 */
public class SourceView extends AbstractInfoView {

	/** Symbolic Java editor font name. */
	private static final String SYMBOLIC_FONT_NAME= "org.eclipse.jdt.ui.editors.textfont"; //$NON-NLS-1$

	/**
	 * Internal property change listener for handling changes in the editor's preferences.
	 *
	 * @since 3.0
	 */
	class PropertyChangeListener implements IPropertyChangeListener {
		/*
		 * @see IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
		 */
		public void propertyChange(PropertyChangeEvent event) {
			if (fViewer == null)
				return;

			if (fViewerConfiguration.affectsTextPresentation(event)) {
				fViewerConfiguration.handlePropertyChangeEvent(event);
				fViewer.invalidateTextPresentation();
			}
		}
	}

	/**
	 * Internal property change listener for handling workbench font changes.
	 */
	class FontPropertyChangeListener implements IPropertyChangeListener {
		/*
		 * @see IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
		 */
		public void propertyChange(PropertyChangeEvent event) {
			if (fViewer == null)
				return;

			String property= event.getProperty();

			if (SYMBOLIC_FONT_NAME.equals(property))
				setViewerFont();
		}
	}

	/**
	 * The Javadoc view's select all action.
	 */
	private static class SelectAllAction extends Action {

		private TextViewer fTextViewer;

		/**
		 * Creates the action.
		 *
		 * @param textViewer the text viewer
		 */
		public SelectAllAction(TextViewer textViewer) {
			super("selectAll"); //$NON-NLS-1$

			Assert.isNotNull(textViewer);
			fTextViewer= textViewer;

			setText(InfoViewMessages.SelectAllAction_label);
			setToolTipText(InfoViewMessages.SelectAllAction_tooltip);
			setDescription(InfoViewMessages.SelectAllAction_description);

			PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IAbstractTextEditorHelpContextIds.SELECT_ALL_ACTION);
		}

		/**
		 * Selects all in the viewer.
		 */
		@Override
		public void run() {
			fTextViewer.doOperation(ITextOperationTarget.SELECT_ALL);
		}
	}

	/** This view's source viewer */
	private SourceViewer fViewer;
	/** The viewers configuration */
	private JavaSourceViewerConfiguration fViewerConfiguration;
	/** The viewer's font properties change listener. */
	private IPropertyChangeListener fFontPropertyChangeListener= new FontPropertyChangeListener();
	/**
	 * The editor's property change listener.
	 * @since 3.0
	 */
	private IPropertyChangeListener fPropertyChangeListener= new PropertyChangeListener();
	/** The open action */
	private OpenAction fOpen;
	/** The number of removed leading comment lines. */
	private int fCommentLineCount;
	/** The select all action. */
	private SelectAllAction fSelectAllAction;
	/** Element opened by the open action. */
	private IJavaElement fLastOpenedElement;


	/*
	 * @see AbstractInfoView#internalCreatePartControl(Composite)
	 */
	@Override
	protected void internalCreatePartControl(Composite parent) {
		IPreferenceStore store= JavaPlugin.getDefault().getCombinedPreferenceStore();
		fViewer= new JavaSourceViewer(parent, null, null, false, SWT.V_SCROLL | SWT.H_SCROLL, store);
		fViewerConfiguration= new SimpleJavaSourceViewerConfiguration(JavaPlugin.getDefault().getJavaTextTools().getColorManager(), store, null, IJavaPartitions.JAVA_PARTITIONING, false);
		fViewer.configure(fViewerConfiguration);
		fViewer.setEditable(false);

		setViewerFont();
		JFaceResources.getFontRegistry().addListener(fFontPropertyChangeListener);

		store.addPropertyChangeListener(fPropertyChangeListener);

		getViewSite().setSelectionProvider(fViewer);
	}

	/*
	 * @see AbstractInfoView#internalCreatePartControl(Composite)
	 */
	@Override
	protected void createActions() {
		super.createActions();
		fSelectAllAction= new SelectAllAction(fViewer);

		// Setup OpenAction
		fOpen= new OpenAction(getViewSite()) {

			/*
			 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#getSelection()
			 */
			@Override
			public ISelection getSelection() {
				return convertToJavaElementSelection(fViewer.getSelection());
			}

			/*
			 * @see org.eclipse.jdt.ui.actions.OpenAction#run(IStructuredSelection)
			 */
			@Override
			public void run(IStructuredSelection selection) {
				if (selection.isEmpty()) {
					getShell().getDisplay().beep();
					return;
				}
				super.run(selection);
			}

			/*
			 * @see org.eclipse.jdt.ui.actions.OpenAction#getElementToOpen(Object)
			 */
			@Override
			public Object getElementToOpen(Object object) throws JavaModelException {
				if (object instanceof IJavaElement)
					fLastOpenedElement= (IJavaElement)object;
				else
					fLastOpenedElement= null;
				return super.getElementToOpen(object);
			}

			/*
			 * @see org.eclipse.jdt.ui.actions.OpenAction#run(Object[])
			 */
			@Override
			public void run(Object[] elements) {
				stopListeningForSelectionChanges();
				super.run(elements);
				startListeningForSelectionChanges();
			}
		};
	}


	/*
	 * @see org.eclipse.jdt.internal.ui.infoviews.AbstractInfoView#getSelectAllAction()
	 * @since 3.0
	 */
	@Override
	protected IAction getSelectAllAction() {
		return fSelectAllAction;
	}

	/*
	 * @see AbstractInfoView#fillActionBars(IActionBars)
	 */
	@Override
	protected void fillActionBars(IActionBars actionBars) {
		super.fillActionBars(actionBars);
		actionBars.setGlobalActionHandler(JdtActionConstants.OPEN, fOpen);
		fOpen.setActionDefinitionId(IJavaEditorActionDefinitionIds.OPEN_EDITOR);
	}

	/*
	 * @see AbstractInfoView#getControl()
	 */
	@Override
	protected Control getControl() {
		return fViewer.getControl();
	}

	/*
	 * @see AbstractInfoView#menuAboutToShow(IMenuManager)
	 */
	@Override
	public void menuAboutToShow(IMenuManager menu) {
		super.menuAboutToShow(menu);
		menu.appendToGroup(IContextMenuConstants.GROUP_OPEN, fOpen);
	}

	/*
	 * @see AbstractInfoView#setForeground(Color)
	 */
	@Override
	protected void setForeground(Color color) {
		fViewer.getTextWidget().setForeground(color);
	}

	/*
	 * @see AbstractInfoView#setBackground(Color)
	 */
	@Override
	protected void setBackground(Color color) {
		fViewer.getTextWidget().setBackground(color);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.infoviews.AbstractInfoView#getBackgroundColorKey()
	 * @since 3.2
	 */
	@Override
	protected String getBackgroundColorKey() {
		return "org.eclipse.jdt.ui.DeclarationView.backgroundColor";		 //$NON-NLS-1$
	}

	/**
	 * Converts the given selection to a structured selection
	 * containing Java elements.
	 *
	 * @param selection the selection
	 * @return a structured selection with Java elements
	 */
	private IStructuredSelection convertToJavaElementSelection(ISelection selection) {

		if (!(selection instanceof ITextSelection && fCurrentViewInput instanceof ISourceReference))
			return StructuredSelection.EMPTY;

		ITextSelection textSelection= (ITextSelection)selection;

		Object codeAssist= fCurrentViewInput.getAncestor(IJavaElement.COMPILATION_UNIT);
		if (codeAssist == null)
			codeAssist= fCurrentViewInput.getAncestor(IJavaElement.CLASS_FILE);

		if (codeAssist instanceof ICodeAssist) {
			IJavaElement[] elements= null;
			try {
				ISourceRange range= ((ISourceReference)fCurrentViewInput).getSourceRange();
				elements= ((ICodeAssist)codeAssist).codeSelect(range.getOffset() + getOffsetInUnclippedDocument(textSelection), textSelection.getLength());
			} catch (JavaModelException e) {
				return StructuredSelection.EMPTY;
			}
			if (elements != null && elements.length > 0) {
				return new StructuredSelection(elements[0]);
			} else
				return StructuredSelection.EMPTY;
		}

		return StructuredSelection.EMPTY;
	}

	/**
	 * Computes and returns the offset in the unclipped document based on the given text selection
	 * from the clipped document.
	 *
	 * @param textSelection the text selection
	 * @return the offset in the unclipped document or <code>-1</code> if the offset cannot be
	 *         computed
	 */
	private int getOffsetInUnclippedDocument(ITextSelection textSelection) {
		IDocument unclippedDocument= null;
		try {
			unclippedDocument= new Document(((ISourceReference)fCurrentViewInput).getSource());
		} catch (JavaModelException e) {
			return -1;
		}
		IDocument clippedDoc= (IDocument)fViewer.getInput();
		try {
			IRegion unclippedLineInfo= unclippedDocument.getLineInformation(fCommentLineCount + textSelection.getStartLine());
			IRegion clippedLineInfo= clippedDoc.getLineInformation(textSelection.getStartLine());
			int removedIndentation= unclippedLineInfo.getLength() - clippedLineInfo.getLength();
			int relativeLineOffset= textSelection.getOffset() - clippedLineInfo.getOffset();
			return unclippedLineInfo.getOffset() + removedIndentation + relativeLineOffset ;
		} catch (BadLocationException ex) {
			return -1;
		}
	}

	/*
	 * @see AbstractInfoView#internalDispose()
	 */
	@Override
	protected void internalDispose() {
		fViewer= null;
		fViewerConfiguration= null;
		JFaceResources.getFontRegistry().removeListener(fFontPropertyChangeListener);
		JavaPlugin.getDefault().getCombinedPreferenceStore().removePropertyChangeListener(fPropertyChangeListener);
	}

	/*
	 * @see org.eclipse.ui.part.WorkbenchPart#setFocus()
	 */
	@Override
	public void setFocus() {
		fViewer.getTextWidget().setFocus();
	}

	/*
	 * @see AbstractInfoView#computeInput(Object)
	 */
	@Override
	protected Object computeInput(Object input) {

		if (fViewer == null || !(input instanceof ISourceReference))
			return null;

		ISourceReference sourceRef= (ISourceReference)input;

		if (fLastOpenedElement != null && input instanceof IJavaElement && ((IJavaElement)input).getHandleIdentifier().equals(fLastOpenedElement.getHandleIdentifier())) {
			fLastOpenedElement= null;
			return null;
		} else {
			fLastOpenedElement= null;
		}

		String source;
		try {
			source= sourceRef.getSource();
		} catch (JavaModelException ex) {
			return ""; //$NON-NLS-1$
		}

		if (source == null)
			return ""; //$NON-NLS-1$

		source= removeLeadingComments(source);
		String delim= StubUtility.getLineDelimiterUsed((IJavaElement) input);

		String[] sourceLines= Strings.convertIntoLines(source);
		if (sourceLines == null || sourceLines.length == 0)
			return ""; //$NON-NLS-1$

		String firstLine= sourceLines[0];
		boolean firstCharNotWhitespace= firstLine != null && firstLine.length() > 0 && !Character.isWhitespace(firstLine.charAt(0));
		if (firstCharNotWhitespace)
			sourceLines[0]= ""; //$NON-NLS-1$
		IJavaProject project;
		if (input instanceof IJavaElement)
			project= ((IJavaElement) input).getJavaProject();
		else
			project= null;
		Strings.trimIndentation(sourceLines, project);

		if (firstCharNotWhitespace)
			sourceLines[0]= firstLine;

		return Strings.concatenate(sourceLines, delim);
	}

	/*
	 * @see AbstractInfoView#doSetInput(Object)
	 */
	@Override
	protected void doSetInput(Object input) {
		if (input instanceof IDocument)
			fViewer.setInput(input);
		else if (input == null)
			fViewer.setInput(new Document("")); //$NON-NLS-1$
		else {
			IDocument document= new Document(input.toString());
			JavaPlugin.getDefault().getJavaTextTools().setupJavaDocumentPartitioner(document, IJavaPartitions.JAVA_PARTITIONING);
			fViewer.setInput(document);
		}
	}

	/**
	 * Removes the leading comments from the given source.
	 *
	 * @param source the string with the source
	 * @return the source without leading comments
	 */
	private String removeLeadingComments(String source) {
		JavaCodeReader reader= new JavaCodeReader();
		IDocument document= new Document(source);
		int i;
		try {
			reader.configureForwardReader(document, 0, document.getLength(), true, false);
			int c= reader.read();
			while (c != -1 && (c == '\r' || c == '\n' || c == '\t')) {
				c= reader.read();
			}
			i= reader.getOffset();
			reader.close();
		} catch (IOException ex) {
			i= 0;
		} finally {
			try {
				reader.close();
			} catch (IOException ex) {
				JavaPlugin.log(ex);
			}
		}

		try {
			fCommentLineCount= document.getLineOfOffset(i);
		} catch (BadLocationException e) {
			fCommentLineCount= 0;
		}

		if (i < 0)
			return source;

		return source.substring(i);
	}

	/**
	 * Sets the font for this viewer sustaining selection and scroll position.
	 */
	private void setViewerFont() {
		Font font= JFaceResources.getFont(SYMBOLIC_FONT_NAME);

		if (fViewer.getDocument() != null) {

			Point selection= fViewer.getSelectedRange();
			int topIndex= fViewer.getTopIndex();

			StyledText styledText= fViewer.getTextWidget();
			Control parent= fViewer.getControl();

			parent.setRedraw(false);

			styledText.setFont(font);

			fViewer.setSelectedRange(selection.x , selection.y);
			fViewer.setTopIndex(topIndex);

			if (parent instanceof Composite) {
				Composite composite= (Composite) parent;
				composite.layout(true);
			}

			parent.setRedraw(true);


		} else {
			StyledText styledText= fViewer.getTextWidget();
			styledText.setFont(font);
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.infoviews.AbstractInfoView#getHelpContextId()
	 * @since 3.1
	 */
	@Override
	protected String getHelpContextId() {
		return IJavaHelpContextIds.SOURCE_VIEW;
	}
}

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
package org.eclipse.jdt.internal.ui.refactoring;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.resources.IFile;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.source.SourceViewer;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.part.FileEditorInput;

import org.eclipse.ui.texteditor.IDocumentProvider;

import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.eclipse.ltk.ui.refactoring.TextStatusContextViewer;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStringStatusContext;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.InternalClassFileEditorInput;
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer;


public class JavaStatusContextViewer extends TextStatusContextViewer {

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.refactoring.IStatusContextViewer#createControl(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		final SourceViewer viewer= getSourceViewer();
		viewer.unconfigure();
		IPreferenceStore store= JavaPlugin.getDefault().getCombinedPreferenceStore();
		viewer.configure(new JavaSourceViewerConfiguration(JavaPlugin.getDefault().getJavaTextTools().getColorManager(), store, null, null));
		viewer.getControl().setFont(JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT));
	}

	@Override
	protected SourceViewer createSourceViewer(Composite parent) {
		IPreferenceStore store= JavaPlugin.getDefault().getCombinedPreferenceStore();
		return new JavaSourceViewer(parent, null, null, false, SWT.LEFT_TO_RIGHT | SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI | SWT.FULL_SELECTION, store);
	}

	private IPackageFragmentRoot getPackageFragmentRoot(IClassFile file) {

		IJavaElement element= file.getParent();
		while (element != null && element.getElementType() != IJavaElement.PACKAGE_FRAGMENT_ROOT)
			element= element.getParent();

		return (IPackageFragmentRoot) element;
	}

	public void setInput(RefactoringStatusContext context) {
		if (context instanceof JavaStatusContext) {
			JavaStatusContext jsc= (JavaStatusContext)context;
			IDocument document= null;
			if (jsc.isBinary()) {
				IClassFile file= jsc.getClassFile();
				IEditorInput editorInput= new InternalClassFileEditorInput(file);
				document= getDocument(JavaPlugin.getDefault().getClassFileDocumentProvider(), editorInput);
				if (document.getLength() == 0)
					document= new Document(Messages.format(RefactoringMessages.JavaStatusContextViewer_no_source_found0, JavaElementLabels.getElementLabel(getPackageFragmentRoot(file), JavaElementLabels.ALL_DEFAULT)));
				updateTitle(file);
			} else {
				ICompilationUnit cunit= jsc.getCompilationUnit();
				if (cunit.isWorkingCopy()) {
					try {
						document= newJavaDocument(cunit.getSource());
					} catch (JavaModelException e) {
						// document is null which is a valid input.
					}
				} else {
					IEditorInput editorInput= new FileEditorInput((IFile)cunit.getResource());
					document= getDocument(JavaPlugin.getDefault().getCompilationUnitDocumentProvider(), editorInput);
				}
				if (document == null)
					document= new Document(RefactoringMessages.JavaStatusContextViewer_no_source_available);
				updateTitle(cunit);
			}
			setInput(document, createRegion(jsc.getSourceRange()));
		} else if (context instanceof JavaStringStatusContext) {
			updateTitle(null);
			JavaStringStatusContext sc= (JavaStringStatusContext)context;
			setInput(newJavaDocument(sc.getSource()), createRegion(sc.getSourceRange()));
		}
	}

	private IDocument newJavaDocument(String source) {
		IDocument result= new Document(source);
		JavaTextTools textTools= JavaPlugin.getDefault().getJavaTextTools();
		textTools.setupJavaDocumentPartitioner(result);
		return result;
	}

	private static IRegion createRegion(ISourceRange range) {
		return new Region(range.getOffset(), range.getLength());
	}

	private IDocument getDocument(IDocumentProvider provider, IEditorInput input) {
		if (input == null)
			return null;
		IDocument result= null;
		try {
			provider.connect(input);
			result= provider.getDocument(input);
		} catch (CoreException e) {
		} finally {
			provider.disconnect(input);
		}
		return result;
	}
}

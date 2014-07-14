/*******************************************************************************
 * Copyright (c) 2000, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Florian Albrecht <florian.albrecht@clintworld.de> - make NLSKeyHyperlink work with non-text editors - https://bugs.eclipse.org/bugs/show_bug.cgi?id=97228
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.javaeditor;

import org.eclipse.swt.widgets.Display;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IStorage;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPartitioningException;
import org.eclipse.jface.text.FindReplaceDocumentAdapter;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;

import org.eclipse.ui.texteditor.IEditorStatusLine;
import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ITypeBinding;

import org.eclipse.jdt.internal.corext.refactoring.nls.AccessorClassReference;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSHintHelper;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.nls.PropertyFileDocumentModel;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.IPropertiesFilePartitions;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.PropertyKeyHyperlinkDetector;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;


/**
 * NLS key hyperlink.
 *
 * @since 3.1
 */
public class NLSKeyHyperlink implements IHyperlink {

	private IRegion fRegion;
	private AccessorClassReference fAccessorClassReference;
	private IEditorPart fEditor;
	private final String fKeyName;


	/**
	 * Creates a new NLS key hyperlink.
	 * 
	 * @param region the region of the link
	 * @param keyName the name of the key
	 * @param ref the accessor class reference
	 * @param editor the editor which contains the hyperlink
	 */
	public NLSKeyHyperlink(IRegion region, String keyName, AccessorClassReference ref, IEditorPart editor) {
		Assert.isNotNull(region);
		Assert.isNotNull(keyName);
		Assert.isNotNull(ref);
		Assert.isNotNull(editor);

		fRegion= region;
		fKeyName= keyName;
		fAccessorClassReference= ref;
		fEditor= editor;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.IHyperlink#getHyperlinkRegion()
	 */
	public IRegion getHyperlinkRegion() {
		return fRegion;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.IHyperlink#open()
	 */
	public void open() {
		IStorage propertiesFile= null;
		try {
			ITypeBinding typeBinding= fAccessorClassReference.getBinding();
			propertiesFile= NLSHintHelper.getResourceBundle(typeBinding.getJavaElement().getJavaProject(), fAccessorClassReference);
		} catch (JavaModelException e) {
			// Don't open the file
		}
		openKeyInPropertiesFile(fKeyName, propertiesFile, fEditor);
	}

	/**
	 * Calculates the region of the NLS key in the properties file and reveals it in editor.
	 * 
	 * @param keyName the NLS key
	 * @param propertiesFile the properties file, or <code>null</code>
	 * @param activeEditor the active editor part
	 */
	public static void openKeyInPropertiesFile(String keyName, IStorage propertiesFile, IEditorPart activeEditor) {
		if (propertiesFile == null) {
			showErrorInStatusLine(activeEditor, JavaEditorMessages.Editor_OpenPropertiesFile_error_fileNotFound_dialogMessage);
			return;
		}

		IEditorPart editor;
		try {
			editor= EditorUtility.openInEditor(propertiesFile, true);
		} catch (PartInitException e) {
			handleOpenPropertiesFileFailed(propertiesFile, activeEditor);
			return;
		}

		// Reveal the key in the editor
		IEditorInput editorInput = editor.getEditorInput();
		IDocument document= null;
		if (editor instanceof ITextEditor)
			document= ((ITextEditor)editor).getDocumentProvider().getDocument(editorInput);
		else {
			IFile file= (IFile)editorInput.getAdapter(IFile.class);
			if (file != null) {
				IPath path= file.getFullPath();
				ITextFileBufferManager manager= FileBuffers.getTextFileBufferManager();
				try {
					manager.connect(path, LocationKind.IFILE, null);
					try {
						ITextFileBuffer buffer= manager.getTextFileBuffer(path, LocationKind.IFILE);
						if (buffer != null)
							document= buffer.getDocument();
					} finally {
						manager.disconnect(path, LocationKind.IFILE, null);
					}
				} catch (CoreException ex) {
					JavaPlugin.log(ex);
				}
			}
		}

		// Find key in document
		boolean found= false;
		IRegion region= null;
		if (document != null) {
			FindReplaceDocumentAdapter finder= new FindReplaceDocumentAdapter(document);
			PropertyKeyHyperlinkDetector detector= new PropertyKeyHyperlinkDetector();
			detector.setContext(editor);
			String key= PropertyFileDocumentModel.escape(keyName, false);
			int offset= document.getLength() - 1;
			try {
				while (!found && offset >= 0) {
					region= finder.find(offset, key, false, true, false, false);
					if (region == null)
						offset= -1;
					else {
						// test whether it's the key
						IHyperlink[] hyperlinks= detector.detectHyperlinks(null, region, false);
						if (hyperlinks != null) {
							for (int i= 0; i < hyperlinks.length; i++) {
								IRegion hyperlinkRegion= hyperlinks[i].getHyperlinkRegion();
								found= key.equals(document.get(hyperlinkRegion.getOffset(), hyperlinkRegion.getLength()));
							}
						} else if (document instanceof IDocumentExtension3) {
							// Fall back: test using properties file partitioning
							ITypedRegion partition= null;
							partition= ((IDocumentExtension3)document).getPartition(IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING, region.getOffset(), false);
							found= IDocument.DEFAULT_CONTENT_TYPE.equals(partition.getType())
							&& key.equals(document.get(partition.getOffset(), partition.getLength()).trim());
						}
						// Prevent endless loop (panic code, shouldn't be needed)
						if (offset == region.getOffset())
							offset= -1;
						else
							offset= region.getOffset();
					}
				}
			} catch (BadLocationException ex) {
				found= false;
			} catch (BadPartitioningException e1) {
				found= false;
			}
		}
		if (found)
			EditorUtility.revealInEditor(editor, region);
		else {
			EditorUtility.revealInEditor(editor, 0, 0);
			showErrorInStatusLine(editor, Messages.format(JavaEditorMessages.Editor_OpenPropertiesFile_error_keyNotFound, keyName));
		}
	}

	/**
	 * Shows the given message as error on the status line.
	 * 
	 * @param editor the editor part
	 * @param message message to be displayed
	 */
	private static void showErrorInStatusLine(IEditorPart editor, final String message) {
		final Display display= editor.getSite().getShell().getDisplay();
		display.beep();
		final IEditorStatusLine statusLine= (IEditorStatusLine)editor.getAdapter(IEditorStatusLine.class);
		if (statusLine != null) {
			display.asyncExec(new Runnable() {
				/*
				 * @see java.lang.Runnable#run()
				 */
				public void run() {
					statusLine.setMessage(true, message, null);
				}
			});
		}
	}

	/**
	 * Shows error message in status line if opening the properties file in editor fails.
	 * 
	 * @param propertiesFile the propertiesFile
	 * @param editor the editor part
	 */
	private static void handleOpenPropertiesFileFailed(IStorage propertiesFile, IEditorPart editor) {
		showErrorInStatusLine(editor, Messages.format(JavaEditorMessages.Editor_OpenPropertiesFile_error_openEditor_dialogMessage, BasicElementLabels.getPathLabel(propertiesFile.getFullPath(), true)));
	}

/*
 * @see org.eclipse.jdt.internal.ui.javaeditor.IHyperlink#getTypeLabel()
 */
	public String getTypeLabel() {
		return null;
	}

/*
 * @see org.eclipse.jdt.internal.ui.javaeditor.IHyperlink#getHyperlinkText()
 */
	public String getHyperlinkText() {
		String bundleName= fAccessorClassReference.getResourceBundleName();
		String propertyFileName= BasicElementLabels.getResourceName((bundleName.substring(bundleName.lastIndexOf('.') + 1, bundleName.length()) + NLSRefactoring.PROPERTY_FILE_EXT));
		return Messages.format(JavaEditorMessages.Editor_OpenPropertiesFile_hyperlinkText, new Object[] { propertyFileName });
	}
}

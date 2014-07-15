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
package org.eclipse.jdt.internal.ui.compare;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.core.resources.IFile;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextUtilities;

import org.eclipse.ui.IEditorInput;

import org.eclipse.compare.EditionSelectionDialog;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.DocumentRangeNode;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.util.Resources;

import org.eclipse.jdt.ui.IWorkingCopyManager;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;


class JavaAddElementFromHistoryImpl extends JavaHistoryActionImpl {

	private static final String BUNDLE_NAME= "org.eclipse.jdt.internal.ui.compare.AddFromHistoryAction"; //$NON-NLS-1$

	JavaAddElementFromHistoryImpl() {
		super(true);
	}

	@Override
	public void run(ISelection selection) {

		String errorTitle= CompareMessages.AddFromHistory_title;
		String errorMessage= CompareMessages.AddFromHistory_internalErrorMessage;
		Shell shell= getShell();

		ICompilationUnit cu= null;
		IParent parent= null;
		IMember input= null;

		// analyze selection
		if (selection.isEmpty()) {
			// no selection: we try to use the editor's input
			JavaEditor editor= getEditor();
			if (editor != null) {
				IEditorInput editorInput= editor.getEditorInput();
				IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();
				if (manager != null) {
					cu= manager.getWorkingCopy(editorInput);
					parent= cu;
				}
			}
		} else {
			input= getEditionElement(selection);
			if (input != null) {
				cu= input.getCompilationUnit();
				parent= input;
				input= null;

			} else {
				if (selection instanceof IStructuredSelection) {
					Object o= ((IStructuredSelection)selection).getFirstElement();
					if (o instanceof ICompilationUnit) {
						cu= (ICompilationUnit) o;
						parent= cu;
					}
				}
			}
		}

		if (parent == null || cu == null) {
			String invalidSelectionMessage= CompareMessages.AddFromHistory_invalidSelectionMessage;
			MessageDialog.openInformation(shell, errorTitle, invalidSelectionMessage);
			return;
		}

		IFile file= getFile(parent);
		if (file == null) {
			MessageDialog.openError(shell, errorTitle, errorMessage);
			return;
		}

		boolean inEditor= beingEdited(file);

		IStatus status= Resources.makeCommittable(file, shell);
		if (!status.isOK()) {
			return;
		}

		// get the document where to insert the text
		IPath path= file.getFullPath();
		ITextFileBufferManager bufferManager= FileBuffers.getTextFileBufferManager();
		ITextFileBuffer textFileBuffer= null;
		try {
			bufferManager.connect(path, LocationKind.IFILE, null);
			textFileBuffer= bufferManager.getTextFileBuffer(path, LocationKind.IFILE);
			IDocument document= textFileBuffer.getDocument();

			// configure EditionSelectionDialog and let user select an edition
			ITypedElement target= new JavaTextBufferNode(file, document, inEditor);
			ITypedElement[] editions= buildEditions(target, file);

			ResourceBundle bundle= ResourceBundle.getBundle(BUNDLE_NAME);
			EditionSelectionDialog d= new EditionSelectionDialog(shell, bundle);
			d.setAddMode(true);
			d.setHelpContextId(IJavaHelpContextIds.ADD_ELEMENT_FROM_HISTORY_DIALOG);
			ITypedElement selected= d.selectEdition(target, editions, parent);
			if (selected == null)
				return;	// user cancel

			ICompilationUnit cu2= cu;
			if (parent instanceof IMember)
				cu2= ((IMember)parent).getCompilationUnit();

			CompilationUnit root= parsePartialCompilationUnit(cu2);
			ASTRewrite rewriter= ASTRewrite.create(root.getAST());

			ITypedElement[] results= d.getSelection();
			for (int i= 0; i < results.length; i++) {

			    // create an AST node
				ASTNode newNode= createASTNode(rewriter, results[i], TextUtilities.getDefaultLineDelimiter(document), cu.getJavaProject());
				if (newNode == null) {
					MessageDialog.openError(shell, errorTitle, errorMessage);
					return;
				}

				// now determine where to put the new node
				if (newNode instanceof PackageDeclaration) {
				    rewriter.set(root, CompilationUnit.PACKAGE_PROPERTY, newNode, null);

				} else if (newNode instanceof ImportDeclaration) {
					ListRewrite lw= rewriter.getListRewrite(root, CompilationUnit.IMPORTS_PROPERTY);
					lw.insertFirst(newNode, null);

				} else {	// class, interface, enum, annotation, method, field

					if (parent instanceof ICompilationUnit) {	// top level
						ListRewrite lw= rewriter.getListRewrite(root, CompilationUnit.TYPES_PROPERTY);
						int index= ASTNodes.getInsertionIndex((BodyDeclaration)newNode, root.types());
						lw.insertAt(newNode, index, null);

					} else if (parent instanceof IType) {
						ASTNode declaration= getBodyContainer(root, (IType)parent);
						if (declaration instanceof TypeDeclaration || declaration instanceof AnnotationTypeDeclaration) {
							List<BodyDeclaration> container= ASTNodes.getBodyDeclarations(declaration);
							int index= ASTNodes.getInsertionIndex((BodyDeclaration)newNode, container);
							ListRewrite lw= rewriter.getListRewrite(declaration, ASTNodes.getBodyDeclarationsProperty(declaration));
							lw.insertAt(newNode, index, null);
						} else if (declaration instanceof EnumDeclaration) {
							List<EnumConstantDeclaration> container= ((EnumDeclaration)declaration).enumConstants();
							int index= ASTNodes.getInsertionIndex((FieldDeclaration)newNode, container);
							ListRewrite lw= rewriter.getListRewrite(declaration, EnumDeclaration.ENUM_CONSTANTS_PROPERTY);
							lw.insertAt(newNode, index, null);
						}
					} else {
						JavaPlugin.logErrorMessage("JavaAddElementFromHistoryImpl: unknown container " + parent); //$NON-NLS-1$
					}

				}
			}

			Map<String, String> options= null;
			IJavaProject javaProject= cu2.getJavaProject();
			if (javaProject != null)
				options= javaProject.getOptions(true);
			applyChanges(rewriter, document, textFileBuffer, shell, inEditor, options);

	 	} catch(InvocationTargetException ex) {
			ExceptionHandler.handle(ex, shell, errorTitle, errorMessage);

		} catch(InterruptedException ex) {
			// shouldn't be called because is not cancelable
			Assert.isTrue(false);

		} catch(CoreException ex) {
			ExceptionHandler.handle(ex, shell, errorTitle, errorMessage);

		} finally {
			try {
				if (textFileBuffer != null)
					bufferManager.disconnect(path, LocationKind.IFILE, null);
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		}
	}

	/**
	 * Creates a place holder ASTNode for the given element.
	 * @param rewriter
	 * @param element
	 * @param delimiter the line delimiter
	 * @param project
	 * @return a ASTNode or null
	 * @throws CoreException
	 */
	private ASTNode createASTNode(ASTRewrite rewriter, ITypedElement element, String delimiter, IJavaProject project) throws CoreException {
		if (element instanceof IStreamContentAccessor) {
			String content= JavaCompareUtilities.readString((IStreamContentAccessor)element);
			if (content != null) {
				content= trimTextBlock(content, delimiter, project);
				if (content != null) {
				    int type= getPlaceHolderType(element);
				    if (type != -1)
				        return rewriter.createStringPlaceholder(content, type);
				}
			}
		}
		return null;
	}

	/**
	 * Returns the corresponding place holder type for the given element.
	 * @return a place holder type (see ASTRewrite) or -1 if there is no corresponding placeholder
	 */
	private int getPlaceHolderType(ITypedElement element) {

		if (element instanceof DocumentRangeNode) {
			JavaNode jn= (JavaNode) element;
			switch (jn.getTypeCode()) {

			case JavaNode.PACKAGE:
			    return ASTNode.PACKAGE_DECLARATION;

			case JavaNode.CLASS:
			case JavaNode.INTERFACE:
				return ASTNode.TYPE_DECLARATION;

			case JavaNode.ENUM:
				return ASTNode.ENUM_DECLARATION;

			case JavaNode.ANNOTATION:
				return ASTNode.ANNOTATION_TYPE_DECLARATION;

			case JavaNode.CONSTRUCTOR:
			case JavaNode.METHOD:
				return ASTNode.METHOD_DECLARATION;

			case JavaNode.FIELD:
				return ASTNode.FIELD_DECLARATION;

			case JavaNode.INIT:
				return ASTNode.INITIALIZER;

			case JavaNode.IMPORT:
			case JavaNode.IMPORT_CONTAINER:
				return ASTNode.IMPORT_DECLARATION;

			case JavaNode.CU:
			    return ASTNode.COMPILATION_UNIT;
			}
		}
		return -1;
	}

	@Override
	protected boolean isEnabled(ISelection selection) {

		if (selection.isEmpty()) {
			JavaEditor editor= getEditor();
			if (editor != null) {
				// we check whether editor shows CompilationUnit
				IEditorInput editorInput= editor.getEditorInput();
				IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();
				return manager.getWorkingCopy(editorInput) != null;
			}
			return false;
		}

		if (selection instanceof IStructuredSelection) {
			Object o= ((IStructuredSelection)selection).getFirstElement();
			if (o instanceof ICompilationUnit)
				return true;
		}

		return super.isEnabled(selection);
	}
}

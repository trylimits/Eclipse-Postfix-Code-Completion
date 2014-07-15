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
package org.eclipse.jdt.internal.ui.javaeditor;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.osgi.framework.Bundle;

import org.eclipse.swt.SWT;

import org.eclipse.core.filesystem.IFileStore;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelectionProvider;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextSelection;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.MultiEditorInput;

import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.TextEditorAction;

import org.eclipse.ui.editors.text.TextFileDocumentProvider;

import org.eclipse.compare.rangedifferencer.IRangeComparator;
import org.eclipse.compare.rangedifferencer.RangeDifference;
import org.eclipse.compare.rangedifferencer.RangeDifferencer;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.IJavaStatusConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.compare.JavaCompareUtilities;
import org.eclipse.jdt.internal.ui.text.LineComparator;


/**
 * A number of routines for working with JavaElements in editors.
 *
 * Use 'isOpenInEditor' to test if an element is already open in a editor
 * Use 'openInEditor' to force opening an element in a editor
 * With 'getWorkingCopy' you get the working copy (element in the editor) of an element
 */
public class EditorUtility {

	/**
	 * Tests if a CU is currently shown in an editor
	 *
	 * @param inputElement the input element
	 * @return the IEditorPart if shown, null if element is not open in an editor
	 */
	public static IEditorPart isOpenInEditor(Object inputElement) {
		IEditorPart editor= findEditor(inputElement, false);
		if (editor != null)
			return editor;

		IEditorInput input= getEditorInput(inputElement);

		if (input != null) {
			IWorkbenchPage p= JavaPlugin.getActivePage();
			if (p != null) {
				return p.findEditor(input);
			}
		}

		return null;
	}

	/**
	 * Opens a Java editor for an element such as <code>IJavaElement</code>, <code>IFile</code>, or <code>IStorage</code>.
	 * The editor is activated by default.
	 *
	 * @param inputElement the input element
	 * @return an open editor or <code>null</code> if an external editor was opened
	 * @throws PartInitException if the editor could not be opened or the input element is not valid.
	 * Status code {@link IJavaStatusConstants#EDITOR_NO_EDITOR_INPUT} if opening the editor failed as
	 * no editor input could be created for the given element.
	 */
	public static IEditorPart openInEditor(Object inputElement) throws PartInitException {
		return openInEditor(inputElement, true);
	}

	/**
	 * Opens the editor currently associated with the given element (IJavaElement, IFile, IStorage...)
	 *
	 * @param inputElement the input element
	 * @param activate <code>true</code> if the editor should be activated
	 * @return an open editor or <code>null</code> if an external editor was opened
	 * @throws PartInitException if the editor could not be opened or the input element is not valid
	 * Status code {@link IJavaStatusConstants#EDITOR_NO_EDITOR_INPUT} if opening the editor failed as
	 * no editor input could be created for the given element.
	 */
	public static IEditorPart openInEditor(Object inputElement, boolean activate) throws PartInitException {

		if (inputElement instanceof IFile) {
			IFile file= (IFile) inputElement;
			if (!isClassFile(file))
				return openInEditor(file, activate);
			inputElement= JavaCore.createClassFileFrom(file);
		}

		IEditorPart editor= findEditor(inputElement, activate);
		if (editor != null)
			return editor;

		IEditorInput input= getEditorInput(inputElement);
		if (input == null)
			throwPartInitException(JavaEditorMessages.EditorUtility_no_editorInput, IJavaStatusConstants.EDITOR_NO_EDITOR_INPUT);

		return openInEditor(input, getEditorID(input), activate);
	}
	
	/**
	 * Tries to find the editor for the given input element.
	 * 
	 * @param inputElement the input element
	 * @param activate <code>true</code> if the found editor should be activated
	 * @return the editor or <code>null</code>
	 * @since 3.5
	 */
	private static IEditorPart findEditor(Object inputElement, boolean activate) {
		/*
		 * Support to navigate inside non-primary working copy.
		 * For now we only support to navigate inside the currently
		 * active editor.
		 *
		 * XXX: once we have FileStoreEditorInput as API,
		 * see https://bugs.eclipse.org/bugs/show_bug.cgi?id=111887
		 * we can fix this code by creating the correct editor input
		 * in getEditorInput(Object)
		 */
		if (inputElement instanceof IJavaElement) {
			ICompilationUnit cu= (ICompilationUnit)((IJavaElement)inputElement).getAncestor(IJavaElement.COMPILATION_UNIT);
			if (cu != null) {
				IWorkbenchPage page= JavaPlugin.getActivePage();
				IEditorPart editor= page != null ? editor= page.getActiveEditor() : null;
				if (editor != null) {
					boolean isCompareEditorInput= isCompareEditorInput(editor.getEditorInput());
					if (isCompareEditorInput || !JavaModelUtil.isPrimary(cu)) {
						IEditorInput editorInput;
						if (isCompareEditorInput)
							editorInput= (IEditorInput)editor.getAdapter(IEditorInput.class);
						else
							editorInput= editor.getEditorInput();
						IJavaElement editorCU= getEditorInputJavaElement(editorInput, false);
						if (cu.equals(editorCU)) {
							if (activate && page.getActivePart() != editor)
								page.activate(editor);
							return editor;
						}
					}
				}
			}
		}
		return null;
	}

	/*
	 * Avoid unnecessary loading of Compare plug-in
	 */
	private static boolean isComparePlugInActivated() {
		return Platform.getBundle("org.eclipse.compare").getState() == Bundle.ACTIVE; //$NON-NLS-1$
	}

	/*
	 * Avoid unnecessary loading of Compare plug-in
	 */
	public static boolean isCompareEditorInput(IEditorInput input) {
		return isComparePlugInActivated() && JavaCompareUtilities.isCompareEditorInput(input);
	}

	/**
	 * Selects a Java Element in an editor.
	 *
	 * @param part the editor part
	 * @param element the Java element to reveal
	 */
	public static void revealInEditor(IEditorPart part, IJavaElement element) {
		if (element == null)
			return;

		if (part instanceof JavaEditor) {
			((JavaEditor) part).setSelection(element);
			return;
		}

		// Support for non-Java editor
		try {
			ISourceRange range= null;
			if (element instanceof ICompilationUnit)
				return;
			else if (element instanceof IClassFile)
				return;

			if (element instanceof ISourceReference)
				range= ((ISourceReference)element).getNameRange();

			if (range != null)
				revealInEditor(part, range.getOffset(), range.getLength());
		} catch (JavaModelException e) {
			// don't reveal
		}
	}

	/**
	 * Selects and reveals the given region in the given editor part.
	 *
	 * @param part the editor part
	 * @param region the region
	 */
	public static void revealInEditor(IEditorPart part, IRegion region) {
		if (part != null && region != null)
			revealInEditor(part, region.getOffset(), region.getLength());
	}

	/**
	 * Selects and reveals the given offset and length in the given editor part.
	 * @param editor the editor part
	 * @param offset the offset
	 * @param length the length
	 */
	public static void revealInEditor(IEditorPart editor, final int offset, final int length) {
		if (editor instanceof ITextEditor) {
			((ITextEditor)editor).selectAndReveal(offset, length);
			return;
		}

		// Support for non-text editor - try IGotoMarker interface
		final IGotoMarker gotoMarkerTarget;
		if (editor instanceof IGotoMarker)
			gotoMarkerTarget= (IGotoMarker)editor;
		else
			gotoMarkerTarget= editor != null ? (IGotoMarker)editor.getAdapter(IGotoMarker.class) : null;
		if (gotoMarkerTarget != null) {
			final IEditorInput input= editor.getEditorInput();
			if (input instanceof IFileEditorInput) {
				WorkspaceModifyOperation op = new WorkspaceModifyOperation() {
					@Override
					protected void execute(IProgressMonitor monitor) throws CoreException {
						IMarker marker= null;
						try {
							marker= ((IFileEditorInput)input).getFile().createMarker(IMarker.TEXT);
							marker.setAttribute(IMarker.CHAR_START, offset);
							marker.setAttribute(IMarker.CHAR_END, offset + length);

							gotoMarkerTarget.gotoMarker(marker);

						} finally {
							if (marker != null)
								marker.delete();
						}
					}
				};

				try {
					op.run(null);
				} catch (InvocationTargetException ex) {
					// reveal failed
				} catch (InterruptedException e) {
					Assert.isTrue(false, "this operation can not be canceled"); //$NON-NLS-1$
				}
			}
			return;
		}

		/*
		 * Workaround: send out a text selection
		 * XXX: Needs to be improved, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=32214
		 */
		if (editor != null && editor.getEditorSite().getSelectionProvider() != null) {
			IEditorSite site= editor.getEditorSite();
			if (site == null)
				return;

			ISelectionProvider provider= editor.getEditorSite().getSelectionProvider();
			if (provider == null)
				return;

			provider.setSelection(new TextSelection(offset, length));
		}
	}

	private static IEditorPart openInEditor(IFile file, boolean activate) throws PartInitException {
		if (file == null)
			throwPartInitException(JavaEditorMessages.EditorUtility_file_must_not_be_null);

		IWorkbenchPage p= JavaPlugin.getActivePage();
		if (p == null)
			throwPartInitException(JavaEditorMessages.EditorUtility_no_active_WorkbenchPage);

		IEditorPart editorPart= IDE.openEditor(p, file, activate);
		initializeHighlightRange(editorPart);
		return editorPart;
	}

	private static IEditorPart openInEditor(IEditorInput input, String editorID, boolean activate) throws PartInitException {
		Assert.isNotNull(input);
		Assert.isNotNull(editorID);

		IWorkbenchPage p= JavaPlugin.getActivePage();
		if (p == null)
			throwPartInitException(JavaEditorMessages.EditorUtility_no_active_WorkbenchPage);

		IEditorPart editorPart= p.openEditor(input, editorID, activate);
		initializeHighlightRange(editorPart);
		return editorPart;
	}

	private static void throwPartInitException(String message, int code) throws PartInitException {
		IStatus status= new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, code, message, null);
		throw new PartInitException(status);
	}

	private static void throwPartInitException(String message) throws PartInitException {
		throwPartInitException(message, IStatus.OK);
	}

	private static void initializeHighlightRange(IEditorPart editorPart) {
		if (editorPart instanceof ITextEditor) {
			IAction toggleAction= editorPart.getEditorSite().getActionBars().getGlobalActionHandler(ITextEditorActionDefinitionIds.TOGGLE_SHOW_SELECTED_ELEMENT_ONLY);
			boolean enable= toggleAction != null;
			if (enable && editorPart instanceof JavaEditor)
				enable= JavaPlugin.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_SHOW_SEGMENTS);
			else
				enable= enable && toggleAction.isEnabled() && toggleAction.isChecked();
			if (enable) {
				if (toggleAction instanceof TextEditorAction) {
					// Reset the action
					((TextEditorAction)toggleAction).setEditor(null);
					// Restore the action
					((TextEditorAction)toggleAction).setEditor((ITextEditor)editorPart);
				} else {
					// Uncheck
					toggleAction.run();
					// Check
					toggleAction.run();
				}
			}
		}
	}

	public static String getEditorID(IEditorInput input) throws PartInitException {
		Assert.isNotNull(input);
		if (input instanceof IFileEditorInput)
			return IDE.getEditorDescriptor(((IFileEditorInput)input).getFile()).getId();

		String name= input.getName();

		if (input instanceof IClassFileEditorInput) {
			boolean hasSource;
			try {
				hasSource= ((IClassFileEditorInput) input).getClassFile().getSourceRange() != null;
			} catch (JavaModelException e) {
				hasSource= false;
			}

			if (!hasSource)
				name= "*.class without source"; //$NON-NLS-1$
		}

		return IDE.getEditorDescriptor(name).getId();
	}

	/**
	 * Returns the given editor's input as Java element.
	 *
	 * @param editor the editor
	 * @param primaryOnly if <code>true</code> only primary working copies will be returned
	 * @return the given editor's input as <code>ITypeRoot</code> or <code>null</code> if none
	 * @since 3.2
	 */
	public static ITypeRoot getEditorInputJavaElement(IEditorPart editor, boolean primaryOnly) {
		Assert.isNotNull(editor);
		return getEditorInputJavaElement(editor.getEditorInput(), primaryOnly);
	}

	private static ITypeRoot getEditorInputJavaElement(IEditorInput editorInput, boolean primaryOnly) {
		if (editorInput == null)
			return null;

		ICompilationUnit cu= JavaPlugin.getDefault().getWorkingCopyManager().getWorkingCopy(editorInput, primaryOnly);
		if (cu != null)
			return cu;

		IJavaElement je= (IJavaElement)editorInput.getAdapter(IJavaElement.class);
		if (je instanceof ITypeRoot)
			return (ITypeRoot)je;

		return null;
	}

	private static IEditorInput getEditorInput(IJavaElement element) {
		while (element != null) {
			if (element instanceof ICompilationUnit) {
				ICompilationUnit unit= ((ICompilationUnit) element).getPrimary();
					IResource resource= unit.getResource();
					if (resource instanceof IFile)
						return new FileEditorInput((IFile) resource);
			}

			if (element instanceof IClassFile)
				return new InternalClassFileEditorInput((IClassFile) element);

			element= element.getParent();
		}

		return null;
	}

	public static IEditorInput getEditorInput(Object input) {
		if (input instanceof IJavaElement)
			return getEditorInput((IJavaElement) input);

		if (input instanceof IFile)
			return new FileEditorInput((IFile) input);

		if (JavaModelUtil.isOpenableStorage(input))
			return new JarEntryEditorInput((IStorage)input);

		return null;
	}

	/**
	 * Returns the Java element edited in the current active editor.
	 *
	 * @return the Java element or <code>null</code> if the active editor doesn't edit a Java element
	 */
	public static IJavaElement getActiveEditorJavaInput() {
		IWorkbenchPage page= JavaPlugin.getActivePage();
		if (page != null) {
			IEditorPart part= page.getActiveEditor();
			if (part != null) {
				IEditorInput editorInput= part.getEditorInput();
				if (editorInput != null) {
					return JavaUI.getEditorInputJavaElement(editorInput);
				}
			}
		}
		return null;
	}

	/**
	 * Maps the localized modifier name to a code in the same
	 * manner as #findModifier.
	 *
	 * @param modifierName the modifier name
	 * @return the SWT modifier bit, or <code>0</code> if no match was found
	 * @since 2.1.1
	 */
	public static int findLocalizedModifier(String modifierName) {
		if (modifierName == null)
			return 0;

		if (modifierName.equalsIgnoreCase(Action.findModifierString(SWT.CTRL)))
			return SWT.CTRL;
		if (modifierName.equalsIgnoreCase(Action.findModifierString(SWT.SHIFT)))
			return SWT.SHIFT;
		if (modifierName.equalsIgnoreCase(Action.findModifierString(SWT.ALT)))
			return SWT.ALT;
		if (modifierName.equalsIgnoreCase(Action.findModifierString(SWT.COMMAND)))
			return SWT.COMMAND;

		return 0;
	}

	/**
	 * Returns the modifier string for the given SWT modifier
	 * modifier bits.
	 *
	 * @param stateMask	the SWT modifier bits
	 * @return the modifier string
	 * @since 2.1.1
	 */
	public static String getModifierString(int stateMask) {
		String modifierString= ""; //$NON-NLS-1$
		if ((stateMask & SWT.CTRL) == SWT.CTRL)
			modifierString= appendModifierString(modifierString, SWT.CTRL);
		if ((stateMask & SWT.ALT) == SWT.ALT)
			modifierString= appendModifierString(modifierString, SWT.ALT);
		if ((stateMask & SWT.SHIFT) == SWT.SHIFT)
			modifierString= appendModifierString(modifierString, SWT.SHIFT);
		if ((stateMask & SWT.COMMAND) == SWT.COMMAND)
			modifierString= appendModifierString(modifierString,  SWT.COMMAND);

		return modifierString;
	}

	/**
	 * Appends to modifier string of the given SWT modifier bit
	 * to the given modifierString.
	 *
	 * @param modifierString	the modifier string
	 * @param modifier			an int with SWT modifier bit
	 * @return the concatenated modifier string
	 * @since 2.1.1
	 */
	private static String appendModifierString(String modifierString, int modifier) {
		if (modifierString == null)
			modifierString= ""; //$NON-NLS-1$
		String newModifierString= Action.findModifierString(modifier);
		if (modifierString.length() == 0)
			return newModifierString;
		return Messages.format(JavaEditorMessages.EditorUtility_concatModifierStrings, new String[] {modifierString, newModifierString});
	}

	/**
	 * Returns the Java project for a given editor input or <code>null</code> if no corresponding
	 * Java project exists.
	 *
	 * @param input the editor input
	 * @return the corresponding Java project
	 *
	 * @since 3.0
	 */
	public static IJavaProject getJavaProject(IEditorInput input) {
		IJavaProject jProject= null;
		if (input instanceof IFileEditorInput) {
			IProject project= ((IFileEditorInput)input).getFile().getProject();
			if (project != null) {
				jProject= JavaCore.create(project);
				if (!jProject.exists())
					jProject= null;
			}
		} else if (input instanceof IClassFileEditorInput) {
			jProject= ((IClassFileEditorInput)input).getClassFile().getJavaProject();
		}
		return jProject;
	}

	/**
	 * Returns an array of all editors that have an unsaved content. If the identical content is
	 * presented in more than one editor, only one of those editor parts is part of the result.
	 * @param skipNonResourceEditors if <code>true</code>, editors whose inputs do not adapt to {@link IResource}
	 * are not saved
	 *
	 * @return an array of dirty editor parts
	 * @since 3.4
	 */
	public static IEditorPart[] getDirtyEditors(boolean skipNonResourceEditors) {
		Set<IEditorInput> inputs= new HashSet<IEditorInput>();
		List<IEditorPart> result= new ArrayList<IEditorPart>(0);
		IWorkbench workbench= PlatformUI.getWorkbench();
		IWorkbenchWindow[] windows= workbench.getWorkbenchWindows();
		for (int i= 0; i < windows.length; i++) {
			IWorkbenchPage[] pages= windows[i].getPages();
			for (int x= 0; x < pages.length; x++) {
				IEditorPart[] editors= pages[x].getDirtyEditors();
				for (int z= 0; z < editors.length; z++) {
					IEditorPart ep= editors[z];
					IEditorInput input= ep.getEditorInput();
					if (inputs.add(input)) {
						if (!skipNonResourceEditors || isResourceEditorInput(input)) {
							result.add(ep);
						}
					}
				}
			}
		}
		return result.toArray(new IEditorPart[result.size()]);
	}

	private static boolean isResourceEditorInput(IEditorInput input) {
		if (input instanceof MultiEditorInput) {
			IEditorInput[] inputs= ((MultiEditorInput) input).getInput();
			for (int i= 0; i < inputs.length; i++) {
				if (inputs[i].getAdapter(IResource.class) != null) {
					return true;
				}
			}
		} else if (input.getAdapter(IResource.class) != null) {
			return true;
		}
		return false;
	}

	private static boolean isClassFile(IFile file) {
		IContentDescription contentDescription;
		try {
			contentDescription= file.getContentDescription();
		} catch (CoreException e) {
			contentDescription= null;
		}
		if (contentDescription == null)
			return false;

		IContentType contentType= contentDescription.getContentType();
		if (contentType == null)
			return false;

		return "org.eclipse.jdt.core.javaClass".equals(contentType.getId()); //$NON-NLS-1$
	}

	/**
	 * Returns the editors to save before performing global Java-related
	 * operations.
	 *
	 * @param saveUnknownEditors <code>true</code> iff editors with unknown buffer management should also be saved
	 * @return the editors to save
	 * @since 3.3
	 */
	public static IEditorPart[] getDirtyEditorsToSave(boolean saveUnknownEditors) {
		Set<IEditorInput> inputs= new HashSet<IEditorInput>();
		List<IEditorPart> result= new ArrayList<IEditorPart>(0);
		IWorkbench workbench= PlatformUI.getWorkbench();
		IWorkbenchWindow[] windows= workbench.getWorkbenchWindows();
		for (int i= 0; i < windows.length; i++) {
			IWorkbenchPage[] pages= windows[i].getPages();
			for (int x= 0; x < pages.length; x++) {
				IEditorPart[] editors= pages[x].getDirtyEditors();
				for (int z= 0; z < editors.length; z++) {
					IEditorPart ep= editors[z];
					IEditorInput input= ep.getEditorInput();
					if (!mustSaveDirtyEditor(ep, input, saveUnknownEditors))
						continue;

					if (inputs.add(input))
						result.add(ep);
				}
			}
		}
		return result.toArray(new IEditorPart[result.size()]);
	}

	/*
	 * @since 3.3
	 */
	private static boolean mustSaveDirtyEditor(IEditorPart ep, IEditorInput input, boolean saveUnknownEditors) {
		/*
		 * Goal: save all editors that could interfere with refactoring operations.
		 *
		 * Always save all editors for compilation units that are not working copies.
		 * (Leaving them dirty would cause problems, since the file buffer could have been
		 * modified but the Java model is not reconciled.)
		 *
		 * If <code>saveUnknownEditors</code> is <code>true</code>, save all editors
		 * whose implementation is probably not based on file buffers.
		 */
		IResource resource= (IResource) input.getAdapter(IResource.class);
		if (resource == null)
			return saveUnknownEditors;

		IJavaElement javaElement= JavaCore.create(resource);
		if (javaElement instanceof ICompilationUnit) {
			ICompilationUnit cu= (ICompilationUnit) javaElement;
			if (!cu.isWorkingCopy()) {
				return true;
			}
		}

		if (! (ep instanceof ITextEditor))
			return saveUnknownEditors;

		ITextEditor textEditor= (ITextEditor) ep;
		IDocumentProvider documentProvider= textEditor.getDocumentProvider();
		if (! (documentProvider instanceof TextFileDocumentProvider))
			return saveUnknownEditors;

		return false;
	}

	/**
	 * Return the regions of all lines which have changed in the given buffer since the last save
	 * occurred. Each region in the result spans over the size of at least one line. If successive
	 * lines have changed a region spans over the size of all successive lines. The regions include
	 * line delimiters.
	 * 
	 * @param buffer the buffer to compare contents from
	 * @param monitor to report progress to
	 * @return the regions of the changed lines
	 * @throws CoreException if something goes wrong
	 * @since 3.4
	 */
	public static IRegion[] calculateChangedLineRegions(final ITextFileBuffer buffer, final IProgressMonitor monitor) throws CoreException {
		final IRegion[][] result= new IRegion[1][];
		final IStatus[] errorStatus= new IStatus[] { Status.OK_STATUS };

		try {
			SafeRunner.run(new ISafeRunnable() {

				/*
				 * @see org.eclipse.core.runtime.ISafeRunnable#handleException(java.lang.Throwable)
				 */
				public void handleException(Throwable exception) {
					JavaPlugin.log(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IJavaStatusConstants.EDITOR_CHANGED_REGION_CALCULATION, exception.getLocalizedMessage(), exception));
					String msg= JavaEditorMessages.CompilationUnitDocumentProvider_error_calculatingChangedRegions;
					errorStatus[0]= new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IJavaStatusConstants.EDITOR_CHANGED_REGION_CALCULATION, msg, exception);
					result[0]= null;
				}

				/*
				 * @see org.eclipse.core.runtime.ISafeRunnable#run()
				 */
				public void run() throws Exception {
					monitor.beginTask(JavaEditorMessages.CompilationUnitDocumentProvider_calculatingChangedRegions_message, 20);
					IFileStore fileStore= buffer.getFileStore();

					ITextFileBufferManager fileBufferManager= FileBuffers.createTextFileBufferManager();
					fileBufferManager.connectFileStore(fileStore, getSubProgressMonitor(monitor, 15));
					try {
						IDocument currentDocument= buffer.getDocument();
						IDocument oldDocument= ((ITextFileBuffer) fileBufferManager.getFileStoreFileBuffer(fileStore)).getDocument();

						result[0]= getChangedLineRegions(oldDocument, currentDocument);
					} finally {
						fileBufferManager.disconnectFileStore(fileStore, getSubProgressMonitor(monitor, 5));
						monitor.done();
					}
				}

				/**
				 * Return regions of all lines which differ comparing <code>oldDocument</code>s
				 * content with <code>currentDocument</code>s content. Successive lines are merged
				 * into one region.
				 * 
				 * @param oldDocument a document containing the old content
				 * @param currentDocument a document containing the current content
				 * @return the changed regions
				 * @throws BadLocationException if fetching the line information fails
				 */
				private IRegion[] getChangedLineRegions(IDocument oldDocument, IDocument currentDocument) throws BadLocationException {
					/*
					 * Do not change the type of those local variables. We use Object
					 * here in order to prevent loading of the Compare plug-in at load
					 * time of this class.
					 */
					Object leftSide= new LineComparator(oldDocument);
					Object rightSide= new LineComparator(currentDocument);

					RangeDifference[] differences= RangeDifferencer.findDifferences((IRangeComparator) leftSide, (IRangeComparator) rightSide);

					//It holds that:
					//1. Ranges are sorted:
					//     forAll r1,r2 element differences: indexOf(r1)<indexOf(r2) -> r1.rightStart()<r2.rightStart();
					//2. Successive changed lines are merged into on RangeDifference
					//     forAll r1,r2 element differences: r1.rightStart()<r2.rightStart() -> r1.rightEnd()<r2.rightStart

					ArrayList<IRegion> regions= new ArrayList<IRegion>();
					for (int i= 0; i < differences.length; i++) {
						RangeDifference curr= differences[i];
						if (curr.kind() == RangeDifference.CHANGE && curr.rightLength() > 0) {
							int startLine= curr.rightStart();
							int endLine= curr.rightEnd() - 1;

							IRegion startLineRegion= currentDocument.getLineInformation(startLine);
							if (startLine == endLine) {
								regions.add(startLineRegion);
							} else {
								IRegion endLineRegion= currentDocument.getLineInformation(endLine);
								int startOffset= startLineRegion.getOffset();
								int endOffset= endLineRegion.getOffset() + endLineRegion.getLength();
								regions.add(new Region(startOffset, endOffset - startOffset));
							}
						}
					}

					return regions.toArray(new IRegion[regions.size()]);
				}
			});
		} finally {
			if (!errorStatus[0].isOK())
				throw new CoreException(errorStatus[0]);
		}

		return result[0];
	}

	/**
	 * Creates and returns a new sub-progress monitor for the
	 * given parent monitor.
	 *
	 * @param monitor the parent progress monitor
	 * @param ticks the number of work ticks allocated from the parent monitor
	 * @return the new sub-progress monitor
	 * @since 3.4
	 */
	private static IProgressMonitor getSubProgressMonitor(IProgressMonitor monitor, int ticks) {
		if (monitor != null)
			return new SubProgressMonitor(monitor, ticks, SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK);

		return new NullProgressMonitor();
	}

}

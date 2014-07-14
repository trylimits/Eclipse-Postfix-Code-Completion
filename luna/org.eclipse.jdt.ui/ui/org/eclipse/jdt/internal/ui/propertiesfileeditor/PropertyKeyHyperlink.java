/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.propertiesfileeditor;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import com.ibm.icu.text.Collator;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.window.Window;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IStorageEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.TwoPaneElementSelector;
import org.eclipse.ui.model.IWorkbenchAdapter;
import org.eclipse.ui.model.WorkbenchLabelProvider;

import org.eclipse.ui.texteditor.IEditorStatusLine;
import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.search.core.text.TextSearchEngine;
import org.eclipse.search.core.text.TextSearchMatchAccess;
import org.eclipse.search.core.text.TextSearchRequestor;
import org.eclipse.search.core.text.TextSearchScope;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.SearchUtils;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.PatternConstructor;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;


/**
 * Properties key hyperlink.
 * 
 * @since 3.1
 */
public class PropertyKeyHyperlink implements IHyperlink {


	private static class KeyReference extends PlatformObject implements IWorkbenchAdapter, Comparable<KeyReference> {

		private static final Collator fgCollator= Collator.getInstance();

		private IResource resource;
		private IJavaElement element;
		private int offset;
		private int length;
		private final boolean showLineNumber;


		private KeyReference(IResource resource, IJavaElement element, int offset, int length, boolean showLineNumber) {
			Assert.isNotNull(resource);
			this.resource= resource;
			this.offset= offset;
			this.length= length;
			this.element= element;
			this.showLineNumber= showLineNumber;
		}

		/*
		 * @see org.eclipse.core.runtime.IAdaptable#getAdapter(java.lang.Class)
		 */
		@Override
		public Object getAdapter(Class adapter) {
			if (adapter == IWorkbenchAdapter.class)
				return this;
			else
				return super.getAdapter(adapter);
		}
		/*
		 * @see org.eclipse.ui.model.IWorkbenchAdapter#getChildren(java.lang.Object)
		 */
		public Object[] getChildren(Object o) {
			return null;
		}
		/*
		 * @see org.eclipse.ui.model.IWorkbenchAdapter#getImageDescriptor(java.lang.Object)
		 */
		public ImageDescriptor getImageDescriptor(Object object) {
			IWorkbenchAdapter wbAdapter= (IWorkbenchAdapter) resource.getAdapter(IWorkbenchAdapter.class);
			if (wbAdapter != null)
				return wbAdapter.getImageDescriptor(resource);
			return null;
		}
		/*
		 * @see org.eclipse.ui.model.IWorkbenchAdapter#getLabel(java.lang.Object)
		 */
		public String getLabel(Object o) {

			ITextFileBufferManager manager= FileBuffers.getTextFileBufferManager();
			try {
				manager.connect(resource.getFullPath(), LocationKind.NORMALIZE, null);
				try {
					ITextFileBuffer buffer= manager.getTextFileBuffer(resource.getFullPath(), LocationKind.NORMALIZE);
					IDocument document= buffer.getDocument();
					if (document != null) {
						int line= document.getLineOfOffset(offset) + 1;
						String pathLabel= BasicElementLabels.getPathLabel(resource.getFullPath(), false);
						Object[] args= new Object[] { new Integer(line), pathLabel };
						return showLineNumber ? Messages.format(PropertiesFileEditorMessages.OpenAction_SelectionDialog_elementLabel, args) : pathLabel;
					}
				} finally {
					manager.disconnect(resource.getFullPath(), LocationKind.NORMALIZE, null);
				}
			} catch (CoreException e) {
				JavaPlugin.log(e.getStatus());
			} catch (BadLocationException e) {
				JavaPlugin.log(e);
			}

			return BasicElementLabels.getPathLabel(resource.getFullPath(), false);
		}
		/*
		 * @see org.eclipse.ui.model.IWorkbenchAdapter#getParent(java.lang.Object)
		 */
		public Object getParent(Object o) {
			return null;
		}

		public int compareTo(KeyReference otherRef) {
			String thisPath= resource.getFullPath().toString();
			String otherPath= otherRef.resource.getFullPath().toString();
			int result= fgCollator.compare(thisPath, otherPath);
			if (result != 0)
				return result;
			else
				return offset - otherRef.offset;
		}
	}


	private static class ResultCollector extends TextSearchRequestor {

		private List<KeyReference> fResult;
		private boolean fIsKeyDoubleQuoted;

		public ResultCollector(List<KeyReference> result, boolean isKeyDoubleQuoted) {
			fResult= result;
			fIsKeyDoubleQuoted= isKeyDoubleQuoted;
		}

		@Override
		public boolean acceptPatternMatch(TextSearchMatchAccess matchAccess) throws CoreException {
			int start= matchAccess.getMatchOffset();
			int length= matchAccess.getMatchLength();

			if (fIsKeyDoubleQuoted) {
				start= start + 1;
				length= length - 2;
			}
			fResult.add(new KeyReference(matchAccess.getFile(), null, start, length, true));
			return true;
		}
	}


	private IRegion fRegion;
	private String fPropertiesKey;
	private Shell fShell;
	private IStorage fStorage;
	private ITextEditor fEditor;
	private boolean fIsFileEditorInput;

	/**
	 * Creates a new properties key hyperlink.
	 *
	 * @param region the region
	 * @param key the properties key
	 * @param editor the text editor
	 */
	public PropertyKeyHyperlink(IRegion region, String key, ITextEditor editor) {
		Assert.isNotNull(region);
		Assert.isNotNull(key);
		Assert.isNotNull(editor);

		fRegion= region;
		fPropertiesKey= key;
		fEditor= editor;
		fIsFileEditorInput= fEditor.getEditorInput() instanceof IFileEditorInput;
		IStorageEditorInput storageEditorInput= (IStorageEditorInput)fEditor.getEditorInput();
		fShell= fEditor.getEditorSite().getShell();
		try {
			fStorage= storageEditorInput.getStorage();
		} catch (CoreException e) {
			fStorage= null;
		}
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
		// Search the key
		KeyReference[] references= search(fPropertiesKey);
		if (references == null)
			return; // canceled by the user

		if (references.length == 0) {
			String message= PropertiesFileEditorMessages.OpenAction_error_messageNoResult;
			showErrorInStatusLine(message);
			return;
		}

		open(references);

	}

	private void open(KeyReference[] keyReferences) {
		Assert.isLegal(keyReferences != null && keyReferences.length > 0);

		if (keyReferences.length == 1)
			open(keyReferences[0]);
		else
			open(select(keyReferences));
	}

	/**
	 * Opens a dialog which allows to select a key reference.
	 *
	 * @param keyReferences the array of key references
	 * @return the selected key reference or <code>null</code> if canceled by the user
	 */
	private KeyReference select(final KeyReference[] keyReferences) {
		Arrays.sort(keyReferences);
		final int length= keyReferences.length;
		ILabelProvider labelProvider= new WorkbenchLabelProvider() {
			@Override
			public String decorateText(String input, Object element) {
				KeyReference keyRef= (KeyReference)element;
				IJavaElement javaElement= keyRef.element;
				IResource resource= keyRef.resource;

				String name= javaElement != null ? JavaElementLabels.getElementLabel(javaElement, JavaElementLabels.ALL_DEFAULT | JavaElementLabels.ALL_FULLY_QUALIFIED
						| JavaElementLabels.USE_RESOLVED | JavaElementLabels.P_COMPRESSED) : resource.getName();

				if (name == null)
					return input;

				int count= 0;
				for (int i= 0; i < length; i++) {
					if (javaElement != null) {
						if (keyReferences[i].element.equals(javaElement))
							count++;
					} else {
						if (keyReferences[i].resource.equals(resource))
							count++;
					}
				}
				if (count > 1) {
					Object[] args= new Object[] { BasicElementLabels.getResourceName(name), new Integer(count) };
					name= Messages.format(PropertiesFileEditorMessages.OpenAction_SelectionDialog_elementLabelWithMatchCount, args);
				}

				return name;
			}
			
			@Override
			protected ImageDescriptor decorateImage(ImageDescriptor input, Object element) {
				KeyReference keyRef= (KeyReference)element;
				IJavaElement javaElement= keyRef.element;
				return javaElement != null ? new JavaElementImageProvider().getJavaImageDescriptor(javaElement, JavaElementImageProvider.SMALL_ICONS) : super.decorateImage(input, element);
			}
		};

		TwoPaneElementSelector dialog= new TwoPaneElementSelector(fShell, labelProvider, new WorkbenchLabelProvider());
		dialog.setLowerListLabel(PropertiesFileEditorMessages.OpenAction_SelectionDialog_details);
		dialog.setLowerListComparator(new Comparator<Object>() {
			public int compare(Object o1, Object o2) {
				return 0; // don't sort
			}
		});
		dialog.setTitle(PropertiesFileEditorMessages.OpenAction_SelectionDialog_title);
		dialog.setMessage(PropertiesFileEditorMessages.OpenAction_SelectionDialog_message);
		dialog.setElements(keyReferences);

		if (dialog.open() == Window.OK) {
			Object[] result= dialog.getResult();
			if (result != null && result.length == 1)
			 return (KeyReference)result[0];
		}

		return null;
	}

	private void open(KeyReference keyReference) {
		if (keyReference == null)
			return;

		try {
			IEditorPart part= keyReference.element != null ? EditorUtility.openInEditor(keyReference.element, true) : EditorUtility.openInEditor(keyReference.resource, true);
			if (part != null)
				EditorUtility.revealInEditor(part, keyReference.offset, keyReference.length);
		} catch (PartInitException x) {

			String message= null;

			IWorkbenchAdapter wbAdapter= (IWorkbenchAdapter)((IAdaptable)keyReference).getAdapter(IWorkbenchAdapter.class);
			if (wbAdapter != null)
				message= Messages.format(PropertiesFileEditorMessages.OpenAction_error_messageArgs,
						new String[] { wbAdapter.getLabel(keyReference), x.getLocalizedMessage() } );

			if (message == null)
				message= Messages.format(PropertiesFileEditorMessages.OpenAction_error_message, x.getLocalizedMessage());

			MessageDialog.openError(fShell,
				PropertiesFileEditorMessages.OpenAction_error_messageProblems,
				message);
		}
	}

	private String getErrorDialogTitle() {
		return PropertiesFileEditorMessages.OpenAction_error_title;
	}

	private void showError(CoreException e) {
		ExceptionHandler.handle(e, fShell, getErrorDialogTitle(), PropertiesFileEditorMessages.OpenAction_error_message);
	}

	private void showErrorInStatusLine(final String message) {
		fShell.getDisplay().beep();
		final IEditorStatusLine statusLine= (IEditorStatusLine)fEditor.getAdapter(IEditorStatusLine.class);
		if (statusLine != null) {
			fShell.getDisplay().asyncExec(new Runnable() {
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
	 * Returns whether we search the key in double-quotes or not.
	 * <p>
	 * XXX: This is a hack to improve the accuracy of matches, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=81140
	 * </p>
	 *
	 * @return <code>true</code> if we search for double-quoted key
	 */
	private boolean useDoubleQuotedKey() {
		if (fStorage == null)
			return false;

		String name= fStorage.getName();

		return name != null && !"about.properties".equals(name) && !"feature.properties".equals(name) && !"plugin.properties".equals(name);  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	/**
	 * Searches references to the given key in the given scope.
	 *
	 * @param key the properties key
	 * @return the references or <code>null</code> if the search has been canceled by the user
	 */
	private KeyReference[] search(final String key) {
		if (key == null)
			return new KeyReference[0];

		final List<KeyReference> result= new ArrayList<KeyReference>(5);
		try {
			fEditor.getEditorSite().getWorkbenchWindow().getWorkbench().getProgressService().busyCursorWhile(
				new IRunnableWithProgress() {
						public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
							if (monitor == null)
								monitor= new NullProgressMonitor();

							monitor.beginTask("", 5); //$NON-NLS-1$
							try {
								// XXX: This is a hack to improve the accuracy of matches, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=81140
								boolean useDoubleQuotedKey= useDoubleQuotedKey();
								if (useDoubleQuotedKey) {
									SearchPattern pattern= SearchPattern.createPattern(key, IJavaSearchConstants.FIELD, IJavaSearchConstants.REFERENCES, SearchPattern.R_PATTERN_MATCH
											| SearchPattern.R_CASE_SENSITIVE);
									if (pattern == null)
										return;
									try {
										new SearchEngine().search(pattern, SearchUtils.getDefaultSearchParticipants(), SearchEngine.createWorkspaceScope(), new SearchRequestor() {
											@Override
											public void acceptSearchMatch(SearchMatch match) throws CoreException {
												IResource resource= match.getResource();
												if (resource != null)
													result.add(new KeyReference(resource, (IJavaElement) match.getElement(), match.getOffset(), match.getLength(), fIsFileEditorInput));
											}
										}, new SubProgressMonitor(monitor, 1));
									} catch (CoreException e) {
										throw new InvocationTargetException(e);
									}
								}
								if (result.size() == 0) {
									//maybe not an eclipse style NLS string
									String searchString;
									if (useDoubleQuotedKey) {
										StringBuffer buf= new StringBuffer("\""); //$NON-NLS-1$
										buf.append(key);
										buf.append('"');
										searchString= buf.toString();
									} else
										searchString= key;
									ResultCollector collector= new ResultCollector(result, useDoubleQuotedKey);
									TextSearchEngine engine= TextSearchEngine.create();
									Pattern searchPattern= PatternConstructor.createPattern(searchString, true, false);

									/* <p>
									 * XXX: This does not work for properties files coming from a JAR.
									 * For details see https://bugs.eclipse.org/bugs/show_bug.cgi?id=23341
									 * </p>
									*/
									if (fStorage instanceof IResource) {
										engine.search(createScope(((IResource)fStorage).getProject()), collector, searchPattern, new SubProgressMonitor(monitor, 4));
									}
								} else {
									monitor.worked(1);
								}
							} finally {
								monitor.done();
							}
						}
				}
			);
		} catch (InvocationTargetException ex) {
			String message= PropertiesFileEditorMessages.OpenAction_error_messageErrorSearchingKey;
			showError(new CoreException(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.OK, message, ex.getTargetException())));
		} catch (InterruptedException ex) {
			return null; // canceled
		}

		return result.toArray(new KeyReference[result.size()]);
	}

	private static TextSearchScope createScope(IResource scope) {
		ArrayList<String> fileNamePatternStrings= new ArrayList<String>();

		// XXX: Should be configurable via preference, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=81117
		String[] javaExtensions= JavaCore.getJavaLikeExtensions();
		for (int i= 0; i < javaExtensions.length; i++)
			fileNamePatternStrings.add("*." + javaExtensions[i]); //$NON-NLS-1$
		fileNamePatternStrings.add("*.xml"); //$NON-NLS-1$
		fileNamePatternStrings.add("*.ini"); //$NON-NLS-1$

		String[] allPatternStrings= fileNamePatternStrings.toArray(new String[fileNamePatternStrings.size()]);
		Pattern fileNamePattern= PatternConstructor.createPattern(allPatternStrings, false, false);

		return TextSearchScope.newSearchScope(new IResource[] { scope }, fileNamePattern, false);
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
		return Messages.format(PropertiesFileEditorMessages.OpenAction_hyperlinkText, fPropertiesKey);
	}
}

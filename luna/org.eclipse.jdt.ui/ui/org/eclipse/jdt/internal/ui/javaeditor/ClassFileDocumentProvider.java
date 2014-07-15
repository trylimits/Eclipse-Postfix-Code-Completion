/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.javaeditor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.resources.IResource;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ISynchronizable;
import org.eclipse.jface.text.source.IAnnotationModel;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;

import org.eclipse.ui.editors.text.FileDocumentProvider;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.ui.IResourceLocator;
import org.eclipse.jdt.internal.ui.JavaPlugin;


/**
 * A document provider for class files. Class files can be either inside
 */
public class ClassFileDocumentProvider extends FileDocumentProvider {

	/**
	 * An input change listener to request the editor to reread the input.
	 */
	public interface InputChangeListener {
		void inputChanged(IClassFileEditorInput input);
	}

	/**
	 * Synchronizes the document with external resource changes.
	 */
	protected class ClassFileSynchronizer implements IElementChangedListener {

		protected IClassFileEditorInput fInput;
		protected IPackageFragmentRoot fPackageFragmentRoot;

		/**
		 * Default constructor.
		 * 
		 * @param input the class file editor input
		 */
		public ClassFileSynchronizer(IClassFileEditorInput input) {

			fInput= input;

			IJavaElement parent= fInput.getClassFile().getParent();
			while (parent != null && !(parent instanceof IPackageFragmentRoot)) {
				parent= parent.getParent();
			}
			fPackageFragmentRoot= (IPackageFragmentRoot) parent;
		}

		/**
		 * Installs the synchronizer.
		 */
		public void install() {
			JavaCore.addElementChangedListener(this);
		}

		/**
		 * Uninstalls the synchronizer.
		 */
		public void uninstall() {
			JavaCore.removeElementChangedListener(this);
		}

		/*
		 * @see IElementChangedListener#elementChanged
		 */
		public void elementChanged(ElementChangedEvent e) {
			check(fPackageFragmentRoot, e.getDelta());
		}

		/**
		 * Recursively check whether the class file has been deleted.
		 * 
		 * @param input the package fragment root
		 * @param delta the Java element delta
		 * @return <code>true</code> if delta processing can be stopped
		 */
		protected boolean check(IPackageFragmentRoot input, IJavaElementDelta delta) {
			IJavaElement element= delta.getElement();

			if ((delta.getKind() & IJavaElementDelta.REMOVED) != 0 || (delta.getFlags() & IJavaElementDelta.F_CLOSED) != 0) {
				// http://dev.eclipse.org/bugs/show_bug.cgi?id=19023
				if (element.equals(input.getJavaProject()) || element.equals(input)) {
					handleDeleted(fInput);
					return true;
				}
			}

			if (((delta.getFlags() & IJavaElementDelta.F_ARCHIVE_CONTENT_CHANGED) != 0) && input.equals(element)) {
				handleDeleted(fInput);
				return true;
			}

			if (((delta.getFlags() & IJavaElementDelta.F_REMOVED_FROM_CLASSPATH) != 0) && input.equals(element)) {
				handleDeleted(fInput);
				return true;
			}

			IJavaElementDelta[] subdeltas= delta.getAffectedChildren();
			for (int i= 0; i < subdeltas.length; i++) {
				if (check(input, subdeltas[i]))
					return true;
			}

			if ((delta.getFlags() & IJavaElementDelta.F_SOURCEDETACHED) != 0 ||
				(delta.getFlags() & IJavaElementDelta.F_SOURCEATTACHED) != 0)
			{
				IClassFile file= fInput != null ? fInput.getClassFile() : null;
				IJavaProject project= input != null ? input.getJavaProject() : null;

				boolean isOnClasspath= false;
				if (file != null && project != null)
					isOnClasspath= project.isOnClasspath(file);

				if (isOnClasspath) {
					fireInputChanged(fInput);
					return false;
				} else {
					handleDeleted(fInput);
					return true;
				}
			}

			return false;
		}
	}

	/**
	 * Correcting the visibility of <code>FileSynchronizer</code>.
	 */
	protected class _FileSynchronizer extends FileSynchronizer {
		public _FileSynchronizer(IFileEditorInput fileEditorInput) {
			super(fileEditorInput);
		}
	}

	/**
	 * Bundle of all required informations.
	 */
	protected class ClassFileInfo extends FileInfo {

		ClassFileSynchronizer fClassFileSynchronizer= null;

		ClassFileInfo(IDocument document, IAnnotationModel model, _FileSynchronizer fileSynchronizer) {
			super(document, model, fileSynchronizer);
		}

		ClassFileInfo(IDocument document, IAnnotationModel model, ClassFileSynchronizer classFileSynchronizer) {
			super(document, model, null);
			fClassFileSynchronizer= classFileSynchronizer;
		}
	}

	/** Input change listeners. */
	private List<InputChangeListener> fInputListeners= new ArrayList<InputChangeListener>();

	/**
	 * Creates a new document provider.
	 */
	public ClassFileDocumentProvider() {
		super();
	}

	/*
	 * @see StorageDocumentProvider#setDocumentContent(IDocument, IEditorInput)
	 */
	@Override
	protected boolean setDocumentContent(IDocument document, IEditorInput editorInput, String encoding) throws CoreException {
		if (editorInput instanceof IClassFileEditorInput) {
			IClassFile classFile= ((IClassFileEditorInput) editorInput).getClassFile();
			String source= classFile.getSource();
			if (source == null)
				source= ""; //$NON-NLS-1$
			document.set(source);
			return true;
		}
		return super.setDocumentContent(document, editorInput, encoding);
	}

	/**
	 * Creates an annotation model derived from the given class file editor input.
	 *
	 * @param classFileEditorInput the editor input from which to query the annotations
	 * @return the created annotation model
	 * @exception CoreException if the editor input could not be accessed
	 */
	protected IAnnotationModel createClassFileAnnotationModel(IClassFileEditorInput classFileEditorInput) throws CoreException {
		IResource resource= null;
		IClassFile classFile= classFileEditorInput.getClassFile();

		IResourceLocator locator= (IResourceLocator) classFile.getAdapter(IResourceLocator.class);
		if (locator != null)
			resource= locator.getContainingResource(classFile);

		if (resource != null) {
			ClassFileMarkerAnnotationModel model= new ClassFileMarkerAnnotationModel(resource);
			model.setClassFile(classFile);
			return model;
		}

		return null;
	}

	/*
	 * @see org.eclipse.ui.editors.text.StorageDocumentProvider#createEmptyDocument()
	 * @since 3.1
	 */
	@Override
	protected IDocument createEmptyDocument() {
		IDocument document= FileBuffers.getTextFileBufferManager().createEmptyDocument(null, LocationKind.IFILE);
		if (document instanceof ISynchronizable)
			((ISynchronizable)document).setLockObject(new Object());
		return document;
	}

	/*
	 * @see AbstractDocumentProvider#createDocument(Object)
	 */
	@Override
	protected IDocument createDocument(Object element) throws CoreException {
		IDocument document= super.createDocument(element);
		if (document != null) {
			JavaTextTools tools= JavaPlugin.getDefault().getJavaTextTools();
			tools.setupJavaDocumentPartitioner(document, IJavaPartitions.JAVA_PARTITIONING);
		}
		return document;
	}

	/*
	 * @see AbstractDocumentProvider#createElementInfo(Object)
	 */
	@Override
	protected ElementInfo createElementInfo(Object element) throws CoreException {

		if (element instanceof IClassFileEditorInput) {

			IClassFileEditorInput input = (IClassFileEditorInput) element;
			ExternalClassFileEditorInput external= null;
			if (input instanceof ExternalClassFileEditorInput)
				external= (ExternalClassFileEditorInput) input;

			if (external != null) {
				try {
					refreshFile(external.getFile());
				} catch (CoreException x) {
					handleCoreException(x, JavaEditorMessages.ClassFileDocumentProvider_error_createElementInfo);
				}
			}

			IDocument d= createDocument(input);
			IAnnotationModel m= createClassFileAnnotationModel(input);

			if (external != null) {
				_FileSynchronizer f= new _FileSynchronizer(external);
				f.install();
				ClassFileInfo info= new ClassFileInfo(d, m, f);
				info.fModificationStamp= computeModificationStamp(external.getFile());
				info.fEncoding= getPersistedEncoding(element);
				return info;
			} else if (input instanceof InternalClassFileEditorInput) {
				ClassFileSynchronizer s= new ClassFileSynchronizer(input);
				s.install();
				ClassFileInfo info= new ClassFileInfo(d, m, s);
				info.fEncoding= getPersistedEncoding(element);
				return info;
			}
		}

		return null;
	}

	/*
	 * @see FileDocumentProvider#disposeElementInfo(Object, ElementInfo)
	 */
	@Override
	protected void disposeElementInfo(Object element, ElementInfo info) {
		ClassFileInfo classFileInfo= (ClassFileInfo) info;
		if (classFileInfo.fClassFileSynchronizer != null) {
			classFileInfo.fClassFileSynchronizer.uninstall();
			classFileInfo.fClassFileSynchronizer= null;
		}

		super.disposeElementInfo(element, info);
	}

	/*
	 * @see org.eclipse.ui.texteditor.IDocumentProviderExtension3#isSynchronized(java.lang.Object)
	 * @since 3.0
	 */
	@Override
	public boolean isSynchronized(Object element) {
		Object elementInfo= getElementInfo(element);
		if (elementInfo instanceof ClassFileInfo) {
			IClassFileEditorInput input= (IClassFileEditorInput)element;
			IResource resource;
			try {
				resource= input.getClassFile().getUnderlyingResource();
			} catch (JavaModelException e) {
				return true;
			}
			return resource == null || resource.isSynchronized(IResource.DEPTH_ZERO);
		}
		return false;
	}

	/**
	 * Handles the deletion of the element underlying the given class file editor input.
	 * @param input the editor input
	 */
	protected void handleDeleted(IClassFileEditorInput input) {
		if (input == null) {
			fireElementDeleted(input);
			return;
		}

		if (input.exists())
			return;

		IClassFile cf= input.getClassFile();
		try {
			/*
			 * Let's try to find the class file - maybe the JAR changed
			 */
			IType type= cf.getType();
			IJavaProject project= cf.getJavaProject();
			if (project != null) {
				type= project.findType(type.getFullyQualifiedName());
				if (type != null) {
					IEditorInput editorInput= EditorUtility.getEditorInput(type.getParent());
					if (editorInput instanceof IClassFileEditorInput) {
						fireInputChanged((IClassFileEditorInput)editorInput);
						return;
					}
				}
			}
		} catch (JavaModelException x) {
			// Don't log and fall through: element deleted
		}

		fireElementDeleted(input);

	}

	/*
	 * @see org.eclipse.ui.editors.text.FileDocumentProvider#handleElementContentChanged(org.eclipse.ui.IFileEditorInput)
	 * @since 3.9
	 */
	@Override
	protected void handleElementContentChanged(IFileEditorInput fileEditorInput) {
		super.handleElementContentChanged(fileEditorInput);
		if (fileEditorInput instanceof ExternalClassFileEditorInput) {
			fireInputChanged((IClassFileEditorInput) fileEditorInput);
		}
	}

	/**
	 * Fires input changes to input change listeners.
	 * 
	 * @param input the class file editor input
	 */
	protected void fireInputChanged(IClassFileEditorInput input) {
		List<InputChangeListener> list= new ArrayList<InputChangeListener>(fInputListeners);
		for (Iterator<InputChangeListener> i = list.iterator(); i.hasNext();)
			i.next().inputChanged(input);
	}

	/**
	 * Adds an input change listener.
	 * 
	 * @param listener the input change listener
	 */
	public void addInputChangeListener(InputChangeListener listener) {
		fInputListeners.add(listener);
	}

	/**
	 * Removes an input change listener.
	 * 
	 * @param listener the input change listener
	 */
	public void removeInputChangeListener(InputChangeListener listener) {
		fInputListeners.remove(listener);
	}

}

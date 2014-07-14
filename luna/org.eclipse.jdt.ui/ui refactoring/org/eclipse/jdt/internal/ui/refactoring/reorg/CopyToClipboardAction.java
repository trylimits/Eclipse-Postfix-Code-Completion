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
package org.eclipse.jdt.internal.ui.refactoring.reorg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.osgi.util.TextProcessor;

import org.eclipse.swt.SWTError;
import org.eclipse.swt.dnd.ByteArrayTransfer;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ResourceTransfer;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.internal.corext.refactoring.TypedSource;
import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaElementTransfer;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ParentChecker;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgUtils;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;

import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;


public class CopyToClipboardAction extends SelectionDispatchAction{

	private final Clipboard fClipboard;
	private boolean fAutoRepeatOnFailure= false;

	public CopyToClipboardAction(IWorkbenchSite site) {
		this(site, null);
	}

	public CopyToClipboardAction(IWorkbenchSite site, Clipboard clipboard) {
		super(site);
		setText(ReorgMessages.CopyToClipboardAction_text);
		setDescription(ReorgMessages.CopyToClipboardAction_description);
		fClipboard= clipboard;
		ISharedImages workbenchImages= getWorkbenchSharedImages();
		setDisabledImageDescriptor(workbenchImages.getImageDescriptor(ISharedImages.IMG_TOOL_COPY_DISABLED));
		setImageDescriptor(workbenchImages.getImageDescriptor(ISharedImages.IMG_TOOL_COPY));
		setHoverImageDescriptor(workbenchImages.getImageDescriptor(ISharedImages.IMG_TOOL_COPY));
		update(getSelection());

		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.COPY_ACTION);
	}

	public void setAutoRepeatOnFailure(boolean autorepeatOnFailure){
		fAutoRepeatOnFailure= autorepeatOnFailure;
	}

	private static ISharedImages getWorkbenchSharedImages() {
		return JavaPlugin.getDefault().getWorkbench().getSharedImages();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#selectionChanged(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		List<?> elements= selection.toList();
		IResource[] resources= ReorgUtils.getResources(elements);
		IJavaElement[] javaElements= ReorgUtils.getJavaElements(elements);
		IJarEntryResource[] jarEntryResources= ReorgUtils.getJarEntryResources(elements);
		if (elements.size() != resources.length + javaElements.length + jarEntryResources.length)
			setEnabled(false);
		else
			setEnabled(canEnable(resources, javaElements, jarEntryResources));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#run(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	@Override
	public void run(IStructuredSelection selection) {
		try {
			List<?> elements= selection.toList();
			IResource[] resources= ReorgUtils.getResources(elements);
			IJavaElement[] javaElements= ReorgUtils.getJavaElements(elements);
			IJarEntryResource[] jarEntryResources= ReorgUtils.getJarEntryResources(elements);
			if (elements.size() == resources.length + javaElements.length + jarEntryResources.length && canEnable(resources, javaElements, jarEntryResources))
				doRun(resources, javaElements, jarEntryResources);
		} catch (CoreException e) {
			ExceptionHandler.handle(e, getShell(), ReorgMessages.CopyToClipboardAction_2, ReorgMessages.CopyToClipboardAction_3);
		}
	}

	private void doRun(IResource[] resources, IJavaElement[] javaElements, IJarEntryResource[] jarEntryResources) throws CoreException {
		ClipboardCopier copier= new ClipboardCopier(resources, javaElements, jarEntryResources, getShell(), fAutoRepeatOnFailure);

		if (fClipboard != null) {
			copier.copyToClipboard(fClipboard);
		} else {
			Clipboard clipboard= new Clipboard(getShell().getDisplay());
			try {
				copier.copyToClipboard(clipboard);
			} finally {
				clipboard.dispose();
			}
		}
	}

	private boolean canEnable(IResource[] resources, IJavaElement[] javaElements, IJarEntryResource[] jarEntryResources) {
		return new CopyToClipboardEnablementPolicy(resources, javaElements, jarEntryResources).canEnable();
	}

	//----------------------------------------------------------------------------------------//

	private static class ClipboardCopier{
		private final boolean fAutoRepeatOnFailure;
		private final IResource[] fResources;
		private final IJavaElement[] fJavaElements;
		private final IJarEntryResource[] fJarEntryResources;
		private final Shell fShell;
		private final ILabelProvider fLabelProvider;

		private ClipboardCopier(IResource[] resources, IJavaElement[] javaElements, IJarEntryResource[] jarEntryResources, Shell shell, boolean autoRepeatOnFailure) {
			Assert.isNotNull(resources);
			Assert.isNotNull(javaElements);
			Assert.isNotNull(jarEntryResources);
			Assert.isNotNull(shell);
			fResources= resources;
			fJavaElements= javaElements;
			fJarEntryResources= jarEntryResources;
			fShell= shell;
			fLabelProvider= createLabelProvider();
			fAutoRepeatOnFailure= autoRepeatOnFailure;
		}

		public void copyToClipboard(Clipboard clipboard) throws CoreException {
			StringBuffer namesBuf= new StringBuffer();
			int countOfNonJarResources= fResources.length + fJavaElements.length;

			processJarEntryResources(namesBuf);
			if (countOfNonJarResources == 0) {
				copyToClipboard(fResources, new String[0], namesBuf.toString(), fJavaElements, new TypedSource[0], 0, clipboard);
			} else {
				//Set<String> fileNames
				Set<String> fileNames= new HashSet<String>(countOfNonJarResources);
				processResources(fileNames, namesBuf);
				processJavaElements(fileNames, namesBuf);

				IType[] mainTypes= ReorgUtils.getMainTypes(fJavaElements);
				ICompilationUnit[] cusOfMainTypes= ReorgUtils.getCompilationUnits(mainTypes);
				IResource[] resourcesOfMainTypes= ReorgUtils.getResources(cusOfMainTypes);
				addFileNames(fileNames, resourcesOfMainTypes);

				IResource[] cuResources= ReorgUtils.getResources(getCompilationUnits(fJavaElements));
				addFileNames(fileNames, cuResources);

				IResource[] resourcesForClipboard= ReorgUtils.union(fResources, ReorgUtils.union(cuResources, resourcesOfMainTypes));
				IJavaElement[] javaElementsForClipboard= ReorgUtils.union(fJavaElements, cusOfMainTypes);

				TypedSource[] typedSources= TypedSource.createTypedSources(javaElementsForClipboard);
				String[] fileNameArray= fileNames.toArray(new String[fileNames.size()]);
				copyToClipboard(resourcesForClipboard, fileNameArray, namesBuf.toString(), javaElementsForClipboard, typedSources, 0, clipboard);
			}
		}

		private static IJavaElement[] getCompilationUnits(IJavaElement[] javaElements) {
			List<?> cus= ReorgUtils.getElementsOfType(javaElements, IJavaElement.COMPILATION_UNIT);
			return cus.toArray(new ICompilationUnit[cus.size()]);
		}

		private void processResources(Set<String> fileNames, StringBuffer namesBuf) {
			for (int i= 0; i < fResources.length; i++) {
				IResource resource= fResources[i];
				addFileName(fileNames, resource);

				if (namesBuf.length() > 0 || i > 0)
					namesBuf.append(System.getProperty("line.separator")); //$NON-NLS-1$
				namesBuf.append(getName(resource));
			}
		}

		private void processJavaElements(Set<String> fileNames, StringBuffer namesBuf) {
			for (int i= 0; i < fJavaElements.length; i++) {
				IJavaElement element= fJavaElements[i];
				switch (element.getElementType()) {
					case IJavaElement.JAVA_PROJECT :
					case IJavaElement.PACKAGE_FRAGMENT_ROOT :
					case IJavaElement.PACKAGE_FRAGMENT :
					case IJavaElement.COMPILATION_UNIT :
					case IJavaElement.CLASS_FILE :
						addFileName(fileNames, ReorgUtils.getResource(element));
						break;
					default :
						break;
				}

				if (namesBuf.length() > 0 || i > 0)
					namesBuf.append(System.getProperty("line.separator")); //$NON-NLS-1$
				namesBuf.append(getName(element));
			}
		}

		/**
		 * Gets the names of the jar entry resources and adds them to the string buffer.
		 * 
		 * @param namesBuf the names buffer
		 * @since 3.6
		 */
		private void processJarEntryResources(StringBuffer namesBuf) {
			for (int i= 0; i < fJarEntryResources.length; i++) {
				if (namesBuf.length() > 0 || i > 0)
					namesBuf.append(System.getProperty("line.separator")); //$NON-NLS-1$
				namesBuf.append(getName(fJarEntryResources[i]));
			}
		}

		private static void addFileNames(Set<String> fileName, IResource[] resources) {
			for (int i= 0; i < resources.length; i++) {
				addFileName(fileName, resources[i]);
			}
		}

		private static void addFileName(Set<String> fileName, IResource resource){
			if (resource == null)
				return;
			IPath location = resource.getLocation();
			if (location != null) {
				fileName.add(location.toOSString());
			} else {
				// not a file system path. skip file.
			}
		}

		private void copyToClipboard(IResource[] resources, String[] fileNames, String names, IJavaElement[] javaElements, TypedSource[] typedSources, int repeat, Clipboard clipboard) {
			final int repeat_max_count= 10;
			try{
				clipboard.setContents(createDataArray(resources, javaElements, fileNames, names, typedSources),
										createDataTypeArray(resources, javaElements, fileNames, typedSources));
			} catch (SWTError e) {
				if (e.code != DND.ERROR_CANNOT_SET_CLIPBOARD || repeat >= repeat_max_count)
					throw e;
				if (fAutoRepeatOnFailure) {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e1) {
						// do nothing.
					}
				}
				if (fAutoRepeatOnFailure || MessageDialog.openQuestion(fShell, ReorgMessages.CopyToClipboardAction_4, ReorgMessages.CopyToClipboardAction_5))
					copyToClipboard(resources, fileNames, names, javaElements, typedSources, repeat + 1, clipboard);
			}
		}

		private static Transfer[] createDataTypeArray(IResource[] resources, IJavaElement[] javaElements, String[] fileNames, TypedSource[] typedSources) {
			List<ByteArrayTransfer> result= new ArrayList<ByteArrayTransfer>(4);
			if (resources.length != 0)
				result.add(ResourceTransfer.getInstance());
			if (javaElements.length != 0)
				result.add(JavaElementTransfer.getInstance());
			if (fileNames.length != 0)
				result.add(FileTransfer.getInstance());
			if (typedSources.length != 0)
				result.add(TypedSourceTransfer.getInstance());
			result.add(TextTransfer.getInstance());
			return result.toArray(new Transfer[result.size()]);
		}

		private static Object[] createDataArray(IResource[] resources, IJavaElement[] javaElements, String[] fileNames, String names, TypedSource[] typedSources) {
			List<Object> result= new ArrayList<Object>(4);
			if (resources.length != 0)
				result.add(resources);
			if (javaElements.length != 0)
				result.add(javaElements);
			if (fileNames.length != 0)
				result.add(fileNames);
			if (typedSources.length != 0)
				result.add(typedSources);
			result.add(names);
			return result.toArray();
		}

		private static ILabelProvider createLabelProvider(){
			return new JavaElementLabelProvider(
				JavaElementLabelProvider.SHOW_VARIABLE
				+ JavaElementLabelProvider.SHOW_PARAMETERS
				+ JavaElementLabelProvider.SHOW_TYPE
			);
		}
		private String getName(IResource resource){
			return TextProcessor.deprocess(fLabelProvider.getText(resource));
		}
		private String getName(IJavaElement javaElement){
			return TextProcessor.deprocess(fLabelProvider.getText(javaElement));
		}
		/**
		 * Gets the name of the jar entry resource.
		 * @param resource the jar entry resource
		 * @return the name of the jar entry resource
		 * @since 3.6
		 */
		private String getName(IJarEntryResource resource) {
			return TextProcessor.deprocess(fLabelProvider.getText(resource));
		}
	}

	private static class CopyToClipboardEnablementPolicy {
		private final IResource[] fResources;
		private final IJavaElement[] fJavaElements;
		private final IJarEntryResource[] fJarEntryResources;

		public CopyToClipboardEnablementPolicy(IResource[] resources, IJavaElement[] javaElements, IJarEntryResource[] jarEntryResources) {
			Assert.isNotNull(resources);
			Assert.isNotNull(javaElements);
			Assert.isNotNull(jarEntryResources);
			fResources= resources;
			fJavaElements= javaElements;
			fJarEntryResources= jarEntryResources;
		}

		public boolean canEnable() {
			if (fResources.length + fJavaElements.length + fJarEntryResources.length == 0)
				return false;
			if (hasProjects() && hasNonProjects())
				return false;
			if (! canCopyAllToClipboard())
				return false;
			if (! new ParentChecker(fResources, fJavaElements, fJarEntryResources).haveCommonParent())
				return false;
			return true;
		}

		private boolean canCopyAllToClipboard() {
			for (int i= 0; i < fResources.length; i++) {
				if (! canCopyToClipboard(fResources[i])) return false;
			}
			for (int i= 0; i < fJavaElements.length; i++) {
				if (! canCopyToClipboard(fJavaElements[i])) return false;
			}
			for (int i= 0; i < fJarEntryResources.length; i++) {
				if (fJarEntryResources[i] == null) return false;
			}
			return true;
		}

		private static boolean canCopyToClipboard(IJavaElement element) {
			if (element == null || ! element.exists())
				return false;

			if (JavaElementUtil.isDefaultPackage(element))
				return false;

			return true;
		}

		private static boolean canCopyToClipboard(IResource resource) {
			return 	resource != null &&
					resource.exists() &&
					! resource.isPhantom() &&
					resource.getType() != IResource.ROOT;
		}

		private boolean hasProjects() {
			for (int i= 0; i < fResources.length; i++) {
				if (ReorgUtils.isProject(fResources[i])) return true;
			}
			for (int i= 0; i < fJavaElements.length; i++) {
				if (ReorgUtils.isProject(fJavaElements[i])) return true;
			}
			return false;
		}

		private boolean hasNonProjects() {
			for (int i= 0; i < fResources.length; i++) {
				if (! ReorgUtils.isProject(fResources[i])) return true;
			}
			for (int i= 0; i < fJavaElements.length; i++) {
				if (! ReorgUtils.isProject(fJavaElements[i])) return true;
			}
			return false;
		}
	}
}

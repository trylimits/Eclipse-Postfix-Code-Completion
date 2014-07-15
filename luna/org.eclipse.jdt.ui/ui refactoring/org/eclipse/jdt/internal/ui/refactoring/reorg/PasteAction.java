/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring.reorg;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IEncodedStorage;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.resources.IWorkspaceRunnable;

import org.eclipse.core.filebuffers.ITextFileBuffer;

import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentRewriteSession;
import org.eclipse.jface.text.DocumentRewriteSessionType;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.CopyFilesAndFoldersOperation;
import org.eclipse.ui.actions.CopyProjectOperation;
import org.eclipse.ui.part.ResourceTransfer;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.patch.ApplyPatchOperation;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.TypedSource;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChangeCompatibility;
import org.eclipse.jdt.internal.corext.refactoring.reorg.IConfirmQuery;
import org.eclipse.jdt.internal.corext.refactoring.reorg.IReorgQueries;
import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaElementTransfer;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ParentChecker;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgDestinationFactory;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgUtils;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringFileBuffers;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.Strings;

import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstall2;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jdt.launching.environments.IExecutionEnvironmentsManager;

import org.eclipse.jdt.ui.CodeGeneration;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;
import org.eclipse.jdt.ui.refactoring.RefactoringSaveHelper;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.IJavaStatusConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaUIMessages;
import org.eclipse.jdt.internal.ui.javaeditor.ASTProvider;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringExecutionHelper;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.SelectionUtil;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathsBlock;
import org.eclipse.jdt.internal.ui.workingsets.IWorkingSetIDs;


public class PasteAction extends SelectionDispatchAction{

	public static final String ID= "org.eclipse.jdt.internal.ui.refactoring.reorg.PasteAction.id"; //$NON-NLS-1$

	private final Clipboard fClipboard;

	public PasteAction(IWorkbenchSite site) {
		this(site, null);
	}

	public PasteAction(IWorkbenchSite site, Clipboard clipboard) {
		super(site);
		fClipboard= clipboard;

		setId(ID);
		setText(ReorgMessages.PasteAction_4);
		setDescription(ReorgMessages.PasteAction_5);

		ISharedImages workbenchImages= JavaPlugin.getDefault().getWorkbench().getSharedImages();
		setDisabledImageDescriptor(workbenchImages.getImageDescriptor(ISharedImages.IMG_TOOL_PASTE_DISABLED));
		setImageDescriptor(workbenchImages.getImageDescriptor(ISharedImages.IMG_TOOL_PASTE));
		setHoverImageDescriptor(workbenchImages.getImageDescriptor(ISharedImages.IMG_TOOL_PASTE));

		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.PASTE_ACTION);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.actions.SelectionDispatchAction#selectionChanged(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		// Moved condition checking to run (see http://bugs.eclipse.org/bugs/show_bug.cgi?id=78450)
	}

	private Paster[] createEnabledPasters(TransferData[] availableDataTypes, Clipboard clipboard) {
		Paster paster;
		Shell shell = getShell();
		List<Paster> result= new ArrayList<Paster>(2);
		paster= new ProjectPaster(shell, clipboard);
		if (paster.canEnable(availableDataTypes))
			result.add(paster);

		paster= new JavaElementAndResourcePaster(shell, clipboard);
		if (paster.canEnable(availableDataTypes))
			result.add(paster);

		paster= new TypedSourcePaster(shell, clipboard);
		if (paster.canEnable(availableDataTypes))
			result.add(paster);

		paster= new FilePaster(shell, clipboard);
		if (paster.canEnable(availableDataTypes))
			result.add(paster);

		paster= new WorkingSetPaster(shell, clipboard);
		if (paster.canEnable(availableDataTypes))
			result.add(paster);

		paster= new TextPaster(shell, clipboard);
		if (paster.canEnable(availableDataTypes))
			result.add(paster);
		return result.toArray(new Paster[result.size()]);
	}

	private static Object getContents(final Clipboard clipboard, final Transfer transfer, Shell shell) {
		//see bug 33028 for explanation why we need this
		final Object[] result= new Object[1];
		shell.getDisplay().syncExec(new Runnable() {
			public void run() {
				result[0]= clipboard.getContents(transfer);
			}
		});
		return result[0];
	}

	private static boolean isAvailable(Transfer transfer, TransferData[] availableDataTypes) {
		for (int i= 0; i < availableDataTypes.length; i++) {
			if (transfer.isSupportedType(availableDataTypes[i])) return true;
		}
		return false;
	}

	@Override
	public void run(IStructuredSelection selection) {
		Clipboard clipboard;
		if (fClipboard != null)
			clipboard= fClipboard;
		else
			clipboard= new Clipboard(getShell().getDisplay());
		try {
			TransferData[] availableTypes= clipboard.getAvailableTypes();
			List<?> elements= selection.toList();
			IResource[] resources= ReorgUtils.getResources(elements);
			IJavaElement[] javaElements= ReorgUtils.getJavaElements(elements);
			IWorkingSet[] workingSets= ReorgUtils.getWorkingSets(elements);
			Paster[] pasters= createEnabledPasters(availableTypes, clipboard);
			for (int i= 0; i < pasters.length; i++) {
				try {
					if (pasters[i].canPasteOn(javaElements, resources, workingSets, elements)) {
						pasters[i].paste(javaElements, resources, workingSets, availableTypes);
						return;// one is enough
					}
				} catch (JavaModelException e) {
					ExceptionHandler.handle(e, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
				} catch (InvocationTargetException e) {
					ExceptionHandler.handle(e, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
				} catch (InterruptedException e) {
					// user canceled the paste operation
					return;
				}
			}
			String msg= resources.length + javaElements.length + workingSets.length == 0
			? ReorgMessages.PasteAction_cannot_no_selection
					: ReorgMessages.PasteAction_cannot_selection;
			MessageDialog.openError(JavaPlugin.getActiveWorkbenchShell(), ReorgMessages.PasteAction_name, msg);
		} finally {
			if (fClipboard == null)
				clipboard.dispose();
		}
	}

	private abstract static class Paster{
		private final Shell fShell;
		private final Clipboard fClipboard2;
		protected Paster(Shell shell, Clipboard clipboard){
			fShell= shell;
			fClipboard2= clipboard;
		}
		protected final Shell getShell() {
			return fShell;
		}
		protected final Clipboard getClipboard() {
			return fClipboard2;
		}

		protected final IResource[] getClipboardResources(TransferData[] availableDataTypes) {
			Transfer transfer= ResourceTransfer.getInstance();
			if (isAvailable(transfer, availableDataTypes)) {
				return (IResource[])getContents(fClipboard2, transfer, getShell());
			}
			return null;
		}

		protected final IJavaElement[] getClipboardJavaElements(TransferData[] availableDataTypes) {
			Transfer transfer= JavaElementTransfer.getInstance();
			if (isAvailable(transfer, availableDataTypes)) {
				return (IJavaElement[])getContents(fClipboard2, transfer, getShell());
			}
			return null;
		}

		protected final TypedSource[] getClipboardTypedSources(TransferData[] availableDataTypes) {
			Transfer transfer= TypedSourceTransfer.getInstance();
			if (isAvailable(transfer, availableDataTypes)) {
				return (TypedSource[])getContents(fClipboard2, transfer, getShell());
			}
			return null;
		}

		protected final String getClipboardText(TransferData[] availableDataTypes) {
			Transfer transfer= TextTransfer.getInstance();
			if (isAvailable(transfer, availableDataTypes)) {
				return (String) getContents(fClipboard2, transfer, getShell());
			}
			return null;
		}

		/**
		 * Used to be called on selection change, but is only called on execution now (before
		 * {@link #canPasteOn(IJavaElement[], IResource[], IWorkingSet[], List)}).
		 *
		 * @param availableTypes transfer types
		 * @return whether the paste action can be enabled
		 */
		public abstract boolean canEnable(TransferData[] availableTypes);

		/*
		 * Only called if {@link #canEnable(TransferData[])} returns <code>true</code>.
		 */
		public abstract boolean canPasteOn(IJavaElement[] selectedJavaElements, IResource[] selectedResources, IWorkingSet[] selectedWorkingSets, List<?> selectedElements)  throws JavaModelException;

		/*
		 * only called if {@link #canPasteOn(IJavaElement[], IResource[], IWorkingSet[])} returns <code>true</code>
		 */
		public abstract void paste(IJavaElement[] selectedJavaElements, IResource[] selectedResources, IWorkingSet[] selectedWorkingSets, TransferData[] availableTypes) throws JavaModelException, InterruptedException, InvocationTargetException;
	}

    private static class TextPaster extends Paster {

		private static class ParsedCu {
			private final String fText;
			private final int fKind;
			private final String fTypeName;
			private final String fPackageName;

			public static List<ParsedCu> parseCus(IJavaProject javaProject, String compilerCompliance, String text) {
				ASTParser parser= ASTParser.newParser(ASTProvider.SHARED_AST_LEVEL);
				if (javaProject != null) {
					parser.setProject(javaProject);
				} else if (compilerCompliance != null) {
					Map<String, String> options= JavaCore.getOptions();
					JavaModelUtil.setComplianceOptions(options, compilerCompliance);
					parser.setCompilerOptions(options);
				}
				parser.setSource(text.toCharArray());
				parser.setStatementsRecovery(true);
				CompilationUnit unit= (CompilationUnit) parser.createAST(null);

				if (unit.types().size() > 0)
					return parseAsTypes(text, unit);

				parser.setProject(javaProject);
				parser.setSource(text.toCharArray());
				parser.setStatementsRecovery(true);
				parser.setKind(ASTParser.K_CLASS_BODY_DECLARATIONS);
				ASTNode root= parser.createAST(null);
				if (root instanceof TypeDeclaration) {
					List<BodyDeclaration> bodyDeclarations= ((TypeDeclaration) root).bodyDeclarations();
					if (bodyDeclarations.size() > 0)
						return Collections.singletonList(new ParsedCu(text, ASTParser.K_CLASS_BODY_DECLARATIONS, null, null));
				}

				parser.setProject(javaProject);
				parser.setSource(text.toCharArray());
				parser.setStatementsRecovery(true);
				parser.setKind(ASTParser.K_STATEMENTS);
				root= parser.createAST(null);
				if (root instanceof Block) {
					List<Statement> statements= ((Block) root).statements();
					if (statements.size() > 0)
						return Collections.singletonList(new ParsedCu(text, ASTParser.K_STATEMENTS, null, null));
				}

				return Collections.emptyList();
			}

			private static List<ParsedCu> parseAsTypes(String text, CompilationUnit unit) {
				String packageName= IPackageFragment.DEFAULT_PACKAGE_NAME;
				PackageDeclaration pack= unit.getPackage();
				if (pack != null) {
					packageName= pack.getName().getFullyQualifiedName();
				}

				ArrayList<ParsedCu> cus= new ArrayList<ParsedCu>();
				List<AbstractTypeDeclaration> types= unit.types();

				int startOffset= 0;
				String typeName= null;
				int maxVisibility= JdtFlags.VISIBILITY_CODE_INVALID;

				// Public types must be in separate CUs:
				for (Iterator<AbstractTypeDeclaration> iter= types.iterator(); iter.hasNext(); ) {
					AbstractTypeDeclaration type= iter.next();
					if (typeName == null) {
						// first in group:
						maxVisibility= JdtFlags.getVisibilityCode(type);
						typeName= type.getName().getIdentifier();
					} else {
						int visibility= JdtFlags.getVisibilityCode(type);
						if (visibility == Modifier.PUBLIC && maxVisibility == Modifier.PUBLIC) {
							// public and there was a public type before => create CU for previous:
							int prevEnd= type.getStartPosition();
							String source= text.substring(startOffset, prevEnd);
							cus.add(new ParsedCu(source, ASTParser.K_COMPILATION_UNIT, typeName, packageName));
							// ... and restart:
							startOffset= prevEnd;
							typeName= type.getName().getIdentifier();
							maxVisibility= visibility;
						} else {
							if (JdtFlags.isHigherVisibility(visibility, maxVisibility)) {
								maxVisibility= visibility;
								typeName= type.getName().getIdentifier();
							}
						}
					}
				}
				if (typeName != null) {
					// create CU for the rest:
					String source= text.substring(startOffset);
					cus.add(new ParsedCu(source, ASTParser.K_COMPILATION_UNIT, typeName, packageName));
				}
				return cus;
			}

			private ParsedCu(String text, int kind, String typeName, String packageName) {
				fText= text;
				fTypeName= typeName;
				fPackageName= packageName;
				fKind= kind;
			}

			public String getTypeName() {
				return fTypeName;
			}

			public String getPackageName() {
				return fPackageName;
			}

			public String getText() {
				return fText;
			}

			public int getKind() {
				return fKind;
			}
		}
		
		private IStorage fPatchStorage;

		private IPackageFragmentRoot fDestination;
		/**
		 * destination pack iff pasted 1 CU to package fragment or compilation unit, <code>null</code> otherwise
		 */
		private IPackageFragment fDestinationPack;
		private IType fDestinationType;
		private IMethod fDestinationMethod;
		private int fPackageDeclCount;
		private ParsedCu[] fParsedCus;
		private TransferData[] fAvailableTypes;
		private IPath fVMPath;
		private String fCompilerCompliance;

		protected TextPaster(Shell shell, Clipboard clipboard) {
			super(shell, clipboard);
		}

		@Override
		public boolean canEnable(TransferData[] availableTypes) {
			fAvailableTypes= availableTypes;
			return PasteAction.isAvailable(TextTransfer.getInstance(), availableTypes) && ! PasteAction.isAvailable(FileTransfer.getInstance(), availableTypes);
		}

		@Override
		public boolean canPasteOn(IJavaElement[] javaElements, IResource[] resources, IWorkingSet[] selectedWorkingSets, List<?> selectedElements) throws JavaModelException {
			final String text= getClipboardText(fAvailableTypes);
			
			IStorage storage= new IEncodedStorage() {
				public Object getAdapter(Class adapter) {
					return null;
				}
				public boolean isReadOnly() {
					return false;
				}
				public String getName() {
					return null;
				}
				public IPath getFullPath() {
					return null;
				}
				public InputStream getContents() throws CoreException {
					try {
						return new ByteArrayInputStream(text.getBytes(getCharset()));
					} catch (UnsupportedEncodingException e) {
						throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(),
			                    IJavaStatusConstants.INTERNAL_ERROR, JavaUIMessages.JavaPlugin_internal_error, e));
					}
				}
				public String getCharset() throws CoreException {
					return "UTF-8"; //$NON-NLS-1$
				}
			};
			try {
				if (ApplyPatchOperation.isPatch(storage)) {
					fPatchStorage= storage;
					return true;
				}
			} catch (CoreException e) {
				// continue
			}
			
			
			if (selectedWorkingSets.length > 1)
				return false;
			if (resources.length != 0)
				return false; //alternative: create text file?
			if (javaElements.length > 1)
				return false;

			IJavaProject javaProject= null;
			IJavaElement destination= null;
			if (javaElements.length == 1) {
				destination= javaElements[0];
				javaProject= destination.getJavaProject();
			} else if (selectedWorkingSets.length == 1) {
				// OK
			} else if (selectedElements.size() != 0) {
				return false; // e.g. ClassPathContainer
			}
			
			computeLatestVM();
			parseCUs(javaProject, text);

			if (fParsedCus.length == 0)
				return false;

			if (destination == null)
				return true;

			/*
			 * 0 to 1 package declarations in source: paste into package, adapt package declarations
			 * 2+ package declarations: always paste into source folder
			 */

			IPackageFragmentRoot packageFragmentRoot;
			IPackageFragment destinationPack;
			IJavaElement cu;
			switch (destination.getElementType()) {
				case IJavaElement.JAVA_PROJECT :
					IPackageFragmentRoot[] packageFragmentRoots= ((IJavaProject) destination).getPackageFragmentRoots();
					for (int i= 0; i < packageFragmentRoots.length; i++) {
						packageFragmentRoot= packageFragmentRoots[i];
						if (isWritable(packageFragmentRoot)) {
							fDestination= packageFragmentRoot;
							return true;
						}
					}
					return false;

				case IJavaElement.PACKAGE_FRAGMENT_ROOT :
					packageFragmentRoot= (IPackageFragmentRoot) destination;
					if (isWritable(packageFragmentRoot)) {
						fDestination= packageFragmentRoot;
						return true;
					}
					return false;

				case IJavaElement.PACKAGE_FRAGMENT :
					destinationPack= (IPackageFragment) destination;
					packageFragmentRoot= (IPackageFragmentRoot) destinationPack.getParent();
					if (isWritable(packageFragmentRoot)) {
						fDestination= packageFragmentRoot;
						if (fPackageDeclCount <= 1) {
							fDestinationPack= destinationPack;
						}
						return true;
					}
					return false;

				case IJavaElement.COMPILATION_UNIT :
					destinationPack= (IPackageFragment) destination.getParent();
					packageFragmentRoot= (IPackageFragmentRoot) destinationPack.getParent();
					if (isWritable(packageFragmentRoot)) {
						fDestination= packageFragmentRoot;
						if (fPackageDeclCount <= 1) {
							fDestinationPack= destinationPack;
							fDestinationType= ((ICompilationUnit) destination).findPrimaryType();
						}
						return true;
					}
					return false;

				case IJavaElement.TYPE:
					cu= ((IType) destination).getCompilationUnit();
					if (cu != null) {
						fDestinationType= (IType) destination;
						fDestinationPack= (IPackageFragment) cu.getParent();
						fDestination= (IPackageFragmentRoot) fDestinationPack.getParent();
						return true;
					}
					return false;

				case IJavaElement.METHOD:
					cu= ((IMethod) destination).getCompilationUnit();
					if (cu != null) {
						fDestinationMethod= (IMethod) destination;
						fDestinationType= (IType) destination.getParent();
						fDestinationPack= (IPackageFragment) cu.getParent();
						fDestination= (IPackageFragmentRoot) fDestinationPack.getParent();
						return true;
					}
					return false;

				default:
					return false;
			}
		}

		private boolean isWritable(IPackageFragmentRoot packageFragmentRoot) {
			try {
				return packageFragmentRoot.exists() && ! packageFragmentRoot.isArchive() && ! packageFragmentRoot.isReadOnly()
						&& packageFragmentRoot.getKind() == IPackageFragmentRoot.K_SOURCE;
			} catch (JavaModelException e) {
				return false;
			}
		}

		@Override
		public void paste(IJavaElement[] javaElements, IResource[] resources, final IWorkingSet[] selectedWorkingSets, TransferData[] availableTypes) throws JavaModelException, InterruptedException, InvocationTargetException{
			if (fPatchStorage != null) {
				IResource resource= null;
				if (resources.length > 0) {
					resource= resources[0];
				} else if (javaElements.length > 0) {
					resource= javaElements[0].getResource();
				}
				// XXX: This will be fixed in 3.7, see https://bugs.eclipse.org/309803
				new org.eclipse.team.internal.ui.synchronize.patch.ApplyPatchOperation(null, fPatchStorage, resource, new CompareConfiguration()).openWizard();
				return;
			}
			
			final IEditorPart[] editorPart= new IEditorPart[1];

			IRunnableWithProgress op= new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException {

					final ArrayList<ICompilationUnit> cus= new ArrayList<ICompilationUnit>();
					try {
						JavaCore.run(new IWorkspaceRunnable() {
							public void run(IProgressMonitor pm) throws CoreException {
								pm.beginTask("", 1 + fParsedCus.length); //$NON-NLS-1$

								if (fDestination == null) {
									fDestination= createNewProject(new SubProgressMonitor(pm, 1));
								} else {
									pm.worked(1);
								}
								IConfirmQuery confirmQuery= new ReorgQueries(getShell()).createYesYesToAllNoNoToAllQuery(ReorgMessages.PasteAction_TextPaster_confirmOverwriting, true, IReorgQueries.CONFIRM_OVERWRITING);
								for (int i= 0; i < fParsedCus.length; i++) {
									if (pm.isCanceled())
										break;
									ICompilationUnit cu= pasteCU(fParsedCus[i], new SubProgressMonitor(pm, 1), confirmQuery);
									if (cu != null)
										cus.add(cu);
								}

								if (selectedWorkingSets.length == 1) {
									IWorkingSet ws= selectedWorkingSets[0];
									if (!IWorkingSetIDs.OTHERS.equals(ws.getId())) {
										ArrayList<IAdaptable> newElements= new ArrayList<IAdaptable>();
										newElements.addAll(Arrays.asList(ws.getElements()));
										newElements.addAll(Arrays.asList(ws.adaptElements(new IAdaptable[] { fDestination.getJavaProject() })));
										ws.setElements(newElements.toArray(new IAdaptable[newElements.size()]));
									}
								}
							}
						}, monitor);
					} catch (OperationCanceledException e) {
						// cancelling is fine
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					} finally {
						monitor.done();
					}
					IResource[] cuResources= ResourceUtil.getFiles(cus.toArray(new ICompilationUnit[cus.size()]));
					SelectionUtil.selectAndReveal(cuResources, PlatformUI.getWorkbench().getActiveWorkbenchWindow());
				}

				private ICompilationUnit pasteCU(ParsedCu parsedCu, IProgressMonitor pm, IConfirmQuery confirmQuery) throws CoreException, OperationCanceledException {
					pm.beginTask("", 4); //$NON-NLS-1$
					try {
						IPackageFragment destinationPack;
						if (fDestinationPack != null) {
							destinationPack= fDestinationPack;
							pm.worked(1);
						} else {
							String packageName= parsedCu.getPackageName();
							if (packageName == null)
								packageName= ReorgMessages.PasteAction_snippet_default_package_name;
							destinationPack= fDestination.getPackageFragment(packageName);
							if (! destinationPack.exists()) {
								JavaModelUtil.getPackageFragmentRoot(destinationPack).createPackageFragment(packageName, true, new SubProgressMonitor(pm, 1));
							} else {
								pm.worked(1);
							}
						}

						String parsedText= Strings.trimIndentation(parsedCu.getText(), destinationPack.getJavaProject(), true);
						int kind= parsedCu.getKind();
						if (kind == ASTParser.K_COMPILATION_UNIT) {
							final String cuName= parsedCu.getTypeName() + JavaModelUtil.DEFAULT_CU_SUFFIX;
							ICompilationUnit cu= destinationPack.getCompilationUnit(cuName);
							boolean alreadyExists= cu.exists();
							if (alreadyExists) {
								String msg= Messages.format(ReorgMessages.PasteAction_TextPaster_exists, new Object[] { BasicElementLabels.getResourceName(cuName)});
								boolean overwrite= confirmQuery.confirm(msg);
								if (! overwrite)
									return null;

								editorPart[0]= openCu(cu); //Open editor before overwriting to allow undo to restore original package declaration
							}

							parsedText= parsedText.replaceAll("\r\n?|\n", StubUtility.getLineDelimiterUsed(cu)); //$NON-NLS-1$
							destinationPack.createCompilationUnit(cuName, parsedText, true, new SubProgressMonitor(pm, 1));

							if (! alreadyExists) {
								editorPart[0]= openCu(cu);
							}
							if (fDestinationPack != null) {
								if (fDestinationPack.getElementName().length() == 0) {
									removePackageDeclaration(cu);
								} else {
									cu.createPackageDeclaration(fDestinationPack.getElementName(), new SubProgressMonitor(pm, 1));
								}
							} else {
								String packageName= destinationPack.getElementName();
								if (packageName.length() > 0) {
									cu.createPackageDeclaration(packageName, new SubProgressMonitor(pm, 1));
								}
							}
							if (! alreadyExists && editorPart[0] != null)
								editorPart[0].doSave(new SubProgressMonitor(pm, 1)); //avoid showing error marker due to missing/wrong package declaration
							return cu;

						} else if (kind == ASTParser.K_CLASS_BODY_DECLARATIONS || kind == ASTParser.K_STATEMENTS) {
							return pasteBodyDeclsOrStatements(destinationPack, parsedText, kind, new SubProgressMonitor(pm, 2));
						} else {
							throw new IllegalStateException("Unexpected kind: " + kind); //$NON-NLS-1$
						}
					} finally {
						pm.done();
					}
				}

				private ICompilationUnit pasteBodyDeclsOrStatements(IPackageFragment destinationPack, String parsedText, int kind, IProgressMonitor pm) throws CoreException, JavaModelException {
					ICompilationUnit cu;
					IType type;
					String typeName;
					if (fDestinationType != null) {
						cu= fDestinationType.getCompilationUnit();
						type= fDestinationType;
						typeName= fDestinationType.getElementName();
					} else {
						typeName= ReorgMessages.PasteAction_snippet_default_type_name;
						cu= destinationPack.getCompilationUnit(typeName + JavaModelUtil.DEFAULT_CU_SUFFIX);
						type= cu.getType(typeName);
					}
					if (cu.exists()) {
						editorPart[0]= openCu(cu); //Open editor before pasting to allow undo to restore original contents
						ITextFileBuffer fileBuffer= RefactoringFileBuffers.acquire(cu);
						try {
							IDocument document= fileBuffer.getDocument();

							ASTParser parser= ASTParser.newParser(ASTProvider.SHARED_AST_LEVEL);
							parser.setProject(cu.getJavaProject());
							parser.setSource(document.get().toCharArray());
							parser.setStatementsRecovery(true);
							CompilationUnit cuNode= (CompilationUnit) parser.createAST(pm);

							AbstractTypeDeclaration typeDecl= type.exists() ? ASTNodeSearchUtil.getAbstractTypeDeclarationNode(type, cuNode) : null;
							MethodDeclaration methodDecl= fDestinationMethod != null ? ASTNodeSearchUtil.getMethodDeclarationNode(fDestinationMethod, cuNode) : null;

							ITrackedNodePosition textPosition= createOrFillTypeDeclaration(cuNode, document, cu, typeDecl, typeName, methodDecl, kind, parsedText);
							EditorUtility.revealInEditor(editorPart[0], textPosition.getStartPosition() + textPosition.getLength(), 0);
							// leave unsaved
						} finally {
							RefactoringFileBuffers.release(cu);
						}

					} else {
						String delim= StubUtility.getLineDelimiterUsed(cu);
						String fileComment= null;
						if (StubUtility.doAddComments(cu.getJavaProject())) {
							fileComment= CodeGeneration.getFileComment(cu, delim);
						}
						String cuContent= CodeGeneration.getCompilationUnitContent(cu, fileComment, null, "", delim); //$NON-NLS-1$
						if (cuContent == null)
							cuContent= ""; //$NON-NLS-1$
						IDocument document= new Document(cuContent);

						ASTParser parser= ASTParser.newParser(ASTProvider.SHARED_AST_LEVEL);
						parser.setProject(cu.getJavaProject());
						parser.setSource(cuContent.toCharArray());
						parser.setStatementsRecovery(true);
						CompilationUnit cuNode= (CompilationUnit) parser.createAST(pm);

						ITrackedNodePosition textPosition= createOrFillTypeDeclaration(cuNode, document, cu, null, typeName, null, kind, parsedText);
						destinationPack.createCompilationUnit(cu.getElementName(), document.get(), false, null);
						editorPart[0]= openCu(cu);
						EditorUtility.revealInEditor(editorPart[0], textPosition.getStartPosition() + textPosition.getLength(), 0);
					}
					return cu;
				}

				private ITrackedNodePosition createOrFillTypeDeclaration(
						CompilationUnit cuNode,
						IDocument document,
						ICompilationUnit cu,
						AbstractTypeDeclaration typeDecl,
						String typeName,
						MethodDeclaration methodDecl,
						int kind,
						String parsedText) throws CoreException {

					AST ast= cuNode.getAST();
					ASTRewrite rewrite= ASTRewrite.create(ast);
					String delim= StubUtility.getLineDelimiterUsed(cu);

					if (typeDecl == null) {
						typeDecl= ast.newTypeDeclaration();
						typeDecl.getName().setIdentifier(typeName);
						if (StubUtility.doAddComments(cu.getJavaProject())) {
							String typeComment= CodeGeneration.getTypeComment(cu, typeName, delim);
							if (typeComment != null)
								typeDecl.setJavadoc((Javadoc) rewrite.createStringPlaceholder(typeComment, ASTNode.JAVADOC));
						}
						typeDecl.modifiers().add(ast.newModifier(ModifierKeyword.PUBLIC_KEYWORD));

						String typeBody= CodeGeneration.getTypeBody(CodeGeneration.CLASS_BODY_TEMPLATE_ID, cu, typeName, delim);
						if (typeBody != null) {
							BodyDeclaration typeBodyNode= (BodyDeclaration) rewrite.createStringPlaceholder(typeBody, ASTNode.METHOD_DECLARATION);
							ListRewrite bodyRewrite= rewrite.getListRewrite(typeDecl, typeDecl.getBodyDeclarationsProperty());
							bodyRewrite.insertFirst(typeBodyNode, null);
						}

						rewrite.getListRewrite(cuNode, CompilationUnit.TYPES_PROPERTY).insertLast(typeDecl, null);
					}

					ITrackedNodePosition textPosition;
					if (kind == ASTParser.K_CLASS_BODY_DECLARATIONS) {
						ListRewrite bodyRewrite= rewrite.getListRewrite(typeDecl, typeDecl.getBodyDeclarationsProperty());
						BodyDeclaration textNode= (BodyDeclaration) rewrite.createStringPlaceholder(parsedText, ASTNode.METHOD_DECLARATION);
						bodyRewrite.insertLast(textNode, null);
						textPosition= rewrite.track(textNode);

					} else { // kind == ASTParser.K_STATEMENTS
						if (methodDecl == null || methodDecl.getBody() == null) {
							methodDecl= ast.newMethodDeclaration();
							String qualifiedtypeName= fDestinationType != null ? fDestinationType.getFullyQualifiedName('.') : cu.getParent().getElementName() + '.' + typeName;
							if (StubUtility.doAddComments(cu.getJavaProject())) {
								String methodComment= CodeGeneration.getMethodComment(cu, qualifiedtypeName, methodDecl, null, delim);
								if (methodComment != null)
									methodDecl.setJavadoc((Javadoc) rewrite.createStringPlaceholder(methodComment, ASTNode.JAVADOC));
							}
							methodDecl.modifiers().addAll(ast.newModifiers(Modifier.PUBLIC | Modifier.STATIC));
							String methodName= "main"; //$NON-NLS-1$
							methodDecl.getName().setIdentifier(methodName);
							SingleVariableDeclaration param= ast.newSingleVariableDeclaration();
							param.setType(ast.newArrayType(ast.newSimpleType(ast.newSimpleName("String")))); //$NON-NLS-1$
							param.getName().setIdentifier("args"); //$NON-NLS-1$
							methodDecl.parameters().add(param);
							Block block= ast.newBlock();
							methodDecl.setBody(block);

							rewrite.getListRewrite(typeDecl, typeDecl.getBodyDeclarationsProperty()).insertLast(methodDecl, null);
						}
						Block body= methodDecl.getBody();
						ListRewrite statementsRewrite= rewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY);
						Statement textNode= (Statement) rewrite.createStringPlaceholder(parsedText, ASTNode.EMPTY_STATEMENT);
						statementsRewrite.insertLast(textNode, null);
						textPosition= rewrite.track(textNode);
					}

					DocumentRewriteSession rewriteSession= null;
					if (document instanceof IDocumentExtension4)
						rewriteSession= ((IDocumentExtension4) document).startRewriteSession(DocumentRewriteSessionType.UNRESTRICTED_SMALL);
					TextEdit edit= rewrite.rewriteAST(document, cu.getJavaProject().getOptions(true));
					try {
						edit.apply(document, TextEdit.UPDATE_REGIONS);
						return textPosition;
					} catch (MalformedTreeException e) {
						JavaPlugin.log(e);
					} catch (BadLocationException e) {
						JavaPlugin.log(e);
					} finally {
						if (rewriteSession != null)
							((IDocumentExtension4) document).stopRewriteSession(rewriteSession);
					}
					return null;
				}

				private IPackageFragmentRoot createNewProject(SubProgressMonitor pm) throws CoreException {
					pm.beginTask("", 10); //$NON-NLS-1$
					IProject project;
					int i= 1;
					do {
						String name= Messages.format(ReorgMessages.PasteAction_projectName, i == 1 ? (Object) "" : new Integer(i)); //$NON-NLS-1$
						project= JavaPlugin.getWorkspace().getRoot().getProject(name);
						i++;
					} while (project.exists());

					BuildPathsBlock.createProject(project, null, new SubProgressMonitor(pm, 3));
					BuildPathsBlock.addJavaNature(project, new SubProgressMonitor(pm, 1));
					IJavaProject javaProject= JavaCore.create(project);

					IResource srcFolder;
					IPreferenceStore store= PreferenceConstants.getPreferenceStore();
					String sourceFolderName= store.getString(PreferenceConstants.SRCBIN_SRCNAME);
					if (store.getBoolean(PreferenceConstants.SRCBIN_FOLDERS_IN_NEWPROJ) && sourceFolderName.length() > 0) {
						IFolder folder= project.getFolder(sourceFolderName);
						if (! folder.exists()) {
							folder.create(false, true, new SubProgressMonitor(pm, 1));
						}
						srcFolder= folder;
					} else {
						srcFolder= project;
					}

					if (fCompilerCompliance != null) {
						Map<String, String> options= javaProject.getOptions(false);
						JavaModelUtil.setComplianceOptions(options, fCompilerCompliance);
						JavaModelUtil.setDefaultClassfileOptions(options, fCompilerCompliance);
						javaProject.setOptions(options);
					}
					IClasspathEntry srcEntry= JavaCore.newSourceEntry(srcFolder.getFullPath());
					IClasspathEntry jreEntry= JavaCore.newContainerEntry(fVMPath);
					IPath outputLocation= BuildPathsBlock.getDefaultOutputLocation(javaProject);
					IClasspathEntry[] cpes= new IClasspathEntry[] { srcEntry, jreEntry };
					javaProject.setRawClasspath(cpes, outputLocation, new SubProgressMonitor(pm, 1));
					return javaProject.getPackageFragmentRoot(srcFolder);
				}

				private void removePackageDeclaration(final ICompilationUnit cu) throws JavaModelException, CoreException {
					IPackageDeclaration[] packageDeclarations= cu.getPackageDeclarations();
					if (packageDeclarations.length != 0) {
						ITextFileBuffer buffer= null;
						try {
							buffer= RefactoringFileBuffers.acquire(cu);
							ISourceRange sourceRange= packageDeclarations[0].getSourceRange();
							buffer.getDocument().replace(sourceRange.getOffset(), sourceRange.getLength(), ""); //$NON-NLS-1$
						} catch (BadLocationException e) {
							JavaPlugin.log(e);
						} finally {
							if (buffer != null)
								RefactoringFileBuffers.release(cu);
						}
					}
				}
			};

			IRunnableContext context= JavaPlugin.getActiveWorkbenchWindow();
			if (context == null) {
				context= new BusyIndicatorRunnableContext();
			}
			//TODO: wrap op in workspace runnable and pass to JavaCore.run(..); take project creation out of UI thread.
			PlatformUI.getWorkbench().getProgressService().runInUI(context, op, JavaPlugin.getWorkspace().getRoot());

			if (editorPart[0] != null)
				editorPart[0].getEditorSite().getPage().activate(editorPart[0]); //activate editor again, since runInUI restores previous active part
		}

		private IEditorPart openCu(ICompilationUnit cu) {
			try {
				return JavaUI.openInEditor(cu, true, true);
			} catch (PartInitException e) {
				JavaPlugin.log(e);
				return null;
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
				return null;
			}
		}

		private void parseCUs(IJavaProject javaProject, String text) {
			IScanner scanner= ToolFactory.createScanner(false, false, false, false);
			scanner.setSource(text.toCharArray());

			ArrayList<ParsedCu> cus= new ArrayList<ParsedCu>();
			int start= 0;
			boolean tokensScanned= false;
			fPackageDeclCount= 0;
			int tok;
			while (true) {
				try {
					tok= scanner.getNextToken();
				} catch (InvalidInputException e) {
					// Handle gracefully to give the ASTParser a chance to recover,
					// see https://bugs.eclipse.org/bugs/show_bug.cgi?id=168691
					tok= ITerminalSymbols.TokenNameEOF;
				}
				if (tok == ITerminalSymbols.TokenNamepackage) {
					fPackageDeclCount++;
					if (tokensScanned) {
						int packageStart= scanner.getCurrentTokenStartPosition();
						List<ParsedCu> parsed= ParsedCu.parseCus(javaProject, fCompilerCompliance, text.substring(start, packageStart));
						if (parsed.size() > 0) {
							cus.addAll(parsed);
							start= packageStart;
						}
					}
				} else if (tok == ITerminalSymbols.TokenNameEOF) {
					List<ParsedCu> parsed= ParsedCu.parseCus(javaProject, fCompilerCompliance, text.substring(start, text.length()));
					if (parsed.size() > 0) {
						cus.addAll(parsed);
					}
					break;
				}
				tokensScanned= true;
			}
			fParsedCus= cus.toArray(new ParsedCu[cus.size()]);
		}

		private void computeLatestVM() {
			IVMInstall bestVM= JavaRuntime.getDefaultVMInstall();
			String bestVersion= getVMVersion(bestVM);

			IExecutionEnvironmentsManager eeManager= JavaRuntime.getExecutionEnvironmentsManager();
			IExecutionEnvironment bestEE= null;

			IExecutionEnvironment[] ees= eeManager.getExecutionEnvironments();
			outer: for (int j= 0; j < ees.length; j++) {
				IExecutionEnvironment ee= ees[j];
				IVMInstall vm= ee.getDefaultVM();
				String ver= getVMVersion(vm);
				if (ver != null) {
					if (bestVersion == null || JavaModelUtil.isVersionLessThan(bestVersion, ver) || bestVersion.equals(ver)) {
						bestVersion= ver;
						bestEE= ee;
					}
				} else {
					String eeVer= JavaModelUtil.getExecutionEnvironmentCompliance(ee);
					IVMInstall[] compatibleVMs= ee.getCompatibleVMs();
					for (int i= 0; i < compatibleVMs.length; i++) {
						vm= compatibleVMs[i];
						ver= getVMVersion(vm);
						if (!eeVer.equals(ver))
							continue; // don't want to set an EE where there's no strictly compatible VM
						if (bestVersion == null || JavaModelUtil.isVersionLessThan(bestVersion, ver) || bestVersion.equals(ver)) {
							bestVersion= ver;
							bestEE= ee;
							continue outer;
						}
					}
				}
			}

			IVMInstallType[] vmTypes= JavaRuntime.getVMInstallTypes();
			for (int i= 0; i < vmTypes.length; i++) {
				IVMInstall[] vms= vmTypes[i].getVMInstalls();
				for (int j= 0; j < vms.length; j++) {
					IVMInstall vm= vms[j];
					String ver= getVMVersion(vm);
					if (ver != null && (bestVersion == null || JavaModelUtil.isVersionLessThan(bestVersion, ver))) {
						bestVersion= ver;
						bestVM= vm;
						bestEE= null;
					}
				}
			}

			if (bestEE != null) {
				fVMPath= JavaRuntime.newJREContainerPath(bestEE);
				fCompilerCompliance= bestVersion;
			} else if (bestVM != null) {
				fVMPath= JavaRuntime.newJREContainerPath(bestVM);
				fCompilerCompliance= bestVersion;
			} else {
				fVMPath= JavaRuntime.newDefaultJREContainerPath();
			}
		}

		private String getVMVersion(IVMInstall vm) {
			if (vm instanceof IVMInstall2) {
				IVMInstall2 vm2= (IVMInstall2) vm;
				return JavaModelUtil.getCompilerCompliance(vm2, null);
			} else {
				return null;
			}
		}
    }

	private static class WorkingSetPaster extends Paster {
		protected WorkingSetPaster(Shell shell, Clipboard clipboard) {
			super(shell, clipboard);
		}
		@Override
		public void paste(IJavaElement[] selectedJavaElements, IResource[] selectedResources, IWorkingSet[] selectedWorkingSets, TransferData[] availableTypes) throws JavaModelException, InterruptedException, InvocationTargetException {
			IWorkingSet workingSet= selectedWorkingSets[0];
			Set<IAdaptable> elements= new HashSet<IAdaptable>(Arrays.asList(workingSet.getElements()));
			IJavaElement[] javaElements= getClipboardJavaElements(availableTypes);
			if (javaElements != null) {
				for (int i= 0; i < javaElements.length; i++) {
					if (!ReorgUtils.containsElementOrParent(elements, javaElements[i]))
						elements.add(javaElements[i]);
				}
			}
			IResource[] resources= getClipboardResources(availableTypes);
			if (resources != null) {
				List<IJavaElement> realJavaElements= new ArrayList<IJavaElement>();
				List<IResource> realResource= new ArrayList<IResource>();
				ReorgUtils.splitIntoJavaElementsAndResources(resources, realJavaElements, realResource);
				for (Iterator<IJavaElement> iter= realJavaElements.iterator(); iter.hasNext();) {
					IJavaElement element= iter.next();
					if (!ReorgUtils.containsElementOrParent(elements, element))
						elements.add(element);
				}
				for (Iterator<IResource> iter= realResource.iterator(); iter.hasNext();) {
					IResource element= iter.next();
					if (!ReorgUtils.containsElementOrParent(elements, element))
						elements.add(element);
				}
			}
			workingSet.setElements(elements.toArray(new IAdaptable[elements.size()]));
		}
		@Override
		public boolean canEnable(TransferData[] availableTypes) {
			return isAvailable(ResourceTransfer.getInstance(), availableTypes) ||
				isAvailable(JavaElementTransfer.getInstance(), availableTypes);
		}
		@Override
		public boolean canPasteOn(IJavaElement[] selectedJavaElements, IResource[] selectedResources, IWorkingSet[] selectedWorkingSets, List<?> selectedElements) throws JavaModelException {
			if (selectedResources.length != 0 || selectedJavaElements.length != 0 || selectedWorkingSets.length != 1)
				return false;
			IWorkingSet ws= selectedWorkingSets[0];
			return !IWorkingSetIDs.OTHERS.equals(ws.getId());
		}
	}

    private static class ProjectPaster extends Paster{

    	protected ProjectPaster(Shell shell, Clipboard clipboard) {
			super(shell, clipboard);
		}

		@Override
		public boolean canEnable(TransferData[] availableDataTypes) {
			boolean resourceTransfer= isAvailable(ResourceTransfer.getInstance(), availableDataTypes);
			boolean javaElementTransfer= isAvailable(JavaElementTransfer.getInstance(), availableDataTypes);
			if (! javaElementTransfer)
				return canPasteSimpleProjects(availableDataTypes);
			if (! resourceTransfer)
				return canPasteJavaProjects(availableDataTypes);
			return canPasteJavaProjects(availableDataTypes) && canPasteSimpleProjects(availableDataTypes);
    	}

		@Override
		public void paste(IJavaElement[] javaElements, IResource[] resources, IWorkingSet[] selectedWorkingSets, TransferData[] availableTypes) {
			pasteProjects(availableTypes);
		}

		private void pasteProjects(TransferData[] availableTypes) {
			pasteProjects(getProjectsToPaste(availableTypes));
		}

		private void pasteProjects(IProject[] projects){
			Shell shell= getShell();
			for (int i = 0; i < projects.length; i++) {
				new CopyProjectOperation(shell).copyProject(projects[i]);
			}
		}
		private IProject[] getProjectsToPaste(TransferData[] availableTypes) {
			IResource[] resources= getClipboardResources(availableTypes);
			IJavaElement[] javaElements= getClipboardJavaElements(availableTypes);
			Set<IResource> result= new HashSet<IResource>();
			if (resources != null)
				result.addAll(Arrays.asList(resources));
			if (javaElements != null)
				result.addAll(Arrays.asList(ReorgUtils.getNotNulls(ReorgUtils.getResources(javaElements))));
			Assert.isTrue(result.size() > 0);
			return result.toArray(new IProject[result.size()]);
		}

		@Override
		public boolean canPasteOn(IJavaElement[] javaElements, IResource[] resources, IWorkingSet[] selectedWorkingSets, List<?> selectedElements) {
			return selectedWorkingSets.length == 0; // Can't paste on working sets here
		}

		private boolean canPasteJavaProjects(TransferData[] availableDataTypes) {
			IJavaElement[] javaElements= getClipboardJavaElements(availableDataTypes);
			return 	javaElements != null &&
					javaElements.length != 0 &&
					! ReorgUtils.hasElementsNotOfType(javaElements, IJavaElement.JAVA_PROJECT);
		}

		private boolean canPasteSimpleProjects(TransferData[] availableDataTypes) {
			IResource[] resources= getClipboardResources(availableDataTypes);
			if (resources == null || resources.length == 0) return false;
			for (int i= 0; i < resources.length; i++) {
				if (resources[i].getType() != IResource.PROJECT || ! ((IProject)resources[i]).isOpen())
					return false;
			}
			return true;
		}
    }

    private static class FilePaster extends Paster{
		protected FilePaster(Shell shell, Clipboard clipboard) {
			super(shell, clipboard);
		}

		@Override
		public void paste(IJavaElement[] javaElements, IResource[] resources, IWorkingSet[] selectedWorkingSets, TransferData[] availableTypes) throws JavaModelException {
			String[] fileData= getClipboardFiles(availableTypes);
			if (fileData == null)
				return;

			IContainer container= getAsContainer(getTarget(javaElements, resources));
			if (container == null)
				return;

			new CopyFilesAndFoldersOperation(getShell()).copyFiles(fileData, container);
		}

		private Object getTarget(IJavaElement[] javaElements, IResource[] resources) {
			if (javaElements.length + resources.length == 1){
				if (javaElements.length == 1)
					return javaElements[0];
				else
					return resources[0];
			} else
				return getCommonParent(javaElements, resources);
		}

		@Override
		public boolean canPasteOn(IJavaElement[] javaElements, IResource[] resources, IWorkingSet[] selectedWorkingSets, List<?> selectedElements) throws JavaModelException {
			Object target= getTarget(javaElements, resources);
			return target != null && canPasteFilesOn(getAsContainer(target)) && selectedWorkingSets.length == 0;
		}

		@Override
		public boolean canEnable(TransferData[] availableDataTypes) {
			return isAvailable(FileTransfer.getInstance(), availableDataTypes);
		}

		private boolean canPasteFilesOn(Object target) {
			boolean isPackageFragment= target instanceof IPackageFragment;
			boolean isJavaProject= target instanceof IJavaProject;
			boolean isPackageFragmentRoot= target instanceof IPackageFragmentRoot;
			boolean isContainer= target instanceof IContainer;

			if (!(isPackageFragment || isJavaProject || isPackageFragmentRoot || isContainer))
				return false;

			if (isContainer) {
				if (target instanceof IProject)
					return ((IProject)target).isOpen();

				return true;
			} else {
				if (isJavaProject && !((IJavaProject)target).isOpen())
					return false;

				IJavaElement element= (IJavaElement)target;
				return !element.isReadOnly();
			}
		}

		private IContainer getAsContainer(Object target) throws JavaModelException{
			if (target == null)
				return null;
			if (target instanceof IContainer)
				return (IContainer)target;
			if (target instanceof IFile)
				return ((IFile)target).getParent();
			return getAsContainer(((IJavaElement)target).getCorrespondingResource());
		}

		private String[] getClipboardFiles(TransferData[] availableDataTypes) {
			Transfer transfer= FileTransfer.getInstance();
			if (isAvailable(transfer, availableDataTypes)) {
				return (String[])getContents(getClipboard(), transfer, getShell());
			}
			return null;
		}
		private Object getCommonParent(IJavaElement[] javaElements, IResource[] resources) {
			return new ParentChecker(resources, javaElements).getCommonParent();
		}
    }
    private static class JavaElementAndResourcePaster extends Paster {

		protected JavaElementAndResourcePaster(Shell shell, Clipboard clipboard) {
			super(shell, clipboard);
		}

		private TransferData[] fAvailableTypes;

		@Override
		public void paste(IJavaElement[] javaElements, IResource[] resources, IWorkingSet[] selectedWorkingSets, TransferData[] availableTypes) throws JavaModelException, InterruptedException, InvocationTargetException{
			IResource[] clipboardResources= getClipboardResources(availableTypes);
			if (clipboardResources == null)
				clipboardResources= new IResource[0];
			IJavaElement[] clipboardJavaElements= getClipboardJavaElements(availableTypes);
			if (clipboardJavaElements == null)
				clipboardJavaElements= new IJavaElement[0];

			Object destination= getTarget(javaElements, resources);
			Assert.isNotNull(destination);
			Assert.isLegal(clipboardResources.length + clipboardJavaElements.length > 0);
			ReorgCopyStarter.create(clipboardJavaElements, clipboardResources, ReorgDestinationFactory.createDestination(destination)).run(getShell());
		}

		private Object getTarget(IJavaElement[] javaElements, IResource[] resources) {
			if (javaElements.length + resources.length == 1){
				if (javaElements.length == 1)
					return javaElements[0];
				else
					return resources[0];
			} else
				return getCommonParent(javaElements, resources);
		}

		private Object getCommonParent(IJavaElement[] javaElements, IResource[] resources) {
			return new ParentChecker(resources, javaElements).getCommonParent();
		}

		@Override
		public boolean canPasteOn(IJavaElement[] javaElements, IResource[] resources, IWorkingSet[] selectedWorkingSets, List<?> selectedElements) throws JavaModelException {
			if (selectedWorkingSets.length != 0)
				return false;
			IResource[] clipboardResources= getClipboardResources(fAvailableTypes);
			if (clipboardResources == null)
				clipboardResources= new IResource[0];
			IJavaElement[] clipboardJavaElements= getClipboardJavaElements(fAvailableTypes);
			if (clipboardJavaElements == null)
				clipboardJavaElements= new IJavaElement[0];
			Object destination= getTarget(javaElements, resources);
			return ReorgCopyStarter.create(clipboardJavaElements, clipboardResources, ReorgDestinationFactory.createDestination(destination)) != null;
		}

		@Override
		public boolean canEnable(TransferData[] availableTypes) {
			fAvailableTypes= availableTypes;
			return isAvailable(JavaElementTransfer.getInstance(), availableTypes) || isAvailable(ResourceTransfer.getInstance(), availableTypes);
		}
    }

    private static class TypedSourcePaster extends Paster{

		protected TypedSourcePaster(Shell shell, Clipboard clipboard) {
			super(shell, clipboard);
		}
		private TransferData[] fAvailableTypes;

		@Override
		public boolean canEnable(TransferData[] availableTypes) {
			fAvailableTypes= availableTypes;
			return isAvailable(TypedSourceTransfer.getInstance(), availableTypes);
		}

		@Override
		public boolean canPasteOn(IJavaElement[] selectedJavaElements, IResource[] selectedResources, IWorkingSet[] selectedWorkingSets, List<?> selectedElements) throws JavaModelException {
			if (selectedResources.length != 0 || selectedWorkingSets.length != 0)
				return false;
			TypedSource[] typedSources= getClipboardTypedSources(fAvailableTypes);
			Object destination= getTarget(selectedJavaElements, selectedResources);
			if (destination instanceof IJavaElement)
				return ReorgTypedSourcePasteStarter.create(typedSources, (IJavaElement)destination) != null;
			return false;
		}

		@Override
		public void paste(IJavaElement[] selectedJavaElements, IResource[] selectedResources, IWorkingSet[] selectedWorkingSets, TransferData[] availableTypes) throws JavaModelException, InterruptedException, InvocationTargetException {
			TypedSource[] typedSources= getClipboardTypedSources(availableTypes);
			IJavaElement destination= getTarget(selectedJavaElements, selectedResources);
			ReorgTypedSourcePasteStarter.create(typedSources, destination).run(getShell());
		}

		private static IJavaElement getTarget(IJavaElement[] selectedJavaElements, IResource[] selectedResources) {
			Assert.isTrue(selectedResources.length == 0);
			if (selectedJavaElements.length == 1)
				return getAsTypeOrCu(selectedJavaElements[0]);
			Object parent= new ParentChecker(selectedResources, selectedJavaElements).getCommonParent();
			if (parent instanceof IJavaElement)
				return getAsTypeOrCu((IJavaElement)parent);
			return null;
		}
		private static IJavaElement getAsTypeOrCu(IJavaElement element) {
			//try to get type first
			if (element.getElementType() == IJavaElement.COMPILATION_UNIT || element.getElementType() == IJavaElement.TYPE)
				return element;
			IJavaElement ancestorType= element.getAncestor(IJavaElement.TYPE);
			if (ancestorType != null)
				return ancestorType;
			return ReorgUtils.getCompilationUnit(element);
		}
		private static class ReorgTypedSourcePasteStarter {

			private final PasteTypedSourcesRefactoring fPasteRefactoring;

			private ReorgTypedSourcePasteStarter(PasteTypedSourcesRefactoring pasteRefactoring) {
				Assert.isNotNull(pasteRefactoring);
				fPasteRefactoring= pasteRefactoring;
			}

			public static ReorgTypedSourcePasteStarter create(TypedSource[] typedSources, IJavaElement destination) {
				Assert.isNotNull(typedSources);
				Assert.isNotNull(destination);
				PasteTypedSourcesRefactoring pasteRefactoring= PasteTypedSourcesRefactoring.create(typedSources);
				if (pasteRefactoring == null)
					return null;
				if (! pasteRefactoring.setDestination(destination).isOK())
					return null;
				return new ReorgTypedSourcePasteStarter(pasteRefactoring);
			}

			public void run(Shell parent) throws InterruptedException, InvocationTargetException {
				IRunnableContext context= new ProgressMonitorDialog(parent);
				new RefactoringExecutionHelper(fPasteRefactoring, RefactoringCore.getConditionCheckingFailedSeverity(), RefactoringSaveHelper.SAVE_NOTHING, parent, context).perform(false, false);
			}
		}
		private static class PasteTypedSourcesRefactoring extends Refactoring {

			private final TypedSource[] fSources;
			private IJavaElement fDestination;

			static PasteTypedSourcesRefactoring create(TypedSource[] sources){
				if (! isAvailable(sources))
					return null;
				return new PasteTypedSourcesRefactoring(sources);
			}
			public RefactoringStatus setDestination(IJavaElement destination) {
				fDestination= destination;
				if (ReorgUtils.getCompilationUnit(destination) == null)
					return RefactoringStatus.createFatalErrorStatus(ReorgMessages.PasteAction_wrong_destination);
				if (! destination.exists())
					return RefactoringStatus.createFatalErrorStatus(ReorgMessages.PasteAction_element_doesnot_exist);
				if (! canPasteAll(destination))
					return RefactoringStatus.createFatalErrorStatus(ReorgMessages.PasteAction_invalid_destination);
				return new RefactoringStatus();
			}
			private boolean canPasteAll(IJavaElement destination) {
				for (int i= 0; i < fSources.length; i++) {
					if (! canPaste(fSources[i].getType(), destination))
						return false;
				}
				return true;
			}
			private static boolean canPaste(int elementType, IJavaElement destination) {
				IType ancestorType= getAncestorType(destination);
				if (ancestorType != null)
					return canPasteToType(elementType);
				return canPasteToCu(elementType);
			}
			private static boolean canPasteToType(int elementType) {
				return 	elementType == IJavaElement.TYPE ||
						elementType == IJavaElement.FIELD ||
						elementType == IJavaElement.INITIALIZER ||
						elementType == IJavaElement.METHOD;
			}
			private static boolean canPasteToCu(int elementType) {
				return	elementType == IJavaElement.PACKAGE_DECLARATION ||
						elementType == IJavaElement.TYPE ||
						elementType == IJavaElement.IMPORT_DECLARATION;
			}
			PasteTypedSourcesRefactoring(TypedSource[] sources){
				Assert.isNotNull(sources);
				Assert.isTrue(sources.length != 0);
				fSources= sources;
			}

			private static boolean isAvailable(TypedSource[] sources) {
				return sources != null && sources.length > 0;
			}

			@Override
			public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
				return new RefactoringStatus();
			}

			@Override
			public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException {
				RefactoringStatus result= Checks.validateModifiesFiles(
					ResourceUtil.getFiles(new ICompilationUnit[]{getDestinationCu()}), getValidationContext());
				return result;
			}

			@Override
			public Change createChange(IProgressMonitor pm) throws CoreException {
				ASTParser p= ASTParser.newParser(ASTProvider.SHARED_AST_LEVEL);
				p.setSource(getDestinationCu());
				CompilationUnit cuNode= (CompilationUnit) p.createAST(pm);
				ASTRewrite rewrite= ASTRewrite.create(cuNode.getAST());
				TypedSource source= null;
				for (int i= fSources.length - 1; i >= 0; i--) {
					source= fSources[i];
					final ASTNode destination= getDestinationNodeForSourceElement(fDestination, source.getType(), cuNode);
					if (destination != null) {
						if (destination instanceof CompilationUnit)
							insertToCu(rewrite, createNewNodeToInsertToCu(source, rewrite), (CompilationUnit) destination);
						else if (destination instanceof AbstractTypeDeclaration)
							insertToType(rewrite, createNewNodeToInsertToType(source, rewrite), (AbstractTypeDeclaration) destination);
					}
				}
				final CompilationUnitChange result= new CompilationUnitChange(ReorgMessages.PasteAction_change_name, getDestinationCu());
				try {
					ITextFileBuffer buffer= RefactoringFileBuffers.acquire(getDestinationCu());
					TextEdit rootEdit= rewrite.rewriteAST(buffer.getDocument(), fDestination.getJavaProject().getOptions(true));
					if (getDestinationCu().isWorkingCopy())
						result.setSaveMode(TextFileChange.LEAVE_DIRTY);
					TextChangeCompatibility.addTextEdit(result, ReorgMessages.PasteAction_edit_name, rootEdit);
				} finally {
					RefactoringFileBuffers.release(getDestinationCu());
				}
				return result;
			}

			private static void insertToType(ASTRewrite rewrite, ASTNode node, AbstractTypeDeclaration typeDeclaration) {
				switch (node.getNodeType()) {
					case ASTNode.ANNOTATION_TYPE_DECLARATION:
					case ASTNode.ENUM_DECLARATION:
					case ASTNode.TYPE_DECLARATION:
					case ASTNode.METHOD_DECLARATION:
					case ASTNode.FIELD_DECLARATION:
					case ASTNode.INITIALIZER:
						rewrite.getListRewrite(typeDeclaration, typeDeclaration.getBodyDeclarationsProperty()).insertAt(node, ASTNodes.getInsertionIndex((BodyDeclaration) node, typeDeclaration.bodyDeclarations()), null);
						break;
					default:
						Assert.isTrue(false, String.valueOf(node.getNodeType()));
				}
			}

			private static void insertToCu(ASTRewrite rewrite, ASTNode node, CompilationUnit cuNode) {
				switch (node.getNodeType()) {
					case ASTNode.TYPE_DECLARATION:
					case ASTNode.ENUM_DECLARATION:
					case ASTNode.ANNOTATION_TYPE_DECLARATION:
						rewrite.getListRewrite(cuNode, CompilationUnit.TYPES_PROPERTY).insertAt(node, ASTNodes.getInsertionIndex((AbstractTypeDeclaration) node, cuNode.types()), null);
						break;
					case ASTNode.IMPORT_DECLARATION:
						rewrite.getListRewrite(cuNode, CompilationUnit.IMPORTS_PROPERTY).insertLast(node, null);
						break;
					case ASTNode.PACKAGE_DECLARATION:
						// only insert if none exists
						if (cuNode.getPackage() == null)
							rewrite.set(cuNode, CompilationUnit.PACKAGE_PROPERTY, node, null);
						break;
					default:
						Assert.isTrue(false, String.valueOf(node.getNodeType()));
				}
			}

			/**
			 * @param destination the destination element
			 * @param kind the type of the source to paste
			 * @param unit the parsed CU
			 * @return an AbstractTypeDeclaration, a CompilationUnit, or null
			 * @throws JavaModelException if something fails
			 */
			private ASTNode getDestinationNodeForSourceElement(IJavaElement destination, int kind, CompilationUnit unit) throws JavaModelException {
				final IType ancestor= getAncestorType(destination);
				if (ancestor != null)
					return ASTNodeSearchUtil.getAbstractTypeDeclarationNode(ancestor, unit);
				if (kind == IJavaElement.TYPE || kind == IJavaElement.PACKAGE_DECLARATION || kind == IJavaElement.IMPORT_DECLARATION || kind == IJavaElement.IMPORT_CONTAINER)
					return unit;
				return null;
			}

			private static IType getAncestorType(IJavaElement destinationElement) {
				return destinationElement.getElementType() == IJavaElement.TYPE ? (IType)destinationElement: (IType)destinationElement.getAncestor(IJavaElement.TYPE);
			}
			private ASTNode createNewNodeToInsertToCu(TypedSource source, ASTRewrite rewrite) {
				switch(source.getType()){
					case IJavaElement.TYPE:
						return rewrite.createStringPlaceholder(source.getSource(), ASTNode.TYPE_DECLARATION);
					case IJavaElement.PACKAGE_DECLARATION:
						return rewrite.createStringPlaceholder(source.getSource(), ASTNode.PACKAGE_DECLARATION);
					case IJavaElement.IMPORT_DECLARATION:
						return rewrite.createStringPlaceholder(source.getSource(), ASTNode.IMPORT_DECLARATION);
					default: Assert.isTrue(false, String.valueOf(source.getType()));
						return null;
				}
			}

			private ASTNode createNewNodeToInsertToType(TypedSource source, ASTRewrite rewrite) {
				switch(source.getType()){
					case IJavaElement.TYPE:
						return rewrite.createStringPlaceholder(source.getSource(), ASTNode.TYPE_DECLARATION);
					case IJavaElement.METHOD:
						return rewrite.createStringPlaceholder(source.getSource(), ASTNode.METHOD_DECLARATION);
					case IJavaElement.FIELD:
						return rewrite.createStringPlaceholder(source.getSource(), ASTNode.FIELD_DECLARATION);
					case IJavaElement.INITIALIZER:
						return rewrite.createStringPlaceholder(source.getSource(), ASTNode.INITIALIZER);
					default: Assert.isTrue(false);
						return null;
				}
			}

			private ICompilationUnit getDestinationCu() {
				return ReorgUtils.getCompilationUnit(fDestination);
			}

			@Override
			public String getName() {
				return ReorgMessages.PasteAction_name;
			}
		}
    }
}

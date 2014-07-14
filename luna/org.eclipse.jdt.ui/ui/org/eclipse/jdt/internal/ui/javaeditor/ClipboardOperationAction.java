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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;

import org.eclipse.swt.SWTError;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.dnd.ByteArrayTransfer;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.RTFTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;

import org.eclipse.jface.text.IRewriteTarget;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Region;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressService;
import org.eclipse.ui.progress.IWorkbenchSiteProgressService;

import org.eclipse.ui.texteditor.IAbstractTextEditorHelpContextIds;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorExtension3;
import org.eclipse.ui.texteditor.TextEditorAction;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;

import org.eclipse.jdt.internal.corext.codemanipulation.ImportReferencesCollector;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.SharedASTProvider;

import org.eclipse.jdt.internal.ui.IJavaStatusConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaUIMessages;


/**
 * Action for cut/copy and paste with support for adding imports on paste.
 */
public final class ClipboardOperationAction extends TextEditorAction {

	public static class ClipboardData {
		private String fOriginHandle;
		private String[] fTypeImports;
		private String[] fStaticImports;

		public ClipboardData(IJavaElement origin, String[] typeImports, String[] staticImports) {
			Assert.isNotNull(origin);
			Assert.isNotNull(typeImports);
			Assert.isNotNull(staticImports);

			fTypeImports= typeImports;
			fStaticImports= staticImports;
			fOriginHandle= origin.getHandleIdentifier();
		}

		public ClipboardData(byte[] bytes) throws IOException {
			DataInputStream dataIn = new DataInputStream(new ByteArrayInputStream(bytes));
			try {
				fOriginHandle= dataIn.readUTF();
				fTypeImports= readArray(dataIn);
				fStaticImports= readArray(dataIn);
			} finally {
				dataIn.close();
			}
		}

		private static String[] readArray(DataInputStream dataIn) throws IOException {
			int count= dataIn.readInt();

			String[] array= new String[count];
			for (int i = 0; i < count; i++) {
				array[i]= dataIn.readUTF();
			}
			return array;
		}

		private static void writeArray(DataOutputStream dataOut, String[] array) throws IOException {
			dataOut.writeInt(array.length);
			for (int i = 0; i < array.length; i++) {
				dataOut.writeUTF(array[i]);
			}
		}

		public String[] getTypeImports() {
			return fTypeImports;
		}

		public String[] getStaticImports() {
			return fStaticImports;
		}

		public boolean isFromSame(IJavaElement elem) {
			return fOriginHandle.equals(elem.getHandleIdentifier());
		}

		public byte[] serialize() throws IOException {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			DataOutputStream dataOut = new DataOutputStream(out);
			try {
				dataOut.writeUTF(fOriginHandle);
				writeArray(dataOut, fTypeImports);
				writeArray(dataOut, fStaticImports);
			} finally {
				dataOut.close();
				out.close();
			}

			return out.toByteArray();
		}
	}


	private static class ClipboardTransfer extends ByteArrayTransfer {

		private static final String TYPE_NAME = "source-with-imports-transfer-format" + System.currentTimeMillis(); //$NON-NLS-1$

		private static final int TYPEID = registerType(TYPE_NAME);

		/* (non-Javadoc)
		 * @see org.eclipse.swt.dnd.Transfer#getTypeIds()
		 */
		@Override
		protected int[] getTypeIds() {
			return new int[] { TYPEID };
		}

		/* (non-Javadoc)
		 * @see org.eclipse.swt.dnd.Transfer#getTypeNames()
		 */
		@Override
		protected String[] getTypeNames() {
			return new String[] { TYPE_NAME };
		}

		/* (non-Javadoc)
		 * @see org.eclipse.swt.dnd.Transfer#javaToNative(java.lang.Object, org.eclipse.swt.dnd.TransferData)
		 */
		@Override
		protected void javaToNative(Object data, TransferData transferData) {
			if (data instanceof ClipboardData) {
				try {
					super.javaToNative(((ClipboardData) data).serialize(), transferData);
				} catch (IOException e) {
					//it's best to send nothing if there were problems
				}
			}
		}

		/* (non-Javadoc)
		 * Method declared on Transfer.
		 */
		@Override
		protected Object nativeToJava(TransferData transferData) {
			byte[] bytes = (byte[]) super.nativeToJava(transferData);
			if (bytes != null) {
				try {
					return new ClipboardData(bytes);
				} catch (IOException e) {
				}
			}
			return null;
		}

	}

	private static final ClipboardTransfer fgTransferInstance = new ClipboardTransfer();

	/** The text operation code */
	private int fOperationCode= -1;
	/** The text operation target */
	private ITextOperationTarget fOperationTarget;


	/**
	 * Creates the action.
	 * @param bundle the resource bundle
	 * @param prefix a prefix to be prepended to the various resource keys
	 *   (described in <code>ResourceAction</code> constructor), or
	 *   <code>null</code> if none
	 * @param editor the text editor
	 * @param operationCode the operation code
	 */
	public ClipboardOperationAction(ResourceBundle bundle, String prefix, ITextEditor editor, int operationCode) {
		super(bundle, prefix, editor);
		fOperationCode= operationCode;

		if (operationCode == ITextOperationTarget.CUT) {
			setHelpContextId(IAbstractTextEditorHelpContextIds.CUT_ACTION);
			setActionDefinitionId(IWorkbenchCommandConstants.EDIT_CUT);
		} else if (operationCode == ITextOperationTarget.COPY) {
			setHelpContextId(IAbstractTextEditorHelpContextIds.COPY_ACTION);
			setActionDefinitionId(IWorkbenchCommandConstants.EDIT_COPY);
		} else if (operationCode == ITextOperationTarget.PASTE) {
			setHelpContextId(IAbstractTextEditorHelpContextIds.PASTE_ACTION);
			setActionDefinitionId(IWorkbenchCommandConstants.EDIT_PASTE);
		} else {
			Assert.isTrue(false, "Invalid operation code"); //$NON-NLS-1$
		}
		update();
	}

	private boolean isReadOnlyOperation() {
		return fOperationCode == ITextOperationTarget.COPY;
	}


	/* (non-Javadoc)
	 * @see org.eclipse.jface.action.IAction#run()
	 */
	@Override
	public void run() {
		if (fOperationCode == -1 || fOperationTarget == null)
			return;

		ITextEditor editor= getTextEditor();
		if (editor == null)
			return;

		if (!isReadOnlyOperation() && !validateEditorInputState())
			return;

		BusyIndicator.showWhile(getDisplay(), new Runnable() {
			public void run() {
				internalDoOperation();
			}
		});
	}

	private Shell getShell() {
		ITextEditor editor= getTextEditor();
		if (editor != null) {
			IWorkbenchPartSite site= editor.getSite();
			Shell shell= site.getShell();
			if (shell != null && !shell.isDisposed()) {
				return shell;
			}
		}
		return null;
	}

	private Display getDisplay() {
		Shell shell= getShell();
		if (shell != null) {
			return shell.getDisplay();
		}
		return null;
	}

	/**
	 * Returns whether the Smart Insert Mode is selected.
	 * 
	 * @return <code>true</code> if the Smart Insert Mode is selected
	 * @since 3.7
	 */
	private boolean isSmartInsertMode() {
		IWorkbenchPage page= JavaPlugin.getActivePage();
		if (page != null) {
			IEditorPart part= page.getActiveEditor();
			if (part instanceof ITextEditorExtension3) {
				ITextEditorExtension3 extension= (ITextEditorExtension3)part;
				return extension.getInsertMode() == ITextEditorExtension3.SMART_INSERT;
			} else if (part != null && EditorUtility.isCompareEditorInput(part.getEditorInput())) {
				ITextEditorExtension3 extension= (ITextEditorExtension3)part.getAdapter(ITextEditorExtension3.class);
				if (extension != null)
					return extension.getInsertMode() == ITextEditorExtension3.SMART_INSERT;
			}
		}
		return false;
	}

	protected final void internalDoOperation() {
		if (PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_IMPORTS_ON_PASTE) && isSmartInsertMode()) {
			if (fOperationCode == ITextOperationTarget.PASTE) {
				doPasteWithImportsOperation();
			} else {
				doCutCopyWithImportsOperation();
			}
		} else {
			fOperationTarget.doOperation(fOperationCode);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.texteditor.IUpdate#update()
	 */
	@Override
	public void update() {
		super.update();

		if (!isReadOnlyOperation() && !canModifyEditor()) {
			setEnabled(false);
			return;
		}

		ITextEditor editor= getTextEditor();
		if (fOperationTarget == null && editor!= null && fOperationCode != -1)
			fOperationTarget= (ITextOperationTarget) editor.getAdapter(ITextOperationTarget.class);

		boolean isEnabled= (fOperationTarget != null && fOperationTarget.canDoOperation(fOperationCode));
		setEnabled(isEnabled);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.texteditor.TextEditorAction#setEditor(org.eclipse.ui.texteditor.ITextEditor)
	 */
	@Override
	public void setEditor(ITextEditor editor) {
		super.setEditor(editor);
		fOperationTarget= null;
	}


	private void doCutCopyWithImportsOperation() {
		ITextEditor editor= getTextEditor();
		ITypeRoot inputElement= JavaUI.getEditorInputTypeRoot(editor.getEditorInput());
		ISelection selection= editor.getSelectionProvider().getSelection();

		Object clipboardData= null;
		if (inputElement != null && selection instanceof ITextSelection && !selection.isEmpty()) {
			ITextSelection textSelection= (ITextSelection) selection;
			if (isNonTrivialSelection(textSelection)) {
				clipboardData= getClipboardData(inputElement, textSelection.getOffset(), textSelection.getLength());
			}
		}

		fOperationTarget.doOperation(fOperationCode);

		if (clipboardData != null) {
			/*
			 * We currently make assumptions about what the styled text widget sets,
			 * see https://bugs.eclipse.org/bugs/show_bug.cgi?id=61876
			 */
			Clipboard clipboard= new Clipboard(getDisplay());
			try {
				Object textData= clipboard.getContents(TextTransfer.getInstance());
				/*
				 * Don't add if we didn't get any text data from the clipboard, see:
				 * - https://bugs.eclipse.org/bugs/show_bug.cgi?id=70077
				 * - https://bugs.eclipse.org/bugs/show_bug.cgi?id=200743
				 */
				if (textData == null)
					return;

				ArrayList<Object> datas= new ArrayList<Object>(3);
				ArrayList<ByteArrayTransfer> transfers= new ArrayList<ByteArrayTransfer>(3);
				datas.add(textData);
				transfers.add(TextTransfer.getInstance());

				Object rtfData= clipboard.getContents(RTFTransfer.getInstance());
				if (rtfData != null) {
					datas.add(rtfData);
					transfers.add(RTFTransfer.getInstance());
				}

				datas.add(clipboardData);
				transfers.add(fgTransferInstance);

				Transfer[] dataTypes= transfers.toArray(new Transfer[transfers.size()]);
				Object[] data= datas.toArray();
				setClipboardContents(clipboard, data, dataTypes);
			} finally {
				clipboard.dispose();
			}
		}
	}

	private void setClipboardContents(Clipboard clipboard, Object[] datas, Transfer[] transfers) {
		try {
			clipboard.setContents(datas, transfers);
		} catch (SWTError e) {
			if (e.code != DND.ERROR_CANNOT_SET_CLIPBOARD) {
				throw e;
			}
			// silently fail.  see e.g. https://bugs.eclipse.org/bugs/show_bug.cgi?id=65975
		}
	}

	private boolean isNonTrivialSelection(ITextSelection selection) {
		if (selection.getLength() < 30) {
			String text= selection.getText();
			if (text != null) {
				for (int i= 0; i < text.length(); i++) {
					if (!Character.isJavaIdentifierPart(text.charAt(i))) {
						return true;
					}
				}
			}
			return false;
		}
		return true;
	}


	private ClipboardData getClipboardData(ITypeRoot inputElement, int offset, int length) {
		CompilationUnit astRoot= SharedASTProvider.getAST(inputElement, SharedASTProvider.WAIT_ACTIVE_ONLY, null);
		if (astRoot == null) {
			return null;
		}

		// do process import if selection spans over import declaration or package
		List<ImportDeclaration> list= astRoot.imports();
		if (!list.isEmpty()) {
			if (offset < ((ASTNode) list.get(list.size() - 1)).getStartPosition()) {
				return null;
			}
		} else if (astRoot.getPackage() != null) {
			if (offset < ((ASTNode) astRoot.getPackage()).getStartPosition()) {
				return null;
			}
		}

		ArrayList<SimpleName> typeImportsRefs= new ArrayList<SimpleName>();
		ArrayList<SimpleName> staticImportsRefs= new ArrayList<SimpleName>();

		ImportReferencesCollector.collect(astRoot, inputElement.getJavaProject(), new Region(offset, length), typeImportsRefs, staticImportsRefs);

		if (typeImportsRefs.isEmpty() && staticImportsRefs.isEmpty()) {
			return null;
		}

		HashSet<String> namesToImport= new HashSet<String>(typeImportsRefs.size());
		for (int i= 0; i < typeImportsRefs.size(); i++) {
			Name curr= typeImportsRefs.get(i);
			IBinding binding= curr.resolveBinding();
			if (binding != null && binding.getKind() == IBinding.TYPE) {
				ITypeBinding typeBinding= (ITypeBinding) binding;
				if (typeBinding.isArray()) {
					typeBinding= typeBinding.getElementType();
				}
				if (typeBinding.isTypeVariable() || typeBinding.isCapture() || typeBinding.isWildcardType()) { // can be removed when bug 98473 is fixed
					continue;
				}

				if (typeBinding.isMember() || typeBinding.isTopLevel()) {
					String name= Bindings.getRawQualifiedName(typeBinding);
					if (name.length() > 0) {
						namesToImport.add(name);
					}
				}
			}
		}

		HashSet<String> staticsToImport= new HashSet<String>(staticImportsRefs.size());
		for (int i= 0; i < staticImportsRefs.size(); i++) {
			Name curr= staticImportsRefs.get(i);
			IBinding binding= curr.resolveBinding();
			if (binding != null) {
				StringBuffer buf= new StringBuffer(Bindings.getImportName(binding));
				if (binding.getKind() == IBinding.METHOD) {
					buf.append("()"); //$NON-NLS-1$
				}
				staticsToImport.add(buf.toString());
			}
		}


		if (namesToImport.isEmpty() && staticsToImport.isEmpty()) {
			return null;
		}

		String[] typeImports= namesToImport.toArray(new String[namesToImport.size()]);
		String[] staticImports= staticsToImport.toArray(new String[staticsToImport.size()]);
		return new ClipboardData(inputElement, typeImports, staticImports);
	}

	private void doPasteWithImportsOperation() {
		ITextEditor editor= getTextEditor();
		IJavaElement inputElement= JavaUI.getEditorInputTypeRoot(editor.getEditorInput());

		Clipboard clipboard= new Clipboard(getDisplay());
		try {
			ClipboardData importsData= (ClipboardData)clipboard.getContents(fgTransferInstance);
			if (importsData != null && inputElement instanceof ICompilationUnit && !importsData.isFromSame(inputElement)) {
				// combine operation and adding of imports
				IRewriteTarget target= (IRewriteTarget)editor.getAdapter(IRewriteTarget.class);
				if (target != null) {
					target.beginCompoundChange();
				}
				try {
					fOperationTarget.doOperation(fOperationCode);
					addImports((ICompilationUnit)inputElement, importsData);
				} catch (CoreException e) {
					JavaPlugin.log(e);
				} finally {
					if (target != null) {
						target.endCompoundChange();
					}
				}
			} else {
				fOperationTarget.doOperation(fOperationCode);
			}
		} finally {
			clipboard.dispose();
		}
	}

	private void addImports(final ICompilationUnit unit, ClipboardData data) throws CoreException {
		final ImportRewrite rewrite= StubUtility.createImportRewrite(unit, true);
		String[] imports= data.getTypeImports();
		for (int i= 0; i < imports.length; i++) {
			rewrite.addImport(imports[i]);
		}
		String[] staticImports= data.getStaticImports();
		for (int i= 0; i < staticImports.length; i++) {
			String name= Signature.getSimpleName(staticImports[i]);
			boolean isField= !name.endsWith("()"); //$NON-NLS-1$
			if (!isField) {
				name= name.substring(0, name.length() - 2);
			}
			String qualifier= Signature.getQualifier(staticImports[i]);
			rewrite.addStaticImport(qualifier, name, isField);
		}

		try {
			getProgressService().busyCursorWhile(new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					try {
						JavaModelUtil.applyEdit(unit, rewrite.rewriteImports(monitor), false, null);
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					}
				}
			});
		} catch (InvocationTargetException e) {
			Throwable cause= e.getCause();
			if (cause instanceof CoreException)
				throw (CoreException) cause;
			throw new CoreException(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IJavaStatusConstants.INTERNAL_ERROR, JavaUIMessages.JavaPlugin_internal_error, cause));
		} catch (InterruptedException e) {
			// Canceled by the user
		}
	}

	private IProgressService getProgressService() {
		IEditorPart editor= getTextEditor();
		if (editor != null) {
			IWorkbenchPartSite site= editor.getSite();
			if (site != null)
				return (IWorkbenchSiteProgressService) editor.getSite().getAdapter(IWorkbenchSiteProgressService.class);
		}
		return PlatformUI.getWorkbench().getProgressService();
	}

}

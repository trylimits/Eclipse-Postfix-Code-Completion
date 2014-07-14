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
package org.eclipse.jdt.internal.ui.javaeditor;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.core.resources.IFile;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.jface.text.IWidgetTokenKeeper;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionGroup;

import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;

import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.util.ClassFileBytesDisassembler;
import org.eclipse.jdt.core.util.ClassFormatException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jdt.ui.actions.RefactorActionGroup;
import org.eclipse.jdt.ui.wizards.BuildPathDialogAccess;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaUIStatus;
import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.SourceAttachmentBlock;


/**
 * Java specific text editor.
 */
public class ClassFileEditor extends JavaEditor implements ClassFileDocumentProvider.InputChangeListener {


	/**
	 * A form to attach source to a class file.
	 */
	private class SourceAttachmentForm implements IPropertyChangeListener {

		private final IClassFile fFile;
		private Composite fComposite;
		private Color fBackgroundColor;
		private Color fForegroundColor;
		private Color fSeparatorColor;
		private List<Label> fBannerLabels= new ArrayList<Label>();
		private List<Label> fHeaderLabels= new ArrayList<Label>();
		private Font fFont;

		/**
		 * Creates a source attachment form for a class file.
		 *
		 * @param file the class file
		 */
		public SourceAttachmentForm(IClassFile file) {
			fFile= file;
		}

		/**
		 * Returns the package fragment root of this file.
		 *
		 * @param file the class file
		 * @return the package fragment root of the given class file
		 */
		private IPackageFragmentRoot getPackageFragmentRoot(IClassFile file) {

			IJavaElement element= file.getParent();
			while (element != null && element.getElementType() != IJavaElement.PACKAGE_FRAGMENT_ROOT)
				element= element.getParent();

			return (IPackageFragmentRoot) element;
		}

		/**
		 * Creates the control of the source attachment form.
		 *
		 * @param parent the parent composite
		 * @return the creates source attachment form
		 */
		public Control createControl(Composite parent) {

			Display display= parent.getDisplay();
			fBackgroundColor= display.getSystemColor(SWT.COLOR_LIST_BACKGROUND);
			fForegroundColor= display.getSystemColor(SWT.COLOR_LIST_FOREGROUND);
			fSeparatorColor= new Color(display, 152, 170, 203);

			JFaceResources.getFontRegistry().addListener(this);

			fComposite= createComposite(parent);
			fComposite.setLayout(new GridLayout());
			fComposite.addDisposeListener(new DisposeListener() {
				public void widgetDisposed(DisposeEvent e) {
					JFaceResources.getFontRegistry().removeListener(SourceAttachmentForm.this);
					fComposite= null;
					fSeparatorColor.dispose();
					fSeparatorColor= null;
					fBannerLabels.clear();
					fHeaderLabels.clear();
					if (fFont != null) {
						fFont.dispose();
						fFont= null;
					}
				}
			});

			createTitleLabel(fComposite, JavaEditorMessages.SourceAttachmentForm_title);
			createLabel(fComposite, null);
			createLabel(fComposite, null);

			createHeadingLabel(fComposite, JavaEditorMessages.SourceAttachmentForm_heading);

			Composite separator= createCompositeSeparator(fComposite);
			GridData data= new GridData(GridData.FILL_HORIZONTAL);
			data.heightHint= 2;
			separator.setLayoutData(data);

			try {
				IPackageFragmentRoot root= getPackageFragmentRoot(fFile);
				if (root != null) {
					createSourceAttachmentControls(fComposite, root);
				}
			} catch (JavaModelException e) {
				String title= JavaEditorMessages.SourceAttachmentForm_error_title;
				String message= JavaEditorMessages.SourceAttachmentForm_error_message;
				ExceptionHandler.handle(e, fComposite.getShell(), title, message);
			}

			separator= createCompositeSeparator(fComposite);
			data= new GridData(GridData.FILL_HORIZONTAL);
			data.heightHint= 2;
			separator.setLayoutData(data);

			fNoSourceTextWidget= createCodeView(fComposite);
			data= new GridData(GridData.FILL_BOTH);
			fNoSourceTextWidget.setLayoutData(data);

			updateCodeView(fNoSourceTextWidget, fFile);

			return fComposite;
		}

		private void createSourceAttachmentControls(Composite composite, IPackageFragmentRoot root) throws JavaModelException {
			IClasspathEntry entry;
			try {
				entry= JavaModelUtil.getClasspathEntry(root);
			} catch (JavaModelException ex) {
				if (ex.isDoesNotExist())
					entry= null;
				else
					throw ex;
			}
			IPath containerPath= null;

			if (entry == null || root.getKind() != IPackageFragmentRoot.K_BINARY) {
				createLabel(composite, Messages.format(JavaEditorMessages.SourceAttachmentForm_message_noSource, BasicElementLabels.getFileName( fFile)));
				return;
			}

			IJavaProject jproject= root.getJavaProject();
			boolean canEditEncoding= true;
			if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
				containerPath= entry.getPath();
				ClasspathContainerInitializer initializer= JavaCore.getClasspathContainerInitializer(containerPath.segment(0));
				IClasspathContainer container= JavaCore.getClasspathContainer(containerPath, jproject);
				if (initializer == null || container == null) {
					createLabel(composite, Messages.format(JavaEditorMessages.ClassFileEditor_SourceAttachmentForm_cannotconfigure, BasicElementLabels.getPathLabel(containerPath, false)));
					return;
				}
				String containerName= container.getDescription();
				IStatus status= initializer.getSourceAttachmentStatus(containerPath, jproject);
				if (status.getCode() == ClasspathContainerInitializer.ATTRIBUTE_NOT_SUPPORTED) {
					createLabel(composite, Messages.format(JavaEditorMessages.ClassFileEditor_SourceAttachmentForm_notsupported, containerName));
					return;
				}
				if (status.getCode() == ClasspathContainerInitializer.ATTRIBUTE_READ_ONLY) {
					createLabel(composite, Messages.format(JavaEditorMessages.ClassFileEditor_SourceAttachmentForm_readonly, containerName));
					return;
				}
				IStatus attributeStatus= initializer.getAttributeStatus(containerPath, jproject, IClasspathAttribute.SOURCE_ATTACHMENT_ENCODING);
				canEditEncoding= !(attributeStatus.getCode() == ClasspathContainerInitializer.ATTRIBUTE_NOT_SUPPORTED || attributeStatus.getCode() == ClasspathContainerInitializer.ATTRIBUTE_READ_ONLY);
				entry= JavaModelUtil.findEntryInContainer(container, root.getPath());
				Assert.isNotNull(entry);
			}


			Button button;

			IPath path= entry.getSourceAttachmentPath();
			if (path == null || path.isEmpty()) {
				String rootLabel= JavaElementLabels.getElementLabel(root, JavaElementLabels.ALL_DEFAULT);
				createLabel(composite, Messages.format(JavaEditorMessages.SourceAttachmentForm_message_noSourceAttachment, rootLabel));
				createLabel(composite, JavaEditorMessages.SourceAttachmentForm_message_pressButtonToAttach);
				createLabel(composite, null);

				button= createButton(composite, JavaEditorMessages.SourceAttachmentForm_button_attachSource);

			} else {
				createLabel(composite, Messages.format(JavaEditorMessages.SourceAttachmentForm_message_noSourceInAttachment, BasicElementLabels.getFileName(fFile)));
				createLabel(composite, JavaEditorMessages.SourceAttachmentForm_message_pressButtonToChange);
				createLabel(composite, null);

				button= createButton(composite, JavaEditorMessages.SourceAttachmentForm_button_changeAttachedSource);
			}

			button.addSelectionListener(getButtonListener(entry, containerPath, jproject, canEditEncoding));
		}

		private SelectionListener getButtonListener(final IClasspathEntry entry, final IPath containerPath, final IJavaProject jproject, final boolean canEditEncoding) {
			return new SelectionListener() {
				public void widgetSelected(SelectionEvent event) {
					Shell shell= getSite().getShell();
					try {
						IClasspathEntry result= BuildPathDialogAccess.configureSourceAttachment(shell, entry, canEditEncoding);
						if (result != null) {
							applySourceAttachment(shell, result, jproject, containerPath, entry.getReferencingEntry() != null);
							verifyInput(getEditorInput());
						}
					} catch (CoreException e) {
						String title= JavaEditorMessages.SourceAttachmentForm_error_title;
						String message= JavaEditorMessages.SourceAttachmentForm_error_message;
						ExceptionHandler.handle(e, shell, title, message);
					}
				}

				public void widgetDefaultSelected(SelectionEvent e) {}
			};
		}

		protected void applySourceAttachment(Shell shell, IClasspathEntry newEntry, IJavaProject project, IPath containerPath, boolean isReferencedEntry) {
			try {
				IRunnableWithProgress runnable= SourceAttachmentBlock.getRunnable(shell, newEntry, project, containerPath, isReferencedEntry);
				PlatformUI.getWorkbench().getProgressService().run(true, true, runnable);

			} catch (InvocationTargetException e) {
				String title= JavaEditorMessages.SourceAttachmentForm_attach_error_title;
				String message= JavaEditorMessages.SourceAttachmentForm_attach_error_message;
				ExceptionHandler.handle(e, shell, title, message);

			} catch (InterruptedException e) {
				// cancelled
			}
		}

		/*
		 * @see IPropertyChangeListener#propertyChange(PropertyChangeEvent)
		 */
		public void propertyChange(PropertyChangeEvent event) {

			for (Iterator<Label> iterator = fBannerLabels.iterator(); iterator.hasNext();) {
				Label label = iterator.next();
				label.setFont(JFaceResources.getBannerFont());
			}

			for (Iterator<Label> iterator = fHeaderLabels.iterator(); iterator.hasNext();) {
				Label label = iterator.next();
				label.setFont(JFaceResources.getHeaderFont());
			}

			fComposite.layout(true);
			fComposite.redraw();
		}

		// --- copied from org.eclipse.update.ui.forms.internal.FormWidgetFactory

		private Composite createComposite(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			composite.setBackground(fBackgroundColor);
			//		composite.addMouseListener(new MouseAdapter() {
			//			public void mousePressed(MouseEvent e) {
			//				((Control) e.widget).setFocus();
			//			}
			//		});
			return composite;
		}

		private Composite createCompositeSeparator(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			composite.setBackground(fSeparatorColor);
			return composite;
		}

		private StyledText createCodeView(Composite parent) {
			int styles= SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI | SWT.FULL_SELECTION;
			StyledText styledText= new StyledText(parent, styles);
			styledText.setBackground(fBackgroundColor);
			styledText.setForeground(fForegroundColor);
			styledText.setEditable(false);
			return styledText;
		}

		private Label createLabel(Composite parent, String text) {
			Label label= new Label(parent, SWT.WRAP);
			if (text != null)
				label.setText(text);
			label.setBackground(fBackgroundColor);
			label.setForeground(fForegroundColor);
			GridData gd= new GridData(SWT.FILL, SWT.FILL, true, false);
			label.setLayoutData(gd);
			return label;
		}

		private Label createTitleLabel(Composite parent, String text) {
			Label label = new Label(parent, SWT.NONE);
			if (text != null)
				label.setText(text);
			label.setBackground(fBackgroundColor);
			label.setForeground(fForegroundColor);
			label.setFont(JFaceResources.getHeaderFont());
			fHeaderLabels.add(label);
			return label;
		}

		private Label createHeadingLabel(Composite parent, String text) {
			Label label = new Label(parent, SWT.NONE);
			if (text != null)
				label.setText(text);
			label.setBackground(fBackgroundColor);
			label.setForeground(fForegroundColor);
			label.setFont(JFaceResources.getBannerFont());
			fBannerLabels.add(label);
			return label;
		}

		private Button createButton(Composite parent, String text) {
			Button button = new Button(parent, SWT.FLAT);
			button.setBackground(fBackgroundColor);
			button.setForeground(fForegroundColor);
			if (text != null)
				button.setText(text);
			return button;
		}

		private void updateCodeView(StyledText styledText, IClassFile classFile) {
			String content= null;
			ClassFileBytesDisassembler disassembler= ToolFactory.createDefaultClassFileBytesDisassembler();
			try {
				content= disassembler.disassemble(classFile.getBytes(), "\n", ClassFileBytesDisassembler.DETAILED); //$NON-NLS-1$
			} catch (JavaModelException ex) {
				JavaPlugin.log(ex.getStatus());
			} catch (ClassFormatException ex) {
				JavaPlugin.log(ex);
			}
			styledText.setText(content == null ? "" : content); //$NON-NLS-1$
		}
	}

	/**
	 *  Updater that takes care of minimizing changes of the editor input.
	 */
	private class InputUpdater implements Runnable {

		/** Has the runnable already been posted? */
		private boolean fPosted= false;
		/** Editor input */
		private IClassFileEditorInput fClassFileEditorInput;


		public InputUpdater() {
		}

		/*
		 * @see Runnable#run()
		 */
		public void run() {

			IClassFileEditorInput input;
			synchronized (this) {
				input= fClassFileEditorInput;
			}

			try {

				if (getSourceViewer() != null)
					setInput(input);

			} finally {
				synchronized (this) {
					fPosted= false;
				}
			}
		}

		/**
		 * Posts this runnable into the event queue if not already there.
		 *
		 * @param input the input to be set when executed
		 */
		public void post(IClassFileEditorInput input) {

			synchronized(this) {
				if (fPosted) {
					if (isEqualInput(input, fClassFileEditorInput))
						fClassFileEditorInput= input;
					return;
				}
			}

			if (isEqualInput(input, getEditorInput())) {
				ISourceViewer viewer= getSourceViewer();
				if (viewer != null) {
					StyledText textWidget= viewer.getTextWidget();
					if (textWidget != null && !textWidget.isDisposed()) {
						synchronized (this) {
							fPosted= true;
							fClassFileEditorInput= input;
						}
						textWidget.getDisplay().asyncExec(this);
					}
				}
			}
		}

		private boolean isEqualInput(IEditorInput input1, IEditorInput input2) {
			return input1 != null && input1.equals(input2);
		}
	}


	private StackLayout fStackLayout;
	private Composite fParent;

	private Composite fViewerComposite;
	private Control fSourceAttachmentForm;

	private CompositeActionGroup fContextMenuGroup;

	private InputUpdater fInputUpdater= new InputUpdater();

	/**
	 * The copy action used when there's attached source.
	 * @since 3.3
	 */
	private IAction fSourceCopyAction;
	/**
	 * The Select All action used when there's attached source.
	 * @since 3.3
	 */
	private IAction fSelectAllAction;

	/**
	 * StyledText widget used to show the disassembled code.
	 * if there's no source.
	 *
	 * @since 3.3
	 */
	private StyledText fNoSourceTextWidget;


	/**
	 * Default constructor.
	 */
	public ClassFileEditor() {
		setDocumentProvider(JavaPlugin.getDefault().getClassFileDocumentProvider());
		setEditorContextMenuId("#ClassFileEditorContext"); //$NON-NLS-1$
		setRulerContextMenuId("#ClassFileRulerContext"); //$NON-NLS-1$
		setOutlinerContextMenuId("#ClassFileOutlinerContext"); //$NON-NLS-1$
		// don't set help contextId, we install our own help context
	}

	/*
	 * @see AbstractTextEditor#createActions()
	 */
	@Override
	protected void createActions() {
		super.createActions();

		setAction(ITextEditorActionConstants.SAVE, null);
		setAction(ITextEditorActionConstants.REVERT_TO_SAVED, null);

		fSourceCopyAction= getAction(ITextEditorActionConstants.COPY);
		fSelectAllAction= getAction(ITextEditorActionConstants.SELECT_ALL);

		final ActionGroup group= new RefactorActionGroup(this, ITextEditorActionConstants.GROUP_EDIT, true);
		fActionGroups.addGroup(group);
		fContextMenuGroup= new CompositeActionGroup(new ActionGroup[] {group});

		/*
		 * 1GF82PL: ITPJUI:ALL - Need to be able to add bookmark to class file
		 *
		 *  // replace default action with class file specific ones
		 *
		 *	setAction(ITextEditorActionConstants.BOOKMARK, new AddClassFileMarkerAction("AddBookmark.", this, IMarker.BOOKMARK, true)); //$NON-NLS-1$
		 *	setAction(ITextEditorActionConstants.ADD_TASK, new AddClassFileMarkerAction("AddTask.", this, IMarker.TASK, false)); //$NON-NLS-1$
		 *	setAction(ITextEditorActionConstants.RULER_MANAGE_BOOKMARKS, new ClassFileMarkerRulerAction("ManageBookmarks.", getVerticalRuler(), this, IMarker.BOOKMARK, true)); //$NON-NLS-1$
		 *	setAction(ITextEditorActionConstants.RULER_MANAGE_TASKS, new ClassFileMarkerRulerAction("ManageTasks.", getVerticalRuler(), this, IMarker.TASK, true)); //$NON-NLS-1$
		 */
	}

	/*
	 * @see AbstractTextEditor#editorContextMenuAboutToShow(IMenuManager)
	 */
	@Override
	public void editorContextMenuAboutToShow(IMenuManager menu) {
		super.editorContextMenuAboutToShow(menu);

		ActionContext context= new ActionContext(getSelectionProvider().getSelection());
		fContextMenuGroup.setContext(context);
		fContextMenuGroup.fillContextMenu(menu);
		fContextMenuGroup.setContext(null);
	}

	/*
	 * @see JavaEditor#getElementAt(int)
	 */
	@Override
	protected IJavaElement getElementAt(int offset) {
		if (getEditorInput() instanceof IClassFileEditorInput) {
			try {
				IClassFileEditorInput input= (IClassFileEditorInput) getEditorInput();
				return input.getClassFile().getElementAt(offset);
			} catch (JavaModelException x) {
			}
		}
		return null;
	}

	/*
	 * @see JavaEditor#getCorrespondingElement(IJavaElement)
	 */
	@Override
	protected IJavaElement getCorrespondingElement(IJavaElement element) {
		if (getEditorInput() instanceof IClassFileEditorInput) {
			IClassFileEditorInput input= (IClassFileEditorInput) getEditorInput();
			IJavaElement parent= element.getAncestor(IJavaElement.CLASS_FILE);
			if (input.getClassFile().equals(parent))
				return element;
		}
		return null;
	}

	/*
	 * 1GEPKT5: ITPJUI:Linux - Source in editor for external classes is editable
	 * Removed methods isSaveOnClosedNeeded and isDirty.
	 * Added method isEditable.
	 */
	/*
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#isEditable()
	 */
	@Override
	public boolean isEditable() {
		return false;
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#isEditorInputReadOnly()
	 * @since 3.2
	 */
	@Override
	public boolean isEditorInputReadOnly() {
		return true;
	}

	/**
	 * Translates the given editor input into an <code>ExternalClassFileEditorInput</code>
	 * if it is a file editor input representing an external class file.
	 *
	 * @param input the editor input to be transformed if necessary
	 * @return the transformed editor input
	 */
	protected IEditorInput transformEditorInput(IEditorInput input) {

		if (input instanceof IFileEditorInput) {
			IFile file= ((IFileEditorInput) input).getFile();
			IClassFileEditorInput classFileInput= new ExternalClassFileEditorInput(file);
			if (classFileInput.getClassFile() != null)
				input= classFileInput;
		}

		return input;
	}

	/*
	 * @see AbstractTextEditor#doSetInput(IEditorInput)
	 */
	@Override
	protected void doSetInput(IEditorInput input) throws CoreException {
		uninstallOccurrencesFinder();

		input= transformEditorInput(input);
		if (!(input instanceof IClassFileEditorInput)) {
			String inputClassName= input != null ? input.getClass().getName() : "null"; //$NON-NLS-1$
			String message= Messages.format(JavaEditorMessages.ClassFileEditor_error_invalid_input_message, inputClassName);
			throw new CoreException(JavaUIStatus.createError(
					IJavaModelStatusConstants.INVALID_RESOURCE_TYPE,
					message,
					null));
		}

		JavaModelException e= probeInputForSource(input);
		if (e != null) {
			IClassFileEditorInput classFileEditorInput= (IClassFileEditorInput) input;
			IClassFile file= classFileEditorInput.getClassFile();
			IJavaProject javaProject= file.getJavaProject();
			if (!javaProject.exists() || !javaProject.isOnClasspath(file)) {
				throw new CoreException(JavaUIStatus.createError(
						IJavaModelStatusConstants.INVALID_RESOURCE,
						JavaEditorMessages.ClassFileEditor_error_classfile_not_on_classpath,
						null));
			} else {
				throw e;
			}
		}

		IDocumentProvider documentProvider= getDocumentProvider();
		if (documentProvider instanceof ClassFileDocumentProvider)
			((ClassFileDocumentProvider) documentProvider).removeInputChangeListener(this);

		super.doSetInput(input);

		documentProvider= getDocumentProvider();
		if (documentProvider instanceof ClassFileDocumentProvider)
			((ClassFileDocumentProvider) documentProvider).addInputChangeListener(this);

		verifyInput(getEditorInput());

		JavaPlugin.getDefault().getASTProvider().activeJavaEditorChanged(this);

		if (fSemanticManager != null)
			installSemanticHighlighting();

	}

	/*
	 * @see org.eclipse.jdt.internal.ui.javaeditor.JavaEditor#installSemanticHighlighting()
	 * @since 3.7
	 */
	@Override
	protected void installSemanticHighlighting() {
		super.installSemanticHighlighting();
		Job job= new Job(JavaEditorMessages.OverrideIndicatorManager_intallJob) {
			/*
			 * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
			 * @since 3.0
			 */
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				CompilationUnit ast= SharedASTProvider.getAST(getInputJavaElement(), SharedASTProvider.WAIT_YES, null);
				if (fOverrideIndicatorManager != null)
					fOverrideIndicatorManager.reconciled(ast, true, monitor);
				if (fSemanticManager != null) {
					SemanticHighlightingReconciler reconciler= fSemanticManager.getReconciler();
					if (reconciler != null)
						reconciler.reconciled(ast, false, monitor);
				}
				if (isMarkingOccurrences())
					installOccurrencesFinder(false);
				return Status.OK_STATUS;
			}
		};
		job.setPriority(Job.DECORATE);
		job.setSystem(true);
		job.schedule();
	}

	/*
	 * @see IWorkbenchPart#createPartControl(Composite)
	 */
	@Override
	public void createPartControl(Composite parent) {

		fParent= new Composite(parent, SWT.NONE);
		fStackLayout= new StackLayout();
		fParent.setLayout(fStackLayout);

		fViewerComposite= new Composite(fParent, SWT.NONE);
		fViewerComposite.setLayout(new FillLayout());

		super.createPartControl(fViewerComposite);

		fStackLayout.topControl= fViewerComposite;
		fParent.layout();

		try {
			verifyInput(getEditorInput());
		} catch (CoreException e) {
			String title= JavaEditorMessages.ClassFileEditor_error_title;
			String message= JavaEditorMessages.ClassFileEditor_error_message;
			ExceptionHandler.handle(e, fParent.getShell(), title, message);
		}
	}

	private JavaModelException probeInputForSource(IEditorInput input) {
		if (input == null)
			return null;

		IClassFileEditorInput classFileEditorInput= (IClassFileEditorInput) input;
		IClassFile file= classFileEditorInput.getClassFile();

		try {
			file.getSourceRange();
		} catch (JavaModelException e) {
			return e;
		}

		return null;
	}

	/**
	 * Checks if the class file input has no source attached. If so, a source attachment form is shown.
	 *
	 * @param input the editor input
	 * @throws JavaModelException if an exception occurs while accessing its corresponding resource
	 */
	private void verifyInput(IEditorInput input) throws JavaModelException {

		if (fParent == null || input == null)
			return;

		IClassFileEditorInput classFileEditorInput= (IClassFileEditorInput) input;
		IClassFile file= classFileEditorInput.getClassFile();

		IAction copyQualifiedName= getAction(IJavaEditorActionConstants.COPY_QUALIFIED_NAME);

		boolean wasUsingSourceCopyAction= fSourceCopyAction == getAction(ITextEditorActionConstants.COPY);

		// show source attachment form if no source found
		if (file.getSourceRange() == null) {

			// dispose old source attachment form
			if (fSourceAttachmentForm != null)
				fSourceAttachmentForm.dispose();

			SourceAttachmentForm form= new SourceAttachmentForm(file);
			fSourceAttachmentForm= form.createControl(fParent);

			fStackLayout.topControl= fSourceAttachmentForm;
			fParent.layout();

			if (fNoSourceTextWidget != null) {
				// Copy action for the no attached source case
				final IAction copyAction= new Action() {
					@Override
					public void run() {
						fNoSourceTextWidget.copy();
					}
				};
				copyAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_COPY);
				setAction(ITextEditorActionConstants.COPY, copyAction);
				copyAction.setEnabled(fNoSourceTextWidget.getSelectionText().length() > 0);
				fNoSourceTextWidget.addSelectionListener(new SelectionListener() {
					public void widgetSelected(SelectionEvent e) {
						copyAction.setEnabled(fNoSourceTextWidget.getSelectionText().length() > 0);
					}
					public void widgetDefaultSelected(SelectionEvent e) {
					}
				});

				// Select All action for the no attached source case
				final IAction selectAllAction= new Action() {
					@Override
					public void run() {
						fNoSourceTextWidget.selectAll();
						copyAction.setEnabled(true);
					}
				};
				selectAllAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_SELECT_ALL);
				setAction(ITextEditorActionConstants.SELECT_ALL, selectAllAction);
				copyAction.setEnabled(fNoSourceTextWidget.getSelectionText().length() > 0);
				copyQualifiedName.setEnabled(false);


			}

		} else { // show source viewer

			if (fSourceAttachmentForm != null) {
				fSourceAttachmentForm.dispose();
				fSourceAttachmentForm= null;

				fStackLayout.topControl= fViewerComposite;
				fParent.layout();
			}

			setAction(ITextEditorActionConstants.COPY, fSourceCopyAction);
			setAction(ITextEditorActionConstants.SELECT_ALL, fSelectAllAction);
			copyQualifiedName.setEnabled(true);

		}

		IAction currentCopyAction= getAction(ITextEditorActionConstants.COPY);
		boolean isUsingSourceCopyAction=  fSourceCopyAction == currentCopyAction;
		if (wasUsingSourceCopyAction != isUsingSourceCopyAction) {
			IActionBars actionBars= getEditorSite().getActionBars();

			if (isUsingSourceCopyAction) {
				createNavigationActions();
			} else {
				for (int i= 0; i < ACTION_MAP.length; i++) {
					IdMapEntry entry= ACTION_MAP[i];
					actionBars.setGlobalActionHandler(entry.getActionId(), null);
					setAction(entry.getActionId(), null);
				}
			}

			actionBars.setGlobalActionHandler(ITextEditorActionConstants.COPY, currentCopyAction);
			actionBars.setGlobalActionHandler(ITextEditorActionConstants.SELECT_ALL, getAction(ITextEditorActionConstants.SELECT_ALL));
			actionBars.updateActionBars();
		}

	}

	/*
	 * @see ClassFileDocumentProvider.InputChangeListener#inputChanged(IClassFileEditorInput)
	 */
	public void inputChanged(IClassFileEditorInput input) {
		fInputUpdater.post(input);
	}

	/*
	 * @see JavaEditor#createJavaSourceViewer(Composite, IVerticalRuler, int)
	 */
	protected ISourceViewer createJavaSourceViewer(Composite parent, IVerticalRuler ruler, int styles, IPreferenceStore store) {
		return new JavaSourceViewer(parent, ruler, null, false, styles, store) {

			@Override
			public boolean requestWidgetToken(IWidgetTokenKeeper requester) {
				if (PlatformUI.getWorkbench().getHelpSystem().isContextHelpDisplayed())
					return false;
				return super.requestWidgetToken(requester);
			}

			@Override
			public boolean requestWidgetToken(IWidgetTokenKeeper requester, int priority) {
				if (PlatformUI.getWorkbench().getHelpSystem().isContextHelpDisplayed())
					return false;
				return super.requestWidgetToken(requester, priority);
			}
		};
	}

	/*
	 * @see org.eclipse.ui.IWorkbenchPart#dispose()
	 */
	@Override
	public void dispose() {
		// http://bugs.eclipse.org/bugs/show_bug.cgi?id=18510
		IDocumentProvider documentProvider= getDocumentProvider();
		if (documentProvider instanceof ClassFileDocumentProvider)
			((ClassFileDocumentProvider) documentProvider).removeInputChangeListener(this);
		super.dispose();
	}

	/*
	 * @see org.eclipse.ui.IWorkbenchPart#setFocus()
	 */
	@Override
	public void setFocus() {
		super.setFocus();

		if (fSourceAttachmentForm != null && !fSourceAttachmentForm.isDisposed())
			fSourceAttachmentForm.setFocus();
	}

}

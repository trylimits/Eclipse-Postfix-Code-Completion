/*******************************************************************************
 * Copyright (c) 2008, 2011 Mateusz Matela and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Mateusz Matela <mateusz.matela@gmail.com> - [code manipulation] [dcr] toString() builder wizard - https://bugs.eclipse.org/bugs/show_bug.cgi?id=26070
 *     Mateusz Matela <mateusz.matela@gmail.com> - [toString] Template edit dialog has usability issues - https://bugs.eclipse.org/bugs/show_bug.cgi?id=267916
 *     Mateusz Matela <mateusz.matela@gmail.com> - [toString] finish toString() builder wizard - https://bugs.eclipse.org/bugs/show_bug.cgi?id=267710
 *     Mateusz Matela <mateusz.matela@gmail.com> - [toString] toString() generator: Fields in declaration order - https://bugs.eclipse.org/bugs/show_bug.cgi?id=279924
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.dialogs;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.ibm.icu.text.Collator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalProvider;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.dialogs.SelectionDialog;
import org.eclipse.ui.fieldassist.ContentAssistCommandAdapter;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.GenerateToStringOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.ToStringGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.ToStringGenerationSettings.CustomBuilderSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.ToStringTemplateParser;
import org.eclipse.jdt.internal.corext.util.JavaConventionsUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.IJavaElementSearchConstants;
import org.eclipse.jdt.ui.JavaElementImageDescriptor;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.dialogs.TypeSelectionExtension;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.JavaUIMessages;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.viewsupport.BindingLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;

/**
 * Dialog for the generate toString() action.
 * 
 * @since 3.5
 */
public class GenerateToStringDialog extends SourceActionDialog {

	private static class GenerateToStringContentProvider implements ITreeContentProvider {

		private static final Object[] EMPTY= new Object[0];

		private IVariableBinding[] fFields;

		private IMethodBinding[] fMethods;

		private IVariableBinding[] fInheritedFields;

		private IMethodBinding[] fInheritedMethods;

		private String[] mainNodes;

		private static final String fieldsNode= JavaUIMessages.GenerateToStringDialog_fields_node;

		private static final String methodsNode= JavaUIMessages.GenerateToStringDialog_methods_node;

		private static final String inheritedFieldsNode= JavaUIMessages.GenerateToStringDialog_inherited_fields_node;

		private static final String inheritedMethodsNode= JavaUIMessages.GenerateToStringDialog_inherited_methods_node;

		public GenerateToStringContentProvider(IVariableBinding[] fields, IVariableBinding[] inheritedFields, IMethodBinding[] methods, IMethodBinding[] inheritedMethods) {
			ArrayList<String> nodes= new ArrayList<String>();
			fFields= fields.clone();
			if (fFields.length > 0)
				nodes.add(fieldsNode);
			fInheritedFields= inheritedFields.clone();
			if (fInheritedFields.length > 0)
				nodes.add(inheritedFieldsNode);
			fMethods= methods.clone();
			if (fMethods.length > 0)
				nodes.add(methodsNode);
			fInheritedMethods= inheritedMethods.clone();
			if (fInheritedMethods.length > 0)
				nodes.add(inheritedMethodsNode);
			mainNodes= nodes.toArray(new String[nodes.size()]);
		}

		private int getElementPosition(Object element, Object[] array) {
			for (int i= 0; i < array.length; i++) {
				if (array[i].equals(element)) {
					return i;
				}
			}
			return -1;
		}

		private Object[] getContainingArray(Object element) {
			if (element instanceof String)
				return mainNodes;
			if (element instanceof IVariableBinding) {
				if (getElementPosition(element, fFields) >= 0)
					return fFields;
				if (getElementPosition(element, fInheritedFields) >= 0)
					return fInheritedFields;
			}
			if (element instanceof IMethodBinding) {
				if (getElementPosition(element, fMethods) >= 0)
					return fMethods;
				if (getElementPosition(element, fInheritedMethods) >= 0)
					return fInheritedMethods;
			}
			return EMPTY;
		}

		public boolean canMoveDown(Object element) {
			Object[] array= getContainingArray(element);
			int position= getElementPosition(element, array);
			return position != -1 && position != array.length - 1;
		}

		public boolean canMoveUp(Object element) {
			return getElementPosition(element, getContainingArray(element)) > 0;
		}

		public void down(Object element, CheckboxTreeViewer tree) {
			move(element, tree, 1);
		}

		public void up(Object element, CheckboxTreeViewer tree) {
			move(element, tree, -1);
		}

		private void move(Object element, CheckboxTreeViewer tree, int direction) {
			Object[] array= getContainingArray(element);
			int position= getElementPosition(element, array);
			Object temp= array[position];
			array[position]= array[position + direction];
			array[position + direction]= temp;
			tree.setSelection(new StructuredSelection(element));
			tree.refresh();
		}

		public void sort() {
			Comparator<IBinding> comparator= new Comparator<IBinding>() {
				Collator collator= Collator.getInstance();
				public int compare(IBinding b1, IBinding b2) {
					return collator.compare(b1.getName(), b2.getName());
				}
			};
			Arrays.sort(fFields, comparator);
			Arrays.sort(fMethods, comparator);
			Arrays.sort(fInheritedFields, comparator);
			Arrays.sort(fInheritedMethods, comparator);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.jface.viewers.ITreeContentProvider#getChildren(java.lang.Object)
		 */
		public Object[] getChildren(Object parentElement) {
			if (parentElement == fieldsNode)
				return fFields;
			if (parentElement == inheritedFieldsNode)
				return fInheritedFields;
			if (parentElement == methodsNode)
				return fMethods;
			if (parentElement == inheritedMethodsNode)
				return fInheritedMethods;
			return EMPTY;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.jface.viewers.ITreeContentProvider#getParent(java.lang.Object)
		 */
		public Object getParent(Object element) {
			Object[] array= getContainingArray(element);
			if (array == fFields)
				return fieldsNode;
			if (array == fInheritedFields)
				return inheritedFieldsNode;
			if (array == fMethods)
				return methodsNode;
			if (array == fInheritedMethods)
				return inheritedMethodsNode;
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.jface.viewers.ITreeContentProvider#hasChildren(java.lang.Object)
		 */
		public boolean hasChildren(Object element) {
			if (element instanceof String)
				return true;
			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
		 */
		public Object[] getElements(Object inputElement) {
			return mainNodes;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
		 */
		public void dispose() {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer,
		 * java.lang.Object, java.lang.Object)
		 */
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}
	}

	private static class GenerateToStringLabelProvider extends BindingLabelProvider {
		@Override
		public Image getImage(Object element) {
			ImageDescriptor descriptor= null;
			if (element == GenerateToStringContentProvider.fieldsNode || element == GenerateToStringContentProvider.inheritedFieldsNode)
				descriptor= JavaPluginImages.DESC_FIELD_PUBLIC;
			if (element == GenerateToStringContentProvider.methodsNode || element == GenerateToStringContentProvider.inheritedMethodsNode)
				descriptor= JavaPluginImages.DESC_MISC_PUBLIC;
			if (descriptor != null) {
				descriptor= new JavaElementImageDescriptor(descriptor, 0, JavaElementImageProvider.BIG_SIZE);
				return JavaPlugin.getImageDescriptorRegistry().get(descriptor);
			}
			return super.getImage(element);
		}
	}

	private class GenerateToStringValidator implements ISelectionStatusValidator {

		private int fNumFields;

		private int fNumMethods;

		private CustomBuilderValidator fValidator;

		public GenerateToStringValidator(int fields, int methods) {
			fNumFields= fields;
			fNumMethods= methods;
		}


		public IStatus validate(Object[] selection) {
			if (getGenerationSettings().toStringStyle == GenerateToStringOperation.CUSTOM_BUILDER) {
				if (fValidator == null)
					fValidator= new CustomBuilderValidator(getType().getJavaProject());
				IStatus status= fValidator.revalidateAll(getGenerationSettings().getCustomBuilderSettings());
				if (!status.isOK())
					return new StatusInfo(IStatus.ERROR, JavaUIMessages.GenerateToStringDialog_selectioninfo_customBuilderConfigError);
			}

			int countFields= 0, countMethods= 0;
			for (int index= 0; index < selection.length; index++) {
				if (selection[index] instanceof IVariableBinding)
					countFields++;
				else if (selection[index] instanceof IMethodBinding)
					countMethods++;
			}

			return new StatusInfo(IStatus.INFO, Messages.format(JavaUIMessages.GenerateToStringDialog_selectioninfo_more, new String[] { String.valueOf(countFields), String.valueOf(fNumFields),
					String.valueOf(countMethods), String.valueOf(fNumMethods) }));
		}
	}

	private class ToStringTemplatesDialog extends StatusDialog {

		private class TemplateEditionDialog extends StatusDialog {
			/**
			 * Template number, -1 for new template.
			 */
			private final int templateNumber;

			/**
			 * Initial template name, can be <code>null</code>.
			 */
			private final String fInitialTemplateName;

			private Text templateName;

			private Text template;

			private String resultTemplateName;

			private String resultTemplate;

			private StatusInfo nameValidationStatus= new StatusInfo();


			public TemplateEditionDialog(Shell parent, int templateNumber) {
				super(parent);
				this.templateNumber= templateNumber;
				fInitialTemplateName= templateNumber < 0 ? null : templateNames.get(templateNumber);
				setHelpAvailable(false);
			}

			@Override
			protected boolean isResizable() {
				return true;
			}

			@Override
			protected Control createDialogArea(Composite parent) {
				getShell().setText(templateNumber >= 0 ? JavaUIMessages.GenerateToStringDialog_templateEdition_WindowTitle : JavaUIMessages.GenerateToStringDialog_templateEdition_NewWindowTitle);

				Composite composite= (Composite)super.createDialogArea(parent);

				GridLayout layout= (GridLayout)composite.getLayout();
				layout.numColumns= 2;
				layout.horizontalSpacing= 8;

				Label label= new Label(composite, SWT.LEFT);
				label.setText(JavaUIMessages.GenerateToStringDialog_template_name);
				label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

				templateName= new Text(composite, SWT.BORDER | SWT.SINGLE);
				templateName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

				label= new Label(composite, SWT.LEFT);
				label.setText(JavaUIMessages.GenerateToStringDialog_template_content);
				label.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));

				template= new Text(composite, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
				GridData gridData= new GridData(SWT.FILL, SWT.FILL, true, true);
				gridData.heightHint= 80;
				gridData.widthHint= 480;
				template.setLayoutData(gridData);
				new ContentAssistCommandAdapter(template, new TextContentAdapter(), new ToStringTemplateProposalProvider(), null, new char[] { '$' }, true).setPropagateKeys(false);

				if (templateNumber >= 0) {
					templateName.setText(fInitialTemplateName);
					template.setText(templates.get(templateNumber));
				} else {
					templateName.setText(createNewTemplateName());
					template.setText(ToStringTemplateParser.DEFAULT_TEMPLATE);
				}
				templateName.setSelection(0, templateName.getText().length());

				templateName.addModifyListener(new ModifyListener() {
					public void modifyText(ModifyEvent e) {
						validate(templateName.getText());
					}
				});

				//Ctrl+Enter should execute the default button, workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=145959
				template.addTraverseListener(new TraverseListener() {
					public void keyTraversed(TraverseEvent e) {
						if (e.detail == SWT.TRAVERSE_RETURN && (e.stateMask & SWT.MODIFIER_MASK) != 0) {
							buttonPressed(((Integer)getShell().getDefaultButton().getData()).intValue());
						}
					}
				});

				return composite;
			}

			private void validate(String newName) {
				if (newName.length() == 0) {
					nameValidationStatus.setError(JavaUIMessages.GenerateToStringDialog_templateEdition_TemplateNameEmptyErrorMessage);
				} else if (!newName.equals(fInitialTemplateName) && templateNames.contains(newName)) {
					nameValidationStatus.setError(JavaUIMessages.GenerateToStringDialog_templateEdition_TemplateNameDuplicateErrorMessage);
				} else {
					nameValidationStatus.setOK();
				}
				updateStatus(nameValidationStatus);
			}

			private String createNewTemplateName() {
				if (!templateNames.contains(JavaUIMessages.GenerateToStringDialog_newTemplateName))
					return JavaUIMessages.GenerateToStringDialog_newTemplateName;

				int copyCount= 2;
				String newName;
				do {
					newName= Messages.format(JavaUIMessages.GenerateToStringDialog_newTemplateNameArg, new Integer(copyCount));
					copyCount++;
				} while (templateNames.contains(newName));
				return newName;
			}

			@Override
			public boolean close() {
				resultTemplateName= templateName.getText();
				resultTemplate= fixLineBreaks(template.getText());
				return super.close();
			}

			public String getTemplateName() {
				return resultTemplateName;
			}

			public String getTemplate() {
				return resultTemplate;
			}

			private String fixLineBreaks(String input) {
				String systemLineDelimiter= Text.DELIMITER;
				final String javaLineDelimiter= "\n"; //$NON-NLS-1$
				if (!systemLineDelimiter.equals(javaLineDelimiter)) {
					StringBuffer outputBuffer= new StringBuffer(input);
					int pos= outputBuffer.indexOf(systemLineDelimiter);
					while (pos >= 0) {
						outputBuffer.delete(pos, pos + systemLineDelimiter.length());
						outputBuffer.insert(pos, javaLineDelimiter);
						pos= outputBuffer.indexOf(systemLineDelimiter, pos + javaLineDelimiter.length());
					}
					return outputBuffer.toString();
				}
				return input;
			}

		}


		private class ToStringTemplateProposalProvider implements IContentProposalProvider {
			private class Proposal implements IContentProposal {
				final private String proposal;

				private int position;

				public Proposal(String proposal) {
					this.proposal= proposal;
					this.position= proposal.length();
				}

				public String getContent() {
					int overlap= stringOverlap(latestContents.substring(0, latestPosition), proposal);
					position= proposal.length() - overlap;
					return proposal.substring(overlap);
				}

				public int getCursorPosition() {
					return position;
				}

				public String getDescription() {
					return parser.getVariableDescriptions().get(proposal);
				}

				public String getLabel() {
					return proposal;
				}
			}

			private String latestContents;

			private int latestPosition;

			public IContentProposal[] getProposals(String contents, int position) {
				List<Proposal> primaryProposals= new ArrayList<Proposal>();
				List<Proposal> secondaryProposals= new ArrayList<Proposal>();
				String[] proposalStrings= parser.getVariables();
				String contentToCursor= contents.substring(0, position);
				for (int i= 0; i < proposalStrings.length; i++) {
					if (stringOverlap(contentToCursor, proposalStrings[i]) > 0)
						primaryProposals.add(new Proposal(proposalStrings[i]));
					else
						secondaryProposals.add(new Proposal(proposalStrings[i]));
				}

				this.latestContents= contents;
				this.latestPosition= position;

				primaryProposals.addAll(secondaryProposals);
				return primaryProposals.toArray(new IContentProposal[0]);
			}

			/**
			 * Checks if the end of the first string is equal to the beginning of of the second
			 * string.
			 * 
			 * @param s1 first String
			 * @param s2 second String
			 * @return length of overlapping segment (0 if strings don't overlap)
			 */
			private int stringOverlap(String s1, String s2) {
				int l1= s1.length();
				for (int l= 1; l <= Math.min(s1.length(), s2.length()); l++) {
					boolean ok= true;
					for (int i= 0; i < l; i++) {
						if (s1.charAt(l1 - l + i) != s2.charAt(i)) {
							ok= false;
							break;
						}
					}
					if (ok)
						return l;
				}
				return 0;
			}
		}

		private final int ADD_BUTTON= IDialogConstants.CLIENT_ID + 1;

		private final int REMOVE_BUTTON= IDialogConstants.CLIENT_ID + 2;

		private final int APPLY_BUTTON= IDialogConstants.CLIENT_ID + 3;

		private final int EDIT_BUTTON= IDialogConstants.CLIENT_ID + 4;

		private Text templateTextControl;

		private org.eclipse.swt.widgets.List templateNameControl;

		private ToStringTemplateParser parser;

		private List<String> templateNames;

		private List<String> templates;

		private int selectedTemplateNumber;

		private boolean somethingChanged= false;

		private StatusInfo validationStatus= new StatusInfo();

		protected ToStringTemplatesDialog(Shell parentShell, ToStringTemplateParser parser) {
			super(parentShell);
			this.parser= parser;
			this.setShellStyle(this.getShellStyle() | SWT.RESIZE);
			this.create();
			PlatformUI.getWorkbench().getHelpSystem().setHelp(getShell(), IJavaHelpContextIds.GENERATE_TOSTRING_MANAGE_TEMPLATES_DIALOG);
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			getShell().setText(JavaUIMessages.GenerateToStringDialog_templatesManagerTitle);

			Composite composite= (Composite)super.createDialogArea(parent);

			Label label= new Label(composite, SWT.LEFT);
			label.setText(JavaUIMessages.GenerateToStringDialog_templatesManagerTemplatesList);

			Composite templatesComposite= new Composite(composite, SWT.NONE);
			templatesComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			GridLayout gl= new GridLayout(2, false);
			gl.marginWidth= gl.marginHeight= 0;
			templatesComposite.setLayout(gl);

			templateNameControl= new org.eclipse.swt.widgets.List(templatesComposite, SWT.BORDER | SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL);
			templateNameControl.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			Composite rightComposite= new Composite(templatesComposite, SWT.NONE);
			gl= new GridLayout();
			gl.marginWidth= gl.marginHeight= 0;
			rightComposite.setLayout(gl);
			rightComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
			createButton(rightComposite, ADD_BUTTON, JavaUIMessages.GenerateToStringDialog_templatesManagerNewButton, false);
			createButton(rightComposite, REMOVE_BUTTON, JavaUIMessages.GenerateToStringDialog_templatesManagerRemoveButton, false);
			createButton(rightComposite, EDIT_BUTTON, JavaUIMessages.GenerateToStringDialog_teplatesManagerEditButton, false);
			((GridLayout)rightComposite.getLayout()).numColumns= 1;

			label= new Label(composite, SWT.LEFT);
			label.setText(JavaUIMessages.GenerateToStringDialog_templatesManagerPreview);

			templateTextControl= new Text(composite, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY);
			SWTUtil.fixReadonlyTextBackground(templateTextControl);
			GridData gd= new GridData(SWT.FILL, SWT.FILL, true, true);
			gd.heightHint= 80;
			gd.widthHint= 450;
			templateTextControl.setLayoutData(gd);

			templateNames= new ArrayList<String>(Arrays.asList(getTemplateNames()));
			templates= new ArrayList<String>(Arrays.asList(getTemplates(getDialogSettings())));
			selectedTemplateNumber= getGenerationSettings().stringFormatTemplateNumber;
			refreshControls();

			templateNameControl.addSelectionListener(new SelectionListener() {
				public void widgetDefaultSelected(SelectionEvent e) {
					onEdit();
				}

				public void widgetSelected(SelectionEvent e) {
					if (templateNameControl.getSelectionIndex() >= 0) {
						selectedTemplateNumber= templateNameControl.getSelectionIndex();
						templateTextControl.setText(templates.get(selectedTemplateNumber));
					}
				}
			});

			applyDialogFont(composite);

			return composite;
		}



		@Override
		protected void createButtonsForButtonBar(Composite parent) {
			super.createButtonsForButtonBar(parent);
			createButton(parent, APPLY_BUTTON, JavaUIMessages.GenerateToStringDialog_templateManagerApplyButton, false).setEnabled(false);
		}

		private void applyChanges() {
			getDialogSettings().put(ToStringGenerationSettings.SETTINGS_TEMPLATE_NAMES, templateNames.toArray(new String[0]));
			getDialogSettings().put(ToStringGenerationSettings.SETTINGS_TEMPLATES, templates.toArray(new String[0]));
			getGenerationSettings().stringFormatTemplateNumber= Math.max(selectedTemplateNumber, 0);
			somethingChanged= false;
			getButton(APPLY_BUTTON).setEnabled(false);
		}

		@Override
		protected void buttonPressed(int buttonId) {
			switch (buttonId) {
				case APPLY_BUTTON:
					applyChanges();
					break;
				case IDialogConstants.OK_ID:
					applyChanges();
					close();
					break;
				case IDialogConstants.CANCEL_ID:
					close();
					break;
				case ADD_BUTTON:
					TemplateEditionDialog dialog= new TemplateEditionDialog(getShell(), -1);
					dialog.open();
					if (dialog.getReturnCode() == IDialogConstants.OK_ID) {
						templateNames.add(dialog.getTemplateName());
						templates.add(dialog.getTemplate());
						selectedTemplateNumber= templateNames.size() - 1;
						somethingChanged= true;
						refreshControls();
					}
					break;
				case REMOVE_BUTTON:
					if (templateNames.size() > 0) {
						templateNames.remove(selectedTemplateNumber);
						templates.remove(selectedTemplateNumber);
					}
					if (selectedTemplateNumber >= templateNames.size())
						selectedTemplateNumber= templateNames.size() - 1;
					somethingChanged= true;
					refreshControls();
					break;
				case EDIT_BUTTON:
					onEdit();
			}
		}

		private void onEdit() {
			TemplateEditionDialog dialog;
			dialog= new TemplateEditionDialog(getShell(), selectedTemplateNumber);
			dialog.open();
			if (dialog.getReturnCode() == IDialogConstants.OK_ID) {
				templateNames.set(selectedTemplateNumber, dialog.getTemplateName());
				templates.set(selectedTemplateNumber, dialog.getTemplate());
				somethingChanged= true;
				refreshControls();
			}
		}

		public void refreshControls() {
			templateNameControl.setItems(templateNames.toArray(new String[0]));
			if (templateNames.size() > 0) {
				templateNameControl.select(selectedTemplateNumber);
				templateTextControl.setText(templates.get(selectedTemplateNumber));
			} else {
				templateTextControl.setText(""); //$NON-NLS-1$
			}
			revalidate();
			if (getButton(APPLY_BUTTON) != null)
				getButton(APPLY_BUTTON).setEnabled(somethingChanged && getButton(IDialogConstants.OK_ID).getEnabled());
			if (getButton(REMOVE_BUTTON) != null)
				getButton(REMOVE_BUTTON).setEnabled(templateNames.size() > 0);
			if (getButton(EDIT_BUTTON) != null)
				getButton(EDIT_BUTTON).setEnabled(templateNames.size() > 0);
		}

		private void revalidate() {
			if (templateNames.size() > 0)
				validationStatus.setOK();
			else
				validationStatus.setError(JavaUIMessages.GenerateToStringDialog_templateManagerNoTemplateErrorMessage);
			updateStatus(validationStatus);
		}
	}

	private static class CustomBuilderValidator implements ISelectionStatusValidator {

		private final IJavaProject fJavaProject;

		private IType fLastValidBuilderType;

		private List<String> fLastValidAppendMethodSuggestions;

		private List<String> fLastValidResultMethodSuggestions;

		public CustomBuilderValidator(IJavaProject javaProject) {
			fJavaProject= javaProject;
		}

		public IStatus validateBuilderType(IType type) {
			if (fLastValidBuilderType != null && fLastValidBuilderType.equals(type)) {
				return new StatusInfo();
			}

			try {
				IMethod[] methods= type.getMethods();
				boolean foundConstructor= false;
				for (int i= 0; i < methods.length; i++) {
					if (methods[i].isConstructor() && Flags.isPublic(methods[i].getFlags())) {
						String[] parameterTypes= methods[i].getParameterTypes();
						if (parameterTypes.length == 1 && "java.lang.Object".equals(JavaModelUtil.getResolvedTypeName(parameterTypes[0], type))) { //$NON-NLS-1$
							foundConstructor= true;
							break;
						}
					}
				}
				if (!foundConstructor)
					return new StatusInfo(IStatus.ERROR, JavaUIMessages.GenerateToStringDialog_customBuilderConfig_noConstructorError);

				List<String> appendMethodSuggestions= getAppendMethodSuggestions(type);
				if (appendMethodSuggestions.isEmpty())
					return new StatusInfo(IStatus.ERROR, JavaUIMessages.GenerateToStringDialog_customBuilderConfig_noAppendMethodError);

				List<String> resultMethodSuggestions= getResultMethodSuggestions(type);
				if (resultMethodSuggestions.isEmpty())
					return new StatusInfo(IStatus.ERROR, JavaUIMessages.GenerateToStringDialog_customBuilderConfig_noResultMethodError);

				fLastValidBuilderType= type;
				fLastValidAppendMethodSuggestions= appendMethodSuggestions;
				fLastValidResultMethodSuggestions= resultMethodSuggestions;
				return new StatusInfo();
			} catch (JavaModelException e1) {
				return new StatusInfo(IStatus.WARNING, JavaUIMessages.GenerateToStringDialog_customBuilderConfig_typeValidationError);
			}
		}

		public IStatus validate(Object[] selection) {
			return validateBuilderType(((IType)selection[0]));
		}

		public IStatus revalidateAll(CustomBuilderSettings builderSettings) {
			try {
				if (builderSettings.className.length() == 0) {
					return new StatusInfo(IStatus.ERROR, JavaUIMessages.GenerateToStringDialog_customBuilderConfig_noBuilderClassError);
				}

				IType type= findType(builderSettings.className);

				if (type == null || !type.exists()) {
					return new StatusInfo(IStatus.ERROR, MessageFormat.format(JavaUIMessages.GenerateToStringDialog_customBuilderConfig_invalidClassError,
							new Object[] { builderSettings.className }));
				}

				IStatus typeValidation= validateBuilderType(type);
				if (!typeValidation.isOK())
					return typeValidation;

				if (!getAppendMethodSuggestions(type).contains(builderSettings.appendMethod))
					return new StatusInfo(IStatus.ERROR, MessageFormat.format(JavaUIMessages.GenerateToStringDialog_customBuilderConfig_invalidAppendMethodError,
							new Object[] { builderSettings.appendMethod }));

				if (!getResultMethodSuggestions(type).contains(builderSettings.resultMethod))
					return new StatusInfo(IStatus.ERROR, MessageFormat.format(JavaUIMessages.GenerateToStringDialog_customBuilderConfig_invalidResultMethodError,
							new Object[] { builderSettings.resultMethod }));

				if (!isValidJavaIdentifier(builderSettings.variableName))
					return new StatusInfo(IStatus.ERROR, MessageFormat.format(JavaUIMessages.GenerateToStringDialog_customBuilderConfig_invalidVariableNameError,
							new Object[] { builderSettings.variableName }));

			} catch (JavaModelException e) {
				return new StatusInfo(IStatus.WARNING, JavaUIMessages.GenerateToStringDialog_customBuilderConfig_dataValidationError);
			}
			return new StatusInfo();
		}

		public IType findType(String builderClassName) throws JavaModelException {
			if (fLastValidBuilderType != null && builderClassName.equals(fLastValidBuilderType.getFullyQualifiedParameterizedName())) {
				return fLastValidBuilderType;
			}

			return fJavaProject.findType(builderClassName, (IProgressMonitor)null);
		}

		public List<String> getAppendMethodSuggestions(final IType type) throws JavaModelException {
			if (fLastValidBuilderType != null && fLastValidBuilderType.equals(type)) {
				return fLastValidAppendMethodSuggestions;
			}
			return getMethodSuggestions(type, new MethodChecker() {
				public boolean isMethodOK(IMethod method) throws JavaModelException {
					if (!Flags.isPublic(method.getFlags()) || method.isConstructor())
						return false;
					/* To be an append method, it must take exactly one
					 * Object parameter, and optionally one String parameter. */
					String[] parameterTypes= method.getParameterTypes();
					if (parameterTypes.length == 0 || parameterTypes.length > 2) {
						return false;
					}
					int countObjects= 0, countStrings= 0;
					for (int i= 0; i < parameterTypes.length; i++) {
						String resolvedParameterTypeName= JavaModelUtil.getResolvedTypeName(parameterTypes[i], type);
						if ("java.lang.Object".equals(resolvedParameterTypeName))//$NON-NLS-1$
							countObjects++;
						if ("java.lang.String".equals(resolvedParameterTypeName))//$NON-NLS-1$
							countStrings++;
					}
					return countObjects == 1 && countObjects + countStrings == parameterTypes.length;

				}
			});
		}

		public List<String> getResultMethodSuggestions(final IType type) throws JavaModelException {
			if (fLastValidBuilderType != null && fLastValidBuilderType.equals(type)) {
				return fLastValidResultMethodSuggestions;
			}
			return getMethodSuggestions(type, new MethodChecker() {
				public boolean isMethodOK(IMethod method) throws JavaModelException {
					return Flags.isPublic(method.getFlags()) && method.getParameterTypes().length == 0 && "java.lang.String".equals(JavaModelUtil.getResolvedTypeName(method.getReturnType(), type)); //$NON-NLS-1$
				}
			});
		}

		private interface MethodChecker {
			boolean isMethodOK(IMethod method) throws JavaModelException;
		}

		private List<String> getMethodSuggestions(IType type, MethodChecker checker) throws JavaModelException {
			ArrayList<String> result= new ArrayList<String>();
			IType[] classes= type.newSupertypeHierarchy(null).getAllClasses();
			for (int i= 0; i < classes.length; i++) {
				IMethod[] methods= classes[i].getMethods();
				for (int j= 0; j < methods.length; j++) {
					if (checker.isMethodOK(methods[j])) {
						String name= methods[j].getElementName();
						if (!result.contains(name))
							result.add(name);
					}
				}
			}
			return result;
		}

		private boolean isValidJavaIdentifier(String identifier) {
			return JavaConventionsUtil.validateIdentifier(identifier, fJavaProject).isOK();
		}
	}

	private class CustomBuilderConfigurationDialog extends StatusDialog {

		private final int APPLY_BUTTON= IDialogConstants.CLIENT_ID + 1;

		/**
		 * Extension for class selection dialog - validates selected type
		 */
		private final TypeSelectionExtension fExtension= new TypeSelectionExtension() {
			@Override
			public ISelectionStatusValidator getSelectionValidator() {
				return getValidator();
			}
		};

		/**
		 * Listener for text fields - updates combos and validates entered data
		 */
		private final ModifyListener modifyListener= new ModifyListener() {

			public void modifyText(ModifyEvent e) {
				if (e.widget == fBuilderClassName) {
					fBuilderSettings.className= fBuilderClassName.getText();
					updateCombos();
				} else if (e.widget == fBuilderVariableName)
					fBuilderSettings.variableName= fBuilderVariableName.getText();

				IStatus status= getValidator().revalidateAll(fBuilderSettings);
				updateStatus(status);

				enableApplyButton();
			}
		};

		private final CustomBuilderValidator fValidator= new CustomBuilderValidator(getType().getJavaProject());

		private Text fBuilderClassName;

		private Text fBuilderVariableName;

		private Combo fAppendMethodName;

		private Combo fResultMethodName;

		private Button fChainInvocations;

		private ToStringGenerationSettings.CustomBuilderSettings fBuilderSettings;

		private boolean somethingChanged= false;

		public CustomBuilderConfigurationDialog(Shell parent) {
			super(parent);
			this.setShellStyle(this.getShellStyle() | SWT.RESIZE);
			fBuilderSettings= getGenerationSettings().getCustomBuilderSettings();
		}

		public CustomBuilderValidator getValidator() {
			return fValidator;
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			getShell().setText(JavaUIMessages.GenerateToStringDialog_customBuilderConfig_windowTitle);

			Composite composite= (Composite)super.createDialogArea(parent);
			((GridLayout)composite.getLayout()).numColumns= 3;
			LayoutUtil.setWidthHint(composite, convertWidthInCharsToPixels(100));

			Label label= new Label(composite, SWT.LEFT);
			label.setText(JavaUIMessages.GenerateToStringDialog_customBuilderConfig_builderClassField);
			fBuilderClassName= createTextField(composite, 1, fBuilderSettings.className);

			Button button= new Button(composite, SWT.NONE);
			button.setText(JavaUIMessages.GenerateToStringDialog_customBuilderConfig_browseButton);
			setButtonLayoutData(button);
			button.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					browseForBuilderClass();
				}
			});

			label= new Label(composite, SWT.LEFT);
			label.setText(JavaUIMessages.GenerateToStringDialog_customBuilderConfig_varNameField);
			fBuilderVariableName= createTextField(composite, 2, fBuilderSettings.variableName);

			label= new Label(composite, SWT.LEFT);
			label.setText(JavaUIMessages.GenerateToStringDialog_customBuilderConfig_appendMethodField);
			fAppendMethodName= new Combo(composite, SWT.READ_ONLY);
			fAppendMethodName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

			label= new Label(composite, SWT.LEFT);
			label.setText(JavaUIMessages.GenerateToStringDialog_customBuilderConfig_resultMethodField);
			fResultMethodName= new Combo(composite, SWT.READ_ONLY);
			fResultMethodName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

			updateCombos();
			ModifyListener comboListener= new ModifyListener() {
				public void modifyText(ModifyEvent e) {
					Combo c= (Combo)e.widget;
					if (c.getText().length() > 0) {
						if (c == fAppendMethodName)
							fBuilderSettings.appendMethod= c.getText();
						if (c == fResultMethodName)
							fBuilderSettings.resultMethod= c.getText();
					}
					updateStatus(fValidator.revalidateAll(fBuilderSettings));
					enableApplyButton();
				}
			};
			fAppendMethodName.addModifyListener(comboListener);
			fResultMethodName.addModifyListener(comboListener);
			if (!select(fAppendMethodName, fBuilderSettings.appendMethod)) {
				fAppendMethodName.select(0);
			}
			if (!select(fResultMethodName, fBuilderSettings.resultMethod)) {
				fResultMethodName.select(0);
			}

			fChainInvocations= new Button(composite, SWT.CHECK);
			fChainInvocations.setText(JavaUIMessages.GenerateToStringDialog_customBuilderConfig_chainedCallsCheckbox);
			fChainInvocations.setSelection(fBuilderSettings.chainCalls);
			fChainInvocations.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
			fChainInvocations.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					fBuilderSettings.chainCalls= fChainInvocations.getSelection();
					enableApplyButton();
				}
			});

			PlatformUI.getWorkbench().getHelpSystem().setHelp(getShell(), IJavaHelpContextIds.GENERATE_TOSTRING_CONFIGURE_CUSTOM_BUILDER_DIALOG);

			return composite;
		}

		@Override
		public void create() {
			super.create();
			IStatus status= getValidator().revalidateAll(fBuilderSettings);
			updateStatus(status);
		}

		@Override
		protected void createButtonsForButtonBar(Composite parent) {
			super.createButtonsForButtonBar(parent);
			createButton(parent, APPLY_BUTTON, JavaUIMessages.GenerateToStringDialog_customBuilderConfig_applyButton, false).setEnabled(false);
		}

		private void enableApplyButton() {
			somethingChanged= true;
			getButton(APPLY_BUTTON).setEnabled(!getStatus().matches(IStatus.ERROR));
		}

		@Override
		protected void updateButtonsEnableState(IStatus status) {
			super.updateButtonsEnableState(status);
			getButton(APPLY_BUTTON).setEnabled(!status.matches(IStatus.ERROR) && somethingChanged);
		}

		@Override
		protected void buttonPressed(int buttonId) {
			switch (buttonId) {
				case APPLY_BUTTON:
					getButton(APPLY_BUTTON).setEnabled(false);
					somethingChanged= false;
					//$FALL-THROUGH$
				case OK:
					applyChanges();
			}
			super.buttonPressed(buttonId);
		}

		private boolean select(Combo combo, String item) {
			int index= Arrays.asList(combo.getItems()).indexOf(item);
			if (index >= 0) {
				combo.select(index);
				return true;
			}
			return false;
		}

		private void updateCombos() {
			final String[] empty= new String[0];
			try {
				IType type= fValidator.findType(fBuilderSettings.className);
				if (type == null) {
					fAppendMethodName.setItems(empty);
					fResultMethodName.setItems(empty);
				} else {
					fAppendMethodName.setItems(fValidator.getAppendMethodSuggestions(type).toArray(empty));
					select(fAppendMethodName, fBuilderSettings.appendMethod);
					fResultMethodName.setItems(fValidator.getResultMethodSuggestions(type).toArray(empty));
					select(fResultMethodName, fBuilderSettings.resultMethod);
				}
			} catch (JavaModelException e1) {
				fAppendMethodName.setItems(empty);
				fResultMethodName.setItems(empty);
			}
		}

		private void applyChanges() {
			fBuilderSettings.appendMethod= fAppendMethodName.getText();
			fBuilderSettings.resultMethod= fResultMethodName.getText();
			getGenerationSettings().writeCustomBuilderSettings(fBuilderSettings);
		}

		private Text createTextField(Composite composite, int gridHSpan, String text) {
			Text result= new Text(composite, SWT.BORDER);
			result.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, gridHSpan, 1));
			result.setText(text);
			result.addModifyListener(modifyListener);
			TextFieldNavigationHandler.install(result);
			return result;
		}

		private void browseForBuilderClass() {
			try {
				IJavaSearchScope scope= SearchEngine.createJavaSearchScope(new IJavaElement[] { getType().getJavaProject() });
				SelectionDialog dialog= JavaUI.createTypeDialog(getShell(), PlatformUI.getWorkbench().getProgressService(), scope,
						IJavaElementSearchConstants.CONSIDER_CLASSES, false, "*ToString", fExtension); //$NON-NLS-1$
				dialog.setTitle(JavaUIMessages.GenerateToStringDialog_customBuilderConfig_classSelection_windowTitle);
				dialog.setMessage(JavaUIMessages.GenerateToStringDialog_customBuilderConfig_classSelection_message);
				dialog.open();
				if (dialog.getReturnCode() == OK) {
					IType type= (IType)dialog.getResult()[0];
					fBuilderClassName.setText(type.getFullyQualifiedParameterizedName());
					List<String> suggestions= fValidator.getAppendMethodSuggestions(type);
					if (!suggestions.contains(fAppendMethodName.getText()))
						fAppendMethodName.setText(suggestions.get(0));
					suggestions= fValidator.getResultMethodSuggestions(type);
					if (!suggestions.contains(fResultMethodName.getText()))
						fResultMethodName.setText(suggestions.get(0));
				}
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
			}
		}
	}

	private ToStringGenerationSettings fGenerationSettings;

	private static final int DOWN_BUTTON= IDialogConstants.CLIENT_ID + 2;

	private static final int UP_BUTTON= IDialogConstants.CLIENT_ID + 1;
	
	private static final int SORT_BUTTON= IDialogConstants.CLIENT_ID + 3;

	protected Button[] fButtonControls;

	boolean[] fButtonsEnabled;

	private static final int DOWN_INDEX= 1;

	private static final int UP_INDEX= 0;

	public ToStringGenerationSettings getGenerationSettings() {
		return fGenerationSettings;
	}

	public static String[] getTemplates(IDialogSettings dialogSettings) {
		String[] result= dialogSettings.getArray(ToStringGenerationSettings.SETTINGS_TEMPLATES);
		if (result != null && result.length > 0)
			return result;
		return new String[] { ToStringTemplateParser.DEFAULT_TEMPLATE };
	}

	public String[] getTemplateNames() {
		String[] result= getDialogSettings().getArray(ToStringGenerationSettings.SETTINGS_TEMPLATE_NAMES);
		if (result != null && result.length > 0)
			return result;
		return new String[] { JavaUIMessages.GenerateToStringDialog_defaultTemplateName };
	}

	public int getSelectedTemplate() {
		try {
			int result= getDialogSettings().getInt(ToStringGenerationSettings.SETTINGS_SELECTED_TEMPLATE);
			if (result < 0)
				return 0;
			return result;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public void setSelectedTemplate(int templateNumber) {
		getDialogSettings().put(ToStringGenerationSettings.SETTINGS_SELECTED_TEMPLATE, templateNumber);
	}

	public GenerateToStringDialog(Shell shell, CompilationUnitEditor editor, IType type, IVariableBinding[] fields, IVariableBinding[] inheritedFields, IVariableBinding[] selectedFields,
			IMethodBinding[] methods, IMethodBinding[] inheritededMethods) throws JavaModelException {
		super(shell, new BindingLabelProvider(), new GenerateToStringContentProvider(fields, inheritedFields, methods, inheritededMethods), editor, type, false);
		setEmptyListMessage(JavaUIMessages.GenerateHashCodeEqualsDialog_no_entries);

		List<Object> selected= new ArrayList<Object>(Arrays.asList(selectedFields));
		if (selectedFields.length == fields.length && selectedFields.length > 0)
			selected.add(getContentProvider().getParent(selectedFields[0]));
		setInitialElementSelections(selected);

		setTitle(JavaUIMessages.GenerateToStringDialog_dialog_title);
		setMessage(JavaUIMessages.GenerateToStringDialog_select_fields_to_include);
		setValidator(new GenerateToStringValidator(fields.length + inheritedFields.length, methods.length + inheritededMethods.length));
		setSize(60, 18);
		setInput(new Object());

		fGenerationSettings= new ToStringGenerationSettings(getDialogSettings());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean close() {
		fGenerationSettings.writeDialogSettings();

		fGenerationSettings.stringFormatTemplate= getTemplates(getDialogSettings())[fGenerationSettings.stringFormatTemplateNumber];

		fGenerationSettings.createComments= getGenerateComment();

		return super.close();
	}

	@Override
	public Object[] getResult() {
		Object[] oldResult= super.getResult();
		List<Object> newResult= new ArrayList<Object>();
		for (int i= 0; i < oldResult.length; i++) {
			if (!(oldResult[i] instanceof String)) {
				newResult.add(oldResult[i]);
			}
		}
		return newResult.toArray();
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(shell, IJavaHelpContextIds.GENERATE_TOSTRING_SELECTION_DIALOG);
	}

	@Override
	protected CheckboxTreeViewer createTreeViewer(Composite parent) {
		CheckboxTreeViewer treeViewer= super.createTreeViewer(parent);

		treeViewer.setLabelProvider(new GenerateToStringLabelProvider());

		//expandAll because setSubtreeChecked() used in CheckStateListener below assumes that elements have been expanded
		treeViewer.expandAll();
		//but actually we only need one branch expanded
		treeViewer.collapseAll();
		treeViewer.expandToLevel(GenerateToStringContentProvider.fieldsNode, 1);

		treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection= (IStructuredSelection)getTreeViewer().getSelection();

				Object selected= selection.size() > 0 ? selection.toList().get(0) : null;
				GenerateToStringContentProvider cp= (GenerateToStringContentProvider)getContentProvider();

				fButtonControls[UP_INDEX].setEnabled(cp.canMoveUp(selected));
				fButtonControls[DOWN_INDEX].setEnabled(cp.canMoveDown(selected));
			}

		});
		treeViewer.addCheckStateListener(new ICheckStateListener() {
			public void checkStateChanged(CheckStateChangedEvent event) {
				getTreeViewer().setSubtreeChecked(event.getElement(), event.getChecked());
				getTreeViewer().setGrayed(event.getElement(), false);
				Object parentElement= ((ITreeContentProvider)(getTreeViewer().getContentProvider())).getParent(event.getElement());
				if (parentElement != null) {
					Object[] siblings= ((ITreeContentProvider)(getTreeViewer().getContentProvider())).getChildren(parentElement);
					int count= 0;
					for (int i= 0; i < siblings.length; i++) {
						if (getTreeViewer().getChecked(siblings[i]))
							count++;
					}
					if (count == 0)
						getTreeViewer().setGrayChecked(parentElement, false);
					else if (count == siblings.length) {
						getTreeViewer().setChecked(parentElement, true);
						getTreeViewer().setGrayed(parentElement, false);
					} else
						getTreeViewer().setGrayChecked(parentElement, true);
				}
				updateOKStatus();
			}

		});
		return treeViewer;
	}

	@Override
	protected Composite createSelectionButtons(Composite composite) {
		Composite buttonComposite= super.createSelectionButtons(composite);

		GridLayout layout= new GridLayout();
		buttonComposite.setLayout(layout);

		createUpDownButtons(buttonComposite);
		
		createButton(buttonComposite, SORT_BUTTON, JavaUIMessages.GenerateToStringDialog_sort_button, false);

		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.numColumns= 1;

		return buttonComposite;
	}

	@Override
	protected void buttonPressed(int buttonId) {
		super.buttonPressed(buttonId);
		switch (buttonId) {
			case UP_BUTTON: {
				GenerateToStringContentProvider contentProvider= (GenerateToStringContentProvider)getTreeViewer().getContentProvider();
				List<?> selection= ((IStructuredSelection)getTreeViewer().getSelection()).toList();
				if (selection.size() > 0)
					contentProvider.up(selection.get(0), getTreeViewer());
				updateOKStatus();
				break;
			}
			case DOWN_BUTTON: {
				GenerateToStringContentProvider contentProvider= (GenerateToStringContentProvider)getTreeViewer().getContentProvider();
				List<?> selection= ((IStructuredSelection)getTreeViewer().getSelection()).toList();
				if (selection.size() > 0)
					contentProvider.down(selection.get(0), getTreeViewer());
				updateOKStatus();
				break;
			}
			case SORT_BUTTON: {
				GenerateToStringContentProvider contentProvider= (GenerateToStringContentProvider)getTreeViewer().getContentProvider();
				contentProvider.sort();
				getTreeViewer().refresh();
				updateOKStatus();
				break;
			}
		}
	}

	protected void createUpDownButtons(Composite buttonComposite) {
		int numButtons= 2; // up, down
		fButtonControls= new Button[numButtons];
		fButtonsEnabled= new boolean[numButtons];
		fButtonControls[UP_INDEX]= createButton(buttonComposite, UP_BUTTON, JavaUIMessages.GenerateToStringDialog_up_button, false);
		fButtonControls[DOWN_INDEX]= createButton(buttonComposite, DOWN_BUTTON, JavaUIMessages.GenerateToStringDialog_down_button, false);
		boolean defaultState= false;
		fButtonControls[UP_INDEX].setEnabled(defaultState);
		fButtonControls[DOWN_INDEX].setEnabled(defaultState);
		fButtonsEnabled[UP_INDEX]= defaultState;
		fButtonsEnabled[DOWN_INDEX]= defaultState;
	}

	private Label formatLabel;

	private Combo formatCombo;

	private Button skipNullsButton;

	private Button styleButton;

	@Override
	protected Composite createCommentSelection(Composite parentComposite) {
		Composite composite= super.createCommentSelection(parentComposite);

		Group group= new Group(parentComposite, SWT.NONE);
		group.setText(JavaUIMessages.GenerateToStringDialog_generated_code_group);
		GridLayout groupLayout= new GridLayout();
		group.setLayout(groupLayout);
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));


		Composite composite2= new Composite(group, SWT.NONE);
		GridLayout layout= new GridLayout(3, false);
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		composite2.setLayout(layout);
		composite2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		formatLabel= new Label(composite2, SWT.NONE);
		formatLabel.setText(JavaUIMessages.GenerateToStringDialog_string_format_combo);
		GridData gridData= new GridData(SWT.FILL, SWT.CENTER, false, false);
		formatLabel.setLayoutData(gridData);

		formatCombo= new Combo(composite2, SWT.READ_ONLY);
		formatCombo.setItems(getTemplateNames());
		formatCombo.select(fGenerationSettings.stringFormatTemplateNumber);
		formatCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		SWTUtil.setDefaultVisibleItemCount(formatCombo);
		formatCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				fGenerationSettings.stringFormatTemplateNumber= ((Combo)e.widget).getSelectionIndex();
			}
		});

		final Button formatButton= new Button(composite2, SWT.NONE);
		formatButton.setText(JavaUIMessages.GenerateToStringDialog_manage_templates_button);
		setButtonLayoutData(formatButton);
		formatButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				manageTemplatesButtonSelected();
			}
		});

		final Label styleLabel= new Label(composite2, SWT.NONE);
		styleLabel.setText(JavaUIMessages.GenerateToStringDialog_code_style_combo);
		gridData= new GridData(SWT.FILL, SWT.CENTER, false, false);
		styleLabel.setLayoutData(gridData);

		final Combo styleCombo= new Combo(composite2, SWT.READ_ONLY);
		styleCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		styleCombo.setItems(GenerateToStringOperation.getStyleNames());
		styleCombo.select(Math.min(fGenerationSettings.toStringStyle, styleCombo.getItemCount() - 1));
		SWTUtil.setDefaultVisibleItemCount(styleCombo);
		styleCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				changeToStringStyle(((Combo)e.widget).getSelectionIndex());
			}
		});

		styleButton= new Button(composite2, SWT.NONE);
		styleButton.setText(JavaUIMessages.GenerateToStringDialog_codeStyleConfigureButton);
		setButtonLayoutData(styleButton);
		styleButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				configureStyleButtonSelected();
			}
		});

		skipNullsButton= new Button(group, SWT.CHECK);
		skipNullsButton.setText(JavaUIMessages.GenerateToStringDialog_skip_null_button);
		skipNullsButton.setSelection(fGenerationSettings.skipNulls);
		skipNullsButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				fGenerationSettings.skipNulls= ((Button)event.widget).getSelection();
			}
		});

		final Button arrayButton= new Button(group, SWT.CHECK);
		arrayButton.setText(JavaUIMessages.GenerateToStringDialog_ignore_default_button);
		arrayButton.setSelection(fGenerationSettings.customArrayToString);
		arrayButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				fGenerationSettings.customArrayToString= ((Button)e.widget).getSelection();
			}
		});

		Composite limitRow= new Composite(group, SWT.NONE);
		RowLayout rowLayout= new RowLayout();
		rowLayout.center= true;
		rowLayout.marginLeft= rowLayout.marginRight= rowLayout.marginTop= rowLayout.marginBottom= 0;
		limitRow.setLayout(rowLayout);

		final Button limitButton= new Button(limitRow, SWT.CHECK);
		limitButton.setText(JavaUIMessages.GenerateToStringDialog_limit_elements_button);
		limitButton.setSelection(fGenerationSettings.limitElements);
		limitButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				fGenerationSettings.limitElements= ((Button)e.widget).getSelection();
			}
		});

		final Spinner limitSpinner= new Spinner(limitRow, SWT.BORDER);
		limitSpinner.setMinimum(0);
		limitSpinner.setSelection(fGenerationSettings.limitValue);
		limitSpinner.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				fGenerationSettings.limitValue= ((Spinner)e.widget).getSelection();
			}
		});

		//invoked to change initial enable state of controls
		changeToStringStyle(styleCombo.getSelectionIndex());

		return composite;
	}

	private void manageTemplatesButtonSelected() {
		ToStringTemplatesDialog dialog= new ToStringTemplatesDialog(getShell(), GenerateToStringOperation.createTemplateParser(fGenerationSettings.toStringStyle));
		dialog.open();
		formatCombo.setItems(getTemplateNames());
		formatCombo.select(Math.min(fGenerationSettings.stringFormatTemplateNumber, formatCombo.getItemCount() - 1));
	}

	private void configureStyleButtonSelected() {
		CustomBuilderConfigurationDialog dialog= new CustomBuilderConfigurationDialog(getShell());
		dialog.open();
		updateOKStatus();
	}

	private void changeToStringStyle(int style) {
		fGenerationSettings.toStringStyle= style;
		skipNullsButton.setEnabled(style != GenerateToStringOperation.STRING_FORMAT);
		boolean enableFormat= (style != GenerateToStringOperation.CUSTOM_BUILDER);
		formatLabel.setEnabled(enableFormat);
		formatCombo.setEnabled(enableFormat);
		styleButton.setEnabled(style == GenerateToStringOperation.CUSTOM_BUILDER);
		updateOKStatus();
	}

}

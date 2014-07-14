/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Eric Rizzo - Externalize Strings wizard always defaults to the "legacy" mechanism - http://bugs.eclipse.org/271375
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring.nls;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.core.resources.IFile;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLayoutData;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.source.SourceViewer;

import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.nls.KeyValuePair;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSSubstitution;

import org.eclipse.jdt.ui.JavaElementImageDescriptor;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.PropertiesFileEscapes;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringDialogField;

class ExternalizeWizardPage extends UserInputWizardPage {

	private static final String[] PROPERTIES;
	private static final String[] fgTitles;
	private static final int STATE_PROP= 0;
	private static final int VAL_PROP= 1;
	private static final int KEY_PROP= 2;
	private static final int SIZE= 3; //column counter
	private static final int ROW_COUNT= 5;

	public static final String PAGE_NAME= "NLSWizardPage1"; //$NON-NLS-1$
	static {
		PROPERTIES= new String[SIZE];
		PROPERTIES[STATE_PROP]= "task"; //$NON-NLS-1$
		PROPERTIES[KEY_PROP]= "key"; //$NON-NLS-1$
		PROPERTIES[VAL_PROP]= "value"; //$NON-NLS-1$

		fgTitles= new String[SIZE];
		fgTitles[STATE_PROP]= ""; //$NON-NLS-1$
		fgTitles[KEY_PROP]= NLSUIMessages.ExternalizeWizardPage_key;
		fgTitles[VAL_PROP]= NLSUIMessages.ExternalizeWizardPage_value;
	}

	private class CellModifier implements ICellModifier {

		/**
		 * @see ICellModifier#canModify(Object, String)
		 */
		public boolean canModify(Object element, String property) {
			if (property == null)
				return false;

			if (!(element instanceof NLSSubstitution))
				return false;

			NLSSubstitution subst= (NLSSubstitution) element;
			if (PROPERTIES[KEY_PROP].equals(property) && subst.getState() != NLSSubstitution.EXTERNALIZED) {
				return false;
			}

			return true;
		}

		/**
		 * @see ICellModifier#getValue(Object, String)
		 */
		public Object getValue(Object element, String property) {
			if (element instanceof NLSSubstitution) {
				NLSSubstitution substitution= (NLSSubstitution) element;
				String res= null;
				if (PROPERTIES[KEY_PROP].equals(property)) {
					res= substitution.getKeyWithoutPrefix();
				} else if (PROPERTIES[VAL_PROP].equals(property)) {
					res= substitution.getValue();
				} else if (PROPERTIES[STATE_PROP].equals(property)) {
					return new Integer(substitution.getState());
				}
				if (res != null) {
					return getEscapedAsciiString(res);
				}
				return ""; //$NON-NLS-1$
			}
			return ""; //$NON-NLS-1$
		}

		/**
		 * @see ICellModifier#modify(Object, String, Object)
		 */
		public void modify(Object element, String property, Object value) {
			if (element instanceof TableItem) {
				Object data= ((TableItem) element).getData();
				if (data instanceof NLSSubstitution) {
					NLSSubstitution substitution= (NLSSubstitution) data;
					if (PROPERTIES[KEY_PROP].equals(property)) {
						String string = (String)value;
						try {
							string= PropertiesFileEscapes.unescape(string);
						} catch (CoreException e) {
							setPageComplete(RefactoringStatus.create(e.getStatus()));
							return;
						}
						substitution.setKey(string);
					}
					if (PROPERTIES[VAL_PROP].equals(property)) {
						String string = (String)value;
						try {
							string= PropertiesFileEscapes.unescape(string);
						} catch (CoreException e) {
							setPageComplete(RefactoringStatus.create(e.getStatus()));
							return;
						}
						substitution.setValue(string);
					}
					if (PROPERTIES[STATE_PROP].equals(property)) {
						substitution.setState(((Integer) value).intValue());
						if ((substitution.getState() == NLSSubstitution.EXTERNALIZED) && substitution.hasStateChanged()) {
							substitution.generateKey(fSubstitutions, getProperties(fNLSRefactoring.getPropertyFileHandle()));
						}
					}
				}
				validateKeys(false);
				fTableViewer.update(data, null);
			}
		}
	}

	private class NLSSubstitutionLabelProvider extends LabelProvider implements ITableLabelProvider, IFontProvider {

		private FontRegistry fFontRegistry;

		public NLSSubstitutionLabelProvider() {
			fFontRegistry= JFaceResources.getFontRegistry();
		}

		public String getColumnText(Object element, int columnIndex) {
			String columnText= ""; //$NON-NLS-1$
			if (element instanceof NLSSubstitution) {
				NLSSubstitution substitution= (NLSSubstitution) element;
				if (columnIndex == KEY_PROP) {
					if (substitution.getState() == NLSSubstitution.EXTERNALIZED) {
						columnText=  BasicElementLabels.getJavaElementName(substitution.getKey());
					}
				} else
					if ((columnIndex == VAL_PROP) && (substitution.getValue() != null)) {
						columnText= substitution.getValue();
					}
			}
			return getEscapedAsciiString(columnText);
		}

		public Image getColumnImage(Object element, int columnIndex) {
			if ((columnIndex == STATE_PROP) && (element instanceof NLSSubstitution)) {
				return getNLSImage((NLSSubstitution) element);
			}

			return null;
		}

		public Font getFont(Object element) {
			if (element instanceof NLSSubstitution) {
				NLSSubstitution substitution= (NLSSubstitution) element;
				if (substitution.hasPropertyFileChange() || substitution.hasSourceChange()) {
					return fFontRegistry.getBold(JFaceResources.DIALOG_FONT);
				}
			}
			return null;
		}

		private Image getNLSImage(NLSSubstitution sub) {
			if ((sub.getValue() == null) && (sub.getKey() != null)) {
				// Missing keys
				JavaElementImageDescriptor imageDescriptor= new JavaElementImageDescriptor(getNLSImageDescriptor(sub.getState()), JavaElementImageDescriptor.WARNING, JavaElementImageProvider.SMALL_SIZE);
				return JavaPlugin.getImageDescriptorRegistry().get(imageDescriptor);
			} else
				if (sub.isConflicting(fSubstitutions) || !isKeyValid(sub, null)) {
					JavaElementImageDescriptor imageDescriptor= new JavaElementImageDescriptor(getNLSImageDescriptor(sub.getState()), JavaElementImageDescriptor.ERROR, JavaElementImageProvider.SMALL_SIZE);
					return JavaPlugin.getImageDescriptorRegistry().get(imageDescriptor);
				} else {
					return	getNLSImage(sub.getState());
				}
		}

		private Image getNLSImage(int task) {
			switch (task) {
				case NLSSubstitution.EXTERNALIZED :
					return JavaPluginImages.get(JavaPluginImages.IMG_OBJS_NLS_TRANSLATE);
				case NLSSubstitution.IGNORED :
					return JavaPluginImages.get(JavaPluginImages.IMG_OBJS_NLS_NEVER_TRANSLATE);
				case NLSSubstitution.INTERNALIZED :
					return JavaPluginImages.get(JavaPluginImages.IMG_OBJS_NLS_SKIP);
				default :
					Assert.isTrue(false);
					return null;
			}
		}

		private ImageDescriptor getNLSImageDescriptor(int task) {
			switch (task) {
				case NLSSubstitution.EXTERNALIZED :
					return JavaPluginImages.DESC_OBJS_NLS_TRANSLATE;
				case NLSSubstitution.IGNORED :
					return JavaPluginImages.DESC_OBJS_NLS_NEVER_TRANSLATE;
				case NLSSubstitution.INTERNALIZED :
					return JavaPluginImages.DESC_OBJS_NLS_SKIP;
				default :
					Assert.isTrue(false);
					return null;
			}
		}
	}

	private static String getEscapedAsciiString(String s) {
		if (s != null) {
			StringBuffer sb= new StringBuffer(s.length());
			int length= s.length();
			for (int i= 0; i < length; i++) {
				char c= s.charAt(i);
				sb.append(getEscapedAsciiString(c));
			}
			return sb.toString();
		}
		return null;
	}

	private static String getEscapedAsciiString(char c) {
		switch (c) {
			case '\b':
				return "\\b";//$NON-NLS-1$
			case '\t':
				return "\\t";//$NON-NLS-1$
			case '\n':
				return "\\n";//$NON-NLS-1$
			case '\f':
				return "\\f";//$NON-NLS-1$
			case '\r':
				return "\\r";//$NON-NLS-1$
			case '\\':
				return "\\\\";//$NON-NLS-1$
		}
		return String.valueOf(c);
	}

	private class NLSInputDialog extends StatusDialog implements IDialogFieldListener {
		private StringDialogField fKeyField;
		private StringDialogField fValueField;
		private DialogField fMessageField;
		private NLSSubstitution fSubstitution;

		public NLSInputDialog(Shell parent, NLSSubstitution substitution) {
			super(parent);

			setTitle(NLSUIMessages.ExternalizeWizardPage_NLSInputDialog_Title);

			fSubstitution= substitution;

			fMessageField= new DialogField();
			if (substitution.getState() == NLSSubstitution.EXTERNALIZED) {
				fMessageField.setLabelText(NLSUIMessages.ExternalizeWizardPage_NLSInputDialog_ext_Label);
			} else {
				fMessageField.setLabelText(NLSUIMessages.ExternalizeWizardPage_NLSInputDialog_Label);
			}

			fKeyField= new StringDialogField();
			fKeyField.setLabelText(NLSUIMessages.ExternalizeWizardPage_NLSInputDialog_Enter_key);
			fKeyField.setDialogFieldListener(this);

			fValueField= new StringDialogField();
			fValueField.setLabelText(NLSUIMessages.ExternalizeWizardPage_NLSInputDialog_Enter_value);
			fValueField.setDialogFieldListener(this);

			if (substitution.getState() == NLSSubstitution.EXTERNALIZED) {
				fKeyField.setText(substitution.getKeyWithoutPrefix());
			} else {
				fKeyField.setText(""); //$NON-NLS-1$
			}

			fValueField.setText(substitution.getValueNonEmpty());
		}

		public KeyValuePair getResult() {
			KeyValuePair res= new KeyValuePair(fKeyField.getText(), fValueField.getText());
			return res;
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite composite= (Composite) super.createDialogArea(parent);

			Composite inner= new Composite(composite, SWT.NONE);
			inner.setFont(composite.getFont());

			GridLayout layout= new GridLayout();
			layout.marginHeight= 0;
			layout.marginWidth= 0;
			layout.numColumns= 2;
			inner.setLayout(layout);

			fMessageField.doFillIntoGrid(inner, 2);

			if (fSubstitution.getState() == NLSSubstitution.EXTERNALIZED) {
				fKeyField.doFillIntoGrid(inner, 2);
				LayoutUtil.setWidthHint(fKeyField.getTextControl(null), convertWidthInCharsToPixels(45));
			}

			fValueField.doFillIntoGrid(inner, 2);
			LayoutUtil.setWidthHint(fValueField.getTextControl(null), convertWidthInCharsToPixels(45));
			LayoutUtil.setHorizontalGrabbing(fValueField.getTextControl(null));

			fValueField.postSetFocusOnDialogField(parent.getDisplay());

			applyDialogFont(composite);
			return composite;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener#dialogFieldChanged(org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField)
		 */
		public void dialogFieldChanged(DialogField field) {
			IStatus keyStatus= validateKey(fKeyField.getText());
			//IStatus valueStatus= StatusInfo.OK_STATUS; // no validation yet

			//updateStatus(StatusUtil.getMoreSevere(valueStatus, keyStatus));
			updateStatus(keyStatus);
		}


		private IStatus validateKey(String val) {
			if (fSubstitution.getState() != NLSSubstitution.EXTERNALIZED) {
				return StatusInfo.OK_STATUS;
			}

			if (val == null || val.length() == 0) {
				return new StatusInfo(IStatus.ERROR, NLSUIMessages.ExternalizeWizardPage_NLSInputDialog_Error_empty_key);
			}

			if (fNLSRefactoring.isEclipseNLS()) {
				if (!Character.isJavaIdentifierStart(val.charAt(0)))
					return new StatusInfo(IStatus.ERROR, NLSUIMessages.ExternalizeWizardPage_NLSInputDialog_Error_invalid_EclipseNLS_key);

				for (int i= 1, length= val.length(); i < length; i++) {
					if (!Character.isJavaIdentifierPart(val.charAt(i)))
						return new StatusInfo(IStatus.ERROR, NLSUIMessages.ExternalizeWizardPage_NLSInputDialog_Error_invalid_EclipseNLS_key);
				}
			} else {
				// validation so keys don't contain spaces
				for (int i= 0; i < val.length(); i++) {
					if (Character.isWhitespace(val.charAt(i))) {
						return new StatusInfo(IStatus.ERROR, NLSUIMessages.ExternalizeWizardPage_NLSInputDialog_Error_invalid_key);
					}
				}
			}
			return StatusInfo.OK_STATUS;
		}
	}

	private static final String SETTINGS_NLS_ACCESSORS= "nls_accessor_history"; //$NON-NLS-1$
	private static final int SETTINGS_MAX_ENTRIES= 5;

	private Text fPrefixField;
	private Button fIsEclipseNLS;
	private Table fTable;
	private TableViewer fTableViewer;
	private SourceViewer fSourceViewer;

	private final ICompilationUnit fCu;
	private NLSSubstitution[] fSubstitutions;
	private Button fExternalizeButton;
	private Button fIgnoreButton;
	private Button fInternalizeButton;
	private Button fRevertButton;
	private Button fEditButton;
	private NLSRefactoring fNLSRefactoring;
	private Button fRenameButton;
	private Combo fAccessorClassField;

	private AccessorDescription[] fAccessorChoices;
	private Button fFilterCheckBox;

	public ExternalizeWizardPage(NLSRefactoring nlsRefactoring) {
		super(PAGE_NAME);
		fCu= nlsRefactoring.getCu();
		fSubstitutions= nlsRefactoring.getSubstitutions();
		fNLSRefactoring= nlsRefactoring;
		fAccessorChoices= null;

		setDescription(NLSUIMessages.ExternalizeWizardPage_description);
		createDefaultExternalization(fSubstitutions);
	}

	/*
	 * @see IDialogPage#createControl(Composite)
	 */
	public void createControl(Composite parent) {
		initializeDialogUnits(parent);

		Composite supercomposite= new Composite(parent, SWT.NONE);
		supercomposite.setFont(parent.getFont());
		supercomposite.setLayout(new GridLayout());

		createIsEclipseNLSCheckbox(supercomposite);

		createKeyPrefixField(supercomposite);

		SashForm composite= new SashForm(supercomposite, SWT.VERTICAL);
		composite.setFont(supercomposite.getFont());

		GridData data= new GridData(GridData.FILL_BOTH);
		composite.setLayoutData(data);

		createTableViewer(composite);
		createSourceViewer(composite);

		createAccessorInfoComposite(supercomposite);

		composite.setWeights(new int[]{65, 45});

		validateKeys(false);
		updateButtonStates(StructuredSelection.EMPTY);

		// promote control
		setControl(supercomposite);
		Dialog.applyDialogFont(supercomposite);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(supercomposite, IJavaHelpContextIds.EXTERNALIZE_WIZARD_KEYVALUE_PAGE);
	}

	private void createAccessorInfoComposite(Composite supercomposite) {
		Composite accessorComposite= new Composite(supercomposite, SWT.NONE);
		accessorComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		GridLayout layout= new GridLayout(2, false);
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		accessorComposite.setLayout(layout);

		Composite composite= new Composite(accessorComposite, SWT.NONE);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		layout= new GridLayout(1, true);
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		composite.setLayout(layout);

		Label accessorClassLabel= new Label(composite, SWT.NONE);
		accessorClassLabel.setText(NLSUIMessages.ExternalizeWizardPage_accessorclass_label);
		accessorClassLabel.setLayoutData(new GridData());


		SelectionListener listener= new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (e.widget instanceof Button) {
					doConfigureButtonPressed();
				} else {
					doAccessorSelectionChanged();
				}
			}
		};


		GridData data= new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint= convertWidthInCharsToPixels(30);
		fAccessorClassField= new Combo(composite, SWT.READ_ONLY);
		SWTUtil.setDefaultVisibleItemCount(fAccessorClassField);
		fAccessorClassField.setLayoutData(data);
		fAccessorClassField.addSelectionListener(listener);


		//new Label(composite, SWT.NONE); // placeholder

		Button configure= new Button(accessorComposite, SWT.PUSH);
		configure.setText(NLSUIMessages.ExternalizeWizardPage_configure_button);
		data= new GridData(GridData.HORIZONTAL_ALIGN_END | GridData.VERTICAL_ALIGN_END);
		data.widthHint= SWTUtil.getButtonWidthHint(configure);
		configure.setLayoutData(data);

		configure.addSelectionListener(listener);

		updateAccessorChoices();

	}

	protected void doAccessorSelectionChanged() {
		int selectionIndex= fAccessorClassField.getSelectionIndex();
		if (fAccessorChoices != null && selectionIndex < fAccessorChoices.length) {
			AccessorDescription selected= fAccessorChoices[selectionIndex];
			fNLSRefactoring.setAccessorClassName(selected.getAccessorClassName());
			fNLSRefactoring.setAccessorClassPackage(selected.getAccessorClassPackage());
			fNLSRefactoring.setResourceBundleName(selected.getResourceBundleName());
			fNLSRefactoring.setResourceBundlePackage(selected.getResourceBundlePackage());
			fNLSRefactoring.setIsEclipseNLS(fNLSRefactoring.detectIsEclipseNLS());

			NLSSubstitution.updateSubtitutions(fSubstitutions, getProperties(fNLSRefactoring.getPropertyFileHandle()), fNLSRefactoring.getAccessorClassName());
			if (fIsEclipseNLS != null) {
				fIsEclipseNLS.setSelection(fNLSRefactoring.isEclipseNLS());
				fIsEclipseNLS.setEnabled(willCreateAccessorClass());
				updatePrefix();
			}
			validateKeys(true);
		}
	}

	private boolean willCreateAccessorClass() {
		try {
			return fNLSRefactoring.willCreateAccessorClass();
		} catch (JavaModelException e) {
			return false;
		}
	}

	private void updateAccessorChoices() {

		AccessorDescription configured= new AccessorDescription(
				fNLSRefactoring.getAccessorClassName(),
				fNLSRefactoring.getAccessorClassPackage(),
				fNLSRefactoring.getResourceBundleName(),
				fNLSRefactoring.getResourceBundlePackage());

		ArrayList<AccessorDescription> currChoices= new ArrayList<AccessorDescription>();
		ArrayList<String> currLabels= new ArrayList<String>();

		currChoices.add(configured);
		currLabels.add(configured.getLabel());

		AccessorDescription[] choices= fAccessorChoices;
		if (choices == null) {
			choices= loadAccessorDescriptions();
		}

		for (int i= 0; i < choices.length; i++) {
			AccessorDescription curr= choices[i];
			if (!curr.equals(configured)) {
				currChoices.add(curr);
				currLabels.add(curr.getLabel());
			}
		}

		String[] labels= currLabels.toArray(new String[currLabels.size()]);
		fAccessorChoices= currChoices.toArray(new AccessorDescription[currChoices.size()]);

		fAccessorClassField.setItems(labels);
		fAccessorClassField.select(0);
	}


	private AccessorDescription[] loadAccessorDescriptions() {
		IDialogSettings section= JavaPlugin.getDefault().getDialogSettings().getSection(SETTINGS_NLS_ACCESSORS);
		if (section == null) {
			return new AccessorDescription[0];
		}
		ArrayList<AccessorDescription> res= new ArrayList<AccessorDescription>();
		for (int i= 0; i < SETTINGS_MAX_ENTRIES; i++) {
			IDialogSettings serializedDesc= section.getSection(String.valueOf(i));
			if (serializedDesc != null) {
				AccessorDescription accessor= AccessorDescription.deserialize(serializedDesc);
				if (accessor != null) {
					res.add(accessor);
				}
			}
		}
		return res.toArray(new AccessorDescription[res.size()]);
	}



	private void storeAccessorDescriptions() {
		if (fAccessorChoices == null) {
			return;
		}
		IDialogSettings dialogSettings= JavaPlugin.getDefault().getDialogSettings();
		IDialogSettings nlsSection= dialogSettings.getSection(SETTINGS_NLS_ACCESSORS);
		if (nlsSection == null) {
			nlsSection= dialogSettings.addNewSection(SETTINGS_NLS_ACCESSORS);
		}
		int nEntries= Math.min(SETTINGS_MAX_ENTRIES, fAccessorChoices.length);
		for (int i= 0; i < nEntries; i++) {
			IDialogSettings serializedDesc= nlsSection.addNewSection(String.valueOf(i));
			fAccessorChoices[i].serialize(serializedDesc);
		}
	}


	private void doConfigureButtonPressed() {
		NLSAccessorConfigurationDialog dialog= new NLSAccessorConfigurationDialog(getShell(), fNLSRefactoring);
		if (dialog.open() == Window.OK) {
			NLSSubstitution.updateSubtitutions(fSubstitutions, getProperties(fNLSRefactoring.getPropertyFileHandle()), fNLSRefactoring.getAccessorClassName());
			if (fIsEclipseNLS != null) {
				fIsEclipseNLS.setSelection(fNLSRefactoring.isEclipseNLS());
				fIsEclipseNLS.setEnabled(willCreateAccessorClass());
			}
			validateKeys(true);
			updateAccessorChoices();
		}
	}

	private Properties getProperties(IFile propertyFile) {
		Properties props= new Properties();
		try {
			if (propertyFile.exists()) {
				InputStream is= propertyFile.getContents();
				props.load(is);
				is.close();
			}
		} catch (Exception e) {
			// sorry no property
		}
		return props;
	}

	private void createTableViewer(Composite composite) {
		createTableComposite(composite);

		/*
		 * Feature of CellEditors - double click is ignored.
		 * The workaround is to register my own listener and force the desired
		 * behavior.
		 */
		fTableViewer= new TableViewer(fTable) {
			@Override
			protected void hookControl(Control control) {
				super.hookControl(control);
				((Table) control).addMouseListener(new MouseAdapter() {
					@Override
					public void mouseDoubleClick(MouseEvent e) {
						if (getTable().getSelection().length == 0)
							return;
						TableItem item= getTable().getSelection()[0];
						if (item.getBounds(STATE_PROP).contains(e.x, e.y)) {
							List<?> widgetSel= getSelectionFromWidget();
							if (widgetSel == null || widgetSel.size() != 1)
								return;
							NLSSubstitution substitution= (NLSSubstitution) widgetSel.get(0);
							Integer value= (Integer) getCellModifier().getValue(substitution, PROPERTIES[STATE_PROP]);
							int newValue= MultiStateCellEditor.getNextValue(NLSSubstitution.STATE_COUNT, value.intValue());
							getCellModifier().modify(item, PROPERTIES[STATE_PROP], new Integer(newValue));
						}
					}
				});
			}
		};

		fTableViewer.setUseHashlookup(true);

		final CellEditor[] editors= createCellEditors();
		fTableViewer.setCellEditors(editors);
		fTableViewer.setColumnProperties(PROPERTIES);
		fTableViewer.setCellModifier(new CellModifier());

		fTableViewer.setContentProvider(new IStructuredContentProvider() {
			public Object[] getElements(Object inputElement) {
				return fSubstitutions;
			}
			public void dispose() {
			}
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			}
		});
		fTableViewer.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				if (!fFilterCheckBox.getSelection()) {
					return true;
				}
				NLSSubstitution curr= (NLSSubstitution) element;
				return (curr.getInitialState() == NLSSubstitution.INTERNALIZED) || (curr.getInitialState() == NLSSubstitution.EXTERNALIZED && curr.getInitialValue() == null);
			}
		});


		fTableViewer.setLabelProvider(new NLSSubstitutionLabelProvider());
		fTableViewer.setInput(new Object());

		fTableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				ExternalizeWizardPage.this.selectionChanged(event);
			}
		});
	}

	private void createDefaultExternalization(NLSSubstitution[] substitutions) {
		for (int i= 0; i < substitutions.length; i++) {
			NLSSubstitution substitution= substitutions[i];
			if (substitution.getState() == NLSSubstitution.INTERNALIZED) {
				substitution.setState(NLSSubstitution.EXTERNALIZED);
				substitution.generateKey(substitutions, getProperties(fNLSRefactoring.getPropertyFileHandle()));
			}
		}
	}

	private CellEditor[] createCellEditors() {
		final CellEditor editors[]= new CellEditor[SIZE];
		editors[STATE_PROP]= new MultiStateCellEditor(fTable, NLSSubstitution.STATE_COUNT, NLSSubstitution.DEFAULT);
		editors[KEY_PROP]= new TextCellEditor(fTable);
		editors[VAL_PROP]= new TextCellEditor(fTable);
		return editors;
	}

	private void createSourceViewer(Composite parent) {
		Composite c= new Composite(parent, SWT.NONE);
		c.setLayoutData(new GridData(GridData.FILL_BOTH));
		GridLayout gl= new GridLayout();
		gl.marginHeight= 0;
		gl.marginWidth= 0;
		c.setLayout(gl);

		Label l= new Label(c, SWT.NONE);
		l.setText(NLSUIMessages.ExternalizeWizardPage_context);
		l.setLayoutData(new GridData());

		// source viewer
		JavaTextTools tools= JavaPlugin.getDefault().getJavaTextTools();
		int styles= SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION;
		IPreferenceStore store= JavaPlugin.getDefault().getCombinedPreferenceStore();
		fSourceViewer= new JavaSourceViewer(c, null, null, false, styles, store);
		fSourceViewer.configure(new JavaSourceViewerConfiguration(tools.getColorManager(), store, null, null));
		fSourceViewer.getControl().setFont(JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT));

		try {

			String contents= fCu.getBuffer().getContents();
			IDocument document= new Document(contents);
			tools.setupJavaDocumentPartitioner(document);

			fSourceViewer.setDocument(document);
			fSourceViewer.setEditable(false);

			GridData gd= new GridData(GridData.FILL_BOTH);
			gd.heightHint= convertHeightInCharsToPixels(10);
			gd.widthHint= convertWidthInCharsToPixels(40);
			fSourceViewer.getControl().setLayoutData(gd);

		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, NLSUIMessages.ExternalizeWizardPage_exception_title, NLSUIMessages.ExternalizeWizardPage_exception_message);
		}
	}

	private void createKeyPrefixField(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		GridLayout gl= new GridLayout();
		gl.numColumns= 2;
		gl.marginWidth= 0;
		composite.setLayout(gl);

		Label l= new Label(composite, SWT.NONE);
		l.setText(NLSUIMessages.ExternalizeWizardPage_common_prefix);
		l.setLayoutData(new GridData());

		fPrefixField= new Text(composite, SWT.SINGLE | SWT.BORDER);
		fPrefixField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		fPrefixField.setText(fNLSRefactoring.getPrefix());
		fPrefixField.selectAll();

		fPrefixField.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				fNLSRefactoring.setPrefix(fPrefixField.getText());
				validateKeys(true);
			}
		});
	}

	private void createIsEclipseNLSCheckbox(Composite parent) {
		if (fNLSRefactoring.isEclipseNLS() || fNLSRefactoring.isEclipseNLSAvailable()) {
			fIsEclipseNLS= new Button(parent, SWT.CHECK);
			fIsEclipseNLS.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			fIsEclipseNLS.setText(NLSUIMessages.ExternalizeWizardPage_isEclipseNLSCheckbox);
			fIsEclipseNLS.setSelection(fNLSRefactoring.isEclipseNLS());
			fIsEclipseNLS.setEnabled(willCreateAccessorClass());
			fIsEclipseNLS.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					super.widgetDefaultSelected(e);
					boolean isEclipseNLS= fIsEclipseNLS.getSelection();
					fNLSRefactoring.setIsEclipseNLS(isEclipseNLS);
					updatePrefix();
				}
			});
		}
	}

	private void validateKeys(boolean refreshTable) {
		RefactoringStatus status= new RefactoringStatus();
		checkInvalidKeys(status);
		checkDuplicateKeys(status);
		checkMissingKeys(status);
		setPageComplete(status);
		if (refreshTable)
			fTableViewer.refresh(true);
	}

	private void checkInvalidKeys(RefactoringStatus status) {
		for (int i= 0; i < fSubstitutions.length; i++) {
			if (!isKeyValid(fSubstitutions[i], status))
				return;
		}
	}

	private boolean isKeyValid(NLSSubstitution substitution, RefactoringStatus status) {
		if (substitution == null)
			return false;

		if (substitution.getState() != NLSSubstitution.EXTERNALIZED)
			return true;

		String key= substitution.getKey();

		if (fNLSRefactoring.isEclipseNLS()) {
			if (key == null || key.length() == 0 || !Character.isJavaIdentifierStart(key.charAt(0))) {
				if (status != null)
					status.addFatalError(NLSUIMessages.ExternalizeWizardPage_warning_EclipseNLS_keyInvalid);
				return false;
			}
			for (int i= 1, length= key.length(); i < length; i++) {
				if (!Character.isJavaIdentifierPart(key.charAt(i))) {
					if (status != null)
						status.addFatalError(NLSUIMessages.ExternalizeWizardPage_warning_EclipseNLS_keyInvalid);
					return false;
				}
			}
		} else {
			if (key == null || key.length() == 0) {
				if (status != null)
					status.addFatalError(NLSUIMessages.ExternalizeWizardPage_warning_keyInvalid);
				return false;
			}
			// validation so keys don't contain spaces
			for (int i= 0; i < key.length(); i++) {
				if (Character.isWhitespace(key.charAt(i))) {
					if (status != null)
						status.addFatalError(NLSUIMessages.ExternalizeWizardPage_warning_keyInvalid);
					return false;
				}
			}
		}

		return true;
	}

	private void checkDuplicateKeys(RefactoringStatus status) {
		for (int i= 0; i < fSubstitutions.length; i++) {
			NLSSubstitution substitution= fSubstitutions[i];
			if (conflictingKeys(substitution)) {
				status.addFatalError(NLSUIMessages.ExternalizeWizardPage_warning_conflicting);
				return;
			}
		}
	}

	private void checkMissingKeys(RefactoringStatus status) {
		for (int i= 0; i < fSubstitutions.length; i++) {
			NLSSubstitution substitution= fSubstitutions[i];
			if ((substitution.getValue() == null) && (substitution.getKey() != null)) {
				status.addWarning(NLSUIMessages.ExternalizeWizardPage_warning_keymissing);
				return;
			}
		}
	}

	private boolean conflictingKeys(NLSSubstitution substitution) {
		if (substitution.getState() == NLSSubstitution.EXTERNALIZED) {
			return substitution.isConflicting(fSubstitutions);
		}
		return false;
	}

	private void createTableComposite(Composite parent) {
		Composite comp= new Composite(parent, SWT.NONE);
		comp.setFont(parent.getFont());

		comp.setLayoutData(new GridData(GridData.FILL_BOTH));

		FormLayout fl= new FormLayout();
		fl.marginWidth= 0;
		fl.marginHeight= 0;
		comp.setLayout(fl);

		Label l= new Label(comp, SWT.NONE);
		l.setText(NLSUIMessages.ExternalizeWizardPage_strings_to_externalize);
		FormData formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		l.setLayoutData(formData);

		Control tableControl= createTable(comp);
		formData = new FormData();
		formData.top = new FormAttachment(l, 5);
		formData.left = new FormAttachment(0, 0);
		formData.right = new FormAttachment(100,0);
		formData.bottom = new FormAttachment(100,0);
		tableControl.setLayoutData(formData);

		fFilterCheckBox= new Button(comp, SWT.CHECK);
		fFilterCheckBox.setText(NLSUIMessages.ExternalizeWizardPage_filter_label);
		formData = new FormData();
		formData.right = new FormAttachment(100,0);
		fFilterCheckBox.setLayoutData(formData);
		fFilterCheckBox.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				doFilterCheckBoxPressed();
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		fFilterCheckBox.setSelection(hasNewOrMissingSubstitutions());
	}

	private boolean hasNewOrMissingSubstitutions() {
		for (int i= 0; i < fSubstitutions.length; i++) {
			NLSSubstitution curr= fSubstitutions[i];
			if (curr.getInitialState() == NLSSubstitution.INTERNALIZED) {
				return true;
			}
			if (curr.getInitialState() == NLSSubstitution.EXTERNALIZED && curr.getInitialValue() == null) {
				return true;
			}
		}
		return false;
	}

	/**
	 *
	 */
	protected void doFilterCheckBoxPressed() {
		fTableViewer.refresh();
	}

	private Control createTable(Composite parent) {
		Composite c= new Composite(parent, SWT.NONE);
		GridLayout gl= new GridLayout();
		gl.numColumns= 2;
		gl.marginWidth= 0;
		gl.marginHeight= 0;
		c.setLayout(gl);


		fTable= new Table(c, SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI | SWT.FULL_SELECTION | SWT.HIDE_SELECTION | SWT.BORDER);
		fTable.setFont(parent.getFont());

		GridData tableGD= new GridData(GridData.FILL_BOTH);
		tableGD.heightHint= SWTUtil.getTableHeightHint(fTable, ROW_COUNT);
		//tableGD.widthHint= 40;
		fTable.setLayoutData(tableGD);

		fTable.setLinesVisible(true);

		TableLayout layout= new TableLayout();
		fTable.setLayout(layout);
		fTable.setHeaderVisible(true);

		ColumnLayoutData[] columnLayoutData= new ColumnLayoutData[SIZE];
		columnLayoutData[STATE_PROP]= new ColumnPixelData(18, false, true);
		columnLayoutData[KEY_PROP]= new ColumnWeightData(40, true);
		columnLayoutData[VAL_PROP]= new ColumnWeightData(40, true);

		for (int i= 0; i < fgTitles.length; i++) {
			TableColumn tc= new TableColumn(fTable, SWT.NONE, i);
			tc.setText(fgTitles[i]);
			layout.addColumnData(columnLayoutData[i]);
			tc.setResizable(columnLayoutData[i].resizable);
		}

		createButtonComposite(c);
		return c;
	}

	private void createButtonComposite(Composite parent) {
		Composite buttonComp= new Composite(parent, SWT.NONE);
		GridLayout gl= new GridLayout();
		gl.marginHeight= 0;
		gl.marginWidth= 0;
		buttonComp.setLayout(gl);
		buttonComp.setLayoutData(new GridData(GridData.FILL_VERTICAL));

		SelectionAdapter adapter= new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleButtonPressed(e.widget);
			}
		};

		fExternalizeButton= createTaskButton(buttonComp, NLSUIMessages.ExternalizeWizardPage_Externalize_Selected, adapter);
		fIgnoreButton= createTaskButton(buttonComp, NLSUIMessages.ExternalizeWizardPage_Ignore_Selected, adapter);
		fInternalizeButton= createTaskButton(buttonComp, NLSUIMessages.ExternalizeWizardPage_Internalize_Selected, adapter);

		new Label(buttonComp, SWT.NONE); // separator

		fEditButton= createTaskButton(buttonComp, NLSUIMessages.ExternalizeWizardPage_Edit_key_and_value, adapter);
		fRevertButton= createTaskButton(buttonComp, NLSUIMessages.ExternalizeWizardPage_Revert_Selected, adapter);
		fRenameButton= createTaskButton(buttonComp, NLSUIMessages.ExternalizeWizardPage_Rename_Keys, adapter);

		fEditButton.setEnabled(false);
		fRenameButton.setEnabled(false);
		buttonComp.pack();
	}

	protected void handleButtonPressed(Widget widget) {
		fTableViewer.getTable().forceFocus(); // make sure cell editor is applied on all platforms, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=269611
		if (widget == fExternalizeButton) {
			setSelectedTasks(NLSSubstitution.EXTERNALIZED);
		} else if (widget == fIgnoreButton) {
			setSelectedTasks(NLSSubstitution.IGNORED);
		} else if (widget == fInternalizeButton) {
			setSelectedTasks(NLSSubstitution.INTERNALIZED);
		} else if (widget == fEditButton) {
			openEditButton(fTableViewer.getSelection());
		} else if (widget == fRevertButton) {
			revertStateOfSelection();
		} else if (widget == fRenameButton) {
			openRenameDialog();
		}
	}

	/**
	 *
	 */
	private void openRenameDialog() {
		IStructuredSelection sel= (IStructuredSelection) fTableViewer.getSelection();
		List<NLSSubstitution> elementsToRename= getExternalizedElements(sel);
		RenameKeysDialog dialog= new RenameKeysDialog(getShell(), elementsToRename);
		if (dialog.open() == Window.OK) {
			fTableViewer.refresh();
			updateButtonStates((IStructuredSelection) fTableViewer.getSelection());
		}
	}

	private void revertStateOfSelection() {
		List<?> selection= getSelectedTableEntries();
		for (Iterator<?> iter= selection.iterator(); iter.hasNext();) {
			NLSSubstitution substitution= (NLSSubstitution) iter.next();
			substitution.revert();
		}
		fTableViewer.refresh();
		updateButtonStates((IStructuredSelection) fTableViewer.getSelection());
	}

	private Button createTaskButton(Composite parent, String label, SelectionAdapter adapter) {
		Button button= new Button(parent, SWT.PUSH);
		button.setText(label);
		button.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		SWTUtil.setButtonDimensionHint(button);
		button.addSelectionListener(adapter);
		return button;
	}

	private void openEditButton(ISelection selection) {
		try {
			IStructuredSelection sel= (IStructuredSelection) fTableViewer.getSelection();
			NLSSubstitution substitution= (NLSSubstitution) sel.getFirstElement();
			if (substitution == null) {
				return;
			}

			NLSInputDialog dialog= new NLSInputDialog(getShell(), substitution);
			if (dialog.open() == Window.CANCEL)
				return;
			KeyValuePair kvPair= dialog.getResult();
			if (substitution.getState() == NLSSubstitution.EXTERNALIZED) {
				substitution.setKey(kvPair.getKey());
			}
			substitution.setValue(kvPair.getValue());
			validateKeys(false);
		} finally {
			fTableViewer.refresh();
			fTableViewer.getControl().setFocus();
			fTableViewer.setSelection(selection);
		}
	}

	private List<?> getSelectedTableEntries() {
		ISelection sel= fTableViewer.getSelection();
		if (sel instanceof IStructuredSelection)
			return((IStructuredSelection) sel).toList();
		else
			return Collections.EMPTY_LIST;
	}

	private void setSelectedTasks(int state) {
		Assert.isTrue(state == NLSSubstitution.EXTERNALIZED || state == NLSSubstitution.IGNORED || state == NLSSubstitution.INTERNALIZED);
		List<?> selected= getSelectedTableEntries();
		String[] props= new String[]{PROPERTIES[STATE_PROP]};
		for (Iterator<?> iter= selected.iterator(); iter.hasNext();) {
			NLSSubstitution substitution= (NLSSubstitution) iter.next();
			substitution.setState(state);
			if ((substitution.getState() == NLSSubstitution.EXTERNALIZED) && substitution.hasStateChanged()) {
				substitution.generateKey(fSubstitutions, getProperties(fNLSRefactoring.getPropertyFileHandle()));
			}
		}
		fTableViewer.update(selected.toArray(), props);
		fTableViewer.getControl().setFocus();
		updateButtonStates((IStructuredSelection) fTableViewer.getSelection());
	}

	private void selectionChanged(SelectionChangedEvent event) {
		IStructuredSelection selection= (IStructuredSelection) event.getSelection();
		updateButtonStates(selection);
		updateSourceView(selection);
	}

	private void updateSourceView(IStructuredSelection selection) {
		NLSSubstitution first= (NLSSubstitution) selection.getFirstElement();
		if (first != null) {
			Region region= first.getNLSElement().getPosition();
			fSourceViewer.setSelectedRange(region.getOffset(), region.getLength());
			fSourceViewer.revealRange(region.getOffset(), region.getLength());
		}
	}

	private void updateButtonStates(IStructuredSelection selection) {
		fExternalizeButton.setEnabled(true);
		fIgnoreButton.setEnabled(true);
		fInternalizeButton.setEnabled(true);
		fRevertButton.setEnabled(true);

		if (containsOnlyElementsOfSameState(NLSSubstitution.EXTERNALIZED, selection)) {
			fExternalizeButton.setEnabled(false);
		}

		if (containsOnlyElementsOfSameState(NLSSubstitution.IGNORED, selection)) {
			fIgnoreButton.setEnabled(false);
		}

		if (containsOnlyElementsOfSameState(NLSSubstitution.INTERNALIZED, selection)) {
			fInternalizeButton.setEnabled(false);
		}

		if (!containsElementsWithChange(selection)) {
			fRevertButton.setEnabled(false);
		}

		fRenameButton.setEnabled(getExternalizedElements(selection).size() > 1);
		fEditButton.setEnabled(selection.size() == 1);
	}

	private boolean containsElementsWithChange(IStructuredSelection selection) {
		for (Iterator<?> iter= selection.iterator(); iter.hasNext();) {
			NLSSubstitution substitution= (NLSSubstitution) iter.next();
			if (substitution.hasPropertyFileChange() || substitution.hasSourceChange()) {
				return true;
			}
		}
		return false;
	}

	private List<NLSSubstitution> getExternalizedElements(IStructuredSelection selection) {
		ArrayList<NLSSubstitution> res= new ArrayList<NLSSubstitution>();
		for (Iterator<?> iter= selection.iterator(); iter.hasNext();) {
			NLSSubstitution substitution= (NLSSubstitution) iter.next();
			if (substitution.getState() == NLSSubstitution.EXTERNALIZED && !substitution.hasStateChanged()) {
				res.add(substitution);
			}
		}
		return res;
	}

	private boolean containsOnlyElementsOfSameState(int state, IStructuredSelection selection) {
		for (Iterator<?> iter= selection.iterator(); iter.hasNext();) {
			NLSSubstitution substitution= (NLSSubstitution) iter.next();
			if (substitution.getState() != state) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean performFinish() {
		return super.performFinish();
	}

	@Override
	public IWizardPage getNextPage() {
		return super.getNextPage();
	}

	@Override
	public void dispose() {
		storeAccessorDescriptions();
		//widgets will be disposed. only need to null'em
		fPrefixField= null;
		fSourceViewer= null;
		fTable= null;
		fTableViewer= null;
		fEditButton= null;
		super.dispose();
	}

	/**
	 * Updates the prefix.
	 *
	 * @since 3.4
	 */
	private void updatePrefix() {
		if (fNLSRefactoring.isEclipseNLS()) {
			fNLSRefactoring.setPrefix(fNLSRefactoring.getPrefix().replace('.', '_'));
		} else {
			fNLSRefactoring.setPrefix(fNLSRefactoring.getPrefix().replace('_', '.'));
		}
		fPrefixField.setText(fNLSRefactoring.getPrefix());
		validateKeys(true);
	}
}

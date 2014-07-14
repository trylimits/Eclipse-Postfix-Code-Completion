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
package org.eclipse.jdt.internal.ui.refactoring.nls;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IFile;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.progress.IProgressService;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;

import org.eclipse.jdt.internal.corext.refactoring.nls.NLSRefactoring;
import org.eclipse.jdt.internal.corext.util.JavaConventionsUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.dialogs.FilteredTypesSelectionDialog;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.dialogs.TextFieldNavigationHandler;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.Separator;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringDialogField;

public class NLSAccessorConfigurationDialog extends StatusDialog {


	private SourceFirstPackageSelectionDialogField fResourceBundlePackage;
	private StringButtonDialogField fResourceBundleFile;
	private SourceFirstPackageSelectionDialogField fAccessorPackage;
	private StringDialogField fAccessorClassName;
	private StringDialogField fSubstitutionPattern;

	private NLSRefactoring fRefactoring;

	private IStatus[] fStati;

	private static final int IDX_ACCESSOR_CLASS= 0;
	private static final int IDX_ACCESSOR_PACKAGE= 1;
	private static final int IDX_SUBST_PATTERN= 2;
	private static final int IDX_BUNDLE_NAME= 3;
	private static final int IDX_BUNDLE_PACKAGE= 4;

	private class AccessorAdapter implements IDialogFieldListener, IStringButtonAdapter {
		public void dialogFieldChanged(DialogField field) {
			validateAll();
		}

		public void changeControlPressed(DialogField field) {
			if (field == fResourceBundleFile) {
				browseForPropertyFile();
			} else if (field == fAccessorClassName) {
				browseForAccessorClass();
			}
		}
	}


	public NLSAccessorConfigurationDialog(Shell parent, NLSRefactoring refactoring) {
		super(parent);
		fRefactoring= refactoring;
		fStati= new IStatus[] { StatusInfo.OK_STATUS, StatusInfo.OK_STATUS, StatusInfo.OK_STATUS, StatusInfo.OK_STATUS, StatusInfo.OK_STATUS };

		setTitle(NLSUIMessages.NLSAccessorConfigurationDialog_title);

		AccessorAdapter updateListener= new AccessorAdapter();

		ICompilationUnit cu= refactoring.getCu();

		fAccessorPackage= new SourceFirstPackageSelectionDialogField(NLSUIMessages.NLSAccessorConfigurationDialog_accessor_path,
				NLSUIMessages.NLSAccessorConfigurationDialog_accessor_package,
				NLSUIMessages.NLSAccessorConfigurationDialog_browse1,
				NLSUIMessages.NLSAccessorConfigurationDialog_browse2,
				NLSUIMessages.NLSAccessorConfigurationDialog_default_package,
				NLSUIMessages.NLSAccessorConfigurationDialog_accessor_dialog_title,
				NLSUIMessages.NLSAccessorConfigurationDialog_accessor_dialog_message,
				NLSUIMessages.NLSAccessorConfigurationDialog_accessor_dialog_emtpyMessage,
				cu, updateListener, refactoring.getAccessorClassPackage());

		fAccessorClassName= createStringButtonField(NLSUIMessages.NLSAccessorConfigurationDialog_className,
				NLSUIMessages.NLSAccessorConfigurationDialog_browse6, updateListener);
		fSubstitutionPattern= createStringField(NLSUIMessages.NLSAccessorConfigurationDialog_substitutionPattern, updateListener);

		fResourceBundlePackage= new SourceFirstPackageSelectionDialogField(NLSUIMessages.NLSAccessorConfigurationDialog_property_path,
				NLSUIMessages.NLSAccessorConfigurationDialog_property_package,
				NLSUIMessages.NLSAccessorConfigurationDialog_browse3,
				NLSUIMessages.NLSAccessorConfigurationDialog_browse4,
				NLSUIMessages.NLSAccessorConfigurationDialog_default_package,
				NLSUIMessages.NLSAccessorConfigurationDialog_property_dialog_title,
				NLSUIMessages.NLSAccessorConfigurationDialog_property_dialog_message,
				NLSUIMessages.NLSAccessorConfigurationDialog_property_dialog_emptyMessage,
				cu, updateListener, fRefactoring.getResourceBundlePackage());

		fResourceBundleFile= createStringButtonField(NLSUIMessages.NLSAccessorConfigurationDialog_property_file_name,
				NLSUIMessages.NLSAccessorConfigurationDialog_browse5, updateListener);

		initFields();
	}

	/*
	 * @see org.eclipse.jface.dialogs.Dialog#isResizable()
	 * @since 3.4
	 */
	@Override
	protected boolean isResizable() {
		return true;
	}

	private void initFields() {
		initAccessorClassFields();
		String resourceBundleName= fRefactoring.getResourceBundleName();
		fResourceBundleFile.setText(resourceBundleName != null ? resourceBundleName : (NLSRefactoring.DEFAULT_PROPERTY_FILENAME + NLSRefactoring.PROPERTY_FILE_EXT));
	}

	private void initAccessorClassFields() {
		String accessorClassName= fRefactoring.getAccessorClassName();

		if (accessorClassName == null) {
			accessorClassName= NLSRefactoring.DEFAULT_ACCESSOR_CLASSNAME;
		}
		fAccessorClassName.setText(accessorClassName);

		fSubstitutionPattern.setText(fRefactoring.getSubstitutionPattern());
	}

	@Override
	protected Control createDialogArea(Composite ancestor) {
		Composite parent= (Composite) super.createDialogArea(ancestor);

		final int nOfColumns= 4;

		initializeDialogUnits(ancestor);

		GridLayout layout= (GridLayout) parent.getLayout();
		layout.numColumns= nOfColumns;
		parent.setLayout(layout);

		createAccessorPart(parent, nOfColumns, convertWidthInCharsToPixels(40));

		Separator s= new Separator(SWT.SEPARATOR | SWT.HORIZONTAL);
		s.doFillIntoGrid(parent, nOfColumns);

		createPropertyPart(parent, nOfColumns, convertWidthInCharsToPixels(40));

		Dialog.applyDialogFont(parent);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, IJavaHelpContextIds.EXTERNALIZE_WIZARD_PROPERTIES_FILE_PAGE);
		validateAll();
		return parent;
	}


	private void createAccessorPart(Composite parent, final int nOfColumns, int textWidth) {
		createLabel(parent, NLSUIMessages.NLSAccessorConfigurationDialog_resourceBundle_title, nOfColumns);
		fAccessorPackage.createControl(parent, nOfColumns, textWidth);

		fAccessorClassName.doFillIntoGrid(parent, nOfColumns);
		Text accessorClassText= fAccessorClassName.getTextControl(null);
		LayoutUtil.setWidthHint(accessorClassText, convertWidthInCharsToPixels(60));
		TextFieldNavigationHandler.install(accessorClassText);

		fSubstitutionPattern.doFillIntoGrid(parent, nOfColumns);
		Text substitutionPatternText= fSubstitutionPattern.getTextControl(null);
		LayoutUtil.setWidthHint(substitutionPatternText, convertWidthInCharsToPixels(60));
		TextFieldNavigationHandler.install(substitutionPatternText);

		fSubstitutionPattern.setEnabled(!fRefactoring.isEclipseNLS());
	}

	private void createPropertyPart(Composite parent, final int nOfColumns, final int textWidth) {
		createLabel(parent, NLSUIMessages.NLSAccessorConfigurationDialog_property_location, nOfColumns);
		fResourceBundlePackage.createControl(parent, nOfColumns, textWidth);

		fResourceBundleFile.doFillIntoGrid(parent, nOfColumns);
		LayoutUtil.setWidthHint(fResourceBundleFile.getTextControl(null), convertWidthInCharsToPixels(60));
	}

	private void createLabel(Composite parent, final String text, final int N_OF_COLUMNS) {
		Separator label= new Separator(SWT.NONE);
		((Label) label.getSeparator(parent)).setText(text);
		GC gc= new GC(parent);
		int height= gc.stringExtent(text).y;
		gc.dispose();
		label.doFillIntoGrid(parent, N_OF_COLUMNS, height);
	}

	private void browseForPropertyFile() {
		ElementListSelectionDialog dialog= new ElementListSelectionDialog(getShell(), new JavaElementLabelProvider());
		dialog.setIgnoreCase(false);
		dialog.setTitle(NLSUIMessages.NLSAccessorConfigurationDialog_Property_File_Selection);
		dialog.setMessage(NLSUIMessages.NLSAccessorConfigurationDialog_Choose_the_property_file);
		dialog.setElements(createFileListInput());
		dialog.setFilter('*' + NLSRefactoring.PROPERTY_FILE_EXT);
		if (dialog.open() == Window.OK) {
			IFile selectedFile= (IFile) dialog.getFirstResult();
			if (selectedFile != null)
				fResourceBundleFile.setText(selectedFile.getName());
		}
	}

	protected void browseForAccessorClass() {
		IProgressService service= PlatformUI.getWorkbench().getProgressService();
		IPackageFragmentRoot root= fAccessorPackage.getSelectedFragmentRoot();

		IJavaSearchScope scope= root != null ? SearchEngine.createJavaSearchScope(new IJavaElement[] { root }) : SearchEngine.createWorkspaceScope();

		FilteredTypesSelectionDialog  dialog= new FilteredTypesSelectionDialog (getShell(), false,
			service, scope, IJavaSearchConstants.CLASS);
		dialog.setTitle(NLSUIMessages.NLSAccessorConfigurationDialog_Accessor_Selection);
		dialog.setMessage(NLSUIMessages.NLSAccessorConfigurationDialog_Choose_the_accessor_file);
		dialog.setInitialPattern("*Messages"); //$NON-NLS-1$
		if (dialog.open() == Window.OK) {
			IType selectedType= (IType) dialog.getFirstResult();
			if (selectedType != null) {
				fAccessorClassName.setText(selectedType.getElementName());
				fAccessorPackage.setSelected(selectedType.getPackageFragment());
			}
		}


	}

	private Object[] createFileListInput() {
		try {

			IPackageFragment fPkgFragment= fResourceBundlePackage.getSelected();
			if (fPkgFragment == null)
				return new Object[0];
			List<Object> result= new ArrayList<Object>(1);
			Object[] nonjava= fPkgFragment.getNonJavaResources();
			for (int i= 0; i < nonjava.length; i++) {
				if (isPropertyFile(nonjava[i]))
					result.add(nonjava[i]);
			}
			return result.toArray();

		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, NLSUIMessages.NLSAccessorConfigurationDialog_externalizing, NLSUIMessages .NLSAccessorConfigurationDialog_exception);
			return new Object[0];
		}
	}

	private static boolean isPropertyFile(Object o) {
		if (!(o instanceof IFile))
			return false;
		IFile file= (IFile) o;
		return (NLSRefactoring.PROPERTY_FILE_EXT.equals('.' + file.getFileExtension()));
	}

	/**
	 * checks all entered values delegates to the specific validate methods these methods
	 * update the refactoring
	 */
	private void validateAll() {
		validateSubstitutionPattern();

		validateAccessorClassName();
		checkPackageFragment();

		validatePropertyFilename();
		validatePropertyPackage();

		updateStatus(StatusUtil.getMostSevere(fStati));
	}

	private void validateAccessorClassName() {
		String className= fAccessorClassName.getText();

		IStatus status= JavaConventionsUtil.validateJavaTypeName(className, fAccessorPackage.getSelectedFragmentRoot());
		if (status.getSeverity() == IStatus.ERROR) {
			setInvalid(IDX_ACCESSOR_CLASS, status.getMessage());
			return;
		}

		if (className.indexOf('.') != -1) {
			setInvalid(IDX_ACCESSOR_CLASS, NLSUIMessages.NLSAccessorConfigurationDialog_no_dot);
			return;
		}

		setValid(IDX_ACCESSOR_CLASS);
	}

	private void validatePropertyFilename() {
		String fileName= fResourceBundleFile.getText();
		if ((fileName == null) || (fileName.length() == 0)) {
			setInvalid(IDX_BUNDLE_NAME, NLSUIMessages.NLSAccessorConfigurationDialog_enter_name);
			return;
		}

		if (!fileName.endsWith(NLSRefactoring.PROPERTY_FILE_EXT)) {
			setInvalid(IDX_BUNDLE_NAME, Messages.format(NLSUIMessages.NLSAccessorConfigurationDialog_file_name_must_end, NLSRefactoring.PROPERTY_FILE_EXT));
			return;
		}

		setValid(IDX_BUNDLE_NAME);
	}

	private void validatePropertyPackage() {

		IPackageFragmentRoot root= fResourceBundlePackage.getSelectedFragmentRoot();
		if ((root == null) || !root.exists()) {
			setInvalid(IDX_BUNDLE_PACKAGE, NLSUIMessages.NLSAccessorConfigurationDialog_property_package_root_invalid);
			return;
		}

		IPackageFragment fragment= fResourceBundlePackage.getSelected();
		if ((fragment == null) || !fragment.exists()) {
			setInvalid(IDX_BUNDLE_PACKAGE, NLSUIMessages.NLSAccessorConfigurationDialog_property_package_invalid);
			return;
		}

		String pkgName= fragment.getElementName();

		IStatus status= JavaConventionsUtil.validatePackageName(pkgName, root);
		if ((pkgName.length() > 0) && (status.getSeverity() == IStatus.ERROR)) {
			setInvalid(IDX_BUNDLE_PACKAGE, status.getMessage());
			return;
		}

		IPath pkgPath= new Path(pkgName.replace('.', IPath.SEPARATOR)).makeRelative();

		IJavaProject project= fRefactoring.getCu().getJavaProject();
		try {
			IJavaElement element= project.findElement(pkgPath);
			if (element == null || !element.exists()) {
				setInvalid(IDX_BUNDLE_PACKAGE, NLSUIMessages.NLSAccessorConfigurationDialog_must_exist);
				return;
			}
			IPackageFragment fPkgFragment= (IPackageFragment) element;
			if (!PackageBrowseAdapter.canAddPackage(fPkgFragment)) {
				setInvalid(IDX_BUNDLE_PACKAGE, NLSUIMessages.NLSAccessorConfigurationDialog_incorrect_package);
				return;
			}
			if (!PackageBrowseAdapter.canAddPackageRoot((IPackageFragmentRoot) fPkgFragment.getParent())) {
				setInvalid(IDX_BUNDLE_PACKAGE, NLSUIMessages.NLSAccessorConfigurationDialog_incorrect_package);
				return;
			}
		} catch (JavaModelException e) {
			setInvalid(IDX_BUNDLE_PACKAGE, e.getStatus().getMessage());
			return;
		}

		setValid(IDX_BUNDLE_PACKAGE);
	}

	private void checkPackageFragment() {
		IPackageFragmentRoot root= fAccessorPackage.getSelectedFragmentRoot();
		if ((root == null) || !root.exists()) {
			setInvalid(IDX_ACCESSOR_PACKAGE, NLSUIMessages.NLSAccessorConfigurationDialog_accessor_package_root_invalid);
			return;
		}

		IPackageFragment fragment= fAccessorPackage.getSelected();
		if ((fragment == null) || !fragment.exists()) {
			setInvalid(IDX_ACCESSOR_PACKAGE, NLSUIMessages.NLSAccessorConfigurationDialog_accessor_package_invalid);
			return;
		}
		setValid(IDX_ACCESSOR_PACKAGE);
	}

	private void validateSubstitutionPattern() {
		if ((fSubstitutionPattern.getText() == null) || (fSubstitutionPattern.getText().length() == 0)) {
			setInvalid(IDX_SUBST_PATTERN, NLSUIMessages.NLSAccessorConfigurationDialog_substitution_pattern_missing);
		} else {
			setValid(IDX_SUBST_PATTERN);
		}
	}

	private void setInvalid(int idx, String msg) {
		fStati[idx]= new StatusInfo(IStatus.ERROR, msg);
	}

	private void setValid(int idx) {
		fStati[idx]= StatusInfo.OK_STATUS;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#okPressed()
	 */
	@Override
	protected void okPressed() {
		updateRefactoring();
		super.okPressed();
	}


	void updateRefactoring() {
		fRefactoring.setAccessorClassPackage(fAccessorPackage.getSelected());
		fRefactoring.setAccessorClassName(fAccessorClassName.getText());

		fRefactoring.setResourceBundleName(fResourceBundleFile.getText());
		fRefactoring.setResourceBundlePackage(fResourceBundlePackage.getSelected());

		if (!fRefactoring.isEclipseNLS())
			fRefactoring.setSubstitutionPattern(fSubstitutionPattern.getText());

		fRefactoring.setIsEclipseNLS(fRefactoring.detectIsEclipseNLS());
	}

	private StringDialogField createStringField(String label, AccessorAdapter updateListener) {
		StringDialogField field= new StringDialogField();
		field.setDialogFieldListener(updateListener);
		field.setLabelText(label);
		return field;
	}

	private StringButtonDialogField createStringButtonField(String label, String button, AccessorAdapter adapter) {
		StringButtonDialogField field= new StringButtonDialogField(adapter);
		field.setDialogFieldListener(adapter);
		field.setLabelText(label);
		field.setButtonLabel(button);
		return field;
	}

}

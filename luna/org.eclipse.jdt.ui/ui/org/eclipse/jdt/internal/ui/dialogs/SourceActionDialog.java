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
package org.eclipse.jdt.internal.ui.dialogs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TreeItem;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.resource.StringConverter;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.dialogs.CheckedTreeSelectionDialog;
import org.eclipse.ui.dialogs.PreferencesUtil;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.Modifier;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.preferences.CodeTemplatePreferencePage;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.refactoring.IVisibilityChangeListener;
import org.eclipse.jdt.internal.ui.util.SWTUtil;

/**
 * An advanced version of CheckedTreeSelectionDialog with right-side button layout and
 * extra buttons and composites.
 */
public class SourceActionDialog extends CheckedTreeSelectionDialog {

	private static final String SETTINGS_SECTION_METHODS= "SourceActionDialog.methods"; //$NON-NLS-1$

	private static final String SETTINGS_SECTION_CONSTRUCTORS= "SourceActionDialog.constructors"; //$NON-NLS-1$

	/**
	 * Valid values are:
	 * <ul>
	 * <li>{@link #INSERT_FIRST_INDEX}</li>
	 * <li>{@link #INSERT_LAST_INDEX}</li>
	 * <li>{@link #INSERT_POSITION_FROM_EDITOR}</li>
	 * </ul>
	 * 
	 * <strong>Note:</strong> Since 3.4.2 the key is "InsertPosition.v2" instead of "InsertPosition"
	 * in order to reset to the new defaults.
	 */
	private static final String SETTINGS_INSERT_POSITION= "InsertPosition.v2"; //$NON-NLS-1$

	// Valid values for the insert position:
	private static final int INSERT_FIRST_INDEX= 0;
	private static final int INSERT_LAST_INDEX= 1;
	private static final int INSERT_POSITION_FROM_EDITOR= 2;

	private static final String SETTINGS_VISIBILITY_MODIFIER= "VisibilityModifier"; //$NON-NLS-1$

	private static final String SETTINGS_FINAL_MODIFIER= "FinalModifier"; //$NON-NLS-1$

	private static final String SETTINGS_SYNCHRONIZED_MODIFIER= "SynchronizedModifier"; //$NON-NLS-1$

	private static final String SETTINGS_COMMENTS= "Comments"; //$NON-NLS-1$


	private List<IJavaElement> fInsertPositions;
	private List<String> fLabels;

	/**
	 * One of:
	 * <ul>
	 * <li>{@link #INSERT_FIRST_INDEX}</li>
	 * <li>{@link #INSERT_LAST_INDEX}</li>
	 * <li><code>i + 2</code>, where <code>i</code> is the index of the member after which new
	 * members should be inserted</li>
	 * </ul>
	 */
	private int fCurrentPositionIndex;
	private IDialogSettings fSettings;
	private CompilationUnitEditor fEditor;
	private ITreeContentProvider fContentProvider;
	private boolean fGenerateComment;
	private IType fType;
	private int fWidth, fHeight;
	private String fCommentString;
	private boolean fEnableInsertPosition= true;
	private int fVisibilityModifier;
	private boolean fFinal;
	private boolean fSynchronized;
	private Composite fInsertPositionComposite;
	private boolean fHasUserChangedPositionIndex;
	private List<Integer> fAllowedVisibilities;


	public SourceActionDialog(Shell parent, ILabelProvider labelProvider, ITreeContentProvider contentProvider, CompilationUnitEditor editor, IType type, boolean isConstructor) throws JavaModelException {
		super(parent, labelProvider, contentProvider);
		fEditor= editor;
		fContentProvider= contentProvider;
		fType= type;
		fCommentString= ActionMessages.SourceActionDialog_createMethodComment;
		setEmptyListMessage(ActionMessages.SourceActionDialog_no_entries);

		fWidth= 60;
		fHeight= 18;

		boolean generateCommentsDefault= JavaPreferencesSettings.getCodeGenerationSettings(type.getJavaProject()).createComments;

		IDialogSettings dialogSettings= JavaPlugin.getDefault().getDialogSettings();
		String sectionId= isConstructor ? SETTINGS_SECTION_CONSTRUCTORS : SETTINGS_SECTION_METHODS;
		fSettings= dialogSettings.getSection(sectionId);
		if (fSettings == null)  {
			fSettings= dialogSettings.addNewSection(sectionId);
		}

		fVisibilityModifier= asInt(fSettings.get(SETTINGS_VISIBILITY_MODIFIER), Modifier.PUBLIC);
		fFinal= asBoolean(fSettings.get(SETTINGS_FINAL_MODIFIER), false);
		fSynchronized= asBoolean(fSettings.get(SETTINGS_SYNCHRONIZED_MODIFIER), false);
		fGenerateComment= asBoolean(fSettings.get(SETTINGS_COMMENTS), generateCommentsDefault);
		fInsertPositions= new ArrayList<IJavaElement>();
		fLabels= new ArrayList<String>();

		IJavaElement[] members= fType.getChildren();

		fInsertPositions.add(members.length > 0 ? members[0]: null); // first
		fInsertPositions.add(null); // last

		fLabels.add(ActionMessages.SourceActionDialog_first);
		fLabels.add(ActionMessages.SourceActionDialog_last);

		for (int i = 0; i < members.length; i++) {
			IJavaElement curr= members[i];
			String methodLabel= JavaElementLabels.getElementLabel(curr, JavaElementLabels.M_PARAMETER_TYPES);
			fLabels.add(Messages.format(ActionMessages.SourceActionDialog_after, methodLabel));
			fInsertPositions.add(findSibling(curr, members));
		}
		fInsertPositions.add(null);

		int storedPositionIndex= asInt(fSettings.get(SETTINGS_INSERT_POSITION), INSERT_POSITION_FROM_EDITOR);
		if (storedPositionIndex == INSERT_POSITION_FROM_EDITOR) {
			int indexAfterCursor= getElementAfterCursorPosition(fEditor, members);
			if (indexAfterCursor == -1)
				fCurrentPositionIndex= isConstructor ? INSERT_FIRST_INDEX : INSERT_LAST_INDEX;
			else if (indexAfterCursor == 0)
				fCurrentPositionIndex= INSERT_FIRST_INDEX;
			else if (indexAfterCursor > 0)
				fCurrentPositionIndex= indexAfterCursor + 1;
		} else
			fCurrentPositionIndex= storedPositionIndex <= INSERT_FIRST_INDEX ? INSERT_FIRST_INDEX : INSERT_LAST_INDEX; // handle illegal values already present in the dialog store
	}

	protected IType getType() {
		return fType;
	}

	protected boolean asBoolean(String string, boolean defaultValue) {
		if (string != null) {
			return StringConverter.asBoolean(string, defaultValue);
		}
		return defaultValue;
	}

	protected int asInt(String string, int defaultValue) {
		if (string != null) {
			return StringConverter.asInt(string, defaultValue);
		}
		return defaultValue;
	}

	private IJavaElement findSibling(IJavaElement curr, IJavaElement[] members) throws JavaModelException {
		IJavaElement res= null;
		int methodStart= ((IMember) curr).getSourceRange().getOffset();
		for (int i= members.length-1; i >= 0; i--) {
			IMember member= (IMember) members[i];
			if (methodStart >= member.getSourceRange().getOffset()) {
				return res;
			}
			res= member;
		}
		return null;
	}

	private int getElementAfterCursorPosition(CompilationUnitEditor editor, IJavaElement[] members) throws JavaModelException {
		if (editor == null) {
			return -1;
		}
		int offset= ((ITextSelection) editor.getSelectionProvider().getSelection()).getOffset();

		for (int i= 0; i < members.length; i++) {
			IMember curr= (IMember) members[i];
			ISourceRange range= curr.getSourceRange();
			if (offset < range.getOffset()) {
				return i;
			}
		}
		return members.length;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#close()
	 */
	@Override
	public boolean close() {
		if (fAllowedVisibilities != null && fAllowedVisibilities.size() > 0 && fAllowedVisibilities.contains(new Integer(fVisibilityModifier)))
			fSettings.put(SETTINGS_VISIBILITY_MODIFIER, StringConverter.asString(fVisibilityModifier));
		fSettings.put(SETTINGS_FINAL_MODIFIER, StringConverter.asString(fFinal));
		fSettings.put(SETTINGS_SYNCHRONIZED_MODIFIER, StringConverter.asString(fSynchronized));
		fSettings.put(SETTINGS_COMMENTS, fGenerateComment);

		if (fHasUserChangedPositionIndex) {
			if (fCurrentPositionIndex == INSERT_FIRST_INDEX || fCurrentPositionIndex == INSERT_LAST_INDEX)
				fSettings.put(SETTINGS_INSERT_POSITION, StringConverter.asString(fCurrentPositionIndex));
			else if (fEditor != null)
				fSettings.put(SETTINGS_INSERT_POSITION, StringConverter.asString(INSERT_POSITION_FROM_EDITOR));
		}

		return super.close();
	}

	/**
	 * Sets the size of the tree in unit of characters.
	 * @param width  the width of the tree.
	 * @param height the height of the tree.
	 */
	@Override
	public void setSize(int width, int height) {
		fWidth = width;
		fHeight = height;
	}


	/***
	 * Set insert position valid input is 0 for the first position, 1 for the last position,
	 * i + 2 for after member with index i.
	 * @param insert the insert position
	 * @see #fCurrentPositionIndex
	 */
	private void setInsertPosition(int insert) {
		fCurrentPositionIndex= insert;
	}

	public void setCommentString(String string) {
		fCommentString= string;
	}

	protected ITreeContentProvider getContentProvider() {
		return fContentProvider;
	}

	public boolean getGenerateComment() {
		return fGenerateComment;
	}

	public int getVisibilityModifier() {
		return fVisibilityModifier;
	}

	public void setGenerateComment(boolean comment) {
		fGenerateComment= comment;
	}

	protected void setVisibility(int visibility) {
		fVisibilityModifier= visibility;
	}

	private void setFinal(boolean value) {
		fFinal= value;
	}

	private void setSynchronized(boolean value)  {
		fSynchronized= value;
	}

	@Override
	protected Composite createSelectionButtons(Composite composite) {
		Composite buttonComposite= super.createSelectionButtons(composite);

		GridLayout layout = new GridLayout();
		buttonComposite.setLayout(layout);

		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.numColumns= 1;

		return buttonComposite;
	}

	@Override
	protected void buttonPressed(int buttonId) {
		switch (buttonId) {
			case IDialogConstants.OK_ID: {
				okPressed();
				break;
			}
			case IDialogConstants.CANCEL_ID: {
				cancelPressed();
				break;
			}
		}
	}

	/**
	 * Returns a composite containing the label created at the top of the dialog. Returns null if there is the
	 * message for the label is null.
	 * @param composite the parent composite
	 * @return the label
	 */
	@Override
	protected Label createMessageArea(Composite composite) {
		if (getMessage() != null) {
			Label label = new Label(composite,SWT.NONE);
			label.setText(getMessage());
			label.setFont(composite.getFont());
			return label;
		}
		return null;
	}

	/*
	 * @see Dialog#createDialogArea(Composite)
	 */
	@Override
	protected Control createDialogArea(Composite parent) {
		initializeDialogUnits(parent);

		Composite composite= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		GridData gd= null;

		layout.marginHeight= convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		layout.marginWidth= convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
		layout.verticalSpacing=	convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		layout.horizontalSpacing= convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		composite.setLayout(layout);

		Label messageLabel = createMessageArea(composite);
		if (messageLabel != null) {
			gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
			gd.horizontalSpan= 2;
			messageLabel.setLayoutData(gd);
		}

		Composite inner= new Composite(composite, SWT.NONE);
		GridLayout innerLayout = new GridLayout();
		innerLayout.numColumns= 2;
		innerLayout.marginHeight= 0;
		innerLayout.marginWidth= 0;
		inner.setLayout(innerLayout);
		inner.setFont(parent.getFont());

		CheckboxTreeViewer treeViewer= createTreeViewer(inner);
		gd= new GridData(GridData.FILL_BOTH);
		gd.widthHint = convertWidthInCharsToPixels(fWidth);
		gd.heightHint = convertHeightInCharsToPixels(fHeight);
		treeViewer.getControl().setLayoutData(gd);

		Composite buttonComposite= createSelectionButtons(inner);
		gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL);
		buttonComposite.setLayoutData(gd);

		gd= new GridData(GridData.FILL_BOTH);
		inner.setLayoutData(gd);

		fInsertPositionComposite= createInsertPositionCombo(composite);
		fInsertPositionComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Composite commentComposite= createCommentSelection(composite);
		commentComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Control linkControl= createLinkControl(composite);
		if (linkControl != null)
			linkControl.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		gd= new GridData(GridData.FILL_BOTH);
		composite.setLayoutData(gd);

		applyDialogFont(composite);

		return composite;
	}

	/**
	 * Clients override to provide link control
	 *
	 * @param composite the parent composite
	 * @return the created control or <code>null</code>
	 */
	protected Control createLinkControl(Composite composite) {
		return null; // No link as default
	}

	protected void openCodeTempatePage(String id) {
		HashMap<String, String> arg= new HashMap<String, String>();
		arg.put(CodeTemplatePreferencePage.DATA_SELECT_TEMPLATE, id);
		PreferencesUtil.createPropertyDialogOn(getShell(), fType.getJavaProject().getProject(), CodeTemplatePreferencePage.PROP_ID, null, arg).open();
	}


	protected Composite createCommentSelection(Composite composite) {
		Composite commentComposite = new Composite(composite, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		commentComposite.setLayout(layout);
		commentComposite.setFont(composite.getFont());

		Button commentButton= new Button(commentComposite, SWT.CHECK);
		commentButton.setText(fCommentString);

		commentButton.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				boolean isSelected= (((Button) e.widget).getSelection());
				setGenerateComment(isSelected);
			}

			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}
		});
		commentButton.setSelection(getGenerateComment());
		GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalSpan= 2;
		commentButton.setLayoutData(gd);

		return commentComposite;
	}

	protected Composite addVisibilityAndModifiersChoices(Composite buttonComposite) {
		// Add visibility and modifiers buttons: http://bugs.eclipse.org/bugs/show_bug.cgi?id=35870
		// Add persistence of options: http://bugs.eclipse.org/bugs/show_bug.cgi?id=38400
		IVisibilityChangeListener visibilityChangeListener= new IVisibilityChangeListener(){
			public void visibilityChanged(int newVisibility) {
				setVisibility(newVisibility);
			}
			public void modifierChanged(int modifier, boolean isChecked) {
				switch (modifier) {
					case Modifier.FINAL:  {
						setFinal(isChecked);
						return;
					}
					case Modifier.SYNCHRONIZED:  {
						setSynchronized(isChecked);
						return;
					}
					default: return;
				}
			}
		};

		int initialVisibility= getVisibilityModifier();
		int[] availableVisibilities= new int[]{Modifier.PUBLIC, Modifier.PROTECTED, Modifier.PRIVATE, Modifier.NONE};

		Composite visibilityComposite= createVisibilityControlAndModifiers(buttonComposite, visibilityChangeListener, availableVisibilities, initialVisibility);
		return visibilityComposite;
	}

	private List<Integer> convertToIntegerList(int[] array) {
		List<Integer> result= new ArrayList<Integer>(array.length);
		for (int i= 0; i < array.length; i++) {
			result.add(new Integer(array[i]));
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.dialogs.CheckedTreeSelectionDialog#create()
	 */
	@Override
	public void create() {
		super.create();

		// select the first checked element, or if none are checked, the first element
		CheckboxTreeViewer treeViewer= getTreeViewer();
		TreeItem[] items= treeViewer.getTree().getItems();
		if (items.length > 0) {
			Object revealedElement= items[0];

			for (int i= 0; i < items.length; i++) {
				if (items[i].getChecked()) {
					revealedElement= items[i].getData();
					break;
				}
			}
			treeViewer.setSelection(new StructuredSelection(revealedElement));
			treeViewer.reveal(revealedElement);
		}
	}

	protected Composite createVisibilityControl(Composite parent, final IVisibilityChangeListener visibilityChangeListener, int[] availableVisibilities, int correctVisibility) {
		fAllowedVisibilities= convertToIntegerList(availableVisibilities);
		if (fAllowedVisibilities.size() == 1)
			return null;

		Group group= new Group(parent, SWT.NONE);
		group.setText(ActionMessages.SourceActionDialog_modifier_group);
		GridData gd= new GridData(GridData.FILL_BOTH);
		group.setLayoutData(gd);
		GridLayout layout= new GridLayout();
		layout.makeColumnsEqualWidth= true;
		layout.numColumns= 4;
		group.setLayout(layout);

		String[] labels= new String[] {
			ActionMessages.SourceActionDialog_modifier_public,
			ActionMessages.SourceActionDialog_modifier_protected,
			ActionMessages.SourceActionDialog_modifier_default,
			ActionMessages.SourceActionDialog_modifier_private,
		};
		Integer[] data= new Integer[] {
					new Integer(Modifier.PUBLIC),
					new Integer(Modifier.PROTECTED),
					new Integer(Modifier.NONE),
					new Integer(Modifier.PRIVATE)};
		Integer initialVisibility= new Integer(correctVisibility);
		for (int i= 0; i < labels.length; i++) {
			Button radio= new Button(group, SWT.RADIO);
			Integer visibilityCode= data[i];
			radio.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
			radio.setText(labels[i]);
			radio.setData(visibilityCode);
			radio.setSelection(visibilityCode.equals(initialVisibility));
			radio.setEnabled(fAllowedVisibilities.contains(visibilityCode));
			radio.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					visibilityChangeListener.visibilityChanged(((Integer)event.widget.getData()).intValue());
				}
			});
		}
		return group;
	}

	protected Composite createVisibilityControlAndModifiers(Composite parent, final IVisibilityChangeListener visibilityChangeListener, int[] availableVisibilities, int correctVisibility) {
		Composite visibilityComposite= createVisibilityControl(parent, visibilityChangeListener, availableVisibilities, correctVisibility);

		Button finalCheckboxButton= new Button(visibilityComposite, SWT.CHECK);
		finalCheckboxButton.setText(ActionMessages.SourceActionDialog_modifier_final);
		GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		finalCheckboxButton.setLayoutData(gd);
		finalCheckboxButton.setData(new Integer(Modifier.FINAL));
		finalCheckboxButton.setEnabled(true);
		finalCheckboxButton.setSelection(isFinal());
		finalCheckboxButton.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent event) {
				visibilityChangeListener.modifierChanged(((Integer)event.widget.getData()).intValue(), ((Button) event.widget).getSelection());
			}

			public void widgetDefaultSelected(SelectionEvent event) {
				widgetSelected(event);
			}
		});

		Button syncCheckboxButton= new Button(visibilityComposite, SWT.CHECK);
		syncCheckboxButton.setText(ActionMessages.SourceActionDialog_modifier_synchronized);
		gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		syncCheckboxButton.setLayoutData(gd);
		syncCheckboxButton.setData(new Integer(Modifier.SYNCHRONIZED));
		syncCheckboxButton.setEnabled(true);
		syncCheckboxButton.setSelection(isSynchronized());
		syncCheckboxButton.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent event) {
				visibilityChangeListener.modifierChanged(((Integer)event.widget.getData()).intValue(), ((Button) event.widget).getSelection());
			}

			public void widgetDefaultSelected(SelectionEvent event) {
				widgetSelected(event);
			}
		});
		return visibilityComposite;
	}

	protected Composite createInsertPositionCombo(Composite composite) {
		Composite selectionComposite = new Composite(composite, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		selectionComposite.setLayout(layout);

		addOrderEntryChoices(selectionComposite);

		return selectionComposite;
	}

	private Composite addOrderEntryChoices(Composite buttonComposite) {
		Label enterLabel= new Label(buttonComposite, SWT.NONE);
		enterLabel.setText(ActionMessages.SourceActionDialog_enterAt_label);
		if (!fEnableInsertPosition)
			enterLabel.setEnabled(false);
		GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		enterLabel.setLayoutData(gd);

		final Combo enterCombo= new Combo(buttonComposite, SWT.READ_ONLY);
		SWTUtil.setDefaultVisibleItemCount(enterCombo);
		if (!fEnableInsertPosition)
			enterCombo.setEnabled(false);
		fillWithPossibleInsertPositions(enterCombo);

		gd= new GridData(GridData.FILL_BOTH);
		gd.widthHint= convertWidthInCharsToPixels(fWidth);
		enterCombo.setLayoutData(gd);
		enterCombo.addSelectionListener(new SelectionAdapter(){
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index= enterCombo.getSelectionIndex();
				// Add persistence only if first or last method: http://bugs.eclipse.org/bugs/show_bug.cgi?id=38400
				setInsertPosition(index);
				fHasUserChangedPositionIndex= true;
			}
		});

		return buttonComposite;
	}

	private void fillWithPossibleInsertPositions(Combo combo) {
		combo.setItems(fLabels.toArray(new String[fLabels.size()]));
		combo.select(fCurrentPositionIndex);
	}

	public boolean getFinal() {
		return fFinal;
	}

	public boolean getSynchronized() {
		return fSynchronized;
	}

	public boolean isFinal() {
		return fFinal;
	}

	public boolean isSynchronized() {
		return fSynchronized;
	}

	public void setElementPositionEnabled(boolean enabled) {
		fEnableInsertPosition= enabled;
	}

	public boolean isElementPositionEnabled() {
		return fEnableInsertPosition;
	}

	/*
	 * Determine where in the file to enter the newly created methods.
	 */
	public IJavaElement getElementPosition() {
		return fInsertPositions.get(fCurrentPositionIndex);
	}

	public int getInsertOffset() throws JavaModelException {
		IJavaElement elementPosition= getElementPosition();
		if (elementPosition instanceof ISourceReference) {
			return ((ISourceReference) elementPosition).getSourceRange().getOffset();
		}
		return -1;
	}

	protected IDialogSettings getDialogSettings() {
		return fSettings;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#getDialogBoundsSettings()
	 * @since 3.8
	 */
	@Override
	protected IDialogSettings getDialogBoundsSettings() {
		return JavaPlugin.getDefault().getDialogSettingsSection("DialogBounds_" + getClass().getSimpleName()); //$NON-NLS-1$
	}

}

/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 *          (report 36180: Callers/Callees view)
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import org.eclipse.jface.dialogs.StatusDialog;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.internal.corext.callhierarchy.CallHierarchy;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;

class FiltersDialog extends StatusDialog {
    private Label fNamesHelpText;
    private Button fFilterOnNames;
    private Text fNames;
    private Text fMaxCallDepth;


    protected FiltersDialog(Shell parentShell) {
        super(parentShell);
    }

    /* (non-Javadoc)
     * Method declared on Window.
     */
    @Override
	protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText(CallHierarchyMessages.FiltersDialog_filter);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell, IJavaHelpContextIds.CALL_HIERARCHY_FILTERS_DIALOG);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
	protected boolean isResizable() {
    	return true;
    }

    /* (non-Javadoc)
     * Method declared on Dialog.
     */
    @Override
	protected Control createDialogArea(Composite parent) {
    	Composite composite= (Composite) super.createDialogArea(parent);

        createNamesArea(composite);
        new Label(composite, SWT.NONE);         // Filler
        createMaxCallDepthArea(composite);

        updateUIFromFilter();

        return composite;
    }

    private void createMaxCallDepthArea(Composite parent) {
        Composite composite= new Composite(parent, SWT.NONE);
        composite.setFont(parent.getFont());
        GridLayout layout = new GridLayout();
        layout.numColumns = 2;
        composite.setLayout(layout);

        Label label= new Label(composite, SWT.NONE);
        label.setFont(composite.getFont());
		label.setText(CallHierarchyMessages.FiltersDialog_maxCallDepth);

        fMaxCallDepth = new Text(composite, SWT.SINGLE | SWT.BORDER);
        fMaxCallDepth.setFont(composite.getFont());
        fMaxCallDepth.setTextLimit(6);
        fMaxCallDepth.addModifyListener(new ModifyListener() {
                public void modifyText(ModifyEvent e) {
                    validateInput();
                }
            });

        GridData gridData = new GridData();
        gridData.widthHint = convertWidthInCharsToPixels(10);
        fMaxCallDepth.setLayoutData(gridData);
    }

    private void createNamesArea(Composite parent) {
        fFilterOnNames = createCheckbox(parent,
                CallHierarchyMessages.FiltersDialog_filterOnNames, true);

        fNames= new Text(parent, SWT.SINGLE | SWT.BORDER);
        fNames.setFont(parent.getFont());
        fNames.addModifyListener(new ModifyListener() {
                public void modifyText(ModifyEvent e) {
                    validateInput();
                }
            });

        GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL);
        gridData.widthHint = convertWidthInCharsToPixels(60);
        fNames.setLayoutData(gridData);

        fNamesHelpText= new Label(parent, SWT.LEFT);
        fNamesHelpText.setFont(parent.getFont());
        fNamesHelpText.setText(CallHierarchyMessages.FiltersDialog_filterOnNamesSubCaption);
    }

    /**
     * Creates a check box button with the given parent and text.
     *
     * @param parent the parent composite
     * @param text the text for the check box
     * @param grabRow <code>true</code>to grab the remaining horizontal space,
     *        <code>false</code> otherwise
     *
     * @return the check box button
     */
    private Button createCheckbox(Composite parent, String text, boolean grabRow) {
        Button button = new Button(parent, SWT.CHECK);
        button.setFont(parent.getFont());

        if (grabRow) {
            GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
            button.setLayoutData(gridData);
        }

        button.setText(text);
        button.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				validateInput();
				updateEnabledState();
			}
		});

        return button;
    }

    /**
     * Updates the enabled state of the widgetry.
     */
    private void updateEnabledState() {
        fNames.setEnabled(fFilterOnNames.getSelection());
        fNamesHelpText.setEnabled(fFilterOnNames.getSelection());
    }

    /**
     * Updates the given filter from the UI state.
     */
    private void updateFilterFromUI() {
        int maxCallDepth = Integer.parseInt(this.fMaxCallDepth.getText());

        CallHierarchyUI.getDefault().setMaxCallDepth(maxCallDepth);
        CallHierarchy.getDefault().setFilters(fNames.getText());
        CallHierarchy.getDefault().setFilterEnabled(fFilterOnNames.getSelection());
    }

    /**
     * Updates the UI state from the given filter.
     */
    private void updateUIFromFilter() {
      fMaxCallDepth.setText(String.valueOf(CallHierarchyUI.getDefault().getMaxCallDepth()));
      fNames.setText(CallHierarchy.getDefault().getFilters());
      fFilterOnNames.setSelection(CallHierarchy.getDefault().isFilterEnabled());
      updateEnabledState();
    }

	/**
     * Updates the filter from the UI state.
     * Must be done here rather than by extending open()
     * because after super.open() is called, the widgetry is disposed.
	 * @see org.eclipse.jface.dialogs.Dialog#okPressed()
     */
    @Override
	protected void okPressed() {
        if (!isMaxCallDepthValid()) {
            if (fMaxCallDepth.forceFocus()) {
                fMaxCallDepth.setSelection(0, fMaxCallDepth.getCharCount());
                fMaxCallDepth.showSelection();
            }
        }

        updateFilterFromUI();
        super.okPressed();
    }

    private boolean isMaxCallDepthValid() {
        String text= fMaxCallDepth.getText();
        if (text.length() == 0)
            return false;

        try {
            int maxCallDepth= Integer.parseInt(text);

            return (maxCallDepth >= 1 && maxCallDepth <= 99);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void validateInput() {
        StatusInfo status= new StatusInfo();
        if (!isMaxCallDepthValid()) {
            status.setError(CallHierarchyMessages.FiltersDialog_messageMaxCallDepthInvalid);
        }
        updateStatus(status);
    }
}

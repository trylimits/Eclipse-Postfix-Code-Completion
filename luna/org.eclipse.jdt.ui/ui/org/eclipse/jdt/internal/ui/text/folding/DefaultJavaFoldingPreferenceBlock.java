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
package org.eclipse.jdt.internal.ui.text.folding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.folding.IJavaFoldingPreferenceBlock;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.preferences.OverlayPreferenceStore;
import org.eclipse.jdt.internal.ui.preferences.OverlayPreferenceStore.OverlayKey;


/**
 * Java default folding preferences.
 *
 * @since 3.0
 */
public class DefaultJavaFoldingPreferenceBlock implements IJavaFoldingPreferenceBlock {

	private IPreferenceStore fStore;
	private OverlayPreferenceStore fOverlayStore;
	private OverlayKey[] fKeys;
	private Map<Button, String> fCheckBoxes= new HashMap<Button, String>();
	private SelectionListener fCheckBoxListener= new SelectionListener() {
		public void widgetDefaultSelected(SelectionEvent e) {
		}
		public void widgetSelected(SelectionEvent e) {
			Button button= (Button) e.widget;
			fOverlayStore.setValue(fCheckBoxes.get(button), button.getSelection());
		}
	};


	public DefaultJavaFoldingPreferenceBlock() {
		fStore= JavaPlugin.getDefault().getPreferenceStore();
		fKeys= createKeys();
		fOverlayStore= new OverlayPreferenceStore(fStore, fKeys);
	}

	private OverlayKey[] createKeys() {
		ArrayList<OverlayKey> overlayKeys= new ArrayList<OverlayKey>();

		overlayKeys.add(new OverlayPreferenceStore.OverlayKey(OverlayPreferenceStore.BOOLEAN, PreferenceConstants.EDITOR_FOLDING_JAVADOC));
		overlayKeys.add(new OverlayPreferenceStore.OverlayKey(OverlayPreferenceStore.BOOLEAN, PreferenceConstants.EDITOR_FOLDING_INNERTYPES));
		overlayKeys.add(new OverlayPreferenceStore.OverlayKey(OverlayPreferenceStore.BOOLEAN, PreferenceConstants.EDITOR_FOLDING_METHODS));
		overlayKeys.add(new OverlayPreferenceStore.OverlayKey(OverlayPreferenceStore.BOOLEAN, PreferenceConstants.EDITOR_FOLDING_IMPORTS));
		overlayKeys.add(new OverlayPreferenceStore.OverlayKey(OverlayPreferenceStore.BOOLEAN, PreferenceConstants.EDITOR_FOLDING_HEADERS));

		return overlayKeys.toArray(new OverlayKey[overlayKeys.size()]);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.folding.IJavaFoldingPreferences#createControl(org.eclipse.swt.widgets.Group)
	 */
	public Control createControl(Composite composite) {
		fOverlayStore.load();
		fOverlayStore.start();

		Composite inner= new Composite(composite, SWT.NONE);
		GridLayout layout= new GridLayout(1, true);
		layout.verticalSpacing= 3;
		layout.marginWidth= 0;
		inner.setLayout(layout);

		Label label= new Label(inner, SWT.LEFT);
		label.setText(FoldingMessages.DefaultJavaFoldingPreferenceBlock_title);

		addCheckBox(inner, FoldingMessages.DefaultJavaFoldingPreferenceBlock_comments, PreferenceConstants.EDITOR_FOLDING_JAVADOC, 0);
		addCheckBox(inner, FoldingMessages.DefaultJavaFoldingPreferenceBlock_headers, PreferenceConstants.EDITOR_FOLDING_HEADERS, 0);
		addCheckBox(inner, FoldingMessages.DefaultJavaFoldingPreferenceBlock_innerTypes, PreferenceConstants.EDITOR_FOLDING_INNERTYPES, 0);
		addCheckBox(inner, FoldingMessages.DefaultJavaFoldingPreferenceBlock_methods, PreferenceConstants.EDITOR_FOLDING_METHODS, 0);
		addCheckBox(inner, FoldingMessages.DefaultJavaFoldingPreferenceBlock_imports, PreferenceConstants.EDITOR_FOLDING_IMPORTS, 0);

		return inner;
	}

	private Button addCheckBox(Composite parent, String label, String key, int indentation) {
		Button checkBox= new Button(parent, SWT.CHECK);
		checkBox.setText(label);

		GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
		gd.horizontalIndent= indentation;
		gd.horizontalSpan= 1;
		gd.grabExcessVerticalSpace= false;
		checkBox.setLayoutData(gd);
		checkBox.addSelectionListener(fCheckBoxListener);

		fCheckBoxes.put(checkBox, key);

		return checkBox;
	}

	private void initializeFields() {
		Iterator<Button> it= fCheckBoxes.keySet().iterator();
		while (it.hasNext()) {
			Button b= it.next();
			String key= fCheckBoxes.get(b);
			b.setSelection(fOverlayStore.getBoolean(key));
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.folding.AbstractJavaFoldingPreferences#performOk()
	 */
	public void performOk() {
		fOverlayStore.propagate();
	}


	/*
	 * @see org.eclipse.jdt.internal.ui.text.folding.AbstractJavaFoldingPreferences#initialize()
	 */
	public void initialize() {
		initializeFields();
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.folding.AbstractJavaFoldingPreferences#performDefaults()
	 */
	public void performDefaults() {
		fOverlayStore.loadDefaults();
		initializeFields();
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.folding.AbstractJavaFoldingPreferences#dispose()
	 */
	public void dispose() {
		fOverlayStore.stop();
	}
}

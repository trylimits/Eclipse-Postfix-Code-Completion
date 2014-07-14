/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Guven Demir <guven.internet+eclipse@gmail.com> - [package explorer] Alternative package name shortening: abbreviation - https://bugs.eclipse.org/bugs/show_bug.cgi?id=299514
 *     IBM Corporation - accessibility
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.wizards.dialogfields;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog field containing a label and a multi-line text control.
 */
public class TextBoxDialogField extends StringDialogField {
	
	/*
	 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.StringDialogField#createTextControl(org.eclipse.swt.widgets.Composite)
	 * @since 3.6
	 */
	@Override
	protected Text createTextControl(Composite parent) {
		Text text= new Text(parent, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		text.addTraverseListener(new TraverseListener() {
			public void keyTraversed(TraverseEvent event) {
				switch (event.detail) {
					case SWT.TRAVERSE_ESCAPE:
					case SWT.TRAVERSE_PAGE_NEXT:
					case SWT.TRAVERSE_PAGE_PREVIOUS:
						event.doit= true;
						break;
					case SWT.TRAVERSE_RETURN:
					case SWT.TRAVERSE_TAB_NEXT:
					case SWT.TRAVERSE_TAB_PREVIOUS:
						if ((event.stateMask & SWT.MODIFIER_MASK) != 0) {
							event.doit= true;
						}
						break;
				}

			}
		});
		return text;
	}
}

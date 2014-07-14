/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Guven Demir <guven.internet+eclipse@gmail.com> - [package explorer] Alternative package name shortening: abbreviation - https://bugs.eclipse.org/bugs/show_bug.cgi?id=299514
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.viewsupport;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;

import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.PreferenceConstants;

/**
 * JavaUILabelProvider that respects settings from the Appearance preference page.
 * Triggers a viewer update when a preference changes.
 */
public class AppearanceAwareLabelProvider extends JavaUILabelProvider implements IPropertyChangeListener, IPropertyListener {

	public final static long DEFAULT_TEXTFLAGS= JavaElementLabels.ROOT_VARIABLE | JavaElementLabels.T_TYPE_PARAMETERS | JavaElementLabels.M_PARAMETER_TYPES |
		JavaElementLabels.M_APP_TYPE_PARAMETERS | JavaElementLabels.M_APP_RETURNTYPE  | JavaElementLabels.REFERENCED_ROOT_POST_QUALIFIED;
	public final static int DEFAULT_IMAGEFLAGS= JavaElementImageProvider.OVERLAY_ICONS;

	private long fTextFlagMask;
	private int fImageFlagMask;

	/**
	 * Constructor for AppearanceAwareLabelProvider.
	 * @param textFlags Flags defined in <code>JavaElementLabels</code>.
	 * @param imageFlags Flags defined in <code>JavaElementImageProvider</code>.
	 */
	public AppearanceAwareLabelProvider(long textFlags, int imageFlags) {
		super(textFlags, imageFlags);
		initMasks();
		PreferenceConstants.getPreferenceStore().addPropertyChangeListener(this);
		PlatformUI.getWorkbench().getEditorRegistry().addPropertyListener(this);
	}

	/**
	 * Creates a labelProvider with DEFAULT_TEXTFLAGS and DEFAULT_IMAGEFLAGS
	 */
	public AppearanceAwareLabelProvider() {
		this(DEFAULT_TEXTFLAGS, DEFAULT_IMAGEFLAGS);
	}

	private void initMasks() {
		IPreferenceStore store= PreferenceConstants.getPreferenceStore();
		fTextFlagMask= -1;
		if (!store.getBoolean(PreferenceConstants.APPEARANCE_METHOD_RETURNTYPE)) {
			fTextFlagMask ^= JavaElementLabels.M_APP_RETURNTYPE;
		}
		if (!store.getBoolean(PreferenceConstants.APPEARANCE_METHOD_TYPEPARAMETERS)) {
			fTextFlagMask ^= JavaElementLabels.M_APP_TYPE_PARAMETERS;
		}
		if (!(store.getBoolean(PreferenceConstants.APPEARANCE_COMPRESS_PACKAGE_NAMES)
				|| store.getBoolean(PreferenceConstants.APPEARANCE_ABBREVIATE_PACKAGE_NAMES))) {
			fTextFlagMask ^= JavaElementLabels.P_COMPRESSED;
		}
		if (!store.getBoolean(PreferenceConstants.APPEARANCE_CATEGORY)) {
			fTextFlagMask ^= JavaElementLabels.ALL_CATEGORY;
		}

		fImageFlagMask= -1;
	}

	/*
	 * @see IPropertyChangeListener#propertyChange(PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent event) {
		String property= event.getProperty();
		if (property.equals(PreferenceConstants.APPEARANCE_METHOD_RETURNTYPE)
				|| property.equals(PreferenceConstants.APPEARANCE_METHOD_TYPEPARAMETERS)
				|| property.equals(PreferenceConstants.APPEARANCE_CATEGORY)
				|| property.equals(PreferenceConstants.APPEARANCE_PKG_NAME_PATTERN_FOR_PKG_VIEW)
				|| property.equals(PreferenceConstants.APPEARANCE_COMPRESS_PACKAGE_NAMES)
				|| property.equals(PreferenceConstants.APPEARANCE_PKG_NAME_ABBREVIATION_PATTERN_FOR_PKG_VIEW)
				|| property.equals(PreferenceConstants.APPEARANCE_ABBREVIATE_PACKAGE_NAMES)) {
			initMasks();
			LabelProviderChangedEvent lpEvent= new LabelProviderChangedEvent(this, null); // refresh all
			fireLabelProviderChanged(lpEvent);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPropertyListener#propertyChanged(java.lang.Object, int)
	 */
	public void propertyChanged(Object source, int propId) {
		if (propId == IEditorRegistry.PROP_CONTENTS) {
			fireLabelProviderChanged(new LabelProviderChangedEvent(this, null)); // refresh all
		}
	}

	/*
	 * @see IBaseLabelProvider#dispose()
	 */
	@Override
	public void dispose() {
		PreferenceConstants.getPreferenceStore().removePropertyChangeListener(this);
		PlatformUI.getWorkbench().getEditorRegistry().removePropertyListener(this);
		super.dispose();
	}

	/*
	 * @see JavaUILabelProvider#evaluateImageFlags()
	 */
	@Override
	protected int evaluateImageFlags(Object element) {
		return getImageFlags() & fImageFlagMask;
	}

	/*
	 * @see JavaUILabelProvider#evaluateTextFlags()
	 */
	@Override
	protected long evaluateTextFlags(Object element) {
		return getTextFlags() & fTextFlagMask;
	}

}

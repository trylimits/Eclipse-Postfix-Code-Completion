/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.wizards;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

/**
 * A {@link ClasspathAttributeConfiguration} specifies how a {@link IClasspathAttribute class path attribute} is presented and configured
 * in the Java build path dialog.
 * <p>
 * Clients should extend this class and include the name of their
 * class in an extension contributed to the jdt.ui's classpath attribute configuration
 * extension point (named <code>org.eclipse.jdt.ui.classpathAttributeConfiguration
 * </code>).
 * </p>
 *
 * @since 3.3
 */
public abstract class ClasspathAttributeConfiguration {

	/**
	 * This class provides information about the attribute to be rendered or configured.
	 */
	public static abstract class ClasspathAttributeAccess {

		/**
		 * Returns the classpath attribute
		 * @return the classpath attribute
		 */
		public abstract IClasspathAttribute getClasspathAttribute();

		/**
		 * Returns the classpath entry the current attribute is part of
		 * @return the parent classpath entry
		 */
		public abstract IClasspathEntry getParentClasspassEntry();

		/**
		 * Returns the Java project the current attribute is part of.
		 * @return the parent Java project
		 */
		public abstract IJavaProject getJavaProject();

	}

	/**
	 * Returns the image descriptor of the classpath attributes name as a translated string.
	 *
	 * @param attribute access to the attributes to render
	 * @return returns the label value of the value
	 */
	public abstract ImageDescriptor getImageDescriptor(ClasspathAttributeAccess attribute);


	/**
	 * Returns the label of the classpath attributes name as a translated string.
	 *
	 * @param attribute access to the attributes to render
	 * @return returns the label value of the value
	 */
	public abstract String getNameLabel(ClasspathAttributeAccess attribute);


	/**
	 * Returns the label of the classpath attributes value as a translated string.
	 *
	 * @param attribute access to the attributes to render
	 * @return returns the label value of the value
	 */
	public abstract String getValueLabel(ClasspathAttributeAccess attribute);

	/**
	 * Specifies if the given attribute can be edited. This will enable the <em>Edit</em> button that typically
	 * shows the edit dialog.
	 *
	 * @param attribute access to the attribute to answer the question of
	 * @return returns true if the attribute can be edited.
	 */
	public abstract boolean canEdit(ClasspathAttributeAccess attribute);

	/**
	 * Specifies if 'Remove' is a valid action on the given attribute. This will enable the <em>Remove</em> button. The action
	 * will typically clear the attributes value. The method should only return <code>true</code> if the element isn't already cleared.
	 *
	 * @param attribute access to the attribute to answer the question of
	 * @return returns true if the attribute can be edited.
	 */
	public abstract boolean canRemove(ClasspathAttributeAccess attribute);

	/**
	 * This method is invoked when the <em>Edit</em> is pressed. The method is expected to show a configuration dialog.
	 *
	 * @param shell the parent shell
	 * @param attribute access to the attribute to configure
	 * @return returns the configured attribute or <code>null</code> if the action has been cancelled.
	 */
	public abstract IClasspathAttribute performEdit(Shell shell, ClasspathAttributeAccess attribute);

	/**
	 * This method is invoked when the <em>Remove</em> is pressed. The method should not show a dialog.
	 *
	 * @param attribute access to the attribute to configure
	 * @return returns the configured attribute
	 */
	public abstract IClasspathAttribute performRemove(ClasspathAttributeAccess attribute);


}

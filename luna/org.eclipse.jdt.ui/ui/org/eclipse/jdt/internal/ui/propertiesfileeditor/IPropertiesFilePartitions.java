/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.propertiesfileeditor;

/**
 * Properties file partitioning definition.
 * <p>
 * A property key is represented by the {@link org.eclipse.jface.text.IDocument#DEFAULT_CONTENT_TYPE default partition}.
 * </p>
 *
 * @since 3.1
 */
public interface IPropertiesFilePartitions {

	/**
	 * The name of the properties file partitioning.
	 * Value: {@value}
	 */
	String PROPERTIES_FILE_PARTITIONING= "___pf_partitioning";  //$NON-NLS-1$

	/**
	 * The name of a comment partition.
	 * Value: {@value}
	 */
	String COMMENT= "__pf_comment"; //$NON-NLS-1$

	/**
	 * The name of a property value partition.
	 * <p>
	 * Note: The value partition may contain assignment characters at their beginning
	 * </p>
	 * Value: {@value}
	 */
	String PROPERTY_VALUE= "__pf_roperty_value"; //$NON-NLS-1$

	/**
	 * Array with properties file partitions.
	 * Value: {@value}
	 */
	String[] PARTITIONS= new String[] { COMMENT, PROPERTY_VALUE };

}

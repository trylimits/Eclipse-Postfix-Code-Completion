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
package org.eclipse.jdt.ui;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;


/**
 * Sorter for Java elements. Ordered by element category, then by element name.
 * Package fragment roots are sorted as ordered on the classpath.
 *
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @deprecated use {@link JavaElementComparator} instead.
 * @since 2.0
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class JavaElementSorter extends ViewerSorter {

	private final JavaElementComparator fComparator;

	/**
	 * Constructor.
	 */
	public JavaElementSorter() {
		super(null); // delay initialization of collator
		fComparator= new JavaElementComparator();
	}

	/**
	 * @deprecated Bug 22518. Method never used: does not override ViewerSorter#isSorterProperty(Object, String).
	 * Method could be removed, but kept for API compatibility.
	 *
     * @param element the element
     * @param property the property
     * @return always <code>true</code>
	 */
	public boolean isSorterProperty(Object element, Object property) {
		return true;
	}

	/*
	 * @see ViewerSorter#category
	 */
	@Override
	public int category(Object element) {
		return fComparator.category(element);
	}

	/*
	 * @see ViewerSorter#compare
	 */
	@Override
	public int compare(Viewer viewer, Object e1, Object e2) {
		return fComparator.compare(viewer, e1, e2);
	}

	/**
	 * Overrides {@link org.eclipse.jface.viewers.ViewerSorter#getCollator()}.
	 * @deprecated The method is not intended to be used by clients.
	 */
	@Override
	public final java.text.Collator getCollator() {
		// kept in for API compatibility
		if (collator == null) {
			collator= java.text.Collator.getInstance();
		}
		return collator;
	}
}

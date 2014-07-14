/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.fix;

import org.eclipse.jface.text.IRegion;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUp;

/**
 * A clean up capable of fixing only a subset of lines
 * in a compilation unit
 *
 * @since 3.4
 */
public interface IMultiLineCleanUp extends ICleanUp {

	public static class MultiLineCleanUpContext extends CleanUpContext {

		private final IRegion[] fRegions;

		public MultiLineCleanUpContext(ICompilationUnit unit, CompilationUnit ast, IRegion[] regions) {
			super(unit, ast);
			fRegions= regions;
		}

		/**
		 * The regions of the lines which should be cleaned up. A region
		 * spans at least one line but can span multiple line if the lines
		 * are successive.
		 *
		 * @return the regions or <b>null</b> if none available
		 */
		public IRegion[] getRegions() {
			return fRegions;
		}
	}
}

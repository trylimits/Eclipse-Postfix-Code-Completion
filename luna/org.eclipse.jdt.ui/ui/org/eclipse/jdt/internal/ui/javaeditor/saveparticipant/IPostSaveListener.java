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
package org.eclipse.jdt.internal.ui.javaeditor.saveparticipant;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.text.IRegion;

import org.eclipse.jdt.core.ICompilationUnit;

import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider;

/**
 * This <code>IPostSaveListener</code> is informed when
 * a compilation unit is saved through the {@link CompilationUnitDocumentProvider}.
 * <p>
 * In oder to get notified the listener must be registered with the {@link SaveParticipantRegistry}
 * and be enabled on the save participant preference page.</p>
 * <p>
 * The notification order of post save listeners is unspecified.</p>
 * <p>
 * Clients may implement this interface.
 * </p>
 *
 * @see SaveParticipantDescriptor
 * @see CompilationUnitDocumentProvider
 * @since 3.3
 */
public interface IPostSaveListener {

	/**
	 * A human readable name of this listener.
	 *
	 * @return the name
	 */
	String getName();

	/**
	 * The unique id of this listener.
	 *
	 * @return a non-empty id
	 */
	String getId();

	/**
	 * Does this save participant need to be informed about the changed
	 * regions in the given compilation unit on next call to
	 * {@link #saved(ICompilationUnit, IRegion[], IProgressMonitor)}?
	 * <p>
	 * <strong>This should return <code>false</code> whenever possible
	 * because calculating changed regions is expensive.</strong>
	 * </p>
	 *
	 * @param compilationUnit
	 * 				the compilation unit which is about to be saved
	 * @return true
	 * 				if changed regions should be computed
	 * @throws CoreException
	 * 				if something went wrong
	 * @since 3.4
	 */
	boolean needsChangedRegions(ICompilationUnit compilationUnit) throws CoreException;

	/**
	 * Informs this post save listener that the given <code>compilationUnit</code>
	 * has been saved by the {@link CompilationUnitDocumentProvider}. The listener
	 * is allowed to modify the given compilation unit and to open a dialog.
	 * <p>
	 * <em>Every implementor of this method must strictly obey these rules:</em>
	 * <ul>
	 *   <li>not touch any file other than the given <code>compilationUnit</code>
	 *   		which is already locked by a scheduling rule </li>
	 *   <li>changing the scheduling rule or posting a new job is not allowed</li>
	 *   <li>it is not allowed to save the given <code>compilationUnit</code></li>
	 *   <li>it must be able to deal with unsaved resources and with compilation units which are not on the Java build path</li>
	 *   <li>must not assume to be called in the UI thread</li>
	 *   <li>should be as fast as possible since this code is executed every time the <code>compilationUnit</code> is saved</li>
	 * </ul>
	 * The compilation unit document provider can disable a listener that violates any of the above rules.</p>
	 *
	 * @param compilationUnit
	 * 				the compilation unit which was saved
	 * @param changedRegions
	 * 				the regions where the compilationUnit has changed since the last save
	 * 				or <b>null</b> if </code>requiresChangedRegions</code> did return <code>false</code>.
	 * 				The changed regions do not include changes introduced by other save participants.
	 * @param monitor
	 * 				the progress monitor for reporting progress
	 * @throws CoreException
	 * 				if something went wrong
	 *
	 * @see CompilationUnitDocumentProvider
	 */
	void saved(ICompilationUnit compilationUnit, IRegion[] changedRegions, IProgressMonitor monitor) throws CoreException;

}

/*******************************************************************************
 * Copyright (c) 2007, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.text.templates.ContextTypeRegistry;
import org.eclipse.jface.text.templates.TemplateContextType;

import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.template.java.SWTContextType;

import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateEngine;


/**
 * Computer that computes the template proposals for the SWT context type.
 *
 * @since 3.4
 */
public class SWTTemplateCompletionProposalComputer extends AbstractTemplateCompletionProposalComputer {

	/**
	 * The name of <code>org.eclipse.swt.SWT</code> used to detect
	 * if a project uses SWT.
	 */
	private static final String SWT_TYPE_NAME= "org.eclipse.swt.SWT"; //$NON-NLS-1$


	/**
	 * Listener that resets the cached java project if its build path changes.
	 */
	private final class BuildPathChangeListener implements IElementChangedListener {

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.core.IElementChangedListener#elementChanged(org.eclipse.jdt.core.ElementChangedEvent)
		 */
		public void elementChanged(ElementChangedEvent event) {
			IJavaProject javaProject= getCachedJavaProject();
			if (javaProject == null)
				return;

			IJavaElementDelta[] children= event.getDelta().getChangedChildren();
			for (int i= 0; i < children.length; i++) {
				IJavaElementDelta child= children[i];
				if (javaProject.equals(child.getElement())) {
					if (isClasspathChange(child)) {
						setCachedJavaProject(null);
					}
				}
			}
		}

		/**
		 * Does the delta indicate a classpath change?
		 * @param delta the delta to inspect
		 * @return true if classpath has changed
		 */
		private boolean isClasspathChange(IJavaElementDelta delta) {
			int flags= delta.getFlags();
			if (isClasspathChangeFlag(flags))
				return true;

			if ((flags & IJavaElementDelta.F_CHILDREN) != 0) {
				IJavaElementDelta[] children= delta.getAffectedChildren();
				for (int i= 0; i < children.length; i++) {
					if (isClasspathChangeFlag(children[i].getFlags()))
						return true;
				}
			}

			return false;
		}

		/**
		 * Do the flags indicate a classpath change?
		 * @param flags the flags to inspect
		 * @return true if the flag flags a classpath change
		 */
		private boolean isClasspathChangeFlag(int flags) {
			if ((flags & IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED) != 0)
				return true;

			if ((flags & IJavaElementDelta.F_ADDED_TO_CLASSPATH) != 0)
				return true;

			if ((flags & IJavaElementDelta.F_REMOVED_FROM_CLASSPATH) != 0)
				return true;

			if ((flags & IJavaElementDelta.F_ARCHIVE_CONTENT_CHANGED) != 0)
				return true;

			return false;
		}
	}


	/**
	 * Engine used to compute the proposals for this computer
	 */
	private final TemplateEngine fSWTTemplateEngine;
	private final TemplateEngine fSWTMembersTemplateEngine;
	private final TemplateEngine fSWTStatementsTemplateEngine;

	/**
	 * The Java project of the compilation unit for which a template
	 * engine has been computed last time if any
	 */
	private IJavaProject fCachedJavaProject;
	/**
	 * Is org.eclipse.swt.SWT on class path of <code>fJavaProject</code>. Invalid
	 * if <code>fJavaProject</code> is <code>false</code>.
	 */
	private boolean fIsSWTOnClasspath;

	public SWTTemplateCompletionProposalComputer() {
		ContextTypeRegistry templateContextRegistry= JavaPlugin.getDefault().getTemplateContextRegistry();
		fSWTTemplateEngine= createTemplateEngine(templateContextRegistry, SWTContextType.ID_ALL);
		fSWTMembersTemplateEngine= createTemplateEngine(templateContextRegistry, SWTContextType.ID_MEMBERS);
		fSWTStatementsTemplateEngine= createTemplateEngine(templateContextRegistry, SWTContextType.ID_STATEMENTS);

		JavaCore.addElementChangedListener(new BuildPathChangeListener());
	}

	private static TemplateEngine createTemplateEngine(ContextTypeRegistry templateContextRegistry, String contextTypeId) {
		TemplateContextType contextType= templateContextRegistry.getContextType(contextTypeId);
		Assert.isNotNull(contextType);
		return new TemplateEngine(contextType);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.java.TemplateCompletionProposalComputer#computeCompletionEngine(org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext)
	 */
	@Override
	protected TemplateEngine computeCompletionEngine(JavaContentAssistInvocationContext context) {
		ICompilationUnit unit= context.getCompilationUnit();
		if (unit == null)
			return null;

		IJavaProject javaProject= unit.getJavaProject();
		if (javaProject == null)
			return null;

		if (isSWTOnClasspath(javaProject)) {
			CompletionContext coreContext= context.getCoreContext();
			if (coreContext != null) {
				int tokenLocation= coreContext.getTokenLocation();
				if ((tokenLocation & CompletionContext.TL_MEMBER_START) != 0) {
					return fSWTMembersTemplateEngine;
				}
				if ((tokenLocation & CompletionContext.TL_STATEMENT_START) != 0) {
					return fSWTStatementsTemplateEngine;
				}
			}
			return fSWTTemplateEngine;
		}

		return null;
	}

	/**
	 * Tells whether SWT is on the given project's class path.
	 * 
	 * @param javaProject the Java project
	 * @return <code>true</code> if the given project's class path
	 */
	private synchronized boolean isSWTOnClasspath(IJavaProject javaProject) {
		if (!javaProject.equals(fCachedJavaProject)) {
			fCachedJavaProject= javaProject;
			try {
				IType type= javaProject.findType(SWT_TYPE_NAME);
				fIsSWTOnClasspath= type != null;
			} catch (JavaModelException e) {
				fIsSWTOnClasspath= false;
			}
		}
		return fIsSWTOnClasspath;
	}

	/**
	 * Returns the cached Java project.
	 *
	 * @return the cached Java project or <code>null</code> if none
	 */
	private synchronized IJavaProject getCachedJavaProject() {
		return fCachedJavaProject;
	}

	/**
	 * Set the cached Java project.
	 *
	 * @param project or <code>null</code> to reset the cache
	 */
	private synchronized void setCachedJavaProject(IJavaProject project) {
		fCachedJavaProject= project;
	}

}

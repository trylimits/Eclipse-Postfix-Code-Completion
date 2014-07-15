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
package org.eclipse.jdt.internal.ui.text.correction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.eclipse.core.expressions.EvaluationContext;
import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.expressions.ExpressionConverter;
import org.eclipse.core.expressions.ExpressionTagNames;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.text.java.ClasspathFixProcessor;
import org.eclipse.jdt.ui.text.java.ClasspathFixProcessor.ClasspathFixProposal;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;

public final class ClasspathFixProcessorDescriptor {

	private static final String ATT_EXTENSION = "classpathFixProcessors"; //$NON-NLS-1$

	private static final String ID= "id"; //$NON-NLS-1$
	private static final String CLASS= "class"; //$NON-NLS-1$

	private static final String OVERRIDES= "overrides"; //$NON-NLS-1$

	private static ClasspathFixProcessorDescriptor[] fgContributedClasspathFixProcessors;

	private final IConfigurationElement fConfigurationElement;
	private ClasspathFixProcessor fProcessorInstance;
	private List<String> fOverriddenIds;
	private Boolean fStatus;

	public ClasspathFixProcessorDescriptor(IConfigurationElement element) {
		fConfigurationElement= element;
		fProcessorInstance= null;
		fStatus= null; // undefined
		if (fConfigurationElement.getChildren(ExpressionTagNames.ENABLEMENT).length == 0) {
			fStatus= Boolean.TRUE;
		}
		IConfigurationElement[] children= fConfigurationElement.getChildren(OVERRIDES);
		if (children.length > 0) {
			fOverriddenIds= new ArrayList<String>(children.length);
			for (int i= 0; i < children.length; i++) {
				fOverriddenIds.add(children[i].getAttribute(ID));
			}
		} else {
			fOverriddenIds= Collections.emptyList();
		}
	}

	public String getID() {
		return fConfigurationElement.getAttribute(ID);
	}

	public Collection<String> getOverridenIds() {
		return fOverriddenIds;
	}

	public IStatus checkSyntax() {
		IConfigurationElement[] children= fConfigurationElement.getChildren(ExpressionTagNames.ENABLEMENT);
		if (children.length > 1) {
			return new StatusInfo(IStatus.ERROR, "Only one < enablement > element allowed. Disabling " + getID()); //$NON-NLS-1$
		}
		return new StatusInfo(IStatus.OK, "Syntactically correct classpath fix processor"); //$NON-NLS-1$
	}

	public boolean matches(IJavaProject javaProject) {
		if (fStatus != null) {
			return fStatus.booleanValue();
		}

		IConfigurationElement[] children= fConfigurationElement.getChildren(ExpressionTagNames.ENABLEMENT);
		if (children.length == 1) {
			try {
				ExpressionConverter parser= ExpressionConverter.getDefault();
				Expression expression= parser.perform(children[0]);
				EvaluationContext evalContext= new EvaluationContext(null, javaProject);
				evalContext.addVariable("project", javaProject); //$NON-NLS-1$
				evalContext.addVariable("sourceLevel", javaProject.getOption(JavaCore.COMPILER_SOURCE, true)); //$NON-NLS-1$
				return expression.evaluate(evalContext) == EvaluationResult.TRUE;
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
			return false;
		}
		fStatus= Boolean.FALSE;
		return false;
	}

	public ClasspathFixProcessor getProcessor(IJavaProject project) {
		if (matches(project)) {
			if (fProcessorInstance == null) {
				try {
					Object extension= fConfigurationElement.createExecutableExtension(CLASS);
					if (extension instanceof ClasspathFixProcessor) {
						fProcessorInstance= (ClasspathFixProcessor) extension;
					} else {
						String message= "Invalid extension to " + ATT_EXTENSION //$NON-NLS-1$
							+ ". Must extends ClasspathFixProcessor: " + getID(); //$NON-NLS-1$
						JavaPlugin.log(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, message));
						fStatus= Boolean.FALSE;
						return null;
					}
				} catch (CoreException e) {
					JavaPlugin.log(e);
					fStatus= Boolean.FALSE;
					return null;
				}
			}
			return fProcessorInstance;
		}
		return null;
	}

	private static ClasspathFixProcessorDescriptor[] getCorrectionProcessors() {
		if (fgContributedClasspathFixProcessors == null) {
			IConfigurationElement[] elements= Platform.getExtensionRegistry().getConfigurationElementsFor(JavaUI.ID_PLUGIN, ATT_EXTENSION);
			ArrayList<ClasspathFixProcessorDescriptor> res= new ArrayList<ClasspathFixProcessorDescriptor>(elements.length);

			for (int i= 0; i < elements.length; i++) {
				ClasspathFixProcessorDescriptor desc= new ClasspathFixProcessorDescriptor(elements[i]);
				IStatus status= desc.checkSyntax();
				if (status.isOK()) {
					res.add(desc);
				} else {
					JavaPlugin.log(status);
				}
			}
			fgContributedClasspathFixProcessors= res.toArray(new ClasspathFixProcessorDescriptor[res.size()]);
			Arrays.sort(fgContributedClasspathFixProcessors, new Comparator<ClasspathFixProcessorDescriptor>() {
				public int compare(ClasspathFixProcessorDescriptor d1, ClasspathFixProcessorDescriptor d2) {
					if (d1.getOverridenIds().contains(d2.getID())) {
						return -1;
					}
					if (d2.getOverridenIds().contains(d1.getID())) {
						return 1;
					}
					return 0;
				}
			});
		}
		return fgContributedClasspathFixProcessors;
	}

	public static ClasspathFixProposal[] getProposals(final IJavaProject project, final String missingType, final MultiStatus status) {
		final ArrayList<ClasspathFixProposal> proposals= new ArrayList<ClasspathFixProposal>();

		final HashSet<String> overriddenIds= new HashSet<String>();
		ClasspathFixProcessorDescriptor[] correctionProcessors= getCorrectionProcessors();
		for (int i= 0; i < correctionProcessors.length; i++) {
			final ClasspathFixProcessorDescriptor curr= correctionProcessors[i];
			if (!overriddenIds.contains(curr.getID())) {
				SafeRunner.run(new ISafeRunnable() {
					public void run() throws Exception {
						ClasspathFixProcessor processor= curr.getProcessor(project);
						if (processor != null) {
							ClasspathFixProposal[] fixProposals= processor.getFixImportProposals(project, missingType);
							if (fixProposals != null) {
								for (int k= 0; k < fixProposals.length; k++) {
									proposals.add(fixProposals[k]);
								}
								overriddenIds.addAll(curr.getOverridenIds());
							}
						}
					}
					public void handleException(Throwable exception) {
						if (status != null) {
							status.merge(new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, IStatus.ERROR, CorrectionMessages.ClasspathFixProcessorDescriptor_error_processing_processors, exception));
						}
					}
				});
			}
		}
		return proposals.toArray(new ClasspathFixProposal[proposals.size()]);
	}
}

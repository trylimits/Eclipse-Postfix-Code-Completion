/*******************************************************************************
 * Copyright (c) 2005, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Marcel Bruch <bruch@cs.tu-darmstadt.de> - [content assist] Allow to re-sort proposals - https://bugs.eclipse.org/bugs/show_bug.cgi?id=350991
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.osgi.framework.Bundle;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.core.runtime.PerformanceStats;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * The description of an extension to the
 * <code>org.eclipse.jdt.ui.javaCompletionProposalComputer</code> extension point. Instances are
 * immutable. Instances can be obtained from a {@link CompletionProposalComputerRegistry}.
 *
 * @see CompletionProposalComputerRegistry
 * @since 3.2
 */
final class CompletionProposalComputerDescriptor {
	/** The default category id. */
	private static final String DEFAULT_CATEGORY_ID= "org.eclipse.jdt.ui.defaultProposalCategory"; //$NON-NLS-1$
	/** The extension schema name of the category id attribute. */
	private static final String CATEGORY_ID= "categoryId"; //$NON-NLS-1$
	/** The extension schema name of the partition type attribute. */
	private static final String TYPE= "type"; //$NON-NLS-1$
	/** The extension schema name of the class attribute. */
	private static final String CLASS= "class"; //$NON-NLS-1$
	/** The extension schema name of the activate attribute. */
	private static final String ACTIVATE= "activate"; //$NON-NLS-1$
	/** The extension schema name of the needsSortingAfterFiltering attribute. */
	private static final String NEEDS_SORTING_AFTER_FILTERING= "needsSortingAfterFiltering"; //$NON-NLS-1$
	/** The extension schema name of the partition child elements. */
	private static final String PARTITION= "partition"; //$NON-NLS-1$
	/** Set of Java partition types. */
	private static final Set<String> PARTITION_SET;
	/** The name of the performance event used to trace extensions. */
	private static final String PERFORMANCE_EVENT= JavaPlugin.getPluginId() + "/perf/content_assist/extensions"; //$NON-NLS-1$
	/**
	 * If <code>true</code>, execution time of extensions is measured and the data forwarded to
	 * core's {@link PerformanceStats} service.
	 */
	private static final boolean MEASURE_PERFORMANCE= PerformanceStats.isEnabled(PERFORMANCE_EVENT);
	/**
	 * Independently of the {@link PerformanceStats} service, any operation that takes longer than
	 * {@value} milliseconds will be flagged as an violation. This timeout does not apply to the
	 * first invocation, as it may take longer due to plug-in initialization etc. See also
	 * {@link #fIsReportingDelay}.
	 */
	private static final long MAX_DELAY= 5000;

	/* log constants */
	private static final String COMPUTE_COMPLETION_PROPOSALS= "computeCompletionProposals()"; //$NON-NLS-1$
	private static final String COMPUTE_CONTEXT_INFORMATION= "computeContextInformation()"; //$NON-NLS-1$
	private static final String SESSION_STARTED= "sessionStarted()"; //$NON-NLS-1$
	private static final String SESSION_ENDED= "sessionEnded()"; //$NON-NLS-1$

	static {
		Set<String> partitions= new HashSet<String>();
		partitions.add(IDocument.DEFAULT_CONTENT_TYPE);
		partitions.add(IJavaPartitions.JAVA_DOC);
		partitions.add(IJavaPartitions.JAVA_MULTI_LINE_COMMENT);
		partitions.add(IJavaPartitions.JAVA_SINGLE_LINE_COMMENT);
		partitions.add(IJavaPartitions.JAVA_STRING);
		partitions.add(IJavaPartitions.JAVA_CHARACTER);

		PARTITION_SET= Collections.unmodifiableSet(partitions);
	}

	/** The identifier of the extension. */
	private final String fId;
	/** The name of the extension. */
	private final String fName;
	/** The class name of the provided <code>IJavaCompletionProposalComputer</code>. */
	private final String fClass;
	/** The activate attribute value. */
	private final boolean fActivate;
	/** The partition of the extension (element type: {@link String}). */
	private final Set<String> fPartitions;
	/** The configuration element of this extension. */
	private final IConfigurationElement fElement;
	/** The registry we are registered with. */
	private final CompletionProposalComputerRegistry fRegistry;
	/** The computer, if instantiated, <code>null</code> otherwise. */
	private IJavaCompletionProposalComputer fComputer;
	/** The ui category. */
	private final CompletionProposalCategory fCategory;
	/** The first error message in the most recent operation, or <code>null</code>. */
	private String fLastError;
	/**
	 * Tells whether to inform the user when <code>MAX_DELAY</code> has been exceeded.
	 * We start timing execution after the first session because the first may take
	 * longer due to plug-in activation and initialization.
	 */
	private boolean fIsReportingDelay= false;
	/** The start of the last operation. */
	private long fStart;
	/**
	 * Tells whether we tried to load the computer.
	 * @since 3.4
	 */
	boolean fTriedLoadingComputer= false;
	
	/**
	 * Tells whether this proposal engine provides dynamic content that needs to be sorted after its
	 * proposal have been filtered. Filtering happens, e.g., when a user continues typing with an
	 * open completion window.
	 * 
	 * @since 3.8
	 */
	private boolean fNeedsSortingAfterFiltering;


	/**
	 * Creates a new descriptor.
	 *
	 * @param element the configuration element to read
	 * @param registry the computer registry creating this descriptor
	 * @param categories the categories
	 * @throws InvalidRegistryObjectException if this extension is no longer valid
	 * @throws CoreException if the extension contains invalid values
	 */
	CompletionProposalComputerDescriptor(IConfigurationElement element, CompletionProposalComputerRegistry registry, List<CompletionProposalCategory> categories) throws InvalidRegistryObjectException, CoreException {
		Assert.isLegal(registry != null);
		Assert.isLegal(element != null);

		fRegistry= registry;
		fElement= element;
		IExtension extension= element.getDeclaringExtension();
		fId= extension.getUniqueIdentifier();
		checkNotNull(fId, "id"); //$NON-NLS-1$

		String name= extension.getLabel();
		if (name.length() == 0)
			fName= fId;
		else
			fName= name;

		Set<String> partitions= new HashSet<String>();
		IConfigurationElement[] children= element.getChildren(PARTITION);
		if (children.length == 0) {
			fPartitions= PARTITION_SET; // add to all partition types if no partition is configured
		} else {
			for (int i= 0; i < children.length; i++) {
				String type= children[i].getAttribute(TYPE);
				checkNotNull(type, TYPE);
				partitions.add(type);
			}
			fPartitions= Collections.unmodifiableSet(partitions);
		}

		String activateAttribute= element.getAttribute(ACTIVATE);
		fActivate= Boolean.valueOf(activateAttribute).booleanValue();

		String needsSortingAfterFilteringAttribute= element.getAttribute(NEEDS_SORTING_AFTER_FILTERING);
		fNeedsSortingAfterFiltering= Boolean.valueOf(needsSortingAfterFilteringAttribute).booleanValue();

		fClass= element.getAttribute(CLASS);
		checkNotNull(fClass, CLASS);

		String categoryId= element.getAttribute(CATEGORY_ID);
		if (categoryId == null)
			categoryId= DEFAULT_CATEGORY_ID;
		CompletionProposalCategory category= null;
		for (Iterator<CompletionProposalCategory> it= categories.iterator(); it.hasNext();) {
			CompletionProposalCategory cat= it.next();
			if (cat.getId().equals(categoryId)) {
				category= cat;
				break;
			}
		}
		if (category == null) {
			// create a category if it does not exist
			fCategory= new CompletionProposalCategory(categoryId, fName, registry);
			categories.add(fCategory);
		} else {
			fCategory= category;
		}
	}

	/**
	 * Checks that the given attribute value is not <code>null</code>.
	 *
	 * @param value the object to check if not null
	 * @param attribute the attribute
	 * @throws InvalidRegistryObjectException if the registry element is no longer valid
	 * @throws CoreException if <code>value</code> is <code>null</code>
	 */
	private void checkNotNull(Object value, String attribute) throws InvalidRegistryObjectException, CoreException {
		if (value == null) {
			Object[] args= { getId(), fElement.getContributor().getName(), attribute };
			String message= Messages.format(JavaTextMessages.CompletionProposalComputerDescriptor_illegal_attribute_message, args);
			IStatus status= new Status(IStatus.WARNING, JavaPlugin.getPluginId(), IStatus.OK, message, null);
			throw new CoreException(status);
		}
	}

	/**
	 * Returns the identifier of the described extension.
	 *
	 * @return Returns the id
	 */
	public String getId() {
		return fId;
	}

	/**
	 * Returns the name of the described extension.
	 *
	 * @return Returns the name
	 */
	public String getName() {
		return fName;
	}

	/**
	 * Returns the partition types of the described extension.
	 *
	 * @return the set of partition types (element type: {@link String})
	 */
	public Set<String> getPartitions() {
		return fPartitions;
	}

	/**
	 * Returns a cached instance of the computer as described in the
	 * extension's xml. If the computer is not yet created and
	 * <code>canCreate</code> is <code>true</code> then {@link #createComputer()}
	 * is called and the result cached.
	 *
	 * @param canCreate <code>true</code> if the proposal computer can be created
	 * @return a new instance of the completion proposal computer as
	 *         described by this descriptor
	 * @throws CoreException if the creation fails
	 * @throws InvalidRegistryObjectException if the extension is not
	 *         valid any longer (e.g. due to plug-in unloading)
	 */
	private synchronized IJavaCompletionProposalComputer getComputer(boolean canCreate) throws CoreException, InvalidRegistryObjectException {
		if (fComputer == null && canCreate && !fTriedLoadingComputer && (fActivate || isPluginLoaded())) {
			fTriedLoadingComputer= true;
			fComputer= createComputer();
		}
		return fComputer;
	}

	private boolean isPluginLoaded() {
		Bundle bundle= getBundle();
		return bundle != null && bundle.getState() == Bundle.ACTIVE;
	}

	private Bundle getBundle() {
		String namespace= fElement.getDeclaringExtension().getContributor().getName();
		Bundle bundle= Platform.getBundle(namespace);
		return bundle;
	}

	/**
	 * Returns a new instance of the computer as described in the
	 * extension's xml. Note that the safest way to access the computer
	 * is by using the
	 * {@linkplain #computeCompletionProposals(ContentAssistInvocationContext, IProgressMonitor) computeCompletionProposals}
	 * and
	 * {@linkplain #computeContextInformation(ContentAssistInvocationContext, IProgressMonitor) computeContextInformation}
	 * methods. These delegate the functionality to the contributed
	 * computer, but handle instance creation and any exceptions thrown.
	 *
	 * @return a new instance of the completion proposal computer as
	 *         described by this descriptor
	 * @throws CoreException if the creation fails
	 * @throws InvalidRegistryObjectException if the extension is not
	 *         valid any longer (e.g. due to plug-in unloading)
	 */
	public IJavaCompletionProposalComputer createComputer() throws CoreException, InvalidRegistryObjectException {
		return (IJavaCompletionProposalComputer) fElement.createExecutableExtension(CLASS);
	}

	/**
	 * Safely computes completion proposals through the described extension. If the extension
	 * is disabled, throws an exception or otherwise does not adhere to the contract described in
	 * {@link IJavaCompletionProposalComputer}, an empty list is returned.
	 *
	 * @param context the invocation context passed on to the extension
	 * @param monitor the progress monitor passed on to the extension
	 * @return the list of computed completion proposals (element type:
	 *         {@link org.eclipse.jface.text.contentassist.ICompletionProposal})
	 */
	public List<ICompletionProposal> computeCompletionProposals(ContentAssistInvocationContext context, IProgressMonitor monitor) {
		if (!isEnabled())
			return Collections.emptyList();

		IStatus status;
		try {
			IJavaCompletionProposalComputer computer= getComputer(true);
			if (computer == null) // not active yet
				return Collections.emptyList();

			try {
				PerformanceStats stats= startMeter(context, computer);
				List<ICompletionProposal> proposals= computer.computeCompletionProposals(context, monitor);
				stopMeter(stats, COMPUTE_COMPLETION_PROPOSALS);

				if (proposals != null) {
					fLastError= computer.getErrorMessage();
					return proposals;
				}
			} finally {
				fIsReportingDelay= true;
			}
			status= createAPIViolationStatus(COMPUTE_COMPLETION_PROPOSALS);
		} catch (InvalidRegistryObjectException x) {
			status= createExceptionStatus(x);
		} catch (CoreException x) {
			status= createExceptionStatus(x);
		} catch (RuntimeException x) {
			status= createExceptionStatus(x);
		} finally {
			monitor.done();
		}

		fRegistry.informUser(this, status);

		return Collections.emptyList();
	}

	/**
	 * Safely computes context information objects through the described extension. If the extension
	 * is disabled, throws an exception or otherwise does not adhere to the contract described in
	 * {@link IJavaCompletionProposalComputer}, an empty list is returned.
	 *
	 * @param context the invocation context passed on to the extension
	 * @param monitor the progress monitor passed on to the extension
	 * @return the list of computed context information objects (element type:
	 *         {@link org.eclipse.jface.text.contentassist.IContextInformation})
	 */
	public List<IContextInformation> computeContextInformation(ContentAssistInvocationContext context, IProgressMonitor monitor) {
		if (!isEnabled())
			return Collections.emptyList();

		IStatus status;
		try {
			IJavaCompletionProposalComputer computer= getComputer(true);
			if (computer == null) // not active yet
				return Collections.emptyList();

			PerformanceStats stats= startMeter(context, computer);
			List<IContextInformation> proposals= computer.computeContextInformation(context, monitor);
			stopMeter(stats, COMPUTE_CONTEXT_INFORMATION);

			if (proposals != null) {
				fLastError= computer.getErrorMessage();
				return proposals;
			}

			status= createAPIViolationStatus(COMPUTE_CONTEXT_INFORMATION);
		} catch (InvalidRegistryObjectException x) {
			status= createExceptionStatus(x);
		} catch (CoreException x) {
			status= createExceptionStatus(x);
		} catch (RuntimeException x) {
			status= createExceptionStatus(x);
		} finally {
			monitor.done();
		}

		fRegistry.informUser(this, status);

		return Collections.emptyList();
	}


	/**
	 * Notifies the described extension of a proposal computation session start.
	 * <p><em>
	 * Note: This method is called every time code assist is invoked and
	 * is <strong>not</strong> filtered by partition type.
	 * </em></p>
	 */
	public void sessionStarted() {
		if (!isEnabled())
			return;

		IStatus status;
		try {
			IJavaCompletionProposalComputer computer= getComputer(true);
			if (computer == null) // not active yet
				return;

			PerformanceStats stats= startMeter(SESSION_STARTED, computer);
			computer.sessionStarted();
			stopMeter(stats, SESSION_ENDED);

			return;
		} catch (InvalidRegistryObjectException x) {
			status= createExceptionStatus(x);
		} catch (CoreException x) {
			status= createExceptionStatus(x);
		} catch (RuntimeException x) {
			status= createExceptionStatus(x);
		}

		fRegistry.informUser(this, status);
	}

	/**
	 * Notifies the described extension of a proposal computation session end.
	 * <p><em>
	 * Note: This method is called every time code assist is invoked and
	 * is <strong>not</strong> filtered by partition type.
	 * </em></p>
	 */
	public void sessionEnded() {
		if (!isEnabled())
			return;

		IStatus status;
		try {
			IJavaCompletionProposalComputer computer= getComputer(false);
			if (computer == null) // not active yet
				return;

			PerformanceStats stats= startMeter(SESSION_ENDED, computer);
			computer.sessionEnded();
			stopMeter(stats, SESSION_ENDED);

			return;
		} catch (InvalidRegistryObjectException x) {
			status= createExceptionStatus(x);
		} catch (CoreException x) {
			status= createExceptionStatus(x);
		} catch (RuntimeException x) {
			status= createExceptionStatus(x);
		}

		fRegistry.informUser(this, status);
	}

	private PerformanceStats startMeter(Object context, IJavaCompletionProposalComputer computer) {
		final PerformanceStats stats;
		if (MEASURE_PERFORMANCE) {
			stats= PerformanceStats.getStats(PERFORMANCE_EVENT, computer);
			stats.startRun(context.toString());
		} else {
			stats= null;
		}

		if (fIsReportingDelay) {
			fStart= System.currentTimeMillis();
		}

		return stats;
	}

	private void stopMeter(final PerformanceStats stats, String operation) {
		if (MEASURE_PERFORMANCE) {
			stats.endRun();
			if (stats.isFailure()) {
				IStatus status= createPerformanceStatus(operation);
				fRegistry.informUser(this, status);
				return;
			}
		}

		if (fIsReportingDelay) {
			long current= System.currentTimeMillis();
			if (current - fStart > MAX_DELAY) {
				IStatus status= createPerformanceStatus(operation);
				fRegistry.informUser(this, status);
			}
		}
	}

	private IStatus createExceptionStatus(InvalidRegistryObjectException x) {
		// extension has become invalid - log & disable
		String blame= createBlameMessage();
		String reason= JavaTextMessages.CompletionProposalComputerDescriptor_reason_invalid;
		return new Status(IStatus.INFO, JavaPlugin.getPluginId(), IStatus.OK, blame + " " + reason, x); //$NON-NLS-1$
	}

	private IStatus createExceptionStatus(CoreException x) {
		// unable to instantiate the extension - log
		String blame= createBlameMessage();
		String reason= JavaTextMessages.CompletionProposalComputerDescriptor_reason_instantiation;
		return new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.OK, blame + " " + reason, x); //$NON-NLS-1$
	}

	private IStatus createExceptionStatus(RuntimeException x) {
		// misbehaving extension - log
		String blame= createBlameMessage();
		String reason= JavaTextMessages.CompletionProposalComputerDescriptor_reason_runtime_ex;
		return new Status(IStatus.WARNING, JavaPlugin.getPluginId(), IStatus.OK, blame + " " + reason, x); //$NON-NLS-1$
	}

	private IStatus createAPIViolationStatus(String operation) {
		String blame= createBlameMessage();
		Object[] args= {operation};
		String reason= Messages.format(JavaTextMessages.CompletionProposalComputerDescriptor_reason_API, args);
		return new Status(IStatus.WARNING, JavaPlugin.getPluginId(), IStatus.OK, blame + " " + reason, null); //$NON-NLS-1$
	}

	private IStatus createPerformanceStatus(String operation) {
		String blame= createBlameMessage();
		Object[] args= {operation};
		String reason= Messages.format(JavaTextMessages.CompletionProposalComputerDescriptor_reason_performance, args);
		return new Status(IStatus.WARNING, JavaPlugin.getPluginId(), IStatus.OK, blame + " " + reason, null); //$NON-NLS-1$
	}

	private String createBlameMessage() {
		Object[] args= { getName(), fElement.getDeclaringExtension().getContributor().getName() };
		String disable= Messages.format( JavaTextMessages.CompletionProposalComputerDescriptor_blame_message, args);
		return disable;
	}

	/**
	 * Returns the enablement state of the described extension.
	 *
	 * @return the enablement state of the described extension
	 */
	private boolean isEnabled() {
		return fCategory.isEnabled();
	}

	CompletionProposalCategory getCategory() {
		return fCategory;
	}

	/**
	 * Returns the error message from the described extension.
	 *
	 * @return the error message from the described extension
	 */
	public String getErrorMessage() {
		return fLastError;
	}

	/**
	 * Returns the contributor of the described extension.
	 *
	 * @return the contributor of the described extension
	 */
    IContributor getContributor()  {
        try {
	        return fElement.getContributor();
        } catch (InvalidRegistryObjectException e) {
        	return null;
        }
    }

	/**
	 * Returns the <code>needsSortingAfterFiltering</code> flag of the described extension.
	 * 
	 * @return the needsSortingAfterFiltering flag of the described extension
	 * @since 3.8
	 */
	public boolean isSortingAfterFilteringNeeded() {
		return fNeedsSortingAfterFiltering;
	}
}

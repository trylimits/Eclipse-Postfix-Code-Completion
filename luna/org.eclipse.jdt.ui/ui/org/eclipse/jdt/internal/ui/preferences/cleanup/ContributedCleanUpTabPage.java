/*******************************************************************************
 * Copyright (c) 2008, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences.cleanup;

import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.SafeRunner;

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpConfigurationUI;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * @since 3.5
 */
public class ContributedCleanUpTabPage extends CleanUpTabPage {

	private final ICleanUpConfigurationUI fContribution;

	public ContributedCleanUpTabPage(ICleanUpConfigurationUI contribution) {
		fContribution= contribution;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.cleanup.CleanUpTabPage#setWorkingValues(java.util.Map)
	 */
	@Override
	public void setWorkingValues(Map<String, String> workingValues) {
		super.setWorkingValues(workingValues);

		final CleanUpOptions options= new CleanUpOptions(workingValues) {
			/*
			 * @see org.eclipse.jdt.internal.ui.fix.CleanUpOptions#setOption(java.lang.String, java.lang.String)
			 */
			@Override
			public void setOption(String key, String value) {
				super.setOption(key, value);

				doUpdatePreview();
				notifyValuesModified();
			}
		};
		SafeRunner.run(new ISafeRunnable() {
			public void handleException(Throwable exception) {
				ContributedCleanUpTabPage.this.handleException(exception);
			}

			public void run() throws Exception {
				fContribution.setOptions(options);
			}
		});
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.cleanup.ICleanUpTabPage#setOptions(org.eclipse.jdt.internal.ui.fix.CleanUpOptions)
	 */
	public void setOptions(CleanUpOptions options) {
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.formatter.ModifyDialogTabPage#doCreatePreferences(org.eclipse.swt.widgets.Composite, int)
	 */
	@Override
	protected void doCreatePreferences(Composite composite, int numColumns) {
		final Composite parent= new Composite(composite, SWT.NONE);
		GridData layoutData= new GridData(SWT.FILL, SWT.FILL, true, true);
		layoutData.horizontalSpan= numColumns;
		parent.setLayoutData(layoutData);
		GridLayout layout= new GridLayout(1, false);
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		parent.setLayout(layout);

		SafeRunner.run(new ISafeRunnable() {
			public void handleException(Throwable exception) {
				ContributedCleanUpTabPage.this.handleException(exception);

				Label label= new Label(parent, SWT.NONE);
				label.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
				label.setText(CleanUpMessages.ContributedCleanUpTabPage_ErrorPage_message);
			}

			public void run() throws Exception {
				fContribution.createContents(parent);
			}
		});
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.cleanup.ICleanUpTabPage#getPreview()
	 */
	public String getPreview() {
		final String[] result= new String[] { "" }; //$NON-NLS-1$
		SafeRunner.run(new ISafeRunnable() {
			public void handleException(Throwable exception) {
				ContributedCleanUpTabPage.this.handleException(exception);
			}

			public void run() throws Exception {
				result[0]= fContribution.getPreview();
			}
		});
		return result[0];
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.cleanup.CleanUpTabPage#getSelectedCleanUpCount()
	 */
	@Override
	public int getSelectedCleanUpCount() {
		final int[] result= new int[] { 0 };
		SafeRunner.run(new ISafeRunnable() {
			public void handleException(Throwable exception) {
				ContributedCleanUpTabPage.this.handleException(exception);
			}

			public void run() throws Exception {
				int count= fContribution.getSelectedCleanUpCount();
				Assert.isTrue(count >= 0 && count <= getCleanUpCount());
				result[0]= count;
			}
		});
		return result[0];
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.cleanup.CleanUpTabPage#getCleanUpCount()
	 */
	@Override
	public int getCleanUpCount() {
		final int[] result= new int[] { 0 };
		SafeRunner.run(new ISafeRunnable() {
			public void handleException(Throwable exception) {
				ContributedCleanUpTabPage.this.handleException(exception);
			}

			public void run() throws Exception {
				result[0]= fContribution.getCleanUpCount();
			}
		});
		return result[0];
	}

	private void handleException(Throwable exception) {
		JavaPlugin.log(exception);
	}

}

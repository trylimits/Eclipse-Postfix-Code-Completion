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
package org.eclipse.jdt.internal.ui.actions;

import java.net.URL;

import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Display;

import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;

import org.eclipse.jdt.internal.ui.JavaPlugin;


public class OpenBrowserUtil {

	/**
	 * Opens the given url in the browser as choosen in the preferences.
	 * 
	 * @param url the URL
	 * @param display the display
	 * @since 3.6
	 */
	public static void open(final URL url, Display display) {
		display.syncExec(new Runnable() {
			public void run() {
				internalOpen(url, false);
			}
		});
	}

	/**
	 * Opens the given URL in an external browser.
	 * 
	 * @param url the URL
	 * @param display the display
	 * @since 3.6
	 */
	public static void openExternal(final URL url, Display display) {
		display.syncExec(new Runnable() {
			public void run() {
				internalOpen(url, true);
			}
		});
	}

	private static void internalOpen(final URL url, final boolean useExternalBrowser) {
		BusyIndicator.showWhile(null, new Runnable() {
			public void run() {
				URL helpSystemUrl= PlatformUI.getWorkbench().getHelpSystem().resolve(url.toExternalForm(), true);
				if (helpSystemUrl == null) { // can happen if org.eclipse.help.ui is not available
					return; // the resolve() method already wrote "Unable to instantiate help UI" to the log
				}
				try {
					IWorkbenchBrowserSupport browserSupport= PlatformUI.getWorkbench().getBrowserSupport();
					IWebBrowser browser;
					if (useExternalBrowser)
						browser= browserSupport.getExternalBrowser();
					else
						browser= browserSupport.createBrowser(null);
					browser.openURL(helpSystemUrl);
				} catch (PartInitException ex) {
					// XXX: show dialog?
					JavaPlugin.logErrorStatus("Opening Javadoc failed", ex.getStatus()); //$NON-NLS-1$
				}
			}
		});
	}

	/**
	 * DO NOT REMOVE, used in a product.
	 * 
	 * @param url the URL
	 * @param display the display
	 * @param title the title
	 * @deprecated As of 3.6, replaced by {@link #open(URL, Display)}
	 */
	public static void open(final URL url, Display display, String title) {
		open(url, display);
	}

}

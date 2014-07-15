/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.LegacyHandlerSubmissionExpression;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;

import org.eclipse.jdt.ui.text.java.correction.ICommandAccess;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;

public class CorrectionCommandInstaller {

	private List<IHandlerActivation> fCorrectionHandlerActivations;

	public CorrectionCommandInstaller() {
		fCorrectionHandlerActivations= null;
	}

	public void registerCommands(CompilationUnitEditor editor) {
		IWorkbench workbench= PlatformUI.getWorkbench();
		ICommandService commandService= (ICommandService) workbench.getAdapter(ICommandService.class);
		IHandlerService handlerService= (IHandlerService) workbench.getAdapter(IHandlerService.class);
		if (commandService == null || handlerService == null) {
			return;
		}

		if (fCorrectionHandlerActivations != null) {
			JavaPlugin.logErrorMessage("correction handler activations not released"); //$NON-NLS-1$
		}
		fCorrectionHandlerActivations= new ArrayList<IHandlerActivation>();

		Collection<String> definedCommandIds= commandService.getDefinedCommandIds();
		for (Iterator<String> iter= definedCommandIds.iterator(); iter.hasNext();) {
			String id= iter.next();
			if (id.startsWith(ICommandAccess.COMMAND_ID_PREFIX)) {
				boolean isAssist= id.endsWith(ICommandAccess.ASSIST_SUFFIX);
				CorrectionCommandHandler handler= new CorrectionCommandHandler(editor, id, isAssist);
				IHandlerActivation activation= handlerService.activateHandler(id, handler, new LegacyHandlerSubmissionExpression(null, null, editor.getSite()));
				fCorrectionHandlerActivations.add(activation);
			}
		}
	}

	public void deregisterCommands() {
		IHandlerService handlerService= (IHandlerService) PlatformUI.getWorkbench().getAdapter(IHandlerService.class);
		if (handlerService != null && fCorrectionHandlerActivations != null) {
			handlerService.deactivateHandlers(fCorrectionHandlerActivations);
			fCorrectionHandlerActivations= null;
		}
	}

}

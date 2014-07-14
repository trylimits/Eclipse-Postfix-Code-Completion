/*******************************************************************************
 * Copyright (c) 2005, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.commands.IParameterValues;

/**
 * Map of parameters for the specific content assist command.
 *
 * @since 3.2
 */
public final class ContentAssistComputerParameter implements IParameterValues {
	/*
	 * @see org.eclipse.core.commands.IParameterValues#getParameterValues()
	 */
	public Map<String, String> getParameterValues() {
		Collection<CompletionProposalCategory> descriptors= CompletionProposalComputerRegistry.getDefault().getProposalCategories();
		Map<String, String> map= new HashMap<String, String>(descriptors.size());
		for (Iterator<CompletionProposalCategory> it= descriptors.iterator(); it.hasNext();) {
			CompletionProposalCategory category= it.next();
			map.put(category.getDisplayName(), category.getId());
		}
		return map;
	}
}

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
package org.eclipse.jdt.internal.ui.refactoring;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

public class UserInterfaceManager {

	private Map<Class<? extends RefactoringProcessor>, Tuple> fMap= new HashMap<Class<? extends RefactoringProcessor>, Tuple>();

	private static class Tuple {
		private Class<? extends UserInterfaceStarter> starter;
		private Class<? extends RefactoringWizard> wizard;
		public Tuple(Class<? extends UserInterfaceStarter> s, Class<? extends RefactoringWizard> w) {
			starter= s;
			wizard= w;
		}
	}

	protected void put(Class<? extends RefactoringProcessor> processor, Class<? extends UserInterfaceStarter> starter, Class<? extends RefactoringWizard> wizard) {
		fMap.put(processor, new Tuple(starter, wizard));
	}


	public UserInterfaceStarter getStarter(Refactoring refactoring) {
		RefactoringProcessor processor= (RefactoringProcessor)refactoring.getAdapter(RefactoringProcessor.class);
		if (processor == null)
			return null;
		Tuple tuple= fMap.get(processor.getClass());
		if (tuple == null)
			return null;
		try {
			UserInterfaceStarter starter= tuple.starter.newInstance();
			Class<? extends RefactoringWizard> wizardClass= tuple.wizard;
			Constructor<? extends RefactoringWizard> constructor= wizardClass.getConstructor(new Class[] {Refactoring.class});
			RefactoringWizard wizard= constructor.newInstance(new Object[] {refactoring});
			starter.initialize(wizard);
			return starter;
		} catch (NoSuchMethodException e) {
			return null;
		} catch (IllegalAccessException e) {
			return null;
		} catch (InstantiationException e) {
			return null;
		} catch (IllegalArgumentException e) {
			return null;
		} catch (InvocationTargetException e) {
			return null;
		}
	}
}

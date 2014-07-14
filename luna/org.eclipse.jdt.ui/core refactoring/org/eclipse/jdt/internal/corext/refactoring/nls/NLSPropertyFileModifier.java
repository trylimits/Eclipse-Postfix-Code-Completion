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
package org.eclipse.jdt.internal.corext.refactoring.nls;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.text.edits.TextEdit;

import org.eclipse.jface.text.Document;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChangeCompatibility;
import org.eclipse.jdt.internal.corext.refactoring.nls.changes.CreateTextFileChange;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;


public class NLSPropertyFileModifier {

	public static Change create(NLSSubstitution[] nlsSubstitutions, IPath propertyFilePath) throws CoreException {

		String name= Messages.format(NLSMessages.NLSPropertyFileModifier_change_name, BasicElementLabels.getPathLabel(propertyFilePath, false));
		TextChange textChange= null;
		if (!Checks.resourceExists(propertyFilePath)) {
			IProject project= getFileHandle(propertyFilePath).getProject();
			String lineDelimiter= StubUtility.getLineDelimiterPreference(project);
			Document document= new Document();
			document.setInitialLineDelimiter(lineDelimiter);
			textChange= new DocumentChange(name, document);
			addChanges(textChange, nlsSubstitutions);
			textChange.perform(new NullProgressMonitor());

			String encoding= null;
			IContentType javaPropertiesContentType= Platform.getContentTypeManager().getContentType("org.eclipse.jdt.core.javaProperties"); //$NON-NLS-1$
			IContentType[] contentTypes= Platform.getContentTypeManager().findContentTypesFor(propertyFilePath.lastSegment());
			if (contentTypes.length == 0 || contentTypes.length > 1 || !contentTypes[0].equals(javaPropertiesContentType)) {
				if (javaPropertiesContentType != null)
					encoding= javaPropertiesContentType.getDefaultCharset();
				if (encoding == null)
					encoding= "ISO-8859-1"; //$NON-NLS-1$
			}

			return new CreateTextFileChange(propertyFilePath, textChange.getCurrentContent(new NullProgressMonitor()), encoding, "properties"); //$NON-NLS-1$
		}

		textChange= new TextFileChange(name, getPropertyFile(propertyFilePath));
		textChange.setTextType("properties"); //$NON-NLS-1$

		addChanges(textChange, nlsSubstitutions);

		return textChange;
	}

	public static Change removeKeys(IPath propertyFilePath, List<String> keys) throws CoreException {

		String name= Messages.format(NLSMessages.NLSPropertyFileModifier_remove_from_property_file, BasicElementLabels.getPathLabel(propertyFilePath, false));
		TextChange textChange= new TextFileChange(name, getPropertyFile(propertyFilePath));
		textChange.setTextType("properties"); //$NON-NLS-1$

		PropertyFileDocumentModel model= new PropertyFileDocumentModel(textChange.getCurrentDocument(new NullProgressMonitor()));

		for (Iterator<String> iterator= keys.iterator(); iterator.hasNext();) {
			String key= iterator.next();
			TextEdit edit= model.remove(key);
			if (edit != null) {
				TextChangeCompatibility.addTextEdit(textChange, Messages.format(NLSMessages.NLSPropertyFileModifier_remove_entry, BasicElementLabels.getJavaElementName(key)), edit);
			}

		}
		return textChange;
	}

	private static IFile getPropertyFile(IPath propertyFilePath) {
		return (IFile) ResourcesPlugin.getWorkspace().getRoot().findMember(propertyFilePath);
	}

	private static IFile getFileHandle(IPath propertyFilePath) {
		if (propertyFilePath == null)
			return null;
		return ResourcesPlugin.getWorkspace().getRoot().getFile(propertyFilePath);
	}

	private static void addChanges(TextChange textChange, NLSSubstitution[] substitutions) throws CoreException {
		PropertyFileDocumentModel model= new PropertyFileDocumentModel(textChange.getCurrentDocument(new NullProgressMonitor()));

		Map<String, NLSSubstitution> newKeyToSubstMap= getNewKeyToSubstitutionMap(substitutions);
		Map<String, NLSSubstitution> oldKeyToSubstMap= getOldKeyToSubstitutionMap(substitutions);

		addInsertEdits(textChange, substitutions, newKeyToSubstMap, oldKeyToSubstMap, model);
		addRemoveEdits(textChange, substitutions, newKeyToSubstMap, oldKeyToSubstMap, model);
		addReplaceEdits(textChange, substitutions, newKeyToSubstMap, oldKeyToSubstMap, model);
	}

	/**
	 * Maps the new keys to a substitutions. If a substitution is not in the map then it is a duplicate.
	 *
	 * @param substitutions the substitutions to add to the map
	 * @return the map containing the substitutions
	 */
	static HashMap<String, NLSSubstitution> getNewKeyToSubstitutionMap(NLSSubstitution[] substitutions) {
		HashMap<String, NLSSubstitution> keyToSubstMap= new HashMap<String, NLSSubstitution>(substitutions.length);
		// find all duplicates
		for (int i= 0; i < substitutions.length; i++) {
			NLSSubstitution curr= substitutions[i];
			if (curr.getState() == NLSSubstitution.EXTERNALIZED) {
				NLSSubstitution val= keyToSubstMap.get(curr.getKey());
				if (val == null || (val.hasPropertyFileChange() && !curr.hasPropertyFileChange())) {
					keyToSubstMap.put(curr.getKey(), curr); // store if first or if stored in new and we are existing
				}
			}
		}
		return keyToSubstMap;
	}

	/**
	 * Maps the old keys to a substitutions. If a substitution is not in the map then it is a duplicate.
	 *
	 * @param substitutions the substitutions to add to the map
	 * @return the map containing the substitutions
	 */
	static HashMap<String, NLSSubstitution> getOldKeyToSubstitutionMap(NLSSubstitution[] substitutions) {
		HashMap<String, NLSSubstitution> keyToSubstMap= new HashMap<String, NLSSubstitution>(substitutions.length);
		// find all duplicates
		for (int i= 0; i < substitutions.length; i++) {
			NLSSubstitution curr= substitutions[i];
			if (curr.getInitialState() == NLSSubstitution.EXTERNALIZED) {
				String key= curr.getInitialKey();
				if (key != null) {
					NLSSubstitution fav= keyToSubstMap.get(key);
					if (fav == null || (fav.hasStateChanged() && !curr.hasStateChanged())) {
						keyToSubstMap.put(key, curr); // store if first or if stored will not be externalized anymore
					}
				}
			}
		}
		return keyToSubstMap;
	}

	static boolean doReplace(NLSSubstitution substitution, Map<String, NLSSubstitution> newKeyToSubstMap, Map<String, NLSSubstitution> oldKeyToSubstMap) {
		if (substitution.getState() != NLSSubstitution.EXTERNALIZED || substitution.hasStateChanged() || substitution.getInitialValue() == null) {
			return false; // was not in property file before
		}
		if (oldKeyToSubstMap.get(substitution.getInitialKey()) != substitution) {
			return false; // not the owner of this key
		}
		if (substitution.isKeyRename() || substitution.isValueRename()) {
			if (newKeyToSubstMap.get(substitution.getKey()) == substitution) { // only rename if we're not a duplicate. duplicates will be removed
				return true;
			}
		}
		return false;
	}

	private static void addReplaceEdits(TextChange textChange, NLSSubstitution[] substitutions, Map<String, NLSSubstitution> newKeyToSubstMap, Map<String, NLSSubstitution> oldKeyToSubstMap, PropertyFileDocumentModel model) {
		for (int i= 0; i < substitutions.length; i++) {
			NLSSubstitution substitution= substitutions[i];
			if (doReplace(substitution, newKeyToSubstMap, oldKeyToSubstMap)) {
				KeyValuePair initialPair= new KeyValuePair(substitution.getInitialKey(), substitution.getInitialValue());

				String key= PropertyFileDocumentModel.escape(substitution.getKey(), false);
				String value= PropertyFileDocumentModel.escape(substitution.getValue(), true) + model.getLineDelimiter();
				KeyValuePair newPair= new KeyValuePair(key, value);
				TextEdit edit= model.replace(initialPair, newPair);
				if (edit != null) {
					TextChangeCompatibility.addTextEdit(textChange, Messages.format(NLSMessages.NLSPropertyFileModifier_replace_entry, BasicElementLabels.getJavaElementName(substitution.getKey())), edit);
				}
			}
		}
	}

	static boolean doInsert(NLSSubstitution substitution, Map<String, NLSSubstitution> newKeyToSubstMap, Map<String, NLSSubstitution> oldKeyToSubstMap) {
		if (substitution.getState() != NLSSubstitution.EXTERNALIZED) {
			return false; // does not go into the property file
		}
		if (!substitution.hasStateChanged() && substitution.getInitialValue() != null) {
			if (!substitution.isKeyRename() || oldKeyToSubstMap.get(substitution.getInitialKey()) == substitution) {
				return false; // no key rename and was not a duplicate
			}
		}
		if (newKeyToSubstMap.get(substitution.getKey()) == substitution) { // only insert if we're not a duplicate
			return true;
		}
		return false;
	}

	private static void addInsertEdits(TextChange textChange, NLSSubstitution[] substitutions, Map<String, NLSSubstitution> newKeyToSubstMap, Map<String, NLSSubstitution> oldKeyToSubstMap, PropertyFileDocumentModel model) {
		ArrayList<KeyValuePair> keyValuePairsToAdd= new ArrayList<KeyValuePair>();

		for (int i= 0; i < substitutions.length; i++) {
			NLSSubstitution substitution= substitutions[i];

			if (doInsert(substitution, newKeyToSubstMap, oldKeyToSubstMap)) {
				String value= PropertyFileDocumentModel.escape(substitution.getValueNonEmpty(), true) + model.getLineDelimiter();
				String key= PropertyFileDocumentModel.escape(substitution.getKey(), false);
				keyValuePairsToAdd.add(new KeyValuePair(key, value));
			}
		}

		if (keyValuePairsToAdd.size() > 0) {
			model.insert(keyValuePairsToAdd.toArray(new KeyValuePair[keyValuePairsToAdd.size()]), textChange);
		}
	}

	static boolean doRemove(NLSSubstitution substitution, Map<String, NLSSubstitution> newKeyToSubstMap, Map<String, NLSSubstitution> oldKeyToSubstMap) {
		if (substitution.getInitialState() != NLSSubstitution.EXTERNALIZED || substitution.getInitialKey() == null) {
			return false; // was not in property file before
		}
		if (oldKeyToSubstMap.get(substitution.getInitialKey()) != substitution) {
			return false; // not the owner of this key
		}
		if (substitution.hasStateChanged()) {
			return true; // was externalized, but not anymore
		} else {
			if (substitution.hasPropertyFileChange() && newKeyToSubstMap.get(substitution.getKey()) != substitution) {
				return true; // has been changed to an already existing
			}
		}
		return false;
	}

	private static void addRemoveEdits(TextChange textChange, NLSSubstitution[] substitutions, Map<String, NLSSubstitution> newKeyToSubstMap, Map<String, NLSSubstitution> oldKeyToSubstMap, PropertyFileDocumentModel model) {
		for (int i= 0; i < substitutions.length; i++) {
			NLSSubstitution substitution= substitutions[i];
			if (doRemove(substitution, newKeyToSubstMap, oldKeyToSubstMap)) {
				TextEdit edit= model.remove(substitution.getInitialKey());
				if (edit != null) {
					TextChangeCompatibility.addTextEdit(textChange, Messages.format(NLSMessages.NLSPropertyFileModifier_remove_entry, BasicElementLabels.getJavaElementName(substitution.getInitialKey())), edit);
				}
			}
		}
	}

}

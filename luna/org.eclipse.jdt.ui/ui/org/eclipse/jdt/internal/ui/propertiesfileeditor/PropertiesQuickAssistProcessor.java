/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.propertiesfileeditor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.IFile;

import org.eclipse.text.edits.TextEdit;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPartitioningException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext;
import org.eclipse.jface.text.source.ISourceViewer;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.TextChange;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringExecutionStarter;
import org.eclipse.jdt.internal.corext.refactoring.nls.AccessorClassModifier;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSPropertyFileModifier;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds;
import org.eclipse.jdt.ui.text.java.correction.CUCorrectionProposal;
import org.eclipse.jdt.ui.text.java.correction.ChangeCorrectionProposal;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.text.correction.proposals.EditAnnotator;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;

/**
 * The properties file quick assist processor.
 * 
 * @since 3.8
 */
public class PropertiesQuickAssistProcessor {

	public static boolean hasAssists(PropertiesAssistContext invocationContext) {
		try {
			return getEscapeUnescapeBackslashProposals(invocationContext, null) ||
					getCreateFieldsInAccessorClassProposals(invocationContext, null) ||
					getRemovePropertiesProposals(invocationContext, null) ||
					getRenameKeysProposals(invocationContext, null);
		} catch (BadLocationException e) {
			JavaPlugin.log(e);
		} catch (BadPartitioningException e) {
			JavaPlugin.log(e);
		}
		return false;
	}

	public static ICompletionProposal[] collectAssists(PropertiesAssistContext invocationContext) throws BadLocationException, BadPartitioningException {
		ArrayList<ICompletionProposal> resultingCollections= new ArrayList<ICompletionProposal>();

		getEscapeUnescapeBackslashProposals(invocationContext, resultingCollections);
		getCreateFieldsInAccessorClassProposals(invocationContext, resultingCollections);
		getRemovePropertiesProposals(invocationContext, resultingCollections);
		getRenameKeysProposals(invocationContext, resultingCollections);
	
		if (resultingCollections.size() == 0)
			return null;
		return resultingCollections.toArray(new ICompletionProposal[resultingCollections.size()]);
	}

	private static boolean getEscapeUnescapeBackslashProposals(IQuickAssistInvocationContext invocationContext, ArrayList<ICompletionProposal> resultingCollections) throws BadLocationException,
			BadPartitioningException {
		ISourceViewer sourceViewer= invocationContext.getSourceViewer();
		IDocument document= sourceViewer.getDocument();
		Point selectedRange= sourceViewer.getSelectedRange();
		int selectionOffset= selectedRange.x;
		int selectionLength= selectedRange.y;
		int proposalOffset;
		int proposalLength;
		String text;
		if (selectionLength == 0) {
			if (selectionOffset != document.getLength()) {
				char ch= document.getChar(selectionOffset);
				if (ch == '=' || ch == ':') { //see PropertiesFilePartitionScanner()
					return false;
				}
			}
	
			ITypedRegion partition= null;
			if (document instanceof IDocumentExtension3)
				partition= ((IDocumentExtension3)document).getPartition(IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING, invocationContext.getOffset(), false);
			if (partition == null)
				return false;
	
			String type= partition.getType();
			if (!(type.equals(IPropertiesFilePartitions.PROPERTY_VALUE) || type.equals(IDocument.DEFAULT_CONTENT_TYPE))) {
				return false;
			}
			proposalOffset= partition.getOffset();
			proposalLength= partition.getLength();
			text= document.get(proposalOffset, proposalLength);
	
			if (type.equals(IPropertiesFilePartitions.PROPERTY_VALUE)) {
				text= text.substring(1); //see PropertiesFilePartitionScanner()
				proposalOffset++;
				proposalLength--;
			}
		} else {
			proposalOffset= selectionOffset;
			proposalLength= selectionLength;
			text= document.get(proposalOffset, proposalLength);
		}
	
		if (PropertiesFileEscapes.containsUnescapedBackslash(text)) {
			if (resultingCollections == null)
				return true;
			resultingCollections.add(new EscapeBackslashCompletionProposal(PropertiesFileEscapes.escape(text, false, true, false), proposalOffset, proposalLength,
					PropertiesFileEditorMessages.EscapeBackslashCompletionProposal_escapeBackslashes));
			return true;
		}
		if (PropertiesFileEscapes.containsEscapedBackslashes(text)) {
			if (resultingCollections == null)
				return true;
			resultingCollections.add(new EscapeBackslashCompletionProposal(PropertiesFileEscapes.unescapeBackslashes(text), proposalOffset, proposalLength,
					PropertiesFileEditorMessages.EscapeBackslashCompletionProposal_unescapeBackslashes));
			return true;
		}
		return false;
	}

	private static boolean getCreateFieldsInAccessorClassProposals(PropertiesAssistContext invocationContext, ArrayList<ICompletionProposal> resultingCollections)
			throws BadLocationException, BadPartitioningException {
		IDocument document= invocationContext.getDocument();
		int selectionOffset= invocationContext.getOffset();
		int selectionLength= invocationContext.getLength();
		List<String> fields= new ArrayList<String>();

		IType accessorClass= invocationContext.getAccessorType();
		if (accessorClass == null || !isEclipseNLSUsed(accessorClass))
			return false;

		List<String> keys= getKeysFromSelection(document, selectionOffset, selectionLength);
		if (keys == null || keys.size() == 0)
			return false;

		for (Iterator<String> iterator= keys.iterator(); iterator.hasNext();) {
			String key= iterator.next();
			if (!isValidJavaIdentifier(key))
				continue;
			IField field= accessorClass.getField(key);
			if (field.exists())
				continue;
			if (resultingCollections == null)
				return true;
			fields.add(key);
		}

		if (fields.size() == 0)
			return false;

		ICompilationUnit cu= accessorClass.getCompilationUnit();
		try {
			Change change= AccessorClassModifier.addFields(cu, fields);
			String name= Messages.format(fields.size() == 1 ? PropertiesFileEditorMessages.PropertiesCorrectionProcessor_create_field_in_accessor_label : PropertiesFileEditorMessages.PropertiesCorrectionProcessor_create_fields_in_accessor_label, BasicElementLabels.getFileName(cu));
			resultingCollections.add(new CUCorrectionProposal(name, cu, (TextChange) change, 5, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE)));
		} catch (CoreException e) {
			JavaPlugin.log(e);
			return false;
		}
		return true;
	}

	private static boolean isValidJavaIdentifier(String key) {
		if (!Character.isJavaIdentifierStart(key.charAt(0)))
			return false;

		for (int i= 1, length= key.length(); i < length; i++) {
			if (!Character.isJavaIdentifierPart(key.charAt(i)))
				return false;
		}
		return true;
	}

	private static boolean getRemovePropertiesProposals(PropertiesAssistContext invocationContext, ArrayList<ICompletionProposal> resultingCollections)
			throws BadLocationException, BadPartitioningException {
		IDocument document= invocationContext.getDocument();
		int selectionOffset= invocationContext.getOffset();
		int selectionLength= invocationContext.getLength();
		List<String> fields= new ArrayList<String>();

		IFile file= invocationContext.getFile();
		if (file == null)
			return false;

		IType accessorClass= invocationContext.getAccessorType();
		if (accessorClass == null || !isEclipseNLSUsed(accessorClass))
			return false;

		List<String> keys= getKeysFromSelection(document, selectionOffset, selectionLength);
		if (keys == null || keys.size() == 0)
			return false;
		if (resultingCollections == null)
			return true;

		for (Iterator<String> iterator= keys.iterator(); iterator.hasNext();) {
			String key= iterator.next();
			IField field= accessorClass.getField(key);
			if (field.exists())
				fields.add(key);
		}

		ICompilationUnit cu= accessorClass.getCompilationUnit();
		try {
			Change propertiesFileChange= NLSPropertyFileModifier.removeKeys(file.getFullPath(), keys);
			Change[] changes;
			if (fields.size() > 0) {
				Change accessorChange= AccessorClassModifier.removeFields(cu, fields);
				changes= new Change[] { propertiesFileChange, accessorChange };
			} else {
				changes= new Change[] { propertiesFileChange };
			}

			String name= (keys.size() == 1)
					? PropertiesFileEditorMessages.PropertiesCorrectionProcessor_remove_property_label
					: PropertiesFileEditorMessages.PropertiesCorrectionProcessor_remove_properties_label;
			resultingCollections.add(new RemovePropertiesProposal(name, 4, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE), changes));
		} catch (CoreException e) {
			JavaPlugin.log(e);
			return false;
		}
		return true;
	}

	private static boolean getRenameKeysProposals(PropertiesAssistContext invocationContext, ArrayList<ICompletionProposal> resultingCollections)
			throws BadLocationException, BadPartitioningException {
		ISourceViewer sourceViewer= invocationContext.getSourceViewer();
		IDocument document= invocationContext.getDocument();
		int selectionOffset= invocationContext.getOffset();
		int selectionLength= invocationContext.getLength();
		IField field= null;

		IType accessorClass= invocationContext.getAccessorType();
		if (accessorClass == null || !isEclipseNLSUsed(accessorClass))
			return false;

		List<String> keys= getKeysFromSelection(document, selectionOffset, selectionLength);
		if (keys == null || keys.size() != 1)
			return false;

		field= accessorClass.getField(keys.get(0));
		if (!field.exists())
			return false;
		if (resultingCollections == null)
			return true;

		String name= PropertiesFileEditorMessages.PropertiesCorrectionProcessor_rename_in_workspace;
		resultingCollections.add(new RenameKeyProposal(name, 5, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE), field, sourceViewer.getTextWidget().getShell()));
		return true;
	}

	private static boolean isEclipseNLSUsed(IType accessor) {
		IJavaProject javaProject= accessor.getJavaProject();
		if (javaProject == null || !javaProject.exists())
			return false;

		try {
			IType nls= javaProject.findType("org.eclipse.osgi.util.NLS"); //$NON-NLS-1$
			if(nls==null)
				return false;
			ITypeHierarchy supertypeHierarchy= accessor.newSupertypeHierarchy(null);
			return supertypeHierarchy.contains(nls);
		} catch (JavaModelException e) {
			return false;
		}
	}

	private static List<String> getKeysFromSelection(IDocument document, int selectionOffset, int selectionLength) throws BadLocationException,
			BadPartitioningException {
		List<String> keys= new ArrayList<String>();
		String selection= document.get(selectionOffset, (selectionLength == 0) ? 1 : selectionLength).trim();
		if (selection.length() == 0)
			return null;
		if (selectionLength == 0) {
			ITypedRegion partition= null;
			if (document instanceof IDocumentExtension3)
				partition= ((IDocumentExtension3) document).getPartition(IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING, selectionOffset, false);
			if (partition == null)
				return null;

			String type= partition.getType();
			if (!(type.equals(IDocument.DEFAULT_CONTENT_TYPE))) {
				return null;
			}
			String key= document.get(partition.getOffset(), partition.getLength()).trim();
			if (key.length() > 0)
				keys.add(key);
		} else {
			int offset= selectionOffset;
			int endOffset= selectionOffset + selectionLength;

			while (offset < endOffset) {
				ITypedRegion partition= null;
				if (document instanceof IDocumentExtension3)
					partition= ((IDocumentExtension3) document).getPartition(IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING, offset, false);
				if (partition == null)
					return null;

				int partitionOffset= partition.getOffset();
				int partitionLength= partition.getLength();
				offset= partitionOffset + partitionLength;

				String type= partition.getType();
				if (!(type.equals(IDocument.DEFAULT_CONTENT_TYPE))) {
					continue;
				}
				String key= document.get(partitionOffset, partitionLength).trim();
				if (key.length() > 0)
					keys.add(key);
			}
		}
		return keys;
	}

	private static class RemovePropertiesProposal extends ChangeCorrectionProposal {

		private final Change[] fChanges;

		protected RemovePropertiesProposal(String name, int relevance, Image image, Change[] changes) {
			super(name, null, relevance, image);
			fChanges= changes;
		}

		@Override
		protected Change createChange() throws CoreException {
			return new CompositeChange(getName(), fChanges);
		}

		@Override
		public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
			final StringBuffer buf= new StringBuffer();

			try {
				for (int i= 0; i < fChanges.length; i++) {
					if (fChanges[i] instanceof TextChange) {
						TextChange change= (TextChange) fChanges[i];
						String filename= getFileName(change);
						if (filename != null) {
							buf.append("<b>"); //$NON-NLS-1$
							buf.append(filename);
							buf.append("</b>"); //$NON-NLS-1$
							buf.append("<br>"); //$NON-NLS-1$
						}
						change.setKeepPreviewEdits(true);
						IDocument currentContent= change.getCurrentDocument(monitor);

						TextEdit rootEdit= change.getEdit();

						EditAnnotator ea= new EditAnnotator(buf, currentContent) {
							@Override
							protected boolean rangeRemoved(TextEdit edit) {
								return annotateEdit(edit, "<del>", "</del>"); //$NON-NLS-1$ //$NON-NLS-2$
							}
						};
						rootEdit.accept(ea);
						ea.unchangedUntil(currentContent.getLength()); // Final pre-existing region
						buf.append("<br><br>"); //$NON-NLS-1$
					}
				}

			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
			return buf.toString();
		}

		private String getFileName(TextChange change) {
			Object modifiedElement= change.getModifiedElement();
			if (modifiedElement instanceof IFile) {
				return ((IFile) modifiedElement).getName();
			} else if (modifiedElement instanceof ICompilationUnit) {
				return ((ICompilationUnit) modifiedElement).getElementName();
			}
			return null;
		}
	}

	private static class RenameKeyProposal extends ChangeCorrectionProposal {

		private final IField fField;

		private final Shell fShell;

		public RenameKeyProposal(String name, int relevance, Image image, IField field, Shell shell) {
			super(name, null, relevance, image);
			fField= field;
			fShell= shell;
		}

		@Override
		public void apply(IDocument document) {
			try {
				RefactoringExecutionStarter.startRenameRefactoring(fField, fShell);
			} catch (CoreException e) {
				JavaPlugin.log(e);
			}
		}

		@Override
		public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
			return PropertiesFileEditorMessages.PropertiesCorrectionProcessor_rename_in_workspace_description;
		}

		@Override
		public String getCommandId() {
			return IJavaEditorActionDefinitionIds.RENAME_ELEMENT;
		}
	}
}

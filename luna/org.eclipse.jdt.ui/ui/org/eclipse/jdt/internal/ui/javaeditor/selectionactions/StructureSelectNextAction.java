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
package org.eclipse.jdt.internal.ui.javaeditor.selectionactions;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;

import org.eclipse.jdt.internal.corext.dom.GenericVisitor;
import org.eclipse.jdt.internal.corext.dom.SelectionAnalyzer;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

public class StructureSelectNextAction extends StructureSelectionAction{

	private static class NextNodeAnalyzer extends GenericVisitor {
		private final int fOffset;
		private ASTNode fNextNode;
		private NextNodeAnalyzer(int offset) {
			super(true);
			fOffset= offset;
		}
		public static ASTNode perform(int offset, ASTNode lastCoveringNode) {
			NextNodeAnalyzer analyzer= new NextNodeAnalyzer(offset);
			lastCoveringNode.accept(analyzer);
			return analyzer.fNextNode;
		}
		@Override
		protected boolean visitNode(ASTNode node) {
			int start= node.getStartPosition();
			int end= start + node.getLength();
			if (start == fOffset) {
				fNextNode= node;
				return true;
			} else {
				return (start < fOffset && fOffset < end);
			}
		}
	}

	public StructureSelectNextAction(JavaEditor editor, SelectionHistory history) {
		super(SelectionActionMessages.StructureSelectNext_label, editor, history);
		setToolTipText(SelectionActionMessages.StructureSelectNext_tooltip);
		setDescription(SelectionActionMessages.StructureSelectNext_description);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.STRUCTURED_SELECT_NEXT_ACTION);
	}

	/*
	 * This constructor is for testing purpose only.
	 */
	public StructureSelectNextAction() {
	}

	/* non java doc
	 * @see StructureSelectionAction#internalGetNewSelectionRange(ISourceRange, ICompilationUnit, SelectionAnalyzer)
	 */
	@Override
	ISourceRange internalGetNewSelectionRange(ISourceRange oldSourceRange, ISourceReference sr, SelectionAnalyzer selAnalyzer) throws JavaModelException{
		if (oldSourceRange.getLength() == 0 && selAnalyzer.getLastCoveringNode() != null) {
			ASTNode previousNode= NextNodeAnalyzer.perform(oldSourceRange.getOffset(), selAnalyzer.getLastCoveringNode());
			if (previousNode != null)
				return getSelectedNodeSourceRange(sr, previousNode);
		}
		ASTNode first= selAnalyzer.getFirstSelectedNode();
		if (first == null)
			return getLastCoveringNodeRange(oldSourceRange, sr, selAnalyzer);

		ASTNode parent= first.getParent();
		if (parent == null)
			return getLastCoveringNodeRange(oldSourceRange, sr, selAnalyzer);

		ASTNode lastSelectedNode= selAnalyzer.getSelectedNodes()[selAnalyzer.getSelectedNodes().length - 1];
		ASTNode nextNode= getNextNode(parent, lastSelectedNode);
		if (nextNode == parent)
			return getSelectedNodeSourceRange(sr, first.getParent());
		int offset= oldSourceRange.getOffset();
		int end= Math.min(sr.getSourceRange().getLength(), nextNode.getStartPosition() + nextNode.getLength() - 1);
		return StructureSelectionAction.createSourceRange(offset, end);
	}

	private static ASTNode getNextNode(ASTNode parent, ASTNode node){
		ASTNode[] siblingNodes= StructureSelectionAction.getSiblingNodes(node);
		if (siblingNodes == null || siblingNodes.length == 0)
			return parent;
		if (node == siblingNodes[siblingNodes.length -1 ])
			return parent;
		else
			return siblingNodes[StructureSelectionAction.findIndex(siblingNodes, node) + 1];
	}
}


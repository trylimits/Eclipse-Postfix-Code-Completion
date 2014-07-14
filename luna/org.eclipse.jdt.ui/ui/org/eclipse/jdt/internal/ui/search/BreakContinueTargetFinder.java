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
package org.eclipse.jdt.internal.ui.search;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.TokenScanner;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;


/**
 * Class used to find the target for a break or continue statement according
 * to the language specification.
 * <p>
 * The target statement is a while, do, switch, for or a labeled statement.
 * Break is described in section 14.15 of the JLS3 and continue in section 14.16.</p>
 *
 * @since 3.2
 */
public class BreakContinueTargetFinder extends ASTVisitor implements IOccurrencesFinder {

	public static final String ID= "BreakContinueTargetFinder"; //$NON-NLS-1$

	private ASTNode fSelected;
	private boolean fIsBreak;
	private SimpleName fLabel;
	private String fDescription;
	private CompilationUnit fASTRoot;

	private static final Class<?>[] STOPPERS=        {MethodDeclaration.class, Initializer.class};
	private static final Class<?>[] BREAKTARGETS=    {ForStatement.class, EnhancedForStatement.class, WhileStatement.class, DoStatement.class, SwitchStatement.class};
	private static final Class<?>[] CONTINUETARGETS= {ForStatement.class, EnhancedForStatement.class, WhileStatement.class, DoStatement.class};
	private static final int BRACE_LENGTH= 1;

	/*
	 * Initializes the finder. Returns error message or <code>null</code> if everything is OK.
	 */
	public String initialize(CompilationUnit root, int offset, int length) {
		return initialize(root, NodeFinder.perform(root, offset, length));
	}

	/*
	 * Initializes the finder. Returns error message or <code>null</code> if everything is OK.
	 */
	public String initialize(CompilationUnit root, ASTNode node) {
		ASTNode controlNode= getBreakOrContinueNode(node);
		if (controlNode != null) {
			fASTRoot= root;

			try {
				if (root.getTypeRoot() == null || root.getTypeRoot().getBuffer() == null)
					return SearchMessages.BreakContinueTargetFinder_cannot_highlight;
			} catch (JavaModelException e) {
				return SearchMessages.BreakContinueTargetFinder_cannot_highlight;
			}
			fSelected= controlNode;
			fIsBreak= fSelected instanceof BreakStatement;
			fLabel= getLabel();
			fDescription= Messages.format(SearchMessages.BreakContinueTargetFinder_occurrence_description, BasicElementLabels.getJavaElementName(ASTNodes.asString(fSelected)));
			return null;
		} else {
			return SearchMessages.BreakContinueTargetFinder_no_break_or_continue_selected;
		}
	}


	//extract the control node: handle labels
	private ASTNode getBreakOrContinueNode(ASTNode selectedNode) {
		if (selectedNode instanceof BreakStatement)
			return selectedNode;
		if (selectedNode instanceof ContinueStatement)
			return selectedNode;
		if (selectedNode instanceof SimpleName && selectedNode.getParent() instanceof BreakStatement)
			return selectedNode.getParent();
		if (selectedNode instanceof SimpleName && selectedNode.getParent() instanceof ContinueStatement)
			return selectedNode.getParent();
		return null;
	}


	private SimpleName getLabel() {
		if (fIsBreak){
			BreakStatement bs= (BreakStatement) fSelected;
			return bs.getLabel();
		} else {
			ContinueStatement cs= (ContinueStatement) fSelected;
			return cs.getLabel();
		}
	}

	/**
	 * Returns the locations of all occurrences or <code>null</code> if no matches are found
	 *
	 * @return the locations of all occurrences or <code>null</code> if no matches are found
	 */
	public OccurrenceLocation[] getOccurrences() {
		ASTNode targetNode= findTargetNode(fSelected);
		if (!isEnclosingStatement(targetNode))
			return null;

		List<OccurrenceLocation> list= new ArrayList<OccurrenceLocation>();
		OccurrenceLocation location= getLocationForFirstToken(targetNode);
		if (location != null) {
			list.add(location);
		}
		if (fIsBreak) {
			location= getLocationForClosingBrace(targetNode);
			if (location != null) {
				list.add(location);
			}
		}
		if (!list.isEmpty()) {
			return list.toArray(new OccurrenceLocation[list.size()]);
		}
		return null;
	}

	private boolean isEnclosingStatement(ASTNode targetNode) {
		return (targetNode != null) && !(targetNode instanceof MethodDeclaration) && !(targetNode instanceof Initializer);
	}

	private ASTNode findTargetNode(ASTNode node) {
		do {
			node= node.getParent();
		} while (keepWalkingUp(node));
		return node;
	}

	private OccurrenceLocation getLocationForFirstToken(ASTNode node) {
		try {
			int nextEndOffset= new TokenScanner(fASTRoot.getTypeRoot()).getNextEndOffset(node.getStartPosition(), true);
			return new OccurrenceLocation(node.getStartPosition(), nextEndOffset - node.getStartPosition(), 0, fDescription);
		} catch (CoreException e) {
			// ignore
		}
		return new OccurrenceLocation(node.getStartPosition(), node.getLength(), 0, fDescription);
	}

	private OccurrenceLocation getLocationForClosingBrace(ASTNode targetNode) {
		/* Ideally, we'd scan backwards to find the '}' token, but it may be an overkill
		 * so I'll just assume the closing brace token has a fixed length. */
		int offset= ASTNodes.getExclusiveEnd(targetNode) - BRACE_LENGTH;
		return new OccurrenceLocation(offset, BRACE_LENGTH, 0, fDescription);
	}

	private boolean keepWalkingUp(ASTNode node) {
		if (node == null)
			return false;
		if (isAnyInstanceOf(STOPPERS, node))
			return false;
		if (fLabel != null && LabeledStatement.class.isInstance(node)){
			LabeledStatement ls= (LabeledStatement)node;
			return ! areEqualLabels(ls.getLabel(), fLabel);
		}
		if (fLabel == null) {
			if (isAnyInstanceOf(fIsBreak ? BREAKTARGETS : CONTINUETARGETS, node))
				return node.getParent() instanceof LabeledStatement; // for behavior consistency of break targets: see bug 339176
			if (node instanceof LabeledStatement)
				return false;
		}
		return true;
	}

	private static boolean areEqualLabels(SimpleName labelToMatch, SimpleName labelSelected) {
		return labelSelected.getIdentifier().equals(labelToMatch.getIdentifier());
	}

	private static boolean isAnyInstanceOf(Class<?>[] continueTargets, ASTNode node) {
		for (int i= 0; i < continueTargets.length; i++) {
			if (continueTargets[i].isInstance(node))
				return true;
		}
		return false;
	}

	public CompilationUnit getASTRoot() {
		return fASTRoot;
	}

	public String getElementName() {
		return ASTNodes.asString(fSelected);
	}

	public String getID() {
		return ID;
	}

	public String getJobLabel() {
		return SearchMessages.BreakContinueTargetFinder_job_label;
	}

	public int getSearchKind() {
		return IOccurrencesFinder.K_BREAK_TARGET_OCCURRENCE;
	}

	public String getUnformattedPluralLabel() {
		if (fIsBreak) {
			return SearchMessages.BreakContinueTargetFinder_break_label_plural;
		} else {
			return SearchMessages.BreakContinueTargetFinder_continue_label_plural;
		}
	}

	public String getUnformattedSingularLabel() {
		if (fIsBreak) {
			return SearchMessages.BreakContinueTargetFinder_break_label_singular;
		} else {
			return SearchMessages.BreakContinueTargetFinder_continue_label_singular;
		}
	}
}

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.Action;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.PlatformUI;

import org.eclipse.ui.texteditor.IUpdate;

import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

public class GoToNextPreviousMemberAction extends Action implements IUpdate {

	public static final String NEXT_MEMBER= "GoToNextMember"; //$NON-NLS-1$
	public static final String PREVIOUS_MEMBER= "GoToPreviousMember"; //$NON-NLS-1$
	private JavaEditor fEditor;
	private boolean fIsGotoNext;

	public static GoToNextPreviousMemberAction newGoToNextMemberAction(JavaEditor editor) {
		String text= SelectionActionMessages.GotoNextMember_label;
		return new GoToNextPreviousMemberAction(editor, text, true);
	}

	public static GoToNextPreviousMemberAction newGoToPreviousMemberAction(JavaEditor editor) {
		String text= SelectionActionMessages.GotoPreviousMember_label;
		return new GoToNextPreviousMemberAction(editor, text, false);
	}

	private GoToNextPreviousMemberAction(JavaEditor editor, String text, boolean isGotoNext) {
		super(text);
		Assert.isNotNull(editor);
		fEditor= editor;
		fIsGotoNext= isGotoNext;
		update();
		if (isGotoNext)
			PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.GOTO_NEXT_MEMBER_ACTION);
		else
			PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.GOTO_PREVIOUS_MEMBER_ACTION);
	}

	/*
	 * This constructor is for testing purpose only.
	 */
	public GoToNextPreviousMemberAction(boolean isSelectNext) {
		super(""); //$NON-NLS-1$
		fIsGotoNext= isSelectNext;
	}

	public void update() {
		boolean enabled= false;
		ISourceReference ref= getSourceReference();
		if (ref != null) {
			ISourceRange range;
			try {
				range= ref.getSourceRange();
				enabled= range != null && range.getLength() > 0;
			} catch (JavaModelException e) {
				// enabled= false;
			}
		}
		setEnabled(enabled);
	}

	/*
	 * @see org.eclipse.jface.action.Action#run()
	 */
	@Override
	public final  void run() {
		ITextSelection selection= getTextSelection();
		ISourceRange newRange= getNewSelectionRange(createSourceRange(selection), null);
		// Check if new selection differs from current selection
		if (selection.getOffset() == newRange.getOffset() && selection.getLength() == newRange.getLength())
			return;
		fEditor.selectAndReveal(newRange.getOffset(), newRange.getLength());
	}

	private IType[] getTypes() throws JavaModelException {
		IEditorInput input= fEditor.getEditorInput();
		if (input instanceof IClassFileEditorInput) {
			return new IType[] { ((IClassFileEditorInput)input).getClassFile().getType() };
		} else {
			return JavaPlugin.getDefault().getWorkingCopyManager().getWorkingCopy(input).getAllTypes();
		}
	}

	private ISourceReference getSourceReference() {
		IEditorInput input= fEditor.getEditorInput();
		if (input instanceof IClassFileEditorInput) {
			return ((IClassFileEditorInput)input).getClassFile();
		} else {
			return JavaPlugin.getDefault().getWorkingCopyManager().getWorkingCopy(input);
		}
	}

	private ITextSelection getTextSelection() {
		return (ITextSelection)fEditor.getSelectionProvider().getSelection();
	}

	public ISourceRange getNewSelectionRange(ISourceRange oldSourceRange, IType[] types) {
		try{
			if (types == null)
				types= getTypes();
			Integer[] offsetArray= createOffsetArray(types);
			if (offsetArray.length == 0)
				return oldSourceRange;
			Arrays.sort(offsetArray);
			Integer oldOffset= new Integer(oldSourceRange.getOffset());
			int index= Arrays.binarySearch(offsetArray, oldOffset);

			if (fIsGotoNext)
				return createNewSourceRange(getNextOffset(index, offsetArray, oldOffset));
			else
				return createNewSourceRange(getPreviousOffset(index, offsetArray, oldOffset));

	 	}	catch (JavaModelException e){
	 		JavaPlugin.log(e); //dialog would be too heavy here
	 		return oldSourceRange;
	 	}
	}

	private static Integer getPreviousOffset(int index, Integer[] offsetArray, Integer oldOffset) {
		if (index == -1)
			return oldOffset;
		if (index == 0)
			return offsetArray[0];
		if (index > 0)
			return offsetArray[index - 1];
		Assert.isTrue(index < -1);
		int absIndex= Math.abs(index);
		return offsetArray[absIndex - 2];
	}

	private static Integer getNextOffset(int index, Integer[] offsetArray, Integer oldOffset) {
		if (index == -1)
			return offsetArray[0];

		if (index == 0){
			if (offsetArray.length != 1)
				return offsetArray[1];
			else
				return offsetArray[0];
		}
		if (index > 0){
			if (index == offsetArray.length - 1)
				return oldOffset;
			return offsetArray[index + 1];
		}
		Assert.isTrue(index < -1);
		int absIndex= Math.abs(index);
		if (absIndex > offsetArray.length)
			return oldOffset;
		else
			return offsetArray[absIndex - 1];
	}

	private static ISourceRange createNewSourceRange(Integer offset){
		return new SourceRange(offset.intValue(), 0);
	}

	private static Integer[] createOffsetArray(IType[] types) throws JavaModelException {
		List<Integer> result= new ArrayList<Integer>();
		for (int i= 0; i < types.length; i++) {
			IType iType= types[i];
			addOffset(result, iType.getNameRange().getOffset());
			addOffset(result, iType.getSourceRange().getOffset() + iType.getSourceRange().getLength());
			addMemberOffsetList(result, iType.getMethods());
			addMemberOffsetList(result, iType.getFields());
			addMemberOffsetList(result, iType.getInitializers());
		}
		return result.toArray(new Integer[result.size()]);
	}

	private static void addMemberOffsetList(List<Integer> result, IMember[] members) throws JavaModelException {
		for (int i= 0; i < members.length; i++) {
			addOffset(result, getOffset(members[i]));
		}
	}

	private static int getOffset(IMember iMember) throws JavaModelException {
		//special case
		if (iMember.getElementType() == IJavaElement.INITIALIZER)
			return firstOpeningBraceOffset((IInitializer)iMember);

		if (iMember.getNameRange() != null && iMember.getNameRange().getOffset() >= 0)
			return iMember.getNameRange().getOffset();
		return iMember.getSourceRange().getOffset();
	}

	private static int firstOpeningBraceOffset(IInitializer iInitializer) throws JavaModelException {
		try {
			IScanner scanner= ToolFactory.createScanner(false, false, false, false);
			scanner.setSource(iInitializer.getSource().toCharArray());
			int token= scanner.getNextToken();
			while (token != ITerminalSymbols.TokenNameEOF && token != ITerminalSymbols.TokenNameLBRACE)
				token= scanner.getNextToken();
			if (token == ITerminalSymbols.TokenNameLBRACE)
				return iInitializer.getSourceRange().getOffset() + scanner.getCurrentTokenStartPosition() + scanner.getRawTokenSource().length;
			return iInitializer.getSourceRange().getOffset();
		} catch (InvalidInputException e) {
			return iInitializer.getSourceRange().getOffset();
		}
	}

	//-- private helper methods

	private static ISourceRange createSourceRange(ITextSelection ts) {
		return new SourceRange(ts.getOffset(), ts.getLength());
	}

	private static void addOffset(List<Integer> result, int offset) {
		if (offset >= 0)
			result.add(new Integer(offset));
	}
}

package org.eclipse.jdt.postfixcompletion.parser;

import org.eclipse.jdt.internal.ui.text.JavaHeuristicScanner;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

public class SlimStatementParser implements IStatementParser {
	
	private JavaHeuristicScanner _scanner;
	private IDocument _document;
	
	public SlimStatementParser(IDocument document) {
		_document = document;
		_scanner = new JavaHeuristicScanner(document);
	}
	
	/**
	 * Finds the first non white character of the statement the cursor currently is in.
	 * <br/>
	 * If the cursor is not whithin a statement the method returns -1.
	 * 
	 * @param cursorPosition the position of the cursor
	 * @return the first non white character of the current statement. -1 if the given position is not within a valid statemnt.
	 */
	private int findStatementBegin(int cursorPosition) {
		/*
		 * Pseudo code:
		 * - TODO
		 */
		
		IRegion region = _scanner.findSurroundingBlock(cursorPosition);
		
		return 0; // XXX
		
	}
	

}

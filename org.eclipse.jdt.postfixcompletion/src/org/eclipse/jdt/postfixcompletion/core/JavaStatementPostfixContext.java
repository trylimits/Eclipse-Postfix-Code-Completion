package org.eclipse.jdt.postfixcompletion.core;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.FieldReference;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.corext.template.java.JavaContext;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateContextType;

@SuppressWarnings("restriction")
public class JavaStatementPostfixContext extends JavaContext {

	private static final Object CONTEXT_TYPE_ID = "postfix";
	
	protected ASTNode currentNode;
	protected ASTNode parentNode;

	public JavaStatementPostfixContext(TemplateContextType type,
			IDocument document, final int completionOffset, int completionLength,
			ICompilationUnit compilationUnit) {
		super(type, document, completionOffset, completionLength,
				compilationUnit);
	}

	public JavaStatementPostfixContext(TemplateContextType type,
			IDocument document, Position completionPosition,
			ICompilationUnit compilationUnit) {
		super(type, document, completionPosition, compilationUnit);

	}
	
	public JavaStatementPostfixContext(
			JavaStatementPostfixContextType type,
			IDocument document, int offset, int length,
			ICompilationUnit compilationUnit,
			ASTNode currentNode,
			ASTNode parentNode) {
		super(type, document, offset, length, compilationUnit);
		this.currentNode = currentNode;
		this.parentNode = parentNode;
	}

	/*
	 * @see TemplateContext#canEvaluate(Template templates)
	 */
	@Override
	public boolean canEvaluate(Template template) {

		if (!template.getContextTypeId().equals(
				JavaStatementPostfixContext.CONTEXT_TYPE_ID))
			return false;

		if (fForceEvaluation)
			return true;
		
		// if the typed in prefix does not match the template name we immediately return false
		if ( template.getName().toLowerCase()
				.startsWith(getPrefixKey().toLowerCase()) == false) {
			return false;
		}
		
		// We check if the template make "sense" by checking the requirements for the template
		
		return true;
	}

	protected String getPrefixKey() {
		IDocument document = getDocument();
		int start = getStart();
		int end = getEnd();
		try {
			return document.get(start, end - start);
		} catch (BadLocationException e) {
			return "";
		}
	}
	
	@Override
	public int getEnd() {
		return getCompletionOffset();
	}
	
	@Override
	public int getStart() {
		// TODO this is not 100% correct atm
		if (currentNode != null) {
			getNodeBegin(currentNode);
		}
		return super.getStart();
	}

	public String getOuterExpression() {

		return ""; // TODO
	}
	
	private int getNodeBegin(ASTNode node) {
		if (node instanceof NameReference) {
			return ((NameReference) node).sourceStart;
		} else if (node instanceof FieldReference) {
			return ((FieldReference) node).receiver.sourceStart;
		} else if (node instanceof MessageSend) {
			return ((MessageSend)node).receiver.sourceStart;
		}
		return node.sourceStart;
	}
	
	public Region getAffectedSourceRegion() {
		return new Region(getStart(), (getCompletionOffset() - getPrefixKey().length()) - getStart());
	}
	
	public String getAffectedStatement() {
		Region r = getAffectedSourceRegion();
		try {
			return getDocument().get(r.getOffset(), r.getLength());
		} catch (BadLocationException e) {
		}
		return "";
	}
	
	public boolean isNodeResolvingTo(ASTNode node, String signature) {
		return true;
	}
	
	public boolean isNodeComplexType(ASTNode node, String signature) {
		return true;
	}
	
	public boolean isNodeBooleanExpression(ASTNode node) {
		return true;
	}
}

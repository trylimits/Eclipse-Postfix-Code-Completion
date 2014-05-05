package org.eclipse.jdt.postfixcompletion.core;

import java.util.Arrays;

import javax.swing.plaf.synth.SynthScrollBarUI;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.FieldReference;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.compiler.lookup.VariableBinding;
import org.eclipse.jdt.internal.corext.template.java.JavaContext;
import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;
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
		
//		System.out.println("JavaStatementPostfixContext.canEvaluate()");

		if (!template.getContextTypeId().equals(
				JavaStatementPostfixContext.CONTEXT_TYPE_ID))
			return false;

		if (fForceEvaluation)
			return true;
		
		if (currentNode == null) // We can evaluate to true only if we have a valid inner expression
			return false;
		
		// if the typed in prefix does not match the template name we immediately return false
//		System.out.println("Current prefix: " + getPrefixKey());
		if ( template.getName().toLowerCase()
				.startsWith(getPrefixKey().toLowerCase()) == false) {
//			System.out.println("Not triggered bcause prefix mismatch");
			return false;
		}
		
		// We check if the template make "sense" by checking the requirements for the template

		
		return true;
	}

	protected String getPrefixKey() {
//		System.out.println("JavaStatementPostfixContext.getPrefixKey()");
		IDocument document = getDocument();
		int start = getCompletionOffset();
		int end = getEnd();
		try {
			String temp = document.get(start, 1);
			while (!".".equals(temp)) {
				temp = document.get(--start, 1);
			}
//			System.out.println(document.get(start + 1, end - start - 1));
			return document.get(start + 1, end - start - 1);
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
		
//		System.out.println("JavaStatementPostfixContext.getStart()");
		// TODO this is not 100% correct atm
//		if (currentNode != null) {
//			return getNodeBegin(currentNode);
//		}
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
		int start = getNodeBegin(currentNode);
		return new Region(start, (getCompletionOffset() - getPrefixKey().length()) - start - 1);
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

	private String resolveNodeToTypeString(ASTNode node) {
		
		if (node instanceof NameReference) {
    		NameReference nr = (NameReference) node;
			if (nr.binding instanceof VariableBinding) {
				VariableBinding vb = (VariableBinding) nr.binding;
				return new String(vb.type.readableName());
			}
		} else if (node instanceof FieldReference) {
    		FieldReference fr = (FieldReference) node;
    		
			return new String(fr.receiver.resolvedType.readableName());
		}
		// TODO
		return "java.lang.Object";
	}
	
	public String getInnerExpressionType() {
		return resolveNodeToTypeString(currentNode);
	}
}

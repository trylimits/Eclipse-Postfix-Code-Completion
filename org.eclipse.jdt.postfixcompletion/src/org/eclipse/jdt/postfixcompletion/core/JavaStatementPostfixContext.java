package org.eclipse.jdt.postfixcompletion.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.FieldReference;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.compiler.lookup.ArrayBinding;
import org.eclipse.jdt.internal.compiler.lookup.BaseTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.ParameterizedTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.VariableBinding;
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
		
		if (currentNode == null) // We can evaluate to true only if we have a valid inner expression
			return false;
		
		if ( template.getName().toLowerCase()
				.startsWith(getPrefixKey().toLowerCase()) == false) {
			return false;
		}
		
		// We check if the template make "sense" by checking the requirements for the template
		// For this purpose we have to resolve the inner_expression variable of the template
		String regex = ("\\$\\{([a-zA-Z]+):inner_expression\\(([^\\$|\\{|\\}]*)\\)\\}");
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(template.getPattern());
		boolean result = (matcher.groupCount() > 0) ? false : true;
		
		while (matcher.find()) {
			String[] types = matcher.group(2).split(",");
			for (String s : types) {
				if (this.isNodeResolvingTo(currentNode, s.trim()) == true) {
					return true;
				}
			}
	    }
		
		return result;
	}
	
	
//	
//	private boolean stringToBoolean(String input) {
//		return "true".equalsIgnoreCase(input);
//	}

	protected String getPrefixKey() {
		IDocument document = getDocument();
		int start = getCompletionOffset();
		int end = getEnd();
		try {
			String temp = document.get(start, 1);
			while (!".".equals(temp)) {
				temp = document.get(--start, 1);
			}
			return document.get(start + 1, end - start - 1);
		} catch (BadLocationException e) {
			return "";
		}
	}
	
	@Override
	public int getEnd() {
		return getCompletionOffset();
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
		if (signature == null || signature.trim().length() == 0) {
			return true;
		}
		Binding b = resolveNodeToBinding(node);
		if (b instanceof ParameterizedTypeBinding) {
			ParameterizedTypeBinding ptb = (ParameterizedTypeBinding) b;
			return resolvesReferenceBindingTo(ptb.actualType(), signature);
		} else if (b instanceof BaseTypeBinding) {
			return (new String(b.readableName()).equals(signature));
		} else if (b instanceof TypeBinding) {
			return resolvesReferenceBindingTo((TypeBinding) b, signature);
		}
		
		return true;
	}
	
	private boolean resolvesReferenceBindingTo(TypeBinding sb, String signature) {
		if (sb == null) {
			return false;
		}
		
		if (new String(sb.readableName()).startsWith(signature) || (sb instanceof ArrayBinding && "array".equals(signature))) {
			return true;
		}
		List<ReferenceBinding> bindings = new ArrayList<>();
		Collections.addAll(bindings, sb.superInterfaces());
		bindings.add(sb.superclass());
		boolean result = false;
		Iterator<ReferenceBinding> it = bindings.iterator();
		while (it.hasNext() && result == false) {
			result = resolvesReferenceBindingTo(it.next(), signature);
		}
		return result;
	}
	
	public boolean resolvesReferenceBindingToArray(TypeBinding sb) {
		return sb instanceof ArrayBinding;
	}
	
	public boolean isNodePrimitiveType(ASTNode node) {
		return true;
	}
	
	public boolean isNodeBooleanExpression(ASTNode node) {
		return true;
	}
	
	private Binding resolveNodeToBinding(ASTNode node) {
		if (node instanceof NameReference) {
    		NameReference nr = (NameReference) node;
			if (nr.binding instanceof VariableBinding) {
				VariableBinding vb = (VariableBinding) nr.binding;
				return vb.type;
			}
		} else if (node instanceof FieldReference) {
    		FieldReference fr = (FieldReference) node;
			return fr.receiver.resolvedType;
		}
		return null;
	}

	private String resolveNodeToTypeString(ASTNode node) {
		Binding b = resolveNodeToBinding(node);
		if (b != null) {
			return new String(b.readableName());
		}
		return "java.lang.Object";
	}
	
	public String getInnerExpressionType() {
		return resolveNodeToTypeString(currentNode);
	}
}

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

/**
 * This class is an extension to the existing {@link JavaContext} and includes/provides additional information
 * on the current node which the code completion was invoked on.
 * <br/>
 * <br/>
 * TODO Atm this class is dependent on non-published changes of the class {@link JavaContext}.
 */
@SuppressWarnings("restriction")
public class JavaStatementPostfixContext extends JavaContext {

	private static final Object CONTEXT_TYPE_ID = "postfix"; //$NON-NLS-1$
	private static final String OBJECT_SIGNATURE = "java.lang.Object"; //$NON-NLS-1$
	
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
		
		if (template.getName().toLowerCase().startsWith(getPrefixKey().toLowerCase()) == false) {
			return false;
		}
		
		// We check if the template makes "sense" by checking the requirements/conditoins for the template
		// For this purpose we have to resolve the inner_expression variable of the template
		// This approach is much faster then delegating this to the existing TemplateTranslator class
		
		String regex = ("\\$\\{([a-zA-Z]+):inner_expression\\(([^\\$|\\{|\\}]*)\\)\\}"); // TODO Review this regex
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(template.getPattern());
		boolean result = true;
		
		while (matcher.find()) {
			String[] types = matcher.group(2).split(",");
			for (String s : types) {
				result = false;
				if (this.isNodeResolvingTo(currentNode, s.trim()) == true) {
					return true;
				}
			}
	    }
		
		return result;
	}

	/**
	 * Returns the current prefix of the key which was typed in.
	 * <br/>
	 * Examples:
	 * <code>
	 * <br/>
	 * new Object().		=> getPrefixKey() returns ""
	 * new Object().a		=> getPrefixKey() returns "a"
	 * new object().asdf	=> getPrefixKey() returns "asdf"
	 * 
	 * @return an empty string or a string which represents the prefix of the key which was typed in
	 */
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

	@Deprecated
	public String getOuterExpression() {
		return ""; // XXX This method is not used anymore
	}
	
	/**
	 * Calculates the beginning position of a given {@link ASTNode}
	 * @param node
	 * @return
	 */
	protected int getNodeBegin(ASTNode node) {
		if (node instanceof NameReference) {
			return ((NameReference) node).sourceStart;
		} else if (node instanceof FieldReference) {
			return ((FieldReference) node).receiver.sourceStart;
		} else if (node instanceof MessageSend) {
			return ((MessageSend) node).receiver.sourceStart;
		}
		return node.sourceStart;
	}
	
	/**
	 * Returns the {@link Region} which represents the source region of the affected statement.
	 * @return
	 */
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
	
	/**
	 * Returns <code>true</code> if the type or one of its supertypes of a given {@link ASTNode} resolves to a given type signature.
	 * <br/>
	 * Examples:
	 * <br/>
	 * <code>
	 * <br/>
	 * isNodeResolvingTo(node of type java.lang.String, "java.lang.Object") returns true<br/>
	 * isNodeResolvingTo(node of type java.lang.String, "java.lang.Iterable") returns false<br/>
	 * </code>
	 * 
	 * TODO Implement this method without using the recursive helper method if there are any performance/stackoverflow issues
	 * 
	 * @param node an ASTNode
	 * @param signature a fully qualified type
	 * @return true if the type of the given ASTNode itself or one of its superclass/superinterfaces resolves to the given signature. false otherwise.
	 */
	protected boolean isNodeResolvingTo(ASTNode node, String signature) {
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
	
	/**
	 * This is a recursive method which performs a depth first search in the inheritance graph of the given {@link TypeBinding}.
	 * 
	 * @param sb a TypeBinding
	 * @param signature a fully qualified type
	 * @return true if the given TypeBinding itself or one of its superclass/superinterfaces resolves to the given signature. false otherwise.
	 */
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
	
	protected boolean resolvesReferenceBindingToArray(TypeBinding sb) {
		return sb instanceof ArrayBinding;
	}
	
	protected boolean isNodeOfBaseType(ASTNode node) {
		return !isNodeResolvingTo(node, OBJECT_SIGNATURE);
	}
	
	protected boolean isNodeBooleanExpression(ASTNode node) {
		return isNodeResolvingTo(node, "boolean");
	}
	
	protected Binding resolveNodeToBinding(ASTNode node) {
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

	protected String resolveNodeToTypeString(ASTNode node) {
		Binding b = resolveNodeToBinding(node);
		if (b != null) {
			return new String(b.readableName());
		}
		return OBJECT_SIGNATURE;
	}
	
	/**
	 * Returns the fully qualified name the node of the current code completion invocation resolves to.
	 * 
	 * @return a fully qualified type signature or the name of the base type.
	 */
	public String getInnerExpressionTypeSignature() {
		return resolveNodeToTypeString(currentNode);
	}
}

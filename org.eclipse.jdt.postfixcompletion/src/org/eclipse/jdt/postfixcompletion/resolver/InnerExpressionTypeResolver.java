package org.eclipse.jdt.postfixcompletion.resolver;

import org.eclipse.jdt.postfixcompletion.core.JavaStatementPostfixContext;
import org.eclipse.jface.text.templates.SimpleTemplateVariableResolver;
import org.eclipse.jface.text.templates.TemplateContext;

/**
 * @deprecated This is not used anymore and should be replaced by other <code>VariableResolver</code>, i.e. {@link VariableTypeResolver} or {@link ActualTypeResolver}
 */
@Deprecated
public class InnerExpressionTypeResolver extends SimpleTemplateVariableResolver {
	
	public static final String INNER_EXPRESSION_TYPE_VAR = "inner_expression_type"; //$NON-NLS-1$
	
	public InnerExpressionTypeResolver() {
		super(INNER_EXPRESSION_TYPE_VAR, "Resolves to the type of the affected inner expression."); // TODO Export desc string to messages file //$NON-NLS-1$
	}
	
	protected String resolve(TemplateContext context) {
		if (!(context instanceof JavaStatementPostfixContext)) 
			return "";
		
		JavaStatementPostfixContext c = (JavaStatementPostfixContext) context;
		
		return ((JavaStatementPostfixContext)context).getInnerExpressionTypeSignature();
	}

}

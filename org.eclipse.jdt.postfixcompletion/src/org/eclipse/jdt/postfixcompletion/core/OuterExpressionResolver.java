package org.eclipse.jdt.postfixcompletion.core;

import org.eclipse.jface.text.templates.SimpleTemplateVariableResolver;
import org.eclipse.jface.text.templates.TemplateContext;

@Deprecated
public class OuterExpressionResolver extends SimpleTemplateVariableResolver {
	
	public OuterExpressionResolver() {
		super("outer_expression", "");
	}
	
	protected String resolve(TemplateContext context) {
		if (!(context instanceof JavaStatementPostfixContext)) 
			return "";
		
		JavaStatementPostfixContext c = (JavaStatementPostfixContext) context;
		
		return c.getOuterExpression();
	}

}

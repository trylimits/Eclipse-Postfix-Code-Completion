package org.eclipse.jdt.postfixcompletion.core;

import org.eclipse.jdt.internal.corext.template.java.FieldResolver;
import org.eclipse.jface.text.templates.SimpleTemplateVariableResolver;
import org.eclipse.jface.text.templates.TemplateContext;

public class OuterExpressionResolver extends SimpleTemplateVariableResolver {
	
	public OuterExpressionResolver() {
		super("outer_expression", "test");
	}
	
	protected String resolve(TemplateContext context) {
		if (!(context instanceof JavaStatementPostfixContext)) 
			return "";
		
		JavaStatementPostfixContext c = (JavaStatementPostfixContext) context;
		
		return ((JavaStatementPostfixContext)context).getOuterExpression();
	}

}

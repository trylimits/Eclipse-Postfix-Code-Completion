package org.eclipse.jdt.postfixcompletion.core;

import org.eclipse.jdt.internal.corext.template.java.FieldResolver;
import org.eclipse.jface.text.templates.SimpleTemplateVariableResolver;
import org.eclipse.jface.text.templates.TemplateContext;

public class InnerExpressionResolver extends SimpleTemplateVariableResolver {
	
	public static final String INNER_EXPRESSION_VAR = "inner_expression";
	
	public InnerExpressionResolver() {
		super(INNER_EXPRESSION_VAR, "test");
	}
	
	protected String resolve(TemplateContext context) {
		if (!(context instanceof JavaStatementPostfixContext)) 
			return "";
		
		JavaStatementPostfixContext c = (JavaStatementPostfixContext) context;
		
		return ((JavaStatementPostfixContext)context).getAffectedStatement();
	}

}

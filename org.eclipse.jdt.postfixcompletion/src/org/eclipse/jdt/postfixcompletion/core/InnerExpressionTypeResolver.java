package org.eclipse.jdt.postfixcompletion.core;

import org.eclipse.jdt.internal.corext.template.java.FieldResolver;
import org.eclipse.jface.text.templates.SimpleTemplateVariableResolver;
import org.eclipse.jface.text.templates.TemplateContext;

public class InnerExpressionTypeResolver extends SimpleTemplateVariableResolver {
	
	public static final String INNER_EXPRESSION_TYPE_VAR = "inner_expression_type";
	
	public InnerExpressionTypeResolver() {
		super(INNER_EXPRESSION_TYPE_VAR, "Resolves to the type of the affected inner expression.");
	}
	
	protected String resolve(TemplateContext context) {
		if (!(context instanceof JavaStatementPostfixContext)) 
			return "";
		
		JavaStatementPostfixContext c = (JavaStatementPostfixContext) context;
		
		return ((JavaStatementPostfixContext)context).getInnerExpressionType();
	}

}

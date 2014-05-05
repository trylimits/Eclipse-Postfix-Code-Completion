package org.eclipse.jdt.postfixcompletion.core;

import org.eclipse.jdt.internal.corext.template.java.JavaVariable;
import org.eclipse.jface.text.templates.SimpleTemplateVariableResolver;
import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.jface.text.templates.TemplateVariable;


@SuppressWarnings("restriction")
public class InnerExpressionResolver extends SimpleTemplateVariableResolver {
	
	public static final String INNER_EXPRESSION_VAR = "inner_expression";
	
	public InnerExpressionResolver() {
		super(INNER_EXPRESSION_VAR, "test");
	}
	
	protected String resolve(TemplateContext context) {
		if (!(context instanceof JavaStatementPostfixContext)) 
			return "";
		
		return ((JavaStatementPostfixContext)context).getAffectedStatement();
	}
	
	@Override
	public void resolve(TemplateVariable variable, TemplateContext context) {
		if (context instanceof JavaStatementPostfixContext && variable instanceof JavaVariable) {
			JavaStatementPostfixContext c = (JavaStatementPostfixContext) context;
			JavaVariable jv = (JavaVariable) variable;
			jv.setValue(resolve(context));
			jv.setParamType(c.getInnerExpressionType());
			jv.setResolved(true);
			return;
		}
		super.resolve(variable, context);
	}

}

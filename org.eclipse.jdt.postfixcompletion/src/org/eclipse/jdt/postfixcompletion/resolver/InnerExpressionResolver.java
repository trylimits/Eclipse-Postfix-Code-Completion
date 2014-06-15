package org.eclipse.jdt.postfixcompletion.resolver;

import java.util.List;

import org.eclipse.jdt.internal.corext.template.java.JavaVariable;
import org.eclipse.jdt.postfixcompletion.core.JavaStatementPostfixContext;
import org.eclipse.jface.text.templates.SimpleTemplateVariableResolver;
import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.jface.text.templates.TemplateVariable;


/**
 * This class is a resolver for the variable <code>inner_expression</code>.
 * <br/>
 * The value of the resolved variable will be the source code of the node which resolves to the <code>inner_expression</code>
 * The type of the resolved variable will be the fully qualified name or the name of the base type of the node which resolves to the <code>inner_expression</code>.
 */
@SuppressWarnings("restriction")
public class InnerExpressionResolver extends SimpleTemplateVariableResolver {
	
	public static final String INNER_EXPRESSION_VAR = "inner_expression"; //$NON-NLS-1$
	
	public static final String HIDE_FLAG = "novalue";
	
	public static final String[] FLAGS = { HIDE_FLAG };
	
	public InnerExpressionResolver() {
		super(INNER_EXPRESSION_VAR, ""); // TODO Add description
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
			List<String> params = variable.getVariableType().getParams();
			
			if (!params.contains(HIDE_FLAG)) {
				jv.setValue(resolve(context));
			} else {
				jv.setValues(new String[] { "", resolve(context) }); // We hide the value from the output
			}
			
			jv.setParamType(c.getInnerExpressionTypeSignature());
			jv.setResolved(true);
			jv.setUnambiguous(true);
			return;
		}
		super.resolve(variable, context);
	}
	
}

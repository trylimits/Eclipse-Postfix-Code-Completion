package org.eclipse.jdt.postfixcompletion.resolver;

import java.util.List;

import org.eclipse.jdt.internal.corext.template.java.JavaContext;
import org.eclipse.jdt.internal.corext.template.java.JavaVariable;
import org.eclipse.jdt.internal.corext.template.java.TypeResolver;
import org.eclipse.jdt.internal.ui.text.template.contentassist.MultiVariable;
import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.jface.text.templates.TemplateVariable;

/**
 * This class is an extension to the existing {@link TypeResolver} and allows the given parameter
 * to be another variable, i.e. <code>${n:newType(another_variable)}</code>.
 */
@SuppressWarnings("restriction")
public class VariableTypeResolver extends TypeResolver {

	@Override
	public void resolve(TemplateVariable variable, TemplateContext context) {
		List<String> params= variable.getVariableType().getParams();
		if (params.size() != 0 && context instanceof JavaContext) {
			String param = params.get(0);
			JavaContext jc = (JavaContext) context;
			TemplateVariable ref = jc.getTemplateVariable(param);
			MultiVariable mv= (MultiVariable) variable;
			
			if (ref instanceof JavaVariable) {
				// Reference is another variable
				JavaVariable refVar= (JavaVariable) ref;
				jc.addDependency(refVar, mv);
				
				param = refVar.getParamType();
				if (param != null && "".equals(param) == false) {
					
					String reference= jc.addImport(param);
					mv.setValue(reference);
					mv.setUnambiguous(true);
		
					mv.setResolved(true);
					return;
				}
			}
		}
		super.resolve(variable, context);
	}
}

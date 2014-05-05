package org.eclipse.jdt.postfixcompletion.core;

import java.util.List;

import org.eclipse.jdt.internal.corext.template.java.JavaContext;
import org.eclipse.jdt.internal.corext.template.java.JavaVariable;
import org.eclipse.jdt.internal.corext.template.java.TypeResolver;
import org.eclipse.jdt.internal.ui.text.template.contentassist.MultiVariable;
import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.jface.text.templates.TemplateVariable;

@SuppressWarnings("restriction")
public class ActualTypeResolver extends TypeResolver {

	@Override
	public void resolve(TemplateVariable variable, TemplateContext context) {
		List<String> params= variable.getVariableType().getParams();
		String param;
		if (params.size() != 0 && context instanceof JavaContext) {
			param = params.get(0);
			JavaContext jc= (JavaContext) context;
			TemplateVariable ref= jc.getTemplateVariable(param);
			MultiVariable mv= (MultiVariable) variable;
			if (ref instanceof JavaVariable) {
				// reference is another variable
				JavaVariable refVar= (JavaVariable) ref;
				jc.addDependency(refVar, mv);
				
				param = refVar.getParamType();
				if (param != null && "".equals(param) == false) {
					param = param.replace("[]", ""); // Remove Array signature
					if (param.contains("<")) {
						// This is a generic type
						String endChar = (param.contains(",") ? ";" : ">");
						param = param.substring(param.indexOf("<") + 1, param.indexOf(endChar));
					}
					
//					String reference= jc.addImport(param);
					mv.setValue(param);
					mv.setUnambiguous(true);
		
					mv.setResolved(true);
					return;
				}
			}
		}
		super.resolve(variable, context);
	}
}

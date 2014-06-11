package org.eclipse.jdt.postfixcompletion.resolver;

import java.util.List;

import org.eclipse.jdt.internal.corext.template.java.JavaVariable;
import org.eclipse.jdt.internal.ui.text.template.contentassist.MultiVariable;
import org.eclipse.jdt.postfixcompletion.core.JavaStatementPostfixContext;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.jface.text.templates.TemplateVariable;
import org.eclipse.jface.text.templates.TemplateVariableResolver;
import org.eclipse.text.edits.TextEdit;

@SuppressWarnings("restriction")
public class NewFieldResolver extends TemplateVariableResolver {


	private final String defaultType;

	public NewFieldResolver() {
		this("java.lang.Object"); //$NON-NLS-1$
	}

	NewFieldResolver(String defaultType) {
		this.defaultType = defaultType;
	}

	/*
	 * @see org.eclipse.jface.text.templates.TemplateVariableResolver#resolve(org.eclipse.jface.text.templates.TemplateVariable, org.eclipse.jface.text.templates.TemplateContext)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void resolve(TemplateVariable variable, TemplateContext context) {
		List<String> params = variable.getVariableType().getParams();
		String param;
		if (params.size() == 0)
			param = defaultType;
		else
			param = params.get(0);
		
		JavaStatementPostfixContext jc = (JavaStatementPostfixContext) context;
		
		TemplateVariable ref = jc.getTemplateVariable(param);
		
		MultiVariable mv = (MultiVariable) variable;
		
		String type = param;
		
		if (ref instanceof MultiVariable) {
			// Reference is another variable
			JavaVariable refVar = (JavaVariable) ref;
			jc.addDependency(refVar, mv);

			type = refVar.getParamType();
		}

//		String newType = jc.addImport(type);
		String newType = type;
		String[] names = jc.suggestFieldName(newType);
		
		mv.setChoices(names);
		
		TextEdit te = jc.addField(newType, names[0]);
		if (te != null) {
			jc.markAsUsed(names[0]);
			// We can apply it to the context
			jc.applyTextEdit(te);
			mv.setResolved(true);
			
			jc.registerOutOfRangeOffset(mv, findAbsolutePositionOfFieldNameBeginning(jc.getDocument(), te, names[0]) - jc.getAffectedSourceRegion().getOffset());
		} else {
			// Field was not created so better do nothing
		}
	}
	
	private int findAbsolutePositionOfFieldNameBeginning(IDocument doc, TextEdit te, String name) {
		try {
			String temp = doc.get(te.getOffset(), te.getLength());
			int nameOcc = temp.lastIndexOf(name);
			if (nameOcc != -1) {
				return te.getOffset() + nameOcc;
			}
			// Variable name not included
			// We try if we can determine the position with some basic logic
			// This case should not happen but it's a good idea to have a fallback
			int offset = temp.length();
			boolean semiColonFound = false;
			while (offset >= 0 && semiColonFound == false) {
				if (temp.charAt(offset) == ' ' && semiColonFound) return te.getOffset() + offset;
				if (temp.charAt(offset) == ';') semiColonFound = true;
			}
			
		} catch (BadLocationException e) {

		}
		return te.getOffset();
	}
}

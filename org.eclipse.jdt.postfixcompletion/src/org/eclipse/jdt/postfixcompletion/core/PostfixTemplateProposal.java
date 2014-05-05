package org.eclipse.jdt.postfixcompletion.core;

import org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateProposal;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.swt.graphics.Image;

@SuppressWarnings("restriction")
public class PostfixTemplateProposal extends TemplateProposal {

	public PostfixTemplateProposal(Template template, TemplateContext context,
			IRegion region, Image image) {
		super(template, context, region, image);
	}
	
	@Override
	protected int getReplaceOffset() {
		if (getContext() instanceof JavaStatementPostfixContext) {
			return ((JavaStatementPostfixContext)getContext()).getAffectedSourceRegion().getOffset();
		}
		System.out.println("PostfixTemplateProposal.getReplaceOffset()");
		return super.getReplaceOffset();
	}
	
	@Override
	public boolean validate(IDocument document, int offset, DocumentEvent event) {
		if (getContext() instanceof JavaStatementPostfixContext) {
			JavaStatementPostfixContext c = (JavaStatementPostfixContext) getContext();
			return this.getTemplate().getName().toLowerCase().startsWith(c.getPrefixKey().toLowerCase());
		}
		return super.validate(document, offset, event);
	}
}

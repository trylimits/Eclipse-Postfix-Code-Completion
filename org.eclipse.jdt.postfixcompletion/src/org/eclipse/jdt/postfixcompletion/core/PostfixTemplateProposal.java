package org.eclipse.jdt.postfixcompletion.core;

import org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateProposal;
import org.eclipse.jface.text.BadLocationException;
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
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateProposal#getReplaceOffset()
	 */
	@Override
	protected int getReplaceOffset() {
		if (getContext() instanceof JavaStatementPostfixContext) {
			return ((JavaStatementPostfixContext)getContext()).getAffectedSourceRegion().getOffset();
		}
		return super.getReplaceOffset();
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateProposal#validate(org.eclipse.jface.text.IDocument, int, org.eclipse.jface.text.DocumentEvent)
	 */
	@Override
	public boolean validate(IDocument document, int offset, DocumentEvent event) {
		if (getContext() instanceof JavaStatementPostfixContext) {
			JavaStatementPostfixContext c = (JavaStatementPostfixContext) getContext();
			try {
				String content = document.get(c.getStart(), offset - c.getStart());
				return this.getTemplate().getName().toLowerCase().startsWith(content.toLowerCase());
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
		return super.validate(document, offset, event);
	}
}

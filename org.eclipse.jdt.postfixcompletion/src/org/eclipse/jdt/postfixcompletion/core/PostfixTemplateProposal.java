package org.eclipse.jdt.postfixcompletion.core;

import org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateProposal;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.swt.graphics.Image;

/**
 * This is an extension to the existing {@link TemplateProposal} class.
 * <br/>
 * The class overrides the methods {@link #getReplaceEndOffset()} and {@link #validate(IDocument, int, DocumentEvent)} to
 * allow replacement of existing code input and fixes a bug in the {@link TemplateProposal#validate(IDocument, int, DocumentEvent)} method. The <code>validate(..)</code> method
 * in the super implementation should not use {@link #getReplaceEndOffset()} to determine the prefix of the typed in template name.
 */
@SuppressWarnings("restriction")
public class PostfixTemplateProposal extends TemplateProposal {

	public PostfixTemplateProposal(Template template, TemplateContext context,
			IRegion region, Image image) {
		super(template, context, region, image);
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
				int start = c.getStart() + c.getAffectedSourceRegion().getLength() + 1;
				String content = document.get(start, offset - start);
				return this.getTemplate().getName().toLowerCase().startsWith(content.toLowerCase());
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
		return super.validate(document, offset, event);
	}
}

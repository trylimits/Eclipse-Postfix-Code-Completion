package org.eclipse.jdt.postfixcompletion.core;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateEngine;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.jface.text.templates.TemplateProposal;
import org.eclipse.swt.graphics.Point;

/**
 * This is an extension to the existing {@link TemplateEngine} and is responsible for the creation of the {@link JavaStatementPostfixContext} instance.
 * TODO If the super implementation of the {@link TemplateEngine#complete(ITextViewer, int, ICompilationUnit)} is rewritten and the creation of the context and {@link TemplateProposal} is
 * externalized to its own method we can get rid of the code duplication in the <code>complete(..)</code> method. 
 *
 */
@SuppressWarnings("restriction")
public class PostfixTemplateEngine extends TemplateEngine {
	
	protected ASTNode currentNode;
	protected ASTNode parentNode;

	public PostfixTemplateEngine(TemplateContextType contextType) {
		super(contextType);
	}
	
	public void setASTNodes(ASTNode currentNode, ASTNode parentNode) {
		this.currentNode = currentNode;
		this.parentNode = parentNode;
	}
	
	@Override
	public void complete(ITextViewer viewer, int completionPosition, ICompilationUnit compilationUnit) {
		IDocument document = viewer.getDocument();

		if (!(fContextType instanceof JavaStatementPostfixContextType))
			return;

		Point selection = viewer.getSelectedRange();

		String selectedText = null;
		if (selection.y != 0) {
			return;
		}

		JavaStatementPostfixContext context = ((JavaStatementPostfixContextType) fContextType).createContext(document, completionPosition, selection.y, compilationUnit, currentNode, parentNode);
		context.setVariable("selection", selectedText); //$NON-NLS-1$
		int start = context.getStart();
		int end = context.getEnd();
		IRegion region = new Region(start, end - start);

		Template[] templates = JavaPlugin.getDefault().getTemplateStore().getTemplates(fContextType.getId());

		for (int i = 0; i != templates.length; i++) {
			Template template = templates[i];
			if (context.canEvaluate(template)) {
				fProposals.add(new PostfixTemplateProposal(template, context, region, getImage()));
			}
		}
	}
}

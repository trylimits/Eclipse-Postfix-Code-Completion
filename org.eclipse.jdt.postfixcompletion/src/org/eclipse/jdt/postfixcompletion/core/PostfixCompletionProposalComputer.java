package org.eclipse.jdt.postfixcompletion.core;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.codeassist.InternalCompletionContext;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.java.AbstractTemplateCompletionProposalComputer;
import org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateEngine;
import org.eclipse.jdt.ui.text.java.CompletionProposalCollector;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.templates.ContextTypeRegistry;
import org.eclipse.jface.text.templates.TemplateContextType;

@SuppressWarnings("restriction")
public class PostfixCompletionProposalComputer extends AbstractTemplateCompletionProposalComputer {
	
	private final PostfixTemplateEngine postfixCompletionTemplateEngine;
	
	public PostfixCompletionProposalComputer() {
		ContextTypeRegistry templateContextRegistry = JavaPlugin.getDefault().getTemplateContextRegistry();
		postfixCompletionTemplateEngine = createTemplateEngine(templateContextRegistry, JavaStatementPostfixContextType.ID_ALL);
	}
	
	private static PostfixTemplateEngine createTemplateEngine(ContextTypeRegistry templateContextRegistry, String contextTypeId) {
		TemplateContextType contextType = templateContextRegistry.getContextType(contextTypeId);
		Assert.isNotNull(contextType);
		return new PostfixTemplateEngine(contextType);
	}

	@Override
	protected TemplateEngine computeCompletionEngine(JavaContentAssistInvocationContext context) {
		ICompilationUnit unit = context.getCompilationUnit();
		if (unit == null)
			return null;

		IJavaProject javaProject = unit.getJavaProject();
		if (javaProject == null)
			return null;

		CompletionContext coreContext = context.getCoreContext();
		if (coreContext != null) {
			int tokenLocation= coreContext.getTokenLocation();
			int tokenStart= coreContext.getTokenStart();
			int tokenKind= coreContext.getTokenKind();
			
			/*
			// XXX print out tokenlocation stuff (debugging)
			System.out.println("All Tokens: " + CompletionContext.TL_CONSTRUCTOR_START + " " + CompletionContext.TL_MEMBER_START + " " + CompletionContext.TL_STATEMENT_START);
			System.out.println("Token Start: " + coreContext.getTokenStart());
			System.out.println("Token End: " + coreContext.getTokenEnd());
			System.out.println("Token Kind: " + coreContext.getTokenKind());
			System.out.println("Token Location: " + coreContext.getTokenLocation());
			System.out.println("Enclosing Element: " + coreContext.getEnclosingElement());
			System.out.println("Offset: " + coreContext.getOffset());
			System.out.println("Token Array: " + Arrays.toString(coreContext.getToken()));
			System.out.println("Kind Tokens: " + CompletionContext.TOKEN_KIND_NAME + ", " + CompletionContext.TOKEN_KIND_STRING_LITERAL + ", " + CompletionContext.TOKEN_KIND_UNKNOWN);
			*/
			if (context.getViewer().getSelectedRange().y > 0) { // If there is an active selection we do not want to contribute to the CA
				return null;
			}
			
			if ((tokenLocation == 0 && tokenStart > -1)
					|| ((tokenLocation & CompletionContext.TL_MEMBER_START) != 0 && tokenKind == CompletionContext.TOKEN_KIND_NAME && tokenStart > -1)
					|| (tokenLocation == 0 && isAfterDot(context.getDocument(), context.getInvocationOffset()))) {
				
				analyzeCoreContext(context, coreContext);

				return postfixCompletionTemplateEngine;
			}
		}
		return null;
	}

	private void analyzeCoreContext(JavaContentAssistInvocationContext context,
			CompletionContext coreContext) {
		// Fetch the information of the InternalCompletionContext and update our template engine
		if (coreContext instanceof InternalCompletionContext && ((InternalCompletionContext)coreContext).isExtended()) {
		    updateTemplateEngine((InternalCompletionContext)coreContext);
		    
		} else if (coreContext instanceof InternalCompletionContext && ((InternalCompletionContext)coreContext).isExtended() == false) {
			// If the coreContext is not extended atm for some reason we have to extend it ourself in order to the needed information
			final ICompilationUnit cu = context.getCompilationUnit();
		    final CompletionProposalCollector collector = new CompletionProposalCollector(cu) {
		        @Override
		        public void acceptContext(final CompletionContext context) {
		            super.acceptContext(context);
		            updateTemplateEngine((InternalCompletionContext) context);
		        }
		    };
		    collector.setInvocationContext(context);
		    collector.setRequireExtendedContext(true);
		    try {
		        cu.codeComplete(context.getInvocationOffset(), collector);
		    } catch (JavaModelException e) {
		    	
		    }
		}
	}
	
	private void updateTemplateEngine(InternalCompletionContext context) {
        ASTNode completionNode = context.getCompletionNode();
        ASTNode completionNodeParent = context.getCompletionNodeParent();
        postfixCompletionTemplateEngine.setASTNodes(completionNode, completionNodeParent);
	}
	
	/**
	 * Returns true if the given offset is directly after a dot character.
	 * @param document
	 * @param offset
	 * @return true if the given offset is directly after a dot character, false otherwise
	 */
	private boolean isAfterDot(IDocument document, int offset) {
		try {
			return document.get(offset - 1, 1).charAt(0) == '.';
		} catch (BadLocationException e) {
			return false;
		}
	}
}

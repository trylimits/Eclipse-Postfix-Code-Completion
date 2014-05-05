package org.eclipse.jdt.postfixcompletion.core;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.codeassist.InternalCompletionContext;
import org.eclipse.jdt.internal.codeassist.complete.CompletionOnMemberAccess;
import org.eclipse.jdt.internal.codeassist.complete.CompletionOnQualifiedNameReference;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.BinaryExpression;
import org.eclipse.jdt.internal.compiler.ast.FieldReference;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.lookup.VariableBinding;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.java.AbstractTemplateCompletionProposalComputer;
import org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateEngine;
import org.eclipse.jdt.ui.text.java.CompletionProposalCollector;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.templates.ContextTypeRegistry;
import org.eclipse.jface.text.templates.TemplateContextType;

public class PostfixCompletionProposalComputer extends AbstractTemplateCompletionProposalComputer {
	
	private final PostfixTemplateEngine fPostfixCompletionTemplateEngine;
	
	private boolean infixFound;
	
	public PostfixCompletionProposalComputer() {
		ContextTypeRegistry templateContextRegistry = JavaPlugin.getDefault().getTemplateContextRegistry();
		fPostfixCompletionTemplateEngine = createTemplateEngine(templateContextRegistry, JavaStatementPostfixContextType.ID_ALL);
	}
	
	private static PostfixTemplateEngine createTemplateEngine(ContextTypeRegistry templateContextRegistry, String contextTypeId) {
		TemplateContextType contextType = templateContextRegistry.getContextType(contextTypeId);
		Assert.isNotNull(contextType);
		return new PostfixTemplateEngine(contextType);
	}

	@SuppressWarnings("restriction")
	@Override
	public List<ICompletionProposal> computeCompletionProposals(
			ContentAssistInvocationContext context, IProgressMonitor monitor) {
		System.out.println("computeCompletionProposals");
		
		final JavaContentAssistInvocationContext jCtx = (JavaContentAssistInvocationContext) context;
        final InternalCompletionContext coreContext = (InternalCompletionContext) jCtx.getCoreContext();
        
        if (coreContext.isExtended() == false) {
        	final ICompilationUnit cu = jCtx.getCompilationUnit();
            final CompletionProposalCollector collector = new CompletionProposalCollector(cu) {
                @Override
                public void acceptContext(final CompletionContext context) {
                    super.acceptContext(context);
                }
            };
            collector.setInvocationContext(jCtx);
            collector.setRequireExtendedContext(true);
            try {
                cu.codeComplete(jCtx.getInvocationOffset(), collector);
            } catch (JavaModelException e) {
            	
            }
        }
        
        if (coreContext.isExtended()) {
            // didn't manage to get in here:
        	System.out.println("Enclosing element: " + coreContext.getEnclosingElement());
            final ASTNode completionNode = coreContext.getCompletionNode();
            final ASTNode completionNodeParent = coreContext.getCompletionNodeParent();
//            final ObjectVector visibleFields = coreContext.getVisibleFields();
//            System.out.println("This node: " + completionNode);
//            System.out.println("Parent node: " + completionNodeParent);
//            CompletionOnMemberAccess asdf = (CompletionOnMemberAccess) completionNode;
//            System.out.println(asdf.receiver.sourceStart);
//            System.out.println("asdf");
            
            System.out.println(context.getInvocationOffset());
            if (completionNodeParent == null && completionNode != null) {
            	System.out.println("We are a full statement");
            	System.out.println("Statement: " + completionNode);
            	if (completionNode instanceof MessageSend) {
            		MessageSend s = (MessageSend) completionNode;
            		try {
						System.out.println(context.getDocument().get(s.receiver.sourceStart, context.getInvocationOffset() - s.receiver.sourceStart));
					} catch (BadLocationException e) {
						e.printStackTrace();
					}
            		System.out.println("Resolved type: " + Arrays.toString(s.receiver.resolvedType.sourceName()));
            	} else if (completionNode instanceof NameReference) {
            		NameReference sc = (NameReference) completionNode;
            		try {
						System.out.println(context.getDocument().get(sc.sourceStart, context.getInvocationOffset() - sc.sourceStart));
						if (sc.binding instanceof VariableBinding) {
							VariableBinding vb = (VariableBinding) sc.binding;
							System.out.println(Arrays.toString(vb.type.readableName()));
						}
					} catch (BadLocationException e) {
						e.printStackTrace();
					}
            	} else if (completionNode instanceof FieldReference) {
            		FieldReference co = (FieldReference) completionNode;
            		try {
						System.out.println(context.getDocument().get(co.receiver.sourceStart, context.getInvocationOffset() - co.receiver.sourceStart));
						System.out.println(Arrays.toString(co.receiver.resolvedType.readableName()));
					} catch (BadLocationException e) {
						e.printStackTrace();
					}
            	}
            } else if (completionNode != null && completionNodeParent != null) {
            	// This is a nested statement
            	if (completionNodeParent instanceof Statement) {
            		// Our completion node is part of a statement (i.e. return asdf.|)
            		System.out.println("We are part of a statement");
            		Statement s = (Statement) completionNodeParent;
            		BinaryExpression be;
            		
            		System.out.println(s);
            	} else {
            		System.out.println("We are in a very nested statement");
            	}
            	
            }
        }
		return super.computeCompletionProposals(context, monitor);
	}

	@SuppressWarnings("restriction")
	@Override
	protected TemplateEngine computeCompletionEngine(
			JavaContentAssistInvocationContext context) {
		ICompilationUnit unit= context.getCompilationUnit();
		if (unit == null)
			return null;

		IJavaProject javaProject= unit.getJavaProject();
		if (javaProject == null)
			return null;

		CompletionContext coreContext= context.getCoreContext();
		if (coreContext != null) {
			int tokenLocation= coreContext.getTokenLocation();
			int tokenStart= coreContext.getTokenStart();
			int tokenKind= coreContext.getTokenKind();
			/*
			// XXX print out tokenlocation stuff
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
			if (context.getViewer().getSelectedRange().y > 0) {
				return null;
			}
			
			if ((tokenLocation == 0 && tokenStart > -1)
					|| ((tokenLocation & CompletionContext.TL_MEMBER_START) != 0 && tokenKind == CompletionContext.TOKEN_KIND_NAME && tokenStart > -1)
					|| (tokenLocation == 0 && isAfterDot(context.getDocument(), context.getInvocationOffset()))) {
				
				System.out.println("CompletionTemplateEngine returned"); // XXX Remove debug stuff
				
		        if (coreContext instanceof InternalCompletionContext && ((InternalCompletionContext)coreContext).isExtended()) {
		            ASTNode completionNode = ((InternalCompletionContext)coreContext).getCompletionNode();
		            ASTNode completionNodeParent = ((InternalCompletionContext)coreContext).getCompletionNodeParent();
		            fPostfixCompletionTemplateEngine.setASTNodes(completionNode, completionNodeParent);
		        } else if (coreContext instanceof InternalCompletionContext && ((InternalCompletionContext)coreContext).isExtended() == false) {
		            if (coreContext.isExtended() == false) {
		            	final ICompilationUnit cu = context.getCompilationUnit();
		                final CompletionProposalCollector collector = new CompletionProposalCollector(cu) {
		                    @Override
		                    public void acceptContext(final CompletionContext context) {
		                        super.acceptContext(context);
		                        ASTNode completionNode = ((InternalCompletionContext)context).getCompletionNode();
		    		            ASTNode completionNodeParent = ((InternalCompletionContext)context).getCompletionNodeParent();
		    		            fPostfixCompletionTemplateEngine.setASTNodes(completionNode, completionNodeParent);
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
		        					
				return fPostfixCompletionTemplateEngine;
			}
			
		}
		System.out.println("Not returned");
		return null;

	}
	
	private boolean isAfterDot(IDocument document, int offset) {
		try {
			return document.get(offset - 1, 1).charAt(0) == '.';
		} catch (BadLocationException e) {
			return false;
		}
	}
}

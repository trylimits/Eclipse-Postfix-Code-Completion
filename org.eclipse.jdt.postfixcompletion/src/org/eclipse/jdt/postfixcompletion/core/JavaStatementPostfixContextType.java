package org.eclipse.jdt.postfixcompletion.core;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.corext.template.java.AbstractJavaContextType;
import org.eclipse.jdt.internal.corext.template.java.CompilationUnitContext;
import org.eclipse.jdt.internal.corext.template.java.JavaContext;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.templates.GlobalTemplateVariables;

public class JavaStatementPostfixContextType extends AbstractJavaContextType {

	public static final String ID_ALL = "postfix"; // $NON-NLS-1$
	
	public JavaStatementPostfixContextType() {
		this.addResolver(new GlobalTemplateVariables.Cursor());
		this.addResolver(new OuterExpressionResolver());
		this.addResolver(new InnerExpressionResolver());
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.template.java.AbstractJavaContextType#initializeContext(org.eclipse.jdt.internal.corext.template.java.JavaContext)
	 */
	@SuppressWarnings("restriction")
	@Override
	protected void initializeContext(JavaContext context) {
		if (!getId().equals(JavaStatementPostfixContextType.ID_ALL)) { // a specific context must also allow the templates that work everywhere
			context.addCompatibleContextType(JavaStatementPostfixContextType.ID_ALL);
		}
	}
	
	/*
	 * @see org.eclipse.jdt.internal.corext.template.java.CompilationUnitContextType#createContext(org.eclipse.jface.text.IDocument, int, int, org.eclipse.jdt.core.ICompilationUnit)
	 */
	@Override
	public CompilationUnitContext createContext(IDocument document, int offset, int length, ICompilationUnit compilationUnit) {
		return createContext(document, offset, length, compilationUnit, null, null);
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.template.java.CompilationUnitContextType#createContext(org.eclipse.jface.text.IDocument, org.eclipse.jface.text.Position, org.eclipse.jdt.core.ICompilationUnit)
	 */
	@Override
	public CompilationUnitContext createContext(IDocument document, Position completionPosition, ICompilationUnit compilationUnit) {
		return createContext(document, completionPosition.getOffset(), completionPosition.getLength(), compilationUnit);
	}
	
	public JavaStatementPostfixContext createContext(IDocument document, int offset, int length, ICompilationUnit compilationUnit, ASTNode currentNode, ASTNode parentNode) {
		JavaStatementPostfixContext javaContext= new JavaStatementPostfixContext(this, document, offset, length, compilationUnit, currentNode, parentNode);
		initializeContext(javaContext);
		return javaContext;
	}
	
}
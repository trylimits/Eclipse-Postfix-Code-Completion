package org.eclipse.jdt.postfixcompletion.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WildcardType;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.FieldReference;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.compiler.lookup.ArrayBinding;
import org.eclipse.jdt.internal.compiler.lookup.BaseTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.ParameterizedTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.VariableBinding;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.template.java.JavaContext;
import org.eclipse.jdt.internal.ui.text.correction.ASTResolving;
import org.eclipse.jdt.postfixcompletion.resolver.InnerExpressionResolver;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateBuffer;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.jface.text.templates.TemplateException;
import org.eclipse.jface.text.templates.TemplateVariable;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

/**
 * This class is an extension to the existing {@link JavaContext} and includes/provides additional information
 * on the current node which the code completion was invoked on.
 * <br/>
 * <br/>
 * TODO Atm this class is dependent on non-published changes of the class {@link JavaContext}.
 */
@SuppressWarnings("restriction")
public class JavaStatementPostfixContext extends JavaContext {

	private static final Object CONTEXT_TYPE_ID = "postfix"; //$NON-NLS-1$
	private static final String OBJECT_SIGNATURE = "java.lang.Object"; //$NON-NLS-1$
	private static final String ID_SEPARATOR = "§§"; //$NON-NLS-1$
	
	protected ASTNode currentCompletionNode;
	protected ASTNode currentCompletionNodeParent;
	
	protected Map<ASTNode, Region> nodeRegions;
	
	protected ASTNode selectedNode;
	private boolean domInitialized;
	private BodyDeclaration bodyDeclaration;
	private org.eclipse.jdt.core.dom.ASTNode parentDeclaration;
	
	private Map<TemplateVariable, int[]> outOfRangeOffsets;

	public JavaStatementPostfixContext(TemplateContextType type,
			IDocument document, final int completionOffset, int completionLength,
			ICompilationUnit compilationUnit) {
		
		this(type, document, completionOffset, completionLength, compilationUnit, null, null);
	}

	public JavaStatementPostfixContext(TemplateContextType type,
			IDocument document, Position completionPosition,
			ICompilationUnit compilationUnit) {
		this(type, document, completionPosition.getOffset(), completionPosition.getLength(), compilationUnit, null, null);
	}
	
	public JavaStatementPostfixContext(
			TemplateContextType type,
			IDocument document, int offset, int length,
			ICompilationUnit compilationUnit,
			ASTNode currentNode,
			ASTNode parentNode) {
		super(type, document, offset, length, compilationUnit);
		
		this.nodeRegions = new HashMap<>();
		
		this.currentCompletionNode = currentNode;
		nodeRegions.put(currentNode, calculateNodeRegion(currentNode));

		this.currentCompletionNodeParent = parentNode;
		nodeRegions.put(parentNode, calculateNodeRegion(parentNode));
		
		this.selectedNode = currentNode;
		
		outOfRangeOffsets = new HashMap<>();
	}
	
	public String addImportGenericClass(String className) {
		Pattern p = Pattern.compile("[a-zA-Z0-9$_\\.]+");
		Matcher m = p.matcher(className);
		List<String> classNames = new ArrayList<String>();
		Map<String, String> classNameMapping = new HashMap<String, String>();
		while (m.find()) {
			classNames.add(className.substring(m.start(), m.end()));
		}
		/*
		 * In case the import class looks like this:
		 * a.b.c.Foo<b.c.Foo>
		 * we have to consider that - if we do not care about ordering, the following could happen:
		 * 1. trying to import b.c.Foo - import is resolved to Foo
		 * 2. replacing b.c.Foo with Foo - a.Foo<Foo> --> not correct (should be a.b.c.Foo<Foo>)
		 * 3. ...
		 * 
		 * The solution to this is as follows:
		 * 1. sorting the fully qualified class names by length
		 * 2. replacing all occurring class names with unique identifiers ($$id$$)
		 * 3. importing all class names and map the fully qualified identifier with the resolved identifier of the class
		 * 4. replace the unique identifiers with the mapped values
		 */
		Collections.sort(classNames, new Comparator<String>() {
			@Override
			public int compare(String arg0, String arg1) {
				return arg1.length() - arg0.length();
			}
		});
		for (int i = 0; i < classNames.size(); i++) {
			className = className.replace(classNames.get(i), ID_SEPARATOR + i + ID_SEPARATOR);
			classNameMapping.put(classNames.get(i), addImport(classNames.get(i)));
		}
		for (int i = 0; i < classNames.size(); i++) {
			className = className.replace(ID_SEPARATOR + i + ID_SEPARATOR, classNameMapping.get(classNames.get(i)));
		}
		return className;
	}
	
	private Region calculateNodeRegion(ASTNode node) {
		if (node == null) {
			return new Region(0, 0);
		}
		int start = getNodeBegin(node);
		int end = getCompletionOffset() - getPrefixKey().length() - start - 1;
		return new Region(start, end);
	}

	/*
	 * @see TemplateContext#canEvaluate(Template templates)
	 */
	@Override
	public boolean canEvaluate(Template template) {
		
		if (!template.getContextTypeId().equals(
				JavaStatementPostfixContext.CONTEXT_TYPE_ID))
			return false;

		if (fForceEvaluation)
			return true;
		
		if (selectedNode == null) // We can evaluate to true only if we have a valid inner expression
			return false;
		
		if (template.getName().toLowerCase().startsWith(getPrefixKey().toLowerCase()) == false) {
			return false;
		}
		
		// We check if the template makes "sense" by checking the requirements/conditions for the template
		// For this purpose we have to resolve the inner_expression variable of the template
		// This approach is much faster then delegating this to the existing TemplateTranslator class
		
		String regex = ("\\$\\{([a-zA-Z]+):" + InnerExpressionResolver.INNER_EXPRESSION_VAR + "\\(([^\\$|\\{|\\}]*)\\)\\}"); // TODO Review this regex
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(template.getPattern());
		boolean result = true;
		
		while (matcher.find()) {
			String[] types = matcher.group(2).split(",");
			for (String s : types) {
				if (!arrayContains(InnerExpressionResolver.FLAGS, s)) {
					result = false;
					if (this.isNodeResolvingTo(selectedNode, s.trim()) == true) {
						return true;
					}
				}
			}
	    }
		
		return result;
	}
	
	private boolean arrayContains(Object[] array, Object o) {
		for (Object a : array) {
			if (a.equals(o)) return true;
		}
		return false;
	}

	/**
	 * Returns the current prefix of the key which was typed in.
	 * <br/>
	 * Examples:
	 * <code>
	 * <br/>
	 * new Object().		=> getPrefixKey() returns ""<br/>
	 * new Object().a		=> getPrefixKey() returns "a"<br/>
	 * new object().asdf	=> getPrefixKey() returns "asdf"<br/>
	 * 
	 * @return an empty string or a string which represents the prefix of the key which was typed in
	 */
	protected String getPrefixKey() {
		IDocument document = getDocument();
		int start = getCompletionOffset();
		int end = getCompletionOffset();
		try {
			String temp = document.get(start, 1);
			while (!".".equals(temp)) {
				temp = document.get(--start, 1);
			}
			return document.get(start + 1, end - start - 1);
		} catch (BadLocationException e) {
		}
		return "";
	}
	
	@Override
	public int getEnd() {
		return getCompletionOffset();
	}

	@Deprecated
	public String getOuterExpression() {
		return ""; // XXX This method is not used anymore
	}
	
	/**
	 * Calculates the beginning position of a given {@link ASTNode}
	 * @param node
	 * @return
	 */
	protected int getNodeBegin(ASTNode node) {
		if (node instanceof NameReference) {
			return ((NameReference) node).sourceStart;
		} else if (node instanceof FieldReference) {
			return ((FieldReference) node).receiver.sourceStart;
		} else if (node instanceof MessageSend) {
			return ((MessageSend) node).receiver.sourceStart;
		}
		return node.sourceStart;
	}
	
	/**
	 * Returns the {@link Region} which represents the source region of the affected statement.
	 * @return
	 */
	public Region getAffectedSourceRegion() {
		return new Region(getCompletionOffset() - getPrefixKey().length() - nodeRegions.get(selectedNode).getLength() - 1, nodeRegions.get(selectedNode).getLength());
	}
	
	public String getAffectedStatement() {
		Region r = getAffectedSourceRegion();
		try {
			return getDocument().get(r.getOffset(), r.getLength());
		} catch (BadLocationException e) {
		}
		return "";
	}
	
	/**
	 * Returns <code>true</code> if the type or one of its supertypes of a given {@link ASTNode} resolves to a given type signature.
	 * <br/>
	 * Examples:
	 * <br/>
	 * <code>
	 * <br/>
	 * isNodeResolvingTo(node of type java.lang.String, "java.lang.Object") returns true<br/>
	 * isNodeResolvingTo(node of type java.lang.String, "java.lang.Iterable") returns false<br/>
	 * </code>
	 * 
	 * TODO Implement this method without using the recursive helper method if there are any performance/stackoverflow issues
	 * 
	 * @param node an ASTNode
	 * @param signature a fully qualified type
	 * @return true if the type of the given ASTNode itself or one of its superclass/superinterfaces resolves to the given signature. false otherwise.
	 */
	protected boolean isNodeResolvingTo(ASTNode node, String signature) {
		if (signature == null || signature.trim().length() == 0) {
			return true;
		}
		Binding b = resolveNodeToBinding(node);
		if (b instanceof ParameterizedTypeBinding) {
			ParameterizedTypeBinding ptb = (ParameterizedTypeBinding) b;
			return resolvesReferenceBindingTo(ptb.actualType(), signature);
		} else if (b instanceof BaseTypeBinding) {
			return (new String(b.readableName()).equals(signature));
		} else if (b instanceof TypeBinding) {
			return resolvesReferenceBindingTo((TypeBinding) b, signature);
		}
		
		return true;
	}
	
	/**
	 * This is a recursive method which performs a depth first search in the inheritance graph of the given {@link TypeBinding}.
	 * 
	 * @param sb a TypeBinding
	 * @param signature a fully qualified type
	 * @return true if the given TypeBinding itself or one of its superclass/superinterfaces resolves to the given signature. false otherwise.
	 */
	private boolean resolvesReferenceBindingTo(TypeBinding sb, String signature) {
		if (sb == null) {
			return false;
		}
		if (new String(sb.readableName()).startsWith(signature) || (sb instanceof ArrayBinding && "array".equals(signature))) {
			return true;
		}
		List<ReferenceBinding> bindings = new ArrayList<>();
		Collections.addAll(bindings, sb.superInterfaces());
		bindings.add(sb.superclass());
		boolean result = false;
		Iterator<ReferenceBinding> it = bindings.iterator();
		while (it.hasNext() && result == false) {
			result = resolvesReferenceBindingTo(it.next(), signature);
		}
		return result;
	}
	
	protected boolean resolvesReferenceBindingToArray(TypeBinding sb) {
		return sb instanceof ArrayBinding;
	}
	
	protected boolean isNodeOfBaseType(ASTNode node) {
		return !isNodeResolvingTo(node, OBJECT_SIGNATURE);
	}
	
	protected boolean isNodeBooleanExpression(ASTNode node) {
		return isNodeResolvingTo(node, "boolean");
	}
	
	protected Binding resolveNodeToBinding(ASTNode node) {
		if (node instanceof NameReference) {
    		NameReference nr = (NameReference) node;
			if (nr.binding instanceof VariableBinding) {
				VariableBinding vb = (VariableBinding) nr.binding;
				return vb.type;
			}
		} else if (node instanceof FieldReference) {
    		FieldReference fr = (FieldReference) node;
			return fr.receiver.resolvedType;
		}
		return null;
	}

	protected String resolveNodeToTypeString(ASTNode node) {
		Binding b = resolveNodeToBinding(node);
		if (b != null) {
			return new String(b.readableName());
		}
		return OBJECT_SIGNATURE;
	}
	
	/**
	 * Returns the fully qualified name the node of the current code completion invocation resolves to.
	 * 
	 * @return a fully qualified type signature or the name of the base type.
	 */
	public String getInnerExpressionTypeSignature() {
		return resolveNodeToTypeString(selectedNode);
	}
	
	/**
	 * Adds a new field to the {@link AST} using the given type and variable name. The method
	 * returns a {@link TextEdit} which can then be applied using the {@link #applyTextEdit(TextEdit)} method.
	 * 
	 * @param type
	 * @param varName
	 * @return a {@link TextEdit} which represents the changes which would be made, or <code>null</code> if the field
	 * can not be created.
	 */
	public TextEdit addField(String type, String varName, boolean publicField, boolean staticField, boolean finalField, String value) {
		
		if (isReadOnly())
			return null;

		if (!domInitialized)
			initDomAST();
		
		boolean isStatic = isBodyStatic();
		int modifiers = (!publicField) ? Modifier.PRIVATE : Modifier.PUBLIC;
		if (isStatic || staticField) {
			modifiers |= Modifier.STATIC;
		}
		if (finalField) {
			modifiers |= Modifier.FINAL;
		}
		
		ASTRewrite rewrite= ASTRewrite.create(parentDeclaration.getAST());
		
		VariableDeclarationFragment newDeclFrag = addFieldDeclaration(rewrite, parentDeclaration, modifiers, varName, type, value);

		TextEdit te = rewrite.rewriteAST(getDocument(), null);
		return te;
	}
	
	private boolean isBodyStatic() {
		boolean isAnonymous = parentDeclaration.getNodeType() == org.eclipse.jdt.core.dom.ASTNode.ANONYMOUS_CLASS_DECLARATION;
		boolean isStatic = Modifier.isStatic(bodyDeclaration.getModifiers()) && !isAnonymous;
		return isStatic;
	}
	
	private void initDomAST() {
		if (isReadOnly())
			return;
		
		ASTParser parser= ASTParser.newParser(AST.JLS8);
		parser.setSource(getCompilationUnit());
		parser.setResolveBindings(true);
		org.eclipse.jdt.core.dom.ASTNode domAst = parser.createAST(new NullProgressMonitor());
		
//		org.eclipse.jdt.core.dom.AST ast = domAst.getAST();
		
		NodeFinder nf = new NodeFinder(domAst, getCompletionOffset(), 1);
		org.eclipse.jdt.core.dom.ASTNode cv = nf.getCoveringNode();
		
		bodyDeclaration = ASTResolving.findParentBodyDeclaration(cv);
		parentDeclaration = ASTResolving.findParentType(cv);
		domInitialized = true;
	}

	/**
	 * Applies a {@link TextEdit} to the {@link IDocument} of this context and updates
	 * the completion offset variable.
	 * 
	 * @param te {@link TextEdit} to apply
	 * @return <code>true</code> if the method was successful, <code>false</code> otherwise
	 */
	public boolean applyTextEdit(TextEdit te) {
		try {
			te.apply(getDocument());
			setCompletionOffset(getCompletionOffset() + ((te.getOffset() < getCompletionOffset()) ? te.getLength() : 0));
			return true;
		} catch (MalformedTreeException | BadLocationException e) {
		
		}
		return false;
	}
		
	private VariableDeclarationFragment addFieldDeclaration(ASTRewrite rewrite, org.eclipse.jdt.core.dom.ASTNode newTypeDecl, int modifiers, String varName, String qualifiedName, String value) {

		ChildListPropertyDescriptor property = ASTNodes.getBodyDeclarationsProperty(newTypeDecl);
		List<BodyDeclaration> decls = ASTNodes.getBodyDeclarations(newTypeDecl);
		AST ast = newTypeDecl.getAST();
		
		VariableDeclarationFragment newDeclFrag = ast.newVariableDeclarationFragment();
		newDeclFrag.setName(ast.newSimpleName(varName));
		
		Type type = createType(Signature.createTypeSignature(qualifiedName, true), ast);
		
		if (value != null && value.trim().length() > 0) {
			Expression e = createExpression(value);
			Expression ne = (Expression) org.eclipse.jdt.core.dom.ASTNode.copySubtree(ast, e);
			newDeclFrag.setInitializer(ne);
			
		} else {
			if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
				newDeclFrag.setInitializer(ASTNodeFactory.newDefaultExpression(ast, type, 0));
			}
		}

		FieldDeclaration newDecl = ast.newFieldDeclaration(newDeclFrag);
		newDecl.setType(type);
		newDecl.modifiers().addAll(ASTNodeFactory.newModifiers(ast, modifiers));

		int insertIndex = findFieldInsertIndex(decls, getCompletionOffset(), modifiers);
		rewrite.getListRewrite(newTypeDecl, property).insertAt(newDecl, insertIndex, null);
		
		return newDeclFrag;
	}
	
	private Expression createExpression(String expr) {
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setKind(ASTParser.K_EXPRESSION);
		parser.setResolveBindings(true);
		parser.setSource(expr.toCharArray());
		
		org.eclipse.jdt.core.dom.ASTNode astNode = parser.createAST(new NullProgressMonitor());
		return (Expression) astNode;
	}
	
	private Type createType(String typeSig, AST ast) {
		int sigKind = Signature.getTypeSignatureKind(typeSig);
        switch (sigKind) {
            case Signature.BASE_TYPE_SIGNATURE:
                return ast.newPrimitiveType(PrimitiveType.toCode(Signature.toString(typeSig)));
            case Signature.ARRAY_TYPE_SIGNATURE:
                Type elementType = createType(Signature.getElementType(typeSig), ast);
                return ast.newArrayType(elementType, Signature.getArrayCount(typeSig));
            case Signature.CLASS_TYPE_SIGNATURE:
                String erasureSig = Signature.getTypeErasure(typeSig);

                String erasureName = Signature.toString(erasureSig);
                if (erasureSig.charAt(0) == Signature.C_RESOLVED) {
                    erasureName = addImport(erasureName);
                }
                
                Type baseType= ast.newSimpleType(ast.newName(erasureName));
                String[] typeArguments = Signature.getTypeArguments(typeSig);
                if (typeArguments.length > 0) {
                    ParameterizedType type = ast.newParameterizedType(baseType);
                    List argNodes = type.typeArguments();
                    for (int i = 0; i < typeArguments.length; i++) {
                        String curr = typeArguments[i];
                        if (containsNestedCapture(curr)) {
                            argNodes.add(ast.newWildcardType());
                        } else {
                            argNodes.add(createType(curr, ast));
                        }
                    }
                    return type;
                }
                return baseType;
            case Signature.TYPE_VARIABLE_SIGNATURE:
                return ast.newSimpleType(ast.newSimpleName(Signature.toString(typeSig)));
            case Signature.WILDCARD_TYPE_SIGNATURE:
                WildcardType wildcardType= ast.newWildcardType();
                char ch = typeSig.charAt(0);
                if (ch != Signature.C_STAR) {
                    Type bound= createType(typeSig.substring(1), ast);
                    wildcardType.setBound(bound, ch == Signature.C_EXTENDS);
                }
                return wildcardType;
            case Signature.CAPTURE_TYPE_SIGNATURE:
                return createType(typeSig.substring(1), ast);
        }
        
        return ast.newSimpleType(ast.newName("java.lang.Object"));
	}
	
	private boolean containsNestedCapture(String signature) {
        return signature.length() > 1 && signature.indexOf(Signature.C_CAPTURE, 1) != -1;
    }
	
	private int findFieldInsertIndex(List<BodyDeclaration> decls, int currPos, int modifiers) {
		for (int i = decls.size() - 1; i >= 0; i--) {
			org.eclipse.jdt.core.dom.ASTNode curr = decls.get(i);
			if (curr instanceof FieldDeclaration && currPos > curr.getStartPosition() + curr.getLength()
					 && ((FieldDeclaration) curr).getModifiers() == modifiers) {
				return i + 1;
			}
		}
		return 0;
	}
	
	public String[] suggestFieldName(String type, String[] excludes, boolean staticField, boolean finalField) throws IllegalArgumentException {
		int dim = 0;
		while (type.endsWith("[]")) {
			dim++;
			type = type.substring(0, type.length() - 2);
		}

		IJavaProject project = getJavaProject();
		
		int namingConventions = 0;
		if (staticField && finalField) {
			namingConventions = NamingConventions.VK_STATIC_FINAL_FIELD;
		} else if (staticField && !finalField) {
			namingConventions = NamingConventions.VK_STATIC_FIELD;
		} else {
			namingConventions = NamingConventions.VK_INSTANCE_FIELD;
		}
		
		if (project != null)
			return StubUtility.getVariableNameSuggestions(namingConventions, project, type, dim, Arrays.asList(excludes), true);

		return new String[] {Signature.getSimpleName(type).toLowerCase()};
	}
	
	public String[] suggestFieldName(String type, boolean finalField, boolean forceStatic) {
		if (!domInitialized) {
			initDomAST();
		}
		if (domInitialized) {
			return suggestFieldName(type, ASTResolving.getUsedVariableNames(bodyDeclaration), (forceStatic) ? forceStatic : isBodyStatic(), finalField);
		}
		// If the dom is not initialized yet (template preview) we return a dummy name
		return new String[] { "newField" };
	}
	
	public void registerOutOfRangeOffset(TemplateVariable var, int absoluteOffset) {
		if (outOfRangeOffsets.get(var) == null) {
			outOfRangeOffsets.put(var, new int[] { absoluteOffset });
		} else {
			int[] temp = outOfRangeOffsets.get(var);
			int[] newArr = new int[temp.length + 1];
			System.arraycopy(temp, 0, newArr, 0, temp.length);
			newArr[temp.length] = absoluteOffset;
			outOfRangeOffsets.put(var, newArr);
		}
	}

	public int[] getVariableOutOfRangeOffsets(TemplateVariable variable) {
		return outOfRangeOffsets.get(variable);
	}
	
	@Override
	public TemplateBuffer evaluate(Template template)
			throws BadLocationException, TemplateException {
		
		TemplateBuffer result = super.evaluate(template);
		
		// After the template buffer has been created we are able to add out of range offsets
		// This is not possible beforehand as it will result in an exception!
		for (TemplateVariable tv : result.getVariables()) {
	            
            int[] outOfRangeOffsets = this.getVariableOutOfRangeOffsets(tv);
            
            if (outOfRangeOffsets != null && outOfRangeOffsets.length > 0) {
            	int[] offsets = tv.getOffsets();
            	int[] newOffsets = new int[outOfRangeOffsets.length + offsets.length];
            	
            	System.arraycopy(offsets, 0, newOffsets, 0, offsets.length);

            	for (int i = 0; i < outOfRangeOffsets.length; i++) {
            		newOffsets[i + offsets.length] = outOfRangeOffsets[i]; // - getAffectedSourceRegion().getOffset();
            	}
            	
            	tv.setOffsets(newOffsets);
            }
		}
		
		return result;
	}
	
}

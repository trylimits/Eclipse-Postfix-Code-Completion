/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences.formatter;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.internal.ui.preferences.formatter.SnippetPreview.PreviewSnippet;


/**
 * Manage code formatter white space options on a higher level.
 */
public final class WhiteSpaceOptions {

    /**
     * Represents a node in the options tree.
     */
	public abstract static class Node {

	    private final InnerNode fParent;
	    private final String fName;

	    public int index;

	    protected final Map<String, String> fWorkingValues;
	    protected final ArrayList<Node> fChildren;

	    public Node(InnerNode parent, Map<String, String> workingValues, String message) {
	        if (workingValues == null || message == null)
	            throw new IllegalArgumentException();
	        fParent= parent;
	        fWorkingValues= workingValues;
	        fName= message;
	        fChildren= new ArrayList<Node>();
	        if (fParent != null)
	            fParent.add(this);
	    }

	    public abstract void setChecked(boolean checked);

	    public boolean hasChildren() {
	        return !fChildren.isEmpty();
	    }

	    public List<Node> getChildren() {
	        return Collections.unmodifiableList(fChildren);
	    }

	    public InnerNode getParent() {
	        return fParent;
	    }

	    @Override
		public final String toString() {
	        return fName;
	    }

	    public abstract List<PreviewSnippet> getSnippets();

	    public abstract void getCheckedLeafs(List<OptionNode> list);
	}

	/**
	 * A node representing a group of options in the tree.
	 */
	public static class InnerNode extends Node {

        public InnerNode(InnerNode parent, Map<String, String> workingValues, String messageKey) {
            super(parent, workingValues, messageKey);
        }

	    @Override
		public void setChecked(boolean checked) {
	        for (final Iterator<Node> iter = fChildren.iterator(); iter.hasNext();)
	            iter.next().setChecked(checked);
	    }

	    public void add(Node child) {
	        fChildren.add(child);
	    }

        @Override
		public List<PreviewSnippet> getSnippets() {
            final ArrayList<PreviewSnippet> snippets= new ArrayList<PreviewSnippet>(fChildren.size());
            for (Iterator<Node> iter= fChildren.iterator(); iter.hasNext();) {
                final List<PreviewSnippet> childSnippets= iter.next().getSnippets();
                for (final Iterator<PreviewSnippet> chIter= childSnippets.iterator(); chIter.hasNext(); ) {
                    final PreviewSnippet snippet= chIter.next();
                    if (!snippets.contains(snippet))
                        snippets.add(snippet);
                }
            }
            return snippets;
        }

        @Override
		public void getCheckedLeafs(List<OptionNode> list) {
            for (Iterator<Node> iter= fChildren.iterator(); iter.hasNext();) {
                iter.next().getCheckedLeafs(list);
            }
        }
	}


	/**
	 * A node representing a concrete white space option in the tree.
	 */
	public static class OptionNode extends Node {
	    private final String fKey;
	    private final ArrayList<PreviewSnippet> fSnippets;

	    public OptionNode(InnerNode parent, Map<String, String> workingValues, String messageKey, String key, PreviewSnippet snippet) {
	        super(parent, workingValues, messageKey);
	        fKey= key;
	        fSnippets= new ArrayList<PreviewSnippet>(1);
	        fSnippets.add(snippet);
	    }

        @Override
		public void setChecked(boolean checked) {
        	fWorkingValues.put(fKey, checked ? JavaCore.INSERT : JavaCore.DO_NOT_INSERT);
        }

        public boolean getChecked() {
            return JavaCore.INSERT.equals(fWorkingValues.get(fKey));
        }

        @Override
		public List<PreviewSnippet> getSnippets() {
            return fSnippets;
        }

        @Override
		public void getCheckedLeafs(List<OptionNode> list) {
            if (getChecked())
                list.add(this);
        }
	}



	/**
	 * Preview snippets.
	 */

    private final PreviewSnippet FOR_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "for (int i= 0, j= array.length; i < array.length; i++, j--) {}\nfor (String s : names) {}" //$NON-NLS-1$
    );

    private final PreviewSnippet WHILE_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "while (condition) {}; do {} while (condition);" //$NON-NLS-1$
    );

    private final PreviewSnippet CATCH_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "try { number= Integer.parseInt(value); } catch (NumberFormatException e) {}"); //$NON-NLS-1$

	private final PreviewSnippet TRY_PREVIEW= new PreviewSnippet(
	CodeFormatter.K_STATEMENTS, 
	"try (FileReader reader1 = new FileReader(\"file1\"); FileReader reader2 = new FileReader(\"file2\")) {}"); //$NON-NLS-1$

    private final PreviewSnippet IF_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "if (condition) { return foo; } else {return bar;}"); //$NON-NLS-1$

    private final PreviewSnippet SYNCHRONIZED_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "synchronized (list) { list.add(element); }"); //$NON-NLS-1$

    private final PreviewSnippet SWITCH_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "switch (number) { case RED: return GREEN; case GREEN: return BLUE; case BLUE: return RED; default: return BLACK;}"); //$NON-NLS-1$

    private final PreviewSnippet CONSTRUCTOR_DECL_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_CLASS_BODY_DECLARATIONS,
    "MyClass() throws E0, E1 { this(0,0,0);}" +  //$NON-NLS-1$
    "MyClass(int x, int y, int z) throws E0, E1 { super(x, y, z, true);}"); //$NON-NLS-1$

    private final PreviewSnippet METHOD_DECL_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_CLASS_BODY_DECLARATIONS,
    "void foo() throws E0, E1 {};" +  //$NON-NLS-1$
    "void bar(int x, int y) throws E0, E1 {}"); //$NON-NLS-1$

    private final PreviewSnippet ARRAY_DECL_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "int [] array0= new int [] {};\n" +  //$NON-NLS-1$
    "int [] array1= new int [] {1, 2, 3};\n" +  //$NON-NLS-1$
    "int [] array2= new int[3];"); //$NON-NLS-1$

    private final PreviewSnippet ARRAY_REF_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "array[i].foo();"); //$NON-NLS-1$

    private final PreviewSnippet METHOD_CALL_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "foo();\n" +  //$NON-NLS-1$
    "bar(x, y);"); //$NON-NLS-1$

//    private final PreviewSnippet CONSTR_CALL_PREVIEW= new PreviewSnippet(
//    CodeFormatter.K_STATEMENTS,
//    "this();\n\n" +  //$NON-NLS-1$
//    "this(x, y);\n"); //$NON-NLS-1$

    private final PreviewSnippet ALLOC_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "String str= new String(); Point point= new Point(x, y);"); //$NON-NLS-1$

    private final PreviewSnippet LABEL_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "label: for (int i= 0; i<list.length; i++) {for (int j= 0; j < list[i].length; j++) continue label;}"); //$NON-NLS-1$

    private final PreviewSnippet SEMICOLON_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "int a= 4; foo(); bar(x, y);"); //$NON-NLS-1$

    private final PreviewSnippet CONDITIONAL_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "String value= condition ? TRUE : FALSE;"); //$NON-NLS-1$

    private final PreviewSnippet CLASS_DECL_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_COMPILATION_UNIT,
    "class MyClass implements I0, I1, I2 {}"); //$NON-NLS-1$

    private final PreviewSnippet ANON_CLASS_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "AnonClass= new AnonClass() {void foo(Some s) { }};"); //$NON-NLS-1$

    private final PreviewSnippet OPERATOR_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "List list= new ArrayList(); int a= -4 + -9; b= a++ / --number; c += 4; boolean value= true && false;"); //$NON-NLS-1$

    private final PreviewSnippet CAST_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "String s= ((String)object);"); //$NON-NLS-1$

    private final PreviewSnippet MULT_LOCAL_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "int a= 0, b= 1, c= 2, d= 3;"); //$NON-NLS-1$

    private final PreviewSnippet MULT_FIELD_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_CLASS_BODY_DECLARATIONS,
    "int a=0,b=1,c=2,d=3;"); //$NON-NLS-1$

    private final PreviewSnippet BLOCK_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "if (true) { return 1; } else { return 2; }"); //$NON-NLS-1$

    private final PreviewSnippet PAREN_EXPR_PREVIEW= new PreviewSnippet(
    CodeFormatter.K_STATEMENTS,
    "result= (a *( b +  c + d) * (e + f));"); //$NON-NLS-1$

    private final PreviewSnippet ASSERT_PREVIEW= new PreviewSnippet(
    	CodeFormatter.K_STATEMENTS,
    	"assert condition : reportError();" //$NON-NLS-1$
	);

    private final PreviewSnippet RETURN_PREVIEW= new PreviewSnippet(
    	CodeFormatter.K_STATEMENTS,
    	"return (o);" //$NON-NLS-1$
    );

    private final PreviewSnippet THROW_PREVIEW= new PreviewSnippet(
    	CodeFormatter.K_STATEMENTS,
    	"throw (e);" //$NON-NLS-1$
    );

    private final PreviewSnippet ANNOTATION_DECL_PREVIEW= new PreviewSnippet(
        CodeFormatter.K_CLASS_BODY_DECLARATIONS,
        "@interface MyAnnotation { String value(); }\n@interface OtherAnnotation { }\n" //$NON-NLS-1$
    );

    private final PreviewSnippet ANNOTATION_MODIFIER_PREVIEW= new PreviewSnippet(
        CodeFormatter.K_CLASS_BODY_DECLARATIONS,
        "@Annot(x=23, y=-3)\npublic class A { }\n" //$NON-NLS-1$
    );

    private final PreviewSnippet ENUM_PREVIEW= new PreviewSnippet(
        CodeFormatter.K_CLASS_BODY_DECLARATIONS,
        "enum MyEnum { GREEN(0, 1), RED() {\nvoid process() {}\n}\n}" //$NON-NLS-1$
    );

    private final PreviewSnippet PARAMETERIZED_TYPE_REFERENCE_PREVIEW= new PreviewSnippet(
            CodeFormatter.K_CLASS_BODY_DECLARATIONS,
            "Map<String, Element> map=\n new HashMap<String, Element>();" //$NON-NLS-1$
        );

    private final PreviewSnippet TYPE_ARGUMENTS_PREVIEW= new PreviewSnippet(
            CodeFormatter.K_STATEMENTS,
            "x.<String, Element>foo();" //$NON-NLS-1$
        );

	private final PreviewSnippet TYPE_PARAMETER_PREVIEW= new PreviewSnippet(
            CodeFormatter.K_CLASS_BODY_DECLARATIONS,
            "class MyGenericType<S, T extends Element & List> { }" //$NON-NLS-1$
        );

    private final PreviewSnippet VARARG_PARAMETER_PREVIEW= new PreviewSnippet(
    		CodeFormatter.K_CLASS_BODY_DECLARATIONS,
			"void format(String s, Object ... args) {}" //$NON-NLS-1$
		);

    private final PreviewSnippet WILDCARD_PREVIEW= new PreviewSnippet(
            CodeFormatter.K_CLASS_BODY_DECLARATIONS,
            "Map<X<?>, Y<? extends K, ? super V>> t;" //$NON-NLS-1$
        );
    
    private final PreviewSnippet LAMBDA_EXPR_PREVIEW= new PreviewSnippet(
    	    CodeFormatter.K_STATEMENTS,
    	    "Runnable r = ()->process();"); //$NON-NLS-1$

	/**
	 * Create the tree, in this order: syntax element - position - abstract element
	 * 
	 * @param workingValues the current values
	 * @return returns roots (type <code>Node</code>)
	 */
	public List<InnerNode> createTreeBySyntaxElem(Map<String, String> workingValues) {
        final ArrayList<InnerNode> roots= new ArrayList<InnerNode>();

        InnerNode element;

        element= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceOptions_opening_paren);
        createBeforeOpenParenTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_before));
        createAfterOpenParenTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_after));
        roots.add(element);

        element= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceOptions_closing_paren);
        createBeforeClosingParenTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_before));
        createAfterCloseParenTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_after));
        roots.add(element);

        element= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceOptions_opening_brace);
        createBeforeOpenBraceTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_before));
        createAfterOpenBraceTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_after));
        roots.add(element);

        element= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceOptions_closing_brace);
        createBeforeClosingBraceTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_before));
        createAfterCloseBraceTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_after));
        roots.add(element);

        element= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceOptions_opening_bracket);
        createBeforeOpenBracketTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_before));
        createAfterOpenBracketTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_after));
        roots.add(element);

        element= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceOptions_closing_bracket);
        createBeforeClosingBracketTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_before));
        roots.add(element);

        element= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceOptions_operator);
        createBeforeOperatorTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_before));
        createAfterOperatorTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_after));
        roots.add(element);

        element= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceOptions_comma);
        createBeforeCommaTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_before));
        createAfterCommaTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_after));
        roots.add(element);

        element= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceOptions_colon);
        createBeforeColonTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_before));
        createAfterColonTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_after));
        roots.add(element);

        element= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceOptions_semicolon);
        createBeforeSemicolonTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_before));
        createAfterSemicolonTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_after));
        roots.add(element);

        element= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceOptions_question_mark);
        createBeforeQuestionTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_before));
        createAfterQuestionTree(workingValues, createChild(element, workingValues, FormatterMessages.WhiteSpaceOptions_after));
        roots.add(element);

        element= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceOptions_between_empty_parens);
        createBetweenEmptyParenTree(workingValues, element);
        roots.add(element);

        element= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceOptions_between_empty_braces);
        createBetweenEmptyBracesTree(workingValues, element);
        roots.add(element);

        element= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceOptions_between_empty_brackets);
        createBetweenEmptyBracketsTree(workingValues, element);
        roots.add(element);

        return roots;
	}

    /**
	 * Create the tree, in this order: position - syntax element - abstract element
	 * 
	 * @param workingValues the current values
	 * @return returns roots (type <code>Node</code>)
	 */
    public List<Node> createAltTree(Map<String, String> workingValues) {

        final ArrayList<Node> roots= new ArrayList<Node>();

        InnerNode parent;

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_opening_paren);
        createBeforeOpenParenTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_opening_paren);
        createAfterOpenParenTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_closing_paren);
        createBeforeClosingParenTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_closing_paren);
        createAfterCloseParenTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_between_empty_parens);
        createBetweenEmptyParenTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_opening_brace);
        createBeforeOpenBraceTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_opening_brace);
        createAfterOpenBraceTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_closing_brace);
        createBeforeClosingBraceTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_closing_brace);
        createAfterCloseBraceTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_between_empty_braces);
        createBetweenEmptyBracesTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_opening_bracket);
        createBeforeOpenBracketTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_opening_bracket);
        createAfterOpenBracketTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_closing_bracket);
        createBeforeClosingBracketTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_between_empty_brackets);
        createBetweenEmptyBracketsTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_opening_angle_bracket);
        createBeforeOpenAngleBracketTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_opening_angle_bracket);
        createAfterOpenAngleBracketTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_closing_angle_bracket);
        createBeforeClosingAngleBracketTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_closing_angle_bracket);
        createAfterClosingAngleBracketTree(workingValues, parent);


        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_operator);
        createBeforeOperatorTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_operator);
        createAfterOperatorTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_comma);
        createBeforeCommaTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_comma);
        createAfterCommaTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_colon);
        createAfterColonTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_colon);
        createBeforeColonTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_semicolon);
        createBeforeSemicolonTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_semicolon);
        createAfterSemicolonTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_question_mark);
        createBeforeQuestionTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_question_mark);
        createAfterQuestionTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_at);
        createBeforeAtTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_at);
        createAfterAtTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_and);
        createBeforeAndTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_and);
        createAfterAndTree(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_before_ellipsis);
        createBeforeEllipsis(workingValues, parent);

        parent= createParentNode(roots, workingValues, FormatterMessages.WhiteSpaceOptions_after_ellipsis);
        createAfterEllipsis(workingValues, parent);

        return roots;
	}

	private InnerNode createParentNode(List<Node> roots, Map<String, String> workingValues, String text) {
        final InnerNode parent= new InnerNode(null, workingValues, text);
        roots.add(parent);
        return parent;
    }

    public ArrayList<Node> createTreeByJavaElement(Map<String, String> workingValues) {

        final InnerNode declarations= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceTabPage_declarations);
        createClassTree(workingValues, declarations);
        createFieldTree(workingValues, declarations);
        createLocalVariableTree(workingValues, declarations);
        createConstructorTree(workingValues, declarations);
        createMethodDeclTree(workingValues, declarations);
        createLabelTree(workingValues, declarations);
        createAnnotationTree(workingValues, declarations);
        createEnumTree(workingValues, declarations);
        createAnnotationTypeTree(workingValues, declarations);
		createLambdaTree(workingValues, declarations);

        final InnerNode statements= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceTabPage_statements);
        createOption(statements, workingValues, FormatterMessages.WhiteSpaceOptions_before_semicolon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_SEMICOLON, SEMICOLON_PREVIEW);
        createBlockTree(workingValues, statements);
        createIfStatementTree(workingValues, statements);
        createForStatementTree(workingValues, statements);
        createSwitchStatementTree(workingValues, statements);
        createDoWhileTree(workingValues, statements);
        createSynchronizedTree(workingValues, statements);
		createTryStatementTree(workingValues, statements);
		createCatchTree(workingValues, statements);
        createAssertTree(workingValues, statements);
        createReturnTree(workingValues, statements);
        createThrowTree(workingValues, statements);

        final InnerNode expressions= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceTabPage_expressions);
        createFunctionCallTree(workingValues, expressions);
		createAssignmentTree(workingValues, expressions);
		createOperatorTree(workingValues, expressions);
		createParenthesizedExpressionTree(workingValues, expressions);
		createTypecastTree(workingValues, expressions);
		createConditionalTree(workingValues, expressions);

		final InnerNode arrays= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceTabPage_arrays);
		createArrayDeclarationTree(workingValues, arrays);
		createArrayAllocTree(workingValues, arrays);
		createArrayInitializerTree(workingValues, arrays);
		createArrayElementAccessTree(workingValues, arrays);

		final InnerNode paramtypes= new InnerNode(null, workingValues, FormatterMessages.WhiteSpaceTabPage_parameterized_types);
		createParameterizedTypeTree(workingValues, paramtypes);
		createTypeArgumentTree(workingValues, paramtypes);
		createTypeParameterTree(workingValues, paramtypes);
		createWildcardTypeTree(workingValues, paramtypes);

        final ArrayList<Node> roots= new ArrayList<Node>();
		roots.add(declarations);
		roots.add(statements);
		roots.add(expressions);
		roots.add(arrays);
		roots.add(paramtypes);
        return roots;
    }

	private InnerNode createLambdaTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceOptions_lambda);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceOptions_lambda_before_arrow_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_LAMBDA_ARROW, LAMBDA_EXPR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceOptions_lambda_after_arrow_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_LAMBDA_ARROW, LAMBDA_EXPR_PREVIEW);
        return root;
    }

	private void createBeforeQuestionTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_conditional, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_QUESTION_IN_CONDITIONAL, CONDITIONAL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_wildcard, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_QUESTION_IN_WILDCARD, WILDCARD_PREVIEW);
	}

	private void createBeforeAtTree(Map<String, String> workingValues, final InnerNode parent) {
		createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_annotation_type, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_AT_IN_ANNOTATION_TYPE_DECLARATION, ANNOTATION_DECL_PREVIEW);
 	}

	private void createBeforeAndTree(Map<String, String> workingValues, final InnerNode parent) {
		createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_parameters, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_AND_IN_TYPE_PARAMETER, TYPE_PARAMETER_PREVIEW);
 	}

    private void createBeforeSemicolonTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_for, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_SEMICOLON_IN_FOR, FOR_PREVIEW);
		createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_try, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_SEMICOLON_IN_TRY_RESOURCES, TRY_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_statements, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_SEMICOLON, SEMICOLON_PREVIEW);
    }

    private void createBeforeColonTree(Map<String, String> workingValues, final InnerNode parent) {
    	createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_assert, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COLON_IN_ASSERT, ASSERT_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_conditional, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COLON_IN_CONDITIONAL, CONDITIONAL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_label, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COLON_IN_LABELED_STATEMENT, LABEL_PREVIEW);

        final InnerNode switchStatement= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_switch);
        createOption(switchStatement, workingValues, FormatterMessages.WhiteSpaceOptions_case, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COLON_IN_CASE, SWITCH_PREVIEW);
        createOption(switchStatement, workingValues, FormatterMessages.WhiteSpaceOptions_default, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COLON_IN_DEFAULT, SWITCH_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_for, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COLON_IN_FOR, FOR_PREVIEW);
    }

    private void createBeforeCommaTree(Map<String, String> workingValues, final InnerNode parent) {

        final InnerNode forStatement= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_for);
        createOption(forStatement, workingValues, FormatterMessages.WhiteSpaceOptions_initialization, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_FOR_INITS, FOR_PREVIEW);
        createOption(forStatement, workingValues, FormatterMessages.WhiteSpaceOptions_incrementation, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_FOR_INCREMENTS, FOR_PREVIEW);

        final InnerNode invocation= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_arguments);
        createOption(invocation, workingValues, FormatterMessages.WhiteSpaceOptions_method_call, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_METHOD_INVOCATION_ARGUMENTS, METHOD_CALL_PREVIEW);
        createOption(invocation, workingValues, FormatterMessages.WhiteSpaceOptions_explicit_constructor_call, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_EXPLICIT_CONSTRUCTOR_CALL_ARGUMENTS, CONSTRUCTOR_DECL_PREVIEW);
        createOption(invocation, workingValues, FormatterMessages.WhiteSpaceOptions_alloc_expr, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_ALLOCATION_EXPRESSION, ALLOC_PREVIEW);

        final InnerNode decl= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_parameters);
        createOption(decl, workingValues, FormatterMessages.WhiteSpaceOptions_constructor, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_CONSTRUCTOR_DECLARATION_PARAMETERS, CONSTRUCTOR_DECL_PREVIEW);
        createOption(decl, workingValues, FormatterMessages.WhiteSpaceOptions_method, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_METHOD_DECLARATION_PARAMETERS, METHOD_DECL_PREVIEW);

        final InnerNode throwsDecl= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_throws);
        createOption(throwsDecl, workingValues, FormatterMessages.WhiteSpaceOptions_constructor, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_CONSTRUCTOR_DECLARATION_THROWS, CONSTRUCTOR_DECL_PREVIEW);
        createOption(throwsDecl, workingValues, FormatterMessages.WhiteSpaceOptions_method, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_METHOD_DECLARATION_THROWS, METHOD_DECL_PREVIEW);

        final InnerNode multDecls= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_mult_decls);
        createOption(multDecls, workingValues, FormatterMessages.WhiteSpaceOptions_fields, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_MULTIPLE_FIELD_DECLARATIONS, MULT_FIELD_PREVIEW);
        createOption(multDecls, workingValues, FormatterMessages.WhiteSpaceOptions_local_vars, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_MULTIPLE_LOCAL_DECLARATIONS, MULT_LOCAL_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_initializer, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_ARRAY_INITIALIZER, ARRAY_DECL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_implements_clause, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_SUPERINTERFACES, CLASS_DECL_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_enum_declaration, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_ENUM_DECLARATIONS, ENUM_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_enum_constant_arguments, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_ENUM_CONSTANT_ARGUMENTS, ENUM_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_annotation_modifier, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_ANNOTATION, ANNOTATION_MODIFIER_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_parameters, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_TYPE_PARAMETERS, TYPE_PARAMETER_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_parameterized_type, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_PARAMETERIZED_TYPE_REFERENCE, TYPE_ARGUMENTS_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_arguments, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_TYPE_ARGUMENTS, TYPE_ARGUMENTS_PREVIEW);
     }

    private void createBeforeOperatorTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_assignment_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_ASSIGNMENT_OPERATOR, OPERATOR_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_unary_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_UNARY_OPERATOR, OPERATOR_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_binary_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_BINARY_OPERATOR, OPERATOR_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_prefix_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_PREFIX_OPERATOR, OPERATOR_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_postfix_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_POSTFIX_OPERATOR, OPERATOR_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_arrow_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_LAMBDA_ARROW, LAMBDA_EXPR_PREVIEW);
    }

    private void createBeforeClosingBracketTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_array_alloc, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_BRACKET_IN_ARRAY_ALLOCATION_EXPRESSION, ARRAY_DECL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_array_element_access, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_BRACKET_IN_ARRAY_REFERENCE, ARRAY_REF_PREVIEW);
    }

    private void createBeforeClosingAngleBracketTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_parameters, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_ANGLE_BRACKET_IN_TYPE_PARAMETERS, TYPE_PARAMETER_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_parameterized_type, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_ANGLE_BRACKET_IN_PARAMETERIZED_TYPE_REFERENCE, TYPE_ARGUMENTS_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_arguments, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENTS, TYPE_ARGUMENTS_PREVIEW);
    }


    private void createBeforeOpenBracketTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_array_decl, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACKET_IN_ARRAY_TYPE_REFERENCE, ARRAY_DECL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_array_alloc, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACKET_IN_ARRAY_ALLOCATION_EXPRESSION, ARRAY_DECL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_array_element_access, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACKET_IN_ARRAY_REFERENCE, ARRAY_REF_PREVIEW);
    }

    private void createBeforeOpenAngleBracketTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_parameters, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETERS, TYPE_PARAMETER_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_parameterized_type, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_PARAMETERIZED_TYPE_REFERENCE, TYPE_ARGUMENTS_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_arguments, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_ARGUMENTS, TYPE_ARGUMENTS_PREVIEW);
    }

    private void createBeforeClosingBraceTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_array_init, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_BRACE_IN_ARRAY_INITIALIZER, CLASS_DECL_PREVIEW);
    }

    private void createBeforeOpenBraceTree(Map<String, String> workingValues, final InnerNode parent) {

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_class_decl, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_TYPE_DECLARATION, CLASS_DECL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_anon_class_decl, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_ANONYMOUS_TYPE_DECLARATION, ANON_CLASS_PREVIEW);

        final InnerNode functionDecl= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_member_function_declaration); {
            createOption(functionDecl, workingValues, FormatterMessages.WhiteSpaceOptions_constructor, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_CONSTRUCTOR_DECLARATION, CONSTRUCTOR_DECL_PREVIEW);
            createOption(functionDecl, workingValues, FormatterMessages.WhiteSpaceOptions_method, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_METHOD_DECLARATION, METHOD_DECL_PREVIEW);
        }

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_initializer, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_ARRAY_INITIALIZER, ARRAY_DECL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_block, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_BLOCK, BLOCK_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_switch, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_SWITCH, SWITCH_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_enum_declaration, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_ENUM_DECLARATION, ENUM_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_enum_constant_body, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_ENUM_CONSTANT, ENUM_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_annotation_type, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_ANNOTATION_TYPE_DECLARATION, ANNOTATION_DECL_PREVIEW);
    }

    private void createBeforeClosingParenTree(Map<String, String> workingValues, final InnerNode parent) {

		createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_try, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_TRY, TRY_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_catch, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_CATCH, CATCH_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_for, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_FOR, FOR_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_if, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_IF, IF_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_switch, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_SWITCH, SWITCH_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_synchronized, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_SYNCHRONIZED, SYNCHRONIZED_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_while, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_WHILE, WHILE_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_cast, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_CAST, CAST_PREVIEW);

        final InnerNode decl= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_member_function_declaration);
        createOption(decl, workingValues, FormatterMessages.WhiteSpaceOptions_constructor, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_CONSTRUCTOR_DECLARATION, CONSTRUCTOR_DECL_PREVIEW);
        createOption(decl, workingValues, FormatterMessages.WhiteSpaceOptions_method, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_METHOD_DECLARATION, METHOD_DECL_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_method_call, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_METHOD_INVOCATION, METHOD_CALL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_paren_expr, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_PARENTHESIZED_EXPRESSION, PAREN_EXPR_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_enum_constant_arguments, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_ENUM_CONSTANT, ENUM_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_annotation_modifier_args, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_ANNOTATION, ANNOTATION_MODIFIER_PREVIEW);

    }

    private void createBeforeOpenParenTree(Map<String, String> workingValues, final InnerNode parent) {

		createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_try, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_TRY, TRY_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_catch, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_CATCH, CATCH_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_for, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_FOR, FOR_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_if, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_IF, IF_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_switch, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_SWITCH, SWITCH_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_synchronized, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_SYNCHRONIZED, SYNCHRONIZED_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_while, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_WHILE, WHILE_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_return_with_parenthesized_expression, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_PARENTHESIZED_EXPRESSION_IN_RETURN, RETURN_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_throw_with_parenthesized_expression, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_PARENTHESIZED_EXPRESSION_IN_THROW, THROW_PREVIEW);

        final InnerNode decls= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_member_function_declaration);
        createOption(decls, workingValues, FormatterMessages.WhiteSpaceOptions_constructor, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_CONSTRUCTOR_DECLARATION, CONSTRUCTOR_DECL_PREVIEW);
        createOption(decls, workingValues, FormatterMessages.WhiteSpaceOptions_method, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_METHOD_DECLARATION, METHOD_DECL_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_method_call, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_METHOD_INVOCATION, METHOD_CALL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_paren_expr, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_PARENTHESIZED_EXPRESSION, PAREN_EXPR_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_enum_constant_arguments, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_ENUM_CONSTANT, ENUM_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_annotation_modifier_args, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_ANNOTATION, ANNOTATION_MODIFIER_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_annotation_type_member, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_ANNOTATION_TYPE_MEMBER_DECLARATION, ANNOTATION_DECL_PREVIEW);

    }

	private void createAfterQuestionTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_conditional, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_QUESTION_IN_CONDITIONAL, CONDITIONAL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_wildcard, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_QUESTION_IN_WILDCARD, WILDCARD_PREVIEW);
	}

	private void createAfterAtTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_annotation_modifier, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_AT_IN_ANNOTATION, ANNOTATION_MODIFIER_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_annotation_type, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_AT_IN_ANNOTATION_TYPE_DECLARATION, ANNOTATION_DECL_PREVIEW);
	}

	private void createAfterAndTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_parameters, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_AND_IN_TYPE_PARAMETER, TYPE_PARAMETER_PREVIEW);
 	}

	private void createBeforeEllipsis(Map<String, String> workingValues, InnerNode parent) {
		createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_vararg_parameter, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_ELLIPSIS, VARARG_PARAMETER_PREVIEW);

	}

	private void createAfterEllipsis(Map<String, String> workingValues, InnerNode parent) {
		createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_vararg_parameter, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_ELLIPSIS, VARARG_PARAMETER_PREVIEW);
	}

    private void createAfterSemicolonTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_for, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_SEMICOLON_IN_FOR, FOR_PREVIEW);
		createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_try, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_SEMICOLON_IN_TRY_RESOURCES, TRY_PREVIEW);
    }

    private void createAfterColonTree(Map<String, String> workingValues, final InnerNode parent) {
    	createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_assert, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COLON_IN_ASSERT, ASSERT_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_conditional, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COLON_IN_CONDITIONAL, CONDITIONAL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_label, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COLON_IN_LABELED_STATEMENT, LABEL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_for, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COLON_IN_FOR, FOR_PREVIEW);
    }

    private void createAfterCommaTree(Map<String, String> workingValues, final InnerNode parent) {

        final InnerNode forStatement= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_for); {
            createOption(forStatement, workingValues, FormatterMessages.WhiteSpaceOptions_initialization, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_FOR_INITS, FOR_PREVIEW);
            createOption(forStatement, workingValues, FormatterMessages.WhiteSpaceOptions_incrementation, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_FOR_INCREMENTS, FOR_PREVIEW);
        }
        final InnerNode invocation= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_arguments); {
            createOption(invocation, workingValues, FormatterMessages.WhiteSpaceOptions_method, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_METHOD_INVOCATION_ARGUMENTS, METHOD_CALL_PREVIEW);
            createOption(invocation, workingValues, FormatterMessages.WhiteSpaceOptions_explicit_constructor_call, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_EXPLICIT_CONSTRUCTOR_CALL_ARGUMENTS, CONSTRUCTOR_DECL_PREVIEW);
            createOption(invocation, workingValues, FormatterMessages.WhiteSpaceOptions_alloc_expr, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_ALLOCATION_EXPRESSION, ALLOC_PREVIEW);
        }
        final InnerNode decl= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_parameters); {
            createOption(decl, workingValues, FormatterMessages.WhiteSpaceOptions_constructor, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_CONSTRUCTOR_DECLARATION_PARAMETERS, CONSTRUCTOR_DECL_PREVIEW);
            createOption(decl, workingValues, FormatterMessages.WhiteSpaceOptions_method, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_METHOD_DECLARATION_PARAMETERS, METHOD_DECL_PREVIEW);
        }
        final InnerNode throwsDecl= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_throws); {
            createOption(throwsDecl, workingValues, FormatterMessages.WhiteSpaceOptions_constructor, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_CONSTRUCTOR_DECLARATION_THROWS, CONSTRUCTOR_DECL_PREVIEW);
            createOption(throwsDecl, workingValues, FormatterMessages.WhiteSpaceOptions_method, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_METHOD_DECLARATION_THROWS, METHOD_DECL_PREVIEW);
        }
        final InnerNode multDecls= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_mult_decls); {
            createOption(multDecls, workingValues, FormatterMessages.WhiteSpaceOptions_fields, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_MULTIPLE_FIELD_DECLARATIONS, MULT_FIELD_PREVIEW);
            createOption(multDecls, workingValues, FormatterMessages.WhiteSpaceOptions_local_vars, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_MULTIPLE_LOCAL_DECLARATIONS, MULT_LOCAL_PREVIEW);
        }
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_initializer, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_ARRAY_INITIALIZER, ARRAY_DECL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_implements_clause, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_SUPERINTERFACES, CLASS_DECL_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_enum_declaration, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_ENUM_DECLARATIONS, ENUM_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_enum_constant_arguments, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_ENUM_CONSTANT_ARGUMENTS, ENUM_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_annotation_modifier_args, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_ANNOTATION, ANNOTATION_MODIFIER_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_parameters, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_TYPE_PARAMETERS, TYPE_PARAMETER_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_parameterized_type, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_PARAMETERIZED_TYPE_REFERENCE, TYPE_ARGUMENTS_PREVIEW);
	    createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_arguments, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS, TYPE_ARGUMENTS_PREVIEW);
    }

    private void createAfterOperatorTree(Map<String, String> workingValues, final InnerNode parent) {

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_assignment_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_ASSIGNMENT_OPERATOR, OPERATOR_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_unary_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_UNARY_OPERATOR, OPERATOR_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_binary_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_BINARY_OPERATOR, OPERATOR_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_prefix_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_PREFIX_OPERATOR, OPERATOR_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_postfix_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_POSTFIX_OPERATOR, OPERATOR_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_arrow_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_LAMBDA_ARROW, LAMBDA_EXPR_PREVIEW);
    }

    private void createAfterOpenBracketTree(Map<String, String> workingValues, final InnerNode parent) {

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_array_alloc, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_BRACKET_IN_ARRAY_ALLOCATION_EXPRESSION, ARRAY_DECL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_array_element_access, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_BRACKET_IN_ARRAY_REFERENCE, ARRAY_REF_PREVIEW);
    }

    private void createAfterOpenAngleBracketTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_parameters, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETERS, TYPE_PARAMETER_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_parameterized_type, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_ANGLE_BRACKET_IN_PARAMETERIZED_TYPE_REFERENCE, TYPE_ARGUMENTS_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_arguments, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_ANGLE_BRACKET_IN_TYPE_ARGUMENTS, TYPE_ARGUMENTS_PREVIEW);
    }



    private void createAfterOpenBraceTree(Map<String, String> workingValues, final InnerNode parent) {

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_initializer, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_BRACE_IN_ARRAY_INITIALIZER, ARRAY_DECL_PREVIEW);
    }

    private void createAfterCloseBraceTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_block, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_BRACE_IN_BLOCK, BLOCK_PREVIEW);
    }

    private void createAfterCloseParenTree(Map<String, String> workingValues, final InnerNode parent) {

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_cast, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_PAREN_IN_CAST, CAST_PREVIEW);
    }

    private void createAfterClosingAngleBracketTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_parameters, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_PARAMETERS, TYPE_PARAMETER_PREVIEW);
        //createOption(parent, workingValues, "WhiteSpaceOptions.parameterized_type", DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_PARAMETERIZED_TYPE_REFERENCE, TYPE_ARGUMENTS_PREVIEW); //$NON-NLS-1$
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_arguments, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENTS, TYPE_ARGUMENTS_PREVIEW);
    }

    private void createAfterOpenParenTree(Map<String, String> workingValues, final InnerNode parent) {
		createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_try, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_TRY, TRY_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_catch, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_CATCH, CATCH_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_for, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_FOR, FOR_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_if, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_IF, IF_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_switch, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_SWITCH, SWITCH_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_synchronized, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_SYNCHRONIZED, SYNCHRONIZED_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_while, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_WHILE, WHILE_PREVIEW);

        final InnerNode decls= createChild(parent, workingValues, FormatterMessages.WhiteSpaceOptions_member_function_declaration); {
            createOption(decls, workingValues, FormatterMessages.WhiteSpaceOptions_constructor, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_CONSTRUCTOR_DECLARATION, CONSTRUCTOR_DECL_PREVIEW);
            createOption(decls, workingValues, FormatterMessages.WhiteSpaceOptions_method, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_METHOD_DECLARATION, METHOD_DECL_PREVIEW);
        }
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_type_cast, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_CAST, CAST_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_method_call, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_METHOD_INVOCATION, METHOD_CALL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_paren_expr, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_PARENTHESIZED_EXPRESSION, PAREN_EXPR_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_enum_constant_arguments, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_ENUM_CONSTANT, ENUM_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_annotation_modifier_args, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_ANNOTATION, ANNOTATION_MODIFIER_PREVIEW);
    }

    private void createBetweenEmptyParenTree(Map<String, String> workingValues, final InnerNode parent) {

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_constructor_decl, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_PARENS_IN_CONSTRUCTOR_DECLARATION, CONSTRUCTOR_DECL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_method_decl, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_PARENS_IN_METHOD_DECLARATION, METHOD_DECL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_method_call, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_PARENS_IN_METHOD_INVOCATION, METHOD_CALL_PREVIEW);

        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_enum_constant_arguments, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_PARENS_IN_ENUM_CONSTANT, ENUM_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_annotation_type_member, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_PARENS_IN_ANNOTATION_TYPE_MEMBER_DECLARATION, ANNOTATION_DECL_PREVIEW);
    }

    private void createBetweenEmptyBracketsTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_array_alloc, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_BRACKETS_IN_ARRAY_ALLOCATION_EXPRESSION, ARRAY_DECL_PREVIEW);
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_array_decl, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_BRACKETS_IN_ARRAY_TYPE_REFERENCE, ARRAY_DECL_PREVIEW);
    }

    private void createBetweenEmptyBracesTree(Map<String, String> workingValues, final InnerNode parent) {
        createOption(parent, workingValues, FormatterMessages.WhiteSpaceOptions_initializer, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_BRACES_IN_ARRAY_INITIALIZER, ARRAY_DECL_PREVIEW);
    }

    // syntax element tree

    private InnerNode createClassTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_classes);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_classes_before_opening_brace_of_a_class, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_TYPE_DECLARATION, CLASS_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_classes_before_opening_brace_of_anon_class, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_ANONYMOUS_TYPE_DECLARATION, ANON_CLASS_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_classes_before_comma_implements, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_SUPERINTERFACES, CLASS_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_classes_after_comma_implements, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_SUPERINTERFACES, CLASS_DECL_PREVIEW);

        return root;
    }

    private InnerNode createAssignmentTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_assignments);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_assignments_before_assignment_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_ASSIGNMENT_OPERATOR, OPERATOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_assignments_after_assignment_operator, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_ASSIGNMENT_OPERATOR, OPERATOR_PREVIEW);
        return root;
    }

    private InnerNode createOperatorTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_operators);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_operators_before_binary_operators, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_BINARY_OPERATOR, OPERATOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_operators_after_binary_operators, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_BINARY_OPERATOR, OPERATOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_operators_before_unary_operators, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_UNARY_OPERATOR, OPERATOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_operators_after_unary_operators, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_UNARY_OPERATOR, OPERATOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_operators_before_prefix_operators, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_PREFIX_OPERATOR, OPERATOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_operators_after_prefix_operators, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_PREFIX_OPERATOR, OPERATOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_operators_before_postfix_operators, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_POSTFIX_OPERATOR, OPERATOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_operators_after_postfix_operators, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_POSTFIX_OPERATOR, OPERATOR_PREVIEW);
        return root;
    }

    private InnerNode createMethodDeclTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_methods);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_METHOD_DECLARATION, METHOD_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_METHOD_DECLARATION, METHOD_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_METHOD_DECLARATION, METHOD_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_between_empty_parens, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_PARENS_IN_METHOD_DECLARATION, METHOD_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_brace, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_METHOD_DECLARATION, METHOD_DECL_PREVIEW);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_comma_in_params, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_METHOD_DECLARATION_PARAMETERS, METHOD_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_comma_in_params, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_METHOD_DECLARATION_PARAMETERS, METHOD_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_ellipsis, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_ELLIPSIS, VARARG_PARAMETER_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_ellipsis, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_ELLIPSIS, VARARG_PARAMETER_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_comma_in_throws, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_METHOD_DECLARATION_THROWS, METHOD_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_comma_in_throws, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_METHOD_DECLARATION_THROWS, METHOD_DECL_PREVIEW);

        return root;
    }

    private InnerNode createConstructorTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_constructors);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_CONSTRUCTOR_DECLARATION, CONSTRUCTOR_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_CONSTRUCTOR_DECLARATION, CONSTRUCTOR_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_CONSTRUCTOR_DECLARATION, CONSTRUCTOR_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_between_empty_parens, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_PARENS_IN_CONSTRUCTOR_DECLARATION, CONSTRUCTOR_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_brace, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_CONSTRUCTOR_DECLARATION, CONSTRUCTOR_DECL_PREVIEW);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_comma_in_params, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_CONSTRUCTOR_DECLARATION_PARAMETERS, CONSTRUCTOR_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_comma_in_params, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_CONSTRUCTOR_DECLARATION_PARAMETERS, CONSTRUCTOR_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_comma_in_throws, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_CONSTRUCTOR_DECLARATION_THROWS, CONSTRUCTOR_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_comma_in_throws, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_CONSTRUCTOR_DECLARATION_THROWS, CONSTRUCTOR_DECL_PREVIEW);
        return root;
    }

    private InnerNode createFieldTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_fields);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_fields_before_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_MULTIPLE_FIELD_DECLARATIONS, MULT_FIELD_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_fields_after_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_MULTIPLE_FIELD_DECLARATIONS, MULT_LOCAL_PREVIEW);
        return root;
    }

    private InnerNode createLocalVariableTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_localvars);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_localvars_before_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_MULTIPLE_LOCAL_DECLARATIONS, MULT_LOCAL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_localvars_after_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_MULTIPLE_LOCAL_DECLARATIONS, MULT_LOCAL_PREVIEW);
        return root;
    }

    private InnerNode createArrayInitializerTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_arrayinit);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_brace, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_ARRAY_INITIALIZER, ARRAY_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_brace, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_BRACE_IN_ARRAY_INITIALIZER, ARRAY_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_brace, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_BRACE_IN_ARRAY_INITIALIZER, ARRAY_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_ARRAY_INITIALIZER, ARRAY_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_ARRAY_INITIALIZER, ARRAY_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_between_empty_braces, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_BRACES_IN_ARRAY_INITIALIZER, ARRAY_DECL_PREVIEW);
        return root;
    }

    private InnerNode createArrayDeclarationTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_arraydecls);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACKET_IN_ARRAY_TYPE_REFERENCE, ARRAY_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_between_empty_brackets, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_BRACKETS_IN_ARRAY_TYPE_REFERENCE, ARRAY_DECL_PREVIEW);
        return root;
    }

    private InnerNode createArrayElementAccessTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_arrayelem);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACKET_IN_ARRAY_REFERENCE, ARRAY_REF_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_BRACKET_IN_ARRAY_REFERENCE, ARRAY_REF_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_BRACKET_IN_ARRAY_REFERENCE, ARRAY_REF_PREVIEW);

        return root;
    }

    private InnerNode createArrayAllocTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_arrayalloc);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACKET_IN_ARRAY_ALLOCATION_EXPRESSION, ARRAY_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_BRACKET_IN_ARRAY_ALLOCATION_EXPRESSION, ARRAY_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_BRACKET_IN_ARRAY_ALLOCATION_EXPRESSION, ARRAY_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_between_empty_brackets, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_BRACKETS_IN_ARRAY_ALLOCATION_EXPRESSION, ARRAY_DECL_PREVIEW);
        return root;
    }

    private InnerNode createFunctionCallTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_calls);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_METHOD_INVOCATION, METHOD_CALL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_METHOD_INVOCATION, METHOD_CALL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_METHOD_INVOCATION, METHOD_CALL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_between_empty_parens, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_PARENS_IN_METHOD_INVOCATION, METHOD_CALL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_calls_before_comma_in_method_args, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_METHOD_INVOCATION_ARGUMENTS, METHOD_CALL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_calls_after_comma_in_method_args, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_METHOD_INVOCATION_ARGUMENTS, METHOD_CALL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_calls_before_comma_in_alloc, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_ALLOCATION_EXPRESSION, ALLOC_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_calls_after_comma_in_alloc, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_ALLOCATION_EXPRESSION, ALLOC_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_calls_before_comma_in_qalloc, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_EXPLICIT_CONSTRUCTOR_CALL_ARGUMENTS, CONSTRUCTOR_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_calls_after_comma_in_qalloc, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_EXPLICIT_CONSTRUCTOR_CALL_ARGUMENTS, CONSTRUCTOR_DECL_PREVIEW);
        return root;
    }

    private InnerNode createBlockTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_blocks);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_brace, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_BLOCK, BLOCK_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_closing_brace, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_BRACE_IN_BLOCK, BLOCK_PREVIEW);
        return root;
    }

    private InnerNode createSwitchStatementTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_switch);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_switch_before_case_colon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COLON_IN_CASE, SWITCH_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_switch_before_default_colon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COLON_IN_DEFAULT, SWITCH_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_brace, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_SWITCH, SWITCH_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_SWITCH, SWITCH_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_SWITCH, SWITCH_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_SWITCH, SWITCH_PREVIEW);
        return root;
    }

    private InnerNode createDoWhileTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_do);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_WHILE, WHILE_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_WHILE, WHILE_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_WHILE, WHILE_PREVIEW);

        return root;
    }

    private InnerNode createSynchronizedTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_synchronized);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_SYNCHRONIZED, SYNCHRONIZED_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_SYNCHRONIZED, SYNCHRONIZED_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_SYNCHRONIZED, SYNCHRONIZED_PREVIEW);
        return root;
    }

	private InnerNode createCatchTree(Map<String, String> workingValues, InnerNode parent) {
		final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_catch);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_CATCH, CATCH_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_CATCH, CATCH_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_CATCH, CATCH_PREVIEW);
        return root;
    }

	private InnerNode createTryStatementTree(Map<String, String> workingValues, InnerNode parent) {
		final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_tryWithResources);

		createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_TRY, TRY_PREVIEW);
		createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_TRY, TRY_PREVIEW);
		createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_semicolon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_SEMICOLON_IN_TRY_RESOURCES, TRY_PREVIEW);
		createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_semicolon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_SEMICOLON_IN_TRY_RESOURCES, TRY_PREVIEW);
		createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_TRY, TRY_PREVIEW);
		return root;
	}

    private InnerNode createIfStatementTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_if);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_IF, IF_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_IF, IF_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_IF, IF_PREVIEW);
        return root;
    }

    private InnerNode createForStatementTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_for);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_FOR, FOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_FOR, FOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_FOR, FOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_for_before_comma_init, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_FOR_INITS, FOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_for_after_comma_init, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_FOR_INITS, FOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_for_before_comma_inc, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_FOR_INCREMENTS, FOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_for_after_comma_inc, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_FOR_INCREMENTS, FOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_semicolon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_SEMICOLON_IN_FOR, FOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_semicolon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_SEMICOLON_IN_FOR, FOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_colon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COLON_IN_FOR, FOR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_colon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COLON_IN_FOR, FOR_PREVIEW);

        return root;
    }

    private InnerNode createAssertTree(Map<String, String> workingValues, InnerNode parent) {
    	final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_assert);
    	createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_colon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COLON_IN_ASSERT, ASSERT_PREVIEW);
    	createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_colon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COLON_IN_ASSERT, ASSERT_PREVIEW);
    	return root;
    }

    private InnerNode createReturnTree(Map<String, String> workingValues, InnerNode parent) {
    	final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceOptions_return);
    	createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_parenthesized_expressions, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_PARENTHESIZED_EXPRESSION_IN_RETURN, RETURN_PREVIEW);
    	return root;
    }

    private InnerNode createThrowTree(Map<String, String> workingValues, InnerNode parent) {
    	final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceOptions_throw);
    	createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_parenthesized_expressions, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_PARENTHESIZED_EXPRESSION_IN_THROW, THROW_PREVIEW);
    	return root;
    }

    private InnerNode createLabelTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_labels);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_colon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COLON_IN_LABELED_STATEMENT, LABEL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_colon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COLON_IN_LABELED_STATEMENT, LABEL_PREVIEW);
        return root;
    }

    private InnerNode createAnnotationTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_annotations);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_at, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_AT_IN_ANNOTATION, ANNOTATION_MODIFIER_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_ANNOTATION, ANNOTATION_MODIFIER_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_ANNOTATION, ANNOTATION_MODIFIER_PREVIEW);
        //createOption(root, workingValues, "WhiteSpaceTabPage.between_empty_parens", DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_PARENS_IN_ANNOTATION, ANNOTATION_MODIFIER_PREVIEW); //$NON-NLS-1$
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_ANNOTATION, ANNOTATION_MODIFIER_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_ANNOTATION, ANNOTATION_MODIFIER_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_ANNOTATION, ANNOTATION_MODIFIER_PREVIEW);
        //createOption(root, workingValues, "WhiteSpaceTabPage.after_closing_paren", DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_PAREN_IN_ANNOTATION, ANNOTATION_MODIFIER_PREVIEW); //$NON-NLS-1$

        return root;
    }

    private InnerNode createAnnotationTypeTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_annotation_types);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_at, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_AT_IN_ANNOTATION_TYPE_DECLARATION, ANNOTATION_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_at, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_AT_IN_ANNOTATION_TYPE_DECLARATION, ANNOTATION_DECL_PREVIEW);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_brace, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_ANNOTATION_TYPE_DECLARATION, ANNOTATION_DECL_PREVIEW);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_annot_type_method_before_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_ANNOTATION_TYPE_MEMBER_DECLARATION, ANNOTATION_DECL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_annot_type_method_between_empty_parens, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_PARENS_IN_ANNOTATION_TYPE_MEMBER_DECLARATION, ANNOTATION_DECL_PREVIEW);
        return root;
    }

    private InnerNode createEnumTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_enums);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_enum_decl_before_opening_brace, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_ENUM_DECLARATION, ENUM_PREVIEW);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_enum_decl_before_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_ENUM_DECLARATIONS, ENUM_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_enum_decl_after_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_ENUM_DECLARATIONS, ENUM_PREVIEW);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_enum_const_arg_before_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_ENUM_CONSTANT, ENUM_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_enum_const_arg_after_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_ENUM_CONSTANT, ENUM_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_enum_const_arg_between_empty_parens, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BETWEEN_EMPTY_PARENS_IN_ENUM_CONSTANT, ENUM_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_enum_const_arg_before_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_ENUM_CONSTANT_ARGUMENTS, ENUM_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_enum_const_arg_after_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_ENUM_CONSTANT_ARGUMENTS, ENUM_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_enum_const_arg_before_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_ENUM_CONSTANT, ENUM_PREVIEW);
        //createOption(inner, workingValues, "WhiteSpaceTabPage.enum_const_arg.after_closing_paren", DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_PAREN_IN_ENUM_CONSTANT, ENUM_PREVIEW); //$NON-NLS-1$

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_enum_const_before_opening_brace, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_BRACE_IN_ENUM_CONSTANT, ENUM_PREVIEW);
        return root;
    }

    private InnerNode createParameterizedTypeTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_param_type_ref);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_angle_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_PARAMETERIZED_TYPE_REFERENCE, PARAMETERIZED_TYPE_REFERENCE_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_angle_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_ANGLE_BRACKET_IN_PARAMETERIZED_TYPE_REFERENCE, PARAMETERIZED_TYPE_REFERENCE_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_PARAMETERIZED_TYPE_REFERENCE, PARAMETERIZED_TYPE_REFERENCE_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_PARAMETERIZED_TYPE_REFERENCE, PARAMETERIZED_TYPE_REFERENCE_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_angle_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_ANGLE_BRACKET_IN_PARAMETERIZED_TYPE_REFERENCE, PARAMETERIZED_TYPE_REFERENCE_PREVIEW);
        //createOption(root, workingValues, "WhiteSpaceTabPage.after_closing_angle_bracket", DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_PARAMETERIZED_TYPE_REFERENCE, TYPE_ARGUMENTS_PREVIEW); //$NON-NLS-1$
        return root;
    }

    private InnerNode createTypeArgumentTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_type_arguments);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_angle_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_ARGUMENTS, TYPE_ARGUMENTS_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_angle_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_ANGLE_BRACKET_IN_TYPE_ARGUMENTS, TYPE_ARGUMENTS_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_TYPE_ARGUMENTS, TYPE_ARGUMENTS_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_comma, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS, TYPE_ARGUMENTS_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_angle_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENTS, TYPE_ARGUMENTS_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_closing_angle_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENTS, TYPE_ARGUMENTS_PREVIEW);
        return root;
    }

    private InnerNode createTypeParameterTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_type_parameters);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_angle_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETERS, TYPE_PARAMETER_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_angle_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETERS, TYPE_PARAMETER_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_comma_in_params, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_TYPE_PARAMETERS, TYPE_PARAMETER_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_comma_in_params, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_TYPE_PARAMETERS, TYPE_PARAMETER_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_angle_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_ANGLE_BRACKET_IN_TYPE_PARAMETERS, TYPE_PARAMETER_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_closing_angle_bracket, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_PARAMETERS, TYPE_PARAMETER_PREVIEW);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_and_list, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_AND_IN_TYPE_PARAMETER, TYPE_PARAMETER_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_and_list, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_AND_IN_TYPE_PARAMETER, TYPE_PARAMETER_PREVIEW);

        return root;
    }

    private InnerNode createWildcardTypeTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_wildcardtype);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_question, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_QUESTION_IN_WILDCARD, WILDCARD_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_question, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_QUESTION_IN_WILDCARD, WILDCARD_PREVIEW);
        return root;
    }


    private InnerNode createConditionalTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_conditionals);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_question, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_QUESTION_IN_CONDITIONAL, CONDITIONAL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_question, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_QUESTION_IN_CONDITIONAL, CONDITIONAL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_colon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COLON_IN_CONDITIONAL, CONDITIONAL_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_colon, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COLON_IN_CONDITIONAL, CONDITIONAL_PREVIEW);
        return root;
    }


    private InnerNode createTypecastTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_typecasts);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_CAST, CAST_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_CAST, CAST_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_PAREN_IN_CAST, CAST_PREVIEW);
        return root;
    }


    private InnerNode createParenthesizedExpressionTree(Map<String, String> workingValues, InnerNode parent) {
        final InnerNode root= new InnerNode(parent, workingValues, FormatterMessages.WhiteSpaceTabPage_parenexpr);

        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_PAREN_IN_PARENTHESIZED_EXPRESSION, PAREN_EXPR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_after_opening_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_PAREN_IN_PARENTHESIZED_EXPRESSION, PAREN_EXPR_PREVIEW);
        createOption(root, workingValues, FormatterMessages.WhiteSpaceTabPage_before_closing_paren, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_PAREN_IN_PARENTHESIZED_EXPRESSION, PAREN_EXPR_PREVIEW);
        return root;
	}



    private static InnerNode createChild(InnerNode root, Map<String, String> workingValues, String message) {
	    return new InnerNode(root, workingValues, message);
	}

	private static OptionNode createOption(InnerNode root, Map<String, String> workingValues, String message, String key, PreviewSnippet snippet) {
	    return new OptionNode(root, workingValues, message, key, snippet);
	}

	public static void makeIndexForNodes(List<Node> tree, List<Node> flatList) {
        for (final Iterator<Node> iter= tree.iterator(); iter.hasNext();) {
            final Node node= iter.next();
            node.index= flatList.size();
            flatList.add(node);
            makeIndexForNodes(node.getChildren(), flatList);
        }
    }
}

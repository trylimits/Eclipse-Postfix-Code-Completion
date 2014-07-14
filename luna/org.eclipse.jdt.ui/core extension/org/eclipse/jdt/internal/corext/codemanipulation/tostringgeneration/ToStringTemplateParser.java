/*******************************************************************************
 * Copyright (c) 2008, 2011 Mateusz Matela and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Mateusz Matela <mateusz.matela@gmail.com> - [code manipulation] [dcr] toString() builder wizard - https://bugs.eclipse.org/bugs/show_bug.cgi?id=26070
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationMessages;

/**
 * <p>
 * A class used for parsing the toString() template and storing the results.
 * </p>
 * <p>
 * The template is divided into four parts:
 * <ul>
 * <li><b>Beginning</b> - all the characters from the beginning of the template (inclusively) to the
 * first occurrence of any <code>{$member.*}</code> variable (exclusively). This part can contain
 * only <code>${object.*}</code> variables and is used to generate the beginning of the generated
 * <code>String</code>.</li>
 * <li><b>Body</b> - all the characters from the first occurrence of any <code>${member.*}</code>
 * variable to the variable directly preceding the <code>${otherMembers}</code>, inclusively. The
 * content of this part is used once for every processed member. All template variables are
 * available (except for <code>${otherMembers}</code>).
 * <li><b>Separator</b></li> - all the characters from the template variable directly preceding the
 * <code>${otherMembers}</code> to the <code>${otherMembers}</code>, exclusively. This part does not
 * contain template variables and is used to separate members in the generated <code>String</code>.
 * <li><b>Ending</b> - all the characters from the occurrence of the <code>${otherMembers}</code>
 * variable (exclusively) to the end of the template (inclusively). This part can also contain only
 * <code>${object.*}</code> variables.</li>
 * </ul>
 * </p>
 * <p>
 * Note: for the template to work properly, it must contain at least one <code>${member.*}</code>
 * variable and exactly one <code>${otherMembers}</code> variable. Additionally, no
 * <code>${member.*}</code> variables can follow the <code>${otherMembers}</code> variable.
 * </p>
 * 
 * @since 3.5
 */
public class ToStringTemplateParser {
	protected String[] beginning;

	protected String[] body;

	protected String separator;

	protected String[] ending;

	protected Map<String, String> descriptions;

	/** The variable that inserts the name of the object's class **/
	public final static String OBJECT_NAME_VARIABLE= "${object.className}"; //$NON-NLS-1$

	/** The variable that inserts a call to <code>this.getClass.getName()</code> **/
	public final static String OBJECT_GET_NAME_VARIABLE= "${object.getClassName}"; //$NON-NLS-1$

	/** The variable that inserts a call to <code>super.toString()</code> **/
	public final static String OBJECT_SUPER_TOSTRING_VARIABLE= "${object.superToString}"; //$NON-NLS-1$

	/** The variable that inserts a call to <code>this.hashCode()</code> **/
	public final static String OBJECT_HASHCODE_VARIABLE= "${object.hashCode}"; //$NON-NLS-1$

	/** The variable that inserts a call to <code>System.identityHashCode(this)</code> **/
	public final static String OBJECT_SYSTEM_HASHCODE_VARIABLE= "${object.identityHashCode}"; //$NON-NLS-1$

	/** The variable that inserts the member name **/
	public final static String MEMBER_NAME_VARIABLE= "${member.name}"; //$NON-NLS-1$

	/** The variable that inserts the member name followed by parenthesis in case of methods **/
	public final static String MEMBER_NAME_PARENTHESIS_VARIABLE= "${member.name()}"; //$NON-NLS-1$

	/** The variable that inserts the values of a member **/
	public final static String MEMBER_VALUE_VARIABLE= "${member.value}"; //$NON-NLS-1$

	/** The variable used for determining separator between members **/
	private final static String OTHER_MEMBERS_VARIABLE= "${otherMembers}"; //$NON-NLS-1$

	private final static String[] OBJECT_RELAED_VARIABLE= { OBJECT_NAME_VARIABLE, OBJECT_GET_NAME_VARIABLE, OBJECT_SUPER_TOSTRING_VARIABLE, OBJECT_HASHCODE_VARIABLE, OBJECT_SYSTEM_HASHCODE_VARIABLE };

	private final static String[] MEMBER_RELATED_VARIABLE= { MEMBER_NAME_VARIABLE, MEMBER_NAME_PARENTHESIS_VARIABLE, MEMBER_VALUE_VARIABLE };

	private final static String[] OBJECT_AND_MEMBER_RELATED_VARIABLES= { OBJECT_NAME_VARIABLE, OBJECT_GET_NAME_VARIABLE, OBJECT_SUPER_TOSTRING_VARIABLE, OBJECT_HASHCODE_VARIABLE,
			OBJECT_SYSTEM_HASHCODE_VARIABLE, MEMBER_NAME_VARIABLE, MEMBER_NAME_PARENTHESIS_VARIABLE, MEMBER_VALUE_VARIABLE };

	/** A default template for toString() format **/
	public final static String DEFAULT_TEMPLATE= "${object.className} [${member.name()}=${member.value}, ${otherMembers}]"; //$NON-NLS-1$

	/** Variables that can be used in toSting() format template **/
	private final static String[] VARIABLES= {
			OBJECT_NAME_VARIABLE,
			OBJECT_GET_NAME_VARIABLE,
			OBJECT_SUPER_TOSTRING_VARIABLE,
			OBJECT_HASHCODE_VARIABLE,
			OBJECT_SYSTEM_HASHCODE_VARIABLE,
			MEMBER_NAME_VARIABLE,
			MEMBER_NAME_PARENTHESIS_VARIABLE,
			MEMBER_VALUE_VARIABLE,
			OTHER_MEMBERS_VARIABLE };

	/** Descriptions of variables in toString format template **/
	private final static String[] VARIABLE_DESCRIPTIONS= {
			CodeGenerationMessages.GenerateToStringOperation_objectClassNameVariableDescription,
			CodeGenerationMessages.GenerateToStringOperation_objectClassGetNameVariableDescription,
			CodeGenerationMessages.GenerateToStringOperation_objectSuperToStringVariableDescription,
			CodeGenerationMessages.GenerateToStringOperation_objectHashCodeVariableDescription,
			CodeGenerationMessages.GenerateToStringOperation_objectIdentityHashCodeVariableDescription,
			CodeGenerationMessages.GenerateToStringOperation_memberNameVariableDescription,
			CodeGenerationMessages.GenerateToStringOperation_memberNameParenthesesVariableDescription,
			CodeGenerationMessages.GenerateToStringOperation_memberValueVariableDescription,
			CodeGenerationMessages.GenerateToStringOperation_otherFieldsVariableDescription };

	/**
	 * This method is used in {@link #parseTemplate(String)} to determine what member specific
	 * variables are expected in a template.
	 * 
	 * @return member related variables recognized by this parser
	 */
	protected String[] getMemberRelatedVariables() {
		return ToStringTemplateParser.MEMBER_RELATED_VARIABLE;
	}

	/**
	 * This method is used in {@link #parseTemplate(String)} to determine what object related
	 * variables are expected in a template.
	 * 
	 * @return object related variables recognized by this parser
	 */
	protected String[] getObjectRelatedVariables() {
		return ToStringTemplateParser.OBJECT_RELAED_VARIABLE;
	}

	/**
	 * This method is used in {@link #parseTemplate(String)}. It returns all variables returned by
	 * {@link #getMemberRelatedVariables()} and {@link #getObjectRelatedVariables()} (sum of sets).
	 * 
	 * @return member and object related variables recognized by this parser (all variables but
	 *         {$otherMembers})
	 */
	protected String[] getObjectAndMemberRelatedVariables() {
		return ToStringTemplateParser.OBJECT_AND_MEMBER_RELATED_VARIABLES;
	}

	public void parseTemplate(String template) {
		String[] emptyArray= new String[0];
		int beginningEnd= firstOccuranceOf(template, getMemberRelatedVariables());
		if (beginningEnd >= 0) {
			beginning= extractElements(template.substring(0, beginningEnd), getObjectRelatedVariables()).toArray(emptyArray);
		} else {
			beginningEnd= 0;
			beginning= emptyArray;
		}
		int endingStart= template.indexOf(ToStringTemplateParser.OTHER_MEMBERS_VARIABLE);
		if (endingStart == -1)
			endingStart= template.length();

		ArrayList<String> bodyList= extractElements(template.substring(beginningEnd, endingStart), getObjectAndMemberRelatedVariables());

		try {
			separator= bodyList.get(bodyList.size() - 1);
			bodyList.remove(bodyList.size() - 1);
		} catch (Exception e) {
			separator= ""; //$NON-NLS-1$
		}

		body= bodyList.toArray(emptyArray);
		ending= extractElements(template.substring(endingStart + ToStringTemplateParser.OTHER_MEMBERS_VARIABLE.length()), getObjectRelatedVariables()).toArray(emptyArray);

	}

	protected int firstOccuranceOf(String template, String[] wantedVariables) {
		int result= -1;
		for (int i= 0; i < wantedVariables.length; i++) {
			int indexOf= template.indexOf(wantedVariables[i]);
			if (result == -1 || (indexOf > 0 && indexOf < result))
				result= indexOf;
		}
		return result;
	}

	protected ArrayList<String> extractElements(String template, String[] wantedVariables) {
		ArrayList<String> result= new ArrayList<String>();
		while (true) {
			if (template.length() == 0)
				break;
			String foundVariable= null;
			int variablePosition= template.length();
			for (int i= 0; i < wantedVariables.length; i++) {
				int position= template.indexOf(wantedVariables[i]);
				if (position >= 0 && position < variablePosition) {
					variablePosition= position;
					foundVariable= wantedVariables[i];
				}
			}
			if (variablePosition == template.length()) {
				result.add(template);
				break;
			} else {
				if (variablePosition != 0)
					result.add(template.substring(0, variablePosition));
				result.add(foundVariable);
				template= template.substring(variablePosition + foundVariable.length());
			}
		}
		return result;
	}

	public String[] getBeginning() {
		return beginning;
	}

	public String[] getBody() {
		return body;
	}

	public String[] getEnding() {
		return ending;
	}

	public String getSeparator() {
		return separator;
	}

	public Map<String, String> getVariableDescriptions() {
		if (descriptions == null) {
			descriptions= new HashMap<String, String>();
			for (int i= 0; i < ToStringTemplateParser.VARIABLES.length; i++)
				descriptions.put(ToStringTemplateParser.VARIABLES[i], ToStringTemplateParser.VARIABLE_DESCRIPTIONS[i]);
		}
		return descriptions;
	}

	public String[] getVariables() {
		return ToStringTemplateParser.VARIABLES;
	}
}

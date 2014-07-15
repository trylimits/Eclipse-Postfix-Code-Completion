/*******************************************************************************
 * Copyright (c) 2013, 2014 GK Software AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Stephan Herrmann - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.compiler.lookup;

import java.util.Set;

import org.eclipse.jdt.core.compiler.CharOperation;

/**
 * Implementation of 18.1.1 in JLS8
 */
public class InferenceVariable extends TypeVariableBinding {

	InvocationSite site;
	TypeBinding typeParameter;
	long nullHints;
	
	public InferenceVariable(TypeBinding typeParameter, int variableRank, InvocationSite site, LookupEnvironment environment, ReferenceBinding object) {
		super(CharOperation.concat(typeParameter.shortReadableName(), Integer.toString(variableRank).toCharArray(), '#'), 
				null/*declaringElement*/, variableRank, environment);
		this.site = site;
		this.typeParameter = typeParameter;
		this.tagBits |= typeParameter.tagBits & TagBits.AnnotationNullMASK;
		if (typeParameter.isTypeVariable()) {
			TypeVariableBinding typeVariable = (TypeVariableBinding) typeParameter;
			if (typeVariable.firstBound != null) {
				long boundBits = typeVariable.firstBound.tagBits & TagBits.AnnotationNullMASK;
				if (boundBits == TagBits.AnnotationNonNull)
					this.tagBits |= boundBits; // @NonNull must be preserved
				else
					this.nullHints |= boundBits; // @Nullable is only a hint
			}
		}
		this.superclass = object;
	}

	public char[] constantPoolName() {
		throw new UnsupportedOperationException();
	}

	public PackageBinding getPackage() {
		throw new UnsupportedOperationException();
	}

	public boolean isCompatibleWith(TypeBinding right, Scope scope) {
		// if inference variables are ever checked for compatibility
		// (like during inner resolve of a ReferenceExpression during inference)
		// treat it as a wildcard, compatible with any any and every type.
		return true;
	}

	public boolean isProperType(boolean admitCapture18) {
		return false;
	}

	TypeBinding substituteInferenceVariable(InferenceVariable var, TypeBinding substituteType) {
		if (this == var) //$IDENTITY-COMPARISON$ InferenceVariable
			return substituteType;
		return this;
	}

	void collectInferenceVariables(Set<InferenceVariable> variables) {
		variables.add(this);
	}

	public ReferenceBinding[] superInterfaces() {
		return Binding.NO_SUPERINTERFACES;
	}

	public char[] qualifiedSourceName() {
		throw new UnsupportedOperationException();
	}

	public char[] sourceName() {
		return this.sourceName;
	}

	public char[] readableName() {
		return this.sourceName;
	}

	public boolean hasTypeBit(int bit) {
		throw new UnsupportedOperationException();
	}
	
	public String debugName() {
		return String.valueOf(this.sourceName);
	}
	
	public String toString() {
		return debugName();
	}
	
	public int hashCode() {
		if (this.sourceName != null)
			return this.sourceName.hashCode();
		return super.hashCode();
	}
	public boolean equals(Object obj) {
		if (!(obj instanceof InferenceVariable))
			return false;
		if (this.sourceName != null)
			return this.sourceName.equals(((InferenceVariable)obj).sourceName);
		return super.equals(obj);
	}

	public TypeBinding erasure() {
		// lazily initialize field that may be required in super.erasure():
		if (this.superclass == null)
			this.superclass = this.environment.getType(TypeConstants.JAVA_LANG_OBJECT);
		return super.erasure();
	}
}

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
package org.eclipse.jdt.internal.ui;

import java.util.List;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;

import org.eclipse.jface.resource.CompositeImageDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IDecoration;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameRequestor;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

public class InterfaceIndicatorLabelDecorator extends AbstractJavaElementLabelDecorator {

	private static class TypeIndicatorOverlay extends CompositeImageDescriptor {
		private static Point fgSize;
		
		private final ImageDescriptor fType;
		private final boolean fDeprecated;
		private final boolean fPackageDefault;
	
		public TypeIndicatorOverlay(ImageDescriptor type, boolean deprecated, boolean packageDefault) {
			fType= type;
			fDeprecated= deprecated;
			fPackageDefault= packageDefault;
		}
		
		/*
		 * @see java.lang.Object#equals(java.lang.Object)
		 * @since 3.9
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TypeIndicatorOverlay other= (TypeIndicatorOverlay) obj;
			if (fDeprecated != other.fDeprecated)
				return false;
			if (fPackageDefault != other.fPackageDefault)
				return false;
			if (fType == null) {
				if (other.fType != null)
					return false;
			} else if (!fType.equals(other.fType))
				return false;
			return true;
		}

		/*
		 * @see org.eclipse.jface.resource.CompositeImageDescriptor#drawCompositeImage(int, int)
		 */
		@Override
		protected void drawCompositeImage(int width, int height) {
			if (fDeprecated) {
				ImageData imageData= JavaPluginImages.DESC_OVR_DEPRECATED.getImageData();
				drawImage(imageData, -1, 1); // looks better, esp. together with interface indicator
			}
			if (fType != null) { // on top of deprecated indicator
				ImageData imageData= fType.getImageData();
				drawImage(imageData, width - imageData.width, 0);
			}
			if (fPackageDefault) {
				ImageData imageData= JavaPluginImages.DESC_OVR_DEFAULT.getImageData();
				drawImage(imageData, width - imageData.width, height - imageData.height);
			}
		}
		
		/*
		 * @see org.eclipse.jface.resource.CompositeImageDescriptor#getSize()
		 */
		@Override
		protected Point getSize() {
			if (fgSize == null) {
				ImageData imageData= JavaPluginImages.DESC_OVR_DEPRECATED.getImageData();
				fgSize= new Point(imageData.width, imageData.height);
			}
			return fgSize;
		}

		/*
		 * @see java.lang.Object#hashCode()
		 * @since 3.9
		 */
		@Override
		public int hashCode() {
			final int prime= 31;
			int result= 1;
			result= prime * result + (fDeprecated ? 1231 : 1237);
			result= prime * result + (fPackageDefault ? 1231 : 1237);
			result= prime * result + ((fType == null) ? 0 : fType.hashCode());
			return result;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void decorate(Object element, IDecoration decoration) {
		try {
			addOverlays(element, decoration);
		} catch (JavaModelException e) {
			return;
		}
	}

	private void addOverlays(Object element, IDecoration decoration) throws JavaModelException {
		if (element instanceof ICompilationUnit) {
			ICompilationUnit unit= (ICompilationUnit) element;
			if (unit.isOpen()) {
				IType mainType= unit.findPrimaryType();
				if (mainType != null) {
					addOverlaysFromFlags(mainType.getFlags(), decoration);
				}
				return;
			}
			String typeName= JavaCore.removeJavaLikeExtension(unit.getElementName());
			addOverlaysWithSearchEngine(unit, typeName, decoration);
			
		} else if (element instanceof IClassFile) {
			IClassFile classFile= (IClassFile) element;
			if (classFile.isOpen()) {
				addOverlaysFromFlags(classFile.getType().getFlags(), decoration);
			} else {
				String typeName= classFile.getType().getElementName();
				addOverlaysWithSearchEngine(classFile, typeName, decoration);
			}
		}
	}

	private void addOverlaysWithSearchEngine(ITypeRoot element, String typeName, IDecoration decoration) {
		SearchEngine engine= new SearchEngine();
		IJavaSearchScope scope= SearchEngine.createJavaSearchScope(new IJavaElement[] { element });

		class Result extends RuntimeException {
			private static final long serialVersionUID= 1L;
			int modifiers;
			public Result(int modifiers) {
				this.modifiers= modifiers;
			}
		}

		TypeNameRequestor requestor= new TypeNameRequestor() {
			@Override
			public void acceptType(int modifiers, char[] packageName, char[] simpleTypeName, char[][] enclosingTypeNames, String path) {
				if (enclosingTypeNames.length == 0 /*&& Flags.isPublic(modifiers)*/) {
					throw new Result(modifiers);
				}
			}
		};

		try {
			String packName = element.getParent().getElementName();
			int matchRule = SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE;
			engine.searchAllTypeNames(packName.toCharArray(), matchRule, typeName.toCharArray(), matchRule, IJavaSearchConstants.TYPE, scope, requestor, IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH , null);
		} catch (Result e) {
			addOverlaysFromFlags(e.modifiers, decoration);
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		}

	}
	
	private void addOverlaysFromFlags(int flags, IDecoration decoration) {
		ImageDescriptor type;
		if (Flags.isAnnotation(flags)) {
			type= JavaPluginImages.DESC_OVR_ANNOTATION;
		} else if (Flags.isEnum(flags)) {
			type= JavaPluginImages.DESC_OVR_ENUM;
		} else if (Flags.isInterface(flags)) {
			type= JavaPluginImages.DESC_OVR_INTERFACE;
		} else if (/* is class */ Flags.isAbstract(flags)) {
			type= JavaPluginImages.DESC_OVR_ABSTRACT_CLASS;
		} else {
			type= null;
		}
		
		boolean deprecated= Flags.isDeprecated(flags);
		boolean packageDefault= Flags.isPackageDefault(flags);
		
		/* Each decoration position can only be used once. Since we don't want to take all positions
		 * away from other decorators, we confine ourselves to only use the top right position. */
		
		if (type != null && !deprecated && !packageDefault) {
			decoration.addOverlay(type, IDecoration.TOP_RIGHT);
			
		} else if (type == null && deprecated && !packageDefault) {
			decoration.addOverlay(JavaPluginImages.DESC_OVR_DEPRECATED, IDecoration.TOP_RIGHT);
			
		} else if (type != null || deprecated || packageDefault) {
			decoration.addOverlay(new TypeIndicatorOverlay(type, deprecated, packageDefault), IDecoration.TOP_RIGHT);
		}
	}

	@Override
	protected void processDelta(IJavaElementDelta delta, List<IJavaElement> result) {
		IJavaElement elem= delta.getElement();

		boolean isChanged= delta.getKind() == IJavaElementDelta.CHANGED;
		boolean isRemoved= delta.getKind() == IJavaElementDelta.REMOVED;
		int flags= delta.getFlags();

		switch (elem.getElementType()) {
			case IJavaElement.JAVA_PROJECT:
				if (isRemoved || (isChanged &&
						(flags & IJavaElementDelta.F_CLOSED) != 0)) {
					return;
				}
				processChildrenDelta(delta, result);
				return;
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				if (isRemoved || (isChanged && (
						(flags & IJavaElementDelta.F_ARCHIVE_CONTENT_CHANGED) != 0 ||
						(flags & IJavaElementDelta.F_REMOVED_FROM_CLASSPATH) != 0))) {
					return;
				}
				processChildrenDelta(delta, result);
				return;
			case IJavaElement.PACKAGE_FRAGMENT:
				if (isRemoved)
					return;
				processChildrenDelta(delta, result);
				return;
			case IJavaElement.TYPE:
			case IJavaElement.CLASS_FILE:
				return;
			case IJavaElement.JAVA_MODEL:
				processChildrenDelta(delta, result);
				return;
			case IJavaElement.COMPILATION_UNIT:
				// Not the primary compilation unit. Ignore it
				if (!JavaModelUtil.isPrimary((ICompilationUnit) elem)) {
					return;
				}

				if (isChanged &&  ((flags & IJavaElementDelta.F_CONTENT) != 0 || (flags & IJavaElementDelta.F_FINE_GRAINED) != 0)) {
					if (delta.getAffectedChildren().length == 0)
						return;

					result.add(elem);
				}
				return;
			default:
				// fields, methods, imports ect
				return;
		}
	}

}

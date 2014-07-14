/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.compare;

import java.util.HashSet;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.ToolBar;

import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareViewerPane;
import org.eclipse.compare.IContentChangeNotifier;
import org.eclipse.compare.IResourceProvider;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.ICompareInput;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.StructureDiffViewer;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;


// XXX: StructuredDiffViewer should allow subclassing, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=258907
class JavaStructureDiffViewer extends StructureDiffViewer implements IElementChangedListener {

	/**
	 * Toggles a boolean property of an <code>CompareConfiguration</code>.
	 */
	static class ChangePropertyAction extends Action {

		private CompareConfiguration fCompareConfiguration;
		private String fPropertyKey;
		private ResourceBundle fBundle;
		private String fPrefix;


		public ChangePropertyAction(ResourceBundle bundle, CompareConfiguration cc, String rkey, String pkey) {
			fPropertyKey= pkey;
			fBundle= bundle;
			fPrefix= rkey;
			JavaCompareUtilities.initAction(this, fBundle, fPrefix);
			setCompareConfiguration(cc);
		}

		@Override
		public void run() {
			boolean b= !JavaCompareUtilities.getBoolean(fCompareConfiguration, fPropertyKey, false);
			setChecked(b);
			if (fCompareConfiguration != null)
				fCompareConfiguration.setProperty(fPropertyKey, new Boolean(b));
		}

		@Override
		public void setChecked(boolean state) {
			super.setChecked(state);
			JavaCompareUtilities.initToggleAction(this, fBundle, fPrefix, state);
		}

		public void setCompareConfiguration(CompareConfiguration cc) {
			fCompareConfiguration= cc;
			setChecked(JavaCompareUtilities.getBoolean(fCompareConfiguration, fPropertyKey, false));
		}
	}

	private static final String SMART= "SMART"; //$NON-NLS-1$

	private ActionContributionItem fSmartActionItem;
	private JavaStructureCreator fStructureCreator;
	private boolean fThreeWay;

	public JavaStructureDiffViewer(Composite parent, CompareConfiguration configuration) {
		super(parent, configuration);
		fStructureCreator= new JavaStructureCreator();
		setStructureCreator(fStructureCreator);
		JavaCore.addElementChangedListener(this);
	}

	/**
	 * Overridden to find and expand the first class.
	 */
	@Override
	protected void initialSelection() {
		Object firstClass= null;
		Object o= getRoot();
		if (o != null) {
			Object[] children= getSortedChildren(o);
			if (children != null && children.length > 0) {
				for (int i= 0; i < children.length; i++) {
					o= children[i];
					Object[] sortedChildren= getSortedChildren(o);
					if (sortedChildren != null && sortedChildren.length > 0) {
						for (int j= 0; j < sortedChildren.length; j++) {
							o= sortedChildren[j];
							if (o instanceof DiffNode) {
								DiffNode dn= (DiffNode) o;
								ITypedElement e= dn.getId();
								if (e instanceof JavaNode) {
									JavaNode jn= (JavaNode) e;
									int tc= jn.getTypeCode();
									if (tc == JavaNode.CLASS || tc == JavaNode.INTERFACE) {
										firstClass= dn;
									}
								}
							}
						}
					}
				}
			}
		}
		if (firstClass != null)
			expandToLevel(firstClass, 1);
		else
			expandToLevel(2);
	}

	@Override
	protected void compareInputChanged(ICompareInput input) {

		fThreeWay= input != null ? input.getAncestor() != null
							     : false;
		setSmartButtonVisible(fThreeWay);

		if (input != null) {
			Map<String, String> compilerOptions= getCompilerOptions(input.getAncestor());
			if (compilerOptions == null)
				compilerOptions= getCompilerOptions(input.getLeft());
			if (compilerOptions == null)
				compilerOptions= getCompilerOptions(input.getRight());
			if (compilerOptions != null)
				fStructureCreator.setDefaultCompilerOptions(compilerOptions);
		}

		super.compareInputChanged(input);
	}

	private Map<String, String> getCompilerOptions(ITypedElement input) {
		IJavaElement element= findJavaElement(input);
		if (element != null) {
			IJavaProject javaProject= element.getJavaProject();
			if (javaProject != null)
				return javaProject.getOptions(true);
		}
		return null;
	}

	/*
	 * @see org.eclipse.compare.structuremergeviewer.StructureDiffViewer#handleDispose(org.eclipse.swt.events.DisposeEvent)
	 * @since 3.5
	 */
	@Override
	protected void handleDispose(DisposeEvent event) {
		JavaCore.removeElementChangedListener(this);
		super.handleDispose(event);
	}

	private IJavaElement findJavaElement(ITypedElement element) {
		if (element instanceof IResourceProvider) {
			IResource resource= ((IResourceProvider)element).getResource();
			if (resource != null) {
				return JavaCore.create(resource);
			}
		}
		return null;
	}

	/**
	 * Overriden to create a "smart" button in the viewer's pane control bar.
	 * <p>
	 * Clients can override this method and are free to decide whether they want to call
	 * the inherited method.
	 *
	 * @param toolBarManager the toolbar manager for which to add the buttons
	 */
	@Override
	protected void createToolItems(ToolBarManager toolBarManager) {

		super.createToolItems(toolBarManager);

		IAction a= new ChangePropertyAction(getBundle(), getCompareConfiguration(), "action.Smart.", SMART); //$NON-NLS-1$
		fSmartActionItem= new ActionContributionItem(a);
		fSmartActionItem.setVisible(fThreeWay);
		toolBarManager.appendToGroup("modes", fSmartActionItem); //$NON-NLS-1$
	}

	@Override
	protected void postDiffHook(Differencer differencer, IDiffContainer root, IProgressMonitor monitor) {
		if (fStructureCreator.canRewriteTree()) {
			boolean smart= JavaCompareUtilities.getBoolean(getCompareConfiguration(), SMART, false);
			if (smart && root != null)
				fStructureCreator.rewriteTree(differencer, root);
		}
	}

	/**
	 * Tracks property changes of the configuration object. Clients may override to track their own
	 * property changes. In this case they must call the inherited method.
	 * 
	 * @param event the property change event
	 */
	@Override
	protected void propertyChange(PropertyChangeEvent event) {
		if (event.getProperty().equals(SMART))
			diff();
		else
			super.propertyChange(event);
	}

	private void setSmartButtonVisible(boolean visible) {
		if (fSmartActionItem == null)
			return;
		Control c= getControl();
		if (c == null || c.isDisposed())
			return;

		fSmartActionItem.setVisible(visible);
		ToolBarManager tbm= CompareViewerPane.getToolBarManager(c.getParent());
		if (tbm != null) {
			tbm.update(true);
			ToolBar tb= tbm.getControl();
			if (!tb.isDisposed())
				tb.getParent().layout(true);
		}
	}

	/*
	 * @see org.eclipse.jdt.core.IElementChangedListener#elementChanged(org.eclipse.jdt.core.ElementChangedEvent)
	 * @since 3.5
	 */
	public void elementChanged(ElementChangedEvent event) {
		ITypedElement[] elements= findAffectedElement(event);
		for (int i= 0; i < elements.length; i++) {
			ITypedElement e= elements[i];
			if (e == null || !(e instanceof IContentChangeNotifier))
				continue;
			contentChanged((IContentChangeNotifier)e);
		}
	}

	/**
	 * Tells which elements of the comparison are affected by the change.
	 * 
	 * @param event element changed event
	 * @return array of typed elements affected by the event. May return an empty array.
	 * @since 3.5
	 */
	private ITypedElement[] findAffectedElement(ElementChangedEvent event) {
		Object input= getInput();
		if (!(input instanceof ICompareInput))
			return new ITypedElement[0];

		Set<ITypedElement> affectedElements= new HashSet<ITypedElement>();
		ICompareInput ci= (ICompareInput)input;
		IJavaElementDelta delta= event.getDelta();
		addAffectedElement(ci.getAncestor(), delta, affectedElements);
		addAffectedElement(ci.getLeft(), delta, affectedElements);
		addAffectedElement(ci.getRight(), delta, affectedElements);

		return affectedElements.toArray(new ITypedElement[affectedElements.size()]);
	}

	/**
	 * Tests whether the given element is affected by the change and if so, adds it to given set.
	 * 
	 * @param element the element to test
	 * @param delta the Java element delta
	 * @param affectedElements the set of affected elements
	 * @since 3.5
	 */
	private void addAffectedElement(ITypedElement element, IJavaElementDelta delta, Set<ITypedElement> affectedElements) {
		IJavaElement javaElement= findJavaElement(element);
		if (isEditable(javaElement) && findJavaElementDelta(javaElement, delta) != null)
			affectedElements.add(element);
	}
	
	/**
	 * Tells whether the given Java element can be edited.
	 * 
	 * @param javaElement the element to test
	 * @return <code>true</code> if the element can be edited, <code>false</code> otherwise
	 * @since 3.5
	 */
	private boolean isEditable(IJavaElement javaElement) {
		return (javaElement instanceof ICompilationUnit) && JavaModelUtil.isEditable((ICompilationUnit)javaElement);
	}

	/**
	 * Test whether the given delta represents a significant change.
	 * 
	 * @param cuDelta the Java element delta
	 * @return <code>true</code> if the delta represents a content change
	 * @since 3.5
	 */
	private boolean isContentChange(IJavaElementDelta cuDelta) {
		int flags= cuDelta.getFlags();
		return flags != IJavaElementDelta.F_AST_AFFECTED && (cuDelta.getKind() == IJavaElementDelta.CHANGED || (flags & IJavaElementDelta.F_CONTENT) != 0);
	}

	/**
	 * Check whether the given delta has been sent when saving this reconciler's editor.
	 * 
	 * @param cu the compilation unit
	 * @param delta the deltas
	 * @return <code>true</code> if the given delta
	 * @since 3.5
	 */
	private boolean canIgnore(IJavaElement cu, IJavaElementDelta[] delta) {
		if (delta.length != 1)
			return false;

		int flags= delta[0].getFlags();

		// become working copy
		if (flags == IJavaElementDelta.F_PRIMARY_WORKING_COPY)
			return true;

		// save
		if (flags == IJavaElementDelta.F_PRIMARY_RESOURCE && delta[0].getElement().equals(cu))
			return true;

		return canIgnore(cu, delta[0].getAffectedChildren());
	}



	/*
	 * This is a copy of the internal JavaOutlinePage.ElementChangedListener#findElement method.
	 * @see org.eclipse.jdt.internal.ui.javaeditor.JavaOutlinePage.ElementChangedListener#findElement(IJavaElement, IJavaElementDelta)
	 * @since 3.5
	 */
	protected IJavaElementDelta findJavaElementDelta(IJavaElement unit, IJavaElementDelta delta) {

		if (delta == null || unit == null)
			return null;

		IJavaElement element= delta.getElement();

		if (canIgnore(unit, delta.getAffectedChildren()))
			return null;

		if (unit.equals(element)) {
			if (isContentChange(delta)) {
				return delta;
			}
			return null;
		}


		if (element.getElementType() > IJavaElement.CLASS_FILE)
			return null;

		IJavaElementDelta[] children= delta.getAffectedChildren();
		if (children == null || children.length == 0)
			return null;

		for (int i= 0; i < children.length; i++) {
			IJavaElementDelta d= findJavaElementDelta(unit, children[i]);
			if (d != null)
				return d;
		}

		return null;
	}
}
/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 * 			(report 36180: Callers/Callees view)
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.swt.widgets.Display;

import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.ui.progress.DeferredTreeContentManager;

import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.callhierarchy.CallerMethodWrapper;
import org.eclipse.jdt.internal.corext.callhierarchy.MethodCall;
import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;
import org.eclipse.jdt.internal.corext.callhierarchy.RealCallers;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

public class CallHierarchyContentProvider implements ITreeContentProvider {

	/**
	 * A named preference that holds the types whose methods are by default expanded with
	 * constructors in the Call Hierarchy.
	 * <p>
	 * Value is of type <code>String</code>: semicolon separated list of fully qualified type names.
	 * <strong> It has been replaced in 3.6 by API:
	 * {@link PreferenceConstants#PREF_DEFAULT_EXPAND_WITH_CONSTRUCTORS_MEMBERS}</strong>
	 * </p>
	 * 
	 * @since 3.5
	 */
	public static final String OLD_PREF_DEFAULT_EXPAND_WITH_CONSTRUCTORS= "CallHierarchy.defaultExpandWithConstructors"; //$NON-NLS-1$

	private final static Object[] EMPTY_ARRAY= new Object[0];

    private DeferredTreeContentManager fManager;
    private CallHierarchyViewPart fPart;

    private class MethodWrapperRunnable implements IRunnableWithProgress {
        private MethodWrapper fMethodWrapper;
        private MethodWrapper[] fCalls= null;

        MethodWrapperRunnable(MethodWrapper methodWrapper) {
            fMethodWrapper= methodWrapper;
        }

        public void run(IProgressMonitor pm) {
        	fCalls= fMethodWrapper.getCalls(pm);
        }

        MethodWrapper[] getCalls() {
            if (fCalls != null) {
                return fCalls;
            }
            return new MethodWrapper[0];
        }
    }

    public CallHierarchyContentProvider(CallHierarchyViewPart part) {
        super();
        fPart= part;
    }

    /**
     * @see org.eclipse.jface.viewers.ITreeContentProvider#getChildren(java.lang.Object)
     */
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof TreeRoot) {
			TreeRoot dummyRoot= (TreeRoot)parentElement;
			return dummyRoot.getRoots();

		} else if (parentElement instanceof RealCallers) {
			MethodWrapper parentWrapper= ((RealCallers)parentElement).getParent();
			RealCallers element= ((RealCallers)parentElement);
			if (fManager != null) {
				Object[] children= fManager.getChildren(new DeferredMethodWrapper(this, element));
				if (children != null)
					return children;
			}
			return fetchChildren(parentWrapper);

		} else if (parentElement instanceof MethodWrapper) {
			MethodWrapper methodWrapper= ((MethodWrapper)parentElement);

			if (shouldStopTraversion(methodWrapper)) {
				return EMPTY_ARRAY;
			} else {
				if (parentElement instanceof CallerMethodWrapper) {
					CallerMethodWrapper caller= (CallerMethodWrapper)parentElement;
					ensureDefaultExpandWithConstructors(caller);
					if (caller.getExpandWithConstructors()) {
						IType type= caller.getMember().getDeclaringType();
						try {
							if (type.isAnonymous()) {
								IMember anonymousClass= type;
								MethodCall anonymousConstructor= new MethodCall(anonymousClass);
								CallerMethodWrapper anonymousWrapper= (CallerMethodWrapper)caller.createMethodWrapper(anonymousConstructor);
								return new Object[] { anonymousWrapper, new RealCallers(methodWrapper, caller.getMethodCall()) };
							} else {
								IMember[] constructors= JavaElementUtil.getAllConstructors(type);
								if (constructors.length == 0) {
									constructors= new IType[] { type }; // type stands for the default constructor
								}
								Object children[]= new Object[constructors.length + 1];
								for (int j= 0; j < constructors.length; j++) {
									MethodCall constructor= new MethodCall(constructors[j]);
									CallerMethodWrapper constructorWrapper= (CallerMethodWrapper)caller.createMethodWrapper(constructor);
									children[j]= constructorWrapper;
								}
								children[constructors.length]= new RealCallers(methodWrapper, caller.getMethodCall());
								return children;
							}
						} catch (JavaModelException e) {
							JavaPlugin.log(e);
							return null;
						}

					}
				}
				if (fManager != null) {
					Object[] children= fManager.getChildren(new DeferredMethodWrapper(this, methodWrapper));
					if (children != null)
						return children;
				}
				return fetchChildren(methodWrapper);
			}
		}
        return EMPTY_ARRAY;
    }

	/**
	 * Sets the default "expand with constructors" mode for the method wrapper. Does nothing if the
	 * mode has already been set.
	 * 
	 * 
	 * @param wrapper the caller method wrapper
	 * @since 3.5
	 */
	static void ensureDefaultExpandWithConstructors(CallerMethodWrapper wrapper) {

		if (!wrapper.isExpandWithConstructorsSet()) {
			if (CallHierarchyContentProvider.canExpandWithConstructors(wrapper)) {
				IMethod method= (IMethod)wrapper.getMember();
				IType type= method.getDeclaringType();
				try {
					boolean withConstructors= false;
					if (type != null) {
						boolean anonymousPref= PreferenceConstants.getPreferenceStore().getBoolean(PreferenceConstants.PREF_ANONYMOUS_EXPAND_WITH_CONSTRUCTORS);
						if (anonymousPref && type.isAnonymous()) {
							withConstructors= true;
						} else if (isInTheDefaultExpandWithConstructorList(method)) {
							withConstructors= true;
						}
					}
					wrapper.setExpandWithConstructors(withConstructors);
				} catch (JavaModelException e) {
					// ignore: expand mode will be off
				}
			}
		}

	}

	/**
	 * Checks whether given caller method wrapper can be expanded with constructors.
	 * 
	 * @param wrapper the caller method wrapper
	 * @return <code> true</code> if the wrapper can be expanded with constructors, <code>false</code> otherwise
	 * @since 3.5
	 */
	static boolean canExpandWithConstructors(CallerMethodWrapper wrapper) {
		IMember member= wrapper.getMember();
		if (!(member instanceof IMethod))
			return false;
		IMethod method= (IMethod)member;
		try {
			if (JdtFlags.isStatic(method) || method.isConstructor())
				return false;
		} catch (JavaModelException e) {
			return false; // don't try to work with inexistent elements
		}
		return true;
	}
	
	/**
	 * Checks if the method or its declaring type matches the pre-defined array of methods and types
	 * for default expand with constructors.
	 * 
	 * @param method the wrapped method
	 * @return <code>true</code> if method or type matches the pre-defined list, <code>false</code>
	 *         otherwise
	 * 
	 * @since 3.5
	 */
	static boolean isInTheDefaultExpandWithConstructorList(IMethod method) {
		String serializedMembers= PreferenceConstants.getPreferenceStore().getString(PreferenceConstants.PREF_DEFAULT_EXPAND_WITH_CONSTRUCTORS_MEMBERS);
		if (serializedMembers.length() == 0)
			return false;
		
		String[] defaultMemberPatterns= serializedMembers.split(";"); //$NON-NLS-1$
		
		String methodName= method.getElementName();
		IType declaringType= method.getDeclaringType();
		String declaringTypeName= declaringType.getFullyQualifiedName('.');
		String superClassName;
		String[] superInterfaceNames;
		try {
			superClassName= declaringType.getSuperclassName();
			if (superClassName != null) {
				superClassName= stripTypeArguments(superClassName);
			}
			superInterfaceNames= declaringType.getSuperInterfaceNames();
			for (int i= 0; i < superInterfaceNames.length; i++) {
				superInterfaceNames[i]= stripTypeArguments(superInterfaceNames[i]);
			}
		} catch (JavaModelException e) {
			return false;
		}
		
		for (int i= 0; i < defaultMemberPatterns.length; i++) {
			String defaultMemberPattern= defaultMemberPatterns[i];
			int pos= defaultMemberPattern.lastIndexOf('.');
			String defaultTypeName= defaultMemberPattern.substring(0, pos);
			String defaultMethodName= defaultMemberPattern.substring(pos + 1);
			
			if ("*".equals(defaultMethodName)) { //$NON-NLS-1$
				if (declaringTypeName.equals(defaultTypeName)) {
					return true;
				}
			} else {
				if (!methodName.equals(defaultMethodName)) {
					continue;
				}
				if (declaringTypeName.equals(defaultTypeName)) {
					return true;
				}
			}
			if (superClassName != null && JavaModelUtil.isMatchingName(superClassName, defaultTypeName)) {
				return true;
			}
			for (int j= 0; j < superInterfaceNames.length; j++) {
				String superInterfaceName= superInterfaceNames[j];
				if (JavaModelUtil.isMatchingName(superInterfaceName, defaultTypeName)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Strips type arguments from the given type name and returns only erased type name.
	 * 
	 * @param typeName the type name
	 * @return the erased type name
	 * 
	 * @since 3.6
	 */
	private static String stripTypeArguments(String typeName) {
		int pos= typeName.indexOf('<');
		if (pos != -1)
			return typeName.substring(0, pos);
		return typeName;
	}

	protected Object[] fetchChildren(final MethodWrapper methodWrapper) {
        IRunnableContext context= JavaPlugin.getActiveWorkbenchWindow();
        MethodWrapperRunnable runnable= new MethodWrapperRunnable(methodWrapper);
        try {
            context.run(true, true, runnable);
        } catch (InvocationTargetException e) {
            ExceptionHandler.handle(e, CallHierarchyMessages.CallHierarchyContentProvider_searchError_title, CallHierarchyMessages.CallHierarchyContentProvider_searchError_message);
            return EMPTY_ARRAY;
        } catch (InterruptedException e) {
        	final CallerMethodWrapper element= (CallerMethodWrapper)methodWrapper;
        	if (!isExpandWithConstructors(element)) {
        		Display.getDefault().asyncExec(new Runnable() {
        			public void run() {
    					collapseAndRefresh(element);
        			}
        		});
        	}
        }

        return runnable.getCalls();
    }


    /**
     * Returns whether the given element is an "Expand witch Constructors" node.
     * 
	 * @param element a method wrapped
	 * @return <code>true</code> iff the element is an "Expand witch Constructors" node
	 * @since 3.5
	 */
	static boolean isExpandWithConstructors(MethodWrapper element) {
		return element instanceof CallerMethodWrapper && ((CallerMethodWrapper)element).getExpandWithConstructors();
	}

	/**
     * Collapses and refreshes the given element when search has been canceled.
     * 
     * @param element the element on which search has been canceled and which has to be collapsed
     * @since 3.5
     */
    protected void collapseAndRefresh(MethodWrapper element) {
    	CallHierarchyViewer viewer= fPart.getViewer();
    	
		/* Problem case: The user expands the RealCallers node and then unchecks "Expand with Constructors"
		 * while the search for the real callers is still in progress.
		 * 
		 * In this scenario, the RealCallers is not even part of the current tree any more, since the
		 * ExpandWithConstructorsAction already toggled the flag and refreshed the tree.
    	 * 
    	 * But since setExpandedState(element, false) walks up the getParent() chain of the given element,
    	 * this causes the parent's children to be created, which would wrongly start a deferred search.
    	 * 
    	 * The fix is to do nothing when the RealCaller's parent is expandWithConstructors.
		 */
    	boolean elementStays= true;
    	if (element instanceof RealCallers) {
    		elementStays= isExpandWithConstructors(element.getParent());
    	}
		if (elementStays) {
    		viewer.setExpandedState(element, false);
    	}
    	
    	viewer.refresh(element);
    }

	/**
     * Returns the call hierarchy view part.
	 * 
	 * @return the call hierarchy view part
	 * @since 3.5
	 */
    public CallHierarchyViewPart getViewPart() {
    	return fPart;
    }

	private boolean shouldStopTraversion(MethodWrapper methodWrapper) {
        return (methodWrapper.getLevel() > CallHierarchyUI.getDefault().getMaxCallDepth()) || methodWrapper.isRecursive();
    }

    /**
     * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
     */
    public Object[] getElements(Object inputElement) {
        return getChildren(inputElement);
    }

    /**
     * @see org.eclipse.jface.viewers.ITreeContentProvider#getParent(java.lang.Object)
     */
    public Object getParent(Object element) {
        if (element instanceof MethodWrapper) {
            return ((MethodWrapper) element).getParent();
        }

        return null;
    }

    /**
     * @see org.eclipse.jface.viewers.IContentProvider#dispose()
     */
    public void dispose() {
        // Nothing to dispose
    }

    /**
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#hasChildren(java.lang.Object)
	 */
	public boolean hasChildren(Object element) {
		if (element == TreeRoot.EMPTY_ROOT || element == TreeTermination.SEARCH_CANCELED) {
			return false;
		}

		// Only certain members can have subelements, so there's no need to fool the
		// user into believing that there is more
		if (element instanceof MethodWrapper) {
			MethodWrapper methodWrapper= (MethodWrapper) element;
			if (! methodWrapper.canHaveChildren()) {
				return false;
			}
			if (shouldStopTraversion(methodWrapper)) {
				return false;
			}
			return true;
		} else if (element instanceof TreeRoot) {
			return true;
		} else if (element instanceof DeferredMethodWrapper) {
			// Err on the safe side by returning true even though
			// we don't know for sure that there are children.
			return true;
		}

		return false; // the "Update ..." placeholder has no children
	}

    /**
	 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer,
	 *      java.lang.Object, java.lang.Object)
	 */
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    	if (oldInput instanceof TreeRoot) {
    		MethodWrapper[] roots = ((TreeRoot) oldInput).getRoots();
   			cancelJobs(roots);
    	}
        if (viewer instanceof AbstractTreeViewer) {
            fManager = new DeferredTreeContentManager((AbstractTreeViewer) viewer, fPart.getSite());
        }
    }

    /**
     * Cancel all current jobs.
     * @param wrappers the parents to cancel jobs for
     */
    void cancelJobs(MethodWrapper[] wrappers) {
        if (fManager != null && wrappers != null) {
        	for (int i= 0; i < wrappers.length; i++) {
				MethodWrapper wrapper= wrappers[i];
				fManager.cancel(wrapper);
			}
            if (fPart != null) {
                fPart.setCancelEnabled(false);
            }
        }
    }

    /**
     *
     */
    public void doneFetching() {
        if (fPart != null) {
            fPart.setCancelEnabled(false);
        }
    }

    /**
     *
     */
    public void startFetching() {
        if (fPart != null) {
            fPart.setCancelEnabled(true);
        }
    }
}

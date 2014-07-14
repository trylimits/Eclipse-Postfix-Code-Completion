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
package org.eclipse.jdt.internal.ui.refactoring.nls;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.swt.widgets.Display;

import org.eclipse.jface.window.Window;

import org.eclipse.ui.dialogs.ElementListSelectionDialog;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;


public class PackageBrowseAdapter implements IStringButtonAdapter {

    PackageSelectionDialogButtonField  fReceiver;
    private ICompilationUnit fCu;

    public PackageBrowseAdapter(ICompilationUnit unit) {
        fCu = unit;
    }

    public void setReceiver(PackageSelectionDialogButtonField  receiver) {
       fReceiver = receiver;
    }

	public void changeControlPressed(DialogField field) {
		ElementListSelectionDialog dialog= new ElementListSelectionDialog(
			Display.getCurrent().getActiveShell(), new JavaElementLabelProvider());
        dialog.setIgnoreCase(false);
        dialog.setTitle(NLSUIMessages.PackageBrowseAdapter_package_selection);
        dialog.setMessage(NLSUIMessages.PackageBrowseAdapter_choose_package);
        dialog.setElements(createPackageListInput(fCu, null));
        if (dialog.open() == Window.OK) {
        	IPackageFragment selectedPackage= (IPackageFragment)dialog.getFirstResult();
        	if (selectedPackage != null) {
        		fReceiver.setPackage(selectedPackage);
        	}
        }
	}
	public static Object[] createPackageListInput(ICompilationUnit cu, String elementNameMatch){
		try{
			IJavaProject project= cu.getJavaProject();
			IPackageFragmentRoot[] roots= project.getPackageFragmentRoots();
			List<IPackageFragment> result= new ArrayList<IPackageFragment>();
			HashMap<String, Object> entered =new HashMap<String, Object>();
			for (int i= 0; i < roots.length; i++){
				if (canAddPackageRoot(roots[i])){
					getValidPackages(roots[i], result, entered, elementNameMatch);
				}
			}
			return result.toArray();
		} catch (JavaModelException e){
			JavaPlugin.log(e);
			return new Object[0];
		}
	}

    static boolean canAddPackageRoot(IPackageFragmentRoot root) throws JavaModelException{
    	if (! root.exists())
    		return false;
    	if (root.isArchive())
    		return false;
    	if (root.isExternal())
    		return false;
    	if (root.isReadOnly())
    		return false;
    	if (! root.isStructureKnown())
    		return false;
    	return true;
    }

	static void getValidPackages(IPackageFragmentRoot root, List<IPackageFragment> result, HashMap<String, Object> entered, String elementNameMatch) throws JavaModelException {
		IJavaElement[] children= null;
		try {
			children= root.getChildren();
		} catch (JavaModelException e){
			return;
		}
		for (int i= 0; i < children.length; i++){
            if (children[i] instanceof IPackageFragment) {
                IPackageFragment packageFragment = (IPackageFragment)children[i];
                String packageName = packageFragment.getElementName();

                if ((entered != null) && (entered.containsKey(packageName)) == true) {
                    continue;
                }

			    if (canAddPackage(packageFragment)) {
			        if ((elementNameMatch == null) || (elementNameMatch.equals(packageName))) {
			            result.add(packageFragment);
			            if (entered != null) {
			                entered.put(packageName, null);
			            }
			        }
			    }
            }
		}
	}

    static boolean canAddPackage(IPackageFragment p) throws JavaModelException{
    	if (! p.exists())
    		return false;
    	if (p.isReadOnly())
    		return false;
    	if (! p.isStructureKnown())
    		return false;
    	return true;
    }

    public static List<IPackageFragment> searchAllPackages(IJavaProject project, String matcher) {
		try{
			IPackageFragmentRoot[] roots= project.getPackageFragmentRoots();
			List<IPackageFragment> result= new ArrayList<IPackageFragment>();
			for (int i= 0; i < roots.length; i++){
				if (canAddPackageRoot(roots[i])){
					getValidPackages(roots[i], result, null, matcher);
				}
			}
			return result;
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
			return new ArrayList<IPackageFragment>(0);
		}
    }
}

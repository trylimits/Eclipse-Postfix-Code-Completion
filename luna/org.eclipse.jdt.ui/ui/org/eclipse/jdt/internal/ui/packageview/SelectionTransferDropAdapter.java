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
package org.eclipse.jdt.internal.ui.packageview;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;

import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.resources.IResource;

import org.eclipse.jface.util.TransferDropTargetListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;

import org.eclipse.ui.views.navigator.LocalSelectionTransfer;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.reorg.IReorgPolicy.ICopyPolicy;
import org.eclipse.jdt.internal.corext.refactoring.reorg.IReorgPolicy.IMovePolicy;
import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaCopyProcessor;
import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaMoveProcessor;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgDestinationFactory;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgPolicyFactory;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgUtils;

import org.eclipse.jdt.internal.ui.dnd.JdtViewerDropAdapter;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.reorg.ReorgCopyStarter;
import org.eclipse.jdt.internal.ui.refactoring.reorg.ReorgMoveStarter;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

public class SelectionTransferDropAdapter extends JdtViewerDropAdapter implements TransferDropTargetListener {

	private List<?> fElements;
	private JavaMoveProcessor fMoveProcessor;
	private int fCanMoveElements;
	private JavaCopyProcessor fCopyProcessor;
	private int fCanCopyElements;
	private ISelection fSelection;

	public SelectionTransferDropAdapter(StructuredViewer viewer) {
		super(viewer);

		setScrollEnabled(true);
		setExpandEnabled(true);
		setSelectionFeedbackEnabled(false);
		setFeedbackEnabled(false);
	}

	//---- TransferDropTargetListener interface ---------------------------------------

	/**
	 * {@inheritDoc}
	 */
	public Transfer getTransfer() {
		return LocalSelectionTransfer.getInstance();
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isEnabled(DropTargetEvent event) {
		Object target= event.item != null ? event.item.getData() : null;
		if (target == null)
			return false;
		return target instanceof IJavaElement || target instanceof IResource;
	}

	//---- Actual DND -----------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dragEnter(DropTargetEvent event) {
		clear();
		super.dragEnter(event);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dragLeave(DropTargetEvent event) {
		clear();
		super.dragLeave(event);
	}

	private void clear() {
		setSelectionFeedbackEnabled(false);
		fElements= null;
		fSelection= null;
		fMoveProcessor= null;
		fCanMoveElements= 0;
		fCopyProcessor= null;
		fCanCopyElements= 0;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean validateDrop(Object target, int operation, TransferData transferType) {
		return determineOperation(target, operation, transferType, DND.DROP_MOVE | DND.DROP_LINK | DND.DROP_COPY) != DND.DROP_NONE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected int determineOperation(Object target, int operation, TransferData transferType, int operations) {
		int result= internalDetermineOperation(target, operation, operations);

		if (result == DND.DROP_NONE) {
			setSelectionFeedbackEnabled(false);
		} else {
			setSelectionFeedbackEnabled(true);
		}

		return result;
	}

	private int internalDetermineOperation(Object target, int operation, int operations) {

		initializeSelection();

		if (target == null)
			return DND.DROP_NONE;

		//Do not allow to drop on itself, bug 14228
		if (getCurrentLocation() == LOCATION_ON) {
			IJavaElement[] javaElements= ReorgUtils.getJavaElements(fElements);
			if (contains(javaElements, target))
				return DND.DROP_NONE;

			IResource[] resources= ReorgUtils.getResources(fElements);
			if (contains(resources, target))
				return DND.DROP_NONE;
		}

		try {
			switch(operation) {
				case DND.DROP_DEFAULT:
					return handleValidateDefault(target, operations);
				case DND.DROP_COPY:
					return handleValidateCopy(target);
				case DND.DROP_MOVE:
					return handleValidateMove(target);
			}
		} catch (JavaModelException e){
			ExceptionHandler.handle(e, PackagesMessages.SelectionTransferDropAdapter_error_title, PackagesMessages.SelectionTransferDropAdapter_error_message);
		}

		return DND.DROP_NONE;
	}

	private boolean contains(IResource[] resources, Object target) {
		for (int i= 0; i < resources.length; i++) {
			if (resources[i].equals(target))
				return true;
		}

		return false;
	}

	private boolean contains(IJavaElement[] elements, Object target) {
		for (int i= 0; i < elements.length; i++) {
			if (elements[i].equals(target))
				return true;
		}

		return false;
	}

	protected void initializeSelection(){
		if (fElements != null)
			return;
		ISelection s= LocalSelectionTransfer.getInstance().getSelection();
		if (!(s instanceof IStructuredSelection)) {
			fSelection= StructuredSelection.EMPTY;
			fElements= Collections.EMPTY_LIST;
			return;
		}
		fSelection= s;
		fElements= ((IStructuredSelection)s).toList();
	}

	protected ISelection getSelection(){
		return fSelection;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean performDrop(Object data) {
		try{
			switch(getCurrentOperation()) {
				case DND.DROP_MOVE:
					return handleDropMove(getCurrentTarget());
				case DND.DROP_COPY:
					return handleDropCopy(getCurrentTarget());
			}
		} catch (JavaModelException e){
			ExceptionHandler.handle(e, PackagesMessages.SelectionTransferDropAdapter_error_title, PackagesMessages.SelectionTransferDropAdapter_error_message);
			return false;
		} catch(InvocationTargetException e) {
			ExceptionHandler.handle(e, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
			return false;
		} catch (InterruptedException e) {
			return false;
		}

		return true;

	}

	private int handleValidateDefault(Object target, int operations) throws JavaModelException{
		if ((operations & DND.DROP_MOVE) != 0) {
			int result= handleValidateMove(target);
			if (result != DND.DROP_NONE)
				return result;
		}

		return handleValidateCopy(target);
	}

	private int handleValidateMove(Object target) throws JavaModelException{
		if (fMoveProcessor == null) {
			IMovePolicy policy= ReorgPolicyFactory.createMovePolicy(ReorgUtils.getResources(fElements), ReorgUtils.getJavaElements(fElements));
			if (policy.canEnable())
				fMoveProcessor= new JavaMoveProcessor(policy);
		}

		if (!canMoveElements())
			return DND.DROP_NONE;

		if (fMoveProcessor == null)
			return DND.DROP_NONE;

		RefactoringStatus moveStatus= fMoveProcessor.setDestination(ReorgDestinationFactory.createDestination(target, getCurrentLocation()));
		if (moveStatus.hasError())
			return DND.DROP_NONE;

		return DND.DROP_MOVE;
	}

	private boolean canMoveElements() {
		if (fCanMoveElements == 0) {
			fCanMoveElements= 2;
			if (fMoveProcessor == null)
				fCanMoveElements= 1;
		}
		return fCanMoveElements == 2;
	}

	private boolean handleDropMove(final Object target) throws JavaModelException, InvocationTargetException, InterruptedException{
		IJavaElement[] javaElements= ReorgUtils.getJavaElements(fElements);
		IResource[] resources= ReorgUtils.getResources(fElements);
		ReorgMoveStarter starter= ReorgMoveStarter.create(javaElements, resources, ReorgDestinationFactory.createDestination(target, getCurrentLocation()));

		if (starter != null)
			return starter.run(getShell());
		return false;
	}

	private int handleValidateCopy(Object target) throws JavaModelException{

		if (fCopyProcessor == null) {
			final ICopyPolicy policy= ReorgPolicyFactory.createCopyPolicy(ReorgUtils.getResources(fElements), ReorgUtils.getJavaElements(fElements));
			fCopyProcessor= policy.canEnable() ? new JavaCopyProcessor(policy) : null;
		}

		if (!canCopyElements())
			return DND.DROP_NONE;

		if (fCopyProcessor == null)
			return DND.DROP_NONE;

		if (!fCopyProcessor.setDestination(ReorgDestinationFactory.createDestination(target, getCurrentLocation())).isOK())
			return DND.DROP_NONE;

		return DND.DROP_COPY;
	}

	private boolean canCopyElements() {
		if (fCanCopyElements == 0) {
			fCanCopyElements= 2;
			if (fCopyProcessor == null)
				fCanCopyElements= 1;
		}
		return fCanCopyElements == 2;
	}

	private boolean handleDropCopy(final Object target) throws JavaModelException, InvocationTargetException, InterruptedException{
		IJavaElement[] javaElements= ReorgUtils.getJavaElements(fElements);
		IResource[] resources= ReorgUtils.getResources(fElements);
		ReorgCopyStarter starter= ReorgCopyStarter.create(javaElements, resources, ReorgDestinationFactory.createDestination(target, getCurrentLocation()));

		if (starter != null) {
			starter.run(getShell());
			return true;
		}
		return false;
	}

	private Shell getShell() {
		return getViewer().getControl().getShell();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected int getCurrentLocation() {
		if (getFeedbackEnabled()) {
			return super.getCurrentLocation();
		} else {
			return LOCATION_ON;
		}
	}
}

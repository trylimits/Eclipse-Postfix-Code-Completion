/*******************************************************************************
 * Copyright (c) 2005, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.CommandManager;
import org.eclipse.core.commands.IParameter;
import org.eclipse.core.commands.Parameterization;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.core.commands.contexts.ContextManager;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.bindings.BindingManager;
import org.eclipse.jface.bindings.Scheme;
import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ViewerComparator;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;

import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.text.java.CompletionProposalCategory;
import org.eclipse.jdt.internal.ui.text.java.CompletionProposalComputerRegistry;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;


/**
 *
 * @since 3.2
 */
final class CodeAssistAdvancedConfigurationBlock extends OptionsConfigurationBlock {

	private static final Key PREF_EXCLUDED_CATEGORIES= getJDTUIKey(PreferenceConstants.CODEASSIST_EXCLUDED_CATEGORIES);
	private static final Key PREF_CATEGORY_ORDER= getJDTUIKey(PreferenceConstants.CODEASSIST_CATEGORY_ORDER);
	private static final Key PREF_CODEASSIST_TIMEOUT_FOR_PARAMETER_NAME_FROM_ATTACHED_JAVADOC= getJDTCoreKey(JavaCore.TIMEOUT_FOR_PARAMETER_NAME_FROM_ATTACHED_JAVADOC);

	private static Key[] getAllKeys() {
		return new Key[] {
				PREF_EXCLUDED_CATEGORIES,
				PREF_CATEGORY_ORDER,
				PREF_CODEASSIST_TIMEOUT_FOR_PARAMETER_NAME_FROM_ATTACHED_JAVADOC
		};
	}

	private final class DefaultTableLabelProvider extends LabelProvider implements ITableLabelProvider {

		/*
		 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnImage(java.lang.Object, int)
		 */
		public Image getColumnImage(Object element, int columnIndex) {
			if (columnIndex == 0)
				return ((ModelElement) element).getImage();
			return null;
		}

		/*
		 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnText(java.lang.Object, int)
		 */
		public String getColumnText(Object element, int columnIndex) {
			switch (columnIndex) {
	            case 0:
	            	return ((ModelElement) element).getName();
	            case 1:
	            	return ((ModelElement) element).getKeybindingAsString();
	            default:
	            	Assert.isTrue(false);
	            	return null;
            }
		}

		/*
		 * @see org.eclipse.jface.viewers.LabelProvider#getText(java.lang.Object)
		 */
		@Override
		public String getText(Object element) {
		    return getColumnText(element, 0); // needed to make the sorter work
		}
	}

	private final class SeparateTableLabelProvider extends LabelProvider implements ITableLabelProvider {

		/*
		 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnImage(java.lang.Object, int)
		 */
		public Image getColumnImage(Object element, int columnIndex) {
			if (columnIndex == 0)
				return ((ModelElement) element).getImage();
			return null;
		}

		/*
		 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnText(java.lang.Object, int)
		 */
		public String getColumnText(Object element, int columnIndex) {
			switch (columnIndex) {
				case 0:
					return ((ModelElement) element).getName();
				default:
					Assert.isTrue(false);
				return null;
			}
		}
	}

	private final Comparator<ModelElement> fCategoryComparator= new Comparator<ModelElement>() {
		private int getRank(ModelElement o) {
			return o.getRank();
		}

		public int compare(ModelElement o1, ModelElement o2) {
			return getRank(o1) - getRank(o2);
		}
	};

	private final class PreferenceModel {
		private static final int LIMIT= 65535;
		private static final String COLON= ":"; //$NON-NLS-1$
		private static final String SEPARATOR= "\0"; //$NON-NLS-1$

		private final List<ModelElement> fElements;
		/**
		 * The read-only list of elements.
		 */
		final List<ModelElement> elements;

		public PreferenceModel(CompletionProposalComputerRegistry registry) {
			List<CompletionProposalCategory> categories= registry.getProposalCategories();
			fElements= new ArrayList<ModelElement>();
			for (Iterator<CompletionProposalCategory> it= categories.iterator(); it.hasNext();) {
				CompletionProposalCategory category= it.next();
				if (category.hasComputers()) {
					fElements.add(new ModelElement(category, this));
				}
			}
			Collections.sort(fElements, fCategoryComparator);
			elements= Collections.unmodifiableList(fElements);
		}

        public void moveUp(ModelElement category) {
        	int index= fElements.indexOf(category);
			if (index > 0) {
				ModelElement item= fElements.remove(index);
				fElements.add(index - 1, item);
				writeOrderPreference(null, false);
			}
        }

        public void moveDown(ModelElement category) {
        	int index= fElements.indexOf(category);
			if (index < fElements.size() - 1) {
				ModelElement item= fElements.remove(index);
				fElements.add(index + 1, item);
				writeOrderPreference(null, false);
			}
        }

    	private void writeInclusionPreference(ModelElement changed, boolean isInDefaultCategory) {
    		StringBuffer buf= new StringBuffer();
    		for (Iterator<ModelElement> it= fElements.iterator(); it.hasNext();) {
    			ModelElement item= it.next();
    			boolean included= changed == item ? isInDefaultCategory : item.isInDefaultCategory();
    			if (!included)
    				buf.append(item.getId() + SEPARATOR);
    		}

    		String newValue= buf.toString();
    		String oldValue= setValue(PREF_EXCLUDED_CATEGORIES, newValue);
    		validateSettings(PREF_EXCLUDED_CATEGORIES, oldValue, newValue);
    	}

    	private void writeOrderPreference(ModelElement changed, boolean isSeparate) {
    		StringBuffer buf= new StringBuffer();
    		int i= 0;
    		for (Iterator<ModelElement> it= fElements.iterator(); it.hasNext(); i++) {
    			ModelElement item= it.next();
    			boolean separate= changed == item ? isSeparate : item.isSeparateCommand();
    			int rank= separate ? i : i + LIMIT;
    			buf.append(item.getId() + COLON + rank + SEPARATOR);
    		}

    		String newValue= buf.toString();
    		String oldValue= setValue(PREF_CATEGORY_ORDER, newValue);
    		validateSettings(PREF_CATEGORY_ORDER, oldValue, newValue);
    	}


    	private boolean readInclusionPreference(CompletionProposalCategory cat) {
    		String[] ids= getTokens(getValue(PREF_EXCLUDED_CATEGORIES), SEPARATOR);
    		for (int i= 0; i < ids.length; i++) {
    			if (ids[i].equals(cat.getId()))
    				return false;
    		}
    		return true;
    	}

    	private int readOrderPreference(CompletionProposalCategory cat) {
    		String[] sortOrderIds= getTokens(getValue(PREF_CATEGORY_ORDER), SEPARATOR);
    		for (int i= 0; i < sortOrderIds.length; i++) {
    			String[] idAndRank= getTokens(sortOrderIds[i], COLON);
    			if (idAndRank[0].equals(cat.getId()))
    				return Integer.parseInt(idAndRank[1]);
    		}
			return LIMIT - 1;
    	}

        public void update() {
			Collections.sort(fElements, fCategoryComparator);
        }
	}

	private final class ModelElement {
		private final CompletionProposalCategory fCategory;
		private final Command fCommand;
		private final IParameter fParam;
		private final PreferenceModel fPreferenceModel;

		ModelElement(CompletionProposalCategory category, PreferenceModel model) {
			fCategory= category;
			ICommandService commandSvc= (ICommandService) PlatformUI.getWorkbench().getAdapter(ICommandService.class);
			fCommand= commandSvc.getCommand("org.eclipse.jdt.ui.specific_content_assist.command"); //$NON-NLS-1$
			IParameter type;
			try {
				type= fCommand.getParameters()[0];
			} catch (NotDefinedException x) {
				Assert.isTrue(false);
				type= null;
			}
			fParam= type;
			fPreferenceModel= model;
		}
		Image getImage() {
			return CodeAssistAdvancedConfigurationBlock.this.getImage(fCategory.getImageDescriptor());
		}
		String getName() {
			return fCategory.getDisplayName();
		}
		String getKeybindingAsString() {
			final Parameterization[] params= { new Parameterization(fParam, fCategory.getId()) };
			final ParameterizedCommand pCmd= new ParameterizedCommand(fCommand, params);
			String key= getKeyboardShortcut(pCmd);
			return key;
		}
		boolean isInDefaultCategory() {
			return fPreferenceModel.readInclusionPreference(fCategory);
		}
		void setInDefaultCategory(boolean included) {
			if (included != isInDefaultCategory())
				fPreferenceModel.writeInclusionPreference(this, included);
		}
		String getId() {
			return fCategory.getId();
		}
		int getRank() {
			int rank= getInternalRank();
			if (rank > PreferenceModel.LIMIT)
				return rank - PreferenceModel.LIMIT;
			return rank;
		}
		void moveUp() {
			fPreferenceModel.moveUp(this);
		}
		void moveDown() {
			fPreferenceModel.moveDown(this);
		}
		private int getInternalRank() {
			return fPreferenceModel.readOrderPreference(fCategory);
		}
		boolean isSeparateCommand() {
			return getInternalRank() < PreferenceModel.LIMIT;
		}

		void setSeparateCommand(boolean separate) {
			if (separate != isSeparateCommand())
				fPreferenceModel.writeOrderPreference(this, separate);
		}

		void update() {
			fCategory.setIncluded(isInDefaultCategory());
			int rank= getInternalRank();
			fCategory.setSortOrder(rank);
			fCategory.setSeparateCommand(rank < PreferenceModel.LIMIT);
		}
	}

	/** element type: {@link ModelElement}. */
	private final PreferenceModel fModel;
	private final Map<ImageDescriptor, Image> fImages= new HashMap<ImageDescriptor, Image>();

	private CheckboxTableViewer fDefaultViewer;
	private CheckboxTableViewer fSeparateViewer;
	private Button fUpButton;
	private Button fDownButton;

	CodeAssistAdvancedConfigurationBlock(IStatusChangeListener statusListener, IWorkbenchPreferenceContainer container) {
		super(statusListener, null, getAllKeys(), container);
		fModel= new PreferenceModel(CompletionProposalComputerRegistry.getDefault());
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#createContents(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	protected Control createContents(Composite parent) {

		ScrolledPageContent scrolled= new ScrolledPageContent(parent, SWT.H_SCROLL | SWT.V_SCROLL);

		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);

		Composite composite= new Composite(scrolled, SWT.NONE);
		int columns= 2;
		GridLayout layout= new GridLayout(columns, false);
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		composite.setLayout(layout);


		createDefaultLabel(composite, columns);
		createDefaultViewer(composite, columns);
		createKeysLink(composite, columns);

		createFiller(composite, columns);

		createSeparateLabel(composite, columns);
        createSeparateSection(composite);

        createFiller(composite, columns);

		createParameterTimeoutControl(composite, columns);

		updateControls();
		if (fModel.elements.size() > 0) {
			fDefaultViewer.getTable().select(0);
			fSeparateViewer.getTable().select(0);
			handleTableSelection();
		}

		scrolled.setContent(composite);
		scrolled.setMinSize(composite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		return scrolled;
	}

	private void createDefaultLabel(Composite composite, int h_span) {
	    final ICommandService commandSvc= (ICommandService) PlatformUI.getWorkbench().getAdapter(ICommandService.class);
		final Command command= commandSvc.getCommand(ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS);
		ParameterizedCommand pCmd= new ParameterizedCommand(command, null);
		String key= getKeyboardShortcut(pCmd);
		if (key == null)
			key= PreferencesMessages.CodeAssistAdvancedConfigurationBlock_no_shortcut;

		PixelConverter pixelConverter= new PixelConverter(composite);
		int width= pixelConverter.convertWidthInCharsToPixels(40);

		Label label= new Label(composite, SWT.NONE | SWT.WRAP);
		label.setText(Messages.format(PreferencesMessages.CodeAssistAdvancedConfigurationBlock_page_description, new Object[] { key }));
		GridData gd= new GridData(GridData.FILL, GridData.FILL, true, false, h_span, 1);
		gd.widthHint= width;
		label.setLayoutData(gd);

		createFiller(composite, h_span);

		label= new Label(composite, SWT.NONE | SWT.WRAP);
		label.setText(PreferencesMessages.CodeAssistAdvancedConfigurationBlock_default_table_description);
		gd= new GridData(GridData.FILL, GridData.FILL, true, false, h_span, 1);
		gd.widthHint= width;
		label.setLayoutData(gd);
    }

	private void createDefaultViewer(Composite composite, int h_span) {
		fDefaultViewer= CheckboxTableViewer.newCheckList(composite, SWT.SINGLE | SWT.BORDER);
		Table table= fDefaultViewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(false);
		table.setLayoutData(new GridData(GridData.FILL, GridData.BEGINNING, false, false, h_span, 1));

		TableColumn nameColumn= new TableColumn(table, SWT.NONE);
		nameColumn.setText(PreferencesMessages.CodeAssistAdvancedConfigurationBlock_default_table_category_column_title);
		nameColumn.setResizable(false);
		TableColumn keyColumn= new TableColumn(table, SWT.NONE);
		keyColumn.setText(PreferencesMessages.CodeAssistAdvancedConfigurationBlock_default_table_keybinding_column_title);
		keyColumn.setResizable(false);

		fDefaultViewer.addCheckStateListener(new ICheckStateListener() {
			public void checkStateChanged(CheckStateChangedEvent event) {
				boolean checked= event.getChecked();
				ModelElement element= (ModelElement) event.getElement();
				element.setInDefaultCategory(checked);
			}
		});

		fDefaultViewer.setContentProvider(new ArrayContentProvider());

		DefaultTableLabelProvider labelProvider= new DefaultTableLabelProvider();
		fDefaultViewer.setLabelProvider(labelProvider);
		fDefaultViewer.setInput(fModel.elements);
		fDefaultViewer.setComparator(new ViewerComparator()); // sort alphabetically

		final int ICON_AND_CHECKBOX_WITH= 50;
		final int HEADER_MARGIN= 20;
		int minNameWidth= computeWidth(table, nameColumn.getText()) + HEADER_MARGIN;
		int minKeyWidth= computeWidth(table, keyColumn.getText()) + HEADER_MARGIN;
		for (int i= 0; i < fModel.elements.size(); i++) {
			minNameWidth= Math.max(minNameWidth, computeWidth(table, labelProvider.getColumnText(fModel.elements.get(i), 0)) + ICON_AND_CHECKBOX_WITH);
			minKeyWidth= Math.max(minKeyWidth, computeWidth(table, labelProvider.getColumnText(fModel.elements.get(i), 1)));
		}

		nameColumn.setWidth(minNameWidth);
		keyColumn.setWidth(minKeyWidth);
	}

	private void createKeysLink(Composite composite, int h_span) {
	    Link link= new Link(composite, SWT.NONE | SWT.WRAP);
		link.setText(PreferencesMessages.CodeAssistAdvancedConfigurationBlock_key_binding_hint);
		link.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				PreferencesUtil.createPreferenceDialogOn(getShell(), e.text, null, null);
			}
		});

		PixelConverter pixelConverter= new PixelConverter(composite);
		int width= pixelConverter.convertWidthInCharsToPixels(40);

		// limit the size of the Link as it would take all it can get
		GridData gd= new GridData(GridData.FILL, GridData.FILL, false, false, h_span, 1);
		gd.widthHint= width;
		link.setLayoutData(gd);
    }

	private void createFiller(Composite composite, int h_span) {
	    Label filler= new Label(composite, SWT.NONE);
		filler.setVisible(false);
		filler.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, h_span, 1));
    }

	private void createSeparateLabel(Composite composite, int h_span) {
		PixelConverter pixelConverter= new PixelConverter(composite);
		int width= pixelConverter.convertWidthInCharsToPixels(40);

		Label label= new Label(composite, SWT.NONE | SWT.WRAP);
		label.setText(PreferencesMessages.CodeAssistAdvancedConfigurationBlock_separate_table_description);
		GridData gd= new GridData(GridData.FILL, GridData.FILL, false, false, h_span, 1);
		gd.widthHint= width;
		label.setLayoutData(gd);
	}

	private void createSeparateSection(Composite composite) {
		createSeparateViewer(composite);
		createButtonList(composite);
	}

	private void createSeparateViewer(Composite composite) {
		fSeparateViewer= CheckboxTableViewer.newCheckList(composite, SWT.SINGLE | SWT.BORDER);
		Table table= fSeparateViewer.getTable();
		table.setHeaderVisible(false);
		table.setLinesVisible(false);
		table.setLayoutData(new GridData(GridData.FILL, GridData.BEGINNING, true, false, 1, 1));

		TableColumn nameColumn= new TableColumn(table, SWT.NONE);
		nameColumn.setText(PreferencesMessages.CodeAssistAdvancedConfigurationBlock_separate_table_category_column_title);
		nameColumn.setResizable(false);

		fSeparateViewer.setContentProvider(new ArrayContentProvider());

		ITableLabelProvider labelProvider= new SeparateTableLabelProvider();
		fSeparateViewer.setLabelProvider(labelProvider);
		fSeparateViewer.setInput(fModel.elements);

		final int ICON_AND_CHECKBOX_WITH= 50;
		final int HEADER_MARGIN= 20;
		int minNameWidth= computeWidth(table, nameColumn.getText()) + HEADER_MARGIN;
		for (int i= 0; i < fModel.elements.size(); i++) {
			minNameWidth= Math.max(minNameWidth, computeWidth(table, labelProvider.getColumnText(fModel.elements.get(i), 0)) + ICON_AND_CHECKBOX_WITH);
		}

		nameColumn.setWidth(minNameWidth);

		fSeparateViewer.addCheckStateListener(new ICheckStateListener() {
			public void checkStateChanged(CheckStateChangedEvent event) {
				boolean checked= event.getChecked();
				ModelElement element= (ModelElement) event.getElement();
				element.setSeparateCommand(checked);
			}
		});

		table.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleTableSelection();
			}
		});

	}

	private void createButtonList(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
		composite.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));

		GridLayout layout= new GridLayout();
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		composite.setLayout(layout);

		fUpButton= new Button(composite, SWT.PUSH | SWT.CENTER);
        fUpButton.setText(PreferencesMessages.CodeAssistAdvancedConfigurationBlock_Up);
        fUpButton.addSelectionListener(new SelectionAdapter() {
        	@Override
			public void widgetSelected(SelectionEvent e) {
        		int index= getSelectionIndex();
        		if (index != -1) {
        			fModel.elements.get(index).moveUp();
        			fSeparateViewer.refresh();
        			handleTableSelection();
        		}
        	}
        });
        fUpButton.setLayoutData(new GridData());
        SWTUtil.setButtonDimensionHint(fUpButton);

        fDownButton= new Button(composite, SWT.PUSH | SWT.CENTER);
        fDownButton.setText(PreferencesMessages.CodeAssistAdvancedConfigurationBlock_Down);
        fDownButton.addSelectionListener(new SelectionAdapter() {
        	@Override
			public void widgetSelected(SelectionEvent e) {
        		int index= getSelectionIndex();
        		if (index != -1) {
        			fModel.elements.get(index).moveDown();
        			fSeparateViewer.refresh();
        			handleTableSelection();
        		}
        	}
        });
        fDownButton.setLayoutData(new GridData());
        SWTUtil.setButtonDimensionHint(fDownButton);
	}

	private void createParameterTimeoutControl(Composite composite, int h_span) {
		Composite timeoutComposite= new Composite(composite, SWT.NONE);
		GridLayout layout= new GridLayout(4, false);
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		timeoutComposite.setLayout(layout);
		GridData gd= new GridData(GridData.FILL, GridData.FILL, true, false, h_span, 1);
		timeoutComposite.setLayoutData(gd);

		PixelConverter pixelConverter= new PixelConverter(composite);
		String str= PreferencesMessages.CodeAssistAdvancedConfigurationBlock_parameterNameFromAttachedJavadoc_timeout;
		addTextField(timeoutComposite, str, PREF_CODEASSIST_TIMEOUT_FOR_PARAMETER_NAME_FROM_ATTACHED_JAVADOC, 0, pixelConverter.convertWidthInCharsToPixels(7));

	}

	private void handleTableSelection() {
		ModelElement item= getSelectedItem();
		if (item != null) {
			int index= getSelectionIndex();
			fUpButton.setEnabled(index > 0);
			fDownButton.setEnabled(index < fModel.elements.size() - 1);
		} else {
			fUpButton.setEnabled(false);
			fDownButton.setEnabled(false);
		}
	}

	private ModelElement getSelectedItem() {
		return (ModelElement) ((IStructuredSelection) fSeparateViewer.getSelection()).getFirstElement();
	}

	private int getSelectionIndex() {
		return fSeparateViewer.getTable().getSelectionIndex();
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#updateControls()
	 */
	@Override
	protected void updateControls() {
		super.updateControls();

		fModel.update();
		updateCheckedState();
		fDefaultViewer.refresh();
		fSeparateViewer.refresh();
		handleTableSelection();
	}

	private void updateCheckedState() {
		final int size= fModel.elements.size();
		List<ModelElement> defaultChecked= new ArrayList<ModelElement>(size);
		List<ModelElement> separateChecked= new ArrayList<ModelElement>(size);

		for (Iterator<ModelElement> it= fModel.elements.iterator(); it.hasNext();) {
			ModelElement element= it.next();
			if (element.isInDefaultCategory())
				defaultChecked.add(element);
			if (element.isSeparateCommand())
				separateChecked.add(element);
		}

		fDefaultViewer.setCheckedElements(defaultChecked.toArray(new Object[defaultChecked.size()]));
		fSeparateViewer.setCheckedElements(separateChecked.toArray(new Object[separateChecked.size()]));
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#processChanges(org.eclipse.ui.preferences.IWorkbenchPreferenceContainer)
	 */
	@Override
	protected boolean processChanges(IWorkbenchPreferenceContainer container) {
		for (Iterator<ModelElement> it= fModel.elements.iterator(); it.hasNext();) {
			ModelElement item= it.next();
			item.update();
		}

		return super.processChanges(container);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#validateSettings(org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock.Key, java.lang.String, java.lang.String)
	 */
	@Override
	protected void validateSettings(Key changedKey, String oldValue, String newValue) {
		if (changedKey == PREF_CODEASSIST_TIMEOUT_FOR_PARAMETER_NAME_FROM_ATTACHED_JAVADOC) {
			final StatusInfo status= new StatusInfo();
			if (newValue.length() == 0)
				status.setError(PreferencesMessages.CodeAssistAdvancedConfigurationBlock_parameterNameFromAttachedJavadoc_timeout_emptyInput);
			else {
				try {
					int number= Integer.parseInt(newValue);
					int min= 0;
					int max= 5000;
					if (number < min || number > max) {
						String msgFormat= PreferencesMessages.CodeAssistAdvancedConfigurationBlock_parameterNameFromAttachedJavadoc_timeout_invalidRange;
						String msg= Messages.format(msgFormat, new Object[] {new Integer(min), new Integer(max)});
						status.setError(msg);
					}
				} catch (NumberFormatException ex) {
					String msgFormat= PreferencesMessages.CodeAssistAdvancedConfigurationBlock_parameterNameFromAttachedJavadoc_timeout_invalidInput;
					String msg= Messages.format(msgFormat, newValue);
					status.setError(msg);
				}
			}
			fContext.statusChanged(status);
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#getFullBuildDialogStrings(boolean)
	 */
	@Override
	protected String[] getFullBuildDialogStrings(boolean workspaceSettings) {
		// no builds triggered by our settings
		return null;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.OptionsConfigurationBlock#dispose()
	 */
	@Override
	public void dispose() {
		for (Iterator<Image> it= fImages.values().iterator(); it.hasNext();) {
			Image image= it.next();
			image.dispose();
		}

		super.dispose();
	}

	private int computeWidth(Control control, String name) {
		if (name == null)
			return 0;
		GC gc= new GC(control);
		try {
			gc.setFont(JFaceResources.getDialogFont());
			return gc.stringExtent(name).x + 10;
		} finally {
			gc.dispose();
		}
	}

	private static BindingManager fgLocalBindingManager;
	static {
		fgLocalBindingManager= new BindingManager(new ContextManager(), new CommandManager());
		final IBindingService bindingService= (IBindingService)PlatformUI.getWorkbench().getService(IBindingService.class);
		final Scheme[] definedSchemes= bindingService.getDefinedSchemes();
		if (definedSchemes != null) {
			try {
				for (int i = 0; i < definedSchemes.length; i++) {
					final Scheme scheme= definedSchemes[i];
					final Scheme copy= fgLocalBindingManager.getScheme(scheme.getId());
					copy.define(scheme.getName(), scheme.getDescription(), scheme.getParentId());
				}
			} catch (final NotDefinedException e) {
				JavaPlugin.log(e);
			}
		}
		fgLocalBindingManager.setLocale(bindingService.getLocale());
		fgLocalBindingManager.setPlatform(bindingService.getPlatform());
	}

	private static String getKeyboardShortcut(ParameterizedCommand command) {
		IBindingService bindingService= (IBindingService) PlatformUI.getWorkbench().getAdapter(IBindingService.class);
		fgLocalBindingManager.setBindings(bindingService.getBindings());
		try {
			Scheme activeScheme= bindingService.getActiveScheme();
			if (activeScheme != null)
				fgLocalBindingManager.setActiveScheme(activeScheme);
		} catch (NotDefinedException e) {
			JavaPlugin.log(e);
		}

		TriggerSequence[] bindings= fgLocalBindingManager.getActiveBindingsDisregardingContextFor(command);
		if (bindings.length > 0)
			return bindings[0].format();
		return null;
	}

	private Image getImage(ImageDescriptor imgDesc) {
		if (imgDesc == null)
			return null;

		Image img= fImages.get(imgDesc);
		if (img == null) {
			img= imgDesc.createImage(false);
			fImages.put(imgDesc, img);
		}
		return img;
	}

}

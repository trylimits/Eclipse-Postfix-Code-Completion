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
package org.eclipse.jdt.internal.ui.preferences.cleanup;

import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.fix.CodeStyleCleanUp;

public final class MemberAccessesTabPage extends AbstractCleanUpTabPage {

	public static final String ID= "org.eclipse.jdt.ui.cleanup.tabpage.member_accesses"; //$NON-NLS-1$

    public MemberAccessesTabPage() {
    	super();
    }

	@Override
	protected AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values) {
		return new AbstractCleanUp[] {
        	new CodeStyleCleanUp(values)
        };
    }

    @Override
	protected void doCreatePreferences(Composite composite, int numColumns) {

    	Group instanceGroup= createGroup(numColumns, composite, CleanUpMessages.MemberAccessesTabPage_GroupName_NonStaticAccesses);

    	final CheckboxPreference thisFieldPref= createCheckboxPref(instanceGroup, numColumns, CleanUpMessages.MemberAccessesTabPage_CheckboxName_FieldQualifier, CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS, CleanUpModifyDialog.FALSE_TRUE);

    	Composite fieldComposite= new Composite(instanceGroup, SWT.NONE);
    	fieldComposite.setLayoutData(createGridData(numColumns, GridData.FILL_HORIZONTAL, SWT.DEFAULT));
    	fieldComposite.setLayout(createGridLayout(3, false));
    	fieldComposite.setFont(composite.getFont());

    	intent(fieldComposite);
		final RadioPreference thisFieldAlwaysPref= createRadioPref(fieldComposite, 1, CleanUpMessages.MemberAccessesTabPage_RadioName_AlwaysThisForFields, CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS, CleanUpModifyDialog.FALSE_TRUE);
		final RadioPreference thisFieldNecessaryPref= createRadioPref(fieldComposite, 1, CleanUpMessages.MemberAccessesTabPage_RadioName_NeverThisForFields, CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_IF_NECESSARY, CleanUpModifyDialog.FALSE_TRUE);
		registerSlavePreference(thisFieldPref, new RadioPreference[] {thisFieldAlwaysPref, thisFieldNecessaryPref});

		final CheckboxPreference thisMethodPref= createCheckboxPref(instanceGroup, numColumns, CleanUpMessages.MemberAccessesTabPage_CheckboxName_MethodQualifier, CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS, CleanUpModifyDialog.FALSE_TRUE);

		Composite methodComposite= new Composite(instanceGroup, SWT.NONE);
    	methodComposite.setLayoutData(createGridData(numColumns, GridData.FILL_HORIZONTAL, SWT.DEFAULT));
    	methodComposite.setLayout(createGridLayout(3, false));
    	methodComposite.setFont(composite.getFont());

    	intent(methodComposite);
		final RadioPreference thisMethodAlwaysPref= createRadioPref(methodComposite, 1, CleanUpMessages.MemberAccessesTabPage_RadioName_AlwaysThisForMethods, CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS_ALWAYS, CleanUpModifyDialog.FALSE_TRUE);
		final RadioPreference thisMethodNecessaryPref= createRadioPref(methodComposite, 1, CleanUpMessages.MemberAccessesTabPage_RadioName_NeverThisForMethods, CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS_IF_NECESSARY, CleanUpModifyDialog.FALSE_TRUE);
		registerSlavePreference(thisMethodPref, new RadioPreference[] {thisMethodAlwaysPref, thisMethodNecessaryPref});

    	Group staticGroup= createGroup(numColumns, composite, CleanUpMessages.MemberAccessesTabPage_GroupName_StaticAccesses);

    	final CheckboxPreference staticMemberPref= createCheckboxPref(staticGroup, numColumns, CleanUpMessages.MemberAccessesTabPage_CheckboxName_QualifyWithDeclaringClass, CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS, CleanUpModifyDialog.FALSE_TRUE);
		intent(staticGroup);
		final CheckboxPreference staticFieldPref= createCheckboxPref(staticGroup, numColumns - 1, CleanUpMessages.MemberAccessesTabPage_CheckboxName_QualifyFieldWithDeclaringClass, CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_FIELD, CleanUpModifyDialog.FALSE_TRUE);
		intent(staticGroup);
		final CheckboxPreference staticMethodPref= createCheckboxPref(staticGroup, numColumns - 1, CleanUpMessages.MemberAccessesTabPage_CheckboxName_QualifyMethodWithDeclaringClass, CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_METHOD, CleanUpModifyDialog.FALSE_TRUE);
		intent(staticGroup);
		final CheckboxPreference accessesThroughSubtypesPref= createCheckboxPref(staticGroup, numColumns - 1, CleanUpMessages.MemberAccessesTabPage_CheckboxName_ChangeAccessesThroughSubtypes, CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_SUBTYPE_ACCESS, CleanUpModifyDialog.FALSE_TRUE);
		intent(staticGroup);
		final CheckboxPreference accessesThroughInstancesPref= createCheckboxPref(staticGroup, numColumns - 1, CleanUpMessages.MemberAccessesTabPage_CheckboxName_ChangeAccessesThroughInstances, CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS, CleanUpModifyDialog.FALSE_TRUE);
		registerSlavePreference(staticMemberPref, new CheckboxPreference[] {staticFieldPref, staticMethodPref, accessesThroughSubtypesPref, accessesThroughInstancesPref});
    }
}

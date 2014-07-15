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
package org.eclipse.jdt.internal.ui.viewsupport;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.Assert;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;

import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.persistence.TemplatePersistenceData;
import org.eclipse.jface.text.templates.persistence.TemplateReaderWriter;
import org.eclipse.jface.text.templates.persistence.TemplateStore;

import org.eclipse.ui.preferences.ScopedPreferenceStore;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * @since 3.1
 */
public final class ProjectTemplateStore {

	private static final String KEY= "org.eclipse.jdt.ui.text.custom_code_templates"; //$NON-NLS-1$

	private final TemplateStore fInstanceStore;
	private final TemplateStore fProjectStore;

	public ProjectTemplateStore(IProject project) {
		fInstanceStore= JavaPlugin.getDefault().getCodeTemplateStore();
		if (project == null) {
			fProjectStore= null;
		} else {
			final ScopedPreferenceStore projectSettings= new ScopedPreferenceStore(new ProjectScope(project), JavaUI.ID_PLUGIN);
			fProjectStore= new TemplateStore(projectSettings, KEY) {
				/*
				 * Make sure we keep the id of added code templates - add removes
				 * it in the usual add() method
				 */
				@Override
				public void add(TemplatePersistenceData data) {
					internalAdd(data);
				}

				@Override
				public void save() throws IOException {

					TemplatePersistenceData[] templateData= ProjectTemplateStore.this.getTemplateData();
					for (int i= 0; i < templateData.length; i++) {
						if (isProjectSpecific(templateData[i].getId())) {
							StringWriter output= new StringWriter();
							TemplateReaderWriter writer= new TemplateReaderWriter();
							writer.save(getTemplateData(false), output);

							projectSettings.setValue(KEY, output.toString());
							projectSettings.save();

							return;
						}
					}

					projectSettings.setToDefault(KEY);
					projectSettings.save();
				}
			};
		}
	}

	public static boolean hasProjectSpecificTempates(IProject project) {
		String pref= new ProjectScope(project).getNode(JavaUI.ID_PLUGIN).get(KEY, null);
		if (pref != null && pref.trim().length() > 0) {
			Reader input= new StringReader(pref);
			TemplateReaderWriter reader= new TemplateReaderWriter();
			TemplatePersistenceData[] datas;
			try {
				datas= reader.read(input);
				return datas.length > 0;
			} catch (IOException e) {
				// ignore
			}
		}
		return false;
	}


	public TemplatePersistenceData[] getTemplateData() {
		if (fProjectStore != null) {
			return fProjectStore.getTemplateData(true);
		} else {
			return fInstanceStore.getTemplateData(true);
		}
	}

	public Template findTemplateById(String id) {
		Template template= null;
		if (fProjectStore != null)
			template= fProjectStore.findTemplateById(id);
		if (template == null)
			template= fInstanceStore.findTemplateById(id);

		return template;
	}

	public void load() throws IOException {
		if (fProjectStore != null) {
			fProjectStore.load();

			Set<String> datas= new HashSet<String>();
			TemplatePersistenceData[] data= fProjectStore.getTemplateData(false);
			for (int i= 0; i < data.length; i++) {
				datas.add(data[i].getId());
			}

			data= fInstanceStore.getTemplateData(false);
			for (int i= 0; i < data.length; i++) {
				TemplatePersistenceData orig= data[i];
				if (!datas.contains(orig.getId())) {
					TemplatePersistenceData copy= new TemplatePersistenceData(new Template(orig.getTemplate()), orig.isEnabled(), orig.getId());
					fProjectStore.add(copy);
					copy.setDeleted(true);
				}
			}
		}
	}

	public boolean isProjectSpecific(String id) {
		if (id == null) {
			return false;
		}

		if (fProjectStore == null)
			return false;

		return fProjectStore.findTemplateById(id) != null;
	}


	public void setProjectSpecific(String id, boolean projectSpecific) {
		Assert.isNotNull(fProjectStore);

		TemplatePersistenceData data= fProjectStore.getTemplateData(id);
		if (data == null) {
			return; // does not exist
		} else {
			data.setDeleted(!projectSpecific);
		}
	}

	public void restoreDefaults() {
		if (fProjectStore == null) {
			fInstanceStore.restoreDefaults(false);
		} else {
			try {
				load();
			} catch (IOException e) {
				JavaPlugin.log(e);
			}
		}
	}

	public void save() throws IOException {
		if (fProjectStore == null) {
			fInstanceStore.save();
		} else {
			fProjectStore.save();
		}
	}

	public void revertChanges() throws IOException {
		if (fProjectStore != null) {
			// nothing to do
		} else {
			fInstanceStore.load();
		}
	}
}

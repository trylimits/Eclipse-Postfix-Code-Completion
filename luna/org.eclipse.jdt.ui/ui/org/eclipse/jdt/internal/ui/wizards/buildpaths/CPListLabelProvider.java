/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import org.eclipse.swt.graphics.Image;

import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.LabelProvider;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.ide.IDE;

import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.ISharedImages;
import org.eclipse.jdt.ui.JavaElementImageDescriptor;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration;
import org.eclipse.jdt.ui.wizards.ClasspathAttributeConfiguration.ClasspathAttributeAccess;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.ImageDescriptorRegistry;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;

public class CPListLabelProvider extends LabelProvider {

	private String fNewLabel, fClassLabel, fMissing;

	private ImageDescriptorRegistry fRegistry;
	private ISharedImages fSharedImages;

	private ImageDescriptor fProjectImage;

	private ClasspathAttributeConfigurationDescriptors fAttributeDescriptors;


	public CPListLabelProvider() {
		fNewLabel= NewWizardMessages.CPListLabelProvider_new;
		fClassLabel= NewWizardMessages.CPListLabelProvider_classcontainer;
		fMissing= NewWizardMessages.CPListLabelProvider_missing;
		fRegistry= JavaPlugin.getImageDescriptorRegistry();

		fSharedImages= JavaUI.getSharedImages();

		IWorkbench workbench= JavaPlugin.getDefault().getWorkbench();

		fProjectImage= workbench.getSharedImages().getImageDescriptor(IDE.SharedImages.IMG_OBJ_PROJECT);
		fAttributeDescriptors= JavaPlugin.getDefault().getClasspathAttributeConfigurationDescriptors();
	}

	@Override
	public String getText(Object element) {
		if (element instanceof CPListElement) {
			return getCPListElementText((CPListElement) element);
		} else if (element instanceof CPListElementAttribute) {
			CPListElementAttribute attribute= (CPListElementAttribute) element;
			String text= getCPListElementAttributeText(attribute);
			if (attribute.isNonModifiable()) {
				return Messages.format(NewWizardMessages.CPListLabelProvider_non_modifiable_attribute, text);
			}
			return text;
		} else if (element instanceof CPUserLibraryElement) {
			return getCPUserLibraryText((CPUserLibraryElement) element);
		} else if (element instanceof IAccessRule) {
			IAccessRule rule= (IAccessRule) element;
			return Messages.format(NewWizardMessages.CPListLabelProvider_access_rules_label, new String[] { AccessRulesLabelProvider.getResolutionLabel(rule.getKind()), BasicElementLabels.getPathLabel(rule.getPattern(), false)});
		}
		return super.getText(element);
	}

	public String getCPUserLibraryText(CPUserLibraryElement element) {
		String name= element.getName();
		if (element.isSystemLibrary()) {
			name= Messages.format(NewWizardMessages.CPListLabelProvider_systemlibrary, name);
		}
		return name;
	}

	public String getCPListElementAttributeText(CPListElementAttribute attrib) {
		String notAvailable= NewWizardMessages.CPListLabelProvider_none;
		String key= attrib.getKey();
		if (key.equals(CPListElement.SOURCEATTACHMENT)) {
			String arg;
			IPath path= (IPath) attrib.getValue();
			if (path != null && !path.isEmpty()) {
				if (attrib.getParent().getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
					arg= getVariableString(path);
				} else {
					arg= getPathString(path, path.getDevice() != null);
				}
			} else {
				arg= notAvailable;
			}
			return Messages.format(NewWizardMessages.CPListLabelProvider_source_attachment_label, new String[] { arg });
		} else if (key.equals(CPListElement.OUTPUT)) {
			String arg= null;
			IPath path= (IPath) attrib.getValue();
			if (path != null) {
				arg= BasicElementLabels.getPathLabel(path, false);
			} else {
				arg= NewWizardMessages.CPListLabelProvider_default_output_folder_label;
			}
			return Messages.format(NewWizardMessages.CPListLabelProvider_output_folder_label, new String[] { arg });
		} else if (key.equals(CPListElement.EXCLUSION)) {
			String arg= null;
			IPath[] patterns= (IPath[]) attrib.getValue();
			if (patterns != null && patterns.length > 0) {
				int patternsCount= 0;
				StringBuffer buf= new StringBuffer();
				for (int i= 0; i < patterns.length; i++) {
					if (patterns[i].segmentCount() > 0) {
						String pattern= BasicElementLabels.getPathLabel(patterns[i], false);
						if (patternsCount > 0) {
							buf.append(NewWizardMessages.CPListLabelProvider_exclusion_filter_separator);
						}
						buf.append(pattern);
						patternsCount++;
					}
				}
				if (patternsCount > 0) {
					arg= buf.toString();
				} else {
					arg= notAvailable;
				}
			} else {
				arg= notAvailable;
			}
			return Messages.format(NewWizardMessages.CPListLabelProvider_exclusion_filter_label, new String[] { arg });
		} else if (key.equals(CPListElement.INCLUSION)) {
			String arg= null;
			IPath[] patterns= (IPath[]) attrib.getValue();
			if (patterns != null && patterns.length > 0) {
				int patternsCount= 0;
				StringBuffer buf= new StringBuffer();
				for (int i= 0; i < patterns.length; i++) {
					if (patterns[i].segmentCount() > 0) {
						String pattern= BasicElementLabels.getPathLabel(patterns[i], false);
						if (patternsCount > 0) {
							buf.append(NewWizardMessages.CPListLabelProvider_inclusion_filter_separator);
						}
						buf.append(pattern);
						patternsCount++;
					}
				}
				if (patternsCount > 0) {
					arg= buf.toString();
				} else {
					arg= notAvailable;
				}
			} else {
				arg= NewWizardMessages.CPListLabelProvider_all;
			}
			return Messages.format(NewWizardMessages.CPListLabelProvider_inclusion_filter_label, new String[] { arg });
		} else if (key.equals(CPListElement.ACCESSRULES)) {
			IAccessRule[] rules= (IAccessRule[]) attrib.getValue();
			int nRules= rules != null ? rules.length : 0;

			int parentKind= attrib.getParent().getEntryKind();
			if (parentKind == IClasspathEntry.CPE_PROJECT) {
				Boolean combined= (Boolean) attrib.getParent().getAttribute(CPListElement.COMBINE_ACCESSRULES);
				if (nRules > 0) {
					if (combined.booleanValue()) {
						if (nRules == 1) {
							return NewWizardMessages.CPListLabelProvider_project_access_rules_combined_singular;
						} else {
							return Messages.format(NewWizardMessages.CPListLabelProvider_project_access_rules_combined_plural, String.valueOf(nRules));
						}
					} else {
						if (nRules == 1) {
							return NewWizardMessages.CPListLabelProvider_project_access_rules_not_combined_singular;
						} else {
							return Messages.format(NewWizardMessages.CPListLabelProvider_project_access_rules_not_combined_plural, String.valueOf(nRules));
						}
					}
				} else {
					return NewWizardMessages.CPListLabelProvider_project_access_rules_no_rules;
				}
			} else if (parentKind == IClasspathEntry.CPE_CONTAINER) {
				if (nRules > 1) {
					return Messages.format(NewWizardMessages.CPListLabelProvider_container_access_rules_plural, String.valueOf(nRules));
				} else if (nRules == 1) {
					return NewWizardMessages.CPListLabelProvider_container_access_rules_singular;
				} else {
					return NewWizardMessages.CPListLabelProvider_container_no_access_rules;
				}
			} else {
				if (nRules > 1) {
					return Messages.format(NewWizardMessages.CPListLabelProvider_access_rules_enabled_plural, String.valueOf(nRules));
				} else if (nRules == 1) {
					return NewWizardMessages.CPListLabelProvider_access_rules_enabled_singular;
				} else {
					return NewWizardMessages.CPListLabelProvider_access_rules_disabled;
				}
			}
		} else if (key.equals(CPListElement.IGNORE_OPTIONAL_PROBLEMS)) {
			String arg;
			if ("true".equals(attrib.getValue())) { //$NON-NLS-1$
				arg= NewWizardMessages.CPListLabelProvider_ignore_optional_problems_yes;
			} else {
				arg= NewWizardMessages.CPListLabelProvider_ignore_optional_problems_no;
			}
			return Messages.format(NewWizardMessages.CPListLabelProvider_ignore_optional_problems_label, arg);
		} else {
			ClasspathAttributeConfiguration config= fAttributeDescriptors.get(key);
			if (config != null) {
				ClasspathAttributeAccess access= attrib.getClasspathAttributeAccess();
				String nameLabel= config.getNameLabel(access);
				String valueLabel= config.getValueLabel(access); // should be LTR marked
				return Messages.format(NewWizardMessages.CPListLabelProvider_attribute_label, new String[] { nameLabel, valueLabel });
			}
			String arg= (String) attrib.getValue();
			if (arg == null) {
				arg= notAvailable;
			}
			return Messages.format(NewWizardMessages.CPListLabelProvider_attribute_label, new String[] { key, arg });
		}
	}

	public String getCPListElementText(CPListElement cpentry) {
		IPath path= cpentry.getPath();
		switch (cpentry.getEntryKind()) {
			case IClasspathEntry.CPE_LIBRARY: {
				IResource resource= cpentry.getResource();
				if (resource instanceof IContainer) {
					StringBuffer buf= new StringBuffer(BasicElementLabels.getPathLabel(path, false));
					IPath linkTarget= cpentry.getLinkTarget();
					if (linkTarget != null) {
						buf.append(JavaElementLabels.CONCAT_STRING);
						buf.append(BasicElementLabels.getPathLabel(linkTarget, true));
					}
					buf.append(' ');
					buf.append(fClassLabel);
					if (!resource.exists()) {
						buf.append(' ');
						if (cpentry.isMissing()) {
							buf.append(fMissing);
						} else {
							buf.append(fNewLabel);
						}
					}
					return buf.toString();
				} else {
					String label= getPathString(path, resource == null);
					if (cpentry.isMissing()) {
						label= label + ' ' + fMissing;
					}
					return label;
				}
			}
			case IClasspathEntry.CPE_VARIABLE: {
				String label= getVariableString(path);
				if (cpentry.isMissing()) {
					label= label + ' ' + fMissing;
				}
				return label;
			}
			case IClasspathEntry.CPE_PROJECT:
				String label= path.lastSegment();
				if (cpentry.isMissing()) {
					label= label + ' ' + fMissing;
				}
				return label;
			case IClasspathEntry.CPE_CONTAINER:
				try {
					IClasspathContainer container= JavaCore.getClasspathContainer(path, cpentry.getJavaProject());
					if (container != null) {
						return container.getDescription();
					}
					ClasspathContainerInitializer initializer= JavaCore.getClasspathContainerInitializer(path.segment(0));
					if (initializer != null) {
						String description= initializer.getDescription(path, cpentry.getJavaProject());
						return Messages.format(NewWizardMessages.CPListLabelProvider_unbound_library, description);
					}
				} catch (JavaModelException e) {

				}
				return BasicElementLabels.getPathLabel(path, false);
			case IClasspathEntry.CPE_SOURCE: {
				String pathLabel= BasicElementLabels.getPathLabel(path, false);
				StringBuffer buf= new StringBuffer(pathLabel);
				IPath linkTarget= cpentry.getLinkTarget();
				if (linkTarget != null) {
					buf.append(JavaElementLabels.CONCAT_STRING);
					buf.append(BasicElementLabels.getPathLabel(linkTarget, true));
				}
				IResource resource= cpentry.getResource();
				if (resource != null && !resource.exists()) {
					buf.append(' ');
					if (cpentry.isMissing()) {
						buf.append(fMissing);
					} else {
						buf.append(fNewLabel);
					}
				} else if (cpentry.getOrginalPath() == null) {
					buf.append(' ');
					buf.append(fNewLabel);
				}
				return buf.toString();
			}
			default:
				// pass
		}
		return NewWizardMessages.CPListLabelProvider_unknown_element_label;
	}

	private String getPathString(IPath path, boolean isExternal) {
		if (ArchiveFileFilter.isArchivePath(path, true)) {
			String appended= BasicElementLabels.getPathLabel(path.removeLastSegments(1), isExternal);
			String lastSegment= BasicElementLabels.getResourceName(path.lastSegment());
			return Messages.format(NewWizardMessages.CPListLabelProvider_twopart, new String[] { lastSegment, appended });
		} else {
			return BasicElementLabels.getPathLabel(path, isExternal);
		}
	}

	private String getVariableString(IPath path) {
		String name= BasicElementLabels.getPathLabel(path, false);
		IPath entryPath= JavaCore.getClasspathVariable(path.segment(0));
		if (entryPath != null) {
			String appended= BasicElementLabels.getPathLabel(entryPath.append(path.removeFirstSegments(1)), true);
			return Messages.format(NewWizardMessages.CPListLabelProvider_twopart, new String[] { name, appended });
		} else {
			return name;
		}
	}

	private ImageDescriptor getCPListElementBaseImage(CPListElement cpentry) {
		switch (cpentry.getEntryKind()) {
			case IClasspathEntry.CPE_SOURCE:
				if (cpentry.getPath().segmentCount() == 1) {
					return fProjectImage;
				} else {
					return fSharedImages.getImageDescriptor(ISharedImages.IMG_OBJS_PACKFRAG_ROOT);
				}
			case IClasspathEntry.CPE_LIBRARY:
				IResource res= cpentry.getResource();
				IPath path= (IPath) cpentry.getAttribute(CPListElement.SOURCEATTACHMENT);
				if (res == null) {
					if (ArchiveFileFilter.isArchivePath(cpentry.getPath(), true)) {
						if (path == null || path.isEmpty()) {
							return fSharedImages.getImageDescriptor(ISharedImages.IMG_OBJS_EXTERNAL_ARCHIVE);
						} else {
							return fSharedImages.getImageDescriptor(ISharedImages.IMG_OBJS_EXTERNAL_ARCHIVE_WITH_SOURCE);
						}
					} else {
						if (path == null || path.isEmpty()) {
							return JavaPluginImages.DESC_OBJS_CLASSFOLDER;
						} else {
							return JavaPluginImages.DESC_OBJS_CLASSFOLDER_WSRC;
						}
					}
				} else if (res instanceof IFile) {
					if (path == null || path.isEmpty()) {
						return fSharedImages.getImageDescriptor(ISharedImages.IMG_OBJS_JAR);
					} else {
						return fSharedImages.getImageDescriptor(ISharedImages.IMG_OBJS_JAR_WITH_SOURCE);
					}
				} else {
					return fSharedImages.getImageDescriptor(ISharedImages.IMG_OBJS_PACKFRAG_ROOT);
				}
			case IClasspathEntry.CPE_PROJECT:
				return fProjectImage;
			case IClasspathEntry.CPE_VARIABLE:
				ImageDescriptor variableImage= fSharedImages.getImageDescriptor(ISharedImages.IMG_OBJS_CLASSPATH_VAR_ENTRY);
				if (cpentry.isDeprecated()) {
					return new JavaElementImageDescriptor(variableImage, JavaElementImageDescriptor.DEPRECATED, JavaElementImageProvider.SMALL_SIZE);
				}
				return variableImage;
			case IClasspathEntry.CPE_CONTAINER:
				return fSharedImages.getImageDescriptor(ISharedImages.IMG_OBJS_LIBRARY);
			default:
				return null;
		}
	}

	@Override
	public Image getImage(Object element) {
		if (element instanceof CPListElement) {
			CPListElement cpentry= (CPListElement) element;
			ImageDescriptor imageDescriptor= getCPListElementBaseImage(cpentry);
			if (imageDescriptor != null) {
				if (cpentry.isMissing() || cpentry.hasMissingChildren()) {
					imageDescriptor= new JavaElementImageDescriptor(imageDescriptor, JavaElementImageDescriptor.ERROR, JavaElementImageProvider.SMALL_SIZE);
				}
				return fRegistry.get(imageDescriptor);
			}
		} else if (element instanceof CPListElementAttribute) {
			CPListElementAttribute attribute= (CPListElementAttribute) element;
			String key= (attribute).getKey();
			if (key.equals(CPListElement.SOURCEATTACHMENT)) {
				return fRegistry.get(JavaPluginImages.DESC_OBJS_SOURCE_ATTACH_ATTRIB);
			} else if (key.equals(CPListElement.OUTPUT)) {
				return fRegistry.get(JavaPluginImages.DESC_OBJS_OUTPUT_FOLDER_ATTRIB);
			} else if (key.equals(CPListElement.EXCLUSION)) {
				return fRegistry.get(JavaPluginImages.DESC_OBJS_EXCLUSION_FILTER_ATTRIB);
			} else if (key.equals(CPListElement.INCLUSION)) {
				return fRegistry.get(JavaPluginImages.DESC_OBJS_INCLUSION_FILTER_ATTRIB);
			} else if (key.equals(CPListElement.ACCESSRULES)) {
				return fRegistry.get(JavaPluginImages.DESC_OBJS_ACCESSRULES_ATTRIB);
			} else if (key.equals(CPListElement.IGNORE_OPTIONAL_PROBLEMS)) {
				Image image= fRegistry.get(getCPListElementBaseImage(attribute.getParent()));
				if (image != null) {
					ImageDescriptor overlay= JavaPluginImages.DESC_OVR_IGNORE_OPTIONAL_PROBLEMS;
					ImageDescriptor imageDescriptor= new DecorationOverlayIcon(image, overlay, IDecoration.BOTTOM_LEFT);
					return fRegistry.get(imageDescriptor);
				}
			} else {
				ClasspathAttributeConfiguration config= fAttributeDescriptors.get(key);
				if (config != null) {
					return fRegistry.get(config.getImageDescriptor(attribute.getClasspathAttributeAccess()));
				}
			}
			return  fSharedImages.getImage(ISharedImages.IMG_OBJS_CLASSPATH_VAR_ENTRY);
		} else if (element instanceof CPUserLibraryElement) {
			if (((CPUserLibraryElement) element).hasMissingChildren()) {
				ImageDescriptor descriptor= fSharedImages.getImageDescriptor(ISharedImages.IMG_OBJS_LIBRARY);
				if (descriptor != null) {
					return fRegistry.get(new JavaElementImageDescriptor(descriptor, JavaElementImageDescriptor.ERROR, JavaElementImageProvider.SMALL_SIZE));
				}
			}
			return fSharedImages.getImage(ISharedImages.IMG_OBJS_LIBRARY);
		} else if (element instanceof IAccessRule) {
			IAccessRule rule= (IAccessRule) element;
			return AccessRulesLabelProvider.getResolutionImage(rule.getKind());
		}
		return null;
	}
}

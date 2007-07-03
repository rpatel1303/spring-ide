/*******************************************************************************
 * Copyright (c) 2005, 2007 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.beans.ui.model;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.ide.eclipse.beans.core.BeansCorePlugin;
import org.springframework.ide.eclipse.beans.core.internal.model.Bean;
import org.springframework.ide.eclipse.beans.core.internal.model.BeansModelUtils;
import org.springframework.ide.eclipse.beans.core.model.IBeansConfig;
import org.springframework.ide.eclipse.beans.core.model.IBeansConfigSet;
import org.springframework.ide.eclipse.beans.core.model.IBeansModel;
import org.springframework.ide.eclipse.beans.core.model.IBeansModelElement;
import org.springframework.ide.eclipse.beans.core.model.IBeansProject;
import org.springframework.ide.eclipse.beans.ui.BeansUIImages;
import org.springframework.ide.eclipse.beans.ui.BeansUIPlugin;
import org.springframework.ide.eclipse.core.MarkerUtils;
import org.springframework.ide.eclipse.core.io.ZipEntryStorage;
import org.springframework.ide.eclipse.core.model.IModelChangeListener;
import org.springframework.ide.eclipse.core.model.IResourceModelElement;
import org.springframework.ide.eclipse.core.model.ISourceModelElement;
import org.springframework.ide.eclipse.core.model.ModelChangeEvent;
import org.springframework.ide.eclipse.ui.SpringLabelDecorator;
import org.springframework.ide.eclipse.ui.SpringUIUtils;

/**
 * This decorator adds an overlay image to all Spring beans config files and
 * their corresponding folders and bean classes (Java source and class files).
 * This decoration is refreshed on every modification to the Spring Beans model.
 * Therefore the decorator adds a {@link IModelChangeListener change listener}
 * to the beans model.
 * @author Torsten Juergeleit
 * @author Christian Dupuis
 */
public class BeansModelLabelDecorator extends SpringLabelDecorator
		implements ILightweightLabelDecorator {

	public static final String DECORATOR_ID = BeansUIPlugin.PLUGIN_ID
			+ ".model.beansModelLabelDecorator";

	public static void update() {
		SpringUIUtils.updateDecorator(SpringLabelDecorator.DECORATOR_ID);
		SpringUIUtils.updateDecorator(DECORATOR_ID);
	}

	private IModelChangeListener listener;

	public BeansModelLabelDecorator() {
		listener = new IModelChangeListener() {
			public void elementChanged(ModelChangeEvent event) {
				if ((event.getElement() instanceof IBeansProject || event
						.getElement() instanceof IBeansConfig)
						&& event.getType() != ModelChangeEvent.Type.REMOVED) {
					update();
				}
			}
		};
		BeansCorePlugin.getModel().addChangeListener(listener);
	}

	public void decorate(Object element, IDecoration decoration) {
		if (element instanceof IFolder) {
			decorateFolder((IFolder) element, decoration);
		}
		else if (element instanceof IFile) {
			decorateFile((IFile) element, decoration);
		}
		else if (element instanceof IJavaElement) {
			decorateJavaElement(((IJavaElement) element), decoration);
		}
		else if (element instanceof IBeansModelElement) {
			if (element instanceof Bean) {
				decorateBean((Bean) element, decoration);
			}
			decorateBeansModelElement(((IBeansModelElement) element),
					decoration);
		}
	}
	
	/**
	 * Adds decorations to {@link Bean}s.
	 * @since 2.0.1
	 */
	private void decorateBean(Bean bean, IDecoration decoration) {
		BeanDefinition bd = bean.getBeanDefinition();
		if (bean.isChildBean()) {
			decoration.addOverlay(BeansUIImages.DESC_OVR_CHILD,
					IDecoration.TOP_RIGHT);
		}
		if (bean.isFactory()) {
			decoration.addOverlay(BeansUIImages.DESC_OVR_FACTORY,
					IDecoration.TOP_LEFT);
		}
		if (bean.isAbstract()) {
			decoration.addOverlay(BeansUIImages.DESC_OVR_ABSTRACT,
					IDecoration.BOTTOM_RIGHT);
		}
		if (!bd.isSingleton()) {
			decoration.addOverlay(BeansUIImages.DESC_OVR_PROTOTYPE,
					IDecoration.BOTTOM_RIGHT);
		}
		if (bd instanceof AnnotatedBeanDefinition) {
			decoration.addOverlay(BeansUIImages.DESC_OVR_ANNOTATION,
					IDecoration.BOTTOM_LEFT);
		}
	}

	/**
	 * Adds error and warning decorations to {@link IBeansModelElement}.
	 * @since 2.0.1
	 */
	private void decorateBeansModelElement(IBeansModelElement element,
			IDecoration decoration) {
		addErrorOverlay(decoration, getSeverity(element));
	}

	protected void decorateFile(IFile file, IDecoration decoration) {
		IBeansModel model = BeansCorePlugin.getModel();
		IBeansProject project = model.getProject(file.getProject());
		if (project != null) {
			for (IBeansConfig config : project.getConfigs()) {

				// The following comparison works for archived config files too
				if (config.getElementResource().equals(file)) {
					addErrorOverlay(decoration, getSeverity(config));
					decoration.addOverlay(BeansUIImages.DESC_OVR_SPRING);
					break;
				}
			}
		}
	}

	protected void decorateFolder(IFolder folder, IDecoration decoration) {
		IBeansModel model = BeansCorePlugin.getModel();
		IBeansProject project = model.getProject(folder.getProject());
		if (project != null) {
			String path = folder.getProjectRelativePath().toString() + '/';
			for (IBeansConfig config : project.getConfigs()) {
				if (config.getElementName().startsWith(path)) {
					decoration.addOverlay(BeansUIImages.DESC_OVR_SPRING);
					break;
				}
			}
		}
	}

	protected void decorateJavaElement(IJavaElement element,
			IDecoration decoration) {
		int type = element.getElementType();
		if (type == IJavaElement.PACKAGE_FRAGMENT_ROOT
				|| type == IJavaElement.CLASS_FILE
				|| type == IJavaElement.COMPILATION_UNIT) {
			IBeansModel model = BeansCorePlugin.getModel();
			IBeansProject project = model.getProject(element.getJavaProject()
					.getProject());
			if (project != null) {
				try {
					if (type == IJavaElement.PACKAGE_FRAGMENT_ROOT) {

						// Decorate JAR file
						IResource resource = ((IPackageFragmentRoot) element)
								.getResource();
						if (resource instanceof IFile) {
							for (IBeansConfig config : project.getConfigs()) {
								if (config.getElementResource()
										.equals(resource)) {
									decoration
											.addOverlay(BeansUIImages.DESC_OVR_SPRING);
									break;
								}
							}
						}
					}
					else if (type == IJavaElement.CLASS_FILE) {

						// Decorate Java class file
						IType javaType = ((IClassFile) element).getType();
						if (BeansModelUtils.isBeanClass(javaType
								.getFullyQualifiedName())) {
							decoration
									.addOverlay(BeansUIImages.DESC_OVR_SPRING);
						}
					}
					else if (type == IJavaElement.COMPILATION_UNIT) {

						// Decorate Java source file
						for (IType javaType : ((ICompilationUnit) element)
								.getTypes()) {
							if (BeansModelUtils.isBeanClass(javaType
									.getFullyQualifiedName())) {
								decoration
										.addOverlay(BeansUIImages.DESC_OVR_SPRING);
								break;
							}
						}
					}
				}
				catch (JavaModelException e) {
					// Ignore
				}
			}
		}
	}

	@Override
	public void dispose() {
		BeansCorePlugin.getModel().removeChangeListener(listener);
	}

	protected int getSeverity(Object element) {
		int severity = 0;
		if (element instanceof ISourceModelElement) {
			ISourceModelElement source = (ISourceModelElement) element;
			severity = MarkerUtils.getHighestSeverityFromMarkersInRange(source
					.getElementResource(), source.getElementStartLine(), source
					.getElementEndLine());
		}
		else if (element instanceof IResourceModelElement) {
			if (element instanceof IBeansProject) {
				for (IBeansConfig config : ((IBeansProject) element)
						.getConfigs()) {
					severity = MarkerUtils
							.getHighestSeverityFromMarkersInRange(config
									.getElementResource(), -1, -1);
					if (severity == IMarker.SEVERITY_ERROR) {
						break;
					}
				}
			}
			else if (element instanceof IBeansConfigSet) {
				for (IBeansConfig config : ((IBeansConfigSet) element)
						.getConfigs()) {
					severity = MarkerUtils
							.getHighestSeverityFromMarkersInRange(config
									.getElementResource(), -1, -1);
					if (severity == IMarker.SEVERITY_ERROR) {
						break;
					}
				}
			}
			else {
				severity = MarkerUtils.getHighestSeverityFromMarkersInRange(
						((IResourceModelElement) element).getElementResource(),
						-1, -1);
			}
		}
		else if (element instanceof IResource) {
			severity = MarkerUtils.getHighestSeverityFromMarkersInRange(
					(IResource) element, -1, -1);
		}
		else if (element instanceof ZipEntryStorage) {
			IResource resource = ((ZipEntryStorage) element).getFile();
			severity = MarkerUtils.getHighestSeverityFromMarkersInRange(
					resource, -1, -1);
		}
		return severity;
	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		return false;
	}
}

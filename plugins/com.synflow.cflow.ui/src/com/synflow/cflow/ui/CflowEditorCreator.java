/*******************************************************************************
 * Copyright (c) 2012-2013 Synflow SAS.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Matthieu Wipliez - initial API and implementation and/or initial documentation
 *******************************************************************************/
package com.synflow.cflow.ui;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.ui.editor.embedded.EmbeddedEditor;
import org.eclipse.xtext.ui.editor.embedded.EmbeddedEditorFactory;
import org.eclipse.xtext.ui.editor.embedded.IEditedResourceProvider;
import org.eclipse.xtext.ui.resource.IResourceSetProvider;

import com.google.inject.Inject;
import com.synflow.core.SynflowCore;
import com.synflow.models.dpn.Transition;

/**
 * This class defines a creator of C~ editors.
 * 
 * @author Matthieu Wipliez
 * 
 */
@SuppressWarnings("restriction")
public class CflowEditorCreator {

	private class CflowEditedResourceProvider implements
			IEditedResourceProvider {

		private final IFile file;

		public CflowEditedResourceProvider(IFile file) {
			this.file = file;
		}

		@Override
		public XtextResource createResource() {
			IProject project = file.getProject();
			ResourceSet resourceSet = resourceSetProvider.get(project);
			String path = file.getFullPath().toString();
			URI uri = URI.createPlatformResourceURI(path, true);
			return (XtextResource) resourceSet.getResource(uri, true);
		}
	}

	private static CflowEditorCreator instance;

	/**
	 * Returns the instance of a C~ editor creator.
	 * 
	 * @return the instance of a C~ editor creator.
	 */
	public static CflowEditorCreator get() {
		if (instance == null) {
			CflowExecutableExtensionFactory factory = new CflowExecutableExtensionFactory();
			try {
				factory.setInitializationData(null, null,
						CflowEditorCreator.class.getName());
				instance = (CflowEditorCreator) factory.create();
			} catch (CoreException e) {
				SynflowCore.log(e);
			}
		}
		return instance;
	}

	@Inject
	private EmbeddedEditorFactory editorFactory;

	@Inject
	private IResourceSetProvider resourceSetProvider;

	/**
	 * Creates an editor on the given composite.
	 * 
	 * @param file
	 *            file to open
	 * @param from
	 *            from
	 * @param to
	 *            to
	 * @param parent
	 *            parent composite
	 * @return control of the editor
	 */
	public Control createEditor(IFile file, Transition transition, Composite parent) {
		SourceViewerFactory.style = SWT.NONE;
		EmbeddedEditor embeddedEditor = editorFactory
				.newEditor(new CflowEditedResourceProvider(file)).readOnly()
				.withParent(parent);
		SourceViewerFactory.style = null;

		String content = "";
		try {
			Path path = Paths.get(file.getLocationURI());
			Charset cs = Charset.forName("UTF-8");
			List<String> lines = Files.readAllLines(path, cs);

			StringBuilder result = new StringBuilder();
			for (int lineNumber : transition.getLines()) {
				String line = lines.get(lineNumber - 1).trim();
				if (!line.isEmpty()) {
					result.append(line);
					result.append(" /* line " + lineNumber + " */\n");
				}
			}

			content = result.toString();
		} catch (IOException e) {
			SynflowCore.log(e);
		}

		// set content
		embeddedEditor.createPartialEditor("", content, "", true);

		return embeddedEditor.getViewer().getControl();
	}

}

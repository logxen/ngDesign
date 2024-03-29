/*******************************************************************************
 * Copyright (c) 2012 Synflow SAS.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Matthieu Wipliez - initial API and implementation and/or initial documentation
 *******************************************************************************/
/*
 * Copyright (c) 2010, IETR/INSA of Rennes
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   * Neither the name of the IETR/INSA of Rennes nor the names of its
 *     contributors may be used to endorse or promote products derived from this
 *     software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package com.synflow.ui.internal;

import static com.synflow.core.ISynflowConstants.FILE_EXT_CFLOW;

import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.FilteredItemsSelectionDialog;
import org.eclipse.ui.dialogs.SearchPattern;

import com.synflow.models.util.OrccUtil;

/**
 * This class defines a custom filtered items selection dialog.
 * 
 * @author Matthieu Wipliez
 * 
 */
public class FilteredRefinementDialog extends FilteredItemsSelectionDialog {

	private class EntityLabelProvider extends LabelProvider {

		@Override
		public String getText(Object element) {
			return getElementName(element);
		}

	}

	/**
	 * This class defines a comparator.
	 * 
	 * @author Matthieu Wipliez
	 * 
	 */
	private class ResourceComparator implements Comparator<Object> {

		@Override
		public int compare(Object o1, Object o2) {
			return getElementName(o1).compareTo(getElementName(o2));
		}

	}

	/**
	 * This class defines a filter.
	 * 
	 * @author Matthieu Wipliez
	 * 
	 */
	private class ResourceFilter extends ItemsFilter {

		public ResourceFilter() {
			super(new SearchPattern(SearchPattern.RULE_PATTERN_MATCH));

			// update pattern to look for anything before and after the original
			// pattern
			String pattern = patternMatcher.getPattern();
			pattern = "*" + pattern + "*";
			patternMatcher.setPattern(pattern);
		}

		@Override
		public boolean isConsistentItem(Object item) {
			return true;
		}

		@Override
		public boolean matchItem(Object item) {
			String name = getElementName(item);
			return matches(name);
		}

	}

	private class ResourceVisitor implements IResourceVisitor {

		private AbstractContentProvider contentProvider;

		private ItemsFilter itemsFilter;

		public ResourceVisitor(AbstractContentProvider contentProvider,
				ItemsFilter itemsFilter) {
			this.contentProvider = contentProvider;
			this.itemsFilter = itemsFilter;
		}

		@Override
		public boolean visit(IResource resource) throws CoreException {
			if (resource.getType() == IResource.FILE) {
				String fileExt = resource.getFileExtension();
				if (FILE_EXT_CFLOW.equals(fileExt)) {
					contentProvider.add(resource, itemsFilter);
				}
			}

			return true;
		}
	}

	private static final String DIALOG_SETTINGS = "com.synflow.ui.internal.FilteredRefinementDialog"; //$NON-NLS-1$

	private ResourceComparator comparator;

	private IProject project;

	/**
	 * Creates a new filtered actors dialog.
	 * 
	 * @param project
	 * @param shell
	 */
	public FilteredRefinementDialog(IProject project, Shell shell) {
		super(shell);
		this.project = project;
		comparator = new ResourceComparator();

		ILabelProvider provider = new EntityLabelProvider();
		setListLabelProvider(provider);
		setDetailsLabelProvider(provider);
	}

	@Override
	protected Control createExtendedContentArea(Composite parent) {
		// do nothing here
		return null;
	}

	@Override
	protected ItemsFilter createFilter() {
		return new ResourceFilter();
	}

	@Override
	protected void fillContentProvider(AbstractContentProvider contentProvider,
			ItemsFilter itemsFilter, IProgressMonitor progressMonitor)
			throws CoreException {
		List<IFolder> folders = OrccUtil.getAllSourceFolders(project);

		for (IFolder srcFolder : folders) {
			if (srcFolder.exists()) {
				srcFolder.accept(new ResourceVisitor(contentProvider,
						itemsFilter));
			}
		}
	}

	@Override
	protected IDialogSettings getDialogSettings() {
		IDialogSettings settings = SynflowUi.getDefault().getDialogSettings()
				.getSection(DIALOG_SETTINGS);

		if (settings == null) {
			settings = SynflowUi.getDefault().getDialogSettings()
					.addNewSection(DIALOG_SETTINGS);
		}

		return settings;
	}

	@Override
	public String getElementName(Object item) {
		if (item == null) {
			return null;
		}

		String name = "TODO"; //TODO
		return name;
	}

	@Override
	protected Comparator<?> getItemsComparator() {
		return comparator;
	}

	@Override
	protected IStatus validateItem(Object item) {
		return Status.OK_STATUS;
	}

}

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
package com.synflow.ui.internal;

import org.osgi.framework.Bundle;

import com.synflow.core.InjectableExtensionFactory;

/**
 * This class defines a factory that injects objects. This needs to be redefined
 * here to use this bundle to load classes defined in this plug-in.
 * 
 * @author Matthieu Wipliez
 * 
 */
public class UiInjectableExtensionFactory extends InjectableExtensionFactory {

	@Override
	protected Bundle getBundle() {
		return SynflowUi.getDefault().getBundle();
	}

}

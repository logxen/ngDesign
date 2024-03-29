/*******************************************************************************
 * Copyright (c) 2014 Synflow SAS.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Matthieu Wipliez - initial API and implementation and/or initial documentation
 *******************************************************************************/
package com.synflow.cflow.internal.instantiation.properties;

import static com.google.common.collect.Iterables.concat;
import static com.synflow.core.IProperties.IMPL_BUILTIN;
import static com.synflow.core.IProperties.IMPL_EXTERNAL;
import static com.synflow.core.IProperties.PROP_TYPE;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.synflow.cflow.internal.services.ExpressionTransformer;
import com.synflow.core.util.CoreUtil;
import com.synflow.models.dpn.Argument;
import com.synflow.models.dpn.DpnFactory;
import com.synflow.models.dpn.Entity;
import com.synflow.models.dpn.Instance;
import com.synflow.models.ir.Expression;
import com.synflow.models.ir.Var;

/**
 * This class specializes an entity based on the properties of an instance.
 * 
 * @author Matthieu Wipliez
 *
 */
public class Specializer {

	private IJsonErrorHandler errorHandler;

	public Specializer(IJsonErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	private void addArguments(Entity entity, Instance instance) {
		JsonObject properties = instance.getProperties();
		List<Var> parameters = new ArrayList<>();
		for (Var variable : concat(entity.getParameters(), entity.getVariables())) {
			if (!variable.isAssignable()) {
				String name = variable.getName();
				JsonElement jsonValue = properties.get(name);
				if (jsonValue == null) {
					if (!variable.isInitialized()) {
						errorHandler.addError(properties,
								"Instantiation: missing value for constant '" + name + "'");
					}
				} else {
					parameters.add(variable);

					Expression value = new ExpressionTransformer(null).transformJson(jsonValue);
					if (value == null) {
						errorHandler.addError(jsonValue,
								"Instantiation: invalid value for constant '" + name + "'");
					} else {
						Argument argument = DpnFactory.eINSTANCE.createArgument(variable, value);
						instance.getArguments().add(argument);
					}
				}
			}
		}

		entity.getParameters().addAll(parameters);
	}

	public void visitArguments(Instance instance) {
		Entity entity = instance.getEntity();
		JsonObject impl = CoreUtil.getImplementation(entity);
		if (impl != null) {
			JsonElement type = impl.get(PROP_TYPE);
			if (IMPL_BUILTIN.equals(type) || IMPL_EXTERNAL.equals(type)) {
				addArguments(entity, instance);
			}
		}
	}

}

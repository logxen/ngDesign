/*******************************************************************************
 * Copyright (c) 2012-2014 Synflow SAS.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Matthieu Wipliez - initial API and implementation and/or initial documentation
 *******************************************************************************/
package com.synflow.cflow;

import static com.synflow.cflow.CflowConstants.DIR_IN;
import static com.synflow.models.util.SwitchUtil.DONE;
import static com.synflow.models.util.SwitchUtil.visit;
import static org.eclipse.xtext.EcoreUtil2.getContainerOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.synflow.cflow.cflow.CExpression;
import com.synflow.cflow.cflow.CType;
import com.synflow.cflow.cflow.CflowPackage.Literals;
import com.synflow.cflow.cflow.ExpressionVariable;
import com.synflow.cflow.cflow.Module;
import com.synflow.cflow.cflow.MultiPortDecl;
import com.synflow.cflow.cflow.PortDecl;
import com.synflow.cflow.cflow.PortDef;
import com.synflow.cflow.cflow.SinglePortDecl;
import com.synflow.cflow.cflow.StatementAssign;
import com.synflow.cflow.cflow.StatementIf;
import com.synflow.cflow.cflow.StatementLoop;
import com.synflow.cflow.cflow.StatementVariable;
import com.synflow.cflow.cflow.Task;
import com.synflow.cflow.cflow.VarDecl;
import com.synflow.cflow.cflow.Variable;
import com.synflow.cflow.cflow.util.CflowSwitch;
import com.synflow.cflow.internal.services.LoopSwitch;
import com.synflow.cflow.internal.services.ScheduleModifierSwitch;
import com.synflow.models.dpn.InterfaceType;
import com.synflow.models.util.Void;

/**
 * This class defines utility functions for analysis of C~.
 * 
 * @author Matthieu Wipliez
 * 
 */
public class CflowUtil {

	private static class PortSwitch extends CflowSwitch<Void> {

		private List<Variable> variables;

		public PortSwitch() {
			variables = new ArrayList<Variable>();
		}

		@Override
		public Void caseMultiPortDecl(MultiPortDecl decl) {
			return visit(this, decl.getDecls());
		}

		@Override
		public Void casePortDef(PortDef portDef) {
			variables.add(portDef.getVar());
			return DONE;
		}

		@Override
		public Void caseSinglePortDecl(SinglePortDecl decl) {
			return visit(this, decl.getPorts());
		}

		public Iterable<Variable> getVariables() {
			return variables;
		}

	}

	/**
	 * This class defines a switch that visits a function to find out if it has side effects. A side
	 * effect is any action on a port, any cycle modifier, and any assignment to state variables.
	 * 
	 * @author Matthieu Wipliez
	 * 
	 */
	public static class SideEffectSwitch extends ScheduleModifierSwitch {

		@Override
		public Boolean caseStatementAssign(StatementAssign stmt) {
			ExpressionVariable target = stmt.getTarget();
			Variable variable = target.getSource().getVariable();
			if (isGlobal(variable) && stmt.getOp() != null) {
				// target of this assignment is a state variable => side effect
				return true;
			}

			return super.caseStatementAssign(stmt);
		}

	}

	private static Function<VarDecl, Iterable<Variable>> funVarDecl = new Function<VarDecl, Iterable<Variable>>() {
		@Override
		public Iterable<Variable> apply(VarDecl varDecl) {
			return varDecl.getVariables();
		}
	};

	/**
	 * Finds the last port declaration before <code>portDef</code> that satisfies the given
	 * predicate.
	 * 
	 * @param portDef
	 *            a port definition
	 * @param predicate
	 *            a predicate
	 * @return a port definition that satisfies the given predicate
	 */
	private static PortDef find(PortDef portDef, Predicate<PortDef> predicate) {
		SinglePortDecl portDecl = (SinglePortDecl) portDef.eContainer();
		List<PortDef> defs = portDecl.getPorts();

		int index = ECollections.indexOf(defs, portDef, 0);
		ListIterator<PortDef> it = defs.listIterator(index + 1);
		while (it.hasPrevious()) {
			PortDef previous = it.previous();
			if (predicate.apply(previous)) {
				return previous;
			}
		}
		return null;
	}

	/**
	 * Returns the direction of the given port.
	 * 
	 * @param port
	 *            a port variable
	 * @return the direction of the given port
	 */
	public static String getDirection(Variable port) {
		EObject cter = port.eContainer();
		if (cter == null) {
			return null;
		}

		SinglePortDecl decl = (SinglePortDecl) cter.eContainer();
		if (decl == null) {
			return null;
		}
		return decl.getDirection();
	}

	/**
	 * Returns the file name of this module.
	 * 
	 * @param module
	 *            a module
	 * @return the module's file name
	 */
	public static String getFileName(Module module) {
		URI uri = module.eResource().getURI();
		if (uri.isPlatform()) {
			return uri.toPlatformString(true);
		} else {
			return uri.path();
		}
	}

	/**
	 * Returns the function in the given module that has the given name, or <code>null</code>.
	 * 
	 * @param module
	 *            a module
	 * @param name
	 *            name of a function
	 * @return a function, or <code>null</code>
	 */
	public static Variable getFunction(Task task, final String name) {
		return Iterables.find(getFunctions(task.getDecls()), new Predicate<Variable>() {
			@Override
			public boolean apply(Variable variable) {
				return name.equals(variable.getName());
			}
		}, null);
	}

	/**
	 * Returns an iterable of variables from an iterable of port declarations.
	 * 
	 * @param portDecls
	 *            an iterable of port declarations
	 * @return an iterable of variables that represent ports
	 */
	public static Iterable<Variable> getFunctions(Iterable<VarDecl> varDecls) {
		return Iterables.filter(Iterables.concat(Iterables.transform(varDecls, funVarDecl)),
				new Predicate<Variable>() {
					@Override
					public boolean apply(Variable variable) {
						return CflowUtil.isFunction(variable);
					}
				});
	}

	/**
	 * Returns the interface type (bare, sync, sync ready, sync ack) of the given port variable.
	 * 
	 * @param port
	 *            a variable that belongs to a port definition
	 * @return an interface type
	 */
	public static InterfaceType getInterface(Variable port) {
		PortDef def = (PortDef) port.eContainer();
		EObject cter = def.eContainer().eContainer();
		MultiPortDecl decl = cter instanceof MultiPortDecl ? (MultiPortDecl) cter : null;

		if (def.isAck() || decl != null && decl.isAck()) {
			return InterfaceType.SYNC_ACK;
		} else if (def.isReady() || decl != null && decl.isReady()) {
			return InterfaceType.SYNC_READY;
		} else if (def.isSync() || decl != null && decl.isSync()) {
			return InterfaceType.SYNC;
		}

		return InterfaceType.BARE;
	}

	/**
	 * Returns an iterable of variables from an iterable of port declarations.
	 * 
	 * @param portDecls
	 *            an iterable of port declarations
	 * @return an iterable of variables that represent ports
	 */
	public static Iterable<Variable> getPorts(Iterable<PortDecl> portDecls) {
		PortSwitch portSwitch = new PortSwitch();
		visit(portSwitch, portDecls);
		return portSwitch.getVariables();
	}

	/**
	 * Returns all ports that match the given direction.
	 * 
	 * @param portDecls
	 *            an iterable of port declarations
	 * @param direction
	 *            a direction
	 * @return an iterable of port variables
	 */
	public static Iterable<Variable> getPorts(Iterable<PortDecl> portDecls, final String direction) {
		return Iterables.filter(getPorts(portDecls), new Predicate<Variable>() {
			@Override
			public boolean apply(Variable port) {
				return port.getName() != null && direction.equals(getDirection(port));
			}
		});
	}

	public static Iterable<Variable> getStateVars(Iterable<VarDecl> stateVars) {
		List<Variable> variables = new ArrayList<Variable>();
		for (VarDecl vars : stateVars) {
			variables.addAll(vars.getVariables());
		}
		return variables;
	}

	/**
	 * Returns the target of the expression, which is defined as the first object in the containment
	 * hierarchy that is not an expression.
	 * 
	 * @param expression
	 *            an expression
	 * @return an EObject
	 */
	public static EObject getTarget(CExpression expression) {
		EObject result = expression;
		do {
			result = result.eContainer();
		} while (result instanceof CExpression);
		return result;
	}

	/**
	 * Returns the type of the given variable. If the variable itself does not have a type, and it
	 * is contained in a local or state variable declaration, the type of its container is returned.
	 * Otherwise this method returns <code>null</code> (should never happen).
	 * 
	 * @param variable
	 *            a variable
	 * @return type of the variable
	 */
	public static CType getType(Variable variable) {
		CType type = variable.getType();
		if (type != null) {
			// parameters of a function are variables with a type
			return type;
		}

		EObject cter = variable.eContainer();
		if (cter instanceof StatementVariable) {
			return ((StatementVariable) cter).getType();
		} else if (cter instanceof VarDecl) {
			return ((VarDecl) cter).getType();
		} else if (cter instanceof PortDef) {
			PortDef decl = find((PortDef) cter, new Predicate<PortDef>() {
				@Override
				public boolean apply(PortDef PortDef) {
					return PortDef.getVar().getType() != null;
				}
			});

			if (decl != null) {
				return decl.getVar().getType();
			}
		}

		// cter will be null if variable is a proxy
		// happens when an expression/statement references a variable
		// that is not defined in its scope
		return null;
	}

	/**
	 * Returns true if the given object has side effects (performs reads, writes, or assigns to
	 * state variables).
	 * 
	 * @param object
	 *            an AST node (function, statement, expression)
	 * @return a boolean indicating if the given object has side effects
	 */
	public static boolean hasSideEffects(EObject object) {
		return new SideEffectSwitch().doSwitch(object);
	}

	public static boolean isConstant(Variable variable) {
		EObject cter = variable.eContainer();
		if (cter instanceof StatementVariable) {
			return ((StatementVariable) cter).isConstant();
		} else if (cter instanceof VarDecl) {
			VarDecl stateVars = (VarDecl) cter;
			if (stateVars.isConstant()) {
				return true;
			}

			// variables in bundles/networks are implicitly constant
			// the variable is thus constant unless contained in a task
			Task task = getContainerOfType(stateVars, Task.class);
			return task == null;
		} else {
			// function parameters are constant
			EStructuralFeature feature = variable.eContainingFeature();
			return feature == Literals.VARIABLE__PARAMETERS;
		}
	}

	/**
	 * Returns true if this variable is a function, i.e. if it has a body.
	 * 
	 * @param variable
	 *            a variable
	 * @return a boolean
	 */
	public static boolean isFunction(Variable variable) {
		return variable.getBody() != null;
	}

	/**
	 * Returns true if this variable is a constant function.
	 * 
	 * @param variable
	 *            a variable
	 * @return a boolean
	 */
	public static boolean isFunctionConstant(Variable variable) {
		if (isFunction(variable)) {
			return isConstant(variable);
		}
		return false;
	}

	/**
	 * Returns true if this variable is a function and has side-effects.
	 * 
	 * @param variable
	 *            a variable
	 * @return a boolean
	 */
	public static boolean isFunctionNotConstant(Variable variable) {
		if (isFunction(variable)) {
			return !isConstant(variable);
		}
		return false;
	}

	/**
	 * Returns true if the variable is global.
	 * 
	 * @param variable
	 *            a variable
	 * @return <code>true</code> if the variable is global
	 */
	public static boolean isGlobal(Variable variable) {
		return variable.eContainer() instanceof VarDecl;
	}

	/**
	 * Returns <code>true</code> if the given if statement can be translated as a simple 'if' IR
	 * statement, which is only the case when the statement cannot have any influence on the
	 * schedule.
	 * 
	 * @param stmt
	 *            if statement
	 * @return boolean indicating if the 'if' can be translated in a simple way or not
	 */
	public static boolean isIfSimple(StatementIf stmt) {
		return !new ScheduleModifierSwitch().doSwitch(stmt);
	}

	/**
	 * Returns <code>true</code> if the given port is an input port.
	 * 
	 * @param port
	 *            a port declaration
	 * @return <code>true</code> if the given port is an input port
	 */
	public static boolean isInput(Variable port) {
		return DIR_IN.equals(getDirection(port));
	}

	/**
	 * Returns true if the variable is a local variable declaration.
	 * 
	 * @param variable
	 *            a variable
	 * @return <code>true</code> if the variable is local
	 */
	public static boolean isLocal(Variable variable) {
		EObject cter = variable.eContainer();
		return cter instanceof StatementVariable;
	}

	/**
	 * Returns <code>true</code> if the given loop statement can be translated as a simple 'loop' IR
	 * statement, which is only the case when the loop cannot have any influence on the schedule and
	 * has a compile-time known number of iterations.
	 * 
	 * @param stmt
	 *            loop statement
	 * @return boolean indicating if the loop can be translated in a simple way or not
	 */
	public static boolean isLoopSimple(StatementLoop stmt) {
		return !new LoopSwitch().doSwitch(stmt);
	}

	/**
	 * Returns <code>true</code> if the given variable is a port.
	 * 
	 * @param variable
	 *            a variable
	 * @return <code>true</code> if the given variable is a port
	 */
	public static boolean isPort(Variable variable) {
		return variable.eContainer() instanceof PortDef;
	}

	public static boolean isVarDecl(Variable variable) {
		return variable.eContainer() instanceof VarDecl;
	}

	/**
	 * Returns true if this function has "void" return type.
	 * 
	 * @param function
	 *            a function
	 * @return true if this function returns void
	 */
	public static boolean isVoid(Variable function) {
		VarDecl decl = (VarDecl) function.eContainer();
		return decl.isVoid();
	}

}

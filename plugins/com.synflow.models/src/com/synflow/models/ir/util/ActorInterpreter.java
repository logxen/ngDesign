/*
 * Copyright (c) 2009-2011, IETR/INSA of Rennes
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
package com.synflow.models.ir.util;

import static java.math.BigInteger.ONE;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature.Setting;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com.synflow.models.OrccRuntimeException;
import com.synflow.models.dpn.Action;
import com.synflow.models.dpn.Actor;
import com.synflow.models.dpn.Pattern;
import com.synflow.models.dpn.Port;
import com.synflow.models.dpn.State;
import com.synflow.models.dpn.Transition;
import com.synflow.models.graph.Edge;
import com.synflow.models.ir.Block;
import com.synflow.models.ir.BlockBasic;
import com.synflow.models.ir.BlockIf;
import com.synflow.models.ir.BlockWhile;
import com.synflow.models.ir.ExprString;
import com.synflow.models.ir.Expression;
import com.synflow.models.ir.InstAssign;
import com.synflow.models.ir.InstCall;
import com.synflow.models.ir.InstLoad;
import com.synflow.models.ir.InstPhi;
import com.synflow.models.ir.InstReturn;
import com.synflow.models.ir.InstStore;
import com.synflow.models.ir.Instruction;
import com.synflow.models.ir.Procedure;
import com.synflow.models.ir.Type;
import com.synflow.models.ir.TypeArray;
import com.synflow.models.ir.TypeInt;
import com.synflow.models.ir.Var;
import com.synflow.models.util.EcoreHelper;
import com.synflow.models.util.OrccUtil;

/**
 * This class defines an interpreter for an actor. The interpreter can
 * {@link #initialize()} and {@link #schedule()} the actor.
 * 
 * @author Pierre-Laurent Lagalaye
 * @author Matthieu Wipliez
 * 
 */
public class ActorInterpreter extends IrSwitch<Object> {

	/** the associated interpreted actor */
	protected Actor actor;
	/** branch being visited */
	protected int branch;
	/** the expression evaluator */
	protected ExpressionEvaluator exprInterpreter;
	/** Actor's FSM current state */
	protected State fsmState;

	/**
	 * Creates a new interpreter without any associated {@link Actor}
	 */
	public ActorInterpreter() {
		this.exprInterpreter = new ExpressionEvaluator();
	}

	/**
	 * Creates a new interpreter.
	 * 
	 * @param actor
	 *            the actor to interpret
	 */
	public ActorInterpreter(Actor actor) {
		this();
		setActor(actor);
	}

	/**
	 * Allocates the variables of the given pattern.
	 * 
	 * @param pattern
	 *            a pattern
	 */
	final protected void allocatePattern(Pattern pattern) {
		for (Port port : pattern.getPorts()) {
			Object value = ValueUtil.createArray((TypeArray) port.getType());
			port.setValue(value);
		}
	}

	/**
	 * Calls the print procedure. Prints to stdout by default. This method may
	 * be overridden.
	 * 
	 * @param arguments
	 *            arguments of the print
	 */
	protected void callPrintProcedure(List<Expression> arguments) {
		for (Expression expr : arguments) {
			if (expr.isExprString()) {
				// String characters rework for escaped control
				// management
				String str = ((ExprString) expr).getValue();
				String unescaped = OrccUtil.getUnescapedString(str);
				System.out.println(unescaped);
			} else {
				Object value = exprInterpreter.doSwitch(expr);
				System.out.println(String.valueOf(value));
			}
		}
	}

	@Override
	public Object caseBlockBasic(BlockBasic block) {
		List<Instruction> instructions = block.getInstructions();
		for (Instruction instruction : instructions) {
			Object result = doSwitch(instruction);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	@Override
	public Object caseBlockIf(BlockIf block) {
		// Interpret first expression ("if" condition)
		Object condition = exprInterpreter.doSwitch(block.getCondition());

		Object ret;
		// if (condition is true)
		if (ValueUtil.isBool(condition)) {
			int oldBranch = branch;
			if (ValueUtil.isTrue(condition)) {
				doSwitch(block.getThenBlocks());
				branch = 0;
			} else {
				doSwitch(block.getElseBlocks());
				branch = 1;
			}

			ret = doSwitch(block.getJoinBlock());
			branch = oldBranch;
		} else {
			throw new OrccRuntimeException("Condition "
					+ new ExpressionPrinter().toString(block.getCondition())
					+ " not boolean at line " + block.getLineNumber());
		}
		return ret;
	}

	@Override
	public Object caseBlockWhile(BlockWhile block) {
		int oldBranch = branch;
		branch = 0;
		doSwitch(block.getJoinBlock());

		// Interpret first expression ("while" condition)
		Object condition = exprInterpreter.doSwitch(block.getCondition());

		// while (condition is true) do
		branch = 1;
		while (ValueUtil.isTrue(condition)) {
			doSwitch(block.getBlocks());
			doSwitch(block.getJoinBlock());

			// Interpret next value of "while" condition
			condition = exprInterpreter.doSwitch(block.getCondition());
		}

		branch = oldBranch;
		return null;
	}

	@Override
	public Object caseInstAssign(InstAssign instr) {
		Var target = instr.getTarget().getVariable();
		Object value = exprInterpreter.doSwitch(instr.getValue());
		value = clipValue(target.getType(), value, instr);
		try {
			target.setValue(value);
		} catch (OrccRuntimeException e) {
			String file = actor.getFileName();
			throw new OrccRuntimeException(file, instr.getLineNumber(), "", e);
		}
		return null;
	}

	@Override
	public Object caseInstCall(InstCall call) {
		// Get called procedure
		Procedure proc = call.getProcedure();

		// Set the input parameters of the called procedure if any
		List<Expression> callParams = call.getArguments();

		// Special "print" case
		if (call.isPrint()) {
			callPrintProcedure(callParams);
		} else {
			List<Var> procParams = proc.getParameters();
			for (int i = 0; i < callParams.size(); i++) {
				Var procVar = procParams.get(i);
				Expression value = callParams.get(i);
				procVar.setValue(exprInterpreter.doSwitch(value));
			}

			// Interpret procedure body
			Object result = doSwitch(proc);
			if (call.hasResult()) {
				call.getTarget().getVariable().setValue(result);
			}
		}
		return null;
	}

	@Override
	public Object caseInstLoad(InstLoad instr) {
		Var target = instr.getTarget().getVariable();
		Var source = instr.getSource().getVariable();
		if (instr.getIndexes().isEmpty()) {
			target.setValue(source.getValue());
		} else {
			Object array = source.getValue();
			Object[] indexes = new Object[instr.getIndexes().size()];
			int i = 0;
			for (Expression index : instr.getIndexes()) {
				indexes[i++] = exprInterpreter.doSwitch(index);
			}
			Type type = ((TypeArray) source.getType()).getElementType();
			try {
				Object value = ValueUtil.get(type, array, indexes);
				target.setValue(value);
			} catch (IndexOutOfBoundsException e) {
				throw new OrccRuntimeException(
						"Array Index Out of Bound at line "
								+ instr.getLineNumber());
			}
		}
		return null;
	}

	@Override
	public Object caseInstPhi(InstPhi phi) {
		Expression value = phi.getValues().get(branch);
		phi.getTarget().getVariable().setValue(exprInterpreter.doSwitch(value));
		return null;
	}

	@Override
	public Object caseInstReturn(InstReturn instr) {
		if (instr.getValue() == null) {
			return null;
		}
		return exprInterpreter.doSwitch(instr.getValue());
	}

	@Override
	public Object caseInstStore(InstStore instr) {
		Var target = instr.getTarget().getVariable();
		Object value = exprInterpreter.doSwitch(instr.getValue());
		if (instr.getIndexes().isEmpty()) {
			value = clipValue(target.getType(), value, instr);
			target.setValue(value);
		} else {
			Object array = target.getValue();
			Object[] indexes = new Object[instr.getIndexes().size()];
			int i = 0;
			for (Expression index : instr.getIndexes()) {
				indexes[i++] = exprInterpreter.doSwitch(index);
			}

			Type type = ((TypeArray) target.getType()).getElementType();
			value = clipValue(type, value, instr);
			try {
				ValueUtil.set(type, array, value, indexes);
			} catch (IndexOutOfBoundsException e) {
				throw new OrccRuntimeException(
						"Array Index Out of Bound at line "
								+ instr.getLineNumber() + "");
			}
		}
		return null;
	}

	@Override
	public Object caseProcedure(Procedure procedure) {

		// Allocate local List variables
		for (Var local : procedure.getLocals()) {
			Type type = local.getType();
			if (type.isArray()) {
				Object value = ValueUtil
						.createArray((TypeArray) local.getType());
				local.setValue(value);
			}
		}

		return doSwitch(procedure.getBlocks());
	}

	/**
	 * Returns true if the action has no output pattern, or if it has an output
	 * pattern and there is enough room in the FIFOs to satisfy it.
	 * 
	 * @param outputPattern
	 *            output pattern of an action
	 * @return true if the pattern is empty or satisfiable
	 */
	protected boolean checkOutputPattern(Pattern outputPattern) {
		return true;
	}

	/**
	 * Returns a new value clipped to:
	 * <ul>
	 * <li>[-2<sup>n-1</sup>; 2<sup>n-1</sup> - 1] if type is int (size=n)</li>
	 * <li>[0; 2<sup>n-1</sup>] if type is uint (size=n)</li>
	 * </ul>
	 * Prints a warning in case of signed overflow/underflow.
	 * 
	 * @param type
	 *            type of the target variable
	 * @param value
	 *            a value
	 * @param instruction
	 *            the instruction
	 * @return the original value or a new value
	 */
	protected Object clipValue(Type type, Object value, Instruction instruction) {
		if (!ValueUtil.isInt(value)) {
			return value;
		}
		BigInteger intVal = (BigInteger) value;

		// converts to unsigned n-bit number
		int n = ((TypeInt) type).getSize();
		BigInteger twoPowSize = ONE.shiftLeft(n);
		BigInteger clippedValue = intVal.and(twoPowSize.subtract(ONE));

		// check signed overflow/underflow
		if (type.isInt()) {
			// if MSB is set, subtract 2**n to make negative number
			if (clippedValue.testBit(n - 1)) {
				clippedValue = clippedValue.subtract(twoPowSize);
			}

			if (!clippedValue.equals(intVal)) {

				String container = "";
				Action parentAction = EcoreHelper.getContainerOfType(
						instruction, Action.class);
				if (parentAction != null) {
					container = parentAction.getName();
				} else if (EcoreHelper.getContainerOfType(instruction,
						Procedure.class) != null) {
					container = EcoreHelper.getContainerOfType(instruction,
							Procedure.class).getName();
				}

				System.err.println("[signed overflow/underflow] "
						+ actor.getName() + ":" + container + " line: "
						+ instruction.getLineNumber());
			}
		}

		return clippedValue;
	}

	/**
	 * Visits the blocks of the given block list.
	 * 
	 * @param blocks
	 *            a list of blocks that belong to a procedure
	 */
	protected Object doSwitch(List<Block> blocks) {
		for (Block block : blocks) {
			Object result = doSwitch(block);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	/**
	 * Executes the given action. This implementation allocates input/output
	 * pattern and executes the body. Should be overriden by implementations to
	 * perform read/write from/to FIFOs.
	 * 
	 * @param action
	 *            an action
	 */
	protected void execute(Action action) {
		Pattern input = action.getInputPattern();
		Pattern output = action.getOutputPattern();
		allocatePattern(input);
		allocatePattern(output);

		doSwitch(action.getBody());
	}

	/**
	 * Returns the value of the <code>actor</code> attribute. This may be
	 * <code>null</code> if the visitor did not set it.
	 * 
	 * @return the value of the <code>actor</code> attribute
	 */
	final public Actor getActor() {
		return actor;
	}

	/**
	 * Returns the current FSM state.
	 * 
	 * @return the current FSM state
	 */
	public final State getFsmState() {
		return fsmState;
	}

	/**
	 * Get the next schedulable action to be executed for this actor
	 * 
	 * @return the schedulable action or null
	 */
	public Action getNextAction() {
		// Check next schedulable action in respect of the priority order
		for (Action action : actor.getActionsOutsideFsm()) {
			if (isSchedulable(action)) {
				if (checkOutputPattern(action.getOutputPattern())) {
					return action;
				}
			}
		}

		if (actor.hasFsm()) {
			// Then check for next FSM transition
			for (Edge edge : fsmState.getOutgoing()) {
				Transition transition = (Transition) edge;
				Action action = transition.getAction();
				if (isSchedulable(action)) {
					// Update FSM state
					if (checkOutputPattern(action.getOutputPattern())) {
						fsmState = transition.getTarget();
						return action;
					}
					return null;
				}
			}
		}

		return null;
	}

	/**
	 * Initializes external resources referenced by an object (actor, procedure,
	 * etc.)
	 * 
	 * @param obj
	 *            an EObject which potentially use external variables or
	 *            procedures
	 */
	private void initExternalResources(EObject obj) {
		Map<EObject, Collection<Setting>> map = EcoreUtil.ExternalCrossReferencer
				.find(obj);
		for (EObject externalObject : map.keySet()) {
			if (externalObject instanceof Var
					&& ((Var) externalObject).getValue() == null) {
				initializeVar((Var) externalObject);
			} else if (externalObject instanceof Procedure) {
				initExternalResources(externalObject);
			}
		}
	}

	/**
	 * Initialize interpreted actor. That is to say constant parameters,
	 * initialized state variables, allocation and initialization of state
	 * arrays.
	 */
	public void initialize() {
		try {
			// initializes parameters
			for (Var var : actor.getParameters()) {
				initializeVar(var);
			}

			// initializes state variables
			for (Var stateVar : actor.getVariables()) {
				initializeVar(stateVar);
			}

			// initializes runtime value of constants declared in units
			initExternalResources(actor);

			// initializes FSM status (if any)
			if (actor.hasFsm()) {
				fsmState = actor.getFsm().getInitialState();
			} else {
				fsmState = null;
			}
		} catch (OrccRuntimeException ex) {
			throw new OrccRuntimeException("Runtime exception thrown by actor "
					+ actor.getName(), ex);
		}
	}

	/**
	 * Initializes the given variable.
	 * 
	 * @param variable
	 *            a variable
	 */
	protected void initializeVar(Var variable) {
		Type type = variable.getType();
		Expression initConst = variable.getInitialValue();
		if (initConst == null) {
			Object value;
			if (type.isBool()) {
				value = false;
			} else if (type.isFloat()) {
				value = BigDecimal.ZERO;
			} else if (type.isInt()) {
				value = BigInteger.ZERO;
			} else if (type.isArray()) {
				value = ValueUtil.createArray((TypeArray) type);
			} else if (type.isString()) {
				value = "";
			} else {
				value = null;
			}
			variable.setValue(value);
		} else {
			// evaluate initial constant value
			// TODO variable.setValue(evaluate list)
		}
	}

	/**
	 * Returns true if the given action is schedulable. This implementation
	 * allocates the peek pattern and calls the scheduler procedure. This method
	 * should be overridden to define how to test the schedulability of an
	 * action.
	 * 
	 * @param action
	 *            an action
	 * @return true if the given action is schedulable
	 */
	protected boolean isSchedulable(Action action) {
		Pattern pattern = action.getPeekPattern();
		allocatePattern(pattern);
		Object result = doSwitch(action.getScheduler());
		return ValueUtil.isTrue(result);
	}

	/**
	 * Schedule next schedulable action if any
	 * 
	 * @return <code>true</code> if an action was scheduled, <code>false</code>
	 *         otherwise
	 */
	public boolean schedule() {
		try {
			// "Synchronous-like" scheduling policy : schedule only 1 action per
			// actor at each "schedule" (network logical cycle) call
			Action action = getNextAction();
			if (action == null) {
				return false;
			} else {
				execute(action);
				return true;
			}
		} catch (OrccRuntimeException ex) {
			throw new OrccRuntimeException("Runtime exception thrown by actor "
					+ actor.getName(), ex);
		}
	}

	/**
	 * Sets the actor interpreter by this interpreter.
	 * 
	 * @param actor
	 *            an actor
	 */
	protected void setActor(Actor actor) {
		this.actor = actor;
	}

}

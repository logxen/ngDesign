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
package com.synflow.models.ir;

/**
 * This interface defines an Assign instruction. The target is a local variable, and the value an
 * expression.
 * 
 * @author Matthieu Wipliez
 * @model extends="net.sf.orcc.ir.Instruction"
 */
public interface InstAssign extends Instruction {

	/**
	 * Returns the target of this assignment.
	 * 
	 * @return the target of this assignment
	 * @model containment="true"
	 */
	Def getTarget();

	/**
	 * Returns the value of this assignment.
	 * 
	 * @return the value of this assignment
	 * @model containment="true"
	 */
	Expression getValue();

	/**
	 * Sets the target of this assignment.
	 * 
	 * @param target
	 *            a local variable
	 */
	void setTarget(Def target);

	/**
	 * Sets the value of this assignment. Uses are updated to point to this assignment.
	 * 
	 * @param value
	 *            an expression
	 */
	void setValue(Expression value);

}

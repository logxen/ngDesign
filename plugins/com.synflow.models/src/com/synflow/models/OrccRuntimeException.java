/*
 * Copyright (c) 2009, IETR/INSA of Rennes
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
package com.synflow.models;


/**
 * This class defines a runtime exception that can be raised in Orcc.
 * 
 * @author Matthieu Wipliez
 * 
 */
public class OrccRuntimeException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new exception with the given message.
	 */
	public OrccRuntimeException(String message) {
		super(message);
	}

	/**
	 * Creates a new exception with the given message, identified to have
	 * occurred in the given file at the given location.
	 */
	public OrccRuntimeException(int lineNumber, String message) {
		super(lineNumber + "\n" + message);
	}

	/**
	 * Creates a new exception with the given message, identified to have
	 * occurred in the given file at the given location.
	 */
	public OrccRuntimeException(String fileName, int lineNumber, String message) {
		super("File \"" + fileName + "\", " + lineNumber + "\n" + message);
	}

	/**
	 * Creates a new exception with the given message and cause, identified to
	 * have occurred in the given file at the given location.
	 */
	public OrccRuntimeException(String fileName, int lineNumber,
			String message, Throwable cause) {
		super("File \"" + fileName + "\", " + lineNumber + "\n" + message,
				cause);
	}

	/**
	 * Creates a new exception with the given message and cause.
	 */
	public OrccRuntimeException(String message, Throwable cause) {
		super(message, cause);
	}

}

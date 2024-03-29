/*******************************************************************************
 * Copyright (c) 2012 Synflow SAS, itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sven Efftinge - Initial contribution and API
 *    Matthieu Wipliez - modified to support C~
 *******************************************************************************/
package com.synflow.cflow.debug;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.debug.IStratumBreakpointSupport;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.resource.XtextResource;

import com.synflow.cflow.cflow.Statement;

/**
 * This class implements breakpoint support for C~ based on Xbase's breakpoint
 * support class.
 * 
 * @author Sven Efftinge - Initial contribution and API
 * @author Matthieu Wipliez support for C~
 */
@SuppressWarnings("restriction")
public class CflowStratumBreakpointSupport implements IStratumBreakpointSupport {

	public boolean isValidLineForBreakPoint(XtextResource resource, int line) {
		IParseResult parseResult = resource.getParseResult();
		if (parseResult == null)
			return false;
		ICompositeNode node = parseResult.getRootNode();
		return isValidLineForBreakpoint(node, line);
	}

	protected boolean isValidLineForBreakpoint(ICompositeNode node, int line) {
		for (INode n : node.getChildren()) {
			if (n.getStartLine() <= line && n.getEndLine() >= line) {
				EObject eObject = n.getSemanticElement();
				if (eObject instanceof Statement) {
					return true;
				}
				if (n instanceof ICompositeNode
						&& isValidLineForBreakpoint((ICompositeNode) n, line)) {
					return true;
				}
			}
			if (n.getStartLine() > line) {
				return false;
			}
		}
		return false;
	}

}

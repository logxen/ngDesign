/*******************************************************************************
 * Copyright (c) 2013 Synflow SAS.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Matthieu Wipliez - initial API and implementation and/or initial documentation
 *******************************************************************************/
package com.synflow.cflow.internal.scheduler.path;

import java.util.Iterator;
import java.util.ListIterator;

import com.synflow.cflow.cflow.StatementIf;
import com.synflow.cflow.internal.scheduler.node.Node;
import com.synflow.cflow.internal.scheduler.node.NodeIterator;

/**
 * This class defines an iterator of paths.
 * 
 * @author Matthieu Wipliez
 */
public class PathIterator extends Node implements Iterator<Path> {

	private NodeIterator childIt;

	public PathIterator(Node node) {
		this(null, node);
	}

	public PathIterator(PathIterator parent, Node node) {
		super(parent, node);

		childIt = new NodeIterator(node);

		updateIterators();
		childIt.reset();
	}

	private void current(Path path) {
		ListIterator<Node> lit = new NodeIterator(this);
		while (lit.hasPrevious()) {
			PathIterator it = (PathIterator) lit.previous();
			it.current(path);
		}

		path.add(childIt.current());
	}

	private Node getNextChildWithChildren() {
		if (childIt.hasNext()) {
			Node child = childIt.getNext();
			if (child.hasChildren()) {
				return child;
			}
		}
		return null;
	}

	/**
	 * Returns the reference node this path iterator was created with.
	 * 
	 * @return a node
	 */
	private Node getNode() {
		return (Node) getContent();
	}

	@Override
	public boolean hasNext() {
		// either this iterator has a child that has a next node
		if (hasNextChildren()) {
			return true;
		}

		// or this iterator is over a 'if' and it has a next child condition node
		return isIf(getNode()) && childIt.hasNext();
	}

	/**
	 * Returns <code>true</code> if the {@link #hasNext()} method of one of this path iterator's
	 * child iterator returns true.
	 * 
	 * @return if one this iterator's children has a next node
	 */
	private boolean hasNextChildren() {
		Iterator<Node> pit = new NodeIterator(this);
		while (pit.hasNext()) {
			PathIterator it = (PathIterator) pit.next();
			if (it.hasNext()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns <code>true</code> if the given node is a 'if' statement
	 * 
	 * @param node
	 *            a node
	 * @return a boolean indicating if the node is a 'if' statement
	 */
	private boolean isIf(Node node) {
		return node.getContent() instanceof StatementIf;
	}

	@Override
	public Path next() {
		while (true) {
			try {
				Path path = new Path();
				next(path);
				return path;
			} catch (PathResetException e) {
				// happens when we should reset walking the path
			}
		}
	}

	public void next(Path path) {
		if (!hasChildren()) {
			Node responsibleChild = getNextChildWithChildren();
			if (responsibleChild != null && new PathIterator(responsibleChild).hasNext()) {
				childIt.next();
				new PathIterator(this, responsibleChild);
			}
		}

		if (!hasChildren()) {
			path.add(childIt.next());
		} else if (!hasNextChildren()) {
			clearChildren();
			updateIterators();

			if (hasNext()) {
				throw new PathResetException();
			}
			path.add(childIt.current());
		} else {
			ListIterator<Node> lit = new NodeIterator(this);
			while (lit.hasPrevious()) {
				PathIterator it = (PathIterator) lit.previous();
				if (it.hasNext()) {
					it.next(path);
					break;
				} else {
					it.reset();
					it.current(path);
				}
			}

			while (lit.hasPrevious()) {
				PathIterator it = (PathIterator) lit.previous();
				it.current(path);
			}

			path.add(childIt.current());
		}
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	private void reset() {
		childIt.reset();
	}

	/**
	 * Adds a child iterator (if any). This method loops over the reference node's children, and
	 * creates a path iterator over each child with children. Only installs an iterator over the
	 * first condition.
	 */
	private void updateIterators() {
		while (childIt.hasNext()) {
			Node child = childIt.next();
			if (child.hasChildren()) {
				new PathIterator(this, child);
			}

			if (isIf(getNode())) {
				break;
			}
		}
	}

}

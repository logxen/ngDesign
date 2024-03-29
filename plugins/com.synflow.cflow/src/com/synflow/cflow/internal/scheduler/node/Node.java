/*******************************************************************************
 * Copyright (c) 2013-2014 Synflow SAS.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Matthieu Wipliez - initial API and implementation and/or initial documentation
 *******************************************************************************/
package com.synflow.cflow.internal.scheduler.node;

import com.google.common.base.Strings;

/**
 * This class defines a Node to create tree models. A Node has links to its parent, previous and
 * next siblings, and first and last child. All fields are updated automatically when a new Node is
 * created with an existing parent node. To get an iterable over a node's children, call the
 * {@link #getChildren()} method.
 * 
 * @author Matthieu Wipliez
 */
public class Node {

	private Object content;

	private Node firstChild;

	private Node lastChild;

	private Node next;

	private final Node parent;

	private Node previous;

	/**
	 * Creates a root empty node, i.e. with no parent and no content.
	 */
	public Node() {
		this(null, null);
	}

	/**
	 * Creates an empty node, i.e. with no content, and the given parent.
	 * 
	 * @param parent
	 *            a parent node (may be <code>null</code>)
	 */
	public Node(Node parent) {
		this(parent, null);
	}

	/**
	 * Creates a node with the given parent and content. If <code>parent != null</code>, this node
	 * is added as a child of <code>parent</code>.
	 * 
	 * @param parent
	 *            a parent node (may be <code>null</code>)
	 * @param content
	 *            an object to use as this node's content (may be <code>null</code>)
	 */
	public Node(Node parent, Object content) {
		if (parent != null) {
			parent.add(this);
		}

		this.content = content;
		this.parent = parent;
	}

	/**
	 * Creates a root node, i.e. with no parent and the given content.
	 * 
	 * @param content
	 *            an object to use as this node's content
	 */
	public Node(Object content) {
		this(null, content);
	}

	/**
	 * Adds a child node to this node.
	 * 
	 * @param node
	 *            a node
	 */
	private void add(Node node) {
		if (!hasChildren()) {
			lastChild = firstChild = node;
		} else {
			lastChild.addSibling(node);
			lastChild = node;
		}
	}

	/**
	 * Adds the given node as a sibling of this node.
	 * 
	 * @param node
	 *            a node
	 */
	private void addSibling(Node node) {
		next = node;
		node.previous = this;
	}

	/**
	 * Removes children of this node. This method just removes links to the first and last child of
	 * this node.
	 */
	public void clearChildren() {
		firstChild = lastChild = null;
	}

	/**
	 * Returns an iterable over this node's children.
	 * 
	 * @return an {@link Iterable}&lt;{@link Node}&gt;
	 */
	public Iterable<Node> getChildren() {
		return new NodeIterable(this);
	}

	/**
	 * Returns this node's content
	 * 
	 * @return an object
	 */
	public Object getContent() {
		return content;
	}

	/**
	 * Returns this node's first child.
	 * 
	 * @return a node, or <code>null</code> if this node has no children
	 */
	public Node getFirstChild() {
		return firstChild;
	}

	/**
	 * Returns this node's last child.
	 * 
	 * @return a node, or <code>null</code> if this node has no children
	 */
	public Node getLastChild() {
		return lastChild;
	}

	/**
	 * Returns this node's next sibling.
	 * 
	 * @return a node, or <code>null</code> if this node has no next sibling
	 */
	public Node getNextSibling() {
		return next;
	}

	/**
	 * Returns this node's parent.
	 * 
	 * @return a node, or <code>null</code> if this node has no parent
	 */
	public Node getParent() {
		return parent;
	}

	/**
	 * Returns this node's previous sibling.
	 * 
	 * @return a node, or <code>null</code> if this node has no previous sibling
	 */
	public Node getPreviousSibling() {
		return previous;
	}

	/**
	 * Returns a boolean indicating if this node has children.
	 * 
	 * @return a boolean
	 */
	public boolean hasChildren() {
		return firstChild != null;
	}

	/**
	 * Sets the content of this node to the given object.
	 * 
	 * @param content
	 *            an object
	 */
	public void setContent(Object content) {
		this.content = content;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	private String toString(int indent) {
		StringBuilder builder = new StringBuilder(Strings.repeat("|  ", indent));
		builder.append(content);
		builder.append('\n');
		for (Node child : getChildren()) {
			builder.append(child.toString(indent + 1));
		}
		return builder.toString();
	}

}

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
package com.synflow.cflow.ui.internal.views.graph;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Lists.newArrayList;
import static org.eclipse.draw2d.PositionConstants.EAST;
import static org.eclipse.gef.EditPolicy.COMPONENT_ROLE;
import static org.eclipse.gef.EditPolicy.LAYOUT_ROLE;
import static org.eclipse.gef.LayerConstants.CONNECTION_LAYER;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.draw2d.AbsoluteBendpoint;
import org.eclipse.draw2d.Bendpoint;
import org.eclipse.draw2d.ConnectionLayer;
import org.eclipse.draw2d.ConnectionRouter;
import org.eclipse.draw2d.Figure;
import org.eclipse.draw2d.FreeformLayer;
import org.eclipse.draw2d.FreeformLayout;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.ManhattanConnectionRouter;
import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Insets;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.draw2d.graph.DirectedGraph;
import org.eclipse.draw2d.graph.DirectedGraphLayout;
import org.eclipse.draw2d.graph.Edge;
import org.eclipse.draw2d.graph.Node;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.DragTracker;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.LayerConstants;
import org.eclipse.gef.Request;
import org.eclipse.gef.editparts.AbstractGraphicalEditPart;
import org.eclipse.gef.editpolicies.RootComponentEditPolicy;

import com.synflow.models.dpn.Connection;
import com.synflow.models.dpn.DPN;
import com.synflow.models.dpn.Endpoint;
import com.synflow.models.dpn.Port;
import com.synflow.models.util.EcoreHelper;

/**
 * This class defines the edit part for a DPN.
 * 
 * @author Matthieu Wipliez
 * 
 */
public class DPNPart extends AbstractGraphicalEditPart {

	@Override
	public void activate() {
		if (isActive()) {
			return;
		}
		super.activate();

		updateLayout();
	}

	@SuppressWarnings("unchecked")
	private DirectedGraph createDirectedGraph() {
		DirectedGraph dg = new DirectedGraph();
		dg.setDirection(EAST);

		Map<GraphicalEditPart, Node> nodeMap = new HashMap<>();
		for (Object obj : getChildren()) {
			GraphicalEditPart part = (GraphicalEditPart) obj;

			Node node = new Node(part);
			Dimension dim = part.getFigure().getPreferredSize();
			node.width = dim.width;
			node.height = dim.height;
			dg.nodes.add(node);
			nodeMap.put(part, node);

			if (part.getModel() instanceof Port) {
				node.setPadding(new Insets(2, 5, 2, 5));
			} else {
				node.setPadding(new Insets(5, 35, 5, 35));
			}
		}

		for (Object obj : getChildren()) {
			GraphicalEditPart part = (GraphicalEditPart) obj;
			for (Object conn : part.getSourceConnections()) {
				ConnectionPart connectionPart = (ConnectionPart) conn;
				Node sourceNode = nodeMap.get(connectionPart.getSource());
				Node targetNode = nodeMap.get(connectionPart.getTarget());
				Edge edge = new Edge(conn, sourceNode, targetNode);
				dg.edges.add(edge);

				Connection connection = (Connection) connectionPart.getModel();
				edge.setSourceOffset(getOffset(connection.getSourceEndpoint()));
				edge.setTargetOffset(getOffset(connection.getTargetEndpoint()));
			}
		}

		return dg;
	}

	@Override
	protected void createEditPolicies() {
		installEditPolicy(COMPONENT_ROLE, new RootComponentEditPolicy());
		installEditPolicy(LAYOUT_ROLE, new SimpleLayoutEditPolicy());
	}

	@Override
	protected IFigure createFigure() {
		Figure f = new FreeformLayer();
		f.setLayoutManager(new FreeformLayout());

		// Create the static router for the connection layer
		ConnectionLayer connLayer = (ConnectionLayer) getLayer(LayerConstants.CONNECTION_LAYER);
		// connLayer.setConnectionRouter(new BendpointConnectionRouter());
		// connLayer.setConnectionRouter(new ShortestPathConnectionRouter(f));
		connLayer.setConnectionRouter(new ManhattanConnectionRouter());

		return f;
	}

	@Override
	public DragTracker getDragTracker(Request req) {
		return new DirtyMarqueeTool();
	}

	@Override
	public final DPN getModel() {
		return (DPN) super.getModel();
	}

	@Override
	public List<EObject> getModelChildren() {
		DPN dpn = getModel();
		return newArrayList(concat(dpn.getInputs(), dpn.getInstances(), dpn.getOutputs()));
	}

	/**
	 * Returns the offset of the edge that is incoming/outgoing to the given endpoint. If the
	 * endpoint is a port, returns -1
	 * 
	 * @param endpoint
	 *            an endpoint
	 * @return an integer offset
	 */
	private int getOffset(Endpoint endpoint) {
		if (endpoint.hasInstance()) {
			Port port = endpoint.getPort();
			return EcoreHelper.getContainingList(port).indexOf(port);
		}
		return -1;
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

	private void updateLayout() {
		DirectedGraph dg = createDirectedGraph();

		DirectedGraphLayout layout = new DirectedGraphLayout();
		layout.visit(dg);

		for (Object obj : dg.nodes) {
			Node node = (Node) obj;
			GraphicalEditPart part = (GraphicalEditPart) node.data;

			Rectangle rect = new Rectangle(node.x, node.y, -1, node.height);
			getFigure().setConstraint(part.getFigure(), rect);
		}

		for (Object obj : dg.edges) {
			Edge edge = (Edge) obj;
			ConnectionPart part = (ConnectionPart) edge.data;

			@SuppressWarnings("unchecked")
			List<Node> vNodes = edge.vNodes;
			if (vNodes != null) {
				ConnectionLayer connLayer = (ConnectionLayer) getLayer(CONNECTION_LAYER);
				ConnectionRouter router = connLayer.getConnectionRouter();

				org.eclipse.draw2d.Connection connFigure = part.getConnectionFigure();
				List<Bendpoint> bendpoints = new ArrayList<>();
				for (Node vNode : vNodes) {
					bendpoints.add(new AbsoluteBendpoint(vNode.x, vNode.y));
				}

				router.setConstraint(connFigure, bendpoints);
				connFigure.setConnectionRouter(router);
			}
		}
	}
}

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
package com.synflow.cflow.internal.instantiation;

import static com.synflow.cflow.CflowConstants.TYPE_READS;
import static com.synflow.models.util.SwitchUtil.visit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.IScopeProvider;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.synflow.cflow.cflow.CflowPackage;
import com.synflow.cflow.cflow.CflowPackage.Literals;
import com.synflow.cflow.cflow.Connect;
import com.synflow.cflow.cflow.Inst;
import com.synflow.cflow.cflow.Network;
import com.synflow.cflow.cflow.VarRef;
import com.synflow.cflow.internal.ErrorMarker;
import com.synflow.models.dpn.Connection;
import com.synflow.models.dpn.DPN;
import com.synflow.models.dpn.DpnFactory;
import com.synflow.models.dpn.Endpoint;
import com.synflow.models.dpn.Entity;
import com.synflow.models.dpn.Instance;
import com.synflow.models.dpn.Port;
import com.synflow.models.ir.Type;
import com.synflow.models.ir.util.IrUtil;
import com.synflow.models.ir.util.TypePrinter;
import com.synflow.models.ir.util.TypeUtil;

/**
 * This class defines an helper class that creates Connections.
 * 
 * @author Matthieu Wipliez
 * 
 */
public class ConnectionMaker {

	@Inject
	private IQualifiedNameConverter converter;

	@Inject
	private IInstantiator instantiator;

	/**
	 * A map whose keys are Instance or DPN, and whose values are ports that can be written to
	 * (input ports for instance, output ports for DPN).
	 */
	private final Multimap<EObject, Port> portMap;

	@Inject
	private IScopeProvider scopeProvider;

	public ConnectionMaker() {
		portMap = LinkedHashMultimap.create();
	}

	private void addError(ErrorMarker marker) {
		Network network = (Network) marker.getSource().eContainer();
		network.getErrors().add(marker);
	}

	/**
	 * Checks the assignment from <code>typeSrc</code> to <code>typeTgt</code>.
	 * 
	 * @param sourceText
	 * @param typeSrc
	 * @param typeTgt
	 * @param source
	 * @param feature
	 * @param index
	 */
	private void checkAssign(String sourceText, Type typeSrc, Type typeTgt, Connect source,
			EStructuralFeature feature, int index) {
		if (typeSrc == null || typeTgt == null) {
			return;
		}

		if (!TypeUtil.canAssign(typeSrc, typeTgt)) {
			addError(new ErrorMarker("Type mismatch: cannot convert " + sourceText + " from "
					+ new TypePrinter().toString(typeSrc) + " to "
					+ new TypePrinter().toString(typeTgt), source, feature, index));
		}
	}

	private void checkPortAssociation(Connect connect, Instance instance, int index, VarRef ref,
			Port targetPort) {
		// resolves type of source ref
		Type srcType = getResolvedPortType(ref);

		// resolves type of target port
		Type tgtType = IrUtil.copy(targetPort.getType());
		if (instance != null) {
			visit(new TypeResolver(instance), tgtType);
		}

		// check assign
		INode node = NodeModelUtils.getNode(ref);
		String srcName = "'" + NodeModelUtils.getTokenText(node) + "'";
		checkAssign(srcName, srcType, tgtType, connect, Literals.CONNECT__PORTS, index);

		// check ports have the same interface type
		Port sourcePort = instantiator.getPort(ref);
		if (sourcePort.getInterface() != targetPort.getInterface()) {
			addError(new ErrorMarker("Port mismatch: incompatible interface type between "
					+ srcName + " and '" + targetPort.getName() + "'", connect,
					Literals.CONNECT__PORTS, index));
		}
	}

	private void connect(DPN dpn, Connect connect, Instance instance, VarRef ref, Port sourcePort,
			Port targetPort) {
		Endpoint otherEndPoint;
		if (sourcePort.eContainer() == dpn) {
			otherEndPoint = new Endpoint(dpn, sourcePort);
		} else {
			otherEndPoint = new Endpoint(getInstance(ref), sourcePort);
		}

		Endpoint thisEndPoint;
		if (connect.isThis()) {
			thisEndPoint = new Endpoint(dpn, targetPort);
		} else {
			thisEndPoint = new Endpoint(instance, targetPort);
		}

		Connection conn;
		if (TYPE_READS.equals(connect.getType())) {
			conn = DpnFactory.eINSTANCE.createConnection(otherEndPoint, thisEndPoint);
		} else {
			conn = DpnFactory.eINSTANCE.createConnection(thisEndPoint, otherEndPoint);
		}

		dpn.getGraph().add(conn);
	}

	/**
	 * Creates a new port from the given parameters, and adds a connection to the DPN associated
	 * with the network containing the instance.
	 * 
	 * @param link
	 *            link
	 * @param thisInst
	 *            an instance
	 * @param ref
	 *            reference to the port
	 * @return a new IR port
	 */
	Port getConnectedPort(String link, Inst thisInst, VarRef ref) {
		QualifiedName name = converter.toQualifiedName(link);
		int size = name.getSegmentCount();
		boolean isSimpleName = size == 1;

		// create connection
		Instance instance = instantiator.getInstance(thisInst);
		DPN dpn = instance.getDPN();
		Port otherPort = instantiator.getPort(ref.getVariable());
		boolean isInput = IrUtil.isInput(otherPort);

		// compute otherEndPoint and portName
		Endpoint otherEndPoint;
		Port thisPort = IrUtil.copy(otherPort);
		if (isSimpleName) {
			// this port is defined in this instance or containing netwok
			otherEndPoint = new Endpoint(dpn, otherPort);
			String portName = ((Entity) otherPort.eContainer()).getSimpleName() + "_"
					+ otherPort.getName();
			thisPort.setName(portName);
		} else {
			// this port is defined by another instance
			Instance otherInst = getInstance(ref);
			otherEndPoint = new Endpoint(otherInst, otherPort);
			String portName = name.toString("_");
			thisPort.setName(portName);

			// we need to replace references to parameters by actual values
			visit(new TypeResolver(otherInst), thisPort.getType());

			// if this is an input port, remove it from the port map
			if (isInput) {
				portMap.remove(otherInst, otherPort);
			}
		}

		// create connection, add port to entity
		Connection conn;
		Endpoint thisEndPoint = new Endpoint(instance, thisPort);
		if (!(isSimpleName ^ isInput)) { // both 0 or both 1
			instance.getEntity().getInputs().add(thisPort);
			conn = DpnFactory.eINSTANCE.createConnection(otherEndPoint, thisEndPoint);
		} else {
			instance.getEntity().getOutputs().add(thisPort);
			conn = DpnFactory.eINSTANCE.createConnection(thisEndPoint, otherEndPoint);
		}

		// add connection to graph
		dpn.getGraph().add(conn);

		return thisPort;
	}

	/**
	 * If the given port reference refers to a port in an instance, returns that instance.
	 * Otherwise, if the reference is that of a simple port (no instance), returns null.
	 * 
	 * @param ref
	 *            a port reference
	 * @return an instance
	 */
	private Instance getInstance(VarRef ref) {
		String link = NodeModelUtils.getTokenText(NodeModelUtils.getNode(ref));
		QualifiedName name = converter.toQualifiedName(link);
		if (name.getSegmentCount() == 1) {
			return null;
		}

		IScope scope = scopeProvider.getScope(ref, CflowPackage.Literals.CONNECT__INSTANCE);
		QualifiedName qualifiedLinkName = converter.toQualifiedName(name.getFirstSegment());
		IEObjectDescription eObjectDescription = scope.getSingleElement(qualifiedLinkName);

		return instantiator.getInstance((Inst) eObjectDescription.getEObjectOrProxy());
	}

	/**
	 * Returns the resolved type of the port referenced by <code>ref</code>.
	 * 
	 * @param ref
	 *            reference to a C~ port variable
	 * @return an IR type resolved
	 */
	private Type getResolvedPortType(VarRef ref) {
		Port port = instantiator.getPort(ref.getVariable());
		Instance otherInst = getInstance(ref);
		if (otherInst == null) {
			return port.getType();
		} else {
			// we need to replace references to parameters by actual values
			Type type = IrUtil.copy(port.getType());
			visit(new TypeResolver(otherInst), type);
			return type;
		}
	}

	/**
	 * Initializes this connection maker with the given DPN.
	 * 
	 * @param dpn
	 *            a DPN
	 */
	void initialize(DPN dpn) {
		portMap.putAll(dpn, dpn.getOutputs());

		for (Instance instance : dpn.getInstances()) {
			portMap.putAll(instance, instance.getEntity().getInputs());
		}
	}

	void makeConnection(Connect connect) {
		Network network = (Network) connect.eContainer();
		DPN dpn = (DPN) instantiator.getEntity(network);
		Instance instance = null;
		String name;

		Collection<Port> ports;
		if (connect.isThis()) {
			name = "this";
			if (TYPE_READS.equals(connect.getType())) {
				ports = portMap.get(dpn);
			} else { // TYPE_WRITES
				ports = dpn.getInputs();
			}
		} else {
			instance = instantiator.getInstance(connect.getInstance());
			name = instance.getName();
			Entity entity = instance.getEntity();
			if (TYPE_READS.equals(connect.getType())) {
				ports = portMap.get(instance);
			} else { // TYPE_WRITES
				ports = entity.getOutputs();
			}
		}

		Iterator<Port> it = ports.iterator();
		List<Port> targetPorts = new ArrayList<>();
		int index = 0;
		for (VarRef ref : connect.getPorts()) {
			if (index >= ports.size()) {
				if (TYPE_READS.equals(connect.getType())) {
					String kind = connect.isThis() ? "output" : "input";
					addError(new ErrorMarker("Connectivity: no more ports available, all " + kind
							+ " ports of '" + name + "' are already connected", connect));
				} else {
					addError(new ErrorMarker("Connectivity: too many ports given to '" + name
							+ ".writes', expected at most " + ports.size() + ", got "
							+ connect.getPorts().size(), connect));
				}
				break;
			}

			Port sourcePort = instantiator.getPort(ref.getVariable());

			// removes sourcePort from portMap
			// this is done for any combination of this/instance and reads/writes
			// for ports other than (instance, input) and (dpn, output) this is a no-op
			Instance sourceInst = getInstance(ref);
			if (sourceInst == null) {
				portMap.remove(dpn, sourcePort);
			} else {
				portMap.remove(sourceInst, sourcePort);
			}

			Port targetPort = it.next();
			targetPorts.add(targetPort);

			checkPortAssociation(connect, instance, index, ref, targetPort);
			index++;

			connect(dpn, connect, instance, ref, sourcePort, targetPort);
		}

		// removes target ports accessed by "reads"
		if (TYPE_READS.equals(connect.getType())) {
			EObject key = connect.isThis() ? dpn : instance;
			for (Port port : targetPorts) {
				portMap.remove(key, port);
			}
		}
	}

}

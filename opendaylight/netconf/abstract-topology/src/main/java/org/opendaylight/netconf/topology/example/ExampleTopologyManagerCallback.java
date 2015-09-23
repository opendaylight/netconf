/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.StateAggregator;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.UserDefinedMessage;
import org.opendaylight.netconf.topology.util.BaseNodeManager;
import org.opendaylight.netconf.topology.util.NodeWriter;
import org.opendaylight.netconf.topology.util.NoopElectionStrategy;
import org.opendaylight.netconf.topology.util.SalNodeWriter;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public class ExampleTopologyManagerCallback implements TopologyManagerCallback<UserDefinedMessage> {

    private final DataBroker dataBroker;
    private boolean isMaster;

    private final String topologyId;
    private final StateAggregator aggregator;
    private final NodeWriter naSalNodeWriter;
    private final Map<NodeId, NodeManager> nodes = new HashMap<>();
    private final NodeManagerCallbackFactory<UserDefinedMessage> nodeHandlerFactory;

    public ExampleTopologyManagerCallback(final DataBroker dataBroker, final String topologyId,
                                          final NodeManagerCallbackFactory<UserDefinedMessage> nodeHandlerFactory, final StateAggregator aggregator) {
        this(dataBroker, topologyId, nodeHandlerFactory, aggregator, new SalNodeWriter(dataBroker, topologyId));
    }

    public ExampleTopologyManagerCallback(final DataBroker dataBroker, final String topologyId,
                                          final NodeManagerCallbackFactory<UserDefinedMessage> nodeHandlerFactory, final StateAggregator aggregator,
                                          final NodeWriter naSalNodeWriter) {
        this.dataBroker = dataBroker;
        this.topologyId = topologyId;
        this.nodeHandlerFactory = nodeHandlerFactory;
        this.aggregator = aggregator;
        this.naSalNodeWriter = naSalNodeWriter;

    }

    @Override
    public void setPeerContext(PeerContext<UserDefinedMessage> peerContext) {

    }

    @Override
    public void handle(UserDefinedMessage msg) {

    }

    @Override
    public ListenableFuture<Node> nodeCreated(final NodeId nodeId, final Node node) {
        // Init node admin and a writer for it

        // TODO let end user code notify the baseNodeManager about state changes and handle them here on topology level
        final BaseNodeManager<UserDefinedMessage> naBaseNodeManager = new BaseNodeManager<>(node.getNodeId().getValue(), nodeHandlerFactory, new NoopElectionStrategy());
        nodes.put(nodeId, naBaseNodeManager);

        // Set initial state ? in every peer or just master ? TODO
        naSalNodeWriter.init(nodeId, naBaseNodeManager.getInitialState(nodeId, node));

        // trigger connect on this node
        return naBaseNodeManager.nodeCreated(nodeId, node);

    }

    @Override
    public ListenableFuture<Node> nodeUpdated(final NodeId nodeId, final Node node) {
        // Set initial state
        naSalNodeWriter.init(nodeId, nodes.get(nodeId).getInitialState(nodeId, node));

        // Trigger nodeUpdated only on this node
        return nodes.get(nodeId).nodeUpdated(nodeId, node);
    }

    @Override
    public ListenableFuture<Void> nodeDeleted(final NodeId nodeId) {
        // Trigger delete only on this node
        return nodes.get(nodeId).nodeDeleted(nodeId);
    }

    @Override public boolean isMaster() {
        return isMaster;
    }

    @Override
    public Iterable<TopologyManager> getPeers() {
        // FIXME this should go through akka
        return Collections.emptySet();
    }

    @Override
    public void ownershipChanged(EntityOwnershipChange ownershipChange) {
        isMaster = ownershipChange.isOwner();
        // our post-election logic
    }
}

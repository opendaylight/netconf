/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.topology.StateAggregator;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.example.ExampleTopology.CustomMessage;
import org.opendaylight.netconf.topology.util.BaseNodeManager;
import org.opendaylight.netconf.topology.util.NodeWriter;
import org.opendaylight.netconf.topology.util.SalNodeWriter;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public class ExampleTopologyManagerCallback implements TopologyManagerCallback<CustomMessage> {

    private final DataBroker dataBroker;
    private final boolean isMaster;

    private final String topologyId;
    private final StateAggregator aggregator;
    private final NodeWriter naSalNodeWriter;
    private final Map<NodeId, NodeManager> nodes = new HashMap<>();
    private final NodeManagerCallbackFactory<CustomMessage> nodeHandlerFactory;

    public ExampleTopologyManagerCallback(final DataBroker dataBroker, final String topologyId,
                                          final NodeManagerCallbackFactory<CustomMessage> nodeHandlerFactory, final StateAggregator aggregator) {
        this(dataBroker, topologyId, nodeHandlerFactory, aggregator, new SalNodeWriter(dataBroker));
    }

    public ExampleTopologyManagerCallback(final DataBroker dataBroker, final String topologyId,
                                          final NodeManagerCallbackFactory<CustomMessage> nodeHandlerFactory, final StateAggregator aggregator,
                                          final NodeWriter naSalNodeWriter) {
        this.dataBroker = dataBroker;
        this.topologyId = topologyId;
        this.nodeHandlerFactory = nodeHandlerFactory;
        this.aggregator = aggregator;
        this.naSalNodeWriter = naSalNodeWriter;

        //this should be inherited from topologyAdmin
        isMaster = elect();

    }

    private boolean elect() {
        // FIXME implement this with EntityElectionService
        return true;
    }

    @Override
    public void setPeerContext(PeerContext<CustomMessage> peerContext) {

    }

    @Override
    public void handle(CustomMessage msg) {

    }

    @Override
    public ListenableFuture<Node> nodeCreated(final NodeId nodeId, final Node node) {
        // Init node admin and a writer for it
        final SalNodeWriter naSalNodeWriter = new SalNodeWriter(dataBroker);
        final BaseNodeManager<CustomMessage> naBaseNodeManager = new BaseNodeManager<>(nodeHandlerFactory.create());
        nodes.put(nodeId, naBaseNodeManager);

        // Set initial state ? in every peer or just master ? TODO
        naSalNodeWriter.init(nodeId, naBaseNodeManager.getInitialState(nodeId, node));

        ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        if (isMaster) {
            futures.add(naBaseNodeManager.nodeCreated(nodeId, node));
            // only master should call connect on peers and aggregate futures
            for (TopologyManager topologyManager : getPeers()) {
                futures.add(topologyManager.nodeCreated(nodeId, node));
            }

            //combine peer futures
            return aggregator.combineCreateAttempts(futures);
        }

        //trigger connect on this node
        return naBaseNodeManager.nodeCreated(nodeId, node);

    }

    @Override
    public ListenableFuture<Node> nodeUpdated(final NodeId nodeId, final Node node) {
        // Set initial state
        naSalNodeWriter.init(nodeId, nodes.get(nodeId).getInitialState(nodeId, node));

        ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        // Master needs to trigger nodeUpdated on peers and combine results
        if (isMaster) {
            futures.add(nodes.get(nodeId).nodeUpdated(nodeId, node));
            for (TopologyManager topology : getPeers()) {
                futures.add(topology.nodeUpdated(nodeId, node));
            }

            return aggregator.combineUpdateAttempts(futures);
        }
        // Trigger nodeUpdated only on this node
        return nodes.get(nodeId).nodeUpdated(nodeId, node);
    }

    @Override
    public ListenableFuture<Void> nodeDeleted(final NodeId nodeId) {

        ArrayList<ListenableFuture<Void>> futures = new ArrayList<>();

        // Master needs to trigger delete on peers and combine results
        if (isMaster) {
            futures.add(nodes.get(nodeId).nodeDeleted(nodeId));
            for (TopologyManager topology : getPeers()) {
                futures.add(topology.nodeDeleted(nodeId));
            }

            return aggregator.combineDeleteAttempts(futures);
        }

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

}

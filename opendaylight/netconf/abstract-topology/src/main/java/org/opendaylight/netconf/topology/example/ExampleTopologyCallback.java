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
import org.opendaylight.netconf.topology.ConnectionAggregator;
import org.opendaylight.netconf.topology.NodeAdministrator;
import org.opendaylight.netconf.topology.NodeAdministratorCallback.NodeAdministratorCallbackFactory;
import org.opendaylight.netconf.topology.TopologyAdministrator;
import org.opendaylight.netconf.topology.TopologyAdministratorCallback;
import org.opendaylight.netconf.topology.example.ExampleTopology.CustomMessage;
import org.opendaylight.netconf.topology.util.BaseNodeAdmin;
import org.opendaylight.netconf.topology.util.NodeWriter;
import org.opendaylight.netconf.topology.util.SalNodeWriter;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public class ExampleTopologyCallback implements TopologyAdministratorCallback<CustomMessage> {

    private final DataBroker dataBroker;
    private final boolean isMaster;

    private final String topologyId;
    private final ConnectionAggregator aggregator;
    private final NodeWriter naSalNodeWriter;
    private final Map<NodeId, NodeAdministrator> nodes = new HashMap<>();
    private final NodeAdministratorCallbackFactory<CustomMessage> nodeHandlerFactory;

    public ExampleTopologyCallback(final DataBroker dataBroker, final String topologyId,
                                   final NodeAdministratorCallbackFactory<CustomMessage> nodeHandlerFactory, final ConnectionAggregator aggregator) {
        this(dataBroker, topologyId, nodeHandlerFactory, aggregator, new SalNodeWriter(dataBroker));
    }

    public ExampleTopologyCallback(final DataBroker dataBroker, final String topologyId,
                                   final NodeAdministratorCallbackFactory<CustomMessage> nodeHandlerFactory, final ConnectionAggregator aggregator,
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
    public ListenableFuture<Node> connect(final NodeId nodeId, final Node node) {
        // Init node admin and a writer for it
        final SalNodeWriter naSalNodeWriter = new SalNodeWriter(dataBroker);
        final BaseNodeAdmin<CustomMessage> naBaseNodeAdmin = new BaseNodeAdmin<>(nodeHandlerFactory.create());
        nodes.put(nodeId, naBaseNodeAdmin);

        // Set initial state ? in every peer or just master ? TODO
        naSalNodeWriter.init(nodeId, naBaseNodeAdmin.getInitialState(nodeId, node));

        ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        if (isMaster) {
            futures.add(naBaseNodeAdmin.connect(nodeId, node));
            // only master should call connect on peers and aggregate futures
            for (TopologyAdministrator topologyAdministrator : getPeers()) {
                futures.add(topologyAdministrator.connect(nodeId, node));
            }

            //combine peer futures
            return aggregator.combineConnectAttempts(futures);
        }

        //trigger connect on this node
        return naBaseNodeAdmin.connect(nodeId, node);

    }

    @Override
    public ListenableFuture<Node> update(final NodeId nodeId, final Node node) {
        // Set initial state
        naSalNodeWriter.init(nodeId, nodes.get(nodeId).getInitialState(nodeId, node));

        ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        // Master needs to trigger update on peers and combine results
        if (isMaster) {
            futures.add(nodes.get(nodeId).update(nodeId, node));
            for (TopologyAdministrator topology : getPeers()) {
                futures.add(topology.update(nodeId, node));
            }

            return aggregator.combineUpdateAttempts(futures);
        }
        // Trigger update only on this node
        return nodes.get(nodeId).update(nodeId, node);
    }

    @Override
    public ListenableFuture<Void> delete(final NodeId nodeId) {

        ArrayList<ListenableFuture<Void>> futures = new ArrayList<>();

        // Master needs to trigger delete on peers and combine results
        if (isMaster) {
            futures.add(nodes.get(nodeId).delete(nodeId));
            for (TopologyAdministrator topology : getPeers()) {
                futures.add(topology.delete(nodeId));
            }

            return aggregator.combineDisconnectAttempts(futures);
        }

        // Trigger delete only on this node
        return nodes.get(nodeId).delete(nodeId);
    }

    @Override public boolean isMaster() {
        return isMaster;
    }

    @Override
    public Iterable<TopologyAdministrator> getPeers() {
        // FIXME this should go through akka
        return Collections.emptySet();
    }

}

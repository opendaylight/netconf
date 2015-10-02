/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import akka.actor.ActorContext;
import akka.actor.TypedActor;
import akka.actor.TypedActorExtension;
import akka.actor.TypedActorFactory;
import akka.actor.TypedProps;
import akka.japi.Creator;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.NodeManager.NodeManagerFactory;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.StateAggregator;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.UserDefinedMessage;
import org.opendaylight.netconf.topology.util.BaseNodeManager;
import org.opendaylight.netconf.topology.util.BaseNodeManager.BaseNodeManagerBuilder;
import org.opendaylight.netconf.topology.util.NodeRoleChangeStrategy;
import org.opendaylight.netconf.topology.util.NodeWriter;
import org.opendaylight.netconf.topology.util.NoopRoleChangeStrategy;
import org.opendaylight.netconf.topology.util.SalNodeWriter;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public class ExampleTopologyManagerCallback implements TopologyManagerCallback<UserDefinedMessage> {

    private final DataBroker dataBroker;
    private boolean isMaster;

    private final String topologyId;
    private final List<String> remotePaths;
    private final NodeWriter naSalNodeWriter;
    private final Map<NodeId, NodeManager> nodes = new HashMap<>();
    private final NodeManagerCallbackFactory<UserDefinedMessage> nodeHandlerFactory;

    public ExampleTopologyManagerCallback(final DataBroker dataBroker, final String topologyId, final List<String> remotePaths,
                                          final NodeManagerCallbackFactory<UserDefinedMessage> nodeHandlerFactory) {
        this(dataBroker, topologyId, remotePaths, nodeHandlerFactory, new SalNodeWriter(dataBroker, topologyId));
    }

    public ExampleTopologyManagerCallback(final DataBroker dataBroker, final String topologyId, final List<String> remotePaths,
                                          final NodeManagerCallbackFactory<UserDefinedMessage> nodeHandlerFactory,
                                          final NodeWriter naSalNodeWriter) {
        this(dataBroker, topologyId, remotePaths, nodeHandlerFactory, naSalNodeWriter, false);

    }

    public ExampleTopologyManagerCallback(final DataBroker dataBroker, final String topologyId, final List<String> remotePaths,
                                          final NodeManagerCallbackFactory<UserDefinedMessage> nodeHandlerFactory,
                                          final NodeWriter naSalNodeWriter, boolean isMaster) {
        this.dataBroker = dataBroker;
        this.topologyId = topologyId;
        this.remotePaths = remotePaths;
        this.nodeHandlerFactory = nodeHandlerFactory;
        this.naSalNodeWriter = naSalNodeWriter;

        this.isMaster = isMaster;
    }

    @Override
    public ListenableFuture<Node> nodeCreated(ActorContext context, NodeId nodeId, Node node) {
        // Init node admin and a writer for it

        // TODO let end user code notify the baseNodeManager about state changes and handle them here on topology level
        final BaseNodeManager<UserDefinedMessage> naBaseNodeManager =
                createNodeManager(nodeId);

        nodes.put(nodeId, naBaseNodeManager);

        // Set initial state ? in every peer or just master ? TODO
        if (isMaster) {
            naSalNodeWriter.init(nodeId, naBaseNodeManager.getInitialState(nodeId, node));
        }

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
    public Iterable<TopologyManager<UserDefinedMessage>> getPeers() {
        // FIXME this should go through akka
        return Collections.emptySet();
    }

    @Override
    public void onRoleChanged(RoleChangeDTO roleChangeDTO) {
        isMaster = roleChangeDTO.isOwner();
        // our post-election logic
    }

    private BaseNodeManager<UserDefinedMessage> createNodeManager(NodeId nodeId) {
        return new BaseNodeManagerBuilder<UserDefinedMessage>().setNodeId(nodeId.getValue())
                .setActorContext(TypedActor.context())
                .setDelegateFactory(nodeHandlerFactory)
                .setRoleChangeStrategy(new NoopRoleChangeStrategy())
                .setTopologyId(topologyId)
                .setRemotePaths(remotePaths)
                .build();
    }
}

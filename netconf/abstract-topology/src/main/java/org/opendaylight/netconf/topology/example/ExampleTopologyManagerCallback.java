/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.util.BaseNodeManager.BaseNodeManagerBuilder;
import org.opendaylight.netconf.topology.util.NodeWriter;
import org.opendaylight.netconf.topology.util.NoopRoleChangeStrategy;
import org.opendaylight.netconf.topology.util.SalNodeWriter;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public class ExampleTopologyManagerCallback implements TopologyManagerCallback {

    private final DataBroker dataBroker;
    private final ActorSystem actorSystem;
    private boolean isMaster;

    private final String topologyId;
    private final NodeWriter naSalNodeWriter;
    private final Map<NodeId, NodeManager> nodes = new HashMap<>();
    private final NodeManagerCallbackFactory nodeHandlerFactory;

    public ExampleTopologyManagerCallback(final ActorSystem actorSystem,
                                          final DataBroker dataBroker,
                                          final String topologyId,
                                          final NodeManagerCallbackFactory nodeHandlerFactory) {
        this(actorSystem, dataBroker, topologyId, nodeHandlerFactory, new SalNodeWriter(dataBroker, topologyId));
    }

    public ExampleTopologyManagerCallback(final ActorSystem actorSystem,
                                          final DataBroker dataBroker,
                                          final String topologyId,
                                          final NodeManagerCallbackFactory nodeHandlerFactory,
                                          final NodeWriter naSalNodeWriter) {
        this(actorSystem, dataBroker, topologyId, nodeHandlerFactory, naSalNodeWriter, false);

    }

    public ExampleTopologyManagerCallback(final ActorSystem actorSystem,
                                          final DataBroker dataBroker,
                                          final String topologyId,
                                          final NodeManagerCallbackFactory nodeHandlerFactory,
                                          final NodeWriter naSalNodeWriter,
                                          boolean isMaster) {
        this.dataBroker = dataBroker;
        this.actorSystem = actorSystem;
        this.topologyId = topologyId;
        this.nodeHandlerFactory = nodeHandlerFactory;
        this.naSalNodeWriter = naSalNodeWriter;

        this.isMaster = isMaster;
    }

    @Override
    public ListenableFuture<Node> onNodeCreated(NodeId nodeId, Node node) {
        // Init node admin and a writer for it

        // TODO let end user code notify the baseNodeManager about state changes and handle them here on topology level
        final NodeManager naBaseNodeManager =
                createNodeManager(nodeId);

        nodes.put(nodeId, naBaseNodeManager);

        // Set initial state ? in every peer or just master ? TODO
        if (isMaster) {
            naSalNodeWriter.init(nodeId, naBaseNodeManager.getInitialState(nodeId, node));
        }

        // trigger connect on this node
        return naBaseNodeManager.onNodeCreated(nodeId, node);
    }

    @Override
    public ListenableFuture<Node> onNodeUpdated(final NodeId nodeId, final Node node) {
        // Set initial state
        naSalNodeWriter.init(nodeId, nodes.get(nodeId).getInitialState(nodeId, node));

        // Trigger onNodeUpdated only on this node
        return nodes.get(nodeId).onNodeUpdated(nodeId, node);
    }

    @Override
    public ListenableFuture<Void> onNodeDeleted(final NodeId nodeId) {
        // Trigger delete only on this node
        final ListenableFuture<Void> future = nodes.get(nodeId).onNodeDeleted(nodeId);
        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // remove proxy from node list and stop the actor
                final NodeManager remove = nodes.remove(nodeId);
                TypedActor.get(actorSystem).stop(remove);
            }

            @Override
            public void onFailure(Throwable t) {
                // NOOP will be handled on higher level
            }
        });
        return future;
    }

    @Nonnull
    @Override
    public ListenableFuture<Node> getCurrentStatusForNode(@Nonnull NodeId nodeId) {
        return nodes.get(nodeId).getCurrentStatusForNode(nodeId);
    }

    @Override
    public void onRoleChanged(RoleChangeDTO roleChangeDTO) {
        isMaster = roleChangeDTO.isOwner();
        // our post-election logic
    }

    private NodeManager createNodeManager(NodeId nodeId) {
        return new BaseNodeManagerBuilder().setNodeId(nodeId.getValue())
                .setActorContext(TypedActor.context())
                .setDelegateFactory(nodeHandlerFactory)
                .setRoleChangeStrategy(new NoopRoleChangeStrategy())
                .setTopologyId(topologyId)
                .build();
    }

    @Override
    public void onReceive(Object o, ActorRef actorRef) {

    }

    @Nonnull
    @Override
    public Node getInitialState(@Nonnull NodeId nodeId, @Nonnull Node configNode) {
        return nodes.get(nodeId).getInitialState(nodeId, configNode);
    }

    @Nonnull
    @Override
    public Node getFailedState(@Nonnull NodeId nodeId, @Nullable Node configNode) {
        return nodes.get(nodeId).getFailedState(nodeId, configNode);
    }
}

/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.cluster.Cluster;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.util.BaseNodeManager;
import org.opendaylight.netconf.topology.util.NodeWriter;
import org.opendaylight.netconf.topology.util.NoopRoleChangeStrategy;
import org.opendaylight.netconf.topology.util.SalNodeWriter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.clustered.connection.status.ClusteredStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.NodeStatus;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

class RestconfTopologyManagerCallback implements TopologyManagerCallback {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfTopologyManagerCallback.class);
    private static final FiniteDuration TIMEOUT = new FiniteDuration(5, TimeUnit.SECONDS);

    private final ActorSystem actorSystem;
    private final Cluster cluster;
    private boolean isMaster;

    private final String topologyId;
    private final NodeWriter naSalNodeWriter;
    private final Map<NodeId, NodeManager> nodes = new HashMap<>();
    private final NodeManagerCallback.NodeManagerCallbackFactory nodeHandlerFactory;

    public RestconfTopologyManagerCallback(final ActorSystem actorSystem,
                                           final DataBroker dataBroker,
                                           final String topologyId,
                                           final NodeManagerCallback.NodeManagerCallbackFactory nodeHandlerFactory) {
        this(actorSystem, topologyId, nodeHandlerFactory, new SalNodeWriter(dataBroker, topologyId));
    }

    public RestconfTopologyManagerCallback(final ActorSystem actorSystem,
                                           final String topologyId,
                                           final NodeManagerCallback.NodeManagerCallbackFactory nodeHandlerFactory,
                                           final NodeWriter naSalNodeWriter) {
        this(actorSystem, topologyId, nodeHandlerFactory, naSalNodeWriter, false);

    }

    public RestconfTopologyManagerCallback(final ActorSystem actorSystem,
                                           final String topologyId,
                                           final NodeManagerCallback.NodeManagerCallbackFactory nodeHandlerFactory,
                                           final NodeWriter naSalNodeWriter,
                                           final boolean isMaster) {
        this.actorSystem = actorSystem;
        this.topologyId = topologyId;
        this.nodeHandlerFactory = nodeHandlerFactory;
        this.naSalNodeWriter = naSalNodeWriter;
        this.cluster = Cluster.get(actorSystem);
        this.isMaster = isMaster;
    }

    @Override
    public ListenableFuture<Node> onNodeCreated(final NodeId nodeId, final Node node) {

        // if this node was already configured, and whole config was pushed again, reinit with update
        if (nodes.containsKey(nodeId)) {
            return onNodeUpdated(nodeId, node);
        }

        // Init node admin
        final NodeManager naBaseNodeManager =
                createNodeManager(nodeId);
        nodes.put(nodeId, naBaseNodeManager);

        // only master should put initial state into datastore
        if (isMaster) {
            naSalNodeWriter.init(nodeId, naBaseNodeManager.getInitialState(nodeId, node));
        }

        // trigger connect on this node
        return naBaseNodeManager.onNodeCreated(nodeId, node);
    }

    @Override
    public ListenableFuture<Node> onNodeUpdated(final NodeId nodeId, final Node node) {
        // only master should put initial state into datastore
        if (isMaster) {
            naSalNodeWriter.init(nodeId, nodes.get(nodeId).getInitialState(nodeId, node));
        }

        // Trigger onNodeUpdated only on this node
        return nodes.get(nodeId).onNodeUpdated(nodeId, node);
    }

    @Override
    public ListenableFuture<Void> onNodeDeleted(final NodeId nodeId) {
        // Trigger delete only on this node
        final ListenableFuture<Void> future = nodes.get(nodeId).onNodeDeleted(nodeId);
        final SettableFuture<Void> deleteResult = SettableFuture.create();
        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                // remove proxy from node list and stop the actor
                LOG.debug("Stopping node actor for node : {}", nodeId.getValue());
                final NodeManager remove = nodes.remove(nodeId);
                final ActorRef nodeManager = TypedActor.get(actorSystem).getActorRefFor(remove);
                try {
                    final Future<Boolean> stopFuture = Patterns.gracefulStop(nodeManager, TIMEOUT);
                    stopFuture.onComplete(new OnComplete<Boolean>() {
                        @Override
                        public void onComplete(final Throwable failure, final Boolean success) throws Throwable {
                            if (failure == null) {
                                deleteResult.set(null);
                            } else {
                                deleteResult.setException(failure);
                            }
                        }
                    }, actorSystem.dispatcher());
                } catch (final Exception e) {
                    LOG.error("Error during actor stop", e);
                }
            }

            @Override
            public void onFailure(final Throwable t) {
                // NOOP will be handled on higher level
            }
        });
        return deleteResult;
    }

    @Nonnull
    @Override
    public ListenableFuture<Node> getCurrentStatusForNode(@Nonnull final NodeId nodeId) {
        if (!nodes.containsKey(nodeId)) {
            return Futures.immediateFuture(TopologyUtil.buildState(nodeId, cluster.selfAddress().toString(),
                    ClusteredStatus.Status.Unavailable, NodeStatus.Status.Connecting, null));
        }
        return nodes.get(nodeId).getCurrentStatusForNode(nodeId);
    }

    @Override
    public void onRoleChanged(final RoleChangeDTO roleChangeDTO) {
        isMaster = roleChangeDTO.isOwner();
        // our post-election logic
    }

    private NodeManager createNodeManager(final NodeId nodeId) {
        return new BaseNodeManager.BaseNodeManagerBuilder().setNodeId(nodeId.getValue())
                .setActorContext(TypedActor.context())
                .setDelegateFactory(nodeHandlerFactory)
                .setRoleChangeStrategy(new NoopRoleChangeStrategy())
                .setTopologyId(topologyId)
                .build();
    }

    @Override
    public void onReceive(final Object o, final ActorRef actorRef) {

    }

    @Nonnull
    @Override
    public Node getInitialState(@Nonnull final NodeId nodeId, @Nonnull final Node configNode) {
        final NodeManager nodeManager = nodes.get(nodeId);
        if (nodeManager != null) {
            return nodeManager.getInitialState(nodeId, configNode);
        } else {
            return TopologyUtil.buildState(nodeId, cluster.selfAddress().toString(), ClusteredStatus.Status.Unavailable,
                    NodeStatus.Status.Connecting, null);
        }
    }

    @Nonnull
    @Override
    public Node getFailedState(@Nonnull final NodeId nodeId, @Nonnull final Node configNode) {
        final NodeManager nodeManager = nodes.get(nodeId);
        if (nodeManager != null) {
            return nodeManager.getFailedState(nodeId, configNode);
        } else {
            return TopologyUtil.buildState(nodeId, cluster.selfAddress().toString(), ClusteredStatus.Status.Failed,
                    NodeStatus.Status.Failed, null);
        }
    }


}


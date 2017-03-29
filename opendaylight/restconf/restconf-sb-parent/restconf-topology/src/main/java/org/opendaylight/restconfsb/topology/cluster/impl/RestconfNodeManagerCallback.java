/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.cluster.Cluster;
import akka.dispatch.OnComplete;
import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.RoleChangeStrategy;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.util.BaseNodeManager;
import org.opendaylight.netconf.topology.util.BaseTopologyManager;
import org.opendaylight.netconf.topology.util.messages.AnnounceMasterMountPoint;
import org.opendaylight.netconf.topology.util.messages.AnnounceMasterMountPointDown;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.clustered.connection.status.ClusteredStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.NodeStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

/**
 * Node manager callback responsible for creating and maintaining restconf device connector.
 */
class RestconfNodeManagerCallback implements NodeManagerCallback {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfNodeManagerCallback.class);

    private final ClusteredRestconfTopology topology;
    private final Cluster clusterExtension;
    private final RoleChangeStrategy roleChangeStrategy;

    private String nodeId;
    private Node currentOperational;
    private ActorContext cachedContext;
    private TopologyManager topologyManager;
    private NodeManager nodeManager;
    private ActorRef masterRestconfFacadeRef;
    private boolean isMaster;

    public RestconfNodeManagerCallback(final String nodeId,
                                       final String topologyId,
                                       final ActorSystem actorSystem,
                                       final ClusteredRestconfTopology topology,
                                       final RoleChangeStrategy roleChangeStrategy) {
        this.nodeId = nodeId;
        this.topology = topology;
        this.clusterExtension = Cluster.get(actorSystem);
        this.roleChangeStrategy = roleChangeStrategy;

        final Future<ActorRef> topologyRefFuture = actorSystem.actorSelection("/user/" + topologyId).resolveOne(FiniteDuration.create(10L, TimeUnit.SECONDS));
        topologyRefFuture.onComplete(new OnComplete<ActorRef>() {
            @Override
            public void onComplete(final Throwable throwable, final ActorRef actorRef) throws Throwable {
                if (throwable != null) {
                    LOG.warn("Unable to resolve actor for path: {} ", "/user/" + topologyId, throwable);

                }

                LOG.debug("Actor ref for path {} resolved", "/user/" + topologyId);
                final TypedProps<BaseTopologyManager> props =
                        new TypedProps<>(TopologyManager.class, BaseTopologyManager.class).withTimeout(topology.getAskTimeout());
                topologyManager = TypedActor.get(actorSystem).typedActorOf(props, actorRef);
            }
        }, actorSystem.dispatcher());

        final Future<ActorRef> nodeRefFuture = actorSystem.actorSelection("/user/" + topologyId + "/" + nodeId).resolveOne(FiniteDuration.create(10L, TimeUnit.SECONDS));
        nodeRefFuture.onComplete(new OnComplete<ActorRef>() {
            @Override
            public void onComplete(final Throwable throwable, final ActorRef actorRef) throws Throwable {
                if (throwable != null) {
                    LOG.warn("Unable to resolve actor for path: {} ", "/user/" + topologyId + "/" + nodeId, throwable);
                }
                LOG.debug("Actor ref for path {} resolved", "/user/" + topologyId + "/" + nodeId);
                final TypedProps<BaseNodeManager> baseNodeManagerTypedProps =
                        new TypedProps<>(NodeManager.class, BaseNodeManager.class).withTimeout(topology.getAskTimeout());
                nodeManager = TypedActor.get(actorSystem).typedActorOf(baseNodeManagerTypedProps, actorRef);
            }
        }, actorSystem.dispatcher());
    }

    @Nonnull
    @Override
    public Node getInitialState(@Nonnull final NodeId nodeId, @Nonnull final Node node) {
        return TopologyUtil.buildState(nodeId, clusterExtension.selfAddress().toString(), ClusteredStatus.Status.Unavailable,
                NodeStatus.Status.Connecting, null);
    }

    @Nonnull
    @Override
    public Node getFailedState(@Nonnull final NodeId nodeId, @Nullable final Node node) {
        return TopologyUtil.buildState(nodeId, clusterExtension.selfAddress().toString(), ClusteredStatus.Status.Failed,
                NodeStatus.Status.Failed, null);
    }

    @Nonnull
    @Override
    public ListenableFuture<Node> onNodeCreated(@Nonnull final NodeId nodeId, @Nonnull final Node node) {
        LOG.info("Node created");
        cachedContext = TypedActor.context();
        this.nodeId = nodeId.getValue();
        // set initial state before anything happens
        this.currentOperational = getInitialState(nodeId, node);

        // connect magic, send config into the restconf pipeline through topo dispatcher
        final ListenableFuture<List<Module>> connectionFuture = topology.connectNode(nodeId, node);
        Futures.addCallback(connectionFuture, new FutureCallback<List<Module>>() {
            @Override
            public void onSuccess(@Nullable final List<Module> result) {
                synchronized (RestconfNodeManagerCallback.this) {
                    roleChangeStrategy.registerRoleCandidate(nodeManager);
                }
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.error("Connection to device failed", t);
            }
        });


        // transform future result into state that gets written into datastore
        return Futures.transform(connectionFuture, new Function<List<Module>, Node>() {
            @Nullable
            @Override
            public Node apply(final List<Module> input) {
                // build state data
                currentOperational = TopologyUtil.buildState(nodeId, clusterExtension.selfAddress().toString(),
                        ClusteredStatus.Status.Connected, NodeStatus.Status.Connected, input);
                return currentOperational;
            }
        });
    }

    @Nonnull
    @Override
    public ListenableFuture<Node> onNodeUpdated(@Nonnull final NodeId nodeId, @Nonnull final Node node) {
        final ListenableFuture<Void> deleteFuture = onNodeDeleted(nodeId);
        return Futures.transform(deleteFuture, new AsyncFunction<Void, Node>() {
            @Override
            public ListenableFuture<Node> apply(@Nullable final Void input) throws Exception {
                return onNodeCreated(nodeId, node);
            }
        });
    }

    @Nonnull
    @Override
    public ListenableFuture<Void> onNodeDeleted(@Nonnull final NodeId nodeId) {
        // cleanup and disconnect
        synchronized (this) {
            roleChangeStrategy.unregisterRoleCandidate();
        }
        onDeviceDisconnected();
        return topology.disconnectNode(nodeId);
    }

    @Nonnull
    @Override
    public ListenableFuture<Node> getCurrentStatusForNode(@Nonnull final NodeId nodeId) {
        LOG.info("Getting current status for node: {}", nodeId);
        return Futures.immediateFuture(currentOperational);
    }

    @Override
    public void onReceive(final Object message, final ActorRef actorRef) {
        LOG.info("Master = {} Restconf node callback received message {} from {}", isMaster, message, actorRef);
        if (isMaster) {
            return;
        }
        if (message instanceof AnnounceMasterMountPoint) {
            masterRestconfFacadeRef = actorRef;
            // candidate gets registered when mount point is already prepared so we can go ahead a register it
            topology.registerSlaveMountPoint(TypedActor.context(), new NodeId(nodeId), masterRestconfFacadeRef);
        } else if (message instanceof AnnounceMasterMountPointDown) {
            if (masterRestconfFacadeRef != null && masterRestconfFacadeRef.equals(actorRef)) {
                LOG.info("Received AnnounceMasterMountPointDown from former master {}, ignoring", actorRef);
                return;
            }
            LOG.debug("Master mountpoint went down");
            masterRestconfFacadeRef = null;
            topology.unregisterMountPoint(new NodeId(nodeId));
        }
    }

    @Override
    public void onDeviceConnected(final SchemaContext remoteSchemaContext,
                                  final NetconfSessionPreferences netconfSessionPreferences,
                                  final DOMRpcService deviceRpc) {
        //no op
    }

    @Override
    public void onDeviceDisconnected() {
        // we need to notify the higher level that something happened, get a current status from all other nodes, and aggregate a new result
        LOG.debug("onDeviceDisconnected received, unregistered role candidate");
        if (isMaster) {
            // set master to false since we are unregistering, the ownershipChanged callback can sometimes lag behind causing multiple nodes behaving as masters
            isMaster = false;
            // onRoleChanged() callback can sometimes lag behind, so unregister the mount right when it disconnects
            topology.unregisterMountPoint(new NodeId(nodeId));
        }

        currentOperational = TopologyUtil.buildState(new NodeId(nodeId), clusterExtension.selfAddress().toString(),
                ClusteredStatus.Status.Unavailable, null, Collections.<Module>emptyList());
        topologyManager.notifyNodeStatusChange(new NodeId(nodeId));
    }

    @Override
    public void onDeviceFailed(final Throwable throwable) {
        //no op
    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        //no op
    }

    @Override
    public void close() {
        //no op
    }

    @Override
    public synchronized void onRoleChanged(final RoleChangeDTO roleChangeDTO) {
        isMaster = roleChangeDTO.isOwner();
        LOG.info("Role changed: master {}", isMaster);
        if (roleChangeDTO.isOwner()) {
            registerMaster();
        }
    }

    private void registerMaster() {
        topology.unregisterMountPoint(new NodeId(nodeId));
        LOG.info("Device connected");
        // we need to notify the higher level that something happened, get a current status from all other nodes, and aggregate a new result
        LOG.debug("Gained ownership of entity, registering master mount point");
        topology.registerMasterMountPoint(TypedActor.context(), new NodeId(nodeId));
        topologyManager.notifyNodeStatusChange(currentOperational.getNodeId());
    }

}

/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.cluster.Cluster;
import akka.dispatch.OnComplete;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.topology.NetconfTopology;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.RoleChangeStrategy;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.pipeline.ClusteredNetconfDeviceCommunicator.NetconfClientSessionListenerRegistration;
import org.opendaylight.netconf.topology.pipeline.TopologyMountPointFacade.ConnectionStatusListenerRegistration;
import org.opendaylight.netconf.topology.util.BaseNodeManager;
import org.opendaylight.netconf.topology.util.BaseTopologyManager;
import org.opendaylight.netconf.topology.util.messages.AnnounceMasterMountPoint;
import org.opendaylight.netconf.topology.util.messages.AnnounceMasterMountPointDown;
import org.opendaylight.netconf.util.NetconfTopologyPathCreator;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.AvailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.UnavailableCapabilities;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.UnavailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.clustered.connection.status.NodeStatus.Status;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.clustered.connection.status.NodeStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.unavailable.capabilities.UnavailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.unavailable.capabilities.UnavailableCapability.FailureReason;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.unavailable.capabilities.UnavailableCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

public class NetconfNodeManagerCallback implements NodeManagerCallback, NetconfClientSessionListener{

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeManagerCallback.class);

    public static final Function<Entry<QName, FailureReason>, UnavailableCapability> UNAVAILABLE_CAPABILITY_TRANSFORMER = new Function<Entry<QName, FailureReason>, UnavailableCapability>() {
        @Override
        public UnavailableCapability apply(final Entry<QName, FailureReason> input) {
            return new UnavailableCapabilityBuilder()
                    .setCapability(input.getKey().toString())
                    .setFailureReason(input.getValue()).build();
        }
    };

    private static final String UNKNOWN_REASON = "Unknown reason";

    private boolean isMaster = false;
    private ClusteredNetconfTopology topologyDispatcher;
    private final ActorSystem actorSystem;
    private final Cluster clusterExtension;

    private final RoleChangeStrategy roleChangeStrategy;

    private String nodeId;
    private String topologyId;
    private TopologyManager topologyManager;
    private NodeManager nodeManager;
    // cached context so that we can use it in callbacks from topology
    private ActorContext cachedContext;

    private Node currentConfig;
    private Node currentOperationalNode;

    private ConnectionStatusListenerRegistration connectionStatusregistration = null;
    private NetconfClientSessionListenerRegistration sessionListener = null;

    private ActorRef masterDataBrokerRef = null;
    private boolean connected = false;

    public NetconfNodeManagerCallback(final String nodeId,
                                      final String topologyId,
                                      final ActorSystem actorSystem,
                                      final NetconfTopology topologyDispatcher,
                                      final RoleChangeStrategy roleChangeStrategy) {
        this.nodeId = nodeId;
        this.topologyId = topologyId;
        this.actorSystem = actorSystem;
        this.clusterExtension = Cluster.get(actorSystem);
        this.topologyDispatcher = (ClusteredNetconfTopology) topologyDispatcher;
        this.roleChangeStrategy = roleChangeStrategy;

        final NetconfTopologyPathCreator pathCreator = new NetconfTopologyPathCreator(topologyId);
        final Future<ActorRef> topologyRefFuture = actorSystem.actorSelection(pathCreator.build()).resolveOne(FiniteDuration.create(10L, TimeUnit.SECONDS));
        topologyRefFuture.onComplete(new OnComplete<ActorRef>() {
            @Override
            public void onComplete(Throwable throwable, ActorRef actorRef) throws Throwable {
                if (throwable != null) {
                    LOG.warn("Unable to resolve actor for path: {} ", "/user/" + topologyId, throwable);

                }

                LOG.debug("Actor ref for path {} resolved", "/user/" + topologyId);
                topologyManager = TypedActor.get(actorSystem).typedActorOf(new TypedProps<>(TopologyManager.class, BaseTopologyManager.class), actorRef);
            }
        }, actorSystem.dispatcher());

        final Future<ActorRef> nodeRefFuture = actorSystem.actorSelection(pathCreator.withSuffix(nodeId).build()).resolveOne(FiniteDuration.create(10L, TimeUnit.SECONDS));
        nodeRefFuture.onComplete(new OnComplete<ActorRef>() {
            @Override
            public void onComplete(Throwable throwable, ActorRef actorRef) throws Throwable {
                if (throwable != null) {
                    LOG.warn("Unable to resolve actor for path: {} ", "/user/" + topologyId + "/" + nodeId, throwable);
                }
                LOG.debug("Actor ref for path {} resolved", "/user/" + topologyId);
                nodeManager = TypedActor.get(actorSystem).typedActorOf(new TypedProps<>(NodeManager.class, BaseNodeManager.class), actorRef);
            }
        }, actorSystem.dispatcher());
    }


    @Nonnull
    @Override public Node getInitialState(@Nonnull final NodeId nodeId,
                                          @Nonnull final Node configNode) {
        final NetconfNode netconfNode = configNode.getAugmentation(NetconfNode.class);

        final Node initialNode = new NodeBuilder()
                .setNodeId(nodeId)
                .addAugmentation(NetconfNode.class,
                        new NetconfNodeBuilder()
                                .setHost(netconfNode.getHost())
                                .setPort(netconfNode.getPort())
                                .setConnectionStatus(ConnectionStatus.Connecting)
                                .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                .setClusteredConnectionStatus(
                                        new ClusteredConnectionStatusBuilder()
                                                .setNodeStatus(
                                                        Lists.newArrayList(
                                                                new NodeStatusBuilder()
                                                                        .setNode(clusterExtension.selfAddress().toString())
                                                                        .setStatus(Status.Unavailable)
                                                                        .build()))
                                                .build())
                                .build())
                .build();

        if (currentOperationalNode == null) {
            currentOperationalNode = initialNode;
        }

        return initialNode;
    }

    @Nonnull @Override public Node getFailedState(@Nonnull final NodeId nodeId,
                                                  @Nullable final Node configNode) {
        final NetconfNode netconfNode = configNode == null ? currentOperationalNode.getAugmentation(NetconfNode.class) : configNode.getAugmentation(NetconfNode.class);

        final Node failedNode = new NodeBuilder()
                .setNodeId(nodeId)
                .addAugmentation(NetconfNode.class,
                        new NetconfNodeBuilder()
                                .setHost(netconfNode.getHost())
                                .setPort(netconfNode.getPort())
                                .setConnectionStatus(ConnectionStatus.UnableToConnect)
                                .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                .setClusteredConnectionStatus(
                                        new ClusteredConnectionStatusBuilder()
                                                .setNodeStatus(
                                                        Collections.singletonList(
                                                                new NodeStatusBuilder()
                                                                        .setNode(clusterExtension.selfAddress().toString())
                                                                        .setStatus(Status.Failed)
                                                                        .build()))
                                                .build())
                                .build())
                .build();

        if (currentOperationalNode == null) {
            currentOperationalNode = failedNode;
        }

        return failedNode;
    }

    @Nonnull @Override public ListenableFuture<Node> onNodeCreated(@Nonnull final NodeId nodeId,
                                                                   @Nonnull final Node configNode) {
        cachedContext = TypedActor.context();
        this.nodeId = nodeId.getValue();
        this.currentConfig = configNode;
        // set initial state before anything happens
        this.currentOperationalNode = getInitialState(nodeId, configNode);

        // connect magic, send config into the netconf pipeline through topo dispatcher
        final ListenableFuture<NetconfDeviceCapabilities> connectionFuture = topologyDispatcher.connectNode(nodeId, configNode);

        Futures.addCallback(connectionFuture, new FutureCallback<NetconfDeviceCapabilities>() {
            @Override
            public void onSuccess(@Nullable NetconfDeviceCapabilities result) {
                connectionStatusregistration = topologyDispatcher.registerConnectionStatusListener(nodeId, nodeManager);
                sessionListener = topologyDispatcher.registerNetconfClientSessionListener(nodeId, NetconfNodeManagerCallback.this);
            }

            @Override
            public void onFailure(Throwable t) {
                LOG.error("Connection to device failed", t);
            }
        });

        final NetconfNode netconfNode = configNode.getAugmentation(NetconfNode.class);

        // transform future result into state that gets written into datastore
        return Futures.transform(connectionFuture, new Function<NetconfDeviceCapabilities, Node>() {
            @Nullable
            @Override
            public Node apply(NetconfDeviceCapabilities input) {
                // build state data
                currentOperationalNode = new NodeBuilder().setNodeId(nodeId)
                        .addAugmentation(NetconfNode.class,
                                new NetconfNodeBuilder()
                                        .setConnectionStatus(ConnectionStatus.Connected)
                                        .setClusteredConnectionStatus(
                                                new ClusteredConnectionStatusBuilder()
                                                        .setNodeStatus(
                                                                Collections.singletonList(
                                                                        new NodeStatusBuilder()
                                                                                .setNode(clusterExtension.selfAddress().toString())
                                                                                .setStatus(Status.Connected)
                                                                                .build()))
                                                        .build())
                                        .setHost(netconfNode.getHost())
                                        .setPort(netconfNode.getPort())
                                        .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                        .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                        .build()).build();
                return currentOperationalNode;
            }
        });
    }

    @Nonnull
    @Override
    public ListenableFuture<Node> onNodeUpdated(@Nonnull final NodeId nodeId,
                                                @Nonnull final Node configNode) {
        // first disconnect this node
        topologyDispatcher.unregisterMountPoint(nodeId);

        if (connectionStatusregistration != null) {
            connectionStatusregistration.close();
        }
        topologyDispatcher.disconnectNode(nodeId);

        // now reinit this connection with new settings
        final ListenableFuture<NetconfDeviceCapabilities> connectionFuture = topologyDispatcher.connectNode(nodeId, configNode);

        Futures.addCallback(connectionFuture, new FutureCallback<NetconfDeviceCapabilities>() {
            @Override
            public void onSuccess(@Nullable NetconfDeviceCapabilities result) {
                connectionStatusregistration = topologyDispatcher.registerConnectionStatusListener(nodeId, NetconfNodeManagerCallback.this);
            }

            @Override
            public void onFailure(Throwable t) {
                LOG.error("Connection to device failed", t);
            }
        });

        final NetconfNode netconfNode = configNode.getAugmentation(NetconfNode.class);

        return Futures.transform(connectionFuture, new Function<NetconfDeviceCapabilities, Node>() {
            @Nullable
            @Override
            public Node apply(NetconfDeviceCapabilities input) {
                // build state data
                return new NodeBuilder()
                        .setNodeId(nodeId)
                        .addAugmentation(NetconfNode.class,
                                new NetconfNodeBuilder()
                                        .setConnectionStatus(ConnectionStatus.Connected)
                                        .setClusteredConnectionStatus(
                                                new ClusteredConnectionStatusBuilder()
                                                        .setNodeStatus(
                                                                Collections.singletonList(
                                                                        new NodeStatusBuilder()
                                                                                .setNode(clusterExtension.selfAddress().toString())
                                                                                .setStatus(Status.Connected)
                                                                                .build()))
                                                        .build())
                                        .setHost(netconfNode.getHost())
                                        .setPort(netconfNode.getPort())
                                        .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                        .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                        .build())
                        .build();
            }
        });
    }

    @Nonnull @Override public ListenableFuture<Void> onNodeDeleted(@Nonnull final NodeId nodeId) {
        // cleanup and disconnect
        topologyDispatcher.unregisterMountPoint(nodeId);

        if(connectionStatusregistration != null) {
            connectionStatusregistration.close();
        }
        roleChangeStrategy.unregisterRoleCandidate();
        return topologyDispatcher.disconnectNode(nodeId);
    }

    @Nonnull
    @Override
    public ListenableFuture<Node> getCurrentStatusForNode(@Nonnull NodeId nodeId) {
        LOG.debug("Getting current status for node: {} status: {}", nodeId, currentOperationalNode);
        return Futures.immediateFuture(currentOperationalNode);
    }

    @Override
    public void onRoleChanged(final RoleChangeDTO roleChangeDTO) {
        topologyDispatcher.unregisterMountPoint(new NodeId(nodeId));

        isMaster = roleChangeDTO.isOwner();
    }

    @Override
    public void onDeviceConnected(final SchemaContext remoteSchemaContext, final NetconfSessionPreferences netconfSessionPreferences, final DOMRpcService deviceRpc) {
        // we need to notify the higher level that something happened, get a current status from all other nodes, and aggregate a new result
        connected = true;
        if (isMaster) {
            LOG.debug("Master is done with schema resolution, registering mount point");
            topologyDispatcher.registerMountPoint(TypedActor.context(), new NodeId(nodeId));
        } else if (masterDataBrokerRef != null) {
            LOG.warn("Device connected, master already present in topology, registering mount point");
            topologyDispatcher.registerMountPoint(cachedContext, new NodeId(nodeId), masterDataBrokerRef);
        }

        List<AvailableCapability> capabilityList = new ArrayList<>();
        capabilityList.addAll(netconfSessionPreferences.getNetconfDeviceCapabilities().getNonModuleBasedCapabilities());
        capabilityList.addAll(netconfSessionPreferences.getNetconfDeviceCapabilities().getResolvedCapabilities());
        final AvailableCapabilitiesBuilder avCapabalitiesBuilder = new AvailableCapabilitiesBuilder();
        avCapabalitiesBuilder.setAvailableCapability(capabilityList);

        final UnavailableCapabilities unavailableCapabilities =
                new UnavailableCapabilitiesBuilder().setUnavailableCapability(FluentIterable.from(netconfSessionPreferences.getNetconfDeviceCapabilities().getUnresolvedCapabilites().entrySet())
                        .transform(UNAVAILABLE_CAPABILITY_TRANSFORMER).toList()).build();

        final NetconfNode netconfNode = currentConfig.getAugmentation(NetconfNode.class);
        currentOperationalNode = new NodeBuilder().setNodeId(new NodeId(nodeId))
                .addAugmentation(NetconfNode.class,
                        new NetconfNodeBuilder()
                                .setConnectionStatus(ConnectionStatus.Connected)
                                .setClusteredConnectionStatus(
                                        new ClusteredConnectionStatusBuilder()
                                                .setNodeStatus(
                                                        Collections.singletonList(
                                                                new NodeStatusBuilder()
                                                                        .setNode(clusterExtension.selfAddress().toString())
                                                                        .setStatus(Status.Connected)
                                                                        .build()))
                                                .build())
                                .setHost(netconfNode.getHost())
                                .setPort(netconfNode.getPort())
                                .setAvailableCapabilities(avCapabalitiesBuilder.build())
                                .setUnavailableCapabilities(unavailableCapabilities)
                                .build())
                .build();
        topologyManager.notifyNodeStatusChange(new NodeId(nodeId));
    }

    @Override
    public void onDeviceDisconnected() {
        // we need to notify the higher level that something happened, get a current status from all other nodes, and aggregate a new result
        LOG.debug("onDeviceDisconnected received, unregistered role candidate");
        connected = false;
        if (isMaster) {
            // set master to false since we are unregistering, the ownershipChanged callback can sometimes lag behind causing multiple nodes behaving as masters
            isMaster = false;
            // onRoleChanged() callback can sometimes lag behind, so unregister the mount right when it disconnects
            topologyDispatcher.unregisterMountPoint(new NodeId(nodeId));
        }

        final NetconfNode netconfNode = currentConfig.getAugmentation(NetconfNode.class);
        currentOperationalNode = new NodeBuilder().setNodeId(new NodeId(nodeId))
                .addAugmentation(NetconfNode.class,
                        new NetconfNodeBuilder()
                                .setConnectionStatus(ConnectionStatus.Connecting)
                                .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                .setClusteredConnectionStatus(
                                        new ClusteredConnectionStatusBuilder()
                                                .setNodeStatus(
                                                        Collections.singletonList(
                                                                new NodeStatusBuilder()
                                                                        .setNode(clusterExtension.selfAddress().toString())
                                                                        .setStatus(Status.Unavailable)
                                                                        .build()))
                                                .build())
                                .setHost(netconfNode.getHost())
                                .setPort(netconfNode.getPort())
                                .build()).build();
        topologyManager.notifyNodeStatusChange(new NodeId(nodeId));
    }

    @Override
    public void onDeviceFailed(Throwable throwable) {
        // we need to notify the higher level that something happened, get a current status from all other nodes, and aggregate a new result
        // no need to remove mountpoint, we should receive onRoleChanged callback after unregistering from election that unregisters the mountpoint
        LOG.warn("Netconf node {} failed with {}", nodeId, throwable);
        connected = false;
        String reason = (throwable != null && throwable.getMessage() != null) ? throwable.getMessage() : UNKNOWN_REASON;

        currentOperationalNode = new NodeBuilder().setNodeId(new NodeId(nodeId))
                .addAugmentation(NetconfNode.class,
                        new NetconfNodeBuilder()
                                .setConnectionStatus(ConnectionStatus.UnableToConnect)
                                .setAvailableCapabilities(new AvailableCapabilitiesBuilder().setAvailableCapability(new ArrayList<AvailableCapability>()).build())
                                .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder().setUnavailableCapability(new ArrayList<UnavailableCapability>()).build())
                                .setClusteredConnectionStatus(
                                        new ClusteredConnectionStatusBuilder()
                                                .setNodeStatus(
                                                        Collections.singletonList(
                                                                new NodeStatusBuilder()
                                                                        .setNode(clusterExtension.selfAddress().toString())
                                                                        .setStatus(Status.Failed)
                                                                        .build()))
                                                .build())
                                .setConnectedMessage(reason)
                                .build()).build();
        topologyManager.notifyNodeStatusChange(new NodeId(nodeId));
    }

    @Override
    public void onNotification(DOMNotification domNotification) {
        //NOOP
    }

    @Override
    public void close() {
        //NOOP
    }

    @Override
    public void onReceive(Object message, ActorRef actorRef) {
        LOG.debug("Netconf node callback received message {}", message);
        if (message instanceof AnnounceMasterMountPoint) {
            masterDataBrokerRef = actorRef;
            // candidate gets registered when mount point is already prepared so we can go ahead a register it
            if (connected) {
                topologyDispatcher.registerMountPoint(TypedActor.context(), new NodeId(nodeId), masterDataBrokerRef);
            } else {
                LOG.debug("Announce master mount point msg received but mount point is not ready yet");
            }
        } else if (message instanceof AnnounceMasterMountPointDown) {
            LOG.debug("Master mountpoint went down");
            masterDataBrokerRef = null;
            topologyDispatcher.unregisterMountPoint(new NodeId(nodeId));
        }
    }

    @Override
    public void onSessionUp(NetconfClientSession netconfClientSession) {
        //NetconfClientSession is up, we can register role candidate
        LOG.debug("Netconf client session is up, registering role candidate");
        roleChangeStrategy.registerRoleCandidate(nodeManager);
    }

    @Override
    public void onSessionDown(NetconfClientSession netconfClientSession, Exception e) {
        LOG.debug("Netconf client session is down, unregistering role candidate");
        roleChangeStrategy.unregisterRoleCandidate();
    }

    @Override
    public void onSessionTerminated(NetconfClientSession netconfClientSession, NetconfTerminationReason netconfTerminationReason) {
        LOG.debug("Netconf client session is down, unregistering role candidate");
        roleChangeStrategy.unregisterRoleCandidate();
    }

    @Override
    public void onMessage(NetconfClientSession netconfClientSession, NetconfMessage netconfMessage) {
        //NOOP
    }
}
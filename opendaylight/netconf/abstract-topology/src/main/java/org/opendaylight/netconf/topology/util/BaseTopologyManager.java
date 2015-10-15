/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.util;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.actor.TypedActor;
import akka.actor.TypedActorExtension;
import akka.actor.TypedProps;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.ClusterEvent.MemberExited;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.ReachableMember;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.cluster.Member;
import akka.dispatch.OnComplete;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.RoleChangeStrategy;
import org.opendaylight.netconf.topology.StateAggregator;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.TopologyManagerCallback.TopologyManagerCallbackFactory;
import org.opendaylight.netconf.topology.util.messages.CustomIdentifyMessage;
import org.opendaylight.netconf.topology.util.messages.CustomIdentifyMessageReply;
import org.opendaylight.netconf.topology.util.messages.NormalizedNodeMessage;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.binding.data.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;
import scala.concurrent.impl.Promise.DefaultPromise;

public final class BaseTopologyManager
        implements TopologyManager {

    private static final Logger LOG = LoggerFactory.getLogger(BaseTopologyManager.class);

    private final ActorSystem system;
    private final TypedActorExtension typedExtension;
    private final Cluster clusterExtension;

    private final BindingNormalizedNodeCodecRegistry codecRegistry;

    private static final String PATH = "/user/";

    private final DataBroker dataBroker;
    private final RoleChangeStrategy roleChangeStrategy;
    private final StateAggregator aggregator;

    private final NodeWriter naSalNodeWriter;
    private final String topologyId;
    private final TopologyManagerCallback delegateTopologyHandler;

    private final Map<NodeId, NodeManager> nodes = new HashMap<>();
    private final Map<Address, TopologyManager> peers = new HashMap<>();
    private TopologyManager masterPeer = null;
    private final int id = new Random().nextInt();

    private boolean isMaster;

    public BaseTopologyManager(final ActorSystem system,
                               final BindingNormalizedNodeCodecRegistry codecRegistry,
                               final DataBroker dataBroker,
                               final String topologyId,
                               final TopologyManagerCallbackFactory topologyManagerCallbackFactory,
                               final StateAggregator aggregator,
                               final NodeWriter naSalNodeWriter,
                               final RoleChangeStrategy roleChangeStrategy) {
        this(system, codecRegistry, dataBroker, topologyId, topologyManagerCallbackFactory, aggregator, naSalNodeWriter, roleChangeStrategy, false);
    }

    public BaseTopologyManager(final ActorSystem system,
                               final BindingNormalizedNodeCodecRegistry codecRegistry,
                               final DataBroker dataBroker,
                               final String topologyId,
                               final TopologyManagerCallbackFactory topologyManagerCallbackFactory,
                               final StateAggregator aggregator,
                               final NodeWriter naSalNodeWriter,
                               final RoleChangeStrategy roleChangeStrategy,
                               final boolean isMaster) {

        this.system = system;
        this.typedExtension = TypedActor.get(system);
        this.clusterExtension = Cluster.get(system);
        this.dataBroker = dataBroker;
        this.topologyId = topologyId;
        this.delegateTopologyHandler = topologyManagerCallbackFactory.create(system, dataBroker, topologyId);
        this.aggregator = aggregator;
        this.naSalNodeWriter = naSalNodeWriter;
        this.roleChangeStrategy = roleChangeStrategy;
        this.codecRegistry = codecRegistry;

        // election has not yet happened
        this.isMaster = isMaster;

        LOG.warn("Base manager started ", +id);
    }

    @Override
    public void preStart() {
        LOG.warn("preStart called");
        // TODO change to enum, master/slave active/standby
        roleChangeStrategy.registerRoleCandidate(TypedActor.<BaseTopologyManager>self());
        LOG.warn("candidate registered");
        clusterExtension.subscribe(TypedActor.context().self(), ClusterEvent.initialStateAsEvents(), MemberEvent.class, UnreachableMember.class);
    }

    @Override
    public void postStop() {
        LOG.warn("postStop called");
        clusterExtension.leave(clusterExtension.selfAddress());
        clusterExtension.unsubscribe(TypedActor.context().self());
    }

    @Override
    public ListenableFuture<Node> nodeCreated(final NodeId nodeId, final Node node) {
        LOG.warn("TopologyManager({}) nodeCreated received, nodeid: {} , isMaster: {}", id, nodeId.getValue(), isMaster);

        final ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        if (isMaster) {

            futures.add(delegateTopologyHandler.nodeCreated(nodeId, node));
            // only master should call connect on peers and aggregate futures
            for (TopologyManager topologyManager : peers.values()) {
                // convert binding into NormalizedNode for transfer
                final Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> normalizedNodeEntry = codecRegistry.toNormalizedNode(getNodeIid(topologyId), node);

                LOG.debug("YangInstanceIdentifier {}", normalizedNodeEntry.getKey());
                LOG.debug("Value {}", normalizedNodeEntry.getValue());

                // add a future into our futures that gets its completion status from the converted scala future
                final SettableFuture<Node> settableFuture = SettableFuture.create();
                futures.add(settableFuture);
                final Future<NormalizedNodeMessage> scalaFuture = topologyManager.remoteNodeCreated(new NormalizedNodeMessage(normalizedNodeEntry.getKey(), normalizedNodeEntry.getValue()));
                scalaFuture.onComplete(new OnComplete<NormalizedNodeMessage>() {
                    @Override
                    public void onComplete(Throwable failure, NormalizedNodeMessage success) throws Throwable {
                        if (failure != null) {
                            settableFuture.setException(failure);
                            return;
                        }
                        final Entry<InstanceIdentifier<?>, DataObject> fromNormalizedNode =
                                codecRegistry.fromNormalizedNode(success.getIdentifier(), success.getNode());
                        final Node value = (Node) fromNormalizedNode.getValue();

                        settableFuture.set(value);
                    }
                }, TypedActor.context().dispatcher());
            }

            final ListenableFuture<Node> aggregatedFuture = aggregator.combineCreateAttempts(futures);
            Futures.addCallback(aggregatedFuture, new FutureCallback<Node>() {
                @Override
                public void onSuccess(final Node result) {
                    LOG.debug("Futures aggregated succesfully");
                    naSalNodeWriter.update(nodeId, result);
                }

                @Override
                public void onFailure(final Throwable t) {
                    // If the combined connection attempt failed, set the node to connection failed
                    LOG.debug("Futures aggregation failed");
                    naSalNodeWriter.update(nodeId, nodes.get(nodeId).getFailedState(nodeId, node));
                    // FIXME disconnect those which succeeded
                    // just issue a delete on delegateTopologyHandler that gets handled on lower level
                }
            }, TypedActor.context().dispatcher());

            //combine peer futures
            return aggregatedFuture;
        }

        // trigger create on this slave
        return delegateTopologyHandler.nodeCreated(nodeId, node);
    }

    @Override
    public ListenableFuture<Node> nodeUpdated(final NodeId nodeId, final Node node) {
        LOG.warn("TopologyManager({}) nodeUpdated received, nodeid: {}", id, nodeId.getValue());

        final ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        // Master needs to trigger nodeUpdated on peers and combine results
        if (isMaster) {
            futures.add(delegateTopologyHandler.nodeUpdated(nodeId, node));
            for (TopologyManager topologyManager : peers.values()) {
                // convert binding into NormalizedNode for transfer
                final Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> normalizedNodeEntry = codecRegistry.toNormalizedNode(getNodeIid(topologyId), node);

                // add a future into our futures that gets its completion status from the converted scala future
                final SettableFuture<Node> settableFuture = SettableFuture.create();
                futures.add(settableFuture);
                final Future<NormalizedNodeMessage> scalaFuture = topologyManager.remoteNodeUpdated(new NormalizedNodeMessage(normalizedNodeEntry.getKey(), normalizedNodeEntry.getValue()));
                scalaFuture.onComplete(new OnComplete<NormalizedNodeMessage>() {
                    @Override
                    public void onComplete(Throwable failure, NormalizedNodeMessage success) throws Throwable {
                        if (failure != null) {
                            settableFuture.setException(failure);
                            return;
                        }
                        final Entry<InstanceIdentifier<?>, DataObject> fromNormalizedNode =
                                codecRegistry.fromNormalizedNode(success.getIdentifier(), success.getNode());
                        final Node value = (Node) fromNormalizedNode.getValue();

                        settableFuture.set(value);
                    }
                }, TypedActor.context().dispatcher());
            }

            final ListenableFuture<Node> aggregatedFuture = aggregator.combineUpdateAttempts(futures);
            Futures.addCallback(aggregatedFuture, new FutureCallback<Node>() {
                @Override
                public void onSuccess(final Node result) {
                    // FIXME make this (writing state data for nodes) optional and customizable
                    // this should be possible with providing your own NodeWriter implementation, maybe rename this interface?
                    naSalNodeWriter.update(nodeId, result);
                }

                @Override
                public void onFailure(final Throwable t) {
                    // If the combined connection attempt failed, set the node to connection failed
                    naSalNodeWriter.update(nodeId, nodes.get(nodeId).getFailedState(nodeId, node));
                    // FIXME disconnect those which succeeded
                    // just issue a delete on delegateTopologyHandler that gets handled on lower level
                }
            });

            //combine peer futures
            return aggregatedFuture;
        }

        // Trigger update on this slave
        return delegateTopologyHandler.nodeUpdated(nodeId, node);
    }

    private static InstanceIdentifier<Node> getNodeIid(final String topologyId) {
        final InstanceIdentifier<NetworkTopology> networkTopology = InstanceIdentifier.create(NetworkTopology.class);
        return networkTopology.child(Topology.class, new TopologyKey(new TopologyId(topologyId))).child(Node.class);
    }

    @Override
    public ListenableFuture<Void> nodeDeleted(final NodeId nodeId) {
        final ArrayList<ListenableFuture<Void>> futures = new ArrayList<>();

        // Master needs to trigger delete on peers and combine results
        if (isMaster) {
            futures.add(delegateTopologyHandler.nodeDeleted(nodeId));
            for (TopologyManager topologyManager : peers.values()) {
                // add a future into our futures that gets its completion status from the converted scala future
                final SettableFuture<Void> settableFuture = SettableFuture.create();
                futures.add(settableFuture);
                final Future<Void> scalaFuture = topologyManager.remoteNodeDeleted(nodeId);
                scalaFuture.onComplete(new OnComplete<Void>() {
                    @Override
                    public void onComplete(Throwable failure, Void success) throws Throwable {
                        if (failure != null) {
                            settableFuture.setException(failure);
                            return;
                        }

                        settableFuture.set(success);
                    }
                }, TypedActor.context().dispatcher());
            }

            final ListenableFuture<Void> aggregatedFuture = aggregator.combineDeleteAttempts(futures);
            Futures.addCallback(aggregatedFuture, new FutureCallback<Void>() {
                @Override
                public void onSuccess(final Void result) {
                    naSalNodeWriter.delete(nodeId);
                }

                @Override
                public void onFailure(final Throwable t) {
                    // FIXME unable to disconnect all the connections, what do we do now ?
                }
            });

            return aggregatedFuture;
        }

        // Trigger delete
        return delegateTopologyHandler.nodeDeleted(nodeId);
    }

    @Nonnull
    @Override
    public ListenableFuture<Node> getCurrentStatusForNode(@Nonnull final NodeId nodeId) {
        return delegateTopologyHandler.getCurrentStatusForNode(nodeId);
    }

    @Override
    public void onRoleChanged(final RoleChangeDTO roleChangeDTO) {
        isMaster = roleChangeDTO.isOwner();
        delegateTopologyHandler.onRoleChanged(roleChangeDTO);
        if (isMaster) {
            LOG.warn("Node {} is master now", clusterExtension.selfAddress());
            clusterExtension.join(clusterExtension.selfAddress());
        }
//        else {
//            for (final TopologyManager manager : peers.values()) {
//                // asynchronously find out which peer is master
//                final Future<Boolean> future = manager.isMaster();
//                future.onComplete(new OnComplete<Boolean>() {
//                    @Override
//                    public void onComplete(Throwable failure, Boolean success) throws Throwable {
//                        if (failure == null && success) {
//                            masterPeer = manager;
//                            return;
//                        }
//                        if (failure != null) {
//                            LOG.debug();
//                        }
//                    }
//                }, TypedActor.context().dispatcher());
//            }
//        }
    }

    @Override
    public Future<Boolean> isMaster() {
        return new DefaultPromise<Boolean>().success(isMaster).future();
    }

    @Override
    public void notifyNodeStatusChange(final NodeId nodeId) {
        LOG.debug("Connection status has changed on node {}", nodeId.getValue());
        if (isMaster) {
            // grab status from all peers and aggregate
            final ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();
            futures.add(delegateTopologyHandler.getCurrentStatusForNode(nodeId));
            // only master should call connect on peers and aggregate futures
            for (TopologyManager topologyManager : peers.values()) {
                // add a future into our futures that gets its completion status from the converted scala future
                final SettableFuture<Node> settableFuture = SettableFuture.create();
                futures.add(settableFuture);
                final Future<NormalizedNodeMessage> scalaFuture = topologyManager.remoteGetCurrentStatusForNode(nodeId);
                scalaFuture.onComplete(new OnComplete<NormalizedNodeMessage>() {
                    @Override
                    public void onComplete(Throwable failure, NormalizedNodeMessage success) throws Throwable {
                        if (failure != null) {
                            settableFuture.setException(failure);
                            return;
                        }
                        final Entry<InstanceIdentifier<?>, DataObject> fromNormalizedNode =
                                codecRegistry.fromNormalizedNode(success.getIdentifier(), success.getNode());
                        final Node value = (Node) fromNormalizedNode.getValue();

                        settableFuture.set(value);
                    }
                }, TypedActor.context().dispatcher());
            }

            final ListenableFuture<Node> aggregatedFuture = aggregator.combineUpdateAttempts(futures);
            Futures.addCallback(aggregatedFuture, new FutureCallback<Node>() {
                @Override
                public void onSuccess(final Node result) {
                    LOG.debug("Futures aggregated succesfully");
                    naSalNodeWriter.update(nodeId, result);
                }

                @Override
                public void onFailure(final Throwable t) {
                    // If the combined connection attempt failed, set the node to connection failed
                    LOG.debug("Futures aggregation failed");
                    naSalNodeWriter.update(nodeId, nodes.get(nodeId).getFailedState(nodeId, null));
                    // FIXME disconnect those which succeeded
                    // just issue a delete on delegateTopologyHandler that gets handled on lower level
                }
            });
            return;
        }
        LOG.debug("Not master, forwarding..");
        for (final TopologyManager manager : peers.values()) {
            // asynchronously find out which peer is master
            final Future<Boolean> future = manager.isMaster();
            future.onComplete(new OnComplete<Boolean>() {
                @Override
                public void onComplete(Throwable failure, Boolean success) throws Throwable {
                    if (failure == null && success) {
                        LOG.debug("Found master peer");
                        // forward to master
                        manager.notifyNodeStatusChange(nodeId);
                        return;
                    }
                    if (failure != null) {
                        LOG.debug("Retrieving master peer failed, {}", failure);
                    }
                }
            }, TypedActor.context().dispatcher());
        }
    }

    @Override
    public boolean hasAllPeersUp() {
        LOG.debug("Peers needed: {} Peers up: {}", 2, peers.size());
        LOG.warn(clusterExtension.state().toString());
        LOG.warn(peers.toString());
        return peers.size() == 2;
    }

    @Override
    public Future<NormalizedNodeMessage> remoteNodeCreated(final NormalizedNodeMessage message) {
        final Entry<InstanceIdentifier<?>, DataObject> fromNormalizedNode =
                codecRegistry.fromNormalizedNode(message.getIdentifier(), message.getNode());
        final InstanceIdentifier<Node> iid = (InstanceIdentifier<Node>) fromNormalizedNode.getKey();
        final Node value = (Node) fromNormalizedNode.getValue();

        LOG.debug("TopologyManager({}) remoteNodeCreated received, nodeid: {}", value.getNodeId(), value);
        final ListenableFuture<Node> nodeListenableFuture = nodeCreated(value.getNodeId(), value);
        final DefaultPromise<NormalizedNodeMessage> promise = new DefaultPromise<>();
        Futures.addCallback(nodeListenableFuture, new FutureCallback<Node>() {
            @Override
            public void onSuccess(Node result) {
                final Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> entry = codecRegistry.toNormalizedNode(iid, result);
                promise.success(new NormalizedNodeMessage(entry.getKey(), entry.getValue()));
            }

            @Override
            public void onFailure(Throwable t) {
                promise.failure(t);
            }
        });

        return promise.future();
    }

    @Override
    public Future<NormalizedNodeMessage> remoteNodeUpdated(final NormalizedNodeMessage message) {
        final Entry<InstanceIdentifier<?>, DataObject> fromNormalizedNode =
                codecRegistry.fromNormalizedNode(message.getIdentifier(), message.getNode());
        final InstanceIdentifier<Node> iid = (InstanceIdentifier<Node>) fromNormalizedNode.getKey();
        final Node value = (Node) fromNormalizedNode.getValue();

        LOG.warn("TopologyManager({}) remoteNodeUpdated received, nodeid: {}", id, value.getNodeId());

        final ListenableFuture<Node> nodeListenableFuture = nodeUpdated(value.getNodeId(), value);
        final DefaultPromise<NormalizedNodeMessage> promise = new DefaultPromise<>();
        Futures.addCallback(nodeListenableFuture, new FutureCallback<Node>() {
            @Override
            public void onSuccess(Node result) {
                final Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> entry = codecRegistry.toNormalizedNode(iid, result);
                promise.success(new NormalizedNodeMessage(entry.getKey(), entry.getValue()));
            }

            @Override
            public void onFailure(Throwable t) {
                promise.failure(t);
            }
        });
        return promise.future();
    }

    @Override
    public Future<Void> remoteNodeDeleted(final NodeId nodeId) {
        LOG.warn("TopologyManager({}) remoteNodeDeleted received, nodeid: {}", id, nodeId.getValue());

        final ListenableFuture<Void> listenableFuture = nodeDeleted(nodeId);
        final DefaultPromise<Void> promise = new DefaultPromise<>();
        Futures.addCallback(listenableFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                promise.success(null);
            }

            @Override
            public void onFailure(Throwable t) {
                promise.failure(t);
            }
        });

        return promise.future();
    }

    public Future<NormalizedNodeMessage> remoteGetCurrentStatusForNode(final NodeId nodeId) {
        LOG.warn("TopologyManager({}) remoteGetCurrentStatusForNode received, nodeid: {}", id, nodeId.getValue());

        final ListenableFuture<Node> listenableFuture = getCurrentStatusForNode(nodeId);
        final DefaultPromise<NormalizedNodeMessage> promise = new DefaultPromise<>();
        Futures.addCallback(listenableFuture, new FutureCallback<Node>() {
            @Override
            public void onSuccess(Node result) {
                final Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> entry = codecRegistry.toNormalizedNode(getNodeIid(topologyId), result);
                promise.success(new NormalizedNodeMessage(entry.getKey(), entry.getValue()));
            }

            @Override
            public void onFailure(Throwable t) {
                promise.failure(t);
            }
        });
        return promise.future();
    }

    @Override
    public void onReceive(final Object message, final ActorRef actorRef) {
        LOG.warn("message received {}", message);
        if (message instanceof MemberUp) {
            final Member member = ((MemberUp) message).member();
            LOG.info("Member is Up: {}", member);
            if (member.address().equals(clusterExtension.selfAddress())) {
                return;
            }

            final String path = member.address() + PATH + topologyId;
            LOG.warn("Actor at :{} is resolving topology actor for path {}", clusterExtension.selfAddress(), path);

            clusterExtension.system().actorSelection(path).tell(new CustomIdentifyMessage(clusterExtension.selfAddress()), TypedActor.context().self());
        } else if (message instanceof MemberExited) {
            // remove peer
            final Member member = ((MemberExited) message).member();
            LOG.info("Member exited cluster: {}", member);
            peers.remove(member.address());
        } else if (message instanceof MemberRemoved) {
            // remove peer
            final Member member = ((MemberRemoved) message).member();
            LOG.info("Member was removed from cluster: {}", member);
            peers.remove(member.address());
        } else if (message instanceof UnreachableMember) {
            // remove peer
            final Member member = ((UnreachableMember) message).member();
            LOG.info("Member is unreachable: {}", member);
            peers.remove(member.address());
        } else if (message instanceof ReachableMember) {
            // resync peer
            final Member member = ((ReachableMember) message).member();
            LOG.info("Member is reachable again: {}", member);

            if (member.address().equals(clusterExtension.selfAddress())) {
                return;
            }

            final String path = member.address() + PATH + topologyId;
            LOG.warn("Actor at :{} is resolving topology actor for path {}", clusterExtension.selfAddress(), path);

            clusterExtension.system().actorSelection(path).tell(new CustomIdentifyMessage(clusterExtension.selfAddress()), TypedActor.context().self());
        } else if (message instanceof CustomIdentifyMessageReply) {
            LOG.warn("Received a custom identify reply message from: {}", ((CustomIdentifyMessageReply) message).getAddress());
            if (!peers.containsKey(((CustomIdentifyMessage) message).getAddress())) {
                final TopologyManager peer = typedExtension.typedActorOf(new TypedProps<>(TopologyManager.class, BaseTopologyManager.class), actorRef);
                peers.put(((CustomIdentifyMessageReply) message).getAddress(), peer);
            }
        } else if (message instanceof CustomIdentifyMessage) {
            LOG.warn("Received a custom identify message from: {}", ((CustomIdentifyMessage) message).getAddress());
            if (!peers.containsKey(((CustomIdentifyMessage) message).getAddress())) {
                final TopologyManager peer = typedExtension.typedActorOf(new TypedProps<>(TopologyManager.class, BaseTopologyManager.class), actorRef);
                peers.put(((CustomIdentifyMessage) message).getAddress(), peer);
            }
            actorRef.tell(new CustomIdentifyMessageReply(clusterExtension.selfAddress()), TypedActor.context().self());
        }
    }
}

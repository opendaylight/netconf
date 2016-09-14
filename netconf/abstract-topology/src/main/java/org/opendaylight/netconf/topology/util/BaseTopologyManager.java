/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.util;

import akka.actor.ActorContext;
import akka.actor.ActorIdentity;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.actor.Identify;
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
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netconf.topology.RoleChangeStrategy;
import org.opendaylight.netconf.topology.StateAggregator;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.TopologyManagerCallback.TopologyManagerCallbackFactory;
import org.opendaylight.netconf.topology.util.messages.CustomIdentifyMessage;
import org.opendaylight.netconf.topology.util.messages.CustomIdentifyMessageReply;
import org.opendaylight.netconf.topology.util.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.util.NetconfTopologyPathCreator;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.binding.data.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;
import scala.concurrent.impl.Promise.DefaultPromise;

public final class BaseTopologyManager
        implements TopologyManager {

    private static final Logger LOG = LoggerFactory.getLogger(BaseTopologyManager.class);

    private final KeyedInstanceIdentifier<Topology, TopologyKey> topologyListPath;

    private final ActorSystem system;
    private final TypedActorExtension typedExtension;
    private final Cluster clusterExtension;

    private final BindingNormalizedNodeCodecRegistry codecRegistry;

    private final DataBroker dataBroker;
    private final RoleChangeStrategy roleChangeStrategy;
    private final StateAggregator aggregator;

    private final NodeWriter naSalNodeWriter;
    private final String topologyId;
    private final TopologyManagerCallback delegateTopologyHandler;
    private final Set<NodeId> created = new HashSet<>();

    private final Map<Address, TopologyManager> peers = new HashMap<>();
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
        this.delegateTopologyHandler = topologyManagerCallbackFactory.create(system, topologyId);
        this.aggregator = aggregator;
        this.naSalNodeWriter = naSalNodeWriter;
        this.roleChangeStrategy = roleChangeStrategy;
        this.codecRegistry = codecRegistry;

        // election has not yet happened
        this.isMaster = isMaster;

        this.topologyListPath = TopologyUtil.createTopologyListPath(topologyId);

        LOG.debug("Base manager started ", +id);
    }

    @Override
    public void preStart() {
        LOG.debug("preStart called");
        // TODO change to enum, master/slave active/standby
        roleChangeStrategy.registerRoleCandidate(TypedActor.<BaseTopologyManager>self());
        LOG.debug("candidate registered");
        clusterExtension.subscribe(TypedActor.context().self(), ClusterEvent.initialStateAsEvents(), MemberEvent.class, UnreachableMember.class);
    }

    @Override
    public void postStop() {
        LOG.debug("postStop called");
        clusterExtension.leave(clusterExtension.selfAddress());
        clusterExtension.unsubscribe(TypedActor.context().self());
    }

    @Override
    public ListenableFuture<Node> onNodeCreated(final NodeId nodeId, final Node node) {
        LOG.debug("TopologyManager({}) onNodeCreated received, nodeid: {} , isMaster: {}", id, nodeId.getValue(), isMaster);

        if (created.contains(nodeId)) {
            LOG.warn("Node{} already exists, triggering update..", nodeId);
            return onNodeUpdated(nodeId, node);
        }
        created.add(nodeId);
        final ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        if (isMaster) {

            futures.add(delegateTopologyHandler.onNodeCreated(nodeId, node));
            // only master should call connect on peers and aggregate futures
            for (TopologyManager topologyManager : peers.values()) {
                // convert binding into NormalizedNode for transfer
                final Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> normalizedNodeEntry = codecRegistry.toNormalizedNode(TopologyUtil.createTopologyNodePath(topologyId), node);

                LOG.debug("YangInstanceIdentifier {}", normalizedNodeEntry.getKey());
                LOG.debug("Value {}", normalizedNodeEntry.getValue());

                // add a future into our futures that gets its completion status from the converted scala future
                final SettableFuture<Node> settableFuture = SettableFuture.create();
                futures.add(settableFuture);
                final Future<NormalizedNodeMessage> scalaFuture = topologyManager.onRemoteNodeCreated(new NormalizedNodeMessage(normalizedNodeEntry.getKey(), normalizedNodeEntry.getValue()));
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
                    naSalNodeWriter.init(nodeId, result);
                }

                @Override
                public void onFailure(final Throwable t) {
                    // If the combined connection attempt failed, set the node to connection failed
                    LOG.debug("Futures aggregation failed");
                    naSalNodeWriter.update(nodeId, delegateTopologyHandler.getFailedState(nodeId, node));
                }
            }, TypedActor.context().dispatcher());

            //combine peer futures
            return aggregatedFuture;
        }

        // trigger create on this slave
        return delegateTopologyHandler.onNodeCreated(nodeId, node);
    }

    @Override
    public ListenableFuture<Node> onNodeUpdated(final NodeId nodeId, final Node node) {
        LOG.debug("TopologyManager({}) onNodeUpdated received, nodeid: {}", id, nodeId.getValue());

        // Master needs to trigger onNodeUpdated on peers and combine results
        if (isMaster) {
            // first cleanup old node
            final ListenableFuture<Void> deleteFuture = onNodeDeleted(nodeId);
            final SettableFuture<Node> createFuture = SettableFuture.create();
            final TopologyManager selfProxy = TypedActor.self();
            final ActorContext context = TypedActor.context();
            Futures.addCallback(deleteFuture, new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    LOG.warn("Delete part of update succesfull, triggering create");
                    // trigger create on all nodes
                    Futures.addCallback(selfProxy.onNodeCreated(nodeId, node), new FutureCallback<Node>() {
                        @Override
                        public void onSuccess(Node result) {
                            createFuture.set(result);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            createFuture.setException(t);
                        }
                    }, context.dispatcher());
                }

                @Override
                public void onFailure(Throwable t) {
                    LOG.warn("Delete part of update failed, {}", t);
                }
            }, context.dispatcher());
            return createFuture;
        }

        // Trigger update on this slave
        return delegateTopologyHandler.onNodeUpdated(nodeId, node);
    }

    @Override
    public ListenableFuture<Void> onNodeDeleted(final NodeId nodeId) {
        final ArrayList<ListenableFuture<Void>> futures = new ArrayList<>();
        created.remove(nodeId);

        // Master needs to trigger delete on peers and combine results
        if (isMaster) {
            futures.add(delegateTopologyHandler.onNodeDeleted(nodeId));
            for (TopologyManager topologyManager : peers.values()) {
                // add a future into our futures that gets its completion status from the converted scala future
                final SettableFuture<Void> settableFuture = SettableFuture.create();
                futures.add(settableFuture);
                final Future<Void> scalaFuture = topologyManager.onRemoteNodeDeleted(nodeId);
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

                }
            });

            return aggregatedFuture;
        }

        // Trigger delete
        return delegateTopologyHandler.onNodeDeleted(nodeId);
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
            LOG.debug("Node {} is master now", clusterExtension.selfAddress());
            clusterExtension.join(clusterExtension.selfAddress());
        }
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
                    naSalNodeWriter.update(nodeId, delegateTopologyHandler.getFailedState(nodeId, null));
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
    public Future<NormalizedNodeMessage> onRemoteNodeCreated(final NormalizedNodeMessage message) {
        final Entry<InstanceIdentifier<?>, DataObject> fromNormalizedNode =
                codecRegistry.fromNormalizedNode(message.getIdentifier(), message.getNode());
        final InstanceIdentifier<Node> iid = (InstanceIdentifier<Node>) fromNormalizedNode.getKey();
        final Node value = (Node) fromNormalizedNode.getValue();

        LOG.debug("TopologyManager({}) onRemoteNodeCreated received, nodeid: {}", value.getNodeId(), value);
        final ListenableFuture<Node> nodeListenableFuture = onNodeCreated(value.getNodeId(), value);
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
    public Future<Void> onRemoteNodeDeleted(final NodeId nodeId) {
        LOG.debug("TopologyManager({}) onRemoteNodeDeleted received, nodeid: {}", id, nodeId.getValue());

        final ListenableFuture<Void> listenableFuture = onNodeDeleted(nodeId);
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
        LOG.debug("TopologyManager({}) remoteGetCurrentStatusForNode received, nodeid: {}", id, nodeId.getValue());

        final ListenableFuture<Node> listenableFuture = getCurrentStatusForNode(nodeId);
        final DefaultPromise<NormalizedNodeMessage> promise = new DefaultPromise<>();
        Futures.addCallback(listenableFuture, new FutureCallback<Node>() {
            @Override
            public void onSuccess(Node result) {
                final Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> entry = codecRegistry.toNormalizedNode(TopologyUtil.createTopologyNodePath(topologyId), result);
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
        LOG.debug("message received {}", message);
        if (message instanceof MemberUp) {
            final Member member = ((MemberUp) message).member();
            LOG.info("Member is Up: {}", member);
            if (member.address().equals(clusterExtension.selfAddress())) {
                return;
            }

            final NetconfTopologyPathCreator pathCreator = new NetconfTopologyPathCreator(member.address().toString(), topologyId);
            final String path = pathCreator.build();
            LOG.debug("Actor at :{} is resolving topology actor for path {}", clusterExtension.selfAddress(), path);

            // first send basic identify message in case our messages have not been loaded through osgi yet to prevent crashing akka.
            clusterExtension.system().actorSelection(path).tell(new Identify(member.address()), TypedActor.context().self());
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
            final NetconfTopologyPathCreator pathCreator = new NetconfTopologyPathCreator(member.address().toString(), topologyId);
            final String path = pathCreator.build();
            LOG.debug("Actor at :{} is resolving topology actor for path {}", clusterExtension.selfAddress(), path);

            clusterExtension.system().actorSelection(path).tell(new Identify(member.address()), TypedActor.context().self());
        } else if (message instanceof ActorIdentity) {
            LOG.debug("Received ActorIdentity message", message);
            final NetconfTopologyPathCreator pathCreator = new NetconfTopologyPathCreator(((ActorIdentity) message).correlationId().toString(), topologyId);
            final String path = pathCreator.build();
            if (((ActorIdentity) message).getRef() == null) {
                LOG.debug("ActorIdentity has null actor ref, retrying..", message);
                final ActorRef self = TypedActor.context().self();
                final ActorContext context = TypedActor.context();
                system.scheduler().scheduleOnce(new FiniteDuration(5, TimeUnit.SECONDS), new Runnable() {
                    @Override
                    public void run() {
                        LOG.debug("Retrying identify message from master to node {} , full path {}", ((ActorIdentity) message).correlationId(), path);
                        context.system().actorSelection(path).tell(new Identify(((ActorIdentity) message).correlationId()), self);

                    }
                }, system.dispatcher());
                return;
            }
            LOG.debug("Actor at :{} is resolving topology actor for path {}, with a custom message", clusterExtension.selfAddress(), path);

            clusterExtension.system().actorSelection(path).tell(new CustomIdentifyMessage(clusterExtension.selfAddress()), TypedActor.context().self());
        } else if (message instanceof CustomIdentifyMessageReply) {

            LOG.warn("Received a custom identify reply message from: {}", ((CustomIdentifyMessageReply) message).getAddress());
            if (!peers.containsKey(((CustomIdentifyMessage) message).getAddress())) {
                final TopologyManager peer = typedExtension.typedActorOf(new TypedProps<>(TopologyManager.class, BaseTopologyManager.class), actorRef);
                peers.put(((CustomIdentifyMessageReply) message).getAddress(), peer);
                if (isMaster) {
                    resyncPeer(peer);
                }
            }
        } else if (message instanceof CustomIdentifyMessage) {
            LOG.warn("Received a custom identify message from: {}", ((CustomIdentifyMessage) message).getAddress());
            if (!peers.containsKey(((CustomIdentifyMessage) message).getAddress())) {
                final TopologyManager peer = typedExtension.typedActorOf(new TypedProps<>(TopologyManager.class, BaseTopologyManager.class), actorRef);
                peers.put(((CustomIdentifyMessage) message).getAddress(), peer);
                if (isMaster) {
                    resyncPeer(peer);
                }
            }
            actorRef.tell(new CustomIdentifyMessageReply(clusterExtension.selfAddress()), TypedActor.context().self());
        }
    }

    private void resyncPeer(final TopologyManager peer) {
        final ReadOnlyTransaction rTx = dataBroker.newReadOnlyTransaction();
        final CheckedFuture<Optional<Topology>, ReadFailedException> read = rTx.read(LogicalDatastoreType.CONFIGURATION, topologyListPath);

        Futures.addCallback(read, new FutureCallback<Optional<Topology>>() {
            @Override
            public void onSuccess(Optional<Topology> result) {
                if (result.isPresent() && result.get().getNode() != null) {
                    for (final Node node : result.get().getNode()) {
                        final Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> entry = codecRegistry.toNormalizedNode(TopologyUtil.createTopologyNodePath(topologyId), node);
                        peer.onRemoteNodeCreated(new NormalizedNodeMessage(entry.getKey(), entry.getValue()));
                        // we dont care about the future from now on since we will be notified by the onConnected event
                    }
                }
            }

            @Override
            public void onFailure(Throwable t) {
                LOG.error("Unable to read from datastore");
            }
        });

    }
}

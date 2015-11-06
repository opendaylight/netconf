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
import java.util.Random;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.RoleChangeStrategy;
import org.opendaylight.netconf.topology.StateAggregator;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.TopologyManagerCallback.TopologyManagerCallbackFactory;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import scala.concurrent.impl.Promise.DefaultPromise;

public final class BaseTopologyManager
        implements TopologyManager {

    private static final Logger LOG = LoggerFactory.getLogger(BaseTopologyManager.class);

    private final ActorSystem system;
    private final TypedActorExtension typedExtension;
    private final Cluster clusterExtension;

    private static final String PATH = "/user/TopologyNetconf";

    private final DataBroker dataBroker;
    private final RoleChangeStrategy roleChangeStrategy;
    private final StateAggregator aggregator;

    private final NodeWriter naSalNodeWriter;
    private final String topologyId;
    private final TopologyManagerCallback delegateTopologyHandler;

    private final Map<NodeId, NodeManager> nodes = new HashMap<>();
    private final Map<Address, TopologyManager> peers = new HashMap<>();
    private final int id = new Random().nextInt();

    private boolean isMaster;

    public BaseTopologyManager(final ActorSystem system,
                               final DataBroker dataBroker,
                               final String topologyId,
                               final TopologyManagerCallbackFactory topologyManagerCallbackFactory,
                               final StateAggregator aggregator,
                               final NodeWriter naSalNodeWriter,
                               final RoleChangeStrategy roleChangeStrategy) {
        this(system, dataBroker, topologyId, topologyManagerCallbackFactory, aggregator, naSalNodeWriter, roleChangeStrategy, false);
    }

    public BaseTopologyManager(final ActorSystem system,
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
        LOG.warn("TopologyManager({}) nodeCreated received, nodeid: {}", id, nodeId.getValue());

        ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        if (isMaster) {

            futures.add(delegateTopologyHandler.nodeCreated(nodeId, node));
            // only master should call connect on peers and aggregate futures
            for (TopologyManager topologyManager : peers.values()) {
                // add a future into our futures that gets its completion status from the converted scala future
                final SettableFuture<Node> settableFuture = SettableFuture.create();
                futures.add(settableFuture);
                Future<Node> scalaFuture = topologyManager.remoteNodeCreated(nodeId, node);
                scalaFuture.onComplete(new OnComplete<Node>() {
                    @Override
                    public void onComplete(Throwable failure, Node success) throws Throwable {
                        if (failure != null) {
                            settableFuture.setException(failure);
                            return;
                        }

                        settableFuture.set(success);
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
            });

            //combine peer futures
            return aggregatedFuture;
        }

        // trigger create on this slave
        return delegateTopologyHandler.nodeCreated(nodeId, node);
    }

    @Override
    public ListenableFuture<Node> nodeUpdated(final NodeId nodeId, final Node node) {
        LOG.warn("TopologyManager({}) nodeUpdated received, nodeid: {}", id, nodeId.getValue());

        ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        // Master needs to trigger nodeUpdated on peers and combine results
        if (isMaster) {
            futures.add(delegateTopologyHandler.nodeUpdated(nodeId, node));
            for (TopologyManager topologyManager : peers.values()) {
                // add a future into our futures that gets its completion status from the converted scala future
                final SettableFuture<Node> settableFuture = SettableFuture.create();
                futures.add(settableFuture);
                Future<Node> scalaFuture = topologyManager.remoteNodeUpdated(nodeId, node);
                scalaFuture.onComplete(new OnComplete<Node>() {
                    @Override
                    public void onComplete(Throwable failure, Node success) throws Throwable {
                        if (failure != null) {
                            settableFuture.setException(failure);
                            return;
                        }

                        settableFuture.set(success);
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

    @Override
    public ListenableFuture<Void> nodeDeleted(final NodeId nodeId) {
        ArrayList<ListenableFuture<Void>> futures = new ArrayList<>();

        // Master needs to trigger delete on peers and combine results
        if (isMaster) {
            futures.add(delegateTopologyHandler.nodeDeleted(nodeId));
            for (TopologyManager topologyManager : peers.values()) {
                // add a future into our futures that gets its completion status from the converted scala future
                final SettableFuture<Void> settableFuture = SettableFuture.create();
                futures.add(settableFuture);
                Future<Void> scalaFuture = topologyManager.remoteNodeDeleted(nodeId);
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
        ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        if (isMaster) {
            futures.add(delegateTopologyHandler.getCurrentStatusForNode(nodeId));
            for (TopologyManager topologyManager : peers.values()) {
                final SettableFuture<Node> settableFuture = SettableFuture.create();
                futures.add(settableFuture);
                final Future<Node> scalaFuture = topologyManager.remoteGetCurrentStatusForNode(nodeId);
                scalaFuture.onComplete(new OnComplete<Node>() {
                    @Override
                    public void onComplete(Throwable failure, Node success) throws Throwable {
                        if (failure != null) {
                            settableFuture.setException(failure);
                            return;
                        }

                        settableFuture.set(success);
                    }
                }, TypedActor.context().dispatcher());
            }

            final ListenableFuture<Node> aggregatedFuture = aggregator.combineUpdateAttempts(futures);
            Futures.addCallback(aggregatedFuture, new FutureCallback<Node>() {
                @Override
                public void onSuccess(Node result) {
                    naSalNodeWriter.update(nodeId, result);
                }

                @Override
                public void onFailure(Throwable t) {
                    naSalNodeWriter.update(nodeId, nodes.get(nodeId).getFailedState(nodeId, null));
                }
            });
        }

        return delegateTopologyHandler.getCurrentStatusForNode(nodeId);
    }

    @Override
    public void onRoleChanged(RoleChangeDTO roleChangeDTO) {
        isMaster = roleChangeDTO.isOwner();
        delegateTopologyHandler.onRoleChanged(roleChangeDTO);
        if (isMaster) {
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
                Future<Node> scalaFuture = topologyManager.remoteGetCurrentStatusForNode(nodeId);
                scalaFuture.onComplete(new OnComplete<Node>() {
                    @Override
                    public void onComplete(Throwable failure, Node success) throws Throwable {
                        if (failure != null) {
                            settableFuture.setException(failure);
                            return;
                        }

                        settableFuture.set(success);
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
        for (TopologyManager manager : peers.values()) {
            final Future<Boolean> future = manager.isMaster();
            future.onComplete(new OnComplete<Boolean>() {
                @Override
                public void onComplete(Throwable failure, Boolean success) throws Throwable {
                    if (failure != null) {
                        LOG.debug("Cannot ", failure);
                    }
                }
            }, TypedActor.context().dispatcher());
            manager.notifyNodeStatusChange(nodeId);
        }
    }

    @Override
    public boolean hasAllPeersUp() {
        LOG.debug("Peers needed: {} Peers up: {}", 2, peers.size());
        LOG.warn(clusterExtension.state().toString());
        return peers.size() == 2;
    }

    @Override
    public Future<Node> remoteNodeCreated(NodeId nodeId, Node node) {
        LOG.warn("TopologyManager({}) remoteNodeCreated received, nodeid: {}", id, nodeId.getValue());

        final ListenableFuture<Node> nodeListenableFuture = nodeCreated(nodeId, node);
        final DefaultPromise<Node> promise = new DefaultPromise<>();
        Futures.addCallback(nodeListenableFuture, new FutureCallback<Node>() {
            @Override
            public void onSuccess(Node result) {
                promise.success(result);
            }

            @Override
            public void onFailure(Throwable t) {
                promise.failure(t);
            }
        });

        return promise.future();
    }

    @Override
    public Future<Node> remoteNodeUpdated(NodeId nodeId, Node node) {
        LOG.warn("TopologyManager({}) remoteNodeUpdated received, nodeid: {}", id, nodeId.getValue());

        final ListenableFuture<Node> nodeListenableFuture = nodeUpdated(nodeId, node);
        final DefaultPromise<Node> promise = new DefaultPromise<>();
        Futures.addCallback(nodeListenableFuture, new FutureCallback<Node>() {
            @Override
            public void onSuccess(Node result) {
                promise.success(result);
            }

            @Override
            public void onFailure(Throwable t) {
                promise.failure(t);
            }
        });
        return promise.future();
    }

    @Override
    public Future<Void> remoteNodeDeleted(NodeId nodeId) {
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

    @Override
    public Future<Node> remoteGetCurrentStatusForNode(NodeId nodeId) {
        return null;
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

            final String path = member.address() + PATH;
            LOG.debug("Resolving topology actor for path {}", path);

            final Future<ActorRef> refFuture = clusterExtension.system().actorSelection(path).resolveOne(FiniteDuration.create(10L, TimeUnit.SECONDS));
            refFuture.onComplete(new OnComplete<ActorRef>() {
                @Override
                public void onComplete(Throwable throwable, ActorRef actorRef) throws Throwable {
                    if (throwable != null) {
                        LOG.warn("Unable to resolve actor for path: {} .Rescheduling..", path, throwable);
                        system.scheduler().scheduleOnce(
                                Duration.create(5L, TimeUnit.SECONDS), createSchedulerRunnable(member.address(), path), system.dispatcher());
                        return;
                    }

                    LOG.debug("Actor ref for path {} resolved", path);
                    final TopologyManager peer = typedExtension.typedActorOf(new TypedProps<>(TopologyManager.class, BaseTopologyManager.class), actorRef);
                    // resync this actor
                    peers.put(member.address(), peer);
                }
            }, system.dispatcher());
        } else if (message instanceof MemberExited) {
            // remove peer
            Member member = ((MemberExited) message).member();
            LOG.info("Member exited cluster: {}", member);
        } else if (message instanceof MemberRemoved) {
            // remove peer
            Member member = ((MemberRemoved) message).member();
            LOG.info("Member was removed from cluster: {}", member);
        } else if (message instanceof UnreachableMember) {
            // remove peer
            Member member = ((UnreachableMember) message).member();
            LOG.info("Member is unreachable: {}", member);
        } else if (message instanceof ReachableMember) {
            // resync peer
            Member member = ((ReachableMember) message).member();
            LOG.info("Member is reachable again: {}", member);
        }
    }

    private Runnable createSchedulerRunnable(final Address address, final String path) {
        return new Runnable() {
            @Override
            public void run() {
                final Future<ActorRef> refFuture = clusterExtension.system().actorSelection(path).resolveOne(FiniteDuration.create(10L, TimeUnit.SECONDS));
                refFuture.onComplete(new OnComplete<ActorRef>() {
                    @Override
                    public void onComplete(Throwable throwable, ActorRef actorRef) throws Throwable {
                        if (throwable != null) {
                            LOG.warn("Unable to resolve actor for path: {} .Rescheduling..", path, throwable);
                            clusterExtension.system().scheduler().scheduleOnce(
                                    Duration.create(5L, TimeUnit.SECONDS), createSchedulerRunnable(address, path), system.dispatcher());
                            return;
                        }

                        LOG.debug("Actor ref for path {} resolved", path);
                        final TopologyManager peer = typedExtension.typedActorOf(new TypedProps<>(TopologyManager.class, BaseTopologyManager.class), actorRef);
                        // resync this actor
                        peers.put(address, peer);
                    }
                }, system.dispatcher());
            }
        };
    }
}
